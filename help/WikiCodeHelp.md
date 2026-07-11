---
page: htroot/WikiCodeHelp.html
help: help/WikiCodeHelp.md
title: WikiCode Help
package: content-apps
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/WikiCodeHelp.java
---

# WikiCode Help

## Purpose

WikiCode Help documents the formatting still used by messages, profiles, and MediaWiki dump imports.

Use it when composing content in one of those remaining WikiCode-enabled fields.

## What You Can Do Here

- WikiCode Help explains the remaining reusable formatting syntax.
- Compare markup examples with their rendered output.
- Return to the message or profile form where the formatted content is edited.

## Page Architecture

This is a read-only syntax reference. The local Wiki application and its page store have been retired.

## Correct Use

Use this page as a syntax reference. Editing and saving happen in the calling message or profile page, not in this help page.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

Page backend: `source/net/yacy/htroot/WikiCodeHelp.java`.

No request parameters are needed for normal use of this page.

## What To Expect

Expect a static table of supported WikiCode constructs and their rendered forms. No content is stored by this page.

## Related Pages

- `MessageSend_p.html`
- `ConfigProfile_p.html`
- `mediawiki_p.html`
