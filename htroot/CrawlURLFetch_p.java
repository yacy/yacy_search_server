// CrawlURLFetch_p.java

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.Enumeration;
import java.util.TreeMap;

import de.anomic.net.URL;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverSwitch;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.server.serverObjects;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class CrawlURLFetch_p {
    
    private static URLFetcher fetcher = null;
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        serverObjects prop = new serverObjects();
        
        prop.put("host", "");
        
        // List known hosts for message sending
        if (yacyCore.seedDB != null && yacyCore.seedDB.sizeConnected() > 0) {
            prop.put("peersKnown", 1);
            int peerCount = 0;
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
        
        if (post != null) {
            if (post.containsKey("start")) {
                try {
                    
                    long frequency = -1;
                    if (post.containsKey("regularly"))
                        frequency = getDate(post.get("frequency", ""), post.get("freq_type", ""));
                    
                    String t = post.get("type", "text");
                    int type = -1;
                    if (t.equals("text")) {
                        type = URLFetcher.TYPE_TEXT;
                    } else if (t.equals("xml")) {
                        type = URLFetcher.TYPE_XML;
                    }
                    
                    URL url = new URL(post.get("host", null));
                    prop.put("host", post.get("host", ""));
                    
                    if (type > -1) {
                        if (frequency > -1) {
                            fetcher = new URLFetcher(
                                    env,
                                    ((plasmaSwitchboard)env).defaultProxyProfile,
                                    url,
                                    frequency,
                                    type);
                        } else {    // only fetch once
                            fetcher = new URLFetcher(
                                    env,
                                    ((plasmaSwitchboard)env).defaultProxyProfile,
                                    url,
                                    type);
                        }
                        fetcher.start();
                    }   
                } catch (MalformedURLException e) {
                    prop.put("host", post.get("host", ""));
                    prop.put("hostError", 1);
                }
            } else if (post.containsKey("stop")) {
                fetcher.interrupt();
            }
        }
        
        if (fetcher != null) {
            prop.put("runs", 1);
            prop.put("runs_status", (fetcher.isRunning()) ? 0 : (fetcher.isPaused()) ? 2 : 1);
            prop.put("runs_totalRuns",          URLFetcher.totalRuns);
            prop.put("runs_totalFetchedURLs",   URLFetcher.totalFetchedURLs);
            prop.put("runs_totalFailedURLs",    URLFetcher.totalFailed);
            prop.put("runs_lastRun",            URLFetcher.lastRun);
            prop.put("runs_lastFetchedURLs",    URLFetcher.lastFetchedURLs);
            prop.put("runs_lastServerResponse", (URLFetcher.lastServerResponse == null)
                    ? "" : URLFetcher.lastServerResponse);
        }
        
        return prop;
    }
    
    private static long getDate(String count, String type) {
        long r = 0;
        if (count != null && count.matches("\\d+")) r = Long.parseLong(count);
        if (r < 1) return -1;
        
        r *= 3600 * 24;
        if (type.equals("weeks"))       return r * 24 * 7;
        else if (type.equals("days"))   return r * 24;
        else if (type.equals("hours"))  return r;
        else return -1;
    }
    
    public static class URLFetcher extends Thread {
        
        public static final int TYPE_TEXT = 0;
        public static final int TYPE_XML = 1;
        
        public static int lastFetchedURLs = 0;
        public static long lastRun = 0;
        public static String lastServerResponse = null;
        public static int lastFailed = 0;
        public static int totalRuns = 0;
        public static int totalFetchedURLs = 0;
        public static int totalFailed = 0;
        
        private final URL url;
        private final long delay;
        private final int type;
        private final plasmaSwitchboard sb;
        private final plasmaCrawlProfile.entry profile;
        
        private boolean running = false;
        private boolean paused = false;
        
        public URLFetcher(
                serverSwitch env,
                plasmaCrawlProfile.entry profile,
                URL url,
                int type) {
            this.sb = (plasmaSwitchboard)env;
            this.profile = profile;
            this.url = url;
            this.type = type;
            this.delay = 0;
            this.setName("URL-Fetcher");
        }
        
        public URLFetcher(
                serverSwitch env,
                plasmaCrawlProfile.entry profile,
                URL url,
                long delayMs,
                int type) {
            this.sb = (plasmaSwitchboard)env;
            this.profile = profile;
            this.url = url;
            this.delay = delayMs;
            this.type = type;
            this.setName("URL-Fetcher");
        }
        
        public boolean isRunning() { return this.running; }
        public boolean isPaused() { return this.paused; }
        
        public void run() {
            this.running = true;
            this.paused = false;
            long start;
            while (!isInterrupted() && this.delay > 0) {
                try {
                    start = System.currentTimeMillis();
                    totalFetchedURLs += addURLs();
                    lastRun = System.currentTimeMillis() - start;
                    totalRuns++;
                    this.paused = true;
                    this.wait(this.delay);
                    this.paused = false;
                } catch (InterruptedException e) { break; }
            }
            this.running = false;
        }
        
        private int addURLs() throws InterruptedException {
            String[] urls = getURLs();
            lastFailed = 0;
            if (urls == null) return 0;
            String reason;
            for (int i=0; i<urls.length; i++) {
                reason = this.sb.sbStackCrawlThread.stackCrawl(
                        urls[i],
                        null,
                        yacyCore.seedDB.mySeed.hash,
                        "PROXY",
                        new Date(),
                        this.profile.generalDepth(),
                        this.profile);
                if (reason != null) lastFailed++;
            }   
            return urls.length;
        }
        
        private String[] getURLs() {
            String[] r = null;
            try {
                httpc con = httpc.getInstance(
                        this.url.getHost(),
                        this.url.getHost(),
                        this.url.getPort(),
                        15000,
                        this.url.getProtocol().equals("https"));
                
                httpHeader header = new httpHeader();
                header.put(httpHeader.ACCEPT_ENCODING, "utf-8");
                header.put(httpHeader.HOST, this.url.getHost());
                
                httpc.response res = con.GET(this.url.getPath(), header);
                lastServerResponse = res.statusCode + " (" + res.statusText + ")";
                System.err.println("LAST RESPONSE: " + lastServerResponse);
                if (res.status.startsWith("2")) {
                    byte[] cbs = res.writeContent();
                    String encoding = res.responseHeader.getCharacterEncoding();
                    
                    if (encoding == null) encoding = "ASCII";
                    switch (this.type) {
                    case TYPE_TEXT: r = parseText(new String(cbs, encoding)); break;
                    // case TYPE_XML: r = parseXML(new String(cbs, encoding));
                    }
                }
                con.close();
                httpc.returnInstance(con);
            } catch (IOException e) {  }
            return r;
        }
        
        private static String[] parseText(String text) {
            return text.split("\n");
        }
    }
}
