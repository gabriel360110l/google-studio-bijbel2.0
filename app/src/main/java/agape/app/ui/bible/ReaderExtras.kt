package agape.app.ui.bible

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.Testament
import agape.app.data.bible.Verse
import agape.app.data.content.Book
import agape.app.data.content.chapterCount
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.Kicker
import agape.app.ui.components.Pill
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.cardShape
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

// ============================================================================
// 1. Chapter Audio Reader (TTS) Controller & Mini-Player Bar
// ============================================================================

@Stable
class ChapterAudioController(
    private val context: Context,
    private val lang: AppLanguage,
) {
    var isPlaying by mutableStateOf(false)
        private set
    var isActive by mutableStateOf(false)
        private set
    var currentVerseIndex by mutableIntStateOf(0)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var fallbackRunnable: Runnable? = null
    private var verses: List<Verse> = emptyList()
    private var bookTitle: String = ""
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsSupported = true

    val currentVerseNumber: Int?
        get() = if (isActive) verses.getOrNull(currentVerseIndex)?.verse else null

    val currentVerseText: String
        get() = verses.getOrNull(currentVerseIndex)?.text.orEmpty()

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                mainHandler.post {
                    if (status == TextToSpeech.SUCCESS) {
                        ttsReady = true
                        configureTts()
                        if (isPlaying && verses.isNotEmpty()) {
                            speakCurrentVerse()
                        }
                    } else {
                        ttsReady = false
                        ttsSupported = false
                        if (isPlaying && verses.isNotEmpty()) {
                            scheduleFallbackAdvance()
                        }
                    }
                }
            }
        }.onFailure {
            ttsSupported = false
        }
    }

    private fun configureTts() {
        val engine = tts ?: return
        runCatching {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            val preferred = if (lang == AppLanguage.DUTCH) Locale("nl", "NL") else Locale.US
            val fallbackNl = Locale("nl")
            val langResult = when {
                engine.isLanguageAvailable(preferred) >= TextToSpeech.LANG_AVAILABLE -> engine.setLanguage(preferred)
                lang == AppLanguage.DUTCH && engine.isLanguageAvailable(fallbackNl) >= TextToSpeech.LANG_AVAILABLE -> engine.setLanguage(fallbackNl)
                engine.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE -> engine.setLanguage(Locale.US)
                else -> engine.setLanguage(Locale.getDefault())
            }
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Keep engine active with default voice if available
                engine.defaultVoice?.locale?.let { engine.setLanguage(it) }
            }
            engine.setSpeechRate(0.92f)
            engine.setPitch(0.98f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    val idx = utteranceId?.removePrefix("v_")?.toIntOrNull() ?: return
                    mainHandler.post {
                        if (isPlaying && idx == currentVerseIndex) {
                            advanceOrStop()
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        if (isPlaying) {
                            scheduleFallbackAdvance()
                        }
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    mainHandler.post {
                        if (isPlaying) {
                            scheduleFallbackAdvance()
                        }
                    }
                }
            })
        }
    }

    fun bindChapter(newVerses: List<Verse>, title: String) {
        if (newVerses.isEmpty()) return
        val sameChapter = verses.size == newVerses.size &&
            verses.firstOrNull()?.book == newVerses.firstOrNull()?.book &&
            verses.firstOrNull()?.chapter == newVerses.firstOrNull()?.chapter
        if (!sameChapter) {
            stop()
            verses = newVerses
            bookTitle = title
            currentVerseIndex = 0
        } else {
            verses = newVerses
            bookTitle = title
        }
    }

    fun togglePlayPause(
        currentVerses: List<Verse> = emptyList(),
        currentTitle: String = "",
        startAtVerse: Int? = null,
    ) {
        if (currentVerses.isNotEmpty()) {
            bindChapter(currentVerses, currentTitle.ifBlank { bookTitle })
        }
        if (verses.isEmpty()) return

        if (startAtVerse != null) {
            val found = verses.indexOfFirst { it.verse == startAtVerse }
            if (found >= 0) currentVerseIndex = found
        }
        if (isPlaying) {
            pause()
        } else {
            isActive = true
            isPlaying = true
            speakCurrentVerse()
        }
    }

    fun pause() {
        isPlaying = false
        cancelFallback()
        runCatching { tts?.stop() }
    }

    fun previousVerse() {
        if (verses.isEmpty()) return
        cancelFallback()
        currentVerseIndex = (currentVerseIndex - 1).coerceAtLeast(0)
        if (isPlaying) speakCurrentVerse()
    }

    fun nextVerse() {
        if (verses.isEmpty()) return
        cancelFallback()
        if (currentVerseIndex + 1 < verses.size) {
            currentVerseIndex += 1
            if (isPlaying) speakCurrentVerse()
        } else {
            stop()
        }
    }

    fun stop() {
        isPlaying = false
        isActive = false
        currentVerseIndex = 0
        cancelFallback()
        runCatching { tts?.stop() }
    }

    private fun advanceOrStop() {
        cancelFallback()
        if (isPlaying && currentVerseIndex + 1 < verses.size) {
            currentVerseIndex += 1
            speakCurrentVerse()
        } else {
            isPlaying = false
        }
    }

    private fun cancelFallback() {
        fallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        fallbackRunnable = null
    }

    private fun scheduleFallbackAdvance() {
        cancelFallback()
        val v = verses.getOrNull(currentVerseIndex) ?: return
        val words = v.text.split(Regex("\\s+")).size.coerceAtLeast(6)
        val durationMs = (words * 240L).coerceIn(2800L, 9500L)
        val r = Runnable {
            if (isPlaying) {
                advanceOrStop()
            }
        }
        fallbackRunnable = r
        mainHandler.postDelayed(r, durationMs)
    }

    private fun speakCurrentVerse() {
        cancelFallback()
        if (verses.isEmpty()) return
        val v = verses.getOrNull(currentVerseIndex) ?: return
        val textToRead = if (currentVerseIndex == 0 && bookTitle.isNotBlank()) {
            "$bookTitle. ${v.text}"
        } else {
            v.text
        }

        val engine = tts
        if (engine == null || !ttsSupported) {
            scheduleFallbackAdvance()
            return
        }
        if (!ttsReady) {
            // Will start automatically once onInit completes; also arm a safety timer in case TTS init hangs
            scheduleFallbackAdvance()
            return
        }

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val res = runCatching {
            engine.speak(textToRead, TextToSpeech.QUEUE_FLUSH, params, "v_$currentVerseIndex")
        }.getOrDefault(TextToSpeech.ERROR)

        if (res == TextToSpeech.ERROR) {
            scheduleFallbackAdvance()
        }
    }

    fun release() {
        cancelFallback()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}

@Composable
fun rememberChapterAudioController(
    verses: List<Verse>,
    title: String,
    lang: AppLanguage,
): ChapterAudioController {
    val context = LocalContext.current
    val controller = remember(lang) { ChapterAudioController(context, lang) }
    LaunchedEffect(verses, title) {
        if (verses.isNotEmpty()) {
            controller.bindChapter(verses, title)
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    return controller
}

@Composable
fun ChapterAudioBar(
    controller: ChapterAudioController,
    title: String,
    lang: AppLanguage,
) {
    val c = AgapeTheme.colors
    val verseNum = controller.currentVerseNumber ?: 1
    val verseSnippet = controller.currentVerseText
    Surface(
        color = c.surface,
        border = BorderStroke(0.5.dp, c.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = if (lang == AppLanguage.DUTCH) "AUDIO VOORLEZEN · $title:$verseNum" else "AUDIO READER · $title:$verseNum",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = c.accent,
                )
                if (verseSnippet.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = verseSnippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { controller.previousVerse() }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.SkipPrevious,
                        contentDescription = if (lang == AppLanguage.DUTCH) "Vorig vers" else "Previous verse",
                        tint = c.ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(c.primary)
                        .clickable { controller.togglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (controller.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (controller.isPlaying) "Pauze" else "Afspelen",
                        tint = c.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { controller.nextVerse() }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.SkipNext,
                        contentDescription = if (lang == AppLanguage.DUTCH) "Volgend vers" else "Next verse",
                        tint = c.ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { controller.stop() }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Stop,
                        contentDescription = if (lang == AppLanguage.DUTCH) "Stop voorlezen" else "Stop audio",
                        tint = c.inkMuted,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

// ============================================================================
// 2. Quick Book & Chapter Picker Modal Sheet
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickBookChapterPickerSheet(
    currentBook: String,
    currentChapter: Int,
    onSelect: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val c = AgapeTheme.colors
    val books = remember(tradition, lang) { container.content.canon(tradition, lang) }
    val activeBookObj = remember(books, currentBook) { books.firstOrNull { it.id == currentBook } }

    var query by rememberSaveable { mutableStateOf("") }
    var testament by rememberSaveable { mutableStateOf(activeBookObj?.testament ?: Testament.OT) }
    var expandedBook by rememberSaveable { mutableStateOf<String?>(currentBook) }

    val filteredBooks = remember(books, testament, query, lang) {
        if (query.isNotBlank()) {
            books.filter { it.name[lang].contains(query.trim(), ignoreCase = true) }
        } else {
            books.filter { it.testament == testament }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Kicker(if (lang == AppLanguage.DUTCH) "SNEL NAVIGEREN" else "QUICK JUMP")
                Spacer(Modifier.height(4.dp))
                Text(
                    if (lang == AppLanguage.DUTCH) "Kies boek en hoofdstuk" else "Select book & chapter",
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.ink,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.bible_filter_books), color = c.inkFaint) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = c.inkMuted) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = null, tint = c.inkMuted)
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.primary,
                        unfocusedBorderColor = c.line,
                        focusedContainerColor = c.background,
                        unfocusedContainerColor = c.background,
                        cursorColor = c.primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (query.isBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(stringResource(R.string.bible_ot), testament == Testament.OT, onClick = { testament = Testament.OT })
                        Pill(stringResource(R.string.bible_nt), testament == Testament.NT, onClick = { testament = Testament.NT })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                items(filteredBooks, key = { it.id }) { b ->
                    val isExpanded = expandedBook == b.id
                    val chCount = b.chapterCount(lang)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isExpanded) c.background.copy(alpha = 0.7f) else Color.Transparent)
                            .border(
                                width = if (b.id == currentBook) 1.dp else 0.5.dp,
                                color = if (b.id == currentBook) c.accent.copy(alpha = 0.7f) else c.line.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(14.dp),
                            ),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { expandedBook = if (isExpanded) null else b.id }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = b.name[lang],
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = if (b.id == currentBook) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (b.id == currentBook) c.primary else c.ink,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "$chCount",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = c.inkMuted,
                                )
                                Icon(
                                    if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = null,
                                    tint = c.inkMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                (1..chCount).forEach { ch ->
                                    val isCurrent = b.id == currentBook && ch == currentChapter
                                    Box(
                                        Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isCurrent) c.primary else c.surface)
                                            .border(
                                                1.dp,
                                                if (isCurrent) c.primary else c.line,
                                                RoundedCornerShape(10.dp),
                                            )
                                            .clickable {
                                                onDismiss()
                                                onSelect(b.id, ch)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "$ch",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            ),
                                            color = if (isCurrent) c.onPrimary else c.ink,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. Verse Image Card Generator & Share Sheet
// ============================================================================

private enum class VerseCardStyle(
    val titleNl: String,
    val titleEn: String,
    val bgTop: Color,
    val bgBottom: Color,
    val textColor: Color,
    val accentColor: Color,
) {
    MIDNIGHT_GOLD(
        titleNl = "Nacht & Goud",
        titleEn = "Midnight Gold",
        bgTop = Color(0xFF0E1726),
        bgBottom = Color(0xFF1C2D48),
        textColor = Color(0xFFF9F5EB),
        accentColor = Color(0xFFD4AF37),
    ),
    PARCHMENT(
        titleNl = "Perkament",
        titleEn = "Parchment",
        bgTop = Color(0xFFF7F0E2),
        bgBottom = Color(0xFFE9DEC8),
        textColor = Color(0xFF281E15),
        accentColor = Color(0xFF8C5E32),
    ),
    SACRED_OLIVE(
        titleNl = "Olijf & Licht",
        titleEn = "Sacred Olive",
        bgTop = Color(0xFF1A2922),
        bgBottom = Color(0xFF283D33),
        textColor = Color(0xFFF4F1EA),
        accentColor = Color(0xFFC8A96A),
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseImageShareSheet(
    selectedVerses: List<Verse>,
    referenceLabel: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    var cardStyle by remember { mutableStateOf(VerseCardStyle.MIDNIGHT_GOLD) }

    val combinedText = remember(selectedVerses) {
        if (selectedVerses.size == 1) {
            "“${selectedVerses.first().text.trim()}”"
        } else {
            selectedVerses.joinToString(" ") { "[${it.verse}] ${it.text.trim()}" }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Kicker(if (lang == AppLanguage.DUTCH) "VERS-AFBEELDING MAKEN" else "VERSE IMAGE CARD")
            Spacer(Modifier.height(4.dp))
            Text(
                referenceLabel,
                style = MaterialTheme.typography.headlineSmall,
                color = c.ink,
            )
            Spacer(Modifier.height(16.dp))

            // Live Compose Card Preview
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(listOf(cardStyle.bgTop, cardStyle.bgBottom)))
                    .border(1.5.dp, cardStyle.accentColor.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .padding(26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "☩",
                        style = MaterialTheme.typography.headlineMedium,
                        color = cardStyle.accentColor,
                    )
                    Text(
                        text = combinedText,
                        style = AgapeTheme.type.scripture.copy(
                            fontSize = if (combinedText.length > 260) 17.sp else 20.sp,
                            lineHeight = if (combinedText.length > 260) 26.sp else 30.sp,
                            fontStyle = FontStyle.Italic,
                        ),
                        color = cardStyle.textColor,
                        textAlign = TextAlign.Center,
                    )
                    Box(
                        Modifier
                            .size(width = 42.dp, height = 1.dp)
                            .background(cardStyle.accentColor.copy(alpha = 0.7f)),
                    )
                    Text(
                        text = referenceLabel.uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 1.8.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = cardStyle.accentColor,
                    )
                    Text(
                        text = "A G A P E",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 3.sp,
                            fontSize = 10.sp,
                        ),
                        color = cardStyle.textColor.copy(alpha = 0.55f),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Style selector pills
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                VerseCardStyle.entries.forEach { style ->
                    val label = if (lang == AppLanguage.DUTCH) style.titleNl else style.titleEn
                    Pill(
                        text = label,
                        selected = cardStyle == style,
                        onClick = { cardStyle = style },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            PrimaryButton(
                text = if (lang == AppLanguage.DUTCH) "Deel Vers-afbeelding" else "Share Verse Image",
                onClick = {
                    shareVerseCardAsBitmap(
                        context = context,
                        scriptureText = combinedText,
                        referenceLabel = referenceLabel,
                        style = cardStyle,
                    )
                    onDismiss()
                },
            )
        }
    }
}

private fun shareVerseCardAsBitmap(
    context: Context,
    scriptureText: String,
    referenceLabel: String,
    style: VerseCardStyle,
) {
    val width = 1080
    val paddingH = 110

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.textColor.toArgb()
        textSize = if (scriptureText.length > 280) 40f else 48f
        typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    }

    val staticLayout = StaticLayout.Builder
        .obtain(scriptureText, 0, scriptureText.length, textPaint, width - paddingH * 2)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(14f, 1.15f)
        .setIncludePad(true)
        .build()

    val height = (staticLayout.height + 520).coerceAtLeast(1080)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Background Gradient
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            style.bgTop.toArgb(),
            style.bgBottom.toArgb(),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // Inner Border Frame
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.accentColor.copy(alpha = 0.45f).toArgb()
        this.style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawRoundRect(RectF(48f, 48f, width - 48f, height - 48f), 36f, 36f, borderPaint)

    // Top Cross Ornament
    val ornamentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.accentColor.toArgb()
        textSize = 64f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    }
    val contentTop = (height - (staticLayout.height + 260)) / 2f
    canvas.drawText("☩", width / 2f, contentTop, ornamentPaint)

    // Draw Scripture Text
    canvas.save()
    canvas.translate(paddingH.toFloat(), contentTop + 56f)
    staticLayout.draw(canvas)
    canvas.restore()

    // Reference Label
    val refY = contentTop + 56f + staticLayout.height + 76f
    val refPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.accentColor.toArgb()
        textSize = 34f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        letterSpacing = 0.14f
    }
    canvas.drawText(referenceLabel.uppercase(), width / 2f, refY, refPaint)

    // AGAPE Footer
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.textColor.copy(alpha = 0.55f).toArgb()
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        letterSpacing = 0.35f
    }
    canvas.drawText("A G A P E", width / 2f, height - 88f, footerPaint)

    runCatching {
        val dir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
        val file = File(dir, "agape_verse_card.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "$scriptureText\n— $referenceLabel")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, referenceLabel))
    }
}
