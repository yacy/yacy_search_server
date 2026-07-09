---
page: htroot/api/yacydoc.html
help: help/api/yacydoc.md
title: yacydoc
package: machine-api-peer
access: public
kind: api-endpoint
backend_java: source/net/yacy/htroot/api/yacydoc.java
---

# yacydoc

## Purpose

yacydoc exposes document data for tools.

Use it when an agent needs a machine-oriented view of one indexed document.

## What You Can Do Here

- Retrieve machine-readable information about one indexed document.
- Provide the exact URL, hash, or document identifier expected by the endpoint.
- Use the response as document context for tools or citations.

## Page Architecture

This is a compact endpoint-style page. Its behavior is driven mainly by request parameters and the selected response template, so callers should send only the fields needed for the specific query or peer-service action.

## Correct Use

Call the endpoint as a protocol surface. Use exact parameter names and encoded values, authenticate when required, and inspect the response before relying on it. Avoid sending browser-only submit buttons unless the backend explicitly requires the action key.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

Page backend: `source/net/yacy/htroot/api/yacydoc.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/api/yacydoc.html` | `POST` | public or page-dependent | `source/net/yacy/htroot/api/yacydoc.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `urlhash` | YaCy URL hash identifying an indexed document. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /api/yacydoc.html
Content-Type: application/x-www-form-urlencoded

url=...&urlhash=...&html=...
```

## What To Expect

Expect a compact service response rather than a teaching interface. The response may be XML, RSS, JSON-like text, plain text, or a small HTML template depending on the endpoint.

## Related Pages

- Related protocol work usually continues through the calling tool, the peer endpoint family under `/yacy/`, or the API page that consumes this response.
