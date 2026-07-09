---
page: htroot/IndexExportImportSolr_p.html
help: help/IndexExportImportSolr_p.md
title: URL Database Administration
package: import-export-federation
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexExportImportSolr_p.java
---

# URL Database Administration

## Purpose

Solr Index Export/Import moves YaCy index data between this peer and Solr dump formats.

Use it when preserving or restoring the Solr-backed part of the index.

## What You Can Do Here

- Solr Index Export/Import moves YaCy index data between this peer and Solr dump formats.
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

Protected related endpoint(s): `/IndexExportImportSolr_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexExportImportSolr_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexExportImportSolr_p.html` | `POST` | admin | `source/net/yacy/htroot/IndexExportImportSolr_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |

Example request shape:

```http
POST /IndexExportImportSolr_p.html
Content-Type: application/x-www-form-urlencoded

indexdump=...&dumpfile=...&indexrestore=...
```

## What To Expect

Small operations may finish during the request; large imports, exports, harvests, and package operations usually need monitoring. Expect progress, logs, queue entries, or generated files rather than instant final search quality.

## Related Pages

- Related transfer work is usually reached through the import/export page for the same format, the index-pack pages, or the queue/status page that reports progress.
