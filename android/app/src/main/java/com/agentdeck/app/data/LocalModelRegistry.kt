package com.agentdeck.app.data

import org.json.JSONArray
import org.json.JSONObject

/** A GGUF file the user imported into app storage. */
data class ImportedModel(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val importedAt: Long,
    val mmprojPath: String? = null,
    val visionCapable: Boolean = false,
    val visionChecked: Boolean = false
)

/** Persisted catalogue of imported local models. */
internal object LocalModelRegistry {

    /**
     * Matches an `mmproj-*.gguf` file to the model it belongs to. Quantization
     * tokens are ignored so Qwen3VL-4B-Instruct-Q4_K_M pairs with
     * mmproj-Qwen3VL-4B-Instruct-Q8_0.
     */
    fun visionNameTokens(name: String): List<String> {
        val clean = name.substringAfterLast('/')
            .removeSuffix(".gguf")
            .removePrefix("mmproj-")
            .replace(Regex("(?i)[-_](q\\d[\\w_]*|f16|f32|bf16)$"), "")
        return clean.split(Regex("[^A-Za-z0-9]+"))
            .map { it.lowercase() }
            .filter { it.isNotBlank() && !it.matches(Regex("q\\d.*")) }
    }

    fun projectorMatchesModel(modelName: String, projectorName: String): Boolean {
        val model = visionNameTokens(modelName)
        val projector = visionNameTokens(projectorName)
        if (model.size < 2 || projector.size < 2) return false
        val shared = minOf(model.size, projector.size)
        if (model.take(shared) != projector.take(shared)) return false
        return model.size == shared || projector.size == shared
    }

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
                        .put("visionCapable", model.visionCapable)
                        .put("visionChecked", model.visionChecked)
                        .apply { model.mmprojPath?.let { put("mmprojPath", it) } }
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
                importedAt = item.optLong("importedAt", 0L),
                mmprojPath = item.optString("mmprojPath").takeIf { it.isNotBlank() },
                visionCapable = item.optBoolean("visionCapable", false),
                visionChecked = item.optBoolean("visionChecked", false)
            )
        }
        return models
    }
}
