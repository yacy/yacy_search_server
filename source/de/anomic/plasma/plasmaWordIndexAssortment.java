// plasmaWordIndexAssortment.java
// ------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 18.5.2005
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
  An assortment is a set of words that appear exactly on a specific
  number of different web pages. A special case is, when the the word
  appear only on a single web page: this is called a 'singleton'.
  YaCy maintains a word cache for words appearing on x web pages.
  For each 'x' there is an assortment database, where 1<=x<=max
  If a word appears on more than 'max' web pages, the corresponing url-list
  is stored to some kind of back-end database which we consider as the
  'slowest' option to save data. This here is the fastest file-based.
 */

package de.anomic.plasma;

import java.io.File;
import java.util.Iterator;

import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntryNew;
import de.anomic.index.indexRWIEntryOld;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroColumn;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroTree;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeedDB;

public final class plasmaWordIndexAssortment {
    
    // environment constants
    private static final String assortmentFileName = "indexAssortment";
    
    // class variables
    private File assortmentFile;
    private serverLog log;
    private kelondroTree assortment;

    private static String intx(int x) {
        String s = Integer.toString(x);
        while (s.length() < 3) s = "0" + s;
        return s;
    }

    private kelondroRow bufferStructure(int assortmentCapacity) {
        kelondroColumn[] structure = new kelondroColumn[3 + assortmentCapacity];
        structure[0] = new kelondroColumn("byte[] wordhash-" + yacySeedDB.commonHashLength);
        structure[1] = new kelondroColumn("Cardinal occ-4 {b256}");
        structure[2] = new kelondroColumn("Cardinal time-8 {b256}");
        kelondroColumn p = new kelondroColumn("byte[] urlprops-" + indexRWIEntryOld.urlEntryRow.objectsize());
        for (int i = 0; i < assortmentCapacity; i++) structure[3 + i] = p;
        return new kelondroRow(structure, kelondroBase64Order.enhancedCoder, 0);
    }
    
    private int assortmentCapacity(int rowsize) {
        return (rowsize - yacySeedDB.commonHashLength - 12) / indexRWIEntryOld.urlEntryRow.objectsize();
    }
    
    public plasmaWordIndexAssortment(File storagePath, int assortmentLength, long preloadTime, serverLog log) {
        if (!(storagePath.exists())) storagePath.mkdirs();
        this.assortmentFile = new File(storagePath, assortmentFileName + intx(assortmentLength) + ".db");
	    //this.bufferStructureLength = 3 + 2 * assortmentLength;
        this.log = log;
        // open assortment tree file
        long start = System.currentTimeMillis();
        assortment = kelondroTree.open(assortmentFile, true, preloadTime, bufferStructure(assortmentLength));
        long stop = System.currentTimeMillis();
        if (log != null) log.logConfig("Opened Assortment, " +
                                  assortment.size() + " entries, width " +
                                  preloadTime + " ms preloadTime, " +
                                  (stop - start) + " ms effective"); 
        
    }
    
    public String getName() {
        return this.assortmentFile.toString();
    }
    
    public final indexContainer row2container(kelondroRow.Entry row) {
        if (row == null) return null;
        String wordHash = row.getColString(0, null);
        final long updateTime = row.getColLong(2);
        indexContainer container = new indexContainer(wordHash, indexRWIEntryNew.urlEntryRow);
        int al = assortmentCapacity(row.objectsize());
        for (int i = 0; i < al; i++) try {
            // fill AND convert old entries to new entries
            container.add(new indexRWIEntryNew(new indexRWIEntryOld(row.getColBytes(3 + i))), updateTime);
        } catch (kelondroException e) {}
        return container;
    }
    
    public Iterator wordContainers() {
        // returns an iteration of indexContainer elements
        try {
            return new containerIterator();
        } catch (kelondroException e) {
            log.logSevere("iterateAssortment/kelondro-error: " + e.getMessage(), e);
            return null;
        }
    }
    
    public class containerIterator implements Iterator {

        private Iterator rowIterator;
        
        public containerIterator() {
            rowIterator = assortment.contentRows(-1);
        }
        
        public boolean hasNext() {
            return rowIterator.hasNext();
        }

        public Object next() {
            kelondroRow.Entry entry = (kelondroRow.Entry) rowIterator.next();
            return row2container(entry);
        }

        public void remove() {
            rowIterator.remove();
        }
        
    }

    public int size() {
        return assortment.size();
    }
    
    public void close() {
        assortment.close();
    }

}
