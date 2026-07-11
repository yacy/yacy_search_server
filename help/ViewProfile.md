---
page: htroot/ViewProfile.html
help: help/ViewProfile.md
title: Remote Peer Profile
package: content-apps
access: mixed
kind: ui-page
backend_java: source/net/yacy/htroot/ViewProfile.java
---

# Remote Peer Profile

## Purpose

Remote Peer Profile displays profile data for a YaCy peer.

Use it to understand who operates a peer and what information it publishes.

## What You Can Do Here

- Remote Peer Profile displays profile data for a YaCy peer.
- Edit or inspect the specific content object shown on the page.
- Check whether the result is local-only, user-visible, or peer-visible before publishing.

## Page Architecture

Content application pages store and render peer-local objects such as bookmarks, messages, tables, profiles, or translations. They combine form editing with a rendered view of the stored object.

## Correct Use

Edit content deliberately and check the rendered page after saving. For shared or peer-visible features, assume written content may be read by someone else unless the page clearly says otherwise.

## Access And Safety

The page may be visible, but the backend performs authentication checks for protected actions.

Protected related endpoint(s): `/ViewProfile.html`.

Backend checks: administrator authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/ViewProfile.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ViewProfile.html` | `GET or POST` | mixed | `source/net/yacy/htroot/ViewProfile.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `hash` | YaCy hash identifier for a peer, URL, row, or stored object. Use exact values copied from YaCy output. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET or POST /ViewProfile.html?display=...&hash=...
```

## What To Expect

Expect stored or rendered content: a message, page, table row, bookmark, profile, translation, or file view. After changes, reload or revisit the object to confirm what was actually saved.

## Related Pages

- `ConfigProfile_p.html`
