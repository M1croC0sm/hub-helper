package app.hubhelper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

data class ReminderPreference(
    val enabled: Boolean = false,
    val dayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
    val time: LocalTime = LocalTime.of(14, 27),
)

class ReminderPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("weekly_reminder", Context.MODE_PRIVATE)

    fun load(shiftPreset: String? = null): ReminderPreference {
        val shiftDefault = if (shiftPreset == "SECOND") LocalTime.of(12, 37) else LocalTime.of(14, 27)
        val dayDefault = if (shiftPreset == "SECOND") DayOfWeek.THURSDAY else DayOfWeek.SUNDAY
        return ReminderPreference(
        enabled = preferences.getBoolean("enabled", false),
        dayOfWeek = runCatching { DayOfWeek.valueOf(preferences.getString("day", null) ?: dayDefault.name) }.getOrDefault(dayDefault),
        time = runCatching { LocalTime.parse(preferences.getString("time", null) ?: shiftDefault.toString()) }.getOrDefault(shiftDefault),
    )
    }

    fun save(value: ReminderPreference) {
        preferences.edit()
            .putBoolean("enabled", value.enabled)
            .putString("day", value.dayOfWeek.name)
            .putString("time", value.time.toString())
            .apply()
    }
}

object WeeklyReminderScheduler {
    private const val WORK_NAME = "weekly-check-in"

    fun apply(context: Context, preference: ReminderPreference) {
        val manager = WorkManager.getInstance(context)
        if (!preference.enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = ZonedDateTime.now()
        var next = now.with(preference.dayOfWeek).with(preference.time).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        createNotificationChannel(context)
        val request = OneTimeWorkRequestBuilder<WeeklyCheckInWorker>()
            .setInitialDelay(Duration.between(now, next))
            .build()
        manager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun notifyNow(context: Context) {
        createNotificationChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            1001,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Hubb Helper")
                .setContentText("Anything from this workweek to log?")
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun createNotificationChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Weekly check-in", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Optional reminder to review the workweek"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        )
    }

    const val CHANNEL_ID = "weekly-check-in"
}

class WeeklyCheckInWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        WeeklyReminderScheduler.notifyNow(applicationContext)
        WeeklyReminderScheduler.apply(applicationContext, ReminderPreferences(applicationContext).load())
        return Result.success()
    }

    private companion object { const val CHANNEL_ID = WeeklyReminderScheduler.CHANNEL_ID }
}
