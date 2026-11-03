package com.hydrapush.tracker.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hydrapush.tracker.data.preferences.TrackerSettings
import com.hydrapush.tracker.data.repository.TrackerRepository
import com.hydrapush.tracker.ui.components.BrandHeader
import com.hydrapush.tracker.ui.components.SectionCard
import com.hydrapush.tracker.ui.theme.HydraColors
import java.time.LocalDate
import kotlinx.coroutines.launch

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

    var showThemeDialog by remember { mutableStateOf(false) }
    var showTimeDialogTarget by remember { mutableStateOf<String?>(null) }
    var timeHours by remember { mutableStateOf(12) }
    var timeMinutes by remember { mutableStateOf(0) }

    var showAboutDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }

    // Android 13+ Notification permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            scope.launch {
                snackbarHostState.showSnackbar("Notifications permission was denied. Reminders will not alert.")
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
                    snackbarHostState.showSnackbar("Export failed: ${result.exceptionOrNull()?.message}")
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
                Text("⚙️ ", fontSize = 20.sp)
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
                GroupHeader(icon = "🎯", title = "Daily Goals")
                Spacer(modifier = Modifier.height(10.dp))

                SettingRow(
                    icon = "💧",
                    title = "Water Goal",
                    value = "${String.format("%,d", settings.waterGoalMl)} ml",
                    onClick = {
                        goalInput = settings.waterGoalMl.toString()
                        goalError = null
                        goalDialogTarget = "water"
                    }
                )

                HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(vertical = 4.dp))

                SettingRow(
                    icon = "↗",
                    title = "Push-up Goal",
                    value = "${settings.pushUpGoalReps} reps",
                    onClick = {
                        goalInput = settings.pushUpGoalReps.toString()
                        goalError = null
                        goalDialogTarget = "pushup"
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Active Habits Group
            SectionCard(modifier = Modifier.fillMaxWidth().testTag("active_habits_group")) {
                GroupHeader(icon = "⚡", title = "Active Habits")
                Spacer(modifier = Modifier.height(10.dp))

                // Water (Permanently On)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💧", fontSize = 16.sp, modifier = Modifier.width(28.dp))
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

                HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(vertical = 4.dp))

                // Push-ups (Permanently On)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("↗", fontSize = 16.sp, color = HydraColors.Lime, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                    Text(
                        text = "Push-ups",
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

                HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(vertical = 4.dp))

                // Creatine (Toggleable)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚡", fontSize = 16.sp, modifier = Modifier.width(28.dp))
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
                            scope.launch { repo.updateSettings { it.copy(creatineEnabled = enabled) } }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = HydraColors.PrimaryTeal
                        ),
                        modifier = Modifier.testTag("switch_creatine")
                    )
                }

                HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(vertical = 4.dp))

                // Protein Shake (Toggleable)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🥤", fontSize = 16.sp, modifier = Modifier.width(28.dp))
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
                            scope.launch { repo.updateSettings { it.copy(proteinEnabled = enabled) } }
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
                GroupHeader(icon = "🔔", title = "Reminders")
                Spacer(modifier = Modifier.height(10.dp))

                // Water Reminder
                ReminderRow(
                    icon = "💧",
                    title = "Water Reminder",
                    subtitle = "Every ${settings.waterReminderIntervalHours} hours",
                    enabled = settings.waterReminderEnabled,
                    onToggle = { isEnabled ->
                        checkPermissionAndLaunch(context, notificationPermissionLauncher)
                        scope.launch { repo.updateSettings { it.copy(waterReminderEnabled = isEnabled) } }
                    },
                    onClickSchedule = {
                        timeHours = settings.waterReminderIntervalHours
                        showTimeDialogTarget = "water_interval"
                    }
                )

                HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(vertical = 4.dp))

                // Push-up Reminder
                ReminderRow(
                    icon = "↗",
                    title = "Push-up Reminder",
                    subtitle = formatMinutesToTime(settings.pushUpReminderTimeMinutes),
                    enabled = settings.pushUpReminderEnabled,
                    onToggle = { isEnabled ->
                        checkPermissionAndLaunch(context, notificationPermissionLauncher)
                        scope.launch { repo.updateSettings { it.copy(pushUpReminderEnabled = isEnabled) } }
                    },
                    onClickSchedule = {
                        timeHours = settings.pushUpReminderTimeMinutes / 60
                        timeMinutes = settings.pushUpReminderTimeMinutes % 60
                        showTimeDialogTarget = "pushup_time"
                    }
                )

                HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(vertical = 4.dp))

                // Creatine Reminder
                ReminderRow(
                    icon = "⚡",
                    title = "Creatine Reminder",
                    subtitle = if (!settings.creatineEnabled) "Habit deactivated" else formatMinutesToTime(settings.creatineReminderTimeMinutes),
                    enabled = settings.creatineEnabled && settings.creatineReminderEnabled,
                    isHabitActive = settings.creatineEnabled,
                    onToggle = { isEnabled ->
                        checkPermissionAndLaunch(context, notificationPermissionLauncher)
                        scope.launch { repo.updateSettings { it.copy(creatineReminderEnabled = isEnabled) } }
                    },
                    onClickSchedule = {
                        if (settings.creatineEnabled) {
                            timeHours = settings.creatineReminderTimeMinutes / 60
                            timeMinutes = settings.creatineReminderTimeMinutes % 60
                            showTimeDialogTarget = "creatine_time"
                        }
                    }
                )

                HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(vertical = 4.dp))

                // Protein Shake Reminder
                ReminderRow(
                    icon = "🥤",
                    title = "Protein Shake Reminder",
                    subtitle = if (!settings.proteinEnabled) "Habit deactivated" else formatMinutesToTime(settings.proteinReminderTimeMinutes),
                    enabled = settings.proteinEnabled && settings.proteinReminderEnabled,
                    isHabitActive = settings.proteinEnabled,
                    onToggle = { isEnabled ->
                        checkPermissionAndLaunch(context, notificationPermissionLauncher)
                        scope.launch { repo.updateSettings { it.copy(proteinReminderEnabled = isEnabled) } }
                    },
                    onClickSchedule = {
                        if (settings.proteinEnabled) {
                            timeHours = settings.proteinReminderTimeMinutes / 60
                            timeMinutes = settings.proteinReminderTimeMinutes % 60
                            showTimeDialogTarget = "protein_time"
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Appearance Group
            SectionCard(modifier = Modifier.fillMaxWidth().testTag("appearance_group")) {
                GroupHeader(icon = "🎨", title = "Appearance")
                Spacer(modifier = Modifier.height(10.dp))

                val themeLabel = when (settings.themeMode.uppercase()) {
                    "LIGHT" -> "Light"
                    "DARK" -> "Dark"
                    else -> "Follow System"
                }

                SettingRow(
                    icon = "☀️",
                    title = "Theme",
                    value = themeLabel,
                    onClick = { showThemeDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Data & Backup Group
            SectionCard(modifier = Modifier.fillMaxWidth().testTag("data_backup_group")) {
                GroupHeader(icon = "💾", title = "Data")
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val defaultName = "hydrapush_backup_${LocalDate.now()}.json"
                            exportLauncher.launch(defaultName)
                        }
                        .padding(vertical = 10.dp)
                        .testTag("export_backup_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = "Export",
                        tint = HydraColors.PrimaryTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Export Backup",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = HydraColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = HydraColors.Muted
                    )
                }

                HorizontalDivider(color = Color(0xFFF0F5F5), modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                        .padding(vertical = 10.dp)
                        .testTag("import_backup_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Import",
                        tint = HydraColors.Lime,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Import Backup",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = HydraColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = HydraColors.Muted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. About Group
            SectionCard(modifier = Modifier.fillMaxWidth().testTag("about_group")) {
                GroupHeader(icon = "ℹ️", title = "About")
                Spacer(modifier = Modifier.height(10.dp))

                SettingRow(
                    icon = "📱",
                    title = "App Version",
                    value = "v1.0.0",
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
        val maxLimit = if (isWater) 20000 else 10000
        AlertDialog(
            onDismissRequest = { goalDialogTarget = null },
            title = {
                Text(
                    text = if (isWater) "Edit Daily Water Goal" else "Edit Daily Push-up Goal",
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
                        label = { Text(if (isWater) "Water (ml)" else "Push-ups (reps)") },
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
                        if (value == null || value !in 1..maxLimit) {
                            goalError = "Enter an amount between 1 and $maxLimit"
                        } else {
                            scope.launch {
                                repo.updateSettings {
                                    if (isWater) it.copy(waterGoalMl = value)
                                    else it.copy(pushUpGoalReps = value)
                                }
                            }
                            goalDialogTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isWater) HydraColors.PrimaryTeal else HydraColors.Lime),
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

    // Theme Picker Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("Follow System" to "SYSTEM", "Light" to "LIGHT", "Dark" to "DARK").forEach { (label, mode) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { repo.updateSettings { it.copy(themeMode = mode) } }
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.themeMode.equals(mode, ignoreCase = true),
                                onClick = {
                                    scope.launch { repo.updateSettings { it.copy(themeMode = mode) } }
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 14.sp, color = HydraColors.Navy)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
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
                        listOf(1, 2, 3, 4).forEach { hours ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch { repo.updateSettings { it.copy(waterReminderIntervalHours = hours) } }
                                        showTimeDialogTarget = null
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.waterReminderIntervalHours == hours,
                                    onClick = {
                                        scope.launch { repo.updateSettings { it.copy(waterReminderIntervalHours = hours) } }
                                        showTimeDialogTarget = null
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Every $hours hour${if (hours > 1) "s" else ""}", fontSize = 14.sp)
                            }
                        }
                    }
                } else {
                    Column {
                        Text("Select time of day:", fontSize = 13.sp, color = HydraColors.Muted)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "Morning (9:00 AM)" to (9 * 60),
                                "Noon (12:00 PM)" to (12 * 60),
                                "Afternoon (3:00 PM)" to (15 * 60),
                                "Evening (6:00 PM)" to (18 * 60),
                                "Night (8:00 PM)" to (20 * 60)
                            ).forEach { (label, minutes) ->
                                SuggestionChip(
                                    onClick = {
                                        scope.launch {
                                            repo.updateSettings { s ->
                                                when (target) {
                                                    "pushup_time" -> s.copy(pushUpReminderTimeMinutes = minutes)
                                                    "creatine_time" -> s.copy(creatineReminderTimeMinutes = minutes)
                                                    else -> s.copy(proteinReminderTimeMinutes = minutes)
                                                }
                                            }
                                        }
                                        showTimeDialogTarget = null
                                    },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeDialogTarget = null }) { Text("Done") }
            }
        )
    }

    // Import confirmation dialog
    if (showImportConfirmDialog && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
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
                                snackbarHostState.showSnackbar("Restore failed: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HydraColors.PrimaryTeal)
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
                    Text("💧 ", fontSize = 20.sp)
                    Text("HydraPush v1.0.0", fontWeight = FontWeight.Bold)
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
                            "• Immutable History: Past records are permanently preserved",
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

private fun checkPermissionAndLaunch(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<String>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private fun formatMinutesToTime(minutes: Int): String {
    val h = (minutes / 60) % 24
    val m = minutes % 60
    val ampm = if (h >= 12) "PM" else "AM"
    val displayH = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "%d:%02d %s".format(displayH, m, ampm)
}

@Composable
private fun GroupHeader(icon: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(6.dp))
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
    icon: String,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 16.sp, modifier = Modifier.width(28.dp))
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = HydraColors.Navy,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = HydraColors.PrimaryTeal,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = HydraColors.Muted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ReminderRow(
    icon: String,
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
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 16.sp, modifier = Modifier.width(28.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = isHabitActive, onClick = onClickSchedule)
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
                    fontSize = 11.sp,
                    color = if (isHabitActive) HydraColors.PrimaryTeal else HydraColors.Muted
                )
                if (isHabitActive) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = HydraColors.PrimaryTeal,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Switch(
            checked = enabled,
            enabled = isHabitActive,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = HydraColors.PrimaryTeal
            )
        )
    }
}
