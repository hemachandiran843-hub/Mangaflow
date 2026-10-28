package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manhwa")
data class ManhwaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val coverUrl: String? = null,
    val sourceName: String = "Web",
    val sourceUrl: String,
    val readingStatus: String = "READING", // READING, COMPLETED, ON_HOLD, PLAN_TO_READ
    val currentChapterNum: Float = 1.0f,
    val totalChapters: Int = 1,
    val rating: Float = 0f,
    val notes: String = "",
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastDetectedNewChapter: Float = 1.0f,
    val hasUnreadNewChapter: Boolean = false
)
