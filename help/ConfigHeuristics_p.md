---
page: htroot/ConfigHeuristics_p.html
help: help/ConfigHeuristics_p.md
title: Heuristics Configuration
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ConfigHeuristics_p.java
---

# Heuristics Configuration

## Purpose

Heuristics Configuration controls optional search heuristics that can broaden or improve result discovery beyond the most direct index lookup.

Use it when the peer should apply helper strategies during search, for example to enrich result discovery or compensate for sparse local index coverage. Heuristics can make search feel smarter, but they should be enabled deliberately because every extra strategy can also add cost or change result expectations.

## What You Can Do Here

- Enable or disable the heuristic sources that YaCy may consult during search.
- Add or remove OpenSearch systems that can supplement local results.
- Compare search behavior before and after changes so extra result sources do not hide the quality of the local index.

## Page Architecture

Configuration pages usually contain persistent settings. A visible form writes values into YaCy configuration, while the backend may reload subsystems such as language files, network listeners, cache handling, or search presentation.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `site_check` | Choice value. Options: `site`. | `site` |
| `searchresult_check` | Choice value. Options: `searchresult`. | `searchresult` |
| `searchresultglobal_check` | Choice value. Options: `siteresultglobal`. | `siteresultglobal` |
| `opensearch_check` | Choice value. Options: `opensearch`. | `opensearch` |
| `ossys_#[title]#` | Choice value. Options: `checked`. | `checked` |
| `ossys_url_#[title]#` | Human-readable title. | URL or URL-derived value; use the exact format shown by the page. |
| `ossys_del_#[title]#` | Choice value. Options: `checked`. | `checked` |
| `ossys_newtitle` | Human-readable title. | Text value; use the page label and surrounding context to choose the exact content. |

## Correct Use

Read the current value before changing it. Configuration changes often persist beyond the current request and may affect later crawling, search, network contact, authentication, or resource use. Change one operational idea at a time and verify the result.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ConfigHeuristics_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/ConfigHeuristics_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ConfigHeuristics_p.html` | `GET` | admin | `source/net/yacy/htroot/ConfigHeuristics_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `site_check` | Choice value. Options: `site`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `searchresult_check` | Choice value. Options: `searchresult`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `searchresultglobal_check` | Choice value. Options: `siteresultglobal`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `opensearch_check` | Choice value. Options: `opensearch`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ossys_#[title]#` | Choice value. Options: `checked`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ossys_url_#[title]#` | Human-readable title. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ossys_del_#[title]#` | Choice value. Options: `checked`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ossys_newtitle` | Human-readable title. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
GET /ConfigHeuristics_p.html?site_check=...&searchresult_check=...&searchresultglobal_check=...&opensearch_check=...&ossys_#[title]#=...
```

## What To Expect

A successful change is visible as a saved value, a confirmation, or changed behavior on a related page. Some settings take effect immediately; others require reconnecting, reloading translations, restarting services, or watching the status page.

## Related Pages

- `IndexSchema_p.html`
