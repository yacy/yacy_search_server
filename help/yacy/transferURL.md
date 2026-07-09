---
page: htroot/yacy/transferURL.html
help: help/yacy/transferURL.md
title: transferURL
package: machine-api-peer
access: peer-service
kind: peer-endpoint
backend_java: source/net/yacy/htroot/yacy/transferURL.java
---

# transferURL

## Purpose

transferURL exchanges URL metadata between peers.

Use it when YaCy peers share crawl or index knowledge.

## What You Can Do Here

- Exchange URL metadata with another peer.
- Keep hashes and URL identifiers exact.
- Use the response to support peer index synchronization.

## Page Architecture

This is a compact endpoint-style page. Its behavior is driven mainly by request parameters and the selected response template, so callers should send only the fields needed for the specific query or peer-service action.

## Correct Use

Call the endpoint as a protocol surface. Use exact parameter names and encoded values, authenticate when required, and inspect the response before relying on it. Avoid sending browser-only submit buttons unless the backend explicitly requires the action key.

## Access And Safety

This is a peer-service endpoint for YaCy peer communication, not a normal editing page.

## Automation And API

Page backend: `source/net/yacy/htroot/yacy/transferURL.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/yacy/transferURL.html` | `GET or POST` | peer-service | `source/net/yacy/htroot/yacy/transferURL.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET or POST /yacy/transferURL.html?url=...&iam=...&key=...&urlc=...&youare=...
```

## What To Expect

Expect a compact service response rather than a teaching interface. The response may be XML, RSS, JSON-like text, plain text, or a small HTML template depending on the endpoint.

## Related Pages

- Related protocol work usually continues through the calling tool, the peer endpoint family under `/yacy/`, or the API page that consumes this response.
