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

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSearchPreOrder;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class index {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;

        boolean global = (post == null) ? true : post.get("resource", "global").equals("global");
        final boolean indexDistributeGranted = sb.getConfig("allowDistributeIndex", "true").equals("true");
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

        // we create empty entries for template strings
        final serverObjects prop = new serverObjects();
        prop.put("promoteSearchPageGreeting", env.getConfig("promoteSearchPageGreeting", ""));
        prop.put("former", "");
        prop.put("num-results", 0);
        prop.put("excluded", 0);
        prop.put("combine", 0);
        prop.put("resultbottomline", 0);
        prop.put("count-10", 1);
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

}
