---
page: htroot/api/push_p.html
help: help/api/push_p.md
title: File Upload
package: machine-api-peer
access: admin
kind: api-endpoint
backend_java: source/net/yacy/htroot/api/push_p.java
---

# File Upload

## Purpose

File Upload accepts pushed content into YaCy.

Use it when another tool submits documents directly instead of asking the crawler to fetch them.

## What You Can Do Here

- Push document content directly into YaCy.
- Send file or document metadata in the expected upload shape.
- Verify indexing afterward because upload acceptance is not the same as search visibility.

## Page Architecture

Machine/API pages are compact endpoints. Their architecture is request-parameter driven: callers provide the smallest needed set of arguments and consume a direct response rather than a guided administration workflow.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `synchronous` | Choice value. Options: `true`. | `true` |
| `commit` | Choice value. Options: `true`. | `true` |

## Correct Use

Call the endpoint as a protocol surface. Use exact parameter names and encoded values, authenticate when required, and inspect the response before relying on it. Avoid sending browser-only submit buttons unless the backend explicitly requires the action key.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/api/push_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/api/push_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/api/push_p.html` | `POST` | admin | `source/net/yacy/htroot/api/push_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `synchronous` | Choice value. Options: `true`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `commit` | Choice value. Options: `true`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `count` | SRU-style result count. It is an alternative to `maximumRecords` on search endpoints. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |

Example request shape:

```http
POST /api/push_p.html
Content-Type: application/x-www-form-urlencoded

synchronous=...&commit=...&data-#[count]#=...&url-#[count]#=...&collection-#[count]#=...
```

## What To Expect

Expect a compact service response rather than a teaching interface. The response may be XML, RSS, JSON-like text, plain text, or a small HTML template depending on the endpoint.

## Related Pages

- Related protocol work usually continues through the calling tool, the peer endpoint family under `/yacy/`, or the API page that consumes this response.
