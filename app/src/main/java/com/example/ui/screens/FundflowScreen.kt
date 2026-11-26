package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.backup.AvailableAppUpdate
import com.example.data.model.ApprovalStatus
import com.example.data.model.EntryCategory
import com.example.data.model.ExpenseSubCategory
import com.example.data.model.Farm
import com.example.data.model.FarmSummary
import com.example.data.model.LedgerEntry
import com.example.i18n.AppStrings
import com.example.ui.components.LedgerEntryCard
import com.example.ui.components.formatCurrency
import com.example.ui.components.subCategoryLabel
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.LivestockBuyTeal
import com.example.ui.theme.LivestockSellGreen
import com.example.ui.theme.LoanTakenAmber
import com.example.ui.theme.MedicineBlue
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.ShepherdAdvancePurple
import com.example.ui.theme.ShepherdSalaryIndigo
import com.example.ui.viewmodel.PostUpdateBanner

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FundflowScreen(
    farm: Farm,
    entries: List<LedgerEntry>,
    summary: FarmSummary,
    entriesSinceBackup: Int,
    isAdminView: Boolean,
    strings: AppStrings,
    postUpdateBanner: PostUpdateBanner?,
    availableUpdate: AvailableAppUpdate?,
    onQuickAddCategory: (EntryCategory) -> Unit,
    onQuickAddFarmSubCategory: (ExpenseSubCategory) -> Unit,
    onEditEntry: (LedgerEntry) -> Unit,
    onApproveEntry: (String) -> Unit,
    onRejectEntry: (String) -> Unit,
    onViewReceipt: (LedgerEntry) -> Unit,
    onDownloadPdf: () -> Unit,
    onOpenBackupSettings: () -> Unit,
    onOpenLoansPage: () -> Unit,
    onSaveSafetyBackupFile: () -> Unit,
    onRestoreBackupFile: () -> Unit,
    onDismissPostUpdateBanner: () -> Unit,
    onDownloadAvailableUpdate: (String) -> Unit,
) {
    if (!isAdminView) {
        CaretakerRestrictedDashboard(
            farmName = farm.name,
            entries = entries,
            strings = strings,
            postUpdateBanner = postUpdateBanner,
            availableUpdate = availableUpdate,
            onQuickAddCategory = onQuickAddCategory,
            onQuickAddFarmSubCategory = onQuickAddFarmSubCategory,
            onViewReceipt = onViewReceipt,
            onSaveSafetyBackupFile = onSaveSafetyBackupFile,
            onDismissPostUpdateBanner = onDismissPostUpdateBanner,
            onDownloadAvailableUpdate = onDownloadAvailableUpdate,
        )
        return
    }

    val pendingEntries = remember(entries) {
        entries.filter { it.parsedApprovalStatus() == ApprovalStatus.PENDING }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (availableUpdate != null) {
            item {
                AvailableUpdateBannerCard(
                    update = availableUpdate,
                    strings = strings,
                    onUpdateClick = { onDownloadAvailableUpdate(availableUpdate.downloadUrl) },
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        }

        if (postUpdateBanner != null) {
            item {
                PostUpdateSafeBannerCard(
                    banner = postUpdateBanner,
                    strings = strings,
                    onSaveBackupClick = onSaveSafetyBackupFile,
                    onDismiss = onDismissPostUpdateBanner,
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        }

        // Adaptive Hero Card — wraps content naturally so numbers and labels are NEVER clipped
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_flock_hero),
                        contentDescription = farm.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.48f),
                                        Color.Black.copy(alpha = 0.84f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = farm.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.size(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.45f)
                            ) {
                                Text(
                                    text = "${strings.currentFlock}: ${summary.currentFlockCount} ${strings.sheepUnit}",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = strings.remainingBalance,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = Color.White.copy(alpha = 0.88f)
                            )
                            Text(
                                text = formatCurrency(summary.remainingCashBalance, strings),
                                fontSize = 28.sp,
                                lineHeight = 36.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 2-column metric grid — ample room so full PKR amounts never truncate with ...
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HeroMetricCard(
                                label = strings.ownerFundsReceived,
                                value = formatCurrency(summary.ownerFundsReceived, strings),
                                modifier = Modifier.weight(1f)
                            )
                            HeroMetricCard(
                                label = strings.totalSpent,
                                value = formatCurrency(summary.totalOutflows, strings),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HeroMetricCard(
                                label = strings.livestockSales,
                                value = formatCurrency(summary.livestockSalesRevenue, strings),
                                modifier = Modifier.weight(1f)
                            )
                            HeroMetricCard(
                                label = strings.navLoans,
                                value = formatCurrency(summary.loansPayableBalance, strings),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = onOpenLoansPage)
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons — well-sized with multi-line friendly labels (no "..." truncation!)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDownloadPdf,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fundflow_pdf_button")
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = strings.downloadPdfReport, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.downloadPdfReport, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    OutlinedButton(
                        onClick = onOpenBackupSettings,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fundflow_backup_button")
                    ) {
                        Icon(Icons.Rounded.CloudDone, contentDescription = strings.backupSettingsMenu, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = String.format(strings.autoBackupCounter, entriesSinceBackup.coerceAtMost(5)),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onQuickAddCategory(EntryCategory.OWNER_FUNDS) },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fundflow_owner_funds_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.quickOwnerFunds, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    OutlinedButton(
                        onClick = onOpenLoansPage,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fundflow_loans_button")
                    ) {
                        Icon(Icons.Rounded.AccountBalance, contentDescription = strings.navLoans, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.navLoans, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }

        // Admin Pending Approval Queue
        if (pendingEntries.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = PendingAmber.copy(alpha = 0.14f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.PendingActions, contentDescription = null, tint = PendingAmber)
                        Text(
                            text = String.format(strings.pendingApprovalsHeading, pendingEntries.size),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PendingAmber
                        )
                    }
                }
            }
            items(pendingEntries, key = { "pending_${it.id}" }) { entry ->
                LedgerEntryCard(
                    entry = entry,
                    strings = strings,
                    canEdit = true,
                    onEditClick = { onEditEntry(entry) },
                    onReceiptClick = { onViewReceipt(entry) },
                    onApproveClick = { onApproveEntry(entry.id) },
                    onRejectClick = { onRejectEntry(entry.id) },
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        }

        // Category Flow Breakdown
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val maxVal = maxOf(
                        summary.generalExpenses,
                        summary.medicineVetExpenses,
                        summary.livestockBuyCost,
                        summary.shepherdAdvancesTotal,
                        summary.shepherdSalariesTotal,
                        summary.loansTakenTotal,
                        1.0
                    )
                    SectionBreakdownBar(strings.generalExpenses, summary.generalExpenses, maxVal, ExpenseRed, strings)
                    val nonZeroSubs = ExpenseSubCategory.entries.filter {
                        (summary.farmExpenseSubTotals[it] ?: 0.0) > 0.0
                    }
                    if (nonZeroSubs.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            nonZeroSubs.forEach { sub ->
                                val amt = summary.farmExpenseSubTotals[sub] ?: 0.0
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = ExpenseRed.copy(alpha = 0.09f)
                                ) {
                                    Text(
                                        text = "${subCategoryLabel(sub, strings)}: ${formatCurrency(amt, strings)}",
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ExpenseRed,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    SectionBreakdownBar(strings.medicineAndVet, summary.medicineVetExpenses, maxVal, MedicineBlue, strings)
                    SectionBreakdownBar(strings.livestockBought, summary.livestockBuyCost, maxVal, LivestockBuyTeal, strings)
                    SectionBreakdownBar(strings.shepherdAdvances, summary.shepherdAdvancesTotal, maxVal, ShepherdAdvancePurple, strings)
                    SectionBreakdownBar(strings.shepherdSalaries, summary.shepherdSalariesTotal, maxVal, ShepherdSalaryIndigo, strings)
                    if (summary.loansPayableBalance > 0.0) {
                        SectionBreakdownBar(strings.loansPayableTitle, summary.loansPayableBalance, maxVal, LoanTakenAmber, strings)
                    }
                }
            }
        }

        item {
            Text(
                text = strings.recentEntries,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            )
        }

        if (entries.isEmpty()) {
            item { EmptyLedgerCard(strings = strings, modifier = Modifier.widthIn(max = 640.dp)) }
            item {
                OutlinedButton(
                    onClick = onRestoreBackupFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .testTag("empty_restore_backup_button")
                ) {
                    Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(strings.restoreBackupFile)
                }
            }
        } else {
            items(entries.take(10), key = { it.id }) { entry ->
                LedgerEntryCard(
                    entry = entry,
                    strings = strings,
                    canEdit = true,
                    onEditClick = { onEditEntry(entry) },
                    onReceiptClick = { onViewReceipt(entry) },
                    onApproveClick = if (entry.parsedApprovalStatus() == ApprovalStatus.PENDING) {
                        { onApproveEntry(entry.id) }
                    } else null,
                    onRejectClick = if (entry.parsedApprovalStatus() == ApprovalStatus.PENDING) {
                        { onRejectEntry(entry.id) }
                    } else null,
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaretakerRestrictedDashboard(
    farmName: String,
    entries: List<LedgerEntry>,
    strings: AppStrings,
    postUpdateBanner: PostUpdateBanner?,
    availableUpdate: AvailableAppUpdate?,
    onQuickAddCategory: (EntryCategory) -> Unit,
    onQuickAddFarmSubCategory: (ExpenseSubCategory) -> Unit,
    onViewReceipt: (LedgerEntry) -> Unit,
    onSaveSafetyBackupFile: () -> Unit,
    onDismissPostUpdateBanner: () -> Unit,
    onDownloadAvailableUpdate: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (availableUpdate != null) {
            item {
                AvailableUpdateBannerCard(
                    update = availableUpdate,
                    strings = strings,
                    onUpdateClick = { onDownloadAvailableUpdate(availableUpdate.downloadUrl) },
                    modifier = Modifier.widthIn(max = 620.dp)
                )
            }
        }
        if (postUpdateBanner != null) {
            item {
                PostUpdateSafeBannerCard(
                    banner = postUpdateBanner,
                    strings = strings,
                    onSaveBackupClick = onSaveSafetyBackupFile,
                    onDismiss = onDismissPostUpdateBanner,
                    modifier = Modifier.widthIn(max = 620.dp)
                )
            }
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = farmName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.caretakerHeader,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.caretakerHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
                    )
                }
            }
        }

        // Caretaker can ONLY submit Expenses, Medicine, and Livestock Buy/Sell
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CaretakerActionCard(
                        title = strings.quickExpense,
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        accent = ExpenseRed,
                        onClick = { onQuickAddCategory(EntryCategory.EXPENSE) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_expense_tile")
                    )
                    CaretakerActionCard(
                        title = strings.quickMedicine,
                        icon = Icons.Rounded.MedicalServices,
                        accent = MedicineBlue,
                        onClick = { onQuickAddCategory(EntryCategory.MEDICINE_VET) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_medicine_tile")
                    )
                }
                CaretakerActionCard(
                    title = strings.quickLivestockTrade,
                    icon = Icons.Rounded.SwapHoriz,
                    accent = LivestockBuyTeal,
                    onClick = { onQuickAddCategory(EntryCategory.LIVESTOCK_BUY) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_trade_tile")
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        ExpenseSubCategory.TREATMENT,
                        ExpenseSubCategory.FEED,
                        ExpenseSubCategory.FARM_MAINTENANCE,
                        ExpenseSubCategory.MACHINERY,
                    ).forEach { sub ->
                        AssistChip(
                            onClick = { onQuickAddFarmSubCategory(sub) },
                            label = { Text(subCategoryLabel(sub, strings), fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("caretaker_sub_${sub.name.lowercase()}")
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = strings.recentEntries,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
            )
        }

        if (entries.isEmpty()) {
            item { EmptyLedgerCard(strings = strings, modifier = Modifier.widthIn(max = 620.dp)) }
        } else {
            items(entries.take(25), key = { it.id }) { entry ->
                LedgerEntryCard(
                    entry = entry,
                    strings = strings,
                    canEdit = false,
                    onEditClick = {},
                    onReceiptClick = { onViewReceipt(entry) },
                    modifier = Modifier.widthIn(max = 620.dp)
                )
            }
        }
    }
}

@Composable
private fun CaretakerActionCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = accent, modifier = Modifier.size(22.dp))
            }
            Text(
                text = title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun HeroMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.42f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                text = value,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SectionBreakdownBar(
    label: String,
    amount: Double,
    maxAmount: Double,
    color: Color,
    strings: AppStrings,
) {
    val progress = (amount / maxAmount).toFloat().coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
            Text(
                formatCurrency(amount, strings),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = color.copy(alpha = 0.14f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun PostUpdateSafeBannerCard(
    banner: PostUpdateBanner,
    strings: AppStrings,
    onSaveBackupClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LivestockSellGreen.copy(alpha = 0.13f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = LivestockSellGreen)
                Text(
                    text = String.format(strings.appUpdatedSafeTitle, banner.versionName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = LivestockSellGreen
                )
            }
            Text(
                text = String.format(strings.appUpdatedSafeBody, banner.totalEntriesCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSaveBackupClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LivestockSellGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("post_update_backup_button")
                ) {
                    Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(strings.saveSafetyBackupNow, fontSize = 12.sp, lineHeight = 16.sp)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("post_update_dismiss_button")
                ) {
                    Text(strings.dismissAction, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AvailableUpdateBannerCard(
    update: AvailableAppUpdate,
    strings: AppStrings,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = String.format(strings.updateAvailableTitle, update.versionName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (update.releaseNotes.isNotBlank()) {
                Text(
                    text = update.releaseNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
            Button(
                onClick = onUpdateClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auto_update_app_button")
            ) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(strings.updateDownloadButton, fontSize = 13.sp)
            }
        }
    }
}
