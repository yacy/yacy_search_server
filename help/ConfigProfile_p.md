---
page: htroot/ConfigProfile_p.html
help: help/ConfigProfile_p.md
title: Your Personal Profile
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ConfigProfile_p.java
---

# Your Personal Profile

## Purpose

Your Personal Profile describes this peer operator to other users or peers when profile sharing is enabled.

Use it to publish only information that should be visible in the YaCy environment.

## What You Can Do Here

- Your Personal Profile describes this peer operator to other users or peers when profile sharing is enabled.
- Read the current value before changing it.
- Verify the effect on the public page, status page, or related administration page.

## Page Architecture

Configuration pages usually contain persistent settings. A visible form writes values into YaCy configuration, while the backend may reload subsystems such as language files, network listeners, cache handling, or search presentation.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `name` | Name. | Text value; use the page label and surrounding context to choose the exact content. |
| `nickname` | Nick Name. | Text value; use the page label and surrounding context to choose the exact content. |
| `homepage` | Homepage (appears on every Supporter Page as long as your peer is online). | Text value; use the page label and surrounding context to choose the exact content. |
| `email` | eMail. | Text value; use the page label and surrounding context to choose the exact content. |
| `icq` | ICQ. | Text value; use the page label and surrounding context to choose the exact content. |
| `jabber` | Jabber. | Text value; use the page label and surrounding context to choose the exact content. |
| `yahoo` | Yahoo!. | Text value; use the page label and surrounding context to choose the exact content. |
| `msn` | MSN. | Text value; use the page label and surrounding context to choose the exact content. |
| `skype` | Skype. | Text value; use the page label and surrounding context to choose the exact content. |
| `comment` | Comment. | Text value; use the page label and surrounding context to choose the exact content. |
| `set` | Submits and applies the basic configuration. | `Save` |

## Correct Use

Read the current value before changing it. Configuration changes often persist beyond the current request and may affect later crawling, search, network contact, authentication, or resource use. Change one operational idea at a time and verify the result.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ConfigProfile_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/ConfigProfile_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ConfigProfile_p.html` | `GET` | admin | `source/net/yacy/htroot/ConfigProfile_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `name` | Name. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `nickname` | Nick Name. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `homepage` | Homepage (appears on every Supporter Page as long as your peer is online). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `email` | eMail. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `icq` | ICQ. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `jabber` | Jabber. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `yahoo` | Yahoo!. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `msn` | MSN. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `skype` | Skype. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `comment` | Comment. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `set` | Submit action that saves the page settings. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
GET /ConfigProfile_p.html?name=...&nickname=...&homepage=...&email=...&icq=...
```

## What To Expect

A successful change is visible as a saved value, a confirmation, or changed behavior on a related page. Some settings take effect immediately; others require reconnecting, reloading translations, restarting services, or watching the status page.

## Related Pages

- `ViewProfile.html`
- `Supporter.html`
- `WikiCodeHelp.html`
