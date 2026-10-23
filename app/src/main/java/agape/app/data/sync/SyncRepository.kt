package agape.app.data.sync

import agape.app.data.auth.AuthState
import agape.app.data.user.UserRepository
import agape.app.data.user.UserSnapshot
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
private data class SnapshotRow(
    @SerialName("user_id") val userId: String,
    val data: JsonElement,
)

/**
 * Keeps progress in the account. Data lives locally first (offline-first); after sign-in the server copy is
 * merged in, and every local change is pushed a few seconds later.
 */
class SyncRepository(
    private val client: SupabaseClient?,
    private val user: UserRepository,
    private val auth: StateFlow<AuthState>,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @OptIn(FlowPreview::class)
    fun start() {
        if (client == null) return
        scope.launch {
            auth.distinctUntilChanged { a, b -> (a as? AuthState.SignedIn)?.userId == (b as? AuthState.SignedIn)?.userId }
                .collect { state -> if (state is AuthState.SignedIn) pull(state.userId) }
        }
        scope.launch {
            user.changes.debounce(3_000).collect { push() }
        }
    }

    private suspend fun pull(userId: String) = runCatching {
        val row = client!!.from(TABLE).select { filter { eq("user_id", userId) } }.decodeSingleOrNull<SnapshotRow>()
        if (row != null) user.merge(json.decodeFromJsonElement(UserSnapshot.serializer(), row.data))
        push()
    }.onFailure { Log.w(TAG, "pull failed", it) }

    suspend fun push() {
        val userId = (auth.value as? AuthState.SignedIn)?.userId ?: return
        runCatching {
            val data = json.encodeToJsonElement(UserSnapshot.serializer(), user.snapshot())
            client?.from(TABLE)?.upsert(SnapshotRow(userId, data))
        }.onFailure { Log.w(TAG, "push failed", it) }
    }

    private companion object {
        const val TABLE = "user_snapshots"
        const val TAG = "AgapeSync"
    }
}
