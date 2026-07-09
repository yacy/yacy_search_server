---
page: htroot/CookieMonitorOutgoing_p.html
help: help/CookieMonitorOutgoing_p.md
title: Outgoing Cookies Monitor
package: blacklist-security-access
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/CookieMonitorOutgoing_p.java
---

# Outgoing Cookies Monitor

## Purpose

Outgoing Cookies Monitor shows cookies YaCy sends back.

Use it to inspect session, authentication, and browser-facing state.

## What You Can Do Here

- Outgoing Cookies Monitor shows cookies YaCy sends back.
- Test a concrete URL, request, user, or pattern before applying broad policy.
- Keep rules narrow enough that they block the intended problem without hiding useful content.

## Page Architecture

Security and blacklist pages turn names, patterns, credentials, or request properties into allow/block decisions. The architecture is rule-oriented: define the rule, test the rule, then apply it to crawling, search, or access.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `enableCookieMonitoring` | Enables the named feature. | `Enable Cookie Monitoring` |
| `disableCookieMonitoring` | Disables the named feature. | `Disable Cookie Monitoring` |

## Correct Use

Test with a concrete example. A blacklist, regular expression, rate limit, cookie rule, or access rule is only understandable when checked against a real URL or request. Prefer narrow patterns and document the reason for broad rules.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/CookieMonitorOutgoing_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/CookieMonitorOutgoing_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/CookieMonitorOutgoing_p.html` | `POST` | admin | `source/net/yacy/htroot/CookieMonitorOutgoing_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `enableCookieMonitoring` | Enables the named feature. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `disableCookieMonitoring` | Disables the named feature. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
POST /CookieMonitorOutgoing_p.html
Content-Type: application/x-www-form-urlencoded

enableCookieMonitoring=...&disableCookieMonitoring=...
```

## What To Expect

Expect a rule list, match test, access record, cookie view, or confirmation. The real proof is behavioral: the same URL, request, or user should now be accepted, blocked, limited, or displayed as intended.

## Related Pages

- Related security work is usually reached through blacklist administration, blacklist testing, cookie monitors, robots data, or access-rate settings.
