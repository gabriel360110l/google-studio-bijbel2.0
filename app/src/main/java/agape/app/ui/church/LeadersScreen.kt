package agape.app.ui.church

import agape.app.R
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.components.QuietButton
import agape.app.ui.onboarding.traditionName
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** The heads of the churches of the user's tradition, today and in history. */
@Composable
fun LeadersScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val context = LocalContext.current
    val tradition = tradition
    val c = AgapeTheme.colors
    Column(Modifier.fillMaxSize()) {
        DetailTopBar("", onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Kicker(stringResource(traditionName(tradition)), color = c.inkMuted)
                Text(stringResource(R.string.leaders_title), style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.padding(top = 6.dp))
            }
            items(container.content.leaders(tradition)) { l ->
                AgapeCard {
                    Text(l.church[lang], style = MaterialTheme.typography.titleLarge, color = c.ink)
                    Text(l.title[lang], style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                    if (l.current.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Kicker(stringResource(R.string.leaders_current))
                        Text(l.current + if (l.since.isNotBlank()) " · ${l.since}" else "", style = MaterialTheme.typography.headlineSmall, color = c.primary)
                    }
                    if (l.notable.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Kicker(stringResource(R.string.leaders_notable))
                        l.notable.forEach { n ->
                            Text("${n.name} · ${n.years}", style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.padding(top = 8.dp))
                            Text(n.note[lang], style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                        }
                    }
                    QuietButton(stringResource(R.string.leaders_full_list), {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.wiki))) }
                    })
                }
            }
        }
    }
}
