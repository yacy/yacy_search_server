---
page: htroot/IndexControlURLs_p.html
help: help/IndexControlURLs_p.md
title: URL Database Administration
package: index-management
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexControlURLs_p.java
---

# URL Database Administration

## Purpose

URL Database Administration manages the URL records stored by YaCy.

Use it when a document is known by URL and must be inspected, exported, deleted, or repaired.

## What You Can Do Here

- URL Database Administration manages the URL records stored by YaCy.
- Select the narrowest URL, host, field, query, queue, or collection scope.
- Treat deletion, rebuilding, and reloading as maintenance operations with visible search impact.

## Page Architecture

Index pages expose stored documents through URL, host, path, Solr field, or queue views. Read-only inspection and destructive maintenance often share the same page, so the target scope matters more than the button label.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `urlhash` | YaCy URL hash identifying an indexed document. | URL or URL-derived value; use the exact format shown by the page. |
| `deleteIndex` | Deletion or termination action. Use only with explicit intent. | Text value; use the page label and surrounding context to choose the exact content. |
| `deleteRemoteSolr` | Deletion or termination action. Use only with explicit intent. | Text value; use the page label and surrounding context to choose the exact content. |
| `deleteRWI` | Deletion or termination action. Use only with explicit intent. | Text value; use the page label and surrounding context to choose the exact content. |
| `deleteCitation` | Deletion or termination action. Use only with explicit intent. | Text value; use the page label and surrounding context to choose the exact content. |
| `deleteFirstSeen` | Deletion or termination action. Use only with explicit intent. | Text value; use the page label and surrounding context to choose the exact content. |
| `deleteCache` | Deletion or termination action. Use only with explicit intent. | Text value; use the page label and surrounding context to choose the exact content. |
| `deleteCrawlQueues` | Deletion or termination action. Use only with explicit intent. | Text value; use the page label and surrounding context to choose the exact content. |
| `deleteRobots` | Deletion or termination action. Use only with explicit intent. | Text value; use the page label and surrounding context to choose the exact content. |
| `deletecomplete` | Deletion or termination action. Use only with explicit intent. | `Delete` |
| `deletedomain` | Deletion or termination action. Use only with explicit intent. | `delete all` |
| `urlhashdelete` | Deletion or termination action. Use only with explicit intent. | `Delete URL` |
| `urlhashdeleteall` | Deletion or termination action. Use only with explicit intent. | `Delete URL and remove all references from words` |

## Correct Use

Use inspection before maintenance. First identify the exact URL, host, field, queue, collection, or query scope, then choose the action. Deletion and re-indexing can be expensive or irreversible from the user's point of view, so never broaden the scope just to make a command easier.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexControlURLs_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms, user authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexControlURLs_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexControlURLs_p.html` | `POST` | admin | `source/net/yacy/htroot/IndexControlURLs_p.java` |
| `/ViewFile.html` | `GET` | public or page-dependent | `source/net/yacy/htroot/ViewFile.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `urlhash` | YaCy URL hash identifying an indexed document. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `deleteIndex` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteRemoteSolr` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteRWI` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteCitation` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteFirstSeen` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteCache` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteCrawlQueues` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteRobots` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deletecomplete` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deletedomain` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `urlhashdelete` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `urlhashdeleteall` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `agentName` | Crawler user-agent profile used for outgoing HTTP requests. Choose a profile that matches the desired identity and politeness behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `domain` | Host or domain scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `search` | Alternative search text parameter accepted by some search endpoints; prefer `query` on browser search pages unless reproducing an existing URL. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `urldelete` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |

Example request shape:

```http
POST /IndexControlURLs_p.html
Content-Type: application/x-www-form-urlencoded

query=...&url=...&urlhash=...&urlstring=...&urlstringsearch=...
```

## What To Expect

The response should make the selected index scope clearer: records listed, queues changed, errors shown, fields rebuilt, or deletion confirmed. Verify user-visible impact with search after maintenance actions.

## Related Pages

- Related index work is usually reached through `IndexBrowser_p.html`, `IndexControlURLs_p.html`, queue pages, deletion pages, or re-indexing monitors.
