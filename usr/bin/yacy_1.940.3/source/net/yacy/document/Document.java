//Document.java
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
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
import java.io.Writer;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.retrieval.Request;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.IconEntry;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.util.FileUtils;


public class Document {

    private DigestURL source;             // the source url
    private final String mimeType;              // mimeType as taken from http header
    private final String charset;               // the charset of the document
    private final Set<String> keywords;         // most resources provide a keyword field
    private       List<String> titles;          // the document titles, taken from title and/or h1 tag; shall appear as headline of search result
    private final StringBuilder creator;        // author or copyright
    private final String publisher;             // publisher
    private final List<String> sections;        // if present: more titles/headlines appearing in the document
    private final List<String> descriptions;    // an abstract, if present: short content description
    private Object text;                        // the clear text, all that is visible
    private final Collection<AnchorURL> anchors;   // all links embedded as clickeable entities (anchor tags)
    private final LinkedHashMap<DigestURL, String> rss;   // all embedded rss feeds
    private final LinkedHashMap<DigestURL, ImageEntry> images; // all visible pictures in document
    // the anchors and images - Maps are URL-to-EntityDescription mappings.
    // The EntityDescription appear either as visible text in anchors or as alternative
    // text in image tags.
    private LinkedHashMap<AnchorURL, String> audiolinks, videolinks, applinks, hyperlinks; // TODO: check if redundant value (set to key.getNameProperty()) is needed
    private LinkedHashMap<DigestURL, String> inboundlinks, outboundlinks;
    
    /** links to icons that belongs to the document (mapped by absolute URL) */
    private Map<DigestURL, IconEntry> icons;
    
    /**
     * URLs of linked data item types/classes referenced by the document (for example in
     * HTML with standard annotations such as RDFa, microdata, microformats or
     * JSON-LD)
     */
    private Set<DigestURL> linkedDataTypes;
    private boolean resorted;
    private final Set<String> languages;
    private boolean indexingDenied;
    private final double lon, lat;
    private final Parser parserObject; // the parser object that was used to create the Document
    // TODO: to allow to use scraper during indexing (for some parsers) it has to be remembered here, but it holds redundant information.
    private  Object scraperObject; // remember the source object that was used to create the Document (used during indexing)
    private final Map<String, Set<String>> generic_facets; // a map from vocabulary names to the set of tags for that vocabulary which apply for this document
    private final Date lastModified; // creation or last modification date of the source document
    private int crawldepth;
    
    /** True when this document is the result of a partially parsed resource, for example due to resource content size exceeding a given limit */
    private boolean partiallyParsed;

    public Document(final DigestURL location, final String mimeType, final String charset,
                    final Parser parserObject,
                    final Set<String> languages,
                    final String[] keywords,
                    final List<String> titles,
                    final String author, final String publisher,
                    final String[] sections, final List<String> abstrcts,
                    final double lon, final double lat,
                    final Object text,
                    final Collection<AnchorURL> anchors,
                    final LinkedHashMap<DigestURL, String> rss,
                    final LinkedHashMap<DigestURL, ImageEntry> images,
                    final boolean indexingDenied,
                    final Date lastModified) {
        this.source = location;
        this.mimeType = (mimeType == null) ? "application/octet-stream" : mimeType;
        this.charset = charset;
        this.parserObject = parserObject;
        this.keywords = new LinkedHashSet<String>();
        if (keywords != null) {
           Collections.addAll(this.keywords, keywords);
        }
        this.titles = (titles == null) ? new ArrayList<String>(1) : titles;
        this.creator = (author == null) ? new StringBuilder(0) : new StringBuilder(author);
        this.sections =  new LinkedList<String>() ;
        if (sections != null) {
           Collections.addAll(this.sections, sections);
        }
        this.descriptions = (abstrcts == null) ? new ArrayList<String>() : abstrcts;
        if (lat >= -90.0d && lat <= 90.0d && lon >= -180.0d && lon <= 180.0d) {
            this.lon = lon;
            this.lat = lat;
        } else {
            // we ignore false values because otherwise solr will cause an error when we input the coordinates into the index
            this.lon = 0.0d;
            this.lat = 0.0d;
        }
        this.anchors = (anchors == null) ? new ArrayList<AnchorURL>(0) : anchors;
        this.rss = (rss == null) ? new LinkedHashMap<DigestURL, String>(0) : rss;
        this.images = (images == null) ? new LinkedHashMap<DigestURL, ImageEntry>() : images;
        this.publisher = publisher;
        this.hyperlinks = null;
        this.audiolinks = null;
        this.videolinks = null;
        this.applinks = null;
        this.icons = new HashMap<>();
        this.linkedDataTypes = new HashSet<>();
        this.resorted = false;
        this.inboundlinks = null;
        this.outboundlinks = null;
        this.languages = languages;
        this.indexingDenied = indexingDenied;
        this.text = text == null ? "" : text;
        this.generic_facets = new HashMap<String, Set<String>>();
        this.lastModified = lastModified == null ? new Date() : lastModified;
        this.crawldepth = 999; // unknown yet
        this.scraperObject = null; // will be set by setScraperObject()
        this.partiallyParsed = false;
    }

    /**
     * Get the content domain of a document. This tries to get the content domain from the mime type
     * and if this fails it uses alternatively the content domain from the file extension.
     * @return the content domain which classifies the content type
     */
    public ContentDomain getContentDomain() {
        ContentDomain contentDomain = Classification.getContentDomainFromMime(this.mimeType);
        if (contentDomain != ContentDomain.ALL) return contentDomain;
        return this.dc_source().getContentDomainFromExt();
    }

    /**
     * The parser used to generate the document
     * @return Parser
     */
    public Parser getParserObject() {
        return this.parserObject;
    }

    /**
     * Convenient call to get the source/scraper object of the underlaying parser
     * if the parser uses a scraper, like htmlParser
     * @return scraper object typically of type ContentScraper but may also of type DCEntry
     */
    public Object getScraperObject() {
        return this.scraperObject;
    }

    /**
     * Remember the scraper object used, to be able to access used scraper by
     * getScraperObject().
     * This is used for surrogate parsers to set a other source/scraper then ContentScraper
     * used e.g. by htmlParser.
     * @param scraper 
     */
    public void setScraperObject(Object scraper) {
        if (this.scraperObject != null) {
            if (this.scraperObject instanceof ContentScraper) {
                // support garbage collection
                ((ContentScraper) this.scraperObject).close();
            }
            this.scraperObject = null;
        }
        this.scraperObject = scraper;
    }
    
    public AnchorURL getCanonical() {
        final Object scraper = this.getScraperObject();
        if (!(scraper instanceof ContentScraper)) return null;
        final ContentScraper html = (ContentScraper) scraper;
        AnchorURL canonical = html.getCanonical();
        return canonical;
    }

    public Set<String> getContentLanguages() {
        return this.languages;
    }

    public String getFileName() {
       return this.source.getFileName();
    }

    public Map<String, Set<String>> getGenericFacets() {
        return this.generic_facets;
    }
    
    /**
     * @return true when this document is the result of a partially parsed resource, for example due to resource content size exceeding a given limit
     */
    public boolean isPartiallyParsed() {
        return this.partiallyParsed;
    }
    
    /**
     * @param partiallyParsed set to true to indicates this document is the result of a partially parsed resource, for example due to resource content size exceeding a given limit
     */
    public void setPartiallyParsed(final boolean partiallyParsed) {
        this.partiallyParsed = partiallyParsed;
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
        if (this.languages.size() == 1) return this.languages.iterator().next();
        if (this.languages.contains(this.source.language())) return this.source.language();
        // now we are confused: the declared languages differ all from the TLD
        // just pick one of the languages that we have
        return this.languages.iterator().next();
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

    /**
     * Get the main document title. This is the 1st in the list of titles.
     * @return title_string (may return null or empty string)
     */
    public String dc_title() {
        return (this.titles == null || this.titles.size() == 0) ? "" : this.titles.iterator().next();
    }

    public List<String> titles() {
        return this.titles;
    }

    /**
     * Sets the title of the document, replacing any existing titles.
     * @param title
     */
    public void setTitle(final String title) {
        this.titles = new ArrayList<String>();
        if (title != null) this.titles.add(title);
    }

    

    public String dc_creator() {
        return (this.creator == null) ? "" : this.creator.toString();
    }

    /**
     * add the given words to the set of keywords.
     * These keywords will appear in dc_subject
     * @param tags
     */
    public void addTags(Set<String> tags) {
        for (String s: tags) {
            if (s != null && !s.isEmpty()) this.keywords.add(s);
        }
    }

    /**
     * add the given words to the set of keywords.
     * These keywords will appear in dc_subject
     * @param tags a map where the key is the navigator name and the value is the set of attributes as metatags
     */
    protected void addMetatags(Map<String, Set<Tagging.Metatag>> tags) {
        this.generic_facets.putAll(computeGenericFacets(tags));
    }

    /**
     * compute generic facets
     * @param tags a map where the key is the navigator name and the value is the set of attributes as metatags
     * @return a map where the key is the navigator name and the value is the set of attributes names
     */
    public static Map<String, Set<String>> computeGenericFacets(Map<String, Set<Tagging.Metatag>> tags) {
        Map<String, Set<String>> gf = new HashMap<String, Set<String>>();
        for (Map.Entry<String, Set<Tagging.Metatag>> e: tags.entrySet()) {
            Tagging vocabulary = LibraryProvider.autotagging.getVocabulary(e.getKey());
            if (vocabulary == null) continue;
            Set<String> objects = new HashSet<String>();
            for (Tagging.Metatag s: e.getValue()) {
                objects.add(s.getObject());
            }
            gf.put(vocabulary.getName(), objects);
        }
        return gf;
    }

    /**
     * Get the set of keywords associated with the document
     * @return set of unique keywords
     */
    public Set<String> dc_subject() {
        return this.keywords;
    }

    /**
     * Get the set of keywords associated with the document and string
     * each keyword separated by the separator character
     *
     * @param separator character
     * @return string of keywords or empty string
     */
    public String dc_subject(final char separator) {
        if (this.keywords.size() == 0) return "";
        // generate a new list
        final StringBuilder sb = new StringBuilder(this.keywords.size() * 8);
        for (final String s: this.keywords) sb.append(s).append(separator);
        return sb.substring(0, sb.length() - 1);
    }

    public String[] dc_description() {
        if (descriptions == null) return new String[0];
        return this.descriptions.toArray(new String[this.descriptions.size()]);
    }

    public String dc_publisher() {
        return this.publisher == null ? "" : this.publisher;
    }

    /**
     * @return the Media Type (aka MIME Type) of the document
     */
    public String dc_format() {
        return this.mimeType;
    }

    public String dc_identifier() {
        return this.source.toNormalform(true);
    }

    public DigestURL dc_source() {
        return this.source;
    }

    /**
     * rewrite the dc_source; this can be used for normalization purpose
     * @param pattern
     * @param replacement
     */
    public void rewrite_dc_source(Pattern pattern, String replacement) {
        String u = this.source.toNormalform(false);
        Matcher m = pattern.matcher(u);
        if (m.matches()) {
            u = m.replaceAll(replacement);
            try {
                DigestURL du = new DigestURL(u);
                this.source = du;
            } catch (MalformedURLException e) {
            }
        }
    }

    /**
     * @return the supposed charset of this document or <code>null</code> if unknown
     */
    public String getCharset() {
        return this.charset;
    }

    public String[] getSectionTitles() {
        if (this.sections == null) {
            return new String[] { dc_title() };
        }
        return this.sections.toArray(new String[this.sections.size()]);
    }

    public InputStream getTextStream() {
        try {
            if (this.text == null) return new ByteArrayInputStream(UTF8.getBytes(""));
            if (this.text instanceof String) {
                //return new StreamReader((String) this.text);
                return new ByteArrayInputStream(UTF8.getBytes(((String) this.text)));
            } else if (this.text instanceof InputStream) {
                return (InputStream) this.text;
            } else if (this.text instanceof File) {
                return new BufferedInputStream(new FileInputStream((File)this.text));
            } else if (this.text instanceof byte[]) {
                return new ByteArrayInputStream((byte[]) this.text);
            } else if (this.text instanceof ByteArrayOutputStream) {
                return new ByteArrayInputStream(((ByteArrayOutputStream) this.text).toByteArray());
            }
            assert false : this.text.getClass().toString();
            return null;
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        }
        return new ByteArrayInputStream(UTF8.getBytes(""));
    }

    public String getTextString() {
        try {
            if (this.text == null) {
                this.text = "";
            } else if (this.text instanceof InputStream) {
                this.text = UTF8.String(FileUtils.read((InputStream) this.text));
            } else if (this.text instanceof File) {
                this.text = UTF8.String(FileUtils.read((File) this.text));
            } else if (this.text instanceof byte[]) {
                this.text = UTF8.String((byte[]) this.text);
            } else if (this.text instanceof ByteArrayOutputStream) {
                this.text = UTF8.String(((ByteArrayOutputStream) this.text).toByteArray());
            }
            assert this.text instanceof String : this.text.getClass().toString();
            return (String) this.text;
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        }
        return "";
    }

    public long getTextLength() {
        try {
            if (this.text == null) return -1;
            if (this.text instanceof String) {
                return ((String) this.text).length();
            } else if (this.text instanceof InputStream) {
                return ((InputStream) this.text).available();
            } else if (this.text instanceof File) {
                return ((File) this.text).length();
            } else if (this.text instanceof byte[]) {
                return ((byte[]) this.text).length;
            } else if (this.text instanceof ByteArrayOutputStream) {
                return ((ByteArrayOutputStream) this.text).size();
            }
            assert false : this.text.getClass().toString();
            return -1;
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        }
        return -1;
    }

    public List<StringBuilder> getSentences(final boolean pre) {
        final SentenceReader sr = new SentenceReader(getTextString(), pre);
        List<StringBuilder> sentences = new ArrayList<StringBuilder>();
        while (sr.hasNext()) {
            sentences.add(sr.next());
        }
        return sentences;
    }

    /**
     * All anchor links of the document
     * (this includes mailto links)
     * @return all links embedded as anchors (clickeable entities)
     */
    public Collection<AnchorURL> getAnchors() {
        // returns all links embedded as anchors (clickeable entities)
        // this is a url(String)/text(String) map
        return this.anchors;
    }

    public LinkedHashMap<DigestURL, String> getRSS() {
        // returns all links embedded as anchors (clickeable entities)
        // this is a url(String)/text(String) map
        return this.rss;
    }


    // the next three methods provide a calculated view on the getAnchors/getImages:

    /**
     * List of links to resources (pages, images, files, media ...)
     * (Hyperlinks do not include mailto: links)
     * @return a subset of the getAnchor-set: only links to other hyperrefs
     */
    public Map<AnchorURL, String> getHyperlinks() {
        // this is a subset of the getAnchor-set: only links to other hyperrefs
        if (!this.resorted) resortLinks();
        return this.hyperlinks;
    }

    public Map<AnchorURL, String> getAudiolinks() {
        if (!this.resorted) resortLinks();
        return this.audiolinks;
    }

    public Map<AnchorURL, String> getVideolinks() {
        if (!this.resorted) resortLinks();
        return this.videolinks;
    }

    public LinkedHashMap<DigestURL, ImageEntry> getImages() {
        // returns all links enbedded as pictures (visible in document)
        // this resturns a htmlFilterImageEntry collection
        if (!this.resorted) resortLinks();
        return this.images;
    }

    public Map<AnchorURL, String> getApplinks() {
        if (!this.resorted) resortLinks();
        return this.applinks;
    }


    /**
     * @return last modification date of the source document. (The date is initialized with last modification date or received date)
     */
    public Date getLastModified() {
        return this.lastModified;
    }

    public double lon() {
        return this.lon;
    }

    public double lat() {
        return this.lat;
    }

    /**
     * sorts all links (anchors) into individual collections
     */
    private void resortLinks() {
        if (this.resorted) return;
        synchronized (this) {
            if (this.resorted) return;
            // extract hyperlinks, medialinks from anchorlinks
            String u;
            int extpos, qpos;
            String ext = null;
            final String thishost = this.source.getHost();
            this.inboundlinks = new LinkedHashMap<DigestURL, String>();
            this.outboundlinks = new LinkedHashMap<DigestURL, String>();
            this.hyperlinks = new LinkedHashMap<AnchorURL, String>();
            this.videolinks = new LinkedHashMap<AnchorURL, String>();
            this.audiolinks = new LinkedHashMap<AnchorURL, String>();
            this.applinks   = new LinkedHashMap<AnchorURL, String>();
            final Map<AnchorURL, ImageEntry> collectedImages = new HashMap<AnchorURL, ImageEntry>(); // this is a set that is collected now and joined later to the imagelinks
            for (final Map.Entry<DigestURL, ImageEntry> entry: this.images.entrySet()) {
                if (entry.getKey() != null && entry.getKey().getHost() != null && entry.getKey().getHost().equals(thishost)) this.inboundlinks.put(entry.getKey(), "image"); else this.outboundlinks.put(entry.getKey(), "image");
            }
            for (final AnchorURL url: this.anchors) {
                if (url == null) continue;
                u = url.toNormalform(true);
                final String name = url.getNameProperty();
                // check mailto scheme first (not supposed to get into in/outboundlinks or hyperlinks -> crawler can't process)
                if (url.getProtocol().equals("mailto")) {
                    continue;
                }

                final boolean noindex = url.getRelProperty().toLowerCase(Locale.ROOT).indexOf("noindex",0) >= 0;
                final boolean nofollow = url.getRelProperty().toLowerCase(Locale.ROOT).indexOf("nofollow",0) >= 0;
                if ((thishost == null && url.getHost() == null) ||
                    ((thishost != null && url.getHost() != null) &&
                     (url.getHost().endsWith(thishost) ||
                      (thishost.startsWith("www.") && url.getHost().endsWith(thishost.substring(4)))))) {
                    this.inboundlinks.put(url, "anchor" + (noindex ? " noindex" : "") + (nofollow ? " nofollow" : ""));
                } else {
                    this.outboundlinks.put(url, "anchor" + (noindex ? " noindex" : "") + (nofollow ? " nofollow" : ""));
                }
                extpos = u.lastIndexOf('.');
                if (extpos > 0) {
                    if (((qpos = u.indexOf('?')) >= 0) && (qpos > extpos)) {
                        ext = u.substring(extpos + 1, qpos).toLowerCase(Locale.ROOT);
                    } else {
                        ext = u.substring(extpos + 1).toLowerCase(Locale.ROOT);
                    }
                    if (Classification.isMediaExtension(ext)) {
                        // this is not a normal anchor, its a media link
                        if (Classification.isImageExtension(ext)) { // TODO: guess on a-tag href extension (may not be correct)
                            collectedImages.put(url, new ImageEntry(url, name, -1, -1, -1));
                        } else if (Classification.isAudioExtension(ext)) this.audiolinks.put(url, name);
                          else if (Classification.isVideoExtension(ext)) this.videolinks.put(url, name);
                          else if (Classification.isApplicationExtension(ext)) this.applinks.put(url, name);
                    }
                }
                // in any case we consider this as a link and let the parser decide if that link can be followed
                this.hyperlinks.put(url, name);
            }

            // add image links that we collected from the anchors to the image map
            this.images.putAll(collectedImages);

            // expand the hyperlinks:
            // we add artificial hyperlinks to the hyperlink set
            // that can be calculated from given hyperlinks and imagelinks
            
            /*
             * Should we also include icons ? with
             * this.hyperlinks.putAll(allReflinks(this.icons.keySet())); It is
             * problematic as allReflinks will modify icons set set, removing those whose URL is
             * starting with "/www" but it is not desired for icons such as
             * www.wikipedia.org/static/favicon/wikipedia.ico
             */

            this.hyperlinks.putAll(allReflinks(this.images.values()));
            this.hyperlinks.putAll(allReflinks(this.audiolinks.keySet()));
            this.hyperlinks.putAll(allReflinks(this.videolinks.keySet()));
            this.hyperlinks.putAll(allReflinks(this.applinks.keySet()));
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
    }

    public static Map<MultiProtocolURL, String> allSubpaths(final Collection<?> links) {
        // links is either a Set of Strings (urls) or a Set of
        // htmlFilterImageEntries
        final Set<String> h = new HashSet<String>();
        Iterator<?> i = links.iterator();
        Object o;
        MultiProtocolURL url;
        String u;
        int pos;
        int l;
        while (i.hasNext())
            try {
                o = i.next();
                if (o instanceof MultiProtocolURL) url = (MultiProtocolURL) o;
                else if (o instanceof String) url = new MultiProtocolURL((String) o);
                else if (o instanceof ImageEntry) url = ((ImageEntry) o).url();
                else {
                    assert false;
                    continue;
                }
                u = url.toNormalform(true);
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
        final Map<MultiProtocolURL, String> v = new HashMap<MultiProtocolURL, String>();
        while (i.hasNext()) {
            u = (String) i.next();
            try {
                url = new MultiProtocolURL(u);
                v.put(url, "sub");
            } catch (final MalformedURLException e) {
            }
        }
        return v;
    }

    /**
     * We find all links that are part of a reference inside a url
     *
     * @param links links is either a Set of AnchorURL, Strings (with urls) or htmlFilterImageEntries
     * @return map with contained urls as key and "ref" as value
     */
    private static Map<AnchorURL, String> allReflinks(final Collection<?> links) {
        final Map<AnchorURL, String> v = new HashMap<AnchorURL, String>();
        final Iterator<?> i = links.iterator();
        Object o;
        AnchorURL url = null;
        String u;
        int pos;
        loop: while (i.hasNext())
            try {
                url = null;
                o = i.next();
                if (o instanceof AnchorURL)
                    url = (AnchorURL) o;
                else if (o instanceof String)
                    url = new AnchorURL((String) o);
                else if (o instanceof ImageEntry)
                    url = new AnchorURL(((ImageEntry) o).url());
                else if (o instanceof IconEntry)
                    url = new AnchorURL(((IconEntry) o).getUrl());
                else {
                    assert false;
                    continue loop;
                }
                u = url.toNormalform(true);

                // find start of a referenced http url
                if ((pos = u.toLowerCase(Locale.ROOT).indexOf("http://", 7)) > 0) { // 7 = skip the protocol part of the source url
                    i.remove();
                    u = u.substring(pos);
                    while ((pos = u.toLowerCase(Locale.ROOT).indexOf("http://", 7)) > 0)
                        u = u.substring(pos);
                    url = new AnchorURL(u);
                    if (!(v.containsKey(url)))
                        v.put(url, "ref");
                    continue loop;
                }

                // find start of a referenced https url
                if ((pos = u.toLowerCase(Locale.ROOT).indexOf("https://", 7)) > 0) { // 7 = skip the protocol part of the source url
                    i.remove();
                    u = u.substring(pos);
                    while ((pos = u.toLowerCase(Locale.ROOT).indexOf("https://", 7)) > 0)
                        u = u.substring(pos);
                    url = new AnchorURL(u);
                    if (!(v.containsKey(url)))
                        v.put(url, "ref");
                    continue loop;
                }
                
                if ((pos = u.toLowerCase(Locale.ROOT).indexOf("/www.", 11)) > 0) { // 11 = skip protocol part + www of source url "http://www."
                    i.remove();
                    u = url.getProtocol()+":/" + u.substring(pos);
                    while ((pos = u.toLowerCase(Locale.ROOT).indexOf("/www.", 11)) > 0)
                        u = url.getProtocol()+":/" + u.substring(pos);

                    AnchorURL addurl = new AnchorURL(u);
                    if (!(v.containsKey(addurl)))
                        v.put(addurl, "ref");
                    continue loop;
                }
            } catch (final MalformedURLException e) {
            }
        return v;
    }

    /**
     * Adds the main content of subdocuments to this document.
     * This is useful if the document is a container for other documents (like zip or other archives)
     * to make the content of the subdocuments searcheable,
     * but has only one url (unlike container-urls as rss).
     *
     * This is similar to mergeDocuments but directly joins internal content variables,
     * uses less parsed details and keeps this documents crawl data (like crawldepth, lastmodified)
     *
     * @see #mergeDocuments(DigestURL, String, Document[])
     * @param docs to be included
     * @throws IOException
     */
    public void addSubDocuments(final Document[] docs) throws IOException {
        for (final Document doc: docs) {
            this.sections.addAll(doc.sections);
            this.titles.addAll(doc.titles());
            this.keywords.addAll(doc.dc_subject());
            for (String d: doc.dc_description()) this.descriptions.add(d);

            if (!(this.text instanceof ByteArrayOutputStream)) {
                this.text = new ByteArrayOutputStream();
            }
            InputStream textStream = doc.getTextStream();
            try {
               FileUtils.copy(textStream, (ByteArrayOutputStream) this.text);
            } finally {
               try {
                   if(textStream != null) {
                       /* textStream can be a FileInputStream : we must close it to ensure releasing system resource */
                       textStream.close();
                   }
               } catch(IOException e) {
                   ConcurrentLog.warn("DOCUMENT", "Could not close text input stream");
               }
            }

            this.anchors.addAll(doc.getAnchors());
            this.rss.putAll(doc.getRSS());
            this.images.putAll(doc.getImages());
        }
    }
    
    /**
     * @return links to icons that belongs to the document (mapped by absolute URL)
     */
    public Map<DigestURL, IconEntry> getIcons() {
        return icons;
    }
    
    /**
     * Set links to icons that belongs to the document (mapped by absolute URL)
     * @param icons
     */
    public void setIcons(final Map<DigestURL, IconEntry> icons) {
       /* Better to ensure now icons property will not be null */
       if(icons != null) {
           this.icons = icons;    
       } else {
           this.icons = new HashMap<>();
       }
    }
    
    /**
     * @return URLs of linked data item types/classes referenced by the document (for example in
     * HTML with standard annotations such as RDFa, microdata, microformats or
     * JSON-LD)
     */
    public Set<DigestURL> getLinkedDataTypes() {
        return this.linkedDataTypes;
    }
    
    /**
     * @return URLs of linked data item types/classes referenced by the document
     */
    public void setLinkedDataTypes(final Set<DigestURL> linkedDataTypes) {
       if(linkedDataTypes != null) {
           /* Ensure non null property */
           this.linkedDataTypes = linkedDataTypes;
       } else {
           this.linkedDataTypes.clear();
       }
    }
    

    public int inboundLinkNofollowCount() {
        if (this.inboundlinks == null) resortLinks();
        if (this.inboundlinks == null) return 0;
        int c = 0;
        for (final String tag: this.inboundlinks.values()) {
            if (tag.contains("nofollow")) c++;
        }
        return c;
    }

    public int outboundLinkNofollowCount() {
        if (this.outboundlinks == null) resortLinks();
        if (this.outboundlinks == null) return 0;
        int c = 0;
        for (final String tag: this.outboundlinks.values()) {
            if (tag.contains("nofollow")) c++;
        }
        return c;
    }

    public LinkedHashMap<DigestURL, String> inboundLinks() {
        if (this.inboundlinks == null) resortLinks();
        return (this.inboundlinks == null) ? null : this.inboundlinks;
    }

    public LinkedHashMap<DigestURL, String> outboundLinks() {
        if (this.outboundlinks == null) resortLinks();
        return (this.outboundlinks == null) ? null : this.outboundlinks;
    }

    public boolean indexingDenied() {
        return this.indexingDenied;
    }

    public void setIndexingDenied(boolean indexingDenied) {
        this.indexingDenied = indexingDenied;
    }

    public void setDepth(int depth) {
        this.crawldepth = depth;
    }
    
    public int getDepth() {
        return this.crawldepth;
    }
    
    public void writeXML(final Writer os) throws IOException {
        os.write("<record>\n");
        final String title = dc_title();
        if (title != null && title.length() > 0) os.write("<dc:title><![CDATA[" + title + "]]></dc:title>\n");
        os.write("<dc:identifier>" + dc_identifier() + "</dc:identifier>\n");
        final String creator = dc_creator();
        if (creator != null && creator.length() > 0) os.write("<dc:creator><![CDATA[" + creator + "]]></dc:creator>\n");
        final String publisher = dc_publisher();
        if (publisher != null && publisher.length() > 0) os.write("<dc:publisher><![CDATA[" + publisher + "]]></dc:publisher>\n");
        final String subject = this.dc_subject(';');
        if (subject != null && subject.length() > 0) os.write("<dc:subject><![CDATA[" + subject + "]]></dc:subject>\n");
        if (this.text != null) {
            os.write("<dc:description><![CDATA[");
            os.write(getTextString());
            os.write("]]></dc:description>\n");
        }
        final String language = dc_language();
        if (language != null && language.length() > 0) os.write("<dc:language>" + dc_language() + "</dc:language>\n");
        os.write("<dc:date>" + ISO8601Formatter.FORMATTER.format(getLastModified()) + "</dc:date>\n");
        if (this.lon != 0.0 && this.lat != 0.0) os.write("<geo:Point><geo:long>" + this.lon +"</geo:long><geo:lat>" + this.lat + "</geo:lat></geo:Point>\n");
        os.write("</record>\n");
    }

    @Override
    public String toString() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            final Writer osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
            writeXML(osw);
            osw.close();
            return UTF8.String(baos.toByteArray());
        } catch (final UnsupportedEncodingException e1) {
            return "";
        } catch (final IOException e) {
            return "";
        }
    }

    public synchronized void close() {
        if (this.text == null) return;

        // try close the output stream
        if (this.text instanceof InputStream) try {
            ((InputStream) this.text).close();
        } catch (final Exception e) {} finally {
            this.text = null;
        }

        // delete the temp file
        if (this.text instanceof File) try {
            FileUtils.deletedelete((File) this.text);
        } catch (final Exception e) {} finally {
            this.text = null;
        }
    }

    /**
     * merge documents: a helper method for all parsers that return multiple documents.
     * Note : when docs contains more than one item, eventual icons in each docs are not merged in result doc, 
     * as their scope is limited to only one document.
     * @param location url of merged document
     * @param globalMime Mime type of merged document
     * @param docs documents to merge
     * @return document resulting of merge, or original document when docs contains only one item.
     */
    public static Document mergeDocuments(final DigestURL location, final String globalMime, final Document[] docs) {
        if (docs == null || docs.length == 0) return null;
        if (docs.length == 1) return docs[0];

        long docTextLength = 0;
        final ByteBuffer         content       = new ByteBuffer();
        final StringBuilder      authors       = new StringBuilder(80);
        final StringBuilder      publishers    = new StringBuilder(80);
        final StringBuilder      subjects      = new StringBuilder(80);
        final List<String>       descriptions  = new ArrayList<>();
        final Collection<String> titles        = new LinkedHashSet<>();
        final Collection<String> sectionTitles = new LinkedHashSet<>();
        final List<AnchorURL>    anchors       = new ArrayList<>();
        final LinkedHashMap<DigestURL, String> rss = new LinkedHashMap<>();
        final LinkedHashMap<DigestURL, ImageEntry> images = new LinkedHashMap<>();
        final Set<String> languages = new HashSet<>();
        double lon = 0.0d, lat = 0.0d;
        boolean indexingDenied = false;
        boolean partiallyParsed = false;
        Date date = null;
        String charset = null;

        int mindepth = 999;
        for (final Document doc: docs) {

            if (doc == null) continue;

            if (charset == null) charset = doc.charset; // TODO: uses this charset for merged content

            final String author = doc.dc_creator();
            if (author.length() > 0) {
                if (authors.length() > 0) authors.append(",");
                authors.append(author);
            }

            final String publisher = doc.dc_publisher();
            if (publisher.length() > 0) {
                if (publishers.length() > 0) publishers.append(",");
                publishers.append(publisher);
            }

            final String subject = doc.dc_subject(',');
            if (subject.length() > 0) {
                if (subjects.length() > 0) subjects.append(",");
                subjects.append(subject);
            }

            titles.addAll(doc.titles());
            Collections.addAll(sectionTitles, doc.getSectionTitles());
            Collections.addAll(descriptions, doc.dc_description());

            if (doc.getTextLength() > 0) {
                if (docTextLength > 0) content.write('\n');
                InputStream textStream = doc.getTextStream();
                try {
                    docTextLength += FileUtils.copy(textStream, content);
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                } finally {
                   try {
                       if(textStream != null) {
                           /* textStream can be a FileInputStream : we must close it to ensure releasing system resource */
                           textStream.close();
                       }
                    } catch (IOException e) {
                        ConcurrentLog.warn("DOCUMENT", "Could not close text input stream");
                    }
                }
            }
            anchors.addAll(doc.getAnchors());
            rss.putAll(doc.getRSS());
            images.putAll(doc.getImages());
            if (doc.lon() != 0.0 && doc.lat() != 0.0) { lon = doc.lon(); lat = doc.lat(); }
            if (date == null || doc.lastModified.after(date)) date = doc.lastModified; // set youngest lastModified date from doc collection
            
            if (doc.getDepth() < mindepth) mindepth = doc.getDepth();
            if (doc.dc_language() != null) languages.add(doc.dc_language());
            
            indexingDenied |= doc.indexingDenied;
            partiallyParsed |= doc.isPartiallyParsed();
        }

        // clean up parser data
        for (final Document doc: docs) {
            Object scraper = doc.getScraperObject();
            if (scraper instanceof ContentScraper) {
                final ContentScraper html = (ContentScraper) scraper;
                html.close();
                doc.scraperObject = null;
            }
        }

        // return consolidation
        ArrayList<String> titlesa = new ArrayList<String>();
        titlesa.addAll(titles);
        Document newDoc = new Document(
                location,
                globalMime,
                charset,
                null,
                languages,
                subjects.toString().split(" |,"),
                titlesa,
                authors.toString(),
                publishers.toString(),
                sectionTitles.toArray(new String[sectionTitles.size()]),
                descriptions,
                lon, lat,
                content.getBytes(),
                anchors,
                rss,
                images,
                indexingDenied,
                date);
        newDoc.setDepth(mindepth);
        newDoc.setPartiallyParsed(partiallyParsed);
        return newDoc;
    }

    public final static String CANONICAL_MARKER = "canonical";
    public final static String IFRAME_MARKER = "iframe";
    public final static String FRAME_MARKER = "frame";
    public final static String EMBED_MARKER = "embed";

    public static Map<AnchorURL, String> getHyperlinks(final Document[] documents, boolean includeNofollow) {
        final Map<AnchorURL, String> result = new HashMap<>();
        for (final Document d: documents) {
            if (includeNofollow) {
                result.putAll(d.getHyperlinks());
            } else {
                for (Map.Entry<AnchorURL, String> entry: d.getHyperlinks().entrySet()) {
                    if (!entry.getKey().attachedNofollow()) result.put(entry.getKey(), entry.getValue());
                }
            }
            final Object scraper = d.getScraperObject();
            if (scraper instanceof ContentScraper) {
                final ContentScraper html = (ContentScraper) scraper;
                String refresh = html.getRefreshPath();
                if (refresh != null && refresh.length() > 0) try {result.put(new AnchorURL(refresh), "refresh");} catch (final MalformedURLException e) {}
                AnchorURL canonical = html.getCanonical();
                if (canonical != null) {
                    result.put(canonical, CANONICAL_MARKER);
                }
                for (AnchorURL u: html.getFrames()) result.put(u, FRAME_MARKER);
                for (AnchorURL u: html.getIFrames()) result.put(u, IFRAME_MARKER);
                for (AnchorURL u: html.getEmbeds().keySet()) result.put(u, EMBED_MARKER);
            }
        }
        return result;
    }

    public static Map<DigestURL, String> getImagelinks(final Document[] documents) {
        final Map<DigestURL, String> result = new HashMap<DigestURL, String>();
        for (final Document d: documents) {
            for (final ImageEntry imageReference : d.getImages().values()) {
                // construct a image name which contains the document title to enhance the search process for images
                result.put(imageReference.url(), description(d, imageReference.alt()));
            }
        }
        return result;
    }

    public static Map<DigestURL, String> getAudiolinks(final Document[] documents) {
        final Map<DigestURL, String> result = new HashMap<DigestURL, String>();
        for (final Document d: documents) {
            for (Map.Entry<AnchorURL, String> e: d.audiolinks.entrySet()) {
                result.put(e.getKey(), description(d, e.getValue()));
            }
        }
        return result;
    }

    public static Map<DigestURL, String> getVideolinks(final Document[] documents) {
        final Map<DigestURL, String> result = new HashMap<DigestURL, String>();
        for (final Document d: documents) {
            for (Map.Entry<AnchorURL, String> e: d.videolinks.entrySet()) {
                result.put(e.getKey(), description(d, e.getValue()));
            }
        }
        return result;
    }

    public static Map<DigestURL, String> getApplinks(final Document[] documents) {
        final Map<DigestURL, String> result = new HashMap<DigestURL, String>();
        for (final Document d: documents) {
            for (Map.Entry<AnchorURL, String> e: d.applinks.entrySet()) {
                result.put(e.getKey(), description(d, e.getValue()));
            }
        }
        return result;
    }

    private static final String description(Document d, String tagname) {
        if (tagname == null || tagname.isEmpty()) {
            tagname = d.source.toTokens();
        }
        StringBuilder sb = new StringBuilder(60);
        sb.append(d.dc_title());
        if (d.dc_description().length > 0) {
            if (!d.dc_description()[0].equals(d.dc_title()) && sb.length() < Request.descrLength - tagname.length()) {
                sb.append(' ');
                sb.append(d.dc_description()[0]);
            }
        }
        if (sb.length() < Request.descrLength - tagname.length()) {
            sb.append(' ');
            sb.append(d.dc_subject(','));
        }
        if (tagname.length() > 0) {
            if (sb.length() > Request.descrLength - tagname.length() - 3) {
                // cut this off because otherwise the tagname is lost.
                if (tagname.length() > Request.descrLength) { // but in rare case tagname could be extreme long
                    sb.setLength(0);
                    sb.append(tagname.substring(0, Request.descrLength));
                } else {
                    sb.setLength(Request.descrLength - tagname.length());
                    sb.append(" - ");
                    sb.append(tagname);
                }
            } else {
                sb.append(" - ");
                sb.append(tagname);
            }
        }
        return sb.toString().trim();
    }

}
