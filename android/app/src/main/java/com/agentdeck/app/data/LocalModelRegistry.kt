package com.agentdeck.app.data

import org.json.JSONArray
import org.json.JSONObject

/** A GGUF file the user imported into app storage. */
data class ImportedModel(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val importedAt: Long
)

/** Persisted catalogue of imported local models. */
internal object LocalModelRegistry {

    fun encode(models: List<ImportedModel>): String =
        JSONArray().also { array ->
            models.forEach { model ->
                array.put(
                    JSONObject()
                        .put("id", model.id)
                        .put("name", model.name)
                        .put("path", model.path)
                        .put("sizeBytes", model.sizeBytes)
                        .put("importedAt", model.importedAt)
                )
            }
        }.toString()

    fun decode(json: String): List<ImportedModel> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val models = ArrayList<ImportedModel>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val path = item.optString("path").takeIf { it.isNotBlank() } ?: continue
            models += ImportedModel(
                id = id,
                name = item.optString("name", "Local model"),
                path = path,
                sizeBytes = item.optLong("sizeBytes", 0L),
                importedAt = item.optLong("importedAt", 0L)
            )
        }
        return models
    }
}
