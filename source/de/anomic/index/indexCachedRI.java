// indexCachedRI.java
// -----------------------------
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 7.11.2006 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
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

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroMergeIterator;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroOrder;
import de.anomic.kelondro.kelondroRow;
import de.anomic.server.logging.serverLog;

public class indexCachedRI implements indexRI {

    private kelondroRow   payloadrow;
    private kelondroOrder indexOrder = new kelondroNaturalOrder(true);
    private indexRAMRI    riExtern, riIntern;
    private indexRI       backend;
    public  boolean       busyCacheFlush;            // shows if a cache flush is currently performed
    private int           idleDivisor, busyDivisor;
    
    public indexCachedRI(indexRAMRI riExtern, indexRAMRI riIntern, indexRI backend, kelondroRow payloadrow, serverLog log) {
        this.riExtern = riExtern;
        this.riIntern  = riIntern;
        this.backend = backend;
        this.payloadrow = payloadrow;
        this.busyCacheFlush = false;
        this.busyDivisor = 5000;
        this.idleDivisor = 420;
    }

    public kelondroRow payloadrow() {
        return payloadrow;
    }

    public void setWordFlushDivisor(int idleDivisor, int busyDivisor) {
       this.idleDivisor = idleDivisor;
       this.busyDivisor = busyDivisor;
    }

    public void flushControl() {
        // check for forced flush
        synchronized (this) {
            if (riExtern.size() > riExtern.getMaxWordCount()) {
                flushCache(riExtern, riExtern.size() + 500 - riExtern.getMaxWordCount());
            }
            if (riIntern.size() > riIntern.getMaxWordCount()) {
                flushCache(riIntern, riIntern.size() + 500 - riIntern.getMaxWordCount());
            }
        }
    }
    
    public long getUpdateTime(String wordHash) {
        indexContainer entries = getContainer(wordHash, null, false, -1);
        if (entries == null) return 0;
        return entries.updated();
    }
    
    public indexContainer emptyContainer(String wordHash) {
        return new indexContainer(wordHash, payloadrow);
    }
    
    public indexContainer addEntry(String wordHash, indexEntry entry, long updateTime, boolean intern) {        
        // add the entry
        if (intern) {
            riIntern.addEntry(wordHash, entry, updateTime, true);
        } else {
            riExtern.addEntry(wordHash, entry, updateTime, false);
            flushControl();
        }
        return null;
    }
    
    public indexContainer addEntries(indexContainer entries, long updateTime, boolean intern) {
        // add the entry
        if (intern) {
            riIntern.addEntries(entries, updateTime, true);
        } else {
            riExtern.addEntries(entries, updateTime, false);
            flushControl();
        }
        return null;
    }

    public void flushCacheSome(boolean busy) {
        flushCacheSome(riExtern, busy);
        flushCacheSome(riIntern, busy);
    }
    
    private void flushCacheSome(indexRAMRI ram, boolean busy) {
        int flushCount = (busy) ? ram.size() / busyDivisor : ram.size() / idleDivisor;
        if (flushCount > 100) flushCount = 100;
        if (flushCount < 1) flushCount = Math.min(1, ram.size());
        flushCache(ram, flushCount);
        while (ram.maxURLinCache() > 1024) flushCache(ram, 1);
    }
    
    private void flushCache(indexRAMRI ram, int count) {
        if (count <= 0) return;
        if (count > 1000) count = 1000;
        busyCacheFlush = true;
        String wordHash;
        for (int i = 0; i < count; i++) { // possible position of outOfMemoryError ?
            if (ram.size() == 0) break;
            synchronized (this) {
                wordHash = ram.bestFlushWordHash();
                
                // flush the wordHash
                indexContainer c = ram.deleteContainer(wordHash);
                if (c != null) {
                    indexContainer feedback = backend.addEntries(c, c.updated(), false);
                    if (feedback != null) {
                        throw new RuntimeException("indexCollectionRI shall not return feedback entries; feedback = " + feedback.toString());
                    }
                }
                
                // pause to next loop to give other processes a chance to use IO
                //try {this.wait(8);} catch (InterruptedException e) {}
            }
        }
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

    public indexContainer getContainer(String wordHash, Set urlselection, boolean deleteIfEmpty, long maxTime) {
        // get from cache
        indexContainer container = riExtern.getContainer(wordHash, urlselection, true, maxTime);
        if (container == null) {
            container = riIntern.getContainer(wordHash, urlselection, true, maxTime);
        } else {
            container.add(riIntern.getContainer(wordHash, urlselection, true, maxTime), maxTime);
        }

        // get from collection index
        if (container == null) {
            container = backend.getContainer(wordHash, urlselection, true,  (maxTime < 0) ? -1 : maxTime);
        } else {
            container.add(backend.getContainer(wordHash, urlselection, true, (maxTime < 0) ? -1 : maxTime), maxTime);
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
                singleContainer = getContainer(singleHash, urlselection, deleteIfEmpty, (maxTime < 0) ? -1 : remaining / (wordHashes.size() - containers.size()));
            
                // check result
                if (((singleContainer == null) || (singleContainer.size() == 0)) && (interruptIfEmpty)) return new HashMap();
            
                containers.put(singleHash, singleContainer);
            }
        return containers;
    }

    public int size() {
        return java.lang.Math.max(backend.size(), java.lang.Math.max(riIntern.size(), riExtern.size()));
    }

    public int indexSize(String wordHash) {
        int size = backend.indexSize(wordHash);
        size += riIntern.indexSize(wordHash);
        size += riExtern.indexSize(wordHash);
        return size;
    }

    public void close(int waitingBoundSeconds) {
        synchronized (this) {
            riIntern.close(waitingBoundSeconds);
            riExtern.close(waitingBoundSeconds);
            backend.close(-1);
        }
    }

    public indexContainer deleteContainer(String wordHash) {
        indexContainer c = new indexContainer(wordHash, payloadrow);
        c.add(riIntern.deleteContainer(wordHash), -1);
        c.add(riExtern.deleteContainer(wordHash), -1);
        c.add(backend.deleteContainer(wordHash), -1);
        return c;
    }
    
    public boolean removeEntry(String wordHash, String urlHash, boolean deleteComplete) {
        boolean removed = false;
        removed = removed | (riIntern.removeEntry(wordHash, urlHash, deleteComplete));
        removed = removed | (riExtern.removeEntry(wordHash, urlHash, deleteComplete));
        removed = removed | (backend.removeEntry(wordHash, urlHash, deleteComplete));
        return removed;
    }
    
    public int removeEntries(String wordHash, Set urlHashes, boolean deleteComplete) {
        int removed = 0;
        removed += riIntern.removeEntries(wordHash, urlHashes, deleteComplete);
        removed += riExtern.removeEntries(wordHash, urlHashes, deleteComplete);
        removed += backend.removeEntries(wordHash, urlHashes, deleteComplete);
        return removed;
    }
    
    public String removeEntriesExpl(String wordHash, Set urlHashes, boolean deleteComplete) {
        String removed = "";
        removed += riIntern.removeEntries(wordHash, urlHashes, deleteComplete) + ", ";
        removed += riExtern.removeEntries(wordHash, urlHashes, deleteComplete) + ", ";
        removed += backend.removeEntries(wordHash, urlHashes, deleteComplete) + ", ";
        return removed;
    }
    
    public TreeSet indexContainerSet(String startHash, boolean ramOnly, boolean rot, int count) {
        // creates a set of indexContainers
        // this does not use the dhtInCache
        kelondroOrder containerOrder = new indexContainerOrder((kelondroOrder) indexOrder.clone());
        containerOrder.rotate(startHash.getBytes());
        TreeSet containers = new TreeSet(containerOrder);
        Iterator i = wordContainers(startHash, ramOnly, rot);
        if (ramOnly) count = Math.min(riExtern.size(), count);
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
        return wordContainers(startHash, false, rot);
    }
    
    public Iterator wordContainers(String startHash, boolean ramOnly, boolean rot) {
        if (rot) return new rotatingContainerIterator(startHash, ramOnly);
        if (ramOnly) {
            return riExtern.wordContainers(startHash, false);
        }
        return new kelondroMergeIterator(
                            riExtern.wordContainers(startHash, false),
                            backend.wordContainers(startHash, false),
                            new indexContainerOrder(kelondroNaturalOrder.naturalOrder),
                            indexContainer.containerMergeMethod,
                            true);
    }
    
    private class rotatingContainerIterator implements Iterator {
        Iterator i;
        boolean ramOnly;

        public rotatingContainerIterator(String startWordHash, boolean ramOnly) {
            this.ramOnly = ramOnly;
            i = wordContainers(startWordHash, ramOnly);
        }

        public void finalize() {
            i = null;
        }

        public boolean hasNext() {
            if (i.hasNext()) return true;
            else {
                i = wordContainers("------------", ramOnly);
                return i.hasNext();
            }
        }

        public Object next() {
            return i.next();
        }

        public void remove() {
            throw new java.lang.UnsupportedOperationException("rotatingWordIterator does not support remove");
        }
    } // class rotatingContainerIterator

}
