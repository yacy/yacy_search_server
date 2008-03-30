// indexContainerHeap.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.03.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2008-03-14 01:16:04 +0100 (Fr, 14 Mrz 2008) $
// $LastChangedRevision: 4558 $
// $LastChangedBy: orbiter $
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

package de.anomic.index;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroByteOrder;
import de.anomic.kelondro.kelondroCloneableIterator;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRowSet;
import de.anomic.server.serverMemory;
import de.anomic.server.logging.serverLog;

public final class indexContainerHeap {

    // class variables
    private final File databaseRoot;
    protected final SortedMap<String, indexContainer> cache; // wordhash-container
    private final serverLog log;
    private String indexArrayFileName;
    private kelondroRow payloadrow;
    
    public indexContainerHeap(File databaseRoot, kelondroRow payloadrow, String dumpname, serverLog log) {

        // creates a new index cache
        // the cache has a back-end where indexes that do not fit in the cache are flushed
        this.databaseRoot = databaseRoot;
        this.cache = Collections.synchronizedSortedMap(new TreeMap<String, indexContainer>(new kelondroByteOrder.StringOrder(payloadrow.getOrdering())));
        this.log = log;
        this.indexArrayFileName = dumpname;
        this.payloadrow = payloadrow;
        
        // read in dump of last session
        try {
            restore();
        } catch (IOException e){
            log.logSevere("unable to restore cache dump: " + e.getMessage(), e);
        }
    }
    
    private void dump() throws IOException {
        log.logConfig("creating dump for index cache '" + indexArrayFileName + "', " + cache.size() + " words (and much more urls)");
        File indexDumpFile = new File(databaseRoot, indexArrayFileName);
        if (indexDumpFile.exists()) indexDumpFile.delete();
        OutputStream os = new BufferedOutputStream(new FileOutputStream(indexDumpFile));
        long startTime = System.currentTimeMillis();
        long messageTime = System.currentTimeMillis() + 5000;
        long wordsPerSecond = 0, wordcount = 0, urlcount = 0;
        Map.Entry<String, indexContainer> entry;
        String wordHash;
        indexContainer container;

        // write wCache
        synchronized (cache) {
            Iterator<Map.Entry<String, indexContainer>> i = cache.entrySet().iterator();
            while (i.hasNext()) {
                // get entries
                entry = i.next();
                wordHash = entry.getKey();
                container = entry.getValue();
                
                // put entries on stack
                if (container != null) {
                    os.write(wordHash.getBytes());
                    os.write(container.exportCollection());
                }
                wordcount++;
                i.remove(); // free some mem

                // write a log
                if (System.currentTimeMillis() > messageTime) {
                    serverMemory.gc(1000, "indexRAMRI, for better statistic-1"); // for better statistic - thq
                    wordsPerSecond = wordcount * 1000
                            / (1 + System.currentTimeMillis() - startTime);
                    log.logInfo("dump status: " + wordcount
                            + " words done, "
                            + (cache.size() / (wordsPerSecond + 1))
                            + " seconds remaining, free mem = "
                            + (serverMemory.free() / 1024 / 1024)
                            + "MB");
                    messageTime = System.currentTimeMillis() + 5000;
                }
            }
        }
        os.flush();
        os.close();
        log.logInfo("finished dump of ram cache: " + urlcount + " word/URL relations in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
    }

    private void restore() throws IOException {
        File indexDumpFile = new File(databaseRoot, indexArrayFileName);
        if (!(indexDumpFile.exists())) return;

        InputStream is = new BufferedInputStream(new FileInputStream(indexDumpFile));
        
        long messageTime = System.currentTimeMillis() + 5000;
        long wordCount = 0;
        synchronized (cache) {
            String wordHash;
            byte[] word = new byte[12];
            while (is.available() > 0) {
                // read word
                is.read(word);
                wordHash = new String(word);
                // read collection
                
                indexContainer container = new indexContainer(wordHash, kelondroRowSet.importRowSet(is, payloadrow));
                cache.put(wordHash, container);
                wordCount++;
                // protect against memory shortage
                //while (serverMemory.free() < 1000000) {flushFromMem(); java.lang.System.gc();}
                // write a log
                if (System.currentTimeMillis() > messageTime) {
                    serverMemory.gc(1000, "indexRAMRI, for better statistic-2"); // for better statistic - thq
                    log.logInfo("restoring status: " + wordCount + " words done, free mem = " + (serverMemory.free() / 1024 / 1024) + "MB");
                    messageTime = System.currentTimeMillis() + 5000;
                }
            }
        }
        is.close();
    }

    public int size() {
        return cache.size();
    }

    public synchronized int indexSize(String wordHash) {
        indexContainer cacheIndex = (indexContainer) cache.get(wordHash);
        if (cacheIndex == null) return 0;
        return cacheIndex.size();
    }

    public synchronized kelondroCloneableIterator<indexContainer> wordContainers(String startWordHash, boolean rot) {
        // we return an iterator object that creates top-level-clones of the indexContainers
        // in the cache, so that manipulations of the iterated objects do not change
        // objects in the cache.
        return new wordContainerIterator(startWordHash, rot);
    }

    public class wordContainerIterator implements kelondroCloneableIterator<indexContainer> {

        // this class exists, because the wCache cannot be iterated with rotation
        // and because every indexContainer Object that is iterated must be returned as top-level-clone
        // so this class simulates wCache.tailMap(startWordHash).values().iterator()
        // plus the mentioned features
        
        private boolean rot;
        private Iterator<indexContainer> iterator;
        
        public wordContainerIterator(String startWordHash, boolean rot) {
            this.rot = rot;
            this.iterator = (startWordHash == null) ? cache.values().iterator() : cache.tailMap(startWordHash).values().iterator();
            // The collection's iterator will return the values in the order that their corresponding keys appear in the tree.
        }
        
        public wordContainerIterator clone(Object secondWordHash) {
            return new wordContainerIterator((String) secondWordHash, rot);
        }
        
        public boolean hasNext() {
            if (rot) return true;
            return iterator.hasNext();
        }

        public indexContainer next() {
            if (iterator.hasNext()) {
                return ((indexContainer) iterator.next()).topLevelClone();
            } else {
                // rotation iteration
                if (rot) {
                    iterator = cache.values().iterator();
                    return ((indexContainer) iterator.next()).topLevelClone();
                } else {
                    return null;
                }
            }
        }

        public void remove() {
            iterator.remove();
        }
        
    }
    
    public boolean hasContainer(String wordHash) {
        return cache.containsKey(wordHash);
    }
    
    public int sizeContainer(String wordHash) {
        indexContainer c = (indexContainer) cache.get(wordHash);
        return (c == null) ? 0 : c.size();
    }

    public synchronized indexContainer getContainer(String wordHash, Set<String> urlselection) {
        if (wordHash == null) return null;
        
        // retrieve container
        indexContainer container = (indexContainer) cache.get(wordHash);
        
        // We must not use the container from cache to store everything we find,
        // as that container remains linked to in the cache and might be changed later
        // while the returned container is still in use.
        // create a clone from the container
        if (container != null) container = container.topLevelClone();
        
        // select the urlselection
        if ((urlselection != null) && (container != null)) container.select(urlselection);

        return container;
    }

    public synchronized indexContainer deleteContainer(String wordHash) {
        // returns the index that had been deleted
        indexContainer container = (indexContainer) cache.remove(wordHash);
        return container;
    }

    public synchronized boolean removeEntry(String wordHash, String urlHash) {
        indexContainer c = (indexContainer) cache.get(wordHash);
        if ((c != null) && (c.remove(urlHash) != null)) {
            // removal successful
            if (c.size() == 0) {
                deleteContainer(wordHash);
            } else {
                cache.put(wordHash, c);
            }
            return true;
        }
        return false;
    }
    
    public synchronized int removeEntries(String wordHash, Set<String> urlHashes) {
        if (urlHashes.size() == 0) return 0;
        indexContainer c = (indexContainer) cache.get(wordHash);
        int count;
        if ((c != null) && ((count = c.removeEntries(urlHashes)) > 0)) {
            // removal successful
            if (c.size() == 0) {
                deleteContainer(wordHash);
            } else {
                cache.put(wordHash, c);
            }
            return count;
        }
        return 0;
    }
 
    public synchronized int tryRemoveURLs(String urlHash) {
        // this tries to delete an index from the cache that has this
        // urlHash assigned. This can only work if the entry is really fresh
        // Such entries must be searched in the latest entries
        int delCount = 0;
            Iterator<Map.Entry<String, indexContainer>> i = cache.entrySet().iterator();
            Map.Entry<String, indexContainer> entry;
            String wordhash;
            indexContainer c;
            while (i.hasNext()) {
                entry = i.next();
                wordhash = entry.getKey();
            
                // get container
                c = entry.getValue();
                if (c.remove(urlHash) != null) {
                    if (c.size() == 0) {
                        i.remove();
                    } else {
                        cache.put(wordhash, c); // superfluous?
                    }
                    delCount++;
                }
            }
        return delCount;
    }
    
    public synchronized void addEntries(indexContainer container) {
        // this puts the entries into the cache, not into the assortment directly
        int added = 0;
        if ((container == null) || (container.size() == 0)) return;

        // put new words into cache
        String wordHash = container.getWordHash();
        indexContainer entries = (indexContainer) cache.get(wordHash); // null pointer exception? wordhash != null! must be cache==null
        if (entries == null) {
            entries = container.topLevelClone();
            added = entries.size();
        } else {
            added = entries.putAllRecent(container);
        }
        if (added > 0) {
            cache.put(wordHash, entries);
        }
        entries = null;
    }

    public synchronized void addEntry(String wordHash, indexRWIRowEntry newEntry, long updateTime, boolean dhtCase) {
        indexContainer container = (indexContainer) cache.get(wordHash);
        if (container == null) container = new indexContainer(wordHash, this.payloadrow, 1);
        container.put(newEntry);
        cache.put(wordHash, container);
    }

    public synchronized void close() {
        // dump cache
        try {
            dump();
        } catch (IOException e){
            log.logSevere("unable to dump cache: " + e.getMessage(), e);
        }
    }
}
