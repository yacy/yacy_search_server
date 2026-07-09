---
page: htroot/ConfigUser_p.html
help: help/ConfigUser_p.md
title: User Editor
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ConfigUser_p.java
---

# User Editor

## Purpose

User Editor changes one account.

Use it to create or repair a named user without changing unrelated access settings.

## What You Can Do Here

- User Editor changes one account.
- Read the current value before changing it.
- Verify the effect on the public page, status page, or related administration page.

## Page Architecture

Configuration pages usually contain persistent settings. A visible form writes values into YaCy configuration, while the backend may reload subsystems such as language files, network listeners, cache handling, or search presentation.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `username` | Username. | Checkbox/boolean; present usually means enabled. |
| `password` | Password. | Text value; use the page label and surrounding context to choose the exact content. |
| `password2` | Repeat password. | Text value; use the page label and surrounding context to choose the exact content. |
| `firstname` | First name. | Text value; use the page label and surrounding context to choose the exact content. |
| `lastname` | Last name. | Text value; use the page label and surrounding context to choose the exact content. |
| `address` | Address. | Text value; use the page label and surrounding context to choose the exact content. |
| `#[name]#` | right. | right |
| `timelimit` | Timelimit. | Text value; use the page label and surrounding context to choose the exact content. |
| `timeused` | Time used. | Text value; use the page label and surrounding context to choose the exact content. |
| `change` | Username. | `Save User` |
| `delete` | Deletes the selected object or scope. Use only with explicit confirmation. | `Delete User` |
| `cancel` | Username. | `ConfigAccountList_p.html` |

## Correct Use

Read the current value before changing it. Configuration changes often persist beyond the current request and may affect later crawling, search, network contact, authentication, or resource use. Change one operational idea at a time and verify the result.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ConfigUser_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/ConfigUser_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ConfigUser_p.html` | `POST` | admin | `source/net/yacy/htroot/ConfigUser_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `username` | Username. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `password` | Password. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `password2` | Repeat password. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `firstname` | First name. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `lastname` | Last name. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `address` | Address. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `#[name]#` | right. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `timelimit` | Timelimit. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `timeused` | Time used. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `change` | Username. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `delete` | Deletes the selected URL, path, or index scope. Confirm the scope first. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `cancel` | Username. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `user` | User or account value. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /ConfigUser_p.html
Content-Type: application/x-www-form-urlencoded

username=...&password=...&password2=...&firstname=...&lastname=...
```

## What To Expect

A successful change is visible as a saved value, a confirmation, or changed behavior on a related page. Some settings take effect immediately; others require reconnecting, reloading translations, restarting services, or watching the status page.

## Related Pages

- Related configuration work is usually reached from `ConfigBasic.html`, `Settings_p.html`, or the adjacent configuration page in the administration menu.
