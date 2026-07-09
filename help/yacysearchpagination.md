---
page: htroot/yacysearchpagination.html
help: help/yacysearchpagination.md
title: yacysearchpagination
package: core-search-public
access: public
kind: search-page
backend_java: source/net/yacy/htroot/yacysearchpagination.java
---

# yacysearchpagination

## Purpose

Search pagination is a result-navigation fragment.

Use it indirectly to understand how result pages link to earlier and later result ranges.

## What You Can Do Here

- Search pagination is a result-navigation fragment.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

## Correct Use

Start from the user's information need. Use query text, content type, collection, URL filters, and pagination to narrow results. Do not mix ordinary search with authenticated result actions unless the user explicitly asks to modify stored data.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

Page backend: `source/net/yacy/htroot/yacysearchpagination.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/yacysearchpagination.html` | `GET or POST` | public or page-dependent | `source/net/yacy/htroot/yacysearchpagination.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `auth` | Requests authentication-aware behavior when the endpoint supports user-specific or protected actions. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `jsResort` | Sort field or sort direction. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `maximumRecords` | Maximum number of results to return on one page. Use a modest value for interactive use; larger values are for controlled scripts. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `offset` | Zero-based result offset for pagination. | Read-only pagination control; use it to request later result pages. |

Example request shape:

```http
GET or POST /yacysearchpagination.html?maximumRecords=...&auth=...&eventID=...&jsResort=...&offset=...
```

## What To Expect

Expect rendered search or content output: result lists, snippets, previews, redirects, widgets, or fragments. If output is empty, check whether the index contains matching documents before changing query syntax.

## Related Pages

- Related search work usually continues on `yacysearch.html`, `index.html`, `ViewFile.html`, quick crawl, or the search integration pages.
