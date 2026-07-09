---
page: htroot/RankingRWI_p.html
help: help/RankingRWI_p.md
title: RWI Ranking Configuration
package: ranking-ai-analysis
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/RankingRWI_p.java
---

# RWI Ranking Configuration

## Purpose

RWI Ranking Configuration controls ranking for YaCy's reverse word index.

Use it to tune classic YaCy ranking signals such as word statistics, locality, authority, and document properties.

## What You Can Do Here

- RWI Ranking Configuration controls ranking for YaCy's reverse word index.
- Use representative queries or documents when evaluating changes.
- Change one model, field, weight, or threshold at a time so the effect can be explained.

## Page Architecture

Ranking and analysis pages expose the signals that influence result order or retrieval augmentation. The visible form usually maps directly to weights, model choices, field selections, or diagnostic thresholds.

| Control | Meaning | Values or examples |
| --- | --- | --- |

## Correct Use

Use representative test queries or documents. Ranking, analysis, and AI settings are meaningful only when their effect can be compared. Keep notes about changed weights, model choices, fields, or thresholds.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/RankingRWI_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/RankingRWI_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/RankingRWI_p.html` | `POST` | admin | `source/net/yacy/htroot/RankingRWI_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |

Example request shape:

```http
POST /RankingRWI_p.html
Content-Type: application/x-www-form-urlencoded

#[nameorg]#=...&slider_#[nameorg]#=...&EnterRanking=...&ResetRanking=...
```

## What To Expect

Expect configuration values, diagnostics, or changed result behavior. The effect may only become visible after running the same query again, re-indexing fields, or using the configured model/RAG workflow.

## Related Pages

- Related quality work usually continues on ranking settings, content analysis, LLM selection, RAG configuration, or a representative search result page.
