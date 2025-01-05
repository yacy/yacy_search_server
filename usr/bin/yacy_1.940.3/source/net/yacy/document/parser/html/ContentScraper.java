// ContentScraper.java
// -----------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.NumberTools;
import net.yacy.document.SentenceReader;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.htmlParser;
import net.yacy.document.parser.html.Evaluation.Element;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.ISO639;

/**
 * A content scraper supporting HTML tags.
 */
public class ContentScraper extends AbstractScraper implements Scraper {

    private final static int MAX_TAGSIZE = 1024 * 1024;
    public static final int MAX_DOCSIZE = 40 * 1024 * 1024;

    private final char degree = '\u00B0';
    private final char[] minuteCharsHTML = "&#039;".toCharArray();

    // statics: for initialization of the HTMLFilterAbstractScraper
    /** Set of tag names processed as singletons (no end tag, or not processing the eventual end tag) */
    private static final Set<String> linkTags0 = new HashSet<>(12,0.99f);

    /** Set of tag names processed by pairs of start and end tag */
    private static final Set<String> linkTags1 = new HashSet<>(15,0.99f);

    private static final Pattern LB = Pattern.compile("\n");

    public enum TagType {
        /** Tag with no end tag (see https://www.w3.org/TR/html51/syntax.html#void-elements),
         * optional end tag (see https://www.w3.org/TR/html51/syntax.html#optional-tags),
         * or where processing directly only the start tag is desired. */
        singleton,
        /** Paired tag : has a start tag and an end tag (https://www.w3.org/TR/html51/syntax.html#normal-elements) */
        pair;
    }

    public enum TagName {
        html(TagType.singleton), // scraped as singleton to get attached properties like 'lang'
        body(TagType.singleton), // scraped as singleton to get attached properties like 'class'
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
        em(TagType.pair),
        strong(TagType.pair),
        u(TagType.pair),
        i(TagType.pair),
        li(TagType.pair),
        dt(TagType.pair),
        dd(TagType.pair),
        script(TagType.pair),
        span(TagType.pair),
        div(TagType.pair),
        nav(TagType.pair),
        article(TagType.pair), // html5
        time(TagType.pair), // html5 <time datetime>
        // tags used to capture tag content
        // TODO: considere to use </head> or <body> as trigger to scape for text content
        style(TagType.pair); // embedded css (if not declared as tag content is parsed as text)

        public TagType type;
        private TagName(final TagType type) {
            this.type = type;
        }
    }

    public static class Tag {
        public String name;
        public Properties opts;
        public CharBuffer content;
        private TagValency tv;
        public Tag(final String name, final TagValency defaultValency) {
            this.name = name;
            this.tv = defaultValency;
            this.opts = new Properties();
            this.content = new CharBuffer(MAX_TAGSIZE);
        }
        public Tag(final String name, final TagValency defaultValency, final Properties opts) {
            this.name = name;
            this.tv = defaultValency;
            this.opts = opts;
            this.content = new CharBuffer(MAX_TAGSIZE);
        }
        public Tag(final String name, final TagValency defaultValency, final Properties opts, final CharBuffer content) {
            this.name = name;
            this.tv = defaultValency;
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
        public String toString() {
            return "<" + this.name + " " + this.opts + ">" + this.content + "</" + this.name + ">";
        }

        /** @return true when this tag should be ignored from scraping */
        public boolean isIgnore() {
            return this.tv == TagValency.IGNORE;
        }
        public TagValency getValency() {
            return this.tv;
        }
        public void setValency(final TagValency tv) {
            this.tv = tv;
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
    private final SizeLimitedMap<DigestURL, String> rss, css;
    private final SizeLimitedMap<AnchorURL, EmbedEntry> embeds; // urlhash/embed relation
    private final List<ImageEntry> images;
    private final SizeLimitedSet<AnchorURL> script, frames, iframes;

    /**
     * URLs of linked data item types referenced from HTML content with standard
     * annotations such as RDFa, microdata, microformats or JSON-LD
     */
    private final SizeLimitedSet<DigestURL> linkedDataTypes;

    private final SizeLimitedMap<String, String> metas;
    private final SizeLimitedMap<String, DigestURL> hreflang, navigation;
    private final LinkedHashSet<String> titles;
    private final List<String> articles;
    private final List<Date> startDates, endDates;
    //private String headline;
    private List<String>[] headlines;
    private final ClusteredScoreMap<String> bold, italic, underline;
    private final List<String> li, dt, dd;
    private final CharBuffer content;
    private final EventListenerList htmlFilterEventListeners;
    private double lon, lat;
    private AnchorURL canonical, publisher;

    /** The maximum number of URLs to process and store in the anchors property. */
    private final int maxAnchors;

    private final VocabularyScraper vocabularyScraper;

    /** Set of CSS class names whose matching div elements may switch from IGNORE to EVAL or vice versa */
    private final Set<String> valencySwitchTagNames;
    private final TagValency defaultValency;

    private final int timezoneOffset;
    private int breadcrumbs;


    /** links to icons that belongs to the document (mapped by absolute URL)*/
    private final Map<DigestURL, IconEntry> icons;

    /**
     * The document root {@link MultiProtocolURL}
     */
    private DigestURL root;

    /**
     * evaluation scores: count appearance of specific attributes
     */
    private final Evaluation evaluationScores;

    /** Set to true when a limit on content size scraped has been exceeded */
    private boolean contentSizeLimitExceeded;

    /** Set to true when the maxAnchors limit has been exceeded */
    private boolean maxAnchorsExceeded;

    /**
     * Create an ContentScraper instance
     * @param root the document root url
     * @param maxAnchors the maximum number of URLs to process and store in the anchors property.
     * @param maxLinks the maximum number of links (other than a, area, and canonical and stylesheet links) to store
     * @param valencySwitchTagNames an eventual set of CSS class names whose matching div elements content should be ignored
     * @param defaultValency the valency default; should be TagValency.EVAL by default
     * @param vocabularyScraper handles maps from class names to vocabulary names and from documents to a map from vocabularies to terms
     * @param timezoneOffset local time zone offset
     */
    @SuppressWarnings("unchecked")
    public ContentScraper(
            final DigestURL root,
            final int maxAnchors,
            final int maxLinks,
            final Set<String> valencySwitchTagNames,
            final TagValency defaultValency,
            final VocabularyScraper vocabularyScraper,
            final int timezoneOffset) {
        // the root value here will not be used to load the resource.
        // it is only the reference for relative links
        super(linkTags0, linkTags1);
        assert root != null;
        this.root = root;
        this.vocabularyScraper = vocabularyScraper;
        this.valencySwitchTagNames = valencySwitchTagNames;
        this.defaultValency = defaultValency;
        this.timezoneOffset = timezoneOffset;
        this.evaluationScores = new Evaluation();
        this.rss = new SizeLimitedMap<>(maxLinks);
        this.css = new SizeLimitedMap<>(maxLinks);
        this.anchors = new ArrayList<>();
        this.images = new ArrayList<>();
        this.icons = new HashMap<>();
        this.embeds = new SizeLimitedMap<>(maxLinks);
        this.frames = new SizeLimitedSet<>(maxLinks);
        this.iframes = new SizeLimitedSet<>(maxLinks);
        this.linkedDataTypes = new SizeLimitedSet<>(maxLinks);
        this.metas = new SizeLimitedMap<>(maxLinks);
        this.hreflang = new SizeLimitedMap<>(maxLinks);
        this.navigation = new SizeLimitedMap<>(maxLinks);
        this.script = new SizeLimitedSet<>(maxLinks);
        this.titles = new LinkedHashSet<>();
        this.articles = new ArrayList<>();
        this.startDates = new ArrayList<>();
        this.endDates = new ArrayList<>();
        this.headlines = (List<String>[]) Array.newInstance(ArrayList.class, 6);
        for (int i = 0; i < this.headlines.length; i++) this.headlines[i] = new ArrayList<>();
        this.bold = new ClusteredScoreMap<>(false);
        this.italic = new ClusteredScoreMap<>(false);
        this.underline = new ClusteredScoreMap<>(false);
        this.li = new ArrayList<>();
        this.dt = new ArrayList<>();
        this.dd = new ArrayList<>();
        this.content = new CharBuffer(MAX_DOCSIZE, 1024);
        this.htmlFilterEventListeners = new EventListenerList();
        this.lon = 0.0d;
        this.lat = 0.0d;
        this.evaluationScores.match(Element.url, root.toNormalform(true));
        this.canonical = null;
        this.publisher = null;
        this.breadcrumbs = 0;
        this.contentSizeLimitExceeded = false;
        this.maxAnchorsExceeded = false;
        this.maxAnchors = maxAnchors;
    }

    /**
     * Create an ContentScraper instance
     * @param root the document root url
     * @param maxLinks the maximum number of links (other than a, area, and canonical and stylesheet links) to store
     * @param vocabularyScraper handles maps from class names to vocabulary names and from documents to a map from vocabularies to terms
     * @param timezoneOffset local time zone offset
     */
    public ContentScraper(
            final DigestURL root,
            final int maxLinks,
            final Set<String> valencySwitchTagNames,
            final TagValency defaultValency,
            final VocabularyScraper vocabularyScraper,
            final int timezoneOffset) {
        this(root, Integer.MAX_VALUE, maxLinks, valencySwitchTagNames, defaultValency, vocabularyScraper, timezoneOffset);
    }

    @Override
    public TagValency defaultValency() {
        return this.defaultValency;
    }

    @Override
    public void finish() {
        this.content.trimToSize();
    }

    @Override
    public void scrapeText(final char[] newtext0, final Tag insideTag) {
        if (insideTag != null) {
            if (insideTag.tv == TagValency.IGNORE) {
                return;
            }
            if ((TagName.script.name().equals(insideTag.name) || TagName.style.name().equals(insideTag.name))) {
                return;
            }
        }
        int p, pl, q, s = 0;
        final char[] newtext = CharacterCoding.html2unicode(new String(newtext0)).toCharArray();

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
                        this.lat =  Double.parseDouble(new String(newtext, r + 2, p - r - 2)) +
                                    Double.parseDouble(new String(newtext, p + pl + 1, q - p - pl - 1)) / 60.0d;
                        if (this.lon != 0.0d) break location;
                        s = q + 6;
                        continue location;
                    }
                    if (newtext[r] == 'S') {
                        this.lat = -Double.parseDouble(new String(newtext, r + 2, p - r - 2)) -
                                    Double.parseDouble(new String(newtext, p + pl + 1, q - p - pl - 1)) / 60.0d;
                        if (this.lon != 0.0d) break location;
                        s = q + 6;
                        continue location;
                    }
                    if (newtext[r] == 'E') {
                        this.lon =  Double.parseDouble(new String(newtext, r + 2, p - r - 2)) +
                                    Double.parseDouble(new String(newtext, p + pl + 1, q - p - pl - 1)) / 60.0d;
                        if (this.lat != 0.0d) break location;
                        s = q + 6;
                        continue location;
                    }
                    if (newtext[r] == 'W') {
                        this.lon = -Double.parseDouble(new String(newtext, r + 2, p - r - 2)) -
                                    Double.parseDouble(new String(newtext, p + 2, q - p - pl - 1)) / 60.0d;
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
        if ((insideTag != null) && (!(insideTag.name.equals(TagName.a.name())))) {
            // texts inside tags sometimes have no punctuation at the line end
            // this is bad for the text semantics, because it is not possible for the
            // condenser to distinguish headlines from text beginnings.
            // to make it easier for the condenser, a dot ('.') is appended in case that
            // no punctuation is part of the newtext line
            if ((b.length() != 0) && (!(SentenceReader.punctuation(b.charAt(b.length() - 1))))) b = b + '.';
            //System.out.println("*** Appended dot: " + b.toString());
        }
        // find absolute URLs inside text
        final Object[] listeners = this.htmlFilterEventListeners.getListenerList();
        final List<ContentScraperListener> anchorListeners = new ArrayList<>();
        for (int i = 0; i < listeners.length; i += 2) {
            if (listeners[i] == ContentScraperListener.class) {
                anchorListeners.add((ContentScraperListener)listeners[i+1]);
            }
        }

        if(!this.maxAnchorsExceeded) {
            int maxLinksToDetect = this.maxAnchors - this.anchors.size();
            if(maxLinksToDetect < Integer.MAX_VALUE) {
                /* Add one to the anchors limit to detect when the limit is exceeded */
                maxLinksToDetect++;
            }
            findAbsoluteURLs(b, this.anchors, anchorListeners, maxLinksToDetect);
            if(this.anchors.size() > this.maxAnchors) {
                this.maxAnchorsExceeded = true;
                this.anchors.remove(this.anchors.size() -1);
            }
        }

        // append string to content
        if (!b.isEmpty()) {
            this.content.append(b);
            this.content.appendSpace();
        }
    }

    private final static Pattern protp = Pattern.compile("smb://|ftp://|http://|https://");

    /** A regular expression pattern matching any whitespace character */
    private final static Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");

    /**
     * Try to detect and parse absolute URLs in text (at most maxURLs) , then update the urls collection and fire anchorAdded event on listeners. Any parameter can be null.
     * @param text the text to parse
     * @param urls a mutable collection of URLs to fill.
     * @param listeners a collection of listeners to trigger.
     * @param maxURLs maximum URLs number to add to the urls collection. Be careful with urls collection capacity when this collection is not null and maxURLs value is beyond Integer.MAX_VALUE.
     * @return the number of well formed URLs detected
     */
    public static long findAbsoluteURLs(final String text, final Collection<AnchorURL> urls, final Collection<ContentScraperListener> listeners, final long maxURLs) {
        if(text == null) {
            return 0;
        }
        int schemePosition, offset = 0;
        boolean hasWhiteSpace;
        String urlString;
        AnchorURL url;
        final Matcher urlSchemeMatcher = protp.matcher(text);
        final Matcher whiteSpaceMatcher = WHITESPACE_PATTERN.matcher(text);

        long detectedURLsCount = 0;
        while (offset < text.length() && detectedURLsCount < maxURLs) {
            if(!urlSchemeMatcher.find(offset)) {
                break;
            }
            schemePosition = urlSchemeMatcher.start();

            hasWhiteSpace = whiteSpaceMatcher.find(urlSchemeMatcher.end());
            urlString = text.substring(schemePosition, hasWhiteSpace ? whiteSpaceMatcher.start() : text.length());

            if (urlString.endsWith(".")) {
                urlString = urlString.substring(0, urlString.length() - 1); // remove the '.' that was appended above
            }
            /* URLs can contain brackets, furthermore as they can even be reserved characters in the URI syntax (see https://tools.ietf.org/html/rfc3986#section-2.2)
             * But when unpaired, in most cases this is that the unpaired bracket is not part of the URL, but rather used to wrap it in the text*/
            urlString = removeUnpairedBrackets(urlString, '(', ')');
            urlString = removeUnpairedBrackets(urlString, '{', '}');
               urlString = removeUnpairedBrackets(urlString, '[', ']');

            offset = schemePosition + urlString.length();
            try {
                url = new AnchorURL(urlString);
                detectedURLsCount++;
                if(urls != null) {
                    urls.add(url);
                }
                if(listeners != null) {
                    for(final ContentScraperListener listener : listeners) {
                        listener.anchorAdded(url.toNormalform(false));
                    }
                }
            } catch (final MalformedURLException ignored) {}
        }
        return detectedURLsCount;
    }

    /**
     * Try to detect and parse absolute URLs in text, then update the urls collection and fire anchorAdded event on listeners. Any parameter can be null.
     * @param text the text to parse
     * @param urls a mutable collection of URLs to fill.
     * @param listeners a collection of listeners to trigger.
     */
    public static void findAbsoluteURLs(final String text, final Collection<AnchorURL> urls, final Collection<ContentScraperListener> listeners) {
        findAbsoluteURLs(text, urls, listeners, Long.MAX_VALUE);
    }

    /**
     * Analyze bracket pairs found in the string and eventually
     * return a truncated version of that string when one or more pairs are incomplete
     *
     * @param str
     *            the string to analyze
     * @param openingMark
     *            the opening bracket character (example : '{')
     * @param closingMark
     *            the closing bracket character (example : '}')
     * @return the original string or a truncated copy
     */
    protected static String removeUnpairedBrackets(final String str, final char openingMark,
            final char closingMark) {
        if(str == null) {
            return null;
        }
        String result = str;
        char ch;
        int depth = 0, index = 0, lastUnpairedOpeningIndex = -1;
        /* Loop on all characters of the string */
        for(; index < str.length(); index++) {
            ch = str.charAt(index);
            if(ch == openingMark) {
                if(depth == 0) {
                    lastUnpairedOpeningIndex = index;
                }
                depth++;
            } else if(ch == closingMark) {
                depth--;
                if(depth == 0) {
                    lastUnpairedOpeningIndex = -1;
                }
            }
            if(depth < 0) {
                /* Unpaired closing mark : stop the loop here */
                break;
            }
        }

        if (depth > 0) {
            /* One or more unpaired opening marks : truncate at the first opening level */
            if(lastUnpairedOpeningIndex >= 0) {
                result = str.substring(0, lastUnpairedOpeningIndex);
            }
        } else if (depth < 0) {
            /* One or more unpaired closing marks : truncate at the current index as the loop should have been exited with a break */
            if(index >= 0) {
                result = str.substring(0, index);
            }
        }
        return result;
    }

    /**
     * @param relativePath relative path to this document base URL
     * @return the absolute URL (concatenation of this document root with the relative path) or null when malformed
     */
    private AnchorURL absolutePath(final String relativePath) {
        try {
            return AnchorURL.newAnchor(this.root, relativePath);
        } catch (final Exception e) {
            return null;
        }
    }

    /**
     * Parse the eventual microdata itemtype attribute of a tag and extract its
     * valid URL tokens when the itemscope attribute is present.
     *
     * @param tagAttributes parsed HTML tag attributes.
     * @return a set of URLs eventually empty when no itemtype attribute is present
     *         or when its value is not valid
     * @see <a href="https://www.w3.org/TR/microdata/#dfn-itemtype">itemtype
     *      definition at W3C</a>
     * @see <a href=
     *      "https://html.spec.whatwg.org/multipage/microdata.html#attr-itemtype">itemtype
     *      definition at WHATWG</a>
     */
    private Set<DigestURL> parseMicrodataItemType(final Properties tagAttributes) {
        final Set<DigestURL> types = new HashSet<>();
        if (tagAttributes != null) {
            /*
             * The itemtype attribute must not be specified on elements that do not have an
             * itemscope attribute specified. So we lazily check here for itemscope boolean
             * attribute presence (strictly conforming parsing would also check it has no
             * value or the value is the empty string or "itemscope")
             */
            if (tagAttributes.getProperty("itemscope") != null) {
                final Set<String> itemTypes = parseSpaceSeparatedTokens(tagAttributes.getProperty("itemtype"));

                for (final String itemType : itemTypes) {
                    try {
                        types.add(new DigestURL(itemType));
                    } catch (final MalformedURLException ignored) {
                        /* Each itemtype space-separated token must be a valid absolute URL */
                    }
                }
            }
        }
        return types;
    }

    private void checkOpts(final Tag tag) {
        // vocabulary classes
        final String classprop = tag.opts.getProperty("class", EMPTY_STRING);
        this.vocabularyScraper.check(this.root, classprop, tag.content);

        // itemprop microdata property (standard definition at https://www.w3.org/TR/microdata/#dfn-attr-itemprop)
        final String itemprop = tag.opts.getProperty("itemprop");
        if (itemprop != null) {
            String propval = tag.opts.getProperty("content"); // value for <meta itemprop="" content=""> see https://html.spec.whatwg.org/multipage/microdata.html#values
            if (propval == null) propval = tag.opts.getProperty("datetime"); // html5 + schema.org#itemprop example: <time itemprop="startDate" datetime="2016-01-26">today</time> while each prop is optional
            if (propval != null) {                                           // html5 example: <time datetime="2016-01-26">today</time> while each prop is optional
                // check <itemprop with value="" > (schema.org)
                switch (itemprop) {
                    // <meta> itemprops of main element with microdata <div itemprop="geo" itemscope itemtype="http://schema.org/GeoCoordinates">
                    case "latitude": // <meta itemprop="latitude" content="47.2649990" />
                        this.lat = Double.parseDouble(propval); // TODO: possibly overwrite existing value (multiple coordinates in document)
                        break;                                  // TODO: risk to mix up existing coordinate if longitude not given too
                    case "longitude": // <meta itemprop="longitude" content="11.3428720" />
                        this.lon = Double.parseDouble(propval); // TODO: possibly overwrite existing value (multiple coordinates in document)
                        break;                                  // TODO: risk to mix up existing coordinate if latitude not given too

                    case "startDate": // <meta itemprop="startDate" content="2016-04-21T20:00">
                        try {
                            // parse ISO 8601 date
                            final Date startDate = ISO8601Formatter.FORMATTER.parse(propval, this.timezoneOffset).getTime();
                            this.startDates.add(startDate);
                        } catch (final ParseException e) {}
                        break;
                    case "endDate":
                        try {
                            // parse ISO 8601 date
                            final Date endDate = ISO8601Formatter.FORMATTER.parse(propval, this.timezoneOffset).getTime();
                            this.endDates.add(endDate);
                        } catch (final ParseException e) {}
                        break;
                }
            }
        }
    }

    /**
     * Parses sizes icon link attribute. (see
     * http://www.w3.org/TR/html5/links.html#attr-link-sizes) Eventual
     * duplicates are removed.
     *
     * @param sizesAttr
     *            sizes attribute string, may be null
     * @return a set of sizes eventually empty.
     */
    public static Set<Dimension> parseSizes(final String sizesAttr) {
        final Set<Dimension> sizes = new HashSet<>();
        final Set<String> tokens = parseSpaceSeparatedTokens(sizesAttr);
        for (final String token : tokens) {
            /*
             * "any" keyword may be present, but doesn't have to produce a
             * dimension result
             */
            if (token != null) {
                final Matcher matcher = IconEntry.SIZE_PATTERN.matcher(token);
                if (matcher.matches()) {
                    /* With given pattern no NumberFormatException can occur */
                    sizes.add(new Dimension(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
                }
            }
        }
        return sizes;
    }

    /**
     * Parses a space separated tokens attribute value (see
     * http://www.w3.org/TR/html5/infrastructure.html#space-separated-tokens).
     * Eventual duplicates are removed.
     *
     * @param attr
     *            attribute string, may be null
     * @return a set of tokens eventually empty
     */
    public static Set<String> parseSpaceSeparatedTokens(final String attr) {
        final Set<String> tokens = new HashSet<>();
        /* Check attr string is not empty to avoid adding a single empty string
         * in result */
        if (attr != null && !attr.trim().isEmpty()) {
            final String[] items = attr.trim().split(CommonPattern.SPACES.pattern());
            Collections.addAll(tokens, items);
        }
        return tokens;
    }

    /**
     * Retain only icon relations (standard and non standard) from tokens .
     * @param relTokens relationship tokens (parsed from a rel attribute)
     * @return a Set of icon relations, eventually empty
     */
    public Set<String> retainIconRelations(final Collection<String> relTokens) {
        final HashSet<String> iconRels = new HashSet<>();
        for(final String token : relTokens) {
            if(IconLinkRelations.isIconRel(token)) {
                iconRels.add(token.toLowerCase(Locale.ENGLISH));
            }
        }
        return iconRels;
    }

    /**
     * Process a tag processed as a singleton (no end tag, or not processing the eventual end tag)
     * @param tag the tag to parse. Must not be null.
     */
    @Override
    public void scrapeTag0(final Tag tag) {
        if (tag.tv == TagValency.IGNORE) {
            return;
        }
        checkOpts(tag);
        if (tag.name.equalsIgnoreCase("img")) {
            final String src = tag.opts.getProperty("src", EMPTY_STRING);
            try {
                if (src.length() > 0) {
                    final DigestURL url = absolutePath(src);
                    if (url != null) {
                        // use to allow parse of "550px", with better performance as Numberformat.parse
                        final int width = NumberTools.parseIntDecSubstring(tag.opts.getProperty("width", "-1")); // Integer.parseInt fails on "200px"
                        final int height = NumberTools.parseIntDecSubstring(tag.opts.getProperty("height", "-1"));
                        final ImageEntry ie = new ImageEntry(url, tag.opts.getProperty("alt", EMPTY_STRING), width, height, -1);
                        this.images.add(ie);
                    }
                }
            } catch (final NumberFormatException e) {}
            this.evaluationScores.match(Element.imgpath, src);
        } else if(tag.name.equalsIgnoreCase("base")) {
            final String baseHref = tag.opts.getProperty("href", EMPTY_STRING);
            if(!baseHref.isEmpty()) {
                /* We must use here AnchorURL.newAnchor as the base href may also be an URL relative to the document URL */
                try {
                    this.root = AnchorURL.newAnchor(this.root, baseHref);
                } catch (final MalformedURLException | RuntimeException ignored) {
                    /* Nothing more to do when the base URL is malformed */
                }
            }
        } else if (tag.name.equalsIgnoreCase("frame")) {
            final AnchorURL src = absolutePath(tag.opts.getProperty("src", EMPTY_STRING));
            if(src != null) {
                tag.opts.put("src", src.toNormalform(true));
                src.setAll(tag.opts);
                //this.addAnchor(src); // don't add the frame to the anchors because the webgraph should not contain such links (by definition)
                this.frames.add(src);
                this.evaluationScores.match(Element.framepath, src.toNormalform(true));
            }
        } else if (tag.name.equalsIgnoreCase("body")) {
            final String classprop = tag.opts.getProperty("class", EMPTY_STRING);
            this.evaluationScores.match(Element.bodyclass, classprop);
        } else if (tag.name.equalsIgnoreCase("meta")) {
            final String content = tag.opts.getProperty("content", EMPTY_STRING);
            String name = tag.opts.getProperty("name", EMPTY_STRING);
            if (name.length() > 0) {
                this.metas.put(name.toLowerCase(), content);
                if (name.toLowerCase().equals("generator")) {
                    this.evaluationScores.match(Element.metagenerator, content);
                }
            }
            name = tag.opts.getProperty("http-equiv", EMPTY_STRING);
            if (name.length() > 0) {
                this.metas.put(name.toLowerCase(), content);
            }
            name = tag.opts.getProperty("property", EMPTY_STRING);
            if (name.length() > 0) {
                this.metas.put(name.toLowerCase(), content);
            }
        } else if (tag.name.equalsIgnoreCase("area")) {
            final String areatitle = cleanLine(tag.opts.getProperty("title", EMPTY_STRING));
            //String alt   = tag.opts.getProperty("alt",EMPTY_STRING);
            final String href  = tag.opts.getProperty("href", EMPTY_STRING);
            if (href.length() > 0) {
                tag.opts.put("name", areatitle);
                final AnchorURL url = absolutePath(href);
                if(url != null) {
                    tag.opts.put("href", url.toNormalform(true));
                    url.setAll(tag.opts);
                    this.addAnchor(url);
                }
            }
        } else if (tag.name.equalsIgnoreCase("link")) {
            final String href = tag.opts.getProperty("href", EMPTY_STRING);
            final AnchorURL newLink = absolutePath(href);

            if (newLink != null) {
                tag.opts.put("href", newLink.toNormalform(true));
                final String rel = tag.opts.getProperty("rel", EMPTY_STRING);
                /* Rel attribute is supposed to be a set of space-separated tokens */
                final Set<String> relTokens = parseSpaceSeparatedTokens(rel);

                final String linktitle = tag.opts.getProperty("title", EMPTY_STRING);
                final String type = tag.opts.getProperty("type", EMPTY_STRING);
                final String hreflang = tag.opts.getProperty("hreflang", EMPTY_STRING);

                final Set<String> iconRels = retainIconRelations(relTokens);
                /* Distinguish icons from images. It will enable for example to later search only images and no icons */
                if (!iconRels.isEmpty()) {
                    final String sizesAttr = tag.opts.getProperty("sizes", EMPTY_STRING);
                    final Set<Dimension> sizes = parseSizes(sizesAttr);
                    IconEntry icon = this.icons.get(newLink);
                    /* There is already an icon with same URL for this document :
                     * they may have different rel attribute or different sizes (multi sizes ico file) or this may be a duplicate */
                    if(icon != null) {
                        icon.getRel().addAll(iconRels);
                        icon.getSizes().addAll(sizes);
                    } else {
                        icon = new IconEntry(newLink, iconRels, sizes);
                        this.icons.put(newLink, icon);
                    }
                } else if (rel.equalsIgnoreCase("canonical")) {
                    tag.opts.put("name", this.titles.size() == 0 ? "" : this.titles.iterator().next());
                    newLink.setAll(tag.opts);
                    this.addAnchor(newLink);
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
                    this.addAnchor(newLink);
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
                        // this.addAnchor(url); // don't add the embed to the anchors because the webgraph should not contain such links (by definition)
                    }
                }
            } catch (final NumberFormatException e) {}
        } else if(tag.name.equalsIgnoreCase("param")) {
            final String name = tag.opts.getProperty("name", EMPTY_STRING);
            if (name.equalsIgnoreCase("movie")) {
                final AnchorURL url = absolutePath(tag.opts.getProperty("value", EMPTY_STRING));
                if(url != null) {
                    tag.opts.put("value", url.toNormalform(true));
                    url.setAll(tag.opts);
                    this.addAnchor(url);
                }
            }
        } else if (tag.name.equalsIgnoreCase("iframe")) {
            final AnchorURL src = absolutePath(tag.opts.getProperty("src", EMPTY_STRING));
            if(src != null) {
                tag.opts.put("src", src.toNormalform(true));
                src.setAll(tag.opts);
                // this.addAnchor(src); // don't add the iframe to the anchors because the webgraph should not contain such links (by definition)
                this.iframes.add(src);
                this.evaluationScores.match(Element.iframepath, src.toNormalform(true));
            }
        } else if (tag.name.equalsIgnoreCase("html")) {
            final String lang = tag.opts.getProperty("lang", EMPTY_STRING);
            if (!lang.isEmpty()) // fake a language meta to preserv detection from <html lang="xx" />
                this.metas.put("dc.language",lang.substring(0,2)); // fix found entries like "hu-hu"
        }

        // fire event
        this.fireScrapeTag0(tag.name, tag.opts);
    }

    /**
     * Process a paired tag (has a start and an end tag)
     * @param tag the tag to process. Must not be null.
     */
    @Override
    public void scrapeTag1(final Tag tag) {
        if (tag.tv == TagValency.IGNORE) {
            return;
        }
        checkOpts(tag);
        // System.out.println("ScrapeTag1: tag.tagname=" + tag.tagname + ", opts=" + tag.opts.toString() + ", text=" + UTF8.String(text));
        if (tag.name.equalsIgnoreCase("a") && tag.content.length() < 2048) {
            final String href = tag.opts.getProperty("href", EMPTY_STRING);
            AnchorURL url;
            if ((href.length() > 0) && ((url = absolutePath(href)) != null)) {
                if (followDenied()) {
                    String rel = tag.opts.getProperty("rel", EMPTY_STRING);
                    if (rel.length() == 0) rel = "nofollow"; else if (rel.indexOf("nofollow") < 0) rel += ",nofollow";
                    tag.opts.put("rel", rel);
                }
                tag.opts.put("text", stripAllTags(tag.content.getChars())); // strip any inline html in tag text like  "<a ...> <span>test</span> </a>"
                tag.opts.put("href", url.toNormalform(true)); // we must assign this because the url may have resolved backpaths and may not be absolute
                url.setAll(tag.opts);
                this.addAnchor(url);
            }
            this.evaluationScores.match(Element.apath, href);
        }
        final String h;
        if (tag.name.equalsIgnoreCase("div")) {
           final String id = tag.opts.getProperty("id", EMPTY_STRING);
           this.evaluationScores.match(Element.divid, id);
           final String itemtype = tag.opts.getProperty("itemtype", EMPTY_STRING);
           if (itemtype.equals("http://data-vocabulary.org/Breadcrumb")) {
               this.breadcrumbs++;
           }
        } else if ((tag.name.equalsIgnoreCase("h1")) && (tag.content.length() < 1024)) {
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
        } else if ((tag.name.equalsIgnoreCase("em")) && (tag.content.length() < 1024)) {
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
        } else if ((tag.name.equalsIgnoreCase("dt")) && (tag.content.length() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(stripAllTags(tag.content.getChars())));
            if (h.length() > 0) this.dt.add(h);
        } else if ((tag.name.equalsIgnoreCase("dd")) && (tag.content.length() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(stripAllTags(tag.content.getChars())));
            if (h.length() > 0) this.dd.add(h);
        } else if (tag.name.equalsIgnoreCase("script")) {
            final String src = tag.opts.getProperty("src", EMPTY_STRING);
            if (src.length() > 0) {
                final AnchorURL absoluteSrc = absolutePath(src);
                if(absoluteSrc != null) {
                    this.script.add(absoluteSrc);
                }
                this.evaluationScores.match(Element.scriptpath, src);
            } else {
                this.evaluationScores.match(Element.scriptcode, LB.matcher(new String(tag.content.getChars())).replaceAll(" "));
            }
        } else if (tag.name.equalsIgnoreCase("article")) {
            h = cleanLine(CharacterCoding.html2unicode(stripAllTags(tag.content.getChars())));
            if (h.length() > 0) this.articles.add(h);
        } else if (tag.name.equalsIgnoreCase(TagName.time.name())) { // html5 tag <time datetime="2016-12-23">Event</time>
            h = tag.opts.getProperty("datetime"); // TODO: checkOpts() also parses datetime property if in combination with schema.org itemprop=startDate/endDate
            if (h != null) { // datetime property is optional
                try {
                    final Date startDate = ISO8601Formatter.FORMATTER.parse(h, this.timezoneOffset).getTime();
                    this.startDates.add(startDate);
                } catch (final ParseException ex) { }
            }
        }

        // fire event
        this.fireScrapeTag1(tag.name, tag.opts, tag.content.getChars());
    }

    /**
     * Scraping operation applied to any kind of tag opening, being either singleton
     * or paired tag, not restricted to tags listed in
     * {@link ContentScraper#linkTags0} and {@link ContentScraper#linkTags1}.
     */
    @Override
    public void scrapeAnyTagOpening(final Tag tag) {
        if (tag != null && tag.tv == TagValency.EVAL && tag.opts != null) {
            /*
             * HTML microdata can be annotated on any kind of tag, so we don't restrict this
             * scraping to the limited sets in linkTags0 and linkTags1
             */
            this.linkedDataTypes.addAll(parseMicrodataItemType(tag.opts));
        }
    }

    @Override
    public TagValency tagValency(final Tag tag, final Tag parentTag) {
        if (parentTag != null && parentTag.tv != this.defaultValency) return parentTag.tv;

        if (this.valencySwitchTagNames != null &&
            tag != null &&
            (TagName.div.name().equals(tag.name) || TagName.nav.name().equals(tag.name))) {
            final String classAttr = tag.opts.getProperty("class", EMPTY_STRING);
            final Set<String> classes = ContentScraper.parseSpaceSeparatedTokens(classAttr);
            if (!Collections.disjoint(this.valencySwitchTagNames, classes)) return this.defaultValency.reverse();
        }
        return this.defaultValency;
    }

    /**
     * Add an anchor to the anchors list, and trigger any eventual listener
     * @param anchor anchor to add. Must not be null.
     */
    protected void addAnchor(final AnchorURL anchor) {
        if(this.anchors.size() >= this.maxAnchors) {
            this.maxAnchorsExceeded = true;
        } else {
            this.anchors.add(anchor);
            this.fireAddAnchor(anchor.toNormalform(false));
        }
    }


    @Override
    public void scrapeComment(final char[] comment) {
        this.evaluationScores.match(Element.comment, LB.matcher(new String(comment)).replaceAll(" "));
    }

    public List<String> getTitles() {

        // some documents have a title tag as meta tag
        final String s = this.metas.get("title");
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
        final ArrayList<String> t = new ArrayList<>();
        t.addAll(this.titles);
        return t;
    }

    public String[] getHeadlines(final int i) {
        assert ((i >= 1) && (i <= this.headlines.length));
        return this.headlines[i - 1].toArray(new String[this.headlines[i - 1].size()]);
    }

    public String[] getBold() {
        final List<String> a = new ArrayList<>();
        final Iterator<String> i = this.bold.keys(false);
        while (i.hasNext()) a.add(i.next());
        return a.toArray(new String[a.size()]);
    }

    public Integer[] getBoldCount(final String[] a) {
        final Integer[] counter = new Integer[a.length];
        for (int i = 0; i < a.length; i++) counter[i] = this.bold.get(a[i]);
        return counter;
    }

    public String[] getItalic() {
        final List<String> a = new ArrayList<>();
        final Iterator<String> i = this.italic.keys(false);
        while (i.hasNext()) a.add(i.next());
        return a.toArray(new String[a.size()]);
    }

    public Integer[] getItalicCount(final String[] a) {
        final Integer[] counter = new Integer[a.length];
        for (int i = 0; i < a.length; i++) counter[i] = this.italic.get(a[i]);
        return counter;
    }

    public String[] getUnderline() {
        final List<String> a = new ArrayList<>();
        final Iterator<String> i = this.underline.keys(false);
        while (i.hasNext()) a.add(i.next());
        return a.toArray(new String[a.size()]);
    }

    public Integer[] getUnderlineCount(final String[] a) {
        final Integer[] counter = new Integer[a.length];
        for (int i = 0; i < a.length; i++) counter[i] = this.underline.get(a[i]);
        return counter;
    }

    public String[] getLi() {
        return this.li.toArray(new String[this.li.size()]);
    }

    public String[] getDt() {
        return this.dt.toArray(new String[this.dt.size()]);
    }

    public String[] getDd() {
        return this.dd.toArray(new String[this.dd.size()]);
    }

    public List<Date> getStartDates() {
        return this.startDates;
    }

    public List<Date> getEndDates() {
        return this.endDates;
    }

    public DigestURL[] getFlash() {
        String ext;
        final ArrayList<DigestURL> f = new ArrayList<>();
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
        try {
            return this.content.trim().toString();
        } catch (final OutOfMemoryError e) {
            ConcurrentLog.logException(e);
            return "";
        }
    }

    public List<String> getArticles() {
        return this.articles;
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

    public Set<AnchorURL> getFrames() {
        // returns a url (String) / name (String) relation
        return this.frames;
    }

    public Set<AnchorURL> getIFrames() {
        // returns a url (String) / name (String) relation
        return this.iframes;
    }

    /**
     * @return URLs of linked data item types referenced from HTML content with standard
     *         annotations such as RDFa, microdata, microformats or JSON-LD
     */
    public SizeLimitedSet<DigestURL> getLinkedDataTypes() {
        return this.linkedDataTypes;
    }

    public Set<AnchorURL> getScript() {
        return this.script;
    }

    public AnchorURL getCanonical() {
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

    public Map<AnchorURL, EmbedEntry> getEmbeds() {
        return this.embeds;
    }

    public Map<String, String> getMetas() {
        return this.metas;
    }

    /**
     * @return all icons links
     */
    public Map<DigestURL, IconEntry> getIcons() {
        return this.icons;
    }

    /**
     * @return true when the limit on content size scraped has been exceeded
     */
    public boolean isContentSizeLimitExceeded() {
        return this.contentSizeLimitExceeded;
    }

    /**
     * @param contentSizeLimitExceeded set to true when a limit on content size scraped has been exceeded
     */
    public void setContentSizeLimitExceeded(final boolean contentSizeLimitExceeded) {
        this.contentSizeLimitExceeded = contentSizeLimitExceeded;
    }

    /**
     * @return true when the maxAnchors limit has been exceeded
     */
    public boolean isMaxAnchorsExceeded() {
        return this.maxAnchorsExceeded;
    }

    /**
     * @return true when at least one limit on content size, anchors number or links number has been exceeded
     */
    public boolean isLimitsExceeded() {
        return this.contentSizeLimitExceeded || this.maxAnchorsExceeded || this.css.isLimitExceeded()
                || this.rss.isLimitExceeded() || this.embeds.isLimitExceeded() || this.metas.isLimitExceeded()
                || this.hreflang.isLimitExceeded() || this.navigation.isLimitExceeded() || this.script.isLimitExceeded()
                || this.frames.isLimitExceeded() || this.iframes.isLimitExceeded() || this.linkedDataTypes.isLimitExceeded();
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
        final List<String> descriptions = new ArrayList<>();
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
        final Set<String> hs = new HashSet<>();
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
        String[] k = null;
        if (s.contains(","))
            k = commaSepPattern.split(s);
        else if (s.contains(";"))
            k = semicSepPattern.split(s);
        else
            k = s.split("\\s");

        // trim the Strings
        for (int i = 0; i < k.length; i++)
            k[i] = k[i].trim();

        // remove empty strings
        int p = 0;
        while (p < k.length) {
            if (k[p].length() == 0) {
                final String[] k1 = new String[k.length - 1];
                System.arraycopy(k, 0, k1, 0, p);
                System.arraycopy(k, p + 1, k1, p, k1.length - p);
                k = k1;
            } else {
                p++;
            }
        }

        return k;
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
        if (content != null) try {return ISO8601Formatter.FORMATTER.parse(content, this.timezoneOffset).getTime();} catch (final ParseException e) {}

        // <meta name="DC.date.modified" content="YYYY-MM-DD" />
        content = this.metas.get("dc.date.modified");
        if (content != null) try {return ISO8601Formatter.FORMATTER.parse(content, this.timezoneOffset).getTime();} catch (final ParseException e) {}

        // <meta name="DC.date.created" content="YYYY-MM-DD" />
        content = this.metas.get("dc.date.created");
        if (content != null) try {return ISO8601Formatter.FORMATTER.parse(content, this.timezoneOffset).getTime();} catch (final ParseException e) {}

        // <meta name="DC.date" content="YYYY-MM-DD" />
        content = this.metas.get("dc.date");
        if (content != null) try {return ISO8601Formatter.FORMATTER.parse(content, this.timezoneOffset).getTime();} catch (final ParseException e) {}

        // <meta name="DC:date" content="YYYY-MM-DD" />
        content = this.metas.get("dc:date");
        if (content != null) try {return ISO8601Formatter.FORMATTER.parse(content, this.timezoneOffset).getTime();} catch (final ParseException e) {}

        // <meta http-equiv="last-modified" content="YYYY-MM-DD" />
        content = this.metas.get("last-modified");
        if (content != null) try {return ISO8601Formatter.FORMATTER.parse(content, this.timezoneOffset).getTime();} catch (final ParseException e) {}

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
        final List<String> a = new ArrayList<>();
        final ClusteredScoreMap<String> scores = this.evaluationScores.getScores(modelName);
        if (scores != null) {
            final Iterator<String> i = scores.keys(false);
            while (i.hasNext()) a.add(i.next());
        }
        return a.toArray(new String[a.size()]);
    }

    public Integer[] getEvaluationModelScoreCounts(final String modelName, final String[] a) {
        final ClusteredScoreMap<String> scores = this.evaluationScores.getScores(modelName);
        final Integer[] counter = new Integer[a.length];
        if (scores != null) {
            for (int i = 0; i < a.length; i++) counter[i] = scores.get(a[i]);
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
        this.linkedDataTypes.clear();
        this.embeds.clear();
        this.images.clear();
        this.icons.clear();
        this.metas.clear();
        this.hreflang.clear();
        this.navigation.clear();
        this.titles.clear();
        this.articles.clear();
        this.startDates.clear();
        this.endDates.clear();
        this.headlines = null;
        this.bold.clear();
        this.italic.clear();
        this.underline.clear();
        this.li.clear();
        this.dt.clear();
        this.dd.clear();
        this.content.clear();
        this.root = null;
    }

    public void print() {
        for (final String t: this.titles) {
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

    /**
     * Register a listener for some scrape events
     * @param listener ScraperListener implementation
     */
    @Override
    public void registerHtmlFilterEventListener(final ScraperListener listener) {
        if (listener != null) {
            if(listener instanceof ContentScraperListener) {
                this.htmlFilterEventListeners.add(ContentScraperListener.class, (ContentScraperListener)listener);
            } else {
                this.htmlFilterEventListeners.add(ScraperListener.class, listener);
            }
        }
    }

    /**
     * Unregister a listener previously registered
     * @param listener ScraperListener implementation
     */
    @Override
    public void deregisterHtmlFilterEventListener(final ScraperListener listener) {
        if (listener != null) {
            if(listener instanceof ContentScraperListener) {
                this.htmlFilterEventListeners.remove(ContentScraperListener.class, (ContentScraperListener)listener);
            } else {
                this.htmlFilterEventListeners.remove(ScraperListener.class, listener);
            }
        }
    }

    private void fireScrapeTag0(final String tagname, final Properties tagopts) {
        final Object[] listeners = this.htmlFilterEventListeners.getListenerList();
        for (int i = 0; i < listeners.length; i += 2) {
            if (listeners[i] == ScraperListener.class || listeners[i] == ContentScraperListener.class) {
                    ((ScraperListener)listeners[i+1]).scrapeTag0(tagname, tagopts);
            }
        }
    }

    private void fireScrapeTag1(final String tagname, final Properties tagopts, final char[] text) {
        final Object[] listeners = this.htmlFilterEventListeners.getListenerList();
        for (int i = 0; i < listeners.length; i += 2) {
            if (listeners[i] == ScraperListener.class  || listeners[i] == ContentScraperListener.class) {
                    ((ScraperListener)listeners[i+1]).scrapeTag1(tagname, tagopts, text);
            }
        }
    }

    /**
     * Fire addAnchor event to any listener implemening {@link ContentScraperListener} interface
     * @param url anchor url
     */
    private void fireAddAnchor(final String anchorURL) {
        final Object[] listeners = this.htmlFilterEventListeners.getListenerList();
        for (int i = 0; i < listeners.length; i += 2) {
            if (listeners[i] == ContentScraperListener.class) {
                    ((ContentScraperListener)listeners[i+1]).anchorAdded(anchorURL);
            }
        }
    }

    public static ContentScraper parseResource(final File file, final int maxLinks, final int timezoneOffset) throws IOException {
        // load page
        final byte[] page = FileUtils.read(file);
        if (page == null) throw new IOException("no content in file " + file.toString());

        // scrape document to look up charset
        final ScraperInputStream htmlFilter = new ScraperInputStream(
                new ByteArrayInputStream(page),
                StandardCharsets.UTF_8.name(),
                new HashSet<String>(), TagValency.EVAL,
                new VocabularyScraper(),
                new DigestURL("http://localhost"),
                false, maxLinks, timezoneOffset);
        String charset = htmlParser.patchCharsetEncoding(htmlFilter.detectCharset());
        htmlFilter.close();
        if (charset == null) charset = Charset.defaultCharset().toString();

        // scrape content
        final ContentScraper scraper = new ContentScraper(
                new DigestURL("http://localhost"),
                maxLinks,
                new HashSet<String>(),
                TagValency.EVAL,
                new VocabularyScraper(),
                timezoneOffset);
        final Writer writer = new TransformerWriter(null, null, scraper, false);
        FileUtils.copy(new ByteArrayInputStream(page), writer, Charset.forName(charset));
        writer.close();
        return scraper;
    }

}

