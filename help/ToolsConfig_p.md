---
page: htroot/ToolsConfig_p.html
help: help/ToolsConfig_p.md
title: Tools Config
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ToolsConfig_p.java
---

# Tools Config

## Purpose

Tools Config controls helper tools available inside the YaCy interface.

Use it to enable or adjust optional administration conveniences.

## What You Can Do Here

- Tools Config controls helper tools available inside the YaCy interface.
- Read the current value before changing it.
- Verify the effect on the public page, status page, or related administration page.

## Page Architecture

Configuration pages usually contain persistent settings. A visible form writes values into YaCy configuration, while the backend may reload subsystems such as language files, network listeners, cache handling, or search presentation.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `ai.tools.#[name]#.maxCallsPerTurn` | maxCallsPerTurn. | Integer value. |
| `ai.tools.#[name]#.description` | maxCallsPerTurn. | Text value; use the page label and surrounding context to choose the exact content. |

## Correct Use

Read the current value before changing it. Configuration changes often persist beyond the current request and may affect later crawling, search, network contact, authentication, or resource use. Change one operational idea at a time and verify the result.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ToolsConfig_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/ToolsConfig_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ToolsConfig_p.html` | `POST` | admin | `source/net/yacy/htroot/ToolsConfig_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `ai.tools.#[name]#.maxCallsPerTurn` | maxCallsPerTurn. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `ai.tools.#[name]#.description` | maxCallsPerTurn. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `save` | Saves settings. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
POST /ToolsConfig_p.html
Content-Type: application/x-www-form-urlencoded

ai.tools.#[name]#.maxCallsPerTurn=...&ai.tools.#[name]#.description=...&save=...
```

## What To Expect

A successful change is visible as a saved value, a confirmation, or changed behavior on a related page. Some settings take effect immediately; others require reconnecting, reloading translations, restarting services, or watching the status page.

## Related Pages

- Related configuration work is usually reached from `ConfigBasic.html`, `Settings_p.html`, or the adjacent configuration page in the administration menu.
