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
    
    // class variables
    private File databaseRoot;
    private plasmaWordIndexInterface backend;
    private TreeMap cache;
    private kelondroMScoreCluster hashScore;
    private HashMap hashDate;
    private int maxWords;
    private serverLog log;
    private plasmaWordIndexAssortment singletons;
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
        // migrate
        File oldSingletonFile = new File(databaseRoot, oldSingletonFileName);
        File newSingletonFile = new File(databaseRoot, newSingletonFileName);
        if ((oldSingletonFile.exists()) && (!(newSingletonFile.exists()))) oldSingletonFile.renameTo(newSingletonFile);
        
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
	this.singletons = new plasmaWordIndexAssortment(databaseRoot, 1, singletonBufferSize, log);

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
    
    public int singletonsSize() {
        return singletons.size();
    }
    
    public int size() {
        return java.lang.Math.max(singletons.size(), java.lang.Math.max(backend.size(), cache.size()));
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
                                 singletons.hashes(startWordHash, true, false),
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
        plasmaWordIndexAssortment.record singleton = singletons.read(key);
        if (singleton == null) {
            // not found in singletons
            if (container.size() == 1) {
                // it is a singleton: store to singleton
                singletons.store(key, singletons.newRecord(container.getOne(), time));
                return 1;
            } else {
                // store to back-end; this should be a rare case
                return backend.addEntries(container, time);
            }
        } else {
            // we have a singleton and need to integrate this in the flush
            plasmaWordIndexEntry oldEntry = singleton.entries[0];
            long oldTime = singleton.creationTime;
            if (container.contains(oldEntry.getUrlHash())) {
                // we have an double-occurrence
                if (container.size() == 1) {
                    // it is superfluous to flush this, simple do nothing
                    return 0;
                } else {
                    // we flush to the backend, and the entry from the singletons
                    singletons.remove(key);
                    return backend.addEntries(container, java.lang.Math.max(time, oldTime));
                }
            } else {
                // now we have more than one entry
                // we must remove the key from the singleton database
                singletons.remove(key);
                // .. and put it to the container
                container.add(oldEntry);
                if (reintegrate) {
                    // put singleton together with container back to ram
                    synchronized (cache) {
                        cache.put(key, container);
                        hashScore.setScore(key, container.size());
                        hashDate.put(key, new Long(time));
                    }
                    return -1;
                } else {
                    // add this to the backend
                    return backend.addEntries(container, java.lang.Math.max(time, oldTime));
                }
            }
        }	
    }
    
    private boolean flushFromSingleton(String key) {
	// this should only be called if the singleton shall be deleted or returned in an index entity
        plasmaWordIndexAssortment.record singleton = singletons.read(key);
        if (singleton == null) {
            return false;
        } else {
            // we have a singleton
            plasmaWordIndexEntry entry = (plasmaWordIndexEntry) singleton.entries[0];
            long time = singleton.creationTime;
            // remove it from the singleton database
            singletons.remove(key);
            // integrate it to the backend
            return backend.addEntries(plasmaWordIndexEntryContainer.instantContainer(key, entry), time) > 0;
        }
    }

    private int flushFromMemToLimit() {
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
            TreeMap[] al = new TreeMap[hashScore.getMaxScore() + 1];
	    for (int k = 0; k < al.length; k++) al[k] = new TreeMap(); // by create time ordered hash-list
            while (i.hasNext()) {
		// get the entry properties
                key = (String) i.next();
                createTime = (Long) hashDate.get(key);
                count = hashScore.getScore(key);
		
		// put it into a specific ohl
		al[count].put(createTime, key);
		//System.out.println("COUNT FOR KEY " + key + ": " + count);
            }

	    // print statistics
	    for (int k = 1; k < al.length; k++) log.logDebug("FLUSH-LIST " + k + ": " + al[k].size() + " entries");

            // flush singletons
	    i = al[1].entrySet().iterator();
	    Map.Entry entry;
	    while (i.hasNext()) {
		entry = (Map.Entry) i.next();
		key = (String) entry.getValue();
		createTime = (Long) entry.getKey();
		if ((createTime != null) && ((System.currentTimeMillis() - createTime.longValue()) > 90000)) {
		    //log.logDebug("flushing singleton-key " + key + ", count=" + count + ", cachesize=" + cache.size() + ", singleton-size=" + singletons.size());
		    count += flushFromMem((String) key, true);
		}
	    }

            // flush high-scores
            for (int k = al.length - 1; k >= 2; k--) {
                i = al[k].entrySet().iterator();
                while (i.hasNext()) {
                    entry = (Map.Entry) i.next();
                    key = (String) entry.getValue();
                    createTime = (Long) entry.getKey();
                    if ((createTime != null) && ((System.currentTimeMillis() - createTime.longValue()) > (600000/k))) {
                        //log.logDebug("flushing high-key " + key + ", count=" + count + ", cachesize=" + cache.size() + ", singleton-size=" + singletons.size());
                        count += flushFromMem(key, false);
                    }
                    if (count > 2000) return count;
                }
            }
            
        }
        return count;
    }
    
    public plasmaWordIndexEntity getIndex(String wordHash, boolean deleteIfEmpty) {
        flushFromMem(wordHash, false);
        flushFromSingleton(wordHash);
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
        singletons.remove(wordHash);
	backend.deleteIndex(wordHash);
    }

    public synchronized int removeEntries(String wordHash, String[] urlHashes, boolean deleteComplete) {
        flushFromMem(wordHash, false);
        flushFromSingleton(wordHash);
        return backend.removeEntries(wordHash, urlHashes, deleteComplete);
    }
    
    public synchronized int addEntries(plasmaWordIndexEntryContainer container, long creationTime) {
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
                hashDate.put(wordHash, new Long(creationTime));
            }
	}
        //System.out.println("DEBUG: cache = " + cache.toString());
        return added;
    }

    private void addEntry(String wordHash, plasmaWordIndexEntry newEntry, long creationTime) {
        plasmaWordIndexEntryContainer entries = (plasmaWordIndexEntryContainer) cache.get(wordHash);
        if (entries == null) entries = new plasmaWordIndexEntryContainer(wordHash);
        if (entries.add(newEntry)) {
            cache.put(wordHash, entries);
            hashScore.incScore(wordHash);
            hashDate.put(wordHash, new Long(creationTime));
        }
    }

    public void close(int waitingSeconds) {
        singletons.close();
        try {
            dump(waitingSeconds);
        } catch (IOException e){
            log.logError("unable to dump cache: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
