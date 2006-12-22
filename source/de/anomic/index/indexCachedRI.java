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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.kelondro.kelondroMergeIterator;
import de.anomic.kelondro.kelondroOrder;
import de.anomic.kelondro.kelondroRow;
import de.anomic.server.logging.serverLog;

public class indexCachedRI implements indexRI {

    private kelondroRow   payloadrow;
    private kelondroOrder indexOrder;
    private indexRAMRI    riExtern, riIntern;
    private indexRI       backend;
    public  boolean       busyCacheFlush;            // shows if a cache flush is currently performed
    private int           idleDivisor, busyDivisor;
    
    public indexCachedRI(indexRAMRI riExtern, indexRAMRI riIntern, indexRI backend, kelondroOrder payloadorder, kelondroRow payloadrow, serverLog log) {
        this.riExtern = riExtern;
        this.riIntern  = riIntern;
        this.backend = backend;
        this.indexOrder = payloadorder;
        this.payloadrow = payloadrow;
        this.busyCacheFlush = false;
        this.busyDivisor = 5000;
        this.idleDivisor = 420;
    }

    public kelondroRow payloadrow() {
        return payloadrow;
    }

    public int minMem() {
        return 1024 * 1024;
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
        indexContainer entries = getContainer(wordHash, null, -1);
        if (entries == null) return 0;
        return entries.updated();
    }
    
    public void addEntry(String wordHash, indexRWIEntry entry, long updateTime, boolean intern) {        
        // add the entry
        if (intern) {
            riIntern.addEntry(wordHash, entry, updateTime, true);
        } else {
            riExtern.addEntry(wordHash, entry, updateTime, false);
            flushControl();
        }
    }
    
    public void addEntries(indexContainer entries, long updateTime, boolean intern) {
        // add the entry
        if (intern) {
            riIntern.addEntries(entries, updateTime, true);
        } else {
            riExtern.addEntries(entries, updateTime, false);
            flushControl();
        }
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
                if (c != null) backend.addEntries(c, c.updated(), false);
                
                // pause to next loop to give other processes a chance to use IO
                //try {this.wait(8);} catch (InterruptedException e) {}
            }
        }
        busyCacheFlush = false;
    }
    
    public indexContainer getContainer(String wordHash, Set urlselection, long maxTime) {
        // get from cache
        indexContainer container = riExtern.getContainer(wordHash, urlselection, maxTime);
        if (container == null) {
            container = riIntern.getContainer(wordHash, urlselection, maxTime);
        } else {
            container.addAllUnique(riIntern.getContainer(wordHash, urlselection, maxTime));
        }

        // get from collection index
        if (container == null) {
            container = backend.getContainer(wordHash, urlselection, (maxTime < 0) ? -1 : maxTime);
        } else {
            container.addAllUnique(backend.getContainer(wordHash, urlselection, (maxTime < 0) ? -1 : maxTime));
        }
        return container;
    }

    public Map getContainers(Set wordHashes, Set urlselection, boolean interruptIfEmpty, long maxTime) {
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
        return java.lang.Math.max(backend.size(), java.lang.Math.max(riIntern.size(), riExtern.size()));
    }

    public int indexSize(String wordHash) {
        int size = backend.indexSize(wordHash);
        size += riIntern.indexSize(wordHash);
        size += riExtern.indexSize(wordHash);
        return size;
    }

    public void close() {
        synchronized (this) {
            riIntern.close();
            riExtern.close();
            backend.close();
        }
    }

    public indexContainer deleteContainer(String wordHash) {
        indexContainer c = riIntern.deleteContainer(wordHash);
        if (c == null) c = riExtern.deleteContainer(wordHash); else c.addAllUnique(riExtern.deleteContainer(wordHash));
        if (c == null) c = backend.deleteContainer(wordHash); else c.addAllUnique(backend.deleteContainer(wordHash));
        return c;
    }
    
    public boolean removeEntry(String wordHash, String urlHash) {
        boolean removed = false;
        removed = removed | (riIntern.removeEntry(wordHash, urlHash));
        removed = removed | (riExtern.removeEntry(wordHash, urlHash));
        removed = removed | (backend.removeEntry(wordHash, urlHash));
        return removed;
    }
    
    public int removeEntries(String wordHash, Set urlHashes) {
        int removed = 0;
        removed += riIntern.removeEntries(wordHash, urlHashes);
        removed += riExtern.removeEntries(wordHash, urlHashes);
        removed += backend.removeEntries(wordHash, urlHashes);
        return removed;
    }
    
    public String removeEntriesExpl(String wordHash, Set urlHashes) {
        String removed = "";
        removed += riIntern.removeEntries(wordHash, urlHashes) + ", ";
        removed += riExtern.removeEntries(wordHash, urlHashes) + ", ";
        removed += backend.removeEntries(wordHash, urlHashes) + ", ";
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
                            new indexContainerOrder(this.indexOrder),
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
