package com.hydrapush.tracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrapush.tracker.R
import com.hydrapush.tracker.data.repository.DayView
import com.hydrapush.tracker.data.repository.TrackerRepository
import com.hydrapush.tracker.domain.ProgressMath
import com.hydrapush.tracker.ui.components.BrandHeader
import com.hydrapush.tracker.ui.theme.HydraColors
import java.time.LocalDate
import kotlin.math.min
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    repository: TrackerRepository,
    today: DayView?,
    localToday: LocalDate,
    days: List<DayView>,
    onNavigateToStats: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showWaterDialog by remember { mutableStateOf(false) }
    var showPushUpDialog by remember { mutableStateOf(false) }
    var inputAmount by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }

    var showTodayLogs by remember { mutableStateOf(false) }
    var undoMessage by remember { mutableStateOf<Job?>(null) }
    fun showUndo(message: String, undo: suspend () -> Unit) {
        undoMessage?.cancel()
        snackbarHostState.currentSnackbarData?.dismiss()
        undoMessage = scope.launch {
            if (snackbarHostState.showSnackbar(message, "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                try { undo() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    android.util.Log.e("HydraPush", "Undo failed", error)
                    snackbarHostState.showSnackbar("Could not undo the entry. Please try Today's logs.")
                }
            }
        }
    }
    fun persist(action: suspend () -> Unit) {
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.e("HydraPush", "Entry update failed", error)
                undoMessage?.cancel()
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar("Could not save the change. Please try again.")
            }
        }
    }
    val metrics = today?.metrics
    val streak = remember(days, localToday) {
        ProgressMath.currentPerfectStreak(days.map { it.metrics }, localToday)
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Water Card
                    WaterCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        waterMl = metrics.waterMl,
                        goalMl = metrics.waterGoalMl,
                        onAddQuick = { amount ->
                            persist {
                                val entryId = repository.addWater(amount)
                                showUndo("Added $amount ml water") { repository.undoWater(entryId) }
                            }
                        },
                        onCustomClick = {
                            inputAmount = ""
                            dialogError = null
                            showWaterDialog = true
                        }
                    )

                    // 2. Push-ups Card
                    PushUpCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        reps = metrics.pushUpReps,
                        goalReps = metrics.pushUpGoalReps,
                        sets = today.sets.map { it.reps },
                        onAddSetClick = {
                            inputAmount = ""
                            dialogError = null
                            showPushUpDialog = true
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Optional Active Habits Row (Creatine & Protein Shake)
                val showCreatine = today.snapshot.creatineRequired
                val showProtein = today.snapshot.proteinRequired

                if (showCreatine || showProtein) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val density = LocalDensity.current
                    var contentHeight by remember(maxWidth, density.density, density.fontScale, showCreatine, showProtein) { mutableIntStateOf(0) }
                    val minimumHeight = with(density) { contentHeight.toDp() }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (showCreatine) {
                            SupplementCard(
                                modifier = Modifier.weight(1f).heightIn(min = minimumHeight),
                                onContentHeight = { contentHeight = maxOf(contentHeight, it) },
                                name = "Creatine",
                                subtitle = "5 g daily",
                                iconRes = R.drawable.ic_habit_creatine,
                                isDone = today.snapshot.creatineDone,
                                backgroundColor = HydraColors.SurfaceAqua,
                                testTag = "creatine_card",
                                onToggle = { done ->
                                    persist { repository.toggleSupplement(creatine = true) }
                                }
                            )
                        }
                        if (showProtein) {
                            SupplementCard(
                                modifier = Modifier.weight(1f).heightIn(min = minimumHeight),
                                onContentHeight = { contentHeight = maxOf(contentHeight, it) },
                                name = "Protein Shake",
                                subtitle = "1 serving (≈ 25–30 g)",
                                iconRes = R.drawable.ic_habit_protein,
                                isDone = today.snapshot.proteinDone,
                                backgroundColor = HydraColors.SurfaceCoral,
                                testTag = "protein_card",
                                onToggle = { done ->
                                    persist { repository.toggleSupplement(creatine = false) }
                                }
                            )
                        }
                    }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Bottom Performance Summary Row: Streak Card + Daily Completion Card
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Streak Card
                    Card(
                        modifier = Modifier
                            .weight(1f).fillMaxHeight()
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
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFFFDFD3), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = "Streak",
                                    tint = HydraColors.PerfectDayOrange,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
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
                            .weight(1f).fillMaxHeight()
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
                            Column(Modifier.weight(1f)) {
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
                                modifier = Modifier.size(36.dp),
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
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = "Trophy",
                                    tint = Color(0xFFD49E00),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (today != null) {
                TextButton(onClick = { showTodayLogs = true }, modifier = Modifier.fillMaxWidth().testTag("today_logs_button")) {
                    Text("Today's logs · correct entries", color = HydraColors.DeepTeal)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    if (showTodayLogs && today != null) {
        AlertDialog(
            onDismissRequest = { showTodayLogs = false },
            title = { Text("Today's logs") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    if (today.waters.isEmpty() && today.sets.isEmpty()) item { Text("No entries yet today.") }
                    items(today.waters, key = { "water_${it.id}" }) { entry ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Water · ${entry.amountMl} ml", Modifier.weight(1f))
                            TextButton(onClick = { persist { repository.undoWater(entry.id) } }) { Text("Remove") }
                        }
                    }
                    items(today.sets, key = { "set_${it.id}" }) { entry ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Push-ups · ${entry.reps} reps", Modifier.weight(1f))
                            TextButton(onClick = { persist { repository.removeSet(entry.id) } }) { Text("Remove") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTodayLogs = false }) { Text("Done") } }
        )
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
                    FlowRow(
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
                            persist {
                                val entryId = repository.addWater(amount)
                                showUndo("Added $amount ml water") { repository.undoWater(entryId) }
                            }
                            showWaterDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HydraColors.PrimaryTeal, contentColor = HydraColors.Navy),
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
                    FlowRow(
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
                            persist {
                                val setId = repository.addSet(reps)
                                showUndo("Added $reps reps") { repository.removeSet(setId) }
                            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaterCard(
    modifier: Modifier = Modifier,
    waterMl: Int,
    goalMl: Int,
    onAddQuick: (Int) -> Unit,
    onCustomClick: () -> Unit
) {
    Card(
        modifier = modifier
            .border(1.dp, Color(0xFFD6F3F1), RoundedCornerShape(22.dp))
            .testTag("water_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HydraColors.SurfaceAqua),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Low-contrast decorative accent lines
            Canvas(modifier = Modifier.matchParentSize()) {
                val path = Path().apply {
                    moveTo(size.width * 0.2f, size.height)
                    cubicTo(
                        size.width * 0.5f, size.height * 0.7f,
                        size.width * 0.8f, size.height * 0.9f,
                        size.width, size.height * 0.6f
                    )
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path, color = Color(0xFF0CC5BB).copy(alpha = 0.05f))
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row: Drop Icon + Title + Subtitle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_habit_water),
                        contentDescription = "Water Icon",
                        modifier = Modifier.size(32.dp)
                    )
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
                val percent = if (goalMl > 0) (waterMl.toFloat() / goalMl * 100).toInt() else 0

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
                        Image(
                            painter = painterResource(R.drawable.ic_hydra_drop),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    maxItemsInEachRow = 2
                ) {
                    listOf(150, 250, 500).forEach { amount ->
                        Button(
                            onClick = { onAddQuick(amount) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Custom amount button
                OutlinedButton(
                    onClick = onCustomClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("custom_water_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HydraColors.DeepTeal),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBCE8E5)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Custom amount",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
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
    onAddSetClick: () -> Unit
) {
    Card(
        modifier = modifier
            .border(1.dp, Color(0xFFE2F5CD), RoundedCornerShape(22.dp))
            .testTag("pushup_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HydraColors.SurfaceLime),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Low-contrast decorative accent lines
            Canvas(modifier = Modifier.matchParentSize()) {
                val path = Path().apply {
                    moveTo(0f, size.height * 0.7f)
                    cubicTo(
                        size.width * 0.3f, size.height * 0.85f,
                        size.width * 0.7f, size.height * 0.65f,
                        size.width, size.height * 0.8f
                    )
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, color = Color(0xFF8BD70F).copy(alpha = 0.05f))
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp)
            ) {
                // Header Row: Runner Silhouette + Title + Subtitle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_habit_pushup),
                        contentDescription = "Push-ups Icon",
                        modifier = Modifier.size(32.dp)
                    )
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
                Column(
                    horizontalAlignment = Alignment.Start,
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
                        .heightIn(min = 34.dp)
                        .background(Color(0xFFE8F6D6), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val setsText = if (sets.isEmpty()) "No sets yet" else
                        sets.take(3).joinToString(", ") + if (sets.size > 3) " + ${sets.size - 3} more" else ""
                    Text(
                        text = setsText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HydraColors.Navy,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Spacer(Modifier.weight(1f))
                // + Add Set button (prominent lime green button)
                Button(
                    onClick = onAddSetClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
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
                }
            }
        }
    }

@Composable
private fun SupplementCard(
    modifier: Modifier = Modifier,
    onContentHeight: (Int) -> Unit,
    name: String,
    subtitle: String,
    iconRes: Int,
    isDone: Boolean,
    backgroundColor: Color,
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
        Column(Modifier.fillMaxWidth().onSizeChanged { onContentHeight(it.height) }.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(iconRes), null, Modifier.size(30.dp), contentScale = ContentScale.Fit)
                Box(Modifier.size(28.dp).background(if (isDone) Color(0xFFD7F5D4) else Color.White, CircleShape)
                    .border(1.dp, HydraColors.Stroke, CircleShape), contentAlignment = Alignment.Center) {
                    if (isDone) Icon(Icons.Default.Check, "Done", tint = HydraColors.DeepTeal, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HydraColors.Navy)
            Text(subtitle, fontSize = 12.sp, color = HydraColors.Muted)
            Text(if (isDone) "Done ✓" else "Mark done", fontSize = 12.sp, color = HydraColors.DeepTeal, fontWeight = FontWeight.Bold)
        }
    }
}
