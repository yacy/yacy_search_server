---
page: htroot/IndexSchema_p.html
help: help/IndexSchema_p.md
title: Solr Schema Editor
package: index-management
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexSchema_p.java
---

# Solr Schema Editor

## Purpose

Solr Schema Editor exposes fields used by YaCy's Solr index.

Use it to inspect field availability and advanced schema behavior before changing ranking or import assumptions.

## What You Can Do Here

- Solr Schema Editor exposes fields used by YaCy's Solr index.
- Select the narrowest URL, host, field, query, queue, or collection scope.
- Treat deletion, rebuilding, and reloading as maintenance operations with visible search impact.

## Page Architecture

Index pages expose stored documents through URL, host, path, Solr field, or queue views. Read-only inspection and destructive maintenance often share the same page, so the target scope matters more than the button label.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `filter` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | `active`, `disabled` |
| `schema_#[key]#` | Choice value. Options: `checked`. | `checked` |
| `set` | Submits and applies the basic configuration. | `Set` |
| `reindexSolr` | Reindex documents. | `reindex Solr` |

## Correct Use

Use inspection before maintenance. First identify the exact URL, host, field, queue, collection, or query scope, then choose the action. Deletion and re-indexing can be expensive or irreversible from the user's point of view, so never broaden the scope just to make a command easier.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexSchema_p.html`, `/IndexReIndexMonitor_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexSchema_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexSchema_p.html` | `GET` | admin | `source/net/yacy/htroot/IndexSchema_p.java` |
| `/IndexReIndexMonitor_p.html` | `POST` | admin | `source/net/yacy/htroot/IndexReIndexMonitor_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `filter` | Filter text or expression used to narrow the displayed records. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `schema_#[key]#` | Choice value. Options: `checked`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `set` | Submit action that saves the page settings. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `reindexSolr` | Reindex documents. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `deleteOnRecrawl` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `reindexnow` | Reindex documents. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /IndexSchema_p.html?core=...&filter=...&schema_#[key]#=...&schema_solrfieldname_#[key]#=...&set=...
```

## What To Expect

The response should make the selected index scope clearer: records listed, queues changed, errors shown, fields rebuilt, or deletion confirmed. Verify user-visible impact with search after maintenance actions.

## Related Pages

- `IndexReIndexMonitor_p.html`
