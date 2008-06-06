// sidebar_navigation.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 03.03.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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
import java.util.Set;
import java.util.TreeSet;

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.plasma.plasmaSearchEvent;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDomains;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class sidebar_navigation {

    private static final int MAX_TOPWORDS = 24;
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        final serverObjects prop = new serverObjects();
        
        String eventID = post.get("eventID", "");
        boolean rss = post.get("rss", "false").equals("true");
        
        // default settings for blank item
        prop.put("navigation", "0");
        prop.put("rssreferences", "0");
        
        // find search event
        plasmaSearchEvent theSearch = plasmaSearchEvent.getEvent(eventID);
        if (theSearch == null) {
            // the event does not exist, show empty page
            return prop;
        }
        plasmaSearchQuery theQuery = theSearch.getQuery();
        int offset = theQuery.neededResults() - theQuery.displayResults();
        int totalcount = theSearch.getRankingResult().getLocalResourceSize() + theSearch.getRankingResult().getRemoteResourceSize();
    
        // attach the bottom line with search references (topwords)
        final Set<String> references = theSearch.references(20);
        if (references.size() > 0) {
            // get the topwords
            final TreeSet<String> topwords = new TreeSet<String>(kelondroNaturalOrder.naturalComparator);
            String tmp = "";
            Iterator<String> i = references.iterator();
            while (i.hasNext()) {
                tmp = i.next();
                if (tmp.matches("[a-z]+")) {
                    topwords.add(tmp);
                }
            }

            // filter out the badwords
            final TreeSet<String> filteredtopwords = kelondroMSetTools.joinConstructive(topwords, plasmaSwitchboard.badwords);
            if (filteredtopwords.size() > 0) {
                kelondroMSetTools.excludeDestructive(topwords, plasmaSwitchboard.badwords);
            }

            // avoid stopwords being topwords
            if (env.getConfig("filterOutStopwordsFromTopwords", "true").equals("true")) {
                if ((plasmaSwitchboard.stopwords != null) && (plasmaSwitchboard.stopwords.size() > 0)) {
                    kelondroMSetTools.excludeDestructive(topwords, plasmaSwitchboard.stopwords);
                }
            }
            
            if (rss) {
                String word;
                int hintcount = 0;
                final Iterator<String> iter = topwords.iterator();
                while (iter.hasNext()) {
                    word = iter.next();
                    if (word != null) {
                        prop.putHTML("rssreferences_words_" + hintcount + "_word", word);
                    }
                    prop.put("rssreferences_words", hintcount);
                    if (hintcount++ > MAX_TOPWORDS) {
                        break;
                    }
                }
                prop.put("rssreferences", "1");
            } else {
                String word;
                int hintcount = 0;
                final Iterator<String> iter = topwords.iterator();
                while (iter.hasNext()) {
                    word = iter.next();
                    if (/*(theQuery == null) ||*/ (theQuery.queryString == null)) break;
                    if (word != null) {
                        prop.putHTML("navigation_topwords_words_" + hintcount + "_word", word);
                        prop.putHTML("navigation_topwords_words_" + hintcount + "_newsearch", theQuery.queryString.replace(' ', '+') + "+" + word);
                        prop.put("navigation_topwords_words_" + hintcount + "_count", theQuery.displayResults());
                        prop.put("navigation_topwords_words_" + hintcount + "_offset", "0");
                        prop.put("navigation_topwords_words_" + hintcount + "_contentdom", theQuery.contentdom());
                        prop.put("navigation_topwords_words_" + hintcount + "_resource", ((theQuery.isLocal()) ? "local" : "global"));
                        prop.put("navigation_topwords_words_" + hintcount + "_zonecode", theQuery.zonecode);
                    }
                    hintcount++;
                    if (hintcount >= MAX_TOPWORDS) break;
                }
                prop.put("navigation_topwords_words", hintcount);
                prop.put("navigation_topwords", "1");
            }
        }
        
        // compose language zone drill-down
        final int[] zones = theSearch.getRankingResult().zones();
        boolean z = false;
        domzone(prop, "All", theSearch.getRankingResult().size(), theQuery);
        if (zones[serverDomains.TLD_EuropeRussia_ID] > 0)
            { z = true; domzone(prop, "EuropeRussia", zones[serverDomains.TLD_EuropeRussia_ID], theQuery);}
        if (zones[serverDomains.TLD_MiddleSouthAmerica_ID] > 0)
            { z = true; domzone(prop, "MiddleSouthAmerica", zones[serverDomains.TLD_MiddleSouthAmerica_ID], theQuery);}
        if (zones[serverDomains.TLD_SouthEastAsia_ID] > 0)
            { z = true; domzone(prop, "SouthEastAsia", zones[serverDomains.TLD_SouthEastAsia_ID], theQuery);}
        if (zones[serverDomains.TLD_MiddleEastWestAsia_ID] > 0)
            { z = true; domzone(prop, "MiddleEastWestAsia_", zones[serverDomains.TLD_MiddleEastWestAsia_ID], theQuery);}
        if (zones[serverDomains.TLD_NorthAmericaOceania_ID] + zones[serverDomains.TLD_Generic_ID] > 0)
            { z = true; domzone(prop, "NorthAmericaOceania", zones[serverDomains.TLD_NorthAmericaOceania_ID] + zones[serverDomains.TLD_Generic_ID], theQuery);}
        if (zones[serverDomains.TLD_Africa_ID] > 0)
            { z = true; domzone(prop, "Africa", zones[serverDomains.TLD_Africa_ID], theQuery);}
        if (zones[7] > 0)
            { z = true; domzone(prop, "Intranet", zones[7], theQuery);}
        prop.put("navigation_languagezone", (z) ? "1" : "0");
        
        // compose page navigation
        StringBuffer resnav = new StringBuffer();
        int thispage = offset / theQuery.displayResults();
        if (thispage == 0) resnav.append("&lt;&nbsp;"); else {
            resnav.append(navurla(thispage - 1, theQuery));
            resnav.append("<strong>&lt;</strong></a>&nbsp;");
        }
        int numberofpages = Math.min(10, Math.max(thispage + 2, totalcount / theQuery.displayResults()));
        for (int j = 0; j < numberofpages; j++) {
            if (j == thispage) {
                resnav.append("<strong>");
                resnav.append(j + 1);
                resnav.append("</strong>&nbsp;");
            } else {
                resnav.append(navurla(j, theQuery));
                resnav.append(j + 1);
                resnav.append("</a>&nbsp;");
            }
        }
        if (thispage >= numberofpages) resnav.append("&gt;"); else {
            resnav.append(navurla(thispage + 1, theQuery));
            resnav.append("<strong>&gt;</strong></a>");
        }
        prop.put("navigation_resnav", resnav.toString());
        prop.put("navigation", "1");

        return prop;
    }
    
    private static String navurla(int page, plasmaSearchQuery theQuery) {
        return
        "<a href=\"ysearch.html?search=" + theQuery.queryString(true) +
        "&amp;count="+ theQuery.displayResults() +
        "&amp;offset=" + (page * theQuery.displayResults()) +
        "&amp;resource=" + ((theQuery.isLocal()) ? "local" : "global") +
        "&amp;urlmaskfilter=" + theQuery.urlMask +
        "&amp;prefermaskfilter=" + theQuery.prefer +
        "&amp;cat=href&amp;constraint=" + ((theQuery.constraint == null) ? "" : theQuery.constraint.exportB64()) +
        "&amp;contentdom=" + theQuery.contentdom() +
        "&amp;former=" + theQuery.queryString(true) + "\">";
    }
    
    private static void domzone(serverObjects prop, String zonename, int zonecount, plasmaSearchQuery theQuery) {
        prop.put("navigation_languagezone_" + zonename + "_count", zonecount);
        prop.putHTML("navigation_languagezone_" + zonename + "_search", theQuery.queryString.replace(' ', '+'));
        prop.put("navigation_languagezone_" + zonename + "_offset", "0");
        prop.put("navigation_languagezone_" + zonename + "_contentdom", theQuery.contentdom());
        prop.put("navigation_languagezone_" + zonename + "_resource", ((theQuery.isLocal()) ? "local" : "global"));
        prop.put("navigation_languagezone_" + zonename, 1);
    }
    
}
