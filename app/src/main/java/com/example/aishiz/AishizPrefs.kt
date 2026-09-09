package com.example.aishiz

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AishizPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getModels(): MutableList<ModelInfo> {
        val raw = prefs.getString(KEY_MODELS, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { index ->
                val obj = arr.getJSONObject(index)
                ModelInfo(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    uri = obj.getString("uri")
                )
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun addModel(name: String, uriString: String): ModelInfo {
        getModels().firstOrNull { it.uri == uriString }?.let { return it }

        val models = getModels()
        val model = ModelInfo(
            id = UUID.randomUUID().toString(),
            name = name,
            uri = uriString
        )
        models.add(model)
        saveModels(models)
        saveParams(model.id, InferenceParams())

        if (getSelectedModelId() == null) {
            setSelectedModelId(model.id)
        }
        return model
    }

    fun removeModel(modelId: String) {
        val models = getModels().filterNot { it.id == modelId }
        saveModels(models)
        prefs.edit().remove(paramsKey(modelId)).apply()

        if (getSelectedModelId() == modelId) {
            setSelectedModelId(models.firstOrNull()?.id)
        }
    }

    fun getSelectedModelId(): String? = prefs.getString(KEY_SELECTED_MODEL, null)

    fun setSelectedModelId(modelId: String?) {
        prefs.edit().putString(KEY_SELECTED_MODEL, modelId).apply()
    }

    fun getParams(modelId: String): InferenceParams {
        val raw = prefs.getString(paramsKey(modelId), null) ?: return InferenceParams()
        return try {
            val o = JSONObject(raw)
            InferenceParams(
                temperature = o.optDouble("temperature", 0.70).toFloat(),
                topP = o.optDouble("topP", 0.95).toFloat(),
                topK = o.optInt("topK", 40),
                minP = o.optDouble("minP", 0.05).toFloat(),
                repeatPenalty = o.optDouble("repeatPenalty", 1.10).toFloat(),
                maxTokens = o.optInt("maxTokens", 384),
                contextLength = o.optInt("contextLength", 2048),
                batchSize = o.optInt("batchSize", 256),
                threads = o.optInt("threads", 0),
                seed = o.optInt("seed", -1)
            )
        } catch (_: Exception) {
            InferenceParams()
        }
    }

    fun saveParams(modelId: String, p: InferenceParams) {
        val o = JSONObject().apply {
            put("temperature", p.temperature.toDouble())
            put("topP", p.topP.toDouble())
            put("topK", p.topK)
            put("minP", p.minP.toDouble())
            put("repeatPenalty", p.repeatPenalty.toDouble())
            put("maxTokens", p.maxTokens)
            put("contextLength", p.contextLength)
            put("batchSize", p.batchSize)
            put("threads", p.threads)
            put("seed", p.seed)
        }
        prefs.edit().putString(paramsKey(modelId), o.toString()).apply()
    }

    private fun saveModels(models: List<ModelInfo>) {
        val arr = JSONArray()
        models.forEach { model ->
            arr.put(JSONObject().apply {
                put("id", model.id)
                put("name", model.name)
                put("uri", model.uri)
            })
        }
        prefs.edit().putString(KEY_MODELS, arr.toString()).apply()
    }

    private fun paramsKey(modelId: String) = "params_$modelId"

    companion object {
        private const val PREFS_NAME = "aishiz_prefs"
        private const val KEY_MODELS = "models"
        private const val KEY_SELECTED_MODEL = "selected_model"
    }
}
