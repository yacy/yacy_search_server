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
import java.util.TreeMap;

import org.apache.solr.core.SolrInfoMBean;
import org.apache.solr.search.SolrCache;

import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.index.Cache;
import net.yacy.kelondro.index.RAMIndex;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.Formatter;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.Switchboard;
import net.yacy.search.query.SearchEventCache;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class PerformanceMemory_p {

    private static final long KB = 1024;
    private static final long MB = 1024 * KB;
    private static Map<String, String> defaultSettings = null;
    
    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        Switchboard sb = (Switchboard) env;
        
        final serverObjects prop = new serverObjects();
        if (defaultSettings == null) {
            defaultSettings = FileUtils.loadMap(new File(env.getAppPath(), "defaults/yacy.init"));
        }

        prop.put("gc", "0");
        prop.put("autoreload.checked", "0");
        if (post != null) {
            if (post.containsKey("gc")) {
                System.gc();
                prop.put("gc", "1");
                prop.put("autoreload.checked", "1");
            } else {
                boolean simulatedshortmemory = post.containsKey("simulatedshortmemory");
                MemoryControl.setSimulatedShortStatus(simulatedshortmemory);
                if (simulatedshortmemory) prop.put("autoreload.checked", "1");
                final boolean std = post.containsKey("useStandardmemoryStrategy");
                env.setConfig("memory.standardStrategy", std);
                MemoryControl.setStandardStrategy(std);
            }
        }
        
        prop.put("simulatedshortmemory.checked", MemoryControl.getSimulatedShortStatus() ? 1 : 0);
        prop.put("useStandardmemoryStrategy.checked", env.getConfigBool("memory.standardStrategy", true) ? 1 : 0);
        prop.put("memoryStrategy", MemoryControl.getStrategyName());

        final long memoryFreeAfterInitBGC = env.getConfigLong("memoryFreeAfterInitBGC", 0L);
        final long memoryFreeAfterInitAGC = env.getConfigLong("memoryFreeAfterInitAGC", 0L);
        final long memoryFreeAfterStartup = env.getConfigLong("memoryFreeAfterStartup", 0L);
        final long memoryTotalAfterInitBGC = env.getConfigLong("memoryTotalAfterInitBGC", 0L);
        final long memoryTotalAfterInitAGC = env.getConfigLong("memoryTotalAfterInitAGC", 0L);
        final long memoryTotalAfterStartup = env.getConfigLong("memoryTotalAfterStartup", 0L);

        prop.putNum("memoryMax", MemoryControl.maxMemory() / MB);
        prop.putNum("memoryAvailAfterStartup", (MemoryControl.maxMemory() - memoryTotalAfterStartup + memoryFreeAfterStartup) / MB);
        prop.putNum("memoryAvailAfterInitBGC", (MemoryControl.maxMemory() - memoryTotalAfterInitBGC + memoryFreeAfterInitBGC) / MB);
        prop.putNum("memoryAvailAfterInitAGC", (MemoryControl.maxMemory() - memoryTotalAfterInitAGC + memoryFreeAfterInitAGC) / MB);
        prop.putNum("memoryAvailNow", MemoryControl.available() / MB);
        prop.putNum("memoryTotalAfterStartup", memoryTotalAfterStartup / KB);
        prop.putNum("memoryTotalAfterInitBGC", memoryTotalAfterInitBGC / KB);
        prop.putNum("memoryTotalAfterInitAGC", memoryTotalAfterInitAGC / KB);
        prop.putNum("memoryTotalNow", MemoryControl.total() / MB);
        prop.putNum("memoryFreeAfterStartup", memoryFreeAfterStartup / KB);
        prop.putNum("memoryFreeAfterInitBGC", memoryFreeAfterInitBGC / KB);
        prop.putNum("memoryFreeAfterInitAGC", memoryFreeAfterInitAGC / KB);
        prop.putNum("memoryFreeNow", MemoryControl.free() / MB);
        prop.putNum("memoryUsedAfterStartup", (memoryTotalAfterStartup - memoryFreeAfterStartup) / KB);
        prop.putNum("memoryUsedAfterInitBGC", (memoryTotalAfterInitBGC - memoryFreeAfterInitBGC) / KB);
        prop.putNum("memoryUsedAfterInitAGC", (memoryTotalAfterInitAGC - memoryFreeAfterInitAGC) / KB);
        prop.putNum("memoryUsedNow", MemoryControl.used() / MB);

        
        final Map<String, SolrInfoMBean> solrInfoMBeans = sb.index.fulltext().getSolrInfoBeans();
        final TreeMap<String, Map.Entry<String, SolrInfoMBean>> solrBeanOM = new TreeMap<String, Map.Entry<String, SolrInfoMBean>>();
        int c = 0;
        for (Map.Entry<String, SolrInfoMBean> sc: solrInfoMBeans.entrySet()) solrBeanOM.put(sc.getValue().getName() + "$" + sc.getKey() + "$" + c++, sc);
        c = 0;
        int scc = 0;
        for (Map.Entry<String, SolrInfoMBean> sc: solrBeanOM.values()) {
            prop.put("SolrList_" + c + "_class", sc.getValue().getName());
            prop.put("SolrList_" + c + "_type", sc.getKey());
            prop.put("SolrList_" + c + "_description", sc.getValue().getDescription());
            prop.put("SolrList_" + c + "_statistics", sc.getValue().getStatistics() == null ? "" : sc.getValue().getStatistics().toString().replaceAll(",", ", "));
            prop.put("SolrList_" + c + "_size", sc.getValue() instanceof SolrCache ? Integer.toString(((SolrCache<?,?>)sc.getValue()).size()) : "");
            if (sc.getValue() instanceof SolrCache) scc++;
            c++;
        }
        prop.put("SolrList", c);
        prop.put("SolrCacheCount", scc);
        
        // write table for Table index sizes
        Iterator<String> i = Table.filenames();
        String filename;
        Map<Table.StatKeys, String> mapx;
        int p;
        c = 0;
        long mem, totalmem = 0;
        while (i.hasNext()) {
            filename = i.next();
            mapx = Table.memoryStats(filename);
            prop.put("EcoList_" + c + "_tableIndexPath", ((p = filename.indexOf("DATA",0)) < 0) ? filename : filename.substring(p));
            prop.putNum("EcoList_" + c + "_tableSize", mapx.get(Table.StatKeys.tableSize));

            String v = mapx.get(Table.StatKeys.tableKeyMem);
            mem = v == null ? 0 : Long.parseLong(v);
            totalmem += mem;
            prop.put("EcoList_" + c + "_tableKeyMem", Formatter.bytesToString(mem));
            prop.put("EcoList_" + c + "_tableKeyChunkSize", mapx.get(Table.StatKeys.tableKeyChunkSize));

            v = mapx.get(Table.StatKeys.tableValueMem);
            mem = v == null ? 0 : Long.parseLong(v);
            totalmem += mem;
            prop.put("EcoList_" + c + "_tableValueMem", Formatter.bytesToString(mem));
            prop.put("EcoList_" + c + "_tableValueChunkSize", mapx.get(Table.StatKeys.tableValueChunkSize));

            c++;
        }
        prop.put("EcoList", c);
        prop.putNum("EcoIndexTotalMem", totalmem / (1024d * 1024d));

        // write object cache table
        final Iterator<Map.Entry<String, RAMIndex>> oi = RAMIndex.objects();
        c = 0;
        mem = 0;
        Map.Entry<String, RAMIndex> oie;
        RAMIndex cache;
        long hitmem, totalhitmem = 0;
        while (oi.hasNext()) {
            try {
                oie = oi.next();
            } catch (final ConcurrentModificationException e) {
                // we don't want to synchronize this
                ConcurrentLog.logException(e);
                break;
            }
            filename = oie.getKey();
            cache = oie.getValue();
            prop.put("indexcache_" + c + "_Name", ((p = filename.indexOf("DATA",0)) < 0) ? filename : filename.substring(p));

            hitmem = cache.mem();
            totalhitmem += hitmem;
            prop.put("indexcache_" + c + "_ChunkSize", cache.row().objectsize);
            prop.putNum("indexcache_" + c + "_Count", cache.size());
            prop.put("indexcache_" + c + "_NeededMem", cache.size() * cache.row().objectsize);
            prop.put("indexcache_" + c + "_UsedMem", hitmem);

            c++;
        }
        prop.put("indexcache", c);
        prop.putNum("indexcacheTotalMem", totalhitmem / (1024d * 1024d));

        // write object cache table
        i = Cache.filenames();
        c = 0;
        long missmem, totalmissmem = 0;
        totalhitmem = 0;
        Map<Cache.StatKeys, String> mapy;
        while (i.hasNext()) {
            filename = i.next();
            mapy = Cache.memoryStats(filename);
            prop.put("ObjectList_" + c + "_objectCachePath", ((p = filename.indexOf("DATA",0)) < 0) ? filename : filename.substring(p));

            // hit cache
            hitmem = Long.parseLong(mapy.get(Cache.StatKeys.objectHitMem));
            totalhitmem += hitmem;
            prop.put("ObjectList_" + c + "_objectHitChunkSize", mapy.get(Cache.StatKeys.objectHitChunkSize));
            prop.putNum("ObjectList_" + c + "_objectHitCacheCount", mapy.get(Cache.StatKeys.objectHitCacheCount));
            prop.put("ObjectList_" + c + "_objectHitCacheMem", Formatter.bytesToString(hitmem));
            prop.putNum("ObjectList_" + c + "_objectHitCacheReadHit", mapy.get(Cache.StatKeys.objectHitCacheReadHit));
            prop.putNum("ObjectList_" + c + "_objectHitCacheReadMiss", mapy.get(Cache.StatKeys.objectHitCacheReadMiss));
            prop.putNum("ObjectList_" + c + "_objectHitCacheWriteUnique", mapy.get(Cache.StatKeys.objectHitCacheWriteUnique));
            prop.putNum("ObjectList_" + c + "_objectHitCacheWriteDouble", mapy.get(Cache.StatKeys.objectHitCacheWriteDouble));
            prop.putNum("ObjectList_" + c + "_objectHitCacheDeletes", mapy.get(Cache.StatKeys.objectHitCacheDeletes));
            prop.putNum("ObjectList_" + c + "_objectHitCacheFlushes", mapy.get(Cache.StatKeys.objectHitCacheFlushes));

            // miss cache
            missmem = Long.parseLong(mapy.get(Cache.StatKeys.objectMissMem));
            totalmissmem += missmem;
            prop.put("ObjectList_" + c + "_objectMissChunkSize", mapy.get(Cache.StatKeys.objectMissChunkSize));
            prop.putNum("ObjectList_" + c + "_objectMissCacheCount", mapy.get(Cache.StatKeys.objectMissCacheCount));
            prop.putHTML("ObjectList_" + c + "_objectMissCacheMem", Formatter.bytesToString(missmem));
            prop.putNum("ObjectList_" + c + "_objectMissCacheReadHit", mapy.get(Cache.StatKeys.objectMissCacheReadHit));
            prop.putNum("ObjectList_" + c + "_objectMissCacheReadMiss", mapy.get(Cache.StatKeys.objectMissCacheReadMiss));
            prop.putNum("ObjectList_" + c + "_objectMissCacheWriteUnique", mapy.get(Cache.StatKeys.objectMissCacheWriteUnique));
            prop.putNum("ObjectList_" + c + "_objectMissCacheWriteDouble", mapy.get(Cache.StatKeys.objectMissCacheWriteDouble));
            prop.putNum("ObjectList_" + c + "_objectMissCacheDeletes", mapy.get(Cache.StatKeys.objectMissCacheDeletes));
            //prop.put("ObjectList_" + c + "_objectMissCacheFlushes", mapy.get(Cache.StatKeys.objectMissCacheFlushes));

            c++;
        }
        prop.put("ObjectList", c);
        prop.putNum("objectCacheStopGrow", Cache.getMemStopGrow() / (1024d * 1024d));
        prop.putNum("objectCacheStartShrink", Cache.getMemStartShrink() / (1024d * 1024d));
        prop.putNum("objectHitCacheTotalMem", totalhitmem / (1024d * 1024d));
        prop.putNum("objectMissCacheTotalMem", totalmissmem / (1024d * 1024d));

        // other caching structures
//        final CachedSolrConnector solr = (CachedSolrConnector) Switchboard.getSwitchboard().index.fulltext().getDefaultConnector();
//        prop.putNum("solrcacheHit.size", solr.nameCacheHitSize());
//        prop.putNum("solrcacheHit.Hit", solr.hitCache_Hit);
//        prop.putNum("solrcacheHit.Miss", solr.hitCache_Miss);
//        prop.putNum("solrcacheHit.Insert", solr.hitCache_Insert);
//        
//        prop.putNum("solrcacheMiss.size", solr.nameCacheMissSize());
//        prop.putNum("solrcacheMiss.Hit", solr.missCache_Hit);
//        prop.putNum("solrcacheMiss.Miss", solr.missCache_Miss);
//        prop.putNum("solrcacheMiss.Insert", solr.missCache_Insert);
//        
//        prop.putNum("solrcacheDocument.size", solr.nameCacheDocumentSize());
//        prop.putNum("solrcacheDocument.Hit", solr.documentCache_Hit);
//        prop.putNum("solrcacheDocument.Miss", solr.documentCache_Miss);
//        prop.putNum("solrcacheDocument.Insert", solr.documentCache_Insert);
        
        prop.putNum("namecacheHit.size", Domains.nameCacheHitSize());
        prop.putNum("namecacheHit.Hit", Domains.cacheHit_Hit);
        prop.putNum("namecacheHit.Miss", Domains.cacheHit_Miss);
        prop.putNum("namecacheHit.Insert", Domains.cacheHit_Insert);
        prop.putNum("namecacheMiss.size", Domains.nameCacheMissSize());
        prop.putNum("namecacheMiss.Hit", Domains.cacheMiss_Hit);
        prop.putNum("namecacheMiss.Miss", Domains.cacheMiss_Miss);
        prop.putNum("namecacheMiss.Insert", Domains.cacheMiss_Insert);
        prop.putNum("namecache.noCache", Domains.nameCacheNoCachingPatternsSize());
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
