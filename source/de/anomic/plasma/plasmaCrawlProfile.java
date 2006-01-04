// plasmaCrawlProfile.java 
// ------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 25.02.2004
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMap;
import de.anomic.server.serverCodings;

public class plasmaCrawlProfile {
    
    private kelondroMap profileTable;
    private File profileTableFile;
    private int bufferkb;
    
    public plasmaCrawlProfile(File file, int bufferkb) {
        this.profileTableFile = file;
        kelondroDyn dyn = null;
        if (profileTableFile.exists()) try {
            dyn = new kelondroDyn(file, bufferkb * 1024);
        } catch (IOException e) {
            profileTableFile.delete();
            dyn = new kelondroDyn(file, bufferkb * 1024, plasmaURL.urlCrawlProfileHandleLength, 2000, true);
        } else {
            profileTableFile.getParentFile().mkdirs();
            dyn = new kelondroDyn(file, bufferkb * 1024, plasmaURL.urlCrawlProfileHandleLength, 2000, true);
        }
        profileTable = new kelondroMap(dyn);
    }
    
    public int[] dbCacheChunkSize() {
        return profileTable.cacheChunkSize();
    }    
    
    public int[] dbCacheFillStatus() {
        return profileTable.cacheFillStatus();
    }    
    
    private void resetDatabase() {
        // deletes the profile database and creates a new one
        if (profileTable != null) try { profileTable.close(); } catch (IOException e) {}
        if (!(profileTableFile.delete())) throw new RuntimeException("cannot delete crawl profile database");
        profileTableFile.getParentFile().mkdirs();
        profileTable = new kelondroMap(new kelondroDyn(profileTableFile, bufferkb * 1024, plasmaURL.urlCrawlProfileHandleLength, 2000, true));
    }
    
    public void close() {
        try {
            profileTable.close();
        } catch (IOException e) {}
    }
    
    public int size() {
        return profileTable.size();
    }
    
    public Iterator profiles(boolean up) {
        // enumerates profile entries
        try {
            return new profileIterator(up);
        } catch (IOException e) {
            return new HashSet().iterator();
        }
    }
    
    public class profileIterator implements Iterator {
        // the iterator iterates all keys, which are byte[] objects
        kelondroDyn.dynKeyIterator handleIterator;
        entry next;
        public profileIterator(boolean up) throws IOException {
            handleIterator = profileTable.keys(up, false);
            next = null;
        }
        public boolean hasNext() {
            try {
                return handleIterator.hasNext();
            } catch (kelondroException e) {
                resetDatabase();
                return false;
            }
        }
        public Object next() {
            try {
                return getEntry((String) handleIterator.next());
            } catch (kelondroException e) {
                resetDatabase();
                return null;
            }
        }
        public void remove() {
            if (next != null) try {
                Object handle = next.handle();
                if (handle != null) removeEntry((String) handle);
            } catch (kelondroException e) {
                resetDatabase();
            }
        }
    }
   
    public void removeEntry(String handle) {
        try {
        profileTable.remove(handle);
        } catch (IOException e) {}
    }
    
    public entry newEntry(String name, String startURL, String generalFilter, String specificFilter,
                           int generalDepth, int specificDepth,
                           boolean crawlingQ,
                           boolean storeHTCache, boolean storeTXCache,
                           boolean localIndexing, boolean remoteIndexing,
                           boolean xsstopw, boolean xdstopw, boolean xpstopw) {
        
        entry ne = new entry(name, startURL, generalFilter, specificFilter,
                             generalDepth, specificDepth,
                             crawlingQ, storeHTCache, storeTXCache, localIndexing, remoteIndexing,
                             xsstopw, xdstopw, xpstopw);
        try {
            profileTable.set(ne.handle(), ne.map());
        } catch (kelondroException e) {
            resetDatabase();
            try {
                profileTable.set(ne.handle(), ne.map());
            } catch (IOException ee) {
                e.printStackTrace();
                System.exit(0);
            }
        } catch (IOException e) {
            resetDatabase();
            try {
                profileTable.set(ne.handle(), ne.map());
            } catch (IOException ee) {
                e.printStackTrace();
                System.exit(0);
            }
        }
        return ne;
    }
    
    public entry getEntry(String handle) {
        try {
            Map m = profileTable.get(handle);
            if (m == null) return null;
            return new entry(m);
        } catch (IOException e) {
            return null;
        }
    }
    

    
    public class entry {
        // this is a simple record structure that hold all properties of a single crawl start

        private Map mem;
        public entry(String name, String startURL, String generalFilter, String specificFilter,
                     int generalDepth, int specificDepth,
                     boolean crawlingQ,
                     boolean storeHTCache, boolean storeTXCache,
                     boolean localIndexing, boolean remoteIndexing,
                     boolean xsstopw, boolean xdstopw, boolean xpstopw) {
            String handle = kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(Long.toString(System.currentTimeMillis()))).substring(0, plasmaURL.urlCrawlProfileHandleLength);
            mem = new HashMap();
            mem.put("handle", handle);
            mem.put("name", name);
            mem.put("startURL", startURL);
            mem.put("generalFilter", generalFilter);
            mem.put("specificFilter", specificFilter);
            mem.put("generalDepth", Integer.toString(generalDepth));
            mem.put("specificDepth", Integer.toString(specificDepth));
            mem.put("crawlingQ", (crawlingQ) ? "true" : "false"); // crawling of urls with '?'
            mem.put("storeHTCache", (storeHTCache) ? "true" : "false");
            mem.put("storeTXCache", (storeTXCache) ? "true" : "false");
            mem.put("localIndexing", (localIndexing) ? "true" : "false");
            mem.put("remoteIndexing", (remoteIndexing) ? "true" : "false");
            mem.put("xsstopw", (xsstopw) ? "true" : "false"); // exclude static stop-words
            mem.put("xdstopw", (xdstopw) ? "true" : "false"); // exclude dynamic stop-word
            mem.put("xpstopw", (xpstopw) ? "true" : "false"); // exclude parent stop-words
        }
        
        public String toString() {
            StringBuffer str = new StringBuffer();
            
            if (this.mem != null) {     
                str.append(this.mem.toString());
            }
            
            return str.toString();
        }        
        
        public entry(Map mem) {
            this.mem = mem;
        }
        
        public Map map() {
            return mem;
        }
        public String handle() {
            String r = (String) mem.get("handle");
            if (r == null) return null; else return r;
        }
        public String name() {
            String r = (String) mem.get("name");
            if (r == null) return ""; else return r;
        }
        public String startURL() {
            String r = (String) mem.get("startURL");
            if (r == null) return null; else return r;
        }
        public String generalFilter() {
            String r = (String) mem.get("generalFilter");
            if (r == null) return ".*"; else return r;
        }
        public String specificFilter() {
            String r = (String) mem.get("specificFilter");
            if (r == null) return ".*"; else return r;
        }
        public int generalDepth() {
            String r = (String) mem.get("generalDepth");
            if (r == null) return 0; else try {
                return Integer.parseInt(r);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        public int specificDepth() {
            String r = (String) mem.get("specificDepth");
            if (r == null) return 0; else try {
                return Integer.parseInt(r);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        public boolean crawlingQ() {
            String r = (String) mem.get("crawlingQ");
            if (r == null) return false; else return (r.equals("true"));
        }
        public boolean storeHTCache() {
            String r = (String) mem.get("storeHTCache");
            if (r == null) return false; else return (r.equals("true"));
        }
        public boolean storeTXCache() {
            String r = (String) mem.get("storeTXCache");
            if (r == null) return false; else return (r.equals("true"));
        }
        public boolean localIndexing() {
            String r = (String) mem.get("localIndexing");
            if (r == null) return false; else return (r.equals("true"));
        }
        public boolean remoteIndexing() {
            String r = (String) mem.get("remoteIndexing");
            if (r == null) return false; else return (r.equals("true"));
        }
        public boolean excludeStaticStopwords() {
            String r = (String) mem.get("xsstopw");
            if (r == null) return false; else return (r.equals("true"));
        }
        public boolean excludeDynamicStopwords() {
            String r = (String) mem.get("xdstopw");
            if (r == null) return false; else return (r.equals("true"));
        }
        public boolean excludeParentStopwords() {
            String r = (String) mem.get("xpstopw");
            if (r == null) return false; else return (r.equals("true"));
        }
        public void changeEntry(String propName, String newValue) throws IOException {
            mem.put(propName,  newValue);
            profileTable.set(handle(), mem);
        }
    }
}
