---
page: htroot/QuickCrawlLink_p.html
help: help/QuickCrawlLink_p.md
title: Quick Crawl Link
package: core-search-public
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/QuickCrawlLink_p.java
---

# Quick Crawl Link

## Purpose

Quick Crawl Link is a shortcut for submitting one URL quickly.

Use it for a small, immediate fetch when a full crawl profile would be unnecessary.

## What You Can Do Here

- Quick Crawl Link is a shortcut for submitting one URL quickly.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

## Correct Use

Use this page for one URL or a very small task. If the request needs depth, filters, collections, recrawl policy, or careful politeness settings, use the normal crawl start pages instead.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/QuickCrawlLink_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/QuickCrawlLink_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/QuickCrawlLink_p.html` | `GET or POST` | admin | `source/net/yacy/htroot/QuickCrawlLink_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `collection` | Collection name. Use it to group crawled or imported documents and to search or manage that group later. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlOrder` | Crawl ordering strategy, such as balanced host scheduling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingDepth` | Maximum link depth from the start URL. Depth 0 loads only the submitted document; larger values follow links farther away. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingQ` | Queues discovered URLs for crawler processing; sitemap mode normally enables queued crawling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `followFrames` | Allows the crawler to follow frame and iframe sources. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexMedia` | Indexes discovered media resources when enabled. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexText` | Indexes extracted text content when enabled. Disable only for specialized media-only crawls. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `mustmatch` | Regular expression that discovered URLs must match before they enter the crawl. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `mustnotmatch` | Regular expression that discovered URLs must not match. Use it to exclude logout URLs, calendars, filters, or unwanted directories. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `obeyHtmlRobotsNofollow` | Honors HTML robots `nofollow` instructions while discovering links. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `obeyHtmlRobotsNoindex` | Honors HTML robots `noindex` instructions while indexing. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `storeHTCache` | Stores fetched documents in YaCy hypertext cache. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `timezoneOffset` | Client timezone offset in minutes. YaCy uses it for date display or schedule calculations. | Read-only request context for date handling. |
| `title` | Human-readable title. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET or POST /QuickCrawlLink_p.html?crawlingDepth=...&url=...&collection=...&crawlOrder=...&crawlingQ=...
```

## What To Expect

YaCy should queue or load the submitted URL quickly, but search results appear only after fetching, parsing, and indexing finish.

## Related Pages

- Related search work usually continues on `yacysearch.html`, `index.html`, `ViewFile.html`, quick crawl, or the search integration pages.
