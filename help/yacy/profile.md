---
page: htroot/yacy/profile.html
help: help/yacy/profile.md
title: profile
package: machine-api-peer
access: peer-service
kind: peer-endpoint
backend_java: source/net/yacy/htroot/yacy/profile.java
---

# profile

## Purpose

profile returns peer profile information in a compact form.

Use it when a tool needs metadata about this peer or another peer.

## What You Can Do Here

- Retrieve compact peer profile information.
- Use the response to identify a peer in tools or diagnostics.
- Avoid assuming profile data is complete or private.

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

Page backend: `source/net/yacy/htroot/yacy/profile.java`.

No request parameters are needed for normal use of this page.

## What To Expect

Expect a compact service response rather than a teaching interface. The response may be XML, RSS, JSON-like text, plain text, or a small HTML template depending on the endpoint.

## Related Pages

- Related protocol work usually continues through the calling tool, the peer endpoint family under `/yacy/`, or the API page that consumes this response.
