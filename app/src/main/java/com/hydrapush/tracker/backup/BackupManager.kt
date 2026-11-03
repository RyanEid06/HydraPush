package com.hydrapush.tracker.backup

import com.hydrapush.tracker.data.local.*
import com.hydrapush.tracker.data.preferences.TrackerSettings
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Validated, atomic backup & restore manager for HydraPush.
 * Uses JSON format with versioning, SHA-256 integrity check, and strict schema validation.
 */
object BackupManager {
    const val CURRENT_BACKUP_VERSION = 1
    const val APP_ID = "com.hydrapush.tracker"
    const val MAX_BACKUP_FILE_BYTES = 15 * 1024 * 1024 // 15 MB limit

    private val ISO_DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")

    data class BackupPayload(
        val version: Int,
        val createdAtEpochMs: Long,
        val snapshots: List<DailySnapshotEntity>,
        val water: List<WaterEntryEntity>,
        val pushups: List<PushUpSetEntity>,
        val revisions: List<ConfigurationRevisionEntity>,
        val achievements: List<AchievementEntity>,
        val settings: TrackerSettings
    )

    fun isValidIsoDate(str: String): Boolean {
        if (!ISO_DATE_REGEX.matches(str)) return false
        return try {
            LocalDate.parse(str)
            true
        } catch (_: DateTimeParseException) {
            false
        }
    }

    fun createBackupJson(
        snapshots: List<DailySnapshotEntity>,
        water: List<WaterEntryEntity>,
        pushups: List<PushUpSetEntity>,
        revisions: List<ConfigurationRevisionEntity>,
        achievements: List<AchievementEntity>,
        settings: TrackerSettings
    ): String {
        val root = JSONObject()
        root.put("appId", APP_ID)
        root.put("version", CURRENT_BACKUP_VERSION)
        root.put("createdAtEpochMs", System.currentTimeMillis())

        val snapArr = JSONArray()
        snapshots.forEach { s ->
            val o = JSONObject()
            o.put("day", s.day)
            o.put("waterGoalMl", s.waterGoalMl)
            o.put("pushUpGoalReps", s.pushUpGoalReps)
            o.put("creatineRequired", s.creatineRequired)
            o.put("proteinRequired", s.proteinRequired)
            o.put("creatineDone", s.creatineDone)
            o.put("proteinDone", s.proteinDone)
            s.requirementsRevisionId?.let { o.put("requirementsRevisionId", it) }
            o.put("createdAtEpochMs", s.createdAtEpochMs)
            snapArr.put(o)
        }
        root.put("snapshots", snapArr)

        val waterArr = JSONArray()
        water.forEach { w ->
            val o = JSONObject()
            o.put("id", w.id)
            o.put("day", w.day)
            o.put("amountMl", w.amountMl)
            o.put("createdAtEpochMs", w.createdAtEpochMs)
            waterArr.put(o)
        }
        root.put("water", waterArr)

        val pushArr = JSONArray()
        pushups.forEach { p ->
            val o = JSONObject()
            o.put("id", p.id)
            o.put("day", p.day)
            o.put("reps", p.reps)
            o.put("createdAtEpochMs", p.createdAtEpochMs)
            pushArr.put(o)
        }
        root.put("pushups", pushArr)

        val revArr = JSONArray()
        revisions.forEach { r ->
            val o = JSONObject()
            o.put("id", r.id)
            o.put("effectiveLocalDay", r.effectiveLocalDay)
            o.put("waterGoalMl", r.waterGoalMl)
            o.put("pushUpGoalReps", r.pushUpGoalReps)
            o.put("creatineRequired", r.creatineRequired)
            o.put("proteinRequired", r.proteinRequired)
            o.put("recordedAtEpochMs", r.recordedAtEpochMs)
            revArr.put(o)
        }
        root.put("revisions", revArr)

        val achArr = JSONArray()
        achievements.forEach { a ->
            val o = JSONObject()
            o.put("achievementId", a.achievementId)
            o.put("unlockedAtEpochMs", a.unlockedAtEpochMs)
            o.put("sourceDay", a.sourceDay)
            achArr.put(o)
        }
        root.put("achievements", achArr)

        val setObj = JSONObject()
        setObj.put("waterGoalMl", settings.waterGoalMl)
        setObj.put("pushUpGoalReps", settings.pushUpGoalReps)
        setObj.put("creatineEnabled", settings.creatineEnabled)
        setObj.put("proteinEnabled", settings.proteinEnabled)
        setObj.put("themeMode", settings.themeMode)
        // Complete reminder settings
        setObj.put("waterReminderEnabled", settings.waterReminderEnabled)
        setObj.put("waterReminderIntervalHours", settings.waterReminderIntervalHours)
        setObj.put("pushUpReminderEnabled", settings.pushUpReminderEnabled)
        setObj.put("pushUpReminderTimeMinutes", settings.pushUpReminderTimeMinutes)
        setObj.put("creatineReminderEnabled", settings.creatineReminderEnabled)
        setObj.put("creatineReminderTimeMinutes", settings.creatineReminderTimeMinutes)
        setObj.put("proteinReminderEnabled", settings.proteinReminderEnabled)
        setObj.put("proteinReminderTimeMinutes", settings.proteinReminderTimeMinutes)
        root.put("settings", setObj)

        val dataString = root.toString(2)
        val checksum = sha256(dataString)

        val envelope = JSONObject()
        envelope.put("data", dataString)
        envelope.put("checksum", checksum)
        return envelope.toString()
    }

    fun parseAndValidate(jsonString: String): BackupPayload {
        require(jsonString.isNotBlank()) { "Backup file is empty" }
        require(jsonString.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_FILE_BYTES) {
            "Backup exceeds maximum allowable size (15 MB)"
        }

        val envelope = JSONObject(jsonString)
        require(envelope.has("data") && envelope.has("checksum")) {
            "Invalid backup format: missing envelope"
        }
        val dataStr = envelope.getString("data")
        val expectedChecksum = envelope.getString("checksum")
        val actualChecksum = sha256(dataStr)
        require(expectedChecksum.equals(actualChecksum, ignoreCase = true)) {
            "Backup integrity check failed: checksum mismatch"
        }

        val root = JSONObject(dataStr)
        require(root.optString("appId") == APP_ID) {
            "Incompatible backup: wrong appId '${root.optString("appId")}'"
        }
        val version = root.getInt("version")
        require(version in 1..CURRENT_BACKUP_VERSION) {
            "Unsupported backup version: $version"
        }

        // Validate snapshots
        val snapshots = mutableListOf<DailySnapshotEntity>()
        val seenSnapshotDays = mutableSetOf<String>()
        val snapArr = root.getJSONArray("snapshots")
        for (i in 0 until snapArr.length()) {
            val o = snapArr.getJSONObject(i)
            val day = o.getString("day")
            require(isValidIsoDate(day)) { "Invalid snapshot date: $day" }
            require(seenSnapshotDays.add(day)) { "Duplicate snapshot day in backup: $day" }

            val waterGoal = o.getInt("waterGoalMl")
            require(waterGoal in 1..20000) { "Snapshot waterGoalMl out of range: $waterGoal" }
            val pushGoal = o.getInt("pushUpGoalReps")
            require(pushGoal in 1..10000) { "Snapshot pushUpGoalReps out of range: $pushGoal" }

            snapshots.add(
                DailySnapshotEntity(
                    day = day,
                    waterGoalMl = waterGoal,
                    pushUpGoalReps = pushGoal,
                    creatineRequired = o.getBoolean("creatineRequired"),
                    proteinRequired = o.getBoolean("proteinRequired"),
                    creatineDone = o.optBoolean("creatineDone", false),
                    proteinDone = o.optBoolean("proteinDone", false),
                    requirementsRevisionId = if (o.has("requirementsRevisionId") && !o.isNull("requirementsRevisionId")) {
                        o.getLong("requirementsRevisionId")
                    } else null,
                    createdAtEpochMs = o.optLong("createdAtEpochMs", System.currentTimeMillis())
                )
            )
        }

        // Validate water entries
        val water = mutableListOf<WaterEntryEntity>()
        val seenWaterIds = mutableSetOf<Long>()
        val waterArr = root.getJSONArray("water")
        for (i in 0 until waterArr.length()) {
            val o = waterArr.getJSONObject(i)
            val id = o.optLong("id", 0)
            if (id > 0) {
                require(seenWaterIds.add(id)) { "Duplicate water entry id: $id" }
            }
            val day = o.getString("day")
            require(isValidIsoDate(day)) { "Invalid water entry date: $day" }
            val amount = o.getInt("amountMl")
            require(amount in 1..10000) { "Water amountMl out of range: $amount" }

            water.add(
                WaterEntryEntity(
                    id = id,
                    day = day,
                    amountMl = amount,
                    createdAtEpochMs = o.optLong("createdAtEpochMs", System.currentTimeMillis())
                )
            )
        }

        // Validate push-up sets
        val pushups = mutableListOf<PushUpSetEntity>()
        val seenPushupIds = mutableSetOf<Long>()
        val pushArr = root.getJSONArray("pushups")
        for (i in 0 until pushArr.length()) {
            val o = pushArr.getJSONObject(i)
            val id = o.optLong("id", 0)
            if (id > 0) {
                require(seenPushupIds.add(id)) { "Duplicate pushup set id: $id" }
            }
            val day = o.getString("day")
            require(isValidIsoDate(day)) { "Invalid push-up set date: $day" }
            val reps = o.getInt("reps")
            require(reps in 1..2000) { "Push-up reps out of range: $reps" }

            pushups.add(
                PushUpSetEntity(
                    id = id,
                    day = day,
                    reps = reps,
                    createdAtEpochMs = o.optLong("createdAtEpochMs", System.currentTimeMillis())
                )
            )
        }

        // Validate revisions
        val revisions = mutableListOf<ConfigurationRevisionEntity>()
        val revArr = root.optJSONArray("revisions")
        if (revArr != null) {
            val seenRevIds = mutableSetOf<Long>()
            for (i in 0 until revArr.length()) {
                val o = revArr.getJSONObject(i)
                val id = o.optLong("id", 0)
                if (id > 0) {
                    require(seenRevIds.add(id)) { "Duplicate revision id: $id" }
                }
                val effDay = o.getString("effectiveLocalDay")
                require(isValidIsoDate(effDay)) { "Invalid revision effectiveLocalDay: $effDay" }
                val waterGoal = o.getInt("waterGoalMl")
                require(waterGoal in 1..20000) { "Revision waterGoalMl out of range: $waterGoal" }
                val pushGoal = o.getInt("pushUpGoalReps")
                require(pushGoal in 1..10000) { "Revision pushUpGoalReps out of range: $pushGoal" }

                revisions.add(
                    ConfigurationRevisionEntity(
                        id = id,
                        effectiveLocalDay = effDay,
                        waterGoalMl = waterGoal,
                        pushUpGoalReps = pushGoal,
                        creatineRequired = o.getBoolean("creatineRequired"),
                        proteinRequired = o.getBoolean("proteinRequired"),
                        recordedAtEpochMs = o.optLong("recordedAtEpochMs", System.currentTimeMillis())
                    )
                )
            }
        }

        // Validate achievements
        val achievements = mutableListOf<AchievementEntity>()
        val achArr = root.optJSONArray("achievements")
        if (achArr != null) {
            val seenAchIds = mutableSetOf<String>()
            for (i in 0 until achArr.length()) {
                val o = achArr.getJSONObject(i)
                val achId = o.getString("achievementId")
                require(achId.isNotBlank()) { "Empty achievementId in backup" }
                require(seenAchIds.add(achId)) { "Duplicate achievement in backup: $achId" }
                val srcDay = o.getString("sourceDay")
                require(isValidIsoDate(srcDay)) { "Invalid achievement sourceDay: $srcDay" }

                achievements.add(
                    AchievementEntity(
                        achievementId = achId,
                        unlockedAtEpochMs = o.optLong("unlockedAtEpochMs", System.currentTimeMillis()),
                        sourceDay = srcDay
                    )
                )
            }
        }

        // Validate settings
        val setObj = root.optJSONObject("settings")
        val waterGoal = setObj?.optInt("waterGoalMl", 2500) ?: 2500
        require(waterGoal in 1..20000) { "Settings waterGoalMl out of range: $waterGoal" }
        val pushGoal = setObj?.optInt("pushUpGoalReps", 100) ?: 100
        require(pushGoal in 1..10000) { "Settings pushUpGoalReps out of range: $pushGoal" }

        val waterInterval = setObj?.optInt("waterReminderIntervalHours", 2) ?: 2
        require(waterInterval in 1..24) { "Settings waterReminderIntervalHours out of range: $waterInterval" }

        val pushTime = setObj?.optInt("pushUpReminderTimeMinutes", 18 * 60) ?: (18 * 60)
        require(pushTime in 0..1439) { "Settings pushUpReminderTimeMinutes out of range: $pushTime" }

        val creatineTime = setObj?.optInt("creatineReminderTimeMinutes", 9 * 60) ?: (9 * 60)
        require(creatineTime in 0..1439) { "Settings creatineReminderTimeMinutes out of range: $creatineTime" }

        val proteinTime = setObj?.optInt("proteinReminderTimeMinutes", 19 * 60) ?: (19 * 60)
        require(proteinTime in 0..1439) { "Settings proteinReminderTimeMinutes out of range: $proteinTime" }

        val theme = setObj?.optString("themeMode", "SYSTEM") ?: "SYSTEM"
        require(theme in listOf("SYSTEM", "LIGHT", "DARK")) { "Invalid themeMode: $theme" }

        val settings = TrackerSettings(
            waterGoalMl = waterGoal,
            pushUpGoalReps = pushGoal,
            creatineEnabled = setObj?.optBoolean("creatineEnabled", true) ?: true,
            proteinEnabled = setObj?.optBoolean("proteinEnabled", true) ?: true,
            themeMode = theme,
            waterReminderEnabled = setObj?.optBoolean("waterReminderEnabled", true) ?: true,
            waterReminderIntervalHours = waterInterval,
            pushUpReminderEnabled = setObj?.optBoolean("pushUpReminderEnabled", true) ?: true,
            pushUpReminderTimeMinutes = pushTime,
            creatineReminderEnabled = setObj?.optBoolean("creatineReminderEnabled", true) ?: true,
            creatineReminderTimeMinutes = creatineTime,
            proteinReminderEnabled = setObj?.optBoolean("proteinReminderEnabled", true) ?: true,
            proteinReminderTimeMinutes = proteinTime
        )

        return BackupPayload(
            version = version,
            createdAtEpochMs = root.optLong("createdAtEpochMs", System.currentTimeMillis()),
            snapshots = snapshots,
            water = water,
            pushups = pushups,
            revisions = revisions,
            achievements = achievements,
            settings = settings
        )
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
