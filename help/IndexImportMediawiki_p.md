---
page: htroot/IndexImportMediawiki_p.html
help: help/IndexImportMediawiki_p.md
title: MediaWiki Dump Import
package: import-export-federation
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexImportMediawiki_p.java
---

# MediaWiki Dump Import

## Purpose

MediaWiki Dump Import turns wiki dump content into searchable documents.

Use it to build a search index for a wiki without crawling every page through HTTP.

## What You Can Do Here

- MediaWiki Dump Import turns wiki dump content into searchable documents.
- Define the source, target, format, collection, and expected size before starting.
- Monitor progress because large transfers continue beyond the initial request.

## Page Architecture

Import and export pages translate external data formats into YaCy documents or move YaCy index data into another store. Most long-running actions create background work that should be monitored afterward.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `file` | Selected or uploaded file. | Text value; use the page label and surrounding context to choose the exact content. |
| `iffresh` | Import only when modified since last import. | Import only when modified since last import |
| `submit` | Submits the form. | `Import MediaWiki Dump` |

## Correct Use

Prepare source and target details before starting: file path or URL, format, collection, credentials if needed, and expected size. Imports and exports can continue in the background, so confirm progress on the related monitor or queue page.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexImportMediawiki_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexImportMediawiki_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexImportMediawiki_p.html` | `POST` | admin | `source/net/yacy/htroot/IndexImportMediawiki_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `file` | Selected or uploaded file. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `iffresh` | Import only when modified since last import. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `submit` | Submit action for the form. Its meaning depends on the surrounding fields. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
POST /IndexImportMediawiki_p.html
Content-Type: application/x-www-form-urlencoded

file=...&iffresh=...&submit=...&report=...
```

## What To Expect

Small operations may finish during the request; large imports, exports, harvests, and package operations usually need monitoring. Expect progress, logs, queue entries, or generated files rather than instant final search quality.

## Related Pages

- `https://dumps.wikimedia.org/backup-index-bydb.html`
- `Automation_p.html`
- `CrawlResults.html`
