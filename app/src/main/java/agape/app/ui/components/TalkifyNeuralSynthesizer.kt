package agape.app.ui.components

import agape.app.core.model.AppLanguage
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Curated Neural Voices supported by TalkifyTTS's Microsoft Azure ReadAloud Neural Engine.
 * Default is the warm, calm male narrator for both Dutch (`nl-NL-MaartenNeural`) and English (`en-US-AndrewMultilingualNeural`).
 */
enum class TalkifyVoiceProfile(
    val idNl: String,
    val idEn: String,
    val labelNl: String,
    val labelEn: String,
    val defaultPitchHz: String,
    val fallbackPitchMultiplier: Float,
) {
    MAARTEN_ANDREW(
        idNl = "nl-NL-MaartenNeural",
        idEn = "en-US-AndrewMultilingualNeural",
        labelNl = "Maarten (Warme Mannenstem)",
        labelEn = "Andrew (Warm Male)",
        defaultPitchHz = "-2Hz",
        fallbackPitchMultiplier = 0.86f,
    ),
    ARNAUD_BRIAN(
        idNl = "nl-BE-ArnaudNeural",
        idEn = "en-US-BrianMultilingualNeural",
        labelNl = "Arnaud (Rustige Mannenstem)",
        labelEn = "Brian (Calm Male)",
        defaultPitchHz = "-1Hz",
        fallbackPitchMultiplier = 0.90f,
    ),
    RYAN_CLASSIC(
        idNl = "nl-NL-MaartenNeural",
        idEn = "en-GB-RyanNeural",
        labelNl = "Maarten · Diep",
        labelEn = "Ryan (British Male)",
        defaultPitchHz = "-4Hz",
        fallbackPitchMultiplier = 0.82f,
    );

    fun voiceId(lang: AppLanguage): String = if (lang == AppLanguage.DUTCH) idNl else idEn
    fun shortName(lang: AppLanguage): String = if (lang == AppLanguage.DUTCH) labelNl else labelEn

    fun next(): TalkifyVoiceProfile = when (this) {
        MAARTEN_ANDREW -> ARNAUD_BRIAN
        ARNAUD_BRIAN -> RYAN_CLASSIC
        RYAN_CLASSIC -> MAARTEN_ANDREW
    }
}

data class SynthesizedAudioResult(
    val file: File,
    val isAlreadyMaleVoice: Boolean,
)

/**
 * Ported from LonePheasantWarrior/TalkifyTTS (`AzureProvider.kt`).
 *
 * Uses a persistent WebSocket connection to Microsoft's Neural ReadAloud endpoint
 * (`wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1`)
 * authenticated via [TalkifyAzureDrm.generateSecMsGecV2] (`Sec-MS-GEC-Version=2-143.0.3650.75`),
 * with automatic clock-skew correction and HTTPS neural stream fallback.
 */
object TalkifyNeuralSynthesizer {
    private const val BASE_URL = "speech.platform.bing.com/consumer/speech/synthesize/readaloud"
    private const val DEFAULT_WSS_URL =
        "wss://$BASE_URL/edge/v1?TrustedClientToken=${TalkifyAzureDrm.TRUSTED_CLIENT_TOKEN}"

    private val random = Random()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionMutex = Mutex()
    private val synthesisMutex = Mutex()
    private val inFlightRequests = ConcurrentHashMap<String, CompletableDeferred<ByteArray>>()

    @Volatile
    private var persistentWs: WebSocket? = null

    @Volatile
    private var persistentListener: TalkifyWsListener? = null

    @Volatile
    private var isAlive = false

    /** Clock offset (seconds) between server Date header and local device clock */
    @Volatile
    private var clockSkewSeconds: Double = 0.0

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    private val utcDateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(
            "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
            Locale.US,
        ).withZone(ZoneOffset.UTC)

    private fun currentAdjustedTimestampSeconds(): Double =
        (System.currentTimeMillis() / 1000.0) + clockSkewSeconds

    private fun updateClockSkewFromDateHeader(dateHeader: String?) {
        if (dateHeader.isNullOrBlank()) return
        runCatching {
            val serverEpoch = ZonedDateTime.parse(dateHeader.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond().toDouble()
            val localEpoch = System.currentTimeMillis() / 1000.0
            clockSkewSeconds = serverEpoch - localEpoch
        }
    }

    /**
     * Synthesizes [text] into an MP3 file in [context.cacheDir] (or returns cached file immediately).
     * Tier 1: TalkifyTTS Azure Neural WebSocket (`nl-NL-MaartenNeural` / `en-US-AndrewMultilingualNeural`).
     * Tier 2: HTTPS Neural TTS stream fallback if WebSocket is blocked on the network.
     */
    suspend fun synthesizeToFile(
        context: Context,
        text: String,
        lang: AppLanguage,
        speed: AgapeSpeechSpeed,
        voiceProfile: TalkifyVoiceProfile = TalkifyVoiceProfile.MAARTEN_ANDREW,
    ): SynthesizedAudioResult? = withContext(Dispatchers.IO) {
        val cleaned = cleanTextForNeural(text, lang)
        if (cleaned.isBlank()) return@withContext null

        val voiceId = voiceProfile.voiceId(lang)
        val ratePercent = when (speed) {
            AgapeSpeechSpeed.CALM -> "-14%"
            AgapeSpeechSpeed.NATURAL -> "-6%"
            AgapeSpeechSpeed.NORMAL -> "+0%"
        }
        val pitchHz = voiceProfile.defaultPitchHz

        val cacheDir = File(context.cacheDir, "talkify_tts").apply { mkdirs() }
        val cacheKey = sha256Hex("v2|$voiceId|$ratePercent|$pitchHz|$cleaned")
        val targetFile = File(cacheDir, "$cacheKey.mp3")
        if (targetFile.exists() && targetFile.length() > 256L) {
            return@withContext SynthesizedAudioResult(targetFile, isAlreadyMaleVoice = true)
        }

        // Tier 1: TalkifyTTS Azure Neural WebSocket (Maarten / Arnaud / Andrew / Brian / Ryan)
        val azureMp3Bytes = runCatching {
            synthesizeBytes(
                text = cleaned,
                voice = voiceId,
                rate = ratePercent,
                pitch = pitchHz,
            )
        }.getOrNull()

        if (azureMp3Bytes != null && azureMp3Bytes.size > 256) {
            runCatching {
                val temp = File(cacheDir, "$cacheKey.tmp")
                FileOutputStream(temp).use { it.write(azureMp3Bytes) }
                temp.renameTo(targetFile)
            }
            if (targetFile.exists() && targetFile.length() > 256L) {
                return@withContext SynthesizedAudioResult(targetFile, isAlreadyMaleVoice = true)
            }
        }

        // Tier 2: Fast HTTPS Neural MP3 fallback if WebSocket is blocked by network/firewall
        val fallbackFile = File(cacheDir, "${cacheKey}_https.mp3")
        if (fallbackFile.exists() && fallbackFile.length() > 256L) {
            return@withContext SynthesizedAudioResult(fallbackFile, isAlreadyMaleVoice = false)
        }
        val httpsBytes = runCatching {
            synthesizeViaHttpsNeuralFallback(cleaned, lang)
        }.getOrNull()
        if (httpsBytes != null && httpsBytes.size > 256) {
            runCatching {
                val temp = File(cacheDir, "${cacheKey}_https.tmp")
                FileOutputStream(temp).use { it.write(httpsBytes) }
                temp.renameTo(fallbackFile)
            }
            if (fallbackFile.exists() && fallbackFile.length() > 256L) {
                return@withContext SynthesizedAudioResult(fallbackFile, isAlreadyMaleVoice = false)
            }
        }

        null
    }

    /**
     * Pre-fetches the next verse or prayer stanza in the background AFTER the current verse starts playing.
     */
    fun prefetchInBackground(
        context: Context,
        text: String,
        lang: AppLanguage,
        speed: AgapeSpeechSpeed,
        voiceProfile: TalkifyVoiceProfile = TalkifyVoiceProfile.MAARTEN_ANDREW,
    ) {
        if (text.isBlank()) return
        scope.launch {
            runCatching {
                synthesizeToFile(context, text, lang, speed, voiceProfile)
            }
        }
    }

    /**
     * Core synthesis method ported from TalkifyTTS's `AzureProvider`:
     * Serialized via [synthesisMutex] so concurrent prefetch and active playback requests never collide on the WebSocket.
     */
    suspend fun synthesizeBytes(
        text: String,
        voice: String,
        rate: String = "-6%",
        pitch: String = "-2Hz",
        volume: String = "+0%",
    ): ByteArray = withContext(Dispatchers.IO) {
        synthesisMutex.withLock {
            withTimeout(5_500L) {
                val requestId = connectId()
                val deferred = CompletableDeferred<ByteArray>()
                inFlightRequests[requestId] = deferred
                try {
                    val (ws, listener) = getOrCreateConnection()
                    listener.registerRequest(requestId, deferred)
                    val ssml = mkssml(voice = voice, rate = rate, volume = volume, pitch = pitch, text = text)
                    val msg = ssmlHeadersPlusData(requestId, dateToString(), ssml)
                    val sent = ws.send(msg)
                    if (!sent) {
                        markConnectionDead()
                        throw IllegalStateException("WebSocket send failed")
                    }
                    deferred.await()
                } catch (e: Throwable) {
                    inFlightRequests.remove(requestId)
                    // Retry once on a fresh connection (using updated clockSkewSeconds if 403 occurred)
                    val retryId = connectId()
                    val retryDeferred = CompletableDeferred<ByteArray>()
                    inFlightRequests[retryId] = retryDeferred
                    try {
                        closeConnection()
                        val (ws2, listener2) = getOrCreateConnection()
                        listener2.registerRequest(retryId, retryDeferred)
                        val ssml = mkssml(voice = voice, rate = rate, volume = volume, pitch = pitch, text = text)
                        val msg = ssmlHeadersPlusData(retryId, dateToString(), ssml)
                        ws2.send(msg)
                        retryDeferred.await()
                    } finally {
                        inFlightRequests.remove(retryId)
                    }
                } finally {
                    inFlightRequests.remove(requestId)
                }
            }
        }
    }

    /**
     * Standard HTTPS neural speech stream fallback for environments where WebSockets are filtered.
     */
    private fun synthesizeViaHttpsNeuralFallback(text: String, lang: AppLanguage): ByteArray? {
        val tl = if (lang == AppLanguage.DUTCH) "nl" else "en"
        val chunks = splitIntoShortChunks(text, maxChars = 180)
        val output = ByteArrayOutputStream(32_768)
        for (chunk in chunks) {
            val encoded = URLEncoder.encode(chunk, "UTF-8")
            val url = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=$tl&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                output.write(bytes)
            }
        }
        return output.toByteArray().takeIf { it.size > 256 }
    }

    private fun splitIntoShortChunks(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val sentences = text.split(Regex("(?<=[.!?;:])\\s+"))
        val result = mutableListOf<String>()
        var current = ""
        for (s in sentences) {
            if (current.isEmpty()) {
                current = s
            } else if (current.length + 1 + s.length <= maxChars) {
                current = "$current $s"
            } else {
                result += current
                current = s
            }
        }
        if (current.isNotBlank()) result += current
        return result.ifEmpty { listOf(text.take(maxChars)) }
    }

    private suspend fun getOrCreateConnection(): Pair<WebSocket, TalkifyWsListener> = connectionMutex.withLock {
        val existingWs = persistentWs
        val existingListener = persistentListener
        if (existingWs != null && existingListener != null && isAlive) {
            return@withLock existingWs to existingListener
        }
        closeConnectionInternal()
        val listener = TalkifyWsListener()
        val connId = connectId()
        val gec = TalkifyAzureDrm.generateSecMsGecV2(currentAdjustedTimestampSeconds())
        val url = "$DEFAULT_WSS_URL&ConnectionId=$connId&Sec-MS-GEC=$gec&Sec-MS-GEC-Version=${TalkifyAzureDrm.SEC_MS_GEC_VERSION}"
        val reqBuilder = Request.Builder().url(url)
        getHeadersWithMuid().forEach { (k, v) -> reqBuilder.addHeader(k, v) }
        val ws = client.newWebSocket(reqBuilder.build(), listener)
        try {
            listener.awaitOpen()
            persistentWs = ws
            persistentListener = listener
            isAlive = true
            ws to listener
        } catch (t: Throwable) {
            ws.cancel()
            throw t
        }
    }

    private suspend fun closeConnection() {
        connectionMutex.withLock {
            closeConnectionInternal()
        }
    }

    private fun closeConnectionInternal() {
        runCatching { persistentWs?.close(1000, "Done") }
        persistentWs = null
        persistentListener = null
        isAlive = false
    }

    private fun markConnectionDead() {
        isAlive = false
    }

    private class TalkifyWsListener : WebSocketListener() {
        private val openDeferred = CompletableDeferred<Unit>()
        private val buffers = ConcurrentHashMap<String, ByteArrayOutputStream>()
        private val completions = ConcurrentHashMap<String, CompletableDeferred<ByteArray>>()

        fun registerRequest(requestId: String, deferred: CompletableDeferred<ByteArray>) {
            buffers[requestId] = ByteArrayOutputStream(32_768)
            completions[requestId] = deferred
        }

        suspend fun awaitOpen() {
            openDeferred.await()
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            updateClockSkewFromDateHeader(response.header("Date"))
            try {
                val configMessage =
                    "X-Timestamp:${dateToString()}\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{" +
                        "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"}," +
                        "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                webSocket.send(configMessage)
                openDeferred.complete(Unit)
            } catch (e: Throwable) {
                openDeferred.completeExceptionally(e)
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            try {
                val buffer = bytes.asByteBuffer()
                if (buffer.remaining() < 2) return
                val headerLength = ((buffer.get().toInt() and 0xFF) shl 8) or (buffer.get().toInt() and 0xFF)
                if (buffer.remaining() < headerLength) return
                val headerBytes = ByteArray(headerLength)
                buffer.get(headerBytes)
                val headerStr = String(headerBytes, Charsets.UTF_8)
                if (!headerStr.contains("Path:audio") && !headerStr.contains("Path: audio")) return
                if (buffer.remaining() <= 0) return
                val audioData = ByteArray(buffer.remaining())
                buffer.get(audioData)
                val reqId = extractRequestId(headerStr)
                if (reqId != null) {
                    buffers[reqId]?.write(audioData)
                }
            } catch (_: Throwable) {
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text.contains("Path:turn.end") || text.contains("Path: turn.end")) {
                val reqId = extractRequestId(text)
                if (reqId != null) {
                    val out = buffers.remove(reqId)?.toByteArray() ?: ByteArray(0)
                    completions.remove(reqId)?.complete(out)
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            markConnectionDead()
            failAll(IllegalStateException("WebSocket closing: $code $reason"))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            markConnectionDead()
            failAll(IllegalStateException("WebSocket closed: $code $reason"))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            updateClockSkewFromDateHeader(response?.header("Date"))
            markConnectionDead()
            if (!openDeferred.isCompleted) {
                openDeferred.completeExceptionally(t)
            }
            failAll(t)
        }

        private fun failAll(t: Throwable) {
            completions.values.forEach { it.completeExceptionally(t) }
            completions.clear()
            buffers.clear()
        }
    }

    private fun extractRequestId(headerStr: String): String? {
        val prefix = "X-RequestId:"
        val startIdx = headerStr.indexOf(prefix)
        if (startIdx < 0) return null
        val valueStart = startIdx + prefix.length
        val endIdx = headerStr.indexOf('\r', valueStart).let {
            if (it < 0) headerStr.indexOf('\n', valueStart) else it
        }
        return if (endIdx > valueStart) {
            headerStr.substring(valueStart, endIdx).trim()
        } else {
            headerStr.substring(valueStart).trim().takeIf { it.isNotEmpty() }
        }
    }

    private fun generateMuid(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it).uppercase(Locale.US) }
    }

    private fun getHeadersWithMuid(): Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/${TalkifyAzureDrm.CHROMIUM_MAJOR_VERSION}.0.0.0 Safari/537.36 " +
            "Edg/${TalkifyAzureDrm.CHROMIUM_MAJOR_VERSION}.0.0.0",
        "Accept-Encoding" to "gzip, deflate, br, zstd",
        "Accept-Language" to "en-US,en;q=0.9",
        "Pragma" to "no-cache",
        "Cache-Control" to "no-cache",
        "Origin" to "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold",
        "Sec-WebSocket-Version" to "13",
        "Cookie" to "muid=${generateMuid()};",
    )

    private fun dateToString(): String =
        utcDateTimeFormatter.format(Instant.ofEpochMilli((currentAdjustedTimestampSeconds() * 1000.0).toLong()))

    private fun connectId(): String = UUID.randomUUID().toString().replace("-", "")

    private fun mkssml(voice: String, rate: String, volume: String, pitch: String, text: String): String {
        val xmlLang = if (voice.startsWith("nl", ignoreCase = true)) "nl-NL" else "en-US"
        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$xmlLang'>" +
            "<voice name='$voice'>" +
            "<prosody pitch='$pitch' rate='$rate' volume='$volume'>" +
            escapeXml(text) +
            "</prosody>" +
            "</voice>" +
            "</speak>"
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun ssmlHeadersPlusData(requestId: String, timestamp: String, ssml: String): String =
        "X-RequestId:$requestId\r\n" +
            "Content-Type:application/ssml+xml\r\n" +
            "X-Timestamp:${timestamp}Z\r\n" +
            "Path:ssml\r\n\r\n" +
            ssml

    private fun cleanTextForNeural(input: String, lang: AppLanguage): String {
        var text = input
            .replace("Κύριε ἐλέησον", "Kyrie eleison")
            .replace("ΚΥΡΙΕ ΕΛΕΗΣΟΝ", "Kyrie eleison")
            .replace("•", " ")
            .replace("·", ", ")
            .replace("—", ", ")
            .replace("–", ", ")
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (lang == AppLanguage.DUTCH) {
            text = text
                .replace(Regex("\\bJoh\\."), "Johannes")
                .replace(Regex("\\bMatt\\."), "Matteüs")
                .replace(Regex("\\bPs\\."), "Psalm")
        }
        return text
    }

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }

    /**
     * Plays a synthesized neural MP3 [result] via Android [MediaPlayer] using an open [FileInputStream.getFD]
     * so Android's `mediaserver` process always has read access to the cached file in `context.cacheDir`.
     */
    fun playMp3Result(
        result: SynthesizedAudioResult,
        speed: AgapeSpeechSpeed,
        voiceProfile: TalkifyVoiceProfile,
        onFinished: () -> Unit,
        onError: () -> Unit,
    ): MediaPlayer? {
        return runCatching {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            FileInputStream(result.file).use { fis ->
                mp.setDataSource(fis.fd)
            }
            mp.setOnCompletionListener { player ->
                runCatching { player.release() }
                onFinished()
            }
            mp.setOnErrorListener { player, _, _ ->
                runCatching { player.release() }
                onError()
                true
            }
            mp.prepare()
            if (!result.isAlreadyMaleVoice) {
                runCatching {
                    val params = PlaybackParams()
                        .setSpeed(speed.rate)
                        .setPitch(voiceProfile.fallbackPitchMultiplier)
                    mp.playbackParams = params
                }
            }
            mp.start()
            mp
        }.onFailure {
            onError()
        }.getOrNull()
    }
}
