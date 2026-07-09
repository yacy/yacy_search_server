---
page: htroot/IndexControlRWIs_p.html
help: help/IndexControlRWIs_p.md
title: Reverse Word Index Administration
package: index-management
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexControlRWIs_p.java
---

# Reverse Word Index Administration

## Purpose

Reverse Word Index Administration manages the word-to-document index.

Use it for low-level diagnosis when search terms do not connect to documents as expected.

## What You Can Do Here

- Reverse Word Index Administration manages the word-to-document index.
- Select the narrowest URL, host, field, query, queue, or collection scope.
- Treat deletion, rebuilding, and reloading as maintenance operations with visible search impact.

## Page Architecture

Index pages expose stored documents through URL, host, path, Solr field, or queue views. Read-only inspection and destructive maintenance often share the same page, so the target scope matters more than the button label.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `maxReferencesRadio` | Choice value. Options: `off`, `on`. | `off`, `on` |
| `description` | Description text. | Text value; use the page label and surrounding context to choose the exact content. |
| `title` | Human-readable title. | Text value; use the page label and surrounding context to choose the exact content. |
| `url` | URL to inspect, crawl, import, or act on. | URL or URL-derived value; use the exact format shown by the page. |
| `indexof` | Interprets the query as an index-of style request when supported. | Checkbox/boolean; present usually means enabled. |
| `keyhashdelete` | Deletion or termination action. Use only with explicit intent. | `Delete reference to selected URLs` |
| `blacklistdomains` | Host or domain scope. | `Add selected domains to blacklist` |

## Correct Use

Use inspection before maintenance. First identify the exact URL, host, field, queue, collection, or query scope, then choose the action. Deletion and re-indexing can be expensive or irreversible from the user's point of view, so never broaden the scope just to make a command easier.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexControlRWIs_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexControlRWIs_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexControlRWIs_p.html` | `POST` | admin | `source/net/yacy/htroot/IndexControlRWIs_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `maxReferencesRadio` | Choice value. Options: `off`, `on`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `description` | Description text. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `title` | Human-readable title. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexof` | Interprets the query as an index-of style request when supported. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `keyhashdelete` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `blacklistdomains` | Host or domain scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `agentName` | Crawler user-agent profile used for outgoing HTTP requests. Choose a profile that matches the desired identity and politeness behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `host` | Host or domain scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `hostHash` | Host or domain scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `keyhashdeleteall` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |

Example request shape:

```http
POST /IndexControlRWIs_p.html
Content-Type: application/x-www-form-urlencoded

url=...&keystring=...&keystringsearch=...&keyhash=...&keyhashsearch=...
```

## What To Expect

The response should make the selected index scope clearer: records listed, queues changed, errors shown, fields rebuilt, or deletion confirmed. Verify user-visible impact with search after maintenance actions.

## Related Pages

- `IndexControlURLs_p.html`
