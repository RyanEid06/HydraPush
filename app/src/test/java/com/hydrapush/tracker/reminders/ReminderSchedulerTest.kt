package com.hydrapush.tracker.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hydrapush.tracker.data.preferences.TrackerSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ReminderSchedulerTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
    private fun waterAlarm() = alarms.single { shadowOf(it.operation).requestCode == ReminderScheduler.ID_WATER }

    @Before fun resetAlarms() {
        ReminderScheduler.scheduleAll(context, TrackerSettings(), forceReschedule = true)
    }

    @Test @Config(sdk = [33])
    fun deniedNotificationPermissionCancelsAllReminderAlarms() {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val settings = TrackerSettings(waterReminderEnabled = true, pushUpReminderEnabled = true,
            creatineReminderEnabled = true, proteinReminderEnabled = true, pullUpReminderEnabled = true)
        ReminderScheduler.scheduleAll(context, settings)
        assertEquals(5, alarms.size)
        shadowOf(application).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        ReminderScheduler.scheduleAll(context, settings)
        assertFalse(ReminderScheduler.notificationsAllowed(context))
        assertTrue(alarms.isEmpty())
    }

    @Test fun pullUpAlarmHasItsOwnScheduleAndCancellation() {
        val settings = TrackerSettings(pushUpReminderEnabled = true, pullUpReminderEnabled = true,
            pushUpReminderTimeMinutes = 480, pullUpReminderTimeMinutes = 615)
        ReminderScheduler.scheduleAll(context, settings, forceReschedule = true)
        val pullUp = alarms.single { shadowOf(it.operation).requestCode == ReminderScheduler.ID_PULLUP }
        val trigger = java.util.Calendar.getInstance().apply { timeInMillis = pullUp.triggerAtTime }
        assertEquals(10, trigger.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(15, trigger.get(java.util.Calendar.MINUTE))
        assertEquals("pullup", shadowOf(pullUp.operation).savedIntent.getStringExtra("type"))

        ReminderScheduler.scheduleAll(context, settings.copy(pullUpReminderEnabled = false))
        assertEquals(ReminderScheduler.ID_PUSHUP, shadowOf(alarms.single().operation).requestCode)
    }

    @Test fun openingAppAndUnrelatedGoalEditsKeepTheWaterTrigger() {
        val settings = TrackerSettings(waterReminderEnabled = true)
        ReminderScheduler.scheduleAll(context, settings)
        val original = waterAlarm().triggerAtTime
        ShadowSystemClock.advanceBy(Duration.ofMinutes(20))
        ReminderScheduler.scheduleAll(context, settings)
        ReminderScheduler.scheduleAll(context, settings.copy(pushUpGoalReps = 200))
        assertEquals(original, waterAlarm().triggerAtTime)
        assertEquals(2 * 60 * 60 * 1000L, waterAlarm().interval)
    }

    @Test fun relevantIntervalChangeReplacesTheSchedule() {
        ReminderScheduler.scheduleAll(context, TrackerSettings(waterReminderEnabled = true))
        val first = waterAlarm().triggerAtTime
        ReminderScheduler.scheduleAll(context, TrackerSettings(waterReminderEnabled = true, waterReminderIntervalHours = 3))
        assertTrue(waterAlarm().triggerAtTime > first)
        assertEquals(3 * 60 * 60 * 1000L, waterAlarm().interval)
    }

    @Test fun disablingOptionalHabitCancelsItsReminderOnly() {
        val settings = TrackerSettings(waterReminderEnabled = true, creatineReminderEnabled = true)
        ReminderScheduler.scheduleAll(context, settings)
        val waterTrigger = waterAlarm().triggerAtTime
        assertTrue(alarms.any { shadowOf(it.operation).requestCode == ReminderScheduler.ID_CREATINE })
        ReminderScheduler.scheduleAll(context, settings.copy(creatineEnabled = false))
        assertFalse(alarms.any { shadowOf(it.operation).requestCode == ReminderScheduler.ID_CREATINE })
        assertEquals(waterTrigger, waterAlarm().triggerAtTime)
    }
}
