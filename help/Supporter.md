---
page: htroot/Supporter.html
help: help/Supporter.md
title: Supporter
package: core-search-public
access: mixed
kind: ui-page
backend_java: source/net/yacy/htroot/Supporter.java
---

# Supporter

## Purpose

Supporter presents ways to support the YaCy project.

Use it as informational project context, not as a search or administration tool.

## What You Can Do Here

- Supporter presents ways to support the YaCy project.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

## Correct Use

Start from the user's information need. Use query text, content type, collection, URL filters, and pagination to narrow results. Do not mix ordinary search with authenticated result actions unless the user explicitly asks to modify stored data.

## Access And Safety

The page may be visible, but the backend performs authentication checks for protected actions.

Protected related endpoint(s): `/Supporter.html`.

Backend checks: administrator authentication, user authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/Supporter.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Supporter.html` | `GET or POST` | mixed | `source/net/yacy/htroot/Supporter.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `description` | Description text. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `title` | Human-readable title. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET or POST /Supporter.html?url=...&comment=...&description=...&display=...&refid=...
```

## What To Expect

Expect rendered search or content output: result lists, snippets, previews, redirects, widgets, or fragments. If output is empty, check whether the index contains matching documents before changing query syntax.

## Related Pages

- `Bookmarks.html`
