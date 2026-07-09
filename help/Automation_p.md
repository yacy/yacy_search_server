---
page: htroot/Automation_p.html
help: help/Automation_p.md
title: Automation
package: core-search-public
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/Automation_p.java
---

# Automation

## Purpose

Automation lists API calls YaCy recorded while administrators used the web interface. It is the bridge between “I clicked this once” and “I want to repeat this operation deliberately.”

Use it to inspect, schedule, execute, or delete recorded actions. It is powerful because the stored rows may represent crawls, imports, deletions, configuration changes, or other servlet calls.

## What You Can Do Here

- Review API calls recorded from previous YaCy actions.
- Use recorded calls as examples for repeatable automation, not as commands to replay blindly.
- Check endpoint, method, access requirement, and destructive parameters before reusing a call.

## Page Architecture

Automation is built around YaCy's recorded API-call table. Each row represents an action that was captured from a servlet request. The page lets an administrator select rows, execute them, delete them, and edit scheduling fields such as next execution date, event trigger, repetition interval, and run frequency.

## Correct Use

Treat recorded actions as powerful examples, not as harmless history. Read the endpoint and parameters before executing a row. Pay special attention to actions that delete data, start crawls, change settings, or contact other peers. For scheduled execution, check both the next execution date and the repeat/event fields so the action does not run more often than intended.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/Automation_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/Automation_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Automation_p.html` | `POST` | admin | `source/net/yacy/htroot/Automation_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `submitNextExecDates` | Result of API execution. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `item_#[count]#` | Choice value. Options: `mark_`. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `date_next_exec_#[pk]#` | Date/time value for filtering, display, or scheduling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `event_select_#[pk]#` | Choice value. Options: `off` = no event, `on` = activate event. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `event_kind_#[pk]#` | Choice value. Options: `off`, `once` = run once, `regular` = run regular. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `event_action_#[pk]#` | Choice value. Options: `startup` = after start-up, `0000` = at 00:00h, `0100` = at 01:00h, `0200` = at 02:00h, `0300` = at 03:00h, `0400` = at 04:00h, `0500` = at 05:00h, `0600` = at 06:00h, `0700` = at 07:00h, `0800` = at 08:00h, `0900` = at 09:00h, `1000` = at 10:00h, `1100` = at 11:00h, `1200` = at 12:00h, `1300` = at 13:00h, `1400` = at 14:00h, `1500` = at 15:00h, `1600` = at 16:00h, `1700` = at 17:00h, `1800` = at 18:00h. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `repeat_select_#[pk]#` | Choice value. Options: `off` = no repetition, `on` = activate scheduler. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `repeat_time_#[pk]#` | Date/time value for filtering, display, or scheduling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `repeat_unit_#[pk]#` | Choice value. Options: `selminutes` = minutes, `selhours` = hours, `seldays` = days. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `execrows` | Result of API execution. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `deleterows` | Result of API execution. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteold` | Policy for deleting or replacing older index entries during crawl setup. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteoldtime` | Result of API execution. Options: `1` = 1 day, `2` = 2 days, `3` = 3 days, `4` = 4 days, `5` = 5 days, `6` = 6 days, `7` = 1 week, `14` = 2 weeks, `21` = 3 weeks, `30` = 1 month, `60` = 2 months, `90` = 3 months, `180` = 6 months, `270` = 9 months, `365` = 1 year, `730` = 2 years. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `current_pk` | Result of API execution. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `filter` | Filter text or expression used to narrow the displayed records. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `inline` | Result of API execution. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `maximumRecords` | Maximum number of results to return on one page. Use a modest value for interactive use; larger values are for controlled scripts. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `repeat_time_` | Date/time value for filtering, display, or scheduling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `scheduleeventaction` | Result of API execution. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `sort` | Result of API execution. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `startRecord` | First result record for pagination; accepted as an alternative to `offset` on search endpoints. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /Automation_p.html
Content-Type: application/x-www-form-urlencoded

query=...&maximumRecords=...&allswitch=...&submitNextExecDates=...&item_#[count]#=...
```

## What To Expect

Successful changes update the automation table: selected rows may run immediately, disappear after deletion, or receive new scheduling metadata. If an executed action starts a crawl, import, export, or deletion, follow the related monitor page to see the real operational result.

## Related Pages

- Related search work usually continues on `yacysearch.html`, `index.html`, `ViewFile.html`, quick crawl, or the search integration pages.
