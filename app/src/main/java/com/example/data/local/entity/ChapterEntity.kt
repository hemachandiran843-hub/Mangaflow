package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["seriesId", "chapterUrl"], unique = true),
        Index(value = ["seriesId", "chapterIdentifier"], unique = true),
        Index(value = ["seriesId", "isRead"])
    ]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val seriesId: Long,
    val chapterNumber: Float? = null, // Nullable to support non-numeric chapters (prologue, side stories, specials)
    val chapterIdentifier: String = "1",
    val chapterTitle: String = "",
    val chapterUrl: String,
    val publicationTimestamp: Long? = null,
    val firstDetectedAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val readProgressPercent: Float = 0f,
    val lastReadAt: Long = 0L,
    val isDownloaded: Boolean = false,
    val localPath: String? = null
) {
    // Convenience property for backwards compatibility with any Phase 1 references
    val manhwaId: Long get() = seriesId
}
