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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.geo.GeoLocation;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.document.LibraryProvider;
import net.yacy.document.ProbabilisticClassifier;
import net.yacy.document.Tokenizer;
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

import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.util.DateFormatUtil;

public final class QueryParams {

    public static int FACETS_STANDARD_MAXCOUNT = 100; // max count of item lines in navigator
    public static int FACETS_DATE_MAXCOUNT = 640;
    
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
        // defaultfacetfields.put("location", CollectionSchema.coordinate_p_0_coordinate); // coordinate_p can't be used for facet (subfields), as value isn't used subfield can be used
        defaultfacetfields.put("hosts", CollectionSchema.host_s);
        defaultfacetfields.put("protocol", CollectionSchema.url_protocol_s);
        defaultfacetfields.put("filetype", CollectionSchema.url_file_ext_s);
        defaultfacetfields.put("date", CollectionSchema.dates_in_content_dts);
        defaultfacetfields.put("authors", CollectionSchema.author_sxt);
        defaultfacetfields.put("collections", CollectionSchema.collection_sxt);
        defaultfacetfields.put("language", CollectionSchema.language_s);
        //missing: namespace
    }
    
    public static final Bitfield empty_constraint    = new Bitfield(4, "AAAAAA");
    public static final Pattern catchall_pattern = Pattern.compile(".*");
    private static final Pattern matchnothing_pattern = Pattern.compile("");

    private final QueryGoal queryGoal;
    public int itemsPerPage;
    public int offset;
    public Pattern urlMaskPattern;
    public Automaton urlMaskAutomaton;
    public String urlMaskString;

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
    // values that are set after a search:
    public int transmitcount; // number of results that had been shown to the user
    public long searchtime, urlretrievaltime, snippetcomputationtime; // time to perform the search, to get all the urls, and to compute the snippets
    public final String userAgent;
    protected double lat, lon, radius;
    public LinkedHashSet<String> facetfields;
    private SolrQuery cachedQuery;
    private CollectionConfiguration solrSchema;
    public final int timezoneOffset;

    public QueryParams(
        final QueryGoal queryGoal,
        final QueryModifier modifier,
        final int maxDistance,
        final String prefer,
        final ContentDomain contentdom,
        final String language,
        final int timezoneOffset,
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
        this.timezoneOffset = timezoneOffset;
        this.itemsPerPage = Math.min((specialRights) ? 10000 : 1000, itemsPerPage);
        this.offset = Math.max(0, Math.min((specialRights) ? 10000 - this.itemsPerPage : 1000 - this.itemsPerPage, offset));
        try {
            this.urlMaskString = urlMask;
            // solr doesn't like slashes, backslashes or doublepoints; remove them // urlmask = ".*\\." + ft + "(\\?.*)?";
            int p;
            while ((p = this.urlMaskString.indexOf(':')) >= 0) this.urlMaskString = this.urlMaskString.substring(0, p) + "." + this.urlMaskString.substring(p + 1);
            while ((p = this.urlMaskString.indexOf('/')) >= 0) this.urlMaskString = this.urlMaskString.substring(0, p) + "." + this.urlMaskString.substring(p + 1);
            while ((p = this.urlMaskString.indexOf('\\')) >= 0) this.urlMaskString = this.urlMaskString.substring(0, p) + "." + this.urlMaskString.substring(p + 2);
            this.urlMaskAutomaton = Automata.makeString(this.urlMaskString);
            this.urlMaskPattern = Pattern.compile(this.urlMaskString);
        } catch (final Throwable ex) {
            throw new IllegalArgumentException("Not a valid regular expression: " + urlMask, ex);
        }
        this.urlMask_isCatchall = this.urlMaskString.equals(catchall_pattern.toString());
        if (this.urlMask_isCatchall) {
            String protocolfilter = modifier.protocol == null ? ".*" : modifier.protocol;
            String defaulthostprefix = modifier.protocol == null ? "www" : modifier.protocol;
            String hostfilter = modifier.sitehost == null && tld == null ? ".*" : modifier.sitehost == null ? ".*\\." + tld : modifier.sitehost.startsWith(defaulthostprefix + ".") ? "(" + defaulthostprefix + "\\.)?" + modifier.sitehost.substring(4) : "(" + defaulthostprefix + "\\.)?" + modifier.sitehost;
            String filefilter = modifier.filetype == null ? ".*" : ".*" + modifier.filetype + ".*"; // TODO: should be ".ext" but while/comment above suggests not -> add filetype contrain pullOneFilteredFromRWI()
            String filter = protocolfilter + "..." + hostfilter + "." + filefilter;
            if (!filter.equals(".*....*..*")) {
                Pattern r = Pattern.compile("(\\.|(\\.\\*))\\.\\*");
                Matcher m;
                while ((m = r.matcher(filter)).find()) filter = m.replaceAll(".*");
                this.urlMaskString = filter;
                this.urlMaskAutomaton = Automata.makeString(filter);
                this.urlMask_isCatchall = false;
                this.urlMaskPattern = Pattern.compile(filter);
            }
        }
        this.tld = tld;
        this.inlink = inlink;
        try {
            this.prefer = Pattern.compile(prefer);
        } catch (final PatternSyntaxException ex) {
            throw new IllegalArgumentException("Not a valid regular expression: " + prefer, ex);
        }
        assert language != null;
        this.targetlang = language;
        this.metatags = metatags;
        this.domType = domType;
        this.zonecode = domainzone;
        this.constraint = constraint;
        this.allofconstraint = allofconstraint;
        this.siteexcludes = siteexcludes != null && siteexcludes.isEmpty() ? null: siteexcludes;
        this.snippetCacheStrategy = snippetCacheStrategy;
        this.clienthost = host;
        this.remotepeer = null;
        this.starttime = Long.valueOf(System.currentTimeMillis());
        this.maxtime = 10000;
        this.indexSegment = indexSegment;
        this.userAgent = userAgent;
        this.transmitcount = 0;
        // we normalize here the location and radius because that should cause a better caching
        // and as surplus it will increase privacy
        this.lat = Math.floor(lat * this.kmNormal) / this.kmNormal;
        this.lon = Math.floor(lon * this.kmNormal) / this.kmNormal;
        this.radius = Math.floor(radius * this.kmNormal + 1) / this.kmNormal;
        this.facetfields = new LinkedHashSet<String>();
        
        this.solrSchema = indexSegment.fulltext().getDefaultConfiguration();
        for (String navkey: search_navigation) {
            CollectionSchema f = defaultfacetfields.get(navkey);
            // handle special field, authors_sxt (add to facet w/o contains check, as authors_sxt is not enabled (is copyfield))
            // dto. for coordinate_p_0_coordinate is not enabled but used for location facet (because coordinate_p not valid for facet field)
            if (f != null && (solrSchema.contains(f) || f.name().equals("author_sxt") || f.name().equals("coordinate_p_0_coordinate") ))
                this.facetfields.add(f.getSolrFieldName());
        }
        if (LibraryProvider.autotagging != null) for (Tagging v: LibraryProvider.autotagging.getVocabularies()) {
            if (v.isFacet()) {
                this.facetfields.add(CollectionSchema.VOCABULARY_PREFIX + v.getName() + CollectionSchema.VOCABULARY_TERMS_SUFFIX);
            }
        }
        for (String context: ProbabilisticClassifier.getContextNames()) {
            this.facetfields.add(CollectionSchema.VOCABULARY_PREFIX + context + CollectionSchema.VOCABULARY_TERMS_SUFFIX);
        }
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
        QueryGoal.NormalizedWords words = new QueryGoal.NormalizedWords(Tokenizer.getWords(text, null).keySet());
        if (!SetTools.anymatchByTest(this.queryGoal.getExcludeWords(), words)) {
            ret = SetTools.totalInclusion(this.queryGoal.getIncludeWords(), words);
        }
        return ret;
    }
    
    protected static final boolean anymatch(final String text, final Iterator<String> keywords) {
        if (keywords == null || !keywords.hasNext()) return false;
        final SortedSet<String> textwords = (SortedSet<String>) Tokenizer.getWords(text, null).keySet();
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
        final SolrQuery params = getBasicParams(getFacets, this.queryGoal.collectionTextFilterQuery(excludeintext_image));
        int rankingProfile = this.ranking.coeff_date == RankingProfile.COEFF_MAX ? 1 : (this.modifier.sitehash != null || this.modifier.sitehost != null) ? 2 : 0;
        params.setQuery(this.queryGoal.collectionTextQuery().toString());
        Ranking actRanking = indexSegment.fulltext().getDefaultConfiguration().getRanking(rankingProfile); // for a by-date ranking select different ranking profile

        String fq = actRanking.getFilterQuery();
        String bq = actRanking.getBoostQuery();
        String bf = actRanking.getBoostFunction();
        final String qf = actRanking.getQueryFields();
        if (!qf.isEmpty()) params.setParam(DisMaxParams.QF, qf);
        if (this.queryGoal.getIncludeSize() > 1) {
            // add boost on combined words
            if (bq.length() > 0) bq += "\n";
            bq += CollectionSchema.text_t.getSolrFieldName() + ":\"" + this.queryGoal.getIncludeString() + "\"^10";
        }
        if (fq.length() > 0) {
            String[] oldfq = params.getFilterQueries();
            ArrayList<String> newfq = new ArrayList<>(oldfq.length + 1);
            for (String x: oldfq) newfq.add(x);
            newfq.add(fq);
            params.setFilterQueries(newfq.toArray(new String[newfq.size()]));
        }
        if (bq.length() > 0) params.setParam(DisMaxParams.BQ, bq.split("[\\r\\n]+")); // split on any sequence consisting of CR and/or LF
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
        final SolrQuery params = getBasicParams(getFacets, this.queryGoal.collectionImageFilterQuery());
        params.setQuery(this.queryGoal.collectionImageQuery(this.modifier).toString());
        
        // set boosts
        StringBuilder bq = new StringBuilder();
        bq.append(CollectionSchema.url_file_ext_s.getSolrFieldName()).append(":\"jpg\"");
        bq.append(" OR ").append(CollectionSchema.url_file_ext_s.getSolrFieldName()).append(":\"tif\"");
        bq.append(" OR ").append(CollectionSchema.url_file_ext_s.getSolrFieldName()).append(":\"tiff\"");
        bq.append(" OR ").append(CollectionSchema.url_file_ext_s.getSolrFieldName()).append(":\"png\"");
        params.setParam(DisMaxParams.BQ, bq.toString());
        
        // prepare result
        ConcurrentLog.info("Protocol", "SOLR QUERY: " + params.toString());
        this.cachedQuery = params;
        return params;
    }
    
    private SolrQuery getBasicParams(boolean getFacets, List<String> fqs) {
        final SolrQuery params = new SolrQuery();
        params.setParam("defType", "edismax");
        params.setParam(DisMaxParams.QF, CollectionSchema.text_t.getSolrFieldName() + "^1.0");
        params.setStart(this.offset);
        params.setRows(this.itemsPerPage);
        params.setFacet(false);

        if (this.ranking.coeff_date == RankingProfile.COEFF_MAX) {
            // set a most-recent ordering
            params.setSort(new SortClause(CollectionSchema.last_modified.getSolrFieldName(), SolrQuery.ORDER.desc));
            //params.setSortField(CollectionSchema.last_modified.getSolrFieldName(), ORDER.desc); // deprecated in Solr 4.2
        }
        
        // add site facets
        fqs.addAll(getFacetsFilterQueries());
        if (fqs.size() > 0) {
            params.setFilterQueries(fqs.toArray(new String[fqs.size()]));
        }
        
        // set facet query attributes
        if (getFacets && this.facetfields.size() > 0) {
            params.setFacet(true);
            params.setFacetMinCount(1);
            params.setFacetLimit(FACETS_STANDARD_MAXCOUNT);
            params.setFacetSort(FacetParams.FACET_SORT_COUNT);
            params.setParam(FacetParams.FACET_METHOD, FacetParams.FACET_METHOD_enum); // fight the fieldcache
            for (String field: this.facetfields) params.addFacetField("{!ex=" + field + "}" + field); // params.addFacetField("{!ex=" + field + "}" + field);
            if (this.facetfields.contains(CollectionSchema.dates_in_content_dts.name())) {
            	params.setParam(FacetParams.FACET_RANGE, CollectionSchema.dates_in_content_dts.name());
                String start = DateFormatUtil.formatExternal(new Date(System.currentTimeMillis() - 1000L * 60L * 60L * 24L * 3));
                String end = DateFormatUtil.formatExternal(new Date(System.currentTimeMillis() + 1000L * 60L * 60L * 24L * 3));
                params.setParam("f." + CollectionSchema.dates_in_content_dts.getSolrFieldName() + ".facet.range.start", start);
                params.setParam("f." + CollectionSchema.dates_in_content_dts.getSolrFieldName() + ".facet.range.end", end);
                params.setParam("f." + CollectionSchema.dates_in_content_dts.getSolrFieldName() + ".facet.range.gap", "+1DAY");
                params.setParam("f." + CollectionSchema.dates_in_content_dts.getSolrFieldName() + ".facet.sort", "index");
                params.setParam("f." + CollectionSchema.dates_in_content_dts.getSolrFieldName() + ".facet.limit", Integer.toString(FACETS_DATE_MAXCOUNT)); // the year constraint should cause that limitation already
            }
            //for (String k: params.getParameterNames()) {ArrayList<String> al = new ArrayList<>(); for (String s: params.getParams(k)) al.add(s); System.out.println("Parameter: " + k + "=" + al.toString());}
            //http://localhost:8090/solr/collection1/select?q=*:*&rows=0&facet=true&facet.field=dates_in_content_dts&f.dates_in_content_dts.facet.limit=730&f.dates_in_content_dts.facet.sort=index
        } else {
            params.setFacet(false);
        }
        params.setFields("*", "score"); // we need the score for post-ranking
        return params;
    }
    
    long year = 1000L * 60L * 60L * 24L * 365L;
    
    private List<String> getFacetsFilterQueries() {
        
        ArrayList<String> fqs = new ArrayList<>();
        
        // add site facets
        if (this.modifier.sitehash == null && this.modifier.sitehost == null) {
            if (this.siteexcludes != null) {
                for (String ex: this.siteexcludes) {
                    fqs.add("-" + CollectionSchema.host_id_s.getSolrFieldName() + ':' + ex);
                }
            }
        } else {
            if (this.modifier.sitehost != null) {
                // consider to search for hosts with 'www'-prefix, if not already part of the host name
                if (this.modifier.sitehost.startsWith("www.")) {
                    fqs.add(CollectionSchema.host_s.getSolrFieldName() + ":\"" + this.modifier.sitehost.substring(4) + "\" OR " + CollectionSchema.host_s.getSolrFieldName() + ":\"" + this.modifier.sitehost + "\"");
                } else {
                    fqs.add(CollectionSchema.host_s.getSolrFieldName() + ":\"" + this.modifier.sitehost + "\" OR " + CollectionSchema.host_s.getSolrFieldName() + ":\"www." + this.modifier.sitehost + "\"");
                }
            } else
                fqs.add(CollectionSchema.host_id_s.getSolrFieldName() + ":\"" + this.modifier.sitehash + '\"');
        }

        // add vocabulary facets
        if (this.metatags != null) {
            for (Tagging.Metatag tag : this.metatags) {
                fqs.add(CollectionSchema.VOCABULARY_PREFIX + tag.getVocabularyName() + CollectionSchema.VOCABULARY_TERMS_SUFFIX + ":\"" + tag.getObject() + '\"');
            }
        }

        // add language facet
        if (this.modifier.language != null && this.modifier.language.length() > 0 && this.solrSchema.contains((CollectionSchema.language_s))) {
            fqs.add(CollectionSchema.language_s.getSolrFieldName() + ":\"" + this.modifier.language + '\"');
        }

        // add author facets (check for contains(author) as author_sxt is omitted copyfield)
        if (this.modifier.author != null && this.modifier.author.length() > 0 && this.solrSchema.contains(CollectionSchema.author)) {
            fqs.add(CollectionSchema.author_sxt.getSolrFieldName() + ":\"" + this.modifier.author + '\"');
        }

        // add collection facets
        if (this.modifier.collection != null && this.modifier.collection.length() > 0 && this.solrSchema.contains(CollectionSchema.collection_sxt)) {
            fqs.add(QueryModifier.parseCollectionExpression(this.modifier.collection));
        }
        
        if (this.solrSchema.contains(CollectionSchema.dates_in_content_dts)) {
            if (this.modifier.on != null && this.modifier.on.length() > 0) {
                fqs.add(QueryModifier.parseOnExpression(this.modifier.on, this.timezoneOffset));
            }
            
            if (this.modifier.from != null && this.modifier.from.length() > 0 && (this.modifier.to == null || this.modifier.to.equals("*"))) {
                fqs.add(QueryModifier.parseFromToExpression(this.modifier.from, null, this.timezoneOffset));
            }
            
            if ((this.modifier.from == null || this.modifier.from.equals("*")) && this.modifier.to != null && this.modifier.to.length() > 0) {
                fqs.add(QueryModifier.parseFromToExpression(null, this.modifier.to, this.timezoneOffset));
            }
            
            if (this.modifier.from != null && this.modifier.from.length() > 0 && this.modifier.to != null && this.modifier.to.length() > 0) {
                fqs.add(QueryModifier.parseFromToExpression(this.modifier.from, this.modifier.to, this.timezoneOffset));
            }
        }
        
        if (this.modifier.protocol != null) {
            fqs.add("{!tag=" + CollectionSchema.url_protocol_s.getSolrFieldName() + "}" + CollectionSchema.url_protocol_s.getSolrFieldName() + ':' + this.modifier.protocol);
        }
        
        if (this.tld != null) {
            fqs.add(CollectionSchema.host_dnc_s.getSolrFieldName() + ":\"" + this.tld + '\"');
        }
        
        if (this.modifier.filetype != null) {
            fqs.add(CollectionSchema.url_file_ext_s.getSolrFieldName() + ":\"" + this.modifier.filetype + '\"');
        }
        
        if (this.inlink != null) {
            fqs.add(CollectionSchema.outboundlinks_urlstub_sxt.getSolrFieldName() + ":\"" + this.inlink + '\"');
        }
        
        if (!this.urlMask_isCatchall) {
            // add a filter query on urls
            fqs.add(CollectionSchema.sku.getSolrFieldName() + ":/" + this.urlMaskString + "/");
        }
        
        if (this.radius > 0.0d && this.lat != 0.0d && this.lon != 0.0d) {
            // localtion search, no special ranking
            // try http://localhost:8090/solr/select?q=*:*&fq={!bbox sfield=coordinate_p pt=50.17,8.65 d=1}

            //params.setQuery("!bbox " + q.toString());
            //params.set("sfield", YaCySchema.coordinate_p.name());
            //params.set("pt", Double.toString(this.lat) + "," + Double.toString(this.lon));
            //params.set("d", GeoLocation.degreeToKm(this.radius));
            fqs.add("{!bbox sfield=" + CollectionSchema.coordinate_p.getSolrFieldName() + " pt=" + Double.toString(this.lat) + "," + Double.toString(this.lon) + " d=" + GeoLocation.degreeToKm(this.radius) + "}");
            //params.setRows(Integer.MAX_VALUE);
        }
        
        return fqs;
    }
    
    public QueryGoal getQueryGoal() {
        return this.queryGoal;
    }

    public final Map<AnchorURL, String> separateMatches(final Map<AnchorURL, String> links) {
        final Map<AnchorURL, String> matcher = new HashMap<>();
        final Iterator <Map.Entry<AnchorURL, String>> i = links.entrySet().iterator();
        Map.Entry<AnchorURL, String> entry;
        AnchorURL url;
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
            context.append(Base64Order.enhancedCoder.encodeString(this.urlMaskString)).append(asterisk);
            context.append(this.modifier.sitehash).append(asterisk);
            context.append(this.modifier.author).append(asterisk);
            context.append(this.modifier.protocol).append(asterisk);
            context.append(this.modifier.filetype).append(asterisk);
            context.append(this.modifier.collection).append(asterisk);
            context.append(this.modifier.toString()).append(asterisk);
            context.append(this.siteexcludes).append(asterisk);
            context.append(this.targetlang).append(asterisk);
            context.append(this.domType).append(asterisk);
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

        StringBuilder sb = new StringBuilder(120);
        sb.append("yacysearch.");
        sb.append(ext.name().toLowerCase());
        sb.append("?query=");

        sb.append(theQuery.getQueryGoal().getQueryString(true));
        if (newModifier == null) {
            if (!theQuery.modifier.isEmpty()) sb.append("+" + theQuery.modifier.toString());
        } else {
            if (!newModifier.isEmpty()) {
                if (!theQuery.modifier.isEmpty()) sb.append("+" + theQuery.modifier.toString());
                if (newModifierReplacesOld) {
                    int nmpi = newModifier.indexOf(":");
                    if (nmpi > 0) {
                        String nmp = newModifier.substring(0, nmpi) + ":";
                        int i = sb.indexOf(nmp);
                        if (i > 0) sb = new StringBuilder(sb.substring(0, i).trim());
                        if (sb.charAt(sb.length() - 1) == '+') sb.setLength(sb.length() - 1);
                    }
                }
                try {
                	sb.append("+" + URLEncoder.encode(newModifier, StandardCharsets.UTF_8.name()));
                } catch (final UnsupportedEncodingException e) {
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
