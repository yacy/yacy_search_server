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

package net.yacy.htroot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.solr.common.SolrException;
import org.apache.solr.core.SolrCore;
import org.apache.solr.search.SyntaxError;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.cora.date.AbstractFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.FailCategory;
import net.yacy.cora.federate.solr.instance.EmbeddedInstance;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.CrawlSwitchboard;
import net.yacy.crawler.FileCrawlStarterTask;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.CrawlProfile.CrawlAttribute;
import net.yacy.crawler.data.NoticedURL.StackType;
import net.yacy.crawler.retrieval.SitemapImporter;
import net.yacy.data.WorkTables;
import net.yacy.document.Document;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.TagValency;
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
import net.yacy.search.index.SingleDocumentMatcher;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/**
 * This servlet does NOT create the Crawler servlet page content! This controls
 * a web crawl start or the crawl monitor page (Crawler_p.html). The interfaces for entering the web crawl parameters are
 * in CrawlStartSite.html and CrawlStartExpert.html.
 */
public class Crawler_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;

        // inital values for AJAX Elements (without JavaScript)
        final serverObjects prop = new serverObjects();
        prop.put("rejected", 0);

        // check for JSONP
        if (post != null && post.containsKey("callback") ) {
            final String jsonp = post.get("callback") + "([";
            prop.put("jsonp-start", jsonp);
            prop.put("jsonp-end", "])");
        } else {
            prop.put("jsonp-start", "");
            prop.put("jsonp-end", "");
        }

        final Segment segment = sb.index;
        final Fulltext fulltext = segment.fulltext();
        final String localSolr = "solr/select?core=collection1&q=*:*&start=0&rows=3";
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

        final int coreCrawlJobSize = sb.crawlQueues.coreCrawlJobSize();
        final int limitCrawlJobSize = sb.crawlQueues.limitCrawlJobSize();
        final int remoteTriggeredCrawlJobSize = sb.crawlQueues.remoteTriggeredCrawlJobSize();
        final int noloadCrawlJobSize = sb.crawlQueues.noloadCrawlJobSize();
        final int allsize = coreCrawlJobSize + limitCrawlJobSize + remoteTriggeredCrawlJobSize + noloadCrawlJobSize;

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
        final boolean debug = (post != null && post.containsKey("debug"));

        if (post != null) {
            final String c = post.toString();
            if (c.length() < 1000) ConcurrentLog.info("Crawl Start", c);
        }

        if (post != null && post.containsKey("queues_terminate_all")) {
            // terminate crawls individually
            sb.crawlQueues.noticeURL.clear();
            for (final byte[] h: sb.crawler.getActive()) {
                final CrawlProfile p = sb.crawler.getActive(h);
                if (CrawlSwitchboard.DEFAULT_PROFILES.contains(p.name())) continue;
                if (p != null) sb.crawler.putPassive(h, p);
                sb.crawler.removeActive(h);
                sb.crawler.removePassive(h);
                try {sb.crawlQueues.noticeURL.removeByProfileHandle(p.handle(), 10000);} catch (final SpaceExceededException e) {}
            }

            // clear stacks
            for (final StackType stackType: StackType.values()) sb.crawlQueues.noticeURL.clear(stackType);
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
        final String queuemessage = sb.getConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL + "_isPaused_cause", "");
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

                if(post.getBoolean("cleanSearchCache")) {
                    // clean up all search events
                    SearchEventCache.cleanupEvents(true);
                    sb.index.clearCaches(); // every time the ranking is changed we need to remove old orderings
                }

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
                boolean fullDomain = "domain".equals(post.get("range", "wide")); // special property in simple crawl start
                boolean subPath    = "subpath".equals(post.get("range", "wide")); // special property in simple crawl start

                final boolean restrictedcrawl = fullDomain || subPath || !CrawlProfile.MATCH_ALL_STRING.equals(newcrawlingMustMatch);
                final boolean deleteage = restrictedcrawl && "age".equals(post.get("deleteold","off"));
                Date deleteageDate = null;
                if (deleteage) {
                    deleteageDate = timeParser(true, post.getInt("deleteIfOlderNumber", -1), post.get("deleteIfOlderUnit","year")); // year, month, day, hour
                }
                final boolean deleteold = (deleteage && deleteageDate != null) || (restrictedcrawl && post.getBoolean("deleteold"));

                final String sitemapURLStr = post.get("sitemapURL","");
                final String crawlingStart0 = post.get("crawlingURL","").trim(); // the crawljob start url
                final String[] rootURLs0 = crawlingStart0.indexOf('\n') > 0 || crawlingStart0.indexOf('\r') > 0 ? crawlingStart0.split("[\\r\\n]+") : crawlingStart0.split(Pattern.quote("|"));
                final List<DigestURL> rootURLs = new ArrayList<>();
                String crawlName = "";
                if (crawlingFile == null) {
                    final StringBuilder crawlNameBuilder = new StringBuilder(); // for large crawl queues this can be pretty large
                    for (String crawlingStart: rootURLs0) {
                        if (crawlingStart == null || crawlingStart.length() == 0) continue;
                        // add the prefix http:// if necessary
                        final int pos = crawlingStart.indexOf("://",0);
                        if (pos == -1) {
                            if (crawlingStart.startsWith("ftp")) crawlingStart = "ftp://" + crawlingStart; else crawlingStart = "https://" + crawlingStart; // we default to https instead of http becuase those outnumber http by far
                        }
                        try {
                            final DigestURL crawlingStartURL = new DigestURL(crawlingStart);
                            rootURLs.add(crawlingStartURL);
                            crawlNameBuilder.append((crawlingStartURL.getHost() == null) ? crawlingStartURL.toNormalform(true) : crawlingStartURL.getHost()).append(',');
                            if (crawlingStartURL != null && (crawlingStartURL.isFile() || crawlingStartURL.isSMB())) storeHTCache = false;
                        } catch (final MalformedURLException e) {
                            ConcurrentLog.warn("Crawler_p", "crawl start url invalid: " + e.getMessage());
                        }
                    }
                    crawlName = crawlNameBuilder.toString();
                } else {
                    crawlName = crawlingFile.getName();
                }
                if (crawlName.endsWith(",")) crawlName = crawlName.substring(0, crawlName.length() - 1);
                if (crawlName.length() > 64) {
                    crawlName = "crawl_for_" + rootURLs.size() + "_start_points_" + Integer.toHexString(crawlName.hashCode());
                    final int p = crawlName.lastIndexOf(',');
                    if (p >= 8) crawlName = crawlName.substring(0, p);
                }
                if (crawlName.length() == 0 && sitemapURLStr.length() > 0) crawlName = "sitemap loader for " + sitemapURLStr;
                // in case that a root url has a file protocol, then the site filter does not work, patch that:
                if (fullDomain) {
                    for (final DigestURL u: rootURLs) if (u.isFile()) {fullDomain = false; subPath = true; break;}
                }

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
                final boolean noindexWhenCanonicalUnequalURL = "on".equals(post.get("noindexWhenCanonicalUnequalURL", "off"));

                final boolean crawlOrder = post.get("crawlOrder", "off").equals("on");
                env.setConfig("crawlOrder", crawlOrder);

                if (crawlOrder) crawlerNoDepthLimitMatch = CrawlProfile.MATCH_NEVER_STRING; // without limitation the crawl order does not work

                int newcrawlingdepth = post.getInt("crawlingDepth", 8);
                env.setConfig("crawlingDepth", Integer.toString(newcrawlingdepth));
                if ((crawlOrder) && (newcrawlingdepth > 8)) newcrawlingdepth = 8;

                boolean directDocByURL = "on".equals(post.get("directDocByURL", "off")); // catch also all linked media documents even when no parser is available
                env.setConfig("crawlingDirectDocByURL", directDocByURL);

                final String collection = post.get("collection", "user");
                env.setConfig("collection", collection);

                // recrawl
                final String recrawl = post.get("recrawl", "nodoubles"); // nodoubles, reload, scheduler
                Date crawlingIfOlder = null;
                if ("reload".equals(recrawl)) {
                    crawlingIfOlder = timeParser(true, post.getInt("reloadIfOlderNumber", -1), post.get("reloadIfOlderUnit","year")); // year, month, day, hour
                }
                env.setConfig("crawlingIfOlder", crawlingIfOlder == null ? Long.MAX_VALUE : crawlingIfOlder.getTime());

                // store this call as api call
                sb.tables.recordAPICall(post, "Crawler_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "crawl start for " + ((rootURLs.size() == 0) ? post.get("crawlingFile", "") : rootURLs.iterator().next().toNormalform(true)));

                final boolean crawlingDomMaxCheck = "on".equals(post.get("crawlingDomMaxCheck", "off"));
                final int crawlingDomMaxPages = (crawlingDomMaxCheck) ? post.getInt("crawlingDomMaxPages", -1) : -1;
                env.setConfig("crawlingDomMaxPages", Integer.toString(crawlingDomMaxPages));

                final boolean followFrames = "on".equals(post.get("followFrames", "false"));
                env.setConfig("followFrames", followFrames);

                final boolean obeyHtmlRobotsNoindex = "on".equals(post.get("obeyHtmlRobotsNoindex", "false"));
                env.setConfig("obeyHtmlRobotsNoindex", obeyHtmlRobotsNoindex);

                final boolean obeyHtmlRobotsNofollow = "on".equals(post.get("obeyHtmlRobotsNofollow", "false"));
                env.setConfig("obeyHtmlRobotsNofollow", obeyHtmlRobotsNofollow);

                final boolean indexText = "on".equals(post.get("indexText", "on"));
                env.setConfig("indexText", indexText);

                final boolean indexMedia = "on".equals(post.get("indexMedia", "false"));
                env.setConfig("indexMedia", indexMedia);

                env.setConfig("storeHTCache", storeHTCache);

                final String defaultAgentName = sb.isIntranetMode() ? ClientIdentification.yacyIntranetCrawlerAgentName : ClientIdentification.yacyInternetCrawlerAgentName;
                final String agentName = post.get("agentName", defaultAgentName);
                ClientIdentification.Agent agent = ClientIdentification.getAgent(agentName);
                if (agent == null) agent = ClientIdentification.getAgent(defaultAgentName);

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
                }

                if ("sitelist".equals(crawlingMode)) {
                    newcrawlingMustNotMatch = CrawlProfile.MATCH_NEVER_STRING;
                    final List<DigestURL> newRootURLs = new ArrayList<>();
                    for (final DigestURL sitelistURL: rootURLs) {
                        // download document
                        Document scraper;
                        try {
                            scraper = sb.loader.loadDocument(sitelistURL, CacheStrategy.IFFRESH, BlacklistType.CRAWLER, agent);
                            // get links and generate filter
                            for (final DigestURL u: scraper.getHyperlinks().keySet()) {
                                newRootURLs.add(u);
                            }
                        } catch (final IOException e) {
                            ConcurrentLog.logException(e);
                        }
                    }
                    rootURLs.clear();
                    rootURLs.addAll(newRootURLs);
                    crawlingMode = "url";
                    if ((fullDomain || subPath) && newcrawlingdepth > 0) newcrawlingMustMatch = CrawlProfile.MATCH_ALL_STRING; // to prevent that there is a restriction on the original urls
                }

                // delete all error urls for that domain
                // and all urls for that host from the crawl queue
                final List<String> deleteIDs = new ArrayList<>();
                final Set<String> hosthashes = new HashSet<>();
                boolean anysmbftporpdf = false;
                for (final DigestURL u : rootURLs) {
                    deleteIDs.add(new String(u.hash()));
                    hosthashes.add(u.hosthash());
                    if ("smb.ftp".indexOf(u.getProtocol()) >= 0 || "pdf".equals(MultiProtocolURL.getFileExtension(u.getFileName()))) anysmbftporpdf = true;
                }
                sb.index.fulltext().remove(deleteIDs);
                deleteIDs.forEach(urlhash -> {try {sb.index.loadTimeIndex().remove(urlhash.getBytes());} catch (final IOException e) {}});
                sb.crawlQueues.removeHosts(hosthashes);
                sb.index.fulltext().commit(true);

                final boolean crawlingQ = anysmbftporpdf || "on".equals(post.get("crawlingQ", "off")) || "sitemap".equals(crawlingMode);
                env.setConfig("crawlingQ", crawlingQ);

                // compute mustmatch filter according to rootURLs
                if ((fullDomain || subPath) && newcrawlingdepth > 0) {
                    String siteFilter = ".*";
                    if (fullDomain) {
                        siteFilter = CrawlProfile.siteFilter(rootURLs);
                        if (deleteold) {
                            sb.index.fulltext().deleteStaleDomainHashes(hosthashes, deleteageDate); // takes long time for long lists
                        }
                    } else if (subPath) {
                        siteFilter = CrawlProfile.subpathFilter(rootURLs);
                        if (deleteold) {
                            for (final DigestURL u: rootURLs) {
                                String basepath = u.toNormalform(true);
                                if (!basepath.endsWith("/")) {final int p = basepath.lastIndexOf("/"); if (p > 0) basepath = basepath.substring(0, p + 1);}
                                final int count = sb.index.fulltext().remove(basepath, deleteageDate);
                                try {sb.index.loadTimeIndex().clear();} catch (final IOException e) {}
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
                    final Pattern mmp = Pattern.compile(newcrawlingMustMatch);
                    int maxcheck = 100;
                    for (final DigestURL u: rootURLs) {
                        assert mmp.matcher(u.toNormalform(true)).matches() : "pattern " + mmp.toString() + " does not match url " + u.toNormalform(true);
                        if (maxcheck-- <= 0) break;
                    }
                } catch (final PatternSyntaxException e) {
                    prop.put("info", "4"); // crawlfilter does not match url
                    prop.putHTML("info_newcrawlingfilter", newcrawlingMustMatch);
                    prop.putHTML("info_error", e.getMessage());
                }

                boolean hasCrawlstartDataOK = !crawlName.isEmpty();
                if (hasCrawlstartDataOK) {
                    // check crawlurl was given in sitecrawl
                    if ("url".equals(crawlingMode) && rootURLs.size() == 0) {
                        prop.put("info", "5"); //Crawling failed
                        prop.putHTML("info_crawlingURL", "(no url given)");
                        prop.putHTML("info_reasonString", "you must submit at least one crawl url");
                        hasCrawlstartDataOK = false;
                    }
                }

                final String snapshotsMaxDepthString = post.get("snapshotsMaxDepth", "-1");
                final int snapshotsMaxDepth = Integer.parseInt(snapshotsMaxDepthString);
                final boolean snapshotsLoadImage = post.getBoolean("snapshotsLoadImage");
                final boolean snapshotsReplaceOld = post.getBoolean("snapshotsReplaceOld");
                final String snapshotsMustnotmatch = post.get("snapshotsMustnotmatch", "");

                final String valency_switch_tag_names_s = post.get("valency_switch_tag_names");
                final Set<String> valency_switch_tag_names = new HashSet<>();
                if (valency_switch_tag_names_s != null) {
                    final String[] valency_switch_tag_name_a = valency_switch_tag_names_s.trim().split(",");
                    for (int i = 0; i < valency_switch_tag_name_a.length; i++) {
                        valency_switch_tag_names.add(valency_switch_tag_name_a[i].trim());
                    }
                }
                final String default_valency_radio = post.get("default_valency");
                TagValency default_valency = TagValency.EVAL;
                if (default_valency_radio != null && default_valency_radio.equals("IGNORE")) {
                    default_valency = TagValency.IGNORE;
                }

                // get vocabulary scraper info
                final JSONObject vocabulary_scraper = new JSONObject(); // key = vocabulary_name, value = properties with key = type (i.e. 'class') and value = keyword in context
                for (final String key: post.keySet()) {
                    if (key.startsWith("vocabulary_")) {
                        if (key.endsWith("_class")) {
                            final String vocabulary = key.substring(11, key.length() - 6);
                            final String value = post.get(key);
                            if (value != null && value.length() > 0) {
                                JSONObject props;
                                try {
                                    props = vocabulary_scraper.getJSONObject(vocabulary);
                                } catch (final JSONException e) {
                                    props = new JSONObject();
                                    try {
                                        vocabulary_scraper.put(vocabulary, props);
                                    } catch (final JSONException ee) {}
                                }
                                try {
                                    props.put("class", value);
                                } catch (final JSONException e) {
                                }
                            }
                        }
                    }
                }

                final int timezoneOffset = post.getInt("timezoneOffset", 0);

                // in case that we crawl from a file, load that file and re-compute mustmatch pattern
                List<AnchorURL> hyperlinks_from_file = null;
                if ("file".equals(crawlingMode) && post.containsKey("crawlingFile") && crawlingFile != null) {
                    final String crawlingFileContent = post.get("crawlingFile$file", "");
                    try {
                        if (newcrawlingdepth > 0) {
                            if (fullDomain) {
                                /* Crawl is restricted to start domains or sub-paths : we have to get all the start links now.
                                 * Otherwise we can get them asynchronously later, thus allowing to handle more efficiently large start crawlingFiles */
                                hyperlinks_from_file = crawlingFileStart(crawlingFile, timezoneOffset, crawlingFileContent);
                                newcrawlingMustMatch = CrawlProfile.siteFilter(hyperlinks_from_file);
                            } else if (subPath) {
                                /* Crawl is restricted to start domains or sub-paths : we have to get all the start links now.
                                 * Otherwise we can get them asynchronously later, thus allowing to handle more efficiently large start crawlingFiles */
                                hyperlinks_from_file = crawlingFileStart(crawlingFile, timezoneOffset, crawlingFileContent);
                                newcrawlingMustMatch = CrawlProfile.subpathFilter(hyperlinks_from_file);
                            }
                        }
                    } catch (final Exception e) {
                        // mist
                        prop.put("info", "7"); // Error with file
                        prop.putHTML("info_crawlingStart", crawlingFileName);
                        prop.putHTML("info_error", e.getMessage());
                        ConcurrentLog.logException(e);
                    }
                    sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                }

                /* If a solr query filter is defined, verify now its syntax and that the embedded Solr schema is available */
                final String solrQueryMustMatch = post.get(CrawlAttribute.INDEXING_SOLR_QUERY_MUSTMATCH.key, CrawlProfile.SOLR_MATCH_ALL_QUERY).trim();
                final String solrQueryMustNotMatch = post.get(CrawlAttribute.INDEXING_SOLR_QUERY_MUSTNOTMATCH.key, CrawlProfile.SOLR_EMPTY_QUERY).trim();
                if(!(solrQueryMustMatch.isEmpty() || CrawlProfile.SOLR_MATCH_ALL_QUERY.equals(solrQueryMustMatch)) || !CrawlProfile.SOLR_EMPTY_QUERY.equals(solrQueryMustNotMatch)) {

                    final EmbeddedInstance embeddedSolr = sb.index.fulltext().getEmbeddedInstance();
                    final SolrCore embeddedCore = embeddedSolr != null ? embeddedSolr.getDefaultCore() : null;
                    final boolean embeddedSolrConnected = embeddedSolr != null && embeddedCore != null;
                    prop.put("noEmbeddedSolr", !embeddedSolrConnected);
                    if (embeddedSolrConnected) {
                        if(!(solrQueryMustMatch.isEmpty() || CrawlProfile.SOLR_MATCH_ALL_QUERY.equals(solrQueryMustMatch))) {
                            try {
                                SingleDocumentMatcher.toLuceneQuery(solrQueryMustMatch, embeddedCore);
                            } catch(final SyntaxError | SolrException e) {
                                hasCrawlstartDataOK = false;
                                prop.put("info", "10");
                                prop.put("info_solrQuery", solrQueryMustMatch);
                            } catch(final RuntimeException e) {
                                hasCrawlstartDataOK = false;
                                prop.put("info", "11");
                                prop.put("info_solrQuery", solrQueryMustMatch);
                            }
                        }

                        if(!CrawlProfile.SOLR_EMPTY_QUERY.equals(solrQueryMustNotMatch)) {
                            try {
                                SingleDocumentMatcher.toLuceneQuery(solrQueryMustNotMatch, embeddedCore);
                            } catch(final SyntaxError | SolrException e) {
                                hasCrawlstartDataOK = false;
                                prop.put("info", "10");
                                prop.put("info_solrQuery", solrQueryMustNotMatch);
                            } catch(final RuntimeException e) {
                                hasCrawlstartDataOK = false;
                                prop.put("info", "11");
                                prop.put("info_solrQuery", solrQueryMustNotMatch);
                            }
                        }
                    } else {
                        hasCrawlstartDataOK = false;
                        prop.put("info", "9");
                    }
                }

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
                            noindexWhenCanonicalUnequalURL,
                            newcrawlingdepth,
                            directDocByURL,
                            crawlingIfOlder,
                            crawlingDomMaxPages,
                            crawlingQ, followFrames,
                            obeyHtmlRobotsNoindex, obeyHtmlRobotsNofollow,
                            indexText,
                            indexMedia,
                            storeHTCache,
                            crawlOrder,
                            snapshotsMaxDepth,
                            snapshotsLoadImage,
                            snapshotsReplaceOld,
                            snapshotsMustnotmatch,
                            cachePolicy,
                            collection,
                            agentName,
                            default_valency,
                            valency_switch_tag_names,
                            new VocabularyScraper(vocabulary_scraper),
                            timezoneOffset);

                    profile.put(CrawlAttribute.CRAWLER_ORIGIN_URL_MUSTMATCH.key,
                            post.get(CrawlAttribute.CRAWLER_ORIGIN_URL_MUSTMATCH.key, CrawlProfile.MATCH_ALL_STRING));
                    profile.put(CrawlAttribute.CRAWLER_ORIGIN_URL_MUSTNOTMATCH.key, post
                            .get(CrawlAttribute.CRAWLER_ORIGIN_URL_MUSTNOTMATCH.key, CrawlProfile.MATCH_NEVER_STRING));
                    profile.put(CrawlAttribute.INDEXING_MEDIA_TYPE_MUSTMATCH.key,
                            post.get(CrawlAttribute.INDEXING_MEDIA_TYPE_MUSTMATCH.key, CrawlProfile.MATCH_ALL_STRING));
                    profile.put(CrawlAttribute.INDEXING_MEDIA_TYPE_MUSTNOTMATCH.key, post
                            .get(CrawlAttribute.INDEXING_MEDIA_TYPE_MUSTNOTMATCH.key, CrawlProfile.MATCH_NEVER_STRING));
                    profile.put(CrawlAttribute.INDEXING_SOLR_QUERY_MUSTMATCH.key, solrQueryMustMatch);
                    profile.put(CrawlAttribute.INDEXING_SOLR_QUERY_MUSTNOTMATCH.key, solrQueryMustNotMatch);
                    profile.put(CrawlAttribute.CRAWLER_ALWAYS_CHECK_MEDIA_TYPE.key,
                            post.getBoolean("crawlerAlwaysCheckMediaType"));

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
                if (hasCrawlstartDataOK) {
                    final boolean wontReceiptRemoteRsults = crawlOrder && !sb.getConfigBool(SwitchboardConstants.CRAWLJOB_REMOTE, false);

                    if ("url".equals(crawlingMode)) {
                        // stack requests
                        sb.crawler.putActive(handle, profile);
                        final Set<DigestURL> successurls = new HashSet<>();
                        final Map<DigestURL,String> failurls = new HashMap<>();
                        sb.stackURLs(rootURLs, profile, successurls, failurls);

                        if (failurls.size() == 0) {
                            // liftoff!
                            prop.put("info", "8");
                            prop.putHTML("info_crawlingURL", post.get("crawlingURL"));

                            // generate a YaCyNews if the global flag was set
                            if (!sb.isRobinsonMode() && crawlOrder) {
                                final Map<String, String> m = new HashMap<>(profile); // must be cloned
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
                                if (successurls.size() > 0) { // just include at least one of the startURL's in case of multiple for the news service
                                    m.put("startURL", successurls.iterator().next().toNormalform(true));
                                }
                                sb.peers.newsPool.publishMyNews(sb.peers.mySeed(), NewsPool.CATEGORY_CRAWL_START, m);
                            }
                        } else {
                            final StringBuilder fr = new StringBuilder();
                            for (final Map.Entry<DigestURL, String> failure: failurls.entrySet()) {
                                sb.crawlQueues.errorURL.push(failure.getKey(), 0, null, FailCategory.FINAL_LOAD_CONTEXT, failure.getValue(), -1);
                                fr.append(failure.getValue()).append('/');
                            }

                            prop.put("info", "5"); //Crawling failed
                            prop.putHTML("info_crawlingURL", (post.get("crawlingURL")));
                            prop.putHTML("info_reasonString", fr.toString());
                        }
                        if (successurls.size() > 0) {
                            sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                            prop.put("wontReceiptRemoteResults", wontReceiptRemoteRsults);
                        }
                    } else if ("sitemap".equals(crawlingMode)) {
                        try {
                            final DigestURL sitemapURL = sitemapURLStr.indexOf("//") > 0 ? new DigestURL(sitemapURLStr) : new DigestURL(rootURLs.iterator().next(), sitemapURLStr); // fix for relative paths which should not exist but are used anyway
                            sb.crawler.putActive(handle, profile);
                            final SitemapImporter importer = new SitemapImporter(sb, sitemapURL, profile);
                            importer.start();
                            sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                            prop.put("wontReceiptRemoteResults", wontReceiptRemoteRsults);
                        } catch (final Exception e) {
                            // mist
                            prop.put("info", "6");//Error with url
                            prop.putHTML("info_crawlingStart", sitemapURLStr);
                            prop.putHTML("info_error", e.getMessage());
                            ConcurrentLog.logException(e);
                        }
                    } else if ("file".equals(crawlingMode)) {
                        if (post.containsKey("crawlingFile") && crawlingFile != null) {
                            try {
                                if(newcrawlingdepth > 0 && (fullDomain || subPath)) {
                                    /* All links must have already been loaded because they are the part of the newcrawlingMustMatch filter */
                                    if(hyperlinks_from_file != null) {
                                        sb.crawler.putActive(handle, profile);
                                        sb.crawlStacker.enqueueEntriesAsynchronous(sb.peers.mySeed().hash.getBytes(), profile.handle(), hyperlinks_from_file, profile.timezoneOffset());
                                    }
                                } else {
                                    /* No restriction on domains or subpath : we scrape now links and asynchronously push them to the crawlStacker */
                                    final String crawlingFileContent = post.get("crawlingFile$file", "");
                                    final ContentScraper scraper = new ContentScraper(
                                            new DigestURL(crawlingFile),
                                            10000000,
                                            new HashSet<String>(),
                                            TagValency.EVAL,
                                            new VocabularyScraper(),
                                            profile.timezoneOffset());
                                    final FileCrawlStarterTask crawlStarterTask = new FileCrawlStarterTask(crawlingFile, crawlingFileContent, scraper, profile,
                                            sb.crawlStacker, sb.peers.mySeed().hash.getBytes());
                                    sb.crawler.putActive(handle, profile);
                                    crawlStarterTask.start();
                                }
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
                            prop.put("wontReceiptRemoteResults", wontReceiptRemoteRsults);
                        }
                    }
                }
            }
        }

        /*
         *  <input id="customPPM" name="customPPM" type="number" min="10" max="60000" style="width:46px" value="#[customPPMdefault]#" />PPM
            <input id="latencyFactor" name="latencyFactor" type="number" min="0.1" max="3.0" step="0.1" style="width:32px" value="#[latencyFactorDefault]#" />LF
            <input id="MaxSameHostInQueue" name="MaxSameHostInQueue" type="number" min="1" max="30" style="width:32px" value="#[MaxSameHostInQueueDefault]#" />MH
            <input type="submit" name="crawlingPerformance" value="set" />
            (<a href="/Crawler_p.html?crawlingPerformance=minimum">min</a>/<a href="/Crawler_p.html?crawlingPerformance=maximum">max</a>)
            </td>
         */
        if (post != null && post.containsKey("crawlingPerformance")) {
            final String crawlingPerformance = post.get("crawlingPerformance", "custom");
            final long LCbusySleep1 = sb.getConfigLong(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, 1000L);
            int wantedPPM = (LCbusySleep1 == 0) ? 60000 : (int) (60000L / LCbusySleep1);
            try {
                wantedPPM = post.getInt("customPPM", wantedPPM);
            } catch (final NumberFormatException e) {}
            if ("minimum".equals(crawlingPerformance.toLowerCase(Locale.ROOT))) wantedPPM = 10;
            if ("maximum".equals(crawlingPerformance.toLowerCase(Locale.ROOT))) wantedPPM = 60000;

            int wPPM = wantedPPM;
            if ( wPPM <= 0 ) {
                wPPM = 1;
            }
            if ( wPPM >= 60000 ) {
                wPPM = 60000;
            }
            final int newBusySleep = 60000 / wPPM; // for wantedPPM = 10: 6000; for wantedPPM = 1000: 60

            // we must increase the load limit because a conservative load limit will prevent a high crawling speed
            // however this must not cause that the load limit is reduced again because that may go against the users requirements
            // in case they set the limit themself, see https://github.com/yacy/yacy_search_server/issues/363
            float numberOfCores2 = 2.0f * (float) Runtime.getRuntime().availableProcessors();
            float loadprereq = wantedPPM <= 10 ? 1.0f : wantedPPM <= 100 ? 2.0f : wantedPPM >= 1000 ? numberOfCores2 : 3.0f;
            loadprereq = Math.max(loadprereq, sb.getConfigFloat(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_LOADPREREQ, loadprereq));

            BusyThread thread;
            thread = sb.getThread(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
            if ( thread != null ) {
                sb.setConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, thread.setBusySleep(newBusySleep));
                sb.setConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_LOADPREREQ, thread.setLoadPreReqisite(loadprereq));
                thread.setLoadPreReqisite(loadprereq);
                thread.setIdleSleep(2000);
            }

            final float latencyFactor = post.getFloat("latencyFactor", 0.5f);
            final int MaxSameHostInQueue = post.getInt("MaxSameHostInQueue", 20);
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
                final RowHandleSet urlhashes = sb.crawler.getURLHashes(h);
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

        if (post != null) { // handle config button to display graphic
            if (post.get("hidewebstructuregraph") != null) sb.setConfig(SwitchboardConstants.DECORATION_GRAFICS_LINKSTRUCTURE, false);
            if (post.get("showwebstructuregraph") != null) sb.setConfig(SwitchboardConstants.DECORATION_GRAFICS_LINKSTRUCTURE, true);
        }
        if (count > 0 && sb.getConfigBool(SwitchboardConstants.DECORATION_GRAFICS_LINKSTRUCTURE, true)) {
            // collect the host names for 'wide' crawls which can be visualized
            final boolean showLinkstructure = hosts.length() > 0 && !hosts.contains("file:");
            if (showLinkstructure) {
                final StringBuilder q = new StringBuilder();
                hosts = hosts.substring(1);
                q.append(CollectionSchema.host_s.getSolrFieldName()).append(':').append(hosts).append(" OR ").append(CollectionSchema.host_s.getSolrFieldName()).append(':').append("www.").append(hosts);
                try {
                    prop.put("crawlProfilesShow_linkstructure", count == 1 && sb.index.fulltext().getDefaultConnector().getCountByQuery(q.toString()) > 0 ? 1 : 2);
                    prop.put("crawlProfilesShow_linkstructure_hosts", hosts);
                } catch (final IOException e) {
                }
            }
        }

        // return rewrite properties
        return prop;
    }

    /**
     * Scrape crawlingFile or crawlingFileContent and get all anchor links from it.
     * @param crawlingFile crawl start file (must not be null)
     * @param timezoneOffset local timezone offset
     * @param crawlingFileContent content of the crawling file (optional : used only when crawlingFile does no exists)
     * @return all the anchor links from the crawling file
     * @throws MalformedURLException
     * @throws IOException
     * @throws FileNotFoundException
     */
    private static List<AnchorURL> crawlingFileStart(final File crawlingFile, final int timezoneOffset,
            final String crawlingFileContent) throws MalformedURLException, IOException, FileNotFoundException {
        List<AnchorURL> hyperlinks_from_file;
        // check if the crawl filter works correctly
        final ContentScraper scraper = new ContentScraper(new DigestURL(crawlingFile), 10000000, new HashSet<String>(), TagValency.EVAL, new VocabularyScraper(), timezoneOffset);
        final Writer writer = new TransformerWriter(null, null, scraper, false);
        if((crawlingFileContent == null || crawlingFileContent.isEmpty()) && crawlingFile != null) {
            /* Let's report here detailed error to help user when he selected a wrong file */
            if(!crawlingFile.exists()) {
                writer.close();
                throw new FileNotFoundException(crawlingFile.getAbsolutePath() +  " does not exists");
            }
            if(!crawlingFile.isFile()) {
                writer.close();
                throw new FileNotFoundException(crawlingFile.getAbsolutePath() +  " exists but is not a regular file");
            }
            if(!crawlingFile.canRead()) {
                writer.close();
                throw new IOException("Can not read : " + crawlingFile.getAbsolutePath());
            }
        }
        if (crawlingFile != null) {
            FileInputStream inStream = null;
            try {
                inStream = new FileInputStream(crawlingFile);
                FileUtils.copy(inStream, writer);
            } finally {
                if(inStream != null) {
                    try {
                        inStream.close();
                    } catch(final IOException ignoredException) {
                        ConcurrentLog.info("Crawler_p", "Could not close crawlingFile : " + crawlingFile.getAbsolutePath());
                    }
                }
            }
        } else {
            FileUtils.copy(crawlingFileContent, writer);
        }
        writer.close();

        // get links and generate filter
        hyperlinks_from_file = scraper.getAnchors();
        return hyperlinks_from_file;
    }

    private static Date timeParser(final boolean recrawlIfOlderCheck, final int number, final String unit) {
        if (!recrawlIfOlderCheck) return null;
        if ("year".equals(unit)) return new Date(System.currentTimeMillis() - number * AbstractFormatter.normalyearMillis);
        if ("month".equals(unit)) return new Date(System.currentTimeMillis() - number * AbstractFormatter.monthAverageMillis);
        if ("day".equals(unit)) return new Date(System.currentTimeMillis() - number * AbstractFormatter.dayMillis);
        if ("hour".equals(unit)) return new Date(System.currentTimeMillis() - number * AbstractFormatter.hourMillis);
        if ("minute".equals(unit)) return new Date(System.currentTimeMillis() - number * AbstractFormatter.minuteMillis);
        return null;
    }

}
