package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BudgetAlertLevel
import com.example.data.model.CategoryBudgetStatus
import com.example.data.model.MonthlyBudgetStatus
import com.example.i18n.AppStrings
import com.example.ui.theme.LivestockSellGreen
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.RejectedRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonthlyBudgetAlertBanner(
    status: MonthlyBudgetStatus,
    strings: AppStrings,
    onEditBudgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!status.hasAnyAlert) return

    val hasExceeded = status.feedBudget.alertLevel == BudgetAlertLevel.EXCEEDED ||
        status.medVetBudget.alertLevel == BudgetAlertLevel.EXCEEDED
    val accent = if (hasExceeded) RejectedRed else PendingAmber
    val icon = if (hasExceeded) Icons.Rounded.Warning else Icons.Rounded.NotificationsActive

    val alertLines = buildList {
        when (status.feedBudget.alertLevel) {
            BudgetAlertLevel.EXCEEDED -> add(
                String.format(
                    strings.budgetExceededAlertMsg,
                    strings.subFeed,
                    formatCurrency(status.feedBudget.spentThisMonth, strings)
                )
            )
            BudgetAlertLevel.APPROACHING -> add(
                String.format(
                    strings.budgetApproachingAlertMsg,
                    strings.subFeed,
                    status.feedBudget.percentUsed
                )
            )
            BudgetAlertLevel.NONE -> Unit
        }
        when (status.medVetBudget.alertLevel) {
            BudgetAlertLevel.EXCEEDED -> add(
                String.format(
                    strings.budgetExceededAlertMsg,
                    strings.medicineAndVet,
                    formatCurrency(status.medVetBudget.spentThisMonth, strings)
                )
            )
            BudgetAlertLevel.APPROACHING -> add(
                String.format(
                    strings.budgetApproachingAlertMsg,
                    strings.medicineAndVet,
                    status.medVetBudget.percentUsed
                )
            )
            BudgetAlertLevel.NONE -> Unit
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onEditBudgets)
            .testTag("budget_alert_banner"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = strings.monthlyBudgetsTitle,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                alertLines.forEach { line ->
                    Text(
                        text = line,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
            }
        }
    }
}

@Composable
fun MonthlyBudgetTrackerCard(
    status: MonthlyBudgetStatus,
    strings: AppStrings,
    canEdit: Boolean,
    onEditBudgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthLabel = remember(status.monthTimestampMillis) {
        SimpleDateFormat("MMM yyyy", Locale.US).format(Date(status.monthTimestampMillis))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monthly_budget_tracker_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${strings.monthlyBudgetsTitle} ($monthLabel)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${strings.alertThresholdLabel}: ${status.alertThresholdPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canEdit) {
                    OutlinedButton(
                        onClick = onEditBudgets,
                        modifier = Modifier.testTag("edit_monthly_budgets_button")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = strings.setBudgetCapsAction, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.setBudgetCapsAction, fontSize = 12.sp)
                    }
                }
            }

            if (!status.hasAnyCapSet) {
                Text(
                    text = strings.budgetNoCapSetHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            BudgetCategoryProgressItem(
                categoryTitle = strings.subFeed,
                budget = status.feedBudget,
                strings = strings,
            )
            BudgetCategoryProgressItem(
                categoryTitle = strings.medicineAndVet,
                budget = status.medVetBudget,
                strings = strings,
            )
        }
    }
}

@Composable
fun BudgetCategoryProgressItem(
    categoryTitle: String,
    budget: CategoryBudgetStatus,
    strings: AppStrings,
    modifier: Modifier = Modifier,
) {
    val barColor: Color = when (budget.alertLevel) {
        BudgetAlertLevel.EXCEEDED -> RejectedRed
        BudgetAlertLevel.APPROACHING -> PendingAmber
        BudgetAlertLevel.NONE -> LivestockSellGreen
    }
    val badgeText: String? = when {
        !budget.isCapSet -> null
        budget.alertLevel == BudgetAlertLevel.EXCEEDED -> "${strings.budgetExceededBadge} (${budget.percentUsed}%)"
        budget.alertLevel == BudgetAlertLevel.APPROACHING -> "${strings.budgetApproachingBadge} (${budget.percentUsed}%)"
        else -> "${strings.budgetOnTrackBadge} (${budget.percentUsed}%)"
    }
    val animatedProgress by animateFloatAsState(
        targetValue = budget.utilizationFraction,
        animationSpec = spring(),
        label = "budget_progress"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = categoryTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = barColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = barColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        if (budget.isCapSet) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                color = barColor,
                trackColor = barColor.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(RoundedCornerShape(5.dp))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(
                        strings.budgetSpentOfCap,
                        formatCurrency(budget.spentThisMonth, strings),
                        formatCurrency(budget.monthlyCap, strings)
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (budget.overAmount > 0.0) {
                        String.format(strings.budgetOverByLabel, formatCurrency(budget.overAmount, strings))
                    } else {
                        String.format(strings.budgetRemainingLabel, formatCurrency(budget.remainingAmount, strings))
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (budget.overAmount > 0.0) RejectedRed else MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            Text(
                text = "${formatCurrency(budget.spentThisMonth, strings)} (${strings.budgetNoCapSetHint})",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
