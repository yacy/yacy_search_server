---
page: htroot/RemoteCrawl_p.html
help: help/RemoteCrawl_p.md
title: Remote Crawl Configuration
package: core-search-public
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/RemoteCrawl_p.java
---

# Remote Crawl Configuration

## Purpose

Remote Crawl Configuration controls whether other peers may ask this peer to crawl.

Use it to decide how much cooperative crawling is acceptable for this installation.

## What You Can Do Here

- Remote Crawl Configuration controls whether other peers may ask this peer to crawl.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `crawlResponse` | Accept Remote Crawl Requests. | Accept Remote Crawl Requests |
| `acceptCrawlLimit` | Load with a maximum of. | Text value; use the page label and surrounding context to choose the exact content. |
| `save` | Saves settings. | `Save` |

## Correct Use

Start from the user's information need. Use query text, content type, collection, URL filters, and pagination to narrow results. Do not mix ordinary search with authenticated result actions unless the user explicitly asks to modify stored data.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/RemoteCrawl_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/RemoteCrawl_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/RemoteCrawl_p.html` | `POST` | admin | `source/net/yacy/htroot/RemoteCrawl_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `crawlResponse` | Accept Remote Crawl Requests. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `acceptCrawlLimit` | Load with a maximum of. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `save` | Saves settings. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `search` | Alternative search text parameter accepted by some search endpoints; prefer `query` on browser search pages unless reproducing an existing URL. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /RemoteCrawl_p.html
Content-Type: application/x-www-form-urlencoded

crawlResponse=...&acceptCrawlLimit=...&save=...&search=...
```

## What To Expect

Expect rendered search or content output: result lists, snippets, previews, redirects, widgets, or fragments. If output is empty, check whether the index contains matching documents before changing query syntax.

## Related Pages

- `CrawlResults.html`
