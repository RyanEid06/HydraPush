package com.hydrapush.tracker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hydrapush.tracker.data.repository.TrackerRepository
import com.hydrapush.tracker.reminders.ReminderNotificationReceiver
import com.hydrapush.tracker.reminders.ReminderScheduler
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReminderDispatchTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val repository get() = TrackerRepository.getInstance(context)
    private val notificationManager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationId = 1001
    private lateinit var backupFile: File
    private lateinit var backupUri: Uri

    @Before
    fun preserveAppDataAndGrantNotifications() = runBlocking {
        backupFile = File(context.cacheDir, "reminder-dispatch-test-backup.json")
        backupUri = Uri.fromFile(backupFile)
        assertTrue("Could not back up app data", repository.exportBackup(backupUri).isSuccess)
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    @After
    fun restoreAppDataAndRemoveTestNotification() = runBlocking {
        try {
            if (::backupUri.isInitialized) {
                assertTrue("Could not restore app data", repository.importBackup(backupUri).isSuccess)
            }
        } finally {
            notificationManager.cancel(notificationId)
            notificationManager.cancel(ReminderScheduler.ID_PULLUP)
            if (::backupFile.isInitialized) backupFile.delete()
        }
    }

    @Test
    fun scheduledPullUpReminderUsesOwnChannelAndStopsAfterGoal() = runBlocking {
        val today = LocalDate.now().toString()
        repository.days.first().firstOrNull { it.snapshot.day == today }?.pullUpSets?.forEach {
            repository.removePullUpSet(it.id)
        }
        repository.updateSettings { it.copy(pullUpGoalReps = 10, pullUpReminderEnabled = true) }
        val id = ReminderScheduler.ID_PULLUP
        val scheduled = PendingIntent.getBroadcast(
            context, id, Intent(context, ReminderNotificationReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: error("Pull-up reminder must have a scheduled PendingIntent")
        notificationManager.cancel(id)
        scheduled.send()
        val posted = withTimeout(5_000) {
            while (true) {
                notificationManager.activeNotifications.firstOrNull { it.id == id }?.let { return@withTimeout it }
                delay(100)
            }
            error("unreachable")
        }
        assertEquals("Pull-up reminder", posted.notification.extras.getCharSequence("android.title").toString())
        assertEquals(ReminderScheduler.CHANNEL_PULLUPS, posted.notification.channelId)

        notificationManager.cancel(id)
        repository.addPullUpSet(10)
        scheduled.send()
        delay(1_000)
        assertTrue(notificationManager.activeNotifications.none { it.id == id })
    }

    @Test
    fun enabledIncompleteWaterReminderDispatchesThenCompletedAndDisabledAreSuppressed() = runBlocking {
        val today = LocalDate.now().toString()
        repository.days.first().firstOrNull { it.snapshot.day == today }?.waters?.forEach {
            repository.undoWater(it.id)
        }
        repository.updateSettings { it.copy(waterGoalMl = 20_000, waterReminderEnabled = true) }
        assertTrue("Fixture cleanup should leave today's water goal incomplete", repository.shouldShowReminder("water"))

        notificationManager.cancel(notificationId)
        dispatchReminder()
        val posted = withTimeout(5_000) {
            while (true) {
                notificationManager.activeNotifications.firstOrNull { it.id == notificationId }?.let { return@withTimeout it }
                delay(100)
            }
            error("unreachable")
        }
        assertEquals("Water reminder title", "Water reminder test", posted.notification.extras.getCharSequence("android.title").toString())

        notificationManager.cancel(notificationId)
        repeat(4) { repository.addWater(5_000) }
        assertFalse("Completed water goal should suppress reminders", repository.shouldShowReminder("water"))
        dispatchReminder()
        waitForSuppression()

        val lastWater = repository.days.first().first { it.snapshot.day == today }.waters.last()
        repository.undoWater(lastWater.id)
        repository.updateSettings { it.copy(waterReminderEnabled = false) }
        assertFalse("Disabled-case fixture must be incomplete", repository.days.first().first { it.snapshot.day == today }.metrics.waterMet)
        assertFalse("Disabled water reminder should be suppressed", repository.shouldShowReminder("water"))
        dispatchReminder()
        waitForSuppression()
    }

    private fun dispatchReminder() {
        context.sendBroadcast(
            Intent(context, ReminderNotificationReceiver::class.java).apply {
                putExtra("type", "water")
                putExtra("title", "Water reminder test")
                putExtra("message", "Dispatch verification")
                putExtra("id", notificationId)
            }
        )
    }

    private suspend fun waitForSuppression() {
        // Receiver work is asynchronous; allow it to finish before asserting no post occurred.
        delay(1_000)
        assertTrue(
            "Suppressed reminder must not create notification $notificationId",
            notificationManager.activeNotifications.none { it.id == notificationId }
        )
    }
}
