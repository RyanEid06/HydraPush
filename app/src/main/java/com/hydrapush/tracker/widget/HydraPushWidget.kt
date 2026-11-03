package com.hydrapush.tracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hydrapush.tracker.MainActivity
import com.hydrapush.tracker.R
import com.hydrapush.tracker.data.repository.TrackerRepository
import com.hydrapush.tracker.domain.ProgressMath
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HydraPushAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_LOG_WATER_QUICK = "com.hydrapush.tracker.ACTION_LOG_WATER_QUICK"
        const val ACTION_LOG_PUSHUP_QUICK = "com.hydrapush.tracker.ACTION_LOG_PUSHUP_QUICK"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, HydraPushAppWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (allWidgetIds.isNotEmpty()) {
                val intent = Intent(context, HydraPushAppWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val repo = TrackerRepository.getInstance(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repo.reconcileToday()
                val allDays = repo.days.first()
                val todayStr = LocalDate.now().toString()
                val todayView = allDays.find { it.snapshot.day == todayStr }
                val streak = ProgressMath.currentPerfectStreak(allDays.map { it.metrics }, LocalDate.now())

                val waterTotal = todayView?.totalWater ?: 0
                val waterGoal = todayView?.snapshot?.waterGoalMl ?: 2500
                val pushupTotal = todayView?.totalReps ?: 0
                val pushupGoal = todayView?.snapshot?.pushUpGoalReps ?: 100

                for (widgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_hydrapush)

                    // Header click opens app
                    val openIntent = Intent(context, MainActivity::class.java)
                    val openPi = PendingIntent.getActivity(
                        context, 0, openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, openPi)

                    // Streak
                    views.setTextViewText(R.id.widget_streak, "$streak-day streak")

                    // Water text & quick add +250
                    views.setTextViewText(R.id.widget_water_text, "$waterTotal / $waterGoal ml")
                    val waterIntent = Intent(context, HydraPushAppWidgetProvider::class.java).apply {
                        action = ACTION_LOG_WATER_QUICK
                    }
                    val waterPi = PendingIntent.getBroadcast(
                        context, 101, waterIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_water_btn, waterPi)

                    // Push-up text & quick add +10
                    views.setTextViewText(R.id.widget_pushup_text, "$pushupTotal / $pushupGoal reps")
                    val pushIntent = Intent(context, HydraPushAppWidgetProvider::class.java).apply {
                        action = ACTION_LOG_PUSHUP_QUICK
                    }
                    val pushPi = PendingIntent.getBroadcast(
                        context, 102, pushIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_pushup_btn, pushPi)

                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action == ACTION_LOG_WATER_QUICK || action == ACTION_LOG_PUSHUP_QUICK) {
            val pendingResult = goAsync()
            val repo = TrackerRepository.getInstance(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    when (action) {
                        ACTION_LOG_WATER_QUICK -> repo.addWater(250)
                        ACTION_LOG_PUSHUP_QUICK -> repo.addSet(10)
                    }
                    updateAllWidgets(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
