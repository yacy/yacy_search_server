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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.federate.solr.Boost;
import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.geo.GeoLocation;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.document.Condenser;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.kelondro.util.SetTools;
import net.yacy.peers.Seed;
import net.yacy.search.index.Segment;
import net.yacy.search.ranking.RankingProfile;

public final class QueryParams {

    public enum Searchdom {
        LOCAL, CLUSTER, GLOBAL;

        @Override
        public String toString() {
            if (this == LOCAL) return "local";
            else if (this == CLUSTER) return "global"; // yes thats right: global, not cluster because a cluster search is a global search
            else if (this == GLOBAL) return "global";
            return "local";
        }
    }

    private static final String[] defaultfacetfields = new String[]{
        YaCySchema.host_s.getSolrFieldName(),
        YaCySchema.url_protocol_s.getSolrFieldName(),
        YaCySchema.url_file_ext_s.getSolrFieldName(),
        YaCySchema.author.getSolrFieldName()};
    
    private static final int defaultmaxfacets = 30;
    
    private static final String ampersand = "&amp;";

    public static class Modifier {
        private String s;
        private Modifier(final String modifier) {
            this.s = modifier;
        }
        public String getModifier() {
            return this.s;
        }
    }


    public static final Bitfield empty_constraint    = new Bitfield(4, "AAAAAA");
    public static final Pattern catchall_pattern = Pattern.compile(".*");
    private static final Pattern matchnothing_pattern = Pattern.compile("");

    public final QueryGoal queryGoal;
    public int itemsPerPage;
    public int offset;
    public final Pattern urlMask, prefer;
    final boolean urlMask_isCatchall;
    public final Classification.ContentDomain contentdom;
    public final String targetlang;
    protected final Collection<Tagging.Metatag> metatags;
    public final Searchdom domType;
    private final int zonecode;
    public final int maxDistance;
    public final Bitfield constraint;
    final boolean allofconstraint;
    protected CacheStrategy snippetCacheStrategy;
    public final RankingProfile ranking;
    private final Segment indexSegment;
    public final String clienthost; // this is the client host that starts the query, not a site operator
    public final String nav_sitehost; // this is a domain name which is used to navigate to that host
    public final String nav_sitehash; // this is a domain hash, 6 bytes long or null
    protected final Set<String> siteexcludes; // set of domain hashes that are excluded if not included by sitehash
    public final String authorhash;
    public final Modifier modifier;
    public Seed remotepeer;
    public final long starttime; // the time when the query started, how long it should take and the time when the timeout is reached (milliseconds)
    protected final long maxtime;
    protected final long timeout;
    // values that are set after a search:
    public int transmitcount; // number of results that had been shown to the user
    public long searchtime, urlretrievaltime, snippetcomputationtime; // time to perform the search, to get all the urls, and to compute the snippets
    public final String userAgent;
    protected boolean filterfailurls;
    protected double lat, lon, radius;
    public String[] facetfields;
    public int maxfacets;
    
    // the following values are filled during the search process as statistics for the search
    public final AtomicInteger local_rwi_available;  // the number of hits generated/ranked by the local search in rwi index
    public final AtomicInteger local_rwi_stored;     // the number of existing hits by the local search in rwi index
    public final AtomicInteger local_solr_available; // the number of hits generated/ranked by the local search in solr
    public final AtomicInteger local_solr_stored;    // the number of existing hits by the local search in solr
    public final AtomicInteger remote_available;     // the number of hits imported from remote peers (rwi/solr mixed)
    public final AtomicInteger remote_stored;        // the number of existing hits at remote site
    public final AtomicInteger remote_peerCount;     // the number of peers which contributed to the remote search result 
    public final SortedSet<byte[]> misses; // url hashes that had been sorted out because of constraints in postranking

    public QueryParams(
            final String queryString,
            final int itemsPerPage,
            final Bitfield constraint,
            final Segment indexSegment,
            final RankingProfile ranking,
            final String userAgent) {
        this.queryGoal = new QueryGoal(queryString);
    	this.ranking = ranking;
    	this.modifier = new Modifier("");
        this.maxDistance = Integer.MAX_VALUE;
        this.urlMask = catchall_pattern;
        this.urlMask_isCatchall = true;
        this.prefer = matchnothing_pattern;
        this.contentdom = ContentDomain.ALL;
        this.itemsPerPage = itemsPerPage;
        this.offset = 0;
        this.targetlang = "en";
        this.metatags = new ArrayList<Tagging.Metatag>(0);
        this.domType = Searchdom.LOCAL;
        this.zonecode = DigestURI.TLD_any_zone_filter;
        this.constraint = constraint;
        this.allofconstraint = false;
        this.snippetCacheStrategy = null;
        this.clienthost = null;
        this.nav_sitehash = null;
        this.nav_sitehost = null;
        this.siteexcludes = null;
        this.authorhash = null;
        this.remotepeer = null;
        this.starttime = Long.valueOf(System.currentTimeMillis());
        this.maxtime = 10000;
        this.timeout = this.starttime + this.timeout;
        this.indexSegment = indexSegment;
        this.userAgent = userAgent;
        this.transmitcount = 0;
        this.filterfailurls = false;
        this.lat = 0.0d;
        this.lon = 0.0d;
        this.radius = 0.0d;
        this.local_rwi_available = new AtomicInteger(0); // the number of results in the local peer after filtering
        this.local_rwi_stored    = new AtomicInteger(0);
        this.local_solr_available= new AtomicInteger(0);
        this.local_solr_stored   = new AtomicInteger(0);
        this.remote_stored       = new AtomicInteger(0);
        this.remote_available    = new AtomicInteger(0); // the number of result contributions from all the remote peers
        this.remote_peerCount    = new AtomicInteger(0); // the number of remote peers that have contributed
        this.misses = Collections.synchronizedSortedSet(new TreeSet<byte[]>(URIMetadataRow.rowdef.objectOrder));
        this.facetfields = defaultfacetfields;
        this.maxfacets = defaultmaxfacets;
    }

    public QueryParams(
        final QueryGoal queryGoal,
        final String modifier,
        final int maxDistance, final String prefer, final ContentDomain contentdom,
        final String language,
        final Collection<Tagging.Metatag> metatags,
        final CacheStrategy snippetCacheStrategy,
        final int itemsPerPage, final int offset, final String urlMask,
        final Searchdom domType, final int domMaxTargets,
        final Bitfield constraint, final boolean allofconstraint,
        final String nav_sitehash,
        final String nav_sitehost,
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
        this.queryGoal = queryGoal;
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
        this.prefer.toString().equals(matchnothing_pattern.toString());
        assert language != null;
        this.targetlang = language;
        this.metatags = metatags;
        this.domType = domType;
        this.zonecode = domainzone;
        this.constraint = constraint;
        this.allofconstraint = allofconstraint;
        this.nav_sitehash = nav_sitehash; assert nav_sitehash == null || nav_sitehash.length() == 6;
        this.nav_sitehost = nav_sitehost;
        this.siteexcludes = siteexcludes != null && siteexcludes.isEmpty() ? null: siteexcludes;
        this.authorhash = authorhash; assert authorhash == null || !authorhash.isEmpty();
        this.snippetCacheStrategy = snippetCacheStrategy;
        this.clienthost = host;
        this.remotepeer = null;
        this.starttime = Long.valueOf(System.currentTimeMillis());
        this.maxtime = 10000;
        this.timeout = this.starttime + this.timeout;
        this.indexSegment = indexSegment;
        this.userAgent = userAgent;
        this.transmitcount = 0;
        this.filterfailurls = filterfailurls;
        // we normalize here the location and radius because that should cause a better caching
        // and as surplus it will increase privacy
        this.lat = Math.floor(lat * this.kmNormal) / this.kmNormal;
        this.lon = Math.floor(lon * this.kmNormal) / this.kmNormal;
        this.radius = Math.floor(radius * this.kmNormal + 1) / this.kmNormal;
        this.local_rwi_available = new AtomicInteger(0); // the number of results in the local peer after filtering
        this.local_rwi_stored    = new AtomicInteger(0);
        this.local_solr_available= new AtomicInteger(0);
        this.local_solr_stored   = new AtomicInteger(0);
        this.remote_stored       = new AtomicInteger(0);
        this.remote_available    = new AtomicInteger(0); // the number of result contributions from all the remote peers
        this.remote_peerCount    = new AtomicInteger(0); // the number of remote peers that have contributed
        this.misses = Collections.synchronizedSortedSet(new TreeSet<byte[]>(URIMetadataRow.rowdef.objectOrder));
        this.facetfields = defaultfacetfields;
        this.maxfacets = defaultmaxfacets;
    }

    private double kmNormal = 100.d; // 100 =ca 40000.d / 360.d == 111.11 - if lat/lon is multiplied with this, rounded and diveded by this, the location is normalized to a 1km grid

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

    public int getResultCount() {
        return this.local_rwi_available.get() + this.local_solr_stored.get() - this.misses.size();
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
    private final boolean matchesText(final String text) {
        boolean ret = false;
        final HandleSet wordhashes = Word.words2hashesHandles(Condenser.getWords(text, null).keySet());
        if (!SetTools.anymatch(wordhashes, this.queryGoal.getExcludeHashes())) {
            ret = SetTools.totalInclusion(this.queryGoal.getIncludeHashes(), wordhashes);
        }
        return ret;
    }

    protected static final boolean anymatch(final String text, final HandleSet keyhashes) {
    	// returns true if any of the word hashes in keyhashes appear in the String text
    	// to do this, all words in the string must be recognized and transcoded to word hashes
        if (keyhashes == null || keyhashes.isEmpty()) return false;
    	final HandleSet wordhashes = Word.words2hashesHandles(Condenser.getWords(text, null).keySet());
    	return SetTools.anymatch(wordhashes, keyhashes);
    }

    public String queryString(final boolean encodeHTML) {
        final String ret;
        if (encodeHTML){
            ret = CharacterCoding.unicode2html(this.queryGoal.getQueryString(), true);
        } else {
            ret = this.queryGoal.getQueryString();
        }
        return ret;
    }


    public SolrQuery solrQuery() {
        if (this.queryGoal.getIncludeStrings().size() == 0) return null;
        // get text query
        final StringBuilder q = this.queryGoal.solrQueryString(this.indexSegment.fulltext().getSolrScheme());

        // add constraints
        if (this.nav_sitehash == null && this.nav_sitehost == null) {
            if (this.siteexcludes != null) {
                for (String ex: this.siteexcludes) {
                    q.append(" -").append(YaCySchema.host_id_s.getSolrFieldName()).append(':').append(ex);
                }
            }
        } else {
            if (this.nav_sitehost != null)
                q.append(" AND ").append(YaCySchema.host_s.getSolrFieldName()).append(":\"").append(this.nav_sitehost).append('\"');
            else
                q.append(" AND ").append(YaCySchema.host_id_s.getSolrFieldName()).append(":\"").append(this.nav_sitehash).append('\"');
        }

        // construct query
        final SolrQuery params = new SolrQuery();
        params.setParam("defType", "edismax");
        float f = Boost.RANKING.get(YaCySchema.fuzzy_signature_unique_b);
        params.setParam("bq", YaCySchema.fuzzy_signature_unique_b.getSolrFieldName() + ":true^" + Float.toString(f)); // a boost query that moves double content to the back
        params.setStart(this.offset);
        params.setRows(this.itemsPerPage);
        params.setFacet(false);
        
        if (!this.urlMask_isCatchall) {
            String urlMaskPattern = this.urlMask.pattern();
            
            // translate filetype navigation
            int extm = urlMaskPattern.indexOf(".*\\.");
            if (extm >= 0) {
                String ext = urlMaskPattern.substring(extm + 4);
                q.append(" AND ").append(YaCySchema.url_file_ext_s.getSolrFieldName()).append(':').append(ext);
            }
            
            // translate protocol navigation
            if (urlMaskPattern.startsWith("http://.*")) q.append(" AND ").append(YaCySchema.url_protocol_s.getSolrFieldName()).append(':').append("http");
            else if (urlMaskPattern.startsWith("https://.*")) q.append(" AND ").append(YaCySchema.url_protocol_s.getSolrFieldName()).append(':').append("https");
            else if (urlMaskPattern.startsWith("ftp://.*")) q.append(" AND ").append(YaCySchema.url_protocol_s.getSolrFieldName()).append(':').append("ftp");
            else if (urlMaskPattern.startsWith("smb://.*")) q.append(" AND ").append(YaCySchema.url_protocol_s.getSolrFieldName()).append(':').append("smb");
            else if (urlMaskPattern.startsWith("file://.*")) q.append(" AND ").append(YaCySchema.url_protocol_s.getSolrFieldName()).append(':').append("file");

            // add a filter query on urls
            // solr doesn't like slashes, backslashes or doublepoints; remove them
            int p;
            while ((p = urlMaskPattern.indexOf("\\")) >= 0) urlMaskPattern = urlMaskPattern.substring(0, p) + "." + urlMaskPattern.substring(p + 2);
            while ((p = urlMaskPattern.indexOf(':')) >= 0) urlMaskPattern = urlMaskPattern.substring(0, p) + "." + urlMaskPattern.substring(p + 1);
            while ((p = urlMaskPattern.indexOf('/')) >= 0) urlMaskPattern = urlMaskPattern.substring(0, p) + "." + urlMaskPattern.substring(p + 1);
            params.setFilterQueries(YaCySchema.sku.getSolrFieldName() + ":/" + urlMaskPattern + "/");
        }

        params.setQuery(q.toString());
        
        if (this.radius > 0.0d && this.lat != 0.0d && this.lon != 0.0d) {
            // localtion search, no special ranking
            // try http://localhost:8090/solr/select?q=*:*&fq={!bbox sfield=coordinate_p pt=50.17,8.65 d=1}

            //params.setQuery("!bbox " + q.toString());
            //params.set("sfield", YaCySchema.coordinate_p.name());
            //params.set("pt", Double.toString(this.lat) + "," + Double.toString(this.lon));
            //params.set("d", GeoLocation.degreeToKm(this.radius));
            params.setFilterQueries("{!bbox sfield=" + YaCySchema.coordinate_p.getSolrFieldName() + " pt=" + Double.toString(this.lat) + "," + Double.toString(this.lon) + " d=" + GeoLocation.degreeToKm(this.radius) + "}");
            //params.setRows(Integer.MAX_VALUE);
        } else {
            // set ranking
            if (this.ranking.coeff_date == RankingProfile.COEFF_MAX) {
                // set a most-recent ordering
                params.setSortField(YaCySchema.last_modified.getSolrFieldName(), ORDER.desc);
            }
        }
        
        // prepare result
        Log.logInfo("Protocol", "SOLR QUERY: " + params.toString());
        return params;
    }

    public QueryGoal getQueryGoal() {
        return this.queryGoal;
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
                context.append(anonymizedQueryHashes(this.queryGoal.getIncludeHashes()));
                context.append('-');
                context.append(anonymizedQueryHashes(this.queryGoal.getExcludeHashes()));
            } else {
                context.append(hashSet2hashString(this.queryGoal.getIncludeHashes()));
                context.append('-');
                context.append(hashSet2hashString(this.queryGoal.getExcludeHashes()));
            }
            //context.append(asterisk);
            //context.append(this.domType);
            context.append(asterisk);
            context.append(this.contentdom).append(asterisk);
            context.append(this.zonecode).append(asterisk);
            context.append(ASCII.String(Word.word2hash(this.ranking.toExternalString()))).append(asterisk);
            context.append(Base64Order.enhancedCoder.encodeString(this.prefer.toString())).append(asterisk);
            context.append(Base64Order.enhancedCoder.encodeString(this.urlMask.toString())).append(asterisk);
            context.append(this.nav_sitehash).append(asterisk);
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
    public static StringBuilder navurl(final String ext, final int page, final QueryParams theQuery, final String newQueryString) {

        final StringBuilder sb = navurlBase(ext, theQuery, newQueryString);

        sb.append(ampersand);
        sb.append("startRecord=");
        sb.append(page * theQuery.itemsPerPage());

        return sb;
    }

    public static StringBuilder navurlBase(final String ext, final QueryParams theQuery, final String newQueryString) {

        final StringBuilder sb = new StringBuilder(120);
        sb.append("/yacysearch.");
        sb.append(ext);
        sb.append("?query=");
        sb.append(newQueryString == null ? theQuery.getQueryGoal().queryStringForUrl() : newQueryString);

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
        sb.append(theQuery.getQueryGoal().queryStringForUrl());

        return sb;
    }

}
