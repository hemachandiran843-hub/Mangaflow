# Implementation Plan: MangaFlow Android Browser & Manhwa Tracker

MangaFlow is a high-performance Android mobile browser combining general-purpose web browsing with integrated manhwa library tracking, EasyList ad/tracker blocking, isolated private browsing, on-device OCR chapter translation, reading habits analytics, and automatic background chapter release notifications.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were confirmed in Phase 1:
> - **Runtime & Architecture**: Pure Native Android utilizing Jetpack Compose for the UI, Android `WebView` with custom `WebResourceResponse` interceptors for the browser engine, and Room SQLite for local-first persistence.
> - **OCR & Translation Strategy**: On-device ML Kit Text Recognition for bubble extraction and translation, maintaining zero-network privacy and low latency.
> - **Ad & Content Filtering**: EasyList rule interception in native Kotlin with cosmetic stylesheet injection to collapse empty ad containers.
> - **Strict Private Session Boundary**: Ephemeral in-memory context; zero persistence of private history, logs, or progress.

---

## 1. Overview & Core Concept

- **What It Does**: MangaFlow functions as a daily mobile web browser with tabs, bookmarks, and search, while actively monitoring manhwa/manga reading sites. When a chapter is opened, MangaFlow automatically detects the title and chapter progress, updates the user's reading library, tracks reading speed and statistics, blocks intrusive web ads, and allows instant on-screen OCR translation of untranslated speech bubbles.
- **Target Audience**: Manhwa and webtoon enthusiasts who read across diverse scanlation and publisher websites on Android and want unified tracking, ad-free reading, and privacy without relying on separate manual tracking apps.
- **Key Value**: Eliminates manual reading logs, removes invasive popups and redirects, protects browsing privacy, and keeps readers updated on new chapter releases via local background monitoring.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Browse & Auto-Detect**: User navigates to any webtoon/manhwa website. MangaFlow loads the page with ads stripped. A subtle bottom chip appears: *"Detected: Solo Leveling Ch. 120 — Added to Reading"*.
2. **Reading & Reader Mode**: Reading on long-strip websites works smoothly with pull-to-refresh, fullscreen toggle, and optional Reader Mode to declutter headers and comments.
3. **Instant OCR Bubble Translation**: User taps the "Translate" action on the toolbar. An overlay highlights text inside raw image strips and displays translated text directly over the bubble.
4. **Library & Status Management**: User taps the Library tab to view books sorted by *Reading*, *Completed*, *On Hold*, and *Plan to Read*, showing latest read chapter vs total available chapters.
5. **Private Browsing**: Tapping Incognito opens a dark amethyst-themed private tab. History, cookies, and reading statistics are strictly kept ephemeral and wiped on tab close.
6. **Reading Statistics**: View daily streaks, minutes read today/this week, and chapters completed with interactive Material 3 metric cards.

### Visual Identity & Theme
- **Aesthetic Direction**: Sleek, modern manga reader with high contrast, deep charcoal/slate surfaces (`#121316`), vibrant cyan-blue primary accents (`#4D8BFF`), and electric amethyst accents (`#B775FF`) for private mode.
- **Color Tokens**:
  - `Background`: `#0F1014` (Deep Oled Black/Dark Slate)
  - `Surface`: `#191B22` / `SurfaceVariant`: `#252833`
  - `Primary`: `#4D8BFF` (MangaFlow Cyber Blue)
  - `Secondary / Private`: `#B775FF` (Amethyst Purple)
  - `Accent / Success`: `#00E599` (Mint Green for new chapters and streaks)
- **Typography & Hierarchy**: Clean, legible modern sans typography with high typographic hierarchy (`titleLarge`, `headlineMedium`, `bodyMedium`).
- **Motion & Interactions**: Smooth bottom sheet transitions, staggered library grid reveals, animated badge counters when ads are intercepted, and subtle progress bars.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Pure Native Jetpack Compose & Android WebView**
  - *Chosen Approach*: Implement the full mobile browser engine directly in Kotlin using Android's native `WebView` and Jetpack Compose.
  - *Why*: Direct access to synchronous `shouldInterceptRequest` for zero-lag ad blocking, native cookie managers for private tabs, and full memory efficiency without cross-bridge overhead.
  - *Alternatives Considered*: Hybrid React Native was evaluated, but native Kotlin avoids JS-bridge performance bottlenecks during high-frequency WebView network interception.

- **Decision 2: Local-First Room SQLite vs Cloud Backend**
  - *Chosen Approach*: 100% on-device SQLite database with versioned Room DAOs.
  - *Why*: Maximum user privacy, instant zero-latency loading, offline reading of saved chapters, and zero reliance on external server accounts.

- **Decision 3: Zero-Persistence Private Browsing Architecture**
  - *Chosen Approach*: Private tabs run with a distinct in-memory state, non-persistent cookies, and a hardcoded bypass around the history and analytics DAOs.
  - *Why*: Ensures strict compliance with user privacy requirements; private manhwa reads never appear in public stats or history logs.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```text
┌────────────────────────────────────────────────────────────────────────┐
│                          Jetpack Compose UI                            │
│  ┌──────────────────┬─────────────────┬─────────────────┬───────────┐  │
│  │  BrowserScreen   │  LibraryScreen  │   StatsScreen   │  Settings │  │
│  │ (WebView + Bar)  │ (Tracked items) │ (Streaks/Hours) │ (Shields) │  │
│  └────────┬─────────┴────────┬────────┴────────┬────────┴─────┬─────┘  │
└───────────┼──────────────────┼─────────────────┼──────────────┼────────┘
            │                  │                 │              │
            ▼                  ▼                 ▼              ▼
┌───────────────────────┐ ┌──────────────────────────────────────────────┐
│   BrowserViewModel    │ │      Library & ReadingStats ViewModels       │
└───────────┬───────────┘ └──────────────────────┬───────────────────────┘
            │                                    │
            ▼                                    ▼
┌───────────────────────┐ ┌──────────────────────────────────────────────┐
│ BrowserSessionManager │ │             ManhwaRepository                 │
│ - Multi-tab container │ │ - Library CRUD & chapter state               │
│ - Ephemeral Incognito │ │ - Reading statistics aggregator              │
└─────┬───────────┬─────┘ └──────────────────────┬───────────────────────┘
      │           │                              │
      ▼           ▼                              ▼
┌───────────┐ ┌─────────┐        ┌───────────────────────────────┐
│ Content   │ │ Privacy │        │       MangaFlowDatabase       │
│ Filter    │ │ Manager │        │ (Room SQLite: Manhwa, Chapter,│
│ (EasyList)│ │ (Memory)│        │  History, Bookmarks, Stats)   │
└─────┬─────┘ └─────────┘        └───────────────────────────────┘
      │                                          ▲
      ▼                                          │
┌───────────────────────┐                        │
│    Android WebView    │                        │
│ - Ad-block intercept  │                        │
│ - Reader mode script  │                        │
└───────────┬───────────┘                        │
            │                                    │
            ▼                                    │
┌───────────────────────┐                        │
│     ManhwaTracker     │────────────────────────┤
│ - URL / DOM Heuristic │                        │
│ - Progress detector   │                        │
└───────────┬───────────┘                        │
            │                                    │
            ▼                                    │
┌───────────────────────┐                        │
│   MLKitOcrProcessor   │                        │
│ - On-device Text Recog│                        │
└───────────────────────┘                        │
                                                 │
┌───────────────────────┐                        │
│  ChapterMonitorWorker │────────────────────────┘
│ - Periodic background │
│ - Android Notification│
└───────────────────────┘
```

---

## 5. Phased Implementation Plan

### Phase 1: Mobile UI Shell & Design System *(Completed)*
- Implemented 5 primary destinations (Home, Library, Browser placeholder, Updates, Settings) with bottom navigation.
- Established custom Material 3 Dark/Light themes with accent palettes and adaptive launcher icons.

### Phase 2: Local Manhwa Tracker & Room SQLite Persistence *(Completed)*
- Normalized Room v2 database with `MIGRATION_1_2` migration supporting:
  1. `series` (SeriesEntity: title, alternate titles, sourceId, cover, ratings, reading status, last read, last updated)
  2. `chapters` (ChapterEntity: seriesId, nullable chapterNumber for non-numeric chapters, chapterIdentifier, unique constraints)
  3. `reading_progress` (ReadingProgressEntity: seriesId, chapterId, pageIndex, totalPages, progressPercent, isCompleted)
  4. `reading_sessions` (ReadingSessionEntity: seriesId, chapterId, durationSeconds, chaptersReadCount, dateEpochDay)
  5. `notifications` (NotificationEntity: seriesId, seriesTitle, chapterTitle, timestamp, isRead)
  6. `bookmarks` (BookmarkEntity)
  7. `app_settings` (AppSettingEntity)
  8. `content_sources` (ContentSourceEntity)
- Built `ManhwaTrackerService` with 14 functional tracker capabilities: manual addition, metadata editing, deletion with confirmation, 5 status transitions (`READING`, `PLAN_TO_READ`, `COMPLETED`, `ON_HOLD`, `DROPPED`), distinct non-duplicate reading statistics calculation, `updateReadingProgress` API with validation and >=90% completion threshold, and JSON export/import.
- Connected Home, Library, and Updates screens to the database; removed hardcoded demo data; implemented clean empty states and explicit sample-data loading.
- Comprehensive unit and Robolectric tests in `ManhwaTrackerServiceTest.kt` passing 100%.

### Phase 3: Browser Engine & Multi-Tab Session Manager
- Build `BrowserSessionManager` supporting concurrent normal and private tabs.
- Implement custom `MangaFlowWebViewClient` with navigation guards and safe URL parsing.
- Build Jetpack Compose `BrowserScreen` featuring Omnibox address bar, progress indicator, navigation controls, and tab overview drawer.

### Phase 4: Content Filtering & Ad-Block Engine
- Build `ContentFilterEngine` utilizing compiled domain trie lookup and URL pattern matching based on EasyList conventions.
- Implement `WebResourceResponse` synchronous interception in `WebViewClient`.
- Implement cosmetic CSS stylesheet injector to prevent empty ad placeholders.

### Phase 5: Manhwa Tracker & Progress Detection
- Create `ManhwaTracker` with URL heuristics and non-invasive JavaScript DOM extractor.
- Automatically detect current manhwa title, chapter number, and reading position.
- Prompt or automatically sync progress into the local library database.

### Phase 6: Library, Detail Views & Reading Analytics
- Build `LibraryScreen` with tabbed categories: *Reading*, *Completed*, *On Hold*, *Plan to Read*.
- Build `ManhwaDetailScreen` displaying chapter lists, reading checklist, cover art, and source links.
- Build `ReadingStatsScreen` visualizing daily streaks, reading time, and chapter milestones.

### Phase 7: On-Device OCR & Image Translation
- Integrate Google ML Kit on-device Text Recognition.
- Implement screen viewport frame capture and text bubble detection.
- Render translation overlay composable over the active reader.

### Phase 8: Background Monitoring & OS Notifications
- Implement `ChapterMonitorWorker` with Android `WorkManager`.
- Implement Android Notification channels and heads-up alerts for newly detected chapters.

---

## 6. Verification Plan

- Run `compile_applet` to ensure standard Android compilation succeeds.
- Execute unit and Robolectric tests (`gradle :app:testDebugUnitTest`) for:
  - Room DAO CRUD operations and query correctness.
  - `ContentFilterEngine` EasyList pattern matching and URL blocking.
  - `BrowserSessionManager` tab switching and ephemeral private tab disposal.
  - `ManhwaTracker` chapter number and title extraction heuristics.
