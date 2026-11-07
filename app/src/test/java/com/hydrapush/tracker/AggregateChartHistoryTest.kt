package com.hydrapush.tracker

import com.hydrapush.tracker.ui.screens.aggregateChartHistory
import com.hydrapush.tracker.ui.screens.expandChartHistory
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregateChartHistoryTest {
    @Test fun historicalGoalsCarryAcrossMultipleMissingDates() {
        val start = LocalDate.of(2026, 1, 1)
        val sparse = listOf(Triple(start, 100, 100), Triple(start.plusDays(2), 150, 200))
        val displayed = expandChartHistory(sparse, start, start.plusDays(5), 100)
        assertEquals(listOf(100, 100, 200, 200, 200, 200), displayed.map { it.third })
        assertEquals(listOf(100, 0, 150, 0, 0, 0), displayed.map { it.second })
        assertEquals(listOf(200, 200), expandChartHistory(sparse, start.plusDays(4), start.plusDays(5), 100).map { it.third })
        assertEquals(2, sparse.size)
    }

    @Test
    fun allHistoryDisplayIsBoundedAndAveragesAmountsAndHistoricalGoals() {
        val history = (0 until 365).map { offset ->
            Triple(LocalDate.of(2025, 1, 1).plusDays(offset.toLong()), offset, 2000 + offset)
        }
        val originalFirst = history.first()

        val (bucketDays, display) = aggregateChartHistory(history)

        assertTrue(bucketDays > 1)
        assertTrue(display.size <= 90)
        assertEquals((0 until bucketDays).average().toInt(), display.first().second)
        assertEquals((2000 until 2000 + bucketDays).average().toInt(), display.first().third)
        assertEquals(originalFirst, history.first())
    }

    @Test
    fun shortHistoryKeepsItsDailyValuesUnchanged() {
        val history = listOf(
            Triple(LocalDate.of(2026, 1, 1), 1200, 2500),
            Triple(LocalDate.of(2026, 1, 2), 1800, 3000)
        )

        val (bucketDays, display) = aggregateChartHistory(history)

        assertEquals(1, bucketDays)
        assertEquals(history, display)
    }
}
