// CrawlMonitorRemoteStart.java
// (C) 2004 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 02.12.2004 as IndexCreate_p.java on http://yacy.net
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

import java.util.Iterator;

import net.yacy.cora.protocol.RequestHeader;

import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyNewsDB;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacySeed;

public class CrawlMonitorRemoteStart {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        boolean dark = true;   
        
        // create other peer crawl table using YaCyNews
        Iterator<yacyNewsDB.Record> recordIterator = sb.peers.newsPool.recordIterator(yacyNewsPool.INCOMING_DB, true);
        int showedCrawl = 0;
        yacyNewsDB.Record record;
        yacySeed peer;
        String peername;
        while (recordIterator.hasNext()) {
            record = recordIterator.next();
            if (record == null) {
                continue;
            }
            if (record.category().equals(yacyNewsPool.CATEGORY_CRAWL_START)) {
                peer = sb.peers.get(record.originator());
                peername = (peer == null) ? record.originator() : peer.getName();

                prop.put("otherCrawlStartInProgress_" + showedCrawl + "_dark", dark ? "1" : "0");
                prop.put("otherCrawlStartInProgress_" + showedCrawl + "_cre", record.created().toString());
                prop.put("otherCrawlStartInProgress_" + showedCrawl + "_peername", peername);
                prop.put("otherCrawlStartInProgress_" + showedCrawl + "_startURL", record.attributes().get("startURL").toString());
                prop.put("otherCrawlStartInProgress_" + showedCrawl + "_intention", record.attributes().get("intention").toString());
                prop.put("otherCrawlStartInProgress_" + showedCrawl + "_generalDepth", record.attributes().get("generalDepth"));
                prop.put("otherCrawlStartInProgress_" + showedCrawl + "_crawlingQ", ("true".equals(record.attributes().get("crawlingQ"))) ? "1" : "0");
                showedCrawl++;
                if (showedCrawl > 20) {
                    break;
                }
            }
        }
        prop.put("otherCrawlStartInProgress", showedCrawl);
        
        // finished remote crawls
        recordIterator = sb.peers.newsPool.recordIterator(yacyNewsPool.PROCESSED_DB, true);
        showedCrawl = 0;
        while (recordIterator.hasNext()) {
            record = recordIterator.next();
            if (record == null) {
                continue;
            }
            if (record.category().equals(yacyNewsPool.CATEGORY_CRAWL_START)) {
                peer = sb.peers.get(record.originator());
                peername = (peer == null) ? record.originator() : peer.getName();

                prop.put("otherCrawlStartFinished_" + showedCrawl + "_dark", dark ? "1" : "0");
                prop.put("otherCrawlStartFinished_" + showedCrawl + "_cre", record.created().toString());
                prop.putHTML("otherCrawlStartFinished_" + showedCrawl + "_peername", peername);
                prop.putHTML("otherCrawlStartFinished_" + showedCrawl + "_startURL", record.attributes().get("startURL").toString());
                prop.put("otherCrawlStartFinished_" + showedCrawl + "_intention", record.attributes().get("intention").toString());
                prop.put("otherCrawlStartFinished_" + showedCrawl + "_generalDepth", record.attributes().get("generalDepth"));
                prop.put("otherCrawlStartFinished_" + showedCrawl + "_crawlingQ", ("true".equals(record.attributes().get("crawlingQ"))) ? "1" : "0");
                showedCrawl++;
                if (showedCrawl > 20) break;
            }
        }
        prop.put("otherCrawlStartFinished", showedCrawl);

        // return rewrite properties
        return prop;
    }
}