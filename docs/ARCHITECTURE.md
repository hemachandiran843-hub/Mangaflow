# MangaFlow Architecture (As Implemented)

This document records the implementation found at the audited baseline revision, not an intended replacement design. The shared working tree has since received uncompiled audit/release changes; for current verification limits and configuration see [RELEASE_READINESS.md](RELEASE_READINESS.md). The older `MANGAFLOW_ARCHITECTURE.md`, `OCR_AND_TRANSLATION_ARCHITECTURE.md`, and `CONTENT_FILTERING_AND_PRIVACY_LIMITATIONS.md` contain design statements that should not be treated as proof of implementation; important differences are called out below.

## 1. Technology and module boundaries

- **Application:** one native Android module, `:app`, using Kotlin and Jetpack Compose/Material 3.
- **Browser:** Android `WebView` embedded in Compose via `AndroidView`.
- **Persistence:** Room/SQLite, database schema version 3, with DAOs and repositories.
- **Background checks:** WorkManager `CoroutineWorker` calling the local chapter-detection service.
- **Networking:** OkHttp-based source adapters and translation providers; Retrofit/Moshi are declared dependencies but are not the main source-adapter path in the inspected code.
- **Cloud:** no server module. `CloudMonitoringClient` has a local stub implementation. Firebase AI/App Check dependencies are declared, but no Firebase AI, Firebase Messaging, or FCM application flow was found in the source.
- **OCR:** a viewport bitmap and overlay exist, but no text-recognition SDK/model is integrated.

The namespace is `com.example`; the Gradle application ID is `com.aistudio.mangaflow.qwvxtk`. Build configuration uses AGP 9.1.1, Kotlin 2.2.10, Gradle 9.3.1 in wrapper properties, Java source/target 11, min SDK 24, and target SDK 36 (compile SDK release 36, minor API 1). The checkout is missing the Gradle wrapper launcher/JAR, so these values have not been validated with a build.

## 2. High-level runtime wiring

```text
MainActivity
  └─ MangaFlowTheme + AppNavigation (manual Compose destination state)
       ├─ Home / Library / Detail / Stats / Updates / Settings screens
       └─ BrowserScreen
            └─ one AndroidView WebView
                 ├─ WebViewClient: request filtering, page callbacks, history, tracker bridge
                 └─ WebChromeClient: title/progress

MangaFlowApp (Application singleton/service locator)
  ├─ MangaFlowDatabase (Room)
  ├─ repositories + ManhwaTrackerService
  ├─ BrowserSessionManager + PrivacyManager
  ├─ ContentFilterEngine
  ├─ ManhwaTracker + BrowserTrackerBridge
  ├─ SourceRegistry + ChapterDetectionService
  ├─ TranslationManager + OcrTranslationEngine
  └─ ChapterMonitorWorker scheduling + NotificationHelper
```

There is no dependency-injection framework in the inspected wiring. `MangaFlowApp.instance` is the application-wide service locator. View models and screens retrieve repositories/engines from it.

### Application startup (baseline revision)

At the audited baseline, `MangaFlowApp.onCreate()`:

1. Creates/opens `MangaFlowDatabase`.
2. Instantiates `ContentFilterEngine`, `PrivacyManager`, `BrowserSessionManager`, translation/OCR objects, and the source registry.
3. Constructs the tracker service/bridge, repositories, and `ChapterDetectionService`.
4. Creates the Android notification channel.
5. Calls `ChapterMonitorWorker.schedule(this)` unconditionally inside a try/catch.

The unconditional schedule is significant: `SettingsViewModel` later reads `CHAPTER_MONITORING_ENABLED`, but startup scheduling does not first honor that stored preference.

### Navigation and UI state

`AppNavigation.kt` uses a `Scaffold` with a manually remembered `AppDestination` enum and local state for detail/stats. The navigation-compose dependency is declared, but the inspected app does not create a `NavHost`/navigation graph. Main screens live under `ui/screens/`; shared browser/settings/library components live under `ui/components/`.

## 3. Persistence and data model

`MangaFlowDatabase.kt` is a Room database at schema version 3 (`exportSchema = false`). Its registered entities are:

- `SeriesEntity` — the active tracked-series model.
- `ChapterEntity` — chapter identity, URL, read/download flags, and detection timestamps.
- `ReadingProgressEntity` — page/index/percentage progress per series/chapter.
- `ReadingSessionEntity` — duration/count/date rows used by statistics.
- `NotificationEntity` — in-app release notification rows.
- `BookmarkEntity`, `HistoryEntity`, `AppSettingEntity`, and `ContentSourceEntity`.

DAOs are in `data/local/dao/`. Repositories are in `data/repository/`; `ManhwaTrackerService` is the higher-level service for library CRUD, status changes, progress, statistics, JSON import/export, sample data, and clearing library data.

### Migration and data-safety details

- `MIGRATION_1_2` creates the normalized series/chapter/progress/session/notification/settings/source structures and alters bookmarks.
- `MIGRATION_2_3` adds monitoring status/preferences to `series`.
- The Room builder registers both migrations and also uses `fallbackToDestructiveMigration(dropAllTables = true)`. An upgrade path outside the registered migrations can therefore drop database tables.
- No database schema snapshots or migration-focused test were found.
- `ManhwaEntity`, `ManhwaDao`, `ReadingStatsEntity`, and `ReadingStatsDao` remain in source but are not registered in `MangaFlowDatabase`; the active schema uses `SeriesEntity` and `ReadingSessionEntity` instead.
- The manifest sets `allowBackup="true"`; the supplied backup/data-extraction XML has no explicit database exclusion. Confirm the desired backup policy for history and app settings.

## 4. Browser implementation

### Address/navigation and WebView

`BrowserScreen.kt` creates an Android WebView, enables JavaScript and DOM storage, allows compatibility mixed content, attaches a download listener, and sets a WebViewClient/WebChromeClient. `UrlParser.kt` turns domains into HTTPS URLs and routes other input to DuckDuckGo/Google/Bing search URLs. `BrowserSecurityManager` contains safe/external scheme helpers; in the inspected WebViewClient, external action schemes are delegated, but the helper is not used as a full allowlist for every navigation.

The UI also exposes browser history/bookmarks, find-in-page, desktop user-agent mode, generic `DownloadManager` downloads, and a reader-mode JavaScript transformation. These paths have not been exercised on a device.

### Tabs

`BrowserSessionManager` keeps lists of normal/private `BrowserTab` models in `StateFlow`, enforces a per-list tab limit, tracks the selected tab, and has recently-closed normal-tab metadata. These models do not own separate WebView instances. `BrowserScreen` contains one `AndroidView`; tab switching calls `loadUrl()` on that same WebView. Consequently, independent page state, cookies, scroll position, and back/forward history per tab are not provided by the current design.

### Request filtering

`WebViewClient.shouldInterceptRequest()` calls `ContentFilterEngine.shouldBlockRequest()` and returns an empty response for blocked requests. On page finish, the client injects CSS returned by `getCosmeticCss()`.

`ContentFilterEngine` is an in-memory custom filter with:

- a built-in set of ad hostnames;
- a separate set of tracker hostnames;
- an optional small aggressive-host set;
- URL path/keyword checks;
- a simplified parser for a limited subset of ABP/EasyList-like rules;
- in-memory allowlist/per-site controls and counters;
- a fixed cosmetic CSS snippet.

The Settings “refresh” action parses a hard-coded sample list; it does not download a current EasyList. This is not a complete EasyList engine. In addition, the WebViewClient is created in the AndroidView factory and refers to Compose values such as `currentHost`/`isPrivate`; those callbacks can retain values from the first creation rather than the latest composition.

## 5. Privacy and private-mode boundary

The private-mode UI is backed by a boolean and separate tab-model list. `BrowserRepository.recordHistory()` rejects calls explicitly passed `isPrivate = true`; the WebView page-finish callback has a conditional history write. `TranslationManager` skips its in-memory cache and cloud providers when its caller passes `isPrivate = true`. `BrowserSessionManager` avoids adding private tabs to the recently-closed list.

These are **application-level guards**, not storage-profile isolation:

- No separate WebView data directory/profile is configured.
- `CookieManager` and `WebStorage` are Android WebView global services; cookies and DOM storage are not isolated by the `BrowserTab.isPrivate` flag.
- `domStorageEnabled` is true for the shared WebView. `LOAD_NO_CACHE` is applied only in the WebView factory when the current composition is private; there is no per-tab cache/storage context.
- `BrowserSessionManager.setPrivateMode()` does not call `PrivacyManager.enterPrivateSession()` or `exitPrivateSession()`. Thus `ChapterDetectionService`'s `isPrivateSessionActive()` notification check is not connected to tab-mode changes.
- When the last private tab closes, `PrivacyManager.purgePrivateData()` removes session cookies, calls `WebStorage.deleteAllData()`, and clears WebView cache by creating a cleanup WebView. This cleanup is not scoped to private origins and can affect normal browsing state; it does not demonstrate isolation.
- WebView client callbacks created once may keep stale `isPrivate`/host values. The history callback then writes `isPrivate = false` after its captured guard passes, and tracker callbacks use the same captured mode value. This needs a runtime integration test/fix before making a privacy guarantee.
- The custom translation API key, when entered, is saved as an ordinary `app_settings` value. The manifest also enables app backup; the XML rules do not explicitly exclude the DB.

`PrivacyManager.getPrivacyNotice()` correctly says private browsing does not make the user anonymous to websites, the ISP, or network administrators. That caveat does not resolve the local storage-isolation gaps above.

## 6. Manhwa tracking and automatic progress

### Manual library flow

`LibraryScreen` and `LibraryViewModel` provide status/search/sort controls, add/edit/delete dialogs, and import/export/sample-data actions. `ManhwaTrackerService` validates some inputs, stores series/chapters in Room, and supports statuses including `READING`, `PLAN_TO_READ`, `COMPLETED`, `ON_HOLD`, and `DROPPED`.

Manually added series can seed generic placeholder chapter rows (`Chapter 1`, etc.) from an entered chapter count; this is not a source-derived chapter list. The detail screen lists stored chapters and lets a reader mark them read/unread or open the chapter URL.

### Browser tracker flow

`BrowserTrackerBridge` delegates to `ManhwaTracker`. Detection uses URL path regexes and the WebTOON `episode_no` query parameter, plus the browser page title. No JavaScript DOM extractor is wired from the WebView to the tracker. When detection matches an existing series and the tab is treated as non-private, `ManhwaTracker.onUrlLoaded()` updates last-read state, inserts a read chapter, and adds a fixed 300-second session. The code can do this on page load rather than after observed reading/completion, so it can inflate progress and analytics.

The browser's “Add to Library” action creates/updates a series from the detected title and URL. Detection quality depends on source URL formats and title matching; it is heuristic, not universal automatic tracking.

## 7. Chapter sources, monitoring, and notifications

### Source adapters

`SourceRegistry` orders the built-in adapters as MangaDex, RSS, WEBTOON, custom adapters, then generic Open Web metadata.

- `MangaDexSourceAdapter` calls MangaDex's public API for metadata and the English chapter feed.
- `RssFeedSourceAdapter` parses RSS/Atom XML from a supplied feed URL.
- `WebtoonsSourceAdapter` fetches public WEBTOON HTML and extracts episodes with regular expressions; anti-bot/markup changes can defeat it.
- `OpenWebMetaSourceAdapter` fetches generic HTML metadata and heuristically tries to infer a chapter from title/URL. It is not a general chapter-list service.

`ChapterNormalizer` canonicalizes some numbers/special identifiers and `ChapterDetectionService` compares adapter results with Room rows, records check status/backoff, stores new chapters, and inserts local notification rows.

### WorkManager and notification delivery

`ChapterMonitorWorker` checks enabled series on a periodic network-constrained WorkManager job; Settings presents configurable intervals and a notification permission request appears in Updates on Android 13+. `NotificationHelper` creates local Android notifications that carry an `OPEN_URL` extra.

Limitations observed:

- `MangaFlowApp.onCreate()` schedules the periodic worker unconditionally with the default interval, rather than first applying the saved global enabled/interval settings.
- `SettingsViewModel.setNewChapterNotifications()` changes only an in-memory StateFlow. The detector checks each series' `notificationsEnabled` but does not consult that global switch.
- The optional cloud client is a stub; no backend, FCM registration/token flow, or push receiver was found.
- `MainActivity` updates the browser tab URL from a notification, but `AppNavigation` begins on Home and does not route to Browser for a cold start.
- Source/API behavior, scheduling, runtime notification permission, and notification navigation were not tested on a device.

## 8. Translation, webpage text, and OCR overlay

### Webpage/selected text translation

`WebpageTranslationHelper` uses JavaScript TreeWalker extraction, then `BrowserViewModel` passes extracted strings to `TranslationManager` and applies/restores text through `evaluateJavascript()`.

`TranslationManager` supports three provider IDs:

- `on_device`: `OnDeviceGlossaryTranslationProvider`, a small static glossary/heuristic. It replaces selected Korean terms, returns fixed example phrases for some scripts, and otherwise often returns input text unchanged. It is not a general-purpose offline translation model.
- `public_web`: MyMemory over HTTPS; requires the opt-in `allowCloudTranslation` flag and rejects requests marked private.
- `custom_api`: configurable HTTP endpoint/provider; also requires consent and rejects requests marked private.

Translation preferences/endpoint/key are persisted using `AppSettingDao`. The API key is not encrypted by application code. Private/cache protections rely on a correct `isPrivate` argument from the UI; they do not create a separate WebView data profile.

### Image OCR claim

The UI path in `BrowserScreen` draws the visible WebView into a bitmap and calls `OcrTranslationEngine`. The engine downsamples large images, samples a sparse 6×10 grid for light/dark pixels, then places predetermined sample dialogue text or a predetermined fallback phrase in `OcrBox` objects. `OcrOverlayView` renders the boxes. No ML Kit `TextRecognizer`, `InputImage`, OCR model, or OCR dependency was found. Therefore the present code is an overlay/pixel heuristic demonstration, not OCR or image translation.

The repository's `.env.example` contains only a commented placeholder key name; no GEMINI API call path was found. Firebase AI/Gemini should not be considered active functionality solely because a dependency/metadata entry exists.

## 9. Tests, build configuration, and current verification boundary

Test source is under `app/src/test/java/com/example/` and `app/src/androidTest/java/com/example/`. It covers selected local services/parsers/guards but does not provide a test run result. In particular, direct helper tests do not establish that the WebView uses private-only storage, that a live publisher parser works, or that text is recognized from a real image.

Gradle configuration is in `settings.gradle.kts`, root `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, and `gradle/wrapper/gradle-wrapper.properties`. The checkout does not contain the wrapper scripts/JAR. The audit environment has no Gradle or Android SDK; no app build, Kotlin type-check, lint, Robolectric run, or instrumentation run has been reported as successful.

## 10. Existing documentation and interpretation

The existing `docs/MANGAFLOW_ARCHITECTURE.md` contains a proposed/aspirational directory tree and a phased plan. The older content-filtering/privacy and OCR documents state guarantees such as private WebView instances being released, strict storage isolation, and ML Kit OCR that are not supported by the inspected source. `docs/OPTIONAL_BACKEND_MONITORING.md` describes an optional example backend, not code included in this repository.

Preserve those files as project history/reference, but use this `ARCHITECTURE.md` plus `PROJECT_AUDIT.md` for current implementation facts until the older documents are reconciled.
