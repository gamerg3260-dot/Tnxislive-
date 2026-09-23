package com.example.router

sealed class LocalExecutionResult {
    data class Handled(
        val success: Boolean,
        val actionType: String,
        val messageHindi: String,
        val voiceResponseHindi: String
    ) : LocalExecutionResult()

    object NotHandled : LocalExecutionResult()
}
