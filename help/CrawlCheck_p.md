---
page: htroot/CrawlCheck_p.html
help: help/CrawlCheck_p.md
title: Crawl Check
package: crawler
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/CrawlCheck_p.java
---

# Crawl Check

## Purpose

Crawl Check tests how YaCy would treat a URL before a crawl is started.

Use it to catch malformed URLs, blocked targets, parser issues, or rule conflicts before work enters the queue.

## What You Can Do Here

- Paste one or more candidate start URLs and ask YaCy how it would handle them.
- Confirm that URL syntax, crawler user-agent choice, and crawl rules allow the target.
- Use the result to repair the crawl plan before creating queue work.

## Page Architecture

Crawl Check is a preflight page. It submits candidate URLs to the same kind of URL-normalization and crawler-decision logic used later by crawl start pages, but the goal is diagnosis rather than building a full crawl queue.

## Correct Use

Paste exact URLs, one per line when checking several targets. Use the same crawler agent that the real crawl would use. Fix rejected or surprising URLs before starting a crawl, because bad start URLs create empty queues or noisy crawl failures.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/CrawlCheck_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/CrawlCheck_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/CrawlCheck_p.html` | `POST` | admin | `source/net/yacy/htroot/CrawlCheck_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `crawlingURLs` | List of possible crawl start URLs. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlcheck` | List of possible crawl start URLs. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `agentName` | Crawler user-agent profile used for outgoing HTTP requests. Choose a profile that matches the desired identity and politeness behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /CrawlCheck_p.html
Content-Type: application/x-www-form-urlencoded

crawlingURLs=...&crawlcheck=...&agentName=...
```

## What To Expect

The response should explain how YaCy interprets each submitted URL and whether it is suitable as a crawl target. It should not be treated as proof that the full site has been indexed.

## Related Pages

- Related crawler work is usually reached through `Crawler_p.html`, `CrawlStartSite.html`, `CrawlStartExpert.html`, or crawl result and queue monitors.
