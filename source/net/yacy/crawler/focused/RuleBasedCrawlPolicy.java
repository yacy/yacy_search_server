// Deterministic JSON-driven focused crawl policy.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.yacy.cora.document.id.DigestURL;

/** Rule-based implementation suitable for shareable profile configuration. */
public final class RuleBasedCrawlPolicy implements FocusedCrawlPolicy {

    private final PolicyConfiguration configuration;
    private final FocusedNoveltyState noveltyState;
    private final FocusedURLAdmissionState admissionState;

    public RuleBasedCrawlPolicy(final PolicyConfiguration configuration, final File stateDirectory) {
        this.configuration = configuration;
        this.noveltyState = stateDirectory == null ? null : new FocusedNoveltyState(stateDirectory, configuration.id());
        this.admissionState = stateDirectory == null ? null : new FocusedURLAdmissionState(stateDirectory, configuration.id());
    }

    @Override
    public PolicyConfiguration configuration() { return this.configuration; }

    @Override
    public CrawlPolicyDecision preFetch(final CrawlPolicyContext context) {
        if (!this.configuration.enabled() || context == null || context.url() == null) return CrawlPolicyDecision.ordinary("policy-disabled");
        return decide(context, false);
    }

    @Override
    public CrawlPolicyDecision postFetch(final CrawlPolicyContext context) {
        if (!this.configuration.enabled() || context == null || context.url() == null) return CrawlPolicyDecision.ordinary("policy-disabled");
        return decide(context, true);
    }

    @Override
    public void recordAdmission(final CrawlPolicyContext context, final CrawlPolicyDecision decision) {
        if (this.admissionState == null || context == null || decision == null
                || !this.configuration.id().equals(decision.policyId())
                || decision.action() == CrawlPolicyDecision.Action.REJECT || !decision.focused()) return;
        this.admissionState.recordAdmission(context.url(), this.configuration.version());
    }

    private CrawlPolicyDecision decide(final CrawlPolicyContext context, final boolean parsed) {
        final String host = context.host().toLowerCase(Locale.ROOT);
        final String url = normalize(context.url().toNormalform(true));
        final StringBuilder primaryEvidence = new StringBuilder(url).append(' ').append(normalize(context.anchorText()));
        final StringBuilder linkEvidence = new StringBuilder();
        if (parsed && context.parsedContent() != null) {
            final CrawlPolicyContext.ParsedContent content = context.parsedContent();
            primaryEvidence.append(' ').append(normalize(content.title())).append(' ').append(normalize(content.language()));
            primaryEvidence.append(' ').append(normalize(content.sourceType())).append(' ').append(normalize(content.text()));
            for (final String value : content.metadata().values()) primaryEvidence.append(' ').append(normalize(value));
            for (final String value : content.links().values()) linkEvidence.append(' ').append(normalize(value));
        }
        final String primaryText = primaryEvidence.toString();
        final String text = primaryText + linkEvidence;
        final List<String> reasons = new ArrayList<>();
        for (final String excluded : this.configuration.excludedHosts()) {
            if (hostMatches(host, excluded)) return decision(CrawlPolicyDecision.Action.REJECT, -100,
                    PolicyPriority.ORDINARY, Collections.emptySet(), 0, reasons, "excluded-host", NoveltyClass.SATURATED);
        }

        int score = 0;
        int directScore = 0;
        final boolean trusted = this.configuration.trustedHosts().stream().anyMatch(value -> hostMatches(host, value));
        if (trusted) { score += 30; directScore += 30; reasons.add("trusted-host"); }
        final boolean trustedTld = this.configuration.tlds().stream().anyMatch(value -> host.endsWith("." + value) || host.equals(value));
        if (trustedTld) {
            score += 20;
            directScore += 20;
            reasons.add("trusted-tld");
        }
        if (context.parentScore() > 0) {
            score += Math.min(20, context.parentScore() / this.configuration.parentInheritanceDivisor());
            reasons.add("parent-score");
        }
        for (final PolicyConfiguration.ScoringRule rule : this.configuration.scoringRules()) {
            if (matches(rule, host, text)) {
                score += rule.score();
                directScore += rule.score();
                reasons.add(rule.id());
            }
        }
        final boolean external = !trusted && !trustedTld;
        final boolean directEvidence = directScore >= this.configuration.probableThreshold();
        final boolean explorationEligible = this.configuration.controlledExploration()
                // A probable parent is sufficient to justify a sampled
                // low-priority expansion. Requiring a fully focused parent
                // starves the frontier when trusted or topical parents score
                // between the probable and focused thresholds.
                && context.parentScore() >= this.configuration.probableThreshold()
                && this.configuration.limits().explorationPercent() > 0
                && explorationSlot(url, this.configuration.id(), this.configuration.limits().explorationPercent());
        if (this.configuration.novelty().enabled() && external
                && this.configuration.novelty().externalRequiresDirectEvidence() && !directEvidence
                && !explorationEligible) {
            reasons.add("external-no-direct-evidence");
            return decision(CrawlPolicyDecision.Action.DEFER, score, PolicyPriority.ORDINARY,
                    Collections.emptySet(), 0L, reasons, null, NoveltyClass.SATURATED);
        }
        // Do not assign a focused collection to a weak incidental match. A
        // profile may still return a relevance score for ranking, but the
        // collection is reserved for a candidate that clears the profile's
        // probable threshold.
        Set<String> collections = score >= this.configuration.probableThreshold()
                ? collectionsFor(primaryText, host, context.parsedContent()) : Collections.emptySet();
        if (!collections.isEmpty()) reasons.add("collection-assignment");
        final long recrawlInterval = recrawlInterval(collections);

        PolicyPriority priority;
        CrawlPolicyDecision.Action action;
        if (score >= this.configuration.focusedThreshold()) {
            priority = PolicyPriority.FOCUSED;
            action = CrawlPolicyDecision.Action.ACCEPT;
        } else if (score >= this.configuration.probableThreshold()) {
            priority = PolicyPriority.PROBABLE;
            action = CrawlPolicyDecision.Action.ACCEPT;
        } else if (explorationEligible) {
            priority = PolicyPriority.EXPLORATION;
            action = CrawlPolicyDecision.Action.DEFER;
            reasons.add("exploration-budget");
            if (external && !directEvidence) reasons.add("external-exploration");
            if (collections.isEmpty() && !this.configuration.novelty().explorationCollection().isEmpty()) {
                collections = new LinkedHashSet<>();
                collections.add(this.configuration.novelty().explorationCollection());
                reasons.add("exploration-collection");
            }
        } else {
            priority = PolicyPriority.ORDINARY;
            action = CrawlPolicyDecision.Action.DEFER;
            reasons.add("below-threshold");
        }

        // Many pages can discover the same focused URL. Once YaCy has
        // accepted it into a native queue, suppress repeated admissions until
        // ordinary or focused recrawl rules make it eligible again.
        if (!parsed && action != CrawlPolicyDecision.Action.REJECT
                && score >= this.configuration.probableThreshold()
                && this.configuration.novelty().enabled() && this.admissionState != null
                && !context.frontierRecovery()
                && this.admissionState.shouldSuppress(context.url().hash(), this.configuration.version(), context,
                        recrawlInterval, this.configuration.limits().recrawlFocused(),
                        this.configuration.novelty().repeatSuppressionMillis())) {
            reasons.add("focused-admission-duplicate");
            return decision(CrawlPolicyDecision.Action.REJECT, score, priority, collections, recrawlInterval,
                    reasons, null, NoveltyClass.SATURATED);
        }
        NoveltyClass novelty = NoveltyClass.SATURATED;
        if (score >= this.configuration.probableThreshold()) {
            if (this.configuration.novelty().enabled() && !parsed && this.noveltyState != null) {
                final FocusedNoveltyState.Observation observation = this.noveltyState.observe(context.url(),
                        this.configuration.novelty().pathPrefixDepth());
                if (this.configuration.limits().recrawlFocused()
                        && context.knownInIndex() && isRecrawlDue(context, recrawlInterval(collections))) {
                    novelty = NoveltyClass.FRESHNESS;
                } else if (observation.newHost()) {
                    novelty = NoveltyClass.NEW_HOST;
                } else if (observation.newPath()) {
                    novelty = NoveltyClass.NEW_PATH;
                } else if (!context.knownInIndex()) {
                    novelty = NoveltyClass.PROVEN_EXPANSION;
                }
                reasons.add("novelty-" + novelty.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            } else if (parsed) {
                if (this.configuration.limits().recrawlFocused()
                        && context.knownInIndex() && isRecrawlDue(context, recrawlInterval(collections))) {
                    novelty = NoveltyClass.FRESHNESS;
                } else if (!context.knownInIndex()) {
                    novelty = NoveltyClass.PROVEN_EXPANSION;
                }
            }
        }
        if (novelty == NoveltyClass.SATURATED && priority != PolicyPriority.ORDINARY) {
            // A relevant known, not-yet-due URL remains eligible in the
            // focused expansion lane. The lane is weighted below the
            // novelty lanes, so it does not consume new-host/path capacity.
            reasons.add("novelty-saturated");
        }
        return decision(action, score, priority, collections, recrawlInterval, reasons, null, novelty);
    }

    private CrawlPolicyDecision decision(final CrawlPolicyDecision.Action action, final int score,
            final PolicyPriority priority, final Set<String> collections, final long recrawlInterval,
            final List<String> reasons, final String extraReason, final NoveltyClass noveltyClass) {
        final List<String> codes = new ArrayList<>(reasons);
        if (extraReason != null) codes.add(extraReason);
        return new CrawlPolicyDecision(this.configuration.id(), this.configuration.version(), action, score, priority,
                collections, this.configuration.limits().maximumDepth(), this.configuration.limits().perHostBudget(),
                this.configuration.limits().recrawlFocused(),
                recrawlInterval, codes, noveltyClass);
    }

    private static boolean isRecrawlDue(final CrawlPolicyContext context, final long interval) {
        return context != null && context.knownInIndex() && interval > 0L
                && context.indexedAt() <= System.currentTimeMillis() - interval;
    }

    private static boolean matches(final PolicyConfiguration.ScoringRule rule, final String host, final String text) {
        for (final String candidate : rule.hosts()) if (hostMatches(host, candidate)) return true;
        for (final String tld : rule.tlds()) if (host.endsWith("." + tld) || host.equals(tld)) return true;
        if (rule.hosts().isEmpty() && rule.tlds().isEmpty()) {
        int matches = 0;
        for (final String term : rule.terms()) if (text.contains(normalize(term))) matches++;
        return matches >= rule.minimumTermMatches();
        }
        return false;
    }

    private Set<String> collectionsFor(final String text, final String host, final CrawlPolicyContext.ParsedContent parsed) {
        final Set<String> result = new LinkedHashSet<>();
        for (final PolicyConfiguration.CollectionRule rule : this.configuration.collections().values()) {
            int matches = 0;
            for (final String term : rule.terms()) if (text.contains(normalize(term))) matches++;
            boolean match = matches >= rule.minimumTermMatches();
            if (!match && parsed != null) for (final String sourceType : rule.sourceTypes()) {
                if (sourceType.equalsIgnoreCase(parsed.sourceType())) { match = true; break; }
            }
            if (match) result.add(rule.id());
        }
        if (result.isEmpty()) for (final PolicyConfiguration.ScoringRule rule : this.configuration.scoringRules()) {
            if (!rule.collection().isEmpty() && matches(rule, host, text)) result.add(rule.collection());
        }
        return result;
    }

    private long recrawlInterval(final Set<String> collections) {
        for (final String collection : collections) {
            final PolicyConfiguration.CollectionRule rule = this.configuration.collections().get(collection);
            if (rule != null && rule.recrawlIntervalMillis() > 0) return rule.recrawlIntervalMillis();
        }
        return this.configuration.limits().defaultRecrawlIntervalMillis();
    }

    private static String normalize(final String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}0-9]+", " ").trim();
    }

    /** Stable sampling keeps the configured exploration share across restarts. */
    private static boolean explorationSlot(final String url, final String policyId, final int percent) {
        final int bucket = Math.floorMod((url + "\n" + policyId).hashCode(), 100);
        return bucket < Math.max(0, Math.min(100, percent));
    }

    private static boolean hostMatches(final String host, final String candidate) {
        final String normalized = candidate == null ? "" : candidate.toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("/.*$", "");
        return !normalized.isEmpty() && (host.equals(normalized) || host.endsWith("." + normalized));
    }
}
