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
import java.util.Collection;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.Classification;
import net.yacy.cora.document.Classification.ContentDomain;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.RequestHeader.FileType;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.Formatter;
import net.yacy.peers.NewsPool;
import net.yacy.peers.Seed;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.search.EventTracker;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.snippet.ResultEntry;
import net.yacy.search.snippet.TextSnippet;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;


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
        final QueryParams theQuery = theSearch.getQuery();

        // dynamically update count values
        final int totalcount = theSearch.getRankingResult().getLocalIndexCount() - theSearch.getRankingResult().getMissCount() - theSearch.getRankingResult().getSortOutCount() + theSearch.getRankingResult().getRemoteIndexCount();
        final int offset = theQuery.neededResults() - theQuery.itemsPerPage() + 1;
        prop.put("offset", offset);
        prop.put("itemscount", Formatter.number(Math.min((item < 0) ? theQuery.neededResults() : item + 1, totalcount)));
        prop.put("itemsperpage", Formatter.number(theQuery.itemsPerPage));
        prop.put("totalcount", Formatter.number(totalcount, true));
        prop.put("localResourceSize", Formatter.number(theSearch.getRankingResult().getLocalIndexCount(), true));
        prop.put("localMissCount", Formatter.number(theSearch.getRankingResult().getMissCount(), true));
        prop.put("remoteResourceSize", Formatter.number(theSearch.getRankingResult().getRemoteResourceSize(), true));
        prop.put("remoteIndexCount", Formatter.number(theSearch.getRankingResult().getRemoteIndexCount(), true));
        prop.put("remotePeerCount", Formatter.number(theSearch.getRankingResult().getRemotePeerCount(), true));
        prop.put("navurlBase", QueryParams.navurlBase("html", theQuery, null, theQuery.urlMask.toString(), theQuery.navigators).toString());
        final String target_special_pattern = sb.getConfig(SwitchboardConstants.SEARCH_TARGET_SPECIAL_PATTERN, "");

        if (theQuery.contentdom == Classification.ContentDomain.TEXT || theQuery.contentdom == Classification.ContentDomain.ALL) {
            // text search

            // generate result object
            final ResultEntry result = theSearch.oneResult(item, theQuery.isLocal() ? 1000 : 5000);
            if (result == null) return prop; // no content
            final String resultUrlstring = result.urlstring();
            final DigestURI resultURL = result.url();
            final String target = sb.getConfig(resultUrlstring.matches(target_special_pattern) ? SwitchboardConstants.SEARCH_TARGET_SPECIAL : SwitchboardConstants.SEARCH_TARGET_DEFAULT, "_self");

            final int port = resultURL.getPort();
            DigestURI faviconURL = null;
            if ((fileType == FileType.HTML || fileType == FileType.JSON) && !sb.isIntranetMode()) try {
                faviconURL = new DigestURI(resultURL.getProtocol() + "://" + resultURL.getHost() + ((port != -1) ? (":" + port) : "") + "/favicon.ico");
            } catch (final MalformedURLException e1) {
                Log.logException(e1);
                faviconURL = null;
            }
            final String resource = theQuery.domType.toString();
            prop.put("content", 1); // switch on specific content
            prop.put("content_showDate", sb.getConfigBool("search.result.show.date", true) ? 1 : 0);
            prop.put("content_showSize", sb.getConfigBool("search.result.show.size", true) ? 1 : 0);
            prop.put("content_showMetadata", sb.getConfigBool("search.result.show.metadata", true) ? 1 : 0);
            prop.put("content_showParser", sb.getConfigBool("search.result.show.parser", true) ? 1 : 0);
            prop.put("content_showPictures", sb.getConfigBool("search.result.show.pictures", true) ? 1 : 0);
            prop.put("content_showCache", sb.getConfigBool("search.result.show.cache", true) ? 1 : 0);
            prop.put("content_showProxy", sb.getConfigBool("search.result.show.proxy", true) ? 1 : 0);
            prop.put("content_showTags", sb.getConfigBool("search.result.show.tags", false) ? 1 : 0);
            prop.put("content_authorized", authenticated ? "1" : "0");
            final String urlhash = ASCII.String(result.hash());
            prop.put("content_authorized_bookmark", sb.tables.bookmarks.hasBookmark("admin", urlhash) ? "0" : "1");
            prop.putHTML("content_authorized_bookmark_bookmarklink", "/yacysearch.html?query=" + theQuery.queryString.replace(' ', '+') + "&Enter=Search&count=" + theQuery.itemsPerPage() + "&offset=" + (theQuery.neededResults() - theQuery.itemsPerPage()) + "&order=" + crypt.simpleEncode(theQuery.ranking.toExternalString()) + "&resource=" + resource + "&time=3&bookmarkref=" + urlhash + "&urlmaskfilter=.*");
            prop.put("content_authorized_recommend", (sb.peers.newsPool.getSpecific(NewsPool.OUTGOING_DB, NewsPool.CATEGORY_SURFTIPP_ADD, "url", resultUrlstring) == null) ? "1" : "0");
            prop.putHTML("content_authorized_recommend_deletelink", "/yacysearch.html?query=" + theQuery.queryString.replace(' ', '+') + "&Enter=Search&count=" + theQuery.itemsPerPage() + "&offset=" + (theQuery.neededResults() - theQuery.itemsPerPage()) + "&order=" + crypt.simpleEncode(theQuery.ranking.toExternalString()) + "&resource=" + resource + "&time=3&deleteref=" + urlhash + "&urlmaskfilter=.*");
            prop.putHTML("content_authorized_recommend_recommendlink", "/yacysearch.html?query=" + theQuery.queryString.replace(' ', '+') + "&Enter=Search&count=" + theQuery.itemsPerPage() + "&offset=" + (theQuery.neededResults() - theQuery.itemsPerPage()) + "&order=" + crypt.simpleEncode(theQuery.ranking.toExternalString()) + "&resource=" + resource + "&time=3&recommendref=" + urlhash + "&urlmaskfilter=.*");
            prop.put("content_authorized_urlhash", urlhash);
            final String resulthashString = urlhash;
            prop.putHTML("content_title", result.title());
            prop.putXML("content_title-xml", result.title());
            prop.putJSON("content_title-json", result.title());
            prop.putHTML("content_showPictures_link", resultUrlstring);
            //prop.putHTML("content_link", resultUrlstring);

// START interaction
            String modifyURL = resultUrlstring;
			if (sb.getConfigBool("proxyURL.useforresults", false)) {
				// check if url is allowed to view
				if (sb.getConfig("proxyURL.rewriteURLs", "all").equals("all")) {
					modifyURL = "./proxy.html?url="+modifyURL;
				}

				// check if url is allowed to view
				if (sb.getConfig("proxyURL.rewriteURLs", "all").equals("domainlist")) {
					try {
						if (sb.crawlStacker.urlInAcceptedDomain(new DigestURI (modifyURL)) == null) {
							modifyURL = "./proxy.html?url="+modifyURL;
						}
					} catch (MalformedURLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				if (sb.getConfig("proxyURL.rewriteURLs", "all").equals("yacy")) {
					try {
						if ((new DigestURI (modifyURL).getHost().endsWith(".yacy"))) {
							modifyURL = "./proxy.html?url="+modifyURL;
						}
					} catch (MalformedURLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
            prop.putHTML("content_link", modifyURL);
//            prop.putHTML("content_value", Interaction.TripleGet(result.urlstring(), "http://virtual.x/hasvalue", "anonymous"));
// END interaction

            prop.putHTML("content_target", target);
            if (faviconURL != null && fileType == FileType.HTML) sb.loader.loadIfNotExistBackground(faviconURL, 1024 * 1024 * 10);
            prop.putHTML("content_faviconCode", sb.licensedURLs.aquireLicense(faviconURL)); // acquire license for favicon url loading
            prop.put("content_urlhash", resulthashString);
            prop.put("content_ranking", result.ranking);
            prop.put("content_showMetadata_urlhash", resulthashString);
            prop.put("content_showCache_link", resultUrlstring);
            prop.put("content_showProxy_link", resultUrlstring);
            prop.put("content_showParser_urlhash", resulthashString);
            prop.put("content_showTags_urlhash", resulthashString);
            prop.put("content_urlhexhash", Seed.b64Hash2hexHash(resulthashString));
            prop.putHTML("content_urlname", nxTools.shortenURLString(result.urlname(), MAX_URL_LENGTH));
            prop.put("content_showDate_date", GenericFormatter.RFC1123_SHORT_FORMATTER.format(result.modified()));
            prop.put("content_date822", HeaderFramework.formatRFC1123(result.modified()));
            //prop.put("content_ybr", RankingProcess.ybr(result.hash()));
            prop.putHTML("content_size", Integer.toString(result.filesize())); // we don't use putNUM here because that number shall be usable as sorting key. To print the size, use 'sizename'
            prop.putHTML("content_sizename", sizename(result.filesize()));
            prop.putHTML("content_showSize_sizename", sizename(result.filesize()));
            prop.putHTML("content_host", resultURL.getHost() == null ? "" : resultURL.getHost());
            prop.putHTML("content_file", resultURL.getFile());
            prop.putHTML("content_path", resultURL.getPath());
            prop.put("content_nl", (item == theQuery.offset) ? 0 : 1);
            prop.putHTML("content_publisher", result.publisher());
            prop.putHTML("content_creator", result.creator());// author
            prop.putHTML("content_subject", result.subject());
            final Collection<String>[] query = theQuery.queryWords();
            final StringBuilder s = new StringBuilder(query[0].size() * 20);
            for (final String t: query[0]) {
                s.append('+').append(t);
            }
            final String words = (s.length() > 0) ? s.substring(1) : "";
            prop.putHTML("content_words", words);
            prop.putHTML("content_showParser_words", words);
            prop.putHTML("content_former", theQuery.queryString);
            prop.putHTML("content_showPictures_former", theQuery.queryString);
            final TextSnippet snippet = result.textSnippet();
            final String desc = (snippet == null) ? "" : snippet.getLineMarked(theQuery.fullqueryHashes);
            prop.put("content_description", desc);
            prop.putXML("content_description-xml", desc);
            prop.putJSON("content_description-json", desc);
            final SearchEvent.HeuristicResult heuristic = theSearch.getHeuristic(result.hash());
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
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(theQuery.id(true), SearchEvent.Type.FINALIZATION, "" + item, 0, 0), false);
            final String ext = resultURL.getFileExtension().toLowerCase();
            if (ext.equals("png") || ext.equals("jpg") || ext.equals("gif")) {
                final String license = sb.licensedURLs.aquireLicense(resultURL);
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
            if (sb.getConfigBool("heuristic.searchresults",false)) sb.heuristicSearchResults(resultUrlstring);
            theQuery.transmitcount = item + 1;
            return prop;
        }

        if (theQuery.contentdom == Classification.ContentDomain.IMAGE) {
            // image search; shows thumbnails

            prop.put("content", theQuery.contentdom.getCode() + 1); // switch on specific content
            //final MediaSnippet ms = theSearch.result().oneImage(item);
            final ResultEntry ms = theSearch.oneResult(item, theQuery.isLocal() ? 1000 : 5000);
            if (ms == null) {
                prop.put("content_item", "0");
            } else {
                final String resultUrlstring = ms.url().toNormalform(true, false);
                final String target = sb.getConfig(resultUrlstring.matches(target_special_pattern) ? SwitchboardConstants.SEARCH_TARGET_SPECIAL : SwitchboardConstants.SEARCH_TARGET_DEFAULT, "_self");

                final String license = sb.licensedURLs.aquireLicense(ms.url());
                sb.loader.loadIfNotExistBackground(ms.url(), 1024 * 1024 * 10);
                prop.putHTML("content_item_hrefCache", (auth) ? "/ViewImage.png?url=" + resultUrlstring : resultUrlstring);
                prop.putHTML("content_item_href", resultUrlstring);
                prop.putHTML("content_item_target", target);
                prop.put("content_item_code", license);
                prop.putHTML("content_item_name", shorten(ms.title(), MAX_NAME_LENGTH));
                prop.put("content_item_mimetype", "");
                prop.put("content_item_fileSize", 0);
                prop.put("content_item_width", 0);
                prop.put("content_item_height", 0);
                prop.put("content_item_attr", ""/*(ms.attr.equals("-1 x -1")) ? "" : "(" + ms.attr + ")"*/); // attributes, here: original size of image
                prop.put("content_item_urlhash", ASCII.String(ms.url().hash()));
                prop.put("content_item_source", ms.url().toNormalform(true, false));
                prop.putXML("content_item_source-xml", ms.url().toNormalform(true, false));
                prop.put("content_item_sourcedom", ms.url().getHost());
                prop.put("content_item_nl", (item == theQuery.offset) ? 0 : 1);
                prop.put("content_item", 1);
            }
            theQuery.transmitcount = item + 1;
            return prop;
        }

        if ((theQuery.contentdom == ContentDomain.AUDIO) ||
            (theQuery.contentdom == ContentDomain.VIDEO) ||
            (theQuery.contentdom == ContentDomain.APP)) {
            // any other media content

            // generate result object
            final ResultEntry ms = theSearch.oneResult(item, theQuery.isLocal() ? 1000 : 5000);
            prop.put("content", theQuery.contentdom.getCode() + 1); // switch on specific content
            if (ms == null) {
                prop.put("content_item", "0");
            } else {
                final String resultUrlstring = ms.url().toNormalform(true, false);
                final String target = sb.getConfig(resultUrlstring.matches(target_special_pattern) ? SwitchboardConstants.SEARCH_TARGET_SPECIAL : SwitchboardConstants.SEARCH_TARGET_DEFAULT, "_self");
                prop.putHTML("content_item_href", resultUrlstring);
                prop.putHTML("content_item_hrefshort", nxTools.shortenURLString(resultUrlstring, MAX_URL_LENGTH));
                prop.putHTML("content_item_target", target);
                prop.putHTML("content_item_name", shorten(ms.title(), MAX_NAME_LENGTH));
                prop.put("content_item_col", (item % 2 == 0) ? "0" : "1");
                prop.put("content_item_nl", (item == theQuery.offset) ? 0 : 1);
                prop.put("content_item", 1);
            }
            theQuery.transmitcount = item + 1;
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

    private static String sizename(int size) {
        if (size < 1024) return size + " bytes";
        size = size / 1024;
        if (size < 1024) return size + " kbyte";
        size = size / 1024;
        if (size < 1024) return size + " mbyte";
        size = size / 1024;
        return size + " gbyte";
    }
}
