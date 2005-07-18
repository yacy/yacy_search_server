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

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroMergeIterator;
import de.anomic.kelondro.kelondroRecords;
import de.anomic.kelondro.kelondroStack;
import de.anomic.kelondro.kelondroArray;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeedDB;

public final class plasmaWordIndexCache implements plasmaWordIndexInterface {
    
    // environment constants
    private static final String indexArrayFileName = "indexDump1.array";
    private static final String indexStackFileName = "indexDump0.stack";
    private static final String oldSingletonFileName = "indexSingletons0.db";
    private static final String newSingletonFileName = "indexAssortment001.db";
    private static final String indexAssortmentClusterPath = "ACLUSTER";
    private static final int assortmentLimit = 50;
    private static final int ramCacheLimit = 200;
    
    
    // class variables
    private File databaseRoot;
    private plasmaWordIndexInterface backend;
    private TreeMap cache;
    private kelondroMScoreCluster hashScore;
    private kelondroMScoreCluster hashDate;
    private long startTime;
    private int maxWords;
    private serverLog log;
    private plasmaWordIndexAssortmentCluster assortmentCluster;
    private int assortmentBufferSize; //kb
    private flush flushThread;

    // calculated constants
    private static String minKey, maxKey;
    static {
	maxKey = "";
	for (int i = 0; i < yacySeedDB.commonHashLength; i++) maxKey += 'z';
	minKey = "";
	for (int i = 0; i < yacySeedDB.commonHashLength; i++) maxKey += '-';
    }

    public plasmaWordIndexCache(File databaseRoot, plasmaWordIndexInterface backend, int assortmentbufferkb, serverLog log) {
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
        
        // create flushing thread
        flushThread = new flush();
        
        // creates a new index cache
        // the cache has a back-end where indexes that do not fit in the cache are flushed
        this.databaseRoot = databaseRoot;
        this.assortmentBufferSize = assortmentbufferkb;
        this.cache = new TreeMap();
	this.hashScore = new kelondroMScoreCluster();
        this.hashDate  = new kelondroMScoreCluster();
        this.startTime = System.currentTimeMillis();
	this.maxWords = 10000;
        this.backend = backend;
        this.log = log;
	this.assortmentCluster = new plasmaWordIndexAssortmentCluster(assortmentClusterPath, assortmentLimit, assortmentBufferSize, log);

        // read in dump of last session
        try {
            restore();
        } catch (IOException e){
            log.logError("unable to restore cache dump: " + e.getMessage());
            e.printStackTrace();
        }
        
        // start permanent flushing
        flushThread.start();
    }

    
    private void dump(int waitingSeconds) throws IOException {
        log.logSystem("creating dump for index cache, " + cache.size() + " words (and much more urls)");
        File indexDumpFile = new File(databaseRoot, indexArrayFileName);
        if (indexDumpFile.exists()) indexDumpFile.delete();
        kelondroArray dumpArray = new kelondroArray(indexDumpFile, plasmaWordIndexAssortment.bufferStructureBasis, 0);
        long startTime = System.currentTimeMillis();
        long messageTime = System.currentTimeMillis() + 5000;
        long wordsPerSecond = 0, wordcount = 0, urlcount = 0;
        synchronized (cache) {
            Iterator i = cache.entrySet().iterator();
            Map.Entry entry;
            String wordHash;
            plasmaWordIndexEntryContainer container;
            long updateTime;
            plasmaWordIndexEntry wordEntry;
            byte[][] row = new byte[5][];
            while (i.hasNext()) {
                // get entries
                entry = (Map.Entry) i.next();
                wordHash = (String) entry.getKey();
                updateTime = getUpdateTime(wordHash);
                container = (plasmaWordIndexEntryContainer) entry.getValue();

                // put entries on stack
                if (container != null) {
                    Iterator ci = container.entries();
                    while (ci.hasNext()) {
                        wordEntry = (plasmaWordIndexEntry) ci.next();
                        row[0] = wordHash.getBytes();
                        row[1] = kelondroRecords.long2bytes(container.size(), 4);
                        row[2] = kelondroRecords.long2bytes(updateTime, 8);
                        row[3] = wordEntry.getUrlHash().getBytes();
                        row[4] = wordEntry.toEncodedForm(true).getBytes();
                        dumpArray.set((int) urlcount++, row);
                    }
                }
                wordcount++;
                i.remove(); // free some mem
                
                // write a log
                if (System.currentTimeMillis() > messageTime) {
                    System.gc(); // for better statistic
                    wordsPerSecond = wordcount * 1000 / (1 + System.currentTimeMillis() - startTime);
                    log.logInfo("dumping status: " + wordcount + " words done, " + (cache.size() / (wordsPerSecond + 1)) + " seconds remaining, free mem = " + (Runtime.getRuntime().freeMemory() / 1024 / 1024) + "MB");
                    messageTime = System.currentTimeMillis() + 5000;
                }
            }
        }
	dumpArray.close();
        log.logSystem("dumped " + urlcount + " word/url relations in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
    }
    
    private long restore() throws IOException {
        if ((new File(databaseRoot, indexArrayFileName)).exists())
            return restoreArray();
        else
            return restoreStack();
    }
    
    private long restoreArray() throws IOException {
        File indexDumpFile = new File(databaseRoot, indexArrayFileName);
        if (!(indexDumpFile.exists())) return 0;
        kelondroArray dumpArray = new kelondroArray(indexDumpFile);
        log.logSystem("restore array dump of index cache, " + dumpArray.size() + " word/url relations");
        long startTime = System.currentTimeMillis();
        long messageTime = System.currentTimeMillis() + 5000;
        long urlCount = 0, urlsPerSecond = 0;
        try {
            synchronized (cache) {
                int i = dumpArray.size();
                String wordHash;
                plasmaWordIndexEntryContainer container;
                long creationTime;
                plasmaWordIndexEntry wordEntry;
                byte[][] row;
                Runtime rt = Runtime.getRuntime();
                while (i-- > 0) {
                    // get out one entry
                    row = dumpArray.get(i);
                    wordHash = new String(row[0]);
                    creationTime = kelondroRecords.bytes2long(row[2]);
                    wordEntry = new plasmaWordIndexEntry(new String(row[3]), new String(row[4]));
                    // store to cache
                    addEntry(wordHash, wordEntry, creationTime);
                    urlCount++;
                    // protect against memory shortage
                    while (rt.freeMemory() < 1000000) {flushFromMem(); java.lang.System.gc();}
                    // write a log
                    if (System.currentTimeMillis() > messageTime) {
                        System.gc(); // for better statistic
                        urlsPerSecond = 1 + urlCount * 1000 / (1 + System.currentTimeMillis() - startTime);
                        log.logInfo("restoring status: " + urlCount + " urls done, " + (i / urlsPerSecond) + " seconds remaining, free mem = " + (Runtime.getRuntime().freeMemory() / 1024 / 1024) + "MB");
                        messageTime = System.currentTimeMillis() + 5000;
                    }
                }
            }
            
            dumpArray.close();
            log.logSystem("restored " + cache.size() + " words in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
        } catch (kelondroException e) {
            // restore failed
            log.logError("restore of indexCache array dump failed: " + e.getMessage());
            e.printStackTrace();
        }
        return urlCount;
    }

    /*
    private void dump(int waitingSeconds) throws IOException {
        log.logSystem("creating dump for index cache, " + cache.size() + " words (and much more urls)");
        File indexDumpFile = new File(databaseRoot, indexStackFileName);
        if (indexDumpFile.exists()) indexDumpFile.delete();
        kelondroStack dumpStack = new kelondroStack(indexDumpFile, 1024, plasmaWordIndexAssortment.bufferStructureBasis);
        long startTime = System.currentTimeMillis();
        long messageTime = System.currentTimeMillis() + 5000;
        long wordsPerSecond = 0, wordcount = 0, urlcount = 0;
        synchronized (cache) {
            Iterator i = cache.entrySet().iterator();
            Map.Entry entry;
            String wordHash;
            plasmaWordIndexEntryContainer container;
            long updateTime;
            plasmaWordIndexEntry wordEntry;
            byte[][] row = new byte[5][];
            System.gc(); // this can speed up the assortment, because they may better use the cache
            while (i.hasNext()) {
                // get entries
                entry = (Map.Entry) i.next();
                wordHash = (String) entry.getKey();
                updateTime = getUpdateTime(wordHash);
                container = (plasmaWordIndexEntryContainer) entry.getValue();

                // put entries on stack
                if (container != null) {
                    Iterator ci = container.entries();
                    while (ci.hasNext()) {
                        wordEntry = (plasmaWordIndexEntry) ci.next();
                        row[0] = wordHash.getBytes();
                        row[1] = kelondroRecords.long2bytes(container.size(), 4);
                        row[2] = kelondroRecords.long2bytes(updateTime, 8);
                        row[3] = wordEntry.getUrlHash().getBytes();
                        row[4] = wordEntry.toEncodedForm(true).getBytes();
                        dumpStack.push(row);
                        urlcount++;
                    }
                }
                wordcount++;
                i.remove(); // free some mem
                
                // write a log
                if (System.currentTimeMillis() > messageTime) {
                    System.gc(); // for better statistic
                    wordsPerSecond = wordcount * 1000 / (1 + System.currentTimeMillis() - startTime);
                    log.logInfo("dumping status: " + wordcount + " words done, " + (cache.size() / (wordsPerSecond + 1)) + " seconds remaining, free mem = " + (Runtime.getRuntime().freeMemory() / 1024 / 1024) + "MB");
                    messageTime = System.currentTimeMillis() + 5000;
                }
            }
        }
	dumpStack.close();
        log.logSystem("dumped " + urlcount + " word/url relations in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
    }
        
    */
    
    private long restoreStack() throws IOException {
        File indexDumpFile = new File(databaseRoot, indexStackFileName);
        if (!(indexDumpFile.exists())) return 0;
        kelondroStack dumpStack = new kelondroStack(indexDumpFile, 1024);
        log.logSystem("restore stack dump of index cache, " + dumpStack.size() + " word/url relations");
        long startTime = System.currentTimeMillis();
        long messageTime = System.currentTimeMillis() + 5000;
        long urlCount = 0, urlsPerSecond = 0;
        try {
            synchronized (cache) {
                Iterator i = dumpStack.iterator();
                kelondroRecords.Node node;
                String wordHash;
                plasmaWordIndexEntryContainer container;
                long creationTime;
                plasmaWordIndexEntry wordEntry;
                byte[][] row;
                Runtime rt = Runtime.getRuntime();
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
                    // protect against memory shortage
                    while (rt.freeMemory() < 1000000) {flushFromMem(); java.lang.System.gc();}
                    // write a log
                    if (System.currentTimeMillis() > messageTime) {
                        System.gc(); // for better statistic
                        urlsPerSecond = 1 + urlCount * 1000 / (1 + System.currentTimeMillis() - startTime);
                        log.logInfo("restoring status: " + urlCount + " urls done, " + ((dumpStack.size() - urlCount) / urlsPerSecond) + " seconds remaining, free mem = " + (Runtime.getRuntime().freeMemory() / 1024 / 1024) + "MB");
                        messageTime = System.currentTimeMillis() + 5000;
                    }
                }
            }
            
            dumpStack.close();
            log.logSystem("restored " + cache.size() + " words in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
        } catch (kelondroException e) {
            // restore failed
            log.logError("restore of indexCache dump failed: " + e.getMessage());
            e.printStackTrace();
        }
        indexDumpFile.delete();
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
        // - the assortmentCluster File Cache
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
    
    private class flush extends Thread {
        boolean terminate, pause;
        
        public flush() {
            terminate = false;
            pause = false;
        }
        
        public void run() {
            String nextHash;
            Runtime rt = Runtime.getRuntime();
            while (!terminate) {
                if (pause) {
                    try {this.sleep(300);} catch (InterruptedException e) {}
                } else {
                    flushFromMem();
                    if ((rt.freeMemory() > 1000000) || (cache.size() == 0)) try {
                        this.sleep(10 + java.lang.Math.min(1000, 10 * maxWords/(cache.size() + 1)));
                    } catch (InterruptedException e) {}
                }              
            }
        }
        
        public void pause() {
            pause = true;
        }
        
        public void proceed() {
            pause = false;
        }
        
        public void terminate() {
            terminate = true;
        }
    }
    
    private void flushFromMem() {
        // select appropriate hash
        // we have 2 different methods to find a good hash:
        // - the oldest entry in the cache
        // - the entry with maximum count
        if (cache.size() == 0) return;
        flushThread.pause();
        try {
            String hash = (String) hashScore.getMaxObject();
            if (hash == null) {
                flushThread.proceed();
                return;
            }
            int count = hashScore.getMaxScore();
            long time = longTime(hashDate.getScore(hash));
            if ((count > ramCacheLimit) ||
                ((count > assortmentLimit) && (System.currentTimeMillis() - time > 10000))) {
                // flush high-score entries
                flushFromMem(hash, true);
            } else {
                // flush oldest entries
                hash = (String) hashDate.getMinObject();
                flushFromMem(hash, true);
            }
        } catch (Exception e) {
            log.logError("flushFromMem: " + e.getMessage());
            e.printStackTrace();
        }
        flushThread.proceed();
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
            time = getUpdateTime(key);

            // remove it from the cache
            cache.remove(key);
	    hashScore.deleteScore(key);
            hashDate.deleteScore(key);
	}
        
        // now decide where to flush that container
        if (container.size() <= assortmentLimit) {
            // this fits into the assortments
            plasmaWordIndexEntryContainer feedback = assortmentCluster.storeTry(key, container);
            if (feedback == null) {
                return container.size();
            } else if ((container.size() != feedback.size()) && (reintegrate)) {
                // put assortmentRecord together with container back to ram
                synchronized (cache) {
                    cache.put(key, feedback);
                    hashScore.setScore(key, feedback.size());
                    hashDate.setScore(key, intTime(time));
                }
                return container.size() - feedback.size();
            } else {
                // *** should care about another option here ***
                return backend.addEntries(feedback, time);
            }
        } else {
            // store to back-end; this should be a rare case
            return backend.addEntries(container, time);
        }

    }
    
    private int intTime(long longTime) {
        return (int) ((longTime - startTime) / 1000);
    }
    
    private long longTime(int intTime) {
        return ((long) intTime) * ((long) 1000) + startTime;
    }
    
    private boolean flushFromAssortmentCluster(String key) {
	// this should only be called if the assortment shall be deleted or returned in an index entity
        plasmaWordIndexEntryContainer container = assortmentCluster.removeFromAll(key);
        if (container == null) {
            return false;
        } else {
            // we have a non-empty entry-container
            // integrate it to the backend
            return backend.addEntries(container, container.updated()) > 0;
        }
    }

    public plasmaWordIndexEntity getIndex(String wordHash, boolean deleteIfEmpty) {
        flushThread.pause();
        flushFromMem(wordHash, false);
        flushFromAssortmentCluster(wordHash);
        flushThread.proceed();
	return backend.getIndex(wordHash, deleteIfEmpty);
    }
    
    public long getUpdateTime(String wordHash) {
        plasmaWordIndexEntryContainer entries = (plasmaWordIndexEntryContainer) cache.get(wordHash);
        if (entries == null) return 0;
        return entries.updated();
        /*
        Long time = new Long(longTime(hashDate.getScore(wordHash)));
        if (time == null) return 0;
        return time.longValue();
        */
    }
    
    public void deleteIndex(String wordHash) {
        flushThread.pause();
        synchronized (cache) {
            cache.remove(wordHash);
            hashScore.deleteScore(wordHash);
            hashDate.deleteScore(wordHash);
        }
        assortmentCluster.removeFromAll(wordHash);
	backend.deleteIndex(wordHash);
        flushThread.proceed();
    }

    public synchronized int removeEntries(String wordHash, String[] urlHashes, boolean deleteComplete) {
        flushThread.pause();
        flushFromMem(wordHash, false);
        flushFromAssortmentCluster(wordHash);
        int removed = backend.removeEntries(wordHash, urlHashes, deleteComplete);
        flushThread.proceed();
        return removed;
    }
    
    public synchronized int addEntries(plasmaWordIndexEntryContainer container, long updateTime) {
        flushThread.pause();
	//serverLog.logDebug("PLASMA INDEXING", "addEntryToIndexMem: cache.size=" + cache.size() + "; hashScore.size=" + hashScore.size());
        while (cache.size() >= this.maxWords) flushFromMem();
        if ((cache.size() > 10000) && (Runtime.getRuntime().freeMemory() < 5000000)) flushFromMem();
        if ((cache.size() > 0) && (Runtime.getRuntime().freeMemory() < 1000000)) flushFromMem();
        
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
                hashDate.setScore(wordHash, intTime(updateTime));
            }
            entries = null;
	}
        //System.out.println("DEBUG: cache = " + cache.toString());
        flushThread.proceed();
        return added;
    }

    private void addEntry(String wordHash, plasmaWordIndexEntry newEntry, long updateTime) {
        flushThread.pause();
	plasmaWordIndexEntryContainer container = (plasmaWordIndexEntryContainer) cache.get(wordHash);
        if (container == null) container = new plasmaWordIndexEntryContainer(wordHash);
        plasmaWordIndexEntry[] entries = new plasmaWordIndexEntry[]{newEntry};
        if (container.add(entries, updateTime) > 0) {
            cache.put(wordHash, container);
            hashScore.incScore(wordHash);
            hashDate.setScore(wordHash, intTime(updateTime));
        }
        entries = null;
        container = null;
        flushThread.proceed();
    }

    public void close(int waitingSeconds) {
        // stop permanent flushing
        flushThread.terminate();
        try {flushThread.join(5000);} catch (InterruptedException e) {}
        
        // close cluster
        assortmentCluster.close();
        try {
            dump(waitingSeconds);
        } catch (IOException e){
            log.logError("unable to dump cache: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
