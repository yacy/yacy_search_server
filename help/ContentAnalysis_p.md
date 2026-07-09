---
page: htroot/ContentAnalysis_p.html
help: help/ContentAnalysis_p.md
title: Content Analysis
package: ranking-ai-analysis
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ContentAnalysis_p.java
---

# Content Analysis

## Purpose

Content Analysis inspects document text for duplicate or near-duplicate patterns.

Use it to understand whether repeated content, boilerplate, or very similar pages are polluting the index.

## What You Can Do Here

- Content Analysis inspects document text for duplicate or near-duplicate patterns.
- Use representative queries or documents when evaluating changes.
- Change one model, field, weight, or threshold at a time so the effect can be explained.

## Page Architecture

Ranking and analysis pages expose the signals that influence result order or retrieval augmentation. The visible form usually maps directly to weights, model choices, field selections, or diagnostic thresholds.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `minTokenLen` | minTokenLen. | Text value; use the page label and surrounding context to choose the exact content. |
| `quantRate` | quantRate. | Text value; use the page label and surrounding context to choose the exact content. |
| `EnterDoublecheck` | minTokenLen. | `Set` |
| `ResetDoublecheck` | minTokenLen. | `Re-Set to default` |

## Correct Use

Use representative test queries or documents. Ranking, analysis, and AI settings are meaningful only when their effect can be compared. Keep notes about changed weights, model choices, fields, or thresholds.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ContentAnalysis_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/ContentAnalysis_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ContentAnalysis_p.html` | `POST` | admin | `source/net/yacy/htroot/ContentAnalysis_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `minTokenLen` | minTokenLen. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `quantRate` | quantRate. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `EnterDoublecheck` | minTokenLen. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ResetDoublecheck` | minTokenLen. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
POST /ContentAnalysis_p.html
Content-Type: application/x-www-form-urlencoded

minTokenLen=...&quantRate=...&EnterDoublecheck=...&ResetDoublecheck=...&ResetRanking=...
```

## What To Expect

Expect configuration values, diagnostics, or changed result behavior. The effect may only become visible after running the same query again, re-indexing fields, or using the configured model/RAG workflow.

## Related Pages

- `https://lucene.apache.org/solr/5_5_2/solr-core/org/apache/solr/update/processor/TextProfileSignature.html`
