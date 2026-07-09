---
page: htroot/mediawiki_p.html
help: help/mediawiki_p.md
title: mediawiki p
package: machine-api-peer
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/mediawiki_p.java
---

# mediawiki p

## Purpose

MediaWiki endpoint serves MediaWiki-oriented integration data.

Use it when a MediaWiki-related tool expects this YaCy service surface.

## What You Can Do Here

- MediaWiki endpoint serves MediaWiki-oriented integration data.
- Send exact parameter names and encoded values rather than natural-language approximations.
- Inspect the response format before using the endpoint in a larger tool chain.

## Page Architecture

This is a compact endpoint-style page. Its behavior is driven mainly by request parameters and the selected response template, so callers should send only the fields needed for the specific query or peer-service action.

## Correct Use

Call the endpoint as a protocol surface. Use exact parameter names and encoded values, authenticate when required, and inspect the response before relying on it. Avoid sending browser-only submit buttons unless the backend explicitly requires the action key.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/mediawiki_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/mediawiki_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/mediawiki_p.html` | `GET or POST` | admin | `source/net/yacy/htroot/mediawiki_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `title` | Human-readable title. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET or POST /mediawiki_p.html?dump=...&title=...
```

## What To Expect

Expect a compact service response rather than a teaching interface. The response may be XML, RSS, JSON-like text, plain text, or a small HTML template depending on the endpoint.

## Related Pages

- Related protocol work usually continues through the calling tool, the peer endpoint family under `/yacy/`, or the API page that consumes this response.
