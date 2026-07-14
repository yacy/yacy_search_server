---
page: htroot/AccessTracker_p.html
help: help/AccessTracker_p.md
title: Access Tracker
package: monitoring-performance
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/AccessTracker_p.java
---

# Access Tracker

## Purpose

Access Tracker shows recent requests handled by the peer.

Use it to understand who is using the interface or API and which paths are active.

## What You Can Do Here

- Access Tracker shows recent requests handled by the peer.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

Monitoring pages read live peer state from queues, logs, network tables, memory counters, or process trackers. They are safest when used first as observation tools and only then as entry points to tuning pages.

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/AccessTracker_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/AccessTracker_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/AccessTracker_p.html` | `GET or POST` | admin | `source/net/yacy/htroot/AccessTracker_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `host` | Host or domain scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET or POST /AccessTracker_p.html?host=...&page=...
```

## What To Expect

Expect observations: counts, logs, queues, timing, network rows, thread states, or resource values. Monitoring does not fix the issue by itself; it points to the next page or setting to change.

When YaCy is reached through a reverse proxy, Access Tracker uses `X-Real-IP`
only when the proxy's direct socket IP matches one of the comma-separated
regular expressions in `server.reverseProxy.trusted` and the header contains
one valid IPv4 or IPv6 address. Loopback proxies are trusted by default.
Authentication and access-control decisions continue to use the direct socket
IP, not the forwarded address. The reverse proxy must overwrite `X-Real-IP`
with the client address; it must not pass a client-supplied value unchanged.

## Related Pages

- `yacysearch.html`
