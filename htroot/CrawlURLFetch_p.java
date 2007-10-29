// CrawlURLFetch_p.java
// -------------------------------------
// part of YACY
//
// (C) 2007 by Franz Brausze
//
// last change: $LastChangedDate: $ by $LastChangedBy: $
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeMap;

import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaCrawlZURL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverSwitch;
import de.anomic.http.httpHeader;
import de.anomic.http.httpRemoteProxyConfig;
import de.anomic.http.httpc;
import de.anomic.server.serverObjects;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;
import de.anomic.yacy.yacyVersion;

public class CrawlURLFetch_p {
    
    private static final long ERR_DATE = 1;
    private static final long ERR_HOST_MALFORMED_URL = 1;
    private static final long ERR_PEER_GENERAL_CONN = 1;
    private static final long ERR_PEER_OFFLINE = 2;
    private static final long ERR_THREAD_STOP = 1;
    private static final long ERR_THREAD_RESUME = 2;
    
    private static final long STAT_THREAD_ALIVE = 0;
    private static final long STAT_THREAD_STOPPED = 1;
    private static final long STAT_THREAD_PAUSED = 2;
    
    private static URLFetcher fetcher = null;
    private static plasmaCrawlProfile.entry profile = null;
    private static ArrayList savedURLs = new ArrayList();
    
    public static plasmaCrawlProfile.entry getCrawlProfile(serverSwitch env) {
        if (profile == null) {
            profile = ((plasmaSwitchboard)env).profilesActiveCrawls.newEntry(
                    "URLFetcher",           // Name
                    null,                   // URL
                    ".*", ".*",             // General / specific filter
                    0, 0,                   // General / specific depth
                    -1, -1, -1,             // Recrawl / Dom-filter depth / Dom-max-pages
                    true,                   // Crawl query
                    true, true,             // Index text / media
                    false, true,            // Store in HT- / TX-Cache
                    false,                  // Remote indexing
                    true, false, false);    // Exclude static / dynamic / parent stopwords
        }
        return profile;
    }
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        serverObjects prop = new serverObjects();
        prop.put("host", "");
        
        // List previously saved URLs for easy selection
        listURLs(prop);
        
        // List known hosts
        listPeers(prop,
                post != null && post.containsKey("checkPeerURLCount"),
                ((plasmaSwitchboard)env).remoteProxyConfig);
        
        if (post != null) {
            if (post.containsKey("start")) {
                long frequency = URLFetcher.DELAY_ONCE;
                if (post.containsKey("reg")) {
                    if (post.get("reg", "").equals("self_det")) {
                        frequency = URLFetcher.DELAY_SELF_DET;
                    } else if (post.get("reg", "").equals("delay")) {
                        frequency = getDate(post.get("frequency", ""), post.get("freq_type", ""));
                        if (frequency == -1)
                            prop.put("freqError", ERR_DATE);
                    }
                }
                
                int count = 50;
                if (post.get("amount", "").matches("\\d+")) {
                    count = Integer.parseInt(post.get("amount", ""));
                    if (count > 999) count = 999;
                }
                
                if (fetcher != null) fetcher.interrupt();
                fetcher = null;
                    if (post.get("source", "").equals("peer") &&
                            post.get("peerhash", "").equals("random")) {
                        fetcher = new URLFetcher(
                                env,
                                getCrawlProfile(env),
                                count,
                                frequency);
                    } else {
                        yacyURL url = null;
                        if (post.get("source", "").equals("url")) {
                            try {
                                url = new yacyURL(post.get("host", null), null);
                                if (!savedURLs.contains(url.toNormalform(true, true)))
                                    savedURLs.add(url.toNormalform(true, true));
                                prop.put("host", post.get("host", url.toString()));
                            } catch (MalformedURLException e) {
                                prop.put("host", post.get("host", ""));
                                prop.put("hostError", ERR_HOST_MALFORMED_URL);
                            }
                        } else if (post.get("source", "").equals("savedURL")) {
                            try {
                                url = new yacyURL(post.get("saved", ""), null);
                            } catch (MalformedURLException e) {
                                /* should never appear, except for invalid input, see above */
                            }
                        } else if (post.get("source", "").equals("peer")) {
                            yacySeed ys = null;
                            ys = yacyCore.seedDB.get(post.get("peerhash", null));
                            if (ys != null) {
                                if ((url = URLFetcher.getListServletURL(
                                        ys.getPublicAddress(),
                                        URLFetcher.MODE_LIST,
                                        count,
                                        yacyCore.seedDB.mySeed().hash)) == null) {
                                    prop.put("peerError", ERR_PEER_GENERAL_CONN);
                                    prop.put("peerError_hash", post.get("peerhash", ""));
                                    prop.put("peerError_name", ys.getName());
                                }
                            } else {
                                prop.put("peerError", ERR_PEER_OFFLINE);
                                prop.put("peerError_hash", post.get("peerhash", ""));
                            }
                        }
                        
                        if (url != null) {
                            fetcher = new URLFetcher(
                                    env,
                                    getCrawlProfile(env),
                                    url,
                                    count,
                                    frequency);
                        }
                    }
                    if (fetcher != null) fetcher.start();
            }
            else if (post.containsKey("stop")) {
                if (fetcher != null) {
                    fetcher.interrupt();
                } else {
                    prop.put("threadError", ERR_THREAD_STOP);
                }
            }
            else if (post.containsKey("restart")) {
                if (fetcher != null) {
                        fetcher.interrupt();
                        if (fetcher.url == null) {
                            fetcher = new URLFetcher(
                                    env,
                                    getCrawlProfile(env),
                                    fetcher.count,
                                    fetcher.delay);
                        } else {
                            fetcher = new URLFetcher(
                                    env,
                                    getCrawlProfile(env),
                                    fetcher.url,
                                    fetcher.count,
                                    fetcher.delay);
                        }
                        fetcher.start();
                } else {
                    prop.put("threadError", ERR_THREAD_RESUME);
                }
            }
            else if (post.containsKey("resetDelay")) {
                final long frequency = getDate(post.get("newDelay", ""), "minutes");
                if (frequency == -1) {
                    prop.put("freqError", ERR_DATE);
                } else {
                    fetcher.delay = frequency;
                }
            }
            prop.put("LOCATION", "/CrawlURLFetch_p.html");
        }
        
        if (fetcher != null) {
            prop.put("runs", "1");
            prop.put("runs_status",
                    ((fetcher.paused && fetcher.isAlive()) ? STAT_THREAD_PAUSED :
                    (fetcher.isAlive()) ? STAT_THREAD_ALIVE : STAT_THREAD_STOPPED));
            prop.putNum("runs_totalRuns",          URLFetcher.totalRuns);
            prop.putNum("runs_totalFetchedURLs",   URLFetcher.totalFetchedURLs);
            prop.putNum("runs_totalFailedURLs",    URLFetcher.totalFailed);
            prop.putNum("runs_lastRun",            fetcher.lastRun);
            prop.putNum("runs_lastFetchedURLs",    fetcher.lastFetchedURLs);
            prop.put("runs_lastServerResponse", (fetcher.lastServerResponse == null)
                    ? "" : fetcher.lastServerResponse);
            prop.putNum("runs_curDelay", (int)(fetcher.delay / 60000));
            
            Iterator it = fetcher.failed.keySet().iterator();
            int i = 0;
            Object key;
            while (it.hasNext()) {
                key = it.next();
                prop.put("runs_error_" + i + "_reason", fetcher.failed.get(key));
                prop.put("runs_error_" + i + "_url", (String)key);
                i++;
            }
            prop.put("runs_error", i);
        }
        
        return prop;
    }
    
    private static int listURLs(serverObjects prop) {
        if (savedURLs.size() == 0) return 0;
        prop.put("saved", "1");
        for (int i=0; i<savedURLs.size(); i++)
            prop.put("saved_urls_" + i + "_url", savedURLs.get(i));
        prop.putNum("saved_urls", savedURLs.size());
        return savedURLs.size();
    }
    
    private static int listPeers(serverObjects prop, boolean checkURLCount, httpRemoteProxyConfig theRemoteProxyConfig) {
        int peerCount = 0;
        TreeMap hostList = new TreeMap();
        String peername;
        if (yacyCore.seedDB != null && yacyCore.seedDB.sizeConnected() > 0) {
            final Iterator e = yacyCore.seedDB.seedsConnected(true, false, null, yacyVersion.YACY_PROVIDES_CRAWLS_VIA_LIST_HTML);
            int dbsize;
            while (e.hasNext()) {
                yacySeed seed = (yacySeed) e.next();
                if (seed != null && !seed.hash.equals(yacyCore.seedDB.mySeed().hash)) {
                    peername = seed.get(yacySeed.NAME, "nameless");
                    if (checkURLCount && (dbsize = getURLs2Fetch(seed, theRemoteProxyConfig)) > 0) {
                        hostList.put(peername + " (" + dbsize + ")", seed.hash);
                    } else {
                        hostList.put(peername, seed.hash);
                    }
                }
            }
        }
        
        if (hostList.size() > 0) {
            while (!hostList.isEmpty() && (peername = (String) hostList.firstKey()) != null) {
                final String hash = (String) hostList.get(peername);
                prop.put("peersKnown_peers_" + peerCount + "_hash", hash);
                prop.put("peersKnown_peers_" + peerCount + "_name", peername);
                hostList.remove(peername);
                peerCount++;
            }
            prop.put("peersKnown_peers", peerCount);
            prop.put("peersKnown", "1");
        } else {
            prop.put("peersKnown", "0");
        }
        return peerCount;
    }
    
    private static int getURLs2Fetch(yacySeed seed, httpRemoteProxyConfig theRemoteProxyConfig) {
        try {
            String answer = new String(httpc.wget(
                    URLFetcher.getListServletURL(seed.getPublicAddress(), URLFetcher.MODE_COUNT, 0, null),
                    seed.getIP(),
                    5000,
                    null, null,
                    theRemoteProxyConfig,
                    null,
                    null));
            if (answer.matches("\\d+"))
                return Integer.parseInt(answer);
            else {
                serverLog.logFine("URLFETCHER", "Retrieved invalid answer from " + seed.getName() + ": '" + answer + "'");
                return -1;
            }
        } catch (MalformedURLException e) {
            /* should not happen */
            return -3;
        } catch (IOException e) {
            return -2;
        }
    }
    
    private static long getDate(String count, String type) {
        long r = 0;
        if (count != null && count.matches("\\d+")) r = Long.parseLong(count);
        if (r < 1) return -1;
        
        r *= 60000;
        if (type.equals("days"))            return r * 60 * 24;
        else if (type.equals("hours"))      return r * 60;
        else if (type.equals("minutes"))    return r;
        else return -1;
    }
    
    public static class URLFetcher extends Thread {
        
        public static final long DELAY_ONCE = -1;
        public static final long DELAY_SELF_DET = 0;
        
        public static final int MODE_LIST = 0;
        public static final int MODE_COUNT = 1;
        
        public static int totalRuns = 0;
        public static int totalFetchedURLs = 0;
        public static int totalFailed = 0;
        
        public final HashMap failed = new HashMap();
        
        public int        lastFetchedURLs = 0;
        public long       lastRun = 0;
        public String     lastServerResponse = null;
        public int        lastFailed = 0;
        
        public final yacyURL url;
        public final int count;
        public long delay;
        public final plasmaSwitchboard sb;
        public final plasmaCrawlProfile.entry profile;
        
        public boolean paused = false;
        
        public static yacyURL getListServletURL(String host, int mode, int count, String peerHash) {
            String r = "http://" + host + "/yacy/list.html?list=queueUrls&display=";
            
            switch (mode) {
            case MODE_LIST: r += "list"; break;
            case MODE_COUNT: r += "count"; break;
            }
            
            if (count > 0) r += "&count=" + count;
            
            if (peerHash != null && peerHash.length() > 0) {
                r += "&iam=" + peerHash;
            } else if (mode == MODE_LIST) {
                r += "&iam=" + yacyCore.seedDB.mySeed().hash;
            }
            
            try {
                return new yacyURL(r, null);
            } catch (MalformedURLException e) {
                return null;
            }
        }
        
        public URLFetcher(
                serverSwitch env,
                plasmaCrawlProfile.entry profile,
                yacyURL url,
                int count,
                long delayMs) {
            if (env == null || profile == null || url == null)
                throw new NullPointerException("env, profile or url must not be null");
            this.sb = (plasmaSwitchboard)env;
            this.profile = profile;
            this.url = url;
            this.count = count;
            this.delay = delayMs;
            this.setName("URLFetcher");
        }
        
        public URLFetcher(
                serverSwitch env,
                plasmaCrawlProfile.entry profile,
                int count,
                long delayMs) {
            if (env == null || profile == null)
                throw new NullPointerException("env or profile must not be null");
            this.sb = (plasmaSwitchboard)env;
            this.profile = profile;
            this.url = null;
            this.count = count;
            this.delay = delayMs;
            this.setName("URLFetcher");
        }
        
        public void run() {
            this.paused = false;
            long start;
            yacyURL url;
            while (!isInterrupted()) {
                try {
                    start = System.currentTimeMillis();
                    url = getDLURL();
                    if (url == null) {
                        serverLog.logSevere(this.getName(), "canceled because no valid URL for the URL-list could be determinded");
                        return;
                    }
                    totalFetchedURLs += stackURLs(getURLs(url));
                    this.lastRun = System.currentTimeMillis() - start;
                    totalRuns++;
                    serverLog.logInfo(this.getName(), "Loaded " + this.lastFetchedURLs + " URLs from " + url + " in " + this.lastRun + " ms into stackcrawler.");
                    if (this.delay < 0 || isInterrupted()) {
                        return;
                    } else synchronized (this) {
                        if (this.delay == 0) {
                            this.paused = true;
                            while (this.paused) this.wait();
                        } else {
                            this.paused = true;
                            this.wait(this.delay);
                        }
                    }
                    this.paused = false;
                } catch (InterruptedException e) { return; }
            }
        }
        
        private yacyURL getDLURL() {
            if (this.url != null) return this.url; 
            
            // choose random seed
            yacySeed ys = null;
            Iterator e = yacyCore.seedDB.seedsConnected(true, false, null, yacyVersion.YACY_PROVIDES_CRAWLS_VIA_LIST_HTML);
            int num = new Random().nextInt(yacyCore.seedDB.sizeConnected()) + 1;
            Object o;
            for (int i=0; i<num && e.hasNext(); i++) {
                o = e.next();
                if (o != null) ys = (yacySeed)o;
            }
            if (ys == null) return null;
            
            return getListServletURL(ys.getPublicAddress(), MODE_LIST, this.count, yacyCore.seedDB.mySeed().hash);
        }
        
        private int stackURLs(ArrayList /*of yacyURL*/ urls) throws InterruptedException {
            this.lastFailed = 0;
            this.lastFetchedURLs = 0;
            this.failed.clear();
            
            if (urls == null) return 0;
            String reason;
            yacyURL url;
            for (int i = 0; i < urls.size() && !isInterrupted(); i++) {
                url = (yacyURL) urls.get(i);
                reason = this.sb.crawlStacker.stackCrawl(
                        url,
                        null,
                        yacyCore.seedDB.mySeed().hash,
                        null,
                        new Date(),
                        this.profile.generalDepth(),
                        this.profile);
                if (reason == null) {
                    serverLog.logFine(this.getName(), "stacked " + url);
                    this.lastFetchedURLs++;
                } else {
                    serverLog.logFine(this.getName(), "error on stacking " + url + ": " + reason);
                    this.lastFailed++;
                    totalFailed++;
                    this.failed.put(url, reason);
                    plasmaCrawlZURL.Entry ee = this.sb.crawlQueues.errorURL.newEntry(
                                url,
                                reason);
                    ee.store();
                    this.sb.crawlQueues.errorURL.push(ee);
                }
            }
            return this.lastFetchedURLs;
        }
        
        private ArrayList /*of yacyURL */ getURLs(yacyURL url) {
            if (url == null) return null;
            ArrayList a = new ArrayList();
            try {
                httpc con = new httpc(
                        url.getHost(),
                        url.getHost(),
                        url.getPort(),
                        15000,
                        url.getProtocol().equals("https"),
                        plasmaSwitchboard.getSwitchboard().remoteProxyConfig, null, null);
                
                httpHeader header = new httpHeader();
                header.put(httpHeader.ACCEPT_ENCODING, "US-ASCII");
                header.put(httpHeader.HOST, url.getHost());
                
                httpc.response res = con.GET(url.getPath() + "?" + url.getQuery(), header);
                serverLog.logFine(this.getName(), "downloaded URL-list from " + url + " (" + res.statusCode + ")");
                this.lastServerResponse = res.statusCode + " (" + res.statusText + ")";
                if (res.status.startsWith("2")) {
                	serverByteBuffer sbb = new serverByteBuffer();
                    //byte[] cbs = res.writeContent();
                	res.writeContent(sbb, null);
                    String encoding = res.responseHeader.getCharacterEncoding();
                    
                    if (encoding == null) encoding = "US-ASCII";
                    String[] s = (new String(sbb.getBytes(), encoding)).split("\n");
                    for (int i = 0; i < s.length; i++) {
                        try {
                            a.add(new yacyURL(s[i], null));
                        } catch (MalformedURLException e) {}
                    }
                }
                con.close();
            } catch (IOException e) {  }
            return a;
        }
        
    }
}
