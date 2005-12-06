// plasmaWordIndexEntity.java
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
import java.util.TreeMap;
import java.util.Set;
import de.anomic.kelondro.kelondroRecords;
import de.anomic.kelondro.kelondroTree;
import de.anomic.kelondro.kelondroException;
import de.anomic.server.logging.serverLog;

public final class plasmaWordIndexEntity {

    private final String theWordHash;
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

    private kelondroTree indexFile(File databaseRoot, String wordHash) throws IOException {
        if (wordHash.length() < 12) throw new IOException("word hash wrong: '" + wordHash + "'");
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
                deleteComplete();
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
        if (entry == null) return false;
    if (theTmpMap == null) {
        return (theIndex.put(entry.getUrlHash().getBytes(), entry.toEncodedForm(false).getBytes()) == null);
    } else {
        return (theTmpMap.put(entry.getUrlHash(), entry) == null);
    }
    }
    
    public int addEntries(plasmaWordIndexEntryContainer container) throws IOException {
    //System.out.println("* adding " + newEntries.size() + " cached word index entries for word " + wordHash); // debug
    // fetch the index cache
        if ((container == null) || (container.size() == 0)) return 0;
        
        // open file
        int count = 0;
        
        // write from vector
        if (container != null) {
            Iterator i = container.entries();
            while (i.hasNext()) {
                if (addEntry((plasmaWordIndexEntry) i.next())) count++;
            }
        }
        
        // close and return
        return count;
    }
    
    public boolean deleteComplete() {
        if (theTmpMap == null) {
            try {theIndex.close();} catch (IOException e) {}
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
    
    public Iterator elements(boolean up) {
    // returns an enumeration of plasmaWordIndexEntry objects
    if (theTmpMap == null) return new dbenum(up); else return new tmpenum(up);
    }

    public final class dbenum implements Iterator {
        Iterator i;
        public dbenum(boolean up) {
            try {
                i = theIndex.nodeIterator(up, false);
            } catch (kelondroException e) {
                e.printStackTrace();
                theIndex.file().delete();
                i = null;
            }
        }
        public boolean hasNext() {
            return (i != null) && (i.hasNext());
        }
        public Object next() {
            if (i == null) return null;
            try {
                byte[][] n = ((kelondroRecords.Node) i.next()).getValues();
                return new plasmaWordIndexEntry(new String(n[0]), new String(n[1]));
            } catch (IOException e) {
                i = null;
                throw new RuntimeException("dbenum: " + e.getMessage());
            }
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
    public final class tmpenum implements Iterator {
        final TreeMap searchTree;
        boolean up;
        public tmpenum(boolean up) {
            this.up = up;
            searchTree = (TreeMap) theTmpMap.clone(); // a shallow clone that is destroyed during search
        }
        public boolean hasNext() {
            return searchTree.size() > 0;
        }
        public Object next() {
            Object urlHash = (up) ? searchTree.firstKey() : searchTree.lastKey();
            plasmaWordIndexEntry entry = (plasmaWordIndexEntry) searchTree.remove(urlHash);
            return entry;
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public String toString() {
        if (theTmpMap == null) return "DB:" + theIndex.toString();
        else if (theTmpMap != null) return "MAP:" + theTmpMap.size() + " RECORDS IN " + theTmpMap.toString();
        else return "EMPTY";
    }

    // join methods
    private static int log2(int x) {
        int l = 0;
        while (x > 0) {x = x >> 1; l++;}
        return l;
    }
    
    public void merge(plasmaWordIndexEntity otherEntity, long time) throws IOException {
        // this is a merge of another entity to this entity
        // the merge is interrupted when the given time is over
        // a time=-1 means: no timeout
        Iterator i = otherEntity.elements(true);
        long timeout = (time == -1) ? Long.MAX_VALUE : System.currentTimeMillis() + time;
        try {
        while ((i.hasNext()) && (System.currentTimeMillis() < timeout)) {
            addEntry((plasmaWordIndexEntry) i.next());            
        }
        } catch (kelondroException e) {
            serverLog.logSevere("PLASMA", "plasmaWordIndexEntity.merge: " + e.getMessage());
        }
    }
    
    public static plasmaWordIndexEntity joinEntities(Set entities, long time) throws IOException {
        
    		// big problem here: there cannot be a time-out for join, since a time-out will leave the joined set too big.
    		// this will result in a OR behavior of the search instead of an AND behavior
    	
        long stamp = System.currentTimeMillis();
        
        // order entities by their size
        TreeMap map = new TreeMap();
        plasmaWordIndexEntity singleEntity;
        Iterator i = entities.iterator();
        int count = 0;
        while (i.hasNext()) {
            // get next entity:
            singleEntity = (plasmaWordIndexEntity) i.next();
            
            // check result
            if ((singleEntity == null) || (singleEntity.size() == 0)) return new plasmaWordIndexEntity(null); // as this is a cunjunction of searches, we have no result if any word is not known
            
            // store result in order of result size
            map.put(new Long(singleEntity.size() * 1000 + count), singleEntity);
            count++;
        }
        
        // check if there is any result
        if (map.size() == 0) return new plasmaWordIndexEntity(null); // no result, nothing found
        
        // the map now holds the search results in order of number of hits per word
        // we now must pairwise build up a conjunction of these sets
        Long k = (Long) map.firstKey(); // the smallest, which means, the one with the least entries
        plasmaWordIndexEntity searchA, searchB, searchResult = (plasmaWordIndexEntity) map.remove(k);
        while ((map.size() > 0) && (searchResult.size() > 0)) {
            // take the first element of map which is a result and combine it with result
            k = (Long) map.firstKey(); // the next smallest...
            time -= (System.currentTimeMillis() - stamp); stamp = System.currentTimeMillis();
            searchA = searchResult;
            searchB = (plasmaWordIndexEntity) map.remove(k);
            searchResult = plasmaWordIndexEntity.joinConstructive(searchA, searchB, 2 * time / (map.size() + 1));
        // close the input files/structures
        if (searchA != searchResult) searchA.close();
        if (searchB != searchResult) searchB.close();
        }
        searchA = null; // free resources
        searchB = null; // free resources

        // in 'searchResult' is now the combined search result
        if (searchResult.size() == 0) return new plasmaWordIndexEntity(null);
        return searchResult;
    }
    
    
    public static plasmaWordIndexEntity joinConstructive(plasmaWordIndexEntity i1, plasmaWordIndexEntity i2, long time) throws IOException {
        if ((i1 == null) || (i2 == null)) return null;
        if ((i1.size() == 0) || (i2.size() == 0)) return new plasmaWordIndexEntity(null);
        
        // decide which method to use
        int high = ((i1.size() > i2.size()) ? i1.size() : i2.size());
        int low  = ((i1.size() > i2.size()) ? i2.size() : i1.size());
        int stepsEnum = 10 * (high + low - 1);
        int stepsTest = 12 * log2(high) * low;
        
        // start most efficient method
        if (stepsEnum > stepsTest) {
            if (i1.size() < i2.size())
                return joinConstructiveByTest(i1, i2, time);
            else
                return joinConstructiveByTest(i2, i1, time);
        } else {
            return joinConstructiveByEnumeration(i1, i2, time);
        }
    }
    
    private static plasmaWordIndexEntity joinConstructiveByTest(plasmaWordIndexEntity small, plasmaWordIndexEntity large, long time) throws IOException {
        System.out.println("DEBUG: JOIN METHOD BY TEST");
        plasmaWordIndexEntity conj = new plasmaWordIndexEntity(null); // start with empty search result
        Iterator se = small.elements(true);
        plasmaWordIndexEntry ie;
        long stamp = System.currentTimeMillis();
        try {
            while ((se.hasNext()) && ((System.currentTimeMillis() - stamp) < time)) {
                ie = (plasmaWordIndexEntry) se.next();
                if (large.contains(ie)) conj.addEntry(ie);
            }
        }  catch (kelondroException e) {
            //serverLog.logSevere("PLASMA", "joinConstructiveByTest: Database corrupt (" + e.getMessage() + "), deleting index");
            small.deleteComplete();
            return conj;
        }
        return conj;
    }
    
    private static plasmaWordIndexEntity joinConstructiveByEnumeration(plasmaWordIndexEntity i1, plasmaWordIndexEntity i2, long time) throws IOException {
        System.out.println("DEBUG: JOIN METHOD BY ENUMERATION");
        plasmaWordIndexEntity conj = new plasmaWordIndexEntity(null); // start with empty search result
        Iterator e1 = i1.elements(true);
        Iterator e2 = i2.elements(true);
        int c;
        if ((e1.hasNext()) && (e2.hasNext())) {
            plasmaWordIndexEntry ie1;
            plasmaWordIndexEntry ie2;
            try {
                ie1 = (plasmaWordIndexEntry) e1.next();
            }  catch (kelondroException e) {
                //serverLog.logSevere("PLASMA", "joinConstructiveByEnumeration: Database corrupt 1 (" + e.getMessage() + "), deleting index");
                i1.deleteComplete();
                return conj;
            }
            try {
                ie2 = (plasmaWordIndexEntry) e2.next();
            }  catch (kelondroException e) {
                //serverLog.logSevere("PLASMA", "joinConstructiveByEnumeration: Database corrupt 2 (" + e.getMessage() + "), deleting index");
                i2.deleteComplete();
                return conj;
            }
            long stamp = System.currentTimeMillis();
            while ((System.currentTimeMillis() - stamp) < time) {
                c = ie1.getUrlHash().compareTo(ie2.getUrlHash());
                if (c < 0) {
                    try {
                        if (e1.hasNext()) ie1 = (plasmaWordIndexEntry) e1.next(); else break;
                    } catch (kelondroException e) {
                        //serverLog.logSevere("PLASMA", "joinConstructiveByEnumeration: Database 1 corrupt (" + e.getMessage() + "), deleting index");
                        i1.deleteComplete();
                        break;
                    }
                } else if (c > 0) {
                    try {
                        if (e2.hasNext()) ie2 = (plasmaWordIndexEntry) e2.next(); else break;
                    } catch (kelondroException e) {
                        //serverLog.logSevere("PLASMA", "joinConstructiveByEnumeration: Database 2 corrupt (" + e.getMessage() + "), deleting index");
                        i2.deleteComplete();
                        break;
                    }
                } else {
                    // we have found the same urls in different searches!
                    conj.addEntry(ie1);
                    try {
                        if (e1.hasNext()) ie1 = (plasmaWordIndexEntry) e1.next(); else break;
                    } catch (kelondroException e) {
                        //serverLog.logSevere("PLASMA", "joinConstructiveByEnumeration: Database 1 corrupt (" + e.getMessage() + "), deleting index");
                        i1.deleteComplete();
                        break;
                    }
                    try {
                        if (e2.hasNext()) ie2 = (plasmaWordIndexEntry) e2.next(); else break;
                    }  catch (kelondroException e) {
                        //serverLog.logSevere("PLASMA", "joinConstructiveByEnumeration: Database 2 corrupt (" + e.getMessage() + "), deleting index");
                        i2.deleteComplete();
                        break;
                    }
                }
            }
        }
        return conj;
    }

}