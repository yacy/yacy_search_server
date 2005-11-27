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

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.io.IOException;

import de.anomic.data.wikiCode;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaCrawlEURL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSwitchboardQueue;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class IndexCreateIndexingQueue_p {
    
    private static SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
    private static String daydate(Date date) {
        if (date == null) return ""; else return dayFormatter.format(date);
    }
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        prop.put("rejected", 0);
        int showRejectedCount = 10;
        
        if (post != null) {
            if (post.containsKey("clearRejected")) {
                switchboard.urlPool.errorURL.clearStack();
            } 
            if (post.containsKey("moreRejected")) {
                showRejectedCount = Integer.parseInt(post.get("showRejected", "10"));
            }
            if (post.containsKey("clearIndexingQueue")) {
                try {
                    synchronized (switchboard.sbQueue) {
                        plasmaSwitchboardQueue.Entry entry = null;
                        while ((entry = switchboard.sbQueue.pop()) != null) {
                            if ((entry != null) && (entry.profile() != null) && (!(entry.profile().storeHTCache()))) {
                                switchboard.cacheManager.deleteFile(entry.url());
                            }                            
                        }
                    } 
                } catch (Exception e) {}
            } else if (post.containsKey("deleteEntry")) {
                String urlHash = (String) post.get("deleteEntry");
                try {
                    synchronized (switchboard.sbQueue) {
                        ArrayList entries = switchboard.sbQueue.list(0);
                        for (int i=entries.size()-1; i >= 0; i--) {
                            plasmaSwitchboardQueue.Entry pcentry = (plasmaSwitchboardQueue.Entry) entries.get(i);
                            if (pcentry.urlHash().equals(urlHash)) {
                                plasmaSwitchboardQueue.Entry entry = switchboard.sbQueue.remove(i);
                                if ((entry != null) && (entry.profile() != null) && (!(entry.profile().storeHTCache()))) {
                                    switchboard.cacheManager.deleteFile(entry.url());
                                }
                                break;
                            }
                        }
                    } 
                } catch (Exception e) {}
                prop.put("LOCATION","");
                return prop;
            }
        }

        yacySeed initiator;
        boolean dark;
        
        if ((switchboard.sbQueue.size() == 0) && (switchboard.indexingTasksInProcess.size() == 0)) {
            prop.put("indexing-queue", 0); //is empty
        } else {
            prop.put("indexing-queue", 1); // there are entries in the queue or in process
            
            dark = true;
            plasmaSwitchboardQueue.Entry pcentry;
            int inProcessCount = 0, entryCount = 0;
            long totalSize = 0;
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
                                
                int count=entryList.size();
                if(count>100)count=100;
                for (int i = 0; i < count; i++) {
                    boolean inProcess = i < inProcessCount;
                    pcentry = (plasmaSwitchboardQueue.Entry) entryList.get(i);
                    long entrySize = pcentry.size();
                    totalSize += entrySize;
                    if ((pcentry != null)&&(pcentry.url() != null)) {
                        initiator = yacyCore.seedDB.getConnected(pcentry.initiator());
                        prop.put("indexing-queue_list_"+entryCount+"_dark", (inProcess)? 2: ((dark) ? 1 : 0));
                        prop.put("indexing-queue_list_"+entryCount+"_initiator", ((initiator == null) ? "proxy" : wikiCode.replaceHTML(initiator.getName())));
                        prop.put("indexing-queue_list_"+entryCount+"_depth", pcentry.depth());
                        prop.put("indexing-queue_list_"+entryCount+"_modified", (pcentry.responseHeader() == null) ? "" : daydate(pcentry.responseHeader().lastModified()));
                        prop.put("indexing-queue_list_"+entryCount+"_anchor", (pcentry.anchorName()==null)?"":pcentry.anchorName());
                        prop.put("indexing-queue_list_"+entryCount+"_url", wikiCode.replaceHTML(pcentry.normalizedURLString()));
                        prop.put("indexing-queue_list_"+entryCount+"_size", bytesToString(entrySize));
                        prop.put("indexing-queue_list_"+entryCount+"_inProcess", (inProcess)?1:0);
                        prop.put("indexing-queue_list_"+entryCount+"_inProcess_hash", pcentry.urlHash());
                        dark = !dark;
                        entryCount++;
                    }
                }
            } catch (IOException e) {}

            prop.put("indexing-queue_num", entryCount);//num entries in queue
            prop.put("indexing-queue_totalSize", bytesToString(totalSize));//num entries in queue 
            prop.put("indexing-queue_list", entryCount);
        }
        
        // failure cases
        if (switchboard.urlPool.errorURL.stackSize() != 0) {
            if (showRejectedCount > switchboard.urlPool.errorURL.stackSize()) showRejectedCount = switchboard.urlPool.errorURL.stackSize();
            prop.put("rejected", 1);
            prop.put("rejected_num", switchboard.urlPool.errorURL.stackSize());
            if (showRejectedCount != switchboard.urlPool.errorURL.stackSize()) {
                prop.put("rejected_only-latest", 1);
                prop.put("rejected_only-latest_num", showRejectedCount);
                prop.put("rejected_only-latest_newnum", ((int) (showRejectedCount * 1.5)));
            }else{
                prop.put("rejected_only-latest", 0);
            }
            dark = true;
            String url, initiatorHash, executorHash;
            plasmaCrawlEURL.Entry entry;
            yacySeed initiatorSeed, executorSeed;
            int j=0;
            for ( int i = switchboard.urlPool.errorURL.stackSize() - 1; i >= (switchboard.urlPool.errorURL.stackSize() - showRejectedCount); i--) {
                entry = (plasmaCrawlEURL.Entry) switchboard.urlPool.errorURL.getStack(i);
                initiatorHash = entry.initiator();
                executorHash = entry.executor();
                url = entry.url().toString();
                initiatorSeed = yacyCore.seedDB.getConnected(initiatorHash);
                executorSeed = yacyCore.seedDB.getConnected(executorHash);
                prop.put("rejected_list_"+j+"_initiator", ((initiatorSeed == null) ? "proxy" : wikiCode.replaceHTML(initiatorSeed.getName())));
                prop.put("rejected_list_"+j+"_executor", ((executorSeed == null) ? "proxy" : wikiCode.replaceHTML(executorSeed.getName())));
                prop.put("rejected_list_"+j+"_url", wikiCode.replaceHTML(url));
                prop.put("rejected_list_"+j+"_failreason", entry.failreason());
                prop.put("rejected_list_"+j+"_dark", ((dark) ? 1 : 0));
                dark = !dark;
                j++;
            }
            prop.put("rejected_list", j);
        }

        // return rewrite properties
        return prop;
    }
    
    public static String bytesToString(long byteCount) {  
        try {
            StringBuffer byteString = new StringBuffer();
            
            DecimalFormat df = new DecimalFormat( "0.00" );
            if (byteCount > 1073741824) {                
                byteString.append(df.format((double)byteCount / (double)1073741824 ))
                          .append(" GB");
            } else if (byteCount > 1048576) {
                byteString.append(df.format((double)byteCount / (double)1048576))
                          .append(" MB");              
            } else if (byteCount > 1024) {
                byteString.append(df.format((double)byteCount /(double)1024))
                          .append(" KB");             
            } else {
                byteString.append(Long.toString(byteCount))
                .append(" Bytes");                
            }
            
            return byteString.toString();       
        } catch (Exception e) {
            return "unknown";
        }        
        
    }    
    
}



