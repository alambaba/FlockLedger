package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.counter.SheepCountRecord
import com.example.data.model.Farm
import com.example.data.model.FarmSummary
import com.example.i18n.AppStrings
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.LivestockBuyTeal
import com.example.ui.theme.LivestockSellGreen
import com.example.ui.theme.PendingAmber
import com.example.util.ImageReceiptHelper
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private data class PhotoMarkerNormalized(
    val nx: Float,
    val ny: Float,
)

@Composable
fun SheepCounterScreen(
    farm: Farm,
    summary: FarmSummary,
    history: List<SheepCountRecord>,
    strings: AppStrings,
    onPersistCameraPhoto: suspend (Uri) -> String?,
    onSaveCountResult: (countedSheep: Int, note: String, photoFilePath: String?) -> Unit,
    onShareResultFile: (filePath: String) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var countedSheep by remember { mutableIntStateOf(0) }
    var noteText by remember { mutableStateOf("") }
    var currentPhotoPath by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val photoMarkers = remember { mutableStateListOf<PhotoMarkerNormalized>() }

    val decodedPhotoBitmap: Bitmap? = remember(currentPhotoPath) {
        currentPhotoPath?.let { decodeScaledBitmapFromFile(it, maxDim = 1024) }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            scope.launch {
                val persisted = onPersistCameraPhoto(uri)
                if (!persisted.isNullOrBlank()) {
                    currentPhotoPath = persisted
                    photoMarkers.clear()
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = ImageReceiptHelper.createCameraTempUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            onShowMessage(strings.counterCameraPermissionDenied)
        }
    }

    val launchCamera = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val uri = ImageReceiptHelper.createCameraTempUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val expectedSheep = summary.currentFlockCount.toInt()
    val variance = countedSheep - expectedSheep

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Counter Header & Live Tally Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.counterPageTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = farm.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        ) {
                            Text(
                                text = "${strings.counterExpectedFlock}: $expectedSheep",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 16.dp, horizontal = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = strings.counterCountLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = countedSheep.toString(),
                                fontSize = 52.sp,
                                lineHeight = 58.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("counter_value_text"),
                            )
                            val varianceColor = when {
                                variance == 0 -> LivestockSellGreen
                                variance > 0 -> LivestockBuyTeal
                                else -> ExpenseRed
                            }
                            val varianceBadgeText = if (variance == 0) {
                                strings.counterMatchedLabel
                            } else {
                                val sign = if (variance > 0) "+$variance" else variance.toString()
                                "${strings.counterVarianceLabel} $sign"
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = varianceColor.copy(alpha = 0.14f),
                            ) {
                                Text(
                                    text = varianceBadgeText,
                                    color = varianceColor,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    // Increment & Decrement Controls (48.dp+ touch targets)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { if (countedSheep > 0) countedSheep -= 1 },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("counter_minus_1_button"),
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "-1")
                            Text("1", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { countedSheep += 1 },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            modifier = Modifier
                                .weight(1.35f)
                                .testTag("counter_plus_1_button"),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "+1")
                            Text("1", fontWeight = FontWeight.ExtraBold)
                        }
                        FilledTonalButton(
                            onClick = { countedSheep += 5 },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("counter_plus_5_button"),
                        ) {
                            Text("+5", fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { countedSheep += 10 },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("counter_plus_10_button"),
                        ) {
                            Text("+10", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Camera Button & Reset Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = launchCamera,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("counter_camera_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = strings.counterCameraBtn,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = strings.counterCameraBtn,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                countedSheep = 0
                                photoMarkers.clear()
                                currentPhotoPath = null
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            modifier = Modifier.testTag("counter_reset_button"),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = strings.counterResetBtn, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(strings.counterResetBtn)
                        }
                    }
                }
            }
        }

        // Interactive Camera Photo Canvas (Tap sheep to drop numbered markers)
        if (decodedPhotoBitmap != null) {
            item {
                val aspect = (decodedPhotoBitmap.width.toFloat() / decodedPhotoBitmap.height.coerceAtLeast(1).toFloat())
                    .coerceIn(0.65f, 1.85f)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = strings.counterTapPhotoHint,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspect)
                                .clip(RoundedCornerShape(16.dp))
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        if (size.width > 0 && size.height > 0) {
                                            photoMarkers.add(
                                                PhotoMarkerNormalized(
                                                    nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f),
                                                    ny = (offset.y / size.height.toFloat()).coerceIn(0f, 1f),
                                                )
                                            )
                                            countedSheep += 1
                                        }
                                    }
                                }
                        ) {
                            val density = LocalDensity.current
                            val markerSize = 26.dp
                            val halfPx = with(density) { (markerSize / 2).toPx() }
                            val boxWidthPx = with(density) { maxWidth.toPx() }
                            val boxHeightPx = with(density) { maxHeight.toPx() }

                            Image(
                                bitmap = decodedPhotoBitmap.asImageBitmap(),
                                contentDescription = strings.counterPhotoAttachedLabel,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize(),
                            )
                            photoMarkers.forEachIndexed { idx, marker ->
                                val offsetX = (marker.nx * boxWidthPx - halfPx).roundToInt()
                                val offsetY = (marker.ny * boxHeightPx - halfPx).roundToInt()
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(offsetX, offsetY) }
                                        .size(markerSize)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00897B))
                                        .border(1.5.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (photoMarkers.isNotEmpty()) {
                                        photoMarkers.removeAt(photoMarkers.lastIndex)
                                        if (countedSheep > 0) countedSheep -= 1
                                    }
                                },
                                enabled = photoMarkers.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("counter_undo_marker_button"),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("${strings.counterUndoMarker} (${photoMarkers.size})")
                            }
                            TextButton(
                                onClick = {
                                    val removeCount = photoMarkers.size
                                    photoMarkers.clear()
                                    countedSheep = (countedSheep - removeCount).coerceAtLeast(0)
                                },
                                enabled = photoMarkers.isNotEmpty(),
                                modifier = Modifier.testTag("counter_clear_markers_button"),
                            ) {
                                Icon(Icons.Rounded.ClearAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(strings.counterClearMarkers)
                            }
                        }
                    }
                }
            }
        }

        // Note Input & Save Results in File (Date & Time)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text(strings.counterNoteHint) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("counter_note_input"),
                    )
                    Button(
                        onClick = {
                            onSaveCountResult(countedSheep, noteText, currentPhotoPath)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LivestockSellGreen,
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("counter_save_file_button"),
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = strings.counterSaveToFileBtn, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = strings.counterSaveToFileBtn,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }

        // Saved Count Result Files History Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = strings.counterHistoryHeading,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (history.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Text(
                        text = strings.counterNoHistoryYet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
        } else {
            items(history, key = { it.id }) { record ->
                SheepCountHistoryCard(
                    record = record,
                    strings = strings,
                    onShareClick = { onShareResultFile(record.internalResultFilePath) },
                    modifier = Modifier.widthIn(max = 640.dp),
                )
            }
        }
    }
}

@Composable
private fun SheepCountHistoryCard(
    record: SheepCountRecord,
    strings: AppStrings,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumb = remember(record.photoFilePath) {
        record.photoFilePath?.let { decodeScaledBitmapFromFile(it, maxDim = 256) }
    }
    val diffColor = when {
        record.variance == 0 -> LivestockSellGreen
        record.variance > 0 -> LivestockBuyTeal
        else -> PendingAmber
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = record.formattedDateTime,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = record.resultFileName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = diffColor.copy(alpha = 0.14f),
                ) {
                    val sign = if (record.variance > 0) "+${record.variance}" else record.variance.toString()
                    Text(
                        text = "${record.countedSheep} / ${record.expectedSheep} ($sign)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = diffColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            if (record.note.isNotBlank()) {
                Text(
                    text = record.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (thumb != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = strings.counterPhotoAttachedLabel,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Text(
                        text = strings.counterPhotoAttachedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${strings.counterSavedInFilesDir} ${record.resultFileName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (record.downloadsSaved) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = LivestockSellGreen,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = strings.counterSavedInDownloads,
                            style = MaterialTheme.typography.labelSmall,
                            color = LivestockSellGreen,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onShareClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("counter_share_${record.id}"),
            ) {
                Icon(Icons.Rounded.Share, contentDescription = strings.counterShareFileAction, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(strings.counterShareFileAction, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun decodeScaledBitmapFromFile(path: String, maxDim: Int): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        val rawW = opts.outWidth
        val rawH = opts.outHeight
        if (rawW <= 0 || rawH <= 0) return null
        var sample = 1
        while ((rawW / sample) > maxDim || (rawH / sample) > maxDim) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, decodeOpts)
    } catch (_: Exception) {
        null
    }
}
