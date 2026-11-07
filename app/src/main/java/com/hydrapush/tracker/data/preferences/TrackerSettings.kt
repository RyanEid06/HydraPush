package com.hydrapush.tracker.data.preferences

import com.hydrapush.tracker.domain.ValidationLimits

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.trackerDataStore by preferencesDataStore(name = "hydrapush_settings")

const val DEFAULT_PULL_UP_GOAL = 10
const val DEFAULT_PULL_UP_REMINDER_TIME = 18 * 60

data class TrackerSettings(
    val waterGoalMl: Int = 2500,
    val pushUpGoalReps: Int = 100,
    val creatineEnabled: Boolean = true,
    val proteinEnabled: Boolean = true,
    val themeMode: String = "LIGHT", // V1 is intentionally light-only; retained for backup compatibility
    val waterReminderEnabled: Boolean = false,
    val waterReminderIntervalHours: Int = 2,
    val pushUpReminderEnabled: Boolean = false,
    val pushUpReminderTimeMinutes: Int = 18 * 60, // 6:00 PM
    val creatineReminderEnabled: Boolean = false,
    val creatineReminderTimeMinutes: Int = 9 * 60, // 9:00 AM
    val proteinReminderEnabled: Boolean = false,
    val proteinReminderTimeMinutes: Int = 19 * 60, // 7:00 PM
    val pullUpGoalReps: Int = DEFAULT_PULL_UP_GOAL,
    val pullUpReminderEnabled: Boolean = false,
    val pullUpReminderTimeMinutes: Int = DEFAULT_PULL_UP_REMINDER_TIME
)

class TrackerSettingsStore(private val context: Context) {
    private object Keys {
        val WATER_GOAL = intPreferencesKey("water_goal_ml")
        val PUSH_GOAL = intPreferencesKey("push_goal_reps")
        val PULL_GOAL = intPreferencesKey("pull_goal_reps")
        val CREATINE = booleanPreferencesKey("creatine_enabled")
        val PROTEIN = booleanPreferencesKey("protein_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val WATER_REMINDER = booleanPreferencesKey("water_reminder_enabled")
        val WATER_INTERVAL = intPreferencesKey("water_reminder_interval")
        val PUSHUP_REMINDER = booleanPreferencesKey("pushup_reminder_enabled")
        val PUSHUP_TIME = intPreferencesKey("pushup_reminder_time")
        val PULLUP_REMINDER = booleanPreferencesKey("pullup_reminder_enabled")
        val PULLUP_TIME = intPreferencesKey("pullup_reminder_time")
        val CREATINE_REMINDER = booleanPreferencesKey("creatine_reminder_enabled")
        val CREATINE_TIME = intPreferencesKey("creatine_reminder_time")
        val PROTEIN_REMINDER = booleanPreferencesKey("protein_reminder_enabled")
        val PROTEIN_TIME = intPreferencesKey("protein_reminder_time")
    }

    val settings: Flow<TrackerSettings> = context.trackerDataStore.data.map { p ->
        TrackerSettings(
            waterGoalMl = p[Keys.WATER_GOAL] ?: 2500,
            pushUpGoalReps = p[Keys.PUSH_GOAL] ?: 100,
            pullUpGoalReps = p[Keys.PULL_GOAL] ?: DEFAULT_PULL_UP_GOAL,
            creatineEnabled = p[Keys.CREATINE] ?: true,
            proteinEnabled = p[Keys.PROTEIN] ?: true,
            themeMode = "LIGHT",
            waterReminderEnabled = p[Keys.WATER_REMINDER] ?: false,
            waterReminderIntervalHours = p[Keys.WATER_INTERVAL] ?: 2,
            pushUpReminderEnabled = p[Keys.PUSHUP_REMINDER] ?: false,
            pushUpReminderTimeMinutes = p[Keys.PUSHUP_TIME] ?: (18 * 60),
            pullUpReminderEnabled = p[Keys.PULLUP_REMINDER] ?: false,
            pullUpReminderTimeMinutes = p[Keys.PULLUP_TIME] ?: DEFAULT_PULL_UP_REMINDER_TIME,
            creatineReminderEnabled = (p[Keys.CREATINE_REMINDER] ?: false) && (p[Keys.CREATINE] ?: true),
            creatineReminderTimeMinutes = p[Keys.CREATINE_TIME] ?: (9 * 60),
            proteinReminderEnabled = (p[Keys.PROTEIN_REMINDER] ?: false) && (p[Keys.PROTEIN] ?: true),
            proteinReminderTimeMinutes = p[Keys.PROTEIN_TIME] ?: (19 * 60)
        )
    }

    suspend fun save(next: TrackerSettings) {
        require(
            next.waterGoalMl in ValidationLimits.WATER_GOAL &&
                next.pushUpGoalReps in ValidationLimits.PUSH_UP_GOAL &&
                next.pullUpGoalReps in ValidationLimits.PULL_UP_GOAL
        ) {
            "Goals out of valid range"
        }
        require(next.pullUpReminderTimeMinutes in 0..1439) { "Pull-up reminder time out of range" }
        context.trackerDataStore.edit { p ->
            p[Keys.WATER_GOAL] = next.waterGoalMl
            p[Keys.PUSH_GOAL] = next.pushUpGoalReps
            p[Keys.PULL_GOAL] = next.pullUpGoalReps
            p[Keys.CREATINE] = next.creatineEnabled
            p[Keys.PROTEIN] = next.proteinEnabled
            p[Keys.THEME_MODE] = "LIGHT"
            p[Keys.WATER_REMINDER] = next.waterReminderEnabled
            p[Keys.WATER_INTERVAL] = next.waterReminderIntervalHours
            p[Keys.PUSHUP_REMINDER] = next.pushUpReminderEnabled
            p[Keys.PUSHUP_TIME] = next.pushUpReminderTimeMinutes
            p[Keys.PULLUP_REMINDER] = next.pullUpReminderEnabled
            p[Keys.PULLUP_TIME] = next.pullUpReminderTimeMinutes
            p[Keys.CREATINE_REMINDER] = next.creatineReminderEnabled && next.creatineEnabled
            p[Keys.CREATINE_TIME] = next.creatineReminderTimeMinutes
            p[Keys.PROTEIN_REMINDER] = next.proteinReminderEnabled && next.proteinEnabled
            p[Keys.PROTEIN_TIME] = next.proteinReminderTimeMinutes
        }
    }
}
