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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.apache.solr.common.params.FacetParams;

import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.geo.GeoLocation;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.document.Condenser;
import net.yacy.document.LibraryProvider;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.kelondro.util.SetTools;
import net.yacy.peers.Seed;
import net.yacy.search.index.Segment;
import net.yacy.search.ranking.RankingProfile;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;

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

    private static final Map<String, CollectionSchema> defaultfacetfields = new HashMap<String, CollectionSchema>();
    static {
        // the key shall match with configuration property search.navigation
        defaultfacetfields.put("location", CollectionSchema.coordinate_p);
        defaultfacetfields.put("hosts", CollectionSchema.host_s);
        defaultfacetfields.put("protocol", CollectionSchema.url_protocol_s);
        defaultfacetfields.put("filetype", CollectionSchema.url_file_ext_s);
        defaultfacetfields.put("authors", CollectionSchema.author_sxt);
        defaultfacetfields.put("language", CollectionSchema.language_s);
        //missing: namespace
    }
    
    private static final int defaultmaxfacets = 30;
    public static final Bitfield empty_constraint    = new Bitfield(4, "AAAAAA");
    public static final Pattern catchall_pattern = Pattern.compile(".*");
    private static final Pattern matchnothing_pattern = Pattern.compile("");

    private final QueryGoal queryGoal;
    public int itemsPerPage;
    public int offset;
    public Pattern urlMask;

    public final Pattern prefer;
    public final String tld, inlink;
    boolean urlMask_isCatchall;
    public final Classification.ContentDomain contentdom;
    public final String targetlang;
    protected final Collection<Tagging.Metatag> metatags;
    public final Searchdom domType;
    private final int zonecode;
    public final int maxDistance;
    public final Bitfield constraint;
    public final boolean allofconstraint;
    protected CacheStrategy snippetCacheStrategy;
    public final RankingProfile ranking;
    private final Segment indexSegment;
    public final String clienthost; // this is the client host that starts the query, not a site operator
    protected final Set<String> siteexcludes; // set of domain hashes that are excluded if not included by sitehash
    public final QueryModifier modifier;
    public Seed remotepeer;
    public final long starttime; // the time when the query started, how long it should take and the time when the timeout is reached (milliseconds)
    protected final long maxtime;
    private final long timeout;
    // values that are set after a search:
    public int transmitcount; // number of results that had been shown to the user
    public long searchtime, urlretrievaltime, snippetcomputationtime; // time to perform the search, to get all the urls, and to compute the snippets
    public final String userAgent;
    protected boolean filterfailurls, filterscannerfail;
    protected double lat, lon, radius;
    public LinkedHashSet<String> facetfields;
    public int maxfacets;
    private SolrQuery cachedQuery;
    private CollectionConfiguration solrSchema;

    public QueryParams(
        final QueryGoal queryGoal,
        final QueryModifier modifier,
        final int maxDistance,
        final String prefer,
        final ContentDomain contentdom,
        final String language,
        final Collection<Tagging.Metatag> metatags,
        final CacheStrategy snippetCacheStrategy,
        final int itemsPerPage,
        final int offset,
        final String urlMask,
        final String tld,
        final String inlink,
        final Searchdom domType,
        final Bitfield constraint,
        final boolean allofconstraint,
        final Set<String> siteexcludes,
        final int domainzone,
        final String host,
        final boolean specialRights,
        final Segment indexSegment,
        final RankingProfile ranking,
        final String userAgent,
        final boolean filterfailurls,
        final boolean filterscannerfail,
        final double lat,
        final double lon,
        final double radius,
        final String[] search_navigation
        ) {
        this.queryGoal = queryGoal;
        this.modifier = modifier;
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
        if (this.urlMask_isCatchall) {
            String protocolfilter = modifier.protocol == null ? ".*" : modifier.protocol;
            String defaulthostprefix = modifier.protocol == null ? "www" : modifier.protocol;
            String hostfilter = modifier.sitehost == null && tld == null ? ".*" : modifier.sitehost == null ? ".*\\." + tld : modifier.sitehost.startsWith(defaulthostprefix + ".") ? "(" + defaulthostprefix + "\\.)?" + modifier.sitehost.substring(4) : "(" + defaulthostprefix + "\\.)?" + modifier.sitehost;
            String filefilter = modifier.filetype == null ? ".*" : ".*" + modifier.filetype + ".*";
            String filter = protocolfilter + "://" + hostfilter + "/" + filefilter;
            if (!filter.equals(".*://.*/.*")) {
                this.urlMask = Pattern.compile(filter);
                this.urlMask_isCatchall = false;
            }
        }
        this.tld = tld;
        this.inlink = inlink;
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
        this.siteexcludes = siteexcludes != null && siteexcludes.isEmpty() ? null: siteexcludes;
        this.snippetCacheStrategy = contentdom == ContentDomain.TEXT ? snippetCacheStrategy : contentdom == null ? null : CacheStrategy.CACHEONLY;
        this.clienthost = host;
        this.remotepeer = null;
        this.starttime = Long.valueOf(System.currentTimeMillis());
        this.maxtime = 10000;
        this.timeout = this.starttime + this.timeout;
        this.indexSegment = indexSegment;
        this.userAgent = userAgent;
        this.transmitcount = 0;
        this.filterfailurls = filterfailurls;
        this.filterscannerfail = filterscannerfail;
        // we normalize here the location and radius because that should cause a better caching
        // and as surplus it will increase privacy
        this.lat = Math.floor(lat * this.kmNormal) / this.kmNormal;
        this.lon = Math.floor(lon * this.kmNormal) / this.kmNormal;
        this.radius = Math.floor(radius * this.kmNormal + 1) / this.kmNormal;
        this.facetfields = new LinkedHashSet<String>();
        
        this.solrSchema = indexSegment.fulltext().getDefaultConfiguration();
        for (String navkey: search_navigation) {
            CollectionSchema f = defaultfacetfields.get(navkey);
            if (f != null && solrSchema.contains(f)) facetfields.add(f.getSolrFieldName());
        }
        for (Tagging v: LibraryProvider.autotagging.getVocabularies()) this.facetfields.add(CollectionSchema.VOCABULARY_PREFIX + v.getName() + CollectionSchema.VOCABULARY_SUFFIX);
        this.maxfacets = defaultmaxfacets;
        this.cachedQuery = null;
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
                ConcurrentLog.logException(e);
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
        QueryGoal.NormalizedWords words = new QueryGoal.NormalizedWords(Condenser.getWords(text, null).keySet());
        if (!SetTools.anymatchByTest(this.queryGoal.getExcludeWords(), words)) {
            ret = SetTools.totalInclusion(this.queryGoal.getIncludeWords(), words);
        }
        return ret;
    }
    
    protected static final boolean anymatch(final String text, final Iterator<String> keywords) {
        if (keywords == null || !keywords.hasNext()) return false;
        final SortedSet<String> textwords = (SortedSet<String>) Condenser.getWords(text, null).keySet();
        return SetTools.anymatchByTest(keywords, textwords);
    }

    public SolrQuery solrQuery(final ContentDomain cd, final boolean getFacets, final boolean excludeintext_image) {
        if (cd == ContentDomain.IMAGE) return solrImageQuery(getFacets);
        return solrTextQuery(getFacets, excludeintext_image);
    }
    
    private SolrQuery solrTextQuery(final boolean getFacets, final boolean excludeintext_image) {
        if (this.cachedQuery != null) {
            this.cachedQuery.setStart(this.offset);
            if (!getFacets) this.cachedQuery.setFacet(false);
            return this.cachedQuery;
        }
        
        // construct query
        final SolrQuery params = getBasicParams(getFacets);
        int rankingProfile = this.ranking.coeff_date == RankingProfile.COEFF_MAX ? 1 : (this.modifier.sitehash != null || this.modifier.sitehost != null) ? 2 : 0;
        params.setQuery(this.queryGoal.collectionTextQueryString(this.indexSegment.fulltext().getDefaultConfiguration(), rankingProfile, excludeintext_image).toString());
        Ranking ranking = indexSegment.fulltext().getDefaultConfiguration().getRanking(rankingProfile); // for a by-date ranking select different ranking profile
        
        String bq = ranking.getBoostQuery();
        String bf = ranking.getBoostFunction();
        if (this.queryGoal.getIncludeSize() > 1) {
            // add boost on combined words
            if (bq.length() > 0) bq += " ";
            bq += CollectionSchema.text_t.getSolrFieldName() + ":\"" + this.queryGoal.getIncludeString() + "\"^10";
        }
        if (bq.length() > 0) params.setParam("bq", bq);
        if (bf.length() > 0) params.setParam("boost", bf); // a boost function extension, see http://wiki.apache.org/solr/ExtendedDisMax#bf_.28Boost_Function.2C_additive.29
        
        // prepare result
        ConcurrentLog.info("Protocol", "SOLR QUERY: " + params.toString());
        this.cachedQuery = params;
        return params;
    }
    
    private SolrQuery solrImageQuery(boolean getFacets) {
        if (this.cachedQuery != null) {
            this.cachedQuery.setStart(this.offset);
            if (!getFacets) this.cachedQuery.setFacet(false);
            return this.cachedQuery;
        }
        
        // construct query
        final SolrQuery params = getBasicParams(getFacets);
        params.setQuery(this.queryGoal.collectionImageQueryString().toString());
        
        // set boosts
        StringBuilder bq = new StringBuilder();
        bq.append(CollectionSchema.url_file_ext_s.getSolrFieldName()).append(":\"jpg\"");
        bq.append(" OR ").append(CollectionSchema.url_file_ext_s.getSolrFieldName()).append(":\"tif\"");
        bq.append(" OR ").append(CollectionSchema.url_file_ext_s.getSolrFieldName()).append(":\"tiff\"");
        bq.append(" OR ").append(CollectionSchema.url_file_ext_s.getSolrFieldName()).append(":\"png\"");
        params.setParam("bq", bq.toString());
        
        // prepare result
        ConcurrentLog.info("Protocol", "SOLR QUERY: " + params.toString());
        this.cachedQuery = params;
        return params;
    }
    
    private SolrQuery getBasicParams(boolean getFacets) {
        final SolrQuery params = new SolrQuery();
        params.setParam("defType", "edismax");
        params.setStart(this.offset);
        params.setRows(this.itemsPerPage);
        params.setFacet(false);

        if (this.ranking.coeff_date == RankingProfile.COEFF_MAX) {
            // set a most-recent ordering
            params.setSort(new SortClause(CollectionSchema.last_modified.getSolrFieldName(), SolrQuery.ORDER.desc));
            //params.setSortField(CollectionSchema.last_modified.getSolrFieldName(), ORDER.desc); // deprecated in Solr 4.2
        }
        
        // add site facets
        final String fq = getFacets();
        if (fq.length() > 0) {
            params.setFilterQueries(fq);
        }
        
        // set facet query attributes
        if (getFacets && this.facetfields.size() > 0) {
            params.setFacet(true);
            params.setFacetMinCount(1);
            params.setFacetLimit(this.maxfacets);
            params.setFacetSort(FacetParams.FACET_SORT_COUNT);
            params.setParam(FacetParams.FACET_METHOD, FacetParams.FACET_METHOD_fcs);
            for (String field: this.facetfields) params.addFacetField("{!ex=" + field + "}" + field);
        } else {
            params.setFacet(false);
        }
        params.setFields("*", "score"); // we need the score for post-ranking
        return params;
    }
    
    private String getFacets() {
        
        // add site facets
        final StringBuilder fq = new StringBuilder();
        if (this.modifier.sitehash == null && this.modifier.sitehost == null) {
            if (this.siteexcludes != null) {
                for (String ex: this.siteexcludes) {
                    fq.append(" AND -").append(CollectionSchema.host_id_s.getSolrFieldName()).append(':').append(ex);
                }
            }
        } else {
            if (this.modifier.sitehost != null) {
                // consider to search for hosts with 'www'-prefix, if not already part of the host name
                if (this.modifier.sitehost.startsWith("www.")) {
                    fq.append(" AND (").append(CollectionSchema.host_s.getSolrFieldName()).append(":\"").append(this.modifier.sitehost.substring(4)).append('\"');
                    fq.append(" OR ").append(CollectionSchema.host_s.getSolrFieldName()).append(":\"").append(this.modifier.sitehost).append("\")");
                } else {
                    fq.append(" AND (").append(CollectionSchema.host_s.getSolrFieldName()).append(":\"").append(this.modifier.sitehost).append('\"');
                    fq.append(" OR ").append(CollectionSchema.host_s.getSolrFieldName()).append(":\"www.").append(this.modifier.sitehost).append("\")");
                }
            } else
                fq.append(" AND ").append(CollectionSchema.host_id_s.getSolrFieldName()).append(":\"").append(this.modifier.sitehash).append('\"');
        }

        // add vocabulary facets
        if (this.metatags != null) {
            for (Tagging.Metatag tag : this.metatags) {
                fq.append(" AND ").append(CollectionSchema.VOCABULARY_PREFIX).append(tag.getVocabularyName()).append(CollectionSchema.VOCABULARY_SUFFIX).append(":\"").append(tag.getObject()).append('\"');
            }
        }

        // add language facet
        if (this.modifier.language != null && this.modifier.language.length() > 0 && this.solrSchema.contains((CollectionSchema.language_s))) {
            fq.append(" AND ").append(CollectionSchema.language_s.getSolrFieldName()).append(":\"").append(this.modifier.language).append('\"');
        }

        // add author facets
        if (this.modifier.author != null && this.modifier.author.length() > 0 && this.solrSchema.contains(CollectionSchema.author_sxt)) {
            fq.append(" AND ").append(CollectionSchema.author_sxt.getSolrFieldName()).append(":\"").append(this.modifier.author).append('\"');
        }
        
        if (this.modifier.protocol != null) {
            fq.append(" AND {!tag=").append(CollectionSchema.url_protocol_s.getSolrFieldName()).append("}").append(CollectionSchema.url_protocol_s.getSolrFieldName()).append(':').append(this.modifier.protocol);
        }
        
        if (this.tld != null) {
            fq.append(" AND ").append(CollectionSchema.host_dnc_s.getSolrFieldName()).append(":\"").append(this.tld).append('\"');
        }
        
        if (this.modifier.filetype != null) {
            fq.append(" AND ").append(CollectionSchema.url_file_ext_s.getSolrFieldName()).append(":\"").append(this.modifier.filetype).append('\"');
        }
        
        if (this.inlink != null) {
            fq.append(" AND ").append(CollectionSchema.outboundlinks_urlstub_sxt.getSolrFieldName()).append(":\"").append(this.inlink).append('\"');
        }
        
        if (!this.urlMask_isCatchall) {
            // add a filter query on urls
            String urlMaskPattern = this.urlMask.pattern();
            
            // solr doesn't like slashes, backslashes or doublepoints; remove them // urlmask = ".*\\." + ft + "(\\?.*)?";
            int p;
            while ((p = urlMaskPattern.indexOf(':')) >= 0) urlMaskPattern = urlMaskPattern.substring(0, p) + "." + urlMaskPattern.substring(p + 1);
            while ((p = urlMaskPattern.indexOf('/')) >= 0) urlMaskPattern = urlMaskPattern.substring(0, p) + "." + urlMaskPattern.substring(p + 1);
            while ((p = urlMaskPattern.indexOf('\\')) >= 0) urlMaskPattern = urlMaskPattern.substring(0, p) + "." + urlMaskPattern.substring(p + 2);
            fq.append(" AND ").append(CollectionSchema.sku.getSolrFieldName() + ":/" + urlMaskPattern + "/");
        }
        
        if (this.radius > 0.0d && this.lat != 0.0d && this.lon != 0.0d) {
            // localtion search, no special ranking
            // try http://localhost:8090/solr/select?q=*:*&fq={!bbox sfield=coordinate_p pt=50.17,8.65 d=1}

            //params.setQuery("!bbox " + q.toString());
            //params.set("sfield", YaCySchema.coordinate_p.name());
            //params.set("pt", Double.toString(this.lat) + "," + Double.toString(this.lon));
            //params.set("d", GeoLocation.degreeToKm(this.radius));
            fq.append(" AND ").append("{!bbox sfield=" + CollectionSchema.coordinate_p.getSolrFieldName() + " pt=" + Double.toString(this.lat) + "," + Double.toString(this.lon) + " d=" + GeoLocation.degreeToKm(this.radius) + "}");
            //params.setRows(Integer.MAX_VALUE);
        }
        
        if (this.modifier.collection != null && this.modifier.collection.length() > 0) {
            fq.append(" AND ").append(QueryModifier.parseCollectionExpression(this.modifier.collection));
        }
        
        return fq.length() > 0 ? fq.substring(5) : fq.toString();
    }
    
    public QueryGoal getQueryGoal() {
        return this.queryGoal;
    }

    public final Map<DigestURL, String> separateMatches(final Map<DigestURL, String> links) {
        final Map<DigestURL, String> matcher = new HashMap<DigestURL, String>();
        final Iterator <Map.Entry<DigestURL, String>> i = links.entrySet().iterator();
        Map.Entry<DigestURL, String> entry;
        DigestURL url;
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
            context.append(this.modifier.sitehash).append(asterisk);
            context.append(this.modifier.author).append(asterisk);
            context.append(this.modifier.protocol).append(asterisk);
            context.append(this.modifier.filetype).append(asterisk);
            context.append(this.modifier.collection).append(asterisk);
            context.append(this.modifier.toString()).append(asterisk);
            context.append(this.siteexcludes).append(asterisk);
            context.append(this.targetlang).append(asterisk);
            context.append(this.constraint).append(asterisk);
            context.append(this.maxDistance).append(asterisk);
            context.append(this.tld).append(asterisk);
            context.append(this.inlink).append(asterisk);
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
    public static StringBuilder navurl(final RequestHeader.FileType ext, final int page, final QueryParams theQuery, final String newQueryString, boolean newModifierReplacesOld) {

        final StringBuilder sb = navurlBase(ext, theQuery, newQueryString, newModifierReplacesOld);

        sb.append("&startRecord=");
        sb.append(page * theQuery.itemsPerPage());

        return sb;
    }

     /**
     * construct navigator url
     *
     * @param ext extension of servlet (e.g. html, rss)
     * @param theQuery search query
     * @param newModifier optional new modifier.
     *      - if null existing modifier of theQuery is appended
     *      - if not null this new modifier is appended in addition to existing modifier
     *      - if isEmpty overwrites (clears) existing modifier
     * @return url to new search result page
     */
    public static StringBuilder navurlBase(final RequestHeader.FileType ext, final QueryParams theQuery, final String newModifier, boolean newModifierReplacesOld) {

        final StringBuilder sb = new StringBuilder(120);
        sb.append("/yacysearch.");
        sb.append(ext.name().toLowerCase());
        sb.append("?query=");

        if (newModifier == null) {
            sb.append(theQuery.getQueryGoal().getQueryString(true));
            if (!theQuery.modifier.isEmpty()) sb.append("+" + theQuery.modifier.toString());
        } else {
            if (newModifier.isEmpty()) {
                sb.append(theQuery.getQueryGoal().getQueryString(true));
            } else {
                if (newModifierReplacesOld) {
                    sb.append(newModifier);
                } else {
                    sb.append(theQuery.queryGoal.getQueryString(true));
                    if (!theQuery.modifier.isEmpty()) sb.append("+" + theQuery.modifier.toString());
                    sb.append("+" + newModifier);
                }
            }
        }

        sb.append("&maximumRecords=");
        sb.append(theQuery.itemsPerPage());

        sb.append("&resource=");
        sb.append((theQuery.isLocal()) ? "local" : "global");

        sb.append("&verify=");
        sb.append(theQuery.snippetCacheStrategy == null ? "false" : theQuery.snippetCacheStrategy.toName());

        sb.append("&prefermaskfilter=");
        sb.append(theQuery.prefer);

        sb.append("&cat=href");

        sb.append("&constraint=");
        sb.append((theQuery.constraint == null) ? "" : theQuery.constraint.exportB64());

        sb.append("&contentdom=");
        sb.append(theQuery.contentdom.toString());

        sb.append("&former=");
        sb.append(theQuery.getQueryGoal().getQueryString(true));

        return sb;
    }

}
