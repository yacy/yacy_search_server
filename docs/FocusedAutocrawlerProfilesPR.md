# Focused Autocrawler Profiles

## Summary

YaCy already provides crawl profiles, sitemap and sitelist crawling, URL and
domain filters, collection tags, and recrawl settings.

This pull request adds a reusable focused-crawl policy layer that combines
those primitives into a persistent, explainable crawling strategy. A policy
can score discovered URLs, assign collections, select priority lanes, apply
geographic or topical rules, and schedule recrawls. Multiple independent
profiles can run concurrently while ordinary YaCy crawling and Freeworld
operation remain compatible.

The persistent frontier records native queue work so a focused profile can
resume after a restart or an explicit profile-scoped queue reset without
replaying completed seed roots. This is a recovery layer around YaCy's native
queue, not a second downloader.

Frontier recovery, queue-candidate reads, status snapshots, and compaction do
not hold the shared frontier-store monitor across full heap scans. Stale
in-flight rows are rechecked before transition and recovered in bounded
batches, so maintenance work does not serialize crawler admission and indexing
callbacks behind a large persistent frontier.

The branch also carries one independently reviewable core-safety commit
(`62302c0`, **Protect Solr commits from search cancellation**). It guards the
local Solr metadata commit performed by remote search against concurrent
search-thread cancellation. Lucene can close its shared `IndexWriter` when the
commit is interrupted; later crawler indexing then fails even though queued
work remains. This fix is generic, does not add focused-crawl policy, and can
be cherry-picked or reverted separately from the focused-crawler commits.

The feature is named **Focused Autocrawler Profiles**. It is disabled unless an
operator enables a profile. The Canadian configuration is the first example
profile and demonstrates the framework through data. The core implementation
contains no Canada-specific Java logic and is intended for technical, legal,
academic, institutional, regional, historical, and other focused indexes.

## Related issues

These are related issues, not closing claims. The pull request does not claim
to fix underlying DNS, memory, or HostBalancer defects outside its scope.

- [#548](https://github.com/yacy/yacy_search_server/issues/548) — Makes
  autocrawler behaviour explicit through named, validated, documented policy
  profiles.
- [#96](https://github.com/yacy/yacy_search_server/issues/96) — Provides a
  practical mechanism for building large intentional corpora with controlled
  frontier growth and recrawling.
- [#161](https://github.com/yacy/yacy_search_server/issues/161) — Generalizes
  geographic restrictions into configurable country, TLD, host, language, and
  place-name rules.
- [#745](https://github.com/yacy/yacy_search_server/issues/745) — Adds
  policy-level recrawl schedules and freshness targets for focused collections.
- [#753](https://github.com/yacy/yacy_search_server/issues/753) — Adds
  policy-aware priority and host-scheduling integration points for queue
  fairness and multi-domain crawl allocation.
- [#364](https://github.com/yacy/yacy_search_server/issues/364) — Extends the
  existing crawl-queue workflow with documented policy creation and monitoring
  through the UI and API.
- [#638](https://github.com/yacy/yacy_search_server/issues/638) — Carries
  per-host budgets and recrawl intervals while preserving YaCy's host politeness
  controls.
- [#799](https://github.com/yacy/yacy_search_server/issues/799) — Exposes
  profile queue budgets and weighted lanes for prioritizing selected crawling
  work without changing ordinary crawl defaults.

## How the feature consolidates existing requests

- Policy profiles and validation clarify and extend autocrawler configuration.
- Priority lanes, profile quotas, and controlled recrawling provide a practical
  foundation for large focused corpora.
- Geographic, domain, language, and trusted-host rules generalize country
  restrictions.
- Policy-owned recrawl schedules improve freshness management.
- Host-aware scheduling and priority decisions provide integration points for
  queue fairness and multi-domain crawling.
- A documented API and UI for creating and monitoring policies extend the
  existing crawl-queue workflow.

The large-scale crawling issue is especially relevant because it describes the
need for very large site-oriented indexes, while this pull request supplies a
general mechanism for deliberately growing and maintaining such indexes.

## Scope and operational limits

The implementation preserves robots handling, host balancing, ordinary crawl
profiles, queue persistence, and existing network behaviour when no focused
profile is enabled. The separate Solr-commit guard addresses only the
interruption race described above; it does not claim to fix general memory,
DNS, or HostBalancer defects. Those remain independent integration points and
operational concerns.

Relevance feedback, automatic weight learning, and cross-peer focused-search
routing remain future extensions. The initial policy engine is deterministic
and rule-based so profiles can be validated, exported, shared, and reproduced.

## Observed production crawl activity

The persisted scheduler log spans 2026-09-21 23:28 UTC through 2026-10-20
11:44 UTC (about 28.5 calendar days). Resource-guard pauses occurred during
this period, so this is not an uninterrupted-uptime or soak claim. The final
logged scheduler reason was `frontier exhausted`.

The focused metrics snapshot saved on 2026-10-20 reports:

- **4,153,092 successful native queue admissions.** This counter increments
  only after YaCy's native `NoticedURL` queue accepts a focused-policy URL. It
  counts admission events, not unique URLs or successful document writes; a URL
  may be admitted again after retry or recrawl.
- **3,944,275 first-seen frontier discovery events**, recorded by the metrics
  as native-link discoveries. The scheduler separately logged 2,145 seed-refill
  handoffs; these are different counters. Frontier compaction also means the
  cumulative discovery count is not a current unique-URL census.
- **24,728,049 scheduler handoff attempts** to YaCy's stacker over the logged
  period: 22,442,800 from frontier recovery plus indexed-source expansion,
  1,023,733 from frontier recovery, 1,259,371 during bounded refills that then
  encountered the resource guard, and 2,145 seed refills. These are scheduling
  attempts, not confirmations of native queue admission, and may include
  repeated URLs.
- **788,937 post-fetch policy decisions counted in the `indexed` metric**
  (779,945 `newIndexed`, 8,992 `recrawledIndexed`). This narrower counter is
  recorded after fetch and parsing when the policy returns a non-reject
  decision, before the subsequent Solr `storeDocument` call. It is therefore
  not a count of all URLs queued or fetched, and not a verified Solr-commit
  total. Its smaller value does not mean that only this many pages entered the
  focused queue.

A separate read-only source-index audit on 2026-10-21 found **7,678,957 live
documents**, including **3,126,774 documents on `.ca` hosts** (about 40.7% of
the local index). The persisted focused-scheduler log covers about 28.5
calendar days, from 2026-09-21 through 2026-10-20, with intermittent
resource-guard pauses.

The retained data does not include a reliable total-index document-count
snapshot from the beginning of that period, so an exact net index-growth
figure over those 28.5 days cannot be verified. The 788,937 post-fetch policy
counter above is not a substitute: it is recorded before the Solr store and
does not establish unique committed documents. YaCy also did not persist
reliable crawl-origin attribution for every indexed document, so neither the
full index nor all `.ca`-host documents are attributed to this profile.

## Validation

- `ant focused-test` passed: 49 focused tests.
- `ant solr-interrupt-test` passed: 2 interruption-guard tests.
- The full Ant/JUnit run passed: 405 tests, 0 failures, 0 errors, 1 skipped.
  The test-only Solr 9.10.1 dependencies use an isolated classpath, keeping
  Solr's Jetty 10 jars separate from YaCy's Jetty 12 tests and production
  runtime. The build/test fix is in commit `cc07b90`.
- The Docker builder compiled the fork successfully.
- A one-shot deployment smoke check returned HTTP 200 and found no threads
  waiting on the focused frontier-store monitor. The 28.5-day run above had
  intermittent resource-guard pauses; no uninterrupted 12–24-hour soak is
  claimed.
