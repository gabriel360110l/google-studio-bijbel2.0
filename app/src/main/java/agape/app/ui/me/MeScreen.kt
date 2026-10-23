package agape.app.ui.me

import agape.app.BuildConfig
import agape.app.R
import agape.app.data.auth.AuthState
import agape.app.data.user.Achievement
import agape.app.data.user.ProgressStats
import agape.app.ui.LocalContainer
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.Divider
import agape.app.ui.components.Kicker
import agape.app.ui.components.ListRow
import agape.app.ui.components.Ornament
import agape.app.ui.components.DetailTopBar
import agape.app.ui.theme.AgapeTheme
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun MeScreen(
    onBack: () -> Unit,
    openSettings: () -> Unit,
    openSaved: () -> Unit,
    openActivity: () -> Unit,
    openAchievements: () -> Unit,
    openHelp: () -> Unit,
    openLegal: (String) -> Unit,
) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth by container.auth.state.collectAsStateWithLifecycle()
    val stats by container.user.stats.collectAsStateWithLifecycle(ProgressStats())
    var confirmSignOut by remember { mutableStateOf(false) }
    val c = AgapeTheme.colors
    val email = (auth as? AuthState.SignedIn)?.email

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.me_sign_out)) },
            text = { Text(stringResource(R.string.me_sign_out_text)) },
            confirmButton = {
                TextButton({
                    confirmSignOut = false
                    scope.launch { signOut(container) }
                }) { Text(stringResource(R.string.me_sign_out), color = c.danger) }
            },
            dismissButton = { TextButton({ confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { DetailTopBar(stringResource(R.string.home_profile), onBack) }
        if (email != null) {
            item { Text(email, style = MaterialTheme.typography.bodySmall, color = c.inkMuted, modifier = Modifier.padding(start = 20.dp, bottom = 4.dp)) }
        }
        item {
            AgapeCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Kicker(stringResource(R.string.me_streak))
                        Text(
                            pluralStringResource(R.plurals.days, stats.currentStreak, stats.currentStreak),
                            style = MaterialTheme.typography.displaySmall, color = c.primary,
                        )
                        Text(stringResource(R.string.me_longest, stats.longestStreak), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                    }
                    Ornament(width = 64.dp)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat(stats.chaptersRead, stringResource(R.string.me_stat_chapters))
                    Stat(stats.hoursPrayed, stringResource(R.string.me_stat_hours))
                    Stat(stats.quizzesPassed, stringResource(R.string.me_stat_quizzes))
                    Stat(Achievement.entries.count { it.earned(stats) }, stringResource(R.string.me_stat_medals))
                }
            }
        }
        item { MeRow(Icons.Outlined.EmojiEvents, stringResource(R.string.me_achievements), openAchievements) }
        item { MeRow(Icons.Outlined.History, stringResource(R.string.me_activity), openActivity) }
        item { MeRow(Icons.Outlined.BookmarkBorder, stringResource(R.string.me_saved), openSaved) }
        item { MeRow(Icons.Outlined.Settings, stringResource(R.string.me_settings), openSettings) }
        item { Spacer(Modifier.height(12.dp)) }
        item { MeRow(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.me_help), openHelp) }
        item {
            MeRow(Icons.Outlined.MailOutline, stringResource(R.string.me_feedback), onClick = {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${BuildConfig.SUPPORT_EMAIL}"))
                    .putExtra(Intent.EXTRA_SUBJECT, "AGAPE feedback (${BuildConfig.VERSION_NAME})")
                runCatching { context.startActivity(intent) }
            })
        }
        item {
            MeRow(Icons.Outlined.Favorite, stringResource(R.string.me_donate), onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.DONATION_URL))) }
            })
        }
        item { Spacer(Modifier.height(12.dp)) }
        item { MeRow(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.me_sign_out), { confirmSignOut = true }, danger = true) }
        item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Ornament()
                Spacer(Modifier.height(8.dp))
                Text("AGAPE ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
                Row {
                    TextButton({ openLegal("privacy") }) { Text(stringResource(R.string.legal_privacy), style = MaterialTheme.typography.labelSmall, color = c.inkMuted) }
                    TextButton({ openLegal("terms") }) { Text(stringResource(R.string.legal_terms), style = MaterialTheme.typography.labelSmall, color = c.inkMuted) }
                }
            }
        }
    }
}

/** Pushes pending progress, then removes this account's data from the device. */
suspend fun signOut(container: agape.app.AppContainer) {
    container.sync.push()
    container.auth.signOut()
    container.user.clear()
    container.settings.clearPersonal()
}

@Composable
private fun Stat(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = AgapeTheme.colors.ink)
        Text(label, style = MaterialTheme.typography.labelSmall, color = AgapeTheme.colors.inkMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MeRow(icon: ImageVector, title: String, onClick: () -> Unit, danger: Boolean = false) {
    val c = AgapeTheme.colors
    ListRow(
        title = title,
        onClick = onClick,
        leading = { Icon(icon, null, tint = if (danger) c.danger else c.primary) },
        trailing = { if (!danger) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.inkFaint) },
    )
    Divider()
}
