---
page: htroot/ConfigAppearance_p.html
help: help/ConfigAppearance_p.md
title: Appearance and Integration
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ConfigAppearance_p.java
---

# Appearance and Integration

## Purpose

Appearance and Integration changes the visual identity of the YaCy interface and embedded presentation.

Use it to adapt a peer to a public portal, intranet, or local administration style.

## What You Can Do Here

- Appearance and Integration changes the visual identity of the YaCy interface and embedded presentation.
- Read the current value before changing it.
- Verify the effect on the public page, status page, or related administration page.

## Page Architecture

Configuration pages usually contain persistent settings. A visible form writes values into YaCy configuration, while the backend may reload subsystems such as language files, network listeners, cache handling, or search presentation.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `skin` | Available Skins. | `generic_pd.css` |
| `use_button` | Current skin. | `Use` |
| `delete_button` | Current skin. | `Delete` |
| `url` | URL to inspect, crawl, import, or act on. | URL or URL-derived value; use the exact format shown by the page. |
| `use_skin` | Use this skin. | `on` = Use this skin |
| `install_button` | Install new skin from URL. | `Install` |

## Correct Use

Read the current value before changing it. Configuration changes often persist beyond the current request and may affect later crawling, search, network contact, authentication, or resource use. Change one operational idea at a time and verify the result.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ConfigAppearance_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/ConfigAppearance_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ConfigAppearance_p.html` | `GET` | admin | `source/net/yacy/htroot/ConfigAppearance_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `skin` | Available Skins. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `use_button` | Current skin. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `delete_button` | Current skin. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `use_skin` | Use this skin. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `install_button` | Install new skin from URL. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /ConfigAppearance_p.html?url=...&skin=...&use_button=...&delete_button=...&color_background=...
```

## What To Expect

A successful change is visible as a saved value, a confirmation, or changed behavior on a related page. Some settings take effect immediately; others require reconnecting, reloading translations, restarting services, or watching the status page.

## Related Pages

- `ConfigPortal_p.html`
