// sidebar_history.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 03.03.2008 on http://yacy.net
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

import java.util.HashSet;
import java.util.Iterator;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class sidebar_history {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
    
        // list search history
        Iterator<plasmaSearchQuery> i = sb.localSearches.iterator();
        String client = (String) header.get(httpHeader.CONNECTION_PROP_CLIENTIP);
        plasmaSearchQuery query;
        int c = 0;
        HashSet<String> visibleQueries = new HashSet<String>();
        while (i.hasNext()) {
            query = i.next();
            if (query.resultcount == 0) continue;
            if (query.offset != 0) continue;
            if (!query.host.equals(client)) continue; // the search history should only be visible from the user who initiated the search
            if (visibleQueries.contains(query.queryString)) continue; // avoid doubles
            visibleQueries.add(query.queryString);
            prop.put("history_list_" + c + "_querystring", query.queryString);
            prop.put("history_list_" + c + "_searchdom", query.searchdom());
            prop.put("history_list_" + c + "_contentdom", query.contentdom());
            c++;
            if (c >= 10) break;
        }
        prop.put("history_list", c);
        prop.put("history_host", client);
        if (c == 0) prop.put("history", 0); else prop.put("history", 1); // switch on if there is anything to see
        
        return prop;
    }

}
