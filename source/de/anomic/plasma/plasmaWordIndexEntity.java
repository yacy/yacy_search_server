// plasmaWordIndexEntity.java
// --------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 21.01.2004
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
import java.util.Enumeration;
import java.util.Iterator;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroRecords;
import de.anomic.kelondro.kelondroTree;

public class plasmaWordIndexEntity {

    private String       theWordHash;
    private kelondroTree theIndex;
    private TreeMap      theTmpMap;
    private File         theLocation;
    private boolean      delete;

    public plasmaWordIndexEntity(File databaseRoot, String wordHash, boolean deleteIfEmpty) throws IOException {
	theWordHash = wordHash;
	theIndex    = indexFile(databaseRoot, wordHash);
	theTmpMap   = null;
        delete      = deleteIfEmpty;
    }

    public static boolean removePlasmaIndex(File databaseRoot, String wordHash) throws IOException {
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
    
    private kelondroTree indexFile(File databaseRoot, String wordHash) throws IOException {
	theLocation = wordHash2path(databaseRoot, wordHash);
	File fp = theLocation.getParentFile();
	if (fp != null) fp.mkdirs();
	kelondroTree kt;
	long cacheSize = theLocation.length();
	if (cacheSize > 1048576) cacheSize = 1048576;
	if (theLocation.exists()) {
	    // open existing index file
	    kt = new kelondroTree(theLocation, cacheSize);
	} else {
	    // create new index file
	    kt = new kelondroTree(theLocation, cacheSize, plasmaURL.urlHashLength, plasmaWordIndexEntry.attrSpaceShort);
	}
	return kt; // everyone who get this should close it when finished!
    }

    public static File wordHash2path(File databaseRoot, String hash) {
	// creates a path that constructs hashing on a file system
	return new File (databaseRoot, "WORDS/" +
			 hash.substring(0,1) + "/" + hash.substring(1,2) + "/" + hash.substring(2,4) + "/" +
			 hash.substring(4,6) + "/" + hash + ".db");
    }

    public plasmaWordIndexEntity(String wordHash) {
	// this creates a nameless temporary index. It is needed for combined search
	// and used to hold the intersection of two indexes
        // if the nameless intity is suppose to hold indexes for a specific word,
        // it can be given here; othervise set wordhash to null
	theWordHash = wordHash;
	theIndex    = null;
        theLocation = null;
	theTmpMap   = new TreeMap();
    }

    public boolean isTMPEntity() {
        return theTmpMap != null;
    }
    
    public String wordHash() {
        return theWordHash;
    }
    
    public int size() {
	if (theTmpMap == null) {
            int size = theIndex.size(); 
            if ((size == 0) && (delete)) {
                try {
                    deleteComplete();
                } catch (IOException e) {
                    delete = false;
                }
                return 0;
            } else {
                return size;
            }
        } else {
            return theTmpMap.size();
        }
    }

    public void close() throws IOException {
	if (theTmpMap == null) {
            if (theIndex != null) theIndex.close(); 
            theIndex = null;
        } else theTmpMap = null;
    }

    public void finalize() {
	try {
	    close();
	} catch (IOException e) {}
    }

    public boolean contains(String urlhash) throws IOException {
        if (theTmpMap == null) return (theIndex.get(urlhash.getBytes()) != null); else return (theTmpMap.containsKey(urlhash));
    }
    
    public boolean contains(plasmaWordIndexEntry entry) throws IOException {
        if (theTmpMap == null) return (theIndex.get(entry.getUrlHash().getBytes()) != null); else return (theTmpMap.containsKey(entry.getUrlHash()));
    }
    
    public boolean addEntry(plasmaWordIndexEntry entry) throws IOException {
	if (theTmpMap == null) {
	    return (theIndex.put(entry.getUrlHash().getBytes(), entry.toEncodedForm(false).getBytes()) == null);
	} else {
	    return (theTmpMap.put(entry.getUrlHash(), entry) == null);
	}
    }
    
    public boolean deleteComplete() throws IOException {
        if (theTmpMap == null) {
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
            // switch to temporary more
            theTmpMap = new TreeMap();
            //theIndex.removeAll();
            return success;
	} else {
            theTmpMap = new TreeMap();
            return true;
	}
    }
    
    public boolean removeEntry(String urlHash, boolean deleteComplete) throws IOException {
        // returns true if there was an entry before, false if the key did not exist
        // if after the removal the file is empty, then the file can be deleted if
        // the flag deleteComplete is set.
        if (theTmpMap == null) {
            boolean wasEntry = (theIndex.remove(urlHash.getBytes()) != null);
            if ((theIndex.size() == 0) && (deleteComplete)) deleteComplete();
            return wasEntry;
	} else {
            return (theTmpMap.remove(urlHash) != null);
	}
    }
    
    public Enumeration elements(boolean up) {
	// returns an enumeration of plasmaWordIndexEntry objects
	if (theTmpMap == null) return new dbenum(up); else return new tmpenum(up);
    }

    public class dbenum implements Enumeration {
	Iterator i;
	public dbenum(boolean up) {
	    i = theIndex.nodeIterator(up, false);
	}
	public boolean hasMoreElements() {
	    return i.hasNext();
	}
	public Object nextElement() {
	    try {
		byte[][] n = ((kelondroRecords.Node) i.next()).getValues();
		return new plasmaWordIndexEntry(new String(n[0]), new String(n[1]));
	    } catch (IOException e) {
		throw new RuntimeException("dbenum: " + e.getMessage());
	    }
	}
    }
    public class tmpenum implements Enumeration {
	TreeMap searchTree;
        boolean up;
	public tmpenum(boolean up) {
            this.up = up;
            searchTree = (TreeMap) theTmpMap.clone(); // a shallow clone that is destroyed during search
	}
	public boolean hasMoreElements() {
	    return searchTree.size() > 0;
	}
	public Object nextElement() {
	    Object urlHash = (up) ? searchTree.firstKey() : searchTree.lastKey();
	    plasmaWordIndexEntry entry = (plasmaWordIndexEntry) searchTree.remove(urlHash);
	    return entry;
	}
    }

    public String toString() {
	if (theTmpMap == null) return "DB:" + theIndex.toString();
	else if (theTmpMap != null) return "MAP:" + theTmpMap.size() + " RECORDS IN " + theTmpMap.toString();
	else return "EMPTY";
    }

    
 
}
