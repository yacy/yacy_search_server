// Generic focused crawl policy result.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Explainable result returned by either focused-policy hook. */
public final class CrawlPolicyDecision {

    public enum Action { ACCEPT, REJECT, DEFER }

    private final String policyId;
    private final String policyVersion;
    private final Action action;
    private final int relevanceScore;
    private final PolicyPriority priority;
    private final Set<String> collections;
    private final int maximumDepth;
    private final int perHostBudget;
    private final boolean recrawlFocused;
    private final long recrawlIntervalMillis;
    private final List<String> reasonCodes;
    private final NoveltyClass noveltyClass;

    public CrawlPolicyDecision(final String policyId, final String policyVersion, final Action action,
            final int relevanceScore, final PolicyPriority priority, final Set<String> collections,
            final int maximumDepth, final int perHostBudget, final boolean recrawlFocused,
            final long recrawlIntervalMillis,
            final List<String> reasonCodes, final NoveltyClass noveltyClass) {
        this.policyId = policyId == null ? "" : policyId;
        this.policyVersion = policyVersion == null ? "" : policyVersion;
        this.action = action == null ? Action.DEFER : action;
        this.relevanceScore = relevanceScore;
        this.priority = priority == null ? PolicyPriority.ORDINARY : priority;
        this.collections = Collections.unmodifiableSet(new LinkedHashSet<>(collections == null ? Collections.emptySet() : collections));
        this.maximumDepth = Math.max(0, maximumDepth);
        this.perHostBudget = Math.max(0, perHostBudget);
        this.recrawlFocused = recrawlFocused;
        this.recrawlIntervalMillis = Math.max(0L, recrawlIntervalMillis);
        this.reasonCodes = Collections.unmodifiableList(new ArrayList<>(reasonCodes == null ? Collections.emptyList() : reasonCodes));
        this.noveltyClass = noveltyClass == null ? NoveltyClass.SATURATED : noveltyClass;
    }

    /** Backward-compatible constructor for policies that do not opt into focused refreshes. */
    public CrawlPolicyDecision(final String policyId, final String policyVersion, final Action action,
            final int relevanceScore, final PolicyPriority priority, final Set<String> collections,
            final int maximumDepth, final int perHostBudget, final long recrawlIntervalMillis,
            final List<String> reasonCodes) {
        this(policyId, policyVersion, action, relevanceScore, priority, collections, maximumDepth,
                perHostBudget, false, recrawlIntervalMillis, reasonCodes, NoveltyClass.SATURATED);
    }

    /** Backward-compatible constructor for focused policies with refresh control. */
    public CrawlPolicyDecision(final String policyId, final String policyVersion, final Action action,
            final int relevanceScore, final PolicyPriority priority, final Set<String> collections,
            final int maximumDepth, final int perHostBudget, final boolean recrawlFocused,
            final long recrawlIntervalMillis, final List<String> reasonCodes) {
        this(policyId, policyVersion, action, relevanceScore, priority, collections, maximumDepth,
                perHostBudget, recrawlFocused, recrawlIntervalMillis, reasonCodes, NoveltyClass.SATURATED);
    }

    public static CrawlPolicyDecision ordinary(final String reason) {
        return new CrawlPolicyDecision("", "", Action.ACCEPT, 0, PolicyPriority.ORDINARY,
                Collections.emptySet(), Integer.MAX_VALUE, 0, false, 0L,
                reason == null ? Collections.emptyList() : Collections.singletonList(reason), NoveltyClass.SATURATED);
    }

    public String policyId() { return this.policyId; }
    public String policyVersion() { return this.policyVersion; }
    public Action action() { return this.action; }
    public int relevanceScore() { return this.relevanceScore; }
    public PolicyPriority priority() { return this.priority; }
    public Set<String> collections() { return this.collections; }
    public int maximumDepth() { return this.maximumDepth; }
    public int perHostBudget() { return this.perHostBudget; }
    public boolean recrawlFocused() { return this.recrawlFocused; }
    public long recrawlIntervalMillis() { return this.recrawlIntervalMillis; }
    public List<String> reasonCodes() { return this.reasonCodes; }
    public NoveltyClass noveltyClass() { return this.noveltyClass; }

    public boolean focused() {
        return !this.policyId.isEmpty() && this.priority != PolicyPriority.ORDINARY
                && this.action != Action.REJECT;
    }
}
