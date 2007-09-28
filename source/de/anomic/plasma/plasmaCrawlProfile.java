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
import de.anomic.kelondro.kelondroCloneableIterator;
import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMapObjects;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.server.serverCodings;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyURL;

public class plasmaCrawlProfile {
    
    private static HashMap domsCache = new HashMap();
    
    private kelondroMapObjects profileTable;
    private File profileTableFile;
    private long preloadTime;
    
    public plasmaCrawlProfile(File file, long preloadTime) {
        this.profileTableFile = file;
        this.preloadTime = preloadTime;
        profileTableFile.getParentFile().mkdirs();
        kelondroDyn dyn = new kelondroDyn(profileTableFile, true, true, preloadTime, yacySeedDB.commonHashLength, 2000, '#', kelondroNaturalOrder.naturalOrder, true, false, true);
        profileTable = new kelondroMapObjects(dyn, 500);
    }
    
    private void resetDatabase() {
        // deletes the profile database and creates a new one
        if (profileTable != null) profileTable.close();
        if (!(profileTableFile.delete())) throw new RuntimeException("cannot delete crawl profile database");
        profileTableFile.getParentFile().mkdirs();
        kelondroDyn dyn = new kelondroDyn(profileTableFile, true, true, preloadTime, yacySeedDB.commonHashLength, 2000, '#', kelondroNaturalOrder.naturalOrder, true, false, true);
        profileTable = new kelondroMapObjects(dyn, 500);
    }
    
    public void close() {
        profileTable.close();
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
        kelondroCloneableIterator handleIterator;
        String lastkey;
        public profileIterator(boolean up) throws IOException {
            handleIterator = profileTable.keys(up, false);
            lastkey = null;
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
                lastkey = (String) handleIterator.next();
                return getEntry(lastkey);
            } catch (kelondroException e) {
                resetDatabase();
                return null;
            }
        }
        public void remove() {
            if (lastkey != null) try {
                removeEntry(lastkey);
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
    
    public entry newEntry(Map mem) {
        entry ne = new entry(mem);
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
    
    public entry newEntry(String name, yacyURL startURL, String generalFilter, String specificFilter,
                           int generalDepth, int specificDepth,
                           int recrawlIfOlder /*minutes*/, int domFilterDepth,  int domMaxPages,
                           boolean crawlingQ,
                           boolean indexText, boolean indexMedia,
                           boolean storeHTCache, boolean storeTXCache,
                           boolean remoteIndexing,
                           boolean xsstopw, boolean xdstopw, boolean xpstopw) {
        
        entry ne = new entry(name, startURL, generalFilter, specificFilter,
                             generalDepth, specificDepth,
                             recrawlIfOlder, domFilterDepth, domMaxPages,
                             crawlingQ,
                             indexText, indexMedia,
                             storeHTCache, storeTXCache,
                             remoteIndexing,
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
        Map m = profileTable.getMap(handle);
        if (m == null) return null;
        return new entry(m);
    }

    public void changeEntry(entry e, String propName, String newValue) throws IOException {
        e.mem.put(propName,  newValue);
        profileTable.set(e.handle(), e.mem);
    }
    
    public static class DomProfile {
        
        public String referrer;
        public int depth, count;
        
        public DomProfile(String ref, int d) {
            this.referrer = ref;
            this.depth = d;
            this.count = 1;
        }
        
        public void inc() {
            this.count++;
        }
        
    }
    
    public static class entry {
        // this is a simple record structure that hold all properties of a single crawl start
        
        public static final String HANDLE           = "handle";
        public static final String NAME             = "name";
        public static final String START_URL        = "startURL";
        public static final String GENERAL_FILTER   = "generalFilter";
        public static final String SPECIFIC_FILTER  = "specificFilter";
        public static final String GENERAL_DEPTH    = "generalDepth";
        public static final String SPECIFIC_DEPTH   = "specificDepth";
        public static final String RECRAWL_IF_OLDER = "recrawlIfOlder";
        public static final String DOM_FILTER_DEPTH = "domFilterDepth";
        public static final String DOM_MAX_PAGES    = "domMaxPages";
        public static final String CRAWLING_Q       = "crawlingQ";
        public static final String INDEX_TEXT       = "indexText";
        public static final String INDEX_MEDIA      = "indexMedia";
        public static final String STORE_HTCACHE    = "storeHTCache";
        public static final String STORE_TXCACHE    = "storeTXCache";
        public static final String REMOTE_INDEXING  = "remoteIndexing";
        public static final String XSSTOPW          = "xsstopw";
        public static final String XDSTOPW          = "xdstopw";
        public static final String XPSTOPW          = "xpstopw";
        
        private Map mem;
        private Map doms;
        
        public entry(String name, yacyURL startURL, String generalFilter, String specificFilter,
                     int generalDepth, int specificDepth,
                     int recrawlIfOlder /*minutes*/, int domFilterDepth, int domMaxPages,
                     boolean crawlingQ,
                     boolean indexText, boolean indexMedia,
                     boolean storeHTCache, boolean storeTXCache,
                     boolean remoteIndexing,
                     boolean xsstopw, boolean xdstopw, boolean xpstopw) {
            if (name == null || name.length() == 0) throw new NullPointerException("name must not be null");
            String handle = (startURL == null) ? kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(Long.toString(System.currentTimeMillis()))).substring(0, yacySeedDB.commonHashLength) : startURL.hash();
            mem = new HashMap();
            mem.put(HANDLE,           handle);
            mem.put(NAME,             name);
            mem.put(START_URL,        (startURL == null) ? "" : startURL.toNormalform(true, false));
            mem.put(GENERAL_FILTER,   (generalFilter == null) ? ".*" : generalFilter);
            mem.put(SPECIFIC_FILTER,  (specificFilter == null) ? ".*" : specificFilter);
            mem.put(GENERAL_DEPTH,    Integer.toString(generalDepth));
            mem.put(SPECIFIC_DEPTH,   Integer.toString(specificDepth));
            mem.put(RECRAWL_IF_OLDER, Integer.toString(recrawlIfOlder));
            mem.put(DOM_FILTER_DEPTH, Integer.toString(domFilterDepth));
            mem.put(DOM_MAX_PAGES,    Integer.toString(domMaxPages));
            mem.put(CRAWLING_Q,       Boolean.toString(crawlingQ)); // crawling of urls with '?'
            mem.put(INDEX_TEXT,       Boolean.toString(indexText));
            mem.put(INDEX_MEDIA,      Boolean.toString(indexMedia));
            mem.put(STORE_HTCACHE,    Boolean.toString(storeHTCache));
            mem.put(STORE_TXCACHE,    Boolean.toString(storeTXCache));
            mem.put(REMOTE_INDEXING,  Boolean.toString(remoteIndexing));
            mem.put(XSSTOPW,          Boolean.toString(xsstopw)); // exclude static stop-words
            mem.put(XDSTOPW,          Boolean.toString(xdstopw)); // exclude dynamic stop-word
            mem.put(XPSTOPW,          Boolean.toString(xpstopw)); // exclude parent stop-words

            doms = new HashMap();
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
            this.doms = (HashMap) domsCache.get(this.mem.get(HANDLE));
            if (this.doms == null) this.doms = new HashMap();
        }
        
        public Map map() {
            return mem;
        }
        public String handle() {
            String r = (String) mem.get(HANDLE);
            if (r == null) return null; else return r;
        }
        public String name() {
            String r = (String) mem.get(NAME);
            if (r == null) return ""; else return r;
        }
        public String startURL() {
            String r = (String) mem.get(START_URL);
            if (r == null) return null; else return r;
        }
        public String generalFilter() {
            String r = (String) mem.get(GENERAL_FILTER);
            if (r == null) return ".*"; else return r;
        }
        public String specificFilter() {
            String r = (String) mem.get(SPECIFIC_FILTER);
            if (r == null) return ".*"; else return r;
        }
        public int generalDepth() {
            String r = (String) mem.get(GENERAL_DEPTH);
            if (r == null) return 0; else try {
                return Integer.parseInt(r);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        public int specificDepth() {
            String r = (String) mem.get(SPECIFIC_DEPTH);
            if (r == null) return 0; else try {
                return Integer.parseInt(r);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        public long recrawlIfOlder() {
            // returns a long (millis) that is the minimum age that
            // an antry must have to be re-crawled
            String r = (String) mem.get(RECRAWL_IF_OLDER);
            if (r == null) return Long.MAX_VALUE; else try {
                long l = Long.parseLong(r) * 60000L;
                return (l < 0) ? Long.MAX_VALUE : l;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        public int domFilterDepth() {
            // if the depth is equal or less to this depth,
            // then the current url feeds with its domain the crawl filter
            // if this is -1, all domains are feeded
            String r = (String) mem.get(DOM_FILTER_DEPTH);
            if (r == null) return Integer.MAX_VALUE; else try {
                int i = Integer.parseInt(r);
                if (i < 0) return Integer.MAX_VALUE;
                return i;
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }
        public int domMaxPages() {
            // this is the maximum number of pages that are crawled for a single domain
            // if -1, this means no limit
            String r = (String) mem.get(DOM_MAX_PAGES);
            if (r == null) return Integer.MAX_VALUE; else try {
                int i = Integer.parseInt(r);
                if (i < 0) return Integer.MAX_VALUE;
                return i;
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }
        public boolean crawlingQ() {
            String r = (String) mem.get(CRAWLING_Q);
            if (r == null) return false; else return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean indexText() {
            String r = (String) mem.get(INDEX_TEXT);
            if (r == null) return true; else return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean indexMedia() {
            String r = (String) mem.get(INDEX_MEDIA);
            if (r == null) return true; else return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean storeHTCache() {
            String r = (String) mem.get(STORE_HTCACHE);
            if (r == null) return false; else return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean storeTXCache() {
            String r = (String) mem.get(STORE_TXCACHE);
            if (r == null) return false; else return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean remoteIndexing() {
            String r = (String) mem.get(REMOTE_INDEXING);
            if (r == null) return false; else return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean excludeStaticStopwords() {
            String r = (String) mem.get(XSSTOPW);
            if (r == null) return false; else return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean excludeDynamicStopwords() {
            String r = (String) mem.get(XDSTOPW);
            if (r == null) return false; else return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean excludeParentStopwords() {
            String r = (String) mem.get(XPSTOPW);
            if (r == null) return false; else return (r.equals(Boolean.TRUE.toString()));
        }
        public void domInc(String domain, String referrer, int depth) {
            synchronized (domain.intern()) {
                DomProfile dp = (DomProfile) doms.get(domain);
                if (dp == null) {
                    // new domain
                    doms.put(domain, new DomProfile(referrer, depth));
                } else {
                    // increase counter
                    dp.inc();
                    doms.put(domain, dp);
                }
            }
            domsCache.put(this.mem.get(HANDLE), doms);
        }
        public boolean grantedDomAppearance(String domain) {
            int max = domFilterDepth();
            if (max == Integer.MAX_VALUE) return true;
            synchronized (domain.intern()) {
                DomProfile dp = (DomProfile) doms.get(domain);
                if (dp == null) {
                    return 0 < max;
                } else {
                    return dp.depth <= max;
                }
            }
        }

        public boolean grantedDomCount(String domain) {
            int max = domMaxPages();
            if (max == Integer.MAX_VALUE) return true;
            synchronized (domain.intern()) {
                DomProfile dp = (DomProfile) doms.get(domain);
                if (dp == null) {
                    return 0 < max;
                } else {
                    return dp.count <= max;
                }
            }
        }
        public int domSize() {
            return doms.size();
        }
        public boolean domExists(String domain) {
            if (domFilterDepth() == Integer.MAX_VALUE) return true;
            return doms.containsKey(domain);
        }

        public String domName(boolean attr, int index){
            Iterator domnamesi = doms.entrySet().iterator();
            String domname="";
            Map.Entry ey;
            DomProfile dp;
            int i = 0;
            while ((domnamesi.hasNext()) && (i < index)) {
                ey = (Map.Entry) domnamesi.next();
                i++;
            }
            if(domnamesi.hasNext()){
                ey = (Map.Entry) domnamesi.next();
                dp = (DomProfile) ey.getValue();
                domname = ((String) ey.getKey()) + ((attr) ? ("/r=" + dp.referrer + ", d=" + dp.depth + ", c=" + dp.count) : " ");
            }
            return domname;
        }
    }
}
