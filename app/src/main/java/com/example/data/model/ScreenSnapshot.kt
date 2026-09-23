package com.example.data.model

/**
 * Snapshot of the current screen captured via Accessibility Service.
 * Optimized for low-token overhead and fast AI perception.
 */
data class ScreenSnapshot(
    val packageName: String = "",
    val windowTitle: String = "",
    val elements: List<ScreenNode> = emptyList(),
    val screenWidth: Int = 1080,
    val screenHeight: Int = 2400,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Converts the snapshot into an ultra-compact structured prompt for Gemini Flash.
     * Prunes off-screen, empty, and non-actionable layout nodes.
     */
    fun toPromptString(): String {
        val appName = when {
            packageName.contains("youtube") -> "YouTube"
            packageName.contains("whatsapp") -> "WhatsApp"
            packageName.contains("instagram") -> "Instagram"
            packageName.contains("chrome") -> "Chrome Browser"
            packageName.contains("spotify") -> "Spotify"
            packageName.contains("settings") -> "Settings"
            packageName.contains("launcher") || packageName.contains("nexuslauncher") -> "Home Screen"
            packageName.isBlank() -> "Current App"
            else -> packageName.substringAfterLast(".")
        }

        // Fast filter: visible inside screen boundaries + has interactive or textual signal
        val relevantNodes = elements.filter { node ->
            node.isVisibleToUser &&
            node.bounds.right > 0 && node.bounds.bottom > 0 &&
            node.bounds.left < screenWidth && node.bounds.top < screenHeight &&
            (node.isClickable || node.isEditable || node.text.isNotBlank() || node.contentDescription.isNotBlank() || node.isScrollable)
        }

        val sb = StringBuilder()
        sb.appendLine("App: $appName ($packageName) | Res: ${screenWidth}x$screenHeight")
        sb.appendLine("Key Elements (${relevantNodes.size}):")

        // Keep at most 28 most relevant elements for super-fast token generation
        relevantNodes.take(28).forEachIndexed { idx, node ->
            sb.appendLine(node.toCompactSummary(idx))
        }

        return sb.toString()
    }
}

