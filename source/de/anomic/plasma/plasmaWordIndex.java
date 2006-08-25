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
import de.anomic.net.URL;
import de.anomic.plasma.urlPattern.plasmaURLPattern;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.index.indexCollectionRI;
import de.anomic.index.indexContainer;
import de.anomic.index.indexContainerOrder;
import de.anomic.index.indexEntry;
import de.anomic.index.indexEntryAttribute;
import de.anomic.index.indexRAMCacheRI;
import de.anomic.index.indexRI;
import de.anomic.index.indexAbstractRI;
import de.anomic.index.indexRowSetContainer;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMergeIterator;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroOrder;
import de.anomic.server.logging.serverLog;

public final class plasmaWordIndex extends indexAbstractRI implements indexRI {

    private static final String indexAssortmentClusterPath = "ACLUSTER";
    private static final int assortmentCount = 64;
    
    private final File                             oldDatabaseRoot;
    private final kelondroOrder                    indexOrder = new kelondroNaturalOrder(true);
    private final indexRAMCacheRI                  ramCache;
    private final indexCollectionRI                collections;          // new database structure to replace AssortmentCluster and FileCluster
    private int                                    assortmentBufferSize; // kb
    private final plasmaWordIndexAssortmentCluster assortmentCluster;    // old database structure, to be replaced by CollectionRI
    private final plasmaWordIndexFileCluster       backend;              // old database structure, to be replaced by CollectionRI
    public        boolean                          busyCacheFlush;       // shows if a cache flush is currently performed
    public        boolean                          useCollectionIndex;   // flag for usage of new collectionIndex db
    private       int idleDivisor, busyDivisor;
    
    public plasmaWordIndex(File oldDatabaseRoot, File newIndexRoot, int bufferkb, long preloadTime, serverLog log, boolean useCollectionIndex) {
        this.oldDatabaseRoot = oldDatabaseRoot;
        this.backend = new plasmaWordIndexFileCluster(oldDatabaseRoot, log);
        this.ramCache = new indexRAMCacheRI(oldDatabaseRoot, (useCollectionIndex) ? 1024 : 64, log);

        // create assortment cluster path
        File assortmentClusterPath = new File(oldDatabaseRoot, indexAssortmentClusterPath);
        if (!(assortmentClusterPath.exists())) assortmentClusterPath.mkdirs();
        this.assortmentBufferSize = bufferkb;
        this.assortmentCluster = new plasmaWordIndexAssortmentCluster(assortmentClusterPath, assortmentCount, assortmentBufferSize, preloadTime, log);
        
        // create collections storage path
        if (!(newIndexRoot.exists())) newIndexRoot.mkdirs();
        if (useCollectionIndex)
            collections = new indexCollectionRI(newIndexRoot, "test_generation1", bufferkb * 1024, preloadTime);
        else
            collections = null;
        
        busyCacheFlush = false;
        this.useCollectionIndex = useCollectionIndex;
        this.busyDivisor = 5000;
        this.idleDivisor = 420;
    }

    public File getRoot() {
        return oldDatabaseRoot;
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

    public int assortmentsCacheChunkSizeAvg() {
        return assortmentCluster.cacheChunkSizeAvg();
    }

    public int assortmentsCacheObjectSizeAvg() {
        return assortmentCluster.cacheObjectSizeAvg();
    }

    public int[] assortmentsCacheNodeStatus() {
        return assortmentCluster.cacheNodeStatus();
    }
    
    public long[] assortmentsCacheObjectStatus() {
        return assortmentCluster.cacheObjectStatus();
    }
    
    public void setMaxWordCount(int maxWords) {
        ramCache.setMaxWordCount(maxWords);
    }

    public void setWordFlushDivisor(int idleDivisor, int busyDivisor) {
       this.idleDivisor = idleDivisor;
       this.busyDivisor = busyDivisor;
    }

    public void flushControl() {
        // check for forced flush
        synchronized (this) { ramCache.shiftK2W(); }
        flushCache(ramCache.maxURLinWCache() - ramCache.wCacheReferenceLimit);
        if (ramCache.wSize() > ramCache.getMaxWordCount()) {
            flushCache(ramCache.wSize() + 500 - ramCache.getMaxWordCount());
        }
    }

    public indexContainer addEntry(String wordHash, indexEntry entry, long updateTime, boolean dhtCase) {
        indexContainer c;
            if ((c = ramCache.addEntry(wordHash, entry, updateTime, dhtCase)) == null) {
                if (!dhtCase) flushControl();
                return null;
            }
        return c;
    }
    
    public indexContainer addEntries(indexContainer entries, long updateTime, boolean dhtCase) {
            indexContainer added = ramCache.addEntries(entries, updateTime, dhtCase);
            // force flush
            if (!dhtCase) flushControl();
            return added;
    }

    public void flushCacheSome(boolean busy) {
        synchronized (this) { ramCache.shiftK2W(); }
        int flushCount = (busy) ? ramCache.wSize() / busyDivisor : ramCache.wSize() / idleDivisor;
        if (flushCount > 100) flushCount = 100;
        if (flushCount < 1) flushCount = Math.min(1, ramCache.wSize());
        flushCache(flushCount);
    }
    
    public void flushCache(int count) {
        if (count <= 0) return;
        busyCacheFlush = true;
        String wordHash;
        //System.out.println("DEBUG-Started flush of " + count + " entries from RAM to DB");
        //long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) { // possible position of outOfMemoryError ?
            if (ramCache.wSize() == 0) break;
            synchronized (this) {
                wordHash = ramCache.bestFlushWordHash();
                
                // flush the wordHash
                indexContainer c = ramCache.deleteContainer(wordHash);
                if (c != null) {
                    if (useCollectionIndex) {
                        indexContainer feedback = collections.addEntries(c, c.updated(), false);
                        if (feedback != null) {
                            throw new RuntimeException("indexCollectionRI shall not return feedback entries; feedback = " + feedback.toString());
                        }
                    } else {
                        indexContainer feedback = assortmentCluster.addEntries(c, c.updated(), false);
                        if (feedback != null) {
                            backend.addEntries(feedback, System.currentTimeMillis(), true);
                        }
                    }
                }
                
                // pause to next loop to give other processes a chance to use IO
                try {this.wait(8);} catch (InterruptedException e) {}
            }
        }
        //System.out.println("DEBUG-Finished flush of " + count + " entries from RAM to DB in " + (System.currentTimeMillis() - start) + " milliseconds");
        busyCacheFlush = false;
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
    
    public int addPageIndex(URL url, String urlHash, Date urlModified, int size, plasmaParserDocument document, plasmaCondenser condenser, String language, char doctype, int outlinksSame, int outlinksOther) {
        // this is called by the switchboard to put in a new page into the index
        // use all the words in one condenser object to simultanous create index entries
        
        // iterate over all words
        Iterator i = condenser.words();
        Map.Entry wentry;
        String word;
        indexEntry ientry;
        plasmaCondenser.wordStatProp wprop;
        String wordHash;
        int urlLength = url.toString().length();
        int urlComps = htmlFilterContentScraper.urlComps(url.toString()).length;
        
        while (i.hasNext()) {
            wentry = (Map.Entry) i.next();
            word = (String) wentry.getKey();
            wprop = (plasmaCondenser.wordStatProp) wentry.getValue();
            // if ((s.length() > 4) && (c > 1)) System.out.println("# " + s + ":" + c);
            wordHash = indexEntryAttribute.word2hash(word);
            ientry = new indexURLEntry(urlHash,
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

    public indexContainer getContainer(String wordHash, boolean deleteIfEmpty, long maxTime) {
        long start = System.currentTimeMillis();

            // get from cache
            indexContainer container = ramCache.getContainer(wordHash, true, -1);

            // We must not use the container from cache to store everything we find,
            // as that container remains linked to in the cache and might be changed later
            // while the returned container is still in use.
            // create a clone from the container
            if (container != null) container = container.topLevelClone();
        
            // get from collection index
            if (useCollectionIndex) {
                if (container == null) {
                    container = collections.getContainer(wordHash, true, (maxTime < 0) ? -1 : maxTime);
                } else {
                    container.add(collections.getContainer(wordHash, true, (maxTime < 0) ? -1 : maxTime), -1);
                }
            }
        
            // get from assortments
            if (container == null) {
                container = assortmentCluster.getContainer(wordHash, true, (maxTime < 0) ? -1 : maxTime);
            } else {
                // add containers from assortment cluster
                container.add(assortmentCluster.getContainer(wordHash, true, (maxTime < 0) ? -1 : maxTime), -1);
            }
        
            // get from backend
            if (maxTime > 0) {
                maxTime = maxTime - (System.currentTimeMillis() - start);
                if (maxTime < 0) maxTime = 100;
            }
            container.add(backend.getContainer(wordHash, deleteIfEmpty, (maxTime < 0) ? -1 : maxTime), -1);
            return container;
    }

    public Set getContainers(Set wordHashes, boolean deleteIfEmpty, boolean interruptIfEmpty, long maxTime) {
        
        // retrieve entities that belong to the hashes
        HashSet containers = new HashSet();
        String singleHash;
        indexContainer singleContainer;
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
            if (useCollectionIndex)
                return java.lang.Math.max(collections.size(),
                    java.lang.Math.max(assortmentCluster.size(),
                     java.lang.Math.max(backend.size(), ramCache.size())));
            else
                return java.lang.Math.max(assortmentCluster.size(),
                        java.lang.Math.max(backend.size(), ramCache.size()));
    }

    public int indexSize(String wordHash) {
        int size = 0;
            try {
                plasmaWordIndexFile entity = backend.getEntity(wordHash, true, -1);
                if (entity != null) {
                    size += entity.size();
                    entity.close();
                }
            } catch (IOException e) {}
            if (useCollectionIndex) size += collections.indexSize(wordHash);
            size += assortmentCluster.indexSize(wordHash);
            size += ramCache.indexSize(wordHash);
        return size;
    }

    public void close(int waitingBoundSeconds) {
        synchronized (this) {
            ramCache.close(waitingBoundSeconds);
            if (useCollectionIndex) collections.close(-1);
            assortmentCluster.close(-1);
            backend.close(10);
        }
    }

    public indexContainer deleteContainer(String wordHash) {
            indexContainer c = ramCache.deleteContainer(wordHash);
            if (c == null) c = new indexRowSetContainer(wordHash);
            if (useCollectionIndex) c.add(collections.deleteContainer(wordHash), -1);
            c.add(assortmentCluster.deleteContainer(wordHash), -1);
            c.add(backend.deleteContainer(wordHash), -1);
            return c;
    }
    
    public boolean removeEntry(String wordHash, String urlHash, boolean deleteComplete) {
            if (ramCache.removeEntry(wordHash, urlHash, deleteComplete)) return true;
            if (useCollectionIndex) {if (collections.removeEntry(wordHash, urlHash, deleteComplete)) return true;}
            if (assortmentCluster.removeEntry(wordHash, urlHash, deleteComplete)) return true;
            return backend.removeEntry(wordHash, urlHash, deleteComplete);
    }
    
    public int removeEntries(String wordHash, Set urlHashes, boolean deleteComplete) {
        int removed = 0;
        removed += ramCache.removeEntries(wordHash, urlHashes, deleteComplete);
        if (removed == urlHashes.size()) return removed;
        if (useCollectionIndex) {
            removed += collections.removeEntries(wordHash, urlHashes, deleteComplete);
            if (removed == urlHashes.size()) return removed;
        }
        removed += assortmentCluster.removeEntries(wordHash, urlHashes, deleteComplete);
        if (removed == urlHashes.size()) return removed;
        removed += backend.removeEntries(wordHash, urlHashes, deleteComplete);
        return removed;
    }
    
    public int tryRemoveURLs(String urlHash) {
        // this tries to delete an index from the cache that has this
        // urlHash assigned. This can only work if the entry is really fresh
        // and can be found in the RAM cache
        // this returns the number of deletion that had been possible
            return ramCache.tryRemoveURLs(urlHash);
    }
    
    public static final int RL_RAMCACHE    = 0;
    public static final int RL_COLLECTIONS = 1; // the new index structure
    public static final int RL_ASSORTMENTS = 2; // (to be) outdated structure
    public static final int RL_WORDFILES   = 3; // (to be) outdated structure
    

    public TreeSet indexContainerSet(String startHash, int resourceLevel, boolean rot, int count) throws IOException {
        // creates a set of indexContainers
        kelondroOrder containerOrder = new indexContainerOrder((kelondroOrder) indexOrder.clone());
        containerOrder.rotate(startHash.getBytes());
        TreeSet containers = new TreeSet(containerOrder);
            Iterator i = wordContainers(startHash, resourceLevel, rot);
            if (resourceLevel == plasmaWordIndex.RL_RAMCACHE) count = Math.min(ramCache.wSize(), count);
            indexContainer container;
            while ((count > 0) && (i.hasNext())) {
                container = (indexContainer) i.next();
                if ((container != null) && (container.size() > 0)) {
                    containers.add(container);
                    count--;
                }
            }
        return containers;
    }
    
    public Iterator wordContainers(String startHash, boolean rot) {
        // returns an iteration of indexContainers
        try {
            return wordContainers(startHash, RL_WORDFILES, rot);
        } catch (IOException e) {
            return new HashSet().iterator();
        }
    }
    
    public Iterator wordContainers(String startHash, int resourceLevel, boolean rot) throws IOException {
        if (rot) return new rotatingContainerIterator(startHash, resourceLevel);
        else return wordContainers(startHash, resourceLevel);
    }

    private Iterator wordContainers(String startWordHash, int resourceLevel) throws IOException {
        if (resourceLevel == plasmaWordIndex.RL_RAMCACHE) {
            return ramCache.wordContainers(startWordHash, false);
        }
        if ((resourceLevel == plasmaWordIndex.RL_COLLECTIONS) && (useCollectionIndex)) {
            return new kelondroMergeIterator(
                            ramCache.wordContainers(startWordHash, false),
                            collections.wordContainers(startWordHash, false),
                            new indexContainerOrder(kelondroNaturalOrder.naturalOrder),
                            indexRowSetContainer.containerMergeMethod,
                            true);
        }
        if (resourceLevel == plasmaWordIndex.RL_ASSORTMENTS) {
            if (useCollectionIndex) {
                return new kelondroMergeIterator(
                        new kelondroMergeIterator(
                                 ramCache.wordContainers(startWordHash, false),
                                 collections.wordContainers(startWordHash, false),
                                 new indexContainerOrder(kelondroNaturalOrder.naturalOrder),
                                 indexRowSetContainer.containerMergeMethod,
                                 true),
                        assortmentCluster.wordContainers(startWordHash, true, false),
                        new indexContainerOrder(kelondroNaturalOrder.naturalOrder),
                        indexRowSetContainer.containerMergeMethod,
                        true);
            } else {
                return new kelondroMergeIterator(
                            ramCache.wordContainers(startWordHash, false),
                            assortmentCluster.wordContainers(startWordHash, true, false),
                            new indexContainerOrder(kelondroNaturalOrder.naturalOrder),
                            indexRowSetContainer.containerMergeMethod,
                            true);
            }
        }
        if (resourceLevel == plasmaWordIndex.RL_WORDFILES) {
            if (useCollectionIndex) {
                return new kelondroMergeIterator(
                        new kelondroMergeIterator(
                         new kelondroMergeIterator(
                                 ramCache.wordContainers(startWordHash, false),
                                 collections.wordContainers(startWordHash, false),
                                 new indexContainerOrder(kelondroNaturalOrder.naturalOrder),
                                 indexRowSetContainer.containerMergeMethod,
                                 true),
                         assortmentCluster.wordContainers(startWordHash, true, false),
                         new indexContainerOrder(kelondroNaturalOrder.naturalOrder),
                         indexRowSetContainer.containerMergeMethod,
                         true),
                        backend.wordContainers(startWordHash, false),
                        new indexContainerOrder(kelondroNaturalOrder.naturalOrder),
                        indexRowSetContainer.containerMergeMethod,
                        true);
            } else {
                return new kelondroMergeIterator(
                            new kelondroMergeIterator(
                                     ramCache.wordContainers(startWordHash, false),
                                     assortmentCluster.wordContainers(startWordHash, true, false),
                                     new indexContainerOrder(kelondroNaturalOrder.naturalOrder),
                                     indexRowSetContainer.containerMergeMethod,
                                     true),
                            backend.wordContainers(startWordHash, false),
                            new indexContainerOrder(kelondroNaturalOrder.naturalOrder),
                            indexRowSetContainer.containerMergeMethod,
                            true);
            }
        }
        return null;
    }
    
    private class rotatingContainerIterator implements Iterator {
        Iterator i;
        int resourceLevel;

        public rotatingContainerIterator(String startWordHash, int resourceLevel) throws IOException {
            this.resourceLevel = resourceLevel;
            i = wordContainers(startWordHash, resourceLevel);
        }

        public void finalize() {
            i = null;
        }

        public boolean hasNext() {
            if (i.hasNext()) return true;
            else try {
                i = wordContainers("------------", resourceLevel);
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
    } // class rotatingContainerIterator

    public Object migrateWords2Assortment(String wordhash) throws IOException {
        // returns the number of entries that had been added to the assortments
        // can be negative if some assortments have been moved to the backend
        File db = plasmaWordIndexFile.wordHash2path(oldDatabaseRoot, wordhash);
        if (!(db.exists())) return "not available";
        plasmaWordIndexFile entity = null;
        try {
            entity =  new plasmaWordIndexFile(oldDatabaseRoot, wordhash, true);
            int size = entity.size();
            if (size > assortmentCluster.clusterCapacity) {
                // this will be too big to integrate it
                entity.close(); entity = null;
                return "too big";
            } else {
                // take out all words from the assortment to see if it fits
                // together with the extracted assortment
                indexContainer container = assortmentCluster.deleteContainer(wordhash, -1);
                if (size + container.size() > assortmentCluster.clusterCapacity) {
                    // this will also be too big to integrate, add to entity
                    entity.addEntries(container);
                    entity.close(); entity = null;
                    return new Integer(-container.size());
                } else {
                    // the combined container will fit, read the container
                    try {
                        Iterator entries = entity.elements(true);
                        indexEntry entry;
                        while (entries.hasNext()) {
                            entry = (indexEntry) entries.next();
                            // System.out.println("ENTRY = " + entry.getUrlHash());
                            container.add(new indexEntry[]{entry}, System.currentTimeMillis());
                        }
                        // we have read all elements, now delete the entity
                        entity.deleteComplete();
                        entity.close(); entity = null;
                        // integrate the container into the assortments; this will work
                        assortmentCluster.addEntries(container, container.updated(), false);
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
            indexContainer container = null;
            indexEntry entry = null;
            URL url = null;
            HashSet urlHashs = new HashSet();
            try {
                Iterator indexContainerIterator = indexContainerSet(startHash, plasmaWordIndex.RL_WORDFILES, false, 100).iterator();
                while (indexContainerIterator.hasNext() && run) {
                    waiter();
                    container = (indexContainer) indexContainerIterator.next();
                    Iterator containerIterator = container.entries();
                    wordHashNow = container.getWordHash();
                    while (containerIterator.hasNext() && run) {
                        waiter();
                        entry = (indexEntry) containerIterator.next();
                        // System.out.println("Wordhash: "+wordHash+" UrlHash:
                        // "+entry.getUrlHash());
                        try {
                            url = lurl.getEntry(entry.urlHash(), null).url();
                            if ((url == null) || (plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_CRAWLER, url) == true)) {
                                urlHashs.add(entry.urlHash());
                            }
                        } catch (IOException e) {
                            urlHashs.add(entry.urlHash());
                        }
                    }
                    if (urlHashs.size() > 0) {
                        int removed = removeEntries(container.getWordHash(), urlHashs, true);
                        serverLog.logFine("INDEXCLEANER", container.getWordHash() + ": " + removed + " of " + container.size() + " URL-entries deleted");
                        lastWordHash = container.getWordHash();
                        lastDeletionCounter = urlHashs.size();
                        urlHashs.clear();
                    }
                    if (!containerIterator.hasNext()) {
                        // We may not be finished yet, try to get the next chunk of wordHashes
                        TreeSet containers = indexContainerSet(container.getWordHash(), plasmaWordIndex.RL_WORDFILES, false, 100);
                        indexContainerIterator = containers.iterator();
                        // Make sure we don't get the same wordhash twice, but don't skip a word
                        if ((indexContainerIterator.hasNext())&&(!container.getWordHash().equals(((indexContainer) indexContainerIterator.next()).getWordHash()))) {
                            indexContainerIterator = containers.iterator();
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
        File plasmadb = new File("D:\\dev\\proxy\\DATA\\PLASMADB");
        File indexdb = new File("D:\\dev\\proxy\\DATA\\INDEX\\PRIVATE\\TEXT");
        plasmaWordIndex index = new plasmaWordIndex(plasmadb, indexdb, 555, 1000, new serverLog("TESTAPP"), false);
        try {
            Iterator containerIter = index.wordContainers("5A8yhZMh_Kmv", plasmaWordIndex.RL_WORDFILES, true);
            while (containerIter.hasNext()) {
                System.out.println("File: " + (indexContainer) containerIter.next());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }

}
