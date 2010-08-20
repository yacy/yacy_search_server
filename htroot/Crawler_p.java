// Crawler_p.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 18.12.2006 on http://www.anomic.de
// this file was created using the an implementation from IndexCreate_p.java, published 02.12.2004
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

import java.io.File;
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.TransformerWriter;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.SitemapImporter;
import de.anomic.crawler.retrieval.Request;
import de.anomic.data.BookmarkHelper;
import de.anomic.data.WorkTables;
import de.anomic.data.bookmarksDB;
import de.anomic.data.listManager;
import de.anomic.http.server.RequestHeader;
import de.anomic.search.Segment;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyNewsPool;

public class Crawler_p {
	public static final String CRAWLING_MODE_URL = "url";
	public static final String CRAWLING_MODE_FILE = "file";
	public static final String CRAWLING_MODE_SITEMAP = "sitemap";
	

    // this servlet does NOT create the Crawler servlet page content!
    // this servlet starts a web crawl. The interface for entering the web crawl parameters is in IndexCreate_p.html
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        // inital values for AJAX Elements (without JavaScript) 
        final serverObjects prop = new serverObjects();
        prop.put("rejected", 0);
        prop.put("urlpublictextSize", 0);
        prop.put("rwipublictextSize", 0);
        prop.put("list", "0");
        prop.put("loaderSize", 0);        
        prop.put("loaderMax", 0);
        prop.put("list-loader", 0);
        prop.put("localCrawlSize", 0);
        prop.put("localCrawlState", "");
        prop.put("limitCrawlSize", 0);
        prop.put("limitCrawlState", "");
        prop.put("remoteCrawlSize", 0);
        prop.put("remoteCrawlState", "");
        prop.put("list-remote", 0);
        prop.put("forwardToCrawlStart", "0");
        
        // get segment
        Segment indexSegment = null;
        if (post != null && post.containsKey("segment")) {
            String segmentName = post.get("segment");
            if (sb.indexSegments.segmentExist(segmentName)) {
                indexSegment = sb.indexSegments.segment(segmentName);
            }
        } else {
            // take default segment
            indexSegment = sb.indexSegments.segment(Segments.Process.PUBLIC);
        }
        
        prop.put("info", "0");
        if (post != null) {
            // a crawl start
            
            if (post.containsKey("continue")) {
                // continue queue
                final String queue = post.get("continue", "");
                if (queue.equals("localcrawler")) {
                    sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                } else if (queue.equals("remotecrawler")) {
                    sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
                }
            }

            if (post.containsKey("pause")) {
                // pause queue
                final String queue = post.get("pause", "");
                if (queue.equals("localcrawler")) {
                    sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                } else if (queue.equals("remotecrawler")) {
                    sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
                }
            }
            
            if (post.containsKey("crawlingstart")) {
                // init crawl
                if (sb.peers == null) {
                    prop.put("info", "3");
                } else {
                    String crawlingStart = post.get("crawlingURL","").trim(); // the crawljob start url
                    // add the prefix http:// if necessary
                    int pos = crawlingStart.indexOf("://");
                    if (pos == -1) crawlingStart = "http://" + crawlingStart;

                    // normalizing URL
                    DigestURI crawlingStartURL = null;
                    try {crawlingStartURL = new DigestURI(crawlingStart, null);} catch (final MalformedURLException e1) {}
                    crawlingStart = (crawlingStartURL == null) ? null : crawlingStartURL.toNormalform(true, true);
                   
                    // set new properties
                    final boolean fullDomain = post.get("range", "wide").equals("domain"); // special property in simple crawl start
                    final boolean subPath    = post.get("range", "wide").equals("subpath"); // special property in simple crawl start
                    
                    
                    // set the crawling filter
                    String newcrawlingMustMatch = post.get("mustmatch", CrawlProfile.MATCH_ALL);
                    String newcrawlingMustNotMatch = post.get("mustnotmatch", CrawlProfile.MATCH_NEVER);
                    if (newcrawlingMustMatch.length() < 2) newcrawlingMustMatch = CrawlProfile.MATCH_ALL; // avoid that all urls are filtered out if bad value was submitted
                    // special cases:
                    if (crawlingStartURL!= null && fullDomain) {
                        newcrawlingMustMatch = ".*" + crawlingStartURL.getHost() + ".*";
                    }
                    if (crawlingStart!= null && subPath && (pos = crawlingStart.lastIndexOf('/')) > 0) {
                        newcrawlingMustMatch = crawlingStart.substring(0, pos + 1) + ".*";
                    }
                    
                    final boolean crawlOrder = post.get("crawlOrder", "off").equals("on");
                    env.setConfig("crawlOrder", (crawlOrder) ? "true" : "false");
                    
                    int newcrawlingdepth = Integer.parseInt(post.get("crawlingDepth", "8"));
                    env.setConfig("crawlingDepth", Integer.toString(newcrawlingdepth));
                    if ((crawlOrder) && (newcrawlingdepth > 8)) newcrawlingdepth = 8;
                    
                    // recrawl
                    final String recrawl = post.get("recrawl", "nodoubles"); // nodoubles, reload, scheduler
                    boolean crawlingIfOlderCheck = post.get("crawlingIfOlderCheck", "off").equals("on");
                    int crawlingIfOlderNumber = Integer.parseInt(post.get("crawlingIfOlderNumber", "-1"));
                    String crawlingIfOlderUnit = post.get("crawlingIfOlderUnit","year"); // year, month, day, hour
                    int repeat_time = Integer.parseInt(post.get("repeat_time", "-1"));
                    final String repeat_unit = post.get("repeat_unit", "seldays"); // selminutes, selhours, seldays
                    
                    if (recrawl.equals("scheduler") && repeat_time > 0) {
                        // set crawlingIfOlder attributes that are appropriate for scheduled crawling 
                        crawlingIfOlderCheck = true;
                        crawlingIfOlderNumber = repeat_unit.equals("selminutes") ? 1 : repeat_unit.equals("selhours") ? repeat_time / 2 : repeat_time * 12;
                        crawlingIfOlderUnit = "hour";
                    } else if (recrawl.equals("reload")) {
                        repeat_time = -1;
                        crawlingIfOlderCheck = true;
                    } else if (recrawl.equals("nodoubles")) {
                        repeat_time = -1;
                        crawlingIfOlderCheck = false;
                    }
                    long crawlingIfOlder = recrawlIfOlderC(crawlingIfOlderCheck, crawlingIfOlderNumber, crawlingIfOlderUnit);
                    env.setConfig("crawlingIfOlder", crawlingIfOlder);

                    // store this call as api call
                    if (repeat_time > 0) {
                        // store as scheduled api call
                        sb.tables.recordAPICall(post, "Crawler_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "crawl start for " + crawlingStart, repeat_time, repeat_unit.substring(3));
                    } else {
                        // store just a protocol
                        sb.tables.recordAPICall(post, "Crawler_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "crawl start for " + crawlingStart);
                    }                    
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
                    
                    final String cachePolicyString = post.get("cachePolicy", "iffresh");
                    CrawlProfile.CacheStrategy cachePolicy = CrawlProfile.CacheStrategy.IFFRESH;
                    if (cachePolicyString.equals("nocache")) cachePolicy = CrawlProfile.CacheStrategy.NOCACHE;
                    if (cachePolicyString.equals("iffresh")) cachePolicy = CrawlProfile.CacheStrategy.IFFRESH;
                    if (cachePolicyString.equals("ifexist")) cachePolicy = CrawlProfile.CacheStrategy.IFEXIST;
                    if (cachePolicyString.equals("cacheonly")) cachePolicy = CrawlProfile.CacheStrategy.CACHEONLY;
                    
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
                            prop.putHTML("info_newcrawlingfilter", newcrawlingMustMatch);
                            prop.putHTML("info_crawlingStart", crawlingStart);
                        } else try {
                            
                            // check if the crawl filter works correctly
                            Pattern.compile(newcrawlingMustMatch);
                            
                            // stack request
                            // first delete old entry, if exists
                            final DigestURI url = new DigestURI(crawlingStart, null);
                            final byte[] urlhash = url.hash();
                            indexSegment.urlMetadata().remove(urlhash);
                            sb.crawlQueues.noticeURL.removeByURLHash(urlhash);
                            sb.crawlQueues.errorURL.remove(urlhash);
                            
                            // stack url
                            sb.crawler.profilesPassiveCrawls.removeEntry(crawlingStartURL.hash()); // if there is an old entry, delete it
                            final CrawlProfile.entry pe = sb.crawler.profilesActiveCrawls.newEntry(
                                    (crawlingStartURL.getHost() == null) ? Long.toHexString(System.currentTimeMillis()) : crawlingStartURL.getHost(),
                                    crawlingStartURL,
                                    newcrawlingMustMatch,
                                    newcrawlingMustNotMatch,
                                    newcrawlingdepth,
                                    crawlingIfOlder, crawlingDomFilterDepth, crawlingDomMaxPages,
                                    crawlingQ,
                                    indexText, indexMedia,
                                    storeHTCache, true, crawlOrder, xsstopw, xdstopw, xpstopw, cachePolicy);
                            final String reasonString = sb.crawlStacker.stackCrawl(new Request(
                                    sb.peers.mySeed().hash.getBytes(),
                                    url,
                                    null,
                                    "CRAWLING-ROOT",
                                    new Date(),
                                    pe.handle(),
                                    0,
                                    0,
                                    0
                                    ));
                            
                            if (reasonString == null) {
                            	// create a bookmark from crawl start url
                            	Set<String> tags=listManager.string2set(BookmarkHelper.cleanTagsString(post.get("bookmarkFolder","/crawlStart")));                                
                                tags.add("crawlStart");
                            	if (post.get("createBookmark","off").equals("on")) {
                                	bookmarksDB.Bookmark bookmark = sb.bookmarksDB.createBookmark(crawlingStart, "admin");
                        			if(bookmark != null){
                        				bookmark.setProperty(bookmarksDB.Bookmark.BOOKMARK_TITLE, post.get("bookmarkTitle", crawlingStart));                        				
                        				bookmark.setOwner("admin");                        				
                        				bookmark.setPublic(false);    
                        				bookmark.setTags(tags, true);
                        				sb.bookmarksDB.saveBookmark(bookmark);
                        			}
                                }
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
                                    sb.peers.newsPool.publishMyNews(sb.peers.mySeed(), yacyNewsPool.CATEGORY_CRAWL_START, m);
                                }                                
                            } else {
                                prop.put("info", "5"); //Crawling failed
                                prop.putHTML("info_crawlingURL", (post.get("crawlingURL")));
                                prop.putHTML("info_reasonString", reasonString);
                                
                                sb.crawlQueues.errorURL.push(
                                        new Request(
                                                sb.peers.mySeed().hash.getBytes(), 
                                                crawlingStartURL, 
                                                null, 
                                                "", 
                                                new Date(),
                                                pe.handle(),
                                                0, 
                                                0, 
                                                0),
                                        sb.peers.mySeed().hash.getBytes(),
                                        new Date(),
                                        1,
                                        reasonString);
                            }
                        } catch (final PatternSyntaxException e) {
                            prop.put("info", "4"); //crawlfilter does not match url
                            prop.putHTML("info_newcrawlingfilter", newcrawlingMustMatch);
                            prop.putHTML("info_error", e.getMessage());
                        } catch (final Exception e) {
                            // mist
                            prop.put("info", "6");//Error with url
                            prop.putHTML("info_crawlingStart", crawlingStart);
                            prop.putHTML("info_error", e.getMessage());
                            Log.logException(e);
                        }
                        
                    } else if (crawlingMode.equals(CRAWLING_MODE_FILE)) {
                        if (post.containsKey("crawlingFile")) {
                            // getting the name of the uploaded file
                            final String fileName = post.get("crawlingFile");  
                            try {
                                // check if the crawl filter works correctly
                                Pattern.compile(newcrawlingMustMatch);
                                
                                // loading the file content
                                final File file = new File(fileName);
                                
                                // getting the content of the bookmark file
                                final String fileString = post.get("crawlingFile$file");
                                
                                // parsing the bookmark file and fetching the headline and contained links
                                final ContentScraper scraper = new ContentScraper(new DigestURI(file));
                                //OutputStream os = new htmlFilterOutputStream(null, scraper, null, false);
                                final Writer writer = new TransformerWriter(null,null,scraper,null,false);
                                FileUtils.copy(fileString, writer);
                                writer.close();
                                
                                //String headline = scraper.getHeadline();
                                final Map<MultiProtocolURI, String> hyperlinks = scraper.getAnchors();
                                
                                // creating a crawler profile
                                final DigestURI crawlURL = new DigestURI("file://" + file.toString(), null);
                                final CrawlProfile.entry profile = sb.crawler.profilesActiveCrawls.newEntry(
                                        fileName, crawlURL,
                                        newcrawlingMustMatch,
                                        CrawlProfile.MATCH_NEVER,
                                        newcrawlingdepth,
                                        crawlingIfOlder,
                                        crawlingDomFilterDepth,
                                        crawlingDomMaxPages,
                                        crawlingQ,
                                        indexText,
                                        indexMedia,
                                        storeHTCache,
                                        true,
                                        crawlOrder,
                                        xsstopw, xdstopw, xpstopw,
                                        cachePolicy);
                                
                                // pause local crawl here
                                sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                                
                                // loop through the contained links
                                final Iterator<Map.Entry<MultiProtocolURI, String>> linkiterator = hyperlinks.entrySet().iterator();
                                DigestURI nexturl;
                                while (linkiterator.hasNext()) {
                                    final Map.Entry<MultiProtocolURI, String> e = linkiterator.next();
                                    if (e.getKey() == null) continue;
                                    nexturl = new DigestURI(e.getKey());
                                    
                                    // enqueuing the url for crawling
                                    sb.crawlStacker.enqueueEntry(new Request(
                                            sb.peers.mySeed().hash.getBytes(), 
                                            nexturl, 
                                            null, 
                                            e.getValue(), 
                                            new Date(),
                                            profile.handle(),
                                            0,
                                            0,
                                            0
                                            ));
                                }
                               
                            } catch (final PatternSyntaxException e) {
                                // print error message
                                prop.put("info", "4"); //crawlfilter does not match url
                                prop.putHTML("info_newcrawlingfilter", newcrawlingMustMatch);
                                prop.putHTML("info_error", e.getMessage());
                            } catch (final Exception e) {
                                // mist
                                prop.put("info", "7");//Error with file
                                prop.putHTML("info_crawlingStart", fileName);
                                prop.putHTML("info_error", e.getMessage());
                                Log.logException(e);
                            }
                            sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                        }
                    } else if (crawlingMode.equals(CRAWLING_MODE_SITEMAP)) { 
                    	String sitemapURLStr = null;
                    	try {
                    		// getting the sitemap URL
                    		sitemapURLStr = post.get("sitemapURL","");
                    		final DigestURI sitemapURL = new DigestURI(sitemapURLStr, null);
                            
                    		// create a new profile
                    		final CrawlProfile.entry pe = sb.crawler.profilesActiveCrawls.newEntry(
                    				sitemapURLStr, sitemapURL,
                    				newcrawlingMustMatch,
                    				CrawlProfile.MATCH_NEVER,
                    				newcrawlingdepth,
                    				crawlingIfOlder, crawlingDomFilterDepth, crawlingDomMaxPages,
                    				crawlingQ,
                    				indexText, indexMedia,
                    				storeHTCache, true, crawlOrder,
                    				xsstopw, xdstopw, xpstopw,
                    				cachePolicy);
                    		
                    		// create a new sitemap importer
                    		final SitemapImporter importerThread = new SitemapImporter(sb, sb.dbImportManager, new DigestURI(sitemapURLStr, null), pe);
                    		if (importerThread != null) {
                    		    importerThread.setJobID(sb.dbImportManager.generateUniqueJobID());
                    			importerThread.startIt();
                    		}
                    	} catch (final Exception e) {
                    		// mist
                    		prop.put("info", "6");//Error with url
                    		prop.putHTML("info_crawlingStart", sitemapURLStr);
                    		prop.putHTML("info_error", e.getMessage());
                    		Log.logException(e);
                    	}
                    }
                }
            }
            
            if (post.containsKey("crawlingPerformance")) {
                setPerformance(sb, post);
            }
        }
        
        // performance settings
        final long LCbusySleep = Integer.parseInt(env.getConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, "1000"));
        final int LCppm = (int) (60000L / Math.max(1,LCbusySleep));
        prop.put("crawlingSpeedMaxChecked", (LCppm >= 30000) ? "1" : "0");
        prop.put("crawlingSpeedCustChecked", ((LCppm > 10) && (LCppm < 30000)) ? "1" : "0");
        prop.put("crawlingSpeedMinChecked", (LCppm <= 10) ? "1" : "0");
        prop.put("customPPMdefault", Integer.toString(LCppm));
        
        // return rewrite properties
        return prop;
    }
    
    private static long recrawlIfOlderC(final boolean recrawlIfOlderCheck, final int recrawlIfOlderNumber, final String crawlingIfOlderUnit) {
        if (!recrawlIfOlderCheck) return 0L;
        if (crawlingIfOlderUnit.equals("year")) return System.currentTimeMillis() - (long) recrawlIfOlderNumber * 1000L * 60L * 60L * 24L * 365L;
        if (crawlingIfOlderUnit.equals("month")) return System.currentTimeMillis() - (long) recrawlIfOlderNumber * 1000L * 60L * 60L * 24L * 30L;
        if (crawlingIfOlderUnit.equals("day")) return System.currentTimeMillis() - (long) recrawlIfOlderNumber * 1000L * 60L * 60L * 24L;
        if (crawlingIfOlderUnit.equals("hour")) return System.currentTimeMillis() - (long) recrawlIfOlderNumber * 1000L * 60L * 60L;
        return System.currentTimeMillis() - (long) recrawlIfOlderNumber;
    }
    
    private static void setPerformance(final Switchboard sb, final serverObjects post) {
        final String crawlingPerformance = post.get("crawlingPerformance", "custom");
        final long LCbusySleep = Integer.parseInt(sb.getConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, "1000"));
        int wantedPPM = (LCbusySleep == 0) ? 30000 : (int) (60000L / LCbusySleep);
        try {
            wantedPPM = Integer.parseInt(post.get("customPPM", Integer.toString(wantedPPM)));
        } catch (final NumberFormatException e) {}
        if (crawlingPerformance.toLowerCase().equals("minimum")) wantedPPM = 10;
        if (crawlingPerformance.toLowerCase().equals("maximum")) wantedPPM = 30000;
        sb.setPerformance(wantedPPM);
    }
    
}
