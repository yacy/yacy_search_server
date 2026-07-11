---
page: htroot/Vocabulary_p.html
help: help/Vocabulary_p.md
title: Federated Index
package: content-apps
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/Vocabulary_p.java
---

# Federated Index

## Purpose

Federated Index Vocabulary manages named vocabulary terms.

Use it to keep controlled terms that can support structured indexing or search.

## What You Can Do Here

- Federated Index Vocabulary manages named vocabulary terms.
- Edit or inspect the specific content object shown on the page.
- Check whether the result is local-only, user-visible, or peer-visible before publishing.

## Page Architecture

Content application pages store and render peer-local objects such as bookmarks, messages, tables, profiles, or translations. They combine form editing with a rendered view of the stored object.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `vocabulary` | Vocabulary Name / Objectspace. | Text value; use the page label and surrounding context to choose the exact content. |
| `view` | Vocabulary Name. | `View` |
| `discovername` | Vocabulary Name. | Text value; use the page label and surrounding context to choose the exact content. |
| `discovermethod` | Empty Vocabulary / from file name / from page title. Options: `none` = Empty Vocabulary, `path` = from file name, `title` = from page title, `titlesplitted` = from page title (split), `author` = from page author, `csv` = Import from a csv file. | `none` = Empty Vocabulary, `path` = from file name, `title` = from page title, `titlesplitted` = from page title (split), `author` = from page author, `csv` = Import from a csv file |
| `discoverobjectspace` | Objectspace. | `http://` |
| `discoverpath` | File Path or URL. | Text value; use the page label and surrounding context to choose the exact content. |
| `discoverLineStart` | Start line. | `0` |
| `discovercolumnliteral` | Column for Literals. | `0` |
| `discoversynonymsmethod` | no Synonyms / Auto-Enrich with Synonyms from Stemming Library / Read Column. Options: `none` = no Synonyms, `enrichsynonyms` = Auto-Enrich with Synonyms from Stemming Library, `readcolumn` = Read Column. | `none` = no Synonyms, `enrichsynonyms` = Auto-Enrich with Synonyms from Stemming Library, `readcolumn` = Read Column |
| `discovercolumnsynonyms` | no Synonyms. | `1` |
| `discovercolumnobjectlink` | Column for Object Link (optional). | `1` |
| `charset` | Charset of Import File. | Text value; use the page label and surrounding context to choose the exact content. |
| `columnSeparator` | Column separator. Options: `,` = Comma ',', `;` = Semicolon ';'. | `,` = Comma ',', `;` = Semicolon ';' |
| `create` | Vocabulary Name. | `Create` |
| `objectspace` | Objectspace. | Text value; use the page label and surrounding context to choose the exact content. |
| `isFacet` | Is Facet?. | Is Facet? |
| `vocabularies.matchLinkedData` | Cleartext / Linked data/Semantic web annotations. Options: `false` = Cleartext, `true` = Linked data/Semantic web annotations. | `false` = Cleartext, `true` = Linked data/Semantic web annotations |
| `modify_#[term]#` | add. | `checked` = add |
| `delete_#[term]#` | add. | `checked` = add |
| `synonyms_#[term]#` | add. | Text value; use the page label and surrounding context to choose the exact content. |
| `objectlink_#[term]#` | add. | Text value; use the page label and surrounding context to choose the exact content. |
| `add_new` | add. | `checked` = add |
| `newterm` | add. | Text value; use the page label and surrounding context to choose the exact content. |
| `newsynonyms` | add. | Text value; use the page label and surrounding context to choose the exact content. |
| `newobjectlink` | add. | Text value; use the page label and surrounding context to choose the exact content. |
| `clear_table` | clear table (remove all terms). | `checked` = clear table (remove all terms) |
| `delete_vocabulary` | delete vocabulary. | `checked` = delete vocabulary |
| `set` | Submits and applies the basic configuration. | `Submit` |

## Correct Use

Edit content deliberately and check the rendered page after saving. For shared or peer-visible features, assume written content may be read by someone else unless the page clearly says otherwise.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/Vocabulary_p.html`.

Backend checks: transaction token for protected POST, transaction token issued for forms.

## Automation And API

Page backend: `source/net/yacy/htroot/Vocabulary_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/Vocabulary_p.html` | `GET` | admin | `source/net/yacy/htroot/Vocabulary_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `vocabulary` | Vocabulary Name / Objectspace. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `view` | Vocabulary Name. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `discovername` | Vocabulary Name. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `discovermethod` | Empty Vocabulary / from file name / from page title. Options: `none` = Empty Vocabulary, `path` = from file name, `title` = from page title, `titlesplitted` = from page title (split), `author` = from page author, `csv` = Import from a csv file. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `discoverobjectspace` | Objectspace. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `discoverpath` | File Path or URL. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `discoverLineStart` | Start line. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `discovercolumnliteral` | Column for Literals. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `discoversynonymsmethod` | no Synonyms / Auto-Enrich with Synonyms from Stemming Library / Read Column. Options: `none` = no Synonyms, `enrichsynonyms` = Auto-Enrich with Synonyms from Stemming Library, `readcolumn` = Read Column. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `discovercolumnsynonyms` | no Synonyms. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `discovercolumnobjectlink` | Column for Object Link (optional). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `charset` | Charset of Import File. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `columnSeparator` | Column separator. Options: `,` = Comma ',', `;` = Semicolon ';'. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `create` | Vocabulary Name. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `objectspace` | Objectspace. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `isFacet` | Is Facet?. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `vocabularies.matchLinkedData` | Cleartext / Linked data/Semantic web annotations. Options: `false` = Cleartext, `true` = Linked data/Semantic web annotations. | Controls the scope or format of the result. Prefer the narrowest value that answers the request. |
| `modify_#[term]#` | add. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `delete_#[term]#` | add. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `synonyms_#[term]#` | add. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `objectlink_#[term]#` | add. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `add_new` | add. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `newterm` | add. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `newsynonyms` | add. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `newobjectlink` | add. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `clear_table` | clear table (remove all terms). | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |
| `delete_vocabulary` | delete vocabulary. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `set` | Submit action that saves the page settings. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |

Example request shape:

```http
GET /Vocabulary_p.html?vocabulary=...&view=...&discovername=...&discovermethod=...&discoverobjectspace=...
```

## What To Expect

Expect stored or rendered content: a message, page, table row, bookmark, profile, translation, or file view. After changes, reload or revisit the object to confirm what was actually saved.

## Related Pages

- Related content work usually continues on the matching bookmark, message, table, translation, vocabulary, or file page.
