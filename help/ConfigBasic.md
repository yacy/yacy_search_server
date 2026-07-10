---
page: htroot/ConfigBasic.html
help: help/ConfigBasic.md
title: Access Configuration
package: configuration-administration
access: mixed
kind: ui-page
backend_java: source/net/yacy/htroot/ConfigBasic.java
---

# Access Configuration

## Purpose

Basic Configuration is the first meaningful setup page after installation. It sets the language of the web interface, the use case of the peer, the peer name, and the network port. These are not cosmetic details: they decide who can comfortably use YaCy, whether the peer joins the community network, whether it behaves as a private portal, and how other browsers or peers reach it.

The language selector shows that YaCy is built for an international audience. The value `default` uses English. The two-letter values select specific translations, for example `de` for German, `fr` for French, `es` for Spanish, `zh` for Chinese, `ja` for Japanese, and `ko` for Korean.

## What You Can Do Here

- Choose the interface language used by YaCy pages.
- Choose the operating mode: community search, independent portal, or intranet indexing.
- Set a recognizable peer name for this YaCy instance.
- Change the HTTP port and optionally enable HTTPS.
- Ask YaCy to configure router port mapping through UPnP when available.
- Continue to the next setup steps: account security, search, crawling, peer profile, and network monitoring.

## Page Architecture

The page is organized as a setup checklist. Language is first because it affects the whole user interface. Use case comes next because it changes YaCy's network and index-sharing behavior. Each use-case option is shown as a card containing its radio button, name, illustration, and description. Peer name and port define how this peer identifies itself and how it is reached.

| Control | Meaning | Valid values |
| --- | --- | --- |
| `language` | Interface language. `default` selects English, and two-letter language codes select translations. | `default`, `de`, `fr`, `pl`, `el`, `it`, `es`, `tr`, `sk`, `uk`, `ru`, `zh`, `hi`, `ja`, `ko` |
| `usecase` | Peer operating mode. | `freeworld` = join the community-based search network; `portal` = independent search portal for your own pages; `intranet` = private/local indexing for intranet, file, FTP, SMB, or local-domain content |
| `peername` | Human-readable peer name. Java accepts names matching `[A-Za-z0-9\-_]{3,80}` after spaces are converted to hyphens. | Example: `research-peer`, `library_search`, `OfficeNode01` |
| `port` | HTTP port where YaCy listens. The form accepts ports above 1023; changing it redirects the browser to the new address. | Default is commonly `8090` |
| `withssl` | Enables HTTPS support. | Checkbox: present means enabled |
| `enableUpnp` | Enables router port mapping through UPnP when the environment supports it. | Checkbox: present means enabled |
| `set` | Applies the configuration. | Submit button |

## Correct Use

Pick the language first. If translated pages for the selected language are not generated yet, YaCy can generate them and reload the page. Then choose the use case deliberately. `freeworld` enables community network participation and index exchange. `portal` behaves independently and is suited for a topic or site search. `intranet` is for private/local material and must be handled carefully to avoid exposing private documents.

Changing the port or SSL setting changes how the browser reaches YaCy. Expect a reconnect or redirect. Change the administrator password on `ConfigAccounts_p.html` before exposing the peer to other machines.

## Access And Safety

The page may be visible, but the backend performs authentication checks for protected actions.

Protected related endpoint(s): `/ConfigBasic.html`.

Backend checks: administrator authentication, transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/ConfigBasic.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ConfigBasic.html` | `POST` | mixed | `source/net/yacy/htroot/ConfigBasic.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `language` | Interface language. Values are `default` for English, or a language code such as `de`, `fr`, `es`, `zh`, `ja`, or `ko`. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `usecase` | Operating mode for the peer: `freeworld` joins the community search network, `portal` creates an independent search portal, and `intranet` is for local/private indexing. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `peername` | Public peer name. Use 3 to 80 letters, digits, hyphen, or underscore; spaces are converted to hyphens. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `port` | HTTP port where YaCy listens. Values below 1024 are ignored by this form; changing the port triggers reconnect/redirect behavior. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `withssl` | Enables HTTPS support for the peer. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `enableUpnp` | Asks YaCy to configure router port mapping through UPnP when available. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `set` | Submit action that saves the page settings. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
POST /ConfigBasic.html
Content-Type: application/x-www-form-urlencoded

language=...&usecase=...&peername=...&port=...&withssl=...
```

## What To Expect

Changing `language` can immediately reload the interface in the selected translation. Changing `port` or SSL can trigger a reconnect to the new address. Changing `usecase` changes network/index-sharing behavior and may be blocked or warned when remote Solr data could expose private or irrelevant documents.

## Related Pages

- `ConfigAccounts_p.html`
- `IndexControlURLs_p.html`
- `IndexFederated_p.html`
- `Settings_p.html`
- `index.html`
- `CrawlStartSite.html`
- `ConfigProfile_p.html`
- `Network.html`
