# Platform integrations: Instagram, X, Vimeo, Dailymotion, Reddit, Pinterest (0.13.0)

Almus Downloader recognises content on these six platforms, asks each platform's **own public oEmbed service** what it is willing to share, identifies what kind of media the address is, offers the **official** ways to open it (the platform's app, its website, share), and says clearly when saving is not possible. It never signs in, never scrapes a page, never reads a media stream, never removes protection and never gets around a privacy or access setting.

All six use the existing `MediaPlatformAdapter` architecture: the adapters in `platform/adapters` (unchanged) recognise and classify addresses; this module (`com.almus.downloader.integration`) takes over from what the detector found.

## What each requirement maps to

| Requirement | Where it is done |
|---|---|
| Platform identification | `platform/adapters/{Instagram,X,Vimeo,Dailymotion,Reddit,Pinterest}Adapter` via `SmartLinkDetector` (detection only, unchanged). Look-alike hosts (`vimeo.com.evil.example`) are never a platform. |
| URL validation | `PlatformLinks.contentRef`: kind must be content (not a profile, story, sign-in or short link), the verdict must not be *Restricted*, and the id must match the platform's own id format (`PlatformSpec.isValidContentId`). Everything built later comes from the validated parts, so share and tracking parameters are never passed on. |
| Public metadata where authorized | `OEmbedClient` → one request to the platform's documented oEmbed endpoint. No sign-in, key, token, cookie or extra header. |
| Thumbnail previews | Shown only when the service returns one **and** it is https on that platform's image host (`PlatformSpec.thumbnailDomains`). Loaded by `PlatformThumbnailLoader`, never saved. Where a service returns none, the dialog says so instead of guessing. |
| Supported media identification | `PlatformSpec.identifyMedia`: from the address kind and, when known, the service answer. It never claims more than is known ("Post (a photo, a video or a carousel)", not "photo"). |
| Authorized download support | `DownloadProvider` seam in `DownloadPolicy`: the only door for an official download mechanism or an owner's explicit file offer. Offers on the platform's own hosts, plain http, local networks or non-files are dropped; the rest goes through the normal link check and approval. **No provider ships** (see below). |
| Fallback to the official platform | `AccessOptions`: open in the platform's app, open on its website, share the link. Also offered for profiles and boards. The platform decides what the person may see. |
| Clear unsupported-content messages | Per-platform reasons and alternatives (`downloadReasons`, `downloadAlternatives`), refusal messages (`unavailableMessage`), plus the detector's messages for sign-in pages, stories, protected content and short links. |

## The six platforms

| | Public service used | What it returns | Thumbnail | Content handled |
|---|---|---|---|---|
| **Instagram** | Meta oEmbed for Instagram: `graph.facebook.com/v26.0/instagram_oembed`. Needs no token since 15 June 2026. | Only technical data (embed markup, size). Since Nov 2025 no title, author or thumbnail. The dialog can confirm "public" but not preview. | none | `/p/`, `/reel/`, `/tv/` (also `/user/reel/…`) |
| **X (Twitter)** | `publish.twitter.com/oembed` | Author name and link; the post's text (read from the first paragraph of the embed markup as plain text) | none | `/user/status/ID`, `/i/status/ID` |
| **Vimeo** | `vimeo.com/api/oembed.json` | Title, owner, length, publishing date, thumbnail | `*.vimeocdn.com` | `vimeo.com/ID`, `player.vimeo.com/video/ID`, channel/group/showcase video addresses |
| **Dailymotion** | `www.dailymotion.com/services/oembed` | Title, owner, thumbnail | `*.dmcdn.net` | `/video/ID`, `dai.ly/ID`, `geo.dailymotion.com/player.html?video=ID` |
| **Reddit** | `www.reddit.com/oembed` | Post title, author | `*.redditmedia.com`, `*.redd.it` if given | `/r/sub/comments/ID`, `/gallery/ID`, `redd.it/ID`. **Not** `v.redd.it` (its id is a video id, not a post id) |
| **Pinterest** | `www.pinterest.com/oembed.json` | Creator, title and thumbnail when given | `*.pinimg.com` | `/pin/ID` on any Pinterest country domain (rebuilt as `www.pinterest.com`) |

Short links (`t.co`, `pin.it`, `redd.it/r/…/s/…`, `instagram.com/share/…`) are resolved by the existing `SafeRedirectResolver` (web addresses only, at most 5 hops, body never read) and the result is detected again.

## Downloads: what is and is not supported

None of these platforms publishes a public download service for third-party apps, so **no download provider ships**, and the dialog says so with platform-specific reasons and alternatives. What stays possible, by design:

- A direct file link published by the content owner can be pasted into Almus Downloader: the normal link check and approval apply.
- If an official mechanism or an owner's explicit offer ever exists, it is added as a `DownloadProvider` for that platform; `DownloadPolicy` still drops offers that point back at the platform's own hosts or at unsafe addresses.
- For the person's **own** content each platform has a guide (`PlatformSpec.ownContent`) to the platform's official data export or download option, with a button to its Help Center. The guides say menu names change and the Help Center is the reference.

Not done, on purpose: DRM or protection removal, sign-in or cookies, private/protected/age/region-limited content, stream, manifest or player-configuration handling, page scraping, watermark handling, access tokens.

## Architecture (`com.almus.downloader.integration`)

| File | Role |
|---|---|
| `PlatformSpec` + `specs/*Spec` | everything platform-specific, one small object per platform: id format, canonical address, oEmbed endpoint, trusted hosts, app package, media identification, reasons, own-content guide |
| `PlatformSpecs` | the list of platforms; adding one = one spec + one line here (+ its recognition adapter) |
| `PlatformLinks` | detector output → validated `ContentRef`; trust checks for thumbnails, creator and openable addresses |
| `OEmbedClient` + `OEmbedHttp` | the only network seam (tests replace it); one request per lookup; maps statuses to outcomes |
| `OEmbedParser`, `MarkupText` | documented fields only; checks the answer is about the content asked for; untrusted addresses dropped; markup read as text, never shown or run |
| `DownloadPolicy` | `DownloadProvider` seam (none shipped) and the explanation |
| `AccessOptions` | open in app / on the website / share |
| `PlatformIntegration` | single entry point; `inspect()` never throws for a reference made by `PlatformLinks` |
| `android/` | OkHttp transport (fixed host and path per platform, https only, no redirects), thumbnail loader, opener |
| UI | `PlatformContentDialog`, `PlatformViewModel`; `LinkDetectionDialog` routes content to the dialog and offers *Open in …* buttons for profiles and boards |

The adapters and this module are independent: adapters only read text; this module only talks to the official services. `IntegrationSourceGuardTest` fails the build if the module ever gains scraping, stream/manifest handling, sign-in/cookie/token code, a host outside the reviewed list, extra request headers, a web view or file writing.

## Outcomes

| Situation | Result |
|---|---|
| 200, readable, about this content | details shown |
| 200, `error` object | *not found or not supported* message |
| 200, unreadable, or about other content, or unknown `type` | "answer could not be read" |
| 401 / 403 | *not shared with apps* (private, limited, removed, region/age, or the request was refused; the platform does not say which) |
| 400 / 404 / 410 | *no information for this address* |
| 429, or a rate-limit code (Meta: 4, 17, 32, 613) | "limiting requests, try again later" |
| 5xx or any other status | "not answering right now" |
| Offline / timeout / answer too large | "could not reach …" with Try again |

## Adding a platform
1. Add or reuse its recognition adapter (`platform/adapters`).
2. Write `XyzSpec : PlatformSpec` in `integration/specs`.
3. Add it to `PlatformSpecs.all`, add the app package to the manifest `<queries>`, and add the new hosts to `IntegrationSourceGuardTest.onlyOfficialHostsAreAddressed`.
4. Add tests next to `OEmbedClientTest`.

## Manual test guide
1. Paste a public Vimeo video link: dialog shows title, owner, length, thumbnail, "Video", open buttons, why saving is unavailable, your-own-videos guide.
2. Paste a public X post: the post text and author show; no thumbnail note.
3. Paste an Instagram post/Reel: "Instagram confirms this content is public", no thumbnail note; a private-account post gives the *not shared* message.
4. Reddit post, Pinterest pin, Dailymotion video: details as in the table.
5. `v.redd.it/…`, a story, a sign-in page, a profile: no content dialog; profiles show *Open in …* buttons; stories and sign-in pages are explained and never opened.
6. Airplane mode: "Could not reach …" and Try again. Without the platform app installed: the browser opens instead.

## Known limitations
- **Not compiled and not run.** The authoring environment had no Kotlin compiler, Android SDK or network. The new tests were written but never executed; the source-guard patterns were checked against the sources with a script. The first CI run is the real check and may need small fixes.
- No platform service was contacted. Endpoints come from the platforms' public documentation; only the Instagram change (tokenless Meta oEmbed since 15 June 2026, no title/author/thumbnail) was confirmed against Meta's own announcement. The Instagram path is pinned to `v26.0` (as the Facebook module is); Meta retires Graph versions over time, so update `InstagramSpec` when that happens. Pinterest's and Reddit's answers may be limited or blocked for non-browser clients; the module reports what it gets and never retries another way.
- Help Center addresses are site roots; they were not opened to check them.
- Unlisted Vimeo videos (`vimeo.com/ID/HASH`) are asked about by id only, so the service may report them as not shared.
- Detection of 401/403/404 on pages (`pageStatusIsReliable`) is unchanged for these platforms; a bot-protection error seen while resolving a short link can still be read as "private". The oEmbed answer is what decides in the dialog.
- No downloading, by design (see above).
