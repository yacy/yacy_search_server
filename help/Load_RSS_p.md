---
page: htroot/Load_RSS_p.html
help: help/Load_RSS_p.md
title: Configuration of a RSS Search
package: crawler
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/Load_RSS_p.java
---

# Configuration of a RSS Search

## Purpose

RSS Search configuration turns feeds into crawl or import sources.

Use it when new documents arrive through RSS or Atom rather than normal website navigation.

## What You Can Do Here

- RSS Search configuration turns feeds into crawl or import sources.
- Choose limits, boundaries, and queue actions that match the size of the source.
- Watch the crawler monitor afterward because indexing happens after fetching and parsing.

## Page Architecture

Crawler pages separate three decisions: the source to load, the rules that decide which discovered URLs are accepted, and the monitor or control action that follows the job after it starts.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `url` | URL to inspect, crawl, import, or act on. | URL or URL-derived value; use the exact format shown by the page. |
| `collection` | Collection name. Use it to group crawled or imported documents and to search or manage that group later. | Collection name such as `user`, `docs`, or a project-specific name. |
| `repeat` | Choice value. Options: `off`, `on`. | `off`, `on` |
| `repeat_time` | Choice value. Options: `1`, `2`, `3`, `4`, `5`, `6`, `7`, `8`, `9`, `10`, `12`, `14`, `21`, `28`, `30`. | `1`, `2`, `3`, `4`, `5`, `6`, `7`, `8`, `9`, `10`, `12`, `14`, `21`, `28`, `30` |
| `repeat_unit` | Choice value. Options: `selminutes` = minutes, `selhours` = hours, `seldays` = days. | `selminutes` = minutes, `selhours` = hours, `seldays` = days |
| `item_#[count]#` | Choice value. Options: `mark_`. | `mark_` |
| `removeSelectedFeedsScheduler` | Deletion or termination action. Use only with explicit intent. | `Remove Selected Feeds from Scheduler` |
| `removeAllFeedsScheduler` | Deletion or termination action. Use only with explicit intent. | `Remove All Feeds from Scheduler` |
| `removeSelectedFeedsNewList` | Deletion or termination action. Use only with explicit intent. | `Remove Selected Feeds from Feed List` |
| `removeAllFeedsNewList` | Deletion or termination action. Use only with explicit intent. | `Remove All Feeds from Feed List` |

## Correct Use

Begin with the smallest crawl that proves the idea. Use exact start URLs, prefer restrictive boundaries, set limits for unfamiliar sites, and watch the queue after submission. A crawler is not a magic search box: it creates search results only after documents have been loaded, parsed, and indexed.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/Load_RSS_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/Load_RSS_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Load_RSS_p.html` | `POST` | admin | `source/net/yacy/htroot/Load_RSS_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `collection` | Collection name. Use it to group crawled or imported documents and to search or manage that group later. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `repeat` | Choice value. Options: `off`, `on`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `repeat_time` | Choice value. Options: `1`, `2`, `3`, `4`, `5`, `6`, `7`, `8`, `9`, `10`, `12`, `14`, `21`, `28`, `30`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `repeat_unit` | Choice value. Options: `selminutes` = minutes, `selhours` = hours, `seldays` = days. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `item_#[count]#` | Choice value. Options: `mark_`. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `removeSelectedFeedsScheduler` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `removeAllFeedsScheduler` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `removeSelectedFeedsNewList` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `removeAllFeedsNewList` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `agentName` | Crawler user-agent profile used for outgoing HTTP requests. Choose a profile that matches the desired identity and politeness behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /Load_RSS_p.html
Content-Type: application/x-www-form-urlencoded

url=...&showrss=...&collection=...&repeat=...&repeat_time=...
```

## What To Expect

A successful action usually changes crawl state rather than producing finished search results immediately. Expect queued URLs, progress counters, success or error reports, and later changes in search results after indexing catches up.

## Related Pages

- Related crawler work is usually reached through `Crawler_p.html`, `CrawlStartSite.html`, `CrawlStartExpert.html`, or crawl result and queue monitors.
