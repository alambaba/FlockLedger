package com.example.ui.screens

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
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
    onSaveConfig: (sheetUrl: String, siaUrl: String, siaBucket: String, siaToken: String) -> Unit,
    onBackupNow: () -> Unit,
    onExportCsv: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var sheetUrl by remember(prefs.googleSheetWebhookUrl) { mutableStateOf(prefs.googleSheetWebhookUrl) }
    var siaUrl by remember(prefs.siaEndpointUrl) { mutableStateOf(prefs.siaEndpointUrl) }
    var siaBucket by remember(prefs.siaBucket) { mutableStateOf(prefs.siaBucket) }
    var siaToken by remember(prefs.siaAuthToken) { mutableStateOf(prefs.siaAuthToken) }
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(strings.cancel) }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = {
                            onSaveConfig(sheetUrl, siaUrl, siaBucket, siaToken)
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
    isAdmin: Boolean,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSaveFarmSettings: (name: String, initialFlock: Long, shepherds: List<String>) -> Unit,
    onJoinFarmByCode: (String) -> Unit,
    onSyncMembers: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var farmName by remember(farm.name) { mutableStateOf(farm.name) }
    var initialFlockText by remember(farm.initialFlockCount) { mutableStateOf(farm.initialFlockCount.toString()) }
    var joinCodeInput by remember { mutableStateOf("") }
    var codeCopied by remember { mutableStateOf(false) }

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

                // Invite code card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${strings.inviteCodeLabel}: ${farm.joinCode}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = strings.shareInviteHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                        Text(
                            text = "${strings.membersCountLabel}: ${farm.memberUids.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(farm.joinCode))
                                    codeCopied = true
                                }
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(if (codeCopied) "Copied!" else farm.joinCode)
                            }
                            if (isAdmin) {
                                OutlinedButton(onClick = onSyncMembers) {
                                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(strings.syncMembersAccess)
                                }
                            }
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.saveEntry)
                    }
                }

                HorizontalDivider()

                // Join another farm by code
                Text(strings.joinFarmTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = { joinCodeInput = it.uppercase() },
                        label = { Text(strings.enterJoinCode) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (joinCodeInput.isNotBlank()) {
                                onJoinFarmByCode(joinCodeInput)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Rounded.GroupAdd, contentDescription = strings.joinFarmAction)
                    }
                }
            }
        }
    }
}
