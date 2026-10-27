package agape.app.ui.prayer

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.data.schedule.SolarAnchor
import agape.app.data.user.SmartScheduleEntry
import agape.app.notifications.Notifications
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.Divider
import agape.app.ui.components.Kicker
import agape.app.ui.components.Pill
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SmartScheduleCard(
    modifier: Modifier = Modifier,
    openHour: ((String) -> Unit)? = null,
    initiallyExpanded: Boolean = false,
) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val nl = lang == AppLanguage.DUTCH
    val currentTradition = tradition
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = AgapeTheme.colors

    val scheduleEntries by container.user.smartSchedule.collectAsStateWithLifecycle(emptyList())
    val activities by container.user.activity.collectAsStateWithLifecycle(emptyList())
    val book = remember(currentTradition) { container.content.prayerBook(currentTradition) }
    val hoursById = remember(book) { book.hours.associateBy { it.id } }

    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var useCanonicalFixed by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var showCityDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<SmartScheduleEntry?>(null) }
    var appliedFeedback by remember { mutableStateOf<String?>(null) }

    // Populate or refresh Room smart_schedule table when empty, when tradition changes,
    // or when legacy entries (e.g. midnight=23:30 or emulator Mountain View coords) are detected.
    val needsRefresh = remember(scheduleEntries, hoursById) {
        val matchesTradition = scheduleEntries.isNotEmpty() && scheduleEntries.any { it.hourId in hoursById }
        val hasLegacyMidnight = scheduleEntries.any { it.hourId == "midnight" && it.suggestedTime == "23:30" }
        val hasEmulatorCoords = scheduleEntries.any { it.latitude in 37.38..37.46 && it.longitude in -122.14..-122.04 }
        !matchesTradition || hasLegacyMidnight || hasEmulatorCoords
    }
    LaunchedEffect(currentTradition, needsRefresh) {
        if (needsRefresh && !loading) {
            loading = true
            runCatching {
                container.smartSchedule.generateFromDeviceLocation(
                    tradition = currentTradition,
                    lang = lang,
                    useCanonicalFixedTimes = useCanonicalFixed,
                )
            }
            loading = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        scope.launch {
            loading = true
            appliedFeedback = null
            runCatching {
                container.smartSchedule.generateFromDeviceLocation(
                    tradition = currentTradition,
                    lang = lang,
                    useCanonicalFixedTimes = useCanonicalFixed,
                )
            }
            loading = false
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        scope.launch {
            val count = container.smartSchedule.applySmartScheduleToReminders()
            appliedFeedback = if (nl) {
                "✓ $count gebedstijden uit het Slim Rooster ingesteld als dagelijkse kerkklok-herinnering!"
            } else {
                "✓ $count Smart Schedule prayer times applied to your daily church-bell reminders!"
            }
        }
    }

    // Time editor dialog for any individual Smart Schedule entry (persisted directly into Room)
    editingEntry?.let { entry ->
        val hourObj = hoursById[entry.hourId]
        val parsed = runCatching { LocalTime.parse(entry.suggestedTime) }.getOrDefault(LocalTime.of(8, 0))
        val pickerState = rememberTimePickerState(
            initialHour = parsed.hour,
            initialMinute = parsed.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { editingEntry = null },
            containerColor = c.surface,
            title = {
                Text(
                    hourObj?.name?.get(lang) ?: entry.hourId,
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.ink,
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (nl) "Pas het tijdstip voor dit gebedsuur aan (wordt opgeslagen in Room):"
                        else "Customize the time for this prayer hour (saved in Room):",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkMuted,
                    )
                    TimeInput(state = pickerState)
                    if (hourObj != null) {
                        TextButton(
                            onClick = {
                                val canonical = hourObj.time
                                scope.launch {
                                    container.user.updateSmartScheduleTime(
                                        entry.copy(
                                            suggestedTime = canonical,
                                            enabled = true,
                                            updatedAt = System.currentTimeMillis(),
                                        ),
                                    )
                                }
                                editingEntry = null
                            },
                        ) {
                            Text(
                                if (nl) "Herstel naar klassieke tijd (${hourObj.time})"
                                else "Reset to canonical time (${hourObj.time})",
                                color = c.accent,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newTime = "%02d:%02d".format(pickerState.hour, pickerState.minute)
                        scope.launch {
                            container.user.updateSmartScheduleTime(
                                entry.copy(
                                    suggestedTime = newTime,
                                    enabled = true,
                                    updatedAt = System.currentTimeMillis(),
                                ),
                            )
                        }
                        editingEntry = null
                    },
                ) {
                    Text(if (nl) "Opslaan" else "Save", color = c.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEntry = null }) {
                    Text(stringResource(R.string.cancel), color = c.inkMuted)
                }
            },
        )
    }

    if (showCityDialog) {
        var searchQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf(container.smartSchedule.presetCities) }
        var searching by remember { mutableStateOf(false) }

        LaunchedEffect(searchQuery) {
            if (searchQuery.trim().length < 2) {
                searchResults = container.smartSchedule.presetCities
                searching = false
            } else {
                searching = true
                delay(280)
                searchResults = container.smartSchedule.searchCities(searchQuery, lang)
                searching = false
            }
        }

        AlertDialog(
            onDismissRequest = { showCityDialog = false },
            containerColor = c.surface,
            title = {
                Text(
                    if (nl) "Kies locatie voor Zonnestand" else "Choose Location for Solar Times",
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.ink,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (nl) "Zoek uw stad of kies een plek om lokale zonsopgang, zonnemiddag en zonsondergang in de juiste tijdzone op te halen:"
                        else "Search your city or pick a place to fetch local sunrise, solar noon, and sunset in its true timezone:",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkMuted,
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(if (nl) "Stad zoeken (bijv. Amsterdam, Utrecht)" else "Search city (e.g. Amsterdam, London)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("smart_schedule_city_search"),
                    )
                    if (searching) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = c.primary)
                            Text(if (nl) "Steden zoeken..." else "Searching cities...", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        searchResults.take(10).forEach { city ->
                            Pill(
                                text = city.label,
                                selected = false,
                                onClick = {
                                    showCityDialog = false
                                    scope.launch {
                                        loading = true
                                        appliedFeedback = null
                                        runCatching {
                                            container.smartSchedule.generateForCity(
                                                city = city,
                                                tradition = currentTradition,
                                                lang = lang,
                                                useCanonicalFixedTimes = useCanonicalFixed,
                                            )
                                        }
                                        loading = false
                                    }
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({ showCityDialog = false }) {
                    Text(stringResource(R.string.cancel), color = c.inkMuted)
                }
            },
        )
    }

    val firstEntry = scheduleEntries.firstOrNull { it.hourId in hoursById } ?: scheduleEntries.firstOrNull()
    val visibleEntries = remember(scheduleEntries, hoursById) {
        book.hours.mapNotNull { h -> scheduleEntries.firstOrNull { it.hourId == h.id } }
    }
    val roomInsight = remember(activities, lang) {
        container.smartSchedule.roomActivityInsight(activities, lang)
    }

    AgapeCard(modifier = modifier.testTag("smart_schedule_card")) {
        // Top header row
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Kicker(
                    if (nl) "Slim Gebedsrooster · Zonnestand & Room"
                    else "Smart Schedule · Solar & Room DB",
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (nl) "Gebedstijden op lokale zonsopgang & -ondergang"
                    else "Prayer times tuned to local sunrise & sunset",
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.ink,
                )
            }
            if (loading) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = c.accent)
            } else {
                Icon(
                    Icons.Outlined.WbSunny,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Location & Weather pill bar
        if (firstEntry != null) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(c.surfaceSunken)
                        .border(0.8.dp, c.accent.copy(alpha = 0.45f), CircleShape)
                        .clickable { showCityDialog = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("smart_schedule_location_chip"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = c.primary, modifier = Modifier.size(15.dp))
                    Text(
                        firstEntry.locationLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (firstEntry.weatherSummary.isNotBlank()) {
                    Text(
                        firstEntry.weatherSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 3 Solar summary badges (Sunrise, Solar Noon, Sunset)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SolarTimeBadge(
                    symbol = "☀️",
                    label = if (nl) "Zonsopgang" else "Sunrise",
                    time = firstEntry.sunriseTime,
                    modifier = Modifier.weight(1f),
                )
                SolarTimeBadge(
                    symbol = "🕛",
                    label = if (nl) "Zonnemiddag" else "Solar Noon",
                    time = firstEntry.solarNoonTime,
                    modifier = Modifier.weight(1f),
                )
                SolarTimeBadge(
                    symbol = "🌇",
                    label = if (nl) "Zonsondergang" else "Sunset",
                    time = firstEntry.sunsetTime,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Mode toggle: Solar-tuned vs. Canonical Fixed Hours
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(
                text = if (nl) "☀️ Zonnestand-ritme" else "☀️ Solar Rhythm",
                selected = !useCanonicalFixed,
                onClick = {
                    if (useCanonicalFixed) {
                        useCanonicalFixed = false
                        scope.launch {
                            loading = true
                            appliedFeedback = null
                            runCatching {
                                val f = firstEntry
                                if (f != null) {
                                    container.smartSchedule.generateForCoordinates(
                                        latitude = f.latitude,
                                        longitude = f.longitude,
                                        locationLabel = f.locationLabel,
                                        tradition = currentTradition,
                                        lang = lang,
                                        useCanonicalFixedTimes = false,
                                    )
                                } else {
                                    container.smartSchedule.generateFromDeviceLocation(currentTradition, lang, false)
                                }
                            }
                            loading = false
                        }
                    }
                },
            )
            Pill(
                text = if (nl) "☦ Klassiek vast rooster" else "☦ Canonical Fixed",
                selected = useCanonicalFixed,
                onClick = {
                    if (!useCanonicalFixed) {
                        useCanonicalFixed = true
                        scope.launch {
                            loading = true
                            appliedFeedback = null
                            runCatching {
                                val f = firstEntry
                                if (f != null) {
                                    container.smartSchedule.generateForCoordinates(
                                        latitude = f.latitude,
                                        longitude = f.longitude,
                                        locationLabel = f.locationLabel,
                                        tradition = currentTradition,
                                        lang = lang,
                                        useCanonicalFixedTimes = true,
                                    )
                                } else {
                                    container.smartSchedule.generateFromDeviceLocation(currentTradition, lang, true)
                                }
                            }
                            loading = false
                        }
                    }
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            roomInsight,
            style = MaterialTheme.typography.bodySmall,
            color = c.inkMuted,
        )

        Spacer(Modifier.height(10.dp))

        // Action row: GPS Refresh, City Picker, Expand/Collapse hours
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(c.primary.copy(alpha = 0.12f))
                    .border(0.8.dp, c.primary.copy(alpha = 0.4f), CircleShape)
                    .clickable {
                        if (container.smartSchedule.hasLocationPermission()) {
                            scope.launch {
                                loading = true
                                appliedFeedback = null
                                runCatching {
                                    container.smartSchedule.generateFromDeviceLocation(
                                        tradition = currentTradition,
                                        lang = lang,
                                        useCanonicalFixedTimes = useCanonicalFixed,
                                    )
                                }
                                loading = false
                            }
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("smart_schedule_gps_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Outlined.Explore, contentDescription = null, tint = c.primary, modifier = Modifier.size(16.dp))
                Text(
                    if (nl) "GPS Locatie" else "Use GPS",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = c.primary,
                )
            }

            Row(
                Modifier
                    .clip(CircleShape)
                    .background(c.surfaceSunken)
                    .border(0.8.dp, c.line, CircleShape)
                    .clickable { showCityDialog = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("smart_schedule_choose_city_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(16.dp))
                Text(
                    if (nl) "Kies stad" else "Choose city",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.ink,
                )
            }

            Row(
                Modifier
                    .clip(CircleShape)
                    .background(c.surfaceSunken)
                    .border(0.8.dp, c.line, CircleShape)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("smart_schedule_toggle_expand"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (expanded) (if (nl) "Verberg uren" else "Hide hours")
                    else (if (nl) "Toon & wijzig ${visibleEntries.size} uren" else "Show & edit ${visibleEntries.size} hours"),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.ink,
                )
                Icon(
                    if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = c.inkMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && visibleEntries.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically(),
        ) {
            Column(Modifier.padding(top = 12.dp)) {
                Divider()
                Spacer(Modifier.height(6.dp))
                Text(
                    if (nl) "Tik op een tijdstip om het handmatig aan te passen, of tik op de naam om het gebedsuur te openen."
                    else "Tap any time badge to customize it manually, or tap the hour name to pray.",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.inkFaint,
                )
                Spacer(Modifier.height(6.dp))
                visibleEntries.forEach { entry ->
                    val hour = hoursById[entry.hourId] ?: return@forEach
                    val anchor = SolarAnchor.fromKey(entry.solarAnchor)
                    val differsFromCanonical = entry.suggestedTime != hour.time
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Tappable time badge to customize the exact time in Room
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (entry.enabled) c.primary.copy(alpha = 0.15f) else c.surfaceSunken)
                                .border(0.8.dp, if (entry.enabled) c.accent.copy(alpha = 0.65f) else c.line, RoundedCornerShape(10.dp))
                                .clickable { editingEntry = entry }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("smart_schedule_time_${hour.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.AccessTime,
                                contentDescription = null,
                                tint = if (entry.enabled) c.primary else c.inkMuted,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                entry.suggestedTime,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = AgapeTheme.type.ui,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = if (entry.enabled) c.primary else c.inkMuted,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(
                            Modifier
                                .weight(1f)
                                .then(
                                    if (openHour != null) Modifier.clickable { openHour(hour.id) }
                                    else Modifier,
                                ),
                        ) {
                            Text(
                                hour.name[lang],
                                style = MaterialTheme.typography.titleSmall,
                                color = c.ink,
                            )
                            val canonicalSuffix = if (differsFromCanonical) {
                                if (nl) " · Klassiek ${hour.time}" else " · Canonical ${hour.time}"
                            } else ""
                            Text(
                                "${anchor.badge} ${anchor.title[lang]}$canonicalSuffix",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.inkMuted,
                            )
                        }
                        Switch(
                            checked = entry.enabled,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    container.user.setSmartScheduleEnabled(entry.hourId, checked)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = c.onPrimary,
                                checkedTrackColor = c.primary,
                            ),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Apply Smart Schedule to Daily Reminders button
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.primary)
                .border(0.8.dp, c.accent.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                .clickable {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        scope.launch {
                            val count = container.smartSchedule.applySmartScheduleToReminders()
                            appliedFeedback = if (nl) {
                                "✓ $count gebedstijden uit het Slim Rooster ingesteld als dagelijkse kerkklok-herinnering!"
                            } else {
                                "✓ $count Smart Schedule prayer times applied to your daily church-bell reminders!"
                            }
                        }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("apply_smart_schedule_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (nl) "Pas Slim Rooster toe op gebedsherinneringen"
                else "Apply Smart Schedule to Prayer Reminders",
                style = MaterialTheme.typography.labelLarge,
                color = c.onPrimary,
            )
        }

        appliedFeedback?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = c.primary,
                modifier = Modifier.testTag("smart_schedule_applied_feedback"),
            )
        }
    }
}

@Composable
private fun SolarTimeBadge(
    symbol: String,
    label: String,
    time: String,
    modifier: Modifier = Modifier,
) {
    val c = AgapeTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceSunken.copy(alpha = 0.72f))
            .border(0.8.dp, c.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "$symbol $time",
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = AgapeTheme.type.ui,
                fontWeight = FontWeight.Bold,
            ),
            color = c.ink,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = c.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
