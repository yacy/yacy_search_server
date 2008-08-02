// WatchCrawler_p.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
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

import xml.queues_p;
import de.anomic.crawler.CrawlEntry;
import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.SitemapImporter;
import de.anomic.crawler.ZURL;
import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterWriter;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSwitchboardConstants;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacyURL;

public class WatchCrawler_p {
	public static final String CRAWLING_MODE_URL = "url";
	public static final String CRAWLING_MODE_FILE = "file";
	public static final String CRAWLING_MODE_SITEMAP = "sitemap";
	

    // this servlet does NOT create the WatchCrawler page content!
    // this servlet starts a web crawl. The interface for entering the web crawl parameters is in IndexCreate_p.html
    
    public static serverObjects respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        // inital values for AJAX Elements (without JavaScript) 
        final serverObjects prop = queues_p.respond(header, post, env);
        prop.put("forwardToCrawlStart", "0");
        
        prop.put("info", "0");
        if (post != null) {
            // a crawl start
            
            if ((post.containsKey("autoforward")) &&
                (sb.crawlQueues.coreCrawlJobSize() == 0) &&
                (sb.crawlQueues.remoteTriggeredCrawlJobSize() == 0) &&
                (sb.queueSize() < 30)) {
                prop.put("forwardToCrawlStart", "1");
            }
            
            if (post.containsKey("continue")) {
                // continue queue
                final String queue = post.get("continue", "");
                if (queue.equals("localcrawler")) {
                    sb.continueCrawlJob(plasmaSwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                } else if (queue.equals("remotecrawler")) {
                    sb.continueCrawlJob(plasmaSwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
                }
            }

            if (post.containsKey("pause")) {
                // pause queue
                final String queue = post.get("pause", "");
                if (queue.equals("localcrawler")) {
                    sb.pauseCrawlJob(plasmaSwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                } else if (queue.equals("remotecrawler")) {
                    sb.pauseCrawlJob(plasmaSwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
                }
            }
            
            if (post.containsKey("crawlingstart")) {
                // init crawl
                if (sb.webIndex.seedDB == null) {
                    prop.put("info", "3");
                } else {
                    // set new properties
                    final boolean fullDomain = post.get("range", "wide").equals("domain"); // special property in simple crawl start
                    final boolean subPath    = post.get("range", "wide").equals("subpath"); // special property in simple crawl start
                    
                    String crawlingStart = post.get("crawlingURL","").trim(); // the crawljob start url

                    // adding the prefix http:// if necessary
                    int pos = crawlingStart.indexOf("://");
                    if (pos == -1) crawlingStart = "http://" + crawlingStart;

                    // normalizing URL
                    yacyURL crawlingStartURL = null;
                    try {crawlingStartURL = new yacyURL(crawlingStart, null);} catch (final MalformedURLException e1) {}
                    crawlingStart = (crawlingStartURL == null) ? null : crawlingStartURL.toNormalform(true, true);
                    
                    // set the crawling filter
                    String newcrawlingfilter = post.get("crawlingFilter", ".*");
                    if (newcrawlingfilter.length() < 2) newcrawlingfilter = ".*"; // avoid that all urls are filtered out if bad value was submitted
                    
                    if (crawlingStartURL!= null && fullDomain) {
                        newcrawlingfilter = ".*" + crawlingStartURL.getHost() + ".*";
                    }
                    if (crawlingStart!= null && subPath && (pos = crawlingStart.lastIndexOf("/")) > 0) {
                        newcrawlingfilter = crawlingStart.substring(0, pos + 1) + ".*";
                    }
                    env.setConfig("crawlingFilter", newcrawlingfilter);
                    
                    final boolean crawlOrder = post.get("crawlOrder", "off").equals("on");
                    env.setConfig("crawlOrder", (crawlOrder) ? "true" : "false");
                    
                    int newcrawlingdepth = Integer.parseInt(post.get("crawlingDepth", "8"));
                    env.setConfig("crawlingDepth", Integer.toString(newcrawlingdepth));
                    if ((crawlOrder) && (newcrawlingdepth > 8)) newcrawlingdepth = 8;
                    
                    final boolean crawlingIfOlderCheck = post.get("crawlingIfOlderCheck", "off").equals("on");
                    final int crawlingIfOlderNumber = Integer.parseInt(post.get("crawlingIfOlderNumber", "-1"));
                    final String crawlingIfOlderUnit = post.get("crawlingIfOlderUnit","year");
                    final int crawlingIfOlder = recrawlIfOlderC(crawlingIfOlderCheck, crawlingIfOlderNumber, crawlingIfOlderUnit);                    
                    env.setConfig("crawlingIfOlder", crawlingIfOlder);
                    
                    final boolean crawlingDomFilterCheck = post.get("crawlingDomFilterCheck", "off").equals("on");
                    final int crawlingDomFilterDepth = (crawlingDomFilterCheck) ? Integer.parseInt(post.get("crawlingDomFilterDepth", "-1")) : -1;
                    env.setConfig("crawlingDomFilterDepth", Integer.toString(crawlingDomFilterDepth));
                    
                    final boolean crawlingDomMaxCheck = post.get("crawlingDomMaxCheck", "off").equals("on");
                    final int crawlingDomMaxPages = (crawlingDomMaxCheck) ? Integer.parseInt(post.get("crawlingDomMaxPages", "-1")) : -1;
                    env.setConfig("crawlingDomMaxPages", Integer.toString(crawlingDomMaxPages));
                    
                    final boolean crawlingQ = post.get("crawlingQ", "off").equals("on");
                    env.setConfig("crawlingQ", (crawlingQ) ? "true" : "false");
                    
                    final boolean indexText = post.get("indexText", "off").equals("on");
                    env.setConfig("indexText", (indexText) ? "true" : "false");
                    
                    final boolean indexMedia = post.get("indexMedia", "off").equals("on");
                    env.setConfig("indexMedia", (indexMedia) ? "true" : "false");
                    
                    final boolean storeHTCache = post.get("storeHTCache", "off").equals("on");
                    env.setConfig("storeHTCache", (storeHTCache) ? "true" : "false");
                    
                    final boolean xsstopw = post.get("xsstopw", "off").equals("on");
                    env.setConfig("xsstopw", (xsstopw) ? "true" : "false");
                    
                    final boolean xdstopw = post.get("xdstopw", "off").equals("on");
                    env.setConfig("xdstopw", (xdstopw) ? "true" : "false");
                    
                    final boolean xpstopw = post.get("xpstopw", "off").equals("on");
                    env.setConfig("xpstopw", (xpstopw) ? "true" : "false");
                    
                    final String crawlingMode = post.get("crawlingMode","url");
                    if (crawlingMode.equals(CRAWLING_MODE_URL)) {
                        
                        // check if pattern matches
                        if ((crawlingStart == null || crawlingStartURL == null) /* || (!(crawlingStart.matches(newcrawlingfilter))) */) {
                            // print error message
                            prop.put("info", "4"); //crawlfilter does not match url
                            prop.putHTML("info_newcrawlingfilter", newcrawlingfilter);
                            prop.putHTML("info_crawlingStart", crawlingStart);
                        } else try {
                            
                            // check if the crawl filter works correctly
                            Pattern.compile(newcrawlingfilter);
                            
                            // stack request
                            // first delete old entry, if exists
                            final yacyURL url = new yacyURL(crawlingStart, null);
                            final String urlhash = url.hash();
                            sb.webIndex.removeURL(urlhash);
                            sb.crawlQueues.noticeURL.removeByURLHash(urlhash);
                            sb.crawlQueues.errorURL.remove(urlhash);
                            
                            // stack url
                            sb.webIndex.profilesPassiveCrawls.removeEntry(crawlingStartURL.hash()); // if there is an old entry, delete it
                            final CrawlProfile.entry pe = sb.webIndex.profilesActiveCrawls.newEntry(
                                    crawlingStartURL.getHost(), crawlingStartURL, newcrawlingfilter, newcrawlingfilter,
                                    newcrawlingdepth, newcrawlingdepth,
                                    crawlingIfOlder, crawlingDomFilterDepth, crawlingDomMaxPages,
                                    crawlingQ,
                                    indexText, indexMedia,
                                    storeHTCache, true, crawlOrder, xsstopw, xdstopw, xpstopw);
                            final String reasonString = sb.crawlStacker.stackCrawl(url, null, sb.webIndex.seedDB.mySeed().hash, "CRAWLING-ROOT", new Date(), 0, pe);
                            
                            if (reasonString == null) {
                                // liftoff!
                                prop.put("info", "8");//start msg
                                prop.putHTML("info_crawlingURL", (post.get("crawlingURL")));
                                
                                // generate a YaCyNews if the global flag was set
                                if (crawlOrder) {
                                    final Map<String, String> m = new HashMap<String, String>(pe.map()); // must be cloned
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
                                    sb.webIndex.newsPool.publishMyNews(yacyNewsRecord.newRecord(sb.webIndex.seedDB.mySeed(), yacyNewsPool.CATEGORY_CRAWL_START, m));
                                }
                                
                            } else {
                                prop.put("info", "5"); //Crawling failed
                                prop.putHTML("info_crawlingURL", (post.get("crawlingURL")));
                                prop.putHTML("info_reasonString", reasonString);
                                
                                final ZURL.Entry ee = sb.crawlQueues.errorURL.newEntry(
                                        new CrawlEntry(
                                                sb.webIndex.seedDB.mySeed().hash, 
                                                crawlingStartURL, 
                                                "", 
                                                "", 
                                                new Date(),
                                                pe.handle(),
                                                0, 
                                                0, 
                                                0),
                                        sb.webIndex.seedDB.mySeed().hash,
                                        new Date(),
                                        1,
                                        reasonString);
                                
                                ee.store();
                                sb.crawlQueues.errorURL.push(ee);
                            }
                        } catch (final PatternSyntaxException e) {
                            prop.put("info", "4"); //crawlfilter does not match url
                            prop.putHTML("info_newcrawlingfilter", newcrawlingfilter);
                            prop.putHTML("info_error", e.getMessage());
                        } catch (final Exception e) {
                            // mist
                            prop.put("info", "6");//Error with url
                            prop.putHTML("info_crawlingStart", crawlingStart);
                            prop.putHTML("info_error", e.getMessage());
                            e.printStackTrace();
                        }
                        
                    } else if (crawlingMode.equals(CRAWLING_MODE_FILE)) {
                        if (post.containsKey("crawlingFile")) {
                            // getting the name of the uploaded file
                            final String fileName = post.get("crawlingFile");  
                            try {
                                // check if the crawl filter works correctly
                                Pattern.compile(newcrawlingfilter);
                                
                                // loading the file content
                                final File file = new File(fileName);
                                
                                // getting the content of the bookmark file
                                final String fileString = post.get("crawlingFile$file");
                                
                                // parsing the bookmark file and fetching the headline and contained links
                                final htmlFilterContentScraper scraper = new htmlFilterContentScraper(new yacyURL(file));
                                //OutputStream os = new htmlFilterOutputStream(null, scraper, null, false);
                                final Writer writer = new htmlFilterWriter(null,null,scraper,null,false);
                                serverFileUtils.copy(fileString, writer);
                                writer.close();
                                
                                //String headline = scraper.getHeadline();
                                final Map<yacyURL, String> hyperlinks = scraper.getAnchors();
                                
                                // creating a crawler profile
                                final yacyURL crawlURL = new yacyURL("file://" + file.toString(), null);
                                final CrawlProfile.entry profile = sb.webIndex.profilesActiveCrawls.newEntry(fileName, crawlURL, newcrawlingfilter, newcrawlingfilter, newcrawlingdepth, newcrawlingdepth, crawlingIfOlder, crawlingDomFilterDepth, crawlingDomMaxPages, crawlingQ, indexText, indexMedia, storeHTCache, true, crawlOrder, xsstopw, xdstopw, xpstopw);
                                
                                // pause local crawl here
                                sb.pauseCrawlJob(plasmaSwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                                
                                // loop through the contained links
                                final Iterator<Map.Entry<yacyURL, String>> linkiterator = hyperlinks.entrySet().iterator();
                                yacyURL nexturl;
                                while (linkiterator.hasNext()) {
                                    final Map.Entry<yacyURL, String> e = linkiterator.next();
                                    nexturl = e.getKey();
                                    if (nexturl == null) continue;
                                    
                                    // enqueuing the url for crawling
                                    sb.crawlStacker.enqueueEntry(
                                            nexturl, 
                                            "", 
                                            sb.webIndex.seedDB.mySeed().hash, 
                                            e.getValue(), 
                                            new Date(), 
                                            0, 
                                            profile);
                                }
                               
                            } catch (final PatternSyntaxException e) {
                                // print error message
                                prop.put("info", "4"); //crawlfilter does not match url
                                prop.putHTML("info_newcrawlingfilter", newcrawlingfilter);
                                prop.putHTML("info_error", e.getMessage());
                            } catch (final Exception e) {
                                // mist
                                prop.put("info", "7");//Error with file
                                prop.putHTML("info_crawlingStart", fileName);
                                prop.putHTML("info_error", e.getMessage());
                                e.printStackTrace();
                            }
                            sb.continueCrawlJob(plasmaSwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                        }
                    } else if (crawlingMode.equals(CRAWLING_MODE_SITEMAP)) { 
                    	String sitemapURLStr = null;
                    	try {
                    		// getting the sitemap URL
                    		sitemapURLStr = post.get("sitemapURL","");
                    		final yacyURL sitemapURL = new yacyURL(sitemapURLStr, null);
                            
                    		// create a new profile
                    		final CrawlProfile.entry pe = sb.webIndex.profilesActiveCrawls.newEntry(
                    				sitemapURLStr, sitemapURL, newcrawlingfilter, newcrawlingfilter,
                    				newcrawlingdepth, newcrawlingdepth,
                    				crawlingIfOlder, crawlingDomFilterDepth, crawlingDomMaxPages,
                    				crawlingQ,
                    				indexText, indexMedia,
                    				storeHTCache, true, crawlOrder, xsstopw, xdstopw, xpstopw);
                    		
                    		// create a new sitemap importer
                    		final SitemapImporter importerThread = new SitemapImporter(sb, sb.dbImportManager, new yacyURL(sitemapURLStr, null), pe);
                    		if (importerThread != null) {
                    		    importerThread.setJobID(sb.dbImportManager.generateUniqueJobID());
                    			importerThread.startIt();
                    		}
                    	} catch (final Exception e) {
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
                setPerformance(sb, post);
            }
        }
        
        // performance settings
        final long LCbusySleep = Integer.parseInt(env.getConfig(plasmaSwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, "100"));
        final int LCppm = (int) (60000L / Math.max(1,LCbusySleep));
        prop.put("crawlingSpeedMaxChecked", (LCppm >= 1000) ? "1" : "0");
        prop.put("crawlingSpeedCustChecked", ((LCppm > 10) && (LCppm < 1000)) ? "1" : "0");
        prop.put("crawlingSpeedMinChecked", (LCppm <= 10) ? "1" : "0");
        prop.put("customPPMdefault", ((LCppm > 10) && (LCppm < 1000)) ? Integer.toString(LCppm) : "");
        
        // return rewrite properties
        return prop;
    }

    private static int recrawlIfOlderC(final boolean recrawlIfOlderCheck, final int recrawlIfOlderNumber, final String crawlingIfOlderUnit) {
        if (!recrawlIfOlderCheck) return -1;
        if (crawlingIfOlderUnit.equals("year")) return recrawlIfOlderNumber * 60 * 24 * 365;
        if (crawlingIfOlderUnit.equals("month")) return recrawlIfOlderNumber * 60 * 24 * 30;
        if (crawlingIfOlderUnit.equals("day")) return recrawlIfOlderNumber * 60 * 24;
        if (crawlingIfOlderUnit.equals("hour")) return recrawlIfOlderNumber * 60;
        return recrawlIfOlderNumber;
    }
    
    private static void setPerformance(final plasmaSwitchboard sb, final serverObjects post) {
        final String crawlingPerformance = post.get("crawlingPerformance","custom");
        final long LCbusySleep = Integer.parseInt(sb.getConfig(plasmaSwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, "100"));
        int wantedPPM = (LCbusySleep == 0) ? 6000 : (int) (60000L / LCbusySleep);
        try {
            wantedPPM = Integer.parseInt(post.get("customPPM", Integer.toString(wantedPPM)));
        } catch (final NumberFormatException e) {}
        if (crawlingPerformance.equals("minimum")) wantedPPM = 10;
        if (crawlingPerformance.equals("maximum")) wantedPPM = 6000;
        sb.setPerformance(wantedPPM);
    }
    
}
