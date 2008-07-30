//PerformaceQueues_p.java
//-----------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
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

//You must compile this file with
//javac -classpath .:../classes Network.java
//if the shell's current path is HTROOT

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverBusyThread;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverThread;
import de.anomic.tools.yFormatter;

public class PerformanceQueues_p {
    /**
     * list of pre-defined settings: filename -> description
     */
    private final static Map<String, String> performanceProfiles = new HashMap<String, String>(4, 0.9f);
    static {
        // no sorted output!
        performanceProfiles.put("defaults/yacy.init", "default (crawl)");
        performanceProfiles.put("defaults/performance_dht.profile", "prefer DHT");
    }
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> sb) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) sb;
        serverObjects prop = new serverObjects();
        File defaultSettingsFile = new File(switchboard.getRootPath(), "defaults/yacy.init");
        if(post != null && post.containsKey("defaultFile")) {
            // TODO check file-path!
            final File value = new File(switchboard.getRootPath(), post.get("defaultFile", "defaults/yacy.init"));
            // check if value is readable file
            if(value.exists() && value.isFile() && value.canRead()) {
                defaultSettingsFile = value;
            }
        }
        Map<String, String> defaultSettings = ((post == null) || (!(post.containsKey("submitdefault")))) ? null : serverFileUtils.loadHashMap(defaultSettingsFile);
        Iterator<String> threads = switchboard.threadNames();
        String threadName;
        serverBusyThread thread;
        
        boolean xml = (header.get("PATH")).endsWith(".xml");
        prop.setLocalized(!xml);
        
        // calculate totals
        long blocktime_total = 0, sleeptime_total = 0, exectime_total = 0;
        while (threads.hasNext()) {
            threadName = threads.next();
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
        // set profile?
        final double multiplier = (post != null) && post.containsKey("multiplier") ? post.getDouble("multiplier", 1) : 1;
        final boolean setProfile = (post != null && post.containsKey("submitdefault"));
        final boolean setDelay = (post != null) && (post.containsKey("submitdelay"));
        // save used settings file to config
        if (setProfile) switchboard.setConfig("performanceProfile", post.get("defaultFile", "defaults/yacy.init"));
        
        while (threads.hasNext()) {
            threadName = threads.next();
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
            
            // load with old values
            idlesleep = switchboard.getConfigLong(threadName + "_idlesleep" , 1000);
            busysleep = switchboard.getConfigLong(threadName + "_busysleep",   100);
            memprereq = switchboard.getConfigLong(threadName + "_memprereq",     0);
            if (setDelay) {
                // load with new values
                idlesleep = post.getLong(threadName + "_idlesleep", idlesleep);
                busysleep = post.getLong(threadName + "_busysleep", busysleep);
                memprereq = post.getLong(threadName + "_memprereq", memprereq) * 1024;
                if (memprereq == 0) memprereq = switchboard.getConfigLong(threadName + "_memprereq", 0);
                    
                // check values to prevent short-cut loops
                if (idlesleep < 1000) idlesleep = 1000;
                if (threadName.equals("10_httpd")) { idlesleep = 0; busysleep = 0; memprereq = 0; }
                
                onTheFlyReconfiguration(switchboard, threadName, idlesleep, busysleep, memprereq);
            } if (setProfile) {
                // load with new values
                idlesleep = (long) (Long.parseLong(d(defaultSettings.get(threadName + "_idlesleep"), String.valueOf(idlesleep))) * multiplier);
                busysleep = (long) (Long.parseLong(d(defaultSettings.get(threadName + "_busysleep"), String.valueOf(busysleep))) * multiplier);
                memprereq = (long) (Long.parseLong(d(defaultSettings.get(threadName + "_memprereq"), String.valueOf(memprereq))) * multiplier);

                // check values to prevent short-cut loops
                if (idlesleep < 1000) idlesleep = 1000;
                if (threadName.equals("10_httpd")) { idlesleep = 0; busysleep = 0; memprereq = 0; }
                if ((threadName.equals("50_localcrawl")) && (busysleep < 100)) busysleep = 100;
                if ((threadName.equals("61_globalcrawltrigger")) && (busysleep < 100)) busysleep = 100;
                if ((threadName.equals("62_remotetriggeredcrawl")) && (busysleep < 100)) busysleep = 100;

                onTheFlyReconfiguration(switchboard, threadName, idlesleep, busysleep, memprereq);
            }
            prop.put("table_" + c + "_idlesleep", idlesleep);
            prop.put("table_" + c + "_busysleep", busysleep);
            prop.put("table_" + c + "_memprereq", memprereq / 1024);
            // disallow setting of memprereq for indexer to prevent db from throwing OOMs
            prop.put("table_" + c + "_disabled", /*(threadName.endsWith("_indexing")) ? 1 :*/ "0");
            prop.put("table_" + c + "_recommendation", threadName.endsWith("_indexing") ? "1" : "0");
            prop.putNum("table_" + c + "_recommendation_value", threadName.endsWith("_indexing") ? (switchboard.webIndex.minMem() / 1024) : 0);
            c++;
        }
        prop.put("table", c);
        
        // performance profiles
        c = 0;
        final String usedfile = switchboard.getConfig("performanceProfile", "defaults/yacy.init");
        for(String filename: performanceProfiles.keySet()) {
            prop.put("profile_" + c + "_filename", filename);
            prop.put("profile_" + c + "_description", performanceProfiles.get(filename));
            prop.put("profile_" + c + "_used", usedfile.equalsIgnoreCase(filename) ? "1" : "0");
            c++;
        }
        prop.put("profile", c);
        
        if ((post != null) && (post.containsKey("cacheSizeSubmit"))) {
            int wordCacheMaxCount = post.getInt("wordCacheMaxCount", 20000);
            switchboard.setConfig(plasmaSwitchboard.WORDCACHE_MAX_COUNT, Integer.toString(wordCacheMaxCount));
            switchboard.webIndex.setMaxWordCount(wordCacheMaxCount);
            
            int wordCacheInitCount = post.getInt(plasmaSwitchboard.WORDCACHE_INIT_COUNT, 30000);
            switchboard.setConfig(plasmaSwitchboard.WORDCACHE_INIT_COUNT, Integer.toString(wordCacheInitCount));
        }
        
        if ((post != null) && (post.containsKey("poolConfig"))) {
            
            /* 
             * configuring the crawler pool 
             */
            // getting the current crawler pool configuration
            int maxBusy = Integer.parseInt(post.get("Crawler Pool_maxActive","8"));
            
            // storing the new values into configfile
            switchboard.setConfig(plasmaSwitchboard.CRAWLER_THREADS_ACTIVE_MAX,maxBusy);
            //switchboard.setConfig("crawler.MinIdleThreads",minIdle);
            
            /* 
             * configuring the http pool 
             */
            serverThread httpd = switchboard.getThread("10_httpd");
            try {
                maxBusy = Integer.parseInt(post.get("httpd Session Pool_maxActive","8"));
            } catch (NumberFormatException e) {
                maxBusy = 8;
            }

            ((serverCore)httpd).setMaxSessionCount(maxBusy);    
            
            // storing the new values into configfile
            switchboard.setConfig("httpdMaxBusySessions",maxBusy);

        }        
        
        if ((post != null) && (post.containsKey("PrioritySubmit"))) {
        	switchboard.setConfig("javastart_priority",post.get("YaCyPriority","0"));
        }
        
        if ((post != null) && (post.containsKey("onlineCautionSubmit"))) {
            switchboard.setConfig(plasmaSwitchboard.PROXY_ONLINE_CAUTION_DELAY, Integer.toString(post.getInt("crawlPauseProxy", 30000)));
            switchboard.setConfig(plasmaSwitchboard.LOCALSEACH_ONLINE_CAUTION_DELAY, Integer.toString(post.getInt("crawlPauseLocalsearch", 30000)));
            switchboard.setConfig(plasmaSwitchboard.REMOTESEARCH_ONLINE_CAUTION_DELAY, Integer.toString(post.getInt("crawlPauseRemotesearch", 30000)));
        }
        
        if ((post != null) && (post.containsKey("minimumDeltaSubmit"))) {
            long minimumLocalDelta = post.getLong("minimumLocalDelta", switchboard.crawlQueues.noticeURL.getMinimumLocalDelta());
            long minimumGlobalDelta = post.getLong("minimumGlobalDelta", switchboard.crawlQueues.noticeURL.getMinimumGlobalDelta());
            switchboard.setConfig("minimumLocalDelta", minimumLocalDelta);
            switchboard.setConfig("minimumGlobalDelta", minimumGlobalDelta);
            switchboard.crawlQueues.noticeURL.setMinimumLocalDelta(minimumLocalDelta);
            switchboard.crawlQueues.noticeURL.setMinimumGlobalDelta(minimumGlobalDelta);
        }
        
        // delta settings
        prop.put("minimumLocalDelta", switchboard.crawlQueues.noticeURL.getMinimumLocalDelta());
        prop.put("minimumGlobalDelta", switchboard.crawlQueues.noticeURL.getMinimumGlobalDelta());
        
        // table cache settings
        prop.putNum("urlCacheSize", switchboard.webIndex.getURLwriteCacheSize());  
        prop.putNum("wordCacheWSize", switchboard.webIndex.dhtOutCacheSize());
        prop.putNum("wordCacheKSize", switchboard.webIndex.dhtInCacheSize());
        prop.putNum("wordCacheWSizeKBytes", switchboard.webIndex.dhtCacheSizeBytes(false)/1024);
        prop.putNum("wordCacheKSizeKBytes", switchboard.webIndex.dhtCacheSizeBytes(true)/1024);
        prop.putNum("maxURLinWCache", switchboard.webIndex.maxURLinDHTOutCache());
        prop.putNum("maxURLinKCache", switchboard.webIndex.maxURLinDHTInCache());
        prop.putNum("maxAgeOfWCache", switchboard.webIndex.maxAgeOfDHTOutCache() / 1000 / 60); // minutes
        prop.putNum("maxAgeOfKCache", switchboard.webIndex.maxAgeOfDHTInCache() / 1000 / 60); // minutes
        prop.putNum("minAgeOfWCache", switchboard.webIndex.minAgeOfDHTOutCache() / 1000 / 60); // minutes
        prop.putNum("minAgeOfKCache", switchboard.webIndex.minAgeOfDHTInCache() / 1000 / 60); // minutes
        prop.putNum("maxWaitingWordFlush", switchboard.getConfigLong("maxWaitingWordFlush", 180));
        prop.put("wordCacheMaxCount", switchboard.getConfigLong(plasmaSwitchboard.WORDCACHE_MAX_COUNT, 20000));
        prop.put("wordCacheInitCount", switchboard.getConfigLong(plasmaSwitchboard.WORDCACHE_INIT_COUNT, 30000));
        prop.put("crawlPauseProxy", switchboard.getConfigLong(plasmaSwitchboard.PROXY_ONLINE_CAUTION_DELAY, 30000));
        prop.put("crawlPauseLocalsearch", switchboard.getConfigLong(plasmaSwitchboard.LOCALSEACH_ONLINE_CAUTION_DELAY, 30000));
        prop.put("crawlPauseRemotesearch", switchboard.getConfigLong(plasmaSwitchboard.REMOTESEARCH_ONLINE_CAUTION_DELAY, 30000));
        prop.putNum("crawlPauseProxyCurrent", (System.currentTimeMillis() - switchboard.proxyLastAccess) / 1000);
        prop.putNum("crawlPauseLocalsearchCurrent", (System.currentTimeMillis() - switchboard.localSearchLastAccess) / 1000);
        prop.putNum("crawlPauseRemotesearchCurrent", (System.currentTimeMillis() - switchboard.remoteSearchLastAccess) / 1000);
        
        // table thread pool settings
        prop.put("pool_0_name","Crawler Pool");
        prop.put("pool_0_maxActive", switchboard.getConfigLong("crawler.MaxActiveThreads", 0));
        prop.put("pool_0_numActive",switchboard.crawlQueues.size());
        
        serverThread httpd = switchboard.getThread("10_httpd");
        prop.put("pool_1_name", "httpd Session Pool");
        prop.put("pool_1_maxActive", ((serverCore)httpd).getMaxSessionCount());
        prop.put("pool_1_numActive", ((serverCore)httpd).getJobCount());
        
        prop.put("pool", "2");
        
        long curr_prio = switchboard.getConfigLong("javastart_priority",0);
        prop.put("priority_normal",(curr_prio==0) ? "1" : "0");
        prop.put("priority_below",(curr_prio==10) ? "1" : "0");
        prop.put("priority_low",(curr_prio==20) ? "1" : "0");
        
        // return rewrite values for templates
        return prop;
    }

    /**
     * @param switchboard
     * @param threadName
     * @param idlesleep
     * @param busysleep
     * @param memprereq
     */
    private static void onTheFlyReconfiguration(plasmaSwitchboard switchboard, String threadName, long idlesleep,
            long busysleep, long memprereq) {
        // on-the-fly re-configuration
        switchboard.setThreadPerformance(threadName, idlesleep, busysleep, memprereq);
        switchboard.setConfig(threadName + "_idlesleep", idlesleep);
        switchboard.setConfig(threadName + "_busysleep", busysleep);
        switchboard.setConfig(threadName + "_memprereq", memprereq);
    }
    
    private static String d(String a, String b) {
        return (a == null) ? b : a;
    }
}
