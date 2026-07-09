---
page: htroot/WatchWebStructure_p.html
help: help/WatchWebStructure_p.md
title: Web Structure
package: monitoring-performance
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/WatchWebStructure_p.java
---

# Web Structure

## Purpose

Web Structure visualizes host and link relationships learned by crawling.

Use it to see the shape of the crawled web rather than individual documents.

## What You Can Do Here

- Web Structure visualizes host and link relationships learned by crawling.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

Monitoring pages read live peer state from queues, logs, network tables, memory counters, or process trackers. They are safest when used first as observation tools and only then as entry points to tuning pages.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `host` | Host or domain scope. | Text value; use the page label and surrounding context to choose the exact content. |
| `time` | Date/time value for filtering, display, or scheduling. | Text value; use the page label and surrounding context to choose the exact content. |

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/WatchWebStructure_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/WatchWebStructure_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/WatchWebStructure_p.html` | `GET` | admin | `source/net/yacy/htroot/WatchWebStructure_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `host` | Host or domain scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `time` | Date/time value for filtering, display, or scheduling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `hosts` | Host list filter. Values such as `crawling` or `error` restrict the host overview to active crawl hosts or error hosts when supported. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /WatchWebStructure_p.html?host=...&depth=...&nodes=...&time=...&width=...
```

## What To Expect

Expect observations: counts, logs, queues, timing, network rows, thread states, or resource values. Monitoring does not fix the issue by itself; it points to the next page or setting to change.

## Related Pages

- Related diagnosis usually continues on the status page, log viewer, performance pages, connection tracker, queue monitor, or network view.
