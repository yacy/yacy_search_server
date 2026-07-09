---
page: htroot/ViewLog_p.html
help: help/ViewLog_p.md
title: Server Log
package: monitoring-performance
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ViewLog_p.java
---

# Server Log

## Purpose

Server Log displays raw log lines with filtering.

Use it when summaries are not enough and the exact sequence of events matters.

## What You Can Do Here

- Server Log displays raw log lines with filtering.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

Monitoring pages read live peer state from queues, logs, network tables, memory counters, or process trackers. They are safest when used first as observation tools and only then as entry points to tuning pages.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `lines` | Maximum number of log or text lines to display. | Integer maximum line count. |
| `mode` | reversed order. | `reversed` = reversed order |
| `filterMode` | Lines (max. ). Options: `regex`, `terms`. | `regex`, `terms` |
| `filter` | Filter text or expression used to narrow the displayed records. | Pattern or filter expression; test narrow expressions before broad use. |

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ViewLog_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/ViewLog_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ViewLog_p.html` | `GET` | admin | `source/net/yacy/htroot/ViewLog_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `lines` | Maximum number of log or text lines to display. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `mode` | Display or processing mode selected by the page. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `filterMode` | Filter interpretation mode, for example plain terms or regular expression when available. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `filter` | Filter text or expression used to narrow the displayed records. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |

Example request shape:

```http
GET /ViewLog_p.html?lines=...&mode=...&filterMode=...&filter=...&json=...
```

## What To Expect

Expect observations: counts, logs, queues, timing, network rows, thread states, or resource values. Monitoring does not fix the issue by itself; it points to the next page or setting to change.

## Related Pages

- Related diagnosis usually continues on the status page, log viewer, performance pages, connection tracker, queue monitor, or network view.
