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

import java.net.MalformedURLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.robots.RobotsTxt.CheckEntry;
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
            Set<DigestURL> rootURLs = new LinkedHashSet<DigestURL>();
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
                    DigestURL crawlingStartURL = new DigestURL(crawlingStart);
                    rootURLs.add(crawlingStartURL);
                } catch (final MalformedURLException e) {
                    ConcurrentLog.logException(e);
                }
            }

            if (rootURLs.size() == 0) {
                prop.put("table", 0);
            } else {
                prop.put("table", 1);
                
                // mass check
                final ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
                final int concurrency = Math.min(rootURLs.size(), 20);
                Collection<CheckEntry> out = sb.robots.massCrawlCheck(rootURLs, agent, concurrency);
                // evaluate the result from the concurrent computation
                
                // make a string that is used to fill the starturls field again
                // and analyze the urls to make the table rows
                StringBuilder s = new StringBuilder(300);
                int row = 0;
                for (CheckEntry entry: out) {
                    String u = entry.digestURL.toNormalform(true);
                    s.append(u).append('\n');
                    prop.put("table_list_" + row + "_url", u);

                    // try to load the robots
                    boolean robotsAllowed = true;
                    if (entry.robotsTxtEntry == null) {
                        prop.put("table_list_" + row + "_robots", "no robots");
                        prop.put("table_list_" + row + "_crawldelay", agent.minimumDelta + " ms");
                        prop.put("table_list_" + row + "_sitemap", "");
                    } else {
                        robotsAllowed = !entry.robotsTxtEntry.isDisallowed(entry.digestURL);
                        prop.put("table_list_" + row + "_robots", "robots exist: " + (robotsAllowed ? "crawl allowed" : "url disallowed"));
                        prop.put("table_list_" + row + "_crawldelay", Math.max(agent.minimumDelta, entry.robotsTxtEntry.getCrawlDelayMillis()) + " ms");
                        prop.put("table_list_" + row + "_sitemap", entry.robotsTxtEntry.getSitemaps().toString());
                    }
                    
                    // try to load the url
                    if (robotsAllowed) {
                        if (entry.response == null) {
                            prop.put("table_list_" + row + "_access", entry.error == null ? "no response" : entry.error);
                        } else {
                            if (entry.response.getResponseHeader().getStatusCode() == 200) {
                                prop.put("table_list_" + row + "_access", "200 ok, last-modified = " + entry.response.lastModified());
                            } else {
                                prop.put("table_list_" + row + "_access", entry.response.getResponseHeader().getStatusCode() + " - load failed");
                            }
                        }
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
