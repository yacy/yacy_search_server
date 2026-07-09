---
page: htroot/CrawlStartSite.html
help: help/CrawlStartSite.md
title: Crawl Start
package: crawler
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/CrawlStartSite.java
---

# Crawl Start

## Purpose

Site Crawling is the beginner-friendly way to teach YaCy a website. The operator gives a start URL, chooses the crawl boundary, optionally limits the number of documents, and starts a crawl job that feeds the index.

Use it for small, deliberate crawls before moving to expert crawling.

The page is organized around a simple site-crawl form and a short hint section. The form defines the start URL, the crawl boundary, an optional page limit, and the target collection.

## What You Can Do Here

- Start a small crawl from one website or one section of a website.
- Set boundaries before starting or changing a crawl, so the crawler loads the intended part of the web and no more.
- Put the discovered documents into a named collection when the crawl belongs to a project or topic.
- Check the resulting queues, errors, and indexed documents before widening a crawl or making it faster.

## Page Architecture

This page is a simplified front end for `Crawler_p.html`. It prepares a normal crawl-start request with a start URL, a boundary, an optional page limit, and a collection. YaCy then hands the request to the crawler backend, which creates a crawl profile and queues the first URL.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `crawlingMode` | Crawl source mode. On this page `url` is the normal start-URL crawl; `sitelist` and `sitemap` are prepared modes that become available when YaCy detects a link list or sitemap. | `url`, `sitelist`, `sitemap` |
| `crawlingURL` | Start URL for a crawl. Include `http://` or `https://`; for local files or intranet protocols use the expert crawler where appropriate. | Absolute URL, for example `https://example.org/docs/`. |
| `bookmarkTitle` | Optional title discovered for the submitted URL; normally read-only on the simple crawl page. | Usually omitted by scripts. |
| `expandSiteListBtn` | Browser-only button that expands a discovered link list; do not send it in API calls. | Browser action only. |
| `sitemapURL` | Sitemap URL used as the crawl source. It should point to a valid XML sitemap or sitemap index. | Absolute URL to an XML sitemap. |
| `range` | Crawl boundary. `domain` allows the crawl to stay within the same domain; `subpath` limits it to URLs below the start path. | `domain`, `subpath` |
| `crawlingDomMaxCheck` | Enables the page-count limit. Use it for first crawls and for sites where the full size is unknown. | checkbox |
| `crawlingDomMaxPages` | Maximum number of pages allowed when `crawlingDomMaxCheck` is enabled. | Integer page limit. |
| `collection` | Collection name. Use it to group crawled or imported documents and to search or manage that group later. | Collection name such as `user`, `docs`, or a project-specific name. |
| `crawlingstart` | Submit action that creates the crawl job. | `Start New Crawl` |

## Correct Use

Start narrow. Enter a full URL including `http://` or `https://`, choose `subpath` when you only want a section of a site, and enable a page limit for first crawls. A website crawl is iterative: YaCy loads the start page, extracts links, keeps the links that match the boundary rules, queues them, loads those pages, and repeats the process until the queue is empty or a limit stops it.

After submitting, watch `Crawler_p.html` and verify indexed results before widening depth or limits. If the user only asked for one known document, use the expert/direct-document options instead of a site crawl.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

Protected related endpoint(s): `/Crawler_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/CrawlStartSite.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Crawler_p.html` | `POST` | admin | `source/net/yacy/htroot/Crawler_p.java` |

### Parameter Guide

For this simple page, an agent normally needs only the first group of parameters below. The remaining parameters belong to the shared `Crawler_p.html` backend and are included because they may appear in recorded API calls or advanced crawl automation.

Simple site-crawl request:

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `crawlingMode` | Crawl source mode. `url` starts from one URL, `sitemap` reads sitemap entries, `file`/list modes submit many URLs, and page-specific modes may prepare these values for `Crawler_p.html`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingURL` | Start URL for a crawl. Include `http://` or `https://`; for local files or intranet protocols use the expert crawler where appropriate. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `bookmarkTitle` | Optional title discovered for the submitted URL; normally read-only on the simple crawl page. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `sitemapURL` | Sitemap URL used as the crawl source. It should point to a valid XML sitemap or sitemap index. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `range` | Boundary for a simple site crawl. `domain` stays in the same domain; `subpath` stays below the start path; expert pages may expose wider policies. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingDomMaxCheck` | Enables the maximum-page safeguard for a domain crawl. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingDomMaxPages` | Maximum number of pages allowed when `crawlingDomMaxCheck` is enabled. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `collection` | Collection name. Use it to group crawled or imported documents and to search or manage that group later. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingstart` | Submit action that creates a crawl job. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Advanced `Crawler_p.html` controls:

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `MaxSameHostInQueue` | Maximum queued URLs allowed for the same host. It prevents one host from occupying too much of the crawl queue. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `agentName` | Crawler user-agent profile used for outgoing HTTP requests. Choose a profile that matches the desired identity and politeness behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `cachePolicy` | Cache strategy for fetching documents, for example whether cached material may be reused when fresh enough. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `callback` | JSONP callback name for legacy script clients. Leave empty for normal HTML or JSON-style use. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `cleanSearchCache` | Clears cached search results so newly crawled material can appear without stale search state. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `countryMustMatchList` | Country-code allow list used when country filtering is enabled. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `countryMustMatchSwitch` | Enables country-code filtering for crawl targets. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `crawlOrder` | Crawl ordering strategy, such as balanced host scheduling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlerAlwaysCheckMediaType` | Check media type before deciding parser/indexing behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingDepth` | Maximum link depth from the start URL. Depth 0 loads only the submitted document; larger values follow links farther away. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingDepthExtension` | Additional depth behavior for special crawl modes. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingFile` | Uploaded file name or submitted URL-list source for multi-URL crawls. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingFile$file` | Multipart file content containing crawl URLs. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingPerformance` | Crawler speed preset. Use slower presets for shared servers; use custom only when you understand the load impact. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingQ` | Queues discovered URLs for crawler processing; sitemap mode normally enables queued crawling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `customPPM` | Custom pages-per-minute target used with custom crawl performance. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `default_valency` | Default link valency used by the crawler when deciding how links contribute to discovery and indexing. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `deleteIfOlderNumber` | Number part of the age threshold for deleting old documents. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteIfOlderUnit` | Unit for deleting old documents, typically `year`, `month`, `day`, or `hour`. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteold` | Policy for deleting or replacing older index entries during crawl setup. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `directDocByURL` | Treats the submitted URL as a direct document target rather than mainly as a link-discovery seed. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `domlistlength` | Number of submitted domain-list entries. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `followFrames` | Allows the crawler to follow frame and iframe sources. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `handle` | Crawl profile or job handle. Use the exact value shown by YaCy for the job you want to control. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `hidewebstructuregraph` | Hide the web-structure graph on crawler pages. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexMedia` | Indexes discovered media resources when enabled. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexText` | Indexes extracted text content when enabled. Disable only for specialized media-only crawls. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexcontentmustmatch` | Regular expression that extracted content must match before indexing. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `indexcontentmustnotmatch` | Regular expression excluding documents by extracted content. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `indexmustmatch` | Regular expression that fetched URLs must match before they are indexed. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `indexmustnotmatch` | Regular expression excluding fetched URLs from indexing. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `intention` | Optional human label describing why the crawl was started. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ipMustmatch` | IP address pattern that target hosts must match. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `ipMustnotmatch` | IP address pattern that target hosts must not match. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `latencyFactor` | Politeness multiplier for crawl delay. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `mustmatch` | Regular expression that discovered URLs must match before they enter the crawl. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `mustnotmatch` | Regular expression that discovered URLs must not match. Use it to exclude logout URLs, calendars, filters, or unwanted directories. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `noindexWhenCanonicalUnequalURL` | Skips indexing when the document declares a canonical URL different from the fetched URL. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `obeyHtmlRobotsNofollow` | Honors HTML robots `nofollow` instructions while discovering links. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `obeyHtmlRobotsNoindex` | Honors HTML robots `noindex` instructions while indexing. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `pause` | Pauses the selected queue or crawl process. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `queues_terminate_all` | Stops all crawler queues. This is a broad emergency control, not a normal crawl setting. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `recrawl` | Recrawl policy for already known URLs, such as avoiding duplicates, reloading, or using scheduler rules depending on the page. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `reloadIfOlderNumber` | Number part of the age threshold for refreshing older documents. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `reloadIfOlderUnit` | Unit for the refresh threshold, typically `year`, `month`, `day`, or `hour`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `showwebstructuregraph` | Show the web-structure graph on crawler pages. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `storeHTCache` | Stores fetched documents in YaCy hypertext cache. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `terminate` | Terminates the selected crawl profile or running crawl. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `timezoneOffset` | Client timezone offset in minutes. YaCy uses it for date display or schedule calculations. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `valency_switch_tag_names` | HTML tag names whose links use switched valency behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /Crawler_p.html
Content-Type: application/x-www-form-urlencoded

crawlingstart=Start+New+Crawl&crawlingMode=url&crawlingURL=https%3A%2F%2Fexample.org%2Fdocs%2F&range=subpath&crawlingDomMaxCheck=on&crawlingDomMaxPages=100
```

## What To Expect

Successful crawl actions usually create or modify crawl jobs. Verify progress on `Crawler_p.html`, then confirm indexed content through search or index browsing.

## Related Pages

- `CrawlStartExpert.html`
- `Automation_p.html`
