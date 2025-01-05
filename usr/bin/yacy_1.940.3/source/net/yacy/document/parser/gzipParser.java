//gzipParser.java
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.document.parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.compressors.gzip.GzipUtils;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.TagValency;
import net.yacy.kelondro.util.FileUtils;

/**
 * Parses a gz archive.
 * Unzips and parses the content and adds it to the created main document
 */
public class gzipParser extends AbstractParser implements Parser {

    private static final int DEFAULT_DEPTH = 999;

    public gzipParser() {
        super("GNU Zip Compressed Archive Parser");
        this.SUPPORTED_EXTENSIONS.add("gz");
        this.SUPPORTED_EXTENSIONS.add("tgz");
        this.SUPPORTED_MIME_TYPES.add("application/x-gzip");
        this.SUPPORTED_MIME_TYPES.add("application/gzip");
        this.SUPPORTED_MIME_TYPES.add("application/x-gunzip");
        this.SUPPORTED_MIME_TYPES.add("application/gzipped");
        this.SUPPORTED_MIME_TYPES.add("application/gzip-compressed");
        this.SUPPORTED_MIME_TYPES.add("gzip/document");
    }

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

        File tempFile = null;
        Document maindoc = null;
        GZIPInputStream zippedContent = null;
        FileOutputStream out = null;
        try {
            zippedContent = new GZIPInputStream(source);
        } catch(IOException e) {
            /* Use a GZIPOpeningStreamException to signal the caller the error occurred directly on stream opening
             * and eventually apply special error handling */
            throw new Parser.Failure("Unexpected error while parsing gzip file. " + e.getMessage(), location,
                    new GZIPOpeningStreamException());
        }
        try {
            int read = 0;
            final byte[] data = new byte[1024];

            tempFile = File.createTempFile("gunzip","tmp");

            // creating a temp file to store the uncompressed data
            out = new FileOutputStream(tempFile);

            // reading gzip file and store it uncompressed
            while ((read = zippedContent.read(data, 0, 1024)) != -1) {
                out.write(data, 0, read);
            }
        } catch(Exception e) {
            if (tempFile != null) {
                FileUtils.deletedelete(tempFile);
            }
            throw new Parser.Failure("Unexpected error while parsing gzip file. " + e.getMessage(), location);
        } finally {
            if(zippedContent != null) {
                try {
                    zippedContent.close();
                } catch (IOException ignored) {
                    log.warn("Could not close gzip input stream");
                }
            }
            if(out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    throw new Parser.Failure("Unexpected error while parsing gzip file. " + e.getMessage(), location);
                }
            }
        }
        try {
            maindoc = createMainDocument(location, mimeType, charset, this);
            // creating a new parser class to parse the unzipped content
            final String contentfilename = GzipUtils.getUncompressedFileName(location.getFileName());
            final String mime = TextParser.mimeOf(MultiProtocolURL.getFileExtension(contentfilename));
            Document[] docs = TextParser.parseSource(location, mime, null, defaultValency, valencySwitchTagNames, scraper, timezoneOffset, DEFAULT_DEPTH, tempFile);
            if (docs != null) maindoc.addSubDocuments(docs);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;

            throw new Parser.Failure("Unexpected error while parsing gzip file. " + e.getMessage(),location);
        } finally {
            if (tempFile != null) FileUtils.deletedelete(tempFile);
        }
        return maindoc == null ? null : new Document[]{maindoc};
    }

    /**
     * Create the main parsed document for this gzip container, register with supplied url & mime type
     * @param location the parsed resource URL
     * @param mimeType the media type of the resource
     * @param charset the charset name if known
     * @param an instance of gzipParser that is registered as the parser origin of the document
     * @return a Document instance
     */
    public static Document createMainDocument(final DigestURL location, final String mimeType, final String charset, final gzipParser parser) {
        final String filename = location.getFileName();
        Document maindoc = new Document(
                location,
                mimeType,
                charset,
                parser,
                null,
                null,
                AbstractParser.singleList(filename.isEmpty() ? location.toTokens() : MultiProtocolURL.unescape(filename)), // title
                null,
                null,
                null,
                null,
                0.0d, 0.0d,
                (Object) null,
                null,
                null,
                null,
                false,
                new Date());
        return maindoc;
    }

    /**
     * Parse content in an open stream uncompressing on the fly a gzipped resource.
     * @param location the URL of the gzipped resource 
     * @param charset the charset name if known
     * @param timezoneOffset the local time zone offset
     * @param compressedInStream an open stream uncompressing on the fly the compressed content
     * @param maxLinks
     *            the maximum total number of links to parse and add to the
     *            result documents
     * @param maxBytes
     *            the maximum number of content bytes to process
     * @return a list of documents that result from parsing the source, with
     *         empty or null text.
     * @throws Parser.Failure
     *             when the parser processing failed
     */
    public Document[] parseCompressedInputStream(final DigestURL location, final String charset, final int timezoneOffset, final int depth,
            final InputStream compressedInStream, final int maxLinks, final long maxBytes) throws Failure {
        // creating a new parser class to parse the unzipped content
        final String compressedFileName = location.getFileName();
        final String contentfilename = GzipUtils.getUncompressedFileName(compressedFileName);
        final String mime = TextParser.mimeOf(MultiProtocolURL.getFileExtension(contentfilename));
        try {
            /* Use the uncompressed file name for sub parsers to not unnecessarily use again the gzipparser */
            final String locationPath = location.getPath();
            final String contentPath = locationPath.substring(0, locationPath.length() - compressedFileName.length()) + contentfilename;
            final DigestURL contentLocation = new DigestURL(location.getProtocol(), location.getHost(), location.getPort(), contentPath);

            /* Rely on the supporting parsers to respect the maxLinks and maxBytes limits on compressed content */
            return TextParser.parseWithLimits(contentLocation, mime, charset, timezoneOffset, depth, -1, compressedInStream, maxLinks, maxBytes);
        } catch (MalformedURLException e) {
            throw new Parser.Failure("Unexpected error while parsing gzip file. " + e.getMessage(), location);
        }
    }

    @Override
    public boolean isParseWithLimitsSupported() {
        return true;
    }

    @Override
    public Document[] parseWithLimits(final DigestURL location, final String mimeType, final String charset, final VocabularyScraper scraper,
            final int timezoneOffset, final InputStream source, final int maxLinks, final long maxBytes)
            throws Parser.Failure {
        Document maindoc = null;
        GZIPInputStream zippedContent = null;
        try {
            /* Only use in-memory stream here (no temporary file) : the parsers 
             * matching compressed content are expected to handle properly the maxBytes limit and terminate 
             * before an eventual OutOfMemory occurs */
            zippedContent = new GZIPInputStream(source);
        } catch(IOException e) {
            /* Use a GZIPOpeningStreamException to signal the caller the error occurred directly on stream opening
             * and eventually apply special error handling */
            throw new Parser.Failure("Unexpected error while parsing gzip file. " + e.getMessage(), location,
                    new GZIPOpeningStreamException());
        }
        try {
            maindoc = createMainDocument(location, mimeType, charset, this);

            Document[] docs = parseCompressedInputStream(location, charset, timezoneOffset, DEFAULT_DEPTH, zippedContent, maxLinks, maxBytes);
            if (docs != null) {
                maindoc.addSubDocuments(docs);
                if(docs.length > 0 && docs[0].isPartiallyParsed()) {
                    maindoc.setPartiallyParsed(true);
                }
            }
        } catch (final Exception e) {
            throw new Parser.Failure("Unexpected error while parsing gzip file. " + e.getMessage(),location);
        }
        return maindoc == null ? null : new Document[]{maindoc};
    }

    /**
     * Used to signal an error occurred when opening a gzipped input stream.
     */
    public class GZIPOpeningStreamException extends Exception {

        /** The serialization ID */
        private static final long serialVersionUID = 2824038185373304636L;

        public GZIPOpeningStreamException() {
            super();
        }

        public GZIPOpeningStreamException(final String message) {
            super(message);
        }
    }
}
