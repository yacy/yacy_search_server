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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.ObjectIndex;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.ByteBuffer;

import de.anomic.crawler.retrieval.Request;
import de.anomic.http.client.Cache;

public class Balancer {
    
    private static final String indexSuffix = "9.db";
    private static final int EcoFSBufferSize = 1000;

    // class variables
    private final ConcurrentHashMap<String, LinkedList<byte[]>> domainStacks;    // a map from domain name part to Lists with url hashs
    private   final ConcurrentLinkedQueue<byte[]> top;
    private   final TreeMap<Long, byte[]> delayed;
    protected ObjectIndex  urlFileIndex;
    private   final File   cacheStacksPath;
    private   long         minimumLocalDelta;
    private   long         minimumGlobalDelta;
    private   long         lastDomainStackFill;
    private   int          domStackInitSize;
    
    public Balancer(
    		final File cachePath,
    		final String stackname,
            final long minimumLocalDelta,
            final long minimumGlobalDelta,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.cacheStacksPath = cachePath;
        this.domainStacks   = new ConcurrentHashMap<String, LinkedList<byte[]>>();
        this.top = new ConcurrentLinkedQueue<byte[]>();
        this.delayed = new TreeMap<Long, byte[]>();
        this.minimumLocalDelta = minimumLocalDelta;
        this.minimumGlobalDelta = minimumGlobalDelta;
        this.domStackInitSize = Integer.MAX_VALUE;
        
        // create a stack for newly entered entries
        if (!(cachePath.exists())) cachePath.mkdir(); // make the path
        cacheStacksPath.mkdirs();
        final File f = new File(cacheStacksPath, stackname + indexSuffix);
        try {
            urlFileIndex = new Table(f, Request.rowdef, EcoFSBufferSize, 0, useTailCache, exceed134217727);
        } catch (RowSpaceExceededException e) {
            try {
                urlFileIndex = new Table(f, Request.rowdef, 0, 0, false, exceed134217727);
            } catch (RowSpaceExceededException e1) {
                Log.logException(e1);
            }
        }
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
    	Log.logInfo("Balancer", "cleaning balancer with " + urlFileIndex.size() + " entries from " + urlFileIndex.filename());
        try {
            urlFileIndex.clear();
        } catch (IOException e) {
            Log.logException(e);
        }
        domainStacks.clear();
        top.clear();
        synchronized (this.delayed) {
        	delayed.clear();
        }
    }
    
    public Request get(final byte[] urlhash) throws IOException {
        assert urlhash != null;
        if (urlFileIndex == null) return null; // case occurs during shutdown
        final Row.Entry entry = urlFileIndex.get(urlhash);
        if (entry == null) return null;
        return new Request(entry);
    }
    
    public int removeAllByProfileHandle(final String profileHandle, final long timeout) throws IOException, RowSpaceExceededException {
        // removes all entries with a specific profile hash.
        // this may last some time
        // returns number of deletions
        
        // first find a list of url hashes that shall be deleted
        final HandleSet urlHashes = Base64Order.enhancedCoder.getHandleSet(this.urlFileIndex.row().primaryKeyLength, 100);
        final long terminate = (timeout > 0) ? System.currentTimeMillis() + timeout : Long.MAX_VALUE;
        synchronized (this) {
            final Iterator<Row.Entry> i = urlFileIndex.rows();
            Row.Entry rowEntry;
            Request crawlEntry;
            while (i.hasNext() && (System.currentTimeMillis() < terminate)) {
                rowEntry = i.next();
                crawlEntry = new Request(rowEntry);
                if (crawlEntry.profileHandle().equals(profileHandle)) {
                    urlHashes.put(crawlEntry.url().hash());
                }
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
    public synchronized int remove(final HandleSet urlHashes) throws IOException {
        final int s = urlFileIndex.size();
        int removedCounter = 0;
        for (final byte[] urlhash: urlHashes) {
            final Row.Entry entry = urlFileIndex.remove(urlhash);
            if (entry != null) removedCounter++;
        }
        if (removedCounter == 0) return 0;
        assert urlFileIndex.size() + removedCounter == s : "urlFileIndex.size() = " + urlFileIndex.size() + ", s = " + s;

        // iterate through the top list
        final Iterator<byte[]> j = top.iterator();
        byte[] urlhash;
        while (j.hasNext()) {
        	urlhash = j.next();
        	if (urlHashes.has(urlhash)) j.remove();
        }
        
        // remove from delayed
        synchronized (this.delayed) {
        	final Iterator<Map.Entry<Long, byte[]>> k = this.delayed.entrySet().iterator();
        	while (k.hasNext()) {
        		if (urlHashes.has(k.next().getValue())) k.remove();
        	}
        }
        
        // iterate through the domain stacks
        final Iterator<Map.Entry<String, LinkedList<byte[]>>> q = domainStacks.entrySet().iterator();
        Map.Entry<String, LinkedList<byte[]>> se;
        LinkedList<byte[]> stack;
        while (q.hasNext()) {
            se = q.next();
            stack = se.getValue();
            final Iterator<byte[]> i = stack.iterator();
            while (i.hasNext()) {
                if (urlHashes.has(i.next())) i.remove();
            }
            if (stack.isEmpty()) q.remove();
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
    
    public boolean isEmpty() {
        return urlFileIndex.isEmpty();
    }
    
    private boolean domainStacksNotEmpty() {
        if (domainStacks == null) return false;
        synchronized (domainStacks) {
            final Iterator<LinkedList<byte[]>> i = domainStacks.values().iterator();
            while (i.hasNext()) {
                if (!i.next().isEmpty()) return true;
            }
        }
        return false;
    }
    
    public void push(final Request entry) throws IOException, RowSpaceExceededException {
        assert entry != null;
        final byte[] hash = entry.url().hash();
        synchronized (this) {
    	    if (urlFileIndex.has(hash)) {
                return;
            }
        
            // add to index
            final int s = urlFileIndex.size();
	        urlFileIndex.put(entry.toRow());
	        assert s < urlFileIndex.size() : "hash = " + new String(hash) + ", s = " + s + ", size = " + urlFileIndex.size();
	        assert urlFileIndex.has(hash) : "hash = " + new String(hash);

	        // add the hash to a queue
	        pushHashToDomainStacks(entry.url().hash(), 50);
        }
    }
    
    private void pushHashToDomainStacks(final byte[] hash, final int maxstacksize) {
        // extend domain stack
        final String dom = new String(hash).substring(6);
        LinkedList<byte[]> domainList = domainStacks.get(dom);
        if (domainList == null) {
            // create new list
            domainList = new LinkedList<byte[]>();
            domainList.add(hash);
            domainStacks.put(dom, domainList);
        } else {
            // extend existent domain list
        	if (domainList.size() < maxstacksize) domainList.addLast(hash);
        }
    }
    
    private void removeHashFromDomainStacks(final byte[] hash) {
        // extend domain stack
        final String dom = new String(hash).substring(6);
        final LinkedList<byte[]> domainList = domainStacks.get(dom);
        if (domainList == null) return;
        final Iterator<byte[]> i = domainList.iterator();
        while (i.hasNext()) {
        	if (Base64Order.enhancedCoder.equal(i.next(), hash)) {
        		i.remove();
        		return;
        	}
        }
    }
    
    private byte[] nextFromDelayed() {
		if (this.delayed.isEmpty()) return null;
		final Long first = this.delayed.firstKey();
		if (first.longValue() < System.currentTimeMillis()) {
			return this.delayed.remove(first);
		}
    	return null;
    }
    
    private byte[] anyFromDelayed() {
        if (this.delayed.isEmpty()) return null;
        final Long first = this.delayed.firstKey();
        return this.delayed.remove(first);
    }
    
    /**
     * get the next entry in this crawl queue in such a way that the domain access time delta is maximized
     * and always above the given minimum delay time. An additional delay time is computed using the robots.txt
     * crawl-delay time which is always respected. In case the minimum time cannot ensured, this method pauses
     * the necessary time until the url is released and returned as CrawlEntry object. In case that a profile
     * for the computed Entry does not exist, null is returned
     * @param delay true if the requester demands forced delays using explicit thread sleep
     * @param profile
     * @return a url in a CrawlEntry object
     * @throws IOException
     * @throws RowSpaceExceededException 
     */
    public Request pop(final boolean delay, final CrawlProfile profile) throws IOException {
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
        filltop(delay, 500, true);
        filltop(delay, 1000, true);
        filltop(delay, 2000, true);
        filltop(delay, 3000, true);
        filltop(delay, 4000, true);
        filltop(delay, 6000, true);
        filltop(delay, Long.MAX_VALUE, true);
    	
    	long sleeptime = 0;
    	Request crawlEntry = null;
    	synchronized (this) {
    	    byte[] failhashb = null;
    		while (!this.urlFileIndex.isEmpty()) {
		    	// first simply take one of the entries in the top list, that should be one without any delay
    		    byte[] nexthashb = nextFromDelayed();
		        //System.out.println("*** nextFromDelayed=" + nexthash);
		        if (nexthashb == null && !this.top.isEmpty()) {
		            nexthashb = top.remove();
		            //System.out.println("*** top.remove()=" + nexthash);
		        }
		        if (nexthashb == null) {
		            nexthashb = anyFromDelayed();
		        }
		        
		        // check minimumDelta and if necessary force a sleep
		        //final int s = urlFileIndex.size();
		        Row.Entry rowEntry = (nexthashb == null) ? null : urlFileIndex.remove(nexthashb);
		        if (rowEntry == null) {
		            //System.out.println("*** rowEntry=null, nexthash=" + nexthash);
		        	rowEntry = urlFileIndex.removeOne();
		        	if (rowEntry == null) {
		        	    nexthashb = null;
		        	} else {
		        	    nexthashb = rowEntry.getPrimaryKeyBytes();
		        	    //System.out.println("*** rowEntry.getPrimaryKeyBytes()=" + nexthash);
		        	}
		        	
		        }
		        if (rowEntry == null) {
		        	Log.logWarning("Balancer", "removeOne() failed - size = " + this.size());
		        	return null;
		        }
		        //assert urlFileIndex.size() + 1 == s : "urlFileIndex.size() = " + urlFileIndex.size() + ", s = " + s + ", result = " + result;
		        
		        crawlEntry = new Request(rowEntry);
		        //Log.logInfo("Balancer", "fetched next url: " + crawlEntry.url().toNormalform(true, false));
		        
		        // at this point we must check if the crawlEntry has relevance because the crawl profile still exists
		        // if not: return null. A calling method must handle the null value and try again
		        final CrawlProfile.entry profileEntry = (profile == null) ? null : profile.getEntry(crawlEntry.profileHandle());
		        if (profileEntry == null) {
		        	Log.logWarning("Balancer", "no profile entry for handle " + crawlEntry.profileHandle());
		        	return null;
		        }
		        // depending on the caching policy we need sleep time to avoid DoS-like situations
		        sleeptime = (
		                profileEntry.cacheStrategy() == CrawlProfile.CACHE_STRATEGY_CACHEONLY ||
		                (profileEntry.cacheStrategy() == CrawlProfile.CACHE_STRATEGY_IFEXIST && Cache.has(crawlEntry.url()))
		                ) ? 0 : Latency.waitingRemaining(crawlEntry.url(), minimumLocalDelta, minimumGlobalDelta); // this uses the robots.txt database and may cause a loading of robots.txt from the server
		        
		        assert Base64Order.enhancedCoder.equal(nexthashb, rowEntry.getPrimaryKeyBytes()) : "result = " + new String(nexthashb) + ", rowEntry.getPrimaryKeyBytes() = " + new String(rowEntry.getPrimaryKeyBytes());
		        assert Base64Order.enhancedCoder.equal(nexthashb, crawlEntry.url().hash()) : "result = " + new String(nexthashb) + ", crawlEntry.url().hash() = " + new String(crawlEntry.url().hash());
		        
		        if (failhashb != null && Base64Order.enhancedCoder.equal(failhashb, nexthashb)) break; // prevent endless loops
		        
		        if (delay && sleeptime > 0 && this.domStackInitSize > 1) {
		            //System.out.println("*** putback: nexthash=" + nexthash + ", failhash="+failhash);
		        	// put that thing back to omit a delay here
		            if (!ByteBuffer.contains(delayed.values(), nexthashb)) {
		                //System.out.println("*** delayed +=" + nexthash);
		                this.delayed.put(Long.valueOf(System.currentTimeMillis() + sleeptime + 1), nexthashb);
		            }
		        	try {
                        this.urlFileIndex.put(rowEntry);
                        this.domainStacks.remove(new String(nexthashb).substring(6));
                        failhashb = nexthashb;
                    } catch (RowSpaceExceededException e) {
                        Log.logException(e);
                    }
                    continue;
		        }
		        break;
	    	}
    	}
    	if (crawlEntry == null) return null;
    	
        if (delay && sleeptime > 0) {
            // force a busy waiting here
            // in best case, this should never happen if the balancer works propertly
            // this is only to protection against the worst case, where the crawler could
            // behave in a DoS-manner
            Log.logInfo("BALANCER", "forcing crawl-delay of " + sleeptime + " milliseconds for " + crawlEntry.url().getHost() + ": " + Latency.waitingRemainingExplain(crawlEntry.url(), minimumLocalDelta, minimumGlobalDelta) + ", top.size() = " + top.size() + ", delayed.size() = " + delayed.size() + ", domainStacks.size() = " + domainStacks.size() + ", domainStacksInitSize = " + this.domStackInitSize);
            long loops = sleeptime / 3000;
            long rest = sleeptime % 3000;
            if (loops < 2) {
            	rest = rest + 3000 * loops;
            	loops = 0;
            }
            if (rest > 0) {try {synchronized(this) { this.wait(rest); }} catch (final InterruptedException e) {}}
            for (int i = 0; i < loops; i++) {
            	Log.logInfo("BALANCER", "waiting for " + crawlEntry.url().getHost() + ": " + ((loops - i) * 3) + " seconds remaining...");
                try {synchronized(this) { this.wait(3000); }} catch (final InterruptedException e) {}
            }
        }
        Latency.update(new String(crawlEntry.url().hash()).substring(6), crawlEntry.url().getHost());
        return crawlEntry;
    }
    
    private void filltop(final boolean delay, final long maximumwaiting, final boolean acceptonebest) {
    	if (!this.top.isEmpty()) return;
    	
    	//System.out.println("*** DEBUG started filltop delay=" + ((delay) ? "true":"false") + ", maximumwaiting=" + maximumwaiting + ", acceptonebest=" + ((acceptonebest) ? "true":"false"));
    	
    	// check if we need to get entries from the file index
    	try {
			fillDomainStacks(200);
		} catch (IOException e) {
		    Log.logException(e);
		}
    	
    	// iterate over the domain stacks
    	final Iterator<Map.Entry<String, LinkedList<byte[]>>> i = this.domainStacks.entrySet().iterator();
    	Map.Entry<String, LinkedList<byte[]>> entry;
    	long smallestWaiting = Long.MAX_VALUE;
    	byte[] besthashb = null;
    	while (i.hasNext()) {
    		entry = i.next();
    		
    		// clean up empty entries
    		if (entry.getValue().isEmpty()) {
    			i.remove();
    			continue;
    		}
    		
    		byte[] n = entry.getValue().getFirst();
    		if (delay) {
    			final long w = Latency.waitingRemainingGuessed(n, minimumLocalDelta, minimumGlobalDelta);
    			if (w > maximumwaiting) {
    				if (w < smallestWaiting) {
    					smallestWaiting = w;
    					besthashb = n;
    				}
    				continue;
    			}
    			//System.out.println("*** accepting " + n + " : " + w);
    		}
    		n = entry.getValue().removeFirst();
    		this.top.add(n);
    		if (entry.getValue().isEmpty()) i.remove();
    	}
    	
    	// if we could not find any entry, then take the best we have seen so far
    	if (acceptonebest && !this.top.isEmpty() && besthashb != null) {
    		removeHashFromDomainStacks(besthashb);
    		this.top.add(besthashb);
    	}
    }
    
    private void fillDomainStacks(final int maxdomstacksize) throws IOException {
    	if (!this.domainStacks.isEmpty() && System.currentTimeMillis() - lastDomainStackFill < 120000L) return;
    	this.domainStacks.clear();
    	//synchronized (this.delayed) { delayed.clear(); }
    	this.lastDomainStackFill = System.currentTimeMillis();
    	final CloneableIterator<byte[]> i = this.urlFileIndex.keys(true, null);
    	while (i.hasNext()) {
    		pushHashToDomainStacks(i.next(), 1000);
    		if (this.domainStacks.size() > maxdomstacksize) break;
    	}
    	Log.logInfo("BALANCER", "re-fill of domain stacks; fileIndex.size() = " + this.urlFileIndex.size() + ", domainStacks.size = " + domainStacks.size() + ", collection time = " + (System.currentTimeMillis() - this.lastDomainStackFill) + " ms");
        this.domStackInitSize = this.domainStacks.size();
    }

    public ArrayList<Request> top(int count) {
    	count = Math.min(count, top.size());
    	final ArrayList<Request> cel = new ArrayList<Request>();
    	if (count == 0) return cel;
    	synchronized (this) {
	    	for (byte[] n: top) {
	    		try {
					final Row.Entry rowEntry = urlFileIndex.get(n);
					if (rowEntry == null) continue;
					final Request crawlEntry = new Request(rowEntry);
					cel.add(crawlEntry);
					count--;
					if (count <= 0) break;
				} catch (IOException e) {}
	    	}
	    	
	    	int depth = 0;
	    	loop: while (count > 0) {
    	    	// iterate over the domain stacks
    	        for (LinkedList<byte[]> list: this.domainStacks.values()) {
    	            if (list.size() <= depth) continue loop;
    	            byte[] n = list.get(depth);
                    try {
                        Row.Entry rowEntry = urlFileIndex.get(n);
                        if (rowEntry == null) continue;
                        final Request crawlEntry = new Request(rowEntry);
                        cel.add(crawlEntry);
                        count--;
                        if (count <= 0) break loop;
                    } catch (IOException e) {}
    	        }
	    	}
	    	
    	}
    	return cel;
    }
    
    public Iterator<Request> iterator() throws IOException {
        return new EntryIterator();
    }
    
    private class EntryIterator implements Iterator<Request> {

        private Iterator<Row.Entry> rowIterator;
        
        public EntryIterator() throws IOException {
            rowIterator = urlFileIndex.rows();
        }
        
        public boolean hasNext() {
            return (rowIterator == null) ? false : rowIterator.hasNext();
        }

        public Request next() {
            final Row.Entry entry = rowIterator.next();
            try {
                return (entry == null) ? null : new Request(entry);
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
