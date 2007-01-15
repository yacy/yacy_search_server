// SearchStatisticsLocal_p.java
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

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

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
        Iterator i = (page <= 2) ? switchboard.localSearches.entrySet().iterator() : switchboard.remoteSearches.entrySet().iterator() ;
        Map.Entry entry;
        Long trackerHandle;
        HashMap searchProfile;
        StringBuffer a = null;
        while ((entCount < maxCount) && (i.hasNext())) {
            entry = (Map.Entry) i.next();
            trackerHandle = (Long) entry.getKey();
            searchProfile = (HashMap) entry.getValue();
         
            if (page <= 2) {
                Iterator wi = ((Set) searchProfile.get("querywords")).iterator();
                a = new StringBuffer();
                while (wi.hasNext()) a.append((String) wi.next()).append(' ');
            }
            
            // put values in template
            prop.put("page_list_" + entCount + "_dark", ((dark) ? 1 : 0) ); dark =! dark;
            prop.put("page_list_" + entCount + "_host", (String) searchProfile.get("host"));
            prop.put("page_list_" + entCount + "_date", (new Date(trackerHandle.longValue())).toString());
            if (page <= 2) {
                prop.put("page_list_" + entCount + "_querywords", new String(a));
            } else {
                prop.put("page_list_" + entCount + "_queryhashes", plasmaSearchQuery.hashSet2hashString((Set) searchProfile.get("queryhashes")));
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
        prop.put("page_total", (page <= 2) ? switchboard.localSearches.size() : switchboard.remoteSearches.size());
        // return rewrite properties
        return prop;
    }
 
}
