// Performace_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last major change: 16.02.2005
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// You must compile this file with
// javac -classpath .:../classes Network.java
// if the shell's current path is HTROOT

import java.util.Iterator;

import org.apache.commons.pool.impl.GenericObjectPool;

import de.anomic.http.httpHeader;
import de.anomic.http.httpd;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverThread;

public class Performance_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch sb) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) sb;
        serverObjects prop = new serverObjects();

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
        long idlesleep, busysleep;
        int queuesize;
        threads = switchboard.threadNames();
        int c = 0;
        long idleCycles, busyCycles;
        while (threads.hasNext()) {
            threadName = (String) threads.next();
            thread = switchboard.getThread(threadName);
            
            // set values to templates
            prop.put("table_" + c + "_threadname", threadName);
            prop.put("table_" + c + "_shortdescr", thread.getShortDescription());
            prop.put("table_" + c + "_longdescr", thread.getLongDescription());
            queuesize = thread.getJobCount();
            prop.put("table_" + c + "_queuesize", (queuesize == Integer.MAX_VALUE) ? "unlimited" : ("" + queuesize));
            
            blocktime = thread.getBlockTime();
            sleeptime = thread.getSleepTime();
            exectime = thread.getExecTime();
            idleCycles = thread.getIdleCycles();
            busyCycles = thread.getBusyCycles();
            prop.put("table_" + c + "_blocktime", blocktime / 1000);
            prop.put("table_" + c + "_blockpercent", "" + (100 * blocktime / blocktime_total));
            prop.put("table_" + c + "_sleeptime", sleeptime / 1000);
            prop.put("table_" + c + "_sleeppercent", "" + (100 * sleeptime / sleeptime_total));
            prop.put("table_" + c + "_exectime", exectime / 1000);
            prop.put("table_" + c + "_execpercent", "" + (100 * exectime / exectime_total));
            prop.put("table_" + c + "_totalcycles", "" + (idleCycles + busyCycles));
            prop.put("table_" + c + "_idlecycles", "" + idleCycles);
            prop.put("table_" + c + "_busycycles", "" + busyCycles);
            prop.put("table_" + c + "_sleeppercycle", ((idleCycles + busyCycles) == 0) ? "-" : ("" + (sleeptime / (idleCycles + busyCycles))));
            prop.put("table_" + c + "_execpercycle", (busyCycles == 0) ? "-" : ("" + (exectime / busyCycles)));
            
            if ((post != null) && (post.containsKey("delaysubmit"))) {
                // load with new values
                idlesleep = Long.parseLong((String) post.get(threadName + "_idlesleep", "1"));
                busysleep = Long.parseLong((String) post.get(threadName + "_busysleep", "1"));

		// check values to prevent short-cut loops
		if (idlesleep == 0) idlesleep = 1000;
                
                // on-the-fly re-configuration
                switchboard.setThreadSleep(threadName, idlesleep, busysleep);
                switchboard.setConfig(threadName + "_idlesleep", idlesleep);
                switchboard.setConfig(threadName + "_busysleep", busysleep);
            } else {
                // load with old values
                idlesleep = Long.parseLong(switchboard.getConfig(threadName + "_idlesleep" , "1000"));
                busysleep = Long.parseLong(switchboard.getConfig(threadName + "_busysleep", "1000"));
            }
            prop.put("table_" + c + "_idlesleep", idlesleep);
            prop.put("table_" + c + "_busysleep", busysleep);
            
            c++;
        }
        prop.put("table", c);
        
        if ((post != null) && (post.containsKey("cacheSizeSubmit"))) {
            int wordCacheMax = Integer.parseInt((String) post.get("wordCacheMax", "10000"));
            switchboard.setConfig("wordCacheMax", "" + wordCacheMax);
            switchboard.wordIndex.setMaxWords(wordCacheMax);
            int maxWaitingWordFlush = Integer.parseInt((String) post.get("maxWaitingWordFlush", "180"));
            switchboard.setConfig("maxWaitingWordFlush", "" + maxWaitingWordFlush);
        }
        
        if ((post != null) && (post.containsKey("poolConfig"))) {
            GenericObjectPool.Config crawlerPoolConfig = switchboard.cacheLoader.getPoolConfig();
            int maxActive = Integer.parseInt(post.get("Crawler Pool_maxActive","8"));
            int maxIdle = Integer.parseInt(post.get("Crawler Pool_maxIdle","4"));
            int minIdle = Integer.parseInt(post.get("Crawler Pool_minIdle","0"));
            
            crawlerPoolConfig.minIdle = (minIdle > maxIdle) ? maxIdle/2 : minIdle;
            crawlerPoolConfig.maxIdle = (maxIdle > maxActive) ? maxActive/2 : maxIdle;
            crawlerPoolConfig.maxActive = maxActive;    
            
            plasmaSwitchboard.crawlSlots = maxActive;
            switchboard.cacheLoader.setPoolConfig(crawlerPoolConfig);
            
            serverThread httpd = switchboard.getThread("10_httpd");
            GenericObjectPool.Config httpdPoolConfig = ((serverCore)httpd).getPoolConfig();
            maxActive = Integer.parseInt(post.get("httpd Session Pool_maxActive","8"));
            maxIdle = Integer.parseInt(post.get("httpd Session Pool_maxIdle","4"));
            minIdle = Integer.parseInt(post.get("httpd Session Pool_minIdle","0"));
            
            httpdPoolConfig.minIdle = (minIdle > maxIdle) ? maxIdle/2 : minIdle;
            httpdPoolConfig.maxIdle = (maxIdle > maxActive) ? maxActive/2 : maxIdle;
            httpdPoolConfig.maxActive = maxActive;    
            
            ((serverCore)httpd).maxSessions = maxActive;
            ((serverCore)httpd).setPoolConfig(httpdPoolConfig);            
        }        
        
        // table cache settings
        prop.put("wordCacheRAMSize", switchboard.wordIndex.wordCacheRAMSize());
        prop.put("maxURLinWordCache", "" + switchboard.wordIndex.maxURLinWordCache());
        prop.put("maxWaitingWordFlush", switchboard.getConfig("maxWaitingWordFlush", "180"));
        prop.put("wordCacheMax", switchboard.getConfig("wordCacheMax", "10000"));
        
        int[] asizes = switchboard.wordIndex.assortmentSizes();
        for (int i = 0; i < asizes.length; i++) {
            prop.put("assortmentCluster_" + i + "_assortmentSlot", i + 1);
            prop.put("assortmentCluster_" + i + "_assortmentSize", asizes[i]);
        }
        prop.put("assortmentCluster", asizes.length);
        
        // table thread pool settings
        GenericObjectPool.Config crawlerPoolConfig = switchboard.cacheLoader.getPoolConfig();
        prop.put("pool_0_name","Crawler Pool");
        prop.put("pool_0_maxActive",crawlerPoolConfig.maxActive);
        prop.put("pool_0_maxIdle",crawlerPoolConfig.maxIdle);
        prop.put("pool_0_minIdle",crawlerPoolConfig.maxIdle);
        
        serverThread httpd = switchboard.getThread("10_httpd");
        GenericObjectPool.Config httpdPoolConfig = ((serverCore)httpd).getPoolConfig();
        prop.put("pool_1_name","httpd Session Pool");
        prop.put("pool_1_maxActive",httpdPoolConfig.maxActive);
        prop.put("pool_1_maxIdle",httpdPoolConfig.maxIdle);
        prop.put("pool_1_minIdle",httpdPoolConfig.maxIdle);                
        prop.put("pool",2);
        
        
        // return rewrite values for templates
        return prop;
    }
    
}
