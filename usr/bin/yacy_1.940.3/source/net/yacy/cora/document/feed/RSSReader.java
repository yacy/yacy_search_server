/**
 *  RSSReader
 *  Copyright 2007 by Michael Peter Christen
 *  First released 16.7.2007 at https://yacy.net
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

import org.apache.commons.lang.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import net.yacy.cora.document.feed.RSSMessage.Token;
import net.yacy.cora.util.StreamLimitException;
import net.yacy.cora.util.StrictLimitInputStream;


public class RSSReader extends DefaultHandler {

    // class variables
    private RSSMessage item;
    private final StringBuilder buffer;
    private boolean parsingChannel, parsingItem;
    private final RSSFeed theChannel;
    private Type type;

    /** When a parsing limit on instance construction has been exceeded */
    private boolean maxBytesExceeded;

    public enum Type { rss, atom, rdf, none }

    private RSSReader(final int maxsize) {
        this.theChannel = new RSSFeed(maxsize);
        this.buffer = new StringBuilder(300);
        this.item = null;
        this.parsingChannel = false;
        this.parsingItem = false;
        this.type = Type.none;
        this.maxBytesExceeded = false;
    }

    private static final ThreadLocal<SAXParser> tlSax = new ThreadLocal<>();
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

    public RSSReader(final int maxsize, InputStream stream) throws IOException {
        this(maxsize);
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

    public RSSReader(final int maxsize, final long maxBytes, InputStream stream) throws IOException {
        this(maxsize);

        if (!(stream instanceof ByteArrayInputStream) && !(stream instanceof BufferedInputStream)) {
        	stream = new BufferedInputStream(stream);
        }

		final StrictLimitInputStream limitedSource = new StrictLimitInputStream(stream, maxBytes);

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
            saxParser.parse(limitedSource, this);
        } catch (final SAXException e) {
	        throw new IOException (e.getMessage());
        } catch(final StreamLimitException e) {
        	this.maxBytesExceeded = true;
        }
    }

    public Type getType() {
        return this.type;
    }

    public static RSSReader parse(final int maxsize, final byte[] a) throws IOException {

        // check integrity of array
        if (a == null || a.length < 100) {
            return null; // returning null instead of throwing an IOException is expected in most calling methods where a fail is checked against null
        }

        // make input stream
        final ByteArrayInputStream bais = new ByteArrayInputStream(a);

        // parse stream
        RSSReader reader = null;
        try {
            reader = new RSSReader(maxsize, bais);
        } catch (final Exception e) {
            throw new IOException("parse exception: " + e.getMessage(), e);
        }
        try { bais.close(); } catch (final IOException e) {}
        return reader;
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
        } else if (this.parsingItem) {
        	if(this.type == Type.atom) {
				if ("link".equals(tag)) {
					final String linkRelation = atts.getValue("rel");
					if (linkRelation == null || linkRelation.equals("alternate")) {
						// atom link handling (rss link is handled in endElement)
						final String url = atts.getValue("href");
						if (StringUtils.isNotBlank(url)) {
							this.item.setValue(Token.link, url);
						}
					} else if("enclosure".equals(linkRelation)) {
						/* Atom rel="enclosure" link type */
						final String url = atts.getValue("href");
						if(StringUtils.isNotBlank(url)) {
							this.item.setEnclosure(url);
						}
					}
				}
        	} else if(this.type == Type.rss) {
        		/* RSS 0.92 and 2.0 <enclosure> element */
    			if ("enclosure".equals(tag)) {
    				final String url = atts.getValue("url");
					if(StringUtils.isNotBlank(url)) {
						this.item.setEnclosure(url);
					}
    			}
        	}
        } else if ("rss".equals(tag)) {
            this.type = Type.rss;
        }
    }

    @Override
    public void endElement(final String uri, final String name, final String tag) throws SAXException {
        if (tag == null) return;
        if ("channel".equals(tag) || "feed".equals(tag)) {
            if (this.parsingChannel) this.theChannel.setChannel(this.item);
            this.parsingChannel = false;
        } else if ("item".equals(tag) || "entry".equals(tag)) {
            this.theChannel.addMessage(this.item);
            this.parsingItem = false;
        } else if (this.parsingItem)  {
            final String value = this.buffer.toString().trim();
            this.buffer.setLength(0);
            if (RSSMessage.tags.contains(tag) && value.length() > 0) {
            	this.item.setValue(RSSMessage.valueOfNick(tag), value);
            }
        } else if (this.parsingChannel) {
            final String value = this.buffer.toString().trim();
            this.buffer.setLength(0);
            if (RSSMessage.tags.contains(tag)) this.item.setValue(RSSMessage.valueOfNick(tag), value);
        } else if (this.type == Type.none) {
            // give up if we don't known the feed format
            throw new SAXException("response incomplete or unknown feed format");
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

    /**
     * @return true when a parsing limit on instance construction has been exceeded
     */
    public boolean isMaxBytesExceeded() {
		return this.maxBytesExceeded;
	}

}