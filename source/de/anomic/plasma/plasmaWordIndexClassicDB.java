// plasmaWordIndexClassicDB.java
// -----------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 6.5.2005
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;

import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeedDB;

public class plasmaWordIndexClassicDB implements plasmaWordIndexInterface {
    

    // class variables
    private final File      databaseRoot;
    private final serverLog log;
    private int       size;

    public plasmaWordIndexClassicDB(File databaseRoot, serverLog log) {
	this.databaseRoot = databaseRoot;
        this.log = log;
        this.size = 0;
    }
    
    public int size() {
        return size;
    }
    
    public Iterator wordHashes(String startHash, boolean up) {
        return new iterateFiles(startHash, up);
    }
    
    public class iterateFiles implements Iterator {
        
        private final ArrayList hierarchy; // contains TreeSet elements, earch TreeSet contains File Entries
        private final Comparator comp;     // for string-compare
        private String buffer;       // the prefetch-buffer
        
        public iterateFiles(String startHash, boolean up) {
            this.hierarchy = new ArrayList();
            this.comp = kelondroMSetTools.fastStringComparator(up);
            
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
                        // the folder is empty, delete it
                        f.delete();
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

    public plasmaWordIndexEntity getIndex(String wordHash, boolean deleteIfEmpty, long maxTime) {
        try {
            return new plasmaWordIndexEntity(databaseRoot, wordHash, deleteIfEmpty);
        } catch (IOException e) {
            log.logSevere("plasmaWordIndexClassic.getIndex: " + e.getMessage());
            return null;
        }
    }
    
    public long getUpdateTime(String wordHash) {
        File f = plasmaWordIndexEntity.wordHash2path(databaseRoot, wordHash);
        if (f.exists()) return f.lastModified(); else return -1;
    }
    
    
    public void deleteIndex(String wordHash) {
        plasmaWordIndexEntity.removePlasmaIndex(databaseRoot, wordHash);
    }

    public int removeEntries(String wordHash, String[] urlHashes, boolean deleteComplete) {
        // removes all given url hashes from a single word index. Returns number of deletions.
        plasmaWordIndexEntity pi = null;
        int count = 0;
        try {
            pi = getIndex(wordHash, true, -1);
            for (int i = 0; i < urlHashes.length; i++)
                if (pi.removeEntry(urlHashes[i], deleteComplete)) count++;
            int size = pi.size();
            pi.close(); pi = null;
            // check if we can remove the index completely
            if ((deleteComplete) && (size == 0)) deleteIndex(wordHash);
            return count;
        } catch (IOException e) {
            log.logSevere("plasmaWordIndexClassic.removeEntries: " + e.getMessage());
            return count;
        } finally {
            if (pi != null) try{pi.close();}catch(Exception e){}
        }
    }
    
    public int addEntries(plasmaWordIndexEntryContainer container, long creationTime, boolean highPriority) {
	//System.out.println("* adding " + newEntries.size() + " cached word index entries for word " + wordHash); // debug
	// fetch the index cache
        if ((container == null) || (container.size() == 0)) return 0;
        
        // open file
        plasmaWordIndexEntity pi = null;
        try {
            pi = new plasmaWordIndexEntity(databaseRoot, container.wordHash(), false);
            int count = pi.addEntries(container);
            
            // close and return
            pi.close(); pi = null;
            return count;
        } catch (IOException e) {
            log.logSevere("plasmaWordIndexClassic.addEntries: " + e.getMessage());
            return 0;
        } finally {
            if (pi != null) try{pi.close();}catch (Exception e){}
        }
    }

    public void close(int waitingSeconds) {
        
    }

}
