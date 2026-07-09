---
page: htroot/rssTerminal.html
help: help/rssTerminal.md
title: rss terminal
package: machine-api-peer
access: public
kind: ui-page
backend_java: none
---

# rss terminal

## Purpose

RSS Terminal exposes feed-style output for terminal or script use.

Use it when a caller needs YaCy data as a feed rather than an HTML page.

## What You Can Do Here

- RSS Terminal exposes feed-style output for terminal or script use.
- Send exact parameter names and encoded values rather than natural-language approximations.
- Inspect the response format before using the endpoint in a larger tool chain.

## Page Architecture

This is a compact endpoint-style page. Its behavior is driven mainly by request parameters and the selected response template, so callers should send only the fields needed for the specific query or peer-service action.

## Correct Use

Call the endpoint as a protocol surface. Use exact parameter names and encoded values, authenticate when required, and inspect the response before relying on it. Avoid sending browser-only submit buttons unless the backend explicitly requires the action key.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

No matching Java servlet was found for this template.

No request parameters are needed for normal use of this page.

## What To Expect

Expect a compact service response rather than a teaching interface. The response may be XML, RSS, JSON-like text, plain text, or a small HTML template depending on the endpoint.

## Related Pages

- Related protocol work usually continues through the calling tool, the peer endpoint family under `/yacy/`, or the API page that consumes this response.
