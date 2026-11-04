package com.hydrapush.tracker.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hydrapush.tracker.data.local.TrackerDao
import com.hydrapush.tracker.data.local.TrackerDatabase
import com.hydrapush.tracker.data.preferences.TrackerSettings
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class TrackerRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: TrackerRepository
    private lateinit var dao: TrackerDao
    private lateinit var database: TrackerDatabase

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("hydrapush.db")
        repository = TrackerRepository::class.java.getDeclaredConstructor(Context::class.java).apply {
            isAccessible = true
        }.newInstance(context)
        val daoField = TrackerRepository::class.java.getDeclaredField("dao").apply { isAccessible = true }
        dao = daoField.get(repository) as TrackerDao
        val dbField = TrackerRepository::class.java.getDeclaredField("database").apply { isAccessible = true }
        database = dbField.get(repository) as TrackerDatabase
        repository.settingsStore.save(TrackerSettings())
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase("hydrapush.db")
    }

    @Test
    fun unrelatedSettingsDoNotAddRequirementRevision() = runBlocking {
        repository.reconcileToday()
        val before = dao.getAllRevisions().size

        repository.updateSettings { it.copy(waterReminderEnabled = !it.waterReminderEnabled) }

        assertEquals(before, dao.getAllRevisions().size)
    }

    @Test
    fun requirementSettingsUpdateTodayAndRecordsOneRevision() = runBlocking {
        repository.reconcileToday()
        val original = dao.getSnapshot(LocalDate.now().toString())!!
        val before = dao.getAllRevisions().size

        repository.updateSettings { it.copy(waterGoalMl = it.waterGoalMl + 100) }

        assertEquals(original.waterGoalMl + 100, dao.getSnapshot(original.day)!!.waterGoalMl)
        assertEquals(before + 1, dao.getAllRevisions().size)
    }

    @Test
    fun reconcileAlignsImportedTodayRequirementsButPreservesCompletion() = runBlocking {
        repository.reconcileToday()
        val day = LocalDate.now().toString()
        val original = dao.getSnapshot(day)!!
        dao.upsertSnapshot(
            original.copy(
                waterGoalMl = original.waterGoalMl + 500,
                creatineRequired = false,
                creatineDone = true
            )
        )

        repository.reconcileToday()

        val reconciled = dao.getSnapshot(day)!!
        assertEquals(original.waterGoalMl, reconciled.waterGoalMl)
        assertTrue(reconciled.creatineRequired)
        assertTrue(reconciled.creatineDone)
    }

    @Test
    fun concurrentSupplementTogglesDoNotLoseAnUpdate() = runBlocking {
        repository.reconcileToday()

        val first = async { repository.toggleSupplement(creatine = true) }
        val second = async { repository.toggleSupplement(creatine = true) }
        first.await()
        second.await()

        assertEquals(false, dao.getSnapshot(LocalDate.now().toString())!!.creatineDone)
    }

    @Test
    fun completedHabitReminderIsSuppressedAndDisabledReminderIsSuppressed() = runBlocking {
        repository.reconcileToday()
        repository.settingsStore.save(TrackerSettings(waterReminderEnabled = true, waterGoalMl = 250))
        repository.addWater(250)

        assertTrue(!repository.shouldShowReminder("water"))
        repository.settingsStore.save(TrackerSettings(waterReminderEnabled = false))
        assertTrue(!repository.shouldShowReminder("water"))
    }
}
