package com.hydrapush.tracker.widget

import android.app.PendingIntent
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.graphics.Color
import android.widget.RemoteViews
import com.hydrapush.tracker.MainActivity
import com.hydrapush.tracker.R
import com.hydrapush.tracker.data.preferences.DEFAULT_PULL_UP_GOAL
import com.hydrapush.tracker.data.repository.DayView
import com.hydrapush.tracker.data.repository.TrackerRepository
import com.hydrapush.tracker.domain.ProgressMath
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WidgetProgress(val total: Int, val goal: Int, val percent: Long) {
    companion object {
        fun from(total: Int, goal: Int): WidgetProgress {
            val safeGoal = goal.coerceAtLeast(1)
            return WidgetProgress(total, safeGoal, (total.coerceAtLeast(0).toLong() * 100) / safeGoal)
        }
    }
}

data class WidgetState(
    val water: WidgetProgress,
    val pushUps: WidgetProgress,
    val pullUps: WidgetProgress,
    val streak: Int
) {
    companion object {
        fun from(days: List<DayView>, today: LocalDate): WidgetState {
            val todayView = days.firstOrNull { it.snapshot.day == today.toString() }
            val snapshot = todayView?.snapshot
            val streak = ProgressMath.currentActiveStreak(days.map { it.metrics }, today)
            return WidgetState(
                water = WidgetProgress.from(todayView?.totalWater ?: 0, snapshot?.waterGoalMl ?: 2500),
                pushUps = WidgetProgress.from(todayView?.totalReps ?: 0, snapshot?.pushUpGoalReps ?: 100),
                pullUps = WidgetProgress.from(todayView?.totalPullUps ?: 0, snapshot?.pullUpGoalReps ?: DEFAULT_PULL_UP_GOAL),
                streak = streak
            )
        }
    }
}

class HydraPushAppWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_REFRESH_MIDNIGHT = "com.hydrapush.tracker.ACTION_REFRESH_WIDGET_MIDNIGHT"
        private const val TAG = "HydraPushWidget"
        private val operations = Mutex()

        private fun midnightPendingIntent(context: Context) = PendingIntent.getBroadcast(
            context,
            15,
            Intent(context, HydraPushAppWidgetProvider::class.java).setAction(ACTION_REFRESH_MIDNIGHT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun scheduleMidnightRefresh(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnight, midnightPendingIntent(context))
        }

        private fun cancelMidnightRefresh(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.cancel(midnightPendingIntent(context))
        }

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HydraPushAppWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(Intent(context, HydraPushAppWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                })
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                operations.withLock {
                    val repo = TrackerRepository.getInstance(context)
                    repo.reconcileToday()
                    render(context, appWidgetManager, appWidgetIds, WidgetState.from(repo.days.first(), LocalDate.now()))
                    scheduleMidnightRefresh(context)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Unable to refresh widget", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        scheduleMidnightRefresh(context)
    }

    override fun onDisabled(context: Context) {
        cancelMidnightRefresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action == ACTION_REFRESH_MIDNIGHT || action in setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED)) {
            scheduleMidnightRefresh(context)
            updateAllWidgets(context)
            return
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetIds: IntArray, state: WidgetState) {
        for (widgetId in widgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_hydrapush)
            views.setTextViewText(R.id.widget_wordmark, SpannableString("HydraPush").apply {
                setSpan(ForegroundColorSpan(Color.rgb(0, 166, 158)), 5, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            })
            views.setTextViewText(R.id.widget_streak, "${state.streak} 🔥")
            views.setContentDescription(R.id.widget_streak, "${state.streak} day streak")
            views.setTextViewText(R.id.widget_water_total, "${state.water.total} / ${state.water.goal} ml")
            views.setProgressBar(R.id.widget_water_progress, 100, state.water.percent.coerceIn(0, 100).toInt(), false)
            views.setTextViewText(R.id.widget_water_percent, "${state.water.percent}%")
            views.setTextViewText(R.id.widget_pushup_total, "${state.pushUps.total} / ${state.pushUps.goal} reps")
            views.setProgressBar(R.id.widget_pushup_progress, 100, state.pushUps.percent.coerceIn(0, 100).toInt(), false)
            views.setTextViewText(R.id.widget_pushup_percent, "${state.pushUps.percent}%")
            views.setTextViewText(R.id.widget_pullup_total, "${state.pullUps.total} / ${state.pullUps.goal} reps")
            views.setProgressBar(R.id.widget_pullup_progress, 100, state.pullUps.percent.coerceIn(0, 100).toInt(), false)
            views.setTextViewText(R.id.widget_pullup_percent, "${state.pullUps.percent}%")

            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent(context, 10))
            views.setContentDescription(R.id.widget_header, "Open HydraPush")
            views.setContentDescription(R.id.widget_water_card, "Water progress: ${state.water.total} of ${state.water.goal} milliliters, ${state.water.percent} percent. Tap to open HydraPush.")
            views.setContentDescription(R.id.widget_pushup_card, "Push-ups progress: ${state.pushUps.total} of ${state.pushUps.goal} reps, ${state.pushUps.percent} percent. Tap to open HydraPush.")
            views.setContentDescription(R.id.widget_pullup_card, "Pull-ups progress: ${state.pullUps.total} of ${state.pullUps.goal} reps, ${state.pullUps.percent} percent. Tap to open HydraPush.")
            views.setOnClickPendingIntent(R.id.widget_header, pendingIntent(context, 10))
            views.setOnClickPendingIntent(R.id.widget_water_card, pendingIntent(context, 10))
            views.setOnClickPendingIntent(R.id.widget_pushup_card, pendingIntent(context, 10))
            views.setOnClickPendingIntent(R.id.widget_pullup_card, pendingIntent(context, 10))
            manager.updateAppWidget(widgetId, views)
        }
    }

    private fun pendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }
}
