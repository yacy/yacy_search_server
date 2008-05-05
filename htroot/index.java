// index.java
// (C) 2004-2007 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
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


import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class index {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
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
        final int count = Math.min(100, (post == null) ? 10 : post.getInt("count", 10));
        final String urlmaskfilter = (post == null) ? ".*" : post.get("urlmaskfilter", ".*");
        final String prefermaskfilter = (post == null) ? "" : post.get("prefermaskfilter", "");
        final String constraint = (post == null) ? "" : post.get("constraint", "");
        final String cat = (post == null) ? "href" : post.get("cat", "href");
        final int type = (post == null) ? 0 : post.getInt("type", 0);
        
        final boolean indexDistributeGranted = sb.getConfigBool(plasmaSwitchboard.INDEX_DIST_ALLOW, true);
        final boolean indexReceiveGranted = sb.getConfigBool(plasmaSwitchboard.INDEX_RECEIVE_ALLOW, true);
        //global = global && indexDistributeGranted && indexReceiveGranted;
        
        // search domain
        int contentdom = plasmaSearchQuery.CONTENTDOM_TEXT;
        String cds = (post == null) ? "text" : post.get("contentdom", "text");
        if (cds.equals("text")) contentdom = plasmaSearchQuery.CONTENTDOM_TEXT;
        if (cds.equals("audio")) contentdom = plasmaSearchQuery.CONTENTDOM_AUDIO;
        if (cds.equals("video")) contentdom = plasmaSearchQuery.CONTENTDOM_VIDEO;
        if (cds.equals("image")) contentdom = plasmaSearchQuery.CONTENTDOM_IMAGE;
        if (cds.equals("app")) contentdom = plasmaSearchQuery.CONTENTDOM_APP;
        
        //long mylinks = 0;
        prop.putNum("links", sb.wordIndex.seedDB.mySeed().getLinkCount());
        
        // we create empty entries for template strings
        String promoteSearchPageGreeting = env.getConfig("promoteSearchPageGreeting", "");
        if (env.getConfigBool("promoteSearchPageGreeting.useNetworkName", false)) promoteSearchPageGreeting = env.getConfig("network.unit.description", "");
        if (promoteSearchPageGreeting.length() == 0) promoteSearchPageGreeting = "P2P WEB SEARCH";
        prop.putHTML("promoteSearchPageGreeting", promoteSearchPageGreeting);
        prop.putHTML("former", former);
        prop.put("num-results", "0");
        prop.put("excluded", "0");
        prop.put("combine", "0");
        prop.put("resultbottomline", "0");
        prop.put("searchoptions", searchoptions);
        prop.put("searchoptions_count-10", (count == 10) ? "1" : "0");
        prop.put("searchoptions_count-50", (count == 50) ? "1" : "0");
        prop.put("searchoptions_count-100", (count == 100) ? "1" : "0");
        prop.put("searchoptions_resource-global", global ? "1" : "0");
        prop.put("searchoptions_resource-global-disabled", (indexReceiveGranted && indexDistributeGranted) ? "0" : "1");
        prop.put("searchoptions_resource-global-disabled_reason", (indexReceiveGranted) ? "0" : (indexDistributeGranted ? "1" : "2"));
        prop.put("searchoptions_resource-local", global ? "0" : "1");
        prop.put("searchoptions_urlmaskoptions", "0");
        prop.putHTML("searchoptions_urlmaskoptions_urlmaskfilter", urlmaskfilter);
        prop.put("searchoptions_prefermaskoptions", "0");
        prop.putHTML("searchoptions_prefermaskoptions_prefermaskfilter", prefermaskfilter);
        prop.put("searchoptions_indexofChecked", "");
        prop.put("searchoptions_publicSearchpage", (publicPage == true) ? "0" : "1");
        prop.put("results", "");
        prop.put("cat", cat);
        prop.put("type", type);
        prop.put("depth", "0");
        prop.put("display", display);
        prop.put("constraint", constraint);
        prop.put("searchoptions_display", display);
        prop.put("contentdomCheckText", (contentdom == plasmaSearchQuery.CONTENTDOM_TEXT) ? "1" : "0");
        prop.put("contentdomCheckAudio", (contentdom == plasmaSearchQuery.CONTENTDOM_AUDIO) ? "1" : "0");
        prop.put("contentdomCheckVideo", (contentdom == plasmaSearchQuery.CONTENTDOM_VIDEO) ? "1" : "0");
        prop.put("contentdomCheckImage", (contentdom == plasmaSearchQuery.CONTENTDOM_IMAGE) ? "1" : "0");
        prop.put("contentdomCheckApp", (contentdom == plasmaSearchQuery.CONTENTDOM_APP) ? "1" : "0");
        
        // online caution timing
        sb.localSearchLastAccess = System.currentTimeMillis();
        
        return prop;
    }
}
