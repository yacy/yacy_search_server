---
page: htroot/CrawlMonitorRemoteStart.html
help: help/CrawlMonitorRemoteStart.md
title: Monitor for remotely started global crawls
package: crawler
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/CrawlMonitorRemoteStart.java
---

# Monitor for remotely started global crawls

## Purpose

Remote Crawl Monitor shows crawls that were started remotely through the YaCy network.

Use it to audit externally triggered crawl activity before trusting or continuing it.

## What You Can Do Here

- Remote Crawl Monitor shows crawls that were started remotely through the YaCy network.
- Choose limits, boundaries, and queue actions that match the size of the source.
- Watch the crawler monitor afterward because indexing happens after fetching and parsing.

## Page Architecture

Crawler pages separate three decisions: the source to load, the rules that decide which discovered URLs are accepted, and the monitor or control action that follows the job after it starts.

## Correct Use

Begin with the smallest crawl that proves the idea. Use exact start URLs, prefer restrictive boundaries, set limits for unfamiliar sites, and watch the queue after submission. A crawler is not a magic search box: it creates search results only after documents have been loaded, parsed, and indexed.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

Page backend: `source/net/yacy/htroot/CrawlMonitorRemoteStart.java`.

No request parameters are needed for normal use of this page.

## What To Expect

A successful action usually changes crawl state rather than producing finished search results immediately. Expect queued URLs, progress counters, success or error reports, and later changes in search results after indexing catches up.

## Related Pages

- Related crawler work is usually reached through `Crawler_p.html`, `CrawlStartSite.html`, `CrawlStartExpert.html`, or crawl result and queue monitors.
