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

package net.yacy.crawler.retrieval;

import java.net.MalformedURLException;
import java.util.Date;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.HarvestProcess;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.document.parser.sitemapParser;
import net.yacy.document.parser.sitemapParser.URLEntry;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.Switchboard;

public class SitemapImporter extends Thread {

    private CrawlProfile crawlingProfile = null;
    private static final ConcurrentLog logger = new ConcurrentLog("SITEMAP");
    private DigestURL siteMapURL = null;
    private final Switchboard sb;

    public SitemapImporter(final Switchboard sb, final DigestURL sitemapURL, final CrawlProfile profileEntry) {
        assert sitemapURL != null;
        this.sb = sb;
        this.siteMapURL = sitemapURL;
        assert profileEntry != null;
        this.crawlingProfile = profileEntry;
    }

    @Override
    public void run() {
        try {
            logger.info("Start parsing sitemap file " + this.siteMapURL);
            sitemapParser.SitemapReader parser = sitemapParser.parse(this.siteMapURL, this.crawlingProfile.getAgent());
            parser.start();
            URLEntry item;
            while ((item = parser.take()) != sitemapParser.POISON_URLEntry) {
                process(item);
            }
        } catch (final Exception e) {
            logger.warn("Unable to parse sitemap file " + this.siteMapURL, e);
        }
    }

    public void process(sitemapParser.URLEntry entry) {

        // get the url hash
        byte[] nexturlhash = null;
        DigestURL url = null;
        try {
            url = new DigestURL(entry.url());
            nexturlhash = url.hash();
        } catch (final MalformedURLException e1) {
        }

        // check if the url is known and needs to be recrawled
        Date lastMod = entry.lastmod(null);
        if (lastMod != null) {
            final HarvestProcess dbocc = this.sb.urlExists(ASCII.String(nexturlhash));
            if (dbocc != null && dbocc == HarvestProcess.LOADED) {
                // the url was already loaded. we need to check the date
                final URIMetadataNode oldEntry = this.sb.index.fulltext().getMetadata(nexturlhash);
                if (oldEntry != null) {
                    final Date modDate = oldEntry.moddate();
                    // check if modDate is null
                    if (modDate.after(lastMod)) return;
                }
            }
        }

        // URL needs to crawled
        this.sb.crawlStacker.enqueueEntry(new Request(
                ASCII.getBytes(this.sb.peers.mySeed().hash),
                url,
                null, // this.siteMapURL.toString(),
                entry.url(),
                entry.lastmod(new Date()),
                this.crawlingProfile.handle(),
                0,
                0,
                0
                ));
        logger.info("New URL '" + entry.url() + "' added for loading.");
    }
}
