// SearchStatistics_p.java
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

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;

public class SearchStatistics_p {
 
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch sb) {
        plasmaSwitchboard switchboard = (plasmaSwitchboard) sb;
     
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();
        int page = 0;
        if (post != null) page = post.getInt("page", 0);
        prop.put("page", page);
     
        int maxCount = 100;
        int entCount = 0;
        boolean dark = true;
        Map.Entry entry;
        if ((page == 1) || (page == 3)) {
            Iterator i = (page == 1) ? switchboard.localSearches.entrySet().iterator() : switchboard.remoteSearches.entrySet().iterator();
            Long trackerHandle;
            HashMap searchProfile;
            StringBuffer a = null;
            while ((entCount < maxCount) && (i.hasNext())) {
                entry = (Map.Entry) i.next();
                trackerHandle = (Long) entry.getKey();
                searchProfile = (HashMap) entry.getValue();
         
                if (page == 1) {
                    Iterator wi = ((Set) searchProfile.get("querywords")).iterator();
                    a = new StringBuffer();
                    while (wi.hasNext()) a.append((String) wi.next()).append(' ');
                }
            
                // put values in template
                prop.put("page_list_" + entCount + "_dark", ((dark) ? 1 : 0) ); dark =! dark;
                prop.put("page_list_" + entCount + "_host", (String) searchProfile.get("host"));
                prop.put("page_list_" + entCount + "_date", yacyCore.universalDateShortString(new Date(trackerHandle.longValue())));
                if (page == 1) {
                    // local search
                    prop.put("page_list_" + entCount + "_offset", ((Integer) searchProfile.get("offset")).toString());
                    prop.put("page_list_" + entCount + "_querywords", new String(a));
                } else {
                    // remote search
                    prop.put("page_list_" + entCount + "_peername", (String) searchProfile.get("peername"));
                    prop.put("page_list_" + entCount + "_queryhashes", plasmaSearchQuery.anonymizedQueryHashes((Set) searchProfile.get("queryhashes")));
                }
                prop.put("page_list_" + entCount + "_querycount", ((Integer) searchProfile.get("querycount")).toString());
                prop.put("page_list_" + entCount + "_querytime", ((Long) searchProfile.get("querytime")).toString());
                prop.put("page_list_" + entCount + "_resultcount", ((Integer) searchProfile.get("resultcount")).toString());
                prop.put("page_list_" + entCount + "_resulttime", ((Long) searchProfile.get("resulttime")).toString());

                // next
                entCount++;
            }
            prop.put("page_list", entCount);
            prop.put("page_num", entCount);
            prop.put("page_total", (page == 1) ? switchboard.localSearches.size() : switchboard.remoteSearches.size());
        }
        if ((page == 2) || (page == 4)) {
            Iterator i = (page == 2) ? switchboard.localSearchTracker.entrySet().iterator() : switchboard.remoteSearchTracker.entrySet().iterator();
            String host, handlestring;
            TreeSet handles;
            while ((entCount < maxCount) && (i.hasNext())) {
                entry = (Map.Entry) i.next();
                host = (String) entry.getKey();
                handles = (TreeSet) entry.getValue();
                handlestring = "";
                Iterator ii = handles.iterator();
                while (ii.hasNext()) {
                    handlestring += yacyCore.universalDateShortString(new Date(((Long) ii.next()).longValue())) + " ";
                }
                prop.put("page_list_" + entCount + "_dark", ((dark) ? 1 : 0) ); dark =! dark;
                prop.put("page_list_" + entCount + "_host", host);
                prop.put("page_list_" + entCount + "_count", new Integer(handles.size()).toString());
                prop.put("page_list_" + entCount + "_dates", handlestring);
                // next
                entCount++;
            }
            prop.put("page_list", entCount);
            prop.put("page_num", entCount);
            prop.put("page_total", (page == 2) ? switchboard.localSearches.size() : switchboard.remoteSearches.size());
        }
        // return rewrite properties
        return prop;
    }
 
}
