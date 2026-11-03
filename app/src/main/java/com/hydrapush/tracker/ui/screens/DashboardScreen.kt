package com.hydrapush.tracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrapush.tracker.data.repository.DayView
import com.hydrapush.tracker.data.repository.TrackerRepository
import com.hydrapush.tracker.domain.ProgressMath
import com.hydrapush.tracker.ui.components.BrandHeader
import com.hydrapush.tracker.ui.theme.HydraColors
import java.time.LocalDate
import kotlin.math.min
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    repository: TrackerRepository,
    today: DayView?,
    days: List<DayView>,
    onNavigateToStats: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var showWaterDialog by remember { mutableStateOf(false) }
    var showPushUpDialog by remember { mutableStateOf(false) }
    var inputAmount by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }

    val localToday = remember { LocalDate.now() }
    val metrics = today?.metrics
    val streak = remember(days, localToday) {
        ProgressMath.currentPerfectStreak(days.map { it.metrics }, localToday)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("dashboard_screen")
    ) {
        BrandHeader(
            subtitle = "Daily performance",
            showDateBar = true,
            date = localToday
        )

        if (metrics == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = HydraColors.PrimaryTeal)
            }
        } else {
            // Main Top Row: Two Equal-Dominance Side-by-Side Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Water Card
                WaterCard(
                    modifier = Modifier.weight(1f),
                    waterMl = metrics.waterMl,
                    goalMl = metrics.waterGoalMl,
                    hasEntries = today.waters.isNotEmpty(),
                    onAddQuick = { amount -> scope.launch { repository.addWater(amount) } },
                    onCustomClick = {
                        inputAmount = ""
                        dialogError = null
                        showWaterDialog = true
                    },
                    onUndoClick = {
                        today.waters.lastOrNull()?.let { last ->
                            scope.launch { repository.undoWater(last.id) }
                        }
                    }
                )

                // 2. Push-ups Card
                PushUpCard(
                    modifier = Modifier.weight(1f),
                    reps = metrics.pushUpReps,
                    goalReps = metrics.pushUpGoalReps,
                    sets = today.sets.map { it.reps },
                    hasSets = today.sets.isNotEmpty(),
                    onAddSetClick = {
                        inputAmount = ""
                        dialogError = null
                        showPushUpDialog = true
                    },
                    onUndoClick = {
                        today.sets.lastOrNull()?.let { last ->
                            scope.launch { repository.removeSet(last.id) }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Optional Active Habits Row (Creatine & Protein Shake)
            val showCreatine = today.snapshot.creatineRequired
            val showProtein = today.snapshot.proteinRequired

            if (showCreatine || showProtein) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showCreatine) {
                        SupplementCard(
                            modifier = Modifier.weight(1f),
                            name = "Creatine",
                            subtitle = "5 g daily",
                            emoji = "⚡",
                            isDone = today.snapshot.creatineDone,
                            backgroundColor = HydraColors.SurfaceAqua,
                            badgeColor = HydraColors.PrimaryTeal,
                            testTag = "creatine_card",
                            onToggle = { done ->
                                scope.launch { repository.setSupplementDone(creatine = true, done = done) }
                            }
                        )
                    }
                    if (showProtein) {
                        SupplementCard(
                            modifier = Modifier.weight(1f),
                            name = "Protein Shake",
                            subtitle = "1 serving (≈ 25–30 g)",
                            emoji = "🥤",
                            isDone = today.snapshot.proteinDone,
                            backgroundColor = HydraColors.SurfaceCoral,
                            badgeColor = HydraColors.ProteinCoral,
                            testTag = "protein_card",
                            onToggle = { done ->
                                scope.launch { repository.setSupplementDone(creatine = false, done = done) }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Bottom Performance Summary Row: Streak Card + Daily Completion Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Streak Card
                Card(
                    modifier = Modifier
                        .weight(1.1f)
                        .clickable(onClick = onNavigateToStats)
                        .testTag("streak_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = HydraColors.SurfaceCoral),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔥",
                            fontSize = 28.sp,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$streak-day Streak",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = HydraColors.Navy
                            )
                            Text(
                                text = if (streak > 0) "Keep it going!" else "Start today!",
                                fontSize = 12.sp,
                                color = HydraColors.Muted
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "View Stats",
                            tint = HydraColors.Muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Daily Completion Card
                Card(
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("daily_completion_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Daily Completion",
                                fontSize = 11.sp,
                                color = HydraColors.Muted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val pct = (metrics.progress * 100).toInt()
                            Text(
                                text = "$pct%",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = HydraColors.Navy
                            )
                        }

                        // Mini progress circle with trophy icon
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 5.dp.toPx()
                                drawCircle(
                                    color = Color(0xFFEAF5E5),
                                    style = Stroke(width = strokeWidth)
                                )
                                drawArc(
                                    color = HydraColors.Lime,
                                    startAngle = -90f,
                                    sweepAngle = metrics.progress * 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                            Text(
                                text = "🏆",
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // Custom Water Dialog
    if (showWaterDialog) {
        AlertDialog(
            onDismissRequest = { showWaterDialog = false },
            title = {
                Text(
                    text = "Custom Water Amount",
                    fontWeight = FontWeight.Bold,
                    color = HydraColors.Navy
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter amount of water consumed (ml):",
                        fontSize = 13.sp,
                        color = HydraColors.Muted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputAmount,
                        onValueChange = {
                            inputAmount = it.filter(Char::isDigit).take(5)
                            dialogError = null
                        },
                        label = { Text("Water (ml)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_water_input")
                    )
                    if (dialogError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dialogError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(200, 330, 750, 1000).forEach { preset ->
                            SuggestionChip(
                                onClick = { inputAmount = preset.toString() },
                                label = { Text("$preset ml", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = inputAmount.toIntOrNull()
                        if (amount == null || amount !in 1..5000) {
                            dialogError = "Enter an amount between 1 and 5,000 ml"
                        } else {
                            scope.launch { repository.addWater(amount) }
                            showWaterDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HydraColors.PrimaryTeal),
                    modifier = Modifier.testTag("save_water_button")
                ) {
                    Text("Add Water")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWaterDialog = false }) {
                    Text("Cancel", color = HydraColors.Muted)
                }
            }
        )
    }

    // Add Push-up Set Dialog
    if (showPushUpDialog) {
        AlertDialog(
            onDismissRequest = { showPushUpDialog = false },
            title = {
                Text(
                    text = "Add Push-up Set",
                    fontWeight = FontWeight.Bold,
                    color = HydraColors.Navy
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter completed repetitions in this set:",
                        fontSize = 13.sp,
                        color = HydraColors.Muted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputAmount,
                        onValueChange = {
                            inputAmount = it.filter(Char::isDigit).take(4)
                            dialogError = null
                        },
                        label = { Text("Repetitions") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_set_input")
                    )
                    if (dialogError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dialogError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(10, 15, 20, 25, 30).forEach { preset ->
                            SuggestionChip(
                                onClick = { inputAmount = preset.toString() },
                                label = { Text("$preset", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reps = inputAmount.toIntOrNull()
                        if (reps == null || reps !in 1..1000) {
                            dialogError = "Enter reps between 1 and 1,000"
                        } else {
                            scope.launch { repository.addSet(reps) }
                            showPushUpDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HydraColors.Lime,
                        contentColor = HydraColors.Navy
                    ),
                    modifier = Modifier.testTag("save_set_button")
                ) {
                    Text("Save Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPushUpDialog = false }) {
                    Text("Cancel", color = HydraColors.Muted)
                }
            }
        )
    }
}

@Composable
private fun WaterCard(
    modifier: Modifier = Modifier,
    waterMl: Int,
    goalMl: Int,
    hasEntries: Boolean,
    onAddQuick: (Int) -> Unit,
    onCustomClick: () -> Unit,
    onUndoClick: () -> Unit
) {
    Card(
        modifier = modifier
            .border(1.dp, Color(0xFFD6F3F1), RoundedCornerShape(22.dp))
            .testTag("water_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HydraColors.SurfaceAqua),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Drop Icon + Title + Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💧", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Water",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = HydraColors.Navy
                    )
                    Text(
                        text = "Stay hydrated",
                        fontSize = 11.sp,
                        color = HydraColors.Muted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Circular Progress Ring
            val progressFraction = min(1f, waterMl.toFloat() / goalMl.coerceAtLeast(1))
            val percent = (waterMl.toFloat() / goalMl * 100).toInt()

            Box(
                modifier = Modifier.size(126.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 10.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    // Track
                    drawArc(
                        color = Color(0xFFD4EFF0),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth)
                    )

                    // Progress arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(HydraColors.Aqua, HydraColors.PrimaryTeal, HydraColors.Aqua)
                        ),
                        startAngle = -90f,
                        sweepAngle = progressFraction * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🥛", fontSize = 16.sp)
                    Text(
                        text = "$waterMl ml",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = HydraColors.Navy
                    )
                    Text(
                        text = "of $goalMl ml",
                        fontSize = 11.sp,
                        color = HydraColors.Muted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFDCF7F5), RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$percent%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = HydraColors.DeepTeal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick add pill buttons (+150, +250, +500)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(150, 250, 500).forEach { amount ->
                    Button(
                        onClick = { onAddQuick(amount) },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("add_water_$amount"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = HydraColors.DeepTeal
                        ),
                        contentPadding = PaddingValues(0.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.5.dp)
                    ) {
                        Text(
                            text = "+$amount",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Custom amount button
            OutlinedButton(
                onClick = onCustomClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("custom_water_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HydraColors.DeepTeal),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBCE8E5)),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Custom amount",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (hasEntries) {
                TextButton(
                    onClick = onUndoClick,
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("undo_water_button"),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = HydraColors.Muted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Undo last",
                        fontSize = 11.sp,
                        color = HydraColors.Muted
                    )
                }
            }
        }
    }
}

@Composable
private fun PushUpCard(
    modifier: Modifier = Modifier,
    reps: Int,
    goalReps: Int,
    sets: List<Int>,
    hasSets: Boolean,
    onAddSetClick: () -> Unit,
    onUndoClick: () -> Unit
) {
    Card(
        modifier = modifier
            .border(1.dp, Color(0xFFE2F5CD), RoundedCornerShape(22.dp))
            .testTag("pushup_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HydraColors.SurfaceLime),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header Row: Runner Icon + Title + Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("↗", fontSize = 16.sp, color = HydraColors.Lime, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Push-ups",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = HydraColors.Navy
                    )
                    Text(
                        text = "Build strength",
                        fontSize = 11.sp,
                        color = HydraColors.Muted
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Reps count: e.g. 72 / 100 reps
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$reps",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = HydraColors.Navy
                )
                Text(
                    text = " / $goalReps reps",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = HydraColors.Muted,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Linear Progress Bar in vivid lime green
            val progressFraction = min(1f, reps.toFloat() / goalReps.coerceAtLeast(1))
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = HydraColors.Lime,
                trackColor = Color(0xFFDDECD0),
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Set History
            Text(
                text = "Set history",
                fontSize = 12.sp,
                color = HydraColors.Muted,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE8F6D6), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                val setsText = if (sets.isEmpty()) "No sets yet" else sets.joinToString(" + ")
                Text(
                    text = setsText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HydraColors.Navy,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // + Add Set button (prominent lime green button)
            Button(
                onClick = onAddSetClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("add_set_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HydraColors.Lime,
                    contentColor = HydraColors.Navy
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Add Set",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (hasSets) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = onUndoClick,
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("undo_set_button"),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = HydraColors.Muted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Undo last",
                            fontSize = 11.sp,
                            color = HydraColors.Muted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SupplementCard(
    modifier: Modifier = Modifier,
    name: String,
    subtitle: String,
    emoji: String,
    isDone: Boolean,
    backgroundColor: Color,
    badgeColor: Color,
    testTag: String,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = modifier
            .testTag(testTag)
            .clickable { onToggle(!isDone) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = HydraColors.Navy
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = HydraColors.Muted
                    )
                }

                // Checkbox status circle
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            color = if (isDone) Color(0xFFD7F5D4) else Color.White,
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDone) Color(0xFF67C95B) else Color(0xFFD6E8E9),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done",
                            tint = Color(0xFF279919),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Done / Pending pill button
            Box(
                modifier = Modifier
                    .background(
                        color = if (isDone) Color(0xFFE0F7D8) else Color.White,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isDone) "Done" else "Tap to check",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDone) Color(0xFF238416) else HydraColors.Muted
                )
            }
        }
    }
}
