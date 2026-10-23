package agape.app.data.auth

import agape.app.BuildConfig
import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.security.MessageDigest
import java.util.UUID

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val userId: String, val email: String?, val isLocalDev: Boolean = false) : AuthState
}

interface AuthRepository {
    val state: StateFlow<AuthState>
    /** False when no Supabase keys are configured and a local development account is used instead. */
    val isConfigured: Boolean
    suspend fun signIn(email: String, password: String)
    /** Returns true when the account still needs e-mail confirmation. */
    suspend fun signUp(email: String, password: String): Boolean
    suspend fun sendPasswordReset(email: String)
    suspend fun signInWithGoogle(activity: Activity)
    suspend fun signOut()
    suspend fun deleteAccount()
}

class SupabaseAuthRepository(private val client: SupabaseClient, scope: CoroutineScope) : AuthRepository {
    override val isConfigured = true

    override val state: StateFlow<AuthState> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> status.session.user
                ?.let { AuthState.SignedIn(it.id, it.email) } ?: AuthState.SignedOut
            is SessionStatus.NotAuthenticated -> AuthState.SignedOut
            is SessionStatus.RefreshFailure -> client.auth.currentUserOrNull()
                ?.let { AuthState.SignedIn(it.id, it.email) } ?: AuthState.SignedOut
            else -> AuthState.Loading
        }
    }.stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    override suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun signUp(email: String, password: String): Boolean {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        return client.auth.currentSessionOrNull() == null
    }

    override suspend fun sendPasswordReset(email: String) = client.auth.resetPasswordForEmail(email.trim())

    override suspend fun signInWithGoogle(activity: Activity) {
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setNonce(hashedNonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activity).getCredential(activity, request)
        val google = GoogleIdTokenCredential.createFrom(result.credential.data)
        client.auth.signInWith(IDToken) {
            idToken = google.idToken
            provider = Google
            nonce = rawNonce
        }
    }

    override suspend fun signOut() = client.auth.signOut()

    override suspend fun deleteAccount() {
        // See supabase/migrations: deletes the caller's auth user and, by cascade, their synced data.
        client.postgrest.rpc("delete_my_account")
        client.auth.signOut()
    }
}

/** Used when the build has no Supabase keys, so the whole app can still be explored during development. */
class LocalDevAuthRepository(context: Context) : AuthRepository {
    private val prefs = context.getSharedPreferences("agape_dev_auth", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<AuthState>(
        prefs.getString("email", null)?.let { AuthState.SignedIn("local-dev", it, isLocalDev = true) } ?: AuthState.SignedOut,
    )
    override val state: StateFlow<AuthState> = _state.asStateFlow()
    override val isConfigured = false

    private fun signInAs(email: String) {
        prefs.edit().putString("email", email).apply()
        _state.value = AuthState.SignedIn("local-dev", email, isLocalDev = true)
    }

    override suspend fun signIn(email: String, password: String) = signInAs(email.trim())
    override suspend fun signUp(email: String, password: String): Boolean { signInAs(email.trim()); return false }
    override suspend fun sendPasswordReset(email: String) = Unit
    override suspend fun signInWithGoogle(activity: Activity) = signInAs("google-dev@agape.local")
    override suspend fun signOut() {
        prefs.edit().remove("email").apply()
        _state.value = AuthState.SignedOut
    }
    override suspend fun deleteAccount() = signOut()
}
