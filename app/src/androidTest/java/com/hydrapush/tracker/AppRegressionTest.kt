package com.hydrapush.tracker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.hydrapush.tracker.data.repository.TrackerRepository
import com.hydrapush.tracker.data.preferences.TrackerSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue

class AppRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var original: TrackerSettings
    private val repo get() = TrackerRepository.getInstance(ApplicationProvider.getApplicationContext<Context>())

    @Before fun configureKnownSettings() = runBlocking {
        original = repo.settings.first()
        repo.updateSettings { it.copy(creatineEnabled = true, proteinEnabled = true, waterReminderEnabled = false) }
    }

    @After fun restoreSettings() = runBlocking { repo.updateSettings { original } }

    private fun waitFor(tag: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun dashboardActionsFitAndUseResponsiveComposition() {
        waitFor("water_card")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = context.resources.configuration
        val stacked = config.screenWidthDp < 390 || config.fontScale > 1.15f
        compose.onNodeWithTag("pushup_card").performScrollTo()
        val water = compose.onNodeWithTag("water_card").fetchSemanticsNode().boundsInRoot
        val push = compose.onNodeWithTag("pushup_card").fetchSemanticsNode().boundsInRoot
        if (stacked) assertTrue("Narrow screens stack dominant cards", push.top >= water.bottom - 1f)
        else assertTrue("Normal screens use paired dominant cards", push.left >= water.right)
        compose.onNodeWithTag("add_water_150").performScrollTo()
        val buttons = listOf(150, 250, 500).map { compose.onNodeWithTag("add_water_$it").fetchSemanticsNode().boundsInRoot }
        assertTrue("All quick amounts share a row", buttons.maxOf { it.top } - buttons.minOf { it.top } <= 1f)
        val card = compose.onNodeWithTag("water_card").fetchSemanticsNode().boundsInRoot
        buttons.forEach { assertTrue("Quick amount fits card", it.left >= card.left && it.right <= card.right) }
        compose.onNodeWithTag("today_logs_button").performScrollTo().assertHasClickAction()
        assertNoClippedText(compose)
    }

    private fun waterTotal() = runBlocking { repo.days.first().first { it.snapshot.day == java.time.LocalDate.now().toString() }.totalWater }
    private fun repTotal() = runBlocking { repo.days.first().first { it.snapshot.day == java.time.LocalDate.now().toString() }.totalReps }

    @Test fun dashboardHistoriesRemoveOnlyTheSelectedWaterEntryAndPushUpSet() {
        waitFor("water_card")
        val initialWater = waterTotal()
        val initialReps = repTotal()
        val (waterId, otherWaterId, setId, otherSetId) = runBlocking {
            listOf(repo.addWater(175), repo.addWater(225), repo.addSet(7), repo.addSet(9))
        }
        try {
            fun removeFromHistory(habit: String, row: String, amount: String) {
                compose.onNodeWithTag("${habit}_history_button").performScrollTo().performClick()
                compose.onNodeWithTag("${habit}_history_list").performScrollToNode(hasTestTag(row))
                val inRow = hasAnyAncestor(hasTestTag(row))
                compose.onNode(hasText(amount) and inRow).assertIsDisplayed()
                assertNoClippedText(compose)
                compose.onNode(hasText("Remove") and inRow).performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithTag(row).fetchSemanticsNodes().isEmpty() }
                compose.onNode(hasText("Done") and hasAnyAncestor(isDialog())).performClick()
            }

            removeFromHistory("water", "water_history_$waterId", "+175 ml")
            assertTrue(waterTotal() == initialWater + 225)
            removeFromHistory("pushup", "pushup_history_$setId", "+7 reps")
            assertTrue(repTotal() == initialReps + 9)
        } finally {
            runBlocking {
                repo.undoWater(waterId)
                repo.undoWater(otherWaterId)
                repo.removeSet(setId)
                repo.removeSet(otherSetId)
            }
        }
    }

    @Test fun pullUpSetDialogOffersPresetsAndCustomReps() {
        waitFor("pullup_card")
        val today = java.time.LocalDate.now().toString()
        fun todayView() = runBlocking { repo.days.first().first { it.snapshot.day == today } }
        val initial = todayView().pullUpSets
        try {
            var expected = initial.sumOf { it.reps }
            compose.onNodeWithTag("add_pullup_1").assertDoesNotExist()
            listOf(1, 2, 3, 5, 10).forEach { reps ->
                compose.onNodeWithTag("add_pullup_set_button").performScrollTo().performClick()
                compose.onNodeWithTag("add_pullup_$reps").assertIsDisplayed().performClick()
                assertTrue("A preset fills the field without saving", todayView().totalPullUps == expected)
                compose.onNodeWithTag("save_pullup_set_button").performClick()
                expected += reps
                compose.waitUntil(15_000) { todayView().totalPullUps == expected }
            }
            compose.onNodeWithTag("add_pullup_set_button").performScrollTo().performClick()
            compose.onNodeWithTag("add_pullup_set_input").performTextInput("7")
            compose.onNodeWithTag("save_pullup_set_button").performClick()
            compose.waitUntil(15_000) { todayView().totalPullUps == expected + 7 }
            assertNoClippedText(compose)
            compose.onNodeWithTag("pullup_history_button").performScrollTo().performClick()
            compose.onNodeWithTag("pullup_history_list").performScrollToNode(hasText("+7 reps"))
            compose.onAllNodesWithText("+7 reps").onLast().assertIsDisplayed()
            compose.onNode(hasText("Done") and hasAnyAncestor(isDialog())).performClick()
        } finally {
            val existingIds = initial.map { it.id }.toSet()
            runBlocking {
                todayView().pullUpSets.filterNot { it.id in existingIds }.forEach { repo.removePullUpSet(it.id) }
            }
        }
    }

    @Test fun dashboardQuickCustomSetUndoAndTodayCorrection() {
        waitFor("water_card")
        compose.mainClock.autoAdvance = false
        fun waitWater(total: Int, snackbar: Boolean = false) {
            compose.waitUntil(15_000) {
                compose.mainClock.advanceTimeByFrame()
                waterTotal() == total && (!snackbar || compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty())
            }
        }
        fun waitReps(total: Int, snackbar: Boolean = false) {
            compose.waitUntil(15_000) {
                compose.mainClock.advanceTimeByFrame()
                repTotal() == total && (!snackbar || compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty())
            }
        }
        fun undoLatest() {
            compose.mainClock.advanceTimeBy(400)
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText("Undo").performClick()
        }
        val initialWater = waterTotal()
        val initialReps = repTotal()
        var added = 0
        listOf(150, 250, 500).forEach { amount ->
            compose.onNodeWithTag("add_water_$amount").performScrollTo().performClick()
            added += amount
            waitWater(initialWater + added, snackbar = true)
        }
        undoLatest()
        waitWater(initialWater + 400)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("water_history_button").performScrollTo().performClick()
        compose.onNodeWithTag("water_history_list").performScrollToNode(hasText("+250 ml"))
        compose.onAllNodesWithText("+250 ml").onLast().assertIsDisplayed()
        compose.onNode(hasText("Done") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithTag("custom_water_button").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithTag("custom_water_input").performTextInput("330")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("save_water_button").performClick()
        waitWater(initialWater + 730, snackbar = true)
        undoLatest()
        waitWater(initialWater + 400)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("add_set_button").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithTag("add_set_input").performTextInput("20")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("save_set_button").performClick()
        waitReps(initialReps + 20, snackbar = true)
        undoLatest()
        waitReps(initialReps)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("add_set_button").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithTag("add_set_input").performTextInput("15")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("save_set_button").performClick()
        waitReps(initialReps + 15, snackbar = true)
        val addedSetId = runBlocking { repo.days.first().last().sets.last { it.reps == 15 }.id }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("today_logs_button").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(250)
        val addedWaterId = runBlocking { repo.days.first().last().waters.last { it.amountMl == 150 }.id }
        compose.onNodeWithTag("today_logs_list").performScrollToNode(hasTestTag("remove_water_$addedWaterId"))
        compose.onNodeWithTag("remove_water_$addedWaterId").performClick()
        waitWater(initialWater + 250)
        compose.onNodeWithTag("today_logs_list").performScrollToNode(hasTestTag("remove_set_$addedSetId"))
        compose.onNodeWithTag("remove_set_$addedSetId").performClick()
        waitReps(initialReps)
        compose.onNode(hasText("Done") and hasAnyAncestor(isDialog())).performClick()
        compose.mainClock.autoAdvance = true
    }

    @Test fun oneAndZeroOptionalHabitsUseFullWidthAndPreserveCompletion() {
        runBlocking { repo.updateSettings { it.copy(creatineEnabled = false, proteinEnabled = true) } }
        waitFor("protein_card")
        compose.onNodeWithTag("creatine_card").assertDoesNotExist()
        compose.onNodeWithTag("protein_card").performScrollTo()
        val card = compose.onNodeWithTag("protein_card").fetchSemanticsNode().boundsInRoot
        val screen = compose.onNodeWithTag("dashboard_screen").fetchSemanticsNode().boundsInRoot
        val density = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density
        assertTrue("Single habit spans both 16dp content gutters", kotlin.math.abs(card.width - (screen.width - 32 * density)) <= 2f)
        val before = runBlocking { repo.days.first().last().snapshot.proteinDone }
        compose.onNodeWithTag("protein_card").performClick()
        compose.waitUntil(15_000) { runBlocking { repo.days.first().last().snapshot.proteinDone != before } }
        runBlocking { repo.updateSettings { it.copy(proteinEnabled = false) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("protein_card").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("creatine_card").assertDoesNotExist()
        runBlocking { repo.updateSettings { it.copy(proteinEnabled = true) } }
        waitFor("protein_card")
        compose.onNodeWithTag("protein_card").performScrollTo().assertExists()
        assertTrue("Re-enabling preserves completion", runBlocking { repo.days.first().last().snapshot.proteinDone != before })
    }

    @Test fun statsSelectionSurvivesBottomNavigation() {
        compose.onNodeWithTag("nav_item_stats").performClick()
        waitFor("stats_tab_Water")
        compose.onNodeWithTag("stats_tab_Water").performClick()
        compose.onNodeWithTag("range_pill_30D").performClick()
        compose.onNodeWithTag("nav_item_dashboard").performClick()
        compose.onNodeWithTag("dashboard_screen").assertExists()
        compose.onNodeWithTag("nav_item_stats").performClick()
        compose.onNodeWithTag("stats_tab_Water").assertIsSelected()
        compose.onNodeWithTag("range_pill_30D").assertIsSelected()
        compose.activityRule.scenario.recreate()
        waitFor("stats_tab_Water")
        compose.onNodeWithTag("stats_tab_Water").assertIsSelected()
        compose.onNodeWithTag("range_pill_30D").assertIsSelected()
    }

    @Test fun goalControlsUpdateTodayAndKeepPastRequirements() {
        waitFor("water_card")
        val previous = runBlocking { repo.days.first().filter { it.snapshot.day != java.time.LocalDate.now().toString() }.map { it.snapshot } }
        compose.onNodeWithTag("nav_item_settings").performClick()
        compose.onNodeWithText("Water Goal").performScrollTo().performClick()
        compose.onNodeWithTag("edit_goal_input").performTextClearance()
        compose.onNodeWithTag("edit_goal_input").performTextInput("20000")
        compose.onNodeWithTag("save_goal_button").performClick()
        compose.waitUntil(15_000) { runBlocking { repo.days.first().last().snapshot.waterGoalMl == 20000 } }
        compose.onNodeWithTag("nav_item_dashboard").performClick()
        compose.onNodeWithText("of 20000 ml").performScrollTo().assertExists()
        assertNoClippedText(compose)
        assertTrue("Past requirements are immutable", previous == runBlocking { repo.days.first().filter { it.snapshot.day != java.time.LocalDate.now().toString() }.map { it.snapshot } })
    }

    @Test fun waterReminderCanBeConfiguredWhileOff() {
        compose.onNodeWithTag("nav_item_settings").performClick()
        waitFor("schedule_Water Reminder")
        compose.onNodeWithTag("reminder_switch_Water Reminder").performScrollTo().assertIsOff()
        compose.onNodeWithTag("schedule_Water Reminder").performScrollTo().performClick()
        compose.onNodeWithText("Save Time").assertDoesNotExist()
        compose.onNode(hasText("Every 3 hours") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Save Interval").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Save Interval").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("schedule_Water Reminder").assertTextContains("Every 3 hours", substring = true)
        compose.onNodeWithTag("reminder_switch_Water Reminder").assertIsOff()
    }

    @Test fun optionalHabitVisibilityAndNavigation() {
        compose.onNodeWithTag("nav_item_settings").performClick()
        waitFor("switch_creatine")
        compose.onNodeWithTag("switch_creatine").performScrollTo().performClick()
        compose.waitUntil(30_000) { runBlocking { !repo.settings.first().creatineEnabled } }
        compose.onNodeWithTag("nav_item_dashboard").performClick()
        compose.onNodeWithTag("creatine_card").assertDoesNotExist()
        compose.onNodeWithTag("nav_item_settings").performClick()
        compose.onNodeWithTag("switch_creatine").performScrollTo().performClick()
        compose.waitUntil(30_000) { runBlocking { repo.settings.first().creatineEnabled } }
        compose.onNodeWithTag("nav_item_dashboard").performClick()
        compose.onNodeWithTag("creatine_card").performScrollTo().assertExists()
        compose.onNodeWithTag("nav_item_stats").performClick()
        compose.onNodeWithTag("stats_screen").assertExists()
    }
}
