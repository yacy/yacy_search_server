---
page: htroot/Trails.html
help: help/Trails.md
title: CyTag Trails
package: monitoring-performance
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/Trails.java
---

# CyTag Trails

## Purpose

CyTag Trails shows navigation or tag trails recorded by YaCy.

Use it to understand browsing or tagging traces when this feature is active.

## What You Can Do Here

- CyTag Trails shows navigation or tag trails recorded by YaCy.
- Filter or limit the view to the symptom being investigated.
- Use the observation to decide the next crawler, index, network, or configuration action.

## Page Architecture

This is a compact endpoint-style page. Its behavior is driven mainly by request parameters and the selected response template, so callers should send only the fields needed for the specific query or peer-service action.

## Correct Use

Use monitoring pages as evidence, not as guesses. Capture the current state, then connect it to the user-visible symptom: slow search, missing documents, stuck crawl, memory pressure, network isolation, or unexpected access.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

Page backend: `source/net/yacy/htroot/Trails.java`.

No request parameters are needed for normal use of this page.

## What To Expect

Expect observations: counts, logs, queues, timing, network rows, thread states, or resource values. Monitoring does not fix the issue by itself; it points to the next page or setting to change.

## Related Pages

- Related diagnosis usually continues on the status page, log viewer, performance pages, connection tracker, queue monitor, or network view.
