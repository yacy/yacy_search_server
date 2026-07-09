---
page: htroot/IndexFederated_p.html
help: help/IndexFederated_p.md
title: Index Sources & Targets
package: import-export-federation
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexFederated_p.java
---

# Index Sources & Targets

## Purpose

Index Sources & Targets configures external index connections, especially Solr federation.

Use it when YaCy should read from or write to another index service.

## What You Can Do Here

- Index Sources & Targets configures external index connections, especially Solr federation.
- Define the source, target, format, collection, and expected size before starting.
- Monitor progress because large transfers continue beyond the initial request.

## Page Architecture

Import and export pages translate external data formats into YaCy documents or move YaCy index data into another store. Most long-running actions create background work that should be monitored afterward.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `federated.service.solr.indexing.authenticated.allowSelfSigned` | Enables the named feature. | Text value; use the page label and surrounding context to choose the exact content. |
| `solr.indexing.solrremote.writeenabled` | Enables the named feature. | Text value; use the page label and surrounding context to choose the exact content. |

## Correct Use

Prepare source and target details before starting: file path or URL, format, collection, credentials if needed, and expected size. Imports and exports can continue in the background, so confirm progress on the related monitor or queue page.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexFederated_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexFederated_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexFederated_p.html` | `POST` | admin | `source/net/yacy/htroot/IndexFederated_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `federated.service.solr.indexing.authenticated.allowSelfSigned` | Enables the named feature. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `solr.indexing.solrremote.writeenabled` | Enables the named feature. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
POST /IndexFederated_p.html
Content-Type: application/x-www-form-urlencoded

solr.indexing.lazy=...&core.service.fulltext=...&solr.indexing.solrremote=...&federated.service.solr.indexing.authenticated.allowSelfSigned=...&solr.indexing.url=...
```

## What To Expect

Small operations may finish during the request; large imports, exports, harvests, and package operations usually need monitoring. Expect progress, logs, queue entries, or generated files rather than instant final search quality.

## Related Pages

- `IndexSchema_p.html`
