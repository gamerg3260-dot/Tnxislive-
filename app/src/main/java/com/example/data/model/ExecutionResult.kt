package com.example.data.model

data class ExecutionResult(
    val success: Boolean,
    val message: String,
    val action: AssistantAction? = null,
    val timestamp: Long = System.currentTimeMillis()
)
