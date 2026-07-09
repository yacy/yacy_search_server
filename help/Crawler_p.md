---
page: htroot/Crawler_p.html
help: help/Crawler_p.md
title: Crawler
package: crawler
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/Crawler_p.java
---

# Crawler

## Purpose

Crawler is the control room for running crawl jobs and queues.

Use it to watch progress, pause or continue work, terminate jobs, and understand what the crawler is doing now.

## What You Can Do Here

- Crawler is the control room for running crawl jobs and queues.
- Choose limits, boundaries, and queue actions that match the size of the source.
- Watch the crawler monitor afterward because indexing happens after fetching and parsing.

## Page Architecture

Crawler pages separate three decisions: the source to load, the rules that decide which discovered URLs are accepted, and the monitor or control action that follows the job after it starts.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `customPPM` | Custom pages-per-minute target used with custom crawl performance. | Integer pages per minute. |
| `latencyFactor` | LF. | Text value; use the page label and surrounding context to choose the exact content. |
| `MaxSameHostInQueue` | Maximum queued URLs allowed for the same host. It prevents one host from occupying too much of the crawl queue. | Integer value. |
| `crawlingPerformance` | PPM. | `set` |
| `terminate` | Deletion or termination action. Use only with explicit intent. | `Terminate` |
| `showwebstructuregraph` | Show the web-structure graph on crawler pages. | `show link structure` |
| `hidewebstructuregraph` | PPM. | `hide graphic` |

## Correct Use

Begin with the smallest crawl that proves the idea. Use exact start URLs, prefer restrictive boundaries, set limits for unfamiliar sites, and watch the queue after submission. A crawler is not a magic search box: it creates search results only after documents have been loaded, parsed, and indexed.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/Crawler_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/Crawler_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Crawler_p.html` | `GET` | admin | `source/net/yacy/htroot/Crawler_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `customPPM` | Custom pages-per-minute target used with custom crawl performance. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `latencyFactor` | LF. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `MaxSameHostInQueue` | Maximum queued URLs allowed for the same host. It prevents one host from occupying too much of the crawl queue. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingPerformance` | Crawler speed preset. Use slower presets for shared servers; use custom only when you understand the load impact. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `terminate` | Terminates the selected crawl profile or running crawl. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `showwebstructuregraph` | Show the web-structure graph on crawler pages. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `hidewebstructuregraph` | PPM. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `agentName` | Crawler user-agent profile used for outgoing HTTP requests. Choose a profile that matches the desired identity and politeness behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `cachePolicy` | Cache strategy for fetching documents, for example whether cached material may be reused when fresh enough. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `callback` | JSONP callback name for legacy script clients. Leave empty for normal HTML or JSON-style use. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `cleanSearchCache` | Clears cached search results so newly crawled material can appear without stale search state. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `collection` | Collection name. Use it to group crawled or imported documents and to search or manage that group later. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `countryMustMatchList` | Country-code allow list used when country filtering is enabled. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `countryMustMatchSwitch` | Enables country-code filtering for crawl targets. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `crawlOrder` | Crawl ordering strategy, such as balanced host scheduling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlerAlwaysCheckMediaType` | Check media type before deciding parser/indexing behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingDepth` | Maximum link depth from the start URL. Depth 0 loads only the submitted document; larger values follow links farther away. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingDepthExtension` | Additional depth behavior for special crawl modes. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingDomMaxCheck` | Enables the maximum-page safeguard for a domain crawl. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingDomMaxPages` | Maximum number of pages allowed when `crawlingDomMaxCheck` is enabled. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingFile` | Uploaded file name or submitted URL-list source for multi-URL crawls. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingFile$file` | Multipart file content containing crawl URLs. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingMode` | Crawl source mode. `url` starts from one URL, `sitemap` reads sitemap entries, `file`/list modes submit many URLs, and page-specific modes may prepare these values for `Crawler_p.html`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingQ` | Queues discovered URLs for crawler processing; sitemap mode normally enables queued crawling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingURL` | Start URL for a crawl. Include `http://` or `https://`; for local files or intranet protocols use the expert crawler where appropriate. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingstart` | Submit action that creates a crawl job. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `default_valency` | Default link valency used by the crawler when deciding how links contribute to discovery and indexing. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `deleteIfOlderNumber` | Number part of the age threshold for deleting old documents. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteIfOlderUnit` | Unit for deleting old documents, typically `year`, `month`, `day`, or `hour`. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteold` | Policy for deleting or replacing older index entries during crawl setup. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `directDocByURL` | Treats the submitted URL as a direct document target rather than mainly as a link-discovery seed. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `domlistlength` | Number of submitted domain-list entries. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `followFrames` | Allows the crawler to follow frame and iframe sources. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `handle` | Crawl profile or job handle. Use the exact value shown by YaCy for the job you want to control. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexMedia` | Indexes discovered media resources when enabled. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexText` | Indexes extracted text content when enabled. Disable only for specialized media-only crawls. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexcontentmustmatch` | Regular expression that extracted content must match before indexing. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `indexcontentmustnotmatch` | Regular expression excluding documents by extracted content. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `indexmustmatch` | Regular expression that fetched URLs must match before they are indexed. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `indexmustnotmatch` | Regular expression excluding fetched URLs from indexing. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `intention` | Optional human label describing why the crawl was started. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ipMustmatch` | IP address pattern that target hosts must match. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `ipMustnotmatch` | IP address pattern that target hosts must not match. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `mustmatch` | Regular expression that discovered URLs must match before they enter the crawl. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `mustnotmatch` | Regular expression that discovered URLs must not match. Use it to exclude logout URLs, calendars, filters, or unwanted directories. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `noindexWhenCanonicalUnequalURL` | Skips indexing when the document declares a canonical URL different from the fetched URL. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `obeyHtmlRobotsNofollow` | Honors HTML robots `nofollow` instructions while discovering links. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `obeyHtmlRobotsNoindex` | Honors HTML robots `noindex` instructions while indexing. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `pause` | Pauses the selected queue or crawl process. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `queues_terminate_all` | Stops all crawler queues. This is a broad emergency control, not a normal crawl setting. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `range` | Boundary for a simple site crawl. `domain` stays in the same domain; `subpath` stays below the start path; expert pages may expose wider policies. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `recrawl` | Recrawl policy for already known URLs, such as avoiding duplicates, reloading, or using scheduler rules depending on the page. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `reloadIfOlderNumber` | Number part of the age threshold for refreshing older documents. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `reloadIfOlderUnit` | Unit for the refresh threshold, typically `year`, `month`, `day`, or `hour`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `sitemapURL` | Sitemap URL used as the crawl source. It should point to a valid XML sitemap or sitemap index. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `storeHTCache` | Stores fetched documents in YaCy hypertext cache. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `timezoneOffset` | Client timezone offset in minutes. YaCy uses it for date display or schedule calculations. | Read-only request context for date handling. |
| `valency_switch_tag_names` | HTML tag names whose links use switched valency behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /Crawler_p.html?crawlingURL=...&range=...&crawlingDepth=...&crawlingDomMaxPages=...&customPPM=...
```

## What To Expect

A successful action usually changes crawl state rather than producing finished search results immediately. Expect queued URLs, progress counters, success or error reports, and later changes in search results after indexing catches up.

## Related Pages

- `IndexCreateQueues_p.html`
- `IndexCreateLoaderQueue_p.html`
- `PerformanceQueues_p.html`
- `IndexFederated_p.html`
- `RemoteCrawl_p.html`
