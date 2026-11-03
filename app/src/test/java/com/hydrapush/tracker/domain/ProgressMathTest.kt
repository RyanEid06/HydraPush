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
            day(d.minusDays(1), 2500, 100, true, true),
            day(d, 100, 5)
        )
        assertEquals(1, ProgressMath.currentPerfectStreak(days, d))
    }

    @Test
    fun pastGapBreaksStreak() {
        val days = listOf(
            day(d.minusDays(3), 2500, 100, true, true),
            day(d.minusDays(1), 2500, 100, true, true),
            day(d, 2500, 100, true, true)
        )
        assertEquals(2, ProgressMath.currentPerfectStreak(days, d))
        assertEquals(2, ProgressMath.longestPerfectStreak(days))
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
    fun habitStreakCountsConsecutiveDays() {
        val days = listOf(
            day(d.minusDays(2), 2500, 50),
            day(d.minusDays(1), 3000, 50),
            day(d, 2600, 50)
        )
        assertEquals(3, ProgressMath.currentHabitStreak(days, d, "Water"))
        assertEquals(0, ProgressMath.currentHabitStreak(days, d, "Push-ups"))
    }
}
