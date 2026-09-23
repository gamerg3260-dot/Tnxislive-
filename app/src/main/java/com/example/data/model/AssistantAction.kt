package com.example.data.model

enum class ActionType {
    TAP,
    DOUBLE_TAP,
    LONG_PRESS,
    SWIPE,
    SCROLL_DOWN,
    SCROLL_UP,
    TYPE,
    OPEN_APP,
    SKIP_AD,
    BACK,
    HOME,
    SPEAK_ONLY,
    NONE
}

data class AssistantAction(
    val actionType: ActionType = ActionType.NONE,
    val targetX: Int = 0,
    val targetY: Int = 0,
    val endX: Int = 0,
    val endY: Int = 0,
    val durationMs: Long = 100,
    val textToType: String = "",
    val targetAppName: String = "",
    val voiceResponseHindi: String = "",
    val reasonHindi: String = "",
    val targetElementDesc: String = "",
    val rawExplanation: String = ""
)
