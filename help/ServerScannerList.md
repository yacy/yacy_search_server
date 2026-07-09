---
page: htroot/ServerScannerList.html
help: help/ServerScannerList.md
title: Network Scanner Monitor
package: monitoring-performance
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/ServerScannerList.java
---

# Network Scanner Monitor

## Purpose

Network Scanner Monitor helps diagnose the running peer.

Monitoring pages turn the running peer into something observable. They show queues, logs, network contacts, memory, threads, access patterns, and other evidence needed to diagnose a search server.

## What You Can Do Here

- Network Scanner Monitor helps diagnose the running peer.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

Monitoring pages read live peer state from queues, logs, network tables, memory counters, or process trackers. They are safest when used first as observation tools and only then as entry points to tuning pages.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `allswitch` | Available server within the given IP range. | Available server within the given IP range |
| `item_#[count]#` | Available server within the given IP range. | `mark_` = Available server within the given IP range |
| `crawl` | Available server within the given IP range. | `Add Selected Servers to Crawler` |

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

Protected related endpoint(s): `/CrawlStartScanner_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/ServerScannerList.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/CrawlStartScanner_p.html` | `POST` | admin | `source/net/yacy/htroot/CrawlStartScanner_p.java` |
| `/ServerScannerList.html` | `GET or POST` | public or page-dependent | `source/net/yacy/htroot/ServerScannerList.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `allswitch` | Available server within the given IP range. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `item_#[count]#` | Available server within the given IP range. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `crawl` | Available server within the given IP range. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `repeat_time` | Date/time value for filtering, display, or scheduling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `scanhosts` | Host or domain scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `timeout` | Date/time value for filtering, display, or scheduling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /CrawlStartScanner_p.html
Content-Type: application/x-www-form-urlencoded

allswitch=...&item_#[count]#=...&crawl=...&accumulatescancache=...&edit=...
```

## What To Expect

Expect observations: counts, logs, queues, timing, network rows, thread states, or resource values. Monitoring does not fix the issue by itself; it points to the next page or setting to change.

## Related Pages

- Related diagnosis usually continues on the status page, log viewer, performance pages, connection tracker, queue monitor, or network view.
