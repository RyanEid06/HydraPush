package com.hydrapush.tracker.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
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
import com.hydrapush.tracker.reminders.ReminderScheduler
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
                scope.launch {
                    repository.reconcileToday()
                    ReminderScheduler.scheduleAll(context.applicationContext, repository.settings.first())
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Periodic rollover & date check, schedule reminders on initial launch
    LaunchedEffect(repository) {
        repository.reconcileToday()
        val currentSettings = repository.settings.first()
        ReminderScheduler.scheduleAll(context.applicationContext, currentSettings)
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
                Surface(color = Color.White, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).testTag("bottom_nav_bar"),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            Triple(MainTab.Dashboard, "Dashboard", Icons.Default.Home),
                            Triple(MainTab.Stats, "Stats", Icons.Default.BarChart),
                            Triple(MainTab.Settings, "Settings", Icons.Default.Settings)
                        ).forEach { (destination, title, icon) ->
                            val selected = currentTab == destination
                            Column(Modifier.weight(1f)
                                .background(if (selected) Color(0xFFD5FAF5) else Color.Transparent, RoundedCornerShape(28.dp))
                                .selectable(selected = selected, role = Role.Tab, onClick = { currentTab = destination })
                                .heightIn(min = 62.dp).padding(vertical = 6.dp).testTag("nav_item_${title.lowercase()}"),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(icon, title, Modifier.size(26.dp), tint = if (selected) HydraColors.DeepTeal else HydraColors.Muted)
                                Text(title, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) HydraColors.DeepTeal else HydraColors.Muted)
                            }
                        }
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
                if (loadedDays == null || settings == null || loadedAchievements == null) {
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
