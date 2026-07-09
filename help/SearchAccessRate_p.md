---
page: htroot/SearchAccessRate_p.html
help: help/SearchAccessRate_p.md
title: Local Search access rate limitations
package: blacklist-security-access
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/SearchAccessRate_p.java
---

# Local Search access rate limitations

## Purpose

Local Search access rate limitations protect the search interface from excessive requests.

Use it to keep a public or shared peer responsive under heavy or automated search traffic.

## What You Can Do Here

- Local Search access rate limitations protect the search interface from excessive requests.
- Test a concrete URL, request, user, or pattern before applying broad policy.
- Keep rules narrow enough that they block the intended problem without hiding useful content.

## Page Architecture

Security and blacklist pages turn names, patterns, credentials, or request properties into allow/block decisions. The architecture is rule-oriented: define the rule, test the rule, then apply it to crawling, search, or access.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `search.public.max.access.3s` | Max searches in 3s. | Integer value. |
| `search.public.max.access.1mn` | Max searches in 1mn. | Integer value. |
| `search.public.max.access.10mn` | Max searches in 10mn. | Integer value. |
| `search.public.max.p2p.access.3s` | Max searches in 3s. | Integer value. |
| `search.public.max.p2p.access.1mn` | Max searches in 1mn. | Integer value. |
| `search.public.max.p2p.access.10mn` | Max searches in 10mn. | Integer value. |
| `search.public.max.p2p.jsresort.access.3s` | Max searches in 3s. | Integer value. |
| `search.public.max.p2p.jsresort.access.1mn` | Max searches in 1mn. | Integer value. |
| `search.public.max.p2p.jsresort.access.10mn` | Max searches in 10mn. | Integer value. |
| `search.public.max.remoteSnippet.access.3s` | Max searches in 3s. | Integer value. |
| `search.public.max.remoteSnippet.access.1mn` | Max searches in 1mn. | Integer value. |
| `search.public.max.remoteSnippet.access.10mn` | Max searches in 10mn. | Integer value. |
| `set` | Submits and applies the basic configuration. | `Submit` |
| `setDefaults` | Max searches in 3s. | `Set defaults` |

## Correct Use

Test with a concrete example. A blacklist, regular expression, rate limit, cookie rule, or access rule is only understandable when checked against a real URL or request. Prefer narrow patterns and document the reason for broad rules.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/SearchAccessRate_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/SearchAccessRate_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/SearchAccessRate_p.html` | `POST` | admin | `source/net/yacy/htroot/SearchAccessRate_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `search.public.max.access.3s` | Max searches in 3s. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.public.max.access.1mn` | Max searches in 1mn. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.public.max.access.10mn` | Max searches in 10mn. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.public.max.p2p.access.3s` | Max searches in 3s. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.public.max.p2p.access.1mn` | Max searches in 1mn. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.public.max.p2p.access.10mn` | Max searches in 10mn. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.public.max.p2p.jsresort.access.3s` | Max searches in 3s. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `search.public.max.p2p.jsresort.access.1mn` | Max searches in 1mn. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `search.public.max.p2p.jsresort.access.10mn` | Max searches in 10mn. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `search.public.max.remoteSnippet.access.3s` | Max searches in 3s. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.public.max.remoteSnippet.access.1mn` | Max searches in 1mn. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.public.max.remoteSnippet.access.10mn` | Max searches in 10mn. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `set` | Submit action that saves the page settings. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `setDefaults` | Max searches in 3s. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
POST /SearchAccessRate_p.html
Content-Type: application/x-www-form-urlencoded

search.public.max.access.3s=...&search.public.max.access.1mn=...&search.public.max.access.10mn=...&search.public.max.p2p.access.3s=...&search.public.max.p2p.access.1mn=...
```

## What To Expect

Expect a rule list, match test, access record, cookie view, or confirmation. The real proof is behavioral: the same URL, request, or user should now be accepted, blocked, limited, or displayed as intended.

## Related Pages

- `ConfigAccounts_p.html`
- `ConfigPortal_p.html`
