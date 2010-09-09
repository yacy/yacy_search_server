//SitemapImporter.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2007
//
//this file was contributed by Martin Thelian
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.crawler;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.zip.GZIPInputStream;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.document.parser.sitemapParser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.io.ByteCountInputStream;
import net.yacy.kelondro.logging.Log;
import de.anomic.crawler.retrieval.HTTPLoader;
import de.anomic.crawler.retrieval.Request;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;

public class SitemapImporter extends Thread {

    private CrawlProfile crawlingProfile = null;
    private static final Log logger = new Log("SITEMAP");
    private DigestURI siteMapURL = null;
    private final Switchboard sb;
    
    public SitemapImporter(final Switchboard sb, final DigestURI sitemapURL, final CrawlProfile profileEntry) {
        assert sitemapURL != null;
        this.sb = sb;
        this.siteMapURL = sitemapURL;
        assert profileEntry != null;
        this.crawlingProfile = profileEntry;
    }

    public void run() {
        // download document
        final RequestHeader requestHeader = new RequestHeader();
        requestHeader.put(HeaderFramework.USER_AGENT, HTTPLoader.crawlerUserAgent);
        final HTTPClient client = new HTTPClient();
        client.setTimout(5000);
        client.setHeader(requestHeader.entrySet());
        try {
            try {
                client.GET(siteMapURL.toString());
                if (client.getStatusCode() != 200) {
                    logger.logWarning("Unable to download the sitemap file " + this.siteMapURL +
                            "\nServer returned status: " + client.getHttpResponse().getStatusLine());
                    return;
                }

                // get some metadata
                final ResponseHeader header = new ResponseHeader(client.getHttpResponse().getAllHeaders());
                final String contentMimeType = header.mime();

                InputStream contentStream = client.getContentstream();
                if (contentMimeType != null && (contentMimeType.equals("application/x-gzip") || contentMimeType.equals("application/gzip"))) {
                    if (logger.isFine()) logger.logFine("Sitemap file has mimetype " + contentMimeType);
                    contentStream = new GZIPInputStream(contentStream);
                }

                final ByteCountInputStream counterStream = new ByteCountInputStream(contentStream, null);
                // parse it
                logger.logInfo("Start parsing sitemap file " + this.siteMapURL + "\n\tMimeType: " + contentMimeType + "\n\tLength:   " + header.getContentLength());
                sitemapParser.SitemapReader parser = sitemapParser.parse(counterStream);
                for (sitemapParser.SitemapEntry entry: parser) process(entry);
            } finally {
                client.finish();
            }
        } catch (final Exception e) {
            logger.logWarning("Unable to parse sitemap file " + this.siteMapURL, e);
        }
    }

    public void process(sitemapParser.SitemapEntry entry) {

        // get the url hash
        byte[] nexturlhash = null;
        DigestURI url = null;
        try {
            url = new DigestURI(entry.url(), null);
            nexturlhash = url.hash();
        } catch (final MalformedURLException e1) {
        }

        // check if the url is known and needs to be recrawled
        Date lastMod = entry.lastmod(null);
        if (lastMod != null) {
            final String dbocc = this.sb.urlExists(Segments.Process.LOCALCRAWLING, nexturlhash);
            if ((dbocc != null) && (dbocc.equalsIgnoreCase("loaded"))) {
                // the url was already loaded. we need to check the date
                final URIMetadataRow oldEntry = this.sb.indexSegments.urlMetadata(Segments.Process.LOCALCRAWLING).load(nexturlhash, null, 0);
                if (oldEntry != null) {
                    final Date modDate = oldEntry.moddate();
                    // check if modDate is null
                    if (modDate.after(lastMod)) return;
                }
            }
        }

        // URL needs to crawled
        this.sb.crawlStacker.enqueueEntry(new Request(
                this.sb.peers.mySeed().hash.getBytes(),
                url,
                null, // this.siteMapURL.toString(),
                entry.url(),
                entry.lastmod(new Date()),
                this.crawlingProfile.handle(),
                0,
                0,
                0
                ));
        logger.logInfo("New URL '" + entry.url() + "' added for loading.");
    }
}
