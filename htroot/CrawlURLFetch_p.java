// CrawlURLFetch_p.java

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroBitfield;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaCrawlEURL;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverSwitch;
import de.anomic.data.wikiCode;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.server.serverObjects;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

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
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        serverObjects prop = new serverObjects();
        prop.put("host", "");
        listURLs(prop);                     // List previously saved URLs for easy selection
        listPeers(prop);                    // List known hosts
        
        if (profile == null) {
            profile = ((plasmaSwitchboard)env).profiles.newEntry(
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
                
                if (fetcher != null) fetcher.interrupt();
                fetcher = null;
                if (post.get("source", "").equals("peer") &&
                        post.get("peerhash", "").equals("random")) {
                    fetcher = new URLFetcher(
                            env,
                            profile,
                            frequency);
                } else {
                    URL url = null;
                    if (post.get("source", "").equals("url")) {
                        try {
                            url = new URL(post.get("host", null));
                            if (!savedURLs.contains(url.toNormalform()))
                                savedURLs.add(url.toNormalform());
                            prop.put("host", post.get("host", url.toString()));
                        } catch (MalformedURLException e) {
                            prop.put("host", post.get("host", ""));
                            prop.put("hostError", ERR_HOST_MALFORMED_URL);
                        }
                    } else if (post.get("source", "").equals("savedURL")) {
                        try {
                            url = new URL(post.get("saved", ""));
                        } catch (MalformedURLException e) {
                            /* should never appear, except for invalid input, see above */
                        }
                    } else if (post.get("source", "").equals("peer")) {
                        yacySeed ys = null;
                        try {
                            ys = yacyCore.seedDB.getConnected(post.get("peerhash", ""));
                            if (ys != null) {
                                url = new URL("http://" + ys.getAddress() + "/yacy/urllist.html");
                            } else {
                                prop.put("peerError", ERR_PEER_OFFLINE);
                                prop.put("peerError_hash", post.get("peerhash", ""));
                            }
                        } catch (MalformedURLException e) {
                            prop.put("peerError", ERR_PEER_GENERAL_CONN);
                            prop.put("peerError_hash", post.get("peerhash", ""));
                            prop.put("peerError_name", ys.getName());
                        }
                    }
                    
                    if (url != null) {
                        fetcher = new URLFetcher(
                                env,
                                profile,
                                url,
                                frequency);
                    }
                }
                if (fetcher != null)
                    fetcher.start();
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
                                profile,
                                fetcher.delay);
                    } else {
                        fetcher = new URLFetcher(
                                env,
                                profile,
                                fetcher.url,
                                fetcher.delay);
                    }
                    fetcher.start();
                } else {
                    prop.put("threadError", ERR_THREAD_RESUME);
                }
            }
        }
        
        if (fetcher != null) {
            prop.put("runs", 1);
            prop.put("runs_status",
                    ((fetcher.paused && fetcher.isAlive()) ? STAT_THREAD_PAUSED :
                    (fetcher.isAlive()) ? STAT_THREAD_ALIVE : STAT_THREAD_STOPPED));
            prop.put("runs_totalRuns",          URLFetcher.totalRuns);
            prop.put("runs_totalFetchedURLs",   URLFetcher.totalFetchedURLs);
            prop.put("runs_totalFailedURLs",    URLFetcher.totalFailed);
            prop.put("runs_lastRun",            fetcher.lastRun);
            prop.put("runs_lastFetchedURLs",    fetcher.lastFetchedURLs);
            prop.put("runs_lastServerResponse", (fetcher.lastServerResponse == null)
                    ? "" : fetcher.lastServerResponse);
            
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
        prop.put("saved", 1);
        for (int i=0; i<savedURLs.size(); i++)
            prop.put("saved_urls_" + i + "_url", savedURLs.get(i));
        prop.put("saved_urls", savedURLs.size());
        return savedURLs.size();
    }
    
    private static int listPeers(serverObjects prop) {
        int peerCount = 0;
        if (yacyCore.seedDB != null && yacyCore.seedDB.sizeConnected() > 0) {
            prop.put("peersKnown", 1);
            try {
                TreeMap hostList = new TreeMap();
                final Enumeration e = yacyCore.seedDB.seedsConnected(true, false, null, (float) 0.0);
                while (e.hasMoreElements()) {
                    yacySeed seed = (yacySeed) e.nextElement();
                    if (seed != null) hostList.put(seed.get(yacySeed.NAME, "nameless"),seed.hash);
                }

                String peername;
                while ((peername = (String) hostList.firstKey()) != null) {
                    final String Hash = (String) hostList.get(peername);
                    prop.put("peersKnown_peers_" + peerCount + "_hash", Hash);
                    prop.put("peersKnown_peers_" + peerCount + "_name", peername);
                    hostList.remove(peername);
                    peerCount++;
                }
            } catch (Exception e) { /* no comment :P */ }
            prop.put("peersKnown_peers", peerCount);
        } else {
            prop.put("peersKnown", 0);
        }
        return peerCount;
    }
    
    private static long getDate(String count, String type) {
        long r = 0;
        if (count != null && count.matches("\\d+")) r = Long.parseLong(count);
        if (r < 1) return -1;
        
        r *= 3600000;
        if (type.equals("weeks"))       return r * 24 * 7;
        else if (type.equals("days"))   return r * 24;
        else if (type.equals("hours"))  return r;
        else return -1;
    }
    
    public static class URLFetcher extends Thread {
        
        public static final long DELAY_ONCE = -1;
        public static final long DELAY_SELF_DET = 0;
        
        public static int totalRuns = 0;
        public static int totalFetchedURLs = 0;
        public static int totalFailed = 0;
        
        public final HashMap failed = new HashMap();
        
        public int        lastFetchedURLs = 0;
        public long       lastRun = 0;
        public String     lastServerResponse = null;
        public int        lastFailed = 0;
        
        public final URL url;
        public final long delay;
        public final plasmaSwitchboard sb;
        public final plasmaCrawlProfile.entry profile;
        
        public boolean paused = false;
        
        public URLFetcher(
                serverSwitch env,
                plasmaCrawlProfile.entry profile,
                URL url,
                long delayMs) {
            if (env == null || profile == null || url == null)
                throw new NullPointerException("env, profile or url must not be null");
            this.sb = (plasmaSwitchboard)env;
            this.profile = profile;
            this.url = url;
            this.delay = delayMs;
            this.setName("URLFetcher");
        }
        
        public URLFetcher(
                serverSwitch env,
                plasmaCrawlProfile.entry profile,
                long delayMs) {
            if (env == null || profile == null)
                throw new NullPointerException("env or profile must not be null");
            this.sb = (plasmaSwitchboard)env;
            this.profile = profile;
            this.url = null;
            this.delay = delayMs;
            this.setName("URLFetcher");
        }
        
        public void run() {
            this.paused = false;
            long start;
            URL url;
            while (!isInterrupted()) {
                try {
                    start = System.currentTimeMillis();
                    url = getDLURL();
                    if (url == null) {
                        serverLog.logSevere(this.getName(), "canceled because no valid URL for the URL-list could be determinded");
                        return;
                    }
                    totalFetchedURLs += stackURLs(getURLs(url));
                    lastRun = System.currentTimeMillis() - start;
                    totalRuns++;
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
        
        private URL getDLURL() {
            if (this.url != null) return this.url; 
            
            // choose random seed
            yacySeed ys = null;
            Enumeration e = yacyCore.seedDB.seedsConnected(true, false, null, 0F);
            int num = new Random().nextInt(yacyCore.seedDB.sizeConnected()) + 1;
            Object o;
            for (int i=0; i<num && e.hasMoreElements(); i++) {
                o = e.nextElement();
                if (o != null) ys = (yacySeed)o;
            }
            if (ys == null) return null;
            
            try {
                return new URL("http://" + ys.getAddress() + "/yacy/urllist.html");
            } catch (MalformedURLException ee) { return null; }
        }
        
        private int stackURLs(String[] urls) throws InterruptedException {
            this.lastFailed = 0;
            if (urls == null) return 0;
            String reason;
            for (int i=0; i<urls.length && !isInterrupted(); i++) {
                serverLog.logFinest(this.getName(), "stacking " + urls[i]);
                reason = this.sb.sbStackCrawlThread.stackCrawl(
                        urls[i],
                        null,
                        yacyCore.seedDB.mySeed.hash,
                        null,
                        new Date(),
                        this.profile.generalDepth(),
                        this.profile);
                if (reason != null)  {
                    this.lastFailed++;
                    totalFailed++;
                    this.failed.put(urls[i], reason);
                    try {
                        plasmaCrawlEURL.Entry ee = this.sb.errorURL.newEntry(
                                new URL(urls[i]),
                                null,
                                yacyCore.seedDB.mySeed.hash,
                                yacyCore.seedDB.mySeed.hash,
                                "",
                                reason,
                                new kelondroBitfield());
                        ee.store();
                        this.sb.errorURL.stackPushEntry(ee);
                    } catch (MalformedURLException e) {  }
                }
            }
            return urls.length - this.lastFailed;
        }
        
        private String[] getURLs(URL url) {
            if (url == null) return null;
            String[] r = null;
            try {
                httpc con = httpc.getInstance(
                        url.getHost(),
                        url.getHost(),
                        url.getPort(),
                        15000,
                        url.getProtocol().equals("https"));
                
                httpHeader header = new httpHeader();
                header.put(httpHeader.ACCEPT_ENCODING, "US-ASCII");
                header.put(httpHeader.HOST, url.getHost());
                
                httpc.response res = con.GET(url.getPath(), header);
                serverLog.logFine(this.getName(), "downloaded URL-list from " + url + " (" + res.statusCode + ")");
                this.lastServerResponse = res.statusCode + " (" + res.statusText + ")";
                if (res.status.startsWith("2")) {
                    byte[] cbs = res.writeContent();
                    String encoding = res.responseHeader.getCharacterEncoding();
                    
                    if (encoding == null) encoding = "US-ASCII";
                    r = parseText(wikiCode.deReplaceHTMLEntities(new String(cbs, encoding)));
                }
                httpc.returnInstance(con);
            } catch (IOException e) {  }
            return r;
        }
        
        private static String[] parseText(String text) {
            return text.split("\n");
        }
    }
}
