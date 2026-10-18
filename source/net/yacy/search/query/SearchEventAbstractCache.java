// SearchEventAbstractCache.java
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.search.query;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;

import net.yacy.cora.order.Base64Order;
import net.yacy.kelondro.data.word.WordReferenceFactory;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.data.word.WordReference;

/** Lazily creates and caches only the index abstracts a peer-search response requests. */
final class SearchEventAbstractCache {

    private final boolean enabled;
    private final Supplier<Map<byte[], ReferenceContainer<WordReference>>> containers;
    private final SortedMap<byte[], String> abstracts = new TreeMap<>(Base64Order.enhancedCoder);
    private boolean cleared;

    SearchEventAbstractCache(final boolean enabled,
            final Supplier<Map<byte[], ReferenceContainer<WordReference>>> containers) {
        this.enabled = enabled;
        this.containers = containers;
    }

    String get(final byte[] hash) {
        if (!this.enabled || hash == null) return null;
        synchronized (this.abstracts) {
            if (this.cleared) return null;
            String value = this.abstracts.get(hash);
            if (value != null) return value;
            final Map<byte[], ReferenceContainer<WordReference>> source = this.containers.get();
            if (source == null) return null;
            final ReferenceContainer<WordReference> container = source.get(hash);
            if (container == null) return null;
            value = WordReferenceFactory.compressIndex(container, null, 1000L).toString();
            this.abstracts.put(hash, value);
            return value;
        }
    }

    void clear() {
        synchronized (this.abstracts) {
            this.cleared = true;
            this.abstracts.clear();
        }
    }

    int size() {
        synchronized (this.abstracts) {
            return this.abstracts.size();
        }
    }
}
