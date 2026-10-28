package com.example.data.repository

import com.example.data.local.dao.BookmarkDao
import com.example.data.local.dao.HistoryDao
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

class BrowserRepository(
    private val historyDao: HistoryDao,
    private val bookmarkDao: BookmarkDao
) {
    val history: Flow<List<HistoryEntity>> = historyDao.getAllHistory()
    val bookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.getAllBookmarks()

    suspend fun recordHistory(url: String, title: String, isPrivate: Boolean) {
        if (isPrivate || url.startsWith("about:") || url.isBlank()) return
        historyDao.insert(
            HistoryEntity(
                url = url,
                title = title.ifBlank { url },
                visitedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
    }

    suspend fun deleteHistoryItem(id: Long) {
        historyDao.deleteById(id)
    }

    fun isBookmarked(url: String): Flow<Boolean> = bookmarkDao.isBookmarked(url)

    suspend fun toggleBookmark(url: String, title: String, currentlyBookmarked: Boolean) {
        if (currentlyBookmarked) {
            bookmarkDao.deleteByUrl(url)
        } else {
            bookmarkDao.insert(
                BookmarkEntity(
                    title = title.ifBlank { url },
                    url = url
                )
            )
        }
    }

    suspend fun deleteBookmark(id: Long) {
        bookmarkDao.deleteById(id)
    }
}
