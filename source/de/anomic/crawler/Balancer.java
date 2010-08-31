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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.index.BufferedObjectIndex;
import net.yacy.kelondro.index.HandleSet;
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
    private static final int objectIndexBufferSize = 1000;
    private static final String localhost = "localhost";

    // class variables
    private final ConcurrentHashMap<String, HandleSet> domainStacks; // a map from host name to lists with url hashs
    private final ConcurrentLinkedQueue<byte[]> top; // a list of url-hashes that shall be taken next
    private final TreeMap<Long, byte[]> delayed; 
    private final HandleSet ddc;
    private final HandleSet double_push_check; // for debugging
    private BufferedObjectIndex  urlFileIndex;
    private final File   cacheStacksPath;
    private long         minimumLocalDelta;
    private long         minimumGlobalDelta;
    private long         lastDomainStackFill;
    private int          domStackInitSize;
    
    public Balancer(
    		final File cachePath,
    		final String stackname,
            final long minimumLocalDelta,
            final long minimumGlobalDelta,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.cacheStacksPath = cachePath;
        this.domainStacks = new ConcurrentHashMap<String, HandleSet>();
        this.top = new ConcurrentLinkedQueue<byte[]>();
        this.delayed = new TreeMap<Long, byte[]>();
        this.minimumLocalDelta = minimumLocalDelta;
        this.minimumGlobalDelta = minimumGlobalDelta;
        this.domStackInitSize = Integer.MAX_VALUE;
        this.ddc = new HandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 0);
        this.double_push_check = new HandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 0);
        
        // create a stack for newly entered entries
        if (!(cachePath.exists())) cachePath.mkdir(); // make the path
        cacheStacksPath.mkdirs();
        final File f = new File(cacheStacksPath, stackname + indexSuffix);
        try {
            urlFileIndex = new BufferedObjectIndex(new Table(f, Request.rowdef, EcoFSBufferSize, 0, useTailCache, exceed134217727), objectIndexBufferSize);
        } catch (RowSpaceExceededException e) {
            try {
                urlFileIndex = new BufferedObjectIndex(new Table(f, Request.rowdef, 0, 0, false, exceed134217727), objectIndexBufferSize);
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
        final Iterator<Map.Entry<String, HandleSet>> q = domainStacks.entrySet().iterator();
        HandleSet stack;
        while (q.hasNext()) {
            stack = q.next().getValue();
            for (byte[] handle: urlHashes) stack.remove(handle);
            if (stack.isEmpty()) q.remove();
        }
       
       return removedCounter;
    }
    
    public boolean has(final byte[] urlhashb) {
        synchronized (this) {
            return this.urlFileIndex.has(urlhashb) || this.ddc.has(urlhashb);
        }
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
            for (HandleSet l: domainStacks.values()) {
                if (!l.isEmpty()) return true;
            }
        }
        return false;
    }
    
    public void push(final Request entry) throws IOException, RowSpaceExceededException {
        assert entry != null;
        final byte[] hash = entry.url().hash();
        synchronized (this) {
            // double-check
            if (this.double_push_check.has(hash) || this.ddc.has(hash) || this.urlFileIndex.has(hash)) {
                //Log.logSevere("Balancer", "double push: " + new String(hash));
                return;
            }
            if (this.double_push_check.size() > 10000) this.double_push_check.clear();
            this.double_push_check.put(hash);
        
            // add to index
            final int s = this.urlFileIndex.size();
            this.urlFileIndex.put(entry.toRow());
	        assert s < this.urlFileIndex.size() : "hash = " + new String(hash) + ", s = " + s + ", size = " + this.urlFileIndex.size();
	        assert this.urlFileIndex.has(hash) : "hash = " + new String(hash);

	        // add the hash to a queue
	        pushHashToDomainStacks(entry.url().getHost(), entry.url().hash());
        }
    }
    
    private void pushHashToDomainStacks(String host, final byte[] urlhash) throws RowSpaceExceededException {
        // extend domain stack
        if (host == null) host = localhost;
        HandleSet domainList = domainStacks.get(host);
        if (domainList == null) {
            // create new list
            domainList = new HandleSet(12, Base64Order.enhancedCoder, 1);
            domainList.put(urlhash);
            domainStacks.put(host, domainList);
        } else {
            // extend existent domain list
        	domainList.put(urlhash);
        }
    }
    
    private void removeHashFromDomainStacks(String host, final byte[] urlhash) {
        // reduce domain stack
        if (host == null) host = localhost;
        final HandleSet domainList = domainStacks.get(host);
        if (domainList == null) {
            domainStacks.remove(host);
            return;
        }
        domainList.remove(urlhash);
        if (domainList.size() == 0) domainStacks.remove(host);
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
    public Request pop(final boolean delay, final Map<byte[], Map<String, String>> profiles) throws IOException {
        // returns a crawl entry from the stack and ensures minimum delta times
        
    	try {
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
        } catch (RowSpaceExceededException e) {}
    	
    	long sleeptime = 0;
    	Request crawlEntry = null;
    	synchronized (this) {
    	    byte[] failhash = null;
    		while (!this.urlFileIndex.isEmpty()) {
		    	// first simply take one of the entries in the top list, that should be one without any delay
    		    byte[] nexthash = nextFromDelayed();
		        //System.out.println("*** nextFromDelayed=" + nexthash);
		        if (nexthash == null && !this.top.isEmpty()) {
		            nexthash = top.remove();
		            //System.out.println("*** top.remove()=" + nexthash);
		        }
		        if (nexthash == null) {
		            nexthash = anyFromDelayed();
		        }
		        
		        // check minimumDelta and if necessary force a sleep
		        //final int s = urlFileIndex.size();
		        Row.Entry rowEntry = (nexthash == null) ? null : urlFileIndex.remove(nexthash);
		        if (rowEntry == null) {
		            System.out.println("*** rowEntry=null, nexthash=" + new String(nexthash));
		        	rowEntry = urlFileIndex.removeOne();
		        	if (rowEntry == null) {
		        	    nexthash = null;
		        	} else {
		        	    nexthash = rowEntry.getPrimaryKeyBytes();
		        	    //System.out.println("*** rowEntry.getPrimaryKeyBytes()=" + new String(nexthash));
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
		        final Map<String, String> mp = profiles == null ? null : profiles.get(crawlEntry.profileHandle());
		        final CrawlProfile profileEntry = mp == null ? null : new CrawlProfile(mp);
		        if (profileEntry == null) {
		        	Log.logWarning("Balancer", "no profile entry for handle " + crawlEntry.profileHandle());
		        	return null;
		        }
		        // depending on the caching policy we need sleep time to avoid DoS-like situations
		        sleeptime = (
		                profileEntry.cacheStrategy() == CrawlProfile.CacheStrategy.CACHEONLY ||
		                (profileEntry.cacheStrategy() == CrawlProfile.CacheStrategy.IFEXIST && Cache.has(crawlEntry.url()))
		                ) ? 0 : Latency.waitingRemaining(crawlEntry.url(), minimumLocalDelta, minimumGlobalDelta); // this uses the robots.txt database and may cause a loading of robots.txt from the server
		        
		        assert Base64Order.enhancedCoder.equal(nexthash, rowEntry.getPrimaryKeyBytes()) : "result = " + new String(nexthash) + ", rowEntry.getPrimaryKeyBytes() = " + new String(rowEntry.getPrimaryKeyBytes());
		        assert Base64Order.enhancedCoder.equal(nexthash, crawlEntry.url().hash()) : "result = " + new String(nexthash) + ", crawlEntry.url().hash() = " + new String(crawlEntry.url().hash());
		        
		        if (failhash != null && Base64Order.enhancedCoder.equal(failhash, nexthash)) break; // prevent endless loops
		        
		        if (delay && sleeptime > 0 && this.domStackInitSize > 1) {
		            //System.out.println("*** putback: nexthash=" + nexthash + ", failhash="+failhash);
		        	// put that thing back to omit a delay here
		            if (!ByteBuffer.contains(delayed.values(), nexthash)) {
		                //System.out.println("*** delayed +=" + nexthash);
		                this.delayed.put(Long.valueOf(System.currentTimeMillis() + sleeptime + 1), nexthash);
		            }
		        	try {
                        this.urlFileIndex.put(rowEntry);
                        String host = crawlEntry.url().getHost();
                        if (host == null) host = localhost;
                        this.domainStacks.remove(host);
                        failhash = nexthash;
                    } catch (RowSpaceExceededException e) {
                        Log.logException(e);
                    }
                    continue;
		        }
		        break;
	    	}
    		if (crawlEntry != null)
                try { this.ddc.put(crawlEntry.url().hash()); } catch (RowSpaceExceededException e) {}
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
        this.ddc.remove(crawlEntry.url().hash());
        Latency.update(crawlEntry.url());
        return crawlEntry;
    }
    
    private void filltop(final boolean delay, final long maximumwaiting, final boolean acceptonebest) throws RowSpaceExceededException {
    	if (!this.top.isEmpty()) return;
    	
    	//System.out.println("*** DEBUG started filltop delay=" + ((delay) ? "true":"false") + ", maximumwaiting=" + maximumwaiting + ", acceptonebest=" + ((acceptonebest) ? "true":"false"));
    	
    	// check if we need to get entries from the file index
    	try {
			fillDomainStacks();
		} catch (IOException e) {
		    Log.logException(e);
		}
    	
    	// iterate over the domain stacks
    	final Iterator<Map.Entry<String, HandleSet>> i = this.domainStacks.entrySet().iterator();
    	Map.Entry<String, HandleSet> entry;
    	long smallestWaiting = Long.MAX_VALUE;
    	byte[] besturlhash = null;
    	String besthost = null;
    	while (i.hasNext()) {
    		entry = i.next();
    		
    		// clean up empty entries
    		if (entry.getValue().isEmpty()) {
    			i.remove();
    			continue;
    		}
    		
    		byte[] n = entry.getValue().removeOne();
    		if (n == null) continue;
    		if (delay) {
    			final long w = Latency.waitingRemainingGuessed(entry.getKey(), minimumLocalDelta, minimumGlobalDelta);
    			if (w > maximumwaiting) {
    				if (w < smallestWaiting) {
    					smallestWaiting = w;
    					besturlhash = n;
    					besthost = entry.getKey();
    				}
    				entry.getValue().put(n); // put entry back
    				continue;
    			}
    		}
    		
    		this.top.add(n);
    		if (entry.getValue().isEmpty()) i.remove();
    	}
    	
    	// if we could not find any entry, then take the best we have seen so far
    	if (acceptonebest && !this.top.isEmpty() && besturlhash != null) {
    		removeHashFromDomainStacks(besthost, besturlhash);
    		this.top.add(besturlhash);
    	}
    }
    
    private void fillDomainStacks() throws IOException {
    	if (!this.domainStacks.isEmpty() && System.currentTimeMillis() - lastDomainStackFill < 120000L) return;
    	this.domainStacks.clear();
    	this.top.clear();
    	this.lastDomainStackFill = System.currentTimeMillis();
    	final HandleSet handles = this.urlFileIndex.keysFromBuffer(objectIndexBufferSize / 2);
        final CloneableIterator<byte[]> i = handles.keys(true, null);
        byte[] handle;
        String host;
        Request request;
    	while (i.hasNext()) {
    	    handle = i.next();
    	    Row.Entry entry = this.urlFileIndex.get(handle);
    	    if (entry == null) continue;
    	    request = new Request(entry);
    	    host = request.url().getHost();
    		try {
                pushHashToDomainStacks(host, handle);
            } catch (RowSpaceExceededException e) {
                break;
            }
    	}
    	Log.logInfo("BALANCER", "re-fill of domain stacks; fileIndex.size() = " + this.urlFileIndex.size() + ", domainStacks.size = " + domainStacks.size() + ", collection time = " + (System.currentTimeMillis() - this.lastDomainStackFill) + " ms");
        this.domStackInitSize = this.domainStacks.size();
    }

    public ArrayList<Request> top(int count) {
    	final ArrayList<Request> cel = new ArrayList<Request>();
    	if (count == 0) return cel;
    	byte[][] ta = new byte[Math.min(count, top.size())][];
        ta = top.toArray(ta);
    	for (byte[] n: ta) {
    	    if (n == null) break;
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
    	    int celsize = cel.size();
	        ll: for (HandleSet list: this.domainStacks.values()) {
	            if (list.size() <= depth) continue ll;
	            byte[] n = list.getOne(depth);
	            if (n == null) continue ll;
                try {
                    Row.Entry rowEntry = urlFileIndex.get(n);
                    if (rowEntry == null) continue;
                    final Request crawlEntry = new Request(rowEntry);
                    cel.add(crawlEntry);
                    count--;
                    if (count <= 0) break loop;
                } catch (IOException e) {}
	        }
    	    if (cel.size() == celsize) break loop;
	        depth++;
    	}
    	
    	if (cel.size() < count) try {
            List<Row.Entry> list = urlFileIndex.top(count - cel.size());
            for (Row.Entry entry: list) cel.add(new Request(entry));
        } catch (IOException e) { }
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
