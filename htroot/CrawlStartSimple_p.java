// CrawlStartSimple_p.java
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

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public class CrawlStartSimple_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();
        
        // define visible variables
        prop.put("proxyPrefetchDepth", env.getConfig("proxyPrefetchDepth", "0"));
        prop.put("crawlingDepth", Math.min(3, env.getConfigLong("crawlingDepth", 0)));
        prop.put("crawlingFilter", env.getConfig("crawlingFilter", "0"));
        
        int crawlingIfOlder = (int) env.getConfigLong("crawlingIfOlder", -1);
        prop.put("crawlingIfOlderCheck", (crawlingIfOlder == -1) ? "0" : "1");
        prop.put("crawlingIfOlderUnitYearCheck", "0");
        prop.put("crawlingIfOlderUnitMonthCheck", "0");
        prop.put("crawlingIfOlderUnitDayCheck", "0");
        prop.put("crawlingIfOlderUnitHourCheck", "0");
        prop.put("crawlingIfOlderUnitMinuteCheck", "0");
        if ((crawlingIfOlder == -1) || (crawlingIfOlder == Integer.MAX_VALUE)) {
            prop.put("crawlingIfOlderNumber", "-1");
            prop.put("crawlingIfOlderUnitYearCheck", "1");
        } else if (crawlingIfOlder >= 60*24*365) {
            prop.put("crawlingIfOlderNumber", Math.round((float)crawlingIfOlder / (float)(60*24*365)));
            prop.put("crawlingIfOlderUnitYearCheck", "1");
        } else if (crawlingIfOlder >= 60*24*30) {
            prop.put("crawlingIfOlderNumber", Math.round((float)crawlingIfOlder / (float)(60*24*30)));
            prop.put("crawlingIfOlderUnitMonthCheck", "1");
        } else if (crawlingIfOlder >= 60*24) {
            prop.put("crawlingIfOlderNumber", Math.round((float)crawlingIfOlder / (float)(60*24)));
            prop.put("crawlingIfOlderUnitDayCheck", "1");
        } else if (crawlingIfOlder >= 60) {
            prop.put("crawlingIfOlderNumber", Math.round(crawlingIfOlder / 60f));
            prop.put("crawlingIfOlderUnitHourCheck", "1");
        } else {
            prop.put("crawlingIfOlderNumber", crawlingIfOlder);
            prop.put("crawlingIfOlderUnitMinuteCheck", "1");
        }
        int crawlingDomFilterDepth = (int) env.getConfigLong("crawlingDomFilterDepth", -1);
        prop.put("crawlingDomFilterCheck", (crawlingDomFilterDepth == -1) ? "0" : "1");
        prop.put("crawlingDomFilterDepth", (crawlingDomFilterDepth == -1) ? 1 : crawlingDomFilterDepth);
        int crawlingDomMaxPages = (int) env.getConfigLong("crawlingDomMaxPages", -1);
        prop.put("crawlingDomMaxCheck", (crawlingDomMaxPages == -1) ? "0" : "1");
        prop.put("crawlingDomMaxPages", (crawlingDomMaxPages == -1) ? 10000 : crawlingDomMaxPages);
        prop.put("crawlingQChecked", env.getConfig("crawlingQ", "").equals("true") ? "1" : "0");
        prop.put("storeHTCacheChecked", env.getConfig("storeHTCache", "").equals("true") ? "1" : "0");
        prop.put("indexingTextChecked", env.getConfig("indexText", "").equals("true") ? "1" : "0");
        prop.put("indexingMediaChecked", env.getConfig("indexMedia", "").equals("true") ? "1" : "0");
        prop.put("crawlOrderChecked", env.getConfig("crawlOrder", "").equals("true") ? "1" : "0");
        
        long LCbusySleep = Integer.parseInt(env.getConfig(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, "100"));
        int LCppm = (LCbusySleep == 0) ? 1000 : (int) (60000L / LCbusySleep);
        prop.put("crawlingSpeedMaxChecked", (LCppm >= 1000) ? "1" : "0");
        prop.put("crawlingSpeedCustChecked", ((LCppm > 10) && (LCppm < 1000)) ? "1" : "0");
        prop.put("crawlingSpeedMinChecked", (LCppm <= 10) ? "1" : "0");
        prop.put("customPPMdefault", ((LCppm > 10) && (LCppm < 1000)) ? Integer.toString(LCppm) : "");
        
        prop.put("xsstopwChecked", env.getConfig("xsstopw", "").equals("true") ? "1" : "0");
        prop.put("xdstopwChecked", env.getConfig("xdstopw", "").equals("true") ? "1" : "0");
        prop.put("xpstopwChecked", env.getConfig("xpstopw", "").equals("true") ? "1" : "0");
        
        // create prefetch table
        boolean dark = true;   
        
        // create other peer crawl table using YaCyNews
        Iterator<yacyNewsRecord> recordIterator = yacyCore.newsPool.recordIterator(yacyNewsPool.INCOMING_DB, true);
        int showedCrawl = 0;
        yacyNewsRecord record;
        yacySeed peer;
        String peername;
        while (recordIterator.hasNext()) {
            record = recordIterator.next();
            if (record == null) continue;
            if (record.category().equals(yacyNewsPool.CATEGORY_CRAWL_START)) {
                peer = yacyCore.seedDB.get(record.originator());
                if (peer == null) peername = record.originator(); else peername = peer.getName();
                prop.put("otherCrawlStartInProgress_" + showedCrawl + "_dark", dark ? "1" : "0");
                prop.put("otherCrawlStartInProgress_" + showedCrawl + "_cre", record.created().toString());
                prop.put("otherCrawlStartInProgress_" + showedCrawl + "_peername", peername);
                prop.put("otherCrawlStartInProgress_" + showedCrawl + "_startURL", record.attributes().get("startURL").toString());
                prop.put("otherCrawlStartInProgress_" + showedCrawl + "_intention", record.attributes().get("intention").toString());
                prop.put("otherCrawlStartInProgress_" + showedCrawl + "_generalDepth", record.attributes().get("generalDepth"));
                prop.put("otherCrawlStartInProgress_" + showedCrawl + "_crawlingQ", (record.attributes().get("crawlingQ").equals("true")) ? "1" : "0");
                showedCrawl++;
                if (showedCrawl > 20) break;
            }
        }
        prop.put("otherCrawlStartInProgress", showedCrawl);
        
        // finished remote crawls
        recordIterator = yacyCore.newsPool.recordIterator(yacyNewsPool.PROCESSED_DB, true);
        showedCrawl = 0;
        while (recordIterator.hasNext()) {
            record = (yacyNewsRecord) recordIterator.next();
            if (record == null) continue;
            if (record.category().equals(yacyNewsPool.CATEGORY_CRAWL_START)) {
                peer = yacyCore.seedDB.get(record.originator());
                if (peer == null) peername = record.originator(); else peername = peer.getName();
                prop.put("otherCrawlStartFinished_" + showedCrawl + "_dark", dark ? "1" : "0");
                prop.put("otherCrawlStartFinished_" + showedCrawl + "_cre", record.created().toString());
                prop.put("otherCrawlStartFinished_" + showedCrawl + "_peername", peername);
                prop.put("otherCrawlStartFinished_" + showedCrawl + "_startURL", record.attributes().get("startURL").toString());
                prop.put("otherCrawlStartFinished_" + showedCrawl + "_intention", record.attributes().get("intention").toString());
                prop.put("otherCrawlStartFinished_" + showedCrawl + "_generalDepth", record.attributes().get("generalDepth"));
                prop.put("otherCrawlStartFinished_" + showedCrawl + "_crawlingQ", (record.attributes().get("crawlingQ").equals("true")) ? "1" : "0");
                showedCrawl++;
                if (showedCrawl > 20) break;
            }
        }
        prop.put("otherCrawlStartFinished", showedCrawl);

        
        // remote crawl peers
        if ((yacyCore.seedDB == null) || (yacyCore.seedDB.mySeed().isVirgin()) || (yacyCore.seedDB.mySeed().isJunior())) {
            prop.put("remoteCrawlPeers", "0");
        } else {
            Iterator<yacySeed> crawlavail = yacyCore.dhtAgent.getAcceptRemoteCrawlSeeds(yacyURL.dummyHash, true);
            Iterator<yacySeed> crawlpendi = yacyCore.dhtAgent.getAcceptRemoteCrawlSeeds(yacyURL.dummyHash, false);
            if ((!(crawlavail.hasNext())) && (!(crawlpendi.hasNext()))) {
                prop.put("remoteCrawlPeers", "0"); //no peers availible
            } else {
                prop.put("remoteCrawlPeers", "1");
                int maxcount = 100;
                int availcount = 0;
                yacySeed seed;
                while ((availcount < maxcount) && (crawlavail.hasNext())) {
                    seed = crawlavail.next();
                    prop.put("remoteCrawlPeers_available_" + availcount + "_name", seed.getName());
                    prop.put("remoteCrawlPeers_available_" + availcount + "_due", (yacyCore.yacyTime() - seed.available));
                    availcount++;
                }
                prop.put("remoteCrawlPeers_available", availcount);
                int pendicount = 0;
                while ((pendicount < maxcount) && (crawlpendi.hasNext())) {
                    seed = crawlpendi.next();
                    prop.put("remoteCrawlPeers_busy_" + pendicount + "_name", seed.getName());
                    prop.put("remoteCrawlPeers_busy_" + pendicount + "_due", (yacyCore.yacyTime() - seed.available));
                    pendicount++;
                }
                prop.put("remoteCrawlPeers_busy", pendicount);
                prop.put("remoteCrawlPeers_num", (availcount + pendicount));
            }
        }
        
        // return rewrite properties
        return prop;
    }
}
