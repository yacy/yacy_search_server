---
page: htroot/ConfigAccountList_p.html
help: help/ConfigAccountList_p.md
title: User Accounts
package: configuration-administration
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/ConfigAccountList_p.java
---

# User Accounts

## Purpose

User Accounts lists configured accounts and their role-related state.

Use it to audit who can sign in before changing permissions or exposing the peer.

## What You Can Do Here

- User Accounts lists configured accounts and their role-related state.
- Read the current value before changing it.
- Verify the effect on the public page, status page, or related administration page.

## Page Architecture

Configuration pages usually contain persistent settings. A visible form writes values into YaCy configuration, while the backend may reload subsystems such as language files, network listeners, cache handling, or search presentation.

## Correct Use

Read the current value before changing it. Configuration changes often persist beyond the current request and may affect later crawling, search, network contact, authentication, or resource use. Change one operational idea at a time and verify the result.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

## Automation And API

Page backend: `source/net/yacy/htroot/ConfigAccountList_p.java`.

No request parameters are needed for normal use of this page.

## What To Expect

A successful change is visible as a saved value, a confirmation, or changed behavior on a related page. Some settings take effect immediately; others require reconnecting, reloading translations, restarting services, or watching the status page.

## Related Pages

- `ConfigUser_p.html`
