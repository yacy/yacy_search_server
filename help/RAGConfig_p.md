---
page: htroot/RAGConfig_p.html
help: help/RAGConfig_p.md
title: Wire RAG Retrieval
package: ranking-ai-analysis
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/RAGConfig_p.java
---

# Wire RAG Retrieval

## Purpose

Wire RAG Retrieval configures how YaCy turns indexed documents into context for retrieval-augmented generation.

Use it to tune the bridge between search results and generated answers: retrieval scope, context shape, and model-facing behavior.

## What You Can Do Here

- Wire RAG Retrieval configures how YaCy turns indexed documents into context for retrieval-augmented generation.
- Use representative queries or documents when evaluating changes.
- Change one model, field, weight, or threshold at a time so the effect can be explained.

## Page Architecture

Ranking and analysis pages expose the signals that influence result order or retrieval augmentation. The visible form usually maps directly to weights, model choices, field selections, or diagnostic thresholds.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `ai.llm-user-prefix` | User or account value. | Text value; use the page label and surrounding context to choose the exact content. |

## Correct Use

Use representative test queries or documents. Ranking, analysis, and AI settings are meaningful only when their effect can be compared. Keep notes about changed weights, model choices, fields, or thresholds.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/RAGConfig_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/RAGConfig_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/RAGConfig_p.html` | `POST` | admin | `source/net/yacy/htroot/RAGConfig_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `ai.llm-user-prefix` | User or account value. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /RAGConfig_p.html
Content-Type: application/x-www-form-urlencoded

ai.system-prompt=...&ai.llm-user-prefix=...&ai.llm-query-generator-prefix=...&ai.rag.search-document-maxlength=...
```

## What To Expect

Expect configuration values, diagnostics, or changed result behavior. The effect may only become visible after running the same query again, re-indexing fields, or using the configured model/RAG workflow.

## Related Pages

- Related quality work usually continues on ranking settings, content analysis, LLM selection, RAG configuration, or a representative search result page.
