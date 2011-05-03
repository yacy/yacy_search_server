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

package net.yacy.cora.services.federated.solr;


import java.net.InetAddress;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.document.Document;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.cora.document.MultiProtocolURI;
import org.apache.solr.common.SolrInputDocument;

public enum SolrScheme {

    SolrCell,
    DublinCore;

    
    public SolrInputDocument yacy2solr(String id, ResponseHeader header, Document document) {
        if (this == SolrCell) return yacy2solrSolrCell(id, header, document);
        return null;
    }
    
    public static SolrInputDocument yacy2solrSolrCell(String id, ResponseHeader header, Document yacydoc) {
        // we user the SolrCell design as index scheme
        SolrInputDocument solrdoc = new SolrInputDocument();
        DigestURI digestURI = new DigestURI(yacydoc.dc_source());
        solrdoc.addField("id", id);
        solrdoc.addField("sku", digestURI.toNormalform(true, false), 3.0f);
        InetAddress address = Domains.dnsResolve(digestURI.getHost());
        if (address != null) solrdoc.addField("ip_s", address.getHostAddress());
        if (digestURI.getHost() != null) solrdoc.addField("host_s", digestURI.getHost());
        solrdoc.addField("title", yacydoc.dc_title());
        solrdoc.addField("author", yacydoc.dc_creator());
        solrdoc.addField("description", yacydoc.dc_description());
        solrdoc.addField("content_type", yacydoc.dc_format());
        solrdoc.addField("last_modified", header.lastModified());
        solrdoc.addField("keywords", yacydoc.dc_subject(' '));
        String content = UTF8.String(yacydoc.getTextBytes());
        solrdoc.addField("text_t", content);
        int contentwc = content.split(" ").length;
        solrdoc.addField("wordcount_i", contentwc);

        // path elements of link
        String path = digestURI.getPath();
        if (path != null) {
            String[] paths = path.split("/");
            if (paths.length > 0) solrdoc.addField("attr_paths", paths);
        }
        
        // list all links
        Map<MultiProtocolURI, Properties> alllinks = yacydoc.getAnchors();        
        int c = 0;
        String[] inboundlinks = new String[yacydoc.inboundLinkCount()];
        solrdoc.addField("inboundlinkscount_i", inboundlinks.length);
        for (MultiProtocolURI url: yacydoc.inboundLinks()) {
            Properties p = alllinks.get(url);
            String name = p.getProperty("name", "");
            String rel = p.getProperty("rel", "");
            inboundlinks[c++] =
                "<a href=\"" + url.toNormalform(false, false) + "\"" +
                ((rel.toLowerCase().equals("nofollow")) ? " rel=\"nofollow\"" : "") +
                ">" +
                ((name.length() > 0) ? name : "") + "</a>";
        }
        solrdoc.addField("attr_inboundlinks", inboundlinks);
        c = 0;
        String[] outboundlinks = new String[yacydoc.outboundLinkCount()];
        solrdoc.addField("outboundlinkscount_i", outboundlinks.length);
        for (MultiProtocolURI url: yacydoc.outboundLinks()) {
            Properties p = alllinks.get(url);
            String name = p.getProperty("name", "");
            String rel = p.getProperty("rel", "");
            outboundlinks[c++] =
                "<a href=\"" + url.toNormalform(false, false) + "\"" +
                ((rel.toLowerCase().equals("nofollow")) ? " rel=\"nofollow\"" : "") +
                ">" +
                ((name.length() > 0) ? name : "") + "</a>";
        }
        solrdoc.addField("attr_outboundlinks", outboundlinks);
        
        // charset
        solrdoc.addField("charset_s", yacydoc.getCharset());

        // coordinates
        if (yacydoc.lat() != 0.0f && yacydoc.lon() != 0.0f) {
            solrdoc.addField("lon_coordinate", yacydoc.lon());
            solrdoc.addField("lat_coordinate", yacydoc.lat());
        }
        solrdoc.addField("httpstatus_i", 200);
        Object parser = yacydoc.getParserObject();
        if (parser instanceof ContentScraper) {
            ContentScraper html = (ContentScraper) parser;
            
            // header tags
            int h = 0;
            int f = 1;
            for (int i = 1; i <= 6; i++) {
                String[] hs = html.getHeadlines(i);
                h = h | (hs.length > 0 ? f : 0);
                f = f * 2;
                solrdoc.addField("attr_h" + i, hs);
            }
            solrdoc.addField("htags_i", h);

            // meta tags
            Map<String, String> metas = html.getMetas();
            String robots = metas.get("robots");
            if (robots != null) solrdoc.addField("metarobots_t", robots);
            String generator = metas.get("generator");
            if (generator != null) solrdoc.addField("metagenerator_t", generator);
            
            // bold, italic
            String[] bold = html.getBold();
            solrdoc.addField("boldcount_i", bold.length);
            if (bold.length > 0) {
                solrdoc.addField("attr_bold", bold);
                solrdoc.addField("attr_boldcount", html.getBoldCount(bold));
            }
            String[] italic = html.getItalic();
            solrdoc.addField("italiccount_i", italic.length);
            if (italic.length > 0) {
                solrdoc.addField("attr_italic", italic);
                solrdoc.addField("attr_italiccount", html.getItalicCount(italic));
            }
            String[] li = html.getLi();
            solrdoc.addField("licount_i", li.length);
            if (li.length > 0) solrdoc.addField("attr_li", li);
            
            // images
            Collection<ImageEntry> imagesc = html.getImages().values();
            String[] images = new String[imagesc.size()];
            c = 0;
            for (ImageEntry ie: imagesc) images[c++] = ie.toString();
            solrdoc.addField("imagescount_i", images.length);
            if (images.length > 0) solrdoc.addField("attr_images", images);

            // style sheets
            Map<MultiProtocolURI, String> csss = html.getCSS();
            String[] css = new String[csss.size()];
            c = 0;
            for (Map.Entry<MultiProtocolURI, String> entry: csss.entrySet()) {
                css[c++] =
                    "<link rel=\"stylesheet\" type=\"text/css\" media=\"" + entry.getValue() + "\"" +
                    " href=\""+ entry.getKey().toNormalform(false, false, false, false) + "\" />";
            }
            solrdoc.addField("csscount_i", css.length);
            if (css.length > 0) solrdoc.addField("attr_css", css);
            
            // Scripts
            Set<MultiProtocolURI> scriptss = html.getScript();
            String[] scripts = new String[scriptss.size()];
            c = 0;
            for (MultiProtocolURI url: scriptss) {
                scripts[c++] = url.toNormalform(false, false, false, false);
            }
            solrdoc.addField("scriptscount_i", scripts.length);
            if (scripts.length > 0) solrdoc.addField("attr_scripts", scripts);
            
            // Frames
            Set<MultiProtocolURI> framess = html.getFrames();
            String[] frames = new String[framess.size()];
            c = 0;
            for (MultiProtocolURI entry: framess) {
                frames[c++] = entry.toNormalform(false, false, false, false);
            }
            solrdoc.addField("framesscount_i", frames.length);
            if (frames.length > 0) solrdoc.addField("attr_frames", frames);
            
            // IFrames
            Set<MultiProtocolURI> iframess = html.getFrames();
            String[] iframes = new String[iframess.size()];
            c = 0;
            for (MultiProtocolURI entry: iframess) {
                iframes[c++] = entry.toNormalform(false, false, false, false);
            }
            solrdoc.addField("iframesscount_i", iframes.length);
            if (iframes.length > 0) solrdoc.addField("attr_iframes", iframes);
            
            // flash embedded
            solrdoc.addField("flash_b", html.containsFlash());
            
            // generic evaluation pattern
            for (String model: html.getEvaluationModelNames()) {
                String[] scorenames = html.getEvaluationModelScoreNames(model);
                if (scorenames.length > 0) {
                    solrdoc.addField("attr_" + model, scorenames);
                    solrdoc.addField("attr_" + model + "count", html.getEvaluationModelScoreCounts(model, scorenames));
                }
            }
        }
        return solrdoc;
    }
    
    
    /*
     * standard solr scheme

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
