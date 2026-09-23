package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_memory")
data class UserMemoryEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val category: String = CATEGORY_PREFERENCE,
    val descriptionHindi: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val CATEGORY_PREFERENCE = "preference"
        const val CATEGORY_ACTIVITY_CONTEXT = "activity_context"
        const val CATEGORY_IDENTITY = "identity"
        const val CATEGORY_GENERAL = "general"
    }
}
