package com.hydrapush.tracker

import com.hydrapush.tracker.data.local.DailySnapshotEntity
import com.hydrapush.tracker.data.local.PullUpSetEntity
import com.hydrapush.tracker.data.local.WaterEntryEntity
import com.hydrapush.tracker.data.repository.DayView
import com.hydrapush.tracker.widget.WidgetState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStreakTest {
    @Test
    fun onlyCompletedGoalsExtendTheWidgetStreakIncludingPullUps() {
        val today = LocalDate.of(2026, 11, 8)
        val yesterday = today.minusDays(1).toString()
        val past = DayView(DailySnapshotEntity(yesterday, 2500, 100, true, true),
            listOf(WaterEntryEntity(day = yesterday, amountMl = 2500)), emptyList())
        val current = DayView(DailySnapshotEntity(today.toString(), 2500, 100, true, true, pullUpGoalReps = 10),
            listOf(WaterEntryEntity(day = today.toString(), amountMl = 150)), emptyList())
        assertEquals(1, WidgetState.from(listOf(past, current), today).streak)
        val goalHit = current.copy(pullUpSets = listOf(PullUpSetEntity(day = today.toString(), reps = 10)))
        assertEquals(2, WidgetState.from(listOf(past, goalHit), today).streak)
    }
}
