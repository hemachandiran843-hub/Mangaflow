# MangaFlow - Optional Backend Monitoring Architecture & Setup

## 1. Overview & Local-First Guarantee

MangaFlow is designed **local-first**:
- All chapter monitoring, update detection, deduplication, and notification logic execute natively on the Android device using `ChapterDetectionService` and `WorkManager`.
- **No external server, paid cloud account, or backend database is required** for the application to function completely.

This document describes the **optional backend monitoring integration**, which power users or future cloud infrastructure can deploy to offload scraping tasks, save device battery, and receive instant push notifications via Firebase Cloud Messaging (FCM).

---

## 2. Architecture & Data Flow

```
+-------------------------------------------------------------+
|                     MangaFlow Android App                   |
|                                                             |
|  [ChapterDetectionService] <--- (Fallback Local Scraper)    |
|             |                                               |
|             v                                               |
|  [CloudMonitoringClient]                                    |
+-------------+-----------------------------------------------+
              | HTTPS (REST / Webhooks)
              v
+-------------------------------------------------------------+
|                 Optional Cloud Backend                      |
|                                                             |
|  - Serverless Worker (Cloud Functions / AWS Lambda / Docker)|
|  - Headless Cron Scraper (Node.js / Python / Go)            |
|  - Redis / Postgres Series Registry                         |
|  - Firebase Cloud Messaging (FCM) Push Gateway              |
+-------------------------------------------------------------+
```

---

## 3. API Contract & OpenAPI Specification

The backend scraper exposes three lightweight endpoints:

### `POST /api/v1/monitor/register`
Registers a tracked manhwa series with the cloud monitoring cluster.

**Request:**
```json
{
  "client_device_token": "fcm_token_xyz_123",
  "local_series_id": 42,
  "title": "Solo Leveling: Ragnarok",
  "source_url": "https://api.mangadex.org/manga/32d76d19-8a05-4db0-9fc2-e0b0648fe9d0",
  "source_type": "mangadex",
  "check_frequency_hours": 6
}
```

**Response (200 OK):**
```json
{
  "status": "success",
  "cloud_series_id": "cloud_reg_98765",
  "next_scheduled_check": 1729000000000
}
```

---

### `DELETE /api/v1/monitor/{cloud_series_id}`
Stops server-side monitoring for a series.

**Response (200 OK):**
```json
{
  "status": "unregistered",
  "cloud_series_id": "cloud_reg_98765"
}
```

---

### `POST /api/v1/notifications/push` (Server to Device)
Triggered when the cloud scraper detects a new chapter.

**Payload:**
```json
{
  "to": "fcm_token_xyz_123",
  "data": {
    "type": "NEW_CHAPTER",
    "series_id": "42",
    "series_title": "Solo Leveling: Ragnarok",
    "chapter_identifier": "15",
    "chapter_title": "Chapter 15 - Shadow Extraction",
    "chapter_url": "https://mangadex.org/chapter/abc-def",
    "detected_at": 1729000500000
  }
}
```

---

## 4. Example Serverless Scraper Implementation (Node.js)

Below is an example reference implementation for deploying on Google Cloud Functions or AWS Lambda:

```javascript
// index.js - Serverless chapter check task
const axios = require('axios');
const admin = require('firebase-admin');
admin.initializeApp();

exports.checkMangaDexUpdates = async (req, res) => {
  const { seriesList } = req.body;
  const updates = [];

  for (const series of seriesList) {
    try {
      const resp = await axios.get(
        `https://api.mangadex.org/manga/${series.mangaId}/feed?translatedLanguage[]=en&order[chapter]=desc&limit=1`,
        { headers: { 'User-Agent': 'MangaFlow-CloudScraper/1.0' } }
      );

      const latest = resp.data.data[0];
      if (latest && latest.attributes.chapter !== series.lastDetectedChapter) {
        updates.push({
          seriesId: series.localId,
          newChapter: latest.attributes.chapter,
          title: latest.attributes.title
        });
      }
    } catch (err) {
      console.error(`Failed to check ${series.title}:`, err.message);
    }
  }

  res.status(200).json({ checked: seriesList.length, updates });
};
```

---

## 5. Deployment & Configuration

1. **Host Selection:** Deploy the container or function on any provider (Cloud Run, Fly.io, AWS Lambda, or a local Raspberry Pi).
2. **Environment Variable in MangaFlow:**
   - In MangaFlow's Settings, configure the custom backend endpoint URL:
     `APP_SETTING: CLOUD_MONITOR_ENDPOINT = https://your-scraper-host.com/api/v1`
3. **No Lock-In:** Disabling the setting immediately returns MangaFlow to standard device-side polling using Android `WorkManager`.
