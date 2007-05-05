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
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.data.blogBoard;
import de.anomic.data.blogBoardComments;
import de.anomic.data.bookmarksDB;
import de.anomic.data.listManager;
import de.anomic.data.messageBoard;
import de.anomic.data.userDB;
import de.anomic.data.wikiBoard;
import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.http.httpHeader;
import de.anomic.http.httpRemoteProxyConfig;
import de.anomic.http.httpc;
import de.anomic.http.httpd;
import de.anomic.http.httpdRobotsTxtConfig;
import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.plasma.plasmaURL;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroCache;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.kelondro.kelondroMapTable;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroRecords;
import de.anomic.net.URL;
import de.anomic.plasma.dbImport.dbImportManager;
import de.anomic.plasma.parser.ParserException;
import de.anomic.plasma.urlPattern.defaultURLPattern;
import de.anomic.plasma.urlPattern.plasmaURLPattern;
import de.anomic.server.serverAbstractSwitch;
import de.anomic.server.serverDate;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverInstantThread;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSemaphore;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverThread;
import de.anomic.server.serverUpdaterCallback;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.crypt;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacySeed;

public final class plasmaSwitchboard extends serverAbstractSwitch implements serverSwitch {
    
    // load slots
    public static int crawlSlots            = 10;
    public static int indexingSlots         = 30;
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
    public  File                        indexPrimaryPath, indexSecondaryPath;
    public  File                        listsPath;
    public  File                        htDocsPath;
    public  File                        rankingPath;
    public  File                        workPath;
    public  HashMap                     rankingPermissions;
    public  plasmaCrawlNURL             noticeURL;
    public  plasmaCrawlZURL             errorURL, delegatedURL;
    public  plasmaWordIndex             wordIndex;
    public  plasmaHTCache               cacheManager;
    public  plasmaSnippetCache          snippetCache;
    public  plasmaCrawlLoader           cacheLoader;
    public  plasmaSwitchboardQueue      sbQueue;
    public  plasmaCrawlStacker          sbStackCrawlThread;
    public  messageBoard                messageDB;
    public  wikiBoard                   wikiDB;
    public  blogBoard                   blogDB;
    public  blogBoardComments           blogCommentDB;
    public  static plasmaCrawlRobotsTxt robots;
    public  plasmaCrawlProfile          profiles;
    public  plasmaCrawlProfile.entry    defaultProxyProfile;
    public  plasmaCrawlProfile.entry    defaultRemoteProfile;
    public  plasmaCrawlProfile.entry    defaultTextSnippetProfile;
    public  plasmaCrawlProfile.entry    defaultMediaSnippetProfile;
    public  boolean                     rankingOn;
    public  plasmaRankingDistribution   rankingOwnDistribution;
    public  plasmaRankingDistribution   rankingOtherDistribution;
    public  HashMap                     outgoingCookies, incomingCookies;
    public  kelondroMapTable            facilityDB;
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
    public  ArrayList                   localSearches, remoteSearches; // array of search result properties as HashMaps
    public  HashMap                     localSearchTracker, remoteSearchTracker;
    public  long                        startupTime = 0;
    public  long                        lastseedcheckuptime = -1;
    public  long                        indexedPages = 0;
    public  long                        lastindexedPages = 0;
    public  double                      requestedQueries = 0d;
    public  double                      lastrequestedQueries = 0d;
    public  int                         totalPPM = 0;
    public  double                      totalQPM = 0d;
    public  TreeMap                     clusterhashes; // map of peerhash(String)/alternative-local-address as ip:port or only ip (String) or null if address in seed should be used
    
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
    
    public httpdRobotsTxtConfig robotstxtConfig = null;
    
    
    /*
     * Some constants
     */
    private static final String STR_REMOTECRAWLTRIGGER = "REMOTECRAWLTRIGGER: REMOTE CRAWL TO PEER ";
    
    private serverSemaphore shutdownSync = new serverSemaphore(0);
    private boolean terminate = false;
    
    /**
     * Reference to the Updater callback class
     */
    public serverUpdaterCallback updaterCallback = null;
    
    //private Object  crawlingPausedSync = new Object();
    //private boolean crawlingIsPaused = false;    
    
    private static final int   CRAWLJOB_SYNC = 0;
    private static final int   CRAWLJOB_STATUS = 1;
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    // Thread settings
    //////////////////////////////////////////////////////////////////////////////////////////////
    
    // 20_dhtdistribution
    /**
     * <p><code>public static final String <strong>INDEX_DIST</strong> = "20_dhtdistribution"</code></p>
     * <p>Name of the DHT distribution thread, which selects index chunks and transfers them to other peers
     * according to the global DHT rules</p>
     */
    public static final String INDEX_DIST                   = "20_dhtdistribution";
    public static final String INDEX_DIST_METHOD_START      = "dhtTransferJob";
    public static final String INDEX_DIST_METHOD_JOBCOUNT   = null;
    public static final String INDEX_DIST_METHOD_FREEMEM    = null;
    public static final String INDEX_DIST_MEMPREREQ         = "20_dhtdistribution_memprereq";
    public static final String INDEX_DIST_IDLESLEEP         = "20_dhtdistribution_idlesleep";
    public static final String INDEX_DIST_BUSYSLEEP         = "20_dhtdistribution_busysleep";
    
    // 30_peerping
    /**
     * <p><code>public static final String <strong>PEER_PING</strong> = "30_peerping"</code></p>
     * <p>Name of the Peer Ping thread which publishes the own peer and retrieves information about other peers
     * connected to the YaCy-network</p>
     */
    public static final String PEER_PING                    = "30_peerping";
    public static final String PEER_PING_METHOD_START       = "peerPing";
    public static final String PEER_PING_METHOD_JOBCOUNT    = null;
    public static final String PEER_PING_METHOD_FREEMEM     = null;
    public static final String PEER_PING_IDLESLEEP          = "30_peerping_idlesleep";
    public static final String PEER_PING_BUSYSLEEP          = "30_peerping_busysleep";
    
    // 40_peerseedcycle
    /**
     * <p><code>public static final String <strong>SEED_UPLOAD</strong> = "40_peerseedcycle"</code></p>
     * <p>Name of the seed upload thread, providing the so-called seed-lists needed during bootstrapping</p>
     */
    public static final String SEED_UPLOAD                  = "40_peerseedcycle";
    public static final String SEED_UPLOAD_METHOD_START     = "publishSeedList";
    public static final String SEED_UPLOAD_METHOD_JOBCOUNT  = null;
    public static final String SEED_UPLOAD_METHOD_FREEMEM   = null;
    public static final String SEED_UPLOAD_IDLESLEEP        = "40_peerseedcycle_idlesleep";
    public static final String SEED_UPLOAD_BUSYSLEEP        = "40_peerseedcycle_busysleep";
    
    // 50_localcrawl
    /**
     * <p><code>public static final String <strong>CRAWLJOB_LOCAL_CRAWL</strong> = "50_localcrawl"</code></p>
     * <p>Name of the local crawler thread, popping one entry off the Local Crawl Queue, and passing it to the
     * proxy cache enqueue thread to download and further process it</p>
     * 
     * @see plasmaSwitchboard#PROXY_CACHE_ENQUEUE
     */
    public static final String CRAWLJOB_LOCAL_CRAWL                             = "50_localcrawl";
    public static final String CRAWLJOB_LOCAL_CRAWL_METHOD_START                = "coreCrawlJob";
    public static final String CRAWLJOB_LOCAL_CRAWL_METHOD_JOBCOUNT             = "coreCrawlJobSize";
    public static final String CRAWLJOB_LOCAL_CRAWL_METHOD_FREEMEM              = null;
    public static final String CRAWLJOB_LOCAL_CRAWL_IDLESLEEP                   = "50_localcrawl_idlesleep";
    public static final String CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP                   = "50_localcrawl_busysleep";
    
    // 61_globalcawltrigger
    /**
     * <p><code>public static final String <strong>CRAWLJOB_GLOBAL_CRAWL_TRIGGER</strong> = "61_globalcrawltrigger"</code></p>
     * <p>Name of the global crawl trigger thread, popping one entry off it's queue and sending it to a non-busy peer to
     * crawl it</p>
     * 
     * @see plasmaSwitchboard#CRAWLJOB_REMOTE_TRIGGERED_CRAWL
     */
    public static final String CRAWLJOB_GLOBAL_CRAWL_TRIGGER                    = "61_globalcrawltrigger";
    public static final String CRAWLJOB_GLOBAL_CRAWL_TRIGGER_METHOD_START       = "limitCrawlTriggerJob";
    public static final String CRAWLJOB_GLOBAL_CRAWL_TRIGGER_METHOD_JOBCOUNT    = "limitCrawlTriggerJobSize";
    public static final String CRAWLJOB_GLOBAL_CRAWL_TRIGGER_METHOD_FREEMEM     = null;
    public static final String CRAWLJOB_GLOBAL_CRAWL_TRIGGER_IDLESLEEP          = "61_globalcrawltrigger_idlesleep";
    public static final String CRAWLJOB_GLOBAL_CRAWL_TRIGGER_BUSYSLEEP          = "61_globalcrawltrigger_busysleep";
    
    // 62_remotetriggeredcrawl
    /**
     * <p><code>public static final String <strong>CRAWLJOB_REMOTE_TRIGGERED_CRAWL</strong> = "62_remotetriggeredcrawl"</code></p>
     * <p>Name of the remote triggered crawl thread, responsible for processing a remote crawl received from another peer</p>
     */
    public static final String CRAWLJOB_REMOTE_TRIGGERED_CRAWL                  = "62_remotetriggeredcrawl";
    public static final String CRAWLJOB_REMOTE_TRIGGERED_CRAWL_METHOD_START     = "remoteTriggeredCrawlJob";
    public static final String CRAWLJOB_REMOTE_TRIGGERED_CRAWL_METHOD_JOBCOUNT  = "remoteTriggeredCrawlJobSize";
    public static final String CRAWLJOB_REMOTE_TRIGGERED_CRAWL_METHOD_FREEMEM   = null;
    public static final String CRAWLJOB_REMOTE_TRIGGERED_CRAWL_IDLESLEEP        = "62_remotetriggeredcrawl_idlesleep";
    public static final String CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP        = "62_remotetriggeredcrawl_busysleep";
    
    // 70_cachemanager
    /**
     * <p><code>public static final String <strong>PROXY_CACHE_ENQUEUE</strong> = "70_cachemanager"</code></p>
     * <p>Name of the proxy cache enqueue thread which fetches a given website and saves the site itself as well as it's
     * HTTP-headers in the HTCACHE</p>
     * 
     * @see plasmaSwitchboard#PROXY_CACHE_PATH
     */
    public static final String PROXY_CACHE_ENQUEUE                  = "70_cachemanager";
    public static final String PROXY_CACHE_ENQUEUE_METHOD_START     = "htEntryStoreJob";
    public static final String PROXY_CACHE_ENQUEUE_METHOD_JOBCOUNT  = "htEntrySize";
    public static final String PROXY_CACHE_ENQUEUE_METHOD_FREEMEM   = null;
    public static final String PROXY_CACHE_ENQUEUE_IDLESLEEP        = "70_cachemanager_idlesleep";
    public static final String PROXY_CACHE_ENQUEUE_BUSYSLEEP        = "70_cachemanager_busysleep";
    
    // 80_indexing
    /**
     * <p><code>public static final String <strong>INDEXER</strong> = "80_indexing"</code></p>
     * <p>Name of the indexer thread, performing the actual indexing of a website</p>
     */
    public static final String INDEXER                      = "80_indexing";
    public static final String INDEXER_CLUSTER              = "80_indexing_cluster";
    public static final String INDEXER_MEMPREREQ            = "80_indexing_memprereq";
    public static final String INDEXER_IDLESLEEP            = "80_indexing_idlesleep";
    public static final String INDEXER_BUSYSLEEP            = "80_indexing_busysleep";
    public static final String INDEXER_METHOD_START         = "deQueue";
    public static final String INDEXER_METHOD_JOBCOUNT      = "queueSize";
    public static final String INDEXER_METHOD_FREEMEM       = "deQueueFreeMem";
    public static final String INDEXER_SLOTS                = "indexer.slots";
    
    // 82_crawlstack
    /**
     * <p><code>public static final String <strong>CRAWLSTACK</strong> = "82_crawlstack"</code></p>
     * <p>Name of the crawl stacker thread, performing several checks on new URLs to crawl, i.e. double-check</p>
     */
    public static final String CRAWLSTACK                   = "82_crawlstack";
    public static final String CRAWLSTACK_METHOD_START      = "job";
    public static final String CRAWLSTACK_METHOD_JOBCOUNT   = "size";
    public static final String CRAWLSTACK_METHOD_FREEMEM    = null;
    public static final String CRAWLSTACK_IDLESLEEP         = "82_crawlstack_idlesleep";
    public static final String CRAWLSTACK_BUSYSLEEP         = "82_crawlstack_busysleep";
    
    // 90_cleanup
    /**
     * <p><code>public static final String <strong>CLEANUP</strong> = "90_cleanup"</code></p>
     * <p>The cleanup thread which is responsible for pendant cleanup-jobs, news/ranking distribution, etc.</p> 
     */
    public static final String CLEANUP                      = "90_cleanup";
    public static final String CLEANUP_METHOD_START         = "cleanupJob";
    public static final String CLEANUP_METHOD_JOBCOUNT      = "cleanupJobSize";
    public static final String CLEANUP_METHOD_FREEMEM       = null;
    public static final String CLEANUP_IDLESLEEP            = "90_cleanup_idlesleep";
    public static final String CLEANUP_BUSYSLEEP            = "90_cleanup_busysleep";
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    // RAM Cache settings
    //////////////////////////////////////////////////////////////////////////////////////////////
    
    /**
     * <p><code>public static final String <strong>RAM_CACHE_LURL</strong> = "ramCacheLURL"</code></p>
     * <p>Name of the setting how much memory in bytes should be assigned to the Loaded URLs DB for caching purposes</p>
     */
    public static final String RAM_CACHE_LURL_TIME      = "ramCacheLURL_time";
    /**
     * <p><code>public static final String <strong>RAM_CACHE_NURL</strong> = "ramCacheNURL"</code></p>
     * <p>Name of the setting how much memory in bytes should be assigned to the Noticed URLs DB for caching purposes</p>
     */
    public static final String RAM_CACHE_NURL_TIME      = "ramCacheNURL_time";
    /**
     * <p><code>public static final String <strong>RAM_CACHE_EURL</strong> = "ramCacheEURL"</code></p>
     * <p>Name of the setting how much memory in bytes should be assigned to the Erroneous URLs DB for caching purposes</p>
     */
    public static final String RAM_CACHE_EURL_TIME      = "ramCacheEURL_time";
    /**
     * <p><code>public static final String <strong>RAM_CACHE_RWI</strong> = "ramCacheRWI"</code></p>
     * <p>Name of the setting how much memory in bytes should be assigned to the RWIs DB for caching purposes</p>
     */
    public static final String RAM_CACHE_RWI_TIME       = "ramCacheRWI_time";
    /**
     * <p><code>public static final String <strong>RAM_CACHE_HTTP</strong> = "ramCacheHTTP"</code></p>
     * <p>Name of the setting how much memory in bytes should be assigned to the HTTP Headers DB for caching purposes</p>
     */
    public static final String RAM_CACHE_HTTP_TIME      = "ramCacheHTTP_time";
    /**
     * <p><code>public static final String <strong>RAM_CACHE_MESSAGE</strong> = "ramCacheMessage"</code></p>
     * <p>Name of the setting how much memory in bytes should be assigned to the Message DB for caching purposes</p>
     */
    public static final String RAM_CACHE_MESSAGE_TIME   = "ramCacheMessage_time";
    /**
     * <p><code>public static final String <strong>RAM_CACHE_ROBOTS</strong> = "ramCacheRobots"</code></p>
     * <p>Name of the setting how much memory in bytes should be assigned to the robots.txts DB for caching purposes</p>
     */
    public static final String RAM_CACHE_ROBOTS_TIME    = "ramCacheRobots_time";
    /**
     * <p><code>public static final String <strong>RAM_CACHE_PROFILES</strong> = "ramCacheProfiles"</code></p>
     * <p>Name of the setting how much memory in bytes should be assigned to the Crawl Profiles DB for caching purposes</p>
     */
    public static final String RAM_CACHE_PROFILES_TIME  = "ramCacheProfiles_time";
    /**
     * <p><code>public static final String <strong>RAM_CACHE_PRE_NURL</strong> = "ramCachePreNURL"</code></p>
     * <p>Name of the setting how much memory in bytes should be assigned to the Pre-Noticed URLs DB for caching purposes</p>
     */
    public static final String RAM_CACHE_PRE_NURL_TIME  = "ramCachePreNURL_time";
    /**
     * <p><code>public static final String <strong>RAM_CACHE_WIKI</strong> = "ramCacheWiki"</code></p>
     * <p>Name of the setting how much memory in bytes should be assigned to the Wiki DB for caching purposes</p>
     */
    public static final String RAM_CACHE_WIKI_TIME      = "ramCacheWiki_time";
    /**
     * <p><code>public static final String <strong>RAM_CACHE_BLOG</strong> = "ramCacheBlog"</code></p>
     * <p>Name of the setting how much memory in bytes should be assigned to the Blog DB for caching purposes</p>
     */
    public static final String RAM_CACHE_BLOG_TIME      = "ramCacheBlog_time";
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    // DHT settings
    //////////////////////////////////////////////////////////////////////////////////////////////
    
    /**
     * <p><code>public static final String <strong>INDEX_DIST_DHT_RECEIPT_LIMIT</strong> = "indexDistribution.dhtReceiptLimit"</code></p>
     * <p>Name of the setting how many words the DHT-In cache may contain maximal before new DHT receipts
     * will be rejected</p>
     */
    public static final String INDEX_DIST_DHT_RECEIPT_LIMIT     = "indexDistribution.dhtReceiptLimit";
    /**
     * <p><code>public static final String <strong>INDEX_DIST_CHUNK_SIZE_START</strong> = "indexDistribution.startChunkSize"</code></p>
     * <p>Name of the setting specifying how many words the very first chunk will contain when the DHT-thread starts</p>
     */
    public static final String INDEX_DIST_CHUNK_SIZE_START      = "indexDistribution.startChunkSize";
    /**
     * <p><code>public static final String <strong>INDEX_DIST_CHUNK_SIZE_MIN</strong> = "indexDistribution.minChunkSize"</code></p>
     * <p>Name of the setting specifying how many words the smallest chunk may contain</p>
     */
    public static final String INDEX_DIST_CHUNK_SIZE_MIN        = "indexDistribution.minChunkSize";
    /**
     * <p><code>public static final String <strong>INDEX_DIST_CHUNK_SIZE_MAX</strong> = "indexDistribution.maxChunkSize"</code></p>
     * <p>Name of the setting specifying how many words the hugest chunk may contain</p>
     */
    public static final String INDEX_DIST_CHUNK_SIZE_MAX        = "indexDistribution.maxChunkSize";
    public static final String INDEX_DIST_CHUNK_FAILS_MAX       = "indexDistribution.maxChunkFails";
    /**
     * <p><code>public static final String <strong>INDEX_DIST_TIMEOUT</strong> = "indexDistribution.timeout"</code></p>
     * <p>Name of the setting how long the timeout for an Index Distribution shall be in milliseconds</p>
     */
    public static final String INDEX_DIST_TIMEOUT               = "indexDistribution.timeout";
    /**
     * <p><code>public static final String <strong>INDEX_DIST_GZIP_BODY</strong> = "indexDistribution.gzipBody"</code></p>
     * <p>Name of the setting whether DHT chunks shall be transferred gzip-encodedly</p>
     */
    public static final String INDEX_DIST_GZIP_BODY             = "indexDistribution.gzipBody";
    /**
     * <p><code>public static final String <strong>INDEX_DIST_ALLOW</strong> = "allowDistributeIndex"</code></p>
     * <p>Name of the setting whether Index Distribution shall be allowed (and the DHT-thread therefore started) or not</p>
     * 
     * @see plasmaSwitchboard#INDEX_DIST_ALLOW_WHILE_CRAWLING
     */
    public static final String INDEX_DIST_ALLOW                 = "allowDistributeIndex";
    public static final String INDEX_RECEIVE_ALLOW              = "allowReceiveIndex";
    /**
     * <p><code>public static final String <strong>INDEX_DIST_ALLOW_WHILE_CRAWLING</strong> = "allowDistributeIndexWhileCrawling"</code></p>
     * <p>Name of the setting whether Index Distribution shall be allowed while crawling is in progress, i.e.
     * the Local Crawler Queue is filled.</p>
     * <p>This setting only has effect if {@link #INDEX_DIST_ALLOW} is enabled</p>
     * 
     * @see plasmaSwitchboard#INDEX_DIST_ALLOW
     */
    public static final String INDEX_DIST_ALLOW_WHILE_CRAWLING  = "allowDistributeIndexWhileCrawling";
    public static final String INDEX_TRANSFER_TIMEOUT           = "indexTransfer.timeout";
    public static final String INDEX_TRANSFER_GZIP_BODY         = "indexTransfer.gzipBody";
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    // Ranking settings
    //////////////////////////////////////////////////////////////////////////////////////////////
    
    public static final String RANKING_DIST_ON                  = "CRDistOn";
    public static final String RANKING_DIST_0_PATH              = "CRDist0Path";
    public static final String RANKING_DIST_0_METHOD            = "CRDist0Method";
    public static final String RANKING_DIST_0_PERCENT           = "CRDist0Percent";
    public static final String RANKING_DIST_0_TARGET            = "CRDist0Target";
    public static final String RANKING_DIST_1_PATH              = "CRDist1Path";
    public static final String RANKING_DIST_1_METHOD            = "CRDist1Method";
    public static final String RANKING_DIST_1_PERCENT           = "CRDist1Percent";
    public static final String RANKING_DIST_1_TARGET            = "CRDist1Target";
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    // Parser settings
    //////////////////////////////////////////////////////////////////////////////////////////////
    
    public static final String PARSER_MIMETYPES_REALTIME        = "parseableRealtimeMimeTypes";
    public static final String PARSER_MIMETYPES_PROXY           = "parseableMimeTypes.PROXY";
    public static final String PARSER_MIMETYPES_CRAWLER         = "parseableMimeTypes.CRAWLER";
    public static final String PARSER_MIMETYPES_ICAP            = "parseableMimeTypes.ICAP";
    public static final String PARSER_MIMETYPES_URLREDIRECTOR   = "parseableMimeTypes.URLREDIRECTOR";
    public static final String PARSER_MEDIA_EXT                 = "mediaExt";
    public static final String PARSER_MEDIA_EXT_PARSEABLE       = "parseableExt";
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    // Proxy settings
    //////////////////////////////////////////////////////////////////////////////////////////////
    
    /**
     * <p><code>public static final String <strong>PROXY_ONLINE_CAUTION_DELAY</strong> = "onlineCautionDelay"</code></p>
     * <p>Name of the setting how long indexing should pause after the last time the proxy was used in milliseconds</p> 
     */
    public static final String PROXY_ONLINE_CAUTION_DELAY       = "onlineCautionDelay";
    /**
     * <p><code>public static final String <strong>PROXY_PREFETCH_DEPTH</strong> = "proxyPrefetchDepth"</code></p>
     * <p>Name of the setting how deep URLs fetched by proxy usage shall be followed</p>
     */
    public static final String PROXY_PREFETCH_DEPTH             = "proxyPrefetchDepth";
    public static final String PROXY_CRAWL_ORDER                = "proxyCrawlOrder";
    
    public static final String PROXY_CACHE_SIZE                 = "proxyCacheSize";
    /**
     * <p><code>public static final String <strong>PROXY_CACHE_LAYOUT</strong> = "proxyCacheLayout"</code></p>
     * <p>Name of the setting which file-/folder-layout the proxy cache shall use. Possible values are {@link #PROXY_CACHE_LAYOUT_TREE}
     * and {@link #PROXY_CACHE_LAYOUT_HASH}</p>
     * 
     * @see plasmaSwitchboard#PROXY_CACHE_LAYOUT_TREE
     * @see plasmaSwitchboard#PROXY_CACHE_LAYOUT_HASH
     */
    public static final String PROXY_CACHE_LAYOUT               = "proxyCacheLayout";
    /**
     * <p><code>public static final String <strong>PROXY_CACHE_LAYOUT_TREE</strong> = "tree"</code></p>
     * <p>Setting the file-/folder-structure for {@link #PROXY_CACHE_LAYOUT}. Websites are stored in a folder-layout
     * according to the layout, the URL purported. The first folder is either <code>http</code> or <code>https</code>
     * depending on the protocol used to fetch the website, descending follows the hostname and the sub-folders on the
     * website up to the actual file itself.</p>  
     * <p>When using <code>tree</code>, be aware that
     * the possibility of inconsistencies between folders and files with the same name may occur which prevent proper
     * storage of the fetched site. Below is an example how files are stored:</p>
     * <pre>
     * /html/
     * /html/www.example.com/
     * /html/www.example.com/index/
     * /html/www.example.com/index/en/
     * /html/www.example.com/index/en/index.html</pre>
     */
    public static final String PROXY_CACHE_LAYOUT_TREE          = "tree";
    /**
     * <p><code>public static final String <strong>PROXY_CACHE_LAYOUT_HASH</strong> = "hash"</code></p>
     * <p>Setting the file-/folder-structure for {@link #PROXY_CACHE_LAYOUT}. Websites are stored using the MD5-sum of
     * their respective URLs. This method prevents collisions on some websites caused by using the {@link #PROXY_CACHE_LAYOUT_TREE}
     * layout.</p>
     * <p>Similarly to {@link #PROXY_CACHE_LAYOUT_TREE}, the top-folders name is given by the protocol used to fetch the site,
     * followed by either <code>www</code> or &ndash; if the hostname does not start with "www" &ndash; <code>other</code>.
     * Afterwards the next folder has the rest of the hostname as name, followed by a folder <code>hash</code> which contains
     * a folder consisting of the first two letters of the hash. Another folder named after the 3rd and 4th letters of the
     * hash follows which finally contains the file named after the full 18-characters long hash.
     * Below is an example how files are stored:</p>
     * <pre>
     * /html/
     * /html/www/
     * /html/www/example.com/
     * /html/www/example.com/hash/
     * /html/www/example.com/hash/0d/
     * /html/www/example.com/hash/0d/f8/
     * /html/www/example.com/hash/0d/f8/0df83a8444f48317d8</pre>
     */
    public static final String PROXY_CACHE_LAYOUT_HASH          = "hash";
    public static final String PROXY_CACHE_MIGRATION            = "proxyCacheMigration";
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    // Miscellaneous settings
    //////////////////////////////////////////////////////////////////////////////////////////////
    
    public static final String CRAWL_PROFILE_PROXY              = "proxy";
    public static final String CRAWL_PROFILE_REMOTE             = "remote";
    public static final String CRAWL_PROFILE_SNIPPET_TEXT       = "snippetText";
    public static final String CRAWL_PROFILE_SNIPPET_MEDIA      = "snippetMedia";
    
    /**
     * <p><code>public static final String <strong>CRAWLER_THREADS_ACTIVE_MAX</strong> = "crawler.MaxActiveThreads"</code></p>
     * <p>Name of the setting how many active crawler-threads may maximal be running on the same time</p>
     */
    public static final String CRAWLER_THREADS_ACTIVE_MAX       = "crawler.MaxActiveThreads";
    
    public static final String OWN_SEED_FILE                    = "yacyOwnSeedFile";
    /**
     * <p><code>public static final String <strong>STORAGE_PEER_HASH</strong> = "storagePeerHash"</code></p>
     * <p>Name of the setting holding the Peer-Hash where indexes shall be transferred after indexing a webpage. If this setting
     * is empty, the Storage Peer function is disabled</p>
     */
    public static final String STORAGE_PEER_HASH                = "storagePeerHash";
    public static final String YACY_MODE_DEBUG                  = "yacyDebugMode";
    
    public static final String WORDCACHE_INIT_COUNT             = "wordCacheInitCount";
    /**
     * <p><code>public static final String <strong>WORDCACHE_MAX_COUNT</strong> = "wordCacheMaxCount"</code></p>
     * <p>Name of the setting how many words the word-cache (or DHT-Out cache) shall contain maximal. Indexing pages if the
     * cache has reached this limit will slow down the indexing process by flushing some of it's entries</p>
     */
    public static final String WORDCACHE_MAX_COUNT              = "wordCacheMaxCount";
    
    public static final String HTTPC_NAME_CACHE_CACHING_PATTERNS_NO = "httpc.nameCacheNoCachingPatterns";
    
    public static final String ROBOTS_TXT                       = "httpd.robots.txt";
    public static final String ROBOTS_TXT_DEFAULT               = httpdRobotsTxtConfig.LOCKED + "," + httpdRobotsTxtConfig.DIRS;
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    // Lists
    //////////////////////////////////////////////////////////////////////////////////////////////
    
    /**
     * <p><code>public static final String <strong>BLACKLIST_CLASS</strong> = "Blacklist.class"</code></p>
     * <p>Name of the setting which Blacklist backend shall be used. Due to different requirements of users, the
     * {@link plasmaURLPattern}-interface has been created to support blacklist engines different from YaCy's default</p>
     * <p>Attention is required when the backend is changed, because different engines may have different syntaxes</p>
     */
    public static final String BLACKLIST_CLASS          = "BlackLists.class";
    /**
     * <p><code>public static final String <strong>BLACKLIST_CLASS_DEFAULT</strong> = "de.anomic.plasma.urlPattern.defaultURLPattern"</code></p>
     * <p>Package and name of YaCy's {@link defaultURLPattern default} blacklist implementation</p>
     * 
     * @see defaultURLPattern for a detailed overview about the syntax of the default implementation
     */
    public static final String BLACKLIST_CLASS_DEFAULT  = "de.anomic.plasma.urlPattern.defaultURLPattern";
    
    public static final String LIST_BLUE                = "plasmaBlueList";
    public static final String LIST_BLUE_DEFAULT        = null;
    public static final String LIST_BADWORDS_DEFAULT    = "yacy.badwords";
    public static final String LIST_STOPWORDS_DEFAULT   = "yacy.stopwords";
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    // DB Paths
    //////////////////////////////////////////////////////////////////////////////////////////////
    
    /**
     * <p><code>public static final String <strong>DBPATH</strong> = "dbPath"</code></p>
     * <p>Name of the setting specifying the folder beginning from the YaCy-installation's top-folder, where all
     * databases containing queues are stored</p>
     */
    public static final String DBPATH                   = "dbPath";
    public static final String DBPATH_DEFAULT           = "DATA/PLASMADB";
    /**
     * <p><code>public static final String <strong>HTCACHE_PATH</strong> = "proxyCache"</code></p>
     * <p>Name of the setting specifying the folder beginning from the YaCy-installation's top-folder, where all
     * downloaded webpages and their respective ressources and HTTP-headers are stored. It is the location containing
     * the proxy-cache</p>
     * 
     * @see plasmaSwitchboard#PROXY_CACHE_LAYOUT for details on the file-layout in this path
     */
    public static final String HTCACHE_PATH             = "proxyCache";
    public static final String HTCACHE_PATH_DEFAULT     = "DATA/HTCACHE";
    /**
     * <p><code>public static final String <strong>HTDOCS_PATH</strong> = "htDocsPath"</code></p>
     * <p>Name of the setting specifying the folder beginning from the YaCy-installation's top-folder, where all
     * user-ressources (i.e. for the fileshare or the contents displayed on <code>www.peername.yacy</code>) lie.
     * The translated templates of the webinterface will also be put in here</p>
     */
    public static final String HTDOCS_PATH              = "htDocsPath";
    public static final String HTDOCS_PATH_DEFAULT      = "DATA/HTDOCS";
    /**
     * <p><code>public static final String <strong>HTROOT_PATH</strong> = "htRootPath"</code></p>
     * <p>Name of the setting specifying the folder beginning from the YaCy-installation's top-folder, where all
     * original servlets, their stylesheets, scripts, etc. lie. It is also home of the XML-interface to YaCy</p> 
     */
    public static final String HTROOT_PATH              = "htRootPath";
    public static final String HTROOT_PATH_DEFAULT      = "htroot";
    /**
     * <p><code>public static final String <strong>INDEX_PATH</strong> = "indexPath"</code></p>
     * <p>Name of the setting specifying the folder beginning from the YaCy-installation's top-folder, where the
     * whole database of known RWIs and URLs as well as dumps of the DHT-In and DHT-Out caches are stored</p>
     */
    public static final String INDEX_PRIMARY_PATH       = "indexPrimaryPath"; // this is a relative path to the data root
    public static final String INDEX_SECONDARY_PATH     = "indexSecondaryPath"; // this is a absolute path to any location
    public static final String INDEX_PATH_DEFAULT       = "DATA/INDEX";
    /**
     * <p><code>public static final String <strong>LISTS_PATH</strong> = "listsPath"</code></p>
     * <p>Name of the setting specifying the folder beginning from the YaCy-installation's top-folder, where all
     * user-lists like blacklists, etc. are stored</p>
     */
    public static final String LISTS_PATH               = "listsPath";
    public static final String LISTS_PATH_DEFAULT       = "DATA/LISTS";
    /**
     * <p><code>public static final String <strong>RANKING_PATH</strong> = "rankingPath"</code></p>
     * <p>Name of the setting specifying the folder beginning from the YaCy-installation's top-folder, where all
     * ranking files are stored, self-generated as well as received ranking files</p>
     * 
     * @see plasmaSwitchboard#RANKING_DIST_0_PATH
     * @see plasmaSwitchboard#RANKING_DIST_1_PATH
     */
    public static final String RANKING_PATH             = "rankingPath";
    public static final String RANKING_PATH_DEFAULT     = "DATA/RANKING";
    /**
     * <p><code>public static final String <strong>WORK_PATH</strong> = "wordPath"</code></p>
     * <p>Name of the setting specifying the folder beginning from the YaCy-installation's top-folder, where all
     * DBs containing "work" of the user are saved. Such include bookmarks, messages, wiki, blog</p>
     * 
     * @see plasmaSwitchboard#DBFILE_BLOG
     * @see plasmaSwitchboard#DBFILE_BOOKMARKS
     * @see plasmaSwitchboard#DBFILE_BOOKMARKS_DATES
     * @see plasmaSwitchboard#DBFILE_BOOKMARKS_TAGS
     * @see plasmaSwitchboard#DBFILE_MESSAGE
     * @see plasmaSwitchboard#DBFILE_WIKI
     * @see plasmaSwitchboard#DBFILE_WIKI_BKP
     */
    public static final String WORK_PATH                = "workPath";
    public static final String WORK_PATH_DEFAULT        = "DATA/WORK";
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    // DB files
    //////////////////////////////////////////////////////////////////////////////////////////////
    
    /**
     * <p><code>public static final String <strong>DBFILE_MESSAGE</strong> = "message.db"</code></p>
     * <p>Name of the file containing the database holding the user's peer-messages</p>
     * 
     * @see plasmaSwitchboard#WORK_PATH for the folder, this file lies in
     */
    public static final String DBFILE_MESSAGE           = "message.db";
    /**
     * <p><code>public static final String <strong>DBFILE_WIKI</strong> = "wiki.db"</code></p>
     * <p>Name of the file containing the database holding the whole wiki of this peer</p>
     * 
     * @see plasmaSwitchboard#WORK_PATH for the folder, this file lies in
     * @see plasmaSwitchboard#DBFILE_WIKI_BKP for the file previous versions of wiki-pages lie in
     */
    public static final String DBFILE_WIKI              = "wiki.db";
    /**
     * <p><code>public static final String <strong>DBFILE_WIKI_BKP</strong> = "wiki-bkp.db"</code></p>
     * <p>Name of the file containing the database holding all versions but the latest of the wiki-pages of this peer</p>
     * 
     * @see plasmaSwitchboard#WORK_PATH for the folder this file lies in
     * @see plasmaSwitchboard#DBFILE_WIKI for the file the latest version of wiki-pages lie in
     */
    public static final String DBFILE_WIKI_BKP          = "wiki-bkp.db";
    /**
     * <p><code>public static final String <strong>DBFILE_BLOG</strong> = "blog.db"</code></p>
     * <p>Name of the file containing the database holding all blog-entries available on this peer</p>
     * 
     * @see plasmaSwitchboard#WORK_PATH for the folder this file lies in
     */
    public static final String DBFILE_BLOG              = "blog.db";
    /**
     * <p><code>public static final String <strong>DBFILE_BLOGCOMMENTS</strong> = "blogComment.db"</code></p>
     * <p>Name of the file containing the database holding all blogComment-entries available on this peer</p>
     * 
     * @see plasmaSwitchboard#WORK_PATH for the folder this file lies in
     */
    public static final String DBFILE_BLOGCOMMENTS      = "blogComment.db";
    /**
     * <p><code>public static final String <strong>DBFILE_BOOKMARKS</strong> = "bookmarks.db"</code></p>
     * <p>Name of the file containing the database holding all bookmarks available on this peer</p>
     * 
     * @see plasmaSwitchboard#WORK_PATH for the folder this file lies in
     * @see bookmarksDB for more detailed overview about the bookmarks structure
     */
    public static final String DBFILE_BOOKMARKS         = "bookmarks.db";
    /**
     * <p><code>public static final String <strong>DBFILE_BOOKMARKS_TAGS</strong> = "bookmarkTags.db"</code></p>
     * <p>Name of the file containing the database holding all tag-&gt;bookmark relations</p>
     * 
     * @see plasmaSwitchboard#WORK_PATH for the folder this file lies in
     * @see bookmarksDB for more detailed overview about the bookmarks structure
     */
    public static final String DBFILE_BOOKMARKS_TAGS    = "bookmarkTags.db";
    /**
     * <p><code>public static final String <strong>DBFILE_BOOKMARKS_DATES</strong> = "bookmarkDates.db"</code></p>
     * <p>Name of the file containing the database holding all date-&gt;bookmark relations</p>
     * 
     * @see plasmaSwitchboard#WORK_PATH for the folder this file lies in
     * @see bookmarksDB for more detailed overview about the bookmarks structure
     */
    public static final String DBFILE_BOOKMARKS_DATES   = "bookmarkDates.db";
    /**
     * <p><code>public static final String <strong>DBFILE_OWN_SEED</strong> = "mySeed.txt"</code></p>
     * <p>Name of the file containing the database holding this peer's seed</p>
     */
    public static final String DBFILE_OWN_SEED          = "mySeed.txt";
    /**
     * <p><code>public static final String <strong>DBFILE_CRAWL_PROFILES</strong> = "crawlProfiles0.db"</code>
     * <p>Name of the file containing the database holding all recent crawl profiles</p>
     * 
     * @see plasmaSwitchboard#DBPATH for the folder this file lies in
     */
    public static final String DBFILE_CRAWL_PROFILES    = "crawlProfiles0.db";
    /**
     * <p><code>public static final String <strong>DBFILE_CRAWL_ROBOTS</strong> = "crawlRobotsTxt.db"</code></p>
     * <p>Name of the file containing the database holding all <code>robots.txt</code>-entries of the lately crawled domains</p>
     * 
     * @see plasmaSwitchboard#DBPATH for the folder this file lies in
     */
    public static final String DBFILE_CRAWL_ROBOTS      = "crawlRobotsTxt.db";
    /**
     * <p><code>public static final String <strong>DBFILE_USER</strong> = "DATA/SETTINGS/user.db"</code></p>
     * <p>Path to the user-DB, beginning from the YaCy-installation's top-folder. It holds all rights the created
     * users have as well as all other needed data about them</p>
     */
    public static final String DBFILE_USER              = "DATA/SETTINGS/user.db";
    
    
    private Hashtable crawlJobsStatus = new Hashtable(); 
    
    private static plasmaSwitchboard sb;

    public plasmaSwitchboard(String rootPath, String initPath, String configPath) {
        super(rootPath, initPath, configPath);
        sb=this;
        
        // set loglevel and log
        setLog(new serverLog("PLASMA"));
        
        // load values from configs
        this.plasmaPath   = new File(rootPath, getConfig(DBPATH, DBPATH_DEFAULT));
        this.log.logConfig("Plasma DB Path: " + this.plasmaPath.toString());
        this.indexPrimaryPath = new File(rootPath, getConfig(INDEX_PRIMARY_PATH, INDEX_PATH_DEFAULT));
        this.log.logConfig("Index Primary Path: " + this.indexPrimaryPath.toString());
        this.indexSecondaryPath = (getConfig(INDEX_SECONDARY_PATH, "").length() == 0) ? indexPrimaryPath : new File(getConfig(INDEX_SECONDARY_PATH, ""));
        this.log.logConfig("Index Secondary Path: " + this.indexSecondaryPath.toString());
        this.listsPath      = new File(rootPath, getConfig(LISTS_PATH, LISTS_PATH_DEFAULT));
        this.log.logConfig("Lists Path:     " + this.listsPath.toString());
        this.htDocsPath   = new File(rootPath, getConfig(HTDOCS_PATH, HTDOCS_PATH_DEFAULT));
        this.log.logConfig("HTDOCS Path:    " + this.htDocsPath.toString());
        this.rankingPath   = new File(rootPath, getConfig(RANKING_PATH, RANKING_PATH_DEFAULT));
        this.log.logConfig("Ranking Path:    " + this.rankingPath.toString());
        this.rankingPermissions = new HashMap(); // mapping of permission - to filename.
        this.workPath   = new File(rootPath, getConfig(WORK_PATH, WORK_PATH_DEFAULT));
        this.log.logConfig("Work Path:    " + this.workPath.toString());
        
        /* ============================================================================
         * Remote Proxy configuration
         * ============================================================================ */
        this.remoteProxyConfig = httpRemoteProxyConfig.init(this);
        this.log.logConfig("Remote proxy configuration:\n" + this.remoteProxyConfig.toString());
        
        // set up local robots.txt
        this.robotstxtConfig = httpdRobotsTxtConfig.init(this);
        
        // setting timestamp of last proxy access
        this.proxyLastAccess = System.currentTimeMillis() - 60000;
        crg = new StringBuffer(maxCRGDump);
        //crl = new StringBuffer(maxCRLDump);
        
        // configuring list path
        if (!(listsPath.exists())) listsPath.mkdirs();
        
        // load coloured lists
        if (blueList == null) {
            // read only once upon first instantiation of this class
            String f = getConfig(LIST_BLUE, LIST_BLUE_DEFAULT);
            File plasmaBlueListFile = new File(f);
            if (f != null) blueList = kelondroMSetTools.loadList(plasmaBlueListFile, kelondroNaturalOrder.naturalOrder); else blueList= new TreeSet();
            this.log.logConfig("loaded blue-list from file " + plasmaBlueListFile.getName() + ", " +
            blueList.size() + " entries, " +
            ppRamString(plasmaBlueListFile.length()/1024));
        }
        
        // load the black-list / inspired by [AS]
        File ulrBlackListFile = new File(getRootPath(), getConfig(LISTS_PATH, LISTS_PATH_DEFAULT));
        String blacklistClassName = getConfig(BLACKLIST_CLASS, BLACKLIST_CLASS_DEFAULT);
        
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
            File badwordsFile = new File(rootPath, LIST_BADWORDS_DEFAULT);
            badwords = kelondroMSetTools.loadList(badwordsFile, kelondroNaturalOrder.naturalOrder);
            this.log.logConfig("loaded badwords from file " + badwordsFile.getName() +
                               ", " + badwords.size() + " entries, " +
                               ppRamString(badwordsFile.length()/1024));
        }

        // load stopwords
        if (stopwords == null) {
            File stopwordsFile = new File(rootPath, LIST_STOPWORDS_DEFAULT);
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
        long ramLURL_time    = getConfigLong(RAM_CACHE_LURL_TIME, 1000);
        long ramNURL_time    = getConfigLong(RAM_CACHE_NURL_TIME, 1000);
        long ramEURL_time    = getConfigLong(RAM_CACHE_EURL_TIME, 1000);
        long ramRWI_time     = getConfigLong(RAM_CACHE_RWI_TIME, 1000);
        long ramHTTP_time    = getConfigLong(RAM_CACHE_HTTP_TIME, 1000);
        long ramMessage_time = getConfigLong(RAM_CACHE_MESSAGE_TIME, 1000);
        long ramRobots_time  = getConfigLong(RAM_CACHE_ROBOTS_TIME, 1000);
        long ramProfiles_time= getConfigLong(RAM_CACHE_PROFILES_TIME, 1000);
        long ramPreNURL_time = getConfigLong(RAM_CACHE_PRE_NURL_TIME, 1000);
        long ramWiki_time    = getConfigLong(RAM_CACHE_WIKI_TIME, 1000);
        long ramBlog_time    = getConfigLong(RAM_CACHE_BLOG_TIME, 1000);
        this.log.logConfig("LURL     preloadTime = " + ramLURL_time);
        this.log.logConfig("NURL     preloadTime = " + ramNURL_time);
        this.log.logConfig("EURL     preloadTime = " + ramEURL_time);
        this.log.logConfig("RWI      preloadTime = " + ramRWI_time);
        this.log.logConfig("HTTP     preloadTime = " + ramHTTP_time);
        this.log.logConfig("Message  preloadTime = " + ramMessage_time);
        this.log.logConfig("Wiki     preloadTime = " + ramWiki_time);
        this.log.logConfig("Blog     preloadTime = " + ramBlog_time);
        this.log.logConfig("Robots   preloadTime = " + ramRobots_time);
        this.log.logConfig("Profiles preloadTime = " + ramProfiles_time);
        this.log.logConfig("PreNURL  preloadTime = " + ramPreNURL_time);
        
        // make crawl profiles database and default profiles
        this.log.logConfig("Initializing Crawl Profiles");
        File profilesFile = new File(this.plasmaPath, DBFILE_CRAWL_PROFILES);
        this.profiles = new plasmaCrawlProfile(profilesFile, ramProfiles_time);
        initProfiles();
        log.logConfig("Loaded profiles from file " + profilesFile.getName() +
        ", " + this.profiles.size() + " entries" +
        ", " + ppRamString(profilesFile.length()/1024));
        
        // loading the robots.txt db
        this.log.logConfig("Initializing robots.txt DB");
        File robotsDBFile = new File(this.plasmaPath, DBFILE_CRAWL_ROBOTS);
        robots = new plasmaCrawlRobotsTxt(robotsDBFile, ramRobots_time);
        this.log.logConfig("Loaded robots.txt DB from file " + robotsDBFile.getName() +
        ", " + robots.size() + " entries" +
        ", " + ppRamString(robotsDBFile.length()/1024));
        
        // start a cache manager
        log.logConfig("Starting HT Cache Manager");
        
        // create the cache directory
        String cache = getConfig(HTCACHE_PATH, HTCACHE_PATH_DEFAULT);
        cache = cache.replace('\\', '/');
        if (cache.endsWith("/")) { cache = cache.substring(0, cache.length() - 1); }
        if (new File(cache).isAbsolute()) {
            htCachePath = new File(cache); // don't use rootPath
        } else {
            htCachePath = new File(rootPath, cache);
        }
        this.log.logInfo("HTCACHE Path = " + htCachePath.getAbsolutePath());
        long maxCacheSize = 1024 * 1024 * Long.parseLong(getConfig(PROXY_CACHE_SIZE, "2")); // this is megabyte
        String cacheLayout = getConfig(PROXY_CACHE_LAYOUT, PROXY_CACHE_LAYOUT_TREE);
        boolean cacheMigration = getConfigBool(PROXY_CACHE_MIGRATION, true);
        this.cacheManager = new plasmaHTCache(htCachePath, maxCacheSize, ramHTTP_time, cacheLayout, cacheMigration);
        
        // starting message board
        initMessages(ramMessage_time);
        
        // starting wiki
        initWiki(ramWiki_time);
        
        //starting blog
        initBlog(ramBlog_time);
        
        // Init User DB
        this.log.logConfig("Loading User DB");
        File userDbFile = new File(getRootPath(), DBFILE_USER);
        this.userDB = new userDB(userDbFile, 2000);
        this.log.logConfig("Loaded User DB from file " + userDbFile.getName() +
        ", " + this.userDB.size() + " entries" +
        ", " + ppRamString(userDbFile.length()/1024));
        
        //Init bookmarks DB
        initBookmarks();
        
        // start indexing management
        log.logConfig("Starting Indexing Management");
        noticeURL = new plasmaCrawlNURL(plasmaPath);
        errorURL = new plasmaCrawlZURL(plasmaPath, "urlError.db");
        delegatedURL = new plasmaCrawlZURL(plasmaPath, "urlDelegated.db");
        wordIndex = new plasmaWordIndex(indexPrimaryPath, indexSecondaryPath, ramRWI_time, log);
        
        // set a high maximum cache size to current size; this is adopted later automatically
        int wordCacheMaxCount = Math.max((int) getConfigLong(WORDCACHE_INIT_COUNT, 30000),
                                         (int) getConfigLong(WORDCACHE_MAX_COUNT, 20000));
        setConfig(WORDCACHE_MAX_COUNT, Integer.toString(wordCacheMaxCount));
        wordIndex.setMaxWordCount(wordCacheMaxCount); 

        int wordInCacheMaxCount = (int) getConfigLong(INDEX_DIST_DHT_RECEIPT_LIMIT, 1000);
        wordIndex.setInMaxWordCount(wordInCacheMaxCount);
        wordIndex.setWordFlushSize((int) getConfigLong("wordFlushSize", 1000));
        
        // set a minimum amount of memory for the indexer thread
        long memprereq = wordIndex.minMem();
        //setConfig(INDEXER_MEMPREREQ, memprereq);
        kelondroRecords.setCacheGrowStati(memprereq + 2 * 1024 * 1024, memprereq);
        kelondroCache.setCacheGrowStati(memprereq + 2 * 1024 * 1024, memprereq);
        
        // make parser
        log.logConfig("Starting Parser");
        this.parser = new plasmaParser();
        
        /* ======================================================================
         * initialize switchboard queue
         * ====================================================================== */
        // create queue
        this.sbQueue = new plasmaSwitchboardQueue(this.cacheManager, this.wordIndex.loadedURL, new File(this.plasmaPath, "switchboardQueue1.stack"), this.profiles);
        
        // setting the indexing queue slots
        indexingSlots = (int) getConfigLong(INDEXER_SLOTS, 30);
        
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
        plasmaParser.initMediaExt(plasmaParser.extString2extList(getConfig(PARSER_MEDIA_EXT,"")));
        plasmaParser.initSupportedRealtimeFileExt(plasmaParser.extString2extList(getConfig(PARSER_MEDIA_EXT_PARSEABLE,"")));
        
        // define a realtime parsable mimetype list
        log.logConfig("Parser: Initializing Mime Types");
        plasmaParser.initRealtimeParsableMimeTypes(getConfig(PARSER_MIMETYPES_REALTIME,"application/xhtml+xml,text/html,text/plain"));
        plasmaParser.initParseableMimeTypes(plasmaParser.PARSER_MODE_PROXY,getConfig(PARSER_MIMETYPES_PROXY,null));
        plasmaParser.initParseableMimeTypes(plasmaParser.PARSER_MODE_CRAWLER,getConfig(PARSER_MIMETYPES_CRAWLER,null));
        plasmaParser.initParseableMimeTypes(plasmaParser.PARSER_MODE_ICAP,getConfig(PARSER_MIMETYPES_ICAP,null));
        plasmaParser.initParseableMimeTypes(plasmaParser.PARSER_MODE_URLREDIRECTOR,getConfig(PARSER_MIMETYPES_URLREDIRECTOR,null));
        
        // start a loader
        log.logConfig("Starting Crawl Loader");
        crawlSlots = Integer.parseInt(getConfig(CRAWLER_THREADS_ACTIVE_MAX, "10"));
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
        
        // init cookie-Monitor
        this.log.logConfig("Starting Cookie Monitor");
        this.outgoingCookies = new HashMap();
        this.incomingCookies = new HashMap();
        
        // init search history trackers
        this.localSearchTracker = new HashMap(); // String:TreeSet - IP:set of Long(accessTime)
        this.remoteSearchTracker = new HashMap();
        this.localSearches = new ArrayList(); // contains search result properties as HashMaps
        this.remoteSearches = new ArrayList();
        
        // init messages: clean up message symbol
        File notifierSource = new File(getRootPath(), getConfig(HTROOT_PATH, HTROOT_PATH_DEFAULT) + "/env/grafics/empty.gif");
        File notifierDest = new File(getConfig(HTDOCS_PATH, HTDOCS_PATH_DEFAULT), "notifier.gif");
        try {
            serverFileUtils.copy(notifierSource, notifierDest);
        } catch (IOException e) {
        }
        
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
        rankingOn = getConfig(RANKING_DIST_ON, "true").equals("true");
        rankingOwnDistribution = new plasmaRankingDistribution(log, new File(rankingPath, getConfig(RANKING_DIST_0_PATH, plasmaRankingDistribution.CR_OWN)), (int) getConfigLong(RANKING_DIST_0_METHOD, plasmaRankingDistribution.METHOD_ANYSENIOR), (int) getConfigLong(RANKING_DIST_0_METHOD, 0), getConfig(RANKING_DIST_0_TARGET, ""));
        rankingOtherDistribution = new plasmaRankingDistribution(log, new File(rankingPath, getConfig(RANKING_DIST_1_PATH, plasmaRankingDistribution.CR_OTHER)), (int) getConfigLong(RANKING_DIST_1_METHOD, plasmaRankingDistribution.METHOD_MIXEDSENIOR), (int) getConfigLong(RANKING_DIST_1_METHOD, 30), getConfig(RANKING_DIST_1_TARGET, "kaskelix.de:8080,yacy.dyndns.org:8000,suma-lab.de:8080"));
        
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
        httpc.yacyDebugMode = getConfig(YACY_MODE_DEBUG, "false").equals("true");
        
        // init nameCacheNoCachingList
        String noCachingList = getConfig(HTTPC_NAME_CACHE_CACHING_PATTERNS_NO,"");
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
        this.sbStackCrawlThread = new plasmaCrawlStacker(this, this.plasmaPath, ramPreNURL_time, (int) getConfigLong("tableTypeForPreNURL", 0));
        //this.sbStackCrawlThread = new plasmaStackCrawlThread(this,this.plasmaPath,ramPreNURL);
        //this.sbStackCrawlThread.start();
        
        // initializing dht chunk generation
        this.dhtTransferChunk = null;
        this.dhtTransferIndexCount = (int) getConfigLong(INDEX_DIST_CHUNK_SIZE_START, 50);
        
        // deploy threads
        log.logConfig("Starting Threads");
        // System.gc(); // help for profiler
        int indexing_cluster = Integer.parseInt(getConfig(INDEXER_CLUSTER, "1"));
        if (indexing_cluster < 1) indexing_cluster = 1;
        deployThread(CLEANUP, "Cleanup", "simple cleaning process for monitoring information", null,
        new serverInstantThread(this, CLEANUP_METHOD_START, CLEANUP_METHOD_JOBCOUNT, CLEANUP_METHOD_FREEMEM), 10000); // all 5 Minutes
        deployThread(CRAWLSTACK, "Crawl URL Stacker", "process that checks url for double-occurrences and for allowance/disallowance by robots.txt", null,
        new serverInstantThread(sbStackCrawlThread, CRAWLSTACK_METHOD_START, CRAWLSTACK_METHOD_JOBCOUNT, CRAWLSTACK_METHOD_FREEMEM), 8000);

        deployThread(INDEXER, "Parsing/Indexing", "thread that performes document parsing and indexing", "/IndexCreateIndexingQueue_p.html",
        new serverInstantThread(this, INDEXER_METHOD_START, INDEXER_METHOD_JOBCOUNT, INDEXER_METHOD_FREEMEM), 10000);
        for (int i = 1; i < indexing_cluster; i++) {
            setConfig((i + 80) + "_indexing_idlesleep", getConfig(INDEXER_IDLESLEEP, ""));
            setConfig((i + 80) + "_indexing_busysleep", getConfig(INDEXER_BUSYSLEEP, ""));
            deployThread((i + 80) + "_indexing", "Parsing/Indexing (cluster job)", "thread that performes document parsing and indexing", null,
            new serverInstantThread(this, INDEXER_METHOD_START, INDEXER_METHOD_JOBCOUNT, INDEXER_METHOD_FREEMEM), 10000 + (i * 1000),
            Long.parseLong(getConfig(INDEXER_IDLESLEEP , "5000")),
            Long.parseLong(getConfig(INDEXER_BUSYSLEEP , "0")),
            Long.parseLong(getConfig(INDEXER_MEMPREREQ , "1000000")));
        }

        deployThread(PROXY_CACHE_ENQUEUE, "Proxy Cache Enqueue", "job takes new proxy files from RAM stack, stores them, and hands over to the Indexing Stack", null,
        new serverInstantThread(this, PROXY_CACHE_ENQUEUE_METHOD_START, PROXY_CACHE_ENQUEUE_METHOD_JOBCOUNT, PROXY_CACHE_ENQUEUE_METHOD_FREEMEM), 10000);
        deployThread(CRAWLJOB_REMOTE_TRIGGERED_CRAWL, "Remote Crawl Job", "thread that performes a single crawl/indexing step triggered by a remote peer", null,
        new serverInstantThread(this, CRAWLJOB_REMOTE_TRIGGERED_CRAWL_METHOD_START, CRAWLJOB_REMOTE_TRIGGERED_CRAWL_METHOD_JOBCOUNT, CRAWLJOB_REMOTE_TRIGGERED_CRAWL_METHOD_FREEMEM), 30000);
        deployThread(CRAWLJOB_GLOBAL_CRAWL_TRIGGER, "Global Crawl Trigger", "thread that triggeres remote peers for crawling", "/IndexCreateWWWGlobalQueue_p.html",
        new serverInstantThread(this, CRAWLJOB_GLOBAL_CRAWL_TRIGGER_METHOD_START, CRAWLJOB_GLOBAL_CRAWL_TRIGGER_METHOD_JOBCOUNT, CRAWLJOB_GLOBAL_CRAWL_TRIGGER_METHOD_FREEMEM), 30000); // error here?
        deployThread(CRAWLJOB_LOCAL_CRAWL, "Local Crawl", "thread that performes a single crawl step from the local crawl queue", "/IndexCreateWWWLocalQueue_p.html",
        new serverInstantThread(this, CRAWLJOB_LOCAL_CRAWL_METHOD_START, CRAWLJOB_LOCAL_CRAWL_METHOD_JOBCOUNT, CRAWLJOB_LOCAL_CRAWL_METHOD_FREEMEM), 10000);
        deployThread(SEED_UPLOAD, "Seed-List Upload", "task that a principal peer performes to generate and upload a seed-list to a ftp account", null,
        new serverInstantThread(yc, SEED_UPLOAD_METHOD_START, SEED_UPLOAD_METHOD_JOBCOUNT, SEED_UPLOAD_METHOD_FREEMEM), 180000);
        serverInstantThread peerPing = null;
        deployThread(PEER_PING, "YaCy Core", "this is the p2p-control and peer-ping task", null,
        peerPing = new serverInstantThread(yc, PEER_PING_METHOD_START, PEER_PING_METHOD_JOBCOUNT, PEER_PING_METHOD_FREEMEM), 2000);
        peerPing.setSyncObject(new Object());
        
        deployThread(INDEX_DIST, "DHT Distribution", "selection, transfer and deletion of index entries that are not searched on your peer, but on others", null,
            new serverInstantThread(this, INDEX_DIST_METHOD_START, INDEX_DIST_METHOD_JOBCOUNT, INDEX_DIST_METHOD_FREEMEM), 60000,
            Long.parseLong(getConfig(INDEX_DIST_IDLESLEEP , "5000")),
            Long.parseLong(getConfig(INDEX_DIST_BUSYSLEEP , "0")),
            Long.parseLong(getConfig(INDEX_DIST_MEMPREREQ , "1000000")));

        // test routine for snippet fetch
        //Set query = new HashSet();
        //query.add(plasmaWordIndexEntry.word2hash("Weitergabe"));
        //query.add(plasmaWordIndexEntry.word2hash("Zahl"));
        //plasmaSnippetCache.result scr = snippetCache.retrieve(new URL("http://www.heise.de/mobil/newsticker/meldung/mail/54980"), query, true);
        //plasmaSnippetCache.result scr = snippetCache.retrieve(new URL("http://www.heise.de/security/news/foren/go.shtml?read=1&msg_id=7301419&forum_id=72721"), query, true);
        //plasmaSnippetCache.result scr = snippetCache.retrieve(new URL("http://www.heise.de/kiosk/archiv/ct/2003/4/20"), query, true, 260);

        this.dbImportManager = new dbImportManager(this);
        
        // init robinson cluster
        this.clusterhashes = yacyCore.seedDB.clusterHashes(getConfig("cluster.peers.yacydomain", ""));
        
        log.logConfig("Finished Switchboard Initialization");
    }


    public void initMessages(long ramMessage_time) {
        this.log.logConfig("Starting Message Board");
        File messageDbFile = new File(workPath, DBFILE_MESSAGE);
        this.messageDB = new messageBoard(messageDbFile, ramMessage_time);
        this.log.logConfig("Loaded Message Board DB from file " + messageDbFile.getName() +
        ", " + this.messageDB.size() + " entries" +
        ", " + ppRamString(messageDbFile.length()/1024));
    }


    public void initWiki(long ramWiki_time) {
        this.log.logConfig("Starting Wiki Board");
        File wikiDbFile = new File(workPath, DBFILE_WIKI);
        this.wikiDB = new wikiBoard(wikiDbFile, new File(workPath, DBFILE_WIKI_BKP), ramWiki_time);
        this.log.logConfig("Loaded Wiki Board DB from file " + wikiDbFile.getName() +
        ", " + this.wikiDB.size() + " entries" +
        ", " + ppRamString(wikiDbFile.length()/1024));
    }
    public void initBlog(long ramBlog_time) {
        this.log.logConfig("Starting Blog");
        File blogDbFile = new File(workPath, DBFILE_BLOG);
        this.blogDB = new blogBoard(blogDbFile, ramBlog_time);
        this.log.logConfig("Loaded Blog DB from file " + blogDbFile.getName() +
        ", " + this.blogDB.size() + " entries" +
        ", " + ppRamString(blogDbFile.length()/1024));

        File blogCommentDbFile = new File(workPath, DBFILE_BLOGCOMMENTS);
        this.blogCommentDB = new blogBoardComments(blogCommentDbFile, ramBlog_time);
        this.log.logConfig("Loaded Blog-Comment DB from file " + blogCommentDbFile.getName() +
        ", " + this.blogCommentDB.size() + " entries" +
        ", " + ppRamString(blogCommentDbFile.length()/1024));
    }
    public void initBookmarks(){
        this.log.logConfig("Loading Bookmarks DB");
        File bookmarksFile = new File(workPath, DBFILE_BOOKMARKS);
        File tagsFile = new File(workPath, DBFILE_BOOKMARKS_TAGS);
        File datesFile = new File(workPath, DBFILE_BOOKMARKS_DATES);
        this.bookmarksDB = new bookmarksDB(bookmarksFile, tagsFile, datesFile, 2000);
        this.log.logConfig("Loaded Bookmarks DB from files "+ bookmarksFile.getName()+ ", "+tagsFile.getName());
        this.log.logConfig(this.bookmarksDB.tagsSize()+" Tag, "+this.bookmarksDB.bookmarksSize()+" Bookmarks");
    }
    
    
    public static plasmaSwitchboard getSwitchboard(){
        return sb;
    }

    public boolean isRobinsonMode() {
    	// we are in robinson mode, if we do not exchange index by dht distribution
    	// we need to take care that search requests and remote indexing requests go only
    	// to the peers in the same cluster, if we run a robinson cluster.
    	return !getConfigBool(plasmaSwitchboard.INDEX_DIST_ALLOW, false) && !getConfigBool(plasmaSwitchboard.INDEX_RECEIVE_ALLOW, false);
    }

    public boolean isPublicRobinson() {
    	// robinson peers may be member of robinson clusters, which can be public or private
    	// this does not check the robinson attribute, only the specific subtype of the cluster
    	String clustermode = getConfig("cluster.mode", "publicpeer");
    	return (clustermode.equals("publiccluster")) || (clustermode.equals("publicepeer"));
    }
    
    public boolean isInMyCluster(String peer) {
    	// check if the given peer is in the own network, if this is a robinson cluster
    	// depending on the robinson cluster type, the peer String may be a peerhash (b64-hash)
    	// or a ip:port String or simply a ip String
    	// if this robinson mode does not define a cluster membership, false is returned
    	if (!isRobinsonMode()) return false;
    	String clustermode = getConfig("cluster.mode", "publicpeer");
    	if (clustermode.equals("privatecluster")) {
    		// check if we got the request from a peer in the private cluster
    		String network = getConfig("cluster.peers.ipport", "");
            return network.indexOf(peer) >= 0;
    	} else if (clustermode.equals("publiccluster")) {
    		// check if we got the request from a peer in the public cluster
            return this.clusterhashes.containsKey(peer);
    	} else {
    		return false;
    	}
    }
    
    public boolean isInMyCluster(yacySeed seed) {
    	// check if the given peer is in the own network, if this is a robinson cluster
    	// if this robinson mode does not define a cluster membership, false is returned
    	if (seed == null) return false;
		if (!isRobinsonMode()) return false;
    	String clustermode = getConfig("cluster.mode", "publicpeer");
    	if (clustermode.equals("privatecluster")) {
    		// check if we got the request from a peer in the private cluster
    		String network = getConfig("cluster.peers.ipport", "");
            return network.indexOf(seed.getPublicAddress()) >= 0;
    	} else if (clustermode.equals("publiccluster")) {
    	    // check if we got the request from a peer in the public cluster
            return this.clusterhashes.containsKey(seed.hash);
    	} else {
    		return false;
    	}
    }
    
    public String urlExists(String hash) {
        // tests if hash occurrs in any database
        // if it exists, the name of the database is returned,
        // if it not exists, null is returned
        if (wordIndex.loadedURL.exists(hash)) return "loaded";
        if (noticeURL.existsInStack(hash)) return "crawler";
        if (delegatedURL.exists(hash)) return "delegated";
        if (errorURL.exists(hash)) return "errors";
        return null;
    }
    
    public URL getURL(String urlhash) throws IOException {
        if (urlhash.equals(plasmaURL.dummyHash)) return null;
        plasmaCrawlEntry ne = noticeURL.get(urlhash);
        if (ne != null) return ne.url();
        indexURLEntry le = wordIndex.loadedURL.load(urlhash, null);
        if (le != null) return le.comp().url();
        plasmaCrawlZURL.Entry ee = delegatedURL.getEntry(urlhash);
        if (ee != null) return ee.url();
        ee = errorURL.getEntry(urlhash);
        if (ee != null) return ee.url();
        return null;
    }
    
    /**
     * This method changes the HTCache size.<br>
     * @param newCacheSize in MB
     */
    public final void setCacheSize(long newCacheSize) {
        this.cacheManager.setCacheSize(1048576 * newCacheSize);
    }
    
    public boolean onlineCaution() {
        try {
            return System.currentTimeMillis() - proxyLastAccess < Integer.parseInt(getConfig(PROXY_ONLINE_CAUTION_DELAY, "30000"));
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
        this.defaultProxyProfile = null;
        this.defaultRemoteProfile = null;
        this.defaultTextSnippetProfile = null;
        this.defaultMediaSnippetProfile = null;
        Iterator i = this.profiles.profiles(true);
        plasmaCrawlProfile.entry profile;
        String name;
        while (i.hasNext()) {
            profile = (plasmaCrawlProfile.entry) i.next();
            name = profile.name();
            if (name.equals(CRAWL_PROFILE_PROXY)) this.defaultProxyProfile = profile;
            if (name.equals(CRAWL_PROFILE_REMOTE)) this.defaultRemoteProfile = profile;
            if (name.equals(CRAWL_PROFILE_SNIPPET_TEXT)) this.defaultTextSnippetProfile = profile;
            if (name.equals(CRAWL_PROFILE_SNIPPET_MEDIA)) this.defaultMediaSnippetProfile = profile;
        }
        if (this.defaultProxyProfile == null) {
            // generate new default entry for proxy crawling
            this.defaultProxyProfile = this.profiles.newEntry("proxy", "", ".*", ".*",
                    Integer.parseInt(getConfig("proxyPrefetchDepth", "0")),
                    Integer.parseInt(getConfig("proxyPrefetchDepth", "0")),
                    60 * 24, -1, -1, false,
                    getConfigBool("proxyIndexingLocalText", true),
                    getConfigBool("proxyIndexingLocalMedia", true),
                    true, true,
                    getConfigBool("proxyIndexingRemote", false), true, true, true);
        }
        if (this.defaultRemoteProfile == null) {
            // generate new default entry for remote crawling
            defaultRemoteProfile = this.profiles.newEntry(CRAWL_PROFILE_REMOTE, "", ".*", ".*", 0, 0,
                    -1, -1, -1, true, true, true, false, true, false, true, true, false);
        }
        if (this.defaultTextSnippetProfile == null) {
            // generate new default entry for snippet fetch and optional crawling
            defaultTextSnippetProfile = this.profiles.newEntry(CRAWL_PROFILE_SNIPPET_TEXT, "", ".*", ".*", 0, 0,
                    60 * 24 * 30, -1, -1, true, true, true, true, true, false, true, true, false);
        }
        if (this.defaultMediaSnippetProfile == null) {
            // generate new default entry for snippet fetch and optional crawling
            defaultMediaSnippetProfile = this.profiles.newEntry(CRAWL_PROFILE_SNIPPET_MEDIA, "", ".*", ".*", 0, 0,
                    60 * 24 * 30, -1, -1, true, false, true, true, true, false, true, true, false);
        }
    }

    private void resetProfiles() {
        final File pdb = new File(plasmaPath, DBFILE_CRAWL_PROFILES);
        if (pdb.exists()) pdb.delete();
        long ramProfiles_time = getConfigLong(RAM_CACHE_PROFILES_TIME, 1000);
        profiles = new plasmaCrawlProfile(pdb, ramProfiles_time);
        initProfiles();
    }
    
    public boolean cleanProfiles() throws InterruptedException {
        if ((sbQueue.size() > 0) || (cacheLoader.size() > 0) || (noticeURL.stackSize() > 0)) return false;
        final Iterator iter = profiles.profiles(true);
        plasmaCrawlProfile.entry entry;
        boolean hasDoneSomething = false;
        try {
            while (iter.hasNext()) {
                // check for interruption
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Shutdown in progress");
                
                // getting next profile
                entry = (plasmaCrawlProfile.entry) iter.next();
                if (!((entry.name().equals(CRAWL_PROFILE_PROXY))  ||
                      (entry.name().equals(CRAWL_PROFILE_REMOTE)) ||
                      (entry.name().equals(CRAWL_PROFILE_SNIPPET_TEXT)) ||
                      (entry.name().equals(CRAWL_PROFILE_SNIPPET_MEDIA)))) {
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
            if (this.log.isFine())
                this.log.logFine("Crawling of " + entry.url() + " prohibited by request.");
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
                    plasmaURL.urlHash(entry.referrerURL()),
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
        blogCommentDB.close();
        userDB.close();
        bookmarksDB.close();
        messageDB.close();
        if (facilityDB != null) facilityDB.close();
        sbStackCrawlThread.close();
        profiles.close();
        robots.close();
        parser.close();
        cacheManager.close();
        sbQueue.close();
        flushCitationReference(crg, "crg");
        log.logConfig("SWITCHBOARD SHUTDOWN STEP 3: sending termination signal to database manager (stand by...)");
        noticeURL.close();
        delegatedURL.close();
        errorURL.close();
        wordIndex.close();
        yc.close();
        // signal shudown to the updater
        if (updaterCallback != null) updaterCallback.signalYaCyShutdown();
        log.logConfig("SWITCHBOARD SHUTDOWN TERMINATED");
    }
    
    public int queueSize() {
        return sbQueue.size();
        //return processStack.size() + cacheLoader.size() + noticeURL.stackSize();
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
        wordIndex.flushCacheSome();
        // adopt maximum cache size to current size to prevent that further OutOfMemoryErrors occur
        int newMaxCount = Math.max(2000, Math.min((int) getConfigLong(WORDCACHE_MAX_COUNT, 20000), wordIndex.dhtOutCacheSize()));
        setConfig(WORDCACHE_MAX_COUNT, Integer.toString(newMaxCount));
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
            if (sbQueue.size() == 0) wordIndex.flushCacheSome(); // permanent flushing only if we are not busy
            wordIndex.loadedURL.flushCacheSome();

            boolean doneSomething = false;

            // possibly delete entries from last chunk
            if ((this.dhtTransferChunk != null) &&
                    (this.dhtTransferChunk.getStatus() == plasmaDHTChunk.chunkStatus_COMPLETE)) {
                String deletedURLs = this.dhtTransferChunk.deleteTransferIndexes();
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
                int minChunkSize = (int) getConfigLong(INDEX_DIST_CHUNK_SIZE_MIN, 30);
                dhtTransferChunk = new plasmaDHTChunk(this.log, wordIndex, minChunkSize, dhtTransferIndexCount, 5000);
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
                        ", coreStackSize=" + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) +
                        ", limitStackSize=" + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT) +
                        ", overhangStackSize=" + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_OVERHANG) +
                        ", remoteStackSize=" + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE));
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
        if ((delegatedURL.stackSize() > 1000)) c++;
        if ((errorURL.stackSize() > 1000)) c++;
        for (int i = 1; i <= 6; i++) {
            if (wordIndex.loadedURL.getStackSize(i) > 1000) c++;
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

            // clean up delegated stack
            checkInterruption();
            if ((delegatedURL.stackSize() > 1000)) {
                log.logFine("Cleaning Delegated-URLs report stack, " + delegatedURL.stackSize() + " entries on stack");
                delegatedURL.clearStack();
                hasDoneSomething = true;
            }
            
            // clean up error stack
            checkInterruption();
            if ((errorURL.stackSize() > 1000)) {
                log.logFine("Cleaning Error-URLs report stack, " + errorURL.stackSize() + " entries on stack");
                errorURL.clearStack();
                hasDoneSomething = true;
            }
            
            // clean up loadedURL stack
            for (int i = 1; i <= 6; i++) {
                checkInterruption();
                if (wordIndex.loadedURL.getStackSize(i) > 1000) {
                    log.logFine("Cleaning Loaded-URLs report stack, " + wordIndex.loadedURL.getStackSize(i) + " entries on stack " + i);
                    wordIndex.loadedURL.clearStack(i);
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

            // set new memory limit for indexer thread
            long memprereq = Math.max(getConfigLong(INDEXER_MEMPREREQ, 0), wordIndex.minMem());
            //setConfig(INDEXER_MEMPREREQ, memprereq);
            //setThreadPerformance(INDEXER, getConfigLong(INDEXER_IDLESLEEP, 0), getConfigLong(INDEXER_BUSYSLEEP, 0), memprereq);
            kelondroRecords.setCacheGrowStati(memprereq + 2 * 1024 * 1024, memprereq);
            kelondroCache.setCacheGrowStati(memprereq + 2 * 1024 * 1024, memprereq);
            
            // update the cluster set
            this.clusterhashes = yacyCore.seedDB.clusterHashes(getConfig("cluster.peers.yacydomain", ""));
            
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
        return new File(getRootPath(), getConfig(OWN_SEED_FILE, DBFILE_OWN_SEED));
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
        return noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE);
    }
    
    public boolean coreCrawlJob() {
        if (noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) == 0) {
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
        plasmaCrawlEntry urlEntry = null;
        while (urlEntry == null && noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) > 0) {
            String stats = "LOCALCRAWL[" + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_OVERHANG) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE) + "]";
            try {
                urlEntry = noticeURL.pop(plasmaCrawlNURL.STACK_TYPE_CORE);
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
                log.logSevere(stats + ": CANNOT FETCH ENTRY: " + e.getMessage(), e);
                if (e.getMessage().indexOf("hash is null") > 0) noticeURL.clear(plasmaCrawlNURL.STACK_TYPE_CORE);
            }
        }
        return true;
    }
    
    public int limitCrawlTriggerJobSize() {
        return noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT);
    }
    
    public boolean limitCrawlTriggerJob() {
        if (noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT) == 0) {
            //log.logDebug("LimitCrawl: queue is empty");
            return false;
        }
        boolean robinsonPrivateCase = ((isRobinsonMode()) && 
            	(!getConfig("cluster.mode", "").equals("publiccluster")) &&
            	(!getConfig("cluster.mode", "").equals("privatecluster")));
        
        if ((robinsonPrivateCase) || ((coreCrawlJobSize() <= 20) && (limitCrawlTriggerJobSize() > 10))) {
            // it is not efficient if the core crawl job is empty and we have too much to do
            // move some tasks to the core crawl job
            int toshift = 10; // this cannot be a big number because the balancer makes a forced waiting if it cannot balance
            if (toshift > limitCrawlTriggerJobSize()) toshift = limitCrawlTriggerJobSize();
            for (int i = 0; i < toshift; i++) {
                noticeURL.shift(plasmaCrawlNURL.STACK_TYPE_LIMIT, plasmaCrawlNURL.STACK_TYPE_CORE);
            }
            log.logInfo("shifted " + toshift + " jobs from global crawl to local crawl (coreCrawlJobSize()=" + coreCrawlJobSize() + ", limitCrawlTriggerJobSize()=" + limitCrawlTriggerJobSize() + ", cluster.mode=" + getConfig("cluster.mode", "") + ", robinsonMode=" + ((isRobinsonMode()) ? "on" : "off"));
            if (robinsonPrivateCase) return false;
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
        String stats = "REMOTECRAWLTRIGGER[" + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_OVERHANG) + ", "
                        + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE) + "]";
        try {
            plasmaCrawlEntry urlEntry = noticeURL.pop(plasmaCrawlNURL.STACK_TYPE_LIMIT);
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

            boolean tryRemote = ((noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) != 0) || (sbQueue.size() != 0)) &&
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
            log.logSevere(stats + ": CANNOT FETCH ENTRY: " + e.getMessage(), e);
            if (e.getMessage().indexOf("hash is null") > 0) noticeURL.clear(plasmaCrawlNURL.STACK_TYPE_LIMIT);
            return true; // if we return a false here we will block everything
        }
    }
    
    public int remoteTriggeredCrawlJobSize() {
        return noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE);
    }
    
    public boolean remoteTriggeredCrawlJob() {
        // work off crawl requests that had been placed by other peers to our crawl stack
        
        // do nothing if either there are private processes to be done
        // or there is no global crawl on the stack
        if (noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE) == 0) {
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
        String stats = "REMOTETRIGGEREDCRAWL[" + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_OVERHANG) + ", "
                        + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE) + "]";
        try {
            plasmaCrawlEntry urlEntry = noticeURL.pop(plasmaCrawlNURL.STACK_TYPE_REMOTE);
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
            log.logSevere(stats + ": CANNOT FETCH ENTRY: " + e.getMessage(), e);
            if (e.getMessage().indexOf("hash is null") > 0) noticeURL.clear(plasmaCrawlNURL.STACK_TYPE_REMOTE);
            return true;
        }
    }
    
    private plasmaParserDocument parseResource(plasmaSwitchboardQueue.Entry entry, String initiatorHash) throws InterruptedException, ParserException {
        
        // the mimetype of this entry
        String mimeType = entry.getMimeType();
        String charset = entry.getCharacterEncoding();        

        // the parser logger
        //serverLog parserLogger = parser.getLogger();

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
            String initiatorPeerHash = (entry.proxy()) ? plasmaURL.dummyHash : entry.initiator();
            if (initiatorPeerHash.equals(plasmaURL.dummyHash)) {
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
                addURLtoErrorDB(entry.url(), entry.referrerHash(), initiatorPeerHash, entry.anchorName(), e.getErrorCode(), new kelondroBitfield());
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
                URL nextUrl;
                Map.Entry nextEntry;
                while (i.hasNext()) {
                    // check for interruption
                    checkInterruption();
                    
                    // fetching the next hyperlink
                    nextEntry = (Map.Entry) i.next();
                    nextUrlString = (String) nextEntry.getKey();
                    try {                        
                        nextUrl = new URL(nextUrlString);
                        
                        // enqueue the hyperlink into the pre-notice-url db
                        sbStackCrawlThread.enqueue(nextUrl, entry.urlHash(), initiatorPeerHash, (String) nextEntry.getValue(), docDate, entry.depth() + 1, entry.profile());                        
                    } catch (MalformedURLException e1) {}                    
                }
                log.logInfo("CRAWL: ADDED " + hl.size() + " LINKS FROM " + entry.normalizedURLString() +
                        ", NEW CRAWL STACK SIZE IS " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE));
            }
            stackEndTime = System.currentTimeMillis();
            
            /* =========================================================================
             * CREATE INDEX
             * ========================================================================= */  
            String docDescription = document.getTitle();
            URL referrerURL = entry.referrerURL();
            String referrerUrlHash = plasmaURL.urlHash(referrerURL);
            if (referrerUrlHash == null) referrerUrlHash = plasmaURL.dummyHash;

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
                plasmaCondenser condenser = new plasmaCondenser(document, entry.profile().indexText(), entry.profile().indexMedia());
                
                // generate citation reference
                Integer[] ioLinks = generateCitationReference(entry.urlHash(), docDate, document, condenser); // [outlinksSame, outlinksOther]
                
                try {        
                    // check for interruption
                    checkInterruption();
                    
                    // create a new loaded URL db entry
                    long ldate = System.currentTimeMillis();
                    indexURLEntry newEntry = wordIndex.loadedURL.newEntry(
                            entry.url(),                               // URL
                            docDescription,                            // document description
                            document.getAuthor(),                      // author
                            document.getKeywords(' '),                 // tags
                            "",                                        // ETag
                            docDate,                                   // modification date
                            new Date(),                                // loaded date
                            new Date(ldate + Math.max(0, ldate - docDate.getTime()) / 2), // freshdate, computed with Proxy-TTL formula 
                            referrerUrlHash,                           // referer hash
                            new byte[0],                               // md5
                            (int) entry.size(),                        // size
                            condenser.RESULT_NUMB_WORDS,               // word count
                            plasmaURL.docType(document.getMimeType()), // doctype
                            condenser.RESULT_FLAGS,                    // flags
                            plasmaURL.language(entry.url()),           // language
                            ioLinks[0].intValue(),                     // llocal
                            ioLinks[1].intValue(),                     // lother
                            document.getAudiolinks().size(),           // laudio
                            document.getImages().size(),               // limage
                            document.getVideolinks().size(),           // lvideo
                            document.getApplinks().size()              // lapp
                    );
                    /* ========================================================================
                     * STORE URL TO LOADED-URL-DB
                     * ======================================================================== */
                    wordIndex.loadedURL.store(newEntry);
                    wordIndex.loadedURL.stack(
                            newEntry,                       // loaded url db entry
                            initiatorPeerHash,              // initiator peer hash
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
                            ((entry.profile().indexText()) || (entry.profile().indexMedia()))
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
                                ((storagePeerHash = getConfig(STORAGE_PEER_HASH, null)) == null) ||
                                (storagePeerHash.trim().length() == 0) ||
                                ((seed = yacyCore.seedDB.getConnected(storagePeerHash)) == null)
                        ){
                            
                            /* ========================================================================
                             * STORE PAGE INDEX INTO WORD INDEX DB
                             * ======================================================================== */
                            words = wordIndex.addPageIndex(
                                    entry.url(),                                  // document url
                                    urlHash,                                      // document url hash
                                    docDate,                                      // document mod date
                                    (int) entry.size(),                           // document size
                                    document,                                     // document content
                                    condenser,                                    // document condenser
                                    plasmaURL.language(entry.url()),              // document language
                                    plasmaURL.docType(document.getMimeType()),    // document type
                                    ioLinks[0].intValue(),                        // outlinkSame
                                    ioLinks[1].intValue()                         // outlinkOthers
                            );
                        } else {
                            /* ========================================================================
                             * SEND PAGE INDEX TO STORAGE PEER
                             * ======================================================================== */                            
                            HashMap urlCache = new HashMap(1);
                            urlCache.put(newEntry.hash(),newEntry);
                            
                            ArrayList tmpContainers = new ArrayList(condenser.words().size());
                            
                            String language = plasmaURL.language(entry.url());                            
                            char doctype = plasmaURL.docType(document.getMimeType());
                            indexURLEntry.Components comp = newEntry.comp();
                            int urlLength = comp.url().toNormalform().length();
                            int urlComps = htmlFilterContentScraper.urlComps(comp.url().toNormalform()).length;

                            // iterate over all words
                            Iterator i = condenser.words().entrySet().iterator();
                            Map.Entry wentry;
                            plasmaCondenser.wordStatProp wordStat;
                            while (i.hasNext()) {
                                wentry = (Map.Entry) i.next();
                                String word = (String) wentry.getKey();
                                wordStat = (plasmaCondenser.wordStatProp) wentry.getValue();
                                String wordHash = plasmaCondenser.word2hash(word);
                                indexRWIEntry wordIdxEntry = new indexRWIEntry(
                                            urlHash,
                                            urlLength, urlComps,
                                            wordStat.count,
                                            document.getTitle().length(),
                                            condenser.words().size(),
                                            condenser.sentences().size(),
                                            wordStat.posInText,
                                            wordStat.posInPhrase,
                                            wordStat.numOfPhrase,
                                            0,
                                            newEntry.size(),
                                            docDate.getTime(),
                                            System.currentTimeMillis(),
                                            language,
                                            doctype,
                                            ioLinks[0].intValue(),
                                            ioLinks[1].intValue(),
                                            condenser.RESULT_FLAGS
                                        );
                                indexContainer wordIdxContainer = wordIndex.emptyContainer(wordHash);
                                wordIdxContainer.add(wordIdxEntry);
                                tmpContainers.add(wordIdxContainer);
                            }
                            //System.out.println("DEBUG: plasmaSearch.addPageIndex: added " + condenser.getWords().size() + " words, flushed " + c + " entries");
                            words = condenser.words().size();
                            
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
                                        plasmaURL.language(entry.url()),
                                        plasmaURL.docType(document.getMimeType()),
                                        ioLinks[0].intValue(), 
                                        ioLinks[1].intValue()
                                );
                            }
                            
                            tmpContainers = null;
                        } //end: SEND PAGE INDEX TO STORAGE PEER
                        
                        storageEndTime = System.currentTimeMillis();
                        
                        //increment number of indexed urls
                		indexedPages++;
                        
                        if (log.isInfo()) {
                            // TODO: UTF-8 docDescription seems not to be displayed correctly because
                            // of string concatenation
                            log.logInfo("*Indexed " + words + " words in URL " + entry.url() +
                                    " [" + entry.urlHash() + "]" +
                                    "\n\tDescription:  " + docDescription +
                                    "\n\tMimeType: "  + document.getMimeType() + " | Charset: " + document.getCharset() + " | " +
                                    "Size: " + document.getTextLength() + " bytes | " +
                                    "Anchors: " + ((document.getAnchors() == null) ? 0 : document.getAnchors().size()) +
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
                            if (clusterhashes != null) initiatorPeer.setAlternativeAddress((String) clusterhashes.get(initiatorPeer.hash));
                            yacyClient.crawlReceipt(initiatorPeer, "crawl", "fill", "indexed", newEntry, "");
                        }
                    } else {
                        log.logFine("Not Indexed Resource '" + entry.normalizedURLString() + "': process case=" + processCase);
                        addURLtoErrorDB(entry.url(), referrerUrlHash, initiatorPeerHash, docDescription, plasmaCrawlEURL.DENIED_UNKNOWN_INDEXING_PROCESS_CASE, new kelondroBitfield());
                    }
                } catch (Exception ee) {
                    if (ee instanceof InterruptedException) throw (InterruptedException)ee;
                    
                    // check for interruption
                    checkInterruption();
                    
                    log.logSevere("Could not index URL " + entry.url() + ": " + ee.getMessage(), ee);
                    if ((processCase == PROCESSCASE_6_GLOBAL_CRAWLING) && (initiatorPeer != null)) {
                        if (clusterhashes != null) initiatorPeer.setAlternativeAddress((String) clusterhashes.get(initiatorPeer.hash));
                        yacyClient.crawlReceipt(initiatorPeer, "crawl", "exception", ee.getMessage(), null, "");
                    }
                    addURLtoErrorDB(entry.url(), referrerUrlHash, initiatorPeerHash, docDescription, plasmaCrawlEURL.DENIED_UNSPECIFIED_INDEXING_ERROR, new kelondroBitfield());
                }
                
            } else {
                // check for interruption
                checkInterruption();
                
                log.logInfo("Not indexed any word in URL " + entry.url() + "; cause: " + noIndexReason);
                addURLtoErrorDB(entry.url(), referrerUrlHash, initiatorPeerHash, docDescription, noIndexReason, new kelondroBitfield());
                if ((processCase == PROCESSCASE_6_GLOBAL_CRAWLING) && (initiatorPeer != null)) {
                    if (clusterhashes != null) initiatorPeer.setAlternativeAddress((String) clusterhashes.get(initiatorPeer.hash));
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
            /*
            boolean removed = noticeURL.remove(entry.urlHash()); // worked-off
            if (!removed) {
                log.logFinest("Unable to remove indexed URL " + entry.url() + " from Crawler Queue. This could be because of an URL redirect.");
            }
            */
            
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
            nexturlhash = plasmaURL.urlHash((String) ((Map.Entry) it.next()).getKey());
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
        kelondroBase64Order.enhancedCoder.encodeLongSmart(condenser.words().size(), 3) + // count of all unique words
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
    
    private void processLocalCrawling(plasmaCrawlEntry urlEntry, plasmaCrawlProfile.entry profile, String stats) {
        // work off one Crawl stack entry
        if ((urlEntry == null) || (urlEntry.url() == null)) {
            log.logInfo(stats + ": urlEntry=null");
            return;
        }
        
        // convert the referrer hash into the corresponding URL
        URL refererURL = null;
        String refererHash = urlEntry.referrerhash();
        if ((refererHash != null) && (!refererHash.equals(plasmaURL.dummyHash))) try {
            refererURL = this.getURL(refererHash);
        } catch (IOException e) {
            refererURL = null;
        }
        cacheLoader.loadAsync(urlEntry.url(), urlEntry.name(), (refererURL!=null)?refererURL.toString():null, urlEntry.initiator(), urlEntry.depth(), profile);
        log.logInfo(stats + ": enqueued for load " + urlEntry.url() + " [" + urlEntry.urlhash() + "]");
        return;
    }
    
    private boolean processRemoteCrawlTrigger(plasmaCrawlEntry urlEntry) {
        // if this returns true, then the urlEntry is considered as stored somewhere and the case is finished
        // if this returns false, the urlEntry will be enqueued to the local crawl again
        
        // wrong access
        if (urlEntry == null) {
            log.logInfo("REMOTECRAWLTRIGGER[" + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE) + ", " + noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_REMOTE) + "]: urlEntry=null");
            return true; // superfluous request; true correct in this context because the urlEntry shall not be tracked any more
        }
        
        // check url
        if (urlEntry.url() == null) {
            log.logFine("ERROR: plasmaSwitchboard.processRemoteCrawlTrigger - url is null. name=" + urlEntry.name());
            return true; // same case as above: no more consideration
        }
        
        // are we qualified for a remote crawl?
        if ((yacyCore.seedDB.mySeed == null) || (yacyCore.seedDB.mySeed.isJunior())) {
            log.logFine("plasmaSwitchboard.processRemoteCrawlTrigger: no permission");
            return false; // no, we must crawl this page ourselves
        }
        
        // check if peer for remote crawl is available
        yacySeed remoteSeed = ((this.isPublicRobinson()) && (getConfig("cluster.mode", "").equals("publiccluster"))) ?
        	yacyCore.dhtAgent.getPublicClusterCrawlSeed(urlEntry.urlhash(), this.clusterhashes) :	
        	yacyCore.dhtAgent.getGlobalCrawlSeed(urlEntry.urlhash());
        if (remoteSeed == null) {
            log.logFine("plasmaSwitchboard.processRemoteCrawlTrigger: no remote crawl seed available");
            return false;
        }
        
        // do the request
        HashMap page = null;
        try {
            page = yacyClient.crawlOrder(remoteSeed, urlEntry.url(), getURL(urlEntry.referrerhash()), 6000);
        } catch (IOException e1) {
            log.logSevere(STR_REMOTECRAWLTRIGGER + remoteSeed.getName() + " FAILED. URL CANNOT BE RETRIEVED from referrer hash: " + urlEntry.referrerhash(), e1);
            return false;
        }
        
        // check if we got contact to peer and the peer respondet
        if ((page == null) || (page.get("delay") == null)) {
            log.logInfo("CRAWL: REMOTE CRAWL TO PEER " + remoteSeed.getName() + " FAILED. CAUSE: unknown (URL=" + urlEntry.url().toString() + "). Removed peer.");
            yacyCore.peerActions.peerDeparture(remoteSeed);
            return false; // no response from peer, we will crawl this ourself
        }
        
        String response = (String) page.get("response");
        log.logFine("plasmaSwitchboard.processRemoteCrawlTrigger: remoteSeed="
                + remoteSeed.getName() + ", url=" + urlEntry.url().toString()
                + ", response=" + page.toString()); // DEBUG

        // we received an answer and we are told to wait a specific time until we shall ask again for another crawl
        int newdelay = Integer.parseInt((String) page.get("delay"));
        yacyCore.dhtAgent.setCrawlDelay(remoteSeed.hash, newdelay);
        if (response.equals("stacked")) {
            // success, the remote peer accepted the crawl
            log.logInfo(STR_REMOTECRAWLTRIGGER + remoteSeed.getName()
                    + " PLACED URL=" + urlEntry.url().toString()
                    + "; NEW DELAY=" + newdelay);
            // track this remote crawl
            this.delegatedURL.newEntry(urlEntry, remoteSeed.hash, new Date(), 0, response).store();
            return true;
        }
        
        // check other cases: the remote peer may respond that it already knows that url
        if (response.equals("double")) {
            // in case the peer answers double, it transmits the complete lurl data
            String lurl = (String) page.get("lurl");
            if ((lurl != null) && (lurl.length() != 0)) {
                String propStr = crypt.simpleDecode(lurl, (String) page.get("key"));
                indexURLEntry entry = wordIndex.loadedURL.newEntry(propStr);
                try {
                    wordIndex.loadedURL.store(entry);
                    wordIndex.loadedURL.stack(entry, yacyCore.seedDB.mySeed.hash, remoteSeed.hash, 1); // *** ueberfluessig/doppelt?
                    // noticeURL.remove(entry.hash());
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                
                log.logInfo(STR_REMOTECRAWLTRIGGER + remoteSeed.getName()
                        + " SUPERFLUOUS. CAUSE: " + page.get("reason")
                        + " (URL=" + urlEntry.url().toString()
                        + "). URL IS CONSIDERED AS 'LOADED!'");
                return true;
            } else {
                log.logInfo(STR_REMOTECRAWLTRIGGER + remoteSeed.getName()
                        + " REJECTED. CAUSE: bad lurl response / " + page.get("reason") + " (URL="
                        + urlEntry.url().toString() + ")");
                remoteSeed.setFlagAcceptRemoteCrawl(false);
                yacyCore.seedDB.update(remoteSeed.hash, remoteSeed);
                return false;
            }
        }

        log.logInfo(STR_REMOTECRAWLTRIGGER + remoteSeed.getName()
                + " DENIED. RESPONSE=" + response + ", CAUSE="
                + page.get("reason") + ", URL=" + urlEntry.url().toString());
        remoteSeed.setFlagAcceptRemoteCrawl(false);
        yacyCore.seedDB.update(remoteSeed.hash, remoteSeed);
        return false;
    }
    
    private static SimpleDateFormat DateFormatter = new SimpleDateFormat("EEE, dd MMM yyyy");
    public static String dateString(Date date) {
        if (date == null) return ""; else return DateFormatter.format(date);
    }
    
    public plasmaSearchResults searchFromLocal(plasmaSearchQuery query,
                                         plasmaSearchRankingProfile ranking,
                                         plasmaSearchTimingProfile  localTiming,
                                         plasmaSearchTimingProfile  remoteTiming,
                                         boolean postsort,
                                         String client) {
        
        // tell all threads to do nothing for a specific time
        intermissionAllThreads(2 * query.maximumTime);
        
        plasmaSearchResults results=new plasmaSearchResults();
        results.setRanking(ranking);
        results.setQuery(query);
        results.setFormerSearch("");
        try {
            // filter out words that appear in bluelist
            //log.logInfo("E");
            query.filterOut(blueList);
            results.setQuery(query);
            
            // log
            log.logInfo("INIT WORD SEARCH: " + query.queryString + ":" + query.queryHashes + " - " + query.wantedResults + " links, " + (query.maximumTime / 1000) + " seconds");
            long timestamp = System.currentTimeMillis();
            
            // start a presearch, which makes only sense if we idle afterwards.
            // this is especially the case if we start a global search and idle until search
            //if (query.domType == plasmaSearchQuery.SEARCHDOM_GLOBALDHT) {
            //    Thread preselect = new presearch(query.queryHashes, order, query.maximumTime / 10, query.urlMask, 10, 3);
            //    preselect.start();
            //}
            
            // create a new search event
            plasmaSearchEvent theSearch = new plasmaSearchEvent(query, ranking, localTiming, remoteTiming, postsort, log, wordIndex, wordIndex.loadedURL, snippetCache, (isRobinsonMode()) ? this.clusterhashes : null);
            plasmaSearchPostOrder acc = theSearch.search();
            
            // fetch snippets
            //if (query.domType != plasmaSearchQuery.SEARCHDOM_GLOBALDHT) snippetCache.fetch(acc.cloneSmart(), query.queryHashes, query.urlMask, 10, 1000);
            log.logFine("SEARCH TIME AFTER ORDERING OF SEARCH RESULTS: " + ((System.currentTimeMillis() - timestamp) / 1000) + " seconds");
            
            // result is a List of urlEntry elements: prepare answer
            if (acc == null) {
                results.setTotalcount(0);
                results.setFilteredcount(0);
                results.setOrderedcount(0);
                results.setLinkcount(0);
            } else {
                results.setTotalcount(acc.globalContributions + acc.localContributions);
                results.setFilteredcount(acc.filteredResults);
                results.setOrderedcount(acc.sizeOrdered());
                results.setGlobalresults(acc.globalContributions);
                results.setRanking(ranking);
                
                int i = 0;
                int p;
                indexURLEntry urlentry;
                String urlstring, urlname, filename, urlhash;
                String host, hash, address;
                yacySeed seed;
                boolean includeSnippets = false;
                results.setFormerSearch(query.queryString());
                long targetTime = timestamp + query.maximumTime;
                if (targetTime < System.currentTimeMillis()) targetTime = System.currentTimeMillis() + 1000;
                while ((acc.hasMoreElements()) && (i < query.wantedResults) && (System.currentTimeMillis() < targetTime)) {
                    urlentry = acc.nextElement();
                    indexURLEntry.Components comp = urlentry.comp();
                    urlhash = urlentry.hash();
                    assert (urlhash != null);
                    assert (urlhash.length() == 12) : "urlhash = " + urlhash;
                    host = comp.url().getHost();
                    if (host.endsWith(".yacyh")) {
                        // translate host into current IP
                        p = host.indexOf(".");
                        hash = yacySeed.hexHash2b64Hash(host.substring(p + 1, host.length() - 6));
                        seed = yacyCore.seedDB.getConnected(hash);
                        filename = comp.url().getFile();
                        if ((seed == null) || ((address = seed.getPublicAddress()) == null)) {
                            // seed is not known from here
                            wordIndex.removeWordReferences(plasmaCondenser.getWords(("yacyshare " + filename.replace('?', ' ') + " " + comp.title()).getBytes(), "UTF-8").keySet(), urlentry.hash());
                            wordIndex.loadedURL.remove(urlentry.hash()); // clean up
                            continue; // next result
                        }
                        urlstring = "http://" + address + "/" + host.substring(0, p) + filename;
                        urlname = "http://share." + seed.getName() + ".yacy" + filename;
                        if ((p = urlname.indexOf("?")) > 0) urlname = urlname.substring(0, p);
                    } else {
                        urlstring = comp.url().toNormalform();
                        urlname = urlstring;
                    }
                    
                    
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
                    plasmaSearchResults.searchResult result=results.createSearchResult();
                    result.setUrl(urlstring);
                    result.setUrlname(urlname);
                    result.setUrlentry(urlentry);
                    if (includeSnippets) {
						result.setSnippet(snippetCache.retrieveTextSnippet(comp.url(), results.getQuery().queryHashes, false, urlentry.flags().get(plasmaCondenser.flag_cat_indexof), 260, 1000));
						// snippet = snippetCache.retrieveTextSnippet(comp.url(), query.queryHashes, false, urlentry.flags().get(plasmaCondenser.flag_cat_indexof), 260, 1000);
					} else {
						// snippet = null;
						result.setSnippet(null);
					}
					i++;
					results.appendResult(result);
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
                results.setReferences(ws);
                log.logFine("SEARCH TIME AFTER XREF PREPARATION: " + ((System.currentTimeMillis() - timestamp) / 1000) + " seconds");
                
                    /*
                    System.out.print("DEBUG WORD-SCORE: ");
                    for (int ii = 0; ii < ws.length; ii++) System.out.print(ws[ii] + ", ");
                    System.out.println(" all words = " + ref.getElementCount() + ", total count = " + ref.getTotalCount());
                     */
            }
            
            // log
            log.logInfo("EXIT WORD SEARCH: " + query.queryString + " - " +
                    results.getTotalcount() + " links found, " +
                    results.getFilteredcount() + " links filtered, " +
                    results.getOrderedcount() + " links ordered, " +
                    results.getLinkcount() + " links selected, " +
                    ((System.currentTimeMillis() - timestamp) / 1000) + " seconds");
            
            
            // prepare search statistics
            Long trackerHandle = new Long(System.currentTimeMillis());
            HashMap searchProfile = theSearch.resultProfile();
            searchProfile.put("querystring", query.queryString);
            searchProfile.put("time", trackerHandle);
            searchProfile.put("host", client);
            searchProfile.put("offset", new Integer(0));
            searchProfile.put("results", results);
            this.localSearches.add(searchProfile);
            TreeSet handles = (TreeSet) this.localSearchTracker.get(client);
            if (handles == null) handles = new TreeSet();
            handles.add(trackerHandle);
            this.localSearchTracker.put(client, handles);
            
            return results;
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
        return removeAllUrlReferences(plasmaURL.urlHash(url), fetchOnline);
    }
    
    public int removeAllUrlReferences(String urlhash, boolean fetchOnline) {
        // find all the words in a specific resource and remove the url reference from every word index
        // finally, delete the url entry
        
        // determine the url string
        indexURLEntry entry = wordIndex.loadedURL.load(urlhash, null);
        if (entry == null) return 0;
        indexURLEntry.Components comp = entry.comp();
        if (comp.url() == null) return 0;
        
        InputStream resourceContent = null;
        try {
            // get the resource content
            Object[] resource = snippetCache.getResource(comp.url(), fetchOnline, 10000, true);
            resourceContent = (InputStream) resource[0];
            Long resourceContentLength = (Long) resource[1];
            
            // parse the resource
            plasmaParserDocument document = snippetCache.parseDocument(comp.url(), resourceContentLength.longValue(), resourceContent);
            
            // get the word set
            Set words = null;
            try {
                words = new plasmaCondenser(document, true, true).words().keySet();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            
            // delete all word references
            int count = 0;
            if (words != null) count = wordIndex.removeWordReferences(words, urlhash);
            
            // finally delete the url entry itself
            wordIndex.loadedURL.remove(urlhash);
            return count;
        } catch (ParserException e) {
            return 0;
        } finally {
            if (resourceContent != null) try { resourceContent.close(); } catch (Exception e) {/* ignore this */}
        }
    }

    public int adminAuthenticated(httpHeader header) {
        
        String adminAccountBase64MD5 = getConfig(httpd.ADMIN_ACCOUNT_B64MD5, "");
        String authorization = ((String) header.get(httpHeader.AUTHORIZATION, "xxxxxx")).trim().substring(6);
        
        // security check against too long authorization strings
        if (authorization.length() > 256) return 0; 
        
        // authorization by encoded password, only for localhost access
        if ((((String) header.get("CLIENTIP", "")).equals("localhost")) && (adminAccountBase64MD5.equals(authorization))) return 3; // soft-authenticated for localhost

        // authorization by hit in userDB
        if (userDB.hasAdminRight((String) header.get(httpHeader.AUTHORIZATION, "xxxxxx"), ((String) header.get("CLIENTIP", "")), header.getHeaderCookies())) return 4; //return, because 4=max

        // authorization with admin keyword in configuration
        return httpd.staticAdminAuthenticated(authorization, this);
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
    
    public void setPerformance(int wantedPPM) {
        // we consider 3 cases here
        //         wantedPPM <=   10: low performance
        // 10   <  wantedPPM <  1000: custom performance
        // 1000 <= wantedPPM        : maximum performance
        if (wantedPPM <= 10) wantedPPM = 10;
        if (wantedPPM >= 1000) wantedPPM = 1000;
        int newBusySleep = 60000 / wantedPPM; // for wantedPPM = 10: 6000; for wantedPPM = 1000: 60

        serverThread thread;
        
        thread = getThread(INDEX_DIST);
        if (thread != null) {
            setConfig(INDEX_DIST_BUSYSLEEP , thread.setBusySleep(Math.max(2000, thread.setBusySleep(newBusySleep * 2))));
            thread.setIdleSleep(30000);
        }
        
        thread = getThread(CRAWLJOB_LOCAL_CRAWL);
        if (thread != null) {
        	setConfig(CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP , thread.setBusySleep(newBusySleep));
        	thread.setIdleSleep(1000);
        }
        
        thread = getThread(CRAWLJOB_GLOBAL_CRAWL_TRIGGER);
        if (thread != null) {
            setConfig(CRAWLJOB_GLOBAL_CRAWL_TRIGGER_BUSYSLEEP , thread.setBusySleep(Math.max(1000, newBusySleep * 3)));
            thread.setIdleSleep(10000);
        }
        /*
        thread = getThread(CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
        if (thread != null) {
            setConfig(CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP , thread.setBusySleep(newBusySleep * 10));
            thread.setIdleSleep(10000);
        }
        */
        thread = getThread(PROXY_CACHE_ENQUEUE);
        if (thread != null) {
            setConfig(PROXY_CACHE_ENQUEUE_BUSYSLEEP , thread.setBusySleep(0));
            thread.setIdleSleep(1000);
        }
        
        thread = getThread(INDEXER);
        if (thread != null) {
            setConfig(INDEXER_BUSYSLEEP , thread.setBusySleep(newBusySleep / 2));
            thread.setIdleSleep(1000);
        }
        
        thread = getThread(CRAWLSTACK);
        if (thread != null) {
            setConfig(CRAWLSTACK_BUSYSLEEP , thread.setBusySleep(0));
            thread.setIdleSleep(5000);
        }
        
    }
    
    public void startTransferWholeIndex(yacySeed seed, boolean delete) {
        if (transferIdxThread == null) {
            this.transferIdxThread = new plasmaDHTFlush(this.log, this.wordIndex, seed, delete,
                                                        "true".equalsIgnoreCase(getConfig(INDEX_TRANSFER_GZIP_BODY, "false")),
                                                        (int) getConfigLong(INDEX_TRANSFER_TIMEOUT, 60000));
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
        if (getConfig(INDEX_DIST_ALLOW, "false").equalsIgnoreCase("false")) {
            return "no DHT distribution: not enabled";
        }
        if (wordIndex.loadedURL.size() < 10) {
            return "no DHT distribution: loadedURL.size() = " + wordIndex.loadedURL.size();
        }
        if (wordIndex.size() < 100) {
            return "no DHT distribution: not enough words - wordIndex.size() = " + wordIndex.size();
        }
        if ((getConfig(INDEX_DIST_ALLOW_WHILE_CRAWLING, "false").equalsIgnoreCase("false")) &&
            ((noticeURL.stackSize() > 0) /*|| (sbQueue.size() > 3)*/)) {
            return "no DHT distribution: crawl in progress: noticeURL.stackSize() = " + noticeURL.stackSize() + ", sbQueue.size() = " + sbQueue.size();
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
            int minChunkSize = (int) getConfigLong(INDEX_DIST_CHUNK_SIZE_MIN, 30);
            int maxChunkSize = (int) getConfigLong(INDEX_DIST_CHUNK_SIZE_MAX, 3000);
            if (dhtTransferIndexCount < minChunkSize) dhtTransferIndexCount = minChunkSize;
            if (dhtTransferIndexCount > maxChunkSize) dhtTransferIndexCount = maxChunkSize;
            
            // show success
            return true;
        } else {
            dhtTransferChunk.incTransferFailedCounter();
            int maxChunkFails = (int) getConfigLong(INDEX_DIST_CHUNK_FAILS_MAX, 1);
            if (dhtTransferChunk.getTransferFailedCounter() >= maxChunkFails) {
                //System.out.println("DEBUG: " + dhtTransferChunk.getTransferFailedCounter() + " of " + maxChunkFails + " sendings failed for this chunk, aborting!");
                dhtTransferChunk.setStatus(plasmaDHTChunk.chunkStatus_FAILED);
                log.logFine("DHT distribution: transfer FAILED");   
            }
            else {
                //System.out.println("DEBUG: " + dhtTransferChunk.getTransferFailedCounter() + " of " + maxChunkFails + " sendings failed for this chunk, retrying!");
                log.logFine("DHT distribution: transfer FAILED, sending this chunk again");   
            }
            return false;
        }
    }

    public boolean dhtTransferProcess(plasmaDHTChunk dhtChunk, int peerCount) {
        if ((yacyCore.seedDB == null) || (yacyCore.seedDB.sizeConnected() == 0)) return false;

        try {
            // find a list of DHT-peers
            ArrayList seeds = yacyCore.dhtAgent.getDHTTargets(log, peerCount, 10, dhtChunk.firstContainer().getWordHash(), dhtChunk.lastContainer().getWordHash(), 0.2);
            if (seeds.size() < peerCount) {
                log.logWarning("found not enough (" + seeds.size() + ") peers for distribution for dhtchunk [" + dhtChunk.firstContainer().getWordHash() + " .. " + dhtChunk.lastContainer().getWordHash() + "]");
                return false;
            }

            // send away the indexes to all these peers
            int hc1 = 0;

            // getting distribution configuration values
            boolean gzipBody = getConfig(INDEX_DIST_GZIP_BODY, "false").equalsIgnoreCase("true");
            int timeout = (int)getConfigLong(INDEX_DIST_TIMEOUT, 60000);
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
            kelondroBitfield flags
    ) {
        // create a new errorURL DB entry
        plasmaCrawlEntry bentry = new plasmaCrawlEntry(
                initiator, 
                url, 
                referrerHash, 
                (name == null) ? "" : name, 
                new Date(), 
                null,
                0, 
                0, 
                0);
        plasmaCrawlZURL.Entry ee = this.errorURL.newEntry(
                bentry, initiator, new Date(),
                0, failreason);
        // store the entry
        ee.store();
        // push it onto the stack
        this.errorURL.stackPushEntry(ee);
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
            sb.getLog().logInfo("interrupted delayed shutdown");
        }
        this.sb.terminate();
    }
}
