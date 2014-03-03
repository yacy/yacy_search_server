// IndexCreateLoaderQueue_p.java
// -----------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
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
// javac -classpath .:../classes IndexCreate_p.java
// if the shell's current path is HTROOT

import java.util.Map;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.crawler.retrieval.Request;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexCreateLoaderQueue_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        Map<DigestURL, Request> map = sb.crawlQueues.activeWorkerEntries();
        if (map.size() == 0) {
            prop.put("loader-set", "0");
        } else {
            prop.put("loader-set", "1");
            boolean dark = true;
            Seed initiator;
            int count = 0;
            for (Request element : map.values()) {
                if (element == null) continue;

                initiator = sb.peers.getConnected((element.initiator() == null) ? "" : ASCII.String(element.initiator()));
                prop.put("loader-set_list_"+count+"_dark", dark ? "1" : "0");
                prop.putHTML("loader-set_list_"+count+"_initiator", ((initiator == null) ? "proxy" : initiator.getName()));
                prop.put("loader-set_list_"+count+"_depth", element.depth());
                prop.put("loader-set_list_"+count+"_status", element.getStatus());
                prop.putHTML("loader-set_list_"+count+"_url", element.url().toNormalform(true));
                dark = !dark;
                count++;
            }
            prop.put("loader-set_list", count);
            prop.put("loader-set_num", count);
        }

        // return rewrite properties
        return prop;
    }
}
