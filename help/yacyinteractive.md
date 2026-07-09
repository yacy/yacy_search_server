---
page: htroot/yacyinteractive.html
help: help/yacyinteractive.md
title: Interactive Search
package: core-search-public
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/yacyinteractive.java
---

# Interactive Search

## Purpose

Interactive Search offers a dynamic search experience.

Use it when users expect result updates or interaction beyond a static result page.

## What You Can Do Here

- Interactive Search offers a dynamic search experience.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Search terms, for example `climate data` or a more specific phrase. |
| `Enter` | Browser submit button; omit in direct API calls. | `Search` |

## Correct Use

Start from the user's information need. Use query text, content type, collection, URL filters, and pagination to narrow results. Do not mix ordinary search with authenticated result actions unless the user explicitly asks to modify stored data.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

Page backend: `source/net/yacy/htroot/yacyinteractive.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/yacyinteractive.html` | `GET` | public or page-dependent | `source/net/yacy/htroot/yacyinteractive.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `focus` | Browser presentation flag controlling whether the search input receives focus. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `maximumRecords` | Maximum number of results to return on one page. Use a modest value for interactive use; larger values are for controlled scripts. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `startRecord` | First result record for pagination; accepted as an alternative to `offset` on search endpoints. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /yacyinteractive.html?query=...&maximumRecords=...&Enter=...&focus=...&startRecord=...
```

## What To Expect

Expect rendered search or content output: result lists, snippets, previews, redirects, widgets, or fragments. If output is empty, check whether the index contains matching documents before changing query syntax.

## Related Pages

- Related search work usually continues on `yacysearch.html`, `index.html`, `ViewFile.html`, quick crawl, or the search integration pages.
