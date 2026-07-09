---
page: htroot/AIShield_p.html
help: help/AIShield_p.md
title: AI Shield
package: ranking-ai-analysis
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/AIShield_p.java
---

# AI Shield

## Purpose

AI Shield configures safeguards around AI-assisted features.

Use it to decide which model-assisted operations are allowed and how aggressively the peer should protect prompts, responses, and retrieved context.

## What You Can Do Here

- AI Shield configures safeguards around AI-assisted features.
- Use representative queries or documents when evaluating changes.
- Change one model, field, weight, or threshold at a time so the effect can be explained.

## Page Architecture

Ranking and analysis pages expose the signals that influence result order or retrieval augmentation. The visible form usually maps directly to weights, model choices, field selections, or diagnostic thresholds.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `ai.shield.limit-all` | Limit for all requests, including localhost. | Limit for all requests, including localhost |
| `ai.shield.all.per-minute` | Per minute. | Text value; use the page label and surrounding context to choose the exact content. |
| `ai.shield.all.per-hour` | Per minute. | Text value; use the page label and surrounding context to choose the exact content. |
| `ai.shield.all.per-day` | Per minute. | Text value; use the page label and surrounding context to choose the exact content. |
| `ai.shield.allow-nonlocalhost` | Allow non-localhost clients to access the chat interface. | Allow non-localhost clients to access the chat interface |
| `ai.shield.rate.per-minute` | Per minute. | Text value; use the page label and surrounding context to choose the exact content. |
| `ai.shield.rate.per-hour` | Per minute. | Text value; use the page label and surrounding context to choose the exact content. |
| `ai.shield.rate.per-day` | Per minute. | Text value; use the page label and surrounding context to choose the exact content. |
| `ai.shield.show-chat-link` | Show a link to yacychat.html on the search front page. | Show a link to yacychat.html on the search front page |

## Correct Use

Use representative test queries or documents. Ranking, analysis, and AI settings are meaningful only when their effect can be compared. Keep notes about changed weights, model choices, fields, or thresholds.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/AIShield_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/AIShield_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/AIShield_p.html` | `POST` | admin | `source/net/yacy/htroot/AIShield_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `ai.shield.limit-all` | Limit for all requests, including localhost. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ai.shield.all.per-minute` | Per minute. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ai.shield.all.per-hour` | Per minute. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ai.shield.all.per-day` | Per minute. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ai.shield.allow-nonlocalhost` | Allow non-localhost clients to access the chat interface. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ai.shield.rate.per-minute` | Per minute. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ai.shield.rate.per-hour` | Per minute. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ai.shield.rate.per-day` | Per minute. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ai.shield.show-chat-link` | Show a link to yacychat.html on the search front page. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /AIShield_p.html
Content-Type: application/x-www-form-urlencoded

ai.shield.limit-all=...&ai.shield.all.per-minute=...&ai.shield.all.per-hour=...&ai.shield.all.per-day=...&ai.shield.allow-nonlocalhost=...
```

## What To Expect

Expect configuration values, diagnostics, or changed result behavior. The effect may only become visible after running the same query again, re-indexing fields, or using the configured model/RAG workflow.

## Related Pages

- Related quality work usually continues on ranking settings, content analysis, LLM selection, RAG configuration, or a representative search result page.
