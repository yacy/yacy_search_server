// urls.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 22.08.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
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

import java.io.IOException;
import java.util.Date;

import de.anomic.crawler.CrawlEntry;
import de.anomic.crawler.NoticedURL;
import de.anomic.http.httpHeader;
import de.anomic.index.indexURLReference;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyNetwork;
import de.anomic.yacy.yacyURL;

public class urls {
    
    public static serverObjects respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        // insert default values
        final serverObjects prop = new serverObjects();
        prop.put("iam", sb.webIndex.seedDB.mySeed().hash);
        prop.put("response", "rejected - insufficient call parameters");
        prop.put("channel_title", "");
        prop.put("channel_description", "");
        prop.put("channel_pubDate", "");
        prop.put("item", "0");
        
        if ((post == null) || (env == null)) return prop;
        if (!yacyNetwork.authentifyRequest(post, env)) return prop;
        
        if (post.get("call", "").equals("remotecrawl")) {
            // perform a remote crawl url handover
            final int stackType = NoticedURL.STACK_TYPE_LIMIT;
            int count = Math.min(100, post.getInt("count", 0));
            int c = 0;
            CrawlEntry entry;
            yacyURL referrer;
            while ((count > 0) && (sb.crawlQueues.noticeURL.stackSize(stackType) > 0)) {
                try {
                    entry = sb.crawlQueues.noticeURL.pop(stackType, false);
                } catch (final IOException e) {
                    break;
                }
                if (entry == null) break;
                
                // find referrer, if there is one
                referrer = sb.getURL(entry.referrerhash());
                
                // place url to notice-url db
                sb.crawlQueues.delegatedURL.push(
                        sb.crawlQueues.delegatedURL.newEntry(
                                entry,
                                sb.webIndex.seedDB.mySeed().hash,
                                new Date(),
                                0,
                                "client=____________")
                );
                
                // create RSS entry
                prop.put("item_" + c + "_title", "");
                prop.putHTML("item_" + c + "_link", entry.url().toNormalform(true, false));
                prop.putHTML("item_" + c + "_referrer", (referrer == null) ? "" : referrer.toNormalform(true, false));
                prop.putHTML("item_" + c + "_description", entry.name());
                prop.put("item_" + c + "_author", "");
                prop.put("item_" + c + "_pubDate", serverDate.formatShortSecond(entry.appdate()));
                prop.put("item_" + c + "_guid", entry.url().hash());
                c++;
                count--;
            }
            prop.put("item", c);
            prop.putHTML("response", "ok");
        }
        
        if (post.get("call", "").equals("urlhashlist")) {
            // retrieve a list of urls from the LURL-db by a given list of url hashes
            final String urlhashes = post.get("hashes", "");
            if (urlhashes.length() % 12 != 0) return prop;
            final int count = urlhashes.length() / 12;
        	int c = 0;
        	indexURLReference entry;
        	indexURLReference.Components comp;
            yacyURL referrer;
            for (int i = 0; i < count; i++) {
                entry = sb.webIndex.getURL(urlhashes.substring(12 * i, 12 * (i + 1)), null, 0);
                if (entry == null) continue;
                // find referrer, if there is one
                referrer = sb.getURL(entry.referrerHash());
                // create RSS entry
                comp = entry.comp();
                prop.put("item_" + c + "_title", comp.dc_title());
                prop.putHTML("item_" + c + "_link", comp.url().toNormalform(true, false));
                prop.putHTML("item_" + c + "_referrer", (referrer == null) ? "" : referrer.toNormalform(true, false));
                prop.putHTML("item_" + c + "_description", comp.dc_title());
                prop.put("item_" + c + "_author", comp.dc_creator());
                prop.put("item_" + c + "_pubDate", serverDate.formatShortSecond(entry.moddate()));
                prop.put("item_" + c + "_guid", entry.hash());
                c++;
            }
            prop.put("item", c);
            prop.putHTML("response", "ok");
        }

        // return rewrite properties
        return prop;
    }
    
}
