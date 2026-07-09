---
page: htroot/BlacklistTest_p.html
help: help/BlacklistTest_p.md
title: Blacklist Test
package: blacklist-security-access
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/BlacklistTest_p.java
---

# Blacklist Test

## Purpose

Blacklist Test checks whether a URL matches the active blacklist rules.

Use it before blaming the crawler or search page for a missing result.

## What You Can Do Here

- Blacklist Test checks whether a URL matches the active blacklist rules.
- Test a concrete URL, request, user, or pattern before applying broad policy.
- Keep rules narrow enough that they block the intended problem without hiding useful content.

## Page Architecture

Security and blacklist pages turn names, patterns, credentials, or request properties into allow/block decisions. The architecture is rule-oriented: define the rule, test the rule, then apply it to crawling, search, or access.

| Control | Meaning | Values or examples |
| --- | --- | --- |

## Correct Use

Test with a concrete example. A blacklist, regular expression, rate limit, cookie rule, or access rule is only understandable when checked against a real URL or request. Prefer narrow patterns and document the reason for broad rules.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/BlacklistTest_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/BlacklistTest_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/BlacklistTest_p.html` | `POST` | admin | `source/net/yacy/htroot/BlacklistTest_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |

Example request shape:

```http
POST /BlacklistTest_p.html
Content-Type: application/x-www-form-urlencoded

testurl=...&testList=...
```

## What To Expect

Expect a rule list, match test, access record, cookie view, or confirmation. The real proof is behavioral: the same URL, request, or user should now be accepted, blocked, limited, or displayed as intended.

## Related Pages

- Related security work is usually reached through blacklist administration, blacklist testing, cookie monitors, robots data, or access-rate settings.
