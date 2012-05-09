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
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.storage.ConfigurationSet;
import net.yacy.document.Document;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;

public class SolrScheme extends ConfigurationSet {

    /**
     * initialize with an empty ConfigurationSet which will cause that all the index
     * attributes are used
     */
    public SolrScheme() {
        super();
    }

    /**
     * initialize the scheme with a given configuration file
     * the configuration file simply contains a list of lines with keywords
     * @param configurationFile
     */
    public SolrScheme(final File configurationFile) {
        super(configurationFile);
        // check consistency: compare with YaCyField enum
        for (String name: this) {
            try {
                SolrField.valueOf(name);
            } catch (IllegalArgumentException e) {
                Log.logWarning("SolrScheme", "solr scheme file " + configurationFile.getAbsolutePath() + " defines unknown attribute '" + name + "'");
            }
        }
        /*
        for (YaCyField field: YaCyField.values()) {
            if (!this.contains(field.name())) {
                Log.logWarning("SolrScheme", "solr scheme file " + configurationFile.getAbsolutePath() + " omits known attribute '" + field.name() + "'");
            }
        }
        */
    }

    protected void addSolr(final SolrInputDocument solrdoc, final SolrField key, final String value) {
        if (isEmpty() || contains(key.name())) solrdoc.setField(key.name(), value);
    }

    protected void addSolr(final SolrInputDocument solrdoc, final SolrField key, final Date value) {
        if (isEmpty() || contains(key.name())) solrdoc.setField(key.name(), value);
    }

    protected void addSolr(final SolrInputDocument solrdoc, final SolrField key, final int value) {
        if (isEmpty() || contains(key.name())) solrdoc.setField(key.name(), value);
    }

    protected void addSolr(final SolrInputDocument solrdoc, final SolrField key, final String[] value) {
        if (isEmpty() || contains(key.name())) solrdoc.setField(key.name(), value);
    }

    protected void addSolr(final SolrInputDocument solrdoc, final SolrField key, final float value) {
        if (isEmpty() || contains(key.name())) solrdoc.setField(key.name(), value);
    }

    protected void addSolr(final SolrInputDocument solrdoc, final SolrField key, final boolean value) {
        if (isEmpty() || contains(key.name())) solrdoc.setField(key.name(), value);
    }

    protected void addSolr(final SolrInputDocument solrdoc, final SolrField key, final String value, final float boost) {
        if (isEmpty() || contains(key.name())) solrdoc.setField(key.name(), value, boost);
    }

    public SolrInputDocument yacy2solr(final String id, final ResponseHeader header, final Document yacydoc) {
        // we user the SolrCell design as index scheme
        final SolrInputDocument solrdoc = new SolrInputDocument();
        final DigestURI digestURI = new DigestURI(yacydoc.dc_source());
        addSolr(solrdoc, SolrField.failreason_t, ""); // overwrite a possible fail reason (in case that there was a fail reason before)
        addSolr(solrdoc, SolrField.id, id);
        addSolr(solrdoc, SolrField.sku, digestURI.toNormalform(true, false));
        final InetAddress address = digestURI.getInetAddress();
        if (address != null) addSolr(solrdoc, SolrField.ip_s, address.getHostAddress());
        if (digestURI.getHost() != null) addSolr(solrdoc, SolrField.host_s, digestURI.getHost());
        addSolr(solrdoc, SolrField.title, yacydoc.dc_title());
        addSolr(solrdoc, SolrField.author, yacydoc.dc_creator());
        addSolr(solrdoc, SolrField.description, yacydoc.dc_description());
        addSolr(solrdoc, SolrField.content_type, yacydoc.dc_format());
        addSolr(solrdoc, SolrField.last_modified, header.lastModified());
        addSolr(solrdoc, SolrField.keywords, yacydoc.dc_subject(' '));
        final String content = UTF8.String(yacydoc.getTextBytes());
        addSolr(solrdoc, SolrField.text_t, content);
        if (isEmpty() || contains(SolrField.wordcount_i.name())) {
            final int contentwc = content.split(" ").length;
            addSolr(solrdoc, SolrField.wordcount_i, contentwc);
        }

        // path elements of link
        final String path = digestURI.getPath();
        if (path != null && (isEmpty() || contains(SolrField.paths_txt.name()))) {
            final String[] paths = path.split("/");
            if (paths.length > 0) addSolr(solrdoc, SolrField.paths_txt, paths);
        }

        // get list of all links; they will be shrinked by urls that appear in other fields of the solr scheme
        Set<MultiProtocolURI> inboundLinks = yacydoc.inboundLinks();
        Set<MultiProtocolURI> ouboundLinks = yacydoc.outboundLinks();

        int c = 0;
        final Object parser = yacydoc.getParserObject();
        if (parser instanceof ContentScraper) {
            final ContentScraper html = (ContentScraper) parser;

            // header tags
            int h = 0;
            int f = 1;
            String[] hs;

            hs = html.getHeadlines(1); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, SolrField.h1_txt, hs);
            hs = html.getHeadlines(2); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, SolrField.h2_txt, hs);
            hs = html.getHeadlines(3); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, SolrField.h3_txt, hs);
            hs = html.getHeadlines(4); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, SolrField.h4_txt, hs);
            hs = html.getHeadlines(5); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, SolrField.h5_txt, hs);
            hs = html.getHeadlines(6); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, SolrField.h6_txt, hs);

            addSolr(solrdoc, SolrField.htags_i, h);

            // canonical tag
            if (html.getCanonical() != null) addSolr(solrdoc, SolrField.canonical_s, html.getCanonical().toNormalform(false, false));

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
            String x_robots_tag = header.get(HeaderFramework.X_ROBOTS_TAG, "");
            if (x_robots_tag.length() == 0) x_robots_tag = header.get(HeaderFramework.X_ROBOTS, "");
            // this tag may have values: noarchive, nosnippet, noindex, unavailable_after
            if (x_robots_tag.length() > 0) {
                if (x_robots_tag.indexOf("noarchive",0) >= 0) b += 256;         // set bit 8
                if (x_robots_tag.indexOf("nosnippet",0) >= 0) b += 512;         // set bit 9
                if (x_robots_tag.indexOf("noindex",0) >= 0) b += 1024;          // set bit 10
                if (x_robots_tag.indexOf("nofollow",0) >= 0) b += 2048;         // set bit 11
                if (x_robots_tag.indexOf("unavailable_after",0) >=0) b += 4096; // set bit 12
            }
            addSolr(solrdoc, SolrField.robots_i, b);

            // meta tags: generator
            final String generator = html.getMetas().get("generator");
            if (generator != null) addSolr(solrdoc, SolrField.metagenerator_t, generator);

            // bold, italic
            final String[] bold = html.getBold();
            addSolr(solrdoc, SolrField.boldcount_i, bold.length);
            if (bold.length > 0) {
                addSolr(solrdoc, SolrField.bold_txt, bold);
                if (isEmpty() || contains(SolrField.bold_val.name())) {
                    addSolr(solrdoc, SolrField.bold_val, html.getBoldCount(bold));
                }
            }
            final String[] italic = html.getItalic();
            addSolr(solrdoc, SolrField.italiccount_i, italic.length);
            if (italic.length > 0) {
                addSolr(solrdoc, SolrField.italic_txt, italic);
                if (isEmpty() || contains(SolrField.italic_val.name())) {
                    addSolr(solrdoc, SolrField.italic_val, html.getItalicCount(italic));
                }
            }
            final String[] li = html.getLi();
            addSolr(solrdoc, SolrField.licount_i, li.length);
            if (li.length > 0) addSolr(solrdoc, SolrField.li_txt, li);

            // images
            final Collection<ImageEntry> imagesc = html.getImages().values();
            final String[] imgtags  = new String[imagesc.size()];
            final String[] imgprots = new String[imagesc.size()];
            final String[] imgstubs = new String[imagesc.size()];
            final String[] imgalts  = new String[imagesc.size()];
            c = 0;
            for (final ImageEntry ie: imagesc) {
                final MultiProtocolURI uri = ie.url();
                inboundLinks.remove(uri);
                ouboundLinks.remove(uri);
                imgtags[c] = ie.toString();
                imgprots[c] = uri.getProtocol();
                imgstubs[c] = uri.toString().substring(imgprots[c].length() + 3);
                imgalts[c] = ie.alt();
                c++;
            }
            addSolr(solrdoc, SolrField.imagescount_i, imgtags.length);
            if (isEmpty() || contains(SolrField.images_tag_txt.name())) addSolr(solrdoc, SolrField.images_tag_txt, imgtags);
            if (isEmpty() || contains(SolrField.images_protocol_txt.name())) addSolr(solrdoc, SolrField.images_protocol_txt, protocolList2indexedList(imgprots));
            if (isEmpty() || contains(SolrField.images_urlstub_txt.name())) addSolr(solrdoc, SolrField.images_urlstub_txt, imgstubs);
            if (isEmpty() || contains(SolrField.images_alt_txt.name())) addSolr(solrdoc, SolrField.images_alt_txt, imgalts);

            // style sheets
            if (isEmpty() || contains(SolrField.css_tag_txt.name())) {
                final Map<MultiProtocolURI, String> csss = html.getCSS();
                final String[] css_tag = new String[csss.size()];
                final String[] css_url = new String[csss.size()];
                c = 0;
                for (final Map.Entry<MultiProtocolURI, String> entry: csss.entrySet()) {
                    final String url = entry.getKey().toNormalform(false, false, false, false);
                    inboundLinks.remove(url);
                    ouboundLinks.remove(url);
                    css_tag[c] =
                        "<link rel=\"stylesheet\" type=\"text/css\" media=\"" + entry.getValue() + "\"" +
                        " href=\""+ url + "\" />";
                    css_url[c] = url;
                    c++;
                }
                addSolr(solrdoc, SolrField.csscount_i, css_tag.length);
                if (css_tag.length > 0) addSolr(solrdoc, SolrField.css_tag_txt, css_tag);
                if (css_url.length > 0) addSolr(solrdoc, SolrField.css_url_txt, css_url);
            }

            // Scripts
            if (isEmpty() || contains(SolrField.scripts_txt.name())) {
                final Set<MultiProtocolURI> scriptss = html.getScript();
                final String[] scripts = new String[scriptss.size()];
                c = 0;
                for (final MultiProtocolURI url: scriptss) {
                    inboundLinks.remove(url);
                    ouboundLinks.remove(url);
                    scripts[c++] = url.toNormalform(false, false, false, false);
                }
                addSolr(solrdoc, SolrField.scriptscount_i, scripts.length);
                if (scripts.length > 0) addSolr(solrdoc, SolrField.scripts_txt, scripts);
            }

            // Frames
            if (isEmpty() || contains(SolrField.frames_txt.name())) {
                final Set<MultiProtocolURI> framess = html.getFrames();
                final String[] frames = new String[framess.size()];
                c = 0;
                for (final MultiProtocolURI url: framess) {
                    inboundLinks.remove(url);
                    ouboundLinks.remove(url);
                    frames[c++] = url.toNormalform(false, false, false, false);
                }
                addSolr(solrdoc, SolrField.framesscount_i, frames.length);
                if (frames.length > 0) addSolr(solrdoc, SolrField.frames_txt, frames);
            }

            // IFrames
            if (isEmpty() || contains(SolrField.iframes_txt.name())) {
                final Set<MultiProtocolURI> iframess = html.getIFrames();
                final String[] iframes = new String[iframess.size()];
                c = 0;
                for (final MultiProtocolURI url: iframess) {
                    inboundLinks.remove(url);
                    ouboundLinks.remove(url);
                    iframes[c++] = url.toNormalform(false, false, false, false);
                }
                addSolr(solrdoc, SolrField.iframesscount_i, iframes.length);
                if (iframes.length > 0) addSolr(solrdoc, SolrField.iframes_txt, iframes);
            }

            // flash embedded
            addSolr(solrdoc, SolrField.flash_b, html.containsFlash());

            // generic evaluation pattern
            for (final String model: html.getEvaluationModelNames()) {
                if (isEmpty() || contains("ext_" + model + "_txt")) {
                    final String[] scorenames = html.getEvaluationModelScoreNames(model);
                    if (scorenames.length > 0) {
                        addSolr(solrdoc, SolrField.valueOf("ext_" + model + "_txt"), scorenames);
                        addSolr(solrdoc, SolrField.valueOf("ext_" + model + "_val"), html.getEvaluationModelScoreCounts(model, scorenames));
                    }
                }
            }

            // response time
            addSolr(solrdoc, SolrField.responsetime_i, header.get(HeaderFramework.RESPONSE_TIME_MILLIS, "0"));
        }

        // list all links
        final Map<MultiProtocolURI, Properties> alllinks = yacydoc.getAnchors();
        c = 0;
        if (isEmpty() || contains(SolrField.inboundlinkscount_i.name())) addSolr(solrdoc, SolrField.inboundlinkscount_i, inboundLinks.size());
        if (isEmpty() || contains(SolrField.inboundlinksnofollowcount_i.name())) addSolr(solrdoc, SolrField.inboundlinksnofollowcount_i, yacydoc.inboundLinkNofollowCount());
        final String[] inboundlinksTag = new String[inboundLinks.size()];
        final String[] inboundlinksURLProtocol = new String[inboundLinks.size()];
        final String[] inboundlinksURLStub = new String[inboundLinks.size()];
        final String[] inboundlinksName = new String[inboundLinks.size()];
        final String[] inboundlinksRel = new String[inboundLinks.size()];
        final String[] inboundlinksText = new String[inboundLinks.size()];
        for (final MultiProtocolURI url: inboundLinks) {
            final Properties p = alllinks.get(url);
            final String name = p.getProperty("name", ""); // the name attribute
            final String rel = p.getProperty("rel", "");   // the rel-attribute
            final String text = p.getProperty("text", ""); // the text between the <a></a> tag
            final String urls = url.toNormalform(false, false);
            final int pr = urls.indexOf("://",0);
            inboundlinksURLProtocol[c] = urls.substring(0, pr);
            inboundlinksURLStub[c] = urls.substring(pr + 3);
            inboundlinksName[c] = name.length() > 0 ? name : "";
            inboundlinksRel[c] = rel.length() > 0 ? rel : "";
            inboundlinksText[c] = text.length() > 0 ? text : "";
            inboundlinksTag[c] =
                "<a href=\"" + url.toNormalform(false, false) + "\"" +
                (rel.length() > 0 ? " rel=\"" + rel + "\"" : "") +
                (name.length() > 0 ? " name=\"" + name + "\"" : "") +
                ">" +
                ((text.length() > 0) ? text : "") + "</a>";
            c++;
        }
        if (isEmpty() || contains(SolrField.inboundlinks_tag_txt.name())) addSolr(solrdoc, SolrField.inboundlinks_tag_txt, inboundlinksTag);
        if (isEmpty() || contains(SolrField.inboundlinks_protocol_txt.name())) addSolr(solrdoc, SolrField.inboundlinks_protocol_txt, protocolList2indexedList(inboundlinksURLProtocol));
        if (isEmpty() || contains(SolrField.inboundlinks_urlstub_txt.name())) addSolr(solrdoc, SolrField.inboundlinks_urlstub_txt, inboundlinksURLStub);
        if (isEmpty() || contains(SolrField.inboundlinks_name_txt.name())) addSolr(solrdoc, SolrField.inboundlinks_name_txt, inboundlinksName);
        if (isEmpty() || contains(SolrField.inboundlinks_rel_txt.name())) addSolr(solrdoc, SolrField.inboundlinks_rel_txt, inboundlinksRel);
        if (isEmpty() || contains(SolrField.inboundlinks_relflags_txt.name())) addSolr(solrdoc, SolrField.inboundlinks_relflags_txt, relEval(inboundlinksRel));
        if (isEmpty() || contains(SolrField.inboundlinks_text_txt.name())) addSolr(solrdoc, SolrField.inboundlinks_text_txt, inboundlinksText);

        c = 0;
        if (isEmpty() || contains(SolrField.outboundlinkscount_i.name())) addSolr(solrdoc, SolrField.outboundlinkscount_i, ouboundLinks.size());
        if (isEmpty() || contains(SolrField.outboundlinksnofollowcount_i.name())) addSolr(solrdoc, SolrField.outboundlinksnofollowcount_i, yacydoc.outboundLinkNofollowCount());
        final String[] outboundlinksTag = new String[ouboundLinks.size()];
        final String[] outboundlinksURLProtocol = new String[ouboundLinks.size()];
        final String[] outboundlinksURLStub = new String[ouboundLinks.size()];
        final String[] outboundlinksName = new String[ouboundLinks.size()];
        final String[] outboundlinksRel = new String[ouboundLinks.size()];
        final String[] outboundlinksText = new String[ouboundLinks.size()];
        for (final MultiProtocolURI url: ouboundLinks) {
            final Properties p = alllinks.get(url);
            final String name = p.getProperty("name", ""); // the name attribute
            final String rel = p.getProperty("rel", "");   // the rel-attribute
            final String text = p.getProperty("text", ""); // the text between the <a></a> tag
            final String urls = url.toNormalform(false, false);
            final int pr = urls.indexOf("://",0);
            outboundlinksURLProtocol[c] = urls.substring(0, pr);
            outboundlinksURLStub[c] = urls.substring(pr + 3);
            outboundlinksName[c] = name.length() > 0 ? name : "";
            outboundlinksRel[c] = rel.length() > 0 ? rel : "";
            outboundlinksText[c] = text.length() > 0 ? text : "";
            outboundlinksTag[c] =
                "<a href=\"" + url.toNormalform(false, false) + "\"" +
                (rel.length() > 0 ? " rel=\"" + rel + "\"" : "") +
                (name.length() > 0 ? " name=\"" + name + "\"" : "") +
                ">" +
                ((text.length() > 0) ? text : "") + "</a>";
            c++;
        }
        if (isEmpty() || contains(SolrField.outboundlinks_tag_txt.name())) addSolr(solrdoc, SolrField.outboundlinks_tag_txt, outboundlinksTag);
        if (isEmpty() || contains(SolrField.outboundlinks_protocol_txt.name())) addSolr(solrdoc, SolrField.outboundlinks_protocol_txt, protocolList2indexedList(outboundlinksURLProtocol));
        if (isEmpty() || contains(SolrField.outboundlinks_urlstub_txt.name())) addSolr(solrdoc, SolrField.outboundlinks_urlstub_txt, outboundlinksURLStub);
        if (isEmpty() || contains(SolrField.outboundlinks_name_txt.name())) addSolr(solrdoc, SolrField.outboundlinks_name_txt, outboundlinksName);
        if (isEmpty() || contains(SolrField.outboundlinks_rel_txt.name())) addSolr(solrdoc, SolrField.outboundlinks_rel_txt, outboundlinksRel);
        if (isEmpty() || contains(SolrField.outboundlinks_relflags_txt.name())) addSolr(solrdoc, SolrField.outboundlinks_relflags_txt, relEval(inboundlinksRel));
        if (isEmpty() || contains(SolrField.outboundlinks_text_txt.name())) addSolr(solrdoc, SolrField.outboundlinks_text_txt, outboundlinksText);


        // charset
        addSolr(solrdoc, SolrField.charset_s, yacydoc.getCharset());

        // coordinates
        if (yacydoc.lat() != 0.0f && yacydoc.lon() != 0.0f) {
            addSolr(solrdoc, SolrField.lon_coordinate, yacydoc.lon());
            addSolr(solrdoc, SolrField.lat_coordinate, yacydoc.lat());
        }
        addSolr(solrdoc, SolrField.httpstatus_i, 200);

        return solrdoc;
    }

    private static String[] protocolList2indexedList(String[] protocol) {
        List<String> a = new ArrayList<String>();
        for (int i = 0; i < protocol.length; i++) {
            if (!protocol[i].equals("http")) {
                String c = Integer.toString(i);
                while (c.length() < 3) c = "0" + c;
                a.add(c + "-" + protocol[i]);
            }
        }
        return a.toArray(new String[a.size()]);
    }

    /**
     * encode a string containing attributes from anchor rel properties binary:
     * bit 0: "me" contained in rel
     * bit 1: "nofollow" contained in rel
     * @param rel
     * @return binary encoded information about rel
     */
    private int relEval(final String[] rel) {
        int i = 0;
        for (final String s: rel) {
            final String s0 = s.toLowerCase().trim();
            if ("me".equals(s0)) i += 1;
            if ("nofollow".equals(s0)) i += 2;
        }
        return i;
    }

    public String solrGetID(final SolrDocument solr) {
        return (String) solr.getFieldValue("id");
    }

    public DigestURI solrGetURL(final SolrDocument solr) {
        try {
            return new DigestURI((String) solr.getFieldValue("sku"));
        } catch (final MalformedURLException e) {
            return null;
        }
    }

    public String solrGetTitle(final SolrDocument solr) {
        return (String) solr.getFieldValue("title");
    }

    public String solrGetText(final SolrDocument solr) {
        return (String) solr.getFieldValue("text_t");
    }

    public String solrGetAuthor(final SolrDocument solr) {
        return (String) solr.getFieldValue("author");
    }

    public String solrGetDescription(final SolrDocument solr) {
        return (String) solr.getFieldValue("description");
    }

    public Date solrGetDate(final SolrDocument solr) {
        return (Date) solr.getFieldValue("last_modified");
    }

    public Collection<String> solrGetKeywords(final SolrDocument solr) {
        final Collection<Object> c = solr.getFieldValues("keywords");
        final ArrayList<String> a = new ArrayList<String>();
        for (final Object s: c) {
            a.add((String) s);
        }
        return a;
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
