---
page: htroot/ConfigRobotsTxt_p.html
help: help/ConfigRobotsTxt_p.md
title: Local robots.txt
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ConfigRobotsTxt_p.java
---

# Local robots.txt

## Purpose

Local robots.txt publishes crawl instructions for YaCy's own embedded web server.

Use it to tell external crawlers which YaCy-local paths should or should not be fetched.

## What You Can Do Here

- Local robots.txt publishes crawl instructions for YaCy's own embedded web server.
- Read the current value before changing it.
- Verify the effect on the public page, status page, or related administration page.

## Page Architecture

Configuration pages usually contain persistent settings. A visible form writes values into YaCy configuration, while the backend may reload subsystems such as language files, network listeners, cache handling, or search presentation.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `all` | Entire Peer. | Entire Peer |
| `status` | Status page. | Status page |
| `network` | Network pages. | Network pages |
| `surftips` | Surftips. | Surftips |
| `news` | News pages. | News pages |
| `bookmarks` | Public bookmarks. | Public bookmarks |
| `homepage` | Home Page. | Home Page |
| `fileshare` | File Share. | File Share |
| `profile` | Impressum. | Impressum |
| `save` | Saves settings. | `Save restrictions` |

## Correct Use

Read the current value before changing it. Configuration changes often persist beyond the current request and may affect later crawling, search, network contact, authentication, or resource use. Change one operational idea at a time and verify the result.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ConfigRobotsTxt_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/ConfigRobotsTxt_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ConfigRobotsTxt_p.html` | `POST` | admin | `source/net/yacy/htroot/ConfigRobotsTxt_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `all` | Entire Peer. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `status` | Status page. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `network` | Network pages. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `surftips` | Surftips. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `news` | News pages. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `bookmarks` | Public bookmarks. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `homepage` | Home Page. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `fileshare` | File Share. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `profile` | Impressum. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `save` | Saves settings. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
POST /ConfigRobotsTxt_p.html
Content-Type: application/x-www-form-urlencoded

all=...&status=...&network=...&surftips=...&news=...
```

## What To Expect

A successful change is visible as a saved value, a confirmation, or changed behavior on a related page. Some settings take effect immediately; others require reconnecting, reloading translations, restarting services, or watching the status page.

## Related Pages

- Related configuration work is usually reached from `ConfigBasic.html`, `Settings_p.html`, or the adjacent configuration page in the administration menu.
