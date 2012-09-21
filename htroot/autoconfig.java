//autoconfig.pac
//-----------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

//you must compile this file with
//javac -classpath .:../Classes Status.java
//if the shell's current path is HTROOT

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.NumberTools;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class autoconfig {

	// http://web.archive.org/web/20071011034022/http://wp.netscape.com/eng/mozilla/2.0/relnotes/demo/proxy-live.html
    /**
     * Generates a proxy-autoconfig-file (application/x-ns-proxy-autoconfig)
     * See: <a href="http://wp.netscape.com/eng/mozilla/2.0/relnotes/demo/proxy-live.html">Proxy Auto-Config File Format</a>
     * @param header the complete HTTP header of the request
     * @param post any arguments for this servlet, the request carried with (GET as well as POST)
     * @param env the serverSwitch object holding all runtime-data
     * @return the rewrite-properties for the template
     */
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();

        final boolean yacyonly = env.getConfigBool(SwitchboardConstants.PROXY_YACY_ONLY, false);

        // get the http host header
        final String hostSocket = header.get(HeaderFramework.CONNECTION_PROP_HOST);

        String host = hostSocket;
        int port = 80;
        final int pos = hostSocket.indexOf(':',0);
        if (pos != -1) {
            port = NumberTools.parseIntDecSubstring(hostSocket, pos + 1);
            host = hostSocket.substring(0, pos);
        }

        prop.put("yacy", yacyonly ? "0" : "1");
        prop.put("yacy_host", host);
        prop.put("yacy_port", port);

        return prop;
    }

}
