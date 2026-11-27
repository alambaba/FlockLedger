package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CommentStatus
import com.example.data.model.EntryCategory
import com.example.data.model.ExpenseSubCategory
import com.example.data.model.Farm
import com.example.data.model.FarmSummary
import com.example.data.model.LedgerEntry
import com.example.data.model.ReportComment
import com.example.data.model.ReportFilterMode
import com.example.data.model.computeCashRunningBalances
import com.example.data.model.computeFarmSummary
import com.example.data.model.filterLedgerEntriesForReport
import com.example.i18n.AppStrings
import com.example.ui.components.LedgerEntryCard
import com.example.ui.components.categoryLabel
import com.example.ui.components.formatCurrency
import com.example.ui.components.subCategoryLabel
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.LivestockBuyTeal
import com.example.ui.theme.LivestockSellGreen
import com.example.ui.theme.PendingAmber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
    farm: Farm,
    entries: List<LedgerEntry>,
    reportComments: List<ReportComment>,
    isAdminView: Boolean,
    strings: AppStrings,
    onExportPdf: (filteredEntries: List<LedgerEntry>, filteredSummary: FarmSummary, includePending: Boolean, periodLabel: String) -> Unit,
    onPrintA4: (filteredEntries: List<LedgerEntry>, filteredSummary: FarmSummary, includePending: Boolean, periodLabel: String) -> Unit,
    onEditEntry: (LedgerEntry) -> Unit,
    onViewReceipt: (LedgerEntry) -> Unit,
    onSubmitComment: (entryId: String?, entryTitleSnapshot: String, commentText: String) -> Unit,
    onResolveComment: (commentId: String, adminResponse: String) -> Unit,
    onDeleteComment: (commentId: String) -> Unit,
) {
    val initCal = remember { Calendar.getInstance() }
    var filterMode by remember { mutableStateOf(ReportFilterMode.MONTHLY) }
    var selectedYear by remember { mutableIntStateOf(initCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(initCal.get(Calendar.MONTH)) }
    var selectedCategory by remember { mutableStateOf<EntryCategory?>(null) }
    var selectedSubCategory by remember { mutableStateOf<ExpenseSubCategory?>(null) }
    var customStartMillis by remember {
        mutableLongStateOf(
            Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis
        )
    }
    var customEndMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var includePending by remember { mutableStateOf(true) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // Comment composer state
    var linkedEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var commentDraft by remember { mutableStateOf("") }
    var quickCommentTarget by remember { mutableStateOf<LedgerEntry?>(null) }
    var quickCommentDraft by remember { mutableStateOf("") }
    var resolvingCommentTarget by remember { mutableStateOf<ReportComment?>(null) }
    var adminReplyDraft by remember { mutableStateOf("") }

    val shortDateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }

    val periodLabel = remember(
        filterMode,
        selectedYear,
        selectedMonth,
        selectedCategory,
        selectedSubCategory,
        customStartMillis,
        customEndMillis,
        strings,
    ) {
        when (filterMode) {
            ReportFilterMode.MONTHLY -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                monthFmt.format(cal.time)
            }
            ReportFilterMode.YEARLY -> "${strings.filterYearly}: $selectedYear"
            ReportFilterMode.CATEGORY -> {
                val catName = selectedCategory?.let { categoryLabel(it, strings) } ?: strings.catAll
                if (selectedCategory == EntryCategory.EXPENSE && selectedSubCategory != null) {
                    "$catName (${subCategoryLabel(selectedSubCategory!!, strings)})"
                } else {
                    catName
                }
            }
            ReportFilterMode.CUSTOM -> "${shortDateFmt.format(Date(customStartMillis))} - ${shortDateFmt.format(Date(customEndMillis))}"
        }
    }

    val filteredEntries = remember(
        entries,
        filterMode,
        selectedYear,
        selectedMonth,
        selectedCategory,
        selectedSubCategory,
        customStartMillis,
        customEndMillis,
        includePending,
    ) {
        filterLedgerEntriesForReport(
            entries = entries,
            mode = filterMode,
            year = selectedYear,
            monthZeroBased = selectedMonth,
            filterCategory = selectedCategory,
            filterSubCategory = selectedSubCategory,
            customStartMillis = customStartMillis,
            customEndMillis = customEndMillis,
            includePending = includePending,
        )
    }

    val filteredSummary = remember(farm, filteredEntries) {
        computeFarmSummary(farm, filteredEntries)
    }
    val runningBalances = remember(entries) {
        computeCashRunningBalances(entries)
    }
    val openCommentsCount = remember(reportComments) {
        reportComments.count { it.parsedStatus() == CommentStatus.OPEN }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reports_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header + Filter Mode Selector + A4 Print / PDF Actions
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.reportsPageTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = String.format(strings.filteredCountLabel, filteredEntries.size),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // 4 Filter Mode Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterMode == ReportFilterMode.MONTHLY,
                            onClick = { filterMode = ReportFilterMode.MONTHLY },
                            label = { Text(strings.filterMonthly, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("report_filter_monthly")
                        )
                        FilterChip(
                            selected = filterMode == ReportFilterMode.YEARLY,
                            onClick = { filterMode = ReportFilterMode.YEARLY },
                            label = { Text(strings.filterYearly, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("report_filter_yearly")
                        )
                        FilterChip(
                            selected = filterMode == ReportFilterMode.CATEGORY,
                            onClick = { filterMode = ReportFilterMode.CATEGORY },
                            label = { Text(strings.filterCategories, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("report_filter_category")
                        )
                        FilterChip(
                            selected = filterMode == ReportFilterMode.CUSTOM,
                            onClick = { filterMode = ReportFilterMode.CUSTOM },
                            label = { Text(strings.filterCustom, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("report_filter_custom")
                        )
                    }

                    // Mode-specific controls
                    when (filterMode) {
                        ReportFilterMode.MONTHLY -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (selectedMonth == 0) {
                                            selectedMonth = 11
                                            selectedYear -= 1
                                        } else {
                                            selectedMonth -= 1
                                        }
                                    },
                                    modifier = Modifier.testTag("report_prev_month")
                                ) {
                                    Icon(Icons.Rounded.ChevronLeft, contentDescription = strings.previousMonthAction)
                                }
                                Text(
                                    text = periodLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = {
                                        if (selectedMonth == 11) {
                                            selectedMonth = 0
                                            selectedYear += 1
                                        } else {
                                            selectedMonth += 1
                                        }
                                    },
                                    modifier = Modifier.testTag("report_next_month")
                                ) {
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = strings.nextMonthAction)
                                }
                            }
                        }

                        ReportFilterMode.YEARLY -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { selectedYear -= 1 },
                                    modifier = Modifier.testTag("report_prev_year")
                                ) {
                                    Icon(Icons.Rounded.ChevronLeft, contentDescription = strings.previousMonthAction)
                                }
                                Text(
                                    text = selectedYear.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { selectedYear += 1 },
                                    modifier = Modifier.testTag("report_next_year")
                                ) {
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = strings.nextMonthAction)
                                }
                            }
                        }

                        ReportFilterMode.CATEGORY -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedCategory == null,
                                        onClick = {
                                            selectedCategory = null
                                            selectedSubCategory = null
                                        },
                                        label = { Text(strings.catAll) }
                                    )
                                    EntryCategory.entries.forEach { cat ->
                                        FilterChip(
                                            selected = selectedCategory == cat,
                                            onClick = {
                                                selectedCategory = if (selectedCategory == cat) null else cat
                                                if (selectedCategory != EntryCategory.EXPENSE) {
                                                    selectedSubCategory = null
                                                }
                                            },
                                            label = { Text(categoryLabel(cat, strings)) }
                                        )
                                    }
                                }
                                if (selectedCategory == EntryCategory.EXPENSE) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = selectedSubCategory == null,
                                            onClick = { selectedSubCategory = null },
                                            label = { Text(strings.catAll) }
                                        )
                                        ExpenseSubCategory.entries.forEach { sub ->
                                            FilterChip(
                                                selected = selectedSubCategory == sub,
                                                onClick = {
                                                    selectedSubCategory = if (selectedSubCategory == sub) null else sub
                                                },
                                                label = { Text(subCategoryLabel(sub, strings)) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        ReportFilterMode.CUSTOM -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showStartPicker = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("report_custom_start_button")
                                ) {
                                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("${strings.startDateLabel}: ${shortDateFmt.format(Date(customStartMillis))}", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { showEndPicker = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("report_custom_end_button")
                                ) {
                                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("${strings.endDateLabel}: ${shortDateFmt.format(Date(customEndMillis))}", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.includePendingPdfLabel,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Switch(
                            checked = includePending,
                            onCheckedChange = { includePending = it },
                            modifier = Modifier.testTag("report_include_pending_switch")
                        )
                    }

                    // Highlighted Bold A4 Print & PDF Export Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onPrintA4(filteredEntries, filteredSummary, includePending, periodLabel)
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .testTag("report_print_a4_button")
                        ) {
                            Icon(Icons.Rounded.Print, contentDescription = strings.printA4Action, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(strings.printA4Action, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                onExportPdf(filteredEntries, filteredSummary, includePending, periodLabel)
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .testTag("report_export_pdf_button")
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = strings.downloadPdfReport, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(strings.generatePdfAction, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Filtered Summary Metrics
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionStatChip(
                            label = strings.totalCashAvailableLabel,
                            value = formatCurrency(filteredSummary.totalCashAvailable, strings),
                            color = LivestockSellGreen,
                            modifier = Modifier.weight(1f)
                        )
                        SectionStatChip(
                            label = strings.totalSpent,
                            value = formatCurrency(filteredSummary.totalOutflows, strings),
                            color = ExpenseRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionStatChip(
                            label = strings.kpiOperatingExpenses,
                            value = formatCurrency(filteredSummary.totalOperatingExpenses, strings),
                            color = ExpenseRed,
                            modifier = Modifier.weight(1f)
                        )
                        SectionStatChip(
                            label = strings.kpiTotalSales,
                            value = formatCurrency(filteredSummary.livestockSalesRevenue, strings),
                            color = LivestockBuyTeal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Report Comments & Error Correction Workflow
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Comment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = strings.reportCommentsHeading,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (openCommentsCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = PendingAmber.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = String.format(strings.openCommentsCountLabel, openCommentsCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PendingAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Entry Picker Chips for Linked Error Comments
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = strings.selectEntryOptional,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = linkedEntry == null,
                                onClick = { linkedEntry = null },
                                label = { Text(strings.generalReportComment, fontSize = 12.sp) }
                            )
                            filteredEntries.take(12).forEach { candidate ->
                                FilterChip(
                                    selected = linkedEntry?.id == candidate.id,
                                    onClick = {
                                        linkedEntry = if (linkedEntry?.id == candidate.id) null else candidate
                                    },
                                    label = { Text(candidate.title.take(24), fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = commentDraft,
                        onValueChange = { commentDraft = it },
                        placeholder = { Text(strings.commentPlaceholder) },
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("report_comment_input")
                    )

                    Button(
                        onClick = {
                            if (commentDraft.isNotBlank()) {
                                onSubmitComment(
                                    linkedEntry?.id,
                                    linkedEntry?.title ?: "",
                                    commentDraft.trim()
                                )
                                commentDraft = ""
                                linkedEntry = null
                            }
                        },
                        enabled = commentDraft.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("submit_report_comment_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(strings.sendCommentAction, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (reportComments.isEmpty()) {
            item {
                Text(
                    text = strings.noCommentsYet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .padding(horizontal = 4.dp)
                )
            }
        } else {
            items(reportComments, key = { "comment_${it.id}" }) { comment ->
                val isOpen = comment.parsedStatus() == CommentStatus.OPEN
                val targetEntry = remember(entries, comment.entryId) {
                    comment.entryId?.let { id -> entries.firstOrNull { it.id == id } }
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOpen) {
                            PendingAmber.copy(alpha = 0.10f)
                        } else {
                            LivestockSellGreen.copy(alpha = 0.08f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = (if (isOpen) PendingAmber else LivestockSellGreen).copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = if (isOpen) strings.pendingApprovalBadge else strings.commentResolvedBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOpen) PendingAmber else LivestockSellGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "${comment.authorRole} • ${shortDateFmt.format(Date(comment.createdAtMillis))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isAdminView) {
                                IconButton(
                                    onClick = { onDeleteComment(comment.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = strings.deleteEntry, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        if (comment.entryTitleSnapshot.isNotBlank()) {
                            Text(
                                text = "→ ${comment.entryTitleSnapshot}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = comment.commentText,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (comment.adminResponse.isNotBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            Text(
                                text = "Admin: ${comment.adminResponse}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = LivestockSellGreen
                            )
                        }

                        if (isAdminView && isOpen) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (targetEntry != null) {
                                    OutlinedButton(
                                        onClick = { onEditEntry(targetEntry) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("comment_edit_entry_${comment.id}")
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(4.dp))
                                        Text(strings.editFlaggedEntryAction, fontSize = 12.sp)
                                    }
                                }
                                Button(
                                    onClick = {
                                        adminReplyDraft = ""
                                        resolvingCommentTarget = comment
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LivestockSellGreen,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("comment_resolve_${comment.id}")
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text(strings.markResolvedAction, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Filtered Ledger Section
        item {
            Text(
                text = strings.recentEntries,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .padding(top = 4.dp)
            )
        }

        if (filteredEntries.isEmpty()) {
            item { EmptyLedgerCard(strings = strings, modifier = Modifier.widthIn(max = 640.dp)) }
        } else {
            items(filteredEntries, key = { "rep_${it.id}" }) { entry ->
                val runBal = runningBalances[entry.id]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LedgerEntryCard(
                        entry = entry,
                        strings = strings,
                        canEdit = isAdminView,
                        onEditClick = { onEditEntry(entry) },
                        onReceiptClick = { onViewReceipt(entry) },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (runBal != null) {
                            Text(
                                text = "${strings.runningBalanceLabel}: ${formatCurrency(runBal, strings)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(
                            onClick = {
                                linkedEntry = entry
                                quickCommentDraft = ""
                                quickCommentTarget = entry
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("comment_on_entry_${entry.id}")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Comment, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(strings.sendCommentAction, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customStartMillis)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { customStartMillis = it }
                    showStartPicker = false
                }) { Text(strings.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text(strings.cancel) }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customEndMillis)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { customEndMillis = it }
                    showEndPicker = false
                }) { Text(strings.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text(strings.cancel) }
            }
        ) {
            DatePicker(state = state)
        }
    }

    val resolveTarget = resolvingCommentTarget
    if (resolveTarget != null) {
        AlertDialog(
            onDismissRequest = { resolvingCommentTarget = null },
            title = { Text(strings.markResolvedAction, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(resolveTarget.commentText, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = adminReplyDraft,
                        onValueChange = { adminReplyDraft = it },
                        label = { Text(strings.adminReplyHint) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onResolveComment(resolveTarget.id, adminReplyDraft.trim())
                    resolvingCommentTarget = null
                }) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { resolvingCommentTarget = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    val entryToComment = quickCommentTarget
    if (entryToComment != null) {
        AlertDialog(
            onDismissRequest = { quickCommentTarget = null },
            title = { Text(strings.sendCommentAction, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "→ ${entryToComment.title}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = quickCommentDraft,
                        onValueChange = { quickCommentDraft = it },
                        placeholder = { Text(strings.commentPlaceholder) },
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_comment_dialog_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = quickCommentDraft.isNotBlank(),
                    onClick = {
                        onSubmitComment(entryToComment.id, entryToComment.title, quickCommentDraft.trim())
                        quickCommentDraft = ""
                        quickCommentTarget = null
                    },
                    modifier = Modifier.testTag("quick_comment_dialog_submit")
                ) {
                    Text(strings.sendCommentAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { quickCommentTarget = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
