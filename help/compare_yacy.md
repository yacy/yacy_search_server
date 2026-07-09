---
page: htroot/compare_yacy.html
help: help/compare_yacy.md
title: Websearch Comparison
package: core-search-public
access: mixed
kind: ui-page
backend_java: source/net/yacy/htroot/compare_yacy.java
---

# Websearch Comparison

## Purpose

Websearch Comparison compares YaCy results with another search source.

Use it to evaluate coverage or ranking differences for the same query.

## What You Can Do Here

- Websearch Comparison compares YaCy results with another search source.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Search terms, for example `climate data` or a more specific phrase. |

## Correct Use

Start from the user's information need. Use query text, content type, collection, URL filters, and pagination to narrow results. Do not mix ordinary search with authenticated result actions unless the user explicitly asks to modify stored data.

## Access And Safety

The page may be visible, but the backend performs authentication checks for protected actions.

Protected related endpoint(s): `/compare_yacy.html`.

Backend checks: administrator authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/compare_yacy.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/compare_yacy.html` | `GET` | mixed | `source/net/yacy/htroot/compare_yacy.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |

Example request shape:

```http
GET /compare_yacy.html?query=...&left=...&right=...&display=...
```

## What To Expect

Expect rendered search or content output: result lists, snippets, previews, redirects, widgets, or fragments. If output is empty, check whether the index contains matching documents before changing query syntax.

## Related Pages

- Related search work usually continues on `yacysearch.html`, `index.html`, `ViewFile.html`, quick crawl, or the search integration pages.
