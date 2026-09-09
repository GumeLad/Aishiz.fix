package com.example.aishiz

object NativeLlamaBridge {

    interface TokenCallback {
        fun onToken(tokenChunk: String)
        fun onComplete()
        fun onError(message: String)
    }

    init {
        System.loadLibrary("aishiz_native")
    }

    external fun startGeneration(
        modelPath: String,
        prompt: String,
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        repeatPenalty: Float,
        maxTokens: Int,
        contextLength: Int,
        batchSize: Int,
        threads: Int,
        seed: Int,
        callback: TokenCallback
    ): Long

    external fun stopGeneration(requestId: Long)
}
