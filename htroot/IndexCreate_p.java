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

// you must compile this file with
// javac -classpath .:../classes IndexCreate_p.java
// if the shell's current path is HTROOT

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaCrawlEURL;
import de.anomic.plasma.plasmaCrawlLoaderMessage;
import de.anomic.plasma.plasmaCrawlNURL;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaCrawlWorker;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class IndexCreate_p {
    
    private static SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
    private static String daydate(Date date) {
        if (date == null) return ""; else return dayFormatter.format(date);
    }
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        
        prop.put("error", 0);
        prop.put("info", 0);
        prop.put("refreshbutton", 0);
        prop.put("rejected", 0);
        int showRejectedCount = 10;
        int i;
        
        if (post != null) {
            if (post.containsKey("crawlingstart")) {
                // init crawl
                if (yacyCore.seedDB == null) {
                    prop.put("error", 3);
                } else {
                    // set new properties
                    String newcrawlingfilter = post.get("crawlingFilter", ".*");
                    env.setConfig("crawlingFilter", newcrawlingfilter);
                    int newcrawlingdepth = Integer.parseInt((String) post.get("crawlingDepth", "0"));
                    env.setConfig("crawlingDepth", ("" + newcrawlingdepth));
                    boolean crawlingQ = ((String) post.get("crawlingQ", "")).equals("on");
                    env.setConfig("crawlingQ", (crawlingQ) ? "true" : "false");
                    boolean storeHTCache = ((String) post.get("storeHTCache", "")).equals("on");
                    env.setConfig("storeHTCache", (storeHTCache) ? "true" : "false");
                    boolean localIndexing = ((String) post.get("localIndexing", "")).equals("on");
                    env.setConfig("localIndexing", (localIndexing) ? "true" : "false");
                    boolean crawlOrder = ((String) post.get("crawlOrder", "")).equals("on");
                    env.setConfig("crawlOrder", (crawlOrder) ? "true" : "false");
                    boolean xsstopw = ((String) post.get("xsstopw", "")).equals("on");
                    env.setConfig("xsstopw", (crawlOrder) ? "true" : "false");
                    boolean xdstopw = ((String) post.get("xdstopw", "")).equals("on");
                    env.setConfig("xdstopw", (crawlOrder) ? "true" : "false");
                    boolean xpstopw = ((String) post.get("xpstopw", "")).equals("on");
                    env.setConfig("xpstopw", (crawlOrder) ? "true" : "false");
                    
                    String crawlingStart = (String) post.get("crawlingURL");
                    if (!(crawlingStart.startsWith("http"))) crawlingStart = "http://" + crawlingStart;
                    
                    // check if url is proper
                    URL crawlingStartURL = null;
                    try {
                        crawlingStartURL = new URL(crawlingStart);
                    } catch (MalformedURLException e) {
                        crawlingStartURL = null;
                    }
                    
                    // check if pattern matches
                    if ((crawlingStartURL == null) || (!(crawlingStart.matches(newcrawlingfilter)))) {
                        // print error message
                        prop.put("error", 4); //crawlfilter does not match url
                        prop.put("error_newcrawlingfilter", newcrawlingfilter);
                        prop.put("error_crawlingStart", crawlingStart);
                    } else try {
                        // stack request
                        // first delete old entry, if exists
                        String urlhash = plasmaURL.urlHash(crawlingStart);
                        switchboard.loadedURL.remove(urlhash);
                        switchboard.noticeURL.remove(urlhash);
                        
                        // stack url
                        String reasonString = switchboard.stackCrawl(crawlingStart, null, yacyCore.seedDB.mySeed.hash, "CRAWLING-ROOT", new Date(), 0,
                        switchboard.profiles.newEntry(crawlingStartURL.getHost(), crawlingStart, newcrawlingfilter, newcrawlingfilter, newcrawlingdepth, newcrawlingdepth, crawlingQ, storeHTCache, true, localIndexing, crawlOrder, xsstopw, xdstopw, xpstopw));
                        
                        if (reasonString == null) {
                            // liftoff!
                            prop.put("info", 2);//start msg
                            prop.put("info_crawlingURL", ((String) post.get("crawlingURL")));
                        } else {
                            prop.put("error", 5); //Crawling failed
                            prop.put("error_crawlingURL", ((String) post.get("crawlingURL")));
                            prop.put("error_reasonString", reasonString);
                        }
                    } catch (Exception e) {
                        // mist
                        prop.put("error", 6);//Error with url
                        prop.put("error_crawlingStart", crawlingStart);
                        prop.put("error_error", e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            if (post.containsKey("clearRejected")) {
                switchboard.errorURL.clearStack();
            }
            if (post.containsKey("moreRejected")) {
                showRejectedCount = Integer.parseInt(post.get("showRejected", "10"));
            }
            if (post.containsKey("distributedcrawling")) {
                boolean crawlResponse = ((String) post.get("crawlResponse", "")).equals("on");
                env.setConfig("crawlResponse", (crawlResponse) ? "true" : "false");
            }
            if (post.containsKey("clearcrawlqueue")) {
                String urlHash;
                int c = 0;
                while (switchboard.noticeURL.localStackSize() > 0) {
                    urlHash = switchboard.noticeURL.localPop().hash();
                    if (urlHash != null) {
                        switchboard.noticeURL.remove(urlHash);
                        c++;
                    }
                }
                prop.put("info", 3);//crawling queue cleared
                prop.put("info_numEntries", c);
            }
        }
        
        // define visible variables
        prop.put("proxyPrefetchDepth", env.getConfig("proxyPrefetchDepth", "0"));
        prop.put("crawlingDepth", env.getConfig("crawlingDepth", "0"));
        prop.put("crawlingFilter", env.getConfig("crawlingFilter", "0"));
        prop.put("crawlingQChecked", env.getConfig("crawlingQ", "").equals("true") ? 1 : 0);
        prop.put("storeHTCacheChecked", env.getConfig("storeHTCache", "").equals("true") ? 1 : 0);
        prop.put("localIndexingChecked", env.getConfig("localIndexing", "").equals("true") ? 1 : 0);
        prop.put("crawlOrderChecked", env.getConfig("crawlOrder", "").equals("true") ? 1 : 0);
        prop.put("crawlResponseChecked", env.getConfig("crawlResponse", "").equals("true") ? 1 : 0);
        prop.put("xsstopwChecked", env.getConfig("xsstopw", "").equals("true") ? 1 : 0);
        prop.put("xdstopwChecked", env.getConfig("xdstopw", "").equals("true") ? 1 : 0);
        prop.put("xpstopwChecked", env.getConfig("xpstopw", "").equals("true") ? 1 : 0);
        
        int processStackSize = switchboard.processStack.size();
        int loaderThreadsSize = switchboard.cacheLoader.size();
        int crawlerListSize = switchboard.noticeURL.stackSize();
        int completequeue = processStackSize + loaderThreadsSize + crawlerListSize;
        
        if ((completequeue > 0) || ((post != null) && (post.containsKey("refreshpage")))) {
            prop.put("refreshbutton", 1);
        }
        
        // create prefetch table
        boolean dark;
        
        //  sed crawl profiles
        int count = 0;
        //try{
        Iterator it = switchboard.profiles.profiles(true);
        plasmaCrawlProfile.entry profile;
        dark = true;
        while (it.hasNext()) {
            profile = (plasmaCrawlProfile.entry) it.next();
            //table += profile.map().toString() + "<br>";
            prop.put("crawlProfiles_"+count+"_dark", ((dark) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_name", profile.name());
            prop.put("crawlProfiles_"+count+"_startURL", profile.startURL());
            prop.put("crawlProfiles_"+count+"_depth", profile.generalDepth());
            prop.put("crawlProfiles_"+count+"_filter", profile.generalFilter());
            prop.put("crawlProfiles_"+count+"_withQuery", ((profile.crawlingQ()) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_storeCache", ((profile.storeHTCache()) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_localIndexing", ((profile.localIndexing()) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_remoteIndexing", ((profile.remoteIndexing()) ? 1 : 0));
            
            dark = !dark;
            count++;
        }
        //}catch(IOException e){};
        prop.put("crawlProfiles", count);
        
        // remote crawl peers
        if (yacyCore.seedDB == null) {
            //table += "Sorry, cannot show any crawl output now because the system is not completely initialised. Please re-try.";
            prop.put("error", 3);
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
            
            // failure cases
            if (switchboard.errorURL.stackSize() != 0) {
                if (showRejectedCount > switchboard.errorURL.stackSize()) showRejectedCount = switchboard.errorURL.stackSize();
                prop.put("rejected", 1);
                prop.put("rejected_num", switchboard.errorURL.stackSize());
                if (showRejectedCount != switchboard.errorURL.stackSize()) {
                    prop.put("rejected_only-latest", 1);
                    prop.put("rejected_only-latest_num", showRejectedCount);
                    prop.put("rejected_only-latest_newnum", ((int) (showRejectedCount * 1.5)));
                }else{
                    prop.put("rejected_only-latest", 0);
                }
                dark = true;
                String url, initiatorHash, executorHash;
                plasmaCrawlEURL.entry entry;
                yacySeed initiatorSeed, executorSeed;
                int j=0;
                for (i = switchboard.errorURL.stackSize() - 1; i >= (switchboard.errorURL.stackSize() - showRejectedCount); i--) {
                    entry = (plasmaCrawlEURL.entry) switchboard.errorURL.getStack(i);
                    initiatorHash = entry.initiator();
                    executorHash = entry.executor();
                    url = entry.url().toString();
                    initiatorSeed = yacyCore.seedDB.getConnected(initiatorHash);
                    executorSeed = yacyCore.seedDB.getConnected(executorHash);
                    prop.put("rejected_list_"+j+"_initiator", ((initiatorSeed == null) ? "proxy" : initiatorSeed.getName()));
                    prop.put("rejected_list_"+j+"_executor", ((executorSeed == null) ? "proxy" : executorSeed.getName()));
                    prop.put("rejected_list_"+j+"_url", url);
                    prop.put("rejected_list_"+j+"_failreason", entry.failreason());
                    prop.put("rejected_list_"+j+"_dark", ((dark) ? 1 : 0));
                    dark = !dark;
                    j++;
                }
                prop.put("rejected_list", j);
            }
            
            // now about the current processes
            if (completequeue > 0) {
                
                yacySeed initiator;
                
                if (switchboard.processStack.size() == 0) {
                    prop.put("indexing-queue", 0); //is empty
                } else {
                    prop.put("indexing-queue", 1);
                    prop.put("indexing-queue_num", switchboard.processStack.size());//num entries in queue
                    dark = true;
                    plasmaHTCache.Entry pcentry;
                    for (i = 0; i < switchboard.processStack.size(); i++) {
                        pcentry = (plasmaHTCache.Entry) switchboard.processStack.get(i);
                        if (pcentry != null) {
                            initiator = yacyCore.seedDB.getConnected(pcentry.initiator());
                            prop.put("indexing-queue_list_"+i+"_dark", ((dark) ? 1 : 0));
                            prop.put("indexing-queue_list_"+i+"_initiator", ((initiator == null) ? "proxy" : initiator.getName()));
                            prop.put("indexing-queue_list_"+i+"_depth", pcentry.depth);
                            prop.put("indexing-queue_list_"+i+"_modified", daydate(pcentry.lastModified));
                            prop.put("indexing-queue_list_"+i+"_href",((pcentry.scraper == null) ? "0" : ("" + pcentry.scraper.getAnchors().size())));
                            prop.put("indexing-queue_list_"+i+"_anchor", ((pcentry.scraper == null) ? "-" : pcentry.scraper.getHeadline()) );
                            prop.put("indexing-queue_list_"+i+"_url", pcentry.nomalizedURLString);
                            dark = !dark;
                        }
                    }
                    prop.put("indexing-queue_list", i);
                }
                
                if (loaderThreadsSize == 0) {
                    prop.put("loader-set", 0);
                } else {
                    prop.put("loader-set", 1);
                    prop.put("loader-set_num", loaderThreadsSize);
                    dark = true;                    
                    //plasmaCrawlLoader.Exec[] loaderThreads = switchboard.cacheLoader.threadStatus();
//                  for (i = 0; i < loaderThreads.length; i++) {
//                  initiator = yacyCore.seedDB.getConnected(loaderThreads[i].initiator);
//                  prop.put("loader-set_list_"+i+"_dark", ((dark) ? 1 : 0) );
//                  prop.put("loader-set_list_"+i+"_initiator", ((initiator == null) ? "proxy" : initiator.getName()) );
//                  prop.put("loader-set_list_"+i+"_depth", loaderThreads[i].depth );
//                  prop.put("loader-set_list_"+i+"_url", loaderThreads[i].url ); // null pointer exception here !!! maybe url = null; check reason.
//                  dark = !dark;
//              }
//              prop.put("loader-set_list", i );                    
                    
                    ThreadGroup loaderThreads = switchboard.cacheLoader.threadStatus();
                    
                    int threadCount  = loaderThreads.activeCount();    
                    Thread[] threadList = new Thread[threadCount*2];     
                    threadCount = loaderThreads.enumerate(threadList);                    
                    
                    for (i = 0; i < threadCount; i++)  {                        
                        plasmaCrawlWorker theWorker = (plasmaCrawlWorker)threadList[i];
                        plasmaCrawlLoaderMessage theMsg = theWorker.theMsg;
                        if (theMsg == null) continue;
                        
                        initiator = yacyCore.seedDB.getConnected(theMsg.initiator);
                        prop.put("loader-set_list_"+i+"_dark", ((dark) ? 1 : 0) );
                        prop.put("loader-set_list_"+i+"_initiator", ((initiator == null) ? "proxy" : initiator.getName()) );
                        prop.put("loader-set_list_"+i+"_depth", theMsg.depth );
                        prop.put("loader-set_list_"+i+"_url", theMsg.url ); // null pointer exception here !!! maybe url = null; check reason.
                        dark = !dark;                        
                    }                    
                    prop.put("loader-set_list", i );
                }
                
                if (crawlerListSize == 0) {
                    prop.put("crawler-queue", 0);
                } else {
                    prop.put("crawler-queue", 1);
                    plasmaCrawlNURL.entry[] crawlerList = switchboard.noticeURL.localTop(20);
                    prop.put("crawler-queue_num", crawlerListSize);//num Entries
                    prop.put("crawler-queue_show-num", crawlerList.length); //showin sjow-num most recent
                    plasmaCrawlNURL.entry urle;
                    dark = true;
                    for (i = 0; i < crawlerList.length; i++) {
                        urle = crawlerList[i];
                        if (urle != null) {
                            initiator = yacyCore.seedDB.getConnected(urle.initiator());
                            prop.put("crawler-queue_list_"+i+"_dark", ((dark) ? 1 : 0) );
                            prop.put("crawler-queue_list_"+i+"_initiator", ((initiator == null) ? "proxy" : initiator.getName()) );
                            prop.put("crawler-queue_list_"+i+"_depth", urle.depth());
                            prop.put("crawler-queue_list_"+i+"_modified", daydate(urle.loaddate()) );
                            prop.put("crawler-queue_list_"+i+"_anchor", urle.name());
                            prop.put("crawler-queue_list_"+i+"_url", urle.url());
                            dark = !dark;
                        }
                    }
                    prop.put("crawler-queue_list", i);
                }
            }
        }
        // return rewrite properties
        return prop;
    }
    
}



