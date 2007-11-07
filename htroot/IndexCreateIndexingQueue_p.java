// IndexCreateIndexingQueue_p.java
// -------------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last major change: 04.07.2005
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

import java.util.ArrayList;
import java.util.Iterator;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaCrawlZURL;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSwitchboardQueue;
import de.anomic.server.serverMemory;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public class IndexCreateIndexingQueue_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        prop.put("rejected", "0");
        int showRejectedCount = 100;
        
        int showLimit = 100;
        if (post != null) {
            if (post.containsKey("limit")) {
                try {
                    showLimit = Integer.valueOf((String)post.get("limit")).intValue();
                } catch (NumberFormatException e) {}
            }    
            
            if (post.containsKey("clearRejected")) {
                switchboard.crawlQueues.errorURL.clearStack();
            } 
            if (post.containsKey("moreRejected")) {
                showRejectedCount = post.getInt("showRejected", 10);
            }
            if (post.containsKey("clearIndexingQueue")) {
                try {
                    synchronized (switchboard.sbQueue) {
                        plasmaSwitchboardQueue.Entry entry = null;
                        while ((entry = switchboard.sbQueue.pop()) != null) {
                            if ((entry != null) && (entry.profile() != null) && (!(entry.profile().storeHTCache()))) {
                                plasmaHTCache.deleteURLfromCache(entry.url());
                            }                            
                        }
                        switchboard.sbQueue.clear(); // reset file to clean up content completely
                    } 
                } catch (Exception e) {}
            } else if (post.containsKey("deleteEntry")) {
                String urlHash = (String) post.get("deleteEntry");
                try {
                    switchboard.sbQueue.remove(urlHash);
                } catch (Exception e) {}
                prop.put("LOCATION","");
                return prop;
            }
        }

        yacySeed initiator;
        boolean dark;
        
        if ((switchboard.sbQueue.size() == 0) && (switchboard.indexingTasksInProcess.size() == 0)) {
            prop.put("indexing-queue", "0"); //is empty
        } else {
            prop.put("indexing-queue", "1"); // there are entries in the queue or in process
            
            dark = true;
            plasmaSwitchboardQueue.Entry pcentry;
            int inProcessCount = 0, entryCount = 0, totalCount = 0; 
            long totalSize = 0;
            ArrayList entryList = new ArrayList();
            
            // getting all entries that are currently in process
            synchronized (switchboard.indexingTasksInProcess) {
                inProcessCount = switchboard.indexingTasksInProcess.size();
                entryList.addAll(switchboard.indexingTasksInProcess.values());
            }
            
            // getting all enqueued entries
            if ((switchboard.sbQueue.size() > 0)) {
                Iterator i = switchboard.sbQueue.entryIterator(false);
                while (i.hasNext()) entryList.add((plasmaSwitchboardQueue.Entry) i.next());
            }
                            
            int count=entryList.size();
            totalCount = count;
            for (int i = 0; (i < count) && (entryCount < showLimit); i++) {

                boolean inProcess = i < inProcessCount;
                pcentry = (plasmaSwitchboardQueue.Entry) entryList.get(i);
                if ((pcentry != null)&&(pcentry.url() != null)) {
                    long entrySize = pcentry.size();
                    totalSize += entrySize;
                    initiator = yacyCore.seedDB.getConnected(pcentry.initiator());
                    prop.put("indexing-queue_list_"+entryCount+"_dark", inProcess ? "2" : (dark ? "1" : "0"));
                    prop.put("indexing-queue_list_"+entryCount+"_initiator", ((initiator == null) ? "proxy" : initiator.getName()));
                    prop.put("indexing-queue_list_"+entryCount+"_depth", pcentry.depth());
                    prop.put("indexing-queue_list_"+entryCount+"_modified", pcentry.getModificationDate());
                    prop.putHTML("indexing-queue_list_"+entryCount+"_anchor", (pcentry.anchorName()==null)?"":pcentry.anchorName());
                    prop.put("indexing-queue_list_"+entryCount+"_url", pcentry.url().toNormalform(false, true));
                    prop.put("indexing-queue_list_"+entryCount+"_size", serverMemory.bytesToString(entrySize));
                    prop.put("indexing-queue_list_"+entryCount+"_inProcess", inProcess ? "1" :"0");
                    prop.put("indexing-queue_list_"+entryCount+"_inProcess_hash", pcentry.urlHash());
                    dark = !dark;
                    entryCount++;
                }
            }
            
            prop.putNum("indexing-queue_show", entryCount);//show shown entries
            prop.putNum("indexing-queue_num", totalCount); //num entries in queue 
            prop.put("indexing-queue_totalSize", serverMemory.bytesToString(totalSize));//num entries in queue 
            prop.putNum("indexing-queue_list", entryCount);
        }
        
        // failure cases
        if (switchboard.crawlQueues.errorURL.stackSize() != 0) {
            if (showRejectedCount > switchboard.crawlQueues.errorURL.stackSize()) showRejectedCount = switchboard.crawlQueues.errorURL.stackSize();
            prop.put("rejected", "1");
            prop.putNum("rejected_num", switchboard.crawlQueues.errorURL.stackSize());
            if (showRejectedCount != switchboard.crawlQueues.errorURL.stackSize()) {
                prop.put("rejected_only-latest", "1");
                prop.putNum("rejected_only-latest_num", showRejectedCount);
                prop.put("rejected_only-latest_newnum", ((int) (showRejectedCount * 1.5)));
            }else{
                prop.put("rejected_only-latest", "0");
            }
            dark = true;
            yacyURL url; 
            String initiatorHash, executorHash;
            plasmaCrawlZURL.Entry entry;
            yacySeed initiatorSeed, executorSeed;
            int j=0;
            for (int i = switchboard.crawlQueues.errorURL.stackSize() - 1; i >= (switchboard.crawlQueues.errorURL.stackSize() - showRejectedCount); i--) {
                    entry = switchboard.crawlQueues.errorURL.top(i);
                    url = entry.url();
                    if (url == null) continue;
                    
                    initiatorHash = entry.initiator();
                    executorHash = entry.executor();
                    initiatorSeed = yacyCore.seedDB.getConnected(initiatorHash);
                    executorSeed = yacyCore.seedDB.getConnected(executorHash);
                    prop.put("rejected_list_"+j+"_initiator", ((initiatorSeed == null) ? "proxy" : initiatorSeed.getName()));
                    prop.put("rejected_list_"+j+"_executor", ((executorSeed == null) ? "proxy" : executorSeed.getName()));
                    prop.put("rejected_list_"+j+"_url", url.toNormalform(false, true));
                    prop.putHTML("rejected_list_"+j+"_failreason", entry.anycause());
                    prop.put("rejected_list_"+j+"_dark", dark ? "1" : "0");
                    dark = !dark;
                    j++;
            }
            prop.put("rejected_list", j);
        }

        // return rewrite properties
        return prop;
    }
}
