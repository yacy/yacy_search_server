---
page: htroot/PerformanceMemory_p.html
help: help/PerformanceMemory_p.md
title: Performance Settings for Memory
package: monitoring-performance
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/PerformanceMemory_p.java
---

# Performance Settings for Memory

## Purpose

Memory Performance shows memory use and related limits.

Use it when the peer slows down, swaps, or approaches heap limits.

## What You Can Do Here

- Memory Performance shows memory use and related limits.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

Monitoring pages read live peer state from queues, logs, network tables, memory counters, or process trackers. They are safest when used first as observation tools and only then as entry points to tuning pages.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `option` | refresh graph. | refresh graph |
| `simulatedshortmemory` | simulate short memory status. | simulate short memory status |
| `useStandardmemoryStrategy` | use Standard Memory Strategy. | use Standard Memory Strategy |

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/PerformanceMemory_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/PerformanceMemory_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/PerformanceMemory_p.html` | `GET` | admin | `source/net/yacy/htroot/PerformanceMemory_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `option` | refresh graph. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `simulatedshortmemory` | simulate short memory status. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `useStandardmemoryStrategy` | use Standard Memory Strategy. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `dummy` | simulate short memory status. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /PerformanceMemory_p.html?option=...&simulatedshortmemory=...&useStandardmemoryStrategy=...&dummy=...&gc=...
```

## What To Expect

Expect observations: counts, logs, queues, timing, network rows, thread states, or resource values. Monitoring does not fix the issue by itself; it points to the next page or setting to change.

## Related Pages

- Related diagnosis usually continues on the status page, log viewer, performance pages, connection tracker, queue monitor, or network view.
