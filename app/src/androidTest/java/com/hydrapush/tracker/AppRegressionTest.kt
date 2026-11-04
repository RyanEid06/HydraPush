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
