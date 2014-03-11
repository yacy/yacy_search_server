//QuickCrawlLink_p.java
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file was contributed by Martin Thelian
//$LastChangedDate$
//$LastChangedBy$
//$LastChangedRevision$
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

//You must compile this file with
//javac -classpath .:../classes IndexCreate_p.java
//if the shell's current path is HTROOT


import java.net.MalformedURLException;
import java.util.Date;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.NumberTools;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.retrieval.Request;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Segment;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class QuickCrawlLink_p {

    /**
     * Example Javascript to call this servlet:
     * <code>javascript:w = window.open('http://user:pwd@localhost:8090/QuickCrawlLink_p.html?indexText=on&indexMedia=on&crawlingQ=on&xdstopw=on&title=' + escape(document.title) + '&url=' + location.href,'_blank','height=150,width=500,resizable=yes,scrollbar=no,directory=no,menubar=no,location=no'); w.focus();</code>
     * @param header the complete HTTP header of the request
     * @param post any arguments for this servlet, the request carried with (GET as well as POST)
     * @param env the serverSwitch object holding all runtime-data
     * @return the rewrite-properties for the template
     */
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        // get segment
        Segment indexSegment = sb.index;

        if (post == null) {
            // send back usage example
            prop.put("mode", "0");

            // get the http host header
            final String hostSocket = header.get(HeaderFramework.CONNECTION_PROP_HOST);

            //String host = hostSocket;
            int port = 80;
            final int pos = hostSocket.indexOf(':',0);
            if (pos != -1) {
                port = NumberTools.parseIntDecSubstring(hostSocket, pos + 1);
                //host = hostSocket.substring(0, pos);
            }

            prop.put("mode_host", Domains.LOCALHOST);
            prop.put("mode_port", port);

            return prop;
        }
        prop.put("mode", "1");

        // get the URL
        String crawlingStart = post.get("url",null);
        crawlingStart = UTF8.decodeURL(crawlingStart);

        // get the browser title
        final String title = post.get("title",null);

        // get other parameters if set
        final String crawlingMustMatch  = post.get("mustmatch", CrawlProfile.MATCH_ALL_STRING);
        final String crawlingMustNotMatch  = post.get("mustnotmatch", CrawlProfile.MATCH_NEVER_STRING);
        final int CrawlingDepth      = post.getInt("crawlingDepth", 0);
        final boolean crawlingQ      = post.get("crawlingQ", "").equals("on");
        final boolean followFrames   = post.get("followFrames", "").equals("on");
        final boolean obeyHtmlRobotsNoindex = post.get("obeyHtmlRobotsNoindex", "").equals("on");
        final boolean indexText      = post.get("indexText", "off").equals("on");
        final boolean indexMedia     = post.get("indexMedia", "off").equals("on");
        final boolean storeHTCache   = post.get("storeHTCache", "").equals("on");
        final boolean remoteIndexing = post.get("crawlOrder", "").equals("on");
        final String collection      = post.get("collection", "user");

        prop.put("mode_url", (crawlingStart == null) ? "unknown" : crawlingStart);
        prop.putHTML("mode_title", (title == null) ? "unknown" : title);

        if (crawlingStart != null) {
            crawlingStart = crawlingStart.trim();
            try {crawlingStart = new DigestURL(crawlingStart).toNormalform(true);} catch (final MalformedURLException e1) {}

            // check if url is proper
            DigestURL crawlingStartURL = null;
            try {
                crawlingStartURL = new DigestURL(crawlingStart);
            } catch (final MalformedURLException e) {
                prop.put("mode_status", "1");
                prop.put("mode_code", "1");
                return prop;
            }

            final byte[] urlhash = crawlingStartURL.hash();
            indexSegment.fulltext().remove(urlhash);
            sb.crawlQueues.noticeURL.removeByURLHash(urlhash);

            // create crawling profile
            CrawlProfile pe = null;
            try {
                pe = new CrawlProfile(
                        (crawlingStartURL.getHost() == null) ? crawlingStartURL.toNormalform(true) : crawlingStartURL.getHost(),
                        crawlingMustMatch,               //crawlerUrlMustMatch
                        crawlingMustNotMatch,            //crawlerUrlMustNotMatch
                        CrawlProfile.MATCH_ALL_STRING,   //crawlerIpMustMatch
                        CrawlProfile.MATCH_NEVER_STRING, //crawlerIpMustNotMatch
                        CrawlProfile.MATCH_NEVER_STRING, //crawlerCountryMustMatch
                        CrawlProfile.MATCH_NEVER_STRING, //crawlerNoDepthLimitMatch
                        CrawlProfile.MATCH_ALL_STRING,   //indexUrlMustMatch
                        CrawlProfile.MATCH_NEVER_STRING, //indexUrlMustNotMatch
                        CrawlProfile.MATCH_ALL_STRING,   //indexContentMustMatch
                        CrawlProfile.MATCH_NEVER_STRING, //indexContentMustNotMatch
                        CrawlingDepth,
                        true,
                        60 * 24 * 30, // recrawlIfOlder (minutes); here: one month
                        -1, // domMaxPages, if negative: no count restriction
                        crawlingQ, followFrames, obeyHtmlRobotsNoindex,
                        indexText, indexMedia,
                        storeHTCache, remoteIndexing,
                        CacheStrategy.IFFRESH,
                        collection,
                        ClientIdentification.yacyIntranetCrawlerAgentName);
                sb.crawler.putActive(pe.handle().getBytes(), pe);
            } catch (final Exception e) {
                // mist
                prop.put("mode_status", "2");//Error with url
                prop.put("mode_code", "2");
                prop.putHTML("mode_status_error", e.getMessage());
                return prop;
            }

            // stack URL
            String reasonString = null;
            reasonString = sb.crawlStacker.stackCrawl(new Request(
                    sb.peers.mySeed().hash.getBytes(),
                    crawlingStartURL,
                    null,
                    (title==null)?"CRAWLING-ROOT":title,
                    new Date(),
                    pe.handle(),
                    0,
                    0,
                    0
                ));

            // validate rejection reason
            if (reasonString == null) {
                prop.put("mode_status", "0");//start msg
                prop.put("mode_code", "0");
            } else {
                prop.put("mode_status", "3");//start msg
                prop.put("mode_code","3");
                prop.putHTML("mode_status_error", reasonString);
            }
        }

        return prop;
    }
}
