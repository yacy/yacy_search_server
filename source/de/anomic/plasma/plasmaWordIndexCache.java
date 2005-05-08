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
    
    private static final String indexDumpFileName = "indexDump0.stack";
    
    static String minKey, maxKey;

    // class variables
    private File databaseRoot;
    private plasmaWordIndexInterface backend;
    private TreeMap cache;
    private kelondroMScoreCluster hashScore;
    private HashMap hashDate;
    private int maxWords;
    private serverLog log;

    static {
	maxKey = "";
	for (int i = 0; i < yacySeedDB.commonHashLength; i++) maxKey += 'z';
	minKey = "";
	for (int i = 0; i < yacySeedDB.commonHashLength; i++) maxKey += '-';
    }

    public plasmaWordIndexCache(File databaseRoot, plasmaWordIndexInterface backend, serverLog log) {
        this.databaseRoot = databaseRoot;
        this.cache = new TreeMap();
	this.hashScore = new kelondroMScoreCluster();
        this.hashDate  = new HashMap();
	this.maxWords = 10000;
        this.backend = backend;
        this.log = log;
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
        kelondroStack dumpStack = new kelondroStack(indexDumpFile, 0, new int[]{plasmaWordIndexEntry.wordHashLength, 4, 8, plasmaWordIndexEntry.wordHashLength, plasmaWordIndexEntry.attrSpaceLong});
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
            String wordHash, urlHash;
            plasmaWordIndexEntryContainer container;
            long creationTime;
            plasmaWordIndexEntry wordEntry;
            byte[][] row = new byte[4][];
            while (i.hasNext()) {
                // get out one entry
                node = (kelondroRecords.Node) i.next();
                row = node.getValues();
                wordHash = new String(row[0]);
                creationTime = kelondroRecords.bytes2long(row[2]);
                urlHash = new String(row[3]);
                wordEntry = new plasmaWordIndexEntry(urlHash, new String(row[4]));

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
        if (backend.size() > cache.size()) return backend.size(); else return cache.size();
    }
    
    public Iterator wordHashes(String startWordHash, boolean up) {
        if (!(up)) throw new RuntimeException("plasmaWordIndexCache.wordHashes can only count up");
        return new iterateCombined(cache.keySet().iterator(), backend.wordHashes(startWordHash, true), true);
    }
    
    public class iterateCombined implements Iterator {
        
        Comparator comp;
        Iterator a, b;
        String na, nb;
        boolean up;
        
        public iterateCombined(Iterator a, Iterator b, boolean up) {
            this.a = a;
            this.b = b;
            this.up = up;
            this.comp = kelondroMSetTools.fastStringComparator(up);
            nexta();
            nextb();
        }
 
        private void nexta() {
            if (a.hasNext()) na = (String) a.next(); else na = null;
        }
        private void nextb() {
            if (b.hasNext()) nb = (String) b.next(); else nb = null;
        }
        
        public boolean hasNext() {
            return (na != null) || (nb != null);
        }
        
        public Object next() {
            String s;
            if (na == null) {
                s = nb;
                nextb();
                return s;
            }
            if (nb == null) {
                s = na;
                nexta();
                return s;
            }
            // compare the strings
            int c = comp.compare(na, nb);
            if (c == 0) {
                s = na;
                //System.out.println("Iterate Hash: take " + s + " from file&cache");
                nexta();
                nextb();
                return s;
            } else if ((up) && (c < 0)) {
                s = na;
                nexta();
                return s;
            } else {
                s = nb;
                nextb();
                return s;
            }
        }
        
        public void remove() {
            
        }
    }
    
    private int flushKey(String key) {
        plasmaWordIndexEntryContainer container = null;
        long time;
	synchronized (cache) {
            container = (plasmaWordIndexEntryContainer) cache.get(key);
            if (container == null) return 0; // flushing of nonexisting key
            time = getCreationTime(key);
	    cache.remove(key);
	    hashScore.deleteScore(key);
            hashDate.remove(key);
	}
	return backend.addEntries(container, time);
    }

    private int flushToLimit() {
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
            while (hashScore.size() >= maxWords) {
                key = (String) hashScore.getMaxObject();
                createTime = (Long) hashDate.get(key);
                count = hashScore.getScore(key);
                if ((createTime != null) && ((System.currentTimeMillis() - createTime.longValue()) < 9000)) {
                    log.logDebug("key " + key + " is too fresh, abandon flush (count=" + count + ", cachesize=" + cache.size() + ")");
                    break;
                }
                if (count < 5) log.logWarning("flushing of key " + key + " not appropriate (too less entries, count=" + count + "): increase cache size");
                log.logDebug("flushing key " + key + ", count=" + count + ", cachesize=" + cache.size());
                total += flushKey(key);
                if (total > 100) break;
            }
        }
        return total;
    }
    
    public plasmaWordIndexEntity getIndex(String wordHash, boolean deleteIfEmpty) {
        flushKey(wordHash);
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
	backend.deleteIndex(wordHash);
    }

    public int removeEntries(String wordHash, String[] urlHashes, boolean deleteComplete) {
        flushKey(wordHash);
        return backend.removeEntries(wordHash, urlHashes, deleteComplete);
    }
    
    public int addEntries(plasmaWordIndexEntryContainer container, long creationTime) {
	//serverLog.logDebug("PLASMA INDEXING", "addEntryToIndexMem: cache.size=" + cache.size() + "; hashScore.size=" + hashScore.size());
        flushToLimit();
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
            dump(waitingSeconds);
        } catch (IOException e){
            log.logError("unable to dump cache: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
