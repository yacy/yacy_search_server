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

package de.anomic.document;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.yacy.kelondro.util.DateFormatter;
import net.yacy.kelondro.util.FileUtils;

import de.anomic.document.parser.html.ContentScraper;
import de.anomic.document.parser.html.ImageEntry;
import de.anomic.http.client.Cache;
import de.anomic.http.client.Client;
import de.anomic.http.metadata.ResponseHeader;
import de.anomic.server.serverCachedFileOutputStream;
import de.anomic.yacy.yacyURL;
import de.anomic.ymage.WebStructureGraph;

public class Document {
    
    private final yacyURL source;               // the source url
    private final String mimeType;              // mimeType as taken from http header
    private final String charset;               // the charset of the document
    private final List<String> keywords;        // most resources provide a keyword field
    private       StringBuilder title;          // a document title, taken from title or h1 tag; shall appear as headline of search result
    private final StringBuilder creator;        // author or copyright
    private final List<String>  sections;       // if present: more titles/headlines appearing in the document
    private final StringBuilder description;    // an abstract, if present: short content description
    private Object text;                  // the clear text, all that is visible
    private final Map<yacyURL, String> anchors; // all links embedded as clickeable entities (anchor tags)
    private final HashMap<String, ImageEntry> images; // all visible pictures in document
    // the anchors and images - Maps are URL-to-EntityDescription mappings.
    // The EntityDescription appear either as visible text in anchors or as alternative
    // text in image tags.
    private Map<yacyURL, String> hyperlinks, audiolinks, videolinks, applinks;
    private Map<String, String> emaillinks;
    private yacyURL favicon;
    private boolean resorted;
    private InputStream textStream;
    private int inboundLinks, outboundLinks; // counters for inbound and outbound links, are counted after calling notifyWebStructure
    private Set<String> languages;
    
    protected Document(final yacyURL location, final String mimeType, final String charset, final Set<String> languages,
                    final String[] keywords, final String title, final String author,
                    final String[] sections, final String abstrct,
                    final Object text, final Map<yacyURL, String> anchors, final HashMap<String, ImageEntry> images) {
        this.source = location;
        this.mimeType = (mimeType == null) ? "application/octet-stream" : mimeType;
        this.charset = charset;
        this.keywords = (keywords == null) ? new LinkedList<String>() : Arrays.asList(keywords);
        this.title = (title == null) ? new StringBuilder(0) : new StringBuilder(title);
        this.creator = (author == null) ? new StringBuilder(0) : new StringBuilder(author);
        this.sections = (sections == null) ? new LinkedList<String>() : Arrays.asList(sections);
        this.description = (abstrct == null) ? new StringBuilder(0) : new StringBuilder(abstrct);
        this.anchors = (anchors == null) ? new HashMap<yacyURL, String>(0) : anchors;
        this.images =  (images == null) ? new HashMap<String, ImageEntry>() : images;
        this.hyperlinks = null;
        this.audiolinks = null;
        this.videolinks = null;
        this.applinks = null;
        this.emaillinks = null;
        this.resorted = false;
        this.inboundLinks = -1;
        this.outboundLinks = -1;
        this.languages = languages;
        
        if (text == null) try {
            this.text = new serverCachedFileOutputStream(Idiom.MAX_KEEP_IN_MEMORY_SIZE);
        } catch (final IOException e) {
            e.printStackTrace();
            this.text = new StringBuilder();
        } else {
            this.text = text;
        }
    }
    
    public Document(final yacyURL location, final String mimeType, final String charset, final Set<String> languages) {
        this(location, mimeType, charset, languages, null, null, null, null, null, (Object)null, null, null);
    }
    
    public Document(final yacyURL location, final String mimeType, final String charset, final Set<String> languages,
                    final String[] keywords, final String title, final String author,
                    final String[] sections, final String abstrct,
                    final byte[] text, final Map<yacyURL, String> anchors, final HashMap<String, ImageEntry> images) {
        this(location, mimeType, charset, languages, keywords, title, author, sections, abstrct, (Object)text, anchors, images);
    }
    
    public Document(final yacyURL location, final String mimeType, final String charset, final Set<String> languages,
            final String[] keywords, final String title, final String author,
            final String[] sections, final String abstrct,
            final File text, final Map<yacyURL, String> anchors, final HashMap<String, ImageEntry> images) {
        this(location, mimeType, charset, languages, keywords, title, author, sections, abstrct, (Object)text, anchors, images);
    }
    
    public Document(final yacyURL location, final String mimeType, final String charset, final Set<String> languages,
            final String[] keywords, final String title, final String author,
            final String[] sections, final String abstrct,
            final serverCachedFileOutputStream text, final Map<yacyURL, String> anchors, final HashMap<String, ImageEntry> images) {
        this(location, mimeType, charset, languages, keywords, title, author, sections, abstrct, (Object)text, anchors, images);
    }

    /**
     * compute a set of languages that this document contains
     * the language is not computed using a statistical analysis of the content, only from given metadata that came with the document
     * if there are several languages defined in the document, the TLD is taken to check which one should be picked
     * If there is no metadata at all, null is returned
     * @return a string with a language name using the alpha-2 code of ISO 639
     */
    public String dc_language() {
        if (this.languages == null) return null;
        if (this.languages.size() == 0) return null;
        if (this.languages.size() == 1) return languages.iterator().next();
        if (this.languages.contains(this.source.language())) return this.source.language();
        // now we are confused: the declared languages differ all from the TLD
        // just pick one of the languages that we have
        return languages.iterator().next();
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

    public void setTitle(String title) {
        this.title = new StringBuilder(title);
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
        final StringBuilder sb = new StringBuilder(this.keywords.size() * 6);
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
        return this.source.toNormalform(true, false);
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
                return FileUtils.read((File)this.text);
            } else if (this.text instanceof byte[]) {
                return (byte[])this.text;
            } else if (this.text instanceof serverCachedFileOutputStream) {
                final serverCachedFileOutputStream ffbaos = (serverCachedFileOutputStream)this.text;
                if (ffbaos.isFallback()) {
                    return FileUtils.read(ffbaos.getContent());
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
    
    public Iterator<StringBuilder> getSentences(final boolean pre) {
        if (this.text == null) return null;
        final Condenser.sentencesFromInputStreamEnum e = Condenser.sentencesFromInputStream(getText());
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
    
    public HashMap<String, ImageEntry> getImages() {
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
        if (this.resorted) return;
        
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
        final HashMap<String, ImageEntry> collectedImages = new HashMap<String, ImageEntry>(); // this is a set that is collected now and joined later to the imagelinks
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
                    if (Classification.isMediaExtension(ext)) {
                        // this is not a normal anchor, its a media link
                        if (Classification.isImageExtension(ext)) {
                            ContentScraper.addImage(collectedImages, new ImageEntry(url, entry.getValue(), -1, -1));
                        }
                        else if (Classification.isAudioExtension(ext)) audiolinks.put(url, entry.getValue());
                        else if (Classification.isVideoExtension(ext)) videolinks.put(url, entry.getValue());
                        else if (Classification.isApplicationExtension(ext)) applinks.put(url, entry.getValue());
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
        ContentScraper.addAllImages(images, collectedImages);
       
        // expand the hyperlinks:
        // we add artificial hyperlinks to the hyperlink set
        // that can be calculated from given hyperlinks and imagelinks
        
        hyperlinks.putAll(allReflinks(images.values()));
        hyperlinks.putAll(allReflinks(audiolinks.keySet()));
        hyperlinks.putAll(allReflinks(videolinks.keySet()));
        hyperlinks.putAll(allReflinks(applinks.keySet()));
        /*
        hyperlinks.putAll(allSubpaths(hyperlinks.keySet()));
        hyperlinks.putAll(allSubpaths(images.values()));
        hyperlinks.putAll(allSubpaths(audiolinks.keySet()));
        hyperlinks.putAll(allSubpaths(videolinks.keySet()));
        hyperlinks.putAll(allSubpaths(applinks.keySet()));
         */        
        // don't do this again
        this.resorted = true;
    }
    
    public static Map<yacyURL, String> allSubpaths(final Collection<?> links) {
        // links is either a Set of Strings (urls) or a Set of
        // htmlFilterImageEntries
        final HashSet<String> h = new HashSet<String>();
        Iterator<?> i = links.iterator();
        Object o;
        yacyURL url;
        String u;
        int pos;
        int l;
        while (i.hasNext())
            try {
                o = i.next();
                if (o instanceof yacyURL) url = (yacyURL) o;
                else if (o instanceof String) url = new yacyURL((String) o, null);
                else if (o instanceof ImageEntry) url = ((ImageEntry) o).url();
                else {
                    assert false;
                    continue;
                }
                u = url.toNormalform(true, true);
                if (u.endsWith("/"))
                    u = u.substring(0, u.length() - 1);
                pos = u.lastIndexOf('/');
                while (pos > 8) {
                    l = u.length();
                    u = u.substring(0, pos + 1);
                    h.add(u);
                    u = u.substring(0, pos);
                    assert (u.length() < l) : "u = " + u;
                    pos = u.lastIndexOf('/');
                }
            } catch (final MalformedURLException e) { }
        // now convert the strings to yacyURLs
        i = h.iterator();
        final HashMap<yacyURL, String> v = new HashMap<yacyURL, String>();
        while (i.hasNext()) {
            u = (String) i.next();
            try {
                url = new yacyURL(u, null);
                v.put(url, "sub");
            } catch (final MalformedURLException e) {
            }
        }
        return v;
    }
    
    public static Map<yacyURL, String> allReflinks(final Collection<?> links) {
        // links is either a Set of Strings (with urls) or
        // htmlFilterImageEntries
        // we find all links that are part of a reference inside a url
        final HashMap<yacyURL, String> v = new HashMap<yacyURL, String>();
        final Iterator<?> i = links.iterator();
        Object o;
        yacyURL url;
        String u;
        int pos;
        loop: while (i.hasNext())
            try {
                o = i.next();
                if (o instanceof yacyURL)
                    url = (yacyURL) o;
                else if (o instanceof String)
                    url = new yacyURL((String) o, null);
                else if (o instanceof ImageEntry)
                    url = ((ImageEntry) o).url();
                else {
                    assert false;
                    continue;
                }
                u = url.toNormalform(true, true);
                if ((pos = u.toLowerCase().indexOf("http://", 7)) > 0) {
                    i.remove();
                    u = u.substring(pos);
                    while ((pos = u.toLowerCase().indexOf("http://", 7)) > 0)
                        u = u.substring(pos);
                    url = new yacyURL(u, null);
                    if (!(v.containsKey(url)))
                        v.put(url, "ref");
                    continue loop;
                }
                if ((pos = u.toLowerCase().indexOf("/www.", 7)) > 0) {
                    i.remove();
                    u = "http:/" + u.substring(pos);
                    while ((pos = u.toLowerCase().indexOf("/www.", 7)) > 0)
                        u = "http:/" + u.substring(pos);
                    url = new yacyURL(u, null);
                    if (!(v.containsKey(url)))
                        v.put(url, "ref");
                    continue loop;
                }
            } catch (final MalformedURLException e) {
            }
        return v;
    }
    
    public void addSubDocument(final Document doc) throws IOException {
        this.sections.addAll(Arrays.asList(doc.getSectionTitles()));
        
        if (this.title.length() > 0) this.title.append('\n');
        this.title.append(doc.dc_title());
        
        this.keywords.addAll(doc.getKeywords());
        
        if (this.description.length() > 0) this.description.append('\n');
        this.description.append(doc.dc_description());
        
        if (!(this.text instanceof serverCachedFileOutputStream)) {
            this.text = new serverCachedFileOutputStream(Idiom.MAX_KEEP_IN_MEMORY_SIZE);
            FileUtils.copy(getText(), (serverCachedFileOutputStream)this.text);
        }
        FileUtils.copy(doc.getText(), (serverCachedFileOutputStream)this.text);
        
        anchors.putAll(doc.getAnchors());
        ContentScraper.addAllImages(images, doc.getImages());
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
    
    public void notifyWebStructure(final WebStructureGraph webStructure, final Condenser condenser, final Date docDate) {
        final Integer[] ioLinks = webStructure.generateCitationReference(this, condenser, docDate); // [outlinksSame, outlinksOther]
        this.inboundLinks = ioLinks[0].intValue();
        this.outboundLinks = ioLinks[1].intValue();
    }
    
    public int inboundLinks() {
        return (this.inboundLinks < 0) ? 0 : this.inboundLinks;
    }
    
    public int outboundLinks() {
        return (this.outboundLinks < 0) ? 0 : this.outboundLinks;
    }
    
    public void writeXML(OutputStreamWriter os, Date date) throws IOException {
        os.write("<record>\n");
        os.write("<dc:Title><![CDATA[" + this.dc_title() + "]]></dc:Title>\n");
        os.write("<dc:Identifier>" + this.dc_identifier() + "</dc:Identifier>\n");
        os.write("<dc:Creator><![CDATA[" + this.dc_creator() + "]]></dc:Creator>\n");
        os.write("<dc:Description><![CDATA[");
        byte[] buffer = new byte[1000];
        int c = 0;
        InputStream is = this.getText();
        while ((c = is.read(buffer)) > 0) os.write(new String(buffer, 0, c));
        is.close();
        os.write("]]></dc:Description>\n");
        os.write("<dc:Language>" + this.dc_language() + "</dc:Language>\n");
        os.write("<dc:Date>" + DateFormatter.formatISO8601(date) + "</dc:Date>\n");
        os.write("</record>\n");
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
                FileUtils.deletedelete((File) this.text); 
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
    

    /**
     * Parse the resource
     * @param url the URL of the resource
     * @param contentLength the contentLength of the resource
     * @param resourceStream the resource body as stream
     * @param docInfo metadata about the resource
     * @return the extracted data
     * @throws ParserException
     */
    public static Document parseDocument(final yacyURL url, final long contentLength, final InputStream resourceStream, ResponseHeader responseHeader) throws ParserException {
        try {
            if (resourceStream == null) return null;

            // STEP 1: if no resource metadata is available, try to load it from cache 
            if (responseHeader == null) {
                // try to get the header from the htcache directory
                try {                    
                    responseHeader = Cache.getResponseHeader(url);
                } catch (final Exception e) {
                    // ignore this. resource info loading failed
                }   
            }
            
            // STEP 2: if the metadata is still null try to download it from web
            if ((responseHeader == null) && (url.getProtocol().startsWith("http"))) {
                // TODO: we need a better solution here
                // e.g. encapsulate this in the crawlLoader class
                
                // getting URL mimeType
                try {
                    responseHeader = Client.whead(url.toString());
                } catch (final Exception e) {
                    // ingore this. http header download failed
                } 
            }

            // STEP 3: if the metadata is still null try to guess the mimeType of the resource
            String supportError = Parser.supports(url, responseHeader == null ? null : responseHeader.mime());
            if (supportError != null) {
                return null;
            }
            if (responseHeader == null) {
                return Parser.parseSource(url, null, null, contentLength, resourceStream);
            }
            return Parser.parseSource(url, responseHeader.mime(), responseHeader.getCharacterEncoding(), contentLength, resourceStream);
        } catch (final InterruptedException e) {
            // interruption of thread detected
            return null;
        }
    }
    
    public static Document parseDocument(final yacyURL url, final long contentLength, final InputStream resourceStream) throws ParserException {
        return parseDocument(url, contentLength, resourceStream, null);
    }
    
}
