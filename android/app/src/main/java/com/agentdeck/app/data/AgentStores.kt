package com.agentdeck.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Durable agent memory: short facts the model chooses to save and recall
 * across conversations. Plain JSON on app storage, capped — no secrets belong
 * here and entries are user-inspectable via Settings → Memory.
 */
class MemoryStore(filesDir: File) {

    data class Entry(val id: Long, val text: String, val at: Long)

    private val file = File(filesDir, "agent-memory.json")
    private val entries = mutableListOf<Entry>()
    private var nextId = 1L

    init {
        runCatching {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val text = obj.optString("text").trim()
                if (text.isEmpty()) continue
                val id = obj.optLong("id", nextId)
                entries += Entry(id, text.take(MAX_ENTRY_CHARS), obj.optLong("at", 0L))
                nextId = maxOf(nextId, id + 1)
            }
        }
        entries.sortByDescending { it.at }
    }

    @Synchronized
    fun save(text: String): Entry {
        val trimmed = text.trim().take(MAX_ENTRY_CHARS)
        if (trimmed.isEmpty()) return Entry(nextId, "", 0L)
        // Refresh an existing entry with the same normalized text.
        entries.removeAll { it.text.equals(trimmed, ignoreCase = true) }
        val entry = Entry(nextId++, trimmed, System.currentTimeMillis())
        entries.add(0, entry)
        while (entries.size > MAX_ENTRIES) entries.removeAt(entries.size - 1)
        persist()
        return entry
    }

    @Synchronized
    fun recall(query: String, max: Int = 8): List<Entry> {
        val terms = query.lowercase().split(Regex("[^a-z0-9\\u4e00-\\u9fff]+")).filter { it.length >= 2 }
        if (query.isBlank()) return entries.take(max)
        return entries.filter { entry ->
            val lower = entry.text.lowercase()
            terms.any { lower.contains(it) }
        }.ifEmpty { entries.take(max) }.take(max)
    }

    @Synchronized
    fun all(): List<Entry> = entries.toList()

    @Synchronized
    fun delete(id: Long): Boolean {
        val removed = entries.removeAll { it.id == id }
        if (removed) persist()
        return removed
    }

    @Synchronized
    fun clear() {
        entries.clear()
        persist()
    }

    @Synchronized
    fun snapshot(): String =
        if (entries.isEmpty()) "" else entries.take(10).joinToString("\n") { "- ${it.text.take(200)}" }

    private fun persist() {
        runCatching {
            val array = JSONArray()
            entries.forEach { array.put(JSONObject().put("id", it.id).put("text", it.text).put("at", it.at)) }
            file.writeText(array.toString())
        }
    }

    companion object {
        const val MAX_ENTRIES = 60
        const val MAX_ENTRY_CHARS = 600
    }
}

/** Reusable instruction sets ("skills"): named prompt fragments the user curates. */
class InstructionStore(filesDir: File) {

    data class Instruction(val id: String, val name: String, val body: String)

    private val file = File(filesDir, "agent-instructions.json")
    private val items = mutableListOf<Instruction>()

    init {
        runCatching {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val name = obj.optString("name").trim()
                val body = obj.optString("body").trim()
                if (id.isNotEmpty() && name.isNotEmpty() && body.isNotEmpty()) {
                    items += Instruction(id, name.take(60), body.take(MAX_BODY_CHARS))
                }
            }
        }
    }

    @Synchronized
    fun all(): List<Instruction> = items.toList()

    @Synchronized
    fun upsert(id: String?, name: String, body: String): Instruction {
        val cleanName = name.trim().take(60)
        val cleanBody = body.trim().take(MAX_BODY_CHARS)
        val entry = Instruction(id ?: "ins-" + UUID_SAFE.take(6), cleanName, cleanBody)
        items.removeAll { it.id == entry.id || it.name.equals(cleanName, ignoreCase = true) }
        items.add(0, entry)
        persist()
        return entry
    }

    @Synchronized
    fun delete(id: String) {
        items.removeAll { it.id == id }
        persist()
    }

    private fun persist() {
        runCatching {
            val array = JSONArray()
            items.forEach {
                array.put(
                    JSONObject().put("id", it.id).put("name", it.name).put("body", it.body)
                )
            }
            file.writeText(array.toString())
        }
    }

    companion object {
        const val MAX_BODY_CHARS = 4_000
        private val UUID_SAFE = java.util.UUID.randomUUID().toString()
    }
}
