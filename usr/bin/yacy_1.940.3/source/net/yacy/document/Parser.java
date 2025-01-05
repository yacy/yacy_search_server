/**
 *  Parser.java
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 29.6.2010 at https://yacy.net
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

// this is a new definition of the parser interface using multiple documents as result set
// and a much simpler method structure with only one single parser method to implement

package net.yacy.document;

import java.io.InputStream;
import java.util.Set;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.document.parser.html.TagValency;

public interface Parser {

    /**
     * each parser must define a set of supported mime types
     * @return a set of mime type strings that are supported
     */
    public Set<String> supportedMimeTypes();

    /**
     * each parser must define a set of supported file extensions
     * @return a set of file name extensions that are supported
     */
    public Set<String> supportedExtensions();

    /**
     * parse an input stream
     * @param url the url of the source
     * @param mimeType the mime type of the source, if known
     * @param charset the charset name of the source, if known
     * @param scraper an entity scraper to detect facets from text annotation context
     * @param timezoneOffset the local time zone offset
     * @param source a input stream
     * @return a list of documents that result from parsing the source
     * @throws Parser.Failure when the parser processing failed
     * @throws InterruptedException when the processing was interrupted before termination
     */
    public Document[] parse(
            DigestURL url,
            String mimeType,
            String charset,
            VocabularyScraper scraper,
            int timezoneOffset,
            InputStream source
            ) throws Parser.Failure, InterruptedException;

    public Document[] parse(
            DigestURL url,
            String mimeType,
            String charset,
            final TagValency defaultValency,
            final Set<String> valencySwitchTagNames,
            VocabularyScraper scraper,
            int timezoneOffset,
            InputStream source
            ) throws Parser.Failure, InterruptedException;

    /**
    * Parse an input stream, eventually terminating processing when a total of
    * maxLinks URLS (anchors, images links, media links...) have been reached,
    * or when maxBytes content bytes have been processed, thus potentially
    * resulting in partially parsed documents (with
    * {@link Document#isPartiallyParsed()} returning true). Some parser
    * implementations will not support parsing within maxLinks or maxBytes
    * limits : make sure to check this by calling fist
    * {@link #isParseWithLimitsSupported()}, or a UnsupportedOperationException
    * could be thrown.
    * 
    * @param url
    *            the URL of the source
    * @param mimeType
    *            the mime type of the source, if known
    * @param charset
    *            the charset name of the source, if known
    * @param scraper
    *            an entity scraper to detect facets from text annotation
    *            context
    * @param timezoneOffset
    *            the local time zone offset
    * @param source
    *            a input stream
    * @param maxLinks
    *            the maximum total number of links to parse and add to the
    *            result documents
    * @param maxBytes
    *            the maximum number of content bytes to process
    * @return a list of documents that result from parsing the source, with
    *         empty or null text.
    * @throws Parser.Failure
    *             when the parser processing failed
    * @throws InterruptedException
    *             when the processing was interrupted before termination
    * @throws UnsupportedOperationException
    *             when the parser implementation doesn't support parsing within
    *             limits
    */
    public Document[] parseWithLimits(
            DigestURL url,
            String mimeType,
            String charset,
            VocabularyScraper scraper,
            int timezoneOffset,
            InputStream source,
            int maxLinks,
            long maxBytes)
                    throws Parser.Failure, InterruptedException, UnsupportedOperationException;


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
                    throws Parser.Failure, InterruptedException, UnsupportedOperationException;

    /**
    * @return true when the parser implementation supports the
    *         parseWithLimits() operation.
    */
    public boolean isParseWithLimitsSupported();

    // methods to that shall make it possible to put Parser objects into a hashtable

    /**
     * get the name of the parser
     * @return the name of the parser
     */
    public String getName();

    /**
     * check equivalence of parsers; this simply tests equality of parser names
     * @return true when this parser is equivalent to o
     */
    @Override
    public boolean equals(Object o);

    /**
     * the hash code of a parser
     * @return the hash code of the parser name string
     */
    @Override
    public int hashCode();

    /**
     * a parser warning
     * thrown as an exception
     */
    public class Failure extends Exception {

        private static final long serialVersionUID = 2278214953869122883L;
        private MultiProtocolURL url = null;
        public Failure() {
            super();
        }

        public Failure(final String message, final MultiProtocolURL url) {
            super(message + "; url = " + url.toNormalform(true));
            this.url = url;
        }

        public Failure(final String message, final MultiProtocolURL url, Throwable e) {
            super(message + "; url = " + url.toNormalform(true), e);
            this.url = url;
        }

        public MultiProtocolURL getURL() {
            return this.url;
        }
    }
}


