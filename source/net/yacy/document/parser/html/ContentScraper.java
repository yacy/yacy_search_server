// ContentScraper.java
// -----------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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
import java.util.Set;
import java.util.regex.Pattern;

import javax.swing.event.EventListenerList;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.storage.ClusteredScoreMap;
import net.yacy.document.SentenceReader;
import net.yacy.document.parser.htmlParser;
import net.yacy.document.parser.html.Evaluation.Element;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.ISO639;


public class ContentScraper extends AbstractScraper implements Scraper {

    private final char degree = '\u00B0';
    private final char[] minuteCharsHTML = "&#039;".toCharArray();
    
    // statics: for initialization of the HTMLFilterAbstractScraper
    private static final Set<String> linkTags0 = new HashSet<String>(9,0.99f);
    private static final Set<String> linkTags1 = new HashSet<String>(7,0.99f);
    
    public enum TagType {
        singleton, pair;
    }
    
    public enum Tag {
        html(TagType.singleton), // scraped as singleton to get attached properties like 'lang'
        body(TagType.singleton), // scraped as singleton to get attached properties like 'class'
        div(TagType.singleton),  // scraped as singleton to get attached properties like 'id'
        img(TagType.singleton),
        base(TagType.singleton),
        frame(TagType.singleton),
        meta(TagType.singleton),
        area(TagType.singleton),
        link(TagType.singleton),
        embed(TagType.singleton), //added by [MN]
        param(TagType.singleton), //added by [MN]

        a(TagType.pair),
        h1(TagType.pair),
        h2(TagType.pair),
        h3(TagType.pair),
        h4(TagType.pair),
        h5(TagType.pair),
        h6(TagType.pair),
        title(TagType.pair),
        b(TagType.pair),
        strong(TagType.pair),
        i(TagType.pair),
        li(TagType.pair),
        iframe(TagType.pair),
        script(TagType.pair);

        public TagType type;
        private Tag(TagType type) {
            this.type = type;
        }
    }
    
    // all these tags must be given in lowercase, because the tags from the files are compared in lowercase
    static {
        for (Tag tag: Tag.values()) {
            if (tag.type == TagType.singleton) linkTags0.add(tag.name());
            if (tag.type == TagType.pair) linkTags1.add(tag.name());
        }
        //<iframe src="../../../index.htm" name="SELFHTML_in_a_box" width="90%" height="400">
    }

    // class variables: collectors for links
    private Map<MultiProtocolURI, Properties> anchors;
    private Map<MultiProtocolURI, String> rss, css;
    private Set<MultiProtocolURI> script, frames, iframes;
    private Map<MultiProtocolURI, ImageEntry> images; // urlhash/image relation
    private final Map<String, String> metas;
    private String title;
    //private String headline;
    private List<String>[] headlines;
    private ClusteredScoreMap<String> bold, italic;
    private List<String> li;
    private CharBuffer content;
    private final EventListenerList htmlFilterEventListeners;
    private float lon, lat;
    
    /**
     * {@link MultiProtocolURI} to the favicon that belongs to the document
     */
    private MultiProtocolURI favicon;
    
    /**
     * The document root {@link MultiProtocolURI} 
     */
    private MultiProtocolURI root;
    
    /**
     * evaluation scores: count appearance of specific attributes
     */
    private Evaluation.Scores evaluationScores;

    @SuppressWarnings("unchecked")
    public ContentScraper(final MultiProtocolURI root) {
        // the root value here will not be used to load the resource.
        // it is only the reference for relative links
        super(linkTags0, linkTags1);
        this.root = root;
        this.evaluationScores = new Evaluation.Scores();
        this.rss = new HashMap<MultiProtocolURI, String>();
        this.css = new HashMap<MultiProtocolURI, String>();
        this.anchors = new HashMap<MultiProtocolURI, Properties>();
        this.images = new HashMap<MultiProtocolURI, ImageEntry>();
        this.frames = new HashSet<MultiProtocolURI>();
        this.iframes = new HashSet<MultiProtocolURI>();
        this.metas = new HashMap<String, String>();
        this.script = new HashSet<MultiProtocolURI>();
        this.title = "";
        this.headlines = new ArrayList[6];
        for (int i = 0; i < this.headlines.length; i++) headlines[i] = new ArrayList<String>();
        this.bold = new ClusteredScoreMap<String>();
        this.italic = new ClusteredScoreMap<String>();
        this.li = new ArrayList<String>();
        this.content = new CharBuffer(1024);
        this.htmlFilterEventListeners = new EventListenerList();
        this.lon = 0.0f;
        this.lat = 0.0f;
        Evaluation.match(Element.url, root.toNormalform(false, false), this.evaluationScores);
    }
    
    public void scrapeText(final char[] newtext, final String insideTag) {
        // System.out.println("SCRAPE: " + UTF8.String(newtext));
        int p, pl, q, s = 0;

        // match evaluation pattern
        Evaluation.match(Element.text, newtext, this.evaluationScores);
        
        // try to find location information in text
        // Opencaching:
        // <nobr>N 50o 05.453&#039;</nobr><nobr>E 008o 30.191&#039;</nobr>
        // N 52o 28.025 E 013o 20.299
        location: while (s < newtext.length) {
            pl = 1;
            p = CharBuffer.indexOf(newtext, s, degree);
            if (p < 0) {p = CharBuffer.indexOf(newtext, s, "&deg;".toCharArray()); if (p >= 0) pl = 5;}
            if (p < 0) break location;
            q = CharBuffer.indexOf(newtext, p + pl, minuteCharsHTML);
            if (q < 0) q = CharBuffer.indexOf(newtext, p + pl, "'".toCharArray());
            if (q < 0) q = CharBuffer.indexOf(newtext, p + pl, " E".toCharArray());
            if (q < 0) q = CharBuffer.indexOf(newtext, p + pl, " W".toCharArray());
            if (q < 0 && newtext.length - p == 7 + pl) q = newtext.length; 
            if (q < 0) break location;
            int r = p;
            while (r-- > 1) {
                if (newtext[r] == ' ') {
                    r--;
                    if (newtext[r] == 'N') {
                        this.lat =  Float.parseFloat(new String(newtext, r + 2, p - r - 2)) +
                                    Float.parseFloat(new String(newtext, p + pl + 1, q - p - pl - 1)) / 60.0f;
                        if (this.lon != 0.0f) break location;
                        s = q + 6;
                        continue location;
                    }
                    if (newtext[r] == 'S') {
                        this.lat = -Float.parseFloat(new String(newtext, r + 2, p - r - 2)) -
                                    Float.parseFloat(new String(newtext, p + pl + 1, q - p - pl - 1)) / 60.0f;
                        if (this.lon != 0.0f) break location;
                        s = q + 6;
                        continue location;
                    }
                    if (newtext[r] == 'E') {
                        this.lon =  Float.parseFloat(new String(newtext, r + 2, p - r - 2)) +
                                    Float.parseFloat(new String(newtext, p + pl + 1, q - p - pl - 1)) / 60.0f;
                        if (this.lat != 0.0f) break location;
                        s = q + 6;
                        continue location;
                    }
                    if (newtext[r] == 'W') {
                        this.lon = -Float.parseFloat(new String(newtext, r + 2, p - r - 2)) -
                                    Float.parseFloat(new String(newtext, p + 2, q - p - pl - 1)) / 60.0f;
                        if (this.lat != 0.0f) break location;
                        s = q + 6;
                        continue location;
                    }
                    break location;
                }
            }
            break location;
        }
        // find tags inside text
        String b = cleanLine(super.stripAllTags(newtext));
        if ((insideTag != null) && (!(insideTag.equals("a")))) {
            // texts inside tags sometimes have no punctuation at the line end
            // this is bad for the text semantics, because it is not possible for the
            // condenser to distinguish headlines from text beginnings.
            // to make it easier for the condenser, a dot ('.') is appended in case that
            // no punctuation is part of the newtext line
            if ((b.length() != 0) && (!(SentenceReader.punctuation(b.charAt(b.length() - 1))))) b = b + '.';
            //System.out.println("*** Appended dot: " + b.toString());
        }
        // find http links inside text
        s = 0;
        String u;
        MultiProtocolURI url;
        while (s < b.length()) {
            p = find(b, "://", s);
            if (p == Integer.MAX_VALUE) break;
            s = Math.max(0, p - 5);
            p = Math.min(find(b, "smb://", s), Math.min(find(b, "ftp://", s), Math.min(find(b, "http://", s), find(b, "https://", s))));
            if (p == Integer.MAX_VALUE) break;
            q = b.indexOf(" ", p + 1);
            u = b.substring(p, q < 0 ? b.length() : q);
            if (u.endsWith(".")) u = u.substring(0, u.length() - 1); // remove the '.' that was appended above
            s = p + 6;
            try {
                url = new MultiProtocolURI(u);
                anchors.put(url, new Properties());
                continue;
            } catch (MalformedURLException e) {}
        }
        // append string to content
        if (b.length() != 0) content.append(b).append(32);
    }

    private static final int find(final String s, final String m, final int start) {
        final int p = s.indexOf(m, start);
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
            String src = tagopts.getProperty("src", "");
            try {
                final int width = Integer.parseInt(tagopts.getProperty("width", "-1"));
                final int height = Integer.parseInt(tagopts.getProperty("height", "-1"));
                if (src.length() > 0) {
                    final MultiProtocolURI url = absolutePath(src);
                    if (url != null) {
                        final ImageEntry ie = new ImageEntry(url, tagopts.getProperty("alt", ""), width, height, -1);
                        addImage(images, ie);
                    }
                }
            } catch (final NumberFormatException e) {}
            Evaluation.match(Element.imgpath, src, this.evaluationScores);
        } else if(tagname.equalsIgnoreCase("base")) {
            try {
                root = new MultiProtocolURI(tagopts.getProperty("href", ""));
            } catch (final MalformedURLException e) {}
        } else if (tagname.equalsIgnoreCase("frame")) {
            anchors.put(absolutePath(tagopts.getProperty("src", "")), tagopts /* with property "name" */);
            frames.add(absolutePath(tagopts.getProperty("src", "")));
        } else if (tagname.equalsIgnoreCase("body")) {
            String c = tagopts.getProperty("class", "");
            Evaluation.match(Element.bodyclass, c, this.evaluationScores);
        } else if (tagname.equalsIgnoreCase("div")) {
            String id = tagopts.getProperty("id", "");
            Evaluation.match(Element.divid, id, this.evaluationScores);
        } else if (tagname.equalsIgnoreCase("meta")) {
            String name = tagopts.getProperty("name", "");
            String content = tagopts.getProperty("content","");
            if (name.length() > 0) {
                metas.put(name.toLowerCase(), CharacterCoding.html2unicode(content));
                if (name.equals("generator")) {
                    Evaluation.match(Element.metagenerator, content, this.evaluationScores);
                }
            } else {
                name = tagopts.getProperty("http-equiv", "");
                if (name.length() > 0) {
                    metas.put(name.toLowerCase(), CharacterCoding.html2unicode(content));
                }
            }
        } else if (tagname.equalsIgnoreCase("area")) {
            final String areatitle = cleanLine(tagopts.getProperty("title",""));
            //String alt   = tagopts.getProperty("alt","");
            final String href  = tagopts.getProperty("href", "");
            Properties p = new Properties(); p.put("name", areatitle);
            if (href.length() > 0) anchors.put(absolutePath(href), p);
        } else if (tagname.equalsIgnoreCase("link")) {
            String href = tagopts.getProperty("href", "");
            final MultiProtocolURI newLink = absolutePath(href);

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
                } else if (rel.equalsIgnoreCase("stylesheet") && type.equalsIgnoreCase("text/css")) {
                    css.put(newLink, rel);
                    Evaluation.match(Element.csspath, href, this.evaluationScores);
                } else if (!rel.equalsIgnoreCase("stylesheet") && !rel.equalsIgnoreCase("alternate stylesheet")) {
                    Properties p = new Properties(); p.put("name", linktitle);
                    anchors.put(newLink, p);
                }
            }
        } else if(tagname.equalsIgnoreCase("embed")) {
            anchors.put(absolutePath(tagopts.getProperty("src", "")), tagopts /* with property "name" */);
        } else if(tagname.equalsIgnoreCase("param")) {
            final String name = tagopts.getProperty("name", "");
            if (name.equalsIgnoreCase("movie")) {
                anchors.put(absolutePath(tagopts.getProperty("value", "")), tagopts /* with property "name" */);
            }
        }

        // fire event
        fireScrapeTag0(tagname, tagopts);
    }
    
    public void scrapeTag1(final String tagname, final Properties tagopts, final char[] text) {
        // System.out.println("ScrapeTag1: tagname=" + tagname + ", opts=" + tagopts.toString() + ", text=" + UTF8.String(text));
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
                    tagopts.put("name", recursiveParse(text));
                    anchors.put(url, tagopts);
                }
            }
            Evaluation.match(Element.apath, href, this.evaluationScores);
        }
        final String h;
        if ((tagname.equalsIgnoreCase("h1")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) headlines[0].add(h);
        } else if((tagname.equalsIgnoreCase("h2")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) headlines[1].add(h);
        } else if ((tagname.equalsIgnoreCase("h3")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) headlines[2].add(h);
        } else if ((tagname.equalsIgnoreCase("h4")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) headlines[3].add(h);
        } else if ((tagname.equalsIgnoreCase("h5")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) headlines[4].add(h);
        } else if ((tagname.equalsIgnoreCase("h6")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) headlines[5].add(h);
        } else if ((tagname.equalsIgnoreCase("title")) && (text.length < 1024)) {
            title = recursiveParse(text);
        } else if ((tagname.equalsIgnoreCase("b")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) bold.inc(h);
        } else if ((tagname.equalsIgnoreCase("strong")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) bold.inc(h);
        } else if ((tagname.equalsIgnoreCase("i")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) italic.inc(h);
        } else if ((tagname.equalsIgnoreCase("li")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) li.add(h);
        } else if (tagname.equalsIgnoreCase("iframe")) {
            String src = tagopts.getProperty("src", "");
            anchors.put(absolutePath(src), tagopts /* with property "name" */);
            iframes.add(absolutePath(src));
            Evaluation.match(Element.iframepath, src, this.evaluationScores);
        } else if (tagname.equalsIgnoreCase("script")) {
            String src = tagopts.getProperty("src", "");
            if (src.length() > 0) {
                script.add(absolutePath(src));
                Evaluation.match(Element.scriptpath, src, this.evaluationScores);
            } else {
                Evaluation.match(Element.scriptcode, text, this.evaluationScores);
            }
        }

        // fire event
        fireScrapeTag1(tagname, tagopts, text);
    }
    

    public void scrapeComment(final char[] comment) {
        Evaluation.match(Element.comment, comment, this.evaluationScores);
    }

    private String recursiveParse(final char[] inlineHtml) {
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
    
    private final static String cleanLine(final String s) {
        final StringBuilder sb = new StringBuilder(s.length());
        char l = ' ';
        for (char c : s.toCharArray()) {
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
        for (int i = 0; i < this.headlines.length; i++) {
            if (!this.headlines[i].isEmpty()) return this.headlines[i].get(0);
        }
        
        // take description tag
        s = getDescription();
        if (s.length() > 0) return s;
        
        // extract headline from file name
        return MultiProtocolURI.unescape(root.getFileName()); 
    }
    
    public String[] getHeadlines(final int i) {
        assert ((i >= 1) && (i <= this.headlines.length));
        return this.headlines[i - 1].toArray(new String[this.headlines[i - 1].size()]);
    }
    
    public String[] getBold() {
        List<String> a = new ArrayList<String>();
        Iterator<String> i = this.bold.keys(false);
        while (i.hasNext()) a.add(i.next());        
        return a.toArray(new String[a.size()]);
    }
    
    public String[] getBoldCount(String[] a) {
        String[] counter = new String[a.length];
        for (int i = 0; i < a.length; i++) counter[i] = Integer.toString(this.bold.get(a[i]));
        return counter;
    }
    
    public String[] getItalic() {
        List<String> a = new ArrayList<String>();
        Iterator<String> i = this.italic.keys(false);
        while (i.hasNext()) a.add(i.next());        
        return a.toArray(new String[a.size()]);
    }
    
    public String[] getItalicCount(String[] a) {
        String[] counter = new String[a.length];
        for (int i = 0; i < a.length; i++) counter[i] = Integer.toString(this.italic.get(a[i]));
        return counter;
    }
    
    public String[] getLi() {
        return this.li.toArray(new String[this.li.size()]);
    }
    
    public boolean containsFlash() {
        this.anchors = new HashMap<MultiProtocolURI, Properties>();
        String ext;
        for (MultiProtocolURI url: this.anchors.keySet()) {
            ext = url.getFileExtension();
            if (ext == null) continue;
            if (ext.equals("swf")) return true;
        }
        return false;
    }
    
    public byte[] getText() {
        try {
            return content.getBytes();
        } catch (final OutOfMemoryError e) {
            Log.logException(e);
            return new byte[0];
        }
    }

    public Map<MultiProtocolURI, Properties> getAnchors() {
        // returns a url (String) / name (String) relation
        return anchors;
    }

    public Map<MultiProtocolURI, String> getRSS() {
        // returns a url (String) / name (String) relation
        return rss;
    }

    public Map<MultiProtocolURI, String> getCSS() {
        // returns a url (String) / name (String) relation
        return css;
    }

    public Set<MultiProtocolURI> getFrames() {
        // returns a url (String) / name (String) relation
        return frames;
    }

    public Set<MultiProtocolURI> getIFrames() {
        // returns a url (String) / name (String) relation
        return iframes;
    }

    public Set<MultiProtocolURI> getScript() {
        return script;
    }

    /**
     * get all images
     * @return a map of <urlhash, ImageEntry>
     */
    public Map<MultiProtocolURI, ImageEntry> getImages() {
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

    private final static Pattern commaSepPattern = Pattern.compile(" |,");
    private final static Pattern semicSepPattern = Pattern.compile(" |;");
    
    public Set<String> getContentLanguages() {
        // i.e. <meta name="DC.language" content="en" scheme="DCTERMS.RFC3066">
        // or <meta http-equiv="content-language" content="en">
        String s = metas.get("content-language");
        if (s == null) s = metas.get("dc.language");
        if (s == null) return null;
        Set<String> hs = new HashSet<String>();
        String[] cl = commaSepPattern.split(s);
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
        if (s.contains(",")) return commaSepPattern.split(s);
        if (s.contains(";")) return semicSepPattern.split(s);
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

    // parse location
    // <meta NAME="ICBM" CONTENT="38.90551492, 1.454004505" />
    // <meta NAME="geo.position" CONTENT="38.90551492;1.454004505" />
    
    public float getLon() {
        if (this.lon != 0.0f) return this.lon;
        String s = metas.get("ICBM"); // InterContinental Ballistic Missile (abbrev. supposed to be a joke: http://www.jargon.net/jargonfile/i/ICBMaddress.html), see http://geourl.org/add.html#icbm
        if (s != null) {
            int p = s.indexOf(';');
            if (p < 0) p = s.indexOf(',');
            if (p < 0) p = s.indexOf(' ');
            if (p > 0) {
                this.lat = Float.parseFloat(s.substring(0, p).trim());
                this.lon = Float.parseFloat(s.substring(p + 1).trim());
            }
        }
        if (this.lon != 0.0f) return this.lon;
        s = metas.get("geo.position"); // http://geotags.com/geobot/add-tags.html
        if (s != null) {
            int p = s.indexOf(';');
            if (p < 0) p = s.indexOf(',');
            if (p < 0) p = s.indexOf(' ');
            if (p > 0) {
                this.lat = Float.parseFloat(s.substring(0, p).trim());
                this.lon = Float.parseFloat(s.substring(p + 1).trim());
            }
        }
        return this.lon;
    }
    
    public float getLat() {
        if (this.lat != 0.0f) return this.lat;
        getLon(); // parse with getLon() method which creates also the lat value
        return this.lat;
    }
    
    /**
     * produce all model names
     * @return a set of model names
     */
    public Set<String> getEvaluationModelNames() {
        return this.evaluationScores.getModelNames();
    }
    
    public String[] getEvaluationModelScoreNames(String modelName) {
        List<String> a = new ArrayList<String>();
        ClusteredScoreMap<String> scores = this.evaluationScores.getScores(modelName);
        if (scores != null) {
            Iterator<String> i = scores.keys(false);
            while (i.hasNext()) a.add(i.next());
        }
        return a.toArray(new String[a.size()]);
    }
    
    public String[] getEvaluationModelScoreCounts(String modelName, String[] a) {
        ClusteredScoreMap<String> scores = this.evaluationScores.getScores(modelName);
        String[] counter = new String[a.length];
        if (scores != null) {
            for (int i = 0; i < a.length; i++) counter[i] = Integer.toString(scores.get(a[i]));
        }
        return counter;
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
    
    public static void addAllImages(final Map<MultiProtocolURI, ImageEntry> a, final Map<MultiProtocolURI, ImageEntry> b) {
        final Iterator<Map.Entry<MultiProtocolURI, ImageEntry>> i = b.entrySet().iterator();
        Map.Entry<MultiProtocolURI, ImageEntry> ie;
        while (i.hasNext()) {
            ie = i.next();
            addImage(a, ie.getValue());
        }
    }
    
    public static void addImage(final Map<MultiProtocolURI, ImageEntry> a, final ImageEntry ie) {
        if (a.containsKey(ie.url())) {
            // in case of a collision, take that image that has the better image size tags
            if ((ie.height() > 0) && (ie.width() > 0)) a.put(ie.url(), ie);
        } else {
            a.put(ie.url(), ie);
        }
    }
    
}

