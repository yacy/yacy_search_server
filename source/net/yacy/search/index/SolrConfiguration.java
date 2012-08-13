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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.services.federated.solr.SolrDoc;
import net.yacy.cora.storage.ConfigurationSet;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadata;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Bitfield;

import org.apache.solr.common.SolrDocument;

import de.anomic.crawler.retrieval.Response;

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
                Log.logWarning("SolrScheme", "solr scheme file " + configurationFile.getAbsolutePath() + " defines unknown attribute '" + etr.toString() + "'");
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

    private boolean contains(YaCySchema field) {
    	return this.contains(field.name());
    }

    protected void addSolr(final SolrDoc solrdoc, final YaCySchema key, final byte[] value) {
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.length != 0))) solrdoc.addSolr(key, UTF8.String(value));
    }

    protected void addSolr(final SolrDoc solrdoc, final YaCySchema key, final String value) {
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && !value.isEmpty()))) solrdoc.addSolr(key, value);
    }

    protected void addSolr(final SolrDoc solrdoc, final YaCySchema key, final String value, final float boost) {
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && !value.isEmpty()))) solrdoc.addSolr(key, value, boost);
    }

    protected void addSolr(final SolrDoc solrdoc, final YaCySchema key, final Date value) {
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.getTime() > 0))) solrdoc.addSolr(key, value);
    }

    protected void addSolr(final SolrDoc solrdoc, final YaCySchema key, final String[] value) {
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.length > 0))) solrdoc.addSolr(key, value);
    }

    protected void addSolr(final SolrDoc solrdoc, final YaCySchema key, final List<String> value) {
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && !value.isEmpty()))) solrdoc.addSolr(key, value);
    }

    protected void addSolr(final SolrDoc solrdoc, final YaCySchema key, final int value) {
        if ((isEmpty() || contains(key)) && (!this.lazy || value != 0)) solrdoc.addSolr(key, value);
    }

    protected void addSolr(final SolrDoc solrdoc, final YaCySchema key, final long value) {
        if ((isEmpty() || contains(key)) && (!this.lazy || value != 0)) solrdoc.addSolr(key, value);
    }

    protected void addSolr(final SolrDoc solrdoc, final YaCySchema key, final float value) {
        if ((isEmpty() || contains(key)) && (!this.lazy || value != 0.0f)) solrdoc.addSolr(key, value);
    }

    protected void addSolr(final SolrDoc solrdoc, final YaCySchema key, final double value) {
        if ((isEmpty() || contains(key)) && (!this.lazy || value != 0.0d)) solrdoc.addSolr(key, value);
    }

    protected void addSolr(final SolrDoc solrdoc, final YaCySchema key, final boolean value) {
        if (isEmpty() || contains(key)) solrdoc.addSolr(key, value);
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

    public SolrDoc metadata2solr(final URIMetadata md) {
        final SolrDoc solrdoc = new SolrDoc();
        final DigestURI digestURI = new DigestURI(md.url());
        boolean allAttr = this.isEmpty();

        if (allAttr || contains(YaCySchema.failreason_t)) addSolr(solrdoc, YaCySchema.failreason_t, "");
        addSolr(solrdoc, YaCySchema.id, ASCII.String(md.hash()));
        addSolr(solrdoc, YaCySchema.sku, digestURI.toNormalform(true, false));
        if (allAttr || contains(YaCySchema.ip_s)) {
        	final InetAddress address = digestURI.getInetAddress();
        	if (address != null) addSolr(solrdoc, YaCySchema.ip_s, address.getHostAddress());
        }
        if (digestURI.getHost() != null) addSolr(solrdoc, YaCySchema.host_s, digestURI.getHost());
        if (allAttr || contains(YaCySchema.title)) addSolr(solrdoc, YaCySchema.title, md.dc_title());
        if (allAttr || contains(YaCySchema.author)) addSolr(solrdoc, YaCySchema.author, md.dc_creator());
        if (allAttr || contains(YaCySchema.description)) addSolr(solrdoc, YaCySchema.description, md.snippet());
        if (allAttr || contains(YaCySchema.content_type)) addSolr(solrdoc, YaCySchema.content_type, Response.doctype2mime(digestURI.getFileExtension(), md.doctype()));
        if (allAttr || contains(YaCySchema.last_modified)) addSolr(solrdoc, YaCySchema.last_modified, md.moddate());
        if (allAttr || contains(YaCySchema.text_t)) addSolr(solrdoc, YaCySchema.text_t, ""); // not delivered in metadata
        if (allAttr || contains(YaCySchema.wordcount_i)) addSolr(solrdoc, YaCySchema.wordcount_i, md.wordCount());
        if (allAttr || contains(YaCySchema.keywords)) {
        	String keywords = md.dc_subject();
        	Bitfield flags = md.flags();
        	if (flags.get(Condenser.flag_cat_indexof)) {
        		if (keywords == null || keywords.isEmpty()) keywords = "indexof"; else {
        			if (keywords.indexOf(',') > 0) keywords += ", indexof"; else keywords += " indexof";
        		}
        	}
        	addSolr(solrdoc, YaCySchema.keywords, keywords);
        }

        // path elements of link
        final String path = digestURI.getPath();
        if (path != null && (allAttr || contains(YaCySchema.paths_txt))) {
            final String[] paths = path.split("/");
            if (paths.length > 0) addSolr(solrdoc, YaCySchema.paths_txt, paths);
        }

        if (allAttr || contains(YaCySchema.imagescount_i)) addSolr(solrdoc, YaCySchema.imagescount_i, md.limage());
        if (allAttr || contains(YaCySchema.inboundlinkscount_i)) addSolr(solrdoc, YaCySchema.inboundlinkscount_i, md.llocal());
        if (allAttr || contains(YaCySchema.outboundlinkscount_i)) addSolr(solrdoc, YaCySchema.outboundlinkscount_i, md.lother());
        if (allAttr || contains(YaCySchema.charset_s)) addSolr(solrdoc, YaCySchema.charset_s, "UTF8");

        // coordinates
        if (md.lat() != 0.0f && md.lon() != 0.0f) {
            if (allAttr || contains(YaCySchema.lat_coordinate)) addSolr(solrdoc, YaCySchema.lat_coordinate, md.lat());
        	if (allAttr || contains(YaCySchema.lon_coordinate)) addSolr(solrdoc, YaCySchema.lon_coordinate, md.lon());
        }
        if (allAttr || contains(YaCySchema.httpstatus_i)) addSolr(solrdoc, YaCySchema.httpstatus_i, 200);

        // fields that are in URIMetadataRow additional to yacy2solr basic requirement
        if (allAttr || contains(YaCySchema.load_date_dt)) addSolr(solrdoc, YaCySchema.load_date_dt, md.loaddate());
        if (allAttr || contains(YaCySchema.fresh_date_dt)) addSolr(solrdoc, YaCySchema.fresh_date_dt, md.freshdate());
        if (allAttr || contains(YaCySchema.host_id_s)) addSolr(solrdoc, YaCySchema.host_id_s, md.hosthash());
        if ((allAttr || contains(YaCySchema.referrer_id_txt)) && md.referrerHash() != null) addSolr(solrdoc, YaCySchema.referrer_id_txt, new String[]{ASCII.String(md.referrerHash())});
        if (allAttr || contains(YaCySchema.md5_s)) addSolr(solrdoc, YaCySchema.md5_s, md.md5());
        if (allAttr || contains(YaCySchema.publisher_t)) addSolr(solrdoc, YaCySchema.publisher_t, md.dc_publisher());
        if ((allAttr || contains(YaCySchema.language_txt)) && md.language() != null) addSolr(solrdoc, YaCySchema.language_txt,new String[]{UTF8.String(md.language())});
        if (allAttr || contains(YaCySchema.size_i)) addSolr(solrdoc, YaCySchema.size_i, md.size());
        if (allAttr || contains(YaCySchema.audiolinkscount_i)) addSolr(solrdoc, YaCySchema.audiolinkscount_i, md.laudio());
        if (allAttr || contains(YaCySchema.videolinkscount_i)) addSolr(solrdoc, YaCySchema.videolinkscount_i, md.lvideo());
        if (allAttr || contains(YaCySchema.applinkscount_i)) addSolr(solrdoc, YaCySchema.applinkscount_i, md.lapp());

        return solrdoc;
    }

    public SolrDoc yacy2solr(final String id, final ResponseHeader header, final Document yacydoc, final URIMetadata metadata) {
        // we use the SolrCell design as index scheme
        final SolrDoc solrdoc = new SolrDoc();
        final DigestURI digestURI = new DigestURI(yacydoc.dc_source());
        boolean allAttr = this.isEmpty();
        addSolr(solrdoc, YaCySchema.id, id);
        addSolr(solrdoc, YaCySchema.sku, digestURI.toNormalform(true, false));
        if (allAttr || contains(YaCySchema.failreason_t)) addSolr(solrdoc, YaCySchema.failreason_t, ""); // overwrite a possible fail reason (in case that there was a fail reason before)
        if (allAttr || contains(YaCySchema.ip_s)) {
        	final InetAddress address = digestURI.getInetAddress();
        	if (address != null) addSolr(solrdoc, YaCySchema.ip_s, address.getHostAddress());
        }
        if (digestURI.getHost() != null) addSolr(solrdoc, YaCySchema.host_s, digestURI.getHost());
        if (allAttr || contains(YaCySchema.title)) addSolr(solrdoc, YaCySchema.title, yacydoc.dc_title());
        if (allAttr || contains(YaCySchema.author)) addSolr(solrdoc, YaCySchema.author, yacydoc.dc_creator());
        if (allAttr || contains(YaCySchema.description)) addSolr(solrdoc, YaCySchema.description, yacydoc.dc_description());
        if (allAttr || contains(YaCySchema.content_type)) addSolr(solrdoc, YaCySchema.content_type, yacydoc.dc_format());
        if (allAttr || contains(YaCySchema.last_modified)) addSolr(solrdoc, YaCySchema.last_modified, header == null ? new Date() : header.lastModified());
        if (allAttr || contains(YaCySchema.keywords)) addSolr(solrdoc, YaCySchema.keywords, yacydoc.dc_subject(' '));
        final String content = yacydoc.getTextString();
        if (allAttr || contains(YaCySchema.text_t)) addSolr(solrdoc, YaCySchema.text_t, content);
        if (allAttr || contains(YaCySchema.wordcount_i)) {
            final int contentwc = content.split(" ").length;
            addSolr(solrdoc, YaCySchema.wordcount_i, contentwc);
        }

        // path elements of link
        final String path = digestURI.getPath();
        if (path != null && (allAttr || contains(YaCySchema.paths_txt))) {
            final String[] paths = path.split("/");
            if (paths.length > 0) addSolr(solrdoc, YaCySchema.paths_txt, paths);
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

            hs = html.getHeadlines(1); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, YaCySchema.h1_txt, hs);
            hs = html.getHeadlines(2); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, YaCySchema.h2_txt, hs);
            hs = html.getHeadlines(3); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, YaCySchema.h3_txt, hs);
            hs = html.getHeadlines(4); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, YaCySchema.h4_txt, hs);
            hs = html.getHeadlines(5); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, YaCySchema.h5_txt, hs);
            hs = html.getHeadlines(6); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, YaCySchema.h6_txt, hs);

            addSolr(solrdoc, YaCySchema.htags_i, h);

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
            addSolr(solrdoc, YaCySchema.robots_i, b);

            // meta tags: generator
            final String generator = html.getMetas().get("generator");
            if (generator != null) addSolr(solrdoc, YaCySchema.metagenerator_t, generator);

            // bold, italic
            final String[] bold = html.getBold();
            addSolr(solrdoc, YaCySchema.boldcount_i, bold.length);
            if (bold.length > 0) {
                addSolr(solrdoc, YaCySchema.bold_txt, bold);
                if (allAttr || contains(YaCySchema.bold_val)) {
                    addSolr(solrdoc, YaCySchema.bold_val, html.getBoldCount(bold));
                }
            }
            final String[] italic = html.getItalic();
            addSolr(solrdoc, YaCySchema.italiccount_i, italic.length);
            if (italic.length > 0) {
                addSolr(solrdoc, YaCySchema.italic_txt, italic);
                if (allAttr || contains(YaCySchema.italic_val)) {
                    addSolr(solrdoc, YaCySchema.italic_val, html.getItalicCount(italic));
                }
            }
            final String[] li = html.getLi();
            addSolr(solrdoc, YaCySchema.licount_i, li.length);
            if (li.length > 0) addSolr(solrdoc, YaCySchema.li_txt, li);

            // images
            final Collection<ImageEntry> imagesc = html.getImages().values();
            final List<String> imgtags  = new ArrayList<String>(imagesc.size());
            final List<String> imgprots = new ArrayList<String>(imagesc.size());
            final List<String> imgstubs = new ArrayList<String>(imagesc.size());
            final List<String> imgalts  = new ArrayList<String>(imagesc.size());
            for (final ImageEntry ie: imagesc) {
                final MultiProtocolURI uri = ie.url();
                inboundLinks.remove(uri);
                ouboundLinks.remove(uri);
                imgtags.add(ie.toString());
                String protocol = uri.getProtocol();
                imgprots.add(protocol);
                imgstubs.add(uri.toString().substring(protocol.length() + 3));
                imgalts.add(ie.alt());
            }
            if (allAttr || contains(YaCySchema.imagescount_i)) addSolr(solrdoc, YaCySchema.imagescount_i, imgtags.size());
            if (allAttr || contains(YaCySchema.images_tag_txt)) addSolr(solrdoc, YaCySchema.images_tag_txt, imgtags);
            if (allAttr || contains(YaCySchema.images_protocol_txt)) addSolr(solrdoc, YaCySchema.images_protocol_txt, protocolList2indexedList(imgprots));
            if (allAttr || contains(YaCySchema.images_urlstub_txt)) addSolr(solrdoc, YaCySchema.images_urlstub_txt, imgstubs);
            if (allAttr || contains(YaCySchema.images_alt_txt)) addSolr(solrdoc, YaCySchema.images_alt_txt, imgalts);

            // style sheets
            if (allAttr || contains(YaCySchema.css_tag_txt)) {
                final Map<MultiProtocolURI, String> csss = html.getCSS();
                final String[] css_tag = new String[csss.size()];
                final String[] css_url = new String[csss.size()];
                c = 0;
                for (final Map.Entry<MultiProtocolURI, String> entry: csss.entrySet()) {
                    final String url = entry.getKey().toNormalform(false, false);
                    inboundLinks.remove(url);
                    ouboundLinks.remove(url);
                    css_tag[c] =
                        "<link rel=\"stylesheet\" type=\"text/css\" media=\"" + entry.getValue() + "\"" +
                        " href=\""+ url + "\" />";
                    css_url[c] = url;
                    c++;
                }
                addSolr(solrdoc, YaCySchema.csscount_i, css_tag.length);
                if (css_tag.length > 0) addSolr(solrdoc, YaCySchema.css_tag_txt, css_tag);
                if (css_url.length > 0) addSolr(solrdoc, YaCySchema.css_url_txt, css_url);
            }

            // Scripts
            if (allAttr || contains(YaCySchema.scripts_txt)) {
                final Set<MultiProtocolURI> scriptss = html.getScript();
                final String[] scripts = new String[scriptss.size()];
                c = 0;
                for (final MultiProtocolURI url: scriptss) {
                    inboundLinks.remove(url);
                    ouboundLinks.remove(url);
                    scripts[c++] = url.toNormalform(false, false);
                }
                addSolr(solrdoc, YaCySchema.scriptscount_i, scripts.length);
                if (scripts.length > 0) addSolr(solrdoc, YaCySchema.scripts_txt, scripts);
            }

            // Frames
            if (allAttr || contains(YaCySchema.frames_txt)) {
                final Set<MultiProtocolURI> framess = html.getFrames();
                final String[] frames = new String[framess.size()];
                c = 0;
                for (final MultiProtocolURI url: framess) {
                    inboundLinks.remove(url);
                    ouboundLinks.remove(url);
                    frames[c++] = url.toNormalform(false, false);
                }
                addSolr(solrdoc, YaCySchema.framesscount_i, frames.length);
                if (frames.length > 0) addSolr(solrdoc, YaCySchema.frames_txt, frames);
            }

            // IFrames
            if (allAttr || contains(YaCySchema.iframes_txt)) {
                final Set<MultiProtocolURI> iframess = html.getIFrames();
                final String[] iframes = new String[iframess.size()];
                c = 0;
                for (final MultiProtocolURI url: iframess) {
                    inboundLinks.remove(url);
                    ouboundLinks.remove(url);
                    iframes[c++] = url.toNormalform(false, false);
                }
                addSolr(solrdoc, YaCySchema.iframesscount_i, iframes.length);
                if (iframes.length > 0) addSolr(solrdoc, YaCySchema.iframes_txt, iframes);
            }

            // canonical tag
            if (allAttr || contains(YaCySchema.canonical_s)) {
                final MultiProtocolURI canonical = html.getCanonical();
                if (canonical != null) {
                    inboundLinks.remove(canonical);
                    ouboundLinks.remove(canonical);
                    addSolr(solrdoc, YaCySchema.canonical_s, canonical.toNormalform(false, false));
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
                            ouboundLinks.remove(refreshURL);
                            addSolr(solrdoc, YaCySchema.refresh_s, refreshURL.toNormalform(false, false));
                        }
                    } catch (MalformedURLException e) {
                        addSolr(solrdoc, YaCySchema.refresh_s, refresh);
                    }
                }
            }

            // flash embedded
            if (allAttr || contains(YaCySchema.flash_b)) {
                MultiProtocolURI[] flashURLs = html.getFlash();
                for (MultiProtocolURI u: flashURLs) {
                    // remove all flash links from ibound/outbound links
                    inboundLinks.remove(u);
                    ouboundLinks.remove(u);
                }
                addSolr(solrdoc, YaCySchema.flash_b, flashURLs.length > 0);
            }

            // generic evaluation pattern
            for (final String model: html.getEvaluationModelNames()) {
                if (allAttr || contains("ext_" + model + "_txt")) {
                    final String[] scorenames = html.getEvaluationModelScoreNames(model);
                    if (scorenames.length > 0) {
                        addSolr(solrdoc, YaCySchema.valueOf("ext_" + model + "_txt"), scorenames);
                        addSolr(solrdoc, YaCySchema.valueOf("ext_" + model + "_val"), html.getEvaluationModelScoreCounts(model, scorenames));
                    }
                }
            }

            // response time
            addSolr(solrdoc, YaCySchema.responsetime_i, header == null ? 0 : Integer.parseInt(header.get(HeaderFramework.RESPONSE_TIME_MILLIS, "0")));
        }

        // list all links
        final Map<MultiProtocolURI, Properties> alllinks = yacydoc.getAnchors();
        c = 0;
        if (allAttr || contains(YaCySchema.inboundlinkscount_i)) addSolr(solrdoc, YaCySchema.inboundlinkscount_i, inboundLinks.size());
        if (allAttr || contains(YaCySchema.inboundlinksnofollowcount_i)) addSolr(solrdoc, YaCySchema.inboundlinksnofollowcount_i, yacydoc.inboundLinkNofollowCount());
        final List<String> inboundlinksTag = new ArrayList<String>(inboundLinks.size());
        final List<String> inboundlinksURLProtocol = new ArrayList<String>(inboundLinks.size());
        final List<String> inboundlinksURLStub = new ArrayList<String>(inboundLinks.size());
        final List<String> inboundlinksName = new ArrayList<String>(inboundLinks.size());
        final List<String> inboundlinksRel = new ArrayList<String>(inboundLinks.size());
        final List<String> inboundlinksText = new ArrayList<String>(inboundLinks.size());
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
            inboundlinksTag.add(
                "<a href=\"" + url.toNormalform(false, false) + "\"" +
                (rel.length() > 0 ? " rel=\"" + rel + "\"" : "") +
                (name.length() > 0 ? " name=\"" + name + "\"" : "") +
                ">" +
                ((text.length() > 0) ? text : "") + "</a>");
            c++;
        }
        if (allAttr || contains(YaCySchema.inboundlinks_tag_txt)) addSolr(solrdoc, YaCySchema.inboundlinks_tag_txt, inboundlinksTag);
        if (allAttr || contains(YaCySchema.inboundlinks_protocol_txt)) addSolr(solrdoc, YaCySchema.inboundlinks_protocol_txt, protocolList2indexedList(inboundlinksURLProtocol));
        if (allAttr || contains(YaCySchema.inboundlinks_urlstub_txt)) addSolr(solrdoc, YaCySchema.inboundlinks_urlstub_txt, inboundlinksURLStub);
        if (allAttr || contains(YaCySchema.inboundlinks_name_txt)) addSolr(solrdoc, YaCySchema.inboundlinks_name_txt, inboundlinksName);
        if (allAttr || contains(YaCySchema.inboundlinks_rel_txt)) addSolr(solrdoc, YaCySchema.inboundlinks_rel_txt, inboundlinksRel);
        if (allAttr || contains(YaCySchema.inboundlinks_relflags_txt)) addSolr(solrdoc, YaCySchema.inboundlinks_relflags_txt, relEval(inboundlinksRel));
        if (allAttr || contains(YaCySchema.inboundlinks_text_txt)) addSolr(solrdoc, YaCySchema.inboundlinks_text_txt, inboundlinksText);

        c = 0;
        if (allAttr || contains(YaCySchema.outboundlinkscount_i)) addSolr(solrdoc, YaCySchema.outboundlinkscount_i, ouboundLinks.size());
        if (allAttr || contains(YaCySchema.outboundlinksnofollowcount_i)) addSolr(solrdoc, YaCySchema.outboundlinksnofollowcount_i, yacydoc.outboundLinkNofollowCount());
        final List<String> outboundlinksTag = new ArrayList<String>(ouboundLinks.size());
        final List<String> outboundlinksURLProtocol = new ArrayList<String>(ouboundLinks.size());
        final List<String> outboundlinksURLStub = new ArrayList<String>(ouboundLinks.size());
        final List<String> outboundlinksName = new ArrayList<String>(ouboundLinks.size());
        final List<String> outboundlinksRel = new ArrayList<String>(ouboundLinks.size());
        final List<String> outboundlinksText = new ArrayList<String>(ouboundLinks.size());
        for (final MultiProtocolURI url: ouboundLinks) {
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
            outboundlinksTag.add(
                "<a href=\"" + url.toNormalform(false, false) + "\"" +
                (rel.length() > 0 ? " rel=\"" + rel + "\"" : "") +
                (name.length() > 0 ? " name=\"" + name + "\"" : "") +
                ">" +
                ((text.length() > 0) ? text : "") + "</a>");
            c++;
        }
        if (allAttr || contains(YaCySchema.outboundlinks_tag_txt)) addSolr(solrdoc, YaCySchema.outboundlinks_tag_txt, outboundlinksTag);
        if (allAttr || contains(YaCySchema.outboundlinks_protocol_txt)) addSolr(solrdoc, YaCySchema.outboundlinks_protocol_txt, protocolList2indexedList(outboundlinksURLProtocol));
        if (allAttr || contains(YaCySchema.outboundlinks_urlstub_txt)) addSolr(solrdoc, YaCySchema.outboundlinks_urlstub_txt, outboundlinksURLStub);
        if (allAttr || contains(YaCySchema.outboundlinks_name_txt)) addSolr(solrdoc, YaCySchema.outboundlinks_name_txt, outboundlinksName);
        if (allAttr || contains(YaCySchema.outboundlinks_rel_txt)) addSolr(solrdoc, YaCySchema.outboundlinks_rel_txt, outboundlinksRel);
        if (allAttr || contains(YaCySchema.outboundlinks_relflags_txt)) addSolr(solrdoc, YaCySchema.outboundlinks_relflags_txt, relEval(inboundlinksRel));
        if (allAttr || contains(YaCySchema.outboundlinks_text_txt)) addSolr(solrdoc, YaCySchema.outboundlinks_text_txt, outboundlinksText);

        // charset
        if (allAttr || contains(YaCySchema.charset_s)) addSolr(solrdoc, YaCySchema.charset_s, yacydoc.getCharset());

        // coordinates
        if (yacydoc.lat() != 0.0f && yacydoc.lon() != 0.0f) {
            if (allAttr || contains(YaCySchema.lat_coordinate)) addSolr(solrdoc, YaCySchema.lat_coordinate, yacydoc.lat());
        	if (allAttr || contains(YaCySchema.lon_coordinate)) addSolr(solrdoc, YaCySchema.lon_coordinate, yacydoc.lon());
        }
        if (allAttr || contains(YaCySchema.httpstatus_i)) addSolr(solrdoc, YaCySchema.httpstatus_i, header == null ? 200 : header.getStatusCode());

        // fields that are additionally in URIMetadataRow
        if (allAttr || contains(YaCySchema.load_date_dt)) addSolr(solrdoc, YaCySchema.load_date_dt, metadata.loaddate());
        if (allAttr || contains(YaCySchema.fresh_date_dt)) addSolr(solrdoc, YaCySchema.fresh_date_dt, metadata.freshdate());
        if (allAttr || contains(YaCySchema.host_id_s)) addSolr(solrdoc, YaCySchema.host_id_s, metadata.hosthash());
        if ((allAttr || contains(YaCySchema.referrer_id_txt)) && metadata.referrerHash() != null) addSolr(solrdoc, YaCySchema.referrer_id_txt, new String[]{ASCII.String(metadata.referrerHash())});
        //if (allAttr || contains(SolrField.md5_s)) addSolr(solrdoc, SolrField.md5_s, new byte[0]);
        if (allAttr || contains(YaCySchema.publisher_t)) addSolr(solrdoc, YaCySchema.publisher_t, yacydoc.dc_publisher());
        if ((allAttr || contains(YaCySchema.language_txt)) && metadata.language() != null) addSolr(solrdoc, YaCySchema.language_txt,new String[]{UTF8.String(metadata.language())});
        if (allAttr || contains(YaCySchema.size_i)) addSolr(solrdoc, YaCySchema.size_i, metadata.size());
        if (allAttr || contains(YaCySchema.audiolinkscount_i)) addSolr(solrdoc, YaCySchema.audiolinkscount_i, yacydoc.getAudiolinks().size());
        if (allAttr || contains(YaCySchema.videolinkscount_i)) addSolr(solrdoc, YaCySchema.videolinkscount_i, yacydoc.getVideolinks().size());
        if (allAttr || contains(YaCySchema.applinkscount_i)) addSolr(solrdoc, YaCySchema.applinkscount_i, yacydoc.getApplinks().size());

        return solrdoc;
    }

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
    private static int relEval(final List<String> rel) {
        int i = 0;
        for (final String s: rel) {
            final String s0 = s.toLowerCase().trim();
            if ("me".equals(s0)) i += 1;
            if ("nofollow".equals(s0)) i += 2;
        }
        return i;
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
        return (Date) solr.getFieldValue(YaCySchema.last_modified.getSolrFieldName());
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
    public SolrDoc err(final DigestURI digestURI, final String failReason, final int httpstatus) throws IOException {
        final SolrDoc solrdoc = new SolrDoc();
        addSolr(solrdoc, YaCySchema.id, ASCII.String(digestURI.hash()));
        addSolr(solrdoc, YaCySchema.sku, digestURI.toNormalform(true, false));
        final InetAddress address = digestURI.getInetAddress();
        if (address != null) addSolr(solrdoc, YaCySchema.ip_s, address.getHostAddress());
        if (digestURI.getHost() != null) addSolr(solrdoc, YaCySchema.host_s, digestURI.getHost());

        // path elements of link
        final String path = digestURI.getPath();
        if (path != null) {
            final String[] paths = path.split("/");
            if (paths.length > 0) addSolr(solrdoc, YaCySchema.paths_txt, paths);
        }
        addSolr(solrdoc, YaCySchema.failreason_t, failReason);
        addSolr(solrdoc, YaCySchema.httpstatus_i, httpstatus);
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
