package agape.app.ui.me

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.Tradition
import agape.app.core.model.VisualStyle
import agape.app.data.content.PrayerHour
import agape.app.notifications.CustomReminder
import agape.app.notifications.HourReminder
import agape.app.notifications.Notifications
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.LocalSettings
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Divider
import agape.app.ui.components.Kicker
import agape.app.ui.components.Pill
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.QuietButton
import agape.app.ui.onboarding.LanguageSwitch
import agape.app.ui.onboarding.StyleSwatches
import agape.app.ui.onboarding.ThemeModeSwitch
import agape.app.ui.onboarding.styleName
import agape.app.ui.onboarding.traditionName
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, openLegal: (String) -> Unit) {
    val container = LocalContainer.current
    val settings = LocalSettings.current
    val lang = LocalLanguage.current
    val currentTradition = tradition
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = AgapeTheme.colors
    val nl = lang == AppLanguage.DUTCH

    var pendingTradition by remember { mutableStateOf<Tradition?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var fontScale by remember(settings.fontScale) { mutableFloatStateOf(settings.fontScale) }
    var editingHourTime by remember { mutableStateOf<Pair<PrayerHour, LocalTime>?>(null) }
    var editingCustomReminder by remember { mutableStateOf<CustomReminder?>(null) }
    var addingCustomReminder by remember { mutableStateOf(false) }
    var testSentFeedback by remember { mutableStateOf(false) }

    val book = remember(currentTradition) { container.content.prayerBook(currentTradition) }
    val activeHourReminders = remember(settings.hourReminders) {
        settings.hourReminders.mapNotNull { HourReminder.decode(it) }.associateBy { it.hourId }
    }
    val customReminders = remember(settings.customReminders) {
        settings.customReminders.mapNotNull { CustomReminder.decode(it) }.sortedBy { it.time }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { setPrayerNotifications(container, true) }
    }
    val testPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Notifications.sendTestPrayerNotification(context)
        testSentFeedback = true
    }

    pendingTradition?.let { t ->
        AlertDialog(
            onDismissRequest = { pendingTradition = null },
            title = { Text(stringResource(R.string.settings_change_church)) },
            text = { Text(stringResource(R.string.settings_change_church_text, stringResource(traditionName(t)))) },
            confirmButton = {
                TextButton({
                    pendingTradition = null
                    scope.launch {
                        container.settings.setTradition(t)
                        // Hours differ per tradition, so reminders start again from the new defaults.
                        container.settings.setHourReminders(HourReminder.defaults(container.content.prayerBook(t).hours))
                        Notifications.reschedule(context)
                    }
                }) { Text(stringResource(R.string.continue_label)) }
            },
            dismissButton = { TextButton({ pendingTradition = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    editingHourTime?.let { (hour, initialTime) ->
        val pickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { editingHourTime = null },
            containerColor = c.surface,
            title = {
                Text(
                    hour.name[lang],
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.ink,
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (nl) "Kies het tijdstip waarop u voor dit gebedsuur herinnerd wilt worden:"
                        else "Choose the time of day to be reminded for this prayer hour:",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkMuted,
                    )
                    Spacer(Modifier.height(14.dp))
                    TimeInput(
                        state = pickerState,
                        colors = TimePickerDefaults.colors(
                            timeSelectorSelectedContainerColor = c.primary.copy(alpha = 0.16f),
                            timeSelectorSelectedContentColor = c.primary,
                            timeSelectorUnselectedContainerColor = c.surfaceSunken,
                            timeSelectorUnselectedContentColor = c.ink,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton({
                    val newTime = LocalTime.of(pickerState.hour, pickerState.minute)
                    editingHourTime = null
                    scope.launch {
                        val updated = settings.hourReminders.filterNot { it.startsWith("${hour.id}@") }.toSet() +
                            HourReminder(hour.id, newTime).encode()
                        container.settings.setHourReminders(updated)
                        if (!settings.prayerNotifications) container.settings.setPrayerNotifications(true)
                        Notifications.reschedule(context)
                    }
                }) { Text(if (nl) "Opslaan" else "Save", color = c.primary) }
            },
            dismissButton = {
                TextButton({ editingHourTime = null }) { Text(stringResource(R.string.cancel), color = c.inkMuted) }
            },
        )
    }

    if (addingCustomReminder || editingCustomReminder != null) {
        val existing = editingCustomReminder
        val initialTime = existing?.time ?: LocalTime.of(8, 0)
        val pickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = true,
        )
        var label by remember(existing) {
            mutableStateOf(existing?.label ?: if (nl) "Dagelijks gebedsmoment" else "Daily prayer")
        }
        AlertDialog(
            onDismissRequest = {
                addingCustomReminder = false
                editingCustomReminder = null
            },
            containerColor = c.surface,
            title = {
                Text(
                    if (existing == null) (if (nl) "Eigen herinnering toevoegen" else "Add custom reminder")
                    else (if (nl) "Herinnering aanpassen" else "Edit reminder"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.ink,
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(if (nl) "Naam van herinnering" else "Reminder label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("custom_reminder_label_input"),
                    )
                    Spacer(Modifier.height(16.dp))
                    TimeInput(
                        state = pickerState,
                        colors = TimePickerDefaults.colors(
                            timeSelectorSelectedContainerColor = c.primary.copy(alpha = 0.16f),
                            timeSelectorSelectedContentColor = c.primary,
                            timeSelectorUnselectedContainerColor = c.surfaceSunken,
                            timeSelectorUnselectedContentColor = c.ink,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton({
                    val newTime = LocalTime.of(pickerState.hour, pickerState.minute)
                    val id = existing?.id ?: UUID.randomUUID().toString().take(8)
                    val cleanLabel = label.trim().ifBlank { if (nl) "Dagelijks gebed" else "Daily prayer" }
                    val reminder = CustomReminder(id, newTime, cleanLabel)
                    addingCustomReminder = false
                    editingCustomReminder = null
                    scope.launch {
                        val base = settings.customReminders.filterNot { it.startsWith("$id|") }.toSet()
                        container.settings.setCustomReminders(base + reminder.encode())
                        if (!settings.prayerNotifications) container.settings.setPrayerNotifications(true)
                        Notifications.reschedule(context)
                    }
                }) { Text(if (nl) "Opslaan" else "Save", color = c.primary) }
            },
            dismissButton = {
                TextButton({
                    addingCustomReminder = false
                    editingCustomReminder = null
                }) { Text(stringResource(R.string.cancel), color = c.inkMuted) }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_delete_account)) },
            text = { Text(stringResource(R.string.settings_delete_text)) },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    scope.launch {
                        runCatching { container.auth.deleteAccount() }
                        container.user.clear()
                        container.settings.clearPersonal()
                    }
                }) { Text(stringResource(R.string.settings_delete_confirm), color = c.danger) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(R.string.me_settings), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingsCard(R.string.settings_language) {
                LanguageSwitch()
            }
            SettingsCard(R.string.settings_style) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VisualStyle.entries.forEach { s ->
                        Pill(stringResource(styleName(s)), settings.style == s, { scope.launch { container.settings.setStyle(s) } })
                    }
                }
                Spacer(Modifier.height(12.dp))
                settings.style?.let { StyleSwatches(it) }
                Spacer(Modifier.height(12.dp))
                ThemeModeSwitch(settings.themeMode) { m -> scope.launch { container.settings.setThemeMode(m) } }
            }
            SettingsCard(R.string.settings_church) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tradition.entries.forEach { t ->
                        Pill(stringResource(traditionName(t)), settings.tradition == t, { if (settings.tradition != t) pendingTradition = t })
                    }
                }
            }
            SettingsCard(R.string.settings_notifications) {
                ToggleRow(stringResource(R.string.settings_notify_hours), stringResource(R.string.settings_notify_hours_hint), settings.prayerNotifications) { on ->
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else scope.launch { setPrayerNotifications(container, on) }
                }

                // Test button for church bell & notification
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.primary.copy(alpha = 0.12f))
                        .border(1.dp, c.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) {
                                testPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                Notifications.sendTestPrayerNotification(context)
                                testSentFeedback = true
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("test_bell_notification_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = c.primary, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (nl) "Test kerkklok & melding" else "Test church bell & notification",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = c.primary,
                        )
                        Text(
                            if (testSentFeedback) {
                                if (nl) "Kerkklok luidt nu met trilling en testmelding!" else "Church bell ringing with vibration and test alert!"
                            } else {
                                if (nl) "Laat de kerkklok klinken met trilling om uw herinnering te testen" else "Ring the church bell with vibration to test your reminder"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = c.inkMuted,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Divider()
                Spacer(Modifier.height(12.dp))

                // 1. Canonical prayer hours with adjustable times
                Kicker(if (nl) "Vaste gebedsuren (${book.book[lang]})" else "Canonical Prayer Hours (${book.book[lang]})")
                Spacer(Modifier.height(4.dp))
                Text(
                    if (nl) "Schakel gebedsuren in of tik op het tijdstip om het uur aan te passen."
                    else "Toggle prayer hours or tap the time badge to customize its time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkMuted,
                )
                Spacer(Modifier.height(8.dp))

                book.hours.forEach { hour ->
                    val activeReminder = activeHourReminders[hour.id]
                    val isEnabled = settings.prayerNotifications && activeReminder != null
                    val displayTime = activeReminder?.time ?: runCatching { LocalTime.parse(hour.time) }.getOrDefault(LocalTime.of(6, 0))
                    val timeLabel = "%02d:%02d".format(displayTime.hour, displayTime.minute)

                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(hour.name[lang], style = MaterialTheme.typography.titleSmall, color = c.ink)
                            Text(hour.subtitle[lang], style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                        }
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(if (isEnabled) c.primary.copy(alpha = 0.14f) else c.surfaceSunken)
                                .border(1.dp, if (isEnabled) c.primary.copy(alpha = 0.45f) else c.line, CircleShape)
                                .clickable { editingHourTime = hour to displayTime }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("hour_time_chip_${hour.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = if (isEnabled) c.primary else c.inkMuted, modifier = Modifier.size(14.dp))
                            Text(
                                timeLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = AgapeTheme.type.ui),
                                color = if (isEnabled) c.primary else c.inkMuted,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    val updated = if (checked) {
                                        settings.hourReminders.filterNot { it.startsWith("${hour.id}@") }.toSet() +
                                            HourReminder(hour.id, displayTime).encode()
                                    } else {
                                        settings.hourReminders.filterNot { it.startsWith("${hour.id}@") }.toSet()
                                    }
                                    container.settings.setHourReminders(updated)
                                    if (checked && !settings.prayerNotifications) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) {
                                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            container.settings.setPrayerNotifications(true)
                                        }
                                    }
                                    Notifications.reschedule(context)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))

                // 2. Custom daily reminder times
                Kicker(if (nl) "Eigen gebedsherinneringen" else "Custom Daily Reminders")
                Spacer(Modifier.height(4.dp))
                Text(
                    if (nl) "Voeg één of meerdere eigen tijdstippen per dag toe voor uw persoonlijke gebed."
                    else "Add one or more custom times per day for your personal prayer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkMuted,
                )
                Spacer(Modifier.height(8.dp))

                if (customReminders.isEmpty()) {
                    Text(
                        if (nl) "Nog geen eigen herinneringstijden ingesteld." else "No custom reminder times set yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkFaint,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    customReminders.forEach { item ->
                        val timeFormatted = "%02d:%02d".format(item.time.hour, item.time.minute)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.surfaceSunken)
                                .clickable { editingCustomReminder = item }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(c.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    timeFormatted,
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = AgapeTheme.type.ui, fontWeight = FontWeight.Bold),
                                    color = c.primary,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                item.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = c.ink,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val updated = settings.customReminders.filterNot { it.startsWith("${item.id}|") }.toSet()
                                        container.settings.setCustomReminders(updated)
                                        Notifications.reschedule(context)
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = if (nl) "Verwijderen" else "Delete",
                                    tint = c.inkMuted,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .clip(CircleShape)
                        .border(1.dp, c.primary, CircleShape)
                        .clickable { addingCustomReminder = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("add_custom_reminder_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
                    Text(
                        if (nl) "Eigen tijdstip toevoegen" else "Add custom time",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.primary,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(8.dp))

                ToggleRow(stringResource(R.string.settings_notify_inactive), stringResource(R.string.settings_notify_inactive_hint), settings.inactivityReminders) { on ->
                    scope.launch {
                        container.settings.setInactivityReminders(on)
                        Notifications.reschedule(context)
                    }
                }
            }
            SettingsCard(R.string.settings_font_size) {
                Text(
                    "Aa — " + stringResource(R.string.settings_font_preview),
                    style = AgapeTheme.type.scripture.copy(fontSize = AgapeTheme.type.scripture.fontSize * (fontScale / settings.fontScale)),
                    color = c.ink,
                )
                Slider(
                    value = fontScale, onValueChange = { fontScale = it }, valueRange = 0.85f..1.6f, steps = 6,
                    onValueChangeFinished = { scope.launch { container.settings.setFontScale(fontScale) } },
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary),
                )
            }
            SettingsCard(R.string.settings_more) {
                QuietButton(stringResource(R.string.settings_replay_tour), { scope.launch { container.settings.resetTour() } })
                QuietButton(stringResource(R.string.legal_privacy), { openLegal("privacy") })
                QuietButton(stringResource(R.string.legal_terms), { openLegal("terms") })
                QuietButton(stringResource(R.string.settings_delete_account), { confirmDelete = true })
            }
        }
    }
}

private suspend fun setPrayerNotifications(container: agape.app.AppContainer, on: Boolean) {
    val s = container.settings.current()
    container.settings.setPrayerNotifications(on)
    if (on && s.hourReminders.isEmpty() && s.tradition != null) {
        container.settings.setHourReminders(HourReminder.defaults(container.content.prayerBook(s.tradition).hours))
    }
    Notifications.reschedule(container.appContext())
}

@Composable
private fun SettingsCard(title: Int, content: @Composable () -> Unit) {
    AgapeCard {
        Kicker(stringResource(title))
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = AgapeTheme.colors
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary))
    }
}
