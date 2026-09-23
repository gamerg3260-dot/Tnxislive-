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
     * Converts the snapshot into a structured visual-ordered prompt for Gemini Flash.
     * Elements are sorted strictly in top-to-bottom visual reading order, and labeled with
     * clear 1-based ordinal tags (1st/Pehla, 2nd/Doosra, 3rd/Teesra) to guarantee precise selection.
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
        val visibleNodes = elements.filter { node ->
            node.isVisibleToUser &&
            node.bounds.right > 0 && node.bounds.bottom > 0 &&
            node.bounds.left < screenWidth && node.bounds.top < screenHeight &&
            (node.isClickable || node.isEditable || node.text.isNotBlank() || node.contentDescription.isNotBlank() || node.isScrollable)
        }

        // Sort strictly by top-to-bottom, left-to-right visual screen coordinates
        val sortedNodes = visibleNodes.sortedWith(
            compareBy<ScreenNode> { it.bounds.top }.thenBy { it.bounds.left }
        )

        // Split into Top App Header/Search (top 15%), Main Content Items (middle 15% to 88%), and Bottom Navigation (bottom > 88%)
        val headerThreshold = (screenHeight * 0.14).toInt()
        val bottomThreshold = (screenHeight * 0.88).toInt()

        val headerNodes = sortedNodes.filter { it.centerY <= headerThreshold }
        val contentNodes = sortedNodes.filter { it.centerY in (headerThreshold + 1)..bottomThreshold }
        val bottomNodes = sortedNodes.filter { it.centerY > bottomThreshold }

        val sb = StringBuilder()
        sb.appendLine("App: $appName ($packageName) | Resolution: ${screenWidth}x$screenHeight")
        sb.appendLine()

        if (headerNodes.isNotEmpty()) {
            sb.appendLine("TOP HEADER / CONTROLS:")
            headerNodes.take(6).forEach { node ->
                val label = node.text.ifBlank { node.contentDescription }.ifBlank { node.viewIdResourceName.substringAfterLast("/") }
                sb.appendLine("  - Header Item: \"$label\" @ (X: ${node.centerX}, Y: ${node.centerY})")
            }
            sb.appendLine()
        }

        sb.appendLine("MAIN CONTENT LIST ITEMS (TOP-TO-BOTTOM VISUAL ORDER - USE THESE FOR 'PEHLA', 'DOOSRA', 'TEESRA'):")
        // Deduplicate overlapping parent/child clickables with near-identical coordinates
        val distinctContentNodes = mutableListOf<ScreenNode>()
        for (node in contentNodes) {
            val isDuplicate = distinctContentNodes.any { existing ->
                Math.abs(existing.centerY - node.centerY) < 40 &&
                (existing.text.contains(node.text, ignoreCase = true) || node.text.contains(existing.text, ignoreCase = true))
            }
            if (!isDuplicate) {
                distinctContentNodes.add(node)
            }
        }

        val ordinalNamesHindi = listOf(
            "1st / Pehla (पहला)",
            "2nd / Doosra (दूसरा)",
            "3rd / Teesra (तीसरा)",
            "4th / Chautha (चौथा)",
            "5th / Paanchva (पाँचवाँ)",
            "6th / Chhatha (छठा)",
            "7th / Saathva (सातवाँ)",
            "8th / Aathva (आठवाँ)",
            "9th / Nauva (नौवाँ)",
            "10th / Dasva (दसवाँ)"
        )

        distinctContentNodes.take(16).forEachIndexed { idx, node ->
            val ordinalTag = if (idx < ordinalNamesHindi.size) ordinalNamesHindi[idx] else "${idx + 1}th"
            val label = when {
                node.text.isNotBlank() && node.contentDescription.isNotBlank() && node.text != node.contentDescription -> "\"${node.text}\" (${node.contentDescription})"
                node.text.isNotBlank() -> "\"${node.text}\""
                node.contentDescription.isNotBlank() -> "(${node.contentDescription})"
                node.viewIdResourceName.isNotBlank() -> node.viewIdResourceName.substringAfterLast("/")
                else -> node.className.substringAfterLast(".")
            }
            val clickStr = if (node.isClickable) "[Clickable Item]" else ""
            sb.appendLine("  [$ordinalTag] $clickStr: $label @ (X: ${node.centerX}, Y: ${node.centerY})")
        }
        sb.appendLine()

        if (bottomNodes.isNotEmpty()) {
            sb.appendLine("BOTTOM NAVIGATION:")
            bottomNodes.take(5).forEach { node ->
                val label = node.text.ifBlank { node.contentDescription }.ifBlank { node.viewIdResourceName.substringAfterLast("/") }
                sb.appendLine("  - Nav Item: \"$label\" @ (X: ${node.centerX}, Y: ${node.centerY})")
            }
        }

        return sb.toString()
    }
}

