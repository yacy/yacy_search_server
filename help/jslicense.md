---
page: htroot/jslicense.html
help: help/jslicense.md
title: JavaScript license information
package: core-search-public
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/jslicense.java
---

# JavaScript license information

## Purpose

JavaScript license information lists script licensing details.

Use it to inspect legal and compliance information for browser-side code.

## What You Can Do Here

- JavaScript license information lists script licensing details.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

## Correct Use

Start from the user's information need. Use query text, content type, collection, URL filters, and pagination to narrow results. Do not mix ordinary search with authenticated result actions unless the user explicitly asks to modify stored data.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

Page backend: `source/net/yacy/htroot/jslicense.java`.

No request parameters are needed for normal use of this page.

## What To Expect

Expect rendered search or content output: result lists, snippets, previews, redirects, widgets, or fragments. If output is empty, check whether the index contains matching documents before changing query syntax.

## Related Pages

- `http://www.freebsd.org/copyright/freebsd-license.html`
- `http://www.gnu.org/licenses/gpl-2.0.html`
