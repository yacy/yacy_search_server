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
import java.util.Set;

import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.workflow.WorkflowThread;

//import de.anomic.http.client.ConnectionInfo;
//import de.anomic.http.client.Client;
import de.anomic.search.Switchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverCore.Session;
import de.anomic.yacy.yacyPeerActions;
import de.anomic.yacy.yacySeed;

public final class Connections_p {    
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        // server sessions
        // get the serverCore thread
        final WorkflowThread httpd = sb.getThread("10_httpd");
        
        // determines if name lookup should be done or not 
        final boolean doNameLookup;
        if (post != null) {  
            doNameLookup = (post.containsKey("nameLookup") && post.getBoolean("nameLookup", true));
            if (post.containsKey("closeServerSession")) {
                final String sessionName = post.get("closeServerSession", null);
                sb.closeSessions("10_httpd", sessionName);
                prop.put("LOCATION","");
                return prop;                
            }
        } else {
            doNameLookup = false;
        }
        
        // waiting for all threads to finish
        int idx = 0, numActiveRunning = 0, numActivePending = 0;
        boolean dark = true;
        for (final Session s: ((serverCore) httpd).getJobList()) {
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
            yacySeed seed = null;
            if (doNameLookup) {
                seed = sb.peers.lookupByIP(userAddress,true,false,false);
                if (seed != null && (seed.hash.equals(sb.peers.mySeed().hash)) &&
                        (!seed.get(yacySeed.PORT,"").equals(Integer.toString(userPort)))) {
                    seed = null;
                }
            }
            
            prop.put("list_" + idx + "_dark", dark ? "1" : "0");
            dark = !dark;
            try {
                prop.put("list_" + idx + "_serverSessionID",URLEncoder.encode(s.getName(),"UTF8"));
            } catch (final UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                Log.logException(e);
            }
            prop.putHTML("list_" + idx + "_sessionName", s.getName());
            prop.put("list_" + idx + "_proto", prot);
            if (sessionTime > 1000*60) {
                prop.put("list_" + idx + "_ms", "0");
                prop.put("list_" + idx + "_ms_duration",yacyPeerActions.formatInterval(sessionTime));
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
        // TODO sorting
//        Arrays.sort(a, httpc.connectionTimeComparatorInstance);
        int c = 0;
        synchronized (allConnections) {
        for (final ConnectionInfo conInfo: allConnections) {
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
