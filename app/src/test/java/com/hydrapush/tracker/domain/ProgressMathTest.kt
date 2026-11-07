package com.hydrapush.tracker.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ProgressMathTest {
    private val d = LocalDate.of(2026, 11, 3)

    private fun day(
        date: LocalDate,
        water: Int,
        reps: Int,
        creatine: Boolean = false,
        protein: Boolean = false,
        cr: Boolean = true,
        pr: Boolean = true
    ) = DailyMetrics(date, water, reps, 2500, 100, cr, pr, creatine, protein)

    @Test
    fun optionalSupplementsDoNotBlockPerfectDay() {
        assertTrue(day(d, 2500, 100, cr = false, pr = false).perfect)
        assertFalse(day(d, 2500, 100, cr = true, pr = false).perfect)
    }

    @Test
    fun extraVolumeDoesNotPushProgressAboveOne() {
        assertEquals(1f, day(d, 5000, 500, cr = false, pr = false).progress, 0.0001f)
    }

    @Test
    fun currentStreakRetainsYesterdayWhileTodayIncomplete() {
        val days = listOf(
            day(d.minusDays(1), 2500, 0),
            day(d, 150, 0, creatine = true, protein = true, cr = false, pr = false)
        )
        assertEquals(1, ProgressMath.currentActiveStreak(days, d))
        assertEquals(0, ProgressMath.currentActiveStreak(days.takeLast(1), d))
    }

    @Test
    fun pastGapBreaksStreak() {
        val days = listOf(
            day(d.minusDays(3), 2500, 100, true, true),
            day(d.minusDays(1), 2500, 100, true, true),
            day(d, 2500, 100, true, true)
        )
        assertEquals(2, ProgressMath.currentActiveStreak(days, d))
        assertEquals(2, ProgressMath.longestActiveStreak(days))
    }

    @Test
    fun evaluateAchievementsUnlocksAppropriately() {
        val days = listOf(
            day(d.minusDays(2), 2500, 100, true, true),
            day(d.minusDays(1), 3200, 120, true, true),
            day(d, 2500, 100, true, true)
        )
        val achievements = ProgressMath.evaluateAchievements(days)
        val firstPerfect = achievements.find { it.id == "first_perfect" }
        val streak3 = achievements.find { it.id == "streak_3" }
        val water2l = achievements.find { it.id == "water_2l" }
        val water3l = achievements.find { it.id == "water_3l" }
        val pushup100 = achievements.find { it.id == "pushup_100" }

        assertTrue(firstPerfect?.isUnlocked == true)
        assertTrue(streak3?.isUnlocked == true)
        assertTrue(water2l?.isUnlocked == true)
        assertTrue(water3l?.isUnlocked == true)
        assertTrue(pushup100?.isUnlocked == true)
    }

    @Test
    fun streakBadgeCountsDifferentCompletedGoalsOnConsecutiveDays() {
        val days = listOf(
            day(d.minusDays(2), 2500, 0),
            day(d.minusDays(1), 0, 100),
            day(d, 0, 0, creatine = true)
        )
        val achievements = ProgressMath.evaluateAchievements(days)
        assertTrue(achievements.single { it.id == "streak_3" }.isUnlocked)
        assertFalse(achievements.single { it.id == "first_perfect" }.isUnlocked)
    }

    @Test
    fun onlyActiveGoalsCanEarnAStreakDay() {
        val empty = day(d, 0, 0).copy(pullUpGoalReps = 10)
        val completed = listOf(
            empty.copy(waterMl = 2500),
            empty.copy(pushUpReps = 100),
            empty.copy(pullUpReps = 10),
            empty.copy(creatineDone = true),
            empty.copy(proteinDone = true)
        )
        completed.forEach { assertEquals(1, ProgressMath.currentActiveStreak(listOf(it), d)) }
        assertEquals(0, ProgressMath.currentActiveStreak(listOf(empty.copy(pullUpReps = 9)), d))
    }

    @Test
    fun pullUpsAffectOnlyDaysWithAHistoricalPullUpGoal() {
        val legacy = day(d.minusDays(1), 2500, 100, cr = false, pr = false)
        val new = legacy.copy(day = d, pullUpGoalReps = 10, pullUpReps = 5, pullUpsRequired = true)
        assertTrue(legacy.perfect)
        assertFalse(new.perfect)
        assertEquals(2.5f / 3, new.progress, 0.0001f)
        assertTrue(new.copy(pullUpReps = 10).perfect)
        val settled = d.plusDays(1)
        assertEquals(50, ProgressMath.completionPercent(listOf(legacy, new), settled))
        assertEquals(0, ProgressMath.completionPercent(listOf(legacy, new), settled, "Pull-ups"))
    }

    @Test
    fun habitStreakCountsConsecutiveDays() {
        val days = listOf(
            day(d.minusDays(2), 2500, 50),
            day(d.minusDays(1), 3000, 50),
            day(d, 2600, 50)
        )
        assertEquals(3, ProgressMath.currentHabitStreak(days, d, "Water"))
        assertEquals(0, ProgressMath.currentHabitStreak(days, d, "Push-ups"))
    }

    @Test
    fun eligibleHistoryExcludesFutureAndUnfinishedTodayForSelectedHabit() {
        val days = listOf(
            day(d.minusDays(1), 100, 100, true, true),
            day(d, 100, 100, true, true),
            day(d.plusDays(1), 2500, 100, true, true)
        )

        assertEquals(listOf(d.minusDays(1)), ProgressMath.eligibleHistory(days, d, "Water").map { it.day })
        assertEquals(0, ProgressMath.completionPercent(days, d, "Water"))
    }

    @Test
    fun completionPercentIncludesTodayOnlyWhenSelectedHabitIsMet() {
        val past = day(d.minusDays(1), 100, 100, true, true)
        val todayIncomplete = day(d, 100, 100, true, true)
        val todayComplete = todayIncomplete.copy(waterMl = 2500)

        assertNull(ProgressMath.completionPercent(listOf(todayIncomplete), d, "Water"))
        assertEquals(100, ProgressMath.completionPercent(listOf(todayComplete), d, "Water"))
        assertEquals(0, ProgressMath.completionPercent(listOf(past, todayIncomplete), d, "Water"))
        assertEquals(50, ProgressMath.completionPercent(listOf(past, todayComplete), d, "Water"))
    }

    @Test
    fun disabledOptionalHabitIsNotIncludedInCompletionDenominator() {
        val disabledToday = day(d, 0, 0, cr = false)

        assertTrue(ProgressMath.eligibleHistory(listOf(disabledToday), d, "Creatine").isEmpty())
        assertNull(ProgressMath.completionPercent(listOf(disabledToday), d, "Creatine"))
        assertNull(ProgressMath.completionPercent(emptyList(), d, "Protein"))
    }

    @Test
    fun historicallyDisabledOptionalDaysAreExcludedFromMixedDenominator() {
        val days = listOf(
            day(d.minusDays(3), 0, 0, creatine = true),
            day(d.minusDays(2), 0, 0, cr = false),
            day(d.minusDays(1), 0, 0)
        )

        assertEquals(50, ProgressMath.completionPercent(days, d, "Creatine"))
    }
}
