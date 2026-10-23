package agape.app.data.daily

import agape.app.core.model.AppLanguage
import agape.app.core.model.Passage
import agape.app.core.model.Tradition
import agape.app.core.prefs.SettingsRepository
import agape.app.data.content.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** Saint of the day, feast and readings for one date and tradition. */
@Serializable
data class DailyInfo(
    val date: String,
    val tradition: Tradition,
    val title: String,
    val subtitle: String = "",
    val saints: List<String> = emptyList(),
    /** Passages in the app's own `BOOK C:V-V` notation. */
    val readings: List<String> = emptyList(),
    val source: String,
)

/**
 * Fetches daily data from the free APIs listed in EOC-church (orthocal.info, api.coptic.io, calapi) and keeps
 * the last result so Home still has something to show offline. Eastern Orthodox falls back to the bundled menologion.
 */
class DailyRepository(
    private val content: ContentRepository,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun today(tradition: Tradition, language: AppLanguage, date: LocalDate = LocalDate.now()): DailyInfo? {
        val cached = settings.dailyCache()?.let { runCatching { json.decodeFromString<DailyInfo>(it) }.getOrNull() }
        if (cached != null && cached.date == date.toString() && cached.tradition == tradition && cached.source != LOCAL) {
            return localized(cached, tradition, language, date)
        }
        val fresh = runCatching { fetch(tradition, date) }.getOrNull()
        if (fresh != null) {
            settings.setDailyCache(json.encodeToString(DailyInfo.serializer(), fresh))
            return localized(fresh, tradition, language, date)
        }
        return local(tradition, language, date) ?: cached
    }

    /** Any date, for the calendar: online first, the bundled menologion when offline. Not cached. */
    suspend fun forDate(tradition: Tradition, language: AppLanguage, date: LocalDate): DailyInfo? {
        if (date == LocalDate.now()) return today(tradition, language, date)
        return runCatching { fetch(tradition, date) }.getOrNull()?.let { localized(it, tradition, language, date) }
            ?: local(tradition, language, date)
    }

    /** For Dutch, the Eastern Orthodox title comes from the bundled Dutch menologion when the API is English-only. */
    private fun localized(info: DailyInfo, tradition: Tradition, language: AppLanguage, date: LocalDate): DailyInfo {
        if (language != AppLanguage.DUTCH || tradition != Tradition.EASTERN_ORTHODOX) return info
        val saint = content.saintEo(date) ?: return info
        return info.copy(title = saint.title[language])
    }

    private fun local(tradition: Tradition, language: AppLanguage, date: LocalDate): DailyInfo? {
        if (tradition != Tradition.EASTERN_ORTHODOX) return null
        val saint = content.saintEo(date) ?: return null
        return DailyInfo(date.toString(), tradition, saint.title[language], saint.summary[language], source = LOCAL)
    }

    private suspend fun fetch(tradition: Tradition, date: LocalDate): DailyInfo = when (tradition) {
        Tradition.EASTERN_ORTHODOX -> {
            val o = get("https://orthocal.info/api/gregorian/${date.year}/${date.monthValue}/${date.dayOfMonth}/").jsonObject
            val saints = o["saints"].strings()
            val titles = o["titles"].strings()
            DailyInfo(
                date = date.toString(), tradition = tradition,
                title = (o["feasts"].strings().firstOrNull() ?: saints.firstOrNull() ?: titles.firstOrNull()).orEmpty(),
                subtitle = listOfNotNull(titles.firstOrNull(), o["fast_level_desc"]?.jsonPrimitive?.content).joinToString(" · "),
                saints = saints,
                readings = (o["readings"] as? JsonArray).orEmpty()
                    .mapNotNull { r -> r.jsonObject["display"]?.jsonPrimitive?.content?.let { toPassage(it) } },
                source = "orthocal.info",
            )
        }
        Tradition.ORIENTAL_ORTHODOX -> {
            val entries = get("https://api.coptic.io/api/synaxarium/$date").jsonArray
            val names = entries.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content?.trimEnd('.') }
            DailyInfo(date.toString(), tradition, names.firstOrNull().orEmpty(), saints = names, source = "api.coptic.io")
        }
        Tradition.CATHOLIC -> {
            // calapi is only reachable over plain HTTP; see network_security_config.xml.
            val o = get("http://calapi.inadiutorium.cz/api/v0/en/calendars/default/${date.year}/${date.monthValue}/${date.dayOfMonth}").jsonObject
            val celebrations = (o["celebrations"] as? JsonArray).orEmpty().map { it.jsonObject }
            val season = o["season"]?.jsonPrimitive?.content.orEmpty()
            DailyInfo(
                date = date.toString(), tradition = tradition,
                title = celebrations.firstOrNull()?.get("title")?.jsonPrimitive?.content.orEmpty(),
                subtitle = season.replaceFirstChar { it.uppercase() },
                saints = celebrations.drop(1).mapNotNull { it["title"]?.jsonPrimitive?.content },
                source = "calapi.inadiutorium.cz",
            )
        }
    }

    private suspend fun get(url: String) = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            json.parseToJsonElement(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun kotlinx.serialization.json.JsonElement?.strings(): List<String> =
        (this as? JsonArray).orEmpty().mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }

    /** "Colossians 1.18-23" or "II Corinthians 4.6-15" → "COL 1:18-23". */
    internal fun toPassage(display: String): String? {
        val m = Regex("""^(.+?)\s+(\d+)\.(\d+)(?:-(\d+)(?:\.(\d+))?)?""").find(display.trim()) ?: return null
        val (rawName, ch, v1, a, b) = m.destructured
        val name = rawName.replace(Regex("^III "), "3 ").replace(Regex("^II "), "2 ").replace(Regex("^I "), "1 ")
            .replace("Psalm ", "Psalms ").lowercase()
        val book = content.books.firstOrNull { it.name.en.lowercase() == name || it.name.en.lowercase().startsWith(name) } ?: return null
        val text = when {
            a.isEmpty() -> "${book.id} $ch:$v1"
            b.isNotEmpty() -> "${book.id} $ch:$v1-$a:$b"
            else -> "${book.id} $ch:$v1-$a"
        }
        return text.takeIf { Passage.parse(it) != null }
    }

    private companion object {
        const val LOCAL = "local"
    }
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
