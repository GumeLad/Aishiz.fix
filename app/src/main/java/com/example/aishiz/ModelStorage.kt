package com.example.aishiz

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ModelStorage {

    fun ensureLocalModelFile(context: Context, model: ModelInfo): File {
        require(model.name.endsWith(".gguf", ignoreCase = true)) {
            "Only GGUF models are supported."
        }

        val modelsDir = File(context.filesDir, "models")
        check(modelsDir.exists() || modelsDir.mkdirs()) {
            "Unable to create the model storage directory."
        }

        val outFile = File(modelsDir, "${model.id}.gguf")
        if (outFile.exists() && outFile.length() > 0L) return outFile

        val tempFile = File(modelsDir, "${model.id}.gguf.partial")
        tempFile.delete()

        try {
            val uri = Uri.parse(model.uri)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open model. Re-add it from Models." }
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE * 8)
                    output.fd.sync()
                }
            }

            require(tempFile.length() > 0L) { "The selected model file is empty." }

            if (outFile.exists()) outFile.delete()
            check(tempFile.renameTo(outFile)) {
                "Unable to finish importing the model."
            }
            return outFile
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        }
    }

    fun deleteLocalModel(context: Context, modelId: String) {
        val modelsDir = File(context.filesDir, "models")
        File(modelsDir, "$modelId.gguf").delete()
        File(modelsDir, "$modelId.gguf.partial").delete()
    }

    fun clearCache(context: Context): Boolean {
        val modelsDir = File(context.filesDir, "models")
        return !modelsDir.exists() || modelsDir.deleteRecursively()
    }
}
