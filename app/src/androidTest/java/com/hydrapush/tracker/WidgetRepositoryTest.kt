package com.hydrapush.tracker

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.hydrapush.tracker.data.repository.TrackerRepository
import com.hydrapush.tracker.widget.HydraPushAppWidgetProvider
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class WidgetRepositoryTest {
    @Test fun fourActionsAndRapidTapsPersistOnceAndBackupRestores() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = TrackerRepository.getInstance(context)
        repo.reconcileToday()
        val backup = File(context.cacheDir, "widget-regression-backup.json")
        val uri = Uri.fromFile(backup)
        repo.exportBackup(uri).getOrThrow()
        val date = LocalDate.now().toString()
        suspend fun day() = withTimeout(30_000) { repo.days.first { days -> days.any { it.snapshot.day == date } }.first { it.snapshot.day == date } }
        fun send(action: String) = context.sendBroadcast(Intent(context, HydraPushAppWidgetProvider::class.java).setAction(action))
        try {
            repo.updateSettings { it.copy(creatineEnabled = true, proteinEnabled = true) }
            val before = day()
            repeat(5) { send(HydraPushAppWidgetProvider.ACTION_LOG_WATER_QUICK) }
            withTimeout(30_000) { repo.days.first { days -> days.first { it.snapshot.day == date }.totalWater == before.totalWater + 1250 } }
            assertEquals(before.waters.size + 5, day().waters.size)
            send(HydraPushAppWidgetProvider.ACTION_LOG_PUSHUP_QUICK)
            withTimeout(30_000) { repo.days.first { days -> days.first { it.snapshot.day == date }.totalReps == before.totalReps + 10 } }
            val set = day().sets.first { it.id !in before.sets.map { old -> old.id } }
            assertEquals(10, set.reps)
            send(HydraPushAppWidgetProvider.ACTION_TOGGLE_CREATINE)
            withTimeout(30_000) { repo.days.first { days -> days.first { it.snapshot.day == date }.snapshot.creatineDone != before.snapshot.creatineDone } }
            send(HydraPushAppWidgetProvider.ACTION_TOGGLE_PROTEIN)
            withTimeout(30_000) { repo.days.first { days -> days.first { it.snapshot.day == date }.snapshot.proteinDone != before.snapshot.proteinDone } }
            send(HydraPushAppWidgetProvider.ACTION_TOGGLE_CREATINE)
            send(HydraPushAppWidgetProvider.ACTION_TOGGLE_PROTEIN)
            withTimeout(30_000) { repo.days.first { days -> days.first { it.snapshot.day == date }.snapshot.let { it.creatineDone == before.snapshot.creatineDone && it.proteinDone == before.snapshot.proteinDone } } }
            repo.removeSet(set.id)
            assertEquals(before.totalReps, day().totalReps)
            val addedWaters = day().waters.filter { it.id !in before.waters.map { old -> old.id } }
            addedWaters.forEach { repo.undoWater(it.id) }
            assertEquals(before.totalWater, day().totalWater)
        } finally {
            repo.importBackup(uri).getOrThrow()
            backup.delete()
        }
    }
}
