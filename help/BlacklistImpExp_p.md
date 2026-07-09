---
page: htroot/BlacklistImpExp_p.html
help: help/BlacklistImpExp_p.md
title: Blacklist Import
package: blacklist-security-access
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/BlacklistImpExp_p.java
---

# Blacklist Import

## Purpose

Blacklist Import/Export moves blacklist rules between files, peers, or installations.

Use it when maintaining shared policy rather than editing one rule at a time.

## What You Can Do Here

- Blacklist Import/Export moves blacklist rules between files, peers, or installations.
- Test a concrete URL, request, user, or pattern before applying broad policy.
- Keep rules narrow enough that they block the intended problem without hiding useful content.

## Page Architecture

Security and blacklist pages turn names, patterns, credentials, or request properties into allow/block decisions. The architecture is rule-oriented: define the rule, test the rule, then apply it to crawling, search, or access.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `hash` | YaCy hash identifier for a peer, URL, row, or stored object. Use exact values copied from YaCy output. | Exact YaCy hash value. |
| `url` | URL to inspect, crawl, import, or act on. | URL or URL-derived value; use the exact format shown by the page. |
| `file` | Selected or uploaded file. | Text value; use the page label and surrounding context to choose the exact content. |
| `listname` | Choice value. Options: all. | all |

## Correct Use

Test with a concrete example. A blacklist, regular expression, rate limit, cookie rule, or access rule is only understandable when checked against a real URL or request. Prefer narrow patterns and document the reason for broad rules.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/sharedBlacklist_p.html`, `/api/blacklists_p.xml`, `/api/blacklists_p.txt`.

## Automation And API

Page backend: `source/net/yacy/htroot/BlacklistImpExp_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/sharedBlacklist_p.html` | `GET` | admin | `source/net/yacy/htroot/sharedBlacklist_p.java` |
| `/api/blacklists_p.xml` | `GET` | admin | `source/net/yacy/htroot/api/blacklists_p.java` |
| `/api/blacklists_p.txt` | `GET` | admin | `source/net/yacy/htroot/api/blacklists_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `hash` | YaCy hash identifier for a peer, URL, row, or stored object. Use exact values copied from YaCy output. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `url` | URL to inspect, crawl, import, or act on. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `file` | Selected or uploaded file. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `listname` | Choice value. Options: all. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `agentName` | Crawler user-agent profile used for outgoing HTTP requests. Choose a profile that matches the desired identity and politeness behavior. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /sharedBlacklist_p.html?url=...&hash=...&file=...&listname=...&add=...
```

## What To Expect

Expect a rule list, match test, access record, cookie view, or confirmation. The real proof is behavioral: the same URL, request, or user should now be accepted, blocked, limited, or displayed as intended.

## Related Pages

- Related security work is usually reached through blacklist administration, blacklist testing, cookie monitors, robots data, or access-rate settings.
