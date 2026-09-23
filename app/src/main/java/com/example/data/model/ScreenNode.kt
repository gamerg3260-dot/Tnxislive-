package com.example.data.model

import android.graphics.Rect

/**
 * Represents a parsed node from the Android Accessibility Tree.
 */
data class ScreenNode(
    val id: String = "",
    val viewIdResourceName: String = "",
    val className: String = "",
    val packageName: String = "",
    val text: String = "",
    val contentDescription: String = "",
    val bounds: Rect = Rect(),
    val centerX: Int = 0,
    val centerY: Int = 0,
    val isClickable: Boolean = false,
    val isScrollable: Boolean = false,
    val isEditable: Boolean = false,
    val isVisibleToUser: Boolean = true,
    val depth: Int = 0
) {
    /**
     * Ultra compact single-line label for fast token generation.
     */
    fun toCompactSummary(index: Int): String {
        val label = when {
            text.isNotBlank() && contentDescription.isNotBlank() && text != contentDescription -> "\"$text\" ($contentDescription)"
            text.isNotBlank() -> "\"$text\""
            contentDescription.isNotBlank() -> "($contentDescription)"
            viewIdResourceName.isNotBlank() -> viewIdResourceName.substringAfterLast("/")
            else -> className.substringAfterLast(".")
        }
        val type = className.substringAfterLast(".")
        val clickStr = if (isClickable) " [Click]" else ""
        val editStr = if (isEditable) " [Edit]" else ""
        return "[$index] $type: $label @ ($centerX,$centerY)$clickStr$editStr"
    }

    /**
     * Compact label for AI prompt summarization.
     */
    fun toPromptSummary(index: Int): String {
        return toCompactSummary(index)
    }
}
