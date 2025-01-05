//Connections_p.java
//-----------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004, 2005
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

//You must compile this file with
//javac -classpath .:../classes Network.java
//if the shell's current path is HTROOT

package net.yacy.htroot;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public final class Connections_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();

        // server sessions
        List<ConnectionInfo> allConnectionsSorted = new LinkedList<ConnectionInfo>(ConnectionInfo.getServerConnections());
        Collections.sort(allConnectionsSorted);
        Collections.reverse(allConnectionsSorted); // toggle ascending/descending

        int c = 0;
        synchronized (allConnectionsSorted) {
	        for (final ConnectionInfo conInfo: allConnectionsSorted) {
	            prop.put("list_" + c + "_proto", conInfo.getProtocol());
	            prop.putNum("list_" + c + "_duration", conInfo.getLifetime());
	            prop.put("list_" + c + "_source", conInfo.getTargetHost());
	            prop.putHTML("list_" + c + "_command", conInfo.getCommand());
	            prop.put("list_" + c + "_id", conInfo.getID());
	            c++;
	        }
        }
        prop.put("list", c);
        prop.putNum("numMax", ConnectionInfo.getServerMaxcount());
        prop.putNum("numActiveRunning", c);

        // client sessions
        // sorting: sort by initTime, decending
        allConnectionsSorted = new LinkedList<ConnectionInfo>(ConnectionInfo.getAllConnections());
        Collections.sort(allConnectionsSorted);
        Collections.reverse(allConnectionsSorted); // toggle ascending/descending

        c = 0;
        synchronized (allConnectionsSorted) {
	        for (final ConnectionInfo conInfo: allConnectionsSorted) {
	            prop.put("clientList_" + c + "_clientProtocol", conInfo.getProtocol());
	            prop.putNum("clientList_" + c + "_clientLifetime", conInfo.getLifetime());
	            prop.putNum("clientList_" + c + "_clientUpbytes", conInfo.getUpbytes());
	            prop.put("clientList_" + c + "_clientTargetHost", conInfo.getTargetHost());
	            prop.putHTML("clientList_" + c + "_clientCommand", conInfo.getCommand());
	            prop.put("clientList_" + c + "_clientID", conInfo.getID());
	            c++;
	        }
        }
        prop.put("clientList", c);
        prop.put("clientActive", ConnectionInfo.getCount());

        // return rewrite values for templates
        return prop;
    }
}
