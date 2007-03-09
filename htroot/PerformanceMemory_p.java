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
import de.anomic.http.httpc;
import de.anomic.kelondro.kelondroCache;
import de.anomic.kelondro.kelondroFlexTable;
import de.anomic.kelondro.kelondroRecords;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverMemory;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
//import de.anomic.kelondro.kelondroObjectSpace;

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
        prop.put("gc", 0);
        if (post != null) {
            int xmx = 96; // default maximum heap size
            if (post.containsKey("Xmx")) {
                try { xmx = Integer.valueOf(post.get("Xmx", "64")).intValue(); } catch (NumberFormatException e){}
                env.setConfig("javastart_Xmx", "Xmx" + xmx + "m");
                env.setConfig("javastart_Xms", "Xms" + xmx + "m");
                prop.put("setStartupCommit", 1);
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
        long memoryMax = serverMemory.max;
        
        prop.put("memoryMax", memoryMax / MB);
        prop.put("memoryAvailAfterStartup", (memoryMax - memoryTotalAfterStartup + memoryFreeAfterStartup) / MB);
        prop.put("memoryAvailAfterInitBGC", (memoryMax - memoryTotalAfterInitBGC + memoryFreeAfterInitBGC) / MB);
        prop.put("memoryAvailAfterInitAGC", (memoryMax - memoryTotalAfterInitAGC + memoryFreeAfterInitAGC) / MB);
        prop.put("memoryAvailNow", (memoryMax - memoryTotalNow + memoryFreeNow) / MB);
        prop.put("memoryTotalAfterStartup", memoryTotalAfterStartup / KB);
        prop.put("memoryTotalAfterInitBGC", memoryTotalAfterInitBGC / KB);
        prop.put("memoryTotalAfterInitAGC", memoryTotalAfterInitAGC / KB);
        prop.put("memoryTotalNow", memoryTotalNow / MB);
        prop.put("memoryFreeAfterStartup", memoryFreeAfterStartup / KB);
        prop.put("memoryFreeAfterInitBGC", memoryFreeAfterInitBGC / KB);
        prop.put("memoryFreeAfterInitAGC", memoryFreeAfterInitAGC / KB);
        prop.put("memoryFreeNow", memoryFreeNow / MB);
        prop.put("memoryUsedAfterStartup", (memoryTotalAfterStartup - memoryFreeAfterStartup) / KB);
        prop.put("memoryUsedAfterInitBGC", (memoryTotalAfterInitBGC - memoryFreeAfterInitBGC) / KB);
        prop.put("memoryUsedAfterInitAGC", (memoryTotalAfterInitAGC - memoryFreeAfterInitAGC) / KB);
        prop.put("memoryUsedNow", (memoryTotalNow - memoryFreeNow) / MB);
        
        // write table for FlexTable index sizes
        Iterator i = kelondroFlexTable.filenames();
        String filename;
        Map map;
        int p, c = 0;
        long ROmem, RWmem, totalmem = 0;
        while (i.hasNext()) {
            filename = (String) i.next();
            map = (Map) kelondroFlexTable.memoryStats(filename);
            ROmem = Long.parseLong((String) map.get("tableROIndexMem"));
            RWmem = Long.parseLong((String) map.get("tableRWIndexMem"));
            totalmem += ROmem;
            totalmem += RWmem;
            prop.put("TableList_" + c + "_tableIndexPath", ((p = filename.indexOf("DATA")) < 0) ? filename : filename.substring(p));
            prop.put("TableList_" + c + "_tableIndexChunkSize", map.get("tableIndexChunkSize"));
            prop.put("TableList_" + c + "_tableROIndexCount", map.get("tableROIndexCount"));
            prop.put("TableList_" + c + "_tableROIndexMem", ROmem / (1024 * 1024));
            prop.put("TableList_" + c + "_tableRWIndexCount", map.get("tableRWIndexCount"));
            prop.put("TableList_" + c + "_tableRWIndexMem", RWmem / (1024 * 1024));
            c++;
        }
        prop.put("TableList", c);
        prop.put("TableIndexTotalMem", totalmem / (1024 * 1024));
        
        // write node cache table
        i = kelondroRecords.filenames();
        c = 0;
        totalmem = 0;
        long mem;
        while (i.hasNext()) {
            filename = (String) i.next();
            map = (Map) kelondroRecords.memoryStats(filename);
            mem = Long.parseLong((String) map.get("nodeCacheMem"));
            totalmem += mem;
            prop.put("NodeList_" + c + "_nodeCachePath", ((p = filename.indexOf("DATA")) < 0) ? filename : filename.substring(p));
            prop.put("NodeList_" + c + "_nodeChunkSize", map.get("nodeChunkSize"));
            prop.put("NodeList_" + c + "_nodeCacheCount", map.get("nodeCacheCount"));
            prop.put("NodeList_" + c + "_nodeCacheMem", mem / (1024 * 1024));
            prop.put("NodeList_" + c + "_nodeCacheReadHit", map.get("nodeCacheReadHit"));
            prop.put("NodeList_" + c + "_nodeCacheReadMiss", map.get("nodeCacheReadMiss"));
            prop.put("NodeList_" + c + "_nodeCacheWriteUnique", map.get("nodeCacheWriteUnique"));
            prop.put("NodeList_" + c + "_nodeCacheWriteDouble", map.get("nodeCacheWriteDouble"));
            prop.put("NodeList_" + c + "_nodeCacheDeletes", map.get("nodeCacheDeletes"));
            prop.put("NodeList_" + c + "_nodeCacheFlushes", map.get("nodeCacheFlushes"));
            c++;
        }
        prop.put("NodeList", c);
        prop.put("nodeCacheStopGrow", kelondroRecords.getMemStopGrow() / (1024 * 1024));
        prop.put("nodeCacheStartShrink", kelondroRecords.getMemStartShrink() / (1024 * 1024));
        prop.put("nodeCacheTotalMem", totalmem / (1024 * 1024));
        
        // write object cache table
        i = kelondroCache.filenames();
        c = 0;
        long hitmem, missmem, totalhitmem = 0, totalmissmem = 0;
        while (i.hasNext()) {
            filename = (String) i.next();
            map = (Map) kelondroCache.memoryStats(filename);
            prop.put("ObjectList_" + c + "_objectCachePath", ((p = filename.indexOf("DATA")) < 0) ? filename : filename.substring(p));
            
            // hit cache
            hitmem = Long.parseLong((String) map.get("objectHitMem"));
            totalhitmem += hitmem;
            prop.put("ObjectList_" + c + "_objectHitChunkSize", map.get("objectHitChunkSize"));
            prop.put("ObjectList_" + c + "_objectHitCacheCount", map.get("objectHitCacheCount"));
            prop.put("ObjectList_" + c + "_objectHitCacheMem", hitmem / (1024 * 1024));
            prop.put("ObjectList_" + c + "_objectHitCacheReadHit", map.get("objectHitCacheReadHit"));
            prop.put("ObjectList_" + c + "_objectHitCacheReadMiss", map.get("objectHitCacheReadMiss"));
            prop.put("ObjectList_" + c + "_objectHitCacheWriteUnique", map.get("objectHitCacheWriteUnique"));
            prop.put("ObjectList_" + c + "_objectHitCacheWriteDouble", map.get("objectHitCacheWriteDouble"));
            prop.put("ObjectList_" + c + "_objectHitCacheDeletes", map.get("objectHitCacheDeletes"));
            prop.put("ObjectList_" + c + "_objectHitCacheFlushes", map.get("objectHitCacheFlushes"));
            
            // miss cache
            missmem = Long.parseLong((String) map.get("objectMissMem"));
            totalmissmem += missmem;
            prop.put("ObjectList_" + c + "_objectMissChunkSize", map.get("objectMissChunkSize"));
            prop.put("ObjectList_" + c + "_objectMissCacheCount", map.get("objectMissCacheCount"));
            prop.put("ObjectList_" + c + "_objectMissCacheMem", missmem / (1024 * 1024));
            prop.put("ObjectList_" + c + "_objectMissCacheReadHit", map.get("objectMissCacheReadHit"));
            prop.put("ObjectList_" + c + "_objectMissCacheReadMiss", map.get("objectMissCacheReadMiss"));
            prop.put("ObjectList_" + c + "_objectMissCacheWriteUnique", map.get("objectMissCacheWriteUnique"));
            prop.put("ObjectList_" + c + "_objectMissCacheWriteDouble", map.get("objectMissCacheWriteDouble"));
            prop.put("ObjectList_" + c + "_objectMissCacheDeletes", map.get("objectMissCacheDeletes"));
            prop.put("ObjectList_" + c + "_objectMissCacheFlushes", map.get("objectMissCacheFlushes"));
            
            c++;
        }
        prop.put("ObjectList", c);
        prop.put("objectCacheStopGrow", kelondroCache.getMemStopGrow() / (1024 * 1024));
        prop.put("objectCacheStartShrink", kelondroCache.getMemStartShrink() / (1024 * 1024));
        prop.put("objectHitCacheTotalMem", totalhitmem / (1024 * 1024));
        prop.put("objectMissCacheTotalMem", totalmissmem / (1024 * 1024));

        // parse initialization memory settings
        String Xmx = env.getConfig("javastart_Xmx", "Xmx64m").substring(3);
        prop.put("Xmx", Xmx.substring(0, Xmx.length() - 1));
        String Xms = env.getConfig("javastart_Xms", "Xms10m").substring(3);
        prop.put("Xms", Xms.substring(0, Xms.length() - 1));
        
        // other caching structures
        long amount = httpc.nameCacheHitSize();
        prop.put("namecache.hit",Long.toString(amount));
        amount = httpc.nameCacheNoCachingListSize();
        prop.put("namecache.noCache",Long.toString(amount));
        amount = plasmaSwitchboard.urlBlacklist.blacklistCacheSize();
        prop.put("blacklistcache.size",Long.toString(amount));
        // return rewrite values for templates
        return prop;
    }
}
