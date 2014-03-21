// index.java
// (C) 2004-2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
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

import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class index {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        final String forwardTarget = sb.getConfig(SwitchboardConstants.INDEX_FORWARD, "");
        if (forwardTarget.length() > 0) {
            // forward the page
            prop.put("forward", 1);
            prop.put("forward_target", forwardTarget);
            return prop;
        }

        // access control
        final boolean authorizedAccess = sb.verifyAuthentication(header);
        if ((post != null) && (post.containsKey("publicPage"))) {
            if (!authorizedAccess) {
            	prop.authenticationRequired();
                return prop;
            }
        }
        
        boolean global = (post == null) ? true : post.get("resource", "global").equals("global");
        final boolean focus  = (post == null) ? true : post.get("focus", "1").equals("1");

        int searchoptions = (post == null) ? 0 : Math.min(1, post.getInt("searchoptions", 0));
        if (!sb.getConfigBool("search.options", true)) {
            searchoptions = 0;
        } else { // show heuristic hint on search option screen
            prop.put("searchoptions_heuristic", sb.getConfigBool(SwitchboardConstants.HEURISTIC_OPENSEARCH, false));
        }
        final String former = (post == null) ? "" : post.get("former", "");
        final int count = Math.min(100, (post == null) ? 10 : post.getInt("count", 10));
        final int maximumRecords = sb.getConfigInt(SwitchboardConstants.SEARCH_ITEMS, 10);
        final String prefermaskfilter = (post == null) ? "" : post.get("prefermaskfilter", "");
        final String constraint = (post == null) ? "" : post.get("constraint", "");
        final String cat = (post == null) ? "href" : post.get("cat", "href");
        final int type = (post == null) ? 0 : post.getInt("type", 0);

        //final boolean indexDistributeGranted = sb.getConfigBool(SwitchboardConstants.INDEX_DIST_ALLOW, true);
        final boolean indexReceiveGranted = sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW_SEARCH, true);
        global = global && indexReceiveGranted;

        // search domain
        Classification.ContentDomain contentdom = ContentDomain.TEXT;
        final String cds = (post == null) ? "text" : post.get("contentdom", "text");
        if (cds.equals("text")) contentdom = ContentDomain.TEXT;
        if (cds.equals("audio")) contentdom = ContentDomain.AUDIO;
        if (cds.equals("video")) contentdom = ContentDomain.VIDEO;
        if (cds.equals("image")) contentdom = ContentDomain.IMAGE;
        if (cds.equals("app")) contentdom = ContentDomain.APP;

        // we create empty entries for template strings
        String promoteSearchPageGreeting = env.getConfig(SwitchboardConstants.GREETING, "");
        if (env.getConfigBool(SwitchboardConstants.GREETING_NETWORK_NAME, false)) promoteSearchPageGreeting = env.getConfig("network.unit.description", "");
        prop.put(SwitchboardConstants.GREETING, promoteSearchPageGreeting);
        prop.put(SwitchboardConstants.GREETING_HOMEPAGE, sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
        prop.put("topmenu_" + SwitchboardConstants.GREETING_HOMEPAGE, sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
        prop.put("topmenu_" + SwitchboardConstants.GREETING_SMALL_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));
        prop.put(SwitchboardConstants.GREETING_LARGE_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, ""));
        prop.putHTML("former", former);
        prop.put("num-results", "0");
        prop.put("excluded", "0");
        prop.put("combine", "0");
        prop.put("resultbottomline", "0");
        prop.put("searchoptions", searchoptions);
        prop.put("searchoptions_maximumRecords", maximumRecords);
        prop.put("searchoptions_count-10", (count == 10) ? "1" : "0");
        prop.put("searchoptions_count-50", (count == 50) ? "1" : "0");
        prop.put("searchoptions_count-100", (count == 100) ? "1" : "0");
        prop.put("searchoptions_resource-select", (sb.peers == null || sb.peers.sizeConnected() == 0 || !indexReceiveGranted) ?  0 : global ? 1 : 2);
        prop.put("searchoptions_prefermaskoptions", "0");
        prop.putHTML("searchoptions_prefermaskoptions_prefermaskfilter", prefermaskfilter);
        prop.put("searchoptions_indexofChecked", "");
        prop.put("results", "");
        prop.putHTML("cat", cat);
        prop.put("type", type);
        prop.put("depth", "0");
        prop.put("topmenu", sb.getConfigBool("publicTopmenu", true) ? 1 : 0);
        prop.put("focus", focus ? 1 : 0);
        prop.putHTML("constraint", constraint);
        prop.put("searchdomswitches", sb.getConfigBool("search.text", true) || sb.getConfigBool("search.audio", true) || sb.getConfigBool("search.video", true) || sb.getConfigBool("search.image", true) || sb.getConfigBool("search.app", true) ? 1 : 0);
        prop.put("searchdomswitches_searchoptions", searchoptions);
        prop.put("searchdomswitches_searchtext", sb.getConfigBool("search.text", true) ? 1 : 0);
        prop.put("searchdomswitches_searchaudio", sb.getConfigBool("search.audio", true) ? 1 : 0);
        prop.put("searchdomswitches_searchvideo", sb.getConfigBool("search.video", true) ? 1 : 0);
        prop.put("searchdomswitches_searchimage", sb.getConfigBool("search.image", true) ? 1 : 0);
        prop.put("searchdomswitches_searchapp", sb.getConfigBool("search.app", true) ? 1 : 0);
        prop.put("searchdomswitches_searchtext_check", (contentdom == ContentDomain.TEXT) ? "1" : "0");
        prop.put("searchdomswitches_searchaudio_check", (contentdom == ContentDomain.AUDIO) ? "1" : "0");
        prop.put("searchdomswitches_searchvideo_check", (contentdom == ContentDomain.VIDEO) ? "1" : "0");
        prop.put("searchdomswitches_searchimage_check", (contentdom == ContentDomain.IMAGE) ? "1" : "0");
        prop.put("searchdomswitches_searchapp_check", (contentdom == ContentDomain.APP) ? "1" : "0");
        prop.put("search.navigation", sb.getConfig("search.navigation", "all") );
        prop.put("search.verify", sb.getConfig("search.verify", "iffresh") );
        // online caution timing
        sb.localSearchLastAccess = System.currentTimeMillis();

        return prop;
    }
}
