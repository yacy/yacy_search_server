---
page: htroot/IndexPackDownloader_p.html
help: help/IndexPackDownloader_p.md
title: Index Pack Downloader
package: import-export-federation
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexPackDownloader_p.java
---

# Index Pack Downloader

## Purpose

Index Pack Downloader retrieves packaged YaCy index data.

Use it when a prepared index package should be installed instead of crawling from scratch.

## What You Can Do Here

- Index Pack Downloader retrieves packaged YaCy index data.
- Define the source, target, format, collection, and expected size before starting.
- Monitor progress because large transfers continue beyond the initial request.

## Page Architecture

Import and export pages translate external data formats into YaCy documents or move YaCy index data into another store. Most long-running actions create background work that should be monitored afterward.

## Correct Use

Prepare source and target details before starting: file path or URL, format, collection, credentials if needed, and expected size. Imports and exports can continue in the background, so confirm progress on the related monitor or queue page.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexPackDownloader_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexPackDownloader_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexPackDownloader_p.html` | `GET or POST` | admin | `source/net/yacy/htroot/IndexPackDownloader_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |

Example request shape:

```http
GET or POST /IndexPackDownloader_p.html?dlfile=...&dlrepoid=...&dlsource=...
```

## What To Expect

Small operations may finish during the request; large imports, exports, harvests, and package operations usually need monitoring. Expect progress, logs, queue entries, or generated files rather than instant final search quality.

## Related Pages

- Related transfer work is usually reached through the import/export page for the same format, the index-pack pages, or the queue/status page that reports progress.
