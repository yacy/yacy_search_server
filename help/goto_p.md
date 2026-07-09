---
page: htroot/goto_p.html
help: help/goto_p.md
title: forwarding
package: core-search-public
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/goto_p.java
---

# forwarding

## Purpose

Forwarding redirects to a selected result or target URL.

Use it as a controlled handoff from YaCy to an external document.

## What You Can Do Here

- Forwarding redirects to a selected result or target URL.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

## Correct Use

Start from the user's information need. Use query text, content type, collection, URL filters, and pagination to narrow results. Do not mix ordinary search with authenticated result actions unless the user explicitly asks to modify stored data.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/goto_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/goto_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/goto_p.html` | `GET or POST` | admin | `source/net/yacy/htroot/goto_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `hash` | YaCy hash identifier for a peer, URL, row, or stored object. Use exact values copied from YaCy output. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `path` | URL path, URL prefix, or host/path scope. In index tools it decides which part of the stored URL tree is inspected or changed. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET or POST /goto_p.html?hash=...&path=...
```

## What To Expect

Expect rendered search or content output: result lists, snippets, previews, redirects, widgets, or fragments. If output is empty, check whether the index contains matching documents before changing query syntax.

## Related Pages

- Related search work usually continues on `yacysearch.html`, `index.html`, `ViewFile.html`, quick crawl, or the search integration pages.
