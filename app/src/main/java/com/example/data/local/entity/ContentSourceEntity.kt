package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "content_sources")
data class ContentSourceEntity(
    @PrimaryKey
    val sourceId: String, // e.g. "webtoons", "asurascans", "mangadex", "generic"
    val displayName: String,
    val baseUrl: String,
    val isEnabled: Boolean = true,
    val requiresCustomUserAgent: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis()
)
