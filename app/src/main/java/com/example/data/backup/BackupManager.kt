package com.example.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.ApprovalStatus
import com.example.data.model.EntryCategory
import com.example.data.model.Farm
import com.example.data.model.FarmSummary
import com.example.data.model.LedgerEntry
import com.example.data.model.PaymentMethod
import com.example.data.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class BackupExecutionResult(
    val timestampMillis: Long,
    val localFileSaved: Boolean,
    val googleSheetStatus: String,
    val siaStorageStatus: String,
    val overallSummary: String,
)

data class SafetySnapshotResult(
    val internalSaved: Boolean,
    val downloadsSaved: Boolean,
    val fileName: String,
)

data class AvailableAppUpdate(
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val releaseNotes: String = "",
)

class BackupManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun performBackup(
        farm: Farm,
        entries: List<LedgerEntry>,
        summary: FarmSummary,
        prefs: UserPreferences,
    ): BackupExecutionResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val payloadJson = buildBackupJson(farm, entries, summary, now)
        val jsonString = payloadJson.toString(2)

        // 1. Always write verified local backup snapshot
        val localSaved = saveLocalSnapshot(farm.id, now, jsonString)

        // 2. Sync to Google Sheets (Apps Script Web App URL)
        val sheetStatus = if (prefs.googleSheetWebhookUrl.isBlank()) {
            "Not configured (Saved locally)"
        } else {
            syncToGoogleSheet(prefs.googleSheetWebhookUrl, jsonString)
        }

        // 3. Sync to Sia Storage (renterd HTTP worker API / S3-compatible endpoint)
        val siaStatus = if (prefs.siaEndpointUrl.isBlank()) {
            "Not configured (Saved locally)"
        } else {
            syncToSiaStorage(
                endpoint = prefs.siaEndpointUrl,
                bucket = prefs.siaBucket,
                token = prefs.siaAuthToken,
                farmId = farm.id,
                timestamp = now,
                jsonPayload = jsonString,
            )
        }

        val timeText = SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(now))
        val summaryLine = "$timeText | Local: ${if (localSaved) "OK" else "Fail"} | Sheet: $sheetStatus | Sia: $siaStatus"

        BackupExecutionResult(
            timestampMillis = now,
            localFileSaved = localSaved,
            googleSheetStatus = sheetStatus,
            siaStorageStatus = siaStatus,
            overallSummary = summaryLine,
        )
    }

    suspend fun exportCsvUri(farm: Farm, entries: List<LedgerEntry>): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val file = File(dir, "flock_ledger_${farm.id.take(12)}.csv")
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val sb = StringBuilder()
        sb.appendLine("ID,Date,Category,Sub_Category,Title,Amount_PKR,Sheep_Count,Shepherd,Note,Payment_Method,Logged_By,Has_Receipt")
        entries.forEach { e ->
            sb.appendLine(
                listOf(
                    e.id,
                    dateFmt.format(Date(e.entryDateMillis)),
                    e.category,
                    csvEscape(e.subCategory),
                    csvEscape(e.title),
                    e.amount.toString(),
                    e.livestockCount.toString(),
                    csvEscape(e.shepherdName),
                    csvEscape(e.note),
                    e.paymentMethod,
                    csvEscape(e.createdByName),
                    if (!e.receiptBase64.isNullOrBlank()) "YES" else "NO"
                ).joinToString(",")
            )
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun csvEscape(raw: String): String {
        val cleaned = raw.replace("\"", "\"\"").replace("\n", " ")
        return "\"$cleaned\""
    }

    private fun saveLocalSnapshot(farmId: String, timestamp: Long, json: String): Boolean {
        return try {
            val dir = File(context.filesDir, "backups").apply { mkdirs() }
            val file = File(dir, "backup_${farmId.take(10)}_$timestamp.json")
            file.writeText(json, Charsets.UTF_8)
            // Keep latest 10 local snapshots
            dir.listFiles()
                ?.filter { it.name.startsWith("backup_") && it.name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(10)
                ?.forEach { it.delete() }
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Local snapshot failed", e)
            false
        }
    }

    private fun syncToGoogleSheet(webhookUrl: String, jsonPayload: String): String {
        return try {
            val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(webhookUrl)
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    "Synced (${response.code})"
                } else {
                    "HTTP ${response.code}"
                }
            }
        } catch (e: Exception) {
            Log.w("BackupManager", "Google Sheet sync error: ${e.message}")
            "Error: ${e.localizedMessage ?: "Network failed"}"
        }
    }

    private fun syncToSiaStorage(
        endpoint: String,
        bucket: String,
        token: String,
        farmId: String,
        timestamp: Long,
        jsonPayload: String,
    ): String {
        return try {
            val cleanBase = endpoint.trimEnd('/')
            val objectKey = "flock_${farmId.take(12)}_$timestamp.json"
            val targetUrl = if (cleanBase.contains("/api/worker/objects")) {
                "$cleanBase/$objectKey?bucket=$bucket"
            } else {
                "$cleanBase/$bucket/$objectKey"
            }

            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .put(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))

            if (token.isNotBlank()) {
                if (token.startsWith("Bearer ", ignoreCase = true) || token.startsWith("Basic ", ignoreCase = true)) {
                    reqBuilder.header("Authorization", token)
                } else {
                    // Standard Sia renterd uses Basic auth with empty username and API password
                    reqBuilder.header("Authorization", Credentials.basic("", token))
                }
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    "Synced (${response.code})"
                } else {
                    "HTTP ${response.code}"
                }
            }
        } catch (e: Exception) {
            Log.w("BackupManager", "Sia Storage sync error: ${e.message}")
            "Error: ${e.localizedMessage ?: "Network failed"}"
        }
    }

    private fun buildBackupJson(
        farm: Farm,
        entries: List<LedgerEntry>,
        summary: FarmSummary,
        timestampMillis: Long,
    ): JSONObject {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val summaryJson = JSONObject().apply {
            put("ownerFundsReceived", summary.ownerFundsReceived)
            put("totalOutflows", summary.totalOutflows)
            put("livestockSalesRevenue", summary.livestockSalesRevenue)
            put("remainingCashBalance", summary.remainingCashBalance)
            put("generalExpenses", summary.generalExpenses)
            put("medicineVetExpenses", summary.medicineVetExpenses)
            put("livestockBuyCost", summary.livestockBuyCost)
            put("shepherdAdvancesTotal", summary.shepherdAdvancesTotal)
            put("shepherdSalariesTotal", summary.shepherdSalariesTotal)
            put("livestockBoughtCount", summary.livestockBoughtCount)
            put("livestockSoldCount", summary.livestockSoldCount)
            put("currentFlockCount", summary.currentFlockCount)
        }

        val rowsArray = JSONArray()
        entries.forEach { entry ->
            rowsArray.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("date", dateFmt.format(Date(entry.entryDateMillis)))
                    put("category", entry.category)
                    put("subCategory", entry.subCategory)
                    put("title", entry.title)
                    put("amount", entry.amount)
                    put("livestockCount", entry.livestockCount)
                    put("shepherdName", entry.shepherdName)
                    put("note", entry.note)
                    put("paymentMethod", entry.paymentMethod)
                    put("loggedBy", entry.createdByName)
                    put("hasReceipt", !entry.receiptBase64.isNullOrBlank())
                }
            )
        }

        return JSONObject().apply {
            put("app", "FlockLedger")
            put("farmId", farm.id)
            put("farmName", farm.name)
            put("backupTimestamp", timestampMillis)
            put("backupDate", dateFmt.format(Date(timestampMillis)))
            put("entryCount", entries.size)
            put("summary", summaryJson)
            put("entries", rowsArray)
        }
    }

    suspend fun saveSafetySnapshotEverywhere(
        farms: List<Farm>,
        entries: List<LedgerEntry>,
    ): SafetySnapshotResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val json = buildFullSafetyBackupJson(farms, entries, now).toString(2)
        val internalOk = try {
            val dir = File(context.filesDir, "backups").apply { mkdirs() }
            File(dir, SAFETY_BACKUP_INTERNAL_FILE).writeText(json, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Internal safety snapshot failed", e)
            false
        }

        val downloadsOk = writeSafetyFileToDownloads(json)
        SafetySnapshotResult(
            internalSaved = internalOk,
            downloadsSaved = downloadsOk,
            fileName = SAFETY_BACKUP_DOWNLOADS_FILE,
        )
    }

    private fun writeSafetyFileToDownloads(json: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val resolver = context.contentResolver
            val relPath = Environment.DIRECTORY_DOWNLOADS + "/FlockLedger"
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            val args = arrayOf(SAFETY_BACKUP_DOWNLOADS_FILE, "%FlockLedger%")

            var existingUri: Uri? = null
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                args,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val id = cursor.getLong(idCol)
                    existingUri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                }
            }

            if (existingUri != null) {
                try {
                    resolver.openOutputStream(existingUri!!, "wt")?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                        out.flush()
                        return true
                    }
                } catch (_: Exception) {
                    // Fallback: delete stale entry and re-insert below
                    try {
                        resolver.delete(existingUri!!, null, null)
                    } catch (_: Exception) {
                    }
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, SAFETY_BACKUP_DOWNLOADS_FILE)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            }
            val newUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(newUri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            true
        } catch (e: Exception) {
            Log.w("BackupManager", "Downloads mirror write failed: ${e.message}")
            false
        }
    }

    suspend fun exportBackupToUri(
        uri: Uri,
        farms: List<Farm>,
        entries: List<LedgerEntry>,
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val json = buildFullSafetyBackupJson(farms, entries, System.currentTimeMillis()).toString(2)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return@withContext Result.failure(IllegalStateException("Could not open file for writing"))
            Result.success(entries.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readBackupFromUri(uri: Uri): Result<Pair<List<Farm>, List<LedgerEntry>>> = withContext(Dispatchers.IO) {
        try {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: return@withContext Result.failure(IllegalStateException("Could not read selected file"))
            parseFullSafetyBackupJson(raw)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkAppUpdate(
        repoOrUrl: String,
        currentVersionCode: Long,
        currentVersionName: String,
    ): AvailableAppUpdate? = withContext(Dispatchers.IO) {
        val targetUrl = normalizeUpdateUrl(repoOrUrl) ?: return@withContext null
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return@withContext null
                parseUpdatePayload(body, currentVersionCode, currentVersionName)
            }
        } catch (e: Exception) {
            Log.w("BackupManager", "Update check error: ${e.message}")
            null
        }
    }

    companion object {
        const val SAFETY_BACKUP_INTERNAL_FILE = "flockledger_safety_latest.json"
        const val SAFETY_BACKUP_DOWNLOADS_FILE = "FlockLedger_Safety_Backup.json"

        fun normalizeUpdateUrl(repoOrUrl: String): String? {
            val clean = repoOrUrl.trim().removeSuffix("/")
            if (clean.isEmpty()) return null

            val githubWebRegex = Regex("""^https?://(?:www\.)?github\.com/([^/]+)/([^/]+?)(?:\.git)?(?:/releases.*)?$""", RegexOption.IGNORE_CASE)
            val webMatch = githubWebRegex.matchEntire(clean)
            if (webMatch != null) {
                val owner = webMatch.groupValues[1]
                val repo = webMatch.groupValues[2]
                return "https://api.github.com/repos/$owner/$repo/releases/latest"
            }

            return when {
                clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true) -> clean
                clean.count { it == '/' } == 1 && !clean.contains(' ') ->
                    "https://api.github.com/repos/${clean.removeSuffix(".git")}/releases/latest"
                else -> null
            }
        }

        fun buildFullSafetyBackupJson(
            farms: List<Farm>,
            entries: List<LedgerEntry>,
            timestampMillis: Long,
        ): JSONObject {
            val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

            val farmsArray = JSONArray()
            farms.forEach { farm ->
                val shepherdsArray = JSONArray()
                farm.shepherds.forEach { shepherdsArray.put(it) }
                farmsArray.put(
                    JSONObject().apply {
                        put("id", farm.id)
                        put("ownerId", farm.ownerId)
                        put("name", farm.name)
                        put("shepherds", shepherdsArray)
                        put("initialFlockCount", farm.initialFlockCount)
                        put("createdAtMillis", farm.createdAtMillis)
                        put("updatedAtMillis", farm.updatedAtMillis)
                    }
                )
            }

            val entriesArray = JSONArray()
            entries.forEach { entry ->
                entriesArray.put(
                    JSONObject().apply {
                        put("id", entry.id)
                        put("farmId", entry.farmId)
                        put("createdByName", entry.createdByName)
                        put("category", entry.category)
                        put("subCategory", entry.subCategory)
                        put("amount", entry.amount)
                        put("livestockCount", entry.livestockCount)
                        put("shepherdName", entry.shepherdName)
                        put("title", entry.title)
                        put("note", entry.note)
                        if (!entry.receiptBase64.isNullOrBlank()) {
                            put("receiptBase64", entry.receiptBase64)
                        }
                        put("entryDateMillis", entry.entryDateMillis)
                        put("createdAtMillis", entry.createdAtMillis)
                        put("updatedAtMillis", entry.updatedAtMillis)
                        put("approvalStatus", entry.approvalStatus)
                        put("paymentMethod", entry.paymentMethod)
                    }
                )
            }

            return JSONObject().apply {
                put("app", "FlockLedger")
                put("backupFormatVersion", 2)
                put("backupTimestamp", timestampMillis)
                put("backupDate", dateFmt.format(Date(timestampMillis)))
                put("farmCount", farms.size)
                put("entryCount", entries.size)
                put("farms", farmsArray)
                put("entries", entriesArray)
            }
        }

        fun parseFullSafetyBackupJson(rawJson: String): Result<Pair<List<Farm>, List<LedgerEntry>>> {
            return try {
                val root = JSONObject(rawJson)
                val now = System.currentTimeMillis()
                val farms = mutableListOf<Farm>()

                val farmsArr = root.optJSONArray("farms")
                if (farmsArr != null) {
                    for (i in 0 until farmsArr.length()) {
                        val obj = farmsArr.optJSONObject(i) ?: continue
                        val shepherds = mutableListOf<String>()
                        val shArr = obj.optJSONArray("shepherds")
                        if (shArr != null) {
                            for (j in 0 until shArr.length()) {
                                val s = shArr.optString(j).trim()
                                if (s.isNotEmpty()) shepherds.add(s)
                            }
                        }
                        farms.add(
                            Farm(
                                id = obj.optString("id").ifBlank { "farm_default" },
                                ownerId = obj.optString("ownerId").ifBlank { "local_admin" },
                                name = obj.optString("name").ifBlank { "My Sheep Farm" },
                                shepherds = shepherds,
                                initialFlockCount = obj.optLong("initialFlockCount", 0L),
                                createdAtMillis = obj.optLong("createdAtMillis", now),
                                updatedAtMillis = obj.optLong("updatedAtMillis", now),
                            )
                        )
                    }
                } else {
                    val legacyFarmId = root.optString("farmId").ifBlank { "farm_default" }
                    val legacyFarmName = root.optString("farmName").ifBlank { "My Sheep Farm" }
                    farms.add(Farm(id = legacyFarmId, name = legacyFarmName))
                }

                val defaultFarmId = farms.firstOrNull()?.id ?: "farm_default"
                val entries = mutableListOf<LedgerEntry>()
                val entriesArr = root.optJSONArray("entries")
                if (entriesArr != null) {
                    for (i in 0 until entriesArr.length()) {
                        val obj = entriesArr.optJSONObject(i) ?: continue
                        val cat = EntryCategory.fromRaw(obj.optString("category")).name
                        val amount = obj.optDouble("amount", 0.0)
                        if (amount.isNaN() || amount < 0.0 || (amount == 0.0 && cat != EntryCategory.LIVESTOCK_DEATH.name)) continue
                        val title = obj.optString("title").trim().ifEmpty { "Entry" }
                        val status = ApprovalStatus.fromRaw(obj.optString("approvalStatus", ApprovalStatus.APPROVED.name)).name
                        val method = PaymentMethod.fromRaw(obj.optString("paymentMethod", PaymentMethod.CASH.name)).name
                        val receipt = obj.optString("receiptBase64", "").ifBlank { null }
                        val entryDate = obj.optLong("entryDateMillis", now)
                        entries.add(
                            LedgerEntry(
                                id = obj.optString("id").ifBlank {
                                    "e_restored_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                                },
                                farmId = obj.optString("farmId").ifBlank { defaultFarmId },
                                createdByName = obj.optString("createdByName", obj.optString("loggedBy", "Admin")),
                                category = cat,
                                subCategory = obj.optString("subCategory", ""),
                                amount = amount,
                                livestockCount = obj.optLong("livestockCount", 0L),
                                shepherdName = obj.optString("shepherdName", ""),
                                title = title,
                                note = obj.optString("note", ""),
                                receiptBase64 = receipt,
                                entryDateMillis = entryDate,
                                createdAtMillis = obj.optLong("createdAtMillis", entryDate),
                                updatedAtMillis = obj.optLong("updatedAtMillis", now),
                                approvalStatus = status,
                                paymentMethod = method,
                            )
                        )
                    }
                }

                Result.success(farms to entries)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        fun parseUpdatePayload(
            rawJson: String,
            currentVersionCode: Long,
            currentVersionName: String,
        ): AvailableAppUpdate? {
            return try {
                val obj = JSONObject(rawJson)
                // Format 1: Custom version.json manifest { versionCode, versionName, apkUrl, notes }
                if (obj.has("versionCode") || obj.has("apkUrl")) {
                    val remoteCode = obj.optLong("versionCode", 0L)
                    val remoteName = obj.optString("versionName", "")
                    val apkUrl = obj.optString("apkUrl", "")
                    val notes = obj.optString("notes", "")
                    if (apkUrl.isNotBlank() && (remoteCode > currentVersionCode || isVersionNewer(remoteName, currentVersionName))) {
                        return AvailableAppUpdate(
                            versionName = remoteName.ifBlank { "v$remoteCode" },
                            versionCode = remoteCode,
                            downloadUrl = apkUrl,
                            releaseNotes = notes,
                        )
                    }
                    return null
                }

                // Format 2: GitHub Releases API (/releases/latest)
                val tagName = obj.optString("tag_name", "").trim()
                val htmlUrl = obj.optString("html_url", "")
                val notes = obj.optString("body", "")
                var apkDownloadUrl = ""
                val assets = obj.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }
                val finalUrl = apkDownloadUrl.ifBlank { htmlUrl }
                if (finalUrl.isNotBlank() && isVersionNewer(tagName, currentVersionName)) {
                    AvailableAppUpdate(
                        versionName = tagName,
                        versionCode = currentVersionCode + 1,
                        downloadUrl = finalUrl,
                        releaseNotes = notes.take(180),
                    )
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        fun isVersionNewer(candidateRaw: String, currentRaw: String): Boolean {
            val candParts = candidateRaw.trim().removePrefix("v").removePrefix("V")
                .split('.').mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
            val currParts = currentRaw.trim().removePrefix("v").removePrefix("V")
                .split('.').mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
            if (candParts.isEmpty() || currParts.isEmpty()) return false
            val maxLen = maxOf(candParts.size, currParts.size)
            for (i in 0 until maxLen) {
                val c = candParts.getOrElse(i) { 0 }
                val cur = currParts.getOrElse(i) { 0 }
                if (c > cur) return true
                if (c < cur) return false
            }
            return false
        }

        const val GOOGLE_APPS_SCRIPT_TEMPLATE = """function doPost(e) {
  var data = JSON.parse(e.postData.contents);
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName("FlockLedger") || ss.insertSheet("FlockLedger");
  sheet.clear();
  sheet.appendRow(["Date", "Category", "SubCategory", "Title", "Amount (PKR)", "Sheep Count", "Shepherd", "Note", "Payment Method", "Logged By"]);
  data.entries.forEach(function(row) {
    sheet.appendRow([row.date, row.category, row.subCategory || "", row.title, row.amount, row.livestockCount, row.shepherdName, row.note, row.paymentMethod || "CASH", row.loggedBy]);
  });
  return ContentService.createTextOutput(JSON.stringify({status: "ok", rows: data.entries.length}))
    .setMimeType(ContentService.MimeType.JSON);
}"""
    }
}
