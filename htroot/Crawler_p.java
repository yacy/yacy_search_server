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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.CrawlQueues;
import net.yacy.crawler.data.ZURL.FailCategory;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.SitemapImporter;
import net.yacy.data.BookmarkHelper;
import net.yacy.data.BookmarksDB;
import net.yacy.data.ListManager;
import net.yacy.data.WorkTables;
import net.yacy.data.ymark.YMarkTables;
import net.yacy.document.Document;
import net.yacy.document.Parser.Failure;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.TransformerWriter;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.peers.NewsPool;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class Crawler_p {

    // this servlet does NOT create the Crawler servlet page content!
    // this servlet starts a web crawl. The interface for entering the web crawl parameters is in IndexCreate_p.html

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
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
        prop.put("localCrawlSize", sb.crawlQueues.coreCrawlJobSize());
        prop.put("localCrawlState", "");
        prop.put("limitCrawlSize", sb.crawlQueues.limitCrawlJobSize());
        prop.put("limitCrawlState", "");
        prop.put("remoteCrawlSize", sb.crawlQueues.remoteTriggeredCrawlJobSize());
        prop.put("remoteCrawlState", "");
        prop.put("noloadCrawlSize", sb.crawlQueues.noloadCrawlJobSize());
        prop.put("noloadCrawlState", "");
        prop.put("list-remote", 0);
        prop.put("forwardToCrawlStart", "0");

        prop.put("info", "0");

        if (post != null) {
            String c = post.toString();
            if (c.length() < 1000) Log.logInfo("Crawl Start", c);
        }

        if (post != null && post.containsKey("continue")) {
            // continue queue
            final String queue = post.get("continue", "");
            if ("localcrawler".equals(queue)) {
                sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
            } else if ("remotecrawler".equals(queue)) {
                sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
            }
        }

        if (post != null && post.containsKey("pause")) {
            // pause queue
            final String queue = post.get("pause", "");
            if ("localcrawler".equals(queue)) {
                sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
            } else if ("remotecrawler".equals(queue)) {
                sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
            }
        }

    	if (post != null && post.containsKey("terminate")) try {
            final String handle = post.get("handle", "");
            // termination of a crawl: shift the crawl from active to passive
            final CrawlProfile p = sb.crawler.getActive(handle.getBytes());
            if (p != null) sb.crawler.putPassive(handle.getBytes(), p);
            // delete all entries from the crawl queue that are deleted here
            sb.crawler.removeActive(handle.getBytes());
            sb.crawlQueues.noticeURL.removeByProfileHandle(handle, 10000);
        } catch (final SpaceExceededException e) {
            Log.logException(e);
        }

        if (post != null && post.containsKey("crawlingstart")) {
            // init crawl
            if (sb.peers == null) {
                prop.put("info", "3");
            } else {
                
                // remove crawlingFileContent before we record the call
                String crawlingFileName = post.get("crawlingFile");
                final File crawlingFile;
                if (crawlingFileName == null || crawlingFileName.isEmpty()) {
                    crawlingFile = null;
                } else {
                    if (crawlingFileName.startsWith("file://")) crawlingFileName = crawlingFileName.substring(7);
                    crawlingFile = new File(crawlingFileName);
                }
                if (crawlingFile != null && crawlingFile.exists()) {
                    post.remove("crawlingFile$file");
                }
                
                // prepare some filter that are adjusted in case that this is wanted
                boolean storeHTCache = "on".equals(post.get("storeHTCache", "on"));
                String newcrawlingMustMatch = post.get("mustmatch", CrawlProfile.MATCH_ALL_STRING);
                String newcrawlingMustNotMatch = post.get("mustnotmatch", CrawlProfile.MATCH_NEVER_STRING);
                if (newcrawlingMustMatch.length() < 2) newcrawlingMustMatch = CrawlProfile.MATCH_ALL_STRING; // avoid that all urls are filtered out if bad value was submitted
                final boolean fullDomain = "domain".equals(post.get("range", "wide")); // special property in simple crawl start
                final boolean subPath    = "subpath".equals(post.get("range", "wide")); // special property in simple crawl start

                String crawlingStart0 = post.get("crawlingURL","").trim(); // the crawljob start url
                String[] rootURLs0 = crawlingStart0.indexOf('\n') > 0 || crawlingStart0.indexOf('\r') > 0 ? crawlingStart0.split("[\\r\\n]+") : crawlingStart0.split(Pattern.quote("|"));
                Set<DigestURI> rootURLs = new HashSet<DigestURI>();
                String crawlName = "";
                if (crawlingFile == null) for (String crawlingStart: rootURLs0) {
                    if (crawlingStart == null || crawlingStart.length() == 0) continue;
                    // add the prefix http:// if necessary
                    int pos = crawlingStart.indexOf("://",0);
                    if (pos == -1) {
                        if (crawlingStart.startsWith("www")) crawlingStart = "http://" + crawlingStart;
                        if (crawlingStart.startsWith("ftp")) crawlingStart = "ftp://" + crawlingStart;
                    }
                    try {
                        DigestURI crawlingStartURL = new DigestURI(crawlingStart);
                        rootURLs.add(crawlingStartURL);
                        crawlName += crawlingStartURL.getHost() + "_";
                        if (fullDomain) {
                            newcrawlingMustMatch = CrawlProfile.mustMatchFilterFullDomain(crawlingStartURL);
                            if (subPath) newcrawlingMustMatch = newcrawlingMustMatch.substring(0, newcrawlingMustMatch.length() - 2) + crawlingStartURL.getPath() + ".*";
                        }
                        if (crawlingStart!= null && subPath && (pos = crawlingStart.lastIndexOf('/')) > 0) {
                            newcrawlingMustMatch = crawlingStart.substring(0, pos + 1) + ".*";
                        }
                        if (crawlingStartURL != null && (crawlingStartURL.isFile() || crawlingStartURL.isSMB())) storeHTCache = false;
                        
                    } catch (MalformedURLException e) {
                        Log.logException(e);
                    }
                }
                if (crawlName.length() > 80) crawlName = crawlName.substring(0, 80);
                if (crawlName.endsWith("_")) crawlName = crawlName.substring(0, crawlName.length() - 1);

                
                // set the crawl filter
                String ipMustMatch = post.get("ipMustmatch", CrawlProfile.MATCH_ALL_STRING);
                final String ipMustNotMatch = post.get("ipMustnotmatch", CrawlProfile.MATCH_NEVER_STRING);
                if (ipMustMatch.length() < 2) ipMustMatch = CrawlProfile.MATCH_ALL_STRING;
                final String countryMustMatch = post.getBoolean("countryMustMatchSwitch") ? post.get("countryMustMatchList", "") : "";
                sb.setConfig("crawlingIPMustMatch", ipMustMatch);
                sb.setConfig("crawlingIPMustNotMatch", ipMustNotMatch);
                if (countryMustMatch.length() > 0) sb.setConfig("crawlingCountryMustMatch", countryMustMatch);

                String crawlerNoDepthLimitMatch = post.get("crawlingDepthExtension", CrawlProfile.MATCH_NEVER_STRING);
                final String indexUrlMustMatch = post.get("indexmustmatch", CrawlProfile.MATCH_ALL_STRING);
                final String indexUrlMustNotMatch = post.get("indexmustnotmatch", CrawlProfile.MATCH_NEVER_STRING);

                final boolean crawlOrder = post.get("crawlOrder", "off").equals("on");
                env.setConfig("crawlOrder", crawlOrder);

                if (crawlOrder) crawlerNoDepthLimitMatch = CrawlProfile.MATCH_NEVER_STRING; // without limitation the crawl order does not work
                
                int newcrawlingdepth = post.getInt("crawlingDepth", 8);
                env.setConfig("crawlingDepth", Integer.toString(newcrawlingdepth));
                if ((crawlOrder) && (newcrawlingdepth > 8)) newcrawlingdepth = 8;

                boolean directDocByURL = "on".equals(post.get("directDocByURL", "on")); // catch also all linked media documents without loading them
                env.setConfig("crawlingDirectDocByURL", directDocByURL);

                final String collection = post.get("collection", sb.getConfig("collection", "user"));
                env.setConfig("collection", collection);

                // recrawl
                final String recrawl = post.get("recrawl", "nodoubles"); // nodoubles, reload, scheduler
                boolean crawlingIfOlderCheck = "on".equals(post.get("crawlingIfOlderCheck", "off"));
                int crawlingIfOlderNumber = post.getInt("crawlingIfOlderNumber", -1);
                String crawlingIfOlderUnit = post.get("crawlingIfOlderUnit","year"); // year, month, day, hour
                int repeat_time = post.getInt("repeat_time", -1);
                final String repeat_unit = post.get("repeat_unit", "seldays"); // selminutes, selhours, seldays

                if ("scheduler".equals(recrawl) && repeat_time > 0) {
                    // set crawlingIfOlder attributes that are appropriate for scheduled crawling
                    crawlingIfOlderCheck = true;
                    crawlingIfOlderNumber = "selminutes".equals(repeat_unit) ? 1 : "selhours".equals(repeat_unit) ? repeat_time / 2 : repeat_time * 12;
                    crawlingIfOlderUnit = "hour";
                } else if ("reload".equals(recrawl)) {
                    repeat_time = -1;
                    crawlingIfOlderCheck = true;
                } else if ("nodoubles".equals(recrawl)) {
                    repeat_time = -1;
                    crawlingIfOlderCheck = false;
                }
                final long crawlingIfOlder = recrawlIfOlderC(crawlingIfOlderCheck, crawlingIfOlderNumber, crawlingIfOlderUnit);
                env.setConfig("crawlingIfOlder", crawlingIfOlder);

                // store this call as api call
                if (repeat_time > 0) {
                    // store as scheduled api call
                    sb.tables.recordAPICall(post, "Crawler_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "crawl start for " + ((rootURLs.size() == 0) ? post.get("crawlingFile", "") : rootURLs.iterator().next().toNormalform(true, false)), repeat_time, repeat_unit.substring(3));
                } else {
                    // store just a protocol
                    sb.tables.recordAPICall(post, "Crawler_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "crawl start for " + ((rootURLs.size() == 0) ? post.get("crawlingFile", "") : rootURLs.iterator().next().toNormalform(true, false)));
                }

                final boolean crawlingDomMaxCheck = "on".equals(post.get("crawlingDomMaxCheck", "off"));
                final int crawlingDomMaxPages = (crawlingDomMaxCheck) ? post.getInt("crawlingDomMaxPages", -1) : -1;
                env.setConfig("crawlingDomMaxPages", Integer.toString(crawlingDomMaxPages));

                boolean crawlingQ = "on".equals(post.get("crawlingQ", "off"));
                env.setConfig("crawlingQ", crawlingQ);

                final boolean indexText = "on".equals(post.get("indexText", "on"));
                env.setConfig("indexText", indexText);

                final boolean indexMedia = "on".equals(post.get("indexMedia", "on"));
                env.setConfig("indexMedia", indexMedia);

                env.setConfig("storeHTCache", storeHTCache);

                CacheStrategy cachePolicy = CacheStrategy.parse(post.get("cachePolicy", "iffresh"));
                if (cachePolicy == null) cachePolicy = CacheStrategy.IFFRESH;

                final boolean xsstopw = "on".equals(post.get("xsstopw", "off"));
                env.setConfig("xsstopw", xsstopw);

                final boolean xdstopw = "on".equals(post.get("xdstopw", "off"));
                env.setConfig("xdstopw", xdstopw);

                final boolean xpstopw = "on".equals(post.get("xpstopw", "off"));
                env.setConfig("xpstopw", xpstopw);

                String crawlingMode = post.get("crawlingMode","url");
                
                if ("file".equals(crawlingMode) && post.containsKey("crawlingFile")) {
                    newcrawlingMustNotMatch = CrawlProfile.MATCH_NEVER_STRING;
                    directDocByURL = false;
                }
                
                if ("sitemap".equals(crawlingMode)) {
                    newcrawlingMustMatch = CrawlProfile.MATCH_ALL_STRING;
                    newcrawlingMustNotMatch = CrawlProfile.MATCH_NEVER_STRING;
                    newcrawlingdepth = 0;
                    directDocByURL = false;
                    crawlingQ = true;
                }
                
                if ("sitelist".equals(crawlingMode)) {
                    newcrawlingMustNotMatch = CrawlProfile.MATCH_NEVER_STRING;
                    Set<DigestURI> newRootURLs = new HashSet<DigestURI>();
                    for (DigestURI sitelistURL: rootURLs) {
                        // download document
                        Document scraper;
                        try {
                            scraper = sb.loader.loadDocument(sitelistURL, CacheStrategy.IFFRESH, BlacklistType.CRAWLER, CrawlQueues.queuedMinLoadDelay);
                            // get links and generate filter
                            for (MultiProtocolURI u: scraper.getAnchors().keySet()) {
                                newRootURLs.add(new DigestURI(u));
                            }
                        } catch (IOException e) {
                            Log.logException(e);
                        }
                    }
                    rootURLs = newRootURLs;
                    crawlingMode = "url";
                    if ((fullDomain || subPath) && newcrawlingdepth > 0) newcrawlingMustMatch = CrawlProfile.MATCH_ALL_STRING; // to prevent that there is a restriction on the original urls
                }
                
                // compute mustmatch filter according to rootURLs
                if ((fullDomain || subPath) && newcrawlingdepth > 0) {
                    String siteFilter = ".*";
                    if (fullDomain) {
                        siteFilter = siteFilter(rootURLs);
                    } else if (subPath) {
                        siteFilter = subpathFilter(rootURLs);
                    }
                    newcrawlingMustMatch = CrawlProfile.MATCH_ALL_STRING.equals(newcrawlingMustMatch) ? siteFilter : "(?=(" + newcrawlingMustMatch + "))(" + siteFilter + ")";
                }
                
                // check if the crawl filter works correctly
                try {
                    Pattern.compile(newcrawlingMustMatch);
                } catch (final PatternSyntaxException e) {
                    prop.put("info", "4"); // crawlfilter does not match url
                    prop.putHTML("info_newcrawlingfilter", newcrawlingMustMatch);
                    prop.putHTML("info_error", e.getMessage());
                } 
                try {
                    Pattern.compile(newcrawlingMustNotMatch);
                } catch (final PatternSyntaxException e) {
                    prop.put("info", "4"); // crawlfilter does not match url
                    prop.putHTML("info_newcrawlingfilter", newcrawlingMustNotMatch);
                    prop.putHTML("info_error", e.getMessage());
                } 
                
                // prepare a new crawling profile
                final CrawlProfile profile = new CrawlProfile(
                        crawlName,
                        newcrawlingMustMatch,
                        newcrawlingMustNotMatch,
                        ipMustMatch,
                        ipMustNotMatch,
                        countryMustMatch,
                        crawlerNoDepthLimitMatch,
                        indexUrlMustMatch,
                        indexUrlMustNotMatch,
                        newcrawlingdepth,
                        directDocByURL,
                        crawlingIfOlder,
                        crawlingDomMaxPages,
                        crawlingQ,
                        indexText,
                        indexMedia,
                        storeHTCache,
                        crawlOrder,
                        xsstopw,
                        xdstopw,
                        xpstopw,
                        cachePolicy,
                        collection);
                byte[] handle = ASCII.getBytes(profile.handle());
                
                if ("url".equals(crawlingMode)) {
                    if (rootURLs.size() == 0) {
                        prop.put("info", "5"); //Crawling failed
                        prop.putHTML("info_crawlingURL", "(no url given)");
                        prop.putHTML("info_reasonString", "you must submit at least one crawl url");
                    } else {
                        
                        // stack requests
                        sb.crawler.putActive(handle, profile);
                        sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                        Set<DigestURI> successurls = new HashSet<DigestURI>();
                        Map<DigestURI,String> failurls = new HashMap<DigestURI, String>();
                        String failreason;
                        for (DigestURI url: rootURLs) {
                            if ((failreason = stackUrl(sb, profile, url)) == null) successurls.add(url); else failurls.put(url, failreason);
                        }
                        
                        if (failurls.size() == 0) {
                            // liftoff!
                            prop.put("info", "8");
                            prop.putHTML("info_crawlingURL", post.get("crawlingURL"));
    
                            // generate a YaCyNews if the global flag was set
                            if (!sb.isRobinsonMode() && crawlOrder) {
                                final Map<String, String> m = new HashMap<String, String>(profile); // must be cloned
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
                                sb.peers.newsPool.publishMyNews(sb.peers.mySeed(), NewsPool.CATEGORY_CRAWL_START, m);
                            }
                        } else {
                            StringBuilder fr = new StringBuilder();
                            for (Map.Entry<DigestURI, String> failure: failurls.entrySet()) {
                                sb.crawlQueues.errorURL.push(
                                    new Request(
                                            sb.peers.mySeed().hash.getBytes(),
                                            failure.getKey(),
                                            null,
                                            "",
                                            new Date(),
                                            profile.handle(),
                                            0,
                                            0,
                                            0,
                                            0),
                                    sb.peers.mySeed().hash.getBytes(),
                                    new Date(),
                                    1,
                                    FailCategory.FINAL_LOAD_CONTEXT,
                                    failure.getValue(), -1);
                                fr.append(failure.getValue()).append('/');
                            }
    
                            prop.put("info", "5"); //Crawling failed
                            prop.putHTML("info_crawlingURL", (post.get("crawlingURL")));
                            prop.putHTML("info_reasonString", fr.toString());
                        }
                        if (successurls.size() > 0) sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                    }
                } else if ("sitemap".equals(crawlingMode)) {
                    final String sitemapURLStr = post.get("sitemapURL","");
                    try {
                        final DigestURI sitemapURL = new DigestURI(sitemapURLStr);
                        sb.crawler.putActive(handle, profile);
                        final SitemapImporter importer = new SitemapImporter(sb, sitemapURL, profile);
                        importer.start();
                        sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                    } catch (final Exception e) {
                        // mist
                        prop.put("info", "6");//Error with url
                        prop.putHTML("info_crawlingStart", sitemapURLStr);
                        prop.putHTML("info_error", e.getMessage());
                        Log.logException(e);
                    }
                } else if ("file".equals(crawlingMode)) {
                    if (post.containsKey("crawlingFile")) {
                        final String crawlingFileContent = post.get("crawlingFile$file", "");
                        try {
                            // check if the crawl filter works correctly
                            Pattern.compile(newcrawlingMustMatch);
                            final ContentScraper scraper = new ContentScraper(new DigestURI(crawlingFile), 10000);
                            final Writer writer = new TransformerWriter(null, null, scraper, null, false);
                            if (crawlingFile != null && crawlingFile.exists()) {
                                FileUtils.copy(new FileInputStream(crawlingFile), writer);
                            } else {
                                FileUtils.copy(crawlingFileContent, writer);
                            }
                            writer.close();

                            // get links and generate filter
                            final Map<MultiProtocolURI, Properties> hyperlinks = scraper.getAnchors();
                            if (newcrawlingdepth > 0) {
                                if (fullDomain) {
                                    newcrawlingMustMatch = siteFilter(hyperlinks.keySet());
                                } else if (subPath) {
                                    newcrawlingMustMatch = subpathFilter(hyperlinks.keySet());
                                }
                            }

                            sb.crawler.putActive(handle, profile);
                            sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                            sb.crawlStacker.enqueueEntriesAsynchronous(sb.peers.mySeed().hash.getBytes(), profile.handle(), hyperlinks);
                        } catch (final PatternSyntaxException e) {
                            prop.put("info", "4"); // crawlfilter does not match url
                            prop.putHTML("info_newcrawlingfilter", newcrawlingMustMatch);
                            prop.putHTML("info_error", e.getMessage());
                        } catch (final Exception e) {
                            // mist
                            prop.put("info", "7"); // Error with file
                            prop.putHTML("info_crawlingStart", crawlingFileName);
                            prop.putHTML("info_error", e.getMessage());
                            Log.logException(e);
                        }
                        sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                    }
                }
            }
        }

        if (post != null && post.containsKey("crawlingPerformance")) {
            setPerformance(sb, post);
        }

        // performance settings
        final long LCbusySleep = env.getConfigLong(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, 1000L);
        final int LCppm = (int) (60000L / Math.max(1,LCbusySleep));
        prop.put("crawlingSpeedMaxChecked", (LCppm >= 30000) ? "1" : "0");
        prop.put("crawlingSpeedCustChecked", ((LCppm > 10) && (LCppm < 30000)) ? "1" : "0");
        prop.put("crawlingSpeedMinChecked", (LCppm <= 10) ? "1" : "0");
        prop.put("customPPMdefault", Integer.toString(LCppm));

        // generate crawl profile table
        int count = 0;
        boolean dark = true;
        final int domlistlength = (post == null) ? 160 : post.getInt("domlistlength", 160);
        CrawlProfile profile;
        // put active crawls into list
        for (final byte[] h: sb.crawler.getActive()) {
            profile = sb.crawler.getActive(h);
        	if (CrawlProfile.ignoreNames.contains(profile.name())) continue;
            profile.putProfileEntry("crawlProfilesShow_list_", prop, true, dark, count, domlistlength);
            dark = !dark;
            count++;
        }
        prop.put("crawlProfilesShow_list", count);
        prop.put("crawlProfilesShow", count == 0 ? 0 : 1);


        // return rewrite properties
        return prop;
    }

    /**
     * stack the url to the crawler
     * @param sb
     * @param profile
     * @param url
     * @return null if this was ok. If this failed, return a string with a fail reason
     */
    private static String stackUrl(Switchboard sb, CrawlProfile profile, DigestURI url) {
        
        byte[] handle = ASCII.getBytes(profile.handle());

        // remove url from the index to be prepared for a re-crawl
        final byte[] urlhash = url.hash();
        sb.index.fulltext().remove(urlhash);
        sb.crawlQueues.noticeURL.removeByURLHash(urlhash);
        sb.crawlQueues.errorURL.remove(urlhash);
        
        // special handling of ftp protocol
        if (url.isFTP()) {
            try {
                sb.crawler.putActive(handle, profile);
                sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                sb.crawlStacker.enqueueEntriesFTP(sb.peers.mySeed().hash.getBytes(), profile.handle(), url.getHost(), url.getPort(), false);
                return null;
            } catch (final Exception e) {
                // mist
                Log.logException(e);
                return "problem crawling an ftp site: " + e.getMessage();
            }
        }

        // get a scraper to get the title
        Document scraper;
        try {
            scraper = sb.loader.loadDocument(url, CacheStrategy.IFFRESH, BlacklistType.CRAWLER, CrawlQueues.queuedMinLoadDelay);
        } catch (IOException e) {
            Log.logException(e);
            return "scraper cannot load URL: " + e.getMessage();
        }
        
        final String title = scraper == null ? url.toNormalform(true, true) : scraper.dc_title();
        final String description = scraper.dc_description();

        // add the url to the crawl stack
        sb.crawler.removePassive(handle); // if there is an old entry, delete it
        sb.crawler.putActive(handle, profile);
        final String reasonString = sb.crawlStacker.stackCrawl(new Request(
                sb.peers.mySeed().hash.getBytes(),
                url,
                null,
                "CRAWLING-ROOT",
                new Date(),
                profile.handle(),
                0,
                0,
                0,
                0
                ));
        
        if (reasonString != null) return reasonString;
        
        // create a bookmark from crawl start url
        //final Set<String> tags=ListManager.string2set(BookmarkHelper.cleanTagsString(post.get("bookmarkFolder","/crawlStart")));
        final Set<String> tags=ListManager.string2set(BookmarkHelper.cleanTagsString("/crawlStart"));
        tags.add("crawlStart");
        final String[] keywords = scraper.dc_subject();
        if (keywords != null) {
            for (final String k: keywords) {
                final String kk = BookmarkHelper.cleanTagsString(k);
                if (kk.length() > 0) tags.add(kk);
            }
        }
        String tagStr = tags.toString();
        if (tagStr.length() > 2 && tagStr.startsWith("[") && tagStr.endsWith("]")) tagStr = tagStr.substring(1, tagStr.length() - 2);

        // we will create always a bookmark to use this to track crawled hosts
        final BookmarksDB.Bookmark bookmark = sb.bookmarksDB.createBookmark(url.toNormalform(true, false), "admin");
        if (bookmark != null) {
            bookmark.setProperty(BookmarksDB.Bookmark.BOOKMARK_TITLE, title);
            bookmark.setProperty(BookmarksDB.Bookmark.BOOKMARK_DESCRIPTION, description);
            bookmark.setOwner("admin");
            bookmark.setPublic(false);
            bookmark.setTags(tags, true);
            sb.bookmarksDB.saveBookmark(bookmark);
        }

        // do the same for ymarks
        // TODO: could a non admin user add crawls?
        try {
            sb.tables.bookmarks.createBookmark(sb.loader, url, YMarkTables.USER_ADMIN, true, "crawlStart", "/Crawl Start");
        } catch (IOException e) {
            Log.logException(e);
        } catch (Failure e) {
            Log.logException(e);
        }

        // that was ok
        return null;
    }
    
    private static long recrawlIfOlderC(final boolean recrawlIfOlderCheck, final int recrawlIfOlderNumber, final String crawlingIfOlderUnit) {
        if (!recrawlIfOlderCheck) return 0L;
        if ("year".equals(crawlingIfOlderUnit)) return System.currentTimeMillis() - recrawlIfOlderNumber * 1000L * 60L * 60L * 24L * 365L;
        if ("month".equals(crawlingIfOlderUnit)) return System.currentTimeMillis() - recrawlIfOlderNumber * 1000L * 60L * 60L * 24L * 30L;
        if ("day".equals(crawlingIfOlderUnit)) return System.currentTimeMillis() - recrawlIfOlderNumber * 1000L * 60L * 60L * 24L;
        if ("hour".equals(crawlingIfOlderUnit)) return System.currentTimeMillis() - recrawlIfOlderNumber * 1000L * 60L * 60L;
        return System.currentTimeMillis() - recrawlIfOlderNumber;
    }

    private static void setPerformance(final Switchboard sb, final serverObjects post) {
        final String crawlingPerformance = post.get("crawlingPerformance", "custom");
        final long LCbusySleep = sb.getConfigLong(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, 1000L);
        int wantedPPM = (LCbusySleep == 0) ? 30000 : (int) (60000L / LCbusySleep);
        try {
            wantedPPM = post.getInt("customPPM", wantedPPM);
        } catch (final NumberFormatException e) {}
        if ("minimum".equals(crawlingPerformance.toLowerCase())) wantedPPM = 10;
        if ("maximum".equals(crawlingPerformance.toLowerCase())) wantedPPM = 30000;
        sb.setPerformance(wantedPPM);
    }

    private static String siteFilter(final Set<? extends MultiProtocolURI> uris) {
        final StringBuilder filter = new StringBuilder();
        final Set<String> filterSet = new HashSet<String>();
        for (final MultiProtocolURI uri: uris) {
            filterSet.add(new StringBuilder().append(uri.getProtocol()).append("://").append(uri.getHost()).append(".*").toString());
            if (!uri.getHost().startsWith("www.")) {
                filterSet.add(new StringBuilder().append(uri.getProtocol()).append("://www.").append(uri.getHost()).append(".*").toString());
            }
        }
        for (final String element : filterSet) {
            filter.append('|').append(element);
        }
        return filter.length() > 0 ? filter.substring(1) : "";
    }

    private static String subpathFilter(final Set<? extends MultiProtocolURI> uris) {
        final StringBuilder filter = new StringBuilder();
        final Set<String> filterSet = new HashSet<String>();
        for (final MultiProtocolURI uri: uris) {
            filterSet.add(new StringBuilder().append(uri.toNormalform(true, false)).append(".*").toString());
        }
        for (final String element : filterSet) {
            filter.append('|').append(element);
        }
        return filter.length() > 0 ? filter.substring(1) : "";
    }
}
