/**
 *  CollectionConfiguration
 *  Copyright 2011 by Michael Peter Christen
 *  First released 14.04.2011 at https://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 22:05:04 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7654 $
 *  $LastChangedBy: orbiter $
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.search.schema;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;

import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.analysis.EnhancedTextProfileSignature;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.FailType;
import net.yacy.cora.federate.solr.ProcessType;
import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.federate.solr.SchemaConfiguration;
import net.yacy.cora.federate.solr.SchemaDeclaration;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.solr.logic.BooleanLiteral;
import net.yacy.cora.federate.solr.logic.CatchallLiteral;
import net.yacy.cora.federate.solr.logic.Conjunction;
import net.yacy.cora.federate.solr.logic.Disjunction;
import net.yacy.cora.federate.solr.logic.LongLiteral;
import net.yacy.cora.federate.solr.logic.Negation;
import net.yacy.cora.federate.solr.logic.StringLiteral;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.ProbabilisticClassifier;
import net.yacy.document.SentenceReader;
import net.yacy.document.Tokenizer;
import net.yacy.document.content.DCEntry;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.IconEntry;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.citation.CitationReference;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.index.RowHandleMap;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.index.Segment;
import net.yacy.search.index.Segment.ReferenceReport;
import net.yacy.search.index.Segment.ReferenceReportCache;
import net.yacy.search.query.QueryParams;


public class CollectionConfiguration extends SchemaConfiguration implements Serializable {

    private static final long serialVersionUID=-499100932212840385L;

    public static boolean UNIQUE_HEURISTIC_PREFER_HTTPS = false;
    public static boolean UNIQUE_HEURISTIC_PREFER_WWWPREFIX = true;

    private final ArrayList<Ranking> rankings;

    /**
     * initialize the schema with a given configuration file
     * the configuration file simply contains a list of lines with keywords
     * or keyword = value lines (while value is a custom Solr field name
     * @param configurationFile
     * @throws IOException
     */
    public CollectionConfiguration(final File configurationFile, final boolean lazy) throws IOException {
        super(configurationFile);
        super.lazy = lazy;
        this.rankings = new ArrayList<>(4);
        for (int i = 0; i <= 3; i++) this.rankings.add(new Ranking());
        // check consistency: compare with YaCyField enum
        if (this.isEmpty()) return;
        final Iterator<Entry> it = this.entryIterator();
        for (SchemaConfiguration.Entry etr = it.next(); it.hasNext(); etr = it.next()) {
            try {
                final CollectionSchema f = CollectionSchema.valueOf(etr.key());
                f.setSolrFieldName(etr.getValue());
            } catch (final IllegalArgumentException e) {
                ConcurrentLog.fine("SolrCollectionWriter", "solr schema file " + configurationFile.getAbsolutePath() + " defines unknown attribute '" + etr.toString() + "'");
                it.remove();
            }
        }
        // check consistency the other way: look if all enum constants in SolrField appear in the configuration file
        for (final CollectionSchema field: CollectionSchema.values()) {
        	if (this.get(field.name()) == null) {
        	    if (CollectionSchema.author_sxt.getSolrFieldName().endsWith(field.name())) continue; // exception for this: that is a copy-field
        	    if (CollectionSchema.coordinate_p_0_coordinate.getSolrFieldName().endsWith(field.name())) continue; // exception for this: automatically generated
        	    if (CollectionSchema.coordinate_p_1_coordinate.getSolrFieldName().endsWith(field.name())) continue; // exception for this: automatically generated
                ConcurrentLog.warn("SolrCollectionWriter", " solr schema file " + configurationFile.getAbsolutePath() + " is missing declaration for '" + field.name() + "'");
        	}
        }
        this.checkMandatoryFields(); // Check minimum needed fields for proper operation are enabled
        this.checkFieldRelationConsistency();
    }

    /**
     * Check and update schema configuration with required related fields.
     * If a specific field is enabled, there might be a other field internal
     * processes rely on. Enable these required fields.
     * For example, the outboundlinks are split into protocol and url part. The
     * correct original url can only be assembled if both fields are available (protocol + url = originalUrl)
     */
    private void checkFieldRelationConsistency() {
        Entry e;
        // for correct assembly of outboundlinks  outboundlinks_protocol_sxt + outboundlinks_urlstub_sxt is needed
        if (this.contains(CollectionSchema.outboundlinks_urlstub_sxt) && !this.contains(CollectionSchema.outboundlinks_protocol_sxt)) {
            e = new Entry(CollectionSchema.outboundlinks_protocol_sxt.name(), CollectionSchema.outboundlinks_protocol_sxt.getSolrFieldName(), true);
            this.put(CollectionSchema.outboundlinks_protocol_sxt.name(), e);
        }
        // for correct assembly of inboundlinks  inboundlinks_protocol_sxt + inboundlinks_urlstub_sxt is needed
        if (this.contains(CollectionSchema.inboundlinks_urlstub_sxt) && !this.contains(CollectionSchema.inboundlinks_protocol_sxt)) {
            e = new Entry(CollectionSchema.inboundlinks_protocol_sxt.name(), CollectionSchema.inboundlinks_protocol_sxt.getSolrFieldName(), true);
            this.put(CollectionSchema.inboundlinks_protocol_sxt.name(), e);
        }
        // for correct assembly of icon url  icons_protocol_sxt + icons_urlstub_sxt is needed
        if (this.contains(CollectionSchema.icons_urlstub_sxt) && !this.contains(CollectionSchema.icons_protocol_sxt)) {
            e = new Entry(CollectionSchema.icons_protocol_sxt.name(), CollectionSchema.icons_protocol_sxt.getSolrFieldName(), true);
            this.put(CollectionSchema.icons_protocol_sxt.name(), e);
        }
        // for correct assembly of image url  images_protocol_sxt + images_urlstub_sxt is needed
        if (this.contains(CollectionSchema.images_urlstub_sxt) && !this.contains(CollectionSchema.images_protocol_sxt)) {
            e = new Entry(CollectionSchema.images_protocol_sxt.name(), CollectionSchema.images_protocol_sxt.getSolrFieldName(), true);
            this.put(CollectionSchema.images_protocol_sxt.name(), e);
        }
    }

    /**
     * Check and update schema configuration with fields strictly needed for proper YaCy operation.
     */
    private void checkMandatoryFields() {
        SchemaConfiguration.Entry entry;
        for (final CollectionSchema field: CollectionSchema.values()) {
        	if(field.isMandatory()) {
        		entry = this.get(field.name());
            	if (entry != null) {
            		if(!entry.enabled()) {
            			entry.setEnable(true);
            			ConcurrentLog.info("SolrCollectionWriter", "Forced activation of mandatory field " + field.name());
            		}
            	} else {
            		this.put(field.name(), new Entry(field.name(), field.getSolrFieldName(), true));
            		ConcurrentLog.info("SolrCollectionWriter", "Added missing mandatory field " + field.name());
            	}
        	}
        }
    }

    public String[] allFields() {
        final ArrayList<String> a = new ArrayList<>(this.size());
        for (final CollectionSchema f: CollectionSchema.values()) {
            if (this.contains(f)) a.add(f.getSolrFieldName());
        }
        return a.toArray(new String[a.size()]);
    }

    public Ranking getRanking(final int idx) {
        return this.rankings.get(idx % this.rankings.size()); // simply prevent out of bound exeption (& callers don't check for null)
    }

    /**
     * @param name The name of the ranking to get.
     * @return The corresponding Ranking-object.
     */
    public Ranking getRanking(final String name) {
        if (name == null) return null;
        for (int i = 0; i < this.rankings.size(); i++) {
            final Ranking currentRanking = this.rankings.get(i);
            if (name.equals(currentRanking.getName())) return currentRanking;
        }
        return null;
    }

    /**
     * save configuration to file and update enum SolrFields
     * @throws IOException
     */
    @Override
    public void commit() throws IOException {
    	this.checkMandatoryFields(); // Check minimum needed fields for proper operation are enabled
        this.checkFieldRelationConsistency(); // in case of changes, check related fields are enabled before save
        try {
            super.commit();
            // make sure the enum SolrField.SolrFieldName is current
            final Iterator<Entry> it = this.entryIterator();
            for (SchemaConfiguration.Entry etr = it.next(); it.hasNext(); etr = it.next()) {
                try {
                    final SchemaDeclaration f = CollectionSchema.valueOf(etr.key());
                    f.setSolrFieldName(etr.getValue());
                } catch (final IllegalArgumentException e) {
                    continue;
                }
            }
        } catch (final IOException e) {}
    }

    private final static Set<String> omitFields = new HashSet<>(3);
    static {
        omitFields.add(CollectionSchema.author_sxt.getSolrFieldName());
        omitFields.add(CollectionSchema.coordinate_p_0_coordinate.getSolrFieldName());
        omitFields.add(CollectionSchema.coordinate_p_1_coordinate.getSolrFieldName());
    }

    public SolrInputDocument toSolrInputDocument(final SolrDocument doc) {
        return this.toSolrInputDocument(doc, omitFields);
    }

    public SolrDocument toSolrDocument(final SolrInputDocument doc) {
        return this.toSolrDocument(doc, omitFields);
    }

    /**
     * add uri attributes to solr document and assign the document id
     * @param doc
     * @param allAttr
     * @param digestURL used to calc. the document.id and the doc.sku=(in index stored url)
     * @return the normalized url
     */
    public String addURIAttributes(final SolrInputDocument doc, final boolean allAttr, final DigestURL digestURL) {
        this.add(doc, CollectionSchema.id, ASCII.String(digestURL.hash()));
        if (allAttr || this.contains(CollectionSchema.host_id_s)) this.add(doc, CollectionSchema.host_id_s, digestURL.hosthash());
        final String us = digestURL.toNormalform(true);
        this.add(doc, CollectionSchema.sku, us);
        if (allAttr || this.contains(CollectionSchema.ip_s)) {
            final InetAddress address = digestURL.getInetAddress();
            if (address != null) this.add(doc, CollectionSchema.ip_s, address.getHostAddress());
        }
        String host = null;
        if ((host = digestURL.getHost()) != null) {
            final String dnc = Domains.getDNC(host);
            final String subdomOrga = host.length() - dnc.length() <= 0 ? "" : host.substring(0, host.length() - dnc.length() - 1);
            final int p = subdomOrga.lastIndexOf('.');
            final String subdom = (p < 0) ? "" : subdomOrga.substring(0, p);
            final String orga = (p < 0) ? subdomOrga : subdomOrga.substring(p + 1);
            if (allAttr || this.contains(CollectionSchema.host_s)) this.add(doc, CollectionSchema.host_s, host);
            if (allAttr || this.contains(CollectionSchema.host_dnc_s)) this.add(doc, CollectionSchema.host_dnc_s, dnc);
            if (allAttr || this.contains(CollectionSchema.host_organization_s)) this.add(doc, CollectionSchema.host_organization_s, orga);
            if (allAttr || this.contains(CollectionSchema.host_organizationdnc_s)) this.add(doc, CollectionSchema.host_organizationdnc_s, orga + '.' + dnc);
            if (allAttr || this.contains(CollectionSchema.host_subdomain_s)) this.add(doc, CollectionSchema.host_subdomain_s, subdom);
        }

        // path elements of link
        final String filename = digestURL.getFileName();
        String extension = MultiProtocolURL.getFileExtension(filename);
        final String filenameStub = filename.toLowerCase(Locale.ROOT).endsWith("." + extension) ? filename.substring(0, filename.length() - extension.length() - 1) : filename;
        // remove possible jsession (or other url parm like "img.jpg;jsession=123")
        // TODO: consider to implement ";jsession=123" check in getFileExtension()
        if (extension.indexOf(';') >= 0) extension = extension.substring(0,extension.indexOf(';'));

        if (allAttr || this.contains(CollectionSchema.url_chars_i)) this.add(doc, CollectionSchema.url_chars_i, us.length());
        if (allAttr || this.contains(CollectionSchema.url_protocol_s)) this.add(doc, CollectionSchema.url_protocol_s, digestURL.getProtocol());
        if (allAttr || this.contains(CollectionSchema.url_paths_sxt) || this.contains(CollectionSchema.url_paths_count_i)) {
            final String[] paths = digestURL.getPaths();
            if (allAttr || this.contains(CollectionSchema.url_paths_count_i)) this.add(doc, CollectionSchema.url_paths_count_i, paths.length);
            if (allAttr || this.contains(CollectionSchema.url_paths_sxt)) this.add(doc, CollectionSchema.url_paths_sxt, paths);
        }
        if (allAttr || this.contains(CollectionSchema.url_file_name_s)) this.add(doc, CollectionSchema.url_file_name_s, filenameStub);
        if (allAttr || this.contains(CollectionSchema.url_file_name_tokens_t)) this.add(doc, CollectionSchema.url_file_name_tokens_t, MultiProtocolURL.toTokens(filenameStub));
        if (allAttr || this.contains(CollectionSchema.url_file_ext_s)) this.add(doc, CollectionSchema.url_file_ext_s, extension);

        final Map<String, String> searchpart = digestURL.getSearchpartMap();
        if (searchpart == null) {
            if (allAttr || this.contains(CollectionSchema.url_parameter_i)) this.add(doc, CollectionSchema.url_parameter_i, 0);
        } else {
            if (allAttr || this.contains(CollectionSchema.url_parameter_i)) this.add(doc, CollectionSchema.url_parameter_i, searchpart.size());
            if (allAttr || this.contains(CollectionSchema.url_parameter_key_sxt)) this.add(doc, CollectionSchema.url_parameter_key_sxt, searchpart.keySet().toArray(new String[searchpart.size()]));
            if (allAttr || this.contains(CollectionSchema.url_parameter_value_sxt)) this.add(doc, CollectionSchema.url_parameter_value_sxt,  searchpart.values().toArray(new String[searchpart.size()]));
        }
        return us;
    }

    /**
     * Convert a URIMetadataNode, which has some private fields to a pure
     * SolrInputDocument with all field values from the input.
     * This also assigns the document.id with the YaCy url.hash()
     * @param md
     * @return
     */
    public SolrInputDocument metadata2solr(final URIMetadataNode md) {

        final SolrInputDocument doc = this.toSolrInputDocument(md); //urimetadatanode stores some values in private fields, add now to sorldocument

        final boolean allAttr = this.isEmpty();
        this.addURIAttributes(doc, allAttr, md.url()); // assign doc.id, doc.sku and url attribute fields

        final String title = md.dc_title();
        if (allAttr || this.contains(CollectionSchema.title_count_i)) this.add(doc, CollectionSchema.title_count_i, 1);
        if (allAttr || this.contains(CollectionSchema.title_chars_val)) {
            final Integer[] cv = new Integer[]{Integer.valueOf(title.length())};
            this.add(doc, CollectionSchema.title_chars_val, cv);
        }
        if (allAttr || this.contains(CollectionSchema.title_words_val)) {
            final Integer[] cv = new Integer[]{Integer.valueOf(CommonPattern.SPACES.split(title).length)};
            this.add(doc, CollectionSchema.title_words_val, cv);
        }

        String description = md.snippet();
        final boolean description_exist = description != null;
        if (description == null) description = "";
        if (allAttr || this.contains(CollectionSchema.description_txt)) this.add(doc, CollectionSchema.description_txt, description_exist ? new String[]{description} : new String[0]);
        if (allAttr || this.contains(CollectionSchema.description_count_i)) this.add(doc, CollectionSchema.description_count_i, description_exist ? 1 : 0);
        if (allAttr || this.contains(CollectionSchema.description_chars_val)) {
            this.add(doc, CollectionSchema.description_chars_val, description_exist ? new Integer[]{Integer.valueOf(description.length())} : new Integer[0]);
        }
        if (allAttr || this.contains(CollectionSchema.description_words_val)) {
            this.add(doc, CollectionSchema.description_words_val, description_exist ? new Integer[]{Integer.valueOf(description.length() == 0 ? 0 : CommonPattern.SPACES.split(description).length)} : new Integer[0]);
        }

        String keywords = md.dc_subject();
    	final Bitfield flags = md.flags();
    	if (flags.get(Tokenizer.flag_cat_indexof)) {
    		if (keywords == null || keywords.isEmpty()) keywords = "indexof"; else {
    			if (keywords.indexOf(',') > 0) keywords += ", indexof"; else keywords += " indexof";
    		}
    	}
        if (allAttr || this.contains(CollectionSchema.keywords)) {
        	this.add(doc, CollectionSchema.keywords, keywords);
        }

        /* Metadata node may contain one favicon url when transmitted as dht chunk */
        this.processIcons(doc, allAttr, md.getIcons());
        if (allAttr || this.contains(CollectionSchema.imagescount_i)) this.add(doc, CollectionSchema.imagescount_i, md.limage());
        if (allAttr || this.contains(CollectionSchema.linkscount_i)) this.add(doc, CollectionSchema.linkscount_i, md.llocal() + md.lother());
        if (allAttr || this.contains(CollectionSchema.inboundlinkscount_i)) this.add(doc, CollectionSchema.inboundlinkscount_i, md.llocal());
        if (allAttr || this.contains(CollectionSchema.outboundlinkscount_i)) this.add(doc, CollectionSchema.outboundlinkscount_i, md.lother());
        if (allAttr || this.contains(CollectionSchema.charset_s)) this.add(doc, CollectionSchema.charset_s, StandardCharsets.UTF_8.name());

        // coordinates
        if (md.lat() != 0.0 && md.lon() != 0.0) {
            // i.e. from <meta name="geo.position" content="50.78;11.52" /> or <meta name="ICBM" content="52.50695, 13.328348">
            if (allAttr || this.contains(CollectionSchema.coordinate_p)) {
                this.add(doc, CollectionSchema.coordinate_p, Double.toString(md.lat()) + "," + Double.toString(md.lon()));
            }
        }
        if (allAttr || this.contains(CollectionSchema.httpstatus_i)) this.add(doc, CollectionSchema.httpstatus_i, 200);
        if (allAttr || this.contains(CollectionSchema.publisher_t)) this.add(doc, CollectionSchema.publisher_t, md.dc_publisher());

        // fields that are in URIMetadataRow additional to yacy2solr basic requirement
        if (allAttr || this.contains(CollectionSchema.audiolinkscount_i)) this.add(doc, CollectionSchema.audiolinkscount_i, md.laudio());
        if (allAttr || this.contains(CollectionSchema.videolinkscount_i)) this.add(doc, CollectionSchema.videolinkscount_i, md.lvideo());
        if (allAttr || this.contains(CollectionSchema.applinkscount_i)) this.add(doc, CollectionSchema.applinkscount_i, md.lapp());

        return doc;
    }

    public static class Subgraph {
        public final ArrayList<String>[] urlProtocols, urlStubs, urlAnchorTexts;
        @SuppressWarnings("unchecked")
        public Subgraph(int inboundSize, int outboundSize) {
            this.urlProtocols = (ArrayList<String>[]) Array.newInstance(ArrayList.class, 2);
            this.urlProtocols[0] = new ArrayList<>(inboundSize);
            this.urlProtocols[1] = new ArrayList<>(outboundSize);
            this.urlStubs = (ArrayList<String>[]) Array.newInstance(ArrayList.class, 2);
            this.urlStubs[0] = new ArrayList<>(inboundSize);
            this.urlStubs[1] = new ArrayList<>(outboundSize);
            this.urlAnchorTexts = (ArrayList<String>[]) Array.newInstance(ArrayList.class, 2);
            this.urlAnchorTexts[0] = new ArrayList<>(inboundSize);
            this.urlAnchorTexts[1] = new ArrayList<>(outboundSize);
        }
    }

    public static boolean enrichSubgraph(final Subgraph subgraph, final DigestURL source_url, AnchorURL target_url) {
        final String text = target_url.getTextProperty(); // the text between the <a></a> tag
        final String source_host = source_url.getHost();
        final String target_host = target_url.getHost();
        final boolean inbound =
                (source_host == null && target_host == null) ||
                (source_host != null && target_host != null &&
                 (target_host.equals(source_host) ||
                  target_host.equals("www." + source_host) ||
                  source_host.equals("www." + target_host))); // well, not everybody defines 'outbound' that way but however, thats used here.
        final int ioidx = inbound ? 0 : 1;
        subgraph.urlProtocols[ioidx].add(target_url.getProtocol());
        subgraph.urlStubs[ioidx].add(target_url.urlstub(true, true));
        subgraph.urlAnchorTexts[ioidx].add(text);
        return inbound;
    }

    /**
     * a SolrVector is a SolrInputDocument with the ability
     * to store also the webgraph that is associated with
     * the web document in the Solr document.
     */
    public static class SolrVector extends SolrInputDocument {
        private static final long serialVersionUID = -210901881471714939L;
        private final List<SolrInputDocument> webgraphDocuments;
        public SolrVector() {
            super();
            this.webgraphDocuments = new ArrayList<>();
        }
        public void addWebgraphDocument(SolrInputDocument webgraphDocument) {
            this.webgraphDocuments.add(webgraphDocument);
        }
        public List<SolrInputDocument> getWebgraphDocuments() {
            return this.webgraphDocuments;
        }
    }

    public SolrVector yacy2solr(
            final Segment segment,
            final Map<String, Pattern> collections, final ResponseHeader responseHeader,
            final Document document, final Condenser condenser, final DigestURL referrerURL, final String language, final boolean setUnique,
            final WebgraphConfiguration webgraph, final String sourceName) {
        // we use the SolrCell design as index schema
        final SolrVector doc = new SolrVector();
        final DigestURL digestURL = document.dc_source();
        final boolean allAttr = this.isEmpty();
        final String url = this.addURIAttributes(doc, allAttr, digestURL);
        this.add(doc, CollectionSchema.content_type, new String[]{document.dc_format()}); // content_type (mime) is defined a schema field and we rely on it in some queries like imagequery (makes it mandatory, no need to check)

        final Set<ProcessType> processTypes = new LinkedHashSet<>();
        final String host = digestURL.getHost();

        final int crawldepth = document.getDepth();
        if ((allAttr || this.contains(CollectionSchema.crawldepth_i))) {
            CollectionSchema.crawldepth_i.add(doc, crawldepth);
        }

        if (allAttr || (this.contains(CollectionSchema.cr_host_chance_d) && this.contains(CollectionSchema.cr_host_count_i) && this.contains(CollectionSchema.cr_host_norm_i))) {
            processTypes.add(ProcessType.CITATION); // postprocessing needed
        }

        if (allAttr || this.contains(CollectionSchema.collection_sxt) && collections != null && collections.size() > 0) {
            final List<String> cs = new ArrayList<>();
            for (final Map.Entry<String, Pattern> e: collections.entrySet()) {
                if (e.getValue().matcher(url).matches()) cs.add(e.getKey());
            }
            this.add(doc, CollectionSchema.collection_sxt, cs);
        }

        final List<String> titles = document.titles();
        if (allAttr || this.contains(CollectionSchema.title)) {
            this.add(doc, CollectionSchema.title, titles);
            if ((allAttr || this.contains(CollectionSchema.title_exact_signature_l)) && titles.size() > 0) {
                this.add(doc, CollectionSchema.title_exact_signature_l, EnhancedTextProfileSignature.getSignatureLong(titles.get(0)));
            }

        }
        if (allAttr || this.contains(CollectionSchema.title_count_i)) this.add(doc, CollectionSchema.title_count_i, titles.size());
        if (allAttr || this.contains(CollectionSchema.title_chars_val)) {
            final ArrayList<Integer> cv = new ArrayList<>(titles.size());
            for (final String s: titles) cv.add(Integer.valueOf(s.length()));
            this.add(doc, CollectionSchema.title_chars_val, cv);
        }
        if (allAttr || this.contains(CollectionSchema.title_words_val)) {
            final ArrayList<Integer> cv = new ArrayList<>(titles.size());
            for (final String s: titles) cv.add(Integer.valueOf(CommonPattern.SPACES.split(s).length));
            this.add(doc, CollectionSchema.title_words_val, cv);
        }

        final String[] descriptions = document.dc_description();
        if (allAttr || this.contains(CollectionSchema.description_txt)) {
            this.add(doc, CollectionSchema.description_txt, descriptions);
            if ((allAttr || this.contains(CollectionSchema.description_exact_signature_l)) && descriptions != null && descriptions.length > 0) {
                this.add(doc, CollectionSchema.description_exact_signature_l, EnhancedTextProfileSignature.getSignatureLong(descriptions));
            }
        }
        if (allAttr || this.contains(CollectionSchema.description_count_i)) this.add(doc, CollectionSchema.description_count_i, descriptions.length);
        if (allAttr || this.contains(CollectionSchema.description_chars_val)) {
            final ArrayList<Integer> cv = new ArrayList<>(descriptions.length);
            for (final String s: descriptions) cv.add(Integer.valueOf(s.length()));
            this.add(doc, CollectionSchema.description_chars_val, cv);
        }
        if (allAttr || this.contains(CollectionSchema.description_words_val)) {
            final ArrayList<Integer> cv = new ArrayList<>(descriptions.length);
            for (final String s: descriptions) cv.add(Integer.valueOf(CommonPattern.SPACES.split(s).length));
            this.add(doc, CollectionSchema.description_words_val, cv);
        }

        if (allAttr || this.contains(CollectionSchema.author)) {
            String author = document.dc_creator();
            if (author == null || author.length() == 0) author = document.dc_publisher();
            this.add(doc, CollectionSchema.author, author);
        }
        if (allAttr || this.contains(CollectionSchema.last_modified)) {
            Date lastModified = responseHeader == null ? document.getLastModified() : responseHeader.lastModified();
            if (lastModified == null) {
                final long firstSeen = segment.getFirstSeenTime(digestURL.hash());
                if (firstSeen > 0) {
                    lastModified = new Date(firstSeen); // patch the date if we have seen the document earlier
                } else {
                    lastModified = new Date();
                }
            }
            if (document.getLastModified().before(lastModified)) {
                lastModified = document.getLastModified();
            }
            this.add(doc, CollectionSchema.last_modified, lastModified);
        }
        if (allAttr || this.contains(CollectionSchema.dates_in_content_dts) || this.contains(CollectionSchema.dates_in_content_count_i)) {
            final LinkedHashSet<Date> dates_in_content = condenser.dates_in_content;
            if (allAttr || this.contains(CollectionSchema.dates_in_content_count_i)) {
                this.add(doc, CollectionSchema.dates_in_content_count_i, dates_in_content.size());
            }
            if (dates_in_content.size() > 0 && (allAttr || this.contains(CollectionSchema.dates_in_content_dts))) {
                this.add(doc, CollectionSchema.dates_in_content_dts, dates_in_content.toArray(new Date[dates_in_content.size()]));
            }
        }
        if (allAttr || this.contains(CollectionSchema.keywords)) {
            final String keywords = document.dc_subject(' ');
            this.add(doc, CollectionSchema.keywords, keywords);
        }

        // unique-fields; these values must be corrected during postprocessing. (the following logic is !^ (not-xor) but I prefer to write it that way as it is)
        this.add(doc, CollectionSchema.http_unique_b, setUnique || UNIQUE_HEURISTIC_PREFER_HTTPS ? digestURL.isHTTPS() : digestURL.isHTTP()); // this must be corrected afterwards during storage!
        this.add(doc, CollectionSchema.www_unique_b, setUnique || host != null && (UNIQUE_HEURISTIC_PREFER_WWWPREFIX ? host.startsWith("www.") : !host.startsWith("www."))); // this must be corrected afterwards during storage!

        this.add(doc, CollectionSchema.exact_signature_l, condenser.exactSignature());
        this.add(doc, CollectionSchema.exact_signature_unique_b, true); // this must be corrected afterwards during storage!
        this.add(doc, CollectionSchema.exact_signature_copycount_i, 0); // this must be corrected afterwards during postprocessing!
        this.add(doc, CollectionSchema.fuzzy_signature_l, condenser.fuzzySignature());
        this.add(doc, CollectionSchema.fuzzy_signature_text_t, condenser.fuzzySignatureText());
        this.add(doc, CollectionSchema.fuzzy_signature_unique_b, true); // this must be corrected afterwards during storage!
        this.add(doc, CollectionSchema.fuzzy_signature_copycount_i, 0); // this must be corrected afterwards during postprocessing!
        if (this.contains(CollectionSchema.exact_signature_unique_b) || this.contains(CollectionSchema.exact_signature_copycount_i) ||
            this.contains(CollectionSchema.fuzzy_signature_l) || this.contains(CollectionSchema.fuzzy_signature_copycount_i) ||
            this.contains(CollectionSchema.http_unique_b) || this.contains(CollectionSchema.www_unique_b)) {
            processTypes.add(ProcessType.UNIQUE);
        }

        // get list of all links; they will be shrinked by urls that appear in other fields of the solr schema
        final LinkedHashMap<DigestURL,String> inboundLinks = document.inboundLinks();
        final LinkedHashMap<DigestURL,String> outboundLinks = document.outboundLinks();

        final Subgraph subgraph = new Subgraph(inboundLinks.size(), outboundLinks.size());
        int c = 0;
        final Object scraper = document.getScraperObject();
        boolean containsCanonical = false;
        DigestURL canonical = null;

        this.processIcons(doc, allAttr, inboundLinks, outboundLinks, document.getIcons().values());

        if (scraper instanceof ContentScraper) {
            final ContentScraper html = (ContentScraper) scraper;
            final List<ImageEntry> images = html.getImages();

            // header tags
            int h = 0;
            int f = 1;
            String[] hs;

            hs = html.getHeadlines(1); h = h | (hs.length > 0 ? f : 0); f = f * 2; this.add(doc, CollectionSchema.h1_txt, hs); this.add(doc, CollectionSchema.h1_i, hs.length);
            hs = html.getHeadlines(2); h = h | (hs.length > 0 ? f : 0); f = f * 2; this.add(doc, CollectionSchema.h2_txt, hs); this.add(doc, CollectionSchema.h2_i, hs.length);
            hs = html.getHeadlines(3); h = h | (hs.length > 0 ? f : 0); f = f * 2; this.add(doc, CollectionSchema.h3_txt, hs); this.add(doc, CollectionSchema.h3_i, hs.length);
            hs = html.getHeadlines(4); h = h | (hs.length > 0 ? f : 0); f = f * 2; this.add(doc, CollectionSchema.h4_txt, hs); this.add(doc, CollectionSchema.h4_i, hs.length);
            hs = html.getHeadlines(5); h = h | (hs.length > 0 ? f : 0); f = f * 2; this.add(doc, CollectionSchema.h5_txt, hs); this.add(doc, CollectionSchema.h5_i, hs.length);
            hs = html.getHeadlines(6); h = h | (hs.length > 0 ? f : 0); f = f * 2; this.add(doc, CollectionSchema.h6_txt, hs); this.add(doc, CollectionSchema.h6_i, hs.length);

            this.add(doc, CollectionSchema.htags_i, h);
            this.add(doc, CollectionSchema.schema_org_breadcrumb_i, html.breadcrumbCount());

            // meta tags: Open Graph properties
            String og;
            og = html.getMetas().get("og:title"); if (og != null) this.add(doc, CollectionSchema.opengraph_title_t, og);
            og = html.getMetas().get("og:type"); if (og != null) this.add(doc, CollectionSchema.opengraph_type_s, og);
            og = html.getMetas().get("og:url"); if (og != null) this.add(doc, CollectionSchema.opengraph_url_s, og);
            og = html.getMetas().get("og:image"); if (og != null) this.add(doc, CollectionSchema.opengraph_image_s, og);

            // noindex and nofollow attributes
            // from HTML (meta-tag in HTML header: robots)
            // and HTTP header (X-Robots-Tag property)
            // coded as binary value:
            // bit  0: "all" contained in html header meta
            // bit  1: "index" contained in html header meta
            // bit  2: "follow" contained in html header meta
            // bit  3: "noindex" contained in html header meta
            // bit  4: "nofollow" contained in html header meta
            // bit  5: "noarchive" contained in html header meta
            // bit  8: "all" contained in http header X-Robots-Tag
            // bit  9: "noindex" contained in http header X-Robots-Tag
            // bit 10: "nofollow" contained in http header X-Robots-Tag
            // bit 11: "noarchive" contained in http header X-Robots-Tag
            // bit 12: "nosnippet" contained in http header X-Robots-Tag
            // bit 13: "noodp" contained in http header X-Robots-Tag
            // bit 14: "notranslate" contained in http header X-Robots-Tag
            // bit 15: "noimageindex" contained in http header X-Robots-Tag
            // bit 16: "unavailable_after" contained in http header X-Robots-Tag
            int b = 0;
            String robots_meta = html.getMetas().get("robots");
            // this tag may have values: all, index, noindex, nofollow; see http://www.robotstxt.org/meta.html
            if (robots_meta != null) {
                robots_meta = robots_meta.toLowerCase(Locale.ROOT);
                if (robots_meta.indexOf("all",0) >= 0) b += 1;      // set bit 0
                if (robots_meta.indexOf("index",0) == 0 || robots_meta.indexOf(" index",0) >= 0 || robots_meta.indexOf(",index",0) >= 0 ) b += 2; // set bit 1
                if (robots_meta.indexOf("follow",0) == 0 || robots_meta.indexOf(" follow",0) >= 0 || robots_meta.indexOf(",follow",0) >= 0 ) b += 4; // set bit 2
                if (robots_meta.indexOf("noindex",0) >= 0) b += 8;  // set bit 3
                if (robots_meta.indexOf("nofollow",0) >= 0) b += 16; // set bit 4
                if (robots_meta.indexOf("noarchive",0) >= 0) b += 32; // set bit 5
            }
            final String x_robots_tag = responseHeader == null ? "" : responseHeader.getXRobotsTag();
            if (!x_robots_tag.isEmpty()) {
                // this tag may have values: all, noindex, nofollow, noarchive, nosnippet, noodp, notranslate, noimageindex, unavailable_after, none; see https://developers.google.com/webmasters/control-crawl-index/docs/robots_meta_tag?hl=de
                if (x_robots_tag.indexOf("all",0) >= 0) b += 1<<8;                // set bit 8
                if (x_robots_tag.indexOf("noindex",0) >= 0||x_robots_tag.indexOf("none",0) >= 0) b += 1<<9;   // set bit 9
                if (x_robots_tag.indexOf("nofollow",0) >= 0||x_robots_tag.indexOf("none",0) >= 0) b += 1<<10; // set bit 10
                if (x_robots_tag.indexOf("noarchive",0) >= 0) b += 1<<11;         // set bit 11
                if (x_robots_tag.indexOf("nosnippet",0) >= 0) b += 1<<12;         // set bit 12
                if (x_robots_tag.indexOf("noodp",0) >= 0) b += 1<<13;             // set bit 13
                if (x_robots_tag.indexOf("notranslate",0) >= 0) b += 1<<14;       // set bit 14
                if (x_robots_tag.indexOf("noimageindex",0) >= 0) b += 1<<15;      // set bit 15
                if (x_robots_tag.indexOf("unavailable_after",0) >= 0) b += 1<<16; // set bit 16
            }
            this.add(doc, CollectionSchema.robots_i, b);

            // meta tags: generator
            final String generator = html.getMetas().get("generator");
            if (generator != null) this.add(doc, CollectionSchema.metagenerator_t, generator);

            // bold, italic
            final String[] bold = html.getBold();
            this.add(doc, CollectionSchema.boldcount_i, bold.length);
            if (bold.length > 0) {
                this.add(doc, CollectionSchema.bold_txt, bold);
                if (allAttr || this.contains(CollectionSchema.bold_val)) {
                    this.add(doc, CollectionSchema.bold_val, html.getBoldCount(bold));
                }
            }
            final String[] italic = html.getItalic();
            this.add(doc, CollectionSchema.italiccount_i, italic.length);
            if (italic.length > 0) {
                this.add(doc, CollectionSchema.italic_txt, italic);
                if (allAttr || this.contains(CollectionSchema.italic_val)) {
                    this.add(doc, CollectionSchema.italic_val, html.getItalicCount(italic));
                }
            }
            final String[] underline = html.getUnderline();
            this.add(doc, CollectionSchema.underlinecount_i, underline.length);
            if (underline.length > 0) {
                this.add(doc, CollectionSchema.underline_txt, underline);
                if (allAttr || this.contains(CollectionSchema.underline_val)) {
                    this.add(doc, CollectionSchema.underline_val, html.getUnderlineCount(underline));
                }
            }
            final String[] li = html.getLi();
            this.add(doc, CollectionSchema.licount_i, li.length);
            if (li.length > 0) this.add(doc, CollectionSchema.li_txt, li);

            final String[] dt = html.getDt();
            this.add(doc, CollectionSchema.dtcount_i, dt.length);
            if (dt.length > 0) this.add(doc, CollectionSchema.dt_txt, dt);

            final String[] dd = html.getDd();
            this.add(doc, CollectionSchema.ddcount_i, dd.length);
            if (dd.length > 0) this.add(doc, CollectionSchema.dd_txt, dd);

            final List<Date> startDates = html.getStartDates();
            if (startDates.size() > 0) this.add(doc, CollectionSchema.startDates_dts, startDates.toArray(new Date[startDates.size()]));
            final List<Date> endDates = html.getEndDates();
            if (endDates.size() > 0) this.add(doc, CollectionSchema.endDates_dts, endDates.toArray(new Date[endDates.size()]));

            final List<String> articles = html.getArticles();
            this.add(doc, CollectionSchema.articlecount_i, articles.size());
            if (articles.size() > 0) this.add(doc, CollectionSchema.article_txt, articles);

            // images
            this.processImages(doc, allAttr, inboundLinks, outboundLinks, images);

            // style sheets
            if (allAttr || this.contains(CollectionSchema.css_tag_sxt)) {
                final Map<DigestURL, String> csss = html.getCSS();
                final String[] css_tag = new String[csss.size()];
                final String[] css_url = new String[csss.size()];
                c = 0;
                for (final Map.Entry<DigestURL, String> entry: csss.entrySet()) {
                    final String cssurl = entry.getKey().toNormalform(false);
                    inboundLinks.remove(entry.getKey());
                    outboundLinks.remove(entry.getKey());
                    css_tag[c] =
                        "<link rel=\"stylesheet\" type=\"text/css\" media=\"" + entry.getValue() + "\"" +
                        " href=\""+ cssurl + "\" />";
                    css_url[c] = cssurl;
                    c++;
                }
                this.add(doc, CollectionSchema.csscount_i, css_tag.length);
                if (css_tag.length > 0) this.add(doc, CollectionSchema.css_tag_sxt, css_tag);
                if (css_url.length > 0) this.add(doc, CollectionSchema.css_url_sxt, css_url);
            }

            // Scripts
            if (allAttr || this.contains(CollectionSchema.scripts_sxt)) {
                final Set<AnchorURL> scriptss = html.getScript();
                final String[] scripts = new String[scriptss.size()];
                c = 0;
                for (final AnchorURL u: scriptss) {
                    inboundLinks.remove(u);
                    outboundLinks.remove(u);
                    scripts[c++] = u.toNormalform(false);
                }
                this.add(doc, CollectionSchema.scriptscount_i, scripts.length);
                if (scripts.length > 0) this.add(doc, CollectionSchema.scripts_sxt, scripts);
            }

            // Frames
            if (allAttr || this.contains(CollectionSchema.frames_sxt)) {
                final Set<AnchorURL> framess = html.getFrames();
                final String[] frames = new String[framess.size()];
                c = 0;
                for (final AnchorURL u: framess) {
                    inboundLinks.remove(u);
                    outboundLinks.remove(u);
                    frames[c++] = u.toNormalform(false);
                }
                this.add(doc, CollectionSchema.framesscount_i, frames.length);
                if (frames.length > 0) {
                    this.add(doc, CollectionSchema.frames_sxt, frames);
                    //webgraph.addEdges(subgraph, digestURI, responseHeader, collections, crawldepth, alllinks, images, true, framess, citations); // add here because links have been removed from remaining inbound/outbound
                }
            }

            // IFrames
            if (allAttr || this.contains(CollectionSchema.iframes_sxt)) {
                final Set<AnchorURL> iframess = html.getIFrames();
                final String[] iframes = new String[iframess.size()];
                c = 0;
                for (final AnchorURL u: iframess) {
                    inboundLinks.remove(u);
                    outboundLinks.remove(u);
                    iframes[c++] = u.toNormalform(false);
                }
                this.add(doc, CollectionSchema.iframesscount_i, iframes.length);
                if (iframes.length > 0) {
                    this.add(doc, CollectionSchema.iframes_sxt, iframes);
                    //webgraph.addEdges(subgraph, digestURI, responseHeader, collections, crawldepth, alllinks, images, true, iframess, citations); // add here because links have been removed from remaining inbound/outbound
                }
            }

            // canonical tag
            if (allAttr || this.contains(CollectionSchema.canonical_s)) {
                canonical = html.getCanonical();
                // if there is no canonical in the html then look into the http header:
                if (canonical == null && responseHeader != null) {
                    String link = responseHeader.get("Link", null);
                    int p;
                    if (link != null && ((p = link.indexOf("rel=\"canonical\"")) > 0)) {
                        link = link.substring(0, p).trim();
                        p = link.indexOf('<');
                        final int q = link.lastIndexOf('>');
                        if (p >= 0 && q > 0) {
                            link = link.substring(p + 1, q);
                            try {
                                canonical = new DigestURL(link);
                            } catch (final MalformedURLException e) {}
                        }
                    }
                }
                if (canonical != null) {
                    containsCanonical = true;
                    inboundLinks.remove(canonical);
                    outboundLinks.remove(canonical);
                    this.add(doc, CollectionSchema.canonical_s, canonical.toNormalform(false));
                    // set a flag if this is equal to sku
                    if (this.contains(CollectionSchema.canonical_equal_sku_b)) {
                        this.add(doc, CollectionSchema.canonical_equal_sku_b, canonical.equals(digestURL));
                    }
                }
            }

            // meta refresh tag
            if (allAttr || this.contains(CollectionSchema.refresh_s)) {
                final String refresh = html.getRefreshPath();
                if (refresh != null && refresh.length() > 0) {
                    MultiProtocolURL refreshURL;
                    try {
                        refreshURL = refresh.startsWith("http") ? new MultiProtocolURL(html.getRefreshPath()) : new MultiProtocolURL(digestURL, html.getRefreshPath());
                        if (refreshURL != null) {
                            inboundLinks.remove(refreshURL);
                            outboundLinks.remove(refreshURL);
                            this.add(doc, CollectionSchema.refresh_s, refreshURL.toNormalform(false));
                        }
                    } catch (final MalformedURLException e) {
                        this.add(doc, CollectionSchema.refresh_s, refresh);
                    }
                }
            }

            // flash embedded
            if (allAttr || this.contains(CollectionSchema.flash_b)) {
                final MultiProtocolURL[] flashURLs = html.getFlash();
                for (final MultiProtocolURL u: flashURLs) {
                    // remove all flash links from ibound/outbound links
                    inboundLinks.remove(u);
                    outboundLinks.remove(u);
                }
                this.add(doc, CollectionSchema.flash_b, flashURLs.length > 0);
            }

            // generic evaluation pattern
            for (final String model: html.getEvaluationModelNames()) {
                if (allAttr || this.contains("ext_" + model + "_txt")) {
                    final String[] scorenames = html.getEvaluationModelScoreNames(model);
                    if (scorenames.length > 0) {
                        this.add(doc, CollectionSchema.valueOf("ext_" + model + "_txt"), scorenames);
                        this.add(doc, CollectionSchema.valueOf("ext_" + model + "_val"), html.getEvaluationModelScoreCounts(model, scorenames));
                    }
                }
            }

            // response time
            this.add(doc, CollectionSchema.responsetime_i, responseHeader == null ? 0 : Integer.parseInt(responseHeader.get(HeaderFramework.RESPONSE_TIME_MILLIS, "0")));

            // hreflang link tag, see http://support.google.com/webmasters/bin/answer.py?hl=de&answer=189077
            if (allAttr || (this.contains(CollectionSchema.hreflang_url_sxt) && this.contains(CollectionSchema.hreflang_cc_sxt))) {
                final String[] ccs = new String[html.getHreflang().size()];
                final String[] urls = new String[html.getHreflang().size()];
                c = 0;
                for (final Map.Entry<String, DigestURL> e: html.getHreflang().entrySet()) {
                    ccs[c] = e.getKey();
                    urls[c] = e.getValue().toNormalform(true);
                    c++;
                }
                this.add(doc, CollectionSchema.hreflang_cc_sxt, ccs);
                this.add(doc, CollectionSchema.hreflang_url_sxt, urls);
            }

            // page navigation url, see http://googlewebmastercentral.blogspot.de/2011/09/pagination-with-relnext-and-relprev.html
            if (allAttr || (this.contains(CollectionSchema.navigation_url_sxt) && this.contains(CollectionSchema.navigation_type_sxt))) {
                final String[] navs = new String[html.getNavigation().size()];
                final String[] urls = new String[html.getNavigation().size()];
                c = 0;
                for (final Map.Entry<String, DigestURL> e: html.getNavigation().entrySet()) {
                    navs[c] = e.getKey();
                    urls[c] = e.getValue().toNormalform(true);
                    c++;
                }
                this.add(doc, CollectionSchema.navigation_type_sxt, navs);
                this.add(doc, CollectionSchema.navigation_url_sxt, urls);

            }

            // publisher url as defined in http://support.google.com/plus/answer/1713826?hl=de
            if (allAttr || this.contains(CollectionSchema.publisher_url_s) && html.getPublisherLink() != null) {
                this.add(doc, CollectionSchema.publisher_url_s, html.getPublisherLink().toNormalform(true));
            }
        }

        if (scraper instanceof DCEntry) {
            // the document was created with a surrogate parsing; overwrite all md: -entries to Solr
            final DCEntry dcentry = (DCEntry) scraper;
            for (final Map.Entry<String, String[]> entry: dcentry.getMap().entrySet()) {
                final String tag = entry.getKey();
                if (!tag.startsWith("md:") || tag.length() < 4) continue; // md: is a YaCy internal identifier for metadata in surrugate.xml files ( md:SOLR_FIELDNAME )
                final CollectionSchema solr_field = CollectionSchema.valueOf(tag.substring(3));
                if (solr_field == null) continue;
                final String[] values = entry.getValue();
                if (values == null || values.length == 0) continue;
                if (allAttr || this.contains(solr_field)) {
                    this.add(doc, solr_field, values);
                }
            }
        }

        String content = document.getTextString();

        // handle image source meta data
        if (document.getContentDomain() == ContentDomain.IMAGE) {
            // add image pixel sizes if enabled in index
            // get values if as least one of the size fields is enabled in index
            if (allAttr || (this.contains(CollectionSchema.images_height_val) || this.contains(CollectionSchema.images_width_val)) || this.contains(CollectionSchema.images_pixel_val)) {
                // add image pixel size if known
                final Iterator<ImageEntry> imgit = document.getImages().values().iterator();
                final List<Integer> heights = new ArrayList<>();
                final List<Integer> widths = new ArrayList<>();
                final List<Integer> pixels = new ArrayList<>();
                while (imgit.hasNext()) {
                    final ImageEntry img = imgit.next();
                    final int imgpixels = (img.height() < 0 || img.width() < 0) ? -1 : img.height() * img.width();
                    if (imgpixels > 0) {
                        heights.add(img.height());
                        widths.add(img.width());
                        pixels.add(imgpixels);
                    }
                }
                if (heights.size() > 0) { // add to index document
                    this.add(doc, CollectionSchema.images_height_val, heights); // add() checks if field is enabled in index
                    this.add(doc, CollectionSchema.images_width_val, widths);
                    this.add(doc, CollectionSchema.images_pixel_val, pixels);
                }
            }

            if (allAttr || this.contains(CollectionSchema.images_text_t))  {
                this.add(doc, CollectionSchema.images_text_t, content); // the content may contain the exif data from the image parser
                content = digestURL.toTokens(); // remove all other entry but the url tokens
            }
        }

        // content (must be written after special parser data, since this can influence the content)
        if (allAttr || this.contains(CollectionSchema.text_t)) this.add(doc, CollectionSchema.text_t, content);
        if (allAttr || this.contains(CollectionSchema.wordcount_i)) {
            if (content.length() == 0) {
                this.add(doc, CollectionSchema.wordcount_i, 0);
            } else {
                int contentwc = 1;
                for (int i = content.length() - 1; i >= 0; i--) if (content.charAt(i) == ' ') contentwc++;
                this.add(doc, CollectionSchema.wordcount_i, contentwc);
            }
        }

        // statistics about the links
        if (allAttr || this.contains(CollectionSchema.linkscount_i)) this.add(doc, CollectionSchema.linkscount_i, inboundLinks.size() + outboundLinks.size());
        if (allAttr || this.contains(CollectionSchema.linksnofollowcount_i)) this.add(doc, CollectionSchema.linksnofollowcount_i, document.inboundLinkNofollowCount() + document.outboundLinkNofollowCount());
        if (allAttr || this.contains(CollectionSchema.inboundlinkscount_i)) this.add(doc, CollectionSchema.inboundlinkscount_i, inboundLinks.size());
        if (allAttr || this.contains(CollectionSchema.inboundlinksnofollowcount_i)) this.add(doc, CollectionSchema.inboundlinksnofollowcount_i, document.inboundLinkNofollowCount());
        if (allAttr || this.contains(CollectionSchema.outboundlinkscount_i)) this.add(doc, CollectionSchema.outboundlinkscount_i, outboundLinks.size());
        if (allAttr || this.contains(CollectionSchema.outboundlinksnofollowcount_i)) this.add(doc, CollectionSchema.outboundlinksnofollowcount_i, document.outboundLinkNofollowCount());

        // create a subgraph
        final Boolean canonical_equal_sku = canonical == null ? null : canonical.toNormalform(true).equals(url);
        if (webgraph != null && (!containsCanonical || (canonical_equal_sku != null && (canonical_equal_sku.booleanValue())))) {
            // a document with canonical tag should not get a webgraph relation, because that belongs to the canonical document
            final List<SolrInputDocument> edges = webgraph.getEdges(subgraph, digestURL, responseHeader, collections, crawldepth, processTypes, document.getHyperlinks().keySet(), sourceName);
            // this also enriched the subgraph
            doc.webgraphDocuments.addAll(edges);
        } else {
            if (allAttr ||
                this.contains(CollectionSchema.inboundlinks_protocol_sxt) ||
                this.contains(CollectionSchema.inboundlinks_urlstub_sxt) ||
                this.contains(CollectionSchema.inboundlinks_anchortext_txt) ||
                this.contains(CollectionSchema.outboundlinks_protocol_sxt) ||
                this.contains(CollectionSchema.outboundlinks_urlstub_sxt) ||
                this.contains(CollectionSchema.outboundlinks_anchortext_txt)) {
                for (final AnchorURL target_url: document.getHyperlinks().keySet()) {
                    enrichSubgraph(subgraph, digestURL, target_url);
                }
            }
        }

        // attach the subgraph content
        if (allAttr || this.contains(CollectionSchema.inboundlinks_protocol_sxt)) this.add(doc, CollectionSchema.inboundlinks_protocol_sxt, protocolList2indexedList(subgraph.urlProtocols[0]));
        if (allAttr || this.contains(CollectionSchema.inboundlinks_urlstub_sxt)) this.add(doc, CollectionSchema.inboundlinks_urlstub_sxt, subgraph.urlStubs[0]);
        if (allAttr || this.contains(CollectionSchema.inboundlinks_anchortext_txt)) this.add(doc, CollectionSchema.inboundlinks_anchortext_txt, subgraph.urlAnchorTexts[0]);
        if (allAttr || this.contains(CollectionSchema.outboundlinks_protocol_sxt)) this.add(doc, CollectionSchema.outboundlinks_protocol_sxt, protocolList2indexedList(subgraph.urlProtocols[1]));
        if (allAttr || this.contains(CollectionSchema.outboundlinks_urlstub_sxt)) this.add(doc, CollectionSchema.outboundlinks_urlstub_sxt, subgraph.urlStubs[1]);
        if (allAttr || this.contains(CollectionSchema.outboundlinks_anchortext_txt)) this.add(doc, CollectionSchema.outboundlinks_anchortext_txt, subgraph.urlAnchorTexts[1]);

        // charset
        if (allAttr || this.contains(CollectionSchema.charset_s)) this.add(doc, CollectionSchema.charset_s, document.getCharset());

        // coordinates
        if (document.lat() != 0.0 && document.lon() != 0.0) {
            if (allAttr || this.contains(CollectionSchema.coordinate_p)) this.add(doc, CollectionSchema.coordinate_p, Double.toString(document.lat()) + "," + Double.toString(document.lon()));
        }
        if (allAttr || this.contains(CollectionSchema.httpstatus_i)) this.add(doc, CollectionSchema.httpstatus_i, responseHeader == null ? 200 : responseHeader.getStatusCode());

        // fields that were additionally in URIMetadataRow
        final Date loadDate = new Date();
        Date modDate = responseHeader == null ? new Date() : responseHeader.lastModified();
        if (modDate.getTime() > loadDate.getTime()) modDate = loadDate;
        final int size = (int) Math.max(document.dc_source().length(), responseHeader == null ? 0 : responseHeader.getContentLength());
        if (allAttr || this.contains(CollectionSchema.load_date_dt)) this.add(doc, CollectionSchema.load_date_dt, loadDate);
        if (allAttr || this.contains(CollectionSchema.fresh_date_dt)) this.add(doc, CollectionSchema.fresh_date_dt, new Date(loadDate.getTime() + Math.max(0, loadDate.getTime() - modDate.getTime()) / 2)); // freshdate, computed with Proxy-TTL formula
        if ((allAttr || this.contains(CollectionSchema.referrer_id_s)) && referrerURL != null) this.add(doc, CollectionSchema.referrer_id_s, ASCII.String(referrerURL.hash()));
        //if (allAttr || contains(SolrField.md5_s)) add(solrdoc, SolrField.md5_s, new byte[0]);
        if (allAttr || this.contains(CollectionSchema.publisher_t)) this.add(doc, CollectionSchema.publisher_t, document.dc_publisher());
        if ((allAttr || this.contains(CollectionSchema.language_s)) && language != null) this.add(doc, CollectionSchema.language_s, language);
        if (allAttr || this.contains(CollectionSchema.size_i)) this.add(doc, CollectionSchema.size_i, size);
        if (allAttr || this.contains(CollectionSchema.audiolinkscount_i)) this.add(doc, CollectionSchema.audiolinkscount_i, document.getAudiolinks().size());
        if (allAttr || this.contains(CollectionSchema.videolinkscount_i)) this.add(doc, CollectionSchema.videolinkscount_i, document.getVideolinks().size());
        if (allAttr || this.contains(CollectionSchema.applinkscount_i)) this.add(doc, CollectionSchema.applinkscount_i, document.getApplinks().size());

        // document post-processing
        if ((allAttr || this.contains(CollectionSchema.process_sxt)) && processTypes.size() > 0) {
            final List<String> p = new ArrayList<>();
            for (final ProcessType t: processTypes) p.add(t.name());
            this.add(doc, CollectionSchema.process_sxt, p);
            if (allAttr || this.contains(CollectionSchema.harvestkey_s)) {
                this.add(doc, CollectionSchema.harvestkey_s, sourceName);
            }
        }

        // document enrichments (synonyms, facets)
        this.enrich(doc, condenser.synonyms(), document.getGenericFacets());
        return doc;
    }

	/**
	 * Add icons metadata to Solr doc when corresponding schema attributes are
	 * enabled.
	 *
	 * @param doc
	 *            solr document to fill. Must not be null.
	 * @param allAttr
	 *            all attributes are enabled.
	 * @param icons
	 *            document icon entries.
	 */
	private void processIcons(SolrInputDocument doc, boolean allAttr, Collection<IconEntry> icons) {
		this.processIcons(doc, allAttr, null, null, icons);
	}

	/**
	 * Add icons metadata to Solr doc when corresponding schema attributes are
	 * enabled. Remove icons urls from inboudLinks and outboundLinks.
	 *
	 * @param doc
	 *            solr document to fill. Must not be null.
	 * @param allAttr
	 *            all attributes are enabled.
	 * @param inboundLinks
	 *            all document inbound links.
	 * @param outboundLinks
	 *            all document outbound links.
	 * @param icons
	 *            document icon entries.
	 */
	private void processIcons(SolrInputDocument doc, boolean allAttr, LinkedHashMap<DigestURL, String> inboundLinks,
			LinkedHashMap<DigestURL, String> outboundLinks, Collection<IconEntry> icons) {
		if (icons != null) {
			final List<String> protocols = new ArrayList<>(icons.size());
			final String[] sizes = new String[icons.size()];
			final String[] stubs = new String[icons.size()];
			final String[] rels = new String[icons.size()];
			int i = 0;
			/* Prepare solr field values */
			for (final IconEntry ie : icons) {
				final DigestURL url = ie.getUrl();

				if(inboundLinks != null) {
					inboundLinks.remove(url);
				}
				if(outboundLinks != null) {
					outboundLinks.remove(url);
				}

				final String protocol = url.getProtocol();
				protocols.add(protocol);

				/*
				 * There may be multiple sizes and multiple rels for one icon :
				 * we store this as flat string as currently solr doesn't
				 * support multidimensionnal array fields
				 */
				sizes[i] = ie.sizesToString();
				stubs[i] = url.toString().substring(protocol.length() + 3);
				rels[i] = ie.relToString();

				i++;
			}
			if (allAttr || this.contains(CollectionSchema.icons_protocol_sxt)) {
				this.add(doc, CollectionSchema.icons_protocol_sxt, protocolList2indexedList(protocols));
			}
			if (allAttr || this.contains(CollectionSchema.icons_urlstub_sxt)) {
				this.add(doc, CollectionSchema.icons_urlstub_sxt, stubs);
			}
			if (allAttr || this.contains(CollectionSchema.icons_rel_sxt)) {
				this.add(doc, CollectionSchema.icons_rel_sxt, rels);
			}
			if (allAttr || this.contains(CollectionSchema.icons_sizes_sxt)) {
				this.add(doc, CollectionSchema.icons_sizes_sxt, sizes);
			}
		}
	}

    /**
     * Add images metadata to Solr doc when corresponding schema attributes are enabled.
     * Remove images urls from inboudLinks and outboundLinks.
     * @param doc solr document to fill
     * @param allAttr all attributes are enabled
     * @param inboundLinks all document inbound links
     * @param outboundLinks all document outbound links
     * @param images document images
     */
	private void processImages(SolrVector doc, boolean allAttr, LinkedHashMap<DigestURL, String> inboundLinks,
			LinkedHashMap<DigestURL, String> outboundLinks, List<ImageEntry> images) {
		final ArrayList<String> imgprots = new ArrayList<>(images.size());
		final Integer[] imgheights = new Integer[images.size()];
		final Integer[] imgwidths = new Integer[images.size()];
		final Integer[] imgpixels = new Integer[images.size()];
		final String[] imgstubs = new String[images.size()];
		final String[] imgalts  = new String[images.size()];
		int withalt = 0;
		int i = 0;
		final LinkedHashSet<String> images_text_map = new LinkedHashSet<>();
		/* Prepare flat solr field values */
		for (final ImageEntry ie: images) {
		    final MultiProtocolURL uri = ie.url();
		    inboundLinks.remove(uri);
		    outboundLinks.remove(uri);
		    imgheights[i] = ie.height();
		    imgwidths[i] = ie.width();
		    imgpixels[i] = ie.height() < 0 || ie.width() < 0 ? -1 : ie.height() * ie.width();
		    final String protocol = uri.getProtocol();
		    imgprots.add(protocol);
		    imgstubs[i] = uri.toString().substring(protocol.length() + 3);
		    imgalts[i] = ie.alt();
		    for (final String it: CommonPattern.SPACE.split(uri.toTokens())) images_text_map.add(it);
		    if (ie.alt() != null && ie.alt().length() > 0) {
		        final SentenceReader sr = new SentenceReader(ie.alt());
		        while (sr.hasNext()) images_text_map.add(sr.next().toString());
		        withalt++;
		    }
		    i++;
		}
		final StringBuilder images_text = new StringBuilder(images_text_map.size() * 6 + 1);
		for (final String s: images_text_map) images_text.append(s.trim()).append(' ');
		if (allAttr || this.contains(CollectionSchema.imagescount_i)) this.add(doc, CollectionSchema.imagescount_i, images.size());
		if (allAttr || this.contains(CollectionSchema.images_protocol_sxt)) this.add(doc, CollectionSchema.images_protocol_sxt, protocolList2indexedList(imgprots));
		if (allAttr || this.contains(CollectionSchema.images_urlstub_sxt)) this.add(doc, CollectionSchema.images_urlstub_sxt, imgstubs);
		if (allAttr || this.contains(CollectionSchema.images_alt_sxt)) this.add(doc, CollectionSchema.images_alt_sxt, imgalts);
		if (allAttr || this.contains(CollectionSchema.images_height_val)) this.add(doc, CollectionSchema.images_height_val, imgheights);
		if (allAttr || this.contains(CollectionSchema.images_width_val)) this.add(doc, CollectionSchema.images_width_val, imgwidths);
		if (allAttr || this.contains(CollectionSchema.images_pixel_val)) this.add(doc, CollectionSchema.images_pixel_val, imgpixels);
		if (allAttr || this.contains(CollectionSchema.images_withalt_i)) this.add(doc, CollectionSchema.images_withalt_i, withalt);
		if (allAttr || this.contains(CollectionSchema.images_text_t)) this.add(doc, CollectionSchema.images_text_t, images_text.toString().trim());
	}

    /**
     * attach additional information to the document to enable navigation features
     * @param doc the document to be enriched
     * @param synonyms a list of synonyms detected for the text content
     * @param genericFacets a map where the key is the navigator name and the value is the set of attributes names
     */
    public void enrich(SolrInputDocument doc, List<String> synonyms, Map<String, Set<String>> genericFacets) {
        this.remove(doc, CollectionSchema.vocabularies_sxt); // delete old values
        for (final SolrInputField sif: doc) {
            if (sif.getName().startsWith(CollectionSchema.VOCABULARY_PREFIX)) this.remove(doc, sif.getName());
        }
        if (this.isEmpty() || this.contains(CollectionSchema.vocabularies_sxt)) {
            // write generic navigation
            // there are no pre-defined solr fields for navigation because the vocabulary is generic
            // we use dynamically allocated solr fields for this.
            // It must be a multi-value string/token field, therefore we use _sxt extensions for the field names

            // add to genericFacets the probabilistic categories
            final String text = (String) doc.getFieldValue(CollectionSchema.text_t.getSolrFieldName());
            final Map<String, String> classification = ProbabilisticClassifier.getClassification(text);
            for (final Map.Entry<String, String> entry: classification.entrySet()) {
                final Set<String> facetAttrbutes = new HashSet<>();
                facetAttrbutes.add(entry.getValue());
                genericFacets.put(entry.getKey(), facetAttrbutes);
            }

            // compute the document field values
            final List<String> vocabularies = new ArrayList<>();
            for (final Map.Entry<String, Set<String>> facet: genericFacets.entrySet()) {
                final String facetName = facet.getKey();
                final Set<String> facetValues = facet.getValue();
                final int count = facetValues.size();
                if (count == 0) continue;
                final int logcount = (int) (Math.log(count) / Math.log(2));
                final Integer[] counts = new Integer[logcount + 1]; for (int i = 0; i <= logcount; i++) counts[i] = i;
                doc.setField(CollectionSchema.VOCABULARY_PREFIX + facetName + CollectionSchema.VOCABULARY_TERMS_SUFFIX, facetValues.toArray(new String[count]));
                doc.setField(CollectionSchema.VOCABULARY_PREFIX + facetName + CollectionSchema.VOCABULARY_COUNT_SUFFIX, facetValues.size());
                doc.setField(CollectionSchema.VOCABULARY_PREFIX + facetName + CollectionSchema.VOCABULARY_LOGCOUNT_SUFFIX, logcount);
                doc.setField(CollectionSchema.VOCABULARY_PREFIX + facetName + CollectionSchema.VOCABULARY_LOGCOUNTS_SUFFIX, counts);
                vocabularies.add(facetName);
            }
            if (vocabularies.size() > 0) this.add(doc, CollectionSchema.vocabularies_sxt, vocabularies);
        }
        this.remove(doc, CollectionSchema.synonyms_sxt); // delete old values
        if (this.isEmpty() || this.contains(CollectionSchema.synonyms_sxt)) {
            if (synonyms.size() > 0) this.add(doc, CollectionSchema.synonyms_sxt, synonyms);
        }
    }

    public static boolean postprocessingRunning   = false;
    public static String  postprocessingActivity  = "";
    // if started, the following values are assigned
    public static long  postprocessingStartTime = 0; // the start time for the processing; not started = 0
    public static int   postprocessingCollection1Count = 0; // number of documents to be processed
    public static int   postprocessingWebgraphCount = 0; // number of documents to be processed

    public static final String collection1query(final Segment segment, final String harvestkey) {
        return (harvestkey == null || !segment.fulltext().getDefaultConfiguration().contains(CollectionSchema.harvestkey_s) ?
                       "" : CollectionSchema.harvestkey_s.getSolrFieldName() + ":\"" + harvestkey + "\" AND ") +
               CollectionSchema.process_sxt.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM;
    }
    public static final String webgraphquery(final Segment segment, final String harvestkey) {
        return (harvestkey == null || !segment.fulltext().getWebgraphConfiguration().contains(WebgraphSchema.harvestkey_s) ?
                       "" : WebgraphSchema.harvestkey_s.getSolrFieldName() + ":\"" + harvestkey + "\" AND ") +
               WebgraphSchema.process_sxt.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM;
    }

    /**
     * Performs post-processing steps for all entries that have a process tag assigned
     * @param segment Solr segment. Must not be null.
     * @param rrCache reference report cache for the segment.
     * @param harvestkey key from a harvest process, used to mark documents needing post-processing
     * @param byPartialUpdate when true, perform partial updates on documents
     * @return the number of post processed documents
     */
    public int postprocessing(final Segment segment, final ReferenceReportCache rrCache, final String harvestkey, final boolean byPartialUpdate) {
        if (!this.contains(CollectionSchema.process_sxt)) return 0;
        if (!segment.connectedCitation() && !segment.fulltext().useWebgraph()) return 0;
        final SolrConnector collectionConnector = segment.fulltext().getDefaultConnector();
        collectionConnector.commit(false); // make sure that we have latest information that can be found
        if (segment.fulltext().useWebgraph()) segment.fulltext().getWebgraphConnector().commit(false);
        final CollectionConfiguration collection = segment.fulltext().getDefaultConfiguration();
        final WebgraphConfiguration webgraph = segment.fulltext().getWebgraphConfiguration();

        // calculate the number of documents to be processed
        final String collection1query = collection1query(segment, harvestkey);
        final String webgraphquery = webgraphquery(segment, harvestkey);
        postprocessingRunning = true;
        postprocessingStartTime = System.currentTimeMillis();
        postprocessingActivity = "collecting counts";
        ConcurrentLog.info("CollectionConfiguration", postprocessingActivity);
        try {
            postprocessingCollection1Count = (int) collectionConnector.getCountByQuery("{!cache=false}" + collection1query);
            postprocessingWebgraphCount = segment.fulltext().useWebgraph() ? (int) segment.fulltext().getWebgraphConnector().getCountByQuery("{!cache=false}" + webgraphquery) : 0;
        } catch (final IOException e) {
            postprocessingCollection1Count = -1;
            postprocessingWebgraphCount = -1;
        }

        postprocessingActivity = "create ranking map";
        ConcurrentLog.info("CollectionConfiguration", postprocessingActivity);
        final boolean shallComputeCR = (segment.fulltext().useWebgraph() &&
                ((webgraph.contains(WebgraphSchema.source_id_s) && webgraph.contains(WebgraphSchema.source_cr_host_norm_i)) ||
                        (webgraph.contains(WebgraphSchema.target_id_s) && webgraph.contains(WebgraphSchema.target_cr_host_norm_i))) ||
                      (collection.contains(CollectionSchema.cr_host_count_i) &&
                       collection.contains(CollectionSchema.cr_host_chance_d) &&
                       collection.contains(CollectionSchema.cr_host_norm_i)));
        // create the ranking map
        final Map<String, CRV> rankings;
        if(shallComputeCR) {
            // collect hosts from index which shall take part in citation computation
            postprocessingActivity = "collecting host facets for collection";
            ConcurrentLog.info("CollectionConfiguration", postprocessingActivity);
            ReversibleScoreMap<String> collection1hosts;
            try {
                final Map<String, ReversibleScoreMap<String>> hostfacet = collectionConnector.getFacets("{!cache=false}" + collection1query, 10000000, CollectionSchema.host_s.getSolrFieldName());
                collection1hosts = hostfacet.get(CollectionSchema.host_s.getSolrFieldName());
            } catch (final IOException e2) {
                ConcurrentLog.logException(e2);
                collection1hosts = new ClusteredScoreMap<>(true);
            }

        	rankings = this.createRankingMap(segment, rrCache, collectionConnector, collection1hosts);
        } else {
        	rankings = new ConcurrentHashMap<>();
        }

        // process all documents at the webgraph for the outgoing links of this document
        final AtomicInteger allcount = new AtomicInteger(0);
        if (segment.fulltext().useWebgraph() && shallComputeCR) {
            this.postprocessWebgraph(segment, webgraph, webgraphquery, rankings, allcount);
        }

        // process all documents in collection
        this.postprocessDocuments(segment, rrCache, harvestkey, byPartialUpdate, collectionConnector, collection,
				collection1query, rankings, allcount);


        postprocessingCollection1Count = 0;
        postprocessingWebgraphCount = 0;
        postprocessingActivity = "postprocessing terminated";
        ConcurrentLog.info("CollectionConfiguration", postprocessingActivity);
        postprocessingRunning = false;
        return allcount.get();
    }

    /**
     * Performs postprocessing steps on the main documents dollection.
     * @param segment Solr segment.
     * @param rrCache reference report cache for the segment.
     * @param harvestkey key from a harvest process, used to mark documents needing post-processing
     * @param byPartialUpdate when true, perform partial updates on documents
     * @param collectionConnector connector to the main Solr collection
     * @param collection schema configuration for the collection
     * @param collection1query query used to harvest items to postprocess in the main collection
     * @param rankings postprocessed rankings
     * @param allcount global postprocessed documents count
     */
	private void postprocessDocuments(final Segment segment, final ReferenceReportCache rrCache,
			final String harvestkey, final boolean byPartialUpdate, final SolrConnector collectionConnector,
			final CollectionConfiguration collection, final String collection1query, final Map<String, CRV> rankings,
			final AtomicInteger allcount) {
		final Map<String, Long> hostExtentCache = new HashMap<>(); // a mapping from the host id to the number of documents which contain this host-id
        final Set<String> uniqueURLs = ConcurrentHashMap.newKeySet(); // will be used in a concurrent environment
        final Set<String> localOmitFields = new HashSet<>();
        localOmitFields.add(CollectionSchema.process_sxt.getSolrFieldName());
        localOmitFields.add(CollectionSchema.harvestkey_s.getSolrFieldName());
        final Collection<String> failids = ConcurrentHashMap.newKeySet();
        final AtomicInteger countcheck = new AtomicInteger(0);
        final AtomicInteger proccount = new AtomicInteger();
        final AtomicInteger proccount_referencechange = new AtomicInteger();
        final AtomicInteger proccount_citationchange = new AtomicInteger();
        try {
            // partitioning of the index, get a facet for a partitioning key
            final long count = collectionConnector.getCountByQuery("{!cache=false}" + collection1query);
            final String partitioningKey = CollectionSchema.responsetime_i.getSolrFieldName();
            postprocessingActivity = "collecting " + count + " documents from the collection for harvestkey " + harvestkey + ", partitioned by " + partitioningKey;
            if (count > 0) {
                final Map<String, ReversibleScoreMap<String>> partitioningFacet = collectionConnector.getFacets("{!cache=false}" + collection1query, 100000, partitioningKey);
                final ReversibleScoreMap<String> partitioning = partitioningFacet.get(partitioningKey);
                final long emptyCount = collectionConnector.getCountByQuery("{!cache=false}" + "-" + partitioningKey + AbstractSolrConnector.CATCHALL_DTERM + " AND (" + collection1query + ")");
                if (emptyCount > 0) partitioning.inc("", (int) emptyCount);
                final long start = System.currentTimeMillis();
                final List<String> querystrings = new ArrayList<>(partitioning.size());
                for (final String partitioningValue: partitioning) {
                    final String partitioningQuery = "{!cache=false}" + ((partitioningValue.length() == 0) ?
                            "-" + partitioningKey + AbstractSolrConnector.CATCHALL_DTERM + " AND (" + collection1query + ")" :
                            partitioningKey + ":" + partitioningValue + " AND (" + collection1query + ")");
                    querystrings.add(partitioningQuery);
                }
                // start collection of documents
                final int concurrency = Math.max(1, Math.min((int) (MemoryControl.available() / (100L * 1024L * 1024L)), Runtime.getRuntime().availableProcessors()));
                //final int concurrency = 1;
                final boolean reference_computation = this.contains(CollectionSchema.references_i) &&
                        this.contains(CollectionSchema.references_internal_i) &&
                        this.contains(CollectionSchema.references_external_i) &&
                        this.contains(CollectionSchema.references_exthosts_i);
                ConcurrentLog.info("CollectionConfiguration", postprocessingActivity);
                final BlockingQueue<SolrDocument> docs = collectionConnector.concurrentDocumentsByQueries(
                        querystrings,
                        (this.contains(CollectionSchema.http_unique_b) || this.contains(CollectionSchema.www_unique_b)) ?
                        CollectionSchema.host_subdomain_s.getSolrFieldName() + " asc," + // sort on subdomain to get hosts without subdomain first; that gives an opportunity to set www_unique_b flag to false
                        CollectionSchema.url_protocol_s.getSolrFieldName() + " asc" // sort on protocol to get http before https; that gives an opportunity to set http_unique_b flag to false
                        : null, // null sort is faster!
                        0, 100000000, Long.MAX_VALUE, concurrency + 1, concurrency, true,
                        byPartialUpdate ?
                        new String[]{
                        // the following fields are needed to perform the postprocessing
                        // and should only be used for partial updates; for full updates use a
                        // full list of fields to avoid LazyInstantiation which has poor performace
                        CollectionSchema.id.getSolrFieldName(),
                        CollectionSchema.sku.getSolrFieldName(),
                        CollectionSchema.harvestkey_s.getSolrFieldName(),
                        CollectionSchema.process_sxt.getSolrFieldName(),
                        CollectionSchema.canonical_equal_sku_b.getSolrFieldName(),
                        CollectionSchema.canonical_s.getSolrFieldName(),
                        CollectionSchema.exact_signature_l.getSolrFieldName(),
                        CollectionSchema.fuzzy_signature_l.getSolrFieldName(),
                        CollectionSchema.title_exact_signature_l.getSolrFieldName(),
                        CollectionSchema.description_exact_signature_l.getSolrFieldName(),
                        CollectionSchema.host_id_s.getSolrFieldName(),
                        CollectionSchema.host_s.getSolrFieldName(),
                        CollectionSchema.host_subdomain_s.getSolrFieldName(),
                        CollectionSchema.url_chars_i.getSolrFieldName(),
                        CollectionSchema.url_protocol_s.getSolrFieldName(),
                        CollectionSchema.httpstatus_i.getSolrFieldName(),
                        CollectionSchema.inboundlinkscount_i.getSolrFieldName(),
                        CollectionSchema.robots_i.getSolrFieldName()} :
                        this.allFields());
                final Thread rewriteThread[] = new Thread[concurrency];
                for (int rewrite_start = 0; rewrite_start < concurrency; rewrite_start++) {
                    rewriteThread[rewrite_start] = new Thread("CollectionConfiguration.postprocessing.rewriteThread-" + rewrite_start) {
                        @Override
                        public void run() {
                            SolrDocument doc;
                            try {
                                while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                                    // for each to-be-processed entry work on the process tag
                                    final Collection<Object> proctags = doc.getFieldValues(CollectionSchema.process_sxt.getSolrFieldName());
                                    final String u = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
                                    final String i = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
                                    if (proctags == null || proctags.size() == 0) {
                                        // this should not happen since we collected the documents using a process_sxt:[* TO *] term
                                        ConcurrentLog.warn("CollectionConfiguration", "no process_sxt entry for url " + u + ", id=" + i);
                                        continue;
                                    }
                                    try {
                                        final DigestURL url = new DigestURL(u, ASCII.getBytes(i));
                                        final byte[] id = url.hash();
                                        final SolrInputDocument sid = byPartialUpdate ? new SolrInputDocument() : collection.toSolrInputDocument(doc, localOmitFields);
                                        sid.setField(CollectionSchema.id.getSolrFieldName(), i);
                                        for (final Object tag: proctags) try {

                                            // switch over tag types
                                            final ProcessType tagtype = ProcessType.valueOf((String) tag);

                                            if (tagtype == ProcessType.CITATION &&
                                                collection.contains(CollectionSchema.cr_host_count_i) &&
                                                collection.contains(CollectionSchema.cr_host_chance_d) &&
                                                collection.contains(CollectionSchema.cr_host_norm_i)) {
                                                final CRV crv = rankings.remove(ASCII.String(id)); // instead of 'get'ting the CRV, we also remove it because we will not need it again and free some memory here
                                                if (crv != null) {
                                                    sid.setField(CollectionSchema.cr_host_count_i.getSolrFieldName(), crv.count);
                                                    sid.setField(CollectionSchema.cr_host_chance_d.getSolrFieldName(), crv.cr);
                                                    sid.setField(CollectionSchema.cr_host_norm_i.getSolrFieldName(), crv.crn);
                                                    proccount_citationchange.incrementAndGet();
                                                }
                                            }

                                            if (tagtype == ProcessType.UNIQUE) {
                                                CollectionConfiguration.this.postprocessing_http_unique(segment, doc, sid, url);
                                                CollectionConfiguration.this.postprocessing_www_unique(segment, doc, sid, url);
                                                CollectionConfiguration.this.postprocessing_doublecontent(segment, uniqueURLs, doc, sid, url);
                                            }

                                        } catch (final IllegalArgumentException e) {}

                                        // compute references
                                        if (reference_computation) {
                                            final String hosthash = url.hosthash();
                                            if (!hostExtentCache.containsKey(hosthash)) {
                                                final StringBuilder q = new StringBuilder();
                                                q.append(CollectionSchema.host_id_s.getSolrFieldName()).append(":\"").append(hosthash).append("\" AND ").append(CollectionSchema.httpstatus_i.getSolrFieldName()).append(":200");
                                                final long hostExtentCount = segment.fulltext().getDefaultConnector().getCountByQuery(q.toString());
                                                hostExtentCache.put(hosthash, hostExtentCount);
                                            }
                                            if (CollectionConfiguration.this.postprocessing_references(rrCache, sid, url, hostExtentCache)) proccount_referencechange.incrementAndGet();
                                        }

                                        // all processing steps checked, remove the processing and harvesting key
                                        if (byPartialUpdate) {
                                            sid.setField(CollectionSchema.process_sxt.getSolrFieldName(), null); // setting this to null will cause a removal when doing a partial update
                                            sid.setField(CollectionSchema.harvestkey_s.getSolrFieldName(), null);
                                        } /*else { // fields are omitted on sid creation
                                            sid.removeField(CollectionSchema.process_sxt.getSolrFieldName());
                                            sid.removeField(CollectionSchema.harvestkey_s.getSolrFieldName());
                                        }*/
                                        // with standard solr fields selected, the sid now contains the fields
                                        // id, http_unique_b, www_unique_b, references_i, references_internal_i, references_external_i, references_exthosts_i, host_extent_i
                                        // and the value for host_extent_i is by default 2147483647

                                        // send back to index
                                        //collectionConnector.deleteById(i);
                                        if (byPartialUpdate) {
                                            collectionConnector.update(sid);
                                        } else {
                                            collectionConnector.add(sid);
                                        }
                                        final long thiscount = proccount.incrementAndGet(); allcount.incrementAndGet();
                                        if (thiscount % 100 == 0) {
                                            postprocessingActivity = "postprocessed " + thiscount + " from " + count + " collection documents; " +
                                                (thiscount * 60000L / (System.currentTimeMillis() - start)) + " ppm; " +
                                                ((System.currentTimeMillis() - start) * (count - thiscount) / thiscount / 60000) + " minutes remaining";
                                            ConcurrentLog.info("CollectionConfiguration", postprocessingActivity);
                                        }
                                    } catch (final Throwable e1) {
                                        ConcurrentLog.logException(e1);
                                        failids.add(i);
                                    }
                                    countcheck.incrementAndGet();
                                }
                            } catch (final InterruptedException e) {
                                ConcurrentLog.logException(e);
                            }
                        }
                    };
                    rewriteThread[rewrite_start].start();
                }
                // wait for termination
                for (int rewrite_start = 0; rewrite_start < concurrency; rewrite_start++) rewriteThread[rewrite_start].join();

                if (failids.size() > 0) {
                    ConcurrentLog.info("CollectionConfiguration", "cleanup_processing: deleting " + failids.size() + " documents which have permanent execution fails");
                    collectionConnector.deleteByIds(failids);
                }
                if (count != countcheck.get()) ConcurrentLog.warn("CollectionConfiguration", "ambiguous collection document count for harvestkey " + harvestkey + ": expected=" + count + ", counted=" + countcheck + "; countquery=" + collection1query); // big gap for harvestkey = null
                ConcurrentLog.info("CollectionConfiguration", "cleanup_processing: re-calculated " + proccount + " new documents, " +
                            proccount_referencechange + " reference-count changes, " +
                            proccount_citationchange + " citation ranking changes.");
            }

        } catch (final InterruptedException e2) {
            ConcurrentLog.warn("CollectionConfiguration", e2.getMessage(), e2);
        } catch (final IOException e3) {
            ConcurrentLog.warn("CollectionConfiguration", e3.getMessage(), e3);
        }
        collectionConnector.commit(true); // make changes available directly to prevent that the process repeats again
	}

	/**
	 * Perform postprocessing steps on the webgraph core.
	 * @param segment Solr segment.
	 * @param webgraph webgraph schema configuration
	 * @param webgraphquery query used to harvest items to postprocess in the webgraph collection
	 * @param rankings postprocessed rankings
	 * @param allcount global postprocessed documents count
	 */
	private void postprocessWebgraph(final Segment segment, final WebgraphConfiguration webgraph, String webgraphquery,
			final Map<String, CRV> rankings, final AtomicInteger allcount) {
		postprocessingActivity = "collecting host facets for webgraph cr calculation";
		ConcurrentLog.info("CollectionConfiguration", postprocessingActivity);
		final Set<String> omitFields = new HashSet<>();
		omitFields.add(WebgraphSchema.process_sxt.getSolrFieldName());
		omitFields.add(WebgraphSchema.harvestkey_s.getSolrFieldName());

		// collect hosts from index which shall take part in citation computation
		ReversibleScoreMap<String> webgraphhosts;
		try {
		    final Map<String, ReversibleScoreMap<String>> hostfacet = segment.fulltext().getWebgraphConnector().getFacets(webgraphquery, 10000000, WebgraphSchema.source_host_s.getSolrFieldName());
		    webgraphhosts = hostfacet.get(WebgraphSchema.source_host_s.getSolrFieldName());
		} catch (final IOException e2) {
		    ConcurrentLog.logException(e2);
		    webgraphhosts = new ClusteredScoreMap<>(true);
		}
		try {
		    final long start = System.currentTimeMillis();
		    for (final String host: webgraphhosts.keyList(true)) {
		        if (webgraphhosts.get(host) <= 0) continue;
		        final String hostfinal = host;
		        // select all webgraph edges and modify their cr value
		        postprocessingActivity = "writing cr values to webgraph for host " + host;
		        ConcurrentLog.info("CollectionConfiguration", postprocessingActivity);
		        final String patchquery = WebgraphSchema.source_host_s.getSolrFieldName() + ":\"" + host + "\" AND " + WebgraphSchema.process_sxt.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM;
		        final long count = segment.fulltext().getWebgraphConnector().getCountByQuery("{!cache=false}" + patchquery);
		        final int concurrency = Math.min((int) count, Math.max(1, Runtime.getRuntime().availableProcessors() / 4));
		        ConcurrentLog.info("CollectionConfiguration", "collecting " + count + " documents from the webgraph, concurrency = " + concurrency);
		        final BlockingQueue<SolrDocument> docs = segment.fulltext().getWebgraphConnector().concurrentDocumentsByQuery(
		                patchquery,
		                WebgraphSchema.source_chars_i.getSolrFieldName() + " asc",
		                0, 100000000, Long.MAX_VALUE, concurrency + 1, concurrency, true
		                // TODO: add field list and do partial updates
		                );
		        final AtomicInteger proccount = new AtomicInteger(0);
		        final Thread[] t = new Thread[concurrency];
		        for (int i = 0; i < t.length; i++) {
		            t[i] = new Thread("CollectionConfiguration.postprocessing.webgraph-" + i) {
		                @Override
		                public void run() {
		                    SolrDocument doc; String id;
		                    try {
		                        processloop: while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
		                            try {
		                                final SolrInputDocument sid = webgraph.toSolrInputDocument(doc, omitFields);
		                                final Collection<Object> proctags = doc.getFieldValues(WebgraphSchema.process_sxt.getSolrFieldName());

		                                for (final Object tag: proctags) try {

		                                    // switch over tag types
		                                    final ProcessType tagtype = ProcessType.valueOf((String) tag);

		                                    // set cr values
		                                    if (tagtype == ProcessType.CITATION) {
		                                        if (segment.fulltext().useWebgraph() && webgraph.contains(WebgraphSchema.source_id_s) && webgraph.contains(WebgraphSchema.source_cr_host_norm_i)) {
		                                            id = (String) doc.getFieldValue(WebgraphSchema.source_id_s.getSolrFieldName());
		                                            final CRV crv = rankings.get(id);
		                                            if (crv != null) {
		                                                sid.setField(WebgraphSchema.source_cr_host_norm_i.getSolrFieldName(), crv.crn);
		                                            }
		                                        }
		                                        if (webgraph.contains(WebgraphSchema.target_id_s) && webgraph.contains(WebgraphSchema.target_cr_host_norm_i)) {
		                                            id = (String) doc.getFieldValue(WebgraphSchema.target_id_s.getSolrFieldName());
		                                            final CRV crv = rankings.get(id);
		                                            if (crv != null) {
		                                                sid.setField(WebgraphSchema.target_cr_host_norm_i.getSolrFieldName(), crv.crn);
		                                            }
		                                        }
		                                    }
		                                } catch (final IllegalArgumentException e) {
		                                    ConcurrentLog.logException(e);
		                                }

		                                // write document back to index
		                                try {
		                                    sid.removeField(WebgraphSchema.process_sxt.getSolrFieldName());
		                                    sid.removeField(WebgraphSchema.harvestkey_s.getSolrFieldName());
		                                    //segment.fulltext().getWebgraphConnector().deleteById((String) sid.getFieldValue(WebgraphSchema.id.getSolrFieldName()));
		                                    segment.fulltext().getWebgraphConnector().add(sid);
		                                } catch (final SolrException e) {
		                                    ConcurrentLog.logException(e);
		                                } catch (final IOException e) {
		                                    ConcurrentLog.logException(e);
		                                }
		                                proccount.incrementAndGet();
		                                allcount.incrementAndGet();
		                                if (proccount.get() % 1000 == 0) {
		                                    postprocessingActivity = "writing CitationRank values to webgraph for host " + hostfinal + "postprocessed " + proccount + " from " + count + " documents; " +
		                                        (proccount.get() * 1000 / (System.currentTimeMillis() - start)) + " docs/second; " +
		                                        ((System.currentTimeMillis() - start) * (count - proccount.get()) / proccount.get() / 60000) + " minutes remaining";
		                                    ConcurrentLog.info("CollectionConfiguration", postprocessingActivity);
		                                }
		                            } catch (final Throwable e) {
		                                ConcurrentLog.logException(e);
		                                continue processloop;
		                            }
		                        }
		                    } catch (final InterruptedException e) {
		                        ConcurrentLog.warn("CollectionConfiguration", e.getMessage(), e);
		                    }
		                }
		            };
		            t[i].start();
		        }
		        for (int i = 0; i < t.length; i++) try {
		            t[i].join(10000);
		            if (t[i].isAlive()) t[i].interrupt();
		        } catch (final InterruptedException e) {}

		        if (count != proccount.get()) ConcurrentLog.warn("CollectionConfiguration", "ambiguous webgraph document count for host " + host + ": expected=" + count + ", counted=" + proccount);
		    }
		} catch (final IOException e2) {
		    ConcurrentLog.warn("CollectionConfiguration", e2.getMessage(), e2);
		}
	}

	/**
	 * Patches the citation index for links with canonical tags and perform the citation rank computation
	 * @param segment Solr segment
	 * @param rrCache reference report cache for the segment
	 * @param collectionConnector default connector to the Solr segment
	 * @param collection1hosts hosts from index which shall take part in citation computation
	 * @return the ranking map
	 */
	private Map<String, CRV> createRankingMap(final Segment segment, final ReferenceReportCache rrCache,
			final SolrConnector collectionConnector, ReversibleScoreMap<String> collection1hosts) {
		final Map<String, CRV> rankings = new ConcurrentHashMap<>();
        try {
            final int concurrency = Math.min(collection1hosts.size(), Runtime.getRuntime().availableProcessors());
            postprocessingActivity = "collecting CitationRank for " + collection1hosts.size() + " hosts, concurrency = " + concurrency;
            ConcurrentLog.info("CollectionConfiguration", postprocessingActivity);
            int countcheck = 0;
            for (final String host: collection1hosts.keyList(true)) {
                // Patch the citation index for links with canonical tags.
                // This shall fulfill the following requirement:
                // If a document A links to B and B contains a 'canonical C', then the citation rank computation shall consider that A links to C and B does not link to C.
                // To do so, we first must collect all canonical links, find all references to them, get the anchor list of the documents and patch the citation reference of these links
                final String patchquery = CollectionSchema.host_s.getSolrFieldName() + ":" + host + " AND " + CollectionSchema.canonical_s.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM;
                final long patchquerycount = collectionConnector.getCountByQuery("{!cache=false}" + patchquery);
                final BlockingQueue<SolrDocument> documents_with_canonical_tag = collectionConnector.concurrentDocumentsByQuery(patchquery, CollectionSchema.url_chars_i.getSolrFieldName() + " asc", 0, 100000000, Long.MAX_VALUE, 20, 1, true,
                        CollectionSchema.id.getSolrFieldName(), CollectionSchema.sku.getSolrFieldName(), CollectionSchema.canonical_s.getSolrFieldName());
                SolrDocument doc_B;
                int patchquerycountcheck = 0;
                try {
                    while ((doc_B = documents_with_canonical_tag.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                        // find all documents which link to the canonical doc
                        final DigestURL doc_C_url = new DigestURL((String) doc_B.getFieldValue(CollectionSchema.canonical_s.getSolrFieldName()));
                        final byte[] doc_B_id = ASCII.getBytes(((String) doc_B.getFieldValue(CollectionSchema.id.getSolrFieldName())));
                        // we remove all references to B, because these become references to C
                        if (segment.connectedCitation()) {
                            final ReferenceContainer<CitationReference> doc_A_ids = segment.urlCitation().remove(doc_B_id);
                            if (doc_A_ids == null) {
                                //System.out.println("*** document with canonical but no referrer: " + doc_B.getFieldValue(CollectionSchema.sku.getSolrFieldName()));
                                continue; // the document has a canonical tag but no referrer?
                            }
                            final Iterator<CitationReference> doc_A_ids_iterator = doc_A_ids.entries();
                            // for each of the referrer A of B, set A as a referrer of C
                            while (doc_A_ids_iterator.hasNext()) {
                                final CitationReference doc_A_citation = doc_A_ids_iterator.next();
                                segment.urlCitation().add(doc_C_url.hash(), doc_A_citation);
                            }
                        }
                        patchquerycountcheck++;
                        if (MemoryControl.shortStatus()) {
                            ConcurrentLog.warn("CollectionConfiguration", "terminated canonical collection during postprocessing because of short memory");
                            break;
                        }
                    }
                } catch (final InterruptedException e) {
                    ConcurrentLog.logException(e);
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                }
                if (patchquerycount != patchquerycountcheck) ConcurrentLog.warn("CollectionConfiguration", "ambiguous patchquery count for host " + host + ": expected=" + patchquerycount + ", counted=" + patchquerycountcheck);

                // do the citation rank computation
                if (collection1hosts.get(host) <= 0) continue;
                // select all documents for each host
                final CRHost crh = new CRHost(segment, rrCache, host, 0.85d, 6);
                int convergence_attempts = 0;
                while (convergence_attempts++ < 30) {
                    ConcurrentLog.info("CollectionConfiguration", "convergence step " + convergence_attempts + " for host " + host + " ...");
                    if (crh.convergenceStep()) break;
                    if (MemoryControl.shortStatus()) {
                        ConcurrentLog.warn("CollectionConfiguration", "terminated convergenceStep during postprocessing because of short memory");
                        break;
                    }
                }
                ConcurrentLog.info("CollectionConfiguration", "convergence for host " + host + " after " + convergence_attempts + " steps");
                // we have now the cr for all documents of a specific host; we store them for later use
                final Map<String, CRV> crn = crh.normalize();
                //crh.log(crn);
                rankings.putAll(crn); // accumulate this here for usage in document update later
                if (MemoryControl.shortStatus()) {
                    ConcurrentLog.warn("CollectionConfiguration", "terminated crn akkumulation during postprocessing because of short memory");
                    break;
                }
                countcheck++;
            }
            if (collection1hosts.size() != countcheck) ConcurrentLog.warn("CollectionConfiguration", "ambiguous host count: expected=" + collection1hosts.size() + ", counted=" + countcheck);
        } catch (final IOException e2) {
            ConcurrentLog.logException(e2);
            collection1hosts = new ClusteredScoreMap<>(true);
        }
		return rankings;
	}

	/**
	 * Search in the segment any document having the same url as doc but with the opposite secure/unsecure (https or http) version of the protocol.
	 * Then updates accordingly the document http_unique_b field.
	 * @param segment Solr segment
	 * @param doc document to process
	 * @param sid updatable version of the document
	 * @param url document's url
	 */
    public void postprocessing_http_unique(final Segment segment, final SolrDocument doc, final SolrInputDocument sid, final DigestURL url) {
        if (!this.contains(CollectionSchema.http_unique_b)) return;
        if (!url.isHTTPS() && !url.isHTTP()) return;
        try {
            final DigestURL u = new DigestURL((url.isHTTP() ? "https://" : "http://") + url.urlstub(true, true));
            final SolrDocument d = segment.fulltext().getDefaultConnector().getDocumentById(ASCII.String(u.hash()), CollectionSchema.http_unique_b.getSolrFieldName());
            this.set_unique_flag(CollectionSchema.http_unique_b, doc, sid, d);
        } catch (final IOException e) {
        	ConcurrentLog.warn("CollectionConfiguration", "Failed to postProcess http_unique_b field" + e.getMessage() != null ? " : " + e.getMessage() : ".");
        }
    }

	/**
	 * Search in the segment any document having the same url as doc but with or without the www prefix.
	 * Then updates accordingly the document www_unique_b field.
	 * @param segment Solr segment
	 * @param doc document to process
	 * @param sid updatable version of the document
	 * @param url document's url
	 */
    public void postprocessing_www_unique(final Segment segment, final SolrDocument doc, final SolrInputDocument sid, final DigestURL url) {
        if (!this.contains(CollectionSchema.www_unique_b)) return;
        final String us = url.urlstub(true, true);
        try {
            final DigestURL u = new DigestURL(url.getProtocol() + (us.startsWith("www.") ? "://" + us.substring(4) : "://www." + us));
            final SolrDocument d = segment.fulltext().getDefaultConnector().getDocumentById(ASCII.String(u.hash()), CollectionSchema.www_unique_b.getSolrFieldName());
            this.set_unique_flag(CollectionSchema.www_unique_b, doc, sid, d);
        } catch (final IOException e) {
        	ConcurrentLog.warn("CollectionConfiguration", "Failed to postProcess www_unique_b field" + e.getMessage() != null ? " : " + e.getMessage() : ".");
        }
    }

    private void set_unique_flag(CollectionSchema field, final SolrDocument doc, final SolrInputDocument sid, final SolrDocument d) {
        final Object sb = doc.getFieldValue(field.getSolrFieldName());
        final boolean sbb = sb != null && ((Boolean) sb).booleanValue();
        final Object ob = d == null ? null : d.getFieldValue(field.getSolrFieldName());
        final boolean obb = ob != null && ((Boolean) ob).booleanValue();
        if (sbb == obb) sid.setField(field.getSolrFieldName(), !sbb);
    }

    public void postprocessing_doublecontent(Segment segment, Set<String> uniqueURLs, SolrDocument doc, final SolrInputDocument sid, final DigestURL url) {
        // FIND OUT IF THIS IS A DOUBLE DOCUMENT
        // term to describe documents which are indexable:
        // - no noindex in meta oder x-robots
        // - no canonical-tag
        final Conjunction ValidDocTermTemplate = new Conjunction();
        ValidDocTermTemplate.addOperand(new LongLiteral(CollectionSchema.httpstatus_i, 200));
        ValidDocTermTemplate.addOperand(new Disjunction(new Negation(new CatchallLiteral(CollectionSchema.canonical_equal_sku_b)), new BooleanLiteral(CollectionSchema.canonical_equal_sku_b, true)));
        ValidDocTermTemplate.addOperand(new Negation(new LongLiteral(CollectionSchema.robots_i, 8))); // bit 3 (noindex)
        ValidDocTermTemplate.addOperand(new Negation(new LongLiteral(CollectionSchema.robots_i, 24))); // bit 3 + 4 (noindex + nofollow)
        ValidDocTermTemplate.addOperand(new Negation(new LongLiteral(CollectionSchema.robots_i, 512))); // bit 9 (noindex)
        ValidDocTermTemplate.addOperand(new Negation(new LongLiteral(CollectionSchema.robots_i, 1536))); // bit 9 + 10 (noindex + nofollow)

        final String urlhash = ASCII.String(url.hash());
        final String hostid = url.hosthash();
        final Disjunction dnf = new Disjunction();
        final CollectionSchema[][] doccheckschema = new CollectionSchema[][]{
                {CollectionSchema.exact_signature_l, CollectionSchema.exact_signature_unique_b, CollectionSchema.exact_signature_copycount_i},
                {CollectionSchema.fuzzy_signature_l, CollectionSchema.fuzzy_signature_unique_b, CollectionSchema.fuzzy_signature_copycount_i}};
        uniquecheck: for (final CollectionSchema[] checkfields: doccheckschema) {
            final CollectionSchema signaturefield = checkfields[0];
            final CollectionSchema uniquefield = checkfields[1];
            final CollectionSchema countfield = checkfields[2];

            if (this.contains(signaturefield) && this.contains(uniquefield) && this.contains(countfield)) {
                // lookup the document with the same signature
                final Long signature = (Long) doc.getFieldValue(signaturefield.getSolrFieldName());
                if (signature == null) continue uniquecheck;
                //con.addOperand(new Negation(new Literal(CollectionSchema.id, urlhash)));
                //con.addOperand(new Literal(CollectionSchema.host_id_s, hostid));
                dnf.addOperand(new LongLiteral(signaturefield, signature));
            }
        }
        final Conjunction con = (Conjunction) ValidDocTermTemplate.clone();
        con.addOperand(dnf);
        con.addOperand(new Negation(new StringLiteral(CollectionSchema.id, urlhash)));
        con.addOperand(new StringLiteral(CollectionSchema.host_id_s, hostid));
        final String query = con.toString();
        SolrDocumentList docsAkk;
        try {
             docsAkk = segment.fulltext().getDefaultConnector().getDocumentListByQuery(query, null, 0, 1000,
                     CollectionSchema.id.getSolrFieldName(), CollectionSchema.exact_signature_l.getSolrFieldName(), CollectionSchema.fuzzy_signature_l.getSolrFieldName());
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            docsAkk = new SolrDocumentList();
        }
        if (docsAkk.getNumFound() > 0) uniquecheck: for (final CollectionSchema[] checkfields: doccheckschema) {
            final CollectionSchema signaturefield = checkfields[0];
            final CollectionSchema uniquefield = checkfields[1];
            final CollectionSchema countfield = checkfields[2];

            if (this.contains(signaturefield) && this.contains(uniquefield) && this.contains(countfield)) {
                // lookup the document with the same signature
                final Long signature = (Long) doc.getFieldValue(signaturefield.getSolrFieldName());
                if (signature == null) continue uniquecheck;
                final SolrDocumentList docs = new StringLiteral(signaturefield, signature.toString()).apply(docsAkk);
                if (docs.getNumFound() == 0) {
                    sid.setField(uniquefield.getSolrFieldName(), true);
                    sid.setField(countfield.getSolrFieldName(), 1);
                } else {
                    boolean firstappearance = true;
                    for (final SolrDocument d: docs) {if (uniqueURLs.contains(d.getFieldValue(CollectionSchema.id.getSolrFieldName()))) firstappearance = false; break;}
                    sid.setField(uniquefield.getSolrFieldName(), firstappearance);
                    sid.setField(countfield.getSolrFieldName(), docs.getNumFound() + 1); // the current url was excluded from search but is included in count
                }
            }
        }

        // CHECK IF TITLE AND DESCRIPTION IS UNIQUE (this is by default not switched on)
        // in case that the document has no status code 200, has a noindex attribute
        // or a canonical tag which does not point to the document itself,
        // then the unique-field is not written at all!
        final Integer robots_i = this.contains(CollectionSchema.robots_i) ? (Integer) doc.getFieldValue(CollectionSchema.robots_i.getSolrFieldName()) : null;
        final Integer httpstatus_i = this.contains(CollectionSchema.httpstatus_i) ? (Integer) doc.getFieldValue(CollectionSchema.httpstatus_i.getSolrFieldName()) : null;
        final String canonical_s = this.contains(CollectionSchema.canonical_s) ? (String) doc.getFieldValue(CollectionSchema.canonical_s.getSolrFieldName()) : null;
        final Boolean canonical_equal_sku_b = this.contains(CollectionSchema.canonical_equal_sku_b) ? (Boolean) doc.getFieldValue(CollectionSchema.canonical_equal_sku_b.getSolrFieldName()) : null;

        final CollectionSchema[][] metadatacheckschema = new CollectionSchema[][]{
                {CollectionSchema.title, CollectionSchema.title_exact_signature_l, CollectionSchema.title_unique_b},
                {CollectionSchema.description_txt, CollectionSchema.description_exact_signature_l, CollectionSchema.description_unique_b}};
        if (segment.fulltext().getDefaultConfiguration().contains(CollectionSchema.host_id_s) &&
            (robots_i == null || (robots_i.intValue() & (1 << 9)) == 0 /*noindex in http X-ROBOTS*/ && (robots_i.intValue() & (1 << 3)) == 0 /*noindex in html metas*/ ) &&
            (canonical_s == null || canonical_s.length() == 0 || (canonical_equal_sku_b != null && canonical_equal_sku_b.booleanValue()) || url.toNormalform(true).equals(canonical_s)) &&
            (httpstatus_i == null || httpstatus_i.intValue() == 200)) {
            uniquecheck: for (final CollectionSchema[] checkfields: metadatacheckschema) {
                final CollectionSchema checkfield = checkfields[0];
                final CollectionSchema signaturefield = checkfields[1];
                final CollectionSchema uniquefield = checkfields[2];
                if (this.contains(checkfield) && this.contains(signaturefield) && this.contains(uniquefield)) {
                    // lookup in the index within the same hosts for the same title or description
                    //String checkstring = checkfield == CollectionSchema.title ? document.dc_title() : document.dc_description();
                    final Long signature = (Long) doc.getFieldValue(signaturefield.getSolrFieldName());
                    if (signature == null) {
                        continue uniquecheck;
                    }
                    try {
                        final Conjunction doccountterm = (Conjunction) ValidDocTermTemplate.clone();
                        doccountterm.addOperand(new Negation(new StringLiteral(CollectionSchema.id, urlhash)));
                        doccountterm.addOperand(new StringLiteral(CollectionSchema.host_id_s, hostid));
                        doccountterm.addOperand(new LongLiteral(signaturefield, signature));
                        final long doccount = segment.fulltext().getDefaultConnector().getCountByQuery("{!cache=false}" + doccountterm.toString());
                        sid.setField(uniquefield.getSolrFieldName(), doccount  == 0);
                    } catch (final IOException e) {}
                }
            }
        }
        uniqueURLs.add(urlhash);
    }

    public boolean postprocessing_references(final ReferenceReportCache rrCache, final SolrInputDocument sid, final DigestURL url, final Map<String, Long> hostExtentCount) {
        if (!(this.contains(CollectionSchema.references_i) ||
              this.contains(CollectionSchema.references_internal_i) ||
              this.contains(CollectionSchema.references_external_i) || this.contains(CollectionSchema.references_exthosts_i))) return false;
        final Integer all_old = sid == null ? null : (Integer) sid.getFieldValue(CollectionSchema.references_i.getSolrFieldName());
        final Integer internal_old = sid == null ? null : (Integer) sid.getFieldValue(CollectionSchema.references_internal_i.getSolrFieldName());
        final Integer external_old = sid == null ? null : (Integer) sid.getFieldValue(CollectionSchema.references_external_i.getSolrFieldName());
        final Integer exthosts_old = sid == null ? null : (Integer) sid.getFieldValue(CollectionSchema.references_exthosts_i.getSolrFieldName());
        final Integer hostextc_old = sid == null ? null : (Integer) sid.getFieldValue(CollectionSchema.host_extent_i.getSolrFieldName());
        try {
            final ReferenceReport rr = rrCache.getReferenceReport(ASCII.String(url.hash()), false);
            final List<String> internalIDs = new ArrayList<>();
            final HandleSet iids = rr.getInternallIDs();
            for (final byte[] b: iids) internalIDs.add(ASCII.String(b));

            boolean change = false;
            final int all = rr.getExternalCount() + rr.getInternalCount();
            if (this.contains(CollectionSchema.references_i) &&
                (all_old == null || all_old.intValue() != all)) {
                sid.setField(CollectionSchema.references_i.getSolrFieldName(), all);
                change = true;
            }
            if (this.contains(CollectionSchema.references_internal_i) &&
                (internal_old == null || internal_old.intValue() != rr.getInternalCount())) {
                sid.setField(CollectionSchema.references_internal_i.getSolrFieldName(), rr.getInternalCount());
                change = true;
            }
            if (this.contains(CollectionSchema.references_external_i) &&
                (external_old == null || external_old.intValue() != rr.getExternalCount())) {
                sid.setField(CollectionSchema.references_external_i.getSolrFieldName(), rr.getExternalCount());
                change = true;
            }
            if (this.contains(CollectionSchema.references_exthosts_i) &&
                (exthosts_old == null || exthosts_old.intValue() != rr.getExternalHostIDs().size())) {
                sid.setField(CollectionSchema.references_exthosts_i.getSolrFieldName(), rr.getExternalHostIDs().size());
                change = true;
            }
            final Long hostExtent = hostExtentCount == null ? Long.MAX_VALUE : hostExtentCount.get(url.hosthash());
            if (this.contains(CollectionSchema.host_extent_i) &&
                (hostextc_old == null || hostextc_old.intValue() != hostExtent)) {
                sid.setField(CollectionSchema.host_extent_i.getSolrFieldName(), hostExtent.intValue());
                change = true;
            }
            return change;
        } catch (final IOException e) {
        }
        return false;
    }



    private static final class CRV {
        public double cr;
        public int crn, count;
        public CRV(final int count, final double cr, final int crn) {this.count = count; this.cr = cr; this.crn = crn;}
        @Override
        public String toString() {
            return "count=" + this.count + ", cr=" + this.cr + ", crn=" + this.crn;
        }
    }

    /**
     * The CRHost class is a container for all ranking values of a specific host.
     * Objects of that class are needed as an environment for repeated convergenceStep() computations,
     * which are iterative citation rank computations that are repeated until the ranking values
     * converge to stable values.
     * The class also contains normalization methods to compute simple integer ranking values out of the
     * double relevance values.
     */
    private static final class CRHost {
        private final Segment segment;
        private final Map<String, double[]> crt;
        private final int cr_host_count;
        private final RowHandleMap internal_links_counter;
        private final double damping;
        private final int converge_eq_factor;
        private final ReferenceReportCache rrCache;
        public CRHost(final Segment segment, final ReferenceReportCache rrCache, final String host, final double damping, final int converge_digits) {
            this.segment = segment;
            this.damping = damping;
            this.rrCache = rrCache;
            this.converge_eq_factor = (int) Math.pow(10.0d, converge_digits);
            final SolrConnector connector = segment.fulltext().getDefaultConnector();
            this.crt = new ConcurrentHashMap<>();
            try {
                // select all documents for each host
                final BlockingQueue<String> ids = connector.concurrentIDsByQuery("{!cache=false raw f=" + CollectionSchema.host_s.getSolrFieldName() + "}" + host, CollectionSchema.url_chars_i.getSolrFieldName() + " asc", 0, 100000000, 86400000, 200, 1);
                String id;
                while ((id = ids.take()) != AbstractSolrConnector.POISON_ID) {
                    this.crt.put(id, new double[]{0.0d,0.0d}); //{old value, new value}
                    if (MemoryControl.shortStatus()) {
                        ConcurrentLog.warn("CollectionConfiguration", "terminated CRHost collection during postprocessing because of short memory");
                        break;
                    }
                }
            } catch (final InterruptedException e2) {
            }
            this.cr_host_count = this.crt.size();
            final double initval = 1.0d / this.cr_host_count;
            for (final Map.Entry<String, double[]> entry: this.crt.entrySet()) entry.getValue()[0] = initval;
            this.internal_links_counter = new RowHandleMap(12, Base64Order.enhancedCoder, 8, 100, "internal_links_counter");
        }
        /**
         * produce a map from IDs to CRV records, normalization entries containing the values that are stored to solr.
         * @return
         */
        public Map<String, CRV> normalize() {
            final TreeMap<Double, List<byte[]>> reorder = new TreeMap<>();
            for (final Map.Entry<String, double[]> entry: this.crt.entrySet()) {
                final Double d = entry.getValue()[0];
                List<byte[]> ds = reorder.get(d);
                if (ds == null) {ds = new ArrayList<>(); reorder.put(d, ds);}
                ds.add(ASCII.getBytes(entry.getKey()));
            }
            int nextcount = (this.cr_host_count + 1) / 2;
            int nextcrn = 0;
            final Map<String, CRV> r = new HashMap<>();
            while (reorder.size() > 0) {
                int count = nextcount;
                while (reorder.size() > 0 && count > 0) {
                    final Map.Entry<Double, List<byte[]>> next = reorder.pollFirstEntry();
                    final List<byte[]> ids = next.getValue();
                    count -= ids.size();
                    final double cr = next.getKey();
                    for (final byte[] id: ids) r.put(ASCII.String(id), new CRV(this.cr_host_count, cr, nextcrn));
                }
                nextcrn++;
                nextcount = Math.max(1, (nextcount + count + 1) / 2);
            }
            // finally, increase the crn number in such a way that the maximum is always 10
            final int inc = 11 - nextcrn; // nextcrn is +1
            for (final Map.Entry<String, CRV> entry: r.entrySet()) entry.getValue().crn += inc;
            return r;
        }
        /**
         * log out a complete CRHost set of urls and ranking values
         * @param rm
         */
        @SuppressWarnings("unused")
        public void log(final Map<byte[], CRV> rm) {
            // print out all urls with their cr-values
            final SolrConnector connector = this.segment.fulltext().getDefaultConnector();
            for (final Map.Entry<byte[], CRV> entry: rm.entrySet()) {
                if (entry == null || entry.getValue() == null) continue;
                try {
                    final String url = connector.getURL(ASCII.String(entry.getKey()));
                    ConcurrentLog.info("CollectionConfiguration", "CR for " + url);
                    ConcurrentLog.info("CollectionConfiguration", ">> " + entry.getValue().toString());
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                }
            }
        }
        /**
         * Calculate the number of internal links from a specific document, denoted by the document ID.
         * This is a very important attribute for the ranking computation because it is the dividend for the previous ranking attribute.
         * The internalLinks value will be requested several times for the same id during the convergenceStep()-steps; therefore it should use a cache.
         * This cache is part of the CRHost data structure.
         * @param id
         * @return the number of links from the document, denoted by the ID to documents within the same domain
         */
        public int getInternalLinks(final byte[] id) {
            int il = (int) this.internal_links_counter.get(id);
            if (il >= 0) return il;
            final SolrConnector connector = this.segment.fulltext().getDefaultConnector();
            if (connector == null) return 0;
            try {
                final SolrDocument doc = connector.getDocumentById(ASCII.String(id), CollectionSchema.inboundlinkscount_i.getSolrFieldName());
                if (doc == null) {
                    this.internal_links_counter.put(id, 0);
                    return 0;
                }
                final Object x = doc.getFieldValue(CollectionSchema.inboundlinkscount_i.getSolrFieldName());
                il = (x == null) ? 0 : (x instanceof Integer) ? ((Integer) x).intValue() : (x instanceof Long) ? ((Long) x).intValue() : 0;
                this.internal_links_counter.put(id, il);
                return il;
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            } catch (final SpaceExceededException e) {
                ConcurrentLog.logException(e);
            }
            try {this.internal_links_counter.put(id, 0);} catch (final SpaceExceededException e) {}
            return 0;
        }
        /**
         * Use the crt cache to compute the next generation of crt values.
         * @return
         */
        public boolean convergenceStep() {
            boolean convergence = true;
            final double df = (1.0d - this.damping) / this.cr_host_count;
            try {
                for (final Map.Entry<String, double[]> entry: this.crt.entrySet()) {
                    final String id = entry.getKey();
                    final ReferenceReport rr = this.rrCache.getReferenceReport(id, false);
                    // sum up the cr of the internal links
                    final HandleSet iids = rr.getInternallIDs();
                    double ncr = 0.0d;
                    for (final byte[] iid: iids) {
                        final int ilc = this.getInternalLinks(iid);
                        if (ilc > 0) { // if (ilc == 0) then the reference report is wrong!
                            final double[] d = this.crt.get(ASCII.String(iid));
                            // d[] could be empty at some situations
                            if (d != null && d.length > 0) {
                                ncr += d[0] / ilc;
                            } else {
                                // Output a warning that d[] is empty
                                ConcurrentLog.warn("COLLECTION", "d[] is empty, iid="  + ASCII.String(iid));
                                break;
                            }
                        }
                    }
                    ncr = df + this.damping * ncr;
                    if (convergence && !this.eqd(ncr, entry.getValue()[0])) convergence = false;
                    entry.getValue()[1] = ncr;
                }
                // after the loop, replace the old value with the new value in crt
                for (final Map.Entry<String, double[]> entry: this.crt.entrySet()) {
                    entry.getValue()[0] = entry.getValue()[1];
                }
            } catch (final IOException e) {
            }
            return convergence;
        }
        /**
         * helper method to check if two doubles are equal using a specific number of digits
         * @param a
         * @param b
         * @return
         */
        private boolean eqd(final double a, final double b) {
            return ((int) (a * this.converge_eq_factor)) == ((int) (b * this.converge_eq_factor));
        }
    }

    /**
     * this method compresses a list of protocol names to an indexed list.
     * To do this, all 'http' entries are removed and considered as default.
     * The remaining entries are indexed as follows: a list of <i>-<p> entries is produced, where
     * <i> is an index pointing to the original index of the protocol entry and <p> is the protocol entry itself.
     * The <i> entry is formatted as a 3-digit decimal number with leading zero digits.
     * @param protocol
     * @return a list of indexed protocol entries
     */
    public static List<String> protocolList2indexedList(final List<String> protocol) {
        final List<String> a = new ArrayList<>();
        String p;
        for (int i = 0; i < protocol.size(); i++) {
        	p = protocol.get(i);
            if (!p.equals("http")) {
                String c = Integer.toString(i);
                while (c.length() < 3) c = "0" + c;
                a.add(c + "-" + p);
            }
        }
        return a;
    }

    /**
     * Uncompress indexed iplist of protocol names to a list of specified dimension.
     * @param iplist indexed list typically produced by protocolList2indexedList
     * @param dimension size of target list
     * @return a list of protocol names
     */
    public static List<String> indexedList2protocolList(Collection<Object> iplist, int dimension) {
        final List<String> a = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) a.add("http");
        if (iplist == null) return a;
        for (final Object ip : iplist) {
            // ip format is 001-https but can be 4 digits  1011-https
        	final String indexedProtocol = ((String) ip);
            final int i = indexedProtocol.indexOf('-');
            /* Silently ignore badly formatted entry */
            if(i > 0 && indexedProtocol.length() > (i + 1)) {
            	a.set(Integer.parseInt(indexedProtocol.substring(0, i)), indexedProtocol.substring(i+1));
            }
        }
        return a;
    }

//    /**
//     * encode a string containing attributes from anchor rel properties binary:
//     * bit 0: "me" contained in rel
//     * bit 1: "nofollow" contained in rel
//     * @param rel
//     * @return binary encoded information about rel
//     */
    /*
    private static List<Integer> relEval(final List<String> rel) {
        List<Integer> il = new ArrayList<Integer>(rel.size());
        for (final String s: rel) {
            int i = 0;
            final String s0 = s.toLowerCase(Locale.ROOT).trim();
            if ("me".equals(s0)) i += 1;
            if ("nofollow".equals(s0)) i += 2;
            il.add(i);
        }
        return il;
    }
    */

    public static class FailDoc {
        DigestURL digestURL;
        final Map<String, Pattern> collections;
        final String failReason;
        final FailType failType;
        final int httpstatus;
        final Date failtime;
        final int crawldepth;
        public FailDoc(final DigestURL digestURL, final Map<String, Pattern> collections, final String failReason, final FailType failType, final int httpstatus, final int crawldepth) {
            this.digestURL = digestURL;
            this.collections = collections;
            this.failReason = failReason;
            this.failType = failType;
            this.httpstatus = httpstatus;
            this.failtime = new Date();
            this.crawldepth = crawldepth;
        }
        public FailDoc(final SolrDocument doc) {
            try {
                this.digestURL = new DigestURL((String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName()));
            } catch (final MalformedURLException e) {
                this.digestURL = null;
            }
            this.collections = new HashMap<>();
            final Collection<Object> c = doc.getFieldValues(CollectionSchema.collection_sxt.getSolrFieldName());
            if (c != null) for (final Object cn: c) if (cn != null) this.collections.put((String) cn, QueryParams.catchall_pattern);
            this.failReason = (String) doc.getFieldValue(CollectionSchema.failreason_s.getSolrFieldName());
            final String fts = (String) doc.getFieldValue(CollectionSchema.failtype_s.getSolrFieldName());
            if (fts == null) ConcurrentLog.warn("CollectionConfiguration", "no fail type given for URL " + this.digestURL.toNormalform(true));
            this.failType = fts == null ? FailType.fail : FailType.valueOf(fts);
            this.httpstatus = (Integer) doc.getFieldValue(CollectionSchema.httpstatus_i.getSolrFieldName());
            this.failtime = (Date) doc.getFieldValue(CollectionSchema.load_date_dt.getSolrFieldName());
            final Integer cd = (Integer) doc.getFieldValue(CollectionSchema.crawldepth_i.getSolrFieldName());
            this.crawldepth = cd == null ? 0 : cd.intValue();
        }
        public DigestURL getDigestURL() {
            return this.digestURL;
        }
        public Map<String, Pattern> getCollections() {
            return this.collections;
        }
        public String getFailReason() {
            return this.failReason;
        }
        public FailType getFailType() {
            return this.failType;
        }
        public Date getFailDate() {
            return this.failtime;
        }
        public int getHttpstatus() {
            return this.httpstatus;
        }
        public SolrInputDocument toSolr(CollectionConfiguration configuration) {
            final boolean allAttr = configuration.isEmpty();
            assert allAttr || configuration.contains(CollectionSchema.failreason_s);

            final SolrInputDocument doc = new SolrInputDocument();
            final String url = configuration.addURIAttributes(doc, allAttr, this.getDigestURL());
            // content_type (mime) is defined a schema field and we rely on it in some queries like imagequery (makes it mandatory, no need to check)
            CollectionSchema.content_type.add(doc, new String[]{Classification.url2mime(this.digestURL)});
            if (allAttr || configuration.contains(CollectionSchema.load_date_dt)) configuration.add(doc, CollectionSchema.load_date_dt, this.getFailDate());
            if (allAttr || configuration.contains(CollectionSchema.crawldepth_i)) configuration.add(doc, CollectionSchema.crawldepth_i, this.crawldepth);

            // fail reason and status
            if (allAttr || configuration.contains(CollectionSchema.failreason_s)) configuration.add(doc, CollectionSchema.failreason_s, this.getFailReason());
            if (allAttr || configuration.contains(CollectionSchema.failtype_s)) configuration.add(doc, CollectionSchema.failtype_s, this.getFailType().name());
            if (allAttr || configuration.contains(CollectionSchema.httpstatus_i)) configuration.add(doc, CollectionSchema.httpstatus_i, this.getHttpstatus());
            if (allAttr || configuration.contains(CollectionSchema.collection_sxt) && this.getCollections() != null && this.getCollections().size() > 0) {
                final List<String> cs = new ArrayList<>();
                for (final Map.Entry<String, Pattern> e: this.getCollections().entrySet()) {
                    if (e.getValue().matcher(url).matches()) cs.add(e.getKey());
                }
                configuration.add(doc, CollectionSchema.collection_sxt, cs);
            }

            // cr and postprocessing
            final Set<ProcessType> processTypes = new LinkedHashSet<>();
            if (allAttr || (configuration.contains(CollectionSchema.cr_host_chance_d) && configuration.contains(CollectionSchema.cr_host_count_i) && configuration.contains(CollectionSchema.cr_host_norm_i))) {
                processTypes.add(ProcessType.CITATION); // postprocessing needed
            }
            if (allAttr || configuration.contains(CollectionSchema.process_sxt)) {
                final List<String> p = new ArrayList<>();
                for (final ProcessType t: processTypes) p.add(t.name());
                configuration.add(doc, CollectionSchema.process_sxt, p);
            }
            return doc;
        }

    }

}
