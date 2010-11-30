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

package net.yacy.document;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.ArrayList;
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

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.kelondro.util.DateFormatter;
import net.yacy.kelondro.util.FileUtils;


public class Document {
    
    private final MultiProtocolURI source;      // the source url
    private final String mimeType;              // mimeType as taken from http header
    private final String charset;               // the charset of the document
    private final List<String> keywords;        // most resources provide a keyword field
    private       StringBuilder title;          // a document title, taken from title or h1 tag; shall appear as headline of search result
    private final StringBuilder creator;        // author or copyright
    private final String publisher;             // publisher
    private final List<String>  sections;       // if present: more titles/headlines appearing in the document
    private final StringBuilder description;    // an abstract, if present: short content description
    private Object text;                        // the clear text, all that is visible
    private final Map<MultiProtocolURI, String> anchors; // all links embedded as clickeable entities (anchor tags)
    private final Map<MultiProtocolURI, String> rss; // all embedded rss feeds
    private final HashMap<MultiProtocolURI, ImageEntry> images; // all visible pictures in document
    // the anchors and images - Maps are URL-to-EntityDescription mappings.
    // The EntityDescription appear either as visible text in anchors or as alternative
    // text in image tags.
    private Map<MultiProtocolURI, String> hyperlinks, audiolinks, videolinks, applinks;
    private Map<String, String> emaillinks;
    private MultiProtocolURI favicon;
    private boolean resorted;
    private InputStream textStream;
    private int inboundLinks, outboundLinks; // counters for inbound and outbound links, are counted after calling notifyWebStructure
    private Set<String> languages;
    private boolean indexingDenied;
    
    public Document(final MultiProtocolURI location, final String mimeType, final String charset, final Set<String> languages,
                    final String[] keywords, final String title, final String author, final String publisher,
                    final String[] sections, final String abstrct,
                    final Object text,
                    final Map<MultiProtocolURI, String> anchors,
                    final Map<MultiProtocolURI, String> rss,
                    final HashMap<MultiProtocolURI, ImageEntry> images,
                    boolean indexingDenied) {
        this.source = location;
        this.mimeType = (mimeType == null) ? "application/octet-stream" : mimeType;
        this.charset = charset;
        this.keywords = (keywords == null) ? new LinkedList<String>() : Arrays.asList(keywords);
        this.title = (title == null) ? new StringBuilder(0) : new StringBuilder(title);
        this.creator = (author == null) ? new StringBuilder(0) : new StringBuilder(author);
        this.sections = (sections == null) ? new LinkedList<String>() : Arrays.asList(sections);
        this.description = (abstrct == null) ? new StringBuilder(0) : new StringBuilder(abstrct);
        this.anchors = (anchors == null) ? new HashMap<MultiProtocolURI, String>(0) : anchors;
        this.rss = (rss == null) ? new HashMap<MultiProtocolURI, String>(0) : rss;
        this.images =  (images == null) ? new HashMap<MultiProtocolURI, ImageEntry>() : images;
        this.publisher = publisher;
        this.hyperlinks = null;
        this.audiolinks = null;
        this.videolinks = null;
        this.applinks = null;
        this.emaillinks = null;
        this.resorted = false;
        this.inboundLinks = -1;
        this.outboundLinks = -1;
        this.languages = languages;
        this.indexingDenied = indexingDenied;
        
        if (text == null)
            this.text = new ByteArrayOutputStream();
        else {
            this.text = text;
        }
    }
    
    public void setInboundLinks(int il) {
        this.inboundLinks = il;
    }
    
    public void setOutboundLinks(int ol) {
        this.outboundLinks = ol;
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
        if (this.languages.isEmpty()) return null;
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
        return (title == null) ? "" : title.toString();
    }

    public void setTitle(String title) {
        this.title = new StringBuilder(title);
    }
    
    public String dc_creator() {
        return (creator == null) ? "" : creator.toString();
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
        if (hs.isEmpty()) return "";
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
        return this.publisher == null ? "" : this.publisher;
    }
    
    public String dc_format() {
        return this.mimeType;
    }
    
    public String dc_identifier() {
        return this.source.toNormalform(true, false);
    }
    
    public MultiProtocolURI dc_source() {
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
            if (this.text == null) return new ByteArrayInputStream("".getBytes());

            if (this.text instanceof File) {
                this.textStream = new BufferedInputStream(new FileInputStream((File)this.text));
            } else if (this.text instanceof byte[]) {
                this.textStream =  new ByteArrayInputStream((byte[]) this.text);
            } else if (this.text instanceof ByteArrayOutputStream) {
                this.textStream =  new ByteArrayInputStream(((ByteArrayOutputStream) this.text).toByteArray());
            }
            return this.textStream;
        } catch (final Exception e) {
            Log.logException(e);
        }
        return new ByteArrayInputStream("".getBytes());
    }
    
    public byte[] getTextBytes() {
        try {
            if (this.text == null) return new byte[0];

            if (this.text instanceof File) {
                return FileUtils.read((File)this.text);
            } else if (this.text instanceof byte[]) {
                return (byte[])this.text;
            } else if (this.text instanceof ByteArrayOutputStream) {
                return ((ByteArrayOutputStream) this.text).toByteArray();
            }
        } catch (final Exception e) {
            Log.logException(e);
        }
        return new byte[0];             
    }
    
    public long getTextLength() {
        if (this.text == null) return 0;
        if (this.text instanceof File) return ((File) this.text).length();
        else if (this.text instanceof byte[]) return ((byte[]) this.text).length;
        else if (this.text instanceof ByteArrayOutputStream) {
            return ((ByteArrayOutputStream)this.text).size();
        }
        
        return -1; 
    }
    
    public List<StringBuilder> getSentences(final boolean pre) {
        if (this.text == null) return null;
        final Condenser.sentencesFromInputStreamEnum e = Condenser.sentencesFromInputStream(getText());
        e.pre(pre);
        ArrayList<StringBuilder> sentences = new ArrayList<StringBuilder>();
        while (e.hasNext()) {
            sentences.add(e.next());
        }
        return sentences;
    }
    
    public List<String> getKeywords() {
        return this.keywords;
    }
    
    public Map<MultiProtocolURI, String> getAnchors() {
        // returns all links embedded as anchors (clickeable entities)
        // this is a url(String)/text(String) map
        return anchors;
    }
    
    public Map<MultiProtocolURI, String> getRSS() {
        // returns all links embedded as anchors (clickeable entities)
        // this is a url(String)/text(String) map
        return rss;
    }
    
    
    // the next three methods provide a calculated view on the getAnchors/getImages:
    
    public Map<MultiProtocolURI, String> getHyperlinks() {
        // this is a subset of the getAnchor-set: only links to other hyperrefs
        if (!resorted) resortLinks();
        return hyperlinks;
    }
    
    public Map<MultiProtocolURI, String> getAudiolinks() {
        if (!resorted) resortLinks();
        return this.audiolinks;
    }
    
    public Map<MultiProtocolURI, String> getVideolinks() {
        if (!resorted) resortLinks();
        return this.videolinks;
    }
    
    public HashMap<MultiProtocolURI, ImageEntry> getImages() {
        // returns all links enbedded as pictures (visible in document)
        // this resturns a htmlFilterImageEntry collection
        if (!resorted) resortLinks();
        return images;
    }
    
    public Map<MultiProtocolURI, String> getApplinks() {
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
        MultiProtocolURI url;
        String u;
        int extpos, qpos;
        String ext = null;
        final Iterator<Map.Entry<MultiProtocolURI, String>> i = anchors.entrySet().iterator();
        hyperlinks = new HashMap<MultiProtocolURI, String>();
        videolinks = new HashMap<MultiProtocolURI, String>();
        audiolinks = new HashMap<MultiProtocolURI, String>();
        applinks   = new HashMap<MultiProtocolURI, String>();
        emaillinks = new HashMap<String, String>();
        final HashMap<MultiProtocolURI, ImageEntry> collectedImages = new HashMap<MultiProtocolURI, ImageEntry>(); // this is a set that is collected now and joined later to the imagelinks
        Map.Entry<MultiProtocolURI, String> entry;
        while (i.hasNext()) {
            entry = i.next();
            url = entry.getKey();
            if (url == null) continue;
            u = url.toNormalform(true, false);
            if (u.startsWith("mailto:")) {
                emaillinks.put(u.substring(7), entry.getValue());
            } else {
                extpos = u.lastIndexOf('.');
                if (extpos > 0) {
                    if (((qpos = u.indexOf('?')) >= 0) && (qpos > extpos)) {
                        ext = u.substring(extpos + 1, qpos).toLowerCase();
                    } else {
                        ext = u.substring(extpos + 1).toLowerCase();
                    }
                    if (Classification.isMediaExtension(ext)) {
                        // this is not a normal anchor, its a media link
                        if (Classification.isImageExtension(ext)) {
                            ContentScraper.addImage(collectedImages, new ImageEntry(url, entry.getValue(), -1, -1, -1));
                        }
                        else if (Classification.isAudioExtension(ext)) audiolinks.put(url, entry.getValue());
                        else if (Classification.isVideoExtension(ext)) videolinks.put(url, entry.getValue());
                        else if (Classification.isApplicationExtension(ext)) applinks.put(url, entry.getValue());
                    }
                }
                // in any case we consider this as a link and let the parser decide if that link can be followed
                hyperlinks.put(url, entry.getValue());
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
    
    public static Map<MultiProtocolURI, String> allSubpaths(final Collection<?> links) {
        // links is either a Set of Strings (urls) or a Set of
        // htmlFilterImageEntries
        final HashSet<String> h = new HashSet<String>();
        Iterator<?> i = links.iterator();
        Object o;
        MultiProtocolURI url;
        String u;
        int pos;
        int l;
        while (i.hasNext())
            try {
                o = i.next();
                if (o instanceof MultiProtocolURI) url = (MultiProtocolURI) o;
                else if (o instanceof String) url = new MultiProtocolURI((String) o);
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
        final HashMap<MultiProtocolURI, String> v = new HashMap<MultiProtocolURI, String>();
        while (i.hasNext()) {
            u = (String) i.next();
            try {
                url = new MultiProtocolURI(u);
                v.put(url, "sub");
            } catch (final MalformedURLException e) {
            }
        }
        return v;
    }
    
    public static Map<MultiProtocolURI, String> allReflinks(final Collection<?> links) {
        // links is either a Set of Strings (with urls) or
        // htmlFilterImageEntries
        // we find all links that are part of a reference inside a url
        final HashMap<MultiProtocolURI, String> v = new HashMap<MultiProtocolURI, String>();
        final Iterator<?> i = links.iterator();
        Object o;
        MultiProtocolURI url;
        String u;
        int pos;
        loop: while (i.hasNext())
            try {
                o = i.next();
                if (o instanceof MultiProtocolURI)
                    url = (MultiProtocolURI) o;
                else if (o instanceof String)
                    url = new MultiProtocolURI((String) o);
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
                    url = new MultiProtocolURI(u);
                    if (!(v.containsKey(url)))
                        v.put(url, "ref");
                    continue loop;
                }
                if ((pos = u.toLowerCase().indexOf("/www.", 7)) > 0) {
                    i.remove();
                    u = "http:/" + u.substring(pos);
                    while ((pos = u.toLowerCase().indexOf("/www.", 7)) > 0)
                        u = "http:/" + u.substring(pos);
                    url = new MultiProtocolURI(u);
                    if (!(v.containsKey(url)))
                        v.put(url, "ref");
                    continue loop;
                }
            } catch (final MalformedURLException e) {
            }
        return v;
    }
    
    public void addSubDocuments(final Document[] docs) throws IOException {
        for (Document doc: docs) {
            this.sections.addAll(Arrays.asList(doc.getSectionTitles()));
            
            if (this.title.length() > 0) this.title.append('\n');
            this.title.append(doc.dc_title());
            
            this.keywords.addAll(doc.getKeywords());
            
            if (this.description.length() > 0) this.description.append('\n');
            this.description.append(doc.dc_description());
            
            if (!(this.text instanceof ByteArrayOutputStream)) {
                this.text = new ByteArrayOutputStream();
            }
            FileUtils.copy(doc.getText(), (ByteArrayOutputStream) this.text);

            anchors.putAll(doc.getAnchors());
            rss.putAll(doc.getRSS());
            ContentScraper.addAllImages(images, doc.getImages());
        }
    }
    
    /**
     * @return the {@link URL} to the favicon that belongs to the document
     */
    public MultiProtocolURI getFavicon() {
    	return this.favicon;
    }
    
    /**
     * @param faviconURL the {@link URL} to the favicon that belongs to the document
     */
    public void setFavicon(final MultiProtocolURI faviconURL) {
    	this.favicon = faviconURL;
    }
    
    public int inboundLinks() {
        return (this.inboundLinks < 0) ? 0 : this.inboundLinks;
    }
    
    public int outboundLinks() {
        return (this.outboundLinks < 0) ? 0 : this.outboundLinks;
    }
    
    public boolean indexingDenied() {
        return this.indexingDenied;
    }
    
    public void writeXML(OutputStreamWriter os, Date date) throws IOException {
        os.write("<record>\n");
        String title = this.dc_title();
        if (title != null && title.length() > 0) os.write("<dc:title><![CDATA[" + title + "]]></dc:title>\n");
        os.write("<dc:identifier>" + this.dc_identifier() + "</dc:identifier>\n");
        String creator = this.dc_creator();
        if (creator != null && creator.length() > 0) os.write("<dc:creator><![CDATA[" + creator + "]]></dc:creator>\n");
        String publisher = this.dc_publisher();
        if (publisher != null && publisher.length() > 0) os.write("<dc:publisher><![CDATA[" + publisher + "]]></dc:publisher>\n");
        String subject = this.dc_subject(';');
        if (subject != null && subject.length() > 0) os.write("<dc:subject><![CDATA[" + subject + "]]></dc:subject>\n");
        if (this.text != null) {
            os.write("<dc:description><![CDATA[");
            byte[] buffer = new byte[1000];
            int c = 0;
            InputStream is = this.getText();
            while ((c = is.read(buffer)) > 0) os.write(new String(buffer, 0, c));
            is.close();
            os.write("]]></dc:description>\n");
        }
        String language = this.dc_language();
        if (language != null && language.length() > 0) os.write("<dc:language>" + this.dc_language() + "</dc:language>\n");
        os.write("<dc:date>" + DateFormatter.formatISO8601(date) + "</dc:date>\n");
        os.write("</record>\n");
    }
    
    public String toString() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter osw;
        try {
            osw = new OutputStreamWriter(baos, "UTF-8");
            writeXML(osw, new Date());
            osw.close();
            return new String(baos.toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException e1) {
            return "";
        } catch (IOException e) {
            return "";
        }
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
    
    /**
     * merge documents: a helper method for all parsers that return multiple documents
     * @param docs
     * @return
     */
    public static Document mergeDocuments(final MultiProtocolURI location, final String globalMime, Document[] docs) {
        if (docs == null || docs.length == 0) return null;
        if (docs.length == 1) return docs[0];
        
        long docTextLength = 0;
        final ByteBuffer         content       = new ByteBuffer();
        final StringBuilder      authors       = new StringBuilder();
        final StringBuilder      publishers    = new StringBuilder();
        final StringBuilder      subjects      = new StringBuilder();
        final StringBuilder      title         = new StringBuilder();
        final StringBuilder      description   = new StringBuilder();
        final LinkedList<String> sectionTitles = new LinkedList<String>();

        final Map<MultiProtocolURI, String> anchors = new HashMap<MultiProtocolURI, String>();
        final Map<MultiProtocolURI, String> rss = new HashMap<MultiProtocolURI, String>();
        final HashMap<MultiProtocolURI, ImageEntry> images = new HashMap<MultiProtocolURI, ImageEntry>();
        
        for (Document doc: docs) {
            
            String author = doc.dc_creator();
            if (author.length() > 0) {
                if (authors.length() > 0) authors.append(",");
                subjects.append(author);
            }
            
            String publisher = doc.dc_publisher();
            if (publisher.length() > 0) {
                if (publishers.length() > 0) publishers.append(",");
                publishers.append(publisher);
            }
            
            String subject = doc.dc_subject(',');
            if (subject.length() > 0) {
                if (subjects.length() > 0) subjects.append(",");
                subjects.append(subject);
            }
            
            if (title.length() > 0) title.append("\n");
            title.append(doc.dc_title());
            
            sectionTitles.addAll(Arrays.asList(doc.getSectionTitles()));
            
            if (description.length() > 0) description.append("\n");
            description.append(doc.dc_description());
    
            if (doc.getTextLength() > 0) {
                if (docTextLength > 0) content.write('\n');
                try {
                    docTextLength += FileUtils.copy(doc.getText(), content);
                } catch (IOException e) {
                    Log.logException(e);
                }
            }
            anchors.putAll(doc.getAnchors());
            rss.putAll(doc.getRSS());
            ContentScraper.addAllImages(images, doc.getImages());
        }
        return new Document(
                location,
                globalMime,
                null,
                null,
                subjects.toString().split(" |,"),
                title.toString(),
                authors.toString(),
                publishers.toString(),
                sectionTitles.toArray(new String[sectionTitles.size()]),
                description.toString(),
                content.getBytes(),
                anchors,
                rss,
                images,
                false);
    }
    
    public static Map<MultiProtocolURI, String> getHyperlinks(Document[] documents) {
        Map<MultiProtocolURI, String> result = new HashMap<MultiProtocolURI, String>();
        for (Document d: documents) result.putAll(d.getHyperlinks());
        return result;
    }
    
    public static Map<MultiProtocolURI, String> getImagelinks(Document[] documents) {
        Map<MultiProtocolURI, String> result = new HashMap<MultiProtocolURI, String>();
        for (Document d: documents) {
            for (ImageEntry imageReference : d.getImages().values()) {
                result.put(imageReference.url(), imageReference.alt());
            }
        }
        return result;
    }

    
}
