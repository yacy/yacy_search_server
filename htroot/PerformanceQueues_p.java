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

import org.apache.commons.pool.impl.GenericObjectPool;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverThread;
import de.anomic.tools.yFormatter;

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
        
        boolean xml = ((String)header.get("PATH")).endsWith(".xml");
        prop.setLocalized(!xml);
        
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

			prop.putHTML("table_" + c + "_hasurl_shortdescr", thread.getShortDescription(), xml);
			if(thread.getMonitorURL() == null) {
				prop.put("table_"+c+"_hasurl", "0");
			}else{
				prop.put("table_"+c+"_hasurl", "1");
				prop.put("table_" + c + "_hasurl_url", thread.getMonitorURL());
			}
            prop.putHTML("table_" + c + "_longdescr", thread.getLongDescription(), xml);
            queuesize = thread.getJobCount();
            prop.put("table_" + c + "_queuesize", (queuesize == Integer.MAX_VALUE) ? "unlimited" : yFormatter.number(queuesize, !xml));
            
            blocktime = thread.getBlockTime();
            sleeptime = thread.getSleepTime();
            exectime = thread.getExecTime();
            memuse = thread.getMemoryUse();
            idleCycles = thread.getIdleCycles();
            busyCycles = thread.getBusyCycles();
            memshortageCycles = thread.getOutOfMemoryCycles();
            prop.putNum("table_" + c + "_blocktime", blocktime / 1000);
            prop.putNum("table_" + c + "_blockpercent", 100 * blocktime / blocktime_total);
            prop.putNum("table_" + c + "_sleeptime", sleeptime / 1000);
            prop.putNum("table_" + c + "_sleeppercent", 100 * sleeptime / sleeptime_total);
            prop.putNum("table_" + c + "_exectime", exectime / 1000);
            prop.putNum("table_" + c + "_execpercent", 100 * exectime / exectime_total);
            prop.putNum("table_" + c + "_totalcycles", idleCycles + busyCycles + memshortageCycles);
            prop.putNum("table_" + c + "_idlecycles", idleCycles);
            prop.putNum("table_" + c + "_busycycles", busyCycles);
            prop.putNum("table_" + c + "_memscycles", memshortageCycles);
            prop.putNum("table_" + c + "_sleeppercycle", ((idleCycles + busyCycles) == 0) ? -1 : sleeptime / (idleCycles + busyCycles));
            prop.putNum("table_" + c + "_execpercycle", (busyCycles == 0) ? -1 : exectime / busyCycles);
            prop.putNum("table_" + c + "_memusepercycle", (busyCycles == 0) ? -1 : memuse / busyCycles / 1024);
            
            if ((post != null) && (post.containsKey("submitdelay"))) {
                // load with new values
                idlesleep = post.getLong(threadName + "_idlesleep", 1000);
                busysleep = post.getLong(threadName + "_busysleep",  100);
                memprereq = post.getLong(threadName + "_memprereq",    0) * 1024;
                if (memprereq == 0) memprereq = sb.getConfigLong(threadName + "_memprereq", 0);
                    
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
            // disallow setting of memprereq for indexer to prevent db from throwing OOMs
            prop.put("table_" + c + "_disabled", /*(threadName.endsWith("_indexing")) ? 1 :*/ "0");
            prop.put("table_" + c + "_recommendation", threadName.endsWith("_indexing") ? "1" : "0");
            prop.putNum("table_" + c + "_recommendation_value", threadName.endsWith("_indexing") ? (switchboard.wordIndex.minMem() / 1024) : 0);
            c++;
        }
        prop.put("table", c);
        
        if ((post != null) && (post.containsKey("cacheSizeSubmit"))) {
            int wordCacheMaxCount = post.getInt("wordCacheMaxCount", 20000);
            switchboard.setConfig(plasmaSwitchboard.WORDCACHE_MAX_COUNT, Integer.toString(wordCacheMaxCount));
            switchboard.wordIndex.setMaxWordCount(wordCacheMaxCount);
            
            int wordCacheInitCount = post.getInt(plasmaSwitchboard.WORDCACHE_INIT_COUNT, 30000);
            switchboard.setConfig(plasmaSwitchboard.WORDCACHE_INIT_COUNT, Integer.toString(wordCacheInitCount));
            
            int flushsize = post.getInt("wordFlushSize", 2000);
            switchboard.setConfig("wordFlushSize", Integer.toString(flushsize));
            switchboard.wordIndex.setWordFlushSize(flushsize);
        }
        
        if ((post != null) && (post.containsKey("poolConfig"))) {
            
            /* 
             * configuring the crawler pool 
             */
            // getting the current crawler pool configuration
            int maxActive = Integer.parseInt(post.get("Crawler Pool_maxActive","8"));
            
            // storing the new values into configfile
            switchboard.setConfig(plasmaSwitchboard.CRAWLER_THREADS_ACTIVE_MAX,maxActive);
            //switchboard.setConfig("crawler.MinIdleThreads",minIdle);
            
            /* 
             * configuring the http pool 
             */
            serverThread httpd = switchboard.getThread("10_httpd");
            GenericObjectPool.Config httpdPoolConfig = ((serverCore)httpd).getPoolConfig();
            try {
                maxActive = Integer.parseInt(post.get("httpd Session Pool_maxActive","8"));
            } catch (NumberFormatException e) {
                maxActive = 8;
            }
            int maxIdle = 0;
            int minIdle = 0;
            try {
                maxIdle = Integer.parseInt(post.get("httpd Session Pool_maxIdle","4"));
            } catch (NumberFormatException e) {
                maxIdle = 4;
            }
            try {
                minIdle = Integer.parseInt(post.get("httpd Session Pool_minIdle","0"));
            } catch (NumberFormatException e) {
                minIdle = 0;
            }

            httpdPoolConfig.minIdle = (minIdle > maxIdle) ? maxIdle/2 : minIdle;
            httpdPoolConfig.maxIdle = (maxIdle > maxActive) ? maxActive/2 : maxIdle;
            httpdPoolConfig.maxActive = maxActive;    
            
            ((serverCore)httpd).setPoolConfig(httpdPoolConfig);     
            
            // storing the new values into configfile
            switchboard.setConfig("httpdMaxActiveSessions",maxActive);
            switchboard.setConfig("httpdMaxIdleSessions",maxIdle);
            switchboard.setConfig("httpdMinIdleSessions",minIdle);

        }        
        
        if ((post != null) && (post.containsKey("PrioritySubmit"))) {
        	switchboard.setConfig("javastart_priority",post.get("YaCyPriority","0"));
        }
        
        if ((post != null) && (post.containsKey("proxyControlSubmit"))) {
            int onlineCautionDelay = post.getInt("onlineCautionDelay", 30000);
            switchboard.setConfig("onlineCautionDelay", Integer.toString(onlineCautionDelay));
        }
        
        // table cache settings
        prop.putNum("urlCacheSize", switchboard.wordIndex.loadedURL.writeCacheSize());  
        prop.putNum("wordCacheWSize", switchboard.wordIndex.dhtOutCacheSize());
        prop.putNum("wordCacheKSize", switchboard.wordIndex.dhtInCacheSize());
        prop.putNum("wordCacheWSizeKBytes", switchboard.wordIndex.dhtCacheSizeBytes(false)/1024);
        prop.putNum("wordCacheKSizeKBytes", switchboard.wordIndex.dhtCacheSizeBytes(true)/1024);
        prop.putNum("maxURLinWCache", switchboard.wordIndex.maxURLinDHTOutCache());
        prop.putNum("maxURLinKCache", switchboard.wordIndex.maxURLinDHTInCache());
        prop.putNum("maxAgeOfWCache", switchboard.wordIndex.maxAgeOfDHTOutCache() / 1000 / 60); // minutes
        prop.putNum("maxAgeOfKCache", switchboard.wordIndex.maxAgeOfDHTInCache() / 1000 / 60); // minutes
        prop.putNum("minAgeOfWCache", switchboard.wordIndex.minAgeOfDHTOutCache() / 1000 / 60); // minutes
        prop.putNum("minAgeOfKCache", switchboard.wordIndex.minAgeOfDHTInCache() / 1000 / 60); // minutes
        prop.putNum("maxWaitingWordFlush", switchboard.getConfigLong("maxWaitingWordFlush", 180));
        prop.put("wordCacheMaxCount", switchboard.getConfigLong(plasmaSwitchboard.WORDCACHE_MAX_COUNT, 20000));
        prop.put("wordCacheInitCount", switchboard.getConfigLong(plasmaSwitchboard.WORDCACHE_INIT_COUNT, 30000));
        prop.put("wordFlushSize", switchboard.getConfigLong("wordFlushSize", 2000));
        prop.put("onlineCautionDelay", switchboard.getConfigLong("onlineCautionDelay", 30000));
        prop.putNum("onlineCautionDelayCurrent", System.currentTimeMillis() - switchboard.proxyLastAccess);
        
        // table thread pool settings
        prop.put("pool_0_name","Crawler Pool");
        prop.put("pool_0_maxActive", switchboard.getConfigLong("crawler.MaxActiveThreads", 0));
        prop.put("pool_0_maxIdle", 0);
        prop.put("pool_0_minIdleConfigurable",0);
        prop.put("pool_0_minIdle", 0);
        prop.put("pool_0_numActive",switchboard.crawlQueues.size());
        prop.put("pool_0_numIdle", 0);
        
        serverThread httpd = switchboard.getThread("10_httpd");
        GenericObjectPool.Config httpdPoolConfig = ((serverCore)httpd).getPoolConfig();
        prop.put("pool_1_name", "httpd Session Pool");
        prop.put("pool_1_maxActive", httpdPoolConfig.maxActive);
        prop.put("pool_1_maxIdle", httpdPoolConfig.maxIdle);
        prop.put("pool_1_minIdleConfigurable", "1");
        prop.put("pool_1_minIdle", httpdPoolConfig.minIdle);  
        prop.put("pool_1_numActive", ((serverCore)httpd).getActiveSessionCount());
        prop.put("pool_1_numIdle", ((serverCore)httpd).getIdleSessionCount());
        
        prop.put("pool", "2");
        
        long curr_prio = switchboard.getConfigLong("javastart_priority",0);
        prop.put("priority_normal",(curr_prio==0) ? "1" : "0");
        prop.put("priority_below",(curr_prio==10) ? "1" : "0");
        prop.put("priority_low",(curr_prio==20) ? "1" : "0");
        
        // return rewrite values for templates
        return prop;
    }
    
    private static String d(String a, String b) {
        return (a == null) ? b : a;
    }
}
