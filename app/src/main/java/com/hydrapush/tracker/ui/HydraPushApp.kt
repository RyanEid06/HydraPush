package com.hydrapush.tracker.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrapush.tracker.data.preferences.TrackerSettings
import com.hydrapush.tracker.data.repository.TrackerRepository
import com.hydrapush.tracker.data.repository.DayView
import com.hydrapush.tracker.domain.Achievement
import com.hydrapush.tracker.ui.screens.DashboardScreen
import com.hydrapush.tracker.ui.screens.SettingsScreen
import com.hydrapush.tracker.ui.screens.StatsScreen
import com.hydrapush.tracker.ui.theme.HydraColors
import com.hydrapush.tracker.ui.theme.HydraTheme
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class MainTab { Dashboard, Stats, Settings }

@Composable
fun HydraPushApp(context: Context) {
    val repository = remember(context) { TrackerRepository.getInstance(context) }
    val loadedDays by produceState<List<DayView>?>(null, repository) {
        repository.days.collect { value = it }
    }
    val settings by produceState<TrackerSettings?>(null, repository) {
        repository.settings.collect { value = it }
    }
    val loadedAchievements by produceState<List<Achievement>?>(null, repository) {
        repository.achievements.collect { value = it }
    }
    val days = loadedDays.orEmpty()
    val achievements = loadedAchievements.orEmpty()

    var currentTab by rememberSaveable { mutableStateOf(MainTab.Dashboard) }
    val screenState = rememberSaveableStateHolder()
    var localToday by remember { mutableStateOf(LocalDate.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner, repository) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                localToday = LocalDate.now()
                scope.launch { repository.reconcileToday() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Periodic rollover & date check, schedule reminders on initial launch
    LaunchedEffect(repository) {
        repository.reconcileToday()
        val currentSettings = repository.settings.first()
        com.hydrapush.tracker.reminders.ReminderScheduler.scheduleAll(context.applicationContext, currentSettings)
        while (true) {
            val now = LocalDate.now()
            if (now != localToday) {
                localToday = now
                repository.reconcileToday()
            }
            delay(30_000)
        }
    }

    HydraTheme {
        Scaffold(
            containerColor = HydraColors.Background,
            contentWindowInsets = WindowInsets.systemBars,
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 2.dp,
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
                            label = { Text(title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = HydraColors.PrimaryTeal,
                                selectedTextColor = HydraColors.Navy,
                                unselectedIconColor = HydraColors.Muted,
                                unselectedTextColor = HydraColors.Muted,
                                indicatorColor = Color(0xFFD7F5F0)
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
                    .consumeWindowInsets(paddingValues)
            ) {
                if (loadedDays == null || settings == null || loadedAchievements == null ||
                    days.none { it.snapshot.day == localToday.toString() }) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else screenState.SaveableStateProvider(currentTab.name) {
                when (currentTab) {
                    MainTab.Dashboard -> {
                        val todayStr = localToday.toString()
                        val todayDayView = days.find { it.snapshot.day == todayStr }
                        DashboardScreen(
                            repository = repository,
                            today = todayDayView,
                            localToday = localToday,
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
                            settings = settings!!
                        )
                    }
                }
                }
            }
        }
    }
}
