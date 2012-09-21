// CookieMonitorOutgoing_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
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

// You must compile this file with
// javac -classpath .:../classes Network.java
// if the shell's current path is HTROOT

import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class CookieMonitorOutgoing_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch sb) {
        final Switchboard switchboard = (Switchboard) sb;

        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();

	// handle on/off button
        if(post != null) {
            if(post.containsKey("enableCookieMonitoring")) {
                switchboard.setConfig("proxy.monitorCookies", true);
            } else if (post.containsKey("disableCookieMonitoring")) {
                switchboard.setConfig("proxy.monitorCookies", false);
		switchboard.incomingCookies.clear();
		switchboard.outgoingCookies.clear();
            }
        }
        prop.put("monitorCookies.on", switchboard.getConfigBool("proxy.monitorCookies", false) ? "1":"0");
        prop.put("monitorCookies.off", !switchboard.getConfigBool("proxy.monitorCookies", false) ? "1":"0");

        final int maxCount = 100;
        int entCount = 0;
        int tmpCount = 0;
        boolean dark = true;
        final Iterator<Map.Entry<String, Object[]>> i = switchboard.outgoingCookies.entrySet().iterator();
        Map.Entry<String, Object[]> entry;
        String host, client;
        Object[] cookies;
        Date date;
        Object[] oa;
        while ((entCount < maxCount) && (i.hasNext())) {
            // get out values
            entry = i.next();
            host = entry.getKey();
            oa = entry.getValue();
            date = (Date) oa[0];
            client = (String) oa[1];
            cookies = (Object[]) oa[2];

            // put values in template
            prop.put("list_" + entCount + "_dark", dark ? "1" : "0" );
            dark =! dark;
            prop.put("list_" + entCount + "_host", host);
            prop.put("list_" + entCount + "_date", HeaderFramework.formatRFC1123(date));
            prop.put("list_" + entCount + "_client", client);
            while (tmpCount < cookies.length){
                prop.putHTML("list_" + entCount + "_cookies_" + tmpCount + "_item", ((String) cookies[tmpCount]));
                tmpCount++;
            }
            prop.put("list_" + entCount + "_cookies", tmpCount);
            tmpCount = 0;

            // next
            entCount++;
        }
        prop.put("list", entCount);
        prop.put("num", entCount);
        prop.put("total", switchboard.outgoingCookies.size());
        // return rewrite properties
        return prop;
    }

}
