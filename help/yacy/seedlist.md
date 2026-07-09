---
page: htroot/yacy/seedlist.html
help: help/yacy/seedlist.md
title: seedlist
package: machine-api-peer
access: peer-service
kind: peer-endpoint
backend_java: source/net/yacy/htroot/yacy/seedlist.java
---

# seedlist

## Purpose

seedlist publishes known peer seeds.

Use it when bootstrapping or inspecting YaCy network peer knowledge.

## What You Can Do Here

- Retrieve known peer seed information.
- Use the response for network bootstrap, peer diagnostics, or network-state inspection.
- Do not treat the seed list as user search content.

## Page Architecture

This is a compact endpoint-style page. Its behavior is driven mainly by request parameters and the selected response template, so callers should send only the fields needed for the specific query or peer-service action.

## Correct Use

Call the endpoint as a protocol surface. Use exact parameter names and encoded values, authenticate when required, and inspect the response before relying on it. Avoid sending browser-only submit buttons unless the backend explicitly requires the action key.

## Access And Safety

This is a peer-service endpoint for YaCy peer communication, not a normal editing page.

## Automation And API

Page backend: `source/net/yacy/htroot/yacy/seedlist.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/yacy/seedlist.html` | `GET or POST` | peer-service | `source/net/yacy/htroot/yacy/seedlist.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `callback` | JSONP callback name for legacy script clients. Leave empty for normal HTML or JSON-style use. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `peername` | Public peer name. Use 3 to 80 letters, digits, hyphen, or underscore; spaces are converted to hyphens. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
GET or POST /yacy/seedlist.html?peername=...&address=...&callback=...&id=...&maxcount=...
```

## What To Expect

Expect a compact service response rather than a teaching interface. The response may be XML, RSS, JSON-like text, plain text, or a small HTML template depending on the endpoint.

## Related Pages

- Related protocol work usually continues through the calling tool, the peer endpoint family under `/yacy/`, or the API page that consumes this response.
