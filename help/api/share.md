---
page: htroot/api/share.html
help: help/api/share.md
title: File Share
package: machine-api-peer
access: public
kind: api-endpoint
backend_java: source/net/yacy/htroot/api/share.java
---

# File Share

## Purpose

File Share exposes shared file content through an API-style page.

Use it when another tool needs to retrieve a shared object from the peer.

## What You Can Do Here

- Retrieve or expose shared file content.
- Use exact share identifiers and access parameters.
- Confirm that shared material is intended to be visible.

## Page Architecture

Machine/API pages are compact endpoints. Their architecture is request-parameter driven: callers provide the smallest needed set of arguments and consume a direct response rather than a guided administration workflow.

| Control | Meaning | Values or examples |
| --- | --- | --- |

## Correct Use

Call the endpoint as a protocol surface. Use exact parameter names and encoded values, authenticate when required, and inspect the response before relying on it. Avoid sending browser-only submit buttons unless the backend explicitly requires the action key.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

Page backend: `source/net/yacy/htroot/api/share.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/api/share.html` | `POST` | public or page-dependent | `source/net/yacy/htroot/api/share.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |

Example request shape:

```http
POST /api/share.html
Content-Type: application/x-www-form-urlencoded

data=...&c=...
```

## What To Expect

Expect a compact service response rather than a teaching interface. The response may be XML, RSS, JSON-like text, plain text, or a small HTML template depending on the endpoint.

## Related Pages

- Related protocol work usually continues through the calling tool, the peer endpoint family under `/yacy/`, or the API page that consumes this response.
