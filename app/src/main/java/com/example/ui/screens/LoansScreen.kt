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
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Lock
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
import com.example.data.model.EntryCategory
import com.example.data.model.FarmSummary
import com.example.data.model.LedgerEntry
import com.example.data.model.LoanPartySummary
import com.example.i18n.AppStrings
import com.example.ui.components.LedgerEntryCard
import com.example.ui.components.formatCurrency
import com.example.ui.components.isLoanCategory
import com.example.ui.theme.LivestockSellGreen
import com.example.ui.theme.LoanGivenTeal
import com.example.ui.theme.LoanTakenAmber
import com.example.ui.theme.ShepherdSalaryIndigo

@Composable
fun LoansScreen(
    entries: List<LedgerEntry>,
    summary: FarmSummary,
    isAdminView: Boolean,
    strings: AppStrings,
    onAddLoan: (category: EntryCategory, partyName: String) -> Unit,
    onEditEntry: (LedgerEntry) -> Unit,
    onViewReceipt: (LedgerEntry) -> Unit,
) {
    val loanEntries = remember(entries) {
        entries.filter { it.parsedCategory().isLoanCategory() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
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
                    label = strings.loansPayableTitle,
                    value = formatCurrency(summary.loansPayableBalance, strings),
                    color = LoanTakenAmber,
                    modifier = Modifier.weight(1f)
                )
                SectionStatChip(
                    label = strings.loansReceivableTitle,
                    value = formatCurrency(summary.loansReceivableBalance, strings),
                    color = LoanGivenTeal,
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
                            onClick = { onAddLoan(EntryCategory.LOAN_TAKEN, "") },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("loan_taken_button")
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(strings.quickLoanTaken, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                        OutlinedButton(
                            onClick = { onAddLoan(EntryCategory.LOAN_GIVEN, "") },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("loan_given_button")
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(strings.quickLoanGiven, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onAddLoan(EntryCategory.LOAN_REPAY, "") },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("loan_repay_button")
                        ) {
                            Icon(Icons.Rounded.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(strings.quickLoanRepay, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                        OutlinedButton(
                            onClick = { onAddLoan(EntryCategory.LOAN_RECOVER, "") },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("loan_recover_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(strings.quickLoanRecover, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }

        if (summary.loanPartyBreakdown.isNotEmpty()) {
            items(summary.loanPartyBreakdown, key = { it.partyName }) { party ->
                LoanPartyCard(
                    item = party,
                    isAdminView = isAdminView,
                    strings = strings,
                    onSettleClick = {
                        val cat = if (party.payableRemaining >= party.receivableRemaining) {
                            EntryCategory.LOAN_REPAY
                        } else {
                            EntryCategory.LOAN_RECOVER
                        }
                        onAddLoan(cat, party.partyName)
                    },
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        }

        if (loanEntries.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Text(
                        text = strings.noLoansYet,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(loanEntries, key = { it.id }) { entry ->
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
private fun LoanPartyCard(
    item: LoanPartySummary,
    isAdminView: Boolean,
    strings: AppStrings,
    onSettleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.partyName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isAdminView && (item.payableRemaining > 0.0 || item.receivableRemaining > 0.0)) {
                    OutlinedButton(onClick = onSettleClick) {
                        Text(
                            text = if (item.payableRemaining >= item.receivableRemaining) {
                                strings.quickLoanRepay
                            } else {
                                strings.quickLoanRecover
                            },
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.taken > 0.0) {
                    SectionStatChip(
                        label = strings.loansTakenTotalLabel,
                        value = formatCurrency(item.payableRemaining, strings),
                        color = LoanTakenAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (item.given > 0.0) {
                    SectionStatChip(
                        label = strings.loansGivenTotalLabel,
                        value = formatCurrency(item.receivableRemaining, strings),
                        color = LoanGivenTeal,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (item.repaid > 0.0) {
                    SectionStatChip(
                        label = strings.loansRepaidLabel,
                        value = formatCurrency(item.repaid, strings),
                        color = ShepherdSalaryIndigo,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (item.recovered > 0.0) {
                    SectionStatChip(
                        label = strings.loansRecoveredLabel,
                        value = formatCurrency(item.recovered, strings),
                        color = LivestockSellGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
