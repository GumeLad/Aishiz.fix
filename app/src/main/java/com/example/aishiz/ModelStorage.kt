package com.example.aishiz

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ModelStorage {

    fun ensureLocalModelFile(context: Context, model: ModelInfo): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val ext = guessExtension(model.name)
        val outFile = File(modelsDir, model.id + ext)

        if (outFile.exists() && outFile.length() > 0) return outFile

        val uri = Uri.parse(model.uri)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open model URI. Re-add the model." }
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }

    private fun guessExtension(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".gguf") -> ".gguf"
            lower.endsWith(".bin") -> ".bin"
            else -> ".model"
        }
    }
}
