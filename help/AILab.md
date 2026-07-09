---
page: htroot/AILab.html
help: help/AILab.md
title: AI Lab
package: ranking-ai-analysis
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/AILab.java
---

# AI Lab

## Purpose

AI Lab is a workspace for experimenting with AI-assisted retrieval on top of YaCy's index.

Use it to test how indexed documents can support model-assisted answers before making the behavior part of a production workflow.

## What You Can Do Here

- AI Lab is a workspace for experimenting with AI-assisted retrieval on top of YaCy's index.
- Use representative queries or documents when evaluating changes.
- Change one model, field, weight, or threshold at a time so the effect can be explained.

## Page Architecture

Ranking and analysis pages expose the signals that influence result order or retrieval augmentation. The visible form usually maps directly to weights, model choices, field selections, or diagnostic thresholds.

## Correct Use

Use representative test queries or documents. Ranking, analysis, and AI settings are meaningful only when their effect can be compared. Keep notes about changed weights, model choices, fields, or thresholds.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

Page backend: `source/net/yacy/htroot/AILab.java`.

No request parameters are needed for normal use of this page.

## What To Expect

Expect configuration values, diagnostics, or changed result behavior. The effect may only become visible after running the same query again, re-indexing fields, or using the configured model/RAG workflow.

## Related Pages

- `LLMSelection_p.html`
- `CrawlStartSite.html`
- `IndexPackDownloader_p.html`
- `RAGConfig_p.html`
- `yacychat.html`
- `ToolsConfig_p.html`
- `LogReports_p.html`
- `AIShield_p.html`
