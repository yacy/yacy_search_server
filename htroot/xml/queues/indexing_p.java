// /xml.queues/indexing_p.java
// -------------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last major change: 28.10.2005
// this file is contributed by Alexander Schier
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

// You must compile this file with
// javac -classpath .:../classes IndexCreate_p.java
// if the shell's current path is HTROOT

package xml.queues;

import java.util.ArrayList;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.io.IOException;

import de.anomic.data.wikiCode;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSwitchboardQueue;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class indexing_p {
    
    private static SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
    private static String daydate(Date date) {
        if (date == null) return "";
        return dayFormatter.format(date);
    }
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        //wikiCode wikiTransformer = new wikiCode(switchboard);
        serverObjects prop = new serverObjects();
        prop.put("rejected", 0);
        //int showRejectedCount = 10;
        
        yacySeed initiator;
        
        if ((switchboard.sbQueue.size() == 0) && (switchboard.indexingTasksInProcess.size() == 0)) {
            prop.put("list", 0); //is empty
        } else {
            plasmaSwitchboardQueue.Entry pcentry;
            int inProcessCount = 0;
            long totalSize = 0;
            int i=0; //counter
            try {
                ArrayList entryList = new ArrayList();
                
                // getting all entries that are currently in process
                synchronized (switchboard.indexingTasksInProcess) {
                    inProcessCount = switchboard.indexingTasksInProcess.size();
                    entryList.addAll(switchboard.indexingTasksInProcess.values());
                }
                
                // getting all enqueued entries
                if ((switchboard.sbQueue.size() > 0)) {
                    entryList.addAll(switchboard.sbQueue.list(0));
                }
                int size=10;
                if (post!= null) size = post.getInt("num", 10);
                
                if(size>entryList.size()){
                    size=entryList.size();
                }
                
                for (i = 0; i < size; i++) {
                    boolean inProcess = i < inProcessCount;
                    pcentry = (plasmaSwitchboardQueue.Entry) entryList.get(i);
                    long entrySize = pcentry.size();
                    totalSize += entrySize;
                    if ((pcentry != null)&&(pcentry.url() != null)) {
                        initiator = yacyCore.seedDB.getConnected(pcentry.initiator());
                        prop.put("list_"+i+"_initiator", ((initiator == null) ? "proxy" : wikiCode.replaceHTML(initiator.getName())));
                        prop.put("list_"+i+"_depth", pcentry.depth());
                        prop.put("list_"+i+"_modified", (pcentry.responseHeader() == null) ? "" : daydate(pcentry.responseHeader().lastModified()));
                        prop.put("list_"+i+"_anchor", (pcentry.anchorName()==null)?"":wikiCode.replaceHTML(pcentry.anchorName()));
                        prop.put("list_"+i+"_url", wikiCode.replaceHTML(pcentry.normalizedURLString()));
                        prop.put("list_"+i+"_size", entrySize);
                        prop.put("list_"+i+"_inProcess", (inProcess)?1:0);
                        prop.put("list_"+i+"_hash", pcentry.urlHash());
                    }
                }
                prop.put("list", i);
            } catch (IOException e) {}
        }

        // return rewrite properties
        return prop;
    }
    
}



