package com.example.data.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.EntryCategory
import com.example.data.model.Farm
import com.example.data.model.FarmSummary
import com.example.data.model.LedgerEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GeneratedPdfResult(
    val shareUri: Uri,
    val fileName: String,
    val savedToDownloads: Boolean,
)

class PdfReportGenerator(private val context: Context) {

    suspend fun generateSummaryPdf(
        farm: Farm,
        summary: FarmSummary,
        entries: List<LedgerEntry>,
        isSindhi: Boolean,
    ): Result<GeneratedPdfResult> = withContext(Dispatchers.IO) {
        try {
            val document = PdfDocument()
            val pageWidth = 595 // A4 points
            val pageHeight = 842
            val margin = 36f
            val contentWidth = pageWidth - margin * 2

            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#165634")
                style = Paint.Style.FILL
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#DCEFE2")
                textSize = 10.5f
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#165634")
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#222222")
                textSize = 10.5f
            }
            val boldValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#111111")
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#666666")
                textSize = 9.5f
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#DDDDDD")
                strokeWidth = 1f
            }
            val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F4F7F4")
                style = Paint.Style.FILL
            }

            val numFmt = NumberFormat.getNumberInstance(Locale.US).apply {
                maximumFractionDigits = 0
            }
            val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
            val shortDateFmt = SimpleDateFormat("dd/MM/yy", Locale.US)

            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas: Canvas = page.canvas

            // Top Banner
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 76f, headerPaint)
            val reportTitle = if (isSindhi) "${farm.name} - حساب جي سمري رپورٽ" else "${farm.name} - Summary Report"
            canvas.drawText(reportTitle, margin, 34f, titlePaint)
            canvas.drawText(
                "Generated: ${dateFmt.format(Date())} | Flock Count: ${summary.currentFlockCount} sheep",
                margin,
                56f,
                subHeaderPaint
            )

            var y = 102f

            fun ensureSpace(neededHeight: Float) {
                if (y + neededHeight > pageHeight - 40f) {
                    document.finishPage(page)
                    pageNumber += 1
                    page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    y = 44f
                }
            }

            fun drawKeyValueRow(label: String, value: String) {
                ensureSpace(20f)
                canvas.drawText(label, margin + 8f, y, labelPaint)
                canvas.drawText(value, pageWidth - margin - 8f, y, boldValuePaint)
                y += 18f
            }

            // Financial Summary Box
            canvas.drawRect(margin, y - 14f, pageWidth - margin, y + 186f, cardBgPaint)
            canvas.drawText(
                if (isSindhi) "مالي خلاصو (Financial Summary)" else "Financial Summary (PKR)",
                margin + 8f,
                y,
                sectionPaint
            )
            y += 22f
            drawKeyValueRow("Owner Funds Received (مالڪ کان مليل)", "PKR ${numFmt.format(summary.ownerFundsReceived)}")
            drawKeyValueRow("Livestock Sales Revenue (مال وڪرو)", "PKR ${numFmt.format(summary.livestockSalesRevenue)}")
            drawKeyValueRow("Farm Expenses (فارم جا خرچ)", "PKR ${numFmt.format(summary.generalExpenses)}")
            drawKeyValueRow("Medicine & Vet (دوائون ۽ علاج)", "PKR ${numFmt.format(summary.medicineVetExpenses)}")
            drawKeyValueRow("Livestock Purchases (مال خريد)", "PKR ${numFmt.format(summary.livestockBuyCost)}")
            drawKeyValueRow("Shepherd Advances (ڌراڙ ايڊوانس)", "PKR ${numFmt.format(summary.shepherdAdvancesTotal)}")
            drawKeyValueRow("Shepherd Salaries (ڌراڙ پگھار)", "PKR ${numFmt.format(summary.shepherdSalariesTotal)}")
            canvas.drawLine(margin + 8f, y - 8f, pageWidth - margin - 8f, y - 8f, linePaint)
            y += 6f
            drawKeyValueRow("Total Spent (ڪل خرچ)", "PKR ${numFmt.format(summary.totalOutflows)}")
            drawKeyValueRow("Remaining Balance (باقي بچيل)", "PKR ${numFmt.format(summary.remainingCashBalance)}")

            y += 20f
            ensureSpace(70f)
            canvas.drawText("Flock Inventory (مال جو تفصيل)", margin, y, sectionPaint)
            y += 20f
            drawKeyValueRow(
                "Initial: ${farm.initialFlockCount} | Bought: +${summary.livestockBoughtCount} | Sold: -${summary.livestockSoldCount}",
                "Current: ${summary.currentFlockCount} sheep"
            )

            if (summary.shepherdBreakdown.isNotEmpty()) {
                y += 14f
                ensureSpace(50f)
                canvas.drawText("Shepherds (ڌراڙن جو حساب)", margin, y, sectionPaint)
                y += 20f
                summary.shepherdBreakdown.forEach { sh ->
                    drawKeyValueRow(
                        "${sh.shepherdName} (${sh.entryCount} entries)",
                        "Adv: ${numFmt.format(sh.totalAdvance)} | Sal: ${numFmt.format(sh.totalSalary)}"
                    )
                }
            }

            y += 16f
            ensureSpace(50f)
            val approvedEntries = entries.filter { it.parsedApprovalStatus() == com.example.data.model.ApprovalStatus.APPROVED }
            canvas.drawText("Transactions Ledger (${approvedEntries.size} approved)", margin, y, sectionPaint)
            y += 18f
            canvas.drawLine(margin, y - 10f, pageWidth - margin, y - 10f, linePaint)

            approvedEntries.forEach { entry ->
                ensureSpace(34f)
                val catLabel = when (entry.parsedCategory()) {
                    EntryCategory.EXPENSE -> {
                        val sub = entry.parsedSubCategory()?.name?.lowercase()?.replace('_', ' ')
                        if (sub != null) "Farm Expense ($sub)" else "Farm Expense"
                    }
                    EntryCategory.MEDICINE_VET -> "Medicine/Vet"
                    EntryCategory.LIVESTOCK_BUY -> "Buy Sheep (${entry.livestockCount})"
                    EntryCategory.LIVESTOCK_SELL -> "Sell Sheep (${entry.livestockCount})"
                    EntryCategory.SHEPHERD_ADVANCE -> "Advance (${entry.shepherdName})"
                    EntryCategory.SHEPHERD_SALARY -> "Salary (${entry.shepherdName})"
                    EntryCategory.OWNER_FUNDS -> "Owner Funds"
                    EntryCategory.LOAN_TAKEN -> "Loan Taken (${entry.shepherdName})"
                    EntryCategory.LOAN_GIVEN -> "Loan Given (${entry.shepherdName})"
                    EntryCategory.LOAN_REPAY -> "Loan Repay (${entry.shepherdName})"
                    EntryCategory.LOAN_RECOVER -> "Loan Recover (${entry.shepherdName})"
                }
                val dateStr = shortDateFmt.format(Date(entry.entryDateMillis))
                canvas.drawText("$dateStr  [$catLabel]  ${entry.title.take(36)}", margin, y, labelPaint)
                canvas.drawText("PKR ${numFmt.format(entry.amount)}", pageWidth - margin, y, boldValuePaint)
                y += 13f
                if (entry.note.isNotBlank()) {
                    canvas.drawText("  ${entry.note.take(65)}", margin, y, mutedPaint)
                    y += 12f
                }
                canvas.drawLine(margin, y - 5f, pageWidth - margin, y - 5f, linePaint)
                y += 6f
            }

            document.finishPage(page)

            val safeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val fileName = "FlockLedger_Report_$safeStamp.pdf"

            val reportsDir = File(context.filesDir, "reports").apply { mkdirs() }
            val internalFile = File(reportsDir, fileName)
            FileOutputStream(internalFile).use { out ->
                document.writeTo(out)
            }

            var savedToDownloads = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FlockLedger")
                }
                val resolver = context.contentResolver
                val downloadUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (downloadUri != null) {
                    resolver.openOutputStream(downloadUri)?.use { out ->
                        internalFile.inputStream().use { input -> input.copyTo(out) }
                        savedToDownloads = true
                    }
                }
            }

            document.close()
            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                internalFile
            )
            Result.success(GeneratedPdfResult(shareUri, fileName, savedToDownloads))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
