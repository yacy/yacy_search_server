// Generic priority lanes for focused crawl policies.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

/** Priority assigned by a focused crawl policy. */
public enum PolicyPriority {
    FOCUSED,
    PROBABLE,
    EXPLORATION,
    ORDINARY;

    public int rank() {
        switch (this) {
            case FOCUSED: return 3;
            case PROBABLE: return 2;
            case EXPLORATION: return 1;
            default: return 0;
        }
    }
}
