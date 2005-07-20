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
import java.util.HashSet;
import java.util.Iterator;

import de.anomic.kelondro.kelondroMergeIterator;
import de.anomic.server.logging.serverLog;

public final class plasmaWordIndexAssortmentCluster {
    
    // class variables
    private File assortmentsPath;
    private int clusterCount;
    public int clusterCapacity;
    
    private serverLog log;
    private plasmaWordIndexAssortment[] assortments;
    private long completeBufferKB;

    public plasmaWordIndexAssortmentCluster(File assortmentsPath, int clusterCount, int bufferkb, serverLog log) {
	// set class variables
	if (!(assortmentsPath.exists())) assortmentsPath.mkdirs();
	this.clusterCount = clusterCount;
        this.clusterCapacity = clusterCount * (clusterCount + 1) / 2;
        this.completeBufferKB = bufferkb;
        this.log = log;
	this.assortments = new plasmaWordIndexAssortment[clusterCount];

        // open cluster and close it directly again to detect the element sizes
        int[] sizes = new int[clusterCount];
        int sumSizes = 1;
        plasmaWordIndexAssortment testAssortment;
        for (int i = 0; i < clusterCount; i++) {
	    testAssortment = new plasmaWordIndexAssortment(assortmentsPath, i + 1, 0, null);
            sizes[i] = testAssortment.size() + clusterCount - i;
            sumSizes += sizes[i];
            testAssortment.close();
            testAssortment = null;
	}
        
	// initialize cluster using the cluster elements size for optimal buffer size
	for (int i = 0; i < clusterCount; i++) {
	    assortments[i] = new plasmaWordIndexAssortment(assortmentsPath, i + 1, (int) ((long) completeBufferKB * (long) sizes[i] / (long) sumSizes), log);
	}
    }

    private plasmaWordIndexEntryContainer storeSingular(String wordHash, plasmaWordIndexEntryContainer newContainer) {
	// this tries to store the record. If the record does not fit, or a same hash already
	// exists and would not fit together with the new record, then the record is deleted from
	// the assortmen(s) and returned together with the newRecord.
	// if storage was successful, NULL is returned.
	if (newContainer.size() > clusterCount) return newContainer; // it will not fit
        plasmaWordIndexEntryContainer buffer;
        while ((buffer = assortments[newContainer.size() - 1].remove(wordHash)) != null) {
            newContainer.add(buffer);
            if (newContainer.size() > clusterCount) return newContainer; // it will not fit
        }
        // the assortment (newContainer.size() - 1) should now be empty. put it in there
        assortments[newContainer.size() - 1].store(wordHash, newContainer);
        // return null to show that we have stored the new Record successfully
        return null;
    }
    
    private void storeForced(String wordHash, plasmaWordIndexEntryContainer newContainer) {
	// this stores the record and overwrites an existing record.
        // this is safe of we can be shure that the record does not exist before.
	if ((newContainer == null) || (newContainer.size() == 0) || (newContainer.size() > clusterCount)) return; // it will not fit
        assortments[newContainer.size() - 1].store(wordHash, newContainer);
    }
    
    private void storeStretched(String wordHash, plasmaWordIndexEntryContainer newContainer) {
	// this stores the record and stretches the storage over
        // all the assortments that are necessary to fit in the record
	if (newContainer.size() <= clusterCount) {
            storeForced(wordHash, newContainer);
            return;
        }
        plasmaWordIndexEntryContainer c;
        Iterator i = newContainer.entries();
        for (int j = clusterCount; j >= 1; j--) {
            c = new plasmaWordIndexEntryContainer(wordHash);
            for (int k = 0; k < j; k++) {
                if (i.hasNext()) {
                    c.add((plasmaWordIndexEntry) i.next(), newContainer.updated());
                } else {
                    storeForced(wordHash, c);
                    return;
                }
            }
            storeForced(wordHash, c);
        }
    }
    
    public plasmaWordIndexEntryContainer storeTry(String wordHash, plasmaWordIndexEntryContainer newContainer) {
        if (newContainer.size() > clusterCapacity) return newContainer; // it will not fit
        if (newContainer.size() <= clusterCount) newContainer = storeSingular(wordHash, newContainer);
        if (newContainer == null) return null;
        newContainer.add(removeFromAll(wordHash));
        if (newContainer.size() > clusterCapacity) return newContainer;
        storeStretched(wordHash, newContainer);
        return null;
    }
    
    public plasmaWordIndexEntryContainer removeFromAll(String wordHash) {
        // collect all records from all the assortments and return them
        plasmaWordIndexEntryContainer buffer, record = new plasmaWordIndexEntryContainer(wordHash);
	for (int i = 0; i < clusterCount; i++) {
	    buffer = assortments[i].remove(wordHash);
	    if (buffer != null) record.add(buffer);
	}
        return record;
    }

    public Iterator hashConjunction(String startWordHash, boolean up) {
        HashSet iterators = new HashSet();
        for (int i = 0; i < clusterCount; i++) iterators.add(assortments[i].hashes(startWordHash, up, true));
        return kelondroMergeIterator.cascade(iterators, up);
    }

    public int sizeTotal() {
        int total = 0;
        for (int i = 0; i < clusterCount; i++) total += assortments[i].size();
        return total;
    }

    public int[] sizes() {
        int[] sizes = new int[clusterCount];
        for (int i = 0; i < clusterCount; i++) sizes[i] = assortments[i].size();
        return sizes;
    }
        
    public void close() {
        for (int i = 0; i < clusterCount; i++) assortments[i].close();
    }

}
