---
page: htroot/ConfigAccounts_p.html
help: help/ConfigAccounts_p.md
title: User Accounts
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ConfigAccounts_p.java
---

# User Accounts

## Purpose

User Accounts defines administrator and user credentials.

Use it early, especially before the peer is reachable from another machine.

## What You Can Do Here

- User Accounts defines administrator and user credentials.
- Read the current value before changing it.
- Verify the effect on the public page, status page, or related administration page.

## Page Architecture

Configuration pages usually contain persistent settings. A visible form writes values into YaCy configuration, while the backend may reload subsystems such as language files, network listeners, cache handling, or search presentation.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `access` | Access from localhost without account / Access only with qualified account. Options: `localhost` = Access from localhost without account, `account` = Access only with qualified account. | `localhost` = Access from localhost without account, `account` = Access only with qualified account |
| `adminuser` | Peer User. | Text value; use the page label and surrounding context to choose the exact content. |
| `adminpw1` | New Peer Password. | Text value; use the page label and surrounding context to choose the exact content. |
| `adminpw2` | Repeat Peer Password. | Text value; use the page label and surrounding context to choose the exact content. |
| `setAdmin` | Peer User. | `Define Administrator` |
| `adminAccountAllPages` | Access from localhost without account. | Access from localhost without account |
| `setAccess` | Access from localhost without account. | `Set Access Rules` |
| `user` | Select user. | `newuser` = New user |
| `change_user` | Select user. | `Edit User` |
| `delete_user` | Select user. | `Delete User` |
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

## Correct Use

Read the current value before changing it. Configuration changes often persist beyond the current request and may affect later crawling, search, network contact, authentication, or resource use. Change one operational idea at a time and verify the result.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ConfigAccounts_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/ConfigAccounts_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ConfigAccounts_p.html` | `POST` | admin | `source/net/yacy/htroot/ConfigAccounts_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `access` | Access from localhost without account / Access only with qualified account. Options: `localhost` = Access from localhost without account, `account` = Access only with qualified account. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `adminuser` | Peer User. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `adminpw1` | New Peer Password. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `adminpw2` | Repeat Peer Password. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `setAdmin` | Peer User. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `adminAccountAllPages` | Access from localhost without account. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `setAccess` | Access from localhost without account. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `user` | Select user. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `change_user` | Select user. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `delete_user` | Select user. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
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
| `current_user` | Username. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /ConfigAccounts_p.html
Content-Type: application/x-www-form-urlencoded

access=...&adminuser=...&adminpw1=...&adminpw2=...&setAdmin=...
```

## What To Expect

A successful change is visible as a saved value, a confirmation, or changed behavior on a related page. Some settings take effect immediately; others require reconnecting, reloading translations, restarting services, or watching the status page.

## Related Pages

- `ConfigAccountList_p.html`
