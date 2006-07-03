//YPStats_p.java
//-----------------------
//Author: Franz Brauﬂe
//part of YaCy (C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004, 2005
//last major change: 27.06.2006
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
//javac -classpath .:../classes YPStats_p.java
//if the shell's current path is HTROOT

import java.util.Iterator;


import org.apache.commons.pool.impl.GenericObjectPool;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverThread;
import de.anomic.yacy.yacyCore;

import de.anomic.yacy.yacySeed;

public class YPStats_p {

    private static final int KB = 1024;
        
    //private static long[]    slt,chk;
    //private static String[] ost;
    private static long     req /*, usd, bst, god*/;
    
    //private static long usedTotal, currTotal, dfltTotal, goodTotal, bestTotal;
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch sb) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) sb;
        serverObjects prop = new serverObjects();
        //File defaultSettingsFile = new File(switchboard.getRootPath(), "yacy.init");
        //Map defaultSettings = ((post == null) || (!(post.containsKey("submitdefault")))) ? null : serverFileUtils.loadHashMap(defaultSettingsFile);
        
        String url=null;
        if(post!=null && post.containsKey("url")) {
            url=(String)post.get("url");
        } else {
            url="http://ypstats.yacy-forum.de/index.php";
        }
        prop.put("url", url);
        
        Iterator threads = switchboard.threadNames();
        String threadName;
        serverThread thread;
        
        // calculate totals
        long blocktime_total = 0, sleeptime_total = 0, exectime_total = 0;
        while (threads.hasNext()) {
            threadName = (String) threads.next();
            thread = switchboard.getThread(threadName);
            blocktime_total += thread.getBlockTime();
            sleeptime_total += thread.getSleepTime();
            exectime_total += thread.getExecTime();
        }   
        if (blocktime_total == 0) blocktime_total = 1;
        if (sleeptime_total == 0) sleeptime_total = 1;
        if (exectime_total == 0) exectime_total = 1;
        
        // set templates for latest news from the threads
        long blocktime, sleeptime, exectime;
        long idlesleep, busysleep, memuse, memprereq;
        int queuesize;
        threads = switchboard.threadNames();
        int c = 0;
        long idleCycles, busyCycles, memshortageCycles;
        while (threads.hasNext()) {
            threadName = (String) threads.next();
            thread = switchboard.getThread(threadName);
            
            // set values to templates
            prop.put("table_" + c + "_threadname", threadName);
            prop.put("table_" + c + "_shortdescr", (thread.getMonitorURL() == null) ? thread.getShortDescription() : "<a href=\"" + thread.getMonitorURL() + "\" class=?\"small\">" + thread.getShortDescription() + "</a>");
            prop.put("table_" + c + "_longdescr", thread.getLongDescription());
            queuesize = thread.getJobCount();
            prop.put("table_" + c + "_queuesize", (queuesize == Integer.MAX_VALUE) ? "unlimited" : Integer.toString(queuesize));
            
            blocktime = thread.getBlockTime();
            sleeptime = thread.getSleepTime();
            exectime = thread.getExecTime();
            memuse = thread.getMemoryUse();
            idleCycles = thread.getIdleCycles();
            busyCycles = thread.getBusyCycles();
            memshortageCycles = thread.getOutOfMemoryCycles();
            prop.put("table_" + c + "_blocktime", blocktime / 1000);
            prop.put("table_" + c + "_blockpercent", Long.toString(100 * blocktime / blocktime_total));
            prop.put("table_" + c + "_sleeptime", sleeptime / 1000);
            prop.put("table_" + c + "_sleeppercent", Long.toString(100 * sleeptime / sleeptime_total));
            prop.put("table_" + c + "_exectime", exectime / 1000);
            prop.put("table_" + c + "_execpercent", Long.toString(100 * exectime / exectime_total));
            prop.put("table_" + c + "_totalcycles", Long.toString(idleCycles + busyCycles + memshortageCycles));
            prop.put("table_" + c + "_idlecycles", Long.toString(idleCycles));
            prop.put("table_" + c + "_busycycles", Long.toString(busyCycles));
            prop.put("table_" + c + "_memscycles", Long.toString(memshortageCycles));
            prop.put("table_" + c + "_sle ", ((idleCycles + busyCycles) == 0) ? "-" : Long.toString(sleeptime / (idleCycles + busyCycles)));
            prop.put("table_" + c + "_execpercycle", (busyCycles == 0) ? "-" : Long.toString(exectime / busyCycles));
            prop.put("table_" + c + "_memusepercycle", (busyCycles == 0) ? "-" : Long.toString(memuse / busyCycles / 1024));
            
            // load with old values
            idlesleep = Long.parseLong(switchboard.getConfig(threadName + "_idlesleep" , "1000"));
            busysleep = Long.parseLong(switchboard.getConfig(threadName + "_busysleep",   "100"));
            memprereq = Long.parseLong(switchboard.getConfig(threadName + "_memprereq",     "0"));
            
            prop.put("table_" + c + "_idlesleep", idlesleep);
            prop.put("table_" + c + "_busysleep", busysleep);
            prop.put("table_" + c + "_memprereq", memprereq / 1024);
            
            c++;
        }
        prop.put("table", c);
        
        // table cache settings
        prop.put("wordCacheMaxCount", switchboard.getConfig("wordCacheMaxCount", "10000"));
        
        // table thread pool settings
        GenericObjectPool.Config crawlerPoolConfig = switchboard.cacheLoader.getPoolConfig();
        prop.put("pool_0_name","Crawler Pool");
        prop.put("pool_0_maxActive",crawlerPoolConfig.maxActive);
        prop.put("pool_0_maxIdle",crawlerPoolConfig.maxIdle);
        prop.put("pool_0_minIdle",crawlerPoolConfig.minIdle);
        
        serverThread httpd = switchboard.getThread("10_httpd");
        GenericObjectPool.Config httpdPoolConfig = ((serverCore)httpd).getPoolConfig();
        prop.put("pool_1_name","httpd Session Pool");
        prop.put("pool_1_maxActive",httpdPoolConfig.maxActive);
        prop.put("pool_1_maxIdle",httpdPoolConfig.maxIdle);
        prop.put("pool_1_minIdle",httpdPoolConfig.minIdle);
        
        GenericObjectPool.Config stackerPoolConfig = switchboard.sbStackCrawlThread.getPoolConfig();
        prop.put("pool_2_name","CrawlStacker Session Pool");
        prop.put("pool_2_maxActive",stackerPoolConfig.maxActive);
        prop.put("pool_2_maxIdle",stackerPoolConfig.maxIdle);
        prop.put("pool_2_minIdle",stackerPoolConfig.minIdle);
        prop.put("pool",3);        
        
        // parse initialization memory settings
        String Xmx = sb.getConfig("javastart_Xmx", "Xmx64m").substring(3);
        prop.put("Xmx", Xmx.substring(0, Xmx.length() - 1));
        String Xms = sb.getConfig("javastart_Xms", "Xms10m").substring(3);
        prop.put("Xms", Xms.substring(0, Xms.length() - 1));
        
        plasmaSwitchboard sb1 = (plasmaSwitchboard) sb;
        req = sb1.wordIndex.size();
        putprop(prop, sb, "RWI");
        
        req = sb1.cacheManager.dbSize();
        putprop(prop, sb, "HTTP");
        
        //req = sb1.urlPool.loadedURL.urlHashCache.size();
        putprop(prop, sb, "LURL");
        
        //req = sb1.urlPool.noticeURL.urlHashCache.size();
        putprop(prop, sb, "NURL");
        
        //req = sb1.urlPool.errorURL.urlHashCache.size();
        putprop(prop, sb, "EURL");
        
        req = yacyCore.seedDB.sizeConnected() + yacyCore.seedDB.sizeDisconnected() + yacyCore.seedDB.sizePotential();
        putprop(prop, sb, "DHT");
        
        req = sb1.messageDB.size();
        putprop(prop, sb, "Message");
        
        req = sb1.wikiDB.sizeOfTwo();
        putprop(prop, sb, "Wiki");
        
        req = sb1.blogDB.size();
        putprop(prop, sb, "Blog");
        
        req = yacyCore.newsPool.dbSize();
        putprop(prop, sb, "News");
        
        req = plasmaSwitchboard.robots.size();
        putprop(prop, sb, "Robots");
        
        req = sb1.profiles.size();
        putprop(prop, sb, "Profiles");
        
        prop.put("versionpp", yacy.combinedVersionString2PrettyString(sb.getConfig("version","0.1")));
        
        prop.put("links", yacyCore.seedDB.mySeed.get(yacySeed.LCOUNT, "unknown"));
        prop.put("words", yacyCore.seedDB.mySeed.get(yacySeed.ICOUNT, "unknown"));
        /*prop.put("address", yacyCore.seedDB.mySeed.getAddress());
        prop.put("ip", serverCore.publicLocalIP());*/
        prop.put("hash", yacyCore.seedDB.mySeed.hash);
        
        String jversion = System.getProperties().getProperty("java.vendor");
        jversion += " " + System.getProperties().getProperty("java.version");
        prop.put("jversion", jversion);
        prop.put("processors",Runtime.getRuntime().availableProcessors());
        
        // return rewrite values for templates
        return prop;
    }
    
    private static void putprop(serverObjects prop, serverSwitch sb, String db) {
        prop.put("slreq" + db, req);
        prop.put("ramCache" + db, Long.parseLong(sb.getConfig("ramCache" + db, "0")) / KB);
    }
    
    /*
    private static String d(String a, String b) {
        return (a == null) ? b : a;
    }
    */
}