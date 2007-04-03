// plasmaCrawlBalancer.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created: 24.09.2005
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

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroCache;
import de.anomic.kelondro.kelondroFlexTable;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroRecords;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroStack;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeedDB;

public class plasmaCrawlBalancer {
    
    private static final String stackSuffix = "7.stack";
    private static final String indexSuffix = "7.db";

    // a shared domainAccess map for all balancers
    private static final Map domainAccess = Collections.synchronizedMap(new HashMap());
    
    // definition of payload for fileStack
    private static final kelondroRow stackrow = new kelondroRow("byte[] urlhash-" + yacySeedDB.commonHashLength, kelondroBase64Order.enhancedCoder, 0);
    
    // class variables
    private ArrayList     urlRAMStack;     // a list that is flused first
    private kelondroStack urlFileStack;    // a file with url hashes
    private kelondroIndex urlFileIndex;
    private HashMap       domainStacks;    // a map from domain name part to Lists with url hashs
    private File          cacheStacksPath;
    private String        stackname;
    private boolean       top;             // to alternate between top and bottom of the file stack
    
    public plasmaCrawlBalancer(File cachePath, String stackname) {
        this.cacheStacksPath = cachePath;
        this.stackname = stackname;
        File stackFile = new File(cachePath, stackname + stackSuffix);
        urlFileStack    = kelondroStack.open(stackFile, stackrow);
        domainStacks = new HashMap();
        urlRAMStack     = new ArrayList();
        top = true;
        
        // create a stack for newly entered entries
        if (!(cachePath.exists())) cachePath.mkdir(); // make the path
        openFileIndex();
    }

    public synchronized void close() {
        while (sizeDomainStacks() > 0) flushOnceDomStacks(0, true); // flush to ram, because the ram flush is optimized
        try { flushAllRamStack(); } catch (IOException e) {}
        if (urlFileIndex != null) {
            urlFileIndex.close();
            urlFileIndex = null;
        }
        if (urlFileStack != null) {
            urlFileStack.close();
            urlFileStack = null;
        }
    }
    
    public void finalize() {
        if (urlFileStack != null) close();
    }
    
    public synchronized void clear() {
        urlFileStack = kelondroStack.reset(urlFileStack);
        domainStacks.clear();
        urlRAMStack.clear();
        resetFileIndex();
    }
    

    private void openFileIndex() {
        cacheStacksPath.mkdirs();
        try {
            urlFileIndex = new kelondroCache(new kelondroFlexTable(cacheStacksPath, stackname + indexSuffix, -1, plasmaCrawlEntry.rowdef), true, false);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
    
    private void resetFileIndex() {
        if (urlFileIndex != null) {
            urlFileIndex.close();
            urlFileIndex = null;
            kelondroFlexTable.delete(cacheStacksPath, stackname + indexSuffix);
            //File cacheFile = new File(cacheStacksPath, stackname + indexSuffix);
             //cacheFile.delete();
        }
        openFileIndex();
    }
    
    public synchronized plasmaCrawlEntry get(String urlhash) throws IOException {
       kelondroRow.Entry entry = urlFileIndex.get(urlhash.getBytes());
       if (entry == null) return null;
       return new plasmaCrawlEntry(entry);
    }
    
    public synchronized plasmaCrawlEntry remove(String urlhash) throws IOException {
        // this method is only here, because so many import/export methods need it
        // and it was implemented in the previous architecture
        // however, usage is not recommendet
       kelondroRow.Entry entry = urlFileIndex.remove(urlhash.getBytes());
       if (entry == null) return null;
       
       // now delete that thing also from the queues
       
       // iterate through the RAM stack
       Iterator i = urlRAMStack.iterator();
       String h;
       boolean removed = false;
       while (i.hasNext()) {
           h = (String) i.next();
           if (h.equals(urlhash)) {
               i.remove();
               removed = true;
               break;
           }
       }
       if ((kelondroRecords.debugmode) && (!removed)) {
           serverLog.logWarning("PLASMA BALANCER", "remove: not found urlhash " + urlhash + " in " + stackname);
       }
       
       // we cannot iterate through the file stack, because the stack iterator
       // has not yet a delete method implemented. It would also be a bad idea
       // to do that, it would make too much IO load
       // instead, the top/pop methods that aquire elements from the stack, that
       // cannot be found in the urlFileIndex must handle that case silently
       
       return new plasmaCrawlEntry(entry);
    }
    
    public boolean has(String urlhash) {
        try {
            return urlFileIndex.has(urlhash.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public synchronized int size() {
        int componentsize = urlFileStack.size() + urlRAMStack.size() + sizeDomainStacks();
        try {
            if (componentsize != urlFileIndex.size()) {
                // hier ist urlIndexFile.size() immer grš§er. warum?
                if (kelondroRecords.debugmode) {
                    serverLog.logWarning("PLASMA BALANCER", "size operation wrong in " + stackname + " - componentsize = " + componentsize + ", urlFileIndex.size() = " + urlFileIndex.size());
                }
                if ((componentsize == 0) && (urlFileIndex.size() > 0)) {
                    resetFileIndex();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return componentsize;
    }
    
    private int sizeDomainStacks() {
        if (domainStacks == null) return 0;
        int sum = 0;
        Iterator i = domainStacks.values().iterator();
        while (i.hasNext()) sum += ((LinkedList) i.next()).size();
        return sum;
    }
    
    private void flushOnceDomStacks(int minimumleft, boolean ram) {
        // takes one entry from every domain stack and puts it on the ram or file stack
        // the minimumleft value is a limit for the number of entries that should be left
        if (domainStacks.size() == 0) return;
        Iterator i = domainStacks.entrySet().iterator();
        Map.Entry entry;
        LinkedList list;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            list = (LinkedList) entry.getValue();
            if (list.size() > minimumleft) {
                if (ram) {
                    urlRAMStack.add(list.removeFirst());
                } else try {
                    urlFileStack.push(urlFileStack.row().newEntry(new byte[][]{((String) list.removeFirst()).getBytes()}));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (list.size() == 0) i.remove();
        }
    }
    
    private void flushAllRamStack() throws IOException {
        // this flushes only the ramStack to the fileStack, but does not flush the domainStacks
        for (int i = 0; i < urlRAMStack.size() / 2; i++) {
            urlFileStack.push(urlFileStack.row().newEntry(new byte[][]{((String) urlRAMStack.get(i)).getBytes()}));
            urlFileStack.push(urlFileStack.row().newEntry(new byte[][]{((String) urlRAMStack.get(urlRAMStack.size() - i - 1)).getBytes()}));
        }
        if (urlRAMStack.size() % 2 == 1) 
            urlFileStack.push(urlFileStack.row().newEntry(new byte[][]{((String) urlRAMStack.get(urlRAMStack.size() / 2)).getBytes()}));
    }
    
    public synchronized void push(plasmaCrawlEntry entry) throws IOException {
        assert entry != null;
        if (urlFileIndex.has(entry.urlhash().getBytes())) {
            serverLog.logWarning("PLASMA BALANCER", "double-check has failed for urlhash " + entry.urlhash()  + " in " + stackname + " - fixed");
            return;
        }
        
        // extend domain stack
        String dom = entry.urlhash().substring(6);
        LinkedList domainList = (LinkedList) domainStacks.get(dom);
        if (domainList == null) {
            // create new list
            domainList = new LinkedList();
            domainList.add(entry.urlhash());
            domainStacks.put(dom, domainList);
        } else {
            // extend existent domain list
            domainList.addLast(entry.urlhash());
        }
        
        // add to index
        urlFileIndex.put(entry.toRow());
        
        // check size of domainStacks and flush
        if ((domainStacks.size() > 20) || (sizeDomainStacks() > 1000)) {
            flushOnceDomStacks(1, urlRAMStack.size() < 100); // when the ram stack is small, flush it there
        }
    }
    
    public synchronized plasmaCrawlEntry pop(long minimumDelta, long maximumAge) throws IOException {
        // returns an url-hash from the stack and ensures minimum delta times
        // we have 3 sources to choose from: the ramStack, the domainStacks and the fileStack
        
        String result = null; // the result
        
        // 1st: check ramStack
        if (urlRAMStack.size() > 0) {
            result = (String) urlRAMStack.remove(0);
        }
        
        // 2nd-a: check domainStacks for latest arrivals
        if (result == null) {
            // we select specific domains that have not been used for a long time
            // i.e. 60 seconds. Latest arrivals that have not yet been crawled
            // fit also in that scheme
            Iterator i = domainStacks.entrySet().iterator();
            Map.Entry entry;
            String domhash;
            long delta, maxdelta = 0;
            String maxhash = null;
            LinkedList domlist;
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                domhash = (String) entry.getKey();
                delta = lastAccessDelta(domhash);
                if (delta == Integer.MAX_VALUE) {
                    // a brand new domain - we take it
                    domlist = (LinkedList) entry.getValue();
                    result = (String) domlist.removeFirst();
                    if (domlist.size() == 0) i.remove();
                    break;
                }
                if (delta > maxdelta) {
                    maxdelta = delta;
                    maxhash = domhash;
                }
            }
            if (maxdelta > maximumAge) {
                // success - we found an entry from a domain that has not been used for a long time
                domlist = (LinkedList) domainStacks.get(maxhash);
                result = (String) domlist.removeFirst();
                if (domlist.size() == 0) domainStacks.remove(maxhash);
            }
        }
        
        // 2nd-b: check domainStacks for best match between stack size and retrieval time
        if (result == null) {
            // we order all domains by the number of entries per domain
            // then we iterate through these domains in descending entry order
            // and that that one, that has a delta > minimumDelta
            Iterator i = domainStacks.entrySet().iterator();
            Map.Entry entry;
            String domhash;
            LinkedList domlist;
            TreeMap hitlist = new TreeMap();
            int count = 0;
            // first collect information about sizes of the domain lists
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                domhash = (String) entry.getKey();
                domlist = (LinkedList) entry.getValue();
                hitlist.put(new Integer(domlist.size() * 100 + count++), domhash);
            }
            
            // now iterate in descending order an fetch that one,
            // that is acceptable by the minimumDelta constraint
            long delta;
            String maxhash = null;
            while (hitlist.size() > 0) {
                domhash = (String) hitlist.remove(hitlist.lastKey());
                if (maxhash == null) maxhash = domhash; // remember first entry
                delta = lastAccessDelta(domhash);
                if (delta > minimumDelta) {
                    domlist = (LinkedList) domainStacks.get(domhash);
                    result = (String) domlist.removeFirst();
                    if (domlist.size() == 0) domainStacks.remove(domhash);
                    break;
                }
            }
            
            // if we did yet not choose any entry, we simply take that one with the most entries
            if ((result == null) && (maxhash != null)) {
                domlist = (LinkedList) domainStacks.get(maxhash);
                result = (String) domlist.removeFirst();
                if (domlist.size() == 0) domainStacks.remove(maxhash);
            }
        }
        
        // 3rd: take entry from file
        if ((result == null) && (urlFileStack.size() > 0)) {
            kelondroRow.Entry nextentry = (top) ? urlFileStack.top() : urlFileStack.bot();
            if (nextentry == null) {
                // emergency case: this means that something with the stack organization is wrong
                // the file appears to be broken. We kill the file.
                kelondroStack.reset(urlFileStack);
                serverLog.logSevere("PLASMA BALANCER", "get() failed to fetch entry from file stack. reset stack file.");
            } else {
                String nexthash = new String(nextentry.getColBytes(0));

                // check if the time after retrieval of last hash from same
                // domain is not shorter than the minimumDelta
                long delta = lastAccessDelta(nexthash);
                if (delta > minimumDelta) {
                    // the entry is fine
                    result = new String((top) ? urlFileStack.pop().getColBytes(0) : urlFileStack.pot().getColBytes(0));
                } else {
                    // try other entry
                    result = new String((top) ? urlFileStack.pot().getColBytes(0) : urlFileStack.pop().getColBytes(0));
                    delta = lastAccessDelta(result);
                }
            }
            top = !top; // alternate top/bottom
        }
        
        // check case where we did not found anything
        if (result == null) {
            serverLog.logSevere("PLASMA BALANCER", "get() was not able to find a valid urlhash - total size = " + size() + ", fileStack.size() = " + urlFileStack.size() + ", ramStack.size() = " + urlRAMStack.size() + ", domainStacks.size() = " + domainStacks.size());
            return null;
        }
        
        // finally: check minimumDelta and if necessary force a sleep
        long delta = lastAccessDelta(result);
        if (delta < minimumDelta) {
            // force a busy waiting here
            // in best case, this should never happen if the balancer works propertly
            // this is only to protect against the worst case, where the crawler could
            // behave in a DoS-manner
            long sleeptime = minimumDelta - delta;
            try {synchronized(this) { this.wait(sleeptime); }} catch (InterruptedException e) {}
        }
        
        // update statistical data
        domainAccess.put(result.substring(6), new Long(System.currentTimeMillis()));
        kelondroRow.Entry entry = urlFileIndex.remove(result.getBytes());
        if (entry == null) return null;
        return new plasmaCrawlEntry(entry);
    }
    
    private long lastAccessDelta(String hash) {
        assert hash != null;
        Long lastAccess = (Long) domainAccess.get((hash.length() > 6) ? hash.substring(6) : hash);
        if (lastAccess == null) return Long.MAX_VALUE; // never accessed
        return System.currentTimeMillis() - lastAccess.longValue();
    }
    
    public synchronized plasmaCrawlEntry top(int dist) throws IOException {
        // if we need to flush anything, then flush the domain stack first,
        // to avoid that new urls get hidden by old entries from the file stack
        while ((sizeDomainStacks() > 0) && (urlRAMStack.size() <= dist)) {
            // flush only that much as we need to display
            flushOnceDomStacks(0, true); 
        }
        while ((urlRAMStack.size() <= dist) && (urlFileStack.size() > 0)) {
            // flush some entries from disc to ram stack
            try {
                urlRAMStack.add(new String(urlFileStack.pop().getColBytes(0)));
            } catch (IOException e) {
                break;
            }
        }
        if (dist >= urlRAMStack.size()) return null;
        String urlhash = (String) urlRAMStack.get(dist);
        kelondroRow.Entry entry = urlFileIndex.get(urlhash.getBytes());
        if (entry == null) {
            if (kelondroRecords.debugmode) serverLog.logWarning("PLASMA BALANCER", "no entry in index for urlhash " + urlhash);
            return null;
        }
        return new plasmaCrawlEntry(entry);
    }

    public Iterator iterator() throws IOException {
        return new EntryIterator();
    }
    
    public class EntryIterator implements Iterator {

        Iterator rowIterator;
        
        public EntryIterator() throws IOException {
            rowIterator = urlFileIndex.rows(true, null);
        }
        
        public boolean hasNext() {
            return (rowIterator == null) ? false : rowIterator.hasNext();
        }

        public Object next() {
            kelondroRow.Entry entry = (kelondroRow.Entry) rowIterator.next();
            try {
                return (entry == null) ? null : new plasmaCrawlEntry(entry);
            } catch (IOException e) {
                rowIterator = null;
                return null;
            }
        }

        public void remove() {
            if (rowIterator != null) rowIterator.remove();
        }
        
    }
    
}
