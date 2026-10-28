package agape.app.ui.components

import agape.app.core.model.AppLanguage
import agape.app.ui.LocalLanguage
import agape.app.ui.theme.AgapeTheme
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Calm, contemplative playback speeds tailored for Scripture and liturgical prayer.
 */
enum class AgapeSpeechSpeed(
    val rate: Float,
    val badge: String,
    val labelNl: String,
    val labelEn: String,
) {
    CALM(0.82f, "0.82×", "Rustig", "Calm"),
    NATURAL(0.90f, "0.90×", "Natuurlijk", "Natural"),
    NORMAL(1.00f, "1.0×", "Normaal", "Normal");

    fun next(): AgapeSpeechSpeed = when (this) {
        CALM -> NATURAL
        NATURAL -> NORMAL
        NORMAL -> CALM
    }

    fun label(lang: AppLanguage): String = if (lang == AppLanguage.DUTCH) labelNl else labelEn
}

data class SpeechPhrase(
    val text: String,
    val pauseAfterMs: Long,
)

data class SpokenPrayerItem(
    val index: Int,
    val label: String,
    val text: String,
)

/**
 * Shared Natural Human Voice engine that uses TalkifyTTS's Microsoft Neural Speech engine
 * (`nl-NL-MaartenNeural`, `nl-BE-ArnaudNeural`, `en-US-AndrewMultilingualNeural`, `en-GB-RyanNeural`)
 * as the primary voice, with offline Android `TextToSpeech` as automatic fallback.
 */
object AgapeNaturalVoice {
    private val preferredDutchMaleTags = listOf(
        "nl-nl-x-dma",
        "nl-nl-x-bma",
        "nl-be-x-bed",
        "nl-nl-x-dfm",
        "male",
    )

    private val preferredEnglishMaleTags = listOf(
        "en-us-x-iom",
        "en-us-x-tpd",
        "en-gb-x-rjs",
        "en-gb-x-gbd",
        "en-us-x-sfg",
        "male",
    )

    private val knownFemaleTags = listOf(
        "female",
        "nl-nl-x-cma",
        "nl-nl-x-ema",
        "nl-be-x-bec",
        "en-us-x-iob",
        "en-us-x-tpf",
        "en-us-x-sfg-female",
        "en-gb-x-gba",
        "en-gb-x-gbc",
    )

    fun createTts(
        context: Context,
        onInit: (TextToSpeech?, Int) -> Unit,
    ): TextToSpeech {
        val appContext = context.applicationContext
        var holder: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            onInit(holder, status)
        }
        holder = TextToSpeech(appContext, listener)
        return holder
    }

    fun configureWarmMaleVoice(
        engine: TextToSpeech,
        lang: AppLanguage,
        speed: AgapeSpeechSpeed = AgapeSpeechSpeed.NATURAL,
    ) {
        runCatching {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )

            val preferredLocale = if (lang == AppLanguage.DUTCH) Locale("nl", "NL") else Locale.US
            val fallbackNl = Locale("nl")
            val targetLangCode = if (lang == AppLanguage.DUTCH) "nl" else "en"

            val langResult = when {
                engine.isLanguageAvailable(preferredLocale) >= TextToSpeech.LANG_AVAILABLE ->
                    engine.setLanguage(preferredLocale)
                lang == AppLanguage.DUTCH && engine.isLanguageAvailable(fallbackNl) >= TextToSpeech.LANG_AVAILABLE ->
                    engine.setLanguage(fallbackNl)
                engine.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE ->
                    engine.setLanguage(Locale.US)
                else -> engine.setLanguage(Locale.getDefault())
            }

            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.defaultVoice?.locale?.let { engine.setLanguage(it) }
            }

            val candidateVoices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
                .filter { v ->
                    v.locale != null &&
                        v.locale.language.equals(targetLangCode, ignoreCase = true) &&
                        !v.isNetworkConnectionRequired &&
                        (v.features == null || !v.features.contains("notInstalled"))
                }

            val bestMaleVoice = candidateVoices.maxByOrNull { voice ->
                scoreVoiceForWarmMale(voice, lang)
            }

            var matchedExplicitMaleVoice = false
            if (bestMaleVoice != null) {
                runCatching {
                    engine.voice = bestMaleVoice
                    val idLower = (bestMaleVoice.name + " " + bestMaleVoice.features.orEmpty().joinToString(" ")).lowercase()
                    matchedExplicitMaleVoice = preferredDutchMaleTags.any { it in idLower } ||
                        preferredEnglishMaleTags.any { it in idLower }
                }
            }

            val tunedPitch = if (matchedExplicitMaleVoice) 0.95f else 0.89f
            engine.setPitch(tunedPitch)
            engine.setSpeechRate(speed.rate)
        }
    }

    private fun scoreVoiceForWarmMale(voice: Voice, lang: AppLanguage): Int {
        var score = 0
        val nameLower = voice.name.lowercase()
        val featuresLower = voice.features.orEmpty().joinToString(" ").lowercase()
        val combined = "$nameLower $featuresLower"

        val country = voice.locale.country.uppercase()
        if (lang == AppLanguage.DUTCH && country == "NL") score += 120
        if (lang == AppLanguage.ENGLISH && (country == "GB" || country == "US")) score += 100

        score += when (voice.quality) {
            Voice.QUALITY_VERY_HIGH -> 500
            Voice.QUALITY_HIGH -> 380
            Voice.QUALITY_NORMAL -> 200
            else -> 50
        }
        if ("network" in combined || "neural" in combined || "wavenet" in combined || "-x-" in nameLower) {
            score += 220
        }

        val maleTags = if (lang == AppLanguage.DUTCH) preferredDutchMaleTags else preferredEnglishMaleTags
        maleTags.forEachIndexed { idx, tag ->
            if (tag in combined) {
                score += (450 - idx * 35).coerceAtLeast(250)
            }
        }
        if (knownFemaleTags.any { it in combined }) {
            score -= 600
        }
        return score
    }

    fun normalizeForHumanSpeech(input: String, lang: AppLanguage): String {
        var text = input
            .replace("Κύριε ἐλέησον", "Kyrie eleison")
            .replace("ΚΥΡΙΕ ΕΛΕΗΣΟΝ", "Kyrie eleison")
            .replace("•", " ")
            .replace("·", ", ")
            .replace("—", ", ")
            .replace("–", ", ")
            .replace(Regex("\\s+"), " ")
            .trim()

        text = text.replace(Regex("([.!?])\\s*(Amen\\.?)$", RegexOption.IGNORE_CASE), "$1 $2")
        if (lang == AppLanguage.DUTCH) {
            text = text.replace(Regex("\\bJoh\\."), "Johannes")
                .replace(Regex("\\bMatt\\."), "Matteüs")
                .replace(Regex("\\bPs\\."), "Psalm")
        }
        return text
    }

    fun speakOffline(
        engine: TextToSpeech,
        rawText: String,
        lang: AppLanguage,
        finalUtteranceId: String,
    ): Int {
        val cleaned = normalizeForHumanSpeech(rawText, lang)
        if (cleaned.isBlank()) return TextToSpeech.ERROR
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        return runCatching {
            engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, params, finalUtteranceId)
        }.getOrDefault(TextToSpeech.ERROR)
    }
}

/**
 * Stateful audio controller for prayers, canonical hours, and devotional texts.
 * Powered by TalkifyTTS's Microsoft Neural Speech engine (`nl-NL-MaartenNeural` / `en-US-AndrewMultilingualNeural`)
 * with pre-fetching and automatic offline Android TTS fallback.
 */
@Stable
class AgapeSpeakerState internal constructor(
    private val context: Context,
    private val lang: AppLanguage,
) {
    var isSpeaking by mutableStateOf(false)
        internal set
    var isActive by mutableStateOf(false)
        internal set
    var currentIndex by mutableIntStateOf(0)
        internal set
    var speed by mutableStateOf(AgapeSpeechSpeed.NATURAL)
        internal set
    var voiceProfile by mutableStateOf(TalkifyVoiceProfile.MAARTEN_ANDREW)
        internal set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var fallbackRunnable: Runnable? = null
    private var items: List<SpokenPrayerItem> = emptyList()
    private var prayerTitle: String = ""
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsSupported = true
    private var pendingOfflineFallback = false

    val currentItem: SpokenPrayerItem?
        get() = if (isActive) items.getOrNull(currentIndex) else null

    val totalItems: Int
        get() = items.size

    init {
        runCatching {
            tts = AgapeNaturalVoice.createTts(context) { engine, status ->
                mainHandler.post {
                    if (status == TextToSpeech.SUCCESS && engine != null) {
                        tts = engine
                        ttsReady = true
                        configureOfflineTts()
                        if (isSpeaking && pendingOfflineFallback) {
                            pendingOfflineFallback = false
                            items.getOrNull(currentIndex)?.let { speakWithOfflineTts(it, currentIndex) }
                        }
                    } else {
                        ttsReady = false
                        ttsSupported = false
                    }
                }
            }
        }.onFailure {
            ttsSupported = false
        }
    }

    private fun configureOfflineTts() {
        val engine = tts ?: return
        AgapeNaturalVoice.configureWarmMaleVoice(engine, lang, speed)
        runCatching {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    val idx = utteranceId?.removePrefix("pray_done_")?.toIntOrNull() ?: return
                    mainHandler.post {
                        if (isSpeaking && idx == currentIndex) {
                            advanceOrFinish()
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        if (isSpeaking) scheduleFallbackAdvance()
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    mainHandler.post {
                        if (isSpeaking) scheduleFallbackAdvance()
                    }
                }
            })
        }
    }

    fun bindItems(newItems: List<SpokenPrayerItem>, title: String = "") {
        val filtered = newItems.filter { it.text.isNotBlank() }
        if (filtered.isEmpty()) return
        val sameContent = items.size == filtered.size &&
            items.firstOrNull()?.text == filtered.firstOrNull()?.text &&
            prayerTitle == title
        if (!sameContent) {
            stop()
            items = filtered
            prayerTitle = title
            currentIndex = 0
        } else {
            items = filtered
            prayerTitle = title
        }
    }

    fun toggle(text: String) {
        if (isSpeaking) {
            pause()
        } else {
            if (items.isEmpty() || (items.size == 1 && items.first().text != text.trim())) {
                val paragraphs = text.trim()
                    .split(Regex("\\n{2,}|(?<=\\.)\\s+(?=[A-ZÀ-Ý])"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .ifEmpty { listOf(text.trim()) }
                items = paragraphs.mapIndexed { idx, para ->
                    SpokenPrayerItem(
                        index = idx,
                        label = "${idx + 1}/${paragraphs.size}",
                        text = para,
                    )
                }
                currentIndex = 0
            }
            if (items.isEmpty()) return
            isActive = true
            isSpeaking = true
            speakCurrentItem()
        }
    }

    fun togglePlayPause(startAtIndex: Int? = null) {
        if (items.isEmpty()) return
        if (startAtIndex != null && startAtIndex in items.indices) {
            currentIndex = startAtIndex
            isActive = true
            isSpeaking = true
            speakCurrentItem()
            return
        }
        if (isSpeaking) {
            pause()
        } else {
            isActive = true
            isSpeaking = true
            speakCurrentItem()
        }
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        items = listOf(SpokenPrayerItem(0, "", clean))
        currentIndex = 0
        isActive = true
        isSpeaking = true
        speakCurrentItem()
    }

    fun pause() {
        isSpeaking = false
        cancelPlaybackResources()
    }

    fun previousItem() {
        if (items.isEmpty()) return
        cancelPlaybackResources()
        currentIndex = (currentIndex - 1).coerceAtLeast(0)
        if (isSpeaking) speakCurrentItem()
    }

    fun nextItem() {
        if (items.isEmpty()) return
        cancelPlaybackResources()
        if (currentIndex + 1 < items.size) {
            currentIndex += 1
            if (isSpeaking) speakCurrentItem()
        } else {
            stop()
        }
    }

    fun cycleSpeed() {
        speed = speed.next()
        tts?.let { engine ->
            runCatching { engine.setSpeechRate(speed.rate) }
        }
        if (isSpeaking) {
            speakCurrentItem()
        }
    }

    fun cycleVoiceProfile() {
        voiceProfile = voiceProfile.next()
        if (isSpeaking) {
            speakCurrentItem()
        }
    }

    fun stop() {
        isSpeaking = false
        isActive = false
        currentIndex = 0
        cancelPlaybackResources()
    }

    private fun advanceOrFinish() {
        cancelPlaybackResources()
        if (isSpeaking && currentIndex + 1 < items.size) {
            currentIndex += 1
            speakCurrentItem()
        } else {
            isSpeaking = false
            isActive = false
            currentIndex = 0
        }
    }

    private fun cancelPlaybackResources() {
        pendingOfflineFallback = false
        activeJob?.cancel()
        activeJob = null
        fallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        fallbackRunnable = null
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        runCatching { tts?.stop() }
    }

    private fun scheduleFallbackAdvance() {
        fallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        val item = items.getOrNull(currentIndex) ?: return
        val words = item.text.split(Regex("\\s+")).size.coerceAtLeast(6)
        val durationMs = (words * 260L).coerceIn(2800L, 12000L)
        val r = Runnable {
            if (isSpeaking) {
                advanceOrFinish()
            }
        }
        fallbackRunnable = r
        mainHandler.postDelayed(r, durationMs)
    }

    private fun speakCurrentItem() {
        cancelPlaybackResources()
        val idx = currentIndex
        val item = items.getOrNull(idx) ?: return

        activeJob = scope.launch {
            val audioResult = TalkifyNeuralSynthesizer.synthesizeToFile(
                context = context,
                text = item.text,
                lang = lang,
                speed = speed,
                voiceProfile = voiceProfile,
            )
            if (!isSpeaking || currentIndex != idx) return@launch

            if (audioResult != null) {
                // Only prefetch the next stanza AFTER the current stanza is ready to play
                items.getOrNull(idx + 1)?.let { next ->
                    TalkifyNeuralSynthesizer.prefetchInBackground(context, next.text, lang, speed, voiceProfile)
                }
                mediaPlayer = TalkifyNeuralSynthesizer.playMp3Result(
                    result = audioResult,
                    speed = speed,
                    voiceProfile = voiceProfile,
                    onFinished = {
                        scope.launch {
                            delay(480L)
                            if (isSpeaking && currentIndex == idx) {
                                advanceOrFinish()
                            }
                        }
                    },
                    onError = {
                        mainHandler.post {
                            if (isSpeaking && currentIndex == idx) {
                                speakWithOfflineTts(item, idx)
                            }
                        }
                    },
                )
            } else {
                speakWithOfflineTts(item, idx)
            }
        }
    }

    private fun speakWithOfflineTts(item: SpokenPrayerItem, idx: Int) {
        val engine = tts
        if (engine == null || !ttsSupported) {
            scheduleFallbackAdvance()
            return
        }
        if (!ttsReady) {
            pendingOfflineFallback = true
            scheduleFallbackAdvance()
            return
        }
        runCatching { engine.setSpeechRate(speed.rate) }
        val res = AgapeNaturalVoice.speakOffline(
            engine = engine,
            rawText = item.text,
            lang = lang,
            finalUtteranceId = "pray_done_$idx",
        )
        if (res == TextToSpeech.ERROR) {
            scheduleFallbackAdvance()
        }
    }

    fun release() {
        cancelPlaybackResources()
        scope.cancel()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        isSpeaking = false
        isActive = false
    }
}

@Composable
fun rememberAgapeSpeaker(
    items: List<SpokenPrayerItem> = emptyList(),
    title: String = "",
): AgapeSpeakerState {
    val context = LocalContext.current
    val lang = LocalLanguage.current
    val state = remember(lang) { AgapeSpeakerState(context, lang) }

    LaunchedEffect(items, title) {
        if (items.isNotEmpty()) {
            state.bindItems(items, title)
        }
    }

    DisposableEffect(state) {
        onDispose { state.release() }
    }
    return state
}

@Composable
fun SpeakerButton(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = AgapeTheme.colors.primary,
    speakerState: AgapeSpeakerState? = null,
) {
    val speaker = speakerState ?: rememberAgapeSpeaker()
    val lang = LocalLanguage.current
    val nl = lang == AppLanguage.DUTCH
    IconButton(
        onClick = {
            if (speakerState != null) {
                speaker.togglePlayPause()
            } else {
                speaker.toggle(text)
            }
        },
        modifier = modifier.testTag("speaker_button"),
    ) {
        Icon(
            imageVector = if (speaker.isSpeaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = if (speaker.isSpeaking) {
                if (nl) "Pauzeer voorlezen" else "Pause reading aloud"
            } else {
                if (nl) "Voorlezen met TalkifyTTS mannenstem" else "Read aloud with TalkifyTTS male voice"
            },
            tint = if (speaker.isSpeaking || speaker.isActive) AgapeTheme.colors.accent else tint,
        )
    }
}

@Composable
fun SpeakerPill(
    text: String,
    modifier: Modifier = Modifier,
    speakerState: AgapeSpeakerState? = null,
) {
    val speaker = speakerState ?: rememberAgapeSpeaker()
    val lang = LocalLanguage.current
    val nl = lang == AppLanguage.DUTCH
    val c = AgapeTheme.colors
    val active = speaker.isSpeaking
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (active) c.primary.copy(alpha = 0.16f) else c.surfaceSunken)
            .border(1.dp, if (active) c.primary else c.line, CircleShape)
            .clickable {
                if (speakerState != null) {
                    speaker.togglePlayPause()
                } else {
                    speaker.toggle(text)
                }
            }
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag("speaker_pill_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (active) Icons.Outlined.Pause else Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = null,
            tint = if (active) c.primary else c.inkMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = if (active) {
                if (nl) "Pauzeer stem" else "Pause voice"
            } else if (speaker.isActive) {
                if (nl) "Hervat stem" else "Resume voice"
            } else {
                if (nl) "Luister · ${speaker.voiceProfile.shortName(lang)}" else "Listen · ${speaker.voiceProfile.shortName(lang)}"
            },
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = if (active) c.primary else c.inkMuted,
        )
    }
}

/**
 * Interactive audio control bar for Canonical Hours and Daily Prayers powered by TalkifyTTS Neural Voices.
 */
@Composable
fun PrayerAudioBar(
    speaker: AgapeSpeakerState,
    title: String,
    lang: AppLanguage,
    modifier: Modifier = Modifier,
) {
    val c = AgapeTheme.colors
    val nl = lang == AppLanguage.DUTCH
    val current = speaker.currentItem
    val stepBadge = if (speaker.totalItems > 1) {
        "${speaker.currentIndex + 1}/${speaker.totalItems}"
    } else {
        ""
    }
    val headerLabel = buildString {
        append(speaker.voiceProfile.shortName(lang).uppercase())
        if (current?.label?.isNotBlank() == true) {
            append(" · ").append(current.label.uppercase())
        } else if (stepBadge.isNotBlank()) {
            append(" · ").append(stepBadge)
        }
    }

    Surface(
        color = c.surface,
        border = BorderStroke(0.5.dp, c.line),
        modifier = modifier.fillMaxWidth().testTag("prayer_audio_bar"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = headerLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.0.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = c.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(c.surfaceSunken)
                            .border(0.8.dp, c.line, CircleShape)
                            .clickable { speaker.cycleVoiceProfile() }
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                            .testTag("prayer_audio_voice_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            Icons.Outlined.RecordVoiceOver,
                            contentDescription = null,
                            tint = c.primary,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = if (nl) "Stem" else "Voice",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                            color = c.primary,
                        )
                    }
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(c.surfaceSunken)
                            .border(0.8.dp, c.line, CircleShape)
                            .clickable { speaker.cycleSpeed() }
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                            .testTag("prayer_audio_speed_button"),
                    ) {
                        Text(
                            text = "${speaker.speed.label(lang)} ${speaker.speed.badge}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                            color = c.primary,
                        )
                    }
                }
                val snippet = current?.text?.ifBlank { title } ?: title
                if (snippet.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (speaker.totalItems > 1) {
                    IconButton(onClick = { speaker.previousItem() }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Outlined.SkipPrevious,
                            contentDescription = if (nl) "Vorige sectie" else "Previous section",
                            tint = c.ink,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(c.primary)
                        .clickable { speaker.togglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (speaker.isSpeaking) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (speaker.isSpeaking) {
                            if (nl) "Pauze" else "Pause"
                        } else {
                            if (nl) "Afspelen" else "Play"
                        },
                        tint = c.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (speaker.totalItems > 1) {
                    IconButton(onClick = { speaker.nextItem() }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Outlined.SkipNext,
                            contentDescription = if (nl) "Volgende sectie" else "Next section",
                            tint = c.ink,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                IconButton(onClick = { speaker.stop() }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Outlined.Stop,
                        contentDescription = if (nl) "Stop voorlezen" else "Stop audio",
                        tint = c.inkMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
