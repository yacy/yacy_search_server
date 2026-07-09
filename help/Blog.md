---
page: htroot/Blog.html
help: help/Blog.md
title: Blog
package: content-apps
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/Blog.java
---

# Blog

## Purpose

Blog provides a local publication area inside YaCy.

Use it for peer-local announcements or notes when that feature is enabled.

## What You Can Do Here

- Blog provides a local publication area inside YaCy.
- Edit or inspect the specific content object shown on the page.
- Check whether the result is local-only, user-visible, or peer-visible before publishing.

## Page Architecture

Content application pages store and render peer-local objects such as bookmarks, messages, wiki text, tables, profiles, or translations. They combine form editing with a rendered view of the stored object.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `author` | Author. | Text value; use the page label and surrounding context to choose the exact content. |
| `subject` | Subject. | Text value; use the page label and surrounding context to choose the exact content. |
| `content` | Text. | Text value; use the page label and surrounding context to choose the exact content. |
| `commentMode` | Comments. Options: `0` = deactivated, `1` = activated, `2` = moderated. | `0` = deactivated, `1` = activated, `2` = moderated |
| `submit` | Submits the form. | `Submit` |
| `preview` | Author. | `Preview` |
| `discard` | Author. | `Discard` |
| `xmlfile` | Author. | Text value; use the page label and surrounding context to choose the exact content. |
| `importxml` | Author. | `Import` |

## Correct Use

Edit content deliberately and check the rendered page after saving. For shared or peer-visible features, assume written content may be read by someone else unless the page clearly says otherwise.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

Backend checks: user authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/Blog.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Blog.html` | `POST` | public or page-dependent | `source/net/yacy/htroot/Blog.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `author` | Author. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `subject` | Subject. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `content` | Text. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `commentMode` | Comments. Options: `0` = deactivated, `1` = activated, `2` = moderated. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `submit` | Submit action for the form. Its meaning depends on the surrounding fields. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `preview` | Author. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `discard` | Author. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `xmlfile` | Author. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `importxml` | Author. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `delete` | Deletes the selected URL, path, or index scope. Confirm the scope first. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `page` | Author. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /Blog.html
Content-Type: application/x-www-form-urlencoded

author=...&subject=...&content=...&commentMode=...&submit=...
```

## What To Expect

Expect stored or rendered content: a message, page, table row, bookmark, profile, translation, or file view. After changes, reload or revisit the object to confirm what was actually saved.

## Related Pages

- `BlogComments.html`
- `WikiHelp.html`
