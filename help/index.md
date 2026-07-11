---
page: htroot/index.html
help: help/index.md
title: Search Page
package: core-search-public
access: mixed
kind: search-page
backend_java: source/net/yacy/htroot/index.java
---

# Search Page

## Purpose

The start page is the usual entry point for searching and navigating YaCy.

Use it when a user first opens the peer in a browser.

## What You Can Do Here

- The start page is the usual entry point for searching and navigating YaCy.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Search terms, for example `climate data` or a more specific phrase. |
| `Enter` | Browser submit button. Omit it from direct API calls; YaCy uses the query and filter parameters, not the button label, to run the search. | Text value; use the page label and surrounding context to choose the exact content. |
| `contentdom` | Content domain filter: text, image, audio, video, application, or all depending on the page. | `text` = Text, `image` = Text, `audio` = Text, `video` = Text, `app` = Text |
| `indexof` | Text / only index pages. | only index pages |
| `resource` | Search resource selection such as local or global/network search. | `global` = the peer-to-peer network, `local` = only the local index |
| `prefermaskfilter` | Regular-expression URL preference. Matching results are favored without excluding all others. | Regular expression for preferred result URLs. |
| `maximumRecords` | Maximum number of records displayed or returned. | `10`, `50`, `100` |
| `prefermask` | Prefer mask. Options: `yes` = Prefer mask, `no` = Prefer mask. | `yes` = Prefer mask, `no` = Prefer mask |
| `strictContentDom` | Require the content domain filter strictly. | `false` = Extended, `true` = Extended |

## Correct Use

Start from the user's information need. Use query text, content type, collection, URL filters, and pagination to narrow results. Do not mix ordinary search with authenticated result actions unless the user explicitly asks to modify stored data.

## Access And Safety

The page may be visible, but the backend performs authentication checks for protected actions.

Protected related endpoint(s): `/yacysearch.html`, `/index.html`.

Backend checks: built-in administrator authentication for protected search features.

## Automation And API

Page backend: `source/net/yacy/htroot/index.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/yacysearch.html` | `GET` | mixed | `source/net/yacy/htroot/yacysearch.java` |
| `/index.html` | `GET or POST` | mixed | `source/net/yacy/htroot/index.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `contentdom` | Content domain filter. Common values are `all`, `text`, `image`, `audio`, `video`, and `app`; use it to ask for web pages, media, or application documents deliberately. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexof` | Interprets the query as an index-of style request when supported. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `resource` | Search source. `local` searches this peer index; `global` may use the YaCy network when the peer and configuration allow it. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `prefermaskfilter` | Regular-expression URL preference. Matching results are favored without excluding all others. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `maximumRecords` | Maximum number of results to return on one page. Use a modest value for interactive use; larger values are for controlled scripts. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `prefermask` | Prefer mask. Options: `yes` = Prefer mask, `no` = Prefer mask. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `strictContentDom` | When true, YaCy enforces the selected content domain more strictly instead of using it mainly for presentation. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `auth` | Requests authentication-aware behavior when the endpoint supports user-specific or protected actions. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `callback` | JSONP callback name for legacy script clients. Leave empty for normal HTML or JSON-style use. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `collection` | Collection name. Use it to group crawled or imported documents and to search or manage that group later. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `constraint` | Encoded YaCy bitfield constraint. Leave it empty unless you are replaying a URL generated by YaCy itself. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `count` | SRU-style result count. It is an alternative to `maximumRecords` on search endpoints. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `deleteref` | URL hash/reference selected for deletion from results. Use only with administrator intent and an exact known reference. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `focus` | Browser presentation flag controlling whether the search input receives focus. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `lr` | Language filter, usually a two-letter code such as `de`, `fr`, or `en` when the page supports language-restricted search. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `meanCount` | Text. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `nav` | Navigator/facet selection for search results. `all` requests the normal set; narrower values reduce displayed facets. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `offset` | Zero-based result offset for pagination. | Read-only pagination control; use it to request later result pages. |
| `recommendref` | URL hash/reference selected for recommendation. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `resortCachedResults` | Requests resorting of a cached search event instead of starting a fully fresh result event. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `resource-switch` | Browser control for switching between local and global search. For direct calls use `resource=local` or `resource=global`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `rows` | Rows requested by API-style clients. It is another result-count alias on search endpoints. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `search` | Alternative search text parameter accepted by some search endpoints; prefer `query` on browser search pages unless reproducing an existing URL. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `startRecord` | First result record for pagination; accepted as an alternative to `offset` on search endpoints. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `timezoneOffset` | Client timezone offset in minutes. YaCy uses it for date display or schedule calculations. | Read-only request context for date handling. |
| `urlmaskfilter` | Regular-expression URL filter for search results. Use `.*` for no restriction; use a precise host/path expression to search inside a site or section. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `verify` | Snippet/cache verification strategy. Typical values are `iffresh`, `ifexist`, `cacheonly`, `nocache`, and `false`; choose according to whether freshness or speed matters more. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /yacysearch.html?query=...&maximumRecords=...&resource=...&contentdom=...&Enter=...
```

## What To Expect

Expect rendered search or content output: result lists, snippets, previews, redirects, widgets, or fragments. If output is empty, check whether the index contains matching documents before changing query syntax.

## Related Pages

- `CrawlStartSite.html`
- `ConfigBasic.html`
