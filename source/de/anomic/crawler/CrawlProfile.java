// CrawlProfile.java 
// ------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.crawler;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import net.yacy.kelondro.blob.MapHeap;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.order.Digest;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.kelondroException;

public class CrawlProfile {
    
    public static final String MATCH_ALL = ".*";
    public static final String MATCH_NEVER = "";
    public static final String MATCH_BAD_URL = ".*memberlist.*|.*previous.*|.*next.*|.*p=.*";
    
    static ConcurrentHashMap<String, Map<String, DomProfile>> domsCache = new ConcurrentHashMap<String, Map<String, DomProfile>>();
    
    MapHeap profileTable;
    private final File profileTableFile;
    
    public CrawlProfile(final File file) throws IOException {
        //System.out.println("loading crawl profile from " + file);
        this.profileTableFile = file;
        profileTableFile.getParentFile().mkdirs();
        profileTable = new MapHeap(profileTableFile, Word.commonHashLength, NaturalOrder.naturalOrder, 1024 * 64, 500, '_');
        profileIterator pi = new profileIterator(true);
        entry e;
        while (pi.hasNext()) {
        	e = pi.next();
        	if (e == null) continue;
        	Log.logInfo("CrawlProfiles", "loaded Profile " + e.handle() + ": " + e.name());
        }
    }
    
    public void clear() {
        // deletes the profile database and creates a new one
        if (profileTable != null) profileTable.close();
        FileUtils.deletedelete(profileTableFile);
        profileTableFile.getParentFile().mkdirs();
        try {
            profileTable = new MapHeap(profileTableFile, Word.commonHashLength, NaturalOrder.naturalOrder, 1024 * 64, 500, '_');
        } catch (IOException e) {
            Log.logException(e);
        }
    }
    
    public void close() {
        if (profileTable != null) profileTable.close();
        this.profileTable = null;
    }
    
    public int size() {
        return profileTable.size();
    }
    
    public Iterator<entry> profiles(final boolean up) {
        // enumerates profile entries
        try {
            return new profileIterator(up);
        } catch (final IOException e) {
            Log.logException(e);
            return new HashSet<entry>().iterator();
        }
    }
    
    public class profileIterator implements Iterator<entry> {
        // the iterator iterates all keys, which are byte[] objects
        CloneableIterator<byte[]> handleIterator;
        String lastkey;
        public profileIterator(final boolean up) throws IOException {
            handleIterator = profileTable.keys(up, false);
            lastkey = null;
        }
        public boolean hasNext() {
            try {
                return handleIterator.hasNext();
            } catch (final kelondroException e) {
                Log.logException(e);
                clear();
                return false;
            }
        }
        public entry next() {
            try {
                lastkey = new String(handleIterator.next());
                return getEntry(lastkey);
            } catch (final kelondroException e) {
                Log.logException(e);
                clear();
                return null;
            }
        }
        public void remove() {
            if (lastkey != null) try {
                removeEntry(lastkey.getBytes());
            } catch (final kelondroException e) {
                Log.logException(e);
                clear();
            }
        }
    }
   
    public void removeEntry(final byte[] handle) {
        try {
            profileTable.remove(handle);
        } catch (final IOException e) {
            Log.logException(e);
        }
    }
    
    public entry newEntry(final Map<String, String> mem) {
        final entry ne = new entry(mem);
        try {
            profileTable.put(ne.handle().getBytes(), ne.map());
        } catch (final Exception e) {
            clear();
            try {
                profileTable.put(ne.handle().getBytes(), ne.map());
            } catch (final Exception ee) {
                Log.logException(e);
                System.exit(0);
            }
        }
        return ne;        
    }
    
    public entry newEntry( final String name,
                           final DigestURI startURL,
                           final String mustmatch, final String mustnotmatch,
                           final int generalDepth,
                           final long recrawlIfOlder /*date*/, final int domFilterDepth,  final int domMaxPages,
                           final boolean crawlingQ,
                           final boolean indexText, final boolean indexMedia,
                           final boolean storeHTCache, final boolean storeTXCache,
                           final boolean remoteIndexing,
                           final boolean xsstopw, final boolean xdstopw, final boolean xpstopw,
                           final CacheStrategy cacheStrategy) {
        
        final entry ne = new entry(
                             name, startURL,
                             mustmatch, mustnotmatch,
                             generalDepth,
                             recrawlIfOlder, domFilterDepth, domMaxPages,
                             crawlingQ,
                             indexText, indexMedia,
                             storeHTCache, storeTXCache,
                             remoteIndexing,
                             xsstopw, xdstopw, xpstopw,
                             cacheStrategy);
        try {
            profileTable.put(ne.handle().getBytes(), ne.map());
        } catch (final Exception e) {
            clear();
            try {
                profileTable.put(ne.handle().getBytes(), ne.map());
            } catch (final Exception ee) {
                Log.logException(e);
                System.exit(0);
            }
        }
        return ne;
    }
    
    public boolean hasEntry(final String handle) {
        return profileTable.has(handle.getBytes());
    }

    public entry getEntry(final String handle) {
        if (profileTable == null) return null;
        Map<String, String> m;
        try {
            m = profileTable.get(handle.getBytes());
        } catch (final IOException e) {
            Log.logException(e);
            return null;
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
            return null;
        }
        if (m == null) return null;
        return new entry(m);
    }

    public void changeEntry(final entry e, final String propName, final String newValue) throws IOException, RowSpaceExceededException {
        e.mem.put(propName,  newValue);
        assert e.handle() != null;
        profileTable.put(e.handle().getBytes(), e.mem);
    }
    
    public long getRecrawlDate(final long oldTimeMinutes) {
    	return System.currentTimeMillis() - (60000L * oldTimeMinutes);
    }
    
    public static class DomProfile {
        
        public String referrer;
        public int depth, count;
        
        public DomProfile(final String ref, final int d) {
            this.referrer = ref;
            this.depth = d;
            this.count = 1;
        }
        
        public void inc() {
            this.count++;
        }
        
    }
    
    public static enum CacheStrategy {
        NOCACHE(0),    // never use the cache, all content from fresh internet source
        IFFRESH(1),    // use the cache if the cache exists and is fresh using the proxy-fresh rules
        IFEXIST(2),    // use the cache if the cache exist. Do no check freshness. Otherwise use online source.
        CACHEONLY(3);  // never go online, use all content from cache. If no cache exist, treat content as unavailable
        public int code;
        private CacheStrategy(int code) {
            this.code = code;
        }
        public String toString() {
            return Integer.toString(this.code);
        }
        public static CacheStrategy decode(int code) {
            for (CacheStrategy strategy: CacheStrategy.values()) if (strategy.code == code) return strategy;
            return NOCACHE;
        }
    }
    
    public static class entry {
        // this is a simple record structure that hold all properties of a single crawl start
        
        public static final String HANDLE           = "handle";
        public static final String NAME             = "name";
        public static final String START_URL        = "startURL";
        public static final String FILTER_MUSTMATCH = "generalFilter";
        public static final String FILTER_MUSTNOTMATCH = "nevermatch";
        public static final String DEPTH            = "generalDepth";
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
        public static final String CACHE_STRAGEGY   = "cacheStrategy";
        
        private Map<String, String> mem;
        private Map<String, DomProfile> doms;
        private Pattern mustmatch = null, mustnotmatch = null;
        
        
        public entry(final String name, final DigestURI startURL,
                     final String mustmatch,
                     final String mustnotmatch,
                     final int depth,
                     final long recrawlIfOlder /*date*/,
                     final int domFilterDepth, final int domMaxPages,
                     final boolean crawlingQ,
                     final boolean indexText, final boolean indexMedia,
                     final boolean storeHTCache, final boolean storeTXCache,
                     final boolean remoteIndexing,
                     final boolean xsstopw, final boolean xdstopw, final boolean xpstopw,
                     final CacheStrategy cacheStrategy) {
            if (name == null || name.length() == 0) throw new NullPointerException("name must not be null");
            final String handle = (startURL == null) ? Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(name)).substring(0, Word.commonHashLength) : new String(startURL.hash());
            mem = new ConcurrentHashMap<String, String>(40);
            mem.put(HANDLE,           handle);
            mem.put(NAME,             name);
            mem.put(START_URL,        (startURL == null) ? "" : startURL.toNormalform(true, false));
            mem.put(FILTER_MUSTMATCH,   (mustmatch == null) ? MATCH_ALL : mustmatch);
            mem.put(FILTER_MUSTNOTMATCH,   (mustnotmatch == null) ? MATCH_NEVER : mustnotmatch);
            mem.put(DEPTH,    Integer.toString(depth));
            mem.put(RECRAWL_IF_OLDER, Long.toString(recrawlIfOlder));
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
            mem.put(CACHE_STRAGEGY,   cacheStrategy.toString());
            doms = new ConcurrentHashMap<String, DomProfile>();
        }
        
        @Override
        public String toString() {
            final StringBuilder str = new StringBuilder();
            
            if (this.mem != null) {     
                str.append(this.mem.toString());
            }
            
            return str.toString();
        }        
        
        public entry(final Map<String, String> mem) {
            this.mem = mem;
            this.doms = domsCache.get(this.mem.get(HANDLE));
            if (this.doms == null) this.doms = new ConcurrentHashMap<String, DomProfile>();
        }
        
        public Map<String, String> map() {
            return mem;
        }
        public String handle() {
            final String r = mem.get(HANDLE);
            //if (r == null) return null;
            return r;
        }
        public String name() {
            final String r = mem.get(NAME);
            if (r == null) return "";
            return r;
        }
        public String startURL() {
            final String r = mem.get(START_URL);
            return r;
        }
        public Pattern mustMatchPattern() {
            if (this.mustmatch == null) {
                String r = mem.get(FILTER_MUSTMATCH);
                if (r == null) r = MATCH_ALL;
                this.mustmatch = Pattern.compile(r);
            }
            return this.mustmatch;
        }
        public Pattern mustNotMatchPattern() {
            if (this.mustnotmatch == null) {
                String r = mem.get(FILTER_MUSTNOTMATCH);
                if (r == null) r = MATCH_NEVER;
                this.mustnotmatch = Pattern.compile(r);
            }
            return this.mustnotmatch;
        }
        public int depth() {
            final String r = mem.get(DEPTH);
            if (r == null) return 0;
            try {
                return Integer.parseInt(r);
            } catch (final NumberFormatException e) {
                Log.logException(e);
                return 0;
            }
        }
        public CacheStrategy cacheStrategy() {
            final String r = mem.get(CACHE_STRAGEGY);
            if (r == null) return CacheStrategy.IFFRESH;
            try {
                return CacheStrategy.decode(Integer.parseInt(r));
            } catch (final NumberFormatException e) {
                Log.logException(e);
                return CacheStrategy.IFFRESH;
            }
        }
        public void setCacheStrategy(CacheStrategy newStrategy) {
            mem.put(CACHE_STRAGEGY, newStrategy.toString());
        }
        public long recrawlIfOlder() {
            // returns a long (millis) that is the minimum age that
            // an entry must have to be re-crawled
            final String r = mem.get(RECRAWL_IF_OLDER);
            if (r == null) return 0L;
            try {
                final long l = Long.parseLong(r);
                return (l < 0) ? 0L : l;
            } catch (final NumberFormatException e) {
                Log.logException(e);
                return 0L;
            }
        }
        public int domFilterDepth() {
            // if the depth is equal or less to this depth,
            // then the current url feeds with its domain the crawl filter
            // if this is -1, all domains are feeded
            final String r = mem.get(DOM_FILTER_DEPTH);
            if (r == null) return Integer.MAX_VALUE;
            try {
                final int i = Integer.parseInt(r);
                if (i < 0) return Integer.MAX_VALUE;
                return i;
            } catch (final NumberFormatException e) {
                Log.logException(e);
                return Integer.MAX_VALUE;
            }
        }
        public int domMaxPages() {
            // this is the maximum number of pages that are crawled for a single domain
            // if -1, this means no limit
            final String r = mem.get(DOM_MAX_PAGES);
            if (r == null) return Integer.MAX_VALUE;
            try {
                final int i = Integer.parseInt(r);
                if (i < 0) return Integer.MAX_VALUE;
                return i;
            } catch (final NumberFormatException e) {
                Log.logException(e);
                return Integer.MAX_VALUE;
            }
        }
        public boolean crawlingQ() {
            final String r = mem.get(CRAWLING_Q);
            if (r == null) return false;
            return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean indexText() {
            final String r = mem.get(INDEX_TEXT);
            if (r == null) return true;
            return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean indexMedia() {
            final String r = mem.get(INDEX_MEDIA);
            if (r == null) return true;
            return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean storeHTCache() {
            final String r = mem.get(STORE_HTCACHE);
            if (r == null) return false;
            return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean storeTXCache() {
            final String r = mem.get(STORE_TXCACHE);
            if (r == null) return false;
            return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean remoteIndexing() {
            final String r = mem.get(REMOTE_INDEXING);
            if (r == null) return false;
            return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean excludeStaticStopwords() {
            final String r = mem.get(XSSTOPW);
            if (r == null) return false;
            return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean excludeDynamicStopwords() {
            final String r = mem.get(XDSTOPW);
            if (r == null) return false;
            return (r.equals(Boolean.TRUE.toString()));
        }
        public boolean excludeParentStopwords() {
            final String r = mem.get(XPSTOPW);
            if (r == null) return false;
            return (r.equals(Boolean.TRUE.toString()));
        }
        public void domInc(final String domain, final String referrer, final int depth) {
            final DomProfile dp = doms.get(domain);
            if (dp == null) {
                // new domain
                doms.put(domain, new DomProfile(referrer, depth));
            } else {
                // increase counter
                dp.inc();
            }
            domsCache.put(this.mem.get(HANDLE), doms);
        }
        public boolean grantedDomAppearance(final String domain) {
            final int max = domFilterDepth();
            if (max == Integer.MAX_VALUE) return true;
            final DomProfile dp = doms.get(domain);
            if (dp == null) {
                return 0 < max;
            }
            return dp.depth <= max;
        }

        public boolean grantedDomCount(final String domain) {
            final int max = domMaxPages();
            if (max == Integer.MAX_VALUE) return true;
            final DomProfile dp = doms.get(domain);
            if (dp == null) {
                return 0 < max;
            }
            return dp.count <= max;
        }
        public int domSize() {
            return doms.size();
        }
        public boolean domExists(final String domain) {
            if (domFilterDepth() == Integer.MAX_VALUE) return true;
            return doms.containsKey(domain);
        }

        public String domName(final boolean attr, final int index){
            final Iterator<Map.Entry<String, DomProfile>> domnamesi = doms.entrySet().iterator();
            String domname="";
            Map.Entry<String, DomProfile> ey;
            DomProfile dp;
            int i = 0;
            while ((domnamesi.hasNext()) && (i < index)) {
                ey = domnamesi.next();
                i++;
            }
            if (domnamesi.hasNext()) {
                ey = domnamesi.next();
                dp = ey.getValue();
                domname = ey.getKey() + ((attr) ? ("/r=" + dp.referrer + ", d=" + dp.depth + ", c=" + dp.count) : " ");
            }
            return domname;
        }
    }
    
}
