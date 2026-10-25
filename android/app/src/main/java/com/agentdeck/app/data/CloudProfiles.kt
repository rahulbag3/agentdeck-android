package com.agentdeck.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Tolerant JSON codec for the saved cloud API profiles. */
object CloudProfilesCodec {

    fun encode(profiles: List<CloudProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("label", profile.label)
                    .put("baseUrl", profile.baseUrl)
                    .put("models", JSONArray(profile.models))
                    .put("lastModel", profile.lastModel)
                    .put("keySaved", profile.keySaved)
            )
        }
        return array.toString()
    }

    fun decode(json: String): List<CloudProfile> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val id = obj.optString("id").trim()
            val baseUrl = obj.optString("baseUrl").trim()
            if (id.isEmpty() || baseUrl.isEmpty()) return@mapNotNull null
            val models = obj.optJSONArray("models") ?: JSONArray()
            CloudProfile(
                id = id,
                label = obj.optString("label").ifBlank { CloudClient.providerLabel(baseUrl) },
                baseUrl = baseUrl,
                models = (0 until models.length())
                    .map { models.optString(it) }
                    .filter { it.isNotBlank() }
                    .distinct(),
                lastModel = obj.optString("lastModel"),
                keySaved = obj.optBoolean("keySaved", false)
            )
        }
    }
}
