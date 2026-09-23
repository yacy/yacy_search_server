// Generic novelty classification for focused crawl policies.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

/**
 * Describes why a focused URL is worth admitting.  The values are generic so
 * the same queue policy can be used for regional, topical, legal or technical
 * profiles.
 */
public enum NoveltyClass {
    NEW_HOST,
    NEW_PATH,
    PROVEN_EXPANSION,
    FRESHNESS,
    SATURATED;

    /**
     * Every novelty class is eligible for a focused lane. SATURATED URLs use
     * the low-priority expansion lane rather than being diverted to ordinary
     * crawling.
     */
    public boolean focused() {
        return true;
    }
}
