---
page: htroot/ConfigPortal_p.html
help: help/ConfigPortal_p.md
title: Integration of a Search Portal
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ConfigPortal_p.java
---

# Integration of a Search Portal

## Purpose

Integration of a Search Portal controls the public search portal around the result page.

Use it to make YaCy feel like a site search or topic portal instead of a raw administration interface.

## What You Can Do Here

- Configure the public search portal that users see before and after searching.
- Adjust branding, navigation, and result-page behavior so the portal fits the intended audience.
- Check the public search page after saving, not only the administration form.

## Page Architecture

The portal configuration page controls the public shell around YaCy search: names, navigation, visible features, and integration details. It separates administration of the portal from the search index itself; changing the portal changes how users approach search, not which documents exist.

## Correct Use

Configure the portal for the audience first: public web search, site search, intranet search, or a specialized collection. After saving, open the public search page as a normal user would and check whether the labels, links, and available search options make sense.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ConfigPortal_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/ConfigPortal_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ConfigPortal_p.html` | `POST` | admin | `source/net/yacy/htroot/ConfigPortal_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `promoteSearchPageGreeting` | Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `promoteSearchPageGreeting.homepage` | Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `promoteSearchPageGreeting.smallImage` | Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `promoteSearchPageGreeting.largeImage` | Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `promoteSearchPageGreeting.imageAlt` | Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `publicSearchpage` | Extended. Options: `true` = Extended, `false` = Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `publicTopmenu` | Extended. Options: `true` = Extended, `false` = Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.options` | Extended. Options: `true` = Extended, `false` = Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.strictContentDom` | Extended. Options: `false` = Extended, `true` = Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.jsresort` | On demand, server-side. Options: `false` = On demand, server-side, `true` = On demand, server-side. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `remotesearch.https.preferred` | Prefer https for search queries on remote peers.. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.verify` | Extended. Options: `nocache` = Extended, `iffresh` = Extended, `ifexist` = Extended, `cacheonly` = Extended, `false` = Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.verify.delete` | Extended. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `greedylearning.active` | Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `remotesearch.result.store` | Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `remotesearch.result.store.maxsize` | Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `popup` | Extended. Options: `status` = Extended, `front` = Extended, `search` = Extended, `interactive` = Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `maximumRecords` | Maximum number of results to return on one page. Use a modest value for interactive use; larger values are for controlled scripts. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `indexForward` | Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `target` | Extended. Options: `_blank` = "_blank" (new window), `_self` = "_self" (same window), `_parent` = "_parent" (the parent frame of a frameset), `_top` = "_top" (top of all frames), `searchresult` = "searchresult" (a default custom page name for search results). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `target_special` | Extended. Options: `_blank` = "_blank" (new window), `_self` = "_self" (same window), `_parent` = "_parent" (the parent frame of a frameset), `_top` = "_top" (top of all frames), `searchresult` = "searchresult" (a default custom page name for search results). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `target_special_pattern` | Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `search.excludehosts` | Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `about.headline` | Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `about.body` | Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `searchpage_set` | Extended. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `searchpage_default` | Extended. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /ConfigPortal_p.html
Content-Type: application/x-www-form-urlencoded

maximumRecords=...&promoteSearchPageGreeting=...&promoteSearchPageGreeting.homepage=...&promoteSearchPageGreeting.smallImage=...&promoteSearchPageGreeting.largeImage=...
```

## What To Expect

A successful change alters the public-facing search portal or its generated integration code. Existing indexed documents are not recrawled by this page.

## Related Pages

- `ConfigAppearance_p.html`
- `SearchAccessRate_p.html`
- `Settings_p.html`
- `ConfigHeuristics_p.html`
- `ConfigAccounts_p.html`
