---
page: htroot/Threaddump_p.html
help: help/Threaddump_p.md
title: Debugging: Thread Dump
package: monitoring-performance
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/Threaddump_p.java
---

# Debugging: Thread Dump

## Purpose

Thread Dump captures Java thread state.

Use it for debugging deadlocks, stalls, or high CPU situations.

## What You Can Do Here

- Thread Dump captures Java thread state.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

Monitoring pages read live peer state from queues, logs, network tables, memory counters, or process trackers. They are safest when used first as observation tools and only then as entry points to tuning pages.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `count` | SRU-style result count. It is an alternative to `maximumRecords` on search endpoints. | Integer value. |

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/Threaddump_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/Threaddump_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Threaddump_p.html` | `GET` | admin | `source/net/yacy/htroot/Threaddump_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `count` | SRU-style result count. It is an alternative to `maximumRecords` on search endpoints. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |

Example request shape:

```http
GET /Threaddump_p.html?singleThreaddump=...&count=...&multipleThreaddump=...&plain=...&sleep=...
```

## What To Expect

Expect observations: counts, logs, queues, timing, network rows, thread states, or resource values. Monitoring does not fix the issue by itself; it points to the next page or setting to change.

## Related Pages

- Related diagnosis usually continues on the status page, log viewer, performance pages, connection tracker, queue monitor, or network view.
