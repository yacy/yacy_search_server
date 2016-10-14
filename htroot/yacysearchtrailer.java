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

import java.text.ParseException;
import java.util.AbstractMap;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.apache.solr.util.DateFormatUtil;

import net.yacy.cora.date.AbstractFormatter;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.document.DateDetection;
import net.yacy.document.LibraryProvider;
import net.yacy.kelondro.util.ISO639;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.search.EventTracker;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
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
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;
        final String eventID = post.get("eventID", "");

        final boolean authorized = sb.verifyAuthentication(header);
        final boolean clustersearch = sb.isRobinsonMode() && sb.getConfig(SwitchboardConstants.CLUSTER_MODE, "").equals(SwitchboardConstants.CLUSTER_MODE_PUBLIC_CLUSTER);
        final boolean indexReceiveGranted = sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW_SEARCH, true) || clustersearch;
        boolean p2pmode = sb.peers != null && sb.peers.sizeConnected() > 0 && indexReceiveGranted;
        boolean global = post == null || (!post.get("resource-switch", post.get("resource", "global")).equals("local") && p2pmode);
        boolean stealthmode = p2pmode && !global;
        prop.put("resource-select", !authorized ? 0 : stealthmode ? 2 : global ? 1 : 0);
        // find search event
        final SearchEvent theSearch = SearchEventCache.getEvent(eventID);
        if (theSearch == null) {
            // the event does not exist, show empty page
            return prop;
        }
        final RequestHeader.FileType fileType = header.fileType();

        // compose search navigation
        ContentDomain contentdom = theSearch.getQuery().contentdom;
        prop.put("searchdomswitches",
            sb.getConfigBool("search.text", true)
                || sb.getConfigBool("search.audio", true)
                || sb.getConfigBool("search.video", true)
                || sb.getConfigBool("search.image", true)
                || sb.getConfigBool("search.app", true) ? 1 : 0);
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

        // namespace navigators
        String name;
        int count;
        Iterator<String> navigatorIterator;
        if (theSearch.namespaceNavigator == null || theSearch.namespaceNavigator.isEmpty()) {
            prop.put("nav-namespace", 0);
        } else {
            prop.put("nav-namespace", 1);
            navigatorIterator = theSearch.namespaceNavigator.keys(false);
            int i = 0, pos = 0, neg = 0;
            String nav, rawNav;
            while (i < QueryParams.FACETS_STANDARD_MAXCOUNT && navigatorIterator.hasNext()) {
                name = navigatorIterator.next();
                count = theSearch.namespaceNavigator.get(name);
                if (count == 0) break;
                nav = "inurl%3A" + name;
                /* Avoid double percent encoding in QueryParams.navurl */
                rawNav = "inurl:" + name;
                if (!theSearch.query.modifier.toString().contains("inurl:"+name)) {
                    pos++;
                    prop.put("nav-namespace_element_" + i + "_on", 1);
                    prop.put(fileType, "nav-namespace_element_" + i + "_modifier", nav);
                } else {
                    neg++;                    
                    prop.put("nav-namespace_element_" + i + "_on", 0);
                    prop.put(fileType, "nav-namespace_element_" + i + "_modifier", "-" + nav);
                    nav="";
                    rawNav = "";
                }
                prop.put(fileType, "nav-namespace_element_" + i + "_name", name);
                /* URL is already percent encoded : no need to re-encode specifically for the file type */
                prop.put("nav-namespace_element_" + i + "_url", QueryParams.navurl(fileType, 0, theSearch.query, rawNav, false).toString());
                prop.put(fileType, "nav-namespace_element_" + i + "_id", "namespace_" + i);
                prop.put("nav-namespace_element_" + i + "_count", count);
                prop.put("nav-namespace_element_" + i + "_nl", 1);
                i++;
            }
            prop.put("nav-namespace_element", i);
            i--;
            prop.put("nav-namespace_element_" + i + "_nl", 0);
            if (pos == 1 && neg == 0) prop.put("nav-namespace", 0); // this navigation is not useful
        }

        // domain navigators
        final ScoreMap<String> hostNavigator = theSearch.hostNavigator;
        if (hostNavigator == null || hostNavigator.isEmpty()) {
            prop.put("nav-domains", 0);
        } else {
            prop.put("nav-domains", 1);
            navigatorIterator = hostNavigator.keys(false);
            int i = 0, pos = 0, neg = 0;
            String nav, rawNav;
            while (i < QueryParams.FACETS_STANDARD_MAXCOUNT && navigatorIterator.hasNext()) {
                name = navigatorIterator.next();
                count = hostNavigator.get(name);
                if (count == 0) break;
                nav = "site%3A" + name;
                /* Avoid double percent encoding in QueryParams.navurl */
                rawNav = "site:" + name;
                if (theSearch.query.modifier.sitehost == null || !theSearch.query.modifier.sitehost.contains(name)) {
                    pos++;                    
                    prop.put("nav-domains_element_" + i + "_on", 1);
                    prop.put(fileType, "nav-domains_element_" + i + "_modifier", nav);
                } else {
                    neg++;                    
                    prop.put("nav-domains_element_" + i + "_on", 0);
                    prop.put(fileType, "nav-domains_element_" + i + "_modifier", "-" + nav);
                    nav="";
                    rawNav = "";
                }
                prop.put(fileType, "nav-domains_element_" + i + "_name", name);
                /* URL is already percent encoded : no need to re-encode specifically for the file type */
                prop.put("nav-domains_element_" + i + "_url", QueryParams.navurl(fileType, 0, theSearch.query, rawNav, false).toString());
                prop.put(fileType, "nav-domains_element_" + i + "_id", "domains_" + i);
                prop.put("nav-domains_element_" + i + "_count", count);
                prop.put("nav-domains_element_" + i + "_nl", 1);
                i++;
            }
            prop.put("nav-domains_element", i);
            prop.put("nav-domains_count", i);
            i--;
            prop.put("nav-domains_element_" + i + "_nl", 0);
            if (pos == 1 && neg == 0) prop.put("nav-domains", 0); // this navigation is not useful
        }

        // language navigators
        final ScoreMap<String> languageNavigator = theSearch.languageNavigator;
        if (languageNavigator == null || languageNavigator.isEmpty()) {
            prop.put("nav-languages", 0);
        } else {
            prop.put("nav-languages", 1);
            navigatorIterator = languageNavigator.keys(false);
            int i = 0, pos = 0, neg = 0;
            String nav, rawNav;
            while (i < QueryParams.FACETS_STANDARD_MAXCOUNT && navigatorIterator.hasNext()) {
                name = navigatorIterator.next();
                count = languageNavigator.get(name);
                if (count == 0) break;
                nav = "%2Flanguage%2F" + name;
                /* Avoid double percent encoding in QueryParams.navurl */
                rawNav = "/language/" + name;
                if (theSearch.query.modifier.language == null || !theSearch.query.modifier.language.contains(name)) {
                    pos++;
                    prop.put("nav-languages_element_" + i + "_on", 1);
                    prop.put(fileType, "nav-languages_element_" + i + "_modifier", nav);
                } else {
                    neg++;                    
                    prop.put("nav-languages_element_" + i + "_on", 0);
                    prop.put(fileType, "nav-languages_element_" + i + "_modifier", "-" + nav);
                    nav="";
                    rawNav = "";
                }
                String longname = ISO639.country(name);
                prop.put(fileType, "nav-languages_element_" + i + "_name", longname == null ? name : longname);
                /* URL is already percent encoded : no need to re-encode specifically for the file type */
                prop.put("nav-languages_element_" + i + "_url", QueryParams.navurl(fileType, 0, theSearch.query, rawNav, false).toString());
                prop.put(fileType, "nav-languages_element_" + i + "_id", "languages_" + i);
                prop.put("nav-languages_element_" + i + "_count", count);
                prop.put("nav-languages_element_" + i + "_nl", 1);
                i++;
            }
            prop.put("nav-languages_element", i);
            prop.put("nav-languages_count", i);
            i--;
            prop.put("nav-languages_element_" + i + "_nl", 0);
            if (pos == 1 && neg == 0) prop.put("nav-languages", 0); // this navigation is not useful
        }

        // author navigators
        if (theSearch.authorNavigator == null || theSearch.authorNavigator.isEmpty()) {
            prop.put("nav-authors", 0);
        } else {
            prop.put("nav-authors", 1);
            navigatorIterator = theSearch.authorNavigator.keys(false);
            int i = 0, pos = 0, neg = 0;
            String nav, rawNav;
            while (i < QueryParams.FACETS_STANDARD_MAXCOUNT && navigatorIterator.hasNext()) {
                name = navigatorIterator.next().trim();
                count = theSearch.authorNavigator.get(name);
                if (count == 0) break;
                nav = (name.indexOf(' ', 0) < 0) ? "author%3A" + name : "author%3A%28" + name.replace(" ", "+") + "%29";
                /* Avoid double percent encoding in QueryParams.navurl */
                rawNav = (name.indexOf(' ', 0) < 0) ? "author:" + name : "author:(" + name.replace(" ", "+") + ")";
                if (theSearch.query.modifier.author == null || !theSearch.query.modifier.author.contains(name)) {
                    pos++;
                    prop.put("nav-authors_element_" + i + "_on", 1);
                    prop.put(fileType, "nav-authors_element_" + i + "_modifier", nav);
                } else {
                    neg++;                    
                    prop.put("nav-authors_element_" + i + "_on", 0);
                    prop.put(fileType, "nav-authors_element_" + i + "_modifier", "-" + nav);
                    nav="";
                    rawNav = "";
                }
                prop.put(fileType, "nav-authors_element_" + i + "_name", name);
                /* URL is already percent encoded : no need to re-encode specifically for the file type */
                prop.put("nav-authors_element_" + i + "_url", QueryParams.navurl(fileType, 0, theSearch.query, rawNav, false).toString());
                prop.put(fileType, "nav-authors_element_" + i + "_id", "authors_" + i);
                prop.put("nav-authors_element_" + i + "_count", count);
                prop.put("nav-authors_element_" + i + "_nl", 1);
                i++;
            }
            prop.put("nav-authors_element", i);
            prop.put("nav-authors_count", i);
            i--;
            prop.put("nav-authors_element_" + i + "_nl", 0);
            if (pos == 1 && neg == 0) {
                prop.put("nav-authors", 0); // this navigation is not useful
            }
        }

        // collection navigators
        if (theSearch.collectionNavigator == null || theSearch.collectionNavigator.isEmpty()) {
            prop.put("nav-collections", 0);
        } else {
            prop.put("nav-collections", 1);
            navigatorIterator = theSearch.collectionNavigator.keys(false);
            int i = 0, pos = 0, neg = 0;
            String nav, rawNav;
            while (i < QueryParams.FACETS_STANDARD_MAXCOUNT && navigatorIterator.hasNext()) {
                name = navigatorIterator.next().trim();
                count = theSearch.collectionNavigator.get(name);
                if (count == 0) break;
                nav = (name.indexOf(' ', 0) < 0) ? "collection%3A" + name : "collection%3A%28" + name.replace(" ", "+") + "%29";
                /* Avoid double percent encoding in QueryParams.navurl */
                rawNav = (name.indexOf(' ', 0) < 0) ? "collection:" + name : "collection:(" + name.replace(" ", "+") + ")";
                if (theSearch.query.modifier.collection == null || !theSearch.query.modifier.collection.contains(name)) {
                    pos++;
                    prop.put("nav-collections_element_" + i + "_on", 1);
                    prop.put(fileType, "nav-collections_element_" + i + "_modifier", nav);
                } else {
                    neg++;                    
                    prop.put("nav-collections_element_" + i + "_on", 0);
                    prop.put(fileType, "nav-collections_element_" + i + "_modifier", "-" + nav);
                    nav="";
                    rawNav = "";
                }
                prop.put(fileType, "nav-collections_element_" + i + "_name", name);
                /* URL is already percent encoded : no need to re-encode specifically for the file type */
                prop.put("nav-collections_element_" + i + "_url", QueryParams.navurl(fileType, 0, theSearch.query, rawNav, true).toString());
                prop.put(fileType, "nav-collections_element_" + i + "_id", "collections_" + i);
                prop.put("nav-collections_element_" + i + "_count", count);
                prop.put("nav-collections_element_" + i + "_nl", 1);
                i++;
            }
            prop.put("nav-collections_element", i);
            prop.put("nav-collections_count", i);
            i--;
            prop.put("nav-collections_element_" + i + "_nl", 0);
            if (pos == 1 && neg == 0) prop.put("nav-collections", 0); // this navigation is not useful
        }

        // topics navigator
        final ScoreMap<String> topicNavigator = theSearch.getTopicNavigator(TOPWORDS_MAXCOUNT);
        if (topicNavigator == null || topicNavigator.isEmpty()) {
            prop.put("nav-topics", "0");
        } else {
            prop.put("nav-topics", "1");
            navigatorIterator = topicNavigator.keys(false);
            int i = 0;
            // first sort the list to a form where the greatest element is in the middle
            LinkedList<Map.Entry<String, Integer>> cloud = new LinkedList<Map.Entry<String, Integer>>();
            int mincount = Integer.MAX_VALUE; int maxcount = 0;
            while (i < TOPWORDS_MAXCOUNT && navigatorIterator.hasNext()) {
                name = navigatorIterator.next();
                count = topicNavigator.get(name);
                if (count == 0) break;
                if (name == null) continue;
                int normcount = (count + TOPWORDS_MAXCOUNT - i) / 2;
                if (normcount > maxcount) maxcount = normcount;
                if (normcount < mincount) mincount = normcount;
                Map.Entry<String, Integer> entry = new AbstractMap.SimpleEntry<String, Integer>(name, normcount);
                if (cloud.size() % 2 == 0) cloud.addFirst(entry); else cloud.addLast(entry); // alternating add entry to first or last position.
                i++;
            }
            i= 0;
            for (Map.Entry<String, Integer> entry: cloud) {
                name = entry.getKey();
                count = entry.getValue();
                prop.put("nav-topics_element_" + i + "_on", 1);
                prop.put(fileType, "nav-topics_element_" + i + "_modifier", name);
                prop.put(fileType, "nav-topics_element_" + i + "_name", name);
                prop.put("nav-topics_element_" + i + "_url", QueryParams.navurl(fileType, 0, theSearch.query, name, false).toString());
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
            boolean visible = false;
            String oldQuery = theSearch.query.getQueryGoal().query_original; // prepare hack to make radio-button like navigation
            String oldProtocolModifier = theSearch.query.modifier.protocol;
            if (oldProtocolModifier != null && oldProtocolModifier.length() > 0) {theSearch.query.modifier.remove("/" + oldProtocolModifier); theSearch.query.modifier.remove(oldProtocolModifier);}
            theSearch.query.modifier.protocol = "";
            theSearch.query.getQueryGoal().query_original = oldQuery.replaceAll(" /https", "").replaceAll(" /http", "").replaceAll(" /ftp", "").replaceAll(" /smb", "").replaceAll(" /file", "");
            while (i < QueryParams.FACETS_STANDARD_MAXCOUNT && navigatorIterator.hasNext()) {
                name = navigatorIterator.next().trim();
                count = theSearch.protocolNavigator.get(name);
                if (count == 0) break;
                visible = visible || "ftp,smb".indexOf(name) >= 0;
                nav = "%2F" + name;
                /* Avoid double percent encoding in QueryParams.navurl */
                rawNav = "/" + name;
                if (oldProtocolModifier == null || !oldProtocolModifier.equals(name)) {
                    pos++;
                    prop.put("nav-protocols_element_" + i + "_on", 0);
                    prop.put(fileType, "nav-protocols_element_" + i + "_modifier", nav);
                } else {
                    neg++;                    
                    prop.put("nav-protocols_element_" + i + "_on", 1);
                    prop.put(fileType, "nav-protocols_element_" + i + "_modifier", "-" + nav);
                    nav="";
                    rawNav = "";
                }
                prop.put(fileType, "nav-protocols_element_" + i + "_name", name);
                /* URL is already percent encoded : no need to re-encode specifically for the file type */
                String url = QueryParams.navurl(fileType, 0, theSearch.query, rawNav, false).toString();
                prop.put("nav-protocols_element_" + i + "_on_url", url);
                prop.put("nav-protocols_element_" + i + "_count", count);
                prop.put("nav-protocols_element_" + i + "_nl", 1);
                i++;
            }
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
            navigatorIterator = theSearch.dateNavigator.iterator(); // this iterator is different as it iterates by the key order (which is a date order)
            int i = 0, pos = 0, neg = 0;
            long dx = -1;
            Date fromconstraint = theSearch.getQuery().modifier.from == null ? null : DateDetection.parseLine(theSearch.getQuery().modifier.from, theSearch.getQuery().timezoneOffset);
            if (fromconstraint == null) fromconstraint = new Date(System.currentTimeMillis() - AbstractFormatter.normalyearMillis);
            Date toconstraint = theSearch.getQuery().modifier.to == null ? null : DateDetection.parseLine(theSearch.getQuery().modifier.to, theSearch.getQuery().timezoneOffset);
            if (toconstraint == null) toconstraint = new Date(System.currentTimeMillis() + AbstractFormatter.normalyearMillis);
            while (i < QueryParams.FACETS_DATE_MAXCOUNT && navigatorIterator.hasNext()) {
                name = navigatorIterator.next().trim();
                if (name.length() < 10) continue;
                count = theSearch.dateNavigator.get(name);
                String shortname = name.substring(0, 10);
                long d;
                Date dd;
                try {dd = DateFormatUtil.parseDate(name); d = dd.getTime();} catch (ParseException e) {continue;}
                if (fromconstraint != null && dd.before(fromconstraint)) continue;
                if (toconstraint != null && dd.after(toconstraint)) break;
                if (dx > 0) {
                    while (d - dx > AbstractFormatter.dayMillis) {
                        dx += AbstractFormatter.dayMillis;
                        String sn = DateFormatUtil.formatExternal(new Date(dx)).substring(0, 10);
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

        // filetype navigators
        if (theSearch.filetypeNavigator == null || theSearch.filetypeNavigator.isEmpty()) {
            prop.put("nav-filetypes", 0);
        } else {
            prop.put("nav-filetypes", 1);
            navigatorIterator = theSearch.filetypeNavigator.keys(false);
            int i = 0, pos = 0, neg = 0;
            String nav, rawNav;
            while (i < QueryParams.FACETS_STANDARD_MAXCOUNT && navigatorIterator.hasNext()) {
                name = navigatorIterator.next().trim();
                count = theSearch.filetypeNavigator.get(name);
                if (count == 0) break;
                nav = "filetype%3A" + name;
                /* Avoid double percent encoding in QueryParams.navurl */
                rawNav = "filetype:" + name;
                if (theSearch.query.modifier.filetype == null || !theSearch.query.modifier.filetype.contains(name) ) {
                    pos++;
                    prop.put("nav-filetypes_element_" + i + "_on", 1);
                    prop.put(fileType, "nav-filetypes_element_" + i + "_modifier", nav);
                } else {
                    neg++;                    
                    prop.put("nav-filetypes_element_" + i + "_on", 0);                    
                    prop.put(fileType, "nav-filetypes_element_" + i + "_modifier", "-" + nav);
                    nav="";
                    rawNav = "";
                }
                prop.put(fileType, "nav-filetypes_element_" + i + "_name", name);
                /* URL is already percent encoded : no need to re-encode specifically for the file type */
                prop.put("nav-filetypes_element_" + i + "_url", QueryParams.navurl(fileType, 0, theSearch.query, rawNav, false).toString());
                prop.put(fileType, "nav-filetypes_element_" + i + "_id", "filetypes_" + i);
                prop.put("nav-filetypes_element_" + i + "_count", count);
                prop.put("nav-filetypes_element_" + i + "_nl", 1);
                i++;
            }
            prop.put("nav-filetypes_element", i);
            prop.put("nav-filetypes_count", i);
            i--;
            prop.put("nav-filetypes_element_" + i + "_nl", 0);
            if (pos == 1 && neg == 0) prop.put("nav-filetypes", 0); // this navigation is not useful
        }

        // vocabulary navigators
        final Map<String, ScoreMap<String>> vocabularyNavigators = theSearch.vocabularyNavigator;
        if (vocabularyNavigators != null && !vocabularyNavigators.isEmpty()) {
            int navvoccount = 0;
            vocnav: for (Map.Entry<String, ScoreMap<String>> ve: vocabularyNavigators.entrySet()) {
                String navname = ve.getKey();
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
                    if (!theSearch.query.modifier.toString().contains("/vocabulary/" + navname + "/" + name.replace(' ', '_'))) {
                        prop.put("nav-vocabulary_" + navvoccount + "_element_" + i + "_on", 1);
                        prop.put(fileType, "nav-vocabulary_" + navvoccount + "_element_" + i + "_modifier", nav);
                    } else {
                        prop.put("nav-vocabulary_" + navvoccount + "_element_" + i + "_on", 0);
                        prop.put(fileType, "nav-vocabulary_" + navvoccount + "_element_" + i + "_modifier", "-" + nav);
                        nav="";
                        rawNav = "";
                    }
                    prop.put(fileType, "nav-vocabulary_" + navvoccount + "_element_" + i + "_name", name);
                    /* URL is already percent encoded : no need to re-encode specifically for the file type */
                    prop.put("nav-vocabulary_" + navvoccount + "_element_" + i + "_url", QueryParams.navurl(fileType, 0, theSearch.query, rawNav, false).toString());
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
            prop.put(fileType, "cat-location_queryenc", queryenc);
        }
        prop.put("num-results_totalcount", theSearch.getResultCount());
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(theSearch.query.id(true), SearchEventType.FINALIZATION, "bottomline", 0, 0), false);
        return prop;
    }

}
//http://localhost:8090/yacysearch.html?query=java+&amp;maximumRecords=10&amp;resource=local&amp;verify=cacheonly&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=ftp://.*&amp;prefermaskfilter=&amp;constraint=&amp;contentdom=text&amp;former=java+%2Fftp&amp;startRecord=0
//http://localhost:8090/yacysearch.html?query=java+&amp;maximumRecords=10&amp;resource=local&amp;verify=cacheonly&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;constraint=&amp;contentdom=text&amp;former=java+%2Fvocabulary%2FGewerke%2FTore&amp;startRecord=0
