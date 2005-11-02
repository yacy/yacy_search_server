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

import java.util.Map;
import java.io.File;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverFileUtils;
import de.anomic.yacy.yacyCore;

public class PerformanceMemory_p {
    
    private static final int KB = 1024;
    private static final int MB = 1024 * KB;
    private static Map defaultSettings = null;
        
    private static int[] slt,chk;
    private static int   req,usd,bst,god;
    
    private static long usedTotal, currTotal, dfltTotal, goodTotal, bestTotal;
        
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        if (defaultSettings == null) {
            defaultSettings = serverFileUtils.loadHashMap(new File(env.getRootPath(), "yacy.init"));
        }
        prop.put("gc", 0);
        String set = "";
        if (post != null) {
            if (post.containsKey("setCustom")) {
                env.setConfig("ramCacheRWI", Long.parseLong(post.get("ramCacheRWI", "0")) * KB);
                env.setConfig("ramCacheHTTP", Long.parseLong(post.get("ramCacheHTTP", "0")) * KB);
                env.setConfig("ramCacheLURL", Long.parseLong(post.get("ramCacheLURL", "0")) * KB);
                env.setConfig("ramCacheNURL", Long.parseLong(post.get("ramCacheNURL", "0")) * KB);
                env.setConfig("ramCacheEURL", Long.parseLong(post.get("ramCacheEURL", "0")) * KB);
                env.setConfig("ramCacheDHT", Long.parseLong(post.get("ramCacheDHT", "0")) * KB);
                env.setConfig("ramCacheMessage", Long.parseLong(post.get("ramCacheMessage", "0")) * KB);
                env.setConfig("ramCacheWiki", Long.parseLong(post.get("ramCacheWiki", "0")) * KB);
                env.setConfig("ramCacheNews", Long.parseLong(post.get("ramCacheNews", "0")) * KB);
                env.setConfig("ramCacheRobots", Long.parseLong(post.get("ramCacheRobots", "0")) * KB);
                env.setConfig("ramCacheProfiles", Long.parseLong(post.get("ramCacheProfiles", "0")) * KB);
            }
            if (post.containsKey("setDefault")) {
                env.setConfig("ramCacheRWI", Long.parseLong((String) defaultSettings.get("ramCacheRWI")));
                env.setConfig("ramCacheHTTP", Long.parseLong((String) defaultSettings.get("ramCacheHTTP")));
                env.setConfig("ramCacheLURL", Long.parseLong((String) defaultSettings.get("ramCacheLURL")));
                env.setConfig("ramCacheNURL", Long.parseLong((String) defaultSettings.get("ramCacheNURL")));
                env.setConfig("ramCacheEURL", Long.parseLong((String) defaultSettings.get("ramCacheEURL")));
                env.setConfig("ramCacheDHT", Long.parseLong((String) defaultSettings.get("ramCacheDHT")));
                env.setConfig("ramCacheMessage", Long.parseLong((String) defaultSettings.get("ramCacheMessage")));
                env.setConfig("ramCacheWiki", Long.parseLong((String) defaultSettings.get("ramCacheWiki")));
                env.setConfig("ramCacheNews", Long.parseLong((String) defaultSettings.get("ramCacheNews")));
                env.setConfig("ramCacheRobots", Long.parseLong((String) defaultSettings.get("ramCacheRobots")));
                env.setConfig("ramCacheProfiles", Long.parseLong((String) defaultSettings.get("ramCacheProfiles")));
            }
            if (post.containsKey("setGood")) set = "setGood";
            if (post.containsKey("setBest")) set = "setBest";
            if (post.containsKey("gc")) {
                Runtime.getRuntime().gc();
                prop.put("gc", 1);
            }
            if (post.containsKey("Xmx")) {
                env.setConfig("javastart_Xmx", "Xmx" + post.get("Xmx", "64") + "m");
            }
            if (post.containsKey("Xms")) {
                env.setConfig("javastart_Xms", "Xms" + post.get("Xms", "10") + "m");
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
        
        usedTotal = 0;
        currTotal = 0;
        dfltTotal = 0;
        goodTotal = 0;
        bestTotal = 0;
    
        req = sb.wordIndex.size();
        chk = sb.wordIndex.assortmentsCacheChunkSizeAvg();
        slt = sb.wordIndex.assortmentsCacheFillStatusCml();
        putprop(prop, env, "RWI", set);
        
        req = sb.cacheManager.dbSize();
        chk = sb.cacheManager.dbCacheChunkSize();
        slt = sb.cacheManager.dbCacheFillStatus();
        putprop(prop, env, "HTTP", set);
        
        req = sb.urlPool.loadedURL.urlHashCache.size();
        chk = sb.urlPool.loadedURL.urlHashCache.cacheChunkSize();
        slt = sb.urlPool.loadedURL.urlHashCache.cacheFillStatus();
        putprop(prop, env, "LURL", set);
        
        req = sb.urlPool.noticeURL.urlHashCache.size();
        chk = sb.urlPool.noticeURL.urlHashCache.cacheChunkSize();
        slt = sb.urlPool.noticeURL.urlHashCache.cacheFillStatus();
        putprop(prop, env, "NURL", set);
        
        req = sb.urlPool.errorURL.urlHashCache.size();
        chk = sb.urlPool.errorURL.urlHashCache.cacheChunkSize();
        slt = sb.urlPool.errorURL.urlHashCache.cacheFillStatus();
        putprop(prop, env, "EURL", set);
        
        req = yacyCore.seedDB.sizeConnected() + yacyCore.seedDB.sizeDisconnected() + yacyCore.seedDB.sizePotential();
        chk = yacyCore.seedDB.dbCacheChunkSize();
        slt = yacyCore.seedDB.dbCacheFillStatus();
        putprop(prop, env, "DHT", set);
        
        req = sb.messageDB.size();
        chk = sb.messageDB.dbCacheChunkSize();
        slt = sb.messageDB.dbCacheFillStatus();
        putprop(prop, env, "Message", set);
        
        req = sb.wikiDB.sizeOfTwo();
        chk = sb.wikiDB.dbCacheChunkSize();
        slt = sb.wikiDB.dbCacheFillStatus();
        putprop(prop, env, "Wiki", set);
        
        req = yacyCore.newsPool.dbSize();
        chk = yacyCore.newsPool.dbCacheChunkSize();
        slt = yacyCore.newsPool.dbCacheFillStatus();
        putprop(prop, env, "News", set);
        
        req = plasmaSwitchboard.robots.size();
        chk = plasmaSwitchboard.robots.dbCacheChunkSize();
        slt = plasmaSwitchboard.robots.dbCacheFillStatus();
        putprop(prop, env, "Robots", set);        
        
        req = sb.profiles.size();
        chk = sb.profiles.dbCacheChunkSize();
        slt = sb.profiles.dbCacheFillStatus();
        putprop(prop, env, "Profiles", set);        
        
        prop.put("usedTotal", usedTotal / MB);
        prop.put("currTotal", currTotal / MB);
        prop.put("dfltTotal", dfltTotal / MB);
        prop.put("goodTotal", goodTotal / MB);
        prop.put("bestTotal", bestTotal / MB);
        
        // parse initialization memory settings
        String Xmx = (String) env.getConfig("javastart_Xmx", "Xmx64m").substring(3);
        prop.put("Xmx", Xmx.substring(0, Xmx.length() - 1));
        String Xms = (String) env.getConfig("javastart_Xms", "Xms10m").substring(3);
        prop.put("Xms", Xms.substring(0, Xms.length() - 1));
        
        // return rewrite values for templates
        return prop;
    }
    
    private static void putprop(serverObjects prop, serverSwitch env, String db, String set) {
        usd = chk[0]*slt[3] + chk[1]*slt[2] + chk[2]*slt[1];
        bst = (((chk[2] * req) >> 10) + 1) << 10;
        god = (((bst / (1+slt[1]+slt[2]+slt[3]) * slt[1]) >> 10) + 1) << 10;
        if (set.equals("setGood")) env.setConfig("ramCache" + db, god);
        if (set.equals("setBest")) env.setConfig("ramCache" + db, bst);
        prop.put("chunk" + db, chk[2] + "/" + chk[1] + "/" + chk[0]);
        prop.put("slreq" + db, req);
        prop.put("slemp" + db, slt[0]);
        prop.put("slhig" + db, slt[1]);
        prop.put("slmed" + db, slt[2]);
        prop.put("sllow" + db, slt[3]);
        prop.put("used" + db, usd / KB);
        prop.put("good" + db, god / KB);
        prop.put("best" + db, bst / KB);
        prop.put("dflt" + db, Long.parseLong((String) defaultSettings.get("ramCache" + db)) / KB);
        prop.put("ramCache" + db, Long.parseLong(env.getConfig("ramCache" + db, "0")) / KB);
        usedTotal += usd;
        currTotal += Long.parseLong(env.getConfig("ramCache" + db, "0"));
        dfltTotal += Long.parseLong((String) defaultSettings.get("ramCache" + db));
        goodTotal += god;
        bestTotal += bst;
    }
}
