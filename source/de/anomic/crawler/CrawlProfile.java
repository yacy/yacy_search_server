// CrawlProfile.java
// ------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.crawler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.services.federated.yacy.CacheStrategy;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Digest;

public class CrawlProfile extends ConcurrentHashMap<String, String> implements Map<String, String> {

    private static final long serialVersionUID = 5527325718810703504L;

    public static final String  MATCH_ALL_STRING    = ".*";
    public static final String  MATCH_NEVER_STRING  = "";
    public static final Pattern MATCH_ALL_PATTERN   = Pattern.compile(MATCH_ALL_STRING);
    public static final Pattern MATCH_NEVER_PATTERN = Pattern.compile(MATCH_NEVER_STRING);

    // this is a simple record structure that hold all properties of a single crawl start
    public static final String HANDLE           = "handle";
    public static final String NAME             = "name";
    public static final String START_URL        = "startURL";
    public static final String DEPTH            = "generalDepth";
    public static final String DIRECT_DOC_BY_URL= "directDocByURL";
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
    public static final String FILTER_URL_MUSTMATCH     = "generalFilter"; // for URLs
    public static final String FILTER_URL_MUSTNOTMATCH  = "nevermatch";    // for URLs
    public static final String FILTER_IP_MUSTMATCH      = "crawlingIPMustMatch";
    public static final String FILTER_IP_MUSTNOTMATCH   = "crawlingIPMustNotMatch";
    public static final String FILTER_COUNTRY_MUSTMATCH = "crawlingCountryMustMatch";

    private Pattern urlmustmatch = null, urlmustnotmatch = null, ipmustmatch = null, ipmustnotmatch = null;

    /**
     * Constructor which creates CrawlPofile from parameters.
     * @param name name of the crawl profile
     * @param startURL root URL of the crawl
     * @param urlMustMatch URLs which do not match this regex will be ignored
     * @param urlMustNotMatch URLs which match this regex will be ignored
     * @param ipMustMatch IPs from URLs which do not match this regex will be ignored
     * @param ipMustNotMatch IPs from URLs which match this regex will be ignored
     * @param countryMustMatch URLs from a specific country must match
     * @param depth height of the tree which will be created by the crawler
     * @param directDocByURL if true, then linked documents that cannot be parsed are indexed as document
     * @param recrawlIfOlder documents which have been indexed in the past will
     * be indexed again if they are older than the time (ms) in this parameter
     * @param domMaxPages maximum number from one domain which will be indexed
     * @param crawlingQ true if URLs containing questionmarks shall be indexed
     * @param indexText true if text content of URL shall be indexed
     * @param indexMedia true if media content of URL shall be indexed
     * @param storeHTCache true if content chall be kept in cache after indexing
     * @param remoteIndexing true if part of the crawl job shall be distributed
     * @param xsstopw true if static stop words shall be ignored
     * @param xdstopw true if dynamic stop words shall be ignored
     * @param xpstopw true if parent stop words shall be ignored
     * @param cacheStrategy determines if and how cache is used loading content
     */
    public CrawlProfile(
                 final String name,
                 final DigestURI startURL,
                 final String urlMustMatch,
                 final String urlMustNotMatch,
                 final String ipMustMatch,
                 final String ipMustNotMatch,
                 final String countryMustMatch,
                 final int depth,
                 final boolean directDocByURL,
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
        if (name == null || name.isEmpty()) {
            throw new NullPointerException("name must not be null or empty");
        }
        final String handle = (startURL == null)
                ? Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(name)).substring(0, Word.commonHashLength)
                : ASCII.String(startURL.hash());
        put(HANDLE,           handle);
        put(NAME,             name);
        put(START_URL,        (startURL == null) ? "" : startURL.toNormalform(true, false));
        put(FILTER_URL_MUSTMATCH,     (urlMustMatch == null) ? CrawlProfile.MATCH_ALL_STRING : urlMustMatch);
        put(FILTER_URL_MUSTNOTMATCH,  (urlMustNotMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : urlMustNotMatch);
        put(FILTER_IP_MUSTMATCH,      (ipMustMatch == null) ? CrawlProfile.MATCH_ALL_STRING : ipMustMatch);
        put(FILTER_IP_MUSTNOTMATCH,   (ipMustNotMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : ipMustNotMatch);
        put(FILTER_COUNTRY_MUSTMATCH, (countryMustMatch == null) ? "" : countryMustMatch);
        put(DEPTH,            depth);
        put(DIRECT_DOC_BY_URL, directDocByURL);
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

    /**
     * Constructor which creats a CrawlProfile from values in a Map.
     * @param ext contains values
     */
    public CrawlProfile(final Map<String, String> ext) {
        super(ext == null ? 1 : ext.size());
        if (ext != null) putAll(ext);
    }

    /**
     * Adds a parameter to CrawlProfile.
     * @param key name of the parameter
     * @param value values if the parameter
     */
    public final void put(final String key, final boolean value) {
        super.put(key, Boolean.toString(value));
    }

    /**
     * Adds a parameter to CrawlProfile.
     * @param key name of the parameter
     * @param value values if the parameter
     */
    public final void put(final String key, final int value) {
        super.put(key, Integer.toString(value));
    }

    /**
     * Adds a parameter to CrawlProfile.
     * @param key name of the parameter
     * @param value values if the parameter
     */
    public final void put(final String key, final long value) {
        super.put(key, Long.toString(value));
    }

    /**
     * Gets handle of the CrawlProfile.
     * @return handle of the profile
     */
    public String handle() {
        final String r = get(HANDLE);
        //if (r == null) return null;
        return r;
    }

    /**
     * Gets the name of the CrawlProfile.
     * @return  name of the profile
     */
    public String name() {
        final String r = get(NAME);
        if (r == null) return "";
        return r;
    }

    /**
     * Gets the root URL of the crawl job.
     * @return root URL
     */
    public String startURL() {
        final String r = get(START_URL);
        return r;
    }

    /**
     * Gets the regex which must be matched by URLs in order to be crawled.
     * @return regex which must be matched
     */
    public Pattern urlMustMatchPattern() {
        if (this.urlmustmatch == null) {
            final String r = get(FILTER_URL_MUSTMATCH);
            if (r == null || r.equals(CrawlProfile.MATCH_ALL_STRING)) {
                this.urlmustmatch = CrawlProfile.MATCH_ALL_PATTERN;
            } else {
                this.urlmustmatch = Pattern.compile(r);
            }
        }
        return this.urlmustmatch;
    }

    /**
     * Gets the regex which must not be matched by URLs in order to be crawled.
     * @return regex which must not be matched
     */
    public Pattern urlMustNotMatchPattern() {
        if (this.urlmustnotmatch == null) {
            final String r = get(FILTER_URL_MUSTNOTMATCH);
            if (r == null || r.equals(CrawlProfile.MATCH_NEVER_STRING)) {
                this.urlmustnotmatch = CrawlProfile.MATCH_NEVER_PATTERN;
            } else {
                this.urlmustnotmatch = Pattern.compile(r);
            }
        }
        return this.urlmustnotmatch;
    }

    /**
     * Gets the regex which must be matched by IPs in order to be crawled.
     * @return regex which must be matched
     */
    public Pattern ipMustMatchPattern() {
        if (this.ipmustmatch == null) {
            final String r = get(FILTER_IP_MUSTMATCH);
            if (r == null || r.equals(CrawlProfile.MATCH_ALL_STRING)) {
                this.ipmustmatch = CrawlProfile.MATCH_ALL_PATTERN;
            } else {
                this.ipmustmatch = Pattern.compile(r);
            }
        }
        return this.ipmustmatch;
    }

    /**
     * Gets the regex which must not be matched by IPs in order to be crawled.
     * @return regex which must not be matched
     */
    public Pattern ipMustNotMatchPattern() {
        if (this.ipmustnotmatch == null) {
            final String r = get(FILTER_IP_MUSTNOTMATCH);
            if (r == null || r.equals(CrawlProfile.MATCH_NEVER_STRING)) {
                this.ipmustnotmatch = CrawlProfile.MATCH_NEVER_PATTERN;
            } else {
                this.ipmustnotmatch = Pattern.compile(r);
            }
        }
        return this.ipmustnotmatch;
    }

    /**
     * get the list of countries that must match for the locations of the URLs IPs
     * @return a list of country codes
     */
    public String[] countryMustMatchList() {
        String countryMustMatch = get(FILTER_COUNTRY_MUSTMATCH);
        if (countryMustMatch == null) countryMustMatch = "";
        if (countryMustMatch.length() == 0) return new String[0];
        String[] list = countryMustMatch.split(",");
        if (list.length == 1 && list.length == 0) list = new String[0];
        return list;
    }

    /**
     * Gets depth of crawl job (or height of the tree which will be
     * created by the crawler).
     * @return depth of crawl job
     */
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

    public boolean directDocByURL() {
        final String r = get(DIRECT_DOC_BY_URL);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
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

    public void setCacheStrategy(final CacheStrategy newStrategy) {
        put(CACHE_STRAGEGY, newStrategy.toString());
    }

    /**
     * Gets the minimum age that an entry must have to be re-crawled.
     * @return time in ms
     */
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

    public static long getRecrawlDate(final long oldTimeMinutes) {
        return System.currentTimeMillis() - (60000L * oldTimeMinutes);
    }
}
