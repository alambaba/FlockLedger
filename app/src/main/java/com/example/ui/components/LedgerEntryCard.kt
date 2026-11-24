package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.EntryCategory
import com.example.data.model.LedgerEntry
import com.example.i18n.AppStrings
import com.example.util.ImageReceiptHelper
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatCurrency(amount: Double, strings: AppStrings): String {
    val fmt = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 0
    }
    return "${strings.currencyPrefix} ${fmt.format(amount)}"
}

fun categoryIcon(category: EntryCategory): ImageVector = when (category) {
    EntryCategory.EXPENSE -> Icons.Rounded.ReceiptLong
    EntryCategory.MEDICINE_VET -> Icons.Rounded.MedicalServices
    EntryCategory.LIVESTOCK_BUY -> Icons.Rounded.ShoppingCart
    EntryCategory.LIVESTOCK_SELL -> Icons.Rounded.Sell
    EntryCategory.SHEPHERD_ADVANCE -> Icons.Rounded.Payments
    EntryCategory.SHEPHERD_SALARY -> Icons.Rounded.AccountBalanceWallet
    EntryCategory.OWNER_FUNDS -> Icons.Rounded.Pets
}

@Composable
fun LedgerEntryCard(
    entry: LedgerEntry,
    strings: AppStrings,
    canEdit: Boolean,
    onEditClick: () -> Unit,
    onReceiptClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cat = entry.parsedCategory()
    val accent = categoryColor(cat)
    val isInflow = cat == EntryCategory.OWNER_FUNDS || cat == EntryCategory.LIVESTOCK_SELL
    val dateStr = remember(entry.entryDateMillis) {
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(entry.entryDateMillis))
    }
    val thumbBmp = remember(entry.receiptBase64) {
        ImageReceiptHelper.decodeBase64ToBitmap(entry.receiptBase64)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(cat),
                    contentDescription = categoryLabel(cat, strings),
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accent.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = categoryLabel(cat, strings),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val metaParts = buildList {
                    if (entry.livestockCount > 0L) add("${entry.livestockCount} ${strings.sheepUnit}")
                    if (entry.shepherdName.isNotBlank()) add(entry.shepherdName)
                    if (entry.note.isNotBlank()) add(entry.note)
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (entry.createdByName.isNotBlank()) {
                    Text(
                        text = "${strings.loggedByPrefix} ${entry.createdByName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = (if (isInflow) "+ " else "- ") + formatCurrency(entry.amount, strings),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isInflow) MaterialTheme.colorScheme.primary else accent
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (thumbBmp != null) {
                        Image(
                            bitmap = thumbBmp.asImageBitmap(),
                            contentDescription = strings.viewReceipt,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onReceiptClick)
                        )
                    }
                    if (canEdit) {
                        IconButton(onClick = onEditClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = strings.editEntryTitle,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
