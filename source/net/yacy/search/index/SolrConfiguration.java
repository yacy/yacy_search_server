/**
 *  SolrScheme
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

package net.yacy.search.index;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.federate.yacy.ConfigurationSet;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadata;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.Bitfield;

import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;


public class SolrConfiguration extends ConfigurationSet implements Serializable {

    private static final long serialVersionUID=-499100932212840385L;

    private boolean lazy;

    /**
     * initialize with an empty ConfigurationSet which will cause that all the index
     * attributes are used
     */
    public SolrConfiguration() {
        super();
        this.lazy = false;
    }

    /**
     * initialize the scheme with a given configuration file
     * the configuration file simply contains a list of lines with keywords
     * or keyword = value lines (while value is a custom Solr field name
     * @param configurationFile
     */
    public SolrConfiguration(final File configurationFile, boolean lazy) {
        super(configurationFile);
        // check consistency: compare with YaCyField enum
        if (this.isEmpty()) return;
        Iterator<Entry> it = this.entryIterator();
        for (ConfigurationSet.Entry etr = it.next(); it.hasNext(); etr = it.next()) {
            try {
                YaCySchema f = YaCySchema.valueOf(etr.key());
                f.setSolrFieldName(etr.getValue());
            } catch (IllegalArgumentException e) {
                Log.logFine("SolrScheme", "solr scheme file " + configurationFile.getAbsolutePath() + " defines unknown attribute '" + etr.toString() + "'");
                it.remove();
            }
        }
        // check consistency the other way: look if all enum constants in SolrField appear in the configuration file
        for (YaCySchema field: YaCySchema.values()) {
        	if (this.get(field.name()) == null) {
        		Log.logWarning("SolrScheme", " solr scheme file " + configurationFile.getAbsolutePath() + " is missing declaration for '" + field.name() + "'");
        	}
        }
        this.lazy = lazy;
    }

    public boolean contains(YaCySchema field) {
    	return this.contains(field.name());
    }

    protected void add(final SolrInputDocument doc, final YaCySchema key, final byte[] value) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.length != 0))) key.add(doc, UTF8.String(value));
    }

    protected void add(final SolrInputDocument doc, final YaCySchema key, final String value) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && !value.isEmpty()))) key.add(doc, value);
    }

    protected void add(final SolrInputDocument doc, final YaCySchema key, final String value, final float boost) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && !value.isEmpty()))) key.add(doc, value, boost);
    }

    protected void add(final SolrInputDocument doc, final YaCySchema key, final Date value) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.getTime() > 0))) key.add(doc, value);
    }

    protected void add(final SolrInputDocument doc, final YaCySchema key, final String[] value) {
        assert key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.length > 0))) key.add(doc, value);
    }

    protected void add(final SolrInputDocument doc, final YaCySchema key, final Integer[] value) {
        assert key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.length > 0))) key.add(doc, value);
    }

    protected void add(final SolrInputDocument doc, final YaCySchema key, final List<?> values) {
        assert key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (values != null && !values.isEmpty()))) key.add(doc, values);
    }

    protected void add(final SolrInputDocument doc, final YaCySchema key, final int value) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || value != 0)) key.add(doc, value);
    }

    protected void add(final SolrInputDocument doc, final YaCySchema key, final long value) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || value != 0)) key.add(doc, value);
    }

    protected void add(final SolrInputDocument doc, final YaCySchema key, final float value) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || value != 0.0f)) key.add(doc, value);
    }

    protected void add(final SolrInputDocument doc, final YaCySchema key, final double value) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || value != 0.0d)) key.add(doc, value);
    }

    protected void add(final SolrInputDocument doc, final YaCySchema key, final boolean value) {
        assert !key.isMultiValued();
        if (isEmpty() || contains(key)) key.add(doc, value);
    }

    public Date getDate(SolrInputDocument doc, final YaCySchema key) {
        Date x = (Date) doc.getFieldValue(key.name());
        Date now = new Date();
        return (x == null) ? new Date(0) : x.after(now) ? now : x;
    }

    public Date getDate(SolrDocument doc, final YaCySchema key) {
        Date x = doc == null ? null : (Date) doc.getFieldValue(key.name());
        Date now = new Date();
        return (x == null) ? new Date(0) : x.after(now) ? now : x;
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
            for (ConfigurationSet.Entry etr = it.next(); it.hasNext(); etr = it.next()) {
                try {
                    YaCySchema f = YaCySchema.valueOf(etr.key());
                    f.setSolrFieldName(etr.getValue());
                } catch (IllegalArgumentException e) {
                    continue;
                }
            }
        } catch (final IOException e) {}
    }

    public SolrInputDocument metadata2solr(final URIMetadata md) {
        assert md instanceof URIMetadataRow;
    	if (md instanceof URIMetadataNode) {
    		return ClientUtils.toSolrInputDocument(((URIMetadataNode) md).getDocument());
    	}

        final SolrInputDocument doc = new SolrInputDocument();
        final DigestURI digestURI = new DigestURI(md.url());
        boolean allAttr = this.isEmpty();

        if (allAttr || contains(YaCySchema.failreason_t)) add(doc, YaCySchema.failreason_t, "");
        add(doc, YaCySchema.id, ASCII.String(md.hash()));
        String us = digestURI.toNormalform(true, false);
        add(doc, YaCySchema.sku, us);
        if (allAttr || contains(YaCySchema.ip_s)) {
        	final InetAddress address = digestURI.getInetAddress();
        	if (address != null) add(doc, YaCySchema.ip_s, address.getHostAddress());
        }
        if (allAttr || contains(YaCySchema.url_protocol_s)) add(doc, YaCySchema.url_protocol_s, digestURI.getProtocol());
        Map<String, String> searchpart = digestURI.getSearchpartMap();
        if (searchpart == null) {
            if (allAttr || contains(YaCySchema.url_parameter_i)) add(doc, YaCySchema.url_parameter_i, 0);
        } else {
            if (allAttr || contains(YaCySchema.url_parameter_i)) add(doc, YaCySchema.url_parameter_i, searchpart.size());
            if (allAttr || contains(YaCySchema.url_parameter_key_sxt)) add(doc, YaCySchema.url_parameter_key_sxt, searchpart.keySet().toArray(new String[searchpart.size()]));
            if (allAttr || contains(YaCySchema.url_parameter_value_sxt)) add(doc, YaCySchema.url_parameter_value_sxt,  searchpart.values().toArray(new String[searchpart.size()]));
        }
        if (allAttr || contains(YaCySchema.url_chars_i)) add(doc, YaCySchema.url_chars_i, us.length());
        String host = null;
        if ((host = digestURI.getHost()) != null) {
            String dnc = Domains.getDNC(host);
            String subdomOrga = host.substring(0, host.length() - dnc.length() - 1);
            int p = subdomOrga.lastIndexOf('.');
            String subdom = (p < 0) ? "" : subdomOrga.substring(0, p);
            String orga = (p < 0) ? subdomOrga : subdomOrga.substring(p + 1);
            if (allAttr || contains(YaCySchema.host_s)) add(doc, YaCySchema.host_s, host);
            if (allAttr || contains(YaCySchema.host_dnc_s)) add(doc, YaCySchema.host_dnc_s, dnc);
            if (allAttr || contains(YaCySchema.host_organization_s)) add(doc, YaCySchema.host_organization_s, orga);
            if (allAttr || contains(YaCySchema.host_organizationdnc_s)) add(doc, YaCySchema.host_organizationdnc_s, orga + '.' + dnc);
            if (allAttr || contains(YaCySchema.host_subdomain_s)) add(doc, YaCySchema.host_subdomain_s, subdom);
        }

        String title = md.dc_title();
        if (allAttr || contains(YaCySchema.title)) add(doc, YaCySchema.title, new String[]{title});
        if (allAttr || contains(YaCySchema.title_count_i)) add(doc, YaCySchema.title_count_i, 1);
        if (allAttr || contains(YaCySchema.title_chars_val)) {
            Integer[] cv = new Integer[]{new Integer(title.length())};
            add(doc, YaCySchema.title_chars_val, cv);
        }
        if (allAttr || contains(YaCySchema.title_words_val)) {
            Integer[] cv = new Integer[]{new Integer(title.split(" ").length)};
            add(doc, YaCySchema.title_words_val, cv);
        }

        String description = md.snippet(); if (description == null) description = "";
        if (allAttr || contains(YaCySchema.description)) add(doc, YaCySchema.description, description);
        if (allAttr || contains(YaCySchema.description_count_i)) add(doc, YaCySchema.description_count_i, 1);
        if (allAttr || contains(YaCySchema.description_chars_val)) {
            Integer[] cv = new Integer[]{new Integer(description.length())};
            add(doc, YaCySchema.description_chars_val, cv);
        }
        if (allAttr || contains(YaCySchema.description_words_val)) {
            Integer[] cv = new Integer[]{new Integer(description.split(" ").length)};
            add(doc, YaCySchema.description_words_val, cv);
        }

        if (allAttr || contains(YaCySchema.author)) add(doc, YaCySchema.author, md.dc_creator());
        if (allAttr || contains(YaCySchema.content_type)) add(doc, YaCySchema.content_type, Response.doctype2mime(digestURI.getFileExtension(), md.doctype()));
        if (allAttr || contains(YaCySchema.last_modified)) add(doc, YaCySchema.last_modified, md.moddate());
        if (allAttr || contains(YaCySchema.wordcount_i)) add(doc, YaCySchema.wordcount_i, md.wordCount());

        String keywords = md.dc_subject();
    	Bitfield flags = md.flags();
    	if (flags.get(Condenser.flag_cat_indexof)) {
    		if (keywords == null || keywords.isEmpty()) keywords = "indexof"; else {
    			if (keywords.indexOf(',') > 0) keywords += ", indexof"; else keywords += " indexof";
    		}
    	}
        if (allAttr || contains(YaCySchema.keywords)) {
        	add(doc, YaCySchema.keywords, keywords);
        }

        // path elements of link
        if (allAttr || contains(YaCySchema.url_paths_sxt)) add(doc, YaCySchema.url_paths_sxt, digestURI.getPaths());
        if (allAttr || contains(YaCySchema.url_file_ext_s)) add(doc, YaCySchema.url_file_ext_s, digestURI.getFileExtension());

        if (allAttr || contains(YaCySchema.imagescount_i)) add(doc, YaCySchema.imagescount_i, md.limage());
        if (allAttr || contains(YaCySchema.inboundlinkscount_i)) add(doc, YaCySchema.inboundlinkscount_i, md.llocal());
        if (allAttr || contains(YaCySchema.outboundlinkscount_i)) add(doc, YaCySchema.outboundlinkscount_i, md.lother());
        if (allAttr || contains(YaCySchema.charset_s)) add(doc, YaCySchema.charset_s, "UTF8");

        // coordinates
        if (md.lat() != 0.0f && md.lon() != 0.0f) {
            if (allAttr || contains(YaCySchema.coordinate_p)) add(doc, YaCySchema.coordinate_p, Double.toString(md.lat()) + "," + Double.toString(md.lon()));
        }
        if (allAttr || contains(YaCySchema.httpstatus_i)) add(doc, YaCySchema.httpstatus_i, 200);

        // fields that are in URIMetadataRow additional to yacy2solr basic requirement
        if (allAttr || contains(YaCySchema.load_date_dt)) add(doc, YaCySchema.load_date_dt, md.loaddate());
        if (allAttr || contains(YaCySchema.fresh_date_dt)) add(doc, YaCySchema.fresh_date_dt, md.freshdate());
        if (allAttr || contains(YaCySchema.host_id_s)) add(doc, YaCySchema.host_id_s, md.hosthash());
        if ((allAttr || contains(YaCySchema.referrer_id_txt)) && md.referrerHash() != null) add(doc, YaCySchema.referrer_id_txt, new String[]{ASCII.String(md.referrerHash())});
        if (allAttr || contains(YaCySchema.md5_s)) add(doc, YaCySchema.md5_s, md.md5());
        if (allAttr || contains(YaCySchema.publisher_t)) add(doc, YaCySchema.publisher_t, md.dc_publisher());
        if ((allAttr || contains(YaCySchema.language_s)) && md.language() != null) add(doc, YaCySchema.language_s, UTF8.String(md.language()));
        if (allAttr || contains(YaCySchema.size_i)) add(doc, YaCySchema.size_i, md.size());
        if (allAttr || contains(YaCySchema.audiolinkscount_i)) add(doc, YaCySchema.audiolinkscount_i, md.laudio());
        if (allAttr || contains(YaCySchema.videolinkscount_i)) add(doc, YaCySchema.videolinkscount_i, md.lvideo());
        if (allAttr || contains(YaCySchema.applinkscount_i)) add(doc, YaCySchema.applinkscount_i, md.lapp());
        if (allAttr || contains(YaCySchema.text_t)) {
        	// construct the text from other metadata parts.
        	// This is necessary here since that is used to search the link when no other data (parsed text body) is available
        	StringBuilder sb = new StringBuilder(120);
        	accText(sb, md.dc_title());
        	accText(sb, md.dc_creator());
        	accText(sb, md.dc_publisher());
        	accText(sb, md.snippet());
        	accText(sb, digestURI.toTokens());
        	accText(sb, keywords);
        	add(doc, YaCySchema.text_t, sb.toString());
        }

        return doc;
    }

    private static void accText(final StringBuilder sb, String text) {
    	if (text == null || text.length() == 0) return;
    	if (sb.length() != 0) sb.append(' ');
    	text = text.trim();
    	if (!text.isEmpty() && text.charAt(text.length() - 1) == '.') sb.append(text); else sb.append(text).append('.');
    }

    public SolrInputDocument yacy2solr(final String id, final CrawlProfile profile, final ResponseHeader header, final Document yacydoc, Condenser condenser, final URIMetadata metadata) {
        // we use the SolrCell design as index scheme
        final SolrInputDocument doc = new SolrInputDocument();
        final DigestURI digestURI = new DigestURI(yacydoc.dc_source());
        boolean allAttr = this.isEmpty();
        add(doc, YaCySchema.id, id);
        if (allAttr || contains(YaCySchema.failreason_t)) add(doc, YaCySchema.failreason_t, ""); // overwrite a possible fail reason (in case that there was a fail reason before)
        String us = digestURI.toNormalform(true, false);
        add(doc, YaCySchema.sku, us);
        if (allAttr || contains(YaCySchema.ip_s)) {
            final InetAddress address = digestURI.getInetAddress();
            if (address != null) add(doc, YaCySchema.ip_s, address.getHostAddress());
        }
        if (allAttr || contains(YaCySchema.collection_sxt) && profile != null) add(doc, YaCySchema.collection_sxt, profile.collections());
        if (allAttr || contains(YaCySchema.url_protocol_s)) add(doc, YaCySchema.url_protocol_s, digestURI.getProtocol());
        Map<String, String> searchpart = digestURI.getSearchpartMap();
        if (searchpart == null) {
            if (allAttr || contains(YaCySchema.url_parameter_i)) add(doc, YaCySchema.url_parameter_i, 0);
        } else {
            if (allAttr || contains(YaCySchema.url_parameter_i)) add(doc, YaCySchema.url_parameter_i, searchpart.size());
            if (allAttr || contains(YaCySchema.url_parameter_key_sxt)) add(doc, YaCySchema.url_parameter_key_sxt, searchpart.keySet().toArray(new String[searchpart.size()]));
            if (allAttr || contains(YaCySchema.url_parameter_value_sxt)) add(doc, YaCySchema.url_parameter_value_sxt,  searchpart.values().toArray(new String[searchpart.size()]));
        }
        if (allAttr || contains(YaCySchema.url_chars_i)) add(doc, YaCySchema.url_chars_i, us.length());
        String host = null;
        if ((host = digestURI.getHost()) != null) {
            String dnc = Domains.getDNC(host);
            String subdomOrga = host.substring(0, host.length() - dnc.length() - 1);
            int p = subdomOrga.lastIndexOf('.');
            String subdom = (p < 0) ? "" : subdomOrga.substring(0, p);
            String orga = (p < 0) ? subdomOrga : subdomOrga.substring(p + 1);
            if (allAttr || contains(YaCySchema.host_s)) add(doc, YaCySchema.host_s, host);
            if (allAttr || contains(YaCySchema.host_dnc_s)) add(doc, YaCySchema.host_dnc_s, dnc);
            if (allAttr || contains(YaCySchema.host_organization_s)) add(doc, YaCySchema.host_organization_s, orga);
            if (allAttr || contains(YaCySchema.host_organizationdnc_s)) add(doc, YaCySchema.host_organizationdnc_s, orga + '.' + dnc);
            if (allAttr || contains(YaCySchema.host_subdomain_s)) add(doc, YaCySchema.host_subdomain_s, subdom);
        }

        List<String> titles = yacydoc.titles();
        if (allAttr || contains(YaCySchema.title)) add(doc, YaCySchema.title, titles);
        if (allAttr || contains(YaCySchema.title_count_i)) add(doc, YaCySchema.title_count_i, titles.size());
        if (allAttr || contains(YaCySchema.title_chars_val)) {
            ArrayList<Integer> cv = new ArrayList<Integer>(titles.size());
            for (String s: titles) cv.add(new Integer(s.length()));
            add(doc, YaCySchema.title_chars_val, cv);
        }
        if (allAttr || contains(YaCySchema.title_words_val)) {
            ArrayList<Integer> cv = new ArrayList<Integer>(titles.size());
            for (String s: titles) cv.add(new Integer(s.split(" ").length));
            add(doc, YaCySchema.title_words_val, cv);
        }

        String description = yacydoc.dc_description();
        List<String> descriptions = new ArrayList<String>();
        for (String s: description.split("\n")) descriptions.add(s);
        if (allAttr || contains(YaCySchema.description)) add(doc, YaCySchema.description, description);
        if (allAttr || contains(YaCySchema.description_count_i)) add(doc, YaCySchema.description_count_i, descriptions.size());
        if (allAttr || contains(YaCySchema.description_chars_val)) {
            ArrayList<Integer> cv = new ArrayList<Integer>(descriptions.size());
            for (String s: descriptions) cv.add(new Integer(s.length()));
            add(doc, YaCySchema.description_chars_val, cv);
        }
        if (allAttr || contains(YaCySchema.description_words_val)) {
            ArrayList<Integer> cv = new ArrayList<Integer>(descriptions.size());
            for (String s: descriptions) cv.add(new Integer(s.split(" ").length));
            add(doc, YaCySchema.description_words_val, cv);
        }

        if (allAttr || contains(YaCySchema.author)) add(doc, YaCySchema.author, yacydoc.dc_creator());
        if (allAttr || contains(YaCySchema.content_type)) add(doc, YaCySchema.content_type, new String[]{yacydoc.dc_format()});
        if (allAttr || contains(YaCySchema.last_modified)) add(doc, YaCySchema.last_modified, header == null ? new Date() : header.lastModified());
        if (allAttr || contains(YaCySchema.keywords)) add(doc, YaCySchema.keywords, yacydoc.dc_subject(' '));
        final String content = yacydoc.getTextString();
        if (allAttr || contains(YaCySchema.text_t)) add(doc, YaCySchema.text_t, content);
        if (allAttr || contains(YaCySchema.wordcount_i)) {
            final int contentwc = content.split(" ").length;
            add(doc, YaCySchema.wordcount_i, contentwc);
        }
        if (allAttr || contains(YaCySchema.synonyms_sxt)) {
            List<String> synonyms = condenser.synonyms();
            add(doc, YaCySchema.synonyms_sxt, synonyms);
        }

        // path elements of link
        if (allAttr || contains(YaCySchema.url_paths_sxt)) add(doc, YaCySchema.url_paths_sxt, digestURI.getPaths());
        if (allAttr || contains(YaCySchema.url_file_ext_s)) add(doc, YaCySchema.url_file_ext_s, digestURI.getFileExtension());

        // get list of all links; they will be shrinked by urls that appear in other fields of the solr scheme
        Set<MultiProtocolURI> inboundLinks = yacydoc.inboundLinks();
        Set<MultiProtocolURI> outboundLinks = yacydoc.outboundLinks();

        int c = 0;
        final Object parser = yacydoc.getParserObject();
        Map<MultiProtocolURI, ImageEntry> images = new HashMap<MultiProtocolURI, ImageEntry>();
        if (parser instanceof ContentScraper) {
            final ContentScraper html = (ContentScraper) parser;
            images = html.getImages();

            // header tags
            int h = 0;
            int f = 1;
            String[] hs;

            hs = html.getHeadlines(1); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, YaCySchema.h1_txt, hs); add(doc, YaCySchema.h1_i, hs.length);
            hs = html.getHeadlines(2); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, YaCySchema.h2_txt, hs); add(doc, YaCySchema.h2_i, hs.length);
            hs = html.getHeadlines(3); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, YaCySchema.h3_txt, hs); add(doc, YaCySchema.h3_i, hs.length);
            hs = html.getHeadlines(4); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, YaCySchema.h4_txt, hs); add(doc, YaCySchema.h4_i, hs.length);
            hs = html.getHeadlines(5); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, YaCySchema.h5_txt, hs); add(doc, YaCySchema.h5_i, hs.length);
            hs = html.getHeadlines(6); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, YaCySchema.h6_txt, hs); add(doc, YaCySchema.h6_i, hs.length);
       
            add(doc, YaCySchema.htags_i, h);
            add(doc, YaCySchema.schema_org_breadcrumb_i, html.breadcrumbCount());

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
            if (header != null) {
                x_robots_tag = header.get(HeaderFramework.X_ROBOTS_TAG, "");
                if (x_robots_tag.isEmpty()) {
                    x_robots_tag = header.get(HeaderFramework.X_ROBOTS, "");
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
            add(doc, YaCySchema.robots_i, b);

            // meta tags: generator
            final String generator = html.getMetas().get("generator");
            if (generator != null) add(doc, YaCySchema.metagenerator_t, generator);

            // bold, italic
            final String[] bold = html.getBold();
            add(doc, YaCySchema.boldcount_i, bold.length);
            if (bold.length > 0) {
                add(doc, YaCySchema.bold_txt, bold);
                if (allAttr || contains(YaCySchema.bold_val)) {
                    add(doc, YaCySchema.bold_val, html.getBoldCount(bold));
                }
            }
            final String[] italic = html.getItalic();
            add(doc, YaCySchema.italiccount_i, italic.length);
            if (italic.length > 0) {
                add(doc, YaCySchema.italic_txt, italic);
                if (allAttr || contains(YaCySchema.italic_val)) {
                    add(doc, YaCySchema.italic_val, html.getItalicCount(italic));
                }
            }
            final String[] underline = html.getUnderline();
            add(doc, YaCySchema.underlinecount_i, underline.length);
            if (underline.length > 0) {
                add(doc, YaCySchema.underline_txt, underline);
                if (allAttr || contains(YaCySchema.underline_val)) {
                    add(doc, YaCySchema.underline_val, html.getUnderlineCount(underline));
                }
            }
            final String[] li = html.getLi();
            add(doc, YaCySchema.licount_i, li.length);
            if (li.length > 0) add(doc, YaCySchema.li_txt, li);

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
            if (allAttr || contains(YaCySchema.imagescount_i)) add(doc, YaCySchema.imagescount_i, imgtags.size());
            if (allAttr || contains(YaCySchema.images_tag_txt)) add(doc, YaCySchema.images_tag_txt, imgtags);
            if (allAttr || contains(YaCySchema.images_protocol_sxt)) add(doc, YaCySchema.images_protocol_sxt, protocolList2indexedList(imgprots));
            if (allAttr || contains(YaCySchema.images_urlstub_txt)) add(doc, YaCySchema.images_urlstub_txt, imgstubs);
            if (allAttr || contains(YaCySchema.images_alt_txt)) add(doc, YaCySchema.images_alt_txt, imgalts);
            if (allAttr || contains(YaCySchema.images_withalt_i)) add(doc, YaCySchema.images_withalt_i, withalt);

            // style sheets
            if (allAttr || contains(YaCySchema.css_tag_txt)) {
                final Map<MultiProtocolURI, String> csss = html.getCSS();
                final String[] css_tag = new String[csss.size()];
                final String[] css_url = new String[csss.size()];
                c = 0;
                for (final Map.Entry<MultiProtocolURI, String> entry: csss.entrySet()) {
                    final String url = entry.getKey().toNormalform(false, false);
                    inboundLinks.remove(url);
                    outboundLinks.remove(url);
                    css_tag[c] =
                        "<link rel=\"stylesheet\" type=\"text/css\" media=\"" + entry.getValue() + "\"" +
                        " href=\""+ url + "\" />";
                    css_url[c] = url;
                    c++;
                }
                add(doc, YaCySchema.csscount_i, css_tag.length);
                if (css_tag.length > 0) add(doc, YaCySchema.css_tag_txt, css_tag);
                if (css_url.length > 0) add(doc, YaCySchema.css_url_txt, css_url);
            }

            // Scripts
            if (allAttr || contains(YaCySchema.scripts_txt)) {
                final Set<MultiProtocolURI> scriptss = html.getScript();
                final String[] scripts = new String[scriptss.size()];
                c = 0;
                for (final MultiProtocolURI url: scriptss) {
                    inboundLinks.remove(url);
                    outboundLinks.remove(url);
                    scripts[c++] = url.toNormalform(false, false);
                }
                add(doc, YaCySchema.scriptscount_i, scripts.length);
                if (scripts.length > 0) add(doc, YaCySchema.scripts_txt, scripts);
            }

            // Frames
            if (allAttr || contains(YaCySchema.frames_txt)) {
                final Set<MultiProtocolURI> framess = html.getFrames();
                final String[] frames = new String[framess.size()];
                c = 0;
                for (final MultiProtocolURI url: framess) {
                    inboundLinks.remove(url);
                    outboundLinks.remove(url);
                    frames[c++] = url.toNormalform(false, false);
                }
                add(doc, YaCySchema.framesscount_i, frames.length);
                if (frames.length > 0) add(doc, YaCySchema.frames_txt, frames);
            }

            // IFrames
            if (allAttr || contains(YaCySchema.iframes_txt)) {
                final Set<MultiProtocolURI> iframess = html.getIFrames();
                final String[] iframes = new String[iframess.size()];
                c = 0;
                for (final MultiProtocolURI url: iframess) {
                    inboundLinks.remove(url);
                    outboundLinks.remove(url);
                    iframes[c++] = url.toNormalform(false, false);
                }
                add(doc, YaCySchema.iframesscount_i, iframes.length);
                if (iframes.length > 0) add(doc, YaCySchema.iframes_txt, iframes);
            }

            // canonical tag
            if (allAttr || contains(YaCySchema.canonical_t)) {
                final MultiProtocolURI canonical = html.getCanonical();
                if (canonical != null) {
                    inboundLinks.remove(canonical);
                    outboundLinks.remove(canonical);
                    add(doc, YaCySchema.canonical_t, canonical.toNormalform(false, false));
                }
            }

            // meta refresh tag
            if (allAttr || contains(YaCySchema.refresh_s)) {
                String refresh = html.getRefreshPath();
                if (refresh != null && refresh.length() > 0) {
                    MultiProtocolURI refreshURL;
                    try {
                        refreshURL = refresh.startsWith("http") ? new MultiProtocolURI(html.getRefreshPath()) : new MultiProtocolURI(digestURI, html.getRefreshPath());
                        if (refreshURL != null) {
                            inboundLinks.remove(refreshURL);
                            outboundLinks.remove(refreshURL);
                            add(doc, YaCySchema.refresh_s, refreshURL.toNormalform(false, false));
                        }
                    } catch (MalformedURLException e) {
                        add(doc, YaCySchema.refresh_s, refresh);
                    }
                }
            }

            // flash embedded
            if (allAttr || contains(YaCySchema.flash_b)) {
                MultiProtocolURI[] flashURLs = html.getFlash();
                for (MultiProtocolURI u: flashURLs) {
                    // remove all flash links from ibound/outbound links
                    inboundLinks.remove(u);
                    outboundLinks.remove(u);
                }
                add(doc, YaCySchema.flash_b, flashURLs.length > 0);
            }

            // generic evaluation pattern
            for (final String model: html.getEvaluationModelNames()) {
                if (allAttr || contains("ext_" + model + "_txt")) {
                    final String[] scorenames = html.getEvaluationModelScoreNames(model);
                    if (scorenames.length > 0) {
                        add(doc, YaCySchema.valueOf("ext_" + model + "_txt"), scorenames);
                        add(doc, YaCySchema.valueOf("ext_" + model + "_val"), html.getEvaluationModelScoreCounts(model, scorenames));
                    }
                }
            }

            // response time
            add(doc, YaCySchema.responsetime_i, header == null ? 0 : Integer.parseInt(header.get(HeaderFramework.RESPONSE_TIME_MILLIS, "0")));
        }

        // list all links
        final Map<MultiProtocolURI, Properties> alllinks = yacydoc.getAnchors();
        c = 0;
        if (allAttr || contains(YaCySchema.inboundlinkscount_i)) add(doc, YaCySchema.inboundlinkscount_i, inboundLinks.size());
        if (allAttr || contains(YaCySchema.inboundlinksnofollowcount_i)) add(doc, YaCySchema.inboundlinksnofollowcount_i, yacydoc.inboundLinkNofollowCount());
        final List<String> inboundlinksTag = new ArrayList<String>(inboundLinks.size());
        final List<String> inboundlinksURLProtocol = new ArrayList<String>(inboundLinks.size());
        final List<String> inboundlinksURLStub = new ArrayList<String>(inboundLinks.size());
        final List<String> inboundlinksName = new ArrayList<String>(inboundLinks.size());
        final List<String> inboundlinksRel = new ArrayList<String>(inboundLinks.size());
        final List<String> inboundlinksText = new ArrayList<String>(inboundLinks.size());
        final List<Integer> inboundlinksTextChars = new ArrayList<Integer>(inboundLinks.size());
        final List<Integer> inboundlinksTextWords = new ArrayList<Integer>(inboundLinks.size());
        final List<String> inboundlinksAltTag = new ArrayList<String>(inboundLinks.size());
        for (final MultiProtocolURI url: inboundLinks) {
            final Properties p = alllinks.get(url);
            if (p == null) continue;
            final String name = p.getProperty("name", ""); // the name attribute
            final String rel = p.getProperty("rel", "");   // the rel-attribute
            final String text = p.getProperty("text", ""); // the text between the <a></a> tag
            final String urls = url.toNormalform(false, false);
            final int pr = urls.indexOf("://",0);
            inboundlinksURLProtocol.add(urls.substring(0, pr));
            inboundlinksURLStub.add(urls.substring(pr + 3));
            inboundlinksName.add(name.length() > 0 ? name : "");
            inboundlinksRel.add(rel.length() > 0 ? rel : "");
            inboundlinksText.add(text.length() > 0 ? text : "");
            inboundlinksTextChars.add(text.length() > 0 ? text.length() : 0);
            inboundlinksTextWords.add(text.length() > 0 ? text.split(" ").length : 0);
            inboundlinksTag.add(
                "<a href=\"" + url.toNormalform(false, false) + "\"" +
                (rel.length() > 0 ? " rel=\"" + rel + "\"" : "") +
                (name.length() > 0 ? " name=\"" + name + "\"" : "") +
                ">" +
                ((text.length() > 0) ? text : "") + "</a>");
            ImageEntry ientry = images.get(url);
            inboundlinksAltTag.add(ientry == null ? "" : ientry.alt());
            c++;
        }
        if (allAttr || contains(YaCySchema.inboundlinks_tag_txt)) add(doc, YaCySchema.inboundlinks_tag_txt, inboundlinksTag);
        if (allAttr || contains(YaCySchema.inboundlinks_protocol_sxt)) add(doc, YaCySchema.inboundlinks_protocol_sxt, protocolList2indexedList(inboundlinksURLProtocol));
        if (allAttr || contains(YaCySchema.inboundlinks_urlstub_txt)) add(doc, YaCySchema.inboundlinks_urlstub_txt, inboundlinksURLStub);
        if (allAttr || contains(YaCySchema.inboundlinks_name_txt)) add(doc, YaCySchema.inboundlinks_name_txt, inboundlinksName);
        if (allAttr || contains(YaCySchema.inboundlinks_rel_sxt)) add(doc, YaCySchema.inboundlinks_rel_sxt, inboundlinksRel);
        if (allAttr || contains(YaCySchema.inboundlinks_relflags_val)) add(doc, YaCySchema.inboundlinks_relflags_val, relEval(inboundlinksRel));
        if (allAttr || contains(YaCySchema.inboundlinks_text_txt)) add(doc, YaCySchema.inboundlinks_text_txt, inboundlinksText);
        if (allAttr || contains(YaCySchema.inboundlinks_text_chars_val)) add(doc, YaCySchema.inboundlinks_text_chars_val, inboundlinksTextChars);
        if (allAttr || contains(YaCySchema.inboundlinks_text_words_val)) add(doc, YaCySchema.inboundlinks_text_words_val, inboundlinksTextWords);
        if (allAttr || contains(YaCySchema.inboundlinks_alttag_txt)) add(doc, YaCySchema.inboundlinks_alttag_txt, inboundlinksAltTag);

        c = 0;
        if (allAttr || contains(YaCySchema.outboundlinkscount_i)) add(doc, YaCySchema.outboundlinkscount_i, outboundLinks.size());
        if (allAttr || contains(YaCySchema.outboundlinksnofollowcount_i)) add(doc, YaCySchema.outboundlinksnofollowcount_i, yacydoc.outboundLinkNofollowCount());
        final List<String> outboundlinksTag = new ArrayList<String>(outboundLinks.size());
        final List<String> outboundlinksURLProtocol = new ArrayList<String>(outboundLinks.size());
        final List<String> outboundlinksURLStub = new ArrayList<String>(outboundLinks.size());
        final List<String> outboundlinksName = new ArrayList<String>(outboundLinks.size());
        final List<String> outboundlinksRel = new ArrayList<String>(outboundLinks.size());
        final List<Integer> outboundlinksTextChars = new ArrayList<Integer>(outboundLinks.size());
        final List<Integer> outboundlinksTextWords = new ArrayList<Integer>(outboundLinks.size());
        final List<String> outboundlinksText = new ArrayList<String>(outboundLinks.size());
        final List<String> outboundlinksAltTag = new ArrayList<String>(outboundLinks.size());
        for (final MultiProtocolURI url: outboundLinks) {
            final Properties p = alllinks.get(url);
            if (p == null) continue;
            final String name = p.getProperty("name", ""); // the name attribute
            final String rel = p.getProperty("rel", "");   // the rel-attribute
            final String text = p.getProperty("text", ""); // the text between the <a></a> tag
            final String urls = url.toNormalform(false, false);
            final int pr = urls.indexOf("://",0);
            outboundlinksURLProtocol.add(urls.substring(0, pr));
            outboundlinksURLStub.add(urls.substring(pr + 3));
            outboundlinksName.add(name.length() > 0 ? name : "");
            outboundlinksRel.add(rel.length() > 0 ? rel : "");
            outboundlinksText.add(text.length() > 0 ? text : "");
            outboundlinksTextChars.add(text.length() > 0 ? text.length() : 0);
            outboundlinksTextWords.add(text.length() > 0 ? text.split(" ").length : 0);
            outboundlinksTag.add(
                "<a href=\"" + url.toNormalform(false, false) + "\"" +
                (rel.length() > 0 ? " rel=\"" + rel + "\"" : "") +
                (name.length() > 0 ? " name=\"" + name + "\"" : "") +
                ">" +
                ((text.length() > 0) ? text : "") + "</a>");
            ImageEntry ientry = images.get(url);
            inboundlinksAltTag.add(ientry == null ? "" : ientry.alt());
            c++;
        }
        if (allAttr || contains(YaCySchema.outboundlinks_tag_txt)) add(doc, YaCySchema.outboundlinks_tag_txt, outboundlinksTag);
        if (allAttr || contains(YaCySchema.outboundlinks_protocol_sxt)) add(doc, YaCySchema.outboundlinks_protocol_sxt, protocolList2indexedList(outboundlinksURLProtocol));
        if (allAttr || contains(YaCySchema.outboundlinks_urlstub_txt)) add(doc, YaCySchema.outboundlinks_urlstub_txt, outboundlinksURLStub);
        if (allAttr || contains(YaCySchema.outboundlinks_name_txt)) add(doc, YaCySchema.outboundlinks_name_txt, outboundlinksName);
        if (allAttr || contains(YaCySchema.outboundlinks_rel_sxt)) add(doc, YaCySchema.outboundlinks_rel_sxt, outboundlinksRel);
        if (allAttr || contains(YaCySchema.outboundlinks_relflags_val)) add(doc, YaCySchema.outboundlinks_relflags_val, relEval(outboundlinksRel));
        if (allAttr || contains(YaCySchema.outboundlinks_text_txt)) add(doc, YaCySchema.outboundlinks_text_txt, outboundlinksText);
        if (allAttr || contains(YaCySchema.outboundlinks_text_chars_val)) add(doc, YaCySchema.outboundlinks_text_chars_val, outboundlinksTextChars);
        if (allAttr || contains(YaCySchema.outboundlinks_text_words_val)) add(doc, YaCySchema.outboundlinks_text_words_val, outboundlinksTextWords);
        if (allAttr || contains(YaCySchema.outboundlinks_alttag_txt)) add(doc, YaCySchema.outboundlinks_alttag_txt, outboundlinksAltTag);

        // charset
        if (allAttr || contains(YaCySchema.charset_s)) add(doc, YaCySchema.charset_s, yacydoc.getCharset());

        // coordinates
        if (yacydoc.lat() != 0.0f && yacydoc.lon() != 0.0f) {
            if (allAttr || contains(YaCySchema.coordinate_p)) add(doc, YaCySchema.coordinate_p, Double.toString(yacydoc.lat()) + "," + Double.toString(yacydoc.lon()));
        }
        if (allAttr || contains(YaCySchema.httpstatus_i)) add(doc, YaCySchema.httpstatus_i, header == null ? 200 : header.getStatusCode());

        // fields that are additionally in URIMetadataRow
        if (allAttr || contains(YaCySchema.load_date_dt)) add(doc, YaCySchema.load_date_dt, metadata.loaddate());
        if (allAttr || contains(YaCySchema.fresh_date_dt)) add(doc, YaCySchema.fresh_date_dt, metadata.freshdate());
        if (allAttr || contains(YaCySchema.host_id_s)) add(doc, YaCySchema.host_id_s, metadata.hosthash());
        if ((allAttr || contains(YaCySchema.referrer_id_txt)) && metadata.referrerHash() != null) add(doc, YaCySchema.referrer_id_txt, new String[]{ASCII.String(metadata.referrerHash())});
        //if (allAttr || contains(SolrField.md5_s)) add(solrdoc, SolrField.md5_s, new byte[0]);
        if (allAttr || contains(YaCySchema.publisher_t)) add(doc, YaCySchema.publisher_t, yacydoc.dc_publisher());
        if ((allAttr || contains(YaCySchema.language_s)) && metadata.language() != null) add(doc, YaCySchema.language_s, UTF8.String(metadata.language()));
        if (allAttr || contains(YaCySchema.size_i)) add(doc, YaCySchema.size_i, metadata.size());
        if (allAttr || contains(YaCySchema.audiolinkscount_i)) add(doc, YaCySchema.audiolinkscount_i, yacydoc.getAudiolinks().size());
        if (allAttr || contains(YaCySchema.videolinkscount_i)) add(doc, YaCySchema.videolinkscount_i, yacydoc.getVideolinks().size());
        if (allAttr || contains(YaCySchema.applinkscount_i)) add(doc, YaCySchema.applinkscount_i, yacydoc.getApplinks().size());

        return doc;
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
    
    public static List<String> indexedList2protocolList(Collection<Object> iplist, int dimension) {
        List<String> a = new ArrayList<String>(dimension);
        for (int i = 0; i < dimension; i++) a.add("http");
        if (iplist == null) return a;
        for (Object ip: iplist) a.set(Integer.parseInt(((String) ip).substring(0, 3)), ((String) ip).substring(4));
        return a;
    }
    
    /**
     * encode a string containing attributes from anchor rel properties binary:
     * bit 0: "me" contained in rel
     * bit 1: "nofollow" contained in rel
     * @param rel
     * @return binary encoded information about rel
     */
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

    public String solrGetID(final SolrDocument solr) {
        return (String) solr.getFieldValue(YaCySchema.id.getSolrFieldName());
    }

    public DigestURI solrGetURL(final SolrDocument solr) {
        try {
            return new DigestURI((String) solr.getFieldValue(YaCySchema.sku.getSolrFieldName()));
        } catch (final MalformedURLException e) {
            return null;
        }
    }

    public String solrGetTitle(final SolrDocument solr) {
        return (String) solr.getFieldValue(YaCySchema.title.getSolrFieldName());
    }

    public String solrGetText(final SolrDocument solr) {
        return (String) solr.getFieldValue(YaCySchema.text_t.getSolrFieldName());
    }

    public String solrGetAuthor(final SolrDocument solr) {
        return (String) solr.getFieldValue(YaCySchema.author.getSolrFieldName());
    }

    public String solrGetDescription(final SolrDocument solr) {
        return (String) solr.getFieldValue(YaCySchema.description.getSolrFieldName());
    }

    public Date solrGetDate(final SolrDocument solr) {
        Date date = (Date) solr.getFieldValue(YaCySchema.last_modified.getSolrFieldName());
        Date now = new Date();
        return date.after(now) ? now : date;
    }

    public Collection<String> solrGetKeywords(final SolrDocument solr) {
        final Collection<Object> c = solr.getFieldValues(YaCySchema.keywords.getSolrFieldName());
        final ArrayList<String> a = new ArrayList<String>();
        for (final Object s: c) {
            a.add((String) s);
        }
        return a;
    }
    
    /**
     * register an entry as error document
     * @param digestURI
     * @param failReason
     * @param httpstatus
     * @throws IOException
     */
    public SolrInputDocument err(final DigestURI digestURI, final String failReason, final int httpstatus) throws IOException {
        final SolrInputDocument solrdoc = new SolrInputDocument();
        add(solrdoc, YaCySchema.id, ASCII.String(digestURI.hash()));
        add(solrdoc, YaCySchema.sku, digestURI.toNormalform(true, false));
        final InetAddress address = digestURI.getInetAddress();
        if (contains(YaCySchema.ip_s) && address != null) add(solrdoc, YaCySchema.ip_s, address.getHostAddress());
        if (contains(YaCySchema.host_s) && digestURI.getHost() != null) add(solrdoc, YaCySchema.host_s, digestURI.getHost());

        // path elements of link
        if (contains(YaCySchema.url_paths_sxt)) add(solrdoc, YaCySchema.url_paths_sxt, digestURI.getPaths());
        if (contains(YaCySchema.url_file_ext_s)) add(solrdoc, YaCySchema.url_file_ext_s, digestURI.getFileExtension());
        
        // fail reason and status
        if (contains(YaCySchema.failreason_t)) add(solrdoc, YaCySchema.failreason_t, failReason);
        if (contains(YaCySchema.httpstatus_i)) add(solrdoc, YaCySchema.httpstatus_i, httpstatus);
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
