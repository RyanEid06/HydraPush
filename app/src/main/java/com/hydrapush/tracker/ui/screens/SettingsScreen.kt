package com.hydrapush.tracker.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hydrapush.tracker.domain.ValidationLimits
import com.hydrapush.tracker.BuildConfig
import com.hydrapush.tracker.R
import com.hydrapush.tracker.data.preferences.TrackerSettings
import com.hydrapush.tracker.data.repository.TrackerRepository
import com.hydrapush.tracker.reminders.ReminderScheduler
import com.hydrapush.tracker.ui.components.BrandHeader
import com.hydrapush.tracker.ui.components.SectionCard
import com.hydrapush.tracker.ui.theme.HydraColors
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    repo: TrackerRepository,
    settings: TrackerSettings
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var goalDialogTarget by remember { mutableStateOf<String?>(null) }
    var goalInput by remember { mutableStateOf("") }
    var goalError by remember { mutableStateOf<String?>(null) }

    var showTimeDialogTarget by remember { mutableStateOf<String?>(null) }
    var customHour by remember { mutableIntStateOf(12) }
    var customMinute by remember { mutableIntStateOf(0) }

    var showAboutDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingReminderEnableTarget by remember { mutableStateOf<String?>(null) }
    var notificationsAllowed by remember { mutableStateOf(ReminderScheduler.notificationsAllowed(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsAllowed = ReminderScheduler.notificationsAllowed(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android 13+ notification permission. A reminder is persisted as ON only after permission exists.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationsAllowed = isGranted || ReminderScheduler.notificationsAllowed(context)
        val target = pendingReminderEnableTarget
        pendingReminderEnableTarget = null
        if (notificationsAllowed && target != null) {
            scope.launch { repo.updateSettings { it.withReminderEnabled(target, true) } }
        } else if (!notificationsAllowed) {
            scope.launch {
                snackbarHostState.showSnackbar("Notification permission is required to enable reminders.")
            }
        }
    }

    // Keep stored reminder state honest if permission was revoked outside the app.
    LaunchedEffect(
        notificationsAllowed,
        settings.waterReminderEnabled,
        settings.pushUpReminderEnabled,
        settings.creatineReminderEnabled,
        settings.proteinReminderEnabled
    ) {
        if (!notificationsAllowed && (
                settings.waterReminderEnabled || settings.pushUpReminderEnabled ||
                    settings.creatineReminderEnabled || settings.proteinReminderEnabled
            )
        ) {
            repo.updateSettings {
                it.copy(
                    waterReminderEnabled = false,
                    pushUpReminderEnabled = false,
                    creatineReminderEnabled = false,
                    proteinReminderEnabled = false
                )
            }
        }
    }

    // SAF Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = repo.exportBackup(uri)
                if (result.isSuccess) {
                    snackbarHostState.showSnackbar("Backup exported successfully!")
                } else {
                    android.util.Log.e("HydraPush", "Backup export failed", result.exceptionOrNull())
                    snackbarHostState.showSnackbar("Could not export the backup. Choose a writable location and try again.")
                }
            }
        }
    }

    // SAF Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImportUri = uri
            showImportConfirmDialog = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("settings_screen")
        ) {
            BrandHeader(
                subtitle = "Settings & preferences",
                showDateBar = false
            )

            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = HydraColors.Navy,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = HydraColors.Navy
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Daily Goals Group
            SectionCard(modifier = Modifier.fillMaxWidth().testTag("daily_goals_group")) {
                GroupHeader(icon = Icons.Default.TrackChanges, title = "Daily Goals")
                Spacer(modifier = Modifier.height(10.dp))

                SettingRow(
                    iconRes = R.drawable.ic_habit_water,
                    title = "Water Goal",
                    value = "${String.format(java.util.Locale.getDefault(), "%,d", settings.waterGoalMl)} ml",
                    onClick = {
                        goalInput = settings.waterGoalMl.toString()
                        goalError = null
                        goalDialogTarget = "water"
                    }
                )

                HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(start = 50.dp, top = 4.dp, bottom = 4.dp))

                SettingRow(
                    iconRes = R.drawable.ic_habit_pushup,
                    title = "Push-up Goal",
                    value = "${settings.pushUpGoalReps} reps",
                    onClick = {
                        goalInput = settings.pushUpGoalReps.toString()
                        goalError = null
                        goalDialogTarget = "pushup"
                    }
                )

                HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(start = 50.dp, top = 4.dp, bottom = 4.dp))
                SettingRow(
                    iconRes = R.drawable.ic_habit_pullup,
                    title = "Pull-up Goal",
                    value = "${settings.pullUpGoalReps} reps",
                    onClick = {
                        goalInput = settings.pullUpGoalReps.toString()
                        goalError = null
                        goalDialogTarget = "pullup"
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Active Habits Group
            SectionCard(modifier = Modifier.fillMaxWidth().testTag("active_habits_group")) {
                GroupHeader(icon = Icons.Default.FitnessCenter, title = "Active Habits")
                Spacer(modifier = Modifier.height(10.dp))

                // Water (Permanently On)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingIcon(iconRes = R.drawable.ic_habit_water)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Water",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = HydraColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = HydraColors.Muted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Always active",
                            fontSize = 12.sp,
                            color = HydraColors.Muted
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(start = 50.dp, top = 4.dp, bottom = 4.dp))

                for ((habit, icon) in listOf(
                    "Push-ups" to R.drawable.ic_habit_pushup,
                    "Pull-ups" to R.drawable.ic_habit_pullup
                )) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingIcon(iconRes = icon)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = habit,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = HydraColors.Navy,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = HydraColors.Muted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Always active",
                                fontSize = 12.sp,
                                color = HydraColors.Muted
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(start = 50.dp, top = 4.dp, bottom = 4.dp))
                }

                // Creatine (Toggleable)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingIcon(iconRes = R.drawable.ic_habit_creatine)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Creatine",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = HydraColors.Navy
                        )
                        Text(
                            text = "5 g daily dose",
                            fontSize = 11.sp,
                            color = HydraColors.Muted
                        )
                    }
                    Switch(
                        checked = settings.creatineEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                repo.updateSettings {
                                    it.copy(
                                        creatineEnabled = enabled,
                                        creatineReminderEnabled = if (enabled) it.creatineReminderEnabled else false
                                    )
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = HydraColors.PrimaryTeal
                        ),
                        modifier = Modifier.testTag("switch_creatine")
                    )
                }

                HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(start = 50.dp, top = 4.dp, bottom = 4.dp))

                // Protein Shake (Toggleable)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingIcon(iconRes = R.drawable.ic_habit_protein)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Protein Shake",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = HydraColors.Navy
                        )
                        Text(
                            text = "Daily post-workout protein",
                            fontSize = 11.sp,
                            color = HydraColors.Muted
                        )
                    }
                    Switch(
                        checked = settings.proteinEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                repo.updateSettings {
                                    it.copy(
                                        proteinEnabled = enabled,
                                        proteinReminderEnabled = if (enabled) it.proteinReminderEnabled else false
                                    )
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = HydraColors.ProteinCoral
                        ),
                        modifier = Modifier.testTag("switch_protein")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Reminders Group
            SectionCard(modifier = Modifier.fillMaxWidth().testTag("reminders_group")) {
                GroupHeader(icon = Icons.Default.Notifications, title = "Reminders")
                Spacer(modifier = Modifier.height(10.dp))

                // Water Reminder
                ReminderRow(
                    iconRes = R.drawable.ic_habit_water,
                    title = "Water Reminder",
                    subtitle = "Every ${settings.waterReminderIntervalHours} hours" + if (notificationsAllowed) "" else " · Permission required",
                    enabled = settings.waterReminderEnabled && notificationsAllowed,
                    onToggle = { isEnabled ->
                        handleReminderToggle(
                            target = "water",
                            enable = isEnabled,
                            context = context,
                            launcher = notificationPermissionLauncher,
                            permissionAlreadyGranted = notificationsAllowed,
                            onPermissionRequest = { pendingReminderEnableTarget = it },
                            onPersist = { target, enabled ->
                                scope.launch { repo.updateSettings { it.withReminderEnabled(target, enabled) } }
                            }
                        )
                    },
                    onClickSchedule = {
                        customHour = settings.waterReminderIntervalHours
                        showTimeDialogTarget = "water_interval"
                    }
                )

                HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(start = 50.dp, top = 4.dp, bottom = 4.dp))

                for (isPullUp in listOf(false, true)) {
                    val time = if (isPullUp) settings.pullUpReminderTimeMinutes else settings.pushUpReminderTimeMinutes
                    val enabled = if (isPullUp) settings.pullUpReminderEnabled else settings.pushUpReminderEnabled
                    val habit = if (isPullUp) "pullup" else "pushup"
                    ReminderRow(
                        iconRes = if (isPullUp) R.drawable.ic_habit_pullup else R.drawable.ic_habit_pushup,
                        title = if (isPullUp) "Pull-up Reminder" else "Push-up Reminder",
                        subtitle = formatMinutesToTime(context, time) + if (notificationsAllowed) "" else " · Permission required",
                        enabled = enabled && (isPullUp || notificationsAllowed),
                        onToggle = { isEnabled ->
                            handleReminderToggle(
                                target = habit, enable = isEnabled, context = context,
                                launcher = notificationPermissionLauncher,
                                permissionAlreadyGranted = notificationsAllowed,
                                onPermissionRequest = { pendingReminderEnableTarget = it },
                                onPersist = { target, enabled ->
                                    scope.launch { repo.updateSettings { it.withReminderEnabled(target, enabled) } }
                                }
                            )
                        },
                        onClickSchedule = {
                            customHour = time / 60
                            customMinute = time % 60
                            showTimeDialogTarget = "${habit}_time"
                        }
                    )

                    HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(start = 50.dp, top = 4.dp, bottom = 4.dp))
                }

                // Creatine Reminder
                ReminderRow(
                    iconRes = R.drawable.ic_habit_creatine,
                    title = "Creatine Reminder",
                    subtitle = when {
                        !settings.creatineEnabled -> "Habit deactivated"
                        else -> formatMinutesToTime(context, settings.creatineReminderTimeMinutes) + if (notificationsAllowed) "" else " · Permission required"
                    },
                    enabled = settings.creatineEnabled && settings.creatineReminderEnabled && notificationsAllowed,
                    isHabitActive = settings.creatineEnabled,
                    onToggle = { isEnabled ->
                        handleReminderToggle(
                            target = "creatine", enable = isEnabled, context = context,
                            launcher = notificationPermissionLauncher,
                            permissionAlreadyGranted = notificationsAllowed,
                            onPermissionRequest = { pendingReminderEnableTarget = it },
                            onPersist = { target, enabled ->
                                scope.launch { repo.updateSettings { it.withReminderEnabled(target, enabled) } }
                            }
                        )
                    },
                    onClickSchedule = {
                        if (settings.creatineEnabled) {
                            customHour = settings.creatineReminderTimeMinutes / 60
                            customMinute = settings.creatineReminderTimeMinutes % 60
                            showTimeDialogTarget = "creatine_time"
                        }
                    }
                )

                HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(start = 50.dp, top = 4.dp, bottom = 4.dp))

                // Protein Shake Reminder
                ReminderRow(
                    iconRes = R.drawable.ic_habit_protein,
                    title = "Protein Shake Reminder",
                    subtitle = when {
                        !settings.proteinEnabled -> "Habit deactivated"
                        else -> formatMinutesToTime(context, settings.proteinReminderTimeMinutes) + if (notificationsAllowed) "" else " · Permission required"
                    },
                    enabled = settings.proteinEnabled && settings.proteinReminderEnabled && notificationsAllowed,
                    isHabitActive = settings.proteinEnabled,
                    onToggle = { isEnabled ->
                        handleReminderToggle(
                            target = "protein", enable = isEnabled, context = context,
                            launcher = notificationPermissionLauncher,
                            permissionAlreadyGranted = notificationsAllowed,
                            onPermissionRequest = { pendingReminderEnableTarget = it },
                            onPersist = { target, enabled ->
                                scope.launch { repo.updateSettings { it.withReminderEnabled(target, enabled) } }
                            }
                        )
                    },
                    onClickSchedule = {
                        if (settings.proteinEnabled) {
                            customHour = settings.proteinReminderTimeMinutes / 60
                            customMinute = settings.proteinReminderTimeMinutes % 60
                            showTimeDialogTarget = "protein_time"
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Data & Backup Group
            SectionCard(modifier = Modifier.fillMaxWidth().testTag("data_backup_group")) {
                GroupHeader(icon = Icons.Default.Storage, title = "Data & Backup")
                Spacer(modifier = Modifier.height(10.dp))

                Box(Modifier.testTag("export_backup_button")) {
                    SettingRow(icon = Icons.Default.Upload, title = "Export Backup", value = "", onClick = {
                        exportLauncher.launch("hydrapush_backup_${LocalDate.now()}.json")
                    })
                }
                HorizontalDivider(color = HydraColors.Stroke, modifier = Modifier.padding(start = 50.dp))
                Box(Modifier.testTag("import_backup_button")) {
                    SettingRow(icon = Icons.Default.Download, title = "Import Backup", value = "", onClick = {
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    })
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. About Group
            SectionCard(modifier = Modifier.fillMaxWidth().testTag("about_group")) {
                GroupHeader(icon = Icons.Default.Info, title = "About")
                Spacer(modifier = Modifier.height(10.dp))

                SettingRow(
                    icon = Icons.Default.PhoneAndroid,
                    title = "App Version",
                    value = "v${BuildConfig.VERSION_NAME}",
                    onClick = { showAboutDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    }

    // Goal Edit Dialog
    goalDialogTarget?.let { target ->
        val isWater = target == "water"
        val (habitName, limit) = when (target) {
            "water" -> "Water" to ValidationLimits.WATER_GOAL
            "pullup" -> "Pull-up" to ValidationLimits.PULL_UP_GOAL
            else -> "Push-up" to ValidationLimits.PUSH_UP_GOAL
        }
        val maxLimit = limit.last
        AlertDialog(
            onDismissRequest = { goalDialogTarget = null },
            title = {
                Text(
                    text = "Edit Daily $habitName Goal",
                    fontWeight = FontWeight.Bold,
                    color = HydraColors.Navy
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isWater) "Enter daily target in ml (1 – $maxLimit ml):" else "Enter daily target reps (1 – $maxLimit reps):",
                        fontSize = 13.sp,
                        color = HydraColors.Muted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = {
                            goalInput = it.filter(Char::isDigit)
                            goalError = null
                        },
                        label = { Text(if (isWater) "Water (ml)" else "${habitName}s (reps)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("edit_goal_input")
                    )
                    if (goalError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(goalError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val value = goalInput.toIntOrNull()
                        if (value == null || value !in limit) {
                            goalError = "Enter an amount between 1 and $maxLimit"
                        } else {
                            scope.launch {
                                repo.updateSettings {
                                    when (target) {
                                        "water" -> it.copy(waterGoalMl = value)
                                        "pullup" -> it.copy(pullUpGoalReps = value)
                                        else -> it.copy(pushUpGoalReps = value)
                                    }
                                }
                            }
                            goalDialogTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (target) {
                            "water" -> HydraColors.PrimaryTeal
                            "pullup" -> HydraColors.SurfaceGold
                            else -> HydraColors.Lime
                        },
                        contentColor = HydraColors.Navy
                    ),
                    modifier = Modifier.testTag("save_goal_button")
                ) {
                    Text("Save Goal")
                }
            },
            dismissButton = {
                TextButton(onClick = { goalDialogTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Time / Interval picker dialog for reminders
    showTimeDialogTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { showTimeDialogTarget = null },
            title = {
                Text(
                    text = when (target) {
                        "water_interval" -> "Water Reminder Interval"
                        "pushup_time" -> "Push-up Reminder Time"
                        "pullup_time" -> "Pull-up Reminder Time"
                        "creatine_time" -> "Creatine Reminder Time"
                        else -> "Protein Reminder Time"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (target == "water_interval") {
                    Column {
                        Text("Remind me every:", fontSize = 13.sp, color = HydraColors.Muted)
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf(1, 2, 3, 4, 6, 8).forEach { hours ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        customHour = hours
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = customHour == hours,
                                    onClick = {
                                        customHour = hours
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Every $hours hour${if (hours > 1) "s" else ""}", fontSize = 14.sp)
                            }
                        }
                    }
                } else {
                    Column {
                        Text("Set reminder time:", fontSize = 13.sp, color = HydraColors.Muted)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick presets
                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                formatMinutesToTime(context, 9 * 60) to (9 * 60),
                                formatMinutesToTime(context, 12 * 60) to (12 * 60),
                                formatMinutesToTime(context, 15 * 60) to (15 * 60),
                                formatMinutesToTime(context, 18 * 60) to (18 * 60),
                                formatMinutesToTime(context, 20 * 60) to (20 * 60)
                            ).forEach { (label, minutes) ->
                                SuggestionChip(
                                    onClick = {
                                        customHour = minutes / 60
                                        customMinute = minutes % 60
                                    },
                                    label = { Text(label, fontSize = 10.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Hour / Minute custom selector
                        FlowRow(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Hour
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Hour", fontSize = 11.sp, color = HydraColors.Muted)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { customHour = if (customHour > 0) customHour - 1 else 23 }) {
                                        Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "%02d".format(customHour),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HydraColors.Navy
                                    )
                                    IconButton(onClick = { customHour = if (customHour < 23) customHour + 1 else 0 }) {
                                        Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            // Minute
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Minute", fontSize = 11.sp, color = HydraColors.Muted)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { customMinute = if (customMinute >= 5) customMinute - 5 else 55 }) {
                                        Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "%02d".format(customMinute),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HydraColors.Navy
                                    )
                                    IconButton(onClick = { customMinute = if (customMinute <= 50) customMinute + 5 else 0 }) {
                                        Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val minutes = customHour * 60 + customMinute
                        scope.launch {
                            repo.updateSettings { s ->
                                when (target) {
                                    "water_interval" -> s.copy(waterReminderIntervalHours = customHour)
                                    "pushup_time" -> s.copy(pushUpReminderTimeMinutes = minutes)
                                    "pullup_time" -> s.copy(pullUpReminderTimeMinutes = minutes)
                                    "creatine_time" -> s.copy(creatineReminderTimeMinutes = minutes)
                                    "protein_time" -> s.copy(proteinReminderTimeMinutes = minutes)
                                    else -> s
                                }
                            }
                        }
                        showTimeDialogTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HydraColors.PrimaryTeal, contentColor = HydraColors.Navy)
                ) {
                    Text(if (target == "water_interval") "Save Interval" else "Save Time")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimeDialogTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Import confirmation dialog
    if (showImportConfirmDialog && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false; pendingImportUri = null },
            title = { Text("Confirm Backup Restore", fontWeight = FontWeight.Bold, color = HydraColors.Navy) },
            text = {
                Text(
                    text = "This will restore your habits, past logs, goals, and settings from the backup file, replacing current device records.\n\nAre you sure you want to proceed?",
                    fontSize = 13.sp,
                    color = HydraColors.Navy
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingImportUri!!
                        showImportConfirmDialog = false
                        pendingImportUri = null
                        scope.launch {
                            val res = repo.importBackup(uri)
                            if (res.isSuccess) {
                                snackbarHostState.showSnackbar("Backup restored successfully!")
                            } else {
                                android.util.Log.e("HydraPush", "Backup restore failed", res.exceptionOrNull())
                                snackbarHostState.showSnackbar("Could not restore this backup. Check that it is a valid HydraPush backup and try again.")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HydraColors.PrimaryTeal, contentColor = HydraColors.Navy)
                ) {
                    Text("Restore Backup")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirmDialog = false
                    pendingImportUri = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_hydra_drop),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("HydraPush v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "HydraPush is built for offline daily performance tracking.",
                        fontSize = 13.sp,
                        color = HydraColors.Navy
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• 100% Offline: Zero internet permission\n" +
                            "• Private: No account, no telemetry, no ads\n" +
                            "• Portable: User-controlled JSON backups via Storage Access Framework\n" +
                            "• Historical Integrity: Past records are read-only during normal use; full backup restore replaces the dataset after confirmation.",
                        fontSize = 12.sp,
                        color = HydraColors.Muted,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingIcon(icon: ImageVector? = null, iconRes: Int? = null) {
    Box(Modifier.size(40.dp).background(HydraColors.SurfaceAqua, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        if (iconRes != null) Image(painterResource(iconRes), null, Modifier.size(24.dp), contentScale = ContentScale.Fit)
        else if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = HydraColors.DeepTeal)
    }
}

@Composable
private fun GroupHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HydraColors.PrimaryTeal,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = HydraColors.Navy
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector? = null,
    iconRes: Int? = null,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingIcon(icon, iconRes)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = HydraColors.Navy)
            if (value.isNotEmpty()) Text(value, fontSize = 13.sp, color = HydraColors.DeepTeal)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = HydraColors.Muted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ReminderRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    enabled: Boolean,
    isHabitActive: Boolean = true,
    onToggle: (Boolean) -> Unit,
    onClickSchedule: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingIcon(iconRes = iconRes)
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(enabled = isHabitActive, onClick = onClickSchedule)
                .testTag("schedule_$title")
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (isHabitActive) HydraColors.Navy else HydraColors.Muted
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = subtitle,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = if (isHabitActive) HydraColors.DeepTeal else HydraColors.Muted
                )
                if (isHabitActive) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = HydraColors.PrimaryTeal,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Switch(
            modifier = Modifier.testTag("reminder_switch_$title"),
            checked = enabled && isHabitActive,
            onCheckedChange = onToggle,
            enabled = isHabitActive,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = HydraColors.PrimaryTeal
            )
        )
    }
}

private fun formatMinutesToTime(context: Context, minutesSinceMidnight: Int): String {
    val time = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, minutesSinceMidnight / 60)
        set(java.util.Calendar.MINUTE, minutesSinceMidnight % 60)
    }
    return android.text.format.DateFormat.getTimeFormat(context).format(time.time)
}

private fun TrackerSettings.withReminderEnabled(target: String, enabled: Boolean): TrackerSettings = when (target) {
    "water" -> copy(waterReminderEnabled = enabled)
    "pushup" -> copy(pushUpReminderEnabled = enabled)
    "pullup" -> copy(pullUpReminderEnabled = enabled)
    "creatine" -> copy(creatineReminderEnabled = enabled)
    "protein" -> copy(proteinReminderEnabled = enabled)
    else -> this
}

private fun handleReminderToggle(
    target: String,
    enable: Boolean,
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<String>,
    permissionAlreadyGranted: Boolean,
    onPermissionRequest: (String) -> Unit,
    onPersist: (String, Boolean) -> Unit
) {
    if (!enable) {
        onPersist(target, false)
        return
    }
    if (permissionAlreadyGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        onPersist(target, true)
        return
    }
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        onPersist(target, true)
        return
    }
    onPermissionRequest(target)
    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
}
