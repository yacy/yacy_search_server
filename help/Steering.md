---
page: htroot/Steering.html
help: help/Steering.md
title: Steering
package: monitoring-performance
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/Steering.java
---

# Steering

## Purpose

Steering is a navigation and operations hub.

Use it to reach major administration areas when diagnosing or operating a peer.

## What You Can Do Here

- Steering is a navigation and operations hub.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

Monitoring pages read live peer state from queues, logs, network tables, memory counters, or process trackers. They are safest when used first as observation tools and only then as entry points to tuning pages.

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

Backend checks: transaction token for protected POST, transaction token issued for forms, user authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/Steering.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Steering.html` | `POST` | public or page-dependent | `source/net/yacy/htroot/Steering.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `update` | Submit action that refreshes, updates, or applies the selected setting depending on the page. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /Steering.html
Content-Type: application/x-www-form-urlencoded

releaseinstall=...&restart=...&shutdown=...&update=...
```

## What To Expect

Expect observations: counts, logs, queues, timing, network rows, thread states, or resource values. Monitoring does not fix the issue by itself; it points to the next page or setting to change.

## Related Pages

- `Settings_p.html`
- `ConfigAccounts_p.html`
- `ConfigUpdate_p.html`
- `ViewLog_p.html`
