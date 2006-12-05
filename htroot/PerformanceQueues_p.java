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

import java.io.File;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverThread;

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
            prop.put("table_" + c + "_shortdescr", (thread.getMonitorURL() == null) ? thread.getShortDescription() : "<a href=\"" + thread.getMonitorURL() + "\">" + thread.getShortDescription() + "</a>");
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
                idlesleep = post.getLong(threadName + "_idlesleep", 1000);
                busysleep = post.getLong(threadName + "_busysleep",  100);
                memprereq = post.getLong(threadName + "_memprereq",    0) * 1024;
                
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
            int wordOutCacheMaxCount = post.getInt("wordOutCacheMaxCount", 20000);
            switchboard.setConfig("wordCacheMaxCount", Integer.toString(wordOutCacheMaxCount));
            switchboard.wordIndex.setMaxWordCount(wordOutCacheMaxCount);

            int wordInCacheMaxCount = post.getInt("wordInCacheMaxCount", 1000);
            switchboard.setConfig("indexDistribution.dhtReceiptLimit", Integer.toString(wordInCacheMaxCount));
            switchboard.wordIndex.setInMaxWordCount(wordInCacheMaxCount);
            
            int wordCacheInitCount = post.getInt("wordCacheInitCount", 30000);
            switchboard.setConfig("wordCacheInitCount", Integer.toString(wordCacheInitCount));
            
            int maxWaitingWordFlush = post.getInt("maxWaitingWordFlush", 180);
            switchboard.setConfig("maxWaitingWordFlush", Integer.toString(maxWaitingWordFlush));
            
            int wordFlushIdleDivisor = post.getInt("wordFlushIdleDivisor", 420);
            switchboard.setConfig("wordFlushIdleDivisor", Integer.toString(wordFlushIdleDivisor));
            int wordFlushBusyDivisor = post.getInt("wordFlushBusyDivisor", 5000);
            switchboard.setConfig("wordFlushBusyDivisor", Integer.toString(wordFlushBusyDivisor));
            switchboard.wordIndex.setWordFlushDivisor(wordFlushIdleDivisor, wordFlushBusyDivisor);
        }
        
        if ((post != null) && (post.containsKey("poolConfig"))) {
            
            /* 
             * configuring the crawler pool 
             */
            // getting the current crawler pool configuration
            GenericKeyedObjectPool.Config crawlerPoolConfig = switchboard.cacheLoader.getPoolConfig();
            int maxActive = Integer.parseInt(post.get("Crawler Pool_maxActive","8"));
            int maxIdle = Integer.parseInt(post.get("Crawler Pool_maxIdle","4"));
            int minIdle = 0; // Integer.parseInt(post.get("Crawler Pool_minIdle","0"));
            
            //crawlerPoolConfig.minIdle = (minIdle > maxIdle) ? maxIdle/2 : minIdle;
            crawlerPoolConfig.maxIdle = (maxIdle > maxActive) ? maxActive/2 : maxIdle;
            crawlerPoolConfig.maxActive = maxActive;    
            
            // accept new crawler pool settings
            plasmaSwitchboard.crawlSlots = maxActive;
            switchboard.cacheLoader.setPoolConfig(crawlerPoolConfig);
            
            // storing the new values into configfile
            switchboard.setConfig("crawler.MaxActiveThreads",maxActive);
            switchboard.setConfig("crawler.MaxIdleThreads",maxIdle);
            //switchboard.setConfig("crawler.MinIdleThreads",minIdle);
            
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
            
            /*
             * Configuring the crawlStacker pool
             */
            GenericObjectPool.Config stackerPoolConfig = switchboard.sbStackCrawlThread.getPoolConfig();
            maxActive = Integer.parseInt(post.get("CrawlStacker Session Pool_maxActive","10"));
            maxIdle = Integer.parseInt(post.get("CrawlStacker Session Pool_maxIdle","10"));
            minIdle = Integer.parseInt(post.get("CrawlStacker Session Pool_minIdle","5"));
            
            stackerPoolConfig.minIdle = (minIdle > maxIdle) ? maxIdle/2 : minIdle;
            stackerPoolConfig.maxIdle = (maxIdle > maxActive) ? maxActive/2 : maxIdle;
            stackerPoolConfig.maxActive = maxActive;   
            
            switchboard.sbStackCrawlThread.setPoolConfig(stackerPoolConfig);     
            
            // storing the new values into configfile
            switchboard.setConfig("stacker.MaxActiveThreads",maxActive);
            switchboard.setConfig("stacker.MaxIdleThreads",maxIdle);
            switchboard.setConfig("stacker.MinIdleThreads",minIdle);
        }        
        
        if ((post != null) && (post.containsKey("proxyControlSubmit"))) {
            int onlineCautionDelay = post.getInt("onlineCautionDelay", 30000);
            switchboard.setConfig("onlineCautionDelay", Integer.toString(onlineCautionDelay));
        }
        
        // table cache settings
        prop.put("urlCacheSize", switchboard.wordIndex.loadedURL.writeCacheSize());  
        prop.put("wordCacheWSize", switchboard.wordIndex.dhtOutCacheSize());
        prop.put("wordCacheKSize", switchboard.wordIndex.dhtInCacheSize());
        prop.put("maxURLinWCache", "" + switchboard.wordIndex.maxURLinDHTOutCache());
        prop.put("maxURLinKCache", "" + switchboard.wordIndex.maxURLinDHTInCache());
        prop.put("maxAgeOfWCache", "" + (switchboard.wordIndex.maxAgeOfDHTOutCache() / 1000 / 60)); // minutes
        prop.put("maxAgeOfKCache", "" + (switchboard.wordIndex.maxAgeOfDHTInCache() / 1000 / 60)); // minutes
        prop.put("minAgeOfWCache", "" + (switchboard.wordIndex.minAgeOfDHTOutCache() / 1000 / 60)); // minutes
        prop.put("minAgeOfKCache", "" + (switchboard.wordIndex.minAgeOfDHTInCache() / 1000 / 60)); // minutes
        prop.put("maxWaitingWordFlush", switchboard.getConfig("maxWaitingWordFlush", "180"));
        prop.put("wordOutCacheMaxCount", switchboard.getConfigLong("wordCacheMaxCount", 20000));
        prop.put("wordInCacheMaxCount", switchboard.getConfigLong("indexDistribution.dhtReceiptLimit", 1000));
        prop.put("wordCacheInitCount", switchboard.getConfigLong("wordCacheInitCount", 30000));
        prop.put("wordFlushIdleDivisor", switchboard.getConfigLong("wordFlushIdleDivisor", 420));
        prop.put("wordFlushBusyDivisor", switchboard.getConfigLong("wordFlushBusyDivisor", 5000));
        prop.put("onlineCautionDelay", switchboard.getConfig("onlineCautionDelay", "30000"));
        prop.put("onlineCautionDelayCurrent", System.currentTimeMillis() - switchboard.proxyLastAccess);
        
        // table thread pool settings
        GenericKeyedObjectPool.Config crawlerPoolConfig = switchboard.cacheLoader.getPoolConfig();
        prop.put("pool_0_name","Crawler Pool");
        prop.put("pool_0_maxActive",crawlerPoolConfig.maxActive);
        prop.put("pool_0_maxIdle",crawlerPoolConfig.maxIdle);
        prop.put("pool_0_minIdleConfigurable",0);
        prop.put("pool_0_minIdle","0");        
        prop.put("pool_0_numActive",switchboard.cacheLoader.getNumActiveWorker());
        prop.put("pool_0_numIdle",switchboard.cacheLoader.getNumIdleWorker());
        
        serverThread httpd = switchboard.getThread("10_httpd");
        GenericObjectPool.Config httpdPoolConfig = ((serverCore)httpd).getPoolConfig();
        prop.put("pool_1_name","httpd Session Pool");
        prop.put("pool_1_maxActive",httpdPoolConfig.maxActive);
        prop.put("pool_1_maxIdle",httpdPoolConfig.maxIdle);
        prop.put("pool_1_minIdleConfigurable",1);
        prop.put("pool_1_minIdle",httpdPoolConfig.minIdle);  
        prop.put("pool_1_numActive",((serverCore)httpd).getActiveSessionCount());
        prop.put("pool_1_numIdle",((serverCore)httpd).getIdleSessionCount());
        
        GenericObjectPool.Config stackerPoolConfig = switchboard.sbStackCrawlThread.getPoolConfig();
        prop.put("pool_2_name","CrawlStacker Session Pool");
        prop.put("pool_2_maxActive",stackerPoolConfig.maxActive);
        prop.put("pool_2_maxIdle",stackerPoolConfig.maxIdle);
        prop.put("pool_2_minIdleConfigurable",1);
        prop.put("pool_2_minIdle",stackerPoolConfig.minIdle);  
        prop.put("pool_2_numActive",switchboard.sbStackCrawlThread.getNumActiveWorker());
        prop.put("pool_2_numIdle",switchboard.sbStackCrawlThread.getNumIdleWorker());
        prop.put("pool",3);        
        
        // return rewrite values for templates
        return prop;
    }
    
    private static String d(String a, String b) {
        return (a == null) ? b : a;
    }
}
