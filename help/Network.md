---
page: htroot/Network.html
help: help/Network.md
title: YaCy Search Network
package: monitoring-performance
access: mixed
kind: ui-page
backend_java: source/net/yacy/htroot/Network.java
---

# YaCy Search Network

## Purpose

YaCy Search Network shows known peers and network state.

Peer rows no longer expose links to the retired peer-local Blog or Wiki applications, and their former update announcements are not displayed.

Use it to understand whether this peer is isolated, connected, senior, junior, or participating normally.

## What You Can Do Here

- YaCy Search Network shows known peers and network state.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

Monitoring pages read live peer state from queues, logs, network tables, memory counters, or process trackers. They are safest when used first as observation tools and only then as entry points to tuning pages.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `match` | Search for a peername (RegExp allowed). | Pattern or filter expression; test narrow expressions before broad use. |
| `search` | Alternative query parameter. | `Search` |
| `peerHash` | Search for a peername (RegExp allowed). | Text value; use the page label and surrounding context to choose the exact content. |
| `peerIP` | Search for a peername (RegExp allowed). | Text value; use the page label and surrounding context to choose the exact content. |
| `peerPort` | Search for a peername (RegExp allowed). | Text value; use the page label and surrounding context to choose the exact content. |
| `addPeer` | Search for a peername (RegExp allowed). | `add Peer` |

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

The page may be visible, but the backend performs authentication checks for protected actions.

Protected related endpoint(s): `/Network.html`.

Backend checks: administrator authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/Network.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Network.html` | `GET` | mixed | `source/net/yacy/htroot/Network.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `match` | Search for a peername (RegExp allowed). | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `search` | Alternative search text parameter accepted by some search endpoints; prefer `query` on browser search pages unless reproducing an existing URL. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `peerHash` | Search for a peername (RegExp allowed). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `peerIP` | Search for a peername (RegExp allowed). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `peerPort` | Search for a peername (RegExp allowed). | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `addPeer` | Search for a peername (RegExp allowed). | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `page` | Search for a peername (RegExp allowed). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `sort` | Sort field or sort direction. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |

Example request shape:

```http
GET /Network.html?match=...&search=...&peerHash=...&peerIP=...&peerPort=...
```

## What To Expect

Expect observations: counts, logs, queues, timing, network rows, thread states, or resource values. Monitoring does not fix the issue by itself; it points to the next page or setting to change.

## Related Pages

- `MessageSend_p.html`
- `ViewProfile.html`
- `goto_p.html`
