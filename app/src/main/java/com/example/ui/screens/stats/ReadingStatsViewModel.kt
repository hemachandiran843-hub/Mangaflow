package com.example.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.MangaFlowApp
import com.example.data.tracker.ReadingStatistics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyStatItem(
    val dayLabel: String,
    val minutes: Int,
    val chapters: Int,
    val isToday: Boolean
)

data class StatsSummary(
    val statistics: ReadingStatistics = ReadingStatistics(),
    val streakDays: Int = 0,
    val dailyList: List<DailyStatItem> = emptyList()
)

class ReadingStatsViewModel : ViewModel() {
    private val app = MangaFlowApp.instance
    private val trackerService = app.trackerService
    private val statsRepo = app.readingStatsRepository

    val statsSummary: StateFlow<StatsSummary> = combine(
        trackerService.getReadingStatistics(),
        statsRepo.allStats
    ) { statistics, sessions ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val durationSecondsByDay = sessions
            .filter { it.durationSeconds > 0 }
            .groupBy { it.dateEpochDay }
            .mapValues { (_, rows) -> rows.sumOf { it.durationSeconds } }
        val chapterCountsByDay = mutableMapOf<Long, Int>()
        val days = (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val chapters = trackerService.getReadChapterCountBetween(start, end).coerceAtLeast(0)
            val epochDay = date.toEpochDay()
            chapterCountsByDay[epochDay] = chapters
            val seconds = durationSecondsByDay[epochDay] ?: 0
            DailyStatItem(
                dayLabel = DAY_NAMES[date.dayOfWeek.value - 1],
                minutes = (seconds / 60).coerceAtLeast(0),
                chapters = chapters,
                isToday = offset == 0
            )
        }

        val activeDays = durationSecondsByDay.filterValues { it > 0 }.keys.toMutableSet()
        trackerService.getReadChapterTimestamps().forEach { timestamp ->
            activeDays += Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().toEpochDay()
        }
        var checkDay = today.toEpochDay()
        if (checkDay !in activeDays) checkDay--
        var streak = 0
        while (checkDay in activeDays) {
            streak++
            checkDay--
        }

        StatsSummary(
            statistics = statistics,
            streakDays = streak,
            dailyList = days
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StatsSummary()
    )

    private companion object {
        val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }
}
