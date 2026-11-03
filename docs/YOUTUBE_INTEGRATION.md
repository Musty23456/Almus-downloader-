# YouTube integration (0.10.0)

Almus Downloader recognises YouTube links, asks **YouTube's own public services** for the video's public details,
shows the **official ways to watch it**, and explains plainly **why it cannot be downloaded here**.

It does **not** download YouTube videos. That is a design decision, not a missing feature: YouTube offers no public
download service or API for third-party apps, and this module deliberately has no code that works around that.

## What the user sees

Paste a YouTube video link on Home (or tap one in the Browser tab) and a dialog opens with:

| Part | Source | Notes |
|---|---|---|
| Thumbnail, title, channel | oEmbed, or Data API when a key is saved | No key needed for these three |
| Duration, live/upcoming state | Data API (key needed) | oEmbed does not provide it; the dialog says so |
| Licence (standard / Creative Commons), embedding allowed or not | Data API | |
| Age-restricted / region-restricted flags | Data API | |
| **Open in the YouTube app** | `ACTION_VIEW` of the youtube.com address, restricted to `com.google.android.youtube` | Falls back to the browser with a message if the app is not installed |
| **Open on youtube.com** | `ACTION_VIEW` | |
| **Share the link** | share sheet | |
| **Downloading** section | `YouTubeDownloadPolicy` | Always explains; offers a button only for an authorized offer (none ships) |

## Handling of unusual videos

| Situation | What happens |
|---|---|
| Deleted / removed | "YouTube says this video does not exist…" (oEmbed 404) or "removed, rejected or never finished processing" (Data API upload status) |
| Private | Data API with a key returns no item for other people's private videos, so the text says "deleted, private, or not shared with apps. YouTube does not say which." |
| Age-restricted | With a key: details are shown with a note and the download explanation. Without a key: oEmbed typically refuses (401), shown as "may be private or age-restricted, or embedding is off. YouTube does not say which." |
| Region-restricted | Flag from the Data API, shown as a note |
| Live / upcoming | Shown as "Live now" / "Upcoming"; the policy says live streams cannot be saved |
| Network error / rate limit | A clear message and a **Try again** button; the viewing options are still shown |
| Bad, short or look-alike links | Never reach this module (`youtube.com.evil.example`, 10-character ids, playlists, channels, `googlevideo.com` stream addresses are all rejected by `YouTubeLinks`) |

For every unavailable case the three viewing options stay available: the video may be the person's own, or they may
be able to sign in inside the official app.

## Architecture (package `com.almus.downloader.youtube`)

```
youtube/
├── YouTubeLinks.kt             validated video reference from a LinkDetection (text only)
├── YouTubeModels.kt            ref, info, lookup result, unavailable reasons, API key shape
├── YouTubeMetadataClient.kt    Data API (if key) then oEmbed; the only code that decides what to ask
├── YouTubeResponseParser.kt    reads the two official answers
├── MiniJson.kt, IsoDuration.kt tiny strict JSON reader; "PT3M32S" -> 212 s -> "3:32"
├── YouTubeViewingOptions.kt    official ways to watch / share
├── YouTubeDownloadPolicy.kt    why downloading is unavailable; the AuthorizedDownloadProvider seam
├── YouTubeIntegration.kt       facade: inspect(ref) -> YouTubeReport (never throws)
└── android/
    ├── OkHttpYouTubeHttp.kt        https GET to www.googleapis.com and www.youtube.com only, no redirects, 256 KB cap
    ├── ThumbnailLoader.kt          fetches only https *.ytimg.com pictures, shows them, never saves them
    ├── YouTubeOpener.kt            open in app / browser / share sheet
    └── SharedPrefsYouTubeKeyStore.kt   the person's own API key
ui/components/YouTubeVideoDialog.kt, ui/state/YouTubeViewModel.kt   the dialog and its state
```

Everything outside `android/` is plain Kotlin and unit tested on the JVM. The existing detection package
(`platform/`) and its YouTube adapter are unchanged; `LinkDetectionDialog` simply routes plain YouTube video
addresses to the new dialog. Removing the module means deleting the `youtube` package and the one `if` in
`LinkDetectionDialog`.

## The API key

* Optional. Without one, title, channel and thumbnail still work (oEmbed).
* The person creates their own free key in the Google Cloud console and enables **YouTube Data API v3**. No key is
  built into the app, so there is no shared quota and nothing to leak from the APK.
* It is sent in the `x-goog-api-key` header, never in the address. It is stored in the app's private
  SharedPreferences (not encrypted; Android's app sandbox is the protection). It is used only to read public
  video details. It is **not** a sign-in and grants no access to private data.
* If the key is rejected, restricted to other apps, or out of quota, the dialog falls back to oEmbed and says so.
  A key restricted to "Android apps" in Google Cloud needs the app's package and signing certificate headers,
  which this version does not send: restrict the key to the YouTube Data API v3 only (API restriction) instead.
* The free Data API quota is 10,000 units per day; one `videos.list` call costs 1 unit.

## Downloads: the policy

`YouTubeDownloadPolicy.decide()` returns `NotAvailable` (with reasons and alternatives) unless an
`AuthorizedDownloadProvider` returns an offer. **No provider is shipped**, because no official mechanism exists for
third-party apps. The seam exists so that, if YouTube ever publishes one, or a content owner explicitly offers a
file, it can be added in one place. Even then every offer is checked: it must be https, public, and must **not**
point at YouTube's own hosts (`youtube.com`, `youtu.be`, `youtube-nocookie.com`, `ytimg.com`, `googlevideo.com`).
An accepted offer goes through the normal link check and the normal "Download?" confirmation.

Reasons shown to the person: no public download service exists; live streams cannot be saved; age-restricted videos
need a signed-in adult in YouTube's own app and Almus never signs in; regional limits are not worked around;
a Creative Commons marking describes reuse permissions but does not give apps a download mechanism.
Alternatives shown: use the download feature inside the YouTube app (YouTube Premium may be needed), or paste a
direct file link if the owner publishes the file themselves.

## What this module will never do (and a test enforces it)

`YouTubeSourceGuardTest` reads the module's source and fails the build if it contains: stream, player or signature
handling (`streamingData`, `adaptiveFormats`, `player_response`, `videoplayback`, `youtubei`, ciphers, manifests);
cookies, tokens, OAuth or sign-in code; any address other than `www.googleapis.com` and `www.youtube.com`; request
headers other than `User-Agent`, `Accept`, `x-goog-api-key`; Android or OkHttp code outside `android/`;
or any code that writes files. The existing `NoTranscodingGuardTest` still applies to the whole project.

## Limitations

1. **No YouTube downloads.** By design (see above).
2. **Duration, age and region flags need the person's own API key.** Without one the dialog says so.
3. **oEmbed cannot tell "private", "age-restricted" and "embedding disabled" apart** (all answer 401), and the Data API
   returns an empty list for deleted and for private videos. The texts say "YouTube does not say which" instead of guessing.
4. **Region restriction is a flag, not a verdict.** The API lists allowed or blocked countries; the app does not
   compare them with the person's country, so it only says availability varies.
5. **No in-app player.** YouTube's official IFrame Player could show embeddable videos inside the app; it needs a
   hardened WebView and was left out of this version. The dialog reports whether the owner allows embedding.
6. **Only video, Shorts and live links** open this dialog. Playlists, channels and search pages keep the existing explanation.
7. **Opening in the YouTube app** relies on the app being installed; otherwise the page opens in a browser.
8. **English text only**, like the rest of the detection messages.
9. **Not compiled or run** by the author (no Kotlin compiler, Android SDK, Gradle or network was available): the first
   GitHub Actions run is the real compile and test run, and the dialog, Settings field, intents and thumbnail loading
   have never run on a device. oEmbed and Data API behaviour was written from the services' documented behaviour and
   not tested against the live services.
10. The API's behaviour and quotas can change; YouTube's terms apply to any use of its services.

## Manual test guide (device, Android 8.0+)

1. Paste `https://www.youtube.com/watch?v=<a public video id>` on Home, tap **Start download**: a YouTube dialog opens
   with thumbnail, title and channel; duration shows "needs a YouTube API key".
2. Settings → YouTube: paste a Data API v3 key → Save ("A key is saved (ends in …)"). Repeat step 1: duration appears.
3. **Open in the YouTube app** opens the app on that video. With the app uninstalled, a message appears and the browser opens.
4. **Share the link** opens the share sheet with the youtube.com address.
5. Try a deleted video id, a private video, an age-restricted video, a live stream, a Short and a `youtu.be` link:
   each gives the matching text from the table above and never a Download button.
6. Turn on airplane mode and try again: "Could not reach YouTube…" with **Try again**.
7. Paste a wrong key in Settings: "That does not look like a Google API key." Paste a well-shaped but invalid key:
   the dialog shows basic details with "YouTube did not accept the saved API key…".
8. Paste a playlist, a channel and a `googlevideo.com` address: the older explanation dialog appears, not this one.

## Tests (new, 42)

`MiniJsonAndDurationTest` (6), `YouTubeLinksTest` (4), `YouTubeMetadataClientTest` (14, with a fake HTTP layer),
`YouTubePolicyAndOptionsTest` (10), `YouTubeSourceGuardTest` (8).
