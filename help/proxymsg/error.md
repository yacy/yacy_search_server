---
page: htroot/proxymsg/error.html
help: help/proxymsg/error.md
title: Error Message
package: proxy-embedded-static
access: public
kind: proxy-message
backend_java: none
---

# Error Message

## Purpose

Proxy Error Message explains a failed proxy request.

Use it when YaCy acts as a proxy and must tell the browser why a page could not be delivered.

## What You Can Do Here

- Proxy Error Message explains a failed proxy request.
- Use it through the page or proxy context that includes it.
- Treat it as a support view unless the parameters document a direct service call.

## Page Architecture

The page is built around a visible form and backend parameters. The visible controls are the starting point; hidden fields usually carry defaults, transaction data, or browser state.

## Correct Use

Use this page in the context where YaCy displays it. Embedded and static pages usually explain, wrap, or visualize another workflow; direct calls should stay minimal and read-only unless documented otherwise.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

No matching Java servlet was found for this template.

No request parameters are needed for normal use of this page.

## What To Expect

Expect a focused support view, embedded fragment, visual page, or proxy message. The important action usually happens on the page that linked here.

## Related Pages

- Related embedded work usually continues in the proxy page, portal page, or visualization that included this fragment.
