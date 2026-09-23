// Context passed to focused crawl policies.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.yacy.cora.document.id.DigestURL;

/** Immutable context shared by pre-fetch and post-fetch policy hooks. */
public final class CrawlPolicyContext {

    /** Parsed page attributes supplied to the post-fetch hook. */
    public static final class ParsedContent {
        private final String title;
        private final Map<String, String> metadata;
        private final String language;
        private final String text;
        private final Map<String, String> links;
        private final String sourceType;

        public ParsedContent(final String title, final Map<String, String> metadata, final String language,
                final String text, final Map<String, String> links, final String sourceType) {
            this.title = title == null ? "" : title;
            this.metadata = metadata == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
            this.language = language == null ? "" : language;
            this.text = text == null ? "" : text;
            this.links = links == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(links));
            this.sourceType = sourceType == null ? "" : sourceType;
        }

        public String title() { return this.title; }
        public Map<String, String> metadata() { return this.metadata; }
        public String language() { return this.language; }
        public String text() { return this.text; }
        public Map<String, String> links() { return this.links; }
        public String sourceType() { return this.sourceType; }
    }

    private final DigestURL url;
    private final DigestURL parentURL;
    private final String host;
    private final int parentScore;
    private final String anchorText;
    private final String crawlProfile;
    private final int depth;
    private final ParsedContent parsedContent;
    private final long indexedAt;
    private final boolean profileRecrawlDue;
    private final boolean frontierRecovery;

    public CrawlPolicyContext(final DigestURL url, final DigestURL parentURL, final int parentScore,
            final String anchorText, final String crawlProfile, final int depth, final ParsedContent parsedContent) {
        this(url, parentURL, parentScore, anchorText, crawlProfile, depth, parsedContent, -1L);
    }

    public CrawlPolicyContext(final DigestURL url, final DigestURL parentURL, final int parentScore,
            final String anchorText, final String crawlProfile, final int depth, final ParsedContent parsedContent,
            final long indexedAt) {
        this(url, parentURL, parentScore, anchorText, crawlProfile, depth, parsedContent, indexedAt, false);
    }

    public CrawlPolicyContext(final DigestURL url, final DigestURL parentURL, final int parentScore,
            final String anchorText, final String crawlProfile, final int depth, final ParsedContent parsedContent,
            final long indexedAt, final boolean profileRecrawlDue) {
        this.url = url;
        this.parentURL = parentURL;
        this.host = url == null || url.getHost() == null ? "" : url.getHost().toLowerCase(java.util.Locale.ROOT);
        this.parentScore = parentScore;
        this.anchorText = anchorText == null ? "" : anchorText;
        this.crawlProfile = crawlProfile == null ? "" : crawlProfile;
        this.depth = Math.max(0, depth);
        this.parsedContent = parsedContent;
        this.indexedAt = indexedAt;
        this.profileRecrawlDue = profileRecrawlDue;
        this.frontierRecovery = false;
    }

    public CrawlPolicyContext(final DigestURL url, final DigestURL parentURL, final int parentScore,
            final String anchorText, final String crawlProfile, final int depth, final ParsedContent parsedContent,
            final long indexedAt, final boolean profileRecrawlDue, final boolean frontierRecovery) {
        this.url = url;
        this.parentURL = parentURL;
        this.host = url == null || url.getHost() == null ? "" : url.getHost().toLowerCase(java.util.Locale.ROOT);
        this.parentScore = parentScore;
        this.anchorText = anchorText == null ? "" : anchorText;
        this.crawlProfile = crawlProfile == null ? "" : crawlProfile;
        this.depth = Math.max(0, depth);
        this.parsedContent = parsedContent;
        this.indexedAt = indexedAt;
        this.profileRecrawlDue = profileRecrawlDue;
        this.frontierRecovery = frontierRecovery;
    }

    public DigestURL url() { return this.url; }
    public DigestURL parentURL() { return this.parentURL; }
    public String host() { return this.host; }
    public int parentScore() { return this.parentScore; }
    public String anchorText() { return this.anchorText; }
    public String crawlProfile() { return this.crawlProfile; }
    public int depth() { return this.depth; }
    public ParsedContent parsedContent() { return this.parsedContent; }
    public boolean knownInIndex() { return this.indexedAt >= 0L; }
    public long indexedAt() { return this.indexedAt; }
    public boolean profileRecrawlDue() { return this.profileRecrawlDue; }
    public boolean frontierRecovery() { return this.frontierRecovery; }

    public CrawlPolicyContext withParsedContent(final ParsedContent parsed) {
        return new CrawlPolicyContext(this.url, this.parentURL, this.parentScore, this.anchorText,
                this.crawlProfile, this.depth, parsed, this.indexedAt, this.profileRecrawlDue, this.frontierRecovery);
    }
}
