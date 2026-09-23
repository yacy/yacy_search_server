# Pull request description: Focused Autocrawler Profiles

## Summary

YaCy already provides crawl profiles, sitemap and sitelist crawling, URL and
domain filters, collection tags, and recrawl settings.

This pull request adds a reusable focused-crawl policy layer that combines
those primitives into a persistent, explainable crawling strategy. A policy can
score discovered URLs, assign collections, select priority lanes, apply
geographic or topical rules, and schedule recrawls. Multiple independent
profiles can run concurrently while ordinary YaCy crawling and Freeworld
operation remain compatible.

The persistent frontier records native queue work so a focused profile can
resume after a restart or an explicit profile-scoped queue reset without
replaying completed seed roots. This is a recovery layer around YaCy's native
queue, not a second downloader.

The feature is named **Focused Autocrawler Profiles**. It is disabled unless an
operator enables a profile. The Canadian configuration is the first example
profile and demonstrates the framework through data. The core implementation
contains no Canada-specific Java logic and is intended for technical, legal,
academic, institutional, regional, historical, and other focused indexes.

## Related issues

Use `Related to #N` while the scope is being reviewed. This pull request does
not use closing keywords for issues whose underlying operational or
HostBalancer defects are outside this feature's scope.

### Related issue coverage

| Issue | How this pull request relates |
|---|---|
| Related to [#548](https://github.com/yacy/yacy_search_server/issues/548) | Makes autocrawler behaviour explicit through named, validated, documented policy profiles. |
| Related to [#96](https://github.com/yacy/yacy_search_server/issues/96) | Provides a practical mechanism for building large intentional corpora with controlled frontier growth and recrawling. |
| Related to [#161](https://github.com/yacy/yacy_search_server/issues/161) | Generalizes geographic restrictions into configurable country, TLD, host, language, and place-name rules. |
| Related to [#745](https://github.com/yacy/yacy_search_server/issues/745) | Adds policy-level recrawl schedules and freshness targets for focused collections. |
| Related to [#753](https://github.com/yacy/yacy_search_server/issues/753) | Adds policy-aware priority and host-scheduling hooks that provide integration points for queue fairness and multi-domain crawl allocation. |

Additional related work:

- `Related to [#364](https://github.com/yacy/yacy_search_server/issues/364)` — the documented UI and API extend the existing crawl-queue workflow for creating and monitoring policy work.
- `Related to [#638](https://github.com/yacy/yacy_search_server/issues/638)` — policy decisions carry per-host budgets and recrawl intervals while preserving YaCy's host politeness controls.
- `Related to [#799](https://github.com/yacy/yacy_search_server/issues/799)` — profile queue budgets and weighted lanes expose controlled ways to make selected crawling work harder without changing ordinary crawl defaults.

## How the feature consolidates existing requests

- Policy profiles and validation clarify and extend autocrawler configuration.
- Priority lanes, profile quotas, and controlled recrawling provide a practical
  foundation for large focused corpora.
- Geographic, domain, language, and trusted-host rules generalize country
  restrictions.
- Policy-owned recrawl schedules improve freshness management.
- Host-aware scheduling and priority decisions provide integration points for
  queue fairness and multi-domain crawling.
- A documented API/UI for creating and monitoring policies extends the existing
  crawl-queue workflow.

The large-scale crawling issue is especially relevant because it describes the
need for very large site-oriented indexes, while this pull request supplies a
general mechanism for deliberately growing and maintaining such indexes.

## Scope and operational limits

The implementation preserves robots handling, host balancing, ordinary crawl
profiles, queue persistence, and existing network behaviour when no focused
profile is enabled. It does not claim to fix underlying DNS, memory, or
HostBalancer defects; those are integration points and operational concerns
that can be addressed independently.

Relevance feedback, automatic weight learning, and cross-peer focused-search
routing remain future extensions. The initial policy engine is deterministic
and rule-based so profiles can be validated, exported, shared, and reproduced.
