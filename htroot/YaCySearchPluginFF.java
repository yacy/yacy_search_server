//YaCySearchPluginFF.pac
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

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class YaCySearchPluginFF {

    /**
     * @param header the complete HTTP header of the request
     * @param post any arguments for this servlet, the request carried with (GET as well as POST)
     * @param env the serverSwitch object holding all runtime-data
     * @return the rewrite-properties for the template
     */
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        String host = header.getServerName();
        int port = header.getServerPort();

        prop.put("host", host);
        prop.put("port", port);
        prop.putHTML("name", sb.peers.mySeed().getName());

        return prop;
    }

}
