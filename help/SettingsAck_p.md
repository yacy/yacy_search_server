---
page: htroot/SettingsAck_p.html
help: help/SettingsAck_p.md
title: Settings Acknowledge
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/SettingsAck_p.java
---

# Settings Acknowledge

## Purpose

Settings Acknowledge is the confirmation page after settings are applied.

Use it to verify what YaCy accepted and what may still require a restart or follow-up check.

## What You Can Do Here

- Settings Acknowledge is the confirmation page after settings are applied.
- Read the current value before changing it.
- Verify the effect on the public page, status page, or related administration page.

## Page Architecture

Configuration pages usually contain persistent settings. A visible form writes values into YaCy configuration, while the backend may reload subsystems such as language files, network listeners, cache handling, or search presentation.

## Correct Use

Read the current value before changing it. Configuration changes often persist beyond the current request and may affect later crawling, search, network contact, authentication, or resource use. Change one operational idea at a time and verify the result.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/SettingsAck_p.html`.

Backend checks: transaction token for protected POST.

## Automation And API

Page backend: `source/net/yacy/htroot/SettingsAck_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/SettingsAck_p.html` | `GET or POST` | admin | `source/net/yacy/htroot/SettingsAck_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `adminaccount` | User or account value. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `adminuser` | User or account value. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `crawler.clientTimeout` | Date/time value for filtering, display, or scheduling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `fileHost` | Host or domain scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `msgForwardingEnabled` | Enables the named feature. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `port` | HTTP port where YaCy listens. Values below 1024 are ignored by this form; changing the port triggers reconnect/redirect behavior. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `publicPort` | Optional public non-TLS HTTP port, from 1 through 65535, advertised in YaCy's seed/P2P metadata. It takes precedence over UPnP and the local listening port. An empty value disables the manual override. | Processed with the `serveraccount` settings action. Invalid, non-numeric, zero, or out-of-range values leave the previous setting unchanged. Set it when NAT or a reverse proxy exposes YaCy on a different external port. |
| `proxyaccess` | Saves the proxy client IP-number filter. | Changes proxy access scope; verify the filter before submitting. |
| `proxyfilter` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `remoteProxyHost` | Host or domain scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `remoteProxyUser` | User or account value. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `serveraccount` | Action marker whose presence applies the Server Access form, including `fileHost`, `staticIP`, `publicPort`, and `serverfilter`. Its submitted value is not interpreted. | Requires administrator access and a valid transaction token. Include the complete Server Access form state because the action processes its related fields together. |
| `serverfilter` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `serveruser` | User or account value. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `urlproxydomains` | Host or domain scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `urlproxyenabled` | Enables the named feature. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `urlproxyfilter` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |

Example request shape:

```http
GET or POST /SettingsAck_p.html?port=...&adminaccount=...&adminpw1=...&adminpw2=...&adminuser=...
```

For a server-access form submission, `serveraccount` selects the action and
`publicPort` carries the optional override. A valid value is stored and applied
to the current peer seed. Clearing the value removes the override; subsequent
seed refreshes then use the UPnP mapping when available, otherwise the local
HTTP listening port. The setting does not override the public TLS port.

## What To Expect

A successful change is visible as a saved value, a confirmation, or changed behavior on a related page. Some settings take effect immediately; others require reconnecting, reloading translations, restarting services, or watching the status page.

## Related Pages

- `Status.html`
- `ConfigBasic.html`
