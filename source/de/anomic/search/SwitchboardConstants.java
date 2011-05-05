// plasmaSwitchboardConstants.java
// (C) 2004-2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.07.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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

package de.anomic.search;

import net.yacy.kelondro.util.MapTools;
import de.anomic.http.server.RobotsTxtConfig;

/**
 * @author danielr
 *
 */
public final class SwitchboardConstants {


    /**
     * <p><code>public static final String <strong>ADMIN_ACCOUNT_B64MD5</strong> = "adminAccountBase64MD5"</code></p>
     * <p>Name of the setting holding the authentication hash for the static <code>admin</code>-account. It is calculated
     * by first encoding <code>username:password</code> as Base64 and hashing it using {@link MapTools#encodeMD5Hex(String)}.</p>
     */
    public static final String ADMIN_ACCOUNT_B64MD5 = "adminAccountBase64MD5";
    
    public static final int   CRAWLJOB_SYNC = 0;
    public static final int   CRAWLJOB_STATUS = 1;
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
     * @see Switchboard#PROXY_CACHE_ENQUEUE
     */
    public static final String CRAWLJOB_LOCAL_CRAWL                             = "50_localcrawl";
    public static final String CRAWLJOB_LOCAL_CRAWL_METHOD_START                = "coreCrawlJob";
    public static final String CRAWLJOB_LOCAL_CRAWL_METHOD_JOBCOUNT             = "coreCrawlJobSize";
    public static final String CRAWLJOB_LOCAL_CRAWL_METHOD_FREEMEM              = null;
    public static final String CRAWLJOB_LOCAL_CRAWL_IDLESLEEP                   = "50_localcrawl_idlesleep";
    public static final String CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP                   = "50_localcrawl_busysleep";
    // 60_remotecrawlloader
    /**
     * <p><code>public static final String <strong>CRAWLJOB_REMOTE_CRAWL_LOADER</strong> = "60_remotecrawlloader"</code></p>
     * <p>Name of the remote crawl list loading thread</p>
     * 
     * @see Switchboard#CRAWLJOB_REMOTE_CRAWL_LOADER
     */
    public static final String CRAWLJOB_REMOTE_CRAWL_LOADER                    = "60_remotecrawlloader";
    public static final String CRAWLJOB_REMOTE_CRAWL_LOADER_METHOD_START       = "remoteCrawlLoaderJob";
    public static final String CRAWLJOB_REMOTE_CRAWL_LOADER_METHOD_JOBCOUNT    = null;
    public static final String CRAWLJOB_REMOTE_CRAWL_LOADER_METHOD_FREEMEM     = null;
    public static final String CRAWLJOB_REMOTE_CRAWL_LOADER_IDLESLEEP          = "60_remotecrawlloader_idlesleep";
    public static final String CRAWLJOB_REMOTE_CRAWL_LOADER_BUSYSLEEP          = "60_remotecrawlloader_busysleep";
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
    // 80_indexing
    /**
     * <p><code>public static final String <strong>SURROGATES</strong> = "70_surrogates"</code></p>
     * <p>A thread that polls the SURROGATES path and puts all Documents in one surroagte file into the indexing queue.</p>
     */
    public static final String SURROGATES                      = "70_surrogates";
    public static final String SURROGATES_MEMPREREQ            = "70_surrogates_memprereq";
    public static final String SURROGATES_IDLESLEEP            = "70_surrogates_idlesleep";
    public static final String SURROGATES_BUSYSLEEP            = "70_surrogates_busysleep";
    public static final String SURROGATES_METHOD_START         = "surrogateProcess";
    public static final String SURROGATES_METHOD_JOBCOUNT      = "surrogateQueueSize";
    public static final String SURROGATES_METHOD_FREEMEM       = "surrogateFreeMem";
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
     * @see Switchboard#INDEX_DIST_ALLOW_WHILE_CRAWLING
     */
    public static final String INDEX_DIST_ALLOW                 = "allowDistributeIndex";
    public static final String INDEX_RECEIVE_ALLOW              = "allowReceiveIndex";
    /**
     * <p><code>public static final String <strong>INDEX_DIST_ALLOW_WHILE_CRAWLING</strong> = "allowDistributeIndexWhileCrawling"</code></p>
     * <p>Name of the setting whether Index Distribution shall be allowed while crawling is in progress, i.e.
     * the Local Crawler Queue is filled.</p>
     * <p>This setting only has effect if {@link #INDEX_DIST_ALLOW} is enabled</p>
     * 
     * @see Switchboard#INDEX_DIST_ALLOW
     */
    public static final String INDEX_DIST_ALLOW_WHILE_CRAWLING  = "allowDistributeIndexWhileCrawling";
    public static final String INDEX_DIST_ALLOW_WHILE_INDEXING  = "allowDistributeIndexWhileIndexing";
    public static final String INDEX_TRANSFER_TIMEOUT           = "indexTransfer.timeout";
    public static final String INDEX_TRANSFER_GZIP_BODY         = "indexTransfer.gzipBody";
    public static final String PARSER_MIME_DENY                 = "parser.mime.deny";
    /**
     * <p><code>public static final String <strong>PROXY_ONLINE_CAUTION_DELAY</strong> = "onlineCautionDelay"</code></p>
     * <p>Name of the setting how long indexing should pause after the last time the proxy was used in milliseconds</p> 
     */
    public static final String PROXY_ONLINE_CAUTION_DELAY        = "crawlPause.proxy";
    public static final String LOCALSEACH_ONLINE_CAUTION_DELAY   = "crawlPause.localsearch";
    public static final String REMOTESEARCH_ONLINE_CAUTION_DELAY = "crawlPause.remotesearch";
    /**
     * <p><code>public static final String <strong>PROXY_PREFETCH_DEPTH</strong> = "proxyPrefetchDepth"</code></p>
     * <p>Name of the setting how deep URLs fetched by proxy usage shall be followed</p>
     */
    public static final String PROXY_PREFETCH_DEPTH             = "proxyPrefetchDepth";
    public static final String PROXY_CRAWL_ORDER                = "proxyCrawlOrder";
    public static final String PROXY_INDEXING_REMOTE            = "proxyIndexingRemote";
    public static final String PROXY_INDEXING_LOCAL_TEXT        = "proxyIndexingLocalText";
    public static final String PROXY_INDEXING_LOCAL_MEDIA       = "proxyIndexingLocalMedia";
    public static final String PROXY_CACHE_SIZE                 = "proxyCacheSize";
    /**
     * <p><code>public static final String <strong>PROXY_CACHE_LAYOUT</strong> = "proxyCacheLayout"</code></p>
     * <p>Name of the setting which file-/folder-layout the proxy cache shall use. Possible values are {@link #PROXY_CACHE_LAYOUT_TREE}
     * and {@link #PROXY_CACHE_LAYOUT_HASH}</p>
     * 
     * @see Switchboard#PROXY_CACHE_LAYOUT_TREE
     * @see Switchboard#PROXY_CACHE_LAYOUT_HASH
     */
    public static final String PROXY_YACY_ONLY                 = "proxyYacyOnly";
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    // Cluster settings
    //////////////////////////////////////////////////////////////////////////////////////////////
    
    public static final String CLUSTER_MODE                     = "cluster.mode";
    public static final String CLUSTER_MODE_PUBLIC_CLUSTER      = "publiccluster";
    public static final String CLUSTER_MODE_PRIVATE_CLUSTER     = "privatecluster";
    public static final String CLUSTER_MODE_PUBLIC_PEER         = "publicpeer";
    public static final String CLUSTER_PEERS_IPPORT             = "cluster.peers.ipport";

    public static final String DHT_BURST_ROBINSON               = "network.unit.dht.burst.robinson";
    public static final String DHT_BURST_MULTIWORD              = "network.unit.dht.burst.multiword";

    public static final String REMOTESEARCH_MAXCOUNT_DEFAULT    = "network.unit.remotesearch.maxcount";
    public static final String REMOTESEARCH_MAXTIME_DEFAULT     = "network.unit.remotesearch.maxtime";

    public static final String REMOTESEARCH_MAXCOUNT_USER       = "remotesearch.maxcount";
    public static final String REMOTESEARCH_MAXTIME_USER        = "remotesearch.maxtime";
    
    /**
     * <p><code>public static final String <strong>CRAWLER_THREADS_ACTIVE_MAX</strong> = "crawler.MaxActiveThreads"</code></p>
     * <p>Name of the setting how many active crawler-threads may maximal be running on the same time</p>
     */
    public static final String CRAWLER_THREADS_ACTIVE_MAX       = "crawler.MaxActiveThreads";
    public static final String YACY_MODE_DEBUG                  = "yacyDebugMode";
    
    /**
     * <p><code>public static final String <strong>WORDCACHE_MAX_COUNT</strong> = "wordCacheMaxCount"</code></p>
     * <p>Name of the setting how many words the word-cache (or DHT-Out cache) shall contain maximal. Indexing pages if the
     * cache has reached this limit will slow down the indexing process by flushing some of it's entries</p>
     */
    public static final String WORDCACHE_MAX_COUNT              = "wordCacheMaxCount";
    public static final String HTTPC_NAME_CACHE_CACHING_PATTERNS_NO = "httpc.nameCacheNoCachingPatterns";
    public static final String ROBOTS_TXT                       = "httpd.robots.txt";
    public static final String ROBOTS_TXT_DEFAULT               = RobotsTxtConfig.LOCKED + "," + RobotsTxtConfig.DIRS;
    
    /**
     * <p><code>public static final String <strong>BLACKLIST_CLASS_DEFAULT</strong> = "de.anomic.plasma.urlPattern.defaultURLPattern"</code></p>
     * <p>Package and name of YaCy's {@link DefaultBlacklist default} blacklist implementation</p>
     * 
     * @see DefaultBlacklist for a detailed overview about the syntax of the default implementation
     */
    public static final String LIST_BLUE                = "plasmaBlueList";
    public static final String LIST_BLUE_DEFAULT        = null;
    public static final String LIST_BADWORDS_DEFAULT    = "yacy.badwords";
    public static final String LIST_STOPWORDS_DEFAULT   = "yacy.stopwords";
    
    /**
     * <p><code>public static final String <strong>HTCACHE_PATH</strong> = "proxyCache"</code></p>
     * <p>Name of the setting specifying the folder beginning from the YaCy-installation's top-folder, where all
     * downloaded webpages and their respective ressources and HTTP-headers are stored. It is the location containing
     * the proxy-cache</p>
     * 
     * @see Switchboard#PROXY_CACHE_LAYOUT for details on the file-layout in this path
     */
    public static final String HTCACHE_PATH             = "proxyCache";
    public static final String HTCACHE_PATH_DEFAULT     = "DATA/HTCACHE";
    public static final String RELEASE_PATH             = "releases";
    public static final String RELEASE_PATH_DEFAULT     = "DATA/RELEASE";
    
    public static final String SURROGATES_IN_PATH          = "surrogates.in";
    public static final String SURROGATES_IN_PATH_DEFAULT  = "DATA/SURROGATES/in";
    public static final String SURROGATES_OUT_PATH         = "surrogates.out";
    public static final String SURROGATES_OUT_PATH_DEFAULT = "DATA/SURROGATES/out";
    
    public static final String DICTIONARY_SOURCE_PATH         = "dictionaries";
    public static final String DICTIONARY_SOURCE_PATH_DEFAULT = "DATA/DICTIONARIES";
    
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
    public static final String INDEX_PATH_DEFAULT       = "DATA/INDEX";
    /**
     * <p><code>public static final String <strong>LISTS_PATH</strong> = "listsPath"</code></p>
     * <p>Name of the setting specifying the folder beginning from the YaCy-installation's top-folder, where all
     * user-lists like blacklists, etc. are stored</p>
     */
    public static final String LISTS_PATH               = "listsPath";
    public static final String LISTS_PATH_DEFAULT       = "DATA/LISTS";
    /**
     * <p><code>public static final String <strong>WORK_PATH</strong> = "wordPath"</code></p>
     * <p>Name of the setting specifying the folder beginning from the YaCy-installation's top-folder, where all
     * DBs containing "work" of the user are saved. Such include bookmarks, messages, wiki, blog</p>
     * 
     * @see Switchboard#DBFILE_BLOG
     * @see Switchboard#DBFILE_BOOKMARKS
     * @see Switchboard#DBFILE_BOOKMARKS_DATES
     * @see Switchboard#DBFILE_BOOKMARKS_TAGS
     * @see Switchboard#DBFILE_MESSAGE
     * @see Switchboard#DBFILE_WIKI
     * @see Switchboard#DBFILE_WIKI_BKP
     */
    public static final String WORK_PATH                = "workPath";
    public static final String WORK_PATH_DEFAULT        = "DATA/WORK";

    
    /**
     * ResourceObserver
     */
    public static final String DISK_FREE = "disk.free";
    public static final String DISK_FREE_HARDLIMIT = "disk.free.hardlimit";
    
    public static final String MEMORY_ACCEPTDHT = "memory.acceptDHTabove";
    public static final String INDEX_RECEIVE_AUTODISABLED = "memory.disabledDHT";
    
    /*
     * Some constants
     */
    public static final String STR_REMOTECRAWLTRIGGER = "REMOTECRAWLTRIGGER: REMOTE CRAWL TO PEER ";

    /**
     * network properties
     * 
     */
    public static final String NETWORK_NAME = "network.unit.name";
    public static final String NETWORK_DOMAIN = "network.unit.domain";
    public static final String NETWORK_DOMAIN_NOCHECK = "network.unit.domain.nocheck";
    public static final String NETWORK_WHITELIST = "network.unit.access.whitelist";
    public static final String NETWORK_BLACKLIST = "network.unit.access.blacklist";
    
    public static final String NETWORK_SEARCHVERIFY = "network.unit.inspection.searchverify";
    
    /**
     * appearance
     */
    public static final String GREETING              = "promoteSearchPageGreeting";
    public static final String GREETING_NETWORK_NAME = "promoteSearchPageGreeting.useNetworkName";
    public static final String GREETING_HOMEPAGE     = "promoteSearchPageGreeting.homepage";
    public static final String GREETING_LARGE_IMAGE  = "promoteSearchPageGreeting.largeImage";
    public static final String GREETING_SMALL_IMAGE  = "promoteSearchPageGreeting.smallImage";
    
    /**
     * browser pop up
     */
    public static final String BROWSER_POP_UP_TRIGGER     = "browserPopUpTrigger";
    public static final String BROWSER_POP_UP_PAGE        = "browserPopUpPage";
    
    /**
     * forwarder of the index page
     */
    public static final String INDEX_FORWARD        = "indexForward";
    
    public static final String UPNP_ENABLED			= "upnp.enabled";
    public static final String UPNP_REMOTEHOST		= "upnp.remoteHost";

    public static final String SEARCH_ITEMS   = "search.items";
    public static final String SEARCH_TARGET  = "search.target";
    
    /**
     * system tray
     */
	public static final String TRAY_ICON_ENABLED	 = "trayIcon";
	public static final String TRAY_ICON_FORCED		 = "trayIcon.force";
	public static final String TRAY_LABEL			 = "tray.label";
	public static final String BROWSERINTEGRATION	 = "browserintegration";
	
	/**
	 * Segments
	 */
	public static final String SEGMENT_RECEIPTS      = "segment.process.receipts_tmp";
	public static final String SEGMENT_QUERIES       = "segment.process.queries_tmp";
	public static final String SEGMENT_DHTIN         = "segment.process.dhtin_tmp";
	public static final String SEGMENT_DHTOUT        = "segment.process.dhtout_tmp";
	public static final String SEGMENT_PROXY         = "segment.process.proxy_tmp";
	public static final String SEGMENT_LOCALCRAWLING = "segment.process.localcrawling_tmp";
	public static final String SEGMENT_REMOTECRAWLING= "segment.process.remotecrawling_tmp";
	public static final String SEGMENT_PUBLIC        = "segment.process.public_tmp";
}
