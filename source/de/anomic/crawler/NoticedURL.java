// NoticedURL.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.crawler;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import de.anomic.crawler.retrieval.Request;

public class NoticedURL {

    public enum StackType {
        LOCAL, GLOBAL, OVERHANG, REMOTE, NOLOAD;
    }

    public static final long minimumLocalDeltaInit  =  10; // the minimum time difference between access of the same local domain
    public static final long minimumGlobalDeltaInit = 500; // the minimum time difference between access of the same global domain

    private Balancer coreStack;      // links found by crawling to depth-1
    private Balancer limitStack;     // links found by crawling at target depth
    private Balancer remoteStack;    // links from remote crawl orders
    private Balancer noloadStack;    // links that are not passed to a loader; the index will be generated from the Request entry

    public NoticedURL(
            final File cachePath,
            final Set<String> myAgentIDs,
            final boolean useTailCache,
            final boolean exceed134217727) {
        Log.logInfo("NoticedURL", "CREATING STACKS at " + cachePath.toString());
        this.coreStack = new Balancer(cachePath, "urlNoticeCoreStack", minimumLocalDeltaInit, minimumGlobalDeltaInit, myAgentIDs, useTailCache, exceed134217727);
        this.limitStack = new Balancer(cachePath, "urlNoticeLimitStack", minimumLocalDeltaInit, minimumGlobalDeltaInit, myAgentIDs, useTailCache, exceed134217727);
        //overhangStack = new plasmaCrawlBalancer(overhangStackFile);
        this.remoteStack = new Balancer(cachePath, "urlNoticeRemoteStack", minimumLocalDeltaInit, minimumGlobalDeltaInit, myAgentIDs, useTailCache, exceed134217727);
        this.noloadStack = new Balancer(cachePath, "urlNoticeNoLoadStack", minimumLocalDeltaInit, minimumGlobalDeltaInit, myAgentIDs, useTailCache, exceed134217727);
    }

    public long getMinimumLocalDelta() {
        return this.coreStack.getMinimumLocalDelta();
    }

    public long getMinimumGlobalDelta() {
        return this.coreStack.getMinimumGlobalDelta();
    }

    public void setMinimumDelta(final long minimumLocalDelta, final long minimumGlobalDelta) {
        this.coreStack.setMinimumDelta(minimumLocalDelta, minimumGlobalDelta);
        this.limitStack.setMinimumDelta(minimumLocalDelta, minimumGlobalDelta);
        this.remoteStack.setMinimumDelta(minimumLocalDelta, minimumGlobalDelta);
        this.noloadStack.setMinimumDelta(minimumLocalDelta, minimumGlobalDelta);
    }

    public void clear() {
    	Log.logInfo("NoticedURL", "CLEARING ALL STACKS");
        this.coreStack.clear();
        this.limitStack.clear();
        this.remoteStack.clear();
        this.noloadStack.clear();
    }

    public void close() {
        Log.logInfo("NoticedURL", "CLOSING ALL STACKS");
        if (this.coreStack != null) {
            this.coreStack.close();
            this.coreStack = null;
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

    @Override
    protected void finalize() throws Throwable {
        if ((this.coreStack != null) || (this.limitStack != null) || (this.remoteStack != null)) {
            Log.logWarning("plasmaCrawlNURL", "NURL stack closed by finalizer");
            close();
        }
        super.finalize();
    }

    public boolean notEmpty() {
        return this.coreStack.notEmpty() || this.limitStack.notEmpty() || this.remoteStack.notEmpty() || this.noloadStack.notEmpty();
    }

    public boolean notEmptyLocal() {
        return this.coreStack.notEmpty() || this.limitStack.notEmpty() || this.noloadStack.notEmpty();
    }

    public int size() {
        // this does not count the overhang stack size
        return ((this.coreStack == null) ? 0 : this.coreStack.size()) + ((this.limitStack == null) ? 0 : this.limitStack.size()) + ((this.remoteStack == null) ? 0 : this.remoteStack.size());
    }

    public boolean isEmpty() {
        if (this.coreStack == null) return true;
        if (!this.coreStack.isEmpty()) return false;
        if (!this.limitStack.isEmpty()) return false;
        if (!this.remoteStack.isEmpty()) return false;
        if (!this.noloadStack.isEmpty()) return false;
        return true;
    }

    public int stackSize(final StackType stackType) {
        switch (stackType) {
            case NOLOAD:    return (this.noloadStack == null) ? 0 : this.noloadStack.size();
            case LOCAL:     return (this.coreStack == null) ? 0 : this.coreStack.size();
            case GLOBAL:    return (this.limitStack == null) ? 0 : this.limitStack.size();
            case OVERHANG: return 0;
            case REMOTE:   return (this.remoteStack == null) ? 0 : this.remoteStack.size();
            default: return -1;
        }
    }

    public boolean existsInStack(final byte[] urlhashb) {
        return
            this.coreStack.has(urlhashb) ||
            this.limitStack.has(urlhashb) ||
            //overhangStack.has(urlhashb) ||
            this.remoteStack.has(urlhashb) ||
            this.noloadStack.has(urlhashb);
    }

    /**
     * push a crawl request on one of the different crawl stacks
     * @param stackType
     * @param entry
     * @return null if this was successful or a String explaining what went wrong in case of an error
     */
    public String push(final StackType stackType, final Request entry) {
        try {
            switch (stackType) {
                case LOCAL:
                    return this.coreStack.push(entry);
                case GLOBAL:
                    return this.limitStack.push(entry);
                case REMOTE:
                    return this.remoteStack.push(entry);
                case NOLOAD:
                    return this.noloadStack.push(entry);
                default:
                    return "stack type unknown";
            }
        } catch (final Exception er) {
            Log.logException(er);
            return "error pushing onto the crawl stack: " + er.getMessage();
        }
    }

    public Request get(final byte[] urlhash) {
        Request entry = null;
        try {if ((entry = this.noloadStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
        try {if ((entry = this.coreStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
        try {if ((entry = this.limitStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
        try {if ((entry = this.remoteStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
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
            final HandleSet urlHashes = new HandleSet(12, Base64Order.enhancedCoder, 1);
            urlHashes.put(urlhashBytes);
            boolean ret = false;
            try {ret |= this.noloadStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {ret |= this.coreStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {ret |= this.limitStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {ret |= this.remoteStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            return ret;
        } catch (final RowSpaceExceededException e) {
            Log.logException(e);
            return false;
        }
    }

    public int removeByProfileHandle(final String handle, final long timeout) throws RowSpaceExceededException {
        int removed = 0;
        try {removed += this.noloadStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += this.coreStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += this.limitStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += this.remoteStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        return removed;
    }

    /**
     * get a list of domains that are currently maintained as domain stacks
     * @return a map of clear text strings of host names to the size of the domain stacks
     */
    public Map<String, Integer[]> getDomainStackHosts(final StackType stackType) {
        switch (stackType) {
            case LOCAL:     return this.coreStack.getDomainStackHosts();
            case GLOBAL:    return this.limitStack.getDomainStackHosts();
            case REMOTE:   return this.remoteStack.getDomainStackHosts();
            case NOLOAD:   return this.noloadStack.getDomainStackHosts();
            default: return null;
        }
    }

    /**
     * get a list of domains that are currently maintained as domain stacks
     * @return a collection of clear text strings of host names
     */
    public long getDomainSleepTime(final StackType stackType, final RobotsTxt robots, final CrawlSwitchboard cs, Request crawlEntry) {
        switch (stackType) {
            case LOCAL:     return this.coreStack.getDomainSleepTime(cs, robots, crawlEntry);
            case GLOBAL:    return this.limitStack.getDomainSleepTime(cs, robots, crawlEntry);
            case REMOTE:   return this.remoteStack.getDomainSleepTime(cs, robots, crawlEntry);
            case NOLOAD:   return this.noloadStack.getDomainSleepTime(cs, robots, crawlEntry);
            default: return 0;
        }
    }

    /**
     * get lists of crawl request entries for a specific host
     * @param host
     * @param maxcount
     * @return a list of crawl loader requests
     */
    public List<Request> getDomainStackReferences(final StackType stackType, String host, int maxcount) {
        switch (stackType) {
            case LOCAL:     return this.coreStack.getDomainStackReferences(host, maxcount);
            case GLOBAL:    return this.limitStack.getDomainStackReferences(host, maxcount);
            case REMOTE:   return this.remoteStack.getDomainStackReferences(host, maxcount);
            case NOLOAD:   return this.noloadStack.getDomainStackReferences(host, maxcount);
            default: return null;
        }
    }

    public Request pop(final StackType stackType, final boolean delay, final CrawlSwitchboard cs, final RobotsTxt robots) throws IOException {
        switch (stackType) {
            case LOCAL:     return pop(this.coreStack, delay, cs, robots);
            case GLOBAL:    return pop(this.limitStack, delay, cs, robots);
            case REMOTE:   return pop(this.remoteStack, delay, cs, robots);
            case NOLOAD:   return pop(this.noloadStack, false, cs, robots);
            default: return null;
        }
    }

    public void shift(final StackType fromStack, final StackType toStack, final CrawlSwitchboard cs, final RobotsTxt robots) {
        try {
            final Request entry = pop(fromStack, false, cs, robots);
            if (entry != null) {
                final String warning = push(toStack, entry);
                if (warning != null) {
                    Log.logWarning("NoticedURL", "shift from " + fromStack + " to " + toStack + ": " + warning);
                }
            }
        } catch (final IOException e) {
            return;
        }
    }

    public void clear(final StackType stackType) {
    	Log.logInfo("NoticedURL", "CLEARING STACK " + stackType);
        switch (stackType) {
                case LOCAL:     this.coreStack.clear(); break;
                case GLOBAL:    this.limitStack.clear(); break;
                case REMOTE:   this.remoteStack.clear(); break;
                case NOLOAD:   this.noloadStack.clear(); break;
                default: return;
            }
    }

    private Request pop(final Balancer balancer, final boolean delay, final CrawlSwitchboard cs, final RobotsTxt robots) throws IOException {
        // this is a filo - pop
        int s;
        Request entry;
        int errors = 0;
        synchronized (balancer) {
            while ((s = balancer.size()) > 0) {
                entry = balancer.pop(delay, cs, robots);
                if (entry == null) {
                    if (s > balancer.size()) continue;
                    errors++;
                    if (errors < 100) continue;
                    final int aftersize = balancer.size();
                    balancer.clear(); // the balancer is broken and cannot shrink
                    Log.logWarning("BALANCER", "entry is null, balancer cannot shrink (bevore pop = " + s + ", after pop = " + aftersize + "); reset of balancer");
                }
                return entry;
            }
        }
        return null;
    }

    public Iterator<Request> iterator(final StackType stackType) {
        // returns an iterator of plasmaCrawlBalancerEntry Objects
        try {switch (stackType) {
            case LOCAL:     return this.coreStack.iterator();
            case GLOBAL:    return this.limitStack.iterator();
            case REMOTE:   return this.remoteStack.iterator();
            case NOLOAD:   return this.noloadStack.iterator();
            default: return null;
        }} catch (final IOException e) {
            return new HashSet<Request>().iterator();
        }
    }

}
