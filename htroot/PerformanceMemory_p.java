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
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;

import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.index.Cache;
import net.yacy.kelondro.index.RAMIndex;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.Formatter;
import net.yacy.kelondro.util.MemoryControl;

import de.anomic.search.SearchEventCache;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class PerformanceMemory_p {
    
    private static final long KB = 1024;
    private static final long MB = 1024 * KB;
    private static Map<String, String> defaultSettings = null;
        
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        if (defaultSettings == null) {
            defaultSettings = FileUtils.loadMap(new File(env.getAppPath(), "defaults/yacy.init"));
        }
        
        prop.put("gc", "0");
        if (post != null) {
            if (post.containsKey("gc")) {
                System.gc();
                prop.put("gc", "1");
            }
        }
        
        final long memoryFreeNow = MemoryControl.free();
        final long memoryFreeAfterInitBGC = env.getConfigLong("memoryFreeAfterInitBGC", 0L);
        final long memoryFreeAfterInitAGC = env.getConfigLong("memoryFreeAfterInitAGC", 0L);
        final long memoryFreeAfterStartup = env.getConfigLong("memoryFreeAfterStartup", 0L);
        final long memoryTotalNow = MemoryControl.total();
        final long memoryTotalAfterInitBGC = env.getConfigLong("memoryTotalAfterInitBGC", 0L);
        final long memoryTotalAfterInitAGC = env.getConfigLong("memoryTotalAfterInitAGC", 0L);
        final long memoryTotalAfterStartup = env.getConfigLong("memoryTotalAfterStartup", 0L);
        
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
        
        // write table for Table index sizes
        Iterator<String> i = Table.filenames();
        String filename;
        Map<String, String> map;
        int p, c = 0;
        long mem, totalmem = 0;
        while (i.hasNext()) {
            filename = i.next();
            map = Table.memoryStats(filename);
            prop.put("EcoList_" + c + "_tableIndexPath", ((p = filename.indexOf("DATA")) < 0) ? filename : filename.substring(p));
            prop.putNum("EcoList_" + c + "_tableSize", map.get("tableSize"));

            assert map.get("tableKeyMem") != null : map;
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
        Iterator<Map.Entry<String, RAMIndex>> oi = RAMIndex.objects();
        c = 0;
        mem = 0;
        Map.Entry<String, RAMIndex> oie;
        RAMIndex cache;
        long hitmem, totalhitmem = 0;
        while (oi.hasNext()) {
            try {
                oie = oi.next();
            } catch (ConcurrentModificationException e) {
                break;
            }
            filename = oie.getKey();
            cache = oie.getValue();
            prop.put("indexcache_" + c + "_Name", ((p = filename.indexOf("DATA")) < 0) ? filename : filename.substring(p));
            
            hitmem = cache.mem();
            totalhitmem += hitmem;
            prop.put("indexcache_" + c + "_ChunkSize", cache.row().objectsize);
            prop.putNum("indexcache_" + c + "_Count", cache.size());
            prop.put("indexcache_" + c + "_NeededMem", cache.size() * cache.row().objectsize);
            prop.put("indexcache_" + c + "_UsedMem", hitmem);
            
            c++;
        }
        prop.put("indexcache", c);
        prop.putNum("indexcacheTotalMem", totalhitmem / (1024 * 1024d));
        
        // write object cache table
        i = Cache.filenames();
        c = 0;
        long missmem, totalmissmem = 0;
        totalhitmem = 0;
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
        prop.putNum("namecacheHit.size", Domains.nameCacheHitSize());
        prop.putNum("namecacheMiss.size", Domains.nameCacheMissSize());
        prop.putNum("namecache.noCache", 0);
        prop.putNum("blacklistcache.size", Switchboard.urlBlacklist.blacklistCacheSize());
        prop.putNum("searchevent.size", SearchEventCache.size());
        prop.putNum("searchevent.hit", SearchEventCache.cacheHit);
        prop.putNum("searchevent.miss", SearchEventCache.cacheMiss);
        prop.putNum("searchevent.insert", SearchEventCache.cacheInsert);
        prop.putNum("searchevent.delete", SearchEventCache.cacheDelete);
        // return rewrite values for templates
        return prop;
    }
}
