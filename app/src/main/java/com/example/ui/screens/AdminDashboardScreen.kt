package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.EntryCategory
import com.example.data.model.Farm
import com.example.data.model.FarmSummary
import com.example.data.model.LedgerEntry
import com.example.data.model.ShepherdLedgerSummary
import com.example.i18n.AppStrings
import com.example.ui.components.LedgerEntryCard
import com.example.ui.components.categoryColor
import com.example.ui.components.categoryLabel
import com.example.ui.components.formatCurrency
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.LivestockBuyTeal
import com.example.ui.theme.LivestockSellGreen
import com.example.ui.theme.MedicineBlue
import com.example.ui.theme.OwnerFundsGold
import com.example.ui.theme.ShepherdAdvancePurple
import com.example.ui.theme.ShepherdSalaryIndigo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    farm: Farm,
    entries: List<LedgerEntry>,
    summary: FarmSummary,
    entriesSinceBackup: Int,
    strings: AppStrings,
    onEditEntry: (LedgerEntry) -> Unit,
    onViewReceipt: (LedgerEntry) -> Unit,
    onAddShepherd: (String) -> Unit,
    onPayShepherd: (String) -> Unit,
    onDownloadPdf: () -> Unit,
    onOpenBackupSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        strings.tabOverview,
        strings.tabShepherds,
        strings.tabLivestockMed,
        strings.tabFullLedger
    )

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    modifier = Modifier.testTag("admin_tab_$idx")
                )
            }
        }

        when (selectedTab) {
            0 -> AdminOverviewTab(
                farm = farm,
                entries = entries,
                summary = summary,
                entriesSinceBackup = entriesSinceBackup,
                strings = strings,
                onEditEntry = onEditEntry,
                onViewReceipt = onViewReceipt,
                onDownloadPdf = onDownloadPdf,
                onOpenBackupSettings = onOpenBackupSettings
            )
            1 -> AdminShepherdsTab(
                summary = summary,
                strings = strings,
                onAddShepherd = onAddShepherd,
                onPayShepherd = onPayShepherd
            )
            2 -> AdminLivestockMedTab(
                farm = farm,
                entries = entries,
                summary = summary,
                strings = strings,
                onEditEntry = onEditEntry,
                onViewReceipt = onViewReceipt
            )
            else -> AdminFullLedgerTab(
                entries = entries,
                strings = strings,
                onEditEntry = onEditEntry,
                onViewReceipt = onViewReceipt
            )
        }
    }
}

@Composable
private fun AdminOverviewTab(
    farm: Farm,
    entries: List<LedgerEntry>,
    summary: FarmSummary,
    entriesSinceBackup: Int,
    strings: AppStrings,
    onEditEntry: (LedgerEntry) -> Unit,
    onViewReceipt: (LedgerEntry) -> Unit,
    onDownloadPdf: () -> Unit,
    onOpenBackupSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Owner Balance Card
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(215.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(215.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.25f),
                                        Color.Black.copy(alpha = 0.78f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "${strings.currentFlock}: ${summary.currentFlockCount} ${strings.sheepUnit}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = strings.remainingBalance,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            text = formatCurrency(summary.remainingCashBalance, strings),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MetricMiniPill(
                                label = strings.ownerFundsReceived,
                                value = formatCurrency(summary.ownerFundsReceived, strings),
                                modifier = Modifier.weight(1f)
                            )
                            MetricMiniPill(
                                label = strings.totalSpent,
                                value = formatCurrency(summary.totalOutflows, strings),
                                modifier = Modifier.weight(1f)
                            )
                            MetricMiniPill(
                                label = strings.livestockSales,
                                value = formatCurrency(summary.livestockSalesRevenue, strings),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Action strip: PDF Report + Auto-Backup progress
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDownloadPdf,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("overview_pdf_button")
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = strings.downloadPdfReport, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(strings.downloadPdfReport, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onOpenBackupSettings,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("overview_backup_button")
                ) {
                    Icon(Icons.Rounded.CloudDone, contentDescription = strings.backupSettingsMenu, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = String.format(strings.autoBackupCounter, entriesSinceBackup.coerceAtMost(5)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Category Breakdown Card
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
                        1.0
                    )
                    BreakdownBarRow(strings.generalExpenses, summary.generalExpenses, maxVal, ExpenseRed, strings)
                    BreakdownBarRow(strings.medicineAndVet, summary.medicineVetExpenses, maxVal, MedicineBlue, strings)
                    BreakdownBarRow(strings.livestockBought, summary.livestockBuyCost, maxVal, LivestockBuyTeal, strings)
                    BreakdownBarRow(strings.shepherdAdvances, summary.shepherdAdvancesTotal, maxVal, ShepherdAdvancePurple, strings)
                    BreakdownBarRow(strings.shepherdSalaries, summary.shepherdSalariesTotal, maxVal, ShepherdSalaryIndigo, strings)
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = strings.recentEntries,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (entries.isEmpty()) {
            item {
                EmptyLedgerCard(strings = strings, modifier = Modifier.widthIn(max = 640.dp))
            }
        } else {
            items(entries.take(15), key = { it.id }) { entry ->
                LedgerEntryCard(
                    entry = entry,
                    strings = strings,
                    canEdit = true,
                    onEditClick = { onEditEntry(entry) },
                    onReceiptClick = { onViewReceipt(entry) },
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun MetricMiniPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BreakdownBarRow(
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
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(formatCurrency(amount, strings), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
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
private fun AdminShepherdsTab(
    summary: FarmSummary,
    strings: AppStrings,
    onAddShepherd: (String) -> Unit,
    onPayShepherd: (String) -> Unit,
) {
    var newShepherd by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                ShepherdAccountCard(
                    item = sh,
                    strings = strings,
                    onPayClick = { onPayShepherd(sh.shepherdName) },
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun ShepherdAccountCard(
    item: ShepherdLedgerSummary,
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
                OutlinedButton(onClick = onPayClick) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(strings.payShepherdQuick)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatChip(
                    label = strings.totalAdvanceGiven,
                    value = formatCurrency(item.totalAdvance, strings),
                    color = ShepherdAdvancePurple,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = strings.totalSalaryPaid,
                    value = formatCurrency(item.totalSalary, strings),
                    color = ShepherdSalaryIndigo,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
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
private fun StatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AdminLivestockMedTab(
    farm: Farm,
    entries: List<LedgerEntry>,
    summary: FarmSummary,
    strings: AppStrings,
    onEditEntry: (LedgerEntry) -> Unit,
    onViewReceipt: (LedgerEntry) -> Unit,
) {
    val relevant = remember(entries) {
        entries.filter {
            val c = it.parsedCategory()
            c == EntryCategory.LIVESTOCK_BUY || c == EntryCategory.LIVESTOCK_SELL || c == EntryCategory.MEDICINE_VET
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
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
                StatChip(
                    label = "${strings.livestockBought} (${summary.livestockBoughtCount})",
                    value = formatCurrency(summary.livestockBuyCost, strings),
                    color = LivestockBuyTeal,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "${strings.livestockSales} (${summary.livestockSoldCount})",
                    value = formatCurrency(summary.livestockSalesRevenue, strings),
                    color = LivestockSellGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatChip(
                    label = strings.currentFlock,
                    value = "${summary.currentFlockCount} ${strings.sheepUnit} (Init: ${farm.initialFlockCount})",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = strings.medicineAndVet,
                    value = formatCurrency(summary.medicineVetExpenses, strings),
                    color = MedicineBlue,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (relevant.isEmpty()) {
            item { EmptyLedgerCard(strings = strings, modifier = Modifier.widthIn(max = 640.dp)) }
        } else {
            items(relevant, key = { it.id }) { entry ->
                LedgerEntryCard(
                    entry = entry,
                    strings = strings,
                    canEdit = true,
                    onEditClick = { onEditEntry(entry) },
                    onReceiptClick = { onViewReceipt(entry) },
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun AdminFullLedgerTab(
    entries: List<LedgerEntry>,
    strings: AppStrings,
    onEditEntry: (LedgerEntry) -> Unit,
    onViewReceipt: (LedgerEntry) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<EntryCategory?>(null) }

    val filtered = remember(entries, query, selectedCategory) {
        entries.filter { entry ->
            val catMatch = selectedCategory == null || entry.parsedCategory() == selectedCategory
            val q = query.trim().lowercase()
            val textMatch = q.isEmpty() ||
                entry.title.lowercase().contains(q) ||
                entry.shepherdName.lowercase().contains(q) ||
                entry.note.lowercase().contains(q)
            catMatch && textMatch
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
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
                    onClick = { selectedCategory = null },
                    label = { Text(strings.catAll) }
                )
                EntryCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text(categoryLabel(cat, strings)) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item { EmptyLedgerCard(strings = strings, modifier = Modifier.widthIn(max = 640.dp)) }
        } else {
            items(filtered, key = { it.id }) { entry ->
                LedgerEntryCard(
                    entry = entry,
                    strings = strings,
                    canEdit = true,
                    onEditClick = { onEditEntry(entry) },
                    onReceiptClick = { onViewReceipt(entry) },
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}
