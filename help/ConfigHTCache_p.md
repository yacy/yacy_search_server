---
page: htroot/ConfigHTCache_p.html
help: help/ConfigHTCache_p.md
title: Hypertext Cache Configuration
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ConfigHTCache_p.java
---

# Hypertext Cache Configuration

## Purpose

Hypertext Cache Configuration controls whether fetched documents are stored for later reuse.

Use it to balance speed, disk use, freshness, and privacy.

## What You Can Do Here

- Hypertext Cache Configuration controls whether fetched documents are stored for later reuse.
- Read the current value before changing it.
- Verify the effect on the public page, status page, or related administration page.

## Page Architecture

Configuration pages usually contain persistent settings. A visible form writes values into YaCy configuration, while the backend may reload subsystems such as language files, network listeners, cache handling, or search presentation.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `HTCachePath` | The path where the cache is stored. | Text value; use the page label and surrounding context to choose the exact content. |
| `maxCacheSize` | The maximum size of the cache. | Integer value. |
| `compressionLevel` | Compression level. | Text value; use the page label and surrounding context to choose the exact content. |
| `lockTimeout` | Concurrent access timeout. | Text value; use the page label and surrounding context to choose the exact content. |
| `set` | Submits and applies the basic configuration. | `Set` |
| `deleteCache` | The path where the cache is stored. | The path where the cache is stored |
| `deleteRobots` | The path where the cache is stored. | The path where the cache is stored |
| `deletecomplete` | The path where the cache is stored. | `Delete` |

## Correct Use

Read the current value before changing it. Configuration changes often persist beyond the current request and may affect later crawling, search, network contact, authentication, or resource use. Change one operational idea at a time and verify the result.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ConfigHTCache_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/ConfigHTCache_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ConfigHTCache_p.html` | `POST` | admin | `source/net/yacy/htroot/ConfigHTCache_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `HTCachePath` | The path where the cache is stored. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `maxCacheSize` | The maximum size of the cache. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `compressionLevel` | Compression level. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `lockTimeout` | Concurrent access timeout. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `set` | Submit action that saves the page settings. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `deleteCache` | The path where the cache is stored. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deleteRobots` | The path where the cache is stored. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `deletecomplete` | The path where the cache is stored. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |

Example request shape:

```http
POST /ConfigHTCache_p.html
Content-Type: application/x-www-form-urlencoded

HTCachePath=...&maxCacheSize=...&compressionLevel=...&lockTimeout=...&set=...
```

## What To Expect

A successful change is visible as a saved value, a confirmation, or changed behavior on a related page. Some settings take effect immediately; others require reconnecting, reloading translations, restarting services, or watching the status page.

## Related Pages

- Related configuration work is usually reached from `ConfigBasic.html`, `Settings_p.html`, or the adjacent configuration page in the administration menu.
