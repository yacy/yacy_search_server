// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 28.08.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import net.yacy.cora.date.AbstractFormatter;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.data.UserDB;
import net.yacy.document.DateDetection;
import net.yacy.document.LibraryProvider;
import net.yacy.http.servlets.TemplateMissingParameterException;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.search.EventTracker;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Segment;
import net.yacy.search.navigator.Navigator;
import net.yacy.search.navigator.NavigatorSortDirection;
import net.yacy.search.navigator.NavigatorSortType;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.query.SearchEventType;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;


public class yacysearchtrailer {

    private static final int TOPWORDS_MAXCOUNT = 16;
    private static final int TOPWORDS_MINSIZE = 8;
    private static final int TOPWORDS_MAXSIZE = 22;

    @SuppressWarnings({ })
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        if (post == null) {
            throw new TemplateMissingParameterException("The eventID parameter is required");
        }

        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;
        final String eventID = post.get("eventID", "");

        final boolean adminAuthenticated = sb.verifyAuthentication(header);

        final UserDB.Entry user = sb.userDB != null ? sb.userDB.getUser(header) : null;
        final boolean authenticated = adminAuthenticated || user != null;

        if (post.containsKey("auth") && !authenticated) {
            /*
             * Authenticated search is explicitely requested here
             * but no authentication is provided : ask now for authentication.
             * Wihout this, after timeout of HTTP Digest authentication nonce, browsers no more send authentication information
             * and as this page is not private, protected features would simply be hidden without asking browser again for authentication.
             * (see mantis 766 : http://mantis.tokeek.de/view.php?id=766) *
             */
            prop.authenticationRequired();
            return prop;
        }

        // find search event
        final SearchEvent theSearch = SearchEventCache.getEvent(eventID);
        if (theSearch == null) {
            // the event does not exist, show empty page
            return prop;
        }
        final RequestHeader.FileType fileType = header.fileType();

        final boolean clustersearch = sb.isRobinsonMode() && sb.getConfig(SwitchboardConstants.CLUSTER_MODE, "").equals(SwitchboardConstants.CLUSTER_MODE_PUBLIC_CLUSTER);
        final boolean indexReceiveGranted = sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW_SEARCH, true) || clustersearch;
        final boolean p2pmode = sb.peers != null && sb.peers.sizeConnected() > 0 && indexReceiveGranted;
        final boolean global = post == null || (!post.get("resource-switch", post.get("resource", "global")).equals("local") && p2pmode);
        final boolean stealthmode = p2pmode && !global;

        // compose search navigation
        final ContentDomain contentdom = theSearch.getQuery().contentdom;
        prop.put("searchdomswitches",
            sb.getConfigBool("search.text", true)
                || sb.getConfigBool("search.audio", true)
                || sb.getConfigBool("search.video", true)
                || sb.getConfigBool("search.image", true)
                || sb.getConfigBool("search.app", true) ? 1 : 0);

        String originalquerystring = post.get("query", post.get("search", "")).trim();
        originalquerystring = originalquerystring.replace('<', ' ').replace('>', ' '); // light xss protection
        final String former = originalquerystring.replaceAll(Segment.catchallString, "*");
        final CacheStrategy snippetFetchStrategy = CacheStrategy.parse(post.get("verify", sb.getConfig("search.verify", "")));
        final String snippetFetchStrategyName = snippetFetchStrategy == null
                ? sb.getConfig("search.verify", CacheStrategy.IFFRESH.toName())
                : snippetFetchStrategy.toName();
        final int startRecord = post.getInt("startRecord", post.getInt("offset", post.getInt("start", 0)));
        /* Maximum number of suggestions to display in the first results page */
        final int meanMax = post.getInt("meanCount", 0);

        prop.put("resource-switches", adminAuthenticated && (stealthmode || global));
        prop.put("resource-switches_global", adminAuthenticated && global);
        appendSearchFormValues("resource-switches_", post, prop, global, theSearch, former, snippetFetchStrategyName, startRecord, meanMax);

        /* The search event has been found : we can render ranking switches */
        prop.put("ranking-switches", true);
        appendSearchFormValues("ranking-switches_", post, prop, global, theSearch, former, snippetFetchStrategyName, startRecord, meanMax);

        /* Add information about the current navigators generation (number of updates since their initialization) */
        prop.put("ranking-switches_nav-generation", theSearch.getNavGeneration());

        prop.put("ranking-switches_contextRanking", !former.contains(" /date"));
        prop.put("ranking-switches_contextRanking_formerWithoutDate", former.replace(" /date", ""));
        prop.put("ranking-switches_dateRanking", former.contains(" /date"));
        prop.put("ranking-switches_dateRanking_former", former);

        appendSearchFormValues("searchdomswitches_", post, prop, global, theSearch, former, snippetFetchStrategyName, startRecord, meanMax);

        prop.put("searchdomswitches_searchtext", sb.getConfigBool("search.text", true) ? 1 : 0);
        prop.put("searchdomswitches_searchaudio", sb.getConfigBool("search.audio", true) ? 1 : 0);
        prop.put("searchdomswitches_searchvideo", sb.getConfigBool("search.video", true) ? 1 : 0);
        prop.put("searchdomswitches_searchimage", sb.getConfigBool("search.image", true) ? 1 : 0);
        prop.put("searchdomswitches_searchapp", sb.getConfigBool("search.app", true) ? 1 : 0);
        prop.put("searchdomswitches_searchtext_check", (contentdom == ContentDomain.TEXT || contentdom == ContentDomain.ALL) ? "1" : "0");
        prop.put("searchdomswitches_searchaudio_check", (contentdom == ContentDomain.AUDIO) ? "1" : "0");
        prop.put("searchdomswitches_searchvideo_check", (contentdom == ContentDomain.VIDEO) ? "1" : "0");
        prop.put("searchdomswitches_searchimage_check", (contentdom == ContentDomain.IMAGE) ? "1" : "0");
        prop.put("searchdomswitches_searchapp_check", (contentdom == ContentDomain.APP) ? "1" : "0");

        appendSearchFormValues("searchdomswitches_strictContentDomSwitch_", post, prop, global, theSearch, former, snippetFetchStrategyName, startRecord, meanMax);
        prop.put("searchdomswitches_strictContentDomSwitch", (contentdom != ContentDomain.TEXT && contentdom != ContentDomain.ALL) ? 1 : 0);
        prop.put("searchdomswitches_strictContentDomSwitch_strictContentDom", theSearch.getQuery().isStrictContentDom() ? 1 : 0);

        String name;
        int count;
        Iterator<String> navigatorIterator;

        // topics navigator
        final ScoreMap<String> topicNavigator = theSearch.getTopicNavigator(TOPWORDS_MAXCOUNT);
        if (topicNavigator == null || topicNavigator.isEmpty()) {
            prop.put("nav-topics", "0");
        } else {
            prop.put("nav-topics", "1");
            navigatorIterator = topicNavigator.keys(false);
            int i = 0;
            // first sort the list to a form where the greatest element is in the middle
            final LinkedList<Map.Entry<String, Integer>> cloud = new LinkedList<Map.Entry<String, Integer>>();
            int mincount = Integer.MAX_VALUE; int maxcount = 0;
            while (i < TOPWORDS_MAXCOUNT && navigatorIterator.hasNext()) {
                name = navigatorIterator.next();
                count = topicNavigator.get(name);
                if (count == 0) break;
                if (name == null) continue;
                final int normcount = (count + TOPWORDS_MAXCOUNT - i) / 2;
                if (normcount > maxcount) maxcount = normcount;
                if (normcount < mincount) mincount = normcount;
                final Map.Entry<String, Integer> entry = new AbstractMap.SimpleEntry<String, Integer>(name, normcount);
                if (cloud.size() % 2 == 0) cloud.addFirst(entry); else cloud.addLast(entry); // alternating add entry to first or last position.
                i++;
            }
            i= 0;
            for (final Map.Entry<String, Integer> entry: cloud) {
                name = entry.getKey();
                count = entry.getValue();
                prop.put(fileType, "nav-topics_element_" + i + "_modifier", name);
                prop.put(fileType, "nav-topics_element_" + i + "_name", name);
                prop.putUrlEncoded(fileType, "nav-topics_element_" + i + "_url", QueryParams
                        .navurl(fileType, 0, theSearch.query, name, false, authenticated).toString());
                prop.put("nav-topics_element_" + i + "_count", count);
                int fontsize = TOPWORDS_MINSIZE + (TOPWORDS_MAXSIZE - TOPWORDS_MINSIZE) * (count - mincount) / (1 + maxcount - mincount);
                fontsize = Math.max(TOPWORDS_MINSIZE, fontsize - (name.length() - 5));
                prop.put("nav-topics_element_" + i + "_size", fontsize); // font size in pixel
                prop.put("nav-topics_element_" + i + "_nl", 1);
                i++;
            }
            prop.put("nav-topics_element", i);
            prop.put("nav-topics_count", i);
            i--;
            prop.put("nav-topics_element_" + i + "_nl", 0);
        }

        // protocol navigators
        if (theSearch.protocolNavigator == null || theSearch.protocolNavigator.isEmpty()) {
            prop.put("nav-protocols", 0);
        } else {
            prop.put("nav-protocols", 1);
            //int httpCount = theSearch.protocolNavigator.delete("http");
            //int httpsCount = theSearch.protocolNavigator.delete("https");
            //theSearch.protocolNavigator.inc("http(s)", httpCount + httpsCount);
            navigatorIterator = theSearch.protocolNavigator.keys(false);
            int i = 0, pos = 0, neg = 0;
            String nav, rawNav;
            final String oldQuery = theSearch.query.getQueryGoal().query_original; // prepare hack to make radio-button like navigation
            final String oldProtocolModifier = theSearch.query.modifier.protocol;
            if (oldProtocolModifier != null && oldProtocolModifier.length() > 0) {theSearch.query.modifier.remove("/" + oldProtocolModifier); theSearch.query.modifier.remove(oldProtocolModifier);}
            theSearch.query.modifier.protocol = "";
            theSearch.query.getQueryGoal().query_original = oldQuery.replaceAll(" /https", "").replaceAll(" /http", "").replaceAll(" /ftp", "").replaceAll(" /smb", "").replaceAll(" /file", "");
            while (i < theSearch.getQuery().getStandardFacetsMaxCount() && navigatorIterator.hasNext()) {
                name = navigatorIterator.next().trim();
                count = theSearch.protocolNavigator.get(name);
                if (count == 0) break;
                nav = "%2F" + name;
                /* Avoid double percent encoding in QueryParams.navurl */
                rawNav = "/" + name;
                final String url;
                if (oldProtocolModifier == null || !oldProtocolModifier.equals(name)) {
                    pos++;
                    prop.put("nav-protocols_element_" + i + "_on", 0);
                    prop.put("nav-protocols_element_" + i + "_onclick", 0);
                    prop.put(fileType, "nav-protocols_element_" + i + "_modifier", nav);
                    url = QueryParams.navurl(fileType, 0, theSearch.query, rawNav, false, authenticated).toString();
                } else {
                    neg++;
                    prop.put("nav-protocols_element_" + i + "_on", 1);
                    prop.put("nav-protocols_element_" + i + "_onclick", 1);
                    prop.put(fileType, "nav-protocols_element_" + i + "_modifier", "-" + nav);
                    url = QueryParams
                            .navUrlWithSingleModifierRemoved(fileType, 0, theSearch.query, rawNav, authenticated)
                            .toString();
                }
                prop.put(fileType, "nav-protocols_element_" + i + "_name", name);
                prop.put("nav-protocols_element_" + i + "_onclick_url", url);
                prop.putUrlEncoded(fileType, "nav-protocols_element_" + i + "_url", url);
                prop.put("nav-protocols_element_" + i + "_count", count);
                prop.put("nav-protocols_element_" + i + "_nl", 1);
                i++;
            }
            if (i == 1) prop.put("nav-protocols_element_0_onclick", 0); // allow to unselect, if only one button
            theSearch.query.modifier.protocol = oldProtocolModifier;
            if (oldProtocolModifier != null && oldProtocolModifier.length() > 0) theSearch.query.modifier.add(oldProtocolModifier.startsWith("/") ? oldProtocolModifier : "/" + oldProtocolModifier);
            theSearch.query.getQueryGoal().query_original = oldQuery;
            prop.put("nav-protocols_element", i);
            prop.put("nav-protocols_count", i);
            i--;
            prop.put("nav-protocols_element_" + i + "_nl", 0);
            if (pos == 1 && neg == 0) prop.put("nav-protocols", 0); // this navigation is not useful
        }

        // date navigators
        if (theSearch.dateNavigator == null || theSearch.dateNavigator.isEmpty()) {
            prop.put("nav-dates", 0);
        } else {
            prop.put("nav-dates", 1);
            navigatorIterator = theSearch.dateNavigator.keysByNaturalOrder(true); // this iterator is different as it iterates by the key order (which is a date order)
            int i = 0, pos = 0, neg = 0;
            long dx = -1;
            Date fromconstraint = theSearch.getQuery().modifier.from == null ? null : DateDetection.parseLine(theSearch.getQuery().modifier.from, theSearch.getQuery().timezoneOffset);
            if (fromconstraint == null) fromconstraint = new Date(System.currentTimeMillis() - AbstractFormatter.normalyearMillis);
            Date toconstraint = theSearch.getQuery().modifier.to == null ? null : DateDetection.parseLine(theSearch.getQuery().modifier.to, theSearch.getQuery().timezoneOffset);
            if (toconstraint == null) toconstraint = new Date(System.currentTimeMillis() + AbstractFormatter.normalyearMillis);
            while (i < theSearch.getQuery().getDateFacetMaxCount() && navigatorIterator.hasNext()) {
                name = navigatorIterator.next().trim();
                if (name.length() < 10) continue;
                count = theSearch.dateNavigator.get(name);
                if(count == 0) {
                    continue;
                }
                final String shortname = name.substring(0, 10);
                final long d = Instant.parse(name).toEpochMilli();
                final Date dd = new Date(d);
                if (fromconstraint != null && dd.before(fromconstraint)) continue;
                if (toconstraint != null && dd.after(toconstraint)) break;
                if (dx > 0) {
                    while (d - dx > AbstractFormatter.dayMillis && i < theSearch.getQuery().getDateFacetMaxCount()) {
                        dx += AbstractFormatter.dayMillis;
                        final String sn = new Date(dx).toInstant().toString().substring(0, 10);
                        prop.put("nav-dates_element_" + i + "_on", 0);
                        prop.put(fileType, "nav-dates_element_" + i + "_name", sn);
                        prop.put("nav-dates_element_" + i + "_count", 0);
                        prop.put("nav-dates_element_" + i + "_nl", 1);
                        i++;
                    }
                }
                dx = d;
                if (theSearch.query.modifier.on == null || !theSearch.query.modifier.on.contains(shortname) ) {
                    pos++;
                    prop.put("nav-dates_element_" + i + "_on", 1);
                } else {
                    neg++;
                    prop.put("nav-dates_element_" + i + "_on", 0);
                }
                prop.put(fileType, "nav-dates_element_" + i + "_name", shortname);
                prop.put("nav-dates_element_" + i + "_count", count);
                prop.put("nav-dates_element_" + i + "_nl", 1);
                i++;
            }
            prop.put("nav-dates_element", i);
            prop.put("nav-dates_count", i);
            i--;
            prop.put("nav-dates_element_" + i + "_nl", 0);
            if (pos == 1 && neg == 0) prop.put("nav-dates", 0); // this navigation is not useful
        }

        // vocabulary navigators
        final Map<String, ScoreMap<String>> vocabularyNavigators = theSearch.vocabularyNavigator;
        if (vocabularyNavigators != null && !vocabularyNavigators.isEmpty()) {
            int navvoccount = 0;
            vocnav: for (final Map.Entry<String, ScoreMap<String>> ve: vocabularyNavigators.entrySet()) {
                final String navname = ve.getKey();
                if (ve.getValue() == null || ve.getValue().isEmpty()) {
                    continue vocnav;
                }
                prop.put(fileType, "nav-vocabulary_" + navvoccount + "_navname", navname);
                navigatorIterator = ve.getValue().keys(false);
                int i = 0;
                String nav, rawNav;
                while (i < 20 && navigatorIterator.hasNext()) {
                    name = navigatorIterator.next();
                    count = ve.getValue().get(name);
                    if (count == 0) break;
                    nav = "%2Fvocabulary%2F" + navname + "%2F" + MultiProtocolURL.escape(Tagging.encodePrintname(name)).toString();
                    /* Avoid double percent encoding in QueryParams.navurl */
                    rawNav = "/vocabulary/" + navname + "/" + MultiProtocolURL.escape(Tagging.encodePrintname(name)).toString();
                    final String navUrl;
                    if (!theSearch.query.modifier.toString().contains("/vocabulary/" + navname + "/" + name.replace(' ', '_'))) {
                        prop.put("nav-vocabulary_" + navvoccount + "_element_" + i + "_on", 1);
                        prop.put(fileType, "nav-vocabulary_" + navvoccount + "_element_" + i + "_modifier", nav);
                        navUrl = QueryParams.navurl(fileType, 0, theSearch.query, rawNav, false, authenticated).toString();
                    } else {
                        prop.put("nav-vocabulary_" + navvoccount + "_element_" + i + "_on", 0);
                        prop.put(fileType, "nav-vocabulary_" + navvoccount + "_element_" + i + "_modifier", "-" + nav);
                        navUrl = QueryParams.navUrlWithSingleModifierRemoved(fileType, 0, theSearch.query, rawNav, authenticated);
                    }
                    prop.put(fileType, "nav-vocabulary_" + navvoccount + "_element_" + i + "_name", name);
                    prop.putUrlEncoded(fileType, "nav-vocabulary_" + navvoccount + "_element_" + i + "_url", navUrl);
                    prop.put(fileType, "nav-vocabulary_" + navvoccount + "_element_" + i + "_id", "vocabulary_" + navname + "_" + i);
                    prop.put("nav-vocabulary_" + navvoccount + "_element_" + i + "_count", count);
                    prop.put("nav-vocabulary_" + navvoccount + "_element_" + i + "_nl", 1);
                    i++;
                }
                prop.put("nav-vocabulary_" + navvoccount + "_element", i);
                prop.put("nav-vocabulary_" + navvoccount + "_count", i);
                i--;
                prop.put("nav-vocabulary_" + navvoccount + "_element_" + i + "_nl", 0);
                navvoccount++;
            }
            prop.put("nav-vocabulary", navvoccount);
        } else {
            prop.put("nav-vocabulary", 0);
        }

        // navigator plugins
        int ni = 0;
        for (final String naviname : theSearch.navigatorPlugins.keySet()) {

            final Navigator navi = theSearch.navigatorPlugins.get(naviname);
            if (navi.isEmpty()) {
                continue;
            }

            prop.put("navs_" + ni + "_displayname", navi.getDisplayName());
            prop.put("navs_" + ni + "_name", naviname);
            prop.put("navs_" + ni + "_count", navi.size());

            final int navSort;
            if(navi.getSort() == null) {
                navSort = 0;
            } else {
                if(navi.getSort().getSortType() == NavigatorSortType.COUNT) {
                    if(navi.getSort().getSortDir() == NavigatorSortDirection.DESC) {
                        navSort = 0;
                    } else {
                        navSort = 1;
                    }
                } else {
                    if(navi.getSort().getSortDir() == NavigatorSortDirection.DESC) {
                        navSort = 2;
                    } else {
                        navSort = 3;
                    }
                }
            }
            prop.put("navs_" + ni + "_navSort", navSort);

               navigatorIterator = navi.navigatorKeys();
            int i = 0, pos = 0, neg = 0;
            String nav, rawNav;
            while (i < theSearch.getQuery().getStandardFacetsMaxCount() && navigatorIterator.hasNext()) {
                name = navigatorIterator.next();
                count = navi.get(name);
                if (count == 0) {
                    /* This entry has a zero count, but the next may be positive */
                    continue;
                }

                rawNav = navi.getQueryModifier(name);
                try {
                    nav = URLEncoder.encode(rawNav, StandardCharsets.UTF_8.name());
                } catch (final UnsupportedEncodingException ex) {
                    nav = "";
                }
                final boolean isactive = navi.modifieractive(theSearch.query.modifier, name);
                final String navUrl;
                if (!isactive) {
                    pos++;
                    prop.put("navs_" + ni + "_element_" + i + "_on", 1);
                    prop.put(fileType, "navs_" + ni + "_element_" + i + "_modifier", nav);
                    navUrl = QueryParams.navurl(fileType, 0, theSearch.query, rawNav, false, authenticated).toString();
                } else {
                    neg++;
                    prop.put("navs_" + ni + "_element_" + i + "_on", 0);
                    prop.put(fileType, "navs_" + ni + "_element_" + i + "_modifier", "-" + nav);
                    navUrl = QueryParams.navUrlWithSingleModifierRemoved(fileType, 0, theSearch.query, rawNav,
                            authenticated);
                }
                prop.put(fileType, "navs_" + ni + "_element_" + i + "_name", navi.getElementDisplayName(name));
                prop.putUrlEncoded(fileType, "navs_" + ni + "_element_" + i + "_url", navUrl);
                prop.put(fileType, "navs_" + ni + "_element_" + i + "_id", naviname + "_" + i);
                prop.put("navs_" + ni + "_element_" + i + "_count", count);
                prop.put("navs_" + ni + "_element_" + i + "_nl", 1);
                i++;
            }
            if(i == 0) {
                /* The navigator has only entries with value==0 : this is equivalent to empty navigator */
                continue;
            }
            prop.put("navs_" + ni + "_element", i);
            prop.put("navs_" + ni + "_count", i);
            i--;
            prop.put("navs_" + ni + "_element_" + i + "_nl", 0);
            if (pos == 1 && neg == 0) {
                prop.put("navs_" + ni, 0); // this navigation is not useful
            }

            ni++;
        }
        prop.put("navs", ni); // navigatior plugins - eof

        // about box
        final String aboutBody = env.getConfig("about.body", "");
        final String aboutHeadline = env.getConfig("about.headline", "");
        if ((aboutBody.isEmpty() && aboutHeadline.isEmpty()) || theSearch.getResultCount() == 0) {
            prop.put("nav-about", 0);
        } else {
            prop.put("nav-about", 1);
            prop.put("nav-about_headline", aboutHeadline);
            prop.put("nav-about_body", aboutBody);
        }

        // category: location search
        // show only if active and there is a location database present or if there had been any search results (with lat/lon)
        if (theSearch.locationNavigator == null
                || ((LibraryProvider.geoLoc.isEmpty() || theSearch.getResultCount() == 0) && theSearch.locationNavigator.isEmpty())) {
            prop.put("cat-location", 0);
        } else {
            prop.put("cat-location", 1);
            final String query = theSearch.query.getQueryGoal().getQueryString(false);
            prop.put(fileType, "cat-location_query", query);
            final String queryenc = theSearch.query.getQueryGoal().getQueryString(true).replace(' ', '+');
            prop.putUrlEncoded(fileType, "cat-location_queryenc", queryenc);
        }
        prop.put("num-results_totalcount", theSearch.getResultCount());
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(theSearch.query.id(true), SearchEventType.FINALIZATION, "bottomline", 0, 0), false);
        return prop;
    }

    /**
     * Append search input fields values to the prop object. All parameters are required and must not be null.
     * @param templatePrefix the template prefix to append before each template key
     * @param post request parameters
     * @param prop the servlet answer object to be filled
     * @param global when true the search scope is blobal (not limited to this peer local index)
     * @param theSearch the search object
     * @param former the previous search terms
     * @param snippetFetchStrategyName the snippet fetching strategy name to apply
     * @param startRecord the zero based index of the first record to return
     * @param meanMax the maximum number of suggestions ("Did you mean") to propose
     */
    private static void appendSearchFormValues(final String templatePrefix, final serverObjects post, final serverObjects prop,
            final boolean global, final SearchEvent theSearch, final String former, final String snippetFetchStrategyName,
            final int startRecord, final int meanMax) {
        prop.putHTML(templatePrefix + "former", former);
        prop.put(templatePrefix + "authSearch", post.containsKey("auth"));
        prop.put(templatePrefix + "contentdom", post.get("contentdom", "text"));
        prop.put(templatePrefix + "strictContentDom", String.valueOf(theSearch.getQuery().isStrictContentDom()));
        prop.put(templatePrefix + "maximumRecords", theSearch.getQuery().itemsPerPage);
        prop.put(templatePrefix + "startRecord", startRecord);
        prop.put(templatePrefix + "search.verify", snippetFetchStrategyName);
        prop.put(templatePrefix + "resource", global ? "global" : "local");
        prop.put(templatePrefix + "search.navigation", post.get("nav", "all"));
        prop.putHTML(templatePrefix + "prefermaskfilter", theSearch.getQuery().prefer.pattern());
        prop.put(templatePrefix + "depth", "0");
        prop.put(templatePrefix + "constraint", (theSearch.getQuery().constraint == null) ? "" : theSearch.getQuery().constraint.exportB64());
        prop.put(templatePrefix + "meanCount", meanMax);
    }

}
//http://localhost:8090/yacysearch.html?query=java+&amp;maximumRecords=10&amp;resource=local&amp;verify=cacheonly&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=ftp://.*&amp;prefermaskfilter=&amp;constraint=&amp;contentdom=text&amp;former=java+%2Fftp&amp;startRecord=0
//http://localhost:8090/yacysearch.html?query=java+&amp;maximumRecords=10&amp;resource=local&amp;verify=cacheonly&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;constraint=&amp;contentdom=text&amp;former=java+%2Fvocabulary%2FGewerke%2FTore&amp;startRecord=0
