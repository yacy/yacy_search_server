---
page: htroot/Wiki.html
help: help/Wiki.md
title: Wiki
package: content-apps
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/Wiki.java
---

# Wiki

## Purpose

Wiki provides editable local documentation pages.

Use it for notes and knowledge that should live inside the YaCy peer.

## What You Can Do Here

- Wiki provides editable local documentation pages.
- Edit or inspect the specific content object shown on the page.
- Check whether the result is local-only, user-visible, or peer-visible before publishing.

## Page Architecture

Content application pages store and render peer-local objects such as bookmarks, messages, wiki text, tables, profiles, or translations. They combine form editing with a rendered view of the stored object.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `access` | Grant Write Access to. | `all`, `admin` |
| `author` | Author. | Text value; use the page label and surrounding context to choose the exact content. |
| `content` | Text. | Text value; use the page label and surrounding context to choose the exact content. |
| `submit` | Submits the form. | `Submit` |
| `preview` | Author. | `Preview` |
| `view` | Author. | `Discard` |
| `old` | Compare version from. | Text value; use the page label and surrounding context to choose the exact content. |
| `viewold` | Compare version from. | `Show` |
| `new` | with version from. | Text value; use the page label and surrounding context to choose the exact content. |
| `compare` | Compare version from. | `Compare` |

## Correct Use

Edit content deliberately and check the rendered page after saving. For shared or peer-visible features, assume written content may be read by someone else unless the page clearly says otherwise.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

Backend checks: user authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/Wiki.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Wiki.html` | `POST` | public or page-dependent | `source/net/yacy/htroot/Wiki.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `access` | Grant Write Access to. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `author` | Author. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `content` | Text. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `submit` | Submit action for the form. Its meaning depends on the surrounding fields. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `preview` | Author. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `view` | Author. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `old` | Compare version from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `viewold` | Compare version from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `new` | with version from. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `compare` | Compare version from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `diff` | Compare version from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `reason` | Author. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /Wiki.html
Content-Type: application/x-www-form-urlencoded

access=...&author=...&content=...&submit=...&preview=...
```

## What To Expect

Expect stored or rendered content: a message, page, table row, bookmark, profile, translation, or file view. After changes, reload or revisit the object to confirm what was actually saved.

## Related Pages

- `WikiHelp.html`
