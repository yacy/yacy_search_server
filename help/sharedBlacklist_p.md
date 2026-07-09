---
page: htroot/sharedBlacklist_p.html
help: help/sharedBlacklist_p.md
title: Shared Blacklist
package: blacklist-security-access
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/sharedBlacklist_p.java
---

# Shared Blacklist

## Purpose

Shared Blacklist exposes blacklist data for sharing between peers.

Use it when another peer or tool needs the current block policy in a machine-readable form.

## What You Can Do Here

- Shared Blacklist exposes blacklist data for sharing between peers.
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

Protected related endpoint(s): `/sharedBlacklist_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/sharedBlacklist_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/sharedBlacklist_p.html` | `POST` | admin | `source/net/yacy/htroot/sharedBlacklist_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `agentName` | Crawler user-agent profile used for outgoing HTTP requests. Choose a profile that matches the desired identity and politeness behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `file` | Selected or uploaded file. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `hash` | YaCy hash identifier for a peer, URL, row, or stored object. Use exact values copied from YaCy output. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /sharedBlacklist_p.html
Content-Type: application/x-www-form-urlencoded

url=...&currentBlacklist=...&item#[count]#=...&add=...&agentName=...
```

## What To Expect

Expect a rule list, match test, access record, cookie view, or confirmation. The real proof is behavioral: the same URL, request, or user should now be accepted, blocked, limited, or displayed as intended.

## Related Pages

- Related security work is usually reached through blacklist administration, blacklist testing, cookie monitors, robots data, or access-rate settings.
