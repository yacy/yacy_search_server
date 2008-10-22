// opensearchdescription.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 12.08.2006 on http://www.anomic.de
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
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class opensearchdescription {

    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        // generate message content for open search description
        String promoteSearchPageGreeting = env.getConfig("promoteSearchPageGreeting", "");
        if (env.getConfigBool("promoteSearchPageGreeting.useNetworkName", false)) promoteSearchPageGreeting = env.getConfig("network.unit.description", "");
        if (promoteSearchPageGreeting.length() == 0) promoteSearchPageGreeting = "P2P WEB SEARCH";

        String thisaddress = (String) header.get("Host", "localhost");
        if (thisaddress.indexOf(":") == -1) thisaddress += ":" + serverCore.getPortNr(env.getConfig("port", "8080"));
        
        final serverObjects prop = new serverObjects();
        prop.putXML("thisaddress", thisaddress);
        prop.putXML("SearchPageGreeting", promoteSearchPageGreeting);
        prop.putXML("clientname", sb.webIndex.seedDB.mySeed().getName());
        
        // return rewrite properties
        return prop;
    }

}
