---
page: htroot/yacy/crawlReceipt.html
help: help/yacy/crawlReceipt.md
title: crawlReceipt
package: machine-api-peer
access: peer-service
kind: peer-endpoint
backend_java: source/net/yacy/htroot/yacy/crawlReceipt.java
---

# crawlReceipt

## Purpose

crawlReceipt acknowledges crawl information exchanged between peers.

Use it as part of peer communication rather than as a manual browser page.

## What You Can Do Here

- Acknowledge or inspect crawl-receipt communication between peers.
- Use exact crawl and peer identifiers.
- Treat the response as protocol confirmation.

## Page Architecture

This is a compact endpoint-style page. Its behavior is driven mainly by request parameters and the selected response template, so callers should send only the fields needed for the specific query or peer-service action.

## Correct Use

Call the endpoint as a protocol surface. Use exact parameter names and encoded values, authenticate when required, and inspect the response before relying on it. Avoid sending browser-only submit buttons unless the backend explicitly requires the action key.

## Access And Safety

This is a peer-service endpoint for YaCy peer communication, not a normal editing page.

## Automation And API

Page backend: `source/net/yacy/htroot/yacy/crawlReceipt.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/yacy/crawlReceipt.html` | `GET or POST` | peer-service | `source/net/yacy/htroot/yacy/crawlReceipt.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |

Example request shape:

```http
GET or POST /yacy/crawlReceipt.html?iam=...&lurlEntry=...&reason=...&result=...&wordh=...
```

## What To Expect

Expect a compact service response rather than a teaching interface. The response may be XML, RSS, JSON-like text, plain text, or a small HTML template depending on the endpoint.

## Related Pages

- Related protocol work usually continues through the calling tool, the peer endpoint family under `/yacy/`, or the API page that consumes this response.
