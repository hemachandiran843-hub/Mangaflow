package com.example.engine.tracker

import android.os.SystemClock
import com.example.data.local.entity.ReadingSessionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Captures only elapsed time while an eligible normal-browser chapter is visible and the app is
 * resumed. Elapsed time is measured monotonically; a page load alone never marks a chapter read.
 */
class ReadingSessionTracker(
    private val scope: CoroutineScope,
    private val persist: suspend (ReadingSessionEntity) -> Unit,
    private val monotonicClock: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val epochDayFor: (Long) -> Long = { timestamp ->
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private data class ActiveSession(
        val seriesId: Long,
        val chapterId: Long?,
        val chapterIdentity: String?,
        val startedAtElapsed: Long,
        val startedAtWall: Long
    )

    private val lock = Any()
    private var active: ActiveSession? = null

    val isActive: Boolean get() = synchronized(lock) { active != null }

    /** Starts (or switches) a session. Repeated recompositions for the same chapter do not reset it. */
    fun start(
        seriesId: Long,
        chapterId: Long?,
        eligible: Boolean,
        privateSession: Boolean,
        chapterIdentity: String? = chapterId?.toString()
    ) {
        if (!eligible || privateSession || seriesId <= 0L) {
            stop()
            return
        }
        val elapsedNow = monotonicClock()
        val wallNow = wallClock()
        val completed = synchronized(lock) {
            val previous = active
            if (previous?.seriesId == seriesId && previous.chapterId == chapterId && previous.chapterIdentity == chapterIdentity) {
                return@synchronized null
            }
            active = ActiveSession(seriesId, chapterId, chapterIdentity, elapsedNow, wallNow)
            previous?.let { toEntity(it, elapsedNow) }
        }
        completed?.let(::persistSafely)
    }

    /** Flushes the current eligible interval; callers use this on page change, pause and disposal. */
    fun stop() {
        val elapsedNow = monotonicClock()
        val completed = synchronized(lock) {
            val previous = active
            active = null
            previous?.let { toEntity(it, elapsedNow) }
        }
        completed?.let(::persistSafely)
    }

    private fun toEntity(session: ActiveSession, elapsedNow: Long): ReadingSessionEntity? {
        val durationMillis = (elapsedNow - session.startedAtElapsed).coerceAtLeast(0L)
        val durationSeconds = (durationMillis / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (durationSeconds <= 0) return null
        val endWall = session.startedAtWall + durationMillis
        return ReadingSessionEntity(
            seriesId = session.seriesId,
            chapterId = session.chapterId,
            startTime = session.startedAtWall,
            endTime = endWall,
            durationSeconds = durationSeconds,
            chaptersReadCount = 0,
            dateEpochDay = epochDayFor(session.startedAtWall)
        )
    }

    private fun persistSafely(session: ReadingSessionEntity) {
        scope.launch(ioDispatcher) {
            try {
                persist(session)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Statistics persistence is best-effort and must never interrupt browser use.
            }
        }
    }
}
