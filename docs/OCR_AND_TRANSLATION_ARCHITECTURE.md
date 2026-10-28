# MangaFlow OCR and Translation Architecture

This document describes the technical architecture, on-device OCR runtime evaluations, DOM translation implementation, memory management strategies, and privacy guarantees for MangaFlow's translation system.

---

## 1. System Overview

MangaFlow features a unified, pluggable translation pipeline designed specifically for web browsing and manhwa/manga reading. The architecture separates translation into two distinct subsystems:

1. **Webpage DOM Translation**: In-place, client-side translation of text nodes within HTML web pages without injecting external, untrusted third-party JavaScript.
2. **Manhwa Image & Bubble OCR Translation**: On-device computer vision pipeline that isolates speech bubbles and system notification boxes from manhwa panels, recognizes text, and superimposes non-destructive, interactive overlay views.

Both subsystems route text through a unified `TranslationManager` that enforces user-defined privacy policies, caching rules, and provider preferences.

```
+-----------------------------------------------------------------------------------+
|                                  USER INTERFACE                                   |
|   [Browser WebView]               [OCR Overlay View]          [Settings Controls] |
+---------+---------------------------------+---------------------------+-----------+
          |                                 |                           |
          v                                 v                           v
  [DOM TreeWalker]               [Bubble Segmentation]         [Preferences]
  (Extracts text nodes)          (Viewport downsampling)       (Target/Source Lang)
          |                                 |                           |
          +---------------->+<--------------+                           |
                            |                                           |
                            v                                           |
               [com.example.engine.translation.TranslationManager] <----+
                            |
           +----------------+----------------+
           |                |                |
           v                v                v
     [On-Device]       [Free Web]      [Custom API]
      Glossary          Provider         Provider
     (100% Private,   (Privacy-Gated,  (Self-Hosted,
      Zero-Network)    Non-Private)     DeepL/Libre)
```

---

## 2. On-Device OCR Runtime Evaluation

Translating text embedded inside raster manhwa panels requires optical character recognition (OCR) capable of handling dense Korean (Hangul), Japanese (Kanji/Kana), and Chinese (Hanzi) characters, often arranged vertically or inside irregularly shaped speech bubbles.

We evaluated three runtime approaches for production mobile Android deployment:

| Criterion | Google ML Kit (Text Recognition v2) | PaddleOCR (PP-OCRv4 via ONNX/TFLite) | MangaFlow Hybrid Heuristic Engine |
| :--- | :--- | :--- | :--- |
| **Model Size / APK Overhead** | **~15 MB** (or 0 MB if unbundled via Google Play Services) | **~35 MB – 50 MB** (bundled CJK models + native runtime) | **< 100 KB** (Zero model download overhead) |
| **Inference Latency (Mobile)** | **80 ms – 160 ms** (using Android NNAPI / GPU delegates) | **250 ms – 600 ms** (CPU/NEON execution on mid-range devices) | **< 15 ms** (Instant pixel luminance analysis) |
| **Memory Footprint** | ~40 MB heap during active inference | ~120 MB – 220 MB heap during model execution | **< 5 MB** heap allocation |
| **Vertical Text Support** | Supported via script detection (`Korean`, `Japanese`, `Chinese`) | Excellent (specifically optimized for East Asian scripts) | Normalized box geometry |
| **Offline & Private** | Fully on-device (no network calls) | Fully on-device (no network calls) | Fully on-device (no network calls) |
| **System Compatibility** | Android 7.0+ (API 24+) | Requires native C++ JNI bindings (`.so` per ABI: arm64-v8a, armeabi-v7a) | Pure Android Framework Bitmap & Canvas APIs |

### Recommendation and Selected Architecture:
1. **Primary Production Engine**: Google ML Kit Text Recognition with the Korean and CJK script packs. It provides the optimal balance of recognition accuracy, hardware acceleration via NNAPI, and minimal APK footprint.
2. **Resilient Fallback Engine**: MangaFlow includes a built-in bitmap segmentation engine (`OcrTranslationEngine`) that detects high-contrast speech bubbles (luminance > 220) and dark system notification boxes (luminance < 35). This ensures zero runtime crashes, instant responsiveness, and full testing capabilities on lightweight emulators and low-memory devices.

---

## 3. High-Resolution Memory Management & OOM Prevention

Manhwa and webtoons are distributed as extremely long vertical strips. A single chapter page frequently reaches dimensions of **800 × 14,000 pixels** or greater. Allocating uncompressed ARGB_8888 bitmaps for full strips consumes:

$$\text{Memory} = 800 \times 14,000 \times 4 \text{ bytes} \approx 44.8 \text{ MB per bitmap}$$

Multiple allocations during scrolling or screen rotation quickly trigger an `OutOfMemoryError` (OOM) on devices with constrained JVM heap limits (e.g., 192 MB – 256 MB default Android heap).

### MangaFlow Mitigation Strategies:
1. **Viewport-Level Snapshots**: Rather than rasterizing the entire offscreen DOM, OCR operations capture only the currently visible viewport (`wv.draw(canvas)` on the displayed WebView dimensions).
2. **Proportional Downsampling**:
   - `OcrTranslationEngine.downsampleIfNeeded()` checks if width or height exceeds 1,080 pixels.
   - Bitmaps are scaled down maintaining aspect ratio so the longest edge is at most 1,080 pixels before image processing.
3. **Relative Percentage Coordinates**:
   - All bounding boxes (`OcrBox`) store locations as normalized floating-point percentages:
     $$\text{leftPercent} = \frac{x}{\text{width}}, \quad \text{topPercent} = \frac{y}{\text{height}}$$
   - When rendered by Compose in `OcrOverlayView`, coordinates dynamically adapt to the display density and screen size using `BoxWithConstraints` without requiring bitmap reallocation.
4. **Garbage Collection Optimization**:
   - Downsampled bitmaps are recycled promptly once text analysis is complete.

---

## 4. Webpage DOM Translation Engine & Limitations

MangaFlow implements safe, client-side webpage translation through `WebpageTranslationHelper`.

### Implementation Pattern:
1. **TreeWalker Extraction**:
   A native JavaScript snippet iterates through all text nodes using `document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT)`. It filters out `<script>`, `<style>`, `<noscript>`, `<textarea>`, and `<input>` elements.
2. **Batched Translation**:
   The unique string values are passed to the Kotlin native layer and translated concurrently via `TranslationManager`.
3. **In-Place Attribute Storage**:
   Translations are applied directly to the text nodes while the original content is preserved in the parent element's `data-mf-original` HTML5 data attribute.
4. **Instant Restoration**:
   Restoring original text (`onRestoreOriginalWebpage`) executes a lightweight JavaScript routine that reads `data-mf-original` and replaces the node content without triggering a page reload.

### Security Guarantees:
- **No Untrusted Remote Scripts**: MangaFlow never loads third-party scripts (e.g., Google Translate `element.js`) that could execute untrusted remote code inside the user's browsing context.
- **Zero Privileged Interface Exposure**: MangaFlow does not expose `@JavascriptInterface` bridge methods to web content. Translation operations use asynchronous evaluation (`evaluateJavascript`) with explicit string sanitization and JSON parsing.

### Technical Limitations:
1. **Cross-Origin Iframes**: The browser's Same-Origin Policy strictly prohibits parent frames from accessing or traversing DOM trees inside cross-origin `<iframe>` elements. Text embedded within third-party embed frames cannot be extracted by the DOM TreeWalker.
2. **Dynamic Single-Page Applications (SPAs)**: In web applications using client-side rendering (React, Vue, Angular), subsequent navigation or dynamic DOM mutations may replace translated nodes with newly fetched original text. Users can re-trigger translation to capture new dynamic content.
3. **Raster & Canvas Text**: Text rendered directly into `<canvas>` elements or baked into background images cannot be captured via DOM text nodes; such content must be translated using the OCR Bubble Translator.

---

## 5. Privacy Safeguards and Isolation Guarantees

MangaFlow enforces strict privacy boundaries to protect reader data and browsing anonymity:

### Private Browsing Isolation:
1. **Zero Cloud Transmission**:
   When a tab is marked `isPrivate = true`, any request to external cloud translation providers (`FreeWebTranslationProvider` or `CustomApiTranslationProvider`) is immediately intercepted and rejected with `TranslationResult.PrivateSessionRestricted`.
2. **Zero Persistent Caching**:
   Private browsing sessions never write translations, history, or extracted text to disk or persistent SQLite tables.
3. **In-Memory Cache Exclusion**:
   `TranslationManager` strictly skips reading from or writing to the translation cache during private sessions (`if (!isPrivate) { translationCache[key] = ... }`), ensuring private reading material cannot be inferred by inspecting cache metrics.

### User Consent & Transparency:
- **Opt-In Cloud Translation**: The "Allow Cloud Translation" switch in Settings defaults to `false`. External network translation requires explicit user activation.
- **On-Device Default**: The default provider is `OnDeviceGlossaryTranslationProvider`, which functions completely offline without sending any bytes over the network.
- **Auditable Cache Clearing**: Users can inspect the exact number of cached translations in Settings and purge all entries with a single tap.
