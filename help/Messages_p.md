---
page: htroot/Messages_p.html
help: help/Messages_p.md
title: Messages
package: content-apps
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/Messages_p.java
---

# Messages

## Purpose

Messages lists received or stored messages.

Use it to read, manage, or inspect peer communication.

## What You Can Do Here

- Messages lists received or stored messages.
- Edit or inspect the specific content object shown on the page.
- Check whether the result is local-only, user-visible, or peer-visible before publishing.

## Page Architecture

Content application pages store and render peer-local objects such as bookmarks, messages, tables, profiles, or translations. They combine form editing with a rendered view of the stored object.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `hash` | YaCy hash identifier for a peer, URL, row, or stored object. Use exact values copied from YaCy output. | Exact YaCy hash value. |
| `submit` | Submits the form. | `Compose` |

## Correct Use

Edit content deliberately and check the rendered page after saving. For shared or peer-visible features, assume written content may be read by someone else unless the page clearly says otherwise.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/MessageSend_p.html`, `/Messages_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/Messages_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/MessageSend_p.html` | `GET` | admin | `source/net/yacy/htroot/MessageSend_p.java` |
| `/Messages_p.html` | `GET or POST` | admin | `source/net/yacy/htroot/Messages_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `hash` | YaCy hash identifier for a peer, URL, row, or stored object. Use exact values copied from YaCy output. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `submit` | Submit action for the form. Its meaning depends on the surrounding fields. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
GET /MessageSend_p.html?hash=...&submit=...&action=...&attachmentsize=...&message=...
```

## What To Expect

Expect stored or rendered content: a message, page, table row, bookmark, profile, translation, or file view. After changes, reload or revisit the object to confirm what was actually saved.

## Related Pages

- `ViewProfile.html`
- `MessageSend_p.html`
