package com.hydrapush.tracker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.min

data class DailyMetrics(
    val day: LocalDate,
    val waterMl: Int,
    val pushUpReps: Int,
    val waterGoalMl: Int,
    val pushUpGoalReps: Int,
    val creatineRequired: Boolean,
    val proteinRequired: Boolean,
    val creatineDone: Boolean,
    val proteinDone: Boolean,
    val pullUpReps: Int = 0,
    val pullUpGoalReps: Int? = null,
    val pullUpsRequired: Boolean = pullUpGoalReps != null
) {
    init {
        require(waterGoalMl > 0 && pushUpGoalReps > 0) { "Goals must be strictly positive" }
    }

    val waterMet: Boolean get() = waterMl >= waterGoalMl
    val pushUpsMet: Boolean get() = pushUpReps >= pushUpGoalReps
    val pullUpsMet: Boolean get() = pullUpGoalReps?.let { pullUpReps >= it } == true
    val creatineMet: Boolean get() = !creatineRequired || creatineDone
    val proteinMet: Boolean get() = !proteinRequired || proteinDone

    val active: Boolean get() = waterMet || pushUpsMet || pullUpsMet ||
        (creatineRequired && creatineDone) || (proteinRequired && proteinDone)

    val perfect: Boolean get() = waterMet && pushUpsMet && creatineMet && proteinMet &&
        (!pullUpsRequired || pullUpsMet)

    val progress: Float get() {
        val total = 2 + (if (creatineRequired) 1 else 0) + (if (proteinRequired) 1 else 0) +
            (if (pullUpsRequired) 1 else 0)
        var earned = min(1f, waterMl.toFloat() / waterGoalMl) +
            min(1f, pushUpReps.toFloat() / pushUpGoalReps)
        if (creatineRequired && creatineDone) earned += 1f
        if (proteinRequired && proteinDone) earned += 1f
        if (pullUpsRequired && pullUpGoalReps != null) earned += min(1f, pullUpReps.toFloat() / pullUpGoalReps)
        return (earned / total).coerceIn(0f, 1f)
    }
}

data class BestWeekResult(
    val startDay: LocalDate,
    val endDay: LocalDate,
    val percent: Int,
    val description: String
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val isUnlocked: Boolean,
    val progressCurrent: Int = 0,
    val progressMax: Int = 1,
    val unlockedDate: String? = null
)

object ProgressMath {
    fun eligibleHistory(
        days: List<DailyMetrics>,
        today: LocalDate,
        habit: String? = null
    ): List<DailyMetrics> = days.asSequence()
        .filter { !it.day.isAfter(today) }
        .filter { habitIsEnabled(it, habit) }
        .filter { it.day != today || isMet(it, habit) }
        .sortedBy { it.day }
        .toList()

    fun completionPercent(
        days: List<DailyMetrics>,
        today: LocalDate,
        habit: String? = null
    ): Int? {
        val eligible = eligibleHistory(days, today, habit)
        if (eligible.isEmpty()) return null
        return eligible.count { isMet(it, habit) } * 100 / eligible.size
    }

    private fun isMet(day: DailyMetrics, habit: String?): Boolean = when (habit) {
        "Water" -> day.waterMet
        "Push-ups" -> day.pushUpsMet
        "Pull-ups" -> day.pullUpsMet
        "Creatine" -> day.creatineMet
        "Protein" -> day.proteinMet
        null -> day.perfect
        else -> false
    }

    private fun habitIsEnabled(day: DailyMetrics, habit: String?): Boolean = when (habit) {
        "Creatine" -> day.creatineRequired
        "Protein" -> day.proteinRequired
        "Pull-ups" -> day.pullUpGoalReps != null
        else -> true
    }

    /**
     * Today being incomplete does not erase an active streak earned through yesterday.
     * If yesterday was missed, current streak is 0 until today's first completed goal.
     */
    fun currentActiveStreak(days: List<DailyMetrics>, today: LocalDate): Int =
        currentStreak(days, today) { it.active }

    private fun currentStreak(days: List<DailyMetrics>, today: LocalDate, qualifies: (DailyMetrics) -> Boolean): Int {
        val map = days.associateBy { it.day }
        var cursor = if (map[today]?.let(qualifies) == true) today else today.minusDays(1)
        var count = 0
        while (map[cursor]?.let(qualifies) == true) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun longestActiveStreak(days: List<DailyMetrics>): Int {
        val sorted = days.sortedBy { it.day }
        var best = 0
        var running = 0
        var previous: LocalDate? = null
        for (d in sorted) {
            running = if (d.active) {
                if (previous == d.day.minusDays(1)) running + 1 else 1
            } else {
                0
            }
            best = maxOf(best, running)
            previous = d.day
        }
        return best
    }

    /** Consecutive days hitting the specific habit goal */
    fun currentHabitStreak(days: List<DailyMetrics>, today: LocalDate, habit: String): Int =
        currentStreak(days, today) { habitIsEnabled(it, habit) && isMet(it, habit) }

    /**
     * Finds the best complete Monday–Sunday calendar week with the highest consistency.
     */
    fun bestFullWeek(days: List<DailyMetrics>, habit: String? = null): BestWeekResult? {
        if (days.size < 7) return null
        val sorted = days.sortedBy { it.day }
        val map = sorted.associateBy { it.day }
        val minDate = sorted.first().day
        val maxDate = sorted.last().day

        var monday = minDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var bestPercent = -1
        var bestResult: BestWeekResult? = null

        while (!monday.plusDays(6).isAfter(maxDate)) {
            val sunday = monday.plusDays(6)
            var hits = 0
            var eligible = 0
            for (i in 0..6) {
                val d = monday.plusDays(i.toLong())
                val m = map[d]
                if (m != null && habitIsEnabled(m, habit)) {
                    if (isMet(m, habit)) hits++
                    eligible++
                }
            }
            if (eligible == 7) {
                val pct = (hits * 100) / 7
                if (pct >= bestPercent) { // Latest tie breaks forward
                    bestPercent = pct
                    bestResult = BestWeekResult(
                        startDay = monday,
                        endDay = sunday,
                        percent = pct,
                        description = "${monday.month.name.take(3)} ${monday.dayOfMonth} – ${sunday.month.name.take(3)} ${sunday.dayOfMonth}"
                    )
                }
            }
            monday = monday.plusWeeks(1)
        }

        return bestResult
    }

    /** Evaluates badges based on historical performance */
    fun evaluateAchievements(days: List<DailyMetrics>): List<Achievement> {
        val sorted = days.sortedBy { it.day }
        val perfectDays = sorted.filter { it.perfect }
        val maxStreak = longestActiveStreak(sorted)
        val maxWater = sorted.maxOfOrNull { it.waterMl } ?: 0
        val maxPushups = sorted.maxOfOrNull { it.pushUpReps } ?: 0
        val totalLifetimeReps = sorted.sumOf { it.pushUpReps }
        val creatineDays = sorted.count { it.creatineRequired && it.creatineDone }
        val proteinDays = sorted.count { it.proteinRequired && it.proteinDone }

        return listOf(
            Achievement(
                id = "first_perfect",
                title = "First Perfect Day",
                description = "Complete 100% of all required daily targets",
                iconEmoji = "🌟",
                isUnlocked = perfectDays.isNotEmpty(),
                progressCurrent = min(1, perfectDays.size),
                progressMax = 1,
                unlockedDate = perfectDays.firstOrNull()?.day?.toString()
            ),
            Achievement(
                id = "streak_3",
                title = "3-Day Streak",
                description = "Complete at least one goal on 3 consecutive days",
                iconEmoji = "🥉",
                isUnlocked = maxStreak >= 3,
                progressCurrent = min(3, maxStreak),
                progressMax = 3
            ),
            Achievement(
                id = "streak_7",
                title = "7-Day Streak",
                description = "Complete at least one goal each day for 7 days",
                iconEmoji = "🥈",
                isUnlocked = maxStreak >= 7,
                progressCurrent = min(7, maxStreak),
                progressMax = 7
            ),
            Achievement(
                id = "streak_30",
                title = "30-Day Master",
                description = "Complete at least one goal each day for 30 days",
                iconEmoji = "👑",
                isUnlocked = maxStreak >= 30,
                progressCurrent = min(30, maxStreak),
                progressMax = 30
            ),
            Achievement(
                id = "water_2l",
                title = "Hydra Master",
                description = "Drink at least 2,000 ml of water in a single day",
                iconEmoji = "💧",
                isUnlocked = maxWater >= 2000,
                progressCurrent = min(2000, maxWater),
                progressMax = 2000
            ),
            Achievement(
                id = "water_3l",
                title = "Ocean Flow",
                description = "Drink at least 3,000 ml of water in a single day",
                iconEmoji = "🌊",
                isUnlocked = maxWater >= 3000,
                progressCurrent = min(3000, maxWater),
                progressMax = 3000
            ),
            Achievement(
                id = "pushup_50",
                title = "Fifty Strong",
                description = "Complete 50+ push-ups in a single day",
                iconEmoji = "💪",
                isUnlocked = maxPushups >= 50,
                progressCurrent = min(50, maxPushups),
                progressMax = 50
            ),
            Achievement(
                id = "pushup_100",
                title = "Centurion",
                description = "Complete 100+ push-ups in a single day",
                iconEmoji = "💯",
                isUnlocked = maxPushups >= 100,
                progressCurrent = min(100, maxPushups),
                progressMax = 100
            ),
            Achievement(
                id = "pushup_1000",
                title = "1,000 Rep Club",
                description = "Reach 1,000 lifetime push-ups logged",
                iconEmoji = "⚡",
                isUnlocked = totalLifetimeReps >= 1000,
                progressCurrent = min(1000, totalLifetimeReps),
                progressMax = 1000
            ),
            Achievement(
                id = "creatine_7",
                title = "Consistent Creatine",
                description = "Take creatine on 7 days",
                iconEmoji = "⚡",
                isUnlocked = creatineDays >= 7,
                progressCurrent = min(7, creatineDays),
                progressMax = 7
            ),
            Achievement(
                id = "protein_7",
                title = "Fueled & Ready",
                description = "Log protein shakes on 7 days",
                iconEmoji = "🥤",
                isUnlocked = proteinDays >= 7,
                progressCurrent = min(7, proteinDays),
                progressMax = 7
            ),
            Achievement(
                id = "perfect_14",
                title = "Fortnight of Focus",
                description = "Accumulate 14 total perfect days",
                iconEmoji = "🛡️",
                isUnlocked = perfectDays.size >= 14,
                progressCurrent = min(14, perfectDays.size),
                progressMax = 14
            )
        )
    }
}
