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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.document.analysis.EnhancedTextProfileSignature;
import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.federate.solr.SchemaConfiguration;
import net.yacy.cora.federate.solr.FailType;
import net.yacy.cora.federate.solr.ProcessType;
import net.yacy.cora.federate.solr.SchemaDeclaration;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.CommonPattern;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.citation.CitationReference;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.rwi.IndexCell;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.search.index.Segment;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;


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
    public CollectionConfiguration(final File configurationFile, boolean lazy) throws IOException {
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
            } catch (IllegalArgumentException e) {
                Log.logFine("SolrCollectionWriter", "solr schema file " + configurationFile.getAbsolutePath() + " defines unknown attribute '" + etr.toString() + "'");
                it.remove();
            }
        }
        // check consistency the other way: look if all enum constants in SolrField appear in the configuration file
        for (CollectionSchema field: CollectionSchema.values()) {
        	if (this.get(field.name()) == null) {
        	    if (CollectionSchema.author_sxt.getSolrFieldName().endsWith(field.name())) continue; // exception for this: that is a copy-field
        	    if (CollectionSchema.coordinate_p_0_coordinate.getSolrFieldName().endsWith(field.name())) continue; // exception for this: automatically generated
        	    if (CollectionSchema.coordinate_p_1_coordinate.getSolrFieldName().endsWith(field.name())) continue; // exception for this: automatically generated
                Log.logWarning("SolrCollectionWriter", " solr schema file " + configurationFile.getAbsolutePath() + " is missing declaration for '" + field.name() + "'");
        	}
        }
    }
    
    public Ranking getRanking(int idx) {
        return this.rankings.get(idx);
    }
    
    public Ranking getRanking(String name) {
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
    public void commit() throws IOException {
        try {
            super.commit();
            // make sure the enum SolrField.SolrFieldName is current
            Iterator<Entry> it = this.entryIterator();
            for (SchemaConfiguration.Entry etr = it.next(); it.hasNext(); etr = it.next()) {
                try {
                    SchemaDeclaration f = CollectionSchema.valueOf(etr.key());
                    f.setSolrFieldName(etr.getValue());
                } catch (IllegalArgumentException e) {
                    continue;
                }
            }
        } catch (final IOException e) {}
    }

    /**
     * Convert a SolrDocument to a SolrInputDocument.
     * This is useful if a document from the search index shall be modified and indexed again.
     * This shall be used as replacement of ClientUtils.toSolrInputDocument because we remove some fields
     * which are created automatically during the indexing process.
     * @param doc the solr document
     * @return a solr input document
     */
    public SolrInputDocument toSolrInputDocument(SolrDocument doc) {
        SolrInputDocument sid = new SolrInputDocument();
        Set<String> omitFields = new HashSet<String>(3);
        omitFields.add(CollectionSchema.author_sxt.getSolrFieldName());
        omitFields.add(CollectionSchema.coordinate_p_0_coordinate.getSolrFieldName());
        omitFields.add(CollectionSchema.coordinate_p_1_coordinate.getSolrFieldName());
        for (String name: doc.getFieldNames()) {
            if (this.contains(name) && !omitFields.contains(name)) { // check each field if enabled in local Solr schema
                sid.addField(name, doc.getFieldValue(name), 1.0f);
            }
        }
        return sid;
    }
    
    public SolrDocument toSolrDocument(SolrInputDocument doc) {
        SolrDocument sid = new SolrDocument();
        Set<String> omitFields = new HashSet<String>(3);
        omitFields.add(CollectionSchema.author_sxt.getSolrFieldName());
        omitFields.add(CollectionSchema.coordinate_p_0_coordinate.getSolrFieldName());
        omitFields.add(CollectionSchema.coordinate_p_1_coordinate.getSolrFieldName());
        for (SolrInputField field: doc) {
            if (this.contains(field.getName()) && !omitFields.contains(field.getName())) { // check each field if enabled in local Solr schema
                sid.setField(field.getName(), field.getValue());
            }
        }
        return sid;
    }
    
    public SolrInputDocument metadata2solr(final URIMetadataRow md) {

        final SolrInputDocument doc = new SolrInputDocument();
        final DigestURI digestURI = md.url();
        boolean allAttr = this.isEmpty();

        if (allAttr || contains(CollectionSchema.failreason_t)) add(doc, CollectionSchema.failreason_t, "");
        add(doc, CollectionSchema.id, ASCII.String(md.hash()));
        String us = digestURI.toNormalform(true);
        add(doc, CollectionSchema.sku, us);
        if (allAttr || contains(CollectionSchema.ip_s)) {
        	final InetAddress address = digestURI.getInetAddress();
        	if (address != null) add(doc, CollectionSchema.ip_s, address.getHostAddress());
        }
        if (allAttr || contains(CollectionSchema.url_protocol_s)) add(doc, CollectionSchema.url_protocol_s, digestURI.getProtocol());
        Map<String, String> searchpart = digestURI.getSearchpartMap();
        if (searchpart == null) {
            if (allAttr || contains(CollectionSchema.url_parameter_i)) add(doc, CollectionSchema.url_parameter_i, 0);
        } else {
            if (allAttr || contains(CollectionSchema.url_parameter_i)) add(doc, CollectionSchema.url_parameter_i, searchpart.size());
            if (allAttr || contains(CollectionSchema.url_parameter_key_sxt)) add(doc, CollectionSchema.url_parameter_key_sxt, searchpart.keySet().toArray(new String[searchpart.size()]));
            if (allAttr || contains(CollectionSchema.url_parameter_value_sxt)) add(doc, CollectionSchema.url_parameter_value_sxt,  searchpart.values().toArray(new String[searchpart.size()]));
        }
        if (allAttr || contains(CollectionSchema.url_chars_i)) add(doc, CollectionSchema.url_chars_i, us.length());
        String host = null;
        if ((host = digestURI.getHost()) != null) {
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

        String description = md.snippet(); if (description == null) description = "";
        if (allAttr || contains(CollectionSchema.description)) add(doc, CollectionSchema.description, description);
        if (allAttr || contains(CollectionSchema.description_count_i)) add(doc, CollectionSchema.description_count_i, 1);
        if (allAttr || contains(CollectionSchema.description_chars_val)) {
            Integer[] cv = new Integer[]{new Integer(description.length())};
            add(doc, CollectionSchema.description_chars_val, cv);
        }
        if (allAttr || contains(CollectionSchema.description_words_val)) {
            Integer[] cv = new Integer[]{new Integer(CommonPattern.SPACE.split(description).length)};
            add(doc, CollectionSchema.description_words_val, cv);
        }

        if (allAttr || contains(CollectionSchema.author)) add(doc, CollectionSchema.author, md.dc_creator());
        if (allAttr || contains(CollectionSchema.content_type)) add(doc, CollectionSchema.content_type, Response.doctype2mime(digestURI.getFileExtension(), md.doctype()));
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

        // path elements of link
        if (allAttr || contains(CollectionSchema.url_paths_sxt)) add(doc, CollectionSchema.url_paths_sxt, digestURI.getPaths());
        if (allAttr || contains(CollectionSchema.url_file_ext_s)) add(doc, CollectionSchema.url_file_ext_s, digestURI.getFileExtension());

        if (allAttr || contains(CollectionSchema.imagescount_i)) add(doc, CollectionSchema.imagescount_i, md.limage());
        if (allAttr || contains(CollectionSchema.inboundlinkscount_i)) add(doc, CollectionSchema.inboundlinkscount_i, md.llocal());
        if (allAttr || contains(CollectionSchema.outboundlinkscount_i)) add(doc, CollectionSchema.outboundlinkscount_i, md.lother());
        if (allAttr || contains(CollectionSchema.charset_s)) add(doc, CollectionSchema.charset_s, "UTF8");

        // coordinates
        if (md.lat() != 0.0 && md.lon() != 0.0) {
            if (allAttr || contains(CollectionSchema.coordinate_p)) add(doc, CollectionSchema.coordinate_p, Double.toString(md.lat()) + "," + Double.toString(md.lon()));
        }
        if (allAttr || contains(CollectionSchema.httpstatus_i)) add(doc, CollectionSchema.httpstatus_i, 200);

        // fields that are in URIMetadataRow additional to yacy2solr basic requirement
        if (allAttr || contains(CollectionSchema.load_date_dt)) add(doc, CollectionSchema.load_date_dt, md.loaddate());
        if (allAttr || contains(CollectionSchema.fresh_date_dt)) add(doc, CollectionSchema.fresh_date_dt, md.freshdate());
        if (allAttr || contains(CollectionSchema.host_id_s)) add(doc, CollectionSchema.host_id_s, md.hosthash());
        if ((allAttr || contains(CollectionSchema.referrer_id_txt)) && md.referrerHash() != null) add(doc, CollectionSchema.referrer_id_txt, new String[]{ASCII.String(md.referrerHash())});
        if (allAttr || contains(CollectionSchema.md5_s)) add(doc, CollectionSchema.md5_s, md.md5());
        if (allAttr || contains(CollectionSchema.publisher_t)) add(doc, CollectionSchema.publisher_t, md.dc_publisher());
        if ((allAttr || contains(CollectionSchema.language_s)) && md.language() != null) add(doc, CollectionSchema.language_s, UTF8.String(md.language()));
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
        	accText(sb, digestURI.toTokens());
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
            final String id, final CrawlProfile profile, final ResponseHeader responseHeader,
            final Document document, Condenser condenser, DigestURI referrerURL, String language,
            IndexCell<CitationReference> citations,
            WebgraphConfiguration webgraph) {
        // we use the SolrCell design as index schema
        SolrVector doc = new SolrVector();
        final DigestURI digestURI = document.dc_source();
        boolean allAttr = this.isEmpty();
        
        Set<ProcessType> processTypes = new LinkedHashSet<ProcessType>();
        
        add(doc, CollectionSchema.id, id);
        if (allAttr || contains(CollectionSchema.failreason_t)) add(doc, CollectionSchema.failreason_t, ""); // overwrite a possible fail reason (in case that there was a fail reason before)
        String docurl = digestURI.toNormalform(true);
        add(doc, CollectionSchema.sku, docurl);

        int clickdepth = 999;
        if ((allAttr || contains(CollectionSchema.clickdepth_i)) && citations != null) {
            if (digestURI.probablyRootURL()) {
                boolean lc = this.lazy; this.lazy = false;
                clickdepth = 0;
                this.lazy = lc;
            } else {
                clickdepth = 999;
            }
            processTypes.add(ProcessType.CLICKDEPTH); // postprocessing needed; this is also needed if the depth is positive; there could be a shortcut
            CollectionSchema.clickdepth_i.add(doc, clickdepth); // no lazy value checking to get a '0' into the index
        }
        
        if (allAttr || contains(CollectionSchema.ip_s)) {
            final InetAddress address = digestURI.getInetAddress();
            if (address != null) add(doc, CollectionSchema.ip_s, address.getHostAddress());
        }
        if (allAttr || contains(CollectionSchema.collection_sxt) && profile != null) add(doc, CollectionSchema.collection_sxt, profile.collections());
        if (allAttr || contains(CollectionSchema.url_protocol_s)) add(doc, CollectionSchema.url_protocol_s, digestURI.getProtocol());
        Map<String, String> searchpart = digestURI.getSearchpartMap();
        if (searchpart == null) {
            if (allAttr || contains(CollectionSchema.url_parameter_i)) add(doc, CollectionSchema.url_parameter_i, 0);
        } else {
            if (allAttr || contains(CollectionSchema.url_parameter_i)) add(doc, CollectionSchema.url_parameter_i, searchpart.size());
            if (allAttr || contains(CollectionSchema.url_parameter_key_sxt)) add(doc, CollectionSchema.url_parameter_key_sxt, searchpart.keySet().toArray(new String[searchpart.size()]));
            if (allAttr || contains(CollectionSchema.url_parameter_value_sxt)) add(doc, CollectionSchema.url_parameter_value_sxt,  searchpart.values().toArray(new String[searchpart.size()]));
        }
        if (allAttr || contains(CollectionSchema.url_chars_i)) add(doc, CollectionSchema.url_chars_i, docurl.length());
        String host = null;
        if ((host = digestURI.getHost()) != null) {
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

        String description = document.dc_description();
        List<String> descriptions = new ArrayList<String>();
        for (String s: CommonPattern.NEWLINE.split(description)) descriptions.add(s);
        if (allAttr || contains(CollectionSchema.description)) {
            add(doc, CollectionSchema.description, description);
            if ((allAttr || contains(CollectionSchema.description_exact_signature_l)) && description != null && description.length() > 0) {
                add(doc, CollectionSchema.description_exact_signature_l, EnhancedTextProfileSignature.getSignatureLong(description));
            }
        }
        if (allAttr || contains(CollectionSchema.description_count_i)) add(doc, CollectionSchema.description_count_i, descriptions.size());
        if (allAttr || contains(CollectionSchema.description_chars_val)) {
            ArrayList<Integer> cv = new ArrayList<Integer>(descriptions.size());
            for (String s: descriptions) cv.add(new Integer(s.length()));
            add(doc, CollectionSchema.description_chars_val, cv);
        }
        if (allAttr || contains(CollectionSchema.description_words_val)) {
            ArrayList<Integer> cv = new ArrayList<Integer>(descriptions.size());
            for (String s: descriptions) cv.add(new Integer(CommonPattern.SPACE.split(s).length));
            add(doc, CollectionSchema.description_words_val, cv);
        }

        if (allAttr || contains(CollectionSchema.author)) {
            String author = document.dc_creator();
            if (author == null || author.length() == 0) author = document.dc_publisher();
            add(doc, CollectionSchema.author, author);
        }
        if (allAttr || contains(CollectionSchema.content_type)) add(doc, CollectionSchema.content_type, new String[]{document.dc_format()});
        if (allAttr || contains(CollectionSchema.last_modified)) add(doc, CollectionSchema.last_modified, responseHeader == null ? new Date() : responseHeader.lastModified());
        if (allAttr || contains(CollectionSchema.keywords)) add(doc, CollectionSchema.keywords, document.dc_subject(' '));
        String content = document.getTextString();
        if (content == null || content.length() == 0) {
            content = digestURI.toTokens();
        }
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
        if (allAttr || contains(CollectionSchema.synonyms_sxt)) {
            List<String> synonyms = condenser.synonyms();
            add(doc, CollectionSchema.synonyms_sxt, synonyms);
        }
        add(doc, CollectionSchema.exact_signature_l, condenser.exactSignature());
        add(doc, CollectionSchema.exact_signature_unique_b, true); // this must be corrected afterwards!
        add(doc, CollectionSchema.fuzzy_signature_l, condenser.fuzzySignature());
        add(doc, CollectionSchema.fuzzy_signature_text_t, condenser.fuzzySignatureText());
        add(doc, CollectionSchema.fuzzy_signature_unique_b, true); // this must be corrected afterwards!

        // path elements of link
        if (allAttr || contains(CollectionSchema.url_paths_sxt)) add(doc, CollectionSchema.url_paths_sxt, digestURI.getPaths());
        if (allAttr || contains(CollectionSchema.url_file_ext_s)) add(doc, CollectionSchema.url_file_ext_s, digestURI.getFileExtension());

        // get list of all links; they will be shrinked by urls that appear in other fields of the solr schema
        Set<DigestURI> inboundLinks = document.inboundLinks();
        Set<DigestURI> outboundLinks = document.outboundLinks();

        int c = 0;
        final Object parser = document.getParserObject();
        Map<DigestURI, ImageEntry> images = new HashMap<DigestURI, ImageEntry>();
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
            // and HTTP header (x-robots property)
            // coded as binary value:
            // bit  0: "all" contained in html header meta
            // bit  1: "index" contained in html header meta
            // bit  2: "noindex" contained in html header meta
            // bit  3: "nofollow" contained in html header meta
            // bit  8: "noarchive" contained in http header properties
            // bit  9: "nosnippet" contained in http header properties
            // bit 10: "noindex" contained in http header properties
            // bit 11: "nofollow" contained in http header properties
            // bit 12: "unavailable_after" contained in http header properties
            int b = 0;
            final String robots_meta = html.getMetas().get("robots");
            // this tag may have values: all, index, noindex, nofollow
            if (robots_meta != null) {
                if (robots_meta.indexOf("all",0) >= 0) b += 1;      // set bit 0
                if (robots_meta.indexOf("index",0) == 0 || robots_meta.indexOf(" index",0) >= 0 || robots_meta.indexOf(",index",0) >= 0 ) b += 2; // set bit 1
                if (robots_meta.indexOf("noindex",0) >= 0) b += 4;  // set bit 2
                if (robots_meta.indexOf("nofollow",0) >= 0) b += 8; // set bit 3
            }
            String x_robots_tag = "";
            if (responseHeader != null) {
                x_robots_tag = responseHeader.get(HeaderFramework.X_ROBOTS_TAG, "");
                if (x_robots_tag.isEmpty()) {
                    x_robots_tag = responseHeader.get(HeaderFramework.X_ROBOTS, "");
                }
            }
            if (!x_robots_tag.isEmpty()) {
                // this tag may have values: noarchive, nosnippet, noindex, unavailable_after
                if (x_robots_tag.indexOf("noarchive",0) >= 0) b += 256;         // set bit 8
                if (x_robots_tag.indexOf("nosnippet",0) >= 0) b += 512;         // set bit 9
                if (x_robots_tag.indexOf("noindex",0) >= 0) b += 1024;          // set bit 10
                if (x_robots_tag.indexOf("nofollow",0) >= 0) b += 2048;         // set bit 11
                if (x_robots_tag.indexOf("unavailable_after",0) >=0) b += 4096; // set bit 12
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
            final Collection<ImageEntry> imagesc = images.values();
            final List<String> imgtags  = new ArrayList<String>(imagesc.size());
            final List<String> imgprots = new ArrayList<String>(imagesc.size());
            final List<String> imgstubs = new ArrayList<String>(imagesc.size());
            final List<String> imgalts  = new ArrayList<String>(imagesc.size());
            int withalt = 0;
            for (final ImageEntry ie: imagesc) {
                final MultiProtocolURI uri = ie.url();
                inboundLinks.remove(uri);
                outboundLinks.remove(uri);
                imgtags.add(ie.toString());
                String protocol = uri.getProtocol();
                imgprots.add(protocol);
                imgstubs.add(uri.toString().substring(protocol.length() + 3));
                imgalts.add(ie.alt());
                if (ie.alt() != null && ie.alt().length() > 0) withalt++;
            }
            if (allAttr || contains(CollectionSchema.imagescount_i)) add(doc, CollectionSchema.imagescount_i, imgtags.size());
            if (allAttr || contains(CollectionSchema.images_tag_txt)) add(doc, CollectionSchema.images_tag_txt, imgtags);
            if (allAttr || contains(CollectionSchema.images_protocol_sxt)) add(doc, CollectionSchema.images_protocol_sxt, protocolList2indexedList(imgprots));
            if (allAttr || contains(CollectionSchema.images_urlstub_txt)) add(doc, CollectionSchema.images_urlstub_txt, imgstubs);
            if (allAttr || contains(CollectionSchema.images_alt_txt)) add(doc, CollectionSchema.images_alt_txt, imgalts);
            if (allAttr || contains(CollectionSchema.images_withalt_i)) add(doc, CollectionSchema.images_withalt_i, withalt);

            // style sheets
            if (allAttr || contains(CollectionSchema.css_tag_txt)) {
                final Map<DigestURI, String> csss = html.getCSS();
                final String[] css_tag = new String[csss.size()];
                final String[] css_url = new String[csss.size()];
                c = 0;
                for (final Map.Entry<DigestURI, String> entry: csss.entrySet()) {
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
                if (css_tag.length > 0) add(doc, CollectionSchema.css_tag_txt, css_tag);
                if (css_url.length > 0) add(doc, CollectionSchema.css_url_txt, css_url);
            }

            // Scripts
            if (allAttr || contains(CollectionSchema.scripts_txt)) {
                final Set<DigestURI> scriptss = html.getScript();
                final String[] scripts = new String[scriptss.size()];
                c = 0;
                for (final DigestURI u: scriptss) {
                    inboundLinks.remove(u);
                    outboundLinks.remove(u);
                    scripts[c++] = u.toNormalform(false);
                }
                add(doc, CollectionSchema.scriptscount_i, scripts.length);
                if (scripts.length > 0) add(doc, CollectionSchema.scripts_txt, scripts);
            }

            // Frames
            if (allAttr || contains(CollectionSchema.frames_sxt)) {
                final Set<DigestURI> framess = html.getFrames();
                final String[] frames = new String[framess.size()];
                c = 0;
                for (final DigestURI u: framess) {
                    inboundLinks.remove(u);
                    outboundLinks.remove(u);
                    frames[c++] = u.toNormalform(false);
                }
                add(doc, CollectionSchema.framesscount_i, frames.length);
                if (frames.length > 0) add(doc, CollectionSchema.frames_sxt, frames);
            }

            // IFrames
            if (allAttr || contains(CollectionSchema.iframes_sxt)) {
                final Set<DigestURI> iframess = html.getIFrames();
                final String[] iframes = new String[iframess.size()];
                c = 0;
                for (final DigestURI u: iframess) {
                    inboundLinks.remove(u);
                    outboundLinks.remove(u);
                    iframes[c++] = u.toNormalform(false);
                }
                add(doc, CollectionSchema.iframesscount_i, iframes.length);
                if (iframes.length > 0) add(doc, CollectionSchema.iframes_sxt, iframes);
            }

            // canonical tag
            if (allAttr || contains(CollectionSchema.canonical_s)) {
                final DigestURI canonical = html.getCanonical();
                if (canonical != null) {
                    inboundLinks.remove(canonical);
                    outboundLinks.remove(canonical);
                    add(doc, CollectionSchema.canonical_s, canonical.toNormalform(false));
                    // set a flag if this is equal to sku
                    if (contains(CollectionSchema.canonical_equal_sku_b) && canonical.equals(docurl)) {
                        add(doc, CollectionSchema.canonical_equal_sku_b, true);
                    }
                }
            }

            // meta refresh tag
            if (allAttr || contains(CollectionSchema.refresh_s)) {
                String refresh = html.getRefreshPath();
                if (refresh != null && refresh.length() > 0) {
                    MultiProtocolURI refreshURL;
                    try {
                        refreshURL = refresh.startsWith("http") ? new MultiProtocolURI(html.getRefreshPath()) : new MultiProtocolURI(digestURI, html.getRefreshPath());
                        if (refreshURL != null) {
                            inboundLinks.remove(refreshURL);
                            outboundLinks.remove(refreshURL);
                            add(doc, CollectionSchema.refresh_s, refreshURL.toNormalform(false));
                        }
                    } catch (MalformedURLException e) {
                        add(doc, CollectionSchema.refresh_s, refresh);
                    }
                }
            }

            // flash embedded
            if (allAttr || contains(CollectionSchema.flash_b)) {
                MultiProtocolURI[] flashURLs = html.getFlash();
                for (MultiProtocolURI u: flashURLs) {
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
        }

        // statistics about the links
        if (allAttr || contains(CollectionSchema.inboundlinkscount_i)) add(doc, CollectionSchema.inboundlinkscount_i, inboundLinks.size());
        if (allAttr || contains(CollectionSchema.inboundlinksnofollowcount_i)) add(doc, CollectionSchema.inboundlinksnofollowcount_i, document.inboundLinkNofollowCount());
        if (allAttr || contains(CollectionSchema.outboundlinkscount_i)) add(doc, CollectionSchema.outboundlinkscount_i, outboundLinks.size());
        if (allAttr || contains(CollectionSchema.outboundlinksnofollowcount_i)) add(doc, CollectionSchema.outboundlinksnofollowcount_i, document.outboundLinkNofollowCount());
        
        // list all links
        WebgraphConfiguration.Subgraph subgraph = webgraph.edges(digestURI, responseHeader, profile.collections(), clickdepth, document.getAnchors(), images, inboundLinks, outboundLinks, citations);
        doc.webgraphDocuments.addAll(subgraph.edges);
        if (allAttr || contains(CollectionSchema.inboundlinks_protocol_sxt)) add(doc, CollectionSchema.inboundlinks_protocol_sxt, protocolList2indexedList(subgraph.urlProtocols[0]));
        if (allAttr || contains(CollectionSchema.inboundlinks_urlstub_txt)) add(doc, CollectionSchema.inboundlinks_urlstub_txt, subgraph.urlStubs[0]);
        if (allAttr || contains(CollectionSchema.outboundlinks_protocol_sxt)) add(doc, CollectionSchema.outboundlinks_protocol_sxt, protocolList2indexedList(subgraph.urlProtocols[1]));
        if (allAttr || contains(CollectionSchema.outboundlinks_urlstub_txt)) add(doc, CollectionSchema.outboundlinks_urlstub_txt, subgraph.urlStubs[1]);
        
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
        if (allAttr || contains(CollectionSchema.host_id_s)) add(doc, CollectionSchema.host_id_s, document.dc_source().hosthash());
        if ((allAttr || contains(CollectionSchema.referrer_id_txt)) && referrerURL != null) add(doc, CollectionSchema.referrer_id_txt, new String[]{ASCII.String(referrerURL.hash())});
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
        }
        return doc;
    }

    
    /**
     * post-processing steps for all entries that have a process tag assigned
     * @param connector
     * @param urlCitation
     * @return
     */
    public void postprocessing(Segment segment) {
        if (!this.contains(CollectionSchema.process_sxt)) return;
        if (!segment.connectedCitation()) return;
        SolrConnector connector = segment.fulltext().getDefaultConnector();
        // that means we must search for those entries.
        connector.commit(true); // make sure that we have latest information that can be found
        //BlockingQueue<SolrDocument> docs = index.fulltext().getSolr().concurrentQuery("*:*", 0, 1000, 60000, 10);
        BlockingQueue<SolrDocument> docs = connector.concurrentQuery(CollectionSchema.process_sxt.getSolrFieldName() + ":[* TO *]", 0, 10000, 60000, 50);
        
        SolrDocument doc;
        int proccount = 0, proccount_clickdepthchange = 0, proccount_referencechange = 0;

        Map<String, Long> hostExtentCache = new HashMap<String, Long>();
        try {
            while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                // for each to-be-processed entry work on the process tag
                Collection<Object> proctags = doc.getFieldValues(CollectionSchema.process_sxt.getSolrFieldName());
                for (Object tag: proctags) {
                    
                    try {
                        DigestURI url = new DigestURI((String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName()), ASCII.getBytes((String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName())));
                        SolrInputDocument sid = this.toSolrInputDocument(doc);
                        
                        // switch over tag types
                        ProcessType tagtype = ProcessType.valueOf((String) tag);
                        if (tagtype == ProcessType.CLICKDEPTH) {
                            if (postprocessing_clickdepth(segment, doc, sid, url, CollectionSchema.clickdepth_i)) proccount_clickdepthchange++;
                        }
                        
                        // refresh the link count; it's 'cheap' to do this here
                        String hosthash = url.hosthash();
                        if (!hostExtentCache.containsKey(hosthash)) {
                            StringBuilder q = new StringBuilder();
                            q.append(CollectionSchema.host_id_s.getSolrFieldName()).append(":\"").append(hosthash).append("\" AND ").append(CollectionSchema.httpstatus_i.getSolrFieldName()).append(":200");
                            long count = segment.fulltext().getDefaultConnector().getQueryCount(q.toString());
                            hostExtentCache.put(hosthash, count);
                        }
                        if (postprocessing_references(segment, doc, sid, url, hostExtentCache)) proccount_referencechange++;
                        
                        // all processing steps checked, remove the processing tag
                        sid.removeField(CollectionSchema.process_sxt.getSolrFieldName());
                        
                        // send back to index
                        connector.add(sid);
                        proccount++;
                    } catch (Throwable e1) {
                    }
                    
                }
            }
            Log.logInfo("CollectionConfiguration", "cleanup_processing: re-calculated " + proccount+ " new documents, " + proccount_clickdepthchange + " clickdepth values changed, " + proccount_referencechange + " reference-count values changed.");
        } catch (InterruptedException e) {
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
    private static List<String> protocolList2indexedList(List<String> protocol) {
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
    
    /**
     * register an entry as error document
     * @param digestURI
     * @param failReason
     * @param httpstatus
     * @throws IOException
     */
    public SolrInputDocument err(final DigestURI digestURI, final String failReason, final FailType failType, final int httpstatus) throws IOException {
        final SolrInputDocument solrdoc = new SolrInputDocument();
        add(solrdoc, CollectionSchema.id, ASCII.String(digestURI.hash()));
        add(solrdoc, CollectionSchema.sku, digestURI.toNormalform(true));
        final InetAddress address = digestURI.getInetAddress();
        if (contains(CollectionSchema.ip_s) && address != null) add(solrdoc, CollectionSchema.ip_s, address.getHostAddress());
        if (contains(CollectionSchema.host_s) && digestURI.getHost() != null) add(solrdoc, CollectionSchema.host_s, digestURI.getHost());

        // path elements of link
        if (contains(CollectionSchema.url_paths_sxt)) add(solrdoc, CollectionSchema.url_paths_sxt, digestURI.getPaths());
        if (contains(CollectionSchema.url_file_ext_s)) add(solrdoc, CollectionSchema.url_file_ext_s, digestURI.getFileExtension());
        
        // fail reason and status
        if (contains(CollectionSchema.failreason_t)) add(solrdoc, CollectionSchema.failreason_t, failReason);
        if (contains(CollectionSchema.failtype_s)) add(solrdoc, CollectionSchema.failtype_s, failType.name());
        if (contains(CollectionSchema.httpstatus_i)) add(solrdoc, CollectionSchema.httpstatus_i, httpstatus);
        return solrdoc;
    }


    /*
   standard solr schema

   <field name="name" type="textgen" indexed="true" stored="true"/>
   <field name="cat" type="string" indexed="true" stored="true" multiValued="true"/>
   <field name="features" type="text" indexed="true" stored="true" multiValued="true"/>
   <field name="includes" type="text" indexed="true" stored="true" termVectors="true" termPositions="true" termOffsets="true" />

   <field name="weight" type="float" indexed="true" stored="true"/>
   <field name="price"  type="float" indexed="true" stored="true"/>
   <field name="popularity" type="int" indexed="true" stored="true" />

   <!-- Common metadata fields, named specifically to match up with
     SolrCell metadata when parsing rich documents such as Word, PDF.
     Some fields are multiValued only because Tika currently may return
     multiple values for them.
   -->
   <field name="title" type="text" indexed="true" stored="true" multiValued="true"/>
   <field name="subject" type="text" indexed="true" stored="true"/>
   <field name="description" type="text" indexed="true" stored="true"/>
   <field name="comments" type="text" indexed="true" stored="true"/>
   <field name="author" type="textgen" indexed="true" stored="true"/>
   <field name="keywords" type="textgen" indexed="true" stored="true"/>
   <field name="category" type="textgen" indexed="true" stored="true"/>
   <field name="content_type" type="string" indexed="true" stored="true" multiValued="true"/>
   <field name="last_modified" type="date" indexed="true" stored="true"/>
   <field name="links" type="string" indexed="true" stored="true" multiValued="true"/>
     */
}
