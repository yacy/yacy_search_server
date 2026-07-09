---
page: htroot/IndexExport_p.html
help: help/IndexExport_p.md
title: URL Database Administration
package: import-export-federation
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexExport_p.java
---

# URL Database Administration

## Purpose

URL Database Export writes selected index data to an external file.

Use it for backup, migration, or analysis of a chosen part of the index.

## What You Can Do Here

- Export selected URL database records for backup, migration, or offline inspection.
- Choose the output format according to the tool that will read the export.
- Use `exportfilter` or related query controls to avoid exporting more of the index than needed.

## Page Architecture

Import and export pages translate external data formats into YaCy documents or move YaCy index data into another store. Most long-running actions create background work that should be monitored afterward.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `exportfilter` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | `.*.*` |
| `minified` | Choice value. Options: `no`, `yes`. | `no`, `yes` |
| `format` | Choice value. Options: `url-text`, `url-html`, `dom-text`, `dom-html`, `text-text`. | `url-text`, `url-html`, `dom-text`, `dom-html`, `text-text` |

## Correct Use

Decide what the export is for before starting. A human-readable URL list, an HTML view, and a text export serve different follow-up tools. Keep filters narrow for experiments; export broad scopes only when disk space and runtime are acceptable.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexExport_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexExport_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexExport_p.html` | `POST` | admin | `source/net/yacy/htroot/IndexExport_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `exportfilter` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `minified` | Choice value. Options: `no`, `yes`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `format` | Choice value. Options: `url-text`, `url-html`, `dom-text`, `dom-html`, `text-text`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /IndexExport_p.html
Content-Type: application/x-www-form-urlencoded

exportfilepath=...&exportfilter=...&exportquery=...&exportmaxseconds=...&maxchunksize=...
```

## What To Expect

A successful export creates or updates an export file or background export job. Verify the generated file and record count before deleting, moving, or relying on the exported data.

## Related Pages

- Related transfer work is usually reached through the import/export page for the same format, the index-pack pages, or the queue/status page that reports progress.
