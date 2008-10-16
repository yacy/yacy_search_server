// IndexCreateIndexingQueue_p.java
// -------------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
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

// You must compile this file with
// javac -classpath .:../classes IndexCreate_p.java
// if the shell's current path is HTROOT

import java.util.ArrayList;
import java.util.Iterator;

import de.anomic.crawler.IndexingStack;
import de.anomic.crawler.ZURL;
import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverMemory;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public class IndexCreateIndexingQueue_p {
    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        prop.put("rejected", "0");
        int showRejectedCount = 100;
        
        int showLimit = 100;
        if (post != null) {
            if (post.containsKey("limit")) {
                try {
                    showLimit = Integer.valueOf(post.get("limit")).intValue();
                } catch (final NumberFormatException e) {}
            }    
            
            if (post.containsKey("clearRejected")) {
                sb.crawlQueues.errorURL.clearStack();
            } 
            if (post.containsKey("moreRejected")) {
                showRejectedCount = post.getInt("showRejected", 10);
            }
            if (post.containsKey("clearIndexingQueue")) {
                try {
                    synchronized (sb.webIndex.queuePreStack) {
                        IndexingStack.QueueEntry entry = null;
                        while ((entry = sb.webIndex.queuePreStack.pop()) != null) {
                            if ((entry != null) && (entry.profile() != null) && (!(entry.profile().storeHTCache()))) {
                                plasmaHTCache.deleteFromCache(entry.url());
                            }                            
                        }
                        sb.webIndex.queuePreStack.clear(); // reset file to clean up content completely
                    } 
                } catch (final Exception e) {}
            } else if (post.containsKey("deleteEntry")) {
                final String urlHash = post.get("deleteEntry");
                try {
                    sb.webIndex.queuePreStack.remove(urlHash);
                } catch (final Exception e) {}
                prop.put("LOCATION","");
                return prop;
            }
        }

        yacySeed initiator;
        boolean dark;
        
        if ((sb.webIndex.queuePreStack.size() == 0) && (sb.webIndex.queuePreStack.getActiveQueueSize() == 0)) {
            prop.put("indexing-queue", "0"); //is empty
        } else {
            prop.put("indexing-queue", "1"); // there are entries in the queue or in process
            
            dark = true;
            IndexingStack.QueueEntry pcentry;
            int entryCount = 0, totalCount = 0; 
            long totalSize = 0;
            
            // getting all entries that are currently in process
            final ArrayList<IndexingStack.QueueEntry> entryList = new ArrayList<IndexingStack.QueueEntry>();
            entryList.addAll(sb.webIndex.queuePreStack.getActiveQueueEntries());
            final int inProcessCount = entryList.size();
            
            // getting all enqueued entries
            if ((sb.webIndex.queuePreStack.size() > 0)) {
                final Iterator<IndexingStack.QueueEntry> i = sb.webIndex.queuePreStack.entryIterator(false);
                while (i.hasNext()) entryList.add(i.next());
            }
                            
            final int count=entryList.size();
            totalCount = count;
            for (int i = 0; (i < count) && (entryCount < showLimit); i++) {

                final boolean inProcess = i < inProcessCount;
                pcentry = entryList.get(i);
                if ((pcentry != null)&&(pcentry.url() != null)) {
                    final long entrySize = pcentry.size();
                    totalSize += entrySize;
                    initiator = sb.webIndex.seedDB.getConnected(pcentry.initiator());
                    prop.put("indexing-queue_list_"+entryCount+"_dark", inProcess ? "2" : (dark ? "1" : "0"));
                    prop.putHTML("indexing-queue_list_"+entryCount+"_initiator", ((initiator == null) ? "proxy" : initiator.getName()));
                    prop.put("indexing-queue_list_"+entryCount+"_depth", pcentry.depth());
                    prop.put("indexing-queue_list_"+entryCount+"_modified", pcentry.getModificationDate().toString());
                    prop.putHTML("indexing-queue_list_"+entryCount+"_anchor", (pcentry.anchorName()==null)?"":pcentry.anchorName());
                    prop.putHTML("indexing-queue_list_"+entryCount+"_url", pcentry.url().toNormalform(false, true));
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
        if (sb.crawlQueues.errorURL.stackSize() != 0) {
            if (showRejectedCount > sb.crawlQueues.errorURL.stackSize()) showRejectedCount = sb.crawlQueues.errorURL.stackSize();
            prop.put("rejected", "1");
            prop.putNum("rejected_num", sb.crawlQueues.errorURL.stackSize());
            if (showRejectedCount != sb.crawlQueues.errorURL.stackSize()) {
                prop.put("rejected_only-latest", "1");
                prop.putNum("rejected_only-latest_num", showRejectedCount);
                prop.put("rejected_only-latest_newnum", ((int) (showRejectedCount * 1.5)));
            }else{
                prop.put("rejected_only-latest", "0");
            }
            dark = true;
            yacyURL url; 
            String initiatorHash, executorHash;
            ZURL.Entry entry;
            yacySeed initiatorSeed, executorSeed;
            int j=0;
            for (int i = sb.crawlQueues.errorURL.stackSize() - 1; i >= (sb.crawlQueues.errorURL.stackSize() - showRejectedCount); i--) {
                    entry = sb.crawlQueues.errorURL.top(i);
                    if (entry == null) continue;
                    url = entry.url();
                    if (url == null) continue;
                    
                    initiatorHash = entry.initiator();
                    executorHash = entry.executor();
                    initiatorSeed = sb.webIndex.seedDB.getConnected(initiatorHash);
                    executorSeed = sb.webIndex.seedDB.getConnected(executorHash);
                    prop.putHTML("rejected_list_"+j+"_initiator", ((initiatorSeed == null) ? "proxy" : initiatorSeed.getName()));
                    prop.putHTML("rejected_list_"+j+"_executor", ((executorSeed == null) ? "proxy" : executorSeed.getName()));
                    prop.putHTML("rejected_list_"+j+"_url", url.toNormalform(false, true));
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
