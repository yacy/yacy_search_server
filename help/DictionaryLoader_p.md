---
page: htroot/DictionaryLoader_p.html
help: help/DictionaryLoader_p.md
title: Knowledge Loader
package: content-apps
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/DictionaryLoader_p.java
---

# Knowledge Loader

## Purpose

Knowledge Loader imports word lists or dictionaries.

Use it when vocabulary data should support search, classification, or local knowledge features.

## What You Can Do Here

- Knowledge Loader imports word lists or dictionaries.
- Edit or inspect the specific content object shown on the page.
- Check whether the result is local-only, user-visible, or peer-visible before publishing.

## Page Architecture

Content application pages store and render peer-local objects such as bookmarks, messages, tables, profiles, or translations. They combine form editing with a rendered view of the stored object.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `geon0Load` | Download from. | `Load` |
| `geon0Deactivate` | Download from. | `Deactivate` |
| `geon0Remove` | Download from. | `Remove` |
| `geon0Activate` | Download from. | `Activate` |
| `geon1Load` | Download from. | `Load` |
| `geon1Deactivate` | Download from. | `Deactivate` |
| `geon1Remove` | Download from. | `Remove` |
| `geon1Activate` | Download from. | `Activate` |
| `geon2Load` | Download from. | `Load` |
| `geon2Deactivate` | Download from. | `Deactivate` |
| `geon2Remove` | Download from. | `Remove` |
| `geon2Activate` | Download from. | `Activate` |
| `geo0Deactivate` | Downloaded from. | `Deactivate` |
| `geo0Remove` | Downloaded from. | `Remove` |
| `geo1Deactivate` | Downloaded from. | `Deactivate` |
| `geo1Remove` | Downloaded from. | `Remove` |
| `geo2Load` | Downloaded from. | `Load` |
| `geo2Deactivate` | Downloaded from. | `Deactivate` |
| `geo2Remove` | Downloaded from. | `Remove` |
| `geo2Activate` | Downloaded from. | `Activate` |
| `drw0Load` | Download from. | `Load` |
| `drw0Deactivate` | Download from. | `Deactivate` |
| `drw0Remove` | Download from. | `Remove` |
| `drw0Activate` | Download from. | `Activate` |
| `syn0Activate` | Status. | `Activate` |
| `syn0Deactivate` | Status. | `Deactivate` |
| `syn1Activate` | Status. | `Activate` |
| `syn1Deactivate` | Status. | `Deactivate` |
| `syn2Activate` | Status. | `Activate` |
| `syn2Deactivate` | Status. | `Deactivate` |

## Correct Use

Edit content deliberately and check the rendered page after saving. For shared or peer-visible features, assume written content may be read by someone else unless the page clearly says otherwise.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/DictionaryLoader_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/DictionaryLoader_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/DictionaryLoader_p.html` | `POST` | admin | `source/net/yacy/htroot/DictionaryLoader_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `geon0Load` | Download from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `geon0Deactivate` | Download from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `geon0Remove` | Download from. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `geon0Activate` | Download from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `geon1Load` | Download from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `geon1Deactivate` | Download from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `geon1Remove` | Download from. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `geon1Activate` | Download from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `geon2Load` | Download from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `geon2Deactivate` | Download from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `geon2Remove` | Download from. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `geon2Activate` | Download from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `geo0Deactivate` | Downloaded from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `geo0Remove` | Downloaded from. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `geo1Deactivate` | Downloaded from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `geo1Remove` | Downloaded from. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `geo2Load` | Downloaded from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `geo2Deactivate` | Downloaded from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `geo2Remove` | Downloaded from. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `geo2Activate` | Downloaded from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `drw0Load` | Download from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `drw0Deactivate` | Download from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `drw0Remove` | Download from. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `drw0Activate` | Download from. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `syn0Activate` | Status. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `syn0Deactivate` | Status. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `syn1Activate` | Status. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `syn1Deactivate` | Status. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `syn2Activate` | Status. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `syn2Deactivate` | Status. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /DictionaryLoader_p.html
Content-Type: application/x-www-form-urlencoded

geon0Load=...&geon0Deactivate=...&geon0Remove=...&geon0Activate=...&geon1Load=...
```

## What To Expect

Expect stored or rendered content: a message, page, table row, bookmark, profile, translation, or file view. After changes, reload or revisit the object to confirm what was actually saved.

## Related Pages

- `http://www1.ids-mannheim.de/kl/projekte/methoden/derewo.html`
