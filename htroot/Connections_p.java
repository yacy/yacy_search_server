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

import java.net.InetAddress;
import java.util.Properties;

import org.apache.commons.pool.impl.GenericObjectPool;

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
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public final class Connections_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch sb) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) sb;
        serverObjects prop = new serverObjects();
                 
        // determines if name lookup should be done or not
        boolean doNameLookup = true;
        if ((post != null) && post.containsKey("nameLookup") && post.get("nameLookup","true").equals("false")) {
            doNameLookup = false;
        }
        
        // getting the virtualHost string
        String virtualHost = switchboard.getConfig("fileHost","localhost");
        
        // getting the serverCore thread
        serverThread httpd = switchboard.getThread("10_httpd");
        
        // getting the session threadgroup
        ThreadGroup httpSessions = ((serverCore)httpd).getSessionThreadGroup();        
        
        // getting the server core pool configuration
        GenericObjectPool.Config httpdPoolConfig = ((serverCore)httpd).getPoolConfig();  
        
        /* waiting for all threads to finish */
        int threadCount  = httpSessions.activeCount();    
        Thread[] threadList = new Thread[httpdPoolConfig.maxActive];     
        threadCount = httpSessions.enumerate(threadList);        
        
        int idx = 0, numActiveRunning = 0, numActivePending = 0, numMax = ((serverCore)httpd).getMaxSessionCount();
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
                
                serverHandler cmdObj = currentSession.getCommandObj();
                if (cmdObj instanceof httpd) {
                    
                    // getting the http command object
                    httpd currentHttpd =  (httpd)cmdObj;
                    
                    // getting the connection properties of this session
                    Properties conProp = (Properties) currentHttpd.getConProp().clone();
                    
                    // getting the destination host
                    String dest = conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
                    if (dest==null)continue;
                    if (dest.equals(virtualHost)) dest = yacyCore.seedDB.mySeed.getName() + ".yacy";
                    

                    
                    // determining if the source is a yacy host
                    yacySeed seed = null;
                    if (doNameLookup) {
                        seed = yacyCore.seedDB.lookupByIP(userAddress,true,false,false);
                        if (seed != null) {
                            if ((seed.hash.equals(yacyCore.seedDB.mySeed.hash)) && 
                                    (!seed.get(yacySeed.PORT,"").equals(Integer.toString(userPort)))) {
                                seed = null;
                            }
                        }
                    }
                    
                    prop.put("list_" + idx + "_dark", ((dark) ? 1 : 0) ); dark=!dark;
                    prop.put("list_" + idx + "_sessionName",currentSession.getName());
                    prop.put("list_" + idx + "_proto","http");             
                    if (sessionTime > 1000*60) {
                        prop.put("list_" + idx + "_ms",0);
                        prop.put("list_" + idx + "_ms_duration",serverDate.intervalToString(sessionTime));
                    } else {
                        prop.put("list_" + idx + "_ms",1);
                        prop.put("list_" + idx + "_ms_duration",Long.toString(sessionTime));
                    }
                    prop.put("list_" + idx + "_source",(seed!=null)?seed.getName()+".yacy":userAddress.getHostAddress()+":"+userPort);
                    prop.put("list_" + idx + "_dest",dest);
                    if (blockingRequest) {
                        prop.put("list_" + idx + "_running",0);
                        prop.put("list_" + idx + "_running_reqNr",Integer.toString(commandCount+1));
                        numActivePending++;
                    } else {
                        prop.put("list_" + idx + "_running",1);
                        prop.put("list_" + idx + "_running_command",commandLine);
                        numActiveRunning++;
                    }
                    prop.put("list_" + idx + "_used",Integer.toString(commandCount));
                    
                    idx++;
                }
            }
        }     
        prop.put("list",idx);
        
        prop.put("numMax",Integer.toString(numMax));
        prop.put("numActiveRunning",Integer.toString(numActiveRunning));
        prop.put("numActivePending",Integer.toString(numActivePending));
        
        // return rewrite values for templates
        return prop;
    }
}
