---
page: htroot/ConfigParser_p.html
help: help/ConfigParser_p.md
title: Advanced Settings
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ConfigParser_p.java
---

# Advanced Settings

## Purpose

Parser settings decide which document formats YaCy can understand.

Use it when crawled files arrive but are not converted into searchable text as expected.

## What You Can Do Here

- Parser settings decide which document formats YaCy can understand.
- Read the current value before changing it.
- Verify the effect on the public page, status page, or related administration page.

## Page Architecture

Configuration pages usually contain persistent settings. A visible form writes values into YaCy configuration, while the backend may reload subsystems such as language files, network listeners, cache handling, or search presentation.

| Control | Meaning | Values or examples |
| --- | --- | --- |

## Correct Use

Read the current value before changing it. Configuration changes often persist beyond the current request and may affect later crawling, search, network contact, authentication, or resource use. Change one operational idea at a time and verify the result.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ConfigParser_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/ConfigParser_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ConfigParser_p.html` | `POST` | admin | `source/net/yacy/htroot/ConfigParser_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |

Example request shape:

```http
POST /ConfigParser_p.html
Content-Type: application/x-www-form-urlencoded

allswitch=...&extension_#[extension]#=...&mimename_#[mimetype]#=...&parserSettings=...&extension_=...
```

## What To Expect

A successful change is visible as a saved value, a confirmation, or changed behavior on a related page. Some settings take effect immediately; others require reconnecting, reloading translations, restarting services, or watching the status page.

## Related Pages

- `ViewFile.html`
