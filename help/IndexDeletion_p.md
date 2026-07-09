---
page: htroot/IndexDeletion_p.html
help: help/IndexDeletion_p.md
title: Index Deletion
package: index-management
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexDeletion_p.java
---

# Index Deletion

## Purpose

Index Deletion removes selected documents from the index.

Use it only with a clear query, host, collection, or URL scope.

## What You Can Do Here

- Index Deletion removes selected documents from the index.
- Select the narrowest URL, host, field, query, queue, or collection scope.
- Treat deletion, rebuilding, and reloading as maintenance operations with visible search impact.

## Page Architecture

Index pages expose stored documents through URL, host, path, Solr field, or queue views. Read-only inspection and destructive maintenance often share the same page, so the target scope matters more than the button label.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `urldelete` | Deletion or termination action. Use only with explicit intent. | URL or URL-derived value; use the exact format shown by the page. |
| `urldelete-mm` | Choice value. Options: `subpath`, `regexp`. | `subpath`, `regexp` |
| `simulate-urldelete` | Deletion or termination action. Use only with explicit intent. | `Simulate Deletion` |
| `engage-urldelete` | Deletion or termination action. Use only with explicit intent. | `Engage Deletion` |
| `timedelete-number` | Choice value. Options: `1`, `2`, `3`, `4`, `5`, `6`, `7`, `8`, `9`, `10`, `12`, `14`, `21`, `24`, `28`, `30`, `60`, `90`. | `1`, `2`, `3`, `4`, `5`, `6`, `7`, `8`, `9`, `10`, `12`, `14`, `21`, `24`, `28`, `30` |
| `timedelete-unit` | Choice value. Options: `year` = years, `month` = months, `day` = days, `hour` = hours. | `year` = years, `month` = months, `day` = days, `hour` = hours |
| `timedelete-source` | Choice value. Options: `loaddate`, `lastmodified`. | `loaddate`, `lastmodified` |
| `simulate-timedelete` | Deletion or termination action. Use only with explicit intent. | `Simulate Deletion` |
| `engage-timedelete` | Deletion or termination action. Use only with explicit intent. | `Engage Deletion` |
| `collectiondelete-mode` | Choice value. Options: `unassigned`, `assigned`. | `unassigned`, `assigned` |
| `collectiondelete` | Deletion or termination action. Use only with explicit intent. | Text value; use the page label and surrounding context to choose the exact content. |
| `simulate-collectiondelete` | Deletion or termination action. Use only with explicit intent. | `Simulate Deletion` |
| `engage-collectiondelete` | Deletion or termination action. Use only with explicit intent. | `Engage Deletion` |
| `querydelete` | Deletion or termination action. Use only with explicit intent. | Text value; use the page label and surrounding context to choose the exact content. |
| `simulate-querydelete` | Deletion or termination action. Use only with explicit intent. | `Simulate Deletion` |
| `engage-querydelete` | Deletion or termination action. Use only with explicit intent. | `Engage Deletion` |

## Correct Use

Use inspection before maintenance. First identify the exact URL, host, field, queue, collection, or query scope, then choose the action. Deletion and re-indexing can be expensive or irreversible from the user's point of view, so never broaden the scope just to make a command easier.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexDeletion_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexDeletion_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexDeletion_p.html` | `POST` | admin | `source/net/yacy/htroot/IndexDeletion_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `urldelete` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `urldelete-mm` | Choice value. Options: `subpath`, `regexp`. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `simulate-urldelete` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `engage-urldelete` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `timedelete-number` | Choice value. Options: `1`, `2`, `3`, `4`, `5`, `6`, `7`, `8`, `9`, `10`, `12`, `14`, `21`, `24`, `28`, `30`, `60`, `90`. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `timedelete-unit` | Choice value. Options: `year` = years, `month` = months, `day` = days, `hour` = hours. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `timedelete-source` | Choice value. Options: `loaddate`, `lastmodified`. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `simulate-timedelete` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `engage-timedelete` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `collectiondelete-mode` | Choice value. Options: `unassigned`, `assigned`. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `collectiondelete` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `simulate-collectiondelete` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `engage-collectiondelete` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `querydelete` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `simulate-querydelete` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `engage-querydelete` | Deletion or termination action. Use only with explicit intent. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `count` | SRU-style result count. It is an alternative to `maximumRecords` on search endpoints. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |

Example request shape:

```http
POST /IndexDeletion_p.html
Content-Type: application/x-www-form-urlencoded

urldelete=...&urldelete-mm=...&simulate-urldelete=...&engage-urldelete=...&timedelete-number=...
```

## What To Expect

The response should make the selected index scope clearer: records listed, queues changed, errors shown, fields rebuilt, or deletion confirmed. Verify user-visible impact with search after maintenance actions.

## Related Pages

- `IndexControlURLs_p.html`
