// plasmaNURL.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 09.08.2004
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;

import de.anomic.crawler.retrieval.Request;

public class NoticedURL {
    
    public static final int STACK_TYPE_NULL     =  0; // do not stack
    public static final int STACK_TYPE_CORE     =  1; // put on local stack
    public static final int STACK_TYPE_LIMIT    =  2; // put on global stack
    public static final int STACK_TYPE_OVERHANG =  3; // put on overhang stack; links that are known but not crawled
    public static final int STACK_TYPE_REMOTE   =  4; // put on remote-triggered stack
    public static final int STACK_TYPE_IMAGE    = 11; // put on image stack
    public static final int STACK_TYPE_MOVIE    = 12; // put on movie stack
    public static final int STACK_TYPE_MUSIC    = 13; // put on music stack

    public static final long minimumLocalDeltaInit  =  10; // the minimum time difference between access of the same local domain
    public static final long minimumGlobalDeltaInit = 500; // the minimum time difference between access of the same global domain
    
    private Balancer coreStack;      // links found by crawling to depth-1
    private Balancer limitStack;     // links found by crawling at target depth
    private Balancer remoteStack;    // links from remote crawl orders
    
    public NoticedURL(
    		final File cachePath,
            final boolean useTailCache,
            final boolean exceed134217727) {
        Log.logInfo("NoticedURL", "CREATING STACKS at " + cachePath.toString());
        this.coreStack = new Balancer(cachePath, "urlNoticeCoreStack", minimumLocalDeltaInit, minimumGlobalDeltaInit, useTailCache, exceed134217727);
        this.limitStack = new Balancer(cachePath, "urlNoticeLimitStack", minimumLocalDeltaInit, minimumGlobalDeltaInit, useTailCache, exceed134217727);
        //overhangStack = new plasmaCrawlBalancer(overhangStackFile);
        this.remoteStack = new Balancer(cachePath, "urlNoticeRemoteStack", minimumLocalDeltaInit, minimumGlobalDeltaInit, useTailCache, exceed134217727);
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
    }
    
    public void clear() {
    	Log.logInfo("NoticedURL", "CLEARING ALL STACKS");
        coreStack.clear();
        limitStack.clear();
        remoteStack.clear();
    }
    
    public void close() {
        Log.logInfo("NoticedURL", "CLOSING ALL STACKS");
        if (coreStack != null) {
            coreStack.close();
            coreStack = null;
        }
        if (limitStack != null) {
            limitStack.close();
            limitStack = null;
        }
        //overhangStack.close();
        if (remoteStack != null) {
            remoteStack.close();
            remoteStack = null;
        }
    }
    
    protected void finalize() {
        if ((coreStack != null) || (limitStack != null) || (remoteStack != null)) {
            Log.logWarning("plasmaCrawlNURL", "NURL stack closed by finalizer");
            close();
        }
    }
    
    public boolean notEmpty() {
        return coreStack.notEmpty() || limitStack.notEmpty() || remoteStack.notEmpty();
    }
    
    public boolean notEmptyLocal() {
        return coreStack.notEmpty() || limitStack.notEmpty();
    }
    
    public int size() {
        // this does not count the overhang stack size
        return ((coreStack == null) ? 0 : coreStack.size()) + ((limitStack == null) ? 0 : limitStack.size()) + ((remoteStack == null) ? 0 : remoteStack.size());
    }

    public boolean isEmpty() {
        if (coreStack == null) return true;
        if (!coreStack.isEmpty()) return false;
        if (!limitStack.isEmpty()) return false;
        if (!remoteStack.isEmpty()) return false;
        return true;
    }
    
    public int stackSize(final int stackType) {
        switch (stackType) {
            case STACK_TYPE_CORE:     return (coreStack == null) ? 0 : coreStack.size();
            case STACK_TYPE_LIMIT:    return (limitStack == null) ? 0 : limitStack.size();
            case STACK_TYPE_OVERHANG: return 0;
            case STACK_TYPE_REMOTE:   return (remoteStack == null) ? 0 : remoteStack.size();
            default: return -1;
        }
    }

    public boolean existsInStack(final byte[] urlhashb) {
        return
            coreStack.has(urlhashb) ||
            limitStack.has(urlhashb) ||
            //overhangStack.has(urlhashb) || 
            remoteStack.has(urlhashb);
    }
    
    public void push(final int stackType, final Request entry) {
        try {
            switch (stackType) {
                case STACK_TYPE_CORE:
                    coreStack.push(entry);
                    break;
                case STACK_TYPE_LIMIT:
                    limitStack.push(entry);
                    break;
                case STACK_TYPE_REMOTE:
                    remoteStack.push(entry);
                    break;
                default: break;
            }
        } catch (final Exception er) {
            Log.logException(er);
        }
    }

    public Request get(final byte[] urlhash) {
        Request entry = null;
        try {if ((entry = coreStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
        try {if ((entry = limitStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
        try {if ((entry = remoteStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
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
            HandleSet urlHashes = Base64Order.enhancedCoder.getHandleSet(12, 1);
            urlHashes.put(urlhashBytes);
            try {return coreStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {return limitStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            try {return remoteStack.remove(urlHashes) > 0;} catch (final IOException e) {}
            return false;
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
            return false;
        }
    }
    
    public int removeByProfileHandle(final String handle, final long timeout) throws RowSpaceExceededException {
        int removed = 0;
        try {removed += coreStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += limitStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += remoteStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        return removed;
    }
    
    public ArrayList<Request> top(final int stackType, final int count) {
        switch (stackType) {
            case STACK_TYPE_CORE:     return top(coreStack, count);
            case STACK_TYPE_LIMIT:    return top(limitStack, count);
            case STACK_TYPE_REMOTE:   return top(remoteStack, count);
            default: return null;
        }
    }
    
    public Request pop(final int stackType, final boolean delay, Map<byte[], Map<String, String>> profiles) throws IOException {
        switch (stackType) {
            case STACK_TYPE_CORE:     return pop(coreStack, delay, profiles);
            case STACK_TYPE_LIMIT:    return pop(limitStack, delay, profiles);
            case STACK_TYPE_REMOTE:   return pop(remoteStack, delay, profiles);
            default: return null;
        }
    }

    public void shift(final int fromStack, final int toStack, Map<byte[], Map<String, String>> profiles) {
        try {
            final Request entry = pop(fromStack, false, profiles);
            if (entry != null) push(toStack, entry);
        } catch (final IOException e) {
            return;
        }
    }

    public void clear(final int stackType) {
    	Log.logInfo("NoticedURL", "CLEARING STACK " + stackType);
        switch (stackType) {
                case STACK_TYPE_CORE:     coreStack.clear(); break;
                case STACK_TYPE_LIMIT:    limitStack.clear(); break;
                case STACK_TYPE_REMOTE:   remoteStack.clear(); break;
                default: return;
            }
    }
    
    private Request pop(final Balancer balancer, final boolean delay, Map<byte[], Map<String, String>> profiles) throws IOException {
        // this is a filo - pop
        int s;
        Request entry;
        int errors = 0;
        synchronized (balancer) {
            while ((s = balancer.size()) > 0) {
                entry = balancer.pop(delay, profiles);
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
    
    private ArrayList<Request> top(final Balancer balancer, int count) {
        // this is a filo - top
        if (count > balancer.size()) count = balancer.size();
        ArrayList<Request> list;
        list = balancer.top(count);
        return list;
    }
    
    public Iterator<Request> iterator(final int stackType) {
        // returns an iterator of plasmaCrawlBalancerEntry Objects
        try {switch (stackType) {
            case STACK_TYPE_CORE:     return coreStack.iterator();
            case STACK_TYPE_LIMIT:    return limitStack.iterator();
            case STACK_TYPE_REMOTE:   return remoteStack.iterator();
            default: return null;
        }} catch (final IOException e) {
            return new HashSet<Request>().iterator();
        }
    }
    
}
