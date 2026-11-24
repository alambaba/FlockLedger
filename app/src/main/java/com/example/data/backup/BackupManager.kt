package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.Farm
import com.example.data.model.FarmSummary
import com.example.data.model.LedgerEntry
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
import java.util.concurrent.TimeUnit

data class BackupExecutionResult(
    val timestampMillis: Long,
    val localFileSaved: Boolean,
    val googleSheetStatus: String,
    val siaStorageStatus: String,
    val overallSummary: String,
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
        sb.appendLine("ID,Date,Category,Title,Amount_PKR,Sheep_Count,Shepherd,Note,Logged_By,Has_Receipt")
        entries.forEach { e ->
            sb.appendLine(
                listOf(
                    e.id,
                    dateFmt.format(Date(e.entryDateMillis)),
                    e.category,
                    csvEscape(e.title),
                    e.amount.toString(),
                    e.livestockCount.toString(),
                    csvEscape(e.shepherdName),
                    csvEscape(e.note),
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
                    put("title", entry.title)
                    put("amount", entry.amount)
                    put("livestockCount", entry.livestockCount)
                    put("shepherdName", entry.shepherdName)
                    put("note", entry.note)
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

    companion object {
        const val GOOGLE_APPS_SCRIPT_TEMPLATE = """function doPost(e) {
  var data = JSON.parse(e.postData.contents);
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName("FlockLedger") || ss.insertSheet("FlockLedger");
  sheet.clear();
  sheet.appendRow(["Date", "Category", "Title", "Amount (PKR)", "Sheep Count", "Shepherd", "Note", "Logged By"]);
  data.entries.forEach(function(row) {
    sheet.appendRow([row.date, row.category, row.title, row.amount, row.livestockCount, row.shepherdName, row.note, row.loggedBy]);
  });
  return ContentService.createTextOutput(JSON.stringify({status: "ok", rows: data.entries.length}))
    .setMimeType(ContentService.MimeType.JSON);
}"""
    }
}
