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
import java.util.Iterator;

import de.anomic.kelondro.kelondroCollectionIndex;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroRow;

public class indexCollectionRI extends indexAbstractRI implements indexRI {

    kelondroCollectionIndex collectionIndex;
    
    public indexCollectionRI(File path, String filenameStub, long buffersize, long preloadTime) throws IOException {
        kelondroRow rowdef = new kelondroRow(new int[]{});
        
        collectionIndex = new kelondroCollectionIndex(
                path, filenameStub, 9 /*keyLength*/,
                kelondroNaturalOrder.naturalOrder, buffersize, preloadTime,
                4 /*loadfactor*/, rowdef, 8 /*partitions*/);
    }
    
    public int size() {
        try {
            return collectionIndex.size();
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public Iterator wordHashes(String startWordHash, boolean rot) {
        return new wordHashIterator(startWordHash, rot);
    }

    public class wordHashIterator implements Iterator {

        //private Iterator whi;
        
        public wordHashIterator(String startWordHash, boolean rot) {

        }
        
        public boolean hasNext() {
            // TODO Auto-generated method stub
            return false;
        }

        public Object next() {
            // TODO Auto-generated method stub
            return null;
        }
        
        public void remove() {
            // TODO Auto-generated method stub
            
        }

    }
     
    public indexContainer getContainer(String wordHash, boolean deleteIfEmpty, long maxtime) {
        try {
            indexRowSetContainer idx = (indexRowSetContainer) collectionIndex.get(wordHash.getBytes());
            idx.setWordHash(wordHash);
            return idx;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public indexContainer deleteContainer(String wordHash) {
        indexContainer idx = getContainer(wordHash, true, -1);
        try {
            collectionIndex.remove(wordHash.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return idx;
    }

    public int removeEntries(String wordHash, String[] referenceHashes, boolean deleteComplete) {
        // TODO Auto-generated method stub
        return 0;
    }

    public indexContainer addEntries(indexContainer newEntries, long creationTime, boolean dhtCase) {
        // TODO Auto-generated method stub
        return null;
    }

    public void close(int waitingSeconds) {
        // TODO Auto-generated method stub
        
    }



}
