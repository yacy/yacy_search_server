// QueryParams.java
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created: 10.10.2005
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

package net.yacy.search.query;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.Classification;
import net.yacy.cora.document.Classification.ContentDomain;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.services.federated.yacy.CacheStrategy;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.document.Condenser;
import net.yacy.document.geolocation.GeoLocation;
import net.yacy.document.parser.html.AbstractScraper;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.util.SetTools;
import net.yacy.peers.Seed;
import net.yacy.search.index.Segment;
import net.yacy.search.index.YaCySchema;
import net.yacy.search.ranking.RankingProfile;

public final class QueryParams {

    public enum Searchdom {
        LOCAL, CLUSTER, GLOBAL;

        public static Searchdom contentdomParser(final String dom) {
            if ("local".equals(dom)) return LOCAL;
            else if ("global".equals(dom)) return GLOBAL;
            else if ("cluster".equals(dom)) return CLUSTER;
            return LOCAL;
        }

        @Override
        public String toString() {
            if (this == LOCAL) return "local";
            else if (this == CLUSTER) return "global"; // yes thats right: global, not cluster because a cluster search is a global search
            else if (this == GLOBAL) return "global";
            return "local";
        }
    }

    private static final String ampersand = "&amp;";

    public static enum FetchMode {
    	NO_FETCH_NO_VERIFY,
    	FETCH_BUT_ACCEPT_OFFLINE_OR_USE_CACHE,
    	FETCH_AND_VERIFY_ONLINE;
    }

    public static class Modifier {
        String s;
        public Modifier(final String modifier) {
            this.s = modifier;
        }
        public String getModifier() {
            return this.s;
        }
    }


    public static final Bitfield empty_constraint    = new Bitfield(4, "AAAAAA");
    public static final Pattern catchall_pattern = Pattern.compile(".*");
    public static final Pattern matchnothing_pattern = Pattern.compile("");

    public final String queryString;
    public final HandleSet query_include_hashes, query_exclude_hashes, query_all_hashes;
    public final Collection<String> query_include_words, query_exclude_words, query_all_words;
    public final int itemsPerPage;
    public int offset;
    public final Pattern urlMask, prefer;
    public final boolean urlMask_isCatchall, prefer_isMatchnothing;
    public final Classification.ContentDomain contentdom;
    public final String targetlang;
    public final Collection<Tagging.Metatag> metatags;
    public final String navigators;
    public final Searchdom domType;
    public final int zonecode;
    public final int domMaxTargets;
    public final int maxDistance;
    public final Bitfield constraint;
    public final boolean allofconstraint;
    public CacheStrategy snippetCacheStrategy;
    public final RankingProfile ranking;
    private final Segment indexSegment;
    public final String host; // this is the client host that starts the query, not a site operator
    public final String sitehash; // this is a domain hash, 6 bytes long or null
    public final Set<String> siteexcludes; // set of domain hashes that are excluded if not included by sitehash
    public final String authorhash;
    public final String tenant;
    public final Modifier modifier;
    public Seed remotepeer;
    public final long starttime, maxtime, timeout; // the time when the query started, how long it should take and the time when the timeout is reached (milliseconds)
    // values that are set after a search:
    public int resultcount; // number of found results
    public int transmitcount; // number of results that had been shown to the user
    public long searchtime, urlretrievaltime, snippetcomputationtime; // time to perform the search, to get all the urls, and to compute the snippets
    public boolean specialRights; // is true if the user has a special authorization and my use more database-extensive options
    public final String userAgent;
    public boolean filterfailurls;
    public double lat, lon, radius;

    public QueryParams(
            final String queryString,
            final int itemsPerPage,
            final Bitfield constraint,
            final Segment indexSegment,
            final RankingProfile ranking,
            final String userAgent) {
        byte[] queryHash;
    	if ((queryString.length() == 12) && (Base64Order.enhancedCoder.wellformed(queryHash = UTF8.getBytes(queryString)))) {
            this.queryString = null;
            this.query_include_words = null;
            this.query_exclude_words = null;
            this.query_all_words = null;
            this.query_include_hashes = new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
            this.query_exclude_hashes = new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
            this.query_all_hashes = new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
            try {
                this.query_include_hashes.put(queryHash);
                this.query_all_hashes.put(queryHash);
            } catch (final SpaceExceededException e) {
                Log.logException(e);
            }
    	} else {
    		this.queryString = queryString;
    		final Collection<String>[] cq = cleanQuery(queryString);
            this.query_include_words = cq[0];
            this.query_exclude_words = cq[1];
            this.query_all_words = cq[2];
    		this.query_include_hashes = Word.words2hashesHandles(cq[0]);
    		this.query_exclude_hashes = Word.words2hashesHandles(cq[1]);
    		this.query_all_hashes = Word.words2hashesHandles(cq[2]);
    	}
    	this.ranking = ranking;
    	this.tenant = null;
    	this.modifier = new Modifier("");
        this.maxDistance = Integer.MAX_VALUE;
        this.urlMask = catchall_pattern;
        this.urlMask_isCatchall = true;
        this.prefer = matchnothing_pattern;
        this.prefer_isMatchnothing = true;
        this.contentdom = ContentDomain.ALL;
        this.itemsPerPage = itemsPerPage;
        this.offset = 0;
        this.targetlang = "en";
        this.metatags = new ArrayList<Tagging.Metatag>(0);
        this.domType = Searchdom.LOCAL;
        this.zonecode = DigestURI.TLD_any_zone_filter;
        this.domMaxTargets = 0;
        this.constraint = constraint;
        this.allofconstraint = false;
        this.snippetCacheStrategy = null;
        this.host = null;
        this.sitehash = null;
        this.siteexcludes = null;
        this.authorhash = null;
        this.remotepeer = null;
        this.starttime = Long.valueOf(System.currentTimeMillis());
        this.maxtime = 10000;
        this.timeout = this.starttime + this.timeout;
        this.specialRights = false;
        this.navigators = "all";
        this.indexSegment = indexSegment;
        this.userAgent = userAgent;
        this.transmitcount = 0;
        this.filterfailurls = false;
        this.lat = 0.0d;
        this.lon = 0.0d;
        this.radius = 0.0d;
    }

    public QueryParams(
        final String queryString,
        final Collection<String> queryWords,
        final Collection<String> excludeWords,
        final Collection<String> fullqueryWords,
        final HandleSet queryHashes,
        final HandleSet excludeHashes,
        final HandleSet fullqueryHashes,
        final String tenant,
        final String modifier,
        final int maxDistance, final String prefer, final ContentDomain contentdom,
        final String language,
        final Collection<Tagging.Metatag> metatags,
        final String navigators,
        final CacheStrategy snippetCacheStrategy,
        final int itemsPerPage, final int offset, final String urlMask,
        final Searchdom domType, final int domMaxTargets,
        final Bitfield constraint, final boolean allofconstraint,
        final String site,
        final Set<String> siteexcludes,
        final String authorhash,
        final int domainzone,
        final String host,
        final boolean specialRights,
        final Segment indexSegment,
        final RankingProfile ranking,
        final String userAgent,
        final boolean filterfailurls,
        final double lat, final double lon, final double radius) {

        this.queryString = queryString;
        this.query_include_words = queryWords;
        this.query_exclude_words = excludeWords;
        this.query_all_words = fullqueryWords;
        this.query_include_hashes = queryHashes;
        this.query_exclude_hashes = excludeHashes;
        this.query_all_hashes = fullqueryHashes;
        this.tenant = (tenant != null && tenant.isEmpty()) ? null : tenant;
        this.modifier = new Modifier(modifier == null ? "" : modifier);
        this.ranking = ranking;
        this.maxDistance = maxDistance;
        this.contentdom = contentdom;
        this.itemsPerPage = Math.min((specialRights) ? 10000 : 1000, itemsPerPage);
        this.offset = Math.max(0, Math.min((specialRights) ? 10000 - this.itemsPerPage : 1000 - this.itemsPerPage, offset));
        try {
            this.urlMask = Pattern.compile(urlMask.toLowerCase());
        } catch (final PatternSyntaxException ex) {
            throw new IllegalArgumentException("Not a valid regular expression: " + urlMask, ex);
        }
        this.urlMask_isCatchall = this.urlMask.toString().equals(catchall_pattern.toString());
        try {
            this.prefer = Pattern.compile(prefer);
        } catch (final PatternSyntaxException ex) {
            throw new IllegalArgumentException("Not a valid regular expression: " + prefer, ex);
        }
        this.prefer_isMatchnothing = this.prefer.toString().equals(matchnothing_pattern.toString());
        assert language != null;
        this.targetlang = language;
        this.metatags = metatags;
        this.navigators = navigators;
        this.domType = domType;
        this.zonecode = domainzone;
        this.domMaxTargets = domMaxTargets;
        this.constraint = constraint;
        this.allofconstraint = allofconstraint;
        this.sitehash = site; assert site == null || site.length() == 6;
        this.siteexcludes = siteexcludes != null && siteexcludes.isEmpty() ? null: siteexcludes;
        this.authorhash = authorhash; assert authorhash == null || !authorhash.isEmpty();
        this.snippetCacheStrategy = snippetCacheStrategy;
        this.host = host;
        this.remotepeer = null;
        this.starttime = Long.valueOf(System.currentTimeMillis());
        this.maxtime = 10000;
        this.timeout = this.starttime + this.timeout;
        this.specialRights = specialRights;
        this.indexSegment = indexSegment;
        this.userAgent = userAgent;
        this.transmitcount = 0;
        this.filterfailurls = filterfailurls;
        // we normalize here the location and radius because that should cause a better caching
        // and as surplus it will increase privacy
        this.lat = Math.floor(lat * this.kmNormal) / this.kmNormal;
        this.lon = Math.floor(lon * this.kmNormal) / this.kmNormal;
        this.radius = Math.floor(radius * this.kmNormal + 1) / this.kmNormal;
    }

    double kmNormal = 100.d; // 100 =ca 40000.d / 360.d == 111.11 - if lat/lon is multiplied with this, rounded and diveded by this, the location is normalized to a 1km grid

    public Segment getSegment() {
        return this.indexSegment;
    }

    public int neededResults() {
        // the number of result lines that must be computed
        return this.offset + this.itemsPerPage;
    }

    public int itemsPerPage() {
        // the number of result lines that are displayed at once (size of result page)
        return this.itemsPerPage;
    }

    public void setOffset(final int newOffset) {
        this.offset = newOffset;
    }

    public boolean isLocal() {
        return this.domType == Searchdom.LOCAL;
    }

    public static HandleSet hashes2Set(final String query) {
        final HandleSet keyhashes = new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
        if (query != null) {
            for (int i = 0; i < (query.length() / Word.commonHashLength); i++) try {
                keyhashes.put(ASCII.getBytes(query.substring(i * Word.commonHashLength, (i + 1) * Word.commonHashLength)));
            } catch (final SpaceExceededException e) {
                Log.logException(e);
            }
        }
        return keyhashes;
    }

    public static HandleSet hashes2Handles(final String query) {
        final HandleSet keyhashes = new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
        if (query != null) {
            for (int i = 0; i < (query.length() / Word.commonHashLength); i++) try {
                keyhashes.put(ASCII.getBytes(query.substring(i * Word.commonHashLength, (i + 1) * Word.commonHashLength)));
            } catch (final SpaceExceededException e) {
                Log.logException(e);
            }
        }
        return keyhashes;
    }

    public static String hashSet2hashString(final HandleSet hashes) {
        final byte[] bb = new byte[hashes.size() * Word.commonHashLength];
        int p = 0;
        for (final byte[] b : hashes) {
            assert b.length == Word.commonHashLength : "hash = " + ASCII.String(b);
            System.arraycopy(b, 0, bb, p, Word.commonHashLength);
            p += Word.commonHashLength;
        }
        return ASCII.String(bb);
    }

    public static String hashSet2hashString(final Set<String> hashes) {
        final byte[] bb = new byte[hashes.size() * Word.commonHashLength];
        int p = 0;
        for (final String s : hashes) {
            assert s.length() == Word.commonHashLength : "hash = " + s;
            System.arraycopy(ASCII.getBytes(s), 0, bb, p, Word.commonHashLength);
            p += Word.commonHashLength;
        }
        return ASCII.String(bb);
    }

    public static String anonymizedQueryHashes(final HandleSet hashes) {
        // create a more anonymized representation of a query hashes for logging
        final Iterator<byte[]> i = hashes.iterator();
        final StringBuilder sb = new StringBuilder(hashes.size() * (Word.commonHashLength + 2) + 2);
        sb.append("[");
        byte[] hash;
        if (i.hasNext()) {
            hash = i.next();
            sb.append(ASCII.String(hash).substring(0, 3)).append(".........");
        }
        while (i.hasNext()) {
            hash = i.next();
            sb.append(", ").append(ASCII.String(hash).substring(0, 3)).append(".........");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * check if the given text matches with the query
     * this checks inclusion and exclusion words
     * @param text
     * @return true if the query matches with the given text
     */
    public final boolean matchesText(final String text) {
        boolean ret = false;
        final HandleSet wordhashes = Word.words2hashesHandles(Condenser.getWords(text, null).keySet());
        if (!SetTools.anymatch(wordhashes, this.query_exclude_hashes)) {
            ret = SetTools.totalInclusion(this.query_include_hashes, wordhashes);
        }
        return ret;
    }

    public static final boolean anymatch(final String text, final HandleSet keyhashes) {
    	// returns true if any of the word hashes in keyhashes appear in the String text
    	// to do this, all words in the string must be recognized and transcoded to word hashes
        if (keyhashes == null || keyhashes.isEmpty()) return false;
    	final HandleSet wordhashes = Word.words2hashesHandles(Condenser.getWords(text, null).keySet());
    	return SetTools.anymatch(wordhashes, keyhashes);
    }

    private static String seps = "'.,/&_"; static {seps += '"';}

    @SuppressWarnings("unchecked")
	public static Collection<String>[] cleanQuery(String querystring) {
        // returns three sets: a query set, an exclude set and a full query set
        final Collection<String> query_include_words = new ArrayList<String>();
        final Collection<String> query_exclude_words = new ArrayList<String>();
        final Collection<String> query_all_words = new ArrayList<String>();

        if ((querystring != null) && (!querystring.isEmpty())) {

            // convert Umlaute
            querystring = AbstractScraper.stripAll(querystring.toCharArray()).toLowerCase().trim();
            int c;
            for (int i = 0; i < seps.length(); i++) {
                while ((c = querystring.indexOf(seps.charAt(i))) >= 0) {
                    querystring = querystring.substring(0, c) + (((c + 1) < querystring.length()) ? (" " + querystring.substring(c + 1)) : "");
                }
            }

            String s;
            int l;
            // the string is clean now, but we must generate a set out of it
            final String[] queries = querystring.split(" ");
            for (String quer : queries) {
                if (quer.startsWith("-")) {
                    String x = quer.substring(1);
                    if (!query_exclude_words.contains(x)) query_exclude_words.add(x);
                } else {
                    while ((c = quer.indexOf('-')) >= 0) {
                        s = quer.substring(0, c);
                        l = s.length();
                        if (l >= Condenser.wordminsize && !query_include_words.contains(s)) {query_include_words.add(s);}
                        if (l > 0 && !query_all_words.contains(s)) {query_all_words.add(s);}
                        quer = quer.substring(c + 1);
                    }
                    l = quer.length();
                    if (l >= Condenser.wordminsize && !query_include_words.contains(quer)) {query_include_words.add(quer);}
                    if (l > 0 && !query_all_words.contains(quer)) {query_all_words.add(quer);}
                }
            }
        }
        return new Collection[]{query_include_words, query_exclude_words, query_all_words};
    }

    public String queryString(final boolean encodeHTML) {
        final String ret;
        if (encodeHTML){
            ret = CharacterCoding.unicode2html(this.queryString, true);
        } else {
            ret = this.queryString;
        }
        return ret;
    }

    final static String[] fields = new String[]{"sku","title","h1_txt","h2_txt","author","description","keywords","text_t"};

    public String solrQueryString(boolean urlencoded) {
        if (this.query_include_words == null || this.query_include_words.size() == 0) return null;
        final StringBuilder q = new StringBuilder(80);

        // add text query
        int wc = 0;
        StringBuilder w = new StringBuilder(80);
        for (String s: this.query_include_words) {
            if (wc > 0) w.append(urlencoded ? "+AND+" : " AND ");
            w.append(s);
            wc++;
        }
        for (String s: this.query_exclude_words){
            if (wc > 0) w.append(urlencoded ? "+AND+-" : " AND -");
            w.append(s);
            wc++;
        }
        wc = 0;
        for (String a: fields) {
            if (wc > 0) q.append(urlencoded ? "+OR+" : " OR ");
            q.append('(').append(a).append(':').append(w).append(')');
            wc++;
        }

        // add filter to prevent that results come from failed urls
        q.append(urlencoded ? "+-failreason_t:[*+TO+*]" : " -failreason_t:[* TO *]");

        // add constraints
        if ( this.sitehash == null ) {
            if (this.siteexcludes != null) {
                for (String ex: this.siteexcludes) {
                    q.append(urlencoded ? "+-host_id_s:" : " -host_id_s:").append(ex);
                }
            }
        } else {
            q.append(urlencoded ? "+host_id_s:" : " host_id_s:").append(this.sitehash);
        }

        if (this.radius > 0.0d && this.lat != 0.0d && this.lon != 0.0d) {
            // localtion search, no special ranking
            q.append("&fq={!bbox sfield=").append(YaCySchema.coordinate_p.name()).append("}&pt=");
            q.append(Double.toString(this.lat)).append(',').append(Double.toString(this.lon)).append("&d=").append(GeoLocation.degreeToKm(this.radius));
        } else {
            // boost fields
            q.append("&defType=edismax&qf=sku^20.0,title^15.0,h1_txt^11.0,h2_txt^10.0,author^8.0,description^5.0,keywords^2.0,text_t^1.0");
        }

        // prepare result
        return (urlencoded) ? CharacterCoding.unicode2html(q.toString(), true) : q.toString();
    }

    public String queryStringForUrl() {
    	try {
            return URLEncoder.encode(this.queryString, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            Log.logException(e);
            return this.queryString;
        }
    }

    public Collection<String>[] queryWords() {
        return cleanQuery(this.queryString);
    }

    public void filterOut(final SortedSet<String> blueList) {
        // filter out words that appear in this set
    	// this is applied to the queryHashes
    	final HandleSet blues = Word.words2hashesHandles(blueList);
    	for (final byte[] b: blues) this.query_include_hashes.remove(b);
    }


    public final Map<MultiProtocolURI, String> separateMatches(final Map<MultiProtocolURI, String> links) {
        final Map<MultiProtocolURI, String> matcher = new HashMap<MultiProtocolURI, String>();
        final Iterator <Map.Entry<MultiProtocolURI, String>> i = links.entrySet().iterator();
        Map.Entry<MultiProtocolURI, String> entry;
        MultiProtocolURI url;
        String anchorText;
        while (i.hasNext()) {
            entry = i.next();
            url = entry.getKey();
            anchorText = entry.getValue();
            if (matchesText(anchorText)) {
                matcher.put(url, anchorText);
                i.remove();
            }
        }
        return matcher;
    }

    private volatile String idCacheAnon = null, idCache = null;
    final static private char asterisk = '*';
    public String id(final boolean anonymized) {
        if (anonymized) {
            if (this.idCacheAnon != null) return this.idCacheAnon;
        } else {
            if (this.idCache != null) return this.idCache;
        }
        synchronized (this) {
            // do a Double-Checked Locking
            if (anonymized) {
                if (this.idCacheAnon != null) return this.idCacheAnon;
            } else {
                if (this.idCache != null) return this.idCache;
            }
            // generate a string that identifies a search so results can be re-used in a cache
            final StringBuilder context = new StringBuilder(180);
            if (anonymized) {
                context.append(anonymizedQueryHashes(this.query_include_hashes));
                context.append('-');
                context.append(anonymizedQueryHashes(this.query_exclude_hashes));
            } else {
                context.append(hashSet2hashString(this.query_include_hashes));
                context.append('-');
                context.append(hashSet2hashString(this.query_exclude_hashes));
            }
            //context.append(asterisk);
            //context.append(this.domType);
            context.append(asterisk);
            context.append(this.contentdom).append(asterisk);
            context.append(this.zonecode).append(asterisk);
            context.append(ASCII.String(Word.word2hash(this.ranking.toExternalString()))).append(asterisk);
            context.append(Base64Order.enhancedCoder.encodeString(this.prefer.toString())).append(asterisk);
            context.append(Base64Order.enhancedCoder.encodeString(this.urlMask.toString())).append(asterisk);
            context.append(this.sitehash).append(asterisk);
            context.append(this.siteexcludes).append(asterisk);
            context.append(this.authorhash).append(asterisk);
            context.append(this.targetlang).append(asterisk);
            context.append(this.constraint).append(asterisk);
            context.append(this.maxDistance).append(asterisk);
            context.append(this.modifier.s).append(asterisk);
            context.append(this.lat).append(asterisk).append(this.lon).append(asterisk).append(this.radius).append(asterisk);
            context.append(this.snippetCacheStrategy == null ? "null" : this.snippetCacheStrategy.name());
            String result = context.toString();
            if (anonymized) {
                this.idCacheAnon = result;
            } else {
                this.idCache = result;
            }
            return result;
        }
    }

    /**
     * make a query anchor tag
     * @param page
     * @param theQuery
     * @param originalUrlMask
     * @param addToQuery
     * @return
     */
    public static StringBuilder navurl(
            final String ext, final int page, final QueryParams theQuery,
            final String newQueryString, final String originalUrlMask, final String nav) {

        final StringBuilder sb = navurlBase(ext, theQuery, newQueryString, originalUrlMask, nav);

        sb.append(ampersand);
        sb.append("startRecord=");
        sb.append(page * theQuery.itemsPerPage());

        return sb;
    }

    public static StringBuilder navurlBase(
                    final String ext, final QueryParams theQuery,
                    final String newQueryString, final String originalUrlMask, final String nav) {

        final StringBuilder sb = new StringBuilder(120);
        sb.append("/yacysearch.");
        sb.append(ext);
        sb.append("?query=");
        sb.append(newQueryString == null ? theQuery.queryStringForUrl() : newQueryString);

        sb.append(ampersand);
        sb.append("maximumRecords=");
        sb.append(theQuery.itemsPerPage());

        sb.append(ampersand);
        sb.append("resource=");
        sb.append((theQuery.isLocal()) ? "local" : "global");

        sb.append(ampersand);
        sb.append("verify=");
        sb.append(theQuery.snippetCacheStrategy == null ? "false" : theQuery.snippetCacheStrategy.toName());

        sb.append(ampersand);
        sb.append("nav=");
        sb.append(nav);

        sb.append(ampersand);
        sb.append("urlmaskfilter=");
        sb.append(originalUrlMask);

        sb.append(ampersand);
        sb.append("prefermaskfilter=");
        sb.append(theQuery.prefer);

        sb.append(ampersand);
        sb.append("cat=href");

        sb.append(ampersand);
        sb.append("constraint=");
        sb.append((theQuery.constraint == null) ? "" : theQuery.constraint.exportB64());

        sb.append(ampersand);
        sb.append("contentdom=");
        sb.append(theQuery.contentdom.toString());

        sb.append(ampersand);
        sb.append("former=");
        sb.append(theQuery.queryStringForUrl());

        return sb;
    }

}
