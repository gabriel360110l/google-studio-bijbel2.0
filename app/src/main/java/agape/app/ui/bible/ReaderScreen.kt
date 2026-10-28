package agape.app.ui.bible

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.Passage
import agape.app.core.prefs.ReadingPosition
import agape.app.data.bible.Verse
import agape.app.data.content.Book
import agape.app.data.content.chapterCount
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.LocalSettings
import agape.app.ui.bookName
import agape.app.ui.components.Ornament
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bible reader with:
 * - Multi-verse selection & batch highlight / copy / share / verse card image generator
 * - End-of-chapter completion & single Quiz card fixed at the very bottom of the text (never moving with scroll)
 * - Translucent floating chapter pill that automatically hides while scrolling and reappears when idle
 * - Quick Book & Chapter picker when tapping the chapter title
 * - Calm chapter audio reader (TTS)
 */
@Composable
fun ReaderScreen(
    book: String,
    chapter: Int,
    focusVerse: Int,
    onBack: () -> Unit,
    openChapter: (String, Int, Int) -> Unit,
    openReference: (String, Int, Int) -> Unit,
    openQuiz: (String, String) -> Unit,
) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val settings = LocalSettings.current
    val context = LocalContext.current
    val tradition = tradition
    val scope = rememberCoroutineScope()
    val c = AgapeTheme.colors
    val bookInfo = remember(book) { container.content.book(book) }
    val bookName = container.bookName(book, lang)
    val title = "$bookName $chapter"
    val chapterCount = bookInfo?.chapterCount(lang) ?: chapter

    val verses by produceState<List<Verse>?>(null, book, chapter, lang) {
        value = container.bible.chapter(book, chapter, lang)
    }
    val highlights by remember(book, chapter) { container.user.highlightsIn(book, chapter) }.collectAsStateWithLifecycle(emptyList())
    val bookmarks by container.user.bookmarks.collectAsStateWithLifecycle(emptyList())
    val notes by container.user.notes.collectAsStateWithLifecycle(emptyList())
    val read by container.user.chaptersRead.collectAsStateWithLifecycle(emptyList())
    val isRead = read.any { it.book == book && it.chapter == chapter }

    // Multi-verse selection state (sorted set of verse numbers)
    var selectedVerses by remember(book, chapter) { mutableStateOf(emptySet<Int>()) }
    var detailVerse by rememberSaveable { mutableStateOf<Int?>(null) }
    var showInfoSheet by rememberSaveable { mutableStateOf(false) }
    var showBookPicker by rememberSaveable { mutableStateOf(false) }
    var showImageCardSheet by rememberSaveable { mutableStateOf(false) }
    var flash by remember(book, chapter, focusVerse) { mutableStateOf(focusVerse) }
    var optionsMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val audioController = rememberChapterAudioController(
        verses = verses.orEmpty(),
        title = title,
        lang = lang,
    )

    // Hide the floating chapter pill while actively scrolling so it never distracts during reading
    val isScrolling = listState.isScrollInProgress
    val isAtEnd by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 2
        }
    }
    var showFloatingChapterPill by remember { mutableStateOf(true) }
    LaunchedEffect(isScrolling, isAtEnd) {
        if (isScrolling && !isAtEnd) {
            showFloatingChapterPill = false
        } else {
            delay(320)
            showFloatingChapterPill = true
        }
    }

    LaunchedEffect(book, chapter) {
        container.settings.setLastPosition(ReadingPosition(book, chapter, focusVerse.coerceAtLeast(1)))
    }
    LaunchedEffect(verses, focusVerse) {
        val vs = verses ?: return@LaunchedEffect
        if (focusVerse > 0) {
            val index = vs.indexOfFirst { it.verse == focusVerse }
            if (index >= 0) {
                listState.scrollToItem(index + 1)
                delay(1600)
                flash = 0
            }
        }
    }

    // Auto-scroll gently when audio reader advances to a verse
    val spokenVerse = audioController.currentVerseNumber
    LaunchedEffect(spokenVerse) {
        val vs = verses ?: return@LaunchedEffect
        if (spokenVerse != null && audioController.isPlaying) {
            val index = vs.indexOfFirst { it.verse == spokenVerse }
            if (index >= 0) {
                listState.animateScrollToItem(index + 1)
            }
        }
    }

    // Visual chapter reading progress calculation (0f..1f + current verse reached / total verses)
    val readingStats by remember(verses, spokenVerse) {
        derivedStateOf {
            val total = verses?.size ?: 0
            if (total == 0) {
                Triple(0f, 0, 0)
            } else {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) {
                    Triple(0f, 1, total)
                } else if (!listState.canScrollForward || visibleItems.last().index > total) {
                    Triple(1f, total, total)
                } else {
                    val lastVerseItem = visibleItems.lastOrNull { it.index in 1..total }
                    val scrollReachedVerse = lastVerseItem?.index?.coerceIn(1, total) ?: 1
                    val reachedVerse = if (spokenVerse != null) {
                        maxOf(scrollReachedVerse, spokenVerse.coerceIn(1, total))
                    } else {
                        scrollReachedVerse
                    }

                    val firstIndex = listState.firstVisibleItemIndex
                    val firstItem = visibleItems.firstOrNull()
                    val itemFraction = if (firstItem != null && firstItem.size > 0) {
                        (listState.firstVisibleItemScrollOffset.toFloat() / firstItem.size.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val visibleVerseCount = visibleItems.count { it.index in 1..total }.coerceAtLeast(1)
                    val scrollableVerses = (total - visibleVerseCount + 1).coerceAtLeast(1).toFloat()
                    val scrolledVerses = (firstIndex - 1 + itemFraction).coerceAtLeast(0f)
                    val scrollFraction = (scrolledVerses / scrollableVerses).coerceIn(0f, 1f)
                    val verseFraction = reachedVerse.toFloat() / total.toFloat()

                    val progress = if (firstIndex == 0 && listState.firstVisibleItemScrollOffset < 24 && spokenVerse == null) {
                        (verseFraction * 0.45f).coerceIn(0.03f, 1f)
                    } else {
                        maxOf(scrollFraction, verseFraction * 0.88f).coerceIn(0.03f, 0.99f)
                    }
                    Triple(progress, reachedVerse, total)
                }
            }
        }
    }
    val (rawReadingProgress, currentVerseReached, totalVersesCount) = readingStats
    val animatedReadingProgress by animateFloatAsState(
        targetValue = rawReadingProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 220),
        label = "chapter_reading_progress_anim",
    )
    val readingProgressPercent = (rawReadingProgress * 100).toInt().coerceIn(0, 100)

    Scaffold(
        containerColor = c.background,
        topBar = {
            Column(Modifier.fillMaxWidth().background(c.background)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(c.surface)
                            .border(1.dp, c.line, CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.back),
                            tint = c.ink,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    // Clickable chapter title -> opens Quick Book & Chapter Picker
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(c.surface.copy(alpha = 0.7f))
                            .border(1.dp, c.line.copy(alpha = 0.8f), CircleShape)
                            .clickable { showBookPicker = true }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = c.ink,
                        )
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = c.inkMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Chapter Audio (TTS) button
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (audioController.isActive) c.primary else c.surface)
                                .border(1.dp, if (audioController.isActive) c.primary else c.line, CircleShape)
                                .clickable {
                                    val firstSelected = selectedVerses.minOrNull()
                                    audioController.togglePlayPause(
                                        currentVerses = verses.orEmpty(),
                                        currentTitle = title,
                                        startAtVerse = firstSelected,
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.VolumeUp,
                                contentDescription = if (lang == AppLanguage.DUTCH) "Voorlezen" else "Read aloud",
                                tint = if (audioController.isActive) c.onPrimary else c.ink,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        // Font Scale menu
                        Box {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(c.surface)
                                    .border(1.dp, c.line, CircleShape)
                                    .clickable { optionsMenu = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.FormatSize,
                                    contentDescription = "Lettergrootte",
                                    tint = c.ink,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            DropdownMenu(optionsMenu, onDismissRequest = { optionsMenu = false }) {
                                listOf(
                                    0.9f to R.string.font_small,
                                    1f to R.string.font_normal,
                                    1.15f to R.string.font_large,
                                    1.3f to R.string.font_xlarge,
                                ).forEach { (scale, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(label),
                                                color = if (settings.fontScale == scale) c.primary else c.ink,
                                            )
                                        },
                                        onClick = {
                                            optionsMenu = false
                                            scope.launch { container.settings.setFontScale(scale) }
                                        },
                                    )
                                }
                            }
                        }

                        // Chapter Info button (Uitleg, Kerkvaders, Kruisverwijzingen)
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(c.surface)
                                .border(1.dp, c.line, CircleShape)
                                .clickable { showInfoSheet = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = stringResource(R.string.reader_info),
                                tint = c.ink,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = audioController.isActive) {
                    ChapterAudioBar(
                        controller = audioController,
                        title = title,
                        lang = lang,
                    )
                }

                ChapterReadingProgressStrip(
                    progress = animatedReadingProgress,
                    progressPercent = readingProgressPercent,
                    currentVerse = currentVerseReached,
                    totalVerses = totalVersesCount,
                    isChapterMarkedRead = isRead,
                    lang = lang,
                )
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 120.dp),
            ) {
                // Chapter heading + subtle multi-select hint
                item(key = "chapter_header") {
                    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = c.ink,
                            )
                            if (totalVersesCount > 0) {
                                val estMinutes = (totalVersesCount / 12).coerceAtLeast(1)
                                Row(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(c.surface)
                                        .border(1.dp, c.line, CircleShape)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.MenuBook,
                                        contentDescription = null,
                                        tint = c.primary,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = if (lang == AppLanguage.DUTCH) {
                                            "$totalVersesCount verzen · ~$estMinutes min"
                                        } else {
                                            "$totalVersesCount verses · ~$estMinutes min"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = c.inkMuted,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (lang == AppLanguage.DUTCH) {
                                "Tik op één of meerdere verzen om samen te markeren of te delen"
                            } else {
                                "Tap one or more verses to highlight or share together"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = c.inkFaint,
                        )
                    }
                }

                val vs = verses
                if (vs == null) {
                    item(key = "loading_spacer") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (lang == AppLanguage.DUTCH) "Bijbeltekst laden…" else "Loading Scripture…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.inkMuted,
                            )
                        }
                    }
                } else {
                    items(vs, key = { it.verse }) { v ->
                        val isSelected = v.verse in selectedVerses
                        val isSpoken = spokenVerse == v.verse
                        val hl = highlights.firstOrNull { it.verse == v.verse }?.color?.let { c.highlights.getOrNull(it) }
                        val target = when {
                            isSelected -> c.accent.copy(alpha = 0.22f)
                            isSpoken || flash == v.verse -> c.primary.copy(alpha = 0.14f)
                            hl != null -> hl
                            else -> Color.Transparent
                        }
                        val bg by animateColorAsState(target, label = "verse_bg")
                        val marked = bookmarks.any { it.book == book && it.chapter == chapter && it.verse == v.verse }
                        val hasNote = notes.any { it.book == book && it.chapter == chapter && it.verse == v.verse }
                        val verseLabel = stringResource(R.string.reader_verse_a11y, v.verse)

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .then(
                                    when {
                                        isSelected -> Modifier.border(1.dp, c.accent.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                                        isSpoken -> Modifier.border(1.dp, c.primary.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                        else -> Modifier
                                    },
                                )
                                .clickable {
                                    selectedVerses = if (isSelected) {
                                        selectedVerses - v.verse
                                    } else {
                                        selectedVerses + v.verse
                                    }
                                }
                                .semantics { contentDescription = "$verseLabel. ${v.text}" }
                                .padding(vertical = 6.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "${v.verse}",
                                style = AgapeTheme.type.verseNumber.copy(
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                ),
                                color = if (isSelected) c.primary else c.inkFaint,
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .width(26.dp)
                                    .padding(top = 6.dp, end = 8.dp),
                            )
                            Text(
                                buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            textDecoration = if (isSelected) TextDecoration.Underline else TextDecoration.None,
                                        ),
                                    ) {
                                        append(v.text)
                                    }
                                    if (marked || hasNote) {
                                        withStyle(SpanStyle(color = c.accent)) {
                                            append(if (marked) "  ◆" else "  ✎")
                                        }
                                    }
                                },
                                style = AgapeTheme.type.scripture.copy(lineHeight = 30.sp),
                                color = c.ink,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Fixed at the very bottom of the chapter text:
                    // "Markeer als gelezen" + SINGLE "Quiz" button (never moves with scroll!)
                    item(key = "chapter_completion_footer") {
                        ChapterCompletionFooter(
                            book = book,
                            bookName = bookName,
                            chapter = chapter,
                            isRead = isRead,
                            lastOfBook = bookInfo.takeIf { chapter == chapterCount },
                            lang = lang,
                            onToggleRead = {
                                scope.launch {
                                    container.user.markChapterRead(book, chapter, !isRead, "$bookName $chapter")
                                }
                            },
                            openQuiz = openQuiz,
                        )
                    }
                }
            }

            // Bottom Floating Overlay:
            // 1) If verses are selected -> Multi-Verse Selection Bar
            // 2) Otherwise -> Translucent Floating Chapter Pill (hides while scrolling!)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = selectedVerses.isNotEmpty(),
                    enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 2 },
                    exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 2 },
                ) {
                    val sortedNums = remember(selectedVerses) { selectedVerses.sorted() }
                    val selectedVerseObjs = remember(verses, sortedNums) {
                        val vs = verses.orEmpty()
                        sortedNums.mapNotNull { num -> vs.firstOrNull { it.verse == num } }
                    }
                    val refSummary = remember(bookName, chapter, sortedNums) {
                        formatMultiVerseReference(bookName, chapter, sortedNums)
                    }
                    val allShareHighlightColor = remember(highlights, sortedNums) {
                        if (sortedNums.isEmpty()) null
                        else {
                            val firstColor = highlights.firstOrNull { it.verse == sortedNums.first() }?.color
                            if (firstColor != null && sortedNums.all { vNum -> highlights.any { it.verse == vNum && it.color == firstColor } }) {
                                firstColor
                            } else {
                                null
                            }
                        }
                    }

                    MultiVerseSelectionBar(
                        referenceSummary = refSummary,
                        selectedCount = sortedNums.size,
                        activeColor = allShareHighlightColor,
                        lang = lang,
                        onSelectColor = { colorIndex ->
                            val targetColor = if (allShareHighlightColor == colorIndex) null else colorIndex
                            scope.launch {
                                sortedNums.forEach { vNum ->
                                    val label = container.passageLabel(Passage(book, chapter, vNum), lang)
                                    container.user.setHighlight(book, chapter, vNum, targetColor, label)
                                }
                                selectedVerses = emptySet()
                            }
                        },
                        onClearHighlight = {
                            scope.launch {
                                sortedNums.forEach { vNum ->
                                    val label = container.passageLabel(Passage(book, chapter, vNum), lang)
                                    container.user.setHighlight(book, chapter, vNum, null, label)
                                }
                                selectedVerses = emptySet()
                            }
                        },
                        onCopy = {
                            val formatted = formatSelectedVersesText(selectedVerseObjs, refSummary)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(refSummary, formatted))
                            Toast.makeText(
                                context,
                                if (lang == AppLanguage.DUTCH) "Verzen gekopieerd" else "Verses copied",
                                Toast.LENGTH_SHORT,
                            ).show()
                            selectedVerses = emptySet()
                        },
                        onShareText = {
                            val formatted = formatSelectedVersesText(selectedVerseObjs, refSummary)
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, formatted),
                                    refSummary,
                                ),
                            )
                        },
                        onCreateImageCard = {
                            showImageCardSheet = true
                        },
                        onOpenDetails = {
                            detailVerse = sortedNums.firstOrNull()
                        },
                        onClearSelection = {
                            selectedVerses = emptySet()
                        },
                    )
                }

                AnimatedVisibility(
                    visible = selectedVerses.isEmpty() && showFloatingChapterPill,
                    enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 2 },
                    exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 2 },
                ) {
                    TranslucentFloatingChapterPill(
                        bookName = bookName,
                        chapter = chapter,
                        progressPercent = readingProgressPercent,
                        canPrev = chapter > 1,
                        canNext = chapter < chapterCount,
                        onPrev = { if (chapter > 1) openChapter(book, chapter - 1, 0) },
                        onNext = { if (chapter < chapterCount) openChapter(book, chapter + 1, 0) },
                        onClickTitle = { showBookPicker = true },
                    )
                }
            }
        }
    }

    // Comprehensive Chapter Info Sheet (Uitleg, Kerkvaders, Kruisverwijzingen)
    if (showInfoSheet) {
        ChapterInfoSheet(
            book = book,
            chapter = chapter,
            onDismiss = { showInfoSheet = false },
            openReference = openReference,
        )
    }

    // Quick Book & Chapter Picker Sheet
    if (showBookPicker) {
        QuickBookChapterPickerSheet(
            currentBook = book,
            currentChapter = chapter,
            onSelect = { targetBook, targetChapter ->
                openChapter(targetBook, targetChapter, 0)
            },
            onDismiss = { showBookPicker = false },
        )
    }

    // Verse Image Card Generator Sheet
    if (showImageCardSheet && selectedVerses.isNotEmpty()) {
        val sortedNums = selectedVerses.sorted()
        val selectedObjs = verses.orEmpty().filter { it.verse in selectedVerses }.sortedBy { it.verse }
        val refLabel = formatMultiVerseReference(bookName, chapter, sortedNums)
        if (selectedObjs.isNotEmpty()) {
            VerseImageShareSheet(
                selectedVerses = selectedObjs,
                referenceLabel = refLabel,
                onDismiss = { showImageCardSheet = false },
            )
        }
    }

    // Detailed Verse Sheet (Kerkvaders, Kruisverwijzingen, Notities)
    detailVerse?.let { verse ->
        val v = verses?.firstOrNull { it.verse == verse } ?: return@let
        LaunchedEffect(verse) { container.settings.setLastPosition(ReadingPosition(book, chapter, verse)) }
        VerseSheet(
            verse = v,
            tradition = tradition,
            highlight = highlights.firstOrNull { it.verse == verse }?.color,
            bookmarked = bookmarks.any { it.book == book && it.chapter == chapter && it.verse == verse },
            note = notes.firstOrNull { it.book == book && it.chapter == chapter && it.verse == verse }?.text.orEmpty(),
            onDismiss = { detailVerse = null },
            openReference = { b, ch, vv -> detailVerse = null; openReference(b, ch, vv) },
        )
    }
}

/**
 * Visual chapter reading progress strip displayed at the bottom of the Reader top bar.
 * Shows the current verse reached out of total verses, the percentage read, and a smooth animated progress bar.
 */
@Composable
private fun ChapterReadingProgressStrip(
    progress: Float,
    progressPercent: Int,
    currentVerse: Int,
    totalVerses: Int,
    isChapterMarkedRead: Boolean,
    lang: AppLanguage,
) {
    val c = AgapeTheme.colors
    val a11yDescription = if (lang == AppLanguage.DUTCH) {
        "Leesvoortgang: vers $currentVerse van $totalVerses, $progressPercent procent gelezen"
    } else {
        "Reading progress: verse $currentVerse of $totalVerses, $progressPercent percent read"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chapter_reading_progress")
            .semantics { contentDescription = a11yDescription },
    ) {
        if (totalVerses > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (lang == AppLanguage.DUTCH) {
                        "Vers $currentVerse van $totalVerses"
                    } else {
                        "Verse $currentVerse of $totalVerses"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = c.inkMuted,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (isChapterMarkedRead || progressPercent >= 100) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = if (isChapterMarkedRead) c.accent else c.primary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(
                        text = "$progressPercent%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (progressPercent >= 100 || isChapterMarkedRead) c.primary else c.inkMuted,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(c.line.copy(alpha = 0.45f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(c.primary, c.accent),
                        ),
                    ),
            )
        }
    }
}

/**
 * Fixed end-of-chapter card placed at the very bottom of the LazyColumn.
 * Contains "Markeer als gelezen" and the SINGLE "Quiz" button so they never move while reading.
 */
@Composable
private fun ChapterCompletionFooter(
    book: String,
    bookName: String,
    chapter: Int,
    isRead: Boolean,
    lastOfBook: Book?,
    lang: AppLanguage,
    onToggleRead: () -> Unit,
    openQuiz: (String, String) -> Unit,
) {
    val c = AgapeTheme.colors
    val title = "$bookName $chapter"

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Ornament()
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (lang == AppLanguage.DUTCH) "Einde van $title" else "End of $title",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = c.ink,
        )
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. Markeer als gelezen
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isRead) c.accent.copy(alpha = 0.16f) else c.background)
                    .border(
                        1.dp,
                        if (isRead) c.accent else c.line,
                        RoundedCornerShape(14.dp),
                    )
                    .clickable(onClick = onToggleRead)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = if (isRead) Icons.Outlined.CheckCircle else Icons.Outlined.CheckCircleOutline,
                    contentDescription = null,
                    tint = if (isRead) c.accent else c.inkMuted,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isRead) stringResource(R.string.reader_read_done) else stringResource(R.string.reader_mark_read),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isRead) c.accent else c.ink,
                )
            }

            // 2. Single Quiz button (shown only once!)
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.background)
                    .border(1.dp, c.line, RoundedCornerShape(14.dp))
                    .clickable { openQuiz("chapter:$book.$chapter", title) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Quiz,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.reader_take_quiz),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = c.ink,
                )
            }
        }

        // Optional Book-End Quiz on the final chapter of a book
        lastOfBook?.let { b ->
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.primary.copy(alpha = 0.10f))
                    .border(1.dp, c.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable { openQuiz("book:${b.id}", b.name[lang]) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Quiz,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.book_end_quiz),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = c.primary,
                )
            }
        }
    }
}

/**
 * Translucent, floating pill for switching chapters (`< Boek Hoofdstuk >`).
 * Auto-hides while scrolling so it never distracts during reading.
 */
@Composable
private fun TranslucentFloatingChapterPill(
    bookName: String,
    chapter: Int,
    progressPercent: Int,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClickTitle: () -> Unit,
) {
    val c = AgapeTheme.colors
    Surface(
        shape = CircleShape,
        color = c.surface.copy(alpha = 0.80f),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, c.line.copy(alpha = 0.65f)),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(
                onClick = onPrev,
                enabled = canPrev,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBackIos,
                    contentDescription = "Vorig hoofdstuk",
                    tint = if (canPrev) c.ink else c.inkFaint.copy(alpha = 0.3f),
                    modifier = Modifier.size(15.dp),
                )
            }

            Row(
                Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClickTitle)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "$bookName $chapter",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = c.ink,
                )
                Text(
                    text = "· $progressPercent%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = c.primary,
                )
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = c.inkMuted,
                    modifier = Modifier.size(16.dp),
                )
            }

            IconButton(
                onClick = onNext,
                enabled = canNext,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForwardIos,
                    contentDescription = "Volgend hoofdstuk",
                    tint = if (canNext) c.ink else c.inkFaint.copy(alpha = 0.3f),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * Floating action bar shown when 1 or more verses are selected.
 * Supports batch highlighting, copying, sharing text, creating a verse image card, or opening commentary.
 */
@Composable
private fun MultiVerseSelectionBar(
    referenceSummary: String,
    selectedCount: Int,
    activeColor: Int?,
    lang: AppLanguage,
    onSelectColor: (Int) -> Unit,
    onClearHighlight: () -> Unit,
    onCopy: () -> Unit,
    onShareText: () -> Unit,
    onCreateImageCard: () -> Unit,
    onOpenDetails: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val c = AgapeTheme.colors
    val names = listOf(R.string.color_gold, R.string.color_blue, R.string.color_green, R.string.color_rose)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = c.surface.copy(alpha = 0.96f),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, c.accent.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Top Row: Selected reference + count & Clear selection button
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = referenceSummary,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = c.ink,
                    )
                    Text(
                        text = if (lang == AppLanguage.DUTCH) {
                            "$selectedCount ${if (selectedCount == 1) "vers" else "verzen"} geselecteerd"
                        } else {
                            "$selectedCount ${if (selectedCount == 1) "verse" else "verses"} selected"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkMuted,
                    )
                }

                // Highlight Color Circles
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    c.highlights.forEachIndexed { idx, color ->
                        val chosen = activeColor == idx
                        val desc = stringResource(names.getOrElse(idx) { R.string.color_gold })
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.92f))
                                .border(
                                    width = if (chosen) 2.5.dp else 1.dp,
                                    color = if (chosen) c.primary else c.line,
                                    shape = CircleShape,
                                )
                                .clickable { onSelectColor(idx) }
                                .semantics { contentDescription = desc },
                        )
                    }
                    // Clear highlight
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(1.dp, c.line, CircleShape)
                            .clickable(onClick = onClearHighlight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.verse_clear_highlight),
                            tint = c.inkMuted,
                            modifier = Modifier.size(14.dp),
                        )
                    }

                    Spacer(Modifier.width(2.dp))

                    IconButton(
                        onClick = onClearSelection,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = c.ink,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Bottom Row: Action Buttons (Copy, Share Text, Verse Card Image, Commentary/Note)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionActionChip(
                    icon = Icons.Outlined.ContentCopy,
                    label = if (lang == AppLanguage.DUTCH) "Kopieer" else "Copy",
                    onClick = onCopy,
                )
                SelectionActionChip(
                    icon = Icons.Outlined.Share,
                    label = if (lang == AppLanguage.DUTCH) "Deel tekst" else "Share text",
                    onClick = onShareText,
                )
                SelectionActionChip(
                    icon = Icons.Outlined.Image,
                    label = if (lang == AppLanguage.DUTCH) "Vers-kaart" else "Verse card",
                    onClick = onCreateImageCard,
                )
                SelectionActionChip(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    label = if (lang == AppLanguage.DUTCH) "Uitleg & Notitie" else "Commentary & Note",
                    onClick = onOpenDetails,
                )
            }
        }
    }
}

@Composable
private fun SelectionActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val c = AgapeTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.background)
            .border(1.dp, c.line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = c.ink,
        )
    }
}

/**
 * Formats a sorted list of verse numbers into compact scripture ranges, e.g. "Johannes 1:1–3, 5".
 */
private fun formatMultiVerseReference(bookName: String, chapter: Int, sortedVerses: List<Int>): String {
    if (sortedVerses.isEmpty()) return "$bookName $chapter"
    val ranges = mutableListOf<String>()
    var rangeStart = sortedVerses.first()
    var prev = rangeStart
    for (i in 1 until sortedVerses.size) {
        val curr = sortedVerses[i]
        if (curr == prev + 1) {
            prev = curr
        } else {
            ranges += if (rangeStart == prev) "$rangeStart" else "$rangeStart–$prev"
            rangeStart = curr
            prev = curr
        }
    }
    ranges += if (rangeStart == prev) "$rangeStart" else "$rangeStart–$prev"
    return "$bookName $chapter:${ranges.joinToString(", ")}"
}

private fun formatSelectedVersesText(verses: List<Verse>, referenceLabel: String): String {
    val body = if (verses.size == 1) {
        "“${verses.first().text.trim()}”"
    } else {
        verses.joinToString("\n") { "[${it.verse}] ${it.text.trim()}" }
    }
    return "$body\n— $referenceLabel"
}
