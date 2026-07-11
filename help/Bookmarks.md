---
page: htroot/Bookmarks.html
help: help/Bookmarks.md
title: Bookmarks
package: content-apps
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/Bookmarks.java
---

# Bookmarks

## Purpose

Bookmarks stores useful URLs known to the peer.

Use it to save, organize, and later re-use important search or crawl targets.

## What You Can Do Here

- Bookmarks stores useful URLs known to the peer.
- Edit or inspect the specific content object shown on the page.
- Check whether the result is local-only, user-visible, or peer-visible before publishing.

## Page Architecture

Content application pages store and render peer-local objects such as bookmarks, messages, tables, profiles, or translations. They combine form editing with a rendered view of the stored object.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `url` | URL to inspect, crawl, import, or act on. | URL or URL-derived value; use the exact format shown by the page. |
| `title` | Title. | Text value; use the page label and surrounding context to choose the exact content. |
| `description` | Description. | Text value; use the page label and surrounding context to choose the exact content. |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Search terms, for example `climate data` or a more specific phrase. |
| `path` | URL path, URL prefix, or host/path scope. In index tools it decides which part of the stored URL tree is inspected or changed. | URL, host/path prefix, or path scope shown by the page. |
| `tags` | Tags (comma separated). | `imported` |
| `public` | Public. Options: `public` = yes, `private` = no. | `public` = yes, `private` = no |
| `feed` | Bookmark is a newsfeed. | `true` = Bookmark is a newsfeed |
| `add` | URL. | `create`, `Save` |
| `xmlfile` | File. | Text value; use the page label and surrounding context to choose the exact content. |
| `importxml` | File. | `import` |
| `htmlfile` | File. | Text value; use the page label and surrounding context to choose the exact content. |
| `importbookmarks` | File. | `import` |
| `tag` | URL. Options: All (), (). | All (), () |
| `startautosearch` | URL. | `start it` |

## Correct Use

Edit content deliberately and check the rendered page after saving. For shared or peer-visible features, assume written content may be read by someone else unless the page clearly says otherwise.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

Public bookmarks can be read without authentication. Private bookmarks, imports, edits, and other write actions require built-in administrator authority: HTTP BASIC/DIGEST, or the configured localhost-without-account access.

## Automation And API

Page backend: `source/net/yacy/htroot/Bookmarks.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Bookmarks.html` | `GET` | public or page-dependent | `source/net/yacy/htroot/Bookmarks.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `title` | Title. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `description` | Description. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `path` | URL path, URL prefix, or host/path scope. In index tools it decides which part of the stored URL tree is inspected or changed. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `tags` | Tags (comma separated). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `public` | Public. Options: `public` = yes, `private` = no. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `feed` | Bookmark is a newsfeed. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `add` | URL. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `xmlfile` | File. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `importxml` | File. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `htmlfile` | File. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `importbookmarks` | File. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `tag` | URL. Options: All (), (). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `startautosearch` | URL. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `agentName` | Crawler user-agent profile used for outgoing HTTP requests. Choose a profile that matches the desired identity and politeness behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `delete` | Deletes the selected URL, path, or index scope. Confirm the scope first. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |

Example request shape:

```http
GET /Bookmarks.html?query=...&url=...&title=...&description=...&path=...
```

## What To Expect

Expect stored or rendered content: a message, page, table row, bookmark, profile, translation, or file view. After changes, reload or revisit the object to confirm what was actually saved.

## Related Pages

- `yacysearch.html`
