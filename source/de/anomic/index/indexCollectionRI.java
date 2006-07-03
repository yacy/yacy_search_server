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
        // TODO Auto-generated method stub
        return null;
    }

    public indexContainer deleteContainer(String wordHash) {
        // TODO Auto-generated method stub
        return null;
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
