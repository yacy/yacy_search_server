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


import de.anomic.http.server.RequestHeader;
import de.anomic.search.ContentDomain;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class index {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        // access control
        boolean publicPage = sb.getConfigBool("publicSearchpage", true);
        final boolean authorizedAccess = sb.verifyAuthentication(header, false);
        if ((post != null) && (post.containsKey("publicPage"))) {
            if (!authorizedAccess) {
                prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                return prop;
            }
            publicPage = post.get("publicPage", "0").equals("1");
            sb.setConfig("publicSearchpage", publicPage);
        }
        
        final boolean global = (post == null) ? true : post.get("resource", "global").equals("global");
        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        int display = (post == null) ? 0 : post.getInt("display", 0);
        if ((display == 1) && (!authenticated)) display = 0;
        final boolean browserPopUpTrigger = sb.getConfig(SwitchboardConstants.BROWSER_POP_UP_TRIGGER, "true").equals("true");
        if (browserPopUpTrigger) {
            final String  browserPopUpPage = sb.getConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "ConfigBasic.html");
            if (browserPopUpPage.startsWith("index") || browserPopUpPage.startsWith("yacysearch")) display = 2;
        }

        final int searchoptions = (post == null) ? 0 : post.getInt("searchoptions", 0);
        final String former = (post == null) ? "" : post.get("former", "");
        final int count = Math.min(100, (post == null) ? 10 : post.getInt("count", 10));
        final int maximumRecords = Integer.parseInt((sb.getConfig(SwitchboardConstants.DEFAULT_SEARCHITEMS, "10")));
        final String urlmaskfilter = (post == null) ? ".*" : post.get("urlmaskfilter", ".*");
        final String prefermaskfilter = (post == null) ? "" : post.get("prefermaskfilter", "");
        final String constraint = (post == null) ? "" : post.get("constraint", "");
        final String cat = (post == null) ? "href" : post.get("cat", "href");
        final int type = (post == null) ? 0 : post.getInt("type", 0);
        
        final boolean indexDistributeGranted = sb.getConfigBool(SwitchboardConstants.INDEX_DIST_ALLOW, true);
        final boolean indexReceiveGranted = sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW, true) ||
        									sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_AUTODISABLED, true);
        //global = global && indexDistributeGranted && indexReceiveGranted;
        
        // search domain
        ContentDomain contentdom = ContentDomain.TEXT;
        final String cds = (post == null) ? "text" : post.get("contentdom", "text");
        if (cds.equals("text")) contentdom = ContentDomain.TEXT;
        if (cds.equals("audio")) contentdom = ContentDomain.AUDIO;
        if (cds.equals("video")) contentdom = ContentDomain.VIDEO;
        if (cds.equals("image")) contentdom = ContentDomain.IMAGE;
        if (cds.equals("app")) contentdom = ContentDomain.APP;
        
        // we create empty entries for template strings
        String promoteSearchPageGreeting = env.getConfig(SwitchboardConstants.GREETING, "");
        if (env.getConfigBool(SwitchboardConstants.GREETING_NETWORK_NAME, false)) promoteSearchPageGreeting = env.getConfig("network.unit.description", "");
        prop.putHTML(SwitchboardConstants.GREETING, promoteSearchPageGreeting);
        prop.put(SwitchboardConstants.GREETING_HOMEPAGE, sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
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
        prop.put("searchoptions_resource-global", global ? "1" : "0");
        prop.put("searchoptions_resource-global-disabled", (indexReceiveGranted && indexDistributeGranted) ? "0" : "1");
        prop.put("searchoptions_resource-global-disabled_reason", (indexReceiveGranted) ? "0" : (indexDistributeGranted ? "1" : "2"));
        prop.put("searchoptions_resource-local", global ? "0" : "1");
        prop.put("searchoptions_urlmaskoptions", "0");
        prop.putHTML("searchoptions_urlmaskoptions_urlmaskfilter", urlmaskfilter);
        prop.put("searchoptions_prefermaskoptions", "0");
        prop.putHTML("searchoptions_prefermaskoptions_prefermaskfilter", prefermaskfilter);
        prop.put("searchoptions_indexofChecked", "");
        prop.put("searchoptions_publicSearchpage", (publicPage) ? "0" : "1");
        prop.put("results", "");
        prop.putHTML("cat", cat);
        prop.put("type", type);
        prop.put("depth", "0");
        prop.put("display", display);
        prop.putHTML("constraint", constraint);
        prop.put("searchoptions_display", display);
        prop.put("searchtext", sb.getConfigBool("search.text", true) ? 1 : 0);
        prop.put("searchaudio", sb.getConfigBool("search.audio", true) ? 1 : 0);
        prop.put("searchvideo", sb.getConfigBool("search.video", true) ? 1 : 0);
        prop.put("searchimage", sb.getConfigBool("search.image", true) ? 1 : 0);
        prop.put("searchapp", sb.getConfigBool("search.app", true) ? 1 : 0);
        prop.put("searchtext_check", (contentdom == ContentDomain.TEXT) ? "1" : "0");
        prop.put("searchaudio_check", (contentdom == ContentDomain.AUDIO) ? "1" : "0");
        prop.put("searchvideo_check", (contentdom == ContentDomain.VIDEO) ? "1" : "0");
        prop.put("searchimage_check", (contentdom == ContentDomain.IMAGE) ? "1" : "0");
        prop.put("searchapp_check", (contentdom == ContentDomain.APP) ? "1" : "0");
        // online caution timing
        sb.localSearchLastAccess = System.currentTimeMillis();
        
        return prop;
    }
}
