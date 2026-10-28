package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_stats")
data class ReadingStatsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val manhwaId: Long,
    val dateEpochDay: Long, // LocalDate.now().toEpochDay()
    val durationMinutes: Int = 0,
    val chaptersReadCount: Int = 0
)
