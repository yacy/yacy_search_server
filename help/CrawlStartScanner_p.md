---
page: htroot/CrawlStartScanner_p.html
help: help/CrawlStartScanner_p.md
title: Network Scanner
package: crawler
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/CrawlStartScanner_p.java
---

# Network Scanner

## Purpose

Network Scanner discovers services in an IP range and can turn discoveries into crawl targets.

Use it for intranet discovery when the content landscape is not already known.

## What You Can Do Here

- Network Scanner discovers services in an IP range and can turn discoveries into crawl targets.
- Choose limits, boundaries, and queue actions that match the size of the source.
- Watch the crawler monitor afterward because indexing happens after fetching and parsing.

## Page Architecture

Crawler pages separate three decisions: the source to load, the rules that decide which discovered URLs are accepted, and the monitor or control action that follows the job after it starts.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `source` | Choice value. Options: `hosts`, `intranet`, `all`. | `hosts`, `intranet`, `all` |
| `scanhosts` | Host or domain scope. | Text value; use the page label and surrounding context to choose the exact content. |
| `subnet` | Choice value. Options: `31`, `24`, `20`, `16`. | `31`, `24`, `20`, `16` |
| `timeout` | Date/time value for filtering, display, or scheduling. | `2000` |
| `rescan` | Choice value. Options: `off`, `scheduler`. | `off`, `scheduler` |
| `repeat_time` | Choice value. Options: `1`, `2`, `3`, `4`, `5`, `6`, `7`, `8`, `9`, `10`, `12`, `14`, `15`, `21`, `28`, `30`. | `1`, `2`, `3`, `4`, `5`, `6`, `7`, `8`, `9`, `10`, `12`, `14`, `15`, `21`, `28`, `30` |
| `repeat_unit` | Choice value. Options: `selminutes` = minutes, `selhours` = hours, `seldays` = days. | `selminutes` = minutes, `selhours` = hours, `seldays` = days |

## Correct Use

Begin with the smallest crawl that proves the idea. Use exact start URLs, prefer restrictive boundaries, set limits for unfamiliar sites, and watch the queue after submission. A crawler is not a magic search box: it creates search results only after documents have been loaded, parsed, and indexed.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/CrawlStartScanner_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/CrawlStartScanner_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/CrawlStartScanner_p.html` | `GET` | admin | `source/net/yacy/htroot/CrawlStartScanner_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `source` | Choice value. Options: `hosts`, `intranet`, `all`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `scanhosts` | Host or domain scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `subnet` | Choice value. Options: `31`, `24`, `20`, `16`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `timeout` | Date/time value for filtering, display, or scheduling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `rescan` | Choice value. Options: `off`, `scheduler`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `repeat_time` | Choice value. Options: `1`, `2`, `3`, `4`, `5`, `6`, `7`, `8`, `9`, `10`, `12`, `14`, `15`, `21`, `28`, `30`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `repeat_unit` | Choice value. Options: `selminutes` = minutes, `selhours` = hours, `seldays` = days. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /CrawlStartScanner_p.html?source=...&scanhosts=...&subnet=...&timeout=...&accumulatescancache=...
```

## What To Expect

A successful action usually changes crawl state rather than producing finished search results immediately. Expect queued URLs, progress counters, success or error reports, and later changes in search results after indexing catches up.

## Related Pages

- Related crawler work is usually reached through `Crawler_p.html`, `CrawlStartSite.html`, `CrawlStartExpert.html`, or crawl result and queue monitors.
