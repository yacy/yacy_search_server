---
page: htroot/ConfigAccounts_p.html
help: help/ConfigAccounts_p.md
title: Admin Account
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ConfigAccounts_p.java
---

# Admin Account

## Purpose

This page configures YaCy's single built-in administrator account and page-protection policy.

Use it early, especially before the peer is reachable from another machine.

## What You Can Do Here

- Configure the built-in administrator username and password used by HTTP BASIC or DIGEST authentication.
- Choose whether trusted localhost requests are granted administrator access without credentials.
- Choose whether authentication protects only `_p` pages or all applicable pages.
- Read the current value before changing it.
- Verify the effect on the public page, status page, or related administration page.

## Page Architecture

The page separates administrator credentials from access rules. The two access-rule switches share one form and are saved automatically whenever either switch changes. Each switch selects its own existing backend action, so changing one rule does not rewrite the other.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `adminuser` | Username of the built-in administrator. | Non-empty text, up to 32 characters. |
| `adminpw1` | New administrator password. | Non-empty password. |
| `adminpw2` | Confirmation of the new administrator password. | Must equal `adminpw1`. |
| `setAdmin` | Saves only the administrator username and password. | Submit action. |
| `adminAccountForLocalhost` | Grants administrator authority to eligible localhost requests without authentication. | Checkbox: present = on, absent = off. Default: on. |
| `setLocalhostAccess` | Saves only the localhost-access policy. | Submitted automatically when `adminAccountForLocalhost` changes. |
| `adminAccountAllPages` | Extends administrator authorization from `_p` administration pages to all applicable pages. | Checkbox: present = on, absent = off. Default: off. |
| `setAccess` | Saves only the page-protection policy. | Submitted automatically when `adminAccountAllPages` changes. |

## Correct Use

Replace the default `admin` / `yacy` credentials even when localhost access remains enabled. The warning is displayed directly above the credential form while the default password hash is active.

If the administrator password must be reset from the console, run `bin/passwd.sh` from the YaCy installation directory. With no password argument, the script prompts for the new password without echoing it. It supports both a running peer, through the account configuration endpoint, and a stopped peer, through the local configuration file.

Turning off localhost access means that local browser requests also need the configured administrator credentials. Turning on protection for all pages broadens the authentication requirement. Changing either switch automatically reloads the page after saving that rule; it does not change the other rule or the administrator credentials.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

There are no secondary YaCy users or per-feature roles. Privileged actions use the built-in administrator authority only. When localhost access without an account is enabled, eligible local requests receive that same authority without credentials.

Legacy `DATA/SETTINGS/user.heap` files are no longer loaded or modified. Keep or remove such a file according to your own backup policy.

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
| `adminuser` | Non-empty built-in administrator username. | Send only with `setAdmin`. Changing it invalidates the previous username. |
| `adminpw1` | Non-empty new administrator password. | Send only with `setAdmin`; YaCy stores its DIGEST-compatible hash, not the clear text. |
| `adminpw2` | Repetition of `adminpw1`. | Must match exactly. |
| `setAdmin` | Selects the credential-change action. | Does not change either access-policy switch. |
| `adminAccountForLocalhost` | Enables unauthenticated administrator access for eligible localhost requests when present. | Omit the checkbox to disable it; send only with `setLocalhostAccess`. |
| `setLocalhostAccess` | Selects the localhost-policy action. | Does not change credentials or page-protection scope. |
| `adminAccountAllPages` | Enables protection of all applicable pages when present. | Omit the checkbox to disable it; send only with `setAccess`. `_p` administration pages remain protected in both states. |
| `setAccess` | Selects the page-protection action. | Does not change credentials or localhost policy. |

Example request shapes (each also requires a valid `transactionToken`):

```http
POST /ConfigAccounts_p.html
Content-Type: application/x-www-form-urlencoded

transactionToken=...&adminuser=admin&adminpw1=new-secret&adminpw2=new-secret&setAdmin=

POST /ConfigAccounts_p.html
Content-Type: application/x-www-form-urlencoded

transactionToken=...&adminAccountForLocalhost=on&setLocalhostAccess=1

POST /ConfigAccounts_p.html
Content-Type: application/x-www-form-urlencoded

transactionToken=...&adminAccountAllPages=on&setAccess=1
```

## What To Expect

Access-rule changes take effect immediately when a switch is changed. After changing the username or password, the HTTP server's cached administrator identity is reset. A browser may continue sending cached BASIC credentials until its authentication cache is cleared or the browser session is closed.

## Related Pages

- `Status.html`
