# MangaFlow Project Audit

- **Audit date:** 2026-10-28 (Asia/Calcutta)
- **Repository:** `hemachandiran843-hub/Mangaflow`
- **Audited revision:** `main` at `9e597f8` (`chore: initialize project structure`)
- **Scope:** The initial inspection was read-only and described the clean baseline commit above. The shared working tree now also contains audit-remediation and release-preparation changes; those changes are summarized in [RELEASE_READINESS.md](RELEASE_READINESS.md) and have not been compiled or runtime-tested. Treat implementation findings below as baseline findings unless explicitly updated.

## Executive finding

> **Working-tree note (2026-10-28):** The source has since been revised to add provider-gated private WebView profiles with fail-closed page loading and best-effort cleanup, explicit-only reading-progress writes, persisted notification/monitoring gates, notification-intent routing, browser download/import handling, secure credential storage, and WebView/reader hardening. These are code-presence notes only; no build or test has validated them. See `docs/RELEASE_READINESS.md` for the current release blockers and status.

MangaFlow is a **single-module, native Android/Kotlin application** built around Jetpack Compose, Android `WebView`, Room/SQLite, and WorkManager. The repository contains real UI and substantial local tracking/browser code, but the “Phase 6” label is not evidence that all listed capabilities work.

The current checkout **cannot be built or tested in the audit environment**, so buildability remains unverified—not proven impossible. The project has Gradle wrapper properties but no `gradlew`, `gradlew.bat`, or wrapper JAR; no system Gradle, Android SDK, `adb`, or `sdkmanager` was available; and the available Java runtime is 11. The attempted unit-test command stopped before Gradle started. There are 53 unit-test annotations and one instrumented-test annotation in source, but none were executed.

Most important findings at the audited baseline revision (`9e597f8`):

1. **Private browsing is not an isolated WebView profile.** History and translation guards exist at code level, but regular/private tabs share the same WebView storage services. Closing private tabs invokes global storage/cache cleanup, and the privacy manager's enter/exit methods are not connected to the tab manager. Browser callbacks also capture tab/privacy state when the WebView is first created.
2. **OCR is not implemented as OCR.** The current image path scans a pixel grid and displays hard-coded dialogue/fallback text; no ML Kit Text Recognition dependency or recognizer exists.
3. **Reading analytics and automatic progress can be synthetic.** Opening a detected chapter can mark it read and record a fixed five-minute session. The stats view model also invents fallback daily minutes/chapter counts.
4. **The content filter is a small custom rule set, not a full EasyList implementation.** Its “refresh” path loads a hard-coded sample; filtering preferences/rules are in memory.
5. **Background checking and notifications have code, but integration is incomplete.** The global release-alert switch is not used by the detector, monitoring is scheduled unconditionally on application startup, and notification taps do not select the Browser destination on a cold start.
6. Several existing architecture/privacy/OCR documents describe intended or stronger behavior than the inspected code provides. See the evidence and caveats below; this audit is the implementation-focused reference.

## Technology stack actually declared

| Area | Evidence in repository | Audit finding |
|---|---|---|
| Android app | `settings.gradle.kts`, `app/build.gradle.kts`, `AndroidManifest.xml` | One Android application module (`:app`); no server/backend module. |
| Language/UI | `gradle/libs.versions.toml`, `app/src/main/java/com/example/ui/` | Kotlin 2.2.10, Jetpack Compose, Material 3. Navigation is implemented with local Compose state in `AppNavigation`, not a `NavHost`. |
| Android build | `app/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties` | AGP 9.1.1; wrapper properties request Gradle 9.3.1; `compileSdk` 36 minor API 1, `targetSdk` 36, `minSdk` 24; Java source/target compatibility 11. The wrapper launcher/JAR is missing. |
| Persistence | `MangaFlowDatabase.kt`, `data/local/` | Room 2.7.0 with KSP 2.3.5, SQLite schema version 3, DAOs/entities, and migrations 1→2 and 2→3. |
| Async/background | version catalog and `engine/worker/` | Coroutines 1.10.2 and WorkManager 2.9.1 are declared. |
| Browser/network | `BrowserScreen.kt`, `engine/browser/`, source adapters | Android WebView; OkHttp 4.10.0, Retrofit 2.12.0, Moshi 1.15.2 are declared. |
| Images | Gradle catalog and UI screens | Coil Compose 2.7.0 is declared. |
| Firebase/AI | `app/build.gradle.kts`, version catalog, `metadata.json` | Firebase AI and App Check artifacts are declared, but no Firebase AI/FCM integration was found in app source. No `google-services.json` is present. `CloudMonitoringClient` defaults to a local stub. |
| OCR | `OcrTranslationEngine.kt`, Gradle files | Android bitmap APIs and a Compose overlay are used, but no ML Kit/Text Recognition dependency or OCR API is present. |

## Repository map

```text
app/src/main/AndroidManifest.xml       Android permissions, application/activity config
app/src/main/java/com/example/
  MangaFlowApp.kt                      Application singleton/service wiring
  MainActivity.kt                      Compose activity and notification URL handling
  data/local/                          Room database, entities, DAOs
  data/repository/                     Browser, manhwa, reading-stat repositories
  data/tracker/                        ManhwaTrackerService (local library operations)
  domain/model/                        Browser-tab and detected-title models
  engine/browser/                      URL parsing, tab state, download/security helpers
  engine/filter/                       Host/path filtering and simplified rule parser
  engine/privacy/                      Privacy helper and global WebView cleanup
  engine/source/                       MangaDex, WEBTOON, RSS, generic metadata adapters;
                                       chapter detection and cloud-monitoring interface/stub
  engine/tracker/                      URL-based tracker and browser bridge
  engine/translation/                   Glossary, external providers, webpage helper,
                                       pixel-grid image overlay generator
  engine/worker/                       WorkManager chapter polling and notifications
  ui/                                  Compose screens/components/theme/navigation
app/src/test/java/                     Unit and Robolectric source tests
app/src/androidTest/java/              One basic instrumented context test
docs/                                  Four pre-existing design/limitations documents
```

There is no root `README.md`, no tracked dependency lockfile, and no CI workflow was found. Existing untracked/ignored local configuration was not opened or printed.

## Feature verification matrix

“Verified” below means inspected in source. It does **not** mean exercised on a device: no build or test run was possible.

| Claimed area | Classification | What the source actually does / evidence |
|---|---|---|
| Mobile UI | **Implemented but not tested** | Compose screens exist for Home, Library, Browser, Updates, Settings, manhwa detail, and reading stats. `AppNavigation.kt` switches screens with a `remember` state variable. App launch/runtime rendering was not verified. |
| Local library and tracking data | **Implemented but not tested** | `MangaFlowDatabase.kt`, entities/DAOs, repositories, and `ManhwaTrackerService.kt` provide Room-backed series/chapter CRUD, status changes, progress records, import/export, and sample data. Tests exist but were not run. |
| Database migration safety | **Implemented but not tested; important risk** | Version 3 includes 1→2 and 2→3 migration code. The builder also calls `fallbackToDestructiveMigration(dropAllTables = true)`, which can erase data for an unhandled schema version. `exportSchema = false`; no migration test or exported schema was found. |
| Mobile browser | **Partially implemented** | A real Android WebView is hosted by `BrowserScreen`; address parsing, normal navigation, reload/back/forward, find-in-page, bookmarks/history, desktop user agent, generic downloads, error display, and reader-mode JavaScript are present. It is one `AndroidView`/WebView: tab cards hold URL/title state and switching loads a URL into that same view, so independent per-tab WebView state/back stacks are not implemented. |
| Ad/tracker blocking | **Partially implemented** | `ContentFilterEngine` blocks a built-in list of domains and URL substrings, parses a small subset of EasyList-like lines, and supplies CSS. `BrowserScreen` calls it from `shouldInterceptRequest`. There is no full EasyList parser/list download or durable rule store; Settings “refresh” applies hard-coded sample lines. Unit tests cover selected rules only and were not run. |
| Private browsing | **Partially implemented; not safe to claim strict isolation** | Private/normal tab lists, UI styling, history guards, translator guards, and no-restore behavior exist. The actual WebView uses the default global cookie/storage context; `domStorageEnabled` remains enabled. `PrivacyManager.purgePrivateData()` removes session cookies and clears WebStorage/cache globally, potentially affecting normal browsing too. `BrowserSessionManager` does not call `enterPrivateSession()`/`exitPrivateSession()`. `BrowserScreen` callbacks capture `isPrivate` in the WebView factory, which can become stale after mode changes. |
| Automatic chapter detection/progress | **Partially implemented** | `ManhwaTracker` uses URL regexes/query parameters and page title; no DOM extractor is wired. `onPageLoaded` may update a matching series, insert a chapter marked read, and record a fixed 300-second session simply because a page loaded. Source URL/title matching and chapter URL patterns are heuristic; automatic reads/stats are therefore not reliable evidence of actual reading. |
| Source adapters/update detection | **Partially implemented; externally dependent** | MangaDex API, WEBTOON public HTML regex parsing, RSS/Atom parsing, and a generic OpenGraph/title fallback are present. Network access, source HTML/API changes, rate limits, and bot checks can prevent results. These were not tested against live services. The generic adapter does not provide a reliable universal chapter feed. |
| Reading analytics | **Partially implemented; values can be fabricated** | Statistics use Room read flags/session rows, but tracker events record fixed five-minute sessions. `ReadingStatsViewModel` can substitute 25 minutes/3 chapters for today when any chapters are read, and `ManhwaTrackerService` applies minimum chapter-count fallbacks for week/month values. Streaks use the session-day map. These figures should not be treated as accurate activity measurements. |
| Background checks/system notifications | **Partially implemented; not runtime tested** | WorkManager scheduling, local polling, notification channel/permission handling, Room notifications, and notification construction exist. No FCM receiver/service or deployed backend exists. The global “System Release Alerts” state is not consulted by `ChapterDetectionService`; app startup schedules monitoring unconditionally; a cold-start notification tap updates a URL but `AppNavigation` still initializes at Home. |
| Webpage text translation | **Partially implemented / external-service conditional** | A TreeWalker script extracts page text and applies/restores translated text. Providers include a small offline glossary, MyMemory HTTP requests, and a configurable custom HTTP API. Cloud providers are opt-in and code checks the `isPrivate` argument. The default glossary is not a general translation model; no provider/network integration was exercised. The custom API key is stored in the Room `app_settings` table without application-level encryption. |
| Image OCR/bubble translation | **Not implemented as claimed** | `BrowserScreen` captures a viewport bitmap, but `OcrTranslationEngine` tests a sparse 6×10 grid for light/dark pixels and attaches hard-coded example Korean phrases or a fixed fallback. It does not recognize text from the image. The overlay UI exists; actual OCR does not. |
| Cloud monitoring / FCM | **Not implemented** | `CloudMonitoringClient.kt` contains an interface and `LocalFirstCloudMonitoringStub`; registration returns a locally generated ID and polling always returns an empty list. The optional-backend document is design/example material, not a backend implementation. |
| Offline chapter library | **Not implemented** | A generic `DownloadManager` listener downloads ordinary browser files. `ChapterEntity` has download fields, but no in-app chapter-download repository/flow was found. |

## Project health and validation results

### Git and repository state

- The clone was clean at inspection start (`main`, aligned with `origin/main` at `9e597f8`); history contains two commits. No history was rewritten.
- No app code, dependencies, or existing documentation were changed during this audit. The three handoff documents are the only intended additions.

### Build/toolchain

- `./gradlew :app:testDebugUnitTest` was attempted and failed before Gradle started: `./gradlew: No such file or directory` (exit 127).
- `gradle`, `adb`, and `sdkmanager` are not installed in this environment. Android SDK environment variables are unset. Available Java is OpenJDK 11.
- The repository has only `gradle/wrapper/gradle-wrapper.properties`; the launcher scripts and wrapper JAR are absent. The configured Android SDK platform is not available in this environment.
- The checked-out tree has no `google-services.json`, `local.properties`, `.env`, `debug.keystore`, or release keystore. The Gradle file permits missing Google Services configuration with a warning, but its debug signing config explicitly points to a root-level `debug.keystore`; verify the build environment supplies it. Release signing also expects keystore/password configuration. No secret values were read or printed.
- **Build result:** not run / cannot determine from this environment. No claim is made that source compilation succeeds or fails.

### Tests/static checks

- Found 53 `@Test` annotations under `app/src/test/java` and one under `app/src/androidTest/java`. The test suite includes tracker/Room, URL parser, filtering/privacy helpers, source parsing/detection, and translation/OCR helper tests. These tests do not establish WebView cookie isolation or real OCR: privacy tests pass an `isPrivate` flag directly, and OCR tests validate the current heuristic output.
- Unit, Robolectric, and instrumentation tests were **not run** because no Gradle launcher/runtime/Android SDK is available.
- Read-only static validation passed: Python parsed the Gradle version-catalog TOML and all 10 Android XML files; `git diff --check` produced no errors at the time of the check. No Kotlin compiler/type-check or Android lint ran.

### Important project-health risks

1. **Privacy boundary:** browser private-mode tabs share global WebView data; cleanup is global rather than scoped. The manifest enables cleartext traffic, WebView allows compatibility mixed content, and application backup is enabled with no explicit database exclusion. Clarify whether browsing history/configuration should enter Android backup before making privacy guarantees.
2. **Data safety:** unhandled Room schema upgrades can drop all tables due to the destructive fallback. Migration paths are not covered by a test in the inspected suite.
3. **Progress/stat accuracy:** page load can be counted as a completed read and a five-minute session; stats include hard-coded fallback values.
4. **Settings integration:** several SettingsViewModel fields are only in-memory (including global release alerts, theme/accent, and some reading/privacy toggles). The release-alert field is not read by the detector. Chapter-monitor scheduling in `MangaFlowApp.onCreate()` is unconditional even if the saved preference is disabled.
5. **WebView state:** one WebView is shared across the tab list; captured callbacks may use stale current-host/private-mode values. The safe-scheme helper is not used as a complete navigation allowlist.
6. **Credentials/config:** custom translation credentials are persisted in ordinary Room settings; Android backup policy is not explicitly restrictive. Firebase AI/Gemini configuration is declared in metadata/dependencies but no API integration was found.
7. **Documentation drift:** older architecture/OCR/privacy documents include intended behavior (ML Kit, strict private isolation, tab destruction) not present in source. Keep them, but do not use them as verification evidence until reconciled.

## Build and run commands

There is no browser/dev-server command: this is an Android app. Once the environment has a compatible JDK (AGP 9.x requires a modern JDK; use JDK 17 or newer), Android SDK Platform 36.1/build tools, a working signing setup, and Gradle 9.3.1, run from the repository root:

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
gradle :app:lintDebug
gradle :app:connectedDebugAndroidTest
gradle :app:installDebug
```

`connectedDebugAndroidTest` and `installDebug` require a connected emulator/device and Android tooling. After a successful install, the launcher activity is `com.example.MainActivity` in application ID `com.aistudio.mangaflow.qwvxtk`:

```bash
adb shell am start -n com.aistudio.mangaflow.qwvxtk/com.example.MainActivity
```

If the project is updated to include the missing Gradle wrapper, the equivalent commands should use `./gradlew` instead. Those wrapper commands **do not work in the current checkout**. The repository itself contains no documented README runbook; Android Studio can open the root project, but it still needs a compatible JDK/SDK and working signing config.

## What should be resolved before Phase 7 work

1. Establish a reproducible baseline: restore/provide the intended Gradle wrapper or a documented Gradle 9.3.1 install, configure JDK/SDK/signing, then run assemble, unit tests, lint, and migration tests. Record actual results before changing feature code.
2. Decide and implement the intended privacy guarantee before representing private mode as isolated: fix the shared global WebView storage and callback-state boundary, or explicitly scope the product promise to history suppression. The current purge may clear normal-profile state.
3. Correct progress/stat semantics so a page load is not treated as proof of completion or a fixed-duration reading session; remove synthetic analytics fallbacks before presenting them as measured values.
4. Decide the supported OCR/translation approach and acceptance criteria. The current Phase 7 plan says on-device OCR, but the code has no recognizer. Select whether external translation is permitted and how user text/API credentials are protected.
5. Resolve data protection/migration policy: confirm Android backup inclusion/exclusion; test Room 1→2→3 upgrades; decide whether destructive fallback is acceptable for production data.
6. Reconcile the existing aspirational docs and phase numbering with this inspected implementation. The repository plan places OCR in Phase 7 and background notifications in Phase 8; the handoff text groups them under Phase 6.

## Decisions that may need your input

- Is strict private-session isolation (cookies, DOM storage, cache, and history separated) required, or is no-history persistence the intended scope? The current implementation does not provide strict isolation.
- Should app data—including browsing history and custom translation settings—be excluded from Android cloud/device backup?
- What OCR and translation providers are acceptable for Phase 7 (on-device only vs. user-opted external service), and what accuracy/privacy acceptance criteria should be used?
- Should the repo's existing phase map (1–8) be canonical, or should it be renumbered to match the handoff's Phase 6 description?
