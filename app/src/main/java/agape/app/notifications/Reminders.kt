package agape.app.notifications

import agape.app.AgapeApp
import agape.app.MainActivity
import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.L10n
import agape.app.data.content.PrayerHour
import agape.app.launchSafely
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** A reminder for one prayer hour, stored in settings as `hourId@HH:mm`. */
data class HourReminder(val hourId: String, val time: LocalTime) {
    fun encode(): String = "$hourId@%02d:%02d".format(time.hour, time.minute)

    companion object {
        fun decode(value: String): HourReminder? {
            val (id, t) = value.split('@').takeIf { it.size == 2 } ?: return null
            return runCatching { HourReminder(id, LocalTime.parse(t)) }.getOrNull()
        }

        /** A gentle default: one morning hour, Vespers and the hour before sleep. */
        fun defaults(hours: List<PrayerHour>): Set<String> {
            val morning = hours.firstOrNull { LocalTime.parse(it.time).hour in 6..7 }
            val evening = hours.firstOrNull { it.id == "vespers" }
            val night = hours.firstOrNull { it.id == "compline" }
            return listOfNotNull(morning, evening, night).map { HourReminder(it.id, LocalTime.parse(it.time)).encode() }.toSet()
        }
    }
}

/** A user-defined custom daily prayer reminder, stored in settings as `id|HH:mm|label`. */
data class CustomReminder(val id: String, val time: LocalTime, val label: String) {
    fun encode(): String = "$id|%02d:%02d|%s".format(time.hour, time.minute, label.replace('|', ' '))

    companion object {
        fun decode(value: String): CustomReminder? {
            val parts = value.split('|')
            if (parts.size < 2) return null
            val id = parts[0].ifBlank { return null }
            val time = runCatching { LocalTime.parse(parts[1]) }.getOrNull() ?: return null
            val label = parts.getOrNull(2)?.trim().orEmpty()
            return CustomReminder(id, time, label)
        }
    }
}

enum class IntentionCategory(val title: L10n) {
    LIVING(L10n("For the living & the sick", "Voor de levenden & zieken")),
    DEPARTED(L10n("For the departed", "Voor de ontslapenen")),
    THANKSGIVING(L10n("Thanksgiving & intentions", "Dankzegging & intenties")),
}

/** A personal prayer intention or name to commemorate during prayer, stored as `id|CATEGORY|text`. */
data class PrayerIntention(val id: String, val category: IntentionCategory, val text: String) {
    fun encode(): String = "$id|${category.name}|${text.replace('|', ' ').trim()}"

    companion object {
        fun decode(value: String): PrayerIntention? {
            val parts = value.split('|', limit = 3)
            if (parts.size < 3) return null
            val category = runCatching { IntentionCategory.valueOf(parts[1]) }.getOrDefault(IntentionCategory.LIVING)
            val text = parts[2].trim().ifBlank { return null }
            return PrayerIntention(parts[0], category, text)
        }
    }
}

object Notifications {
    const val CHANNEL_PRAYER = "prayer_hours_bell_v3"
    const val CHANNEL_REMINDERS = "gentle_reminders"
    private const val INACTIVITY_WORK = "inactivity"
    val BELL_VIBRATION = longArrayOf(0L, 500L, 250L, 500L)

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val bellUri = Uri.parse("android.resource://${context.packageName}/${R.raw.church_bell}")
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PRAYER, context.getString(R.string.channel_prayer), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_prayer_desc)
                setSound(bellUri, audioAttrs)
                enableVibration(true)
                vibrationPattern = BELL_VIBRATION
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_reminders_desc) },
        )
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Plays the church bell sound (`R.raw.church_bell`) and triggers a gentle double bell vibration. */
    fun ringChurchBellAndVibrate(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(BELL_VIBRATION, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(BELL_VIBRATION, -1)
                }
            }
        }
        runCatching {
            val mp = MediaPlayer.create(context.applicationContext, R.raw.church_bell)
            mp?.setOnCompletionListener { player ->
                runCatching { player.release() }
            }
            mp?.start()
        }
    }

    /** Sends an immediate test notification with the church bell sound and vibration. */
    fun sendTestPrayerNotification(context: Context) {
        createChannels(context)
        ringChurchBellAndVibrate(context)
        val app = context.applicationContext as? AgapeApp
        val lang = app?.container?.language() ?: AppLanguage.DUTCH
        val title = if (lang == AppLanguage.DUTCH) "☦ Gebedsherinnering" else "☦ Prayer Reminder"
        val body = if (lang == AppLanguage.DUTCH) {
            "De kerkklok luidt: een stil moment voor uw dagelijks gebed in AGAPE."
        } else {
            "The church bell rings: a quiet moment for your daily prayer in AGAPE."
        }
        post(context, CHANNEL_PRAYER, 7777, title, body, "agape://home")
    }

    fun post(context: Context, channel: String, id: Int, title: String, text: String, deepLink: String) {
        if (!canPost(context)) return
        createChannels(context)
        val bellUri = Uri.parse("android.resource://${context.packageName}/${R.raw.church_bell}")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
        if (channel == CHANNEL_PRAYER) {
            builder.setSound(bellUri)
            builder.setVibrate(BELL_VIBRATION)
            builder.priority = NotificationCompat.PRIORITY_HIGH
        }
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private var scheduledCustomIds: Set<String> = emptySet()

    /** Re-plans every prayer-hour alarm, custom daily reminder, and the inactivity check from current settings. */
    suspend fun reschedule(context: Context) {
        val app = context.applicationContext as AgapeApp
        val settings = app.container.settings.current()
        val alarms = context.getSystemService(AlarmManager::class.java)
        // Cancel everything we may have set before; request codes are derived from the hour id or custom reminder id.
        app.container.content.prayerBookHourIds().forEach { alarms.cancel(alarmIntent(context, it, null)) }
        val customDecoded = settings.customReminders.mapNotNull { CustomReminder.decode(it) }
        (scheduledCustomIds + customDecoded.map { it.id }).forEach { id ->
            alarms.cancel(customAlarmIntent(context, id, null))
        }
        scheduledCustomIds = customDecoded.map { it.id }.toSet()

        if (settings.prayerNotifications) {
            settings.hourReminders.mapNotNull { HourReminder.decode(it) }.forEach { schedule(context, it) }
            customDecoded.forEach { scheduleCustom(context, it) }
        }
        val work = WorkManager.getInstance(context)
        if (settings.inactivityReminders) {
            work.enqueueUniquePeriodicWork(
                INACTIVITY_WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<InactivityWorker>(1, TimeUnit.DAYS).build(),
            )
        } else work.cancelUniqueWork(INACTIVITY_WORK)
    }

    fun schedule(context: Context, reminder: HourReminder) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        var next = LocalDate.now().atTime(reminder.time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = alarmIntent(context, reminder.hourId, reminder)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        else alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 5 * 60_000L, pending)
    }

    fun scheduleCustom(context: Context, reminder: CustomReminder) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        var next = LocalDate.now().atTime(reminder.time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = customAlarmIntent(context, reminder.id, reminder)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        else alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 5 * 60_000L, pending)
    }

    private fun alarmIntent(context: Context, hourId: String, reminder: HourReminder?): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).setAction("agape.PRAYER_HOUR.$hourId")
        reminder?.let { intent.putExtra(EXTRA_REMINDER, it.encode()) }
        return PendingIntent.getBroadcast(context, hourId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun customAlarmIntent(context: Context, id: String, reminder: CustomReminder?): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).setAction("agape.CUSTOM_PRAYER.$id")
        reminder?.let { intent.putExtra(EXTRA_CUSTOM_REMINDER, it.encode()) }
        return PendingIntent.getBroadcast(context, ("custom_$id").hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    const val EXTRA_REMINDER = "reminder"
    const val EXTRA_CUSTOM_REMINDER = "custom_reminder"
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hourReminder = intent.getStringExtra(Notifications.EXTRA_REMINDER)?.let { HourReminder.decode(it) }
        val customReminder = intent.getStringExtra(Notifications.EXTRA_CUSTOM_REMINDER)?.let { CustomReminder.decode(it) }
        if (hourReminder == null && customReminder == null) return

        val pending = goAsync()
        val app = context.applicationContext as AgapeApp
        app.container.appScope.launchSafely {
            try {
                val settings = app.container.settings.current()
                if (!settings.prayerNotifications) return@launchSafely
                val lang = app.container.language()
                val res = app.container.localized(context)

                if (hourReminder != null) {
                    val tradition = settings.tradition
                    val hour = tradition?.let { app.container.content.prayerBook(it).hours.firstOrNull { h -> h.id == hourReminder.hourId } }
                    if (hour != null) {
                        Notifications.ringChurchBellAndVibrate(context)
                        Notifications.post(
                            context, Notifications.CHANNEL_PRAYER, hourReminder.hourId.hashCode(),
                            res.getString(R.string.notif_hour_title, hour.name[lang]),
                            hour.theme[lang].ifBlank { res.getString(R.string.notif_hour_text) },
                            "agape://prayer/${hour.id}",
                        )
                        Notifications.schedule(context, hourReminder)
                    }
                } else if (customReminder != null) {
                    val defaultTitle = if (lang == AppLanguage.DUTCH) "Dagelijks gebed" else "Daily Prayer"
                    val title = customReminder.label.ifBlank { defaultTitle }
                    val body = res.getString(R.string.notif_hour_text)
                    Notifications.ringChurchBellAndVibrate(context)
                    Notifications.post(
                        context, Notifications.CHANNEL_PRAYER, ("custom_${customReminder.id}").hashCode(),
                        title, body, "agape://home",
                    )
                    Notifications.scheduleCustom(context, customReminder)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) return
        val pending = goAsync()
        val app = context.applicationContext as AgapeApp
        app.container.appScope.launchSafely {
            try { Notifications.reschedule(context) } finally { pending.finish() }
        }
    }
}

/** Once a day: if the app has not been opened for a few days, send one quiet invitation. */
class InactivityWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AgapeApp
        val settings = app.container.settings
        if (!settings.settings.first().inactivityReminders) return Result.success()
        val idleDays = (System.currentTimeMillis() - settings.lastOpenedAt()) / TimeUnit.DAYS.toMillis(1)
        if (idleDays >= INACTIVE_AFTER_DAYS) {
            val res = app.container.localized(applicationContext)
            Notifications.post(
                applicationContext, Notifications.CHANNEL_REMINDERS, 4242,
                res.getString(R.string.notif_inactive_title),
                res.getString(R.string.notif_inactive_text),
                "agape://home",
            )
        }
        return Result.success()
    }

    private companion object {
        const val INACTIVE_AFTER_DAYS = 3
    }
}
