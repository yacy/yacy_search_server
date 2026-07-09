---
page: htroot/LogReports_p.html
help: help/LogReports_p.md
title: Log Reports
package: monitoring-performance
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/LogReports_p.java
---

# Log Reports

## Purpose

Log Reports summarize notable events from YaCy logs.

Use it to read operational history without scanning raw log files first.

## What You Can Do Here

- Log Reports summarize notable events from YaCy logs.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

Monitoring pages read live peer state from queues, logs, network tables, memory counters, or process trackers. They are safest when used first as observation tools and only then as entry points to tuning pages.

| Control | Meaning | Values or examples |
| --- | --- | --- |

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/LogReports_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/LogReports_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/LogReports_p.html` | `POST` | admin | `source/net/yacy/htroot/LogReports_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `deleteReport` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |

Example request shape:

```http
POST /LogReports_p.html
Content-Type: application/x-www-form-urlencoded

runReportNow=...&deleteReport=...&report=...
```

## What To Expect

Expect observations: counts, logs, queues, timing, network rows, thread states, or resource values. Monitoring does not fix the issue by itself; it points to the next page or setting to change.

## Related Pages

- `LLMSelection_p.html`
