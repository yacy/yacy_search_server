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
import java.io.IOException;
import java.util.Iterator;

import de.anomic.index.indexContainer;
import de.anomic.index.indexEntry;
import de.anomic.index.indexEntryAttribute;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroColumn;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroTree;
import de.anomic.server.logging.serverLog;

public final class plasmaWordIndexAssortment {
    
    // environment constants
    private static final String assortmentFileName = "indexAssortment";
    public  static final kelondroRow bufferStructureBasis = new kelondroRow(
        "byte[] wordhash-" + indexEntryAttribute.wordHashLength + ", " +
        "Cardinal occ-4 {b256}, " +
        "Cardinal time-8 {b256}, " +
        "byte[] urlhash-" + indexEntryAttribute.urlHashLength + ", " +
        "byte[] urlattr-" + indexURLEntry.encodedByteArrayFormLength(false));
    
    // class variables
    private File assortmentFile;
    private int assortmentLength;
    private serverLog log;
    private kelondroTree assortments;
    private long bufferSize;
    private long preloadTime;

    private static String intx(int x) {
        String s = Integer.toString(x);
        while (s.length() < 3) s = "0" + s;
        return s;
    }

    private static kelondroRow bufferStructure(int assortmentCapacity) {
        kelondroColumn[] structure = new kelondroColumn[3 + 2 * assortmentCapacity];
        structure[0] = bufferStructureBasis.column(0);
        structure[1] = bufferStructureBasis.column(1);
        structure[2] = bufferStructureBasis.column(2);
        for (int i = 0; i < assortmentCapacity; i++) {
            structure[3 + 2 * i] = bufferStructureBasis.column(3);
            structure[4 + 2 * i] = bufferStructureBasis.column(4);
        }
        return new kelondroRow(structure);
    }
    
    private static int assortmentCapacity(int rowsize) {
        return (rowsize - bufferStructureBasis.width(0) - bufferStructureBasis.width(1) - bufferStructureBasis.width(2)) / (bufferStructureBasis.width(3) + bufferStructureBasis.width(4));
    }

    public plasmaWordIndexAssortment(File storagePath, int assortmentLength, int bufferkb, long preloadTime, serverLog log) {
        if (!(storagePath.exists())) storagePath.mkdirs();
        this.assortmentFile = new File(storagePath, assortmentFileName + intx(assortmentLength) + ".db");
        this.assortmentLength = assortmentLength;
	    //this.bufferStructureLength = 3 + 2 * assortmentLength;
        this.bufferSize = bufferkb * 1024;
        this.preloadTime = preloadTime;
        this.log = log;
        // open assortment tree file
        long start = System.currentTimeMillis();
        assortments = kelondroTree.open(assortmentFile, bufferSize, preloadTime, kelondroTree.defaultObjectCachePercent, bufferStructure(assortmentLength));
        long stop = System.currentTimeMillis();
        if (log != null) log.logConfig("Opened Assortment, " +
                                  assortments.size() + " entries, width " +
                                  assortmentLength + ", " + bufferkb + "kb buffer, " +
                                  preloadTime + " ms preloadTime, " +
                                  (stop - start) + " ms effective, " +
                                  assortments.cacheNodeStatus()[1] + " preloaded"); 
    }

    public void store(indexContainer newContainer) {
        // stores a word index to assortment database
        // this throws an exception if the word hash already existed
        //log.logDebug("storeAssortment: wordHash=" + wordHash + ", urlHash=" + entry.getUrlHash() + ", time=" + creationTime);
        if (newContainer.size() != assortmentLength) throw new RuntimeException("plasmaWordIndexAssortment.store: wrong container size");
        kelondroRow.Entry row = assortments.row().newEntry();
        row.setCol(0, newContainer.getWordHash().getBytes());
        row.setCol(1, 1);
        row.setCol(2, newContainer.updated());
        Iterator entries = newContainer.entries();
        indexEntry entry;
        for (int i = 0; i < assortmentLength; i++) {
            entry = (indexEntry) entries.next();
            row.setCol(3 + 2 * i, entry.urlHash().getBytes());
            row.setCol(4 + 2 * i, entry.toEncodedByteArrayForm(false));
        }
        kelondroRow.Entry oldrow = null;
        try {
            oldrow = assortments.put(row);
        } catch (IOException e) {
            e.printStackTrace();
            log.logSevere("storeAssortment/IO-error: " + e.getMessage() + " - reset assortment-DB " + assortments.file(), e);
            resetDatabase();
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            log.logSevere("storeAssortment/IO-error: " + e.getMessage() + " - reset assortment-DB " + assortments.file(), e);
            resetDatabase();
        } catch (kelondroException e) {
            e.printStackTrace();
            log.logSevere("storeAssortment/kelondro-error: " + e.getMessage() + " - reset assortment-DB " + assortments.file(), e);
            resetDatabase();
        }
        if (oldrow != null) throw new RuntimeException("Store to assortment ambiguous");
    }

    public indexContainer remove(String wordHash) {
		// deletes a word index from assortment database
		// and returns the content record
		kelondroRow.Entry row = null;
		try {
			row = assortments.remove(wordHash.getBytes());
		} catch (IOException e) {
			log.logSevere("removeAssortment/IO-error: " + e.getMessage()
					+ " - reset assortment-DB " + assortments.file(), e);
			resetDatabase();
			return null;
		} catch (kelondroException e) {
			log.logSevere("removeAssortment/kelondro-error: " + e.getMessage()
					+ " - reset assortment-DB " + assortments.file(), e);
			resetDatabase();
			return null;
		}
        return row2container(row);
	}

    public boolean contains(String wordHash) {
        // gets a word index from assortment database
        // and returns the content record
        kelondroRow.Entry row = null;
        try {
            row = assortments.get(wordHash.getBytes());
            return (row != null);
        } catch (IOException e) {
            return false;
        } catch (kelondroException e) {
            log.logSevere("removeAssortment/kelondro-error: " + e.getMessage()
                    + " - reset assortment-DB " + assortments.file(), e);
            resetDatabase();
            return false;
        }
    }
    
    public indexContainer get(String wordHash) {
        // gets a word index from assortment database
        // and returns the content record
        kelondroRow.Entry row = null;
        try {
            row = assortments.get(wordHash.getBytes());
        } catch (IOException e) {
            log.logSevere("removeAssortment/IO-error: " + e.getMessage()
                    + " - reset assortment-DB " + assortments.file(), e);
            resetDatabase();
            return null;
        } catch (kelondroException e) {
            log.logSevere("removeAssortment/kelondro-error: " + e.getMessage()
                    + " - reset assortment-DB " + assortments.file(), e);
            resetDatabase();
            return null;
        }
        return row2container(row);
    }
    
    public final static indexContainer row2container(kelondroRow.Entry row) {
        if (row == null) return null;
        String wordHash = row.getColString(0, null);
        final long updateTime = row.getColLong(2);
        indexContainer container = new indexContainer(wordHash);
        int al = assortmentCapacity(row.objectsize());
        for (int i = 0; i < al; i++) {
            container.add(
                    new indexEntry[] { new indexURLEntry(
                            new String(row.getColBytes(3 + 2 * i)), new String(row.getColBytes(4 + 2 * i))) }, updateTime);
        }
        return container;
    }
    
    private void resetDatabase() {
        // deletes the assortment database and creates a new one
        if (assortments != null) try {
            assortments.close();
        } catch (IOException e) {}
        
        try {
            // make a back-up
            File backupPath = new File(assortmentFile.getParentFile(), "ABKP");
            if (!(backupPath.exists())) backupPath.mkdirs();
            File backupFile = new File(backupPath, assortmentFile.getName() + System.currentTimeMillis());
            assortmentFile.renameTo(backupFile);
            log.logInfo("a back-up of the deleted assortment file is in " + backupFile.toString());
        } catch (Exception e) {
            // if this fails, delete the file
            if (!(assortmentFile.delete())) throw new RuntimeException("cannot delete assortment database");
        }
        if (assortmentFile.exists()) assortmentFile.delete();
        assortments = kelondroTree.open(assortmentFile, bufferSize, preloadTime, kelondroTree.defaultObjectCachePercent, bufferStructure(assortmentLength));
    }
    
    public Iterator containers(String startWordHash, boolean up, boolean rot) throws IOException {
        // returns an iteration of indexContainer elements
        try {
            return new containerIterator(startWordHash, up, rot);
        } catch (kelondroException e) {
            log.logSevere("iterateAssortment/kelondro-error: " + e.getMessage() + " - reset assortment-DB " + assortments.file(), e);
            resetDatabase();
            return null;
        }
    }
    
    public class containerIterator implements Iterator {

        private Iterator rowIterator;
        
        public containerIterator(String startWordHash, boolean up, boolean rot) throws IOException {
            rowIterator = assortments.rows(up, rot, (startWordHash == null) ? null : startWordHash.getBytes());
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
        return assortments.size();
    }

    public int cacheNodeChunkSize() {
        return assortments.cacheNodeChunkSize();
    }
    
    public int cacheObjectChunkSize() {
        return assortments.cacheObjectChunkSize();
    }
    
    public int[] cacheNodeStatus() {
        return assortments.cacheNodeStatus();
    }
    
    public long[] cacheObjectStatus() {
        return assortments.cacheObjectStatus();
    }
    
    public void close() {
        try {
            assortments.close();
        } catch (IOException e){
            log.logSevere("unable to close assortment database: " + e.getMessage(), e);
        }
    }

}
