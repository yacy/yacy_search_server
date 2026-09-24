# Focused Autocrawler Profiles

Focused Autocrawler Profiles let an operator maintain a deep, subject-oriented
local search corpus with YaCy's ordinary crawler. A profile supplies deterministic
rules and roots; YaCy still performs the fetch, robots check, host balancing,
deduplication, link extraction, parsing, and indexing. The feature is disabled
unless at least one profile has `"enabled": true`.

## Configuration

Built-in examples are read from `defaults/focused/policies/*.json`. Local
overrides are read from `DATA/SETTINGS/focused/policies/*.json` and replace a
built-in profile with the same `id`. The Canada file is an example and is
disabled by default. Its country, topic, institution, collection, and recrawl
behaviour is data, not Java logic.

Each profile has an ID, version, description, seeds, trusted hosts and TLDs,
exclusions, scoring rules, collections, thresholds, exploration percentage,
queue watermarks, storage guard, crawl depth, host budget, and recrawl policy.
Multiple enabled profiles are evaluated and scheduled independently.

Scoring and collection rules accept `minimumTermMatches` when a single generic
word should not be enough to classify a page. A profile may set
`limits.refreshSeeds` to refresh configured roots through the native stacker
even when those roots are already present in the local index; this is useful
when starting a focused profile after a prior general crawl. It does not purge
the existing index or create a second downloader.

The generic queue defaults are in `defaults/yacy.init`:

```text
focused.autocrawler.paused = false
focused.autocrawler.laneWeight = 6
focused.autocrawler.ordinaryWeight = 4
focused.autocrawler.controlInterval = 300000
```

Profile-specific `limits.queueTarget`, `limits.refillBelow`, and
`limits.hardMaximum` control each profile. No queue size is hard-coded for a
particular subject. A profile must satisfy `refillBelow <= queueTarget <=
hardMaximum`; invalid watermark combinations are rejected during validation
and reload rather than silently changed.

## Policy hooks

`FocusedCrawlPolicy.preFetch()` receives a `CrawlPolicyContext` containing the
URL, host, parent URL and score, anchor text, profile, and depth. It returns a
`CrawlPolicyDecision` with `ACCEPT`, `REJECT`, or `DEFER`, score, lane, depth,
host budget, recrawl interval, collection IDs, and explainable reason codes.

`FocusedCrawlPolicy.postFetch()` receives the same context plus parsed title,
metadata, language, text, links, and source type. It runs after the normal
fetch/parser path, so it cannot cause a second fetch. The rule implementation
persists parent signals and uses stable hashing for the configured exploration
percentage.

The indexed fields are:

```text
focused_policy_sxt
focused_profile_version_sxt
focused_relevance_i
focused_priority_s
focused_reason_sxt
```

YaCy's regular collection operator remains the simplest way to search a
profile's assigned corpus:

```text
collection:canada-pulse Owen Sound
```

Focused policy metadata also has generic query modifiers:

```text
policy:canada relevance:35 Owen Sound
```

`policy:<id>` filters by focused policy, `relevance:<n>` keeps documents with
at least that policy relevance score, and `/relevance` sorts local results by
the stored focused relevance score. The ordinary YaCy search ranking remains
unchanged unless one of these modifiers is requested. This keeps profiles
additive and preserves existing Freeworld behaviour.

## Scheduler and administration

`FocusedCrawlScheduler` runs as YaCy job `56_focusedautocrawl`. It has a
crash-safe single-instance lock at
`DATA/SETTINGS/focused/state/scheduler.lock`, per-profile properties, and a
JSON-lines control report. It reloads state after restart, checks resource and
storage guards before seeding, and submits roots through `CrawlStacker` rather
than introducing a second downloader. Native links discovered from those roots
continue through the same focused policy hooks.

### Persistent frontier and safe queue reset

A profile can opt into a native-queue recovery frontier:

```json
"frontier": {
  "enabled": true,
  "seedMode": "initial-only",
  "recoveryGraceSeconds": 600,
  "maxEntries": 1000000,
  "terminalRetentionDays": 30
}
```

The sidecar is stored at
`DATA/SETTINGS/focused/state/<profile-id>.frontier.heap`. It records the URL,
native request fields, policy score, lane, collections, reasons, and lifecycle
state. It is only a recovery record: YaCy's ordinary queue remains authoritative
for fetching, robots handling, host politeness, parsing, and indexing.

`seedMode` controls how configured roots interact with the persisted frontier:

- `initial-only` submits roots only when the frontier has never started;
- `exhausted` permits roots again only when no recoverable frontier URL exists;
- `disabled` never submits configured roots.

Use `initial-only` for a profile whose general crawl has already covered its
roots. For example, the Canada profile uses `initial-only` and
`limits.refreshSeeds: false`, so a restart or queue drain does not restart the
broad seed crawl. New links discovered by YaCy are added to the frontier and
are eligible for recovery after a process restart.

The authenticated profile page also provides **Snapshot and reset frontier**.
It snapshots that profile's pending native requests, removes only that
profile's queued rows, and lets the scheduler refill from the snapshot. It does
not delete indexed documents, other profile queues, ordinary queues, or
configuration. Active worker requests are allowed to finish normally. The same
operation is exposed through the `resetFrontier` POST action on
`FocusedProfiles_p.html`.

Terminal frontier rows are compacted according to `terminalRetentionDays` and
trimmed to `maxEntries`. Frontier status is included in the authenticated JSON
and XML profile status responses.

The administration page is:

```text
/FocusedProfiles_p.html
```

It supports authenticated reload, pause/resume, profile import/export,
validation, and enable/disable. Machine-readable status is available at:

```text
/FocusedProfiles_p.json
/FocusedProfiles_p.xml
```

The JSON and XML endpoints are read-only status views. Mutating operations use
the authenticated administration POST endpoint. For example, an operator can
validate and then save an exported profile with:

```sh
curl -u admin:password --data-urlencode 'configuration@profile.json' \
  -d validate=Validate https://peer.example/FocusedProfiles_p.html
curl -u admin:password --data-urlencode 'configuration@profile.json' \
  -d saveProfile='Validate, save and reload' https://peer.example/FocusedProfiles_p.html
```

The same endpoint accepts `reload`, `pause`, `resume`, `setEnabled`, and
`resetFrontier` actions. Profile saves create a timestamped backup before the
atomic replacement; reload never rebuilds the index.

Before an imported profile replaces a local override, the UI creates a
timestamped copy under `DATA/SETTINGS/focused/backups/` and writes the new file
with an atomic rename.

The focused queue uses the persisted `CrawlerCanadianStacks` path as a
backward-compatible storage location for already-created queues. The Java
implementation treats it as the generic focused lane; the historical path is
not changed so an upgrade does not discard pending requests. The old
`NoticedURL.StackType.CANADIAN` and `CrawlQueues.canadianCrawlJobSize()` names
are deprecated compatibility aliases only; new integrations should use the
generic focused names.

## Review boundaries

The implementation is arranged as independently reviewable changes:

1. Generic policy contracts and JSON configuration.
2. Pre-fetch and post-fetch hooks.
3. Persisted policy metadata and index fields.
4. Weighted focused/ordinary lane scheduling.
5. Persistent focused scheduler.
6. Administration endpoints, documentation, and neutral tests.
7. Disabled Canada example policy.

The focused regression suite can be run independently of unrelated legacy
tests with:

```sh
ant focused-test
```

Relevance feedback, learned weights, and cross-peer focused routing are left as
future interface extensions. The first implementation is deterministic and
rule-based so a profile can be exported, reviewed, shared, and reproduced.

## Core indexing safety dependency

Focused Autocrawler Profiles reuse YaCy's existing local index and do not own
or replace Solr's writer. During continuous indexing, a separate core race can
still stop all subsequent indexing: remote-search cleanup may interrupt a
worker while it commits fetched peer metadata to the local Solr index. Lucene
can close its shared `IndexWriter` when that commit is interrupted, after which
crawl indexing fails with `IndexWriter is closed` even while queued crawl work
remains.

The accompanying core fix serializes that metadata commit with search-thread
cancellation. Cancellation is delayed only for the duration of the commit;
pending cancellation is checked before entering it. This is generic YaCy index
writer protection, not focused-policy or Canada-specific behavior. It does not
purge or rebuild the index, alter crawl queues, or change ordinary crawler
policy. The fix is kept in its own commit so it can be reviewed, cherry-picked,
or reverted independently from the focused-crawler feature. After a live writer
has already been closed by this failure, one controlled YaCy restart is needed
to reopen it; the existing index and queued URLs remain on their persistent
storage.
