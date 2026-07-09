---
page: htroot/User.html
help: help/User.md
title: User Page
package: configuration-administration
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/User.java
---

# User Page

## Purpose

User Page is the signed-in user's own view of account-related state.

Use it to inspect the current session and user-facing account information.

## What You Can Do Here

- User Page is the signed-in user's own view of account-related state.
- Read the current value before changing it.
- Verify the effect on the public page, status page, or related administration page.

## Page Architecture

Configuration pages usually contain persistent settings. A visible form writes values into YaCy configuration, while the backend may reload subsystems such as language files, network listeners, cache handling, or search presentation.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `username` | old Password. | Checkbox/boolean; present usually means enabled. |
| `password` | old Password. | Text value; use the page label and surrounding context to choose the exact content. |
| `logout` | old Password. | `logout` |
| `oldpass` | old Password. | Text value; use the page label and surrounding context to choose the exact content. |
| `newpass` | new Password. | Text value; use the page label and surrounding context to choose the exact content. |
| `newpass2` | new Password(repetition). | Text value; use the page label and surrounding context to choose the exact content. |
| `changepass` | old Password. | `Change` |

## Correct Use

Read the current value before changing it. Configuration changes often persist beyond the current request and may affect later crawling, search, network contact, authentication, or resource use. Change one operational idea at a time and verify the result.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

Backend checks: user authentication.

## Automation And API

Page backend: `source/net/yacy/htroot/User.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/User.html` | `POST` | public or page-dependent | `source/net/yacy/htroot/User.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `username` | old Password. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `password` | old Password. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `logout` | old Password. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `oldpass` | old Password. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `newpass` | new Password. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `newpass2` | new Password(repetition). | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `changepass` | old Password. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `returnto` | old Password. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /User.html
Content-Type: application/x-www-form-urlencoded

username=...&password=...&logout=...&oldpass=...&newpass=...
```

## What To Expect

A successful change is visible as a saved value, a confirmation, or changed behavior on a related page. Some settings take effect immediately; others require reconnecting, reloading translations, restarting services, or watching the status page.

## Related Pages

- Related configuration work is usually reached from `ConfigBasic.html`, `Settings_p.html`, or the adjacent configuration page in the administration menu.
