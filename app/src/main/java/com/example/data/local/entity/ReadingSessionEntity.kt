package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_sessions",
    indices = [
        Index(value = ["seriesId"]),
        Index(value = ["dateEpochDay"]),
        Index(value = ["startTime"])
    ]
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val seriesId: Long,
    val chapterId: Long? = null,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val chaptersReadCount: Int = 0,
    val dateEpochDay: Long = java.time.LocalDate.now().toEpochDay()
)
