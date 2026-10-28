package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AppSettingDao
import com.example.data.local.dao.BookmarkDao
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.ContentSourceDao
import com.example.data.local.dao.HistoryDao
import com.example.data.local.dao.NotificationDao
import com.example.data.local.dao.ReadingProgressDao
import com.example.data.local.dao.ReadingSessionDao
import com.example.data.local.dao.SeriesDao
import com.example.data.local.entity.AppSettingEntity
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.ContentSourceEntity
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.ReadingProgressEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.SeriesEntity

@Database(
    entities = [
        SeriesEntity::class,
        ChapterEntity::class,
        ReadingProgressEntity::class,
        ReadingSessionEntity::class,
        NotificationEntity::class,
        BookmarkEntity::class,
        AppSettingEntity::class,
        ContentSourceEntity::class,
        HistoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class MangaFlowDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
    abstract fun chapterDao(): ChapterDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun notificationDao(): NotificationDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun contentSourceDao(): ContentSourceDao
    abstract fun historyDao(): HistoryDao

    // Backwards-compatible accessor for Phase 1 code
    fun manhwaDao(): SeriesDao = seriesDao()

    companion object {
        @Volatile
        private var INSTANCE: MangaFlowDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create normalized Series table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `series` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `alternateTitles` TEXT,
                        `coverUrl` TEXT,
                        `sourceId` TEXT NOT NULL,
                        `sourceName` TEXT NOT NULL,
                        `sourceUrl` TEXT NOT NULL,
                        `readingStatus` TEXT NOT NULL,
                        `latestChapterIdentifier` TEXT NOT NULL,
                        `lastReadChapterIdentifier` TEXT NOT NULL,
                        `currentChapterNum` REAL NOT NULL,
                        `totalChapters` INTEGER NOT NULL,
                        `rating` REAL,
                        `description` TEXT,
                        `notes` TEXT NOT NULL,
                        `dateAdded` INTEGER NOT NULL,
                        `lastReadTimestamp` INTEGER NOT NULL,
                        `lastUpdated` INTEGER NOT NULL,
                        `hasUnreadNewChapter` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_sourceUrl` ON `series` (`sourceUrl`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_readingStatus` ON `series` (`readingStatus`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_lastUpdated` ON `series` (`lastUpdated`)")

                // Migrate existing manhwa rows if table exists
                try {
                    db.execSQL("""
                        INSERT INTO `series` (
                            `id`, `title`, `coverUrl`, `sourceId`, `sourceName`, `sourceUrl`, 
                            `readingStatus`, `latestChapterIdentifier`, `lastReadChapterIdentifier`, 
                            `currentChapterNum`, `totalChapters`, `rating`, `notes`, `dateAdded`, 
                            `lastReadTimestamp`, `lastUpdated`, `hasUnreadNewChapter`
                        )
                        SELECT 
                            `id`, `title`, `coverUrl`, 'web', `sourceName`, `sourceUrl`, 
                            `readingStatus`, CAST(`lastDetectedNewChapter` AS TEXT), CAST(`currentChapterNum` AS TEXT), 
                            `currentChapterNum`, `totalChapters`, `rating`, `notes`, `createdAt`, 
                            `lastReadTimestamp`, `lastReadTimestamp`, `hasUnreadNewChapter`
                        FROM `manhwa`
                    """.trimIndent())
                } catch (_: Exception) {
                    // Fresh db or no previous data
                }

                // 2. Recreate chapters with seriesId & non-numeric chapter support
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chapters_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `seriesId` INTEGER NOT NULL,
                        `chapterNumber` REAL,
                        `chapterIdentifier` TEXT NOT NULL,
                        `chapterTitle` TEXT NOT NULL,
                        `chapterUrl` TEXT NOT NULL,
                        `publicationTimestamp` INTEGER,
                        `firstDetectedAt` INTEGER NOT NULL,
                        `isRead` INTEGER NOT NULL,
                        `readProgressPercent` REAL NOT NULL,
                        `lastReadAt` INTEGER NOT NULL,
                        `isDownloaded` INTEGER NOT NULL,
                        `localPath` TEXT,
                        FOREIGN KEY(`seriesId`) REFERENCES `series`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                try {
                    db.execSQL("""
                        INSERT INTO `chapters_new` (
                            `id`, `seriesId`, `chapterNumber`, `chapterIdentifier`, 
                            `chapterTitle`, `chapterUrl`, `publicationTimestamp`, 
                            `firstDetectedAt`, `isRead`, `readProgressPercent`, 
                            `lastReadAt`, `isDownloaded`, `localPath`
                        )
                        SELECT 
                            `id`, `manhwaId`, `chapterNumber`, CAST(`chapterNumber` AS TEXT), 
                            `chapterTitle`, `chapterUrl`, NULL, 
                            `lastReadAt`, `isRead`, `readProgressPercent`, 
                            `lastReadAt`, `isDownloaded`, `localPath`
                        FROM `chapters`
                    """.trimIndent())
                    db.execSQL("DROP TABLE `chapters`")
                } catch (_: Exception) {
                    // Fresh or unpopulated
                }
                db.execSQL("ALTER TABLE `chapters_new` RENAME TO `chapters`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chapters_seriesId_chapterUrl` ON `chapters` (`seriesId`, `chapterUrl`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chapters_seriesId_chapterIdentifier` ON `chapters` (`seriesId`, `chapterIdentifier`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_seriesId_isRead` ON `chapters` (`seriesId`, `isRead`)")

                // 3. Create Reading Progress table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reading_progress` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `seriesId` INTEGER NOT NULL,
                        `chapterId` INTEGER NOT NULL,
                        `pageIndex` INTEGER NOT NULL,
                        `totalPages` INTEGER,
                        `progressPercent` REAL NOT NULL,
                        `lastReadAt` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        FOREIGN KEY(`seriesId`) REFERENCES `series`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reading_progress_seriesId_chapterId` ON `reading_progress` (`seriesId`, `chapterId`)")

                // 4. Create Reading Sessions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reading_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `seriesId` INTEGER NOT NULL,
                        `chapterId` INTEGER,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `chaptersReadCount` INTEGER NOT NULL,
                        `dateEpochDay` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_seriesId` ON `reading_sessions` (`seriesId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_dateEpochDay` ON `reading_sessions` (`dateEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_startTime` ON `reading_sessions` (`startTime`)")

                // 5. Create Notifications table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notifications` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `seriesId` INTEGER,
                        `chapterId` INTEGER,
                        `seriesTitle` TEXT NOT NULL,
                        `chapterTitle` TEXT NOT NULL,
                        `chapterIdentifier` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `sourceUrl` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `isRead` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_seriesId` ON `notifications` (`seriesId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_isRead` ON `notifications` (`isRead`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_timestamp` ON `notifications` (`timestamp`)")

                // 6. Create App Settings table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `app_settings` (
                        `key` TEXT NOT NULL PRIMARY KEY,
                        `value` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())

                // 7. Create Content Sources table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `content_sources` (
                        `sourceId` TEXT NOT NULL PRIMARY KEY,
                        `displayName` TEXT NOT NULL,
                        `baseUrl` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `requiresCustomUserAgent` INTEGER NOT NULL,
                        `dateAdded` INTEGER NOT NULL
                    )
                """.trimIndent())

                // 8. Update bookmarks table columns if not present
                try {
                    db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `seriesId` INTEGER")
                    db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `chapterId` INTEGER")
                    db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `pageIndex` INTEGER")
                } catch (_: Exception) {
                    // Columns might already be present
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_seriesId` ON `bookmarks` (`seriesId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_url` ON `bookmarks` (`url`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `series` ADD COLUMN `isMonitoringEnabled` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `series` ADD COLUMN `notificationsEnabled` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `series` ADD COLUMN `lastCheckTimestamp` INTEGER")
                db.execSQL("ALTER TABLE `series` ADD COLUMN `lastCheckStatus` TEXT")
                db.execSQL("ALTER TABLE `series` ADD COLUMN `lastCheckError` TEXT")
            }
        }

        /** Allows distinct manually tracked chapters to share a listing-page URL; preserves rows. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_chapters_seriesId_chapterUrl`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_seriesId_chapterUrl` ON `chapters` (`seriesId`, `chapterUrl`)")
            }
        }

        fun getDatabase(context: Context): MangaFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MangaFlowDatabase::class.java,
                    "mangaflow_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
