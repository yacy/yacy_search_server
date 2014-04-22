/**
 *  CollectionConfiguration
 *  Copyright 2011 by Michael Peter Christen
 *  First released 14.04.2011 at http://yacy.net
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
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

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
import net.yacy.cora.federate.solr.connector.SolrConnector.Metadata;
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
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.SentenceReader;
import net.yacy.document.content.DCEntry;
import net.yacy.document.parser.html.ContentScraper;
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

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;


public class CollectionConfiguration extends SchemaConfiguration implements Serializable {

    private static final long serialVersionUID=-499100932212840385L;

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
        this.rankings = new ArrayList<Ranking>(4);
        for (int i = 0; i <= 3; i++) rankings.add(new Ranking());
        // check consistency: compare with YaCyField enum
        if (this.isEmpty()) return;
        Iterator<Entry> it = this.entryIterator();
        for (SchemaConfiguration.Entry etr = it.next(); it.hasNext(); etr = it.next()) {
            try {
                CollectionSchema f = CollectionSchema.valueOf(etr.key());
                f.setSolrFieldName(etr.getValue());
            } catch (final IllegalArgumentException e) {
                ConcurrentLog.fine("SolrCollectionWriter", "solr schema file " + configurationFile.getAbsolutePath() + " defines unknown attribute '" + etr.toString() + "'");
                it.remove();
            }
        }
        // check consistency the other way: look if all enum constants in SolrField appear in the configuration file
        for (CollectionSchema field: CollectionSchema.values()) {
        	if (this.get(field.name()) == null) {
        	    if (CollectionSchema.author_sxt.getSolrFieldName().endsWith(field.name())) continue; // exception for this: that is a copy-field
        	    if (CollectionSchema.coordinate_p_0_coordinate.getSolrFieldName().endsWith(field.name())) continue; // exception for this: automatically generated
        	    if (CollectionSchema.coordinate_p_1_coordinate.getSolrFieldName().endsWith(field.name())) continue; // exception for this: automatically generated
                ConcurrentLog.warn("SolrCollectionWriter", " solr schema file " + configurationFile.getAbsolutePath() + " is missing declaration for '" + field.name() + "'");
        	}
        }
    }
    
    public Ranking getRanking(final int idx) {
        return this.rankings.get(idx);
    }
    
    public Ranking getRanking(final String name) {
        if (name == null) return null;
        for (int i = 0; i < this.rankings.size(); i++) {
            Ranking r = this.rankings.get(i);
            if (name.equals(r)) return r;
        }
        return null;
    }

    /**
     * save configuration to file and update enum SolrFields
     * @throws IOException
     */
    @Override
    public void commit() throws IOException {
        try {
            super.commit();
            // make sure the enum SolrField.SolrFieldName is current
            Iterator<Entry> it = this.entryIterator();
            for (SchemaConfiguration.Entry etr = it.next(); it.hasNext(); etr = it.next()) {
                try {
                    SchemaDeclaration f = CollectionSchema.valueOf(etr.key());
                    f.setSolrFieldName(etr.getValue());
                } catch (final IllegalArgumentException e) {
                    continue;
                }
            }
        } catch (final IOException e) {}
    }
    
    private final static Set<String> omitFields = new HashSet<String>(3);
    static {
        omitFields.add(CollectionSchema.author_sxt.getSolrFieldName());
        omitFields.add(CollectionSchema.coordinate_p_0_coordinate.getSolrFieldName());
        omitFields.add(CollectionSchema.coordinate_p_1_coordinate.getSolrFieldName());
    }
    
    public SolrInputDocument toSolrInputDocument(final SolrDocument doc) {
        return toSolrInputDocument(doc, omitFields);
    }
    
    public SolrDocument toSolrDocument(final SolrInputDocument doc) {
        return toSolrDocument(doc, omitFields);
    }
    
    /**
     * add uri attributes to solr document
     * @param doc
     * @param allAttr
     * @param digestURL
     * @param doctype
     * @return the normalized url
     */
    public String addURIAttributes(final SolrInputDocument doc, final boolean allAttr, final DigestURL digestURL, final char doctype) {
        add(doc, CollectionSchema.id, ASCII.String(digestURL.hash()));
        if (allAttr || contains(CollectionSchema.host_id_s)) add(doc, CollectionSchema.host_id_s, digestURL.hosthash());
        String us = digestURL.toNormalform(true);
        add(doc, CollectionSchema.sku, us);
        if (allAttr || contains(CollectionSchema.ip_s)) {
            final InetAddress address = digestURL.getInetAddress();
            if (address != null) add(doc, CollectionSchema.ip_s, address.getHostAddress());
        }
        String host = null;
        if ((host = digestURL.getHost()) != null) {
            String dnc = Domains.getDNC(host);
            String subdomOrga = host.length() - dnc.length() <= 0 ? "" : host.substring(0, host.length() - dnc.length() - 1);
            int p = subdomOrga.lastIndexOf('.');
            String subdom = (p < 0) ? "" : subdomOrga.substring(0, p);
            String orga = (p < 0) ? subdomOrga : subdomOrga.substring(p + 1);
            if (allAttr || contains(CollectionSchema.host_s)) add(doc, CollectionSchema.host_s, host);
            if (allAttr || contains(CollectionSchema.host_dnc_s)) add(doc, CollectionSchema.host_dnc_s, dnc);
            if (allAttr || contains(CollectionSchema.host_organization_s)) add(doc, CollectionSchema.host_organization_s, orga);
            if (allAttr || contains(CollectionSchema.host_organizationdnc_s)) add(doc, CollectionSchema.host_organizationdnc_s, orga + '.' + dnc);
            if (allAttr || contains(CollectionSchema.host_subdomain_s)) add(doc, CollectionSchema.host_subdomain_s, subdom);
        }
        
        // path elements of link
        String filename = digestURL.getFileName();
        String extension = MultiProtocolURL.getFileExtension(filename);
        String filenameStub = filename.toLowerCase().endsWith("." + extension) ? filename.substring(0, filename.length() - extension.length() - 1) : filename;
        // remove possible jsession (or other url parm like "img.jpg;jsession=123") 
        // TODO: consider to implement ";jsession=123" check in getFileExtension()
        if (extension.indexOf(';') >= 0) extension = extension.substring(0,extension.indexOf(';'));
        
        if (allAttr || contains(CollectionSchema.url_chars_i)) add(doc, CollectionSchema.url_chars_i, us.length());
        if (allAttr || contains(CollectionSchema.url_protocol_s)) add(doc, CollectionSchema.url_protocol_s, digestURL.getProtocol());
        if (allAttr || contains(CollectionSchema.url_paths_sxt)) add(doc, CollectionSchema.url_paths_sxt, digestURL.getPaths());
        if (allAttr || contains(CollectionSchema.url_file_name_s)) add(doc, CollectionSchema.url_file_name_s, filenameStub);
        if (allAttr || contains(CollectionSchema.url_file_name_tokens_t)) add(doc, CollectionSchema.url_file_name_tokens_t, MultiProtocolURL.toTokens(filenameStub));
        if (allAttr || contains(CollectionSchema.url_file_ext_s)) add(doc, CollectionSchema.url_file_ext_s, extension);
        if (allAttr || contains(CollectionSchema.content_type)) add(doc, CollectionSchema.content_type, Response.doctype2mime(extension, doctype));
        

        Map<String, String> searchpart = digestURL.getSearchpartMap();
        if (searchpart == null) {
            if (allAttr || contains(CollectionSchema.url_parameter_i)) add(doc, CollectionSchema.url_parameter_i, 0);
        } else {
            if (allAttr || contains(CollectionSchema.url_parameter_i)) add(doc, CollectionSchema.url_parameter_i, searchpart.size());
            if (allAttr || contains(CollectionSchema.url_parameter_key_sxt)) add(doc, CollectionSchema.url_parameter_key_sxt, searchpart.keySet().toArray(new String[searchpart.size()]));
            if (allAttr || contains(CollectionSchema.url_parameter_value_sxt)) add(doc, CollectionSchema.url_parameter_value_sxt,  searchpart.values().toArray(new String[searchpart.size()]));
        }
        return us;
    }
    
    public SolrInputDocument metadata2solr(final URIMetadataNode md) {

        final SolrInputDocument doc = new SolrInputDocument();
        boolean allAttr = this.isEmpty();

        addURIAttributes(doc, allAttr, md.url(), md.doctype());

        String title = md.dc_title();
        if (allAttr || contains(CollectionSchema.title)) add(doc, CollectionSchema.title, new String[]{title});
        if (allAttr || contains(CollectionSchema.title_count_i)) add(doc, CollectionSchema.title_count_i, 1);
        if (allAttr || contains(CollectionSchema.title_chars_val)) {
            Integer[] cv = new Integer[]{new Integer(title.length())};
            add(doc, CollectionSchema.title_chars_val, cv);
        }
        if (allAttr || contains(CollectionSchema.title_words_val)) {
            Integer[] cv = new Integer[]{new Integer(CommonPattern.SPACE.split(title).length)};
            add(doc, CollectionSchema.title_words_val, cv);
        }

        String description = md.snippet();
        boolean description_exist = description != null;
        if (description == null) description = "";
        if (allAttr || contains(CollectionSchema.description_txt)) add(doc, CollectionSchema.description_txt, description_exist ? new String[]{description} : new String[0]);
        if (allAttr || contains(CollectionSchema.description_count_i)) add(doc, CollectionSchema.description_count_i, description_exist ? 1 : 0);
        if (allAttr || contains(CollectionSchema.description_chars_val)) {
            add(doc, CollectionSchema.description_chars_val, description_exist ? new Integer[]{new Integer(description.length())} : new Integer[0]);
        }
        if (allAttr || contains(CollectionSchema.description_words_val)) {
            add(doc, CollectionSchema.description_words_val, description_exist ? new Integer[]{new Integer(description.length() == 0 ? 0 : CommonPattern.SPACE.split(description).length)} : new Integer[0]);
        }

        if (allAttr || contains(CollectionSchema.author)) add(doc, CollectionSchema.author, md.dc_creator());
        if (allAttr || contains(CollectionSchema.last_modified)) add(doc, CollectionSchema.last_modified, md.moddate());
        if (allAttr || contains(CollectionSchema.wordcount_i)) add(doc, CollectionSchema.wordcount_i, md.wordCount());

        String keywords = md.dc_subject();
    	Bitfield flags = md.flags();
    	if (flags.get(Condenser.flag_cat_indexof)) {
    		if (keywords == null || keywords.isEmpty()) keywords = "indexof"; else {
    			if (keywords.indexOf(',') > 0) keywords += ", indexof"; else keywords += " indexof";
    		}
    	}
        if (allAttr || contains(CollectionSchema.keywords)) {
        	add(doc, CollectionSchema.keywords, keywords);
        }

        if (allAttr || contains(CollectionSchema.imagescount_i)) add(doc, CollectionSchema.imagescount_i, md.limage());
        if (allAttr || contains(CollectionSchema.linkscount_i)) add(doc, CollectionSchema.linkscount_i, md.llocal() + md.lother());
        if (allAttr || contains(CollectionSchema.inboundlinkscount_i)) add(doc, CollectionSchema.inboundlinkscount_i, md.llocal());
        if (allAttr || contains(CollectionSchema.outboundlinkscount_i)) add(doc, CollectionSchema.outboundlinkscount_i, md.lother());
        if (allAttr || contains(CollectionSchema.charset_s)) add(doc, CollectionSchema.charset_s, "UTF-8");

        // coordinates
        if (md.lat() != 0.0 && md.lon() != 0.0) {
            if (allAttr || contains(CollectionSchema.coordinate_p)) add(doc, CollectionSchema.coordinate_p, Double.toString(md.lat()) + "," + Double.toString(md.lon()));
        }
        if (allAttr || contains(CollectionSchema.httpstatus_i)) add(doc, CollectionSchema.httpstatus_i, 200);

        // fields that are in URIMetadataRow additional to yacy2solr basic requirement
        if (allAttr || contains(CollectionSchema.load_date_dt)) add(doc, CollectionSchema.load_date_dt, md.loaddate());
        if (allAttr || contains(CollectionSchema.fresh_date_dt)) add(doc, CollectionSchema.fresh_date_dt, md.freshdate());
        if ((allAttr || contains(CollectionSchema.referrer_id_s)) && md.referrerHash() != null) add(doc, CollectionSchema.referrer_id_s, ASCII.String(md.referrerHash()));
        if (allAttr || contains(CollectionSchema.md5_s)) add(doc, CollectionSchema.md5_s, md.md5());
        if (allAttr || contains(CollectionSchema.publisher_t)) add(doc, CollectionSchema.publisher_t, md.dc_publisher());
        if (allAttr || contains(CollectionSchema.language_s)) add(doc, CollectionSchema.language_s, md.language());
        if (allAttr || contains(CollectionSchema.size_i)) add(doc, CollectionSchema.size_i, md.size());
        if (allAttr || contains(CollectionSchema.audiolinkscount_i)) add(doc, CollectionSchema.audiolinkscount_i, md.laudio());
        if (allAttr || contains(CollectionSchema.videolinkscount_i)) add(doc, CollectionSchema.videolinkscount_i, md.lvideo());
        if (allAttr || contains(CollectionSchema.applinkscount_i)) add(doc, CollectionSchema.applinkscount_i, md.lapp());
        if (allAttr || contains(CollectionSchema.text_t)) {
        	// construct the text from other metadata parts.
        	// This is necessary here since that is used to search the link when no other data (parsed text body) is available
        	StringBuilder sb = new StringBuilder(120);
        	accText(sb, md.dc_title());
        	accText(sb, md.dc_creator());
        	accText(sb, md.dc_publisher());
        	accText(sb, md.snippet());
        	accText(sb, md.url().toTokens());
        	accText(sb, keywords);
        	add(doc, CollectionSchema.text_t, sb.toString());
        }

        return doc;
    }

    private static void accText(final StringBuilder sb, String text) {
    	if (text == null || text.length() == 0) return;
    	if (sb.length() != 0) sb.append(' ');
    	text = text.trim();
    	if (!text.isEmpty() && text.charAt(text.length() - 1) == '.') sb.append(text); else sb.append(text).append('.');
    }
    
    public static class Subgraph {
        public final ArrayList<String>[] urlProtocols, urlStubs, urlAnchorTexts;
        @SuppressWarnings("unchecked")
        public Subgraph(int inboundSize, int outboundSize) {
            this.urlProtocols = new ArrayList[]{new ArrayList<String>(inboundSize), new ArrayList<String>(outboundSize)};
            this.urlStubs = new ArrayList[]{new ArrayList<String>(inboundSize), new ArrayList<String>(outboundSize)};
            this.urlAnchorTexts = new ArrayList[]{new ArrayList<String>(inboundSize), new ArrayList<String>(outboundSize)};
        }
    }
    
    public static boolean enrichSubgraph(final Subgraph subgraph, final DigestURL source_url, AnchorURL target_url) {
        final String text = target_url.getTextProperty(); // the text between the <a></a> tag
        String source_host = source_url.getHost();
        String target_host = target_url.getHost();
        boolean inbound =
                (source_host == null && target_host == null) || 
                (source_host != null && target_host != null &&
                 (target_host.equals(source_host) ||
                  target_host.equals("www." + source_host) ||
                  source_host.equals("www." + target_host))); // well, not everybody defines 'outbound' that way but however, thats used here.
        final String target_url_string = target_url.toNormalform(false);
        int pr_target = target_url_string.indexOf("://",0);
        int ioidx = inbound ? 0 : 1;
        subgraph.urlProtocols[ioidx].add(target_url_string.substring(0, pr_target));
        subgraph.urlStubs[ioidx].add(target_url_string.substring(pr_target + 3));
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
        private List<SolrInputDocument> webgraphDocuments;
        public SolrVector() {
            super();
            this.webgraphDocuments = new ArrayList<SolrInputDocument>();
        }
        public void addWebgraphDocument(SolrInputDocument webgraphDocument) {
            this.webgraphDocuments.add(webgraphDocument);
        }
        public List<SolrInputDocument> getWebgraphDocuments() {
            return this.webgraphDocuments;
        }
    }
    
    public SolrVector yacy2solr(
            final Map<String, Pattern> collections, final ResponseHeader responseHeader,
            final Document document, final Condenser condenser, final DigestURL referrerURL, final String language,
            final WebgraphConfiguration webgraph, final String sourceName) {
        // we use the SolrCell design as index schema
        SolrVector doc = new SolrVector();
        final DigestURL digestURL = document.dc_source();
        final String id = ASCII.String(digestURL.hash());
        boolean allAttr = this.isEmpty();
        String url = addURIAttributes(doc, allAttr, digestURL, Response.docType(digestURL));
        
        Set<ProcessType> processTypes = new LinkedHashSet<ProcessType>();
        
        String us = digestURL.toNormalform(true);
        
        int crawldepth = document.getDepth();
        if ((allAttr || contains(CollectionSchema.crawldepth_i))) {
            CollectionSchema.crawldepth_i.add(doc, crawldepth);
        }
        
        if (allAttr || (contains(CollectionSchema.cr_host_chance_d) && contains(CollectionSchema.cr_host_count_i) && contains(CollectionSchema.cr_host_norm_i))) {
            processTypes.add(ProcessType.CITATION); // postprocessing needed
        }
        
        if (allAttr || contains(CollectionSchema.collection_sxt) && collections != null && collections.size() > 0) {
            List<String> cs = new ArrayList<String>();
            for (Map.Entry<String, Pattern> e: collections.entrySet()) {
                if (e.getValue().matcher(url).matches()) cs.add(e.getKey());
            }
            add(doc, CollectionSchema.collection_sxt, cs);
        }
        
        List<String> titles = document.titles();
        if (allAttr || contains(CollectionSchema.title)) {
            add(doc, CollectionSchema.title, titles);
            if ((allAttr || contains(CollectionSchema.title_exact_signature_l)) && titles.size() > 0) {
                add(doc, CollectionSchema.title_exact_signature_l, EnhancedTextProfileSignature.getSignatureLong(titles.get(0)));
            }
            
        }
        if (allAttr || contains(CollectionSchema.title_count_i)) add(doc, CollectionSchema.title_count_i, titles.size());
        if (allAttr || contains(CollectionSchema.title_chars_val)) {
            ArrayList<Integer> cv = new ArrayList<Integer>(titles.size());
            for (String s: titles) cv.add(new Integer(s.length()));
            add(doc, CollectionSchema.title_chars_val, cv);
        }
        if (allAttr || contains(CollectionSchema.title_words_val)) {
            ArrayList<Integer> cv = new ArrayList<Integer>(titles.size());
            for (String s: titles) cv.add(new Integer(CommonPattern.SPACE.split(s).length));
            add(doc, CollectionSchema.title_words_val, cv);
        }

        String[] descriptions = document.dc_description();
        if (allAttr || contains(CollectionSchema.description_txt)) {
            add(doc, CollectionSchema.description_txt, descriptions);
            if ((allAttr || contains(CollectionSchema.description_exact_signature_l)) && descriptions != null && descriptions.length > 0) {
                add(doc, CollectionSchema.description_exact_signature_l, EnhancedTextProfileSignature.getSignatureLong(descriptions));
            }
        }
        if (allAttr || contains(CollectionSchema.description_count_i)) add(doc, CollectionSchema.description_count_i, descriptions.length);
        if (allAttr || contains(CollectionSchema.description_chars_val)) {
            ArrayList<Integer> cv = new ArrayList<Integer>(descriptions.length);
            for (String s: descriptions) cv.add(new Integer(s.length()));
            add(doc, CollectionSchema.description_chars_val, cv);
        }
        if (allAttr || contains(CollectionSchema.description_words_val)) {
            ArrayList<Integer> cv = new ArrayList<Integer>(descriptions.length);
            for (String s: descriptions) cv.add(new Integer(CommonPattern.SPACE.split(s).length));
            add(doc, CollectionSchema.description_words_val, cv);
        }

        if (allAttr || contains(CollectionSchema.author)) {
            String author = document.dc_creator();
            if (author == null || author.length() == 0) author = document.dc_publisher();
            add(doc, CollectionSchema.author, author);
        }
        if (allAttr || contains(CollectionSchema.content_type)) add(doc, CollectionSchema.content_type, new String[]{document.dc_format()});
        if (allAttr || contains(CollectionSchema.last_modified)) {
            Date lastModified = responseHeader == null ? new Date() : responseHeader.lastModified();
            if (document.getDate().before(lastModified)) lastModified = document.getDate();
            add(doc, CollectionSchema.last_modified, lastModified);
        }
        if (allAttr || contains(CollectionSchema.keywords)) add(doc, CollectionSchema.keywords, document.dc_subject(' '));
        if (allAttr || contains(CollectionSchema.synonyms_sxt)) {
            List<String> synonyms = condenser.synonyms();
            add(doc, CollectionSchema.synonyms_sxt, synonyms);
        }
        add(doc, CollectionSchema.exact_signature_l, condenser.exactSignature());
        add(doc, CollectionSchema.exact_signature_unique_b, true); // this must be corrected afterwards during storage!
        add(doc, CollectionSchema.exact_signature_copycount_i, 0); // this must be corrected afterwards during postprocessing!
        add(doc, CollectionSchema.fuzzy_signature_l, condenser.fuzzySignature());
        add(doc, CollectionSchema.fuzzy_signature_text_t, condenser.fuzzySignatureText());
        add(doc, CollectionSchema.fuzzy_signature_unique_b, true); // this must be corrected afterwards during storage!
        add(doc, CollectionSchema.fuzzy_signature_copycount_i, 0); // this must be corrected afterwards during postprocessing!
        if (this.contains(CollectionSchema.exact_signature_unique_b) || this.contains(CollectionSchema.exact_signature_copycount_i) ||
            this.contains(CollectionSchema.fuzzy_signature_l) || this.contains(CollectionSchema.fuzzy_signature_copycount_i)) {
            processTypes.add(ProcessType.UNIQUE); 
        }
        
        // get list of all links; they will be shrinked by urls that appear in other fields of the solr schema
        LinkedHashMap<DigestURL,String> inboundLinks = document.inboundLinks();
        LinkedHashMap<DigestURL,String> outboundLinks = document.outboundLinks();

        Subgraph subgraph = new Subgraph(inboundLinks.size(), outboundLinks.size());
        List<ImageEntry> images = new ArrayList<ImageEntry>();
        int c = 0;
        final Object parser = document.getParserObject();
        boolean containsCanonical = false;
        if (parser instanceof ContentScraper) {
            final ContentScraper html = (ContentScraper) parser;
            images = html.getImages();

            // header tags
            int h = 0;
            int f = 1;
            String[] hs;

            hs = html.getHeadlines(1); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, CollectionSchema.h1_txt, hs); add(doc, CollectionSchema.h1_i, hs.length);
            hs = html.getHeadlines(2); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, CollectionSchema.h2_txt, hs); add(doc, CollectionSchema.h2_i, hs.length);
            hs = html.getHeadlines(3); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, CollectionSchema.h3_txt, hs); add(doc, CollectionSchema.h3_i, hs.length);
            hs = html.getHeadlines(4); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, CollectionSchema.h4_txt, hs); add(doc, CollectionSchema.h4_i, hs.length);
            hs = html.getHeadlines(5); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, CollectionSchema.h5_txt, hs); add(doc, CollectionSchema.h5_i, hs.length);
            hs = html.getHeadlines(6); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, CollectionSchema.h6_txt, hs); add(doc, CollectionSchema.h6_i, hs.length);
       
            add(doc, CollectionSchema.htags_i, h);
            add(doc, CollectionSchema.schema_org_breadcrumb_i, html.breadcrumbCount());

            // meta tags: Open Graph properties
            String og;
            og = html.getMetas().get("og:title"); if (og != null) add(doc, CollectionSchema.opengraph_title_t, og);
            og = html.getMetas().get("og:type"); if (og != null) add(doc, CollectionSchema.opengraph_type_s, og);
            og = html.getMetas().get("og:url"); if (og != null) add(doc, CollectionSchema.opengraph_url_s, og);
            og = html.getMetas().get("og:image"); if (og != null) add(doc, CollectionSchema.opengraph_image_s, og);

            // noindex and nofollow attributes
            // from HTML (meta-tag in HTML header: robots)
            // and HTTP header (X-Robots-Tag property)
            // coded as binary value:
            // bit  0: "all" contained in html header meta
            // bit  1: "index" contained in html header meta
            // bit  2: "follow" contained in html header meta
            // bit  3: "noindex" contained in html header meta
            // bit  4: "nofollow" contained in html header meta
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
            final String robots_meta = html.getMetas().get("robots");
            // this tag may have values: all, index, noindex, nofollow; see http://www.robotstxt.org/meta.html
            if (robots_meta != null) {
                if (robots_meta.indexOf("all",0) >= 0) b += 1;      // set bit 0
                if (robots_meta.indexOf("index",0) == 0 || robots_meta.indexOf(" index",0) >= 0 || robots_meta.indexOf(",index",0) >= 0 ) b += 2; // set bit 1
                if (robots_meta.indexOf("follow",0) == 0 || robots_meta.indexOf(" follow",0) >= 0 || robots_meta.indexOf(",follow",0) >= 0 ) b += 4; // set bit 2
                if (robots_meta.indexOf("noindex",0) >= 0) b += 8;  // set bit 3
                if (robots_meta.indexOf("nofollow",0) >= 0) b += 16; // set bit 4
            }
            String x_robots_tag = "";
            if (responseHeader != null) {
                x_robots_tag = responseHeader.get(HeaderFramework.X_ROBOTS_TAG, "");
                if (x_robots_tag.isEmpty()) {
                    x_robots_tag = responseHeader.get(HeaderFramework.X_ROBOTS, "");
                }
            }
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
            add(doc, CollectionSchema.robots_i, b);

            // meta tags: generator
            final String generator = html.getMetas().get("generator");
            if (generator != null) add(doc, CollectionSchema.metagenerator_t, generator);

            // bold, italic
            final String[] bold = html.getBold();
            add(doc, CollectionSchema.boldcount_i, bold.length);
            if (bold.length > 0) {
                add(doc, CollectionSchema.bold_txt, bold);
                if (allAttr || contains(CollectionSchema.bold_val)) {
                    add(doc, CollectionSchema.bold_val, html.getBoldCount(bold));
                }
            }
            final String[] italic = html.getItalic();
            add(doc, CollectionSchema.italiccount_i, italic.length);
            if (italic.length > 0) {
                add(doc, CollectionSchema.italic_txt, italic);
                if (allAttr || contains(CollectionSchema.italic_val)) {
                    add(doc, CollectionSchema.italic_val, html.getItalicCount(italic));
                }
            }
            final String[] underline = html.getUnderline();
            add(doc, CollectionSchema.underlinecount_i, underline.length);
            if (underline.length > 0) {
                add(doc, CollectionSchema.underline_txt, underline);
                if (allAttr || contains(CollectionSchema.underline_val)) {
                    add(doc, CollectionSchema.underline_val, html.getUnderlineCount(underline));
                }
            }
            final String[] li = html.getLi();
            add(doc, CollectionSchema.licount_i, li.length);
            if (li.length > 0) add(doc, CollectionSchema.li_txt, li);

            // images
            final ArrayList<String> imgprots = new ArrayList<String>(images.size());
            final Integer[] imgheights = new Integer[images.size()];
            final Integer[] imgwidths = new Integer[images.size()];
            final Integer[] imgpixels = new Integer[images.size()];
            final String[] imgstubs = new String[images.size()];
            final String[] imgalts  = new String[images.size()];
            int withalt = 0;
            int i = 0;
            LinkedHashSet<String> images_text_map = new LinkedHashSet<String>();
            for (final ImageEntry ie: images) {
                final MultiProtocolURL uri = ie.url();
                inboundLinks.remove(uri);
                outboundLinks.remove(uri);
                imgheights[i] = ie.height();
                imgwidths[i] = ie.width();
                imgpixels[i] = ie.height() < 0 || ie.width() < 0 ? -1 : ie.height() * ie.width();
                String protocol = uri.getProtocol();
                imgprots.add(protocol);
                imgstubs[i] = uri.toString().substring(protocol.length() + 3);
                imgalts[i] = ie.alt();
                for (String it: uri.toTokens().split(" ")) images_text_map.add(it);
                if (ie.alt() != null && ie.alt().length() > 0) {
                    SentenceReader sr = new SentenceReader(ie.alt());
                    while (sr.hasNext()) images_text_map.add(sr.next().toString());
                    withalt++;
                }
                i++;
            }
            StringBuilder images_text = new StringBuilder(images_text_map.size() * 6 + 1);
            for (String s: images_text_map) images_text.append(s.trim()).append(' ');
            if (allAttr || contains(CollectionSchema.imagescount_i)) add(doc, CollectionSchema.imagescount_i, images.size());
            if (allAttr || contains(CollectionSchema.images_protocol_sxt)) add(doc, CollectionSchema.images_protocol_sxt, protocolList2indexedList(imgprots));
            if (allAttr || contains(CollectionSchema.images_urlstub_sxt)) add(doc, CollectionSchema.images_urlstub_sxt, imgstubs);
            if (allAttr || contains(CollectionSchema.images_alt_sxt)) add(doc, CollectionSchema.images_alt_sxt, imgalts);
            if (allAttr || contains(CollectionSchema.images_height_val)) add(doc, CollectionSchema.images_height_val, imgheights);
            if (allAttr || contains(CollectionSchema.images_width_val)) add(doc, CollectionSchema.images_width_val, imgwidths);
            if (allAttr || contains(CollectionSchema.images_pixel_val)) add(doc, CollectionSchema.images_pixel_val, imgpixels);
            if (allAttr || contains(CollectionSchema.images_withalt_i)) add(doc, CollectionSchema.images_withalt_i, withalt);
            if (allAttr || contains(CollectionSchema.images_text_t)) add(doc, CollectionSchema.images_text_t, images_text.toString().trim());

            // style sheets
            if (allAttr || contains(CollectionSchema.css_tag_sxt)) {
                final Map<DigestURL, String> csss = html.getCSS();
                final String[] css_tag = new String[csss.size()];
                final String[] css_url = new String[csss.size()];
                c = 0;
                for (final Map.Entry<DigestURL, String> entry: csss.entrySet()) {
                    final String cssurl = entry.getKey().toNormalform(false);
                    inboundLinks.remove(cssurl);
                    outboundLinks.remove(cssurl);
                    css_tag[c] =
                        "<link rel=\"stylesheet\" type=\"text/css\" media=\"" + entry.getValue() + "\"" +
                        " href=\""+ cssurl + "\" />";
                    css_url[c] = cssurl;
                    c++;
                }
                add(doc, CollectionSchema.csscount_i, css_tag.length);
                if (css_tag.length > 0) add(doc, CollectionSchema.css_tag_sxt, css_tag);
                if (css_url.length > 0) add(doc, CollectionSchema.css_url_sxt, css_url);
            }

            // Scripts
            if (allAttr || contains(CollectionSchema.scripts_sxt)) {
                final Set<DigestURL> scriptss = html.getScript();
                final String[] scripts = new String[scriptss.size()];
                c = 0;
                for (final DigestURL u: scriptss) {
                    inboundLinks.remove(u);
                    outboundLinks.remove(u);
                    scripts[c++] = u.toNormalform(false);
                }
                add(doc, CollectionSchema.scriptscount_i, scripts.length);
                if (scripts.length > 0) add(doc, CollectionSchema.scripts_sxt, scripts);
            }

            // Frames
            if (allAttr || contains(CollectionSchema.frames_sxt)) {
                final Set<DigestURL> framess = html.getFrames();
                final String[] frames = new String[framess.size()];
                c = 0;
                for (final DigestURL u: framess) {
                    inboundLinks.remove(u);
                    outboundLinks.remove(u);
                    frames[c++] = u.toNormalform(false);
                }
                add(doc, CollectionSchema.framesscount_i, frames.length);
                if (frames.length > 0) {
                    add(doc, CollectionSchema.frames_sxt, frames);
                    //webgraph.addEdges(subgraph, digestURI, responseHeader, collections, crawldepth, alllinks, images, true, framess, citations); // add here because links have been removed from remaining inbound/outbound
                }
            }

            // IFrames
            if (allAttr || contains(CollectionSchema.iframes_sxt)) {
                final Set<DigestURL> iframess = html.getIFrames();
                final String[] iframes = new String[iframess.size()];
                c = 0;
                for (final DigestURL u: iframess) {
                    inboundLinks.remove(u);
                    outboundLinks.remove(u);
                    iframes[c++] = u.toNormalform(false);
                }
                add(doc, CollectionSchema.iframesscount_i, iframes.length);
                if (iframes.length > 0) {
                    add(doc, CollectionSchema.iframes_sxt, iframes);
                    //webgraph.addEdges(subgraph, digestURI, responseHeader, collections, crawldepth, alllinks, images, true, iframess, citations); // add here because links have been removed from remaining inbound/outbound
                }
            }

            // canonical tag
            if (allAttr || contains(CollectionSchema.canonical_s)) {
                DigestURL canonical = html.getCanonical();
                // if there is no canonical in the html then look into the http header:
                if (canonical == null) {
                    String link = responseHeader.get("Link", null);
                    int p;
                    if (link != null && ((p = link.indexOf("rel=\"canonical\"")) > 0)) {
                        link = link.substring(0, p).trim();
                        p = link.indexOf('<');
                        int q = link.lastIndexOf('>');
                        if (p >= 0 && q > 0) {
                            link = link.substring(p + 1, q);
                            try {
                                canonical = new DigestURL(link);
                            } catch (MalformedURLException e) {}
                        }
                    }
                }
                if (canonical != null && !ASCII.String(canonical.hash()).equals(id)) {
                    containsCanonical = true;
                    inboundLinks.remove(canonical);
                    outboundLinks.remove(canonical);
                    add(doc, CollectionSchema.canonical_s, canonical.toNormalform(false));
                    // set a flag if this is equal to sku
                    if (contains(CollectionSchema.canonical_equal_sku_b)) {
                        add(doc, CollectionSchema.canonical_equal_sku_b, canonical.equals(us));
                    }
                }
            }

            // meta refresh tag
            if (allAttr || contains(CollectionSchema.refresh_s)) {
                String refresh = html.getRefreshPath();
                if (refresh != null && refresh.length() > 0) {
                    MultiProtocolURL refreshURL;
                    try {
                        refreshURL = refresh.startsWith("http") ? new MultiProtocolURL(html.getRefreshPath()) : new MultiProtocolURL(digestURL, html.getRefreshPath());
                        if (refreshURL != null) {
                            inboundLinks.remove(refreshURL);
                            outboundLinks.remove(refreshURL);
                            add(doc, CollectionSchema.refresh_s, refreshURL.toNormalform(false));
                        }
                    } catch (final MalformedURLException e) {
                        add(doc, CollectionSchema.refresh_s, refresh);
                    }
                }
            }

            // flash embedded
            if (allAttr || contains(CollectionSchema.flash_b)) {
                MultiProtocolURL[] flashURLs = html.getFlash();
                for (MultiProtocolURL u: flashURLs) {
                    // remove all flash links from ibound/outbound links
                    inboundLinks.remove(u);
                    outboundLinks.remove(u);
                }
                add(doc, CollectionSchema.flash_b, flashURLs.length > 0);
            }

            // generic evaluation pattern
            for (final String model: html.getEvaluationModelNames()) {
                if (allAttr || contains("ext_" + model + "_txt")) {
                    final String[] scorenames = html.getEvaluationModelScoreNames(model);
                    if (scorenames.length > 0) {
                        add(doc, CollectionSchema.valueOf("ext_" + model + "_txt"), scorenames);
                        add(doc, CollectionSchema.valueOf("ext_" + model + "_val"), html.getEvaluationModelScoreCounts(model, scorenames));
                    }
                }
            }

            // response time
            add(doc, CollectionSchema.responsetime_i, responseHeader == null ? 0 : Integer.parseInt(responseHeader.get(HeaderFramework.RESPONSE_TIME_MILLIS, "0")));
            
            // hreflang link tag, see http://support.google.com/webmasters/bin/answer.py?hl=de&answer=189077
            if (allAttr || (contains(CollectionSchema.hreflang_url_sxt) && contains(CollectionSchema.hreflang_cc_sxt))) {
                final String[] ccs = new String[html.getHreflang().size()];
                final String[] urls = new String[html.getHreflang().size()];
                c = 0;
                for (Map.Entry<String, DigestURL> e: html.getHreflang().entrySet()) {
                    ccs[c] = e.getKey();
                    urls[c] = e.getValue().toNormalform(true);
                    c++;
                }
                add(doc, CollectionSchema.hreflang_cc_sxt, ccs);
                add(doc, CollectionSchema.hreflang_url_sxt, urls);
            }

            // page navigation url, see http://googlewebmastercentral.blogspot.de/2011/09/pagination-with-relnext-and-relprev.html
            if (allAttr || (contains(CollectionSchema.navigation_url_sxt) && contains(CollectionSchema.navigation_type_sxt))) {
                final String[] navs = new String[html.getNavigation().size()];
                final String[] urls = new String[html.getNavigation().size()];
                c = 0;
                for (Map.Entry<String, DigestURL> e: html.getNavigation().entrySet()) {
                    navs[c] = e.getKey();
                    urls[c] = e.getValue().toNormalform(true);
                    c++;
                }
                add(doc, CollectionSchema.navigation_type_sxt, navs);
                add(doc, CollectionSchema.navigation_url_sxt, urls);
                
            }

            // publisher url as defined in http://support.google.com/plus/answer/1713826?hl=de
            if (allAttr || contains(CollectionSchema.publisher_url_s) && html.getPublisherLink() != null) {
                add(doc, CollectionSchema.publisher_url_s, html.getPublisherLink().toNormalform(true));
            }
        }

        if (parser instanceof DCEntry) {
            // the document was created with a surrogate parsing; overwrite all md: -entries to Solr
            DCEntry dcentry = (DCEntry) parser;
            for (Map.Entry<String, String[]> entry: dcentry.getMap().entrySet()) {
                String tag = entry.getKey();
                if (!tag.startsWith("md:") || tag.length() < 4) continue;
                CollectionSchema solr_field = CollectionSchema.valueOf(tag.substring(3));
                if (solr_field == null) continue;
                String[] values = entry.getValue();
                if (values == null || values.length == 0) continue;
                if (allAttr || contains(solr_field)) {
                    add(doc, solr_field, values);
                }
            }
        }
        
        String content = document.getTextString();
        String tokens = digestURL.toTokens();
        if (content == null || content.length() == 0) {
            content = tokens;
        } else {
            String[] t = tokens.split(" ");
            for (String r: t) {
                if (r.length() > 0 &&
                    content.indexOf(" " + r + " ") < 0 &&
                    !content.startsWith(r + " ") &&
                    !content.endsWith(" " + r)) content += " " + r;
            }
        }
        
        if ((allAttr || contains(CollectionSchema.images_text_t)) && MultiProtocolURL.isImage(MultiProtocolURL.getFileExtension(digestURL.getFileName()))) {
            add(doc, CollectionSchema.images_text_t, content); // the content may contain the exif data from the image parser
            content = digestURL.toTokens(); // remove all other entry but the url tokens
        }
        
        // content (must be written after special parser data, since this can influence the content)
        if (allAttr || contains(CollectionSchema.text_t)) add(doc, CollectionSchema.text_t, content);
        if (allAttr || contains(CollectionSchema.wordcount_i)) {
            if (content.length() == 0) {
                add(doc, CollectionSchema.wordcount_i, 0);
            } else {
                int contentwc = 1;
                for (int i = content.length() - 1; i >= 0; i--) if (content.charAt(i) == ' ') contentwc++;
                add(doc, CollectionSchema.wordcount_i, contentwc);
            }
        }
        
        // statistics about the links
        if (allAttr || contains(CollectionSchema.linkscount_i)) add(doc, CollectionSchema.linkscount_i, inboundLinks.size() + outboundLinks.size());
        if (allAttr || contains(CollectionSchema.linksnofollowcount_i)) add(doc, CollectionSchema.linksnofollowcount_i, document.inboundLinkNofollowCount() + document.outboundLinkNofollowCount());
        if (allAttr || contains(CollectionSchema.inboundlinkscount_i)) add(doc, CollectionSchema.inboundlinkscount_i, inboundLinks.size());
        if (allAttr || contains(CollectionSchema.inboundlinksnofollowcount_i)) add(doc, CollectionSchema.inboundlinksnofollowcount_i, document.inboundLinkNofollowCount());
        if (allAttr || contains(CollectionSchema.outboundlinkscount_i)) add(doc, CollectionSchema.outboundlinkscount_i, outboundLinks.size());
        if (allAttr || contains(CollectionSchema.outboundlinksnofollowcount_i)) add(doc, CollectionSchema.outboundlinksnofollowcount_i, document.outboundLinkNofollowCount());
        
        // create a subgraph
        if (!containsCanonical && webgraph != null) {
            // a document with canonical tag should not get a webgraph relation, because that belongs to the canonical document
            List<SolrInputDocument> edges = webgraph.getEdges(subgraph, digestURL, responseHeader, collections, crawldepth, images, document.getAnchors(), sourceName);
            // this also enriched the subgraph
            doc.webgraphDocuments.addAll(edges);
        } else {
            if (allAttr ||
                contains(CollectionSchema.inboundlinks_protocol_sxt) ||
                contains(CollectionSchema.inboundlinks_urlstub_sxt) ||
                contains(CollectionSchema.inboundlinks_anchortext_txt) ||
                contains(CollectionSchema.outboundlinks_protocol_sxt) ||
                contains(CollectionSchema.outboundlinks_urlstub_sxt) ||
                contains(CollectionSchema.outboundlinks_anchortext_txt)) {
                for (final AnchorURL target_url: document.getAnchors()) {
                    enrichSubgraph(subgraph, digestURL, target_url);
                }
            }
        }
            
        // attach the subgraph content
        if (allAttr || contains(CollectionSchema.inboundlinks_protocol_sxt)) add(doc, CollectionSchema.inboundlinks_protocol_sxt, protocolList2indexedList(subgraph.urlProtocols[0]));
        if (allAttr || contains(CollectionSchema.inboundlinks_urlstub_sxt)) add(doc, CollectionSchema.inboundlinks_urlstub_sxt, subgraph.urlStubs[0]);
        if (allAttr || contains(CollectionSchema.inboundlinks_anchortext_txt)) add(doc, CollectionSchema.inboundlinks_anchortext_txt, subgraph.urlAnchorTexts[0]);
        if (allAttr || contains(CollectionSchema.outboundlinks_protocol_sxt)) add(doc, CollectionSchema.outboundlinks_protocol_sxt, protocolList2indexedList(subgraph.urlProtocols[1]));
        if (allAttr || contains(CollectionSchema.outboundlinks_urlstub_sxt)) add(doc, CollectionSchema.outboundlinks_urlstub_sxt, subgraph.urlStubs[1]);
        if (allAttr || contains(CollectionSchema.outboundlinks_anchortext_txt)) add(doc, CollectionSchema.outboundlinks_anchortext_txt, subgraph.urlAnchorTexts[1]);
        
        // charset
        if (allAttr || contains(CollectionSchema.charset_s)) add(doc, CollectionSchema.charset_s, document.getCharset());

        // coordinates
        if (document.lat() != 0.0 && document.lon() != 0.0) {
            if (allAttr || contains(CollectionSchema.coordinate_p)) add(doc, CollectionSchema.coordinate_p, Double.toString(document.lat()) + "," + Double.toString(document.lon()));
        }
        if (allAttr || contains(CollectionSchema.httpstatus_i)) add(doc, CollectionSchema.httpstatus_i, responseHeader == null ? 200 : responseHeader.getStatusCode());

        // fields that were additionally in URIMetadataRow
        Date loadDate = new Date();
        Date modDate = responseHeader == null ? new Date() : responseHeader.lastModified();
        if (modDate.getTime() > loadDate.getTime()) modDate = loadDate;
        int size = (int) Math.max(document.dc_source().length(), responseHeader == null ? 0 : responseHeader.getContentLength());
        if (allAttr || contains(CollectionSchema.load_date_dt)) add(doc, CollectionSchema.load_date_dt, loadDate);
        if (allAttr || contains(CollectionSchema.fresh_date_dt)) add(doc, CollectionSchema.fresh_date_dt, new Date(loadDate.getTime() + Math.max(0, loadDate.getTime() - modDate.getTime()) / 2)); // freshdate, computed with Proxy-TTL formula
        if ((allAttr || contains(CollectionSchema.referrer_id_s)) && referrerURL != null) add(doc, CollectionSchema.referrer_id_s, ASCII.String(referrerURL.hash()));
        //if (allAttr || contains(SolrField.md5_s)) add(solrdoc, SolrField.md5_s, new byte[0]);
        if (allAttr || contains(CollectionSchema.publisher_t)) add(doc, CollectionSchema.publisher_t, document.dc_publisher());
        if ((allAttr || contains(CollectionSchema.language_s)) && language != null) add(doc, CollectionSchema.language_s, language);
        if (allAttr || contains(CollectionSchema.size_i)) add(doc, CollectionSchema.size_i, size);
        if (allAttr || contains(CollectionSchema.audiolinkscount_i)) add(doc, CollectionSchema.audiolinkscount_i, document.getAudiolinks().size());
        if (allAttr || contains(CollectionSchema.videolinkscount_i)) add(doc, CollectionSchema.videolinkscount_i, document.getVideolinks().size());
        if (allAttr || contains(CollectionSchema.applinkscount_i)) add(doc, CollectionSchema.applinkscount_i, document.getApplinks().size());

        // write generic navigation
        // there are no pre-defined solr fields for navigation because the vocabulary is generic
        // we use dynamically allocated solr fields for this.
        // It must be a multi-value string/token field, therefore we use _sxt extensions for the field names
        for (Map.Entry<String, Set<String>> facet: document.getGenericFacets().entrySet()) {
            String facetName = facet.getKey();
            Set<String> facetValues = facet.getValue();
            doc.setField(CollectionSchema.VOCABULARY_PREFIX + facetName + CollectionSchema.VOCABULARY_SUFFIX, facetValues.toArray(new String[facetValues.size()]));
        }

        if (allAttr || contains(CollectionSchema.process_sxt)) {
            List<String> p = new ArrayList<String>();
            for (ProcessType t: processTypes) p.add(t.name());
            add(doc, CollectionSchema.process_sxt, p);
            if (allAttr || contains(CollectionSchema.harvestkey_s)) {
                add(doc, CollectionSchema.harvestkey_s, sourceName);
            }
        }
        return doc;
    }

    
    /**
     * post-processing steps for all entries that have a process tag assigned
     * @param connector
     * @param urlCitation
     * @return
     */
    public int postprocessing(final Segment segment, final ReferenceReportCache rrCache, final String harvestkey) {
        if (!this.contains(CollectionSchema.process_sxt)) return 0;
        if (!segment.connectedCitation() && !segment.fulltext().useWebgraph()) return 0;
        final SolrConnector collectionConnector = segment.fulltext().getDefaultConnector();
        collectionConnector.commit(false); // make sure that we have latest information that can be found
        if (segment.fulltext().useWebgraph()) segment.fulltext().getWebgraphConnector().commit(false);
        final CollectionConfiguration collection = segment.fulltext().getDefaultConfiguration();
        final WebgraphConfiguration webgraph = segment.fulltext().getWebgraphConfiguration();
        
 
        // collect hosts from index which shall take part in citation computation
        String query = (harvestkey == null || !segment.fulltext().getDefaultConfiguration().contains(CollectionSchema.harvestkey_s) ? "" : CollectionSchema.harvestkey_s.getSolrFieldName() + ":\"" + harvestkey + "\" AND ") +
                CollectionSchema.process_sxt.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM;
        ReversibleScoreMap<String> hostscore;
        try {
            Map<String, ReversibleScoreMap<String>> hostfacet = collectionConnector.getFacets(query, 10000000, CollectionSchema.host_s.getSolrFieldName());
            hostscore = hostfacet.get(CollectionSchema.host_s.getSolrFieldName());
        } catch (final IOException e2) {
            ConcurrentLog.logException(e2);
            hostscore = new ClusteredScoreMap<String>();
        }
        
        // create the ranking map
        final Map<String, CRV> rankings = new ConcurrentHashMap<String, CRV>();
        if ((segment.fulltext().useWebgraph() &&
             ((webgraph.contains(WebgraphSchema.source_id_s) && webgraph.contains(WebgraphSchema.source_cr_host_norm_i)) ||
              (webgraph.contains(WebgraphSchema.target_id_s) && webgraph.contains(WebgraphSchema.target_cr_host_norm_i))) ||
            (collection.contains(CollectionSchema.cr_host_count_i) &&
             collection.contains(CollectionSchema.cr_host_chance_d) &&
             collection.contains(CollectionSchema.cr_host_norm_i)))) try {
            int concurrency = Math.min(hostscore.size(), Runtime.getRuntime().availableProcessors());
            ConcurrentLog.info("CollectionConfiguration", "collecting " + hostscore.size() + " hosts, concrrency = " + concurrency);
            int countcheck = 0;
            for (String host: hostscore.keyList(true)) {
                // Patch the citation index for links with canonical tags.
                // This shall fulfill the following requirement:
                // If a document A links to B and B contains a 'canonical C', then the citation rank computation shall consider that A links to C and B does not link to C.
                // To do so, we first must collect all canonical links, find all references to them, get the anchor list of the documents and patch the citation reference of these links
                String patchquery = CollectionSchema.host_s.getSolrFieldName() + ":" + host + " AND " + CollectionSchema.canonical_s.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM;
                long patchquerycount = collectionConnector.getCountByQuery(patchquery);
                BlockingQueue<SolrDocument> documents_with_canonical_tag = collectionConnector.concurrentDocumentsByQuery(patchquery, CollectionSchema.url_chars_i.getSolrFieldName() + " asc", 0, 100000000, 86400000, 200, 1,
                        CollectionSchema.id.getSolrFieldName(), CollectionSchema.sku.getSolrFieldName(), CollectionSchema.canonical_s.getSolrFieldName());
                SolrDocument doc_B;
                int patchquerycountcheck = 0;
                try {
                    while ((doc_B = documents_with_canonical_tag.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                        // find all documents which link to the canonical doc
                        DigestURL doc_C_url = new DigestURL((String) doc_B.getFieldValue(CollectionSchema.canonical_s.getSolrFieldName()));
                        byte[] doc_B_id = ASCII.getBytes(((String) doc_B.getFieldValue(CollectionSchema.id.getSolrFieldName())));
                        // we remove all references to B, because these become references to C
                        if (segment.connectedCitation()) {
                            ReferenceContainer<CitationReference> doc_A_ids = segment.urlCitation().remove(doc_B_id);
                            if (doc_A_ids == null) {
                                //System.out.println("*** document with canonical but no referrer: " + doc_B.getFieldValue(CollectionSchema.sku.getSolrFieldName()));
                                continue; // the document has a canonical tag but no referrer?
                            }
                            Iterator<CitationReference> doc_A_ids_iterator = doc_A_ids.entries();
                            // for each of the referrer A of B, set A as a referrer of C
                            while (doc_A_ids_iterator.hasNext()) {
                                CitationReference doc_A_citation = doc_A_ids_iterator.next();
                                segment.urlCitation().add(doc_C_url.hash(), doc_A_citation);
                            }
                        }
                        patchquerycountcheck++;
                        if (MemoryControl.shortStatus()) {
                            ConcurrentLog.warn("CollectionConfiguration", "terminated canonical collection during postprocessing because of short memory");
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    ConcurrentLog.logException(e);
                } catch (SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                }
                if (patchquerycount != patchquerycountcheck) ConcurrentLog.warn("CollectionConfiguration", "ambiguous patchquery count for host " + host + ": expected=" + patchquerycount + ", counted=" + patchquerycountcheck);
                
                // do the citation rank computation
                if (hostscore.get(host) <= 0) continue;
                // select all documents for each host
                CRHost crh = new CRHost(segment, rrCache, host, 0.85d, 6);
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
                Map<String, CRV> crn = crh.normalize();
                //crh.log(crn);
                rankings.putAll(crn); // accumulate this here for usage in document update later
                if (MemoryControl.shortStatus()) {
                    ConcurrentLog.warn("CollectionConfiguration", "terminated crn akkumulation during postprocessing because of short memory");
                    break;
                }
                countcheck++;
            }
            if (hostscore.size() != countcheck) ConcurrentLog.warn("CollectionConfiguration", "ambiguous host count: expected=" + hostscore.size() + ", counted=" + countcheck);
        } catch (final IOException e2) {
            ConcurrentLog.logException(e2);
            hostscore = new ClusteredScoreMap<String>();
        }
        
        // process all documents at the webgraph for the outgoing links of this document
        final AtomicInteger allcount = new AtomicInteger(0);
        if (segment.fulltext().useWebgraph()) {
            final Set<String> omitFields = new HashSet<String>();
            omitFields.add(WebgraphSchema.process_sxt.getSolrFieldName());
            omitFields.add(WebgraphSchema.harvestkey_s.getSolrFieldName());
            try {
                final long start = System.currentTimeMillis();
                for (String host: hostscore.keyList(true)) {
                    if (hostscore.get(host) <= 0) continue;
                    final String hostfinal = host;
                    // select all webgraph edges and modify their cr value
                    query = WebgraphSchema.source_host_s.getSolrFieldName() + ":\"" + host + "\" AND " + WebgraphSchema.process_sxt.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM;
                    final long count = segment.fulltext().getWebgraphConnector().getCountByQuery(query);
                    int concurrency = Math.min((int) count, Math.max(1, Runtime.getRuntime().availableProcessors() / 4));
                    ConcurrentLog.info("CollectionConfiguration", "collecting " + count + " documents from the webgraph, concurrency = " + concurrency);
                    final BlockingQueue<SolrDocument> docs = segment.fulltext().getWebgraphConnector().concurrentDocumentsByQuery(query, WebgraphSchema.source_chars_i.getSolrFieldName() + " asc", 0, 100000000, 86400000, 200, concurrency);
                    final AtomicInteger proccount = new AtomicInteger(0);
                    Thread[] t = new Thread[concurrency];
                    for (final AtomicInteger i = new AtomicInteger(0); i.get() < t.length; i.incrementAndGet()) {
                        t[i.get()] = new Thread() {
                            private String name = "CollectionConfiguration.postprocessing.webgraph-" + i.get();
                            @Override
                            public void run() {
                                Thread.currentThread().setName(name);
                                SolrDocument doc; String id;
                                try {
                                    while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                                        SolrInputDocument sid = webgraph.toSolrInputDocument(doc, omitFields);
                                        Collection<Object> proctags = doc.getFieldValues(WebgraphSchema.process_sxt.getSolrFieldName());
                                        Set<ProcessType> process = new HashSet<ProcessType>();
                                        for (Object tag: proctags) {
                                            ProcessType tagtype = ProcessType.valueOf((String) tag);
                                            process.add(tagtype);
                                        }
                                        
                                        // set cr values
                                        if (webgraph.contains(WebgraphSchema.source_id_s) && webgraph.contains(WebgraphSchema.source_cr_host_norm_i)) {
                                            id = (String) doc.getFieldValue(WebgraphSchema.source_id_s.getSolrFieldName());
                                            CRV crv = rankings.get(id);
                                            if (crv != null) {
                                                sid.setField(WebgraphSchema.source_cr_host_norm_i.getSolrFieldName(), crv.crn);
                                            }
                                        }
                                        if (webgraph.contains(WebgraphSchema.target_id_s) && webgraph.contains(WebgraphSchema.target_cr_host_norm_i)) {
                                            id = (String) doc.getFieldValue(WebgraphSchema.target_id_s.getSolrFieldName());
                                            CRV crv = rankings.get(id);
                                            if (crv != null) {
                                                sid.setField(WebgraphSchema.target_cr_host_norm_i.getSolrFieldName(), crv.crn);
                                            }
                                        }
                                        
                                        // write document back to index
                                        try {
                                            sid.removeField(WebgraphSchema.process_sxt.getSolrFieldName());
                                            sid.removeField(WebgraphSchema.harvestkey_s.getSolrFieldName());
                                            segment.fulltext().getWebgraphConnector().deleteById((String) sid.getFieldValue(WebgraphSchema.id.getSolrFieldName()));
                                            segment.fulltext().getWebgraphConnector().add(sid);
                                        } catch (SolrException e) {
                                            ConcurrentLog.logException(e);
                                        } catch (IOException e) {
                                            ConcurrentLog.logException(e);
                                        }
                                        proccount.incrementAndGet();
                                        allcount.incrementAndGet();
                                        if (proccount.get() % 1000 == 0) ConcurrentLog.info(
                                                "CollectionConfiguration", "webgraph - postprocessed " + proccount + " from " + count + " documents; " +
                                                (proccount.get() * 1000 / (System.currentTimeMillis() - start)) + " docs/second; " +
                                                ((System.currentTimeMillis() - start) * (count - proccount.get()) / proccount.get() / 60000) + " minutes remaining for host " + hostfinal);
                                    }
                                } catch (InterruptedException e) {
                                    ConcurrentLog.warn("CollectionConfiguration", e.getMessage(), e);
                                }
                            }
                        };
                        t[i.get()].start();
                    }
                    for (int i = 0; i < t.length; i++) try {t[i].join();} catch (InterruptedException e) {}
                    
                    if (count != proccount.get()) ConcurrentLog.warn("CollectionConfiguration", "ambiguous webgraph document count for host " + host + ": expected=" + count + ", counted=" + proccount);
                }
            } catch (final IOException e2) {
                ConcurrentLog.warn("CollectionConfiguration", e2.getMessage(), e2);
            }
        }
        
        // process all documents in collection
        query = (harvestkey == null || !segment.fulltext().getDefaultConfiguration().contains(CollectionSchema.harvestkey_s) ? "" : CollectionSchema.harvestkey_s.getSolrFieldName() + ":\"" + harvestkey + "\" AND ") +
                CollectionSchema.process_sxt.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM;
        Map<String, Long> hostExtentCache = new HashMap<String, Long>(); // a mapping from the host id to the number of documents which contain this host-id
        Set<String> uniqueURLs = new HashSet<String>();
        try {
            Set<String> omitFields = new HashSet<String>();
            omitFields.add(CollectionSchema.process_sxt.getSolrFieldName());
            omitFields.add(CollectionSchema.harvestkey_s.getSolrFieldName());
            int proccount = 0, proccount_referencechange = 0, proccount_citationchange = 0, proccount_uniquechange = 0;
            long count = collectionConnector.getCountByQuery(query);
            long start = System.currentTimeMillis();
            ConcurrentLog.info("CollectionConfiguration", "collecting " + count + " documents from the collection for harvestkey " + harvestkey);
            BlockingQueue<SolrDocument> docs = collectionConnector.concurrentDocumentsByQuery(query, CollectionSchema.url_chars_i.getSolrFieldName() + " asc", 0, 100000000, 86400000, 200, 1);
            int countcheck = 0;
            Collection<String> failids = new ArrayList<String>();
            SolrDocument doc;
            while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                // for each to-be-processed entry work on the process tag
                Collection<Object> proctags = doc.getFieldValues(CollectionSchema.process_sxt.getSolrFieldName());
                final String u = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
                final String i = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
                try {
                    DigestURL url = new DigestURL(u, ASCII.getBytes(i));
                    byte[] id = url.hash();
                    SolrInputDocument sid = collection.toSolrInputDocument(doc, omitFields);
                    
                    for (Object tag: proctags) try {
                        
                        // switch over tag types
                        ProcessType tagtype = ProcessType.valueOf((String) tag);

                        if (tagtype == ProcessType.CITATION &&
                            collection.contains(CollectionSchema.cr_host_count_i) &&
                            collection.contains(CollectionSchema.cr_host_chance_d) &&
                            collection.contains(CollectionSchema.cr_host_norm_i)) {
                            CRV crv = rankings.remove(ASCII.String(id)); // instead of 'get'ting the CRV, we also remove it because we will not need it again and free some memory here
                            if (crv != null) {
                                sid.setField(CollectionSchema.cr_host_count_i.getSolrFieldName(), crv.count);
                                sid.setField(CollectionSchema.cr_host_chance_d.getSolrFieldName(), crv.cr);
                                sid.setField(CollectionSchema.cr_host_norm_i.getSolrFieldName(), crv.crn);
                                proccount_citationchange++;
                            }
                        }

                        if (tagtype == ProcessType.UNIQUE) {
                            if (postprocessing_doublecontent(segment, uniqueURLs, sid, url)) proccount_uniquechange++;
                        }
                        
                    } catch (IllegalArgumentException e) {}
                    
                    // refresh the link count; it's 'cheap' to do this here
                    String hosthash = url.hosthash();
                    if (!hostExtentCache.containsKey(hosthash)) {
                        StringBuilder q = new StringBuilder();
                        q.append(CollectionSchema.host_id_s.getSolrFieldName()).append(":\"").append(hosthash).append("\" AND ").append(CollectionSchema.httpstatus_i.getSolrFieldName()).append(":200");
                        long hostExtentCount = segment.fulltext().getDefaultConnector().getCountByQuery(q.toString());
                        hostExtentCache.put(hosthash, hostExtentCount);
                    }
                    if (postprocessing_references(rrCache, sid, url, hostExtentCache)) proccount_referencechange++;
                    
                    // all processing steps checked, remove the processing and harvesting key
                    sid.removeField(CollectionSchema.process_sxt.getSolrFieldName());
                    sid.removeField(CollectionSchema.harvestkey_s.getSolrFieldName());
                    
                    // send back to index
                    collectionConnector.deleteById(i);
                    collectionConnector.add(sid);
                    
                    proccount++; allcount.incrementAndGet();
                    if (proccount % 100 == 0) ConcurrentLog.info(
                            "CollectionConfiguration", "collection - postprocessed " + proccount + " from " + count + " documents; " +
                            (proccount * 1000 / (System.currentTimeMillis() - start)) + " docs/second; " +
                            ((System.currentTimeMillis() - start) * (count - proccount) / proccount / 60000) + " minutes remaining");
                } catch (final Throwable e1) {
                    ConcurrentLog.logException(e1);
                    failids.add(i);
                }
                countcheck++;
            }
            if (failids.size() > 0) {
                ConcurrentLog.info("CollectionConfiguration", "cleanup_processing: deleting " + failids.size() + " documents which have permanent execution fails");
                collectionConnector.deleteByIds(failids);
            }
            if (count != countcheck) ConcurrentLog.warn("CollectionConfiguration", "ambiguous collection document count for harvestkey " + harvestkey + ": expected=" + count + ", counted=" + countcheck); // big gap for harvestkey = null
            ConcurrentLog.info("CollectionConfiguration", "cleanup_processing: re-calculated " + proccount+ " new documents, " +
                        proccount_referencechange + " reference-count changes, " +
                        proccount_uniquechange + " unique field changes, " +
                        proccount_citationchange + " citation ranking changes.");
        } catch (final InterruptedException e2) {
            ConcurrentLog.warn("CollectionConfiguration", e2.getMessage(), e2);
        } catch (IOException e3) {
            ConcurrentLog.warn("CollectionConfiguration", e3.getMessage(), e3);
        }
        return allcount.get();
    }

    private static final class CRV {
        public double cr;
        public int crn, count;
        public CRV(final int count, final double cr, final int crn) {this.count = count; this.cr = cr; this.crn = crn;}
        @Override
        public String toString() {
            return "count=" + count + ", cr=" + cr + ", crn=" + crn;
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
        private double damping;
        private int converge_eq_factor;
        private ReferenceReportCache rrCache;
        public CRHost(final Segment segment, final ReferenceReportCache rrCache, final String host, final double damping, final int converge_digits) {
            this.segment = segment;
            this.damping = damping;
            this.rrCache = rrCache;
            this.converge_eq_factor = (int) Math.pow(10.0d, converge_digits);
            SolrConnector connector = segment.fulltext().getDefaultConnector();
            this.crt = new ConcurrentHashMap<String, double[]>();
            try {
                // select all documents for each host
                BlockingQueue<String> ids = connector.concurrentIDsByQuery("{!raw f=" + CollectionSchema.host_s.getSolrFieldName() + "}" + host, CollectionSchema.url_chars_i.getSolrFieldName() + " asc", 0, 100000000, 86400000, 200, 1);
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
            double initval = 1.0d / cr_host_count;
            for (Map.Entry<String, double[]> entry: this.crt.entrySet()) entry.getValue()[0] = initval;
            this.internal_links_counter = new RowHandleMap(12, Base64Order.enhancedCoder, 8, 100, "internal_links_counter");
        }
        /**
         * produce a map from IDs to CRV records, normalization entries containing the values that are stored to solr.
         * @return
         */
        public Map<String, CRV> normalize() {
            final TreeMap<Double, List<byte[]>> reorder = new TreeMap<Double, List<byte[]>>();
            for (Map.Entry<String, double[]> entry: this.crt.entrySet()) {
                Double d = entry.getValue()[0];
                List<byte[]> ds = reorder.get(d);
                if (ds == null) {ds = new ArrayList<byte[]>(); reorder.put(d, ds);}
                ds.add(ASCII.getBytes(entry.getKey()));
            }
            int nextcount = (this.cr_host_count + 1) / 2;
            int nextcrn = 0;
            Map<String, CRV> r = new HashMap<String, CRV>();
            while (reorder.size() > 0) {
                int count = nextcount;
                while (reorder.size() > 0 && count > 0) {
                    Map.Entry<Double, List<byte[]>> next = reorder.pollFirstEntry();
                    List<byte[]> ids = next.getValue();
                    count -= ids.size();
                    double cr = next.getKey();
                    for (byte[] id: ids) r.put(ASCII.String(id), new CRV(this.cr_host_count, cr, nextcrn));
                }
                nextcrn++;
                nextcount = Math.max(1, (nextcount + count + 1) / 2);
            }
            // finally, increase the crn number in such a way that the maximum is always 10
            int inc = 11 - nextcrn; // nextcrn is +1
            for (Map.Entry<String, CRV> entry: r.entrySet()) entry.getValue().crn += inc;
            return r;
        }
        /**
         * log out a complete CRHost set of urls and ranking values
         * @param rm
         */
        @SuppressWarnings("unused")
        public void log(final Map<byte[], CRV> rm) {
            // print out all urls with their cr-values
            SolrConnector connector = segment.fulltext().getDefaultConnector();
            for (Map.Entry<byte[], CRV> entry: rm.entrySet()) {
                if (entry == null || entry.getValue() == null) continue;
                try {
                    Metadata md = connector.getMetadata(ASCII.String(entry.getKey()));
                    ConcurrentLog.info("CollectionConfiguration", "CR for " + md.url);
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
            SolrConnector connector = this.segment.fulltext().getDefaultConnector();
            if (connector == null) return 0;
            try {
                SolrDocument doc = connector.getDocumentById(ASCII.String(id), CollectionSchema.inboundlinkscount_i.getSolrFieldName());
                if (doc == null) {
                    this.internal_links_counter.put(id, 0);
                    return 0;
                }
                Object x = doc.getFieldValue(CollectionSchema.inboundlinkscount_i.getSolrFieldName());
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
            double df = (1.0d - damping) / this.cr_host_count;
            try {
                for (Map.Entry<String, double[]> entry: this.crt.entrySet()) {
                    String id = entry.getKey();
                    ReferenceReport rr = this.rrCache.getReferenceReport(id, false);
                    // sum up the cr of the internal links
                    HandleSet iids = rr.getInternallIDs();
                    double ncr = 0.0d;
                    for (byte[] iid: iids) {
                        int ilc = getInternalLinks(iid);
                        if (ilc > 0) { // if (ilc == 0) then the reference report is wrong!
                            double[] d = this.crt.get(ASCII.String(iid));
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
                    ncr = df + damping * ncr;
                    if (convergence && !eqd(ncr, entry.getValue()[0])) convergence = false;
                    entry.getValue()[1] = ncr;
                }
                // after the loop, replace the old value with the new value in crt
                for (Map.Entry<String, double[]> entry: this.crt.entrySet()) {
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
    private static List<String> protocolList2indexedList(final List<String> protocol) {
        List<String> a = new ArrayList<String>();
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
     * encode a string containing attributes from anchor rel properties binary:
     * bit 0: "me" contained in rel
     * bit 1: "nofollow" contained in rel
     * @param rel
     * @return binary encoded information about rel
     */
    /*
    private static List<Integer> relEval(final List<String> rel) {
        List<Integer> il = new ArrayList<Integer>(rel.size());
        for (final String s: rel) {
            int i = 0;
            final String s0 = s.toLowerCase().trim();
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
            } catch (MalformedURLException e) {
                this.digestURL = null;
            }
            this.collections = new HashMap<String, Pattern>();
            Collection<Object> c = doc.getFieldValues(CollectionSchema.collection_sxt.getSolrFieldName());
            if (c != null) for (Object cn: c) if (cn != null) this.collections.put((String) cn, QueryParams.catchall_pattern);
            this.failReason = (String) doc.getFieldValue(CollectionSchema.failreason_s.getSolrFieldName());
            String fts = (String) doc.getFieldValue(CollectionSchema.failtype_s.getSolrFieldName());
            if (fts == null) ConcurrentLog.warn("CollectionConfiguration", "no fail type given for URL " + this.digestURL.toNormalform(true));
            this.failType = fts == null ? FailType.fail : FailType.valueOf(fts);
            this.httpstatus = (Integer) doc.getFieldValue(CollectionSchema.httpstatus_i.getSolrFieldName());
            this.failtime = (Date) doc.getFieldValue(CollectionSchema.load_date_dt.getSolrFieldName());
            Integer cd = (Integer) doc.getFieldValue(CollectionSchema.crawldepth_i.getSolrFieldName());
            this.crawldepth = cd == null ? 0 : cd.intValue();
        }
        public DigestURL getDigestURL() {
            return digestURL;
        }
        public Map<String, Pattern> getCollections() {
            return collections;
        }
        public String getFailReason() {
            return failReason;
        }
        public FailType getFailType() {
            return failType;
        }
        public int getHttpstatus() {
            return httpstatus;
        }
        public SolrInputDocument toSolr(CollectionConfiguration configuration) {
            boolean allAttr = configuration.isEmpty();
            assert allAttr || configuration.contains(CollectionSchema.failreason_s);
            
            final SolrInputDocument doc = new SolrInputDocument();
            String url = configuration.addURIAttributes(doc, allAttr, this.getDigestURL(), Response.docType(this.getDigestURL()));
            if (allAttr || configuration.contains(CollectionSchema.load_date_dt)) configuration.add(doc, CollectionSchema.load_date_dt, new Date());
            if (allAttr || configuration.contains(CollectionSchema.crawldepth_i)) configuration.add(doc, CollectionSchema.crawldepth_i, this.crawldepth);
            
            // fail reason and status
            if (allAttr || configuration.contains(CollectionSchema.failreason_s)) configuration.add(doc, CollectionSchema.failreason_s, this.getFailReason());
            if (allAttr || configuration.contains(CollectionSchema.failtype_s)) configuration.add(doc, CollectionSchema.failtype_s, this.getFailType().name());
            if (allAttr || configuration.contains(CollectionSchema.httpstatus_i)) configuration.add(doc, CollectionSchema.httpstatus_i, this.getHttpstatus());
            if (allAttr || configuration.contains(CollectionSchema.collection_sxt) && this.getCollections() != null && this.getCollections().size() > 0) {
                List<String> cs = new ArrayList<String>();
                for (Map.Entry<String, Pattern> e: this.getCollections().entrySet()) {
                    if (e.getValue().matcher(url).matches()) cs.add(e.getKey());
                }
                configuration.add(doc, CollectionSchema.collection_sxt, cs);
            }

            // cr and postprocessing
            Set<ProcessType> processTypes = new LinkedHashSet<ProcessType>();
            if (allAttr || (configuration.contains(CollectionSchema.cr_host_chance_d) && configuration.contains(CollectionSchema.cr_host_count_i) && configuration.contains(CollectionSchema.cr_host_norm_i))) {
                processTypes.add(ProcessType.CITATION); // postprocessing needed
            }
            if (allAttr || configuration.contains(CollectionSchema.process_sxt)) {
                List<String> p = new ArrayList<String>();
                for (ProcessType t: processTypes) p.add(t.name());
                configuration.add(doc, CollectionSchema.process_sxt, p);
            }
            return doc;
        }
        
    }
    
}
