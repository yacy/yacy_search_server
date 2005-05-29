// plasmaWordIndexCache.java
// -------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 6.5.2005
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

import java.io.*;
import java.util.*;
import java.lang.RuntimeException;
import de.anomic.kelondro.*;
import de.anomic.server.serverLog;
import de.anomic.yacy.yacySeedDB;

public final class plasmaWordIndexCache implements plasmaWordIndexInterface {
    
    // environment constants
    private static final String indexDumpFileName = "indexDump0.stack";
    private static final String oldSingletonFileName = "indexSingletons0.db";
    private static final String newSingletonFileName = "indexAssortment001.db";
    private static final String indexAssortmentClusterPath = "ACLUSTER";
    private static final int assortmentLimit = 50;
    private static final int ramcacheLimit = 51;
    
    
    // class variables
    private File databaseRoot;
    private plasmaWordIndexInterface backend;
    private TreeMap cache;
    private kelondroMScoreCluster hashScore;
    private HashMap hashDate;
    private int maxWords;
    private serverLog log;
    private plasmaWordIndexAssortmentCluster assortmentCluster;
    private int singletonBufferSize; //kb

    // calculated constants
    private static String minKey, maxKey;
    static {
	maxKey = "";
	for (int i = 0; i < yacySeedDB.commonHashLength; i++) maxKey += 'z';
	minKey = "";
	for (int i = 0; i < yacySeedDB.commonHashLength; i++) maxKey += '-';
    }

    public plasmaWordIndexCache(File databaseRoot, plasmaWordIndexInterface backend, int singletonbufferkb, serverLog log) {
        // migrate#1
        File oldSingletonFile = new File(databaseRoot, oldSingletonFileName);
        File newSingletonFile = new File(databaseRoot, newSingletonFileName);
        if ((oldSingletonFile.exists()) && (!(newSingletonFile.exists()))) oldSingletonFile.renameTo(newSingletonFile);
        
        // create new assortment cluster path
        File assortmentClusterPath = new File(databaseRoot, indexAssortmentClusterPath);
        if (!(assortmentClusterPath.exists())) assortmentClusterPath.mkdirs();
        
        // migrate#2
        File acSingletonFile = new File(assortmentClusterPath, newSingletonFileName);
        if ((newSingletonFile.exists()) && (!(acSingletonFile.exists()))) newSingletonFile.renameTo(acSingletonFile);
        
        // creates a new index cache
        // the cache has a back-end where indexes that do not fit in the cache are flushed
        this.databaseRoot = databaseRoot;
        this.singletonBufferSize = singletonbufferkb;
        this.cache = new TreeMap();
	this.hashScore = new kelondroMScoreCluster();
        this.hashDate  = new HashMap();
	this.maxWords = 10000;
        this.backend = backend;
        this.log = log;
	this.assortmentCluster = new plasmaWordIndexAssortmentCluster(assortmentClusterPath, assortmentLimit, singletonBufferSize, log);

        // read in dump of last session
        try {
            restore();
        } catch (IOException e){
            log.logError("unable to restore cache dump: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void dump(int waitingSeconds) throws IOException {
        log.logSystem("creating dump for index cache, " + cache.size() + " words (and much more urls)");
        File indexDumpFile = new File(databaseRoot, indexDumpFileName);
        if (indexDumpFile.exists()) indexDumpFile.delete();
        kelondroStack dumpStack = new kelondroStack(indexDumpFile, 1024, plasmaWordIndexAssortment.bufferStructureBasis);
        long startTime = System.currentTimeMillis();
        long messageTime = System.currentTimeMillis() + 5000;
        long wordsPerSecond = 0, wordcount = 0, urlcount = 0;
        synchronized (cache) {
            Iterator i = cache.entrySet().iterator();
            //Iterator i = hashScore.scores(true);
            Map.Entry entry;
            String wordHash;
            plasmaWordIndexEntryContainer container;
            long creationTime;
            plasmaWordIndexEntry wordEntry;
            byte[][] row = new byte[5][];
            while (i.hasNext()) {
                // get entries
                entry = (Map.Entry) i.next();
                //wordHash = (String) i.next();
                wordHash = (String) entry.getKey();
                creationTime = getCreationTime(wordHash);
                //container = (plasmaWordIndexEntryContainer) cache.get(wordHash);
                container = (plasmaWordIndexEntryContainer) entry.getValue();

                // put entries on stack
                if (container != null) {
                    Iterator ci = container.entries();
                    while (ci.hasNext()) {
                        wordEntry = (plasmaWordIndexEntry) ci.next();
                        row[0] = wordHash.getBytes();
                        row[1] = kelondroRecords.long2bytes(container.size(), 4);
                        row[2] = kelondroRecords.long2bytes(creationTime, 8);
                        row[3] = wordEntry.getUrlHash().getBytes();
                        row[4] = wordEntry.toEncodedForm(true).getBytes();
                        dumpStack.push(row);
                        urlcount++;
                    }
                }
                wordcount++;
                
                // write a log
                if (System.currentTimeMillis() > messageTime) {
                    wordsPerSecond = wordcount * 1000 / (1 + System.currentTimeMillis() - startTime);
                    log.logInfo("dumping status: " + wordcount + " words done, " + ((cache.size() - wordcount) / wordsPerSecond) + " seconds remaining");
                    messageTime = System.currentTimeMillis() + 5000;
                }
            }
        }
	dumpStack.close();
        log.logSystem("dumped " + urlcount + " word/url relations in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
    }
        
    private long restore() throws IOException {
        File indexDumpFile = new File(databaseRoot, indexDumpFileName);
        if (!(indexDumpFile.exists())) return 0;
        kelondroStack dumpStack = new kelondroStack(indexDumpFile, 1024);
        log.logSystem("restore dump of index cache, " + dumpStack.size() + " word/url relations");
        long startTime = System.currentTimeMillis();
        long messageTime = System.currentTimeMillis() + 5000;
        long urlCount = 0, urlsPerSecond = 0;
        synchronized (cache) {
            Iterator i = dumpStack.iterator();
            kelondroRecords.Node node;
            String wordHash;
            plasmaWordIndexEntryContainer container;
            long creationTime;
            plasmaWordIndexEntry wordEntry;
            byte[][] row;
            while (i.hasNext()) {
                // get out one entry
                node = (kelondroRecords.Node) i.next();
                row = node.getValues();
                wordHash = new String(row[0]);
                creationTime = kelondroRecords.bytes2long(row[2]);
                wordEntry = new plasmaWordIndexEntry(new String(row[3]), new String(row[4]));

                // store to cache
                addEntry(wordHash, wordEntry, creationTime);
                urlCount++;
                
                // write a log
                if (System.currentTimeMillis() > messageTime) {
                    urlsPerSecond = 1 + urlCount * 1000 / (1 + System.currentTimeMillis() - startTime);
                    log.logInfo("restoring status: " + urlCount + " urls done, " + ((dumpStack.size() - urlCount) / urlsPerSecond) + " seconds remaining");
                    messageTime = System.currentTimeMillis() + 5000;
                }
            }
        }
	dumpStack.close();
        log.logSystem("restored " + cache.size() + " words in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
        return urlCount;
    }

    // cache settings
    
    public int maxURLinWordCache() {
        return hashScore.getScore(hashScore.getMaxObject());
    }

    public int wordCacheRAMSize() {
        return cache.size();
    }
    
    public void setMaxWords(int maxWords) {
        this.maxWords = maxWords;
    }
    
    public int[] assortmentsSizes() {
        return assortmentCluster.sizes();
    }
    
    public int size() {
        return java.lang.Math.max(assortmentCluster.sizeTotal(), java.lang.Math.max(backend.size(), cache.size()));
    }
    
    public Iterator wordHashes(String startWordHash, boolean up) {
        // here we merge 3 databases into one view:
        // - the RAM Cache
        // - the singleton File Cache
        // - the backend
        if (!(up)) throw new RuntimeException("plasmaWordIndexCache.wordHashes can only count up");
        return new kelondroMergeIterator(
                        new kelondroMergeIterator(
                                 cache.keySet().iterator(),
                                 assortmentCluster.hashConjunction(startWordHash, true),
                                 true),
                        backend.wordHashes(startWordHash, true),
                        true);
    }
    
    private int flushFromMem(String key, boolean reintegrate) {
        // this method flushes indexes out from the ram to the disc.
        // at first we check the singleton database and act accordingly
        // if we we are to flush an index, but see also an entry in the singletons, we
        // decide upn the 'reintegrate'-Flag:
        // true: do not flush to disc, but re-Integrate the singleton to the RAM
        // false: flush the singleton together with container to disc
        
        plasmaWordIndexEntryContainer container = null;
        long time;
	synchronized (cache) {
            // get the container
            container = (plasmaWordIndexEntryContainer) cache.get(key);
            if (container == null) return 0; // flushing of nonexisting key
            time = getCreationTime(key);

            // remove it from the cache
            cache.remove(key);
	    hashScore.deleteScore(key);
            hashDate.remove(key);
	}
        
        // now decide where to flush that container
        plasmaWordIndexEntryContainer flushedFromAssortment = assortmentCluster.removeFromAll(key);
        if ((flushedFromAssortment == null) || (flushedFromAssortment.size() == 0)) {
            // not found in assortments
            if (container.size() <= assortmentLimit) {
                // this fits into the assortments
                plasmaWordIndexEntryContainer feedback = assortmentCluster.storeTry(key, container);
                if (feedback == null) {
                    return container.size();
                } else {
                    // *** should care about another option here ***
                    return backend.addEntries(feedback, time);
                }
            } else {
                // store to back-end; this should be a rare case
                return backend.addEntries(container, time);
            }
        } else {
            // we have some records and must integrate them into the flush
            container.add(flushedFromAssortment);
            
	    // possibly reintegrate
	    if (reintegrate) {
		// put assortmentRecord together with container back to ram
		synchronized (cache) {
		    cache.put(key, container);
		    hashScore.setScore(key, container.size());
		    hashDate.put(key, new Long(time));
		}
		return -flushedFromAssortment.size();
	    } else {
		// add this to the backend
		return backend.addEntries(container, java.lang.Math.max(time, flushedFromAssortment.updated())) - flushedFromAssortment.size();
            }
        }	
    }
    
    private boolean flushFromAssortmentCluster(String key) {
	// this should only be called if the singleton shall be deleted or returned in an index entity
        plasmaWordIndexEntryContainer container = assortmentCluster.removeFromAll(key);
        if (container == null) {
            return false;
        } else {
            // we have a non-empty entry-container
            // integrate it to the backend
            return backend.addEntries(container, container.updated()) > 0;
        }
    }

    private synchronized int flushFromMemToLimit() {
	if ((hashScore.size() == 0) && (cache.size() == 0)) {
	    serverLog.logDebug("PLASMA INDEXING", "flushToLimit: called but cache is empty");
	    return 0;
	}
	if ((hashScore.size() == 0) && (cache.size() != 0)) {
	    serverLog.logError("PLASMA INDEXING", "flushToLimit: hashScore.size=0 but cache.size=" + cache.size());
	    return 0;
	}
	if ((hashScore.size() != 0) && (cache.size() == 0)) {
	    serverLog.logError("PLASMA INDEXING", "flushToLimit: hashScore.size=" + hashScore.size() + " but cache.size=0");
	    return 0;
	}

        int count = 0;
	//serverLog.logDebug("PLASMA INDEXING", "flushSpecific: hashScore.size=" + hashScore.size() + ", cache.size=" + cache.size());
        synchronized (hashScore) {
            String key;
            Long createTime;

            // generate flush list
            Iterator i = hashScore.scores(true);
            TreeMap[] clusterCandidate = new TreeMap[hashScore.getMaxScore()];
	    for (int k = 0; k < clusterCandidate.length; k++) clusterCandidate[k] = new TreeMap(); // by create time ordered hash-list
            while (i.hasNext()) {
		// get the entry properties
                key = (String) i.next();
                createTime = (Long) hashDate.get(key);
                count = hashScore.getScore(key);
		
		// put it into a specific ohl
		clusterCandidate[count - 1].put(createTime, key);
		//System.out.println("COUNT FOR KEY " + key + ": " + count);
            }

	    // print statistics
	    for (int k = 0; k < clusterCandidate.length; k++)
                log.logDebug("FLUSH-LIST " + (k + 1) + ": " + clusterCandidate[k].size() + " entries");

            Map.Entry entry;
            int candidateCounter;
	    count = 0;

            // flush high-scores that accumultated too much
            for (int cluster = clusterCandidate.length; cluster >= ramcacheLimit; cluster--) {
                candidateCounter = 0;
                i = clusterCandidate[cluster - 1].entrySet().iterator();
                while (i.hasNext()) {
                    entry = (Map.Entry) i.next();
                    key = (String) entry.getValue();
                    createTime = (Long) entry.getKey();
		    count += java.lang.Math.abs(flushFromMem(key, false));
		    candidateCounter += cluster;
		    log.logDebug("flushed high-cluster over limit #" + cluster + ", key=" + key + ", count=" + count + ", cachesize=" + cache.size());
                }
            }

            // flush from assortment cluster
            for (int cluster = 1; cluster <= java.lang.Math.min(clusterCandidate.length, assortmentLimit); cluster++) {
                candidateCounter = 0;
                // select a specific cluster
                i = clusterCandidate[cluster - 1].entrySet().iterator();
                // check each element in this flush-list: too old?
                while (i.hasNext()) {
                    entry = (Map.Entry) i.next();
                    key = (String) entry.getValue();
                    createTime = (Long) entry.getKey();
                    if ((createTime != null) && ((System.currentTimeMillis() - createTime.longValue()) > (20000 + (cluster * 5000)))) {
                        //log.logDebug("flushing key " + key + ", count=" + count + ", cachesize=" + cache.size() + ", singleton-size=" + singletons.size());
                        count += java.lang.Math.abs(flushFromMem(key, true));
                        candidateCounter += cluster;
                    }
                }
                if (candidateCounter > 0) log.logDebug("flushed low-cluster #" + cluster + ", count=" + count + ", candidateCounter=" + candidateCounter + ", cachesize=" + cache.size());
            }

	    // stop flushing if cache is shrinked enough
	    // avoid as possible to flush high-scores
	    if (cache.size() < this.maxWords - 100) return count;

            // flush high-scores
            for (int cluster = java.lang.Math.min(clusterCandidate.length, ramcacheLimit); cluster > assortmentLimit; cluster--) {
                candidateCounter = 0;
                i = clusterCandidate[cluster - 1].entrySet().iterator();
                while (i.hasNext()) {
		    entry = (Map.Entry) i.next();
                    key = (String) entry.getValue();
                    createTime = (Long) entry.getKey();
                    if ((createTime != null) && ((System.currentTimeMillis() - createTime.longValue()) > (600000/cluster))) {
                        count += java.lang.Math.abs(flushFromMem(key, false));
                        candidateCounter += cluster;
                        log.logDebug("flushed high-cluster below limit #" + cluster + ", key=" + key + ", count=" + count + ", cachesize=" + cache.size());
                    }
                    if (cache.size() < this.maxWords - 100) return count;
                }
            }
            
        }
        return count;
    }
    
    public plasmaWordIndexEntity getIndex(String wordHash, boolean deleteIfEmpty) {
        flushFromMem(wordHash, false);
        flushFromAssortmentCluster(wordHash);
	return backend.getIndex(wordHash, deleteIfEmpty);
    }
    
    public long getCreationTime(String wordHash) {
        Long time = (Long) hashDate.get(wordHash);
        if (time == null) return 0;
        return time.longValue();
    }
    
    public void deleteIndex(String wordHash) {
        synchronized (cache) {
            cache.remove(wordHash);
            hashScore.deleteScore(wordHash);
            hashDate.remove(wordHash);
        }
        assortmentCluster.removeFromAll(wordHash);
	backend.deleteIndex(wordHash);
    }

    public synchronized int removeEntries(String wordHash, String[] urlHashes, boolean deleteComplete) {
        flushFromMem(wordHash, false);
        flushFromAssortmentCluster(wordHash);
        return backend.removeEntries(wordHash, urlHashes, deleteComplete);
    }
    
    public synchronized int addEntries(plasmaWordIndexEntryContainer container, long updateTime) {
	//serverLog.logDebug("PLASMA INDEXING", "addEntryToIndexMem: cache.size=" + cache.size() + "; hashScore.size=" + hashScore.size());
        if (cache.size() >= this.maxWords) flushFromMemToLimit();
	//if (flushc > 0) serverLog.logDebug("PLASMA INDEXING", "addEntryToIndexMem - flushed " + flushc + " entries");

	// put new words into cache
        int added = 0;
	synchronized (cache) {
            String wordHash = container.wordHash();
	    plasmaWordIndexEntryContainer entries = (plasmaWordIndexEntryContainer) cache.get(wordHash); // null pointer exception? wordhash != null! must be cache==null
	    if (entries == null) entries = new plasmaWordIndexEntryContainer(wordHash);
            added = entries.add(container);
            if (added > 0) {
                cache.put(wordHash, entries);
                hashScore.addScore(wordHash, added);
                hashDate.put(wordHash, new Long(updateTime));
            }
	}
        //System.out.println("DEBUG: cache = " + cache.toString());
        return added;
    }

    private void addEntry(String wordHash, plasmaWordIndexEntry newEntry, long updateTime) {
        plasmaWordIndexEntryContainer entries = (plasmaWordIndexEntryContainer) cache.get(wordHash);
        if (entries == null) entries = new plasmaWordIndexEntryContainer(wordHash);
        if (entries.add(new plasmaWordIndexEntry[]{newEntry}, updateTime) > 0) {
            cache.put(wordHash, entries);
            hashScore.incScore(wordHash);
            hashDate.put(wordHash, new Long(updateTime));
        }
    }

    public void close(int waitingSeconds) {
        assortmentCluster.close();
        try {
            dump(waitingSeconds);
        } catch (IOException e){
            log.logError("unable to dump cache: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
