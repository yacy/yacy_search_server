// WatchCrawler_p.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 18.12.2006 on http://www.anomic.de
// this file was created using the an implementation from IndexCreate_p.java, published 02.12.2004
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

import java.io.File;
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterWriter;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaCrawlZURL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.dbImport.SitemapImporter;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacyURL;

public class WatchCrawler_p {
	public static final String CRAWLING_MODE_URL = "url";
	public static final String CRAWLING_MODE_FILE = "file";
	public static final String CRAWLING_MODE_SITEMAP = "sitemap";
	

    // this servlet does NOT create the WatchCrawler page content!
    // this servlet starts a web crawl. The interface for entering the web crawl parameters is in IndexCreate_p.html
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        prop.put("forwardToCrawlStart", "0");
        
        if (post == null) {
            // not a crawl start, only monitoring
            prop.put("info", "0");
        } else {
            prop.put("info", "0");
            
            if ((post.containsKey("autoforward")) &&
                (switchboard.crawlQueues.coreCrawlJobSize() == 0) &&
                (switchboard.crawlQueues.remoteTriggeredCrawlJobSize() == 0) &&
                (switchboard.queueSize() < 30)) {
                prop.put("forwardToCrawlStart", "1");
            }
            
            if (post.containsKey("continue")) {
                // continue queue
                String queue = post.get("continue", "");
                if (queue.equals("localcrawler")) {
                    switchboard.continueCrawlJob(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
                } else if (queue.equals("remotecrawler")) {
                    switchboard.continueCrawlJob(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
                }
            }

            if (post.containsKey("pause")) {
                // pause queue
                String queue = post.get("pause", "");
                if (queue.equals("localcrawler")) {
                    switchboard.pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
                } else if (queue.equals("remotecrawler")) {
                    switchboard.pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
                }
            }
            
            if (post.containsKey("crawlingstart")) {
                // init crawl
                if (yacyCore.seedDB == null) {
                    prop.put("info", "3");
                } else {
                    // set new properties
                    boolean fullDomain = post.get("range", "wide").equals("domain"); // special property in simple crawl start
                    
                    String newcrawlingfilter = post.get("crawlingFilter", ".*");
                    if (newcrawlingfilter.length() < 2) newcrawlingfilter = ".*"; // avoid that all urls are filtered out if bad value was submitted
                    env.setConfig("crawlingFilter", newcrawlingfilter);
                    if (fullDomain) try {
                        newcrawlingfilter = ".*" + (new yacyURL(post.get("crawlingURL",""), null)).getHost() + ".*";
                    } catch (MalformedURLException e) {}
                    
                    boolean crawlOrder = post.get("crawlOrder", "off").equals("on");
                    env.setConfig("crawlOrder", (crawlOrder) ? "true" : "false");
                    
                    int newcrawlingdepth = Integer.parseInt(post.get("crawlingDepth", "8"));
                    env.setConfig("crawlingDepth", Integer.toString(newcrawlingdepth));
                    if ((crawlOrder) && (newcrawlingdepth > 8)) newcrawlingdepth = 8;
                    
                    boolean crawlingIfOlderCheck = post.get("crawlingIfOlderCheck", "off").equals("on");
                    int crawlingIfOlderNumber = Integer.parseInt(post.get("crawlingIfOlderNumber", "-1"));
                    String crawlingIfOlderUnit = post.get("crawlingIfOlderUnit","year");
                    int crawlingIfOlder = recrawlIfOlderC(crawlingIfOlderCheck, crawlingIfOlderNumber, crawlingIfOlderUnit);                    
                    env.setConfig("crawlingIfOlder", crawlingIfOlder);
                    
                    boolean crawlingDomFilterCheck = post.get("crawlingDomFilterCheck", "off").equals("on");
                    int crawlingDomFilterDepth = (crawlingDomFilterCheck) ? Integer.parseInt(post.get("crawlingDomFilterDepth", "-1")) : -1;
                    env.setConfig("crawlingDomFilterDepth", Integer.toString(crawlingDomFilterDepth));
                    
                    boolean crawlingDomMaxCheck = post.get("crawlingDomMaxCheck", "off").equals("on");
                    int crawlingDomMaxPages = (crawlingDomMaxCheck) ? Integer.parseInt(post.get("crawlingDomMaxPages", "-1")) : -1;
                    env.setConfig("crawlingDomMaxPages", Integer.toString(crawlingDomMaxPages));
                    
                    boolean crawlingQ = post.get("crawlingQ", "off").equals("on");
                    env.setConfig("crawlingQ", (crawlingQ) ? "true" : "false");
                    
                    boolean indexText = post.get("indexText", "off").equals("on");
                    env.setConfig("indexText", (indexText) ? "true" : "false");
                    
                    boolean indexMedia = post.get("indexMedia", "off").equals("on");
                    env.setConfig("indexMedia", (indexMedia) ? "true" : "false");
                    
                    boolean storeHTCache = post.get("storeHTCache", "off").equals("on");
                    env.setConfig("storeHTCache", (storeHTCache) ? "true" : "false");
                    
                    boolean xsstopw = post.get("xsstopw", "off").equals("on");
                    env.setConfig("xsstopw", (xsstopw) ? "true" : "false");
                    
                    boolean xdstopw = post.get("xdstopw", "off").equals("on");
                    env.setConfig("xdstopw", (xdstopw) ? "true" : "false");
                    
                    boolean xpstopw = post.get("xpstopw", "off").equals("on");
                    env.setConfig("xpstopw", (xpstopw) ? "true" : "false");
                    
                    String crawlingMode = post.get("crawlingMode","url");
                    if (crawlingMode.equals(CRAWLING_MODE_URL)) {
                        // getting the crawljob start url
                        String crawlingStart = post.get("crawlingURL","");
                        crawlingStart = crawlingStart.trim();
                        
                        // adding the prefix http:// if necessary
                        int pos = crawlingStart.indexOf("://");
                        if (pos == -1) crawlingStart = "http://" + crawlingStart;

                        // normalizing URL
                        yacyURL crawlingStartURL = null;
                        try {crawlingStartURL = new yacyURL(crawlingStart, null);} catch (MalformedURLException e1) {}
                        crawlingStart = (crawlingStartURL == null) ? null : crawlingStartURL.toNormalform(true, true);
                        
                        // check if pattern matches
                        if ((crawlingStart == null) /* || (!(crawlingStart.matches(newcrawlingfilter))) */) {
                            // print error message
                            prop.put("info", "4"); //crawlfilter does not match url
                            prop.putHTML("info_newcrawlingfilter", newcrawlingfilter);
                            prop.putHTML("info_crawlingStart", crawlingStart);
                        } else try {
                            
                            // check if the crawl filter works correctly
                            Pattern.compile(newcrawlingfilter);
                            
                            // stack request
                            // first delete old entry, if exists
                            yacyURL url = new yacyURL(crawlingStart, null);
                            String urlhash = url.hash();
                            switchboard.wordIndex.loadedURL.remove(urlhash);
                            switchboard.crawlQueues.noticeURL.removeByURLHash(urlhash);
                            switchboard.crawlQueues.errorURL.remove(urlhash);
                            
                            // stack url
                            switchboard.profilesPassiveCrawls.removeEntry(crawlingStartURL.hash()); // if there is an old entry, delete it
                            plasmaCrawlProfile.entry pe = switchboard.profilesActiveCrawls.newEntry(
                                    crawlingStartURL.getHost(), crawlingStartURL, newcrawlingfilter, newcrawlingfilter,
                                    newcrawlingdepth, newcrawlingdepth,
                                    crawlingIfOlder, crawlingDomFilterDepth, crawlingDomMaxPages,
                                    crawlingQ,
                                    indexText, indexMedia,
                                    storeHTCache, true, crawlOrder, xsstopw, xdstopw, xpstopw);
                            String reasonString = switchboard.crawlStacker.stackCrawl(url, null, yacyCore.seedDB.mySeed().hash, "CRAWLING-ROOT", new Date(), 0, pe);
                            
                            if (reasonString == null) {
                                // liftoff!
                                prop.put("info", "8");//start msg
                                prop.putHTML("info_crawlingURL", ((String) post.get("crawlingURL")));
                                
                                // generate a YaCyNews if the global flag was set
                                if (crawlOrder) {
                                    Map<String, String> m = new HashMap<String, String>(pe.map()); // must be cloned
                                    m.remove("specificDepth");
                                    m.remove("indexText");
                                    m.remove("indexMedia");
                                    m.remove("remoteIndexing");
                                    m.remove("xsstopw");
                                    m.remove("xpstopw");
                                    m.remove("xdstopw");
                                    m.remove("storeTXCache");
                                    m.remove("storeHTCache");
                                    m.remove("generalFilter");
                                    m.remove("specificFilter");
                                    m.put("intention", post.get("intention", "").replace(',', '/'));
                                    yacyCore.newsPool.publishMyNews(yacyNewsRecord.newRecord(yacyNewsPool.CATEGORY_CRAWL_START, m));
                                }
                                
                            } else {
                                prop.put("info", "5"); //Crawling failed
                                prop.putHTML("info_crawlingURL", ((String) post.get("crawlingURL")));
                                prop.putHTML("info_reasonString", reasonString);
                                
                                plasmaCrawlZURL.Entry ee = switchboard.crawlQueues.errorURL.newEntry(crawlingStartURL, reasonString);
                                ee.store();
                                switchboard.crawlQueues.errorURL.push(ee);
                            }
                        } catch (PatternSyntaxException e) {
                            prop.put("info", "4"); //crawlfilter does not match url
                            prop.putHTML("info_newcrawlingfilter", newcrawlingfilter);
                            prop.putHTML("info_error", e.getMessage());
                        } catch (Exception e) {
                            // mist
                            prop.put("info", "6");//Error with url
                            prop.putHTML("info_crawlingStart", crawlingStart);
                            prop.putHTML("info_error", e.getMessage());
                            e.printStackTrace();
                        }
                        
                    } else if (crawlingMode.equals(CRAWLING_MODE_FILE)) {
                        if (post.containsKey("crawlingFile")) {
                            // getting the name of the uploaded file
                            String fileName = (String) post.get("crawlingFile");  
                            try {
                                // check if the crawl filter works correctly
                                Pattern.compile(newcrawlingfilter);
                                
                                // loading the file content
                                File file = new File(fileName);
                                
                                // getting the content of the bookmark file
                                String fileString = post.get("crawlingFile$file");
                                
                                // parsing the bookmark file and fetching the headline and contained links
                                htmlFilterContentScraper scraper = new htmlFilterContentScraper(new yacyURL(file));
                                //OutputStream os = new htmlFilterOutputStream(null, scraper, null, false);
                                Writer writer = new htmlFilterWriter(null,null,scraper,null,false);
                                serverFileUtils.copy(fileString, writer);
                                writer.close();
                                
                                //String headline = scraper.getHeadline();
                                Map<yacyURL, String> hyperlinks = scraper.getAnchors();
                                
                                // creating a crawler profile
                                yacyURL crawlURL = new yacyURL("file://" + file.toString(), null);
                                plasmaCrawlProfile.entry profile = switchboard.profilesActiveCrawls.newEntry(fileName, crawlURL, newcrawlingfilter, newcrawlingfilter, newcrawlingdepth, newcrawlingdepth, crawlingIfOlder, crawlingDomFilterDepth, crawlingDomMaxPages, crawlingQ, indexText, indexMedia, storeHTCache, true, crawlOrder, xsstopw, xdstopw, xpstopw);
                                
                                // pause local crawl here
                                switchboard.pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
                                
                                // loop through the contained links
                                Iterator<Map.Entry<yacyURL, String>> linkiterator = hyperlinks.entrySet().iterator();
                                yacyURL nexturl;
                                while (linkiterator.hasNext()) {
                                    Map.Entry<yacyURL, String> e = linkiterator.next();
                                    nexturl = e.getKey();
                                    if (nexturl == null) continue;
                                    
                                    // enqueuing the url for crawling
                                    switchboard.crawlStacker.enqueueEntry(
                                            nexturl, 
                                            null, 
                                            yacyCore.seedDB.mySeed().hash, 
                                            (String) e.getValue(), 
                                            new Date(), 
                                            0, 
                                            profile);
                                }
                               
                            } catch (PatternSyntaxException e) {
                                // print error message
                                prop.put("info", "4"); //crawlfilter does not match url
                                prop.putHTML("info_newcrawlingfilter", newcrawlingfilter);
                                prop.putHTML("info_error", e.getMessage());
                            } catch (Exception e) {
                                // mist
                                prop.put("info", "7");//Error with file
                                prop.putHTML("info_crawlingStart", fileName);
                                prop.putHTML("info_error", e.getMessage());
                                e.printStackTrace();
                            }
                            switchboard.continueCrawlJob(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
                        }
                    } else if (crawlingMode.equals(CRAWLING_MODE_SITEMAP)) { 
                    	String sitemapURLStr = null;
                    	try {
                    		// getting the sitemap URL
                    		sitemapURLStr = post.get("sitemapURL","");
                    		yacyURL sitemapURL = new yacyURL(sitemapURLStr, null);
                            
                    		// create a new profile
                    		plasmaCrawlProfile.entry pe = switchboard.profilesActiveCrawls.newEntry(
                    				sitemapURLStr, sitemapURL, newcrawlingfilter, newcrawlingfilter,
                    				newcrawlingdepth, newcrawlingdepth,
                    				crawlingIfOlder, crawlingDomFilterDepth, crawlingDomMaxPages,
                    				crawlingQ,
                    				indexText, indexMedia,
                    				storeHTCache, true, crawlOrder, xsstopw, xdstopw, xpstopw);
                    		
                    		// create a new sitemap importer
                    		SitemapImporter importerThread = (SitemapImporter) switchboard.dbImportManager.getNewImporter("sitemap");
                    		if (importerThread != null) {
                    			importerThread.init(switchboard, 0);
                    			importerThread.initSitemap(new yacyURL(sitemapURLStr, null), pe);
                    			importerThread.startIt();
                    		}
                    	} catch (Exception e) {
                    		// mist
                    		prop.put("info", "6");//Error with url
                    		prop.putHTML("info_crawlingStart", sitemapURLStr);
                    		prop.putHTML("info_error", e.getMessage());
                    		e.printStackTrace();
                    	}
                    }
                }
            }
            
            if (post.containsKey("crawlingPerformance")) {
                setPerformance(switchboard, post);
            }
        }
        
        // performance settings
        long LCbusySleep = Integer.parseInt(env.getConfig(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, "100"));
        int LCppm = (int) (60000L / Math.max(1,LCbusySleep));
        prop.put("crawlingSpeedMaxChecked", (LCppm >= 1000) ? "1" : "0");
        prop.put("crawlingSpeedCustChecked", ((LCppm > 10) && (LCppm < 1000)) ? "1" : "0");
        prop.put("crawlingSpeedMinChecked", (LCppm <= 10) ? "1" : "0");
        prop.put("customPPMdefault", ((LCppm > 10) && (LCppm < 1000)) ? Integer.toString(LCppm) : "");
        
        // return rewrite properties
        return prop;
    }

    private static int recrawlIfOlderC(boolean recrawlIfOlderCheck, int recrawlIfOlderNumber, String crawlingIfOlderUnit) {
        if (!recrawlIfOlderCheck) return -1;
        if (crawlingIfOlderUnit.equals("year")) return recrawlIfOlderNumber * 60 * 24 * 365;
        if (crawlingIfOlderUnit.equals("month")) return recrawlIfOlderNumber * 60 * 24 * 30;
        if (crawlingIfOlderUnit.equals("day")) return recrawlIfOlderNumber * 60 * 24;
        if (crawlingIfOlderUnit.equals("hour")) return recrawlIfOlderNumber * 60;
        if (crawlingIfOlderUnit.equals("minute")) return recrawlIfOlderNumber;
        return -1;
    }
    
    private static void setPerformance(plasmaSwitchboard sb, serverObjects post) {
        String crawlingPerformance = post.get("crawlingPerformance","custom");
        long LCbusySleep = Integer.parseInt(sb.getConfig(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, "100"));
        int wantedPPM = (int) (60000L / LCbusySleep);
        try {
            wantedPPM = Integer.parseInt(post.get("customPPM",Integer.toString(wantedPPM)));
        } catch (NumberFormatException e) {}
        if (crawlingPerformance.equals("minimum")) wantedPPM = 10;
        if (crawlingPerformance.equals("maximum")) wantedPPM = 1000;
        sb.setPerformance(wantedPPM);
    }
    
}
