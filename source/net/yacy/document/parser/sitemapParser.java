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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.io.ByteCountInputStream;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class sitemapParser extends AbstractParser implements Parser {

    public sitemapParser() {
        super("sitemap Parser");
        // unfortunately sitemap files have neither a mime type nor a typical file extension.
        //SUPPORTED_EXTENSIONS.add("php");
        //SUPPORTED_EXTENSIONS.add("xml");
    }

    @Override
    public Document[] parse(final AnchorURL url, final String mimeType,
            final String charset, final InputStream source)
            throws Failure, InterruptedException {
        final List<Document> docs = new ArrayList<Document>();
        SitemapReader sitemap = new SitemapReader(source, ClientIdentification.yacyInternetCrawlerAgent);
        sitemap.start();
        DigestURL uri;
        Document doc;
        URLEntry item;
        while ((item = sitemap.take()) != POISON_URLEntry) try {
            uri = new DigestURL(item.loc);
            doc = new Document(
                    uri,
                    TextParser.mimeOf(url),
                    charset,
                    this,
                    null,
                    null,
                    singleList(""),
                    "",
                    "",
                    new String[0],
                    new ArrayList<String>(),
                    0.0f, 0.0f,
                    null,
                    null,
                    null,
                    new LinkedHashMap<AnchorURL, ImageEntry>(),
                    false,
                    new Date());
            docs.add(doc);
        } catch (final MalformedURLException e) {
            continue;
        }

        Document[] da = new Document[docs.size()];
        docs.toArray(da);
        return da;
    }

    public static SitemapReader parse(final DigestURL sitemapURL, final ClientIdentification.Agent agent) throws IOException {
        // download document
        ConcurrentLog.info("SitemapReader", "loading sitemap from " + sitemapURL.toNormalform(true));
        final RequestHeader requestHeader = new RequestHeader();
        final HTTPClient client = new HTTPClient(agent);
        client.setHeader(requestHeader.entrySet());
        try {
            client.GET(sitemapURL.toString(), false);
            if (client.getStatusCode() != 200) {
                throw new IOException("Unable to download the sitemap file " + sitemapURL +
                        "\nServer returned status: " + client.getHttpResponse().getStatusLine());
            }

            // get some metadata
            int statusCode = client.getHttpResponse().getStatusLine().getStatusCode();
            final ResponseHeader header = new ResponseHeader(statusCode, client.getHttpResponse().getAllHeaders());
            final String contentMimeType = header.mime();

            InputStream contentStream = client.getContentstream();
            if (contentMimeType != null && (contentMimeType.equals("application/x-gzip") || contentMimeType.equals("application/gzip"))) {
                contentStream = new GZIPInputStream(contentStream);
            }
            final ByteCountInputStream counterStream = new ByteCountInputStream(contentStream, null);
            return new SitemapReader(counterStream, agent);
        } catch (final IOException e) {
            throw e;
        }
    }

    /**
     * for schemas see:
     * http://www.sitemaps.org/schemas/sitemap/0.9
     * http://www.google.com/schemas/sitemap/0.84
     */
    public static class SitemapReader extends Thread {
        private final InputStream source;
        private final BlockingQueue<URLEntry> queue;
        private final ClientIdentification.Agent agent;
        public SitemapReader(final InputStream source, final ClientIdentification.Agent agent) {
            this.source = source;
            this.queue = new ArrayBlockingQueue<URLEntry>(10000);
            this.agent = agent;
        }
        @Override
        public void run() {
            try {
                org.w3c.dom.Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(this.source);
                NodeList sitemapNodes = doc.getElementsByTagName("sitemap");
                for (int i = 0; i < sitemapNodes.getLength(); i++) {
                    String url = new SitemapEntry((Element) sitemapNodes.item(i)).url();
                    if (url != null && url.length() > 0) {
                        try {
                            final SitemapReader r = parse(new DigestURL(url), agent);
                            r.start();
                            URLEntry item;
                            while ((item = r.take()) != POISON_URLEntry) {
                                try {
                                    this.queue.put(item);
                                } catch (final InterruptedException e) {
                                    break;
                                }
                            }
                        } catch (final IOException e) {}
                    }
                }
                final NodeList urlEntryNodes = doc.getElementsByTagName("url");
                for (int i = 0; i < urlEntryNodes.getLength(); i++) {
                    try {
                        this.queue.put(new URLEntry((Element) urlEntryNodes.item(i)));
                    } catch (final InterruptedException e) {
                        break;
                    }
                }
            } catch (final Throwable e) {
                ConcurrentLog.logException(e);
            }

            try {
                this.queue.put(POISON_URLEntry);
            } catch (final InterruptedException e) {
            }
        }
        /**
         * retrieve the next entry, waiting until one becomes available.
         * if no more are available, POISON_URLEntry is returned
         * @return the next entry from the sitemap or POISON_URLEntry if no more are there
         */
        public URLEntry take() {
            try {
                return this.queue.take();
            } catch (final InterruptedException e) {
                return POISON_URLEntry;
            }
        }
    }

    public final static URLEntry POISON_URLEntry = new URLEntry(null);

    public static class URLEntry {
        public String loc, lastmod, changefreq, priority;

        public URLEntry(final Element element) {
            this.loc = val(element, "loc", "");
            this.lastmod  = val(element, "lastmod", "");
            this.changefreq  = val(element, "changefreq", "");
            this.priority  = val(element, "priority", "");
        }

        public String url() {
            return this.loc;
        }

        public Date lastmod(final Date dflt) {
            try {
                return ISO8601Formatter.FORMATTER.parse(this.lastmod);
            } catch (final ParseException e) {
                return dflt;
            }
        }
    }

    public static class SitemapEntry {
        public String loc, lastmod;

        public SitemapEntry(final Element element) {
            this.loc = val(element, "loc", "");
            this.lastmod  = val(element, "lastmod", "");
        }

        public String url() {
            return this.loc;
        }

        public Date lastmod(final Date dflt) {
            try {
                return ISO8601Formatter.FORMATTER.parse(this.lastmod);
            } catch (final ParseException e) {
                return dflt;
            }
        }
    }

    private static String val(final Element parent, final String label, final String dflt) {
        if (parent == null) return null;
        final Element e = (Element) parent.getElementsByTagName(label).item(0);
        if (e == null) return dflt;
        final Node child = e.getFirstChild();
        return (child instanceof CharacterData) ? ((CharacterData) child).getData() : dflt;
    }
}
