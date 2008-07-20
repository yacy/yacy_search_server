package www;
// welcome.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
// javac -classpath .:../classes index.java
// if the shell's current path is HTROOT

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverDomains;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeed;

public class welcome {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();

        // update seed info
        sb.updateMySeed();

        prop.putHTML("peername", env.getConfig("peerName", "<nameless>"));
        prop.putHTML("peerdomain", env.getConfig("peerName", "<nameless>").toLowerCase());
        prop.putHTML("peeraddress", sb.webIndex.seedDB.mySeed().getPublicAddress());
        prop.put("hostname", serverDomains.myPublicIP());
        try{
            prop.put("hostip", InetAddress.getByName(serverDomains.myPublicIP()).getHostAddress());
        }catch(UnknownHostException e){
            prop.put("hostip", "Unknown Host Exception");
        }       
        prop.put("port", serverCore.getPortNr(env.getConfig("port","8080")));
        prop.put("clientip", (String) header.get(httpHeader.CONNECTION_PROP_CLIENTIP, ""));

        final String peertype = (sb.webIndex.seedDB.mySeed() == null) ? yacySeed.PEERTYPE_JUNIOR : sb.webIndex.seedDB.mySeed().get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN);
        final boolean senior = (peertype.equals(yacySeed.PEERTYPE_SENIOR)) || (peertype.equals(yacySeed.PEERTYPE_PRINCIPAL));
        if (senior) { prop.put("couldcan", "can"); } else { prop.put("couldcan", "could"); }
        if (senior) { prop.put("seniorinfo", "This peer runs in senior mode which means that your peer can be accessed using the addresses shown above."); } else { prop.putHTML("seniorinfo", "<b>Nobody can access your peer from the outside of your intranet. You must open your firewall and/or set a 'virtual server' in the settings of your router to enable access to the addresses as shown below.</b>"); }
        File wwwpath = env.getConfigPath("htDocsPath", "DATA/HTDOCS");
        prop.putHTML("wwwpath", wwwpath.isAbsolute() ? wwwpath.getAbsolutePath() : "<application_root_path>/" + env.getConfig("htDocsPath", "DATA/HTDOCS"));

        // return rewrite properties
        return prop;
    }
}
