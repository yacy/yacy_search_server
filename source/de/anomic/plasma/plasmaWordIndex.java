// plasmaWordIndex.java
// (C) 2005, 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 2005 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy: $
//
// LICENSE
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

package de.anomic.plasma;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.index.indexCollectionRI;
import de.anomic.index.indexContainer;
import de.anomic.index.indexContainerOrder;
import de.anomic.index.indexRAMRI;
import de.anomic.index.indexRI;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroCloneableIterator;
import de.anomic.kelondro.kelondroMergeIterator;
import de.anomic.kelondro.kelondroOrder;
import de.anomic.kelondro.kelondroRotateIterator;
import de.anomic.net.URL;
import de.anomic.plasma.urlPattern.plasmaURLPattern;
import de.anomic.server.logging.serverLog;
import de.anomic.server.serverMemory;
import de.anomic.yacy.yacyDHTAction;

public final class plasmaWordIndex implements indexRI {

    // environment constants
    public  static final long wCacheMaxAge   = 1000 * 60 * 30; // milliseconds; 30 minutes
    public  static final int  wCacheMaxChunk = 1000;           // number of references for each urlhash
    public  static final int  lowcachedivisor = 200;
    public  static final int  maxCollectionPartition = 7; // should be 7
    
    private final kelondroOrder      indexOrder = kelondroBase64Order.enhancedCoder;
    private final indexRAMRI         dhtOutCache, dhtInCache;
    private final indexCollectionRI  collections;          // new database structure to replace AssortmentCluster and FileCluster
    public        boolean            busyCacheFlush;       // shows if a cache flush is currently performed
    private       int                flushsize;
    public  final plasmaCrawlLURL    loadedURL;
    
    public plasmaWordIndex(File indexPrimaryRoot, File indexSecondaryRoot, long preloadTime, serverLog log) {
        File textindexcache = new File(indexPrimaryRoot, "PUBLIC/TEXT/RICACHE");
        if (!(textindexcache.exists())) textindexcache.mkdirs();
        this.dhtOutCache = new indexRAMRI(textindexcache, indexRWIEntry.urlEntryRow, wCacheMaxChunk, wCacheMaxAge, "dump1.array", log);
        this.dhtInCache  = new indexRAMRI(textindexcache, indexRWIEntry.urlEntryRow, wCacheMaxChunk, wCacheMaxAge, "dump2.array", log);
        
        // create collections storage path
        File textindexcollections = new File(indexPrimaryRoot, "PUBLIC/TEXT/RICOLLECTION");
        if (!(textindexcollections.exists())) textindexcollections.mkdirs();
        this.collections = new indexCollectionRI(textindexcollections, "collection", preloadTime, maxCollectionPartition, indexRWIEntry.urlEntryRow);

        // create LURL-db
        loadedURL = new plasmaCrawlLURL(indexSecondaryRoot, preloadTime);
        
        // performance settings
        busyCacheFlush = false;
        this.flushsize = 2000;
    }

    public int minMem() {
        return 2*1024*1024 /* indexing overhead */ + dhtOutCache.minMem() + dhtInCache.minMem() + collections.minMem();
    }

    public int maxURLinDHTOutCache() {
        return dhtOutCache.maxURLinCache();
    }

    public long minAgeOfDHTOutCache() {
        return dhtOutCache.minAgeOfCache();
    }

    public long maxAgeOfDHTOutCache() {
        return dhtOutCache.maxAgeOfCache();
    }

    public int maxURLinDHTInCache() {
        return dhtInCache.maxURLinCache();
    }

    public long minAgeOfDHTInCache() {
        return dhtInCache.minAgeOfCache();
    }

    public long maxAgeOfDHTInCache() {
        return dhtInCache.maxAgeOfCache();
    }

    public int dhtOutCacheSize() {
        return dhtOutCache.size();
    }

    public int dhtInCacheSize() {
        return dhtInCache.size();
    }

    public void setMaxWordCount(int maxWords) {
        dhtOutCache.setMaxWordCount(maxWords);
    }

    public void setInMaxWordCount(int maxWords) {
        dhtInCache.setMaxWordCount(maxWords);
    }

    public void setWordFlushSize(int flushsize) {
       this.flushsize = flushsize;
    }

    public void dhtOutFlushControl() {
        // check for forced flush
        synchronized (this) {
            if ((dhtOutCache.getMaxWordCount() > wCacheMaxChunk ) ||
                (dhtOutCache.size() > dhtOutCache.getMaxWordCount()) ||
                (serverMemory.available() < collections.minMem())) {
                flushCache(dhtOutCache, dhtOutCache.size() + flushsize - dhtOutCache.getMaxWordCount());
            }
        }
    }
    public void dhtInFlushControl() {
        // check for forced flush
        synchronized (this) {
            if ((dhtInCache.getMaxWordCount() > wCacheMaxChunk ) ||
                (dhtInCache.size() > dhtInCache.getMaxWordCount())||
                (serverMemory.available() < collections.minMem())) {
                flushCache(dhtInCache, dhtInCache.size() + flushsize - dhtInCache.getMaxWordCount());
            }
        }
    }
    
    public long getUpdateTime(String wordHash) {
        indexContainer entries = getContainer(wordHash, null, -1);
        if (entries == null) return 0;
        return entries.updated();
    }
    
    public indexContainer emptyContainer(String wordHash) {
    	return new indexContainer(wordHash, indexRWIEntry.urlEntryRow);
    }

    public void addEntry(String wordHash, indexRWIEntry entry, long updateTime, boolean dhtInCase) {
        // set dhtInCase depending on wordHash
        if ((!dhtInCase) && (yacyDHTAction.shallBeOwnWord(wordHash))) dhtInCase = true;
        
        // add the entry
        if (dhtInCase) {
            dhtInCache.addEntry(wordHash, entry, updateTime, true);
            dhtInFlushControl();
        } else {
            dhtOutCache.addEntry(wordHash, entry, updateTime, false);
            dhtOutFlushControl();
        }
    }
    
    public void addEntries(indexContainer entries, long updateTime, boolean dhtInCase) {
        assert (entries.row().objectsize() == indexRWIEntry.urlEntryRow.objectsize());
        
        // set dhtInCase depending on wordHash
        if ((!dhtInCase) && (yacyDHTAction.shallBeOwnWord(entries.getWordHash()))) dhtInCase = true;
        
        // add the entry
        if (dhtInCase) {
            dhtInCache.addEntries(entries, updateTime, true);
            dhtInFlushControl();
        } else {
            dhtOutCache.addEntries(entries, updateTime, false);
            dhtOutFlushControl();
        }
    }

    public void flushCacheSome() {
        flushCache(dhtOutCache, (dhtOutCache.size() > 3 * flushsize) ? flushsize : Math.min(flushsize, Math.max(1, dhtOutCache.size() / lowcachedivisor)));
        flushCache(dhtInCache, (dhtInCache.size() > 3 * flushsize) ? flushsize : Math.min(flushsize, Math.max(1, dhtInCache.size() / lowcachedivisor)));
    }
    
    private void flushCache(indexRAMRI ram, int count) {
        if (ram.size() <= count) count = ram.size();
        if (count <= 0) return;
        if (count > 5000) count = 5000;
        busyCacheFlush = true;
        String wordHash;
        ArrayList containerList = new ArrayList();
        synchronized (this) {
            boolean collectMax = true;
            indexContainer c;
            while (collectMax) {
                wordHash = ram.maxScoreWordHash();
                c = ram.getContainer(wordHash, null, -1);
                if ((c != null) && (c.size() > wCacheMaxChunk)) {
                    containerList.add(ram.deleteContainer(wordHash));
                    if (serverMemory.available() < collections.minMem()) break; // protect memory during flush
                } else {
                    collectMax = false;
                }
            }
            count = count - containerList.size();
            for (int i = 0; i < count; i++) { // possible position of outOfMemoryError ?
                if (ram.size() == 0) break;
                if (serverMemory.available() < collections.minMem()) break; // protect memory during flush
                // select one word to flush
                wordHash = ram.bestFlushWordHash();
                
                // move one container from ram to flush list
                c = ram.deleteContainer(wordHash);
                if (c != null) containerList.add(c);
            }
        }
        // flush the containers
        collections.addMultipleEntries(containerList);
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
        
        int wordCount = 0;
        int urlLength = url.toString().length();
        int urlComps = htmlFilterContentScraper.urlComps(url.toString()).length;
        
        // iterate over all words of context text
        Iterator i = condenser.words().entrySet().iterator();
        Map.Entry wentry;
        String word;
        indexRWIEntry ientry;
        plasmaCondenser.wordStatProp wprop;
        while (i.hasNext()) {
            wentry = (Map.Entry) i.next();
            word = (String) wentry.getKey();
            wprop = (plasmaCondenser.wordStatProp) wentry.getValue();
            assert (wprop.flags != null);
            ientry = new indexRWIEntry(urlHash,
                        urlLength, urlComps, (document == null) ? urlLength : document.getTitle().length(),
                        wprop.count,
                        condenser.words().size(),
                        condenser.sentences().size(),
                        wprop.posInText,
                        wprop.posInPhrase,
                        wprop.numOfPhrase,
                        0,
                        size,
                        urlModified.getTime(),
                        System.currentTimeMillis(),
                        language,
                        doctype,
                        outlinksSame, outlinksOther,
                        wprop.flags);
            addEntry(plasmaCondenser.word2hash(word), ientry, System.currentTimeMillis(), false);
            wordCount++;
        }
        
        return wordCount;
    }

    public boolean hasContainer(String wordHash) {
        if (dhtOutCache.hasContainer(wordHash)) return true;
        if (dhtInCache.hasContainer(wordHash)) return true;
        if (collections.hasContainer(wordHash)) return true;
        return false;
    }
    
    public indexContainer getContainer(String wordHash, Set urlselection, long maxTime) {

        // get from cache
        indexContainer container = dhtOutCache.getContainer(wordHash, urlselection, -1);
        if (container == null) {
            container = dhtInCache.getContainer(wordHash, urlselection, -1);
        } else {
            container.addAllUnique(dhtInCache.getContainer(wordHash, urlselection, -1));
        }

        // get from collection index
        if (container == null) {
            container = collections.getContainer(wordHash, urlselection, (maxTime < 0) ? -1 : maxTime);
        } else {
            container.addAllUnique(collections.getContainer(wordHash, urlselection, (maxTime < 0) ? -1 : maxTime));
        }
        return container;
    }

    public Map getContainers(Set wordHashes, Set urlselection, boolean deleteIfEmpty, boolean interruptIfEmpty, long maxTime) {
        // return map of wordhash:indexContainer
        
        // retrieve entities that belong to the hashes
        HashMap containers = new HashMap();
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
            
                // get next word hash:
                singleHash = (String) i.next();
            
                // retrieve index
                singleContainer = getContainer(singleHash, urlselection, (maxTime < 0) ? -1 : remaining / (wordHashes.size() - containers.size()));
            
                // check result
                if (((singleContainer == null) || (singleContainer.size() == 0)) && (interruptIfEmpty)) return new HashMap();
            
                containers.put(singleHash, singleContainer);
            }
        return containers;
    }

    public int size() {
        return java.lang.Math.max(collections.size(), java.lang.Math.max(dhtInCache.size(), dhtOutCache.size()));
    }

    public int indexSize(String wordHash) {
        int size = 0;
        size += dhtInCache.indexSize(wordHash);
        size += dhtOutCache.indexSize(wordHash);
        size += collections.indexSize(wordHash);
        return size;
    }

    public void close() {
        synchronized (this) {
            dhtInCache.close();
            dhtOutCache.close();
            collections.close();
            loadedURL.close();
        }
    }

    public indexContainer deleteContainer(String wordHash) {
        indexContainer c = new indexContainer(wordHash, indexRWIEntry.urlEntryRow);
        c.addAllUnique(dhtInCache.deleteContainer(wordHash));
        c.addAllUnique(dhtOutCache.deleteContainer(wordHash));
        c.addAllUnique(collections.deleteContainer(wordHash));
        return c;
    }
    
    public boolean removeEntry(String wordHash, String urlHash) {
        boolean removed = false;
        removed = removed | (dhtInCache.removeEntry(wordHash, urlHash));
        removed = removed | (dhtOutCache.removeEntry(wordHash, urlHash));
        removed = removed | (collections.removeEntry(wordHash, urlHash));
        return removed;
    }
    
    public int removeEntries(String wordHash, Set urlHashes) {
        int removed = 0;
        removed += dhtInCache.removeEntries(wordHash, urlHashes);
        removed += dhtOutCache.removeEntries(wordHash, urlHashes);
        removed += collections.removeEntries(wordHash, urlHashes);
        return removed;
    }
    
    public String removeEntriesExpl(String wordHash, Set urlHashes) {
        String removed = "";
        removed += dhtInCache.removeEntries(wordHash, urlHashes) + ", ";
        removed += dhtOutCache.removeEntries(wordHash, urlHashes) + ", ";
        removed += collections.removeEntries(wordHash, urlHashes);
        return removed;
    }
    
    public int removeWordReferences(Set words, String urlhash) {
        // sequentially delete all word references
        // returns number of deletions
        Iterator iter = words.iterator();
        int count = 0;
        while (iter.hasNext()) {
            // delete the URL reference in this word index
            if (removeEntry(plasmaCondenser.word2hash((String) iter.next()), urlhash)) count++;
        }
        return count;
    }
    
    public int removeHashReferences(Set hashes, String urlhash) {
        // sequentially delete all word references
        // returns number of deletions
        Iterator iter = hashes.iterator();
        int count = 0;
        while (iter.hasNext()) {
            // delete the URL reference in this word index
            if (removeEntry((String) iter.next(), urlhash)) count++;
        }
        return count;
    }
    
    public int tryRemoveURLs(String urlHash) {
        // this tries to delete an index from the cache that has this
        // urlHash assigned. This can only work if the entry is really fresh
        // and can be found in the RAM cache
        // this returns the number of deletion that had been possible
        int d = dhtInCache.tryRemoveURLs(urlHash);
        if (d > 0) return d; else return dhtOutCache.tryRemoveURLs(urlHash);
    }
    
    public TreeSet indexContainerSet(String startHash, boolean ram, boolean rot, int count) {
        // creates a set of indexContainers
        // this does not use the dhtInCache
        kelondroOrder containerOrder = new indexContainerOrder((kelondroOrder) indexOrder.clone());
        containerOrder.rotate(startHash.getBytes());
        TreeSet containers = new TreeSet(containerOrder);
        Iterator i = wordContainers(startHash, ram, rot);
        if (ram) count = Math.min(dhtOutCache.size(), count);
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

    public kelondroCloneableIterator wordContainers(String startHash, boolean ram, boolean rot) {
        kelondroCloneableIterator i = wordContainers(startHash, ram);
        if (rot) {
            return new kelondroRotateIterator(i, new String(kelondroBase64Order.zero(startHash.length())));
        } else {
            return i;
        }
    }

    public kelondroCloneableIterator wordContainers(String startWordHash, boolean ram) {
        kelondroOrder containerOrder = new indexContainerOrder((kelondroOrder) indexOrder.clone());
        containerOrder.rotate(startWordHash.getBytes());
        if (ram) {
            return dhtOutCache.wordContainers(startWordHash, false);
        } else {
            return new kelondroMergeIterator(
                            dhtOutCache.wordContainers(startWordHash, false),
                            collections.wordContainers(startWordHash, false),
                            containerOrder,
                            indexContainer.containerMergeMethod,
                            true);
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
            indexRWIEntry entry = null;
            URL url = null;
            HashSet urlHashs = new HashSet();
            Iterator indexContainerIterator = indexContainerSet(startHash, false, false, 100).iterator();
            while (indexContainerIterator.hasNext() && run) {
                waiter();
                container = (indexContainer) indexContainerIterator.next();
                Iterator containerIterator = container.entries();
                wordHashNow = container.getWordHash();
                while (containerIterator.hasNext() && run) {
                    waiter();
                    entry = (indexRWIEntry) containerIterator.next();
                    // System.out.println("Wordhash: "+wordHash+" UrlHash:
                    // "+entry.getUrlHash());
                    indexURLEntry ue = lurl.load(entry.urlHash(), null);
                    if (ue == null) {
                        urlHashs.add(entry.urlHash());
                    } else {
                        url = ue.comp().url();
                        if ((url == null) || (plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_CRAWLER, url) == true)) {
                            urlHashs.add(entry.urlHash());
                        }
                    }
                }
                if (urlHashs.size() > 0) {
                    int removed = removeEntries(container.getWordHash(), urlHashs);
                    serverLog.logFine("INDEXCLEANER", container.getWordHash() + ": " + removed + " of " + container.size() + " URL-entries deleted");
                    lastWordHash = container.getWordHash();
                    lastDeletionCounter = urlHashs.size();
                    urlHashs.clear();
                }
                if (!containerIterator.hasNext()) {
                    // We may not be finished yet, try to get the next chunk of wordHashes
                    TreeSet containers = indexContainerSet(container.getWordHash(), false, false, 100);
                    indexContainerIterator = containers.iterator();
                    // Make sure we don't get the same wordhash twice, but don't skip a word
                    if ((indexContainerIterator.hasNext()) && (!container.getWordHash().equals(((indexContainer) indexContainerIterator.next()).getWordHash()))) {
                        indexContainerIterator = containers.iterator();
                    }
                }
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
            synchronized (this) {
                if (!pause) {
                    pause = true;
                    serverLog.logInfo("INDEXCLEANER", "IndexCleaner-Thread paused");
                }
            }
        }

        public void endPause() {
            synchronized (this) {
                if (pause) {
                    pause = false;
                    this.notifyAll();
                    serverLog.logInfo("INDEXCLEANER", "IndexCleaner-Thread resumed");
                }
            }
        }

        public void waiter() {
            synchronized (this) {
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
/*
    public static void main(String[] args) {
        // System.out.println(kelondroMSetTools.fastStringComparator(true).compare("RwGeoUdyDQ0Y", "rwGeoUdyDQ0Y"));
        // System.out.println(new Date(reverseMicroDateDays(microDateDays(System.currentTimeMillis()))));

        File indexdb = new File("D:\\dev\\proxy\\DATA\\INDEX");
        plasmaWordIndex index = new plasmaWordIndex(indexdb, true, 555, 1000, new serverLog("TESTAPP"));
        Iterator containerIter = index.wordContainers("5A8yhZMh_Kmv", plasmaWordIndex.RL_WORDFILES, true);
        while (containerIter.hasNext()) {
            System.out.println("File: " + (indexContainer) containerIter.next());
        }
    }
*/
}
