package com.example.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.MangaFlowApp
import com.example.data.local.entity.SeriesEntity
import com.example.data.tracker.LibraryImportPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibrarySortOrder(val label: String) {
    RECENTLY_READ("Recently Read"),
    TITLE_AZ("Title (A-Z)"),
    LATEST_UPDATE("Latest Update"),
    RATING("Highest Rating"),
    PROGRESS("Chapters Read")
}

class LibraryViewModel : ViewModel() {

    private val app = MangaFlowApp.instance
    private val trackerService = app.trackerService

    private val _selectedStatus = MutableStateFlow("ALL")
    val selectedStatus: StateFlow<String> = _selectedStatus.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(LibrarySortOrder.RECENTLY_READ)
    val sortOrder: StateFlow<LibrarySortOrder> = _sortOrder.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val seriesList: StateFlow<List<SeriesEntity>> = combine(
        trackerService.allSeries,
        _selectedStatus,
        _searchQuery,
        _sortOrder
    ) { list, status, query, sort ->
        val filtered = list.filter { item ->
            val matchesStatus = if (status == "ALL") true else item.readingStatus == status
            val matchesQuery = if (query.isBlank()) true else {
                item.title.contains(query, ignoreCase = true) ||
                        (item.alternateTitles?.contains(query, ignoreCase = true) ?: false)
            }
            matchesStatus && matchesQuery
        }

        when (sort) {
            LibrarySortOrder.RECENTLY_READ -> filtered.sortedByDescending { it.lastReadTimestamp }
            LibrarySortOrder.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
            LibrarySortOrder.LATEST_UPDATE -> filtered.sortedByDescending { it.lastUpdated }
            LibrarySortOrder.RATING -> filtered.sortedByDescending { it.rating ?: 0f }
            LibrarySortOrder.PROGRESS -> filtered.sortedByDescending { it.currentChapterNum }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setStatusFilter(status: String) {
        _selectedStatus.value = status
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: LibrarySortOrder) {
        _sortOrder.value = order
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun addSeriesManually(
        title: String,
        alternateTitles: String?,
        sourceUrl: String,
        coverUrl: String?,
        readingStatus: String,
        rating: Float?,
        description: String?,
        initialChapterCount: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                trackerService.addSeriesManually(
                    title = title,
                    alternateTitles = alternateTitles,
                    sourceUrl = sourceUrl,
                    coverUrl = coverUrl,
                    readingStatus = readingStatus,
                    rating = rating,
                    description = description,
                    initialChapterCount = initialChapterCount
                )
                _actionMessage.value = "Added \"$title\" to library"
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to add series")
            }
        }
    }

    fun editSeriesMetadata(
        seriesId: Long,
        title: String,
        alternateTitles: String?,
        coverUrl: String?,
        readingStatus: String,
        rating: Float?,
        description: String?,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            trackerService.editSeriesMetadata(
                seriesId = seriesId,
                title = title,
                alternateTitles = alternateTitles,
                coverUrl = coverUrl,
                readingStatus = readingStatus,
                rating = rating,
                description = description,
                notes = notes
            )
            _actionMessage.value = "Updated series metadata"
            onSuccess()
        }
    }

    fun updateStatus(id: Long, newStatus: String) {
        viewModelScope.launch {
            trackerService.changeReadingStatus(id, newStatus)
            _actionMessage.value = "Status updated to $newStatus"
        }
    }

    fun markSeriesCompleted(id: Long, markChaptersRead: Boolean = true) {
        viewModelScope.launch {
            trackerService.markSeriesCompleted(id, markChaptersRead)
            _actionMessage.value = "Marked series as Completed"
        }
    }

    fun moveSeriesToDropped(id: Long) {
        viewModelScope.launch {
            trackerService.moveSeriesToDropped(id)
            _actionMessage.value = "Moved series to Dropped"
        }
    }

    fun deleteSeries(id: Long, title: String) {
        viewModelScope.launch {
            trackerService.removeSeries(id)
            _actionMessage.value = "Removed \"$title\" from library"
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            trackerService.clearAllData()
            _actionMessage.value = "All library data cleared"
        }
    }

    fun exportLibrary(onExported: (String) -> Unit) {
        viewModelScope.launch {
            val json = trackerService.exportLibraryJson()
            onExported(json)
        }
    }

    fun previewLibraryImport(json: String, onPreview: (Result<LibraryImportPreview>) -> Unit) {
        viewModelScope.launch {
            onPreview(trackerService.previewLibraryJson(json))
        }
    }

    fun applyLibraryImport(preview: LibraryImportPreview, onComplete: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = trackerService.applyLibraryImport(preview)
            if (result.isSuccess) _actionMessage.value = "Imported ${result.getOrNull()} new series; existing entries were left unchanged"
            onComplete(result)
        }
    }

    fun importLibrary(json: String, onComplete: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = trackerService.importLibraryJson(json)
            if (result.isSuccess) _actionMessage.value = "Successfully imported ${result.getOrNull()} titles"
            onComplete(result)
        }
    }
}
