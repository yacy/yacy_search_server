---
page: htroot/Autocrawl_p.html
help: help/Autocrawl_p.md
title: Crawl Start
package: crawler
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/Autocrawl_p.java
---

# Crawl Start

## Purpose

Autocrawl starts discovery from configured sources without a person submitting every URL manually.

Use it when YaCy should keep finding new content within controlled boundaries.

## What You Can Do Here

- Autocrawl starts discovery from configured sources without a person submitting every URL manually.
- Choose limits, boundaries, and queue actions that match the size of the source.
- Watch the crawler monitor afterward because indexing happens after fetching and parsing.

## Page Architecture

Crawler pages separate three decisions: the source to load, the rules that decide which discovered URLs are accepted, and the monitor or control action that follows the job after it starts.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `autocrawlEnable` | Enables the named feature. | Text value; use the page label and surrounding context to choose the exact content. |
| `autocrawlShallow` | Enables the named feature. | Text value; use the page label and surrounding context to choose the exact content. |
| `save` | Saves settings. | `Save` |

## Correct Use

Begin with the smallest crawl that proves the idea. Use exact start URLs, prefer restrictive boundaries, set limits for unfamiliar sites, and watch the queue after submission. A crawler is not a magic search box: it creates search results only after documents have been loaded, parsed, and indexed.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/Autocrawl_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/Autocrawl_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Autocrawl_p.html` | `POST` | admin | `source/net/yacy/htroot/Autocrawl_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `autocrawlEnable` | Enables the named feature. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `autocrawlShallow` | Enables the named feature. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `save` | Saves settings. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
POST /Autocrawl_p.html
Content-Type: application/x-www-form-urlencoded

autocrawlEnable=...&autocrawlRatio=...&autocrawlRows=...&autocrawlDays=...&autocrawlQuery=...
```

## What To Expect

A successful action usually changes crawl state rather than producing finished search results immediately. Expect queued URLs, progress counters, success or error reports, and later changes in search results after indexing catches up.

## Related Pages

- Related crawler work is usually reached through `Crawler_p.html`, `CrawlStartSite.html`, `CrawlStartExpert.html`, or crawl result and queue monitors.
