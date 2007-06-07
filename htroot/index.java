// index.java
// (C) 2004, 2005, 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 2004 on http://www.anomic.de
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
//
// You must compile this file with
// javac -classpath .:../classes index.java
// if the shell's current path is HTROOT

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;

import de.anomic.http.httpHeader;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class index {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        
        // access control
        boolean publicPage = sb.getConfigBool("publicSearchpage", true);
        boolean authorizedAccess = sb.verifyAuthentication(header, false);
        if ((post != null) && (post.containsKey("publicPage"))) {
            if (!authorizedAccess) {
                prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                return prop;
            }
            publicPage = post.get("publicPage", "0").equals("1");
            sb.setConfig("publicSearchpage", publicPage);
        }
        
        boolean global = (post == null) ? true : post.get("resource", "global").equals("global");
        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        final int display = ((post == null) || (!authenticated)) ? 0 : post.getInt("display", 0);
        final int searchoptions = (post == null) ? 0 : post.getInt("searchoptions", 0);
        final String former = (post == null) ? "" : post.get("former", "");
        final int count = (post == null) ? 10 : post.getInt("count", 10);
        final int time = (post == null) ? 10 : post.getInt("time", 6);
        final String urlmaskfilter = (post == null) ? ".*" : post.get("urlmaskfilter", ".*");
        final String prefermaskfilter = (post == null) ? "" : post.get("prefermaskfilter", "");
        final String constraint = (post == null) ? plasmaSearchQuery.catchall_constraint.exportB64() : post.get("constraint", "______");
        final String cat = (post == null) ? "href" : post.get("cat", "href");
        final int type = (post == null) ? 0 : post.getInt("type", 0);
        
        final boolean indexDistributeGranted = sb.getConfig(plasmaSwitchboard.INDEX_DIST_ALLOW, "true").equals("true");
        final boolean indexReceiveGranted = sb.getConfig("allowReceiveIndex", "true").equals("true");
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

        // search domain
        int contentdom = plasmaSearchQuery.CONTENTDOM_TEXT;
        String cds = (post == null) ? "text" : post.get("contentdom", "text");
        if (cds.equals("text")) contentdom = plasmaSearchQuery.CONTENTDOM_TEXT;
        if (cds.equals("audio")) contentdom = plasmaSearchQuery.CONTENTDOM_AUDIO;
        if (cds.equals("video")) contentdom = plasmaSearchQuery.CONTENTDOM_VIDEO;
        if (cds.equals("image")) contentdom = plasmaSearchQuery.CONTENTDOM_IMAGE;
        if (cds.equals("app")) contentdom = plasmaSearchQuery.CONTENTDOM_APP;
        
        long mylinks = 0;
        try {
            prop.put("links", groupDigits(mylinks = Long.parseLong(yacyCore.seedDB.mySeed.get(yacySeed.LCOUNT, "0"))));
        } catch (NumberFormatException e) { prop.put("links", "0"); }
        prop.put("total-links", groupDigits(mylinks + yacyCore.seedDB.countActiveURL()));
        
        // we create empty entries for template strings
        String promoteSearchPageGreeting = env.getConfig("promoteSearchPageGreeting", "");
        if (promoteSearchPageGreeting.length() == 0) promoteSearchPageGreeting = "P2P WEB SEARCH";
        prop.putASIS("promoteSearchPageGreeting", promoteSearchPageGreeting);
        prop.put("former", former);
        prop.put("num-results", 0);
        prop.put("excluded", 0);
        prop.put("combine", 0);
        prop.put("resultbottomline", 0);
        prop.put("searchoptions", searchoptions);
        prop.put("searchoptions_count-10", (count == 10) ? 1 : 0);
        prop.put("searchoptions_count-50", (count == 50) ? 1 : 0);
        prop.put("searchoptions_count-100", (count == 100) ? 1 : 0);
        prop.put("searchoptions_count-1000", (count == 1000) ? 1 : 0);
        prop.put("searchoptions_resource-global", ((global) ? 1 : 0));
        prop.put("searchoptions_resource-local", ((global) ? 0 : 1));
        prop.put("searchoptions_time-1", (time == 1) ? 1 : 0);
        prop.put("searchoptions_time-3", (time == 3) ? 1 : 0);
        prop.put("searchoptions_time-6", (time == 6) ? 1 : 0);
        prop.put("searchoptions_time-10", (time == 10) ? 1 : 0);
        prop.put("searchoptions_time-30", (time == 30) ? 1 : 0);
        prop.put("searchoptions_time-60", (time == 60) ? 1 : 0);
        prop.put("searchoptions_urlmaskoptions", 0);
        prop.put("searchoptions_urlmaskoptions_urlmaskfilter", urlmaskfilter);
        prop.put("searchoptions_prefermaskoptions", 0);
        prop.put("searchoptions_prefermaskoptions_prefermaskfilter", prefermaskfilter);
        prop.put("searchoptions_indexofChecked", "");
        prop.put("searchoptions_publicSearchpage", (publicPage == true) ? 0 : 1);
        prop.put("results", "");
        prop.put("cat", cat);
        prop.put("type", type);
        prop.put("depth", "0");
        prop.put("display", display);
        prop.put("constraint", constraint);
        prop.put("searchoptions_display", display);
        prop.put("contentdomCheckText", (contentdom == plasmaSearchQuery.CONTENTDOM_TEXT) ? 1 : 0);
        prop.put("contentdomCheckAudio", (contentdom == plasmaSearchQuery.CONTENTDOM_AUDIO) ? 1 : 0);
        prop.put("contentdomCheckVideo", (contentdom == plasmaSearchQuery.CONTENTDOM_VIDEO) ? 1 : 0);
        prop.put("contentdomCheckImage", (contentdom == plasmaSearchQuery.CONTENTDOM_IMAGE) ? 1 : 0);
        prop.put("contentdomCheckApp", (contentdom == plasmaSearchQuery.CONTENTDOM_APP) ? 1 : 0);

        return prop;
    }
    
    private static String groupDigits(long Number) {
        final String s = Long.toString(Number);
        String t = "";
        for (int i = 0; i < s.length(); i++) t = s.charAt(s.length() - i - 1) + (((i % 3) == 0) ? "." : "") + t;
        return t.substring(0, t.length() - 1);
    }
}
