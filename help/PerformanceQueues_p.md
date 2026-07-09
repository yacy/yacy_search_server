---
page: htroot/PerformanceQueues_p.html
help: help/PerformanceQueues_p.md
title: Performance Settings of Queues and Processes
package: monitoring-performance
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/PerformanceQueues_p.java
---

# Performance Settings of Queues and Processes

## Purpose

Queue Performance shows processing queues and worker behavior.

Use it to find bottlenecks between crawling, parsing, indexing, and response generation.

## What You Can Do Here

- Queue Performance shows processing queues and worker behavior.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

Monitoring pages read live peer state from queues, logs, network tables, memory counters, or process trackers. They are safest when used first as observation tools and only then as entry points to tuning pages.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `#[name]#_maxActive` | Enables the named feature. | Integer value. |

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/PerformanceQueues_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/PerformanceQueues_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/PerformanceQueues_p.html` | `POST` | admin | `source/net/yacy/htroot/PerformanceQueues_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `#[name]#_maxActive` | Enables the named feature. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `Crawler Pool_maxActive` | Enables the named feature. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `Robots.txt Pool_maxActive` | Enables the named feature. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlPauseRemotesearch` | User or account value. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `httpd Session Pool_maxActive` | Enables the named feature. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /PerformanceQueues_p.html
Content-Type: application/x-www-form-urlencoded

#[threadname]#_idlesleep=...&#[threadname]#_busysleep=...&#[threadname]#_memprereq=...&#[threadname]#_loadprereq=...&submitdelay=...
```

## What To Expect

Expect observations: counts, logs, queues, timing, network rows, thread states, or resource values. Monitoring does not fix the issue by itself; it points to the next page or setting to change.

## Related Pages

- Related diagnosis usually continues on the status page, log viewer, performance pages, connection tracker, queue monitor, or network view.
