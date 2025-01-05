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

package net.yacy.htroot;

import java.awt.Dimension;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

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
import net.yacy.data.UserDB;
import net.yacy.document.parser.html.IconEntry;
import net.yacy.http.servlets.TemplateMissingParameterException;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.util.Formatter;
import net.yacy.peers.NewsPool;
import net.yacy.peers.Seed;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.search.EventTracker;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.navigator.Navigator;
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
    private static final int DEFAULT_IMG_WIDTH = 256;
    /** Default image item height in pixels */
    private static final int DEFAULT_IMG_HEIGHT = DEFAULT_IMG_WIDTH;

    //private static boolean col = true;

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
		if (post == null) {
			throw new TemplateMissingParameterException("The eventID parameter is required");
		}

        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        final String eventID = post.get("eventID", "");
        final boolean adminAuthenticated = sb.verifyAuthentication(header);

	final UserDB.Entry user = sb.userDB != null ? sb.userDB.getUser(header) : null;
	final boolean authenticated = adminAuthenticated || user != null;

        final boolean extendedSearchRights = adminAuthenticated || (user != null && user.hasRight(UserDB.AccessRight.EXTENDED_SEARCH_RIGHT));
        //final boolean bookmarkRights = adminAuthenticated || (user != null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT));

        final int item = post.getInt("item", -1);
        final RequestHeader.FileType fileType = header.fileType();

		if (post.containsKey("auth") && !adminAuthenticated && user == null) {
			/*
			 * Access to authentication protected features is explicitely requested here
			 * but no authentication is provided : ask now for authentication.
             * Wihout this, after timeout of HTTP Digest authentication nonce, browsers no more send authentication information
             * and as this page is not private, protected features would simply be hidden without asking browser again for authentication.
             * (see mantis 766 : http://mantis.tokeek.de/view.php?id=766) *
			 */
			prop.authenticationRequired();
			return prop;
		}

        // default settings for blank item
        prop.put("content", "0");
        prop.put("rss", "0");
        prop.put("references", "0");
        prop.put("rssreferences", "0");
        prop.put("dynamic", "0");
        prop.put("localQuery", "0");
        prop.put("statistics", "0");

        // find search event
        final SearchEvent theSearch = SearchEventCache.getEvent(eventID);
        if (theSearch == null) {
            // the event does not exist, show empty page
            return prop;
        }

        // dynamically update count values
        prop.put("statistics", "1");
        prop.put("statistics_offset", theSearch.query.neededResults() - theSearch.query.itemsPerPage() + 1);
        prop.put("statistics_itemscount", Formatter.number(Math.min((item < 0) ? theSearch.query.neededResults() : item + 1, theSearch.getResultCount())));
        prop.put("statistics_itemsperpage", Formatter.number(theSearch.query.itemsPerPage));
        prop.put("statistics_totalcount", Formatter.number(theSearch.getResultCount(), true));
        prop.put("statistics_localIndexCount", Formatter.number(theSearch.local_rwi_available.get() + theSearch.local_solr_stored.get() - theSearch.local_solr_evicted.get(), true));
        prop.put("statistics_remoteIndexCount", Formatter.number(theSearch.remote_rwi_available.get() + theSearch.remote_solr_available.get(), true));
        prop.put("statistics_remotePeerCount", Formatter.number(theSearch.remote_rwi_peerCount.get() + theSearch.remote_solr_peerCount.get(), true));
		prop.put("statistics_navurlBase",
				QueryParams.navurlBase(RequestHeader.FileType.HTML, theSearch.query, null, false, authenticated)
						.toString());
        prop.put("statistics_localQuery", theSearch.query.isLocal() ? "1" : "0");
        prop.put("statistics_feedRunning", Boolean.toString(!theSearch.isFeedingFinished()));
        final String target_special_pattern = sb.getConfig(SwitchboardConstants.SEARCH_TARGET_SPECIAL_PATTERN, "");
        final boolean noreferrer = sb.getConfigBool(SwitchboardConstants.SEARCH_RESULT_NOREFERRER, SwitchboardConstants.SEARCH_RESULT_NOREFERRER_DEFAULT);

        final long timeout = item == 0 ? 10000 : (theSearch.query.isLocal() ? 1000 : 3000);

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
            final String urlhash = ASCII.String(result.hash());
            if (adminAuthenticated) { // only needed if authorized
                addAuthorizedActions(sb, prop, theSearch, resultUrlstring, resource, origQ, urlhash, null);
            } else if (authenticated && user != null) {
                addAuthorizedActions(sb, prop, theSearch, resultUrlstring, resource, origQ, urlhash, user);
            } else
                prop.put("content_authorized", "0"); // disable for not authorized user
// for testing only
// result
// if local Admin - no admin_right
// if admin authent -> admin_right = true, bookmark_right = false
            if (header.isUserInRole(UserDB.AccessRight.ADMIN_RIGHT.toString())) {
                if (header.isUserInRole(UserDB.AccessRight.BOOKMARK_RIGHT.toString())) {
                    System.out.println("booki");
                }
            }
            prop.putHTML("content_title", result.title());
            prop.putXML("content_title-xml", result.title());
            prop.putJSON("content_title-json", result.title());
            prop.putHTML("content_showPictures_link", resultUrlstring);
            prop.put("content_showPictures_authSearch", authenticated);

            /* Add information about the current search navigators to let browser refresh yacysearchtrailer only if needed */
            prop.put("content_nav-generation", theSearch.getNavGeneration());

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
            prop.put("content_noreferrer", noreferrer ? 1 : 0);

// END interaction

            final boolean isAtomFeed = header.get(HeaderFramework.CONNECTION_PROP_EXT, "").equals("atom");
            final String resultFileName = resultURL.getFileName();
            prop.putHTML("content_target", target);
            DigestURL faviconURL = null;
			final boolean showFavicon = sb.getConfigBool(SwitchboardConstants.SEARCH_RESULT_SHOW_FAVICON,
					SwitchboardConstants.SEARCH_RESULT_SHOW_FAVICON_DEFAULT);

			if (((fileType == FileType.HTML && showFavicon) || fileType == FileType.JSON)
					&& (resultURL.isHTTP() || resultURL.isHTTPS())) {
				faviconURL = getFaviconURL(result, new Dimension(16, 16));
			}
            if(faviconURL == null) {
            	prop.put("content_favicon", 0);
            } else {
            	prop.put("content_favicon", 1);
            }
            prop.putHTML("content_favicon_faviconUrl", processFaviconURL(ImageViewer.hasFullViewingRights(header, sb), faviconURL));
            prop.putHTML("content_favicon_urlhash", urlhash);

            if (result.limage() == 0) {
            	if (faviconURL == null) {
            		prop.put("content_image", 0);
            	} else {
            		prop.put("content_image", 1);
                	prop.putXML("content_image_url", faviconURL.toNormalform(true));
            	}
            } else {
            	try {
            		prop.putXML("content_image_url", result.imageURL());
            		prop.put("content_image", 1);
            	} catch (final UnsupportedOperationException e) {
            		/* May occur when the document embedded images information is incomplete to retrieve at least an valid image url*/
                	prop.put("content_image", 0);

            	}
            }

            prop.put("content_urlhash", urlhash);
            prop.put("content_ranking", Float.toString(result.score()));
            final Date[] events = result.events();
            final boolean showEvent = events != null && events.length > 0 && sb.getConfig("search.navigation", "").indexOf("date",0) >= 0;
            prop.put("content_showEvent", showEvent ? 1 : 0);
            final Collection<File> snapshotPaths = sb.getConfigBool("search.result.show.snapshots", true) ? Transactions.findPaths(result.url(), null, State.ANY) : null;
            if (fileType == FileType.HTML) { // html template specific settings
				final boolean showKeywords = (sb.getConfigBool(SwitchboardConstants.SEARCH_RESULT_SHOW_KEYWORDS,
						SwitchboardConstants.SEARCH_RESULT_SHOW_KEYWORDS_DEFAULT) && !result.dc_subject().isEmpty());
                prop.put("content_showKeywords", showKeywords);
                prop.put("content_showDate", sb.getConfigBool("search.result.show.date", true) && !showEvent ? 1 : 0);
                prop.put("content_showSize", sb.getConfigBool("search.result.show.size", true) ? 1 : 0);
                prop.put("content_showMetadata", sb.getConfigBool("search.result.show.metadata", true) ? 1 : 0);
                prop.put("content_showParser", sb.getConfigBool("search.result.show.parser", true) ? 1 : 0);
                prop.put("content_showCitation", sb.getConfigBool("search.result.show.citation", true) ? 1 : 0);
                prop.put("content_showPictures", sb.getConfigBool("search.result.show.pictures", true) ? 1 : 0);
                prop.put("content_showCache", sb.getConfigBool("search.result.show.cache", true) && Cache.has(resultURL.hash()) ? 1 : 0);
                prop.put("content_showProxy", sb.getConfigBool("search.result.show.proxy", true) && sb.getConfigBool("proxyURL", false) ? 1 : 0);
                prop.put("content_showIndexBrowser", sb.getConfigBool("search.result.show.indexbrowser", true) ? 1 : 0);
                prop.put("content_showSnapshots", snapshotPaths != null && snapshotPaths.size() > 0 && sb.getConfigBool("search.result.show.snapshots", true) ? 1 : 0);
                prop.put("content_showVocabulary", sb.getConfigBool("search.result.show.vocabulary", true) ? 1 : 0);
                prop.put("content_showRanking", sb.getConfigBool("search.result.show.ranking", false) ? 1 : 0);

                if (showEvent) prop.put("content_showEvent_date", GenericFormatter.RFC1123_SHORT_FORMATTER.format(events[0]));
                if (showKeywords) { // tokenize keywords
                    final StringTokenizer stoc = new StringTokenizer(result.dc_subject()," ");
                    String rawNavQueryModifier;
                    final Navigator navi = theSearch.navigatorPlugins.get("keywords");
                    final boolean naviAvail = navi != null;
                    final int firstMaxKeywords = sb.getConfigInt(SwitchboardConstants.SEARCH_RESULT_KEYWORDS_FISRT_MAX_COUNT,
							SwitchboardConstants.SEARCH_RESULT_KEYWORDS_FISRT_MAX_COUNT_DEFAULT);
                    int i = 0;
					while (stoc.hasMoreTokens()
							&& i < firstMaxKeywords) {
                        final String word = stoc.nextToken();
                        prop.putHTML("content_showKeywords_keywords_" + i + "_tagword", word);
                        if (naviAvail) { // use query modifier if navigator available
                            rawNavQueryModifier = navi.getQueryModifier(word);
                        } else { // otherwise just use the keyword as additional query word
                            rawNavQueryModifier = word;
                        }
						prop.put("content_showKeywords_keywords_" + i + "_tagurl", QueryParams.navurl(fileType, 0,
								theSearch.query, rawNavQueryModifier, naviAvail, authenticated).toString());
                        i++;
                    }
                    prop.put("content_showKeywords_keywords", i);
                    if(stoc.hasMoreTokens()) {
                    	prop.put("content_showKeywords_moreKeywords", "1");
                    	prop.put("content_showKeywords_moreKeywords_urlhash", urlhash);
                    	i = 0;
                        while (stoc.hasMoreTokens()) {
                            final String word = stoc.nextToken();
                            prop.putHTML("content_showKeywords_moreKeywords_keywords_" + i + "_tagword", word);
                            if (naviAvail) { // use query modifier if navigator available
                                rawNavQueryModifier = navi.getQueryModifier(word);
                            } else { // otherwise just use the keyword as additional query word
                                rawNavQueryModifier = word;
                            }
    						prop.put("content_showKeywords_moreKeywords_keywords_" + i + "_tagurl", QueryParams.navurl(fileType, 0,
    								theSearch.query, rawNavQueryModifier, naviAvail, authenticated).toString());
                            i++;
                        }
                        prop.put("content_showKeywords_moreKeywords_keywords", i);
                    }
                }
                prop.put("content_showDate_date", GenericFormatter.RFC1123_SHORT_FORMATTER.format(result.moddate()));
                prop.putHTML("content_showSize_sizename", RSSMessage.sizename(result.filesize()));
                prop.put("content_showMetadata_urlhash", urlhash);
                prop.put("content_showParser_urlhash", urlhash);
                prop.put("content_showCitation_urlhash", urlhash);
                prop.putUrlEncodedHTML("content_showPictures_former", origQ);
                prop.put("content_showCache_link", resultUrlstring);
                prop.put("content_showProxy_link", resultUrlstring);
                prop.put("content_showIndexBrowser_link", resultUrlstring);
                if (sb.getConfigBool("search.result.show.vocabulary", true)) {
                    int c = 0;
                    for (final String key: result.getFieldNames()) {
                        if (key.startsWith("vocabulary_") && key.endsWith("_sxt")) {
                            final Collection<Object> terms = result.getFieldValues(key);
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
            		/* Only add a link to the eventual snapshot file in the format it is stored (no resource fetching and conversion here) */
                	String selectedExt = null, ext;
                	for(final File snapshot : snapshotPaths) {
                		ext = MultiProtocolURL.getFileExtension(snapshot.getName());
                		if("jpg".equals(ext) || "png".equals(ext)) {
                			/* Prefer snapshots in jpeg or png format */
                			selectedExt = ext;
                			break;
                		} else if("pdf".equals(ext)) {
                			selectedExt = ext;
                		} else if("xml".equals(ext) && selectedExt == null) {
                			/* Use the XML metadata snapshot in last resort */
                			selectedExt = ext;
                		}
                	}
                	if(selectedExt != null) {
                		prop.putHTML("content_showSnapshots_extension", selectedExt.toUpperCase(Locale.ROOT));
                		prop.putHTML("content_showSnapshots_link", "api/snapshot." + selectedExt + "?url=" + resultURL);
                	} else {
                		prop.put("content_showSnapshots", 0);
                	}
                }
                prop.put("content_showRanking_ranking", Float.toString(result.score()));
                prop.put("content_ranking", Float.toString(result.score()));
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
            while (query.hasNext()) {
            	if(s.length() > 0) {
            		s.append(' ');
            	}
            	s.append(query.next());
            }
            final String words = MultiProtocolURL.escape(s.toString()).toString();
            prop.putUrlEncodedHTML("content_words", words);
            prop.putUrlEncodedHTML("content_showParser_words", words);
            prop.putUrlEncodedHTML("content_former", origQ);
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
            final boolean p2pmode = sb.peers != null && sb.peers.sizeConnected() > 0 && indexReceiveGranted;
            final boolean stealthmode = p2pmode && theSearch.query.isLocal();
            if ((sb.getConfigBool(SwitchboardConstants.HEURISTIC_SEARCHRESULTS, false) ||
                (sb.getConfigBool(SwitchboardConstants.GREEDYLEARNING_ACTIVE, false) && sb.getConfigBool(SwitchboardConstants.GREEDYLEARNING_ENABLED, false) && Memory.getSystemLoadAverage() < 1.0)) &&
                !stealthmode) sb.heuristicSearchResults(result);
            theSearch.query.transmitcount = item + 1;
            return prop;
        }

        if (theSearch.query.contentdom == Classification.ContentDomain.IMAGE) {
            // image search; shows thumbnails
            processImage(sb, prop, item, theSearch, target_special_pattern, timeout, ImageViewer.hasFullViewingRights(header, sb), noreferrer);
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
                if(theSearch.query.contentdom == ContentDomain.AUDIO && extendedSearchRights) {
            		/*
            		 * Display HTML5 embedded audio only to authenticated users with extended search rights to prevent any media redistribution issue
            		 */
            		processEmbedAudio(prop, theSearch, ms);
                }else {
                	prop.put("content_item_embed", false);
                }
                prop.put("content_item_noreferrer", noreferrer ? 1 : 0);
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
	 *
	 * @param prop      the target properties
	 * @param theSearch the search event
	 * @param result    a result entry
	 */
	private static void processEmbedAudio(final serverObjects prop, final SearchEvent theSearch,
			final URIMetadataNode result) {
		final String mediaType = result.mime();

		if (mediaType != null && mediaType.startsWith("audio/")) {
			/*
			 * content-type is known to be audio : each browser has its own set of supported
			 * audio subtypes, so the browser will then handle itself eventual report about
			 * unsupported media format
			 */
			prop.put("content_item_embed", true);
			prop.put("content_item_embed_list", false);
			prop.put("content_item_embed_audioSources", 1);
			appendEmbeddedAudio(result, result.url(), prop, "content_item_embed_audioSources_0");
			prop.put("content_item_embed_audioSources_0_list", false);
		} else if (result.laudio() > 0 && !theSearch.query.isStrictContentDom()) {
			/*
			 * The result media type is not audio, but there are some links to audio
			 * resources : render a limited list of embedded audio elements
			 */
			final TreeSet<MultiProtocolURL> audioLinks = new TreeSet<>(
					Comparator.comparing(MultiProtocolURL::getHost).thenComparing(MultiProtocolURL::getFile));
			final int firstAudioLinksLimit = 3;
			final int secondAudioLinksLimit = 50;

			filterAudioLinks(URIMetadataNode.getLinks(result, false), audioLinks, result.laudio());
			filterAudioLinks(URIMetadataNode.getLinks(result, true), audioLinks, result.laudio());

			if (!audioLinks.isEmpty()) {
				prop.put("content_item_embed", true);
				final boolean hasMoreThanOne = audioLinks.size() > 1;
				prop.put("content_item_embed_list", hasMoreThanOne);
				prop.put("content_item_embed_audioSources", Math.min(audioLinks.size(), firstAudioLinksLimit));
				final Iterator<MultiProtocolURL> linksIter = audioLinks.iterator();
				for (int i = 0; linksIter.hasNext() && i < firstAudioLinksLimit; i++) {
					appendEmbeddedAudio(result, linksIter.next(), prop, "content_item_embed_audioSources_" + i);
					prop.put("content_item_embed_audioSources_" + i + "_list", hasMoreThanOne);
				}
				if (audioLinks.size() > firstAudioLinksLimit) {
					prop.put("content_item_embed_moreAudios", true);
					prop.put("content_item_embed_moreAudios_firstLimit", firstAudioLinksLimit);
					prop.put("content_item_embed_moreAudios_hiddenCount",
							String.valueOf(audioLinks.size() - firstAudioLinksLimit));
					prop.put("content_item_embed_moreAudios_expandableCount",
							String.valueOf(Math.min(audioLinks.size(), secondAudioLinksLimit) - firstAudioLinksLimit));
					prop.put("content_item_embed_moreAudios_urlhash", ASCII.String(result.hash()));

					prop.put("content_item_embed_moreAudios_audioSources",
							Math.min(audioLinks.size(), secondAudioLinksLimit) - firstAudioLinksLimit);
					for (int i = 0; linksIter.hasNext() && i < (secondAudioLinksLimit - firstAudioLinksLimit); i++) {
						appendEmbeddedAudio(result, linksIter.next(), prop,
								"content_item_embed_moreAudios_audioSources_" + i);
					}
				} else {
					prop.put("content_item_embed_moreAudios", false);
				}
				prop.put("content_item_embed_moreAudios_evenMore", audioLinks.size() > secondAudioLinksLimit);
				if (audioLinks.size() > secondAudioLinksLimit) {
					prop.put("content_item_embed_moreAudios_evenMore_count",
							String.valueOf(audioLinks.size() - secondAudioLinksLimit));
					prop.put("content_item_embed_moreAudios_evenMore_urlhash", ASCII.String(result.hash()));
				}
			} else {
				prop.put("content_item_embed", false);
			}
		}
	}

    /**
     * Write the properties of an embedded audio element to prop. All parameters must not be null.
     * @param mainResult the result entry to which the audio link belongs
     * @param audioLink an audio link URL
     * @param prop the target properties
     * @param propPrefix the prefix to use when appending prop
     */
	private static void appendEmbeddedAudio(final URIMetadataNode mainResult,
			final MultiProtocolURL audioLink, final serverObjects prop, final String propPrefix) {
		prop.putHTML(propPrefix + "_href", audioLink.toString());

		/* Add a title to help user distinguish embedded elements of the list */
		final String title;
		if(audioLink.getHost().equals(mainResult.url().getHost())) {
			/* Inbound link : the file name is sufficient */
			title = shorten(audioLink.getFileName(), MAX_NAME_LENGTH);
		} else {
			/* Outbound link : it may help to know where the file is hosted without having to inspect the html element */
			title = nxTools.shortenURLString(audioLink.toString(), MAX_URL_LENGTH);
		}
		prop.putHTML(propPrefix+ "_title", title);
	}

	/**
	 * Add to the target set, valid URLs from the iterator that are classified as
	 * audio from their file name extension.
	 *
	 * @param linksIter     an iterator on URL strings
	 * @param target        the target set to fill
	 * @param targetMaxSize the maximum target set size
	 */
	protected static void filterAudioLinks(final Iterator<String> linksIter, final Set<MultiProtocolURL> target,
			final int targetMaxSize) {
		while (linksIter.hasNext() && target.size() < targetMaxSize) {
			final String linkStr = linksIter.next();
			try {
				final MultiProtocolURL url = new MultiProtocolURL(linkStr);
				if (Classification.isAudioExtension(MultiProtocolURL.getFileExtension(url.getFileName()))) {
					target.add(url);
				}
			} catch (final MalformedURLException ignored) {
				/* Continue to next link */
			}
		}
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
	protected static DigestURL getFaviconURL(final URIMetadataNode result, final Dimension preferredSize) {
		/*
		 * We look preferably for a standard icon with preferred size, but
		 * accept as a fallback other icons below 128x128 or with no known size
		 */
		final IconEntry faviconEntry = result.getFavicon(preferredSize);
		DigestURL faviconURL;
		if (faviconEntry == null) {
			try {
				final String defaultFaviconURL = result.url().getProtocol() + "://" + result.url().getHost()
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
	 * @param hasFullViewingRights
	 *            true when current user has full favicon viewing rights
	 * @param faviconURL
	 *            url icon of web site
	 * @return url to propose in search result or empty string when faviconURL
	 *         is null
	 */
	private static String processFaviconURL(final boolean hasFullViewingRights, final DigestURL faviconURL) {
		/* Only use licence code for non authentified users. For authenticated users licence would never be released and would unnecessarily fill URLLicense.permissions. */
		final StringBuilder contentFaviconURL = new StringBuilder();
		if (faviconURL != null) {
			final String iconUrlExt = MultiProtocolURL.getFileExtension(faviconURL.getFileName());
		    /* Image format ouput for ViewFavicon servlet : default is png, except with gif and svg icons */
		    final String viewFaviconExt = !iconUrlExt.isEmpty() && ImageViewer.isBrowserRendered(iconUrlExt) ? iconUrlExt : "png";

			contentFaviconURL.append("ViewFavicon.").append(viewFaviconExt).append("?maxwidth=16&maxheight=16&isStatic=true&quadratic");
			if (hasFullViewingRights) {
				contentFaviconURL.append("&url=").append(faviconURL.toNormalform(true));
			} else {
				contentFaviconURL.append("&code=").append(URLLicense.aquireLicense(faviconURL));
			}
		}
		return contentFaviconURL.toString();
	}

    /**
     * Add action links reserved to authorized users (adminRights). All
     * parameters must be non null.
     *
     * @param sb the main Switchboard instance
     * @param prop properties map to feed
     * @param theSearch search event
     * @param resultUrlstring URL of the result item
     * @param resource resource scope ("local" or "global")
     * @param origQ origin query terms
     * @param urlhash URL hash of the result item
     * @param user current user or null if current user is admin
     */
    private static void addAuthorizedActions(final Switchboard sb, final serverObjects prop,
            final SearchEvent theSearch, final String resultUrlstring, final String resource, final String origQ,
            final String urlhash, final UserDB.Entry user) {
        // check if url exists in bookmarks
        final boolean bookmarkexists = sb.bookmarksDB.getBookmark(urlhash) != null;
        if (user == null || user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT)) {
            prop.put("content_authorized_bookmark", !bookmarkexists);
        } else
            prop.put("content_authorized_bookmark", "0");
/*      boolean blacklistislisted = false;
        try {
            DigestURL durl = new DigestURL(resultUrlstring);
            blacklistislisted = sb.urlBlacklist.isListed(Blacklist.BlacklistType.SEARCH, durl);
        } catch (Exception e) {}
*/
        final StringBuilder linkBuilder = QueryParams.navurl(RequestHeader.FileType.HTML, theSearch.query.offset / theSearch.query.itemsPerPage(),
                theSearch.query, null, false, true);
        final int baseUrlLength = linkBuilder.length();

        String encodedURLString;
        try {
            encodedURLString = URLEncoder.encode(crypt.simpleEncode(resultUrlstring), StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e1) {
            ConcurrentLog.warn("YACY_SEARCH_ITEM", "UTF-8 encoding is not supported!");
            encodedURLString = crypt.simpleEncode(resultUrlstring);
        }
        final String bookmarkLink = linkBuilder.append("&bookmarkurl=").append(encodedURLString).append("&bookmarkref=" + urlhash).toString();
        linkBuilder.setLength(baseUrlLength);

        final String deleteLink = linkBuilder.append("&deleteref=").append(urlhash).toString();
        linkBuilder.setLength(baseUrlLength);

        final String recommendLink = linkBuilder.append("&recommendref=").append(urlhash).toString();
        linkBuilder.setLength(baseUrlLength);

        final String blacklistLink = linkBuilder.append("&blacklisturl=").append(encodedURLString).toString();
        linkBuilder.setLength(baseUrlLength); // cut off - for next new append

        prop.put("content_authorized_blacklist_blacklistlink", blacklistLink);
        prop.put("content_authorized_bookmark_bookmarklink", bookmarkLink);
        prop.put("content_authorized_recommend_deletelink", deleteLink);
        prop.put("content_authorized_recommend_recommendlink", recommendLink);

        if (user == null || user.hasRight(UserDB.AccessRight.ADMIN_RIGHT)) {
            prop.put("content_authorized_recommend", (sb.peers.newsPool.getSpecific(NewsPool.OUTGOING_DB, NewsPool.CATEGORY_SURFTIPP_ADD, "url", resultUrlstring) == null) ? "1" : "0");
            prop.put("content_authorized_blacklist", "1");
        } else {
            prop.put("content_authorized_recommend", "0");
            prop.put("content_authorized_blacklist", "0");
        }
        // prop.put("content_authorized_urlhash", urlhash); // not used 2022-02-09
        prop.put("content_authorized", "1"); // enable authorized icons/content
    }


    /**
     * Process search of image type and feed prop object. All parameters must not be null.
     * @param sb Switchboard instance
     * @param prop result
     * @param item item index.
     * @param theSearch search event
     * @param target_special_pattern
     * @param timeout result getting timeOut
     * @param fullViewingRights set to true when current user has full image viewing rights
     * @param noreferrer set to true when the noreferrer link type should be added to the original image source links
     */
	private static void processImage(final Switchboard sb, final serverObjects prop, final int item,
			final SearchEvent theSearch, final String target_special_pattern, final long timeout, final boolean fullViewingRights, final boolean noreferrer) {
		prop.put("content", theSearch.query.contentdom.getCode() + 1); // switch on specific content
		try {
		    final SearchEvent.ImageResult image = theSearch.oneImageResult(item, timeout, theSearch.query.isStrictContentDom());
		    final String imageUrlstring = image.imageUrl.toNormalform(true);
		    final String imageUrlExt = MultiProtocolURL.getFileExtension(image.imageUrl.getFileName());
		    final String target = sb.getConfig(imageUrlstring.matches(target_special_pattern) ? SwitchboardConstants.SEARCH_TARGET_SPECIAL : SwitchboardConstants.SEARCH_TARGET_DEFAULT, "_self");

		    final String license = URLLicense.aquireLicense(image.imageUrl); // this is just the license key to get the image forwarded through the YaCy thumbnail viewer, not an actual lawful license
		    /* Image format ouput for ViewImage servlet : default is png, except with gif and svg images */
		    final String viewImageExt = !imageUrlExt.isEmpty() && ImageViewer.isBrowserRendered(imageUrlExt) ? imageUrlExt : "png";
		    /* Thumb URL */
			final StringBuilder thumbURLBuilder = new StringBuilder("ViewImage.").append(viewImageExt).append("?maxwidth=")
					.append(DEFAULT_IMG_WIDTH).append("&maxheight=").append(DEFAULT_IMG_HEIGHT)
					.append("&isStatic=true&quadratic");
		    /* Only use licence code for non authentified users. For authenticated users licence would never be released and would unnecessarily fill URLLicense.permissions. */
		    if(fullViewingRights) {
		    	thumbURLBuilder.append("&url=").append(imageUrlstring);
		    } else {
		    	thumbURLBuilder.append("&code=").append(URLLicense.aquireLicense(image.imageUrl));
		    }
		    final String thumbURL = thumbURLBuilder.toString();
		    prop.putHTML("content_item_hrefCache", thumbURL);
		    /* Full size preview URL */
		    if(fullViewingRights) {
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
						final double scale = ((double)DEFAULT_IMG_HEIGHT) / ((double)image.height);
						final int margin =  (int)((image.height - image.width) * (scale / 2.0));
						itemStyle = "margin-left: " + margin + "px;";
					}
				} else {
					/* Portrait orientation, or square or unknown dimensions (both equals zero) */
					itemWidth = "100%";
					itemHeight = "";
					if(image.height > image.width && image.width > 0) {
						final double scale = ((double)DEFAULT_IMG_WIDTH) / ((double)image.width);
						final int margin =  (int)((image.width - image.height) * (scale / 2.0));
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
		    prop.put("content_item_noreferrer", noreferrer ? 1 : 0);
		    prop.putXML("content_item_source-xml", image.sourceUrl.toNormalform(true));
		    prop.put("content_item_sourcedom", image.sourceUrl.getHost());
		    prop.put("content_item_nl", (item == theSearch.query.offset) ? 0 : 1);
		    prop.put("content_item", 1);
		} catch (final MalformedURLException e) {
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
