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
    
    public enum StackType {
        NULL, CORE, LIMIT, OVERHANG, REMOTE, NOLOAD, IMAGE, MOVIE, MUSIC;
    }

    public static final long minimumLocalDeltaInit  =  10; // the minimum time difference between access of the same local domain
    public static final long minimumGlobalDeltaInit = 500; // the minimum time difference between access of the same global domain
    
    private Balancer coreStack;      // links found by crawling to depth-1
    private Balancer limitStack;     // links found by crawling at target depth
    private Balancer remoteStack;    // links from remote crawl orders
    private Balancer noloadStack;    // links that are not passed to a loader; the index will be generated from the Request entry
    
    public NoticedURL(
    		final File cachePath,
            final boolean useTailCache,
            final boolean exceed134217727) {
        Log.logInfo("NoticedURL", "CREATING STACKS at " + cachePath.toString());
        this.coreStack = new Balancer(cachePath, "urlNoticeCoreStack", minimumLocalDeltaInit, minimumGlobalDeltaInit, useTailCache, exceed134217727);
        this.limitStack = new Balancer(cachePath, "urlNoticeLimitStack", minimumLocalDeltaInit, minimumGlobalDeltaInit, useTailCache, exceed134217727);
        //overhangStack = new plasmaCrawlBalancer(overhangStackFile);
        this.remoteStack = new Balancer(cachePath, "urlNoticeRemoteStack", minimumLocalDeltaInit, minimumGlobalDeltaInit, useTailCache, exceed134217727);
        this.noloadStack = new Balancer(cachePath, "urlNoticeNoLoadStack", minimumLocalDeltaInit, minimumGlobalDeltaInit, useTailCache, exceed134217727);
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
        coreStack.clear();
        limitStack.clear();
        remoteStack.clear();
        noloadStack.clear();
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
        if (noloadStack != null) {
            noloadStack.close();
            noloadStack = null;
        }
    }
    
    protected void finalize() {
        if ((coreStack != null) || (limitStack != null) || (remoteStack != null)) {
            Log.logWarning("plasmaCrawlNURL", "NURL stack closed by finalizer");
            close();
        }
    }
    
    public boolean notEmpty() {
        return coreStack.notEmpty() || limitStack.notEmpty() || remoteStack.notEmpty() || noloadStack.notEmpty();
    }
    
    public boolean notEmptyLocal() {
        return coreStack.notEmpty() || limitStack.notEmpty() || noloadStack.notEmpty();
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
        if (!noloadStack.isEmpty()) return false;
        return true;
    }
    
    public int stackSize(final StackType stackType) {
        switch (stackType) {
            case NOLOAD:    return (noloadStack == null) ? 0 : noloadStack.size();
            case CORE:     return (coreStack == null) ? 0 : coreStack.size();
            case LIMIT:    return (limitStack == null) ? 0 : limitStack.size();
            case OVERHANG: return 0;
            case REMOTE:   return (remoteStack == null) ? 0 : remoteStack.size();
            default: return -1;
        }
    }

    public boolean existsInStack(final byte[] urlhashb) {
        return
            coreStack.has(urlhashb) ||
            limitStack.has(urlhashb) ||
            //overhangStack.has(urlhashb) || 
            remoteStack.has(urlhashb) ||
            noloadStack.has(urlhashb);
    }
    
    public void push(final StackType stackType, final Request entry) {
        try {
            switch (stackType) {
                case CORE:
                    coreStack.push(entry);
                    break;
                case LIMIT:
                    limitStack.push(entry);
                    break;
                case REMOTE:
                    remoteStack.push(entry);
                    break;
                case NOLOAD:
                    noloadStack.push(entry);
                    break;
                default: break;
            }
        } catch (final Exception er) {
            Log.logException(er);
        }
    }

    public Request get(final byte[] urlhash) {
        Request entry = null;
        try {if ((entry = noloadStack.get(urlhash)) != null) return entry;} catch (final IOException e) {}
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
            try {return noloadStack.remove(urlHashes) > 0;} catch (final IOException e) {}
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
        try {removed += noloadStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += coreStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += limitStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        try {removed += remoteStack.removeAllByProfileHandle(handle, timeout);} catch (final IOException e) {}
        return removed;
    }
    
    public ArrayList<Request> top(final StackType stackType, final int count) {
        switch (stackType) {
            case CORE:     return top(coreStack, count);
            case LIMIT:    return top(limitStack, count);
            case REMOTE:   return top(remoteStack, count);
            case NOLOAD:   return top(noloadStack, count);
            default: return null;
        }
    }
    
    public Request pop(final StackType stackType, final boolean delay, Map<byte[], Map<String, String>> profiles) throws IOException {
        switch (stackType) {
            case CORE:     return pop(coreStack, delay, profiles);
            case LIMIT:    return pop(limitStack, delay, profiles);
            case REMOTE:   return pop(remoteStack, delay, profiles);
            case NOLOAD:   return pop(noloadStack, false, profiles);
            default: return null;
        }
    }

    public void shift(final StackType fromStack, final StackType toStack, Map<byte[], Map<String, String>> profiles) {
        try {
            final Request entry = pop(fromStack, false, profiles);
            if (entry != null) push(toStack, entry);
        } catch (final IOException e) {
            return;
        }
    }

    public void clear(final StackType stackType) {
    	Log.logInfo("NoticedURL", "CLEARING STACK " + stackType);
        switch (stackType) {
                case CORE:     coreStack.clear(); break;
                case LIMIT:    limitStack.clear(); break;
                case REMOTE:   remoteStack.clear(); break;
                case NOLOAD:   noloadStack.clear(); break;
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
    
    public Iterator<Request> iterator(final StackType stackType) {
        // returns an iterator of plasmaCrawlBalancerEntry Objects
        try {switch (stackType) {
            case CORE:     return coreStack.iterator();
            case LIMIT:    return limitStack.iterator();
            case REMOTE:   return remoteStack.iterator();
            case NOLOAD:   return noloadStack.iterator();
            default: return null;
        }} catch (final IOException e) {
            return new HashSet<Request>().iterator();
        }
    }
    
}
