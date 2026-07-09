---
page: htroot/IndexPackManager_p.html
help: help/IndexPackManager_p.md
title: Index Pack Manager
package: import-export-federation
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexPackManager_p.java
---

# Index Pack Manager

## Purpose

Index Pack Manager supervises installed or available index packages.

Use it to see which packages are present and how they affect local search.

## What You Can Do Here

- Index Pack Manager supervises installed or available index packages.
- Define the source, target, format, collection, and expected size before starting.
- Monitor progress because large transfers continue beyond the initial request.

## Page Architecture

Import and export pages translate external data formats into YaCy documents or move YaCy index data into another store. Most long-running actions create background work that should be monitored afterward.

## Correct Use

Use the manager to inspect packages before installing or removing them. Package changes can alter what local searches find without running a new crawl.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexPackManager_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexPackManager_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexPackManager_p.html` | `GET or POST` | admin | `source/net/yacy/htroot/IndexPackManager_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `delete` | Deletes the selected URL, path, or index scope. Confirm the scope first. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `file` | Selected or uploaded file. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET or POST /IndexPackManager_p.html?delete=...&file=...&mvfrom=...&mvto=...
```

## What To Expect

The page should show package state, installed packages, or available package actions. Search results may change after package installation or removal.

## Related Pages

- Related transfer work is usually reached through the import/export page for the same format, the index-pack pages, or the queue/status page that reports progress.
