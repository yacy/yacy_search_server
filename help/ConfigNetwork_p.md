---
page: htroot/ConfigNetwork_p.html
help: help/ConfigNetwork_p.md
title: Network Configuration
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ConfigNetwork_p.java
---

# Network Configuration

## Purpose

Network Configuration decides how the peer participates in YaCy's network.

Use it to choose between private operation and communication with other peers.

## What You Can Do Here

- Network Configuration decides how the peer participates in YaCy's network.
- Read the current value before changing it.
- Verify the effect on the public page, status page, or related administration page.

## Page Architecture

Configuration pages usually contain persistent settings. A visible form writes values into YaCy configuration, while the backend may reload subsystems such as language files, network listeners, cache handling, or search presentation.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `networkDefinition` | Network and Domain Specification. | Text value; use the page label and surrounding context to choose the exact content. |
| `networkDefinitionURL` | Network and Domain Specification. | URL or URL-derived value; use the exact format shown by the page. |
| `changeNetwork` | Network and Domain Specification. | `Change Network` |
| `network` | Peer-to-Peer Mode / Robinson Mode. Options: `p2p` = Peer-to-Peer Mode, `robinson` = Robinson Mode. | `p2p` = Peer-to-Peer Mode, `robinson` = Robinson Mode |
| `indexDistribute` | Index Distribution. | Index Distribution |
| `indexDistributeWhileCrawling` | enabled / disabled during crawling. Options: `on` = enabled, `off` = disabled during crawling. | `on` = enabled, `off` = disabled during crawling |
| `indexDistributeWhileIndexing` | enabled / disabled during indexing. Options: `on` = enabled, `off` = disabled during indexing. | `on` = enabled, `off` = disabled during indexing |
| `indexReceive` | Index Receive. | Index Receive |
| `indexReceiveBlockBlacklist` | reject / accept transmitted URLs that match your blacklist. Options: `on` = reject, `off` = accept transmitted URLs that match your blacklist. | `on` = reject, `off` = accept transmitted URLs that match your blacklist |
| `indexReceiveSearch` | allow / deny remote search. Options: `on` = allow, `off` = deny remote search. | `on` = allow, `off` = deny remote search |
| `save` | Saves settings. | `Save` |
| `cluster.mode` | Private Peer / Public Peer / Public Cluster. Options: `privatepeer` = Private Peer, `publicpeer` = Public Peer, `publiccluster` = Public Cluster. | `privatepeer` = Private Peer, `publicpeer` = Public Peer, `publiccluster` = Public Cluster |
| `cluster.peers.yacydomain` | Private Peer. | Text value; use the page label and surrounding context to choose the exact content. |
| `peertags` | Peer Tags. | Text value; use the page label and surrounding context to choose the exact content. |
| `network.unit.protocol.https.preferred` | Prefer HTTPS for outgoing connexions to remote peers.. | `true` = Prefer HTTPS for outgoing connexions to remote peers. |
| `setEncryption` | Prefer HTTPS for outgoing connexions to remote peers.. | `Save` |

## Correct Use

Read the current value before changing it. Configuration changes often persist beyond the current request and may affect later crawling, search, network contact, authentication, or resource use. Change one operational idea at a time and verify the result.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/ConfigNetwork_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/ConfigNetwork_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/ConfigNetwork_p.html` | `POST` | admin | `source/net/yacy/htroot/ConfigNetwork_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `networkDefinition` | Network and Domain Specification. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `networkDefinitionURL` | Network and Domain Specification. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `changeNetwork` | Network and Domain Specification. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `network` | Peer-to-Peer Mode / Robinson Mode. Options: `p2p` = Peer-to-Peer Mode, `robinson` = Robinson Mode. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexDistribute` | Index Distribution. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexDistributeWhileCrawling` | enabled / disabled during crawling. Options: `on` = enabled, `off` = disabled during crawling. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexDistributeWhileIndexing` | enabled / disabled during indexing. Options: `on` = enabled, `off` = disabled during indexing. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexReceive` | Index Receive. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexReceiveBlockBlacklist` | reject / accept transmitted URLs that match your blacklist. Options: `on` = reject, `off` = accept transmitted URLs that match your blacklist. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `indexReceiveSearch` | allow / deny remote search. Options: `on` = allow, `off` = deny remote search. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `save` | Saves settings. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `cluster.mode` | Private Peer / Public Peer / Public Cluster. Options: `privatepeer` = Private Peer, `publicpeer` = Public Peer, `publiccluster` = Public Cluster. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `cluster.peers.yacydomain` | Private Peer. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `peertags` | Peer Tags. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `network.unit.protocol.https.preferred` | Prefer HTTPS for outgoing connexions to remote peers.. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `setEncryption` | Prefer HTTPS for outgoing connexions to remote peers.. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
POST /ConfigNetwork_p.html
Content-Type: application/x-www-form-urlencoded

networkDefinition=...&networkDefinitionURL=...&changeNetwork=...&network=...&indexDistribute=...
```

## What To Expect

A successful change is visible as a saved value, a confirmation, or changed behavior on a related page. Some settings take effect immediately; others require reconnecting, reloading translations, restarting services, or watching the status page.

## Related Pages

- `ConfigPortal_p.html`
