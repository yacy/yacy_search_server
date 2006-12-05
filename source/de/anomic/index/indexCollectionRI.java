// indexCollectionRI.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 03.07.2006 on http://www.anomic.de
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

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroCollectionIndex;
import de.anomic.kelondro.kelondroOutOfLimitsException;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRowCollection;
import de.anomic.kelondro.kelondroRowSet;
import de.anomic.server.logging.serverLog;

public class indexCollectionRI implements indexRI {

    kelondroCollectionIndex collectionIndex;
    
    public indexCollectionRI(File path, String filenameStub, long buffersize, long preloadTime, kelondroRow payloadrow) {
        try {
            collectionIndex = new kelondroCollectionIndex(
                    path,
                    filenameStub,
                    12 /*keyLength*/,
                    kelondroBase64Order.enhancedCoder,
                    buffersize,
                    preloadTime,
                    4 /*loadfactor*/,
                    payloadrow);
        } catch (IOException e) {
            serverLog.logSevere("PLASMA", "unable to open collection index at " + path.toString() + ":" + e.getMessage());
        }
    }
    
    public long getUpdateTime(String wordHash) {
        indexContainer entries = getContainer(wordHash, null, false, -1);
        if (entries == null) return 0;
        return entries.updated();
    }
    
    public synchronized int size() {
        try {
            return collectionIndex.size();
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }
    
    public synchronized int indexSize(String wordHash) {
        try {
            return collectionIndex.indexSize(wordHash.getBytes());
        } catch (IOException e) {
            return 0;
        }
    }

    public synchronized Iterator wordContainers(String startWordHash, boolean rot) {
        return new wordContainersIterator(startWordHash, rot);
    }

    public class wordContainersIterator implements Iterator {

        private Iterator wci;
        
        public wordContainersIterator(String startWordHash, boolean rot) {
            wci = collectionIndex.keycollections(startWordHash.getBytes(), rot);
        }
        
        public boolean hasNext() {
            return wci.hasNext();
        }

        public Object next() {
            Object[] oo = (Object[]) wci.next();
            byte[] key = (byte[]) oo[0];
            kelondroRowSet collection = (kelondroRowSet) oo[1];
            if (collection == null) return null;
            return new indexContainer(new String(key), collection);
        }
        
        public void remove() {
            wci.remove();
        }

    }
     
    public synchronized indexContainer getContainer(String wordHash, Set urlselection, boolean deleteIfEmpty, long maxtime) {
        try {
            kelondroRowSet collection = collectionIndex.get(wordHash.getBytes(), deleteIfEmpty);
            if (collection != null) collection.select(urlselection);
            if ((collection == null) || (collection.size() == 0)) return null;
            return new indexContainer(wordHash, collection);
        } catch (IOException e) {
            return null;
        }
    }

    public synchronized indexContainer deleteContainer(String wordHash) {
        try {
            kelondroRowSet collection = collectionIndex.delete(wordHash.getBytes());
            if (collection == null) return null;
            return new indexContainer(wordHash, collection);
        } catch (IOException e) {
            return null;
        }
    }

    public synchronized boolean removeEntry(String wordHash, String urlHash, boolean deleteComplete) {
        HashSet hs = new HashSet();
        hs.add(urlHash.getBytes());
        return removeEntries(wordHash, hs, deleteComplete) == 1;
    }
    
    public synchronized int removeEntries(String wordHash, Set urlHashes, boolean deleteComplete) {
        try {
            return collectionIndex.remove(wordHash.getBytes(), urlHashes, deleteComplete);
        } catch (kelondroOutOfLimitsException e) {
            e.printStackTrace();
            return 0;
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public synchronized void addEntry(String wordHash, indexRWIEntry newEntry, long updateTime, boolean dhtCase) {
        indexContainer container = new indexContainer(wordHash, collectionIndex.payloadRow());
        container.add(newEntry);
        addEntries(container, updateTime, dhtCase);
    }
    
    public synchronized void addEntries(indexContainer newEntries, long creationTime, boolean dhtCase) {
        String wordHash = newEntries.getWordHash();
        try {
            collectionIndex.merge(wordHash.getBytes(), (kelondroRowCollection) newEntries);
        } catch (kelondroOutOfLimitsException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void close() {
        try {
            collectionIndex.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
