---
page: htroot/IndexShare_p.html
help: help/IndexShare_p.md
title: Index Sharing
package: import-export-federation
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexShare_p.java
---

# Index Sharing

## Purpose

Index Sharing controls how this peer shares index information with others.

Use it to decide what local search knowledge may leave the peer.

## What You Can Do Here

- Index Sharing controls how this peer shares index information with others.
- Define the source, target, format, collection, and expected size before starting.
- Monitor progress because large transfers continue beyond the initial request.

## Page Architecture

Import and export pages translate external data formats into YaCy documents or move YaCy index data into another store. Most long-running actions create background work that should be monitored afterward.

| Control | Meaning | Values or examples |
| --- | --- | --- |

## Correct Use

Prepare source and target details before starting: file path or URL, format, collection, credentials if needed, and expected size. Imports and exports can continue in the background, so confirm progress on the related monitor or queue page.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexShare_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexShare_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexShare_p.html` | `GET` | admin | `source/net/yacy/htroot/IndexShare_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |

Example request shape:

```http
GET /IndexShare_p.html?distribute=...&receive=...&linkfreq=...&wordfreq=...&indexsharesetting=...
```

## What To Expect

Small operations may finish during the request; large imports, exports, harvests, and package operations usually need monitoring. Expect progress, logs, queue entries, or generated files rather than instant final search quality.

## Related Pages

- Related transfer work is usually reached through the import/export page for the same format, the index-pack pages, or the queue/status page that reports progress.
