// NoticedURL.java
// -----------------------
// part of YaCy
// SPDX-FileCopyrightText: 2004 Michael Peter Christen <mc@yacy.net)>
// SPDX-License-Identifier: GPL-2.0-or-later
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
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

// NURL - noticed (known but not loaded) URL's

package net.yacy.crawler.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.order.Base64Order;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.Balancer;
import net.yacy.crawler.CrawlSwitchboard;
import net.yacy.crawler.HostBalancer;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.util.MemoryControl;

public class NoticedURL {

    public enum StackType {
        LOCAL,
        /**
         * @deprecated persisted compatibility alias for the generic focused lane.
         * The on-disk queue path is retained so upgrades do not strand old work.
         */
        @Deprecated CANADIAN,
        FOCUSED,
        /** persisted focused PDF lane; ordinary focused queue rows are unchanged */ FOCUSED_PDF,
        /** Generic novelty lanes used by focused crawl policies. */
        FOCUSED_NEW_HOST, FOCUSED_NEW_PATH, FOCUSED_EXPANSION, FOCUSED_REFRESH,
        GLOBAL, REMOTE, NOLOAD;
    }

    /** links found by crawling to depth-1 */
    private Balancer coreStack;

    /**
     * Focused-policy lane. The historical on-disk path is retained for queue
     * compatibility with earlier Canadian-focused deployments.
     */
    private Balancer focusedStack;

    /** New-host and new-path lanes are additive; the legacy focused stack is retained as expansion. */
    private Balancer focusedNewHostStack;
    private Balancer focusedNewPathStack;
    private Balancer focusedRefreshStack;
    private final int[] focusedNoveltyWeights = new int[] {45, 35, 15, 5};
    private final long[] focusedNoveltyDeficits = new long[] {0L, 0L, 0L, 0L};

    /** Focused PDF lane. Keeping this separate means queued PDFs do not consume HTML lane slots. */
    private Balancer focusedPdfStack;

    /** links found by crawling at target depth */
    private Balancer limitStack;

    /** links from remote crawl orders (init on demand) */
    private Balancer remoteStack;

    /** links that are not passed to a loader; the index will be generated from the Request entry */
    private Balancer noloadStack;

    private final File cachePath;

    protected NoticedURL(
            final File cachePath,
            final int onDemandLimit,
            final boolean exceed134217727) {
        ConcurrentLog.info("NoticedURL", "START CREATING STACKS at " + cachePath.toString());
        ConcurrentLog.info("NoticedURL", "opening CrawlerCoreStacks..");
        this.cachePath = cachePath;
        this.coreStack = new HostBalancer(new File(cachePath, "CrawlerCoreStacks"), onDemandLimit, exceed134217727);
        this.focusedStack = new HostBalancer(new File(cachePath, "CrawlerCanadianStacks"), onDemandLimit, exceed134217727);
        this.focusedNewHostStack = new HostBalancer(new File(cachePath, "CrawlerFocusedNewHostStacks"), onDemandLimit, exceed134217727);
        this.focusedNewPathStack = new HostBalancer(new File(cachePath, "CrawlerFocusedNewPathStacks"), onDemandLimit, exceed134217727);
        this.focusedRefreshStack = new HostBalancer(new File(cachePath, "CrawlerFocusedRefreshStacks"), onDemandLimit, exceed134217727);
        this.focusedPdfStack = new HostBalancer(new File(cachePath, "CrawlerFocusedPdfStacks"), onDemandLimit, exceed134217727);
        ConcurrentLog.info("NoticedURL", "opening CrawlerLimitStacks..");
        this.limitStack = new HostBalancer(new File(cachePath, "CrawlerLimitStacks"), onDemandLimit, exceed134217727);

        this.remoteStack = null; // init on demand (on first push)

        ConcurrentLog.info("NoticedURL", "opening CrawlerNoLoadStacks..");
        this.noloadStack = new HostBalancer(new File(cachePath, "CrawlerNoLoadStacks"), onDemandLimit, exceed134217727);
        ConcurrentLog.info("NoticedURL", "FINISHED CREATING STACKS at " + cachePath.toString());
    }

    /**
     * Init Remote crawl stack, internally called on 1st push to remoteStack
     */
    protected void initRemoteStack() {
        if (this.remoteStack == null && !MemoryControl.shortStatus()) {
            ConcurrentLog.info("NoticedURL", "opening CrawlerRemoteStacks..");
            this.remoteStack = new HostBalancer(new File(this.cachePath, "CrawlerRemoteStacks"), this.coreStack.getOnDemandLimit(), this.coreStack.getExceed134217727());
        }
    }

    /** Apply the profile's generic novelty weights without changing persisted queues. */
    public synchronized void setFocusedNoveltyWeights(final int newHost, final int newPath,
            final int expansion, final int refresh) {
        this.focusedNoveltyWeights[0] = Math.max(0, newHost);
        this.focusedNoveltyWeights[1] = Math.max(0, newPath);
        this.focusedNoveltyWeights[2] = Math.max(0, expansion);
        this.focusedNoveltyWeights[3] = Math.max(0, refresh);
    }

    private int focusedStackSize() {
        return (this.focusedStack == null ? 0 : this.focusedStack.size())
                + (this.focusedNewHostStack == null ? 0 : this.focusedNewHostStack.size())
                + (this.focusedNewPathStack == null ? 0 : this.focusedNewPathStack.size())
                + (this.focusedRefreshStack == null ? 0 : this.focusedRefreshStack.size());
    }

    private boolean focusedStackEmpty() {
        return (this.focusedStack == null || this.focusedStack.isEmpty())
                && (this.focusedNewHostStack == null || this.focusedNewHostStack.isEmpty())
                && (this.focusedNewPathStack == null || this.focusedNewPathStack.isEmpty())
                && (this.focusedRefreshStack == null || this.focusedRefreshStack.isEmpty());
    }

    private Map<String, Integer[]> focusedDomainHosts(final RobotsTxt robots) {
        final Map<String, Integer[]> result = new java.util.TreeMap<>();
        for (final Balancer balancer : focusedBalancers()) {
            for (final Map.Entry<String, Integer[]> entry : balancer.getDomainStackHosts(robots).entrySet()) {
                final Integer[] previous = result.get(entry.getKey());
                if (previous == null) result.put(entry.getKey(), entry.getValue());
                else result.put(entry.getKey(), new Integer[] {previous[0] + entry.getValue()[0],
                        Math.min(previous[1], entry.getValue()[1])});
            }
        }
        return result;
    }

    private List<Request> focusedDomainReferences(final String host, final int maxcount, final long maxtime) {
        final List<Request> result = new ArrayList<>();
        for (final Balancer balancer : focusedBalancers()) {
            if (result.size() >= maxcount) break;
            result.addAll(balancer.getDomainStackReferences(host, maxcount - result.size(), maxtime));
        }
        return result;
    }

    private Balancer[] focusedBalancers() {
        return new Balancer[] {this.focusedStack, this.focusedNewHostStack,
                this.focusedNewPathStack, this.focusedRefreshStack};
    }

    private Request popFocused(final boolean delay, final CrawlSwitchboard cs, final RobotsTxt robots) throws IOException {
        // The weights are scheduling targets, not admission gates. Empty or
        // temporarily unavailable lanes are skipped, so their share is
        // borrowed by whichever focused lanes have eligible work.
        final Balancer[] balancers = focusedBalancers();
        final boolean[] attempted = new boolean[balancers.length];
        final int total = Math.max(1, this.focusedNoveltyWeights[0] + this.focusedNoveltyWeights[1]
                + this.focusedNoveltyWeights[2] + this.focusedNoveltyWeights[3]);
        synchronized (this) {
            for (int i = 0; i < this.focusedNoveltyDeficits.length; i++) {
                this.focusedNoveltyDeficits[i] += this.focusedNoveltyWeights[i];
            }
        }
        for (int attempt = 0; attempt < balancers.length; attempt++) {
            int selected = -1;
            long best = Long.MIN_VALUE;
            synchronized (this) {
                for (int i = 0; i < balancers.length; i++) {
                    if (attempted[i] || balancers[i] == null || balancers[i].isEmpty()) continue;
                    if (this.focusedNoveltyDeficits[i] > best) {
                        best = this.focusedNoveltyDeficits[i];
                        selected = i;
                    }
                }
                if (selected >= 0) this.focusedNoveltyDeficits[selected] -= total;
            }
            if (selected < 0) return null;
            attempted[selected] = true;
            final Request result = pop(balancers[selected], delay, cs, robots);
            if (result != null) return result;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Iterator<Request> focusedIterator() throws IOException {
        final Iterator<Request>[] iterators = new Iterator[] {
                this.focusedStack.iterator(), this.focusedNewHostStack.iterator(),
                this.focusedNewPathStack.iterator(), this.focusedRefreshStack.iterator()};
        return new Iterator<>() {
            private int index;
            private Request next;
            private boolean prepared;

            private void prepare() {
                if (this.prepared) return;
                while (this.index < iterators.length) {
                    final Iterator<Request> iterator = iterators[this.index];
                    if (iterator != null && iterator.hasNext()) {
                        this.next = iterator.next();
                        this.prepared = true;
                        return;
                    }
                    this.index++;
                }
            }

            @Override public boolean hasNext() { prepare(); return this.prepared; }
            @Override public Request next() {
                prepare();
                final Request result = this.next;
                this.next = null;
                this.prepared = false;
                return result;
            }
        };
    }

    public void clear() {
    	ConcurrentLog.info("NoticedURL", "CLEARING ALL STACKS");
     if (this.coreStack != null) this.coreStack.clear();
     if (this.focusedStack != null) this.focusedStack.clear();
     if (this.focusedNewHostStack != null) this.focusedNewHostStack.clear();
     if (this.focusedNewPathStack != null) this.focusedNewPathStack.clear();
     if (this.focusedRefreshStack != null) this.focusedRefreshStack.clear();
     if (this.focusedPdfStack != null) this.focusedPdfStack.clear();
    	if (this.limitStack != null) this.limitStack.clear();
    	if (this.remoteStack != null) this.remoteStack.clear();
    	if (this.noloadStack != null) this.noloadStack.clear();
    }

    protected void close() {
        ConcurrentLog.info("NoticedURL", "CLOSING ALL STACKS");
        if (this.coreStack != null) {
            this.coreStack.close();
            this.coreStack = null;
        }
        if (this.focusedStack != null) {
            this.focusedStack.close();
            this.focusedStack = null;
        }
        if (this.focusedNewHostStack != null) {
            this.focusedNewHostStack.close();
            this.focusedNewHostStack = null;
        }
        if (this.focusedNewPathStack != null) {
            this.focusedNewPathStack.close();
            this.focusedNewPathStack = null;
        }
        if (this.focusedRefreshStack != null) {
            this.focusedRefreshStack.close();
            this.focusedRefreshStack = null;
        }
        if (this.focusedPdfStack != null) {
            this.focusedPdfStack.close();
            this.focusedPdfStack = null;
        }
        if (this.limitStack != null) {
            this.limitStack.close();
            this.limitStack = null;
        }
        //overhangStack.close();
        if (this.remoteStack != null) {
            this.remoteStack.close();
            this.remoteStack = null;
        }
        if (this.noloadStack != null) {
            this.noloadStack.close();
            this.noloadStack = null;
        }
    }

    public int size() {
        return ((this.coreStack == null) ? 0 : this.coreStack.size()) + focusedStackSize()
                + ((this.focusedPdfStack == null) ? 0 : this.focusedPdfStack.size())
                + ((this.limitStack == null) ? 0 : this.limitStack.size()) + ((this.remoteStack == null) ? 0 : this.remoteStack.size());
    }

    public boolean isEmptyLocal() {
        if (this.coreStack == null) return true;
        if (!this.coreStack.isEmpty()) return false;
        if (!focusedStackEmpty()) return false;
        if (!this.focusedPdfStack.isEmpty()) return false;
        if (!this.limitStack.isEmpty()) return false;
        if (!this.noloadStack.isEmpty()) return false;
        return true;
    }

    public boolean isEmpty() {
        if (!this.isEmptyLocal()) return false;
        if (this.remoteStack != null && !this.remoteStack.isEmpty()) return false;
        return true;
    }

    public boolean isEmpty(final StackType stackType) {
        switch (stackType) {
            case NOLOAD:    return (this.noloadStack == null) ? true : this.noloadStack.isEmpty();
            case LOCAL:     return (this.coreStack == null) ? true : this.coreStack.isEmpty();
            case FOCUSED:
            case CANADIAN:  return focusedStackEmpty();
            case FOCUSED_NEW_HOST: return this.focusedNewHostStack == null || this.focusedNewHostStack.isEmpty();
            case FOCUSED_NEW_PATH: return this.focusedNewPathStack == null || this.focusedNewPathStack.isEmpty();
            case FOCUSED_EXPANSION: return this.focusedStack == null || this.focusedStack.isEmpty();
            case FOCUSED_REFRESH: return this.focusedRefreshStack == null || this.focusedRefreshStack.isEmpty();
            case FOCUSED_PDF: return (this.focusedPdfStack == null) ? true : this.focusedPdfStack.isEmpty();
            case GLOBAL:    return (this.limitStack == null) ? true : this.limitStack.isEmpty();
            case REMOTE:   return (this.remoteStack == null) ? true : this.remoteStack.isEmpty();
            default: return true;
        }
    }

    public int stackSize(final StackType stackType) {
        switch (stackType) {
            case NOLOAD:    return (this.noloadStack == null) ? 0 : this.noloadStack.size();
            case LOCAL:     return (this.coreStack == null) ? 0 : this.coreStack.size();
            case FOCUSED:
            case CANADIAN:  return focusedStackSize();
            case FOCUSED_NEW_HOST: return this.focusedNewHostStack == null ? 0 : this.focusedNewHostStack.size();
            case FOCUSED_NEW_PATH: return this.focusedNewPathStack == null ? 0 : this.focusedNewPathStack.size();
            case FOCUSED_EXPANSION: return this.focusedStack == null ? 0 : this.focusedStack.size();
            case FOCUSED_REFRESH: return this.focusedRefreshStack == null ? 0 : this.focusedRefreshStack.size();
            case FOCUSED_PDF: return (this.focusedPdfStack == null) ? 0 : this.focusedPdfStack.size();
            case GLOBAL:    return (this.limitStack == null) ? 0 : this.limitStack.size();
            case REMOTE:   return (this.remoteStack == null) ? 0 : this.remoteStack.size();
            default: return -1;
        }
    }

    protected boolean existsInStack(final byte[] urlhashb) {
        return
            this.coreStack.has(urlhashb) ||
            this.focusedStack.has(urlhashb) ||
            this.focusedNewHostStack.has(urlhashb) ||
            this.focusedNewPathStack.has(urlhashb) ||
            this.focusedRefreshStack.has(urlhashb) ||
            this.focusedPdfStack.has(urlhashb) ||
            this.limitStack.has(urlhashb) ||
            (this.remoteStack != null && this.remoteStack.has(urlhashb)) ||
            this.noloadStack.has(urlhashb);
    }

    /** Return whether a URL is already present in any native crawl stack. */
    public boolean contains(final byte[] urlhashb) {
        return urlhashb != null && existsInStack(urlhashb);
    }

    /**
     * push a crawl request on one of the different crawl stacks
     * @param stackType
     * @param entry
     * @return null if this was successful or a String explaining what went wrong in case of an error
     */
    public String push(final StackType stackType, final Request entry, CrawlProfile profile, final RobotsTxt robots) {
        try {
            switch (stackType) {
                case LOCAL:  return this.coreStack.push(entry, profile, robots);
            case FOCUSED:
                case CANADIAN:
                case FOCUSED_EXPANSION: return this.focusedStack.push(entry, profile, robots);
                case FOCUSED_NEW_HOST: return this.focusedNewHostStack.push(entry, profile, robots);
                case FOCUSED_NEW_PATH: return this.focusedNewPathStack.push(entry, profile, robots);
                case FOCUSED_REFRESH: return this.focusedRefreshStack.push(entry, profile, robots);
                case FOCUSED_PDF: return this.focusedPdfStack.push(entry, profile, robots);
                case GLOBAL: return this.limitStack.push(entry, profile, robots);
                case REMOTE: {
                    if (this.remoteStack == null) {
                        this.initRemoteStack();
                    }
                    return (this.remoteStack != null) ? this.remoteStack.push(entry, profile, robots) : "remote crawler stack deactivated";
                }
                case NOLOAD: return this.noloadStack.push(entry, profile, robots);
                default:     return "stack type unknown";
            }
        } catch (final Exception er) {
            ConcurrentLog.logException(er);
            return "error pushing onto the crawl stack: " + er.getMessage();
        }
    }

    protected Request get(final byte[] urlhash) {
        Request entry = null;
        try {if ((entry = this.noloadStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
        try {if ((entry = this.focusedStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
        try {if ((entry = this.focusedNewHostStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
        try {if ((entry = this.focusedNewPathStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
        try {if ((entry = this.focusedRefreshStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
        try {if ((entry = this.focusedPdfStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
        try {if ((entry = this.coreStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
        try {if ((entry = this.limitStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
        try {if (this.remoteStack != null && (entry = this.remoteStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
        return null;
    }

    /**
     * remove a CrawlEntry by a given hash. Usage of this method is not encouraged,
     * because the underlying data structure (crawl stacks) cannot handle removals very good.
     * @param urlhash
     * @return true, if the entry was removed; false if not
     */
    public boolean removeByURLHash(final byte[] urlhashBytes) {
        try {
            final HandleSet urlHashes = new RowHandleSet(Word.commonHashLength, Base64Order.enhancedCoder, 1);
            urlHashes.put(urlhashBytes);
            boolean ret = false;
            try {ret |= this.noloadStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {ret |= this.focusedStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {ret |= this.focusedNewHostStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {ret |= this.focusedNewPathStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {ret |= this.focusedRefreshStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {ret |= this.focusedPdfStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {ret |= this.coreStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {ret |= this.limitStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {ret |= this.remoteStack != null && this.remoteStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            return ret;
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
            return false;
        }
    }

    public int removeByProfileHandle(final String handle, final long timeout) throws SpaceExceededException {
        int removed = 0;
        try {removed += this.noloadStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += this.focusedStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += this.focusedNewHostStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += this.focusedNewPathStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += this.focusedRefreshStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += this.focusedPdfStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += this.coreStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += this.limitStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        if (this.remoteStack != null) try {removed += this.remoteStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        return removed;
    }

    public int removeByHostHash(final Set<String> hosthashes) {
        int removed = 0;
        removed += this.noloadStack.removeAllByHostHashes(hosthashes);
        removed += this.focusedStack.removeAllByHostHashes(hosthashes);
        removed += this.focusedNewHostStack.removeAllByHostHashes(hosthashes);
        removed += this.focusedNewPathStack.removeAllByHostHashes(hosthashes);
        removed += this.focusedRefreshStack.removeAllByHostHashes(hosthashes);
        removed += this.focusedPdfStack.removeAllByHostHashes(hosthashes);
        removed += this.coreStack.removeAllByHostHashes(hosthashes);
        removed += this.limitStack.removeAllByHostHashes(hosthashes);
        if (this.remoteStack != null) removed += this.remoteStack.removeAllByHostHashes(hosthashes);
        return removed;
    }

    /**
     * get a list of domains that are currently maintained as domain stacks
     * @return a map of clear text strings of host names (each host name eventually concatenated with a port, depending on the stack) to two integers: the size of the domain stacks and the access delta time
     */
    public Map<String, Integer[]> getDomainStackHosts(final StackType stackType, RobotsTxt robots) {
        switch (stackType) {
            case LOCAL:     return this.coreStack.getDomainStackHosts(robots);
            case FOCUSED:
            case CANADIAN:  return focusedDomainHosts(robots);
            case FOCUSED_NEW_HOST: return this.focusedNewHostStack.getDomainStackHosts(robots);
            case FOCUSED_NEW_PATH: return this.focusedNewPathStack.getDomainStackHosts(robots);
            case FOCUSED_EXPANSION: return this.focusedStack.getDomainStackHosts(robots);
            case FOCUSED_REFRESH: return this.focusedRefreshStack.getDomainStackHosts(robots);
            case FOCUSED_PDF: return this.focusedPdfStack.getDomainStackHosts(robots);
            case GLOBAL:    return this.limitStack.getDomainStackHosts(robots);
            case REMOTE:   return (this.remoteStack != null) ? this.remoteStack.getDomainStackHosts(robots) : null;
            case NOLOAD:   return this.noloadStack.getDomainStackHosts(robots);
            default: return null;
        }
    }

    /**
     * get lists of crawl request entries for a specific host
     * @param host
     * @param maxcount
     * @return a list of crawl loader requests
     */
    public List<Request> getDomainStackReferences(final StackType stackType, String host, int maxcount, final long maxtime) {
        switch (stackType) {
            case LOCAL:     return this.coreStack.getDomainStackReferences(host, maxcount, maxtime);
            case FOCUSED:
            case CANADIAN:  return focusedDomainReferences(host, maxcount, maxtime);
            case FOCUSED_NEW_HOST: return this.focusedNewHostStack.getDomainStackReferences(host, maxcount, maxtime);
            case FOCUSED_NEW_PATH: return this.focusedNewPathStack.getDomainStackReferences(host, maxcount, maxtime);
            case FOCUSED_EXPANSION: return this.focusedStack.getDomainStackReferences(host, maxcount, maxtime);
            case FOCUSED_REFRESH: return this.focusedRefreshStack.getDomainStackReferences(host, maxcount, maxtime);
            case FOCUSED_PDF: return this.focusedPdfStack.getDomainStackReferences(host, maxcount, maxtime);
            case GLOBAL:    return this.limitStack.getDomainStackReferences(host, maxcount, maxtime);
            case REMOTE:   return (this.remoteStack != null) ? this.remoteStack.getDomainStackReferences(host, maxcount, maxtime) : Collections.emptyList();
            case NOLOAD:   return this.noloadStack.getDomainStackReferences(host, maxcount, maxtime);
            default: return Collections.emptyList();
        }
    }

    public Request pop(final StackType stackType, final boolean delay, final CrawlSwitchboard cs, final RobotsTxt robots) throws IOException {
        switch (stackType) {
            case LOCAL:     return pop(this.coreStack, delay, cs, robots);
            case FOCUSED:
            case CANADIAN:  return popFocused(delay, cs, robots);
            case FOCUSED_NEW_HOST: return pop(this.focusedNewHostStack, delay, cs, robots);
            case FOCUSED_NEW_PATH: return pop(this.focusedNewPathStack, delay, cs, robots);
            case FOCUSED_EXPANSION: return pop(this.focusedStack, delay, cs, robots);
            case FOCUSED_REFRESH: return pop(this.focusedRefreshStack, delay, cs, robots);
            case FOCUSED_PDF: return pop(this.focusedPdfStack, delay, cs, robots);
            case GLOBAL:    return pop(this.limitStack, delay, cs, robots);
            case REMOTE:   return (this.remoteStack != null) ? pop(this.remoteStack, delay, cs, robots) : null;
            case NOLOAD:   return pop(this.noloadStack, false, cs, robots);
            default: return null;
        }
    }

    protected void shift(final StackType fromStack, final StackType toStack, final CrawlSwitchboard cs, final RobotsTxt robots) {
        try {
            final Request entry = this.pop(fromStack, false, cs, robots);
            if (entry != null) {
                final String warning = this.push(toStack, entry, null, robots);
                if (warning != null) {
                    ConcurrentLog.warn("NoticedURL", "shift from " + fromStack + " to " + toStack + ": " + warning);
                }
            }
        } catch (final IOException e) {
            return;
        }
    }

    public void clear(final StackType stackType) {
        ConcurrentLog.info("NoticedURL", "CLEARING STACK " + stackType);
        switch (stackType) {
            case LOCAL:
                this.coreStack.clear();
                break;
            case FOCUSED:
            case CANADIAN:
                this.focusedStack.clear();
                this.focusedNewHostStack.clear();
                this.focusedNewPathStack.clear();
                this.focusedRefreshStack.clear();
                break;
            case FOCUSED_NEW_HOST:
                this.focusedNewHostStack.clear();
                break;
            case FOCUSED_NEW_PATH:
                this.focusedNewPathStack.clear();
                break;
            case FOCUSED_EXPANSION:
                this.focusedStack.clear();
                break;
            case FOCUSED_REFRESH:
                this.focusedRefreshStack.clear();
                break;
            case FOCUSED_PDF:
                this.focusedPdfStack.clear();
                break;
            case GLOBAL:
                this.limitStack.clear();
                break;
            case REMOTE:
                if (this.remoteStack != null) {
                    this.remoteStack.clear();
                }
                break;
            case NOLOAD:
                this.noloadStack.clear();
                break;
            default:
                return;
        }
    }

    private static Request pop(final Balancer balancer, final boolean delay, final CrawlSwitchboard cs, final RobotsTxt robots) throws IOException {
        // this is a filo - pop
        int s;
        Request entry;
        int errors = 0;
        while (!balancer.isEmpty()) {
            entry = balancer.pop(delay, cs, robots);
            if (entry != null) return entry;

            // the balancer was supposed to be not empty. Check this again
            // it may be possible that another process has taken all
            s = balancer.size(); // this time read the size to find errors
            if (s == 0) return null; // the balancer is actually empty!

            // if the balancer is not empty, try again
            entry = balancer.pop(delay, cs, robots);
            if (entry != null) return entry;

            if (s > balancer.size()) continue; // the balancer has shrinked, thats good, it will terminate
            errors++; // bad, if the size does not shrink we are in danger to not terminate
            if (errors < 100) continue; // there is the possibility that it is not a bug but concurrency, so just ignore it for some time

            // at this point we consider the balancer to be broken
            final int aftersize = balancer.size(); // get the amount of data that we loose
            balancer.clear(); // the balancer is broken and cannot shrink
            ConcurrentLog.warn("BALANCER", "balancer cannot shrink (bevore pop = " + s + ", after pop = " + aftersize + "); reset of balancer");
            return null;
        }
        return null;
    }

    public Iterator<Request> iterator(final StackType stackType) {
        // returns an iterator of plasmaCrawlBalancerEntry Objects
        try {switch (stackType) {
            case LOCAL:     return this.coreStack.iterator();
            case FOCUSED:
            case CANADIAN:  return focusedIterator();
            case FOCUSED_NEW_HOST: return this.focusedNewHostStack.iterator();
            case FOCUSED_NEW_PATH: return this.focusedNewPathStack.iterator();
            case FOCUSED_EXPANSION: return this.focusedStack.iterator();
            case FOCUSED_REFRESH: return this.focusedRefreshStack.iterator();
            case FOCUSED_PDF: return this.focusedPdfStack.iterator();
            case GLOBAL:    return this.limitStack.iterator();
            case REMOTE:   return (this.remoteStack != null) ? this.remoteStack.iterator() : null;
            case NOLOAD:   return this.noloadStack.iterator();
            default: return null;
        }} catch (final IOException e) {
            return new HashSet<Request>().iterator();
        }
    }

}
