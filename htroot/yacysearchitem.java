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

import java.awt.Dimension;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.RequestHeader.FileType;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.Memory;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.data.Transactions;
import net.yacy.crawler.data.Transactions.State;
import net.yacy.crawler.retrieval.Response;
import net.yacy.data.URLLicense;
import net.yacy.document.parser.html.IconEntry;
import net.yacy.kelondro.data.meta.URIMetadataNode;
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
import net.yacy.search.snippet.TextSnippet;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.utils.crypt;
import net.yacy.utils.nxTools;
import net.yacy.visualization.ImageViewer;

public class yacysearchitem {

    private static final String SHORTEN_SUFFIX = "...";
    private static final int SHORTEN_SUFFIX_LENGTH = SHORTEN_SUFFIX.length();
    private static final int MAX_NAME_LENGTH = 60;
    private static final int MAX_URL_LENGTH = 120;
    /** Default image item width in pixels */
    private static final int DEFAULT_IMG_WIDTH = 128;
    /** Default image item height in pixels */
    private static final int DEFAULT_IMG_HEIGHT = DEFAULT_IMG_WIDTH;

    //private static boolean col = true;

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        final String eventID = post.get("eventID", "");
        final boolean authenticated = sb.verifyAuthentication(header);
        final int item = post.getInt("item", -1);
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
            final URIMetadataNode result = theSearch.oneResult(item, timeout);
            if (result == null) return prop; // no content
            final String resultUrlstring = result.urlstring();
            final DigestURL resultURL = result.url();
            final String target = sb.getConfig(resultUrlstring.matches(target_special_pattern) ? SwitchboardConstants.SEARCH_TARGET_SPECIAL : SwitchboardConstants.SEARCH_TARGET_DEFAULT, "_self");

            final String resource = theSearch.query.domType.toString();
            final String origQ = theSearch.query.getQueryGoal().getQueryString(true);
            prop.put("content", 1); // switch on specific content
            prop.put("content_authorized", authenticated ? "1" : "0");
            final String urlhash = ASCII.String(result.hash());
            if (authenticated) { // only needed if authorized
                addAuthorizedActions(sb, prop, theSearch, resultUrlstring, resource, origQ, urlhash);
            }
            prop.putHTML("content_title", result.title());
            prop.putXML("content_title-xml", result.title());
            prop.putJSON("content_title-json", result.title());
            prop.putHTML("content_showPictures_link", resultUrlstring);
            //prop.putHTML("content_link", resultUrlstring);

// START interaction
            if (sb.getConfigBool("proxyURL.useforresults", false) && sb.getConfigBool("proxyURL", false)) {
                String modifyURL = resultUrlstring;
                // check if url is allowed to view
                final String tmprewritecfg = sb.getConfig("proxyURL.rewriteURLs", "all");
                if (tmprewritecfg.equals("all")) {
                    modifyURL = "./proxy.html?url=" + resultUrlstring;
                } else if (tmprewritecfg.equals("domainlist")) { // check if url is allowed to view
                    try {
                        if (sb.crawlStacker.urlInAcceptedDomain(new DigestURL(resultUrlstring)) == null) {
                            modifyURL = "./proxy.html?url=" + resultUrlstring;
                        }
                    } catch (final MalformedURLException e) {
                        ConcurrentLog.logException(e);
                    }
                } else if (tmprewritecfg.equals("yacy")) {
                    try {
                        if ((new DigestURL(resultUrlstring).getHost().endsWith(".yacy"))) {
                            modifyURL = "./proxy.html?url=" + resultUrlstring;
                        }
                    } catch (final MalformedURLException e) {
                        ConcurrentLog.logException(e);
                    }
                }
                prop.putXML("content_link", modifyURL); // putXML for rss
            } else {
                prop.putXML("content_link", resultUrlstring); // putXML for rss
            }
            
// END interaction

            boolean isAtomFeed = header.get(HeaderFramework.CONNECTION_PROP_EXT, "").equals("atom");
            String resultFileName = resultURL.getFileName();
            prop.putHTML("content_target", target);
            DigestURL faviconURL = null;
            if ((fileType == FileType.HTML || fileType == FileType.JSON) && !sb.isIntranetMode()) {
            	faviconURL = getFaviconURL(result, new Dimension(16, 16));
            }
            prop.putHTML("content_faviconUrl", processFaviconURL(authenticated, faviconURL));
            prop.put("content_urlhash", urlhash);
            prop.put("content_ranking", Float.toString(result.score()));
            Date[] events = result.events();
            boolean showEvent = events != null && events.length > 0 && sb.getConfig("search.navigation", "").indexOf("date",0) >= 0;
            prop.put("content_showEvent", showEvent ? 1 : 0);
            Collection<File> snapshotPaths = sb.getConfigBool("search.result.show.snapshots", true) ? Transactions.findPaths(result.url(), null, State.ANY) : null;
            if (fileType == FileType.HTML) { // html template specific settings
                prop.put("content_showDate", sb.getConfigBool("search.result.show.date", true) && !showEvent ? 1 : 0);
                prop.put("content_showSize", sb.getConfigBool("search.result.show.size", true) ? 1 : 0);
                prop.put("content_showMetadata", sb.getConfigBool("search.result.show.metadata", true) ? 1 : 0);
                prop.put("content_showParser", sb.getConfigBool("search.result.show.parser", true) ? 1 : 0);
                prop.put("content_showCitation", sb.getConfigBool("search.result.show.citation", true) ? 1 : 0);
                prop.put("content_showPictures", sb.getConfigBool("search.result.show.pictures", true) ? 1 : 0);
                prop.put("content_showCache", sb.getConfigBool("search.result.show.cache", true) && Cache.has(resultURL.hash()) ? 1 : 0);
                prop.put("content_showProxy", sb.getConfigBool("search.result.show.proxy", true) && sb.getConfigBool("proxyURL", false) ? 1 : 0);
                prop.put("content_showHostBrowser", sb.getConfigBool("search.result.show.hostbrowser", true) ? 1 : 0);
                prop.put("content_showSnapshots", snapshotPaths != null && snapshotPaths.size() > 0 && sb.getConfigBool("search.result.show.snapshots", true) ? 1 : 0);
                prop.put("content_showVocabulary", sb.getConfigBool("search.result.show.vocabulary", true) ? 1 : 0);

                if (showEvent) prop.put("content_showEvent_date", GenericFormatter.RFC1123_SHORT_FORMATTER.format(events[0]));
                prop.put("content_showDate_date", GenericFormatter.RFC1123_SHORT_FORMATTER.format(result.moddate()));
                prop.putHTML("content_showSize_sizename", RSSMessage.sizename(result.filesize()));
                prop.put("content_showMetadata_urlhash", urlhash);
                prop.put("content_showParser_urlhash", urlhash);
                prop.put("content_showCitation_urlhash", urlhash);
                prop.putHTML("content_showPictures_former", origQ);
                prop.put("content_showCache_link", resultUrlstring);
                prop.put("content_showProxy_link", resultUrlstring);
                prop.put("content_showHostBrowser_link", resultUrlstring);
                if (sb.getConfigBool("search.result.show.vocabulary", true)) {
                    int c = 0;
                    for (String key: result.getFieldNames()) {
                        if (key.startsWith("vocabulary_") && key.endsWith("_sxt")) {
                            Collection<Object> terms = result.getFieldValues(key);
                            prop.putHTML("content_showVocabulary_vocabulary_" + c + "_name", key.substring(11, key.length() - 4));
                            prop.putHTML("content_showVocabulary_vocabulary_" + c + "_terms", terms.toString());
                            c++;
                        }
                    }
                    prop.put("content_showVocabulary_vocabulary", c);
                    prop.put("content_showVocabulary", 1);
                } else {
                    prop.put("content_showVocabulary_vocabulary", 0);
                    prop.put("content_showVocabulary", 0);
                }
                if (snapshotPaths != null && snapshotPaths.size() > 0) {
                    prop.put("content_showSnapshots_link", snapshotPaths.iterator().next().getAbsolutePath());
                }
            }
            prop.put("content_urlhexhash", Seed.b64Hash2hexHash(urlhash));
            prop.putHTML("content_urlname", nxTools.shortenURLString(result.urlname(), MAX_URL_LENGTH));
            prop.put("content_date822", isAtomFeed ? ISO8601Formatter.FORMATTER.format(result.moddate()) : HeaderFramework.formatRFC1123(result.moddate()));
            if (showEvent) prop.put("content_showEvent_date822", isAtomFeed ? ISO8601Formatter.FORMATTER.format(events[0]) : HeaderFramework.formatRFC1123(events[0]));
            //prop.put("content_ybr", RankingProcess.ybr(result.hash()));
            prop.putHTML("content_size", Integer.toString(result.filesize())); // we don't use putNUM here because that number shall be usable as sorting key. To print the size, use 'sizename'
            prop.putHTML("content_sizename", RSSMessage.sizename(result.filesize()));            
            prop.putHTML("content_host", resultURL.getHost() == null ? "" : resultURL.getHost());
            prop.putXML("content_file", resultFileName); // putXML for rss
            prop.putXML("content_path", resultURL.getPath()); // putXML for rss
            prop.put("content_nl", (item == theSearch.query.offset) ? 0 : 1);
            prop.putHTML("content_publisher", result.dc_publisher());
            prop.putHTML("content_creator", result.dc_creator());// author
            prop.putHTML("content_subject", result.dc_subject());
            final Iterator<String> query = theSearch.query.getQueryGoal().getIncludeStrings();
            final StringBuilder s = new StringBuilder(theSearch.query.getQueryGoal().getIncludeSize() * 20);
            while (query.hasNext()) s.append('+').append(query.next());
            final String words = (s.length() > 0) ? s.substring(1) : "";
            prop.putHTML("content_words", words);
            prop.putHTML("content_showParser_words", words);
            prop.putHTML("content_former", origQ);
            final TextSnippet snippet = result.textSnippet();
            final String desc = (snippet == null) ? "" : snippet.descriptionline(theSearch.query.getQueryGoal());
            prop.put("content_description", desc);
            prop.putXML("content_description-xml", desc);
            prop.putJSON("content_description-json", desc);
            prop.put("content_mimetype", result.mime()); // for atom <link> type attribute
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
            if (result.doctype() == Response.DT_IMAGE) {
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
                !stealthmode) sb.heuristicSearchResults(result);
            theSearch.query.transmitcount = item + 1;
            return prop;
        }

        if (theSearch.query.contentdom == Classification.ContentDomain.IMAGE) {
            // image search; shows thumbnails
            processImage(sb, prop, item, theSearch, target_special_pattern, timeout, authenticated);
            theSearch.query.transmitcount = item + 1;
            return prop;
        }

        if ((theSearch.query.contentdom == ContentDomain.AUDIO) ||
            (theSearch.query.contentdom == ContentDomain.VIDEO) ||
            (theSearch.query.contentdom == ContentDomain.APP)) {
            // any other media content

            // generate result object
            final URIMetadataNode ms = theSearch.oneResult(item, timeout);
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

	/**
	 * Tries to retrieve favicon url from solr result document, or generates
	 * default favicon URL (i.e. "http://host/favicon.ico") from resultURL and
	 * port.
	 * 
	 * @param result
	 *            solr document result. Must not be null.
	 * @param preferredSize preferred icon size. If no one matches, most close icon is returned.
	 * @return favicon URL or null when even default favicon URL can not be generated
	 * @throws NullPointerException when one requested parameter is null
	 */
	protected static DigestURL getFaviconURL(final URIMetadataNode result, Dimension preferredSize) {
		/*
		 * We look preferably for a standard icon with preferred size, but
		 * accept as a fallback other icons below 128x128 or with no known size
		 */
		IconEntry faviconEntry = result.getFavicon(preferredSize);
		DigestURL faviconURL;
		if (faviconEntry == null) {
			try {
				String defaultFaviconURL = result.url().getProtocol() + "://" + result.url().getHost()
						+ ((result.url().getPort() != -1) ? (":" + result.url().getPort()) : "") + "/favicon.ico";
				faviconURL = new DigestURL(defaultFaviconURL);
			} catch (final MalformedURLException e1) {
				ConcurrentLog.logException(e1);
				faviconURL = null;
			}
		} else {
			faviconURL = faviconEntry.getUrl();
		}

		return faviconURL;
	}

	/**
	 * @param authenticated
	 *            true when current user is authenticated
	 * @param faviconURL
	 *            url icon of web site
	 * @return url to propose in search result or empty string when faviconURL
	 *         is null
	 */
	private static String processFaviconURL(final boolean authenticated, DigestURL faviconURL) {
		/* Only use licence code for non authentified users. For authenticated users licence would never be released and would unnecessarily fill URLLicense.permissions. */
		StringBuilder contentFaviconURL = new StringBuilder();
		if (faviconURL != null) {
			final String iconUrlExt = MultiProtocolURL.getFileExtension(faviconURL.getFileName());
		    /* Image format ouput for ViewFavicon servlet : default is png, except with gif and svg icons */
		    final String viewFaviconExt = !iconUrlExt.isEmpty() && ImageViewer.isBrowserRendered(iconUrlExt) ? iconUrlExt : "png";
		    
			contentFaviconURL.append("ViewFavicon.").append(viewFaviconExt).append("?maxwidth=16&maxheight=16&isStatic=true&quadratic");
			if (authenticated) {
				contentFaviconURL.append("&url=").append(faviconURL.toNormalform(true));
			} else {
				contentFaviconURL.append("&code=").append(URLLicense.aquireLicense(faviconURL));
			}
		}
		return contentFaviconURL.toString();
	}
	
    /**
     * Add action links reserved to authorized users. All parameters must be non null.
     * @param sb the main Switchboard instance
     * @param prop properties map to feed
     * @param theSearch search event
     * @param resultUrlstring URL of the result item
     * @param resource resource scope ("local" or "global")
     * @param origQ origin query terms
     * @param urlhash URL hash of the result item
     */
	private static void addAuthorizedActions(final Switchboard sb, final serverObjects prop,
			final SearchEvent theSearch, final String resultUrlstring, final String resource, final String origQ,
			final String urlhash) {
		// check if url exists in bookmarks
		boolean bookmarkexists = sb.bookmarksDB.getBookmark(urlhash) != null;
		prop.put("content_authorized_bookmark", !bookmarkexists);
		// bookmark icon check for YMarks
		//prop.put("content_authorized_bookmark", sb.tables.bookmarks.hasBookmark("admin", urlhash) ? "0" : "1");
		
		/* Bookmark, delete and recommend action links share the same URL prefix */
		StringBuilder linkBuilder = new StringBuilder();
		String actionLinkPrefix = linkBuilder.append("yacysearch.html?query=").append(origQ.replace(' ', '+'))
				.append("&Enter=Search&count=").append(theSearch.query.itemsPerPage()).append("&offset=")
				.append((theSearch.query.neededResults() - theSearch.query.itemsPerPage())).append("&resource=")
				.append(resource).append("&time=3").toString();
		linkBuilder.setLength(0);
		
		String encodedURLString;
		try {
			encodedURLString = URLEncoder.encode(crypt.simpleEncode(resultUrlstring), StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e1) {
			ConcurrentLog.warn("YACY_SEARCH_ITEM", "UTF-8 encoding is not supported!");
			encodedURLString = crypt.simpleEncode(resultUrlstring);
		}
		String bookmarkLink = linkBuilder.append(actionLinkPrefix).append("&bookmarkref=").append(urlhash)
				.append("&bookmarkurl=").append(encodedURLString).append("&urlmaskfilter=.*")
				.toString();
		linkBuilder.setLength(0);
		
		/* Delete and recommend action links share the same URL suffix */
		String encodedRanking;
		try {
			encodedRanking = URLEncoder.encode(crypt.simpleEncode(theSearch.query.ranking.toExternalString()), StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e1) {
			ConcurrentLog.warn("YACY_SEARCH_ITEM", "UTF-8 encoding is not supported!");
			encodedRanking = crypt.simpleEncode(resultUrlstring);
		}
		String actionLinkSuffix = linkBuilder.append(urlhash)
				.append("&urlmaskfilter=.*").append("&order=").append(encodedRanking).toString();
		linkBuilder.setLength(0);
		
		String deleteLink = linkBuilder.append(actionLinkPrefix).append("&deleteref=").append(actionLinkSuffix).toString();
		linkBuilder.setLength(0);
		String recommendLink = linkBuilder.append(actionLinkPrefix).append("&recommendref=").append(actionLinkSuffix).toString();
		linkBuilder.setLength(0);
		
		prop.put("content_authorized_bookmark_bookmarklink", bookmarkLink);
		prop.put("content_authorized_recommend_deletelink", deleteLink);
		prop.put("content_authorized_recommend_recommendlink", recommendLink);
		
		prop.put("content_authorized_recommend", (sb.peers.newsPool.getSpecific(NewsPool.OUTGOING_DB, NewsPool.CATEGORY_SURFTIPP_ADD, "url", resultUrlstring) == null) ? "1" : "0");
		prop.put("content_authorized_urlhash", urlhash);
	}
    

    /**
     * Process search of image type and feed prop object. All parameters must not be null.
     * @param sb Switchboard instance
     * @param prop result
     * @param item item index.
     * @param theSearch search event
     * @param target_special_pattern
     * @param timeout result getting timeOut
     * @param authenticated set to true when user authentication is ok
     */
	private static void processImage(final Switchboard sb, final serverObjects prop, final int item,
			final SearchEvent theSearch, final String target_special_pattern, long timeout, boolean authenticated) {
		prop.put("content", theSearch.query.contentdom.getCode() + 1); // switch on specific content
		try {
		    SearchEvent.ImageResult image = theSearch.oneImageResult(item, timeout);
		    final String imageUrlstring = image.imageUrl.toNormalform(true);
		    final String imageUrlExt = MultiProtocolURL.getFileExtension(image.imageUrl.getFileName());
		    final String target = sb.getConfig(imageUrlstring.matches(target_special_pattern) ? SwitchboardConstants.SEARCH_TARGET_SPECIAL : SwitchboardConstants.SEARCH_TARGET_DEFAULT, "_self");

		    final String license = URLLicense.aquireLicense(image.imageUrl); // this is just the license key to get the image forwarded through the YaCy thumbnail viewer, not an actual lawful license
		    /* Image format ouput for ViewImage servlet : default is png, except with gif and svg images */
		    final String viewImageExt = !imageUrlExt.isEmpty() && ImageViewer.isBrowserRendered(imageUrlExt) ? imageUrlExt : "png";
		    /* Thumb URL */
			StringBuilder thumbURLBuilder = new StringBuilder("ViewImage.").append(viewImageExt).append("?maxwidth=")
					.append(DEFAULT_IMG_WIDTH).append("&maxheight=").append(DEFAULT_IMG_HEIGHT)
					.append("&isStatic=true&quadratic");
		    /* Only use licence code for non authentified users. For authenticated users licence would never be released and would unnecessarily fill URLLicense.permissions. */
		    if(authenticated) {
		    	thumbURLBuilder.append("&url=").append(imageUrlstring);
		    } else {
		    	thumbURLBuilder.append("&code=").append(URLLicense.aquireLicense(image.imageUrl));
		    }
		    String thumbURL = thumbURLBuilder.toString();
		    prop.putHTML("content_item_hrefCache", thumbURL);
		    /* Full size preview URL */
		    if(authenticated) {
		    	prop.putHTML("content_item_hrefFullPreview", "ViewImage." + viewImageExt + "?isStatic=true&url=" + imageUrlstring);
		    } else {
		    	/* Not authenticated : full preview URL must be the same as thumb URL */
		    	prop.putHTML("content_item_hrefFullPreview", thumbURL);
		    }
		    prop.putHTML("content_item_href", imageUrlstring);
		    prop.putHTML("content_item_target", target);
		    prop.put("content_item_code", license);
		    prop.putHTML("content_item_name", shorten(image.imagetext, MAX_NAME_LENGTH));
		    prop.put("content_item_mimetype", image.mimetype);
		    prop.put("content_item_fileSize", 0);
		    
		    String itemWidth = DEFAULT_IMG_WIDTH + "px", itemHeight = DEFAULT_IMG_HEIGHT + "px", itemStyle="";
		    /* When image content is rendered by browser :
		     * - set smaller dimension to 100% in order to crop image on other dimension with CSS style 'overflow:hidden' on image container 
		     * - set negative margin top behave like ViewImage which sets an offset when cutting to square */
			if (ImageViewer.isBrowserRendered(imageUrlExt)) {
				if (image.width > image.height) {
					/* Landscape orientation */
					itemWidth = "";
					itemHeight = "100%";
					if(image.height > 0) {
						double scale = ((double)DEFAULT_IMG_HEIGHT) / ((double)image.height);
						int margin =  (int)((image.height - image.width) * (scale / 2.0));
						itemStyle = "margin-left: " + margin + "px;";
					}
				} else {
					/* Portrait orientation, or square or unknown dimensions (both equals zero) */
					itemWidth = "100%";
					itemHeight = "";
					if(image.height > image.width && image.width > 0) {
						double scale = ((double)DEFAULT_IMG_WIDTH) / ((double)image.width);
						int margin =  (int)((image.width - image.height) * (scale / 2.0));
						itemStyle = "margin-top: " + margin + "px;";
					}
				}
			}
		    prop.put("content_item_width", itemWidth);
		    prop.put("content_item_height", itemHeight);
		    prop.put("content_item_style", itemStyle);
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
