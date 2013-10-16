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

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.workflow.WorkflowThread;
import net.yacy.peers.PeerActions;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.server.serverCore;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.serverCore.Session;

public final class Connections_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        // server sessions
        // get the serverCore thread
        final WorkflowThread httpd = sb.getThread("10_httpd");

        // determines if name lookup should be done or not
        final boolean doNameLookup;
        if (post != null) {
            doNameLookup = post.getBoolean("nameLookup");
            if (post.containsKey("closeServerSession")) {
                final String sessionName = post.get("closeServerSession", null);
                sb.closeSessions(sessionName);
                prop.put(serverObjects.ACTION_LOCATION,"");
                return prop;
            }
        } else {
            doNameLookup = false;
        }

        // waiting for all threads to finish
        int idx = 0, numActiveRunning = 0, numActivePending = 0;
        boolean dark = true;
        for (final Session s: serverCore.getJobList()) {
            if (!s.isAlive()) continue;

            // get the session runtime
            final long sessionTime = s.getTime();

            // get the request command line
            String commandLine = s.getCommandLine();
            final boolean blockingRequest = (commandLine == null);
            final int commandCount = s.getCommandCount();

            // get the source ip address and port
            final InetAddress userAddress = s.getUserAddress();
            final int userPort = s.getUserPort();
            if (userAddress == null) {
                continue;
            }

            String prot = "http"; // only httpd sessions listed

            // determining if the source is a yacy host
            Seed seed = null;
            if (doNameLookup) {
                seed = sb.peers.lookupByIP(userAddress, -1, true, false, false);
                if (seed != null && (seed.hash.equals(sb.peers.mySeed().hash)) &&
                        (!seed.get(Seed.PORT,"").equals(Integer.toString(userPort)))) {
                    seed = null;
                }
            }

            prop.put("list_" + idx + "_dark", dark ? "1" : "0");
            dark = !dark;
            try {
                prop.put("list_" + idx + "_serverSessionID",URLEncoder.encode(s.getName(),"UTF8"));
            } catch (final UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                ConcurrentLog.logException(e);
            }
            prop.putHTML("list_" + idx + "_sessionName", s.getName());
            prop.put("list_" + idx + "_proto", prot);
            if (sessionTime > 1000*60) {
                prop.put("list_" + idx + "_ms", "0");
                prop.put("list_" + idx + "_ms_duration",PeerActions.formatInterval(sessionTime));
            } else {
                prop.put("list_" + idx + "_ms", "1");
                prop.putNum("list_" + idx + "_ms_duration", sessionTime);
            }
            prop.putHTML("list_" + idx + "_source",(seed!=null)?seed.getName()+".yacy":userAddress.getHostAddress()+":"+userPort);
            prop.putHTML("list_" + idx + "_dest", "-");
            if (blockingRequest) {
                prop.put("list_" + idx + "_running", "0");
                prop.putNum("list_" + idx + "_running_reqNr", commandCount+1);
                numActivePending++;
            } else {
                prop.put("list_" + idx + "_running", "1");
                prop.put("list_" + idx + "_running_command", commandLine==null ? "" :commandLine);
                numActiveRunning++;
            }
            prop.putNum("list_" + idx + "_used", commandCount);
            idx++;
        }
        prop.put("list", idx);

        prop.putNum("numMax", ((serverCore)httpd).getMaxSessionCount());
        prop.putNum("numActiveRunning", numActiveRunning);
        prop.putNum("numActivePending", numActivePending);

        // client sessions
        final Set<ConnectionInfo> allConnections = ConnectionInfo.getAllConnections();
        // sorting: sort by initTime, decending
        List<ConnectionInfo> allConnectionsSorted = new LinkedList<ConnectionInfo>(allConnections);
        Collections.sort(allConnectionsSorted);
        Collections.reverse(allConnectionsSorted); // toggle ascending/descending

        int c = 0;
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
