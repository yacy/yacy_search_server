//PerformaceMemory_p.java
//-----------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//last major change: 19.09.2005
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
//javac -classpath .:../classes PerformanceMemory_p.java
//if the shell's current path is HTROOT

//import java.util.Iterator;
import java.io.File;
import java.util.Iterator;
import java.util.Map;

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroCache;
import de.anomic.kelondro.kelondroCachedRecords;
import de.anomic.kelondro.kelondroFlexTable;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDomains;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverMemory;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class PerformanceMemory_p {
    
    private static final long KB = 1024;
    private static final long MB = 1024 * KB;
    private static Map defaultSettings = null;
        
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();
        if (defaultSettings == null) {
            defaultSettings = serverFileUtils.loadHashMap(new File(env.getRootPath(), "yacy.init"));
        }
        prop.put("gc", "0");
        if (post != null) {
            int xmx = 96; // default maximum heap size
            if (post.containsKey("Xmx")) {
                try { xmx = Integer.valueOf(post.get("Xmx", "64")).intValue(); } catch (NumberFormatException e){}
                env.setConfig("javastart_Xmx", "Xmx" + xmx + "m");
                env.setConfig("javastart_Xms", "Xms" + xmx + "m");
                prop.put("setStartupCommit", "1");
            }
            if (post.containsKey("gc")) {
                System.gc();
                prop.put("gc", "1");
            }
        }
        
        long memoryFreeNow = Runtime.getRuntime().freeMemory();
        long memoryFreeAfterInitBGC = Long.parseLong(env.getConfig("memoryFreeAfterInitBGC", "0"));
        long memoryFreeAfterInitAGC = Long.parseLong(env.getConfig("memoryFreeAfterInitAGC", "0"));
        long memoryFreeAfterStartup = Long.parseLong(env.getConfig("memoryFreeAfterStartup", "0"));
        long memoryTotalNow = Runtime.getRuntime().totalMemory();
        long memoryTotalAfterInitBGC = Long.parseLong(env.getConfig("memoryTotalAfterInitBGC", "0"));
        long memoryTotalAfterInitAGC = Long.parseLong(env.getConfig("memoryTotalAfterInitAGC", "0"));
        long memoryTotalAfterStartup = Long.parseLong(env.getConfig("memoryTotalAfterStartup", "0"));
        long memoryMax = Runtime.getRuntime().maxMemory();
        
        prop.putNum("memoryMax", memoryMax / MB);
        prop.putNum("memoryAvailAfterStartup", (memoryMax - memoryTotalAfterStartup + memoryFreeAfterStartup) / MB);
        prop.putNum("memoryAvailAfterInitBGC", (memoryMax - memoryTotalAfterInitBGC + memoryFreeAfterInitBGC) / MB);
        prop.putNum("memoryAvailAfterInitAGC", (memoryMax - memoryTotalAfterInitAGC + memoryFreeAfterInitAGC) / MB);
        prop.putNum("memoryAvailNow", (memoryMax - memoryTotalNow + memoryFreeNow) / MB);
        prop.putNum("memoryTotalAfterStartup", memoryTotalAfterStartup / KB);
        prop.putNum("memoryTotalAfterInitBGC", memoryTotalAfterInitBGC / KB);
        prop.putNum("memoryTotalAfterInitAGC", memoryTotalAfterInitAGC / KB);
        prop.putNum("memoryTotalNow", memoryTotalNow / MB);
        prop.putNum("memoryFreeAfterStartup", memoryFreeAfterStartup / KB);
        prop.putNum("memoryFreeAfterInitBGC", memoryFreeAfterInitBGC / KB);
        prop.putNum("memoryFreeAfterInitAGC", memoryFreeAfterInitAGC / KB);
        prop.putNum("memoryFreeNow", memoryFreeNow / MB);
        prop.putNum("memoryUsedAfterStartup", (memoryTotalAfterStartup - memoryFreeAfterStartup) / KB);
        prop.putNum("memoryUsedAfterInitBGC", (memoryTotalAfterInitBGC - memoryFreeAfterInitBGC) / KB);
        prop.putNum("memoryUsedAfterInitAGC", (memoryTotalAfterInitAGC - memoryFreeAfterInitAGC) / KB);
        prop.putNum("memoryUsedNow", (memoryTotalNow - memoryFreeNow) / MB);
        
        // write table for FlexTable index sizes
        Iterator i = kelondroFlexTable.filenames();
        String filename;
        Map<String, String> map;
        int p, c = 0;
        long mem, totalmem = 0;
        while (i.hasNext()) {
            filename = (String) i.next();
            map = kelondroFlexTable.memoryStats(filename);
            mem = Long.parseLong((String) map.get("tableIndexMem"));
            totalmem += mem;
            prop.put("TableList_" + c + "_tableIndexPath", ((p = filename.indexOf("DATA")) < 0) ? filename : filename.substring(p));
            prop.put("TableList_" + c + "_tableIndexChunkSize", map.get("tableIndexChunkSize"));
            prop.putNum("TableList_" + c + "_tableIndexCount", (String)map.get("tableIndexCount"));
            prop.put("TableList_" + c + "_tableIndexMem", serverMemory.bytesToString(mem));
            c++;
        }
        prop.put("TableList", c);
        prop.putNum("TableIndexTotalMem", totalmem / (1024 * 1024d));
        
        // write node cache table
        i = kelondroCachedRecords.filenames();
        c = 0;
        totalmem = 0;
        while (i.hasNext()) {
            filename = (String) i.next();
            map = kelondroCachedRecords.memoryStats(filename);
            mem = Long.parseLong((String) map.get("nodeCacheMem"));
            totalmem += mem;
            prop.put("NodeList_" + c + "_nodeCachePath", ((p = filename.indexOf("DATA")) < 0) ? filename : filename.substring(p));
            prop.put("NodeList_" + c + "_nodeChunkSize", map.get("nodeChunkSize"));
            prop.putNum("NodeList_" + c + "_nodeCacheCount", (String)map.get("nodeCacheCount"));
            prop.put("NodeList_" + c + "_nodeCacheMem", serverMemory.bytesToString(mem));
            prop.putNum("NodeList_" + c + "_nodeCacheReadHit", (String)map.get("nodeCacheReadHit"));
            prop.putNum("NodeList_" + c + "_nodeCacheReadMiss", (String)map.get("nodeCacheReadMiss"));
            prop.putNum("NodeList_" + c + "_nodeCacheWriteUnique", (String)map.get("nodeCacheWriteUnique"));
            prop.putNum("NodeList_" + c + "_nodeCacheWriteDouble", (String)map.get("nodeCacheWriteDouble"));
            prop.putNum("NodeList_" + c + "_nodeCacheDeletes", (String)map.get("nodeCacheDeletes"));
            prop.putNum("NodeList_" + c + "_nodeCacheFlushes", (String)map.get("nodeCacheFlushes"));
            c++;
        }
        prop.put("NodeList", c);
        prop.putNum("nodeCacheStopGrow", kelondroCachedRecords.getMemStopGrow() / (1024 * 1024d));
        prop.putNum("nodeCacheStartShrink", kelondroCachedRecords.getMemStartShrink() / (1024 * 1024d));
        prop.putNum("nodeCacheTotalMem", totalmem / (1024 * 1024d));
        
        // write object cache table
        i = kelondroCache.filenames();
        c = 0;
        long hitmem, missmem, totalhitmem = 0, totalmissmem = 0;
        while (i.hasNext()) {
            filename = (String) i.next();
            map = kelondroCache.memoryStats(filename);
            prop.put("ObjectList_" + c + "_objectCachePath", ((p = filename.indexOf("DATA")) < 0) ? filename : filename.substring(p));
            
            // hit cache
            hitmem = Long.parseLong((String) map.get("objectHitMem"));
            totalhitmem += hitmem;
            prop.put("ObjectList_" + c + "_objectHitChunkSize", map.get("objectHitChunkSize"));
            prop.putNum("ObjectList_" + c + "_objectHitCacheCount", (String)map.get("objectHitCacheCount"));
            prop.put("ObjectList_" + c + "_objectHitCacheMem", serverMemory.bytesToString(hitmem));
            prop.putNum("ObjectList_" + c + "_objectHitCacheReadHit", (String)map.get("objectHitCacheReadHit"));
            prop.putNum("ObjectList_" + c + "_objectHitCacheReadMiss", (String)map.get("objectHitCacheReadMiss"));
            prop.putNum("ObjectList_" + c + "_objectHitCacheWriteUnique", (String)map.get("objectHitCacheWriteUnique"));
            prop.putNum("ObjectList_" + c + "_objectHitCacheWriteDouble", (String)map.get("objectHitCacheWriteDouble"));
            prop.putNum("ObjectList_" + c + "_objectHitCacheDeletes", (String)map.get("objectHitCacheDeletes"));
            prop.putNum("ObjectList_" + c + "_objectHitCacheFlushes", (String)map.get("objectHitCacheFlushes"));
            
            // miss cache
            missmem = Long.parseLong((String) map.get("objectMissMem"));
            totalmissmem += missmem;
            prop.put("ObjectList_" + c + "_objectMissChunkSize", map.get("objectMissChunkSize"));
            prop.putNum("ObjectList_" + c + "_objectMissCacheCount", (String)map.get("objectMissCacheCount"));
            prop.putHTML("ObjectList_" + c + "_objectMissCacheMem", serverMemory.bytesToString(missmem));
            prop.putNum("ObjectList_" + c + "_objectMissCacheReadHit", (String)map.get("objectMissCacheReadHit"));
            prop.putNum("ObjectList_" + c + "_objectMissCacheReadMiss", (String)map.get("objectMissCacheReadMiss"));
            prop.putNum("ObjectList_" + c + "_objectMissCacheWriteUnique", (String)map.get("objectMissCacheWriteUnique"));
            prop.putNum("ObjectList_" + c + "_objectMissCacheWriteDouble", (String)map.get("objectMissCacheWriteDouble"));
            prop.putNum("ObjectList_" + c + "_objectMissCacheDeletes", (String)map.get("objectMissCacheDeletes"));
            //prop.put("ObjectList_" + c + "_objectMissCacheFlushes", map.get("objectMissCacheFlushes"));
            
            c++;
        }
        prop.put("ObjectList", c);
        prop.putNum("objectCacheStopGrow", kelondroCache.getMemStopGrow() / (1024 * 1024d));
        prop.putNum("objectCacheStartShrink", kelondroCache.getMemStartShrink() / (1024 * 1024d));
        prop.putNum("objectHitCacheTotalMem", totalhitmem / (1024 * 1024d));
        prop.putNum("objectMissCacheTotalMem", totalmissmem / (1024 * 1024d));

        // parse initialization memory settings
        String Xmx = env.getConfig("javastart_Xmx", "Xmx96m").substring(3);
        prop.put("Xmx", Xmx.substring(0, Xmx.length() - 1));
        String Xms = env.getConfig("javastart_Xms", "Xms96m").substring(3);
        prop.put("Xms", Xms.substring(0, Xms.length() - 1));
        
        // other caching structures
        long amount = serverDomains.nameCacheHitSize();
        prop.putNum("namecache.hit", amount);
        amount = serverDomains.nameCacheNoCachingListSize();
        prop.putNum("namecache.noCache", amount);
        amount = plasmaSwitchboard.urlBlacklist.blacklistCacheSize();
        prop.putNum("blacklistcache.size", amount);
        // return rewrite values for templates
        return prop;
    }
}
