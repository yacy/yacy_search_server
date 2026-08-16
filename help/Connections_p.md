---
page: htroot/Connections_p.html
help: help/Connections_p.md
title: Connection and Request Tracking
package: monitoring-performance
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/Connections_p.java
---

# Connection and Request Tracking

## Purpose

Connection and Request Tracking shows active incoming HTTP requests separately from active outgoing client connections.

Use it when requests appear stuck, slow, or unexpectedly numerous, or when outgoing HTTP activity needs inspection.

## What You Can Do Here

- Incoming HTTP Requests contains one row per request currently executing, including concurrent requests sharing one keep-alive connection.
- Outgoing Connections contains YaCy client connections tracked by the outgoing HTTP client.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

Monitoring pages read live peer state from queues, logs, network tables, memory counters, or process trackers. They are safest when used first as observation tools and only then as entry points to tuning pages.

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

## Automation And API

Page backend: `source/net/yacy/htroot/Connections_p.java`.

No request parameters are needed for normal use of this page. The page refreshes every three seconds.

## What To Expect

The incoming count falls when synchronous processing returns or asynchronous processing completes, times out, or fails. Its displayed limit applies to remote requests; local requests are displayed but exempt from rejection. This count is not the number of TCP connections or servlet sessions.

## Related Pages

- Related diagnosis usually continues on the status page, log viewer, performance pages, connection tracker, queue monitor, or network view.
