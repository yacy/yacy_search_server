// CrawlProfile.java 
// ------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 31.08.2010
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Digest;

public class CrawlProfile extends ConcurrentHashMap<String, String> implements Map<String, String> {

    private static final long serialVersionUID = 5527325718810703504L;
    
    public static final String MATCH_ALL = ".*";
    public static final String MATCH_NEVER = "";
    
    // this is a simple record structure that hold all properties of a single crawl start    
    public static final String HANDLE           = "handle";
    public static final String NAME             = "name";
    public static final String START_URL        = "startURL";
    public static final String FILTER_MUSTMATCH = "generalFilter";
    public static final String FILTER_MUSTNOTMATCH = "nevermatch";
    public static final String DEPTH            = "generalDepth";
    public static final String RECRAWL_IF_OLDER = "recrawlIfOlder";
    public static final String DOM_MAX_PAGES    = "domMaxPages";
    public static final String CRAWLING_Q       = "crawlingQ";
    public static final String PUSH_SOLR        = "pushSolr";
    public static final String INDEX_TEXT       = "indexText";
    public static final String INDEX_MEDIA      = "indexMedia";
    public static final String STORE_HTCACHE    = "storeHTCache";
    public static final String REMOTE_INDEXING  = "remoteIndexing";
    public static final String XSSTOPW          = "xsstopw";
    public static final String XDSTOPW          = "xdstopw";
    public static final String XPSTOPW          = "xpstopw";
    public static final String CACHE_STRAGEGY   = "cacheStrategy";
    
    private Pattern mustmatch = null, mustnotmatch = null;
    
    public CrawlProfile(
                 final String name,
                 final DigestURI startURL,
                 final String mustmatch,
                 final String mustnotmatch,
                 final int depth,
                 final long recrawlIfOlder /*date*/,
                 final int domMaxPages,
                 final boolean crawlingQ,
                 final boolean indexText,
                 final boolean indexMedia,
                 final boolean storeHTCache,
                 final boolean remoteIndexing,
                 final boolean xsstopw,
                 final boolean xdstopw,
                 final boolean xpstopw,
                 final CacheStrategy cacheStrategy) {
        super(40);
        if (name == null || name.length() == 0) throw new NullPointerException("name must not be null");
        final String handle = (startURL == null) ? Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(name)).substring(0, Word.commonHashLength) : UTF8.String(startURL.hash());
        put(HANDLE,           handle);
        put(NAME,             name);
        put(START_URL,        (startURL == null) ? "" : startURL.toNormalform(true, false));
        put(FILTER_MUSTMATCH,   (mustmatch == null) ? CrawlProfile.MATCH_ALL : mustmatch);
        put(FILTER_MUSTNOTMATCH,   (mustnotmatch == null) ? CrawlProfile.MATCH_NEVER : mustnotmatch);
        put(DEPTH,            depth);
        put(RECRAWL_IF_OLDER, recrawlIfOlder);
        put(DOM_MAX_PAGES,    domMaxPages);
        put(CRAWLING_Q,       crawlingQ); // crawling of urls with '?'
        put(INDEX_TEXT,       indexText);
        put(INDEX_MEDIA,      indexMedia);
        put(STORE_HTCACHE,    storeHTCache);
        put(REMOTE_INDEXING,  remoteIndexing);
        put(XSSTOPW,          xsstopw); // exclude static stop-words
        put(XDSTOPW,          xdstopw); // exclude dynamic stop-word
        put(XPSTOPW,          xpstopw); // exclude parent stop-words
        put(CACHE_STRAGEGY,   cacheStrategy.toString());
    }
    
    public CrawlProfile(Map<String, String> ext) {
        super(ext == null ? 1 : ext.size());
        if (ext != null) this.putAll(ext);
    }
    
    public void put(String key, boolean value) {
        super.put(key, Boolean.toString(value));
    }
    
    public void put(String key, int value) {
        super.put(key, Integer.toString(value));
    }
    
    public void put(String key, long value) {
        super.put(key, Long.toString(value));
    }
    
    public String handle() {
        final String r = get(HANDLE);
        //if (r == null) return null;
        return r;
    }
    public String name() {
        final String r = get(NAME);
        if (r == null) return "";
        return r;
    }
    public String startURL() {
        final String r = get(START_URL);
        return r;
    }
    public Pattern mustMatchPattern() {
        if (this.mustmatch == null) {
            String r = get(FILTER_MUSTMATCH);
            if (r == null) r = CrawlProfile.MATCH_ALL;
            this.mustmatch = Pattern.compile(r);
        }
        return this.mustmatch;
    }
    public Pattern mustNotMatchPattern() {
        if (this.mustnotmatch == null) {
            String r = get(FILTER_MUSTNOTMATCH);
            if (r == null) r = CrawlProfile.MATCH_NEVER;
            this.mustnotmatch = Pattern.compile(r);
        }
        return this.mustnotmatch;
    }
    public int depth() {
        final String r = get(DEPTH);
        if (r == null) return 0;
        try {
            return Integer.parseInt(r);
        } catch (final NumberFormatException e) {
            Log.logException(e);
            return 0;
        }
    }
    public CacheStrategy cacheStrategy() {
        final String r = get(CACHE_STRAGEGY);
        if (r == null) return CacheStrategy.IFEXIST;
        try {
            return CacheStrategy.decode(Integer.parseInt(r));
        } catch (final NumberFormatException e) {
            Log.logException(e);
            return CacheStrategy.IFEXIST;
        }
    }
    public void setCacheStrategy(CacheStrategy newStrategy) {
        put(CACHE_STRAGEGY, newStrategy.toString());
    }
    public long recrawlIfOlder() {
        // returns a long (millis) that is the minimum age that
        // an entry must have to be re-crawled
        final String r = get(RECRAWL_IF_OLDER);
        if (r == null) return 0L;
        try {
            final long l = Long.parseLong(r);
            return (l < 0) ? 0L : l;
        } catch (final NumberFormatException e) {
            Log.logException(e);
            return 0L;
        }
    }
    public int domMaxPages() {
        // this is the maximum number of pages that are crawled for a single domain
        // if -1, this means no limit
        final String r = get(DOM_MAX_PAGES);
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
        final String r = get(CRAWLING_Q);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }
    public boolean pushSolr() {
        final String r = get(PUSH_SOLR);
        if (r == null) return true;
        return (r.equals(Boolean.TRUE.toString()));
    }
    public boolean indexText() {
        final String r = get(INDEX_TEXT);
        if (r == null) return true;
        return (r.equals(Boolean.TRUE.toString()));
    }
    public boolean indexMedia() {
        final String r = get(INDEX_MEDIA);
        if (r == null) return true;
        return (r.equals(Boolean.TRUE.toString()));
    }
    public boolean storeHTCache() {
        final String r = get(STORE_HTCACHE);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }
    public boolean remoteIndexing() {
        final String r = get(REMOTE_INDEXING);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }
    public boolean excludeStaticStopwords() {
        final String r = get(XSSTOPW);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }
    public boolean excludeDynamicStopwords() {
        final String r = get(XDSTOPW);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }
    public boolean excludeParentStopwords() {
        final String r = get(XPSTOPW);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }
    
    public static enum CacheStrategy {
        NOCACHE(0),    // never use the cache, all content from fresh internet source
        IFFRESH(1),    // use the cache if the cache exists and is fresh using the proxy-fresh rules
        IFEXIST(2),    // use the cache if the cache exist. Do no check freshness. Otherwise use online source.
        CACHEONLY(3);  // never go online, use all content from cache. If no cache entry exist, consider content nevertheless as available 
        // the fifth case may be that the CacheStrategy object is assigned NULL. That means that no snippet creation is wanted.
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
        public static CacheStrategy parse(String name) {
            if (name == null) return null;
            if (name.equals("nocache")) return NOCACHE;
            if (name.equals("iffresh")) return IFFRESH;
            if (name.equals("ifexist")) return IFEXIST;
            if (name.equals("cacheonly")) return CACHEONLY;
            if (name.equals("true")) return IFEXIST;
            if (name.equals("false")) return null; // if this cache strategy is assigned as query attribute, null means "do not create a snippet"
            return null;
        }
        public String toName() {
            return this.name().toLowerCase();
        }
        public boolean isAllowedToFetchOnline() {
            return this.code < 3;
        }
        public boolean mustBeOffline() {
            return this.code == 3;
        }
    }

    public static long getRecrawlDate(final long oldTimeMinutes) {
        return System.currentTimeMillis() - (60000L * oldTimeMinutes);
    }
}
