---
page: htroot/Table_RobotsTxt_p.html
help: help/Table_RobotsTxt_p.md
title: Table Viewer
package: blacklist-security-access
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/Table_RobotsTxt_p.java
---

# Table Viewer

## Purpose

Robots table view inspects stored robots.txt information.

Use it to understand crawl permissions YaCy learned for hosts.

## What You Can Do Here

- Robots table view inspects stored robots.txt information.
- Test a concrete URL, request, user, or pattern before applying broad policy.
- Keep rules narrow enough that they block the intended problem without hiding useful content.

## Page Architecture

Security and blacklist pages turn names, patterns, credentials, or request properties into allow/block decisions. The architecture is rule-oriented: define the rule, test the rule, then apply it to crawling, search, or access.

## Correct Use

Test with a concrete example. A blacklist, regular expression, rate limit, cookie rule, or access rule is only understandable when checked against a real URL or request. Prefer narrow patterns and document the reason for broad rules.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

## Automation And API

Page backend: `source/net/yacy/htroot/Table_RobotsTxt_p.java`.

No request parameters are needed for normal use of this page.

## What To Expect

Expect a rule list, match test, access record, cookie view, or confirmation. The real proof is behavioral: the same URL, request, or user should now be accepted, blocked, limited, or displayed as intended.

## Related Pages

- Related security work is usually reached through blacklist administration, blacklist testing, cookie monitors, robots data, or access-rate settings.
