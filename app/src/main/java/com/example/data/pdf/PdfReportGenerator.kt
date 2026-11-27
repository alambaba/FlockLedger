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
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.model.ApprovalStatus
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
import kotlin.math.roundToLong

data class GeneratedPdfResult(
    val shareUri: Uri,
    val fileName: String,
    val savedToDownloads: Boolean,
    val internalFilePath: String = "",
)

fun printPdfFile(context: Context, file: File, jobName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            try {
                file.inputStream().use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            if (cancellationSignal?.isCanceled == true) {
                                callback.onWriteCancelled()
                                return
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
    val attrs = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()
    printManager.print(jobName, adapter, attrs)
}

class PdfReportGenerator(private val context: Context) {

    suspend fun generateSummaryPdf(
        farm: Farm,
        summary: FarmSummary,
        entries: List<LedgerEntry>,
        isSindhi: Boolean,
        includePending: Boolean = false,
        periodLabel: String = "",
    ): Result<GeneratedPdfResult> = withContext(Dispatchers.IO) {
        try {
            val renderer = PdfLayoutRenderer(
                farm = farm,
                summary = summary,
                entries = entries,
                isSindhi = isSindhi,
                includePending = includePending,
                periodLabel = periodLabel,
            )

            // Pass 1: compute exact total page count cleanly without creating PDF pages
            val totalPages = renderer.render(document = null, totalPagesKnown = 1)

            // Pass 2: draw final A4 PdfDocument with "Page X of Y" footers
            val document = PdfDocument()
            renderer.render(document = document, totalPagesKnown = totalPages)

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
            Result.success(
                GeneratedPdfResult(
                    shareUri = shareUri,
                    fileName = fileName,
                    savedToDownloads = savedToDownloads,
                    internalFilePath = internalFile.absolutePath,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private class PdfLayoutRenderer(
        private val farm: Farm,
        private val summary: FarmSummary,
        private val entries: List<LedgerEntry>,
        private val isSindhi: Boolean,
        private val includePending: Boolean,
        private val periodLabel: String,
    ) {
        private val pageWidth = 595 // A4 width in points
        private val pageHeight = 842 // A4 height in points
        private val margin = 32f
        private val contentWidth = pageWidth - margin * 2f
        private val footerTopY = pageHeight - 36f
        private val maxBodyY = footerTopY - 12f

        private val emeraldDark = Color.parseColor("#165634")
        private val emeraldTint = Color.parseColor("#EBF4EE")
        private val cardBg = Color.parseColor("#F6F8F6")
        private val tableHeaderBg = Color.parseColor("#1E4D33")
        private val altRowBg = Color.parseColor("#FAFBF9")
        private val borderGray = Color.parseColor("#D5DDD7")
        private val inkColor = Color.parseColor("#18201B")
        private val mutedColor = Color.parseColor("#546159")
        private val positiveGreen = Color.parseColor("#186A3B")
        private val negativeRed = Color.parseColor("#B03A2E")

        private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDark
            style = Paint.Style.FILL
        }
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCEFE2")
            textSize = 9.5f
        }
        private val sectionBannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldTint
            style = Paint.Style.FILL
        }
        private val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDark
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val subSectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = 9.2f
        }
        private val boldLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val valueRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = 9.2f
            textAlign = Paint.Align.RIGHT
        }
        private val boldValueRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = 9.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        private val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tableHeaderBg
            style = Paint.Style.FILL
        }
        private val tableHeaderTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val tableHeaderRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        private val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = 8f
        }
        private val cellRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = 8f
            textAlign = Paint.Align.RIGHT
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderGray
            strokeWidth = 0.8f
        }
        private val strongLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDark
            strokeWidth = 1.2f
        }
        private val boxBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBg
            style = Paint.Style.FILL
        }
        private val altRowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = altRowBg
            style = Paint.Style.FILL
        }
        private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedColor
            textSize = 8.2f
        }
        private val footerRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedColor
            textSize = 8.2f
            textAlign = Paint.Align.RIGHT
        }

        private val numFmt = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 0
        }
        private val generatedStamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())
        private val shortDateFmt = SimpleDateFormat("dd/MM/yy", Locale.US)

        private var canvas: Canvas? = null
        private var currentPage: PdfDocument.Page? = null
        private var pageNumber: Int = 1
        private var y: Float = 0f

        private fun formatPkr(value: Double): String = "Rs. ${numFmt.format(value.roundToLong())}"
        private fun formatPlainNum(value: Double): String = numFmt.format(value.roundToLong())

        fun render(document: PdfDocument?, totalPagesKnown: Int): Int {
            pageNumber = 1
            currentPage = document?.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            canvas = currentPage?.canvas

            fun drawFooterOnCurrentPage() {
                val c = canvas ?: return
                c.drawLine(margin, footerTopY, pageWidth - margin, footerTopY, linePaint)
                c.drawText(
                    "Generated: $generatedStamp  |  Current Flock: ${summary.currentFlockCount} sheep",
                    margin,
                    footerTopY + 14f,
                    footerPaint
                )
                c.drawText(
                    "Page $pageNumber of $totalPagesKnown",
                    pageWidth - margin,
                    footerTopY + 14f,
                    footerRightPaint
                )
            }

            fun startNewPage(): Float {
                drawFooterOnCurrentPage()
                if (document != null && currentPage != null) {
                    document.finishPage(currentPage)
                }
                pageNumber += 1
                currentPage = document?.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = currentPage?.canvas
                return 34f
            }

            fun ensureSpace(needed: Float, onNewPage: (() -> Unit)? = null) {
                if (y + needed > maxBodyY) {
                    y = startNewPage()
                    onNewPage?.invoke()
                }
            }

            fun drawTopBanner() {
                val bannerHeight = if (periodLabel.isNotBlank()) 98f else 84f
                canvas?.drawRect(0f, 0f, pageWidth.toFloat(), bannerHeight, headerPaint)
                val heading = farm.name.trim().ifEmpty { "LAAR SHEEP FARM" }.uppercase(Locale.US)
                val subtitle = if (isSindhi) {
                    "Financial & Livestock Management Report  |  مالي ۽ مال جي انتظامي رپورٽ"
                } else {
                    "Financial & Livestock Management Report"
                }
                canvas?.drawText(heading, margin, 32f, titlePaint)
                canvas?.drawText(subtitle, margin, 51f, subHeaderPaint)
                canvas?.drawText(
                    "Generated: $generatedStamp   •   Current Flock: ${summary.currentFlockCount} sheep",
                    margin,
                    69f,
                    subHeaderPaint
                )
                if (periodLabel.isNotBlank()) {
                    canvas?.drawText("Period / Filter: $periodLabel", margin, 85f, subHeaderPaint)
                }
                y = bannerHeight + 20f
            }

            fun drawSectionHeader(title: String) {
                ensureSpace(34f)
                canvas?.drawRect(margin, y - 14f, pageWidth - margin, y + 7f, sectionBannerPaint)
                canvas?.drawText(title, margin + 8f, y, sectionTitlePaint)
                y += 18f
            }

            fun drawKeyValue(
                label: String,
                value: String,
                bold: Boolean = false,
                valueColor: Int = inkColor,
                indent: Float = 8f,
            ) {
                val maxLabelWidth = (contentWidth - 135f - indent).toInt().coerceAtLeast(100)
                val paintToMeasure = if (bold) boldLabelPaint else labelPaint
                val layout = StaticLayout.Builder.obtain(
                    label, 0, label.length, paintToMeasure, maxLabelWidth
                ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
                val rowHeight = maxOf(16f, layout.height + 5f)
                ensureSpace(rowHeight)

                canvas?.let { c ->
                    c.save()
                    c.translate(margin + indent, y - 10f)
                    layout.draw(c)
                    c.restore()

                    val vp = Paint(if (bold) boldValueRightPaint else valueRightPaint).apply {
                        color = valueColor
                    }
                    c.drawText(value, pageWidth - margin - 8f, y, vp)
                }
                y += rowHeight
            }

            drawTopBanner()

            // =========================================================
            // SECTION 1 - FINANCIAL SUMMARY
            // =========================================================
            drawSectionHeader(
                if (isSindhi) "SECTION 1 – مالي خلاصو (Financial Summary)" else "SECTION 1 – Financial Summary"
            )
            drawKeyValue("Owner Capital (مالڪ کان موڙي)", formatPkr(summary.ownerFundsReceived))
            drawKeyValue("Total Sales Revenue (مال وڪرو آمدني)", formatPkr(summary.livestockSalesRevenue))
            drawKeyValue("Other Income (ٻي آمدني)", formatPkr(summary.otherIncomeTotal))
            drawKeyValue("Loans Received (ورتل قرض)", formatPkr(summary.loansTakenTotal))
            canvas?.drawLine(margin + 8f, y - 4f, pageWidth - margin - 8f, y - 4f, linePaint)
            y += 4f
            drawKeyValue("Total Cash Available (ڪل دستياب رقم)", formatPkr(summary.totalCashAvailable), bold = true, valueColor = positiveGreen)
            y += 4f
            drawKeyValue("Total Livestock Purchases (مال خريد)", formatPkr(summary.livestockBuyCost))
            drawKeyValue("Total Operating Expenses (آپريٽنگ خرچ)", formatPkr(summary.totalOperatingExpenses))
            drawKeyValue("  • Farm Expenses (Operating / فارم خرچ)", formatPkr(summary.operatingFarmExpenses), indent = 16f)
            if (summary.feedExpenses > 0.0) {
                drawKeyValue("  • Feed Expenses (چارو ۽ داڻو)", formatPkr(summary.feedExpenses), indent = 24f)
            }
            drawKeyValue("  • Medicine & Veterinary (دوائون ۽ علاج)", formatPkr(summary.medicineVetExpenses), indent = 16f)
            drawKeyValue("  • Shepherd Salaries (ڌراڙ پگھار)", formatPkr(summary.shepherdSalariesTotal), indent = 16f)
            drawKeyValue("Total Shepherd Advances (ڌراڙ ايڊوانس)", formatPkr(summary.shepherdAdvancesTotal))
            drawKeyValue("Total Loans Given (ڏنل قرض)", formatPkr(summary.loansGivenTotal))
            if (summary.loansRepaidTotal > 0.0) {
                drawKeyValue("Loans Repaid (واپس ڪيل قرض)", formatPkr(summary.loansRepaidTotal))
            }
            canvas?.drawLine(margin + 8f, y - 4f, pageWidth - margin - 8f, y - 4f, linePaint)
            y += 4f
            drawKeyValue("Total Cash Outflow (ڪل نقد ادائيگي)", formatPkr(summary.totalOutflows), bold = true, valueColor = negativeRed)
            drawKeyValue(
                "Closing Cash Balance (بچيل نقد رقم)",
                formatPkr(summary.remainingCashBalance),
                bold = true,
                valueColor = if (summary.remainingCashBalance >= 0) positiveGreen else negativeRed
            )
            y += 8f

            // =========================================================
            // SECTION 2 - ASSETS AND LIABILITIES (BALANCE SHEET)
            // =========================================================
            drawSectionHeader(
                if (isSindhi) "SECTION 2 – اثاثا ۽ ذميواريون (Assets, Liabilities & Equity)" else "SECTION 2 – Assets, Liabilities & Owner's Equity"
            )
            ensureSpace(18f)
            canvas?.drawText("Assets (اثاثا)", margin + 8f, y, subSectionPaint)
            y += 16f
            drawKeyValue("Cash in Hand (ھٿ ۾ نقد)", formatPkr(summary.remainingCashBalance), indent = 14f)
            drawKeyValue("Livestock Inventory Value (مال جي ماليت)", formatPkr(summary.livestockInventoryValue), indent = 14f)
            drawKeyValue("Loans Receivable (وٺڻو قرض)", formatPkr(summary.loansReceivableBalance), indent = 14f)
            if (summary.accountsReceivableBalance > 0.0) {
                drawKeyValue("Accounts Receivable (وڪرو اوڌر)", formatPkr(summary.accountsReceivableBalance), indent = 14f)
            }
            drawKeyValue("Machinery and Equipment (مشينري ۽ سامان)", formatPkr(summary.machineryEquipmentValue), indent = 14f)
            if (summary.shepherdAdvancesOutstanding > 0.0) {
                drawKeyValue("Outstanding Shepherd Advances (باقي ايڊوانس)", formatPkr(summary.shepherdAdvancesOutstanding), indent = 14f)
            }
            canvas?.drawLine(margin + 14f, y - 4f, pageWidth - margin - 8f, y - 4f, linePaint)
            y += 4f
            drawKeyValue("Total Assets (ڪل اثاثا)", formatPkr(summary.totalAssets), bold = true, valueColor = positiveGreen)
            y += 4f

            ensureSpace(18f)
            canvas?.drawText("Liabilities (ذميواريون / واجبات)", margin + 8f, y, subSectionPaint)
            y += 16f
            drawKeyValue("Outstanding Loans Payable (ڏيڻو قرض)", formatPkr(summary.loansPayableBalance), indent = 14f)
            drawKeyValue("Other Outstanding Payables (ٻيا واجبات / اوڌر)", formatPkr(summary.otherPayablesBalance), indent = 14f)
            drawKeyValue("Total Liabilities (ڪل ذميواريون)", formatPkr(summary.totalLiabilities), bold = true)
            y += 4f

            ensureSpace(18f)
            canvas?.drawText("Owner's Equity (مالڪ جو حصو)", margin + 8f, y, subSectionPaint)
            y += 16f
            drawKeyValue("Owner Capital (مالڪ جي موڙي)", formatPkr(summary.ownerFundsReceived), indent = 14f)
            if (summary.openingFlockEquity > 0.0) {
                drawKeyValue("Opening Flock Capital (شروعاتي مال)", formatPkr(summary.openingFlockEquity), indent = 14f)
            }
            drawKeyValue(
                "Retained Profit / Loss (خالص منافعو يا نقصان)",
                formatPkr(summary.retainedProfitLoss),
                indent = 14f,
                valueColor = if (summary.retainedProfitLoss >= 0) positiveGreen else negativeRed
            )
            drawKeyValue("Total Owner's Equity (مجموعي موڙي)", formatPkr(summary.totalOwnerEquity), bold = true)
            canvas?.drawLine(margin + 8f, y - 4f, pageWidth - margin - 8f, y - 4f, strongLinePaint)
            y += 5f
            drawKeyValue(
                "Liabilities + Owner's Equity (بيلنس چيڪ)",
                formatPkr(summary.totalLiabilities + summary.totalOwnerEquity),
                bold = true,
                valueColor = emeraldDark
            )
            y += 8f

            // =========================================================
            // SECTION 3 - LIVESTOCK INVENTORY
            // =========================================================
            drawSectionHeader(
                if (isSindhi) "SECTION 3 – مال جو تفصيل (Livestock Inventory)" else "SECTION 3 – Livestock Inventory"
            )
            drawKeyValue("Opening Sheep (شروعاتي رڍون)", "${summary.openingFlockCount}")
            drawKeyValue("Sheep Purchased (خريد ڪيل)", "+${summary.livestockBoughtCount}")
            drawKeyValue("Sheep Sold (وڪرو ڪيل)", "-${summary.livestockSoldCount}")
            drawKeyValue("Sheep Deaths (فوت ٿيل)", "-${summary.livestockDeathCount}")
            drawKeyValue("Current Sheep Count (موجوده رڍون)", "${summary.currentFlockCount} sheep", bold = true, valueColor = positiveGreen)
            drawKeyValue("Total Purchase Cost (خريداري قيمت)", formatPkr(summary.livestockBuyCost))
            drawKeyValue("Average Purchase Cost per Sheep (في رڍ سراسري قيمت)", formatPkr(summary.avgPurchaseCostPerSheep))
            drawKeyValue("Estimated Closing Inventory Value (موجوده مال جي ماليت)", formatPkr(summary.livestockInventoryValue), bold = true)
            y += 8f

            // =========================================================
            // SECTION 4 - SHEPHERD ACCOUNTING
            // =========================================================
            drawSectionHeader(
                if (isSindhi) "SECTION 4 – ڌراڙن جو حساب (Shepherd Accounting)" else "SECTION 4 – Shepherd Accounting"
            )
            val shCols = floatArrayOf(
                margin,
                margin + 128f,
                margin + 200f,
                margin + 272f,
                margin + 350f,
                margin + 432f,
                pageWidth - margin
            )
            fun drawShepherdHeader() {
                ensureSpace(22f)
                canvas?.drawRect(margin, y - 12f, pageWidth - margin, y + 6f, tableHeaderPaint)
                canvas?.drawText("Shepherd", shCols[0] + 4f, y, tableHeaderTextPaint)
                canvas?.drawText("Salary", shCols[1], y, tableHeaderRightPaint)
                canvas?.drawText("Advances", shCols[2], y, tableHeaderRightPaint)
                canvas?.drawText("Recovery", shCols[3], y, tableHeaderRightPaint)
                canvas?.drawText("Outst. Adv", shCols[4], y, tableHeaderRightPaint)
                canvas?.drawText("Sal. Paid", shCols[5], y, tableHeaderRightPaint)
                canvas?.drawText("Payable", shCols[6] - 4f, y, tableHeaderRightPaint)
                y += 16f
            }

            if (summary.shepherdBreakdown.isEmpty()) {
                drawKeyValue("No shepherd records available.", "-")
            } else {
                drawShepherdHeader()
                summary.shepherdBreakdown.forEachIndexed { idx, sh ->
                    val nameLayout = StaticLayout.Builder.obtain(
                        sh.shepherdName, 0, sh.shepherdName.length, cellPaint, 110
                    ).build()
                    val rh = maxOf(17f, nameLayout.height + 6f)
                    ensureSpace(rh, onNewPage = { drawShepherdHeader() })
                    if (idx % 2 == 1) {
                        canvas?.drawRect(margin, y - 11f, pageWidth - margin, y + rh - 11f, altRowPaint)
                    }
                    canvas?.let { c ->
                        c.save()
                        c.translate(shCols[0] + 4f, y - 9f)
                        nameLayout.draw(c)
                        c.restore()
                        c.drawText(formatPlainNum(sh.totalSalary), shCols[1], y, cellRightPaint)
                        c.drawText(formatPlainNum(sh.totalAdvance), shCols[2], y, cellRightPaint)
                        c.drawText(formatPlainNum(sh.advanceRecovery), shCols[3], y, cellRightPaint)
                        c.drawText(formatPlainNum(sh.outstandingAdvance), shCols[4], y, cellRightPaint)
                        c.drawText(formatPlainNum(sh.salaryPaid), shCols[5], y, cellRightPaint)
                        c.drawText(formatPlainNum(sh.salaryPayable), shCols[6] - 4f, y, cellRightPaint)
                        c.drawLine(margin, y + rh - 11f, pageWidth - margin, y + rh - 11f, linePaint)
                    }
                    y += rh
                }
            }
            y += 12f

            // =========================================================
            // SECTION 5 - APPROVED TRANSACTION LEDGER
            // =========================================================
            val approvedEntries = entries.filter { it.parsedApprovalStatus() == ApprovalStatus.APPROVED }
            val pendingEntries = entries.filter { it.parsedApprovalStatus() == ApprovalStatus.PENDING }

            drawLedgerTable(
                sectionTitle = if (isSindhi) {
                    "SECTION 5 – منظور ٿيل کاتو (${approvedEntries.size} Approved Transactions)"
                } else {
                    "SECTION 5 – Transaction Ledger (${approvedEntries.size} Approved)"
                },
                rows = approvedEntries,
                drawSectionHeader = ::drawSectionHeader,
                ensureSpace = ::ensureSpace,
                drawEmptyText = { drawKeyValue("No approved transactions.", "-") }
            )

            // =========================================================
            // OPTIONAL SECTION 6 - PENDING TRANSACTIONS SEPARATELY
            // =========================================================
            if (includePending && pendingEntries.isNotEmpty()) {
                y += 10f
                drawLedgerTable(
                    sectionTitle = if (isSindhi) {
                        "SECTION 6 – التوا هيٺ داخلائون (${pendingEntries.size} Pending — Excluded from Totals)"
                    } else {
                        "SECTION 6 – Pending Transactions (${pendingEntries.size} Excluded from Totals)"
                    },
                    rows = pendingEntries,
                    drawSectionHeader = ::drawSectionHeader,
                    ensureSpace = ::ensureSpace,
                    drawEmptyText = {}
                )
            }

            drawFooterOnCurrentPage()
            if (document != null && currentPage != null) {
                document.finishPage(currentPage)
            }
            return pageNumber
        }

        private fun drawLedgerTable(
            sectionTitle: String,
            rows: List<LedgerEntry>,
            drawSectionHeader: (String) -> Unit,
            ensureSpace: (Float, (() -> Unit)?) -> Unit,
            drawEmptyText: () -> Unit,
        ) {
            drawSectionHeader(sectionTitle)
            if (rows.isEmpty()) {
                drawEmptyText()
                return
            }

            val xDate = margin + 2f
            val xType = margin + 50f
            val xDesc = margin + 138f
            val xDebitRight = margin + 356f
            val xCreditRight = margin + 422f
            val xMethod = margin + 428f
            val xStatus = margin + 478f

            fun drawTableHeader() {
                ensureSpace(22f, null)
                canvas?.drawRect(margin, y - 12f, pageWidth - margin, y + 6f, tableHeaderPaint)
                canvas?.drawText("Date", xDate, y, tableHeaderTextPaint)
                canvas?.drawText("Type", xType, y, tableHeaderTextPaint)
                canvas?.drawText("Description", xDesc, y, tableHeaderTextPaint)
                canvas?.drawText("Debit", xDebitRight, y, tableHeaderRightPaint)
                canvas?.drawText("Credit", xCreditRight, y, tableHeaderRightPaint)
                canvas?.drawText("Method", xMethod, y, tableHeaderTextPaint)
                canvas?.drawText("Status", xStatus, y, tableHeaderTextPaint)
                y += 16f
            }

            drawTableHeader()

            rows.forEachIndexed { idx, entry ->
                val cat = entry.parsedCategory()
                val typeLabel = when (cat) {
                    EntryCategory.EXPENSE -> {
                        val sub = entry.parsedSubCategory()?.name?.lowercase()?.replace('_', ' ')
                        if (sub != null) "Expense ($sub)" else "Farm Expense"
                    }
                    EntryCategory.MEDICINE_VET -> "Med & Vet"
                    EntryCategory.LIVESTOCK_BUY -> "Buy Sheep (${entry.livestockCount})"
                    EntryCategory.LIVESTOCK_SELL -> "Sell Sheep (${entry.livestockCount})"
                    EntryCategory.LIVESTOCK_DEATH -> "Sheep Death (${entry.livestockCount})"
                    EntryCategory.SHEPHERD_ADVANCE -> "Adv (${entry.shepherdName})"
                    EntryCategory.SHEPHERD_ADVANCE_RECOVERY -> "Adv Recov (${entry.shepherdName})"
                    EntryCategory.SHEPHERD_SALARY -> "Salary (${entry.shepherdName})"
                    EntryCategory.OWNER_FUNDS -> "Owner Capital"
                    EntryCategory.OTHER_INCOME -> "Other Income"
                    EntryCategory.LOAN_TAKEN -> "Loan Taken"
                    EntryCategory.LOAN_GIVEN -> "Loan Given"
                    EntryCategory.LOAN_REPAY -> "Loan Repaid"
                    EntryCategory.LOAN_RECOVER -> "Loan Recov."
                }
                val descText = buildString {
                    append(entry.title.trim())
                    if (entry.shepherdName.isNotBlank() &&
                        cat != EntryCategory.SHEPHERD_ADVANCE &&
                        cat != EntryCategory.SHEPHERD_ADVANCE_RECOVERY &&
                        cat != EntryCategory.SHEPHERD_SALARY
                    ) {
                        append(" [${entry.shepherdName.trim()}]")
                    }
                    if (entry.note.isNotBlank()) {
                        append(" - ").append(entry.note.trim())
                    }
                }.ifEmpty { typeLabel }

                val typeLayout = StaticLayout.Builder.obtain(typeLabel, 0, typeLabel.length, cellPaint, 84).build()
                val descLayout = StaticLayout.Builder.obtain(descText, 0, descText.length, cellPaint, 146).build()
                val rh = maxOf(17f, maxOf(typeLayout.height, descLayout.height) + 6f)

                ensureSpace(rh) { drawTableHeader() }

                if (idx % 2 == 1) {
                    canvas?.drawRect(margin, y - 11f, pageWidth - margin, y + rh - 11f, altRowPaint)
                }

                val isCreditInflow = cat == EntryCategory.OWNER_FUNDS ||
                    cat == EntryCategory.LIVESTOCK_SELL ||
                    cat == EntryCategory.OTHER_INCOME ||
                    cat == EntryCategory.LOAN_TAKEN ||
                    cat == EntryCategory.LOAN_RECOVER ||
                    cat == EntryCategory.SHEPHERD_ADVANCE_RECOVERY
                val isDebitOutflow = cat != EntryCategory.LIVESTOCK_DEATH && !isCreditInflow

                val debitStr = if (isDebitOutflow && entry.amount > 0.0) formatPlainNum(entry.amount) else "-"
                val creditStr = if (isCreditInflow && entry.amount > 0.0) formatPlainNum(entry.amount) else "-"
                val dateStr = shortDateFmt.format(Date(entry.entryDateMillis))
                val methodStr = entry.parsedPaymentMethod().name
                val statusStr = entry.parsedApprovalStatus().name.lowercase().replaceFirstChar { it.uppercase() }

                canvas?.let { c ->
                    c.drawText(dateStr, xDate, y, cellPaint)
                    c.save()
                    c.translate(xType, y - 9f)
                    typeLayout.draw(c)
                    c.restore()

                    c.save()
                    c.translate(xDesc, y - 9f)
                    descLayout.draw(c)
                    c.restore()

                    val debitPaint = Paint(cellRightPaint).apply {
                        color = if (debitStr != "-") negativeRed else mutedColor
                    }
                    val creditPaint = Paint(cellRightPaint).apply {
                        color = if (creditStr != "-") positiveGreen else mutedColor
                    }
                    c.drawText(debitStr, xDebitRight, y, debitPaint)
                    c.drawText(creditStr, xCreditRight, y, creditPaint)
                    c.drawText(methodStr, xMethod, y, cellPaint)
                    c.drawText(statusStr, xStatus, y, cellPaint)
                    c.drawLine(margin, y + rh - 11f, pageWidth - margin, y + rh - 11f, linePaint)
                }
                y += rh
            }
        }
    }
}

