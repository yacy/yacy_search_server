---
page: htroot/CacheResource_p.html
help: help/CacheResource_p.md
title: CacheResource p
package: core-search-public
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/CacheResource_p.java
---

# CacheResource p

## Purpose

Cache Resource serves cached resource data.

Use it to retrieve a document or asset from YaCy's cache when direct loading is not desired or possible.

## What You Can Do Here

- Cache Resource serves cached resource data.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

## Correct Use

Start from the user's information need. Use query text, content type, collection, URL filters, and pagination to narrow results. Do not mix ordinary search with authenticated result actions unless the user explicitly asks to modify stored data.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/CacheResource_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/CacheResource_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/CacheResource_p.html` | `GET or POST` | admin | `source/net/yacy/htroot/CacheResource_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `load` | Loads the selected URL, host, or path detail view. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET or POST /CacheResource_p.html?url=...&load=...
```

## What To Expect

Expect rendered search or content output: result lists, snippets, previews, redirects, widgets, or fragments. If output is empty, check whether the index contains matching documents before changing query syntax.

## Related Pages

- Related search work usually continues on `yacysearch.html`, `index.html`, `ViewFile.html`, quick crawl, or the search integration pages.
