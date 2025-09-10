/**
 *  htmlParser.java
 *  Copyright 2009 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 09.07.2009 at https://yacy.net
 *
 * $LastChangedDate$
 * $LastChangedRevision$
 * $LastChangedBy$
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

package net.yacy.document.parser;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import com.ibm.icu.text.CharsetDetector;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.util.CommonPattern;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.document.parser.html.ScraperInputStream;
import net.yacy.document.parser.html.TagValency;
import net.yacy.document.parser.html.TransformerWriter;


public class htmlParser extends AbstractParser implements Parser {

    /** The default maximum number of links (other than a, area, and canonical and stylesheet links) to add to a parsed document */
    private static final int DEFAULT_MAX_LINKS = 10000;

    public htmlParser() {
        super("Streaming HTML Parser");
        this.SUPPORTED_EXTENSIONS.add("htm");
        this.SUPPORTED_EXTENSIONS.add("html");
        this.SUPPORTED_EXTENSIONS.add("shtml");
        this.SUPPORTED_EXTENSIONS.add("shtm");
        this.SUPPORTED_EXTENSIONS.add("stm");
        this.SUPPORTED_EXTENSIONS.add("xhtml");
        this.SUPPORTED_EXTENSIONS.add("phtml");
        this.SUPPORTED_EXTENSIONS.add("phtm");
        this.SUPPORTED_EXTENSIONS.add("tpl");
        this.SUPPORTED_EXTENSIONS.add("php");
        this.SUPPORTED_EXTENSIONS.add("php2");
        this.SUPPORTED_EXTENSIONS.add("php3");
        this.SUPPORTED_EXTENSIONS.add("php4");
        this.SUPPORTED_EXTENSIONS.add("php5");
        this.SUPPORTED_EXTENSIONS.add("cfm");
        this.SUPPORTED_EXTENSIONS.add("asp");
        this.SUPPORTED_EXTENSIONS.add("aspx");
        this.SUPPORTED_EXTENSIONS.add("tex");
        this.SUPPORTED_EXTENSIONS.add("txt");
        this.SUPPORTED_EXTENSIONS.add("msg");

        this.SUPPORTED_MIME_TYPES.add("text/html");
        this.SUPPORTED_MIME_TYPES.add("text/xhtml+xml");
        this.SUPPORTED_MIME_TYPES.add("application/xhtml+xml");
        this.SUPPORTED_MIME_TYPES.add("application/x-httpd-php");
        this.SUPPORTED_MIME_TYPES.add("application/x-tex");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.ms-outlook");
        this.SUPPORTED_MIME_TYPES.add("text/plain");
        this.SUPPORTED_MIME_TYPES.add("text/csv");
    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String documentCharset,
            final VocabularyScraper vocscraper,
            final int timezoneOffset,
            final InputStream sourceStream) throws Parser.Failure, InterruptedException {

        return parseWithLimits(
                location,
                mimeType,
                documentCharset,
                TagValency.EVAL,
                new HashSet<String>(),
                vocscraper,
                timezoneOffset,
                sourceStream,
                Integer.MAX_VALUE,
                DEFAULT_MAX_LINKS,
                Long.MAX_VALUE);
    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String documentCharset,
            final TagValency defaultValency,
            final Set<String> valencySwitchTagNames,
            final VocabularyScraper vocscraper,
            final int timezoneOffset,
            final InputStream sourceStream) throws Parser.Failure, InterruptedException {

        return parseWithLimits(
                location, mimeType,
                documentCharset,
                defaultValency,
                valencySwitchTagNames,
                vocscraper,
                timezoneOffset,
                sourceStream,
                Integer.MAX_VALUE,
                DEFAULT_MAX_LINKS,
                Long.MAX_VALUE);
    }
    
    @Override
    public boolean isParseWithLimitsSupported() {
        return true;
    }
    
    @Override
    public Document[] parseWithLimits(
            final DigestURL location,
            final String mimeType,
            final String documentCharset,
            final TagValency defaultValency,
            final Set<String> valencySwitchTagNames,
            final VocabularyScraper vocscraper,
            final int timezoneOffset,
            final InputStream sourceStream,
            final int maxLinks,
            final long maxBytes)
            throws Failure {
        return parseWithLimits(
                location,
                mimeType,
                documentCharset,
                defaultValency,
                valencySwitchTagNames,
                vocscraper,
                timezoneOffset,
                sourceStream,
                maxLinks,
                maxLinks,
                maxBytes);
    }
    
    private Document[] parseWithLimits(
            final DigestURL location,
            final String mimeType,
            final String documentCharset,
            final TagValency defaultValency,
            final Set<String> valencySwitchTagNames,
            final VocabularyScraper vocscraper,
            final int timezoneOffset,
            final InputStream sourceStream,
            final int maxAnchors,
            final int maxLinks,
            final long maxBytes)
            throws Failure {
        try {
            // first get a document from the parsed html
            Charset[] detectedcharsetcontainer = new Charset[]{null};
            ContentScraper scraper = parseToScraper(location, documentCharset, defaultValency, valencySwitchTagNames, vocscraper, detectedcharsetcontainer, timezoneOffset, sourceStream, maxAnchors, maxLinks, maxBytes);
            // parseToScraper also detects/corrects/sets charset from html content tag
            final Document document = transformScraper(location, mimeType, detectedcharsetcontainer[0].name(), scraper);
            Document documentSnapshot = null;
            try {
                // check for ajax crawling scheme (https://developers.google.com/webmasters/ajax-crawling/docs/specification)
                // and create a sub-document for snapshot page (which will be merged by loader)
                // TODO: as a crawl request removes anchor part from original url getRef() is never successful - considere other handling as removeRef() in crawler
                if (location.getRef() != null && location.getRef().startsWith("!")) {
                    documentSnapshot = parseAlternativeSnapshot(location, mimeType, documentCharset, defaultValency, valencySwitchTagNames, vocscraper, timezoneOffset, maxAnchors, maxLinks, maxBytes);
                } else { // head tag fragment only allowed on url without anchor hashfragment, but there are discussions that existence of hashfragment anchor takes preference (means allow both)
                    if (scraper.getMetas().containsKey("fragment") && scraper.getMetas().get("fragment").equals("!")) {
                        documentSnapshot = parseAlternativeSnapshot(location, mimeType, documentCharset, defaultValency, valencySwitchTagNames, vocscraper, timezoneOffset, maxAnchors, maxLinks, maxBytes);
                    }
                }
            } catch (Exception ex1) { // ignore any exception for any issue with snapshot
                documentSnapshot = null;
            }

            return documentSnapshot == null ? new Document[]{document} : new Document[]{document, documentSnapshot};
        } catch (final IOException e) {
            throw new Parser.Failure("IOException in htmlParser: " + e.getMessage(), location);
        }
    }
    
    

    /**
     *  the transformScraper method transforms a scraper object into a document object
     * @param location
     * @param mimeType
     * @param charSet
     * @param scraper
     * @return a Document instance
     */
    private Document transformScraper(final DigestURL location, final String mimeType, final String charSet, final ContentScraper scraper) {
        final String[] sections = new String[
                 scraper.getHeadlines(1).length +
                 scraper.getHeadlines(2).length +
                 scraper.getHeadlines(3).length +
                 scraper.getHeadlines(4).length +
                 scraper.getHeadlines(5).length +
                 scraper.getHeadlines(6).length];
        int p = 0;
        for (int i = 1; i <= 6; i++) {
            for (final String headline : scraper.getHeadlines(i)) {
                sections[p++] = headline;
            }
        }
        LinkedHashMap<DigestURL, ImageEntry> noDoubleImages = new LinkedHashMap<>();
        for (ImageEntry ie: scraper.getImages()) noDoubleImages.put(ie.url(), ie);
        final Document ppd = new Document(
                location,
                mimeType,
                charSet,
                this,
                scraper.getContentLanguages(),
                scraper.getKeywords(),
                scraper.getTitles(),
                scraper.getAuthor(),
                scraper.getPublisher(),
                sections,
                scraper.getDescriptions(),
                scraper.getLon(), scraper.getLat(),
                scraper.getText(),
                scraper.getAnchors(),
                scraper.getRSS(),
                noDoubleImages,
                scraper.indexingDenied(),
                scraper.getDate());
        ppd.setScraperObject(scraper);
        ppd.setIcons(scraper.getIcons());
        ppd.setLinkedDataTypes(scraper.getLinkedDataTypes());
        ppd.setPartiallyParsed(scraper.isLimitsExceeded());
        
        return ppd;
    }

    public static ContentScraper parseToScraper(
            final DigestURL location,
            final String documentCharset, 
            final TagValency defaultValency,
            final Set<String> valencySwitchTagNames,
            final VocabularyScraper vocabularyScraper,
            final int timezoneOffset,
            final String input,
            final int maxAnchors,
            final int maxLinks) throws IOException {
        Charset[] detectedcharsetcontainer = new Charset[]{null};
        InputStream sourceStream;
        try {
            sourceStream = new ByteArrayInputStream(documentCharset == null ? UTF8.getBytes(input) : input.getBytes(documentCharset));
        } catch (UnsupportedEncodingException e) {
            sourceStream = new ByteArrayInputStream(UTF8.getBytes(input));
        }
        ContentScraper scraper; // for this static methode no need to init local this.scraperObject
        try {
            scraper = parseToScraper(location, documentCharset, defaultValency, valencySwitchTagNames, vocabularyScraper, detectedcharsetcontainer, timezoneOffset, sourceStream, maxAnchors, maxLinks, Long.MAX_VALUE);
        } catch (Failure e) {
            throw new IOException(e.getMessage());
        }
        return scraper;
    }
    
    /**
     * Parse the resource at location and return the resulting ContentScraper
     * @param location the URL of the resource to parse
     * @param documentCharset the document charset name if known
     * @param vocabularyScraper a vocabulary scraper
     * @param detectedcharsetcontainer a mutable array of Charsets : filled with the charset detected when parsing
     * @param timezoneOffset the local time zone offset
     * @param sourceStream an open stream on the resource to parse
     * @param maxAnchors the maximum number of URLs to process and store in the in the scraper's anchors property
     * @param maxLinks the maximum number of links (other than a, area, and canonical and stylesheet links) to store in the scraper
     * @param maxBytes the maximum number of content bytes to process
     * @return a scraper containing parsed information
     * @throws Parser.Failure when an error occurred while parsing
     * @throws IOException when a read/write error occurred while trying to detect the charset
     */
    public static ContentScraper parseToScraper(
            final DigestURL location,
            final String documentCharset,
            final TagValency defaultValency,
            final Set<String> valencySwitchTagNames,
            final VocabularyScraper vocabularyScraper,
            final Charset[] detectedcharsetcontainer,
            final int timezoneOffset,
            InputStream sourceStream,
            final int maxAnchors,
            final int maxLinks,
            final long maxBytes) throws Parser.Failure, IOException {
        
        // make a scraper
        String charset = null;

        // ah, we are lucky, we got a character-encoding via HTTP-header
        if (documentCharset != null) {
            charset = patchCharsetEncoding(documentCharset);
        }

        // nothing found: try to find a meta-tag
        if (charset == null) {
            ScraperInputStream htmlFilter = null;
            try {
                htmlFilter = new ScraperInputStream(
                        sourceStream,
                        documentCharset,
                        valencySwitchTagNames,
                        defaultValency,
                        vocabularyScraper,
                        location,
                        false,
                        maxLinks,
                        timezoneOffset);
                sourceStream = htmlFilter;
                charset = htmlFilter.detectCharset();
            } catch (final IOException e1) {
                throw new Parser.Failure("Charset error:" + e1.getMessage(), location);
            } finally {
                if (htmlFilter != null) htmlFilter.close();
            }
        }

        // the author didn't tell us the encoding, try the mozilla-heuristic
        if (charset == null) {
            final CharsetDetector det = new CharsetDetector();
            det.enableInputFilter(true);
            final InputStream detStream = new BufferedInputStream(sourceStream);
            det.setText(detStream);
            charset = det.detect().getName();
            sourceStream = detStream;
        }

        // wtf? still nothing, just take system-standard
        if (charset == null) {
            detectedcharsetcontainer[0] = Charset.defaultCharset();
        } else {
            try {
                detectedcharsetcontainer[0] = Charset.forName(charset);
            } catch (final IllegalCharsetNameException e) {
                detectedcharsetcontainer[0] = Charset.defaultCharset();
            } catch (final UnsupportedCharsetException e) {
                detectedcharsetcontainer[0] = Charset.defaultCharset();
            }
        }
        
        // parsing the content
        // for this static method no need to init local this.scraperObject here
        final ContentScraper scraper = new ContentScraper(
                location,
                maxAnchors,
                maxLinks,
                valencySwitchTagNames,
                TagValency.EVAL,
                vocabularyScraper,
                timezoneOffset);
        final TransformerWriter writer = new TransformerWriter(null, null, scraper, false, Math.max(64, Math.min(4096, sourceStream.available())));
        try {
            final long maxChars = (long)(maxBytes * detectedcharsetcontainer[0].newDecoder().averageCharsPerByte());
            final Reader sourceReader = new InputStreamReader(sourceStream, detectedcharsetcontainer[0]);
            final long copiedChars = IOUtils.copyLarge(sourceReader, writer, 0, maxChars);
            if(copiedChars > maxChars) {
                /* maxChars limit has been exceeded : do not fail here as we want to use the partially obtained results. */
                scraper.setContentSizeLimitExceeded(true);
            } else if(copiedChars == maxChars) {
                /* Exactly maxChars limit reached : let's check if more to read remain. */
                if(sourceReader.read() >= 0) {
                    scraper.setContentSizeLimitExceeded(true);
                }
            }
        } catch (final IOException e) {
               throw new Parser.Failure("IO error:" + e.getMessage(), location);
        } finally {
            writer.flush();
            //sourceStream.close(); keep open for multipe parsing (close done by caller)
            writer.close();
        }
        //OutputStream hfos = new htmlFilterOutputStream(null, scraper, null, false);
        //serverFileUtils.copy(sourceFile, hfos);
        //hfos.close();
        if (writer.binarySuspect()) {
            final String errorMsg = "Binary data found in resource";
            throw new Parser.Failure(errorMsg, location);
        }
        return scraper;
    }

    /**
     * some html authors use wrong encoding names, either because they don't know exactly what they
     * are doing or they produce a type. Many times, the upper/downcase scheme of the name is fuzzy
     * This method patches wrong encoding names. The correct names are taken from
     * http://www.iana.org/assignments/character-sets
     * @param encoding
     * @return patched encoding name
     */
    public static String patchCharsetEncoding(String encoding) {

        // do nothing with null
        if ((encoding == null) || (encoding.length() < 3)) return null;

        // trim encoding string
        encoding = encoding.trim();

        // fix upper/lowercase
        encoding = encoding.toUpperCase(Locale.ROOT);
        if (encoding.startsWith("SHIFT")) return "Shift_JIS";
        if (encoding.startsWith("BIG")) return "Big5";
        // all other names but such with "windows" use uppercase
        if (encoding.startsWith("WINDOWS")) encoding = "windows" + encoding.substring(7);
        if (encoding.startsWith("MACINTOSH")) encoding = "MacRoman";

        // fix wrong fill characters
        encoding = CommonPattern.UNDERSCORE.matcher(encoding).replaceAll("-");

        if (encoding.matches("GB[_-]?2312([-_]80)?")) return "GB2312";
        if (encoding.matches(".*UTF[-_]?8.*")) return StandardCharsets.UTF_8.name();
        if (encoding.startsWith("US")) return StandardCharsets.US_ASCII.name();
        if (encoding.startsWith("KOI")) return "KOI8-R";

        // patch missing '-'
        if (encoding.startsWith("windows") && encoding.length() > 7) {
            final char c = encoding.charAt(7);
            if ((c >= '0') && (c <= '9')) {
                encoding = "windows-" + encoding.substring(7);
            }
        }

        if (encoding.startsWith("ISO")) {
            // patch typos
            if (encoding.length() > 3) {
                final char c = encoding.charAt(3);
                if ((c >= '0') && (c <= '9')) {
                    encoding = "ISO-" + encoding.substring(3);
                }
            }
            if (encoding.length() > 8) {
                final char c = encoding.charAt(8);
                if ((c >= '0') && (c <= '9')) {
                    encoding = encoding.substring(0, 8) + "-" + encoding.substring(8);
                }
            }
        }

        // patch wrong name
        if (encoding.startsWith("ISO-8559")) {
            // popular typo
            encoding = "ISO-8859" + encoding.substring(8);
        }

        // converting cp\d{4} -> windows-\d{4}
        if (encoding.matches("CP([_-])?125[0-8]")) {
            final char c = encoding.charAt(2);
            if ((c >= '0') && (c <= '9')) {
                encoding = "windows-" + encoding.substring(2);
            } else {
                encoding = "windows" + encoding.substring(2);
            }
        }

        return encoding;
    }

    /**
     * Implementation of ajax crawling scheme to crawl the content of html snapshot page
     * instead of the (empty) original ajax url
     * see https://developers.google.com/webmasters/ajax-crawling/docs/specification
     * Ajax crawling sheme is denoted by url with anchor param starting with "!" (1)
     * or by a header tag <meta name="fragment" content="!"/>
     *
     * It is expected that the check for ajax crawling scheme happend already so we can directly
     * try to get the snapshot page
     *
     * @param location original url (ajax url)
     * @param mimeType
     * @param documentCharset
     * @param vocscraper
     * @param timezoneOffset
     * @param maxAnchors the maximum number of URLs to process and store in the in the scraper's anchors property
     * @param maxLinks the maximum number of links to store in the document
     * @param maxBytes the maximum number of content bytes to process
     * @return document as result of parsed snapshot or null if not exist or on any other issue with snapshot
     */
    private Document parseAlternativeSnapshot(
            final DigestURL location, final String mimeType, final String documentCharset,
            final TagValency defaultValency, final Set<String> valencySwitchTagNames,
            final VocabularyScraper vocscraper,
            final int timezoneOffset, final int maxAnchors, final int maxLinks, final long maxBytes) {
        Document documentSnapshot = null;
        try {
            // construct url for case (1) with anchor
            final DigestURL locationSnapshot;
            if (location.getRef() != null && !location.getRef().isEmpty() && location.getRef().startsWith("!")) {
                if (location.getSearchpart().isEmpty()) {
                    // according to spec hashfragment to be escaped
                    locationSnapshot = new DigestURL(location.toNormalform(true) + "?_escaped_fragment_=" + MultiProtocolURL.escape(location.getRef().substring(1)));
                } else {
                    locationSnapshot = new DigestURL(location.toNormalform(true) + "&_escaped_fragment_=" + MultiProtocolURL.escape(location.getRef().substring(1)).toString());
                }
            } else { // construct url for case (2) - no anchor but header tag fragment="!"
                locationSnapshot = new DigestURL(location.toNormalform(true) + "?_escaped_fragment_=");
            }
            Charset[] detectedcharsetcontainer = new Charset[]{null};
            InputStream snapshotStream = null;
            try {
                snapshotStream = locationSnapshot.getInputStream(ClientIdentification.yacyInternetCrawlerAgent);
                ContentScraper scraperSnapshot = parseToScraper(location, documentCharset, defaultValency, valencySwitchTagNames, vocscraper, detectedcharsetcontainer, timezoneOffset, snapshotStream, maxAnchors, maxLinks, maxBytes);
                documentSnapshot = transformScraper(location, mimeType, detectedcharsetcontainer[0].name(), scraperSnapshot);
            } finally {
                if(snapshotStream != null) {
                    try {
                        snapshotStream.close();
                    } catch(IOException e) {
                        AbstractParser.log.warn("Could not close snapshot stream : " + e.getMessage());
                    }
                }
            }
            AbstractParser.log.info("parse snapshot "+locationSnapshot.toString() + " additional to " + location.toString());
        } catch (IOException | Failure ex) { }
        return documentSnapshot;
    }

    public static void main(final String[] args) {
        // test parsing of a url
        DigestURL url;
        try {
            url = new DigestURL(args[0]);
            final byte[] content = url.get(ClientIdentification.yacyInternetCrawlerAgent, null, null);
            final Document[] document = new htmlParser().parse(url, "text/html", StandardCharsets.UTF_8.name(), new VocabularyScraper(), 0, new ByteArrayInputStream(content));
            final String title = document[0].dc_title();
            System.out.println(title);
        } catch (final MalformedURLException e) {
            e.printStackTrace();
        } catch (final IOException e) {
            e.printStackTrace();
        } catch (final Parser.Failure e) {
            e.printStackTrace();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

}
