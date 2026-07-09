---
page: htroot/yacysearch_location.html
help: help/yacysearch_location.md
title: Location Search
package: core-search-public
access: public
kind: search-page
backend_java: source/net/yacy/htroot/yacysearch_location.java
---

# Location Search

## Purpose

Location Search searches with geographic or location-oriented result handling.

Use it when place context matters more than a plain text result list.

## What You Can Do Here

- Location Search searches with geographic or location-oriented result handling.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Search terms, for example `climate data` or a more specific phrase. |
| `Enter` | Browser submit button; omit in direct API calls. | `search` |

## Correct Use

Start from the user's information need. Use query text, content type, collection, URL filters, and pagination to narrow results. Do not mix ordinary search with authenticated result actions unless the user explicitly asks to modify stored data.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

Page backend: `source/net/yacy/htroot/yacysearch_location.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/yacysearch_location.html` | `GET` | public or page-dependent | `source/net/yacy/htroot/yacysearch_location.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `contentdom` | Content domain filter. Common values are `all`, `text`, `image`, `audio`, `video`, and `app`; use it to ask for web pages, media, or application documents deliberately. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `maximumRecords` | Maximum number of results to return on one page. Use a modest value for interactive use; larger values are for controlled scripts. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `maximumTime` | Date/time value for filtering, display, or scheduling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search` | Alternative search text parameter accepted by some search endpoints; prefer `query` on browser search pages unless reproducing an existing URL. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `verify` | Snippet/cache verification strategy. Typical values are `iffresh`, `ifexist`, `cacheonly`, `nocache`, and `false`; choose according to whether freshness or speed matters more. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /yacysearch_location.html?query=...&maximumRecords=...&contentdom=...&Enter=...&dom=...
```

## What To Expect

Expect rendered search or content output: result lists, snippets, previews, redirects, widgets, or fragments. If output is empty, check whether the index contains matching documents before changing query syntax.

## Related Pages

- Related search work usually continues on `yacysearch.html`, `index.html`, `ViewFile.html`, quick crawl, or the search integration pages.
