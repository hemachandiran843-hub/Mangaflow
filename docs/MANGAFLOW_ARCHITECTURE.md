# MangaFlow: Architecture & System Design Specification

## 1. Environment & Repository Inspection

### Build Environment
- **Platform**: Cloud-based Android Build Environment
- **Runtime & Toolchain**:
  - Android SDK: Target SDK 36, Min SDK 24, Compile SDK 36
  - Language: Kotlin 2.2.10
  - UI Toolkit: Jetpack Compose with Material 3 (BOM 2024.09.00)
  - Database: Room SQLite 2.7.0 with Kotlin Symbol Processing (KSP 2.3.5)
  - Asynchronous: Kotlin Coroutines 1.10.2 & Flow / StateFlow
  - Networking & Image: OkHttp 4.10.0, Retrofit 2.12.0, Coil Compose 2.7.0
  - Browser Core: Android `WebView` with custom `WebViewClient`, `WebChromeClient`, and `WebResourceResponse` interception
  - Local ML: Google ML Kit Text Recognition (on-device, zero-network OCR)

---

## 2. Proposed Directory Structure

```text
app/src/main/java/com/example/
├── MangaFlowApp.kt                     # Application class with DI / repository initialization
├── MainActivity.kt                     # Single Activity with Edge-to-Edge & Compose Navigation
├── data/
│   ├── local/
│   │   ├── MangaFlowDatabase.kt       # Room Database definition with migrations
│   │   ├── dao/
│   │   │   ├── ManhwaDao.kt           # Manhwa titles, metadata, reading status
│   │   │   ├── ChapterDao.kt          # Chapters, release timestamps, read states
│   │   │   ├── HistoryDao.kt          # Navigation & reading history (normal mode only)
│   │   │   ├── BookmarkDao.kt         # Bookmarks & saved page anchors
│   │   │   ├── DownloadDao.kt         # Offline chapter downloads & cache metadata
│   │   │   └── ReadingStatsDao.kt     # Reading time, chapters read, daily streaks
│   │   └── entity/
│   │       ├── ManhwaEntity.kt
│   │       ├── ChapterEntity.kt
│   │       ├── HistoryEntity.kt
│   │       ├── BookmarkEntity.kt
│   │       ├── DownloadEntity.kt
│   │       └── ReadingStatsEntity.kt
│   └── repository/
│       ├── BrowserRepository.kt       # Normal vs Private tabs, history, bookmarks
│       ├── ManhwaRepository.kt        # Manhwa library, status, and manual updates
│       ├── ReadingStatsRepository.kt  # Aggregates duration, read counts, habits
│       └── DownloadRepository.kt      # Disk persistence for offline chapters
├── domain/
│   ├── model/
│   │   ├── BrowserTab.kt              # Tab state, URL, title, favicon, private flag
│   │   ├── Manhwa.kt                  # Domain model for manhwa & tracking
│   │   ├── Chapter.kt                 # Domain model for chapter item
│   │   ├── ReadingSession.kt          # Active session duration and progress
│   │   ├── FilterRule.kt              # Ad-block rule matchers (hosts, patterns)
│   │   └── TranslationResult.kt       # OCR bounding boxes and translated text
│   └── usecase/
│       ├── TrackReadingProgressUseCase.kt
│       ├── DetectNewChaptersUseCase.kt
│       └── FilterAdRequestUseCase.kt
├── engine/
│   ├── browser/
│   │   ├── BrowserSessionManager.kt   # Multi-tab lifecycle, ephemeral incognito context
│   │   ├── MangaFlowWebViewClient.kt  # Intercepts requests, ad-blocking, URL inspection
│   │   ├── MangaFlowWebChromeClient.kt# Progress bar, favicons, custom view handling
│   │   └── ReaderModeExtractor.kt     # Injects readability script to strip clutter
│   ├── privacy/
│   │   ├── PrivacyManager.kt          # Cookie isolation, cache clearing, leak protection
│   │   └── EphemeralStorageContext.kt # In-memory session store for private tabs
│   ├── filter/
│   │   ├── ContentFilterEngine.kt     # EasyList pattern matching + host lookup
│   │   ├── BlocklistParser.kt         # Parses EasyList & ABP filter lists
│   │   └── CosmeticCssInjector.kt     # Injects stylesheet to collapse ad containers
│   ├── tracker/
│   │   ├── ManhwaTracker.kt           # URL heuristics, DOM detector, progress updater
│   │   └── adapters/
│   │       ├── SourceAdapter.kt       # Interface for website-specific metadata parsers
│   │       ├── GenericWebtoonAdapter.kt
│   │       └── AsuraScansAdapter.kt
│   ├── translation/
│   │   ├── TranslationProvider.kt     # Text translation abstraction
│   │   ├── MLKitOcrProcessor.kt       # On-device ML Kit image OCR text extractor
│   │   └── OfflineTranslator.kt       # On-device translation client
│   └── worker/
│       ├── ChapterMonitorWorker.kt    # WorkManager periodic task for new chapter checks
│       └── NotificationHelper.kt      # Notification channels, heads-up notifications
└── ui/
    ├── theme/
    │   ├── Color.kt                   # M3 Dynamic color palette with dark manga styling
    │   ├── Theme.kt                   # DynamicLight/Dark & MangaFlow dark theme
    │   └── Type.kt                    # Custom typography hierarchy
    ├── components/
    │   ├── BrowserAddressBar.kt       # Modern omnibox, security badges, reader mode pill
    │   ├── BrowserBottomBar.kt        # Tab switcher, navigation, bookmarks, library shortcut
    │   ├── TabSwitcherView.kt         # Card grid for normal and private tabs
    │   ├── AdBlockShieldBadge.kt      # Blocked ads/tracker counter popup
    │   └── OcrOverlayView.kt          # Floating translation overlay on top of reader
    ├── screens/
    │   ├── browser/
    │   │   ├── BrowserScreen.kt       # Interactive WebView host with gesture navigation
    │   │   └── BrowserViewModel.kt    # Tab state, history, ad-block counters
    │   ├── library/
    │   │   ├── LibraryScreen.kt       # Reading list: Reading, Completed, On Hold, Plan to Read
    │   │   └── LibraryViewModel.kt
    │   ├── manhwa_detail/
    │   │   ├── ManhwaDetailScreen.kt  # Chapter list, status selector, statistics
    │   │   └── ManhwaDetailViewModel.kt
    │   ├── stats/
    │   │   ├── ReadingStatsScreen.kt  # Reading streaks, time spent, chapter counts
    │   │   └── ReadingStatsViewModel.kt
    │   └── settings/
    │       ├── SettingsScreen.kt      # Content filtering, privacy controls, storage clear
    │       └── SettingsViewModel.kt
    └── navigation/
        └── AppNavigation.kt           # Compose Navigation graph & BottomNavBar
```

---

## 3. Architecture & Module Responsibilities

```text
┌────────────────────────────────────────────────────────────────────────┐
│                          Jetpack Compose UI                            │
│  [BrowserScreen]   [LibraryScreen]   [StatsScreen]   [SettingsScreen]  │
└───────────────┬───────────────────────────────┬────────────────────────┘
                │                               │
                ▼                               ▼
┌───────────────────────────────┐ ┌──────────────────────────────────────┐
│     BrowserViewModel          │ │    Library & Stats ViewModels        │
└───────────────┬───────────────┘ └─────────────┬────────────────────────┘
                │                               │
                ▼                               ▼
┌───────────────────────────────┐ ┌──────────────────────────────────────┐
│    BrowserSessionManager      │ │       ManhwaRepository               │
│ - Normal vs Private Sessions  │ │ - Library CRUD                       │
│ - Tab state lifecycle         │ │ - Progress sync                      │
└───────┬───────────────┬───────┘ └─────────────┬────────────────────────┘
        │               │                       │
        ▼               ▼                       ▼
┌───────────────┐ ┌─────────────┐       ┌────────────────────────────────┐
│ ContentFilter │ │   Privacy   │       │      MangaFlowDatabase         │
│ - EasyList    │ │   Manager   │       │ (Room SQLite: Manhwa, Chapter, │
│ - Host lookup │ │ - Ephemeral │       │  History, Bookmarks, Stats)    │
│ - Cosmetic CSS│ │   Cookies   │       └────────────────────────────────┘
└───────┬───────┘ └─────────────┘                       ▲
        │                                               │
        ▼                                               │
┌───────────────────────────────┐                       │
│      Android WebView          │                       │
│ - WebResourceResponse filter  │                       │
│ - Reader Mode script          │                       │
│ - Gesture / long-strip scroll │                       │
└───────────────┬───────────────┘                       │
                │                                       │
                ▼                                       │
┌───────────────────────────────┐       ┌───────────────┴────────────────┐
│      ManhwaTracker Engine     │──────►│    ChapterMonitorWorker        │
│ - URL / DOM Heuristic parser  │       │ - Periodic background check    │
│ - Source Adapters             │       │ - System notifications         │
└───────────────┬───────────────┘       └────────────────────────────────┘
                │
                ▼
┌───────────────────────────────┐
│     MLKitOcrProcessor         │
│ - Local image capture OCR     │
│ - On-device translation       │
└───────────────────────────────┘
```

---

## 4. Database Entities & Relationships

### Room SQLite Schema

```text
   ┌───────────────────────┐
   │        manhwa         │
   ├───────────────────────┤
   │ id (PK, AutoGen)      │◄──────────┐
   │ title                 │           │
   │ cover_url             │           │
   │ source_name           │           │
   │ source_url            │           │
   │ reading_status        │           │
   │ current_chapter_num   │           │
   │ total_chapters        │           │
   │ rating                │           │
   │ notes                 │           │
   │ last_read_timestamp   │           │
   │ created_at            │           │
   └───────────┬───────────┘           │
               │ 1                     │
               │                       │
               ▼ *                     │ 1
   ┌───────────────────────┐           │
   │        chapter        │           │
   ├───────────────────────┤           │
   │ id (PK, AutoGen)      │           │
   │ manhwa_id (FK)        │           │
   │ chapter_number        │           │
   │ chapter_title         │           │
   │ chapter_url           │           │
   │ is_read               │           │
   │ read_progress_percent │           │
   │ last_read_at          │           │
   │ is_downloaded         │           │
   │ local_path            │           │
   └───────────────────────┘           │
                                       │
                                       ▼ *
   ┌───────────────────────┐   ┌───────────────────────┐
   │    reading_history    │   │     reading_stats     │
   ├───────────────────────┤   ├───────────────────────┤
   │ id (PK, AutoGen)      │   │ id (PK, AutoGen)      │
   │ url                   │   │ manhwa_id (FK)        │
   │ title                 │   │ date_epoch_day        │
   │ favicon_url           │   │ duration_seconds      │
   │ visited_at            │   │ chapters_read_count   │
   │ is_private = 0 (ONLY) │   └───────────────────────┘
   └───────────────────────┘
```

- **Zero-Persistence Guarantee for Private Browsing**: The `reading_history` and `reading_stats` tables have a database-level contract: records from private sessions are NEVER inserted. Private tabs operate exclusively with an in-memory ephemeral list discarded on tab closure.

---

## 5. Core Module Interfaces

### 5.1 Browser Engine & Session Manager
```kotlin
interface IBrowserSessionManager {
    val activeTabs: StateFlow<List<BrowserTab>>
    val currentTab: StateFlow<BrowserTab?>

    fun openTab(url: String, isPrivate: Boolean): BrowserTab
    fun closeTab(tabId: String)
    fun switchTab(tabId: String)
    fun clearPrivateSessions()
}
```

### 5.2 Content Filtering Engine
```kotlin
interface IContentFilterEngine {
    fun shouldBlockUrl(url: String, documentUrl: String): Boolean
    fun getCosmeticCss(domain: String): String
    val blockedCount: StateFlow<Int>
}
```

### 5.3 Manhwa Tracker & Source Adapters
```kotlin
data class DetectedManhwa(
    val title: String,
    val chapterNumber: Float,
    val chapterTitle: String,
    val sourceUrl: String,
    val coverUrl: String?
)

interface ISourceAdapter {
    fun canHandle(url: String): Boolean
    suspend fun extractChapterInfo(url: String, htmlOrDom: String): DetectedManhwa?
    suspend fun checkForLatestChapter(manhwaUrl: String): Float?
}

interface IManhwaTracker {
    suspend fun onPageNavigated(url: String, html: String, isPrivate: Boolean)
    suspend fun updateReadingProgress(manhwaId: Long, chapterNumber: Float, percent: Float)
}
```

### 5.4 Privacy Manager
```kotlin
interface IPrivacyManager {
    fun getCookieManager(isPrivate: Boolean): CookieManager
    fun purgePrivateData()
    fun isUrlAllowedForAnalytics(url: String, isPrivate: Boolean): Boolean // Always false for private
}
```

### 5.5 Translation & OCR Engine
```kotlin
interface ITranslationEngine {
    suspend fun recognizeTextInBitmap(bitmap: Bitmap): List<OcrTextBlock>
    suspend fun translateText(sourceText: String, sourceLang: String, targetLang: String): String
}
```

---

## 6. Technical Risks & Android Native Requirements

| Risk / Requirement | Impact | Mitigation Strategy |
|---|---|---|
| **Ad-blocking in WebView** | High network overhead and ads sneaking through async | Intercept requests synchronously in `shouldInterceptRequest` using compiled Trie/Hash set of blocked domains and rules; return empty `WebResourceResponse("text/plain", "UTF-8", EmptyInputStream())`. |
| **Private Session Leaks** | Cookies/Cache spilling over between tabs | Normal tabs use standard system `CookieManager.getInstance()`. Private tabs instantiate isolated storage or clear cookies immediately upon exit, disabling cache via `WebSettings.LOAD_NO_CACHE`. |
| **DOM Manhwa Extraction** | Dynamic client-rendered websites don't expose HTML in initial HTTP response | Inject a non-intrusive JavaScript snippet via `evaluateJavascript` once `onPageFinished` fires to read `document.title`, OpenGraph tags, and reader image elements safely. |
| **Background Chapter Checks** | Android 14+ battery optimizations killing background threads | Use Android `WorkManager` with `Constraints(NetworkType.CONNECTED, BatteryNotLow=true)` running every 6–12 hours. |
| **Image OCR Memory Overhead** | Full-resolution manhwa strip screenshots causing OOM | Crop only the active viewport or visible reader frame, downscale to max 1920px before feeding into ML Kit `InputImage`. |

---

## 7. Phased Implementation Plan

- **Phase 1: Foundation & Architecture** *(Current Phase)*
  - Environment inspection, architecture documentation, database schema, module interfaces, implementation plan artifact creation.
- **Phase 2: Local Database & Core Repository Layer**
  - Implement Room entities (`ManhwaEntity`, `ChapterEntity`, `HistoryEntity`, `BookmarkEntity`, `ReadingStatsEntity`), DAOs, Database migrations, and Repositories.
- **Phase 3: Browser Engine, Multi-Tab Manager & Privacy Isolation**
  - Implement `BrowserSessionManager`, `MangaFlowWebViewClient`, normal vs private session storage isolation, address bar omnibox, and multi-tab switcher UI.
- **Phase 4: Content Filtering & Ad-Block Engine**
  - Implement rule parser for EasyList format, fast domain trie matching, `WebResourceResponse` interceptor, and cosmetic CSS injection to hide ad banners.
- **Phase 5: Manhwa Tracker & Source Detection**
  - Implement `ManhwaTracker`, URL pattern matching, metadata scraper (generic webtoon + popular scanlation structures), and automatic reading progress tracking.
- **Phase 6: Library, Reading Statistics & Detail Views**
  - Build Jetpack Compose UI for Library (categorized by Reading, Completed, On Hold, Plan to Read), Manhwa Detail with chapter checklist, and Reading Analytics screen.
- **Phase 7: On-Device OCR & Image Translation**
  - Integrate ML Kit Text Recognition, viewport snapshot extractor, text bubble bounding boxes, and on-device overlay translation.
- **Phase 8: Background Monitoring & OS Notifications**
  - Setup Android `WorkManager` periodic task, new chapter difference detector, and Android system Notification Channels with deep-links to the chapter.
- **Phase 9: Verification, UI Polish & Final Build**
  - End-to-end integration testing, Robolectric tests, Gradle build verification via `compile_applet`.

---

## 8. Implementation Checklist

- [x] Inspect project environment and toolchains
- [x] Propose directory structure and modular clean architecture
- [x] Define Room SQLite database entities and relationships
- [x] Define typed interfaces for all sub-engines (Browser, Privacy, Filter, Tracker, Translation)
- [x] Document privacy contracts (zero-persistence rule for private sessions)
- [x] Document technical risks and native Android mitigations
- [x] Create implementation plan artifact for user approval
- [ ] Implement Room database & DAOs (Phase 2)
- [ ] Implement Browser & Session Manager with normal/private tabs (Phase 3)
- [ ] Implement Ad-Blocker & EasyList interceptor (Phase 4)
- [ ] Implement Manhwa Tracker & Progress auto-detection (Phase 5)
- [ ] Implement Library UI & Reading Statistics (Phase 6)
- [ ] Implement On-Device ML Kit OCR overlay (Phase 7)
- [ ] Implement WorkManager chapter monitoring & notifications (Phase 8)
- [ ] Final build verification and testing (Phase 9)

---

## 9. Local vs External Services Matrix

| Feature | Execution Model | External Dependency / Notes |
|---|---|---|
| **General Web Browsing** | Local Native | Standard Android `WebView` |
| **Private Browsing** | 100% Local | Ephemeral in-memory context; zero external calls |
| **Ad & Tracker Blocking** | 100% Local | Bundled EasyList rules matching; zero cloud lookup |
| **Manhwa Library & History** | 100% Local | On-device Room SQLite database |
| **Reading Statistics** | 100% Local | On-device computation & aggregation |
| **Progress Tracking** | 100% Local | Client-side DOM / URL heuristic parsing |
| **Chapter Notifications** | 100% Local | On-device Android `WorkManager` querying source web pages |
| **OCR Text Extraction** | 100% Local | Google ML Kit on-device Vision (zero network latency) |
| **Text Translation** | Local / Optional Ext | Local on-device translation model (offline capability) |
| **Cloud Sync & Backup** | Optional Future | Optional future backend (FastAPI / Firebase) |
