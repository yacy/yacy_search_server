// ProxyIndexingMonitor_p.java 
// ---------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last change: 02.05.2004
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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;

public class ProxyIndexingMonitor_p {

    private static SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
    private static String daydate(Date date) {
	if (date == null) return ""; else return dayFormatter.format(date);
    }
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	// return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
	serverObjects prop = new serverObjects();
        
        int showIndexedCount = 20;
        boolean se = false;
        
        prop.put("info", 0);
        prop.put("info_message", "");
                    
        if (post != null) {
            if (post.containsKey("clearlist4")) switchboard.urlPool.loadedURL.clearStack(4); // local: by proxy crawl
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
            
            if (post.get("se") != null) se = true;

            if (post.containsKey("proxyprofileset")) try {
                // read values and put them in global settings
                int newProxyPrefetchDepth = Integer.parseInt((String) post.get("proxyPrefetchDepth", "0"));
                env.setConfig("proxyPrefetchDepth", "" + newProxyPrefetchDepth);
                boolean proxyStoreHTCache = ((String) post.get("proxyStoreHTCache", "")).equals("on");
                env.setConfig("proxyStoreHTCache", (proxyStoreHTCache) ? "true" : "false");

                // implant these settings also into the crawling profile for the proxy
                plasmaCrawlProfile.entry profile = switchboard.profiles.getEntry(switchboard.getConfig("defaultProxyProfile", ""));
                if (profile == null) {
		    prop.put("info", 1);//delete DATA/PLASMADB/crawlProfiles0.db
                } else {
                    try {
			profile.changeEntry("generalDepth", "" + newProxyPrefetchDepth);
                        profile.changeEntry("storeHTCache", (proxyStoreHTCache) ? "true": "false");
			prop.put("info", 2);//new proxyPrefetchdepth
			prop.put("info_message", newProxyPrefetchDepth);
                        prop.put("info_caching", (proxyStoreHTCache) ? 1 : 0);
                    } catch (IOException e) {
			prop.put("info", 3); //Error: errmsg
                    	prop.put("info_error", e.getMessage());
                    }
                }
 
            } catch (Exception e) {
                prop.put("info", 2); //Error: errmsg
                prop.put("info_error", e.getMessage());
                System.out.println("Case3");
                e.printStackTrace();
            }
        }
         
        // create tables
        String myname = yacyCore.seedDB.mySeed.getName();
        prop.putAll(switchboard.urlPool.loadedURL.genTableProps(4, showIndexedCount, false, false, "proxy", null, "ProxyIndexingMonitor_p.html", true));
        
        prop.put("proxyPrefetchDepth", env.getConfig("proxyPrefetchDepth", "0"));
        prop.put("proxyStoreHTCacheChecked", env.getConfig("proxyStoreHTCache", "").equals("true") ? 1 : 0);
        // return rewrite properties
	return prop;
    }

}
