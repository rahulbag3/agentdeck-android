package com.agentdeck.app.data

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * What a GGUF file actually is. Import uses this instead of trusting a model
 * card: a multimodal base model can be converted to a text-only GGUF.
 */
internal data class GgufInfo(
    val architecture: String?,
    val name: String?,
    val tensorCount: Long,
    val visionTensors: Int,
    val hasVisionMetadata: Boolean,
    val projector: Boolean
) {
    val hasEmbeddedVision: Boolean get() = visionTensors > 0 || hasVisionMetadata
}

internal object GgufInspector {
    private const val MAX_METADATA_BYTES = 128L * 1024 * 1024
    private const val MAX_STRING = 4 * 1024 * 1024
    private const val MAX_TENSORS = 1_000_000L
    private const val MAX_DIMS = 8

    private val VISION_FAMILIES = listOf(
        "qwen3vl", "qwen3-vl", "qwen2.5vl", "qwen2.5-vl",
        "qwen3.5", "qwen3-5", "qwen35",
        "gemma-3", "gemma3", "gemma-4", "gemma4",
        "llava", "minicpm-v", "internvl", "pixtral", "smolvlm",
        "moondream", "paligemma", "idefics", "mllama", "vision"
    )

    fun knownVisionFamily(name: String): Boolean {
        val lower = name.lowercase()
        return VISION_FAMILIES.any { lower.contains(it) }
    }

    fun isVisionTensorName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("v.") ||
            lower.startsWith("mm.") ||
            lower.contains("vision") ||
            lower.contains("visual") ||
            lower.contains("patch_embd") ||
            lower.contains("image_newline") ||
            lower.contains("image_pad") ||
            lower.contains("projector") ||
            lower.contains("merger") ||
            lower.contains("resampler")
    }

    fun inspect(file: File): GgufInfo? {
        if (!file.isFile || file.length() < 24) return null
        return runCatching {
            BufferedInputStream(FileInputStream(file), 1 shl 20).use { input ->
                val reader = Reader(input)
                val magic = reader.bytes(4) ?: return@use null
                if (String(magic) != "GGUF") return@use null
                val version = reader.u32() ?: return@use null
                if (version < 2 || version > 4) return@use null
                val tensorCount = reader.u64() ?: return@use null
                val kvCount = reader.u64() ?: return@use null
                if (tensorCount > MAX_TENSORS || kvCount > MAX_TENSORS) return@use null

                var architecture: String? = null
                var modelName: String? = null
                var visionMetadata = false
                var projectorMetadata = false

                var i = 0L
                while (i < kvCount) {
                    val key = reader.string(MAX_STRING) ?: return@use null
                    val type = reader.u32() ?: return@use null
                    val lower = key.lowercase()
                    when (type) {
                        8 -> {
                            val value = reader.string(MAX_STRING) ?: return@use null
                            when (lower) {
                                "general.architecture" -> architecture = value
                                "general.name" -> modelName = value
                                "clip.projector_type" -> projectorMetadata = true
                            }
                        }
                        9 -> {
                            val elementType = reader.u32() ?: return@use null
                            val count = reader.u64() ?: return@use null
                            if (!reader.skipArray(elementType, count)) return@use null
                        }
                        else -> if (!reader.skipScalar(type)) return@use null
                    }
                    if (lower.startsWith("clip.") || lower.startsWith("vision.") ||
                        lower.contains("vision") || lower.contains("mmproj") ||
                        lower.contains("multimodal")
                    ) {
                        visionMetadata = true
                    }
                    i++
                }

                var visionTensors = 0
                i = 0L
                while (i < tensorCount) {
                    val tensorName = reader.string(MAX_STRING) ?: return@use null
                    val dims = reader.u32() ?: return@use null
                    if (dims > MAX_DIMS) return@use null
                    for (dim in 0 until dims) {
                        if (reader.u64() == null) return@use null
                    }
                    if (reader.u32() == null) return@use null
                    if (reader.u64() == null) return@use null
                    if (isVisionTensorName(tensorName)) visionTensors++
                    i++
                }

                val projector = architecture.equals("clip", ignoreCase = true) ||
                    projectorMetadata
                GgufInfo(
                    architecture = architecture,
                    name = modelName,
                    tensorCount = tensorCount,
                    visionTensors = visionTensors,
                    hasVisionMetadata = visionMetadata,
                    projector = projector
                )
            }
        }.getOrNull()
    }

    private class Reader(private val input: InputStream) {
        private var total = 0L

        fun bytes(count: Int): ByteArray? {
            if (count < 0 || count > MAX_STRING) return null
            val out = ByteArray(count)
            var at = 0
            while (at < count) {
                val read = input.read(out, at, count - at)
                if (read < 0) return null
                at += read
                total += read
                if (total > MAX_METADATA_BYTES) return null
            }
            return out
        }

        fun u32(): Int? {
            val bytes = bytes(4) ?: return null
            return (bytes[0].toInt() and 0xFF) or
                ((bytes[1].toInt() and 0xFF) shl 8) or
                ((bytes[2].toInt() and 0xFF) shl 16) or
                ((bytes[3].toInt() and 0xFF) shl 24)
        }

        fun u64(): Long? {
            val bytes = bytes(8) ?: return null
            var value = 0L
            for (index in 7 downTo 0) {
                value = (value shl 8) or (bytes[index].toLong() and 0xFF)
            }
            return value.takeIf { it >= 0 }
        }

        fun string(max: Int): String? {
            val length = u64() ?: return null
            if (length > max) return null
            val data = bytes(length.toInt()) ?: return null
            return String(data, Charsets.UTF_8)
        }

        fun skipScalar(type: Int): Boolean {
            val size = when (type) {
                0, 1, 7 -> 1
                2, 3 -> 2
                4, 5, 6 -> 4
                10, 11, 12 -> 8
                else -> return false
            }
            return skipFully(size.toLong())
        }

        fun skipArray(elementType: Int, count: Long): Boolean {
            if (count < 0 || count > MAX_TENSORS * 10) return false
            when (elementType) {
                8 -> {
                    var index = 0L
                    while (index < count) {
                        if (string(MAX_STRING) == null) return false
                        index++
                    }
                }
                9 -> {
                    var index = 0L
                    while (index < count) {
                        val nestedType = u32() ?: return false
                        val nestedCount = u64() ?: return false
                        if (!skipArray(nestedType, nestedCount)) return false
                        index++
                    }
                }
                else -> {
                    val size = when (elementType) {
                        0, 1, 7 -> 1
                        2, 3 -> 2
                        4, 5, 6 -> 4
                        10, 11, 12 -> 8
                        else -> return false
                    }
                    if (!skipFully(size.toLong() * count)) return false
                }
            }
            return true
        }

        private fun skipFully(count: Long): Boolean {
            if (count < 0) return false
            var remaining = count
            val buffer = ByteArray(64 * 1024)
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) return false
                remaining -= read
                total += read
                if (total > MAX_METADATA_BYTES) return false
            }
            return true
        }
    }
}
