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

import java.util.Iterator;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.storage.ScoreMap;
import net.yacy.document.LibraryProvider;
import net.yacy.kelondro.util.EventTracker;

import de.anomic.search.QueryParams;
import de.anomic.search.SearchEvent;
import de.anomic.search.SearchEventCache;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.graphics.ProfilingGraph;


public class yacysearchtrailer {

    private static final int MAX_TOPWORDS = 12;
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        
        final String eventID = post.get("eventID", "");
        
        // find search event
        final SearchEvent theSearch = SearchEventCache.getEvent(eventID);
        if (theSearch == null) {
            // the event does not exist, show empty page
            return prop;
        }
        final QueryParams theQuery = theSearch.getQuery();
        
        // compose search navigation

        // namespace navigators
        ScoreMap<String> namespaceNavigator = theSearch.getNamespaceNavigator();
        String name;
        int count;
        Iterator<String> navigatorIterator;
        if (namespaceNavigator == null || namespaceNavigator.isEmpty()) {
            prop.put("nav-namespace", 0);
        } else {
            prop.put("nav-namespace", 1);
            navigatorIterator = namespaceNavigator.keys(false);
            int i = 0;
            while (i < 10 && navigatorIterator.hasNext()) {
                name = navigatorIterator.next();
                count = namespaceNavigator.get(name);
                prop.putJSON("nav-namespace_element_" + i + "_name", name);
                prop.put("nav-namespace_element_" + i + "_url", "<a href=\"" + QueryParams.navurl("html", 0, theQuery, theQuery.queryStringForUrl() + "+" + "inurl:" + name, theQuery.urlMask.toString(), theQuery.navigators) + "\">" + name + " (" + count + ")</a>");
                prop.putJSON("nav-namespace_element_" + i + "_url-json", QueryParams.navurl("json", 0, theQuery, theQuery.queryStringForUrl() + "+" + "inurl:" + name, theQuery.urlMask.toString(), theQuery.navigators));
                prop.put("nav-namespace_element_" + i + "_count", count);
                prop.put("nav-namespace_element_" + i + "_modifier", "inurl:" + name);
                prop.put("nav-namespace_element_" + i + "_nl", 1);
                i++;
            }
            prop.put("nav-namespace_element", i);
            i--;
            prop.put("nav-namespace_element_" + i + "_nl", 0);
        }
        
        // host navigators
        ScoreMap<String> hostNavigator = theSearch.getHostNavigator();
        if (hostNavigator == null || hostNavigator.isEmpty()) {
            prop.put("nav-domains", 0);
        } else {
            prop.put("nav-domains", 1);
            navigatorIterator = hostNavigator.keys(false);
            int i = 0;
            while (i < 20 && navigatorIterator.hasNext()) {
                name = navigatorIterator.next();
                count = hostNavigator.get(name);
                prop.putJSON("nav-domains_element_" + i + "_name", name);
                prop.put("nav-domains_element_" + i + "_url", "<a href=\"" + QueryParams.navurl("html", 0, theQuery, theQuery.queryStringForUrl() + "+" + "site:" + name, theQuery.urlMask.toString(), theQuery.navigators) + "\">" + name + " (" + count + ")</a>");
                prop.putJSON("nav-domains_element_" + i + "_url-json", QueryParams.navurl("json", 0, theQuery, theQuery.queryStringForUrl() + "+" + "site:" + name, theQuery.urlMask.toString(), theQuery.navigators));
                prop.put("nav-domains_element_" + i + "_count", count);
                prop.put("nav-domains_element_" + i + "_modifier", "site:" + name);
                prop.put("nav-domains_element_" + i + "_nl", 1);
                i++;
            }
            prop.put("nav-domains_element", i);
            i--;
            prop.put("nav-domains_element_" + i + "_nl", 0);
        }
        
        // author navigators
        ScoreMap<String> authorNavigator = theSearch.getAuthorNavigator();
        if (authorNavigator == null || authorNavigator.isEmpty()) {
            prop.put("nav-authors", 0);
        } else {
            prop.put("nav-authors", 1);
            navigatorIterator = authorNavigator.keys(false);
            int i = 0;
            String anav;
            while (i < 20 && navigatorIterator.hasNext()) {
                name = navigatorIterator.next().trim();
                count = authorNavigator.get(name);
                anav = (name.indexOf(' ') < 0) ? "author:" + name : "author:'" + name.replace(" ", "+") + "'";
                prop.putJSON("nav-authors_element_" + i + "_name", name);
                prop.put("nav-authors_element_" + i + "_url", "<a href=\"" + QueryParams.navurl("html", 0, theQuery, theQuery.queryStringForUrl() + "+" + anav, theQuery.urlMask.toString(), theQuery.navigators) + "\">" + name + " (" + count + ")</a>");
                prop.putJSON("nav-authors_element_" + i + "_url-json", QueryParams.navurl("json", 0, theQuery, theQuery.queryStringForUrl() + "+" + anav, theQuery.urlMask.toString(), theQuery.navigators));
                prop.put("nav-authors_element_" + i + "_count", count);
                prop.put("nav-authors_element_" + i + "_modifier", "author:'" + name + "'");
                prop.put("nav-authors_element_" + i + "_nl", 1);
                i++;
            }
            prop.put("nav-authors_element", i);
            i--;
            prop.put("nav-authors_element_" + i + "_nl", 0);
        }

        // topics navigator
        ScoreMap<String> topicNavigator = theSearch.getTopicNavigator(MAX_TOPWORDS);
        if (topicNavigator == null || topicNavigator.isEmpty()) {
            prop.put("nav-topics", "0");
        } else {
            prop.put("nav-topics", "1");
            navigatorIterator = topicNavigator.keys(false);
            int i = 0;
            while (i < MAX_TOPWORDS && navigatorIterator.hasNext()) {
                name = navigatorIterator.next();
                count = topicNavigator.get(name);
                if (/*(theQuery == null) ||*/ (theQuery.queryString == null)) break;
                if (name != null) {
                    prop.putJSON("nav-topics_element_" + i + "_name", name);
                    prop.put("nav-topics_element_" + i + "_url",
                            "<a href=\"" + QueryParams.navurl("html", 0, theQuery, theQuery.queryStringForUrl() + "+" + name, theQuery.urlMask.toString(), theQuery.navigators) + "\">" + name + "</a>");
                            //+"<a href=\"" + QueryParams.navurl("html", 0, display, theQuery, theQuery.queryStringForUrl() + "+-" + name, theQuery.urlMask.toString(), theQuery.navigators) + "\">-</a>")*/;
                    prop.putJSON("nav-topics_element_" + i + "_url-json", QueryParams.navurl("json", 0, theQuery, theQuery.queryStringForUrl() + "+" + name, theQuery.urlMask.toString(), theQuery.navigators));
                    prop.put("nav-topics_element_" + i + "_count", count);
                    prop.put("nav-topics_element_" + i + "_modifier", name);
                    prop.put("nav-topics_element_" + i + "_nl", 1);
                    i++;
                }
            }
            prop.put("nav-topics_element", i);
            i--;
            prop.put("nav-topics_element_" + i + "_nl", 0);
        }
        
        // about box
        String aboutBody = env.getConfig("about.body", "");
        String aboutHeadline = env.getConfig("about.headline", "");
        if ((aboutBody.length() == 0 && aboutHeadline.length() == 0) ||
            theSearch.getRankingResult().getLocalIndexCount() - theSearch.getRankingResult().getMissCount() - theSearch.getRankingResult().getSortOutCount() + theSearch.getRankingResult().getRemoteIndexCount() == 0) {
            prop.put("nav-about", 0);
        } else {
            prop.put("nav-about", 1);
            prop.put("nav-about_headline", aboutHeadline);
            prop.put("nav-about_body", aboutBody);
        }
        
        // category: location search
        // show only if there is a location database present and if there had been any search results
        if (LibraryProvider.geoLoc.locations() == 0 ||
            theSearch.getRankingResult().getLocalIndexCount() == 0) {
            prop.put("cat-location", 0);
        } else {
            prop.put("cat-location", 1);
            prop.put("cat-location_query", theQuery.queryString(true));
            prop.put("cat-location_queryenc", theQuery.queryString(true).replace(' ', '+'));        
        }
        
        EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(theQuery.id(true), SearchEvent.Type.FINALIZATION, "bottomline", 0, 0), false);
        
        return prop;
    }

}
