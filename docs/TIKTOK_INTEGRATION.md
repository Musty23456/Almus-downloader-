# TikTok integration (0.12.0)

Almus Downloader recognises TikTok videos and short links, shows what TikTok's **official public oEmbed service** says about a video (caption, creator, thumbnail where available), offers official ways to open it in TikTok, and explains clearly when saving is not possible. It never signs in, never scrapes a page, never reads a media stream and never gets around a restriction.

## What it does

| Need | How |
|---|---|
| Detect video URLs | `platform/adapters/TikTokAdapter` (detection only) recognises `/@name/video/ID`, `/@/video/ID`, `/embed/ID`, `/embed/v2/ID`, `/player/v1/ID` and legacy `/v/ID.html`, with query strings, fragments, trailing slashes, upper-case hosts and the hosts `tiktok.com`, `www.`, `m.` and other sub-domains. Photo posts, live pages, profiles, sign-in pages, tag/music/search pages and file hosts are told apart. Look-alike hosts (`tiktok.com.evil.com`, `nottiktok.com`) are never TikTok. |
| Detect short links | `vm.tiktok.com/…`, `vt.tiktok.com/…` and `tiktok.com/t/…` are classified as opaque short links. |
| Resolve redirects safely | The existing `SafeRedirectResolver`: web addresses only, at most 5 hops, no https→http, no public→local network, HEAD first, body never read, no cookies. The resolved address is detected again. |
| Validate | `TikTokLinks.contentRef` accepts only a TikTok *video* with a 15–20 digit id (and a creator name of up to 24 letters, digits, `_`, `.` when the address has one). Everything built later (canonical address, oEmbed request, "Open in TikTok" address) comes from the validated id and name only, so share/tracking parameters (`_r`, `u_code`, `sec_user_id`, …) are never passed on. |
| Public metadata, authorized | `TikTokMetadataClient` calls TikTok's documented oEmbed endpoint `https://www.tiktok.com/oembed?url=…`. No sign-in, key, token, cookie or extra header. |
| Title, creator, thumbnail | Caption (TikTok's oEmbed `title`), creator name and profile link, thumbnail. The thumbnail is shown only when it is https and on a TikTok image host (`tiktokcdn.com`, `tiktokcdn-us.com`, `tiktokcdn-eu.com`, `muscdn.com`); any other host is dropped. |
| Permitted downloads | `TikTokDownloadPolicy` has a provider seam for an official mechanism or an owner's explicit offer. **No provider ships**, because TikTok publishes no download API for third-party apps (its Display API returns cover image, description and embed link, never the video file). Offers that point at TikTok's own hosts, plain http, local networks or non-files are dropped. |
| User-owned content | `TikTokExport`: guide to TikTok's own *Download your data* and to *Save video* on the person's own profile, with a button to TikTok's Help Center. The guide says the data file describes your videos and may not contain the video files themselves. |
| Open in TikTok | Open in the TikTok app (`com.zhiliaoapp.musically`, then `com.ss.android.ugc.trill`), on tiktok.com, or share the link. Also offered for photo posts, live pages and profiles. TikTok decides what the person may see. |
| Unsupported videos | Clear, separate messages: not shared with apps / no information / limits / server error / unreadable answer / offline, plus why downloading is unavailable and what to do instead. |

## Architecture (`com.almus.downloader.tiktok`)

| File | Role |
|---|---|
| `TikTokLinks` | detector output → validated `TikTokContentRef`; trust checks for thumbnails, profiles and openable addresses |
| `TikTokModels` | reference, info, lookup outcomes, reasons |
| `TikTokMetadataClient` + `TikTokHttp` | the only network seam (tests replace it); one oEmbed request |
| `TikTokResponseParser` | documented fields only; checks the answer is about the video asked for; untrusted addresses dropped; the embed markup is never shown or run |
| `TikTokDownloadPolicy` | `TikTokDownloadProvider` seam (none shipped) and the explanation |
| `TikTokAccessOptions` | open in app / browser / share |
| `TikTokExportGuide` | official export and Save video instructions |
| `TikTokIntegration` | single entry point; `inspect()` never throws |
| `android/` | OkHttp transport (host `www.tiktok.com`, path `/oembed`, https only, no redirects), thumbnail loader, opener |

The platform adapter and this module are independent: the adapter only reads text; this module only talks to the official service.

## A change to shared detection
`MediaPlatformAdapter.pageStatusIsReliable` (default `true`) lets an adapter say that a plain page request's HTTP status must not be read as an access verdict. `TikTokAdapter` returns `false`: sites often answer non-browser requests with bot-protection errors that say nothing about the video, so TikTok's own oEmbed answer decides instead. No other platform changes.

## Error handling
| Situation | Result |
|---|---|
| HTTP 401/403 | "TikTok does not share this video with apps…" (may be private, removed, restricted, or the request refused) |
| HTTP 400/404/410, or an error object in the answer | "TikTok returned no information…" |
| HTTP 429 or a rate-limit code | "TikTok is limiting requests right now. Try again later." |
| 5xx / other status | status shown, *Try again* |
| Unreadable answer, answer about another video, not a video | "could not be read" |
| Offline / timeout / oversized answer | "Could not reach TikTok…", *Try again* |
| Address without a creator name that TikTok does not recognise | extra notice explaining why |

## What is deliberately not done
No login or cookie handling, no page or app-API scraping, no player, stream or watermark handling, no DRM or access-control handling, no embed script or web view. `TikTokSourceGuardTest` fails the build if the module gains any of that, any host other than `www.tiktok.com` / `support.tiktok.com`, extra request headers, or file writing.

## Limitations
- **Not verified against the live service.** The authoring environment had no network, no Android SDK and no Kotlin compiler. oEmbed behaviour follows TikTok's documentation. TikTok does not document its oEmbed error answers, so the status/error mapping above is defensive and should be checked on a device.
- Addresses without a creator name (embed, player, legacy `/v/`) are asked about as `/@/video/ID`; whether oEmbed accepts that is undocumented, so such videos may report "no information" (the notice says why).
- oEmbed returns no length, date or counts; thumbnails on hosts outside the list are not shown.
- Photo posts and live pages get "Open in TikTok" only; no metadata lookup.
- No download from TikTok, by design. A verified own-video lookup through the TikTok Display API (Login Kit, registered app, OAuth) is not included.
- The export guide's menu names come from public descriptions of TikTok's current app and can change; the Help Center button is the reference.
