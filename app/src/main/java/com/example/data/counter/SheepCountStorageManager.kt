package com.example.data.counter

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SheepCountRecord(
    val id: String,
    val farmId: String,
    val farmName: String,
    val countedSheep: Int,
    val expectedSheep: Int,
    val variance: Int,
    val note: String,
    val photoFilePath: String?,
    val timestampMillis: Long,
    val formattedDateTime: String,
    val internalResultFilePath: String,
    val resultFileName: String,
    val downloadsSaved: Boolean,
)

class SheepCountStorageManager(private val context: Context) {

    private fun sheepCountsDir(): File =
        File(context.filesDir, "sheep_counts").apply { mkdirs() }

    private fun historyFile(): File =
        File(sheepCountsDir(), HISTORY_JSON_FILE)

    suspend fun loadHistory(farmId: String? = null): List<SheepCountRecord> = withContext(Dispatchers.IO) {
        val file = historyFile()
        if (!file.exists()) return@withContext emptyList()
        return@withContext try {
            val raw = file.readText(Charsets.UTF_8)
            val arr = JSONArray(raw)
            val list = mutableListOf<SheepCountRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val record = SheepCountRecord(
                    id = obj.optString("id"),
                    farmId = obj.optString("farmId"),
                    farmName = obj.optString("farmName"),
                    countedSheep = obj.optInt("countedSheep", 0),
                    expectedSheep = obj.optInt("expectedSheep", 0),
                    variance = obj.optInt("variance", 0),
                    note = obj.optString("note", ""),
                    photoFilePath = obj.optString("photoFilePath", "").ifBlank { null },
                    timestampMillis = obj.optLong("timestampMillis", 0L),
                    formattedDateTime = obj.optString("formattedDateTime", ""),
                    internalResultFilePath = obj.optString("internalResultFilePath", ""),
                    resultFileName = obj.optString("resultFileName", ""),
                    downloadsSaved = obj.optBoolean("downloadsSaved", false),
                )
                if (farmId == null || record.farmId == farmId) {
                    list.add(record)
                }
            }
            list.sortedByDescending { it.timestampMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun copyCameraPhotoToPersistentFile(sourceUri: Uri, timestampMillis: Long = System.currentTimeMillis()): String? =
        withContext(Dispatchers.IO) {
            try {
                val dir = sheepCountsDir()
                val stamp = fileStamp(timestampMillis)
                val dest = File(dir, "sheep_photo_$stamp.jpg")
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    dest.outputStream().use { out ->
                        input.copyTo(out)
                    }
                } ?: return@withContext null
                dest.absolutePath
            } catch (_: Exception) {
                null
            }
        }

    suspend fun saveCountResultToFile(
        farmId: String,
        farmName: String,
        countedSheep: Int,
        expectedSheep: Int,
        note: String,
        photoFilePath: String?,
        recordedBy: String,
        timestampMillis: Long = System.currentTimeMillis(),
    ): Result<SheepCountRecord> = withContext(Dispatchers.IO) {
        try {
            val dir = sheepCountsDir()
            val stamp = fileStamp(timestampMillis)
            val dateTimeText = displayDateTime(timestampMillis)
            val variance = countedSheep - expectedSheep
            val fileName = "SheepCount_${stamp}.txt"
            val resultFile = File(dir, fileName)

            val content = formatResultFileText(
                farmName = farmName,
                countedSheep = countedSheep,
                expectedSheep = expectedSheep,
                variance = variance,
                note = note.trim(),
                photoFileName = photoFilePath?.let { File(it).name },
                recordedBy = recordedBy,
                formattedDateTime = dateTimeText,
            )
            resultFile.writeText(content, Charsets.UTF_8)

            // Also append a CSV line to cumulative sheep_count_log.csv
            val csvFile = File(dir, CUMULATIVE_CSV_FILE)
            val needHeader = !csvFile.exists()
            val csvLine = buildString {
                if (needHeader) {
                    appendLine("DateTime,FarmName,CountedSheep,ExpectedSheep,Variance,RecordedBy,Note,PhotoFile,ResultFile")
                }
                appendLine(
                    listOf(
                        csvCell(dateTimeText),
                        csvCell(farmName),
                        countedSheep.toString(),
                        expectedSheep.toString(),
                        variance.toString(),
                        csvCell(recordedBy),
                        csvCell(note.trim()),
                        csvCell(photoFilePath?.let { File(it).name } ?: ""),
                        csvCell(fileName),
                    ).joinToString(",")
                )
            }
            csvFile.appendText(csvLine, Charsets.UTF_8)

            val downloadsSaved = writeTextFileToDownloads(fileName, content)

            val record = SheepCountRecord(
                id = "cnt_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                farmId = farmId,
                farmName = farmName,
                countedSheep = countedSheep,
                expectedSheep = expectedSheep,
                variance = variance,
                note = note.trim(),
                photoFilePath = photoFilePath,
                timestampMillis = timestampMillis,
                formattedDateTime = dateTimeText,
                internalResultFilePath = resultFile.absolutePath,
                resultFileName = fileName,
                downloadsSaved = downloadsSaved,
            )

            // Save in history JSON
            val all = loadHistory(farmId = null).toMutableList()
            all.add(0, record)
            val arr = JSONArray()
            all.take(100).forEach { r ->
                arr.put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("farmId", r.farmId)
                        put("farmName", r.farmName)
                        put("countedSheep", r.countedSheep)
                        put("expectedSheep", r.expectedSheep)
                        put("variance", r.variance)
                        put("note", r.note)
                        if (!r.photoFilePath.isNullOrBlank()) put("photoFilePath", r.photoFilePath)
                        put("timestampMillis", r.timestampMillis)
                        put("formattedDateTime", r.formattedDateTime)
                        put("internalResultFilePath", r.internalResultFilePath)
                        put("resultFileName", r.resultFileName)
                        put("downloadsSaved", r.downloadsSaved)
                    }
                )
            }
            historyFile().writeText(arr.toString(2), Charsets.UTF_8)

            Result.success(record)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getShareUri(filePath: String): Uri? {
        val file = File(filePath)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun writeTextFileToDownloads(fileName: String, content: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/FlockLedger")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun csvCell(raw: String): String =
        "\"${raw.replace("\"", "\"\"").replace("\n", " ")}\""

    companion object {
        const val HISTORY_JSON_FILE = "sheep_count_history.json"
        const val CUMULATIVE_CSV_FILE = "sheep_count_log.csv"

        fun fileStamp(timestampMillis: Long): String =
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(timestampMillis))

        fun displayDateTime(timestampMillis: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestampMillis))

        fun formatResultFileText(
            farmName: String,
            countedSheep: Int,
            expectedSheep: Int,
            variance: Int,
            note: String,
            photoFileName: String?,
            recordedBy: String,
            formattedDateTime: String,
        ): String {
            val varianceSign = if (variance > 0) "+$variance" else variance.toString()
            return buildString {
                appendLine("========================================")
                appendLine("       FLOCKLEDGER SHEEP COUNT REPORT   ")
                appendLine("========================================")
                appendLine("Date & Time    : $formattedDateTime")
                appendLine("Farm Name      : ${farmName.ifBlank { "My Sheep Farm" }}")
                appendLine("Recorded By    : $recordedBy")
                appendLine("----------------------------------------")
                appendLine("Counted Sheep  : $countedSheep")
                appendLine("Book Flock     : $expectedSheep")
                appendLine("Difference     : $varianceSign")
                if (note.isNotBlank()) {
                    appendLine("Note / Pen     : $note")
                }
                if (!photoFileName.isNullOrBlank()) {
                    appendLine("Camera Photo   : $photoFileName")
                }
                appendLine("========================================")
            }
        }
    }
}
