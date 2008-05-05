package www;
// welcome.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.
//
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
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class welcome {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();

        // update seed info
        yacyCore.peerActions.updateMySeed();

        prop.putHTML("peername", env.getConfig("peerName", "<nameless>"));
        prop.putHTML("peerdomain", env.getConfig("peerName", "<nameless>").toLowerCase());
        prop.putHTML("peeraddress", sb.wordIndex.seedDB.mySeed().getPublicAddress());
        prop.put("hostname", serverDomains.myPublicIP());
        try{
            prop.put("hostip", InetAddress.getByName(serverDomains.myPublicIP()).getHostAddress());
        }catch(UnknownHostException e){
            prop.put("hostip", "Unknown Host Exception");
        }       
        prop.put("port", serverCore.getPortNr(env.getConfig("port","8080")));
        prop.put("clientip", (String) header.get(httpHeader.CONNECTION_PROP_CLIENTIP, ""));

        final String peertype = (sb.wordIndex.seedDB.mySeed() == null) ? yacySeed.PEERTYPE_JUNIOR : sb.wordIndex.seedDB.mySeed().get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN);
        final boolean senior = (peertype.equals(yacySeed.PEERTYPE_SENIOR)) || (peertype.equals(yacySeed.PEERTYPE_PRINCIPAL));
        if (senior) { prop.put("couldcan", "can"); } else { prop.put("couldcan", "could"); }
        if (senior) { prop.put("seniorinfo", "This peer runs in senior mode which means that your peer can be accessed using the addresses shown above."); } else { prop.putHTML("seniorinfo", "<b>Nobody can access your peer from the outside of your intranet. You must open your firewall and/or set a 'virtual server' in the settings of your router to enable access to the addresses as shown below.</b>"); }
        File wwwpath = env.getConfigPath("htDocsPath", "DATA/HTDOCS");
        prop.putHTML("wwwpath", wwwpath.isAbsolute() ? wwwpath.getAbsolutePath() : "<application_root_path>/" + env.getConfig("htDocsPath", "DATA/HTDOCS"));

        // return rewrite properties
        return prop;
    }
}
