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
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.event.EventListenerList;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.storage.SizeLimitedMap;
import net.yacy.cora.storage.SizeLimitedSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.NumberTools;
import net.yacy.document.SentenceReader;
import net.yacy.document.parser.htmlParser;
import net.yacy.document.parser.html.Evaluation.Element;
import net.yacy.document.parser.images.genericImageParser;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.ISO639;


public class ContentScraper extends AbstractScraper implements Scraper {

    private final static int MAX_TAGSIZE = 1024 * 1024;
	public static final int MAX_DOCSIZE = 40 * 1024 * 1024;

    private final char degree = '\u00B0';
    private final char[] minuteCharsHTML = "&#039;".toCharArray();

    // statics: for initialization of the HTMLFilterAbstractScraper
    private static final Set<String> linkTags0 = new HashSet<String>(12,0.99f);
    private static final Set<String> linkTags1 = new HashSet<String>(15,0.99f);

    private static final Pattern LB = Pattern.compile("\n");

    public enum TagType {
        singleton, pair;
    }

    public enum TagName {
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
        iframe(TagType.singleton), // scraped as singleton to get such iframes that have no closing tag
        source(TagType.singleton), // html5 (part of <video> <audio>) - scaped like embed

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
        u(TagType.pair),
        i(TagType.pair),
        li(TagType.pair),
        script(TagType.pair),
        style(TagType.pair);

        public TagType type;
        private TagName(final TagType type) {
            this.type = type;
        }
    }

    public static class Tag {
        public String name;
        public Properties opts;
        public CharBuffer content;
        public Tag(final String name) {
            this.name = name;
            this.opts = new Properties();
            this.content = new CharBuffer(MAX_TAGSIZE);
        }
        public Tag(final String name, final Properties opts) {
            this.name = name;
            this.opts = opts;
            this.content = new CharBuffer(MAX_TAGSIZE);
        }
        public Tag(final String name, final Properties opts, final CharBuffer content) {
            this.name = name;
            this.opts = opts;
            this.content = content;
        }
        public void close() {
            this.name = null;
            this.opts = null;
            if (this.content != null) this.content.close();
            this.content = null;
        }
        @Override
        public void finalize() {
            this.close();
        }
        @Override
        public String toString() {
            return "<" + name + " " + opts + ">" + content + "</" + name + ">";
        }
    }

    // all these tags must be given in lowercase, because the tags from the files are compared in lowercase
    static {
        for (final TagName tag: TagName.values()) {
            if (tag.type == TagType.singleton) linkTags0.add(tag.name());
            if (tag.type == TagType.pair) linkTags1.add(tag.name());
        }
        //<iframe src="../../../index.htm" name="SELFHTML_in_a_box" width="90%" height="400">
    }

    // class variables: collectors for links
    private final List<AnchorURL> anchors;
    private final LinkedHashMap<DigestURL, String> rss, css;
    private final LinkedHashMap<DigestURL, EmbedEntry> embeds; // urlhash/embed relation
    private final List<ImageEntry> images; 
    private final Set<DigestURL> script, frames, iframes;
    private final Map<String, String> metas;
    private final Map<String, DigestURL> hreflang, navigation;
    private LinkedHashSet<String> titles;
    //private String headline;
    private List<String>[] headlines;
    private final ClusteredScoreMap<String> bold, italic, underline;
    private final List<String> li;
    private final CharBuffer content;
    private final EventListenerList htmlFilterEventListeners;
    private double lon, lat;
    private DigestURL canonical, publisher;
    private final int maxLinks;
    private int breadcrumbs;


    /**
     * {@link MultiProtocolURL} to the favicon that belongs to the document
     */
    private MultiProtocolURL favicon;

    /**
     * The document root {@link MultiProtocolURL}
     */
    private DigestURL root;

    /**
     * evaluation scores: count appearance of specific attributes
     */
    private final Evaluation evaluationScores;

    @SuppressWarnings("unchecked")
    public ContentScraper(final DigestURL root, int maxLinks) {
        // the root value here will not be used to load the resource.
        // it is only the reference for relative links
        super(linkTags0, linkTags1);
        assert root != null;
        this.root = root;
        this.maxLinks = maxLinks;
        this.evaluationScores = new Evaluation();
        this.rss = new SizeLimitedMap<DigestURL, String>(maxLinks);
        this.css = new SizeLimitedMap<DigestURL, String>(maxLinks);
        this.anchors = new ArrayList<AnchorURL>();
        this.images = new ArrayList<ImageEntry>();
        this.embeds = new SizeLimitedMap<DigestURL, EmbedEntry>(maxLinks);
        this.frames = new SizeLimitedSet<DigestURL>(maxLinks);
        this.iframes = new SizeLimitedSet<DigestURL>(maxLinks);
        this.metas = new SizeLimitedMap<String, String>(maxLinks);
        this.hreflang = new SizeLimitedMap<String, DigestURL>(maxLinks);
        this.navigation = new SizeLimitedMap<String, DigestURL>(maxLinks);
        this.script = new SizeLimitedSet<DigestURL>(maxLinks);
        this.titles = new LinkedHashSet<String>();
        this.headlines = new ArrayList[6];
        for (int i = 0; i < this.headlines.length; i++) this.headlines[i] = new ArrayList<String>();
        this.bold = new ClusteredScoreMap<String>();
        this.italic = new ClusteredScoreMap<String>();
        this.underline = new ClusteredScoreMap<String>();
        this.li = new ArrayList<String>();
        this.content = new CharBuffer(MAX_DOCSIZE, 1024);
        this.htmlFilterEventListeners = new EventListenerList();
        this.lon = 0.0d;
        this.lat = 0.0d;
        this.evaluationScores.match(Element.url, root.toNormalform(true));
        this.canonical = null;
        this.publisher = null;
        this.breadcrumbs = 0;
    }

    @Override
    public void finish() {
        this.content.trimToSize();
    }

    @Override
    public void scrapeText(final char[] newtext0, final String insideTag) {
        // System.out.println("SCRAPE: " + UTF8.String(newtext));
        if (insideTag != null && ("script".equals(insideTag) || "style".equals(insideTag))) return;
        int p, pl, q, s = 0;
        char[] newtext = CharacterCoding.html2unicode(new String(newtext0)).toCharArray();
        
        // match evaluation pattern
        this.evaluationScores.match(Element.text, newtext);

        // try to find location information in text
        // Opencaching:
        // <nobr>N 50o 05.453&#039;</nobr><nobr>E 008o 30.191&#039;</nobr>
        // N 52o 28.025 E 013o 20.299
        location: while (s < newtext.length) {
            pl = 1;
            p = CharBuffer.indexOf(newtext, s, this.degree);
            if (p < 0) {p = CharBuffer.indexOf(newtext, s, "&deg;".toCharArray()); if (p >= 0) pl = 5;}
            if (p < 0) break location;
            q = CharBuffer.indexOf(newtext, p + pl, this.minuteCharsHTML);
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
                                    Float.parseFloat(new String(newtext, p + pl + 1, q - p - pl - 1)) / 60.0d;
                        if (this.lon != 0.0d) break location;
                        s = q + 6;
                        continue location;
                    }
                    if (newtext[r] == 'S') {
                        this.lat = -Float.parseFloat(new String(newtext, r + 2, p - r - 2)) -
                                    Float.parseFloat(new String(newtext, p + pl + 1, q - p - pl - 1)) / 60.0d;
                        if (this.lon != 0.0d) break location;
                        s = q + 6;
                        continue location;
                    }
                    if (newtext[r] == 'E') {
                        this.lon =  Float.parseFloat(new String(newtext, r + 2, p - r - 2)) +
                                    Float.parseFloat(new String(newtext, p + pl + 1, q - p - pl - 1)) / 60.0d;
                        if (this.lat != 0.0d) break location;
                        s = q + 6;
                        continue location;
                    }
                    if (newtext[r] == 'W') {
                        this.lon = -Float.parseFloat(new String(newtext, r + 2, p - r - 2)) -
                                    Float.parseFloat(new String(newtext, p + 2, q - p - pl - 1)) / 60.0d;
                        if (this.lat != 0.0d) break location;
                        s = q + 6;
                        continue location;
                    }
                    break location;
                }
            }
            break location;
        }
        // find tags inside text
        String b = cleanLine(stripAllTags(newtext));
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
        while (s < b.length()) {
            p = find(b, dpssp, s);
            if (p == Integer.MAX_VALUE) break;
            s = Math.max(0, p - 5);
            p = find(b, protp, s);
            if (p == Integer.MAX_VALUE) break;
            q = b.indexOf(" ", p + 1);
            u = b.substring(p, q < 0 ? b.length() : q);
            if (u.endsWith(".")) u = u.substring(0, u.length() - 1); // remove the '.' that was appended above
            s = p + 6;
            try {
                this.anchors.add(new AnchorURL(u));
                continue;
            } catch (final MalformedURLException e) {}
        }
        // append string to content
        if (!b.isEmpty()) {
            this.content.append(b);
            this.content.appendSpace();
        }
    }

    private final static Pattern dpssp = Pattern.compile("://");
    private final static Pattern protp = Pattern.compile("smb://|ftp://|http://|https://");

    private static final int find(final String s, final Pattern m, final int start) {
        final Matcher mm = m.matcher(s.subSequence(start, s.length()));
        if (!mm.find()) return Integer.MAX_VALUE;
        final int p = mm.start() + start;
        //final int p = s.indexOf(m, start);
        return (p < 0) ? Integer.MAX_VALUE : p;
    }

    private AnchorURL absolutePath(final String relativePath) {
        try {
            return AnchorURL.newAnchor(this.root, relativePath);
        } catch (final Exception e) {
            return null;
        }
    }

    @Override
    public void scrapeTag0(Tag tag) {
        if (tag.name.equalsIgnoreCase("img")) {
            final String src = tag.opts.getProperty("src", EMPTY_STRING);
            try {
                if (src.length() > 0) {
                    final AnchorURL url = absolutePath(src);
                    if (url != null) {
                        // use Numberformat.parse to allow parse of "550px"
                        NumberFormat intnum = NumberFormat.getIntegerInstance ();
                        final int width = intnum.parse(tag.opts.getProperty("width", "-1")).intValue(); // Integer.parseInt fails on "200px"
                        final int height = intnum.parse(tag.opts.getProperty("height", "-1")).intValue();
                        final ImageEntry ie = new ImageEntry(url, tag.opts.getProperty("alt", EMPTY_STRING), width, height, -1);
                        this.images.add(ie);
                    }
                }
            } catch (final ParseException e) {}
            this.evaluationScores.match(Element.imgpath, src);
        } else if(tag.name.equalsIgnoreCase("base")) {
            try {
                this.root = new DigestURL(tag.opts.getProperty("href", EMPTY_STRING));
            } catch (final MalformedURLException e) {}
        } else if (tag.name.equalsIgnoreCase("frame")) {
            final AnchorURL src = absolutePath(tag.opts.getProperty("src", EMPTY_STRING));
            tag.opts.put("src", src.toNormalform(true));
            src.setAll(tag.opts);
            this.anchors.add(src);
            this.frames.add(src);
            this.evaluationScores.match(Element.framepath, src.toNormalform(true));
        } else if (tag.name.equalsIgnoreCase("body")) {
            final String c = tag.opts.getProperty("class", EMPTY_STRING);
            this.evaluationScores.match(Element.bodyclass, c);
        } else if (tag.name.equalsIgnoreCase("div")) {
            final String id = tag.opts.getProperty("id", EMPTY_STRING);
            this.evaluationScores.match(Element.divid, id);
            final String itemtype = tag.opts.getProperty("itemtype", EMPTY_STRING);
            if (itemtype.equals("http://data-vocabulary.org/Breadcrumb")) {
                breadcrumbs++;
            }
        } else if (tag.name.equalsIgnoreCase("meta")) {
            final String content = tag.opts.getProperty("content", EMPTY_STRING);
            String name = tag.opts.getProperty("name", EMPTY_STRING);
            if (name.length() > 0) {
                this.metas.put(name.toLowerCase(), CharacterCoding.html2unicode(content));
                if (name.toLowerCase().equals("generator")) {
                    this.evaluationScores.match(Element.metagenerator, content);
                }
            }
            name = tag.opts.getProperty("http-equiv", EMPTY_STRING);
            if (name.length() > 0) {
                this.metas.put(name.toLowerCase(), CharacterCoding.html2unicode(content));
            }
            name = tag.opts.getProperty("property", EMPTY_STRING);
            if (name.length() > 0) {
                this.metas.put(name.toLowerCase(), CharacterCoding.html2unicode(content));
            }
        } else if (tag.name.equalsIgnoreCase("area")) {
            final String areatitle = cleanLine(tag.opts.getProperty("title", EMPTY_STRING));
            //String alt   = tag.opts.getProperty("alt",EMPTY_STRING);
            final String href  = tag.opts.getProperty("href", EMPTY_STRING);
            if (href.length() > 0) {
                tag.opts.put("name", areatitle);
                AnchorURL url = absolutePath(href);
                tag.opts.put("href", url.toNormalform(true));
                url.setAll(tag.opts);
                this.anchors.add(url);
            }
        } else if (tag.name.equalsIgnoreCase("link")) {
            final String href = tag.opts.getProperty("href", EMPTY_STRING);
            final AnchorURL newLink = absolutePath(href);

            if (newLink != null) {
                tag.opts.put("href", newLink.toNormalform(true));
                String rel = tag.opts.getProperty("rel", EMPTY_STRING);
                final String linktitle = tag.opts.getProperty("title", EMPTY_STRING);
                final String type = tag.opts.getProperty("type", EMPTY_STRING);
                final String hreflang = tag.opts.getProperty("hreflang", EMPTY_STRING);

                if (rel.equalsIgnoreCase("shortcut icon")) {
                    final ImageEntry ie = new ImageEntry(newLink, linktitle, -1, -1, -1);
                    this.images.add(ie);
                    this.favicon = newLink;
                } else if (rel.equalsIgnoreCase("canonical")) {
                    tag.opts.put("name", this.titles.size() == 0 ? "" : this.titles.iterator().next());
                    newLink.setAll(tag.opts);
                    this.anchors.add(newLink);
                    this.canonical = newLink;
                } else if (rel.equalsIgnoreCase("publisher")) {
                    this.publisher = newLink;
                } else if (rel.equalsIgnoreCase("top") || rel.equalsIgnoreCase("up") || rel.equalsIgnoreCase("next") || rel.equalsIgnoreCase("prev") || rel.equalsIgnoreCase("first") || rel.equalsIgnoreCase("last")) {
                    this.navigation.put(rel, newLink);
                } else if (rel.equalsIgnoreCase("alternate") && type.equalsIgnoreCase("application/rss+xml")) {
                    this.rss.put(newLink, linktitle);
                } else if (rel.equalsIgnoreCase("alternate") && hreflang.length() > 0) {
                    this.hreflang.put(hreflang, newLink);
                } else if (rel.equalsIgnoreCase("stylesheet") && type.equalsIgnoreCase("text/css")) {
                    this.css.put(newLink, rel);
                    this.evaluationScores.match(Element.csspath, href);
                } else if (!rel.equalsIgnoreCase("stylesheet") && !rel.equalsIgnoreCase("alternate stylesheet")) {
                    tag.opts.put("name", linktitle);
                    newLink.setAll(tag.opts);
                    this.anchors.add(newLink);
                }
            }
        } else if(tag.name.equalsIgnoreCase("embed") || tag.name.equalsIgnoreCase("source")) { //html5 tag
            final String src = tag.opts.getProperty("src", EMPTY_STRING);
            try {
                if (src.length() > 0) {
                    final AnchorURL url = absolutePath(src);
                    if (url != null) {
                        final int width = Integer.parseInt(tag.opts.getProperty("width", "-1"));
                        final int height = Integer.parseInt(tag.opts.getProperty("height", "-1"));
                        tag.opts.put("src", url.toNormalform(true));
                        final EmbedEntry ie = new EmbedEntry(url, width, height, tag.opts.getProperty("type", EMPTY_STRING), tag.opts.getProperty("pluginspage", EMPTY_STRING));
                        this.embeds.put(url, ie);
                        url.setAll(tag.opts);
                        this.anchors.add(url);
                    }
                }
            } catch (final NumberFormatException e) {}
        } else if(tag.name.equalsIgnoreCase("param")) {
            final String name = tag.opts.getProperty("name", EMPTY_STRING);
            if (name.equalsIgnoreCase("movie")) {
                AnchorURL url = absolutePath(tag.opts.getProperty("value", EMPTY_STRING));
                tag.opts.put("value", url.toNormalform(true));
                url.setAll(tag.opts);
                this.anchors.add(url);
            }
        } else if (tag.name.equalsIgnoreCase("iframe")) {
            final AnchorURL src = absolutePath(tag.opts.getProperty("src", EMPTY_STRING));
            tag.opts.put("src", src.toNormalform(true));
            src.setAll(tag.opts);
            this.anchors.add(src);
            this.iframes.add(src);
            this.evaluationScores.match(Element.iframepath, src.toNormalform(true));
        } else if (tag.name.equalsIgnoreCase("html")) {
            final String lang = tag.opts.getProperty("lang", EMPTY_STRING);
            if (!lang.isEmpty()) // fake a language meta to preserv detection from <html lang="xx" />
                this.metas.put("dc.language",lang.substring(0,2)); // fix found entries like "hu-hu"
        }

        // fire event
        fireScrapeTag0(tag.name, tag.opts);
    }

    @Override
    public void scrapeTag1(Tag tag) {
        // System.out.println("ScrapeTag1: tag.tagname=" + tag.tagname + ", opts=" + tag.opts.toString() + ", text=" + UTF8.String(text));
        if (tag.name.equalsIgnoreCase("a") && tag.content.length() < 2048) {
            String href = tag.opts.getProperty("href", EMPTY_STRING);
            href = CharacterCoding.html2unicode(href);
            AnchorURL url;
            if ((href.length() > 0) && ((url = absolutePath(href)) != null)) {
                final String ext = MultiProtocolURL.getFileExtension(url.getFileName());
                if (genericImageParser.SUPPORTED_EXTENSIONS.contains(ext)) {
                    // special handling of such urls: put them to the image urls
                    final ImageEntry ie = new ImageEntry(url, recursiveParse(url, tag.content.getChars()), -1, -1, -1);
                    this.images.add(ie);
                } else {
                    if (followDenied()) {
                        String rel = tag.opts.getProperty("rel", EMPTY_STRING);
                        if (rel.length() == 0) rel = "nofollow"; else if (rel.indexOf("nofollow") < 0) rel += ",nofollow"; 
                        tag.opts.put("rel", rel);
                    }
                    tag.opts.put("text", stripAllTags(tag.content.getChars())); // strip any inline html in tag text like  "<a ...> <span>test</span> </a>"
                    tag.opts.put("href", url.toNormalform(true)); // we must assign this because the url may have resolved backpaths and may not be absolute
                    url.setAll(tag.opts);
                    recursiveParse(url, tag.content.getChars());
                    this.anchors.add(url);
                }
            }
            this.evaluationScores.match(Element.apath, href);
        }
        final String h;
        if ((tag.name.equalsIgnoreCase("h1")) && (tag.content.length() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(stripAllTags(tag.content.getChars())));
            if (h.length() > 0) this.headlines[0].add(h);
        } else if((tag.name.equalsIgnoreCase("h2")) && (tag.content.length() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(stripAllTags(tag.content.getChars())));
            if (h.length() > 0) this.headlines[1].add(h);
        } else if ((tag.name.equalsIgnoreCase("h3")) && (tag.content.length() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(stripAllTags(tag.content.getChars())));
            if (h.length() > 0) this.headlines[2].add(h);
        } else if ((tag.name.equalsIgnoreCase("h4")) && (tag.content.length() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(stripAllTags(tag.content.getChars())));
            if (h.length() > 0) this.headlines[3].add(h);
        } else if ((tag.name.equalsIgnoreCase("h5")) && (tag.content.length() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(stripAllTags(tag.content.getChars())));
            if (h.length() > 0) this.headlines[4].add(h);
        } else if ((tag.name.equalsIgnoreCase("h6")) && (tag.content.length() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(stripAllTags(tag.content.getChars())));
            if (h.length() > 0) this.headlines[5].add(h);
        } else if ((tag.name.equalsIgnoreCase("title")) && (tag.content.length() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(stripAllTags(tag.content.getChars())));
            this.titles.add(h);
            this.evaluationScores.match(Element.title, h);
        } else if ((tag.name.equalsIgnoreCase("b")) && (tag.content.length() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(stripAllTags(tag.content.getChars())));
            if (h.length() > 0) this.bold.inc(h);
        } else if ((tag.name.equalsIgnoreCase("strong")) && (tag.content.length() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(stripAllTags(tag.content.getChars())));
            if (h.length() > 0) this.bold.inc(h);
        } else if ((tag.name.equalsIgnoreCase("i")) && (tag.content.length() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(stripAllTags(tag.content.getChars())));
            if (h.length() > 0) this.italic.inc(h);
        } else if ((tag.name.equalsIgnoreCase("u")) && (tag.content.length() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(stripAllTags(tag.content.getChars())));
            if (h.length() > 0) this.underline.inc(h);
        } else if ((tag.name.equalsIgnoreCase("li")) && (tag.content.length() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(stripAllTags(tag.content.getChars())));
            if (h.length() > 0) this.li.add(h);
        } else if (tag.name.equalsIgnoreCase("script")) {
            final String src = tag.opts.getProperty("src", EMPTY_STRING);
            if (src.length() > 0) {
                this.script.add(absolutePath(src));
                this.evaluationScores.match(Element.scriptpath, src);
            } else {
                this.evaluationScores.match(Element.scriptcode, LB.matcher(new String(tag.content.getChars())).replaceAll(" "));
            }
        }

        // fire event
        fireScrapeTag1(tag.name, tag.opts, tag.content.getChars());
    }


    @Override
    public void scrapeComment(final char[] comment) {
        this.evaluationScores.match(Element.comment, LB.matcher(new String(comment)).replaceAll(" "));
    }

    private String recursiveParse(final AnchorURL linkurl, final char[] inlineHtml) {
        if (inlineHtml.length < 14) return cleanLine(CharacterCoding.html2unicode(stripAllTags(inlineHtml)));

        // start a new scraper to parse links inside this text
        // parsing the content
        final ContentScraper scraper = new ContentScraper(this.root, this.maxLinks);
        final TransformerWriter writer = new TransformerWriter(null, null, scraper, null, false);
        try {
            FileUtils.copy(new CharArrayReader(inlineHtml), writer);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            return cleanLine(CharacterCoding.html2unicode(stripAllTags(inlineHtml)));
        } finally {
            try {
                writer.close();
            } catch (final IOException e) {
            }
        }
        for (final AnchorURL entry: scraper.getAnchors()) {
            this.anchors.add(entry);
        }
        String line = cleanLine(CharacterCoding.html2unicode(stripAllTags(scraper.content.getChars())));
        for (ImageEntry ie: scraper.images) {
            if (linkurl != null) {
                ie.setLinkurl(linkurl);
                ie.setAnchortext(line);
            }
            // this image may have been added recently from the same location (as this is a recursive parse)
            // we want to keep only one of them, check if they are equal
            if (this.images.size() > 0 && this.images.get(this.images.size() - 1).url().equals(ie.url())) {
                this.images.remove(this.images.size() - 1);
            }
            this.images.add(ie);
        }

        scraper.close();
        return line;
    }

    public List<String> getTitles() {

        // some documents have a title tag as meta tag
        String s = this.metas.get("title");
        if (s != null && s.length() > 0) {
            this.titles.add(s);
        }

        if (this.titles.size() == 0) {
            // take any headline
            for (int i = 0; i < this.headlines.length; i++) {
                if (!this.headlines[i].isEmpty()) {
                    this.titles.add(this.headlines[i].get(0));
                    break;
                }
            }
        }

        // extract headline from file name
        ArrayList<String> t = new ArrayList<String>();
        t.addAll(this.titles);
        return t;
    }

    public String[] getHeadlines(final int i) {
        assert ((i >= 1) && (i <= this.headlines.length));
        return this.headlines[i - 1].toArray(new String[this.headlines[i - 1].size()]);
    }

    public String[] getBold() {
        final List<String> a = new ArrayList<String>();
        final Iterator<String> i = this.bold.keys(false);
        while (i.hasNext()) a.add(i.next());
        return a.toArray(new String[a.size()]);
    }

    public String[] getBoldCount(final String[] a) {
        final String[] counter = new String[a.length];
        for (int i = 0; i < a.length; i++) counter[i] = Integer.toString(this.bold.get(a[i]));
        return counter;
    }

    public String[] getItalic() {
        final List<String> a = new ArrayList<String>();
        final Iterator<String> i = this.italic.keys(false);
        while (i.hasNext()) a.add(i.next());
        return a.toArray(new String[a.size()]);
    }

    public String[] getItalicCount(final String[] a) {
        final String[] counter = new String[a.length];
        for (int i = 0; i < a.length; i++) counter[i] = Integer.toString(this.italic.get(a[i]));
        return counter;
    }

    public String[] getUnderline() {
        final List<String> a = new ArrayList<String>();
        final Iterator<String> i = this.underline.keys(false);
        while (i.hasNext()) a.add(i.next());
        return a.toArray(new String[a.size()]);
    }

    public String[] getUnderlineCount(final String[] a) {
        final String[] counter = new String[a.length];
        for (int i = 0; i < a.length; i++) counter[i] = Integer.toString(this.underline.get(a[i]));
        return counter;
    }

    public String[] getLi() {
        return this.li.toArray(new String[this.li.size()]);
    }

    public DigestURL[] getFlash() {
        String ext;
        ArrayList<DigestURL> f = new ArrayList<DigestURL>();
        for (final DigestURL url: this.anchors) {
            ext = MultiProtocolURL.getFileExtension(url.getFileName());
            if (ext == null) continue;
            if (ext.equals("swf")) f.add(url);
        }
        return f.toArray(new DigestURL[f.size()]);
    }

    public boolean containsFlash() {
        String ext;
        for (final MultiProtocolURL url: this.anchors) {
            ext = MultiProtocolURL.getFileExtension(url.getFileName());
            if (ext == null) continue;
            if (ext.equals("swf")) return true;
        }
        return false;
    }

    public int breadcrumbCount() {
        return this.breadcrumbs;
    }
    
    public String getText() {
        this.content.trim();
        try {
            return this.content.toString();
        } catch (final OutOfMemoryError e) {
            ConcurrentLog.logException(e);
            return "";
        }
    }

    public List<AnchorURL> getAnchors() {
        // returns a url (String) / name (String) relation
        return this.anchors;
    }

    public LinkedHashMap<DigestURL, String> getRSS() {
        // returns a url (String) / name (String) relation
        return this.rss;
    }

    public Map<DigestURL, String> getCSS() {
        // returns a url (String) / name (String) relation
        return this.css;
    }

    public Set<DigestURL> getFrames() {
        // returns a url (String) / name (String) relation
        return this.frames;
    }

    public Set<DigestURL> getIFrames() {
        // returns a url (String) / name (String) relation
        return this.iframes;
    }

    public Set<DigestURL> getScript() {
        return this.script;
    }

    public DigestURL getCanonical() {
        return this.canonical;
    }

    public DigestURL getPublisherLink() {
        return this.publisher;
    }
    
    public Map<String, DigestURL> getHreflang() {
        return this.hreflang;
    }
    
    public Map<String, DigestURL> getNavigation() {
        return this.navigation;
    }

    /**
     * get all images
     * @return a map of <urlhash, ImageEntry>
     */
    public List<ImageEntry> getImages() {
        return this.images;
    }

    public Map<DigestURL, EmbedEntry> getEmbeds() {
        return this.embeds;
    }

    public Map<String, String> getMetas() {
        return this.metas;
    }

    /**
     * @return the {@link MultiProtocolURL} to the favicon that belongs to the document
     */
    public MultiProtocolURL getFavicon() {
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
        final String s = this.metas.get("robots");
        if (s == null) return false;
        if (s.indexOf("noindex",0) >= 0) return true;
        return false;
    }
    
    public boolean followDenied() {
        final String s = this.metas.get("robots");
        if (s == null) return false;
        if (s.indexOf("nofollow",0) >= 0) return true;
        return false;
    }

    public List<String> getDescriptions() {
        String s = this.metas.get("description");
        if (s == null) s = this.metas.get("dc.description");
        List<String> descriptions = new ArrayList<String>();
        if (s == null) return descriptions;
        descriptions.add(s);
        return descriptions;
    }

    public String getContentType() {
        final String s = this.metas.get("content-type");
        if (s == null) return EMPTY_STRING;
        return s;
    }

    public String getAuthor() {
        String s = this.metas.get("author");
        if (s == null) s = this.metas.get("dc.creator");
        if (s == null) return EMPTY_STRING;
        return s;
    }

    public String getPublisher() {
        String s = this.metas.get("copyright");
        if (s == null) s = this.metas.get("dc.publisher");
        if (s == null) return EMPTY_STRING;
        return s;
    }

    private final static Pattern commaSepPattern = Pattern.compile(" |,");
    private final static Pattern semicSepPattern = Pattern.compile(" |;");

    public Set<String> getContentLanguages() {
        // i.e. <meta name="DC.language" content="en" scheme="DCTERMS.RFC3066">
        // or <meta http-equiv="content-language" content="en">
        String s = this.metas.get("content-language");
        if (s == null) s = this.metas.get("dc.language");
        if (s == null) return null;
        final Set<String> hs = new HashSet<String>();
        final String[] cl = commaSepPattern.split(s);
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
        String s = this.metas.get("keywords");
        if (s == null) s = this.metas.get("dc.description");
        if (s == null) s = EMPTY_STRING;
        if (s.isEmpty()) {
            return new String[0];
        }
        if (s.contains(",")) return commaSepPattern.split(s);
        if (s.contains(";")) return semicSepPattern.split(s);
        return s.split("\\s");
    }

    public int getRefreshSeconds() {
        final String s = this.metas.get("refresh");
        if (s == null) return 9999;
        try {
            final int pos = s.indexOf(';');
            if (pos < 0) return 9999;
            final int i = NumberTools.parseIntDecSubstring(s, 0, pos);
            return i;
        } catch (final NumberFormatException e) {
            return 9999;
        }
    }

    public String getRefreshPath() {
        String s = this.metas.get("refresh");
        if (s == null) return EMPTY_STRING;

        final int pos = s.indexOf(';');
        if (pos < 0) return EMPTY_STRING;
        s = s.substring(pos + 1).trim();
        if (s.toLowerCase().startsWith("url=")) return s.substring(4).trim();
        return EMPTY_STRING;
    }
    
    public Date getDate() {
        String content;
        
        // <meta name="date" content="YYYY-MM-DD..." />
        content = this.metas.get("date");
        if (content != null) try {return ISO8601Formatter.FORMATTER.parse(content);} catch (ParseException e) {}

        // <meta name="DC.date" content="YYYY-MM-DD" />
        content = this.metas.get("dc.date");
        if (content != null) try {return ISO8601Formatter.FORMATTER.parse(content);} catch (ParseException e) {}
        
        // <meta name="DC:date" content="YYYY-MM-DD" />
        content = this.metas.get("dc:date");
        if (content != null) try {return ISO8601Formatter.FORMATTER.parse(content);} catch (ParseException e) {}
        
        // <meta http-equiv="last-modified" content="YYYY-MM-DD" />
        content = this.metas.get("last-modified");
        if (content != null) try {return ISO8601Formatter.FORMATTER.parse(content);} catch (ParseException e) {}
        
        return new Date();
    }

    // parse location
    // <meta NAME="ICBM" CONTENT="38.90551492, 1.454004505" />
    // <meta NAME="geo.position" CONTENT="38.90551492;1.454004505" />

    public double getLon() {
        if (this.lon != 0.0d) return this.lon;
        String s = this.metas.get("ICBM"); // InterContinental Ballistic Missile (abbrev. supposed to be a joke: http://www.jargon.net/jargonfile/i/ICBMaddress.html), see http://geourl.org/add.html#icbm
        if (s != null) {
            int p = s.indexOf(';');
            if (p < 0) p = s.indexOf(',');
            if (p < 0) p = s.indexOf(' ');
            if (p > 0) {
                this.lat = Double.parseDouble(s.substring(0, p).trim());
                this.lon = Double.parseDouble(s.substring(p + 1).trim());
            }
        }
        if (this.lon != 0.0d) return this.lon;
        s = this.metas.get("geo.position"); // http://geotags.com/geobot/add-tags.html
        if (s != null) {
            int p = s.indexOf(';');
            if (p < 0) p = s.indexOf(',');
            if (p < 0) p = s.indexOf(' ');
            if (p > 0) {
                this.lat = Double.parseDouble(s.substring(0, p).trim());
                this.lon = Double.parseDouble(s.substring(p + 1).trim());
            }
        }
        return this.lon;
    }

    public double getLat() {
        if (this.lat != 0.0d) return this.lat;
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

    public String[] getEvaluationModelScoreNames(final String modelName) {
        final List<String> a = new ArrayList<String>();
        final ClusteredScoreMap<String> scores = this.evaluationScores.getScores(modelName);
        if (scores != null) {
            final Iterator<String> i = scores.keys(false);
            while (i.hasNext()) a.add(i.next());
        }
        return a.toArray(new String[a.size()]);
    }

    public String[] getEvaluationModelScoreCounts(final String modelName, final String[] a) {
        final ClusteredScoreMap<String> scores = this.evaluationScores.getScores(modelName);
        final String[] counter = new String[a.length];
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
        this.anchors.clear();
        this.rss.clear();
        this.css.clear();
        this.script.clear();
        this.frames.clear();
        this.iframes.clear();
        this.embeds.clear();
        this.images.clear();
        this.metas.clear();
        this.titles.clear();
        this.headlines = null;
        this.bold.clear();
        this.italic.clear();
        this.content.clear();
        this.root = null;
    }

    public void print() {
        for (String t: this.titles) {
            System.out.println("TITLE    :" + t);
        }
        for (int i = 0; i < 4; i++) {
            System.out.println("HEADLINE" + i + ":" + this.headlines[i].toString());
        }
        System.out.println("ANCHORS  :" + this.anchors.toString());
        System.out.println("IMAGES   :" + this.images.toString());
        System.out.println("METAS    :" + this.metas.toString());
        System.out.println("TEXT     :" + this.content.toString());
    }

    @Override
    public void registerHtmlFilterEventListener(final ScraperListener listener) {
        if (listener != null) {
            this.htmlFilterEventListeners.add(ScraperListener.class, listener);
        }
    }

    @Override
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

    public static ContentScraper parseResource(final File file, final int maxLinks) throws IOException {
        // load page
        final byte[] page = FileUtils.read(file);
        if (page == null) throw new IOException("no content in file " + file.toString());

        // scrape document to look up charset
        final ScraperInputStream htmlFilter = new ScraperInputStream(new ByteArrayInputStream(page),"UTF-8", new DigestURL("http://localhost"),null,false, maxLinks);
        String charset = htmlParser.patchCharsetEncoding(htmlFilter.detectCharset());
        htmlFilter.close();
        if (charset == null) charset = Charset.defaultCharset().toString();

        // scrape content
        final ContentScraper scraper = new ContentScraper(new DigestURL("http://localhost"), maxLinks);
        final Writer writer = new TransformerWriter(null, null, scraper, null, false);
        FileUtils.copy(new ByteArrayInputStream(page), writer, Charset.forName(charset));
        writer.close();
        return scraper;
    }

}

