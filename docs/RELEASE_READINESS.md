# MangaFlow Release Readiness

**Assessment date:** 2026-10-28 (Asia/Calcutta)

**Status:** **Not release-ready; no APK/AAB was generated.**

**Source tree:** `/home/user/Mangaflow` (working-tree changes are not build-verified)

## Summary

MangaFlow is a **native Android/Kotlin/Jetpack Compose** project, not an Expo or TypeScript project. No cloud build service was used. The checkout cannot run Gradle here: it has only `gradle/wrapper/gradle-wrapper.properties`, no `gradlew`/`gradlew.bat`/wrapper JAR, no system Gradle, no Android SDK or `adb`, and only OpenJDK 11. AGP 9.1.1's official compatibility table requires JDK 17 and Gradle 9.3.1; the configured project also targets Android SDK 36 minor API 1. See the [AGP 9.1 release notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes).

Consequently, tests, Android lint, compilation, APK signing, emulator/device smoke tests, and update tests **were not run**. The static checks below do not replace those checks. This report does not certify security or release readiness.

## Release configuration found in the project

| Setting | Current value/status |
|---|---|
| Display name | `MangaFlow` (`@string/app_name`) |
| Application ID | `com.aistudio.mangaflow.qwvxtk` (existing value retained to preserve update identity; ownership of the namespace was not verified) |
| Namespace | `com.example` |
| Version | `versionName = "1.0"`, `versionCode = 1` |
| SDK levels | `minSdk 24`; `targetSdk 36`; compile SDK 36 minor API 1 |
| Launcher icon | Adaptive and density-specific launcher assets exist under `app/src/main/res/mipmap-*`; the adaptive icon was visually inspected. |
| Splash screen | Platform splash colors/icon are configured in `app/src/main/res/values/themes.xml` for Android 12+ with a window-background fallback. Resource linking/runtime display remain unverified. |
| Release signing | Release signing is wired to external environment variables: `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`; `KEY_ALIAS` is optional and defaults to `upload`. No keystore or credentials were supplied, generated, or committed. |
| Debug signing | The project points its debug signing configuration at a root `debug.keystore`; no such file is present in this checkout. |
| Play bundle | Not built. No Google Play release was requested explicitly. |

The current application ID and version are unchanged from the existing project configuration. Changing an Android application ID creates a different app and prevents an in-place update; keep it stable once an APK has been distributed. A version-code increase is required for each subsequent update.

## Pre-release checks

| Check | Result |
|---|---|
| Previous-phase integration | The working tree contains the prior audit/remediation changes, but without compilation/tests it is **not possible to confirm that all phases integrate or run together**. |
| Automated unit tests | Attempted `./gradlew :app:testDebugUnitTest`; blocked before Gradle started: `./gradlew: No such file or directory` (exit 127). No test passed/failed result is available. |
| TypeScript checks | **Not applicable:** zero `.ts`/`.tsx` files, zero `package.json` files, and zero Expo config files were found. |
| Lint | Attempted `./gradlew :app:lint`; same missing-wrapper failure (exit 127). Android lint did not run. |
| Debug APK build | Attempted `./gradlew :app:assembleDebug`; same missing-wrapper failure (exit 127). No APK exists. |
| Release APK build | Attempted `./gradlew :app:assembleRelease`; same missing-wrapper failure (exit 127). No signed release APK exists. |
| Native dependencies | Gradle declares native Android dependencies for Compose/Material 3, Room/KSP, AndroidX WebKit, WorkManager, coroutines, Coil and OkHttp. Firebase AI/App Check and some Retrofit/Moshi artifacts are also declared, but no Firebase initialization/AI/FCM app flow was found in source. Dependency resolution and APK contents were not verified. |
| Permissions | Manifest statically requests Internet, network state, notifications, camera, microphone and coarse location; camera/microphone hardware are marked optional. It does not request storage permission; the fine-location permission was removed because the current browser prompt only requests coarse location. Runtime permission behavior was not device-tested. |
| Secrets | Heuristic credential-pattern scans of current source/build/config files and reachable Git history found zero matches. No tracked `.env`, Google Services JSON, signing keystore, certificate, or private-key file was found; release signing environment variables are unset. This is not a substitute for a dedicated secret-scanning service. Local signing files are git-ignored. |
| App name/icon/ID | Resource/config values and launcher files are present. Icon was visually inspected. Splash and Android resource linking were not build-tested. |

Static validation performed: `git diff --check` completed without reported whitespace errors; Python parsed the 10 Android XML files and the Gradle version-catalog TOML successfully. **No Kotlin compiler/type check or Android resource-linking build ran.**

## Functional verification matrix

All items below are **not runtime-tested** because no Android SDK, emulator, or device is available in this environment.

| Requested test | Current code-level evidence / limitation |
|---|---|
| Fresh install, app launch, database initialization | Room database and Compose app wiring exist. No installation or launch test was possible. Room has a destructive migration fallback for unhandled schema versions; an upgrade test is still required. |
| Browser navigation | Android WebView/address/navigation handlers exist. WebView navigation, downloads, certificates, renderer recovery, and permissions were not device-tested. |
| Tracker persistence | Room-backed series/chapter/progress services and import/export paths exist. Unit tests are present but unrun. |
| Private browsing | Multi-profile support is provider-gated and private page loading is intended to fail closed if isolation is unavailable. Private history/progress are guarded from normal DB writes. Profile cleanup is provider-dependent: cookies/site storage/cache are cleared and profile deletion is requested, but the WebView provider may delay or reject physical deletion. Orphan profile deletion is attempted at next app start. This is not a forensic-erasure guarantee and requires real-provider lifecycle tests. |
| Notifications | Permission prompt/helper and WorkManager paths exist. The global monitoring and notification settings now gate scheduling/alert creation in source. No permission, notification, background-worker, or tap-navigation test ran. Android notification history may retain tracked-series/chapter metadata. |
| Ad blocker | Request filtering and cosmetic CSS exist; the rule set/parser is limited and is not a full EasyList implementation. No live-site or resource-blocking test ran. |
| Translation | On-device glossary and external providers exist. Cloud requests are consent-gated and cloud providers reject private-session requests. Remote text transmission, API-key handling, network errors and locale behavior were not exercised. The glossary is not a general-purpose model. |
| App update | Not tested. Current app ID/version are preserved, but update installation requires a previously installed APK signed with the same key. No release key or device was available. |

## Permission and privacy notes for release review

- `CAMERA`, `RECORD_AUDIO`, and coarse location are needed only for website permission prompts; the browser prompts before granting web permissions. `POST_NOTIFICATIONS` is runtime-gated on supported Android versions. Verify denial, revocation, and repeated-prompt behavior on device.
- `android:usesCleartextTraffic="true"` is currently enabled globally so the embedded browser can visit HTTP sites. It broadens cleartext networking beyond the WebView. Decide whether this is acceptable for a distributable release; HTTPS-only translation endpoints are enforced in the translation code, but the platform-wide cleartext policy remains permissive.
- Auto Backup is enabled. The current backup rules exclude the WebView profile directory and the encrypted translation-credential preferences. The Room database is not excluded, so normal library, browser-history, settings, reading-stat and notification rows may be included in Android backup/transfer. Private browsing paths are designed not to write private history/progress rows, but this must still be tested against a real provider and backup restore.
- Private download details are memory-only in MangaFlow, but the file remains and Android `DownloadManager` may retain URL/session-cookie/notification metadata; the UI discloses this.
- A source scan found no `addJavascriptInterface`/`@JavascriptInterface`, app-level `Log.*`/`println` calls, or crash-reporting SDK usage. This was source inspection only, not a binary inspection.
- OCR remains incomplete: the repository has a bitmap heuristic/overlay but no OCR recognizer/model dependency. Do not advertise image text recognition as functional.

## Local build workflow (no cloud service used)

This is a Gradle Android app; Expo/EAS and TypeScript commands do not apply. A cloud account or paid cloud-build service is not required for the recommended local Android Studio/Gradle workflow.

1. Use Android Studio/Gradle tooling compatible with AGP 9.1.1, JDK 17, Gradle 9.3.1, Android SDK Platform 36.1, and Build Tools 36.0.0. The current checkout has no wrapper launcher/JAR; restore the official Gradle wrapper for 9.3.1 or use a trusted local Gradle 9.3.1 installation. Do not substitute an unverified wrapper binary.
2. Configure Android SDK location through Android Studio or `local.properties`/`ANDROID_HOME`.
3. For debug builds, supply the local debug keystore expected by `app/build.gradle.kts` (the current checkout does not contain it).
4. For a release APK, supply the organization's existing keystore and credentials through the process environment or CI secret store. Do **not** commit them or place passwords in Gradle files. `KEY_ALIAS` defaults to `upload` unless explicitly set.
5. Run from the repository root after tooling and signing are configured:

   ```bash
   ./gradlew :app:testDebugUnitTest
   ./gradlew :app:lint
   ./gradlew :app:assembleDebug
   ./gradlew :app:assembleRelease
   ```

   If Google Play distribution is later desired, also run `./gradlew :app:bundleRelease` after release signing is configured.

Expected output paths **if those builds succeed** (none currently exists):

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`
- Play bundle: `app/build/outputs/bundle/release/app-release.aab`

## Installation and update test instructions

On a clean emulator/device with USB debugging enabled, install a debug APK with:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

For an update test, install the prior build and then use `adb install -r` with the new APK. Both packages must have the same application ID and signing certificate; otherwise Android rejects the update. Use a test device and preserve any needed app data before uninstalling or clearing it. Verify installation/launch, Room initialization, browser navigation, explicit tracker progress confirmation, private-session teardown/process recreation, notification permission and tap routing, ad filtering, translation consent, and an update over the prior version.

## Release checklist

- [ ] Restore/verify the official Gradle 9.3.1 wrapper and use JDK 17 + SDK 36.1/build-tools 36.0.0.
- [ ] Provide the project owner's signing key and secret values through a secure environment; never commit them.
- [ ] Resolve or deliberately accept the globally permissive cleartext-traffic policy.
- [ ] Decide whether the Android backup of the Room database (including normal browser history/notification metadata) is intended.
- [ ] Run tests, lint, debug/release builds, and inspect/signature-check their outputs.
- [ ] Install on a clean device and perform the requested functional smoke tests.
- [ ] Install over the previous version using the same application ID/signing certificate; verify data migration and app update behavior.
- [ ] Verify Play-target requirements separately if publishing an AAB.
- [ ] Document OCR and ad-blocking limitations; do not claim OCR is implemented or security-certified.

**Release artifact:** none generated.

**Source code:** `/home/user/Mangaflow`.
