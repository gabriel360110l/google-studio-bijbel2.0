package agape.app.ui.components

import agape.app.core.model.AppLanguage
import agape.app.ui.LocalLanguage
import agape.app.ui.theme.AgapeTheme
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Locale

@Stable
class AgapeSpeakerState internal constructor(
    private val ttsHolder: () -> TextToSpeech?,
    private val lang: AppLanguage,
) {
    var isSpeaking by mutableStateOf(false)
        internal set

    fun toggle(text: String) {
        if (isSpeaking) {
            stop()
        } else {
            speak(text)
        }
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val tts = ttsHolder() ?: return
        val locale = if (lang == AppLanguage.DUTCH) Locale("nl", "NL") else Locale.ENGLISH
        tts.language = locale
        tts.setSpeechRate(0.94f)
        tts.stop()

        val chunks = clean
            .split(Regex("(?<=[.!?\\n])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (chunks.isEmpty()) return
        isSpeaking = true
        chunks.forEachIndexed { index, chunk ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = if (index == chunks.lastIndex) "agape_done" else "agape_$index"
            tts.speak(chunk, mode, null, utteranceId)
        }
    }

    fun stop() {
        ttsHolder()?.stop()
        isSpeaking = false
    }
}

@Composable
fun rememberAgapeSpeaker(): AgapeSpeakerState {
    val context = LocalContext.current
    val lang = LocalLanguage.current
    var ttsRef by remember { mutableStateOf<TextToSpeech?>(null) }
    val state = remember(lang) { AgapeSpeakerState({ ttsRef }, lang) }

    DisposableEffect(context, lang) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = if (lang == AppLanguage.DUTCH) Locale("nl", "NL") else Locale.ENGLISH
                instance?.language = locale
                instance?.setSpeechRate(0.94f)
                instance?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        state.isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "agape_done") {
                            state.isSpeaking = false
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        state.isSpeaking = false
                    }
                })
            }
        }
        ttsRef = instance
        onDispose {
            runCatching {
                instance?.stop()
                instance?.shutdown()
            }
            ttsRef = null
            state.isSpeaking = false
        }
    }
    return state
}

@Composable
fun SpeakerButton(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = AgapeTheme.colors.primary,
) {
    val speaker = rememberAgapeSpeaker()
    val lang = LocalLanguage.current
    val nl = lang == AppLanguage.DUTCH
    IconButton(
        onClick = { speaker.toggle(text) },
        modifier = modifier.testTag("speaker_button"),
    ) {
        Icon(
            imageVector = if (speaker.isSpeaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = if (speaker.isSpeaking) {
                if (nl) "Stop voorlezen" else "Stop reading aloud"
            } else {
                if (nl) "Voorlezen" else "Read aloud"
            },
            tint = if (speaker.isSpeaking) AgapeTheme.colors.accent else tint,
        )
    }
}

@Composable
fun SpeakerPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val speaker = rememberAgapeSpeaker()
    val lang = LocalLanguage.current
    val nl = lang == AppLanguage.DUTCH
    val c = AgapeTheme.colors
    val active = speaker.isSpeaking
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (active) c.primary.copy(alpha = 0.16f) else c.surfaceSunken)
            .border(1.dp, if (active) c.primary else c.line, CircleShape)
            .clickable { speaker.toggle(text) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("speaker_pill_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (active) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = null,
            tint = if (active) c.primary else c.inkMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = if (active) (if (nl) "Stop audio" else "Stop audio") else (if (nl) "Luister" else "Listen"),
            style = MaterialTheme.typography.labelMedium,
            color = if (active) c.primary else c.inkMuted,
        )
    }
}
