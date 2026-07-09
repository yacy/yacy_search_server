---
page: htroot/ContentIntegrationPHPBB3_p.html
help: help/ContentIntegrationPHPBB3_p.md
title: Content Integration: Retrieval from phpBB3 Databases
package: import-export-federation
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ContentIntegrationPHPBB3_p.java
---

# Content Integration: Retrieval from phpBB3 Databases

## Purpose

phpBB3 content integration imports forum data into the index.

Use it to make discussions searchable without relying only on crawler traversal.

## What You Can Do Here

- phpBB3 content integration imports forum data into the index.
- Define the source, target, format, collection, and expected size before starting.
- Monitor progress because large transfers continue beyond the initial request.

## Page Architecture

Import and export pages translate external data formats into YaCy documents or move YaCy index data into another store. Most long-running actions create background work that should be monitored afterward.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `content.phpbb3.dbhost` | Host or domain scope. | Text value; use the page label and surrounding context to choose the exact content. |
| `content.phpbb3.dbuser` | User or account value. | Text value; use the page label and surrounding context to choose the exact content. |

## Correct Use

Prepare source and target details before starting: file path or URL, format, collection, credentials if needed, and expected size. Imports and exports can continue in the background, so confirm progress on the related monitor or queue page.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ContentIntegrationPHPBB3_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/ContentIntegrationPHPBB3_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ContentIntegrationPHPBB3_p.html` | `GET` | admin | `source/net/yacy/htroot/ContentIntegrationPHPBB3_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `content.phpbb3.dbhost` | Host or domain scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `content.phpbb3.dbuser` | User or account value. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /ContentIntegrationPHPBB3_p.html?content.phpbb3.urlstub=...&content.phpbb3.dbtype=...&content.phpbb3.dbhost=...&content.phpbb3.dbport=...&content.phpbb3.dbname=...
```

## What To Expect

Small operations may finish during the request; large imports, exports, harvests, and package operations usually need monitoring. Expect progress, logs, queue entries, or generated files rather than instant final search quality.

## Related Pages

- Related transfer work is usually reached through the import/export page for the same format, the index-pack pages, or the queue/status page that reports progress.
