---
page: htroot/Translator_p.html
help: help/Translator_p.md
title: Translation Editor
package: content-apps
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/Translator_p.java
---

# Translation Editor

## Purpose

Translation Editor edits YaCy interface translations.

Use it to improve localized text that users see in the web interface.

## What You Can Do Here

- Translation Editor edits YaCy interface translations.
- Edit or inspect the specific content object shown on the page.
- Check whether the result is local-only, user-visible, or peer-visible before publishing.

## Page Architecture

Content application pages store and render peer-local objects such as bookmarks, messages, wiki text, tables, profiles, or translations. They combine form editing with a rendered view of the stored object.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `sourcefile` | UI Translation. | Text value; use the page label and surrounding context to choose the exact content. |
| `filteruntranslated` | filter untranslated. | `true` = filter untranslated |
| `editapproved` | UI Translation. | Text value; use the page label and surrounding context to choose the exact content. |
| `approve` | UI Translation. | Text value; use the page label and surrounding context to choose the exact content. |
| `savetranslationlist` | UI Translation. | `Save translation` |

## Correct Use

Edit content deliberately and check the rendered page after saving. For shared or peer-visible features, assume written content may be read by someone else unless the page clearly says otherwise.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/Translator_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/Translator_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Translator_p.html` | `POST` | admin | `source/net/yacy/htroot/Translator_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `sourcefile` | UI Translation. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `filteruntranslated` | filter untranslated. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `editapproved` | UI Translation. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `approve` | UI Translation. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `savetranslationlist` | UI Translation. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
POST /Translator_p.html
Content-Type: application/x-www-form-urlencoded

sourcefile=...&filteruntranslated=...&targettxt#[tokenid]#=...&editapproved=...&approve=...
```

## What To Expect

Expect stored or rendered content: a message, page, table row, bookmark, profile, translation, or file view. After changes, reload or revisit the object to confirm what was actually saved.

## Related Pages

- `ConfigBasic.html`
- `TransNews_p.html`
