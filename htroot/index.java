// index.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.TreeSet;

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSearchTimingProfile;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSearchPreOrder;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;

public class index {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;

        boolean global = (post == null) ? true : post.get("resource", "global").equals("global");
        final boolean indexDistributeGranted = sb.getConfig("allowDistributeIndex", "true").equals("true");
        final boolean indexReceiveGranted = sb.getConfig("allowReceiveIndex", "true").equals("true");
        if (!indexDistributeGranted || !indexReceiveGranted) { global = false; }

        // case if no values are requested
        final String referer = (String) header.get("Referer");
        if (post == null || env == null) {

            // save referrer
            // System.out.println("HEADER=" + header.toString());
            if (referer != null) {
                URL url;
                try { url = new URL(referer); } catch (MalformedURLException e) { url = null; }
                if ((url != null) && (serverCore.isNotLocal(url))) {
                    final HashMap referrerprop = new HashMap();
                    referrerprop.put("count", "1");
                    referrerprop.put("clientip", header.get("CLIENTIP"));
                    referrerprop.put("useragent", header.get("User-Agent"));
                    referrerprop.put("date", (new serverDate()).toShortString(false));
                    if (sb.facilityDB != null) try { sb.facilityDB.update("backlinks", referer, referrerprop); } catch (IOException e) {}
                }
            }

            // we create empty entries for template strings
            final serverObjects prop = new serverObjects();
            prop.put("promoteSearchPageGreeting", env.getConfig("promoteSearchPageGreeting", ""));
            prop.put("former", "");
            prop.put("num-results", 0);
            prop.put("excluded", 0);
            prop.put("combine", 0);
            prop.put("resultbottomline", 0);
            prop.put("count-10", 0);
            prop.put("count-50", 0);
            prop.put("count-100", 0);
            prop.put("count-1000", 0);
            prop.put("order-ybr-date-quality", plasmaSearchPreOrder.canUseYBR() ? 1 : 0);
            prop.put("order-ybr-quality-date", 0);
            prop.put("order-date-ybr-quality", 0);
            prop.put("order-quality-ybr-date", 0);
            prop.put("order-date-quality-ybr", plasmaSearchPreOrder.canUseYBR() ? 0 : 1);
            prop.put("order-quality-date-ybr", 0);
            prop.put("resource-global", ((global) ? 1 : 0));
            prop.put("resource-local", ((global) ? 0 : 1));
            prop.put("time-1", 0);
            prop.put("time-3", 0);
            prop.put("time-6", 1);
            prop.put("time-10", 0);
            prop.put("time-30", 0);
            prop.put("time-60", 0);
            prop.put("results", "");
            prop.put("urlmaskoptions", 0);
            prop.put("urlmaskoptions_urlmaskfilter", ".*");
            return prop;
        }

        // SEARCH
        // process search words
        int maxDistance = Integer.MAX_VALUE;
        String querystring = post.get("search", "").trim();
        if ((querystring.charAt(0) == '"') && (querystring.charAt(querystring.length() - 1) == '"')) {
            querystring = querystring.substring(1, querystring.length() - 1).trim();
            maxDistance = 1;
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
        final String order = post.get("order", "");
        final int count = Integer.parseInt(post.get("count", "10"));
        final long searchtime = 1000 * Long.parseLong(post.get("time", "10"));
        final boolean yacyonline = ((yacyCore.seedDB != null) &&
                                    (yacyCore.seedDB.mySeed != null) &&
                                    (yacyCore.seedDB.mySeed.getAddress() != null));

        String order1="", order2="", order3="";
        if (order.startsWith("YBR"))        order1 = plasmaSearchQuery.ORDER_YBR;
        if (order.startsWith("Date"))       order1 = plasmaSearchQuery.ORDER_DATE;
        if (order.startsWith("Quality"))    order1 = plasmaSearchQuery.ORDER_QUALITY;
        if (order.indexOf("-YBR-") > 0)     order2 = plasmaSearchQuery.ORDER_YBR;
        if (order.indexOf("-Date-") > 0)    order2 = plasmaSearchQuery.ORDER_DATE;
        if (order.indexOf("-Quality-") > 0) order2 = plasmaSearchQuery.ORDER_QUALITY;
        if (order.endsWith("YBR"))          order3 = plasmaSearchQuery.ORDER_YBR;
        if (order.endsWith("Date"))         order3 = plasmaSearchQuery.ORDER_DATE;
        if (order.endsWith("Quality"))      order3 = plasmaSearchQuery.ORDER_QUALITY;
        String urlmask = "";
        if (post.containsKey("urlmask") && post.get("urlmask").equals("no")) {
            urlmask = ".*";
        } else {
            urlmask = (post.containsKey("urlmaskfilter")) ? (String) post.get("urlmaskfilter") : ".*";
        }

        // do the search
        plasmaSearchQuery thisSearch = new plasmaSearchQuery(query, maxDistance, count, searchtime, urlmask, referer,
                                                             ((global) && (yacyonline) && (!(env.getConfig("last-search","").equals(querystring)))) ? plasmaSearchQuery.SEARCHDOM_GLOBALDHT : plasmaSearchQuery.SEARCHDOM_LOCAL,
                                                             "", 20);
        plasmaSearchRankingProfile ranking = new plasmaSearchRankingProfile(new String[]{order1, order2, order3});
        plasmaSearchTimingProfile localTiming  = new plasmaSearchTimingProfile(4 * thisSearch.maximumTime / 10, thisSearch.wantedResults);
        plasmaSearchTimingProfile remoteTiming = new plasmaSearchTimingProfile(6 * thisSearch.maximumTime / 10, thisSearch.wantedResults);
        final serverObjects prop = sb.searchFromLocal(thisSearch, ranking, localTiming, remoteTiming);

        /*
        final serverObjects prop = sb.searchFromLocal(query, order1, order2, count,
                                   ((global) && (yacyonline) && (!(env.getConfig("last-search","").equals(querystring)))),
                                     searchtime, urlmask);
                                     */
        // remember the last search expression
        env.setConfig("last-search", querystring);
        // process result of search
        prop.put("resultbottomline", 0);
        if (filtered.size() > 0){
            prop.put("excluded", 1);
            prop.put("excluded_stopwords", filtered.toString());
        } else {
            prop.put("excluded", 0);
        }

        if (prop == null || prop.size() == 0) {
            if (post.get("search", "").length() < 3) {
                prop.put("num-results", 2); // no results - at least 3 chars
            } else {
                prop.put("num-results", 1); //no results
            }
        } else {
            final int linkcount = Integer.parseInt(prop.get("linkcount", "0"));
            final int orderedcount = Integer.parseInt(prop.get("orderedcount", "0"));
            final int totalcount = Integer.parseInt(prop.get("totalcount", "0"));
            if (totalcount > 10) {
                final Object[] references = (Object[]) prop.get("references", new String[0]);
                prop.put("num-results", 4);
                prop.put("num-results_linkcount", linkcount);
                prop.put("num-results_orderedcount", orderedcount);
                prop.put("num-results_totalcount", totalcount);
                int hintcount = references.length;
                if (hintcount > 0) {
                    if (hintcount > 16) { hintcount = 16; }
                    prop.put("combine", 1);
                    String word;
                    for (int i = 0; i < hintcount; i++) {
                        word = (String) references[i];
                        if (word != null) {
                            prop.put("combine_words_" + i + "_word", word);
                            prop.put("combine_words_" + i + "_newsearch", post.get("search", "").replace(' ', '+') + "+" + word);
                            prop.put("combine_words_" + i + "_count", count);
                            prop.put("combine_words_" + i + "_order", order);
                            prop.put("combine_words_" + i + "_resource", ((global) ? "global" : "local"));
                            prop.put("combine_words_" + i + "_time", (searchtime / 1000));
                        }
                        prop.put("combine_words", i);
                    }
                }
            } else {
                if (totalcount == 0) {
                    prop.put("num-results", 3);//long
                } else {
                    prop.put("num-results", 4);
                    prop.put("num-results_linkcount", linkcount);
                    prop.put("num-results_orderedcount", orderedcount);
                    prop.put("num-results_totalcount", totalcount);
                }
            }
        }

        if (urlmask.equals(".*")) {
            prop.put("urlmaskoptions", 0);
        } else {
            prop.put("urlmaskoptions", 1);
        }

        prop.put("urlmaskoptions_urlmaskfilter", urlmask);

        if (yacyonline) {
            if (global) {
                prop.put("resultbottomline", 1);
                prop.put("resultbottomline_globalresults", prop.get("globalresults", "0"));
            } else {
                prop.put("resultbottomline", 2);
            }
        } else {
            if (global) {
                prop.put("resultbottomlien", 3);
            } else {
                prop.put("resultbottomline", 4);
            }
        }

        prop.put("count-10", ((count == 10)) ? 1 : 0);
        prop.put("count-50", ((count == 50)) ? 1 : 0);
        prop.put("count-100", ((count == 100)) ? 1 : 0);
        prop.put("count-1000", ((count == 1000)) ? 1 : 0);
        prop.put("order-ybr-date-quality", ((order.equals("YBR-Date-Quality")) ? 1 : 0));
        prop.put("order-ybr-quality-date", ((order.equals("YBR-Quality-Date")) ? 1 : 0));
        prop.put("order-date-ybr-quality", ((order.equals("Date-YBR-Quality")) ? 1 : 0));
        prop.put("order-quality-ybr-date", ((order.equals("Quality-YBR-Date")) ? 1 : 0));
        prop.put("order-date-quality-ybr", ((order.equals("Date-Quality-YBR")) ? 1 : 0));
        prop.put("order-quality-date-ybr", ((order.equals("Quality-Date-YBR")) ? 1 : 0));
        prop.put("resource-global", ((global) ? 1 : 0));
        prop.put("resource-local", ((global) ? 0 : 1));
        prop.put("time-1", ((searchtime == 1000) ? 1 : 0));
        prop.put("time-3", ((searchtime == 3000) ? 1 : 0));
        prop.put("time-6", ((searchtime == 6000) ? 1 : 0));
        prop.put("time-10", ((searchtime == 10000) ? 1 : 0));
        prop.put("time-30", ((searchtime == 30000) ? 1 : 0));
        prop.put("time-60", ((searchtime == 60000) ? 1 : 0));
        prop.put("former", post.get("search", ""));

        // 'enrich search' variables
        prop.put("num-results_former", post.get("search", ""));
        prop.put("num-results_time", searchtime / 1000);
        prop.put("num-results_count", count);
        prop.put("num-results_resource", (global) ? "global" : "local");
        prop.put("num-results_order", order);

        // return rewrite properties
        prop.put("promoteSearchPageGreeting", env.getConfig("promoteSearchPageGreeting", ""));

        // adding some additional properties needed for the rss feed
        String hostName = (String) header.get("Host","localhost");
        if (hostName.indexOf(":") == -1) hostName += ":" + env.getConfig("port","8080");
        prop.put("rssYacyImageURL","http://" + hostName + "/env/grafics/yacy.gif");

        return prop;
    }



}
