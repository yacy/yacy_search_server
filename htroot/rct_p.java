// rct_p.java
// -----------------------
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 28.11.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
import java.util.Date;
import java.util.Iterator;

import net.yacy.cora.document.feed.Hit;
import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.crawler.retrieval.Request;
import net.yacy.peers.DHTSelection;
import net.yacy.peers.Protocol;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class rct_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        if (post != null) {
            if (post.containsKey("retrieve")) {
                final String peerhash = post.get("peer", null);
                final Seed seed = (peerhash == null) ? null : sb.peers.getConnected(peerhash);
                final RSSFeed feed = (seed == null) ? null : Protocol.queryRemoteCrawlURLs(sb.peers, seed, 20, 60000);
                if (feed != null) {
                    for (final Hit item: feed) {
                        //System.out.println("URL=" + item.getLink() + ", desc=" + item.getDescription() + ", pubDate=" + item.getPubDate());

                        // put url on remote crawl stack
                        DigestURL url;
                        try {
                            url = new DigestURL(item.getLink());
                        } catch (final MalformedURLException e) {
                            url = null;
                        }
                        Date loaddate;
                        loaddate = item.getPubDate();
                        final DigestURL referrer = null; // referrer needed!
                        final String urlRejectReason = sb.crawlStacker.urlInAcceptedDomain(url);
                        if (urlRejectReason == null) {
                            // stack url
                            if (sb.getLog().isFinest()) sb.getLog().finest("crawlOrder: stack: url='" + url + "'");
                            sb.crawlStacker.enqueueEntry(new Request(
                                    peerhash.getBytes(),
                                    url,
                                    (referrer == null) ? null : referrer.hash(),
                                    "REMOTE-CRAWLING",
                                    loaddate,
                                    sb.crawler.defaultRemoteProfile.handle(),
                                    0,
                                    0,
                                    0));
                        } else {
                            env.getLog().warn("crawlOrder: Rejected URL '" + urlToString(url) + "': " + urlRejectReason);
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
    private static String urlToString(final DigestURL url) {
        return (url == null ? "null" : url.toNormalform(true));
    }

    private static void listHosts(final Switchboard sb, final serverObjects prop) {
        // list known hosts
        Seed seed;
        int hc = 0;
        if (sb.peers != null && sb.peers.sizeConnected() > 0) {
            final Iterator<Seed> e = DHTSelection.getProvidesRemoteCrawlURLs(sb.peers);
            while (e.hasNext()) {
                seed = e.next();
                if (seed != null) {
                    prop.put("hosts_" + hc + "_hosthash", seed.hash);
                    prop.putHTML("hosts_" + hc + "_hostname", seed.hash + " " + seed.get(Seed.NAME, "nameless") + " (" + seed.getLong(Seed.RCOUNT, 0) + ")");
                    hc++;
                }
            }
            prop.put("hosts", hc);
        } else {
            prop.put("hosts", "0");
        }
    }

}
