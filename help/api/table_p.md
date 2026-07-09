---
page: htroot/api/table_p.html
help: help/api/table_p.md
title: Table Viewer
package: machine-api-peer
access: admin
kind: api-endpoint
backend_java: source/net/yacy/htroot/api/table_p.java
---

# Table Viewer

## Purpose

Table API exposes YaCy table data.

Use it for scripted reads or writes of structured records when the table name and operation are known.

## What You Can Do Here

- Read or modify structured table data through the API surface.
- Specify table names, keys, and operations exactly.
- Check the response before assuming a row was created, changed, or deleted.

## Page Architecture

Machine/API pages are compact endpoints. Their architecture is request-parameter driven: callers provide the smallest needed set of arguments and consume a direct response rather than a guided administration workflow.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `edittable` | Table: ''. | `Edit Table` |

## Correct Use

Call the endpoint as a protocol surface. Use exact parameter names and encoded values, authenticate when required, and inspect the response before relying on it. Avoid sending browser-only submit buttons unless the backend explicitly requires the action key.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/Tables_p.html`, `/api/table_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/api/table_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Tables_p.html` | `POST` | admin | `source/net/yacy/htroot/Tables_p.java` |
| `/api/table_p.html` | `GET or POST` | admin | `source/net/yacy/htroot/api/table_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `edittable` | Table: ''. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `count` | SRU-style result count. It is an alternative to `maximumRecords` on search endpoints. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `deleterows` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deletetable` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `search` | Alternative search text parameter accepted by some search endpoints; prefer `query` on browser search pages unless reproducing an existing URL. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `table` | Table: ''. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /Tables_p.html
Content-Type: application/x-www-form-urlencoded

edittable=...&addrow=...&commitrow=...&count=...&deleterows=...
```

## What To Expect

Expect a compact service response rather than a teaching interface. The response may be XML, RSS, JSON-like text, plain text, or a small HTML template depending on the endpoint.

## Related Pages

- Related protocol work usually continues through the calling tool, the peer endpoint family under `/yacy/`, or the API page that consumes this response.
