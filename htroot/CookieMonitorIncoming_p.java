// CookieMonitorIncoming_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last change: 25.02.2005
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

// you must compile this file with
// javac -classpath .:../classes Network.java
// if the shell's current path is HTROOT

import java.util.*;
import de.anomic.tools.*;
import de.anomic.server.*;
import de.anomic.http.*;
import de.anomic.yacy.*;
import de.anomic.plasma.*;

public class CookieMonitorIncoming_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch sb) {
        plasmaSwitchboard switchboard = (plasmaSwitchboard) sb;
        
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();
        
        int maxCount = 100;
        int entCount = 0;
        boolean dark = true;
        Iterator i = switchboard.incomingCookies.entrySet().iterator();
        Map.Entry entry;
        String host, client;
        Object[] cookies;
        String ucl;
        Date date;
        Object[] oa;
        while ((entCount < maxCount) && (i.hasNext())) {
            // get out values
            entry = (Map.Entry) i.next();
            host = (String) entry.getKey();
            oa = (Object[]) entry.getValue();
            date = (Date) oa[0];
            client = (String) oa[1];
            cookies = (Object[]) oa[2];
            ucl = "<ul>";
            for (int j = 0; j < cookies.length; j++) ucl = ucl + "<li>" + ((String) cookies[j]) + "</li>";
            ucl = ucl + "</ul>";
            
            // put values in template
            prop.put("list_" + entCount + "_dark", ((dark) ? 1 : 0) ); dark =! dark;
            prop.put("list_" + entCount + "_host", host);
            prop.put("list_" + entCount + "_date", httpc.dateString(date));
            prop.put("list_" + entCount + "_client", client);
            prop.put("list_" + entCount + "_cookie", ucl);
            
            // next
            entCount++;
        }
        prop.put("list", entCount);
        prop.put("num", entCount);
        prop.put("total", switchboard.incomingCookies.size());
        // return rewrite properties
        return prop;
    }
    
}
