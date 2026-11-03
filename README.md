# ALMUS DOWNLOADER ULTIMATE

An Android download manager, built in phases.

- **Phase 1** – project foundation, navigation, CI build.
- **Phase 2** – polished interface.
- **Phase 3** – real direct-link download engine for publicly accessible files.
- **Phase 4** – advanced download management: queue, concurrency, hold/resume of queued items, bulk actions, speed and ETA, safe delete.
- **Phase 5** – local file manager for the files this app downloaded.
- **Phase 6** – in-app browser (Android WebView) with safe defaults, routed into the existing download flow.
- **Advanced media selection (this version, 0.14.0)** – audio and video from authorized sources (Internet Archive, Wikimedia Commons, direct files) open a selection screen with the formats the source really offers (quality, format, size or labelled estimate, length, details), a destination folder and a confirmation screen. Nothing is invented; no conversion ships. See [docs/MEDIA_SELECTION.md](docs/MEDIA_SELECTION.md).
- **Platform integrations (0.13.0)** – Instagram, X/Twitter, Vimeo, Dailymotion, Reddit and Pinterest content links open one shared dialog with what each platform's own public oEmbed service shares (title, creator, length, thumbnail where given), what kind of media it is, the official ways to open it in the platform, why downloading is not available, and the platform's own tools for your own content. One spec per platform on the existing adapter architecture; **no downloading, no sign-in, no scraping**: see [docs/PLATFORM_INTEGRATIONS.md](docs/PLATFORM_INTEGRATIONS.md).
- **TikTok integration (0.12.0)** – TikTok video links (including short links, which are resolved safely) open a dialog with the caption, creator and thumbnail from TikTok's own public oEmbed service, ways to open the video in TikTok, why downloading is not available, and TikTok's own tools for your own videos. **No TikTok downloading**: see [docs/TIKTOK_INTEGRATION.md](docs/TIKTOK_INTEGRATION.md).
- **Facebook integration (0.11.0)** – Facebook video, Reel and live links open a dialog with what Facebook's official services return (public status and size; title, owner and thumbnail with your own token), ways to open them in Facebook, and the official export for your own videos. Restricted content (groups, stories) can be opened in Facebook but is never bypassed. See [docs/FACEBOOK_INTEGRATION.md](docs/FACEBOOK_INTEGRATION.md).
- **YouTube integration (0.10.0)** – YouTube video links open a dialog with the public title, thumbnail, channel and duration from YouTube's own services (oEmbed, and the Data API with the person's own key), the official ways to watch (YouTube app, youtube.com, share), and a clear explanation of why downloading is not available. **No YouTube downloading**: see [docs/YOUTUBE_INTEGRATION.md](docs/YOUTUBE_INTEGRATION.md).
- **Download engine (0.9.0)** – new streaming engine with real pause/resume, Range-checked resume, automatic retry, restart recovery, notifications and a queue. Off by default (Settings), Android 10+; the DownloadManager engine is unchanged.
- **Smart Link Detection (0.8.0)** – recognises YouTube, Facebook, TikTok, Instagram, X/Twitter, Vimeo, Dailymotion, Reddit, Pinterest and direct links before anything is downloaded; explains pages that are not files; follows short links safely. Detection only: no extraction from platform pages.
- **Phase 7 (0.7.0)** – audio and video downloads for ordinary files that servers offer for download: link inspection, streaming/live refusal with explanations, folder choice, "Save a copy to…".

The **AI Assistant** is still a layout preview. It is unchanged.

## What the download engine does

1. You paste an `http://` or `https://` link on **Home** and tap **Start download**.
2. The app validates the link, then checks it (HEAD request, or a one-byte ranged GET if the server refuses HEAD). Redirects are followed by hand, up to 5, and every hop is validated. A redirect from https to http is blocked.
3. A **confirmation dialog** shows file name, host, size and type, plus warnings (unencrypted http, web page instead of file, unknown size, very large file, APK). **Nothing downloads until you tap Download.**
4. Android's **DownloadManager** performs the transfer, so it continues in the background and survives the app being closed.
5. Progress, bytes, total (when the server announces it) and final status are stored in a **Room** database and shown on Home, Downloads and Files. On every app start, and while the app is visible, records are re-synchronised with the system, so status is restored after a restart.
6. When a download finishes or fails, the app posts a **notification** (also when the app is closed). Tapping it opens the Downloads screen. It never opens or runs the file.

### File names
From `Content-Disposition` (`filename*` first), else the URL's last path segment, else `download`. The result is sanitised: directories are stripped (no path traversal), control and bidi-override characters and `< > : " | ? * / \` are removed, leading/trailing dots and spaces trimmed, length limited to 200 bytes, an extension is added from a specific MIME type if missing, and duplicates become `name (1).ext`.

### Storage and permissions
| Permission | When | Why |
|---|---|---|
| `INTERNET` | install | link check before queuing |
| `POST_NOTIFICATIONS` | Android 13+, asked when you approve a download | finished/failed notifications. Declining never blocks a download |
| `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=28`) | Android 9 and older only, asked when you approve a download | public Downloads folder. If declined, files go to the app's own folder |

Android 10+ uses scoped storage: files are written to `Download/ALMUS/` by DownloadManager with no storage permission. No other permissions are requested.

### Security behaviour
- Only the link you enter and approve is downloaded. No authentication, login, DRM or access-control bypass: 401/403 are reported as "access denied".
- TLS validation is the Android default (system CAs only, no custom trust manager, no hostname-verifier override).
- Plain `http://` is allowed (requirement) but flagged in the dialog and on the card.
- Links with embedded `user:password@` are rejected.
- Downloaded files are never executed or installed. APKs have no "Open" button; the button reads **Show in Downloads**, which opens the system Downloads app.
- Unknown-size downloads require explicit approval in the dialog and can be cancelled any time. Known sizes are checked against free space before queuing.
- `allowBackup` is off, so download records are not restored onto another device.

## Download management (Phase 4)

### Architecture decision: why not a custom download engine?
Android's `DownloadManager` has **no public pause or resume**. (It has hidden `pauseDownload`/`resumeDownload` methods that apps cannot call, and calling `remove()` deletes the partial file.) Two options were evaluated:

| | Keep `DownloadManager` + app-side queue (chosen) | Custom streaming engine (HTTP Range + foreground service) |
|---|---|---|
| True pause/resume of a running transfer | **No** | Yes, only for servers that support `Range` and a stable `ETag`/`Last-Modified` |
| Survives the app being closed or killed | Yes, the system owns the transfer | Only while a foreground service is alive; needs a persistent notification |
| Network loss, roaming, retry/backoff | Handled by the system | Must be written and tested by us |
| Phone restart | System resumes its own downloads after boot | Cannot start a `dataSync` foreground service from a boot receiver on Android 15+; the user has to open the app |
| Background limits | None to manage | Foreground service type + permission (Android 14), a 6-hour limit per `dataSync` service (Android 15), battery restrictions, notification permission |
| Scoped storage | DownloadManager writes to `Download/ALMUS` without any permission | Must write via MediaStore (Android 10+), keeping an "pending" file that is appended on resume; separate legacy path for Android 9 and older |
| Amount of new code and risk | Small | Large (a download engine, a service, resume validation, storage layer) |

Decision: **keep DownloadManager** and build the queue on top of it. A custom engine would be a separate phase with its own testing on real devices; it is the only way to get real pause/resume of running transfers, at the price of the limits above.

### What is implemented
- **Queue with configurable concurrency** (Settings, 1 to 5, default 3). At most that many downloads are handed to the system; the rest wait in the app queue (state *In queue*), oldest first. Changing the setting takes effect at once. Lowering it never cancels a running download.
- **Hold / Resume** only for downloads *still waiting in the queue*. They have not started, so holding them is real. A held download does not start and does not use a slot; resuming puts it back in its original place. Running transfers have **no pause button**, because the underlying mechanism cannot do it.
- **System-paused downloads** (waiting for network or Wi-Fi) are shown as *Paused* with the reason. The system resumes them by itself, using HTTP Range when the server allows it; otherwise it restarts or reports "could not be resumed" and Retry starts again from zero.
- **Cancel** (single or bulk) stops the transfer and deletes partial data. **Retry** restarts from the beginning, reuses the record and goes to the end of the queue.
- **Remove from list** (completed, failed, cancelled) only deletes the record. It never touches the file or the system entry. It is refused for unfinished downloads.
- **Delete file** (completed only) is a separate button, always behind a confirmation dialog. It asks Android to remove the download, which deletes the file. If Android no longer lists the file, the record is kept and a message explains how to delete it in the Files app.
- **Progress, speed, sizes, ETA**: downloaded size and total (when announced), percentage, speed, and "about N min left". Speed needs about 2 seconds of samples; the ETA needs at least 5 samples over 5 seconds, 32 KB of progress, a known total, and no stall. A stalled, paused or finished download shows neither. ETAs over 48 hours are hidden.
- **Duplicate prevention**: the same link (case of scheme/host, default port and `#fragment` ignored) cannot be added while it is waiting, held, starting, running or paused. It is checked before the link check (no network use) and again when queuing. After a download finished, failed or was cancelled, the same link may be added again; completed files are never overwritten (`name (1).ext`). Retry of a failed item is skipped if the link is already queued.
- **Filters**: All, Active (waiting, starting, running), Paused (system-paused and held), Completed, Failed (failed and cancelled). Each state appears in exactly one specific tab.
- **Bulk actions** (Actions menu, always with a confirmation that shows the count): Cancel all, Hold all waiting, Resume all held, Retry all, Remove from list, Delete files (only offered on the Completed tab). Each action only touches items in the current tab that the state table allows.
- **Restarts and crashes**: records are in Room. On every app start, after the phone restarts (boot receiver) and whenever a download finishes, the app reconciles with the system, fills free queue slots and announces finished/failed downloads once. Before handing a download to the system the record is marked, so a crash in between is detected, the record is marked failed (Retry is offered) and the stray system download is removed. System downloads that no record refers to and that are unfinished are removed; finished ones are never touched.
- **Network interruptions**: handled by DownloadManager (automatic retry, waiting for network/Wi-Fi, resume where the server supports it). The UI shows the system's reason.

### Extra permission
`RECEIVE_BOOT_COMPLETED` (normal permission, granted at install) so queued downloads can continue after a restart. Android only sends the boot broadcast after the app has been opened once and not after a force-stop.

### Limits of Phase 4
- No pause/resume of a *running* transfer (see decision above).
- Speed and ETA are only updated while the app is visible (they come from progress polling); notifications and queue advancement also work in the background.
- DownloadManager runs at most 5 transfers at once itself; the setting range follows that.
- A completed file whose system entry was removed in the Downloads app cannot be deleted from this app.
- The queue keeps its order by time added; there is no drag-to-reorder.
- Wi-Fi-only, download location and notification toggles in Settings are still "Coming soon".

## File manager (Phase 5)

The **Files** tab lists the files this app downloaded and lets you search, sort, open, share and delete them.

### Where the files come from
- **Room records are the source of truth.** Every *completed* download is a row; the file manager checks the real file behind each row. It reaches the file through the `content://` uri that `DownloadManager` provides, so **no storage permission is needed** (Android 10+ scoped storage; on Android 9 and older the Phase 3 fallback still applies).
- **Optional folder access (Storage Access Framework).** The app cannot list `Download/ALMUS` for files it has no record of (for example after a reinstall). If you want that, tap *Choose a folder* and pick it in the system picker. The app then gets access to **that one folder only** (read, and write only so that you can delete there), keeps it as a persisted permission, and shows its files marked *Not in download history*. A file with the same name as an available download is not listed twice. *Stop using folder* gives the permission back. Subfolders are not scanned.
- **No broad access.** The app does not request `READ_EXTERNAL_STORAGE`, `READ_MEDIA_*` or `MANAGE_EXTERNAL_STORAGE`. The CI build fails if any of them (or `REQUEST_INSTALL_PACKAGES`) appears in the APK.

### What you can do
- **Categories:** Videos, Music, Images, Documents, APKs, Archives, Other, with counts. Chips filter the list; Home category tiles open the Files tab on that category. The file name extension is trusted first (servers often send `application/zip` for a `.docx`), then the MIME type.
- **Search** by file name or MIME type; **sort** by name (natural order: `file2` before `file10`), date, size or type, ascending or descending. Unknown sizes and dates always sort last.
- **Each row shows** file name, size, modification date and MIME type (when known), and a badge for problems.
- **Open** uses `ACTION_VIEW` with a temporary read grant. **APKs are never opened or installed by this app**: the action is *Show in Downloads* (system screen) and nothing is launched automatically. **Share** uses the system chooser with a `content://` uri and a temporary read grant (never `file://`).
- **Delete** always asks first and names the file. For downloads it asks Android to remove the download (which deletes the file); for files in the chosen folder it deletes that document through the permission you gave. If the file cannot be deleted the app says so; nothing is deleted silently.
- **Remove from list** removes only the record, never the file. *Remove missing from list* (menu) clears the records of files that vanished, after a confirmation.
- **Refresh** button, and an automatic re-check every time the screen is shown and whenever the set of completed downloads changes.

### Files moved or deleted outside the app
Each scan re-checks every file. A vanished file stays in the list with a **File missing** badge (using the size and date recorded at download time); only *Remove from list* is offered. Before opening, sharing or deleting, the app checks that file again; if it is gone it says so and refreshes the list.

### States
Loading (first scan), refreshing (progress bar over the existing list), empty library, *no matching files* (search/filter), **permission denied** (every file inaccessible, or the chosen folder's permission was removed, with a button to choose the folder again), folder missing or unreadable, and a scan error with *Retry*.

### Limits of Phase 5
- Only files recorded as completed downloads, plus the one optional folder (not recursive). It is not a general file manager and cannot browse other folders.
- The modification date is the file's own date when Android lets the app read it; otherwise the time the download completed.
- Opening a file depends on an installed app that handles its type.
- Deleting a download's file needs Android's download entry; if the user removed it in the system Downloads app, the record stays and a message explains how to delete the file in the Files app.
- No multi-select or bulk file operations in the file manager (bulk actions exist on the Downloads tab).

## In-app browser (Phase 6)

The **Browser** tab is a basic web browser built on Android's `WebView`. It adds no new permission (`INTERNET` was already there) and changes nothing in the download engine, queue or file manager.

### What it does
| Requirement | Behaviour |
|---|---|
| Address bar, http and https | Accepts `http://` and `https://`. An address typed without a scheme (`example.com/page`) gets `https://`. **There is no web search**: text that is not an address (`hello world`) is rejected with a message, so nothing typed leaves the phone by accident. `javascript:`, `data:`, `file:`, `content:`, `intent:`, `mailto:`, `ftp:` … are rejected. |
| Loading indicator | Progress bar under the address bar while a page loads. |
| Back / forward / reload / stop | Back and forward are enabled only when history allows. The third button is **Stop** while loading and **Reload** otherwise (the usual single slot). The system Back button goes back in the page first. |
| Navigation and load errors | A page that cannot load shows an error panel (site not found, could not connect, timed out, too many redirects, blocked by Safe Browsing …) with **Try again**. A page that loads but answers 4xx/5xx gets a short notice above it. If the web engine crashes, it is rebuilt and the panel offers to reload. |
| External links | Menu **Open in system browser** opens the current page in the phone's browser. `mailto:`, `tel:` and `sms:` links open the matching app **only when you tapped them**. Every other non-web link (`intent:`, `market:`, custom schemes, `javascript:`, `file:`, `blob:` …) is **blocked** with a notice; a page cannot launch other apps. Links with `user:password@host` are blocked (a classic disguise). |
| Downloads | See below. |
| Cookies and sessions | Normal first-party cookies, so sites that keep you signed in work. **Third-party cookies are refused.** The app never reads a cookie value (`getCookie` is not called anywhere; a test enforces it) and never adds a `Cookie` header to anything. Menu **Clear browsing data** removes cookies, site storage, cache and page history. |
| SSL errors | **Never bypassed.** `onReceivedSslError` always calls `handler.cancel()`; there is no `proceed()` call, no "continue anyway" button and no setting for one. The error panel explains the reason (expired, wrong site, untrusted, not yet valid …). TLS trust is the Android default (system CAs only). |
| JavaScript | **Off by default.** Menu **Turn JavaScript on for this site** enables it for that one host only (exact host: allowing `example.com` does not allow `ads.example.com`) and reloads the page. The choice is remembered per site, shown under the address bar ("JavaScript on/off"), can be turned off again, and is reset by *Clear browsing data*. The setting is decided before each page starts loading, also for redirects, Back and Forward. |
| No unsafe bridges | The app calls `addJavascriptInterface` **nowhere**. Pages get no object that reaches this app. |
| Privacy notice | Shown **before the first page loads** (you must tap *I understand*), always available in the menu (**Privacy notice**) and summarised on the start page. It says that websites may use cookies and collect their own data and that the app does not see it. |

Other hardening: no `file://` or `content://` access from pages, mixed content never allowed, Safe Browsing on, no geolocation, camera or microphone (permission requests are denied), no pop-up windows, remote debugging off, HTTP-auth prompts and client-certificate requests keep WebView's default (declined), media needs a tap to play.

An `http://` page is shown with an open-lock icon and a red **Not secure (http)** label. Plain http stays allowed because the rest of the app allows it.

### Downloads from web pages
When a page serves a file (WebView's download listener fires), the browser does **not** download anything itself:
1. The address goes through a **routing check** (`DownloadRouting`). Only `http`/`https` file links pass. `blob:`/`data:` addresses, streaming playlists (`.m3u8`, `.mpd`, `.ism`, HLS/DASH MIME types), links with credentials and over-long links are refused with an explanation.
2. A **throttle** lets a page open the download prompt at most once every 3 seconds. A page can start a download from a script without any tap; without the limit it could re-open the dialog each time you close it. An open prompt is never replaced.
3. The link then enters the **same flow as Home**: duplicate check, link inspection (HEAD / ranged GET, redirects checked by hand), the **confirmation dialog** (name, host, size, type, warnings) and only after you tap *Download* the Android `DownloadManager`, the queue, notifications and the Downloads/Files tabs. **Nothing is saved before you confirm.**

### What cannot be downloaded (read this)
**The browser does not make every video or file downloadable, and it does not claim to.** Expect these to fail or be refused:
- **Streaming video** (HLS, DASH, most video sites): the page plays many small pieces from a playlist, there is no single file. Refused with a message.
- **DRM-protected media** (Netflix-style services and similar): not downloadable, by design.
- **`blob:` / `data:` downloads** created inside the page's memory by scripts: refused.
- **Files behind a login or a session**: the download manager receives only the link, **never your cookies or tokens** (passing them on would mean copying your session credentials). The server will answer 401/403 and the download is reported as *access denied*. This is intentional.
- **Links that work only once, only from the page (Referer checks), or via POST**: the link check or the download may fail.
- **Sites that need JavaScript** show a blank or broken page until you turn JavaScript on for them. Some sites also block embedded WebViews.
- **HTTP-auth pop-ups** (browser login boxes) are declined; use sites with normal login forms.
- **No tabs, bookmarks, history list, file upload or password saving.** It is a basic browser.

### Page state
The open page survives switching tabs and rotating the phone: its back/forward list is saved in memory when you leave the Browser tab and restored when you return (JavaScript permission is re-applied first). It is not saved to disk. Closing the app discards it.

### Manual test guide (device or emulator, Android 8.0+)
**Navigation**
1. Browser tab → the privacy notice dialog does **not** appear yet. Type `example.com` and press Go: the **notice appears**; tap *I understand*; the page loads. (Close the app, reopen, load another page: no notice again; menu → *Privacy notice* shows it read-only.)
2. Progress bar shows while loading; the address bar shows `https://example.com/` with a closed lock and "Secure (https)". Open a second page by tapping a link, then Back, Forward, Reload. Tap Stop during a slow load (the ✕ button).
3. Type `http://neverssl.com`: open-lock icon and red **Not secure (http)**.
4. Type `hello world`, `javascript:alert(1)`, `ftp://example.com`: each is refused under the address bar and nothing loads.
5. Switch to Downloads and back, and rotate the phone: the page and its Back history are still there.

**Errors**
6. Turn on airplane mode, load a new address: error panel "Site not found" / "Could not connect" with **Try again**. Turn it off, tap *Try again*: the page loads.
7. Open `https://expired.badssl.com`, `https://wrong.host.badssl.com`, `https://self-signed.badssl.com`, `https://untrusted-root.badssl.com`: each shows **Connection is not secure** with the reason and no way to continue. Confirm there is no "proceed" button anywhere.
8. Open `https://httpstat.us/404` (or any missing page): a notice "Page not found (404)" appears above the page.

**JavaScript**
9. Open a script-driven page (for example `https://www.google.com` or any single-page app): status line says **JavaScript off**, the page may be incomplete. Menu → *Turn JavaScript on for this site*: page reloads, status says **JavaScript on**. Open a different site: JavaScript is **off** again. Come back: it is on for the first site. Menu → *Turn JavaScript off…* returns to off.

**External links**
10. Menu → *Open in system browser* opens the page in Chrome or your default browser.
11. On a page with a `mailto:` link, tap it: the mail app opens. On a page with an `intent://` or `market://` link (or a page that redirects to one): the app stays in the browser and shows "Blocked a link that is not a web page".

**Direct downloads**
12. Open a page with a direct file link (for example `https://www.w3.org/WAI/WCAG21/wcag-2.1.pdf`, or any public PDF/ZIP/MP3 link) and tap it. The app shows **Checking the download link…**, then the **confirmation dialog** with name, host, size and type. Tap **Cancel**: nothing is saved. Tap the link again → **Download**: notification permission prompt (Android 13+), a "Download started" notice; the file appears in **Downloads** and later in **Files**.
13. Tap the same link again while it is downloading: "This link is already in your queue or downloading."
14. Open an HLS/DASH playlist address such as a `.m3u8` link: the page offers or shows it, and the app refuses with "streaming playlist … cannot be downloaded here".
15. Open a site that serves a file only to signed-in users: the confirmation or the download ends as **access denied**. The app never passes your cookies on.
16. Menu → *Clear browsing data* → confirm: the start page returns, Back is disabled, and previously signed-in sites ask you to sign in again.

## Audio and video downloads (Phase 7)

Phase 7 makes the existing download flow aware of audio and video **files**. It adds no permission, no library and no binary. Transfers still run in Android's `DownloadManager`, so the bytes are saved exactly as the server sends them; nothing is converted, re-encoded or re-packed (a test fails if converting APIs, ffmpeg or an extractor library appear in the source).

### What it does
| Requirement | Behaviour |
|---|---|
| Detect direct audio and video | The link check (`LinkInspector`) reads the response's `Content-Type`; when that is missing or generic (`application/octet-stream`) the file extension decides. A server that says `text/html` for `clip.mp4` is treated as a web page, not media. New `MediaFormats` names the format (MP4, WebM, MKV, MP3, M4A, FLAC, Opus, WAV, Ogg, …). |
| Ordinary formats | MP4, WebM, MP3, M4A and others in `MimeTypes` (added: m4v, ogv, mpg, mpeg, wmv, flv, opus, oga, weba, mka, wma, aif/aiff, amr). Anything else a server sends as a single file is downloaded as before. |
| Headers inspected | `Content-Type`, `Content-Disposition`, `Content-Length` / `Content-Range`, `Accept-Ranges`, redirect `Location`, and (for live streams) the `icy-*` radio headers. Only `User-Agent`, `Accept-Encoding: identity`, `Accept` and `Range` are ever sent (a test enforces it: no cookies, login or referrer headers). |
| Shown to the user | The confirmation dialog shows file name, host, size, type, **Video (MP4) / Audio (MP3)**, redirect count, the folder it will be saved in, "saved exactly as the server sends it", and whether the server can resume. |
| Original preserved | No transcoding anywhere. |
| Redirects | 301, 302, 303, 307, 308, relative and absolute, across hosts, up to 5 hops, each hop validated, https→http blocked (existing behaviour, now tested for media and CDN-style redirects). The link you approved is what `DownloadManager` requests. |
| Unsupported streaming | HLS (`.m3u8`), DASH (`.mpd`), Smooth/HDS manifests, playlists (`.m3u`, `.pls`, `.asx`) and live radio (Icecast/Shoutcast headers) are **refused with a specific explanation** in the link check and in the browser. A real `audio/*` or `video/*` type wins over a playlist-looking address, because then the server really is sending a file. |
| Queue, history, progress, files | Unchanged code paths: same queue, Room history, progress/speed/ETA, notifications, and the Files tab (Videos / Music categories). |
| Save location | See below. |

### New warnings (media only)
- **No size announced**: audio/video without a size can be an endless stream; the dialog says so (in addition to the existing unknown-size notice).
- **Server cannot resume** (`Accept-Ranges: none`): an interrupted download may restart.

### Choosing where files are saved
`DownloadManager` can only write below the system Downloads folder, so a "choose any folder" setting for the transfer itself is not possible without replacing it with a custom engine (see the Phase 4 decision table). Phase 7 offers the two things that are real:
1. **Settings → Download location**: *One folder* (default, `Download/ALMUS`) or *Video and Audio folders* (`Download/ALMUS/Video`, `Download/ALMUS/Audio`; everything else stays in `Download/ALMUS`). It applies to downloads handed to the system from then on; files already saved are never moved. The folder names are fixed constants, never text from the network.
2. **Files → ⋮ → Save a copy to…** opens the system "save as" screen so you can pick **any** place the system offers (internal storage, SD card, cloud providers) and a name. The app copies the bytes unchanged (size verified; a wrong-length or failed copy is deleted) and leaves the original in place. No new permission is needed.

Limits: the optional Phase 5 "chosen folder" listing is not recursive, so files in `Video`/`Audio` sub-folders are still listed through their download records but not as "not in history" files. Whether Android honours a sub-folder on every OEM build has not been tested on a device.

### What is NOT supported, and why
- **Streaming video/audio** (HLS, DASH, most video sites): there is no single file. Refused with an explanation.
- **DRM-protected media, files behind a login or session, Referer-only links, one-time links**: not downloadable by design. The app never sends your cookies or tokens and never forges a Referer.
- **Arbitrary social-media or video-site downloading**: not promised and not implemented.

### Third-party extractors and backends: not added (decision needed)
Nothing in this phase needs one. If you later want streams or site pages supported, these are the architectures, with their limits:
1. **On-device segment downloader for unencrypted HLS/DASH**: fetch the playlist, download the segments, join them. Needs a custom download engine (foreground service, own storage layer; see the Phase 4 table) and a muxer to produce one playable file, which is re-packing, not "preserving the original". It still cannot handle encrypted/DRM streams, and playlists that need cookies would fail for the same reason direct links do.
2. **A site extractor (for example a yt-dlp style tool) on a separate backend**: you would run and secure a server that fetches third-party pages on users' behalf. Extractors break whenever sites change, many sites forbid this in their terms, and you become responsible for what the server fetches. A binary or library of this kind must be evaluated first for maintenance status, licence and supply-chain safety.
3. **Bundling an extractor or ffmpeg binary inside the APK**: increases size, adds unreviewed native code and licence obligations (ffmpeg's licence depends on how it is built).
The project now carries a test (`NoTranscodingGuardTest`) that fails if any extractor, converter, native-code loader or unreviewed dependency group is added, so such a step has to be a conscious change.

### Manual test guide (device or emulator)
1. Home → paste a direct MP4 link (`https://…/clip.mp4`): dialog shows **Video (MP4)**, size, "saved exactly as the server sends it", folder `Download/ALMUS`. Download → Downloads tab shows progress; Files tab lists it under Videos.
2. Same with an MP3/M4A: **Audio (MP3)**.
3. Paste a `.m3u8` link, a `.mpd` link and an internet-radio stream URL: each is refused with its own explanation, nothing is queued.
4. Settings → Download location → *Video and Audio folders*; download an MP4 and an MP3: dialog says `Download/ALMUS/Video` / `…/Audio`; check them in the phone's Files app.
5. Files → ⋮ → **Save a copy to…** on a video: pick another folder or SD card; the notice reports the size; open the copy; the original is still listed.
6. A link behind a login ends as "Access denied", never bypassed.

### Phase 7 tests (new)
`MediaFormatsTest`, `StreamingFormatsTest`, `SaveLocationTest`, `MediaTypesTest`, `MediaWarningsTest`, `MediaSaveLocationRepositoryTest`, `FileCopierTest`, `FileCopyPolicyTest`, `LinkInspectorMediaTest` (real HTTP against two local servers: MP4/WebM/M4A/MP3, generic types, all five redirect codes, cross-host and `../` redirects, HLS/DASH/playlist/live refusals, 401/404, resume headers) and `NoTranscodingGuardTest`.

## Download engine (0.9.0)

**What it is.** A new download engine that streams files itself over HTTP(S) with OkHttp, next to the existing one (Android `DownloadManager`). It is **off by default**: Settings > Downloads > **New download engine (beta)** switches *new* downloads to it. The older engine and all its code are unchanged and remain the default until the new one has been tested on devices. The new engine needs **Android 10 (API 29) or newer**; on older versions the switch is disabled and nothing changes.

### What it does
| Requirement | How |
|---|---|
| Background downloads | Foreground service (type dataSync) started only from something the user does; stops itself when idle |
| Real pause / resume | Pause stops the transfer and keeps the partial file (its slot is freed); resume asks the server for the rest with `Range` |
| Verify server support before resuming | Before any request the planner (`ResumePlanner`) refuses hopeless resumes (server said `Accept-Ranges: none`, no way to check the file did not change). After the request the answer is checked: 206 with a matching `Content-Range` continues; 200 (Range or `If-Range` not honoured) restarts from zero; 416 either finishes or restarts |
| Servers without partial downloads | Detected (`Accept-Ranges: none` or a 200 answer to a Range request) and handled by restarting from zero |
| Automatic retry | Exponential delay (2 s, 4 s, ... capped at 60 s), `Retry-After` honoured, 5 tries; only 408/429/500/502/503/504 and connection errors are retried (403/404 and similar fail at once). The counter resets only when a download reaches new ground, so a server that always cuts at the same place cannot loop forever |
| Network interruptions | Offline (or Wi-Fi only on mobile data): transfers wait without using up retries and continue when a usable network returns |
| Recovery after restart | Every download and its partial file are saved (Room table `engine_downloads`). On start, interrupted ones continue from the partial file; a partial file that already has every byte is just published |
| Queue and simultaneous downloads | FIFO queue; limit = the existing "Simultaneous downloads" setting (1 to 5), read every time a slot frees |
| Speed and time remaining | Existing `SpeedEstimator`; shown in the list and in the notification |
| Progress notifications | One ongoing notification per download with Pause / Resume / Cancel buttons, plus a summary for the foreground service; finished/failed notifications |
| Cancellation | Stops the transfer, deletes the partial file |
| Duplicate detection | Same address (fragment and default port ignored) already queued, running, paused or waiting is refused |
| File size validation | Received bytes must equal the announced size when one is known; shorter means retry, longer means start again; the file is never published otherwise |
| Interrupted connections | The partial file only grows at its end and is truncated to the saved offset before each resume; nothing is published until it is complete |
| Persistence | Room (migration 1 to 2, no history lost) |

### Safety
Redirects are followed by the engine itself (`HttpSession`), never by OkHttp: max 5 hops, no circles, never https to http, never from a public address to a local-network address, every hop validated. Only these request headers are ever sent: User-Agent, Accept-Encoding (`identity`), Accept, Range, If-Range. No cookies, no login data, no referrer. Source tests enforce this. Nothing here downloads from platform pages: the link detection of 0.8.0 still decides what may be offered.

### Architecture (package `com.almus.downloader.engine`)
| File | Role |
|---|---|
| `DownloadEngine` | Queue, state machine, transfer, retry, recovery. No Android classes |
| `EnginePorts` | Interfaces: `EngineStore`, `FinalStorage`, `NetworkMonitor`, `EngineSettings`, `HttpTransport` |
| `HttpSession`, `OkHttpTransport` | Safe redirects; OkHttp lives in this one file |
| `ResumePlanner`, `RetryPolicy`, `HttpHeaders` | Pure decision logic |
| `android/*` | Room store, MediaStore storage, network monitor, notifications, foreground service, resume worker, `EngineHost` |

Partial files live in the app's private storage (`parts/dl-<id>.part`, a name the engine makes itself). A complete file is copied into `Download/ALMUS` (or `Video` / `Audio`) with MediaStore as "pending", then made visible. WorkManager (`ResumeWorker`, expedited, needs a network) continues downloads after a restart or when Android ends the foreground service (Android 15 limits dataSync services to about 6 hours); nothing is started from the boot broadcast itself.

### Behaviour and limits you should know
- **Completed downloads of the new engine appear in the Downloads tab (open, remove, delete) but not in the Files tab**, which still reads the older table. Not done in this phase.
- No segmented (multi-connection) downloads, no speed limit, no scheduling.
- A paused or retrying download is not "running", so it does not hold a slot.
- When Android 15 ends the foreground service, running downloads go back to the queue and the worker continues them. That path cannot be tested without a device.
- Dependencies added: OkHttp 4.12.0, WorkManager 2.9.1 (and the notification permissions FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, ACCESS_NETWORK_STATE). No vulnerability scan could be run (no network); both are widely used releases, but they are not the newest.
- Room now exports its schema to `app/schemas` on the first build. **Commit that file.** The migration SQL was written by hand to match the entity and has never been run.

### Manual test guide (device, Android 10 or newer)
1. Settings > Downloads > turn on **New download engine (beta)**. Add a large file (a few hundred MB) from a server you control or a speed-test file.
2. The download shows speed and time left; the notification shows progress with Pause and Cancel.
3. **Pause** from the card and from the notification: the speed disappears, the status becomes "On hold", the file size on screen stops growing. **Resume**: it continues from the same place (not from zero).
4. Turn on airplane mode during a download: it shows "Waiting for a network connection."; turn it off: it continues by itself.
5. Turn on **Download on Wi-Fi only** and switch to mobile data: it waits; back on Wi-Fi it continues.
6. Swipe the app away during a download (or force-stop, then reopen): the download is still listed and continues.
7. Restart the phone with an unfinished download: open the app (or wait for the resume work); it continues.
8. Set Simultaneous downloads to 1 and add three downloads: one runs, two wait; pausing the running one starts the next.
9. Cancel a download: the partial file is removed (Settings > Apps > Storage should not grow).
10. When a download finishes, the file is in Download/ALMUS (and Video/Audio if that layout is chosen) and opens from the card.
Please report anything that differs, with the Android version and device.

### Tests (new, 80)
`DownloadEngineTest` (41, real OkHttp against a local server that can refuse Range, cut connections, answer 503/404/416, change its file and be slow: resume, no-range servers, changed file, pause/resume, cancel, retry and give-up, offline and Wi-Fi only, concurrency limit and order, duplicates, redirects, recovery after a "crash", orphan files), `HttpSessionTest` (10), `ResumePlannerTest` (9), `HttpHeadersTest` (7), `EngineSourceGuardTest` (6), `EngineStateTest` (4), `RetryPolicyTest` (3). `NoTranscodingGuardTest` was extended on purpose (OkHttp group allowed; `If-Range` allowed); it still forbids converters, extractor libraries, other dependencies and any other request header.

## Smart Link Detection (0.8.0)

**What it is.** A text-only step that runs before the existing link check. It tells the user what a link is (platform, kind, id), whether it is a direct file, a page that is not a file, or restricted, and explains why in plain language. **It is detection, not downloading.** It adds no downloader, no extractor, no permission and no library, and it never makes a platform page downloadable. It does not sign in, does not bypass authentication, DRM, paywalls, privacy settings or any platform restriction (a source-reading test fails if cookie/login handling, converting APIs or extractor libraries appear).

### What the user sees
- Under the address field on Home, a one-line hint appears as soon as the link is valid (for example "YouTube video · not a direct file", "Direct link · MP4 video file").
- **Direct file / ordinary web link:** the existing flow continues unchanged (server check, confirmation, DownloadManager). The confirmation now also shows a "Source:" line.
- **Platform page (video, post, profile, playlist ...):** a dialog explains what the link is and that Almus Downloader saves files a server offers directly, not videos taken out of platform pages. Platform-specific notes are shown (for example that Vimeo owners can make videos private or not downloadable). A **Check as a normal link** button runs the old behaviour (ask the server; you would get the page's HTML), so no earlier ability was removed.
- **Restricted** (sign-in page, stories, private groups, DRM/paid, temporary player streams, streaming manifests on platform file hosts, or a platform answering 401/403/404/410): an explanation only. It cannot be forced.
- **Blocked** (`javascript:`, `data:`, `file:`, `intent:` and similar, or a username/password inside the address) and **invalid** links get a clear message. Nothing is opened.
- **Short links** (t.co, fb.watch, vm.tiktok.com, pin.it, Reddit share links, Facebook/Instagram share links): the redirects are followed safely to learn the real address, which is then detected again.

### Safe redirect checking (`SafeRedirectResolver`)
Only http/https; every hop validated; at most 5 hops; no circles; never https to http; never from a public address to a local-network address; HEAD first (one-byte ranged GET if HEAD is refused); the response body is never read; only User-Agent, Accept-Encoding, Accept and Range are sent (no cookies, no login data, no referrer). The only network contact is with the short link's own server, started by the user pressing the button.

### Architecture (package `com.almus.downloader.platform`)
| File | Role |
|---|---|
| `MediaPlatformAdapter` | The common interface: `platform`, `brandTokens`, `ownsHost`, `classify`, `describe`, `notesFor`. Adapters only read text. |
| `adapters/*Adapter.kt` | One independent adapter per platform (YouTube, Facebook, TikTok, Instagram, X, Vimeo, Dailymotion, Reddit, Pinterest) plus `DirectLinkAdapter` as the fallback. A test fails if one adapter refers to another. |
| `SmartLinkDetector` | Validates with the existing `UrlValidator`, picks the adapter, maps to a `LinkVerdict`, builds messages and safety notes. |
| `UrlSafety`, `ParsedUrl`, `AdapterSupport` | Text-only host checks (exact/sub-domain match so `youtube.com.evil.com` is not YouTube), parsing, shared helpers. |
| `SafeRedirectResolver`, `LinkIntake` | Safe redirects; the whole decision flow (detect, resolve, stop at pages, duplicates, server check), testable without Android. |

To add a platform: write one `XxxAdapter : MediaPlatformAdapter`, add it to `SmartLinkDetector.defaultAdapters`, add lines to `src/test/resources/link_vectors.tsv`.

### Behaviour change to know about
Before, pasting a YouTube watch-page link led to the confirmation dialog with a "web page" warning. Now it leads to the explanation dialog first; "Check as a normal link" gives the old result.

### Limits
- Recognition is by address patterns only; platforms change their addresses, so an unrecognised address falls back to the normal server check (it is never wrongly blocked for being unrecognised).
- Private, deleted or age-restricted content cannot be known from the address alone; it is reported only if a short-link check saw a 401/403/404/410.
- Messages are English and live in Kotlin (like the existing error messages), not in `strings.xml`; only the two dialog buttons are string resources.
- Country domains of Pinterest are an allow-list; an unlisted one is treated as an ordinary web link.
- Nothing here downloads video or audio from any platform page. That is intentionally not part of this feature.

### Manual test guide (device or emulator)
1. Paste `https://www.youtube.com/watch?v=dQw4w9WgXcQ`: hint under the field says "YouTube video · not a direct file"; tap Download: the explanation dialog opens; Close. Nothing is queued.
2. Same link, **Check as a normal link**: the old confirmation appears (with the web-page warning).
3. Paste `https://www.instagram.com/stories/someone/1/`: restricted explanation, no override button.
4. Paste a real direct file link (for example a PDF): "Direct link" hint, then the normal confirmation with "Source: direct link".
5. Paste a `t.co` short link to a file: the app resolves it, then shows the normal confirmation.
6. Type `javascript:alert(1)`: the field shows the validator message; nothing opens.
7. In the Browser tab, start a download of a file: the same flow runs.

### Tests (new, 78)
`LinkVectorsTest` (128 shared vectors in `src/test/resources/link_vectors.tsv`), `SmartLinkDetectorTest`, `UrlSafetyTest`, `ParsedUrlTest`, `SafeRedirectResolverTest` (scripted fake connections, no network, body never readable), `LinkIntakeTest`, `PlatformSourceGuardTest`.

## YouTube integration (0.10.0)

Full description, limitations and manual test guide: **[docs/YOUTUBE_INTEGRATION.md](docs/YOUTUBE_INTEGRATION.md)**.

In short: a plain YouTube video, Short or live link now opens a dedicated dialog (package `com.almus.downloader.youtube`).
It shows what YouTube's official services say about the video, offers the official ways to watch it, handles
deleted, private, age-restricted, region-restricted, live and unreachable cases with explanations, and states why the
video cannot be saved. Duration and the age/region flags need a free YouTube Data API v3 key that the person adds in
**Settings → YouTube**. There is no YouTube download: the app has no code for streams, players, signatures, cookies or
sign-in, and `YouTubeSourceGuardTest` fails the build if any appears. No new permissions or dependencies.

## Verify a successful build

1. **Push** to GitHub (steps below). Open **Actions**, then the latest **Android Build** run. Every step must be green:
   *Run unit tests*, *Upload unit test reports*, *Build debug APK*, *Upload debug APK*, *Verify APK (version and permissions)*.
2. **Unit tests:** download the **almus-unit-test-reports** artifact and open `index.html`. Expect **532 tests, 0 failures** (this is the number of `@Test` methods in the project at this version).
3. **APK check (automatic):** the *Verify APK* step prints the package line (it must show `versionName='0.12.0'` and `versionCode='12'`) and the permissions, and fails the build if `INTERNET`, `POST_NOTIFICATIONS` or `RECEIVE_BOOT_COMPLETED` is missing, or if any broad storage permission or `REQUEST_INSTALL_PACKAGES` is present. `WRITE_EXTERNAL_STORAGE` appears with `maxSdkVersion='28'`.
4. **Install** `app-debug.apk` from the **almus-downloader-debug-apk** artifact on a phone or emulator (Android 8.0+).
5. **Browser test:** follow the *Manual test guide* in the in-app browser section above (about 10 minutes).
6. **Smoke test** of the file manager (about 5 minutes):
   - Download a PDF, an MP3 and a ZIP from public direct links. Files tab: three rows with size, date and MIME type; category chips show 1 each under Documents, Music, Archives.
   - Type part of a name in the search box (list narrows); change sort to Size, then flip the direction.
   - Row menu: **Open** (a viewer appears), **Share** (system chooser appears).
   - Delete one file in the **Files app** of the phone, return to the app: the row shows **File missing** and only *Remove from list* is offered.
   - Row menu **Delete file**: a confirmation names the file; Cancel keeps it; Delete removes it and the row disappears.
   - Download an `.apk`: its menu says **Show in Downloads**, never installs anything.
   - Menu **Choose a folder**, pick `Download/ALMUS`: the folder card shows the file count. Revoke it (menu *Stop using folder*) and the card returns to its first state.
   - Turn on airplane mode: the file list still works (it needs no network).

## Phase 3 limits that still apply
- Unknown-size downloads have no automatic size cap; the guard is the confirmation dialog, the Cancel button and the system's out-of-space error.
- Links containing raw spaces or non-ASCII characters are rejected; paste the percent-encoded form.
- Links without a scheme are rejected (not guessed).
- The Home "Search downloads" field is still a visual field only (as in Phase 2).
- The Phase 2 "Preview sample cards" switch was **removed**, because the Downloads screen now shows real data only. The Browser "preview loading" switch was removed in Phase 6, because the browser now loads real pages.
- Settings rows for Wi-Fi only, location and notifications are still "Coming soon".

## Tests

Run: `./gradlew testDebugUnitTest` (HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`).

| Test class | Covers |
|---|---|
| `UrlValidatorTest` | http/https accepted; ftp/file/javascript/data/content rejected; missing scheme/host; credentials; spaces; bad ports; length |
| `FileNameResolverTest` | Content-Disposition (quoted, plain, RFC 5987); URL names; path traversal; control/bidi characters; length limit; fallback; extension from MIME; uniqueness |
| `StatusMapperTest` | DownloadManager status/reason to state and messages; HTTP errors; copied constants compared with the real `DownloadManager` constants at compile time; byte formatting |
| `SnapshotReducerTest` | progress, completion, failure, pause, removal; finished records never change |
| `LinkInspectorTest` | **real HTTP** against a local JVM server: successful check, name/type/size from headers, relative redirect, HEAD refused → ranged GET, unknown size, HTML page, 404/403/500, redirect loop, redirect to ftp, refused connection, unknown host, invalid URL |
| `DownloadRepositoryTest` | successful flow Queued → Running → Completed with one notification; failed download; restart restore; removed-in-system; crash before enqueue; low space; enqueue error; unique names; legacy storage fallback; cancel; retry; remove; foreign broadcast ids ignored |
| `LinkWarningsTest` | warnings shown before approval |
| `QueueManagerTest` | concurrency limit never exceeded; FIFO order; raising/lowering the limit; hold/resume; cancel promotes the next; retry goes to the end; duplicate links; failed hand-off does not block the queue; restart; orphan cleanup; crash between hand-off and saving; remove-from-list never deletes files; delete only completed; bulk cancel/hold/resume/retry |
| `ProgressTrackingTest` | speed appears, ETA only with enough data, no ETA for unknown size, cleared on pause/complete/cancel, independent tracking |
| `SpeedEstimatorTest` | window, minimum samples, stalls, restarts, rounding, absurd values |
| `DownloadPolicyTest` | state × operation table, filter partition (each state in exactly one tab), bulk actions only apply to allowed states, delete files only on Completed tab |
| `FormatsTest` | speed and remaining-time text, duplicate-link key |
| `FileKindTest` | the seven categories, extension-first classification, APK/archive/document rules, folder labels, natural name order |
| `FileListLogicTest` | search, category filter and counts, sorting by name/date/size/type in both directions with unknowns last, the combined view (empty, no matches, permission denied, missing), the open/share/delete/remove policy |
| `AddressResolverTest` | scheme-less addresses get https; http/https kept; scheme case; localhost and IPv6; javascript/data/file/content/intent/mailto/ftp/blob rejected; plain text is not searched; empty; credentials; missing host; too long |
| `NavigationPolicyTest` | web pages load in the app; credential links blocked; dangerous and custom schemes always blocked (with and without a tap); mailto/tel/sms only after a tap on the main page; empty and scheme-less blocked |
| `DownloadRoutingTest` | direct links go to the manager; blob/data refused with an explanation; other schemes and empty refused; credentials refused; HLS/DASH by extension (also with query/fragment/case) and by MIME type; the prompt throttle |
| `JavaScriptPolicyTest` | off by default; per exact host (no subdomain or look-alike leakage); saved and restored; turning off; bad hosts refused without changes; junk in storage ignored; list cap; clear all; host extraction; blank page detection |
| `BrowserErrorsTest` | every WebView error code has a message; SSL handshake and every certificate error are security errors that say there is no way through; distinct reasons; HTTP status notices; Android constants compared at compile time |
| `BrowserReducerTest` | page start/progress/finish/stop; the blank page is the start page; errors survive `onPageFinished`; a certificate error is not overwritten by the generic error that follows; HTTP status notice; crash handling and engine rebuild; reset; title; privacy flag; lock icon state |
| `BrowserSecurityGuardTest` | **reads the app's source** (comments ignored) and fails if it ever contains `proceed()`, a custom trust manager or hostname verifier, `addJavascriptInterface`, `evaluateJavascript`, JavaScript turned on unconditionally, cookie reads or a `Cookie` header, third-party cookies on, file access, mixed content, geolocation, remote debugging, or a granted web permission; and checks that SSL errors cancel and permission requests are denied |
| `FileLibraryTest` | scan of completed downloads with a fake file probe (available, missing, no access, errors), size/date/type fallbacks, the optional SAF folder (merge without duplicates, denied, gone, failed), re-check before acting |

### Tested vs. not tested
**Covered by JVM unit tests (written, see the status note below):** everything in the table above. `DownloadRepositoryTest` uses a fake DownloadManager and an in-memory table, so "successful download" there proves the bookkeeping, not the real transfer.

**Browser (Phase 6) covered by JVM tests:** address handling, navigation and download routing rules, per-site JavaScript policy, error messages, the screen's state transitions, and the source-scan guard for the security rules above.

**Browser not covered by any automated test (needs a device or emulator):** the real WebView (loading, redirects, cookies, the SSL callback actually firing, the download listener firing), the Compose screen, the menu and dialogs, Back-button handling, page restore after tab switch or rotation, `ExternalOpen` intents, and what real sites do. Use the manual test guide.

**Not covered by any automated test (needs a device or emulator):** the real file probe, the SAF folder picker and `DocumentsContract` calls, the open/share intents and chooser, the Files screen and its dialogs, the real `DownloadManager` transfer and its own redirect/cleartext handling, `DownloadManager.remove()` really deleting the file, the Settings chips, the Actions menu and dialogs, the boot receiver, Room on a real database, the notification and its permission prompts, the runtime-permission dialogs, scoped-storage destination, the broadcast receiver, the Compose screens, the Open button on real files.

### Status of this build (YouTube integration, 0.10.0)
Written **without a Kotlin compiler, Android SDK, Gradle or network access**: **not compiled, and none of the 574 tests (42 new) have been run.** What was done: bracket-balance checks of every changed file (two reports were checker artifacts: a `'"'` character literal and a count already off by one in the original file); a check that every `yt_*` string resource used exists and `strings.xml` is valid XML; emulation of the existing `NoTranscodingGuardTest` rules and of the new `YouTubeSourceGuardTest` rules over the real sources (no hits; the only hosts are `www.googleapis.com` and `www.youtube.com`; the only headers are `User-Agent`, `Accept` and `x-goog-api-key`); and a line-by-line review of each test's expected values against the code. The live YouTube services were not contacted, and the dialog, Settings field, intents and thumbnail loading have never run on a device. **Run the GitHub Actions build first and send me the first failure.**

### Status of the 0.9.0 build (download engine)
Written **without a Kotlin compiler, Android SDK, Gradle or network access**: **not compiled, and none of the 532 tests (80 new) have been run.** The engine logic has not been executed anywhere. What was done: brace/parenthesis balance of every changed file; emulation of the existing source-guard rules over all main sources (no hits; request headers are only User-Agent, Accept-Encoding, Accept, Range, If-Range); a review of every engine path against the integration tests; and a review that found and fixed a retry-loop flaw before release (retries used to restart on any progress). The Room migration, the foreground service, the notifications, MediaStore saving and the resume worker need a device. **Run the GitHub Actions build first and send me the first failure.**

### Status of the 0.8.0 build (Smart Link Detection)
Also written **without a Kotlin compiler, Android SDK, Gradle or network access**: **not compiled, and none of the 452 tests (78 new) have been run.** What was run: brace-balance checks on every changed file; an emulation of the existing source guard rules over all main sources (no hits; request headers used are only User-Agent, Accept-Encoding, Accept, Range); and an independent Python port of the adapter rules run against the 128 shared vectors (it found and led to the fix of one real bug, `instagram.com/share/reel/...`; now 128 of 128 agree). The port shows the expected values and the logic are consistent, **not** that the Kotlin compiles or that the Compose UI works. The first GitHub Actions run is the real compile and test run, and the UI was never run on a device.

### Status of the Phase 7 build
The Phase 7 code was also written **without a Kotlin compiler, Android SDK, Gradle or network access**. It has **not been compiled and the Kotlin tests have not been run by the author**. What was actually run: (a) static checks (string resources, XML validity, imports, cross-file symbols); (b) a Python mirror of the new detection, folder and inspector logic run against a real local HTTP server with the same scenarios and expected values as the Kotlin tests (43 checks passed); (c) a Java program confirming the JDK `HttpServer`/`HttpURLConnection` behaviours the new tests rely on (13 checks passed). Those show the expected values and the JDK behaviour are right, not that the Kotlin compiles. The first GitHub Actions run is the real compile and test run.

### Status of the earlier build
This code was written without access to the Kotlin compiler, the Android SDK or Maven. **It has not been compiled, and the unit tests have not been executed by the author.** Only static checks were done (XML validity, string-resource references, bracket balance, unused imports, search for TLS-bypass, cookie-reading and JavaScript-bridge code) plus small experiments confirming the logic the tests rely on: for Phase 6 the address, navigation-policy, download-routing and host logic were re-implemented in a second language and run against the same inputs and expected values the Kotlin tests assert (all passed). That proves the expected values, not that the Kotlin compiles. **The Phase 6 WebView code has never run on a device.** The first GitHub Actions run is the real compile and test run, and the manual test guide is the real check of the browser.

## Build with GitHub Actions

1. Create an empty repository on GitHub (no README).
2. In the unzipped project folder run:
   ```
   git init
   git add .
   git commit -m "ALMUS DOWNLOADER phase 7: audio and video downloads"
   git branch -M main
   git remote add origin https://github.com/<your-user>/<your-repo>.git
   git push -u origin main
   ```
   (If `gradle/wrapper/gradle-wrapper.jar` is missing after unzipping, the build cannot start; it is included in this project.)
3. Open the repository's **Actions** tab, then the latest **Android Build** run (or **Run workflow** on the `main` branch).
4. The job runs, in order: `./gradlew testDebugUnitTest`, upload of the test report, `./gradlew assembleDebug`, upload of the APK, and an APK check (version and permissions).
5. When it finishes, open the run and scroll to **Artifacts**:
   - **almus-downloader-debug-apk** – unzip it to get `app-debug.apk`.
   - **almus-unit-test-reports** – open `index.html` to see test results (uploaded even when tests fail).
6. Copy `app-debug.apk` to the phone and install it (allow "install unknown apps" for your file manager or browser when Android asks).
7. If a step is red, open it and read the first `error:` or `FAILED` line. A failing test step stops the build before the APK is made; send that log line for a fix.

## Build locally
JDK 17 and Android Studio (or the Android SDK with platform 35): `./gradlew testDebugUnitTest assembleDebug`. The APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Project structure
```
app/src/main/java/com/almus/downloader/
├── AlmusApplication.kt          Application + AppContainer (Room, repository, notifier)
├── MainActivity.kt              Edge-to-edge UI, progress sync while visible, notification tap
├── data/                        DownloadEntity, DownloadDao, AlmusDatabase (Room)
├── download/                    DownloadPolicy (state rules, filters, bulk actions), SpeedEstimator, UrlKey,
│                                UrlValidator, FileNameResolver, MimeTypes, LinkInspector,
│                                DownloadState/StatusMapper, SnapshotReducer, DownloadRepository,
│                                LinkWarnings, ByteFormat, Permissions, Platform (interfaces),
│                                AndroidSystemDownloader, AndroidStorageInfo,
│                                AndroidDownloadNotifier, DownloadCompleteReceiver,
│                                BootCompletedReceiver, SharedPrefsQueueSettings
├── files/                       FileKind, FileModels, FileListLogic (search/sort/view/policy), FileLibrary (scan + merge),
│                                NaturalOrder, AndroidFileProbe, DocumentsFolderLister (SAF), AndroidFileActions,
│                                SharedPrefsFolderSettings
├── browser/                     AddressResolver, NavigationPolicy, DownloadRouting (+ prompt throttle), JavaScriptPolicy,
│                                HostKey, BrowserErrors, BrowserState (state + reducer), BrowserWebView (hardened WebView
│                                + clients), ExternalOpen, SharedPrefsBrowserStore
├── youtube/                     YouTube integration: links, official metadata client, viewing options, download policy,
│                                android/ (OkHttp, thumbnail, opener, key store); see docs/YOUTUBE_INTEGRATION.md
├── model/                       FileCategory, DownloadItem (+status/filter, entity mapping), ChatMessage
├── navigation/                  Destinations
└── ui/                          theme, components (cards, dialogs, inputs), screens (BrowserScreen), DownloadsViewModel, BrowserViewModel
app/src/test/java/.../download/  unit tests and fakes
```

## Versions
Android Gradle Plugin 8.7.3, Gradle 8.9, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Room 2.6.1, Lifecycle 2.8.7, Coroutines 1.9.0, Compose BOM 2024.12.01, Navigation Compose 2.8.5, minSdk 26, compile/target SDK 35. App version 0.12.0.
# Almus-downloader-
