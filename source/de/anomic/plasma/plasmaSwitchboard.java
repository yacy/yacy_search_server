// plasmaSwitchboard.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

/*
   This class holds the run-time environment of the plasma
   Search Engine. It's data forms a blackboard which can be used
   to organize running jobs around the indexing algorithm.
   The blackboard consist of the following entities:
   - storage: one plasmaStore object with the url-based database
   - configuration: initialized by properties once, then by external functions
   - job queues: for parsing, condensing, indexing
   - black/blue/whitelists: controls input and output to the index
 
   this class is also the core of the http crawling.
   There are some items that need to be respected when crawling the web:
   1) respect robots.txt
   2) do not access one domain too frequently, wait between accesses
   3) remember crawled URL's and do not access again too early
   4) priorization of specific links should be possible (hot-lists)
   5) attributes for crawling (depth, filters, hot/black-lists, priority)
   6) different crawling jobs with different attributes ('Orders') simultanoulsy
 
   We implement some specific tasks and use different database to archieve these goals:
   - a database 'crawlerDisallow.db' contains all url's that shall not be crawled
   - a database 'crawlerDomain.db' holds all domains and access times, where we loaded the disallow tables
     this table contains the following entities:
     <flag: robotes exist/not exist, last access of robots.txt, last access of domain (for access scheduling)>
   - four databases for scheduled access: crawlerScheduledHotText.db, crawlerScheduledColdText.db,
     crawlerScheduledHotMedia.db and crawlerScheduledColdMedia.db
   - two stacks for new URLS: newText.stack and newMedia.stack
   - two databases for URL double-check: knownText.db and knownMedia.db
   - one database with crawling orders: crawlerOrders.db
 
   The Information flow of a single URL that is crawled is as follows:
   - a html file is loaded from a specific URL within the module httpdProxyServlet as
     a process of the proxy.
   - the file is passed to httpdProxyCache. Here it's processing is delayed until the proxy is idle.
   - The cache entry is passed on to the plasmaSwitchboard. There the URL is stored into plasmaLURL where
     the URL is stored under a specific hash. The URL's from the content are stripped off, stored in plasmaLURL
     with a 'wrong' date (the date of the URL's are not known at this time, only after fetching) and stacked with
     plasmaCrawlerTextStack. The content is read and splitted into rated words in plasmaCondenser.
     The splitted words are then integrated into the index with plasmaSearch.
   - In plasmaSearch the words are indexed by reversing the relation between URL and words: one URL points
     to many words, the words within the document at the URL. After reversing, one word points
     to many URL's, all the URL's where the word occurrs. One single word->URL-hash relation is stored in
     plasmaIndexEntry. A set of plasmaIndexEntries is a reverse word index.
     This reverse word index is stored temporarly in plasmaIndexCache.
   - In plasmaIndexCache the single plasmaIndexEntry'ies are collected and stored into a plasmaIndex - entry
     These plasmaIndex - Objects are the true reverse words indexes.
   - in plasmaIndex the plasmaIndexEntry - objects are stored in a kelondroTree; an indexed file in the file system.
 
   The information flow of a search request is as follows:
   - in httpdFileServlet the user enters a search query, which is passed to plasmaSwitchboard
   - in plasmaSwitchboard, the query is passed to plasmaSearch.
   - in plasmaSearch, the plasmaSearch.result object is generated by simultanous enumeration of
     URL hases in the reverse word indexes plasmaIndex
   - (future: the plasmaSearch.result - object is used to identify more key words for a new search)
 
 
 
 */

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.data.blogBoard;
import de.anomic.data.bookmarksDB;
import de.anomic.data.listManager;
import de.anomic.data.messageBoard;
import de.anomic.data.userDB;
import de.anomic.data.wikiBoard;
import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.http.httpHeader;
import de.anomic.http.httpRemoteProxyConfig;
import de.anomic.http.httpc;
import de.anomic.index.indexContainer;
import de.anomic.index.indexEntry;
import de.anomic.index.indexEntryAttribute;
import de.anomic.index.indexURL;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.kelondro.kelondroMapTable;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.net.URL;
import de.anomic.plasma.dbImport.dbImportManager;
import de.anomic.plasma.parser.ParserException;
import de.anomic.plasma.urlPattern.plasmaURLPattern;
import de.anomic.server.serverAbstractSwitch;
import de.anomic.server.serverCodings;
import de.anomic.server.serverDate;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverInstantThread;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSemaphore;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverThread;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.bitfield;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacySeed;

public final class plasmaSwitchboard extends serverAbstractSwitch implements serverSwitch {
    
    // load slots
    public static int crawlSlots            = 10;
    public static int indexingSlots         = 100;
    public static int stackCrawlSlots       = 1000000;
    
    public static int maxCRLDump            = 500000;
    public static int maxCRGDump            = 200000;
    
    private int       dhtTransferIndexCount = 50;    
    
    // we must distinguish the following cases: resource-load was initiated by
    // 1) global crawling: the index is extern, not here (not possible here)
    // 2) result of search queries, some indexes are here (not possible here)
    // 3) result of index transfer, some of them are here (not possible here)
    // 4) proxy-load (initiator is "------------")
    // 5) local prefetch/crawling (initiator is own seedHash)
    // 6) local fetching for global crawling (other known or unknwon initiator)
    public static final int PROCESSCASE_0_UNKNOWN = 0;
    public static final int PROCESSCASE_1_GLOBAL_CRAWLING = 1;
    public static final int PROCESSCASE_2_SEARCH_QUERY_RESULT = 2;
    public static final int PROCESSCASE_3_INDEX_TRANSFER_RESULT = 3;
    public static final int PROCESSCASE_4_PROXY_LOAD = 4;
    public static final int PROCESSCASE_5_LOCAL_CRAWLING = 5;
    public static final int PROCESSCASE_6_GLOBAL_CRAWLING = 6;
    
    // couloured list management
    public static TreeSet badwords = null;
    public static TreeSet blueList = null;
    public static TreeSet stopwords = null;    
    public static plasmaURLPattern urlBlacklist;
    
    // storage management
    public  File                        htCachePath;
    private File                        plasmaPath;
    public  File                        indexPublicTextPath;
    public  File                        listsPath;
    public  File                        htDocsPath;
    public  File                        rankingPath;
    public  File                        workPath;
    public  HashMap                     rankingPermissions;
    public  plasmaURLPool               urlPool;
    public  plasmaWordIndex             wordIndex;
    public  plasmaHTCache               cacheManager;
    public  plasmaSnippetCache          snippetCache;
    public  plasmaCrawlLoader           cacheLoader;
    public  plasmaSwitchboardQueue      sbQueue;
    public  plasmaCrawlStacker          sbStackCrawlThread;
    public  messageBoard                messageDB;
    public  wikiBoard                   wikiDB;
    public  blogBoard                   blogDB;
    public  static plasmaCrawlRobotsTxt robots;
    public  plasmaCrawlProfile          profiles;
    public  plasmaCrawlProfile.entry    defaultProxyProfile;
    public  plasmaCrawlProfile.entry    defaultRemoteProfile;
    public  boolean                     rankingOn;
    public  plasmaRankingDistribution   rankingOwnDistribution;
    public  plasmaRankingDistribution   rankingOtherDistribution;
    public  HashMap                     outgoingCookies, incomingCookies;
    public  kelondroMapTable              facilityDB;
    public  plasmaParser                parser;
    public  long                        proxyLastAccess;
    public  yacyCore                    yc;
    public  HashMap                     indexingTasksInProcess;
    public  userDB                      userDB;
    public  bookmarksDB                 bookmarksDB;
    //public  StringBuffer                crl; // local citation references
    public  StringBuffer                crg; // global citation references
    public  dbImportManager             dbImportManager;
    public  plasmaDHTFlush              transferIdxThread = null;
    private plasmaDHTChunk              dhtTransferChunk = null;
    
    /*
     * Remote Proxy configuration
     */
    //    public boolean  remoteProxyUse;
    //    public boolean  remoteProxyUse4Yacy;
    //    public String   remoteProxyHost;
    //    public int      remoteProxyPort;
    //    public String   remoteProxyNoProxy = "";
    //    public String[] remoteProxyNoProxyPatterns = null;
    public httpRemoteProxyConfig remoteProxyConfig = null;
    
    
    /*
     * Some constants
     */
    private static final String STR_PROXYPROFILE       = "defaultProxyProfile";
    private static final String STR_REMOTEPROFILE      = "defaultRemoteProfile";
    private static final String STR_REMOTECRAWLTRIGGER = "REMOTECRAWLTRIGGER: REMOTE CRAWL TO PEER ";
    
    private serverSemaphore shutdownSync = new serverSemaphore(0);
    private boolean terminate = false;
    
    //private Object  crawlingPausedSync = new Object();
    //private boolean crawlingIsPaused = false;    
    
    public static final String CRAWLJOB_LOCAL_CRAWL = "50_localcrawl";
    public static final String CRAWLJOB_REMOTE_TRIGGERED_CRAWL = "62_remotetriggeredcrawl";
    public static final String CRAWLJOB_GLOBAL_CRAWL_TRIGGER = "61_globalcrawltrigger";
    private static final int   CRAWLJOB_SYNC = 0;
    private static final int   CRAWLJOB_STATUS = 1;
    
    private Hashtable crawlJobsStatus = new Hashtable(); 
    
    private static plasmaSwitchboard sb;

    public plasmaSwitchboard(String rootPath, String initPath, String configPath) {
        super(rootPath, initPath, configPath);
        
        // set loglevel and log
        setLog(new serverLog("PLASMA"));
        
        // load values from configs
        this.plasmaPath   = new File(rootPath, getConfig("dbPath", "DATA/PLASMADB"));
        this.log.logConfig("Plasma DB Path: " + this.plasmaPath.toString());
        this.indexPublicTextPath = new File(rootPath, getConfig("indexPublicTextPath", "DATA/INDEX/PUBLIC/TEXT"));
        this.log.logConfig("Index Path: " + this.indexPublicTextPath.toString());
        this.listsPath      = new File(rootPath, getConfig("listsPath", "DATA/LISTS"));
        this.log.logConfig("Lists Path:     " + this.listsPath.toString());
        this.htDocsPath   = new File(rootPath, getConfig("htDocsPath", "DATA/HTDOCS"));
        this.log.logConfig("HTDOCS Path:    " + this.htDocsPath.toString());
        this.rankingPath   = new File(rootPath, getConfig("rankingPath", "DATA/RANKING"));
        this.log.logConfig("Ranking Path:    " + this.rankingPath.toString());
        this.rankingPermissions = new HashMap(); // mapping of permission - to filename.
        this.workPath   = new File(rootPath, getConfig("workPath", "DATA/WORK"));
        this.log.logConfig("Work Path:    " + this.workPath.toString());
        
        /* ============================================================================
         * Remote Proxy configuration
         * ============================================================================ */
        this.remoteProxyConfig = httpRemoteProxyConfig.init(this);
        this.log.logConfig("Remote proxy configuration:\n" + this.remoteProxyConfig.toString());
        
        // setting timestamp of last proxy access
        this.proxyLastAccess = System.currentTimeMillis() - 60000;
        crg = new StringBuffer(maxCRGDump);
        //crl = new StringBuffer(maxCRLDump);
        
        // configuring list path
        if (!(listsPath.exists())) listsPath.mkdirs();
        
        // load coloured lists
        if (blueList == null) {
            // read only once upon first instantiation of this class
            String f = getConfig("plasmaBlueList", null);
            File plasmaBlueListFile = new File(f);
            if (f != null) blueList = kelondroMSetTools.loadList(plasmaBlueListFile, kelondroNaturalOrder.naturalOrder); else blueList= new TreeSet();
            this.log.logConfig("loaded blue-list from file " + plasmaBlueListFile.getName() + ", " +
            blueList.size() + " entries, " +
            ppRamString(plasmaBlueListFile.length()/1024));
        }
        
        // load the black-list / inspired by [AS]
        File ulrBlackListFile = new File(getRootPath(), getConfig("listsPath", "DATA/LISTS"));
        String blacklistClassName = getConfig("BlackLists.class", "de.anomic.plasma.urlPattern.defaultURLPattern");
        
        this.log.logConfig("Starting blacklist engine ...");
        try {
            Class blacklistClass = Class.forName(blacklistClassName);
            Constructor blacklistClassConstr = blacklistClass.getConstructor( new Class[] { File.class } );
            urlBlacklist = (plasmaURLPattern) blacklistClassConstr.newInstance(new Object[] { ulrBlackListFile });
            this.log.logFine("Used blacklist engine class: " + blacklistClassName);
            this.log.logConfig("Using blacklist engine: " + urlBlacklist.getEngineInfo());
        } catch (Exception e) {
            this.log.logSevere("Unable to load the blacklist engine",e);
            System.exit(-1);
        } catch (Error e) {
            this.log.logSevere("Unable to load the blacklist engine",e);
            System.exit(-1);
        }
      
        this.log.logConfig("Loading backlist data ...");
        listManager.switchboard = this;
        listManager.listsPath = ulrBlackListFile;        
        listManager.reloadBlacklists();

        // load badwords (to filter the topwords)
        if (badwords == null) {
            File badwordsFile = new File(rootPath, "yacy.badwords");
            badwords = kelondroMSetTools.loadList(badwordsFile, kelondroNaturalOrder.naturalOrder);
            this.log.logConfig("loaded badwords from file " + badwordsFile.getName() +
                               ", " + badwords.size() + " entries, " +
                               ppRamString(badwordsFile.length()/1024));
        }

        // load stopwords
        if (stopwords == null) {
            File stopwordsFile = new File(rootPath, "yacy.stopwords");
            stopwords = kelondroMSetTools.loadList(stopwordsFile, kelondroNaturalOrder.naturalOrder);
            this.log.logConfig("loaded stopwords from file " + stopwordsFile.getName() + ", " +
            stopwords.size() + " entries, " +
            ppRamString(stopwordsFile.length()/1024));
        }

        // load ranking tables
        File YBRPath = new File(rootPath, "ranking/YBR");
        if (YBRPath.exists()) {
            plasmaSearchPreOrder.loadYBR(YBRPath, 15);
        }

        // read memory amount
        int  ramLURL         = (int) getConfigLong("ramCacheLURL", 1024) / 1024;
        long ramLURL_time    = getConfigLong("ramCacheLURL_time", 1000);
        int  ramNURL         = (int) getConfigLong("ramCacheNURL", 1024) / 1024;
        long ramNURL_time    = getConfigLong("ramCacheNURL_time", 1000);
        int  ramEURL         = (int) getConfigLong("ramCacheEURL", 1024) / 1024;
        long ramEURL_time    = getConfigLong("ramCacheEURL_time", 1000);
        int  ramRWI          = (int) getConfigLong("ramCacheRWI",  1024) / 1024;
        long ramRWI_time     = getConfigLong("ramCacheRWI_time", 1000);
        int  ramHTTP         = (int) getConfigLong("ramCacheHTTP", 1024) / 1024;
        long ramHTTP_time    = getConfigLong("ramCacheHTTP_time", 1000);
        int  ramMessage      = (int) getConfigLong("ramCacheMessage", 1024) / 1024;
        long ramMessage_time = getConfigLong("ramCacheMessage_time", 1000);
        int  ramRobots       = (int) getConfigLong("ramCacheRobots",1024) / 1024;
        long ramRobots_time  = getConfigLong("ramCacheRobots_time",1000);
        int  ramProfiles     = (int) getConfigLong("ramCacheProfiles",1024) / 1024;
        long ramProfiles_time= getConfigLong("ramCacheProfiles_time", 1000);
        int  ramPreNURL      = (int) getConfigLong("ramCachePreNURL", 1024) / 1024;
        long ramPreNURL_time = getConfigLong("ramCachePreNURL_time", 1000);
        int  ramWiki         = (int) getConfigLong("ramCacheWiki", 1024) / 1024;
        long ramWiki_time    = getConfigLong("ramCacheWiki_time", 1000);
        int  ramBlog         = (int) getConfigLong("ramCacheBlog", 1024) / 1024;
        long ramBlog_time    = getConfigLong("ramCacheBlog_time", 1000);
        this.log.logConfig("LURL     Cache memory = " + ppRamString(ramLURL)     + ", preloadTime = " + ramLURL_time);
        this.log.logConfig("NURL     Cache memory = " + ppRamString(ramNURL)     + ", preloadTime = " + ramNURL_time);
        this.log.logConfig("EURL     Cache memory = " + ppRamString(ramEURL)     + ", preloadTime = " + ramEURL_time);
        this.log.logConfig("RWI      Cache memory = " + ppRamString(ramRWI)      + ", preloadTime = " + ramRWI_time);
        this.log.logConfig("HTTP     Cache memory = " + ppRamString(ramHTTP)     + ", preloadTime = " + ramHTTP_time);
        this.log.logConfig("Message  Cache memory = " + ppRamString(ramMessage)  + ", preloadTime = " + ramMessage_time);
        this.log.logConfig("Wiki     Cache memory = " + ppRamString(ramWiki)     + ", preloadTime = " + ramWiki_time);
        this.log.logConfig("Blog     Cache memory = " + ppRamString(ramBlog)     + ", preloadTime = " + ramBlog_time);
        this.log.logConfig("Robots   Cache memory = " + ppRamString(ramRobots)   + ", preloadTime = " + ramRobots_time);
        this.log.logConfig("Profiles Cache memory = " + ppRamString(ramProfiles) + ", preloadTime = " + ramProfiles_time);
        this.log.logConfig("PreNURL  Cache memory = " + ppRamString(ramPreNURL)  + ", preloadTime = " + ramPreNURL_time);
        
        // make crawl profiles database and default profiles
        this.log.logConfig("Initializing Crawl Profiles");
        File profilesFile = new File(this.plasmaPath, "crawlProfiles0.db");
        this.profiles = new plasmaCrawlProfile(profilesFile, ramProfiles, ramProfiles_time);
        initProfiles();
        log.logConfig("Loaded profiles from file " + profilesFile.getName() +
        ", " + this.profiles.size() + " entries" +
        ", " + ppRamString(profilesFile.length()/1024));
        
        // loading the robots.txt db
        this.log.logConfig("Initializing robots.txt DB");
        File robotsDBFile = new File(this.plasmaPath, "crawlRobotsTxt.db");
        robots = new plasmaCrawlRobotsTxt(robotsDBFile, ramRobots, ramRobots_time);
        this.log.logConfig("Loaded robots.txt DB from file " + robotsDBFile.getName() +
        ", " + robots.size() + " entries" +
        ", " + ppRamString(robotsDBFile.length()/1024));
        
        // start indexing management
        log.logConfig("Starting Indexing Management");
        urlPool = new plasmaURLPool(plasmaPath,
                                    ramLURL, getConfigBool("useFlexTableForLURL", false),
                                    ramNURL, getConfigBool("useFlexTableForNURL", false),
                                    ramEURL, getConfigBool("useFlexTableForEURL", true),
                                    ramLURL_time);
        wordIndex = new plasmaWordIndex(plasmaPath, indexPublicTextPath, ramRWI, ramRWI_time, log, getConfigBool("useCollectionIndex", false));

        // set a high maximum cache size to current size; this is adopted later automatically
        int wordCacheMaxCount = Math.max((int) getConfigLong("wordCacheInitCount", 30000),
                                         (int) getConfigLong("wordCacheMaxCount", 20000));
        setConfig("wordCacheMaxCount", Integer.toString(wordCacheMaxCount));
        wordIndex.setMaxWordCount(wordCacheMaxCount); 

        int wordInCacheMaxCount = (int) getConfigLong("indexDistribution.dhtReceiptLimit", 1000);
        wordIndex.setInMaxWordCount(wordInCacheMaxCount);
        
        // start a cache manager
        log.logConfig("Starting HT Cache Manager");
        
        // create the cache directory
        String cache = getConfig("proxyCache", "DATA/HTCACHE");
        cache = cache.replace('\\', '/');
        if (cache.endsWith("/")) { cache = cache.substring(0, cache.length() - 1); }
        if (new File(cache).isAbsolute()) {
            htCachePath = new File(cache); // don't use rootPath
        } else {
            htCachePath = new File(rootPath, cache);
        }
        this.log.logInfo("HTCACHE Path = " + htCachePath.getAbsolutePath());
        long maxCacheSize = 1024 * 1024 * Long.parseLong(getConfig("proxyCacheSize", "2")); // this is megabyte
        boolean useTreeStorage = getConfigBool("proxyCacheTree", true);
        this.cacheManager = new plasmaHTCache(htCachePath, maxCacheSize, ramHTTP, ramHTTP_time, useTreeStorage);
        
        // make parser
        log.logConfig("Starting Parser");
        this.parser = new plasmaParser();
        
        /* ======================================================================
         * initialize switchboard queue
         * ====================================================================== */
        // create queue
        this.sbQueue = new plasmaSwitchboardQueue(this.cacheManager, this.urlPool.loadedURL, new File(this.plasmaPath, "switchboardQueue1.stack"), this.profiles);
        
        // setting the indexing queue slots
        indexingSlots = (int) getConfigLong("indexer.slots", 100);
        
        // create in process list
        this.indexingTasksInProcess = new HashMap();
        
        // going through the sbQueue Entries and registering all content files as in use
        int count = 0;
        try {
            ArrayList sbQueueEntries = this.sbQueue.list();
            for (int i = 0; i < sbQueueEntries.size(); i++) {
                plasmaSwitchboardQueue.Entry entry = (plasmaSwitchboardQueue.Entry) sbQueueEntries.get(i);
                if ((entry != null) && (entry.url() != null) && (entry.cacheFile().exists())) {
                    plasmaHTCache.filesInUse.add(entry.cacheFile());
                    count++;
                }
            }
            this.log.logConfig(count + " files in htcache reported to the cachemanager as in use.");
        } catch (IOException e) {
            this.log.logSevere("cannot find any files in htcache reported to the cachemanager: " + e.getMessage());
        }
        // define an extension-blacklist
        log.logConfig("Parser: Initializing Extension Mappings for Media/Parser");
        plasmaParser.initMediaExt(plasmaParser.extString2extList(getConfig("mediaExt","")));
        plasmaParser.initSupportedRealtimeFileExt(plasmaParser.extString2extList(getConfig("parseableExt","")));
        
        // define a realtime parsable mimetype list
        log.logConfig("Parser: Initializing Mime Types");
        plasmaParser.initRealtimeParsableMimeTypes(getConfig("parseableRealtimeMimeTypes","application/xhtml+xml,text/html,text/plain"));
        plasmaParser.initParseableMimeTypes(plasmaParser.PARSER_MODE_PROXY,getConfig("parseableMimeTypes.PROXY",null));
        plasmaParser.initParseableMimeTypes(plasmaParser.PARSER_MODE_CRAWLER,getConfig("parseableMimeTypes.CRAWLER",null));
        plasmaParser.initParseableMimeTypes(plasmaParser.PARSER_MODE_ICAP,getConfig("parseableMimeTypes.ICAP",null));
        plasmaParser.initParseableMimeTypes(plasmaParser.PARSER_MODE_URLREDIRECTOR,getConfig("parseableMimeTypes.URLREDIRECTOR",null));
        
        // start a loader
        log.logConfig("Starting Crawl Loader");
        crawlSlots = Integer.parseInt(getConfig("crawler.MaxActiveThreads", "10"));
        plasmaCrawlLoader.switchboard = this;
        this.cacheLoader = new plasmaCrawlLoader(this.cacheManager, this.log);
                
        /*
         * Creating sync objects and loading status for the crawl jobs
         * a) local crawl
         * b) remote triggered crawl
         * c) global crawl trigger
         */
        this.crawlJobsStatus.put(CRAWLJOB_LOCAL_CRAWL, new Object[]{
                new Object(),
                Boolean.valueOf(getConfig(CRAWLJOB_LOCAL_CRAWL + "_isPaused", "false"))});
        this.crawlJobsStatus.put(CRAWLJOB_REMOTE_TRIGGERED_CRAWL, new Object[]{
                new Object(),
                Boolean.valueOf(getConfig(CRAWLJOB_REMOTE_TRIGGERED_CRAWL + "_isPaused", "false"))});
        this.crawlJobsStatus.put(CRAWLJOB_GLOBAL_CRAWL_TRIGGER, new Object[]{
                new Object(),
                Boolean.valueOf(getConfig(CRAWLJOB_GLOBAL_CRAWL_TRIGGER + "_isPaused", "false"))});
        
        // starting  board
        initMessages(ramMessage, ramMessage_time);
        
        // starting wiki
        initWiki(ramWiki, ramWiki_time);
        
        //starting blog
        initBlog(ramBlog, ramBlog_time);
        
        // Init User DB
        this.log.logConfig("Loading User DB");
        File userDbFile = new File(getRootPath(), "DATA/SETTINGS/user.db");
        this.userDB = new userDB(userDbFile, 512, 500);
        this.log.logConfig("Loaded User DB from file " + userDbFile.getName() +
        ", " + this.userDB.size() + " entries" +
        ", " + ppRamString(userDbFile.length()/1024));
        
        //Init bookmarks DB
        initBookmarks();
        
        // init cookie-Monitor
        this.log.logConfig("Starting Cookie Monitor");
        this.outgoingCookies = new HashMap();
        this.incomingCookies = new HashMap();
        
        // clean up profiles
        this.log.logConfig("Cleaning Profiles");
        try { cleanProfiles(); } catch (InterruptedException e) { /* Ignore this here */ }
        
        // init ranking transmission
        /*
        CRDistOn       = true/false
        CRDist0Path    = GLOBAL/010_owncr
        CRDist0Method  = 1
        CRDist0Percent = 0
        CRDist0Target  =
        CRDist1Path    = GLOBAL/014_othercr/1
        CRDist1Method  = 9
        CRDist1Percent = 30
        CRDist1Target  = kaskelix.de:8080,yacy.dyndns.org:8000,suma-lab.de:8080
         **/
        rankingOn = getConfig("CRDistOn", "true").equals("true");
        rankingOwnDistribution = new plasmaRankingDistribution(log, new File(rankingPath, getConfig("CRDist0Path", plasmaRankingDistribution.CR_OWN)), (int) getConfigLong("CRDist0Method", plasmaRankingDistribution.METHOD_ANYSENIOR), (int) getConfigLong("CRDist0Percent", 0), getConfig("CRDist0Target", ""));
        rankingOtherDistribution = new plasmaRankingDistribution(log, new File(rankingPath, getConfig("CRDist1Path", plasmaRankingDistribution.CR_OTHER)), (int) getConfigLong("CRDist1Method", plasmaRankingDistribution.METHOD_MIXEDSENIOR), (int) getConfigLong("CRDist1Percent", 30), getConfig("CRDist1Target", "kaskelix.de:8080,yacy.dyndns.org:8000,suma-lab.de:8080"));
        
        // init facility DB
        /*
        log.logSystem("Starting Facility Database");
        File facilityDBpath = new File(getRootPath(), "DATA/SETTINGS/");
        facilityDB = new kelondroTables(facilityDBpath);
        facilityDB.declareMaps("backlinks", 250, 500, new String[] {"date"}, null);
        log.logSystem("..opened backlinks");
        facilityDB.declareMaps("zeitgeist",  40, 500);
        log.logSystem("..opened zeitgeist");
        facilityDB.declareTree("statistik", new int[]{11, 8, 8, 8, 8, 8, 8}, 0x400);
        log.logSystem("..opened statistik");
        facilityDB.update("statistik", (new serverDate()).toShortString(false).substring(0, 11), new long[]{1,2,3,4,5,6});
        long[] testresult = facilityDB.selectLong("statistik", "yyyyMMddHHm");
        testresult = facilityDB.selectLong("statistik", (new serverDate()).toShortString(false).substring(0, 11));
         */
        
        /*
         * Initializing httpc
         */
        // initializing yacyDebugMode
        httpc.yacyDebugMode = getConfig("yacyDebugMode", "false").equals("true");
        
        // init nameCacheNoCachingList
        String noCachingList = getConfig("httpc.nameCacheNoCachingPatterns","");
        String[] noCachingEntries = noCachingList.split(",");
        for (int i=0; i<noCachingEntries.length; i++) {
            String entry = noCachingEntries[i].trim();
            httpc.nameCacheNoCachingPatterns.add(entry);
        }
        
        // generate snippets cache
        log.logConfig("Initializing Snippet Cache");
        snippetCache = new plasmaSnippetCache(this,cacheManager, parser,log);
        
        // start yacy core
        log.logConfig("Starting YaCy Protocol Core");
        //try{Thread.currentThread().sleep(5000);} catch (InterruptedException e) {} // for profiler
        this.yc = new yacyCore(this);
        //log.logSystem("Started YaCy Protocol Core");
        //      System.gc(); try{Thread.currentThread().sleep(5000);} catch (InterruptedException e) {} // for profiler
        serverInstantThread.oneTimeJob(yc, "loadSeeds", yacyCore.log, 3000);
        
        // initializing the stackCrawlThread
        this.sbStackCrawlThread = new plasmaCrawlStacker(this, this.plasmaPath, ramPreNURL, ramPreNURL_time, getConfigBool("useFlexTableForPreNURL", false));
        //this.sbStackCrawlThread = new plasmaStackCrawlThread(this,this.plasmaPath,ramPreNURL);
        //this.sbStackCrawlThread.start();
        
        // initializing dht chunk generation
        this.dhtTransferChunk = null;
        this.dhtTransferIndexCount = (int) getConfigLong("indexDistribution.startChunkSize", 50);
        
        // deploy threads
        log.logConfig("Starting Threads");
        // System.gc(); // help for profiler
        int indexing_cluster = Integer.parseInt(getConfig("80_indexing_cluster", "1"));
        if (indexing_cluster < 1) indexing_cluster = 1;
        deployThread("90_cleanup", "Cleanup", "simple cleaning process for monitoring information", null,
        new serverInstantThread(this, "cleanupJob", "cleanupJobSize", null), 10000); // all 5 Minutes
        deployThread("82_crawlstack", "Crawl URL Stacker", "process that checks url for double-occurrences and for allowance/disallowance by robots.txt", null,
        new serverInstantThread(sbStackCrawlThread, "job", "size", null), 8000);

        deployThread("80_indexing", "Parsing/Indexing", "thread that performes document parsing and indexing", "/IndexCreateIndexingQueue_p.html",
        new serverInstantThread(this, "deQueue", "queueSize", "deQueueFreeMem"), 10000);
        for (int i = 1; i < indexing_cluster; i++) {
            setConfig((i + 80) + "_indexing_idlesleep", getConfig("80_indexing_idlesleep", ""));
            setConfig((i + 80) + "_indexing_busysleep", getConfig("80_indexing_busysleep", ""));
            deployThread((i + 80) + "_indexing", "Parsing/Indexing (cluster job)", "thread that performes document parsing and indexing", null,
            new serverInstantThread(this, "deQueue", "queueSize", "deQueueFreeMem"), 10000 + (i * 1000),
            Long.parseLong(getConfig("80_indexing_idlesleep" , "5000")),
            Long.parseLong(getConfig("80_indexing_busysleep" , "0")),
            Long.parseLong(getConfig("80_indexing_memprereq" , "1000000")));
        }

        deployThread("70_cachemanager", "Proxy Cache Enqueue", "job takes new proxy files from RAM stack, stores them, and hands over to the Indexing Stack", null,
        new serverInstantThread(this, "htEntryStoreJob", "htEntrySize", null), 10000);
        deployThread("62_remotetriggeredcrawl", "Remote Crawl Job", "thread that performes a single crawl/indexing step triggered by a remote peer", null,
        new serverInstantThread(this, "remoteTriggeredCrawlJob", "remoteTriggeredCrawlJobSize", null), 30000);
        deployThread("61_globalcrawltrigger", "Global Crawl Trigger", "thread that triggeres remote peers for crawling", "/IndexCreateWWWGlobalQueue_p.html",
        new serverInstantThread(this, "limitCrawlTriggerJob", "limitCrawlTriggerJobSize", null), 30000); // error here?
        deployThread("50_localcrawl", "Local Crawl", "thread that performes a single crawl step from the local crawl queue", "/IndexCreateWWWLocalQueue_p.html",
        new serverInstantThread(this, "coreCrawlJob", "coreCrawlJobSize", null), 10000);
        deployThread("40_peerseedcycle", "Seed-List Upload", "task that a principal peer performes to generate and upload a seed-list to a ftp account", null,
        new serverInstantThread(yc, "publishSeedList", null, null), 180000);
        serverInstantThread peerPing = null;
        deployThread("30_peerping", "YaCy Core", "this is the p2p-control and peer-ping task", null,
        peerPing = new serverInstantThread(yc, "peerPing", null, null), 2000);
        peerPing.setSyncObject(new Object());
        
        deployThread("20_dhtdistribution", "DHT Distribution", "selection, transfer and deletion of index entries that are not searched on your peer, but on others", null,
            new serverInstantThread(this, "dhtTransferJob", null, null), 60000,
            Long.parseLong(getConfig("20_dhtdistribution_idlesleep" , "5000")),
            Long.parseLong(getConfig("20_dhtdistribution_busysleep" , "0")),
            Long.parseLong(getConfig("20_dhtdistribution_memprereq" , "1000000")));

        // test routine for snippet fetch
        //Set query = new HashSet();
        //query.add(plasmaWordIndexEntry.word2hash("Weitergabe"));
        //query.add(plasmaWordIndexEntry.word2hash("Zahl"));
        //plasmaSnippetCache.result scr = snippetCache.retrieve(new URL("http://www.heise.de/mobil/newsticker/meldung/mail/54980"), query, true);
        //plasmaSnippetCache.result scr = snippetCache.retrieve(new URL("http://www.heise.de/security/news/foren/go.shtml?read=1&msg_id=7301419&forum_id=72721"), query, true);
        //plasmaSnippetCache.result scr = snippetCache.retrieve(new URL("http://www.heise.de/kiosk/archiv/ct/2003/4/20"), query, true, 260);

        this.dbImportManager = new dbImportManager(this);
        
        sb=this;
        log.logConfig("Finished Switchboard Initialization");
    }


    public void initMessages(int ramMessage, long ramMessage_time) {
        this.log.logConfig("Starting Message Board");
        File messageDbFile = new File(workPath, "message.db");
        this.messageDB = new messageBoard(messageDbFile, ramMessage, ramMessage_time);
        this.log.logConfig("Loaded Message Board DB from file " + messageDbFile.getName() +
        ", " + this.messageDB.size() + " entries" +
        ", " + ppRamString(messageDbFile.length()/1024));
    }


    public void initWiki(int ramWiki, long ramWiki_time) {
        this.log.logConfig("Starting Wiki Board");
        File wikiDbFile = new File(workPath, "wiki.db");
        this.wikiDB = new wikiBoard(wikiDbFile, new File(workPath, "wiki-bkp.db"), ramWiki, ramWiki_time);
        this.log.logConfig("Loaded Wiki Board DB from file " + wikiDbFile.getName() +
        ", " + this.wikiDB.size() + " entries" +
        ", " + ppRamString(wikiDbFile.length()/1024));
    }
    public void initBlog(int ramBlog, long ramBlog_time) {
        this.log.logConfig("Starting Blog");
        File blogDbFile = new File(workPath, "blog.db");
        this.blogDB = new blogBoard(blogDbFile, ramBlog, ramBlog_time);
        this.log.logConfig("Loaded Blog DB from file " + blogDbFile.getName() +
        ", " + this.blogDB.size() + " entries" +
        ", " + ppRamString(blogDbFile.length()/1024));
    }
    public void initBookmarks(){
        this.log.logConfig("Loading Bookmarks DB");
        File bookmarksFile = new File(workPath, "bookmarks.db");
        File tagsFile = new File(workPath, "bookmarkTags.db");
        File datesFile = new File(workPath, "bookmarkDates.db");
        this.bookmarksDB = new bookmarksDB(bookmarksFile, tagsFile, datesFile, 512, 500);
        this.log.logConfig("Loaded Bookmarks DB from files "+ bookmarksFile.getName()+ ", "+tagsFile.getName());
        this.log.logConfig(this.bookmarksDB.tagsSize()+" Tag, "+this.bookmarksDB.bookmarksSize()+" Bookmarks");
    }
    
    
    public static plasmaSwitchboard getSwitchboard(){
        return sb;
    }

    public boolean isRobinsonMode() {
        return (yacyCore.seedDB.sizeConnected() == 0) && (yacyCore.seedDB.mySeed.isVirgin());
    }
    
    /**
     * This method changes the HTCache size.<br>
     * @param new cache size in mb
     */
    public final void setCacheSize(long newCacheSize) {
        this.cacheManager.setCacheSize(1048576 * newCacheSize);
    }
    
    public boolean onlineCaution() {
        try {
            return System.currentTimeMillis() - proxyLastAccess < Integer.parseInt(getConfig("onlineCautionDelay", "30000"));
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private static String ppRamString(long bytes) {
        if (bytes < 1024) return bytes + " KByte";
        bytes = bytes / 1024;
        if (bytes < 1024) return bytes + " MByte";
        bytes = bytes / 1024;
        if (bytes < 1024) return bytes + " GByte";
        return (bytes / 1024) + "TByte";
    }

    private void initProfiles() {
        if ((this.profiles.size() == 0) ||
            (getConfig(STR_PROXYPROFILE, "").length() == 0) ||
            (this.profiles.getEntry(getConfig(STR_PROXYPROFILE, "")) == null)) {
            // generate new default entry for proxy crawling
            this.defaultProxyProfile = this.profiles.newEntry("proxy", "", ".*", ".*", Integer.parseInt(getConfig("proxyPrefetchDepth", "0")), Integer.parseInt(getConfig("proxyPrefetchDepth", "0")), 60 * 24 * 30, -1, -1, false, true, true, true, getConfigBool("proxyCrawlOrder", false), true, true, true);
            setConfig(STR_PROXYPROFILE, this.defaultProxyProfile.handle());
        } else {
            this.defaultProxyProfile = this.profiles.getEntry(getConfig(STR_PROXYPROFILE, ""));
        }
        if ((profiles.size() == 1) ||
            (getConfig(STR_REMOTEPROFILE, "").length() == 0) ||
            (profiles.getEntry(getConfig(STR_REMOTEPROFILE, "")) == null)) {
            // generate new default entry for remote crawling
            defaultRemoteProfile = profiles.newEntry("remote", "", ".*", ".*", 0, 0, 60 * 24 * 30, -1, -1, true, false, true, true, false, true, true, false);
            setConfig(STR_REMOTEPROFILE, defaultRemoteProfile.handle());
        } else {
            defaultRemoteProfile = profiles.getEntry(getConfig(STR_REMOTEPROFILE, ""));
        }
    }

    private void resetProfiles() {
        final File pdb = new File(plasmaPath, "crawlProfiles0.db");
        if (pdb.exists()) pdb.delete();
        int ramProfiles = (int) getConfigLong("ramCacheProfiles", 1024) / 1024;
        long ramProfiles_time = getConfigLong("ramCacheProfiles_time", 1000);
        profiles = new plasmaCrawlProfile(pdb, ramProfiles, ramProfiles_time);
        initProfiles();
    }
    
    public boolean cleanProfiles() throws InterruptedException {
        if ((sbQueue.size() > 0) || (cacheLoader.size() > 0) || (urlPool.noticeURL.stackSize() > 0)) return false;
        final Iterator iter = profiles.profiles(true);
        plasmaCrawlProfile.entry entry;
        boolean hasDoneSomething = false;
        try {
            while (iter.hasNext()) {
                // check for interruption
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Shutdown in progress");
                
                // getting next profile
                entry = (plasmaCrawlProfile.entry) iter.next();
                if (!((entry.name().equals("proxy")) || (entry.name().equals("remote")))) {
                    iter.remove();
                    hasDoneSomething = true;
                }
            }
        } catch (kelondroException e) {
            resetProfiles();
            hasDoneSomething = true;
        }
        return hasDoneSomething;
    }
    
    public plasmaHTCache getCacheManager() {
        return cacheManager;
    }
    
    synchronized public void htEntryStoreEnqueued(plasmaHTCache.Entry entry) throws IOException {
        if (cacheManager.full())
            htEntryStoreProcess(entry);
        else
            cacheManager.push(entry);
    }
    
    synchronized public boolean htEntryStoreProcess(plasmaHTCache.Entry entry) throws IOException {
        
        if (entry == null) return false;

        /* =========================================================================
         * PARSER SUPPORT
         * 
         * Testing if the content type is supported by the available parsers
         * ========================================================================= */
        boolean isSupportedContent = plasmaParser.supportedContent(entry.url(),entry.getMimeType());
        
        /* =========================================================================
         * INDEX CONTROL HEADER
         * 
         * With the X-YACY-Index-Control header set to "no-index" a client could disallow
         * yacy to index the response returned as answer to a request
         * ========================================================================= */
        boolean doIndexing = true;        
        if (entry.requestProhibitsIndexing()) {
                doIndexing = false;
        }        
        
        /* =========================================================================
         * LOCAL IP ADDRESS CHECK
         * 
         * check if ip is local ip address // TODO: remove this procotol specific code here
         * ========================================================================= */
        InetAddress hostAddress = httpc.dnsResolve(entry.url().getHost());
        if (hostAddress == null) {
            if (this.remoteProxyConfig == null || !this.remoteProxyConfig.useProxy()) {
                this.log.logFine("Unknown host in URL '" + entry.url() + "'. Will not be indexed.");
                doIndexing = false;             
            }
        } else if (hostAddress.isSiteLocalAddress()) {
            this.log.logFine("Host in URL '" + entry.url() + "' has private ip address. Will not be indexed.");
            doIndexing = false;               
        } else if (hostAddress.isLoopbackAddress()) {
            this.log.logFine("Host in URL '" + entry.url() + "' has loopback ip address. Will not be indexed.");
            doIndexing = false;                  
        }
        
        /* =========================================================================
         * STORING DATA
         * 
         * Now we store the response header and response content if 
         * a) the user has configured to use the htcache or
         * b) the content should be indexed
         * ========================================================================= */        
        if (
                (entry.profile().storeHTCache()) ||
                (doIndexing && isSupportedContent)
        ) {
            // store response header            
            if (entry.writeResourceInfo()) {
                this.log.logInfo("WROTE HEADER for " + entry.cacheFile());
            }        
            
            // work off unwritten files
            if (entry.cacheArray() == null)  {
                //this.log.logFine("EXISTING FILE (" + entry.cacheFile.length() + " bytes) for " + entry.cacheFile);
            } else {
                String error = entry.shallStoreCacheForProxy();
                if (error == null) {
                    this.cacheManager.writeResourceContent(entry.url(), entry.cacheArray());
                    this.log.logFine("WROTE FILE (" + entry.cacheArray().length + " bytes) for " + entry.cacheFile());
                } else {
                    this.log.logFine("WRITE OF FILE " + entry.cacheFile() + " FORBIDDEN: " + error);
                }
            }
        }
        
        /* =========================================================================
         * INDEXING
         * ========================================================================= */          
        if (doIndexing && isSupportedContent){
            
            // registering the cachefile as in use
            if (entry.cacheFile().exists()) {
                plasmaHTCache.filesInUse.add(entry.cacheFile());
            }
            
            // enqueue for further crawling
            enQueue(this.sbQueue.newEntry(
                    entry.url(), 
                    indexURL.urlHash(entry.referrerURL()),
                    entry.ifModifiedSince(), 
                    entry.requestWithCookie(),
                    entry.initiator(), 
                    entry.depth(), 
                    entry.profile().handle(),
                    entry.name()
            ));
        } else {
            if (!entry.profile().storeHTCache() && entry.cacheFile().exists()) {
                this.cacheManager.deleteFile(entry.url());                
            }
        }
        
        return true;
    }
    
    public boolean htEntryStoreJob() {
        if (cacheManager.empty()) return false;
        try {
            return htEntryStoreProcess(cacheManager.pop());
        } catch (IOException e) {
            return false;
        }
    }
    
    public int htEntrySize() {
        return cacheManager.size();
    }
    
    public void close() {
        log.logConfig("SWITCHBOARD SHUTDOWN STEP 1: sending termination signal to managed threads:");
        terminateAllThreads(true);
        if (transferIdxThread != null) stopTransferWholeIndex(false);
        log.logConfig("SWITCHBOARD SHUTDOWN STEP 2: sending termination signal to threaded indexing");
        // closing all still running db importer jobs
        this.dbImportManager.close();
        cacheLoader.close();
        wikiDB.close();
        blogDB.close();
        userDB.close();
        bookmarksDB.close();
        messageDB.close();
        if (facilityDB != null) try {facilityDB.close();} catch (IOException e) {}
        sbStackCrawlThread.close();
        profiles.close();
        robots.close();
        parser.close();
        cacheManager.close();
        sbQueue.close();
        flushCitationReference(crg, "crg");
        log.logConfig("SWITCHBOARD SHUTDOWN STEP 3: sending termination signal to database manager (stand by...)");
        int waitingBoundSeconds = Integer.parseInt(getConfig("maxWaitingWordFlush", "120"));
        urlPool.close();
        wordIndex.close(waitingBoundSeconds);
        log.logConfig("SWITCHBOARD SHUTDOWN TERMINATED");
    }
    
    public int queueSize() {
        return sbQueue.size();
        //return processStack.size() + cacheLoader.size() + noticeURL.stackSize();
    }
    
    public int cacheSizeMin() {
        return wordIndex.size();
    }
    
    public void enQueue(Object job) {
        if (!(job instanceof plasmaSwitchboardQueue.Entry)) {
            System.out.println("internal error at plasmaSwitchboard.enQueue: wrong job type");
            System.exit(0);
        }
        try {
            sbQueue.push((plasmaSwitchboardQueue.Entry) job);
        } catch (IOException e) {
            log.logSevere("IOError in plasmaSwitchboard.enQueue: " + e.getMessage(), e);
        }
    }
    
    public void deQueueFreeMem() {
        // flush some entries from the RAM cache
        wordIndex.flushCacheSome(false);
        // adopt maximum cache size to current size to prevent that further OutOfMemoryErrors occur
        int newMaxCount = Math.max(2000, Math.min((int) getConfigLong("wordCacheMaxCount", 20000), wordIndex.dhtOutCacheSize()));
        setConfig("wordCacheMaxCount", Integer.toString(newMaxCount));
        wordIndex.setMaxWordCount(newMaxCount); 
    }
    
    public boolean deQueue() {
        try {
            // work off fresh entries from the proxy or from the crawler
            if (onlineCaution()) {
                log.logFine("deQueue: online caution, omitting resource stack processing");
                return false;
            }

            // flush some entries from the RAM cache
            // (new permanent cache flushing)
            wordIndex.flushCacheSome(sbQueue.size() != 0);
            urlPool.loadedURL.flushCacheSome();

            boolean doneSomething = false;

            // possibly delete entries from last chunk
            if ((this.dhtTransferChunk != null) &&
                    (this.dhtTransferChunk.getStatus() == plasmaDHTChunk.chunkStatus_COMPLETE)) {
                int deletedURLs = this.dhtTransferChunk.deleteTransferIndexes();
                this.log.logFine("Deleted from " + this.dhtTransferChunk.containers().length + " transferred RWIs locally, removed " + deletedURLs + " URL references");
                this.dhtTransferChunk = null;
            }

            // generate a dht chunk
            if (
                    (dhtShallTransfer() == null) &&
                    (
                            (this.dhtTransferChunk == null) ||
                            (this.dhtTransferChunk.getStatus() == plasmaDHTChunk.chunkStatus_UNDEFINED) ||
                            // (this.dhtTransferChunk.getStatus() == plasmaDHTChunk.chunkStatus_COMPLETE) ||
                            (this.dhtTransferChunk.getStatus() == plasmaDHTChunk.chunkStatus_FAILED)
                    )
            ) {
                // generate new chunk
                int minChunkSize = (int) getConfigLong("indexDistribution.minChunkSize", 30);
                dhtTransferChunk = new plasmaDHTChunk(this.log, this.wordIndex, this.urlPool.loadedURL, minChunkSize, dhtTransferIndexCount, 5000);
                doneSomething = true;
            }

            // check for interruption
            checkInterruption();

            // getting the next entry from the indexing queue
            synchronized (sbQueue) {

                if (sbQueue.size() == 0) {
                    //log.logFine("deQueue: nothing to do, queue is emtpy");
                    return doneSomething; // nothing to do
                }

                /*
                if (wordIndex.wordCacheRAMSize() + 1000 > (int) getConfigLong("wordCacheMaxLow", 8000)) {
                    log.logFine("deQueue: word index ram cache too full (" + ((int) getConfigLong("wordCacheMaxLow", 8000) - wordIndex.wordCacheRAMSize()) + " slots left); dismissed to omit ram flush lock");
                    return false;
                }
                */

                int stackCrawlQueueSize;
                if ((stackCrawlQueueSize = sbStackCrawlThread.size()) >= stackCrawlSlots) {
                    log.logFine("deQueue: too many processes in stack crawl thread queue, dismissed to protect emergency case (" + "stackCrawlQueue=" + stackCrawlQueueSize + ")");
                    return doneSomething;
                }

                plasmaSwitchboardQueue.Entry nextentry;

                // if we were interrupted we should return now
                if (Thread.currentThread().isInterrupted()) {
                    log.logFine("deQueue: thread was interrupted");
                    return false;
                }

                // do one processing step
                log.logFine("DEQUEUE: sbQueueSize=" + sbQueue.size() +
                        ", coreStackSize=" + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) +
                        ", limitStackSize=" + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT) +
                        ", overhangStackSize=" + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_OVERHANG) +
                        ", remoteStackSize=" + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE));
                try {
                    nextentry = sbQueue.pop();
                    if (nextentry == null) {
                        log.logFine("deQueue: null entry on queue stack");
                        return false;
                    }
                } catch (IOException e) {
                    log.logSevere("IOError in plasmaSwitchboard.deQueue: " + e.getMessage(), e);
                    return doneSomething;
                }

                synchronized (this.indexingTasksInProcess) {
                    this.indexingTasksInProcess.put(nextentry.urlHash(), nextentry);
                }

                // parse and index the resource
                processResourceStack(nextentry);
            }

            // ready & finished
            return true;
        } catch (InterruptedException e) {
            log.logInfo("DEQUEUE: Shutdown detected.");
            return false;
        }
    }
    
    public int cleanupJobSize() {
        int c = 0;
        if ((urlPool.errorURL.stackSize() > 1000)) c++;
        for (int i = 1; i <= 6; i++) {
            if (urlPool.loadedURL.getStackSize(i) > 1000) c++;
        }
        return c;
    }
    
    public boolean cleanupJob() {
        try {
            boolean hasDoneSomething = false;

            // do transmission of cr-files
            checkInterruption();
            int count = rankingOwnDistribution.size() / 100;
            if (count == 0) count = 1;
            if (count > 5) count = 5;
            rankingOwnDistribution.transferRanking(count);
            rankingOtherDistribution.transferRanking(1);

            // clean up error stack
            checkInterruption();
            if ((urlPool.errorURL.stackSize() > 1000)) {
                log.logFine("Cleaning Error-URLs report stack, " + urlPool.errorURL.stackSize() + " entries on stack");
                urlPool.errorURL.clearStack();
                hasDoneSomething = true;
            }
            // clean up loadedURL stack
            for (int i = 1; i <= 6; i++) {
                checkInterruption();
                if (urlPool.loadedURL.getStackSize(i) > 1000) {
                    log.logFine("Cleaning Loaded-URLs report stack, " + urlPool.loadedURL.getStackSize(i) + " entries on stack " + i);
                    urlPool.loadedURL.clearStack(i);
                    hasDoneSomething = true;
                }
            }
            // clean up profiles
            checkInterruption();
            if (cleanProfiles()) hasDoneSomething = true;

            // clean up news
            checkInterruption();
            try {                
                log.logFine("Cleaning Incoming News, " + yacyCore.newsPool.size(yacyNewsPool.INCOMING_DB) + " entries on stack");
                if (yacyCore.newsPool.automaticProcess() > 0) hasDoneSomething = true;
            } catch (IOException e) {}

            return hasDoneSomething;
        } catch (InterruptedException e) {
            this.log.logInfo("cleanupJob: Shutdown detected");
            return false;
        }
    }
    
    /**
     * Creates a new File instance with absolute path of ours Seed File.<br>
     * @return a new File instance
     */
    public File getOwnSeedFile() {
        return new File(getRootPath(), getConfig("yacyOwnSeedFile", "mySeed.txt"));
    }
    
    /**
     * With this function the crawling process can be paused
     */
    public void pauseCrawlJob(String jobType) {
        Object[] status = (Object[])this.crawlJobsStatus.get(jobType);
        synchronized(status[CRAWLJOB_SYNC]) {
            status[CRAWLJOB_STATUS] = Boolean.TRUE;
        }
        setConfig(jobType + "_isPaused", "true");
    }  
    
    /**
     * Continue the previously paused crawling
     */
    public void continueCrawlJob(String jobType) {
        Object[] status = (Object[])this.crawlJobsStatus.get(jobType);
        synchronized(status[CRAWLJOB_SYNC]) {
            if (((Boolean)status[CRAWLJOB_STATUS]).booleanValue()) {
                status[CRAWLJOB_STATUS] = Boolean.FALSE;
                status[CRAWLJOB_SYNC].notifyAll();
            }
        }
        setConfig(jobType + "_isPaused", "false");
    } 
    
    /**
     * @return <code>true</code> if crawling was paused or <code>false</code> otherwise
     */
    public boolean crawlJobIsPaused(String jobType) {
        Object[] status = (Object[])this.crawlJobsStatus.get(jobType);
        synchronized(status[CRAWLJOB_SYNC]) {
            return ((Boolean)status[CRAWLJOB_STATUS]).booleanValue();
        }
    }
    
    public int coreCrawlJobSize() {
        return urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE);
    }
    
    public boolean coreCrawlJob() {
        if (urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) == 0) {
            //log.logDebug("CoreCrawl: queue is empty");
            return false;
        }
        if (sbQueue.size() >= indexingSlots) {
            log.logFine("CoreCrawl: too many processes in indexing queue, dismissed (" +
            "sbQueueSize=" + sbQueue.size() + ")");
            return false;
        }
        if (cacheLoader.size() >= crawlSlots) {
            log.logFine("CoreCrawl: too many processes in loader queue, dismissed (" +
            "cacheLoader=" + cacheLoader.size() + ")");
            return false;
        }
        if (onlineCaution()) {
            log.logFine("CoreCrawl: online caution, omitting processing");
            return false;
        }
        // if the server is busy, we do crawling more slowly
        //if (!(cacheManager.idle())) try {Thread.currentThread().sleep(2000);} catch (InterruptedException e) {}
        
        // if crawling was paused we have to wait until we wer notified to continue
        Object[] status = (Object[])this.crawlJobsStatus.get(CRAWLJOB_LOCAL_CRAWL);
        synchronized(status[CRAWLJOB_SYNC]) {
            if (((Boolean)status[CRAWLJOB_STATUS]).booleanValue()) {
                try {
                    status[CRAWLJOB_SYNC].wait();
                }
                catch (InterruptedException e){ return false;}
            }
        }
        
        // do a local crawl        
        plasmaCrawlNURL.Entry urlEntry = null;
        while (urlEntry == null && urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) > 0) {
            String stats = "LOCALCRAWL[" + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) + ", " + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT) + ", " + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_OVERHANG) + ", " + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE) + "]";
            try {
                urlEntry = urlPool.noticeURL.pop(plasmaCrawlNURL.STACK_TYPE_CORE);
                String profileHandle = urlEntry.profileHandle();
                // System.out.println("DEBUG plasmaSwitchboard.processCrawling:
                // profileHandle = " + profileHandle + ", urlEntry.url = " + urlEntry.url());
                if (profileHandle == null) {
                    log.logSevere(stats + ": NULL PROFILE HANDLE '" + urlEntry.profileHandle() + "' for URL " + urlEntry.url());
                    return true;
                }
                plasmaCrawlProfile.entry profile = profiles.getEntry(profileHandle);
                if (profile == null) {
                    log.logSevere(stats + ": LOST PROFILE HANDLE '" + urlEntry.profileHandle() + "' for URL " + urlEntry.url());
                    return true;
                }
                log.logFine("LOCALCRAWL: URL=" + urlEntry.url() + ", initiator=" + urlEntry.initiator() + ", crawlOrder=" + ((profile.remoteIndexing()) ? "true" : "false") + ", depth=" + urlEntry.depth() + ", crawlDepth=" + profile.generalDepth() + ", filter=" + profile.generalFilter()
                        + ", permission=" + ((yacyCore.seedDB == null) ? "undefined" : (((yacyCore.seedDB.mySeed.isSenior()) || (yacyCore.seedDB.mySeed.isPrincipal())) ? "true" : "false")));
                
                processLocalCrawling(urlEntry, profile, stats);
                return true;
            } catch (IOException e) {
                log.logSevere(stats + ": CANNOT FETCH ENTRY: " + e.getMessage());
            }
        }
        return true;
    }
    
    public int limitCrawlTriggerJobSize() {
        return urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT);
    }
    
    public boolean limitCrawlTriggerJob() {
        if (urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT) == 0) {
            //log.logDebug("LimitCrawl: queue is empty");
            return false;
        }
        
        if ((coreCrawlJobSize() <= 20) && (limitCrawlTriggerJobSize() > 100)) {
            // it is not efficient if the core crawl job is empty and we have too much to do
            // move some tasks to the core crawl job
            int toshift = limitCrawlTriggerJobSize() / 5;
            if (toshift > 1000) toshift = 1000;
            if (toshift > limitCrawlTriggerJobSize()) toshift = limitCrawlTriggerJobSize();
            for (int i = 0; i < toshift; i++) {
                urlPool.noticeURL.shift(plasmaCrawlNURL.STACK_TYPE_LIMIT, plasmaCrawlNURL.STACK_TYPE_CORE);
            }
            log.logInfo("shifted " + toshift + " jobs from global crawl to local crawl");
        }
        
        // if the server is busy, we do crawling more slowly
        //if (!(cacheManager.idle())) try {Thread.currentThread().sleep(2000);} catch (InterruptedException e) {}
        
        // if crawling was paused we have to wait until we wer notified to continue
        Object[] status = (Object[])this.crawlJobsStatus.get(CRAWLJOB_GLOBAL_CRAWL_TRIGGER);
        synchronized(status[CRAWLJOB_SYNC]) {
            if (((Boolean)status[CRAWLJOB_STATUS]).booleanValue()) {
                try {
                    status[CRAWLJOB_SYNC].wait();
                }
                catch (InterruptedException e){ return false;}
            }
        }
        
        // start a global crawl, if possible
        String stats = "REMOTECRAWLTRIGGER[" + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) + ", " + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT) + ", " + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_OVERHANG) + ", "
                        + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE) + "]";
        try {
            plasmaCrawlNURL.Entry urlEntry = urlPool.noticeURL.pop(plasmaCrawlNURL.STACK_TYPE_LIMIT);
            String profileHandle = urlEntry.profileHandle();
            // System.out.println("DEBUG plasmaSwitchboard.processCrawling:
            // profileHandle = " + profileHandle + ", urlEntry.url = " + urlEntry.url());
            plasmaCrawlProfile.entry profile = profiles.getEntry(profileHandle);
            if (profile == null) {
                log.logSevere(stats + ": LOST PROFILE HANDLE '" + urlEntry.profileHandle() + "' for URL " + urlEntry.url());
                return true;
            }
            log.logFine("plasmaSwitchboard.limitCrawlTriggerJob: url=" + urlEntry.url() + ", initiator=" + urlEntry.initiator() + ", crawlOrder=" + ((profile.remoteIndexing()) ? "true" : "false") + ", depth=" + urlEntry.depth() + ", crawlDepth=" + profile.generalDepth() + ", filter="
                            + profile.generalFilter() + ", permission=" + ((yacyCore.seedDB == null) ? "undefined" : (((yacyCore.seedDB.mySeed.isSenior()) || (yacyCore.seedDB.mySeed.isPrincipal())) ? "true" : "false")));

            boolean tryRemote = ((urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) != 0) || (sbQueue.size() != 0)) &&
                                 (profile.remoteIndexing()) &&
                                 (urlEntry.initiator() != null) &&
                                // (!(urlEntry.initiator().equals(indexURL.dummyHash))) &&
                                 ((yacyCore.seedDB.mySeed.isSenior()) || (yacyCore.seedDB.mySeed.isPrincipal()));
            if (tryRemote) {
                boolean success = processRemoteCrawlTrigger(urlEntry);
                if (success) return true;
            }

            processLocalCrawling(urlEntry, profile, stats); // emergency case
            
            if (sbQueue.size() >= indexingSlots) {
                log.logFine("LimitCrawl: too many processes in indexing queue, delayed to protect emergency case (" +
                "sbQueueSize=" + sbQueue.size() + ")");
                return false;
            }
            
            if (cacheLoader.size() >= crawlSlots) {
                log.logFine("LimitCrawl: too many processes in loader queue, delayed to protect emergency case (" +
                "cacheLoader=" + cacheLoader.size() + ")");
                return false;
            }
            
            return true;
        } catch (IOException e) {
            log.logSevere(stats + ": CANNOT FETCH ENTRY: " + e.getMessage());
            return true; // if we return a false here we will block everything
        }
    }
    
    public int remoteTriggeredCrawlJobSize() {
        return urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE);
    }
    
    public boolean remoteTriggeredCrawlJob() {
        // work off crawl requests that had been placed by other peers to our crawl stack
        
        // do nothing if either there are private processes to be done
        // or there is no global crawl on the stack
        if (urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE) == 0) {
            //log.logDebug("GlobalCrawl: queue is empty");
            return false;
        }
        if (sbQueue.size() >= indexingSlots) {
            log.logFine("GlobalCrawl: too many processes in indexing queue, dismissed (" +
            "sbQueueSize=" + sbQueue.size() + ")");
            return false;
        }
        if (cacheLoader.size() >= crawlSlots) {
            log.logFine("GlobalCrawl: too many processes in loader queue, dismissed (" +
            "cacheLoader=" + cacheLoader.size() + ")");
            return false;
        }        
        if (onlineCaution()) {
            log.logFine("GlobalCrawl: online caution, omitting processing");
            return false;
        }
        
        // if crawling was paused we have to wait until we wer notified to continue
        Object[] status = (Object[])this.crawlJobsStatus.get(CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
        synchronized(status[CRAWLJOB_SYNC]) {
            if (((Boolean)status[CRAWLJOB_STATUS]).booleanValue()) {
                try {
                    status[CRAWLJOB_SYNC].wait();
                }
                catch (InterruptedException e){ return false;}
            }
        }
        
        // we don't want to crawl a global URL globally, since WE are the global part. (from this point of view)
        String stats = "REMOTETRIGGEREDCRAWL[" + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) + ", " + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT) + ", " + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_OVERHANG) + ", "
                        + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE) + "]";
        try {
            plasmaCrawlNURL.Entry urlEntry = urlPool.noticeURL.pop(plasmaCrawlNURL.STACK_TYPE_REMOTE);
            String profileHandle = urlEntry.profileHandle();
            // System.out.println("DEBUG plasmaSwitchboard.processCrawling:
            // profileHandle = " + profileHandle + ", urlEntry.url = " +
            // urlEntry.url());
            plasmaCrawlProfile.entry profile = profiles.getEntry(profileHandle);

            if (profile == null) {
                log.logSevere(stats + ": LOST PROFILE HANDLE '" + urlEntry.profileHandle() + "' for URL " + urlEntry.url());
                return false;
            }
            log.logFine("plasmaSwitchboard.remoteTriggeredCrawlJob: url=" + urlEntry.url() + ", initiator=" + urlEntry.initiator() + ", crawlOrder=" + ((profile.remoteIndexing()) ? "true" : "false") + ", depth=" + urlEntry.depth() + ", crawlDepth=" + profile.generalDepth() + ", filter="
                        + profile.generalFilter() + ", permission=" + ((yacyCore.seedDB == null) ? "undefined" : (((yacyCore.seedDB.mySeed.isSenior()) || (yacyCore.seedDB.mySeed.isPrincipal())) ? "true" : "false")));

            processLocalCrawling(urlEntry, profile, stats);
            return true;
        } catch (IOException e) {
            log.logSevere(stats + ": CANNOT FETCH ENTRY: " + e.getMessage());
            return true;
        }
    }
    
    private plasmaParserDocument parseResource(plasmaSwitchboardQueue.Entry entry, String initiatorHash) throws InterruptedException, ParserException {
        plasmaParserDocument document = null;

        // the mimetype of this entry
        String mimeType = entry.getMimeType();
        String charset = entry.getCharacterEncoding();        

        // the parser logger
        serverLog parserLogger = parser.getLogger();

        // parse the document
        return parseResource(entry.url(), mimeType, charset, entry.cacheFile());
    }
    
    public plasmaParserDocument parseResource(URL location, String mimeType, String documentCharset, File sourceFile) throws InterruptedException, ParserException {
        plasmaParserDocument doc = parser.parseSource(location, mimeType, documentCharset, sourceFile);
        assert(doc != null) : "Unexpected error. Parser returned null.";
        return doc;
    }
    
    private void processResourceStack(plasmaSwitchboardQueue.Entry entry) throws InterruptedException {
        plasmaParserDocument document = null;
        try {
            // work off one stack entry with a fresh resource
            long stackStartTime = 0, stackEndTime = 0,
            parsingStartTime = 0, parsingEndTime = 0,
            indexingStartTime = 0, indexingEndTime = 0,
            storageStartTime = 0, storageEndTime = 0;
            
            // we must distinguish the following cases: resource-load was initiated by
            // 1) global crawling: the index is extern, not here (not possible here)
            // 2) result of search queries, some indexes are here (not possible here)
            // 3) result of index transfer, some of them are here (not possible here)
            // 4) proxy-load (initiator is "------------")
            // 5) local prefetch/crawling (initiator is own seedHash)
            // 6) local fetching for global crawling (other known or unknwon initiator)
            int processCase = PROCESSCASE_0_UNKNOWN;
            yacySeed initiatorPeer = null;
            String initiatorPeerHash = (entry.proxy()) ? indexURL.dummyHash : entry.initiator();
            if (initiatorPeerHash.equals(indexURL.dummyHash)) {
                // proxy-load
                processCase = PROCESSCASE_4_PROXY_LOAD;
            } else if (initiatorPeerHash.equals(yacyCore.seedDB.mySeed.hash)) {
                // normal crawling
                processCase = PROCESSCASE_5_LOCAL_CRAWLING;
            } else {
                // this was done for remote peer (a global crawl)
                initiatorPeer = yacyCore.seedDB.getConnected(initiatorPeerHash);
                processCase = PROCESSCASE_6_GLOBAL_CRAWLING;
            }
            
            log.logFine("processResourceStack processCase=" + processCase +
                    ", depth=" + entry.depth() +
                    ", maxDepth=" + ((entry.profile() == null) ? "null" : Integer.toString(entry.profile().generalDepth())) +
                    ", filter=" + ((entry.profile() == null) ? "null" : entry.profile().generalFilter()) +
                    ", initiatorHash=" + initiatorPeerHash +
                    //", responseHeader=" + ((entry.responseHeader() == null) ? "null" : entry.responseHeader().toString()) +
                    ", url=" + entry.url()); // DEBUG
            
            /* =========================================================================
             * PARSE CONTENT
             * ========================================================================= */
            parsingStartTime = System.currentTimeMillis();

            try {
                document = this.parseResource(entry, initiatorPeerHash);
                if (document == null) return;
            } catch (ParserException e) {
                this.log.logInfo("Unable to parse the resource '" + entry.url() + "'. " + e.getMessage());
                addURLtoErrorDB(entry.url(), entry.referrerHash(), initiatorPeerHash, entry.anchorName(), e.getErrorCode(), new bitfield(indexURL.urlFlagLength));
                if (document != null) {
                    document.close();
                    document = null;
                }
                return;
            }
            
            parsingEndTime = System.currentTimeMillis();            
            
            // getting the document date
            Date docDate = entry.getModificationDate();
            
            /* =========================================================================
             * put anchors on crawl stack
             * ========================================================================= */            
            stackStartTime = System.currentTimeMillis();
            if (
                    ((processCase == PROCESSCASE_4_PROXY_LOAD) || (processCase == PROCESSCASE_5_LOCAL_CRAWLING)) &&
                    ((entry.profile() == null) || (entry.depth() < entry.profile().generalDepth()))
            ) {
                Map hl = document.getHyperlinks();
                Iterator i = hl.entrySet().iterator();
                String nextUrlString;
                Map.Entry nextEntry;
                while (i.hasNext()) {
                    // check for interruption
                    checkInterruption();
                    
                    // fetching the next hyperlink
                    nextEntry = (Map.Entry) i.next();
                    nextUrlString = (String) nextEntry.getKey();
                    try {                        
                        nextUrlString = new URL(nextUrlString).toNormalform();
                        
                        // enqueue the hyperlink into the pre-notice-url db
                        sbStackCrawlThread.enqueue(nextUrlString, entry.url().toString(), initiatorPeerHash, (String) nextEntry.getValue(), docDate, entry.depth() + 1, entry.profile());                        
                    } catch (MalformedURLException e1) {}                    
                }
                log.logInfo("CRAWL: ADDED " + hl.size() + " LINKS FROM " + entry.normalizedURLString() +
                        ", NEW CRAWL STACK SIZE IS " + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE));
            }
            stackEndTime = System.currentTimeMillis();
            
            /* =========================================================================
             * CREATE INDEX
             * ========================================================================= */  
            String docDescription = document.getMainLongTitle();
            URL referrerURL = entry.referrerURL();
            String referrerUrlHash = indexURL.urlHash(referrerURL);
            if (referrerUrlHash == null) referrerUrlHash = indexURL.dummyHash;

            String noIndexReason = plasmaCrawlEURL.DENIED_UNSPECIFIED_INDEXING_ERROR;
            if (processCase == PROCESSCASE_4_PROXY_LOAD) {
                // proxy-load
                noIndexReason = entry.shallIndexCacheForProxy();
            } else {
                // normal crawling
                noIndexReason = entry.shallIndexCacheForCrawler();
            }
            
            if (noIndexReason == null) {
                // strip out words
                indexingStartTime = System.currentTimeMillis();
                
                checkInterruption();
                log.logFine("Condensing for '" + entry.normalizedURLString() + "'");
                plasmaCondenser condenser = new plasmaCondenser(document.getText());
                
                // generate citation reference
                Integer[] ioLinks = generateCitationReference(entry.urlHash(), docDate, document, condenser);
                
                try {        
                    // check for interruption
                    checkInterruption();
                    
                    // create a new loaded URL db entry
                    plasmaCrawlLURL.Entry newEntry = urlPool.loadedURL.newEntry(
                            entry.url(),                                            // URL
                            docDescription,                                         // document description
                            docDate,                                                // modification date
                            new Date(),                                             // loaded date
                            referrerUrlHash,                                        // referer hash
                            0,                                                      // copy count
                            true,                                                   // local need
                            condenser.RESULT_WORD_ENTROPHY,                         // quality
                            indexEntryAttribute.language(entry.url()),              // language
                            indexEntryAttribute.docType(document.getMimeType()),    // doctype
                            (int) entry.size(),                                     // size
                            condenser.RESULT_NUMB_WORDS                             // word count
                    );
                    
                    /* ========================================================================
                     * STORE URL TO LOADED-URL-DB
                     * ======================================================================== */
                    urlPool.loadedURL.store(newEntry, false);
                    urlPool.loadedURL.stack(
                            newEntry,                       // loaded url db entry
                            initiatorPeerHash,                  // initiator peer hash
                            yacyCore.seedDB.mySeed.hash,    // executor peer hash
                            processCase                     // process case
                    );                    
                    
                    // check for interruption
                    checkInterruption();
                    
                    /* ========================================================================
                     * STORE WORD INDEX
                     * ======================================================================== */
                    if (
                            (
                                    (processCase == PROCESSCASE_4_PROXY_LOAD) || 
                                    (processCase == PROCESSCASE_5_LOCAL_CRAWLING) || 
                                    (processCase == PROCESSCASE_6_GLOBAL_CRAWLING)
                            ) && 
                            (entry.profile().localIndexing())
                    ) {
                        String urlHash = newEntry.hash();
                        
                        // remove stopwords                        
                        log.logInfo("Excluded " + condenser.excludeWords(stopwords) + " words in URL " + entry.url());
                        indexingEndTime = System.currentTimeMillis();
                        
                        storageStartTime = System.currentTimeMillis();
                        int words = 0;
                        String storagePeerHash;
                        yacySeed seed;
                                                
                        if (
                                ((storagePeerHash = getConfig("storagePeerHash",null))== null) ||
                                (storagePeerHash.trim().length() == 0) ||
                                ((seed = yacyCore.seedDB.getConnected(storagePeerHash))==null)
                        ){
                            
                            /* ========================================================================
                             * STORE PAGE INDEX INTO WORD INDEX DB
                             * ======================================================================== */
                            words = wordIndex.addPageIndex(
                                    entry.url(),                                            // document url
                                    urlHash,                                                // document url hash
                                    docDate,                                                // document mod date
                                    (int) entry.size(),                                     // document size
                                    document,                                               // document content
                                    condenser,                                              // document condenser
                                    indexEntryAttribute.language(entry.url()),              // document language
                                    indexEntryAttribute.docType(document.getMimeType()),    // document type
                                    ioLinks[0].intValue(),                                  // outlinkSame
                                    ioLinks[1].intValue()                                   // outlinkOthers
                            );
                        } else {
                            /* ========================================================================
                             * SEND PAGE INDEX TO STORAGE PEER
                             * ======================================================================== */                            
                            HashMap urlCache = new HashMap(1);
                            urlCache.put(newEntry.hash(),newEntry);
                            
                            ArrayList tmpContainers = new ArrayList(condenser.RESULT_SIMI_WORDS);
                            
                            String language = indexEntryAttribute.language(entry.url());                            
                            char doctype = indexEntryAttribute.docType(document.getMimeType());
                            int urlLength = newEntry.url().toString().length();
                            int urlComps = htmlFilterContentScraper.urlComps(newEntry.url().toString()).length;

                            // iterate over all words
                            Iterator i = condenser.words();
                            Map.Entry wentry;
                            plasmaCondenser.wordStatProp wordStat;
                            while (i.hasNext()) {
                                wentry = (Map.Entry) i.next();
                                String word = (String) wentry.getKey();
                                wordStat = (plasmaCondenser.wordStatProp) wentry.getValue();
                                String wordHash = indexEntryAttribute.word2hash(word);
                                indexContainer wordIdxContainer = new indexContainer(wordHash);
                                indexEntry wordIdxEntry = new indexURLEntry(
                                        urlHash,
                                        urlLength, urlComps,
                                        wordStat.count,
                                        document.longTitle.length(),
                                        condenser.RESULT_SIMI_WORDS,
                                        condenser.RESULT_SIMI_SENTENCES,
                                        wordStat.posInText,
                                        wordStat.posInPhrase,
                                        wordStat.numOfPhrase,
                                        0,
                                        newEntry.size(),
                                        docDate.getTime(),
                                        System.currentTimeMillis(),
                                        condenser.RESULT_WORD_ENTROPHY,
                                        language,
                                        doctype,
                                        ioLinks[0].intValue(),
                                        ioLinks[1].intValue(),
                                        true
                                );
                                wordIdxContainer.add(wordIdxEntry);
                                tmpContainers.add(wordIdxContainer);
                            }
                            //System.out.println("DEBUG: plasmaSearch.addPageIndex: added " + condenser.getWords().size() + " words, flushed " + c + " entries");
                            words = condenser.RESULT_SIMI_WORDS;
                            
                            // transfering the index to the storage peer
                            indexContainer[] indexData = (indexContainer[]) tmpContainers.toArray(new indexContainer[tmpContainers.size()]);
                            HashMap resultObj = yacyClient.transferIndex(
                                    seed,       // target seed
                                    indexData,  // word index data
                                    urlCache,   // urls
                                    true,       // gzip body
                                    120000      // transfer timeout 
                            );
                            
                            // check for interruption
                            checkInterruption();
                            
                            // if the transfer failed we try to store the index locally
                            String error = (String) resultObj.get("result");
                            if (error != null) {
                                words = wordIndex.addPageIndex(
                                        entry.url(), 
                                        urlHash, 
                                        docDate, 
                                        (int) entry.size(),
                                        document, 
                                        condenser,
                                        indexEntryAttribute.language(entry.url()),
                                        indexEntryAttribute.docType(document.getMimeType()),
                                        ioLinks[0].intValue(), 
                                        ioLinks[1].intValue()
                                );
                            }
                            
                            tmpContainers = null;
                        }
                        storageEndTime = System.currentTimeMillis();
                        
                        if (log.isInfo()) {
                            // TODO: UTF-8 docDescription seems not to be displayed correctly because
                            // of string concatenation
                            log.logInfo("*Indexed " + words + " words in URL " + entry.url() +
                                    " [" + entry.urlHash() + "]" +
                                    "\n\tDescription:  " + docDescription +
                                    "\n\tMimeType: "  + document.getMimeType() + " | Charset: " + document.getSourceCharset() + " | " +
                                    "Size: " + document.getTextLength() + " bytes | " +
                                    "Anchors: " + ((document.anchors==null)?0:document.anchors.size()) +
                                    "\n\tStackingTime:  " + (stackEndTime-stackStartTime) + " ms | " +
                                    "ParsingTime:  " + (parsingEndTime-parsingStartTime) + " ms | " +
                                    "IndexingTime: " + (indexingEndTime-indexingStartTime) + " ms | " +
                                    "StorageTime: " + (storageEndTime-storageStartTime) + " ms");
                        }
                        
                        // check for interruption
                        checkInterruption();
                        
                        // if this was performed for a remote crawl request, notify requester
                        if ((processCase == PROCESSCASE_6_GLOBAL_CRAWLING) && (initiatorPeer != null)) {
                            log.logInfo("Sending crawl receipt for '" + entry.normalizedURLString() + "' to " + initiatorPeer.getName());
                            yacyClient.crawlReceipt(initiatorPeer, "crawl", "fill", "indexed", newEntry, "");
                        }
                    } else {
                        log.logFine("Not Indexed Resource '" + entry.normalizedURLString() + "': process case=" + processCase);
                        addURLtoErrorDB(entry.url(), referrerUrlHash, initiatorPeerHash, docDescription, plasmaCrawlEURL.DENIED_UNKNOWN_INDEXING_PROCESS_CASE, new bitfield(indexURL.urlFlagLength));
                    }
                } catch (Exception ee) {
                    if (ee instanceof InterruptedException) throw (InterruptedException)ee;
                    
                    // check for interruption
                    checkInterruption();
                    
                    log.logSevere("Could not index URL " + entry.url() + ": " + ee.getMessage(), ee);
                    if ((processCase == PROCESSCASE_6_GLOBAL_CRAWLING) && (initiatorPeer != null)) {
                        yacyClient.crawlReceipt(initiatorPeer, "crawl", "exception", ee.getMessage(), null, "");
                    }
                    addURLtoErrorDB(entry.url(), referrerUrlHash, initiatorPeerHash, docDescription, plasmaCrawlEURL.DENIED_UNSPECIFIED_INDEXING_ERROR, new bitfield(indexURL.urlFlagLength));
                }
                
            } else {
                // check for interruption
                checkInterruption();
                
                log.logInfo("Not indexed any word in URL " + entry.url() + "; cause: " + noIndexReason);
                addURLtoErrorDB(entry.url(), referrerUrlHash, initiatorPeerHash, docDescription, noIndexReason, new bitfield(indexURL.urlFlagLength));
                if ((processCase == PROCESSCASE_6_GLOBAL_CRAWLING) && (initiatorPeer != null)) {
                    yacyClient.crawlReceipt(initiatorPeer, "crawl", "rejected", noIndexReason, null, "");
                }
            }
            document.close();
            document = null;
        } catch (Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException)e;
            this.log.logSevere("Unexpected exception while parsing/indexing URL ",e);
        } catch (Error e) {
            this.log.logSevere("Unexpected exception while parsing/indexing URL ",e);
        } finally {
            checkInterruption();
            
            // The following code must be into the finally block, otherwise it will not be executed
            // on errors!

            // removing current entry from in process list
            synchronized (this.indexingTasksInProcess) {
                this.indexingTasksInProcess.remove(entry.urlHash());
            }

            // removing current entry from notice URL queue
            boolean removed = urlPool.noticeURL.remove(entry.urlHash()); // worked-off
            if (!removed) {
                log.logFinest("Unable to remove indexed URL " + entry.url() + " from Crawler Queue. This could be because of an URL redirect.");
            }

            // explicit delete/free resources
            if ((entry != null) && (entry.profile() != null) && (!(entry.profile().storeHTCache()))) {
                plasmaHTCache.filesInUse.remove(entry.cacheFile());
                cacheManager.deleteFile(entry.url());
            }
            entry = null;
            
            if (document != null) try { document.close(); } catch (Exception e) { /* ignore this */ }
        }
    }
    
    private Integer[] /*(outlinksSame, outlinksOther)*/ generateCitationReference(String baseurlhash, Date docDate, plasmaParserDocument document, plasmaCondenser condenser) {
        // generate citation reference
        Map hl = document.getHyperlinks();
        Iterator it = hl.entrySet().iterator();
        String nexturlhash;
        StringBuffer cpg = new StringBuffer(12 * (hl.size() + 1) + 1);
        StringBuffer cpl = new StringBuffer(12 * (hl.size() + 1) + 1);
        String lhp = baseurlhash.substring(6); // local hash part
        int GCount = 0;
        int LCount = 0;
        while (it.hasNext()) {
            nexturlhash = indexURL.urlHash((String) ((Map.Entry) it.next()).getKey());
            if (nexturlhash != null) {
                if (nexturlhash.substring(6).equals(lhp)) {
                    cpl.append(nexturlhash.substring(0, 6));
                    LCount++;
                } else {
                    cpg.append(nexturlhash);
                    GCount++;
                }
            }
        }
        
        // append this reference to buffer
        // generate header info
        String head = baseurlhash + "=" +
        plasmaWordIndex.microDateHoursStr(docDate.getTime()) +          // latest update timestamp of the URL
        plasmaWordIndex.microDateHoursStr(System.currentTimeMillis()) + // last visit timestamp of the URL
        kelondroBase64Order.enhancedCoder.encodeLongSmart(LCount, 2) +  // count of links to local resources
        kelondroBase64Order.enhancedCoder.encodeLongSmart(GCount, 2) +  // count of links to global resources
        kelondroBase64Order.enhancedCoder.encodeLongSmart(document.getImages().size(), 2) + // count of Images in document
        kelondroBase64Order.enhancedCoder.encodeLongSmart(0, 2) +       // count of links to other documents
        kelondroBase64Order.enhancedCoder.encodeLongSmart(document.getTextLength(), 3) +   // length of plain text in bytes
        kelondroBase64Order.enhancedCoder.encodeLongSmart(condenser.RESULT_NUMB_WORDS, 3) + // count of all appearing words
        kelondroBase64Order.enhancedCoder.encodeLongSmart(condenser.RESULT_SIMI_WORDS, 3) + // count of all unique words
        kelondroBase64Order.enhancedCoder.encodeLongSmart(0, 1); // Flags (update, popularity, attention, vote)
        
        //crl.append(head); crl.append ('|'); crl.append(cpl); crl.append((char) 13); crl.append((char) 10);
        crg.append(head); crg.append('|'); crg.append(cpg); crg.append((char) 13); crg.append((char) 10);
        
        // if buffer is full, flush it.
        /*
        if (crl.length() > maxCRLDump) {
            flushCitationReference(crl, "crl");
            crl = new StringBuffer(maxCRLDump);
        }
         **/
        if (crg.length() > maxCRGDump) {
            flushCitationReference(crg, "crg");
            crg = new StringBuffer(maxCRGDump);
        }
        
        return new Integer[] {new Integer(LCount), new Integer(GCount)};
    }
    
    private void flushCitationReference(StringBuffer cr, String type) {
        if (cr.length() < 12) return;
        String filename = type.toUpperCase() + "-A-" + new serverDate().toShortString(true) + "." + cr.substring(0, 12) + ".cr.gz";
        File path = new File(rankingPath, (type.equals("crl") ? "LOCAL/010_cr/" : getConfig("CRDist0Path", plasmaRankingDistribution.CR_OWN)));
        path.mkdirs();
        File file = new File(path, filename);
        
        // generate header
        StringBuffer header = new StringBuffer(200);
        header.append("# Name=YaCy " + ((type.equals("crl")) ? "Local" : "Global") + " Citation Reference Ticket"); header.append((char) 13); header.append((char) 10);
        header.append("# Created=" + System.currentTimeMillis()); header.append((char) 13); header.append((char) 10);
        header.append("# Structure=<Referee-12>,'=',<UDate-3>,<VDate-3>,<LCount-2>,<GCount-2>,<ICount-2>,<DCount-2>,<TLength-3>,<WACount-3>,<WUCount-3>,<Flags-1>,'|',*<Anchor-" + ((type.equals("crl")) ? "6" : "12") + ">"); header.append((char) 13); header.append((char) 10);
        header.append("# ---"); header.append((char) 13); header.append((char) 10);
        cr.insert(0, header.toString());
        try {
            serverFileUtils.writeAndGZip(cr.toString().getBytes(), file);
            log.logFine("wrote citation reference dump " + file.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void processLocalCrawling(plasmaCrawlNURL.Entry urlEntry, plasmaCrawlProfile.entry profile, String stats) {
        // work off one Crawl stack entry
        if ((urlEntry == null) || (urlEntry.url() == null)) {
            log.logInfo(stats + ": urlEntry=null");
            return;
        }
        
        // convert the referrer hash into the corresponding URL
        URL refererURL = null;
        String refererHash = urlEntry.referrerHash();
        if ((refererHash != null) && (!refererHash.equals(indexURL.dummyHash))) try {
            refererURL = this.urlPool.getURL(refererHash);
        } catch (IOException e) {
            refererURL = null;
        }
        cacheLoader.loadAsync(urlEntry.url(), urlEntry.name(), (refererURL!=null)?refererURL.toString():null, urlEntry.initiator(), urlEntry.depth(), profile);
        log.logInfo(stats + ": enqueued for load " + urlEntry.url() + " [" + urlEntry.hash() + "]");
        return;
    }
    
    private boolean processRemoteCrawlTrigger(plasmaCrawlNURL.Entry urlEntry) {
        
        // return true iff another peer has/will index(ed) the url
        if (urlEntry == null) {
            log.logInfo("REMOTECRAWLTRIGGER[" + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) + ", " + urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE) + "]: urlEntry=null");
            return true; // superfluous request; true correct in this context
        }
        
        // are we qualified?
        if ((yacyCore.seedDB.mySeed == null) ||
        (yacyCore.seedDB.mySeed.isJunior())) {
            log.logFine("plasmaSwitchboard.processRemoteCrawlTrigger: no permission");
            return false;
        }
        
        // check url
        if (urlEntry.url() == null) {
            log.logFine("ERROR: plasmaSwitchboard.processRemoteCrawlTrigger - url is null. name=" + urlEntry.name());
            return true;
        }
        String urlhash = indexURL.urlHash(urlEntry.url());
        
        // check remote crawl
        yacySeed remoteSeed = yacyCore.dhtAgent.getCrawlSeed(urlhash);
        
        if (remoteSeed == null) {
            log.logFine("plasmaSwitchboard.processRemoteCrawlTrigger: no remote crawl seed available");
            return false;
        }
        
        // do the request
        try {
            HashMap page = yacyClient.crawlOrder(remoteSeed, urlEntry.url(), urlPool.getURL(urlEntry.referrerHash()), 6000);
        
            // check success
            /*
             * the result of the 'response' value can have one of the following
             * values: negative cases, no retry denied - the peer does not want
             * to crawl that exception - an exception occurred
             * 
             * negative case, retry possible rejected - the peer has rejected to
             * process, but a re-try should be possible
             * 
             * positive case with crawling stacked - the resource is processed
             * asap
             * 
             * positive case without crawling double - the resource is already
             * in database, believed to be fresh and not reloaded the resource
             * is also returned in lurl
             */
            if ((page == null) || (page.get("delay") == null)) {
                log.logInfo("CRAWL: REMOTE CRAWL TO PEER " + remoteSeed.getName() + " FAILED. CAUSE: unknown (URL=" + urlEntry.url().toString() + "). Removed peer.");
                if (remoteSeed != null) {
                    yacyCore.peerActions.peerDeparture(remoteSeed);
                }
                return false;
            } else
                try {
                    log.logFine("plasmaSwitchboard.processRemoteCrawlTrigger: remoteSeed=" + remoteSeed.getName() + ", url=" + urlEntry.url().toString() + ", response=" + page.toString()); // DEBUG

                    int newdelay = Integer.parseInt((String) page.get("delay"));
                    yacyCore.dhtAgent.setCrawlDelay(remoteSeed.hash, newdelay);
                    String response = (String) page.get("response");
                    if (response.equals("stacked")) {
                        log.logInfo(STR_REMOTECRAWLTRIGGER + remoteSeed.getName() + " PLACED URL=" + urlEntry.url().toString() + "; NEW DELAY=" + newdelay);
                        return true;
                    } else if (response.equals("double")) {
                        String lurl = (String) page.get("lurl");
                        if ((lurl != null) && (lurl.length() != 0)) {
                            String propStr = crypt.simpleDecode(lurl, (String) page.get("key"));
                            plasmaCrawlLURL.Entry entry = urlPool.loadedURL.newEntry(propStr, true);
                            urlPool.loadedURL.store(entry, false);
                            urlPool.loadedURL.stack(entry, yacyCore.seedDB.mySeed.hash, remoteSeed.hash, 1); // *** ueberfluessig/doppelt?
                            urlPool.noticeURL.remove(entry.hash());
                            log.logInfo(STR_REMOTECRAWLTRIGGER + remoteSeed.getName() + " SUPERFLUOUS. CAUSE: " + page.get("reason") + " (URL=" + urlEntry.url().toString() + "). URL IS CONSIDERED AS 'LOADED!'");
                            return true;
                        } else {
                            log.logInfo(STR_REMOTECRAWLTRIGGER + remoteSeed.getName() + " REJECTED. CAUSE: " + page.get("reason") + " (URL=" + urlEntry.url().toString() + ")");
                            remoteSeed.setFlagAcceptRemoteCrawl(false);
                            yacyCore.seedDB.update(remoteSeed.hash, remoteSeed);
                            return false;
                        }
                    } else {
                        log.logInfo(STR_REMOTECRAWLTRIGGER + remoteSeed.getName() + " DENIED. RESPONSE=" + response + ", CAUSE=" + page.get("reason") + ", URL=" + urlEntry.url().toString());
                        remoteSeed.setFlagAcceptRemoteCrawl(false);
                        yacyCore.seedDB.update(remoteSeed.hash, remoteSeed);
                        return false;
                    }
                } catch (Exception e) {
                    // wrong values
                    log.logSevere(STR_REMOTECRAWLTRIGGER + remoteSeed.getName() + " FAILED. CLIENT RETURNED: " + page.toString(), e);
                    return false;
                }
        } catch (IOException e) {
            log.logSevere(STR_REMOTECRAWLTRIGGER + remoteSeed.getName() + " FAILED. URL CANNOT BE RETRIEVED from referrer hash: " + urlEntry.referrerHash(), e);
            return false;
        }
    }
    
    private static SimpleDateFormat DateFormatter = new SimpleDateFormat("EEE, dd MMM yyyy");
    public static String dateString(Date date) {
        if (date == null) return ""; else return DateFormatter.format(date);
    }
    
    public serverObjects searchFromLocal(plasmaSearchQuery query,
                                         plasmaSearchRankingProfile ranking,
                                         plasmaSearchTimingProfile  localTiming,
                                         plasmaSearchTimingProfile  remoteTiming,
                                         boolean postsort) {
        
        // tell all threads to do nothing for a specific time
        intermissionAllThreads(2 * query.maximumTime);
        
        serverObjects prop = new serverObjects();
        try {
            // filter out words that appear in bluelist
            //log.logInfo("E");
            query.filterOut(blueList);
            
            // log
            log.logInfo("INIT WORD SEARCH: " + query.queryWords + ":" + query.queryHashes + " - " + query.wantedResults + " links, " + (query.maximumTime / 1000) + " seconds");
            long timestamp = System.currentTimeMillis();
            
            // start a presearch, which makes only sense if we idle afterwards.
            // this is especially the case if we start a global search and idle until search
            //if (query.domType == plasmaSearchQuery.SEARCHDOM_GLOBALDHT) {
            //    Thread preselect = new presearch(query.queryHashes, order, query.maximumTime / 10, query.urlMask, 10, 3);
            //    preselect.start();
            //}
            
            // create a new search event
            plasmaSearchEvent theSearch = new plasmaSearchEvent(query, ranking, localTiming, remoteTiming, postsort, log, wordIndex, urlPool.loadedURL, snippetCache);
            plasmaSearchResult acc = theSearch.search();
            
            // fetch snippets
            //if (query.domType != plasmaSearchQuery.SEARCHDOM_GLOBALDHT) snippetCache.fetch(acc.cloneSmart(), query.queryHashes, query.urlMask, 10, 1000);
            log.logFine("SEARCH TIME AFTER ORDERING OF SEARCH RESULTS: " + ((System.currentTimeMillis() - timestamp) / 1000) + " seconds");
            
            // result is a List of urlEntry elements: prepare answer
            if (acc == null) {
                prop.put("type_totalcount", "0");
                prop.put("type_orderedcount", "0");
                prop.put("type_linkcount", "0");
            } else {
                prop.put("type_totalcount", acc.globalContributions + acc.localContributions);
                prop.put("type_orderedcount", Integer.toString(acc.sizeOrdered()));
                prop.put("type_globalresults", acc.globalContributions);
                int i = 0;
                int p;
                URL url;
                plasmaCrawlLURL.Entry urlentry;
                String urlstring, urlname, filename, urlhash;
                String host, hash, address, descr = "";
                yacySeed seed;
                plasmaSnippetCache.Snippet snippet;
                boolean includeSnippets = false;
                String formerSearch = query.words(" ");
                long targetTime = timestamp + query.maximumTime;
                if (targetTime < System.currentTimeMillis()) targetTime = System.currentTimeMillis() + 1000;
                while ((acc.hasMoreElements()) && (i < query.wantedResults) && (System.currentTimeMillis() < targetTime)) {
                    urlentry = acc.nextElement();
                    url = urlentry.url();
                    urlhash = urlentry.hash();
                    host = url.getHost();
                    if (host.endsWith(".yacyh")) {
                        // translate host into current IP
                        p = host.indexOf(".");
                        hash = yacySeed.hexHash2b64Hash(host.substring(p + 1, host.length() - 6));
                        seed = yacyCore.seedDB.getConnected(hash);
                        filename = url.getFile();
                        if ((seed == null) || ((address = seed.getAddress()) == null)) {
                            // seed is not known from here
                            removeReferences(urlentry.hash(), plasmaCondenser.getWords(("yacyshare " + filename.replace('?', ' ') + " " + urlentry.descr()).getBytes()));
                            urlPool.loadedURL.remove(urlentry.hash()); // clean up
                            continue; // next result
                        }
                        url = new URL("http://" + address + "/" + host.substring(0, p) + filename);
                        urlname = "http://share." + seed.getName() + ".yacy" + filename;
                        if ((p = urlname.indexOf("?")) > 0) urlname = urlname.substring(0, p);
                        urlstring = url.toNormalform();
                    } else {
                        urlstring = url.toNormalform();
                        urlname = urlstring;
                    }
                    descr = urlentry.descr();
                    
                    // check bluelist again: filter out all links where any bluelisted word
                    // appear either in url, url's description or search word
                    // the search word was sorted out earlier
                        /*
                        String s = descr.toLowerCase() + url.toString().toLowerCase();
                        for (int c = 0; c < blueList.length; c++) {
                            if (s.indexOf(blueList[c]) >= 0) return;
                        }
                         */
                    //addScoreForked(ref, gs, descr.split(" "));
                    //addScoreForked(ref, gs, urlstring.split("/"));
                    URL wordURL;
                    if (urlstring.matches(query.urlMask)) { //.* is default
                        if (includeSnippets) {
                            snippet = snippetCache.retrieveSnippet(url, query.queryHashes, false, 260, 1000);
                        } else {
                            snippet = null;
                        }
                        if ((snippet != null) && (snippet.getSource() == plasmaSnippetCache.ERROR_NO_MATCH)) {
                            // suppress line: there is no match in that resource
                        } else {
                            prop.put("type_results_" + i + "_recommend", (yacyCore.newsPool.getSpecific(yacyNewsPool.OUTGOING_DB, "stippadd", "url", urlstring) == null) ? 1 : 0);
                            prop.put("type_results_" + i + "_recommend_deletelink", "/yacysearch.html?search=" + formerSearch + "&Enter=Search&count=" + query.wantedResults + "&order=" + ranking.orderString() + "&resource=local&time=3&deleteref=" + urlhash + "&urlmaskfilter=.*");
                            prop.put("type_results_" + i + "_recommend_recommendlink", "/yacysearch.html?search=" + formerSearch + "&Enter=Search&count=" + query.wantedResults + "&order=" + ranking.orderString() + "&resource=local&time=3&recommendref=" + urlhash + "&urlmaskfilter=.*");
                            prop.put("type_results_" + i + "_description", descr);
                            prop.put("type_results_" + i + "_url", urlstring);
                            prop.put("type_results_" + i + "_urlhash", urlhash);
                            prop.put("type_results_" + i + "_urlhexhash", yacySeed.b64Hash2hexHash(urlhash));
                            prop.put("type_results_" + i + "_urlname", nxTools.shortenURLString(urlname, 120));
                            prop.put("type_results_" + i + "_date", dateString(urlentry.moddate()));
                            prop.put("type_results_" + i + "_ybr", plasmaSearchPreOrder.ybr(urlentry.hash()));
                            prop.put("type_results_" + i + "_size", Long.toString(urlentry.size()));
                            prop.put("type_results_" + i + "_words", URLEncoder.encode(query.queryWords.toString(),"UTF-8"));
                            prop.put("type_results_" + i + "_former", formerSearch);
                            prop.put("type_results_" + i + "_rankingprops", urlentry.word().toPropertyForm(true) + ", domLengthEstimated=" + indexURL.domLengthEstimation(urlhash) +
                                    ((indexURL.probablyRootURL(urlhash)) ? ", probablyRootURL" : "") + 
                                    (((wordURL = indexURL.probablyWordURL(urlhash, query.words(""))) != null) ? ", probablyWordURL=" + wordURL.toNormalform() : ""));
                            // adding snippet if available
                            if ((snippet != null) && (snippet.exists())) {
                                prop.put("type_results_" + i + "_snippet", 1);
                                prop.put("type_results_" + i + "_snippet_text", snippet.getLineMarked(query.queryHashes));
                            } else {
                                prop.put("type_results_" + i + "_snippet", 0);
                                prop.put("type_results_" + i + "_snippet_text", "");
                            }
                            i++;
                        }
                    }
                }
                log.logFine("SEARCH TIME AFTER RESULT PREPARATION: " + ((System.currentTimeMillis() - timestamp) / 1000) + " seconds");
                
                // calc some more cross-reference
                long remainingTime = query.maximumTime - (System.currentTimeMillis() - timestamp);
                if (remainingTime < 0) remainingTime = 1000;
                /*
                while ((acc.hasMoreElements()) && (((time + timestamp) < System.currentTimeMillis()))) {
                    urlentry = acc.nextElement();
                    urlstring = htmlFilterContentScraper.urlNormalform(urlentry.url());
                    descr = urlentry.descr();
                 
                    addScoreForked(ref, gs, descr.split(" "));
                    addScoreForked(ref, gs, urlstring.split("/"));
                }
                 **/
                //Object[] ws = ref.getScores(16, false, 2, Integer.MAX_VALUE);
                Object[] ws = acc.getReferences(16);
                log.logFine("SEARCH TIME AFTER XREF PREPARATION: " + ((System.currentTimeMillis() - timestamp) / 1000) + " seconds");
                
                    /*
                    System.out.print("DEBUG WORD-SCORE: ");
                    for (int ii = 0; ii < ws.length; ii++) System.out.print(ws[ii] + ", ");
                    System.out.println(" all words = " + ref.getElementCount() + ", total count = " + ref.getTotalCount());
                     */
                prop.put("type_references", ws);
                prop.put("type_linkcount", Integer.toString(i));
                prop.put("type_results", Integer.toString(i));
            }
            
            // log
            log.logInfo("EXIT WORD SEARCH: " + query.queryWords + " - " +
            prop.get("type_totalcount", "0") + " links found, " +
            prop.get("type_orderedcount", "0") + " links ordered, " +
            prop.get("type_linkcount", "?") + " links selected, " +
            ((System.currentTimeMillis() - timestamp) / 1000) + " seconds");
            return prop;
        } catch (IOException e) {
            return null;
        }
    }
    
    public serverObjects action(String actionName, serverObjects actionInput) {
        // perform an action. (not used)    
        return null;
    }
    
    public String toString() {
        // it is possible to use this method in the cgi pages.
        // actually it is used there for testing purpose
        return "PROPS: " + super.toString() + "; QUEUE: " + sbQueue.toString();
    }
    
    // method for index deletion
    public int removeAllUrlReferences(URL url, boolean fetchOnline) {
        return removeAllUrlReferences(indexURL.urlHash(url), fetchOnline);
    }
    
    public int removeAllUrlReferences(String urlhash, boolean fetchOnline) {
        // find all the words in a specific resource and remove the url reference from every word index
        // finally, delete the url entry
        
        // determine the url string
        plasmaCrawlLURL.Entry entry = urlPool.loadedURL.load(urlhash, null);
        if (entry == null) return 0;
        
        URL url = entry.url();
        if (url == null) return 0;
        
        InputStream resourceContent = null;
        try {
            // get the resource content
            Object[] resource = snippetCache.getResource(url, fetchOnline, 10000);
            resourceContent = (InputStream) resource[0];
            Long resourceContentLength = (Long) resource[1];
            
            // parse the resource
            plasmaParserDocument document = snippetCache.parseDocument(url, resourceContentLength.longValue(), resourceContent);
            
            // getting parsed body input stream
            InputStream docBodyInputStream = document.getText();
            
            // getting word iterator
            Iterator witer = plasmaCondenser.getWords(docBodyInputStream);
            
            // delete all word references
            int count = removeReferences(urlhash, witer);
            
            // finally delete the url entry itself
            urlPool.loadedURL.remove(urlhash);
            return count;
        } catch (ParserException e) {
            return 0;
        } finally {
            if (resourceContent != null) try { resourceContent.close(); } catch (Exception e) {/* ignore this */}
        }
    }
    
    public int removeReferences(URL url, Set words) {
        return removeReferences(indexURL.urlHash(url), words);
    }
    
    public int removeReferences(final String urlhash, final Set words) {
        // sequentially delete all word references
        // returns number of deletions
        Iterator iter = words.iterator();
        String word;
        int count = 0;
        while (iter.hasNext()) {
            word = (String) iter.next();
            // delete the URL reference in this word index
            if (wordIndex.removeEntry(indexEntryAttribute.word2hash(word), urlhash, true)) count++;
        }
        return count;
    }

    public int removeReferences(final String urlhash, final Iterator wordStatPropIterator) {
        // sequentially delete all word references
        // returns number of deletions
        Map.Entry entry;
        String word;
        int count = 0;
        while (wordStatPropIterator.hasNext()) {
            entry = (Map.Entry) wordStatPropIterator.next();
            word = (String) entry.getKey();
            // delete the URL reference in this word index
            if (wordIndex.removeEntry(indexEntryAttribute.word2hash(word), urlhash, true)) count++;
        }
        return count;
    }

    public int adminAuthenticated(httpHeader header) {
        
        String adminAccountBase64MD5 = getConfig("adminAccountBase64MD5", "");
        String authorization = ((String) header.get(httpHeader.AUTHORIZATION, "xxxxxx")).trim().substring(6);
        
        // security check against too long authorization strings
        if (authorization.length() > 256) return 0; 
        
        // authorization by encoded password, only for localhost access
        if ((((String) header.get("CLIENTIP", "")).equals("localhost")) && (adminAccountBase64MD5.equals(authorization))) return 3; // soft-authenticated for localhost

        // authorization by hit in userDB
        if (userDB.hasAdminRight((String) header.get(httpHeader.AUTHORIZATION, "xxxxxx"), ((String) header.get("CLIENTIP", "")), header.getHeaderCookies())) return 4; //return, because 4=max

        // authorization with admin keyword in configuration
        return staticAdminAuthenticated(authorization);
    }
    
    public int staticAdminAuthenticated(String authorization){
        if(authorization==null) return 1;
        //if (authorization.length() < 6) return 1; // no authentication information given
        //authorization = authorization.trim().substring(6);
        String adminAccountBase64MD5 = getConfig("adminAccountBase64MD5", "");
        if (adminAccountBase64MD5.length() == 0) return 2; // no passwrd stored
        if (adminAccountBase64MD5.equals(serverCodings.encodeMD5Hex(authorization))) return 4; // hard-authenticated, all ok
        return 0;
    }
    
    public boolean verifyAuthentication(httpHeader header, boolean strict) {
        // handle access rights
        switch (adminAuthenticated(header)) {
        case 0: // wrong password given
            try { Thread.sleep(3000); } catch (InterruptedException e) { } // prevent brute-force
            return false;
        case 1: // no password given
            return false;
        case 2: // no password stored
            return !strict;
        case 3: // soft-authenticated for localhost only
            return true;
        case 4: // hard-authenticated, all ok
            return true;
        }
        return false;
    }
    
    public void startTransferWholeIndex(yacySeed seed, boolean delete) {
        if (transferIdxThread == null) {
            this.transferIdxThread = new plasmaDHTFlush(this.log, this.wordIndex, seed, delete,
                                                        "true".equalsIgnoreCase(getConfig("indexTransfer.gzipBody","false")),
                                                        (int) getConfigLong("indexTransfer.timeout",60000));
            this.transferIdxThread.start();
        }
    }    

    public void stopTransferWholeIndex(boolean wait) {
        if ((transferIdxThread != null) && (transferIdxThread.isAlive()) && (!transferIdxThread.isFinished())) {
            try {
                this.transferIdxThread.stopIt(wait);
            } catch (InterruptedException e) { }
        }
    }    

    public void abortTransferWholeIndex(boolean wait) {
        if (transferIdxThread != null) {
            if (!transferIdxThread.isFinished())
                try {
                    this.transferIdxThread.stopIt(wait);
                } catch (InterruptedException e) { }
                transferIdxThread = null;
        }
    }
    
    public String dhtShallTransfer() {
        if (yacyCore.seedDB == null) {
            return "no DHT distribution: seedDB == null";
        }
        if (yacyCore.seedDB.mySeed == null) {
            return "no DHT distribution: mySeed == null";
        }
        if (yacyCore.seedDB.mySeed.isVirgin()) {
            return "no DHT distribution: status is virgin";
        }
        if (getConfig("allowDistributeIndex","false").equalsIgnoreCase("false")) {
            return "no DHT distribution: not enabled";
        }
        if (urlPool.loadedURL.size() < 10) {
            return "no DHT distribution: loadedURL.size() = " + urlPool.loadedURL.size();
        }
        if (wordIndex.size() < 100) {
            return "no DHT distribution: not enough words - wordIndex.size() = " + wordIndex.size();
        }
        if ((getConfig("allowDistributeIndexWhileCrawling","false").equalsIgnoreCase("false")) && (urlPool.noticeURL.stackSize() > 0)) {
            return "no DHT distribution: crawl in progress - noticeURL.stackSize() = " + urlPool.noticeURL.stackSize();
        }
        return null;
    }
    
    public boolean dhtTransferJob() {
        String rejectReason = dhtShallTransfer();
        if (rejectReason != null) {
            log.logFine(rejectReason);
            return false;
        }
        if (this.dhtTransferChunk == null) {
            log.logFine("no DHT distribution: no transfer chunk defined");
            return false;
        }
        if ((this.dhtTransferChunk != null) && (this.dhtTransferChunk.getStatus() != plasmaDHTChunk.chunkStatus_FILLED)) {
            log.logFine("no DHT distribution: index distribution is in progress, status=" + this.dhtTransferChunk.getStatus());
            return false;
        }
        
        // do the transfer
        int peerCount = (yacyCore.seedDB.mySeed.isJunior()) ? 1 : 3;
        long starttime = System.currentTimeMillis();
        
        boolean ok = dhtTransferProcess(dhtTransferChunk, peerCount);

        if (ok) {
            dhtTransferChunk.setStatus(plasmaDHTChunk.chunkStatus_COMPLETE);
            log.logFine("DHT distribution: transfer COMPLETE");
            // adopt transfer count
            if ((System.currentTimeMillis() - starttime) > (10000 * peerCount)) {
                dhtTransferIndexCount--;
            } else {
                if (dhtTransferChunk.indexCount() >= dhtTransferIndexCount) dhtTransferIndexCount++;
            }
            int minChunkSize = (int) getConfigLong("indexDistribution.minChunkSize", 30);
            int maxChunkSize = (int) getConfigLong("indexDistribution.maxChunkSize", 3000);
            if (dhtTransferIndexCount < minChunkSize) dhtTransferIndexCount = minChunkSize;
            if (dhtTransferIndexCount > maxChunkSize) dhtTransferIndexCount = maxChunkSize;
            
            // show success
            return true;
        } else {
            dhtTransferChunk.setStatus(plasmaDHTChunk.chunkStatus_FAILED);
            log.logFine("DHT distribution: transfer FAILED");
            return false;
        }
    }

    public boolean dhtTransferProcess(plasmaDHTChunk dhtChunk, int peerCount) {
        if ((yacyCore.seedDB == null) || (yacyCore.seedDB.sizeConnected() == 0)) return false;

        try {
            // find a list of DHT-peers
            ArrayList seeds = new ArrayList(Arrays.asList(yacyCore.dhtAgent.getDHTTargets(log, peerCount, 10, dhtChunk.firstContainer().getWordHash(), dhtChunk.lastContainer().getWordHash(), 0.4)));
            if (seeds.size() < peerCount) {
                log.logWarning("found not enough (" + seeds.size() + ") peers for distribution");
                return false;
            }

            // send away the indexes to all these peers
            int hc1 = 0;

            // getting distribution configuration values
            boolean gzipBody = getConfig("indexDistribution.gzipBody","false").equalsIgnoreCase("true");
            int timeout = (int)getConfigLong("indexDistribution.timeout",60000);
            int retries = 0;

            // starting up multiple DHT transfer threads   
            Iterator seedIter = seeds.iterator();
            ArrayList transfer = new ArrayList(peerCount);
            while (hc1 < peerCount && (transfer.size() > 0 || seedIter.hasNext())) {
                
                // starting up some transfer threads
                int transferThreadCount = transfer.size();
                for (int i=0; i < peerCount-hc1-transferThreadCount; i++) {
                    // check for interruption
                    checkInterruption();
                                        
                    if (seedIter.hasNext()) {
                        plasmaDHTTransfer t = new plasmaDHTTransfer(log, (yacySeed)seedIter.next(), dhtChunk,gzipBody,timeout,retries);
                        t.start();
                        transfer.add(t);
                    } else {
                        break;
                    }
                }

                // waiting for the transfer threads to finish
                Iterator transferIter = transfer.iterator();
                while (transferIter.hasNext()) {
                    // check for interruption
                    checkInterruption();
                    
                    plasmaDHTTransfer t = (plasmaDHTTransfer)transferIter.next();
                    if (!t.isAlive()) {
                        // remove finished thread from the list
                        transferIter.remove();

                        // count successful transfers
                        if (t.getStatus() == plasmaDHTChunk.chunkStatus_COMPLETE) {
                            this.log.logInfo("DHT distribution: transfer to peer " + t.getSeed().getName() + " finished.");
                            hc1++;
                        }
                    }
                }

                if (hc1 < peerCount) Thread.sleep(100);
            }


            // clean up and finish with deletion of indexes
            if (hc1 >= peerCount) {
                // success
                return true;
            }
            this.log.logSevere("Index distribution failed. Too few peers (" + hc1 + ") received the index, not deleted locally.");
            return false;
        } catch (InterruptedException e) {
            return false;
        }
    }
    
    private void addURLtoErrorDB(
            URL url, 
            String referrerHash, 
            String initiator, 
            String name, 
            String failreason, 
            bitfield flags
    ) {
        // create a new errorURL DB entry
        plasmaCrawlEURL.Entry ee = this.urlPool.errorURL.newEntry(
                url,
                referrerHash,
                initiator,
                yacyCore.seedDB.mySeed.hash,
                (name==null)?"":name,
                failreason,
                flags
        );
        // store the entry
        ee.store();
        // push it onto the stack
        this.urlPool.errorURL.stackPushEntry(ee);
    }    
    
    public void checkInterruption() throws InterruptedException {
        Thread curThread = Thread.currentThread();
        if ((curThread instanceof serverThread) && ((serverThread)curThread).shutdownInProgress()) throw new InterruptedException("Shutdown in progress ...");
        else if (this.terminate || curThread.isInterrupted()) throw new InterruptedException("Shutdown in progress ...");
    }
    
    public void terminate(long delay) {
        if (delay <= 0) throw new IllegalArgumentException("The shutdown delay must be greater than 0.");
        (new delayedShutdown(this,delay)).start();
    }
    
    public void terminate() {
        this.terminate = true;
        this.shutdownSync.V();
    }
    
    public boolean isTerminated() {
        return this.terminate;
    }
    
    public boolean waitForShutdown() throws InterruptedException {
        this.shutdownSync.P();
        return this.terminate;
    }
}

class delayedShutdown extends Thread {
    private plasmaSwitchboard sb;
    private long delay;
    public delayedShutdown(plasmaSwitchboard sb, long delay) {
        this.sb = sb;
        this.delay = delay;
    }
    
    public void run() {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.sb.terminate();
    }
}
