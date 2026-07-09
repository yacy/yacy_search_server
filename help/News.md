---
page: htroot/News.html
help: help/News.md
title: News Monitor
package: content-apps
access: mixed
kind: ui-page
backend_java: source/net/yacy/htroot/News.java
---

# News Monitor

## Purpose

News Monitor shows YaCy network news and peer events.

Use it to understand what other peers announced or what the local peer received.

## What You Can Do Here

- News Monitor shows YaCy network news and peer events.
- Edit or inspect the specific content object shown on the page.
- Check whether the result is local-only, user-visible, or peer-visible before publishing.

## Page Architecture

Content application pages store and render peer-local objects such as bookmarks, messages, wiki text, tables, profiles, or translations. They combine form editing with a rendered view of the stored object.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `deletespecific` | Deletion or termination action. Use only with explicit intent. | `Process Selected News::Delete Selected News::Abort Publication of Selected News::Delete Selected News` |
| `deleteall` | Deletion or termination action. Use only with explicit intent. | `Process All News::Delete All News::Abort Publication of All News::Delete All News` |

## Correct Use

Edit content deliberately and check the rendered page after saving. For shared or peer-visible features, assume written content may be read by someone else unless the page clearly says otherwise.

## Access And Safety

The page may be visible, but the backend performs authentication checks for protected actions.

Protected related endpoint(s): `/News.html`.

Backend checks: administrator authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/News.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/News.html` | `POST` | mixed | `source/net/yacy/htroot/News.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `deletespecific` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteall` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |

Example request shape:

```http
POST /News.html
Content-Type: application/x-www-form-urlencoded

deletespecific=...&deleteall=...&del_#[id]#=...&page=...
```

## What To Expect

Expect stored or rendered content: a message, page, table row, bookmark, profile, translation, or file view. After changes, reload or revisit the object to confirm what was actually saved.

## Related Pages

- `Translator_p.html`
