package com.hydrapush.tracker.widget

import com.hydrapush.tracker.data.local.DailySnapshotEntity
import com.hydrapush.tracker.data.local.PullUpSetEntity
import com.hydrapush.tracker.data.local.PushUpSetEntity
import com.hydrapush.tracker.data.local.WaterEntryEntity
import com.hydrapush.tracker.data.repository.DayView
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetProgressTest {
    @Test
    fun zeroGoalAndTotalStayAtZero() {
        assertEquals(WidgetProgress(total = 0, goal = 1, percent = 0L), WidgetProgress.from(total = 0, goal = 0))
    }

    @Test
    fun displayedTotalsAndPercentRemainUncappedPastGoal() {
        assertEquals(WidgetProgress(total = 3000, goal = 2500, percent = 120L), WidgetProgress.from(3000, 2500))
    }

    @Test
    fun negativeTotalsDoNotCreateNegativePercentages() {
        assertEquals(WidgetProgress(total = -5, goal = 100, percent = 0L), WidgetProgress.from(-5, 100))
    }

    @Test
    fun widgetStateUsesTodaysLoggedAmountsAndGoals() {
        val today = LocalDate.of(2026, 11, 4)
        val day = DayView(
            snapshot = DailySnapshotEntity(
                day = today.toString(),
                waterGoalMl = 2500,
                pushUpGoalReps = 100,
                pullUpGoalReps = 12,
                creatineRequired = true,
                proteinRequired = true
            ),
            waters = listOf(WaterEntryEntity(day = today.toString(), amountMl = 500)),
            sets = listOf(PushUpSetEntity(day = today.toString(), reps = 20)),
            pullUpSets = listOf(PullUpSetEntity(day = today.toString(), reps = 3))
        )

        val state = WidgetState.from(listOf(day), today)

        assertEquals(WidgetProgress(500, 2500, 20), state.water)
        assertEquals(WidgetProgress(20, 100, 20), state.pushUps)
        assertEquals(WidgetProgress(3, 12, 25), state.pullUps)
    }
}
