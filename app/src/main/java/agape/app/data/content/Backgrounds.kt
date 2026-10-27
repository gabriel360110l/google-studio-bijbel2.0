package agape.app.data.content

import agape.app.core.model.Tradition
import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** A Home background: a painted interior or a public-domain / CC0 photograph, with its credit. */
@Serializable
data class BackgroundEntry(
    val file: String,
    val title: String,
    val artist: String = "",
    val licence: String,
    val source: String = "",
)

/** The bundled backgrounds in `assets/backgrounds` (see tools/render_backgrounds.py and tools/build_backgrounds.py). */
class Backgrounds(private val context: Context) {
    val all: List<BackgroundEntry> by lazy {
        runCatching {
            context.assets.open("backgrounds/backgrounds.json").bufferedReader().use {
                Json { ignoreUnknownKeys = true }.decodeFromString<List<BackgroundEntry>>(it.readText())
            }
        }.getOrDefault(emptyList())
    }

    /** Curated pool of atmospheric church and peaceful sunrise nature backgrounds for [tradition]. */
    fun poolFor(tradition: Tradition = Tradition.EASTERN_ORTHODOX): List<BackgroundEntry> {
        if (all.isEmpty()) return emptyList()
        return when (tradition) {
            Tradition.EASTERN_ORTHODOX -> all.filter {
                it.file.contains("church_candles_ljubljana") ||
                    it.file.contains("sunrise_galilee") ||
                    it.file.contains("church_candles_bulgaria") ||
                    it.file.contains("sunrise_mountains_babia") ||
                    it.file.contains("sunrise_forest_rays")
            }
            Tradition.ORIENTAL_ORTHODOX -> all.filter {
                it.file.contains("sunrise_galilee") ||
                    it.file.contains("church_candles_bulgaria") ||
                    it.file.contains("cloister_toledo") ||
                    it.file.contains("sunrise_mountains_babia") ||
                    it.file.contains("church_candles_ljubljana")
            }
            Tradition.CATHOLIC -> all.filter {
                it.file.contains("st_marys_cathedral") ||
                    it.file.contains("sunrise_forest_rays") ||
                    it.file.contains("cloister_jeronimos") ||
                    it.file.contains("sunrise_galilee") ||
                    it.file.contains("cloister_toledo") ||
                    it.file.contains("sunrise_mountains_babia")
            }
        }.ifEmpty { all }
    }

    /** One background per day and per tradition, mixing atmospheric churches and peaceful sunrise nature. */
    fun forDate(date: LocalDate, tradition: Tradition = Tradition.EASTERN_ORTHODOX, offset: Int = 0): BackgroundEntry? {
        val pool = poolFor(tradition)
        if (pool.isEmpty()) return null
        return pick(pool, date.plusDays(offset.toLong()))
    }

    companion object {
        fun <T> pick(items: List<T>, date: LocalDate): T? =
            if (items.isEmpty()) null else items[Math.floorMod(date.toEpochDay(), items.size.toLong()).toInt()]
    }
}
