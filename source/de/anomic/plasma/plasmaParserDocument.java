//plasmaParserDocument.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//last major change: 24.04.2005
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.plasma;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterImageEntry;
import de.anomic.plasma.parser.Parser;
import de.anomic.server.serverCachedFileOutputStream;
import de.anomic.server.serverFileUtils;
import de.anomic.yacy.yacyURL;

public class plasmaParserDocument {
    
    private final yacyURL source;               // the source url
    private final String mimeType;              // mimeType as taken from http header
    private final String charset;               // the charset of the document
    private final List<String> keywords;        // most resources provide a keyword field
    private final StringBuffer title;           // a document title, taken from title or h1 tag; shall appear as headline of search result
    private final StringBuffer creator;         // author or copyright
    private final List<String> sections;        // if present: more titles/headlines appearing in the document
    private final StringBuffer description;     // an abstract, if present: short content description
    private Object text;                  // the clear text, all that is visible
    private final Map<yacyURL, String> anchors; // all links embedded as clickeable entities (anchor tags)
    private final HashMap<String, htmlFilterImageEntry> images; // all visible pictures in document
    // the anchors and images - Maps are URL-to-EntityDescription mappings.
    // The EntityDescription appear either as visible text in anchors or as alternative
    // text in image tags.
    private Map<yacyURL, String> hyperlinks, audiolinks, videolinks, applinks;
    private Map<String, String> emaillinks;
    private yacyURL favicon;
    private boolean resorted;
    private InputStream textStream;
    private int inboundLinks, outboundLinks; // counters for inbound and outbound links, are counted after calling notifyWebStructure
    
    protected plasmaParserDocument(final yacyURL location, final String mimeType, final String charset,
                    final String[] keywords, final String title, final String author,
                    final String[] sections, final String abstrct,
                    final Object text, final Map<yacyURL, String> anchors, final HashMap<String, htmlFilterImageEntry> images) {
        this.source = location;
        this.mimeType = (mimeType == null) ? "application/octet-stream" : mimeType;
        this.charset = charset;
        this.keywords = (keywords == null) ? new LinkedList<String>() : Arrays.asList(keywords);
        this.title = (title == null) ? new StringBuffer() : new StringBuffer(title);
        this.creator = (author == null) ? new StringBuffer() : new StringBuffer(author);
        this.sections = (sections == null) ? new LinkedList<String>() : Arrays.asList(sections);
        this.description = (abstrct == null) ? new StringBuffer() : new StringBuffer(abstrct);
        this.anchors = (anchors == null) ? new HashMap<yacyURL, String>(0) : anchors;
        this.images =  (images == null) ? new HashMap<String, htmlFilterImageEntry>() : images;
        this.hyperlinks = null;
        this.audiolinks = null;
        this.videolinks = null;
        this.applinks = null;
        this.emaillinks = null;
        this.resorted = false;
        this.inboundLinks = -1;
        this.outboundLinks = -1;
        
        if (text == null) try {
            this.text = new serverCachedFileOutputStream(Parser.MAX_KEEP_IN_MEMORY_SIZE);
        } catch (final IOException e) {
            e.printStackTrace();
            this.text = new StringBuffer();
        } else {
            this.text = text;
        }
    }
    
    public plasmaParserDocument(final yacyURL location, final String mimeType, final String charset) {
        this(location, mimeType, charset, null, null, null, null, null, (Object)null, null, null);
    }
    
    public plasmaParserDocument(final yacyURL location, final String mimeType, final String charset,
                    final String[] keywords, final String title, final String author,
                    final String[] sections, final String abstrct,
                    final byte[] text, final Map<yacyURL, String> anchors, final HashMap<String, htmlFilterImageEntry> images) {
        this(location, mimeType, charset, keywords, title, author, sections, abstrct, (Object)text, anchors, images);
    }
    
    public plasmaParserDocument(final yacyURL location, final String mimeType, final String charset,
            final String[] keywords, final String title, final String author,
            final String[] sections, final String abstrct,
            final File text, final Map<yacyURL, String> anchors, final HashMap<String, htmlFilterImageEntry> images) {
        this(location, mimeType, charset, keywords, title, author, sections, abstrct, (Object)text, anchors, images);
    }
    
    public plasmaParserDocument(final yacyURL location, final String mimeType, final String charset,
            final String[] keywords, final String title, final String author,
            final String[] sections, final String abstrct,
            final serverCachedFileOutputStream text, final Map<yacyURL, String> anchors, final HashMap<String, htmlFilterImageEntry> images) {
        this(location, mimeType, charset, keywords, title, author, sections, abstrct, (Object)text, anchors, images);
    }

    /*
DC according to rfc 5013

* dc_title
* dc_creator
* dc_subject
* dc_description
* dc_publisher
dc_contributor
dc_date
dc_type
* dc_format
* dc_identifier
* dc_source
dc_language
dc_relation
dc_coverage
dc_rights
     */
    
    public String dc_title() {
        return title.toString();
    }

    public String dc_creator() {
        if (creator == null)
            return "";
        return creator.toString();
    }
    
    public String dc_subject(final char separator) {
        // sort out doubles and empty words
        final TreeSet<String> hs = new TreeSet<String>();
        String s;
        for (int i = 0; i < this.keywords.size(); i++) {
            if (this.keywords.get(i) == null) continue;
            s = (this.keywords.get(i)).trim();
            if (s.length() > 0) hs.add(s.toLowerCase());
        }
        if (hs.size() == 0) return "";
        // generate a new list
        final StringBuffer sb = new StringBuffer(this.keywords.size() * 6);
        final Iterator<String> i = hs.iterator();
        while (i.hasNext()) sb.append(i.next()).append(separator);
        return sb.substring(0, sb.length() - 1);
    }
    
    public String dc_description() {
        if (description == null)
            return dc_title();
        return description.toString();
    }
    
    public String dc_publisher() {
        // if we don't have a publisher, simply return the host/domain name
        return this.source.getHost();
    }
    
    public String dc_format() {
        return this.mimeType;
    }
    
    public String dc_identifier() {
        return "yacy.net:" + this.source.hash();
    }
    
    public yacyURL dc_source() {
        return this.source;
    }
    
    /**
     * @return the supposed charset of this document or <code>null</code> if unknown
     */
    public String getCharset() {
        return this.charset;
    }
    
    public String[] getSectionTitles() {
        if (sections == null) {
            return new String[] { dc_title() };
        }
        return sections.toArray(new String[this.sections.size()]);
    }

    public InputStream getText() {
        try {
            if (this.text == null) return null;

            if (this.text instanceof File) {
                this.textStream = new BufferedInputStream(new FileInputStream((File)this.text));
            } else if (this.text instanceof byte[]) {
                this.textStream =  new ByteArrayInputStream((byte[])this.text);
            } else if (this.text instanceof serverCachedFileOutputStream) {
                return ((serverCachedFileOutputStream)this.text).getContent();
            }
            return this.textStream;
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return null; 
    }
    
    public byte[] getTextBytes() {
        try {
            if (this.text == null) return new byte[0];

            if (this.text instanceof File) {
                return serverFileUtils.read((File)this.text);
            } else if (this.text instanceof byte[]) {
                return (byte[])this.text;
            } else if (this.text instanceof serverCachedFileOutputStream) {
                final serverCachedFileOutputStream ffbaos = (serverCachedFileOutputStream)this.text;
                if (ffbaos.isFallback()) {
                    return serverFileUtils.read(ffbaos.getContent());
                }
                return ffbaos.getContentBAOS();
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return new byte[0];             
    }
    
    public long getTextLength() {
        if (this.text == null) return 0;
        if (this.text instanceof File) return ((File)this.text).length();
        else if (this.text instanceof byte[]) return ((byte[])this.text).length;
        else if (this.text instanceof serverCachedFileOutputStream) {
            return ((serverCachedFileOutputStream)this.text).getLength();
        }
        
        return -1; 
    }
    
    public Iterator<StringBuffer> getSentences(final boolean pre) {
        if (this.text == null) return null;
        final plasmaCondenser.sentencesFromInputStreamEnum e = plasmaCondenser.sentencesFromInputStream(getText(), this.charset);
        e.pre(pre);
        return e;
    }
    
    public List<String> getKeywords() {
        return this.keywords;
    }
    
    public Map<yacyURL, String> getAnchors() {
        // returns all links embedded as anchors (clickeable entities)
        // this is a url(String)/text(String) map
        return anchors;
    }
    
    
    // the next three methods provide a calculated view on the getAnchors/getImages:
    
    public Map<yacyURL, String> getHyperlinks() {
        // this is a subset of the getAnchor-set: only links to other hyperrefs
        if (!resorted) resortLinks();
        return hyperlinks;
    }
    
    public Map<yacyURL, String> getAudiolinks() {
        if (!resorted) resortLinks();
        return this.audiolinks;
    }
    
    public Map<yacyURL, String> getVideolinks() {
        if (!resorted) resortLinks();
        return this.videolinks;
    }
    
    public HashMap<String, htmlFilterImageEntry> getImages() {
        // returns all links enbedded as pictures (visible in document)
        // this resturns a htmlFilterImageEntry collection
        if (!resorted) resortLinks();
        return images;
    }
    
    public Map<yacyURL, String> getApplinks() {
        if (!resorted) resortLinks();
        return this.applinks;
    }
    
    public Map<String, String> getEmaillinks() {
        // this is part of the getAnchor-set: only links to email addresses
        if (!resorted) resortLinks();
        return emaillinks;
    }
    
    private synchronized void resortLinks() {
        
        // extract hyperlinks, medialinks and emaillinks from anchorlinks
        yacyURL url;
        String u;
        int extpos, qpos;
        String ext = null;
        final Iterator<Map.Entry<yacyURL, String>> i = anchors.entrySet().iterator();
        hyperlinks = new HashMap<yacyURL, String>();
        videolinks = new HashMap<yacyURL, String>();
        audiolinks = new HashMap<yacyURL, String>();
        applinks   = new HashMap<yacyURL, String>();
        emaillinks = new HashMap<String, String>();
        final HashMap<String, htmlFilterImageEntry> collectedImages = new HashMap<String, htmlFilterImageEntry>(); // this is a set that is collected now and joined later to the imagelinks
        Map.Entry<yacyURL, String> entry;
        while (i.hasNext()) {
            entry = i.next();
            url = entry.getKey();
            if (url == null) continue;
            u = url.toNormalform(true, false);
            if (u.startsWith("mailto:")) {
                emaillinks.put(u.substring(7), entry.getValue());
            } else {
                extpos = u.lastIndexOf(".");
                if (extpos > 0) {
                    if (((qpos = u.indexOf("?")) >= 0) && (qpos > extpos)) {
                        ext = u.substring(extpos + 1, qpos).toLowerCase();
                    } else {
                        ext = u.substring(extpos + 1).toLowerCase();
                    }
                    if (plasmaParser.mediaExtContains(ext)) {
                        // this is not a normal anchor, its a media link
                        if (plasmaParser.imageExtContains(ext)) {
                            htmlFilterContentScraper.addImage(collectedImages, new htmlFilterImageEntry(url, entry.getValue(), -1, -1));
                        }
                        else if (plasmaParser.audioExtContains(ext)) audiolinks.put(url, entry.getValue());
                        else if (plasmaParser.videoExtContains(ext)) videolinks.put(url, entry.getValue());
                        else if (plasmaParser.appsExtContains(ext)) applinks.put(url, entry.getValue());
                    } else {
                        hyperlinks.put(url, entry.getValue());
                    }
                } else {
                    // a path to a directory
                    hyperlinks.put(url, entry.getValue());
                }
            }
        }
        
        // add image links that we collected from the anchors to the image map
        htmlFilterContentScraper.addAllImages(images, collectedImages);
       
        // expand the hyperlinks:
        // we add artificial hyperlinks to the hyperlink set
        // that can be calculated from given hyperlinks and imagelinks
        
        hyperlinks.putAll(plasmaParser.allReflinks(images.values()));
        hyperlinks.putAll(plasmaParser.allReflinks(audiolinks.keySet()));
        hyperlinks.putAll(plasmaParser.allReflinks(videolinks.keySet()));
        hyperlinks.putAll(plasmaParser.allReflinks(applinks.keySet()));
        hyperlinks.putAll(plasmaParser.allSubpaths(hyperlinks.keySet()));
        hyperlinks.putAll(plasmaParser.allSubpaths(images.values()));
        hyperlinks.putAll(plasmaParser.allSubpaths(audiolinks.keySet()));
        hyperlinks.putAll(plasmaParser.allSubpaths(videolinks.keySet()));
        hyperlinks.putAll(plasmaParser.allSubpaths(applinks.keySet()));
        
        // don't do this again
        this.resorted = true;
    }
    
    public void addSubDocument(final plasmaParserDocument doc) throws IOException {
        this.sections.addAll(Arrays.asList(doc.getSectionTitles()));
        
        if (this.title.length() > 0) this.title.append('\n');
        this.title.append(doc.dc_title());
        
        this.keywords.addAll(doc.getKeywords());
        
        if (this.description.length() > 0) this.description.append('\n');
        this.description.append(doc.dc_description());
        
        if (!(this.text instanceof serverCachedFileOutputStream)) {
            this.text = new serverCachedFileOutputStream(Parser.MAX_KEEP_IN_MEMORY_SIZE);
            serverFileUtils.copy(getText(), (serverCachedFileOutputStream)this.text);
        }
        serverFileUtils.copy(doc.getText(), (serverCachedFileOutputStream)this.text);
        
        anchors.putAll(doc.getAnchors());
        htmlFilterContentScraper.addAllImages(images, doc.getImages());
    }
    
    /**
     * @return the {@link URL} to the favicon that belongs to the document
     */
    public yacyURL getFavicon() {
    	return this.favicon;
    }
    
    /**
     * @param faviconURL the {@link URL} to the favicon that belongs to the document
     */
    public void setFavicon(final yacyURL faviconURL) {
    	this.favicon = faviconURL;
    }
    
    public void notifyWebStructure(final plasmaWebStructure webStructure, final plasmaCondenser condenser, final Date docDate) {
        final Integer[] ioLinks = webStructure.generateCitationReference(this, condenser, docDate); // [outlinksSame, outlinksOther]
        this.inboundLinks = ioLinks[0].intValue();
        this.outboundLinks = ioLinks[1].intValue();
    }
    
    public int inboundLinks() {
        assert this.inboundLinks >= 0;
        return (this.inboundLinks < 0) ? 0 : this.inboundLinks;
    }
    
    public int outboundLinks() {
        assert this.outboundLinks >= 0;
        return (this.outboundLinks < 0) ? 0 : this.outboundLinks;
    }
    
    public void close() {
        // try close the output stream
        if (this.textStream != null) {
            try {
                this.textStream.close();
            } catch (final Exception e) { 
                /* ignore this */
            } finally {
                this.textStream = null;
            }
        }
        
        // delete the temp file
        if ((this.text != null) && (this.text instanceof File)) {
            try { 
                ((File)this.text).delete(); 
            } catch (final Exception e) {
                /* ignore this */
            } finally {
                this.text = null;
            }
        }        
    }
    
    protected void finalize() throws Throwable {
        this.close();
        super.finalize();
    }
    
}
