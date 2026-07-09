---
page: htroot/env/grafics/donate.html
help: help/env/grafics/donate.md
title: donate
package: proxy-embedded-static
access: public
kind: embedded-static-page
backend_java: none
---

# donate

## Purpose

Donate is a static project-support page.

Use it only to explain the support link embedded in the interface.

## What You Can Do Here

- Donate is a static project-support page.
- Use it through the page or proxy context that includes it.
- Treat it as a support view unless the parameters document a direct service call.

## Page Architecture

The page is built around a visible form and backend parameters. The visible controls are the starting point; hidden fields usually carry defaults, transaction data, or browser state.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `os0` | Choice value. Options: `beneficial` = beneficial: 5 €, `generous` = generous: 25 €, `gracious` = gracious: 50 €. | `beneficial` = beneficial: 5 €, `generous` = generous: 25 €, `gracious` = gracious: 50 € |
| `submit` | Submits the form. | `Donate!` |

## Correct Use

Use this page in the context where YaCy displays it. Embedded and static pages usually explain, wrap, or visualize another workflow; direct calls should stay minimal and read-only unless documented otherwise.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

No matching Java servlet was found for this template.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `https://www.paypal.com/cgi-bin/webscr` | `POST` | external | external service |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `os0` | Choice value. Options: `beneficial` = beneficial: 5 €, `generous` = generous: 25 €, `gracious` = gracious: 50 €. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `submit` | Submit action for the form. Its meaning depends on the surrounding fields. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
POST https://www.paypal.com/cgi-bin/webscr
Content-Type: application/x-www-form-urlencoded

os0=...&submit=...
```

## What To Expect

Expect a focused support view, embedded fragment, visual page, or proxy message. The important action usually happens on the page that linked here.

## Related Pages

- Related embedded work usually continues in the proxy page, portal page, or visualization that included this fragment.
