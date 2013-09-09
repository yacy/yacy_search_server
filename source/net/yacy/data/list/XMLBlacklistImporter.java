// XmlBlacklistImporter.java
// -------------------------------------
// part of YACY
//
// (C) 2009 by Marc Nause
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

package net.yacy.data.list;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.apache.xerces.parsers.SAXParser;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class provides methods to import blacklists from an XML file (see
 * http://www.yacy-websuche.de/wiki/index.php/Dev:APIblacklists
 * for examples) and to return this data as a {@link ListAccumulator} object.
 */
public class XMLBlacklistImporter extends DefaultHandler {

    private ListAccumulator ba = null;
    private String currentListName = null;
    private StringBuilder lastText = null;

    /**
     * Takes the input data and turns it into a {@link ListAccumulator} which can
     * be used for further processing.
     * @param input the XML data
     * @return the data from the XML
     * @throws java.io.IOException if input can't be read
     * @throws org.xml.sax.SAXException if XML can't be parsed
     */
    public synchronized ListAccumulator parse(InputSource input) throws IOException, SAXException {

        XMLReader reader = new SAXParser();
        reader.setContentHandler(this);
        reader.parse(input);

        return ba;
    }

    /**
     * Takes the input data and turns it into a {@link ListAccumulator} which can
     * be used for further processing.
     * @param input the XML data
     * @return the data from the XML
     * @throws java.io.IOException if input can't be read
     * @throws org.xml.sax.SAXException if XML can't be parsed
     */
    public synchronized ListAccumulator parse(Reader input) throws IOException, SAXException {
        return this.parse(new InputSource(input));
    }

    /**
     * Takes the input data and turns it into a {@link ListAccumulator} which can
     * be used for further processing.
     * @param input the XML data
     * @return the data from the XML
     * @throws java.io.IOException if input can't be read
     * @throws org.xml.sax.SAXException if XML can't be parsed
     */
    public synchronized ListAccumulator parse(String input) throws IOException, SAXException {
        return this.parse(new InputSource(input));
    }

    /**
     * Takes the input data and turns it into a {@link ListAccumulator} which can
     * be used for further processing.
     * @param input The XML data.
     * @return The data from the XML.
     * @throws java.io.IOException if input can't be read
     * @throws org.xml.sax.SAXException if XML can't be parsed
     */
    public synchronized ListAccumulator parse(InputStream input) throws IOException, SAXException {
        return this.parse(new InputSource(input));
    }

    /**
     * At the start of the document a new {@link ListAccumulator} is created.
     */
    @Override
    public void startDocument() {
        ba = new ListAccumulator();
    }

    /**
     * If the <list> tag is encountered a new list will be addedto the
     * {@link ListAccumulator} and the properties of the list will be set
     * if provided in the XML.
     * @param uri The Namespace URI, or the empty string if the
     *        element has no Namespace URI or if Namespace
     *        processing is not being performed.
     * @param localName The local name (without prefix), or the
     *        empty string if Namespace processing is not being
     *        performed.
     * @param qName The qualified name (with prefix), or the
     *        empty string if qualified names are not available.
     * @param attributes The attributes attached to the element.  If
     *        there are no attributes, it shall be an empty
     *        Attributes object.
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {

        if (qName.equalsIgnoreCase("list")) {
            currentListName = attributes.getValue("name");
            ba.addList(currentListName);
            
            int attributesLength = 0;

            if ((attributesLength = attributes.getLength()) > 1) {
                for (int i = 0; i < attributesLength; i++) {
                    if (!attributes.getQName(i).equals("name")) {
                        ba.addPropertyToCurrent(attributes.getQName(i), attributes.getValue(i));
                    }
                }
            }
        }
        
        if (qName.equalsIgnoreCase("item")) {
            lastText = new StringBuilder();
        }
        
    }

    /**
     * Adds a new item to the current list in the {@link ListAccumulator}.
     * @param uri The Namespace URI, or the empty string if the
     *        element has no Namespace URI or if Namespace
     *        processing is not being performed.
     * @param localName The local name (without prefix), or the
     *        empty string if Namespace processing is not being
     *        performed.
     * @param qName The qualified name (with prefix), or the
     *        empty string if qualified names are not available.
     * @throws org.xml.sax.SAXException
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (qName.equalsIgnoreCase("item")) {
            ba.addEntryToCurrent(lastText.toString());
        }
    }

    /**
     * Writes characters to a String which might be used by endElement() later.
     * @param ch The characters.
     * @param start The start position in the character array.
     * @param lengthThe number of characters to use from the character array.
     * @throws org.xml.sax.SAXException
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (lastText == null) lastText = new StringBuilder();
        lastText.append(ch, start, length);
    }

}
