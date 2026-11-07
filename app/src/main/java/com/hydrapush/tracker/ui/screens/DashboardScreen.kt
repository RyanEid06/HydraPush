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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.hydrapush.tracker.domain.ValidationLimits
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrapush.tracker.R
import com.hydrapush.tracker.data.repository.DayView
import com.hydrapush.tracker.data.repository.TrackerRepository
import com.hydrapush.tracker.domain.ProgressMath
import com.hydrapush.tracker.ui.components.BrandHeader
import com.hydrapush.tracker.ui.components.HistoryEntryRow
import com.hydrapush.tracker.ui.theme.HydraColors
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

private enum class RepHabit { PushUp, PullUp }
private val PullUpButtonGold = Color(0xFFF7D27A)

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
    var setDialog by remember { mutableStateOf<RepHabit?>(null) }
    var inputAmount by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }

    var showTodayLogs by remember { mutableStateOf(false) }
    var showWaterHistory by remember { mutableStateOf(false) }
    var showPullUpHistory by remember { mutableStateOf(false) }
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
        ProgressMath.currentActiveStreak(days.map { it.metrics }, localToday)
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
                date = localToday,
                onEditToday = { showTodayLogs = true }
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
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stacked = maxWidth < 358.dp || LocalDensity.current.fontScale > 1.15f
                    val water: @Composable (Modifier) -> Unit = { cardModifier ->
                        WaterCard(cardModifier, metrics.waterMl, metrics.waterGoalMl,
                            onAddQuick = { amount -> persist {
                                val id = repository.addWater(amount)
                                showUndo("Added $amount ml water") { repository.undoWater(id) }
                            } },
                            onCustomClick = { inputAmount = ""; dialogError = null; showWaterDialog = true },
                            onHistoryClick = { showWaterHistory = true })
                    }
                    val push: @Composable (Modifier) -> Unit = { cardModifier ->
                        RepCard(cardModifier, RepHabit.PushUp, metrics.pushUpReps, metrics.pushUpGoalReps,
                            today.sets.map { it.reps },
                            onAddSetClick = { inputAmount = ""; dialogError = null; setDialog = RepHabit.PushUp })
                    }
                    if (stacked) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        water(Modifier.fillMaxWidth()); push(Modifier.fillMaxWidth())
                    } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        water(Modifier.weight(1f).fillMaxHeight()); push(Modifier.weight(1f).fillMaxHeight())
                    }
                }
                Spacer(Modifier.height(14.dp))
                metrics.pullUpGoalReps?.let { goal ->
                    RepCard(
                        modifier = Modifier.fillMaxWidth(),
                        habit = RepHabit.PullUp,
                        reps = metrics.pullUpReps,
                        goalReps = goal,
                        sets = today.pullUpSets.map { it.reps },
                        onHistoryClick = { showPullUpHistory = true },
                        onAddSetClick = { inputAmount = ""; dialogError = null; setDialog = RepHabit.PullUp }
                    )
                    Spacer(Modifier.height(14.dp))
                }
                val showCreatine = today.snapshot.creatineRequired
                val showProtein = today.snapshot.proteinRequired
                if (showCreatine || showProtein) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val stacked = maxWidth < 358.dp || LocalDensity.current.fontScale > 1.15f
                        val habits: @Composable (Modifier) -> Unit = { habitModifier ->
                            if (showCreatine) SupplementCard(habitModifier, "Creatine", "5 g daily", R.drawable.ic_habit_creatine,
                                today.snapshot.creatineDone, HydraColors.SurfaceAqua, "creatine_card") {
                                persist { repository.toggleSupplement(creatine = true) }
                            }
                            if (showProtein) SupplementCard(habitModifier, "Protein Shake", "1 serving (≈ 25–30 g)", R.drawable.ic_habit_protein,
                                today.snapshot.proteinDone, HydraColors.SurfaceCoral, "protein_card") {
                                persist { repository.toggleSupplement(creatine = false) }
                            }
                        }
                        if (stacked || !(showCreatine && showProtein)) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            habits(Modifier.fillMaxWidth())
                        } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            habits(Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stacked = maxWidth < 358.dp || LocalDensity.current.fontScale > 1.15f
                    val streakCard: @Composable (Modifier) -> Unit = { cardModifier ->
                        Card(cardModifier.testTag("streak_card").clickable(onClick = onNavigateToStats),
                            shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = HydraColors.SurfaceCoral)) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 86.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalFireDepartment, null, Modifier.size(34.dp), tint = HydraColors.PerfectDayOrange)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("$streak-day Streak", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = HydraColors.Navy)
                                    Text(if (streak > 0) "Keep it going!" else "Start today!", fontSize = 12.sp, color = HydraColors.Muted)
                                }
                                Icon(Icons.Default.ChevronRight, "View Stats", Modifier.size(18.dp), tint = HydraColors.Muted)
                            }
                        }
                    }
                    val completion: @Composable (Modifier) -> Unit = { cardModifier ->
                        Row(cardModifier.testTag("daily_completion_card").heightIn(min = 86.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Daily Completion", fontSize = 12.sp, color = HydraColors.Muted)
                                Text("${(metrics.progress * 100).toInt()}%", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = HydraColors.DeepTeal)
                            }
                            Box(Modifier.size(58.dp).padding(4.dp), contentAlignment = Alignment.Center) {
                                ProgressRing(metrics.progress, HydraColors.Lime, Modifier.fillMaxSize(), 7f)
                                Icon(Icons.Default.EmojiEvents, "Daily progress", Modifier.size(26.dp), tint = HydraColors.PrimaryTeal)
                            }
                        }
                    }
                    if (stacked) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        streakCard(Modifier.fillMaxWidth()); completion(Modifier.fillMaxWidth())
                    } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        streakCard(Modifier.weight(1.05f))
                        VerticalDivider(Modifier.padding(start = 12.dp).height(60.dp), color = HydraColors.Stroke)
                        completion(Modifier.weight(1f))
                    }
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

    if (showWaterHistory && today != null) {
        AlertDialog(
            onDismissRequest = { showWaterHistory = false },
            containerColor = HydraColors.SurfaceBlue,
            title = { Text("Water history") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).testTag("water_history_list")) {
                    if (today.waters.isEmpty()) item { Text("No water logged today.") }
                    items(today.waters, key = { it.id }) {
                        HistoryEntryRow("+${it.amountMl} ml", it.createdAtEpochMs)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showWaterHistory = false }) { Text("Done") } }
        )
    }

    if (showPullUpHistory && today != null) {
        AlertDialog(
            onDismissRequest = { showPullUpHistory = false },
            containerColor = HydraColors.SurfaceGold,
            title = { Text("Pull-up set history") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).testTag("pullup_history_list")) {
                    if (today.pullUpSets.isEmpty()) item { Text("No pull-up sets logged today.") }
                    items(today.pullUpSets, key = { it.id }) { set ->
                        HistoryEntryRow("+${set.reps} reps", set.createdAtEpochMs) {
                            persist { repository.removePullUpSet(set.id) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPullUpHistory = false }) { Text("Done") } }
        )
    }

    if (showTodayLogs && today != null) {
        AlertDialog(
            onDismissRequest = { showTodayLogs = false },
            title = { Text("Today's logs") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).testTag("today_logs_list")) {
                    if (today.waters.isEmpty() && today.sets.isEmpty() && today.pullUpSets.isEmpty()) {
                        item { Text("No entries yet today.") }
                    }
                    items(today.waters, key = { "water_${it.id}" }) { entry ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Water · ${entry.amountMl} ml", Modifier.weight(1f))
                            TextButton(onClick = { persist { repository.undoWater(entry.id) } }, modifier = Modifier.testTag("remove_water_${entry.id}")) { Text("Remove") }
                        }
                    }
                    items(today.sets, key = { "set_${it.id}" }) { entry ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Push-ups · ${entry.reps} reps", Modifier.weight(1f))
                            TextButton(onClick = { persist { repository.removeSet(entry.id) } }, modifier = Modifier.testTag("remove_set_${entry.id}")) { Text("Remove") }
                        }
                    }
                    items(today.pullUpSets, key = { "pullup_${it.id}" }) { set ->
                        HistoryEntryRow("Pull-ups · ${set.reps} reps", set.createdAtEpochMs) {
                            persist { repository.removePullUpSet(set.id) }
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
                        if (amount == null || amount !in ValidationLimits.WATER_ENTRY) {
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

    setDialog?.let { habit ->
        val pullUp = habit == RepHabit.PullUp
        val tag = if (pullUp) "add_pullup_set" else "add_set"
        val limit = if (pullUp) ValidationLimits.PULL_UP_SET else ValidationLimits.PUSH_UP_SET
        val presets = if (pullUp) listOf(1, 2, 3, 5, 10) else listOf(10, 15, 20, 25, 30)
        AlertDialog(
            onDismissRequest = { setDialog = null },
            title = {
                Text(
                    text = if (pullUp) "Add Pull-up Set" else "Add Push-up Set",
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
                            .testTag("${tag}_input")
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
                        presets.forEach { preset ->
                            SuggestionChip(
                                onClick = { inputAmount = preset.toString(); dialogError = null },
                                modifier = if (pullUp) Modifier.testTag("add_pullup_$preset") else Modifier,
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
                        if (reps == null || reps !in limit) {
                            dialogError = "Enter reps between 1 and 1,000"
                        } else {
                            persist {
                                if (pullUp) {
                                    val setId = repository.addPullUpSet(reps)
                                    showUndo("Added $reps pull-up reps") { repository.removePullUpSet(setId) }
                                } else {
                                    val setId = repository.addSet(reps)
                                    showUndo("Added $reps reps") { repository.removeSet(setId) }
                                }
                            }
                            setDialog = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pullUp) PullUpButtonGold else HydraColors.Lime,
                        contentColor = HydraColors.Navy
                    ),
                    modifier = Modifier.testTag(if (pullUp) "save_pullup_set_button" else "save_set_button")
                ) {
                    Text("Save Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { setDialog = null }) {
                    Text("Cancel", color = HydraColors.Muted)
                }
            }
        )
    }
}

@Composable
private fun ProgressRing(progress: Float, color: Color, modifier: Modifier, strokeDp: Float = 10f) {
    Canvas(modifier) {
        val stroke = strokeDp.dp.toPx()
        val diameter = size.minDimension - stroke
        val origin = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        drawArc(Color(0xFFDCEEF0), -90f, 360f, false, origin, Size(diameter, diameter), style = Stroke(stroke))
        drawArc(Brush.sweepGradient(listOf(color, HydraColors.PrimaryTeal, color)), -90f, progress.coerceIn(0f, 1f) * 360f,
            false, origin, Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun HabitHeading(icon: Int, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(icon), null, Modifier.size(28.dp))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HydraColors.Navy)
            Text(subtitle, fontSize = 11.sp, color = HydraColors.Muted)
        }
    }
}

@Composable
private fun WaterCard(
    modifier: Modifier,
    waterMl: Int,
    goalMl: Int,
    onAddQuick: (Int) -> Unit,
    onCustomClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Card(modifier.border(1.dp, Color(0xFFCAF0EF), RoundedCornerShape(22.dp)).testTag("water_card"),
        shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = HydraColors.SurfaceAqua)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            HabitHeading(R.drawable.ic_habit_water, "Water", "Stay hydrated")
            Spacer(Modifier.height(14.dp))
            val progress = waterMl.toFloat() / goalMl.coerceAtLeast(1)
            Box(
                Modifier.size(142.dp).testTag("water_history_button")
                    .clickable(role = Role.Button, onClickLabel = "View water history", onClick = onHistoryClick),
                contentAlignment = Alignment.Center
            ) {
                ProgressRing(progress, HydraColors.Aqua, Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.ic_hydra_drop), null, Modifier.size(22.dp))
                    Text("$waterMl ml", fontSize = if (waterMl > 99999) 16.sp else 21.sp, fontWeight = FontWeight.ExtraBold, color = HydraColors.DeepTeal)
                    Text("of $goalMl ml", fontSize = 12.sp, color = HydraColors.Muted)
                    Row(
                        Modifier.background(Color(0xFFCEF7EF), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${(progress * 100).toInt()}%", fontSize = 13.sp, color = HydraColors.DeepTeal, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.History, null, Modifier.size(14.dp), tint = HydraColors.DeepTeal)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(150, 250, 500).forEach { amount ->
                    OutlinedButton(onClick = { onAddQuick(amount) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("add_water_$amount"),
                        shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(0.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8EE7E3)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFDDFBF9), contentColor = HydraColors.DeepTeal)) {
                        Text("+$amount", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCustomClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("custom_water_button"),
                shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(horizontal = 4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8EE7E3)), colors = ButtonDefaults.outlinedButtonColors(contentColor = HydraColors.DeepTeal)) {
                Icon(Icons.Default.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                Text("Custom amount", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RepCard(
    modifier: Modifier,
    habit: RepHabit,
    reps: Int,
    goalReps: Int,
    sets: List<Int>,
    onHistoryClick: (() -> Unit)? = null,
    onAddSetClick: () -> Unit
) {
    val pullUp = habit == RepHabit.PullUp
    val tag = if (pullUp) "pullup" else "pushup"
    val track = if (pullUp) Color(0xFFF4E0B2) else Color(0xFFDFEBD9)
    Card(modifier.border(1.dp, if (pullUp) track else Color(0xFFE0F1CE), RoundedCornerShape(22.dp)).testTag("${tag}_card"),
        shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = if (pullUp) HydraColors.SurfaceGold else HydraColors.SurfaceLime)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 340.dp).padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            HabitHeading(if (pullUp) R.drawable.ic_habit_pullup else R.drawable.ic_habit_pushup,
                if (pullUp) "Pull-ups" else "Push-ups", "Build strength")
            Column {
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = if (reps > 999) 28.sp else 38.sp, fontWeight = FontWeight.ExtraBold, color = HydraColors.DeepTeal)) { append("$reps") }
                    withStyle(SpanStyle(fontSize = 16.sp, color = HydraColors.Muted)) { append(" / $goalReps reps") }
                }, modifier = Modifier.testTag("${tag}_total"))
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { (reps.toFloat() / goalReps.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp), color = if (pullUp) HydraColors.PullUpGold else HydraColors.Lime,
                    trackColor = track, strokeCap = StrokeCap.Round,
                    drawStopIndicator = {})
                Spacer(Modifier.height(18.dp))
                Text("Set history", fontSize = 12.sp, color = HydraColors.Muted)
                Spacer(Modifier.height(6.dp))
                val visible = sets.take(4)
                val history = if (sets.isEmpty()) "No sets yet" else visible.joinToString(" + ") + if (sets.size > 4) " · +${sets.size - 4} sets" else ""
                val historyShape = RoundedCornerShape(24.dp)
                val historyPill = Modifier.fillMaxWidth()
                    .background(if (pullUp) Color(0xFFFAE8B9) else Color(0xFFE5F8D3), historyShape)
                if (onHistoryClick == null) {
                    Text(history, historyPill.padding(10.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = HydraColors.DeepTeal)
                } else {
                    Row(historyPill.heightIn(min = 48.dp).testTag("pullup_history_button")
                        .clickable(role = Role.Button, onClick = onHistoryClick).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(history, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = HydraColors.DeepTeal)
                        Icon(Icons.Default.History, "View pull-up set history", Modifier.size(20.dp), tint = HydraColors.DeepTeal)
                    }
                }
            }
            Button(onClick = onAddSetClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .testTag(if (pullUp) "add_pullup_set_button" else "add_set_button"),
                shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(
                    containerColor = if (pullUp) PullUpButtonGold else HydraColors.LimeBright, contentColor = HydraColors.DeepTeal)) {
                Icon(Icons.Default.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(4.dp))
                Text("Add Set", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SupplementCard(modifier: Modifier, name: String, subtitle: String, iconRes: Int, isDone: Boolean,
    backgroundColor: Color, testTag: String, onToggle: () -> Unit) {
    Card(modifier.testTag(testTag).clickable(onClick = onToggle), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 94.dp).padding(10.dp), verticalAlignment = Alignment.Top) {
            Image(painterResource(iconRes), null, Modifier.padding(top = 4.dp).size(24.dp))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = HydraColors.Navy)
                Text(subtitle, fontSize = 11.sp, color = HydraColors.Muted)
                Spacer(Modifier.height(5.dp))
                Text(if (isDone) "Done" else "Mark done", Modifier.background(Color(0xFFDFF7C6), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 11.sp, color = HydraColors.DeepTeal, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.size(22.dp).background(if (isDone) Color(0xFFDFF7C6) else Color.White, CircleShape), contentAlignment = Alignment.Center) {
                if (isDone) Icon(Icons.Default.Check, "Done", Modifier.size(18.dp), tint = Color(0xFF40980D))
            }
        }
    }
}
