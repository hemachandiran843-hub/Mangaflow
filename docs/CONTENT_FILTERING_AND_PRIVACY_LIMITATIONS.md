# MangaFlow Content Filtering & Privacy Architecture: Capabilities & Limitations

## 1. Content Filtering Architecture & Request Interception

MangaFlow implements native network-level content filtering through Android `WebViewClient.shouldInterceptRequest(view, request)` combined with runtime cosmetic CSS injection.

### Interceptable Request Types
- **HTTP and HTTPS Subresources**:
  - Image assets (`<img>`, CSS background images, SVG)
  - JavaScript files (`<script src="...">`)
  - Sub-frames and nested iframes (`<iframe>`)
  - Stylesheets (`<link rel="stylesheet">`)
  - Asynchronous XHR and `fetch()` requests directed to third-party ad exchanges, trackers, and popup brokers
- **Top-Level Navigation**:
  - Unsolicited popunder and popup navigations targeting known ad networks and redirect loops are dropped or intercepted
  - External schemes (`market:`, `intent:`, `mailto:`, `tel:`) are trapped by `shouldOverrideUrlLoading` and delegated safely via `BrowserSecurityManager`

### Non-Interceptable Request Types & Platform Limitations
In accordance with Android System WebView constraints, the following traffic cannot be intercepted:
1. **WebSockets (`ws://`, `wss://`)**: Android WebView does not pass WebSocket handshakes through `shouldInterceptRequest`. While cosmetic rules collapse ad wrappers, WebSocket-based telemetry or ads cannot be blocked at the HTTP layer.
2. **Pre-connection DNS Queries**: DNS lookups initiated by the underlying Chromium network stack occur prior to HTTP request interception; domain blocking occurs by returning an empty byte stream (`WebResourceResponse("text/plain", "UTF-8", emptyStream)`), not by DNS blackholing.
3. **WebRTC Media Streams**: Direct peer-to-peer data channels bypass `shouldInterceptRequest`.
4. **First-Party Integrated Content**: Advertisements server-side rendered directly into the HTML body or images hosted on the publisher's primary domain without distinguishable URL indicators cannot be completely intercepted without breaking first-party manhwa image strips.

### Statement on Ad-Blocking Completeness
MangaFlow does **not** claim complete elimination of all advertisements across the web. Advertisers continuously morph domains and obfuscate delivery. MangaFlow targets intrusive popup scripts, mobile redirectors, floating video units, and high-frequency tracking beacons through EasyList-compatible pattern matching.

---

## 2. Isolated Private Browsing Architecture

MangaFlow provides a dedicated private browsing session manager enforcing strict data separation between persistent daily browsing and private reading sessions.

### Implemented Isolation Measures
1. **Zero Persistence of Browsing History**:
   - `PrivacyManager.shouldPersistHistory(isPrivate)` returns `false`.
   - Private URLs and page titles are completely excluded from the SQLite `history` table.
2. **Zero Persistence of Search Queries**:
   - Omnibox queries submitted in private tabs are never written to search suggestions or settings.
3. **Zero Persistence of Reading Sessions & Statistics**:
   - `ReadingSessionEntity` records are suppressed.
   - User reading streaks, total minutes read, and chapter completion counters in `ReadingStats` are left unmodified.
4. **Isolation from Library Progress Updates**:
   - Manhwa chapter detection on reading pages is disabled during private tabs to prevent unapproved changes to library reading status.
5. **No Tab Restoration of Private Tabs**:
   - Closed private tabs are never added to `recentlyClosedTabs`. Tab restoration is strictly restricted to non-private tabs.
6. **Diagnostic & Telemetry Log Sanitization**:
   - Private URLs and titles are replaced with `[PRIVATE_SESSION_REDACTED]` in any diagnostic output.
7. **Session Cleanup & Ephemeral Storage Purging**:
   - When all private tabs close or "Close All & Wipe Ephemeral Data" is selected:
     - Private `WebView` instances are released and destroyed.
     - `CookieManager.removeSessionCookies(null)` and `flush()` are executed.
     - `WebStorage.getInstance().deleteAllData()` purges HTML5 LocalStorage, WebSQL, and IndexedDB data.
     - Ephemeral memory caches are flushed with `clearCache(true)`.

### Android WebView Technical Limitations on Private Sessions
1. **Single-Process Global Cookie Storage**:
   - On Android devices without multi-profile WebView support (`WebViewProvider` profile isolation API level dependent), persistent cookies can potentially share the global `CookieManager` if not explicitly flushed. MangaFlow mitigates this by enforcing `WebSettings.LOAD_NO_CACHE`, actively wiping session cookies on tab closure, and preventing disk writes.
2. **Network Anonymity Limitation (User Transparency)**:
   - Private browsing is an **on-device isolation feature**. It does **not** provide network-layer anonymity, VPN routing, or Tor onion encryption. Visited websites, network administrators (e.g. corporate or educational Wi-Fi), and internet service providers (ISPs) can still observe network packets and domain endpoints.
