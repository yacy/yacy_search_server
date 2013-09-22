/**
 *  mmParser
 *  Copyright 2010 by Marc Nause, marc.nause@gmx.de, Braunschweig, Germany
 *  First released 27.12.2010 at http://yacy.net
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

// this is a new implementation of this parser idiom using multiple documents as result set

public class mmParser extends AbstractParser implements Parser {

    public mmParser() {
        super("FreeMind Parser");
        this.SUPPORTED_EXTENSIONS.add("mm");
        this.SUPPORTED_MIME_TYPES.add("application/freemind");
        this.SUPPORTED_MIME_TYPES.add("application/x-freemind");
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
    public Document[] parse(final AnchorURL location, final String mimeType,
            final String charset, final InputStream source)
            throws Parser.Failure, InterruptedException
    {
        final StringBuilder sb = new StringBuilder();
        String rootElementText = "";
        byte[] content = new byte[0];

        try {
            final SAXParser saxParser = getParser();
            final FreeMindHandler freeMindHandler = new FreeMindHandler();
            saxParser.parse(source, freeMindHandler);

            final List<String> nodeTextList = freeMindHandler.getNodeText();

            rootElementText = nodeTextList.isEmpty() ? "" : nodeTextList.get(0);

            for (final String nodeText : nodeTextList) {
                sb.append(nodeText);
                sb.append(". ");
            }

            content = UTF8.getBytes(sb.toString());

        } catch (final SAXException ex) {
            AbstractParser.log.warn(ex.getMessage());
        } catch (final IOException ex) {
            AbstractParser.log.warn(ex.getMessage());
        }

        return new Document[]{new Document(
            location,
            mimeType,
            "UTF-8",
            this,
            null,
            null,
            singleList(rootElementText),
            null,
            null,
            null,
            null,
            0.0f, 0.0f,
            content,
            null,
            null,
            null,
            false,
            new Date())};
    }

    private class FreeMindHandler extends DefaultHandler {

        private final List<String> nodeText = new ArrayList<String>();

        @Override
        public void startElement(final String uri, final String localName,
                final String qName, final Attributes attributes) {
            if (qName.equals("node")) {
                final String textValue = attributes.getValue("TEXT");
                if (textValue != null) {
                    this.nodeText.add(textValue);
                }
            }
        }

        protected List<String> getNodeText() {
            return this.nodeText;
        }

    }
}
