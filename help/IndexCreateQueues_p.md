---
page: htroot/IndexCreateQueues_p.html
help: help/IndexCreateQueues_p.md
title: '' Crawl Queue
package: index-management
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexCreateQueues_p.java
---

# '' Crawl Queue

## Purpose

Crawl Queue shows pending and failed indexing work.

Use it to understand why documents have not reached the searchable index yet.

## What You Can Do Here

- Crawl Queue shows pending and failed indexing work.
- Select the narrowest URL, host, field, query, queue, or collection scope.
- Treat deletion, rebuilding, and reloading as maintenance operations with visible search impact.

## Page Architecture

Index pages expose stored documents through URL, host, path, Solr field, or queue views. Read-only inspection and destructive maintenance often share the same page, so the target scope matters more than the button label.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `pattern` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | Text value; use the page label and surrounding context to choose the exact content. |
| `option` | Choice value. Options: `5` = Initiator, `3` = Profile, `4` = Depth, `6` = Modified Date, `2` = Anchor Name, `1` = URL. | `5` = Initiator, `3` = Profile, `4` = Depth, `6` = Modified Date, `2` = Anchor Name, `1` = URL |
| `delete` | Deletes the selected object or scope. Use only with explicit confirmation. | `Delete` |

## Correct Use

Use inspection before maintenance. First identify the exact URL, host, field, queue, collection, or query scope, then choose the action. Deletion and re-indexing can be expensive or irreversible from the user's point of view, so never broaden the scope just to make a command easier.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexCreateQueues_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexCreateQueues_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexCreateQueues_p.html` | `POST` | admin | `source/net/yacy/htroot/IndexCreateQueues_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `pattern` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `option` | Choice value. Options: `5` = Initiator, `3` = Profile, `4` = Depth, `6` = Modified Date, `2` = Anchor Name, `1` = URL. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `delete` | Deletes the selected URL, path, or index scope. Confirm the scope first. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `sort` | Sort field or sort direction. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `urlsPerHost` | Host or domain scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /IndexCreateQueues_p.html
Content-Type: application/x-www-form-urlencoded

pattern=...&option=...&delete=...&embed=...&sort=...
```

## What To Expect

The response should make the selected index scope clearer: records listed, queues changed, errors shown, fields rebuilt, or deletion confirmed. Verify user-visible impact with search after maintenance actions.

## Related Pages

- Related index work is usually reached through `IndexBrowser_p.html`, `IndexControlURLs_p.html`, queue pages, deletion pages, or re-indexing monitors.
