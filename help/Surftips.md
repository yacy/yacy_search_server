---
page: htroot/Surftips.html
help: help/Surftips.md
title: Surftips
package: core-search-public
access: mixed
kind: ui-page
backend_java: source/net/yacy/htroot/Surftips.java
---

# Surftips

## Purpose

Surftips displays shared or suggested links.

Retired Blog and Wiki peer announcements are no longer converted into surftips.

Use it to browse peer-provided recommendations when the feature is enabled.

## What You Can Do Here

- Surftips displays shared or suggested links.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

| Control | Meaning | Values or examples |
| --- | --- | --- |

## Correct Use

Start from the user's information need. Use query text, content type, collection, URL filters, and pagination to narrow results. Do not mix ordinary search with authenticated result actions unless the user explicitly asks to modify stored data.

## Access And Safety

The page may be visible, but the backend performs authentication checks for protected actions.

Protected related endpoint(s): `/Surftips.html`.

Backend checks: administrator authentication, user authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/Surftips.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Surftips.html` | `GET` | mixed | `source/net/yacy/htroot/Surftips.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `description` | Description text. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `title` | Human-readable title. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /Surftips.html?url=...&publicPage=...&comment=...&description=...&display=...
```

## What To Expect

Expect rendered search or content output: result lists, snippets, previews, redirects, widgets, or fragments. If output is empty, check whether the index contains matching documents before changing query syntax.

## Related Pages

- `Bookmarks.html`
- `Supporter.html`
