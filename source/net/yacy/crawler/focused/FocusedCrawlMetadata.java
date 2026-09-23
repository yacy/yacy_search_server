// Persisted metadata attached to a focused crawl URL.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Policy metadata kept outside the legacy queue row for compatibility. */
public final class FocusedCrawlMetadata {
    private final List<String> policyIds;
    private final List<String> policyVersions;
    private final int relevanceScore;
    private final PolicyPriority priority;
    private final Set<String> collections;
    private final List<String> reasonCodes;
    private final int maximumDepth;
    private final int perHostBudget;
    private final long recrawlIntervalMillis;
    private final NoveltyClass noveltyClass;

    public FocusedCrawlMetadata(final List<String> policyIds, final List<String> policyVersions, final int relevanceScore,
            final PolicyPriority priority, final Set<String> collections, final List<String> reasonCodes,
            final int maximumDepth, final int perHostBudget, final long recrawlIntervalMillis) {
        this(policyIds, policyVersions, relevanceScore, priority, collections, reasonCodes,
                maximumDepth, perHostBudget, recrawlIntervalMillis, NoveltyClass.SATURATED);
    }

    public FocusedCrawlMetadata(final List<String> policyIds, final List<String> policyVersions, final int relevanceScore,
            final PolicyPriority priority, final Set<String> collections, final List<String> reasonCodes,
            final int maximumDepth, final int perHostBudget, final long recrawlIntervalMillis,
            final NoveltyClass noveltyClass) {
        this.policyIds = Collections.unmodifiableList(new ArrayList<>(policyIds == null ? Collections.emptyList() : policyIds));
        this.policyVersions = Collections.unmodifiableList(new ArrayList<>(policyVersions == null ? Collections.emptyList() : policyVersions));
        this.relevanceScore = relevanceScore;
        this.priority = priority == null ? PolicyPriority.ORDINARY : priority;
        this.collections = Collections.unmodifiableSet(new LinkedHashSet<>(collections == null ? Collections.emptySet() : collections));
        this.reasonCodes = Collections.unmodifiableList(new ArrayList<>(reasonCodes == null ? Collections.emptyList() : reasonCodes));
        this.maximumDepth = Math.max(0, maximumDepth);
        this.perHostBudget = Math.max(0, perHostBudget);
        this.recrawlIntervalMillis = Math.max(0L, recrawlIntervalMillis);
        this.noveltyClass = noveltyClass == null ? NoveltyClass.SATURATED : noveltyClass;
    }

    public static FocusedCrawlMetadata from(final List<CrawlPolicyDecision> decisions) {
        final List<String> ids = new ArrayList<>();
        final List<String> versions = new ArrayList<>();
        final Set<String> collections = new LinkedHashSet<>();
        final List<String> reasons = new ArrayList<>();
        CrawlPolicyDecision best = null;
        for (final CrawlPolicyDecision decision : decisions == null ? Collections.<CrawlPolicyDecision>emptyList() : decisions) {
            if (decision.policyId().isEmpty()) continue;
            if (decision.relevanceScore() <= 0 && decision.collections().isEmpty()
                    && decision.priority() == PolicyPriority.ORDINARY) continue;
            ids.add(decision.policyId());
            versions.add(decision.policyVersion());
            collections.addAll(decision.collections());
            reasons.addAll(decision.reasonCodes());
            if (best == null || decision.relevanceScore() > best.relevanceScore()
                    || decision.priority().rank() > best.priority().rank()) best = decision;
        }
        if (best == null) return null;
        return new FocusedCrawlMetadata(ids, versions, best.relevanceScore(), best.priority(), collections, reasons,
                best.maximumDepth(), best.perHostBudget(), best.recrawlIntervalMillis(), best.noveltyClass());
    }

    public List<String> policyIds() { return this.policyIds; }
    public List<String> policyVersions() { return this.policyVersions; }
    public int relevanceScore() { return this.relevanceScore; }
    public PolicyPriority priority() { return this.priority; }
    public Set<String> collections() { return this.collections; }
    public List<String> reasonCodes() { return this.reasonCodes; }
    public int maximumDepth() { return this.maximumDepth; }
    public int perHostBudget() { return this.perHostBudget; }
    public long recrawlIntervalMillis() { return this.recrawlIntervalMillis; }
    public NoveltyClass noveltyClass() { return this.noveltyClass; }
}
