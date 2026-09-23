package com.example.voice

/**
 * Buffers streamed delta tokens from Gemini and yields complete sentences / clauses
 * for immediate, stutter-free Text-To-Speech delivery.
 */
class SentenceStreamBuffer(
    private val onSentenceReady: (sentence: String, isFirst: Boolean) -> Unit
) {
    private val buffer = StringBuilder()
    private var isFirstSentence = true

    // Sentence punctuation delimiters in Hindi & English
    private val delimiters = charArrayOf('।', '.', '!', '?', '\n', '|', ';')

    fun appendChunk(deltaText: String) {
        buffer.append(deltaText)

        while (true) {
            val text = buffer.toString()
            var delimiterIndex = -1

            for (i in text.indices) {
                if (delimiters.contains(text[i])) {
                    delimiterIndex = i
                    break
                }
            }

            // Also split on comma if buffer is getting long (> 45 chars) for rapid early speech
            if (delimiterIndex == -1 && isFirstSentence && text.length >= 35) {
                val commaIndex = text.indexOf(',')
                if (commaIndex in 10..40) {
                    delimiterIndex = commaIndex
                }
            }

            if (delimiterIndex != -1) {
                val sentence = text.substring(0, delimiterIndex + 1).trim()
                buffer.delete(0, delimiterIndex + 1)
                if (sentence.isNotBlank()) {
                    onSentenceReady(sentence, isFirstSentence)
                    isFirstSentence = false
                }
            } else {
                break
            }
        }
    }

    fun finish() {
        val remaining = buffer.toString().trim()
        if (remaining.isNotBlank()) {
            onSentenceReady(remaining, isFirstSentence)
            isFirstSentence = false
        }
        buffer.clear()
    }
}
