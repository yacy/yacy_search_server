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

package net.yacy.cora.document.feed;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.feed.RSSMessage.Token;

import org.xml.sax.Attributes;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


public class RSSReader extends DefaultHandler {

    // class variables
    private RSSMessage item;
    private final StringBuilder buffer;
    private boolean parsingChannel, parsingImage, parsingItem;
    private final RSSFeed theChannel;
    private Type type;

    public enum Type { rss, atom, rdf, none }

    private RSSReader(final int maxsize) {
        this.theChannel = new RSSFeed(maxsize);
        this.buffer = new StringBuilder(300);
        this.item = null;
        this.parsingChannel = false;
        this.parsingImage = false;
        this.parsingItem = false;
        this.type = Type.none;
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

    public RSSReader(final int maxsize, InputStream stream, final Type type) throws IOException {
        this(maxsize);
        this.type = type;
        if (!(stream instanceof ByteArrayInputStream) && !(stream instanceof BufferedInputStream)) stream = new BufferedInputStream(stream);
        try {
            final SAXParser saxParser = getParser();
            // do not look at external dtd - see: http://www.ibm.com/developerworks/xml/library/x-tipcfsx/index.html
            saxParser.getXMLReader().setEntityResolver(new EntityResolver() {
				@Override
				public InputSource resolveEntity(final String arg0, final String arg1)
						throws SAXException, IOException {
					return new InputSource(new StringReader(""));
				}
            });
            saxParser.parse(stream, this);
        } catch (final SAXException e) {
            throw new IOException (e.getMessage());
        }
    }

    public Type getType() {
        return this.type;
    }

    public static RSSReader parse(final int maxsize, final byte[] a) throws IOException {

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

        final Type type = findOutType(a);
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

    /**
     * Tries to find out the type of feed by stepping through its data
     * starting in the end and looking at the last XML tag. Just grabbing
     * the last few characters of the data does not work since some
     * people add quite long comments at the end of their feeds.
     * @param a contains the feed
     * @return type of feed
     */
    private static Type findOutType(final byte[] a) {
        String end;
        int i = 1;

        do {
            /* In first iteration grab the last 80 characters, after that
             * move towards the start of the data and take some more (90)
             * to have an overlap in order to not miss anything if the tag
             * is on the border of two 80 character blocks.
             */
            end = UTF8.String(a, a.length - (i * 80), (80 + ((i > 1)? 10 : 0)));
            i++;
        } while(!end.contains("</"));

        Type type = Type.none;
        if (end.indexOf("rss",0) > 0) type = Type.rss;
        if (end.indexOf("feed",0) > 0) type = Type.atom;
        if (end.indexOf("rdf",0) > 0) type = Type.rdf;
        return type;
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
            this.item = new RSSMessage();
            this.parsingChannel = true;
        } else if ("feed".equals(tag)) {
            this.type = Type.atom;
            this.item = new RSSMessage();
            this.parsingChannel = true;
        } else if ("item".equals(tag) || "entry".equals(tag)) {
            if (this.parsingChannel) {
                // the channel ends with the first item not with the channel close tag
                this.theChannel.setChannel(this.item);
                this.parsingChannel = false;
            }
            this.item = new RSSMessage();
            this.parsingItem = true;
        } else if (this.parsingItem && this.type == Type.atom && "link".equals(tag) && (atts.getValue("type") == null || this.item.getLink().length() == 0 || atts.getValue("type").startsWith("text") || atts.getValue("type").equals("application/xhtml+xml"))) {
            final String url = atts.getValue("href");
            if (url != null && url.length() > 0) this.item.setValue(Token.link, url);
        } else if ("image".equals(tag) || (this.parsingItem && this.type == Type.atom && "link".equals(tag) && (atts.getValue("type") == null || atts.getValue("type").startsWith("image")))) {
            this.parsingImage = true;
        }
    }

    @Override
    public void endElement(final String uri, final String name, final String tag) {
        if (tag == null) return;
        if ("channel".equals(tag) || "feed".equals(tag)) {
            if (this.parsingChannel) this.theChannel.setChannel(this.item);
            this.parsingChannel = false;
        } else if ("item".equals(tag) || "entry".equals(tag)) {
            this.theChannel.addMessage(this.item);
            this.parsingItem = false;
        } else if ("image".equals(tag)) {
            this.parsingImage = false;
        } else if ((this.parsingImage) && (this.parsingChannel)) {
            final String value = this.buffer.toString().trim();
            this.buffer.setLength(0);
            if ("url".equals(tag)) this.theChannel.setImage(value);
        } else if (this.parsingItem)  {
            final String value = this.buffer.toString().trim();
            this.buffer.setLength(0);
            if (RSSMessage.tags.contains(tag) && value.length() > 0) this.item.setValue(RSSMessage.valueOfNick(tag), value);
        } else if (this.parsingChannel) {
            final String value = this.buffer.toString().trim();
            this.buffer.setLength(0);
            if (RSSMessage.tags.contains(tag)) this.item.setValue(RSSMessage.valueOfNick(tag), value);
        }
    }

    @Override
    public void characters(final char ch[], final int start, final int length) {
        if (this.parsingItem || this.parsingChannel) {
            this.buffer.append(ch, start, length);
        }
    }

    public RSSFeed getFeed() {
        return this.theChannel;
    }

}