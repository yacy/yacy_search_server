// timeline.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 27.02.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-02-10 01:06:59 +0100 (Di, 10 Feb 2009) $
// $LastChangedRevision: 5586 $
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
import java.util.Map;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.EventTracker;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public final class timeline_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;

        final serverObjects prop = new serverObjects();
        if ((post == null) || (env == null)) return prop;


        final String[] data = post.get("data", "").split(",");  // a string of word hashes that shall be searched and combined
        Map<String, Map<Date, EventTracker.Event>> proc = new HashMap<>();
        for (String s: data) proc.put(s, null);
        
        /*
        while (i.hasNext() && c < count) {
            entry = i.next();
            lm = new Date(entry.lastModified());
            lms = GenericFormatter.ANSIC_FORMATTER.format(lm);
            prop.put("event_" + c + "_time", lms); // like "Wed May 01 1963 00:00:00 GMT-0600"
            prop.put("event_" + c + "_isDuration", 0); // 0 (only a point) or 1 (period of time)
            prop.put("event_" + c + "_isDuration_duration", 0); // 0 (only a point) or 1 (period of time)
            prop.putHTML("event_" + c + "_type", "type"); // short title of the event
            prop.putHTML("event_" + c + "_description", ""); // long description of the event
            c++;
        }
        prop.put("event", c);
*/
        return prop;
    }

}
