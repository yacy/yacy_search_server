---
page: htroot/Collage.html
help: help/Collage.md
title: Image Collage
package: core-search-public
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/Collage.java
---

# Image Collage

## Purpose

Image Collage displays image search results as a visual collection.

Use it when browsing pictures is more useful than reading a ranked text list.

## What You Can Do Here

- Image Collage displays image search results as a visual collection.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

## Correct Use

Start from the user's information need. Use query text, content type, collection, URL filters, and pagination to narrow results. Do not mix ordinary search with authenticated result actions unless the user explicitly asks to modify stored data.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

Backend checks: user authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/Collage.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Collage.html` | `GET` | public or page-dependent | `source/net/yacy/htroot/Collage.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |

Example request shape:

```http
GET /Collage.html?emb=...&height=...&max=...&width=...
```

## What To Expect

Expect rendered search or content output: result lists, snippets, previews, redirects, widgets, or fragments. If output is empty, check whether the index contains matching documents before changing query syntax.

## Related Pages

- Related search work usually continues on `yacysearch.html`, `index.html`, `ViewFile.html`, quick crawl, or the search integration pages.
