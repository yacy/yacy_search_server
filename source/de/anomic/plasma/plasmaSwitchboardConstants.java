// plasmaSwitchboardConstants.java
// (C) 2004-2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.07.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2008-03-14 01:16:04 +0100 (Fr, 14 Mrz 2008) $
// $LastChangedRevision: 4558 $
// $LastChangedBy: orbiter $
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

package de.anomic.plasma;

import de.anomic.http.httpdRobotsTxtConfig;

/**
 * @author danielr
 *
 */
public final class plasmaSwitchboardConstants {

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
     * <p><code>public static final String <strong>CRAWLJOB_REMOTE_CRAWL_LOADER</strong> = "60_remotecrawlloader"</code></p>
     * <p>Name of the remote crawl list loading thread</p>
     * 
     * @see plasmaSwitchboard#CRAWLJOB_REMOTE_CRAWL_LOADER
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
    // 74_parsing
    /**
     * <p><code>public static final String <strong>INDEXER</strong> = "80_indexing"</code></p>
     * <p>Name of the indexer thread, performing the actual indexing of a website</p>
     */
    public static final String PARSER                      = "74_indexing";
    public static final String PARSER_MEMPREREQ            = "74_indexing_memprereq";
    public static final String PARSER_IDLESLEEP            = "74_indexing_idlesleep";
    public static final String PARSER_BUSYSLEEP            = "74_indexing_busysleep";
    public static final String PARSER_METHOD_START         = "deQueueProcess";
    public static final String PARSER_METHOD_JOBCOUNT      = "queueSize";
    public static final String PARSER_METHOD_FREEMEM       = "deQueueFreeMem";
    // 80_indexing
    /**
     * <p><code>public static final String <strong>INDEXER</strong> = "80_indexing"</code></p>
     * <p>Name of the indexer thread, performing the actual indexing of a website</p>
     */
    public static final String INDEXER                      = "80_indexing";
    public static final String INDEXER_MEMPREREQ            = "80_indexing_memprereq";
    public static final String INDEXER_IDLESLEEP            = "80_indexing_idlesleep";
    public static final String INDEXER_BUSYSLEEP            = "80_indexing_busysleep";
    public static final String INDEXER_METHOD_START         = "deQueueProcess";
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
    public static final String CRAWLSTACK_SLOTS             = "stacker.slots";
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
    public static final String INDEX_DIST_ALLOW_WHILE_INDEXING  = "allowDistributeIndexWhileIndexing";
    public static final String INDEX_TRANSFER_TIMEOUT           = "indexTransfer.timeout";
    public static final String INDEX_TRANSFER_GZIP_BODY         = "indexTransfer.gzipBody";
    public static final String RANKING_DIST_ON                  = "CRDistOn";
    public static final String RANKING_DIST_0_PATH              = "CRDist0Path";
    public static final String RANKING_DIST_0_METHOD            = "CRDist0Method";
    public static final String RANKING_DIST_0_PERCENT           = "CRDist0Percent";
    public static final String RANKING_DIST_0_TARGET            = "CRDist0Target";
    public static final String RANKING_DIST_1_PATH              = "CRDist1Path";
    public static final String RANKING_DIST_1_METHOD            = "CRDist1Method";
    public static final String RANKING_DIST_1_PERCENT           = "CRDist1Percent";
    public static final String RANKING_DIST_1_TARGET            = "CRDist1Target";
    public static final String PARSER_MIMETYPES_HTML            = "parseableMimeTypes.HTML";
    public static final String PARSER_MIMETYPES_PROXY           = "parseableMimeTypes.PROXY";
    public static final String PARSER_MIMETYPES_CRAWLER         = "parseableMimeTypes.CRAWLER";
    public static final String PARSER_MIMETYPES_ICAP            = "parseableMimeTypes.ICAP";
    public static final String PARSER_MIMETYPES_URLREDIRECTOR   = "parseableMimeTypes.URLREDIRECTOR";
    public static final String PARSER_MIMETYPES_IMAGE           = "parseableMimeTypes.IMAGE";
    public static final String PARSER_MEDIA_EXT                 = "mediaExt";
    public static final String PARSER_MEDIA_EXT_PARSEABLE       = "parseableExt";
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
     * @see plasmaSwitchboard#PROXY_CACHE_LAYOUT_TREE
     * @see plasmaSwitchboard#PROXY_CACHE_LAYOUT_HASH
     */
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    // Cluster settings
    //////////////////////////////////////////////////////////////////////////////////////////////
    
    public static final String CLUSTER_MODE                     = "cluster.mode";
    public static final String CLUSTER_MODE_PUBLIC_CLUSTER      = "publiccluster";
    public static final String CLUSTER_MODE_PRIVATE_CLUSTER     = "privatecluster";
    public static final String CLUSTER_MODE_PUBLIC_PEER         = "publicpeer";
    public static final String CLUSTER_PEERS_IPPORT             = "cluster.peers.ipport";
    /**
     * <p><code>public static final String <strong>CRAWLER_THREADS_ACTIVE_MAX</strong> = "crawler.MaxActiveThreads"</code></p>
     * <p>Name of the setting how many active crawler-threads may maximal be running on the same time</p>
     */
    public static final String CRAWLER_THREADS_ACTIVE_MAX       = "crawler.MaxActiveThreads";
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
    public static final String WIKIPARSER_CLASS                 = "wikiParser.class";
    public static final String WIKIPARSER_CLASS_DEFAULT         = "de.anomic.data.wikiCode";
    /**
     * <p><code>public static final String <strong>BLACKLIST_CLASS</strong> = "Blacklist.class"</code></p>
     * <p>Name of the setting which Blacklist backend shall be used. Due to different requirements of users, the
     * {@link plasmaURLPattern}-interface has been created to support blacklist engines different from YaCy's default</p>
     * <p>Attention is required when the backend is changed, because different engines may have different syntaxes</p>
     */
    public static final String BLACKLIST_CLASS          = "BlackLists.class";
    /**
     * <p><code>public static final String <strong>BLACKLIST_CLASS_DEFAULT</strong> = "de.anomic.plasma.urlPattern.defaultURLPattern"</code></p>
     * <p>Package and name of YaCy's {@link indexDefaultReferenceBlacklist default} blacklist implementation</p>
     * 
     * @see indexDefaultReferenceBlacklist for a detailed overview about the syntax of the default implementation
     */
    public static final String BLACKLIST_CLASS_DEFAULT  = "de.anomic.index.indexDefaultReferenceBlacklist";
    public static final String LIST_BLUE                = "plasmaBlueList";
    public static final String LIST_BLUE_DEFAULT        = null;
    public static final String LIST_BADWORDS_DEFAULT    = "yacy.badwords";
    public static final String LIST_STOPWORDS_DEFAULT   = "yacy.stopwords";
    /**
     * <p><code>public static final String <strong>DBPATH</strong> = "dbPath"</code></p>
     * <p>Name of the setting specifying the folder beginning from the YaCy-installation's top-folder, where all
     * databases containing queues are stored</p>
     */
    public static final String PLASMA_PATH              = "dbPath";
    public static final String PLASMA_PATH_DEFAULT      = "DATA/PLASMADB";
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
    public static final String RELEASE_PATH             = "releases";
    public static final String RELEASE_PATH_DEFAULT     = "DATA/RELEASE";
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
     * <p><code>public static final String <strong>DBFILE_CRAWL_ROBOTS</strong> = "crawlRobotsTxt.db"</code></p>
     * <p>Name of the file containing the database holding all <code>robots.txt</code>-entries of the lately crawled domains</p>
     * 
     * @see plasmaSwitchboard#PLASMA_PATH for the folder this file lies in
     */
    public static final String DBFILE_CRAWL_ROBOTS      = "crawlRobotsTxt.heap";
    /**
     * <p><code>public static final String <strong>DBFILE_USER</strong> = "DATA/SETTINGS/user.db"</code></p>
     * <p>Path to the user-DB, beginning from the YaCy-installation's top-folder. It holds all rights the created
     * users have as well as all other needed data about them</p>
     */
    public static final String DBFILE_USER              = "DATA/SETTINGS/user.db";
    // we must distinguish the following cases: resource-load was initiated by
    // 1) global crawling: the index is extern, not here (not possible here)
    // 2) result of search queries, some indexes are here (not possible here)
    // 3) result of index transfer, some of them are here (not possible here)
    // 4) proxy-load (initiator is "------------")
    // 5) local prefetch/crawling (initiator is own seedHash)
    // 6) local fetching for global crawling (other known or unknown initiator)
    public static final int PROCESSCASE_0_UNKNOWN = 0;
    public static final int PROCESSCASE_4_PROXY_LOAD = 4;
    public static final int PROCESSCASE_5_LOCAL_CRAWLING = 5;
    public static final int PROCESSCASE_6_GLOBAL_CRAWLING = 6;
    /*
     * Some constants
     */
    public static final String STR_REMOTECRAWLTRIGGER = "REMOTECRAWLTRIGGER: REMOTE CRAWL TO PEER ";

}
