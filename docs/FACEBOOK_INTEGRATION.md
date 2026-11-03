# Facebook integration (0.11.0)

Almus Downloader recognises Facebook videos, Reels and live videos, shows what Facebook's **official** services say about them, offers official ways to open them in Facebook, and explains clearly when saving is not possible. It never signs in, never scrapes a page and never gets around a restriction.

## What it does

| Need | How |
|---|---|
| Detect video / Reel URLs | `platform/adapters/FacebookAdapter` (detection only) recognises `watch/?v=`, `video.php?v=`, `/{page}/videos/{id}`, `/reel/{id}`, `/reels/{id}`, `/watch/live/?v=`, `fb.watch` and `/share/...` short links (followed safely by the existing redirect resolver), on `facebook.com`, `m.`, `web.`, `mbasic.` and `fb.com`. Look-alike hosts are never Facebook. |
| Recognise supported public content | `FacebookLinks.contentRef` validates the id (5–20 digits). Facebook's oEmbed answers whether the content is public and embeddable. |
| Metadata through authorized methods | Facebook oEmbed (no token, no credentials sent): public/embeddable + size. Graph API video node, **only** with the person's own access token: title, description, owner, thumbnail, length, date. |
| Downloads where permitted | Only for a video the Graph API returned for the person's own token **and** whose owner is the account of that token (`/me` id equals `from.id`). Then the API's own file address is offered and goes through the normal link check and approval. Nothing else is ever offered. |
| User-owned content via official export | Step-by-step guide for Facebook's export (account and Page) with buttons to Facebook's own help pages. Exported videos can be viewed through Files → Choose a folder. |
| Thumbnails and info | Shown when the API returns them (https, Facebook image host only). |
| Open restricted content in Facebook | Open in the Facebook app / on facebook.com / share, for videos and for restricted pages (groups, stories, posts). Facebook decides what the person may see. |
| Meaningful errors | See below. |

## Important: what Facebook's public service no longer returns
Meta lifted the access-token requirement for oEmbed on 15 June 2026, but oEmbed returns only `html`, `width`, `height`, `type`, `version`, `provider_*`. Title, author and thumbnail are **not** returned (Meta documents them as removed). So without a token the dialog shows "Facebook confirms this video is public", its orientation and size, and says why more is missing. This was verified against Meta's oEmbed Video reference.

## Architecture (`com.almus.downloader.facebook`)
| File | Role |
|---|---|
| `FacebookLinks` | detector output → validated `FacebookContentRef`; trust checks for thumbnail, profile and openable addresses |
| `FacebookModels` | ref, info, lookup outcomes, reasons, token format |
| `FacebookMetadataClient` + `FacebookHttp` | the only network seam (tests replace it); oEmbed then Graph API fallbacks |
| `FacebookResponseParser` | documented fields only; untrusted addresses dropped |
| `FacebookDownloadPolicy` | `FacebookDownloadProvider` seam + `OwnVideoProvider`; offers filtered (https, public host, not a Facebook page, accepted by the app's own link check) |
| `FacebookAccessOptions` | open in app / browser / share |
| `FacebookExportGuide` | official export instructions |
| `FacebookIntegration` | single entry point; `inspect()` never throws |
| `android/` | OkHttp transport (host allow-list `graph.facebook.com`, no redirects), token store, thumbnail loader, opener |

The platform adapter and this module are independent: the adapter only reads text; this module only talks to official services.

## Error handling
| Situation | Result |
|---|---|
| Private / limited / needs sign-in (Graph code 200 or 10, HTTP 401/403) | "Facebook does not share this with apps…" |
| Removed, unsupported, bad id (code 100, HTTP 404) | "Facebook returned no information…" |
| Rate limit (codes 4, 17, 32, 613, HTTP 429) | "Facebook is limiting requests… Try again later." |
| Server error / unreadable answer | status shown / "could not be read" |
| Offline / timeout | "Could not reach Facebook…" with *Try again* |
| Token rejected, expired, lacking permission, rate-limited, malformed | notice, falls back to public information; a malformed token is never sent |
| Unknown Graph field | one retry with fewer fields |
| Restricted by a 401/403/404 on the page | treated as restricted, only *Open in Facebook* is offered |

## What is deliberately not done
No login or cookie handling, no page or GraphQL scraping, no player or stream extraction, no DRM or access-control handling, no private-group access. `FacebookSourceGuardTest` fails the build if the module gains any of that, any host other than `graph.facebook.com` / `www.facebook.com`, extra request headers, the token in an address, or file writing.

## Settings
Settings → Facebook: optional access token (masked input, stored privately, only the last four characters shown). Create it yourself in Meta's developer tools; never paste a password. Which videos a token can read, and for how long, is decided by Facebook and Meta's app permissions (for example `user_videos` for your own videos).

## Limitations
- Meta's API versions and permissions change; `GRAPH_VERSION` is `v26.0`.
- Reels support in oEmbed is not documented; unsupported ones are reported as "no information".
- Own-video file offers depend on Facebook returning the `source` field to your token.
- Not compiled as an Android app in the authoring environment (no Android SDK); the plain-Kotlin core and all its tests were compiled and run. The first CI build is the real check for the UI layer.
