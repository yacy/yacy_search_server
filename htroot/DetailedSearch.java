
// DetailedSearch.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
//
// $LastChangedDate: 2006-02-05 16:38:31 +0100 (So, 05 Feb 2006) $
// $LastChangedRevision: 1548 $
// $LastChangedBy: orbiter $
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
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.
//
// You must compile this file with
// javac -classpath .:../classes index.java
// if the shell's current path is HTROOT

import java.util.TreeSet;

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSearchTimingProfile;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;

public class DetailedSearch {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;

        // case if no values are requested
        if (post == null || env == null) {
            // we create empty entries for template strings
            final serverObjects prop = new serverObjects();
            prop.put("promoteSearchPageGreeting", env.getConfig("promoteSearchPageGreeting", ""));
            prop.put("search", "");
            prop.put("num-results", 0);
            prop.put("excluded", 0);
            prop.put("combine", 0);
            prop.put("resultbottomline", 0);
            prop.put("localCount", 10);
            prop.put("localWDist", 999);
            //prop.put("globalChecked", "checked");
            prop.put("globalChecked", "");
            prop.put("postsortChecked", "checked");
            prop.put("localTime", 6);
            prop.put("results", "");
            prop.put("urlmaskoptions", 0);
            prop.put("urlmaskoptions_urlmaskfilter", ".*");
            String defaultRankingProfile = new plasmaSearchRankingProfile().toExternalString();
            prop.putAll(new plasmaSearchRankingProfile("", defaultRankingProfile).toExternalMap("local"));
            return prop;
        }

        boolean global = (post == null) ? false : post.get("global", "").equals("on");
        boolean postsort = (post == null) ? false : post.get("postsort", "").equals("on");
        final boolean indexDistributeGranted = sb.getConfig("allowDistributeIndex", "true").equals("true");
        final boolean indexReceiveGranted = sb.getConfig("allowReceiveIndex", "true").equals("true");
        if (!indexDistributeGranted || !indexReceiveGranted) { global = false; }
        
        int wdist = Integer.parseInt(post.get("localWDist", "999"));
        String querystring = post.get("search", "").trim();
        if ((querystring.length() > 2) && (querystring.charAt(0) == '"') && (querystring.charAt(querystring.length() - 1) == '"')) {
            querystring = querystring.substring(1, querystring.length() - 1).trim();
            wdist = 1;
        }
        if (sb.facilityDB != null) try { sb.facilityDB.update("zeitgeist", querystring, post); } catch (Exception e) {}
        final TreeSet query = plasmaSearchQuery.cleanQuery(querystring);
        // filter out stopwords
        final TreeSet filtered = kelondroMSetTools.joinConstructive(query, plasmaSwitchboard.stopwords);
        if (filtered.size() > 0) {
            kelondroMSetTools.excludeDestructive(query, plasmaSwitchboard.stopwords);
        }

        // if a minus-button was hit, remove a special reference first
        if (post.containsKey("deleteref")) {
            if (!sb.verifyAuthentication(header, true)) {
                final serverObjects prop = new serverObjects();
                prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                return prop;
            }
            final String delHash = post.get("deleteref", "");
            sb.removeReferences(delHash, query);
        }
        
        // prepare search order
        final int count = Integer.parseInt(post.get("localCount", "10"));
        final long searchtime = 1000 * Long.parseLong(post.get("localTime", "10"));
        final boolean yacyonline = ((yacyCore.seedDB != null) &&
                                    (yacyCore.seedDB.mySeed != null) &&
                                    (yacyCore.seedDB.mySeed.getAddress() != null));

        String urlmask = "";
        if (post.containsKey("urlmask") && post.get("urlmask").equals("no")) {
            urlmask = ".*";
        } else {
            urlmask = (post.containsKey("urlmaskfilter")) ? (String) post.get("urlmaskfilter") : ".*";
        }

        // do the search
        plasmaSearchQuery thisSearch = new plasmaSearchQuery(query, wdist, "", count, searchtime, urlmask,
                                                             ((global) && (yacyonline) && (!(env.getConfig("last-search","").equals(querystring)))) ? plasmaSearchQuery.SEARCHDOM_GLOBALDHT : plasmaSearchQuery.SEARCHDOM_LOCAL,
                                                             "", 20, plasmaSearchQuery.catchall_constraint);
        plasmaSearchRankingProfile localRanking = new plasmaSearchRankingProfile("local", post.toString());
        plasmaSearchTimingProfile localTiming  = new plasmaSearchTimingProfile(4 * thisSearch.maximumTime / 10, thisSearch.wantedResults);
        plasmaSearchTimingProfile remoteTiming = new plasmaSearchTimingProfile(6 * thisSearch.maximumTime / 10, thisSearch.wantedResults);
        final serverObjects prop = sb.searchFromLocal(thisSearch, localRanking, localTiming, remoteTiming, postsort);

        // remember the last search expression
        env.setConfig("last-search", querystring);
        // process result of search
        prop.put("type_resultbottomline", 0);
        if (filtered.size() > 0){
            prop.put("excluded", 1);
            prop.put("excluded_stopwords", filtered.toString());
        } else {
            prop.put("excluded", 0);
        }

        if (prop == null || prop.size() == 0) {
            prop.put("num-results", 0);
        } else {
            final int linkcount = Integer.parseInt(prop.get("type_linkcount", "0"));
            final int orderedcount = Integer.parseInt(prop.get("type_orderedcount", "0"));
            final int totalcount = Integer.parseInt(prop.get("type_totalcount", "0"));
            final Object[] references = (Object[]) prop.get("type_references", new String[0]);
            prop.put("type_num-results", 1);
            prop.put("type_num-results_linkcount", linkcount);
            prop.put("type_num-results_orderedcount", orderedcount);
            prop.put("type_num-results_totalcount", totalcount);
            int hintcount = references.length;
            if (hintcount > 0) {
                if (hintcount > 16) { hintcount = 16; }
                prop.put("type_combine", 1);
                String word;
                for (int i = 0; i < hintcount; i++) {
                    word = (String) references[i];
                    if (word != null) {
                        prop.put("type_combine_words_" + i + "_word", word);
                        prop.put("type_combine_words_" + i + "_newsearch", post.get("search", "").replace(' ', '+') + "+" + word);
                        prop.put("type_combine_words_" + i + "_count", count);
                        prop.put("type_combine_words_" + i + "_ranking", localRanking.toExternalURLGet("local").toString());
                        prop.put("type_combine_words_" + i + "_resource", ((global) ? "global" : "local"));
                        prop.put("type_combine_words_" + i + "_time", (searchtime / 1000));
                    }
                    prop.put("type_combine_words", i);
                }
            }
        }

        if (urlmask.equals(".*")) {
            prop.put("urlmaskoptions", 0);
        } else {
            prop.put("urlmaskoptions", 1);
        }

        prop.put("urlmaskoptions_urlmaskfilter", urlmask);
        prop.put("type", "0");
        prop.put("localCount", count);
        prop.put("localWDist", wdist);
        prop.put("globalChecked", (global) ? "checked" : "");
        prop.put("postsortChecked", (postsort) ? "checked" : "");
        prop.put("localTime", searchtime/1000);
        prop.put("search", post.get("search", ""));
        prop.putAll(localRanking.toExternalMap("local"));
        
        // 'enrich search' variables
        prop.put("num-results_former", post.get("search", ""));
        prop.put("num-results_time", searchtime / 1000);
        prop.put("num-results_count", count);
        prop.put("num-results_resource", (global) ? "global" : "local");
        prop.put("num-results_ranking", localRanking.toExternalURLGet("local").toString());

        // return rewrite properties
        prop.put("promoteSearchPageGreeting", env.getConfig("promoteSearchPageGreeting", ""));

        // adding some additional properties needed for the rss feed
        String hostName = (String) header.get("Host","localhost");
        if (hostName.indexOf(":") == -1) hostName += ":" + serverCore.getPortNr(env.getConfig("port","8080"));
        prop.put("rssYacyImageURL","http://" + hostName + "/env/grafics/yacy.gif");

        return prop;
    }

}
