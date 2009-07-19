// SitemapParser.java
// ------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2007
//
// this file is contributed by Martin Thelian
// last major change: $LastChangedDate$ by $LastChangedBy$
// Revision: $LastChangedRevision$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package de.anomic.data;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Date;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.retrieval.HTTPLoader;
import de.anomic.crawler.retrieval.Request;
import de.anomic.http.client.Client;
import de.anomic.http.io.ByteCountInputStream;
import de.anomic.http.metadata.HeaderFramework;
import de.anomic.http.metadata.RequestHeader;
import de.anomic.http.metadata.ResponseContainer;
import de.anomic.kelondro.text.metadataPrototype.URLMetadataRow;
import de.anomic.kelondro.util.DateFormatter;
import de.anomic.search.Switchboard;
import de.anomic.yacy.yacyURL;
import de.anomic.yacy.logging.Log;

/**
 * Class to parse a sitemap file.<br>
 * An example sitemap file is depicted below:<br>
 * 
 * <pre>
 * &lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot;?&gt;
 * &lt;urlset xmlns=&quot;http://www.sitemaps.org/schemas/sitemap/0.9&quot;&gt;
 *    &lt;url&gt;
 *       &lt;loc&gt;http://www.example.com/&lt;/loc&gt;
 *       &lt;lastmod&gt;2005-01-01&lt;/lastmod&gt;
 *       &lt;changefreq&gt;monthly&lt;/changefreq&gt;
 *       &lt;priority&gt;0.8&lt;/priority&gt;
 *    &lt;/url&gt;
 * &lt;/urlset&gt; 
 * </pre>
 * 
 * A real example can be found here: http://www.xt-service.de/sitemap.xml An example robots.txt containing a sitemap
 * URL: http://notepad.emaillink.de/robots.txt
 * 
 * @see Protocol at sitemaps.org <a href="http://www.sitemaps.org/protocol.php">http://www.sitemaps.org/protocol.php</a>
 * @see Protocol at google.com <a
 *      href="https://www.google.com/webmasters/tools/docs/en/protocol.html">https://www.google.com/webmasters/tools/docs/en/protocol.html</a>
 */
public class SitemapParser extends DefaultHandler {
    public static final String XMLNS_SITEMAPS_ORG = "http://www.sitemaps.org/schemas/sitemap/0.9";
    public static final String XMLNS_SITEMAPS_GOOGLE = "http://www.google.com/schemas/sitemap/0.84";

    public static final String SITEMAP_XMLNS = "xmlns";
    public static final String SITEMAP_URLSET = "urlset";
    public static final String SITEMAP_URL = "url";
    public static final String SITEMAP_URL_LOC = "loc";
    public static final String SITEMAP_URL_LASTMOD = "lastmod";
    public static final String SITEMAP_URL_CHANGEFREQ = "changefreq";
    public static final String SITEMAP_URL_PRIORITY = "priority";

    /**
     * The crawling profile used to parse the URLs contained in the sitemap file
     */
    private CrawlProfile.entry crawlingProfile = null;

    /**
     * Name of the current XML element
     */
    private String currentElement = null;

    /**
     * A special stream to count how many bytes were processed so far
     */
    private int streamCounter = 0;

    /**
     * The total length of the sitemap file
     */
    private long contentLength;

    /**
     * The amount of urls processes so far
     */
    private int urlCounter = 0;

    /**
     * the logger
     */
    private static final Log logger = new Log("SITEMAP");

    /**
     * The location of the sitemap file
     */
    private yacyURL siteMapURL = null;

    /**
     * The next URL to enqueue
     */
    private String nextURL = null;

    /**
     * last modification date of the {@link #nextURL}
     */
    private Date lastMod = null;
    private final Switchboard sb;
    
    public SitemapParser(final Switchboard sb, final yacyURL sitemap, final CrawlProfile.entry theCrawlingProfile) {
        assert sitemap != null;
        this.sb = sb;
        this.siteMapURL = sitemap;

        if (theCrawlingProfile == null) {
            // create a new profile
            this.crawlingProfile = createProfile(this.siteMapURL.getHost(), this.siteMapURL);
        } else {
            // use an existing profile
            this.crawlingProfile = theCrawlingProfile;
        }
    }

    /**
     * Function to download and parse the sitemap file
     */
    public void parse() {
        // download document
        final RequestHeader requestHeader = new RequestHeader();
        requestHeader.put(HeaderFramework.USER_AGENT, HTTPLoader.crawlerUserAgent);
        final Client client = new Client(5000, requestHeader);
        ResponseContainer res = null;
        try {
            res = client.GET(siteMapURL.toString());
            if (res.getStatusCode() != 200) {
                logger.logWarning("Unable to download the sitemap file " + this.siteMapURL +
                        "\nServer returned status: " + res.getStatusLine());
                return;
            }

            // getting some metadata
            final String contentMimeType = res.getResponseHeader().mime();
            this.contentLength = res.getResponseHeader().getContentLength();

            try {
                InputStream contentStream = res.getDataAsStream();
                if ((contentMimeType != null) &&
                        (contentMimeType.equals("application/x-gzip") || contentMimeType.equals("application/gzip"))) {
                    if (logger.isFine()) logger.logFine("Sitemap file has mimetype " + contentMimeType);
                    contentStream = new GZIPInputStream(contentStream);
                }

                final ByteCountInputStream counterStream = new ByteCountInputStream(contentStream, null);
                // parse it
                logger.logInfo("Start parsing sitemap file " + this.siteMapURL + "\n\tMimeType: " + contentMimeType +
                        "\n\tLength:   " + this.contentLength);
                final SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
                saxParser.parse(counterStream, this);
                streamCounter += counterStream.getCount();
            } finally {
                res.closeStream();
            }
        } catch (final Exception e) {
            logger.logWarning("Unable to parse sitemap file " + this.siteMapURL, e);
        } finally {
            if (res != null) {
                // release connection
                res.closeStream();
            }
        }
    }

    /**
     * @return the total length of the sitemap file in bytes or <code>-1</code> if the length is unknown
     */
    public long getTotalLength() {
        return this.contentLength;
    }

    /**
     * @return the amount of bytes of the sitemap file that were downloaded so far
     */
    public long getProcessedLength() {
        return streamCounter;
    }

    /**
     * @return the amount of URLs that were successfully enqueued so far
     */
    public long getUrlcount() {
        return this.urlCounter;
    }

    /**
     * @param localName local name
     * @param qName qualified name
     * @see DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    public void startElement(final String namespaceURI, final String localName, final String qName, final Attributes attrs) throws SAXException {
        this.currentElement = qName;

        // testing if the namespace is known
        if (qName.equalsIgnoreCase(SITEMAP_URLSET)) {
            final String namespace = attrs.getValue(SITEMAP_XMLNS);
            if ((namespace == null) ||
                    ((!namespace.equals(XMLNS_SITEMAPS_ORG)) && (!namespace.equals(XMLNS_SITEMAPS_GOOGLE))))
                throw new SAXException("Unknown sitemap namespace: " + namespace);
        }
    }

    /**
     * @param localName local name
     * @param qName qualified name
     * @throws SAXException
     * @see DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
     */
    public void endElement(final String namespaceURI, final String localName, final String qName) throws SAXException {
        this.currentElement = "";

        if (qName.equalsIgnoreCase(SITEMAP_URL)) {
            if (this.nextURL == null)
                return;

            // get the url hash
            String nexturlhash = null;
            yacyURL url = null;
            try {
                url = new yacyURL(this.nextURL, null);
                nexturlhash = url.hash();
            } catch (final MalformedURLException e1) {
            }

            // check if the url is known and needs to be recrawled
            if (this.lastMod != null) {
                final String dbocc = this.sb.urlExists(nexturlhash);
                if ((dbocc != null) && (dbocc.equalsIgnoreCase("loaded"))) {
                    // the url was already loaded. we need to check the date
                    final URLMetadataRow oldEntry = this.sb.indexSegment.urlMetadata().load(nexturlhash, null, 0);
                    if (oldEntry != null) {
                        final Date modDate = oldEntry.moddate();
                        // check if modDate is null
                        if (modDate.after(this.lastMod))
                            return;
                    }
                }
            }

            // URL needs to crawled
            this.sb.crawlStacker.enqueueEntry(new Request(
                    this.sb.peers.mySeed().hash,
                    url,
                    null, // this.siteMapURL.toString(),
                    this.nextURL,
                    new Date(),
                    null,
                    this.crawlingProfile.handle(),
                    0,
                    0,
                    0
                    ));
            logger.logInfo("New URL '" + this.nextURL + "' added for crawling.");
            this.urlCounter++;
        }
    }

    public void characters(final char[] buf, final int offset, final int len) throws SAXException {
        if (this.currentElement.equalsIgnoreCase(SITEMAP_URL_LOC)) {
            // TODO: we need to decode the URL here
            this.nextURL = (new String(buf, offset, len)).trim();
            if (!this.nextURL.startsWith("http") && !this.nextURL.startsWith("https")) {
                logger.logInfo("The url '" + this.nextURL + "' has a wrong format. Ignore it.");
                this.nextURL = null;
            }
        } else if (this.currentElement.equalsIgnoreCase(SITEMAP_URL_LASTMOD)) {
            final String dateStr = new String(buf, offset, len);
            try {
                this.lastMod = DateFormatter.parseISO8601(dateStr);
            } catch (final ParseException e) {
                logger.logInfo("Unable to parse datestring '" + dateStr + "'");
            }
        }
    }

    private CrawlProfile.entry createProfile(final String domainName, final yacyURL sitemapURL) {
        return this.sb.crawler.profilesActiveCrawls.newEntry(
                domainName, sitemapURL,
                // crawling Filter
                CrawlProfile.MATCH_ALL, CrawlProfile.MATCH_NEVER,
                // Depth
                0,
                // force recrawling
                0,
                // disable Auto-Dom-Filter
                -1, -1,
                // allow crawling of dynamic URLs
                true,
                // index text + media
                true, true,
                // don't store downloaded pages to Web Cache
                false,
                // store to TX cache
                true,
                // remote Indexing disabled
                false,
                // exclude stop-words
                true, true, true);
    }
}
