//IndexTransfer_p.java 
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//This file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
//javac -classpath .:../Classes IndexControl_p.java
//if the shell's current path is HTROOT

import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.TreeMap;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverMemory;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeed;

public final class IndexTransfer_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        // return variable that accumulates replacements
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        
        if (post != null) {            
            if (post.containsKey("startIndexTransfer")) {                
                yacySeed seed = sb.webIndex.seedDB.getConnected(post.get("hostHash", ""));                
                if (seed == null) {
                    prop.put("running_status","Disconnected peer");
                } else {                    
                    boolean deleteIndex = post.get("deleteIndex", "0").equals("1");
                    if(prop.containsKey("overwriteIP") && ! (prop.get("overwriteIP")).equals("")){
                        seed.setIP(prop.get("overwriteIP"));
                    }
                    sb.startTransferWholeIndex(seed,deleteIndex);
                    prop.put("LOCATION","");
                    return prop;
                }
            } else if (post.containsKey("stopIndexTransfer")) {
                sb.stopTransferWholeIndex(true);
                prop.put("LOCATION","");
                return prop;
                
            } else if (post.containsKey("newIndexTransfer")) {
                sb.abortTransferWholeIndex(true);
                prop.put("LOCATION","");
                return prop;
            }
        }
        
        // insert constants
        prop.putNum("wcount", sb.webIndex.size());
        prop.putNum("ucount", sb.webIndex.countURL());
        prop.put("running",(sb.transferIdxThread==null) ? "0" : "1");
        if (sb.transferIdxThread != null) {
            String[] status = sb.transferIdxThread.getStatus();
            String[] range  = sb.transferIdxThread.getRange();
            int[] chunk     = sb.transferIdxThread.getIndexCount();
            
            prop.put("running_selection.status",status[0]);
            prop.put("running_selection.twrange", range[0]);
            prop.put("running_selection.twchunk", chunk[0]);
            
            prop.put("running_transfer.status",status[1]);
            prop.put("running_transfer.twrange", range[1]);
            prop.put("running_transfer.twchunk", chunk[1]);

            
            prop.putNum("running_twEntityCount", sb.transferIdxThread.getTransferedContainerCount());
            prop.putNum("running_twEntryCount", sb.transferIdxThread.getTransferedEntryCount());
            prop.put("running_twPayloadSize", serverMemory.bytesToString(sb.transferIdxThread.getTransferedBytes()));
            prop.putNum("running_twEntityPercent", sb.transferIdxThread.getTransferedContainerPercent());
            prop.putNum("running_twEntrySpeed", sb.transferIdxThread.getTransferedEntrySpeed());
            
            prop.put("running_deleteIndex", sb.transferIdxThread.deleteIndex() ? "1" : "0");
            prop.put("running_peerName",sb.transferIdxThread.getSeed().getName());
            prop.put("running_stopped",(sb.transferIdxThread.isFinished()) || (!sb.transferIdxThread.isAlive()) ? "1" : "0");
        } else {
            if (!prop.containsKey("running_status")) prop.put("running_status","Not running");
        }
        
        
        
        //List known hosts
        yacySeed seed;
        int hc = 0;
        if ((sb.webIndex.seedDB != null) && (sb.webIndex.seedDB.sizeConnected() > 0)) {
            Iterator<yacySeed> e = sb.webIndex.peerActions.dhtAction.getAcceptRemoteIndexSeeds("------------");
            TreeMap<String, String> hostList = new TreeMap<String, String>();
            while (e.hasNext()) {
                seed = e.next();
                if (seed != null) hostList.put(seed.get(yacySeed.NAME, "nameless"), seed.hash);
            }
            
            String hostName = null;
            try {
                while ((hostName = hostList.firstKey()) != null) {
                    prop.put("running_hosts_" + hc + "_hosthash", hostList.get(hostName));
                    prop.putHTML("running_hosts_" + hc + "_hostname", /*seed.hash + " " +*/ hostName);
                    hc++;                
                    hostList.remove(hostName);
                }
            } catch (NoSuchElementException ex) {}
            prop.put("running_hosts", hc);
        } else {
            prop.put("running_hosts", "0");
        }

        prop.put("date",(new Date()).toString());
        return prop;
    }
}
