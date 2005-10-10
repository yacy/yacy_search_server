//PerformaceQueues_p.java
//-----------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004, 2005
//last major change: 16.02.2005
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
//javac -classpath .:../classes Network.java
//if the shell's current path is HTROOT

import java.util.Iterator;
import java.util.Map;
import java.io.File;

import org.apache.commons.pool.impl.GenericObjectPool;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverThread;
import de.anomic.server.serverFileUtils;

public class PerformanceQueues_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch sb) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) sb;
        serverObjects prop = new serverObjects();
        File defaultSettingsFile = new File(switchboard.getRootPath(), "yacy.init");
        Map defaultSettings = ((post == null) || (!(post.containsKey("submitdefault")))) ? null : serverFileUtils.loadHashMap(defaultSettingsFile);
        
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
            prop.put("table_" + c + "_sleeppercycle", ((idleCycles + busyCycles) == 0) ? "-" : Long.toString(sleeptime / (idleCycles + busyCycles)));
            prop.put("table_" + c + "_execpercycle", (busyCycles == 0) ? "-" : Long.toString(exectime / busyCycles));
            prop.put("table_" + c + "_memusepercycle", (busyCycles == 0) ? "-" : Long.toString(memuse / busyCycles / 1024));
            
            if ((post != null) && (post.containsKey("submitdelay"))) {
                // load with new values
                idlesleep = Long.parseLong((String) post.get(threadName + "_idlesleep", "1000"));
                busysleep = Long.parseLong((String) post.get(threadName + "_busysleep",  "100"));
                memprereq = Long.parseLong((String) post.get(threadName + "_memprereq",    "0")) * 1024;
                
                // check values to prevent short-cut loops
                if (idlesleep < 1000) idlesleep = 1000;
                if (threadName.equals("10_httpd")) { idlesleep = 0; busysleep = 0; memprereq = 0; }
                
                // on-the-fly re-configuration
                switchboard.setThreadPerformance(threadName, idlesleep, busysleep, memprereq);
                switchboard.setConfig(threadName + "_idlesleep", idlesleep);
                switchboard.setConfig(threadName + "_busysleep", busysleep);
                switchboard.setConfig(threadName + "_memprereq", memprereq);
            } if ((post != null) && (post.containsKey("submitdefault"))) {
                // load with new values
                idlesleep = Long.parseLong(d((String) defaultSettings.get(threadName + "_idlesleep"), "1000"));
                busysleep = Long.parseLong(d((String) defaultSettings.get(threadName + "_busysleep"),  "100"));
                memprereq = Long.parseLong(d((String) defaultSettings.get(threadName + "_memprereq"),    "0"));
                
                // check values to prevent short-cut loops
                if (idlesleep < 1000) idlesleep = 1000;
                if (threadName.equals("10_httpd")) { idlesleep = 0; busysleep = 0; memprereq = 0; }
                if ((threadName.equals("50_localcrawl")) && (busysleep < 100)) busysleep = 100;
                if ((threadName.equals("61_globalcrawltrigger")) && (busysleep < 100)) busysleep = 100;
                if ((threadName.equals("62_remotetriggeredcrawl")) && (busysleep < 100)) busysleep = 100;
                
                
                // on-the-fly re-configuration
                switchboard.setThreadPerformance(threadName, idlesleep, busysleep, memprereq);
                switchboard.setConfig(threadName + "_idlesleep", idlesleep);
                switchboard.setConfig(threadName + "_busysleep", busysleep);
                switchboard.setConfig(threadName + "_memprereq", memprereq);
            } else {
                // load with old values
                idlesleep = Long.parseLong(switchboard.getConfig(threadName + "_idlesleep" , "1000"));
                busysleep = Long.parseLong(switchboard.getConfig(threadName + "_busysleep",   "100"));
                memprereq = Long.parseLong(switchboard.getConfig(threadName + "_memprereq",     "0"));
            }
            prop.put("table_" + c + "_idlesleep", idlesleep);
            prop.put("table_" + c + "_busysleep", busysleep);
            prop.put("table_" + c + "_memprereq", memprereq / 1024);
            
            c++;
        }
        prop.put("table", c);
        
        if ((post != null) && (post.containsKey("cacheSizeSubmit"))) {
            int wordCacheMaxLow = Integer.parseInt((String) post.get("wordCacheMaxLow", "8000"));
            int wordCacheMaxHigh = Integer.parseInt((String) post.get("wordCacheMaxHigh", "10000"));
            switchboard.setConfig("wordCacheMaxLow", Integer.toString(wordCacheMaxLow));
            switchboard.setConfig("wordCacheMaxHigh", Integer.toString(wordCacheMaxHigh));
            switchboard.wordIndex.setMaxWords(wordCacheMaxLow, wordCacheMaxHigh);
            int maxWaitingWordFlush = Integer.parseInt((String) post.get("maxWaitingWordFlush", "180"));
            switchboard.setConfig("maxWaitingWordFlush", Integer.toString(maxWaitingWordFlush));
        }
        
        if ((post != null) && (post.containsKey("poolConfig"))) {
            
            /* 
             * configuring the crawler pool 
             */
            // getting the current crawler pool configuration
            GenericObjectPool.Config crawlerPoolConfig = switchboard.cacheLoader.getPoolConfig();
            int maxActive = Integer.parseInt(post.get("Crawler Pool_maxActive","8"));
            int maxIdle = Integer.parseInt(post.get("Crawler Pool_maxIdle","4"));
            int minIdle = Integer.parseInt(post.get("Crawler Pool_minIdle","0"));
            
            crawlerPoolConfig.minIdle = (minIdle > maxIdle) ? maxIdle/2 : minIdle;
            crawlerPoolConfig.maxIdle = (maxIdle > maxActive) ? maxActive/2 : maxIdle;
            crawlerPoolConfig.maxActive = maxActive;    
            
            // accept new crawler pool settings
            plasmaSwitchboard.crawlSlots = maxActive;
            switchboard.cacheLoader.setPoolConfig(crawlerPoolConfig);
            
            // storing the new values into configfile
            switchboard.setConfig("crawler.MaxActiveThreads",maxActive);
            switchboard.setConfig("crawler.MaxIdleThreads",maxIdle);
            switchboard.setConfig("crawler.MinIdleThreads",minIdle);
            
            /* 
             * configuring the http pool 
             */
            serverThread httpd = switchboard.getThread("10_httpd");
            GenericObjectPool.Config httpdPoolConfig = ((serverCore)httpd).getPoolConfig();
            maxActive = Integer.parseInt(post.get("httpd Session Pool_maxActive","8"));
            maxIdle = Integer.parseInt(post.get("httpd Session Pool_maxIdle","4"));
            minIdle = Integer.parseInt(post.get("httpd Session Pool_minIdle","0"));
            
            httpdPoolConfig.minIdle = (minIdle > maxIdle) ? maxIdle/2 : minIdle;
            httpdPoolConfig.maxIdle = (maxIdle > maxActive) ? maxActive/2 : maxIdle;
            httpdPoolConfig.maxActive = maxActive;    
            
            ((serverCore)httpd).setPoolConfig(httpdPoolConfig);     
            
            // storing the new values into configfile
            switchboard.setConfig("httpdMaxActiveSessions",maxActive);
            switchboard.setConfig("httpdMaxIdleSessions",maxIdle);
            switchboard.setConfig("httpdMinIdleSessions",minIdle);
        }        
        
        if ((post != null) && (post.containsKey("proxyControlSubmit"))) {
            int onlineCautionDelay = Integer.parseInt((String) post.get("onlineCautionDelay", "30000"));
            switchboard.setConfig("onlineCautionDelay", Integer.toString(onlineCautionDelay));
        }
        
        // table cache settings
        prop.put("wordCacheRAMSize", switchboard.wordIndex.wordCacheRAMSize());
        prop.put("maxURLinWordCache", "" + switchboard.wordIndex.maxURLinWordCache());
        prop.put("maxWaitingWordFlush", switchboard.getConfig("maxWaitingWordFlush", "180"));
        prop.put("wordCacheMaxLow", switchboard.getConfig("wordCacheMaxLow", "10000"));
        prop.put("wordCacheMaxHigh", switchboard.getConfig("wordCacheMaxHigh", "10000"));
        prop.put("onlineCautionDelay", switchboard.getConfig("onlineCautionDelay", "30000"));
        prop.put("onlineCautionDelayCurrent", System.currentTimeMillis() - switchboard.proxyLastAccess);
        
        int[] asizes = switchboard.wordIndex.assortmentSizes();
        for (int i = 0; i < asizes.length; i += 8) {
            prop.put("assortmentCluster_" + (i/8) + "_assortmentSlots", (i + 1) + "-" + (i + 8));
            prop.put("assortmentCluster_" + (i/8) + "_assortmentSizeA", asizes[i]);
            prop.put("assortmentCluster_" + (i/8) + "_assortmentSizeB", asizes[i + 1]);
            prop.put("assortmentCluster_" + (i/8) + "_assortmentSizeC", asizes[i + 2]);
            prop.put("assortmentCluster_" + (i/8) + "_assortmentSizeD", asizes[i + 3]);
            prop.put("assortmentCluster_" + (i/8) + "_assortmentSizeE", asizes[i + 4]);
            prop.put("assortmentCluster_" + (i/8) + "_assortmentSizeF", asizes[i + 5]);
            prop.put("assortmentCluster_" + (i/8) + "_assortmentSizeG", asizes[i + 6]);
            prop.put("assortmentCluster_" + (i/8) + "_assortmentSizeH", asizes[i + 7]);
        }
        prop.put("assortmentCluster", asizes.length / 8);
        
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
        prop.put("pool",2);
        
        
        // return rewrite values for templates
        return prop;
    }
    
    private static String d(String a, String b) {
        return (a == null) ? b : a;
    }
}
