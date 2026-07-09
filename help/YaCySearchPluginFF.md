---
page: htroot/YaCySearchPluginFF.html
help: help/YaCySearchPluginFF.md
title: Quick Crawl Link
package: core-search-public
access: public
kind: search-page
backend_java: source/net/yacy/htroot/YaCySearchPluginFF.java
---

# Quick Crawl Link

## Purpose

Search Plugin setup helps browsers add YaCy as a search provider.

Use it when users should search this peer from the browser search bar.

## What You Can Do Here

- Search Plugin setup helps browsers add YaCy as a search provider.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

## Correct Use

Start from the user's information need. Use query text, content type, collection, URL filters, and pagination to narrow results. Do not mix ordinary search with authenticated result actions unless the user explicitly asks to modify stored data.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

Page backend: `source/net/yacy/htroot/YaCySearchPluginFF.java`.

No request parameters are needed for normal use of this page.

## What To Expect

Expect rendered search or content output: result lists, snippets, previews, redirects, widgets, or fragments. If output is empty, check whether the index contains matching documents before changing query syntax.

## Related Pages

- Related search work usually continues on `yacysearch.html`, `index.html`, `ViewFile.html`, quick crawl, or the search integration pages.
