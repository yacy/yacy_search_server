---
page: htroot/yacychat.html
help: help/yacychat.md
title: Chat
package: core-search-public
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/yacychat.java
---

# Chat

## Purpose

Chat provides a conversational interface around YaCy search or local features.

Use it when the user wants an interactive dialogue instead of a classic result page.

## What You Can Do Here

- Chat provides a conversational interface around YaCy search or local features.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `userInput` | no search, allow attachments. | Checkbox/boolean; present usually means enabled. |
| `sendButton` | no search, allow attachments. | `Send` |

## Correct Use

Start from the user's information need. Use query text, content type, collection, URL filters, and pagination to narrow results. Do not mix ordinary search with authenticated result actions unless the user explicitly asks to modify stored data.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

Page backend: `source/net/yacy/htroot/yacychat.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/yacychat.html` | `GET` | public or page-dependent | `source/net/yacy/htroot/yacychat.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `userInput` | no search, allow attachments. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `sendButton` | no search, allow attachments. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /yacychat.html?userInput=...&searchButton=...&addFileButton=...&fileInput=...&sendButton=...
```

## What To Expect

Expect rendered search or content output: result lists, snippets, previews, redirects, widgets, or fragments. If output is empty, check whether the index contains matching documents before changing query syntax.

## Related Pages

- Related search work usually continues on `yacysearch.html`, `index.html`, `ViewFile.html`, quick crawl, or the search integration pages.
