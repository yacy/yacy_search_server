// CookieMonitorOutgoing_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last change: 25.05.2007
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

import de.anomic.http.HttpClient;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class CookieMonitorOutgoing_p {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> sb) {
        plasmaSwitchboard switchboard = (plasmaSwitchboard) sb;

        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();

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

        int maxCount = 100;
        int entCount = 0;
        int tmpCount = 0;
        boolean dark = true;
        Iterator<Map.Entry<String, Object[]>> i = switchboard.outgoingCookies.entrySet().iterator();
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
            prop.put("list_" + entCount + "_date", HttpClient.dateString(date));
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
