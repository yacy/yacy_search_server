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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroRow;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSearchPreOrder;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacySeed;
import de.anomic.net.URL;

public class index {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;

        boolean authenticated = sb.adminAuthenticated(header) >= 2;
        int display = ((post == null) || (!authenticated)) ? 0 : post.getInt("display", 0);
        boolean global = (post == null) ? true : post.get("resource", "global").equals("global");

        int searchoptions = (post == null) ? 0 : post.getInt("searchoptions", 0);

        final boolean indexDistributeGranted = sb.getConfig("allowDistributeIndex", "true").equals("true");
        final boolean indexReceiveGranted = sb.getConfig("allowReceiveIndex", "true").equals("true");
        final String handover = (post == null) ? "" : post.get("handover", "");
        if (!indexDistributeGranted || !indexReceiveGranted) { global = false; }

        final String referer = (String) header.get("Referer");

        if (referer != null) {
            URL url;
            try {
                url = new URL(referer);
            } catch (MalformedURLException e) {
                url = null;
            }
            if ((url != null) && (serverCore.isNotLocal(url))) {
                final HashMap referrerprop = new HashMap();
                referrerprop.put("count", "1");
                referrerprop.put("clientip", header.get("CLIENTIP"));
                referrerprop.put("useragent", header.get("User-Agent"));
                referrerprop.put("date", (new serverDate()).toShortString(false));
                if (sb.facilityDB != null) try {sb.facilityDB.update("backlinks", referer, referrerprop);} catch (IOException e) {}
            }
        }

        // we create empty entries for template strings
        final serverObjects prop = new serverObjects();
        String promoteSearchPageGreeting = env.getConfig("promoteSearchPageGreeting", "");
        if (promoteSearchPageGreeting.length() == 0) promoteSearchPageGreeting = "P2P WEB SEARCH";
        prop.put("promoteSearchPageGreeting", promoteSearchPageGreeting);
        prop.put("former", handover);
        prop.put("num-results", 0);
        prop.put("excluded", 0);
        prop.put("combine", 0);
        prop.put("resultbottomline", 0);
        prop.put("searchoptions", searchoptions);
        prop.put("searchoptions_count-10", 1);
        prop.put("searchoptions_count-50", 0);
        prop.put("searchoptions_count-100", 0);
        prop.put("searchoptions_count-1000", 0);
        prop.put("searchoptions_order-ybr-date-quality", plasmaSearchPreOrder.canUseYBR() ? 1 : 0);
        prop.put("searchoptions_order-ybr-quality-date", 0);
        prop.put("searchoptions_order-date-ybr-quality", 0);
        prop.put("searchoptions_order-quality-ybr-date", 0);
        prop.put("searchoptions_order-date-quality-ybr", plasmaSearchPreOrder.canUseYBR() ? 0 : 1);
        prop.put("searchoptions_order-quality-date-ybr", 0);
        prop.put("searchoptions_resource-global", ((global) ? 1 : 0));
        prop.put("searchoptions_resource-local", ((global) ? 0 : 1));
        prop.put("searchoptions_time-1", 0);
        prop.put("searchoptions_time-3", 0);
        prop.put("searchoptions_time-6", 1);
        prop.put("searchoptions_time-10", 0);
        prop.put("searchoptions_time-30", 0);
        prop.put("searchoptions_time-60", 0);
        prop.put("searchoptions_urlmaskoptions", 0);
        prop.put("searchoptions_urlmaskoptions_urlmaskfilter", ".*");
        prop.put("searchoptions_prefermaskoptions", 0);
        prop.put("searchoptions_prefermaskoptions_prefermaskfilter", "");
        prop.put("results", "");
        prop.put("cat", "href");
        prop.put("type", "0");
        prop.put("depth", "0");
        prop.put("display", display);
        prop.put("searchoptions_display", display);
        
        
        // create surftipps
        int maxCount = yacyCore.newsPool.size(yacyNewsPool.INCOMING_DB);
        if (maxCount > 300) maxCount = 300;
        kelondroMScoreCluster surftipps = new kelondroMScoreCluster();
        yacyNewsRecord record;
        kelondroRow rowdef = new kelondroRow("String url-255, String title-120, String description-120");
        for (int j = 0; j < maxCount; j++) try {
            record = yacyCore.newsPool.get(yacyNewsPool.INCOMING_DB, j);
            if (record == null) continue;
            
            if (record.category().equals("crwlstrt")) {
                String intention = record.attribute("intention", "");
                surftipps.setScore(
                        rowdef.newEntry(new byte[][]{
                                record.attribute("startURL", "").getBytes(),
                                ((intention.length() == 0) ? record.attribute("startURL", "") : intention).getBytes(),
                                ("Crawl Start Point").getBytes()
                        }), 2 + Math.min(10, intention.length() / 4) + timeFactor(record.created()));
            }
            
            if (record.category().equals("prfleupd")) {
                surftipps.setScore(
                        rowdef.newEntry(new byte[][]{
                                record.attribute("homepage", "").getBytes(),
                                ("Home Page of " + record.attribute("nickname", "")).getBytes(),
                                ("Profile Update").getBytes()
                        }), 1 + timeFactor(record.created()));
            }
            
            if (record.category().equals("bkmrkadd")) {
                surftipps.setScore(
                        rowdef.newEntry(new byte[][]{
                                record.attribute("url", "").getBytes(),
                                (record.attribute("title", "")).getBytes(),
                                ("Bookmark: " + record.attribute("description", "")).getBytes()
                        }), 8 + timeFactor(record.created()));
            }
            
            if (record.category().equals("stippadd")) {
                surftipps.setScore(
                        rowdef.newEntry(new byte[][]{
                                record.attribute("url", "").getBytes(),
                                (record.attribute("title", "")).getBytes(),
                                ("Surf Tipp: " + record.attribute("description", "")).getBytes()
                        }), 5 + timeFactor(record.created()));
            }
            
            if (record.category().equals("wiki_upd")) {
                yacySeed seed = yacyCore.seedDB.getConnected(record.originator());
                if (seed == null) seed = yacyCore.seedDB.getDisconnected(record.originator());
                if (seed != null) surftipps.setScore(
                        rowdef.newEntry(new byte[][]{
                                ("http://" + seed.getAddress() + "/Wiki.html?page=" + record.attribute("page", "")).getBytes(),
                                (record.attribute("author", "Anonymous") + ": " + record.attribute("page", "")).getBytes(),
                                ("Wiki Update: " + record.attribute("description", "")).getBytes()
                        }), 4 + timeFactor(record.created()));
            }
            
        } catch (IOException e) {e.printStackTrace();}
        
        // read out surftipp array and create property entries
        Iterator k = surftipps.scores(false);
        int i = 0;
        kelondroRow.Entry row;
        while (k.hasNext()) {
            row = (kelondroRow.Entry) k.next();
            try {
                prop.put("surftipps_results_" + i + "_recommend", (yacyCore.newsPool.getSpecific(yacyNewsPool.OUTGOING_DB, "stippadd", "url", row.getColString(1, null)) == null) ? 1 : 0);
                prop.put("surftipps_results_" + i + "_recommend_deletelink", "/index.html?");
                prop.put("surftipps" + i + "_recommend_recommendlink", "/index.html?");
                prop.put("surftipps_results_" + i + "_url", row.getColString(0, null));
                prop.put("surftipps_results_" + i + "_urlname", nxTools.shortenURLString(row.getColString(0, null), 60));
                prop.put("surftipps_results_" + i + "_title", row.getColString(1, null));
                prop.put("surftipps_results_" + i + "_description", row.getColString(2, null));
                i++;
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (i >= 50) break;
        }
        prop.put("surftipps_results", i);
        prop.put("surftipps", 1);
        
        
        return prop;
    }

    private static int timeFactor(Date created) {
        return (int) Math.max(0, 10 - ((System.currentTimeMillis() - created.getTime()) / 24 / 60 / 60 / 1000));
    }
    
}
