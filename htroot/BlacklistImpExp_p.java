// BlacklistImpExp_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Schier
//
// $LastChangedDate: 2008-10-30 01:03:14 +0100 (Do, 30 Okt 2008) $
// $LastChangedRevision: 5309 $
// $LastChangedBy: low012 $
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
// javac -classpath .:../classes Blacklist_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.util.Iterator;
import java.util.TreeMap;

import de.anomic.data.listManager;
import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeed;
import java.util.List;

public class BlacklistImpExp_p {
    private final static String DISABLED         = "disabled_";

    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        // initialize the list manager
        listManager.switchboard = (plasmaSwitchboard) env;
        listManager.listsPath = new File(listManager.switchboard.getRootPath(),listManager.switchboard.getConfig("listManager.listsPath", "DATA/LISTS"));
        
        // loading all blacklist files located in the directory
        final List<String> dirlist = listManager.getDirListing(listManager.listsPath);
        
        String blacklistToUse = null;
        final serverObjects prop = new serverObjects();
        prop.putHTML("blacklistEngine", plasmaSwitchboard.urlBlacklist.getEngineInfo());

        // if we have not chosen a blacklist until yet we use the first file
        if (blacklistToUse == null && dirlist != null && dirlist.size() > 0) {
            blacklistToUse = dirlist.get(0);
        }


        // List known hosts for BlackList retrieval
        if (sb.webIndex.seedDB != null && sb.webIndex.seedDB.sizeConnected() > 0) { // no nullpointer error
            int peerCount = 0;
            try {
                final TreeMap<String, String> hostList = new TreeMap<String, String>();
                final Iterator<yacySeed> e = sb.webIndex.seedDB.seedsConnected(true, false, null, (float) 0.0);
                while (e.hasNext()) {
                    final yacySeed seed = e.next();
                    if (seed != null) hostList.put(seed.get(yacySeed.NAME, "nameless"),seed.hash);
                }

                String peername;
                while ((peername = hostList.firstKey()) != null) {
                    final String Hash = hostList.get(peername);
                    prop.putHTML(DISABLED + "otherHosts_" + peerCount + "_hash", Hash);
                    prop.putXML(DISABLED + "otherHosts_" + peerCount + "_name", peername);
                    hostList.remove(peername);
                    peerCount++;
                }
            } catch (final Exception e) {/* */}
            prop.put(DISABLED + "otherHosts", peerCount);
        }

        
        prop.putXML(DISABLED + "currentBlacklist", (blacklistToUse==null) ? "" : blacklistToUse);
        prop.put("disabled", (blacklistToUse == null) ? "1" : "0");
        return prop;
    }
}
