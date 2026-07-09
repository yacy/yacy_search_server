---
page: htroot/portalsearch/yacy-portalsearch.html
help: help/portalsearch/yacy-portalsearch.md
title: Bookmarks
package: proxy-embedded-static
access: public
kind: embedded-static-page
backend_java: none
---

# Bookmarks

## Purpose

Portal Search is an embedded search form for a YaCy-backed site portal.

Use it when a website integrates YaCy search without exposing the full administration interface.

## What You Can Do Here

- Portal Search is an embedded search form for a YaCy-backed site portal.
- Use it through the page or proxy context that includes it.
- Treat it as a support view unless the parameters document a direct service call.

## Page Architecture

The page is built around a visible form and backend parameters. The visible controls are the starting point; hidden fields usually carry defaults, transaction data, or browser state.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Search terms, for example `climate data` or a more specific phrase. |

## Correct Use

Use this page in the context where YaCy displays it. Embedded and static pages usually explain, wrap, or visualize another workflow; direct calls should stay minimal and read-only unless documented otherwise.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

No matching Java servlet was found for this template.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/portalsearch/yacysearch.html` | `GET` | public or page-dependent | template/static |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |

Example request shape:

```http
GET /portalsearch/yacysearch.html?query=...
```

## What To Expect

Expect a focused support view, embedded fragment, visual page, or proxy message. The important action usually happens on the page that linked here.

## Related Pages

- Related embedded work usually continues in the proxy page, portal page, or visualization that included this fragment.
