package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBusiness
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.backup.BackupManager
import com.example.data.model.Farm
import com.example.data.preferences.UserPreferences
import com.example.i18n.AppStrings

@Composable
fun BackupSettingsDialog(
    prefs: UserPreferences,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSaveConfig: (sheetUrl: String, siaUrl: String, siaBucket: String, siaToken: String, updateRepo: String) -> Unit,
    onBackupNow: () -> Unit,
    onExportCsv: () -> Unit,
    onExportFullBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onCheckAppUpdate: (repoOrUrl: String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var sheetUrl by remember(prefs.googleSheetWebhookUrl) { mutableStateOf(prefs.googleSheetWebhookUrl) }
    var siaUrl by remember(prefs.siaEndpointUrl) { mutableStateOf(prefs.siaEndpointUrl) }
    var siaBucket by remember(prefs.siaBucket) { mutableStateOf(prefs.siaBucket) }
    var siaToken by remember(prefs.siaAuthToken) { mutableStateOf(prefs.siaAuthToken) }
    var updateRepo by remember(prefs.updateSourceRepo) { mutableStateOf(prefs.updateSourceRepo) }
    var copiedMsg by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .widthIn(max = 580.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.backupScreenTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.cancel)
                    }
                }

                // Auto-backup every 5 entries status card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = strings.backupEveryFiveRule,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                        val count = prefs.entriesSinceBackup.coerceIn(0, 5)
                        Text(
                            text = String.format(strings.entriesUntilNextBackup, count),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        LinearProgressIndicator(
                            progress = { count / 5f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "${strings.lastBackupLabel}: ${prefs.lastBackupSummary.ifBlank { strings.noBackupYet }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onBackupNow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("backup_now_button")
                    ) {
                        Icon(Icons.Rounded.Backup, contentDescription = strings.backupNow, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.backupNow)
                    }
                    OutlinedButton(
                        onClick = onExportCsv,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_csv_button")
                    ) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = strings.exportCsvShare, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.exportCsvShare)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportFullBackup,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_json_backup_button")
                    ) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.exportBackupFile)
                    }
                    OutlinedButton(
                        onClick = onRestoreBackup,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("restore_json_backup_button")
                    ) {
                        Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.restoreBackupFile)
                    }
                }

                HorizontalDivider()

                // Google Sheets Webhook Configuration
                OutlinedTextField(
                    value = sheetUrl,
                    onValueChange = { sheetUrl = it },
                    label = { Text(strings.googleSheetUrlLabel) },
                    placeholder = { Text(strings.googleSheetHint) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_sheet_url_input")
                )

                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(BackupManager.GOOGLE_APPS_SCRIPT_TEMPLATE))
                        copiedMsg = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(if (copiedMsg) "Copied Apps Script!" else strings.copyAppsScript)
                }

                HorizontalDivider()

                // Sia Storage (renterd) Configuration
                OutlinedTextField(
                    value = siaUrl,
                    onValueChange = { siaUrl = it },
                    label = { Text(strings.siaEndpointLabel) },
                    placeholder = { Text("https://sia.example.com/api/worker/objects") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sia_url_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = siaBucket,
                        onValueChange = { siaBucket = it },
                        label = { Text(strings.siaBucketLabel) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = siaToken,
                        onValueChange = { siaToken = it },
                        label = { Text(strings.siaTokenLabel) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = updateRepo,
                    onValueChange = { updateRepo = it },
                    label = { Text(strings.updateSourceLabel) },
                    placeholder = { Text(strings.updateSourceHint) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_source_input")
                )

                OutlinedButton(
                    onClick = { onCheckAppUpdate(updateRepo) },
                    enabled = updateRepo.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("check_app_update_button")
                ) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(strings.checkUpdateNow)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(strings.cancel) }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = {
                            onSaveConfig(sheetUrl, siaUrl, siaBucket, siaToken, updateRepo)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("save_backup_config_button")
                    ) {
                        Text(strings.saveBackupConfig)
                    }
                }
            }
        }
    }
}

@Composable
fun FarmSettingsDialog(
    farm: Farm,
    allFarms: List<Farm>,
    isAdmin: Boolean,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSelectFarm: (String) -> Unit,
    onCreateFarm: (name: String, initialFlock: Long) -> Unit,
    onSaveFarmSettings: (name: String, initialFlock: Long, shepherds: List<String>) -> Unit,
) {
    var farmName by remember(farm.id, farm.name) { mutableStateOf(farm.name) }
    var initialFlockText by remember(farm.id, farm.initialFlockCount) { mutableStateOf(farm.initialFlockCount.toString()) }
    var newFarmName by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.farmSettingsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.cancel)
                    }
                }

                if (allFarms.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allFarms.forEach { item ->
                            FilterChip(
                                selected = item.id == farm.id,
                                onClick = {
                                    onSelectFarm(item.id)
                                    onDismiss()
                                },
                                label = { Text(item.name) }
                            )
                        }
                    }
                }

                if (isAdmin) {
                    OutlinedTextField(
                        value = farmName,
                        onValueChange = { farmName = it },
                        label = { Text(strings.farmNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = initialFlockText,
                        onValueChange = { raw -> if (raw.all { it.isDigit() }) initialFlockText = raw },
                        label = { Text(strings.initialFlockLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (farmName.isNotBlank()) {
                                onSaveFarmSettings(
                                    farmName,
                                    initialFlockText.toLongOrNull() ?: 0L,
                                    farm.shepherds
                                )
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_farm_settings_button")
                    ) {
                        Text(strings.saveEntry)
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newFarmName,
                            onValueChange = { newFarmName = it },
                            label = { Text(strings.farmNameLabel) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newFarmName.isNotBlank()) {
                                    onCreateFarm(newFarmName.trim(), 0L)
                                    newFarmName = ""
                                    onDismiss()
                                }
                            },
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("create_farm_button")
                        ) {
                            Icon(Icons.Rounded.AddBusiness, contentDescription = strings.saveEntry)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyBudgetDialog(
    prefs: UserPreferences,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSaveBudgets: (feedCap: Double, medVetCap: Double, alertThresholdPercent: Int) -> Unit,
) {
    var feedCapText by remember(prefs.monthlyFeedBudgetCap) {
        mutableStateOf(
            if (prefs.monthlyFeedBudgetCap > 0.0) {
                prefs.monthlyFeedBudgetCap.toLong().toString()
            } else ""
        )
    }
    var medVetCapText by remember(prefs.monthlyMedVetBudgetCap) {
        mutableStateOf(
            if (prefs.monthlyMedVetBudgetCap > 0.0) {
                prefs.monthlyMedVetBudgetCap.toLong().toString()
            } else ""
        )
    }
    var alertThreshold by remember(prefs.budgetAlertThresholdPercent) {
        mutableStateOf(prefs.budgetAlertThresholdPercent)
    }
    val thresholdOptions = listOf(75, 80, 90)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.monthlyBudgetsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.cancel)
                    }
                }

                Text(
                    text = strings.budgetCapHelperText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = feedCapText,
                    onValueChange = { raw ->
                        if (raw.all { it.isDigit() || it == '.' }) feedCapText = raw
                    },
                    label = { Text(strings.feedMonthlyCapLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("feed_budget_cap_input")
                )

                OutlinedTextField(
                    value = medVetCapText,
                    onValueChange = { raw ->
                        if (raw.all { it.isDigit() || it == '.' }) medVetCapText = raw
                    },
                    label = { Text(strings.medVetMonthlyCapLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("med_vet_budget_cap_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = strings.alertThresholdLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        thresholdOptions.forEachIndexed { idx, pct ->
                            SegmentedButton(
                                selected = alertThreshold == pct,
                                onClick = { alertThreshold = pct },
                                shape = SegmentedButtonDefaults.itemShape(idx, thresholdOptions.size),
                                modifier = Modifier.testTag("budget_threshold_$pct")
                            ) {
                                Text("$pct%")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel)
                    }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = {
                            val feedCap = feedCapText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            val medCap = medVetCapText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            onSaveBudgets(feedCap, medCap, alertThreshold)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("save_monthly_budgets_button")
                    ) {
                        Text(strings.saveEntry)
                    }
                }
            }
        }
    }
}

