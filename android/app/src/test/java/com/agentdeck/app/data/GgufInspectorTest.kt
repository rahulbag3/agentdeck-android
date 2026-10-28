package com.agentdeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class GgufInspectorTest {

    @Test
    fun `text-only qwen35 gguf reports no vision tensors`() {
        val info = inspect(
            architecture = "qwen35",
            tensors = listOf("blk.0.attn_q.weight", "token_embd.weight")
        )

        assertEquals("qwen35", info.architecture)
        assertEquals(2, info.tensorCount.toInt())
        assertEquals(0, info.visionTensors)
        assertFalse(info.hasEmbeddedVision)
        assertFalse(info.projector)
        assertFalse(GgufInspector.knownVisionFamily("Qwen3.5-4B-Q4_K_M"))
    }

    @Test
    fun `a vision-language model file is detected from its tensors and name`() {
        val info = inspect(
            architecture = "qwen3vl",
            tensors = listOf("v.blk.0.attn_q.weight", "token_embd.weight")
        )

        assertEquals(1, info.visionTensors)
        assertTrue(info.hasEmbeddedVision)
        assertFalse(info.projector)
        assertTrue(GgufInspector.knownVisionFamily("Qwen3VL-4B-Instruct-Q4_K_M"))
    }

    @Test
    fun `a clip architecture is treated as a projector`() {
        val info = inspect(
            architecture = "clip",
            tensors = listOf("v.patch_embd.weight"),
            visionMetadata = true
        )

        assertTrue(info.projector)
        assertTrue(info.hasVisionMetadata)
        assertTrue(info.visionTensors > 0)
    }

    @Test
    fun `malformed files are rejected`() {
        val file = File.createTempFile("bad-gguf", ".gguf")
        try {
            file.writeText("not a gguf")
            assertNull(GgufInspector.inspect(file))
        } finally {
            file.delete()
        }
    }

    private fun inspect(
        architecture: String?,
        tensors: List<String>,
        visionMetadata: Boolean = false
    ): GgufInfo {
        val file = File.createTempFile("test-gguf", ".gguf")
        try {
            file.writeBytes(buildGguf(architecture, tensors, visionMetadata))
            return GgufInspector.inspect(file)
                ?: throw AssertionError("GGUF was not parsed")
        } finally {
            file.delete()
        }
    }

    private fun buildGguf(
        architecture: String?,
        tensors: List<String>,
        visionMetadata: Boolean
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("GGUF".toByteArray(Charsets.US_ASCII))
        out.u32(3)
        out.u64(tensors.size.toLong())
        var kvCount = 0L
        if (architecture != null) kvCount++
        if (visionMetadata) kvCount++
        out.u64(kvCount)
        architecture?.let {
            out.string("general.architecture")
            out.u32(8)
            out.string(it)
        }
        if (visionMetadata) {
            out.string("clip.has_vision_encoder")
            out.u32(7)
            out.write(1)
        }
        tensors.forEach { name ->
            out.string(name)
            out.u32(1)
            out.u64(1)
            out.u32(0)
            out.u64(0)
        }
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.u32(value: Int) {
        write(byteArrayOf(
            (value and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 24) and 0xFF).toByte()
        ))
    }

    private fun ByteArrayOutputStream.u64(value: Long) {
        val bytes = ByteArray(8)
        for (index in 0 until 8) {
            bytes[index] = ((value ushr (index * 8)) and 0xFF).toByte()
        }
        write(bytes)
    }

    private fun ByteArrayOutputStream.string(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        u64(bytes.size.toLong())
        write(bytes)
    }
}
