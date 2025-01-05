// AbstractCompressorParser.java
// ---------------------------
// Copyright 2018 by luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
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

package net.yacy.document.parser;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.Set;

import org.apache.commons.compress.compressors.CompressorInputStream;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.TagValency;

/**
 * Base class for parsing compressed files relying on Apache commons-compress
 * tools.
 */
public abstract class AbstractCompressorParser extends AbstractParser implements Parser {

    /** Crawl depth applied when parsing internal compressed content */
    protected static final int DEFAULT_DEPTH = 999;

    /**
     * @param name the human readable name of the parser
     */
    public AbstractCompressorParser(final String name) {
        super(name);
    }

    /**
     * @param source an open input stream on a compressed source
     * @return a sub class of CompressorInputStream capable of uncompressing the source
     *         on the fly
     * @throws IOException when an error occurred when trying to open the compressed
     *                     stream
     */
    protected abstract CompressorInputStream createDecompressStream(final InputStream source) throws IOException;

    /**
     * Maps the given name of a compressed file to the name that the
     * file should have after uncompression. For example, for "file.txt.xz", "file.txt" is returned.
     *
     * @param filename name of a compressed file
     * @return name of the corresponding uncompressed file
     */
    protected abstract String getUncompressedFilename(final String filename);

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final TagValency defaultValency,
            final Set<String> valencySwitchTagNames,
            final VocabularyScraper scraper,
            final int timezoneOffset,
            final InputStream source) throws Parser.Failure, InterruptedException {

        return parseWithLimits(location, mimeType, charset, scraper, timezoneOffset, source, Integer.MAX_VALUE,
                Long.MAX_VALUE);
    }

    @Override
    public Document[] parseWithLimits(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final TagValency defaultValency, 
            final Set<String> valencySwitchTagNames,
            final VocabularyScraper scraper,
            final int timezoneOffset,
            final InputStream source,
            final int maxLinks,
            final long maxBytes) throws Parser.Failure {
        Document maindoc;
        final CompressorInputStream compressedInStream;
        try {
            compressedInStream = createDecompressStream(source);
        } catch (final IOException | RuntimeException e) {
            throw new Parser.Failure("Unexpected error while parsing compressed file. " + e.getMessage(), location);
        }

        try {
            // create maindoc for this archive, register with supplied url & mime
            maindoc = AbstractCompressorParser.createMainDocument(location, mimeType, charset, this);

            final Document[] docs = this.parseCompressedInputStream(location, null, defaultValency, valencySwitchTagNames, timezoneOffset,
                    AbstractCompressorParser.DEFAULT_DEPTH, compressedInStream, maxLinks, maxBytes);
            if (docs != null) {
                maindoc.addSubDocuments(docs);
                if (docs.length > 0 && docs[0].isPartiallyParsed()) {
                    maindoc.setPartiallyParsed(true);
                }
            }
        } catch (final Parser.Failure e) {
            throw e;
        } catch (final IOException | RuntimeException e) {
            throw new Parser.Failure("Unexpected error while parsing compressed file. " + e.getMessage(), location);
        }
        return new Document[] { maindoc };
    }

    /**
     * Create the main parsed document for the compressed document at the given URL
     * and Media type
     *
     * @param location the parsed resource URL
     * @param mimeType the media type of the resource
     * @param charset  the charset name if known
     * @param parser   an instance of CompressorParser that is registered as the
     *                 parser origin of the document
     * @return a Document instance
     */
    protected static Document createMainDocument(final DigestURL location, final String mimeType, final String charset,
            final AbstractCompressorParser parser) {
        final String filename = location.getFileName();
        return new Document(location, mimeType, charset, parser, null, null,
                AbstractParser
                        .singleList(filename.isEmpty() ? location.toTokens() : MultiProtocolURL.unescape(filename)), // title
                null, null, null, null, 0.0d, 0.0d, (Object) null, null, null, null, false, new Date());
    }

    /**
     * Parse content in an open stream uncompressing on the fly a compressed
     * resource.
     *
     * @param location           the URL of the compressed resource
     * @param charset            the charset name if known
     * @param ignoreClassNames   an eventual set of CSS class names whose matching
     *                           html elements content should be ignored
     * @param timezoneOffset     the local time zone offset
     * @param compressedInStream an open stream uncompressing on the fly the
     *                           compressed content
     * @param maxLinks           the maximum total number of links to parse and add
     *                           to the result documents
     * @param maxBytes           the maximum number of content bytes to process
     * @return a list of documents that result from parsing the source, with empty
     *         or null text.
     * @throws Parser.Failure when the parser processing failed
     */
    protected Document[] parseCompressedInputStream(
            final DigestURL location,
            final String charset,
            final TagValency defaultValency, 
            final Set<String> valencySwitchTagNames,
            final int timezoneOffset, final int depth,
            final CompressorInputStream compressedInStream,
            final int maxLinks,
            final long maxBytes) throws Failure {
        final String compressedFileName = location.getFileName();
        final String contentfilename = getUncompressedFilename(compressedFileName);
        final String mime = TextParser.mimeOf(MultiProtocolURL.getFileExtension(contentfilename));
        try {
            /*
             * Use the uncompressed file name for sub parsers to not unnecessarily use again
             * this same uncompressing parser
             */
            final String locationPath = location.getPath();
            final String contentPath = locationPath.substring(0, locationPath.length() - compressedFileName.length())
                    + contentfilename;
            final DigestURL contentLocation = new DigestURL(location.getProtocol(), location.getHost(),
                    location.getPort(), contentPath);

            /*
             * Rely on the supporting parsers to respect the maxLinks and maxBytes limits on
             * compressed content
             */
            return TextParser.parseWithLimits(
                    contentLocation, mime, charset, defaultValency, valencySwitchTagNames, timezoneOffset, depth,
                    -1, compressedInStream, maxLinks, maxBytes);
        } catch (final MalformedURLException e) {
            throw new Parser.Failure("Unexpected error while parsing compressed file. " + e.getMessage(), location);
        }
    }

    @Override
    public boolean isParseWithLimitsSupported() {
        return true;
    }

}
