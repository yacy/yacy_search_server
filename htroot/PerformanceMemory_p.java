//PerformaceMemory_p.java
//-----------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
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

//You must compile this file with
//javac -classpath .:../classes PerformanceMemory_p.java
//if the shell's current path is HTROOT

//import java.util.Iterator;
import java.io.File;
import java.util.Iterator;
import java.util.Map;

import de.anomic.http.httpRequestHeader;
import de.anomic.kelondro.index.Cache;
import de.anomic.kelondro.table.EcoTable;
import de.anomic.kelondro.util.MemoryControl;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDomains;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.Formatter;

public class PerformanceMemory_p {
    
    private static final long KB = 1024;
    private static final long MB = 1024 * KB;
    private static Map<String, String> defaultSettings = null;
        
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        if (defaultSettings == null) {
            defaultSettings = FileUtils.loadMap(new File(env.getRootPath(), "defaults/yacy.init"));
        }
        
        prop.put("gc", "0");
        if (post != null) {
            if (post.containsKey("gc")) {
                System.gc();
                prop.put("gc", "1");
            }
        }
        
        final long memoryFreeNow = MemoryControl.free();
        final long memoryFreeAfterInitBGC = Long.parseLong(env.getConfig("memoryFreeAfterInitBGC", "0"));
        final long memoryFreeAfterInitAGC = Long.parseLong(env.getConfig("memoryFreeAfterInitAGC", "0"));
        final long memoryFreeAfterStartup = Long.parseLong(env.getConfig("memoryFreeAfterStartup", "0"));
        final long memoryTotalNow = MemoryControl.total();
        final long memoryTotalAfterInitBGC = Long.parseLong(env.getConfig("memoryTotalAfterInitBGC", "0"));
        final long memoryTotalAfterInitAGC = Long.parseLong(env.getConfig("memoryTotalAfterInitAGC", "0"));
        final long memoryTotalAfterStartup = Long.parseLong(env.getConfig("memoryTotalAfterStartup", "0"));
        
        prop.putNum("memoryMax", MemoryControl.maxMemory / MB);
        prop.putNum("memoryAvailAfterStartup", (MemoryControl.maxMemory - memoryTotalAfterStartup + memoryFreeAfterStartup) / MB);
        prop.putNum("memoryAvailAfterInitBGC", (MemoryControl.maxMemory - memoryTotalAfterInitBGC + memoryFreeAfterInitBGC) / MB);
        prop.putNum("memoryAvailAfterInitAGC", (MemoryControl.maxMemory - memoryTotalAfterInitAGC + memoryFreeAfterInitAGC) / MB);
        prop.putNum("memoryAvailNow", (MemoryControl.maxMemory - memoryTotalNow + memoryFreeNow) / MB);
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
        
        // write table for EcoTable index sizes
        Iterator<String> i = EcoTable.filenames();
        String filename;
        Map<String, String> map;
        int p, c = 0;
        long mem, totalmem = 0;
        while (i.hasNext()) {
            filename = i.next();
            map = EcoTable.memoryStats(filename);
            prop.put("EcoList_" + c + "_tableIndexPath", ((p = filename.indexOf("DATA")) < 0) ? filename : filename.substring(p));
            prop.putNum("EcoList_" + c + "_tableSize", map.get("tableSize"));
            
            mem = Long.parseLong(map.get("tableKeyMem"));
            totalmem += mem;
            prop.put("EcoList_" + c + "_tableKeyMem", Formatter.bytesToString(mem));
            prop.put("EcoList_" + c + "_tableKeyChunkSize", map.get("tableKeyChunkSize"));
            
            mem = Long.parseLong(map.get("tableValueMem"));
            totalmem += mem;
            prop.put("EcoList_" + c + "_tableValueMem", Formatter.bytesToString(mem));
            prop.put("EcoList_" + c + "_tableValueChunkSize", map.get("tableValueChunkSize"));
            
            c++;
        }
        prop.put("EcoList", c);
        prop.putNum("EcoIndexTotalMem", totalmem / (1024 * 1024d));
        
        // write object cache table
        i = Cache.filenames();
        c = 0;
        long hitmem, missmem, totalhitmem = 0, totalmissmem = 0;
        while (i.hasNext()) {
            filename = i.next();
            map = Cache.memoryStats(filename);
            prop.put("ObjectList_" + c + "_objectCachePath", ((p = filename.indexOf("DATA")) < 0) ? filename : filename.substring(p));
            
            // hit cache
            hitmem = Long.parseLong(map.get("objectHitMem"));
            totalhitmem += hitmem;
            prop.put("ObjectList_" + c + "_objectHitChunkSize", map.get("objectHitChunkSize"));
            prop.putNum("ObjectList_" + c + "_objectHitCacheCount", map.get("objectHitCacheCount"));
            prop.put("ObjectList_" + c + "_objectHitCacheMem", Formatter.bytesToString(hitmem));
            prop.putNum("ObjectList_" + c + "_objectHitCacheReadHit", map.get("objectHitCacheReadHit"));
            prop.putNum("ObjectList_" + c + "_objectHitCacheReadMiss", map.get("objectHitCacheReadMiss"));
            prop.putNum("ObjectList_" + c + "_objectHitCacheWriteUnique", map.get("objectHitCacheWriteUnique"));
            prop.putNum("ObjectList_" + c + "_objectHitCacheWriteDouble", map.get("objectHitCacheWriteDouble"));
            prop.putNum("ObjectList_" + c + "_objectHitCacheDeletes", map.get("objectHitCacheDeletes"));
            prop.putNum("ObjectList_" + c + "_objectHitCacheFlushes", map.get("objectHitCacheFlushes"));
            
            // miss cache
            missmem = Long.parseLong(map.get("objectMissMem"));
            totalmissmem += missmem;
            prop.put("ObjectList_" + c + "_objectMissChunkSize", map.get("objectMissChunkSize"));
            prop.putNum("ObjectList_" + c + "_objectMissCacheCount", map.get("objectMissCacheCount"));
            prop.putHTML("ObjectList_" + c + "_objectMissCacheMem", Formatter.bytesToString(missmem));
            prop.putNum("ObjectList_" + c + "_objectMissCacheReadHit", map.get("objectMissCacheReadHit"));
            prop.putNum("ObjectList_" + c + "_objectMissCacheReadMiss", map.get("objectMissCacheReadMiss"));
            prop.putNum("ObjectList_" + c + "_objectMissCacheWriteUnique", map.get("objectMissCacheWriteUnique"));
            prop.putNum("ObjectList_" + c + "_objectMissCacheWriteDouble", map.get("objectMissCacheWriteDouble"));
            prop.putNum("ObjectList_" + c + "_objectMissCacheDeletes", map.get("objectMissCacheDeletes"));
            //prop.put("ObjectList_" + c + "_objectMissCacheFlushes", map.get("objectMissCacheFlushes"));
            
            c++;
        }
        prop.put("ObjectList", c);
        prop.putNum("objectCacheStopGrow", Cache.getMemStopGrow() / (1024 * 1024d));
        prop.putNum("objectCacheStartShrink", Cache.getMemStartShrink() / (1024 * 1024d));
        prop.putNum("objectHitCacheTotalMem", totalhitmem / (1024 * 1024d));
        prop.putNum("objectMissCacheTotalMem", totalmissmem / (1024 * 1024d));
        
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
