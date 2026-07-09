---
page: htroot/BlacklistCleaner_p.html
help: help/BlacklistCleaner_p.md
title: Blacklist Cleaner
package: blacklist-security-access
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/BlacklistCleaner_p.java
---

# Blacklist Cleaner

## Purpose

Blacklist Cleaner finds invalid or dangerous blacklist entries.

Use it after importing or editing lists so malformed patterns do not silently break filtering.

## What You Can Do Here

- Blacklist Cleaner finds invalid or dangerous blacklist entries.
- Test a concrete URL, request, user, or pattern before applying broad policy.
- Keep rules narrow enough that they block the intended problem without hiding useful content.

## Page Architecture

Security and blacklist pages turn names, patterns, credentials, or request properties into allow/block decisions. The architecture is rule-oriented: define the rule, test the rule, then apply it to crawling, search, or access.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `listNames` | Two wildcards in host-part :: ::Either subdomain or wildcard ::Path is invalid Regex ::Wildcard not on begin or end ::Host contains illegal chars ::Double ::Host is invalid Regex. | Text value; use the page label and surrounding context to choose the exact content. |
| `allowRegex` | Two wildcards in host-part :: ::Either subdomain or wildcard ::Path is invalid Regex ::Wildcard not on begin or end ::Host contains illegal chars ::Double ::Host is invalid Regex. | Two wildcards in host-part :: ::Either subdomain or wildcard ::Path is invalid Regex ::Wildcard not on begin or end ::Host contains illegal chars ::Double ::Host is invalid Regex |
| `list` | Two wildcards in host-part :: ::Either subdomain or wildcard ::Path is invalid Regex ::Wildcard not on begin or end ::Host contains illegal chars ::Double ::Host is invalid Regex. | `Check` |
| `select#[entry]#` | Two wildcards in host-part :: ::Either subdomain or wildcard ::Path is invalid Regex ::Wildcard not on begin or end ::Host contains illegal chars ::Double ::Host is invalid Regex. | Two wildcards in host-part :: ::Either subdomain or wildcard ::Path is invalid Regex ::Wildcard not on begin or end ::Host contains illegal chars ::Double ::Host is invalid Regex |
| `entry#[entry]#` | Two wildcards in host-part :: ::Either subdomain or wildcard ::Path is invalid Regex ::Wildcard not on begin or end ::Host contains illegal chars ::Double ::Host is invalid Regex. | Text value; use the page label and surrounding context to choose the exact content. |
| `alter` | Two wildcards in host-part :: ::Either subdomain or wildcard ::Path is invalid Regex ::Wildcard not on begin or end ::Host contains illegal chars ::Double ::Host is invalid Regex. | `Change Selected` |
| `delete` | Deletes the selected object or scope. Use only with explicit confirmation. | `Delete Selected` |

## Correct Use

Test with a concrete example. A blacklist, regular expression, rate limit, cookie rule, or access rule is only understandable when checked against a real URL or request. Prefer narrow patterns and document the reason for broad rules.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/BlacklistCleaner_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/BlacklistCleaner_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/BlacklistCleaner_p.html` | `POST` | admin | `source/net/yacy/htroot/BlacklistCleaner_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `listNames` | Two wildcards in host-part :: ::Either subdomain or wildcard ::Path is invalid Regex ::Wildcard not on begin or end ::Host contains illegal chars ::Double ::Host is invalid Regex. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `allowRegex` | Two wildcards in host-part :: ::Either subdomain or wildcard ::Path is invalid Regex ::Wildcard not on begin or end ::Host contains illegal chars ::Double ::Host is invalid Regex. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `list` | Requests a list view for the selected host or path scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `select#[entry]#` | Two wildcards in host-part :: ::Either subdomain or wildcard ::Path is invalid Regex ::Wildcard not on begin or end ::Host contains illegal chars ::Double ::Host is invalid Regex. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `entry#[entry]#` | Two wildcards in host-part :: ::Either subdomain or wildcard ::Path is invalid Regex ::Wildcard not on begin or end ::Host contains illegal chars ::Double ::Host is invalid Regex. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `alter` | Two wildcards in host-part :: ::Either subdomain or wildcard ::Path is invalid Regex ::Wildcard not on begin or end ::Host contains illegal chars ::Double ::Host is invalid Regex. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `delete` | Deletes the selected URL, path, or index scope. Confirm the scope first. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |

Example request shape:

```http
POST /BlacklistCleaner_p.html
Content-Type: application/x-www-form-urlencoded

listNames=...&allowRegex=...&list=...&select#[entry]#=...&entry#[entry]#=...
```

## What To Expect

Expect a rule list, match test, access record, cookie view, or confirmation. The real proof is behavioral: the same URL, request, or user should now be accepted, blocked, limited, or displayed as intended.

## Related Pages

- Related security work is usually reached through blacklist administration, blacklist testing, cookie monitors, robots data, or access-rate settings.
