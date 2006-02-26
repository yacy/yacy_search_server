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
import java.net.URL;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMergeIterator;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.server.logging.serverLog;

public final class plasmaWordIndex {

    private static final String indexAssortmentClusterPath = "ACLUSTER";
    private static final int assortmentCount = 64;
    
    private final File databaseRoot;
    private final plasmaWordIndexCache ramCache;
    private final plasmaWordIndexAssortmentCluster assortmentCluster;
    private int assortmentBufferSize; //kb
    private final plasmaWordIndexClassicDB backend;    

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

    public int maxURLinWordCache() {
        return ramCache.maxURLinWordCache();
    }

    public long maxAgeOfWordCache() {
        return ramCache.maxAgeOfWordCache();
    }

    public int wordCacheRAMSize() {
        return ramCache.wordCacheRAMSize();
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
    
    public void setMaxWords(int maxWordsLow, int maxWordsHigh) {
        ramCache.setMaxWords(maxWordsLow, maxWordsHigh);
    }

    public void flushControl(boolean highPriority) {
        // check for forced flush
        if (highPriority) {
            if (ramCache.size() > ramCache.getMaxWordsHigh()) {
                while (ramCache.size() + 500 > ramCache.getMaxWordsHigh()) {
                    flushCache(1);
                }
            }
        } else {
            while (ramCache.maxURLinWordCache() > plasmaWordIndexCache.ramCacheReferenceLimit) {
                flushCache(1);
            }
            if (ramCache.size() > ramCache.getMaxWordsLow()) {
                while (ramCache.size() + 500 > ramCache.getMaxWordsLow()) {
                    flushCache(1);
                }
            }
        }
    }

    public boolean addEntry(String wordHash, plasmaWordIndexEntry entry, long updateTime, boolean highPriority) {
        if (ramCache.addEntry(wordHash, entry, updateTime)) {
            flushControl(highPriority);
            return true;
        }
        return false;
    }
    
    public int addEntries(plasmaWordIndexEntryContainer entries, long updateTime, boolean highPriority) {
        int added = ramCache.addEntries(entries, updateTime, highPriority);

        // force flush
        flushControl(highPriority);
        return added;
    }

    public synchronized void flushCacheSome() {
        int flushCount = ramCache.size() / 1000;
        if (flushCount > 50) flushCount = 50;
        if (flushCount < 3) flushCount = 3;
        flushCache(flushCount);
    }
    
    public synchronized void flushCache(int count) {
        for (int i = 0; i < count; i++) {
            if (ramCache.size() == 0) break;
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
            //addEntries(plasmaWordIndexEntryContainer.instantContainer(wordHash, System.currentTimeMillis(), ientry), System.currentTimeMillis(), false);
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
        container.add(ramCache.getContainer(wordHash, true));

        // get from assortments
        container.add(assortmentCluster.getFromAll(wordHash, (maxTime < 0) ? -1 : maxTime / 2));

        // get from backend
        if (maxTime > 0) {
            maxTime = maxTime - (System.currentTimeMillis() - start);
            if (maxTime < 0)
                maxTime = 100;
        }
        container.add(backend.getContainer(wordHash, deleteIfEmpty, (maxTime < 0) ? -1 : maxTime));
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

    public void intermission(long pause) {
        //this.ramCache.intermission(pause);
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
    
    public static final int RL_RAMCACHE    = 0;
    public static final int RL_FILECACHE   = 1;
    public static final int RL_ASSORTMENTS = 2;
    public static final int RL_WORDFILES   = 3;
    
    public Iterator wordHashes(String startHash, int resourceLevel, boolean rot) {
        if (rot) return new rotatingWordIterator(startHash, resourceLevel);
        else return new correctedWordIterator(startHash, resourceLevel, rot); // use correction until bug is found
    }

    private Iterator wordHashesX(String startWordHash, int resourceLevel, boolean rot) {
        if (resourceLevel == plasmaWordIndex.RL_RAMCACHE) {
            return ramCache.wordHashes(startWordHash, rot);
        }
        /*
        if (resourceLevel == plasmaWordIndex.RL_FILECACHE) {
            
        }
        */
        if (resourceLevel == plasmaWordIndex.RL_ASSORTMENTS) {
            return new kelondroMergeIterator(
                            ramCache.wordHashes(startWordHash, rot),
                            assortmentCluster.hashConjunction(startWordHash, true, rot),
                            kelondroNaturalOrder.naturalOrder,
                            true);
        }
        if (resourceLevel == plasmaWordIndex.RL_WORDFILES) {
            return new kelondroMergeIterator(
                            new kelondroMergeIterator(
                                     ramCache.wordHashes(startWordHash, rot),
                                     assortmentCluster.hashConjunction(startWordHash, true, rot),
                                     kelondroNaturalOrder.naturalOrder,
                                     true),
                            backend.wordHashes(startWordHash, true, false),
                            kelondroNaturalOrder.naturalOrder,
                            true);
        }
        return null;
    }
    
    
    private final class correctedWordIterator implements Iterator {    
        Iterator iter;
        String nextWord;

        public correctedWordIterator(String firstWord, int resourceLevel, boolean rotating) {
            iter = wordHashesX(firstWord, resourceLevel, rotating);
            try {
            nextWord = (iter.hasNext()) ? (String) iter.next() : null;
            boolean corrected = true;
            int cc = 0; // to avoid rotation loops
            while ((nextWord != null) && (corrected) && (cc < 50)) {
                int c = firstWord.compareTo(nextWord);
                corrected = false;
                if (c > 0) {
                    // firstKey > nextNode.getKey()
                    //System.out.println("CORRECTING WORD ITERATOR: firstWord=" + firstWord + ", nextWord=" + nextWord);
                    nextWord = (iter.hasNext()) ? (String) iter.next() : null;
                    corrected = true;
                    cc++;
                }
            }
            } catch (java.util.ConcurrentModificationException e) {
                nextWord = null;
            }
        }

        public void finalize() {
            iter = null;
            nextWord = null;
        }

        public boolean hasNext() {
            return nextWord != null;
        }

        public Object next() {
            String r = nextWord;
            try {
                nextWord = (iter.hasNext()) ? (String) iter.next() : null;                        
            } catch (java.util.ConcurrentModificationException e) {
                nextWord = null;
            }
            return r;
        }

        public void remove() {
            throw new java.lang.UnsupportedOperationException("correctedWordIterator  does not support remove");
        }
    } // correctedWordIterator

    private class rotatingWordIterator implements Iterator {
        Iterator i;
        int resourceLevel;

        public rotatingWordIterator(String startWordHash, int resourceLevel) {
            this.resourceLevel = resourceLevel;
            i = new correctedWordIterator(startWordHash, resourceLevel, false);
        }

        public void finalize() {
            i = null;
        }

        public boolean hasNext() {
            if (i.hasNext()) return true;
            else {
                i = new correctedWordIterator("------------", resourceLevel, false);
                return i.hasNext();
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

    public static void main(String[] args) {
        // System.out.println(kelondroMSetTools.fastStringComparator(true).compare("RwGeoUdyDQ0Y", "rwGeoUdyDQ0Y"));
        // System.out.println(new Date(reverseMicroDateDays(microDateDays(System.currentTimeMillis()))));
        
        plasmaWordIndex index = new plasmaWordIndex(new File("D:\\dev\\proxy\\DATA\\PLASMADB"), 555, new serverLog("TESTAPP"));
        Iterator iter = index.wordHashes("5A8yhZMh_Kmv", plasmaWordIndex.RL_WORDFILES, true);
        while (iter.hasNext()) {
            System.out.println("File: " + (String) iter.next());
        }
        
    }

}
