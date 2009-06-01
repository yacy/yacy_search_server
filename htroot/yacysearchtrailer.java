// yacysearchitem.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 28.08.2007 on http://yacy.net
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.http.httpRequestHeader;
import de.anomic.kelondro.order.NaturalOrder;
import de.anomic.kelondro.util.SetTools;
import de.anomic.plasma.plasmaProfiling;
import de.anomic.plasma.plasmaSearchEvent;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSearchRankingProcess.hostnaventry;
import de.anomic.server.serverObjects;
import de.anomic.server.serverProfiling;
import de.anomic.server.serverSwitch;


public class yacysearchtrailer {

    private static final int MAX_TOPWORDS = 24;
    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final serverObjects prop = new serverObjects();
        
        final String eventID = post.get("eventID", "");
        final int display = post.getInt("display", 0);
        
        // default settings for blank item
        prop.put("words", "0");
        
        // find search event
        final plasmaSearchEvent theSearch = plasmaSearchEvent.getEvent(eventID);
        if (theSearch == null) {
            // the event does not exist, show empty page
            return prop;
        }
        final plasmaSearchQuery theQuery = theSearch.getQuery();
        

        // compose search navigation
        ArrayList<hostnaventry> hostNavigator = theSearch.getHostNavigator(10);
        if (hostNavigator == null) {
        	prop.put("navigation", 0);
        } else {
        	prop.put("navigation", 1);
        	hostnaventry entry;
        	for (int i = 0; i < hostNavigator.size(); i++) {
        		entry = hostNavigator.get(i);
        		prop.put("navigation_domains_" + i + "_domain", plasmaSearchQuery.navurla(0, display, theQuery, theQuery.urlMask, "site:" + entry.host) + entry.host + " (" + entry.count + ")</a>");
        	}
        	prop.put("navigation_domains", hostNavigator.size());
        }
        
        // attach the bottom line with search references (topwords)
        final Set<String> references = theSearch.references(20);
        if (references.size() > 0) {
            // get the topwords
            final TreeSet<String> topwords = new TreeSet<String>(NaturalOrder.naturalComparator);
            String tmp = "";
            final Iterator<String> i = references.iterator();
            while (i.hasNext()) {
                tmp = i.next();
                if (tmp.matches("[a-z]+")) {
                    topwords.add(tmp);
                }
            }

            // filter out the badwords
            final TreeSet<String> filteredtopwords = SetTools.joinConstructive(topwords, plasmaSwitchboard.badwords);
            if (filteredtopwords.size() > 0) {
                SetTools.excludeDestructive(topwords, plasmaSwitchboard.badwords);
            }

            // avoid stopwords being topwords
            if (env.getConfig("filterOutStopwordsFromTopwords", "true").equals("true")) {
                if ((plasmaSwitchboard.stopwords != null) && (plasmaSwitchboard.stopwords.size() > 0)) {
                    SetTools.excludeDestructive(topwords, plasmaSwitchboard.stopwords);
                }
            }
        
            String word;
            int hintcount = 0;
            final Iterator<String> iter = topwords.iterator();
            while (iter.hasNext()) {
                word = iter.next();
                if (/*(theQuery == null) ||*/ (theQuery.queryString == null)) break;
                if (word != null) {
                    prop.putHTML("words_" + hintcount + "_word", word);
                    prop.putHTML("words_" + hintcount + "_newsearch", theQuery.queryString.replace(' ', '+') + "+" + word);
                    prop.put("words_" + hintcount + "_count", theQuery.displayResults());
                    prop.put("words_" + hintcount + "_offset", "0");
                    prop.put("words_" + hintcount + "_display", display);
                    prop.put("words_" + hintcount + "_contentdom", theQuery.contentdom());
                    prop.put("words_" + hintcount + "_resource", ((theQuery.isLocal()) ? "local" : "global"));
                    prop.put("words_" + hintcount + "_nl", (iter.hasNext() && hintcount < MAX_TOPWORDS) ? 1 : 0);
                }
                if (hintcount++ > MAX_TOPWORDS) {
                    break;
                }
            }
            prop.put("words", hintcount);
        }
        serverProfiling.update("SEARCH", new plasmaProfiling.searchEvent(theQuery.id(true), plasmaSearchEvent.FINALIZATION + "-" + "bottomline", 0, 0), false);
        
        return prop;
    }

}
