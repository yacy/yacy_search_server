---
page: htroot/ProxyIndexingMonitor_p.html
help: help/ProxyIndexingMonitor_p.md
title: Indexing with Proxy
package: monitoring-performance
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ProxyIndexingMonitor_p.java
---

# Indexing with Proxy

## Purpose

Proxy Indexing Monitor shows documents indexed through proxy use.

Use it when YaCy is configured as a proxy and browsing activity feeds the index.

## What You Can Do Here

- Proxy Indexing Monitor shows documents indexed through proxy use.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

Monitoring pages read live peer state from queues, logs, network tables, memory counters, or process trackers. They are safest when used first as observation tools and only then as entry points to tuning pages.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `proxyYacyOnly` | .yacy-domains only. | .yacy-domains only |
| `proxyPrefetchDepth` | Prefetch Depth. | Integer value. |
| `proxyStoreHTCache` | Store to Cache. | Store to Cache |
| `proxyIndexingLocalText` | Do Local Text-Indexing. | Do Local Text-Indexing |
| `proxyIndexingLocalMedia` | Do Local Media-Indexing. | Do Local Media-Indexing |
| `proxyIndexingRemote` | Do Remote Indexing. | Do Remote Indexing |
| `proxyCache` | Path. | Text value; use the page label and surrounding context to choose the exact content. |
| `proxyCacheSize` | Size. | Text value; use the page label and surrounding context to choose the exact content. |
| `proxyprofileset` | .yacy-domains only. | `Set proxy profile` |

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ProxyIndexingMonitor_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/ProxyIndexingMonitor_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ProxyIndexingMonitor_p.html` | `POST` | admin | `source/net/yacy/htroot/ProxyIndexingMonitor_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `proxyYacyOnly` | .yacy-domains only. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `proxyPrefetchDepth` | Prefetch Depth. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `proxyStoreHTCache` | Store to Cache. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `proxyIndexingLocalText` | Do Local Text-Indexing. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `proxyIndexingLocalMedia` | Do Local Media-Indexing. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `proxyIndexingRemote` | Do Remote Indexing. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `proxyCache` | Path. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `proxyCacheSize` | Size. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `proxyprofileset` | .yacy-domains only. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
POST /ProxyIndexingMonitor_p.html
Content-Type: application/x-www-form-urlencoded

proxyYacyOnly=...&proxyPrefetchDepth=...&proxyStoreHTCache=...&proxyIndexingLocalText=...&proxyIndexingLocalMedia=...
```

## What To Expect

Expect observations: counts, logs, queues, timing, network rows, thread states, or resource values. Monitoring does not fix the issue by itself; it points to the next page or setting to change.

## Related Pages

- `Settings_p.html`
- `CrawlResults.html`
