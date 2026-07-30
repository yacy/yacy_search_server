---
page: htroot/yacy/transferRWI.html
help: help/yacy/transferRWI.md
title: transferRWI
package: machine-api-peer
access: peer-service
kind: peer-endpoint
backend_java: source/net/yacy/htroot/yacy/transferRWI.java
---

# transferRWI

## Purpose

transferRWI exchanges reverse word index data between peers.

Use it for network index-sharing behavior, not for ordinary user search.

## What You Can Do Here

- Exchange reverse-word-index data with another peer.
- Send only the protocol fields needed for the transfer.
- Use it as part of YaCy index sharing, not as a manual search endpoint.

## Page Architecture

This is a compact endpoint-style page. Its behavior is driven mainly by request parameters and the selected response template, so callers should send only the fields needed for the specific query or peer-service action.

## Correct Use

Call the endpoint as a protocol surface. Use exact parameter names and encoded values, authenticate when required, and inspect the response before relying on it. Avoid sending browser-only submit buttons unless the backend explicitly requires the action key.

## Access And Safety

This is a peer-service endpoint for YaCy peer communication, not a normal editing page.

Behind a reverse proxy, YaCy accepts `X-Real-IP` as the effective client and
routing address only when the proxy socket IP matches
`server.reverseProxy.trusted` and the header contains one valid IPv4 or IPv6
address. Otherwise YaCy uses the socket IP. Authentication and access control
always use the socket IP. The proxy must overwrite, not pass through, any
client-supplied `X-Real-IP` value.

## Automation And API

Page backend: `source/net/yacy/htroot/yacy/transferRWI.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/yacy/transferRWI.html` | `GET or POST` | peer-service | `source/net/yacy/htroot/yacy/transferRWI.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |

Example request shape:

```http
GET or POST /yacy/transferRWI.html?entryc=...&iam=...&indexes=...&key=...&wordc=...
```

## What To Expect

Expect a compact service response rather than a teaching interface. The response may be XML, RSS, JSON-like text, plain text, or a small HTML template depending on the endpoint.

## Related Pages

- Related protocol work usually continues through the calling tool, the peer endpoint family under `/yacy/`, or the API page that consumes this response.
