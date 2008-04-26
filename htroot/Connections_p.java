//Connections_p.java
//-----------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
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
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

//You must compile this file with
//javac -classpath .:../classes Network.java
//if the shell's current path is HTROOT

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.Properties;
import java.util.Set;

import de.anomic.http.HttpConnectionInfo;
import de.anomic.http.JakartaCommonsHttpClient;
import de.anomic.http.httpHeader;
import de.anomic.http.httpd;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.server.serverHandler;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverThread;
import de.anomic.server.serverCore.Session;
import de.anomic.urlRedirector.urlRedirectord;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public final class Connections_p {    
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> sb) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) sb;
        serverObjects prop = new serverObjects();
         
        
        // get the virtualHost string
        String virtualHost = switchboard.getConfig("fileHost","localhost");
        
        // get the serverCore thread
        serverThread httpd = switchboard.getThread("10_httpd");
        
        /* waiting for all threads to finish */
        int threadCount  = serverCore.sessionThreadGroup.activeCount();    
        Thread[] threadList = new Thread[((serverCore) httpd).getJobCount()];     
        threadCount = serverCore.sessionThreadGroup.enumerate(threadList);              
        
        // determines if name lookup should be done or not 
        boolean doNameLookup = false;
        if (post != null) {  
            if (post.containsKey("nameLookup") && post.get("nameLookup","true").equals("true")) {
                doNameLookup = true;
            }
            if (post.containsKey("closeSession")) {
                String sessionName = post.get("closeSession",null);
                if (sessionName != null) {
                    for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {                        
                        Thread currentThread = threadList[currentThreadIdx];
                        if (
                                (currentThread != null) && 
                                (currentThread instanceof serverCore.Session) && 
                                (currentThread.isAlive()) &&
                                (currentThread.getName().equals(sessionName))
                        ){
                            // trying to gracefull stop session
                            ((Session)currentThread).setStopped(true);
                            try { Thread.sleep(100); } catch (InterruptedException ex) {}
                            
                            // trying to interrupt session
                            if (currentThread.isAlive()) {
                                currentThread.interrupt();
                                try { Thread.sleep(100); } catch (InterruptedException ex) {}
                            } 
                            
                            // trying to close socket
                            if (currentThread.isAlive()) {
                                ((Session)currentThread).close();
                            }
                            
                            // waiting for session to finish
                            if (currentThread.isAlive()) {
                                try { currentThread.join(500); } catch (InterruptedException ex) {}
                            }
                        }
                    }
                }
                prop.put("LOCATION","");
                return prop;                
            }
        }  
        
        int idx = 0, numActiveRunning = 0, numActivePending = 0;
        boolean dark = true;
        for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
            Thread currentThread = threadList[currentThreadIdx];
            if ((currentThread != null) && (currentThread instanceof serverCore.Session) && (currentThread.isAlive())) {
                // getting the session object
                Session currentSession = ((Session)currentThread);
                
                // getting the session runtime
                long sessionTime = currentSession.getTime();
                
                // getting the request command line
                boolean blockingRequest = false;
                String commandLine = currentSession.getCommandLine();
                if (commandLine == null) blockingRequest = true;                
                int commandCount = currentSession.getCommandCount();
                
                // getting the source ip address and port
                InetAddress userAddress = currentSession.getUserAddress();
                int userPort = currentSession.getUserPort();
                if (userAddress == null) continue;
                
                boolean isSSL = currentSession.isSSL();
                
                String dest = null;
                String prot = null;
                serverHandler cmdObj = currentSession.getCommandObj();
                if (cmdObj instanceof httpd) {
                    prot = isSSL ? "https":"http";
                    
                    // getting the http command object
                    httpd currentHttpd =  (httpd)cmdObj;
                    
                    // getting the connection properties of this session
                    Properties conProp = (Properties) currentHttpd.getConProp().clone();
                    
                    // getting the destination host
                    dest = conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
                    if (dest==null)continue;
                } else if (cmdObj instanceof urlRedirectord) {
                    prot = "urlRedirector";
                    
                    urlRedirectord urlRedir = (urlRedirectord)cmdObj;
                    commandLine = urlRedir.getURL();
                }                
                
                if ((dest != null) && (dest.equals(virtualHost))) dest = yacyCore.seedDB.mySeed().getName() + ".yacy";
                
                // determining if the source is a yacy host
                yacySeed seed = null;
                if (doNameLookup) {
                    seed = yacyCore.seedDB.lookupByIP(userAddress,true,false,false);
                    if (seed != null) {
                        if ((seed.hash.equals(yacyCore.seedDB.mySeed().hash)) && 
                                (!seed.get(yacySeed.PORT,"").equals(Integer.toString(userPort)))) {
                            seed = null;
                        }
                    }
                }
                
                prop.put("list_" + idx + "_dark", dark ? "1" : "0");
                dark=!dark;
                try {
                    prop.put("list_" + idx + "_sessionID",URLEncoder.encode(currentSession.getName(),"UTF8"));
                } catch (UnsupportedEncodingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                prop.putHTML("list_" + idx + "_sessionName", currentSession.getName());
                prop.put("list_" + idx + "_proto", prot);
                if (sessionTime > 1000*60) {
                    prop.put("list_" + idx + "_ms", "0");
                    prop.put("list_" + idx + "_ms_duration",serverDate.formatInterval(sessionTime));
                } else {
                    prop.put("list_" + idx + "_ms", "1");
                    prop.putNum("list_" + idx + "_ms_duration", sessionTime);
                }
                prop.putHTML("list_" + idx + "_source",(seed!=null)?seed.getName()+".yacy":userAddress.getHostAddress()+":"+userPort);
                prop.put("list_" + idx + "_dest",(dest==null)?"-":dest);
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
        }     
        prop.put("list", idx);
        
        prop.putNum("numMax", ((serverCore)httpd).getMaxSessionCount());
        prop.putNum("numActiveRunning", numActiveRunning);
        prop.putNum("numActivePending", numActivePending);
        
        // client sessions
        Set<HttpConnectionInfo> allConnections = HttpConnectionInfo.getAllConnections();
        // TODO sorting
//        Arrays.sort(a, httpc.connectionTimeComparatorInstance);
        int c = 0;
        synchronized (allConnections) {
        for (HttpConnectionInfo conInfo: allConnections) {
            prop.put("clientList_" + c + "_clientProtocol", conInfo.getProtocol());
            prop.putNum("clientList_" + c + "_clientLifetime", conInfo.getLifetime());
            prop.putNum("clientList_" + c + "_clientIdletime", conInfo.getIdletime());
            prop.put("clientList_" + c + "_clientTargetHost", conInfo.getTargetHost());
            prop.putHTML("clientList_" + c + "_clientCommand", conInfo.getCommand());
            prop.put("clientList_" + c + "_clientID", conInfo.getID());
            c++;
        }
        }
        prop.put("clientList", c);
        prop.put("clientActive", JakartaCommonsHttpClient.connectionCount());
        
        // return rewrite values for templates
        return prop;
    }
}
