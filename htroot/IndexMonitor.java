// IndexMonitor.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last change: 09.03.2005
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
// javac -classpath .:../Classes Settings_p.java
// if the shell's current path is HTROOT

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class IndexMonitor {

    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	// return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
	serverObjects prop = new serverObjects();
        
        int showIndexedCount = 40;
        boolean si = false;
        boolean se = false;
        
        
        if (post == null) {
            post = new serverObjects();
            post.put("process", "0");
        }
        
        // find process number
        int process;
        try {
            process = Integer.parseInt(post.get("process", "0"));
        } catch (NumberFormatException e) {
            process = 0;
        }
        
        // check if authorization is needed and/or given
        if (((process > 0) && (process < 6)) ||
            (post.containsKey("clearlist")) ||
            (post.containsKey("deleteentry"))) {
            String authorization = ((String) header.get("Authorization", "xxxxxx")).trim().substring(6);
            if (authorization.length() == 0) {
                // force log-in
                prop.put("AUTHENTICATE", "admin log-in");
                return prop;
            }
            String adminAccountBase64MD5 = switchboard.getConfig("adminAccountBase64MD5", "");
            boolean authenticated = (adminAccountBase64MD5.equals(serverCodings.encodeMD5Hex(authorization)));
            if (!authenticated) {
                // force log-in (again, because wrong password was given)
                prop.put("AUTHENTICATE", "admin log-in");
                return prop;
            }
        }
        
        // custom number of lines
        if (post.containsKey("count")) {
            showIndexedCount = Integer.parseInt(post.get("count", "40"));
        }
        
        // do the commands
        if (post.containsKey("clearlist")) switchboard.urlPool.loadedURL.clearStack(process);
        if (post.containsKey("deleteentry")) {
                String hash = post.get("hash", null);
                if (hash != null) {
                    // delete from database
                    switchboard.urlPool.loadedURL.remove(hash);
                }
            }
        if (post.containsKey("moreIndexed")) {
            showIndexedCount = Integer.parseInt(post.get("showIndexed", "40"));
        }
        if (post.get("si") != null) si = true;
        if (post.get("se") != null) se = true;
        
        // create table
        if (process == 0) {
            prop.put("table", 2);
        } else {
            prop.putAll(switchboard.urlPool.loadedURL.genTableProps(process, showIndexedCount, si, se, "unknown", null, "IndexMonitor.html", true));
        }
        prop.put("process", process);
	// return rewrite properties
	return prop;
    }
    
}
