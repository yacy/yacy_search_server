// yacysearchitem.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 28.08.2007 on http://yacy.net
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

import java.net.MalformedURLException;
import java.util.Iterator;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.RequestHeader.FileType;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.Memory;
import net.yacy.crawler.data.Cache;
import net.yacy.data.URLLicense;
import net.yacy.kelondro.util.Formatter;
import net.yacy.peers.NewsPool;
import net.yacy.peers.Seed;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.search.EventTracker;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.HeuristicResult;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.query.SearchEventType;
import net.yacy.search.snippet.ResultEntry;
import net.yacy.search.snippet.TextSnippet;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.utils.crypt;
import net.yacy.utils.nxTools;


public class yacysearchitem {

    private static final String SHORTEN_SUFFIX = "...";
    private static final int SHORTEN_SUFFIX_LENGTH = SHORTEN_SUFFIX.length();
    private static final int MAX_NAME_LENGTH = 60;
    private static final int MAX_URL_LENGTH = 120;

    //private static boolean col = true;

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        final String eventID = post.get("eventID", "");
        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        final int item = post.getInt("item", -1);
        final boolean auth = Domains.isLocalhost(header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, "")) || sb.verifyAuthentication(header);
        final RequestHeader.FileType fileType = header.fileType();

        // default settings for blank item
        prop.put("content", "0");
        prop.put("rss", "0");
        prop.put("references", "0");
        prop.put("rssreferences", "0");
        prop.put("dynamic", "0");

        // find search event
        final SearchEvent theSearch = SearchEventCache.getEvent(eventID);
        if (theSearch == null) {
            // the event does not exist, show empty page
            return prop;
        }

        // dynamically update count values
        prop.put("offset", theSearch.query.neededResults() - theSearch.query.itemsPerPage() + 1);
        prop.put("itemscount", Formatter.number(Math.min((item < 0) ? theSearch.query.neededResults() : item + 1, theSearch.getResultCount())));
        prop.put("itemsperpage", Formatter.number(theSearch.query.itemsPerPage));
        prop.put("totalcount", Formatter.number(theSearch.getResultCount(), true));
        prop.put("localResourceSize", Formatter.number(theSearch.local_rwi_stored.get() + theSearch.local_solr_stored.get(), true));
        prop.put("remoteResourceSize", Formatter.number(theSearch.remote_rwi_stored.get() + theSearch.remote_solr_stored.get(), true));
        prop.put("remoteIndexCount", Formatter.number(theSearch.remote_rwi_available.get() + theSearch.remote_solr_available.get(), true));
        prop.put("remotePeerCount", Formatter.number(theSearch.remote_rwi_peerCount.get() + theSearch.remote_solr_peerCount.get(), true));
        prop.put("navurlBase", QueryParams.navurlBase(RequestHeader.FileType.HTML, theSearch.query, null, false).toString());
        final String target_special_pattern = sb.getConfig(SwitchboardConstants.SEARCH_TARGET_SPECIAL_PATTERN, "");

        long timeout = item == 0 ? 10000 : (theSearch.query.isLocal() ? 1000 : 3000);
        
        if (theSearch.query.contentdom == Classification.ContentDomain.TEXT || theSearch.query.contentdom == Classification.ContentDomain.ALL) {
            // text search

            // generate result object
            final ResultEntry result = theSearch.oneResult(item, timeout);
            if (result == null) return prop; // no content
            final String resultUrlstring = result.urlstring();
            final DigestURL resultURL = result.url();
            final String target = sb.getConfig(resultUrlstring.matches(target_special_pattern) ? SwitchboardConstants.SEARCH_TARGET_SPECIAL : SwitchboardConstants.SEARCH_TARGET_DEFAULT, "_self");

            final int port = resultURL.getPort();
            DigestURL faviconURL = null;
            if ((fileType == FileType.HTML || fileType == FileType.JSON) && !sb.isIntranetMode()) try {
                faviconURL = new DigestURL(resultURL.getProtocol() + "://" + resultURL.getHost() + ((port != -1) ? (":" + port) : "") + "/favicon.ico");
            } catch (final MalformedURLException e1) {
                ConcurrentLog.logException(e1);
                faviconURL = null;
            }
            final String resource = theSearch.query.domType.toString();
            final String origQ = theSearch.query.getQueryGoal().getQueryString(true);
            prop.put("content", 1); // switch on specific content
            prop.put("content_showDate", sb.getConfigBool("search.result.show.date", true) ? 1 : 0);
            prop.put("content_showSize", sb.getConfigBool("search.result.show.size", true) ? 1 : 0);
            prop.put("content_showMetadata", sb.getConfigBool("search.result.show.metadata", true) ? 1 : 0);
            prop.put("content_showParser", sb.getConfigBool("search.result.show.parser", true) ? 1 : 0);
            prop.put("content_showCitation", sb.getConfigBool("search.result.show.citation", true) ? 1 : 0);
            prop.put("content_showPictures", sb.getConfigBool("search.result.show.pictures", true) ? 1 : 0);
            prop.put("content_showCache", sb.getConfigBool("search.result.show.cache", true) && Cache.has(resultURL.hash()) ? 1 : 0);
            prop.put("content_showProxy", sb.getConfigBool("search.result.show.proxy", true) ? 1 : 0);
            prop.put("content_showHostBrowser", sb.getConfigBool("search.result.show.hostbrowser", true) ? 1 : 0);
            prop.put("content_authorized", authenticated ? "1" : "0");
            final String urlhash = ASCII.String(result.hash());
            prop.put("content_authorized_bookmark", sb.tables.bookmarks.hasBookmark("admin", urlhash) ? "0" : "1");
            prop.putHTML("content_authorized_bookmark_bookmarklink", "yacysearch.html?query=" + origQ.replace(' ', '+') + "&Enter=Search&count=" + theSearch.query.itemsPerPage() + "&offset=" + (theSearch.query.neededResults() - theSearch.query.itemsPerPage()) + "&order=" + crypt.simpleEncode(theSearch.query.ranking.toExternalString()) + "&resource=" + resource + "&time=3&bookmarkref=" + urlhash + "&urlmaskfilter=.*");
            prop.put("content_authorized_recommend", (sb.peers.newsPool.getSpecific(NewsPool.OUTGOING_DB, NewsPool.CATEGORY_SURFTIPP_ADD, "url", resultUrlstring) == null) ? "1" : "0");
            prop.putHTML("content_authorized_recommend_deletelink", "yacysearch.html?query=" + origQ.replace(' ', '+') + "&Enter=Search&count=" + theSearch.query.itemsPerPage() + "&offset=" + (theSearch.query.neededResults() - theSearch.query.itemsPerPage()) + "&order=" + crypt.simpleEncode(theSearch.query.ranking.toExternalString()) + "&resource=" + resource + "&time=3&deleteref=" + urlhash + "&urlmaskfilter=.*");
            prop.putHTML("content_authorized_recommend_recommendlink", "yacysearch.html?query=" + origQ.replace(' ', '+') + "&Enter=Search&count=" + theSearch.query.itemsPerPage() + "&offset=" + (theSearch.query.neededResults() - theSearch.query.itemsPerPage()) + "&order=" + crypt.simpleEncode(theSearch.query.ranking.toExternalString()) + "&resource=" + resource + "&time=3&recommendref=" + urlhash + "&urlmaskfilter=.*");
            prop.put("content_authorized_urlhash", urlhash);
            final String resulthashString = urlhash;
            prop.putHTML("content_title", result.title());
            prop.putXML("content_title-xml", result.title());
            prop.putJSON("content_title-json", result.title());
            prop.putHTML("content_showPictures_link", resultUrlstring);
            //prop.putHTML("content_link", resultUrlstring);

// START interaction
            String modifyURL = resultUrlstring;
			if (sb.getConfigBool("proxyURL.useforresults", false) && sb.getConfigBool("proxyURL", false)) {
				// check if url is allowed to view
				if (sb.getConfig("proxyURL.rewriteURLs", "all").equals("all")) {
					modifyURL = "./proxy.html?url="+modifyURL;
				}

				// check if url is allowed to view
				if (sb.getConfig("proxyURL.rewriteURLs", "all").equals("domainlist")) {
					try {
						if (sb.crawlStacker.urlInAcceptedDomain(new DigestURL (modifyURL)) == null) {
							modifyURL = "./proxy.html?url="+modifyURL;
						}
					} catch (final MalformedURLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				if (sb.getConfig("proxyURL.rewriteURLs", "all").equals("yacy")) {
					try {
						if ((new DigestURL (modifyURL).getHost().endsWith(".yacy"))) {
							modifyURL = "./proxy.html?url="+modifyURL;
						}
					} catch (final MalformedURLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
            prop.putHTML("content_link", modifyURL);
//            prop.putHTML("content_value", Interaction.TripleGet(result.urlstring(), "http://virtual.x/hasvalue", "anonymous"));
// END interaction

            String resultFileName = resultURL.getFileName();
            prop.putHTML("content_target", target);
            if (faviconURL != null && fileType == FileType.HTML) sb.loader.loadIfNotExistBackground(faviconURL, 1024 * 1024 * 10, null, ClientIdentification.yacyIntranetCrawlerAgent);
            prop.putHTML("content_faviconCode", URLLicense.aquireLicense(faviconURL)); // acquire license for favicon url loading
            prop.put("content_urlhash", resulthashString);
            prop.put("content_ranking", result.ranking());
            prop.put("content_showMetadata_urlhash", resulthashString);
            prop.put("content_showCache_link", resultUrlstring);
            prop.put("content_showProxy_link", resultUrlstring);
            prop.put("content_showHostBrowser_link", resultUrlstring);
            prop.put("content_showParser_urlhash", resulthashString);
            prop.put("content_showCitation_urlhash", resulthashString);
            prop.put("content_urlhexhash", Seed.b64Hash2hexHash(resulthashString));
            prop.putHTML("content_urlname", nxTools.shortenURLString(result.urlname(), MAX_URL_LENGTH));
            prop.put("content_showDate_date", GenericFormatter.RFC1123_SHORT_FORMATTER.format(result.modified()));
            prop.put("content_date822", HeaderFramework.formatRFC1123(result.modified()));
            //prop.put("content_ybr", RankingProcess.ybr(result.hash()));
            prop.putHTML("content_size", Integer.toString(result.filesize())); // we don't use putNUM here because that number shall be usable as sorting key. To print the size, use 'sizename'
            prop.putHTML("content_sizename", RSSMessage.sizename(result.filesize()));
            prop.putHTML("content_showSize_sizename", RSSMessage.sizename(result.filesize()));
            prop.putHTML("content_host", resultURL.getHost() == null ? "" : resultURL.getHost());
            prop.putHTML("content_file", resultFileName);
            prop.putHTML("content_path", resultURL.getPath());
            prop.put("content_nl", (item == theSearch.query.offset) ? 0 : 1);
            prop.putHTML("content_publisher", result.publisher());
            prop.putHTML("content_creator", result.creator());// author
            prop.putHTML("content_subject", result.subject());
            final Iterator<String> query = theSearch.query.getQueryGoal().getIncludeStrings();
            final StringBuilder s = new StringBuilder(theSearch.query.getQueryGoal().getIncludeSize() * 20);
            while (query.hasNext()) s.append('+').append(query.next());
            final String words = (s.length() > 0) ? s.substring(1) : "";
            prop.putHTML("content_words", words);
            prop.putHTML("content_showParser_words", words);
            prop.putHTML("content_former", origQ);
            prop.putHTML("content_showPictures_former", origQ);
            final TextSnippet snippet = result.textSnippet();
            final String desc = (snippet == null) ? "" : snippet.isMarked() ? snippet.getLineRaw() : snippet.getLineMarked(theSearch.query.getQueryGoal());
            prop.put("content_description", desc);
            prop.putXML("content_description-xml", desc);
            prop.putJSON("content_description-json", desc);
            final HeuristicResult heuristic = theSearch.getHeuristic(result.hash());
            if (heuristic == null) {
                prop.put("content_heuristic", 0);
            } else {
                if (heuristic.redundant) {
                    prop.put("content_heuristic", 1);
                } else {
                    prop.put("content_heuristic", 2);
                }
                prop.put("content_heuristic_name", heuristic.heuristicName);
            }
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(theSearch.query.id(true), SearchEventType.FINALIZATION, "" + item, 0, 0), false);
            final String ext = MultiProtocolURL.getFileExtension(resultFileName);
            if (MultiProtocolURL.isImage(ext)) {
                final String license = URLLicense.aquireLicense(resultURL);
                prop.put("content_code", license);
            } else {
                prop.put("content_code", "");
            }
            if (result.lat() == 0.0d || result.lon() == 0.0d) {
                prop.put("content_loc", 0);
            } else {
                prop.put("content_loc", 1);
                prop.put("content_loc_lat", result.lat());
                prop.put("content_loc_lon", result.lon());
            }
            final boolean clustersearch = sb.isRobinsonMode() && sb.getConfig(SwitchboardConstants.CLUSTER_MODE, "").equals(SwitchboardConstants.CLUSTER_MODE_PUBLIC_CLUSTER);
            final boolean indexReceiveGranted = sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW_SEARCH, true) || clustersearch;
            boolean p2pmode = sb.peers != null && sb.peers.sizeConnected() > 0 && indexReceiveGranted;
            boolean stealthmode = p2pmode && theSearch.query.isLocal();
            if ((sb.getConfigBool(SwitchboardConstants.HEURISTIC_SEARCHRESULTS, false) ||
                (sb.getConfigBool(SwitchboardConstants.GREEDYLEARNING_ACTIVE, false) && sb.getConfigBool(SwitchboardConstants.GREEDYLEARNING_ENABLED, false) && Memory.load() < 1.0)) &&
                !stealthmode) sb.heuristicSearchResults(resultUrlstring);
            theSearch.query.transmitcount = item + 1;
            return prop;
        }

        if (theSearch.query.contentdom == Classification.ContentDomain.IMAGE) {
            // image search; shows thumbnails

            prop.put("content", theSearch.query.contentdom.getCode() + 1); // switch on specific content
            SearchEvent.ImageResult image = null;
            try {
                image = theSearch.oneImageResult(item, timeout);
                final String imageUrlstring = image.imageUrl.toNormalform(true);
                final String target = sb.getConfig(imageUrlstring.matches(target_special_pattern) ? SwitchboardConstants.SEARCH_TARGET_SPECIAL : SwitchboardConstants.SEARCH_TARGET_DEFAULT, "_self");

                final String license = URLLicense.aquireLicense(image.imageUrl);
                sb.loader.loadIfNotExistBackground(image.imageUrl, 1024 * 1024 * 10, null, ClientIdentification.yacyIntranetCrawlerAgent);
                prop.putHTML("content_item_hrefCache", (auth) ? "/ViewImage.png?url=" + imageUrlstring : imageUrlstring);
                prop.putHTML("content_item_href", imageUrlstring);
                prop.putHTML("content_item_target", target);
                prop.put("content_item_code", license);
                prop.putHTML("content_item_name", shorten(image.imagetext, MAX_NAME_LENGTH));
                prop.put("content_item_mimetype", image.mimetype);
                prop.put("content_item_fileSize", 0);
                prop.put("content_item_width", image.width);
                prop.put("content_item_height", image.height);
                prop.put("content_item_attr", ""/*(ms.attr.equals("-1 x -1")) ? "" : "(" + ms.attr + ")"*/); // attributes, here: original size of image
                prop.put("content_item_urlhash", ASCII.String(image.imageUrl.hash()));
                prop.put("content_item_source", image.sourceUrl.toNormalform(true));
                prop.putXML("content_item_source-xml", image.sourceUrl.toNormalform(true));
                prop.put("content_item_sourcedom", image.sourceUrl.getHost());
                prop.put("content_item_nl", (item == theSearch.query.offset) ? 0 : 1);
                prop.put("content_item", 1);
            } catch (MalformedURLException e) {
                prop.put("content_item", "0");
            }
            theSearch.query.transmitcount = item + 1;
            return prop;
        }

        if ((theSearch.query.contentdom == ContentDomain.AUDIO) ||
            (theSearch.query.contentdom == ContentDomain.VIDEO) ||
            (theSearch.query.contentdom == ContentDomain.APP)) {
            // any other media content

            // generate result object
            final ResultEntry ms = theSearch.oneResult(item, timeout);
            prop.put("content", theSearch.query.contentdom.getCode() + 1); // switch on specific content
            if (ms == null) {
                prop.put("content_item", "0");
            } else {
                final String resultUrlstring = ms.url().toNormalform(true);
                final String target = sb.getConfig(resultUrlstring.matches(target_special_pattern) ? SwitchboardConstants.SEARCH_TARGET_SPECIAL : SwitchboardConstants.SEARCH_TARGET_DEFAULT, "_self");
                prop.putHTML("content_item_href", resultUrlstring);
                prop.putHTML("content_item_hrefshort", nxTools.shortenURLString(resultUrlstring, MAX_URL_LENGTH));
                prop.putHTML("content_item_target", target);
                prop.putHTML("content_item_name", shorten(ms.title(), MAX_NAME_LENGTH));
                prop.put("content_item_col", (item % 2 == 0) ? "0" : "1");
                prop.put("content_item_nl", (item == theSearch.query.offset) ? 0 : 1);
                prop.put("content_item", 1);
            }
            theSearch.query.transmitcount = item + 1;
            return prop;
        }

        return prop;
    }

    private static String shorten(final String s, final int length) {
        final String ret;
        if (s.length() <= length) {
            ret = s;
        } else {
            final int p = s.lastIndexOf('.');
            if (p < 0) {
                ret = s.substring(0, length - SHORTEN_SUFFIX_LENGTH) + SHORTEN_SUFFIX;
            } else {
                assert p >= 0;
                final String ext = s.substring(p + 1);
                if (ext.length() > 4) {
                    ret = s.substring(0, length / 2 - 2) + SHORTEN_SUFFIX + s.substring(s.length() - (length / 2 - 2));
                } else {
                    ret = s.substring(0, length - ext.length() - SHORTEN_SUFFIX_LENGTH) + SHORTEN_SUFFIX + ext;
                }
            }
        }
        return ret;
    }
}
