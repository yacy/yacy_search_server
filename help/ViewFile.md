---
page: htroot/ViewFile.html
help: help/ViewFile.md
title: View URL Content
package: core-search-public
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/ViewFile.java
---

# View URL Content

## Purpose

View URL Content shows a fetched or cached document as YaCy sees it.

Use it to diagnose parsing, cache, snippet, and indexing problems for one URL.

## What You Can Do Here

- View URL Content shows a fetched or cached document as YaCy sees it.
- Choose query, target, content type, or integration options according to the user intent.
- Keep ordinary read-only viewing separate from authenticated actions that alter stored data.

## Page Architecture

Public search pages turn request parameters into result lists, previews, snippets, feeds, or integration fragments. They are mostly read-oriented, but some result actions can bookmark, recommend, blacklist, or delete references when authenticated.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `viewMode` | View as. Options: `iframeWeb` = Original from Web, `iframeCache` = Original from Cache, `plain` = Plain Text, `parsed` = Parsed Text, `sentences` = Parsed Sentences, `words` = Parsed Tokens/Words, `links` = Link List, `schema` = Schema Fields, `iframeCitations` = Citation Report. | `iframeWeb` = Original from Web, `iframeCache` = Original from Cache, `plain` = Plain Text, `parsed` = Parsed Text, `sentences` = Parsed Sentences, `words` = Parsed Tokens/Words, `links` = Link List, `schema` = Schema Fields, `iframeCitations` = Citation Report |
| `url` | URL to inspect, crawl, import, or act on. | URL or URL-derived value; use the exact format shown by the page. |
| `show` | View as. | `Show Metadata`, `Show Snippet`, `Show` |
| `search` | Alternative search text parameter accepted by some search endpoints; prefer `query` on browser search pages unless reproducing an existing URL. | Text value; use the page label and surrounding context to choose the exact content. |

## Correct Use

Use an exact URL or stored reference. Compare the fetched content, cached content, parser output, and headers when diagnosing why a result snippet or indexed text looks wrong.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

Backend checks: user authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/ViewFile.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ViewFile.html` | `GET` | public or page-dependent | `source/net/yacy/htroot/ViewFile.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `viewMode` | View as. Options: `iframeWeb` = Original from Web, `iframeCache` = Original from Cache, `plain` = Plain Text, `parsed` = Parsed Text, `sentences` = Parsed Sentences, `words` = Parsed Tokens/Words, `links` = Link List, `schema` = Schema Fields, `iframeCitations` = Citation Report. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `show` | View as. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search` | Alternative search text parameter accepted by some search endpoints; prefer `query` on browser search pages unless reproducing an existing URL. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `agentName` | Crawler user-agent profile used for outgoing HTTP requests. Choose a profile that matches the desired identity and politeness behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `urlHash` | View as. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `urlhash` | YaCy URL hash identifying an indexed document. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `words` | View as. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /ViewFile.html?query=...&url=...&urlhash=...&viewMode=...&show=...
```

## What To Expect

The page shows YaCy's view of one document. Differences between this view and the live website often explain stale snippets, missing text, parser failures, or cache behavior.

## Related Pages

- Related search work usually continues on `yacysearch.html`, `index.html`, `ViewFile.html`, quick crawl, or the search integration pages.
