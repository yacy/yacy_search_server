// ConfigPHPBB3Search.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 09.06.2009 as IndexCreate_p.java on http://yacy.net
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

import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class ConfigPHPBB3Search {
    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        
        // define visible variables
        String a = sb.peers.mySeed().getPublicAddress();
        if (a == null) a = "localhost:" + sb.getConfig("port", "8080");
        boolean intranet = sb.getConfig(plasmaSwitchboardConstants.NETWORK_NAME, "").equals("intranet");
        String repository = "http://" + a + "/repository/";
        prop.put("starturl", (intranet) ? repository : "http://");
        prop.put("address", a);
        
        // return rewrite properties
        return prop;
    }
}
