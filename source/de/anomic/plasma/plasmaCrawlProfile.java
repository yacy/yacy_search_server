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
    private HashMap domsCache;
    private File profileTableFile;
    private int bufferkb;
    private long preloadTime;
    
    public static final int crawlProfileHandleLength = 4;  // name of the prefetch profile
    
    public plasmaCrawlProfile(File file, int bufferkb, long preloadTime) {
        this.profileTableFile = file;
        this.bufferkb = bufferkb;
        this.preloadTime = preloadTime;
        profileTableFile.getParentFile().mkdirs();
        kelondroDyn dyn = kelondroDyn.open(profileTableFile, bufferkb * 1024, preloadTime, crawlProfileHandleLength, 2000, '#', true);
        profileTable = new kelondroMap(dyn);
        domsCache = new HashMap();
    }
    
    public int cacheNodeChunkSize() {
        return profileTable.cacheNodeChunkSize();
    }    
    
    public int cacheObjectChunkSize() {
        return profileTable.cacheObjectChunkSize();
    }    
    
    public int[] cacheNodeStatus() {
        return profileTable.cacheNodeStatus();
    }    
    
    public long[] cacheObjectStatus() {
        return profileTable.cacheObjectStatus();
    }
    
    private void resetDatabase() {
        // deletes the profile database and creates a new one
        if (profileTable != null) try { profileTable.close(); } catch (IOException e) {}
        if (!(profileTableFile.delete())) throw new RuntimeException("cannot delete crawl profile database");
        profileTableFile.getParentFile().mkdirs();
        kelondroDyn dyn = kelondroDyn.open(profileTableFile, bufferkb * 1024, preloadTime, crawlProfileHandleLength, 2000, '#', true);
        profileTable = new kelondroMap(dyn);
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
    
    public entry newEntry(String name, String startURL, String generalFilter, String specificFilter,
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
        try {
            Map m = profileTable.get(handle);
            if (m == null) return null;
            return new entry(m);
        } catch (IOException e) {
            return null;
        }
    }
    
    public class DomProfile {
        
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
    
    public class entry {
        // this is a simple record structure that hold all properties of a single crawl start

        private Map mem;
        private Map doms;
        
        public entry(String name, String startURL, String generalFilter, String specificFilter,
                     int generalDepth, int specificDepth,
                     int recrawlIfOlder /*minutes*/, int domFilterDepth, int domMaxPages,
                     boolean crawlingQ,
                     boolean indexText, boolean indexMedia,
                     boolean storeHTCache, boolean storeTXCache,
                     boolean remoteIndexing,
                     boolean xsstopw, boolean xdstopw, boolean xpstopw) {
            String handle = kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(Long.toString(System.currentTimeMillis()))).substring(0, crawlProfileHandleLength);
            mem = new HashMap();
            mem.put("handle", handle);
            mem.put("name", name);
            mem.put("startURL", startURL);
            mem.put("generalFilter", generalFilter);
            mem.put("specificFilter", specificFilter);
            mem.put("generalDepth", Integer.toString(generalDepth));
            mem.put("specificDepth", Integer.toString(specificDepth));
            mem.put("recrawlIfOlder", Integer.toString(recrawlIfOlder));
            mem.put("domFilterDepth", Integer.toString(domFilterDepth));
            mem.put("domMaxPages", Integer.toString(domMaxPages));
            mem.put("crawlingQ", (crawlingQ) ? "true" : "false"); // crawling of urls with '?'
            mem.put("indexText", (indexText) ? "true" : "false");
            mem.put("indexMedia", (indexMedia) ? "true" : "false");
            mem.put("storeHTCache", (storeHTCache) ? "true" : "false");
            mem.put("storeTXCache", (storeTXCache) ? "true" : "false");
            mem.put("remoteIndexing", (remoteIndexing) ? "true" : "false");
            mem.put("xsstopw", (xsstopw) ? "true" : "false"); // exclude static stop-words
            mem.put("xdstopw", (xdstopw) ? "true" : "false"); // exclude dynamic stop-word
            mem.put("xpstopw", (xpstopw) ? "true" : "false"); // exclude parent stop-words

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
            this.doms = (HashMap) domsCache.get(this.mem.get("handle"));
            if (this.doms == null) this.doms = new HashMap();
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
        public long recrawlIfOlder() {
            // returns a long (millis) that is the minimum age that
            // an antry must have to be re-crawled
            String r = (String) mem.get("recrawlIfOlder");
            if (r == null) return Long.MAX_VALUE; else try {
                long l = Long.parseLong(r) * ((long) 60000);
                if (l < 0) return Long.MAX_VALUE; else return l;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        public int domFilterDepth() {
            // if the depth is equal or less to this depth,
            // then the current url feeds with its domain the crawl filter
            // if this is -1, all domains are feeded
            String r = (String) mem.get("domFilterDepth");
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
            String r = (String) mem.get("domMaxPages");
            if (r == null) return Integer.MAX_VALUE; else try {
                int i = Integer.parseInt(r);
                if (i < 0) return Integer.MAX_VALUE;
                return i;
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }
        public boolean crawlingQ() {
            String r = (String) mem.get("crawlingQ");
            if (r == null) return false; else return (r.equals("true"));
        }
        public boolean indexText() {
            String r = (String) mem.get("indexText");
            if (r == null) return true; else return (r.equals("true"));
        }
        public boolean indexMedia() {
            String r = (String) mem.get("indexMedia");
            if (r == null) return true; else return (r.equals("true"));
        }
        public boolean storeHTCache() {
            String r = (String) mem.get("storeHTCache");
            if (r == null) return false; else return (r.equals("true"));
        }
        public boolean storeTXCache() {
            String r = (String) mem.get("storeTXCache");
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
            domsCache.put(this.mem.get("handle"), doms);
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
        public String domNames(boolean attr, int maxlength) {
            Iterator domnamesi = doms.entrySet().iterator();
            String domnames="";
            Map.Entry ey;
            DomProfile dp;
            while (domnamesi.hasNext()) {
                ey = (Map.Entry) domnamesi.next();
                dp = (DomProfile) ey.getValue();
                domnames += ((String) ey.getKey()) + ((attr) ? ("/r=" + dp.referrer + ", d=" + dp.depth + ", c=" + dp.count + " ") : " ") + "<br>";
                if ((maxlength > 0) && (domnames.length() >= maxlength)) {
                    domnames = domnames.substring(0, maxlength-3) + "...";
                    break;
                }
            }
            return domnames;
        }
    }
}
