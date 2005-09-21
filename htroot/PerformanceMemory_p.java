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

import java.util.Iterator;
import java.util.Map;
import java.io.File;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverThread;
import de.anomic.server.serverFileUtils;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeedDB;

public class PerformanceMemory_p {
    
    private static int[] slt;
    private static int   req,chk,usd,bst,god;
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch sb) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) sb;
        serverObjects prop = new serverObjects();
        File defaultSettingsFile = new File(switchboard.getRootPath(), "yacy.init");
        Map defaultSettings = ((post == null) || (!(post.containsKey("submitdefault")))) ? null : serverFileUtils.loadHashMap(defaultSettingsFile);
        
        if ((post != null) && (post.containsKey("submitcache"))) {
            sb.setConfig("ramCacheRWI", post.get("ramCacheRWI", "0"));
            sb.setConfig("ramCacheHTTP", post.get("ramCacheHTTP", "0"));
            sb.setConfig("ramCacheLURL", post.get("ramCacheLURL", "0"));
            sb.setConfig("ramCacheNURL", post.get("ramCacheNURL", "0"));
            sb.setConfig("ramCacheEURL", post.get("ramCacheEURL", "0"));
            sb.setConfig("ramCacheDHT", post.get("ramCacheDHT", "0"));
            sb.setConfig("ramCacheMessage", post.get("ramCacheMessage", "0"));
            sb.setConfig("ramCacheWiki", post.get("ramCacheWiki", "0"));
            sb.setConfig("ramCacheNews", post.get("ramCacheNews", "0"));
        }
        
        System.gc(); 
        long memoryFreeNow = Runtime.getRuntime().freeMemory();
        long memoryFreeAfterInit = Long.parseLong(sb.getConfig("memoryFreeAfterInit", "0"));
        long memoryFreeAfterStartup = Long.parseLong(sb.getConfig("memoryFreeAfterStartup", "0"));
        long memoryTotalNow = Runtime.getRuntime().totalMemory();
        long memoryTotalAfterInit = Long.parseLong(sb.getConfig("memoryTotalAfterInit", "0"));
        long memoryTotalAfterStartup = Long.parseLong(sb.getConfig("memoryTotalAfterStartup", "0"));
        long memoryMaxNow = Runtime.getRuntime().maxMemory();
        long memoryMaxAfterInit = Long.parseLong(sb.getConfig("memoryMaxAfterInit", "0"));
        long memoryMaxAfterStartup = Long.parseLong(sb.getConfig("memoryMaxAfterStartup", "0"));
        
        prop.put("memoryFreeNow", memoryFreeNow);
        prop.put("memoryFreeAfterInit", memoryFreeAfterInit);
        prop.put("memoryFreeAfterStartup", memoryFreeAfterStartup);
        prop.put("memoryTotalNow", memoryTotalNow);
        prop.put("memoryTotalAfterInit", memoryTotalAfterInit);
        prop.put("memoryTotalAfterStartup", memoryTotalAfterStartup);
        prop.put("memoryMaxNow", memoryMaxNow);
        prop.put("memoryMaxAfterInit", memoryMaxAfterInit);
        prop.put("memoryMaxAfterStartup", memoryMaxAfterStartup);
        
        req = switchboard.wordIndex.size();
        chk = switchboard.wordIndex.assortmentsCacheChunkSizeAvg();
        slt = switchboard.wordIndex.assortmentsCacheFillStatusCml();
        calc(); putprop(prop, "RWI");
        prop.put("ramCacheRWI", sb.getConfig("ramCacheRWI", "0"));
        
        req = switchboard.cacheManager.dbSize();
        chk = switchboard.cacheManager.dbCacheChunkSize();
        slt = switchboard.cacheManager.dbCacheFillStatus();
        calc(); putprop(prop, "HTTP");
        prop.put("ramCacheHTTP", sb.getConfig("ramCacheHTTP", "0"));
        
        req = switchboard.urlPool.loadedURL.urlHashCache.size();
        chk = switchboard.urlPool.loadedURL.urlHashCache.cacheChunkSize();
        slt = switchboard.urlPool.loadedURL.urlHashCache.cacheFillStatus();
        calc(); putprop(prop, "LURL");
        prop.put("ramCacheLURL", sb.getConfig("ramCacheLURL", "0"));
        
        req = switchboard.urlPool.noticeURL.urlHashCache.size();
        chk = switchboard.urlPool.noticeURL.urlHashCache.cacheChunkSize();
        slt = switchboard.urlPool.noticeURL.urlHashCache.cacheFillStatus();
        calc(); putprop(prop, "NURL");
        prop.put("ramCacheNURL", sb.getConfig("ramCacheNURL", "0"));
        
        req = switchboard.urlPool.errorURL.urlHashCache.size();
        chk = switchboard.urlPool.errorURL.urlHashCache.cacheChunkSize();
        slt = switchboard.urlPool.errorURL.urlHashCache.cacheFillStatus();
        calc(); putprop(prop, "EURL");
        prop.put("ramCacheEURL", sb.getConfig("ramCacheEURL", "0"));
        
        req = yacyCore.seedDB.sizeConnected() + yacyCore.seedDB.sizeDisconnected() + yacyCore.seedDB.sizePotential();
        chk = yacyCore.seedDB.dbCacheChunkSize();
        slt = yacyCore.seedDB.dbCacheFillStatus();
        calc(); putprop(prop, "DHT");
        prop.put("ramCacheDHT", sb.getConfig("ramCacheDHT", "0"));
        
        req = switchboard.messageDB.size();
        chk = switchboard.messageDB.dbCacheChunkSize();
        slt = switchboard.messageDB.dbCacheFillStatus();
        calc(); putprop(prop, "Message");
        prop.put("ramCacheMessage", sb.getConfig("ramCacheMessage", "0"));
        
        req = switchboard.wikiDB.sizeOfTwo();
        chk = switchboard.wikiDB.dbCacheChunkSize();
        slt = switchboard.wikiDB.dbCacheFillStatus();
        calc(); putprop(prop, "Wiki");
        prop.put("ramCacheWiki", sb.getConfig("ramCacheWiki", "0"));

        req = yacyCore.newsPool.dbSize();
        chk = yacyCore.newsPool.dbCacheChunkSize();
        slt = yacyCore.newsPool.dbCacheFillStatus();
        calc(); putprop(prop, "News");
        prop.put("ramCacheNews", sb.getConfig("ramCacheNews", "0"));

        // return rewrite values for templates
        return prop;
    }
    
    private static void calc() {
        usd = chk * (slt[1]+slt[2]+slt[3]);
        bst = (((chk * req) >> 10) + 1) << 10;
        god = (((bst / (1+slt[1]+slt[2]+slt[3]) * slt[1]) >> 10) + 1) << 10;
    }
    
    private static void putprop(serverObjects prop, String db) {
        prop.put("chunk" + db, chk);
        prop.put("slreq" + db, req);
        prop.put("slemp" + db, slt[0]);
        prop.put("slhig" + db, slt[1]);
        prop.put("slmed" + db, slt[2]);
        prop.put("sllow" + db, slt[3]);
        prop.put("used" + db, usd);
        prop.put("good" + db, god);
        prop.put("best" + db, bst);
    }
}
