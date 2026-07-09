---
page: htroot/IndexImportOAIPMHList_p.html
help: help/IndexImportOAIPMHList_p.md
title: OAI-PMH source import list
package: import-export-federation
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexImportOAIPMHList_p.java
---

# OAI-PMH source import list

## Purpose

OAI-PMH Source Import List manages known OAI-PMH sources.

Use it to organize repository endpoints before harvesting records.

## What You Can Do Here

- OAI-PMH Source Import List manages known OAI-PMH sources.
- Define the source, target, format, collection, and expected size before starting.
- Monitor progress because large transfers continue beyond the initial request.

## Page Architecture

Import and export pages translate external data formats into YaCy documents or move YaCy index data into another store. Most long-running actions create background work that should be monitored afterward.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `item_#[count]#` | Choice value. Options: `mark_`. | `mark_` |

## Correct Use

Prepare source and target details before starting: file path or URL, format, collection, credentials if needed, and expected size. Imports and exports can continue in the background, so confirm progress on the related monitor or queue page.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexImportOAIPMH_p.html`, `/IndexImportOAIPMHList_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexImportOAIPMHList_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexImportOAIPMH_p.html` | `POST` | admin | `source/net/yacy/htroot/IndexImportOAIPMH_p.java` |
| `/IndexImportOAIPMHList_p.html` | `GET or POST` | admin | `source/net/yacy/htroot/IndexImportOAIPMHList_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `item_#[count]#` | Choice value. Options: `mark_`. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `agentName` | Crawler user-agent profile used for outgoing HTTP requests. Choose a profile that matches the desired identity and politeness behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /IndexImportOAIPMH_p.html
Content-Type: application/x-www-form-urlencoded

loadrows=...&allswitch=...&item_#[count]#=...&agentName=...&getlist=...
```

## What To Expect

Small operations may finish during the request; large imports, exports, harvests, and package operations usually need monitoring. Expect progress, logs, queue entries, or generated files rather than instant final search quality.

## Related Pages

- Related transfer work is usually reached through the import/export page for the same format, the index-pack pages, or the queue/status page that reports progress.
