// IndexCreate_p.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 02.12.2004
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// You must compile this file with
// javac -classpath .:../classes IndexCreate_p.java
// if the shell's current path is HTROOT

import java.io.IOException;
import java.util.Enumeration;

import de.anomic.data.wikiCode;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaURL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverThread;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacySeed;

public class IndexCreate_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        
        prop.put("info", 0);
        prop.put("refreshbutton", 0);
        
        if (post != null) {
            if (post.containsKey("distributedcrawling")) {
                long newBusySleep = Integer.parseInt(env.getConfig(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP, "100"));
                if (post.get("dcr", "").equals("acceptCrawlMax")) {
                    env.setConfig("crawlResponse", "true");
                    newBusySleep = 100;
                } else if (post.get("dcr", "").equals("acceptCrawlLimited")) {
                    env.setConfig("crawlResponse", "true");
                    int newppm = Integer.parseInt(post.get("acceptCrawlLimit", "1"));
                    if (newppm < 1) newppm = 1;
                    newBusySleep = 60000 / newppm;
                    if (newBusySleep < 100) newBusySleep = 100;
                } else if (post.get("dcr", "").equals("acceptCrawlDenied")) {
                    env.setConfig("crawlResponse", "false");
                }
                serverThread rct = switchboard.getThread(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
                rct.setBusySleep(newBusySleep);
                env.setConfig(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP, Long.toString(newBusySleep));
                //boolean crawlResponse = ((String) post.get("acceptCrawlMax", "")).equals("on");
                //env.setConfig("crawlResponse", (crawlResponse) ? "true" : "false");
            }

            if (post.containsKey("pausecrawlqueue")) {
                switchboard.pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
                prop.put("info", 1);//crawling paused
            }
            
            if (post.containsKey("continuecrawlqueue")) {
                switchboard.continueCrawlJob(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
                prop.put("info", 2);//crawling continued
            }
            
        }
        
        // define visible variables
        prop.put("proxyPrefetchDepth", env.getConfig("proxyPrefetchDepth", "0"));
        prop.put("crawlingDepth", env.getConfig("crawlingDepth", "0"));
        prop.put("crawlingFilter", env.getConfig("crawlingFilter", "0"));
        
        int crawlingIfOlder = (int) env.getConfigLong("crawlingIfOlder", -1);
        prop.put("crawlingIfOlderCheck", (crawlingIfOlder == -1) ? 0 : 1);
        prop.put("crawlingIfOlderUnitYearCheck", 0);
        prop.put("crawlingIfOlderUnitMonthCheck", 0);
        prop.put("crawlingIfOlderUnitDayCheck", 0);
        prop.put("crawlingIfOlderUnitHourCheck", 0);
        prop.put("crawlingIfOlderUnitMinuteCheck", 0);
        if ((crawlingIfOlder == -1) || (crawlingIfOlder == Integer.MAX_VALUE)) {
            prop.put("crawlingIfOlderNumber", -1);
            prop.put("crawlingIfOlderUnitYearCheck", 1);
        } else if (crawlingIfOlder >= 60*24*365) {
            prop.put("crawlingIfOlderNumber", Math.round((float)crawlingIfOlder / (float)(60*24*365)));
            prop.put("crawlingIfOlderUnitYearCheck", 1);
        } else if (crawlingIfOlder >= 60*24*30) {
            prop.put("crawlingIfOlderNumber", Math.round((float)crawlingIfOlder / (float)(60*24*30)));
            prop.put("crawlingIfOlderUnitMonthCheck", 1);
        } else if (crawlingIfOlder >= 60*24) {
            prop.put("crawlingIfOlderNumber", Math.round((float)crawlingIfOlder / (float)(60*24)));
            prop.put("crawlingIfOlderUnitDayCheck", 1);
        } else if (crawlingIfOlder >= 60) {
            prop.put("crawlingIfOlderNumber", Math.round((float)crawlingIfOlder / 60f));
            prop.put("crawlingIfOlderUnitHourCheck", 1);
        } else {
            prop.put("crawlingIfOlderNumber", crawlingIfOlder);
            prop.put("crawlingIfOlderUnitMinuteCheck", 1);
        }
        int crawlingDomFilterDepth = (int) env.getConfigLong("crawlingDomFilterDepth", -1);
        prop.put("crawlingDomFilterCheck", (crawlingDomFilterDepth == -1) ? 0 : 1);
        prop.put("crawlingDomFilterDepth", (crawlingDomFilterDepth == -1) ? 1 : crawlingDomFilterDepth);
        int crawlingDomMaxPages = (int) env.getConfigLong("crawlingDomMaxPages", -1);
        prop.put("crawlingDomMaxCheck", (crawlingDomMaxPages == -1) ? 0 : 1);
        prop.put("crawlingDomMaxPages", (crawlingDomMaxPages == -1) ? 10000 : crawlingDomMaxPages);
        prop.put("crawlingQChecked", env.getConfig("crawlingQ", "").equals("true") ? 1 : 0);
        prop.put("storeHTCacheChecked", env.getConfig("storeHTCache", "").equals("true") ? 1 : 0);
        prop.put("indexingTextChecked", env.getConfig("indexText", "").equals("true") ? 1 : 0);
        prop.put("indexingMediaChecked", env.getConfig("indexMedia", "").equals("true") ? 1 : 0);
        prop.put("crawlOrderChecked", env.getConfig("crawlOrder", "").equals("true") ? 1 : 0);
        
        long LCbusySleep = Integer.parseInt(env.getConfig(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, "100"));
        int LCppm = (LCbusySleep == 0) ? 1000 : (int) (60000L / LCbusySleep);
        prop.put("crawlingSpeedMaxChecked", (LCppm >= 1000) ? 1 : 0);
        prop.put("crawlingSpeedCustChecked", ((LCppm > 10) && (LCppm < 1000)) ? 1 : 0);
        prop.put("crawlingSpeedMinChecked", (LCppm <= 10) ? 1 : 0);
        prop.put("customPPMdefault", ((LCppm > 10) && (LCppm < 1000)) ? Integer.toString(LCppm) : "");

        long RTCbusySleep = Integer.parseInt(env.getConfig(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP, "100"));
        if (RTCbusySleep < 100) {
            RTCbusySleep = 100;
            env.setConfig(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP, Long.toString(RTCbusySleep));
        }
        if (env.getConfig("crawlResponse", "").equals("true")) {
            if (RTCbusySleep <= 100) {
                prop.put("acceptCrawlMaxChecked", 1);
                prop.put("acceptCrawlLimitedChecked", 0);
                prop.put("acceptCrawlDeniedChecked", 0);
            } else {
                prop.put("acceptCrawlMaxChecked", 0);
                prop.put("acceptCrawlLimitedChecked", 1);
                prop.put("acceptCrawlDeniedChecked", 0);
            }
        } else {
            prop.put("acceptCrawlMaxChecked", 0);
            prop.put("acceptCrawlLimitedChecked", 0);
            prop.put("acceptCrawlDeniedChecked", 1);
        }
        int RTCppm = (RTCbusySleep == 0) ? 60 : (int) (60000L / RTCbusySleep);
        if (RTCppm > 60) RTCppm = 60;
        prop.put("PPM", RTCppm);
        
        prop.put("xsstopwChecked", env.getConfig("xsstopw", "").equals("true") ? 1 : 0);
        prop.put("xdstopwChecked", env.getConfig("xdstopw", "").equals("true") ? 1 : 0);
        prop.put("xpstopwChecked", env.getConfig("xpstopw", "").equals("true") ? 1 : 0);
        
        int queueStackSize = switchboard.sbQueue.size();
        int loaderThreadsSize = switchboard.cacheLoader.size();
        int crawlerListSize = switchboard.noticeURL.stackSize();
        int completequeue = queueStackSize + loaderThreadsSize + crawlerListSize;
        
        if ((completequeue > 0) || ((post != null) && (post.containsKey("refreshpage")))) {
            prop.put("refreshbutton", 1);
        }
        
        // create prefetch table
        boolean dark = true;   
        
        // create other peer crawl table using YaCyNews
        int availableNews = yacyCore.newsPool.size(yacyNewsPool.INCOMING_DB);
        int showedCrawl = 0;
        yacyNewsRecord record;
        yacySeed peer;
        String peername;
        try {
            for (int c = 0; c < availableNews; c++) {
                record = yacyCore.newsPool.get(yacyNewsPool.INCOMING_DB, c);
                if (record == null) continue;
                if (record.category().equals(yacyNewsPool.CATEGORY_CRAWL_START)) {
                    peer = yacyCore.seedDB.get(record.originator());
                    if (peer == null) peername = record.originator(); else peername = peer.getName();
                    prop.put("otherCrawlStartInProgress_" + showedCrawl + "_dark", ((dark) ? 1 : 0));
                    prop.put("otherCrawlStartInProgress_" + showedCrawl + "_cre", record.created());
                    prop.put("otherCrawlStartInProgress_" + showedCrawl + "_peername", wikiCode.replaceHTML(peername));
                    prop.put("otherCrawlStartInProgress_" + showedCrawl + "_startURL", wikiCode.replaceHTML(record.attributes().get("startURL").toString()));
                    prop.put("otherCrawlStartInProgress_" + showedCrawl + "_intention", wikiCode.replaceHTML(record.attributes().get("intention").toString()));
                    prop.put("otherCrawlStartInProgress_" + showedCrawl + "_generalDepth", record.attributes().get("generalDepth"));
                    prop.put("otherCrawlStartInProgress_" + showedCrawl + "_crawlingQ", (record.attributes().get("crawlingQ").equals("true")) ? 1 : 0);
                    showedCrawl++;
                    if (showedCrawl > 20) break;
                }
                
            }
        } catch (IOException e) {}
        prop.put("otherCrawlStartInProgress", showedCrawl);
        
        // finished remote crawls
        availableNews = yacyCore.newsPool.size(yacyNewsPool.PROCESSED_DB);
        showedCrawl = 0;
        try {
            for (int c = 0; c < availableNews; c++) {
                record = yacyCore.newsPool.get(yacyNewsPool.PROCESSED_DB, c);
                if (record == null) continue;
                if (record.category().equals(yacyNewsPool.CATEGORY_CRAWL_START)) {
                    peer = yacyCore.seedDB.get(record.originator());
                    if (peer == null) peername = record.originator(); else peername = peer.getName();
                    prop.put("otherCrawlStartFinished_" + showedCrawl + "_dark", ((dark) ? 1 : 0));
                    prop.put("otherCrawlStartFinished_" + showedCrawl + "_cre", record.created());
                    prop.put("otherCrawlStartFinished_" + showedCrawl + "_peername", wikiCode.replaceHTML(peername));
                    prop.put("otherCrawlStartFinished_" + showedCrawl + "_startURL", wikiCode.replaceHTML(record.attributes().get("startURL").toString()));
                    prop.put("otherCrawlStartFinished_" + showedCrawl + "_intention", wikiCode.replaceHTML(record.attributes().get("intention").toString()));
                    prop.put("otherCrawlStartFinished_" + showedCrawl + "_generalDepth", record.attributes().get("generalDepth"));
                    prop.put("otherCrawlStartFinished_" + showedCrawl + "_crawlingQ", (record.attributes().get("crawlingQ").equals("true")) ? 1 : 0);
                    showedCrawl++;
                    if (showedCrawl > 20) break;
                }
                
            }
        } catch (IOException e) {}
        prop.put("otherCrawlStartFinished", showedCrawl);

        
        // remote crawl peers
        if (yacyCore.seedDB != null) {
            prop.put("remoteCrawlPeers", 0);
        } else {
            Enumeration crawlavail = yacyCore.dhtAgent.getAcceptRemoteCrawlSeeds(plasmaURL.dummyHash, true);
            Enumeration crawlpendi = yacyCore.dhtAgent.getAcceptRemoteCrawlSeeds(plasmaURL.dummyHash, false);
            if ((!(crawlavail.hasMoreElements())) && (!(crawlpendi.hasMoreElements()))) {
                prop.put("remoteCrawlPeers", 0); //no peers availible
            } else {
                prop.put("remoteCrawlPeers", 1);
                int maxcount = 100;
                int availcount = 0;
                yacySeed seed;
                while ((availcount < maxcount) && (crawlavail.hasMoreElements())) {
                    seed = (yacySeed) crawlavail.nextElement();
                    prop.put("remoteCrawlPeers_available_" + availcount + "_name", seed.getName());
                    prop.put("remoteCrawlPeers_available_" + availcount + "_due", (yacyCore.yacyTime() - seed.available));
                    availcount++;
                }
                prop.put("remoteCrawlPeers_available", availcount);
                int pendicount = 0;
                while ((pendicount < maxcount) && (crawlpendi.hasMoreElements())) {
                    seed = (yacySeed) crawlpendi.nextElement();
                    prop.put("remoteCrawlPeers_busy_" + pendicount + "_name", seed.getName());
                    prop.put("remoteCrawlPeers_busy_" + pendicount + "_due", (yacyCore.yacyTime() - seed.available));
                    pendicount++;
                }
                prop.put("remoteCrawlPeers_busy", pendicount);
                prop.put("remoteCrawlPeers_num", (availcount + pendicount));
            }

        }
        
        prop.put("crawler-paused",(switchboard.crawlJobIsPaused(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL))?0:1);
        
        // return rewrite properties
        return prop;
    }

}



