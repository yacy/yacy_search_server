// plasmaWordIndexAssortmentCluster.java
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 20.5.2005
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

/*
  An assortment-cluster is a set of assortments.
  Each one carries a different number of URL's
 */

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;

import de.anomic.index.indexContainer;
import de.anomic.index.indexRI;
import de.anomic.index.indexAbstractRI;
import de.anomic.index.indexTreeMapContainer;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroObjectCache;
import de.anomic.kelondro.kelondroRecords;
import de.anomic.kelondro.kelondroMergeIterator;
import de.anomic.server.logging.serverLog;

public final class plasmaWordIndexAssortmentCluster extends indexAbstractRI implements indexRI {
    
    // class variables
    private int clusterCount;   // number of cluster files
    public int clusterCapacity; // number of all url referrences that can be stored to a single word in the cluster
    
    //private serverLog log;
    private plasmaWordIndexAssortment[] assortments;
    private long completeBufferKB;

    public plasmaWordIndexAssortmentCluster(File assortmentsPath, int clusterCount, int bufferkb, long preloadTime, serverLog log) {
        // set class variables
        if (!(assortmentsPath.exists())) assortmentsPath.mkdirs();
        this.clusterCount = clusterCount;
        this.clusterCapacity = clusterCount * (clusterCount + 1) / 2;
        this.completeBufferKB = bufferkb;
        // this.log = log;
        this.assortments = new plasmaWordIndexAssortment[clusterCount];

        // open cluster and close it directly again to detect the element sizes
        int[] sizes = new int[clusterCount];
        int sumSizes = 1;
        plasmaWordIndexAssortment testAssortment;
        for (int i = 0; i < clusterCount; i++) {
            testAssortment = new plasmaWordIndexAssortment(assortmentsPath, i + 1, 0, 0, null);
            sizes[i] = testAssortment.size() + clusterCount - i;
            sumSizes += sizes[i];
            testAssortment.close();
            testAssortment = null;
        }

        // initialize cluster using the cluster elements size for optimal buffer
        // size
        for (int i = 0; i < clusterCount; i++) {
            assortments[i] = new plasmaWordIndexAssortment(
                    assortmentsPath, i + 1,
                    (int) (completeBufferKB * (long) sizes[i] / (long) sumSizes),
                    preloadTime * (long) sizes[i] / (long) sumSizes,
                    log);
        }
    }

    private indexContainer storeSingular(indexContainer newContainer) {
        // this tries to store the record. If the record does not fit, or a same hash already
        // exists and would not fit together with the new record, then the record is deleted from
        // the assortmen(s) and returned together with the newRecord.
        // if storage was successful, NULL is returned.
        if (newContainer.size() > clusterCount) return newContainer; // it will not fit
        indexContainer buffer;
        while ((buffer = assortments[newContainer.size() - 1].remove(newContainer.getWordHash())) != null) {
            if (newContainer.add(buffer, -1) == 0) return newContainer; // security check; othervise this loop does not terminate
            if (newContainer.size() > clusterCount) return newContainer; // it will not fit
        }
        // the assortment (newContainer.size() - 1) should now be empty. put it in there
        assortments[newContainer.size() - 1].store(newContainer);
        // return null to show that we have stored the new Record successfully
        return null;
    }
    
    private void storeForced(indexContainer newContainer) {
        // this stores the record and overwrites an existing record.
        // this is safe if we can be shure that the record does not exist before.
        if ((newContainer == null) || (newContainer.size() == 0) || (newContainer.size() > clusterCount)) return; // it will not fit
        assortments[newContainer.size() - 1].store(newContainer);
    }
    
    private void storeStretched(indexContainer newContainer) {
        // this stores the record and stretches the storage over
        // all the assortments that are necessary to fit in the record
        // IMPORTANT: it must be ensured that the wordHash does not exist in the cluster before
        // i.e. by calling removeFromAll
        if (newContainer.size() <= clusterCount) {
            storeForced(newContainer);
            return;
        }
        
        // calculate minimum cluster insert point
        int clusterMinStart = clusterCount;
        int cap = clusterCapacity - newContainer.size() - 2 * clusterCount;
        while (cap > 0) {
            cap -= clusterMinStart;
            clusterMinStart--;
        }
        
        // point the real cluster insert point somewhere between the minimum and the maximum
        int clusterStart = clusterCount - (int) (Math.random() * (clusterCount - clusterMinStart));

        // do the insert
        indexTreeMapContainer c;
        Iterator i = newContainer.entries();
        for (int j = clusterStart; j >= 1; j--) {
            c = new indexTreeMapContainer(newContainer.getWordHash());
            for (int k = 0; k < j; k++) {
                if (i.hasNext()) {
                    c.add((indexURLEntry) i.next(), newContainer.updated());
                } else {
                    storeForced(c);
                    return;
                }
            }
            storeForced(c);
        }
    }
    
    public indexContainer addEntries(indexContainer newContainer, long creationTime, boolean dhtCase) {
        // this is called by the index ram cache flush process
        // it returnes NULL if the storage was successful
        // it returnes a new container if the given container cannot be stored
        // containers that are returned will be stored in a WORDS file
        if (newContainer == null) return null;
        if (newContainer.size() > clusterCapacity) return newContainer; // it will not fit
        
        // split the container into several smaller containers that will take the whole thing
        // first find out how the container can be splitted
        int testsize = Math.min(clusterCount, newContainer.size());
        int [] spaces = new int[testsize];
        for (int i = testsize - 1; i >= 0; i--) spaces[i] = 0;
        int need = newContainer.size();
        int selectedAssortment = testsize - 1;
        while (selectedAssortment >= 0) {
            if (selectedAssortment + 1 <= need) {
                spaces[selectedAssortment] = (assortments[selectedAssortment].get(newContainer.getWordHash()) == null) ? (selectedAssortment + 1) : 0;
                need -= spaces[selectedAssortment];
                assert (need >= 0);
                if (need == 0) break;
            }
            selectedAssortment--;
        }
        if (need == 0) {
            // we found spaces so that we can put in the newContainer into these spaces
            indexTreeMapContainer c;
            Iterator i = newContainer.entries();
            for (int j = testsize - 1; j >= 0; j--) {
                if (spaces[j] == 0) continue;
                c = new indexTreeMapContainer(newContainer.getWordHash());
                for (int k = 0; k <= j; k++) {
                    assert (i.hasNext());
                    c.add((indexURLEntry) i.next(), newContainer.updated());
                }
                storeForced(c);
            }
            return null;
        }
        
        if (newContainer.size() <= clusterCount) newContainer = storeSingular(newContainer);
        if (newContainer == null) return null;
        
        // clean up the whole thing and try to insert the container then
        newContainer.add(deleteContainer(newContainer.getWordHash(), -1), -1);
        if (newContainer.size() > clusterCapacity) return newContainer;
        storeStretched(newContainer);
        return null;
    }
    
    public indexContainer deleteContainer(String wordHash) {
        return deleteContainer(wordHash, -1);
    }
    
    public indexContainer deleteContainer(String wordHash, long maxTime) {
        // removes all records from all the assortments and return them
        indexContainer buffer, record = new indexTreeMapContainer(wordHash);
        long limitTime = (maxTime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxTime;
        long remainingTime;
        for (int i = 0; i < clusterCount; i++) {
            buffer = assortments[i].remove(wordHash);
            remainingTime = limitTime - System.currentTimeMillis();
            if (0 > remainingTime) break;
            if (buffer != null) record.add(buffer, remainingTime);
        }
        return record;
    }

    public int removeEntries(String wordHash, String[] referenceHashes, boolean deleteComplete) {
        indexContainer c = deleteContainer(wordHash, -1);
        int b = c.size();
        c.removeEntries(wordHash, referenceHashes, false);
        if (c.size() != 0) {
            addEntries(c, c.updated(), false);
        }
        return b - c.size();
    }
    
    public indexContainer getContainer(String wordHash, boolean deleteIfEmpty, long maxTime) {
        // collect all records from all the assortments and return them
        indexContainer buffer, record = new indexTreeMapContainer(wordHash);
        long limitTime = (maxTime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxTime;
        long remainingTime;
        for (int i = 0; i < clusterCount; i++) {
            buffer = assortments[i].get(wordHash);
            remainingTime = limitTime - System.currentTimeMillis();
            if (0 > remainingTime) break;
            if (buffer != null) record.add(buffer, remainingTime);
            
        }
        return record;
    }

    public int indexSize(String wordHash) {
        int size = 0;
        for (int i = 0; i < clusterCount; i++) {
            if (assortments[i].contains(wordHash)) size += i + 1;
        }
        return size;
    }
    
    public Iterator wordHashes(String startWordHash, boolean rot) {
        try {
            return wordHashes(startWordHash, true, rot);
        } catch (IOException e) {
            return new HashSet().iterator();
        }
    }
        
    public Iterator wordHashes(String startWordHash, boolean up, boolean rot) throws IOException {
        HashSet iterators = new HashSet();
        //if (rot) System.out.println("WARNING: kelondroMergeIterator does not work correctly when individual iterators rotate on their own!");
        for (int i = 0; i < clusterCount; i++) iterators.add(assortments[i].hashes(startWordHash, up, rot));
        return kelondroMergeIterator.cascade(iterators, kelondroNaturalOrder.naturalOrder, up);
    }

    public int size() {
        int total = 0;
        for (int i = 0; i < clusterCount; i++) total += assortments[i].size();
        return total;
    }

    public int[] sizes() {
        int[] sizes = new int[clusterCount];
        for (int i = 0; i < clusterCount; i++) sizes[i] = assortments[i].size();
        return sizes;
    }
    
    public int cacheChunkSizeAvg() {
        int i = 0;
        int a;
        for (int j = 0; j < clusterCount; j++) {
            a = assortments[j].cacheNodeChunkSize();
            i    += a;
        }
        return i / clusterCount;
    }
    
    public int[] cacheNodeStatus() {
        int[][] a = new int[assortments.length][];
        for (int i = assortments.length - 1; i >= 0; i--) a[i] = assortments[i].cacheNodeStatus();
        return kelondroRecords.cacheCombinedStatus(a, assortments.length);
    }
    
    public String[] cacheObjectStatus() {
        String[][] a = new String[assortments.length][];
        for (int i = assortments.length - 1; i >= 0; i--) a[i] = assortments[i].dbCacheObjectStatus();
        return kelondroObjectCache.combinedStatus(a, a.length);
    }
    
    public void close(int waitingSeconds) {
        for (int i = 0; i < clusterCount; i++) assortments[i].close();
    }

}
