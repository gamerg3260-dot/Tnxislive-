package com.example.accessibility

import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.model.ScreenNode
import com.example.data.model.ScreenSnapshot

object ScreenTreeParser {

    private const val MAX_DEPTH = 12
    private const val MAX_NODES = 120

    fun parseRootNode(
        root: AccessibilityNodeInfo?,
        displayMetrics: DisplayMetrics? = null
    ): ScreenSnapshot {
        if (root == null) {
            return ScreenSnapshot()
        }

        val screenWidth = displayMetrics?.widthPixels ?: 1080
        val screenHeight = displayMetrics?.heightPixels ?: 2400

        val elements = mutableListOf<ScreenNode>()
        val packageName = root.packageName?.toString() ?: ""

        traverseNode(root, depth = 0, elements = elements)

        return ScreenSnapshot(
            packageName = packageName,
            elements = elements,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo?,
        depth: Int,
        elements: MutableList<ScreenNode>
    ) {
        if (node == null || depth > MAX_DEPTH || elements.size >= MAX_NODES) return

        val rect = Rect()
        node.getBoundsInScreen(rect)

        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val resId = node.viewIdResourceName?.toString() ?: ""
        val className = node.className?.toString() ?: ""
        val isClickable = node.isClickable
        val isScrollable = node.isScrollable
        val isEditable = node.isEditable
        val isVisible = node.isVisibleToUser

        val centerX = rect.centerX()
        val centerY = rect.centerY()

        // Include meaningful interactive nodes or readable text
        val isMeaningful = text.isNotBlank() || contentDesc.isNotBlank() || isClickable || isEditable || isScrollable

        if (isMeaningful && rect.width() > 0 && rect.height() > 0) {
            elements.add(
                ScreenNode(
                    id = "node_${elements.size}",
                    viewIdResourceName = resId,
                    className = className,
                    packageName = node.packageName?.toString() ?: "",
                    text = text,
                    contentDescription = contentDesc,
                    bounds = rect,
                    centerX = centerX,
                    centerY = centerY,
                    isClickable = isClickable,
                    isScrollable = isScrollable,
                    isEditable = isEditable,
                    isVisibleToUser = isVisible,
                    depth = depth
                )
            )
        }

        for (i in 0 until node.childCount) {
            if (elements.size >= MAX_NODES) break
            val child = try {
                node.getChild(i)
            } catch (e: Exception) {
                null
            }
            if (child != null) {
                traverseNode(child, depth + 1, elements)
                try {
                    child.recycle()
                } catch (ignored: Exception) {}
            }
        }
    }
}
