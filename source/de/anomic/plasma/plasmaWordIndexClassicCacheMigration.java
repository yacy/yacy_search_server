// plasmaWordIndexFileCacheMigration.java
// --------------------------------------
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

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;

import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroTree;
import de.anomic.server.logging.serverLog;

public class plasmaWordIndexClassicCacheMigration {

    private static final String indexCacheFileName = "indexCache.db";

    // class variables
    private File         databaseRoot;
    private kelondroTree indexCache;
    private plasmaWordIndex fresh;

    public plasmaWordIndexClassicCacheMigration(File databaseRoot, plasmaWordIndex fresh) throws IOException {
        this.fresh = fresh;
        this.databaseRoot = databaseRoot;
	File indexCacheFile = new File(databaseRoot, indexCacheFileName);
        if (indexCacheFile.exists()) {
	    // simply open the file
	    indexCache = new kelondroTree(indexCacheFile, 0x400);
            if (indexCache.size() == 0) {
                indexCache.close();
                indexCacheFile.delete();
                indexCache = null;
            }
	} else {
            indexCache = null;
	}
    }
    
    protected void close() throws IOException {
	if (indexCache != null) indexCache.close();
	indexCache = null;
    }

    private byte[][] getCache(String wordHash) throws IOException {
        if (indexCache == null) return null;
        // read one line from the cache; if none exists: construct one
        byte[][] row;
        try {
            row = indexCache.get(wordHash.getBytes());
        } catch (Exception e) {
            // we had some negativeSeekOffsetExceptions here, and also loops may cause this
	    // in that case the indexCache is corrupt
            System.out.println("Error in plasmaWordINdexFileCache.getCache: index for hash " + wordHash + " is corrupt:" + e.toString());
            //e.printStackTrace();
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

    protected Iterator wordHashes() throws IOException {
        if (indexCache == null) return new HashSet().iterator();
        try {
            return indexCache.rows(true, false, null);
        } catch (kelondroException e) {
            de.anomic.server.logging.serverLog.logError("PLASMA", "kelondro error in plasmaWordIndexFileCache: " + e.getMessage());
            return new HashSet().iterator();
        }
    }

    protected void remove(String wordHash) throws IOException {
        if (indexCache == null) return;
        indexCache.remove(wordHash.getBytes());
    }

    private int size(String wordHash) throws IOException {
        if (indexCache == null) return 0;
	// return number of entries in specific cache
	byte[][] row = indexCache.get(wordHash.getBytes());
	if (row == null) return 0;
	return (int) row[1][0];
    }

    public int size() {
	if (indexCache == null) return 0; else return indexCache.size();
    }

    public boolean oneStepMigration() {
        try {
            Iterator i = wordHashes();
            if (!(i.hasNext())) return false;
            byte[][] row = (byte[][]) i.next();
            if (row == null) return false;
            String hash = new String(row[0]);
            if (hash == null) return false;
            int size = (int) row[1][0];
            plasmaWordIndexEntryContainer container = new plasmaWordIndexEntryContainer(hash);
            plasmaWordIndexEntry[] entries = new plasmaWordIndexEntry[size];
            for (int j = 0; j < size; j++) {
                entries[j] = new plasmaWordIndexEntry(
                                  new String(row[j + 2], 0, plasmaCrawlLURL.urlHashLength),
                                  new String(row[j + 2], plasmaCrawlLURL.urlHashLength, plasmaWordIndexEntry.attrSpaceShort));
            }
            container.add(entries, System.currentTimeMillis());
            fresh.addEntries(container);
            i = null;
            remove(hash);
            return true;
        } catch (Exception e) {
            serverLog.logError("PLASMA MIGRATION", "oneStepMigration error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

}
