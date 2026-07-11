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

Blog and Wiki categories are retired and no longer rendered as linked news entries.

Use it to understand what other peers announced or what the local peer received.

## What You Can Do Here

- News Monitor shows YaCy network news and peer events.
- Inspect incoming, processed, outgoing, and published news queues.
- Process received records, delete archived records, or stop selected outgoing publications.

## Page Architecture

The page renders records from the four peer-news queues. Each record includes its originator, category, timestamps, distribution count, and attributes.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `deletespecific` | Deletion or termination action. Use only with explicit intent. | `Process Selected News::Delete Selected News::Abort Publication of Selected News::Delete Selected News` |
| `deleteall` | Deletion or termination action. Use only with explicit intent. | `Process All News::Delete All News::Abort Publication of All News::Delete All News` |

## Correct Use

Select only the records intended for processing or deletion. Stopping an outgoing publication prevents further distribution but does not retract copies already received by other peers.

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

After an action, reload the selected queue and confirm that the intended records moved, disappeared, or stopped publishing.
