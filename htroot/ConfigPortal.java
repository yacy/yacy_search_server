// ConfigPortal.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 4.7.2008
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
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

import net.yacy.cora.protocol.RequestHeader;
import de.anomic.data.WorkTables;
import de.anomic.http.server.HTTPDFileHandler;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class ConfigPortal {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;
        
        if (post != null) {
            // AUTHENTICATE
            if (!header.containsKey(RequestHeader.AUTHORIZATION)) {
                prop.putHTML("AUTHENTICATE","log-in");
                return prop;
            }
            
            if (post.containsKey("popup")) {
                final String popup = post.get("popup", "status");
                if ("front".equals(popup)) {
                    sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "index.html?display=2");
                } else if ("search".equals(popup)) {
                    sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "yacysearch.html?display=2");
                } else if ("interactive".equals(popup)) {
                    sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "yacyinteractive.html?display=2");
                } else {
                    sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "Status.html");
                }
            }
            if (post.containsKey("searchpage_set")) {
                final String newGreeting = post.get(SwitchboardConstants.GREETING, "");
                // store this call as api call
                sb.tables.recordAPICall(post, "ConfigPortal.html", WorkTables.TABLE_API_TYPE_CONFIGURATION, "new portal design. greeting: " + newGreeting);
                
                sb.setConfig(SwitchboardConstants.GREETING, newGreeting);
                sb.setConfig(SwitchboardConstants.GREETING_HOMEPAGE, post.get(SwitchboardConstants.GREETING_HOMEPAGE, ""));
                sb.setConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, post.get(SwitchboardConstants.GREETING_LARGE_IMAGE, ""));
                sb.setConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, post.get(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));
                sb.setConfig(SwitchboardConstants.SEARCH_TARGET, post.get("target", "_self"));
                sb.setConfig(SwitchboardConstants.INDEX_FORWARD, post.get(SwitchboardConstants.INDEX_FORWARD, ""));
                HTTPDFileHandler.indexForward = post.get(SwitchboardConstants.INDEX_FORWARD, "");
                sb.setConfig("publicTopmenu", post.getBoolean("publicTopmenu", true));
                sb.setConfig("publicSearchpage", post.getBoolean("publicSearchpage", true));
                sb.setConfig("search.options", post.getBoolean("search.options", false));
                sb.setConfig("search.result.show.date", post.getBoolean("search.result.show.date", false));
                sb.setConfig("search.result.show.size", post.getBoolean("search.result.show.size", false));
                sb.setConfig("search.result.show.metadata", post.getBoolean("search.result.show.metadata", false));
                sb.setConfig("search.result.show.parser", post.getBoolean("search.result.show.parser", false));
                sb.setConfig("search.result.show.pictures", post.getBoolean("search.result.show.pictures", false));
                sb.setConfig("search.verify", post.get("search.verify", "ifexist"));
                sb.setConfig("search.verify.delete", post.getBoolean("search.verify.delete", false));
                // construct navigation String
                String nav = "";
                if (post.getBoolean("search.navigation.hosts", false)) nav += "hosts,";
                if (post.getBoolean("search.navigation.authors", false)) nav += "authors,";
                if (post.getBoolean("search.navigation.namespace", false)) nav += "namespace,";
                if (post.getBoolean("search.navigation.topics", false)) nav += "topics,";
                if (nav.endsWith(",")) nav = nav.substring(0, nav.length() - 1);
                 sb.setConfig("search.navigation", nav);
            }
            if (post.containsKey("searchpage_default")) {
                sb.setConfig(SwitchboardConstants.GREETING, "P2P Web Search");
                sb.setConfig(SwitchboardConstants.GREETING_HOMEPAGE, "http://yacy.net");
                sb.setConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, "/env/grafics/YaCyLogo_120ppi.png");
                sb.setConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, "/env/grafics/YaCyLogo_60ppi.png");
                sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "Status.html");
                sb.setConfig(SwitchboardConstants.INDEX_FORWARD, "");
                HTTPDFileHandler.indexForward = "";
                sb.setConfig(SwitchboardConstants.SEARCH_TARGET, "_self");
                sb.setConfig("publicTopmenu", true);
                sb.setConfig("publicSearchpage", true);
                sb.setConfig("search.navigation", "hosts,authors,namespace,topics");
                sb.setConfig("search.options", true);
                sb.setConfig("search.result.show.date", true);
                sb.setConfig("search.result.show.size", true);
                sb.setConfig("search.result.show.metadata", true);
                sb.setConfig("search.result.show.parser", true);
                sb.setConfig("search.result.show.pictures", true);
                sb.setConfig("search.verify", "iffresh");
                sb.setConfig("search.verify.delete", "true");
            }            
        }

        prop.putHTML(SwitchboardConstants.GREETING, sb.getConfig(SwitchboardConstants.GREETING, ""));
        prop.putHTML(SwitchboardConstants.GREETING_HOMEPAGE, sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
        prop.putHTML(SwitchboardConstants.GREETING_LARGE_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, ""));
        prop.putHTML(SwitchboardConstants.GREETING_SMALL_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));
        prop.putHTML(SwitchboardConstants.INDEX_FORWARD, sb.getConfig(SwitchboardConstants.INDEX_FORWARD, ""));
        prop.put("publicTopmenu", sb.getConfigBool("publicTopmenu", false) ? 1 : 0);
        prop.put("publicSearchpage", sb.getConfigBool("publicSearchpage", false) ? 1 : 0);
        prop.put("search.options", sb.getConfigBool("search.options", false) ? 1 : 0);
        
        prop.put("search.result.show.date", sb.getConfigBool("search.result.show.date", false) ? 1 : 0);
        prop.put("search.result.show.size", sb.getConfigBool("search.result.show.size", false) ? 1 : 0);
        prop.put("search.result.show.metadata", sb.getConfigBool("search.result.show.metadata", false) ? 1 : 0);
        prop.put("search.result.show.parser", sb.getConfigBool("search.result.show.parser", false) ? 1 : 0);
        prop.put("search.result.show.pictures", sb.getConfigBool("search.result.show.pictures", false) ? 1 : 0);

        prop.put("search.navigation.hosts", sb.getConfig("search.navigation", "").indexOf("hosts") >= 0 ? 1 : 0);
        prop.put("search.navigation.authors", sb.getConfig("search.navigation", "").indexOf("authors") >= 0 ? 1 : 0);
        prop.put("search.navigation.namespace", sb.getConfig("search.navigation", "").indexOf("namespace") >= 0 ? 1 : 0);
        prop.put("search.navigation.topics", sb.getConfig("search.navigation", "").indexOf("topics") >= 0 ? 1 : 0);

        prop.put("search.verify.nocache", sb.getConfig("search.verify", "").equals("nocache") ? 1 : 0);
        prop.put("search.verify.iffresh", sb.getConfig("search.verify", "").equals("iffresh") ? 1 : 0);
        prop.put("search.verify.ifexist", sb.getConfig("search.verify", "").equals("ifexist") ? 1 : 0);
        prop.put("search.verify.cacheonly", sb.getConfig("search.verify", "").equals("cacheonly") ? 1 : 0);
        prop.put("search.verify.false", sb.getConfig("search.verify", "").equals("false") ? 1 : 0);
        prop.put("search.verify.delete", sb.getConfigBool("search.verify.delete", true) ? 1 : 0);

        final String  browserPopUpPage = sb.getConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "ConfigBasic.html");
        prop.put("popupFront", 0);
        prop.put("popupSearch", 0);
        prop.put("popupInteractive", 0);
        prop.put("popupStatus", 0);
        if (browserPopUpPage.startsWith("index")) {
            prop.put("popupFront", 1);
        } else if (browserPopUpPage.startsWith("yacysearch")) {
            prop.put("popupSearch", 1);
        } else if (browserPopUpPage.startsWith("yacyinteractive")) {
            prop.put("popupInteractive", 1);
        } else {
            prop.put("popupStatus", 1);
        }
        
        final String target = sb.getConfig(SwitchboardConstants.SEARCH_TARGET, "_self");
        prop.put("selected_blank", "_blank".equals(target) ? 1 : 0);
        prop.put("selected_self", "_self".equals(target) ? 1 : 0);
        prop.put("selected_parent", "_parent".equals(target) ? 1 : 0);
        prop.put("selected_top", "_top".equals(target) ? 1 : 0);
        prop.put("selected_searchresult", "searchresult".equals(target) ? 1 : 0);
                
        String myaddress = sb.peers.mySeed().getPublicAddress();
        if (myaddress == null) {
            myaddress = "localhost:" + sb.getConfig("port", "8090");
        }
        prop.put("myaddress", myaddress);
        return prop;
    }

}
