package com.agentdeck.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Turns image attachments into data URLs that OpenAI-compatible vision models accept. */
internal object ImageInput {
    const val MAX_IMAGES = 3
    private const val MAX_SOURCE_BYTES = 20L * 1024 * 1024
    private const val MAX_DIMENSION = 1_408
    private const val JPEG_QUALITY = 85
    private const val MAX_ENCODED_BYTES = 5 * 1024 * 1024

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif"
    )

    fun isImage(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    fun dataUrl(file: File): String? {
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_SOURCE_BYTES) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION) sample *= 2
        val decoded = runCatching {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inSampleSize = sample
            })
        }.getOrNull() ?: return null

        val longest = max(decoded.width, decoded.height)
        val scaled = if (longest > MAX_DIMENSION) {
            val ratio = MAX_DIMENSION.toFloat() / longest
            runCatching {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).roundToInt().coerceAtLeast(1),
                    (decoded.height * ratio).roundToInt().coerceAtLeast(1),
                    true
                )
            }.getOrNull() ?: decoded
        } else {
            decoded
        }
        if (scaled !== decoded) decoded.recycle()

        val png = scaled.hasAlpha()
        val out = ByteArrayOutputStream()
        val compressed = runCatching {
            scaled.compress(
                if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                JPEG_QUALITY,
                out
            )
        }.getOrDefault(false)
        scaled.recycle()
        if (!compressed) return null

        val bytes = out.toByteArray()
        if (bytes.isEmpty() || bytes.size > MAX_ENCODED_BYTES) return null
        val mime = if (png) "image/png" else "image/jpeg"
        return "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
