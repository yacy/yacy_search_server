---
page: htroot/IndexReIndexMonitor_p.html
help: help/IndexReIndexMonitor_p.md
title: Field Re-Indexing
package: index-management
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexReIndexMonitor_p.java
---

# Field Re-Indexing

## Purpose

Field Re-Indexing rebuilds selected index fields for documents already stored.

Use it after schema, parser, or ranking changes when existing records need recalculation.

## What You Can Do Here

- Field Re-Indexing rebuilds selected index fields for documents already stored.
- Select the narrowest URL, host, field, query, queue, or collection scope.
- Treat deletion, rebuilding, and reloading as maintenance operations with visible search impact.

## Page Architecture

Index pages expose stored documents through URL, host, path, Solr field, or queue views. Read-only inspection and destructive maintenance often share the same page, so the target scope matters more than the button label.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `reindexnow` | Solr query. | `start reindex job now` |
| `stopreindex` | Solr query. | `stop reindexing` |
| `recrawlquerytext` | Solr query / Edit Solr Query. | Text value; use the page label and surrounding context to choose the exact content. |
| `simulateRecrawl` | Solr query. | `Simulate` |
| `includefailedurls` | Include failed URLs / Include failed urls. Options: Include failed URLs, Include failed urls. | Include failed URLs, Include failed urls |
| `deleteOnRecrawl` | Delete URLs / Delete urls. Options: Delete URLs, Delete urls. | Delete URLs, Delete urls |
| `recrawlDefaults` | Solr query. | `Set defaults` |
| `recrawlnow` | Solr query. | `start recrawl job now` |
| `updquery` | Edit Solr Query. | `update` |
| `stoprecrawl` | Edit Solr Query. | `stop recrawl job` |

## Correct Use

Use inspection before maintenance. First identify the exact URL, host, field, queue, collection, or query scope, then choose the action. Deletion and re-indexing can be expensive or irreversible from the user's point of view, so never broaden the scope just to make a command easier.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexReIndexMonitor_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexReIndexMonitor_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexReIndexMonitor_p.html` | `POST` | admin | `source/net/yacy/htroot/IndexReIndexMonitor_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `reindexnow` | Solr query. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `stopreindex` | Solr query. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `recrawlquerytext` | Solr query / Edit Solr Query. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `simulateRecrawl` | Solr query. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `includefailedurls` | Include failed URLs / Include failed urls. Options: Include failed URLs, Include failed urls. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `deleteOnRecrawl` | Delete URLs / Delete urls. Options: Delete URLs, Delete urls. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `recrawlDefaults` | Solr query. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `recrawlnow` | Solr query. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `updquery` | Edit Solr Query. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `stoprecrawl` | Edit Solr Query. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /IndexReIndexMonitor_p.html
Content-Type: application/x-www-form-urlencoded

reindexnow=...&stopreindex=...&recrawlquerytext=...&simulateRecrawl=...&includefailedurls=...
```

## What To Expect

The response should make the selected index scope clearer: records listed, queues changed, errors shown, fields rebuilt, or deletion confirmed. Verify user-visible impact with search after maintenance actions.

## Related Pages

- Related index work is usually reached through `IndexBrowser_p.html`, `IndexControlURLs_p.html`, queue pages, deletion pages, or re-indexing monitors.
