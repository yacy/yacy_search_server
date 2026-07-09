---
page: htroot/terminal_p.html
help: help/terminal_p.md
title: System Terminal Monitor
package: machine-api-peer
access: admin
kind: admin-page
backend_java: none
---

# System Terminal Monitor

## Purpose

System Terminal Monitor exposes terminal-style server interaction.

Use it with care because terminal-like endpoints can reveal or change operational state.

## What You Can Do Here

- System Terminal Monitor exposes terminal-style server interaction.
- Send exact parameter names and encoded values rather than natural-language approximations.
- Inspect the response format before using the endpoint in a larger tool chain.

## Page Architecture

Machine/API pages are compact endpoints. Their architecture is request-parameter driven: callers provide the smallest needed set of arguments and consume a direct response rather than a guided administration workflow.

## Correct Use

Call the endpoint as a protocol surface. Use exact parameter names and encoded values, authenticate when required, and inspect the response before relying on it. Avoid sending browser-only submit buttons unless the backend explicitly requires the action key.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

## Automation And API

No matching Java servlet was found for this template.

No request parameters are needed for normal use of this page.

## What To Expect

Expect a compact service response rather than a teaching interface. The response may be XML, RSS, JSON-like text, plain text, or a small HTML template depending on the endpoint.

## Related Pages

- `index.html`
- `CrawlStartSite.html`
- `Status.html`
- `Steering.html`
- `http://java.sun.com/products/plugin/downloads/index.html`
