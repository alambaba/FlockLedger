package com.example.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.EntryCategory
import com.example.data.model.LedgerEntry
import com.example.i18n.AppStrings
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.LivestockBuyTeal
import com.example.ui.theme.LivestockSellGreen
import com.example.ui.theme.MedicineBlue
import com.example.ui.theme.OwnerFundsGold
import com.example.ui.theme.ShepherdAdvancePurple
import com.example.ui.theme.ShepherdSalaryIndigo
import com.example.util.ImageReceiptHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun categoryLabel(category: EntryCategory, strings: AppStrings): String = when (category) {
    EntryCategory.EXPENSE -> strings.catExpense
    EntryCategory.MEDICINE_VET -> strings.catMedicineVet
    EntryCategory.LIVESTOCK_BUY -> strings.catLivestockBuy
    EntryCategory.LIVESTOCK_SELL -> strings.catLivestockSell
    EntryCategory.SHEPHERD_ADVANCE -> strings.catShepherdAdvance
    EntryCategory.SHEPHERD_SALARY -> strings.catShepherdSalary
    EntryCategory.OWNER_FUNDS -> strings.catOwnerFunds
}

fun categoryColor(category: EntryCategory): Color = when (category) {
    EntryCategory.EXPENSE -> ExpenseRed
    EntryCategory.MEDICINE_VET -> MedicineBlue
    EntryCategory.LIVESTOCK_BUY -> LivestockBuyTeal
    EntryCategory.LIVESTOCK_SELL -> LivestockSellGreen
    EntryCategory.SHEPHERD_ADVANCE -> ShepherdAdvancePurple
    EntryCategory.SHEPHERD_SALARY -> ShepherdSalaryIndigo
    EntryCategory.OWNER_FUNDS -> OwnerFundsGold
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorDialog(
    strings: AppStrings,
    initialCategory: EntryCategory,
    editingEntry: LedgerEntry?,
    shepherds: List<String>,
    prefilledShepherd: String = "",
    onDismiss: () -> Unit,
    onSave: (
        category: EntryCategory,
        title: String,
        amount: Double,
        livestockCount: Long,
        shepherdName: String,
        note: String,
        receiptBase64: String?,
        entryDateMillis: Long,
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var category by remember(editingEntry, initialCategory) {
        mutableStateOf(editingEntry?.parsedCategory() ?: initialCategory)
    }
    var title by remember(editingEntry) {
        mutableStateOf(
            editingEntry?.title ?: defaultTitleForCategory(initialCategory, strings)
        )
    }
    var amountText by remember(editingEntry) {
        mutableStateOf(
            editingEntry?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""
        )
    }
    var sheepCountText by remember(editingEntry) {
        mutableStateOf(
            editingEntry?.livestockCount?.takeIf { it > 0 }?.toString() ?: ""
        )
    }
    var shepherdName by remember(editingEntry, prefilledShepherd) {
        mutableStateOf(editingEntry?.shepherdName ?: prefilledShepherd)
    }
    var note by remember(editingEntry) { mutableStateOf(editingEntry?.note ?: "") }
    var receiptBase64 by remember(editingEntry) { mutableStateOf(editingEntry?.receiptBase64) }
    var entryDateMillis by remember(editingEntry) {
        mutableLongStateOf(editingEntry?.entryDateMillis ?: System.currentTimeMillis())
    }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val receiptBitmap: Bitmap? = remember(receiptBase64) {
        ImageReceiptHelper.decodeBase64ToBitmap(receiptBase64)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val compressed = ImageReceiptHelper.compressUriToBase64(context, uri)
                if (compressed != null) receiptBase64 = compressed
            }
        }
    }

    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraUri
        if (success && capturedUri != null) {
            scope.launch {
                val compressed = ImageReceiptHelper.compressUriToBase64(context, capturedUri)
                if (compressed != null) receiptBase64 = compressed
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp)
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
                        text = if (editingEntry == null) strings.addEntryTitle else strings.editEntryTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.cancel)
                    }
                }

                // Category Chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.fieldCategory, style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EntryCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = {
                                    category = cat
                                    if (editingEntry == null && title.isBlank()) {
                                        title = defaultTitleForCategory(cat, strings)
                                    }
                                },
                                label = { Text(categoryLabel(cat, strings)) }
                            )
                        }
                    }
                }

                // Quick Segmented Toggle for Buy/Sell or Advance/Salary
                if (category == EntryCategory.LIVESTOCK_BUY || category == EntryCategory.LIVESTOCK_SELL) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = category == EntryCategory.LIVESTOCK_BUY,
                            onClick = { category = EntryCategory.LIVESTOCK_BUY },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) { Text(strings.buyOption) }
                        SegmentedButton(
                            selected = category == EntryCategory.LIVESTOCK_SELL,
                            onClick = { category = EntryCategory.LIVESTOCK_SELL },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) { Text(strings.sellOption) }
                    }
                } else if (category == EntryCategory.SHEPHERD_ADVANCE || category == EntryCategory.SHEPHERD_SALARY) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = category == EntryCategory.SHEPHERD_ADVANCE,
                            onClick = { category = EntryCategory.SHEPHERD_ADVANCE },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) { Text(strings.advanceOption) }
                        SegmentedButton(
                            selected = category == EntryCategory.SHEPHERD_SALARY,
                            onClick = { category = EntryCategory.SHEPHERD_SALARY },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) { Text(strings.salaryOption) }
                    }
                }

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; validationError = null },
                    label = { Text(strings.fieldTitle) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_title_input")
                )

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { raw ->
                        if (raw.all { it.isDigit() || it == '.' }) {
                            amountText = raw
                            validationError = null
                        }
                    },
                    label = { Text(strings.fieldAmount) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_amount_input")
                )

                // Sheep Count (for Livestock Buy / Sell)
                if (category == EntryCategory.LIVESTOCK_BUY || category == EntryCategory.LIVESTOCK_SELL) {
                    OutlinedTextField(
                        value = sheepCountText,
                        onValueChange = { raw ->
                            if (raw.all { it.isDigit() }) {
                                sheepCountText = raw
                                validationError = null
                            }
                        },
                        label = { Text(strings.fieldSheepCount) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("entry_sheep_count_input")
                    )
                }

                // Shepherd Name (for Advance / Salary)
                if (category == EntryCategory.SHEPHERD_ADVANCE || category == EntryCategory.SHEPHERD_SALARY) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (shepherds.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                shepherds.forEach { sh ->
                                    FilterChip(
                                        selected = shepherdName == sh,
                                        onClick = { shepherdName = sh; validationError = null },
                                        label = { Text(sh) }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = shepherdName,
                            onValueChange = { shepherdName = it; validationError = null },
                            label = { Text(strings.fieldShepherdName) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("entry_shepherd_input")
                        )
                    }
                }

                // Date Selector
                val formattedDate = remember(entryDateMillis) {
                    SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(entryDateMillis))
                }
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = strings.fieldDate)
                    Spacer(Modifier.size(8.dp))
                    Text("${strings.fieldDate}: $formattedDate")
                }

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(strings.fieldNote) },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_note_input")
                )

                // Bill / Receipt Photo Attachment
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.attachBillPhoto, style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_photo_button")
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = strings.pickFromGallery, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(strings.pickFromGallery)
                        }
                        OutlinedButton(
                            onClick = {
                                val uri = ImageReceiptHelper.createCameraTempUri(context)
                                pendingCameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("camera_photo_button")
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = strings.takePhotoCamera, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(strings.takePhotoCamera)
                        }
                    }

                    if (receiptBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        ) {
                            Image(
                                bitmap = receiptBitmap.asImageBitmap(),
                                contentDescription = strings.photoAttached,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            TextButton(
                                onClick = { receiptBase64 = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            ) {
                                Text(strings.removePhoto, color = Color.White)
                            }
                        }
                    }
                }

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (editingEntry != null && onDelete != null) {
                        OutlinedButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("delete_entry_button")
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = strings.deleteEntry, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(strings.deleteEntry)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel)
                    }
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull()
                            val count = sheepCountText.toLongOrNull() ?: 0L
                            when {
                                title.isBlank() -> validationError = strings.errorMissingTitle
                                amt == null || amt <= 0.0 -> validationError = strings.errorInvalidAmount
                                (category == EntryCategory.LIVESTOCK_BUY || category == EntryCategory.LIVESTOCK_SELL) && count <= 0L ->
                                    validationError = strings.errorMissingSheepCount
                                (category == EntryCategory.SHEPHERD_ADVANCE || category == EntryCategory.SHEPHERD_SALARY) && shepherdName.isBlank() ->
                                    validationError = strings.errorMissingShepherd
                                else -> onSave(
                                    category,
                                    title.trim(),
                                    amt,
                                    count,
                                    shepherdName.trim(),
                                    note.trim(),
                                    receiptBase64,
                                    entryDateMillis,
                                )
                            }
                        },
                        modifier = Modifier.testTag("save_entry_button")
                    ) {
                        Text(if (editingEntry == null) strings.saveEntry else strings.updateEntry)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = entryDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { entryDateMillis = it }
                    showDatePicker = false
                }) { Text(strings.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(strings.cancel) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun defaultTitleForCategory(category: EntryCategory, strings: AppStrings): String = when (category) {
    EntryCategory.EXPENSE -> ""
    EntryCategory.MEDICINE_VET -> ""
    EntryCategory.LIVESTOCK_BUY -> strings.catLivestockBuy
    EntryCategory.LIVESTOCK_SELL -> strings.catLivestockSell
    EntryCategory.SHEPHERD_ADVANCE -> strings.catShepherdAdvance
    EntryCategory.SHEPHERD_SALARY -> strings.catShepherdSalary
    EntryCategory.OWNER_FUNDS -> strings.catOwnerFunds
}

@Composable
fun ReceiptViewerDialog(
    base64: String,
    title: String,
    strings: AppStrings,
    onDismiss: () -> Unit,
) {
    val bmp = remember(base64) { ImageReceiptHelper.decodeBase64ToBitmap(base64) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.cancel)
                    }
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = strings.viewReceipt,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onDismiss)
                    )
                }
            }
        }
    }
}
