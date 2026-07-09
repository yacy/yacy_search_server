---
page: htroot/Performance_p.html
help: help/Performance_p.md
title: Performance Settings
package: monitoring-performance
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/Performance_p.java
---

# Performance Settings

## Purpose

Performance Settings collect runtime tuning controls.

Use it when the peer needs different resource behavior after observing actual load.

## What You Can Do Here

- Performance Settings collect runtime tuning controls.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

Monitoring pages read live peer state from queues, logs, network tables, memory counters, or process trackers. They are safest when used first as observation tools and only then as entry points to tuning pages.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `option` | refresh graph. | refresh graph |
| `Xmx` | Memory reserved for JVM. | Text value; use the page label and surrounding context to choose the exact content. |
| `setStartup` | Memory reserved for JVM. | `Set` |
| `diskFree` | Steady-state minimum. | Text value; use the page label and surrounding context to choose the exact content. |
| `diskFreeHardlimit` | Absolute minimum. | Text value; use the page label and surrounding context to choose the exact content. |
| `diskFreeAutoregulate` | Autoregulate. | Autoregulate |
| `diskUsed` | Steady-state maximum. | Text value; use the page label and surrounding context to choose the exact content. |
| `diskUsedHardlimit` | Absolute maximum. | Text value; use the page label and surrounding context to choose the exact content. |
| `diskUsedAutoregulate` | Autoregulate. | Autoregulate |
| `resetObserver` | Minimum required. | `Reset state` |
| `memoryAcceptDHT` | Minimum required. | Text value; use the page label and surrounding context to choose the exact content. |
| `setObserver` | Steady-state minimum. | `Save` |
| `crawlPauseRemotesearch` | User or account value. | Text value; use the page label and surrounding context to choose the exact content. |

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/Performance_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms, user authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/Performance_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Performance_p.html` | `GET` | admin | `source/net/yacy/htroot/Performance_p.java` |
| `/Steering.html` | `POST` | public or page-dependent | `source/net/yacy/htroot/Steering.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `option` | refresh graph. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `Xmx` | Memory reserved for JVM. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `setStartup` | Memory reserved for JVM. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `diskFree` | Steady-state minimum. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `diskFreeHardlimit` | Absolute minimum. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `diskFreeAutoregulate` | Autoregulate. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `diskUsed` | Steady-state maximum. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `diskUsedHardlimit` | Absolute maximum. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `diskUsedAutoregulate` | Autoregulate. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `resetObserver` | Minimum required. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `memoryAcceptDHT` | Minimum required. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `setObserver` | Steady-state minimum. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `crawlPauseRemotesearch` | User or account value. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `restart` | refresh graph. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `update` | Submit action that refreshes, updates, or applies the selected setting depending on the page. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /Performance_p.html?option=...&Xmx=...&setStartup=...&diskFree=...&diskFreeHardlimit=...
```

## What To Expect

Expect observations: counts, logs, queues, timing, network rows, thread states, or resource values. Monitoring does not fix the issue by itself; it points to the next page or setting to change.

## Related Pages

- Related diagnosis usually continues on the status page, log viewer, performance pages, connection tracker, queue monitor, or network view.
