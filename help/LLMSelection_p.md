---
page: htroot/LLMSelection_p.html
help: help/LLMSelection_p.md
title: LLM Selection
package: ranking-ai-analysis
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/LLMSelection_p.java
---

# LLM Selection

## Purpose

LLM Selection chooses the language model provider or model profile used by YaCy's AI and retrieval features.

Use it before RAG or AI analysis work so later pages know which model endpoint and capability profile to use.

## What You Can Do Here

- Choose the LLM service family YaCy should use for AI-assisted workflows.
- Set the host or endpoint stub so later RAG and AI pages know where to send model requests.
- Verify the selected service before troubleshooting model-assisted answers elsewhere.

## Page Architecture

The page chooses an LLM service family and base host for model-assisted features. `service` selects the integration style, for example Ollama, LM Studio, OpenAI, or Open Router. `hoststub` points YaCy at the local or remote API base that will receive model requests.

## Correct Use

Select the service that matches the running model endpoint. For local tools such as Ollama or LM Studio, verify the local server URL first. For hosted services, treat API keys and host settings as sensitive operational configuration.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/LLMSelection_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/LLMSelection_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/LLMSelection_p.html` | `GET` | admin | `source/net/yacy/htroot/LLMSelection_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `service` | Choice value. Options: `OLLAMA` = Ollama, `LMSTUDIO` = LMStudio, `OPENAI` = OpenAI, `OPENROUTER` = Open Router. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `hoststub` | Host or domain scope. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET /LLMSelection_p.html?service=...&hoststub=...&apikey=...&llmselection=...&BODY=...
```

## What To Expect

After saving, RAG and AI analysis pages should use the selected provider profile. Model failures after this point usually mean the model server, API key, host URL, or model name needs checking.

## Related Pages

- Related quality work usually continues on ranking settings, content analysis, LLM selection, RAG configuration, or a representative search result page.
