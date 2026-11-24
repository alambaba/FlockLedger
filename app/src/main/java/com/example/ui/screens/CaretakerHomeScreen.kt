package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.EntryCategory
import com.example.data.model.LedgerEntry
import com.example.i18n.AppStrings
import com.example.ui.components.LedgerEntryCard
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.LivestockBuyTeal
import com.example.ui.theme.MedicineBlue
import com.example.ui.theme.OwnerFundsGold
import com.example.ui.theme.ShepherdAdvancePurple

@Composable
fun CaretakerHomeScreen(
    farmName: String,
    entries: List<LedgerEntry>,
    entriesSinceBackup: Int,
    strings: AppStrings,
    onQuickAddCategory: (EntryCategory) -> Unit,
    onViewReceipt: (LedgerEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
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
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = String.format(strings.autoBackupCounter, entriesSinceBackup.coerceAtMost(5)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Big, friendly 2-column Quick Action Grid
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
                    QuickActionTile(
                        title = strings.quickExpense,
                        icon = Icons.Rounded.ReceiptLong,
                        accent = ExpenseRed,
                        onClick = { onQuickAddCategory(EntryCategory.EXPENSE) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_expense_tile")
                    )
                    QuickActionTile(
                        title = strings.quickMedicine,
                        icon = Icons.Rounded.MedicalServices,
                        accent = MedicineBlue,
                        onClick = { onQuickAddCategory(EntryCategory.MEDICINE_VET) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_medicine_tile")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        title = strings.quickAdvanceSalary,
                        icon = Icons.Rounded.Payments,
                        accent = ShepherdAdvancePurple,
                        onClick = { onQuickAddCategory(EntryCategory.SHEPHERD_ADVANCE) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_advance_tile")
                    )
                    QuickActionTile(
                        title = strings.quickLivestockTrade,
                        icon = Icons.Rounded.SwapHoriz,
                        accent = LivestockBuyTeal,
                        onClick = { onQuickAddCategory(EntryCategory.LIVESTOCK_BUY) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_trade_tile")
                    )
                }

                QuickActionTile(
                    title = strings.quickOwnerFunds,
                    icon = Icons.Rounded.AccountBalanceWallet,
                    accent = OwnerFundsGold,
                    onClick = { onQuickAddCategory(EntryCategory.OWNER_FUNDS) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_owner_funds_tile")
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                EmptyLedgerCard(strings = strings, modifier = Modifier.widthIn(max = 620.dp))
            }
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

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun EmptyLedgerCard(strings: AppStrings, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Pets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp)
            )
            Text(
                text = strings.emptyEntriesTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = strings.emptyEntriesBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
