package com.hydrapush.tracker

import androidx.compose.ui.semantics.getOrNull

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StatsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun overviewAndDrilldownFollowTheirDistinctHierarchyAndCalendarFitsSevenColumns() {
        compose.onNodeWithTag("nav_item_stats").performClick()
        val rangeTop = compose.onNodeWithTag("range_pill_7D").fetchSemanticsNode().boundsInRoot.top
        val overviewKpisTop = compose.onNodeWithTag("stats_overview_kpis").fetchSemanticsNode().boundsInRoot.top
        val overviewTabTop = compose.onNodeWithTag("stats_tab_Overview").fetchSemanticsNode().boundsInRoot.top
        assertTrue(rangeTop < overviewKpisTop && overviewKpisTop < overviewTabTop)
        assertNoClippedText(compose)

        compose.onNodeWithTag("stats_tab_Water").performClick()
        val waterTabTop = compose.onNodeWithTag("stats_tab_Water").getUnclippedBoundsInRoot().top
        val analysis = compose.onNodeWithTag("stats_drilldown_analysis").getUnclippedBoundsInRoot()
        val waterKpis = compose.onNodeWithTag("stats_drilldown_kpis").getUnclippedBoundsInRoot()
        val chartLabels = compose.onNodeWithTag("stats_drilldown_chart_labels").getUnclippedBoundsInRoot()
        assertTrue("Water tab and analysis order: tabTop=$waterTabTop analysis=$analysis", waterTabTop < analysis.top)
        val geometryFits = waterKpis.left >= analysis.left && waterKpis.top > analysis.top &&
            waterKpis.right <= analysis.right && waterKpis.bottom < chartLabels.top &&
            chartLabels.left >= analysis.left && chartLabels.right <= analysis.right &&
            chartLabels.bottom <= analysis.bottom
        assertTrue(
            "KPI row and chart must stay inside the analysis card: analysis=$analysis kpis=$waterKpis chartLabels=$chartLabels",
            geometryFits
        )
        assertNoClippedText(compose)

        compose.onNodeWithTag("range_pill_All").performClick()
        compose.onNodeWithTag("stats_drilldown_chart_labels").performScrollTo()
        val buckets = compose.onNodeWithTag("stats_drilldown_chart_labels").fetchSemanticsNode().config
            .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)
            .orEmpty().single().substringBefore(" chart buckets").toInt()
        assertTrue("All-history display should be bounded to 90 buckets, got $buckets", buckets <= 90)

        compose.onNodeWithTag("view_history_calendar_button").performScrollTo().performClick()
        assertTrue(compose.onAllNodesWithTag("range_pill_7D").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithTag("stats_tab_Water").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithTag("calendar_weekday_headers").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithTag("calendar_grid").fetchSemanticsNodes().isNotEmpty())
        val legend = compose.onNodeWithTag("calendar_legend").fetchSemanticsNode().boundsInRoot
        val calendarCard = compose.onNodeWithTag("calendar_month_card").fetchSemanticsNode().boundsInRoot
        assertTrue("Calendar legend must stay within the calendar card", legend.left >= calendarCard.left && legend.right <= calendarCard.right)
        compose.onNodeWithContentDescription("Next month").assertIsNotEnabled()
        assertNoClippedText(compose)

        val widths = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").map {
            compose.onNodeWithTag("calendar_weekday_$it").fetchSemanticsNode().boundsInRoot.width
        }
        assertTrue("Calendar weekday columns should be equal width: $widths", widths.maxOrNull()!! - widths.minOrNull()!! <= 1f)
        val calendarRight = compose.onNodeWithTag("calendar_grid").fetchSemanticsNode().boundsInRoot.right
        val sundayRight = compose.onNodeWithTag("calendar_weekday_Sun").fetchSemanticsNode().boundsInRoot.right
        assertTrue("Sunday column must remain within the calendar card", sundayRight <= calendarRight + 1f)

        val dayWidths = (1..7).map { day ->
            compose.onNodeWithTag("calendar_day_$day").fetchSemanticsNode().boundsInRoot.width
        }
        assertTrue("Calendar date columns should be equal width: $dayWidths", abs(dayWidths.maxOrNull()!! - dayWidths.minOrNull()!!) <= 1f)
    }
}
