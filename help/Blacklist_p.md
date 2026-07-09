---
page: htroot/Blacklist_p.html
help: help/Blacklist_p.md
title: Blacklist Administration
package: blacklist-security-access
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/Blacklist_p.java
---

# Blacklist Administration

## Purpose

Blacklist Administration edits the URL and host patterns YaCy blocks.

Use it to prevent crawling, indexing, or displaying unwanted hosts and paths.

## What You Can Do Here

- Blacklist Administration edits the URL and host patterns YaCy blocks.
- Test a concrete URL, request, user, or pattern before applying broad policy.
- Keep rules narrow enough that they block the intended problem without hiding useful content.

## Page Architecture

Security and blacklist pages turn names, patterns, credentials, or request properties into allow/block decisions. The architecture is rule-oriented: define the rule, test the rule, then apply it to crawling, search, or access.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `selectedListName` | Choice value. Options: [not shared::shared]. | [not shared::shared] |
| `action` | Choice value. Options: `editBlacklistEntry` = Edit selected pattern(s), `deleteBlacklistEntry` = Delete selected pattern(s), `moveBlacklistEntry` = Move selected pattern(s) to. | `editBlacklistEntry` = Edit selected pattern(s), `deleteBlacklistEntry` = Delete selected pattern(s), `moveBlacklistEntry` = Move selected pattern(s) to |
| `offset` | Zero-based result offset for pagination. | Text value; use the page label and surrounding context to choose the exact content. |
| `deleteList` | Deletion or termination action. Use only with explicit intent. | `Delete this list` |
| `activateList4#[blTypeName]#` | Choice value. Options: `on`. | `on` |

## Correct Use

Test with a concrete example. A blacklist, regular expression, rate limit, cookie rule, or access rule is only understandable when checked against a real URL or request. Prefer narrow patterns and document the reason for broad rules.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/Blacklist_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/Blacklist_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Blacklist_p.html` | `POST` | admin | `source/net/yacy/htroot/Blacklist_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `selectedListName` | Choice value. Options: [not shared::shared]. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `action` | Choice value. Options: `editBlacklistEntry` = Edit selected pattern(s), `deleteBlacklistEntry` = Delete selected pattern(s), `moveBlacklistEntry` = Move selected pattern(s) to. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `offset` | Zero-based result offset for pagination. | Read-only pagination control; use it to request later result pages. |
| `deleteList` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `activateList4#[blTypeName]#` | Choice value. Options: `on`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /Blacklist_p.html
Content-Type: application/x-www-form-urlencoded

selectedListName=...&newListName=...&createNewList=...&newEntry=...&addBlacklistEntry=...
```

## What To Expect

Expect a rule list, match test, access record, cookie view, or confirmation. The real proof is behavioral: the same URL, request, or user should now be accepted, blocked, limited, or displayed as intended.

## Related Pages

- `https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html`
