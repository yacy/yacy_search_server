/**
 *  WebgraphConfiguration
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.ProcessType;
import net.yacy.cora.federate.solr.SchemaConfiguration;
import net.yacy.cora.federate.solr.SchemaDeclaration;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.HostBalancer;
import net.yacy.document.parser.htmlParser;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.search.schema.CollectionConfiguration.Subgraph;

public class WebgraphConfiguration extends SchemaConfiguration implements Serializable {

    private static final long serialVersionUID=-499100932212840385L;

    /**
     * initialize with an empty ConfigurationSet which will cause that all the index
     * attributes are used
     */
    public WebgraphConfiguration(boolean lazy) {
        super();
        this.lazy = lazy;
    }
    
    /**
     * initialize the schema with a given configuration file
     * the configuration file simply contains a list of lines with keywords
     * or keyword = value lines (while value is a custom Solr field name
     * @param configurationFile
     * @throws IOException 
     */
    public WebgraphConfiguration(final File configurationFile, boolean lazy) throws IOException {
        super(configurationFile);
        this.lazy = lazy;
        // check consistency: compare with YaCyField enum
        if (this.isEmpty()) return;
        Iterator<Entry> it = this.entryIterator();
        for (SchemaConfiguration.Entry etr = it.next(); it.hasNext(); etr = it.next()) {
            try {
                WebgraphSchema f = WebgraphSchema.valueOf(etr.key());
                f.setSolrFieldName(etr.getValue());
            } catch (final IllegalArgumentException e) {
                ConcurrentLog.fine("SolrWebgraphWriter", "solr schema file " + configurationFile.getAbsolutePath() + " defines unknown attribute '" + etr.toString() + "'");
                it.remove();
            }
        }
        // check consistency the other way: look if all enum constants in SolrField appear in the configuration file
        for (SchemaDeclaration field: WebgraphSchema.values()) {
            if (this.get(field.name()) == null) {
                ConcurrentLog.warn("SolrWebgraphWriter", " solr schema file " + configurationFile.getAbsolutePath() + " is missing declaration for '" + field.name() + "'");
            }
        }
    }
    
    public List<SolrInputDocument> getEdges(
            final Subgraph subgraph,
            final DigestURL source, final ResponseHeader responseHeader, Map<String, Pattern> collections, int crawldepth_source,
            final List<ImageEntry> images, final Collection<AnchorURL> links,
            final String sourceName) {
        boolean allAttr = this.isEmpty();
        boolean generalNofollow = responseHeader == null ? false : responseHeader.get("X-Robots-Tag", "").indexOf("nofollow") >= 0;
        int target_order = 0;
        List<SolrInputDocument> edges = new ArrayList<SolrInputDocument>();
        for (final AnchorURL target_url: links) {
            SolrInputDocument edge = getEdge(
                    subgraph, source, responseHeader, collections, crawldepth_source, images,
                    sourceName, allAttr, generalNofollow, target_order, target_url);
            target_order++;
            // add the edge to the subgraph
            edges.add(edge);
        }
        return edges;
    }
    
    public SolrInputDocument getEdge(
            final Subgraph subgraph,
            final DigestURL source_url, final ResponseHeader responseHeader, Map<String, Pattern> collections, int crawldepth_source,
            final List<ImageEntry> images,
            final String sourceName, boolean allAttr, boolean generalNofollow, int target_order, AnchorURL target_url) {

        Set<ProcessType> processTypes = new LinkedHashSet<ProcessType>();
        final String name = target_url.getNameProperty(); // the name attribute
        final String text = target_url.getTextProperty(); // the text between the <a></a> tag
        String rel = target_url.getRelProperty();         // the rel-attribute
        String source_host = source_url.getHost();
        String target_host = target_url.getHost();
        if (generalNofollow) {
            // patch the rel attribute since the header makes nofollow valid for all links
            if (rel.length() == 0) rel = "nofollow"; else if (rel.indexOf("nofollow") < 0) rel += ",nofollow"; 
        }
        
        // index organization
        StringBuilder idi = new StringBuilder(8);
        idi.append(Integer.toHexString((name + text + rel).hashCode()).toLowerCase());
        while (idi.length() < 8) idi.insert(0, '0');
        String source_id = ASCII.String(source_url.hash());
        String target_id = ASCII.String(target_url.hash());
        StringBuilder id = new StringBuilder(source_id).append(target_id).append(idi);
        SolrInputDocument edge = new SolrInputDocument();
        add(edge, WebgraphSchema.id, id.toString());
        add(edge, WebgraphSchema.target_order_i, target_order);
        if (allAttr || contains(WebgraphSchema.load_date_dt)) {
            Date loadDate = new Date();
            Date modDate = responseHeader == null ? new Date() : responseHeader.lastModified();
            if (modDate.getTime() > loadDate.getTime()) modDate = loadDate;
            add(edge, WebgraphSchema.load_date_dt, loadDate);
        }
        if (allAttr || contains(WebgraphSchema.last_modified)) add(edge, WebgraphSchema.last_modified, responseHeader == null ? new Date() : responseHeader.lastModified());
        final String source_url_string = source_url.toNormalform(false);
        if (allAttr || contains(CollectionSchema.collection_sxt) && collections != null && collections.size() > 0) {
            List<String> cs = new ArrayList<String>();
            for (Map.Entry<String, Pattern> e: collections.entrySet()) {
                if (e.getValue().matcher(source_url_string).matches()) cs.add(e.getKey());
            }
            add(edge, WebgraphSchema.collection_sxt, cs);
        }

        // add the source attributes
        add(edge, WebgraphSchema.source_id_s, source_id);
        int pr_source = source_url_string.indexOf("://",0);
        if (allAttr || contains(WebgraphSchema.source_protocol_s)) add(edge, WebgraphSchema.source_protocol_s, source_url_string.substring(0, pr_source));
        if (allAttr || contains(WebgraphSchema.source_urlstub_s)) add(edge, WebgraphSchema.source_urlstub_s, source_url_string.substring(pr_source + 3));
        Map<String, String> source_searchpart = source_url.getSearchpartMap();
        if (source_searchpart == null) {
            if (allAttr || contains(WebgraphSchema.source_parameter_count_i)) add(edge, WebgraphSchema.source_parameter_count_i, 0);
        } else {
            if (allAttr || contains(WebgraphSchema.source_parameter_count_i)) add(edge, WebgraphSchema.source_parameter_count_i, source_searchpart.size());
            if (allAttr || contains(WebgraphSchema.source_parameter_key_sxt)) add(edge, WebgraphSchema.source_parameter_key_sxt, source_searchpart.keySet().toArray(new String[source_searchpart.size()]));
            if (allAttr || contains(WebgraphSchema.source_parameter_value_sxt)) add(edge, WebgraphSchema.source_parameter_value_sxt, source_searchpart.values().toArray(new String[source_searchpart.size()]));
        }
        if (allAttr || contains(WebgraphSchema.source_chars_i)) add(edge, WebgraphSchema.source_chars_i, source_url_string.length());
        if (source_host != null) {
            String dnc = Domains.getDNC(source_host);
            String subdomOrga = source_host.length() - dnc.length() <= 0 ? "" : source_host.substring(0, source_host.length() - dnc.length() - 1);
            int pp = subdomOrga.lastIndexOf('.');
            String subdom = (pp < 0) ? "" : subdomOrga.substring(0, pp);
            String orga = (pp < 0) ? subdomOrga : subdomOrga.substring(pp + 1);
            if (allAttr || contains(WebgraphSchema.source_host_s)) add(edge, WebgraphSchema.source_host_s, source_host);
            if (allAttr || contains(WebgraphSchema.source_host_id_s)) add(edge, WebgraphSchema.source_host_id_s, source_url.hosthash());
            if (allAttr || contains(WebgraphSchema.source_host_dnc_s)) add(edge, WebgraphSchema.source_host_dnc_s, dnc);
            if (allAttr || contains(WebgraphSchema.source_host_organization_s)) add(edge, WebgraphSchema.source_host_organization_s, orga);
            if (allAttr || contains(WebgraphSchema.source_host_organizationdnc_s)) add(edge, WebgraphSchema.source_host_organizationdnc_s, orga + '.' + dnc);
            if (allAttr || contains(WebgraphSchema.source_host_subdomain_s)) add(edge, WebgraphSchema.source_host_subdomain_s, subdom);
        }
        if (allAttr || contains(WebgraphSchema.source_file_ext_s) || contains(WebgraphSchema.source_file_name_s)) {
            String source_file_name = source_url.getFileName();
            String source_file_ext = MultiProtocolURL.getFileExtension(source_file_name);
            add(edge, WebgraphSchema.source_file_name_s, source_file_name.toLowerCase().endsWith("." + source_file_ext) ? source_file_name.substring(0, source_file_name.length() - source_file_ext.length() - 1) : source_file_name);
            add(edge, WebgraphSchema.source_file_ext_s, source_file_ext);
        }
        if (allAttr || contains(WebgraphSchema.source_path_s)) add(edge, WebgraphSchema.source_path_s, source_url.getPath());
        if (allAttr || contains(WebgraphSchema.source_path_folders_count_i) || contains(WebgraphSchema.source_path_folders_sxt)) {
            String[] paths = source_url.getPaths();
            add(edge, WebgraphSchema.source_path_folders_count_i, paths.length);
            add(edge, WebgraphSchema.source_path_folders_sxt, paths);
        }
        if ((allAttr || contains(WebgraphSchema.source_crawldepth_i)) && this.contains(WebgraphSchema.source_protocol_s) && this.contains(WebgraphSchema.source_urlstub_s) && this.contains(WebgraphSchema.source_id_s)) {
            add(edge, WebgraphSchema.source_crawldepth_i, crawldepth_source);
        }

        // parse text to find images and clear text
        ContentScraper textContent = null;
        try {textContent = htmlParser.parseToScraper(source_url, responseHeader.getCharacterEncoding(), text, 10);} catch (IOException e) {}
        String extractedText = textContent.getText();
        
        // add the source attributes about the target
        boolean inbound = CollectionConfiguration.enrichSubgraph(subgraph, source_url, target_url);
        if (allAttr || contains(WebgraphSchema.target_inbound_b)) add(edge, WebgraphSchema.target_inbound_b, inbound);
        if (allAttr || contains(WebgraphSchema.target_name_t)) add(edge, WebgraphSchema.target_name_t, name.length() > 0 ? name : "");
        if (allAttr || contains(WebgraphSchema.target_rel_s)) add(edge, WebgraphSchema.target_rel_s, rel.length() > 0 ? rel : "");
        if (allAttr || contains(WebgraphSchema.target_relflags_i)) add(edge, WebgraphSchema.target_relflags_i, relEval(rel.length() > 0 ? rel : ""));
        if (allAttr || contains(WebgraphSchema.target_linktext_t)) add(edge, WebgraphSchema.target_linktext_t, extractedText.length() > 0 ? extractedText : "");
        if (allAttr || contains(WebgraphSchema.target_linktext_charcount_i)) add(edge, WebgraphSchema.target_linktext_charcount_i, extractedText.length());
        if (allAttr || contains(WebgraphSchema.target_linktext_wordcount_i)) add(edge, WebgraphSchema.target_linktext_wordcount_i, extractedText.length() > 0 ? CommonPattern.SPACE.split(extractedText).length : 0);
        
        StringBuilder alttext = new StringBuilder(textContent == null ? 0 : textContent.getImages().size() * 30);
        if (textContent != null) for (ImageEntry ie: textContent.getImages()) {
            if (ie.alt().length() > 0) alttext.append(ie.alt()).append(' ');
        }
        while (alttext.length() > 0 && alttext.charAt(alttext.length() - 1) == ' ') alttext.setLength(alttext.length() - 1);
        if (allAttr || contains(WebgraphSchema.target_alt_t)) add(edge, WebgraphSchema.target_alt_t, alttext.toString());
        if (allAttr || contains(WebgraphSchema.target_alt_charcount_i)) add(edge, WebgraphSchema.target_alt_charcount_i, alttext.length());
        if (allAttr || contains(WebgraphSchema.target_alt_wordcount_i)) add(edge, WebgraphSchema.target_alt_wordcount_i, alttext.length() > 0 ? CommonPattern.SPACE.split(alttext).length : 0);
        
        // add the target attributes
        add(edge, WebgraphSchema.target_id_s, target_id);
        final String target_url_string = target_url.toNormalform(false);
        int pr_target = target_url_string.indexOf("://",0);
        if (allAttr || contains(WebgraphSchema.target_protocol_s)) add(edge, WebgraphSchema.target_protocol_s, target_url_string.substring(0, pr_target));
        if (allAttr || contains(WebgraphSchema.target_urlstub_s)) add(edge, WebgraphSchema.target_urlstub_s, target_url_string.substring(pr_target + 3));
        Map<String, String> target_searchpart = target_url.getSearchpartMap();
        if (target_searchpart == null) {
            if (allAttr || contains(WebgraphSchema.target_parameter_count_i)) add(edge, WebgraphSchema.target_parameter_count_i, 0);
        } else {
            if (allAttr || contains(WebgraphSchema.target_parameter_count_i)) add(edge, WebgraphSchema.target_parameter_count_i, target_searchpart.size());
            if (allAttr || contains(WebgraphSchema.target_parameter_key_sxt)) add(edge, WebgraphSchema.target_parameter_key_sxt, target_searchpart.keySet().toArray(new String[target_searchpart.size()]));
            if (allAttr || contains(WebgraphSchema.target_parameter_value_sxt)) add(edge, WebgraphSchema.target_parameter_value_sxt,  target_searchpart.values().toArray(new String[target_searchpart.size()]));
        }
        if (allAttr || contains(WebgraphSchema.target_chars_i)) add(edge, WebgraphSchema.target_chars_i, target_url_string.length());
        if (target_host != null) {
            String dnc = Domains.getDNC(target_host);
            String subdomOrga = target_host.length() - dnc.length() <= 0 ? "" : target_host.substring(0, target_host.length() - dnc.length() - 1);
            int pp = subdomOrga.lastIndexOf('.');
            String subdom = (pp < 0) ? "" : subdomOrga.substring(0, pp);
            String orga = (pp < 0) ? subdomOrga : subdomOrga.substring(pp + 1);
            if (allAttr || contains(WebgraphSchema.target_host_s)) add(edge, WebgraphSchema.target_host_s, target_host);
            if (allAttr || contains(WebgraphSchema.target_host_id_s)) add(edge, WebgraphSchema.target_host_id_s, target_url.hosthash());
            if (allAttr || contains(WebgraphSchema.target_host_dnc_s)) add(edge, WebgraphSchema.target_host_dnc_s, dnc);
            if (allAttr || contains(WebgraphSchema.target_host_organization_s)) add(edge, WebgraphSchema.target_host_organization_s, orga);
            if (allAttr || contains(WebgraphSchema.target_host_organizationdnc_s)) add(edge, WebgraphSchema.target_host_organizationdnc_s, orga + '.' + dnc);
            if (allAttr || contains(WebgraphSchema.target_host_subdomain_s)) add(edge, WebgraphSchema.target_host_subdomain_s, subdom);
        }
        if (allAttr || contains(WebgraphSchema.target_file_ext_s) || contains(WebgraphSchema.target_file_name_s)) {
            String target_file_name = target_url.getFileName();
            String target_file_ext = MultiProtocolURL.getFileExtension(target_file_name);
            add(edge, WebgraphSchema.target_file_name_s, target_file_name.toLowerCase().endsWith("." + target_file_ext) ? target_file_name.substring(0, target_file_name.length() - target_file_ext.length() - 1) : target_file_name);
            add(edge, WebgraphSchema.target_file_ext_s, target_file_ext);
        }
        if (allAttr || contains(WebgraphSchema.target_path_s)) add(edge, WebgraphSchema.target_path_s, target_url.getPath());
        if (allAttr || contains(WebgraphSchema.target_path_folders_count_i) || contains(WebgraphSchema.target_path_folders_sxt)) {
            String[] paths = target_url.getPaths();
            add(edge, WebgraphSchema.target_path_folders_count_i, paths.length);
            add(edge, WebgraphSchema.target_path_folders_sxt, paths);
        }

        if ((allAttr || contains(WebgraphSchema.target_crawldepth_i)) && this.contains(WebgraphSchema.target_protocol_s) && this.contains(WebgraphSchema.target_urlstub_s) && this.contains(WebgraphSchema.target_id_s)) {
            if (target_host.equals(source_host)) {
                // get the crawl depth from the crawler directly
                Long targetdepth = HostBalancer.depthCache.get(target_url.hash());
                // if the depth is not known yet then this link configuration implies that it is on the next crawl level
                add(edge, WebgraphSchema.target_crawldepth_i, targetdepth == null ? crawldepth_source + 1 : targetdepth.intValue());
            } else {
                // if the target host is not the same as the source host, the interpretation of the crawl depth as the click depth fails
                // in this case we mark the target depth with a special value for that case, 1111
                add(edge, WebgraphSchema.target_crawldepth_i, 1111);
            }
        }
        
        if (allAttr || contains(WebgraphSchema.process_sxt)) {
            List<String> pr = new ArrayList<String>();
            for (ProcessType t: processTypes) pr.add(t.name());
            add(edge, WebgraphSchema.process_sxt, pr);
            if (allAttr || contains(CollectionSchema.harvestkey_s)) {
                add(edge, CollectionSchema.harvestkey_s, sourceName);
            }
        }
        
        // return the edge
        return edge;
    }

    /**
     * encode a string containing attributes from anchor rel properties binary:
     * bit 0: "me" contained in rel
     * bit 1: "nofollow" contained in rel
     * @param rel
     * @return binary encoded information about rel
     */
    private static int relEval(final String rels) {
        int i = 0;
        final String s0 = rels.toLowerCase().trim();
        if ("me".equals(s0)) i += 1;
        if ("nofollow".equals(s0)) i += 2;
        return i;
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
                    SchemaDeclaration f = WebgraphSchema.valueOf(etr.key());
                    f.setSolrFieldName(etr.getValue());
                } catch (final IllegalArgumentException e) {
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
        for (String name: doc.getFieldNames()) {
            if (this.contains(name)) { // check each field if enabled in local Solr schema
                sid.addField(name, doc.getFieldValue(name), 1.0f);
            }
        }
        return sid;
    }

}
