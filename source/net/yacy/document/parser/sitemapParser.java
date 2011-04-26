/**
 *  sitemapParser.java
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 08.09.2010 at http://yacy.net
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
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.io.ByteCountInputStream;

public class sitemapParser extends AbstractParser implements Parser {

    public sitemapParser() {
        super("RSS Parser");
        // unfortunately sitemap files have neither a mime type nor a typical file extension.
        //SUPPORTED_EXTENSIONS.add("php");
        //SUPPORTED_EXTENSIONS.add("xml");
    }
    
    public Document[] parse(final MultiProtocolURI url, final String mimeType,
            final String charset, final InputStream source)
            throws Failure, InterruptedException {
        SitemapReader sitemap;
        try {
            sitemap = new SitemapReader(source);
        } catch (IOException e) {
            throw new Parser.Failure("Load error:" + e.getMessage(), url);
        }
        
        final List<Document> docs = new ArrayList<Document>();
        MultiProtocolURI uri;
        Document doc;
        for (final URLEntry item: sitemap) try {
            uri = new MultiProtocolURI(item.loc);
            doc = new Document(
                    uri,
                    TextParser.mimeOf(url),
                    charset,
                    this,
                    null,
                    null,
                    "",
                    "",
                    "",
                    new String[0],
                    "",
                    0.0f, 0.0f, 
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
    
    public static SitemapReader parse(final DigestURI sitemapURL) throws IOException {
        // download document
        final RequestHeader requestHeader = new RequestHeader();
        requestHeader.put(HeaderFramework.USER_AGENT, ClientIdentification.getUserAgent());
        final HTTPClient client = new HTTPClient();
        client.setTimout(5000);
        client.setHeader(requestHeader.entrySet());
        try {
            client.GET(sitemapURL.toString());
            if (client.getStatusCode() != 200) {
                throw new IOException("Unable to download the sitemap file " + sitemapURL +
                        "\nServer returned status: " + client.getHttpResponse().getStatusLine());
            }
    
            // get some metadata
            final ResponseHeader header = new ResponseHeader(client.getHttpResponse().getAllHeaders());
            final String contentMimeType = header.mime();
    
            InputStream contentStream = client.getContentstream();
            if (contentMimeType != null && (contentMimeType.equals("application/x-gzip") || contentMimeType.equals("application/gzip"))) {
                contentStream = new GZIPInputStream(contentStream);
            }
            final ByteCountInputStream counterStream = new ByteCountInputStream(contentStream, null);
            return sitemapParser.parse(counterStream);
        } catch (IOException e) {
            throw e;
        } finally {
            client.finish();
        }
    }
    
    public static SitemapReader parse(final InputStream stream) throws IOException {
        return new SitemapReader(stream);
    }

    /**
     * for schemas see:
     * http://www.sitemaps.org/schemas/sitemap/0.9
     * http://www.google.com/schemas/sitemap/0.84
     */
    public static class SitemapReader extends ArrayList<URLEntry> {
        private static final long serialVersionUID = 1337L;
        public SitemapReader(final InputStream source) throws IOException {
            org.w3c.dom.Document doc;
            try { doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source); }
            catch (ParserConfigurationException e) { throw new IOException (e); }
            catch (SAXParseException e) { throw new IOException (e); }
            catch (SAXException e) { throw new IOException (e); }
            catch (OutOfMemoryError e) { throw new IOException (e); }
            NodeList sitemapNodes = doc.getElementsByTagName("sitemap");
            for (int i = 0; i < sitemapNodes.getLength(); i++) {
                String url = new SitemapEntry((Element) sitemapNodes.item(i)).url();
                if (url != null && url.length() > 0) {
                    try {
                        final SitemapReader r = parse(new DigestURI(url));
                        for (final URLEntry ue: r) this.add(ue);
                    } catch (IOException e) {}
                }
            }
            final NodeList urlEntryNodes = doc.getElementsByTagName("url");
            for (int i = 0; i < urlEntryNodes.getLength(); i++) {
                this.add(new URLEntry((Element) urlEntryNodes.item(i)));
            }
        }
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder(300);
            for (final URLEntry entry: this) {
                sb.append(entry.toString());
            }
            return sb.toString();
        }
    }

    public static class URLEntry {
        public String loc, lastmod, changefreq, priority;

        public URLEntry(final Element element) {
            loc = val(element, "loc", "");
            lastmod  = val(element, "lastmod", "");
            changefreq  = val(element, "changefreq", "");
            priority  = val(element, "priority", "");
        }

        public String url() {
            return this.loc;
        }

        public Date lastmod(final Date dflt) {
            try {
                return ISO8601Formatter.FORMATTER.parse(lastmod);
            } catch (final ParseException e) {
                return dflt;
            }
        }
    }
    
    public static class SitemapEntry {
        public String loc, lastmod;

        public SitemapEntry(final Element element) {
            loc = val(element, "loc", "");
            lastmod  = val(element, "lastmod", "");
        }

        public String url() {
            return this.loc;
        }
        
        public Date lastmod(final Date dflt) {
            try {
                return ISO8601Formatter.FORMATTER.parse(lastmod);
            } catch (final ParseException e) {
                return dflt;
            }
        }
    }

    private static String val(final Element parent, final String label, final String dflt) {
        final Element e = (Element) parent.getElementsByTagName(label).item(0);
        if (e == null) return dflt;
        final Node child = e.getFirstChild();
        return (child instanceof CharacterData) ? ((CharacterData) child).getData() : dflt;
    }
}
