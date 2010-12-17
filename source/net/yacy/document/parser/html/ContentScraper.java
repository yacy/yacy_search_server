// ContentScraper.java
// -----------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// Contains contributions by Marc Nause [MN]
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.document.parser.html;

import java.io.ByteArrayInputStream;
import java.io.CharArrayReader;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.swing.event.EventListenerList;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.parser.htmlParser;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.ISO639;


public class ContentScraper extends AbstractScraper implements Scraper {

    // statics: for initialization of the HTMLFilterAbstractScraper
    private static final HashSet<String> linkTags0 = new HashSet<String>(9,0.99f);
    private static final HashSet<String> linkTags1 = new HashSet<String>(7,0.99f);

    // all these tags must be given in lowercase, because the tags from the files are compared in lowercase
    static {
        linkTags0.add("html");      // scraped as tag 0 to get attached properties like 'lang'
        linkTags0.add("img");
        linkTags0.add("base");
        linkTags0.add("frame");
        linkTags0.add("meta");
        linkTags0.add("area");
        linkTags0.add("link");
        linkTags0.add("embed");     //added by [MN]
        linkTags0.add("param");     //added by [MN]

        linkTags1.add("a");
        linkTags1.add("h1");
        linkTags1.add("h2");
        linkTags1.add("h3");
        linkTags1.add("h4");
        linkTags1.add("title");
    }

    // class variables: collectors for links
    private HashMap<MultiProtocolURI, String> rss;
    private HashMap<MultiProtocolURI, String> anchors;
    private HashMap<MultiProtocolURI, ImageEntry> images; // urlhash/image relation
    private final HashMap<String, String> metas;
    private String title;
    //private String headline;
    private List<String>[] headlines;
    private CharBuffer content;
    private final EventListenerList htmlFilterEventListeners;
    
    /**
     * {@link MultiProtocolURI} to the favicon that belongs to the document
     */
    private MultiProtocolURI favicon;
    
    /**
     * The document root {@link MultiProtocolURI} 
     */
    private MultiProtocolURI root;

    @SuppressWarnings("unchecked")
    public ContentScraper(final MultiProtocolURI root) {
        // the root value here will not be used to load the resource.
        // it is only the reference for relative links
        super(linkTags0, linkTags1);
        this.root = root;
        this.rss = new HashMap<MultiProtocolURI, String>();
        this.anchors = new HashMap<MultiProtocolURI, String>();
        this.images = new HashMap<MultiProtocolURI, ImageEntry>();
        this.metas = new HashMap<String, String>();
        this.title = "";
        this.headlines = new ArrayList[4];
        for (int i = 0; i < 4; i++) headlines[i] = new ArrayList<String>();
        this.content = new CharBuffer(1024);
        this.htmlFilterEventListeners = new EventListenerList();
    }
    
    public final static boolean punctuation(final char c) {
        return c == '.' || c == '!' || c == '?';
    }
    
    public void scrapeText(final char[] newtext, final String insideTag) {
        // System.out.println("SCRAPE: " + new String(newtext));
        String b = cleanLine(super.stripAll(newtext));
        if ((insideTag != null) && (!(insideTag.equals("a")))) {
            // texts inside tags sometimes have no punctuation at the line end
            // this is bad for the text semantics, because it is not possible for the
            // condenser to distinguish headlines from text beginnings.
            // to make it easier for the condenser, a dot ('.') is appended in case that
            // no punctuation is part of the newtext line
            if ((b.length() != 0) && (!(punctuation(b.charAt(b.length() - 1))))) b = b + '.';
            //System.out.println("*** Appended dot: " + b.toString());
        }
        // find http links inside text
        int p, q, s = 0;
        String u;
        MultiProtocolURI url;
        while (s < b.length()) {
            p = Math.min(find(b, "smb://", s), Math.min(find(b, "ftp://", s), Math.min(find(b, "http://", s), find(b, "https://", s))));
            if (p == Integer.MAX_VALUE) break;
            q = b.indexOf(" ", p + 1);
            u = b.substring(p, q < 0 ? b.length() : q);
            if (u.endsWith(".")) u = u.substring(0, u.length() - 1); // remove the '.' that was appended above
            s = p + 1;
            try {
                url = new MultiProtocolURI(u);
                anchors.put(url, u);
                continue;
            } catch (MalformedURLException e) {}
        }
        // append string to content
        if (b.length() != 0) content.append(b).append(32);
    }

    private static final int find(final String s, final String m, int start) {
        int p = s.indexOf(m, start);
        return (p < 0) ? Integer.MAX_VALUE : p;
    }
    
    private MultiProtocolURI absolutePath(final String relativePath) {
        try {
            return MultiProtocolURI.newURL(root, relativePath);
        } catch (final Exception e) {
            return null;
        }
    }

    public void scrapeTag0(final String tagname, final Properties tagopts) {
        if (tagname.equalsIgnoreCase("img")) {
            try {
                final int width = Integer.parseInt(tagopts.getProperty("width", "-1"));
                final int height = Integer.parseInt(tagopts.getProperty("height", "-1"));
                if (width > 15 && height > 15) {
                    final float ratio = (float) Math.min(width, height) / Math.max(width, height);
                    if (ratio > 0.4) {
                        final MultiProtocolURI url = absolutePath(tagopts.getProperty("src", ""));
                        final ImageEntry ie = new ImageEntry(url, tagopts.getProperty("alt", ""), width, height, -1);
                        addImage(images, ie);
                    }
// i think that real pictures have witdth & height tags - thq
//                } else if (width < 0 && height < 0) { // add or to ignore !?
//                    final yacyURL url = absolutePath(tagopts.getProperty("src", ""));
//                    final htmlFilterImageEntry ie = new htmlFilterImageEntry(url, tagopts.getProperty("alt", ""), width, height);
//                    addImage(images, ie);
                }
            } catch (final NumberFormatException e) {}
        }
        if (tagname.equalsIgnoreCase("base")) try {
            root = new MultiProtocolURI(tagopts.getProperty("href", ""));
        } catch (final MalformedURLException e) {}
        if (tagname.equalsIgnoreCase("frame")) {
            anchors.put(absolutePath(tagopts.getProperty("src", "")), tagopts.getProperty("name",""));
        }
        if (tagname.equalsIgnoreCase("meta")) {
            String name = tagopts.getProperty("name", "");
            if (name.length() > 0) {
                metas.put(name.toLowerCase(), CharacterCoding.html2unicode(tagopts.getProperty("content","")));
            } else {
                name = tagopts.getProperty("http-equiv", "");
                if (name.length() > 0) {
                    metas.put(name.toLowerCase(), CharacterCoding.html2unicode(tagopts.getProperty("content","")));
                }
            }
        }
        if (tagname.equalsIgnoreCase("area")) {
            final String areatitle = cleanLine(tagopts.getProperty("title",""));
            //String alt   = tagopts.getProperty("alt","");
            final String href  = tagopts.getProperty("href", "");
            if (href.length() > 0) anchors.put(absolutePath(href), areatitle);
        }
        if (tagname.equalsIgnoreCase("link")) {
            final MultiProtocolURI newLink = absolutePath(tagopts.getProperty("href", ""));

            if (newLink != null) {
                final String rel = tagopts.getProperty("rel", "");
                final String linktitle = tagopts.getProperty("title", "");
                final String type = tagopts.getProperty("type", "");

                if (rel.equalsIgnoreCase("shortcut icon")) {
                    final ImageEntry ie = new ImageEntry(newLink, linktitle, -1, -1, -1);
                    images.put(ie.url(), ie);    
                    this.favicon = newLink;
                } else if (rel.equalsIgnoreCase("alternate") && type.equalsIgnoreCase("application/rss+xml")) {
                    rss.put(newLink, linktitle);
                } else if (!rel.equalsIgnoreCase("stylesheet") && !rel.equalsIgnoreCase("alternate stylesheet")) {
                    anchors.put(newLink, linktitle);
                }
            }
        }
        //start contrib [MN]
        if (tagname.equalsIgnoreCase("embed")) {
            anchors.put(absolutePath(tagopts.getProperty("src", "")), tagopts.getProperty("name",""));
        }
        if (tagname.equalsIgnoreCase("param")) {
            final String name = tagopts.getProperty("name", "");
            if (name.equalsIgnoreCase("movie")) {
                anchors.put(absolutePath(tagopts.getProperty("value", "")),name);
            }
        }
        //end contrib [MN]

        // fire event
        fireScrapeTag0(tagname, tagopts);
    }
    
    public void scrapeTag1(final String tagname, final Properties tagopts, final char[] text) {
        // System.out.println("ScrapeTag1: tagname=" + tagname + ", opts=" + tagopts.toString() + ", text=" + new String(text));
        if (tagname.equalsIgnoreCase("a") && text.length < 2048) {
            final String href = tagopts.getProperty("href", "");
            MultiProtocolURI url;
            if ((href.length() > 0) && ((url = absolutePath(href)) != null)) {
                final String f = url.getFile();
                final int p = f.lastIndexOf('.');
                final String type = (p < 0) ? "" : f.substring(p + 1);
                if (type.equals("png") || type.equals("gif") || type.equals("jpg") || type.equals("jpeg") || type.equals("tiff") || type.equals("tif")) {
                    // special handling of such urls: put them to the image urls
                    final ImageEntry ie = new ImageEntry(url, recursiveParse(text), -1, -1, -1);
                    addImage(images, ie);
                } else {
                    anchors.put(url, recursiveParse(text));
                }
            }
        }
        String h;
        if ((tagname.equalsIgnoreCase("h1")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) headlines[0].add(h);
        }
        if ((tagname.equalsIgnoreCase("h2")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) headlines[1].add(h);
        }
        if ((tagname.equalsIgnoreCase("h3")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) headlines[2].add(h);
        }
        if ((tagname.equalsIgnoreCase("h4")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) headlines[3].add(h);
        }
        if ((tagname.equalsIgnoreCase("title")) && (text.length < 1024)) {
            title = recursiveParse(text);
        }

        // fire event
        fireScrapeTag1(tagname, tagopts, text);
    }

    private String recursiveParse(char[] inlineHtml) {
        if (inlineHtml.length < 14) return cleanLine(super.stripAll(inlineHtml));
        
        // start a new scraper to parse links inside this text
        // parsing the content
        final ContentScraper scraper = new ContentScraper(this.root);        
        final TransformerWriter writer = new TransformerWriter(null, null, scraper, null, false);
        try {
            FileUtils.copy(new CharArrayReader(inlineHtml), writer);
            writer.close();
        } catch (IOException e) {
            Log.logException(e);
            return cleanLine(super.stripAll(inlineHtml));
        }
        this.anchors.putAll(scraper.getAnchors());
        this.images.putAll(scraper.images);
        
        return cleanLine(super.stripAll(scraper.content.getChars()));
    }
    
    private final static String cleanLine(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        char c, l = ' ';
        for (int i = 0; i < s.length(); i++) {
            c = s.charAt(i);
            if (c < ' ') c = ' ';
            if (c == ' ') {
                if (l != ' ') sb.append(c);
            } else {
                sb.append(c);
            }
            l = c;
        }
        
        // return result
        return sb.toString().trim();
    }
    
    public String getTitle() {
        // construct a title string, even if the document has no title
        
        // some documents have a title tag as meta tag
        String s = metas.get("title");
        
        // try to construct the title with the content of the title tag
        if (title.length() > 0) {
            if (s == null) {
                return title;
            }
            if ((title.compareToIgnoreCase(s) == 0) || (title.indexOf(s) >= 0)) return s;
            return title + ": " + s;
        }
        if (s != null) {
            return s;
        }
        
        // otherwise take any headline
        for (int i = 0; i < 4; i++) {
            if (!headlines[i].isEmpty()) return headlines[i].get(0);
        }
        
        // take description tag
        s = getDescription();
        if (s.length() > 0) return s;
        
        // extract headline from file name
        return MultiProtocolURI.unescape(root.getFileName()); 
    }
    
    public String[] getHeadlines(final int i) {
        assert ((i >= 1) && (i <= 4));
        final String[] s = new String[headlines[i - 1].size()];
        for (int j = 0; j < headlines[i - 1].size(); j++) s[j] = headlines[i - 1].get(j);
        return s;
    }
    
    public byte[] getText() {
        return this.getText("UTF-8");
    }
    
    public byte[] getText(final String charSet) {
        try {
            return content.getBytes(charSet);
        } catch (final UnsupportedEncodingException e) {
            return content.getBytes();
        }
    }

    public Map<MultiProtocolURI, String> getAnchors() {
        // returns a url (String) / name (String) relation
        return anchors;
    }

    public Map<MultiProtocolURI, String> getRSS() {
        // returns a url (String) / name (String) relation
        return rss;
    }

    /**
     * get all images
     * @return a map of <urlhash, ImageEntry>
     */
    public HashMap<MultiProtocolURI, ImageEntry> getImages() {
        // this resturns a String(absolute url)/htmlFilterImageEntry - relation
        return images;
    }

    public Map<String, String> getMetas() {
        return metas;
    }
    
    /**
     * @return the {@link MultiProtocolURI} to the favicon that belongs to the document
     */    
    public MultiProtocolURI getFavicon() {
        return this.favicon;
    }

    /*
    DC in html example:
    <meta name="DC.title" lang="en" content="Expressing Dublin Core in HTML/XHTML meta and link elements" />
    <meta name="DC.creator" content="Andy Powell, UKOLN, University of Bath" />
    <meta name="DC.identifier" scheme="DCTERMS.URI" content="http://dublincore.org/documents/dcq-html/" />
    <meta name="DC.format" scheme="DCTERMS.IMT" content="text/html" />
    <meta name="DC.type" scheme="DCTERMS.DCMIType" content="Text" />
    */
    
    public boolean indexingDenied() {
        String s = metas.get("robots");
        if (s == null) return false;
        if (s.indexOf("noindex") >= 0) return true;
        return false;
    }
    
    public String getDescription() {
        String s = metas.get("description");
        if (s == null) s = metas.get("dc.description");
        if (s == null) return "";
        return s;
    }
    
    public String getContentType() {
        final String s = metas.get("content-type");
        if (s == null) return "";
        return s;
    }
    
    public String getAuthor() {
        String s = metas.get("author");
        if (s == null) s = metas.get("dc.creator");
        if (s == null) return "";
        return s;
    }
    
    public String getPublisher() {
        String s = metas.get("copyright");
        if (s == null) s = metas.get("dc.publisher");
        if (s == null) return "";
        return s;
    }
    
    public HashSet<String> getContentLanguages() {
        // i.e. <meta name="DC.language" content="en" scheme="DCTERMS.RFC3066">
        // or <meta http-equiv="content-language" content="en">
        String s = metas.get("content-language");
        if (s == null) s = metas.get("dc.language");
        if (s == null) return null;
        HashSet<String> hs = new HashSet<String>();
        String[] cl = s.split(" |,");
        int p;
        for (int i = 0; i < cl.length; i++) {
            cl[i] = cl[i].toLowerCase();
            p = cl[i].indexOf('-');
            if (p > 0) cl[i] = cl[i].substring(0, p);
            if (ISO639.exists(cl[i])) hs.add(cl[i]);
        }
        if (hs.isEmpty()) return null;
        return hs;
    }
    
    public String[] getKeywords() {
        String s = metas.get("keywords");
        if (s == null) s = metas.get("dc.description");
        if (s == null) s = "";
        if (s.length() == 0) {
            return MultiProtocolURI.splitpattern.split(getTitle().toLowerCase());
        }
        if (s.contains(",")) return s.split(" |,");
        if (s.contains(";")) return s.split(" |;");
        return s.split("\\s");
    }
    
    public int getRefreshSeconds() {
        final String s = metas.get("refresh");
        if (s == null) return 9999;
        try {
            final int pos = s.indexOf(';');
            if (pos < 0) return 9999;
            final int i = Integer.parseInt(s.substring(0, pos));
            return i;
        } catch (final NumberFormatException e) {
            return 9999;
        }
    }

    public String getRefreshPath() {
        String s = metas.get("refresh");
        if (s == null) return "";
        
        final int pos = s.indexOf(';');
        if (pos < 0) return "";
        s = s.substring(pos + 1);
        if (s.toLowerCase().startsWith("url=")) return s.substring(4).trim();
        return "";
    }

    /*
     *  (non-Javadoc)
     * @see de.anomic.htmlFilter.htmlFilterScraper#close()
     */
    @Override
    public void close() {
        // free resources
        super.close();
        anchors = null;
        images = null;
        title = null;
        headlines = null;
        content = null;
        root = null;
    }

    public void print() {
        System.out.println("TITLE    :" + title);
        for (int i = 0; i < 4; i++) {
            System.out.println("HEADLINE" + i + ":" + headlines[i].toString());
        }
        System.out.println("ANCHORS  :" + anchors.toString());
        System.out.println("IMAGES   :" + images.toString());
        System.out.println("METAS    :" + metas.toString());
        System.out.println("TEXT     :" + content.toString());
    }

    public void registerHtmlFilterEventListener(final ScraperListener listener) {
        if (listener != null) {
            this.htmlFilterEventListeners.add(ScraperListener.class, listener);
        }        
    }

    public void deregisterHtmlFilterEventListener(final ScraperListener listener) {
        if (listener != null) {
            this.htmlFilterEventListeners.remove(ScraperListener.class, listener);
        }        
    }
    
    private void fireScrapeTag0(final String tagname, final Properties tagopts) {
        final Object[] listeners = this.htmlFilterEventListeners.getListenerList();
        for (int i=0; i<listeners.length; i+=2) {
            if (listeners[i]==ScraperListener.class) {
                    ((ScraperListener)listeners[i+1]).scrapeTag0(tagname, tagopts);
            }
        }
    }    
    
    private void fireScrapeTag1(final String tagname, final Properties tagopts, final char[] text) {
        final Object[] listeners = this.htmlFilterEventListeners.getListenerList();
        for (int i=0; i<listeners.length; i+=2) {
            if (listeners[i]==ScraperListener.class) {
                    ((ScraperListener)listeners[i+1]).scrapeTag1(tagname, tagopts, text);
            }
        }
    }
    
    public static ContentScraper parseResource(final File file) throws IOException {
        // load page
        final byte[] page = FileUtils.read(file);
        if (page == null) throw new IOException("no content in file " + file.toString());
        
        // scrape document to look up charset
        final ScraperInputStream htmlFilter = new ScraperInputStream(new ByteArrayInputStream(page),"UTF-8", new MultiProtocolURI("http://localhost"),null,false);
        String charset = htmlParser.patchCharsetEncoding(htmlFilter.detectCharset());
        if(charset == null)
               charset = Charset.defaultCharset().toString();
        
        // scrape content
        final ContentScraper scraper = new ContentScraper(new MultiProtocolURI("http://localhost"));
        final Writer writer = new TransformerWriter(null, null, scraper, null, false);
        FileUtils.copy(new ByteArrayInputStream(page), writer, Charset.forName(charset));
        
        return scraper;
    }
    
    public static void addAllImages(final HashMap<MultiProtocolURI, ImageEntry> a, final HashMap<MultiProtocolURI, ImageEntry> b) {
        final Iterator<Map.Entry<MultiProtocolURI, ImageEntry>> i = b.entrySet().iterator();
        Map.Entry<MultiProtocolURI, ImageEntry> ie;
        while (i.hasNext()) {
            ie = i.next();
            addImage(a, ie.getValue());
        }
    }
    
    public static void addImage(final HashMap<MultiProtocolURI, ImageEntry> a, final ImageEntry ie) {
        if (a.containsKey(ie.url())) {
            // in case of a collision, take that image that has the better image size tags
            if ((ie.height() > 0) && (ie.width() > 0)) a.put(ie.url(), ie);
        } else {
            a.put(ie.url(), ie);
        }
    }
    
}

