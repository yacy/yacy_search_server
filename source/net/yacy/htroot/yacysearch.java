// yacysearch.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
//
// You must compile this file with
// javac -classpath .:../classes yacysearch.java
// if the shell's current path is HTROOT

package net.yacy.htroot;

import static net.yacy.repository.BlacklistHelper.addBlacklistEntry;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.http.HttpStatus;

import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.FederateSearchManager;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.geo.GeoLocation;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.lod.vocabulary.Tagging.Metatag;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.BookmarksDB.Bookmark;
import net.yacy.data.DidYouMean;
import net.yacy.data.UserDB;
import net.yacy.document.LibraryProvider;
import net.yacy.document.Tokenizer;
import net.yacy.http.servlets.TemplateProcessingException;
import net.yacy.http.servlets.YaCyDefaultServlet;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.kelondro.util.Formatter;
import net.yacy.kelondro.util.ISO639;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.SetTools;
import net.yacy.peers.EventChannel;
import net.yacy.peers.NewsPool;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.repository.Blacklist;
import net.yacy.search.EventTracker;
import net.yacy.search.SearchAccessRateConstants;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Segment;
import net.yacy.search.query.AccessTracker;
import net.yacy.search.query.QueryGoal;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.query.SearchEventType;
import net.yacy.search.ranking.RankingProfile;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;
import net.yacy.utils.crypt;

public class yacysearch {

    public static serverObjects respond(
        final RequestHeader header,
        final serverObjects post,
        final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        sb.localSearchLastAccess = System.currentTimeMillis();

        String authenticatedUserName = null;
        final boolean adminAuthenticated = sb.verifyAuthentication(header);
        final boolean searchAllowed = sb.getConfigBool(SwitchboardConstants.PUBLIC_SEARCHPAGE, true) || adminAuthenticated;

        boolean extendedSearchRights = adminAuthenticated;
        boolean bookmarkRights = false;
        if (adminAuthenticated) {
            authenticatedUserName = sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin");
        } else {
            final UserDB.Entry user = sb.userDB != null ? sb.userDB.getUser(header) : null;
            if (user != null) {
                extendedSearchRights = user.hasRight(UserDB.AccessRight.EXTENDED_SEARCH_RIGHT);
                authenticatedUserName = user.getUserName();
                bookmarkRights = user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT);
            }
        }

        final boolean localhostAccess = header.accessFromLocalhost();
        final String promoteSearchPageGreeting =
            (env.getConfigBool(SwitchboardConstants.GREETING_NETWORK_NAME, false)) ? env.getConfig(
                "network.unit.description",
                "") : env.getConfig(SwitchboardConstants.GREETING, "");
        final String client = header.getRemoteAddr(); // the search client who initiated the search

        // in case that the crawler is running and the search user is the peer admin, we expect that the user wants to check recently crawled document
        // to ensure that recent crawl results are inside the search results, we do a soft commit here. This is also important for live demos!
        if (extendedSearchRights && sb.getThread(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL).getJobCount() > 0) {
            sb.index.fulltext().commit(true);
        }
        final boolean focus  = (post == null) ? true : post.get("focus", "1").equals("1");
        // get query
        String originalquerystring = (post == null) ? "" : post.get("query", post.get("search", "")).trim();
        originalquerystring = originalquerystring.replace('<', ' ').replace('>', ' '); // light xss protection
        String querystring = originalquerystring;
        CacheStrategy snippetFetchStrategy = (post == null) ? null : CacheStrategy.parse(post.get("verify", sb.getConfig("search.verify", "")));

        final servletProperties prop = new servletProperties();
        prop.put("topmenu", sb.getConfigBool("publicTopmenu", true) ? 1 : 0);
        prop.put("authSearch", authenticatedUserName != null);

        // produce vocabulary navigation sidebars
        final Collection<Tagging> vocabularies = LibraryProvider.autotagging.getVocabularies();
        int j = 0;
        for (final Tagging v: vocabularies) {
            prop.put("sidebarVocabulary_" + j + "_vocabulary", v.getName());
            j++;
        }
        prop.put("sidebarVocabulary", j);

        // get segment
        final Segment indexSegment = sb.index;

        final String EXT = header.get(HeaderFramework.CONNECTION_PROP_EXT, "");
        final boolean rss = "rss.atom".contains(EXT);
        final boolean json = EXT.equals("json");
        prop.put("promoteSearchPageGreeting", promoteSearchPageGreeting);

        // adding some additional properties needed for the rss feed
        final String peerContext = YaCyDefaultServlet.getContext(header, sb);
        prop.put("searchBaseURL", peerContext + "/yacysearch.html");
        prop.put("rssYacyImageURL", peerContext + "/env/grafics/yacy.png");
        prop.put("thisaddress", peerContext);
        final boolean clustersearch = sb.isRobinsonMode() && sb.getConfig(SwitchboardConstants.CLUSTER_MODE, "").equals(SwitchboardConstants.CLUSTER_MODE_PUBLIC_CLUSTER);
        final boolean indexReceiveGranted = sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW_SEARCH, true) || clustersearch;
        final boolean p2pmode = sb.peers != null && sb.peers.sizeConnected() > 0 && indexReceiveGranted;
        boolean global = post == null || (!post.get("resource-switch", post.get("resource", "global")).equals("local") && p2pmode);
        final boolean stealthmode = p2pmode && !global;

        if ( post == null || indexSegment == null || env == null || !searchAllowed ) {
            if (indexSegment == null) ConcurrentLog.info("yacysearch", "indexSegment == null");
            // we create empty entries for template strings
            prop.put("searchagain", "0");
            prop.put("former", "");
            prop.put("count", "10");
            prop.put("offset", "0");
            prop.put("resource", "global");
            prop.put("urlmaskfilter", (post == null) ? ".*" : post.get("urlmaskfilter", ".*"));
            prop.put("prefermaskfilter", (post == null) ? "" : post.get("prefermaskfilter", ""));
            prop.put("indexof", "off");
            prop.put("constraint", "");
            prop.put("depth", "0");
            prop.put("localQuery", "0");
            prop.put(
                "search.verify",
                (post == null) ? sb.getConfig("search.verify", "iffresh") : post.get("verify", "iffresh"));
            prop.put(
                "search.navigation",
                (post == null) ? sb.getConfig("search.navigation", "all") : post.get("nav", "all"));
            prop.put("contentdom", "text");
            prop.put("strictContentDom", "false");
            prop.put("contentdomCheckText", "1");
            prop.put("contentdomCheckAudio", "0");
            prop.put("contentdomCheckVideo", "0");
            prop.put("contentdomCheckImage", "0");
            prop.put("contentdomCheckApp", "0");
            prop.put("excluded", "0");
            prop.put("results", "");
            prop.put("resultTable", "0");
            prop.put("num-results", searchAllowed ? "0" : "4");
            prop.put("num-results_totalcount", 0);
            prop.put("num-results_offset", 0);
            prop.put("num-results_itemsPerPage", 10);
            prop.put("geoinfo", "0");
            prop.put("rss_queryenc", "");
            prop.put("meanCount", 5);
            prop.put("eventID",""); // mandatory parameter for yacysearchtrailer/yacysearchitem includes
            return prop;
        }

        if (post.containsKey("auth") && authenticatedUserName == null) {
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

        // check for JSONP
        if ( post.containsKey("callback") ) {
            final String jsonp = post.get("callback") + "([";
            prop.put("jsonp-start", jsonp);
            prop.put("jsonp-end", "])");
        } else {
            prop.put("jsonp-start", "");
            prop.put("jsonp-end", "");
        }

        // Adding CORS Access header for yacysearch.rss output
        if ( rss ) {
            final ResponseHeader outgoingHeader = new ResponseHeader(200);
            outgoingHeader.put(HeaderFramework.CORS_ALLOW_ORIGIN, "*");
            prop.setOutgoingHeader(outgoingHeader);
        }

        // time zone
        final int timezoneOffset = post.getInt("timezoneOffset", 0);

        // collect search attributes

        // check an determine items per page (max of [100 or configured default]}
        final int defaultItemsPerPage = sb.getConfigInt(SwitchboardConstants.SEARCH_ITEMS, 10);
        int itemsPerPage = post.getInt("maximumRecords", post.getInt("count", post.getInt("rows", defaultItemsPerPage))); // requested or default // SRU syntax with old property as alternative
        // whatever admin has set as default, that's always ok
        if (itemsPerPage > defaultItemsPerPage && itemsPerPage > 100) { // if above hardcoded 100 limit restrict request (except default allows more)
            // search option (index.html) offers up to 100 (that defines the lower limit available to request)
            itemsPerPage = Math.max((snippetFetchStrategy != null && snippetFetchStrategy.isAllowedToFetchOnline() ? 100 : 1000), defaultItemsPerPage);
        }

        int startRecord = post.getInt("startRecord", post.getInt("offset", post.getInt("start", 0)));

        final boolean indexof = (post != null && post.get("indexof", "").equals("on"));

        String prefermask = (post == null) ? "" : post.get("prefermaskfilter", "");
        if ( !prefermask.isEmpty() && prefermask.indexOf(".*", 0) < 0 ) {
            prefermask = ".*" + prefermask + ".*";
        }

        Bitfield constraint =
            (post != null && post.containsKey("constraint") && !post.get("constraint", "").isEmpty())
                ? new Bitfield(4, post.get("constraint", "______"))
                : null;
        if ( indexof ) {
            constraint = new Bitfield(4);
            constraint.set(Tokenizer.flag_cat_indexof, true);
        }

        // SEARCH
        final boolean intranetMode = sb.isIntranetMode() || sb.isAllIPMode();

        // increase search statistic counter
        if ( !global ) {
            // we count only searches on the local peer here, because global searches
            // are counted on the target peer to preserve privacy of the searcher
            if ( extendedSearchRights ) {
                // local or authenticated search requests are counted separately
                // because they are not part of a public available peer statistic
                sb.searchQueriesRobinsonFromLocal++;
            } else {
                // robinson-searches from non-authenticated requests are public
                // and may be part of the public available statistic
                sb.searchQueriesRobinsonFromRemote++;
            }
        }

        // find search domain
        final Classification.ContentDomain contentdom = post == null || !post.containsKey("contentdom") ? ContentDomain.ALL : ContentDomain.contentdomParser(post.get("contentdom", "all"));

        // Strict/extended content domain constraint : configured setting may be overriden by request param
        final boolean strictContentDom = !Boolean.FALSE.toString().equalsIgnoreCase(post.get("strictContentDom",
                sb.getConfig(SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM,
                        String.valueOf(SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM_DEFAULT))));

        /* Maximum number of suggestions to display in the first results page */
        final int meanMax = post.getInt("meanCount", 0);

        boolean jsResort = global
                && (contentdom == ContentDomain.ALL || contentdom == ContentDomain.TEXT) // For now JavaScript resorting can only be applied for text search
                && sb.getConfigBool(SwitchboardConstants.SEARCH_JS_RESORT, SwitchboardConstants.SEARCH_JS_RESORT_DEFAULT);

        // check the search tracker
        TreeSet<Long> trackerHandles = sb.localSearchTracker.get(client);
        if ( trackerHandles == null ) {
            trackerHandles = new TreeSet<Long>();
        }
        boolean block = false;
        if ( Domains.matchesList(client, sb.networkWhitelist) ) {
            ConcurrentLog.info("LOCAL_SEARCH", "ACCESS CONTROL: WHITELISTED CLIENT FROM "
                + client
                + " gets no search restrictions");
        } else if ( Domains.matchesList(client, sb.networkBlacklist) ) {
            global = false;
            if ( snippetFetchStrategy != null ) {
                snippetFetchStrategy = null;
            }
            block = true;
            prop.put("num-results_blockReason", 1);
            ConcurrentLog.warn("LOCAL_SEARCH", "ACCESS CONTROL: BLACKLISTED CLIENT FROM "
                + client
                + " gets no permission to search");
            if (!"html".equals(EXT)) {
                /* API request : return the relevant HTTP status */
                throw new TemplateProcessingException("You are not allowed to search the web with this peer.",
                        HttpStatus.SC_FORBIDDEN);
            }
        } else if ( !extendedSearchRights && !localhostAccess && !intranetMode ) {
            // in case that we do a global search or we want to fetch snippets, we check for DoS cases
            final int accInThreeSeconds;
            final int accInOneMinute;
            final int accInTenMinutes;
            synchronized ( trackerHandles ) {
                accInThreeSeconds =
                    trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() - 3000)).size();
                accInOneMinute =
                    trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() - 60000)).size();
                accInTenMinutes =
                    trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() - 600000)).size();
            }
            // protections against too strong YaCy network load, reduces remote search
            if ( global ) {
                if (accInTenMinutes >= sb.getConfigInt(SearchAccessRateConstants.PUBLIC_MAX_P2P_ACCESS_10MN.getKey(),
                        SearchAccessRateConstants.PUBLIC_MAX_P2P_ACCESS_10MN.getDefaultValue())
                        || accInOneMinute >= sb.getConfigInt(
                                SearchAccessRateConstants.PUBLIC_MAX_P2P_ACCESS_1MN.getKey(),
                                SearchAccessRateConstants.PUBLIC_MAX_P2P_ACCESS_1MN.getDefaultValue())
                        || accInThreeSeconds >= sb.getConfigInt(
                                SearchAccessRateConstants.PUBLIC_MAX_P2P_ACCESS_3S.getKey(),
                                SearchAccessRateConstants.PUBLIC_MAX_P2P_ACCESS_3S.getDefaultValue())) {
                    global = false;
                    jsResort = false;
                    ConcurrentLog.warn("LOCAL_SEARCH", "ACCESS CONTROL: CLIENT FROM "
                        + client
                        + ": "
                        + accInThreeSeconds
                        + "/3s, "
                        + accInOneMinute
                        + "/60s, "
                        + accInTenMinutes
                        + "/600s, "
                        + " requests, disallowed global search");
                } else if (accInTenMinutes >= sb.getConfigInt(SearchAccessRateConstants.PUBLIC_MAX_P2P_JSRESORT_ACCESS_10MN.getKey(),
                        SearchAccessRateConstants.PUBLIC_MAX_P2P_JSRESORT_ACCESS_10MN.getDefaultValue())
                        || accInOneMinute >= sb.getConfigInt(
                                SearchAccessRateConstants.PUBLIC_MAX_P2P_JSRESORT_ACCESS_1MN.getKey(),
                                SearchAccessRateConstants.PUBLIC_MAX_P2P_JSRESORT_ACCESS_1MN.getDefaultValue())
                        || accInThreeSeconds >= sb.getConfigInt(
                                SearchAccessRateConstants.PUBLIC_MAX_P2P_JSRESORT_ACCESS_3S.getKey(),
                                SearchAccessRateConstants.PUBLIC_MAX_P2P_JSRESORT_ACCESS_3S.getDefaultValue())) {
                    jsResort = false;
                    ConcurrentLog.warn("LOCAL_SEARCH", "ACCESS CONTROL: CLIENT FROM "
                        + client
                        + ": "
                        + accInThreeSeconds
                        + "/3s, "
                        + accInOneMinute
                        + "/60s, "
                        + accInTenMinutes
                        + "/600s, "
                        + " requests, disallowed JavaScript resorting of global search results");
                }
            }
            // protection against too many remote server snippet loads (protects traffic on server)
            if ( snippetFetchStrategy != null && snippetFetchStrategy.isAllowedToFetchOnline() ) {
                if (accInTenMinutes >= sb.getConfigInt(
                        SearchAccessRateConstants.PUBLIC_MAX_REMOTE_SNIPPET_ACCESS_10MN.getKey(),
                        SearchAccessRateConstants.PUBLIC_MAX_REMOTE_SNIPPET_ACCESS_10MN.getDefaultValue())
                        || accInOneMinute >= sb.getConfigInt(
                                SearchAccessRateConstants.PUBLIC_MAX_REMOTE_SNIPPET_ACCESS_1MN.getKey(),
                                SearchAccessRateConstants.PUBLIC_MAX_REMOTE_SNIPPET_ACCESS_1MN.getDefaultValue())
                        || accInThreeSeconds >= sb.getConfigInt(
                                SearchAccessRateConstants.PUBLIC_MAX_REMOTE_SNIPPET_ACCESS_3S.getKey(),
                                SearchAccessRateConstants.PUBLIC_MAX_REMOTE_SNIPPET_ACCESS_3S.getDefaultValue())) {
                    snippetFetchStrategy = CacheStrategy.CACHEONLY;
                    ConcurrentLog.warn("LOCAL_SEARCH", "ACCESS CONTROL: CLIENT FROM "
                        + client
                        + ": "
                        + accInThreeSeconds
                        + "/3s, "
                        + accInOneMinute
                        + "/60s, "
                        + accInTenMinutes
                        + "/600s, "
                        + " requests, disallowed remote snippet loading");
                }
            }
            // general load protection
            String timePeriodMsg = "";
            if (accInTenMinutes >= sb.getConfigInt(SearchAccessRateConstants.PUBLIC_MAX_ACCESS_10MN.getKey(),
                    SearchAccessRateConstants.PUBLIC_MAX_ACCESS_10MN.getDefaultValue())) {
                block = true;
                timePeriodMsg = "ten minutes";
                prop.put("num-results_blockReason", 2);
            } else if (accInOneMinute >= sb.getConfigInt(SearchAccessRateConstants.PUBLIC_MAX_ACCESS_1MN.getKey(),
                    SearchAccessRateConstants.PUBLIC_MAX_ACCESS_1MN.getDefaultValue())) {
                block = true;
                timePeriodMsg = "one minute";
                prop.put("num-results_blockReason", 3);
            } else if (accInThreeSeconds >= sb.getConfigInt(SearchAccessRateConstants.PUBLIC_MAX_ACCESS_3S.getKey(),
                    SearchAccessRateConstants.PUBLIC_MAX_ACCESS_3S.getDefaultValue())) {
                block = true;
                timePeriodMsg = "three seconds";
                prop.put("num-results_blockReason", 4);
            }
            if(block) {
                ConcurrentLog.warn("LOCAL_SEARCH", "ACCESS CONTROL: CLIENT FROM "
                    + client
                    + ": "
                    + accInThreeSeconds
                    + "/3s, "
                    + accInOneMinute
                    + "/60s, "
                    + accInTenMinutes
                    + "/600s, "
                    + " requests, disallowed search");
                if (!"html".equals(EXT)) {
                    /*
                     * API request : return the relevant HTTP status (429 - Too Many Requests - see
                     * https://tools.ietf.org/html/rfc6585#section-4)
                     */
                    throw new TemplateProcessingException(
                            "You have reached the maximum allowed number of accesses to this search service within "
                                    + timePeriodMsg + ". Please try again later or log in as administrator or as a user with extended search right.",
                            429);
                }
            }
        }

        if (block) {
            prop.put("num-results", 5);
        } else {
            String urlmask = (post == null) ? ".*" : post.get("urlmaskfilter", ".*"); // the expression must be a subset of the java Match syntax described in http://lucene.apache.org/core/4_4_0/core/org/apache/lucene/util/automaton/RegExp.html
            String tld = null;
            String inlink = null;

            // check available memory and clean up if necessary
            if ( !MemoryControl.request(8000000L, false) ) {
                indexSegment.clearCaches();
                SearchEventCache.cleanupEvents(false);
            }

            final RankingProfile ranking = sb.getRanking();
            final QueryModifier modifier = new QueryModifier(timezoneOffset);
            querystring = modifier.parse(querystring);
            if (modifier.sitehost != null && modifier.sitehost.length() > 0 && querystring.length() == 0) querystring = "*"; // allow to search for all documents on a host

            // read collection
            modifier.collection = post.get("collection", modifier.collection); // post arguments may overrule parsed collection values

            final int stp = querystring.indexOf('*');
            if (stp >= 0) {
                // if the star appears as a single entry, use the catchallstring
                if (querystring.length() == 1) {
                    querystring = Segment.catchallString;
                } else {
                    querystring = querystring.replaceAll("\\* ", Segment.catchallString + " ").replace(" \\*", " " + Segment.catchallString);
                }
            }
            if ( querystring.indexOf("/near", 0) >= 0 ) {
                querystring = querystring.replace("/near", "");
                ranking.allZero(); // switch off all attributes
                ranking.coeff_worddistance = RankingProfile.COEFF_MAX;
                modifier.add("/near");
            }
            if ( querystring.indexOf("/date", 0) >= 0 ) {
                querystring = querystring.replace("/date", "");
                ranking.allZero(); // switch off all attributes
                ranking.coeff_date = RankingProfile.COEFF_MAX;
                modifier.add("/date");
            }

            if ( querystring.indexOf("/location", 0) >= 0 ) {
                querystring = querystring.replace("/location", "");
                if ( constraint == null ) {
                    constraint = new Bitfield(4);
                }
                constraint.set(Tokenizer.flag_cat_haslocation, true);
                modifier.add("/location");
            }

            final int inurlp = querystring.indexOf("inurl:", 0);
            if ( inurlp >= 0 ) {
                int ftb = querystring.indexOf(' ', inurlp);
                if ( ftb == -1 ) {
                    ftb = querystring.length();
                }
                final String urlstr = querystring.substring(inurlp + 6, ftb);
                querystring = querystring.replace("inurl:" + urlstr, "");
                if ( !urlstr.isEmpty() ) {
                    urlmask = urlmask == null || urlmask.equals(".*") ? ".*" + urlstr + ".*" : urlmask; // we cannot join the conditions; if an urlmask is already given then stay with that
                }
                modifier.add("inurl:" + urlstr);
            }

            final int inlinkp = querystring.indexOf("inlink:", 0);
            if ( inlinkp >= 0 ) {
                int ftb = querystring.indexOf(' ', inlinkp);
                if ( ftb == -1 ) {
                    ftb = querystring.length();
                }
                inlink = querystring.substring(inlinkp + 7, ftb);
                querystring = querystring.replace("inlink:" + inlink, "");
                modifier.add("inlink:" + inlink);
            }

            int voc = 0;
            final Collection<Tagging.Metatag> metatags = new ArrayList<Tagging.Metatag>(1);
            while ((voc = querystring.indexOf("/vocabulary/", 0)) >= 0) {
                String vocabulary = "";
                final int ve = querystring.indexOf(' ', voc + 12);
                if (ve < 0) {
                    vocabulary = querystring.substring(voc);
                    querystring = querystring.substring(0, voc).trim();
                } else {
                    vocabulary = querystring.substring(voc, ve);
                    querystring = querystring.substring(0, voc) + querystring.substring(ve);
                }
                modifier.add(vocabulary);
                vocabulary = vocabulary.substring(12);
                final int p = vocabulary.indexOf('/');
                if (p > 0) {
                    final String k = vocabulary.substring(0, p);
                    final String v = vocabulary.substring(p + 1);
                    final Metatag mt = LibraryProvider.autotagging.metatag(k, v);
                    if (mt != null) {
                        metatags.add(mt);
                    } else {

                    }
                }
            }

            int radius = 0;
            double lon = 0.0d, lat = 0.0d, rad = 0.0d;
            if ((radius = querystring.indexOf("/radius/")) >= 0) {
                final int ve = querystring.indexOf(' ', radius + 8);
                String geo = "";
                if (ve < 0) {
                    geo = querystring.substring(radius);
                    querystring = querystring.substring(0, radius).trim();
                } else {
                    geo = querystring.substring(radius, ve);
                    querystring = querystring.substring(0, radius) + querystring.substring(ve);
                }
                geo = geo.substring(8);
                final String[] sp = geo.split("/");
                if (sp.length == 3) try {
                    lat = Double.parseDouble(sp[0]);
                    lon = Double.parseDouble(sp[1]);
                    rad = Double.parseDouble(sp[2]);
                } catch (final NumberFormatException e) {
                    lon = 0.0d; lat = 0.0d; rad = 0.0d;
                }
            }

            final int heuristicOS = querystring.indexOf("/heuristic", 0);
            if ( heuristicOS >= 0 ) {
                querystring = querystring.replace("/heuristic", "");
                modifier.add("/heuristic");
            }

            final String tldModifierPrefix = "tld:";
            final int tldp = querystring.indexOf(tldModifierPrefix, 0);
            if (tldp >= 0) {
                int ftb = querystring.indexOf(' ', tldp);
                if (ftb == -1) {
                    ftb = querystring.length();
                }
                tld = querystring.substring(tldp + tldModifierPrefix.length(), ftb);
                querystring = querystring.replace(tldModifierPrefix + tld, "");
                modifier.add(tldModifierPrefix + tld);
                while ( tld.length() > 0 && tld.charAt(0) == '.' ) {
                    tld = tld.substring(1);
                }
                if (tld.length() == 0) {
                    tld = null;
                } else {
                    try {
                        /* Convert to the same lower case ASCII Compatible Encoding that is used in normalized URLs */
                        tld = IDN.toASCII(tld, 0);
                    } catch(final IllegalArgumentException e){
                        ConcurrentLog.warn("LOCAL_SEARCH", "Failed to convert tld modifier value " + tld + "to ASCII Compatible Encoding (ACE)", e);
                    }

                    /* Domain name in an URL is case insensitive : convert now modifier to lower case for further processing over normalized URLs */
                    tld = tld.toLowerCase(Locale.ROOT);
                }
            }

            if (urlmask == null || urlmask.isEmpty()) urlmask = ".*"; //if no urlmask was given

            // read the language from the language-restrict option 'lr'
            // if no one is given, use the user agent or the system language as default
            String language = (post == null) ? null : post.get("lr");
            if (language != null && language.startsWith("lang_") ) {
                language = language.substring(5);
                if (modifier.language == null) modifier.language = language;
            }
            if (language == null || !ISO639.exists(language) ) {
                // find out language of the user by reading of the user-agent string
                String agent = header.get(HeaderFramework.ACCEPT_LANGUAGE);
                if ( agent == null ) {
                    agent = System.getProperty("user.language");
                }
                language = (agent == null) ? "en" : ISO639.userAgentLanguageDetection(agent);
                if ( language == null ) {
                    language = "en";
                }
            }

            // the query
            final QueryGoal qg = new QueryGoal(querystring.trim());
            final int maxDistance = (querystring.indexOf('"', 0) >= 0) ? qg.getIncludeHashes().size() - 1 : Integer.MAX_VALUE;

            // filter out stopwords
            final SortedSet<String> filtered = SetTools.joinConstructiveByTest(qg.getIncludeWords(), Switchboard.stopwords); //find matching stopwords
            qg.removeIncludeWords(filtered);

            // if a minus-button was hit, remove a special reference first
            if ( post != null && post.containsKey("deleteref") ) {
                try {
                    if ( !sb.verifyAuthentication(header) ) {
                        prop.authenticationRequired();
                        return prop;
                    }

                    // delete the index entry locally
                    final String delHash = post.get("deleteref", ""); // urlhash
                    if (indexSegment.termIndex() != null) indexSegment.termIndex().remove(qg.getIncludeHashes(), delHash.getBytes());
                    indexSegment.fulltext().remove(delHash.getBytes());

                    // make new news message with negative voting
                    if ( !sb.isRobinsonMode() ) {
                        final Map<String, String> map = new HashMap<String, String>();
                        map.put("urlhash", delHash);
                        map.put("vote", "negative");
                        map.put("refid", "");
                        sb.peers.newsPool.publishMyNews(
                            sb.peers.mySeed(),
                            NewsPool.CATEGORY_SURFTIPP_VOTE_ADD,
                            map);
                    }

                    // delete the search history since this still shows the entry
                    SearchEventCache.delete(delHash);
                } catch (final IOException e ) {
                    ConcurrentLog.logException(e);
                }
            }

            // if a plus-button was hit, create new voting message
            if ( post != null && post.containsKey("recommendref") ) {
                if ( !sb.verifyAuthentication(header) ) {
                    prop.authenticationRequired();
                    return prop;
                }
                final String recommendHash = post.get("recommendref", ""); // urlhash
                final URIMetadataNode urlentry = indexSegment.fulltext().getMetadata(UTF8.getBytes(recommendHash));
                if (urlentry != null) {
                    // create a news message
                    final Map<String, String> map = new HashMap<String, String>();
                    map.put("url", urlentry.url().toNormalform(true).replace(',', '|'));
                    map.put("title", urlentry.dc_title().replace(',', ' '));
                    map.put("description", urlentry.getDescription().isEmpty() ? urlentry.dc_title().replace(',', ' ') : urlentry.getDescription().get(0).replace(',', ' '));
                    map.put("author", urlentry.dc_creator());
                    map.put("tags", urlentry.dc_subject().replace(',', ' '));
                    sb.peers.newsPool.publishMyNews(
                            sb.peers.mySeed(),
                            NewsPool.CATEGORY_SURFTIPP_ADD,
                            map);
                }
            }

          // if a bookmarks-button was hit, create new bookmark entry
            if (post != null && post.containsKey("bookmarkref")) {
                if (!sb.verifyAuthentication(header) && !bookmarkRights) {
                    prop.authenticationRequired();
                    return prop;
                }
                //final String bookmarkHash = post.get("bookmarkref", ""); // urlhash
                final String urlstr = crypt.simpleDecode(post.get("bookmarkurl"));
                if (urlstr != null) {
                    final Bookmark bmk = sb.bookmarksDB.createorgetBookmark(urlstr, "admin");
                    if (bmk != null) {
                        bmk.setProperty(Bookmark.BOOKMARK_QUERY, querystring);
                        bmk.addTag("/search"); // add to bookmark folder
                        bmk.addTag("searchresult"); // add tag
                        final String urlhash = post.get("bookmarkref");
                        final URIMetadataNode urlentry = indexSegment.fulltext().getMetadata(UTF8.getBytes(urlhash));
                        if (urlentry != null && !urlentry.dc_title().isEmpty()) {
                            bmk.setProperty(Bookmark.BOOKMARK_TITLE, urlentry.dc_title());
                        }
                        sb.bookmarksDB.saveBookmark(bmk);
                    }
                }
            }

            // if a blacklist-button was hit, add host to default blacklist
            if (post != null && post.containsKey("blacklisturl")) {

                if (!sb.verifyAuthentication(header)) {
                    prop.authenticationRequired();
                    return prop;
                }

                final String blacklisturl = crypt.simpleDecode(post.get("blacklisturl", "")); // url
                try {
                    final MultiProtocolURL mpurl = new MultiProtocolURL(blacklisturl);
                    addBlacklistEntry(
                            Blacklist.defaultBlacklist(sb.listsPath),
                            mpurl.getHost() + "/.*");
                } catch (final MalformedURLException e) {
                }
            }

            // check filters
            try {
                Pattern.compile(urlmask);
            } catch (final PatternSyntaxException ex ) {
                SearchEvent.log.warn("Illegal URL mask, not a valid regex: " + urlmask);
                prop.put("urlmaskerror", 1);
                prop.putHTML("urlmaskerror_urlmask", urlmask);
                urlmask = ".*";
            }

            try {
                Pattern.compile(prefermask);
            } catch (final PatternSyntaxException ex ) {
                SearchEvent.log.warn("Illegal prefer mask, not a valid regex: " + prefermask);
                prop.put("prefermaskerror", 1);
                prop.putHTML("prefermaskerror_prefermask", prefermask);
                prefermask = "";
            }

            // do the search
            final QueryParams theQuery =
                new QueryParams(
                    qg,
                    modifier,
                    maxDistance,
                    prefermask,
                    contentdom,
                    language,
                    timezoneOffset,
                    metatags,
                    snippetFetchStrategy,
                    itemsPerPage,
                    startRecord,
                    urlmask, tld, inlink,
                    clustersearch && global ? QueryParams.Searchdom.CLUSTER : (global && indexReceiveGranted ? QueryParams.Searchdom.GLOBAL : QueryParams.Searchdom.LOCAL),
                    constraint,
                    true,
                    DigestURL.hosthashess(sb.getConfig("search.excludehosth", "")),
                    MultiProtocolURL.TLD_any_zone_filter,
                    client,
                    extendedSearchRights,
                    indexSegment,
                    ranking,
                    header.get(HeaderFramework.USER_AGENT, ""),
                    lat, lon, rad,
                    sb.getConfigSet("search.navigation"));
            theQuery.setStrictContentDom(strictContentDom);
            theQuery.setMaxSuggestions(meanMax);
            theQuery.setStandardFacetsMaxCount(sb.getConfigInt(SwitchboardConstants.SEARCH_NAVIGATION_MAXCOUNT,
                    QueryParams.FACETS_STANDARD_MAXCOUNT_DEFAULT));
            theQuery.setDateFacetMaxCount(sb.getConfigInt(SwitchboardConstants.SEARCH_NAVIGATION_DATES_MAXCOUNT,
                    QueryParams.FACETS_DATE_MAXCOUNT_DEFAULT));
            EventTracker.delete(EventTracker.EClass.SEARCH);
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(
                theQuery.id(true),
                SearchEventType.INITIALIZATION,
                "",
                0,
                0), false);

            // tell all threads to do nothing for a specific time
            sb.intermissionAllThreads(3000);

            // filter out words that appear in bluelist
            theQuery.getQueryGoal().filterOut(Switchboard.blueList);

            // log
            ConcurrentLog.info(
                "LOCAL_SEARCH",
                "INIT WORD SEARCH: "
                    + theQuery.getQueryGoal().getQueryString(false)
                    + ":"
                    + QueryParams.hashSet2hashString(theQuery.getQueryGoal().getIncludeHashes())
                    + " - "
                    + theQuery.neededResults()
                    + " links to be computed, "
                    + theQuery.itemsPerPage()
                    + " lines to be displayed");
            EventChannel.channels(EventChannel.LOCALSEARCH).addMessage(
                new RSSMessage("Local Search Request", theQuery.getQueryGoal().getQueryString(false), ""));
            final long timestamp = System.currentTimeMillis();

            // create a new search event
            final SearchEvent cachedEvent = SearchEventCache.getEvent(theQuery.id(false));
            if (cachedEvent == null) {
                theQuery.setOffset(0); // in case that this is a new search, always start without a offset
                startRecord = 0;
            }
            final SearchEvent theSearch =
                SearchEventCache.getEvent(
                    theQuery,
                    sb.peers,
                    sb.tables,
                    (sb.isRobinsonMode()) ? sb.clusterhashes : null,
                    false,
                    sb.loader,
                    (int) sb.getConfigLong(
                        SwitchboardConstants.REMOTESEARCH_MAXCOUNT_USER,
                        sb.getConfigLong(SwitchboardConstants.REMOTESEARCH_MAXCOUNT_DEFAULT, 10)),
                    sb.getConfigLong(
                        SwitchboardConstants.REMOTESEARCH_MAXTIME_USER,
                        sb.getConfigLong(SwitchboardConstants.REMOTESEARCH_MAXTIME_DEFAULT, 3000)));

            if(post.getBoolean("resortCachedResults") && cachedEvent == theSearch) {
                theSearch.resortCachedResults();
            }

            if ( startRecord == 0 && extendedSearchRights && !stealthmode ) {
                if ( modifier.sitehost != null && sb.getConfigBool(SwitchboardConstants.HEURISTIC_SITE, false) ) {
                    sb.heuristicSite(theSearch, modifier.sitehost);
                }
                if ( heuristicOS >= 0 || sb.getConfigBool(SwitchboardConstants.HEURISTIC_OPENSEARCH, false) ) {
                    FederateSearchManager.getManager().search(theSearch);
                }
            }

            // log
            ConcurrentLog.info("LOCAL_SEARCH", "EXIT WORD SEARCH: "
                + theQuery.getQueryGoal().getQueryString(false)
                + " - "
                + "local_rwi_available(" + theSearch.local_rwi_available.get() + "), "
                + "local_rwi_stored(" + theSearch.local_rwi_stored.get() + "), "
                + "remote_rwi_available(" + theSearch.remote_rwi_available.get() + "), "
                + "remote_rwi_stored(" + theSearch.remote_rwi_stored.get() + "), "
                + "remote_rwi_peerCount(" + theSearch.remote_rwi_peerCount.get() + "), "
                + "local_solr_evicted(" + theSearch.local_solr_evicted.get() + "), "
                + "local_solr_stored(" + theSearch.local_solr_stored.get() + "), "
                + "remote_solr_available(" + theSearch.remote_solr_available.get() + "), "
                + "remote_solr_stored(" + theSearch.remote_solr_stored.get() + "), "
                + "remote_solr_peerCount(" + theSearch.remote_solr_peerCount.get() + "), "
                + (System.currentTimeMillis() - timestamp)
                + " ms");

            // prepare search statistics
            theQuery.searchtime = System.currentTimeMillis() - timestamp;
            theQuery.urlretrievaltime = theSearch.getURLRetrievalTime();
            theQuery.snippetcomputationtime = theSearch.getSnippetComputationTime();
            AccessTracker.add(AccessTracker.Location.local, theQuery, theSearch.getResultCount());

            // check suggestions

            prop.put("meanCount", meanMax);
            /* Suggestions ("Did you mean") are only provided in the first html results page */
            if ( meanMax > 0 && startRecord ==0 && !json && !rss) {
                final DidYouMean didYouMean = new DidYouMean(indexSegment, querystring);
                final Iterator<StringBuilder> meanIt = didYouMean.getSuggestions(100, 5, sb.index.fulltext().collectionSize() < 2000000).iterator();
                int meanCount = 0;
                String suggestion;
                try {
                    meanCollect: while ( meanCount < meanMax && meanIt.hasNext() ) {
                        try {
                            suggestion = meanIt.next().toString();
                            prop.put("didYouMean_suggestions_" + meanCount + "_word", suggestion);
                            prop.put("didYouMean_suggestions_" + meanCount + "_url",
                                    QueryParams.navUrlWithNewQueryString(RequestHeader.FileType.HTML, 0, theQuery,
                                            suggestion, authenticatedUserName != null));
                            prop.put("didYouMean_suggestions_" + meanCount + "_sep", "|");
                            meanCount++;
                        } catch (final ConcurrentModificationException e) {
                            ConcurrentLog.logException(e);
                            break meanCollect;
                        }
                    }
                } catch (final ConcurrentModificationException e) {
                    ConcurrentLog.logException(e);
                }
                prop.put("didYouMean_suggestions_" + (meanCount - 1) + "_sep", "");
                prop.put("didYouMean", meanCount > 0 ? 1 : 0);
                prop.put("didYouMean_suggestions", meanCount);
            } else {
                prop.put("didYouMean", 0);
            }

            // find geographic info
            final SortedSet<GeoLocation> coordinates = LibraryProvider.geoLoc.find(originalquerystring, false);
            if ( coordinates == null || coordinates.isEmpty() || startRecord > 0 ) {
                prop.put("geoinfo", "0");
            } else {
                int i = 0;
                for ( final GeoLocation c : coordinates ) {
                    prop.put("geoinfo_loc_" + i + "_lon", Math.round(c.lon() * 10000.0f) / 10000.0f);
                    prop.put("geoinfo_loc_" + i + "_lat", Math.round(c.lat() * 10000.0f) / 10000.0f);
                    prop.put("geoinfo_loc_" + i + "_name", c.getName());
                    i++;
                    if ( i >= 10 ) {
                        break;
                    }
                }
                prop.put("geoinfo_loc", i);
                prop.put("geoinfo", "1");
            }

            // update the search tracker
            try {
                synchronized ( trackerHandles ) {
                    trackerHandles.add(theQuery.starttime);
                    while ( trackerHandles.size() > 600 ) {
                        if ( !trackerHandles.remove(trackerHandles.first()) ) {
                            break;
                        }
                    }
                }
                sb.localSearchTracker.put(client, trackerHandles);
                if ( sb.localSearchTracker.size() > 100 ) {
                    sb.localSearchTracker.remove(sb.localSearchTracker.keys().nextElement());
                }
                if ( MemoryControl.shortStatus() ) {
                    sb.localSearchTracker.clear();
                }
            } catch (final Exception e ) {
                ConcurrentLog.logException(e);
            }

            prop.put("num-results_offset", startRecord);
            prop.put("num-results_itemscount", Formatter.number(startRecord + theSearch.query.itemsPerPage > theSearch.getResultCount() ? startRecord + theSearch.getResultCount() % theSearch.query.itemsPerPage : startRecord + theSearch.query.itemsPerPage, true));
            prop.put("num-results_itemsPerPage", Formatter.number(itemsPerPage));
            prop.put("num-results_totalcount", Formatter.number(theSearch.getResultCount())); // also in yacyserchtrailer (hint: timing in p2p search )
            prop.put("num-results_globalresults", global && (indexReceiveGranted || clustersearch) ? "1" : "0");
            prop.put("num-results_globalresults_localIndexCount", Formatter.number(theSearch.local_rwi_stored.get() + theSearch.local_solr_stored.get(), true));
            prop.put("num-results_globalresults_remoteResourceSize", Formatter.number(theSearch.remote_rwi_stored.get() + theSearch.remote_solr_stored.get(), true));
            prop.put("num-results_globalresults_remoteIndexCount", Formatter.number(theSearch.remote_rwi_available.get() + theSearch.remote_solr_available.get(), true));
            prop.put("num-results_globalresults_remotePeerCount", Formatter.number(theSearch.remote_rwi_peerCount.get() + theSearch.remote_solr_peerCount.get(), true));

            prop.put("jsResort", jsResort);
            prop.put("num-results_jsResort", jsResort);

            /* In p2p mode only and if JavaScript resorting is not enabled, add a link allowing user to resort already drained results,
             * eventually including fetched results with higher ranks from the Solr and RWI stacks */
            prop.put("resortEnabled", !jsResort && global && !stealthmode && theSearch.resortCacheAllowed.availablePermits() > 0 ? 1 : 0);
            prop.put("resortEnabled_url",
                    QueryParams.navurlBase(RequestHeader.FileType.HTML, theQuery, null, true, authenticatedUserName != null)
                            .append("&startRecord=").append(startRecord).append("&resortCachedResults=true")
                            .toString());

            // generate the search result lines; the content will be produced by another servlet
            for ( int i = 0; i < theQuery.itemsPerPage(); i++ ) {
                prop.put("results_" + i + "_item", startRecord + i);
                prop.put("results_" + i + "_eventID", theQuery.id(false));

                prop.put("jsResort_results_" + i + "_item", startRecord + i);
                prop.put("jsResort_results_" + i + "_eventID", theQuery.id(false));
            }
            prop.put("results", theQuery.itemsPerPage());
            prop.put("jsResort_results", theQuery.itemsPerPage());
            prop.put("resultTable", (contentdom == ContentDomain.APP || contentdom == ContentDomain.VIDEO) ? 1 : (contentdom == ContentDomain.AUDIO ? 2 : 0) );
            prop.put("resultTable_embed", (contentdom == ContentDomain.AUDIO && extendedSearchRights));
            prop.put("eventID", theQuery.id(false)); // for bottomline
            prop.put("jsResort_eventID", theQuery.id(false));

            // process result of search
            if ( !filtered.isEmpty() ) {
                prop.put("excluded", "1");
                prop.putHTML("excluded_stopwords", filtered.toString());
            } else {
                prop.put("excluded", "0");
            }

            if (prop.isEmpty() || querystring.length() == 0) {
                if ( querystring.length() == 0 ) { // querystring is trimmed originalquerystring
                    prop.put("num-results", "2"); // no results - at least 2 chars
                } else {
                    prop.put("num-results", "1"); // no results
                }
            } else {
                prop.put("num-results", "3");
            }

            prop.put("depth", "0");
            prop.put("localQuery", theSearch.query.isLocal() ? "1" : "0");
            prop.put("jsResort_localQuery", theSearch.query.isLocal() ? "1" : "0");

            final boolean showLogin = sb.getConfigBool(SwitchboardConstants.SEARCH_PUBLIC_TOP_NAV_BAR_LOGIN,
                    SwitchboardConstants.SEARCH_PUBLIC_TOP_NAV_BAR_LOGIN_DEFAULT);
            if(showLogin) {
                if(authenticatedUserName != null) {
                    /* Show the name of the authenticated user */
                    prop.put("showLogin", 1);
                    prop.put("showLogin_userName", authenticatedUserName);
                } else {
                    /* Show a login link */
                    prop.put("showLogin", 2);
                    prop.put("showLogin_loginURL",
                            QueryParams.navurlBase(RequestHeader.FileType.HTML, theQuery, null, true, true).toString());
                }
            } else {
                prop.put("showLogin", 0);
            }

        }

        prop.put("focus", focus ? 1 : 0); // focus search field
        prop.put("searchagain", global ? "1" : "0");
        final String former = originalquerystring.replaceAll(Segment.catchallString, "*"); // hide catchallString in output
        prop.putHTML("former", former);
        try {
            prop.put("formerEncoded", URLEncoder.encode(former, StandardCharsets.UTF_8.name()));
        } catch (final UnsupportedEncodingException e) {
            ConcurrentLog.warn("LOCAL_SEARCH", "Unsupported UTF-8 encoding!");
            prop.put("formerEncoded", former);
        }
        prop.put("count", itemsPerPage);
        prop.put("offset", startRecord);
        prop.put("resource", global ? "global" : "local");
        prop.putHTML("prefermaskfilter", prefermask);
        prop.put("indexof", (indexof) ? "on" : "off");
        prop.put("constraint", (constraint == null) ? "" : constraint.exportB64());
        prop.put("search.verify", snippetFetchStrategy == null ? sb.getConfig("search.verify", "iffresh") : snippetFetchStrategy.toName());
        prop.put("search.navigation", (post == null) ? sb.getConfig("search.navigation", "all") : post.get("nav", "all"));
        prop.putHTML("contentdom", (post == null ? "text" : post.get("contentdom", "text")));
        prop.putHTML("strictContentDom", String.valueOf(strictContentDom));

        // for RSS: don't HTML encode some elements
        prop.putXML("rss_query", originalquerystring);
        prop.putXML("rss_queryenc", originalquerystring.replace(' ', '+'));

        sb.localSearchLastAccess = System.currentTimeMillis();

        // return rewrite properties
        return prop;
    }
}
