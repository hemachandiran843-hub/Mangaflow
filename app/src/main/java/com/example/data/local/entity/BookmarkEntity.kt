package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["url"], unique = false),
        Index(value = ["seriesId"])
    ]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val seriesId: Long? = null,
    val chapterId: Long? = null,
    val title: String,
    val url: String,
    val pageIndex: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)
