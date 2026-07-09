---
page: htroot/IndexCreateLoaderQueue_p.html
help: help/IndexCreateLoaderQueue_p.md
title: Loader Queue
package: index-management
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexCreateLoaderQueue_p.java
---

# Loader Queue

## Purpose

Loader Queue shows documents waiting to be loaded or parsed.

Use it when crawler work exists but fetching or parsing appears delayed.

## What You Can Do Here

- Loader Queue shows documents waiting to be loaded or parsed.
- Select the narrowest URL, host, field, query, queue, or collection scope.
- Treat deletion, rebuilding, and reloading as maintenance operations with visible search impact.

## Page Architecture

Index pages expose stored documents through URL, host, path, Solr field, or queue views. Read-only inspection and destructive maintenance often share the same page, so the target scope matters more than the button label.

## Correct Use

Use inspection before maintenance. First identify the exact URL, host, field, queue, collection, or query scope, then choose the action. Deletion and re-indexing can be expensive or irreversible from the user's point of view, so never broaden the scope just to make a command easier.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexCreateLoaderQueue_p.java`.

No request parameters are needed for normal use of this page.

## What To Expect

The response should make the selected index scope clearer: records listed, queues changed, errors shown, fields rebuilt, or deletion confirmed. Verify user-visible impact with search after maintenance actions.

## Related Pages

- Related index work is usually reached through `IndexBrowser_p.html`, `IndexControlURLs_p.html`, queue pages, deletion pages, or re-indexing monitors.
