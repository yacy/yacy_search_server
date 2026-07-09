---
page: htroot/CrawlStartExpert.html
help: help/CrawlStartExpert.md
title: Crawl Start
package: crawler
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/CrawlStartExpert.java
---

# Crawl Start

## Purpose

Expert Crawl Start exposes the full crawler model: start sources, depth, filters, media handling, recrawl policy, cache behavior, and indexing rules.

Use it when the simple site crawl page is too limited for a careful crawl plan.

## What You Can Do Here

- Expert Crawl Start exposes the full crawler model: start sources, depth, filters, media handling, recrawl policy, cache behavior, and indexing rules.
- Choose limits, boundaries, and queue actions that match the size of the source.
- Watch the crawler monitor afterward because indexing happens after fetching and parsing.

## Page Architecture

Crawler pages separate three decisions: the source to load, the rules that decide which discovered URLs are accepted, and the monitor or control action that follows the job after it starts.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `crawlingMode` | Crawl source mode, such as URL, site list, or sitemap. | `url` = Add Crawl result to collection (important for Index Pack generation), `sitelist` = Add Crawl result to collection (important for Index Pack generation), `sitemap` = Add Crawl result to collection (important for Index Pack generation), `file` = Add Crawl result to collection (important for Index Pack generation) |
| `crawlingURL` | Start URL for a crawl. Include `http://` or `https://`; for local files or intranet protocols use the expert crawler where appropriate. | Absolute URL, for example `https://example.org/docs/`. |
| `bookmarkTitle` | Add Crawl result to collection (important for Index Pack generation). | Text value; use the page label and surrounding context to choose the exact content. |
| `expandSiteListBtn` | Add Crawl result to collection (important for Index Pack generation). | Text value; use the page label and surrounding context to choose the exact content. |
| `sitemapURL` | Sitemap URL used as the crawl source. It should point to a valid XML sitemap or sitemap index. | Absolute URL to an XML sitemap. |
| `crawlingFile` | Uploaded file name or submitted URL-list source for multi-URL crawls. | Text value; use the page label and surrounding context to choose the exact content. |
| `collection` | Collection name. Use it to group crawled or imported documents and to search or manage that group later. | Collection name such as `user`, `docs`, or a project-specific name. |
| `timezoneOffset` | Client timezone offset in minutes. YaCy uses it for date display or schedule calculations. | Text value; use the page label and surrounding context to choose the exact content. |
| `indexText` | Index extracted text. | index text |
| `indexMedia` | Index media resources. | index media |
| `crawlOrder` | Do Remote Indexing. | Do Remote Indexing |
| `intention` | Optional human label describing why the crawl was started. | Text value; use the page label and surrounding context to choose the exact content. |
| `crawlingDepth` | Maximum link depth from the start URL. Depth 0 loads only the submitted document; larger values follow links farther away. | Integer link depth. |
| `directDocByURL` | index text. | index text |
| `crawlingDepthExtension` | index text. | Integer value. |
| `crawlingDomMaxCheck` | Enables maximum page count for the crawl. | Use |
| `crawlingDomMaxPages` | Maximum number of pages allowed when `crawlingDomMaxCheck` is enabled. | Integer page limit. |
| `crawlingQ` | index text. | index text |
| `obeyHtmlRobotsNoindex` | Respect HTML robots noindex. | index text |
| `obeyHtmlRobotsNofollow` | Respect HTML robots nofollow. | index text |
| `crawlerAlwaysCheckMediaType` | Do not load URLs with an unsupported file extension. Options: `false` = Do not load URLs with an unsupported file extension, `true` = Do not load URLs with an unsupported file extension. | `false` = Do not load URLs with an unsupported file extension, `true` = Do not load URLs with an unsupported file extension |
| `range` | Crawl boundary, usually domain-wide or limited to the start path. | `domain` = index text, `subpath` = index text, `wide` = index text |
| `mustmatch` | Regular expression that discovered URLs must match before they enter the crawl. | Regular expression; default allow-all behavior is equivalent to matching everything. |
| `mustnotmatch` | Regular expression that discovered URLs must not match. Use it to exclude logout URLs, calendars, filters, or unwanted directories. | Regular expression; use a never-match pattern to disable exclusion. |
| `crawlerOriginURLMustMatch` | index text. | URL or URL-derived value; use the exact format shown by the page. |
| `crawlerOriginURLMustNotMatch` | index text. | URL or URL-derived value; use the exact format shown by the page. |
| `ipMustmatch` | IP address pattern that target hosts must match. | Pattern or filter expression; test narrow expressions before broad use. |
| `ipMustnotmatch` | IP address pattern that target hosts must not match. | Pattern or filter expression; test narrow expressions before broad use. |
| `countryMustMatchSwitch` | index text. Options: `0` = index text, `1` = index text. | `0` = index text, `1` = index text |
| `countryMustMatchList` | Country-code allow list used when country filtering is enabled. | Pattern or filter expression; test narrow expressions before broad use. |
| `indexmustmatch` | Regular expression that fetched URLs must match before they are indexed. | Regular expression for URLs allowed into the index. |
| `indexmustnotmatch` | Regular expression excluding fetched URLs from indexing. | Regular expression for URLs excluded from the index. |
| `noindexWhenCanonicalUnequalURL` | Skips indexing when the document declares a canonical URL different from the fetched URL. | URL or URL-derived value; use the exact format shown by the page. |
| `indexcontentmustmatch` | Regular expression that extracted content must match before indexing. | Pattern or filter expression; test narrow expressions before broad use. |
| `indexcontentmustnotmatch` | Regular expression excluding documents by extracted content. | Pattern or filter expression; test narrow expressions before broad use. |
| `indexMediaTypeMustMatch` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | Pattern or filter expression; test narrow expressions before broad use. |
| `indexMediaTypeMustNotMatch` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | Pattern or filter expression; test narrow expressions before broad use. |
| `indexSolrQueryMustMatch` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | Pattern or filter expression; test narrow expressions before broad use. |
| `indexSolrQueryMustNotMatch` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | Pattern or filter expression; test narrow expressions before broad use. |
| `default_valency` | Add Crawl result to collection (important for Index Pack generation). Options: `EVAL` = Add Crawl result to collection (important for Index Pack generation), `IGNORE` = Add Crawl result to collection (important for Index Pack generation). | `EVAL` = Add Crawl result to collection (important for Index Pack generation), `IGNORE` = Add Crawl result to collection (important for Index Pack generation) |
| `valency_switch_tag_names` | HTML tag names whose links use switched valency behavior. | Text value; use the page label and surrounding context to choose the exact content. |
| `cleanSearchCache` | Clean up search events cache. | Clean up search events cache |
| `deleteold` | Clean up search events cache. Options: `off` = Clean up search events cache, `on` = Clean up search events cache, `age` = Clean up search events cache. | `off` = Clean up search events cache, `on` = Clean up search events cache, `age` = Clean up search events cache |
| `deleteIfOlderNumber` | Number part of the age threshold for deleting old documents. | Integer value. |
| `deleteIfOlderUnit` | Unit for deleting old documents, typically `year`, `month`, `day`, or `hour`. | `year`, `month`, `day`, or `hour`. |
| `recrawl` | Whether and how existing documents are refreshed. | `nodoubles` = Add Crawl result to collection (important for Index Pack generation), `reload` = Add Crawl result to collection (important for Index Pack generation) |
| `reloadIfOlderNumber` | Number part of the age threshold for refreshing older documents. | Integer value. |
| `reloadIfOlderUnit` | Unit for the refresh threshold, typically `year`, `month`, `day`, or `hour`. | `year`, `month`, `day`, or `hour`. |
| `storeHTCache` | Store to Web Cache. | Store to Web Cache |
| `cachePolicy` | How YaCy may use cached content while loading. | `nocache` = Store to Web Cache, `iffresh` = Store to Web Cache, `ifexist` = Store to Web Cache, `cacheonly` = Store to Web Cache |
| `agentName` | Crawler user-agent profile used for outgoing HTTP requests. Choose a profile that matches the desired identity and politeness behavior. | One of the user-agent names offered by the page. |
| `vocabulary_#[name]#_class` | Scraping Fields. | Text value; use the page label and surrounding context to choose the exact content. |

## Correct Use

Begin with the smallest crawl that proves the idea. Use exact start URLs, prefer restrictive boundaries, set limits for unfamiliar sites, and watch the queue after submission. A crawler is not a magic search box: it creates search results only after documents have been loaded, parsed, and indexed.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

Protected related endpoint(s): `/Crawler_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/CrawlStartExpert.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Crawler_p.html` | `POST` | admin | `source/net/yacy/htroot/Crawler_p.java` |
| `/CrawlStartExpert.html` | `GET or POST` | public or page-dependent | `source/net/yacy/htroot/CrawlStartExpert.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `crawlingMode` | Crawl source mode. `url` starts from one URL, `sitemap` reads sitemap entries, `file`/list modes submit many URLs, and page-specific modes may prepare these values for `Crawler_p.html`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingURL` | Start URL for a crawl. Include `http://` or `https://`; for local files or intranet protocols use the expert crawler where appropriate. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `bookmarkTitle` | Add Crawl result to collection (important for Index Pack generation). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `sitemapURL` | Sitemap URL used as the crawl source. It should point to a valid XML sitemap or sitemap index. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingFile` | Uploaded file name or submitted URL-list source for multi-URL crawls. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `collection` | Collection name. Use it to group crawled or imported documents and to search or manage that group later. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `timezoneOffset` | Client timezone offset in minutes. YaCy uses it for date display or schedule calculations. | Read-only request context for date handling. |
| `indexText` | Indexes extracted text content when enabled. Disable only for specialized media-only crawls. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexMedia` | Indexes discovered media resources when enabled. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlOrder` | Do Remote Indexing. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `intention` | Optional human label describing why the crawl was started. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingDepth` | Maximum link depth from the start URL. Depth 0 loads only the submitted document; larger values follow links farther away. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `directDocByURL` | Treats the submitted URL as a direct document target rather than mainly as a link-discovery seed. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingDepthExtension` | index text. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingDomMaxCheck` | Enables the maximum-page safeguard for a domain crawl. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingDomMaxPages` | Maximum number of pages allowed when `crawlingDomMaxCheck` is enabled. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingQ` | Queues discovered URLs for crawler processing; sitemap mode normally enables queued crawling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `obeyHtmlRobotsNoindex` | Honors HTML robots `noindex` instructions while indexing. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `obeyHtmlRobotsNofollow` | Honors HTML robots `nofollow` instructions while discovering links. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlerAlwaysCheckMediaType` | Do not load URLs with an unsupported file extension. Options: `false` = Do not load URLs with an unsupported file extension, `true` = Do not load URLs with an unsupported file extension. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `range` | Boundary for a simple site crawl. `domain` stays in the same domain; `subpath` stays below the start path; expert pages may expose wider policies. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `mustmatch` | Regular expression that discovered URLs must match before they enter the crawl. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `mustnotmatch` | Regular expression that discovered URLs must not match. Use it to exclude logout URLs, calendars, filters, or unwanted directories. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `crawlerOriginURLMustMatch` | index text. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `crawlerOriginURLMustNotMatch` | index text. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `ipMustmatch` | IP address pattern that target hosts must match. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `ipMustnotmatch` | IP address pattern that target hosts must not match. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `countryMustMatchSwitch` | Enables country-code filtering for crawl targets. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `countryMustMatchList` | Country-code allow list used when country filtering is enabled. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `indexmustmatch` | Regular expression that fetched URLs must match before they are indexed. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `indexmustnotmatch` | Regular expression excluding fetched URLs from indexing. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `noindexWhenCanonicalUnequalURL` | Skips indexing when the document declares a canonical URL different from the fetched URL. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexcontentmustmatch` | Regular expression that extracted content must match before indexing. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `indexcontentmustnotmatch` | Regular expression excluding documents by extracted content. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `indexMediaTypeMustMatch` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `indexMediaTypeMustNotMatch` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `indexSolrQueryMustMatch` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `indexSolrQueryMustNotMatch` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `default_valency` | Default link valency used by the crawler when deciding how links contribute to discovery and indexing. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `valency_switch_tag_names` | HTML tag names whose links use switched valency behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `cleanSearchCache` | Clears cached search results so newly crawled material can appear without stale search state. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `deleteold` | Policy for deleting or replacing older index entries during crawl setup. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteIfOlderNumber` | Number part of the age threshold for deleting old documents. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteIfOlderUnit` | Unit for deleting old documents, typically `year`, `month`, `day`, or `hour`. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `recrawl` | Recrawl policy for already known URLs, such as avoiding duplicates, reloading, or using scheduler rules depending on the page. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `reloadIfOlderNumber` | Number part of the age threshold for refreshing older documents. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `reloadIfOlderUnit` | Unit for the refresh threshold, typically `year`, `month`, `day`, or `hour`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `storeHTCache` | Store to Web Cache. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `cachePolicy` | Cache strategy for fetching documents, for example whether cached material may be reused when fresh enough. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `agentName` | Crawler user-agent profile used for outgoing HTTP requests. Choose a profile that matches the desired identity and politeness behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `vocabulary_#[name]#_class` | Scraping Fields. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `MaxSameHostInQueue` | Maximum queued URLs allowed for the same host. It prevents one host from occupying too much of the crawl queue. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `callback` | JSONP callback name for legacy script clients. Leave empty for normal HTML or JSON-style use. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingFile$file` | Multipart file content containing crawl URLs. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingPerformance` | Crawler speed preset. Use slower presets for shared servers; use custom only when you understand the load impact. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawlingstart` | Submit action that creates a crawl job. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `customPPM` | Custom pages-per-minute target used with custom crawl performance. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `domlistlength` | Number of submitted domain-list entries. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `followFrames` | Allows the crawler to follow frame and iframe sources. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `handle` | Crawl profile or job handle. Use the exact value shown by YaCy for the job you want to control. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `hidewebstructuregraph` | Hide the web-structure graph on crawler pages. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `latencyFactor` | Politeness multiplier for crawl delay. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `pause` | Pauses the selected queue or crawl process. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `queues_terminate_all` | Stops all crawler queues. This is a broad emergency control, not a normal crawl setting. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `showwebstructuregraph` | Show the web-structure graph on crawler pages. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `terminate` | Terminates the selected crawl profile or running crawl. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |

Example request shape:

```http
POST /Crawler_p.html
Content-Type: application/x-www-form-urlencoded

crawlingURL=...&range=...&crawlingDepth=...&crawlingDomMaxPages=...&crawlingMode=...
```

## What To Expect

A successful action usually changes crawl state rather than producing finished search results immediately. Expect queued URLs, progress counters, success or error reports, and later changes in search results after indexing catches up.

## Related Pages

- `IndexFederated_p.html`
- `RemoteCrawl_p.html`
- `https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html`
- `RegexTest.html`
- `IndexSchema_p.html`
- `https://lucene.apache.org/solr/guide/6_6/the-standard-query-parser.html`
