package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.model.ApprovalStatus
import com.example.data.model.EntryCategory
import com.example.data.model.ExpenseSubCategory
import com.example.data.model.Farm
import com.example.data.model.FarmSummary
import com.example.data.model.LedgerEntry
import com.example.data.model.ShepherdLedgerSummary
import com.example.i18n.AppStrings
import com.example.ui.components.LedgerEntryCard
import com.example.ui.components.categoryLabel
import com.example.ui.components.formatCurrency
import com.example.ui.components.subCategoryLabel
import com.example.ui.theme.LivestockBuyTeal
import com.example.ui.theme.LivestockSellGreen
import com.example.ui.theme.MedicineBlue
import com.example.ui.theme.OwnerFundsGold
import com.example.ui.theme.ShepherdAdvancePurple
import com.example.ui.theme.ShepherdSalaryIndigo

@Composable
fun LedgerScreen(
    entries: List<LedgerEntry>,
    isAdminView: Boolean,
    strings: AppStrings,
    onEditEntry: (LedgerEntry) -> Unit,
    onApproveEntry: (String) -> Unit,
    onRejectEntry: (String) -> Unit,
    onViewReceipt: (LedgerEntry) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<EntryCategory?>(null) }
    var selectedSubCategory by remember { mutableStateOf<ExpenseSubCategory?>(null) }

    val filtered = remember(entries, query, selectedCategory, selectedSubCategory) {
        entries.filter { entry ->
            val catMatch = selectedCategory == null || entry.parsedCategory() == selectedCategory
            val subMatch = selectedCategory != EntryCategory.EXPENSE ||
                selectedSubCategory == null ||
                (entry.parsedSubCategory() ?: ExpenseSubCategory.OTHER) == selectedSubCategory
            val q = query.trim().lowercase()
            val textMatch = q.isEmpty() ||
                entry.title.lowercase().contains(q) ||
                entry.shepherdName.lowercase().contains(q) ||
                entry.note.lowercase().contains(q)
            catMatch && subMatch && textMatch
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(strings.searchPlaceholder) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
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
        }

        if (selectedCategory == EntryCategory.EXPENSE) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedSubCategory == null,
                        onClick = { selectedSubCategory = null },
                        label = { Text(strings.catAll) },
                        modifier = Modifier.testTag("filter_subcat_all")
                    )
                    ExpenseSubCategory.entries.forEach { sub ->
                        FilterChip(
                            selected = selectedSubCategory == sub,
                            onClick = {
                                selectedSubCategory = if (selectedSubCategory == sub) null else sub
                            },
                            label = { Text(subCategoryLabel(sub, strings)) },
                            modifier = Modifier.testTag("filter_subcat_${sub.name.lowercase()}")
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item { EmptyLedgerCard(strings = strings, modifier = Modifier.widthIn(max = 640.dp)) }
        } else {
            items(filtered, key = { it.id }) { entry ->
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

@Composable
fun TradeScreen(
    farm: Farm,
    entries: List<LedgerEntry>,
    summary: FarmSummary,
    isAdminView: Boolean,
    strings: AppStrings,
    onAddTrade: (EntryCategory) -> Unit,
    onEditEntry: (LedgerEntry) -> Unit,
    onApproveEntry: (String) -> Unit,
    onRejectEntry: (String) -> Unit,
    onViewReceipt: (LedgerEntry) -> Unit,
) {
    val tradeEntries = remember(entries) {
        entries.filter {
            val cat = it.parsedCategory()
            cat == EntryCategory.LIVESTOCK_BUY || cat == EntryCategory.LIVESTOCK_SELL
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionStatChip(
                    label = "${strings.livestockBought} (${summary.livestockBoughtCount})",
                    value = formatCurrency(summary.livestockBuyCost, strings),
                    color = LivestockBuyTeal,
                    modifier = Modifier.weight(1f)
                )
                SectionStatChip(
                    label = "${strings.livestockSales} (${summary.livestockSoldCount})",
                    value = formatCurrency(summary.livestockSalesRevenue, strings),
                    color = LivestockSellGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionStatChip(
                label = strings.currentFlock,
                value = "${summary.currentFlockCount} ${strings.sheepUnit} (Init: ${farm.initialFlockCount})",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onAddTrade(EntryCategory.LIVESTOCK_BUY) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trade_buy_button")
                ) {
                    Icon(Icons.Rounded.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(strings.catLivestockBuy, fontSize = 13.sp, lineHeight = 18.sp)
                }
                OutlinedButton(
                    onClick = { onAddTrade(EntryCategory.LIVESTOCK_SELL) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trade_sell_button")
                ) {
                    Icon(Icons.Rounded.Sell, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(strings.catLivestockSell, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }

        if (tradeEntries.isEmpty()) {
            item { EmptyLedgerCard(strings = strings, modifier = Modifier.widthIn(max = 640.dp)) }
        } else {
            items(tradeEntries, key = { it.id }) { entry ->
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

@Composable
fun SalariesScreen(
    entries: List<LedgerEntry>,
    summary: FarmSummary,
    isAdminView: Boolean,
    strings: AppStrings,
    onAddShepherd: (String) -> Unit,
    onPayShepherd: (String) -> Unit,
    onEditEntry: (LedgerEntry) -> Unit,
    onViewReceipt: (LedgerEntry) -> Unit,
) {
    var newShepherd by remember { mutableStateOf("") }
    val salaryEntries = remember(entries) {
        entries.filter {
            val cat = it.parsedCategory()
            cat == EntryCategory.SHEPHERD_ADVANCE || cat == EntryCategory.SHEPHERD_SALARY
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionStatChip(
                    label = strings.shepherdAdvances,
                    value = formatCurrency(summary.shepherdAdvancesTotal, strings),
                    color = ShepherdAdvancePurple,
                    modifier = Modifier.weight(1f)
                )
                SectionStatChip(
                    label = strings.shepherdSalaries,
                    value = formatCurrency(summary.shepherdSalariesTotal, strings),
                    color = ShepherdSalaryIndigo,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (!isAdminView) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = strings.adminOnlyTag)
                        Text(
                            text = "${strings.adminOnlyTag}: ${strings.caretakerRestrictedHint}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newShepherd,
                            onValueChange = { newShepherd = it },
                            label = { Text(strings.shepherdNameHint) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newShepherd.isNotBlank()) {
                                    onAddShepherd(newShepherd)
                                    newShepherd = ""
                                }
                            },
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = strings.addShepherd)
                        }
                    }
                }
            }
        }

        if (summary.shepherdBreakdown.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = strings.noShepherdsYet,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(summary.shepherdBreakdown, key = { it.shepherdName }) { sh ->
                ShepherdSummaryRowCard(
                    item = sh,
                    isAdminView = isAdminView,
                    strings = strings,
                    onPayClick = { onPayShepherd(sh.shepherdName) },
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        }

        if (salaryEntries.isNotEmpty()) {
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
            items(salaryEntries, key = { it.id }) { entry ->
                LedgerEntryCard(
                    entry = entry,
                    strings = strings,
                    canEdit = isAdminView,
                    onEditClick = { onEditEntry(entry) },
                    onReceiptClick = { onViewReceipt(entry) },
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        }
    }
}

@Composable
fun MedicalScreen(
    entries: List<LedgerEntry>,
    summary: FarmSummary,
    isAdminView: Boolean,
    strings: AppStrings,
    onAddMedical: () -> Unit,
    onEditEntry: (LedgerEntry) -> Unit,
    onApproveEntry: (String) -> Unit,
    onRejectEntry: (String) -> Unit,
    onViewReceipt: (LedgerEntry) -> Unit,
) {
    val medEntries = remember(entries) {
        entries.filter {
            it.parsedCategory() == EntryCategory.MEDICINE_VET ||
                (it.parsedCategory() == EntryCategory.EXPENSE && it.parsedSubCategory() == ExpenseSubCategory.TREATMENT)
        }
    }
    val treatmentAndMedTotal = summary.medicineVetExpenses +
        (summary.farmExpenseSubTotals[ExpenseSubCategory.TREATMENT] ?: 0.0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MedicineBlue.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = strings.medicineAndVet,
                            style = MaterialTheme.typography.labelLarge,
                            color = MedicineBlue
                        )
                        Text(
                            text = formatCurrency(treatmentAndMedTotal, strings),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MedicineBlue
                        )
                    }
                    Button(
                        onClick = onAddMedical,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("medical_add_button")
                    ) {
                        Icon(Icons.Rounded.MedicalServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.quickMedicine, fontSize = 14.sp, lineHeight = 18.sp)
                    }
                }
            }
        }

        if (medEntries.isEmpty()) {
            item { EmptyLedgerCard(strings = strings, modifier = Modifier.widthIn(max = 640.dp)) }
        } else {
            items(medEntries, key = { it.id }) { entry ->
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

@Composable
private fun ShepherdSummaryRowCard(
    item: ShepherdLedgerSummary,
    isAdminView: Boolean,
    strings: AppStrings,
    onPayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.shepherdName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isAdminView) {
                    OutlinedButton(onClick = onPayClick) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(strings.payShepherdQuick, fontSize = 12.sp)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionStatChip(
                    label = strings.totalAdvanceGiven,
                    value = formatCurrency(item.totalAdvance, strings),
                    color = ShepherdAdvancePurple,
                    modifier = Modifier.weight(1f)
                )
                SectionStatChip(
                    label = strings.totalSalaryPaid,
                    value = formatCurrency(item.totalSalary, strings),
                    color = ShepherdSalaryIndigo,
                    modifier = Modifier.weight(1f)
                )
                SectionStatChip(
                    label = strings.totalPaidToShepherd,
                    value = formatCurrency(item.totalPaid, strings),
                    color = OwnerFundsGold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SectionStatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = color,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
