// Balancer.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created: 24.09.2005
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

package net.yacy.crawler;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.sorting.OrderedScoreMap;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.Latency;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.index.BufferedObjectIndex;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.MemoryControl;

public class Balancer {

    private static final String indexSuffix           = "A.db";
    private static final int    EcoFSBufferSize       = 1000;
    private static final int    objectIndexBufferSize = 1000;

    // class variables filled with external values
    private final File                 cacheStacksPath;
    private       long                 minimumLocalDelta;
    private       long                 minimumGlobalDelta;
    private final Set<String>          myAgentIDs;
    private       BufferedObjectIndex  urlFileIndex;

    // class variables computed during operation
    private final ConcurrentMap<String, HandleSet> domainStacks; // a map from host name to lists with url hashs
    private final HandleSet                        double_push_check; // for debugging
    private long                                   lastDomainStackFill;
    private int                                    domStackInitSize;
    private final List<Map.Entry<String, byte[]>>  zeroWaitingCandidates;
    private final Random random; // used to alternate between choose-from-maxstack or choose from any zero-waiting

    public Balancer(
            final File cachePath,
            final String stackname,
            final long minimumLocalDelta,
            final long minimumGlobalDelta,
            final Set<String> myAgentIDs,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.cacheStacksPath = cachePath;
        this.domainStacks = new ConcurrentHashMap<String, HandleSet>();
        this.minimumLocalDelta = minimumLocalDelta;
        this.minimumGlobalDelta = minimumGlobalDelta;
        this.myAgentIDs = myAgentIDs;
        this.domStackInitSize = Integer.MAX_VALUE;
        this.double_push_check = new RowHandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 0);
        this.zeroWaitingCandidates = new ArrayList<Map.Entry<String, byte[]>>();
        this.random = new Random(System.currentTimeMillis());
        
        // create a stack for newly entered entries
        if (!(cachePath.exists())) cachePath.mkdir(); // make the path
        this.cacheStacksPath.mkdirs();
        final File f = new File(this.cacheStacksPath, stackname + indexSuffix);
        try {
            this.urlFileIndex = new BufferedObjectIndex(new Table(f, Request.rowdef, EcoFSBufferSize, 0, useTailCache, exceed134217727, true), objectIndexBufferSize);
        } catch (final SpaceExceededException e) {
            try {
                this.urlFileIndex = new BufferedObjectIndex(new Table(f, Request.rowdef, 0, 0, false, exceed134217727, true), objectIndexBufferSize);
            } catch (final SpaceExceededException e1) {
                Log.logException(e1);
            }
        }
        this.lastDomainStackFill = 0;
        Log.logInfo("Balancer", "opened balancer file with " + this.urlFileIndex.size() + " entries from " + f.toString());
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
        if (this.urlFileIndex != null) {
            this.urlFileIndex.close();
            this.urlFileIndex = null;
        }
    }

    public void clear() {
    	Log.logInfo("Balancer", "cleaning balancer with " + this.urlFileIndex.size() + " entries from " + this.urlFileIndex.filename());
        try {
            this.urlFileIndex.clear();
        } catch (final IOException e) {
            Log.logException(e);
        }
        this.domainStacks.clear();
        this.double_push_check.clear();
    }

    public Request get(final byte[] urlhash) throws IOException {
        assert urlhash != null;
        if (this.urlFileIndex == null) return null; // case occurs during shutdown
        final Row.Entry entry = this.urlFileIndex.get(urlhash, false);
        if (entry == null) return null;
        return new Request(entry);
    }

    public int removeAllByProfileHandle(final String profileHandle, final long timeout) throws IOException, SpaceExceededException {
        // removes all entries with a specific profile hash.
        // this may last some time
        // returns number of deletions

        // first find a list of url hashes that shall be deleted
        final HandleSet urlHashes = new RowHandleSet(this.urlFileIndex.row().primaryKeyLength, Base64Order.enhancedCoder, 100);
        final long terminate = (timeout > 0) ? System.currentTimeMillis() + timeout : Long.MAX_VALUE;
        synchronized (this) {
            final Iterator<Row.Entry> i = this.urlFileIndex.rows();
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
        return remove(urlHashes);
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
        final int s = this.urlFileIndex.size();
        int removedCounter = 0;
        for (final byte[] urlhash: urlHashes) {
            final Row.Entry entry = this.urlFileIndex.remove(urlhash);
            if (entry != null) removedCounter++;

            // remove from double-check caches
            this.double_push_check.remove(urlhash);
        }
        if (removedCounter == 0) return 0;
        assert this.urlFileIndex.size() + removedCounter == s : "urlFileIndex.size() = " + this.urlFileIndex.size() + ", s = " + s;

        // iterate through the domain stacks
        final Iterator<Map.Entry<String, HandleSet>> q = this.domainStacks.entrySet().iterator();
        HandleSet stack;
        while (q.hasNext()) {
            stack = q.next().getValue();
            for (final byte[] handle: urlHashes) stack.remove(handle);
            if (stack.isEmpty()) q.remove();
        }
        
        // iterate through zero-waiting map
        final Iterator<Map.Entry<String, byte[]>> i = this.zeroWaitingCandidates.iterator();
        while (i.hasNext()) {
            if (urlHashes.has(i.next().getValue())) i.remove();
        }

        return removedCounter;
    }

    public boolean has(final byte[] urlhashb) {
        return this.urlFileIndex.has(urlhashb) || this.double_push_check.has(urlhashb);
    }

    public boolean notEmpty() {
        // alternative method to the property size() > 0
        // this is better because it may avoid synchronized access to domain stack summarization
        return domainStacksNotEmpty();
    }

    public int size() {
        return this.urlFileIndex.size();
    }

    public boolean isEmpty() {
        return this.urlFileIndex.isEmpty();
    }

    private boolean domainStacksNotEmpty() {
        if (this.domainStacks == null) return false;
        synchronized (this.domainStacks) {
            for (final HandleSet l: this.domainStacks.values()) {
                if (!l.isEmpty()) return true;
            }
        }
        return false;
    }

    /**
     * push a crawl request on the balancer stack
     * @param entry
     * @return null if this was successful or a String explaining what went wrong in case of an error
     * @throws IOException
     * @throws SpaceExceededException
     */
    public String push(final Request entry) throws IOException, SpaceExceededException {
        assert entry != null;
        final byte[] hash = entry.url().hash();
        synchronized (this) {
            // double-check
            if (this.double_push_check.has(hash)) return "double occurrence in double_push_check";
            if (this.urlFileIndex.has(hash)) return "double occurrence in urlFileIndex";

            if (this.double_push_check.size() > 10000 || MemoryControl.shortStatus()) this.double_push_check.clear();
            this.double_push_check.put(hash);

            // add to index
            final int s = this.urlFileIndex.size();
            this.urlFileIndex.put(entry.toRow());
	        assert s < this.urlFileIndex.size() : "hash = " + ASCII.String(hash) + ", s = " + s + ", size = " + this.urlFileIndex.size();
	        assert this.urlFileIndex.has(hash) : "hash = " + ASCII.String(hash);

	        // add the hash to a queue
	        pushHashToDomainStacks(entry.url().getHost(), entry.url().hash());
	        return null;
        }
    }

    /**
     * get a list of domains that are currently maintained as domain stacks
     * @return a map of clear text strings of host names to an integer array: {the size of the domain stack, guessed delta waiting time}
     */
    public Map<String, Integer[]> getDomainStackHosts() {
        Map<String, Integer[]> map = new TreeMap<String, Integer[]>(); // we use a tree map to get a stable ordering
        for (Map.Entry<String, HandleSet> entry: this.domainStacks.entrySet()) {
            int size = entry.getValue().size();
            int delta =  (int) Latency.waitingRemainingGuessed(entry.getKey(), this.minimumLocalDelta, this.minimumGlobalDelta);
            map.put(entry.getKey(), new Integer[]{size, delta});
        }
        return map;
    }

    /**
     * Get the minimum sleep time for a given url. The result can also be negative to reflect the time since the last access
     * The time can be as low as Long.MIN_VALUE to show that there should not be any limitation at all.
     * @param robots
     * @param profileEntry
     * @param crawlURL
     * @return the sleep time in milliseconds; may be negative for no sleep time
     */
    private long getDomainSleepTime(final RobotsTxt robots, final CrawlProfile profileEntry, final DigestURI crawlURL) {
        if (profileEntry == null) return 0;
        long sleeptime = (
            profileEntry.cacheStrategy() == CacheStrategy.CACHEONLY ||
            (profileEntry.cacheStrategy() == CacheStrategy.IFEXIST && Cache.has(crawlURL.hash()))
            ) ? Integer.MIN_VALUE : Latency.waitingRemaining(crawlURL, robots, this.myAgentIDs, this.minimumLocalDelta, this.minimumGlobalDelta); // this uses the robots.txt database and may cause a loading of robots.txt from the server
        return sleeptime;
    }
    
    /**
     * load a robots.txt to get the robots time.
     * ATTENTION: this method causes that a robots.txt is loaded from the web which may cause a longer delay in execution.
     * This shall therefore not be called in synchronized environments.
     * @param robots
     * @param profileEntry
     * @param crawlURL
     * @return
     */
    private long getRobotsTime(final RobotsTxt robots, final CrawlProfile profileEntry, final DigestURI crawlURL) {
        if (profileEntry == null) return 0;
        long sleeptime = Latency.waitingRobots(crawlURL, robots, this.myAgentIDs); // this uses the robots.txt database and may cause a loading of robots.txt from the server
        return sleeptime < 0 ? 0 : sleeptime;
    }

    /**
     * get lists of crawl request entries for a specific host
     * @param host
     * @param maxcount
     * @return a list of crawl loader requests
     */
    public List<Request> getDomainStackReferences(String host, int maxcount) {
        HandleSet domainList = this.domainStacks.get(host);
        if (domainList == null || domainList.isEmpty()) return new ArrayList<Request>(0);
        ArrayList<Request> cel = new ArrayList<Request>(maxcount);
        for (int i = 0; i < maxcount; i++) {
            if (domainList.size() <= i) break;
            final byte[] urlhash = domainList.getOne(i);
            if (urlhash == null) continue;
            Row.Entry rowEntry;
            try {
                rowEntry = this.urlFileIndex.get(urlhash, true);
            } catch (IOException e) {
                continue;
            }
            if (rowEntry == null) continue;
            Request crawlEntry;
            try {
                crawlEntry = new Request(rowEntry);
            } catch (IOException e) {
                continue;
            }
            cel.add(crawlEntry);
        }
        return cel;
    }

    private void pushHashToDomainStacks(String host, final byte[] urlhash) throws SpaceExceededException {
        // extend domain stack
        if (host == null) host = Domains.LOCALHOST;
        HandleSet domainList = this.domainStacks.get(host);
        if (domainList == null) {
            // create new list
            domainList = new RowHandleSet(12, Base64Order.enhancedCoder, 1);
            domainList.put(urlhash);
            this.domainStacks.put(host, domainList);
        } else {
            // extend existent domain list
        	domainList.put(urlhash);
        }
    }

    private void removeHashFromDomainStacks(String host, final byte[] urlhash) {
        // reduce domain stack
        if (host == null) host = Domains.LOCALHOST;
        final HandleSet domainList = this.domainStacks.get(host);
        if (domainList == null) {
            this.domainStacks.remove(host);
            return;
        }
        domainList.remove(urlhash);
        if (domainList.isEmpty()) this.domainStacks.remove(host);
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
     * @throws SpaceExceededException
     */
    public Request pop(final boolean delay, final CrawlSwitchboard cs, final RobotsTxt robots) throws IOException {
        // returns a crawl entry from the stack and ensures minimum delta times

    	long sleeptime = 0;
    	Request crawlEntry = null;
    	CrawlProfile profileEntry = null;
    	synchronized (this) {
    	    byte[] failhash = null;
    		while (!this.urlFileIndex.isEmpty()) {
    		    byte[] nexthash = getbest(robots);
    		    if (nexthash == null) return null;

		        // check minimumDelta and if necessary force a sleep
		        //final int s = urlFileIndex.size();
		        Row.Entry rowEntry = (nexthash == null) ? null : this.urlFileIndex.remove(nexthash);
		        if (rowEntry == null) {
		            //System.out.println("*** rowEntry=null, nexthash=" + UTF8.String(nexthash));
		        	rowEntry = this.urlFileIndex.removeOne();
		        	if (rowEntry == null) {
		        	    nexthash = null;
		        	} else {
		        	    nexthash = rowEntry.getPrimaryKeyBytes();
		        	    //System.out.println("*** rowEntry.getPrimaryKeyBytes()=" + UTF8.String(nexthash));
		        	}

		        }
		        if (rowEntry == null) {
		        	Log.logWarning("Balancer", "removeOne() failed - size = " + size());
		        	return null;
		        }
		        //assert urlFileIndex.size() + 1 == s : "urlFileIndex.size() = " + urlFileIndex.size() + ", s = " + s + ", result = " + result;

		        crawlEntry = new Request(rowEntry);
		        //Log.logInfo("Balancer", "fetched next url: " + crawlEntry.url().toNormalform(true, false));

		        // at this point we must check if the crawlEntry has relevance because the crawl profile still exists
		        // if not: return null. A calling method must handle the null value and try again
		        profileEntry = cs.getActive(UTF8.getBytes(crawlEntry.profileHandle()));
		        if (profileEntry == null) {
		        	Log.logWarning("Balancer", "no profile entry for handle " + crawlEntry.profileHandle());
		        	return null;
		        }
		        // depending on the caching policy we need sleep time to avoid DoS-like situations
		        sleeptime = getDomainSleepTime(robots, profileEntry, crawlEntry.url());

		        assert Base64Order.enhancedCoder.equal(nexthash, rowEntry.getPrimaryKeyBytes()) : "result = " + ASCII.String(nexthash) + ", rowEntry.getPrimaryKeyBytes() = " + ASCII.String(rowEntry.getPrimaryKeyBytes());
		        assert Base64Order.enhancedCoder.equal(nexthash, crawlEntry.url().hash()) : "result = " + ASCII.String(nexthash) + ", crawlEntry.url().hash() = " + ASCII.String(crawlEntry.url().hash());

		        if (failhash != null && Base64Order.enhancedCoder.equal(failhash, nexthash)) break; // prevent endless loops
		        break;
	    	}
    	}
    	if (crawlEntry == null) return null;

    	long robotsTime = getRobotsTime(robots, profileEntry, crawlEntry.url());
        Latency.updateAfterSelection(crawlEntry.url(), profileEntry == null ? 0 : robotsTime);
        if (delay && sleeptime > 0) {
            // force a busy waiting here
            // in best case, this should never happen if the balancer works propertly
            // this is only to protection against the worst case, where the crawler could
            // behave in a DoS-manner
            Log.logInfo("BALANCER", "forcing crawl-delay of " + sleeptime + " milliseconds for " + crawlEntry.url().getHost() + ": " + Latency.waitingRemainingExplain(crawlEntry.url(), robots, this.myAgentIDs, this.minimumLocalDelta, this.minimumGlobalDelta) + ", domainStacks.size() = " + this.domainStacks.size() + ", domainStacksInitSize = " + this.domStackInitSize);
            long loops = sleeptime / 1000;
            long rest = sleeptime % 1000;
            if (loops < 3) {
            	rest = rest + 1000 * loops;
            	loops = 0;
            }
            synchronized(this) {
                // must be synchronized here to avoid 'takeover' moves from other threads which then idle the same time which would not be enough
                if (rest > 0) {try {this.wait(rest);} catch (final InterruptedException e) {}}
                for (int i = 0; i < loops; i++) {
                	Log.logInfo("BALANCER", "waiting for " + crawlEntry.url().getHost() + ": " + (loops - i) + " seconds remaining...");
                	try {this.wait(1000); } catch (final InterruptedException e) {}
                }
            }
            Latency.updateAfterSelection(crawlEntry.url(), robotsTime);
        }
        return crawlEntry;
    }

    private byte[] getbest(final RobotsTxt robots) {

        synchronized (this.zeroWaitingCandidates) {
            if (this.zeroWaitingCandidates.size() > 0) {
                byte[] urlhash = pickFromZeroWaiting();
                if (urlhash != null) return urlhash;
            }
            this.zeroWaitingCandidates.clear();
            
        	// check if we need to get entries from the file index
        	try {
    			fillDomainStacks();
    		} catch (final IOException e) {
    		    Log.logException(e);
    		}
    
        	// iterate over the domain stacks
        	final Iterator<Map.Entry<String, HandleSet>> i = this.domainStacks.entrySet().iterator();
        	Map.Entry<String, HandleSet> entry;
        	long smallestWaiting = Long.MAX_VALUE;
        	byte[] besturlhash = null;
        	String besthost = null;
        	OrderedScoreMap<Map.Entry<String, byte[]>> nextZeroCandidates = new OrderedScoreMap<Map.Entry<String, byte[]>>(null);
        	int newCandidatesForward = 10;
        	while (i.hasNext() && nextZeroCandidates.size() < 1000) {
                entry = i.next();
    
                // clean up empty entries
                if (entry.getValue().isEmpty()) {
                    i.remove();
                    continue;
                }
    
                final byte[] urlhash = entry.getValue().getOne(0);
                if (urlhash == null) continue;
    
                long w;
                Row.Entry rowEntry;
                try {
                    rowEntry = this.urlFileIndex.get(urlhash, false);
                    if (rowEntry == null) {
                    	continue;
                    }
                    Request crawlEntry = new Request(rowEntry);
                    w = Latency.waitingRemaining(crawlEntry.url(), robots, this.myAgentIDs, this.minimumLocalDelta, this.minimumGlobalDelta);
                    //System.out.println("*** waitingRemaining = " + w + ", guessed = " + Latency.waitingRemainingGuessed(entry.getKey(), this.minimumLocalDelta, this.minimumGlobalDelta));
                    //System.out.println("*** explained: " + Latency.waitingRemainingExplain(crawlEntry.url(), robots, this.myAgentIDs, this.minimumLocalDelta, this.minimumGlobalDelta));
                } catch (IOException e1) {
                    w = Latency.waitingRemainingGuessed(entry.getKey(), this.minimumLocalDelta, this.minimumGlobalDelta);
                }

                if (w <= 0) {
                    if (w == Integer.MIN_VALUE && newCandidatesForward > 0) {
                        // give new domains a chance, but not too much; otherwise a massive downloading of robots.txt from too much domains (dns lock!) will more likely block crawling
                        newCandidatesForward--;
                        nextZeroCandidates.set(new AbstractMap.SimpleEntry<String, byte[]>(entry.getKey(), urlhash), 1000);
                    } else {
                        nextZeroCandidates.set(new AbstractMap.SimpleEntry<String, byte[]>(entry.getKey(), urlhash), entry.getValue().size());
                    }
                }
                if (w < smallestWaiting || (w == smallestWaiting && this.random.nextBoolean())) {
                    smallestWaiting = w;
                    besturlhash = urlhash;
                    besthost = entry.getKey();
                }
        	}
        	Log.logInfo("Balancer", "*** getbest: created new nextZeroCandidates-list, size = " + nextZeroCandidates.size() + ", domainStacks.size = " + this.domainStacks.size());
            
        	if (besturlhash == null) {
                Log.logInfo("Balancer", "*** getbest: besturlhash == null");
                return null; // this should never happen
        	}
    
        	// best case would be, if we have some zeroWaitingCandidates,
        	// then we select that one with the largest stack
    
        	if (nextZeroCandidates.isEmpty()) {
                // bad luck: just take that one with least waiting
                removeHashFromDomainStacks(besthost, besturlhash);
                Log.logInfo("Balancer", "*** getbest: no zero waiting candidates, besthost = " + besthost);
                return besturlhash;
        	}
        	
        	// now take some of the nextZeroCandidates and put the best into the zeroWaitingCandidates
        	int pick = nextZeroCandidates.size() <= 10 ? nextZeroCandidates.size() : Math.max(1, nextZeroCandidates.size() / 3);
        	Iterator<Map.Entry<String, byte[]>> k = nextZeroCandidates.keys(false);
        	while (k.hasNext() && pick-- > 0) {
        	    this.zeroWaitingCandidates.add(k.next());
        	}
        	Log.logInfo("Balancer", "*** getbest: created new zeroWaitingCandidates-list, size = " + zeroWaitingCandidates.size() + ", domainStacks.size = " + this.domainStacks.size());
            
        	return pickFromZeroWaiting();
        }
    }

    private byte[] pickFromZeroWaiting() {
        // by random we choose now either from the largest stack or from any of the other stacks
        String host = null;
        byte[] hash = null;
        while (this.zeroWaitingCandidates.size() > 0) {
            Map.Entry<String, byte[]> z = this.zeroWaitingCandidates.remove(this.random.nextInt(this.zeroWaitingCandidates.size()));
            HandleSet hs = this.domainStacks.get(z.getKey());
            if (hs == null) continue;
            host = z.getKey(); if (host == null) continue;
            hash = z.getValue(); if (hash == null) continue;
            removeHashFromDomainStacks(host, hash);
            Log.logInfo("Balancer", "*** getbest: picked a random from the zero-waiting stack: " + host + ", zeroWaitingCandidates.size = " + this.zeroWaitingCandidates.size());
            return hash;
        }

        Log.logInfo("Balancer", "*** getbest: picking from zero-waiting stack failed!" + " zeroWaitingCandidates.size = " + this.zeroWaitingCandidates.size());
        this.zeroWaitingCandidates.clear();
        return null;
    }
    
    private void fillDomainStacks() throws IOException {
    	if (!this.domainStacks.isEmpty() && System.currentTimeMillis() - this.lastDomainStackFill < 60000L) return;
    	this.domainStacks.clear();
    	this.lastDomainStackFill = System.currentTimeMillis();
    	//final HandleSet handles = this.urlFileIndex.keysFromBuffer(objectIndexBufferSize / 2);
        //final CloneableIterator<byte[]> i = handles.keys(true, null);
        final CloneableIterator<byte[]> i = this.urlFileIndex.keys(true, null);
        byte[] handle;
        String host;
        Request request;
        int count = 0;
    	while (i.hasNext()) {
    	    handle = i.next();
    	    final Row.Entry entry = this.urlFileIndex.get(handle, false);
    	    if (entry == null) continue;
    	    request = new Request(entry);
    	    host = request.url().getHost();
    		try {
                pushHashToDomainStacks(host, handle);
            } catch (final SpaceExceededException e) {
                break;
            }
            count++;
            if (!this.domainStacks.isEmpty() && count > 120 * this.domainStacks.size()) break;
    	}
    	Log.logInfo("BALANCER", "re-fill of domain stacks; fileIndex.size() = " + this.urlFileIndex.size() + ", domainStacks.size = " + this.domainStacks.size() + ", collection time = " + (System.currentTimeMillis() - this.lastDomainStackFill) + " ms");
        this.domStackInitSize = this.domainStacks.size();
    }

    public Iterator<Request> iterator() throws IOException {
        return new EntryIterator();
    }

    private class EntryIterator implements Iterator<Request> {

        private Iterator<Row.Entry> rowIterator;

        public EntryIterator() throws IOException {
            this.rowIterator = Balancer.this.urlFileIndex.rows();
        }

        @Override
        public boolean hasNext() {
            return (this.rowIterator == null) ? false : this.rowIterator.hasNext();
        }

        @Override
        public Request next() {
            final Row.Entry entry = this.rowIterator.next();
            try {
                return (entry == null) ? null : new Request(entry);
            } catch (final IOException e) {
                Log.logException(e);
                this.rowIterator = null;
                return null;
            }
        }

        @Override
        public void remove() {
            if (this.rowIterator != null) this.rowIterator.remove();
        }

    }

}
