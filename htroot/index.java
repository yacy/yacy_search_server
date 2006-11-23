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
import de.anomic.plasma.plasmaSearchPreOrder;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class index {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        
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
        prop.put("searchoptions_indexofChecked", "");
        prop.put("results", "");
        prop.put("cat", "href");
        prop.put("type", "0");
        prop.put("depth", "0");
        prop.put("display", display);
        prop.put("searchoptions_display", display);
        
        
        return prop;
    }

}
