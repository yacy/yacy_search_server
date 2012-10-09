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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.event.EventListenerList;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.storage.SizeLimitedMap;
import net.yacy.cora.storage.SizeLimitedSet;
import net.yacy.cora.util.NumberTools;
import net.yacy.document.SentenceReader;
import net.yacy.document.parser.htmlParser;
import net.yacy.document.parser.html.Evaluation.Element;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.ISO639;


public class ContentScraper extends AbstractScraper implements Scraper {
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
        iframe(TagType.singleton), // scraped as singleton to get such iframes that have no closing tag

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
        private Tag(final TagType type) {
            this.type = type;
        }
    }

    // all these tags must be given in lowercase, because the tags from the files are compared in lowercase
    static {
        for (final Tag tag: Tag.values()) {
            if (tag.type == TagType.singleton) linkTags0.add(tag.name());
            if (tag.type == TagType.pair) linkTags1.add(tag.name());
        }
        //<iframe src="../../../index.htm" name="SELFHTML_in_a_box" width="90%" height="400">
    }

    // class variables: collectors for links
    private final Map<MultiProtocolURI, Properties> anchors;
    private final Map<MultiProtocolURI, String> rss, css;
    private final Set<MultiProtocolURI> script, frames, iframes;
    private final Map<MultiProtocolURI, EmbedEntry> embeds; // urlhash/embed relation
    private final Map<MultiProtocolURI, ImageEntry> images; // urlhash/image relation
    private final Map<String, String> metas;
    private Collection<String> titles;
    //private String headline;
    private List<String>[] headlines;
    private final ClusteredScoreMap<String> bold, italic, underline;
    private final List<String> li;
    private final CharBuffer content;
    private final EventListenerList htmlFilterEventListeners;
    private double lon, lat;
    private MultiProtocolURI canonical;
    private final int maxLinks;
    private int breadcrumbs;


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
    private final Evaluation evaluationScores;

    @SuppressWarnings("unchecked")
    public ContentScraper(final MultiProtocolURI root, int maxLinks) {
        // the root value here will not be used to load the resource.
        // it is only the reference for relative links
        super(linkTags0, linkTags1);
        assert root != null;
        this.root = root;
        this.maxLinks = maxLinks;
        this.evaluationScores = new Evaluation();
        this.rss = new SizeLimitedMap<MultiProtocolURI, String>(maxLinks);
        this.css = new SizeLimitedMap<MultiProtocolURI, String>(maxLinks);
        this.anchors = new SizeLimitedMap<MultiProtocolURI, Properties>(maxLinks);
        this.images = new SizeLimitedMap<MultiProtocolURI, ImageEntry>(maxLinks);
        this.embeds = new SizeLimitedMap<MultiProtocolURI, EmbedEntry>(maxLinks);
        this.frames = new SizeLimitedSet<MultiProtocolURI>(maxLinks);
        this.iframes = new SizeLimitedSet<MultiProtocolURI>(maxLinks);
        this.metas = new SizeLimitedMap<String, String>(maxLinks);
        this.script = new SizeLimitedSet<MultiProtocolURI>(maxLinks);
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
        this.evaluationScores.match(Element.url, root.toNormalform(false, false));
        this.canonical = null;
        this.breadcrumbs = 0;
    }

    @Override
    public void finish() {
        this.content.trimToSize();
    }

    private void mergeAnchors(final MultiProtocolURI url, final Properties p) {
        final Properties p0 = this.anchors.get(url);
        if (p0 == null) {
            this.anchors.put(url, p);
            return;
        }
        // merge properties
        for (final Entry<Object, Object> entry: p.entrySet()) {
            if (entry.getValue() != null && entry.getValue().toString().length() > 0) p0.put(entry.getKey(), entry.getValue());
        }
        this.anchors.put(url, p0);
    }

    @Override
    public void scrapeText(final char[] newtext, final String insideTag) {
        // System.out.println("SCRAPE: " + UTF8.String(newtext));
        if (insideTag != null && ("script".equals(insideTag) || "style".equals(insideTag))) return;
        int p, pl, q, s = 0;

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
                url = new MultiProtocolURI(u);
                mergeAnchors(url, new Properties());
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

    private MultiProtocolURI absolutePath(final String relativePath) {
        try {
            return MultiProtocolURI.newURL(this.root, relativePath);
        } catch (final Exception e) {
            return null;
        }
    }

    @Override
    public void scrapeTag0(final String tagname, final Properties tagopts) {
        if (tagname.equalsIgnoreCase("img")) {
            final String src = tagopts.getProperty("src", EMPTY_STRING);
            try {
                if (src.length() > 0) {
                    final MultiProtocolURI url = absolutePath(src);
                    if (url != null) {
                        final int width = Integer.parseInt(tagopts.getProperty("width", "-1"));
                        final int height = Integer.parseInt(tagopts.getProperty("height", "-1"));
                        final ImageEntry ie = new ImageEntry(url, tagopts.getProperty("alt", EMPTY_STRING), width, height, -1);
                        addImage(this.images, ie);
                    }
                }
            } catch (final NumberFormatException e) {}
            this.evaluationScores.match(Element.imgpath, src);
        } else if(tagname.equalsIgnoreCase("base")) {
            try {
                this.root = new MultiProtocolURI(tagopts.getProperty("href", EMPTY_STRING));
            } catch (final MalformedURLException e) {}
        } else if (tagname.equalsIgnoreCase("frame")) {
            final MultiProtocolURI src = absolutePath(tagopts.getProperty("src", EMPTY_STRING));
            tagopts.put("src", src.toNormalform(true, false));
            mergeAnchors(src, tagopts /* with property "name" */);
            this.frames.add(src);
            this.evaluationScores.match(Element.framepath, src.toNormalform(true, false));
        } else if (tagname.equalsIgnoreCase("body")) {
            final String c = tagopts.getProperty("class", EMPTY_STRING);
            this.evaluationScores.match(Element.bodyclass, c);
        } else if (tagname.equalsIgnoreCase("div")) {
            final String id = tagopts.getProperty("id", EMPTY_STRING);
            this.evaluationScores.match(Element.divid, id);
            final String itemtype = tagopts.getProperty("itemtype", EMPTY_STRING);
            if (itemtype.equals("http://data-vocabulary.org/Breadcrumb")) {
                breadcrumbs++;
            }
        } else if (tagname.equalsIgnoreCase("meta")) {
            final String content = tagopts.getProperty("content", EMPTY_STRING);
            String name = tagopts.getProperty("name", EMPTY_STRING);
            if (name.length() > 0) {
                this.metas.put(name.toLowerCase(), CharacterCoding.html2unicode(content));
                if (name.toLowerCase().equals("generator")) {
                    this.evaluationScores.match(Element.metagenerator, content);
                }
            }
            name = tagopts.getProperty("http-equiv", EMPTY_STRING);
            if (name.length() > 0) {
                this.metas.put(name.toLowerCase(), CharacterCoding.html2unicode(content));
            }
            name = tagopts.getProperty("property", EMPTY_STRING);
            if (name.length() > 0) {
                this.metas.put(name.toLowerCase(), CharacterCoding.html2unicode(content));
            }
        } else if (tagname.equalsIgnoreCase("area")) {
            final String areatitle = cleanLine(tagopts.getProperty("title", EMPTY_STRING));
            //String alt   = tagopts.getProperty("alt",EMPTY_STRING);
            final String href  = tagopts.getProperty("href", EMPTY_STRING);
            if (href.length() > 0) {
                tagopts.put("nme", areatitle);
                MultiProtocolURI url = absolutePath(href);
                tagopts.put("href", url.toNormalform(true, false));
                mergeAnchors(url, tagopts);
            }
        } else if (tagname.equalsIgnoreCase("link")) {
            final String href = tagopts.getProperty("href", EMPTY_STRING);
            final MultiProtocolURI newLink = absolutePath(href);

            if (newLink != null) {
                tagopts.put("href", newLink.toNormalform(true, false));
                final String rel = tagopts.getProperty("rel", EMPTY_STRING);
                final String linktitle = tagopts.getProperty("title", EMPTY_STRING);
                final String type = tagopts.getProperty("type", EMPTY_STRING);

                if (rel.equalsIgnoreCase("shortcut icon")) {
                    final ImageEntry ie = new ImageEntry(newLink, linktitle, -1, -1, -1);
                    this.images.put(ie.url(), ie);
                    this.favicon = newLink;
                } else if (rel.equalsIgnoreCase("canonical")) {
                    tagopts.put("name", this.titles.size() == 0 ? "" : this.titles.iterator().next());
                    mergeAnchors(newLink, tagopts);
                    this.canonical = newLink;
                } else if (rel.equalsIgnoreCase("alternate") && type.equalsIgnoreCase("application/rss+xml")) {
                    this.rss.put(newLink, linktitle);
                } else if (rel.equalsIgnoreCase("stylesheet") && type.equalsIgnoreCase("text/css")) {
                    this.css.put(newLink, rel);
                    this.evaluationScores.match(Element.csspath, href);
                } else if (!rel.equalsIgnoreCase("stylesheet") && !rel.equalsIgnoreCase("alternate stylesheet")) {
                    tagopts.put("name", linktitle);
                    mergeAnchors(newLink, tagopts);
                }
            }
        } else if(tagname.equalsIgnoreCase("embed")) {
            final String src = tagopts.getProperty("src", EMPTY_STRING);
            try {
                if (src.length() > 0) {
                    final MultiProtocolURI url = absolutePath(src);
                    if (url != null) {
                        final int width = Integer.parseInt(tagopts.getProperty("width", "-1"));
                        final int height = Integer.parseInt(tagopts.getProperty("height", "-1"));
                        tagopts.put("src", url.toNormalform(true, false));
                        final EmbedEntry ie = new EmbedEntry(url, width, height, tagopts.getProperty("type", EMPTY_STRING), tagopts.getProperty("pluginspage", EMPTY_STRING));
                        this.embeds.put(url, ie);
                        mergeAnchors(url, tagopts);
                    }
                }
            } catch (final NumberFormatException e) {}
        } else if(tagname.equalsIgnoreCase("param")) {
            final String name = tagopts.getProperty("name", EMPTY_STRING);
            if (name.equalsIgnoreCase("movie")) {
                MultiProtocolURI url = absolutePath(tagopts.getProperty("value", EMPTY_STRING));
                tagopts.put("value", url.toNormalform(true, false));
                mergeAnchors(url, tagopts /* with property "name" */);
            }
        } else if (tagname.equalsIgnoreCase("iframe")) {
            final MultiProtocolURI src = absolutePath(tagopts.getProperty("src", EMPTY_STRING));
            tagopts.put("src", src.toNormalform(true, false));
            mergeAnchors(src, tagopts /* with property "name" */);
            this.iframes.add(src);
            this.evaluationScores.match(Element.iframepath, src.toNormalform(true, false));
        } else if (tagname.equalsIgnoreCase("html")) {
            final String lang = tagopts.getProperty("lang", EMPTY_STRING);
            if (!lang.isEmpty()) // fake a language meta to preserv detection from <html lang="xx" />
                this.metas.put("dc.language",lang.substring(0,2)); // fix found entries like "hu-hu"
        }

        // fire event
        fireScrapeTag0(tagname, tagopts);
    }

    @Override
    public void scrapeTag1(final String tagname, final Properties tagopts, char[] text) {
        // System.out.println("ScrapeTag1: tagname=" + tagname + ", opts=" + tagopts.toString() + ", text=" + UTF8.String(text));
        if (tagname.equalsIgnoreCase("a") && text.length < 2048) {
            final String href = tagopts.getProperty("href", EMPTY_STRING);
            MultiProtocolURI url;
            if ((href.length() > 0) && ((url = absolutePath(href)) != null)) {
                final String f = url.getFileName();
                final int p = f.lastIndexOf('.');
                final String type = (p < 0) ? EMPTY_STRING : f.substring(p + 1);
                if (type.equals("png") || type.equals("gif") || type.equals("jpg") || type.equals("jpeg") || type.equals("tiff") || type.equals("tif")) {
                    // special handling of such urls: put them to the image urls
                    final ImageEntry ie = new ImageEntry(url, recursiveParse(text), -1, -1, -1);
                    addImage(this.images, ie);
                } else {
                    tagopts.put("text", recursiveParse(text));
                    tagopts.put("href", url.toNormalform(true, false)); // we must assign this because the url may have resolved backpaths and may not be absolute
                    mergeAnchors(url, tagopts);
                }
            }
            this.evaluationScores.match(Element.apath, href);
        }
        final String h;
        if ((tagname.equalsIgnoreCase("h1")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) this.headlines[0].add(h);
        } else if((tagname.equalsIgnoreCase("h2")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) this.headlines[1].add(h);
        } else if ((tagname.equalsIgnoreCase("h3")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) this.headlines[2].add(h);
        } else if ((tagname.equalsIgnoreCase("h4")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) this.headlines[3].add(h);
        } else if ((tagname.equalsIgnoreCase("h5")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) this.headlines[4].add(h);
        } else if ((tagname.equalsIgnoreCase("h6")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) this.headlines[5].add(h);
        } else if ((tagname.equalsIgnoreCase("title")) && (text.length < 1024)) {
            String t = recursiveParse(text);
            this.titles.add(t);
            this.evaluationScores.match(Element.title, t);
        } else if ((tagname.equalsIgnoreCase("b")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) this.bold.inc(h);
        } else if ((tagname.equalsIgnoreCase("strong")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) this.bold.inc(h);
        } else if ((tagname.equalsIgnoreCase("i")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) this.italic.inc(h);
        } else if ((tagname.equalsIgnoreCase("u")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) this.underline.inc(h);
        } else if ((tagname.equalsIgnoreCase("li")) && (text.length < 1024)) {
            h = recursiveParse(text);
            if (h.length() > 0) this.li.add(h);
        } else if (tagname.equalsIgnoreCase("script")) {
            final String src = tagopts.getProperty("src", EMPTY_STRING);
            if (src.length() > 0) {
                this.script.add(absolutePath(src));
                this.evaluationScores.match(Element.scriptpath, src);
            } else {
                this.evaluationScores.match(Element.scriptcode, LB.matcher(new String(text)).replaceAll(" "));
            }
        }

        // fire event
        fireScrapeTag1(tagname, tagopts, text);
    }


    @Override
    public void scrapeComment(final char[] comment) {
        this.evaluationScores.match(Element.comment, LB.matcher(new String(comment)).replaceAll(" "));
    }

    private String recursiveParse(final char[] inlineHtml) {
        if (inlineHtml.length < 14) return cleanLine(super.stripAll(inlineHtml));

        // start a new scraper to parse links inside this text
        // parsing the content
        final ContentScraper scraper = new ContentScraper(this.root, this.maxLinks);
        final TransformerWriter writer = new TransformerWriter(null, null, scraper, null, false);
        try {
            FileUtils.copy(new CharArrayReader(inlineHtml), writer);
        } catch (final IOException e) {
            Log.logException(e);
            return cleanLine(super.stripAll(inlineHtml));
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
            }
        }
        for (final Map.Entry<MultiProtocolURI, Properties> entry: scraper.getAnchors().entrySet()) {
            mergeAnchors(entry.getKey(), entry.getValue());
        }
        this.images.putAll(scraper.images);

        String line = cleanLine(super.stripAll(scraper.content.getChars()));
        scraper.close();
        return line;
    }

    public List<String> getTitles() {

        // some documents have a title tag as meta tag
        String s = this.metas.get("title");
        if (s != null && s.length() > 0) {
            LinkedHashSet<String> t = new LinkedHashSet<String>();
            t.add(s);
            t.addAll(this.titles);
            this.titles = t;
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

        if (this.titles.size() == 0) {
            // take description tag
            s = getDescription();
            if (!s.isEmpty()) this.titles.add(s);
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

    public MultiProtocolURI[] getFlash() {
        String ext;
        ArrayList<MultiProtocolURI> f = new ArrayList<MultiProtocolURI>();
        for (final MultiProtocolURI url: this.anchors.keySet()) {
            ext = url.getFileExtension();
            if (ext == null) continue;
            if (ext.equals("swf")) f.add(url);
        }
        return f.toArray(new MultiProtocolURI[f.size()]);
    }

    public boolean containsFlash() {
        String ext;
        for (final MultiProtocolURI url: this.anchors.keySet()) {
            ext = url.getFileExtension();
            if (ext == null) continue;
            if (ext.equals("swf")) return true;
        }
        return false;
    }

    public int breadcrumbCount() {
        return this.breadcrumbs;
    }
    
    public String getText() {
        try {
            return this.content.toString();
        } catch (final OutOfMemoryError e) {
            Log.logException(e);
            return "";
        }
    }

    public Map<MultiProtocolURI, Properties> getAnchors() {
        // returns a url (String) / name (String) relation
        return this.anchors;
    }

    public Map<MultiProtocolURI, String> getRSS() {
        // returns a url (String) / name (String) relation
        return this.rss;
    }

    public Map<MultiProtocolURI, String> getCSS() {
        // returns a url (String) / name (String) relation
        return this.css;
    }

    public Set<MultiProtocolURI> getFrames() {
        // returns a url (String) / name (String) relation
        return this.frames;
    }

    public Set<MultiProtocolURI> getIFrames() {
        // returns a url (String) / name (String) relation
        return this.iframes;
    }

    public Set<MultiProtocolURI> getScript() {
        return this.script;
    }

    public MultiProtocolURI getCanonical() {
        return this.canonical;
    }

    /**
     * get all images
     * @return a map of <urlhash, ImageEntry>
     */
    public Map<MultiProtocolURI, ImageEntry> getImages() {
        return this.images;
    }

    public Map<MultiProtocolURI, EmbedEntry> getEmbeds() {
        return this.embeds;
    }

    public Map<String, String> getMetas() {
        return this.metas;
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
        final String s = this.metas.get("robots");
        if (s == null) return false;
        if (s.indexOf("noindex",0) >= 0) return true;
        return false;
    }

    public String getDescription() {
        String s = this.metas.get("description");
        if (s == null) s = this.metas.get("dc.description");
        if (s == null) return EMPTY_STRING;
        return s;
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
        final ScraperInputStream htmlFilter = new ScraperInputStream(new ByteArrayInputStream(page),"UTF-8", new MultiProtocolURI("http://localhost"),null,false, maxLinks);
        String charset = htmlParser.patchCharsetEncoding(htmlFilter.detectCharset());
        htmlFilter.close();
        if (charset == null) charset = Charset.defaultCharset().toString();

        // scrape content
        final ContentScraper scraper = new ContentScraper(new MultiProtocolURI("http://localhost"), maxLinks);
        final Writer writer = new TransformerWriter(null, null, scraper, null, false);
        FileUtils.copy(new ByteArrayInputStream(page), writer, Charset.forName(charset));
        writer.close();
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

