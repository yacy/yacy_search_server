---
page: htroot/IndexImportZim_p.html
help: help/IndexImportZim_p.md
title: ZIM File Import
package: import-export-federation
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexImportZim_p.java
---

# ZIM File Import

## Purpose

ZIM File Import reads offline web/wiki archives into YaCy.

Use it to make a ZIM collection searchable without external network access.

## What You Can Do Here

- ZIM File Import reads offline web/wiki archives into YaCy.
- Define the source, target, format, collection, and expected size before starting.
- Monitor progress because large transfers continue beyond the initial request.

## Page Architecture

Import and export pages translate external data formats into YaCy documents or move YaCy index data into another store. Most long-running actions create background work that should be monitored afterward.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `file` | Selected or uploaded file. | Text value; use the page label and surrounding context to choose the exact content. |
| `collection` | Collection name used to group indexed documents. | `user` |
| `submit` | Submits the form. | `Import ZIM File` |
| `abort` | File. | `Stop` |

## Correct Use

Prepare source and target details before starting: file path or URL, format, collection, credentials if needed, and expected size. Imports and exports can continue in the background, so confirm progress on the related monitor or queue page.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexImportZim_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexImportZim_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexImportZim_p.html` | `POST` | admin | `source/net/yacy/htroot/IndexImportZim_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `file` | Selected or uploaded file. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `collection` | Collection name. Use it to group crawled or imported documents and to search or manage that group later. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `submit` | Submit action for the form. Its meaning depends on the surrounding fields. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `abort` | File. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /IndexImportZim_p.html
Content-Type: application/x-www-form-urlencoded

file=...&collection=...&submit=...&abort=...
```

## What To Expect

Small operations may finish during the request; large imports, exports, harvests, and package operations usually need monitoring. Expect progress, logs, queue entries, or generated files rather than instant final search quality.

## Related Pages

- Related transfer work is usually reached through the import/export page for the same format, the index-pack pages, or the queue/status page that reports progress.
