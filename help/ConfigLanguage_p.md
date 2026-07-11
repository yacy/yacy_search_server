---
page: htroot/ConfigLanguage_p.html
help: help/ConfigLanguage_p.md
title: Language selection
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ConfigLanguage_p.java
---

# Language selection

## Purpose

Language selection changes the interface translation.

Use it when operators or users need YaCy pages in another language.

## What You Can Do Here

- Language selection changes the interface translation.
- Read the current value before changing it.
- Verify the effect on the public page, status page, or related administration page.

## Page Architecture

Configuration pages usually contain persistent settings. A visible form writes values into YaCy configuration, while the backend may reload subsystems such as language files, network listeners, cache handling, or search presentation.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `language` | Interface language. Values are `browser` for the browser-preferred language, `default` for English, or a language code such as `de`, `fr`, `es`, `zh`, `ja`, or `ko`. | Text value; use the page label and surrounding context to choose the exact content. |
| `use_button` | Current language. | `Use` |
| `delete` | Deletes the selected object or scope. Use only with explicit confirmation. | `Delete` |
| `url` | URL to inspect, crawl, import, or act on. | URL or URL-derived value; use the exact format shown by the page. |
| `use_lang` | Use this language. | `on` = Use this language |

## Correct Use

Read the current value before changing it. Configuration changes often persist beyond the current request and may affect later crawling, search, network contact, authentication, or resource use. Change one operational idea at a time and verify the result.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ConfigLanguage_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/ConfigLanguage_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ConfigLanguage_p.html` | `GET` | admin | `source/net/yacy/htroot/ConfigLanguage_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `language` | Interface language. Values are `browser` for the browser-preferred language, `default` for English, or a language code such as `de`, `fr`, `es`, `zh`, `ja`, or `ko`. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `use_button` | Current language. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `delete` | Deletes the selected URL, path, or index scope. Confirm the scope first. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `use_lang` | Use this language. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /ConfigLanguage_p.html?language=...&url=...&use_button=...&delete=...&use_lang=...
```

## What To Expect

A successful change is visible as a saved value, a confirmation, or changed behavior on a related page. Some settings take effect immediately; others require reconnecting, reloading translations, restarting services, or watching the status page.
