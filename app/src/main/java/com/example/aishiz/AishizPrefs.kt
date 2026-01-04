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
            val out = mutableListOf<ModelInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                out.add(
                    ModelInfo(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        uri = obj.getString("uri")
                    )
                )
            }
            out
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun addModel(name: String, uriString: String): ModelInfo {
        val models = getModels()
        val model = ModelInfo(
            id = UUID.randomUUID().toString(),
            name = name,
            uri = uriString
        )
        models.add(model)
        saveModels(models)

        if (prefs.getString(paramsKey(model.id), null) == null) {
            saveParams(model.id, InferenceParams())
        }
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
                repeatPenalty = o.optDouble("repeatPenalty", 1.10).toFloat(),
                maxTokens = o.optInt("maxTokens", 256),
                contextLength = o.optInt("contextLength", 2048),
                seed = o.optInt("seed", -1)
            )
        } catch (_: Exception) {
            InferenceParams()
        }
    }

    fun saveParams(modelId: String, p: InferenceParams) {
        val o = JSONObject()
        o.put("temperature", p.temperature.toDouble())
        o.put("topP", p.topP.toDouble())
        o.put("topK", p.topK)
        o.put("repeatPenalty", p.repeatPenalty.toDouble())
        o.put("maxTokens", p.maxTokens)
        o.put("contextLength", p.contextLength)
        o.put("seed", p.seed)
        prefs.edit().putString(paramsKey(modelId), o.toString()).apply()
    }

    private fun saveModels(models: List<ModelInfo>) {
        val arr = JSONArray()
        for (m in models) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("name", m.name)
            obj.put("uri", m.uri)
            arr.put(obj)
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
