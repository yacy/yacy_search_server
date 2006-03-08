// This file was provided by Hydrox
// see http://www.yacy-forum.de/viewtopic.php?p=18093#18093

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaCrawlLURL;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class IndexCleaner_p {
    private static plasmaCrawlLURL.Cleaner urldbCleanerThread;
    private static plasmaWordIndex.Cleaner indexCleanerThread;
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        serverObjects prop = new serverObjects();
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        prop.put("title", "DbCleanup_p");
        if (post!=null) {
            prop.put("bla", "post!=null");
            if (post.get("action").equals("ustart")) {
                if (urldbCleanerThread==null || !urldbCleanerThread.isAlive()) {
                    urldbCleanerThread = sb.urlPool.loadedURL.makeCleaner();
                    urldbCleanerThread.start();
                }
                else {
                    urldbCleanerThread.endPause();
                }
            }
            else if (post.get("action").equals("ustop")) {
                urldbCleanerThread.abort();
            }
            else if (post.get("action").equals("upause")) {
                urldbCleanerThread.pause();
            }
            else if (post.get("action").equals("rstart")) {
                if (indexCleanerThread==null || !indexCleanerThread.isAlive()) {
                    indexCleanerThread = sb.wordIndex.makeCleaner(sb.urlPool.loadedURL, post.get("wordHash","--------"));
                    indexCleanerThread.start();
                }
                else {
                    indexCleanerThread.endPause();
                }
            }
            else if (post.get("action").equals("rstop")) {
                indexCleanerThread.abort();
            }
            else if (post.get("action").equals("rpause")) {
                indexCleanerThread.pause();
            }
            prop.put("LOCATION","");
            return prop;
        }
        else {
            prop.put("bla", "post==null");            
        }
        if (urldbCleanerThread!=null) {
            prop.put("urldb", 1);
            prop.put("urldb_percentUrls", ((double)urldbCleanerThread.totalSearchedUrls/sb.urlPool.loadedURL.size())*100 + "");
            prop.put("urldb_blacklisted", urldbCleanerThread.blacklistedUrls);
            prop.put("urldb_total", urldbCleanerThread.totalSearchedUrls);
            prop.put("urldb_lastBlacklistedUrl", urldbCleanerThread.lastBlacklistedUrl);
            prop.put("urldb_lastBlacklistedHash", urldbCleanerThread.lastBlacklistedHash);
            prop.put("urldb_lastUrl", urldbCleanerThread.lastUrl);
            prop.put("urldb_lastHash", urldbCleanerThread.lastHash);
            prop.put("urldb_threadAlive", urldbCleanerThread.isAlive() + "");
            prop.put("urldb_threadToString", urldbCleanerThread.toString());
            double percent = ((double)urldbCleanerThread.blacklistedUrls/urldbCleanerThread.totalSearchedUrls)*100;
            prop.put("urldb_percent", percent + "");
        }
        if (indexCleanerThread!=null) {
            prop.put("rwidb", 1);
            prop.put("rwidb_threadAlive", indexCleanerThread.isAlive() + "");
            prop.put("rwidb_threadToString", indexCleanerThread.toString());
            prop.put("rwidb_RWIcountstart", indexCleanerThread.rwiCountAtStart);
            prop.put("rwidb_RWIcountnow", sb.wordIndex.size());
            prop.put("rwidb_wordHashNow", indexCleanerThread.wordHashNow);
            prop.put("rwidb_lastWordHash", indexCleanerThread.lastWordHash);
            prop.put("rwidb_lastDeletionCounter", indexCleanerThread.lastDeletionCounter);
            
        }
        return prop;
    }
}