// Crawler_p.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 18.12.2006 on http://www.anomic.de
// this file was created using the an implementation from IndexCreate_p.java, published 02.12.2004
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.FailCategory;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.CrawlSwitchboard;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.NoticedURL.StackType;
import net.yacy.crawler.retrieval.SitemapImporter;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.data.WorkTables;
import net.yacy.document.Document;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.TransformerWriter;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.workflow.BusyThread;
import net.yacy.peers.NewsPool;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Fulltext;
import net.yacy.search.index.Segment;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class Crawler_p {

    // this servlet does NOT create the Crawler servlet page content!
    // this servlet starts a web crawl. The interface for entering the web crawl parameters is in IndexCreate_p.html

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        // inital values for AJAX Elements (without JavaScript)
        final serverObjects prop = new serverObjects();
        prop.put("rejected", 0);

        Segment segment = sb.index;
        Fulltext fulltext = segment.fulltext();
        String localSolr = "/solr/select?core=collection1&q=*:*&start=0&rows=3";
        String remoteSolr = env.getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_URL, localSolr);
        if (!remoteSolr.endsWith("/")) remoteSolr = remoteSolr + "/";
        prop.put("urlpublictextSolrURL", fulltext.connectedLocalSolr() ? localSolr : remoteSolr + "collection1/select?&q=*:*&start=0&rows=3");
        prop.putNum("urlpublictextSize", fulltext.collectionSize());
        prop.putNum("urlpublictextSegmentCount", fulltext.getDefaultConnector().getSegmentCount());
        prop.put("webgraphSolrURL", fulltext.connectedLocalSolr() ? localSolr.replace("collection1", "webgraph") : remoteSolr + "webgraph/select?&q=*:*&start=0&rows=3");
        prop.putNum("webgraphSize", fulltext.useWebgraph() ? fulltext.webgraphSize() : 0);
        prop.putNum("webgraphSegmentCount", fulltext.useWebgraph() ? fulltext.getWebgraphConnector().getSegmentCount() : 0);
        prop.putNum("citationSize", segment.citationCount());
        prop.putNum("citationSegmentCount", segment.citationSegmentCount());
        prop.putNum("rwipublictextSize", segment.RWICount());
        prop.putNum("rwipublictextSegmentCount", segment.RWISegmentCount());
        
        prop.put("list", "0");
        prop.put("loaderSize", 0);
        prop.put("loaderMax", 0);
        prop.put("list-loader", 0);
        
        int coreCrawlJobSize = sb.crawlQueues.coreCrawlJobSize();
        int limitCrawlJobSize = sb.crawlQueues.limitCrawlJobSize();
        int remoteTriggeredCrawlJobSize = sb.crawlQueues.remoteTriggeredCrawlJobSize();
        int noloadCrawlJobSize = sb.crawlQueues.noloadCrawlJobSize();
        int allsize = coreCrawlJobSize + limitCrawlJobSize + remoteTriggeredCrawlJobSize + noloadCrawlJobSize;
        
        prop.put("localCrawlSize", coreCrawlJobSize);
        prop.put("localCrawlState", "");
        prop.put("limitCrawlSize", limitCrawlJobSize);
        prop.put("limitCrawlState", "");
        prop.put("remoteCrawlSize", remoteTriggeredCrawlJobSize);
        prop.put("remoteCrawlState", "");
        prop.put("noloadCrawlSize", noloadCrawlJobSize);
        prop.put("noloadCrawlState", "");
        prop.put("terminate-button", allsize == 0 ? 0 : 1);
        prop.put("list-remote", 0);
        prop.put("forwardToCrawlStart", "0");

        prop.put("info", "0");
        boolean debug = (post != null && post.containsKey("debug"));
        
        if (post != null) {
            String c = post.toString();
            if (c.length() < 1000) ConcurrentLog.info("Crawl Start", c);
        }

        if (post != null && post.containsKey("queues_terminate_all")) {
            // clear stacks
            for (StackType stackType: StackType.values()) sb.crawlQueues.noticeURL.clear(stackType);
            try { sb.cleanProfiles(); } catch (final InterruptedException e) {/* ignore this */}

            // remove pause
            sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
            sb.setConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL + "_isPaused_cause", "");
            sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
            sb.setConfig(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL + "_isPaused_cause", "");
            prop.put("terminate-button", 0);
        }

        if (post != null && post.containsKey("continue")) {
            // continue queue
            final String queue = post.get("continue", "");
            if ("localcrawler".equals(queue)) {
                sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                sb.setConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL + "_isPaused_cause", "");
            } else if ("remotecrawler".equals(queue)) {
                sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
                sb.setConfig(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL + "_isPaused_cause", "");
            }
        }

        if (post != null && post.containsKey("pause")) {
            // pause queue
            final String queue = post.get("pause", "");
            if ("localcrawler".equals(queue)) {
                sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL, "user request in Crawler_p from " + header.refererHost());
            } else if ("remotecrawler".equals(queue)) {
                sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL, "user request in Crawler_p from " + header.refererHost());
            }
        }
        String queuemessage = sb.getConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL + "_isPaused_cause", "");
        if (queuemessage.length() == 0) {
            prop.put("info-queue", 0);
        } else {
            prop.put("info-queue", 1);
            prop.putHTML("info-queue_message", "pause reason: " + queuemessage);
        }
        
    	if (post != null && post.containsKey("terminate")) try {
            final String handle = post.get("handle", "");
            // termination of a crawl: shift the crawl from active to passive
            final CrawlProfile p = sb.crawler.getActive(handle.getBytes());
            if (p != null) sb.crawler.putPassive(handle.getBytes(), p);
            // delete all entries from the crawl queue that are deleted here
            sb.crawler.removeActive(handle.getBytes());
            sb.crawler.removePassive(handle.getBytes());
            sb.crawlQueues.noticeURL.removeByProfileHandle(handle, 10000);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
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
                boolean storeHTCache = "on".equals(post.get("storeHTCache", "off"));
                String newcrawlingMustMatch = post.get("mustmatch", CrawlProfile.MATCH_ALL_STRING);
                String newcrawlingMustNotMatch = post.get("mustnotmatch", CrawlProfile.MATCH_NEVER_STRING);
                if (newcrawlingMustMatch.length() < 2) newcrawlingMustMatch = CrawlProfile.MATCH_ALL_STRING; // avoid that all urls are filtered out if bad value was submitted
                final boolean fullDomain = "domain".equals(post.get("range", "wide")); // special property in simple crawl start
                final boolean subPath    = "subpath".equals(post.get("range", "wide")); // special property in simple crawl start

                final boolean restrictedcrawl = fullDomain || subPath || !CrawlProfile.MATCH_ALL_STRING.equals(newcrawlingMustMatch);
                final boolean deleteage = restrictedcrawl && "age".equals(post.get("deleteold","off"));
                Date deleteageDate = null;
                if (deleteage) {
                    long t = timeParser(true, post.getInt("deleteIfOlderNumber", -1), post.get("deleteIfOlderUnit","year")); // year, month, day, hour
                    if (t > 0) deleteageDate = new Date(t);
                }
                final boolean deleteold = (deleteage && deleteageDate != null) || (restrictedcrawl && post.getBoolean("deleteold"));

                final String sitemapURLStr = post.get("sitemapURL","");
                String crawlingStart0 = post.get("crawlingURL","").trim(); // the crawljob start url
                String[] rootURLs0 = crawlingStart0.indexOf('\n') > 0 || crawlingStart0.indexOf('\r') > 0 ? crawlingStart0.split("[\\r\\n]+") : crawlingStart0.split(Pattern.quote("|"));
                Set<DigestURL> rootURLs = new HashSet<DigestURL>();
                String crawlName = "";
                if (crawlingFile == null) for (String crawlingStart: rootURLs0) {
                    if (crawlingStart == null || crawlingStart.length() == 0) continue;
                    // add the prefix http:// if necessary
                    int pos = crawlingStart.indexOf("://",0);
                    if (pos == -1) {
                        if (crawlingStart.startsWith("ftp")) crawlingStart = "ftp://" + crawlingStart; else crawlingStart = "http://" + crawlingStart;
                    }
                    try {
                        DigestURL crawlingStartURL = new DigestURL(crawlingStart);
                        rootURLs.add(crawlingStartURL);
                        crawlName += ((crawlingStartURL.getHost() == null) ? crawlingStartURL.toNormalform(true) : crawlingStartURL.getHost()) + ',';
                        if (crawlingStartURL != null && (crawlingStartURL.isFile() || crawlingStartURL.isSMB())) storeHTCache = false;
                        
                    } catch (final MalformedURLException e) {
                        ConcurrentLog.logException(e);
                    }
                } else {
                	crawlName = crawlingFile.getName();
                }
                if (crawlName.length() > 256) {
                    int p = crawlName.lastIndexOf(',');
                    if (p >= 8) crawlName = crawlName.substring(0, p);
                }
                if (crawlName.endsWith(",")) crawlName = crawlName.substring(0, crawlName.length() - 1);
                if (crawlName.length() == 0 && sitemapURLStr.length() > 0) crawlName = "sitemap loader for " + sitemapURLStr;
                
                // delete old robots entries
                for (DigestURL ru: rootURLs) {
                    sb.robots.delete(ru);
                    try {Cache.delete(RobotsTxt.robotsURL(RobotsTxt.getHostPort(ru)).hash());} catch (IOException e) {}
                }
                try {sb.robots.clear();} catch (IOException e) {} // to be safe: clear all.
                
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
                final String indexContentMustMatch = post.get("indexcontentmustmatch", CrawlProfile.MATCH_ALL_STRING);
                final String indexContentMustNotMatch = post.get("indexcontentmustnotmatch", CrawlProfile.MATCH_NEVER_STRING);

                final boolean crawlOrder = post.get("crawlOrder", "off").equals("on");
                env.setConfig("crawlOrder", crawlOrder);

                if (crawlOrder) crawlerNoDepthLimitMatch = CrawlProfile.MATCH_NEVER_STRING; // without limitation the crawl order does not work
                
                int newcrawlingdepth = post.getInt("crawlingDepth", 8);
                env.setConfig("crawlingDepth", Integer.toString(newcrawlingdepth));
                if ((crawlOrder) && (newcrawlingdepth > 8)) newcrawlingdepth = 8;

                boolean directDocByURL = "on".equals(post.get("directDocByURL", "off")); // catch also all linked media documents without loading them
                env.setConfig("crawlingDirectDocByURL", directDocByURL);

                final String collection = post.get("collection", "user");
                env.setConfig("collection", collection);

                // recrawl
                final String recrawl = post.get("recrawl", "nodoubles"); // nodoubles, reload, scheduler
                long crawlingIfOlder = 0;
                if ("reload".equals(recrawl)) {
                    crawlingIfOlder = timeParser(true, post.getInt("reloadIfOlderNumber", -1), post.get("reloadIfOlderUnit","year")); // year, month, day, hour
                }
                env.setConfig("crawlingIfOlder", crawlingIfOlder);

                // store this call as api call
                sb.tables.recordAPICall(post, "Crawler_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "crawl start for " + ((rootURLs.size() == 0) ? post.get("crawlingFile", "") : rootURLs.iterator().next().toNormalform(true)));

                final boolean crawlingDomMaxCheck = "on".equals(post.get("crawlingDomMaxCheck", "off"));
                final int crawlingDomMaxPages = (crawlingDomMaxCheck) ? post.getInt("crawlingDomMaxPages", -1) : -1;
                env.setConfig("crawlingDomMaxPages", Integer.toString(crawlingDomMaxPages));

                boolean crawlingQ = "on".equals(post.get("crawlingQ", "off")); // on unchecked checkbox "crawlingQ" not contained in post
                env.setConfig("crawlingQ", crawlingQ);
                
                boolean followFrames = "on".equals(post.get("followFrames", "false"));
                env.setConfig("followFrames", followFrames);
                
                boolean obeyHtmlRobotsNoindex = "on".equals(post.get("obeyHtmlRobotsNoindex", "false"));
                env.setConfig("obeyHtmlRobotsNoindex", obeyHtmlRobotsNoindex);

                final boolean indexText = "on".equals(post.get("indexText", "false"));
                env.setConfig("indexText", indexText);

                final boolean indexMedia = "on".equals(post.get("indexMedia", "false"));
                env.setConfig("indexMedia", indexMedia);

                env.setConfig("storeHTCache", storeHTCache);
                
                String agentName = post.get("agentName", sb.isIntranetMode() ? ClientIdentification.yacyIntranetCrawlerAgentName : ClientIdentification.yacyInternetCrawlerAgentName);
                ClientIdentification.Agent agent = ClientIdentification.getAgent(agentName);

                CacheStrategy cachePolicy = CacheStrategy.parse(post.get("cachePolicy", "iffresh"));
                if (cachePolicy == null) cachePolicy = CacheStrategy.IFFRESH;

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
                    Set<DigestURL> newRootURLs = new HashSet<DigestURL>();
                    for (DigestURL sitelistURL: rootURLs) {
                        // download document
                        Document scraper;
                        try {
                            scraper = sb.loader.loadDocument(sitelistURL, CacheStrategy.IFFRESH, BlacklistType.CRAWLER, agent);
                            // get links and generate filter
                            for (DigestURL u: scraper.getAnchors()) {
                                newRootURLs.add(u);
                            }
                        } catch (final IOException e) {
                            ConcurrentLog.logException(e);
                        }
                    }
                    rootURLs = newRootURLs;
                    crawlingMode = "url";
                    if ((fullDomain || subPath) && newcrawlingdepth > 0) newcrawlingMustMatch = CrawlProfile.MATCH_ALL_STRING; // to prevent that there is a restriction on the original urls
                }

                // delete all error urls for that domain
                // and all urls for that host from the crawl queue
                Set<String> hosthashes = new HashSet<String>();
                for (DigestURL u : rootURLs) {
                    sb.index.fulltext().remove(u.hash());
                    hosthashes.add(u.hosthash());
                }
                sb.crawlQueues.removeHosts(hosthashes);
                sb.index.fulltext().commit(true);
                
                // compute mustmatch filter according to rootURLs
                if ((fullDomain || subPath) && newcrawlingdepth > 0) {
                    String siteFilter = ".*";
                    if (fullDomain) {
                        siteFilter = CrawlProfile.siteFilter(rootURLs);
                        if (deleteold) {
                            sb.index.fulltext().deleteStaleDomainHashes(hosthashes, deleteageDate);
                        }
                    } else if (subPath) {
                        siteFilter = CrawlProfile.subpathFilter(rootURLs);
                        if (deleteold) {
                            for (DigestURL u: rootURLs) {
                                String basepath = u.toNormalform(true);
                                if (!basepath.endsWith("/")) {int p = basepath.lastIndexOf("/"); if (p > 0) basepath = basepath.substring(0, p + 1);}
                                int count = sb.index.fulltext().remove(basepath, deleteageDate);
                                if (count > 0) ConcurrentLog.info("Crawler_p", "deleted " + count + " documents for host " + u.getHost());
                            }
                        }
                    }
                    if (CrawlProfile.MATCH_ALL_STRING.equals(newcrawlingMustMatch)) {
                        newcrawlingMustMatch = siteFilter;
                    } else if (!CrawlProfile.MATCH_ALL_STRING.equals(siteFilter)) {
                        // combine both
                        newcrawlingMustMatch = "(" + newcrawlingMustMatch + ")|(" + siteFilter + ")";
                    }
                }
                
                // check if the crawl filter works correctly
                try {
                    Pattern mmp = Pattern.compile(newcrawlingMustMatch);
                    for (DigestURL u: rootURLs) {
                        assert mmp.matcher(u.toNormalform(true)).matches() : "pattern " + mmp.toString() + " does not match url " + u.toNormalform(true);
                    }
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
                
                boolean hasCrawlstartDataOK = true;
                // check crawlurl was given in sitecrawl
                if ("url".equals(crawlingMode) && rootURLs.size() == 0) hasCrawlstartDataOK = false;

                // prepare a new crawling profile
                final CrawlProfile profile;
                byte[] handle;
                if (hasCrawlstartDataOK) {
                    profile = new CrawlProfile(
                            crawlName,
                            newcrawlingMustMatch,
                            newcrawlingMustNotMatch,
                            ipMustMatch,
                            ipMustNotMatch,
                            countryMustMatch,
                            crawlerNoDepthLimitMatch,
                            indexUrlMustMatch,
                            indexUrlMustNotMatch,
                            indexContentMustMatch,
                            indexContentMustNotMatch,
                            newcrawlingdepth,
                            directDocByURL,
                            crawlingIfOlder,
                            crawlingDomMaxPages,
                            crawlingQ, followFrames, obeyHtmlRobotsNoindex,
                            indexText,
                            indexMedia,
                            storeHTCache,
                            crawlOrder,
                            cachePolicy,
                            collection,
                            agentName);
                    handle = ASCII.getBytes(profile.handle());

                    // before we fire up a new crawl, we make sure that another crawl with the same name is not running
                    sb.crawler.removeActive(handle);
                    sb.crawler.removePassive(handle);
                    try {
                        sb.crawlQueues.noticeURL.removeByProfileHandle(profile.handle(), 10000);
                    } catch (final SpaceExceededException e1) { }
                } else {
                    profile = null;
                    handle = null;
                }

                // start the crawl
                if ("url".equals(crawlingMode)) {
                    if (rootURLs.size() == 0) {
                        prop.put("info", "5"); //Crawling failed
                        prop.putHTML("info_crawlingURL", "(no url given)");
                        prop.putHTML("info_reasonString", "you must submit at least one crawl url");
                    } else {
                        
                        // stack requests
                        sb.crawler.putActive(handle, profile);
                        final Set<DigestURL> successurls = new HashSet<DigestURL>();
                        final Map<DigestURL,String> failurls = new HashMap<DigestURL, String>();
                        sb.stackURLs(rootURLs, profile, successurls, failurls);

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
                            for (Map.Entry<DigestURL, String> failure: failurls.entrySet()) {
                                sb.crawlQueues.errorURL.push(failure.getKey(), 0, null, FailCategory.FINAL_LOAD_CONTEXT, failure.getValue(), -1);
                                fr.append(failure.getValue()).append('/');
                            }
    
                            prop.put("info", "5"); //Crawling failed
                            prop.putHTML("info_crawlingURL", (post.get("crawlingURL")));
                            prop.putHTML("info_reasonString", fr.toString());
                        }
                        if (successurls.size() > 0) sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                    }
                } else if ("sitemap".equals(crawlingMode)) {
                    try {
                        final DigestURL sitemapURL = sitemapURLStr.indexOf("//") > 0 ? new DigestURL(sitemapURLStr) : new DigestURL(rootURLs.iterator().next(), sitemapURLStr); // fix for relative paths which should not exist but are used anyway
                        sb.crawler.putActive(handle, profile);
                        final SitemapImporter importer = new SitemapImporter(sb, sitemapURL, profile);
                        importer.start();
                        sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                    } catch (final Exception e) {
                        // mist
                        prop.put("info", "6");//Error with url
                        prop.putHTML("info_crawlingStart", sitemapURLStr);
                        prop.putHTML("info_error", e.getMessage());
                        ConcurrentLog.logException(e);
                    }
                } else if ("file".equals(crawlingMode)) {
                    if (post.containsKey("crawlingFile")) {
                        final String crawlingFileContent = post.get("crawlingFile$file", "");
                        try {
                            // check if the crawl filter works correctly
                            Pattern.compile(newcrawlingMustMatch);
                            final ContentScraper scraper = new ContentScraper(new DigestURL(crawlingFile), 10000000);
                            final Writer writer = new TransformerWriter(null, null, scraper, null, false);
                            if (crawlingFile != null && crawlingFile.exists()) {
                                FileUtils.copy(new FileInputStream(crawlingFile), writer);
                            } else {
                                FileUtils.copy(crawlingFileContent, writer);
                            }
                            writer.close();

                            // get links and generate filter
                            final List<AnchorURL> hyperlinks = scraper.getAnchors();
                            if (newcrawlingdepth > 0) {
                                if (fullDomain) {
                                    newcrawlingMustMatch = CrawlProfile.siteFilter(hyperlinks);
                                } else if (subPath) {
                                    newcrawlingMustMatch = CrawlProfile.subpathFilter(hyperlinks);
                                }
                            }

                            sb.crawler.putActive(handle, profile);
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
                            ConcurrentLog.logException(e);
                        }
                        sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                    }
                }
            }
        }

        /*
         *  <input id="customPPM" name="customPPM" type="number" min="10" max="30000" style="width:46px" value="#[customPPMdefault]#" />PPM
            <input id="latencyFactor" name="latencyFactor" type="number" min="0.1" max="3.0" step="0.1" style="width:32px" value="#[latencyFactorDefault]#" />LF
            <input id="MaxSameHostInQueue" name="MaxSameHostInQueue" type="number" min="1" max="30" style="width:32px" value="#[MaxSameHostInQueueDefault]#" />MH            
            <input type="submit" name="crawlingPerformance" value="set" />
            (<a href="/Crawler_p.html?crawlingPerformance=minimum">min</a>/<a href="/Crawler_p.html?crawlingPerformance=maximum">max</a>)
            </td>
         */
        if (post != null && post.containsKey("crawlingPerformance")) {
            final String crawlingPerformance = post.get("crawlingPerformance", "custom");
            final long LCbusySleep1 = sb.getConfigLong(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, 1000L);
            int wantedPPM = (LCbusySleep1 == 0) ? 30000 : (int) (60000L / LCbusySleep1);
            try {
                wantedPPM = post.getInt("customPPM", wantedPPM);
            } catch (final NumberFormatException e) {}
            if ("minimum".equals(crawlingPerformance.toLowerCase())) wantedPPM = 10;
            if ("maximum".equals(crawlingPerformance.toLowerCase())) wantedPPM = 30000;
            
            int wPPM = wantedPPM;
            if ( wPPM <= 0 ) {
                wPPM = 1;
            }
            if ( wPPM >= 30000 ) {
                wPPM = 30000;
            }
            final int newBusySleep = 60000 / wPPM; // for wantedPPM = 10: 6000; for wantedPPM = 1000: 60
            final float loadprereq = wantedPPM <= 10 ? 1.0f : wantedPPM <= 100 ? 2.0f : wantedPPM >= 1000 ? 8.0f : 3.0f;
            
            BusyThread thread;
            
            thread = sb.getThread(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
            if ( thread != null ) {
                sb.setConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, thread.setBusySleep(newBusySleep));
                sb.setConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_LOADPREREQ, thread.setLoadPreReqisite(loadprereq));
                thread.setLoadPreReqisite(loadprereq);
                thread.setIdleSleep(2000);
            }

            float latencyFactor = post.getFloat("latencyFactor", 0.5f);
            int MaxSameHostInQueue = post.getInt("MaxSameHostInQueue", 20);
            env.setConfig(SwitchboardConstants.CRAWLER_LATENCY_FACTOR, latencyFactor);
            env.setConfig(SwitchboardConstants.CRAWLER_MAX_SAME_HOST_IN_QUEUE, MaxSameHostInQueue);
        }

        // performance settings
        final long LCbusySleep = env.getConfigLong(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, 1000L);
        final int LCppm = (int) (60000L / Math.max(1,LCbusySleep));
        prop.put("customPPMdefault", Integer.toString(LCppm));
        prop.put("latencyFactorDefault", env.getConfigFloat(SwitchboardConstants.CRAWLER_LATENCY_FACTOR, 0.5f));
        prop.put("MaxSameHostInQueueDefault", env.getConfigInt(SwitchboardConstants.CRAWLER_MAX_SAME_HOST_IN_QUEUE, 20));

        // generate crawl profile table
        int count = 0;
        boolean dark = true;
        final int domlistlength = (post == null) ? 160 : post.getInt("domlistlength", 160);
        CrawlProfile profile;
        // put active crawls into list
        String hosts = "";
        for (final byte[] h: sb.crawler.getActive()) {
            profile = sb.crawler.getActive(h);
        	if (CrawlSwitchboard.DEFAULT_PROFILES.contains(profile.name())) continue;
            profile.putProfileEntry("crawlProfilesShow_list_", prop, true, dark, count, domlistlength);
            prop.put("crawlProfilesShow_list_" + count + "_debug", debug ? 1 : 0);
            if (debug) {
                RowHandleSet urlhashes = sb.crawler.getURLHashes(h);
                prop.put("crawlProfilesShow_list_" + count + "_debug_count", urlhashes == null ? "unknown" : Integer.toString(urlhashes.size()));
            }
            hosts = hosts + "," + profile.name();
            dark = !dark;
            count++;
        }
        prop.put("crawlProfilesShow_debug", debug ? 1 : 0);
        prop.put("crawlProfilesShow_list", count);
        prop.put("crawlProfilesShow_count", count);
        prop.put("crawlProfilesShow", count == 0 ? 0 : 1);

        prop.put("crawlProfilesShow_linkstructure", 0);
        if (count > 0) {
            // collect the host names for 'wide' crawls which can be visualized
            boolean showLinkstructure = hosts.length() > 0;
            if (showLinkstructure) {
                StringBuilder q = new StringBuilder();
                hosts = hosts.substring(1);
                q.append(CollectionSchema.host_s.getSolrFieldName()).append(':').append(hosts).append(" OR ").append(CollectionSchema.host_s.getSolrFieldName()).append(':').append("www.").append(hosts);
                try {
                    prop.put("crawlProfilesShow_linkstructure", count == 1 && sb.index.fulltext().getDefaultConnector().getCountByQuery(q.toString()) > 0 ? 1 : 2);
                    prop.put("crawlProfilesShow_linkstructure_hosts", hosts);
                } catch (IOException e) {
                }
            }
        }

        // return rewrite properties
        return prop;
    }

    private static long timeParser(final boolean recrawlIfOlderCheck, final int number, final String unit) {
        if (!recrawlIfOlderCheck) return 0L;
        if ("year".equals(unit)) return System.currentTimeMillis() - number * 1000L * 60L * 60L * 24L * 365L;
        if ("month".equals(unit)) return System.currentTimeMillis() - number * 1000L * 60L * 60L * 24L * 30L;
        if ("day".equals(unit)) return System.currentTimeMillis() - number * 1000L * 60L * 60L * 24L;
        if ("hour".equals(unit)) return System.currentTimeMillis() - number * 1000L * 60L * 60L;
        if ("minute".equals(unit)) return System.currentTimeMillis() - number * 1000L * 60L;
        return 0L;
    }

}
