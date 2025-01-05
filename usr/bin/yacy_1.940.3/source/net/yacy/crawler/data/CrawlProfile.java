// CrawlProfile.java
// ------------------------
// part of YaCy
// SPDX-FileCopyrightText: 2004 Michael Peter Christen <mc@yacy.net)>
// SPDX-License-Identifier: GPL-2.0-or-later
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

package net.yacy.crawler.data;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONTokener;

import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.CrawlSwitchboard;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.TagValency;
import net.yacy.kelondro.data.word.Word;
import net.yacy.search.query.QueryParams;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.server.serverObjects;

/**
 *  this is a simple record structure that hold all properties of a single crawl start
 */
public class CrawlProfile extends ConcurrentHashMap<String, String> implements Map<String, String> {

    private static final long serialVersionUID = 5527325718810703504L;

    /** Regular expression pattern matching everything */
    public static final String  MATCH_ALL_STRING    = ".*";

    /** Regular expression pattern matching nothing */
    public static final String  MATCH_NEVER_STRING  = "";

    /** Empty Solr query */
    public static final String  SOLR_EMPTY_QUERY  = "";

    /** Match all Solr query */
    public static final String  SOLR_MATCH_ALL_QUERY  = AbstractSolrConnector.CATCHALL_QUERY;

    /** Regular expression matching everything */
    public static final Pattern MATCH_ALL_PATTERN   = Pattern.compile(MATCH_ALL_STRING);

    /** Regular expression matching nothing */
    public static final Pattern MATCH_NEVER_PATTERN = Pattern.compile(MATCH_NEVER_STRING);

    public static final String CRAWL_PROFILE_PUSH_STUB = "push_";

    public enum CrawlAttribute {
        HANDLE                       ("handle",                     true,  CrawlAttribute.STRING,  "Profile Handle"),
        NAME                         ("name",                       true,  CrawlAttribute.STRING,  "Name"), // corresponds to the start url in many cases (not all)
        DEPTH                        ("generalDepth",               false, CrawlAttribute.INTEGER, "Crawl Depth"),
        DIRECT_DOC_BY_URL            ("directDocByURL",             false, CrawlAttribute.BOOLEAN, "Put all linked urls into index without parsing"),
        CRAWLER_URL_NODEPTHLIMITMATCH("crawlerNoLimitURLMustMatch", false, CrawlAttribute.STRING,  "URL No-Depth-Limit Must-Match Filter"),
        DOM_MAX_PAGES                ("domMaxPages",                false, CrawlAttribute.INTEGER, "Domain Max. Pages"),
        CRAWLING_Q                   ("crawlingQ",                  false, CrawlAttribute.BOOLEAN, "CrawlingQ / '?'-URLs"),
        FOLLOW_FRAMES                ("followFrames",               false, CrawlAttribute.BOOLEAN, "Flag if frames shall be followed (no by default)"),
        OBEY_HTML_ROBOTS_NOINDEX     ("obeyHtmlRobotsNoindex",      false, CrawlAttribute.BOOLEAN, "Obey html-robots-noindex"),
        OBEY_HTML_ROBOTS_NOFOLLOW    ("obeyHtmlRobotsNofollow",     false, CrawlAttribute.BOOLEAN, "Obey html-robots-nofollow"),
        CRAWLER_ALWAYS_CHECK_MEDIA_TYPE("crawlerAlwaysCheckMediaType", false, CrawlAttribute.BOOLEAN, "Always cross check file extension against actual Media Type"),
        CRAWLER_URL_MUSTMATCH        ("crawlerURLMustMatch",        false, CrawlAttribute.STRING,  "URL Must-Match Filter"),
        CRAWLER_URL_MUSTNOTMATCH     ("crawlerURLMustNotMatch",     false, CrawlAttribute.STRING,  "URL Must-Not-Match Filter"),
        CRAWLER_ORIGIN_URL_MUSTMATCH ("crawlerOriginURLMustMatch",  false, CrawlAttribute.STRING,  "Links Origin URL Must-Match Filter"),
        CRAWLER_ORIGIN_URL_MUSTNOTMATCH ("crawlerOriginURLMustNotMatch", false, CrawlAttribute.STRING, "Links Origin URL Must-Not-Match Filter"),
        CRAWLER_IP_MUSTMATCH         ("crawlerIPMustMatch",         false, CrawlAttribute.STRING,  "IP Must-Match Filter"),
        CRAWLER_IP_MUSTNOTMATCH      ("crawlerIPMustNotMatch",      false, CrawlAttribute.STRING,  "IP Must-Not-Match Filter"),
        CRAWLER_COUNTRY_MUSTMATCH    ("crawlerCountryMustMatch",    false, CrawlAttribute.STRING,  "Country Must-Match Filter"),
        INDEXING_URL_MUSTMATCH       ("indexURLMustMatch",          false, CrawlAttribute.STRING,  "Indexing URL Must-Match Filter"),
        INDEXING_URL_MUSTNOTMATCH    ("indexURLMustNotMatch",       false, CrawlAttribute.STRING,  "Indexing URL Must-Not-Match Filter"),
        INDEXING_CONTENT_MUSTMATCH   ("indexContentMustMatch",      false, CrawlAttribute.STRING,  "Indexing Content Must-Match Filter"),
        INDEXING_CONTENT_MUSTNOTMATCH("indexContentMustNotMatch",   false, CrawlAttribute.STRING,  "Indexing Content Must-Not-Match Filter"),
        INDEXING_MEDIA_TYPE_MUSTMATCH("indexMediaTypeMustMatch",    false, CrawlAttribute.STRING,  "Indexing Media Type (MIME) Must-Match Filter"),
        INDEXING_MEDIA_TYPE_MUSTNOTMATCH("indexMediaTypeMustNotMatch", false, CrawlAttribute.STRING, "Indexing Media Type (MIME) Must-Not-Match Filter"),
        INDEXING_SOLR_QUERY_MUSTMATCH("indexSolrQueryMustMatch",    false, CrawlAttribute.STRING,  "Indexing Solr Query Must-Match Filter"),
        INDEXING_SOLR_QUERY_MUSTNOTMATCH("indexSolrQueryMustNotMatch", false, CrawlAttribute.STRING,  "Indexing Solr Query Must-Not-Match Filter"),
        NOINDEX_WHEN_CANONICAL_UNEQUAL_URL("noindexWhenCanonicalUnequalURL", false, CrawlAttribute.STRING,  "No Indexing for Documents with Canonical != URL"),
        RECRAWL_IF_OLDER             ("recrawlIfOlder",             false, CrawlAttribute.INTEGER, "Recrawl If Older"),
        STORE_HTCACHE                ("storeHTCache",               false, CrawlAttribute.BOOLEAN, "Store in HTCache"),
        CACHE_STRAGEGY               ("cacheStrategy",              false, CrawlAttribute.STRING,  "Cache Strategy (NOCACHE,IFFRESH,IFEXIST,CACHEONLY)"),
        AGENT_NAME                   ("agentName",                  false, CrawlAttribute.STRING,  "User Agent Profile Name"),
        SNAPSHOTS_MAXDEPTH           ("snapshotsMaxDepth",          false, CrawlAttribute.INTEGER, "Max Depth for Snapshots"),
        SNAPSHOTS_REPLACEOLD         ("snapshotsReplaceOld",        false, CrawlAttribute.BOOLEAN, "Multiple Snapshot Versions - replace old with new"),
        SNAPSHOTS_MUSTNOTMATCH       ("snapshotsMustnotmatch",      false, CrawlAttribute.STRING,  "must-not-match filter for snapshot generation"),
        SNAPSHOTS_LOADIMAGE          ("snapshotsLoadImage",         false, CrawlAttribute.BOOLEAN, "Flag for Snapshot image generation"),
        REMOTE_INDEXING              ("remoteIndexing",             false, CrawlAttribute.BOOLEAN, "Remote Indexing (only for p2p networks)"),
        INDEX_TEXT                   ("indexText",                  false, CrawlAttribute.BOOLEAN, "Index Text"),
        INDEX_MEDIA                  ("indexMedia",                 false, CrawlAttribute.BOOLEAN, "Index Media"),
        COLLECTIONS                  ("collections",                false, CrawlAttribute.STRING,  "Collections (comma-separated list)"),
        DEFAULT_VALENCY              ("default_valency",            false, CrawlAttribute.STRING,  "default tag valency"),
        VALENCY_SWITCH_TAG_NAMES     ("valency_switch_tag_names",   false, CrawlAttribute.STRING,  "DIV Class names when default valency shall be switched"),
        SCRAPER                      ("scraper",                    false, CrawlAttribute.STRING,  "Declaration for Vocabulary Scraper"),
        TIMEZONEOFFSET               ("timezoneOffset",             true,  CrawlAttribute.INTEGER, "Time Zone of Crawl Start Agent");

        public static final int BOOLEAN = 0;
        public static final int INTEGER = 1;
        public static final int STRING = 2;

        public final String key, label;
        public final boolean readonly;
        public final int type;
        private CrawlAttribute(final String key, final boolean readonly, final int type, final String label) {
            this.key = key;
            this.readonly = readonly;
            this.type = type;
            this.label = label;
        }

        @Override
        public String toString() {
            return this.key;
        }
  }

    private Pattern crawlerurlmustmatch = null, crawlerurlmustnotmatch = null;

    /** Pattern on the URL a document must match to allow adding its embedded links to the crawl stack */
    private Pattern crawlerOriginUrlMustMatch = null;

    /** Pattern on the URL a document must not match to allow adding its embedded links to the crawl stack */
    private Pattern crawlerOriginUrlMustNotMatch = null;

    private Pattern crawleripmustmatch = null, crawleripmustnotmatch = null;
    private Pattern crawlernodepthlimitmatch = null;
    private Pattern indexurlmustmatch = null, indexurlmustnotmatch = null;
    private Pattern indexcontentmustmatch = null, indexcontentmustnotmatch = null;

    /** Pattern on the media type documents must match before being indexed 
     * @see CollectionSchema#content_type */
    private Pattern indexMediaTypeMustMatch = null;

    /** Pattern on the media type documents must not match before being indexed
     * @see CollectionSchema#content_type  */
    private Pattern indexMediaTypeMustNotMatch = null;

    private Pattern snapshotsMustnotmatch = null;

    private final Map<String, AtomicInteger> doms;
    private final TagValency defaultValency;
    private final Set<String> valencySwitchTagNames;
    private final VocabularyScraper scraper;

    /**
     * Constructor which creates CrawlPofile from parameters.
     * @param name name of the crawl profile
     * @param startURL root URL of the crawl
     * @param crawlerUrlMustMatch URLs which do not match this regex will be ignored in the crawler
     * @param crawlerUrlMustNotMatch URLs which match this regex will be ignored in the crawler
     * @param crawlerIpMustMatch IPs from URLs which do not match this regex will be ignored in the crawler
     * @param crawlerIpMustNotMatch IPs from URLs which match this regex will be ignored in the crawler
     * @param crawlerCountryMustMatch URLs from a specific country must match
     * @param crawlerNoDepthLimitMatch if matches, no depth limit is applied to the crawler
     * @param indexUrlMustMatch URLs which do not match this regex will be ignored for indexing
     * @param indexUrlMustNotMatch URLs which match this regex will be ignored for indexing
     * @param indexContentMustMatch content which do not match this regex will be ignored for indexing
     * @param indexContentMustNotMatch content which match this regex will be ignored for indexing
     * @param depth height of the tree which will be created by the crawler
     * @param directDocByURL if true, then linked documents that cannot be parsed are indexed as document
     * @param recrawlIfOlder documents which have been indexed in the past will be indexed again if they are older than the given date
     * @param domMaxPages maximum number from one domain which will be indexed
     * @param crawlingQ true if URLs containing questionmarks shall be indexed
     * @param indexText true if text content of URL shall be indexed
     * @param indexMedia true if media content of URL shall be indexed
     * @param storeHTCache true if content chall be kept in cache after indexing
     * @param remoteIndexing true if part of the crawl job shall be distributed
     * @param snapshotsMaxDepth if the current crawl depth is equal or below that given depth, a snapshot is generated
     * @param snapshotsLoadImage true if graphical (== pdf) shapshots shall be made
     * @param snapshotsReplaceOld true if snapshots shall not be historized
     * @param snapshotsMustnotmatch a regular expression; if it matches on the url, the snapshot is not generated
     * @param xsstopw true if static stop words shall be ignored
     * @param xdstopw true if dynamic stop words shall be ignored
     * @param xpstopw true if parent stop words shall be ignored
     * @param cacheStrategy determines if and how cache is used loading content
     * @param collections a comma-separated list of tags which are attached to index entries
     * @param userAgentName the profile name of the user agent to be used
     * @param scraper a scraper for vocabularies
     * @param timezoneOffset the time offset in minutes for scraped dates in text without time zone
     */
    public CrawlProfile(
                 String name,
                 final String crawlerUrlMustMatch, final String crawlerUrlMustNotMatch,
                 final String crawlerIpMustMatch, final String crawlerIpMustNotMatch,
                 final String crawlerCountryMustMatch, final String crawlerNoDepthLimitMatch,
                 final String indexUrlMustMatch, final String indexUrlMustNotMatch,
                 final String indexContentMustMatch, final String indexContentMustNotMatch,
                 final boolean noindexWhenCanonicalUnequalURL,
                 final int depth,
                 final boolean directDocByURL,
                 final Date recrawlIfOlder /*date*/,
                 final int domMaxPages,
                 final boolean crawlingQ, final boolean followFrames,
                 final boolean obeyHtmlRobotsNoindex, final boolean obeyHtmlRobotsNofollow,
                 final boolean indexText,
                 final boolean indexMedia,
                 final boolean storeHTCache,
                 final boolean remoteIndexing,
                 final int snapshotsMaxDepth,
                 final boolean snapshotsLoadImage,
                 final boolean snapshotsReplaceOld,
                 final String snapshotsMustnotmatch,
                 final CacheStrategy cacheStrategy,
                 final String collections,
                 final String userAgentName,
                 final TagValency defaultValency,
                 final Set<String> valencySwitchTagNames,
                 final VocabularyScraper scraper,
                 final int timezoneOffset) {
        super(40);
        if (name == null || name.isEmpty()) {
            throw new NullPointerException("name must not be null or empty");
        }
        if (name.length() > 256) name = name.substring(256);
        this.doms = new ConcurrentHashMap<String, AtomicInteger>();
        final String handle = Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(name + crawlerUrlMustMatch + depth + crawlerUrlMustNotMatch + domMaxPages + collections)).substring(0, Word.commonHashLength);
        put(CrawlAttribute.HANDLE.key,           handle);
        put(CrawlAttribute.NAME.key,             name);
        put(CrawlAttribute.AGENT_NAME.key, userAgentName);
        put(CrawlAttribute.CRAWLER_ALWAYS_CHECK_MEDIA_TYPE.key, true);
        put(CrawlAttribute.CRAWLER_URL_MUSTMATCH.key,         (crawlerUrlMustMatch == null) ? CrawlProfile.MATCH_ALL_STRING : crawlerUrlMustMatch);
        put(CrawlAttribute.CRAWLER_URL_MUSTNOTMATCH.key,      (crawlerUrlMustNotMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : crawlerUrlMustNotMatch);
        put(CrawlAttribute.CRAWLER_ORIGIN_URL_MUSTMATCH.key,  (crawlerUrlMustMatch == null) ? CrawlProfile.MATCH_ALL_STRING : crawlerUrlMustMatch);
        put(CrawlAttribute.CRAWLER_URL_MUSTNOTMATCH.key,      (crawlerUrlMustNotMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : crawlerUrlMustNotMatch);
        put(CrawlAttribute.CRAWLER_IP_MUSTMATCH.key,          (crawlerIpMustMatch == null) ? CrawlProfile.MATCH_ALL_STRING : crawlerIpMustMatch);
        put(CrawlAttribute.CRAWLER_IP_MUSTNOTMATCH.key,       (crawlerIpMustNotMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : crawlerIpMustNotMatch);
        put(CrawlAttribute.CRAWLER_COUNTRY_MUSTMATCH.key,     (crawlerCountryMustMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : crawlerCountryMustMatch);
        put(CrawlAttribute.CRAWLER_URL_NODEPTHLIMITMATCH.key, (crawlerNoDepthLimitMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : crawlerNoDepthLimitMatch);
        put(CrawlAttribute.INDEXING_URL_MUSTMATCH.key,        (indexUrlMustMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : indexUrlMustMatch);
        put(CrawlAttribute.INDEXING_URL_MUSTNOTMATCH.key,     (indexUrlMustNotMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : indexUrlMustNotMatch);
        put(CrawlAttribute.INDEXING_CONTENT_MUSTMATCH.key,    (indexContentMustMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : indexContentMustMatch);
        put(CrawlAttribute.INDEXING_CONTENT_MUSTNOTMATCH.key, (indexContentMustNotMatch == null) ? CrawlProfile.MATCH_NEVER_STRING : indexContentMustNotMatch);
        put(CrawlAttribute.DEPTH.key,                     depth);
        put(CrawlAttribute.DIRECT_DOC_BY_URL.key,         directDocByURL);
        put(CrawlAttribute.RECRAWL_IF_OLDER.key,          recrawlIfOlder == null ? Long.MAX_VALUE : recrawlIfOlder.getTime());
        put(CrawlAttribute.DOM_MAX_PAGES.key,             domMaxPages);
        put(CrawlAttribute.CRAWLING_Q.key,                crawlingQ); // crawling of urls with '?'
        put(CrawlAttribute.FOLLOW_FRAMES.key,             followFrames); // load pages contained in frames or ifames
        put(CrawlAttribute.OBEY_HTML_ROBOTS_NOINDEX.key,  obeyHtmlRobotsNoindex); // if false, then a meta robots tag containing 'noindex' is ignored
        put(CrawlAttribute.OBEY_HTML_ROBOTS_NOFOLLOW.key, obeyHtmlRobotsNofollow);
        put(CrawlAttribute.INDEX_TEXT.key,                indexText);
        put(CrawlAttribute.INDEX_MEDIA.key,               indexMedia);
        put(CrawlAttribute.STORE_HTCACHE.key,             storeHTCache);
        put(CrawlAttribute.REMOTE_INDEXING.key,           remoteIndexing);
        put(CrawlAttribute.SNAPSHOTS_MAXDEPTH.key,        snapshotsMaxDepth);
        put(CrawlAttribute.SNAPSHOTS_LOADIMAGE.key,       snapshotsLoadImage);
        put(CrawlAttribute.SNAPSHOTS_REPLACEOLD.key,      snapshotsReplaceOld);
        put(CrawlAttribute.SNAPSHOTS_MUSTNOTMATCH.key,    snapshotsMustnotmatch);
        put(CrawlAttribute.CACHE_STRAGEGY.key,            cacheStrategy.toString());
        put(CrawlAttribute.COLLECTIONS.key,               CommonPattern.SPACE.matcher(collections.trim()).replaceAll(""));
        // we transform the ignore_class_name and scraper information into a JSON Array
        this.defaultValency = defaultValency;
        this.valencySwitchTagNames = valencySwitchTagNames == null ? new HashSet<String>() : valencySwitchTagNames;
        String jsonString = new JSONArray(valencySwitchTagNames).toString();
        put(CrawlAttribute.DEFAULT_VALENCY.key, defaultValency.name());
        put(CrawlAttribute.VALENCY_SWITCH_TAG_NAMES.key, jsonString);
        this.scraper = scraper == null ? new VocabularyScraper() : scraper;
        jsonString = this.scraper.toString();
        assert jsonString != null && jsonString.length() > 0 && jsonString.charAt(0) == '{' : "jsonString = " + jsonString;
        put(CrawlAttribute.SCRAPER.key, jsonString);
        put(CrawlAttribute.TIMEZONEOFFSET.key, timezoneOffset);
        put(CrawlAttribute.INDEXING_MEDIA_TYPE_MUSTMATCH.key, CrawlProfile.MATCH_ALL_STRING);
        put(CrawlAttribute.INDEXING_MEDIA_TYPE_MUSTNOTMATCH.key, CrawlProfile.MATCH_NEVER_STRING);
        put(CrawlAttribute.INDEXING_SOLR_QUERY_MUSTMATCH.key, CrawlProfile.SOLR_MATCH_ALL_QUERY);
        put(CrawlAttribute.INDEXING_SOLR_QUERY_MUSTNOTMATCH.key, CrawlProfile.SOLR_EMPTY_QUERY);
        put(CrawlAttribute.NOINDEX_WHEN_CANONICAL_UNEQUAL_URL.key, noindexWhenCanonicalUnequalURL);
    }

    /**
     * Constructor which creates a CrawlProfile from values in a Map.
     * @param ext contains values
     */
    public CrawlProfile(final Map<String, String> ext) {
        super(ext == null ? 1 : ext.size());
        if (ext != null) putAll(ext);
        this.doms = new ConcurrentHashMap<String, AtomicInteger>();
        String defaultValency = ext.get(CrawlAttribute.DEFAULT_VALENCY.key);
        this.defaultValency = defaultValency == null || defaultValency.length() == 0 ? TagValency.EVAL : TagValency.valueOf(defaultValency);
        String jsonString = ext.get(CrawlAttribute.VALENCY_SWITCH_TAG_NAMES.key);
        JSONArray a;
        if (jsonString == null) {
            a = new JSONArray();
        } else {
            try {
                a = new JSONArray(new JSONTokener(jsonString));
            } catch(final JSONException e) {
                ConcurrentLog.logException(e);
                a = new JSONArray();
            }
        }
        this.valencySwitchTagNames = new HashSet<String>();
        for (int i = 0; i < a.length(); i++) try {
            this.valencySwitchTagNames.add(a.getString(i));
        } catch (JSONException e) {}
        jsonString = ext.get(CrawlAttribute.SCRAPER.key);
        if (jsonString == null || jsonString.length() == 0) {
            this.scraper = new VocabularyScraper();
        } else {
            VocabularyScraper loadedScraper;
            try {
                loadedScraper = new VocabularyScraper(jsonString);
            } catch(final JSONException e) {
                ConcurrentLog.logException(e);
                loadedScraper = new VocabularyScraper();    
            }
            this.scraper = loadedScraper;
        }
    }

    public TagValency defaultValency() {
        return this.defaultValency;
    }

    public Set<String> valencySwitchTagNames() {
        return this.valencySwitchTagNames;
    }

    public VocabularyScraper scraper() {
        return this.scraper;
    }

    public void domInc(final String domain) {
        if (domain == null) return; // may be correct for file system crawls
        final AtomicInteger dp = this.doms.get(domain);
        if (dp == null) {
            // new domain
            this.doms.put(domain, new AtomicInteger(1));
        } else {
            // increase counter
            dp.incrementAndGet();
        }
    }

    private String domName(final boolean attr, final int index){
        final Iterator<Map.Entry<String, AtomicInteger>> domnamesi = this.doms.entrySet().iterator();
        String domname="";
        Map.Entry<String, AtomicInteger> ey;
        AtomicInteger dp;
        int i = 0;
        while ((domnamesi.hasNext()) && (i < index)) {
            ey = domnamesi.next();
            i++;
        }
        if (domnamesi.hasNext()) {
            ey = domnamesi.next();
            dp = ey.getValue();
            domname = ey.getKey() + ((attr) ? ("/c=" + dp.get()) : " ");
        }
        return domname;
    }

    public ClientIdentification.Agent getAgent() {
        String agentName = this.get(CrawlAttribute.AGENT_NAME.key);
        return ClientIdentification.getAgent(agentName);
    }
    
    public AtomicInteger getCount(final String domain) {
        if (domain == null) return new AtomicInteger(0); // in case of file indexing this is required
        AtomicInteger dp = this.doms.get(domain);
        if (dp == null) {
            // new domain
            dp = new AtomicInteger(0);
            this.doms.put(domain, dp);
        }
        return dp;
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
    private final void put(final String key, final int value) {
        super.put(key, Integer.toString(value));
    }

    /**
     * Adds a parameter to CrawlProfile.
     * @param key name of the parameter
     * @param value values if the parameter
     */
    private final void put(final String key, final long value) {
        super.put(key, Long.toString(value));
    }

    /**
     * Gets handle of the CrawlProfile.
     * @return handle of the profile
     */
    public String handle() {
        final String r = get(CrawlAttribute.HANDLE.key);
        assert r != null;
        //if (r == null) return null;
        return r;
    }

    private Map<String, Pattern> cmap = null;

    /**
     * get the collections for this crawl
     * @return a list of collection names
     */
    public Map<String, Pattern> collections() {
        if (cmap != null) return cmap;
        final String r = get(CrawlAttribute.COLLECTIONS.key);
        this.cmap = collectionParser(r);
        return this.cmap;
    }

    public static Map<String, Pattern> collectionParser(String collectionString) {
        if (collectionString == null || collectionString.length() == 0) return new HashMap<String, Pattern>();
        String[] cs = CommonPattern.COMMA.split(collectionString);
        final Map<String, Pattern> cm = new LinkedHashMap<String, Pattern>();
        for (String c: cs) {
            int p = c.indexOf(':');
            if (p < 0) cm.put(c, QueryParams.catchall_pattern); else cm.put(c.substring(0, p), Pattern.compile(c.substring(p + 1)));
        }
        return cm;
    }

    /**
     * Gets the name of the CrawlProfile.
     * @return  name of the profile
     */
    public String name() {
        final String r = get(CrawlAttribute.NAME.key);
        if (r == null) return "";
        return r;
    }

    /**
     * create a name that takes the collection as name if this is not "user".
     * @return the name of the collection if that is not "user" or the name() otherwise;
     */
    public String collectionName() {
        final String r = get(CrawlAttribute.COLLECTIONS.key);
        return r == null || r.length() == 0 || "user".equals(r) ? name() : r;
    }

    /**
     * Gets the regex which must be matched by URLs in order to be crawled.
     * @return regex which must be matched
     */
    public Pattern urlMustMatchPattern() {
        if (this.crawlerurlmustmatch == null) {
            final String r = get(CrawlAttribute.CRAWLER_URL_MUSTMATCH.key);
            try {
                this.crawlerurlmustmatch = (r == null || r.equals(CrawlProfile.MATCH_ALL_STRING)) ? CrawlProfile.MATCH_ALL_PATTERN : Pattern.compile(r, Pattern.CASE_INSENSITIVE);
            } catch (final PatternSyntaxException e) { this.crawlerurlmustmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.crawlerurlmustmatch;
    }

    /**
     * Render the urlMustMatchPattern as a String of limited size, suffixing it with
     * "..." when it is truncated. Used to prevent unnecessary growth of the logs,
     * and to prevent exceeding the field size limit for
     * CollectionSchema.failreason_s (32k) when the pattern is present in a fail doc
     * added to the Solr index.
     * 
     * @return the urlMustMatchPattern formatted as a String of limited size
     */
    public String formattedUrlMustMatchPattern() {
        String patternStr = urlMustMatchPattern().toString();
        if(patternStr.length() > 1000) {
            /* The pattern may be quite large when using the 'From Link-List of URL' crawl start point. */
            patternStr = patternStr.substring(0, Math.min(patternStr.length(), 1000)) + "...";
        }
        return patternStr;
    }

    /**
     * Gets the regex which must not be matched by URLs in order to be crawled.
     * @return regex which must not be matched
     */
    public Pattern urlMustNotMatchPattern() {
        if (this.crawlerurlmustnotmatch == null) {
            final String r = get(CrawlAttribute.CRAWLER_URL_MUSTNOTMATCH.key);
            try {
                this.crawlerurlmustnotmatch = (r == null || r.equals(CrawlProfile.MATCH_NEVER_STRING)) ? CrawlProfile.MATCH_NEVER_PATTERN : Pattern.compile(r, Pattern.CASE_INSENSITIVE);
            } catch (final PatternSyntaxException e) { this.crawlerurlmustnotmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.crawlerurlmustnotmatch;
    }

    /**
     * Get the pattern on the URL a document must match to allow adding its embedded links to the crawl stack
     * 
     * @return a {@link Pattern} instance, defaulting to
     *         {@link CrawlProfile#MATCH_ALL_PATTERN} when the regular expression
     *         string is not set or its syntax is incorrect
     */
    public Pattern getCrawlerOriginUrlMustMatchPattern() {
        if (this.crawlerOriginUrlMustMatch == null) {
            /* Cache the compiled pattern for faster next calls */
            final String patternStr = get(CrawlAttribute.CRAWLER_ORIGIN_URL_MUSTMATCH.key);
            try {
                this.crawlerOriginUrlMustMatch = (patternStr == null
                        || patternStr.equals(CrawlProfile.MATCH_ALL_STRING)) ? CrawlProfile.MATCH_ALL_PATTERN
                                : Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            } catch (final PatternSyntaxException e) {
                this.crawlerOriginUrlMustMatch = CrawlProfile.MATCH_ALL_PATTERN;
            }
        }
        return this.crawlerOriginUrlMustMatch;
    }

    /**
     * Get the pattern on the URL a document must not match to allow adding its embedded links to the crawl stack
     * 
     * @return a {@link Pattern} instance, defaulting to
     *         {@link CrawlProfile#MATCH_NEVER_PATTERN} when the regular expression
     *         string is not set or its syntax is incorrect
     */
    public Pattern getCrawlerOriginUrlMustNotMatchPattern() {
        if (this.crawlerOriginUrlMustNotMatch == null) {
            /* Cache the compiled pattern for faster next calls */
            final String patternStr = get(CrawlAttribute.CRAWLER_ORIGIN_URL_MUSTNOTMATCH.key);
            try {
                this.crawlerOriginUrlMustNotMatch = (patternStr == null
                        || patternStr.equals(CrawlProfile.MATCH_NEVER_STRING)) ? CrawlProfile.MATCH_NEVER_PATTERN
                                : Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            } catch (final PatternSyntaxException e) {
                this.crawlerOriginUrlMustNotMatch = CrawlProfile.MATCH_NEVER_PATTERN;
            }
        }
        return this.crawlerOriginUrlMustNotMatch;
    }

    /**
     * Gets the regex which must be matched by IPs in order to be crawled.
     * @return regex which must be matched
     */
    public Pattern ipMustMatchPattern() {
        if (this.crawleripmustmatch == null) {
            final String r = get(CrawlAttribute.CRAWLER_IP_MUSTMATCH.key);
            try {
                this.crawleripmustmatch = (r == null || r.equals(CrawlProfile.MATCH_ALL_STRING)) ? CrawlProfile.MATCH_ALL_PATTERN : Pattern.compile(r, Pattern.CASE_INSENSITIVE);
            } catch (final PatternSyntaxException e) { this.crawleripmustmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.crawleripmustmatch;
    }

    /**
     * Gets the regex which must not be matched by IPs in order to be crawled.
     * @return regex which must not be matched
     */
    public Pattern ipMustNotMatchPattern() {
        if (this.crawleripmustnotmatch == null) {
            final String r = get(CrawlAttribute.CRAWLER_IP_MUSTNOTMATCH.key);
            try {
                this.crawleripmustnotmatch = (r == null || r.equals(CrawlProfile.MATCH_NEVER_STRING)) ? CrawlProfile.MATCH_NEVER_PATTERN : Pattern.compile(r, Pattern.CASE_INSENSITIVE);
            } catch (final PatternSyntaxException e) { this.crawleripmustnotmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.crawleripmustnotmatch;
    }

    /**
     * get the list of countries that must match for the locations of the URLs IPs
     * @return a list of country codes
     */
    public String[] countryMustMatchList() {
        String countryMustMatch = get(CrawlAttribute.CRAWLER_COUNTRY_MUSTMATCH.key);
        if (countryMustMatch == null) countryMustMatch = CrawlProfile.MATCH_NEVER_STRING;
        if (countryMustMatch.isEmpty()) return new String[0];
        String[] list = CommonPattern.COMMA.split(countryMustMatch);
        if (list.length == 1 && list.length == 0) list = new String[0];
        return list;
    }

    /**
     * If the regex matches with the url, then there is no depth limit on the crawl (it overrides depth == 0)
     * @return regex which must be matched
     */
    public Pattern crawlerNoDepthLimitMatchPattern() {
        if (this.crawlernodepthlimitmatch == null) {
            final String r = get(CrawlAttribute.CRAWLER_URL_NODEPTHLIMITMATCH.key);
            try {
                this.crawlernodepthlimitmatch = (r == null || r.equals(CrawlProfile.MATCH_NEVER_STRING)) ? CrawlProfile.MATCH_NEVER_PATTERN : Pattern.compile(r, Pattern.CASE_INSENSITIVE);
            } catch (final PatternSyntaxException e) { this.crawlernodepthlimitmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.crawlernodepthlimitmatch;
    }

    /**
     * Gets the regex which must be matched by URLs in order to be indexed.
     * @return regex which must be matched
     */
    public Pattern indexUrlMustMatchPattern() {
        if (this.indexurlmustmatch == null) {
            final String r = get(CrawlAttribute.INDEXING_URL_MUSTMATCH.key);
            try {
                this.indexurlmustmatch = (r == null || r.equals(CrawlProfile.MATCH_ALL_STRING)) ? CrawlProfile.MATCH_ALL_PATTERN : Pattern.compile(r, Pattern.CASE_INSENSITIVE);
            } catch (final PatternSyntaxException e) { this.indexurlmustmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.indexurlmustmatch;
    }

    /**
     * Gets the regex which must not be matched by URLs in order to be indexed.
     * @return regex which must not be matched
     */
    public Pattern indexUrlMustNotMatchPattern() {
        if (this.indexurlmustnotmatch == null) {
            final String r = get(CrawlAttribute.INDEXING_URL_MUSTNOTMATCH.key);
            try {
                this.indexurlmustnotmatch = (r == null || r.equals(CrawlProfile.MATCH_NEVER_STRING)) ? CrawlProfile.MATCH_NEVER_PATTERN : Pattern.compile(r, Pattern.CASE_INSENSITIVE);
            } catch (final PatternSyntaxException e) { this.indexurlmustnotmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.indexurlmustnotmatch;
    }

    /**
     * Gets the regex which must be matched by URLs in order to be indexed.
     * @return regex which must be matched
     */
    public Pattern indexContentMustMatchPattern() {
        if (this.indexcontentmustmatch == null) {
            final String r = get(CrawlAttribute.INDEXING_CONTENT_MUSTMATCH.key);
            try {
                this.indexcontentmustmatch = (r == null || r.equals(CrawlProfile.MATCH_ALL_STRING)) ? CrawlProfile.MATCH_ALL_PATTERN : Pattern.compile(r, Pattern.CASE_INSENSITIVE);
            } catch (final PatternSyntaxException e) { this.indexcontentmustmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.indexcontentmustmatch;
    }

    /**
     * Gets the regex which must not be matched by URLs in order to be indexed.
     * @return regex which must not be matched
     */
    public Pattern indexContentMustNotMatchPattern() {
        if (this.indexcontentmustnotmatch == null) {
            final String r = get(CrawlAttribute.INDEXING_CONTENT_MUSTNOTMATCH.key);
            try {
                this.indexcontentmustnotmatch = (r == null || r.equals(CrawlProfile.MATCH_NEVER_STRING)) ? CrawlProfile.MATCH_NEVER_PATTERN : Pattern.compile(r, Pattern.CASE_INSENSITIVE);
            } catch (final PatternSyntaxException e) { this.indexcontentmustnotmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.indexcontentmustnotmatch;
    }

    /**
     * Get the Pattern on media type that documents must match in order to be indexed
     * 
     * @return a {@link Pattern} instance, defaulting to
     *         {@link CrawlProfile#MATCH_ALL_PATTERN} when the regular expression
     *         string is not set or its syntax is incorrect
     */
    public Pattern getIndexMediaTypeMustMatchPattern() {
        if (this.indexMediaTypeMustMatch == null) {
            /* Cache the compiled pattern for faster next calls */
            final String patternStr = get(CrawlAttribute.INDEXING_MEDIA_TYPE_MUSTMATCH.key);
            try {
                this.indexMediaTypeMustMatch = (patternStr == null
                        || patternStr.equals(CrawlProfile.MATCH_ALL_STRING)) ? CrawlProfile.MATCH_ALL_PATTERN
                                : Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            } catch (final PatternSyntaxException e) {
                this.indexMediaTypeMustMatch = CrawlProfile.MATCH_ALL_PATTERN;
            }
        }
        return this.indexMediaTypeMustMatch;
    }

    /**
     * Get the Pattern on media type that documents must not match in order to be indexed
     * 
     * @return a {@link Pattern} instance, defaulting to
     *         {@link CrawlProfile#MATCH_NEVER_PATTERN} when the regular expression
     *         string is not set or its syntax is incorrect
     */
    public Pattern getIndexMediaTypeMustNotMatchPattern() {
        if (this.indexMediaTypeMustNotMatch == null) {
            /* Cache the compiled pattern for faster next calls */
            final String patternStr = get(CrawlAttribute.INDEXING_MEDIA_TYPE_MUSTNOTMATCH.key);
            try {
                this.indexMediaTypeMustNotMatch = (patternStr == null
                        || patternStr.equals(CrawlProfile.MATCH_NEVER_STRING)) ? CrawlProfile.MATCH_NEVER_PATTERN
                                : Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            } catch (final PatternSyntaxException e) {
                this.indexMediaTypeMustNotMatch = CrawlProfile.MATCH_NEVER_PATTERN;
            }
        }
        return this.indexMediaTypeMustNotMatch;
    }

    /**
     * Gets depth of crawl job (or height of the tree which will be
     * created by the crawler).
     * @return depth of crawl job
     */
    public int depth() {
        final String r = get(CrawlAttribute.DEPTH.key);
        if (r == null) return 0;
        try {
            return Integer.parseInt(r);
        } catch (final NumberFormatException e) {
            ConcurrentLog.logException(e);
            return 0;
        }
    }

    /**
     * @return true when URLs of unsupported resources (no parser available or denied format) should
     *         be indexed as links (with metadata only on URL and not on content).
     */
    public boolean isIndexNonParseableUrls() {
        final String r = get(CrawlAttribute.DIRECT_DOC_BY_URL.key);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }

    /**
     * @return true when the crawler must always cross check the eventual URL file
     *         extension against the actual Media Type, even when file extension is
     *         unknown or unsupported. False when the crawler should not load URLs
     *         with an unknown or unsupported file extension.
     */
    public boolean isCrawlerAlwaysCheckMediaType() {
        final String r = get(CrawlAttribute.CRAWLER_ALWAYS_CHECK_MEDIA_TYPE.key);
        if (r == null) {
            return false;
        }
        return (r.equals(Boolean.TRUE.toString()));
    }

    public CacheStrategy cacheStrategy() {
        final String r = get(CrawlAttribute.CACHE_STRAGEGY.key);
        if (r == null) return CacheStrategy.IFEXIST;
        try {
            return CacheStrategy.decode(Integer.parseInt(r));
        } catch (final NumberFormatException e) {
            ConcurrentLog.logException(e);
            return CacheStrategy.IFEXIST;
        }
    }

    public void setCacheStrategy(final CacheStrategy newStrategy) {
        put(CrawlAttribute.CACHE_STRAGEGY.key, newStrategy.toString());
    }

    /**
     * Gets the minimum date that an entry must have to be re-crawled.
     * @return time in ms representing a date
     */
    public long recrawlIfOlder() {
        // returns a long (millis) that is the minimum age that
        // an entry must have to be re-crawled
        final String r = get(CrawlAttribute.RECRAWL_IF_OLDER.key);
        if (r == null) return 0L;
        try {
            final long l = Long.parseLong(r);
            return (l < 0) ? 0L : l;
        } catch (final NumberFormatException e) {
            ConcurrentLog.logException(e);
            return 0L;
        }
    }

    public int domMaxPages() {
        // this is the maximum number of pages that are crawled for a single domain
        // if -1, this means no limit
        final String r = get(CrawlAttribute.DOM_MAX_PAGES.key);
        if (r == null) return Integer.MAX_VALUE;
        try {
            final int i = Integer.parseInt(r);
            if (i < 0) return Integer.MAX_VALUE;
            return i;
        } catch (final NumberFormatException e) {
            ConcurrentLog.logException(e);
            return Integer.MAX_VALUE;
        }
    }

    public boolean crawlingQ() {
        final String r = get(CrawlAttribute.CRAWLING_Q.key);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean followFrames() {
        final String r = get(CrawlAttribute.FOLLOW_FRAMES.key);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean obeyHtmlRobotsNoindex() {
        final String r = get(CrawlAttribute.OBEY_HTML_ROBOTS_NOINDEX.key);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean obeyHtmlRobotsNofollow() {
        final String r = get(CrawlAttribute.OBEY_HTML_ROBOTS_NOFOLLOW.key);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean indexText() {
        final String r = get(CrawlAttribute.INDEX_TEXT.key);
        if (r == null) return true;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean indexMedia() {
        final String r = get(CrawlAttribute.INDEX_MEDIA.key);
        if (r == null) return true;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean noindexWhenCanonicalUnequalURL() {
        final String r = get(CrawlAttribute.NOINDEX_WHEN_CANONICAL_UNEQUAL_URL.key);
        if (r == null) return true;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean storeHTCache() {
        final String r = get(CrawlAttribute.STORE_HTCACHE.key);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean remoteIndexing() {
        final String r = get(CrawlAttribute.REMOTE_INDEXING.key);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public int snapshotMaxdepth() {
        final String r = get(CrawlAttribute.SNAPSHOTS_MAXDEPTH.key);
        if (r == null) return -1;
        try {
            final int i = Integer.parseInt(r);
            if (i < 0) return -1;
            return i;
        } catch (final NumberFormatException e) {
            ConcurrentLog.logException(e);
            return -1;
        }
    }

    public boolean snapshotLoadImage() {
        final String r = get(CrawlAttribute.SNAPSHOTS_LOADIMAGE.key);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public boolean snapshotReplaceold() {
        final String r = get(CrawlAttribute.SNAPSHOTS_REPLACEOLD.key);
        if (r == null) return false;
        return (r.equals(Boolean.TRUE.toString()));
    }

    public Pattern snapshotsMustnotmatch() {
        if (this.snapshotsMustnotmatch == null) {
            final String r = get(CrawlAttribute.SNAPSHOTS_MUSTNOTMATCH.key);
            try {
                this.snapshotsMustnotmatch = (r == null || r.equals(CrawlProfile.MATCH_ALL_STRING)) ? CrawlProfile.MATCH_ALL_PATTERN : Pattern.compile(r, Pattern.CASE_INSENSITIVE);
            } catch (final PatternSyntaxException e) { this.snapshotsMustnotmatch = CrawlProfile.MATCH_NEVER_PATTERN; }
        }
        return this.snapshotsMustnotmatch;
    }

    public int timezoneOffset() {
        final String timezoneOffset = get(CrawlAttribute.TIMEZONEOFFSET.key);
        if (timezoneOffset == null) return 0;
        try {
            return Integer.parseInt(timezoneOffset);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * get a recrawl date for a given age in minutes
     * @param oldTimeMinutes
     * @return a Date representing the recrawl date limit
     */
    public static Date getRecrawlDate(final long oldTimeMinutes) {
        return new Date(System.currentTimeMillis() - (60000L * oldTimeMinutes));
    }

    public static String siteFilter(final Collection<? extends MultiProtocolURL> urls) {
        final StringBuilder filter = new StringBuilder();
        filter.append("(smb|ftp|https?)://(www.)?(");
        for (final MultiProtocolURL url: urls) {
            String host = url.getHost();
            if (host == null) continue;
            if (host.startsWith("www.")) host = host.substring(4);
            filter.append(Pattern.quote(host.toLowerCase(Locale.ROOT))).append(".*|");
        }
        filter.setCharAt(filter.length() - 1, ')');
        return filter.toString();
    }

    public static String mustMatchFilterFullDomain(final MultiProtocolURL url) {
        String host = url.getHost();
        if (host == null) return url.getProtocol() + ".*";
        if (host.startsWith("www.")) host = host.substring(4);
        String protocol = url.getProtocol();
        if ("http".equals(protocol) || "https".equals(protocol)) protocol = "https?+";
        return new StringBuilder(host.length() + 20).append(protocol).append("://(www.)?").append(Pattern.quote(host)).append(".*").toString();
    }

    public static String subpathFilter(final Collection<? extends MultiProtocolURL> urls) {
        LinkedHashSet<String> filters = new LinkedHashSet<String>(); // first collect in a set to eliminate doubles
        for (final MultiProtocolURL url: urls) filters.add(mustMatchSubpath(url));
        final StringBuilder filter = new StringBuilder();
        for (final String urlfilter: filters) filter.append('|').append(urlfilter);
        return filter.length() > 0 ? filter.substring(1) : CrawlProfile.MATCH_ALL_STRING;
    }

    public static String mustMatchSubpath(final MultiProtocolURL url) {
        String host = url.getHost();
        if (host == null) return url.getProtocol() + ".*";
        if (host.startsWith("www.")) host = host.substring(4);
        String protocol = url.getProtocol();
        if ("http".equals(protocol) || "https".equals(protocol)) protocol = "https?+";
        return new StringBuilder(host.length() + 20).append(protocol).append("://(www.)?").append(Pattern.quote(host.toLowerCase(Locale.ROOT))).append(url.getPath()).append(".*").toString();
    }

    public boolean isPushCrawlProfile() {
        return this.name().startsWith(CrawlProfile.CRAWL_PROFILE_PUSH_STUB);
    }

    public void putProfileEntry(
            final String CRAWL_PROFILE_PREFIX,
            final serverObjects prop,
            final boolean active,
            final boolean dark,
            final int count,
            final int domlistlength) {
        boolean terminateButton = active && !CrawlSwitchboard.DEFAULT_PROFILES.contains(this.name());
        boolean deleteButton = !active;
        prop.put(CRAWL_PROFILE_PREFIX + count + "_status", terminateButton ? 1 : deleteButton ? 0 : 2);
        prop.put(CRAWL_PROFILE_PREFIX + count + "_terminateButton", terminateButton);
        prop.put(CRAWL_PROFILE_PREFIX + count + "_terminateButton_handle", this.handle());
        prop.put(CRAWL_PROFILE_PREFIX + count + "_deleteButton", deleteButton);
        prop.put(CRAWL_PROFILE_PREFIX + count + "_deleteButton_handle", this.handle());
        prop.put(CRAWL_PROFILE_PREFIX + count + "_dark", dark ? "1" : "0");
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_handle", this.handle());
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_name", this.name());
        prop.put(CRAWL_PROFILE_PREFIX + count + "_depth", this.depth());
        prop.put(CRAWL_PROFILE_PREFIX + count + "_directDocByURL", this.isIndexNonParseableUrls() ? 1 : 0);
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_crawlerNoLimitURLMustMatch", this.get(CrawlAttribute.CRAWLER_URL_NODEPTHLIMITMATCH.key));
        prop.put(CRAWL_PROFILE_PREFIX + count + "_domMaxPages", this.domMaxPages());
        prop.put(CRAWL_PROFILE_PREFIX + count + "_crawlingQ", this.crawlingQ() ? 1 : 0);
        prop.put(CRAWL_PROFILE_PREFIX + count + "_followFrames", this.followFrames() ? 1 : 0);
        prop.put(CRAWL_PROFILE_PREFIX + count + "_obeyHtmlRobotsNoindex", this.obeyHtmlRobotsNoindex() ? 1 : 0);
        prop.put(CRAWL_PROFILE_PREFIX + count + "_obeyHtmlRobotsNofollow", this.obeyHtmlRobotsNofollow() ? 1 : 0);
        prop.put(CRAWL_PROFILE_PREFIX + count + "_crawlerAlwaysCheckMediaType", this.isCrawlerAlwaysCheckMediaType());
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_crawlerURLMustMatch", this.get(CrawlAttribute.CRAWLER_URL_MUSTMATCH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_crawlerURLMustNotMatch", this.get(CrawlAttribute.CRAWLER_URL_MUSTNOTMATCH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_crawlerOriginURLMustMatch", this.get(CrawlAttribute.CRAWLER_ORIGIN_URL_MUSTMATCH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_crawlerOriginURLMustNotMatch", this.get(CrawlAttribute.CRAWLER_ORIGIN_URL_MUSTNOTMATCH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_crawlerIPMustMatch", this.get(CrawlAttribute.CRAWLER_IP_MUSTMATCH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_crawlerIPMustNotMatch", this.get(CrawlAttribute.CRAWLER_IP_MUSTNOTMATCH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_crawlerCountryMustMatch", this.get(CrawlAttribute.CRAWLER_COUNTRY_MUSTMATCH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_indexURLMustMatch", this.get(CrawlAttribute.INDEXING_URL_MUSTMATCH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_indexURLMustNotMatch", this.get(CrawlAttribute.INDEXING_URL_MUSTNOTMATCH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_indexContentMustMatch", this.get(CrawlAttribute.INDEXING_CONTENT_MUSTMATCH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_indexContentMustNotMatch", this.get(CrawlAttribute.INDEXING_CONTENT_MUSTNOTMATCH.key));
        prop.put(CRAWL_PROFILE_PREFIX + count + "_" + CrawlAttribute.NOINDEX_WHEN_CANONICAL_UNEQUAL_URL, noindexWhenCanonicalUnequalURL() ? 1 : 0);
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_" + CrawlAttribute.INDEXING_MEDIA_TYPE_MUSTMATCH.key, this.get(CrawlAttribute.INDEXING_MEDIA_TYPE_MUSTMATCH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_" + CrawlAttribute.INDEXING_MEDIA_TYPE_MUSTNOTMATCH.key, this.get(CrawlAttribute.INDEXING_MEDIA_TYPE_MUSTNOTMATCH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_" + CrawlAttribute.INDEXING_SOLR_QUERY_MUSTMATCH.key, this.get(CrawlAttribute.INDEXING_SOLR_QUERY_MUSTMATCH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_" + CrawlAttribute.INDEXING_SOLR_QUERY_MUSTNOTMATCH.key, this.get(CrawlAttribute.INDEXING_SOLR_QUERY_MUSTNOTMATCH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_recrawlIfOlder", this.recrawlIfOlder() == Long.MAX_VALUE ? "eternity" : (new Date(this.recrawlIfOlder()).toString()));
        prop.put(CRAWL_PROFILE_PREFIX + count + "_storeHTCache", this.storeHTCache() ? 1 : 0);
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_cacheStrategy", this.get(CrawlAttribute.CACHE_STRAGEGY.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_agentName", this.get(CrawlAttribute.AGENT_NAME.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_" + CrawlAttribute.SNAPSHOTS_MAXDEPTH.key, this.get(CrawlAttribute.SNAPSHOTS_MAXDEPTH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_" + CrawlAttribute.SNAPSHOTS_REPLACEOLD.key, this.get(CrawlAttribute.SNAPSHOTS_REPLACEOLD.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_" + CrawlAttribute.SNAPSHOTS_MUSTNOTMATCH.key, this.get(CrawlAttribute.SNAPSHOTS_MUSTNOTMATCH.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_" + CrawlAttribute.SNAPSHOTS_LOADIMAGE.key, this.get(CrawlAttribute.SNAPSHOTS_LOADIMAGE.key));
        prop.put(CRAWL_PROFILE_PREFIX + count + "_remoteIndexing", this.remoteIndexing() ? 1 : 0);
        prop.put(CRAWL_PROFILE_PREFIX + count + "_indexText", this.indexText() ? 1 : 0);
        prop.put(CRAWL_PROFILE_PREFIX + count + "_indexMedia", this.indexMedia() ? 1 : 0);
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_" + CrawlAttribute.COLLECTIONS.key, this.get(CrawlAttribute.COLLECTIONS.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_" + CrawlAttribute.DEFAULT_VALENCY.key, this.get(CrawlAttribute.DEFAULT_VALENCY.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_" + CrawlAttribute.VALENCY_SWITCH_TAG_NAMES.key, this.get(CrawlAttribute.VALENCY_SWITCH_TAG_NAMES.key));
        prop.putXML(CRAWL_PROFILE_PREFIX + count + "_" + CrawlAttribute.TIMEZONEOFFSET.key, this.get(CrawlAttribute.TIMEZONEOFFSET.key));

        int i = 0;
        if (active && this.domMaxPages() > 0 && this.domMaxPages() != Integer.MAX_VALUE) {
            String item;
            while (i <= domlistlength && !(item = this.domName(true, i)).isEmpty()) {
                if (i == domlistlength) item += " ...";
                prop.putHTML(CRAWL_PROFILE_PREFIX + count + "_crawlingDomFilterContent_" + i + "_item", item);
                i++;
            }
        }
        prop.put(CRAWL_PROFILE_PREFIX+count+"_crawlingDomFilterContent", i);

    }

    public static void main(String[] args) {
        // test to convert the key set from set to string and back
        Set<String> a = new HashSet<>();
        a.add("eins"); a.add("zwei"); a.add("drei");
        JSONArray j = new JSONArray(a);
        String s = j.toString();
        System.out.println(s);
        JSONTokener o = new JSONTokener(s);
        try {
            j = new JSONArray(o);
            System.out.println(j);
            Set<String> h = new HashSet<String>();
            for (int i = 0; i < j.length(); i++) h.add(j.getString(i));
            System.out.println(h);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
