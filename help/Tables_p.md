---
page: htroot/Tables_p.html
help: help/Tables_p.md
title: Table Viewer
package: content-apps
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/Tables_p.java
---

# Table Viewer

## Purpose

Table Viewer displays stored table data.

Use it to inspect structured records saved by YaCy features or APIs.

## What You Can Do Here

- Table Viewer displays stored table data.
- Edit or inspect the specific content object shown on the page.
- Check whether the result is local-only, user-visible, or peer-visible before publishing.

## Page Architecture

Content application pages store and render peer-local objects such as bookmarks, messages, tables, profiles, or translations. They combine form editing with a rendered view of the stored object.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `count` | Number of records requested. | `10`, `100`, `1000`, `all` |
| `search` | Alternative search text parameter accepted by some search endpoints; prefer `query` on browser search pages unless reproducing an existing URL. | Text value; use the page label and surrounding context to choose the exact content. |
| `item_#[count]#` | Choice value. Options: `pk_`. | `pk_` |
| `deleterows` | Deletion or termination action. Use only with explicit intent. | `Delete Selected Rows` |
| `deletetable` | Deletion or termination action. Use only with explicit intent. | `Delete Table` |

## Correct Use

Edit content deliberately and check the rendered page after saving. For shared or peer-visible features, assume written content may be read by someone else unless the page clearly says otherwise.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/Tables_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/Tables_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Tables_p.html` | `GET` | admin | `source/net/yacy/htroot/Tables_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `count` | SRU-style result count. It is an alternative to `maximumRecords` on search endpoints. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `search` | Alternative search text parameter accepted by some search endpoints; prefer `query` on browser search pages unless reproducing an existing URL. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `item_#[count]#` | Choice value. Options: `pk_`. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `deleterows` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deletetable` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |

Example request shape:

```http
GET /Tables_p.html?table=...&count=...&reverse=...&search=...&edittable=...
```

## What To Expect

Expect stored or rendered content: a message, page, table row, bookmark, profile, translation, or file view. After changes, reload or revisit the object to confirm what was actually saved.

## Related Pages

- Related content work usually continues on the matching bookmark, message, table, translation, vocabulary, or file page.
