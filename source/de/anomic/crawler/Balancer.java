// plasmaCrawlBalancer.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.crawler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.index.ObjectIndex;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.table.EcoTable;
import de.anomic.kelondro.util.Log;

public class Balancer {
    
    private static final String indexSuffix = "9.db";
    private static final int EcoFSBufferSize = 200;

    // class variables
    private final ConcurrentHashMap<String, LinkedList<String>>
                         domainStacks;    // a map from domain name part to Lists with url hashs
    private ConcurrentLinkedQueue<String>
                         top;
    private TreeMap<Long, String>
                         delayed;
    private ObjectIndex  urlFileIndex;
    private final File   cacheStacksPath;
    private long         minimumLocalDelta;
    private long         minimumGlobalDelta;
    private int          profileErrors;
    private long         lastDomainStackFill;
    
    public Balancer(final File cachePath, final String stackname, final boolean fullram,
                    final long minimumLocalDelta, final long minimumGlobalDelta) {
        this.cacheStacksPath = cachePath;
        this.domainStacks   = new ConcurrentHashMap<String, LinkedList<String>>();
        this.top = new ConcurrentLinkedQueue<String>();
        this.delayed = new TreeMap<Long, String>();
        this.minimumLocalDelta = minimumLocalDelta;
        this.minimumGlobalDelta = minimumGlobalDelta;
        
        // create a stack for newly entered entries
        if (!(cachePath.exists())) cachePath.mkdir(); // make the path
        cacheStacksPath.mkdirs();
        File f = new File(cacheStacksPath, stackname + indexSuffix);
        urlFileIndex = new EcoTable(f, CrawlEntry.rowdef, (fullram) ? EcoTable.tailCacheUsageAuto : EcoTable.tailCacheDenyUsage, EcoFSBufferSize, 0);
        profileErrors = 0;
        lastDomainStackFill = 0;
        Log.logInfo("Balancer", "opened balancer file with " + urlFileIndex.size() + " entries from " + f.toString());
    }

    public long getMinimumLocalDelta() {
        return this.minimumLocalDelta;
    }
    
    public long getMinimumGlobalDelta() {
        return this.minimumGlobalDelta;
    }
    
    public void setMinimumDelta(final long minimumLocalDelta, final long minimumGlobalDelta) {
        this.minimumLocalDelta = minimumLocalDelta;
        this.minimumGlobalDelta = minimumGlobalDelta;
    }
    
    public synchronized void close() {
        if (urlFileIndex != null) {
            urlFileIndex.close();
            urlFileIndex = null;
        }
    }
    
    public void clear() {
    	Log.logInfo("Balancer", "cleaing balancer with " + urlFileIndex.size() + " entries from " + urlFileIndex.filename());
        try {
            urlFileIndex.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
        domainStacks.clear();
        top.clear();
        synchronized (this.delayed) {
        	delayed.clear();
        }
    }
    
    public CrawlEntry get(final String urlhash) throws IOException {
        assert urlhash != null;
        if (urlFileIndex == null) return null; // case occurs during shutdown
        final Row.Entry entry = urlFileIndex.get(urlhash.getBytes());
        if (entry == null) return null;
        return new CrawlEntry(entry);
    }
    
    public int removeAllByProfileHandle(final String profileHandle, final long timeout) throws IOException {
        // removes all entries with a specific profile hash.
        // this may last some time
        // returns number of deletions
        
        // first find a list of url hashes that shall be deleted
        final Iterator<Row.Entry> i = urlFileIndex.rows();
        final HashSet<String> urlHashes = new HashSet<String>();
        Row.Entry rowEntry;
        CrawlEntry crawlEntry;
        final long terminate = (timeout > 0) ? System.currentTimeMillis() + timeout : Long.MAX_VALUE;
        while (i.hasNext() && (System.currentTimeMillis() < terminate)) {
            rowEntry = i.next();
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
    public int remove(final HashSet<String> urlHashes) throws IOException {
        final int s = urlFileIndex.size();
        int removedCounter = 0;
        for (final String urlhash: urlHashes) {
            final Row.Entry entry = urlFileIndex.remove(urlhash.getBytes());
            if (entry != null) removedCounter++;
        }
        if (removedCounter == 0) return 0;
        assert urlFileIndex.size() + removedCounter == s : "urlFileIndex.size() = " + urlFileIndex.size() + ", s = " + s;

        // iterate through the top list
        Iterator<String> j = top.iterator();
        String urlhash;
        while (j.hasNext()) {
        	urlhash = j.next();
        	if (urlHashes.contains(urlhash)) j.remove();
        }
        
        // remove from delayed
        synchronized (this.delayed) {
        	Iterator<Map.Entry<Long, String>> k = this.delayed.entrySet().iterator();
        	while (k.hasNext()) {
        		if (urlHashes.contains(k.next().getValue())) k.remove();
        	}
        }
        
        // iterate through the domain stacks
        final Iterator<Map.Entry<String, LinkedList<String>>> q = domainStacks.entrySet().iterator();
        Map.Entry<String, LinkedList<String>> se;
        LinkedList<String> stack;
        while (q.hasNext()) {
            se = q.next();
            stack = se.getValue();
            Iterator<String> i = stack.iterator();
            while (i.hasNext()) {
                if (urlHashes.contains(i.next())) i.remove();
            }
            if (stack.size() == 0) q.remove();
        }
       
       return removedCounter;
    }
    
    public boolean has(final String urlhash) {
        return urlFileIndex.has(urlhash.getBytes());
    }
    
    public boolean notEmpty() {
        // alternative method to the property size() > 0
        // this is better because it may avoid synchronized access to domain stack summarization
        return domainStacksNotEmpty();
    }
    
    public int size() {
        return urlFileIndex.size();
    }
    
    private boolean domainStacksNotEmpty() {
        if (domainStacks == null) return false;
        synchronized (domainStacks) {
            final Iterator<LinkedList<String>> i = domainStacks.values().iterator();
            while (i.hasNext()) {
                if (i.next().size() > 0) return true;
            }
        }
        return false;
    }
    
    public void push(final CrawlEntry entry) throws IOException {
        assert entry != null;
        String hash = entry.url().hash();
        synchronized (this) {
    	    if (urlFileIndex.has(hash.getBytes())) {
                //Log.logWarning("BALANCER", "double-check has failed for urlhash " + entry.url().hash()  + " in " + stackname + " - fixed");
                return;
            }
        
            // add to index
            int s = urlFileIndex.size();
	        urlFileIndex.put(entry.toRow());
	        assert s < urlFileIndex.size() : "hash = " + hash;
	        assert urlFileIndex.has(hash.getBytes()) : "hash = " + hash;
	        
	        // add the hash to a queue
	        pushHashToDomainStacks(entry.url().hash(), 50);
        }
    }
    
    private void pushHashToDomainStacks(final String hash, int maxstacksize) {
        // extend domain stack
        final String dom = hash.substring(6);
        LinkedList<String> domainList = domainStacks.get(dom);
        if (domainList == null) {
            // create new list
            domainList = new LinkedList<String>();
            synchronized (domainStacks) {
                domainList.add(hash);
                domainStacks.put(dom, domainList);
            }
        } else {
            // extend existent domain list
        	if (domainList.size() < maxstacksize) domainList.addLast(hash);
        }
    }
    
    private void removeHashFromDomainStacks(final String hash) {
        // extend domain stack
        final String dom = hash.substring(6);
        LinkedList<String> domainList = domainStacks.get(dom);
        if (domainList == null) return;
        Iterator<String> i = domainList.iterator();
        while (i.hasNext()) {
        	if (i.next().equals(hash)) {
        		i.remove();
        		return;
        	}
        }
    }
    
    private String nextFromDelayed() {
		if (this.delayed.size() == 0) return null;
    	synchronized (this.delayed) {
    		if (this.delayed.size() == 0) return null;
    		Long first = this.delayed.firstKey();
    		if (first.longValue() < System.currentTimeMillis()) {
    			return this.delayed.remove(first);
    		}
    	}
    	return null;
    }
    
    /**
     * get the next entry in this crawl queue in such a way that the domain access time delta is maximized
     * and always above the given minimum delay time. An additional delay time is computed using the robots.txt
     * crawl-delay time which is always respected. In case the minimum time cannot ensured, this method pauses
     * the necessary time until the url is released and returned as CrawlEntry object. In case that a profile
     * for the computed Entry does not exist, null is returned
     * @param delay
     * @param profile
     * @return a url in a CrawlEntry object
     * @throws IOException
     */
    public CrawlEntry pop(boolean delay, CrawlProfile profile) throws IOException {
        // returns a crawl entry from the stack and ensures minimum delta times
        
    	filltop(delay, -600000, false);
    	filltop(delay, -60000, false);
    	filltop(delay, -10000, false);
    	filltop(delay, -6000, false);
    	filltop(delay, -4000, false);
    	filltop(delay, -3000, false);
    	filltop(delay, -2000, false);
    	filltop(delay, -1000, false);
    	filltop(delay, -500, false);
    	filltop(delay, 0, true);
    	
    	long sleeptime = 0;
    	CrawlEntry crawlEntry = null;
    	while (this.urlFileIndex.size() > 0) {
	    	// first simply take one of the entries in the top list, that should be one without any delay
	        String result = nextFromDelayed();
	        if (result == null && this.top.size() > 0) result = top.remove();
	        
	        // check minimumDelta and if necessary force a sleep
	        //final int s = urlFileIndex.size();
	        Row.Entry rowEntry = (result == null) ? null : urlFileIndex.remove(result.getBytes());
	        if (rowEntry == null) {
	        	rowEntry = urlFileIndex.removeOne();
	        	result = (rowEntry == null) ? null : new String(rowEntry.getPrimaryKeyBytes());
	        }
	        if (rowEntry == null) {
	        	Log.logWarning("Balancer", "removeOne() failed - size = " + this.size());
	        	return null;
	        }
	        //assert urlFileIndex.size() + 1 == s : "urlFileIndex.size() = " + urlFileIndex.size() + ", s = " + s + ", result = " + result;
	        
	        crawlEntry = new CrawlEntry(rowEntry);
	        //Log.logInfo("Balancer", "fetched next url: " + crawlEntry.url().toNormalform(true, false));
	        
	        // at this point we must check if the crawlEntry has relevancy because the crawl profile still exists
	        // if not: return null. A calling method must handle the null value and try again
	        if (profile != null && !profile.hasEntry(crawlEntry.profileHandle())) {
	        	profileErrors++;
	        	if (profileErrors < 20) Log.logInfo("Balancer", "no profile entry for handle " + crawlEntry.profileHandle());
	        	return null;
	        }
	        sleeptime = Latency.waitingRemaining(crawlEntry.url(), minimumLocalDelta, minimumGlobalDelta); // this uses the robots.txt database and may cause a loading of robots.txt from the server
    	
	        assert result.equals(new String(rowEntry.getPrimaryKeyBytes())) : "result = " + result + ", rowEntry.getPrimaryKeyBytes() = " + new String(rowEntry.getPrimaryKeyBytes());
	        assert result.equals(crawlEntry.url().hash()) : "result = " + result + ", crawlEntry.url().hash() = " + crawlEntry.url().hash();
	        if (this.domainStacks.size() <= 1) break;
	        
	        if (delay && sleeptime > 0) {
	        	// put that thing back to omit a delay here
	        	this.delayed.put(new Long(System.currentTimeMillis() + sleeptime + 1), result);
	        	this.urlFileIndex.put(rowEntry);
	        	this.domainStacks.remove(result.substring(6));
	        	continue;
	        }
	        break;
    	}
    	if (crawlEntry == null) return null;
    	
        if (delay && sleeptime > 0) {
            // force a busy waiting here
            // in best case, this should never happen if the balancer works propertly
            // this is only to protection against the worst case, where the crawler could
            // behave in a DoS-manner
            Log.logInfo("BALANCER", "forcing crawl-delay of " + (sleeptime / 1000) + " seconds for " + crawlEntry.url().getHost() + ((sleeptime > Math.max(minimumLocalDelta, minimumGlobalDelta)) ? " (forced latency)" : ""));
            long loops = sleeptime / 3000;
            long rest = sleeptime % 3000;
            if (loops < 2) {
            	rest = rest + 3000 * loops;
            	loops = 0;
            }
            try {synchronized(this) { this.wait(rest); }} catch (final InterruptedException e) {}
            for (int i = 0; i < loops; i++) {
            	Log.logInfo("BALANCER", "waiting for " + crawlEntry.url().getHost() + ": " + ((loops - i) * 3) + " seconds remaining...");
                try {synchronized(this) { this.wait(3000); }} catch (final InterruptedException e) {}
            }
            if (sleeptime > 3000 && this.domainStacks.size() > 1) this.domainStacks.remove(crawlEntry.url().hash().substring(6));
        }
        Latency.update(crawlEntry.url().hash().substring(6), crawlEntry.url().getHost());
        return crawlEntry;
    }
    
    private void filltop(boolean delay, long maximumwaiting, boolean acceptonebest) {
    	if (this.top.size() > 0) return;
    	
    	//System.out.println("*** DEBUG started filltop delay=" + ((delay) ? "true":"false") + ", maximumwaiting=" + maximumwaiting + ", acceptonebest=" + ((acceptonebest) ? "true":"false"));
    	
    	// check if we need to get entries from the file index
    	try {
			fillDomainStacks(800);
		} catch (IOException e) {
			e.printStackTrace();
		}
    	
    	// iterate over the domain stacks
    	Iterator<Map.Entry<String, LinkedList<String>>> i = this.domainStacks.entrySet().iterator();
    	Map.Entry<String, LinkedList<String>> entry;
    	long smallestWaiting = Long.MAX_VALUE;
    	String besthash = null;
    	while (i.hasNext()) {
    		entry = i.next();
    		if (entry.getValue().size() == 0) {
    			i.remove();
    			continue;
    		}
    		
    		String n = entry.getValue().getFirst();
    		if (delay) {
    			long w = Latency.waitingRemainingGuessed(n, minimumLocalDelta, minimumGlobalDelta);
    			if (w > maximumwaiting) {
    				if (w < smallestWaiting) {
    					smallestWaiting = w;
    					besthash = n;
    				}
    				continue;
    			}
    			//System.out.println("*** accepting " + n + " : " + w);
    		}
    		n = entry.getValue().removeFirst();
    		this.top.add(n);
    		if (entry.getValue().size() == 0) i.remove();
    	}
    	
    	// if we could not find any entry, then take the best we have seen so far
    	if (acceptonebest && this.top.size() > 0 && besthash != null) {
    		removeHashFromDomainStacks(besthash);
    		this.top.add(besthash);
    	}
    }
    
    private void fillDomainStacks(int maxdomstacksize) throws IOException {
    	if (this.domainStacks.size() > 0 && System.currentTimeMillis() - lastDomainStackFill < 200000L) return;
    	this.domainStacks.clear();
    	//synchronized (this.delayed) { delayed.clear(); }
    	this.lastDomainStackFill = System.currentTimeMillis();
    	CloneableIterator<byte[]> i = this.urlFileIndex.keys(true, null);
    	while (i.hasNext()) {
    		pushHashToDomainStacks(new String(i.next()), 50);
    		if (this.domainStacks.size() > maxdomstacksize) break;
    	}
    }

    public ArrayList<CrawlEntry> top(int count) {
    	count = Math.min(count, top.size());
    	ArrayList<CrawlEntry> cel = new ArrayList<CrawlEntry>();
    	if (count == 0) return cel;
    	for (String n: top) {
    		try {
				Row.Entry rowEntry = urlFileIndex.get(n.getBytes());
				if (rowEntry == null) continue;
				final CrawlEntry crawlEntry = new CrawlEntry(rowEntry);
				cel.add(crawlEntry);
				count--;
				if (count <= 0) break;
			} catch (IOException e) {
			}
    	}
    	return cel;
    }
    
    public Iterator<CrawlEntry> iterator() throws IOException {
        return new EntryIterator();
    }
    
    private class EntryIterator implements Iterator<CrawlEntry> {

        private Iterator<Row.Entry> rowIterator;
        
        public EntryIterator() throws IOException {
            rowIterator = urlFileIndex.rows();
        }
        
        public boolean hasNext() {
            return (rowIterator == null) ? false : rowIterator.hasNext();
        }

        public CrawlEntry next() {
            final Row.Entry entry = rowIterator.next();
            try {
                return (entry == null) ? null : new CrawlEntry(entry);
            } catch (final IOException e) {
                rowIterator = null;
                return null;
            }
        }

        public void remove() {
            if (rowIterator != null) rowIterator.remove();
        }
        
    }
    
}
