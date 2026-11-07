package com.hydrapush.tracker.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hydrapush.tracker.MainActivity
import com.hydrapush.tracker.R
import com.hydrapush.tracker.data.preferences.TrackerSettings
import com.hydrapush.tracker.data.preferences.TrackerSettingsStore
import com.hydrapush.tracker.data.repository.TrackerRepository
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object ReminderScheduler {
    const val CHANNEL_WATER = "channel_water_reminders"
    const val CHANNEL_PUSHUPS = "channel_pushup_reminders"
    const val CHANNEL_PULLUPS = "channel_pullup_reminders"
    const val CHANNEL_SUPPLEMENTS = "channel_supplement_reminders"

    const val ID_WATER = 1001
    const val ID_PUSHUP = 1002
    const val ID_CREATINE = 1003
    const val ID_PROTEIN = 1004
    const val ID_PULLUP = 1005

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
                    CHANNEL_PULLUPS,
                    "Pull-up Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminders to complete your pull-up sets"
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

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun scheduleAll(context: Context, settings: TrackerSettings, forceReschedule: Boolean = false) {
        createNotificationChannels(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Never keep wake-up alarms active when Android cannot show their notifications.
        if (!notificationsAllowed(context)) {
            listOf(ID_WATER, ID_PUSHUP, ID_PULLUP, ID_CREATINE, ID_PROTEIN).forEach {
                cancel(context, alarmManager, it)
            }
            return
        }

        // Water reminder with configured interval (e.g. every N hours)
        if (settings.waterReminderEnabled) {
            val intervalHours = settings.waterReminderIntervalHours.coerceIn(1, 24)
            val intervalMillis = intervalHours * 60 * 60 * 1000L
            scheduleIntervalRepeating(
                context, alarmManager, ID_WATER,
                intervalMillis = intervalMillis,
                type = "water", title = "Time to hydrate",
                message = "Drink a glass of water to keep your daily streak alive!",
                signature = "water:$intervalHours",
                forceReschedule = forceReschedule
            )
        } else {
            cancel(context, alarmManager, ID_WATER)
        }

        // Push-up reminder
        if (settings.pushUpReminderEnabled) {
            val hour = (settings.pushUpReminderTimeMinutes / 60).coerceIn(0, 23)
            val minute = (settings.pushUpReminderTimeMinutes % 60).coerceIn(0, 59)
            scheduleDailyRepeating(
                context, alarmManager, ID_PUSHUP,
                hour = hour, minute = minute,
                type = "pushup", title = "Push-ups time",
                message = "Log a set of push-ups toward your daily goal!",
                signature = "pushup:$hour:$minute:${java.util.TimeZone.getDefault().id}",
                forceReschedule = forceReschedule
            )
        } else {
            cancel(context, alarmManager, ID_PUSHUP)
        }

        if (settings.pullUpReminderEnabled) {
            val hour = settings.pullUpReminderTimeMinutes / 60
            val minute = settings.pullUpReminderTimeMinutes % 60
            scheduleDailyRepeating(
                context, alarmManager, ID_PULLUP,
                hour = hour, minute = minute,
                type = "pullup", title = "Pull-up reminder",
                message = "Log a set of pull-ups toward your daily goal!",
                signature = "pullup:$hour:$minute:${java.util.TimeZone.getDefault().id}",
                forceReschedule = forceReschedule
            )
        } else {
            cancel(context, alarmManager, ID_PULLUP)
        }

        // Creatine reminder (only active if habit itself is enabled)
        if (settings.creatineEnabled && settings.creatineReminderEnabled) {
            val hour = (settings.creatineReminderTimeMinutes / 60).coerceIn(0, 23)
            val minute = (settings.creatineReminderTimeMinutes % 60).coerceIn(0, 59)
            scheduleDailyRepeating(
                context, alarmManager, ID_CREATINE,
                hour = hour, minute = minute,
                type = "creatine", title = "Creatine reminder",
                message = "Take your 5g daily creatine dose.",
                signature = "creatine:$hour:$minute:${java.util.TimeZone.getDefault().id}",
                forceReschedule = forceReschedule
            )
        } else {
            cancel(context, alarmManager, ID_CREATINE)
        }

        // Protein reminder (only active if habit itself is enabled)
        if (settings.proteinEnabled && settings.proteinReminderEnabled) {
            val hour = (settings.proteinReminderTimeMinutes / 60).coerceIn(0, 23)
            val minute = (settings.proteinReminderTimeMinutes % 60).coerceIn(0, 59)
            scheduleDailyRepeating(
                context, alarmManager, ID_PROTEIN,
                hour = hour, minute = minute,
                type = "protein", title = "Protein Shake reminder",
                message = "Time for your daily protein shake serving.",
                signature = "protein:$hour:$minute:${java.util.TimeZone.getDefault().id}",
                forceReschedule = forceReschedule
            )
        } else {
            cancel(context, alarmManager, ID_PROTEIN)
        }
    }

    private fun scheduleIntervalRepeating(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        intervalMillis: Long,
        type: String,
        title: String,
        message: String,
        signature: String,
        forceReschedule: Boolean
    ) {
        if (!shouldReschedule(context, requestCode, signature, forceReschedule)) return

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

        val triggerAtMillis = System.currentTimeMillis() + intervalMillis
        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                intervalMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
        persistSignature(context, requestCode, signature)
    }

    private fun scheduleDailyRepeating(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        hour: Int,
        minute: Int,
        type: String,
        title: String,
        message: String,
        signature: String,
        forceReschedule: Boolean
    ) {
        if (!shouldReschedule(context, requestCode, signature, forceReschedule)) return

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
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
        persistSignature(context, requestCode, signature)
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
        context.getSharedPreferences("reminder_schedule_state", Context.MODE_PRIVATE)
            .edit().remove(requestCode.toString()).apply()
    }

    private fun shouldReschedule(
        context: Context,
        requestCode: Int,
        signature: String,
        forceReschedule: Boolean
    ): Boolean {
        val preferences = context.getSharedPreferences("reminder_schedule_state", Context.MODE_PRIVATE)
        val existing = PendingIntent.getBroadcast(
            context,
            requestCode,
            pendingIntentIntent(requestCode, context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        val key = requestCode.toString()
        if (!forceReschedule && existing != null && preferences.getString(key, null) == signature) {
            return false
        }
        return true
    }

    private fun persistSignature(context: Context, requestCode: Int, signature: String) {
        context.getSharedPreferences("reminder_schedule_state", Context.MODE_PRIVATE)
            .edit().putString(requestCode.toString(), signature).apply()
    }

    private fun pendingIntentIntent(requestCode: Int, context: Context): Intent =
        Intent(context, ReminderNotificationReceiver::class.java).apply {
            putExtra("id", requestCode)
        }
}

class ReminderNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val type = intent.getStringExtra("type") ?: "general"
        val title = intent.getStringExtra("title") ?: "HydraPush Reminder"
        val message = intent.getStringExtra("message") ?: "Time for your daily fitness habits"
        val id = intent.getIntExtra("id", 1000)
        if (type !in setOf("water", "pushup", "pullup", "creatine", "protein")) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!TrackerRepository.getInstance(context).shouldShowReminder(type)) return@launch
                val channelId = when (type) {
                    "water" -> ReminderScheduler.CHANNEL_WATER
                    "pushup" -> ReminderScheduler.CHANNEL_PUSHUPS
                    "pullup" -> ReminderScheduler.CHANNEL_PULLUPS
                    else -> ReminderScheduler.CHANNEL_SUPPLEMENTS
                }
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val contentIntent = PendingIntent.getActivity(
                    context, 0, openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_notification_hydra)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(id, notification)
            } catch (error: Exception) {
                Log.w("HydraPushReminder", "Unable to process reminder", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val store = TrackerSettingsStore(context.applicationContext)
                    val settings = store.settings.first()
                    ReminderScheduler.scheduleAll(context.applicationContext, settings, forceReschedule = true)
                } catch (error: Exception) {
                    Log.w("HydraPushReminder", "Unable to restore reminder schedule", error)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
