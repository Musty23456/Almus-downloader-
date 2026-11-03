# Changelog

## Unreleased – Segmented download engine

### Added
- Multi-connection downloads in `com.almus.downloader.engine` (see `docs/SEGMENTED_DOWNLOADS.md`): `SegmentedDownloader`, `SegmentPlanner`, `RangeValidator`, `SegmentManifest`, `SegmentMerger`, `AdaptiveConnections` + `ConnectionPolicy`, `TempFiles`.
- Used only when the server proves it supports byte ranges (`SegmentationPolicy`): plain 200 with a known size of at least 4 MiB, explicit `Accept-Ranges: bytes`, no content coding, a strong ETag or Last-Modified for `If-Range`. Every ranged answer is validated header by header before a byte is written; anything doubtful falls back to the unchanged single-connection path.
- Integrity: per-segment CRC-32 (computed while writing, recorded at checkpoints, verified again during the merge); a damaged or missing segment is thrown away and fetched again on its own; a changed file restarts the download instead of mixing versions.
- Safe merge: segment 0 is promoted by rename, the others appended in order with a journal in the manifest; atomic rename to the final partial file; at most one extra segment of disk space.
- Network-aware, adaptive connection count (2 to 8, fewer on mobile data, slow links and when several downloads share the link; backs off on 429/503).
- Automatic cleanup of every temporary file (segments, manifest, merge file, partial file) on completion, failure, cancel and at start-up.
- `EngineSettings.segmentedDownloads` and `maxConnectionsPerDownload` (defaulted; no UI yet), `NetworkStatus.kind/downstreamKbps`, `EngineItem.connections`, `TransportRequest.ownConnection`.
- Tests: 99 new tests (planner, validator, manifest, adaptive logic, policy, temp files, merger, and an end-to-end suite against a scriptable server). Benchmark: `app/src/test/.../engine/bench`, results in `docs/BENCHMARKS.md`. JVM test harness: `tools/jvm-harness`.

### Changed
- `OkHttpTransport`: dispatcher allows 16 simultaneous requests per host (OkHttp's default of 5 would queue segments); segment requests use HTTP/1.1 so they get separate TCP connections.
- `HttpHeaders.parseUnsatisfiedTotal` rewritten with a block body (same behaviour; the `return` inside an expression body is rejected by the Kotlin 2.0.21 compiler used for the tests here).
- Two existing engine tests wait for the finished-callback instead of racing it.

### Fixed (CI)
- `TikTokSourceGuardTest.sourcesWereFound` expected at least 12 source files; the TikTok module has 11 (the number was copied from the YouTube guard). It now expects 11.
- `SegmentedEngineTest.mobileDataUsesFewerConnectionsThanEthernet` counted the server's open requests, which include the first request that is cancelled once the segments start, so 3 segments could read as 4. It now checks the engine's own connection count (at most 3), the server peak with the same one-extra allowance as `neverReportsMoreConnectionsThanAllowed`, and that the cellular limit is below the Ethernet limit.
- Test changes only; no engine or app code changed.

### Not changed
- Room schema (segment progress lives in a manifest file next to the segments), permissions, dependencies, UI, the DownloadManager-based engine.

### Known limitations
- The Android module was not built here (no Android SDK); the engine and its tests were compiled and run on a JVM. The only Android-layer edit is `AndroidNetworkMonitor` (transport kind and bandwidth hint).
- Without a hash from the server, integrity means exact size, same-version validators and local CRC-32; wrong bytes of the right length sent by the server itself cannot be detected.

## 0.14.0 – Advanced media selection

### Added
- Package `com.almus.downloader.media` (see `docs/MEDIA_SELECTION.md`): real-format catalogs from authorized sources (Internet Archive metadata, Wikimedia Commons `videoinfo` derivatives, single direct files); quality ladder 144p–2160p used for labels only (a rung shows only when a real file has that height; other heights show as they are); exact vs estimated size; duration; format details; audio choices limited to what the source provides; destination choices; download plan.
- UI: `MediaSelectionScreens.kt` (selection screen, confirmation screen, busy dialogs) wired into Home and the browser through `MediaFlowHost`. New add-states `Resolving`, `Select`, `Verifying`, `Review`.
- Per-download folder (Automatic / Main / Video or Audio folder) with the new download engine.
- `AudioConverter` seam for future MP3/M4A conversion (none ships).
- Tests: `MediaOptionsTest`, `MediaSourcesTest`, `MediaSourceGuardTest`.

### Changed
- Direct audio/video file links now go through the selection and confirmation screens (one real option) instead of the plain confirm dialog; other files keep the plain dialog.
- Version 0.14.0 (versionCode 14).

### Not changed
- Engines, platform adapters and integrations, browser, file manager, permissions, dependencies, database.

### Known limitations
- Not compiled and tests not run (no Kotlin compiler, Android SDK or network in the authoring environment).
- No MP3/M4A conversion: Android has no MP3 encoder and none could be added. No stream/manifest downloads.

## 0.13.0 – Instagram, X, Vimeo, Dailymotion, Reddit, Pinterest (official services only)

### Added
- Package `com.almus.downloader.integration` (see `docs/PLATFORM_INTEGRATIONS.md`): one shared, spec-driven module instead of six copies: `PlatformSpec` with `specs/{Instagram,X,Vimeo,Dailymotion,Reddit,Pinterest}Spec`, `PlatformLinks` (id validation, canonical and openable addresses, trusted thumbnails and creator links), `OEmbedClient` + `OEmbedParser` (each platform's public oEmbed; no credentials), `DownloadPolicy` (provider seam, none shipped: none of the six has a download API), `AccessOptions` (open in app / on the website / share), per-platform own-content guides, media identification, `PlatformIntegration`.
- Instagram uses Meta's oEmbed, which needs no token since 15 June 2026; it returns no title, author or thumbnail, so only public status is shown. X's post text is read as plain text from the embed markup.
- UI: `PlatformContentDialog`, `PlatformViewModel`; `LinkDetectionDialog` routes content to it and shows *Open in …* buttons for profiles and boards. Manifest `<queries>` for the six apps; `pf_*` strings.
- Tests: `PlatformLinksTest`, `OEmbedClientTest`, `OEmbedParserTest`, `PolicyAndOptionsTest`, `OkHttpOEmbedHttpTest`, `IntegrationSourceGuardTest`.

### Changed
- Version 0.13.0 (versionCode 13).

### Not changed
- The six recognition adapters, `MediaPlatformAdapter`, detection, download engines, browser, file manager, permissions, dependencies, the YouTube, Facebook and TikTok modules.

### Known limitations
- Not compiled and tests not run (no Kotlin compiler, Android SDK or network in the authoring environment); no platform service was contacted. The first CI run is the real check.
- No downloading from these platforms, by design.

## 0.12.0 – TikTok integration (official services only)

### Added
- Package `com.almus.downloader.tiktok` (see `docs/TIKTOK_INTEGRATION.md`): `TikTokLinks` (strict id and creator-name validation, clean canonical and openable addresses, trusted thumbnail hosts), `TikTokMetadataClient` (TikTok's public oEmbed: caption, creator, thumbnail; no credentials), `TikTokResponseParser`, `TikTokDownloadPolicy` (provider seam, none shipped: TikTok has no download API), `TikTokAccessOptions` (open in app / on tiktok.com / share), `TikTokExport` (own data export and Save video), `TikTokIntegration`.
- UI: `TikTokVideoDialog` (details, thumbnail when available, open in TikTok, why saving is unavailable, own-content steps); *Open in TikTok* buttons for TikTok photo posts, live pages and profiles. Manifest `<queries>` for the two TikTok app packages.
- Tests: `TikTokAdapterTest`, `TikTokLinksTest`, `TikTokMetadataClientTest`, `TikTokPolicyAndOptionsTest`, `TikTokLinkIntakeTest`, `OkHttpTikTokHttpTest`, `TikTokSourceGuardTest`, plus 27 new vectors in `link_vectors.tsv`.

### Changed
- `TikTokAdapter`: also recognises `/player/v1/ID` and legacy `/v/ID.html`; documented.
- `MediaPlatformAdapter` gained `pageStatusIsReliable` (default `true`); `SmartLinkDetector` reads a page status only when it is true. `TikTokAdapter` sets it to `false` so a bot-protection 403 is not shown as "private"; TikTok's oEmbed decides. Other platforms unchanged.
- `LinkDetectionDialog` routes plain TikTok videos to the new dialog. Version 0.12.0 (versionCode 12).

### Not changed
- Download engines, browser, file manager, permissions, dependencies, other platform modules, Settings (TikTok needs no key or token).

### Known limitations
- Not compiled and tests not run (no Kotlin compiler, Android SDK or network in the authoring environment); the live TikTok service was not contacted. The first CI run is the real check. The new source guard's patterns were run against the module with a script.
- TikTok does not document oEmbed error answers; the mapping is defensive.
- No TikTok downloading, by design.

## 0.11.0 – Facebook integration (official services only)

### Added
- Package `com.almus.downloader.facebook` (see `docs/FACEBOOK_INTEGRATION.md`): `FacebookLinks`, `FacebookMetadataClient` (public oEmbed; Graph API with the person's own token), `FacebookResponseParser`, `FacebookDownloadPolicy` (only the token owner's own video can be offered as a file), `FacebookAccessOptions`, `FacebookExportGuide` (official export for own videos and Pages), `FacebookIntegration`.
- UI: `FacebookContentDialog` (details, thumbnail when available, open in Facebook, why saving is unavailable, export steps); *Open in Facebook* buttons for restricted pages (groups, stories, posts); Settings → Facebook token (masked).
- Tests: `FacebookAdapterTest`, `FacebookLinksTest`, `FacebookMetadataClientTest`, `FacebookPolicyAndOptionsTest`, `FacebookSourceGuardTest`, plus 8 new vectors in `link_vectors.tsv`.

### Changed
- `FacebookAdapter`: Reels (`/reels/{id}`), live videos (`/watch/live`), link-shim and tool pages no longer mistaken for profiles; Reels titled "Facebook Reel".
- `LinkDetectionDialog` routes plain Facebook videos to the new dialog. `AlmusTextField` gained `secret`. Manifest `<queries>` for the Facebook app. Version 0.11.0 (versionCode 11).

### Not changed
- Download engines, browser, file manager, permissions, dependencies, YouTube module.

### Known limitations
- Meta's public oEmbed returns no title or thumbnail; those need the person's own token.
- Android UI layer not compiled here (no SDK); the core and its tests were. First CI run is the real check.

## 0.10.0 – YouTube integration (official services only)

### Added
- Package `com.almus.downloader.youtube` (see `docs/YOUTUBE_INTEGRATION.md`):
  - `YouTubeLinks`: recognises and validates YouTube video, Shorts, live, embed and `youtu.be` addresses (11-character id check); rejects playlists, channels, look-alike hosts and `googlevideo.com` stream addresses.
  - `YouTubeMetadataClient`: public details from YouTube's official services: oEmbed (title, channel, thumbnail; no key) and the YouTube Data API v3 `videos.list` (adds duration, licence, embedding, age and region flags) when the person saved their own API key. Falls back from the Data API to oEmbed with a notice when the key is rejected, over quota or unreadable.
  - `YouTubeViewingOptions`: open in the YouTube app, open on youtube.com, share the link; plus whether the owner allows embedding.
  - `YouTubeDownloadPolicy`: explains why downloading is unavailable (no public download API, live, age-restricted, regional limits, Creative Commons note) and what to do instead; an `AuthorizedDownloadProvider` seam for a future official mechanism or an explicit owner offer (none shipped; offers pointing at YouTube hosts or unsafe addresses are dropped).
  - Graceful handling of deleted, private, age-restricted, region-restricted, live/upcoming, rate-limited and offline cases.
  - `MiniJson` and `IsoDuration` (small, strict, tested; no new dependency).
- UI: `YouTubeVideoDialog` (thumbnail, title, channel, duration, licence, notes, viewing buttons, download explanation, Try again); Settings → YouTube section to save or remove the optional API key (only the last four characters are ever shown).
- 42 new tests (574 in total), including `YouTubeSourceGuardTest`, which fails the build if the module ever gains stream/player/signature handling, cookies or sign-in code, hosts other than `www.googleapis.com` and `www.youtube.com`, extra request headers, or file writing.

### Changed
- `LinkDetectionDialog` sends plain YouTube video addresses to the new dialog (one `if`); everything else keeps the old explanation dialog. It gained an optional `onUseLink` callback.
- `DownloadsViewModel` gained `youtubeKeyHint`, `saveYoutubeKey`, `clearYoutubeKey`; `AppContainer` gained `youtubeKeys` and `youtube`.
- Version 0.10.0 (versionCode 10).

### Not changed
- Link detection and the YouTube adapter, both download engines, browser, file manager, permissions (no new ones), dependencies (none added).

### Known limitations
- No YouTube downloading, by design.
- Duration and age/region flags need the person's own Data API key; oEmbed cannot distinguish private, age-restricted and embedding-disabled videos.
- No in-app player; region restriction is a flag only; English text only.
- Not compiled and tests not run (no SDK or network here); the live YouTube services were not contacted. The first CI run is the real check.

## 0.9.0 – Download engine

### Added
- Package `com.almus.downloader.engine`: a streaming download engine on OkHttp with real pause and resume, HTTP Range resume checked against the server's answer (206 / 200 / 416, `If-Range` with the saved ETag or date), servers without partial-download support handled, final size validation, automatic retry with growing delays (`Retry-After` honoured, capped, no endless loops), waiting for network / Wi-Fi, queue with a configurable number of simultaneous downloads, duplicate detection, speed and time remaining, cancellation, safe redirects (`HttpSession`), recovery after a restart.
- Android layer: Room table `engine_downloads` with a real migration 1 to 2 (schema export on), MediaStore saving into Download/ALMUS, network monitor, foreground service (dataSync), progress notifications with Pause / Resume / Cancel, WorkManager resume worker, `EngineHost`.
- Settings: **New download engine (beta)** switch (off by default, Android 10+), **Download on Wi-Fi only** now works for the new engine.
- Downloads list: Pause button for new-engine downloads; their items use negative ids.
- 80 new tests (532 in total), including 41 integration tests with real HTTP against a local server.
- Dependencies: OkHttp 4.12.0, WorkManager 2.9.1. Permissions: FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, ACCESS_NETWORK_STATE.

### Changed
- `NoTranscodingGuardTest`: OkHttp group and the `If-Range` header allowed (deliberately); everything else is still forbidden.
- Room database version 2 (`exportSchema = true`, schema written to `app/schemas`).
- Version 0.9.0 (versionCode 9). CI permission check includes the three new permissions.

### Not changed
- The DownloadManager-based engine and its repository, link detection, browser, file manager, app name. The new engine is off by default.

### Known limitations
- Not compiled and tests not run (no SDK or network here); the first CI run is the real check.
- Android 10+ only. Completed new-engine downloads are not listed in the Files tab yet.
- Migration, service, notifications, MediaStore saving and the resume worker are untested on a device.
- No segmented downloads, speed limit or scheduling. Dependencies could not be scanned for vulnerabilities.

## 0.8.0 – Smart Link Detection

### Added
- New package `com.almus.downloader.platform`: `MediaPlatformAdapter` interface and independent adapters for YouTube, Facebook, TikTok, Instagram, X/Twitter, Vimeo, Dailymotion, Reddit, Pinterest, plus `DirectLinkAdapter` (fallback).
- `SmartLinkDetector`: validates links with the existing `UrlValidator`, identifies platform / kind / id, direct files, pages that are not files, restricted links, blocked and invalid links; adds safety notes (plain http, local network, internationalized names, look-alike addresses).
- `SafeRedirectResolver`: safe, bounded redirect following for short links (no body reads, no cookies, no https-to-http, no public-to-local).
- `LinkIntake`: the testable decision flow used by the download screen.
- UI: live hint under the address field; explanation dialog (`LinkDetectionDialog`) for platform pages and restricted links; "Source:" line in the download confirmation; "Check as a normal link" button.
- 78 new tests and a shared vector file (`src/test/resources/link_vectors.tsv`, 128 links).

### Changed
- `DownloadsViewModel.checkLink` now runs through `LinkIntake`. `AddDownloadState` gained `Detected` and `Confirm.detection` (optional). Duplicate message and server check are unchanged.
- Version 0.8.0 (versionCode 8); inspector User-Agent now reports 0.8.
- Pasting a platform page link now shows an explanation first (old behaviour is one tap away).

### Not changed
- Download engine (still Android `DownloadManager`), browser, file manager, permissions, dependencies, UI layout, app name.

### Known limitations
- Not compiled and tests not run by the author (no SDK/network available); first CI run is the real check.
- Address-pattern recognition only; English messages in Kotlin; unlisted Pinterest country domains are treated as ordinary links.
- Nothing downloads from platform pages, by design.
