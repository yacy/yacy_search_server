// Generic focused crawl policy extension point.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

/** Policy hooks called before a URL is queued and after YaCy parses it. */
public interface FocusedCrawlPolicy {
    PolicyConfiguration configuration();

    /** Score a URL before fetching it. */
    CrawlPolicyDecision preFetch(CrawlPolicyContext context);

    /** Classify parsed content without fetching the URL again. */
    CrawlPolicyDecision postFetch(CrawlPolicyContext context);

    /** Record that YaCy accepted the URL into a native crawl queue. */
    default void recordAdmission(final CrawlPolicyContext context, final CrawlPolicyDecision decision) {
        // Optional hook for stateful policies.
    }
}
