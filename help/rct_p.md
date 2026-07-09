---
page: htroot/rct_p.html
help: help/rct_p.md
title: Index Control
package: machine-api-peer
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/rct_p.java
---

# Index Control

## Purpose

Index Control is a compact endpoint for index-related remote control.

Use it only when a tool needs the specific service behavior exposed by this endpoint.

## What You Can Do Here

- Index Control is a compact endpoint for index-related remote control.
- Send exact parameter names and encoded values rather than natural-language approximations.
- Inspect the response format before using the endpoint in a larger tool chain.

## Page Architecture

Machine/API pages are compact endpoints. Their architecture is request-parameter driven: callers provide the smallest needed set of arguments and consume a direct response rather than a guided administration workflow.

| Control | Meaning | Values or examples |
| --- | --- | --- |

## Correct Use

Call the endpoint as a protocol surface. Use exact parameter names and encoded values, authenticate when required, and inspect the response before relying on it. Avoid sending browser-only submit buttons unless the backend explicitly requires the action key.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/rct_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/rct_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/rct_p.html` | `POST` | admin | `source/net/yacy/htroot/rct_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |

Example request shape:

```http
POST /rct_p.html
Content-Type: application/x-www-form-urlencoded

peer=...&retrieve=...
```

## What To Expect

Expect a compact service response rather than a teaching interface. The response may be XML, RSS, JSON-like text, plain text, or a small HTML template depending on the endpoint.

## Related Pages

- Related protocol work usually continues through the calling tool, the peer endpoint family under `/yacy/`, or the API page that consumes this response.
