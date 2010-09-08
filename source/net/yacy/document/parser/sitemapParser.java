/**
 *  sitemapParser.java
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 08.09.2010 at http://yacy.net
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
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.util.DateFormatter;

public class sitemapParser extends AbstractParser implements Parser {

    public sitemapParser() {
        super("RSS Parser");
        // unfortunately sitemap files have neither a mime type nor a typical file extension.
        //SUPPORTED_EXTENSIONS.add("php");
        //SUPPORTED_EXTENSIONS.add("xml");
    }
    
    public Document[] parse(MultiProtocolURI url, String mimeType, String charset, InputStream source) throws Failure, InterruptedException {
        SitemapReader sitemap;
        try {
            sitemap = new SitemapReader(source);
        } catch (IOException e) {
            throw new Parser.Failure("Load error:" + e.getMessage(), url);
        }
        
        List<Document> docs = new ArrayList<Document>();
        MultiProtocolURI uri;
        Document doc;
        for (SitemapEntry item: sitemap) try {
            uri = new MultiProtocolURI(item.loc);
            doc = new Document(
                    uri,
                    TextParser.mimeOf(url),
                    charset,
                    null,
                    null,
                    "",
                    "",
                    "",
                    new String[0],
                    "",
                    null,
                    null,
                    null,
                    new HashMap<MultiProtocolURI, ImageEntry>(),
                    false);
            docs.add(doc);
        } catch (MalformedURLException e) {
            continue;
        }
        
        Document[] da = new Document[docs.size()];
        docs.toArray(da);
        return da;
    }
    
    public static SitemapReader parse(InputStream stream) throws IOException {
        return new SitemapReader(stream);
    }

    /**
     * for schemas see:
     * http://www.sitemaps.org/schemas/sitemap/0.9
     * http://www.google.com/schemas/sitemap/0.84
     */
    public static class SitemapReader extends ArrayList<SitemapEntry> {
        private static final long serialVersionUID = 1337L;
        public SitemapReader(InputStream source) throws IOException {
            org.w3c.dom.Document doc;
            try { doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source); }
            catch (ParserConfigurationException e) { throw new IOException (e); }
            catch (SAXException e) { throw new IOException (e); }
            NodeList nodes = doc.getElementsByTagName("url");
            for (int i = 0; i < nodes.getLength(); i++)
                this.add(new SitemapEntry((Element) nodes.item(i)));
        }
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (SitemapEntry entry: this) sb.append(entry.toString());
            return sb.toString();
        }
    }

    public static class SitemapEntry {
        public String loc, lastmod, changefreq, priority;
        public SitemapEntry(Element element) {
            loc = val(element, "loc", "");
            lastmod  = val(element, "lastmod", "");
            changefreq  = val(element, "changefreq", "");
            priority  = val(element, "priority", "");
        }
        private String val(Element parent, String label, String dflt) {
            Element e = (Element) parent.getElementsByTagName(label).item(0);
            if (e == null) return dflt;
            Node child = e.getFirstChild();
            return (child instanceof CharacterData) ? ((CharacterData) child).getData() : dflt;
        }
        public String url() {
            return this.loc;
        }
        public Date lastmod(Date dflt) {
            try {
                return DateFormatter.parseISO8601(lastmod);
            } catch (final ParseException e) {
                return dflt;
            }
        }
    }
    
}
