---
page: htroot/proxymsg/urlproxyheader.html
help: help/proxymsg/urlproxyheader.md
title: urlproxyheader
package: proxy-embedded-static
access: public
kind: proxy-message
backend_java: source/net/yacy/htroot/proxymsg/urlproxyheader.java
---

# urlproxyheader

## Purpose

URL Proxy Header is an embedded proxy information fragment.

Use it to understand the header area shown around proxied content.

## What You Can Do Here

- URL Proxy Header is an embedded proxy information fragment.
- Use it through the page or proxy context that includes it.
- Treat it as a support view unless the parameters document a direct service call.

## Page Architecture

The page is built around a visible form and backend parameters. The visible controls are the starting point; hidden fields usually carry defaults, transaction data, or browser state.

| Control | Meaning | Values or examples |
| --- | --- | --- |

## Correct Use

Use this page in the context where YaCy displays it. Embedded and static pages usually explain, wrap, or visualize another workflow; direct calls should stay minimal and read-only unless documented otherwise.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

Backend checks: user authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/proxymsg/urlproxyheader.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/proxymsg/urlproxyheader.html` | `GET` | public or page-dependent | `source/net/yacy/htroot/proxymsg/urlproxyheader.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `hash` | YaCy hash identifier for a peer, URL, row, or stored object. Use exact values copied from YaCy output. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /proxymsg/urlproxyheader.html?url=...&exit=...&addbookmark=...&bookmark=...&hash=...
```

## What To Expect

Expect a focused support view, embedded fragment, visual page, or proxy message. The important action usually happens on the page that linked here.

## Related Pages

- Related embedded work usually continues in the proxy page, portal page, or visualization that included this fragment.
