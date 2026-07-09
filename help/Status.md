---
page: htroot/Status.html
help: help/Status.md
title: Console Status
package: monitoring-performance
access: mixed
kind: ui-page
backend_java: source/net/yacy/htroot/Status.java
---

# Console Status

## Purpose

Console Status is the operational dashboard for the peer.

Use it first to see identity, uptime, network position, indexing state, and important warnings.

## What You Can Do Here

- Console Status is the operational dashboard for the peer.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

Monitoring pages read live peer state from queues, logs, network tables, memory counters, or process trackers. They are safest when used first as observation tools and only then as entry points to tuning pages.

| Control | Meaning | Values or examples |
| --- | --- | --- |

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

The page may be visible, but the backend performs authentication checks for protected actions.

Protected related endpoint(s): `/Status.html`.

Backend checks: administrator authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/Status.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Status.html` | `GET` | mixed | `source/net/yacy/htroot/Status.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |

Example request shape:

```http
GET /Status.html?aquirerelease=...&ResetTraffic=...&continueCrawlJob=...&jobType=...&login=...
```

## What To Expect

Expect observations: counts, logs, queues, timing, network rows, thread states, or resource values. Monitoring does not fix the issue by itself; it points to the next page or setting to change.

## Related Pages

- `ConfigAccounts_p.html`
- `index.html`
- `CrawlStartSite.html`
- `Crawler_p.html`
