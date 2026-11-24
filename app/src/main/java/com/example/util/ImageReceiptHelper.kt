package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

object ImageReceiptHelper {

    fun createCameraTempUri(context: Context): Uri {
        val cacheDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(cacheDir, "receipt_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    suspend fun compressUriToBase64(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            val maxDim = 640
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDim * 2 || options.outHeight / sampleSize > maxDim * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return@withContext null

            val ratio = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height, 1f)
            val scaled = if (ratio < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }

            var quality = 65
            var encoded: String
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                quality -= 10
            } while (encoded.length > 260_000 && quality >= 25)

            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()

            if (encoded.length <= 340_000) encoded else null
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBase64ToBitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}
