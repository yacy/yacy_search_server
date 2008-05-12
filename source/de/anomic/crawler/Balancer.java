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

package de.anomic.crawler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroAbstractRecords;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroEcoTable;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroStack;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeedDB;

public class Balancer {
    
    private static final String stackSuffix = "9.stack";
    private static final String indexSuffix = "9.db";
    private static final int EcoFSBufferSize = 200;

    // a shared domainAccess map for all balancers
    private static final Map<String, domaccess> domainAccess = Collections.synchronizedMap(new HashMap<String, domaccess>());
    
    // definition of payload for fileStack
    private static final kelondroRow stackrow = new kelondroRow("byte[] urlhash-" + yacySeedDB.commonHashLength, kelondroBase64Order.enhancedCoder, 0);
    
    // class variables
    private ArrayList<String>                   urlRAMStack;     // a list that is flushed first
    private kelondroStack                       urlFileStack;    // a file with url hashes
    kelondroIndex                               urlFileIndex;
    private HashMap<String, LinkedList<String>> domainStacks;    // a map from domain name part to Lists with url hashs
    private File                                cacheStacksPath;
    private String                              stackname;
    private boolean                             top;             // to alternate between top and bottom of the file stack
    private boolean                             fullram;

    public static class domaccess {
    	long time;
    	int count;
    	public domaccess() {
    		this.time = System.currentTimeMillis();
    		this.count = 0;
    	}
    	public void update() {
    		this.time = System.currentTimeMillis();
    		this.count++;
    	}
    	public long time() {
    		return this.time;
    	}
    	public int count() {
    		return this.count;
    	}
    }
    
    public Balancer(File cachePath, String stackname, boolean fullram) {
        this.cacheStacksPath = cachePath;
        this.stackname = stackname;
        File stackFile = new File(cachePath, stackname + stackSuffix);
        this.urlFileStack   = kelondroStack.open(stackFile, stackrow);
        this.domainStacks   = new HashMap<String, LinkedList<String>>();
        this.urlRAMStack    = new ArrayList<String>();
        this.top            = true;
        this.fullram        = fullram;
        
        // create a stack for newly entered entries
        if (!(cachePath.exists())) cachePath.mkdir(); // make the path
        openFileIndex();
    }

    public synchronized void close() {
        while (domainStacksNotEmpty()) flushOnceDomStacks(0, true); // flush to ram, because the ram flush is optimized
        size();
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
        if (urlFileStack != null) {
            serverLog.logWarning("plasmaCrawlBalancer", "crawl stack " + stackname + " closed by finalizer");
            close();
        }
    }
    
    public synchronized void clear() {
        urlFileStack = kelondroStack.reset(urlFileStack);
        domainStacks.clear();
        urlRAMStack.clear();
        resetFileIndex();
    }
    
    private void openFileIndex() {
        cacheStacksPath.mkdirs();
        urlFileIndex = new kelondroEcoTable(new File(cacheStacksPath, stackname + indexSuffix), CrawlEntry.rowdef, (fullram) ? kelondroEcoTable.tailCacheUsageAuto : kelondroEcoTable.tailCacheDenyUsage, EcoFSBufferSize, 0);
    }
    
    private void resetFileIndex() {
        if (urlFileIndex != null) {
            urlFileIndex.close();
            urlFileIndex = null;
            new File(cacheStacksPath, stackname + indexSuffix).delete();
        }
        openFileIndex();
    }
    
    public synchronized CrawlEntry get(String urlhash) throws IOException {
        assert urlhash != null;
        kelondroRow.Entry entry = urlFileIndex.get(urlhash.getBytes());
       if (entry == null) return null;
       return new CrawlEntry(entry);
    }
    
    public synchronized int removeAllByProfileHandle(String profileHandle, long timeout) throws IOException {
        // removes all entries with a specific profile hash.
        // this may last some time
        // returns number of deletions
        
        // first find a list of url hashes that shall be deleted
        Iterator<kelondroRow.Entry> i = urlFileIndex.rows(true, null);
        HashSet<String> urlHashes = new HashSet<String>();
        kelondroRow.Entry rowEntry;
        CrawlEntry crawlEntry;
        long terminate = (timeout > 0) ? System.currentTimeMillis() + timeout : Long.MAX_VALUE;
        while (i.hasNext() && (System.currentTimeMillis() < terminate)) {
            rowEntry = (kelondroRow.Entry) i.next();
            crawlEntry = new CrawlEntry(rowEntry);
            if (crawlEntry.profileHandle().equals(profileHandle)) {
                urlHashes.add(crawlEntry.url().hash());
            }
        }
        
        // then delete all these urls from the queues and the file index
        return this.remove(urlHashes);
    }
    
    /**
     * this method is only here, because so many import/export methods need it
       and it was implemented in the previous architecture
       however, usage is not recommended
     * @param urlHashes, a list of hashes that shall be removed
     * @return number of entries that had been removed
     * @throws IOException
     */
    public synchronized int remove(HashSet<String> urlHashes) throws IOException {
        int s = urlFileIndex.size();
        int removedCounter = 0;
        for (String urlhash: urlHashes) {
            kelondroRow.Entry entry = urlFileIndex.remove(urlhash.getBytes(), false);
            if (entry != null) removedCounter++;
        }
        if (removedCounter == 0) return 0;
        assert urlFileIndex.size() + removedCounter == s : "urlFileIndex.size() = " + urlFileIndex.size() + ", s = " + s;
       
        // now delete these hashes also from the queues

        // iterate through the RAM stack
        Iterator<String> i = urlRAMStack.iterator();
        String h;
        while (i.hasNext()) {
           h = (String) i.next();
           if (urlHashes.contains(h)) i.remove();
       }
       
       // iterate through the file stack
       // in general this is a bad idea. But this can only be avoided by avoidance of this method
       Iterator<kelondroRow.Entry> j = urlFileStack.stackIterator(true);
       while (j.hasNext()) {
           h = new String(j.next().getColBytes(0));
           if (urlHashes.contains(h)) j.remove();
       }
       
       return removedCounter;
    }
    
    public synchronized boolean has(String urlhash) {
        try {
            return urlFileIndex.has(urlhash.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean notEmpty() {
        // alternative method to the property size() > 0
        // this is better because it may avoid synchronized access to domain stack summarization
        return urlRAMStack.size() > 0 || urlFileStack.size() > 0 || domainStacksNotEmpty();
    }
    
    public synchronized int size() {
        int componentsize = urlFileStack.size() + urlRAMStack.size() + sizeDomainStacks();
        if (componentsize != urlFileIndex.size()) {
		    // here is urlIndexFile.size() always smaller. why?
		    if (kelondroAbstractRecords.debugmode) {
		        serverLog.logWarning("BALANCER", "size wrong in " + stackname +
		                " - urlFileIndex = " + urlFileIndex.size() +
		                ", componentsize = " + componentsize +
                        " = (urlFileStack = " + urlFileStack.size() +
		                ", urlRAMStack = " + urlRAMStack.size() +
		                ", sizeDomainStacks = " + sizeDomainStacks() + ")");
		    }
		    if ((componentsize == 0) && (urlFileIndex.size() > 0)) {
		        resetFileIndex();
		    }
		}
        return componentsize;
    }
    
    private boolean domainStacksNotEmpty() {
        if (domainStacks == null) return false;
        synchronized (domainStacks) {
            Iterator<LinkedList<String>> i = domainStacks.values().iterator();
            while (i.hasNext()) {
                if (i.next().size() > 0) return true;
            }
        }
        return false;
    }
    
    private int sizeDomainStacks() {
        if (domainStacks == null) return 0;
        int sum = 0;
        synchronized (domainStacks) {
            Iterator<LinkedList<String>> i = domainStacks.values().iterator();
            while (i.hasNext()) sum += i.next().size();
        }
        return sum;
    }
    
    private void flushOnceDomStacks(int minimumleft, boolean ram) {
        // takes one entry from every domain stack and puts it on the ram or file stack
        // the minimumleft value is a limit for the number of entries that should be left
        if (domainStacks.size() == 0) return;
        synchronized (domainStacks) {
            Iterator<Map.Entry<String, LinkedList<String>>> i = domainStacks.entrySet().iterator();
            Map.Entry<String, LinkedList<String>> entry;
            LinkedList<String> list;
            while (i.hasNext()) {
                entry = i.next();
                list = entry.getValue();
                if (list.size() > minimumleft) {
                    if (ram) {
                        urlRAMStack.add(list.removeFirst());
                    } else try {
                        urlFileStack.push(urlFileStack.row().newEntry(new byte[][] { ((String) list.removeFirst()).getBytes() }));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (list.size() == 0)  i.remove();
            }
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
    
    public synchronized void push(CrawlEntry entry) throws IOException {
        assert entry != null;
        if (urlFileIndex.has(entry.url().hash().getBytes())) {
            serverLog.logWarning("PLASMA BALANCER", "double-check has failed for urlhash " + entry.url().hash()  + " in " + stackname + " - fixed");
            return;
        }
        
        // extend domain stack
        String dom = entry.url().hash().substring(6);
        LinkedList<String> domainList = domainStacks.get(dom);
        if (domainList == null) {
            // create new list
            domainList = new LinkedList<String>();
            synchronized (domainStacks) {
                domainList.add(entry.url().hash());
                domainStacks.put(dom, domainList);
            }
        } else {
            // extend existent domain list
            domainList.addLast(entry.url().hash());
        }
        
        // add to index
        urlFileIndex.put(entry.toRow());
        
        // check size of domainStacks and flush
        if ((domainStacks.size() > 20) || (sizeDomainStacks() > 1000)) {
            flushOnceDomStacks(1, urlRAMStack.size() < 100); // when the ram stack is small, flush it there
        }
    }
    
    public synchronized CrawlEntry pop(long minimumLocalDelta, long minimumGlobalDelta, long maximumAge) throws IOException {
        // returns an url-hash from the stack and ensures minimum delta times
        // we have 3 sources to choose from: the ramStack, the domainStacks and the fileStack
        
        String result = null; // the result
        
        // 1st: check ramStack
        if (urlRAMStack.size() > 0) {
            result = (String) urlRAMStack.remove(0);
        }
        
        // 2nd-a: check domainStacks for latest arrivals
        if ((result == null) && (domainStacks.size() > 0)) synchronized (domainStacks) {
            // we select specific domains that have not been used for a long time
            // i.e. 60 seconds. Latest arrivals that have not yet been crawled
            // fit also in that scheme
            Iterator<Map.Entry<String, LinkedList<String>>> i = domainStacks.entrySet().iterator();
            Map.Entry<String, LinkedList<String>> entry;
            String domhash;
            long delta, maxdelta = 0;
            String maxhash = null;
            LinkedList<String> domlist;
            while (i.hasNext()) {
                entry = i.next();
                domhash = (String) entry.getKey();
                delta = lastAccessDelta(domhash);
                if (delta == Integer.MAX_VALUE) {
                    // a brand new domain - we take it
                    domlist = entry.getValue();
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
                domlist = domainStacks.get(maxhash);
                result = (String) domlist.removeFirst();
                if (domlist.size() == 0) domainStacks.remove(maxhash);
            }
        }
        
        // 2nd-b: check domainStacks for best match between stack size and retrieval time
        if ((result == null) && (domainStacks.size() > 0)) synchronized (domainStacks) {
            // we order all domains by the number of entries per domain
            // then we iterate through these domains in descending entry order
            // and that that one, that has a delta > minimumDelta
            Iterator<Map.Entry<String, LinkedList<String>>> i = domainStacks.entrySet().iterator();
            Map.Entry<String, LinkedList<String>> entry;
            String domhash;
            LinkedList<String> domlist;
            TreeMap<Integer, String> hitlist = new TreeMap<Integer, String>();
            int count = 0;
            // first collect information about sizes of the domain lists
            while (i.hasNext()) {
                entry = i.next();
                domhash = entry.getKey();
                domlist = entry.getValue();
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
                if (delta > minimumGlobalDelta) {
                    domlist = domainStacks.get(domhash);
                    result = (String) domlist.removeFirst();
                    if (domlist.size() == 0) domainStacks.remove(domhash);
                    break;
                }
            }
            
            // if we did yet not choose any entry, we simply take that one with the most entries
            if ((result == null) && (maxhash != null)) {
                domlist = domainStacks.get(maxhash);
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
                if (delta > minimumGlobalDelta) {
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
        assert delta >= 0: "delta = " + delta;
        int s = urlFileIndex.size();
        kelondroRow.Entry rowEntry = urlFileIndex.remove(result.getBytes(), false);
        assert (rowEntry == null) || (urlFileIndex.size() + 1 == s) : "urlFileIndex.size() = " + urlFileIndex.size() + ", s = " + s + ", result = " + result;
        if (rowEntry == null) {
            serverLog.logSevere("PLASMA BALANCER", "get() found a valid urlhash, but failed to fetch the corresponding url entry - total size = " + size() + ", fileStack.size() = " + urlFileStack.size() + ", ramStack.size() = " + urlRAMStack.size() + ", domainStacks.size() = " + domainStacks.size());
            return null;
        } else {
            assert urlFileIndex.size() + 1 == s : "urlFileIndex.size() = " + urlFileIndex.size() + ", s = " + s + ", result = " + result;
        }
        CrawlEntry crawlEntry = new CrawlEntry(rowEntry);
        long minimumDelta = (crawlEntry.url().isLocal()) ? minimumLocalDelta : minimumGlobalDelta;
        RobotsTxt.Entry robotsEntry = plasmaSwitchboard.robots.getEntry(crawlEntry.url().getHost());
        Integer hostDelay = (robotsEntry == null) ? null : robotsEntry.getCrawlDelay();
        long genericDelta = ((robotsEntry == null) || (hostDelay == null)) ? minimumDelta : Math.max(minimumDelta, hostDelay.intValue() * 1000);
        genericDelta = Math.min(10000, genericDelta); // prevent that ta robots file can stop our indexer completely
        if (delta < genericDelta) {
            // force a busy waiting here
            // in best case, this should never happen if the balancer works propertly
            // this is only to protect against the worst case, where the crawler could
            // behave in a DoS-manner
            long sleeptime = genericDelta - delta;
            try {synchronized(this) { this.wait(sleeptime); }} catch (InterruptedException e) {}
        }
        
        // update statistical data
        domaccess lastAccess = domainAccess.get(result.substring(6));
        if (lastAccess == null) lastAccess = new domaccess(); else lastAccess.update();
        domainAccess.put(result.substring(6), lastAccess);
        
        return crawlEntry;
    }
    
    private long lastAccessDelta(String hash) {
        assert hash != null;
        domaccess lastAccess = domainAccess.get((hash.length() > 6) ? hash.substring(6) : hash);
        if (lastAccess == null) return Long.MAX_VALUE; // never accessed
        return System.currentTimeMillis() - lastAccess.time();
    }
    
    public synchronized CrawlEntry top(int dist) throws IOException {
        // if we need to flush anything, then flush the domain stack first,
        // to avoid that new urls get hidden by old entries from the file stack
        if (urlRAMStack == null) return null;
        while ((domainStacksNotEmpty()) && (urlRAMStack.size() <= dist)) {
            // flush only that much as we need to display
            flushOnceDomStacks(0, true); 
        }
        while ((urlFileStack != null) && (urlRAMStack.size() <= dist) && (urlFileStack.size() > 0)) {
            // flush some entries from disc to ram stack
            try {
                kelondroRow.Entry t = urlFileStack.pop();
                if (t == null) break;
                urlRAMStack.add(new String(t.getColBytes(0)));
            } catch (IOException e) {
                break;
            }
        }
        if (dist >= urlRAMStack.size()) return null;
        String urlhash = (String) urlRAMStack.get(dist);
        kelondroRow.Entry entry = urlFileIndex.get(urlhash.getBytes());
        if (entry == null) {
            if (kelondroAbstractRecords.debugmode) serverLog.logWarning("PLASMA BALANCER", "no entry in index for urlhash " + urlhash);
            return null;
        }
        return new CrawlEntry(entry);
    }

    public synchronized Iterator<CrawlEntry> iterator() throws IOException {
        return new EntryIterator();
    }
    
    private class EntryIterator implements Iterator<CrawlEntry> {

        private Iterator<kelondroRow.Entry> rowIterator;
        
        public EntryIterator() throws IOException {
            rowIterator = urlFileIndex.rows(true, null);
        }
        
        public boolean hasNext() {
            return (rowIterator == null) ? false : rowIterator.hasNext();
        }

        public CrawlEntry next() {
            kelondroRow.Entry entry = (kelondroRow.Entry) rowIterator.next();
            try {
                return (entry == null) ? null : new CrawlEntry(entry);
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
