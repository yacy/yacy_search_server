---
page: htroot/IndexCreateParserErrors_p.html
help: help/IndexCreateParserErrors_p.md
title: Parser Errors
package: index-management
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexCreateParserErrors_p.java
---

# Parser Errors

## Purpose

Parser Errors lists documents YaCy could fetch but could not parse cleanly.

Use it to find format, charset, parser, or document-quality problems.

## What You Can Do Here

- Parser Errors lists documents YaCy could fetch but could not parse cleanly.
- Select the narrowest URL, host, field, query, queue, or collection scope.
- Treat deletion, rebuilding, and reloading as maintenance operations with visible search impact.

## Page Architecture

Index pages expose stored documents through URL, host, path, Solr field, or queue views. Read-only inspection and destructive maintenance often share the same page, so the target scope matters more than the button label.

| Control | Meaning | Values or examples |
| --- | --- | --- |

## Correct Use

Use inspection before maintenance. First identify the exact URL, host, field, queue, collection, or query scope, then choose the action. Deletion and re-indexing can be expensive or irreversible from the user's point of view, so never broaden the scope just to make a command easier.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexCreateParserErrors_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexCreateParserErrors_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexCreateParserErrors_p.html` | `POST` | admin | `source/net/yacy/htroot/IndexCreateParserErrors_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |

Example request shape:

```http
POST /IndexCreateParserErrors_p.html
Content-Type: application/x-www-form-urlencoded

moreRejected=...&clearRejected=...&showRejected=...
```

## What To Expect

The response should make the selected index scope clearer: records listed, queues changed, errors shown, fields rebuilt, or deletion confirmed. Verify user-visible impact with search after maintenance actions.

## Related Pages

- Related index work is usually reached through `IndexBrowser_p.html`, `IndexControlURLs_p.html`, queue pages, deletion pages, or re-indexing monitors.
