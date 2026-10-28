package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["sourceUrl"], unique = false),
        Index(value = ["readingStatus"]),
        Index(value = ["lastUpdated"])
    ]
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val alternateTitles: String? = null,
    val coverUrl: String? = null,
    val sourceId: String = "web",
    val sourceName: String = "Web Source",
    val sourceUrl: String,
    val readingStatus: String = "READING", // READING, PLAN_TO_READ, COMPLETED, ON_HOLD, DROPPED
    val latestChapterIdentifier: String = "1",
    val lastReadChapterIdentifier: String = "0",
    val currentChapterNum: Float = 1.0f,
    val totalChapters: Int = 1,
    val rating: Float? = null,
    val description: String? = null,
    val notes: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val hasUnreadNewChapter: Boolean = false,
    val isMonitoringEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val lastCheckTimestamp: Long? = null,
    val lastCheckStatus: String? = null, // SUCCESS, NO_NEW_CHAPTERS, NETWORK_ERROR, SOURCE_UNAVAILABLE, RATE_LIMITED, UNSUPPORTED_SOURCE, PARSING_ERROR
    val lastCheckError: String? = null
)
