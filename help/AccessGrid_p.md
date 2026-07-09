---
page: htroot/AccessGrid_p.html
help: help/AccessGrid_p.md
title: YaCy Network Access
package: monitoring-performance
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/AccessGrid_p.java
---

# YaCy Network Access

## Purpose

YaCy Network Access visualizes or lists network-facing access information.

Use it to understand how peer access appears across the YaCy network.

## What You Can Do Here

- YaCy Network Access visualizes or lists network-facing access information.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

Monitoring pages read live peer state from queues, logs, network tables, memory counters, or process trackers. They are safest when used first as observation tools and only then as entry points to tuning pages.

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

## Automation And API

Page backend: `source/net/yacy/htroot/AccessGrid_p.java`.

No request parameters are needed for normal use of this page.

## What To Expect

Expect observations: counts, logs, queues, timing, network rows, thread states, or resource values. Monitoring does not fix the issue by itself; it points to the next page or setting to change.

## Related Pages

- Related diagnosis usually continues on the status page, log viewer, performance pages, connection tracker, queue monitor, or network view.
