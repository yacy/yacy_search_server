---
page: htroot/FocusedProfiles_p.html
help: help/FocusedProfiles_p.md
title: Focused Autocrawler Profiles
package: crawler
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/FocusedProfiles_p.java
---

# Focused Autocrawler Profiles

## Purpose

Configure and inspect YaCy's focused crawl profiles. A profile submits work to
YaCy's native crawler and keeps its policy, discovery metrics, and recoverable
frontier in persistent state.

## Status and resource use

`GET /FocusedProfiles_p.html` shows the last queue size sampled by the
scheduler, the current entry count for an already open frontier, cumulative
link/seed discovery totals, and the last scheduler reason. The queue sample time
is available in JSON as `queueSizeObservedAt` (Unix epoch milliseconds).

`GET /FocusedProfiles_p.json` returns the scheduler resource status, profile
metrics, and `stateStores` summaries. A profile's `frontier` object contains
`total` when its store is already open and `countsAvailable: false`. It does not
calculate per-state frontier counts during a status request: that would read and
decode the complete disk-backed frontier while blocking scheduler work. The
`stateStores.profiles[].frontier` summary reports `records`,
`estimatedIndexBytes`, `heapFileBytes`, and `mapHeapCacheEntries` without a row
scan when that frontier store is already open. Discovery metrics are cumulative
counters, not counts of current frontier rows.

The status response also includes `resource` fields for pause state, pause
cause, JVM headroom thresholds, healthy recovery checks, and system-memory
health. A status read does not initialize crawl profiles or traverse the native
crawl queue.

## Access and endpoints

Administrator access is required.

| Endpoint | Method | Use |
| --- | --- | --- |
| `/FocusedProfiles_p.html` | `GET` | Show profile configuration and current cached status. |
| `/FocusedProfiles_p.json` | `GET` | Return scheduler, profile, resource, and focused-store status as JSON. |
| `/FocusedProfiles_p.xml` | `GET` | Return the same page properties as XML. |
| `/FocusedProfiles_p.html` | `POST` | Validate or change profile/scheduler settings. |

## POST parameters

| Parameter | Effect |
| --- | --- |
| `pause` | Pauses focused profile scheduling. It does not clear queues or frontier state. |
| `resume` | Resumes focused profile scheduling unless another resource guard pauses native crawling. |
| `reload` | Reloads configured profiles and restarts their scheduler thread. |
| `validate` with `configuration` | Validates a submitted profile document without saving it. |
| `saveProfile` or `importProfile` with `configuration` | Saves the submitted profile configuration and reloads profiles. |
| `setupProfile` with `setup_id`, `setup_description`, `setup_seeds`, `setup_queue_target`, `setup_refill_below`, and `setup_hard_maximum` | Saves a basic focused profile and reloads profiles. |
| `setEnabled` with `id` and `enabled` | Enables or disables the named profile and reloads profiles. |
| `resetFrontier` with `id` | Snapshots queued requests for that focused profile into its persistent frontier, then removes those profile entries from the native queue. This changes crawl state and must be used only for an explicitly requested recovery. |

## Related pages

- [Crawler](Crawler_p.md)
- [Autocrawl](Autocrawl_p.md)
