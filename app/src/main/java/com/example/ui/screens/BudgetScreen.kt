package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ApprovalStatus
import com.example.data.model.EntryCategory
import com.example.data.model.ExpenseSubCategory
import com.example.data.model.LedgerEntry
import com.example.data.model.MonthlyBudgetStatus
import com.example.i18n.AppStrings
import com.example.ui.components.LedgerEntryCard
import com.example.ui.components.MonthlyBudgetAlertBanner
import com.example.ui.components.MonthlyBudgetTrackerCard
import java.util.Calendar

@Composable
fun BudgetScreen(
    entries: List<LedgerEntry>,
    budgetStatus: MonthlyBudgetStatus,
    isAdminView: Boolean,
    strings: AppStrings,
    onOpenMonthlyBudgets: () -> Unit,
    onQuickAddFeed: () -> Unit,
    onQuickAddMedVet: () -> Unit,
    onEditEntry: (LedgerEntry) -> Unit,
    onApproveEntry: (String) -> Unit,
    onRejectEntry: (String) -> Unit,
    onViewReceipt: (LedgerEntry) -> Unit,
) {
    val currentMonthBudgetEntries = remember(entries, budgetStatus.monthTimestampMillis) {
        val nowCal = Calendar.getInstance().apply { timeInMillis = budgetStatus.monthTimestampMillis }
        val targetYear = nowCal.get(Calendar.YEAR)
        val targetMonth = nowCal.get(Calendar.MONTH)
        val eCal = Calendar.getInstance()
        entries.filter { entry ->
            eCal.timeInMillis = entry.entryDateMillis
            val sameMonth = eCal.get(Calendar.YEAR) == targetYear && eCal.get(Calendar.MONTH) == targetMonth
            if (!sameMonth) return@filter false
            val cat = entry.parsedCategory()
            val sub = entry.parsedSubCategory()
            cat == EntryCategory.MEDICINE_VET ||
                (cat == EntryCategory.EXPENSE && (sub == ExpenseSubCategory.FEED || sub == ExpenseSubCategory.TREATMENT))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("budget_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (budgetStatus.hasAnyAlert) {
            item {
                MonthlyBudgetAlertBanner(
                    status = budgetStatus,
                    strings = strings,
                    onEditBudgets = if (isAdminView) onOpenMonthlyBudgets else ({}),
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.monthlyBudgetsTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = strings.budgetPageHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onQuickAddFeed,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("budget_add_feed_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(strings.subFeed, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = onQuickAddMedVet,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("budget_add_medvet_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(strings.quickMedicine, fontSize = 13.sp)
                        }
                    }
                    if (isAdminView) {
                        Button(
                            onClick = onOpenMonthlyBudgets,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("budget_page_set_caps_button")
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(strings.setBudgetCapsAction, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            MonthlyBudgetTrackerCard(
                status = budgetStatus,
                strings = strings,
                canEdit = isAdminView,
                onEditBudgets = onOpenMonthlyBudgets,
                modifier = Modifier.widthIn(max = 640.dp)
            )
        }

        item {
            Text(
                text = strings.budgetThisMonthExpenses,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            )
        }

        if (currentMonthBudgetEntries.isEmpty()) {
            item {
                EmptyLedgerCard(
                    strings = strings,
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        } else {
            items(currentMonthBudgetEntries, key = { it.id }) { entry ->
                val isPending = entry.parsedApprovalStatus() == ApprovalStatus.PENDING
                LedgerEntryCard(
                    entry = entry,
                    strings = strings,
                    canEdit = isAdminView,
                    onEditClick = { onEditEntry(entry) },
                    onReceiptClick = { onViewReceipt(entry) },
                    onApproveClick = if (isAdminView && isPending) {
                        { onApproveEntry(entry.id) }
                    } else null,
                    onRejectClick = if (isAdminView && isPending) {
                        { onRejectEntry(entry.id) }
                    } else null,
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        }
    }
}
