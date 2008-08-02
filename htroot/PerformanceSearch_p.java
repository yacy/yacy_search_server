// PerformaceSearch_p.java
// (C) 2004, 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 16.02.2005 on http://yacy.net
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
import java.util.Iterator;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaProfiling;
import de.anomic.server.serverObjects;
import de.anomic.server.serverProfiling;
import de.anomic.server.serverSwitch;

public class PerformanceSearch_p {
    
    public static serverObjects respond(final httpHeader header, final serverObjects post, final serverSwitch<?> sb) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        
        final Iterator<serverProfiling.Event> events = serverProfiling.history("SEARCH");
        int c = 0;
        serverProfiling.Event event;
        plasmaProfiling.searchEvent search;
        long lastt = 0;
        while (events.hasNext()) {
            event = events.next();
            search = (plasmaProfiling.searchEvent) event.payload;
            prop.put("table_" + c + "_query", search.queryID);
            prop.put("table_" + c + "_event", search.processName);
            prop.putNum("table_" + c + "_count", search.resultCount);
            prop.putNum("table_" + c + "_delta", event.time - lastt);
            prop.put("table_" + c + "_time", (new Date(event.time)).toString());
            prop.putNum("table_" + c + "_duration", search.duration);
            c++;
            lastt = event.time;
        }
        prop.put("table", c);
        
        return prop;
    }
}
