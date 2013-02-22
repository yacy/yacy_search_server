// IndexShare_p.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last change: 24.08.2004
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
// javac -classpath .:../classes IndexShare_p.java
// if the shell's current path is HTROOT

//import java.util.*;
//import java.net.*;
//import java.io.*;
//import de.anomic.tools.*;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Segment;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexShare_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
    	// return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        // get segment
        Segment indexSegment = sb.index;

        if (post == null) {
            prop.put("linkfreq", sb.getConfigLong("defaultLinkReceiveFrequency",30));
            prop.put("wordfreq", sb.getConfigLong("defaultWordReceiveFrequency",10));
            prop.put("dtable", "");
            prop.put("rtable", "");
            prop.putNum("wcount", indexSegment.RWICount());
            prop.putNum("ucount", indexSegment.fulltext().collectionSize());
            return prop; // be save
        }

        if (post.containsKey("indexsharesetting")) {
            sb.setConfig(SwitchboardConstants.INDEX_DIST_ALLOW, post.containsKey("distribute"));
            sb.setConfig("allowReceiveIndex", post.containsKey("receive"));
            sb.setConfig("defaultLinkReceiveFrequency", post.getInt("linkfreq", 30));
            sb.setConfig("defaultWordReceiveFrequency", post.getInt("wordfreq", 10));
        }

        // insert constants
        prop.putNum("wcount", indexSegment.RWICount());
        prop.putNum("ucount", indexSegment.fulltext().collectionSize());

        // return rewrite properties
        return prop;
    }
}
