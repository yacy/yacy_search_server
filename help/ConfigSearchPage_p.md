---
page: htroot/ConfigSearchPage_p.html
help: help/ConfigSearchPage_p.md
title: Search Page
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ConfigSearchPage_p.java
---

# Search Page

## Purpose

Search Page settings control how the result page looks and which search features are offered.

Use it to shape the default search experience before users begin relying on the portal.

## What You Can Do Here

- Configure default search-page behavior and presentation.
- Decide which navigators, result types, and search options users should see.
- Test a normal query after saving so ranking and presentation can be checked together.

## Page Architecture

This page configures the result page: which result controls, navigators, media views, and presentation defaults users see after they submit a query. It sits between the raw search backend and the human search experience.

## Correct Use

Change result-page options with a real query in mind. After saving, run representative searches for text, media, and restricted collections so you can see whether the page still guides users clearly.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ConfigSearchPage_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/ConfigSearchPage_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ConfigSearchPage_p.html` | `POST` | admin | `source/net/yacy/htroot/ConfigSearchPage_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `publicTopmenu` | Sort by Descending counts Ascending counts Descending labels Ascending labels. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.publicTopNavBar.login` | Choice value. Options: `true`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.navigation.protocol` | Sort by Descending counts Ascending counts Descending labels Ascending labels. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.navigation.topics` | Sort by Descending counts Ascending counts Descending labels Ascending labels. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.navigation.location` | Sort by Descending counts Ascending counts Descending labels Ascending labels. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `del.nav` | Sort by Descending counts Ascending counts Descending labels Ascending labels. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.navigation.#[name]#.navSort` | Sort by Descending counts Ascending counts Descending labels Ascending labels. Options: `count:desc` = Descending counts, `count:asc` = Ascending counts, `label:desc` = Descending labels, `label:asc` = Ascending labels. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `query` | Search text. Use ordinary search terms, quoted phrases where supported by YaCy query parsing, and optional YaCy modifiers such as collection filters when you intentionally need them. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `search.text` | Text. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.image` | Images. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.audio` | Audio. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.video` | Video. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.app` | Applications. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.options` | more options. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.navigation.date` | Date Navigation. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.navigation.dates.maxcount` | Date Navigation. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `search.result.show.favicon` | Show websites favicon. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.result.show.keywords` | Max. tags initially displayed (remaining can then be expanded). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.result.keywords.firstMaxCount` | Max. tags initially displayed (remaining can then be expanded). | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `search.result.show.date` | Max. tags initially displayed (remaining can then be expanded). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.result.show.size` | Max. tags initially displayed (remaining can then be expanded). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.result.show.metadata` | Max. tags initially displayed (remaining can then be expanded). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.result.show.parser` | Max. tags initially displayed (remaining can then be expanded). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.result.show.citation` | Max. tags initially displayed (remaining can then be expanded). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.result.show.pictures` | Max. tags initially displayed (remaining can then be expanded). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.result.show.cache` | Max. tags initially displayed (remaining can then be expanded). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.result.show.proxy` | Max. tags initially displayed (remaining can then be expanded). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.result.show.indexbrowser` | Max. tags initially displayed (remaining can then be expanded). | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `search.navigation.navname` | append #{search.navigation.list}# #{/search.navigation.list}#. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `add.nav` | append #{search.navigation.list}# #{/search.navigation.list}#. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `search.navigation.maxcount` | max. items. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `searchpage_set` | append #{search.navigation.list}# #{/search.navigation.list}#. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `searchpage_default` | append #{search.navigation.list}# #{/search.navigation.list}#. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.navigation.active` | Sort by Descending counts Ascending counts Descending labels Ascending labels. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /ConfigSearchPage_p.html
Content-Type: application/x-www-form-urlencoded

query=...&publicTopmenu=...&search.publicTopNavBar.login=...&search.navigation.protocol=...&search.navigation.topics=...
```

## What To Expect

A successful change affects future result-page rendering. It does not itself add documents to the index or change crawler behavior.

## Related Pages

- `ConfigAppearance_p.html`
- `ConfigPortal_p.html`
- `yacysearch.html`
- `ViewFile.html`
- `api/citation.html`
- `CacheResource_p.html`
- `proxy.html`
- `IndexBrowser_p.html`
- `Settings_p.html`
