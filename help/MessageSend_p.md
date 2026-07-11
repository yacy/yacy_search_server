---
page: htroot/MessageSend_p.html
help: help/MessageSend_p.md
title: Send message
package: content-apps
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/MessageSend_p.java
---

# Send message

## Purpose

Send Message composes a peer-local or network message.

Use it to contact another peer or user when messaging is enabled.

## What You Can Do Here

- Send Message composes a peer-local or network message.
- Edit or inspect the specific content object shown on the page.
- Check whether the result is local-only, user-visible, or peer-visible before publishing.

## Page Architecture

Content application pages store and render peer-local objects such as bookmarks, messages, tables, profiles, or translations. They combine form editing with a rendered view of the stored object.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `subject` | Subject. | Text value; use the page label and surrounding context to choose the exact content. |
| `message` | Text. | Text value; use the page label and surrounding context to choose the exact content. |
| `new` | Subject. | `Enter` |
| `preview` | Subject. | `Preview` |

## Correct Use

Edit content deliberately and check the rendered page after saving. For shared or peer-visible features, assume written content may be read by someone else unless the page clearly says otherwise.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/MessageSend_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/MessageSend_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/MessageSend_p.html` | `POST` | admin | `source/net/yacy/htroot/MessageSend_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `subject` | Subject. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `message` | Text. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `new` | Subject. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `preview` | Subject. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `attachmentsize` | Subject. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `hash` | YaCy hash identifier for a peer, URL, row, or stored object. Use exact values copied from YaCy output. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `messagesize` | Subject. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /MessageSend_p.html
Content-Type: application/x-www-form-urlencoded

subject=...&message=...&new=...&preview=...&attachmentsize=...
```

## What To Expect

Expect stored or rendered content: a message, page, table row, bookmark, profile, translation, or file view. After changes, reload or revisit the object to confirm what was actually saved.

## Related Pages

- `WikiCodeHelp.html`
- `Network.html`
