// plasmaWordIndexFile.java
// --------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexRWIEntryNew;
import de.anomic.index.indexRWIEntryOld;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroTree;
import de.anomic.yacy.yacySeedDB;

public final class plasmaWordIndexFile {

    private final String theWordHash;
    private kelondroTree theIndex;
    private File         theLocation;

    public plasmaWordIndexFile(File databaseRoot, String wordHash) {
        theWordHash = wordHash;
        theIndex    = indexFile(databaseRoot, wordHash);
    }

    public static boolean removePlasmaIndex(File databaseRoot, String wordHash) {
        File f = wordHash2path(databaseRoot, wordHash);
        boolean success = true;
        if (f.exists()) success = f.delete();
        // clean up directory structure
        f = f.getParentFile();
        while ((f.isDirectory()) && (f.list().length == 0)) {
            if (!(f.delete())) break;
            f = f.getParentFile();
        }
        return success;
    }

    private kelondroTree indexFile(File databaseRoot, String wordHash) {
        if (wordHash.length() < 12) throw new RuntimeException("word hash wrong: '" + wordHash + "'");
        theLocation = wordHash2path(databaseRoot, wordHash);
        File fp = theLocation.getParentFile();
        if (fp != null) fp.mkdirs();
        return kelondroTree.open(theLocation, true, 0, 
                    new kelondroRow("byte[] urlhash-" + yacySeedDB.commonHashLength + ", byte[] ba-" + (indexRWIEntryOld.urlEntryRow.objectsize() - yacySeedDB.commonHashLength),
                            kelondroBase64Order.enhancedCoder, 0));
    }

    public static File wordHash2path(File databaseRoot, String hash) {
    // creates a path that constructs hashing on a file system

        return new File (databaseRoot, "WORDS/" +
             hash.substring(0,1) + "/" + hash.substring(1,2) + "/" + hash.substring(2,4) + "/" +
             hash.substring(4,6) + "/" + hash + ".db");
    }

    public String wordHash() {
        return theWordHash;
    }
    
    public int size() {
        if (theIndex == null) return 0;
        int size = theIndex.size();
        if (size == 0) {
            deleteComplete();
            return 0;
        } else {
            return size;
        }
    }

    public void close() throws IOException {
        if (theIndex != null) theIndex.close();
        theIndex = null;
    }

    public void finalize() {
    try {
        close();
    } catch (IOException e) {}
    }

    public indexRWIEntry getEntry(String urlhash) throws IOException {
        kelondroRow.Entry n = theIndex.get(urlhash.getBytes());
        if (n == null) return null;
        try {
            return new indexRWIEntryNew(new indexRWIEntryOld(n.getColString(0, null), n.getColString(1, null)));
        } catch (kelondroException e) {return null;}
    }
    
    public boolean contains(String urlhash) throws IOException {
        return (theIndex.get(urlhash.getBytes()) != null);
    }
    
    public boolean contains(indexRWIEntry entry) throws IOException {
        return (theIndex.get(entry.urlHash().getBytes()) != null);
    }
    
    public void addEntries(indexContainer container) {
        throw new UnsupportedOperationException("word files are not supported in YaCy 0.491 and above");
    }
    
    public boolean deleteComplete() {
        if (theIndex == null) return false;
        theIndex.close();
        // remove file
        boolean success = theLocation.delete();
        // and also the paren directory if that is empty
        if (success) {
            File f = theLocation.getParentFile();
            while ((f.isDirectory()) && (f.list().length == 0)) {
                if (!(f.delete())) break;
                f = f.getParentFile();
            }
        }
        // reset all values
        theIndex = null;
        theLocation = null;
        return success;
    }
    
    public boolean removeEntry(String urlHash, boolean deleteComplete) throws IOException {
        // returns true if there was an entry before, false if the key did not exist
        // if after the removal the file is empty, then the file can be deleted if
        // the flag deleteComplete is set.
        if (urlHash == null || theIndex == null) return false;
        boolean wasEntry = (theIndex.remove(urlHash.getBytes()) != null);
        if ((theIndex.size() == 0) && (deleteComplete)) deleteComplete();
        return wasEntry;
    }
    
    public Iterator elements(boolean up) {
    // returns an enumeration of plasmaWordIndexEntry objects
        return new dbenum(up);
    }

    public final class dbenum implements Iterator {
        Iterator i;
        public dbenum(boolean up) {
            if (theIndex == null) {
                i = null;
            } else try {
                i = theIndex.contentRows(-1);
            } catch (kelondroException e) {
                e.printStackTrace();
                new File(theIndex.filename()).delete();
                i = null;
            }
        }
        public boolean hasNext() {
            return (i != null) && (i.hasNext());
        }
        public Object next() {
            if (i == null) return null;
            kelondroRow.Entry n = (kelondroRow.Entry) i.next();
            try {
                return new indexRWIEntryNew(new indexRWIEntryOld(n.getColString(0, null), n.getColString(1, null)));
            } catch (kelondroException e) { return null; }
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public String toString() {
        return "DB:" + theIndex.toString();
    }
    
}
