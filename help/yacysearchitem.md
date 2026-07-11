---
page: htroot/yacysearchitem.html
help: help/yacysearchitem.md
title: yacysearchitem
package: core-search-public
access: mixed
kind: search-page
backend_java: source/net/yacy/htroot/yacysearchitem.java
---

# yacysearchitem

## Purpose

Search item is a result-rendering fragment.

Use it indirectly: it explains how one result entry is shaped inside the search page.

## What You Can Do Here

- Search item is a result-rendering fragment.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

## Correct Use

Start from the user's information need. Use query text, content type, collection, URL filters, and pagination to narrow results. Do not mix ordinary search with authenticated result actions unless the user explicitly asks to modify stored data.

## Access And Safety

The page may be visible, but the backend performs authentication checks for protected actions.

Protected related endpoint(s): `/yacysearchitem.html`.

Backend checks: built-in administrator authentication for protected actions and full media viewing rights.

## Automation And API

Page backend: `source/net/yacy/htroot/yacysearchitem.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/yacysearchitem.html` | `GET or POST` | mixed | `source/net/yacy/htroot/yacysearchitem.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `auth` | Requests authentication-aware behavior when the endpoint supports user-specific or protected actions. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET or POST /yacysearchitem.html?auth=...&eventID=...&item=...
```

## What To Expect

Expect rendered search or content output: result lists, snippets, previews, redirects, widgets, or fragments. If output is empty, check whether the index contains matching documents before changing query syntax.

## Related Pages

- `ViewFile.html`
- `api/citation.html`
- `yacysearch.html`
- `CacheResource_p.html`
- `proxy.html`
- `IndexBrowser_p.html`
