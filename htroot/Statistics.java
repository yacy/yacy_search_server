// Statistics.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last major change: 16.02.2005
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

// You must compile this file with
// javac -classpath .:../classes Network.java
// if the shell's current path is HTROOT

import java.net.MalformedURLException;
import java.util.HashMap;

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroMapObjects;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyURL;

public class Statistics {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> sb) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) sb;
        serverObjects prop = new serverObjects();
        if (null == switchboard.facilityDB) 
            return prop;

        int page = (post == null) ? 0 : post.getInt("page", 0);
        
        prop.put("page", page);
        switch (page) {
            case 0:
                if (switchboard.facilityDB.size("backlinks") == 0) {
                    prop.put("page_backlinks", "0");
                } else {
                    prop.put("page_backlinks", "1");
                    kelondroMapObjects.mapIterator it = switchboard.facilityDB.maps("backlinks", false, "date");
                    int count = 0;
                    int maxCount = 100;
                    boolean dark = true;
                    HashMap<String, String> map;
                    String urlString;
                    yacyURL url;
                    while ((it.hasNext()) && (count < maxCount)) {
                        map = it.next();
                        if (count >= maxCount) break;
                        urlString = (String) map.get("key");
                        try { url = new yacyURL(urlString, null); } catch (MalformedURLException e) { url = null; }
                        if ((url != null) && (!url.isLocal())) {
                            prop.put("page_backlinks_list_" + count + "_dark", dark ? "1" : "0");
                            dark =! dark;
                            prop.put("page_backlinks_list_" + count + "_url", urlString);
                            prop.put("page_backlinks_list_" + count + "_date", map.get("date"));
                            prop.put("page_backlinks_list_" + count + "_clientip", map.get("clientip"));
                            prop.put("page_backlinks_list_" + count + "_useragent", map.get("useragent"));
                            count++;
                        }
                    }//while
                    prop.putNum("page_backlinks_list", count);
                    prop.putNum("page_backlinks_num", count);
                    prop.putNum("page_backlinks_total", switchboard.facilityDB.size("backlinks"));
                }
                break;
        }
        // return rewrite properties
        return prop;
    }
}
