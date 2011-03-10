/**
 *  RSSReader
 *  Copyright 2007 by Michael Peter Christen
 *  First released 16.7.2007 at http://yacy.net
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

package net.yacy.cora.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


public class RSSReader extends DefaultHandler {
    
    // class variables
    private RSSMessage item;
    private final StringBuilder buffer;
    private boolean parsingChannel, parsingImage, parsingItem;
    private final RSSFeed theChannel;
    private Type type;
    
    public enum Type { rss, atom, rdf, none };
    
    private RSSReader(int maxsize) {
        theChannel = new RSSFeed(maxsize);
        buffer = new StringBuilder(300);
        item = null;
        parsingChannel = false;
        parsingImage = false;
        parsingItem = false;
        type = Type.none;
    }
    
    public RSSReader(int maxsize, final InputStream stream, Type type) throws IOException {
        this(maxsize);
        this.type = type;
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            final SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(stream, this);
        } catch (SAXException e) {
            throw new IOException (e.getMessage());
        } catch (ParserConfigurationException e) {
            throw new IOException (e.getMessage());
        }
    }
    
    public Type getType() {
        return this.type;
    }
    
    public static RSSReader parse(int maxsize, final byte[] a) throws IOException {

        // check integrity of array
        if ((a == null) || (a.length == 0)) {
            throw new IOException("response=null");
        }
        if (a.length < 100) {
            throw new IOException("response=" + UTF8.String(a));
        }
        if (!equals(a, UTF8.getBytes("<?xml")) && !equals(a, UTF8.getBytes("<rss"))) {
            throw new IOException("response does not contain valid xml");
        }
        final String end = UTF8.String(a, a.length - 80, 80);
        Type type = Type.none;
        if (end.indexOf("rss") > 0) type = Type.rss;
        if (end.indexOf("feed") > 0) type = Type.atom;
        if (end.indexOf("rdf") > 0) type = Type.rdf;
        if (type == Type.none) {
            throw new IOException("response incomplete");
        }
        
        // make input stream
        final ByteArrayInputStream bais = new ByteArrayInputStream(a);
        
        // parse stream
        RSSReader reader = null;
        try {
            reader = new RSSReader(maxsize, bais, type);
        } catch (final Exception e) {
            throw new IOException("parse exception: " + e.getMessage(), e);
        }
        try { bais.close(); } catch (final IOException e) {}
        return reader;
    }
    
    private final static boolean equals(final byte[] buffer, final byte[] pattern) {
        // compares two byte arrays: true, if pattern appears completely at offset position
        if (buffer.length < pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) if (buffer[i] != pattern[i]) return false;
        return true;
    }

    @Override
    public void startElement(final String uri, final String name, final String tag, final Attributes atts) throws SAXException {
        if ("channel".equals(tag)) {
            this.type = Type.rss;
            item = new RSSMessage();
            parsingChannel = true;
        } else if ("feed".equals(tag)) {
            this.type = Type.atom;
            item = new RSSMessage();
            parsingChannel = true;
        } else if ("item".equals(tag) || "entry".equals(tag)) {
            if (parsingChannel) {
                // the channel ends with the first item not with the channel close tag
                theChannel.setChannel(item);
                parsingChannel = false;
            }
            item = new RSSMessage();
            parsingItem = true;
        } else if (parsingItem && this.type == Type.atom && "link".equals(tag)) {
            String url = atts.getValue("href");
            if (url != null && url.length() > 0) item.setValue("link", url);
        } else if ("image".equals(tag)) {
            parsingImage = true;
        }
    }

    @Override
    public void endElement(final String uri, final String name, final String tag) {
        if (tag == null) return;
        if ("channel".equals(tag) || "feed".equals(tag)) {
            if (parsingChannel) theChannel.setChannel(item);
            parsingChannel = false;
        } else if ("item".equals(tag) || "entry".equals(tag)) {
            theChannel.addMessage(item);
            parsingItem = false;
        } else if ("image".equals(tag)) {
            parsingImage = false;
        } else if ((parsingImage) && (parsingChannel)) {
            final String value = buffer.toString().trim();
            buffer.setLength(0);
            if ("url".equals(tag)) theChannel.setImage(value);
        } else if (parsingItem)  {
            final String value = buffer.toString().trim();
            buffer.setLength(0);
            if (RSSMessage.tags.contains(tag) && value.length() > 0) item.setValue(tag, value);
        } else if (parsingChannel) {
            final String value = buffer.toString().trim();
            buffer.setLength(0);
            if (RSSMessage.tags.contains(tag)) item.setValue(tag, value);
        }
    }

    @Override
    public void characters(final char ch[], final int start, final int length) {
        if (parsingItem || parsingChannel) {
            buffer.append(ch, start, length);
        }
    }
    
    public RSSFeed getFeed() {
        return theChannel;
    }

}