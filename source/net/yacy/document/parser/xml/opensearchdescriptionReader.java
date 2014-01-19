// opensearchdescriptionReader.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 11.03.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
 
package net.yacy.document.parser.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.ConcurrentLog;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/*
 * reads opensearchdescription xml document and provides the parsed search url
 * templates via get methodes as well as all other tags by getItem(tagname)
 */
public class opensearchdescriptionReader extends DefaultHandler {

    //private static final String recordTag = "OpenSearchDescription";
    private static final String[] tagsDef = new String[]{
        "ShortName",
        "LongName",
        // "Image",
        "Language",
        "OutputEncoding",
        "InputEncoding",
        "AdultContent",
        "Description",
        "Url",
        "Developer",
        "Query",
        "Tags",
        "Contact",
        "Attribution",
        "SyndicationRight"
        };
    /*
    <?xml version="1.0" encoding="UTF-8"?>
    <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
      <ShortName>YaCy/#[clientname]#</ShortName>
      <LongName>YaCy.net - #[SearchPageGreeting]#</LongName>
      <Image type="image/gif">http://#[thisaddress]#/env/grafics/yacy.gif</Image>
      <Language>en-us</Language>
      <OutputEncoding>UTF-8</OutputEncoding>
      <InputEncoding>UTF-8</InputEncoding>
      <AdultContent>true</AdultContent>
      <Description>YaCy is an open-source GPL-licensed software that can be used for stand-alone search engine installations or as a client for a multi-user P2P-based web indexing cluster. This is the access to peer '#[clientname]#'.</Description>
      <Url type="application/rss+xml" method="GET" template="http://#[thisaddress]#/yacysearch.rss?query={searchTerms}&amp;Enter=Search" />
      <Developer>See http://developer.berlios.de/projects/yacy/</Developer>
      <Query role="example" searchTerms="yacy" />
      <Tags>YaCy P2P Web Search</Tags>
      <Contact>See http://#[thisaddress]#/ViewProfile.html?hash=localhash</Contact>
      <Attribution>YaCy Software &amp;copy; 2004-2007 by Michael Christen et al., YaCy.net; Content: ask peer owner</Attribution>
      <SyndicationRight>open</SyndicationRight>
    </OpenSearchDescription>
    */

    private static final HashSet<String> tags = new HashSet<String>();
    static {
        for (final String element : tagsDef) {
            tags.add(element);
        }
    }

    // class variables
    private final StringBuilder buffer;
    private boolean parsingDescription, parsingTextValue;
    private final HashMap<String, String> items; // Opensearchdescription Item map
    private String rssurl, atomurl; // search url templates
    private ClientIdentification.Agent agent;

    public opensearchdescriptionReader() {
        this.items = new HashMap<String, String>();
        this.buffer = new StringBuilder();
        this.parsingDescription = false;
        this.parsingTextValue = false;
        this.rssurl = null;
        this.atomurl = null;
        this.agent = ClientIdentification.yacyInternetCrawlerAgent;
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
    
    public opensearchdescriptionReader(final String path) {
        this();
        try {
            final SAXParser saxParser = getParser();
            saxParser.parse(path, this);
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        }
    }

    public opensearchdescriptionReader(final InputStream stream) {
        this();
        try {
            final SAXParser saxParser = getParser();
            saxParser.parse(stream, this);
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        }
    }

    public opensearchdescriptionReader(final String path, final ClientIdentification.Agent agent) {
        this();
        this.agent = agent;
        try {
            HTTPClient www = new HTTPClient(agent);
            www.GET(path, false);
            final SAXParser saxParser = getParser();
            saxParser.parse(www.getContentstream(), this);
            www.finish();
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        }
    }

    public boolean read(String path) {
        this.items.clear();
        this.buffer.setLength(0);
        this.parsingDescription = false;
        this.parsingTextValue = false;
        this.rssurl = null;
        this.atomurl = null;
        try {
            HTTPClient www = new HTTPClient(this.agent);
            www.GET(path, false);
            final SAXParser saxParser = getParser();
            try {
                saxParser.parse(www.getContentstream(), this);
            } catch (final SAXException se) {
                www.finish();
                return false;
            } catch (final IOException ioe) {
                www.finish();
                return false;
            }
            www.finish();
            return true;
        } catch (final Exception e) {
            ConcurrentLog.warn("opensearchdescriptionReader", "parse exception: " + e);
            return false;
        }
    }

    @Override
    public void startElement(final String uri, final String name, final String tag, final Attributes atts) throws SAXException {
        if ("OpenSearchDescription".equals(tag)) {
            this.parsingDescription = true;
        } else if (this.parsingDescription) {
            if ("Url".equals(tag)) {
                this.parsingTextValue = false;
                String type = atts.getValue("type");
                if ("application/rss+xml".equals(type)) {
                    rssurl = atts.getValue("template");
                } else if ("application/atom+xml".equals(type)) {
                    atomurl = atts.getValue("template");
                }
            } else {
                this.parsingTextValue = tags.contains(tag);
            }
        }
    }

    @Override
    public void endElement(final String uri, final String name, final String tag) {
        if (tag == null) return;        
        if (parsingDescription && "OpenSearchDescription".equals(tag)) {
            this.parsingDescription = false;
        } else if (this.parsingTextValue) {
            final String value = this.buffer.toString().trim();
            this.buffer.setLength(0);
            if (tags.contains(tag)) {
                this.items.put(tag, value);
            }
        }
    }

    @Override
    public void characters(final char ch[], final int start, final int length) {
        if (this.parsingTextValue) {
            this.buffer.append(ch, start, length);
        }
    }

    public String getRSSTemplate() {
        return this.rssurl;
    }

    public String getRSSorAtomUrl() {
        return this.rssurl == null ? this.atomurl : this.rssurl;
    }

    public String getShortName() {
        return items.get("ShortName");
    }

    public String getItem(final String name) {
        // retrieve item by name
        return this.items.get(name);
    }

    public int items() {
        return this.items.size();
    }
}