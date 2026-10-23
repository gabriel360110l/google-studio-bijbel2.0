package agape.app.core.prefs

import agape.app.core.model.ThemeMode
import agape.app.core.model.Tradition
import agape.app.core.model.VisualStyle
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "agape_settings")

/** Where the user left off in the Bible. */
data class ReadingPosition(val book: String, val chapter: Int, val verse: Int)

/** Tabs that have a guided-tour explanation. */
enum class TourStop { HOME, BIBLE, PRAYER, ME }

data class Settings(
    val welcomeSeen: Boolean = false,
    val termsAccepted: Boolean = false,
    val tradition: Tradition? = null,
    val style: VisualStyle? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: Float = 1f,
    val tourSeen: Set<TourStop> = emptySet(),
    val prayerIntroSeen: Boolean = false,
    val prayerNotifications: Boolean = false,
    val inactivityReminders: Boolean = false,
    /** Hour ids with a reminder, stored as `hourId@HH:mm`. */
    val hourReminders: Set<String> = emptySet(),
    /** User-defined free reminder times, stored as `id|HH:mm|label`. */
    val customReminders: Set<String> = emptySet(),
    /** Personal prayer intentions & names, stored as `category|text`. */
    val prayerIntentions: Set<String> = emptySet(),
    val lastPosition: ReadingPosition? = null,
) {
    /** Onboarding is complete up to (but not including) sign-in. */
    val readyForSignIn: Boolean get() = welcomeSeen && termsAccepted && tradition != null && style != null
    val tourComplete: Boolean get() = tourSeen.containsAll(TourStop.entries)
}

private const val SEPARATOR = "\u001F"

class SettingsRepository(private val context: Context) {
    private object Keys {
        val welcomeSeen = booleanPreferencesKey("welcome_seen")
        val termsAccepted = booleanPreferencesKey("terms_accepted")
        val tradition = stringPreferencesKey("tradition")
        val style = stringPreferencesKey("style")
        val themeMode = stringPreferencesKey("theme_mode")
        val fontScale = floatPreferencesKey("font_scale")
        val tourSeen = stringSetPreferencesKey("tour_seen")
        val prayerIntroSeen = booleanPreferencesKey("prayer_intro_seen")
        val prayerNotifications = booleanPreferencesKey("prayer_notifications")
        val inactivityReminders = booleanPreferencesKey("inactivity_reminders")
        val hourReminders = stringSetPreferencesKey("hour_reminders")
        val customReminders = stringSetPreferencesKey("custom_reminders")
        val prayerIntentions = stringSetPreferencesKey("prayer_intentions")
        val lastBook = stringPreferencesKey("last_book")
        val lastChapter = intPreferencesKey("last_chapter")
        val lastVerse = intPreferencesKey("last_verse")
        val lastOpenedAt = longPreferencesKey("last_opened_at")
        val dailyCache = stringPreferencesKey("daily_cache")
        val recentSearches = stringPreferencesKey("recent_searches")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            welcomeSeen = p[Keys.welcomeSeen] ?: false,
            termsAccepted = p[Keys.termsAccepted] ?: false,
            tradition = p[Keys.tradition]?.let { runCatching { Tradition.valueOf(it) }.getOrNull() },
            style = p[Keys.style]?.let { runCatching { VisualStyle.valueOf(it) }.getOrNull() },
            themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            fontScale = p[Keys.fontScale] ?: 1f,
            tourSeen = p[Keys.tourSeen].orEmpty().mapNotNull { runCatching { TourStop.valueOf(it) }.getOrNull() }.toSet(),
            prayerIntroSeen = p[Keys.prayerIntroSeen] ?: false,
            prayerNotifications = p[Keys.prayerNotifications] ?: false,
            inactivityReminders = p[Keys.inactivityReminders] ?: false,
            hourReminders = p[Keys.hourReminders].orEmpty(),
            customReminders = p[Keys.customReminders].orEmpty(),
            prayerIntentions = p[Keys.prayerIntentions].orEmpty(),
            lastPosition = p[Keys.lastBook]?.let { ReadingPosition(it, p[Keys.lastChapter] ?: 1, p[Keys.lastVerse] ?: 1) },
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setWelcomeSeen() = context.dataStore.edit { it[Keys.welcomeSeen] = true }
    suspend fun setTermsAccepted() = context.dataStore.edit { it[Keys.termsAccepted] = true }
    suspend fun setTradition(t: Tradition) = context.dataStore.edit { it[Keys.tradition] = t.name }
    suspend fun setStyle(s: VisualStyle) = context.dataStore.edit { it[Keys.style] = s.name }
    suspend fun setThemeMode(m: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = m.name }
    suspend fun setFontScale(scale: Float) = context.dataStore.edit { it[Keys.fontScale] = scale.coerceIn(0.85f, 1.6f) }
    suspend fun markTourSeen(stop: TourStop) = context.dataStore.edit { it[Keys.tourSeen] = it[Keys.tourSeen].orEmpty() + stop.name }
    suspend fun resetTour() = context.dataStore.edit {
        it.remove(Keys.tourSeen)
        it.remove(Keys.prayerIntroSeen)
    }
    suspend fun setPrayerIntroSeen() = context.dataStore.edit { it[Keys.prayerIntroSeen] = true }
    suspend fun setPrayerNotifications(on: Boolean) = context.dataStore.edit { it[Keys.prayerNotifications] = on }
    suspend fun setInactivityReminders(on: Boolean) = context.dataStore.edit { it[Keys.inactivityReminders] = on }
    suspend fun setHourReminders(entries: Set<String>) = context.dataStore.edit { it[Keys.hourReminders] = entries }
    suspend fun setCustomReminders(entries: Set<String>) = context.dataStore.edit { it[Keys.customReminders] = entries }
    suspend fun setPrayerIntentions(entries: Set<String>) = context.dataStore.edit { it[Keys.prayerIntentions] = entries }

    suspend fun setLastPosition(pos: ReadingPosition) = context.dataStore.edit {
        it[Keys.lastBook] = pos.book
        it[Keys.lastChapter] = pos.chapter
        it[Keys.lastVerse] = pos.verse
    }

    suspend fun markOpened(now: Long = System.currentTimeMillis()) = context.dataStore.edit { it[Keys.lastOpenedAt] = now }
    suspend fun lastOpenedAt(): Long = context.dataStore.data.first()[Keys.lastOpenedAt] ?: 0L

    /** Most recent first, at most eight. */
    val recentSearches: Flow<List<String>> = context.dataStore.data.map { p ->
        p[Keys.recentSearches]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
    }

    suspend fun addRecentSearch(query: String) = context.dataStore.edit {
        val list = it[Keys.recentSearches]?.split(SEPARATOR).orEmpty()
        it[Keys.recentSearches] = (listOf(query) + list.filter { q -> !q.equals(query, ignoreCase = true) }).take(8).joinToString(SEPARATOR)
    }

    suspend fun dailyCache(): String? = context.dataStore.data.first()[Keys.dailyCache]
    suspend fun setDailyCache(json: String) = context.dataStore.edit { it[Keys.dailyCache] = json }

    /** Clears everything tied to the signed-in person, keeping device choices (style, language, church). */
    suspend fun clearPersonal() = context.dataStore.edit {
        it.remove(Keys.lastBook); it.remove(Keys.lastChapter); it.remove(Keys.lastVerse)
        it.remove(Keys.tourSeen); it.remove(Keys.prayerIntroSeen)
    }
}
