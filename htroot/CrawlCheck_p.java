/**
 *  CrawlCheck_p
 *  Copyright 2012 by Michael Peter Christen
 *  First released 10.10.2011 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General private
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.crawler.data.CrawlQueues;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;
import net.yacy.crawler.robots.RobotsTxtEntry;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;


public class CrawlCheck_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        prop.put("starturls", "");
        if (post == null) return prop;
        
        if (post.containsKey("crawlcheck")) {
            
            // get the list of rootURls for this crawl start
            Set<DigestURI> rootURLs = new HashSet<DigestURI>();
            String crawlingStart0 = post.get("crawlingURLs","").trim();
            String[] rootURLs0 = crawlingStart0.indexOf('\n') > 0 || crawlingStart0.indexOf('\r') > 0 ? crawlingStart0.split("[\\r\\n]+") : crawlingStart0.split(Pattern.quote("|"));
            for (String crawlingStart: rootURLs0) {
                if (crawlingStart == null || crawlingStart.length() == 0) continue;
                // add the prefix http:// if necessary
                int pos = crawlingStart.indexOf("://",0);
                if (pos == -1) {
                    if (crawlingStart.startsWith("www")) crawlingStart = "http://" + crawlingStart;
                    if (crawlingStart.startsWith("ftp")) crawlingStart = "ftp://" + crawlingStart;
                }
                try {
                    DigestURI crawlingStartURL = new DigestURI(crawlingStart);
                    rootURLs.add(crawlingStartURL);
                } catch (MalformedURLException e) {
                    Log.logException(e);
                }
            }

            if (rootURLs.size() == 0) {
                prop.put("table", 0);
            } else {
                prop.put("table", 1);
                
                // make a string that is used to fill the starturls field again
                // and analyze the urls to make the table rows
                StringBuilder s = new StringBuilder(300);
                int row = 0;
                for (DigestURI u: rootURLs) {
                    s.append(u.toNormalform(true)).append('\n');
                    prop.put("table_list_" + row + "_url", u.toNormalform(true));

                    // try to load the robots
                    RobotsTxtEntry robotsEntry;
                    boolean robotsAllowed = true;
                    try {
                        robotsEntry = sb.robots.getEntry(u, sb.peers.myBotIDs());
                        if (robotsEntry == null) {
                            prop.put("table_list_" + row + "_robots", "no robots");
                            prop.put("table_list_" + row + "_crawldelay", CrawlQueues.queuedMinLoadDelay + " ms");
                            prop.put("table_list_" + row + "_sitemap", "");
                        } else {
                            robotsAllowed = !robotsEntry.isDisallowed(u);
                            prop.put("table_list_" + row + "_robots", "robots exist: " + (robotsAllowed ? "crawl allowed" : "url disallowed"));
                            prop.put("table_list_" + row + "_crawldelay", Math.max(CrawlQueues.queuedMinLoadDelay, robotsEntry.getCrawlDelayMillis()) + " ms");
                            prop.put("table_list_" + row + "_sitemap", robotsEntry.getSitemap() == null ? "-" : robotsEntry.getSitemap().toNormalform(true));
                        }                        
                    } catch (final IOException e) {
                    }
                    
                    // try to load the url
                    if (robotsAllowed) try {
                        Request request = sb.loader.request(u, true, false);
                        final Response response = sb.loader.load(request, CacheStrategy.NOCACHE, BlacklistType.CRAWLER, CrawlQueues.queuedMinLoadDelay);
                        if (response == null) {
                            prop.put("table_list_" + row + "_access", "no response");
                        } else {
                            if (response.getResponseHeader().getStatusCode() == 200) {
                                prop.put("table_list_" + row + "_access", "200 ok, last-modified = " + response.lastModified());
                            } else {
                                prop.put("table_list_" + row + "_access", response.getResponseHeader().getStatusCode() + " - load failed");
                            }
                        }
                    } catch (final IOException e) {
                        prop.put("table_list_" + row + "_access", "error response: " + e.getMessage());
                    } else {
                        prop.put("table_list_" + row + "_access", "not loaded - prevented by robots.txt");
                    }
                    row++;
                    
                }
                prop.put("table_list", row);
                prop.put("starturls", s.toString());
                
            }
        }
        
        return prop;
    }

}
