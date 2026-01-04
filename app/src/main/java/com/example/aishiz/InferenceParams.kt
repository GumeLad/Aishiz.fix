package com.example.aishiz

data class InferenceParams(
    val temperature: Float = 0.70f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.10f,
    val maxTokens: Int = 256,
    val contextLength: Int = 2048,
    val seed: Int = -1
)
