// plasmaWordIndex.java
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

// compile with
// javac -classpath classes -sourcepath source -d classes -g source/de/anomic/plasma/*.java

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Date;
import java.util.TreeSet;
import java.net.URL;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMergeIterator;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroOrder;
import de.anomic.server.logging.serverLog;

public final class plasmaWordIndex {

    private static final String indexAssortmentClusterPath = "ACLUSTER";
    private static final int assortmentCount = 64;
    
    private final File databaseRoot;
    private final plasmaWordIndexCache ramCache;
    private final plasmaWordIndexAssortmentCluster assortmentCluster;
    private int assortmentBufferSize; //kb
    private final plasmaWordIndexClassicDB backend;    
    private final kelondroOrder indexOrder = new kelondroNaturalOrder(true);
    
    public plasmaWordIndex(File databaseRoot, int bufferkb, serverLog log) {
        this.databaseRoot = databaseRoot;
        this.backend = new plasmaWordIndexClassicDB(databaseRoot, log);
        this.ramCache = new plasmaWordIndexCache(databaseRoot, log);

        // create new assortment cluster path
        File assortmentClusterPath = new File(databaseRoot, indexAssortmentClusterPath);
        if (!(assortmentClusterPath.exists())) assortmentClusterPath.mkdirs();
        this.assortmentBufferSize = bufferkb;
        this.assortmentCluster = new plasmaWordIndexAssortmentCluster(assortmentClusterPath, assortmentCount, assortmentBufferSize, log);
    }

    public File getRoot() {
        return databaseRoot;
    }

    public int maxURLinWCache() {
        return ramCache.maxURLinWCache();
    }

    public long minAgeOfWCache() {
        return ramCache.minAgeOfWCache();
    }

    public long maxAgeOfWCache() {
        return ramCache.maxAgeOfWCache();
    }

    public long minAgeOfKCache() {
        return ramCache.minAgeOfKCache();
    }

    public long maxAgeOfKCache() {
        return ramCache.maxAgeOfKCache();
    }

    public int wSize() {
        return ramCache.wSize();
    }

    public int kSize() {
        return ramCache.kSize();
    }

    public int[] assortmentsSizes() {
        return assortmentCluster.sizes();
    }

    public int[] assortmentsCacheChunkSizeAvg() {
        return assortmentCluster.cacheChunkSizeAvg();
    }

    public int[] assortmentsCacheFillStatusCml() {
        return assortmentCluster.cacheFillStatusCml();
    }
    
    public void setMaxWordCount(int maxWords) {
        ramCache.setMaxWordCount(maxWords);
    }

    public void flushControl() {
        // check for forced flush
        ramCache.shiftK2W();
        while (ramCache.maxURLinWCache() > plasmaWordIndexCache.wCacheReferenceLimit) {
            flushCache(1);
        }
        if (ramCache.wSize() > ramCache.getMaxWordCount()) {
            while (ramCache.wSize() + 500 > ramCache.getMaxWordCount()) {
                flushCache(1);
            }
        }
    }

    public boolean addEntry(String wordHash, plasmaWordIndexEntry entry, long updateTime, boolean dhtCase) {
        if (ramCache.addEntry(wordHash, entry, updateTime, dhtCase)) {
            if (!dhtCase) flushControl();
            return true;
        }
        return false;
    }
    
    public int addEntries(plasmaWordIndexEntryContainer entries, long updateTime, boolean dhtCase) {
        int added = ramCache.addEntries(entries, updateTime, dhtCase);

        // force flush
        if (!dhtCase) flushControl();
        return added;
    }

    public synchronized void flushCacheSome() {
        ramCache.shiftK2W();
        int flushCount = ramCache.wSize() / 500;
        if (flushCount > 70) flushCount = 70;
        if (flushCount < 5) flushCount = 5;
        flushCache(flushCount);
    }
    
    public synchronized void flushCache(int count) {
        for (int i = 0; i < count; i++) {
            if (ramCache.wSize() == 0) break;
            flushCache(ramCache.bestFlushWordHash());
            try {Thread.sleep(10);} catch (InterruptedException e) {}
        }
    }
    
    private synchronized void flushCache(String wordHash) {
        plasmaWordIndexEntryContainer c = ramCache.deleteContainer(wordHash);
        if (c != null) {
            plasmaWordIndexEntryContainer feedback = assortmentCluster.storeTry(wordHash, c);
            if (feedback != null) {
                backend.addEntries(feedback, System.currentTimeMillis(), true);
            }
        }
    }
    
    private static final int hour = 3600000;
    private static final int day  = 86400000;
    
    public static int microDateDays(Date modified) {
        return microDateDays(modified.getTime());
    }
    
    public static int microDateDays(long modified) {
        // this calculates a virtual age from a given date
        // the purpose is to have an age in days of a given modified date
        // from a fixed standpoint in the past
        // one day has 60*60*24 seconds = 86400 seconds
        // we take mod 64**3 = 262144, this is the mask of the storage
        return (int) ((modified / day) % 262144);
    }
        
    public static String microDateHoursStr(long time) {
        return kelondroBase64Order.enhancedCoder.encodeLong(microDateHoursInt(time), 3);
    }
    
    public static int microDateHoursInt(long time) {
        return (int) ((time / hour) % 262144);
    }
    
    public static int microDateHoursAge(String mdhs) {
        return microDateHoursInt(System.currentTimeMillis()) - (int) kelondroBase64Order.enhancedCoder.decodeLong(mdhs);
    }
    
    public static long reverseMicroDateDays(int microDateDays) {
        return ((long) microDateDays) * ((long) day);
    }
    
    public synchronized int addPageIndex(URL url, String urlHash, Date urlModified, int size, plasmaParserDocument document, plasmaCondenser condenser, String language, char doctype, int outlinksSame, int outlinksOther) {
        // this is called by the switchboard to put in a new page into the index
        // use all the words in one condenser object to simultanous create index entries
        
        // iterate over all words
        Iterator i = condenser.words();
        Map.Entry wentry;
        String word;
        plasmaWordIndexEntry ientry;
        plasmaCondenser.wordStatProp wprop;
        String wordHash;
        int urlLength = url.toString().length();
        int urlComps = htmlFilterContentScraper.urlComps(url.toString()).length;
        
        while (i.hasNext()) {
            wentry = (Map.Entry) i.next();
            word = (String) wentry.getKey();
            wprop = (plasmaCondenser.wordStatProp) wentry.getValue();
            // if ((s.length() > 4) && (c > 1)) System.out.println("# " + s + ":" + c);
            wordHash = plasmaWordIndexEntry.word2hash(word);
            ientry = new plasmaWordIndexEntry(urlHash,
                                              urlLength, urlComps, (document == null) ? urlLength : document.longTitle.length(),
                                             wprop.count,
                                             condenser.RESULT_SIMI_WORDS,
                                             condenser.RESULT_SIMI_SENTENCES,
                                             wprop.posInText,
                                             wprop.posInPhrase,
                                             wprop.numOfPhrase,
                                             0,
                                             size,
                                             urlModified.getTime(),
                                             System.currentTimeMillis(),
                                             condenser.RESULT_WORD_ENTROPHY,
                                             language,
                                             doctype,
                                             outlinksSame, outlinksOther,
                                             true);
            addEntry(wordHash, ientry, System.currentTimeMillis(), false);
        }
        // System.out.println("DEBUG: plasmaSearch.addPageIndex: added " +
        // condenser.getWords().size() + " words, flushed " + c + " entries");
        return condenser.RESULT_SIMI_WORDS;
    }

    public plasmaWordIndexEntryContainer getContainer(String wordHash, boolean deleteIfEmpty, long maxTime) {
        long start = System.currentTimeMillis();
        
        plasmaWordIndexEntryContainer container = new plasmaWordIndexEntryContainer(wordHash);
        // get from cache
        // We must not use the container from cache to store everything we find,
        // as that container remains linked to in the cache and might be changed later
        // while the returned container is still in use.
        // e.g. indexTransfer might keep this container for minutes while
        // several new pages could be added to the index, possibly with the same words that have
        // been selected for transfer
        container.add(ramCache.getContainer(wordHash, true), maxTime / 2);

        // get from assortments
        container.add(assortmentCluster.getFromAll(wordHash, (maxTime < 0) ? -1 : maxTime / 2), maxTime / 2);

        // get from backend
        if (maxTime > 0) {
            maxTime = maxTime - (System.currentTimeMillis() - start);
            if (maxTime < 0) maxTime = 100;
        }
        container.add(backend.getContainer(wordHash, deleteIfEmpty, (maxTime < 0) ? -1 : maxTime / 2), maxTime / 2);
        return container;
    }

    public Set getContainers(Set wordHashes, boolean deleteIfEmpty, boolean interruptIfEmpty, long maxTime) {
        
        // retrieve entities that belong to the hashes
        HashSet containers = new HashSet();
        String singleHash;
        plasmaWordIndexEntryContainer singleContainer;
        Iterator i = wordHashes.iterator();
        long start = System.currentTimeMillis();
        long remaining;
        while (i.hasNext()) {
            // check time
            remaining = maxTime - (System.currentTimeMillis() - start);
            //if ((maxTime > 0) && (remaining <= 0)) break;
            if ((maxTime >= 0) && (remaining <= 0)) remaining = 100;
            
            // get next hash:
            singleHash = (String) i.next();
            
            // retrieve index
            singleContainer = getContainer(singleHash, deleteIfEmpty, (maxTime < 0) ? -1 : remaining / (wordHashes.size() - containers.size()));
            
            // check result
            if (((singleContainer == null) || (singleContainer.size() == 0)) && (interruptIfEmpty)) return new HashSet();
            
            containers.add(singleContainer);
        }
        return containers;
    }

    public int size() {
        return java.lang.Math.max(assortmentCluster.sizeTotal(),
                        java.lang.Math.max(backend.size(), ramCache.size()));
    }

    public int indexSize(String wordHash) {
        int size = 0;
        try {
            plasmaWordIndexEntity entity = backend.getEntity(wordHash, true, -1);
            if (entity != null) {
                size += entity.size();
                entity.close();
            }
        } catch (IOException e) {}
        size += assortmentCluster.indexSize(wordHash);
        size += ramCache.indexSize(wordHash);
        return size;
    }

    public void close(int waitingBoundSeconds) {
        ramCache.close(waitingBoundSeconds);
        assortmentCluster.close();
        backend.close(10);
    }

    public synchronized void deleteIndex(String wordHash) {
        ramCache.deleteContainer(wordHash);
        assortmentCluster.removeFromAll(wordHash, -1);
        backend.deleteIndex(wordHash);
    }
    
    public synchronized int removeEntries(String wordHash, String[] urlHashes, boolean deleteComplete) {
        int removed = ramCache.removeEntries(wordHash, urlHashes, deleteComplete);
        if (removed == urlHashes.length) return removed;
        plasmaWordIndexEntryContainer container = assortmentCluster.removeFromAll(wordHash, -1);
        if (container != null) {
            removed += container.removeEntries(wordHash, urlHashes, deleteComplete);
            if (container.size() != 0) this.addEntries(container, System.currentTimeMillis(), false);
        }
        if (removed == urlHashes.length) return removed;
        removed += backend.removeEntries(wordHash, urlHashes, deleteComplete);
        return removed;
    }
    
    public synchronized int tryRemoveURLs(String urlHash) {
        // this tries to delete an index from the cache that has this
        // urlHash assigned. This can only work if the entry is really fresh
        // and can be found in the RAM cache
        // this returns the number of deletion that had been possible
        return ramCache.tryRemoveURLs(urlHash);
    }
    
    public static final int RL_RAMCACHE    = 0;
    public static final int RL_FILECACHE   = 1;
    public static final int RL_ASSORTMENTS = 2;
    public static final int RL_WORDFILES   = 3;
    
    public synchronized TreeSet wordHashSet(String startHash, int resourceLevel, boolean rot, int count) throws IOException {
        kelondroOrder hashOrder = (kelondroOrder) indexOrder.clone();
        hashOrder.rotate(startHash.getBytes());
        TreeSet hashes = new TreeSet(hashOrder);
        Iterator i = wordHashes(startHash, resourceLevel, rot);
        if (resourceLevel == plasmaWordIndex.RL_RAMCACHE) count = Math.min(ramCache.wSize(), count);
        String hash;
        while ((count > 0) && (i.hasNext())) {
            hash = (String) i.next();
            if ((hash != null) && (hash.length() > 0)) {
                hashes.add(hash);
                count--;
            }
        }
        return hashes;
    }
    
    public Iterator wordHashes(String startHash, int resourceLevel, boolean rot) throws IOException {
        if (rot) return new rotatingWordIterator(startHash, resourceLevel);
        else return wordHashes(startHash, resourceLevel);
    }

    private Iterator wordHashes(String startWordHash, int resourceLevel) throws IOException {
        if (resourceLevel == plasmaWordIndex.RL_RAMCACHE) {
            return ramCache.wordHashes(startWordHash, false);
        }
        /*
        if (resourceLevel == plasmaWordIndex.RL_FILECACHE) {
            
        }
        */
        if (resourceLevel == plasmaWordIndex.RL_ASSORTMENTS) {
            return new kelondroMergeIterator(
                            ramCache.wordHashes(startWordHash, false),
                            assortmentCluster.hashConjunction(startWordHash, true, false),
                            kelondroNaturalOrder.naturalOrder,
                            true);
        }
        if (resourceLevel == plasmaWordIndex.RL_WORDFILES) {
            return new kelondroMergeIterator(
                            new kelondroMergeIterator(
                                     ramCache.wordHashes(startWordHash, false),
                                     assortmentCluster.hashConjunction(startWordHash, true, false),
                                     kelondroNaturalOrder.naturalOrder,
                                     true),
                            backend.wordHashes(startWordHash, true, false),
                            kelondroNaturalOrder.naturalOrder,
                            true);
        }
        return null;
    }
    
    private class rotatingWordIterator implements Iterator {
        Iterator i;
        int resourceLevel;

        public rotatingWordIterator(String startWordHash, int resourceLevel) throws IOException {
            this.resourceLevel = resourceLevel;
            i = wordHashes(startWordHash, resourceLevel);
        }

        public void finalize() {
            i = null;
        }

        public boolean hasNext() {
            if (i.hasNext()) return true;
            else try {
                i = wordHashes("------------", resourceLevel);
                return i.hasNext();
            } catch (IOException e) {
                return false;
            }
        }

        public Object next() {
            return i.next();
        }

        public void remove() {
            throw new java.lang.UnsupportedOperationException("rotatingWordIterator does not support remove");
        }
    } // class rotatingWordIterator

    public Object migrateWords2Assortment(String wordhash) throws IOException {
        // returns the number of entries that had been added to the assortments
        // can be negative if some assortments have been moved to the backend
        File db = plasmaWordIndexEntity.wordHash2path(databaseRoot, wordhash);
        if (!(db.exists())) return "not available";
        plasmaWordIndexEntity entity = null;
        try {
            entity =  new plasmaWordIndexEntity(databaseRoot, wordhash, true);
            int size = entity.size();
            if (size > assortmentCluster.clusterCapacity) {
                // this will be too big to integrate it
                entity.close(); entity = null;
                return "too big";
            } else {
                // take out all words from the assortment to see if it fits
                // together with the extracted assortment
                plasmaWordIndexEntryContainer container = assortmentCluster.removeFromAll(wordhash, -1);
                if (size + container.size() > assortmentCluster.clusterCapacity) {
                    // this will also be too big to integrate, add to entity
                    entity.addEntries(container);
                    entity.close(); entity = null;
                    return new Integer(-container.size());
                } else {
                    // the combined container will fit, read the container
                    try {
                        Iterator entries = entity.elements(true);
                        plasmaWordIndexEntry entry;
                        while (entries.hasNext()) {
                            entry = (plasmaWordIndexEntry) entries.next();
                            // System.out.println("ENTRY = " + entry.getUrlHash());
                            container.add(new plasmaWordIndexEntry[]{entry}, System.currentTimeMillis());
                        }
                        // we have read all elements, now delete the entity
                        entity.deleteComplete();
                        entity.close(); entity = null;
                        // integrate the container into the assortments; this will work
                        assortmentCluster.storeTry(wordhash, container);
                        return new Integer(size);
                    } catch (kelondroException e) {
                        // database corrupted, we simply give up the database and delete it
                        try {entity.close();} catch (Exception ee) {} entity = null;
                        try {db.delete();} catch (Exception ee) {}
                        return "database corrupted; deleted";                        
                    }
                }
            }
        } finally {
            if (entity != null) try {entity.close();}catch(Exception e){}
        }
    }

    //  The Cleaner class was provided as "UrldbCleaner" by Hydrox
    //  see http://www.yacy-forum.de/viewtopic.php?p=18093#18093
    public Cleaner makeCleaner(plasmaCrawlLURL lurl, String startHash) {
        return new Cleaner(lurl, startHash);
    }
    
    public class Cleaner extends Thread {
        
        private String startHash;
        private boolean run = true;
        private boolean pause = false;
        public int rwiCountAtStart = 0;
        public String wordHashNow = "";
        public String lastWordHash = "";
        public int lastDeletionCounter = 0;
        private plasmaCrawlLURL lurl;
        
        public Cleaner(plasmaCrawlLURL lurl, String startHash) {
            this.lurl = lurl;
            this.startHash = startHash;
            this.rwiCountAtStart = size();
        }
        
        public void run() {
            serverLog.logInfo("INDEXCLEANER", "IndexCleaner-Thread started");
            String wordHash = "";
            plasmaWordIndexEntryContainer wordContainer = null;
            plasmaWordIndexEntry entry = null;
            URL url = null;
            HashSet urlHashs = new HashSet();
            try {
                Iterator wordHashIterator = wordHashSet(startHash, plasmaWordIndex.RL_WORDFILES, false, 100).iterator();
                while (wordHashIterator.hasNext() && run) {
                    waiter();
                    wordHash = (String) wordHashIterator.next();
                    wordContainer = getContainer(wordHash, true, -1);
                    Iterator containerIterator = wordContainer.entries();
                    wordHashNow = wordHash;
                    while (containerIterator.hasNext() && run) {
                        waiter();
                        entry = (plasmaWordIndexEntry) containerIterator.next();
                        // System.out.println("Wordhash: "+wordHash+" UrlHash:
                        // "+entry.getUrlHash());
                        try {
                            url = lurl.getEntry(entry.getUrlHash(), null).url();
                            if ((url == null) || (plasmaSwitchboard.urlBlacklist.isListed(url) == true)) {
                                urlHashs.add(entry.getUrlHash());
                            }
                        } catch (IOException e) {
                            urlHashs.add(entry.getUrlHash());
                        }
                    }
                    if (urlHashs.size() > 0) {
                        String[] urlArray;
                        urlArray = (String[]) urlHashs.toArray(new String[0]);
                        int removed = removeEntries(wordHash, urlArray, true);
                        serverLog.logFine("INDEXCLEANER", wordHash + ": " + removed + " of " + wordContainer.size() + " URL-entries deleted");
                        lastWordHash = wordHash;
                        lastDeletionCounter = urlHashs.size();
                        urlHashs.clear();
                    }
                    if (!wordHashIterator.hasNext()) {
                        // We may not be finished yet, try to get the next chunk of wordHashes
                        TreeSet wordHashes = wordHashSet(wordHash, plasmaWordIndex.RL_WORDFILES, false, 100);
                        wordHashIterator = wordHashes.iterator();
                        // Make sure we don't get the same wordhash twice, but don't skip a word
                        if ((wordHashIterator.hasNext())&&(!wordHash.equals(wordHashIterator.next()))) {
                            wordHashIterator = wordHashes.iterator();
                        }
                    }
                }
            } catch (IOException e) {
                serverLog.logSevere("INDEXCLEANER",
                        "IndexCleaner-Thread: unable to start: "
                                + e.getMessage());
            }
            serverLog.logInfo("INDEXCLEANER", "IndexCleaner-Thread stopped");
        }
        
        public void abort() {
            synchronized(this) {
                run = false;
                this.notifyAll();
            }
        }

        public void pause() {
            synchronized(this) {
                if(pause == false)  {
                    pause = true;
                    serverLog.logInfo("INDEXCLEANER", "IndexCleaner-Thread paused");                
                }
            }
        }

        public void endPause() {
            synchronized(this) {
                if (pause == true) {
                    pause = false;
                    this.notifyAll();
                    serverLog.logInfo("INDEXCLEANER", "IndexCleaner-Thread resumed");
                }
            }
        }
        
        public void waiter() {
            synchronized(this) {
                if (this.pause) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        this.run = false;
                        return;
                    }
                }
            }
        }
    }
    
    public static void main(String[] args) {
        // System.out.println(kelondroMSetTools.fastStringComparator(true).compare("RwGeoUdyDQ0Y", "rwGeoUdyDQ0Y"));
        // System.out.println(new Date(reverseMicroDateDays(microDateDays(System.currentTimeMillis()))));
        
        plasmaWordIndex index = new plasmaWordIndex(new File("D:\\dev\\proxy\\DATA\\PLASMADB"), 555, new serverLog("TESTAPP"));
        try {
            Iterator iter = index.wordHashes("5A8yhZMh_Kmv", plasmaWordIndex.RL_WORDFILES, true);
            while (iter.hasNext()) {
                System.out.println("File: " + (String) iter.next());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }

}
