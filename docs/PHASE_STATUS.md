# MangaFlow Phase Status (Evidence-Based)

- **Status date:** 2026-10-28
- **Evidence source:** source/configuration inspection at baseline revision `9e597f8`. This table is baseline status, not a claim that the current worktree is verified. The working tree contains uncompiled release/audit changes; see [RELEASE_READINESS.md](RELEASE_READINESS.md). No phase is marked complete based only on a screen, a button, a design document, or the prior handoff description.

## Status key

- **Implemented in source; unverified** — relevant production code exists, but no successful build/runtime test was obtained.
- **Partially implemented** — some working-path code is present, but key requirements are missing, unreliable, or only represented by UI/state.
- **Not implemented as specified** — the named feature is absent or replaced by a demo/placeholder.
- **Undocumented / unknown** — the repository does not define the phase scope or provide evidence for completion.

## Phases 0–9

| Phase | Scope from repository plan / handoff | Actual evidence | Status |
|---|---|---|---|
| **0 — Project/bootstrap baseline** | No Phase 0 scope is defined in the checked-in plan. The repository contains a Gradle Android project scaffold and two commits. | Root Gradle files, `app/`, Android resources, and source tree are present. No root README or working Gradle wrapper launcher is present. | **Undocumented / unknown** — bootstrap files exist, but no phase definition or verification evidence. |
| **1 — Mobile UI shell and design system** | Repository plan names Home, Library, Browser, Updates, Settings, and Material 3 themes. | Compose screens, `AppNavigation.kt`, theme files, launcher resources, and UI components exist. No app build/device execution was available. | **Implemented in source; unverified** |
| **2 — Local tracker and Room persistence** | Repository plan describes local manhwa/series/chapter tracking and Room. | Room v3 entities/DAOs, 1→2 and 2→3 migration code, repositories, `ManhwaTrackerService`, and service tests exist. Tests were not run. Migration tests/schema exports were not found; destructive fallback is enabled. | **Partially implemented** — core local CRUD exists, but migration/data-safety and runtime behavior are unverified. |
| **3 — Browser engine and multi-tab sessions** | Repository plan calls for browser navigation and normal/private tab management. | WebView, address parsing, browser controls, StateFlow tab models, history/bookmarks, download handler, and tab-switcher UI exist. The browser screen owns one WebView and reloads URLs when switching tabs; it does not preserve independent WebView state per tab. | **Partially implemented** |
| **4 — Content filtering/ad blocking** | Repository plan calls for EasyList-style filtering, request interception, and cosmetic filtering. | `ContentFilterEngine`, limited parser, fixed host/path rules, WebView request interception, CSS injection, and unit-test source exist. No full EasyList implementation or live list download; “refresh” uses hard-coded sample rules. Tests were not run. | **Partially implemented** |
| **5 — Automatic manhwa/chapter progress detection** | Repository plan calls for URL/DOM chapter detection and library progress synchronization. | URL/title/query heuristics and `BrowserTrackerBridge` exist. No DOM extraction is wired. Page-load detection may mark a chapter read and add a fixed five-minute session. | **Partially implemented** — heuristic path exists; automatic progress and analytics are not reliable enough to call complete. |
| **6 — Library, detail views, reading analytics** | Repository plan names library filters, detail/chapter views, and reading statistics. The handoff groups browser/privacy/notifications/OCR under Phase 6, but the repository plan puts those in Phases 3–8. | Library add/edit/delete/status/import/export/sample-data flows, detail/chapter checklist, and stats UI exist. Stats derive from Room, but fixed-duration tracker sessions and hard-coded stats fallbacks can produce synthetic values. No runtime verification. | **Partially implemented** |
| **7 — On-device OCR and image translation** | Repository plan explicitly names on-device OCR, viewport capture, and translation overlay. | Viewport capture, `OcrBox` models, overlay UI, and a bitmap-processing class exist. The class scans a sparse grid and supplies hard-coded example text; no ML Kit recognizer or other OCR engine is declared or called. Offline glossary translation is not a general translation model. | **Not implemented as specified** — the overlay/pixel heuristic is a prototype, not OCR. |
| **8 — Background monitoring and OS notifications** | Repository plan names periodic chapter checks, notification channels, and release alerts. | WorkManager worker, MangaDex/WEBTOON/RSS/generic adapters, Room notifications, notification helper, and permission UI exist. No FCM/backend implementation. The global alert switch is not wired to detection, startup schedules work unconditionally, source results were not live-tested, and notification cold-start navigation stays on Home. | **Partially implemented** |
| **9 — No scope documented** | No Phase 9 scope or acceptance criteria were found in checked-in docs/source. | No distinct Phase 9 module, feature boundary, tests, or plan entry was found. | **Undocumented / unknown** |

## Phase-gate notes

- The repository plan in `.aistudio/artifacts/.../implementation_plan.md` defines Phases 1–8; it does not define 0 or 9. The supplied handoff's Phase 6 summary does not match that repository phase map. This status table follows the repository plan where it exists and records the mismatch rather than inventing completed work.
- The audit found 53 unit-test annotations and one instrumented-test annotation, but none could be executed: the wrapper scripts/JAR are absent, system Gradle and Android SDK are unavailable, and the available Java runtime is 11.
- **No phase is certified complete.** “Implemented in source; unverified” means code was found, not that it compiles or behaves correctly.

## Minimum readiness before starting Phase 7 implementation

1. Make the Android build reproducible (Gradle 9.3.1 wrapper or documented install; compatible JDK/SDK/signing) and record successful assemble, test, and lint results.
2. Resolve the private-mode storage/callback boundary and backup policy; tests currently validate helper flags, not actual WebView profile isolation.
3. Correct automatic-progress and stats measurement so page visits do not create synthetic completion/time records.
4. Confirm Phase 7's accepted OCR/translation scope and provider/privacy requirements; the current implementation does not recognize image text.
5. Add/execute database upgrade tests and decide whether destructive migration fallback is acceptable.
6. Reconcile the older design/privacy/OCR documents with the code, while retaining them as history/reference.
