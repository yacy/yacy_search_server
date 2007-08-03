// plasmaNURL.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// NURL - noticed (known but not loaded) URL's

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

public class plasmaCrawlNURL {
    
    public static final int STACK_TYPE_NULL     =  0; // do not stack
    public static final int STACK_TYPE_CORE     =  1; // put on local stack
    public static final int STACK_TYPE_LIMIT    =  2; // put on global stack
    public static final int STACK_TYPE_OVERHANG =  3; // put on overhang stack; links that are known but not crawled
    public static final int STACK_TYPE_REMOTE   =  4; // put on remote-triggered stack
    public static final int STACK_TYPE_IMAGE    = 11; // put on image stack
    public static final int STACK_TYPE_MOVIE    = 12; // put on movie stack
    public static final int STACK_TYPE_MUSIC    = 13; // put on music stack

    private static final long minimumDelta  =    500; // the minimum time difference between access of the same domain
    private static final long maximumDomAge =  60000; // the maximum age of a domain until it is used for another crawl attempt
    
    private final plasmaCrawlBalancer coreStack;      // links found by crawling to depth-1
    private final plasmaCrawlBalancer limitStack;     // links found by crawling at target depth
    private final plasmaCrawlBalancer remoteStack;    // links from remote crawl orders
    //private final plasmaCrawlBalancer overhangStack;  // links found by crawling at depth+1
    //private kelondroStack imageStack;     // links pointing to image resources
    //private kelondroStack movieStack;     // links pointing to movie resources
    //private kelondroStack musicStack;     // links pointing to music resources

    public plasmaCrawlNURL(File cachePath) {
        super();
        coreStack = new plasmaCrawlBalancer(cachePath, "urlNoticeCoreStack");
        limitStack = new plasmaCrawlBalancer(cachePath, "urlNoticeLimitStack");
        //overhangStack = new plasmaCrawlBalancer(overhangStackFile);
        remoteStack = new plasmaCrawlBalancer(cachePath, "urlNoticeRemoteStack");
    }

    public void close() {
        coreStack.close();
        limitStack.close();
        //overhangStack.close();
        remoteStack.close();
    }
    
    public int size() {
        // this does not count the overhang stack size
        return coreStack.size()  + limitStack.size() + remoteStack.size();
    }

    public int stackSize(int stackType) {
        switch (stackType) {
            case STACK_TYPE_CORE:     return coreStack.size();
            case STACK_TYPE_LIMIT:    return limitStack.size();
            case STACK_TYPE_OVERHANG: return 0;
            case STACK_TYPE_REMOTE:   return remoteStack.size();
            default: return -1;
        }
    }

    public boolean existsInStack(String urlhash) {
        return
            coreStack.has(urlhash) ||
            limitStack.has(urlhash) ||
            //overhangStack.has(urlhash) || 
            remoteStack.has(urlhash);
    }
    
    public void push(int stackType, plasmaCrawlEntry entry) {
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
        } catch (IOException er) {}
    }

    public plasmaCrawlEntry get(String urlhash) {
        plasmaCrawlEntry entry = null;
        try {if ((entry = coreStack.get(urlhash)) != null) return entry;} catch (IOException e) {}
        try {if ((entry = limitStack.get(urlhash)) != null) return entry;} catch (IOException e) {}
        try {if ((entry = remoteStack.get(urlhash)) != null) return entry;} catch (IOException e) {}
        return null;
    }
    
    public plasmaCrawlEntry remove(String urlhash) {
        plasmaCrawlEntry entry = null;
        try {if ((entry = coreStack.remove(urlhash)) != null) return entry;} catch (IOException e) {}
        try {if ((entry = limitStack.remove(urlhash)) != null) return entry;} catch (IOException e) {}
        try {if ((entry = remoteStack.remove(urlhash)) != null) return entry;} catch (IOException e) {}
        return null;
    }
    
    public plasmaCrawlEntry[] top(int stackType, int count) {
        switch (stackType) {
            case STACK_TYPE_CORE:     return top(coreStack, count);
            case STACK_TYPE_LIMIT:    return top(limitStack, count);
            case STACK_TYPE_REMOTE:   return top(remoteStack, count);
            default: return null;
        }
    }
    
    public plasmaCrawlEntry pop(int stackType) throws IOException {
        switch (stackType) {
            case STACK_TYPE_CORE:     return pop(coreStack);
            case STACK_TYPE_LIMIT:    return pop(limitStack);
            case STACK_TYPE_REMOTE:   return pop(remoteStack);
            default: return null;
        }
    }

    public void shift(int fromStack, int toStack) {
        try {
            plasmaCrawlEntry entry = pop(fromStack);
            if (entry != null) push(toStack, entry);
        } catch (IOException e) {
            return;
        }
    }

    public void clear(int stackType) {
        switch (stackType) {
                case STACK_TYPE_CORE:     coreStack.clear(); break;
                case STACK_TYPE_LIMIT:    limitStack.clear(); break;
                case STACK_TYPE_REMOTE:   remoteStack.clear(); break;
                default: return;
            }
    }
    
    private plasmaCrawlEntry pop(plasmaCrawlBalancer balancer) throws IOException {
        // this is a filo - pop
        int s;
        plasmaCrawlEntry entry;
        synchronized (balancer) {
        while ((s = balancer.size()) > 0) {
            entry = balancer.pop(minimumDelta, maximumDomAge);
            if (entry == null) {
                if (s > balancer.size()) continue;
                int aftersize = balancer.size();
                balancer.clear(); // the balancer is broken and cannot shrink
                throw new IOException("entry is null, balancer cannot shrink (bevore pop = " + s + ", after pop = " + aftersize + "); reset of balancer");
            }
            return entry;
        }
        }
        throw new IOException("balancer stack is empty");
    }
    
    private plasmaCrawlEntry[] top(plasmaCrawlBalancer balancer, int count) {
        // this is a filo - top
        if (count > balancer.size()) count = balancer.size();
        ArrayList list = new ArrayList(count);
        for (int i = 0; i < count; i++) {
            try {
                plasmaCrawlEntry entry = balancer.top(i);
                if (entry == null) break;
                list.add(entry);
            } catch (IOException e) {
                break;
            }
        }
        return (plasmaCrawlEntry[]) list.toArray(new plasmaCrawlEntry[list.size()]);
    }
    
    public Iterator iterator(int stackType) {
        // returns an iterator of plasmaCrawlBalancerEntry Objects
        try {switch (stackType) {
        case STACK_TYPE_CORE:     return coreStack.iterator();
        case STACK_TYPE_LIMIT:    return limitStack.iterator();
        case STACK_TYPE_REMOTE:   return remoteStack.iterator();
        default: return null;
        }} catch (IOException e) {
            return new HashSet().iterator();
        }
    }
    
}
