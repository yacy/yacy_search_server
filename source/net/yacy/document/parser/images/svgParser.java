/**
 * svgParser.java 
 * Copyright 2015 by Burkhard Buelte
 * First released 26.09.2015 at https://yacy.net
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program in the file lgpl21.txt If not, see
 * <http://www.gnu.org/licenses/>.
 */
package net.yacy.document.parser.images;

import java.io.EOFException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.NumberTools;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.ImageEntry;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Metadata parser for svg image files (which are xml files) SVG 1.1 (Second Edition)
 * http://www.w3.org/TR/SVG/metadata.html#MetadataElement according to SVG 1.1
 * parser stops parsing after the first metadata elment has been read and
 * document level metadata are expected picture data (as proposed in spec) like
 * <svg>
 * <title></title>
 * <desc></desc>
 * <metadata></metadata>
 * <... other/>
 * </svg>
 */
public class svgParser extends AbstractParser implements Parser {

    public svgParser() {
        super("SVG Image Parser");
        this.SUPPORTED_EXTENSIONS.add("svg");
        this.SUPPORTED_MIME_TYPES.add("image/svg+xml");
    }

    private static final ThreadLocal<SAXParser> tlSax = new ThreadLocal<SAXParser>();

    private static SAXParser getParser() throws SAXException {
        SAXParser parser = tlSax.get();
        if (parser == null) {
            try {
                parser = SAXParserFactory.newInstance().newSAXParser();
            } catch (final ParserConfigurationException e) {
                throw new SAXException(e.getMessage(), e);
            }
            tlSax.set(parser);
        }
        return parser;
    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper,
            final int timezoneOffset,
            final InputStream source) throws Parser.Failure, InterruptedException {

        try {
            final SAXParser saxParser = getParser();
            final svgMetaDataHandler metaData = new svgMetaDataHandler();
            try {
                saxParser.parse(source, metaData);
            } catch (SAXException e) {
                // catch EOFException which is intentionally thrown after capturing metadata to skip further reading (not a error, just a way to get out of SAX)
                if (e.getException() == null || !(e.getException() instanceof EOFException)) {
                    throw new Parser.Failure("Unexpected error while parsing svg file. " + e.getMessage(), location);
                }
            }

            String docTitle = metaData.getTitle();
            if (docTitle == null) { // use filename like in genericParser
                docTitle = location.getFileName().isEmpty() ? location.toTokens() : MultiProtocolURL.unescape(location.getFileName()); //
            }
            String docDescription = metaData.getDescription();
            if (docDescription == null) { // use url token as in genericParser
                docDescription = location.toTokens();
            }

            LinkedHashMap<DigestURL, ImageEntry> images = null;
            // add this image to the map of images to register size (as in genericImageParser)
            if (metaData.getHeight() != null && metaData.getWidth() != null) {
                images = new LinkedHashMap<DigestURL, ImageEntry>();
                images.put(location, new ImageEntry(location, "", metaData.getWidth(), metaData.getHeight(), -1));
            }

            // create the parser document
            Document[] docs = new Document[]{new Document(
                location,
                mimeType,
                StandardCharsets.UTF_8.name(),
                this,
                null,
                null,
                AbstractParser.singleList(docTitle),
                null,
                "",
                null,
                null,
                0.0d, 0.0d,
                docDescription, // text - for this image description is best text we have
                null,
                null,
                images,
                false,
                null)};
            return docs;
        } catch (final Exception e) {
            if (e instanceof InterruptedException) {
                throw (InterruptedException) e;
            }
            if (e instanceof Parser.Failure) {
                throw (Parser.Failure) e;
            }

            ConcurrentLog.logException(e);
            throw new Parser.Failure("Unexpected error while parsing svg file. " + e.getMessage(), location);
        }
    }

    /**
     * SAX handler for svg metadata
     */
    public class svgMetaDataHandler extends DefaultHandler {

        private final StringBuilder buffer = new StringBuilder();
        private boolean scrapeMetaData = false; // true if within metadata tag
        private boolean svgStartTagFound = false; // switch to recognize start tag processing, to cancel parsing on wrong tag

        private String docTitle = null; // document level title
        private String docDescription = null; // document level description
        private String imgWidth = null; // size in pixel
        private String imgHeight = null;

        public svgMetaDataHandler() {
        }

        @Override
        public void characters(final char ch[], final int start, final int length) {
            buffer.append(ch, start, length);
        }

        @Override
        public void startElement(final String uri, final String name, final String tag, final Attributes atts) throws SAXException {
            if (scrapeMetaData) {
                // not implemented yet TODO: interprete RDF content
                // may contain RDF + DC, DC, CC ...
            } else {
                if (tag != null) {
                    switch (tag) {
                        case "svg":
                            svgStartTagFound = true;
                            imgHeight = atts.getValue("height");
                            imgWidth = atts.getValue("width");
                            break;
                        case "metadata":
                            scrapeMetaData = true;
                            break;
                        // some common graph elements as stop condition (skip reading remainder of input), metadata is expected before graphic content
                        case "g":
                        case "line":
                        case "path":
                        case "rect":
                            throw new SAXException("EOF svg Metadata", new EOFException());
                        default : { // K.O. criteria, start tag is not svg, fail parser on none svg
                            if (!svgStartTagFound) {
                                throw new SAXException("not a svg file, start tag "+tag, new Failure());
                            }
                        }
                    }
                }
            }
            buffer.delete(0, buffer.length());
        }

        @Override
        public void endElement(final String uri, final String name, final String tag) throws SAXException {
            if (scrapeMetaData) {
                // stop condition, scrape only first metadata element
                if ("metadata".equals(tag)) {
                    scrapeMetaData = false;
                    buffer.delete(0, buffer.length());
                    // we have read metadate, other data are not of interest here, end parsing
                    throw new SAXException("EOF svg Metadata", new EOFException());
                }
            } else if ("title".equals(tag)) {
                this.docTitle = buffer.toString();
            } else if ("desc".equals(tag)) {
                this.docDescription = buffer.toString();
            }
            buffer.delete(0, buffer.length());
        }

        /**
         * @return document level title or null
         */
        public String getTitle() {
            return docTitle;
        }

        /**
         * @return document level description or null
         */
        public String getDescription() {
            return docDescription;
        }

        /**
         * @return image width in pixel or null
         */
        public Integer getWidth() {
            if (imgWidth != null) {
                // return number if given in pixel or a number only, return nothing for size like "100%"
                if ((imgWidth.indexOf("px") > 0) || ((imgWidth.charAt(imgWidth.length() - 1) >= '0' && imgWidth.charAt(imgWidth.length() - 1) <= '9'))) {
                    return NumberTools.parseIntDecSubstring(imgWidth);
                }
            }
            return null;
        }

        /**
         * @return image height in pixel or null
         */
        public Integer getHeight() {
            if (imgHeight != null) {
                // return number if given in pixel or a number only, return nothing for size like "100%"
                if ((imgHeight.indexOf("px") > 0) || ((imgHeight.charAt(imgHeight.length() - 1) >= '0' && imgHeight.charAt(imgHeight.length() - 1) <= '9'))) {
                    return NumberTools.parseIntDecSubstring(imgHeight);
                }
            }
            return null;
        }
    }
}
