---
page: htroot/IndexPackGenerator_p.html
help: help/IndexPackGenerator_p.md
title: Index Pack Generator
package: import-export-federation
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexPackGenerator_p.java
---

# Index Pack Generator

## Purpose

Index Pack Generator creates a package from selected local index data.

Use it to distribute or archive a curated part of the index.

## What You Can Do Here

- Index Pack Generator creates a package from selected local index data.
- Define the source, target, format, collection, and expected size before starting.
- Monitor progress because large transfers continue beyond the initial request.

## Page Architecture

Import and export pages translate external data formats into YaCy documents or move YaCy index data into another store. Most long-running actions create background work that should be monitored afterward.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `category` | Choice value. Options: `mix` = mix - a mix of document types, for content from wide web crawls, `core` = core - technical documentation, operating systems, computer hardware, open source and free software, manuals, protocol standards, `scroll` = scroll - non-technical documents: knowledge, encyclopedia, linguistic corpora, dictionaries, translation memories, texts, non-fiction books, historical books, `regula` = regula - non-technical standards: industry standards, laws, rules, compliance, `gem` = gem - research, papers, university publications, science, `fiction` = fiction - fictional documents: movies, stories, series, books (fiction, science-fiction), `map` = map - geological data, geolocation-data, earth/world information, `echo` = echo – micro-content (tweets, toots, short headlines, SMS corpora), podcasts, radio archives, audio lectures, spoken-word datasets, logs, incidents, telemetry, `spirit` = spirit – related to non-textual data (possibly only metadata): art, music, game assets, creative-commons media (non-text culture loot), `vault` = vault - sensitive data: secrets, leaks, non-public documents, security advisories. | `mix` = mix - a mix of document types, for content from wide web crawls, `core` = core - technical documentation, operating systems, computer hardware, open source and free software, manuals, protocol standards, `scroll` = scroll - non-technical documents: knowledge, encyclopedia, linguistic corpora, dictionaries, translation memories, texts, non-fiction books, historical books, `regula` = regula - non-technical standards: industry standards, laws, rules, compliance, `gem` = gem - research, papers, university publications, science, `fiction` = fiction - fictional documents: movies, stories, series, books (fiction, science-fiction), `map` = map - geological data, geolocation-data, earth/world information, `echo` = echo – micro-content (tweets, toots, short headlines, SMS corpora), podcasts, radio archives, audio lectures, spoken-word datasets, logs, incidents, telemetry, `spirit` = spirit – related to non-textual data (possibly only metadata): art, music, game assets, creative-commons media (non-text culture loot), `vault` = vault - sensitive data: secrets, leaks, non-public documents, security advisories |
| `collection` | Collection name. Use it to group crawled or imported documents and to search or manage that group later. | Collection name such as `user`, `docs`, or a project-specific name. |
| `exportfilter` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | `.*.*` |
| `format` | Choice value. Options: `full-elasticsearch`, `full-solr`, `full-rss`. | `full-elasticsearch`, `full-solr`, `full-rss` |

## Correct Use

Generate an index pack only from a deliberate collection or filter. Index packs are meant to be reused elsewhere, so include enough content to be useful but avoid private or irrelevant documents.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexPackGenerator_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexPackGenerator_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexPackGenerator_p.html` | `POST` | admin | `source/net/yacy/htroot/IndexPackGenerator_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default. Low-level generated parameters are omitted when they are only meaningful inside the rendered YaCy form.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `category` | Choice value. Options: `mix` = mix - a mix of document types, for content from wide web crawls, `core` = core - technical documentation, operating systems, computer hardware, open source and free software, manuals, protocol standards, `scroll` = scroll - non-technical documents: knowledge, encyclopedia, linguistic corpora, dictionaries, translation memories, texts, non-fiction books, historical books, `regula` = regula - non-technical standards: industry standards, laws, rules, compliance, `gem` = gem - research, papers, university publications, science, `fiction` = fiction - fictional documents: movies, stories, series, books (fiction, science-fiction), `map` = map - geological data, geolocation-data, earth/world information, `echo` = echo – micro-content (tweets, toots, short headlines, SMS corpora), podcasts, radio archives, audio lectures, spoken-word datasets, logs, incidents, telemetry, `spirit` = spirit – related to non-textual data (possibly only metadata): art, music, game assets, creative-commons media (non-text culture loot), `vault` = vault - sensitive data: secrets, leaks, non-public documents, security advisories. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `collection` | Collection name. Use it to group crawled or imported documents and to search or manage that group later. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `exportfilter` | Filter expression. It decides which records are included, excluded, displayed, exported, or processed on this page. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `format` | Choice value. Options: `full-elasticsearch`, `full-solr`, `full-rss`. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
POST /IndexPackGenerator_p.html
Content-Type: application/x-www-form-urlencoded

category=...&collection=...&slug=...&exportfilter=...&exportquery=...
```

## What To Expect

A successful run creates a package artifact or package-generation job. Check the package manager or output location before distributing it.

## Related Pages

- Related transfer work is usually reached through the import/export page for the same format, the index-pack pages, or the queue/status page that reports progress.
