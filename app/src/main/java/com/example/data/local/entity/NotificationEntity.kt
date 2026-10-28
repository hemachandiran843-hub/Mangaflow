package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["seriesId"]),
        Index(value = ["isRead"]),
        Index(value = ["timestamp"])
    ]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val seriesId: Long? = null,
    val chapterId: Long? = null,
    val seriesTitle: String,
    val chapterTitle: String,
    val chapterIdentifier: String = "",
    val message: String,
    val sourceUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
