package agape.app.ui.legal

import agape.app.R
import agape.app.ui.LocalLanguage
import agape.app.ui.components.DetailTopBar
import agape.app.ui.theme.AgapeTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Loads `assets/legal/<doc>_<lang>.md`. Only `#` headings and paragraphs are used. */
@Composable
private fun rememberLegalText(doc: String): List<String> {
    val context = LocalContext.current
    val lang = LocalLanguage.current
    return remember(doc, lang) {
        runCatching { context.assets.open("legal/${doc}_${lang.tag}.md").bufferedReader().use { it.readText() } }
            .getOrDefault("")
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}

@Composable
private fun LegalBody(blocks: List<String>, modifier: Modifier = Modifier) {
    val c = AgapeTheme.colors
    Column(modifier) {
        blocks.forEach { block ->
            when {
                block.startsWith("# ") -> Text(block.removePrefix("# "), style = MaterialTheme.typography.headlineMedium, color = c.ink, modifier = Modifier.padding(bottom = 12.dp))
                block.startsWith("## ") -> Text(block.removePrefix("## "), style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                else -> Text(block, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
    }
}

private fun docTitle(doc: String) = if (doc == "privacy") R.string.legal_privacy else R.string.legal_terms

@Composable
fun LegalScreen(doc: String, onBack: () -> Unit) {
    val blocks = rememberLegalText(doc)
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(docTitle(doc)), onBack)
        LegalBody(blocks, Modifier.verticalScroll(rememberScrollState()).padding(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentSheet(doc: String, onDismiss: () -> Unit) {
    val blocks = rememberLegalText(doc)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AgapeTheme.colors.surface) {
        LegalBody(blocks, Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp))
    }
}
