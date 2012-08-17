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
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.services.federated.yacy.CacheStrategy;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.document.Document;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.TransformerWriter;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.peers.NewsPool;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Segment;
import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.CrawlQueues;
import de.anomic.crawler.SitemapImporter;
import de.anomic.crawler.ZURL.FailCategory;
import de.anomic.crawler.retrieval.Request;
import de.anomic.data.BookmarkHelper;
import de.anomic.data.BookmarksDB;
import de.anomic.data.ListManager;
import de.anomic.data.WorkTables;
import de.anomic.data.ymark.YMarkTables;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

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

        // get segment
        Segment indexSegment = sb.index;

        prop.put("info", "0");

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
                String crawlingStart = post.get("crawlingURL","").trim(); // the crawljob start url
                // add the prefix http:// if necessary
                int pos = crawlingStart.indexOf("://",0);
                if (pos == -1) {
                    if (crawlingStart.startsWith("www")) crawlingStart = "http://" + crawlingStart;
                    if (crawlingStart.startsWith("ftp")) crawlingStart = "ftp://" + crawlingStart;
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

                // normalize URL
                DigestURI crawlingStartURL = null;
                if (crawlingFile == null) try {crawlingStartURL = new DigestURI(crawlingStart);} catch (final MalformedURLException e1) {Log.logException(e1);}
                crawlingStart = (crawlingStartURL == null) ? null : crawlingStartURL.toNormalform(true, true);

                // set new properties
                final boolean fullDomain = "domain".equals(post.get("range", "wide")); // special property in simple crawl start
                final boolean subPath    = "subpath".equals(post.get("range", "wide")); // special property in simple crawl start

                // set the crawl filter
                String newcrawlingMustMatch = post.get("mustmatch", CrawlProfile.MATCH_ALL_STRING);
                final String newcrawlingMustNotMatch = post.get("mustnotmatch", CrawlProfile.MATCH_NEVER_STRING);
                if (newcrawlingMustMatch.length() < 2) newcrawlingMustMatch = CrawlProfile.MATCH_ALL_STRING; // avoid that all urls are filtered out if bad value was submitted
                String ipMustMatch = post.get("ipMustmatch", CrawlProfile.MATCH_ALL_STRING);
                final String ipMustNotMatch = post.get("ipMustnotmatch", CrawlProfile.MATCH_NEVER_STRING);
                if (ipMustMatch.length() < 2) ipMustMatch = CrawlProfile.MATCH_ALL_STRING;
                final String countryMustMatch = post.getBoolean("countryMustMatchSwitch") ? post.get("countryMustMatchList", "") : "";
                sb.setConfig("crawlingIPMustMatch", ipMustMatch);
                sb.setConfig("crawlingIPMustNotMatch", ipMustNotMatch);
                if (countryMustMatch.length() > 0) sb.setConfig("crawlingCountryMustMatch", countryMustMatch);

                // special cases:
                if (crawlingStartURL!= null && fullDomain) {
                    newcrawlingMustMatch = CrawlProfile.mustMatchFilterFullDomain(crawlingStartURL);
                    if (subPath) newcrawlingMustMatch = newcrawlingMustMatch.substring(0, newcrawlingMustMatch.length() - 2) + crawlingStartURL.getPath() + ".*";
                }
                if (crawlingStart!= null && subPath && (pos = crawlingStart.lastIndexOf('/')) > 0) {
                    newcrawlingMustMatch = crawlingStart.substring(0, pos + 1) + ".*";
                }

                final boolean crawlOrder = post.get("crawlOrder", "off").equals("on");
                env.setConfig("crawlOrder", crawlOrder);

                int newcrawlingdepth = post.getInt("crawlingDepth", 8);
                env.setConfig("crawlingDepth", Integer.toString(newcrawlingdepth));
                if ((crawlOrder) && (newcrawlingdepth > 8)) newcrawlingdepth = 8;

                final boolean directDocByURL = "on".equals(post.get("directDocByURL", "on")); // catch also all linked media documents without loading them
                env.setConfig("crawlingDirectDocByURL", directDocByURL);

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
                    sb.tables.recordAPICall(post, "Crawler_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "crawl start for " + ((crawlingStart == null) ? post.get("crawlingFile", "") : crawlingStart), repeat_time, repeat_unit.substring(3));
                } else {
                    // store just a protocol
                    sb.tables.recordAPICall(post, "Crawler_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "crawl start for " + ((crawlingStart == null) ? post.get("crawlingFile", "") : crawlingStart));
                }

                final boolean crawlingDomMaxCheck = "on".equals(post.get("crawlingDomMaxCheck", "off"));
                final int crawlingDomMaxPages = (crawlingDomMaxCheck) ? post.getInt("crawlingDomMaxPages", -1) : -1;
                env.setConfig("crawlingDomMaxPages", Integer.toString(crawlingDomMaxPages));

                final boolean crawlingQ = "on".equals(post.get("crawlingQ", "off"));
                env.setConfig("crawlingQ", crawlingQ);

                final boolean indexText = "on".equals(post.get("indexText", "on"));
                env.setConfig("indexText", indexText);

                final boolean indexMedia = "on".equals(post.get("indexMedia", "on"));
                env.setConfig("indexMedia", indexMedia);

                boolean storeHTCache = "on".equals(post.get("storeHTCache", "on"));
                if (crawlingStartURL!= null &&(crawlingStartURL.isFile() || crawlingStartURL.isSMB())) storeHTCache = false;
                env.setConfig("storeHTCache", storeHTCache);

                CacheStrategy cachePolicy = CacheStrategy.parse(post.get("cachePolicy", "iffresh"));
                if (cachePolicy == null) cachePolicy = CacheStrategy.IFFRESH;

                final boolean xsstopw = "on".equals(post.get("xsstopw", "off"));
                env.setConfig("xsstopw", xsstopw);

                final boolean xdstopw = "on".equals(post.get("xdstopw", "off"));
                env.setConfig("xdstopw", xdstopw);

                final boolean xpstopw = "on".equals(post.get("xpstopw", "off"));
                env.setConfig("xpstopw", xpstopw);

                final String crawlingMode = post.get("crawlingMode","url");
                if (crawlingStart != null && crawlingStart.startsWith("ftp")) {
                    try {
                        // check if the crawl filter works correctly
                        Pattern.compile(newcrawlingMustMatch);
                        final CrawlProfile profile = new CrawlProfile(
                                crawlingStart,
                                crawlingStartURL,
                                newcrawlingMustMatch,
                                newcrawlingMustNotMatch,
                                ipMustMatch,
                                ipMustNotMatch,
                                countryMustMatch,
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
                                cachePolicy);
                        sb.crawler.putActive(profile.handle().getBytes(), profile);
                        sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                        final DigestURI url = crawlingStartURL;
                        sb.crawlStacker.enqueueEntriesFTP(sb.peers.mySeed().hash.getBytes(), profile.handle(), url.getHost(), url.getPort(), false);
                    } catch (final PatternSyntaxException e) {
                        prop.put("info", "4"); // crawlfilter does not match url
                        prop.putHTML("info_newcrawlingfilter", newcrawlingMustMatch);
                        prop.putHTML("info_error", e.getMessage());
                    } catch (final Exception e) {
                        // mist
                        prop.put("info", "7"); // Error with file
                        prop.putHTML("info_crawlingStart", crawlingStart);
                        prop.putHTML("info_error", e.getMessage());
                        Log.logException(e);
                    }
                    sb.continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                } else if ("url".equals(crawlingMode)) {

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
                        final DigestURI url = new DigestURI(crawlingStart);
                        final byte[] urlhash = url.hash();
                        indexSegment.fulltext().remove(urlhash);
                        sb.crawlQueues.noticeURL.removeByURLHash(urlhash);
                        sb.crawlQueues.errorURL.remove(urlhash);

                        // get a scraper to get the title
                        final Document scraper = sb.loader.loadDocument(url, CacheStrategy.IFFRESH, BlacklistType.CRAWLER, CrawlQueues.queuedMinLoadDelay);
                        final String title = scraper == null ? url.toNormalform(true, true) : scraper.dc_title();
                        final String description = scraper.dc_description();

                        // stack url
                        sb.crawler.removePassive(crawlingStartURL.hash()); // if there is an old entry, delete it
                        final CrawlProfile pe = new CrawlProfile(
                                (crawlingStartURL.getHost() == null) ? crawlingStartURL.toNormalform(true, false) : crawlingStartURL.getHost(),
                                crawlingStartURL,
                                newcrawlingMustMatch,
                                newcrawlingMustNotMatch,
                                ipMustMatch,
                                ipMustNotMatch,
                                countryMustMatch,
                                newcrawlingdepth,
                                directDocByURL,
                                crawlingIfOlder,
                                crawlingDomMaxPages,
                                crawlingQ,
                                indexText, indexMedia,
                                storeHTCache,
                                crawlOrder,
                                xsstopw,
                                xdstopw,
                                xpstopw,
                                cachePolicy);
                        sb.crawler.putActive(pe.handle().getBytes(), pe);
                        final String reasonString = sb.crawlStacker.stackCrawl(new Request(
                                sb.peers.mySeed().hash.getBytes(),
                                url,
                                null,
                                "CRAWLING-ROOT",
                                new Date(),
                                pe.handle(),
                                0,
                                0,
                                0,
                                0
                                ));

                        if (reasonString == null) {
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
                            final BookmarksDB.Bookmark bookmark = sb.bookmarksDB.createBookmark(crawlingStart, "admin");
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
                            sb.tables.bookmarks.createBookmark(sb.loader, url, YMarkTables.USER_ADMIN, true, "crawlStart", "/Crawl Start");

                            // liftoff!
                            prop.put("info", "8");//start msg
                            prop.putHTML("info_crawlingURL", post.get("crawlingURL"));

                            // generate a YaCyNews if the global flag was set
                            if (!sb.isRobinsonMode() && crawlOrder) {
                                final Map<String, String> m = new HashMap<String, String>(pe); // must be cloned
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
                                        0,
                                        0),
                                sb.peers.mySeed().hash.getBytes(),
                                new Date(),
                                1,
                                FailCategory.FINAL_LOAD_CONTEXT,
                                reasonString, -1);
                        }
                    } catch (final PatternSyntaxException e) {
                        prop.put("info", "4"); // crawlfilter does not match url
                        prop.putHTML("info_newcrawlingfilter", newcrawlingMustMatch);
                        prop.putHTML("info_error", e.getMessage());
                    } catch (final Exception e) {
                        // mist
                        prop.put("info", "6"); // Error with url
                        prop.putHTML("info_crawlingStart", crawlingStart);
                        prop.putHTML("info_error", e.getMessage());
                        Log.logInfo("Crawler_p", "start url rejected: " + e.getMessage());
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

                            final DigestURI crawlURL = new DigestURI("file://" + crawlingFile.toString());
                            final CrawlProfile profile = new CrawlProfile(
                                    crawlingFileName,
                                    crawlURL,
                                    newcrawlingMustMatch,
                                    CrawlProfile.MATCH_NEVER_STRING,
                                    ipMustMatch,
                                    ipMustNotMatch,
                                    countryMustMatch,
                                    newcrawlingdepth,
                                    false,
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
                                    cachePolicy);
                            sb.crawler.putActive(profile.handle().getBytes(), profile);
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
                } else if ("sitemap".equals(crawlingMode)) {
                    final String sitemapURLStr = post.get("sitemapURL","");
                	try {
                		final DigestURI sitemapURL = new DigestURI(sitemapURLStr);
                		final CrawlProfile pe = new CrawlProfile(
                				sitemapURLStr,
                				sitemapURL,
                				CrawlProfile.MATCH_ALL_STRING,
                				CrawlProfile.MATCH_NEVER_STRING,
                                ipMustMatch,
                                ipMustNotMatch,
                                countryMustMatch,
                				0,
                				false,
                				crawlingIfOlder,
                				crawlingDomMaxPages,
                				true,
                				indexText,
                				indexMedia,
                				storeHTCache,
                				crawlOrder,
                				xsstopw,
                				xdstopw,
                				xpstopw,
                				cachePolicy);
                		sb.crawler.putActive(pe.handle().getBytes(), pe);
                		final SitemapImporter importer = new SitemapImporter(sb, sitemapURL, pe);
                		importer.start();
                	} catch (final Exception e) {
                		// mist
                		prop.put("info", "6");//Error with url
                		prop.putHTML("info_crawlingStart", sitemapURLStr);
                		prop.putHTML("info_error", e.getMessage());
                		Log.logException(e);
                	}
                } else if ("sitelist".equals(crawlingMode)) {
                    try {
                        final DigestURI sitelistURL = new DigestURI(crawlingStart);
                        // download document
                        Document scraper = sb.loader.loadDocument(sitelistURL, CacheStrategy.IFFRESH, BlacklistType.CRAWLER, CrawlQueues.queuedMinLoadDelay);
                        // String title = scraper.getTitle();
                        // String description = scraper.getDescription();

                        // get links and generate filter
                        final Map<MultiProtocolURI, Properties> hyperlinks = scraper.getAnchors();
                        if (fullDomain && newcrawlingdepth > 0) newcrawlingMustMatch = siteFilter(hyperlinks.keySet());

                        // put links onto crawl queue
                        final CrawlProfile profile = new CrawlProfile(
                                sitelistURL.getHost(),
                                sitelistURL,
                                newcrawlingMustMatch,
                                CrawlProfile.MATCH_NEVER_STRING,
                                ipMustMatch,
                                ipMustNotMatch,
                                countryMustMatch,
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
                                cachePolicy);
                        sb.crawler.putActive(profile.handle().getBytes(), profile);
                        sb.pauseCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
                        final Iterator<Map.Entry<MultiProtocolURI, Properties>> linkiterator = hyperlinks.entrySet().iterator();
                        DigestURI nexturl;
                        while (linkiterator.hasNext()) {
                            final Map.Entry<MultiProtocolURI, Properties> e = linkiterator.next();
                            if (e.getKey() == null) continue;
                            nexturl = new DigestURI(e.getKey());
                            // remove the url from the database to be prepared to crawl them again
                            final byte[] urlhash = nexturl.hash();
                            indexSegment.fulltext().remove(urlhash);
                            sb.crawlQueues.noticeURL.removeByURLHash(urlhash);
                            sb.crawlQueues.errorURL.remove(urlhash);
                            sb.crawlStacker.enqueueEntry(new Request(
                                    sb.peers.mySeed().hash.getBytes(),
                                    nexturl,
                                    null,
                                    e.getValue().getProperty("name", ""),
                                    new Date(),
                                    profile.handle(),
                                    0,
                                    0,
                                    0,
                                    0
                                    ));
                        }
                    } catch (final Exception e) {
                        // mist
                        prop.put("info", "6");//Error with url
                        prop.putHTML("info_crawlingStart", crawlingStart);
                        prop.putHTML("info_error", e.getMessage());
                        Log.logException(e);
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

    private static String siteFilter(final Set<MultiProtocolURI> uris) {
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

    private static String subpathFilter(final Set<MultiProtocolURI> uris) {
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
