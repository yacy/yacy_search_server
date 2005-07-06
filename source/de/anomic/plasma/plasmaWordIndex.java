// plasmaWordIndex.java
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 02.02.2005
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

// compile with
// javac -classpath classes -sourcepath source -d classes -g source/de/anomic/plasma/*.java


package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;

import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeedDB;

public final class plasmaWordIndex {
    
    final File databaseRoot;
    final plasmaWordIndexCache ramCache;
    
    public plasmaWordIndex(File databaseRoot, int bufferkb, serverLog log) throws IOException {
        this.databaseRoot = databaseRoot;
        plasmaWordIndexClassicDB fileDB = new plasmaWordIndexClassicDB(databaseRoot, log);
        this.ramCache = new plasmaWordIndexCache(databaseRoot, fileDB, bufferkb, log);
    }
    
    public int maxURLinWordCache() {
        return ramCache.maxURLinWordCache();
    }
    
    public int wordCacheRAMSize() {
        return ramCache.wordCacheRAMSize();
    }
    
    public int[] assortmentSizes() {
        return ramCache.assortmentsSizes();
    }
        
    public void setMaxWords(int maxWords) {
        ramCache.setMaxWords(maxWords);
    }
    
    public int addEntries(plasmaWordIndexEntryContainer entries) {
        return ramCache.addEntries(entries, System.currentTimeMillis());
    }   
    
    public plasmaWordIndexEntity getEntity(String wordHash, boolean deleteIfEmpty) {
        return ramCache.getIndex(wordHash, deleteIfEmpty);
    }
    
    public int size() {
        return ramCache.size();
    }
    
    public int removeEntries(String wordHash, String[] urlHashes, boolean deleteComplete) {
        return ramCache.removeEntries(wordHash, urlHashes, deleteComplete);
    }
    
    public void close(int waitingBoundSeconds) {
        ramCache.close(waitingBoundSeconds);
    }
    
    public void deleteIndex(String wordHash) {
        ramCache.deleteIndex(wordHash);
    }
    
    public Iterator wordHashes(String startHash, boolean up, boolean rot) {
        return ramCache.wordHashes(startHash, up);
    }


    public Iterator fileIterator(String startHash, boolean up, boolean deleteEmpty) {
        return new iterateFiles(startHash, up, deleteEmpty);
    }
    
    public class iterateFiles implements Iterator {
        // Iterator of hash-strings in WORDS path
        
        private ArrayList hierarchy; // contains TreeSet elements, earch TreeSet contains File Entries
        private Comparator comp;     // for string-compare
        private String buffer;       // the prefetch-buffer
        private boolean delete;
        
        
        public iterateFiles(String startHash, boolean up, boolean deleteEmpty) {
            this.hierarchy = new ArrayList();
            this.comp = kelondroMSetTools.fastStringComparator(up);
            this.delete = deleteEmpty;
            
            // the we initially fill the hierarchy with the content of the root folder
            String path = "WORDS";
            TreeSet list = list(new File(databaseRoot, path));
            
            // if we have a start hash then we find the appropriate subdirectory to start
            if ((startHash != null) && (startHash.length() == yacySeedDB.commonHashLength)) {
                delete(startHash.substring(0, 1), list);
                if (list.size() > 0) {
                    hierarchy.add(list);
                    String[] paths = new String[]{startHash.substring(0, 1), startHash.substring(1, 2), startHash.substring(2, 4), startHash.substring(4, 6)};
                    int pathc = 0;
                    while ((pathc < paths.length) &&
                    (comp.compare((String) list.first(), paths[pathc]) == 0)) {
                        path = path + "/" + paths[pathc];
                        list = list(new File(databaseRoot, path));
                        delete(paths[pathc], list);
                        if (list.size() == 0) break;
                        hierarchy.add(list);
                        pathc++;
                    }
                }
                while (((buffer = next0()) != null) && (comp.compare(buffer, startHash) < 0)) {};
            } else {
                hierarchy.add(list);
                buffer = next0();
            }
        }
        
        private synchronized void delete(String pattern, TreeSet names) {
            String name;
            while ((names.size() > 0) && (comp.compare((new File(name = (String) names.first())).getName(), pattern) < 0)) names.remove(name);
        }
        
        private TreeSet list(File path) {
            //System.out.println("PATH: " + path);
            TreeSet t = new TreeSet(comp);
            String[] l = path.list();
            if (l != null) for (int i = 0; i < l.length; i++) t.add(path + "/" + l[i]);
            //else System.out.println("DEBUG: wrong path " + path);
            //System.out.println(t);
            return t;
        }
        
        private synchronized String next0() {
            // the object is a File pointing to the corresponding file
            File f;
            String n;
            TreeSet t;
            do {
                t = null;
                while ((t == null) && (hierarchy.size() > 0)) {
                    t = (TreeSet) hierarchy.get(hierarchy.size() - 1);
                    if (t.size() == 0) {
                        hierarchy.remove(hierarchy.size() - 1); // we step up one hierarchy
                        t = null;
                    }
                }
                if ((hierarchy.size() == 0) || (t.size() == 0)) return null; // this is the end
                // fetch value
                f = new File(n = (String) t.first());
                t.remove(n);
                // if the value represents another folder, we step into the next hierarchy
                if (f.isDirectory()) {
                    t = list(f);
                    if (t.size() == 0) {
                        if (delete) f.delete();
                    } else {
                        hierarchy.add(t);
                    }
                    f = null;
                }
            } while (f == null);
            // thats it
	    if ((f == null) || ((n = f.getName()) == null) || (n.length() < yacySeedDB.commonHashLength)) {
		return null;
	    } else {
		return n.substring(0, yacySeedDB.commonHashLength);
	    }
        }
        
        public boolean hasNext() {
            return buffer != null;
        }
        
        public Object next() {
            String r = buffer;
            while (((buffer = next0()) != null) && (comp.compare(buffer, r) < 0)) {};
            return r;
        }
        
        public void remove() {
            
        }
    }
    
    public static void main(String[] args) {
        //System.out.println(kelondroMSetTools.fastStringComparator(true).compare("RwGeoUdyDQ0Y", "rwGeoUdyDQ0Y"));
        try {
        plasmaWordIndex index = new plasmaWordIndex(new File("D:\\dev\\proxy\\DATA\\PLASMADB"), 555, new serverLog("TESTAPP"));
        Iterator i = index.wordHashes("5A8yhZMh_Kmv", true, true);
        while (i.hasNext()) {
            System.out.println("File: " + (String) i.next());
        }
        } catch (IOException e) {}
    }
}
