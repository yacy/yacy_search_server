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

public class plasmaWordIndexCache implements plasmaWordIndexInterface {
    
    // environment constants
    private static final String indexDumpFileName = "indexDump0.stack";
    private static final String singletonFileName = "indexSingletons0.db";
    private static final int[] bufferStructure = new int[]{
        plasmaWordIndexEntry.wordHashLength, // a wordHash
        4,                                   // occurrence counter
        8,                                   // timestamp of last access
        plasmaWordIndexEntry.urlHashLength,  // corresponding URL hash
        plasmaWordIndexEntry.attrSpaceLong   // URL attributes
    };
    
    // class variables
    private File databaseRoot;
    private plasmaWordIndexInterface backend;
    private TreeMap cache;
    private kelondroMScoreCluster hashScore;
    private HashMap hashDate;
    private int maxWords;
    private serverLog log;
    private kelondroTree singletons;
    private long singletonBufferSize;

    // calculated constants
    private static String minKey, maxKey;
    static {
	maxKey = "";
	for (int i = 0; i < yacySeedDB.commonHashLength; i++) maxKey += 'z';
	minKey = "";
	for (int i = 0; i < yacySeedDB.commonHashLength; i++) maxKey += '-';
    }

    public plasmaWordIndexCache(File databaseRoot, plasmaWordIndexInterface backend, long singletonBufferSize, serverLog log) {
        // creates a new index cache
        // the cache has a back-end where indexes that do not fit in the cache are flushed
        this.databaseRoot = databaseRoot;
        this.singletonBufferSize = singletonBufferSize;
        this.cache = new TreeMap();
	this.hashScore = new kelondroMScoreCluster();
        this.hashDate  = new HashMap();
	this.maxWords = 10000;
        this.backend = backend;
        this.log = log;
        File singletonFile = new File(databaseRoot, singletonFileName);
        if (singletonFile.exists()) {
            // open existing singeton tree file
            try {
                singletons = new kelondroTree(singletonFile, singletonBufferSize);
                log.logSystem("Opened Singleton Database, " + singletons.size() + " entries."); 
            } catch (IOException e){
                log.logError("unable to open singleton database: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // create new sigleton tree file
            try {
                singletons = new kelondroTree(singletonFile, singletonBufferSize, bufferStructure);
                log.logSystem("Created new Singleton Database"); 
            } catch (IOException e){
                log.logError("unable to create singleton database: " + e.getMessage());
                e.printStackTrace();
            }
        }
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
        kelondroStack dumpStack = new kelondroStack(indexDumpFile, 0, bufferStructure);
        long startTime = System.currentTimeMillis();
        long messageTime = System.currentTimeMillis() + 5000;
        long wordsPerSecond = 0, wordcount = 0, urlcount = 0;
        synchronized (cache) {
            //Iterator i = cache.entrySet().iterator();
            Iterator i = hashScore.scores(false);
            //Map.Entry entry;
            String wordHash;
            plasmaWordIndexEntryContainer container;
            long creationTime;
            plasmaWordIndexEntry wordEntry;
            byte[][] row = new byte[5][];
            while (i.hasNext()) {
                // get entries
                //entry = (Map.Entry) i.next();
                wordHash = (String) i.next();
                //wordHash = (String) entry.getKey();
                creationTime = getCreationTime(wordHash);
                container = (plasmaWordIndexEntryContainer) cache.get(wordHash);
                //container = (plasmaWordIndexEntryContainer) entry.getValue();

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
        log.logSystem("dumped " + urlcount + " word/url relations in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
    }
        
    private long restore() throws IOException {
        File indexDumpFile = new File(databaseRoot, indexDumpFileName);
        if (!(indexDumpFile.exists())) return 0;
        kelondroStack dumpStack = new kelondroStack(indexDumpFile, 0);
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
        log.logSystem("restored " + cache.size() + " words in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
        return urlCount;
    }

    // singleton access methods
    
    private void storeSingleton(String wordHash, plasmaWordIndexEntry entry, long creationTime) {
        // stores a word index to singleton database
        // this throws an exception if the word hash already existed
        //log.logDebug("storeSingleton: wordHash=" + wordHash + ", urlHash=" + entry.getUrlHash() + ", time=" + creationTime);
        byte[][] row = new byte[5][];
        row[0] = wordHash.getBytes();
        row[1] = kelondroRecords.long2bytes(1, 4);
        row[2] = kelondroRecords.long2bytes(creationTime, 8);
        row[3] = entry.getUrlHash().getBytes();
        row[4] = entry.toEncodedForm(true).getBytes();
        byte[][] oldrow = null;
        try {
            oldrow = singletons.put(row);
        } catch (IOException e) {
            log.logFailure("storeSingleton/IO-error: " + e.getMessage() + " - reset singleton-DB");
            e.printStackTrace();
            resetSingletonDatabase();
        } catch (kelondroException e) {
            log.logFailure("storeSingleton/kelondro-error: " + e.getMessage() + " - reset singleton-DB");
            e.printStackTrace();
            resetSingletonDatabase();
        }
        if (oldrow != null) throw new RuntimeException("Store to singleton ambiguous");
    }
    
    public Object[] /*{plasmaWordIndexEntry, Long(creationTime)}*/ readSingleton(String wordHash) {
        // returns a single word index from singleton database; returns null if index does not exist
        //log.logDebug("readSingleton: wordHash=" + wordHash);
        byte[][] row = null;
        try {
            row = singletons.get(wordHash.getBytes());
        } catch (IOException e) {
            log.logFailure("readSingleton/IO-error: " + e.getMessage() + " - reset singleton-DB");
            e.printStackTrace();
            resetSingletonDatabase();
        } catch (kelondroException e) {
            log.logFailure("readSingleton/kelondro-error: " + e.getMessage() + " - reset singleton-DB");
            e.printStackTrace();
            resetSingletonDatabase();
        }
        if (row == null) return null;
        long creationTime = kelondroRecords.bytes2long(row[2]);
        plasmaWordIndexEntry wordEntry = new plasmaWordIndexEntry(new String(row[3]), new String(row[4]));
        return new Object[]{wordEntry, new Long(creationTime)};
    }
    
    private void removeSingleton(String wordHash) {
        // deletes a word index from singleton database
        //log.logDebug("removeSingleton: wordHash=" + wordHash);
        byte[][] row = null;
        try {
            row = singletons.remove(wordHash.getBytes());
        } catch (IOException e) {
            log.logFailure("removeSingleton/IO-error: " + e.getMessage() + " - reset singleton-DB");
            e.printStackTrace();
            resetSingletonDatabase();
        } catch (kelondroException e) {
            log.logFailure("removeSingleton/kelondro-error: " + e.getMessage() + " - reset singleton-DB");
            e.printStackTrace();
            resetSingletonDatabase();
        }
    }
    
    private void resetSingletonDatabase() {
        // deletes the singleton database and creates a new one
        try {
            singletons.close();
        } catch (IOException e) {}
        File singletonFile = new File(databaseRoot, singletonFileName);
        if (!(singletonFile.delete())) throw new RuntimeException("cannot delete singleton database");
        try {
            singletons = new kelondroTree(singletonFile, singletonBufferSize, bufferStructure);
        } catch (IOException e){
            log.logError("unable to re-create singleton database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public Iterator singletonHashes(String startWordHash, boolean up, boolean rot) {
        try {
            return singletons.keys(up, rot, startWordHash.getBytes());
        } catch (IOException e) {
            log.logFailure("iterateSingleton/IO-error: " + e.getMessage() + " - reset singleton-DB");
            e.printStackTrace();
            resetSingletonDatabase();
            return null;
        }
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
                                 singletonHashes(startWordHash, true, false),
                                 true),
                        backend.wordHashes(startWordHash, true),
                        true);
    }
    
    private int flushFromMem(String key) {
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
        Object[] singleton = readSingleton(key);
        if (singleton == null) {
            if (container.size() == 1) {
                // store to singleton
                storeSingleton(key, container.getOne(), time);
                return 1;
            } else {
                // store to back-end
                return backend.addEntries(container, time);
            }
        } else {
            // we have a singleton and need to integrate this in the flush
            plasmaWordIndexEntry oldEntry = (plasmaWordIndexEntry) singleton[0];
            long oldTime = ((Long) singleton[1]).longValue();
            if (container.contains(oldEntry.getUrlHash())) {
                // we have an double-occurrence
                if (container.size() == 1) {
                    // it is superfluous to flush this, simple do nothing
                    return 0;
                } else {
                    // we flush to the backend, but remove the entry from the singletons
                    removeSingleton(key);
                    return backend.addEntries(container, java.lang.Math.max(time, oldTime));
                }
            } else {
                // now we have more than one entry,
                // we must remove the key from the singleton database
                removeSingleton(key);
                // add this to the backend
                container.add(oldEntry);
                return backend.addEntries(container, java.lang.Math.max(time, oldTime));
            }
        }	
    }
    
    private boolean flushFromSingleton(String key) {
        Object[] singleton = readSingleton(key);
        if (singleton == null) {
            return false;
        } else {
            // we have a singleton
            plasmaWordIndexEntry entry = (plasmaWordIndexEntry) singleton[0];
            long time = ((Long) singleton[1]).longValue();
            // remove it from the singleton database
            removeSingleton(key);
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

	//serverLog.logDebug("PLASMA INDEXING", "flushSpecific: hashScore.size=" + hashScore.size() + ", cache.size=" + cache.size());
        int total = 0;
        synchronized (hashScore) {
            String key;
            int count;
            Long createTime;
            
            // flush high-scores
            while ((total < 100) && (hashScore.size() >= maxWords)) {
                key = (String) hashScore.getMaxObject();
                createTime = (Long) hashDate.get(key);
                count = hashScore.getScore(key);
                if (count < 5) {
                    log.logWarning("flushing of high-key " + key + " not appropriate (too less entries, count=" + count + "): increase cache size");
                    break;
                }
                if ((createTime != null) && ((System.currentTimeMillis() - createTime.longValue()) < 9000)) {
                    //log.logDebug("high-key " + key + " is too fresh, interrupting flush (count=" + count + ", cachesize=" + cache.size()  + ", singleton-size=" + singletons.size() + ")");
                    break;
                }
                //log.logDebug("flushing high-key " + key + ", count=" + count + ", cachesize=" + cache.size() + ", singleton-size=" + singletons.size());
                total += flushFromMem(key);
            }
            
            // flush singletons
            while ((total < 200) && (hashScore.size() >= maxWords)) {
                key = (String) hashScore.getMinObject();
                createTime = (Long) hashDate.get(key);
                count = hashScore.getScore(key);
                if (count > 1) {
                    //log.logDebug("flush of singleton-key " + key + ": count too high (count=" + count + ")");
                    break;
                }
                if ((createTime != null) && ((System.currentTimeMillis() - createTime.longValue()) < 9000)) {
                    //log.logDebug("singleton-key " + key + " is too fresh, interruptiong flush (count=" + count + ", cachesize=" + cache.size()  + ", singleton-size=" + singletons.size() + ")");
                    break;
                }
                //log.logDebug("flushing singleton-key " + key + ", count=" + count + ", cachesize=" + cache.size() + ", singleton-size=" + singletons.size());
                total += flushFromMem(key);
            }
        }
        return total;
    }
    
    public plasmaWordIndexEntity getIndex(String wordHash, boolean deleteIfEmpty) {
        flushFromMem(wordHash);
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
        removeSingleton(wordHash);
	backend.deleteIndex(wordHash);
    }

    public int removeEntries(String wordHash, String[] urlHashes, boolean deleteComplete) {
        flushFromMem(wordHash);
        flushFromSingleton(wordHash);
        return backend.removeEntries(wordHash, urlHashes, deleteComplete);
    }
    
    public int addEntries(plasmaWordIndexEntryContainer container, long creationTime) {
	//serverLog.logDebug("PLASMA INDEXING", "addEntryToIndexMem: cache.size=" + cache.size() + "; hashScore.size=" + hashScore.size());
        flushFromMemToLimit();
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
        try {
            singletons.close();
        } catch (IOException e){
            log.logError("unable to close singleton database: " + e.getMessage());
            e.printStackTrace();
        }
        try {
            dump(waitingSeconds);
        } catch (IOException e){
            log.logError("unable to dump cache: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
