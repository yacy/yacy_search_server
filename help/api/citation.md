---
page: htroot/api/citation.html
help: help/api/citation.md
title: Document Citations for url
package: machine-api-peer
access: public
kind: api-endpoint
backend_java: source/net/yacy/htroot/api/citation.java
---

# Document Citations for url

## Purpose

Document Citations returns citation information for a URL or document.

Use it when a tool needs references around one indexed document.

## What You Can Do Here

- Retrieve citation data for one URL or document.
- Submit the exact document target.
- Use the response to explain or reference indexed material.

## Page Architecture

Machine/API pages are compact endpoints. Their architecture is request-parameter driven: callers provide the smallest needed set of arguments and consume a direct response rather than a guided administration workflow.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `filter` | List of ::Cited Sentences in. | `true::false` |

## Correct Use

Call the endpoint as a protocol surface. Use exact parameter names and encoded values, authenticate when required, and inspect the response before relying on it. Avoid sending browser-only submit buttons unless the backend explicitly requires the action key.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

Page backend: `source/net/yacy/htroot/api/citation.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/api/citation.html` | `POST` | public or page-dependent | `source/net/yacy/htroot/api/citation.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `filter` | Filter text or expression used to narrow the displayed records. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `hash` | YaCy hash identifier for a peer, URL, row, or stored object. Use exact values copied from YaCy output. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /api/citation.html
Content-Type: application/x-www-form-urlencoded

url=...&filter=...&ch=...&hash=...
```

## What To Expect

Expect a compact service response rather than a teaching interface. The response may be XML, RSS, JSON-like text, plain text, or a small HTML template depending on the endpoint.

## Related Pages

- Related protocol work usually continues through the calling tool, the peer endpoint family under `/yacy/`, or the API page that consumes this response.
