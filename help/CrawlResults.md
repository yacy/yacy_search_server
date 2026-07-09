---
page: htroot/CrawlResults.html
help: help/CrawlResults.md
title: Crawl Results
package: crawler
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/CrawlResults.java
---

# Crawl Results

## Purpose

Crawl Results summarizes what crawls produced.

Use it after a crawl to understand successes, failures, discovered URLs, and indexed material.

## What You Can Do Here

- Crawl Results summarizes what crawls produced.
- Choose limits, boundaries, and queue actions that match the size of the source.
- Watch the crawler monitor afterward because indexing happens after fetching and parsing.

## Page Architecture

Crawler pages separate three decisions: the source to load, the rules that decide which discovered URLs are accepted, and the monitor or control action that follows the job after it starts.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `deletedomain` | Deletion or termination action. Use only with explicit intent. | `delete all` |
| `deleteentry` | Deletion or termination action. Use only with explicit intent. | `delete` |

## Correct Use

Begin with the smallest crawl that proves the idea. Use exact start URLs, prefer restrictive boundaries, set limits for unfamiliar sites, and watch the queue after submission. A crawler is not a magic search box: it creates search results only after documents have been loaded, parsed, and indexed.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

Backend checks: user authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/CrawlResults.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/CrawlResults.html` | `GET` | public or page-dependent | `source/net/yacy/htroot/CrawlResults.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `deletedomain` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteentry` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `count` | SRU-style result count. It is an alternative to `maximumRecords` on search endpoints. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `domain` | Host or domain scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /CrawlResults.html?selectedblacklist=...&deletedomain=...&delandaddtoblacklist=...&clearlist=...&deleteentry=...
```

## What To Expect

A successful action usually changes crawl state rather than producing finished search results immediately. Expect queued URLs, progress counters, success or error reports, and later changes in search results after indexing catches up.

## Related Pages

- `CrawlStartExpert.html`
- `RemoteCrawl_p.html`
- `IndexImportMediawiki_p.html`
- `IndexImportOAIPMH_p.html`
- `ViewFile.html`
