// AccessTracker_p.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.01.2007 on http://www.yacy.net
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
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.http.httpHeader;
import de.anomic.net.natLib;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class AccessTracker_p {
    
	private static final TreeMap treemapclone(TreeMap m) {
		TreeMap accessClone = new TreeMap();
		try {
			accessClone.putAll(m);
		} catch (ConcurrentModificationException e) {}
		return accessClone;
	}
	
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch sb) {
        plasmaSwitchboard switchboard = (plasmaSwitchboard) sb;
     
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();
        prop.setLocalized(!((String)header.get("PATH")).endsWith(".xml"));
        int page = 0;
        if (post != null) page = post.getInt("page", 0);
        prop.put("page", page);
     
        int maxCount = 1000;
        boolean dark = true;
        if (page == 0) {
            Iterator i = switchboard.accessHosts();
            String host;
            TreeMap access;
            int entCount = 0;
            try {
            while ((entCount < maxCount) && (i.hasNext())) {
                host = (String) i.next();
                access = switchboard.accessTrack(host);
                prop.putHTML("page_list_" + entCount + "_host", host);
                prop.putNum("page_list_" + entCount + "_countSecond", access.tailMap(new Long(System.currentTimeMillis() - 1000)).size());
                prop.putNum("page_list_" + entCount + "_countMinute", access.tailMap(new Long(System.currentTimeMillis() - 1000 * 60)).size());
                prop.putNum("page_list_" + entCount + "_count10Minutes", access.tailMap(new Long(System.currentTimeMillis() - 1000 * 60 * 10)).size());
                prop.putNum("page_list_" + entCount + "_countHour", access.tailMap(new Long(System.currentTimeMillis() - 1000 * 60 * 60)).size());
                entCount++;
            }
            } catch (ConcurrentModificationException e) {} // we dont want to synchronize this
            prop.put("page_list", entCount);
            prop.put("page_num", entCount);
        }
        if (page == 1) {
            String host = post.get("host", "");
            int entCount = 0;
            TreeMap access;
            Map.Entry entry;
            if (host.length() > 0) {
				access = switchboard.accessTrack(host);
				if (access != null) {
					try {
						Iterator ii = treemapclone(access).entrySet().iterator();
						while (ii.hasNext()) {
							entry = (Map.Entry) ii.next();
							prop.putHTML("page_list_" + entCount + "_host", host);
							prop.put("page_list_" + entCount + "_date", serverDate.formatShortSecond(new Date(((Long) entry.getKey()).longValue())));
							prop.putHTML("page_list_" + entCount + "_path", (String) entry.getValue());
							entCount++;
						}
					} catch (ConcurrentModificationException e) {} // we dont want to synchronize this
				}
			} else {
                try {
                	Iterator i = switchboard.accessHosts();
                    while ((entCount < maxCount) && (i.hasNext())) {
						host = (String) i.next();
						access = switchboard.accessTrack(host);
						Iterator ii = treemapclone(access).entrySet().iterator();
						while (ii.hasNext()) {
							entry = (Map.Entry) ii.next();
							prop.putHTML("page_list_" + entCount + "_host", host);
							prop.put("page_list_" + entCount + "_date", serverDate.formatShortSecond(new Date(((Long) entry.getKey()).longValue())));
							prop.putHTML("page_list_" + entCount + "_path", (String) entry.getValue());
							entCount++;
						}
					}
				} catch (ConcurrentModificationException e) {} // we dont want to synchronize this
			}
            prop.put("page_list", entCount);
            prop.put("page_num", entCount);
        }
        if ((page == 2) || (page == 4)) {
            ArrayList<HashMap<String, Object>> array = (page == 2) ? switchboard.localSearches : switchboard.remoteSearches;
            Long trackerHandle;
            HashMap searchProfile;
            int m = Math.min(maxCount, array.size());
            long qcountSum = 0;
            long qtimeSum = 0;
            long rcountSum = 0;
            long utimeSum = 0;
            long stimeSum = 0;
            long rtimeSum = 0;
            
            for (int entCount = 0; entCount < m; entCount++) {
                searchProfile = (HashMap<String, Object>) array.get(array.size() - entCount - 1);
                trackerHandle = (Long) searchProfile.get("time");
            
                // put values in template
                prop.put("page_list_" + entCount + "_dark", ((dark) ? 1 : 0) );
                dark =! dark;
                prop.putHTML("page_list_" + entCount + "_host", (String) searchProfile.get("host"));
                prop.put("page_list_" + entCount + "_date", serverDate.formatShortSecond(new Date(trackerHandle.longValue())));
                prop.put("page_list_" + entCount + "_timestamp", trackerHandle.longValue());
                if (page == 2) {
                    // local search
                    prop.putNum("page_list_" + entCount + "_offset", ((Integer) searchProfile.get("offset")).longValue());
                    prop.put("page_list_" + entCount + "_querystring", (String) searchProfile.get("querystring"));
                } else {
                    // remote search
                    prop.putHTML("page_list_" + entCount + "_peername", (String) searchProfile.get("peername"));
                    prop.put("page_list_" + entCount + "_queryhashes", plasmaSearchQuery.anonymizedQueryHashes((Set) searchProfile.get("queryhashes")));
                }
                prop.putNum("page_list_" + entCount + "_querycount", ((Integer) searchProfile.get("querycount")).longValue());
                prop.putNum("page_list_" + entCount + "_querytime", ((Long) searchProfile.get("querytime")).longValue());
                prop.putNum("page_list_" + entCount + "_resultcount", ((Integer) searchProfile.get("resultcount")).longValue());
                prop.putNum("page_list_" + entCount + "_urltime", ((Long) searchProfile.get("resulturltime")).longValue());
                prop.putNum("page_list_" + entCount + "_snippettime", ((Long) searchProfile.get("resultsnippettime")).longValue());
                prop.putNum("page_list_" + entCount + "_resulttime", ((Long) searchProfile.get("resulttime")).longValue());
                qcountSum += ((Integer) searchProfile.get("querycount")).intValue();
                qtimeSum += ((Long) searchProfile.get("querytime")).longValue();
                rcountSum += ((Integer) searchProfile.get("resultcount")).intValue();
                utimeSum += ((Long) searchProfile.get("resulturltime")).longValue();
                stimeSum += ((Long) searchProfile.get("resultsnippettime")).longValue();
                rtimeSum += ((Long) searchProfile.get("resulttime")).longValue();
            }
            prop.put("page_list", m);
            prop.put("page_num", m);
            
            // Put -1 instead of NaN as result for empty search list
            if (m == 0) m = -1;
            prop.putNum("page_querycount_avg", (double)qcountSum/m);
            prop.putNum("page_querytime_avg", (double)qtimeSum/m);
            prop.putNum("page_resultcount_avg", (double)rcountSum/m);
            prop.putNum("page_urltime_avg", (double)utimeSum/m);
            prop.putNum("page_snippettime_avg", (double)stimeSum/m);
            prop.putNum("page_resulttime_avg", (double)rtimeSum/m);
            prop.putNum("page_total", (page == 2) ? switchboard.localSearches.size() : switchboard.remoteSearches.size());
        }
        if ((page == 3) || (page == 5)) {
            Iterator i = (page == 3) ? switchboard.localSearchTracker.entrySet().iterator() : switchboard.remoteSearchTracker.entrySet().iterator();
            String host;
            TreeSet handles;
            int entCount = 0;
            int qphSum = 0;
            Map.Entry entry;
            try {
            while ((entCount < maxCount) && (i.hasNext())) {
                entry = (Map.Entry) i.next();
                host = (String) entry.getKey();
                handles = (TreeSet) entry.getValue();
                
                int dateCount = 0;
                Iterator ii = handles.iterator();
                while (ii.hasNext()) {
                	Long timestamp = (Long) ii.next();
                	prop.put("page_list_" + entCount + "_dates_" + dateCount + "_date", serverDate.formatShortSecond(new Date(timestamp.longValue())));
                	prop.put("page_list_" + entCount + "_dates_" + dateCount + "_timestamp", timestamp.toString());
                	dateCount++;
                }
                prop.put("page_list_" + entCount + "_dates", dateCount);
                int qph = handles.tailSet(new Long(System.currentTimeMillis() - 1000 * 60 * 60)).size();
                qphSum += qph;
                prop.put("page_list_" + entCount + "_qph", qph);
                
                prop.put("page_list_" + entCount + "_dark", ((dark) ? 1 : 0) ); dark =! dark;
                prop.putHTML("page_list_" + entCount + "_host", host);
                if (page == 5) {
                    yacySeed remotepeer = yacyCore.seedDB.lookupByIP(natLib.getInetAddress(host), true, true, true);
                    prop.putHTML("page_list_" + entCount + "_peername", (remotepeer == null) ? "UNKNOWN" : remotepeer.getName());
                }
                prop.putNum("page_list_" + entCount + "_count", handles.size());

                // next
                entCount++;
            }
            } catch (ConcurrentModificationException e) {} // we dont want to synchronize this
            prop.put("page_list", entCount);
            prop.putNum("page_num", entCount);
            prop.putNum("page_total", (page == 3) ? switchboard.localSearches.size() : switchboard.remoteSearches.size());
            prop.putNum("page_qph_sum", qphSum);
        }
        // return rewrite properties
        return prop;
    }
 
}
