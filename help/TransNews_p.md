---
page: htroot/TransNews_p.html
help: help/TransNews_p.md
title: Translation News
package: content-apps
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/TransNews_p.java
---

# Translation News

## Purpose

Translation News shows translation-related changes and notices.

Use it when maintaining or reviewing interface translations.

## What You Can Do Here

- Translation News shows translation-related changes and notices.
- Edit or inspect the specific content object shown on the page.
- Check whether the result is local-only, user-visible, or peer-visible before publishing.

## Page Architecture

Content application pages store and render peer-local objects such as bookmarks, messages, wiki text, tables, profiles, or translations. They combine form editing with a rendered view of the stored object.

| Control | Meaning | Values or examples |
| --- | --- | --- |

## Correct Use

Edit content deliberately and check the rendered page after saving. For shared or peer-visible features, assume written content may be read by someone else unless the page clearly says otherwise.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/TransNews_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/TransNews_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/TransNews_p.html` | `POST` | admin | `source/net/yacy/htroot/TransNews_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |

Example request shape:

```http
POST /TransNews_p.html
Content-Type: application/x-www-form-urlencoded

publishtranslation=...&filename=...&source=...&target=...&voteNegative=...
```

## What To Expect

Expect stored or rendered content: a message, page, table row, bookmark, profile, translation, or file view. After changes, reload or revisit the object to confirm what was actually saved.

## Related Pages

- `News.html`
- `Translator_p.html`
- `ConfigBasic.html`
