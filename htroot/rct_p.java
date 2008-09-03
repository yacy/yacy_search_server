// rct_p.java
// -----------------------
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 28.11.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2007-11-14 01:15:28 +0000 (Mi, 14 Nov 2007) $
// $LastChangedRevision: 4216 $
// $LastChangedBy: orbiter $
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

import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;

import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.xml.RSSFeed;
import de.anomic.xml.RSSMessage;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public class rct_p {
    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();

        if (post != null) {
            if (post.containsKey("retrieve")) {
                final String peerhash = post.get("peer", null);
                final yacySeed seed = (peerhash == null) ? null : sb.webIndex.seedDB.getConnected(peerhash);
                final RSSFeed feed = (seed == null) ? null : yacyClient.queryRemoteCrawlURLs(sb.webIndex.seedDB, seed, 10);
                if (feed != null) {
                    for (final RSSMessage item: feed) {
                        //System.out.println("URL=" + item.getLink() + ", desc=" + item.getDescription() + ", pubDate=" + item.getPubDate());
                        
                        // put url on remote crawl stack
                        yacyURL url;
                        try {
                            url = new yacyURL(item.getLink(), null);
                        } catch (final MalformedURLException e) {
                            url = null;
                        }
                        Date loaddate;
                        try {
                            loaddate = serverDate.parseShortSecond(item.getPubDate());
                        } catch (final ParseException e) {
                            loaddate = new Date();
                        }
                        final yacyURL referrer = null; // referrer needed!
                        final String urlRejectReason = sb.acceptURL(url);
                        if (urlRejectReason == null) {
                            // stack url
                            if (sb.getLog().isFinest()) sb.getLog().logFinest("crawlOrder: stack: url='" + url + "'");
                            final String reasonString = sb.crawlStacker.stackCrawl(url, referrer, peerhash, "REMOTE-CRAWLING", loaddate, 0, sb.webIndex.defaultRemoteProfile);

                            if (reasonString == null) {
                                // done
                                env.getLog().logInfo("crawlOrder: added remote crawl url: " + urlToString(url));
                            } else if (reasonString.startsWith("double")) {
                                // case where we have already the url loaded;
                                env.getLog().logInfo("crawlOrder: ignored double remote crawl url: " + urlToString(url));
                            } else {
                                env.getLog().logInfo("crawlOrder: ignored [" + reasonString + "] remote crawl url: " + urlToString(url));
                            }
                        } else {
                            env.getLog().logWarning("crawlOrder: Rejected URL '" + urlToString(url) + "': " + urlRejectReason);
                        }
                    }
                }
            }
        }
        
        listHosts(sb, prop);

        // return rewrite properties
        return prop;
    }

    /**
     * @param url
     * @return
     */
    private static String urlToString(final yacyURL url) {
        return (url == null ? "null" : url.toNormalform(true, false));
    }
    
    private static void listHosts(final plasmaSwitchboard sb, final serverObjects prop) {
        // list known hosts
        yacySeed seed;
        int hc = 0;
        if (sb.webIndex.seedDB != null && sb.webIndex.seedDB.sizeConnected() > 0) {
            final Iterator<yacySeed> e = sb.webIndex.peerActions.dhtAction.getProvidesRemoteCrawlURLs();
            while (e.hasNext()) {
                seed = e.next();
                if (seed != null) {
                    prop.put("hosts_" + hc + "_hosthash", seed.hash);
                    prop.putHTML("hosts_" + hc + "_hostname", seed.hash + " " + seed.get(yacySeed.NAME, "nameless") + " (" + seed.getLong(yacySeed.RCOUNT, 0) + ")");
                    hc++;
                }
            }
            prop.put("hosts", hc);
        } else {
            prop.put("hosts", "0");
        }
    }

}
