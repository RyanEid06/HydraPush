package com.hydrapush.tracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrapush.tracker.R
import com.hydrapush.tracker.data.preferences.DEFAULT_PULL_UP_GOAL
import com.hydrapush.tracker.data.repository.DayView
import com.hydrapush.tracker.domain.Achievement
import com.hydrapush.tracker.domain.DailyMetrics
import com.hydrapush.tracker.domain.ProgressMath
import com.hydrapush.tracker.ui.components.BrandHeader
import com.hydrapush.tracker.ui.components.HistoryEntryRow
import com.hydrapush.tracker.ui.components.SectionCard
import com.hydrapush.tracker.ui.theme.HydraColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.ceil
import kotlinx.coroutines.flow.first

@Composable
fun StatsScreen(
    allDays: List<DayView>,
    today: LocalDate,
    achievements: List<Achievement> = emptyList()
) {
    var selectedTab by rememberSaveable { mutableStateOf("Overview") }
    var selectedRange by rememberSaveable { mutableStateOf("7D") }
    var showCalendarHistory by rememberSaveable { mutableStateOf(false) }
    var showAchievementsSheet by remember { mutableStateOf(false) }
    var selectedDayDetail by remember { mutableStateOf<DayView?>(null) }
    val screenScrollState = rememberScrollState()
    LaunchedEffect(showCalendarHistory) { screenScrollState.scrollTo(0) }

    val startDate = when (selectedRange) {
        "7D" -> today.minusDays(6)
        "30D" -> today.minusDays(29)
        "90D" -> today.minusDays(89)
        else -> if (allDays.isNotEmpty()) LocalDate.parse(allDays.first().snapshot.day) else today.minusDays(6)
    }

    val rangeDays = remember(allDays, startDate, today) {
        allDays.filter {
            val d = LocalDate.parse(it.snapshot.day)
            !d.isBefore(startDate) && !d.isAfter(today)
        }.sortedBy { it.snapshot.day }
    }

    val allMetrics = remember(allDays) { allDays.map { it.metrics } }

    val streak = remember(allMetrics, today) {
        ProgressMath.currentActiveStreak(allMetrics, today)
    }
    val bestStreak = remember(allMetrics, today) {
        ProgressMath.longestActiveStreak(allMetrics.filter { !it.day.isAfter(today) })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(screenScrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("stats_screen")
    ) {
        if (showCalendarHistory) {
            CalendarHistoryView(
                allDays = allDays,
                today = today,
                onBack = { showCalendarHistory = false },
                onSelectDay = { dayView -> selectedDayDetail = dayView }
            )
        } else {
            BrandHeader(subtitle = "Your progress & performance", showDateBar = false)
            StatsTitleAndRange(selectedRange = selectedRange, onSelectRange = { selectedRange = it })
            Text("Today joins each rate or average once its goal is met.", fontSize = 10.sp, lineHeight = 14.sp, color = HydraColors.Muted,
                modifier = Modifier.padding(top = 4.dp))
            Spacer(modifier = Modifier.height(12.dp))
            if (selectedTab != "Overview") {
                StatsTabs(selectedTab = selectedTab, onSelectTab = {
                    selectedTab = it
                    showCalendarHistory = false
                })
                Spacer(modifier = Modifier.height(14.dp))
            }
            when (selectedTab) {
                "Overview" -> {
                    OverviewTabContent(
                        rangeDays = rangeDays,
                        today = today,
                        streak = streak,
                        bestStreak = bestStreak,
                        achievements = achievements,
                        onOpenCalendar = { showCalendarHistory = true },
                        onOpenAchievements = { showAchievementsSheet = true },
                        onSelectTab = { selectedTab = it }
                    )
                }
                "Water", "Push-ups", "Pull-ups" -> {
                    HabitDrilldownTabContent(
                        habitName = selectedTab,
                        rangeDays = rangeDays,
                        allDays = allDays,
                        today = today,
                        selectedRange = selectedRange,
                        onOpenCalendar = { showCalendarHistory = true }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Day Detail Dialog
    selectedDayDetail?.let { dayView ->
        AlertDialog(
            onDismissRequest = { selectedDayDetail = null },
            title = {
                Text(
                    text = "Day Details · ${dayView.snapshot.day}",
                    fontWeight = FontWeight.Bold,
                    color = HydraColors.Navy,
                    fontSize = 16.sp
                )
            },
            text = {
                val m = dayView.metrics
                fun status(met: Boolean) = if (met) "Goal Hit" else if (m.day == today) "In progress" else "Missed"
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).testTag("calendar_day_details"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Text(
                            text = when {
                                m.perfect -> "Perfect Day Completed"
                                m.day == today -> "Today is in progress"
                                m.day.isAfter(today) -> "Not applicable"
                                else -> "Incomplete Day"
                            },
                            fontWeight = FontWeight.Bold,
                            color = if (m.perfect) Color(0xFF279919) else HydraColors.Muted,
                            fontSize = 13.sp
                        )
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Water: ${m.waterMl} / ${m.waterGoalMl} ml (${status(m.waterMet)})",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HydraColors.DeepTeal
                        )
                    }
                    if (dayView.waters.isEmpty()) item { Text("No water entries", fontSize = 12.sp, color = HydraColors.Muted) }
                    items(dayView.waters, key = { "water_${it.id}" }) {
                        HistoryEntryRow("+${it.amountMl} ml", it.createdAtEpochMs)
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Push-ups: ${m.pushUpReps} / ${m.pushUpGoalReps} reps (${status(m.pushUpsMet)})",
                            fontSize = 13.sp
                        )
                        if (dayView.sets.isNotEmpty()) {
                            Text(
                                text = "   Sets: ${dayView.sets.joinToString(" + ") { it.reps.toString() }}",
                                fontSize = 12.sp,
                                color = HydraColors.Muted
                            )
                        }
                    }
                    m.pullUpGoalReps?.let { goal ->
                        item {
                            Spacer(Modifier.height(6.dp))
                            Text("Pull-ups: ${m.pullUpReps} / $goal reps (${status(m.pullUpsMet)})",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HydraColors.Navy)
                            if (dayView.pullUpSets.isEmpty()) {
                                Text("No pull-up sets", fontSize = 12.sp, color = HydraColors.Muted)
                            }
                        }
                        items(dayView.pullUpSets, key = { "pullup_${it.id}" }) {
                            HistoryEntryRow("+${it.reps} reps", it.createdAtEpochMs)
                        }
                    }
                    item {
                        Spacer(Modifier.height(4.dp))
                        if (dayView.snapshot.creatineRequired) {
                            Text(
                                text = "Creatine: ${if (dayView.snapshot.creatineDone) "Done ✓" else if (m.day == today) "Not yet" else "Missed ✗"}",
                                fontSize = 13.sp
                            )
                        }
                        if (dayView.snapshot.proteinRequired) {
                            Text(
                                text = "Protein Shake: ${if (dayView.snapshot.proteinDone) "Done ✓" else if (m.day == today) "Not yet" else "Missed ✗"}",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDayDetail = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Achievements Sheet
    if (showAchievementsSheet) {
        AlertDialog(
            onDismissRequest = { showAchievementsSheet = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color(0xFFD49E00),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Badges & Achievements", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(achievements) { ach ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        color = if (ach.isUnlocked) Color(0xFFFFF0D6) else Color(0xFFF0F0F0),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(ach.iconEmoji, fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ach.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (ach.isUnlocked) HydraColors.Navy else HydraColors.Muted
                                )
                                Text(
                                    text = ach.description,
                                    fontSize = 11.sp,
                                    color = HydraColors.Muted
                                )
                                if (!ach.isUnlocked) {
                                    Text("${ach.progressCurrent} / ${ach.progressMax}", fontSize = 10.sp, color = HydraColors.Muted)
                                    LinearProgressIndicator(
                                        progress = { (ach.progressCurrent.toFloat() / ach.progressMax.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                        color = HydraColors.Lime,
                                        trackColor = Color(0xFFE8F1F1),
                                        modifier = Modifier.fillMaxWidth().height(5.dp),
                                        strokeCap = StrokeCap.Round
                                    )
                                }
                            }
                            if (ach.isUnlocked) {
                                Text(
                                    text = "Unlocked!",
                                    fontSize = 11.sp,
                                    color = Color(0xFF279919),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAchievementsSheet = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun StatsTitleAndRange(selectedRange: String, onSelectRange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Insights, contentDescription = null, tint = HydraColors.PrimaryTeal, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text("Stats", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = HydraColors.Navy)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.background(Color(0xFFEBF6F6), RoundedCornerShape(14.dp)).padding(2.dp)
        ) {
            listOf("7D", "30D", "90D", "All").forEach { range ->
                val isSelected = selectedRange == range
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .background(if (isSelected) HydraColors.PrimaryTeal else Color.Transparent, RoundedCornerShape(11.dp))
                        .clickable { onSelectRange(range) }
                        .semantics { selected = isSelected }
                        .padding(horizontal = 8.dp)
                        .testTag("range_pill_$range"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(range, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else HydraColors.Navy)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsTabs(selectedTab: String, onSelectTab: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 350.dp || LocalDensity.current.fontScale > 1.15f) 2 else 4
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple("Overview", "Overview", null),
                Triple("Water", "Water", R.drawable.ic_habit_water),
                Triple("Push-ups", "Push-ups", R.drawable.ic_habit_pushup),
                Triple("Pull-ups", "Pull-ups", R.drawable.ic_habit_pullup)
            ).forEach { (id, label, iconRes) ->
                val isSelected = selectedTab == id
                val tabColor = when (id) {
                    "Water" -> HydraColors.Aqua
                    "Push-ups" -> HydraColors.Lime
                    "Pull-ups" -> HydraColors.SurfaceGold
                    else -> HydraColors.PrimaryTeal
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .background(if (isSelected) tabColor else Color.White, RoundedCornerShape(14.dp))
                        .border(1.dp, if (isSelected) Color.Transparent else Color(0xFFD6E8E9), RoundedCornerShape(14.dp))
                        .clickable { onSelectTab(id) }
                        .semantics { selected = isSelected }
                        .testTag("stats_tab_$id"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (iconRes != null) {
                        Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(16.dp), contentScale = ContentScale.Fit)
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = HydraColors.Navy)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun OverviewTabContent(
    rangeDays: List<DayView>,
    today: LocalDate,
    streak: Int,
    bestStreak: Int,
    achievements: List<Achievement>,
    onOpenCalendar: () -> Unit,
    onOpenAchievements: () -> Unit,
    onSelectTab: (String) -> Unit
) {
    val metrics = remember(rangeDays) { rangeDays.map { it.metrics } }
    val eligibleMetrics = remember(metrics, today) { ProgressMath.eligibleHistory(metrics, today) }
    val completion = ProgressMath.completionPercent(metrics, today)?.let { "$it%" } ?: "—"
    val perfectDaysCount = eligibleMetrics.count { it.perfect }

    BoxWithConstraints(Modifier.fillMaxWidth().testTag("stats_overview_kpis")) {
    val columns = if (maxWidth < 350.dp || LocalDensity.current.fontScale > 1.15f) 2 else 4
    FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = columns,
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KpiCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalFireDepartment,
            number = "$streak",
            label = "Day Streak",
            bgColor = HydraColors.SurfaceCoral,
            accentColor = HydraColors.PerfectDayOrange
        )
        KpiCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.EmojiEvents,
            number = "$bestStreak",
            label = "Best Streak",
            bgColor = HydraColors.SurfaceLime,
            accentColor = HydraColors.PushupLime
        )
        KpiCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.TrackChanges,
            number = completion,
            label = "Goal Completion",
            bgColor = HydraColors.SurfaceAqua,
            accentColor = HydraColors.PrimaryTeal
        )
        KpiCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.CalendarToday,
            number = "$perfectDaysCount",
            label = "Perfect Days",
            bgColor = HydraColors.SurfaceBlue,
            accentColor = HydraColors.SecondaryBlue
        )
    }

    }

    Text("Complete any daily goal to keep your streak going.", fontSize = 11.sp, color = HydraColors.Muted,
        modifier = Modifier.padding(top = 8.dp))
    Spacer(modifier = Modifier.height(14.dp))
    StatsTabs(selectedTab = "Overview", onSelectTab = onSelectTab)

    Spacer(modifier = Modifier.height(14.dp))

    // Weekly Consistency Card (7 Vertical Bars)
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        val displayWeek = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val dateLabel = if (rangeDays.isNotEmpty()) {
            val f = DateTimeFormatter.ofPattern("MMM d")
            "${displayWeek.first().format(f)} – ${displayWeek.last().format(f)}"
        } else ""
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = HydraColors.PrimaryTeal,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Weekly Consistency",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = HydraColors.Navy
                )
            }
            if (dateLabel.isNotEmpty()) {
                Text(dateLabel, fontSize = 11.sp, color = HydraColors.Muted, textAlign = TextAlign.End)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 7 Days Vertical Bars Chart
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 132.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            val daysToDisplay = displayWeek
            val dayMap = rangeDays.associateBy { LocalDate.parse(it.snapshot.day) }

            daysToDisplay.forEach { d ->
                val dayView = dayMap[d]
                val score = dayView?.metrics?.progress ?: 0f
                val pct = (score * 100).toInt()

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (d == today && dayView?.metrics?.perfect != true) "Now" else "$pct%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (score >= 1f) HydraColors.PrimaryTeal else HydraColors.Muted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(72.dp)
                            .background(Color(0xFFE8F2F2), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(score.coerceIn(0f, 1f))
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(HydraColors.Lime, HydraColors.PrimaryTeal)
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                        fontSize = 11.sp,
                        color = HydraColors.Navy,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Habits Completion Card (Circular Progress Rings for Each Habit)
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Habits Completion",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = HydraColors.Navy
        )
        Spacer(modifier = Modifier.height(14.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 3,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Water
            val waterPct = ProgressMath.completionPercent(metrics, today, "Water")
            HabitRingIndicator(
                modifier = Modifier.weight(1f),
                label = "Water",
                iconRes = R.drawable.ic_habit_water,
                percent = waterPct,
                color = HydraColors.Aqua
            )

            // Push-ups
            val pushPct = ProgressMath.completionPercent(metrics, today, "Push-ups")
            HabitRingIndicator(
                modifier = Modifier.weight(1f),
                label = "Push-ups",
                iconRes = R.drawable.ic_habit_pushup,
                percent = pushPct,
                color = HydraColors.Lime
            )

            HabitRingIndicator(
                modifier = Modifier.weight(1f),
                label = "Pull-ups",
                iconRes = R.drawable.ic_habit_pullup,
                percent = ProgressMath.completionPercent(metrics, today, "Pull-ups"),
                color = HydraColors.PullUpGold
            )

            // Creatine
            val creatinePct = ProgressMath.completionPercent(metrics, today, "Creatine")
            HabitRingIndicator(
                modifier = Modifier.weight(1f),
                label = "Creatine",
                iconRes = R.drawable.ic_habit_creatine,
                percent = creatinePct,
                color = HydraColors.SecondaryBlue
            )

            // Protein
            val proteinPct = ProgressMath.completionPercent(metrics, today, "Protein")
            HabitRingIndicator(
                modifier = Modifier.weight(1f),
                label = "Protein",
                iconRes = R.drawable.ic_habit_protein,
                percent = proteinPct,
                color = HydraColors.ProteinCoral
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Achievements Card
    val unlockedCount = achievements.count { it.isUnlocked }
    val totalCount = max(1, achievements.size)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAchievements)
            .testTag("achievements_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFFFFF0D6), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFD49E00),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Achievements",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = HydraColors.Navy
                )
                Text(
                    text = "$unlockedCount / $totalCount Unlocked",
                    fontSize = 12.sp,
                    color = HydraColors.Muted
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { unlockedCount.toFloat() / totalCount },
                    color = HydraColors.Lime,
                    trackColor = Color(0xFFE8F1F1),
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    strokeCap = StrokeCap.Round
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View Achievements",
                tint = HydraColors.Muted
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // History Calendar button
    TextButton(
        onClick = onOpenCalendar,
        modifier = Modifier.fillMaxWidth().testTag("view_history_calendar_button")
    ) {
        Text(
            text = "View history calendar & logs →",
            color = HydraColors.PrimaryTeal,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HabitDrilldownTabContent(
    habitName: String,
    rangeDays: List<DayView>,
    allDays: List<DayView>,
    today: LocalDate,
    selectedRange: String,
    onOpenCalendar: () -> Unit
) {
    val isWater = habitName == "Water"
    val isPullUp = habitName == "Pull-ups"
    val unit = if (isWater) "ml" else "reps"
    val (icon, themeColor, surface) = when (habitName) {
        "Water" -> Triple(R.drawable.ic_habit_water, HydraColors.Aqua, HydraColors.SurfaceAqua)
        "Pull-ups" -> Triple(R.drawable.ic_habit_pullup, HydraColors.PullUpGold, HydraColors.SurfaceGold)
        else -> Triple(R.drawable.ic_habit_pushup, HydraColors.Lime, HydraColors.SurfaceLime)
    }
    val defaultGoal = when (habitName) {
        "Water" -> 2500
        "Pull-ups" -> DEFAULT_PULL_UP_GOAL
        else -> 100
    }
    fun total(day: DayView): Int = when (habitName) {
        "Water" -> day.totalWater
        "Pull-ups" -> day.totalPullUps
        else -> day.totalReps
    }
    val chartStart = when (selectedRange) {
        "7D" -> today.minusDays(6)
        "30D" -> today.minusDays(29)
        "90D" -> today.minusDays(89)
        else -> allDays.minOfOrNull { LocalDate.parse(it.snapshot.day) } ?: today.minusDays(6)
    }
    val chartHistory = remember(allDays, chartStart, today, habitName) {
        val points = allDays.mapNotNull { day ->
            val goal = when (habitName) {
                "Water" -> day.snapshot.waterGoalMl
                "Pull-ups" -> day.snapshot.pullUpGoalReps
                else -> day.snapshot.pushUpGoalReps
            } ?: return@mapNotNull null
            Triple(LocalDate.parse(day.snapshot.day), total(day), goal)
        }
        if (isPullUp && points.isEmpty()) emptyList()
        else {
            val start = if (isPullUp) maxOf(chartStart, points.minOf { it.first }) else chartStart
            expandChartHistory(points, start, today, defaultGoal)
        }
    }
    val (aggregationDays, displayHistory) = remember(chartHistory, selectedRange) {
        if (selectedRange == "All") aggregateChartHistory(chartHistory) else 1 to chartHistory
    }
    val values = displayHistory.map { it.second }
    val goals = displayHistory.map { it.third }
    val latestGoal = goals.lastOrNull() ?: defaultGoal
    val unitStep = if (isWater) 500 else if (isPullUp) 1 else 10
    val observedMax = maxOf(values.maxOrNull() ?: 0, goals.maxOrNull() ?: 0, unitStep)
    val tickStep = ceil(observedMax * 1.12 / 4 / unitStep).toInt().coerceAtLeast(1) * unitStep
    val scaleMax = tickStep * 4
    val chartStateHolder = rememberSaveableStateHolder()

    val rangeMetrics = remember(rangeDays) { rangeDays.map { it.metrics } }
    val eligibleDays = remember(rangeMetrics, today, habitName) { ProgressMath.eligibleHistory(rangeMetrics, today, habitName) }
    val eligibleDates = remember(eligibleDays) { eligibleDays.mapTo(hashSetOf()) { it.day.toString() } }
    val totalValues = rangeDays.filter { it.snapshot.day in eligibleDates }.map(::total)
    val avg = if (totalValues.isNotEmpty()) totalValues.average().toInt() else 0
    val recordDay = rangeDays
        .filter { it.snapshot.day in eligibleDates }
        .maxByOrNull(::total)
    val recordAmount = recordDay?.let(::total)

    val goalDaysHit = eligibleDays.count {
        when (habitName) {
            "Water" -> it.waterMet
            "Pull-ups" -> it.pullUpsMet
            else -> it.pushUpsMet
        }
    }
    val totalEligible = eligibleDays.size

    val hitStreak = remember(allDays, habitName, today) {
        ProgressMath.currentHabitStreak(allDays.map { it.metrics }, today, habitName)
    }

    val bestWeek = remember(allDays, habitName, today) {
        ProgressMath.bestFullWeek(ProgressMath.eligibleHistory(allDays.map { it.metrics }, today, habitName), habitName)
    }

    SectionCard(modifier = Modifier.fillMaxWidth().testTag("stats_drilldown_analysis")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(8.dp))
                Text(habitName, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = HydraColors.Navy)
            }
            Text(
                text = if (chartHistory.isEmpty()) "No history" else {
                    val format = DateTimeFormatter.ofPattern(if (selectedRange == "All") "MMM d, yyyy" else "MMM d")
                    "${chartHistory.first().first.format(format)} – ${chartHistory.last().first.format(format)}"
                },
                fontSize = 11.sp,
                color = HydraColors.Muted,
                textAlign = TextAlign.End
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("stats_drilldown_kpis"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        KpiCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            iconRes = icon,
            number = if (eligibleDays.isEmpty()) "—" else "$avg $unit",
            label = "Daily Average",
            bgColor = surface,
            accentColor = themeColor
        )
        KpiCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            icon = Icons.Default.EmojiEvents,
            number = recordAmount?.let { "$it $unit" } ?: "—",
            label = "Best Day",
            bgColor = Color(0xFFFFF9E8),
            accentColor = Color(0xFFE5A110)
        )
        KpiCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            icon = Icons.Default.CalendarToday,
            number = if (totalEligible > 0) "$goalDaysHit / $totalEligible" else "—",
            label = "Goal Days",
            bgColor = Color.White,
            accentColor = HydraColors.Navy
        )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chart with historical goal references and a readable value axis.
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (isWater) "Daily Intake" else "Daily Reps",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = HydraColors.Navy
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(themeColor, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isWater) "Intake" else "Reps", fontSize = 11.sp, color = HydraColors.Navy)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("---", color = Color(0xFF328C86), fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${if (aggregationDays > 1) "Goal avg" else "Goal"} ($latestGoal $unit)", fontSize = 11.sp, color = HydraColors.Muted)
                }
            }
            if (aggregationDays > 1) {
                Text("Average per ${aggregationDays}-day period · goal averages shown", fontSize = 10.sp, color = HydraColors.Muted)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        chartStateHolder.SaveableStateProvider("$habitName:$selectedRange") {
            val scrollState = rememberScrollState()
            var initialChartPositionSet by rememberSaveable { mutableStateOf(false) }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            val slotWidth = if (displayHistory.size <= 7) (maxWidth - 40.dp) / 7 else 36.dp
            val chartWidth = slotWidth * maxOf(7, displayHistory.size)
            LaunchedEffect(chartWidth, selectedRange, allDays.size) {
                if (!initialChartPositionSet && allDays.isNotEmpty()) {
                    snapshotFlow { scrollState.maxValue }.first { it > 0 || displayHistory.size <= 7 }
                    if (scrollState.maxValue > 0) scrollState.scrollTo(scrollState.maxValue)
                    initialChartPositionSet = true
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(
                    Modifier.width(40.dp).height(132.dp).padding(end = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    (4 downTo 0).forEach { tick ->
                        val amount = tick * tickStep
                        val label = if (isWater) {
                            when {
                                amount == 0 -> "0"
                                amount % 1000 == 0 -> "${amount / 1000}L"
                                else -> "${amount / 1000.0}L"
                            }
                        } else amount.toString()
                        Text(label, fontSize = 11.sp, lineHeight = 14.sp, color = HydraColors.Muted)
                    }
                }
                Column(Modifier.weight(1f).horizontalScroll(scrollState)) {
                    Canvas(Modifier.width(chartWidth).height(132.dp)) {
                        val bottom = size.height - 4.dp.toPx()
                        val slot = slotWidth.toPx()
                        val barWidth = 20.dp.toPx()
                        for (tick in 0..4) {
                            val y = bottom - bottom * tick / 4f
                            drawLine(Color(0xFFE5EEEE), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        }
                        displayHistory.forEachIndexed { index, day ->
                            val x = index * slot + (slot - barWidth) / 2f
                            val amount = day.second
                            val barHeight = amount.toFloat().coerceAtLeast(0f) / scaleMax * bottom
                            if (amount > 0) {
                                drawRoundRect(
                                    color = themeColor,
                                    topLeft = Offset(x, bottom - barHeight),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
                                )
                            }
                            val goalY = bottom - day.third.toFloat() / scaleMax * bottom
                            drawLine(
                                Color(0xFF328C86),
                                Offset(index * slot + 2.dp.toPx(), goalY),
                                Offset((index + 1) * slot - 2.dp.toPx(), goalY),
                                2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))
                            )
                        }
                    }
                    Row(
                        Modifier.width(chartWidth).heightIn(min = 40.dp)
                            .semantics { contentDescription = "${displayHistory.size} chart buckets" }
                            .testTag("stats_drilldown_chart_labels"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        displayHistory.forEachIndexed { index, day ->
                            val date = day.first
                            val label = if (aggregationDays > 1) {
                                if (index == 0 || date.month != displayHistory[index - 1].first.month) date.format(DateTimeFormatter.ofPattern("MMM yy")) else ""
                            } else when (selectedRange) {
                                "7D" -> date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                                "30D" -> if (index % 5 == 0 || index == chartHistory.lastIndex) date.format(DateTimeFormatter.ofPattern("M/d")) else ""
                                "90D" -> if (date.dayOfMonth == 1 || date.dayOfWeek.value == 1) date.format(DateTimeFormatter.ofPattern("d MMM")) else ""
                                else -> if (date.dayOfMonth == 1 || index == 0) date.format(DateTimeFormatter.ofPattern("MMM yy")) else ""
                            }
                            Box(Modifier.width(slotWidth), contentAlignment = Alignment.Center) {
                                Text(label, fontSize = 11.sp, lineHeight = 14.sp, color = HydraColors.Muted, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }

    }

    Spacer(modifier = Modifier.height(14.dp))

    // Consistency & Hit Streak Row
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val consistencyPct = ProgressMath.completionPercent(rangeMetrics, today, habitName)
        val consistencyLabel = if (selectedRange == "7D") "Weekly Consistency" else "Range Consistency"
        SectionCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    tint = HydraColors.PrimaryTeal,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(consistencyLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HydraColors.Navy)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(if (consistencyPct == null) "—" else "$consistencyPct%", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = HydraColors.Navy)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (consistencyPct ?: 0).toFloat() / 100f },
                color = themeColor,
                trackColor = Color(0xFFE8F1F1),
                modifier = Modifier.fillMaxWidth().height(6.dp),
                strokeCap = StrokeCap.Round
            )
        }

        SectionCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TrackChanges,
                    contentDescription = null,
                    tint = HydraColors.PushupLime,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Goal Hit Streak", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HydraColors.Navy)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("$hitStreak days", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = HydraColors.Navy)
            Text("Consecutive days hit", fontSize = 10.sp, color = HydraColors.Muted)
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Record Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFD49E00),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (recordDay == null) "$habitName Record" else "$habitName Best in selected range",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = HydraColors.Navy
                )
                Text(
                    text = recordAmount?.let { "Most in a day: $it $unit" } ?: "Not enough history yet",
                    fontSize = 13.sp,
                    color = HydraColors.Navy,
                    fontWeight = FontWeight.SemiBold
                )
                if (recordDay != null) {
                    Text(
                        text = "Achieved on ${recordDay.snapshot.day}",
                        fontSize = 11.sp,
                        color = HydraColors.Muted
                    )
                }
                if (bestWeek != null) {
                    Text(
                        text = "Best full week (all-time): ${bestWeek.description} (${bestWeek.percent}%)",
                        fontSize = 11.sp,
                        color = HydraColors.Muted
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    TextButton(
        onClick = onOpenCalendar,
        modifier = Modifier.fillMaxWidth().testTag("view_history_calendar_button")
    ) {
        Text(
            text = "View history calendar & logs →",
            color = HydraColors.PrimaryTeal,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

internal fun expandChartHistory(
    history: List<Triple<LocalDate, Int, Int>>,
    start: LocalDate,
    end: LocalDate,
    fallbackGoal: Int
): List<Triple<LocalDate, Int, Int>> {
    if (end.isBefore(start)) return emptyList()
    val sorted = history.sortedBy { it.first }
    val byDate = sorted.associateBy { it.first }
    var goal = sorted.lastOrNull { it.first.isBefore(start) }?.third ?: sorted.firstOrNull()?.third ?: fallbackGoal
    return (0L..java.time.temporal.ChronoUnit.DAYS.between(start, end)).map { offset ->
        val date = start.plusDays(offset)
        val day = byDate[date]
        if (day != null) goal = day.third
        Triple(date, day?.second ?: 0, goal)
    }
}

internal fun aggregateChartHistory(
    history: List<Triple<LocalDate, Int, Int>>,
    maxBuckets: Int = 90
): Pair<Int, List<Triple<LocalDate, Int, Int>>> {
    val bucketDays = ceil(history.size / maxBuckets.toDouble()).toInt().coerceAtLeast(1)
    if (bucketDays == 1) return bucketDays to history
    return bucketDays to history.chunked(bucketDays).map { bucket ->
        Triple(bucket.first().first, bucket.map { it.second }.average().toInt(), bucket.map { it.third }.average().toInt())
    }
}

@Composable
private fun KpiCard(
    modifier: Modifier,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    number: String,
    label: String,
    bgColor: Color,
    accentColor: Color
) {
    Card(
        modifier = modifier.heightIn(min = 112.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(30.dp),
                contentAlignment = Alignment.Center
            ) {
                if (iconRes != null) {
                    Image(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(22.dp), contentScale = ContentScale.Fit)
                } else if (icon != null) {
                    Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = number,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = HydraColors.Navy,
                maxLines = 2,
                softWrap = true,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = HydraColors.Muted,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun HabitRingIndicator(
    modifier: Modifier = Modifier,
    label: String,
    iconRes: Int,
    percent: Int?,
    color: Color
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center
        ) {
            val progressFraction = if (percent != null) (percent / 100f).coerceIn(0f, 1f) else 0f
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                drawCircle(color = Color(0xFFE8F1F1), style = Stroke(stroke))
                if (percent != null) {
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = progressFraction * 360f,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = if (percent != null) "$percent%" else "—",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = HydraColors.Navy
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = HydraColors.Navy,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CalendarHistoryView(
    allDays: List<DayView>,
    today: LocalDate,
    onBack: () -> Unit,
    onSelectDay: (DayView) -> Unit
) {
    var currentMonthValue by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val currentMonth = remember(currentMonthValue) { YearMonth.parse(currentMonthValue) }
    val firstMonth = remember(allDays, today) {
        allDays.minOfOrNull { YearMonth.from(LocalDate.parse(it.snapshot.day)) } ?: YearMonth.from(today)
    }
    val lastMonth = YearMonth.from(today)
    val dayMap = remember(allDays) { allDays.associateBy { it.snapshot.day } }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "History Calendar",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = HydraColors.Navy,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Month Selector Bar
        SectionCard(modifier = Modifier.fillMaxWidth().testTag("calendar_month_card")) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentMonthValue = currentMonth.minusMonths(1).toString() }, enabled = currentMonth > firstMonth) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev month")
                }
                Text(
                    text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = HydraColors.Navy
                )
                IconButton(onClick = { currentMonthValue = currentMonth.plusMonths(1).toString() }, enabled = currentMonth < lastMonth) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next month")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Day of Week Header
            Row(modifier = Modifier.fillMaxWidth().testTag("calendar_weekday_headers")) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { dow ->
                    Box(Modifier.weight(1f).testTag("calendar_weekday_$dow"), contentAlignment = Alignment.Center) {
                        Text(dow, fontSize = 12.sp, color = HydraColors.Muted, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val firstDayOffset = currentMonth.atDay(1).dayOfWeek.value - 1
            val cellCount = ((firstDayOffset + currentMonth.lengthOfMonth() + 6) / 7) * 7
            val cellDates = (0 until cellCount).map { index ->
                val day = index - firstDayOffset + 1
                if (day in 1..currentMonth.lengthOfMonth()) currentMonth.atDay(day) else null
            }
            Column(modifier = Modifier.fillMaxWidth().testTag("calendar_grid")) {
                cellDates.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            val dayView = date?.let { dayMap[it.toString()] }
                            val isToday = date == today
                            val isFuture = date?.isAfter(today) == true
                            val isPerfect = dayView?.metrics?.perfect == true
                            val status = when {
                                date == null || isFuture || dayView == null -> "Not applicable"
                                isToday && !isPerfect -> "In progress"
                                isPerfect -> "Completed"
                                dayView.metrics.progress > 0f -> "Partial"
                                else -> "Incomplete"
                            }
                            val background = when (status) {
                                "Completed" -> Color(0xFFD6F5D2)
                                "Partial", "In progress" -> Color(0xFFE0F6F5)
                                "Incomplete" -> Color(0xFFF7E6E1)
                                else -> Color(0xFFF5F7F7)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = date?.let { "$it, $status" } ?: "" }
                                    .clickable(enabled = date != null && !isFuture && dayView != null) { dayView?.let(onSelectDay) }
                                    .then(if (date != null) Modifier.testTag("calendar_day_${date.dayOfMonth}") else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                if (date != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(background, CircleShape)
                                            .border(if (isToday) 2.dp else 0.dp, if (isToday) HydraColors.PrimaryTeal else Color.Transparent, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${date.dayOfMonth}", fontSize = 12.sp, fontWeight = if (isToday || isPerfect) FontWeight.Bold else FontWeight.Normal, color = HydraColors.Navy)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth().testTag("calendar_legend"),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CalendarLegend(Color(0xFFD6F5D2), "Complete")
                CalendarLegend(Color(0xFFE0F6F5), "Partial / today")
                CalendarLegend(Color(0xFFF7E6E1), "Incomplete")
                CalendarLegend(Color(0xFFF5F7F7), "N/A")
            }
        }
    }
}

@Composable
private fun CalendarLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 10.sp, color = HydraColors.Muted, maxLines = 1)
    }
}
