package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ApprovalStatus
import com.example.data.model.EntryCategory
import com.example.data.model.ExpenseSubCategory
import com.example.data.model.LedgerEntry
import com.example.i18n.AppStrings
import com.example.ui.theme.LivestockSellGreen
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.RejectedRed
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
    EntryCategory.EXPENSE -> Icons.AutoMirrored.Rounded.ReceiptLong
    EntryCategory.MEDICINE_VET -> Icons.Rounded.MedicalServices
    EntryCategory.LIVESTOCK_BUY -> Icons.Rounded.ShoppingCart
    EntryCategory.LIVESTOCK_SELL -> Icons.Rounded.Sell
    EntryCategory.SHEPHERD_ADVANCE -> Icons.Rounded.Payments
    EntryCategory.SHEPHERD_SALARY -> Icons.Rounded.AccountBalanceWallet
    EntryCategory.OWNER_FUNDS -> Icons.Rounded.Pets
    EntryCategory.LOAN_TAKEN,
    EntryCategory.LOAN_GIVEN,
    EntryCategory.LOAN_REPAY,
    EntryCategory.LOAN_RECOVER -> Icons.Rounded.AccountBalance
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LedgerEntryCard(
    entry: LedgerEntry,
    strings: AppStrings,
    canEdit: Boolean,
    onEditClick: () -> Unit,
    onReceiptClick: () -> Unit,
    onApproveClick: (() -> Unit)? = null,
    onRejectClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cat = entry.parsedCategory()
    val status = entry.parsedApprovalStatus()
    val accent = categoryColor(cat)
    val isInflow = cat == EntryCategory.OWNER_FUNDS ||
        cat == EntryCategory.LIVESTOCK_SELL ||
        cat == EntryCategory.LOAN_TAKEN ||
        cat == EntryCategory.LOAN_RECOVER

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
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

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        val subCat: ExpenseSubCategory? = entry.parsedSubCategory()
                        if (cat == EntryCategory.EXPENSE && subCat != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = subCategoryLabel(subCat, strings),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (status == ApprovalStatus.PENDING) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PendingAmber.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = strings.pendingApprovalBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PendingAmber,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        } else if (status == ApprovalStatus.REJECTED) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = RejectedRed.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = strings.rejectedBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RejectedRed,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    val metaParts = buildList {
                        add(dateStr)
                        if (entry.livestockCount > 0L) add("${entry.livestockCount} ${strings.sheepUnit}")
                        if (entry.shepherdName.isNotBlank()) add(entry.shepherdName)
                        if (entry.note.isNotBlank()) add(entry.note)
                    }
                    Text(
                        text = metaParts.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (entry.createdByName.isNotBlank()) {
                        Text(
                            text = "${strings.loggedByPrefix} ${entry.createdByName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Bottom row: Full untruncated amount + receipt + edit button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (if (isInflow) "+ " else "- ") + formatCurrency(entry.amount, strings),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isInflow) MaterialTheme.colorScheme.primary else accent
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (thumbBmp != null) {
                        Image(
                            bitmap = thumbBmp.asImageBitmap(),
                            contentDescription = strings.viewReceipt,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onReceiptClick)
                        )
                    }
                    if (canEdit) {
                        IconButton(onClick = onEditClick, modifier = Modifier.size(40.dp)) {
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

            // Admin Approve / Reject controls for Pending Caretaker submissions
            if (status == ApprovalStatus.PENDING && onApproveClick != null && onRejectClick != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onApproveClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LivestockSellGreen,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("approve_entry_${entry.id}")
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(strings.approveAction, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = onRejectClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RejectedRed),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reject_entry_${entry.id}")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(strings.rejectAction, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
