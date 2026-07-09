---
page: htroot/CrawlProfileEditor_p.html
help: help/CrawlProfileEditor_p.md
title: Crawl Profile Editor
package: crawler
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/CrawlProfileEditor_p.java
---

# Crawl Profile Editor

## Purpose

Crawl Profile Editor changes the rules of an existing crawl profile.

Use it when a running or reusable crawl needs different limits, filters, or indexing behavior.

## What You Can Do Here

- Crawl Profile Editor changes the rules of an existing crawl profile.
- Choose limits, boundaries, and queue actions that match the size of the source.
- Watch the crawler monitor afterward because indexing happens after fetching and parsing.

## Page Architecture

Crawler pages separate three decisions: the source to load, the rules that decide which discovered URLs are accepted, and the monitor or control action that follows the job after it starts.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `handle` | Crawl profile or job handle. Use the exact value shown by YaCy for the job you want to control. | Text value; use the page label and surrounding context to choose the exact content. |
| `terminate` | Deletion or termination action. Use only with explicit intent. | `Terminate` |
| `delete` | Deletes the selected object or scope. Use only with explicit confirmation. | `Delete` |
| `deleteTerminatedProfiles` | Deletion or termination action. Use only with explicit intent. | `Delete finished crawls` |
| `submit` | Submits the form. | `Submit changes` |

## Correct Use

Begin with the smallest crawl that proves the idea. Use exact start URLs, prefer restrictive boundaries, set limits for unfamiliar sites, and watch the queue after submission. A crawler is not a magic search box: it creates search results only after documents have been loaded, parsed, and indexed.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/CrawlProfileEditor_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/CrawlProfileEditor_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/CrawlProfileEditor_p.html` | `GET` | admin | `source/net/yacy/htroot/CrawlProfileEditor_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `handle` | Crawl profile or job handle. Use the exact value shown by YaCy for the job you want to control. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `terminate` | Terminates the selected crawl profile or running crawl. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `delete` | Deletes the selected URL, path, or index scope. Confirm the scope first. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteTerminatedProfiles` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `submit` | Submit action for the form. Its meaning depends on the surrounding fields. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `domlistlength` | Number of submitted domain-list entries. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /CrawlProfileEditor_p.html?handle=...&terminate=...&delete=...&deleteTerminatedProfiles=...&edit=...
```

## What To Expect

A successful action usually changes crawl state rather than producing finished search results immediately. Expect queued URLs, progress counters, success or error reports, and later changes in search results after indexing catches up.

## Related Pages

- Related crawler work is usually reached through `Crawler_p.html`, `CrawlStartSite.html`, `CrawlStartExpert.html`, or crawl result and queue monitors.
