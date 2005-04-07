// plasmaWordIndexFileCache.java
// -----------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 22.01.2004
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
   The plasmaIndexCache manages a database table with a list of
   indexEntries in it. This is done in a completely different fashion
   as organized by the plasmaIndex tables. The entries are not
   sorted and just stored in a buffer.
   Whenever during a seach an index is retrieved, first it's buffer
   is flushed into the corresponding index table, so that it can be
   sorted into the remaining index entry elements.
   The cache database consist of
   - the word hash as primary key
   - one column with a one-byte counter
   - a number of more columns with indexEntry elements
*/


// compile with
// javac -classpath classes -sourcepath source -d classes -g source/de/anomic/plasma/*.java

package de.anomic.plasma;

import java.io.*;
import java.util.*;
import de.anomic.kelondro.*;

public class plasmaWordIndexFileCache {

    private static final String indexCacheFileName = "indexCache.db";
    private static final int    buffers = 50; // number of buffered entries per word

    // class variables
    private File         databaseRoot;
    private kelondroTree indexCache;

    public plasmaWordIndexFileCache(File databaseRoot, int bufferkb) throws IOException {
	this.databaseRoot = databaseRoot;
	File indexCacheFile = new File(databaseRoot, indexCacheFileName);
        if (indexCacheFile.exists()) {
	    // simply open the file
	    indexCache = new kelondroTree(indexCacheFile, bufferkb * 0x400);
	} else {
	    // create a new file
            int[] columns = new int[buffers + 2];
            columns[0] = plasmaWordIndexEntry.wordHashLength;
            columns[1] = 1;
            for (int i = 0; i < buffers; i++) columns[i + 2] = plasmaCrawlLURL.urlHashLength + plasmaWordIndexEntry.attrSpaceShort;
            indexCache = new kelondroTree(indexCacheFile, bufferkb * 0x400, columns);
	}
    }


    protected void close() throws IOException {
	indexCache.close();
	indexCache = null;
    }

    private byte[][] getCache(String wordHash) throws IOException {
        // read one line from the cache; if none exists: construct one
        byte[][] row;
        try {
            row = indexCache.get(wordHash.getBytes());
        } catch (Exception e) {
            // we had some negativeSeekOffsetExceptions here; in that case the indexCache is corrupt
            System.out.println("Error in plasmaWordINdexFileCache.getCache: index for hash " + wordHash + " is corrupt:" + e.toString());
            e.printStackTrace();
            row = null;
        }
        if (row == null) {
            row = new byte[indexCache.columns()][];
            row[0] = wordHash.getBytes();
            row[1] = new byte[1];
            row[1][0] = (byte) 0;
        }
        return row;
    }

    
    protected Iterator wordHashes(String wordHash, boolean up) throws IOException {
        return indexCache.rows(up, false, (wordHash == null) ? null : wordHash.getBytes());
    }

    protected plasmaWordIndexEntity getIndex(String wordHash, boolean deleteIfEmpty) throws IOException {
	// first flush the index cache, if there is any for that word hash
        byte[][] row = indexCache.get(wordHash.getBytes());
        if (row != null) {
            int entries = (int) row[1][0];
            if (entries != 0) flushCache(row, null); // if the cache has entries, flush it
            indexCache.remove(wordHash.getBytes()); // delete the cache index row; suppose to be empty now
        }
	// then return the index from the uncached file (with new entries)
	return new plasmaWordIndexEntity(databaseRoot, wordHash, deleteIfEmpty);
    }

    protected void addEntriesToIndex(String wordHash, Vector /* of plasmaIndexEntry */ newEntries) throws IOException {
	//System.out.println("* adding cached word index: " + wordHash + "=" + word + ":" + entry.toEncodedForm()); // debug
	// fetch the index cache
        if (newEntries.size() == 0) return;
	byte[][] row = getCache(wordHash);
	int entries = (int) row[1][0];
	// check if the index cache is full
	if (entries + 2 + newEntries.size() >= indexCache.columns()) {
	    flushCache(row, newEntries); // and put in new values
	    entries = 0;
	    row[1][0] = (byte) 0; // set number of entries to zero
	} else {
	    // put in the new values
	    String newEntry;
	    for (int i = 0; i < newEntries.size(); i++) {
		newEntry = ((plasmaWordIndexEntry) newEntries.elementAt(i)).getUrlHash() + ((plasmaWordIndexEntry) newEntries.elementAt(i)).toEncodedForm(false);
		row[entries + 2] = newEntry.getBytes();
		entries++;
	    }
	    row[1][0] = (byte) entries;
            try {
                indexCache.put(row);
            } catch (IllegalArgumentException e) {
                // this is a very bad case; a database inconsistency occurred
                deleteComplete(wordHash);
                System.out.println("fatal error in plasmaWordIndexFileCacle.addEntriesToIndex: write to word hash file " + wordHash + " failed - " + e.getMessage());
            }
        }
	// finished!
    }
    
    protected void deleteComplete(String wordHash) throws IOException {
        plasmaWordIndexEntity.removePlasmaIndex(databaseRoot, wordHash);
        indexCache.remove(wordHash.getBytes());
    }
    
    protected int removeEntries(String wordHash, String[] urlHashes, boolean deleteComplete) throws IOException {
        // removes all given url hashes from a single word index. Returns number of deletions.
        plasmaWordIndexEntity pi = getIndex(wordHash, true);
        int count = 0;
        for (int i = 0; i < urlHashes.length; i++) if (pi.removeEntry(urlHashes[i], deleteComplete)) count++;
        int size = pi.size();
        pi.close(); pi = null;
        // check if we can remove the index completely
        if ((deleteComplete) && (size == 0)) {
            // remove index
            if (!(plasmaWordIndexEntity.removePlasmaIndex(databaseRoot, wordHash))) 
                System.out.println("DEBUG: cannot remove index file for word hash " + wordHash);
            // remove cache
            indexCache.remove(wordHash.getBytes());
        }
        return count;
    }

    private synchronized void flushCache(byte[][] row, Vector indexEntries) throws IOException {
	String wordHash = new String(row[0]);
	int entries  = (int) row[1][0];
	if ((entries == 0) && ((indexEntries == null) || (indexEntries.size() == 0))) return;
    
	// open file
	plasmaWordIndexEntity pi  = new plasmaWordIndexEntity(databaseRoot, wordHash, false);

	// write from array
	plasmaWordIndexEntry entry;
	for (int i = 0; i < entries; i++) {
	    entry = new plasmaWordIndexEntry(new String(row[i + 2], 0, plasmaCrawlLURL.urlHashLength),
					 new String(row[i + 2], plasmaCrawlLURL.urlHashLength, plasmaWordIndexEntry.attrSpaceShort));
	    pi.addEntry(entry);
	}

	// write from vector
	if (indexEntries != null) {
	    for (int i = 0; i < indexEntries.size(); i++)
		pi.addEntry((plasmaWordIndexEntry) indexEntries.elementAt(i));
	}

	// close and return
	pi.close();
	pi = null;
    }

    private int size(String wordHash) throws IOException {
	// return number of entries in specific cache
	byte[][] row = indexCache.get(wordHash.getBytes());
	if (row == null) return 0;
	return (int) row[1][0];
    }

    protected int size() {
	if (indexCache == null) return 0; else return indexCache.size();
    }

    /*
    private plasmaIndex getIndexF(String wordHash) throws IOException {
	return new plasmaIndex(databaseRoot, wordHash);
    }

    private void addEntryToIndexF(String wordHash, plasmaIndexEntry entry) throws IOException {
	plasmaIndex pi = new plasmaIndex(databaseRoot, wordHash);
	pi.addEntry(entry);
	pi.close();
    }
    */

}
