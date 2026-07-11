---
page: htroot/VFS.html
help: help/VFS.md
title: Virtual File System
package: content-apps
access: public
kind: ui-page
backend_java: source/net/yacy/htroot/VFS.java
---

# Virtual File System

## Purpose

Virtual File System exposes files stored in YaCy's virtual storage.

Use it to inspect or manage stored file-like content.

## What You Can Do Here

- Virtual File System exposes files stored in YaCy's virtual storage.
- Edit or inspect the specific content object shown on the page.
- Check whether the result is local-only, user-visible, or peer-visible before publishing.

## Page Architecture

Content application pages store and render peer-local objects such as bookmarks, messages, tables, profiles, or translations. They combine form editing with a rendered view of the stored object.

## Correct Use

Edit content deliberately and check the rendered page after saving. For shared or peer-visible features, assume written content may be read by someone else unless the page clearly says otherwise.

## Access And Safety

The page is normally public or read-only, unless the peer is configured to require authentication for all pages.

## Automation And API

Page backend: `source/net/yacy/htroot/VFS.java`.

No request parameters are needed for normal use of this page.

## What To Expect

Expect stored or rendered content: a message, page, table row, bookmark, profile, translation, or file view. After changes, reload or revisit the object to confirm what was actually saved.

## Related Pages

- Related content work usually continues on the matching bookmark, message, table, translation, vocabulary, or file page.
