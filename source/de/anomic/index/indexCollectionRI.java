// indexCollectionRI.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 03.07.2006 on http://yacy.net
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
import java.util.List;
import java.util.Set;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroCloneableIterator;
import de.anomic.kelondro.kelondroCollectionIndex;
import de.anomic.kelondro.kelondroOutOfLimitsException;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRowSet;
import de.anomic.server.logging.serverLog;

public class indexCollectionRI implements indexRI {

    kelondroCollectionIndex collectionIndex;
    
    public indexCollectionRI(File path, String filenameStub, long preloadTime, int maxpartition, kelondroRow payloadrow) {
        try {
            collectionIndex = new kelondroCollectionIndex(
                    path,
                    filenameStub,
                    12 /*keyLength*/,
                    kelondroBase64Order.enhancedCoder,
                    preloadTime,
                    4 /*loadfactor*/,
                    maxpartition,
                    payloadrow);
        } catch (IOException e) {
            serverLog.logSevere("PLASMA", "unable to open collection index at " + path.toString() + ":" + e.getMessage());
        }
    }
    
    public long getUpdateTime(String wordHash) {
        indexContainer entries = getContainer(wordHash, null);
        if (entries == null) return 0;
        return entries.updated();
    }
    
    public int size() {
        return collectionIndex.size();
    }
    
    public int indexSize(String wordHash) {
        try {
            return collectionIndex.indexSize(wordHash.getBytes());
        } catch (IOException e) {
            return 0;
        }
    }

    public int minMem() {
        // calculate a minimum amount of memory that is necessary to use the index
        // during runtime (after the object was initialized)
        return 100 * 1024 /* overhead here */ + collectionIndex.minMem();
    }

    public synchronized kelondroCloneableIterator wordContainers(String startWordHash, boolean rot) {
        return new wordContainersIterator(startWordHash, rot);
    }

    public class wordContainersIterator implements kelondroCloneableIterator {

        private Iterator wci;
        private boolean rot;
        
        public wordContainersIterator(String startWordHash, boolean rot) {
            this.rot = rot;
            this.wci = collectionIndex.keycollections(startWordHash.getBytes(), kelondroBase64Order.zero(startWordHash.length()), rot);
        }
        
        public Object clone(Object secondWordHash) {
            return new wordContainersIterator((String) secondWordHash, rot);
        }
        
        public boolean hasNext() {
            return wci.hasNext();
        }

        public Object next() {
            Object[] oo = (Object[]) wci.next();
            if (oo == null) return null;
            byte[] key = (byte[]) oo[0];
            kelondroRowSet collection = (kelondroRowSet) oo[1];
            if (collection == null) return null;
            return new indexContainer(new String(key), collection);
        }
        
        public void remove() {
            wci.remove();
        }

    }

    public boolean hasContainer(String wordHash) {
        try {
            return collectionIndex.has(wordHash.getBytes());
        } catch (IOException e) {
            return false;
        }
    }
    
    public indexContainer getContainer(String wordHash, Set urlselection) {
        try {
            kelondroRowSet collection = collectionIndex.get(wordHash.getBytes());
            if (collection != null) collection.select(urlselection);
            if ((collection == null) || (collection.size() == 0)) return null;
            return new indexContainer(wordHash, collection);
        } catch (IOException e) {
            return null;
        }
    }

    public indexContainer deleteContainer(String wordHash) {
        try {
            kelondroRowSet collection = collectionIndex.delete(wordHash.getBytes());
            if (collection == null) return null;
            return new indexContainer(wordHash, collection);
        } catch (IOException e) {
            return null;
        }
    }

    public boolean removeEntry(String wordHash, String urlHash) {
        HashSet hs = new HashSet();
        hs.add(urlHash.getBytes());
        return removeEntries(wordHash, hs) == 1;
    }
    
    public int removeEntries(String wordHash, Set urlHashes) {
        try {
            return collectionIndex.remove(wordHash.getBytes(), urlHashes);
        } catch (kelondroOutOfLimitsException e) {
            e.printStackTrace();
            return 0;
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public void addEntries(indexContainer newEntries, long creationTime, boolean dhtCase) {
        try {
            collectionIndex.merge(newEntries);
        } catch (kelondroOutOfLimitsException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addMultipleEntries(List /*of indexContainer*/ containerList) {
        try {
        	//for (int i = 0; i < containerList.size(); i++) collectionIndex.merge((indexContainer) containerList.get(i));
            collectionIndex.mergeMultiple(containerList);
        } catch (kelondroOutOfLimitsException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        collectionIndex.close();
    }
    
}
