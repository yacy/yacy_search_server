// ResultURLs.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.crawler.data;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.kelondro.util.ReverseMapIterator;

public final class ResultURLs {

    public enum EventOrigin {

        // we must distinguish the following cases: resource-load was initiated by
        // 1) global crawling: the index is extern, not here (not possible here)
        // 2) result of search queries, some indexes are here (not possible here)
        // 3) result of index transfer, some of them are here (not possible here)
        // 4) proxy-load (initiator is "------------")
        // 5) local prefetch/crawling (initiator is own seedHash)
        // 6) local fetching for global crawling (other known or unknown initiator)

        UNKNOWN(0),
        REMOTE_RECEIPTS(1),
        QUERIES(2),
        DHT_TRANSFER(3),
        PROXY_LOAD(4),
        LOCAL_CRAWLING(5),
        GLOBAL_CRAWLING(6),
        SURROGATES(7);

        protected int code;
        private static final EventOrigin[] list = {
            UNKNOWN, REMOTE_RECEIPTS, QUERIES, DHT_TRANSFER, PROXY_LOAD, LOCAL_CRAWLING, GLOBAL_CRAWLING, SURROGATES};
        private EventOrigin(final int code) {
            this.code = code;
        }
        public int getCode() {
            return this.code;
        }
        public static final EventOrigin getEvent(final int key) {
            return list[key];
        }
    }

    private final static Map<EventOrigin, Map<String, InitExecEntry>> resultStacks = new ConcurrentHashMap<EventOrigin, Map<String, InitExecEntry>>(); // a mapping from urlHash to Entries
    private final static Map<EventOrigin, ScoreMap<String>> resultDomains = new ConcurrentHashMap<EventOrigin, ScoreMap<String>>();

    static {
        for (final EventOrigin origin: EventOrigin.values()) {
            resultStacks.put(origin, new LinkedHashMap<String, InitExecEntry>());
            resultDomains.put(origin, new ClusteredScoreMap<String>());
        }
    }

    public static class InitExecEntry {
        public byte[] initiatorHash, executorHash;
        public InitExecEntry(final byte[] initiatorHash, final byte[] executorHash) {
            this.initiatorHash = initiatorHash;
            this.executorHash = executorHash;
        }
    }

    public static void stack(
            final String urlhash,
            final String hostname,
            final byte[] initiatorHash,
            final byte[] executorHash,
            final EventOrigin stackType) {
        // assert initiatorHash != null; // null == proxy !
        assert executorHash != null;
        if (urlhash == null || hostname == null) { return; }
        try {
            final Map<String, InitExecEntry> resultStack = getStack(stackType);
            if (resultStack != null) {
                resultStack.put(urlhash, new InitExecEntry(initiatorHash, executorHash));
            }
        } catch (final Exception ex) {
            System.out.println("INTERNAL ERROR in newEntry/2: " + ex.toString());
            return;
        }
        try {
            final ScoreMap<String> domains = getDomains(stackType);
            if (domains != null) {
                domains.inc(hostname);
            }
        } catch (final Exception ex) {
            System.out.println("INTERNAL ERROR in newEntry/3: " + ex.toString());
            return;
        }
    }

    public static int getStackSize(final EventOrigin stack) {
        final Map<String, InitExecEntry> resultStack = getStack(stack);
        if (resultStack == null) return 0;
        return resultStack.size();
    }

    public static int getDomainListSize(final EventOrigin stack) {
        final ScoreMap<String> domains = getDomains(stack);
        if (domains == null) return 0;
        return domains.size();
    }

    public static Iterator<Map.Entry<String, InitExecEntry>> results(final EventOrigin stack) {
        final Map<String, InitExecEntry> resultStack = getStack(stack);
        if (resultStack == null) return new LinkedHashMap<String, InitExecEntry>().entrySet().iterator();
        return new ReverseMapIterator<String, InitExecEntry>(resultStack);
    }

    /**
     * iterate all domains in the result domain statistic
     * @return iterator of domains in reverse order (downwards)
     */
    public static Iterator<String> domains(final EventOrigin stack) {
        assert getDomains(stack) != null : "getDomains(" + stack + ") = null";
        return getDomains(stack).keys(false);
    }

    public static int deleteDomain(final EventOrigin stack, final String host) {
        assert host != null : "host = null";
        assert getDomains(stack) != null : "getDomains(" + stack + ") = null";
        return getDomains(stack).delete(host);
    }

    /**
     * return the count of the domain
     * @param stack type
     * @param domain name
     * @return the number of occurrences of the domain in the stack statistics
     */
    public static int domainCount(final EventOrigin stack, final String domain) {
        assert domain != null : "domain = null";
        assert getDomains(stack) != null : "getDomains(" + stack + ") = null";
        return getDomains(stack).get(domain);
    }

    /**
     * returns the stack identified by the id <em>stack</em>
     *
     * @param stack id of resultStack
     * @return null if stack does not exist (id is unknown or stack is null (which should not occur and an error is logged))
     */
    private static Map<String, InitExecEntry> getStack(final EventOrigin stack) {
        return resultStacks.get(stack);
    }
    private static ScoreMap<String> getDomains(final EventOrigin stack) {
        return resultDomains.get(stack);
    }

    public static void clearStacks() {
        for (final EventOrigin origin: EventOrigin.values()) clearStack(origin);
    }

    public static void clearStack(final EventOrigin stack) {
        final Map<String, InitExecEntry> resultStack = getStack(stack);
        if (resultStack != null) resultStack.clear();
        final ScoreMap<String> resultDomains = getDomains(stack);
        if (resultDomains != null) {
            // we do not clear this completely, just remove most of the less important entries
            resultDomains.shrinkToMaxSize(100);
            resultDomains.shrinkToMinScore(2);
        }
    }

    public static boolean remove(final String urlHash) {
        if (urlHash == null) return false;
        Map<String, InitExecEntry> resultStack;
        for (final EventOrigin origin: EventOrigin.values()) {
            resultStack = getStack(origin);
            if (resultStack != null) resultStack.remove(urlHash);
        }
        return true;
    }

}
