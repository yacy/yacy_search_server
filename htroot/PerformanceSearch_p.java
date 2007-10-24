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


import java.util.Iterator;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSearchEvent;
import de.anomic.plasma.plasmaSearchProcessing;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class PerformanceSearch_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch sb) {
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();
        plasmaSearchEvent se = plasmaSearchEvent.getEvent(plasmaSearchEvent.lastEventID);
        
        if (se == null) {
            prop.put("table", "0");
            return prop;
        }
        
        Iterator events = se.getProcess().events();
        int c = 0;
        plasmaSearchProcessing.Entry event;
        while (events.hasNext()) {
            event = (plasmaSearchProcessing.Entry) events.next();
            prop.put("table_" + c + "_event", event.process);
            prop.putNum("table_" + c + "_count", event.count);
            prop.putNum("table_" + c + "_time", event.time);
            c++;
        }
        prop.put("table", c);
        
        return prop;
    }
}
