package com.hydrapush.tracker.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hydrapush.tracker.MainActivity
import com.hydrapush.tracker.R
import com.hydrapush.tracker.data.preferences.TrackerSettings
import com.hydrapush.tracker.data.preferences.TrackerSettingsStore
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object ReminderScheduler {
    const val CHANNEL_WATER = "channel_water_reminders"
    const val CHANNEL_PUSHUPS = "channel_pushup_reminders"
    const val CHANNEL_SUPPLEMENTS = "channel_supplement_reminders"

    const val ID_WATER = 1001
    const val ID_PUSHUP = 1002
    const val ID_CREATINE = 1003
    const val ID_PROTEIN = 1004

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_WATER,
                    "Water Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminders to stay hydrated"
                },
                NotificationChannel(
                    CHANNEL_PUSHUPS,
                    "Push-up Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminders to complete your push-up sets"
                },
                NotificationChannel(
                    CHANNEL_SUPPLEMENTS,
                    "Supplement Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminders to take daily creatine and protein shake"
                }
            )
            channels.forEach { nm.createNotificationChannel(it) }
        }
    }

    fun scheduleAll(context: Context, settings: TrackerSettings) {
        createNotificationChannels(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Water reminder
        if (settings.waterReminderEnabled) {
            scheduleDailyRepeating(
                context, alarmManager, ID_WATER,
                hour = 10, minute = 0,
                type = "water", title = "Time to hydrate 💧",
                message = "Drink a glass of water to keep your daily streak alive!"
            )
        } else {
            cancel(context, alarmManager, ID_WATER)
        }

        // Push-up reminder
        if (settings.pushUpReminderEnabled) {
            val hour = settings.pushUpReminderTimeMinutes / 60
            val minute = settings.pushUpReminderTimeMinutes % 60
            scheduleDailyRepeating(
                context, alarmManager, ID_PUSHUP,
                hour = hour, minute = minute,
                type = "pushup", title = "Push-ups time ↗",
                message = "Log a set of push-ups toward your daily goal!"
            )
        } else {
            cancel(context, alarmManager, ID_PUSHUP)
        }

        // Creatine reminder (only active if habit itself is enabled)
        if (settings.creatineEnabled && settings.creatineReminderEnabled) {
            val hour = settings.creatineReminderTimeMinutes / 60
            val minute = settings.creatineReminderTimeMinutes % 60
            scheduleDailyRepeating(
                context, alarmManager, ID_CREATINE,
                hour = hour, minute = minute,
                type = "creatine", title = "Creatine reminder ⚡",
                message = "Take your 5g daily creatine dose."
            )
        } else {
            cancel(context, alarmManager, ID_CREATINE)
        }

        // Protein reminder (only active if habit itself is enabled)
        if (settings.proteinEnabled && settings.proteinReminderEnabled) {
            val hour = settings.proteinReminderTimeMinutes / 60
            val minute = settings.proteinReminderTimeMinutes % 60
            scheduleDailyRepeating(
                context, alarmManager, ID_PROTEIN,
                hour = hour, minute = minute,
                type = "protein", title = "Protein Shake reminder 🥤",
                message = "Time for your daily protein shake serving."
            )
        } else {
            cancel(context, alarmManager, ID_PROTEIN)
        }
    }

    private fun scheduleDailyRepeating(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        hour: Int,
        minute: Int,
        type: String,
        title: String,
        message: String
    ) {
        val intent = Intent(context, ReminderNotificationReceiver::class.java).apply {
            putExtra("type", type)
            putExtra("title", title)
            putExtra("message", message)
            putExtra("id", requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } catch (_: SecurityException) {
            // Safe fallback if exact alarm permission is absent on newer Android
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    private fun cancel(context: Context, alarmManager: AlarmManager, requestCode: Int) {
        val intent = Intent(context, ReminderNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}

class ReminderNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val type = intent.getStringExtra("type") ?: "general"
        val title = intent.getStringExtra("title") ?: "HydraPush Reminder"
        val message = intent.getStringExtra("message") ?: "Time for your daily fitness habits"
        val id = intent.getIntExtra("id", 1000)

        val channelId = when (type) {
            "water" -> ReminderScheduler.CHANNEL_WATER
            "pushup" -> ReminderScheduler.CHANNEL_PUSHUPS
            else -> ReminderScheduler.CHANNEL_SUPPLEMENTS
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_hydrapush)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                val store = TrackerSettingsStore(context.applicationContext)
                val settings = store.settings.first()
                ReminderScheduler.scheduleAll(context.applicationContext, settings)
            }
        }
    }
}
