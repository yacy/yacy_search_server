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
                String popup = post.get("popup", "status");
                if (popup.equals("front")) {
                    sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "index.html?display=2");
                } else if (popup.equals("search")) {
                    sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "yacysearch.html?display=2");
                } else if (popup.equals("interactive")) {
                    sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "yacyinteractive.html?display=2");
                } else {
                    sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "Status.html");
                }
            }
            if (post.containsKey("searchpage_set")) {
                String newGreeting = post.get(SwitchboardConstants.GREETING, "");
                // store this call as api call
                sb.tables.recordAPICall(post, "ConfigPortal.html", WorkTables.TABLE_API_TYPE_CONFIGURATION, "new portal design. greeting: " + newGreeting);
                
                sb.setConfig(SwitchboardConstants.GREETING, newGreeting);
                sb.setConfig(SwitchboardConstants.GREETING_HOMEPAGE, post.get(SwitchboardConstants.GREETING_HOMEPAGE, ""));
                sb.setConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, post.get(SwitchboardConstants.GREETING_LARGE_IMAGE, ""));
                sb.setConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, post.get(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));
            }
            if (post.containsKey("searchpage_default")) {
                sb.setConfig(SwitchboardConstants.GREETING, "P2P Web Search");
                sb.setConfig(SwitchboardConstants.GREETING_HOMEPAGE, "http://yacy.net");
                sb.setConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, "/env/grafics/YaCyLogo_120ppi.png");
                sb.setConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, "/env/grafics/YaCyLogo_60ppi.png");
                sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "Status.html");
            }            
        }

        prop.putHTML(SwitchboardConstants.GREETING, sb.getConfig(SwitchboardConstants.GREETING, ""));
        prop.putHTML(SwitchboardConstants.GREETING_HOMEPAGE, sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
        prop.putHTML(SwitchboardConstants.GREETING_LARGE_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, ""));
        prop.putHTML(SwitchboardConstants.GREETING_SMALL_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));

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
        String myaddress = sb.peers.mySeed().getPublicAddress();
        if (myaddress == null) myaddress = "localhost:" + sb.getConfig("port", "8080");
        prop.put("myaddress", myaddress);
        return prop;
    }

}
