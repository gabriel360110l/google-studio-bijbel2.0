package agape.app.data.schedule

import agape.app.core.model.AppLanguage
import agape.app.core.model.L10n
import agape.app.core.model.Tradition
import agape.app.core.prefs.SettingsRepository
import agape.app.data.content.ContentRepository
import agape.app.data.content.PrayerHour
import agape.app.data.user.Activity
import agape.app.data.user.ActivityType
import agape.app.data.user.SmartScheduleEntry
import agape.app.data.user.UserRepository
import agape.app.notifications.HourReminder
import agape.app.notifications.Notifications
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

data class GeoCity(
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String = "Europe/Amsterdam",
) {
    val label: String get() = if (country.isNotBlank()) "$name, $country" else name
}

data class SolarWeatherInfo(
    val sunrise: LocalTime,
    val sunset: LocalTime,
    val solarNoon: LocalTime,
    val weatherSummaryEn: String,
    val weatherSummaryNl: String,
    val source: String,
    val timezoneId: String = "Europe/Amsterdam",
) {
    fun weatherFor(lang: AppLanguage): String =
        if (lang == AppLanguage.DUTCH) weatherSummaryNl else weatherSummaryEn
}

enum class SolarAnchor(val title: L10n, val badge: String) {
    DAWN(L10n("Before Sunrise · Dawn", "Vóór zonsopgang · Dageraad"), "🌅"),
    SUNRISE(L10n("At Sunrise · Morning Light", "Bij zonsopgang · Ochtendlicht"), "☀️"),
    MORNING(L10n("3rd Hour · 09:00 Morning", "Derde Uur · 09:00 Ochtend"), "🌤"),
    MIDDAY(L10n("6th Hour · 12:00 Midday", "Zesde Uur · 12:00 Middag"), "🕛"),
    AFTERNOON(L10n("9th Hour · 15:00 Afternoon", "Negende Uur · 15:00 Namiddag"), "⛅"),
    SUNSET(L10n("At Sunset · Vespers", "Bij zonsondergang · Vespers"), "🌇"),
    EVENING(L10n("Evening · Compline", "Avondrust · Completen"), "🌙"),
    NIGHT(L10n("Night Watch · Before Sleep", "Nachtwake · Vóór het slapen"), "✨");

    companion object {
        fun fromKey(key: String): SolarAnchor =
            entries.firstOrNull { it.name == key } ?: SUNRISE
    }
}

/**
 * Computes and persists a daily "Smart Schedule" in the Room database (`smart_schedule` table)
 * by combining:
 * 1. Local Sunrise, Solar Noon, and Sunset fetched from Open-Meteo Weather & Solar API (or Sunrise-Sunset API / NOAA fallback)
 *    in the target location's true timezone (avoiding Android emulator UTC/US-Pacific timezone mismatches).
 * 2. Liturgically faithful canonical hours (Horologion, Agpeya, Liturgy of the Hours) anchored to local dawn/sunrise/sunset.
 * 3. User's historical prayer activity (`activity` table) and favorite hours (`favorite_prayers` table) in Room.
 */
class SmartScheduleService(
    private val context: Context,
    private val content: ContentRepository,
    private val user: UserRepository,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val presetCities: List<GeoCity> = listOf(
        GeoCity("Amsterdam", "Nederland", 52.3676, 4.9041, "Europe/Amsterdam"),
        GeoCity("Utrecht", "Nederland", 52.0907, 5.1214, "Europe/Amsterdam"),
        GeoCity("Rotterdam", "Nederland", 51.9244, 4.4777, "Europe/Amsterdam"),
        GeoCity("Den Haag", "Nederland", 52.0705, 4.3007, "Europe/Amsterdam"),
        GeoCity("Eindhoven", "Nederland", 51.4416, 5.4697, "Europe/Amsterdam"),
        GeoCity("Antwerpen", "België", 51.2194, 4.4025, "Europe/Brussels"),
        GeoCity("Brussel", "België", 50.8503, 4.3517, "Europe/Brussels"),
        GeoCity("Jeruzalem", "Israël", 31.7683, 35.2137, "Asia/Jerusalem"),
        GeoCity("Rome", "Italië", 41.9028, 12.4964, "Europe/Rome"),
        GeoCity("Istanbul (Constantinopel)", "Turkije", 41.0082, 28.9784, "Europe/Istanbul"),
        GeoCity("Alexandrië", "Egypte", 31.2001, 29.9187, "Africa/Cairo"),
        GeoCity("London", "UK", 51.5074, -0.1278, "Europe/London"),
    )

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Returns the current local time in the user's active schedule timezone (or Europe/Amsterdam if the emulator
     * is running in UTC or America/Los_Angeles), so "Current Prayer Hour" matches real local time.
     */
    fun currentLocalTime(entries: List<SmartScheduleEntry> = emptyList()): LocalTime {
        val sample = entries.firstOrNull()
        val zone = if (sample != null) {
            zoneForCoordinates(sample.latitude, sample.longitude)
        } else {
            val sys = ZoneId.systemDefault()
            if (sys.id == "UTC" || sys.id == "Etc/UTC" || sys.id.startsWith("America/Los_Angeles")) {
                ZoneId.of("Europe/Amsterdam")
            } else {
                sys
            }
        }
        return LocalTime.now(zone)
    }

    /**
     * Generates and saves the Smart Schedule in the Room database using the device's current GPS/network location
     * (filtering out the Android emulator's fake Mountain View default GPS coordinate).
     */
    suspend fun generateFromDeviceLocation(
        tradition: Tradition,
        lang: AppLanguage,
        useCanonicalFixedTimes: Boolean = false,
    ): List<SmartScheduleEntry> {
        val loc = getDeviceLocation()
        val (lat, lon, label) = if (loc != null && !isEmulatorDefaultLocation(loc.latitude, loc.longitude)) {
            val cityName = reverseGeocode(loc.latitude, loc.longitude)
                ?: "%s (%.2f°, %.2f°)".format(
                    if (lang == AppLanguage.DUTCH) "Huidige locatie" else "Current location",
                    loc.latitude,
                    loc.longitude,
                )
            Triple(loc.latitude, loc.longitude, cityName)
        } else {
            val existing = user.allSmartScheduleList().firstOrNull()
            if (existing != null && !isEmulatorDefaultLocation(existing.latitude, existing.longitude)) {
                Triple(existing.latitude, existing.longitude, existing.locationLabel)
            } else {
                val fallback = defaultCityForTimezone()
                Triple(fallback.latitude, fallback.longitude, fallback.label)
            }
        }
        return generateForCoordinates(
            latitude = lat,
            longitude = lon,
            locationLabel = label,
            tradition = tradition,
            lang = lang,
            useCanonicalFixedTimes = useCanonicalFixedTimes,
        )
    }

    /** Generates and saves the Smart Schedule in Room for a specific city. */
    suspend fun generateForCity(
        city: GeoCity,
        tradition: Tradition,
        lang: AppLanguage,
        useCanonicalFixedTimes: Boolean = false,
    ): List<SmartScheduleEntry> =
        generateForCoordinates(
            latitude = city.latitude,
            longitude = city.longitude,
            locationLabel = city.label,
            tradition = tradition,
            lang = lang,
            timezoneHint = city.timezone,
            useCanonicalFixedTimes = useCanonicalFixedTimes,
        )

    suspend fun generateForCoordinates(
        latitude: Double,
        longitude: Double,
        locationLabel: String,
        tradition: Tradition,
        lang: AppLanguage,
        date: LocalDate = LocalDate.now(),
        timezoneHint: String? = null,
        useCanonicalFixedTimes: Boolean = false,
    ): List<SmartScheduleEntry> = withContext(Dispatchers.IO) {
        val targetZone = timezoneHint?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: zoneForCoordinates(latitude, longitude)
        val solar = fetchSolarAndWeather(latitude, longitude, date, targetZone)
        val hours = content.prayerBook(tradition).hours
        val activities = user.allActivityList()
        val favorites = user.allFavoritesList().map { it.id }.toSet()
        val existingEnabled = user.allSmartScheduleList().associate { it.hourId to it.enabled }

        val entries = buildEntriesForHours(
            hours = hours,
            tradition = tradition,
            solar = solar,
            locationLabel = locationLabel,
            latitude = latitude,
            longitude = longitude,
            lang = lang,
            activities = activities,
            favorites = favorites,
            existingEnabled = existingEnabled,
            useCanonicalFixedTimes = useCanonicalFixedTimes,
        )
        user.saveSmartSchedule(entries)
        entries
    }

    /**
     * Copies all enabled Smart Schedule entries from the Room database into the active alarm reminders
     * (`settings.hourReminders`) and reschedules `AlarmManager`.
     */
    suspend fun applySmartScheduleToReminders(): Int {
        val entries = user.allSmartScheduleList().filter { it.enabled }
        if (entries.isEmpty()) return 0
        val encoded = entries.mapNotNull { entry ->
            runCatching {
                HourReminder(entry.hourId, LocalTime.parse(entry.suggestedTime)).encode()
            }.getOrNull()
        }.toSet()
        settings.setHourReminders(encoded)
        settings.setPrayerNotifications(true)
        Notifications.reschedule(context)
        return encoded.size
    }

    /** Searches cities worldwide using the free Open-Meteo Geocoding API, falling back to preset cities offline. */
    suspend fun searchCities(query: String, lang: AppLanguage): List<GeoCity> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext presetCities
        val remote = runCatching {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=8&language=${lang.tag}&format=json"
            val root = getJson(url).jsonObject
            val results = (root["results"] as? JsonArray).orEmpty()
            results.mapNotNull { item ->
                val obj = item.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val country = obj["country"]?.jsonPrimitive?.content.orEmpty()
                val lat = obj["latitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val lon = obj["longitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val tz = obj["timezone"]?.jsonPrimitive?.content ?: zoneForCoordinates(lat, lon).id
                GeoCity(name, country, lat, lon, tz)
            }
        }.getOrDefault(emptyList())

        if (remote.isNotEmpty()) remote
        else presetCities.filter {
            it.name.contains(clean, ignoreCase = true) || it.country.contains(clean, ignoreCase = true)
        }.ifEmpty { presetCities }
    }

    /** Computes a human-readable insight from the Room `activity` table to explain how history shapes the schedule. */
    fun roomActivityInsight(activities: List<Activity>, lang: AppLanguage): String {
        val prayedHours = activities.filter { it.type == ActivityType.PRAYED_HOUR || it.type == ActivityType.PRAYED }
        val chapters = activities.count { it.type == ActivityType.READ_CHAPTER }
        val nl = lang == AppLanguage.DUTCH
        if (prayedHours.isEmpty() && chapters == 0) {
            return if (nl) {
                "Berekend op uw lokale zonsopgang & zonsondergang + kerkelijke uren, opgeslagen in de Room-database."
            } else {
                "Calculated from your local sunrise & sunset + canonical hours, stored in the Room database."
            }
        }
        val avgHour = prayedHours.map {
            Instant.ofEpochMilli(it.at).atZone(ZoneId.of("Europe/Amsterdam")).hour
        }.average().takeIf { !it.isNaN() }?.roundToInt()

        return if (nl) {
            buildString {
                append("Afgestemd op zonnestand & Room-historie (${prayedHours.size} gebeden, $chapters hoofdstukken")
                if (avgHour != null) append(", vast gebedsmoment rond %02d:00".format(avgHour))
                append(").")
            }
        } else {
            buildString {
                append("Tuned to solar cycle & Room history (${prayedHours.size} prayers, $chapters chapters")
                if (avgHour != null) append(", regular prayer around %02d:00".format(avgHour))
                append(").")
            }
        }
    }

    internal fun buildEntriesForHours(
        hours: List<PrayerHour>,
        tradition: Tradition,
        solar: SolarWeatherInfo,
        locationLabel: String,
        latitude: Double,
        longitude: Double,
        lang: AppLanguage,
        activities: List<Activity>,
        favorites: Set<String>,
        existingEnabled: Map<String, Boolean>,
        useCanonicalFixedTimes: Boolean = false,
    ): List<SmartScheduleEntry> {
        val sunriseMins = solar.sunrise.hour * 60 + solar.sunrise.minute
        val sunsetMins = (solar.sunset.hour * 60 + solar.sunset.minute).coerceAtLeast(sunriseMins + 240)
        val solarNoonMins = solar.solarNoon.hour * 60 + solar.solarNoon.minute
        val nowMillis = System.currentTimeMillis()
        val zone = runCatching { ZoneId.of(solar.timezoneId) }.getOrElse { zoneForCoordinates(latitude, longitude) }

        // Inspect Room activity timestamps per hourId to gently nudge suggested time (up to ±15 min) only if the user repeatedly prayed that hour
        val habitMinuteByHourId: Map<String, Int> = activities
            .filter { it.type == ActivityType.PRAYED_HOUR && it.ref.startsWith("hour:") }
            .groupBy { it.ref.removePrefix("hour:") }
            .filterValues { it.size >= 2 }
            .mapValues { (_, list) ->
                list.map { act ->
                    val lt = Instant.ofEpochMilli(act.at).atZone(zone).toLocalTime()
                    lt.hour * 60 + lt.minute
                }.average().roundToInt()
            }

        val prayedHourIds = habitMinuteByHourId.keys

        return hours.map { hour ->
            val (anchor, baseMinutes) = if (useCanonicalFixedTimes) {
                val parsed = runCatching { LocalTime.parse(hour.time) }.getOrDefault(LocalTime.of(8, 0))
                anchorForHourId(hour.id, tradition) to (parsed.hour * 60 + parsed.minute)
            } else {
                solarMinutesForHour(
                    hourId = hour.id,
                    tradition = tradition,
                    fallbackTime = hour.time,
                    sunriseMins = sunriseMins,
                    solarNoonMins = solarNoonMins,
                    sunsetMins = sunsetMins,
                )
            }

            val habitMins = habitMinuteByHourId[hour.id]
            val finalMinutes = if (!useCanonicalFixedTimes && habitMins != null && abs(habitMins - baseMinutes) <= 45) {
                // Gentle 80/20 blend rounded to nearest 5 minutes
                val raw = (baseMinutes * 0.8 + habitMins * 0.2).roundToInt()
                ((raw + 2) / 5) * 5
            } else {
                baseMinutes
            }

            val normalizedMins = Math.floorMod(finalMinutes, 24 * 60)
            val suggested = LocalTime.of(normalizedMins / 60, normalizedMins % 60)

            val defaultEnabled = anchor in setOf(
                SolarAnchor.SUNRISE,
                SolarAnchor.MIDDAY,
                SolarAnchor.SUNSET,
                SolarAnchor.EVENING,
            ) || ("hour:${hour.id}" in favorites) || (hour.id in prayedHourIds)

            SmartScheduleEntry(
                hourId = hour.id,
                suggestedTime = "%02d:%02d".format(suggested.hour, suggested.minute),
                solarAnchor = anchor.name,
                sunriseTime = "%02d:%02d".format(solar.sunrise.hour, solar.sunrise.minute),
                sunsetTime = "%02d:%02d".format(solar.sunset.hour, solar.sunset.minute),
                solarNoonTime = "%02d:%02d".format(solar.solarNoon.hour, solar.solarNoon.minute),
                locationLabel = locationLabel,
                latitude = latitude,
                longitude = longitude,
                weatherSummary = solar.weatherFor(lang),
                enabled = existingEnabled[hour.id] ?: defaultEnabled,
                updatedAt = nowMillis,
            )
        }
    }

    private fun anchorForHourId(hourId: String, tradition: Tradition): SolarAnchor = when (hourId) {
        "matins", "readings" -> SolarAnchor.DAWN
        "morning", "prime", "lauds", "first" -> SolarAnchor.SUNRISE
        "third", "terce" -> SolarAnchor.MORNING
        "sixth", "sext" -> SolarAnchor.MIDDAY
        "ninth", "none" -> SolarAnchor.AFTERNOON
        "vespers" -> SolarAnchor.SUNSET
        "compline" -> if (tradition == Tradition.ORIENTAL_ORTHODOX) SolarAnchor.SUNSET else SolarAnchor.EVENING
        "veil", "evening", "midnight" -> SolarAnchor.NIGHT
        else -> SolarAnchor.MORNING
    }

    /**
     * Maps each canonical prayer hour to an accurate, realistic time in minutes from 00:00:
     * - Dawn & Morning prayers (`matins`, `readings`, `morning`, `prime`, `first`, `lauds`) anchor to local Sunrise,
     *   clamped to sensible morning bounds so high-latitude winter/summer extremes stay practical.
     * - Daytime Little Hours (`third`/`terce`, `sixth`/`sext`, `ninth`/`none`) stay true to the 3rd (09:00),
     *   6th (12:00), and 9th (15:00) hours of the liturgical day.
     * - Evening & Night Hours (`vespers`, `compline`, `veil`, `evening`, `midnight`) respect both local Sunset
     *   (for Vespers / Agpeya 11th & 12th hour) and real bedtime/midnight hours (`21:00`, `21:30`, `22:00`, `00:00`).
     */
    internal fun solarMinutesForHour(
        hourId: String,
        tradition: Tradition,
        fallbackTime: String,
        sunriseMins: Int,
        solarNoonMins: Int,
        sunsetMins: Int,
    ): Pair<SolarAnchor, Int> {
        fun round5(mins: Int): Int = ((mins + 2) / 5) * 5

        // Practical sunrise window (06:00 .. 07:45) and sunset window (17:00 .. 20:30)
        val practicalSunrise = round5(sunriseMins).coerceIn(6 * 60, 7 * 60 + 45)
        val practicalSunset = round5(sunsetMins).coerceIn(17 * 60, 20 * 60 + 30)

        return when (hourId) {
            // Metten (Orthros) / Lezingendienst: 30 min vóór zonsopgang (tussen 05:30 en 07:00)
            "matins", "readings" -> {
                val t = (practicalSunrise - 30).coerceIn(5 * 60 + 30, 7 * 60)
                SolarAnchor.DAWN to t
            }
            // Morgengebeden / Priem (1e uur Agpeya): bij zonsopgang (tussen 06:00 en 07:30)
            "morning", "prime" -> {
                val t = practicalSunrise.coerceIn(6 * 60, 7 * 60 + 30)
                SolarAnchor.SUNRISE to t
            }
            // Lauden (Katholiek morgengebed): bij zonsopgang (tussen 06:30 en 07:45)
            "lauds" -> {
                val t = practicalSunrise.coerceIn(6 * 60 + 30, 7 * 60 + 45)
                SolarAnchor.SUNRISE to t
            }
            // Eerste uur (Horologion): 30 min na morgengebed / zonsopgang (tussen 07:00 en 08:00)
            "first" -> {
                val morningTime = practicalSunrise.coerceIn(6 * 60, 7 * 60 + 30)
                val t = (morningTime + 30).coerceIn(7 * 60, 8 * 60)
                SolarAnchor.SUNRISE to t
            }
            // Derde uur / Terts: 09:00 (het 3e uur van de dag)
            "third", "terce" -> {
                SolarAnchor.MORNING to (9 * 60)
            }
            // Zesde uur / Sext: 12:00 (het 6e uur / middaguur)
            "sixth", "sext" -> {
                SolarAnchor.MIDDAY to (12 * 60)
            }
            // Negende uur / None: 15:00 (het 9e uur van de dag)
            "ninth", "none" -> {
                SolarAnchor.AFTERNOON to (15 * 60)
            }
            // Vespers:
            // - In de Oriëntaals-Orthodoxe Agpeya is Vespers het 11e uur (vóór zonsondergang, ~45 min vóór sunset, 16:30..18:00)
            // - In Oosters-Orthodox & Katholiek is Vespers het lichtontstekingsgebed bij zonsondergang (17:00..20:00)
            "vespers" -> {
                if (tradition == Tradition.ORIENTAL_ORTHODOX) {
                    val t = (practicalSunset - 45).coerceIn(16 * 60 + 30, 18 * 60)
                    SolarAnchor.SUNSET to t
                } else {
                    val t = practicalSunset.coerceIn(17 * 60, 20 * 60)
                    SolarAnchor.SUNSET to t
                }
            }
            // Completen:
            // - In de Agpeya (Oriëntaals-Orthodox) is Completen het 12e uur bij zonsondergang (17:30..19:30)
            // - In Katholiek Getijdenboek is Completen het nachtgebed om 21:30
            // - In Oosters-Orthodox Horologion zijn de Kleine Completen om 21:00
            "compline" -> {
                when (tradition) {
                    Tradition.ORIENTAL_ORTHODOX -> {
                        val t = practicalSunset.coerceIn(17 * 60 + 30, 19 * 60 + 30)
                        SolarAnchor.EVENING to t
                    }
                    Tradition.CATHOLIC -> SolarAnchor.EVENING to (21 * 60 + 30)
                    Tradition.EASTERN_ORTHODOX -> SolarAnchor.EVENING to (21 * 60)
                }
            }
            // Het Voorhangsel (Agpeya) — avondgebed vóór het slapengaan: 21:00
            "veil" -> {
                SolarAnchor.NIGHT to (21 * 60)
            }
            // Avondgebeden vóór het slapengaan (Horologion): 22:00
            "evening" -> {
                SolarAnchor.NIGHT to (22 * 60)
            }
            // Middernachtsdienst / Middernacht: 00:00
            "midnight" -> {
                SolarAnchor.NIGHT to 0
            }
            else -> {
                val parsed = runCatching { LocalTime.parse(fallbackTime) }.getOrDefault(LocalTime.of(8, 0))
                SolarAnchor.MORNING to (parsed.hour * 60 + parsed.minute)
            }
        }
    }

    /**
     * Fetches today's sunrise, sunset, and current weather from the Open-Meteo API in the target timezone,
     * with Sunrise-Sunset.org and astronomical NOAA calculation as fallbacks.
     */
    suspend fun fetchSolarAndWeather(
        latitude: Double,
        longitude: Double,
        date: LocalDate = LocalDate.now(),
        targetZone: ZoneId = zoneForCoordinates(latitude, longitude),
    ): SolarWeatherInfo = withContext(Dispatchers.IO) {
        // 1. Primary: Open-Meteo Weather & Solar API (HTTPS, no key required)
        runCatching {
            val encodedTz = URLEncoder.encode(targetZone.id, "UTF-8")
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&daily=sunrise,sunset&current=temperature_2m,weather_code" +
                "&timezone=$encodedTz&forecast_days=1"
            val root = getJson(url).jsonObject
            val resolvedTz = root["timezone"]?.jsonPrimitive?.content ?: targetZone.id
            val daily = root["daily"]?.jsonObject
            val sunriseIso = daily?.get("sunrise")?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            val sunsetIso = daily?.get("sunset")?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            if (sunriseIso != null && sunsetIso != null) {
                val sunrise = LocalDateTime.parse(sunriseIso).toLocalTime()
                val sunset = LocalDateTime.parse(sunsetIso).toLocalTime()
                val noonMins = ((sunrise.toSecondOfDay() + sunset.toSecondOfDay()) / 2) / 60
                val solarNoon = LocalTime.of((noonMins / 60) % 24, noonMins % 60)

                val current = root["current"]?.jsonObject
                val temp = current?.get("temperature_2m")?.jsonPrimitive?.doubleOrNull
                val code = current?.get("weather_code")?.jsonPrimitive?.intOrNull ?: 0
                val (descEn, descNl) = weatherDescription(code)
                val tempPrefix = temp?.let { "${it.roundToInt()}°C · " }.orEmpty()

                return@withContext SolarWeatherInfo(
                    sunrise = sunrise,
                    sunset = sunset,
                    solarNoon = solarNoon,
                    weatherSummaryEn = "$tempPrefix$descEn",
                    weatherSummaryNl = "$tempPrefix$descNl",
                    source = "Open-Meteo Weather & Solar API",
                    timezoneId = resolvedTz,
                )
            }
        }

        // 2. Secondary: api.sunrise-sunset.org (returns UTC ISO timestamps, converted to targetZone)
        runCatching {
            val url = "https://api.sunrise-sunset.org/json?lat=$latitude&lng=$longitude&date=$date&formatted=0"
            val results = getJson(url).jsonObject["results"]?.jsonObject
            val sr = results?.get("sunrise")?.jsonPrimitive?.content
            val ss = results?.get("sunset")?.jsonPrimitive?.content
            if (sr != null && ss != null) {
                val sunrise = Instant.parse(sr).atZone(targetZone).toLocalTime()
                val sunset = Instant.parse(ss).atZone(targetZone).toLocalTime()
                val noonMins = ((sunrise.toSecondOfDay() + sunset.toSecondOfDay()) / 2) / 60
                val solarNoon = LocalTime.of((noonMins / 60) % 24, noonMins % 60)
                return@withContext SolarWeatherInfo(
                    sunrise = sunrise,
                    sunset = sunset,
                    solarNoon = solarNoon,
                    weatherSummaryEn = "Solar Ephemeris (Online)",
                    weatherSummaryNl = "Zonnestand (Online)",
                    source = "sunrise-sunset.org",
                    timezoneId = targetZone.id,
                )
            }
        }

        // 3. Offline Fallback: NOAA Astronomical Sunrise/Sunset calculation in targetZone
        computeAstronomicalSolar(latitude, longitude, date, targetZone)
    }

    internal fun computeAstronomicalSolar(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        zone: ZoneId,
    ): SolarWeatherInfo {
        val dayOfYear = date.dayOfYear
        val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1)
        val eqTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
            )
        val decl = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) +
            0.00148 * sin(3 * gamma)

        val latRad = Math.toRadians(latitude.coerceIn(-65.0, 65.0))
        val zenith = Math.toRadians(90.833)
        val cosHa = ((cos(zenith) / (cos(latRad) * cos(decl))) - tan(latRad) * tan(decl)).coerceIn(-1.0, 1.0)
        val haDeg = Math.toDegrees(acos(cosHa))

        val offsetMinutes = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 60.0
        val solarNoonMins = (720.0 - 4.0 * longitude - eqTime + offsetMinutes).roundToInt()
        val sunriseMins = (solarNoonMins - haDeg * 4.0).roundToInt().coerceIn(4 * 60, 10 * 60)
        val sunsetMins = (solarNoonMins + haDeg * 4.0).roundToInt().coerceIn(15 * 60, 22 * 60)
        val noonClamped = ((sunriseMins + sunsetMins) / 2).coerceIn(11 * 60, 14 * 60)

        return SolarWeatherInfo(
            sunrise = LocalTime.of(sunriseMins / 60, sunriseMins % 60),
            sunset = LocalTime.of(sunsetMins / 60, sunsetMins % 60),
            solarNoon = LocalTime.of(noonClamped / 60, noonClamped % 60),
            weatherSummaryEn = "Astronomical Solar Calculation",
            weatherSummaryNl = "Astronomische Zonnestandberekening",
            source = "Astronomical",
            timezoneId = zone.id,
        )
    }

    /**
     * Infers the appropriate IANA timezone for a given `(latitude, longitude)` pair so that even if the
     * Android emulator's system timezone is `America/Los_Angeles` or `UTC`, Dutch and international cities
     * always calculate sunrise/sunset in their real local timezone.
     */
    fun zoneForCoordinates(latitude: Double, longitude: Double): ZoneId {
        presetCities.firstOrNull {
            abs(it.latitude - latitude) < 0.35 && abs(it.longitude - longitude) < 0.35
        }?.let { return ZoneId.of(it.timezone) }

        return when {
            // Netherlands, Belgium, Luxembourg, France, Germany, Spain, Italy, Central/Western Europe
            latitude in 36.0..60.0 && longitude in 2.5..18.0 -> ZoneId.of("Europe/Amsterdam")
            // UK & Ireland
            latitude in 49.5..61.0 && longitude in -10.5..2.0 -> ZoneId.of("Europe/London")
            // Greece, Romania, Bulgaria, Finland, Baltics
            latitude in 34.5..65.0 && longitude in 19.0..28.0 -> ZoneId.of("Europe/Athens")
            // Turkey
            latitude in 35.5..42.5 && longitude in 26.0..45.0 -> ZoneId.of("Europe/Istanbul")
            // Israel / Palestine / Jordan / Lebanon
            latitude in 29.0..34.5 && longitude in 34.0..37.0 -> ZoneId.of("Asia/Jerusalem")
            // Egypt
            latitude in 22.0..32.0 && longitude in 25.0..34.0 -> ZoneId.of("Africa/Cairo")
            else -> {
                val approxHours = (longitude / 15.0).roundToInt().coerceIn(-12, 14)
                ZoneOffset.ofHours(approxHours)
            }
        }
    }

    /** Detects the Android emulator's hardcoded default Googleplex Mountain View GPS coordinate (37.422, -122.084). */
    private fun isEmulatorDefaultLocation(lat: Double, lon: Double): Boolean =
        lat in 37.38..37.46 && lon in -122.14..-122.04

    @SuppressLint("MissingPermission")
    private suspend fun getDeviceLocation(): Location? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val lastKnown = providers.mapNotNull { p ->
            runCatching { if (lm.isProviderEnabled(p)) lm.getLastKnownLocation(p) else null }.getOrNull()
        }.maxByOrNull { it.time }
        if (lastKnown != null) return@withContext lastKnown

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val activeProvider = providers.firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
                ?: return@withContext null
            withTimeoutOrNull(4_000L) {
                suspendCancellableCoroutine { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    runCatching {
                        lm.getCurrentLocation(
                            activeProvider,
                            signal,
                            context.mainExecutor,
                        ) { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                    }.onFailure {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } else null
    }

    private fun reverseGeocode(lat: Double, lon: Double): String? = runCatching {
        @Suppress("DEPRECATION")
        val list = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
        val addr = list?.firstOrNull() ?: return null
        val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea
        val country = addr.countryCode
        if (!city.isNullOrBlank() && !country.isNullOrBlank()) "$city, $country"
        else city ?: country
    }.getOrNull()

    private fun defaultCityForTimezone(): GeoCity {
        val tz = ZoneId.systemDefault().id
        return when {
            tz.contains("Brussels", ignoreCase = true) -> presetCities.first { it.name == "Brussel" }
            tz.contains("Rome", ignoreCase = true) -> presetCities.first { it.name == "Rome" }
            tz.contains("Jerusalem", ignoreCase = true) -> presetCities.first { it.name == "Jeruzalem" }
            tz.contains("Istanbul", ignoreCase = true) -> presetCities.first { it.name.startsWith("Istanbul") }
            tz.contains("Cairo", ignoreCase = true) -> presetCities.first { it.name == "Alexandrië" }
            tz.contains("London", ignoreCase = true) -> presetCities.first { it.name == "London" }
            else -> presetCities.first() // Amsterdam, Nederland
        }
    }

    private fun weatherDescription(code: Int): Pair<String, String> = when (code) {
        0 -> "Clear sky" to "Heldere hemel"
        1, 2 -> "Partly cloudy" to "Licht bewolkt"
        3 -> "Overcast" to "Bewolkt"
        45, 48 -> "Mist & fog" to "Mistig"
        51, 53, 55, 56, 57 -> "Light drizzle" to "Lichte motregen"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Rain" to "Regenachtig"
        71, 73, 75, 77, 85, 86 -> "Snow" to "Sneeuw"
        95, 96, 99 -> "Thunderstorm" to "Onweer"
        else -> "Calm weather" to "Rustig weer"
    }

    private fun getJson(url: String) = URL(url).openConnection().let { raw ->
        val conn = raw as HttpURLConnection
        conn.connectTimeout = 6_000
        conn.readTimeout = 6_000
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            json.parseToJsonElement(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
