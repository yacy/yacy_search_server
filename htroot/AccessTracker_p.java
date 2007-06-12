// AccessStatistics_p.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 14.01.2007 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.http.httpHeader;
import de.anomic.net.natLib;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverTrack;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class AccessTracker_p {
 
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch sb) {
        plasmaSwitchboard switchboard = (plasmaSwitchboard) sb;
     
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();
        int page = 0;
        if (post != null) page = post.getInt("page", 0);
        prop.put("page", page);
     
        int maxCount = 1000;
        boolean dark = true;
        if (page == 0) {
            Iterator i = switchboard.accessHosts();
            String host;
            ArrayList access;
            int entCount = 0;
            serverTrack track;
            while ((entCount < maxCount) && (i.hasNext())) {
                host = (String) i.next();
                access = switchboard.accessTrack(host);
                
                trackl: for (int j = access.size() - 1; j >= 0; j--) {
                    track = (serverTrack) access.get(j);
                    if (track == null) continue trackl; 
                    prop.put("page_list_" + entCount + "_host", host);
                    prop.put("page_list_" + entCount + "_date", yacyCore.universalDateShortString(new Date(track.time)));
                    prop.put("page_list_" + entCount + "_path", track.path);
                    entCount++;
                }
            }
            prop.put("page_list", entCount);
            prop.put("page_num", entCount);
        }
        if ((page == 2) || (page == 4)) {
            ArrayList array = (page == 2) ? switchboard.localSearches : switchboard.remoteSearches;
            Long trackerHandle;
            HashMap searchProfile;
            int m = Math.min(maxCount, array.size());
            for (int entCount = 0; entCount < m; entCount++) {
                searchProfile = (HashMap) array.get(array.size() - entCount - 1);
                trackerHandle = (Long) searchProfile.get("time");
            
                // put values in template
                prop.put("page_list_" + entCount + "_dark", ((dark) ? 1 : 0) ); dark =! dark;
                prop.put("page_list_" + entCount + "_host", (String) searchProfile.get("host"));
                prop.put("page_list_" + entCount + "_date", yacyCore.universalDateShortString(new Date(trackerHandle.longValue())));
                prop.put("page_list_" + entCount + "_timestamp", Long.toString(trackerHandle.longValue()));
                if (page == 2) {
                    // local search
                    prop.put("page_list_" + entCount + "_offset", ((Integer) searchProfile.get("offset")).toString());
                    prop.put("page_list_" + entCount + "_querystring", searchProfile.get("querystring"));
                } else {
                    // remote search
                    prop.put("page_list_" + entCount + "_peername", (String) searchProfile.get("peername"));
                    prop.put("page_list_" + entCount + "_queryhashes", plasmaSearchQuery.anonymizedQueryHashes((Set) searchProfile.get("queryhashes")));
                }
                prop.put("page_list_" + entCount + "_querycount", ((Integer) searchProfile.get("querycount")).toString());
                prop.put("page_list_" + entCount + "_querytime", ((Long) searchProfile.get("querytime")).toString());
                prop.put("page_list_" + entCount + "_resultcount", ((Integer) searchProfile.get("resultcount")).toString());
                prop.put("page_list_" + entCount + "_resulttime", ((Long) searchProfile.get("resulttime")).toString());
            }
            prop.put("page_list", m);
            prop.put("page_num", m);
            prop.put("page_total", (page == 2) ? switchboard.localSearches.size() : switchboard.remoteSearches.size());
        }
        if ((page == 3) || (page == 5)) {
            Iterator i = (page == 3) ? switchboard.localSearchTracker.entrySet().iterator() : switchboard.remoteSearchTracker.entrySet().iterator();
            String host;
            TreeSet handles;
            int entCount = 0;
            Map.Entry entry;
            while ((entCount < maxCount) && (i.hasNext())) {
                entry = (Map.Entry) i.next();
                host = (String) entry.getKey();
                handles = (TreeSet) entry.getValue();
                
                int dateCount = 0;
                Iterator ii = handles.iterator();
                while (ii.hasNext()) {
                	Long timestamp = (Long) ii.next();
                	prop.put("page_list_" + entCount + "_dates_" + dateCount + "_date", yacyCore.universalDateShortString(new Date(timestamp.longValue())));
                	prop.put("page_list_" + entCount + "_dates_" + dateCount + "_timestamp", timestamp.toString());
                	dateCount++;
                }
                prop.put("page_list_" + entCount + "_dates", dateCount);
                int qph = handles.tailSet(new Long(System.currentTimeMillis() - 1000 * 60 * 60)).size();
                prop.put("page_list_" + entCount + "_qph", qph);
                
                prop.put("page_list_" + entCount + "_dark", ((dark) ? 1 : 0) ); dark =! dark;
                prop.put("page_list_" + entCount + "_host", host);
                if (page == 5) {
                    yacySeed remotepeer = yacyCore.seedDB.lookupByIP(natLib.getInetAddress(host), true, true, true);
                    prop.put("page_list_" + entCount + "_peername", (remotepeer == null) ? "UNKNOWN" : remotepeer.getName());
                }
                prop.put("page_list_" + entCount + "_count", new Integer(handles.size()).toString());

                // next
                entCount++;
            }
            prop.put("page_list", entCount);
            prop.put("page_num", entCount);
            prop.put("page_total", (page == 3) ? switchboard.localSearches.size() : switchboard.remoteSearches.size());
        }
        // return rewrite properties
        return prop;
    }
 
}
