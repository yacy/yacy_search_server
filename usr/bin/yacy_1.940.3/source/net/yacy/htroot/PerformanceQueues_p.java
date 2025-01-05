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

package net.yacy.htroot;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

import org.apache.http.pool.PoolStats;

import net.yacy.cora.federate.solr.instance.RemoteInstance;
import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.data.TransactionManager;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.rwi.IndexCell;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.Formatter;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.OS;
import net.yacy.kelondro.workflow.BusyThread;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Segment;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class PerformanceQueues_p {

    @SuppressWarnings("deprecation")
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        File defaultSettingsFile = new File(sb.getAppPath(), "defaults/yacy.init");

        /* Acquire a transaction token for the next POST form submission */
        try {
            prop.put(TransactionManager.TRANSACTION_TOKEN_PARAM, TransactionManager.getTransactionToken(header));
        } catch (IllegalArgumentException e) {
            sb.log.fine("access by unauthorized or unknown user: no transaction token delivered");
        }

        // get segment
        final Segment indexSegment = sb.index;

        if(post != null) {
        	/* Check the transaction is valid : validation apply then for every uses of this post parameter */
        	TransactionManager.checkPostTransaction(header, post);

            if(post.containsKey("resetObserver")) {
            	/* The the reset state button is pushed, we only perform this action and do not save other form field values at the same time */
            	MemoryControl.resetProperState();
            } else {
            	if(post.containsKey("defaultFile")){
            		// TODO check file-path!
            		final File value = new File(sb.getAppPath(), post.get("defaultFile", "defaults/yacy.init"));
            		// check if value is readable file
            		if(value.exists() && value.isFile() && value.canRead()) {
            			defaultSettingsFile = value;
            		}
            	}
            	if (post.containsKey("Xmx")) {
            		int xmx = post.getInt("Xmx", 600); // default maximum heap size
            		if (OS.isWin32) xmx = Math.min(2000, xmx);
            		sb.setConfig("javastart_Xmx", "Xmx" + xmx + "m");
            		prop.put("setStartupCommit", "1");

            		/* Acquire a transaction token for the restart operation */
            		prop.put("setStartupCommit_" + TransactionManager.TRANSACTION_TOKEN_PARAM, TransactionManager.getTransactionToken(header, "/Steering.html"));
            	}
            	if(post.containsKey("diskFree")) {
            		sb.setConfig(SwitchboardConstants.RESOURCE_DISK_FREE_MIN_STEADYSTATE, post.getLong("diskFree", SwitchboardConstants.RESOURCE_DISK_FREE_MIN_STEADYSTATE_DEFAULT));
            	}
            	if(post.containsKey("diskFreeHardlimit")) {
            		sb.setConfig(SwitchboardConstants.RESOURCE_DISK_FREE_MIN_UNDERSHOT, post.getLong("diskFreeHardlimit", SwitchboardConstants.RESOURCE_DISK_FREE_MIN_UNDERSHOT_DEFAULT));

            		/* This is a checkbox in Performance_p.html : when not checked the value is not in post parameters,
            		 * so we take only in account when the relate diskFreeHardlimit is set */
            		sb.setConfig(SwitchboardConstants.RESOURCE_DISK_FREE_AUTOREGULATE,
            				post.getBoolean("diskFreeAutoregulate"));
            	}
            	if (post.containsKey("diskUsed")) {
            		sb.setConfig(SwitchboardConstants.RESOURCE_DISK_USED_MAX_STEADYSTATE,
            				post.getLong("diskUsed", SwitchboardConstants.RESOURCE_DISK_USED_MAX_STEADYSTATE_DEFAULT));
            	}
            	if (post.containsKey("diskUsedHardlimit")) {
            		sb.setConfig(SwitchboardConstants.RESOURCE_DISK_USED_MAX_OVERSHOT, post.getLong("diskUsedHardlimit",
            				SwitchboardConstants.RESOURCE_DISK_USED_MAX_OVERSHOT_DEFAULT));

            		/* This is a checkbox in Performance_p.html : when not checked the value is not in post parameters,
            		 * so we take only in account when the related diskFreeHardlimit is set */
            		sb.setConfig(SwitchboardConstants.RESOURCE_DISK_USED_AUTOREGULATE,
            				post.getBoolean("diskUsedAutoregulate"));
            	}
            	if(post.containsKey("memoryAcceptDHT")) {
            		sb.setConfig(SwitchboardConstants.MEMORY_ACCEPTDHT, post.getInt("memoryAcceptDHT", 50));
            	}
            }
        }
        final Map<String, String> defaultSettings = ((post == null) || (!(post.containsKey("submitdefault")))) ? null : FileUtils.loadMap(defaultSettingsFile);
        Iterator<String> threads = sb.threadNames();
        String threadName;
        BusyThread thread;

        final boolean xml = header.getPathInfo().endsWith(".xml");
        prop.setLocalized(!xml);

        // calculate totals
        long blocktime_total = 0, sleeptime_total = 0, exectime_total = 0;
        while (threads.hasNext()) {
            threadName = threads.next();
            thread = sb.getThread(threadName);
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
        double loadprereq;
        int queuesize;
        threads = sb.threadNames();
        int c = 0;
        long idleCycles, busyCycles, memshortageCycles, highCPUCycles;
        // set profile?
        final boolean setProfile = (post != null && post.containsKey("submitdefault"));
        final boolean setDelay = (post != null) && (post.containsKey("submitdelay"));
        // save used settings file to config
        if (setProfile && post != null){
        	sb.setConfig("performanceSpeed", post.getInt("profileSpeed", 100));
        }

        final IndexCell<WordReference> rwi = indexSegment.termIndex();
        while (threads.hasNext()) {
            threadName = threads.next();
            thread = sb.getThread(threadName);

            // set values to templates
            prop.put("table_" + c + "_threadname", threadName);

			prop.putHTML("table_" + c + "_hasurl_shortdescr", thread.getShortDescription());
			if(thread.getMonitorURL() == null) {
				prop.put("table_"+c+"_hasurl", "0");
			}else{
				prop.put("table_"+c+"_hasurl", "1");
				prop.put("table_" + c + "_hasurl_url", thread.getMonitorURL());
			}
            prop.putHTML("table_" + c + "_longdescr", thread.getLongDescription());
            queuesize = thread.getJobCount();
            prop.put("table_" + c + "_queuesize", (queuesize == Integer.MAX_VALUE) ? "unlimited" : Formatter.number(queuesize, !xml));

            blocktime = thread.getBlockTime();
            sleeptime = thread.getSleepTime();
            exectime = thread.getExecTime();
            memuse = thread.getMemoryUse();
            idleCycles = thread.getIdleCycles();
            busyCycles = thread.getBusyCycles();
            memshortageCycles = thread.getOutOfMemoryCycles();
            highCPUCycles = thread.getHighCPUCycles();
            prop.putNum("table_" + c + "_blocktime", blocktime / 1000);
            prop.putNum("table_" + c + "_blockpercent", 100 * blocktime / blocktime_total);
            prop.putNum("table_" + c + "_sleeptime", sleeptime / 1000);
            prop.putNum("table_" + c + "_sleeppercent", 100 * sleeptime / sleeptime_total);
            prop.putNum("table_" + c + "_exectime", exectime / 1000);
            prop.putNum("table_" + c + "_execpercent", 100 * exectime / exectime_total);
            prop.putNum("table_" + c + "_totalcycles", idleCycles + busyCycles + memshortageCycles + highCPUCycles);
            prop.putNum("table_" + c + "_idlecycles", idleCycles);
            prop.putNum("table_" + c + "_busycycles", busyCycles);
            prop.putNum("table_" + c + "_memscycles", memshortageCycles);
            prop.putNum("table_" + c + "_highcpucycles", highCPUCycles);
            prop.putNum("table_" + c + "_sleeppercycle", ((idleCycles + busyCycles) == 0) ? -1 : sleeptime / (idleCycles + busyCycles));
            prop.putNum("table_" + c + "_execpercycle", (busyCycles == 0) ? -1 : exectime / busyCycles);
            prop.putNum("table_" + c + "_memusepercycle", (busyCycles == 0) ? -1 : memuse / busyCycles / 1024);

            // load with old values
            idlesleep = sb.getConfigLong(threadName + "_idlesleep" , 1000);
            busysleep = sb.getConfigLong(threadName + "_busysleep",   100);
            memprereq = sb.getConfigLong(threadName + "_memprereq",     0);
            loadprereq = sb.getConfigFloat(threadName + "_loadprereq",  9);
            if (setDelay && post != null) {
                // load with new values
                idlesleep = post.getLong(threadName + "_idlesleep", idlesleep);
                busysleep = post.getLong(threadName + "_busysleep", busysleep);
                memprereq = post.getLong(threadName + "_memprereq", memprereq) * 1024l;
                if (memprereq == 0) memprereq = sb.getConfigLong(threadName + "_memprereq", 0);
                loadprereq = post.getDouble(threadName + "_loadprereq", loadprereq);
                if (loadprereq == 0) loadprereq = sb.getConfigFloat(threadName + "_loadprereq",  9);

                // check values to prevent short-cut loops
                if (idlesleep < 1000) idlesleep = 1000;

                sb.setThreadPerformance(threadName, idlesleep, busysleep, memprereq, loadprereq);
                idlesleep = sb.getConfigLong(threadName + "_idlesleep", idlesleep);
                busysleep = sb.getConfigLong(threadName + "_busysleep", busysleep);
            }
            if (setProfile) {
                // load with new values
                idlesleep = Long.parseLong(d(defaultSettings.get(threadName + "_idlesleep"), String.valueOf(idlesleep)));
                busysleep = Long.parseLong(d(defaultSettings.get(threadName + "_busysleep"), String.valueOf(busysleep)));
                memprereq = Long.parseLong(d(defaultSettings.get(threadName + "_memprereq"), String.valueOf(memprereq)));
                loadprereq = Double.parseDouble(d(defaultSettings.get(threadName + "_loadprereq"), String.valueOf(loadprereq)));
                // check values to prevent short-cut loops
                if (idlesleep < 1000) idlesleep = 1000;
                //if (threadName.equals(plasmaSwitchboardConstants.CRAWLJOB_LOCAL_CRAWL) && (busysleep < 50)) busysleep = 50;
                sb.setThreadPerformance(threadName, idlesleep, busysleep, memprereq, loadprereq);
            }
            prop.put("table_" + c + "_idlesleep", idlesleep);
            prop.put("table_" + c + "_busysleep", busysleep);
            prop.put("table_" + c + "_memprereq", memprereq / 1024);
            prop.put("table_" + c + "_loadprereq", loadprereq);
            // disallow setting of memprereq for indexer to prevent db from throwing OOMs
            // prop.put("table_" + c + "_disabled", /*(threadName.endsWith("_indexing")) ? 1 :*/ "0");
            prop.put("table_" + c + "_recommendation", threadName.endsWith("_indexing") ? "1" : "0");
            prop.putNum("table_" + c + "_recommendation_value", rwi == null ? 0 : threadName.endsWith("_indexing") ? (rwi.minMem() / 1024) : 0);
            c++;
        }
        prop.put("table", c);

        c = 0;
        final int[] speedValues = {200,150,100,50,25,10};
        final int usedspeed = sb.getConfigInt("performanceSpeed", 100);
        for(final int speed: speedValues){
        	prop.put("speed_" + c + "_value", speed);
        	prop.put("speed_" + c + "_label", speed + " %");
        	prop.put("speed_" + c + "_used", (speed == usedspeed) ? "1" : "0");
        	c++;
        }
        prop.put("speed", c);

        if ((post != null) && (post.containsKey("cacheSizeSubmit"))) {
            final int wordCacheMaxCount = post.getInt("wordCacheMaxCount", 20000);
            sb.setConfig(SwitchboardConstants.WORDCACHE_MAX_COUNT, Integer.toString(wordCacheMaxCount));
            if (rwi != null) rwi.setBufferMaxWordCount(wordCacheMaxCount);
        }

        /* Setting remote searches max loads */
        if (post != null) {
        	if(post.containsKey("setRemoteSearchLoads")) {
				float loadValue = post.getFloat("remoteSearchRWIMaxLoad",
						sb.getConfigFloat(SwitchboardConstants.REMOTESEARCH_MAXLOAD_RWI,
								SwitchboardConstants.REMOTESEARCH_MAXLOAD_RWI_DEFAULT));
				sb.setConfig(SwitchboardConstants.REMOTESEARCH_MAXLOAD_RWI, loadValue);

				loadValue = post.getFloat("remoteSearchSolrMaxLoad",
						sb.getConfigFloat(SwitchboardConstants.REMOTESEARCH_MAXLOAD_SOLR,
								SwitchboardConstants.REMOTESEARCH_MAXLOAD_SOLR_DEFAULT));
				sb.setConfig(SwitchboardConstants.REMOTESEARCH_MAXLOAD_SOLR, loadValue);
        	} else if(post.containsKey("defaultRemoteSearchLoads")) {
        		sb.setConfig(SwitchboardConstants.REMOTESEARCH_MAXLOAD_RWI, SwitchboardConstants.REMOTESEARCH_MAXLOAD_RWI_DEFAULT);
        		sb.setConfig(SwitchboardConstants.REMOTESEARCH_MAXLOAD_SOLR, SwitchboardConstants.REMOTESEARCH_MAXLOAD_SOLR_DEFAULT);
        	}
        }

        if ((post != null) && (post.containsKey("poolConfig"))) {

            /*
             * configuring the crawler pool
             */
            // get the current crawler pool configuration
            int maxBusy = post.getInt("Crawler Pool_maxActive", 8);

            // storing the new values into configfile
            sb.setConfig(SwitchboardConstants.CRAWLER_THREADS_ACTIVE_MAX,maxBusy);

            /*
             * configuring the robots.txt loading pool
             */
            // get the current crawler pool configuration
            maxBusy = post.getInt("Robots.txt Pool_maxActive", SwitchboardConstants.ROBOTS_TXT_THREADS_ACTIVE_MAX_DEFAULT);

            // storing the new values into configfile
            sb.setConfig(SwitchboardConstants.ROBOTS_TXT_THREADS_ACTIVE_MAX, maxBusy);

            /*
             * configuring the http pool
             */
            try {
                maxBusy = post.getInt("httpd Session Pool_maxActive", 8);
            } catch (final NumberFormatException e) {
                maxBusy = 8;
            }

            ConnectionInfo.setServerMaxcount(maxBusy);

            // storing the new values into configfile
            sb.setConfig("httpdMaxBusySessions",maxBusy);

        }

		if ((post != null) && (post.containsKey("connectionPoolConfig"))) {

			/* Configure the general outgoing HTTP connection pool */
			int maxTotal = post.getInt(SwitchboardConstants.HTTP_OUTGOING_POOL_GENERAL_MAX_TOTAL,
					SwitchboardConstants.HTTP_OUTGOING_POOL_GENERAL_MAX_TOTAL_DEFAULT);
			if (maxTotal > 0) {
				sb.setConfig(SwitchboardConstants.HTTP_OUTGOING_POOL_GENERAL_MAX_TOTAL, maxTotal);
				HTTPClient.initPoolMaxConnections(HTTPClient.CONNECTION_MANAGER, maxTotal);
			}

			/* Configure the remote Solr outgoing HTTP connection pool */
			maxTotal = post.getInt(SwitchboardConstants.HTTP_OUTGOING_POOL_REMOTE_SOLR_MAX_TOTAL,
					SwitchboardConstants.HTTP_OUTGOING_POOL_REMOTE_SOLR_MAX_TOTAL_DEFAULT);
			if (maxTotal > 0) {
				sb.setConfig(SwitchboardConstants.HTTP_OUTGOING_POOL_REMOTE_SOLR_MAX_TOTAL, maxTotal);
				RemoteInstance.initPoolMaxConnections(RemoteInstance.CONNECTION_MANAGER, maxTotal);
			}
		}

        if ((post != null) && (post.containsKey("onlineCautionSubmit"))) {
            sb.setConfig(SwitchboardConstants.PROXY_ONLINE_CAUTION_DELAY, Integer.toString(post.getInt("crawlPauseProxy", 30000)));
            sb.setConfig(SwitchboardConstants.LOCALSEACH_ONLINE_CAUTION_DELAY, Integer.toString(post.getInt("crawlPauseLocalsearch", 30000)));
            sb.setConfig(SwitchboardConstants.REMOTESEARCH_ONLINE_CAUTION_DELAY, Integer.toString(post.getInt("crawlPauseRemotesearch", 30000)));
        }

        // table cache settings
        prop.putNum("wordCacheSize", indexSegment.RWIBufferCount());
        prop.putNum("wordCacheSizeKBytes", rwi == null ? 0 : rwi.getBufferSizeBytes() / 1024L);
        prop.putNum("maxURLinCache", rwi == null ? 0 : rwi.getBufferMaxReferences());
        prop.putNum("maxAgeOfCache", rwi == null ? 0 : rwi.getBufferMaxAge() / 1000 / 60); // minutes
        prop.putNum("minAgeOfCache", rwi == null ? 0 : rwi.getBufferMinAge() / 1000 / 60); // minutes
        prop.putNum("maxWaitingWordFlush", sb.getConfigLong("maxWaitingWordFlush", 180));
        prop.put("wordCacheMaxCount", sb.getConfigLong(SwitchboardConstants.WORDCACHE_MAX_COUNT, 20000));
        prop.put("crawlPauseProxy", sb.getConfigLong(SwitchboardConstants.PROXY_ONLINE_CAUTION_DELAY, 30000));
        prop.put("crawlPauseLocalsearch", sb.getConfigLong(SwitchboardConstants.LOCALSEACH_ONLINE_CAUTION_DELAY, 30000));
        prop.put("crawlPauseRemotesearch", sb.getConfigLong(SwitchboardConstants.REMOTESEARCH_ONLINE_CAUTION_DELAY, 30000));
        prop.putNum("crawlPauseProxyCurrent", (System.currentTimeMillis() - sb.proxyLastAccess) / 1000);
        prop.putNum("crawlPauseLocalsearchCurrent", (System.currentTimeMillis() - sb.localSearchLastAccess) / 1000);
        prop.putNum("crawlPauseRemotesearchCurrent", (System.currentTimeMillis() - sb.remoteSearchLastAccess) / 1000);

        // table thread pool settings
        prop.put("pool_0_name","Crawler Pool");
        prop.put("pool_0_maxActive", sb.getConfigLong(SwitchboardConstants.CRAWLER_THREADS_ACTIVE_MAX, 0));
        prop.put("pool_0_numActive", sb.crawlQueues.activeWorkerEntries().size());

        prop.put("pool_1_name","Robots.txt Pool");
        prop.put("pool_1_maxActive", sb.getConfigInt(SwitchboardConstants.ROBOTS_TXT_THREADS_ACTIVE_MAX, SwitchboardConstants.ROBOTS_TXT_THREADS_ACTIVE_MAX_DEFAULT));
        prop.put("pool_1_numActive", sb.crawlQueues.activeWorkerEntries().size());

        prop.put("pool_2_name", "httpd Session Pool");
        prop.put("pool_2_maxActive", ConnectionInfo.getServerMaxcount());
        prop.put("pool_2_numActive", ConnectionInfo.getServerCount());

        prop.put("pool", "3");

        /* Connection pools settings */
		prop.put(SwitchboardConstants.HTTP_OUTGOING_POOL_GENERAL_MAX_TOTAL,
				sb.getConfigInt(SwitchboardConstants.HTTP_OUTGOING_POOL_GENERAL_MAX_TOTAL,
						SwitchboardConstants.HTTP_OUTGOING_POOL_GENERAL_MAX_TOTAL_DEFAULT));
		prop.put(SwitchboardConstants.HTTP_OUTGOING_POOL_REMOTE_SOLR_MAX_TOTAL,
				sb.getConfigInt(SwitchboardConstants.HTTP_OUTGOING_POOL_REMOTE_SOLR_MAX_TOTAL,
						SwitchboardConstants.HTTP_OUTGOING_POOL_REMOTE_SOLR_MAX_TOTAL_DEFAULT));
		/* Connection pools stats */
		PoolStats stats = HTTPClient.CONNECTION_MANAGER.getTotalStats();
		prop.put("pool.general.leased", stats.getLeased());
		prop.put("pool.general.available", stats.getAvailable());
		prop.put("pool.general.pending", stats.getPending());

		stats = RemoteInstance.CONNECTION_MANAGER.getTotalStats();
		prop.put("pool.remoteSolr.leased", stats.getLeased());
		prop.put("pool.remoteSolr.available", stats.getAvailable());
		prop.put("pool.remoteSolr.pending", stats.getPending());

        /* Remote searches max loads settings */
		prop.put("remoteSearchRWIMaxLoad", sb.getConfigFloat(SwitchboardConstants.REMOTESEARCH_MAXLOAD_RWI,
				SwitchboardConstants.REMOTESEARCH_MAXLOAD_RWI_DEFAULT));
		prop.put("remoteSearchSolrMaxLoad", sb.getConfigFloat(SwitchboardConstants.REMOTESEARCH_MAXLOAD_SOLR,
				SwitchboardConstants.REMOTESEARCH_MAXLOAD_SOLR_DEFAULT));

		// parse initialization memory settings
		final String Xmx = sb.getConfig("javastart_Xmx", "Xmx600m").substring(3);
		prop.put("Xmx", Xmx.substring(0, Xmx.length() - 1));

        final long diskFree = sb.getConfigLong(SwitchboardConstants.RESOURCE_DISK_FREE_MIN_STEADYSTATE, 3000L);
        final long diskFreeHardlimit = sb.getConfigLong(SwitchboardConstants.RESOURCE_DISK_FREE_MIN_UNDERSHOT, 1000L);
        final long memoryAcceptDHT = sb.getConfigLong(SwitchboardConstants.MEMORY_ACCEPTDHT, 50L);
        final boolean observerTrigger = !MemoryControl.properState();
        prop.put("diskFree", diskFree);
        prop.put("diskFreeHardlimit", diskFreeHardlimit);
		prop.put("diskFreeAutoregulate", sb.getConfigBool(SwitchboardConstants.RESOURCE_DISK_FREE_AUTOREGULATE,
				SwitchboardConstants.RESOURCE_DISK_FREE_AUTOREGULATE_DEFAULT) ? 1 : 0);
		prop.put("diskUsed", sb.getConfigLong(SwitchboardConstants.RESOURCE_DISK_USED_MAX_STEADYSTATE,
				SwitchboardConstants.RESOURCE_DISK_USED_MAX_STEADYSTATE_DEFAULT));
		prop.put("diskUsedHardlimit", sb.getConfigLong(SwitchboardConstants.RESOURCE_DISK_USED_MAX_OVERSHOT,
				SwitchboardConstants.RESOURCE_DISK_USED_MAX_OVERSHOT_DEFAULT));
		prop.put("diskUsedAutoregulate", sb.getConfigBool(SwitchboardConstants.RESOURCE_DISK_USED_AUTOREGULATE,
				SwitchboardConstants.RESOURCE_DISK_USED_AUTOREGULATE_DEFAULT) ? 1 : 0);
        prop.put("memoryAcceptDHT", memoryAcceptDHT);
        if(observerTrigger) prop.put("observerTrigger", "1");

        // return rewrite values for templates
        return prop;
    }

    private static String d(final String a, final String b) {
        return (a == null) ? b : a;
    }
}
