---
page: htroot/ConfigUpdate_p.html
help: help/ConfigUpdate_p.md
title: System Update
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ConfigUpdate_p.java
---

# System Update

## Purpose

System Update checks and applies YaCy software updates.

Use it when maintaining the peer version, but read the current state before starting an update.

## What You Can Do Here

- System Update checks and applies YaCy software updates.
- Read the current value before changing it.
- Verify the effect on the public page, status page, or related administration page.

## Page Architecture

Configuration pages usually contain persistent settings. A visible form writes values into YaCy configuration, while the backend may reload subsystems such as language files, network listeners, cache handling, or search presentation.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `releasedownload` | Choice value. Options: (unsigned)::(signed). | (unsigned)::(signed) |
| `update` | Date/time value for filtering, display, or scheduling. | `1`, `Install Release` |
| `releaseinstall` | Choice value. Options: (no signature)::(signed). | (no signature)::(signed) |
| `deleteRelease` | Deletion or termination action. Use only with explicit intent. | `Delete Release` |
| `autoUpdate` | Date/time value for filtering, display, or scheduling. | `Check + Download + Install Release Now` |
| `updateMode` | Choice value. Options: `manual`, `auto`. | `manual`, `auto` |
| `releaseType` | Choice value. Options: `main`, `any`. | `main`, `any` |
| `onlySignedFiles` | Choice value. Options: `true`. | `true` |

## Correct Use

Read the current value before changing it. Configuration changes often persist beyond the current request and may affect later crawling, search, network contact, authentication, or resource use. Change one operational idea at a time and verify the result.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ConfigUpdate_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms, user authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/ConfigUpdate_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ConfigUpdate_p.html` | `GET` | admin | `source/net/yacy/htroot/ConfigUpdate_p.java` |
| `/Steering.html` | `POST` | public or page-dependent | `source/net/yacy/htroot/Steering.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `releasedownload` | Choice value. Options: (unsigned)::(signed). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `update` | Submit action that refreshes, updates, or applies the selected setting depending on the page. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `releaseinstall` | Choice value. Options: (no signature)::(signed). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `deleteRelease` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `autoUpdate` | Date/time value for filtering, display, or scheduling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `updateMode` | Choice value. Options: `manual`, `auto`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `releaseType` | Choice value. Options: `main`, `any`. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `onlySignedFiles` | Choice value. Options: `true`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /ConfigUpdate_p.html?releasedownload=...&downloadRelease=...&checkRelease=...&update=...&releaseinstall=...
```

## What To Expect

A successful change is visible as a saved value, a confirmation, or changed behavior on a related page. Some settings take effect immediately; others require reconnecting, reloading translations, restarting services, or watching the status page.

## Related Pages

- Related configuration work is usually reached from `ConfigBasic.html`, `Settings_p.html`, or the adjacent configuration page in the administration menu.
