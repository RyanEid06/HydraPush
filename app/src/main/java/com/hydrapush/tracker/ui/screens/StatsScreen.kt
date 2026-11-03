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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrapush.tracker.R
import com.hydrapush.tracker.data.repository.DayView
import com.hydrapush.tracker.domain.Achievement
import com.hydrapush.tracker.domain.DailyMetrics
import com.hydrapush.tracker.domain.ProgressMath
import com.hydrapush.tracker.ui.components.BrandHeader
import com.hydrapush.tracker.ui.components.SectionCard
import com.hydrapush.tracker.ui.theme.HydraColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun StatsScreen(
    allDays: List<DayView>,
    today: LocalDate,
    achievements: List<Achievement> = emptyList()
) {
    var selectedTab by remember { mutableStateOf("Overview") }
    var selectedRange by remember { mutableStateOf("7D") }
    var showCalendarHistory by remember { mutableStateOf(false) }
    var showAchievementsSheet by remember { mutableStateOf(false) }
    var selectedDayDetail by remember { mutableStateOf<DayView?>(null) }

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
        ProgressMath.currentPerfectStreak(allMetrics, today)
    }
    val bestStreak = remember(allMetrics) {
        ProgressMath.longestPerfectStreak(allMetrics)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("stats_screen")
    ) {
        BrandHeader(
            subtitle = "Your progress & performance",
            showDateBar = false
        )

        // Title Row: "Stats" + Range Selector Pills (7D, 30D, 90D, All)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Insights,
                    contentDescription = null,
                    tint = HydraColors.PrimaryTeal,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Stats",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = HydraColors.Navy
                )
            }

            // Range Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .background(Color(0xFFEBF6F6), RoundedCornerShape(12.dp))
                    .padding(2.dp)
            ) {
                listOf("7D", "30D", "90D", "All").forEach { r ->
                    val isSelected = selectedRange == r
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) HydraColors.PrimaryTeal else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedRange = r }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("range_pill_$r")
                    ) {
                        Text(
                            text = r,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else HydraColors.Navy
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Sub-Tabs Row: [Overview] [Water] [Push-ups]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple("Overview", "Overview", null),
                Triple("Water", "Water", R.drawable.water_icon),
                Triple("Push-ups", "Push-ups", R.drawable.pushup_icon)
            ).forEach { (id, label, iconRes) ->
                val isSelected = selectedTab == id
                val tabColor = when (id) {
                    "Water" -> HydraColors.Aqua
                    "Push-ups" -> HydraColors.Lime
                    else -> HydraColors.PrimaryTeal
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isSelected) tabColor else Color.White,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color.Transparent else Color(0xFFD6E8E9),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            selectedTab = id
                            showCalendarHistory = false
                        }
                        .padding(vertical = 11.dp)
                        .testTag("stats_tab_$id"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (iconRes != null) {
                            Image(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) (if (id == "Push-ups") HydraColors.Navy else Color.White) else HydraColors.Navy
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (showCalendarHistory) {
            CalendarHistoryView(
                allDays = allDays,
                onBack = { showCalendarHistory = false },
                onSelectDay = { dayView -> selectedDayDetail = dayView }
            )
        } else {
            when (selectedTab) {
                "Overview" -> {
                    OverviewTabContent(
                        rangeDays = rangeDays,
                        streak = streak,
                        bestStreak = bestStreak,
                        achievements = achievements,
                        onOpenCalendar = { showCalendarHistory = true },
                        onOpenAchievements = { showAchievementsSheet = true }
                    )
                }
                "Water" -> {
                    HabitDrilldownTabContent(
                        isWater = true,
                        rangeDays = rangeDays,
                        allDays = allDays,
                        selectedRange = selectedRange,
                        onOpenCalendar = { showCalendarHistory = true }
                    )
                }
                "Push-ups" -> {
                    HabitDrilldownTabContent(
                        isWater = false,
                        rangeDays = rangeDays,
                        allDays = allDays,
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    val m = dayView.metrics
                    Text(
                        text = if (m.perfect) "Perfect Day Completed" else "Incomplete Day",
                        fontWeight = FontWeight.Bold,
                        color = if (m.perfect) Color(0xFF279919) else HydraColors.Muted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Water: ${dayView.totalWater} / ${dayView.snapshot.waterGoalMl} ml (${if (m.waterMet) "Goal Hit" else "Missed"})",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Push-ups: ${dayView.totalReps} / ${dayView.snapshot.pushUpGoalReps} reps (${if (m.pushUpsMet) "Goal Hit" else "Missed"})",
                        fontSize = 13.sp
                    )
                    if (dayView.sets.isNotEmpty()) {
                        Text(
                            text = "   Sets: ${dayView.sets.joinToString(" + ") { it.reps.toString() }}",
                            fontSize = 12.sp,
                            color = HydraColors.Muted
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (dayView.snapshot.creatineRequired) {
                        Text(
                            text = "Creatine: ${if (dayView.snapshot.creatineDone) "Done ✓" else "Missed ✗"}",
                            fontSize = 13.sp
                        )
                    }
                    if (dayView.snapshot.proteinRequired) {
                        Text(
                            text = "Protein Shake: ${if (dayView.snapshot.proteinDone) "Done ✓" else "Missed ✗"}",
                            fontSize = 13.sp
                        )
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
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = if (ach.isUnlocked) Color(0xFFD49E00) else HydraColors.Muted,
                                    modifier = Modifier.size(20.dp)
                                )
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
private fun OverviewTabContent(
    rangeDays: List<DayView>,
    streak: Int,
    bestStreak: Int,
    achievements: List<Achievement>,
    onOpenCalendar: () -> Unit,
    onOpenAchievements: () -> Unit
) {
    // 4 KPI Cards - Completed days used for denominator so morning use does not penalize
    val todayStr = LocalDate.now().toString()
    val elapsedDays = rangeDays.filter { it.snapshot.day < todayStr }
    val todayView = rangeDays.find { it.snapshot.day == todayStr }
    val perfectDaysCount = rangeDays.count { it.metrics.perfect }
    val completionPercent = if (elapsedDays.isEmpty()) {
        if (todayView?.metrics?.perfect == true) "100%" else "—"
    } else {
        val completedPast = elapsedDays.count { it.metrics.perfect }
        val pct = (completedPast * 100) / elapsedDays.size
        "$pct%"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
            number = completionPercent,
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

    Spacer(modifier = Modifier.height(14.dp))

    // Weekly Consistency Card (7 Vertical Bars)
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
            val last7 = rangeDays.takeLast(7)
            val dateLabel = if (last7.isNotEmpty()) {
                val f = DateTimeFormatter.ofPattern("MMM d")
                "${LocalDate.parse(last7.first().snapshot.day).format(f)} – ${LocalDate.parse(last7.last().snapshot.day).format(f)}"
            } else ""
            Text(
                text = dateLabel,
                fontSize = 11.sp,
                color = HydraColors.Muted
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 7 Days Vertical Bars Chart
        val last7Days = remember(rangeDays) { rangeDays.takeLast(7) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            val daysToDisplay = if (last7Days.isEmpty()) {
                (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
            } else {
                last7Days.map { LocalDate.parse(it.snapshot.day) }
            }
            val dayMap = last7Days.associateBy { LocalDate.parse(it.snapshot.day) }

            daysToDisplay.forEach { d ->
                val dayView = dayMap[d]
                val score = dayView?.metrics?.progress ?: 0f
                val pct = (score * 100).toInt()

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(36.dp)
                ) {
                    Text(
                        text = if (score > 0f) "$pct%" else "0%",
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
                                .fillMaxHeight(score.coerceIn(0.04f, 1f))
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            val eligibleDays = if (elapsedDays.isEmpty()) rangeDays else elapsedDays

            // Water
            val waterHits = eligibleDays.count { it.metrics.waterMet }
            val waterPct = if (eligibleDays.isNotEmpty()) (waterHits * 100) / eligibleDays.size else 0
            HabitRingIndicator(
                label = "Water",
                iconRes = R.drawable.water_icon,
                percent = waterPct,
                color = HydraColors.Aqua
            )

            // Push-ups
            val pushHits = eligibleDays.count { it.metrics.pushUpsMet }
            val pushPct = if (eligibleDays.isNotEmpty()) (pushHits * 100) / eligibleDays.size else 0
            HabitRingIndicator(
                label = "Push-ups",
                iconRes = R.drawable.pushup_icon,
                percent = pushPct,
                color = HydraColors.Lime
            )

            // Creatine
            val creatineReqDays = eligibleDays.filter { it.snapshot.creatineRequired }
            val creatineHits = creatineReqDays.count { it.snapshot.creatineDone }
            val creatinePct = if (creatineReqDays.isNotEmpty()) (creatineHits * 100) / creatineReqDays.size else null
            HabitRingIndicator(
                label = "Creatine",
                iconRes = R.drawable.creatine_icon,
                percent = creatinePct,
                color = HydraColors.SecondaryBlue
            )

            // Protein
            val proteinReqDays = eligibleDays.filter { it.snapshot.proteinRequired }
            val proteinHits = proteinReqDays.count { it.snapshot.proteinDone }
            val proteinPct = if (proteinReqDays.isNotEmpty()) (proteinHits * 100) / proteinReqDays.size else null
            HabitRingIndicator(
                label = "Protein",
                iconRes = R.drawable.protein_icon,
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
private fun HabitDrilldownTabContent(
    isWater: Boolean,
    rangeDays: List<DayView>,
    allDays: List<DayView>,
    selectedRange: String,
    onOpenCalendar: () -> Unit
) {
    val habitName = if (isWater) "Water" else "Push-ups"
    val unit = if (isWater) "ml" else "reps"
    val themeColor = if (isWater) HydraColors.Aqua else HydraColors.Lime

    val totalValues = rangeDays.map { if (isWater) it.totalWater else it.totalReps }
    val avg = if (totalValues.isNotEmpty()) totalValues.average().toInt() else 0
    val recordDay = rangeDays.maxByOrNull { if (isWater) it.totalWater else it.totalReps }
    val recordAmount = if (recordDay == null) 0 else if (isWater) recordDay.totalWater else recordDay.totalReps

    val goalDaysHit = rangeDays.count { if (isWater) it.metrics.waterMet else it.metrics.pushUpsMet }
    val totalEligible = rangeDays.size

    val hitStreak = remember(allDays, isWater) {
        ProgressMath.currentHabitStreak(allDays.map { it.metrics }, LocalDate.now(), habitName)
    }

    val bestWeek = remember(allDays, isWater) {
        ProgressMath.bestFullWeek(allDays.map { it.metrics }, habitName)
    }

    // 3 Top KPI Cards
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        KpiCard(
            modifier = Modifier.weight(1f),
            iconRes = if (isWater) R.drawable.water_icon else R.drawable.pushup_icon,
            number = "$avg $unit",
            label = "Daily Average",
            bgColor = if (isWater) HydraColors.SurfaceAqua else HydraColors.SurfaceLime,
            accentColor = themeColor
        )
        KpiCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.EmojiEvents,
            number = "$recordAmount $unit",
            label = "Best Day",
            bgColor = Color(0xFFFFF9E8),
            accentColor = Color(0xFFE5A110)
        )
        KpiCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.CalendarToday,
            number = "$goalDaysHit / $totalEligible",
            label = "Goal Days",
            bgColor = Color.White,
            accentColor = HydraColors.Navy
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Chart Card with accurate Historical Goal Line for each day
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isWater) "Daily Intake" else "Daily Reps",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = HydraColors.Navy
                )
                Text(
                    text = "Tracked period ($selectedRange)",
                    fontSize = 11.sp,
                    color = HydraColors.Muted
                )
            }

            // Legend
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp).background(themeColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isWater) "Intake" else "Reps", fontSize = 11.sp, color = HydraColors.Navy)
                Spacer(modifier = Modifier.width(8.dp))
                Text("---", color = Color(0xFF67B0A8), fontSize = 11.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Goal", fontSize = 11.sp, color = HydraColors.Muted)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Daily Bar Chart Canvas with horizontal scrolling when many days
        val maxAmount = max(
            if (isWater) 3000 else 120,
            max(
                rangeDays.maxOfOrNull { if (isWater) it.totalWater else it.totalReps } ?: 0,
                rangeDays.maxOfOrNull { if (isWater) it.snapshot.waterGoalMl else it.snapshot.pushUpGoalReps } ?: 0
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            val barWidth = 18.dp
            val spacing = 12.dp
            val totalWidth = maxOf(300.dp, (barWidth + spacing) * maxOf(7, rangeDays.size))

            Canvas(modifier = Modifier.width(totalWidth).height(140.dp)) {
                val chartHeight = size.height - 24.dp.toPx()
                val bWidth = barWidth.toPx()
                val sp = spacing.toPx()

                // Historical Per-Day Goal Line / Segment for each day
                rangeDays.forEachIndexed { index, dayView ->
                    val dayGoal = if (isWater) dayView.snapshot.waterGoalMl else dayView.snapshot.pushUpGoalReps
                    val dayGoalY = (chartHeight - (dayGoal.toFloat() / maxAmount.toFloat()) * chartHeight).coerceIn(6f, chartHeight)
                    val x = index * (bWidth + sp) + sp

                    // Draw segment above this day's bar
                    drawLine(
                        color = Color(0xFF4DB6AC),
                        start = Offset(x, dayGoalY),
                        end = Offset(x + bWidth, dayGoalY),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                    )

                    // Step to next day's goal if not last
                    if (index < rangeDays.size - 1) {
                        val nextGoal = if (isWater) rangeDays[index + 1].snapshot.waterGoalMl else rangeDays[index + 1].snapshot.pushUpGoalReps
                        val nextGoalY = (chartHeight - (nextGoal.toFloat() / maxAmount.toFloat()) * chartHeight).coerceIn(6f, chartHeight)
                        drawLine(
                            color = Color(0xFF4DB6AC),
                            start = Offset(x + bWidth, dayGoalY),
                            end = Offset(x + bWidth + sp, nextGoalY),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                        )
                    }
                }

                // Bars
                rangeDays.forEachIndexed { index, dayView ->
                    val amount = if (isWater) dayView.totalWater else dayView.totalReps
                    val barHeight = ((amount.toFloat() / maxAmount.toFloat()) * chartHeight).coerceIn(4f, chartHeight)
                    val x = index * (bWidth + sp) + sp

                    drawRoundRect(
                        color = themeColor,
                        topLeft = Offset(x, chartHeight - barHeight),
                        size = Size(bWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }

            // Labels under bars
            Row(
                modifier = Modifier
                    .width(totalWidth)
                    .align(Alignment.BottomStart)
            ) {
                rangeDays.forEach { d ->
                    Box(
                        modifier = Modifier.width(18.dp + 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = LocalDate.parse(d.snapshot.day).dayOfWeek.name.take(2),
                            fontSize = 9.sp,
                            color = HydraColors.Muted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Consistency & Hit Streak Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val consistencyPct = if (totalEligible > 0) (goalDaysHit * 100) / totalEligible else 0
        val consistencyLabel = if (selectedRange == "7D") "Weekly Consistency" else "Range Consistency"
        SectionCard(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ShowChart,
                    contentDescription = null,
                    tint = HydraColors.PrimaryTeal,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(consistencyLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HydraColors.Navy)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("$consistencyPct%", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = HydraColors.Navy)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { consistencyPct.toFloat() / 100f },
                color = themeColor,
                trackColor = Color(0xFFE8F1F1),
                modifier = Modifier.fillMaxWidth().height(6.dp),
                strokeCap = StrokeCap.Round
            )
        }

        SectionCard(modifier = Modifier.weight(1f)) {
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
        colors = CardDefaults.cardColors(containerColor = if (isWater) HydraColors.SurfaceAqua else HydraColors.SurfaceLime),
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
                    text = "$habitName Record",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = HydraColors.Navy
                )
                Text(
                    text = "Most in a day: $recordAmount $unit",
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
        modifier = Modifier.fillMaxWidth()
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
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    contentScale = ContentScale.Fit
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = number,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = HydraColors.Navy,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = HydraColors.Muted,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun HabitRingIndicator(
    label: String,
    iconRes: Int,
    percent: Int?,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CalendarHistoryView(
    allDays: List<DayView>,
    onBack: () -> Unit,
    onSelectDay: (DayView) -> Unit
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
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

        Spacer(modifier = Modifier.height(10.dp))

        // Month Selector Bar
        SectionCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev month")
                }
                Text(
                    text = "${currentMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${currentMonth.year}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = HydraColors.Navy
                )
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next month")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day of Week Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { dow ->
                    Text(
                        text = dow,
                        fontSize = 11.sp,
                        color = HydraColors.Muted,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Days Grid
            val firstOfMonth = currentMonth.atDay(1)
            val firstDayOfWeek = firstOfMonth.dayOfWeek.value // 1=Mon, 7=Sun
            val daysInMonth = currentMonth.lengthOfMonth()

            var dayNumber = 1
            var rowStart = 1
            while (dayNumber <= daysInMonth) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    for (col in 1..7) {
                        if (rowStart == 1 && col < firstDayOfWeek || dayNumber > daysInMonth) {
                            Spacer(modifier = Modifier.width(48.dp))
                        } else {
                            val thisDate = currentMonth.atDay(dayNumber)
                            val dayStr = thisDate.toString()
                            val dayView = dayMap[dayStr]
                            val isPerfect = dayView?.metrics?.perfect == true
                            val isToday = thisDate == LocalDate.now()

                            val bgColor = when {
                                isPerfect -> Color(0xFFD6F5D2)
                                dayView != null && (dayView.metrics.waterMet || dayView.metrics.pushUpsMet) -> Color(0xFFE0F6F5)
                                dayView != null -> Color(0xFFF7E6E1)
                                else -> Color(0xFFF5F7F7)
                            }

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable(enabled = dayView != null) {
                                        dayView?.let(onSelectDay)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(bgColor, CircleShape)
                                        .border(
                                            width = if (isToday) 2.dp else 0.dp,
                                            color = if (isToday) HydraColors.PrimaryTeal else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$dayNumber",
                                        fontSize = 12.sp,
                                        fontWeight = if (isToday || isPerfect) FontWeight.Bold else FontWeight.Normal,
                                        color = HydraColors.Navy
                                    )
                                }
                            }
                            dayNumber++
                        }
                    }
                }
                rowStart++
            }
        }
    }
}
