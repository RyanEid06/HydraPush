package com.hydrapush.tracker.backup

import android.content.Context
import android.net.Uri
import com.hydrapush.tracker.data.local.*
import com.hydrapush.tracker.data.preferences.TrackerSettings
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Validated, atomic backup & restore manager for HydraPush.
 * Uses JSON format with versioning and SHA-256 integrity check.
 */
object BackupManager {
    const val CURRENT_BACKUP_VERSION = 1
    const val APP_ID = "com.hydrapush.tracker"

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
            "Incompatible backup: wrong appId ${root.optString("appId")}"
        }
        val version = root.getInt("version")
        require(version in 1..CURRENT_BACKUP_VERSION) {
            "Unsupported backup version: $version"
        }

        val snapshots = mutableListOf<DailySnapshotEntity>()
        val snapArr = root.getJSONArray("snapshots")
        for (i in 0 until snapArr.length()) {
            val o = snapArr.getJSONObject(i)
            snapshots.add(
                DailySnapshotEntity(
                    day = o.getString("day"),
                    waterGoalMl = o.getInt("waterGoalMl").coerceIn(1, 20000),
                    pushUpGoalReps = o.getInt("pushUpGoalReps").coerceIn(1, 10000),
                    creatineRequired = o.getBoolean("creatineRequired"),
                    proteinRequired = o.getBoolean("proteinRequired"),
                    creatineDone = o.optBoolean("creatineDone", false),
                    proteinDone = o.optBoolean("proteinDone", false),
                    requirementsRevisionId = if (o.has("requirementsRevisionId")) o.getLong("requirementsRevisionId") else null,
                    createdAtEpochMs = o.optLong("createdAtEpochMs", System.currentTimeMillis())
                )
            )
        }

        val water = mutableListOf<WaterEntryEntity>()
        val waterArr = root.getJSONArray("water")
        for (i in 0 until waterArr.length()) {
            val o = waterArr.getJSONObject(i)
            water.add(
                WaterEntryEntity(
                    id = o.optLong("id", 0),
                    day = o.getString("day"),
                    amountMl = o.getInt("amountMl").coerceIn(1, 10000),
                    createdAtEpochMs = o.optLong("createdAtEpochMs", System.currentTimeMillis())
                )
            )
        }

        val pushups = mutableListOf<PushUpSetEntity>()
        val pushArr = root.getJSONArray("pushups")
        for (i in 0 until pushArr.length()) {
            val o = pushArr.getJSONObject(i)
            pushups.add(
                PushUpSetEntity(
                    id = o.optLong("id", 0),
                    day = o.getString("day"),
                    reps = o.getInt("reps").coerceIn(1, 2000),
                    createdAtEpochMs = o.optLong("createdAtEpochMs", System.currentTimeMillis())
                )
            )
        }

        val revisions = mutableListOf<ConfigurationRevisionEntity>()
        val revArr = root.optJSONArray("revisions")
        if (revArr != null) {
            for (i in 0 until revArr.length()) {
                val o = revArr.getJSONObject(i)
                revisions.add(
                    ConfigurationRevisionEntity(
                        id = o.optLong("id", 0),
                        effectiveLocalDay = o.getString("effectiveLocalDay"),
                        waterGoalMl = o.getInt("waterGoalMl").coerceIn(1, 20000),
                        pushUpGoalReps = o.getInt("pushUpGoalReps").coerceIn(1, 10000),
                        creatineRequired = o.getBoolean("creatineRequired"),
                        proteinRequired = o.getBoolean("proteinRequired"),
                        recordedAtEpochMs = o.optLong("recordedAtEpochMs", System.currentTimeMillis())
                    )
                )
            }
        }

        val achievements = mutableListOf<AchievementEntity>()
        val achArr = root.optJSONArray("achievements")
        if (achArr != null) {
            for (i in 0 until achArr.length()) {
                val o = achArr.getJSONObject(i)
                achievements.add(
                    AchievementEntity(
                        achievementId = o.getString("achievementId"),
                        unlockedAtEpochMs = o.optLong("unlockedAtEpochMs", System.currentTimeMillis()),
                        sourceDay = o.getString("sourceDay")
                    )
                )
            }
        }

        val setObj = root.optJSONObject("settings")
        val settings = TrackerSettings(
            waterGoalMl = setObj?.optInt("waterGoalMl", 2500) ?: 2500,
            pushUpGoalReps = setObj?.optInt("pushUpGoalReps", 100) ?: 100,
            creatineEnabled = setObj?.optBoolean("creatineEnabled", true) ?: true,
            proteinEnabled = setObj?.optBoolean("proteinEnabled", true) ?: true,
            themeMode = setObj?.optString("themeMode", "SYSTEM") ?: "SYSTEM"
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
