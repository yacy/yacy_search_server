/**
 *  TextParser.java
 *  Copyright 2009 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 09.07.2009 at https://yacy.net
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

package net.yacy.document;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.input.CloseShieldInputStream;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.StrictLimitInputStream;
import net.yacy.document.parser.GenericXMLParser;
import net.yacy.document.parser.XZParser;
import net.yacy.document.parser.apkParser;
import net.yacy.document.parser.audioTagParser;
import net.yacy.document.parser.bzipParser;
import net.yacy.document.parser.csvParser;
import net.yacy.document.parser.docParser;
import net.yacy.document.parser.genericParser;
import net.yacy.document.parser.gzipParser;
import net.yacy.document.parser.gzipParser.GZIPOpeningStreamException;
import net.yacy.document.parser.htmlParser;
import net.yacy.document.parser.linkScraperParser;
import net.yacy.document.parser.mmParser;
import net.yacy.document.parser.odtParser;
import net.yacy.document.parser.ooxmlParser;
import net.yacy.document.parser.pdfParser;
import net.yacy.document.parser.pptParser;
import net.yacy.document.parser.psParser;
import net.yacy.document.parser.rssParser;
import net.yacy.document.parser.rtfParser;
import net.yacy.document.parser.sidAudioParser;
import net.yacy.document.parser.tarParser;
import net.yacy.document.parser.torrentParser;
import net.yacy.document.parser.vcfParser;
import net.yacy.document.parser.vsdParser;
import net.yacy.document.parser.xlsParser;
import net.yacy.document.parser.zipParser;
import net.yacy.document.parser.html.TagValency;
import net.yacy.document.parser.images.genericImageParser;
import net.yacy.document.parser.images.metadataImageParser;
import net.yacy.document.parser.images.svgParser;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;

public final class TextParser {

    private static final Object v = new Object();

    private static final Parser genericIdiom = new genericParser();

    /** A generic XML parser instance */
    private static final Parser genericXMLIdiom = new GenericXMLParser();

    //use LinkedHashSet for parser collection to use (init) order to prefered parser for same ext or mime
    private static final Map<String, LinkedHashSet<Parser>> mime2parser = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LinkedHashSet<Parser>> ext2parser = new ConcurrentHashMap<>();
    private static final Map<String, String> ext2mime = new ConcurrentHashMap<>();
    private static final Map<String, Object> denyMime = new ConcurrentHashMap<>();
    private static final Map<String, Object> denyExtensionx = new ConcurrentHashMap<>();

    static {
        initParser(new apkParser());
        initParser(new bzipParser());
        initParser(new XZParser());
        initParser(new csvParser());
        initParser(new docParser());
        initParser(new gzipParser());
        // AugmentParser calls internally RDFaParser (therefore add before RDFa)
        // if (Switchboard.getSwitchboard().getConfigBool("parserAugmentation", true)) initParser(new AugmentParser()); // experimental implementation, not working yet (2015-06-05)
        // RDFaParser calls internally htmlParser (therefore add before html)
        // if (Switchboard.getSwitchboard().getConfigBool("parserAugmentation.RDFa", true)) initParser(new RDFaParser()); // experimental implementation, not working yet (2015-06-04)
        initParser(new htmlParser()); // called within rdfa parser
        initParser(new genericImageParser());
        initParser(new metadataImageParser());
        initParser(new linkScraperParser());
        initParser(new mmParser());
        initParser(new odtParser());
        initParser(new ooxmlParser());
        initParser(new pdfParser());
        initParser(new pptParser());
        initParser(new psParser());
        initParser(new rssParser());
        initParser(new rtfParser());
        initParser(new sidAudioParser());
        initParser(new svgParser());
        initParser(new tarParser());
        initParser(new torrentParser());
        initParser(new vcfParser());
        initParser(new vsdParser());
        initParser(new xlsParser());
        initParser(new zipParser());
        initParser(new audioTagParser());
        /* Order is important : the generic XML parser must be initialized in last, so it will be effectively used only as a fallback one
         * when a specialized parser exists for any XML based format (examples : rssParser or ooxmlParser must be tried first) */
        initParser(genericXMLIdiom);
    }

    public static Set<Parser> parsers() {
        final Set<Parser> c = new HashSet<>();
        for (final Set<Parser> pl: ext2parser.values()) c.addAll(pl);
        for (final Set<Parser> pl: mime2parser.values()) c.addAll(pl);
        return c;
    }

    /**
     * @return the set of all supported mime types
     */
    public static Set<String> supportedMimeTypes() {
        final Set<String> mimeTypes = new HashSet<>();
        mimeTypes.addAll(mime2parser.keySet());
        return mimeTypes;
    }

    private static void initParser(final Parser parser) {
        String prototypeMime = null;
        for (final String mime: parser.supportedMimeTypes()) {
            // process the mime types
            final String mimeType = normalizeMimeType(mime);
            if (prototypeMime == null) prototypeMime = mimeType;
            LinkedHashSet<Parser> p0 = mime2parser.get(mimeType);
            if (p0 == null) {
                p0 = new LinkedHashSet<>();
                mime2parser.put(mimeType, p0);
            }
            p0.add(parser);
            AbstractParser.log.info("Parser for mime type '" + mimeType + "': " + parser.getName());
        }

        if (prototypeMime != null) for (String ext: parser.supportedExtensions()) {
            ext = ext.toLowerCase(Locale.ROOT);
            final String s = ext2mime.get(ext);
            if (s != null && !s.equals(prototypeMime)) AbstractParser.log.info("Parser for extension '" + ext + "' was set to mime '" + s + "', overwriting with new mime '" + prototypeMime + "'.");
            ext2mime.put(ext, prototypeMime);
        }

        for (String ext: parser.supportedExtensions()) {
            // process the extensions
            ext = ext.toLowerCase(Locale.ROOT);
            LinkedHashSet<Parser> p0 = ext2parser.get(ext);
            if (p0 == null) {
                p0 = new LinkedHashSet<>();
                ext2parser.put(ext, p0);
            }
            p0.add(parser);
            AbstractParser.log.info("Parser for extension '" + ext + "': " + parser.getName());
        }
    }

    public static Document[] parseSource(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final TagValency defaultValency,
            final Set<String> valencySwitchTagNames,
            final VocabularyScraper scraper,
            final int timezoneOffset,
            final int depth,
            final File sourceFile
            ) throws InterruptedException, Parser.Failure {

        BufferedInputStream sourceStream = null;
        Document[] docs = null;
        try {
            if (AbstractParser.log.isFine()) AbstractParser.log.fine("Parsing '" + location + "' from file");
            if (!sourceFile.exists() || !sourceFile.canRead() || sourceFile.length() == 0) {
                final String errorMsg = sourceFile.exists() ? "Empty resource file." : "No resource content available (2).";
                AbstractParser.log.info("Unable to parse '" + location + "'. " + errorMsg);
                throw new Parser.Failure(errorMsg, location);
            }
            sourceStream = new BufferedInputStream(new FileInputStream(sourceFile));
            docs = parseSource(location, mimeType, charset, defaultValency, valencySwitchTagNames, scraper, timezoneOffset, depth, sourceFile.length(), sourceStream);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;
            AbstractParser.log.severe("Unexpected exception in parseSource from File: " + e.getMessage(), e);
            throw new Parser.Failure("Unexpected exception: " + e.getMessage(), location);
        } finally {
            if (sourceStream != null) try { sourceStream.close(); } catch (final Exception ex) {}
        }

        return docs;
    }

    public static Document[] parseSource(
            final DigestURL location,
            String mimeType,
            final String charset,
            final TagValency defaultValency,
            final Set<String> valencySwitchTagNames,
            final VocabularyScraper scraper,
            final int timezoneOffset,
            final int depth,
            final byte[] content
            ) throws Parser.Failure {
        if (AbstractParser.log.isFine()) AbstractParser.log.fine("Parsing '" + location + "' from byte-array");
        mimeType = normalizeMimeType(mimeType);
        Set<Parser> idioms = null;
        try {
            idioms = parsers(location, mimeType);
        } catch (final Parser.Failure e) {
            final String errorMsg = "Parser Failure for extension '" + MultiProtocolURL.getFileExtension(location.getFileName()) + "' or mimetype '" + mimeType + "': " + e.getMessage();
            AbstractParser.log.warn(errorMsg);
            throw new Parser.Failure(errorMsg, location);
        }
        assert !idioms.isEmpty() : "no parsers applied for url " + location.toNormalform(true);

        final Document[] docs = parseSource(location, mimeType, idioms, charset, defaultValency, valencySwitchTagNames, scraper, timezoneOffset, depth, content, Integer.MAX_VALUE, Long.MAX_VALUE);

        return docs;
    }

    /**
     * Apply only the generic parser to the given content from location.
     */
    public static Document[] genericParseSource(
            final DigestURL location,
            String mimeType,
            final String charset,
            final TagValency defaultValency,
            final Set<String> valencySwitchTagNames,
            final VocabularyScraper scraper,
            final int timezoneOffset,
            final int depth,
            final byte[] content
            ) throws Parser.Failure {
        if (AbstractParser.log.isFine()) {
            AbstractParser.log.fine("Parsing '" + location + "' from byte-array, applying only the generic parser");
        }
        mimeType = normalizeMimeType(mimeType);
        final Set<Parser> idioms = new HashSet<>();
        idioms.add(TextParser.genericIdiom);

        return parseSource(location, mimeType, idioms, charset, defaultValency, valencySwitchTagNames, scraper, timezoneOffset, depth, content, Integer.MAX_VALUE, Long.MAX_VALUE);
    }

    private static Document[] parseSource(
            final DigestURL location,
            String mimeType,
            final String charset,
            final TagValency defaultValency,
            final Set<String> valencySwitchTagNames,
            final VocabularyScraper scraper,
            final int timezoneOffset,
            final int depth,
            final long contentLength,
            final InputStream sourceStream,
            final int maxLinks,
            final long maxBytes
            ) throws Parser.Failure {
        if (AbstractParser.log.isFine()) AbstractParser.log.fine("Parsing '" + location + "' from stream");
        mimeType = normalizeMimeType(mimeType);
        Set<Parser> idioms = null;
        try {
            idioms = parsers(location, mimeType);
        } catch (final Parser.Failure e) {
            final String errorMsg = "Parser Failure for extension '" + MultiProtocolURL.getFileExtension(location.getFileName()) + "' or mimetype '" + mimeType + "': " + e.getMessage();
            AbstractParser.log.warn(errorMsg);
            throw new Parser.Failure(errorMsg, location);
        }
        assert !idioms.isEmpty() : "no parsers applied for url " + location.toNormalform(true);

        boolean canStream = false;
        if(idioms.size() == 1) {
            canStream = true;
        } else if(idioms.size() == 2) {
            /* When there are only 2 available parsers, stream oriented parsing can still be applied when one of the 2 parsers is the generic one */
            for(final Parser idiom : idioms) {
                if(idiom instanceof genericParser) {
                    canStream = true;
                }
            }
        } else if(sourceStream instanceof ByteArrayInputStream) {
            /* Also check if we have a ByteArrayInputStream as source to prevent useless bytes duplication in a new byte array */
            canStream = true;
        }

        // if we do not have more than one non generic parser, or the content size is over MaxInt (2GB), or is over the totally available memory,
        // or stream is already in memory as a ByteArrayInputStream
        // then we use only stream-oriented parser.
        if (canStream || contentLength > Integer.MAX_VALUE || contentLength > MemoryControl.available()) {
            try {
                /* The size of the buffer on the stream must be large enough to allow parser implementations to start parsing the resource
                 * and eventually fail, but must also be larger than eventual parsers internal buffers such as BufferedInputStream.DEFAULT_BUFFER_SIZE (8192 bytes) */
                final int rewindSize = 10 * 1024;
                final InputStream markableStream;
                if(sourceStream instanceof ByteArrayInputStream) {
                    /* No nead to use a wrapping buffered stream when the source is already entirely in memory.
                     * What's more, ByteArrayInputStream has no read limit when marking.*/
                    markableStream = sourceStream;
                } else {
                    markableStream = new BufferedInputStream(sourceStream, rewindSize);
                }
                /* Mark now to allow resetting the buffered stream to the beginning of the stream */
                markableStream.mark(rewindSize);

                /* Loop on parser : they are supposed to be sorted in order to start with the most specific and end with the most generic */
                for(final Parser parser : idioms) {
                    /* Wrap in a CloseShieldInputStream to prevent SAX parsers closing the sourceStream
                     * and so let us eventually reuse the same opened stream with other parsers on parser failure */
                    CloseShieldInputStream nonCloseInputStream = CloseShieldInputStream.wrap(markableStream);

                    try {
                        return parseSource(location, mimeType, parser, charset, defaultValency, valencySwitchTagNames, scraper, timezoneOffset,
                                nonCloseInputStream, maxLinks, maxBytes);
                    } catch (final Parser.Failure e) {
                        /* Try to reset the marked stream. If the failed parser has consumed too many bytes :
                         * too bad, the marks is invalid and process fails now with an IOException */
                        markableStream.reset();

                        if(parser instanceof gzipParser && e.getCause() instanceof GZIPOpeningStreamException
                                && (idioms.size() == 1 || (idioms.size() == 2 && idioms.contains(genericIdiom)))) {
                            /* The gzip parser failed directly when opening the content stream : before falling back to the generic parser,
                             * let's have a chance to parse the stream as uncompressed. */
                            /* Indeed, this can be a case of misconfigured web server, providing both headers "Content-Encoding" with value "gzip",
                             * and "Content-type" with value such as "application/gzip".
                             * In that case our HTTP client (see GzipResponseInterceptor) is already uncompressing the stream on the fly,
                             * that's why the gzipparser fails opening the stream.
                             * (see RFC 7231 section 3.1.2.2 for "Content-Encoding" header specification https://tools.ietf.org/html/rfc7231#section-3.1.2.2)*/
                            final gzipParser gzParser = (gzipParser)parser;

                            nonCloseInputStream = CloseShieldInputStream.wrap(markableStream);

                            final Document maindoc = gzipParser.createMainDocument(location, mimeType, charset, gzParser);

                            try {
                                final Document[] docs = gzParser.parseCompressedInputStream(location,
                                        charset, timezoneOffset, depth,
                                        nonCloseInputStream, maxLinks, maxBytes);
                                if (docs != null) {
                                    maindoc.addSubDocuments(docs);
                                }
                                return new Document[] { maindoc };
                            } catch(final Exception e1) {
                                /* Try again to reset the marked stream if the failed parser has not consumed too many bytes */
                                markableStream.reset();
                            }
                        }
                    }
                }
            } catch (final IOException e) {
                throw new Parser.Failure("Error reading source", location);
            }
        }

        // in case that we know more parsers we first transform the content into a byte[] and use that as base
        // for a number of different parse attempts.

        int maxBytesToRead = -1;
        if(maxBytes < Integer.MAX_VALUE) {
            /* Load at most maxBytes + 1 :
               - to let parsers not supporting Parser.parseWithLimits detect the maxBytes size is exceeded and end with a Parser.Failure
               - but let parsers supporting Parser.parseWithLimits perform partial parsing of maxBytes content */
            maxBytesToRead = (int)maxBytes + 1;
        }
        if (contentLength >= 0 && contentLength < maxBytesToRead) {
            maxBytesToRead = (int)contentLength;
        }

        byte[] b = null;
        try {
            b = FileUtils.read(sourceStream, maxBytesToRead);
        } catch (final IOException e) {
            throw new Parser.Failure(e.getMessage(), location);
        }
        final Document[] docs = parseSource(location, mimeType, idioms, charset, defaultValency, valencySwitchTagNames, scraper, timezoneOffset, depth, b, maxLinks, maxBytes);

        return docs;
    }

    public static Document[] parseSource(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final TagValency defaultValency,
            final Set<String> valencySwitchTagNames,
            final VocabularyScraper scraper,
            final int timezoneOffset,
            final int depth,
            final long contentLength,
            final InputStream sourceStream) throws Parser.Failure {
        return parseSource(location, mimeType, charset, defaultValency, valencySwitchTagNames, scraper, timezoneOffset, depth, contentLength, sourceStream,
                Integer.MAX_VALUE, Long.MAX_VALUE);
    }

    /**
     * Try to limit the parser processing with a maximum total number of links detection (anchors, images links, media links...)
     * or a maximum amount of content bytes to parse. Limits apply only when the available parsers for the resource media type support parsing within limits
     * (see {@link Parser#isParseWithLimitsSupported()}. When available parsers do
     * not support parsing within limits, an exception is thrown when
     * content size is beyond maxBytes.
     * @param location the URL of the source
     * @param mimeType the mime type of the source, if known
     * @param charset the charset name of the source, if known
     * @param ignoreClassNames an eventual set of CSS class names whose matching html elements content should be ignored
     * @param timezoneOffset the local time zone offset
     * @param depth the current depth of the crawl
     * @param contentLength the length of the source, if known (else -1 should be used)
     * @param sourceStream a input stream
     * @param maxLinks the maximum total number of links to parse and add to the result documents
     * @param maxBytes the maximum number of content bytes to process
     * @return a list of documents that result from parsing the source, with empty or null text.
     * @throws Parser.Failure when the parser processing failed
     */
    public static Document[] parseWithLimits(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final TagValency defaultValency,
            final Set<String> valencySwitchTagNames,
            final int timezoneOffset,
            final int depth,
            final long contentLength,
            final InputStream sourceStream,
            final int maxLinks,
            final long maxBytes) throws Parser.Failure{
        return parseSource(location, mimeType, charset, defaultValency, valencySwitchTagNames, new VocabularyScraper(), timezoneOffset, depth, contentLength,
                sourceStream, maxLinks, maxBytes);
    }

    /**
     * Try to limit the parser processing with a maximum total number of links detection (anchors, images links, media links...)
     * or a maximum amount of content bytes to parse. Limits apply only when the available parsers for the resource media type support parsing within limits
     * (see {@link Parser#isParseWithLimitsSupported()}. When available parsers do
     * not support parsing within limits, an exception is thrown when
     * content size is beyond maxBytes.
     * @param location the URL of the source
     * @param mimeType the mime type of the source, if known
     * @param charset the charset name of the source, if known
     * @param timezoneOffset the local time zone offset
     * @param depth the current depth of the crawl
     * @param contentLength the length of the source, if known (else -1 should be used)
     * @param sourceStream a input stream
     * @param maxLinks the maximum total number of links to parse and add to the result documents
     * @param maxBytes the maximum number of content bytes to process
     * @return a list of documents that result from parsing the source, with empty or null text.
     * @throws Parser.Failure when the parser processing failed
     */
    public static Document[] parseWithLimits(
            final DigestURL location, final String mimeType, final String charset,
            final int timezoneOffset, final int depth, final long contentLength, final InputStream sourceStream, final int maxLinks,
            final long maxBytes) throws Parser.Failure{
        return parseSource(location, mimeType, charset, TagValency.EVAL, new HashSet<String>(), new VocabularyScraper(), timezoneOffset, depth, contentLength,
                sourceStream, maxLinks, maxBytes);
    }

    /**
     *
     * @param location the URL of the source
     * @param mimeType the mime type of the source, if known
     * @param parser a parser supporting the resource at location
     * @param charset the charset name of the source, if known
     * @param scraper a vocabulary scraper
     * @param timezoneOffset the local time zone offset
     * @param sourceStream an open input stream on the source
     * @param maxLinks the maximum total number of links to parse and add to the result documents
     * @param maxBytes the maximum number of content bytes to process
     * @return a list of documents that result from parsing the source
     * @throws Parser.Failure when the source could not be parsed
     */
    private static Document[] parseSource(
            final DigestURL location,
            final String mimeType,
            final Parser parser,
            final String charset,
            final TagValency defaultValency,
            final Set<String> valencySwitchTagNames,
            final VocabularyScraper scraper,
            final int timezoneOffset,
            final InputStream sourceStream,
            final int maxLinks,
            final long maxBytes
            ) throws Parser.Failure {
        if (AbstractParser.log.isFine()) AbstractParser.log.fine("Parsing '" + location + "' from stream");
        final String fileExt = MultiProtocolURL.getFileExtension(location.getFileName());
        final String documentCharset = htmlParser.patchCharsetEncoding(charset);
        assert parser != null;

        if (AbstractParser.log.isFine()) AbstractParser.log.fine("Parsing " + location + " with mimeType '" + mimeType + "' and file extension '" + fileExt + "'.");
        try {
            final Document[] docs;
            if(parser.isParseWithLimitsSupported()) {
                docs = parser.parseWithLimits(location, mimeType, documentCharset, defaultValency, valencySwitchTagNames, scraper, timezoneOffset, sourceStream, maxLinks, maxBytes);
            } else {
                /* Parser do not support partial parsing within limits : let's control it here*/
                final InputStream limitedSource = new StrictLimitInputStream(sourceStream, maxBytes);
                docs = parser.parse(location, mimeType, documentCharset, defaultValency, valencySwitchTagNames, scraper, timezoneOffset, limitedSource);
            }
            return docs;
        } catch(final Parser.Failure e) {
            throw e;
        } catch (final Exception e) {
            throw new Parser.Failure("parser failed: " + parser.getName(), location);
        }
    }

    /**
     * @param location the URL of the source
     * @param mimeType the mime type of the source, if known
     * @param parsers a set of parsers supporting the resource at location
     * @param charset the charset name of the source, if known
     * @param scraper a vocabulary scraper
     * @param timezoneOffset the local time zone offset
     * @param depth the current crawling depth
     * @param sourceArray the resource content bytes
     * @param maxLinks the maximum total number of links to parse and add to the result documents
     * @param maxBytes the maximum number of content bytes to process
     * @return a list of documents that result from parsing the source
     * @throws Parser.Failure when the source could not be parsed
     */
    private static Document[] parseSource(
            final DigestURL location,
            final String mimeType,
            final Set<Parser> parsers,
            final String charset,
            final TagValency defaultValency,
            final Set<String> valencySwitchTagNames,
            final VocabularyScraper scraper,
            final int timezoneOffset,
            final int depth,
            final byte[] sourceArray,
            final int maxLinks,
            final long maxBytes
            ) throws Parser.Failure {
        final String fileExt = MultiProtocolURL.getFileExtension(location.getFileName());
        if (AbstractParser.log.isFine()) AbstractParser.log.fine("Parsing " + location + " with mimeType '" + mimeType + "' and file extension '" + fileExt + "' from byte[]");
        final String documentCharset = htmlParser.patchCharsetEncoding(charset);
        assert !parsers.isEmpty();

        Document[] docs = null;
        final Map<Parser, Parser.Failure> failedParser = new HashMap<>();
        final String origName = Thread.currentThread().getName();
        Thread.currentThread().setName("parsing + " + location.toString()); // set a name to get the address in Thread Dump
        for (final Parser parser: parsers) {
            if (MemoryControl.request(sourceArray.length * 6, false)) {
                ByteArrayInputStream bis;
                if (mimeType.equals("text/plain") && parser.getName().equals("HTML Parser")) {
                    // a hack to simulate html files .. is needed for NOLOAD queues. This throws their data into virtual text/plain messages.
                    bis = new ByteArrayInputStream(UTF8.getBytes("<html><head></head><body><h1>" + UTF8.String(sourceArray) + "</h1></body><html>"));
                } else {
                    bis = new ByteArrayInputStream(sourceArray);
                }
                try {
                    if(parser.isParseWithLimitsSupported()) {
                        docs = parser.parseWithLimits(location, mimeType, documentCharset, defaultValency, valencySwitchTagNames, scraper, timezoneOffset, bis, maxLinks, maxBytes);
                    } else {
                        /* Partial parsing is not supported by this parser : check content length now */
                        if(sourceArray.length > maxBytes) {
                            throw new Parser.Failure("Content size is over maximum size of " + maxBytes + "", location);
                        }
                        docs = parser.parse(location, mimeType, documentCharset, defaultValency, valencySwitchTagNames, scraper, timezoneOffset, bis);
                    }
                } catch (final Parser.Failure e) {
                    if(parser instanceof gzipParser && e.getCause() instanceof GZIPOpeningStreamException &&
                            (parsers.size() == 1 || (parsers.size() == 2 && parsers.contains(genericIdiom)))) {
                        /* The gzip parser failed directly when opening the content stream : before falling back to the generic parser,
                         * let's have a chance to parse the stream as uncompressed. */
                        /* Indeed, this can be a case of misconfigured web server, providing both headers "Content-Encoding" with value "gzip",
                         * and "Content-type" with value such as "application/gzip".
                         * In that case our HTTP client (see GzipResponseInterceptor) is already uncompressing the stream on the fly,
                         * that's why the gzipparser fails opening the stream.
                         * (see RFC 7231 section 3.1.2.2 for "Content-Encoding" header specification https://tools.ietf.org/html/rfc7231#section-3.1.2.2)*/
                        final gzipParser gzParser = (gzipParser)parser;

                        bis = new ByteArrayInputStream(sourceArray);

                        final Document maindoc = gzipParser.createMainDocument(location, mimeType, charset, gzParser);

                        try {
                            docs = gzParser.parseCompressedInputStream(location,
                                    charset, timezoneOffset, depth,
                                    bis, maxLinks, maxBytes);
                            if (docs != null) {
                                maindoc.addSubDocuments(docs);
                            }
                            docs = new Document[] { maindoc };
                            break;
                        } catch(final Parser.Failure e1) {
                            failedParser.put(parser, e1);
                        } catch(final Exception e2) {
                            failedParser.put(parser, new Parser.Failure(e2.getMessage(), location));
                        }
                    } else {
                        failedParser.put(parser, e);
                    }
                } catch (final Exception e) {
                    failedParser.put(parser, new Parser.Failure(e.getMessage(), location));
                    //log.logWarning("tried parser '" + parser.getName() + "' to parse " + location.toNormalform(true, false) + " but failed: " + e.getMessage(), e);
                } finally {
                    try {
                        bis.close();
                    } catch(final IOException ioe) {
                        // Ignore.
                    }
                }
                if (docs != null) break;
            }
        }
        Thread.currentThread().setName(origName);

        if (docs == null) {
            if (failedParser.isEmpty()) {
                final String errorMsg = "Parsing content with file extension '" + fileExt + "' and mimetype '" + mimeType + "' failed.";
                //log.logWarning("Unable to parse '" + location + "'. " + errorMsg);
                throw new Parser.Failure(errorMsg, location);
            }
            String failedParsers = "";
            for (final Map.Entry<Parser, Parser.Failure> error: failedParser.entrySet()) {
                AbstractParser.log.warn("tried parser '" + error.getKey().getName() + "' to parse " + location.toNormalform(true) + " but failed: " + error.getValue().getMessage(), error.getValue());
                failedParsers += error.getKey().getName() + " ";
            }
            throw new Parser.Failure("All parser failed: " + failedParsers, location);
        }
        for (final Document d: docs) {
            final InputStream textStream = d.getTextStream();
            assert textStream != null : "mimeType = " + mimeType;
            try {
                if(textStream != null) {
                    /* textStream can be a FileInputStream : we must close it to ensure releasing system resource */
                    textStream.close();
                }
            } catch (final IOException e) {
                AbstractParser.log.warn("Could not close text input stream");
            }
            d.setDepth(depth);
        } // verify docs

        return docs;
    }

    /**
     * check if the parser supports the given content.
     * @param url
     * @param mimeType
     * @return returns null if the content is supported. If the content is not supported, return a error string.
     */
    public static String supports(final MultiProtocolURL url, final String mimeType) {
        try {
            // try to get a parser. If this works, we don't need the parser itself, we just return null to show that everything is ok.
            final Set<Parser> idioms = parsers(url, mimeType);
            return (idioms == null || idioms.isEmpty() || (idioms.size() == 1 && idioms.iterator().next().getName().equals(genericIdiom.getName()))) ? "no parser found" : null;
        } catch (final Parser.Failure e) {
            // in case that a parser is not available, return a error string describing the problem.
            return e.getMessage();
        }
    }

    /**
     * find a parser for a given url and mime type
     * because mime types returned by web severs are sometimes wrong, we also compute the mime type again
     * from the extension that can be extracted from the url path. That means that there are 3 criteria
     * that can be used to select a parser:
     * - the given mime type (1.)
     * - the extension of url (2.)
     * - the mime type computed from the extension (3.)
     * finally the generic parser is added as backup if all above fail
     * @param url the given url
     * @param mimeType1 the given mime type
     * @return a list of Idiom parsers that may be appropriate for the given criteria
     * @throws Parser.Failure when the file extension or the MIME type is denied
     */
    private static Set<Parser> parsers(final MultiProtocolURL url, String mimeType1) throws Parser.Failure {
        final Set<Parser> idioms = new LinkedHashSet<>(2); // LinkedSet to maintain order (genericParser should be last)

        // check given mime type, place this first because this is the most likely to work and the best fit to the supplied mime
        Set<Parser> idiom;
        if (mimeType1 != null) {
            mimeType1 = normalizeMimeType(mimeType1);
            if (denyMime.containsKey(mimeType1)) throw new Parser.Failure("mime type '" + mimeType1 + "' is denied (1)", url);
            idiom = mime2parser.get(mimeType1);
            if (idiom != null) idioms.addAll(idiom);
        }

        // check extension and add as backup (in case no, wrong or unknown/unsupported mime was supplied)
        final String ext = MultiProtocolURL.getFileExtension(url.getFileName());
        if (ext != null && ext.length() > 0) {
            /* We do not throw here an exception when the media type is provided and inconsistent with the extension (if it is not supported an exception has already beeen thrown).
             * Otherwise we would reject URLs with an apparently unsupported extension but whose actual Media Type is supported (for example text/html).
             * Notable example : wikimedia commons pages, such as https://commons.wikimedia.org/wiki/File:YaCy_logo.png */
            if (denyExtensionx.containsKey(ext) && (mimeType1 == null || mimeType1.equals(mimeOf(ext)))) {
                throw new Parser.Failure("file extension '" + ext + "' is denied (1)", url);
            }
            idiom = ext2parser.get(ext);
            if (idiom != null && !idioms.containsAll(idiom)) { // use containsAll -> idiom is a Set of parser
                idioms.addAll(idiom);
            }
        }

        // check mime type computed from extension
        final String mimeType2 = ext2mime.get(ext);
        if (mimeType2 != null && (idiom = mime2parser.get(mimeType2)) != null && !idioms.containsAll(idiom)) { // use containsAll -> idiom is a Set of parser
            idioms.addAll(idiom);
        }

        /* No matching idiom has been found : let's check if the media type ends with the "+xml" suffix so we can handle it with a generic XML parser
         * (see RFC 7303 - Using '+xml' when Registering XML-Based Media Types : https://tools.ietf.org/html/rfc7303#section-4.2) */
        if(idioms.isEmpty() && mimeType1 != null && mimeType1.endsWith("+xml")) {
            idioms.add(genericXMLIdiom);
        }

        // always add the generic parser (make sure it is the last in access order)
        idioms.add(genericIdiom);
        //if (idioms.isEmpty()) throw new Parser.Failure("no parser found for extension '" + ext + "' and mime type '" + mimeType1 + "'", url);

        return idioms;
    }

    /**
     * checks if the parser supports the given mime type. It is not only checked if the parser can parse such types,
     * it is also checked if the mime type is not included in the mimetype-deny list.
     * @param mimeType
     * @return an error if the mime type is not supported, null otherwise
     */
    public static String supportsMime(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        mimeType = normalizeMimeType(mimeType);
        if (denyMime.containsKey(mimeType)) {
            return "mime type '" + mimeType + "' is denied (2)";
        }
        if (mime2parser.get(mimeType) == null) {
            /* No matching idiom has been found : let's check if the media type ends with the "+xml" suffix as can handle it with a generic XML parser
             * (see RFC 7303 - Using '+xml' when Registering XML-Based Media Types : https://tools.ietf.org/html/rfc7303#section-4.2) */
            if(!mimeType.endsWith("+xml")) {
                return "no parser for mime '" + mimeType + "' available";
            }
        }
        return null;
    }

    /**
     * checks if the parser supports the given extension. It is not only checked if the parser can parse such files,
     * it is also checked if the extension is not included in the extension-deny list.
     * @param ext extension name
     * @return an error if the extension is not supported, null otherwise
     */
    public static String supportsExtension(final String ext) {
        if (ext == null || ext.isEmpty()) return null;
        if (denyExtensionx.containsKey(ext)) return "file extension '" + ext + "' is denied (2)";
        final String mimeType = ext2mime.get(ext);
        if (mimeType == null) return "no parser available";
        final Set<Parser> idiom = mime2parser.get(mimeType);
        assert idiom != null;
        if (idiom == null || idiom.isEmpty()) return "no parser available (internal error!)";
        return null;
    }

    /**
     * checks if the parser supports the given extension or the file at the specified url. It is not only checked if the parser can parse such files,
     * it is also checked if the extension is not included in the extension-deny list.
     * @param url url to check
     * @return an error if the extension is not supported, null otherwise
     */
    public static String supportsExtension(final MultiProtocolURL url) {
        return supportsExtension(MultiProtocolURL.getFileExtension(url.getFileName()));
    }

    public static String mimeOf(final MultiProtocolURL url) {
        return mimeOf(MultiProtocolURL.getFileExtension(url.getFileName()));
    }

    public static String mimeOf(final String ext) {
        return ext2mime.get(ext.toLowerCase(Locale.ROOT));
    }

    /**
     * Normalize a media type information string (can be a HTTP "Content-Type"
     * response header) : convert to lower case, remove any supplementary
     * parameters such as the encoding (charset name), and provide a default
     * value when null.
     *
     * @param mimeType
     *            raw information about media type, eventually provided by a
     *            HTTP "Content-Type" response header
     * @return a non null media type in lower case
     */
    public static String normalizeMimeType(String mimeType) {
        if (mimeType == null) {
            return "application/octet-stream";
        }
        mimeType = mimeType.toLowerCase(Locale.ROOT);
        final int pos = mimeType.indexOf(';');
        return ((pos < 0) ? mimeType.trim() : mimeType.substring(0, pos).trim());
    }

    public static void setDenyMime(final String denyList) {
        denyMime.clear();
        String n;
        for (final String s: CommonPattern.COMMA.split(denyList)) {
            n = normalizeMimeType(s);
            if (n != null && n.length() > 0) denyMime.put(n, v);
        }
    }

    public static String getDenyMime() {
        String s = "";
        for (final String d: denyMime.keySet()) s += d + ",";
        if (!s.isEmpty()) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static void grantMime(final String mime, final boolean grant) {
        final String n = normalizeMimeType(mime);
        if (n == null || n.isEmpty()) return;
        if (grant) denyMime.remove(n); else denyMime.put(n, v);
    }

    public static void setDenyExtension(final String denyList) {
        denyExtensionx.clear();
        for (final String s: CommonPattern.COMMA.split(denyList)) denyExtensionx.put(s.trim(), v);
    }

    public static String getDenyExtension() {
        String s = "";
        for (final String d: denyExtensionx.keySet()) s += d + ",";
        if (!s.isEmpty()) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static void grantExtension(final String ext, final boolean grant) {
        if (ext == null || ext.isEmpty()) return;
        if (grant) denyExtensionx.remove(ext); else denyExtensionx.put(ext, v);
    }

}
