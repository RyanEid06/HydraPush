package com.hydrapush.tracker.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hydrapush.tracker.data.preferences.TrackerSettings
import com.hydrapush.tracker.data.repository.TrackerRepository
import com.hydrapush.tracker.ui.screens.DashboardScreen
import com.hydrapush.tracker.ui.screens.SettingsScreen
import com.hydrapush.tracker.ui.screens.StatsScreen
import com.hydrapush.tracker.ui.theme.HydraColors
import com.hydrapush.tracker.ui.theme.HydraTheme
import java.time.LocalDate
import kotlinx.coroutines.delay

enum class MainTab { Dashboard, Stats, Settings }

@Composable
fun HydraPushApp(context: Context) {
    val repository = remember(context) { TrackerRepository.getInstance(context) }
    val days by repository.days.collectAsState(initial = emptyList())
    val settings by repository.settings.collectAsState(initial = TrackerSettings())
    val achievements by repository.achievements.collectAsState(initial = emptyList())

    var currentTab by remember { mutableStateOf(MainTab.Dashboard) }
    var localToday by remember { mutableStateOf(LocalDate.now()) }

    // Periodic rollover & date check
    LaunchedEffect(repository) {
        repository.reconcileToday()
        while (true) {
            val now = LocalDate.now()
            if (now != localToday) {
                localToday = now
                repository.reconcileToday()
            }
            delay(30_000)
        }
    }

    HydraTheme(themeMode = settings.themeMode) {
        Scaffold(
            containerColor = HydraColors.Background,
            contentWindowInsets = WindowInsets.systemBars,
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    listOf(
                        Triple(MainTab.Dashboard, "Dashboard", Icons.Default.Home),
                        Triple(MainTab.Stats, "Stats", Icons.Default.BarChart),
                        Triple(MainTab.Settings, "Settings", Icons.Default.Settings)
                    ).forEach { (destination, title, icon) ->
                        val isSelected = currentTab == destination
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = destination },
                            icon = { Icon(icon, contentDescription = title) },
                            label = { Text(title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = HydraColors.Navy,
                                selectedTextColor = HydraColors.Navy,
                                unselectedIconColor = HydraColors.Muted,
                                unselectedTextColor = HydraColors.Muted,
                                indicatorColor = HydraColors.TealCard
                            ),
                            modifier = Modifier.testTag("nav_item_${title.lowercase()}")
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentTab) {
                    MainTab.Dashboard -> {
                        val todayStr = localToday.toString()
                        val todayDayView = days.find { it.snapshot.day == todayStr }
                        DashboardScreen(
                            repository = repository,
                            today = todayDayView,
                            days = days,
                            onNavigateToStats = { currentTab = MainTab.Stats }
                        )
                    }
                    MainTab.Stats -> {
                        StatsScreen(
                            allDays = days,
                            today = localToday,
                            achievements = achievements
                        )
                    }
                    MainTab.Settings -> {
                        SettingsScreen(
                            repo = repository,
                            settings = settings
                        )
                    }
                }
            }
        }
    }
}
