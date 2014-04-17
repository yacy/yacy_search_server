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

package net.yacy.crawler.data;

import java.io.File;
import java.io.IOException;
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

public class NoticedURL {

    public enum StackType {
        LOCAL, GLOBAL, REMOTE, NOLOAD;
    }

    private Balancer coreStack;      // links found by crawling to depth-1
    private Balancer limitStack;     // links found by crawling at target depth
    private Balancer remoteStack;    // links from remote crawl orders
    private Balancer noloadStack;    // links that are not passed to a loader; the index will be generated from the Request entry

    protected NoticedURL(
            final File cachePath,
            @SuppressWarnings("unused") final boolean useTailCache,
            final boolean exceed134217727) {
        ConcurrentLog.info("NoticedURL", "CREATING STACKS at " + cachePath.toString());
        this.coreStack = new HostBalancer(new File(cachePath, "CrawlerCoreStacks"), exceed134217727);
        this.limitStack = new HostBalancer(new File(cachePath, "CrawlerLimitStacks"), exceed134217727);
        this.remoteStack = new HostBalancer(new File(cachePath, "CrawlerRemoteStacks"), exceed134217727);
        this.noloadStack = new HostBalancer(new File(cachePath, "CrawlerNoLoadStacks"), exceed134217727);
    }

    public void clear() {
    	ConcurrentLog.info("NoticedURL", "CLEARING ALL STACKS");
    	if (this.coreStack != null) this.coreStack.clear();
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
            ConcurrentLog.warn("plasmaCrawlNURL", "NURL stack closed by finalizer");
            close();
        }
        super.finalize();
    }

    public int size() {
        // this does not count the overhang stack size
        return ((this.coreStack == null) ? 0 : this.coreStack.size()) + ((this.limitStack == null) ? 0 : this.limitStack.size()) + ((this.remoteStack == null) ? 0 : this.remoteStack.size());
    }

    public boolean isEmptyLocal() {
        if (this.coreStack == null) return true;
        if (!this.coreStack.isEmpty()) return false;
        if (!this.limitStack.isEmpty()) return false;
        if (!this.noloadStack.isEmpty()) return false;
        return true;
    }
    
    public boolean isEmpty() {
        if (!isEmptyLocal()) return false;
        if (!this.remoteStack.isEmpty()) return false;
        return true;
    }

    public boolean isEmpty(final StackType stackType) {
        switch (stackType) {
            case NOLOAD:    return (this.noloadStack == null) ? true : this.noloadStack.isEmpty();
            case LOCAL:     return (this.coreStack == null) ? true : this.coreStack.isEmpty();
            case GLOBAL:    return (this.limitStack == null) ? true : this.limitStack.isEmpty();
            case REMOTE:   return (this.remoteStack == null) ? true : this.remoteStack.isEmpty();
            default: return true;
        }
    }
    
    public int stackSize(final StackType stackType) {
        switch (stackType) {
            case NOLOAD:    return (this.noloadStack == null) ? 0 : this.noloadStack.size();
            case LOCAL:     return (this.coreStack == null) ? 0 : this.coreStack.size();
            case GLOBAL:    return (this.limitStack == null) ? 0 : this.limitStack.size();
            case REMOTE:   return (this.remoteStack == null) ? 0 : this.remoteStack.size();
            default: return -1;
        }
    }

    protected boolean existsInStack(final byte[] urlhashb) {
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
    public String push(final StackType stackType, final Request entry, CrawlProfile profile, final RobotsTxt robots) {
        try {
            switch (stackType) {
                case LOCAL: return this.coreStack.push(entry, profile, robots);
                case GLOBAL: return this.limitStack.push(entry, profile, robots);
                case REMOTE: return this.remoteStack.push(entry, profile, robots);
                case NOLOAD: return this.noloadStack.push(entry, profile, robots);
                default: return "stack type unknown";
            }
        } catch (final Exception er) {
            ConcurrentLog.logException(er);
            return "error pushing onto the crawl stack: " + er.getMessage();
        }
    }

    protected Request get(final byte[] urlhash) {
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
            final HandleSet urlHashes = new RowHandleSet(Word.commonHashLength, Base64Order.enhancedCoder, 1);
            urlHashes.put(urlhashBytes);
            boolean ret = false;
            try {ret |= this.noloadStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {ret |= this.coreStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {ret |= this.limitStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {ret |= this.remoteStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            return ret;
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
            return false;
        }
    }

    public int removeByProfileHandle(final String handle, final long timeout) throws SpaceExceededException {
        int removed = 0;
        try {removed += this.noloadStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += this.coreStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += this.limitStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += this.remoteStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        return removed;
    }

    public int removeByHostHash(final Set<String> hosthashes) {
        int removed = 0;
        removed += this.noloadStack.removeAllByHostHashes(hosthashes);
        removed += this.coreStack.removeAllByHostHashes(hosthashes);
        removed += this.limitStack.removeAllByHostHashes(hosthashes);
        removed += this.remoteStack.removeAllByHostHashes(hosthashes);
        return removed;
    }

    /**
     * get a list of domains that are currently maintained as domain stacks
     * @return a map of clear text strings of host names to two integers: the size of the domain stacks and the access delta time
     */
    public Map<String, Integer[]> getDomainStackHosts(final StackType stackType, RobotsTxt robots) {
        switch (stackType) {
            case LOCAL:     return this.coreStack.getDomainStackHosts(robots);
            case GLOBAL:    return this.limitStack.getDomainStackHosts(robots);
            case REMOTE:   return this.remoteStack.getDomainStackHosts(robots);
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
            case GLOBAL:    return this.limitStack.getDomainStackReferences(host, maxcount, maxtime);
            case REMOTE:   return this.remoteStack.getDomainStackReferences(host, maxcount, maxtime);
            case NOLOAD:   return this.noloadStack.getDomainStackReferences(host, maxcount, maxtime);
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

    protected void shift(final StackType fromStack, final StackType toStack, final CrawlSwitchboard cs, final RobotsTxt robots) {
        try {
            final Request entry = pop(fromStack, false, cs, robots);
            if (entry != null) {
                final String warning = push(toStack, entry, null, robots);
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
                case LOCAL:     this.coreStack.clear(); break;
                case GLOBAL:    this.limitStack.clear(); break;
                case REMOTE:   this.remoteStack.clear(); break;
                case NOLOAD:   this.noloadStack.clear(); break;
                default: return;
            }
    }

    private static Request pop(final Balancer balancer, final boolean delay, final CrawlSwitchboard cs, final RobotsTxt robots) throws IOException {
        // this is a filo - pop
        int s;
        Request entry;
        int errors = 0;
        while ((s = balancer.size()) > 0) {
            entry = balancer.pop(delay, cs, robots);
            if (entry == null) {
                if (s > balancer.size()) continue;
                errors++;
                if (errors < 100) continue;
                final int aftersize = balancer.size();
                balancer.clear(); // the balancer is broken and cannot shrink
                ConcurrentLog.warn("BALANCER", "entry is null, balancer cannot shrink (bevore pop = " + s + ", after pop = " + aftersize + "); reset of balancer");
            }
            return entry;
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
