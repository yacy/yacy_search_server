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

package net.yacy.search;

import java.util.zip.Deflater;

import net.yacy.cora.order.Digest;
import net.yacy.server.http.RobotsTxtConfig;

/**
 * @author danielr
 *
 */
public final class SwitchboardConstants {


    /**
     * <p><code>public static final String <strong>ADMIN_ACCOUNT_B64MD5</strong> = "adminAccountBase64MD5"</code></p>
     * <p>Name of the setting holding the authentication hash for the static <code>admin</code>-account. It is calculated
     * by first encoding <code>username:password</code> as Base64 and hashing it using {@link Digest#encodeMD5Hex(String)}.</p>
     * With introduction of DIGEST authentication all passwords are MD5 encoded and calculatd as <code>username:adminrealm:password</code>
     * To differentiate old and new admin passwords, use the new calculated passwords a "MD5:" prefix.
     */

    public static final String ADMIN_ACCOUNT                = "adminAccount"; // not used anymore (did hold clear text  username:pwd)

    // this holds the credential "MD5:" + Digest.encodeMD5Hex(adminAccountUserName + ":" + adminRealm + ":" + password)
    // or the depreciated old style MapTools.encodeMD5Hex( Base64Order.standardCoder.encode(adminAccountUserName + ":" + password) )
    public static final String ADMIN_ACCOUNT_USER_NAME      = "adminAccountUserName"; // by default 'admin'
    public static final String ADMIN_ACCOUNT_B64MD5         = "adminAccountBase64MD5"; // by default the encoding of 'yacy' (MD5:8cffbc0d66567a0987a4aba1ec46d63c)
    public static final String ADMIN_ACCOUNT_B64MD5_DEFAULT = "MD5:8cffbc0d66567a0987a4aba1ec46d63c"; // use this to check if the default setting was overwritten
    public static final String ADMIN_ACCOUNT_FOR_LOCALHOST  = "adminAccountForLocalhost";
    public static final String ADMIN_ACCOUNT_All_PAGES      = "adminAccountAllPages";
    public static final String ADMIN_REALM                  = "adminRealm";

    // server settings
    public static final String SERVER_PORT                  = "port"; // port for the http server
    public static final String SERVER_SSLPORT               = "port.ssl"; // port for https
    public static final String SERVER_SHUTDOWNPORT          = "port.shutdown"; // local port to listen for a shutdown signal (0 <= disabled)
    public static final String SERVER_STATICIP              = "staticIP"; // static IP of http server
    public static final String SERVER_PUBLICPORT            = "publicPort";

    public static final String PUBLIC_SEARCHPAGE            = "publicSearchpage";

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
    public static final String INDEX_DIST_LOADPREREQ        = "20_dhtdistribution_loadprereq";
    public static final String INDEX_DIST_IDLESLEEP         = "20_dhtdistribution_idlesleep";
    public static final String INDEX_DIST_BUSYSLEEP         = "20_dhtdistribution_busysleep";
    public static final String INDEX_RECEIVE_LOADPREREQ     = "20_dhtreceive_loadprereq";
    // 30_peerping
    /**
     * <p><code>public static final String <strong>PEER_PING</strong> = "30_peerping"</code></p>
     * <p>Name of the Peer Ping thread which publishes the own peer and retrieves information about other peers
     * connected to the YaCy-network</p>
     */
    public static final String PEER_PING                    = "30_peerping";
    public static final String PEER_PING_IDLESLEEP          = "30_peerping_idlesleep";
    public static final String PEER_PING_BUSYSLEEP          = "30_peerping_busysleep";
    // 40_peerseedcycle
    /**
     * <p><code>public static final String <strong>SEED_UPLOAD</strong> = "40_peerseedcycle"</code></p>
     * <p>Name of the seed upload thread, providing the so-called seed-lists needed during bootstrapping</p>
     */
    public static final String SEED_UPLOAD                  = "40_peerseedcycle";
    public static final String SEED_UPLOAD_IDLESLEEP        = "40_peerseedcycle_idlesleep";
    public static final String SEED_UPLOAD_BUSYSLEEP        = "40_peerseedcycle_busysleep";
    // 50_localcrawl
    /**
     * <p><code>public static final String <strong>CRAWLJOB_LOCAL_CRAWL</strong> = "50_localcrawl"</code></p>
     * <p>Name of the local crawler thread, popping one entry off the Local Crawl Queue, and passing it to the
     * proxy cache enqueue thread to download and further process it</p>
     *
     */
    public static final String CRAWLJOB_LOCAL_CRAWL                             = "50_localcrawl";
    public static final String CRAWLJOB_LOCAL_CRAWL_IDLESLEEP                   = "50_localcrawl_idlesleep";
    public static final String CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP                   = "50_localcrawl_busysleep";
    public static final String CRAWLJOB_LOCAL_CRAWL_LOADPREREQ                  = "50_localcrawl_loadprereq";    
    // 55_autocrawl
    /**
     * <p><code>public static final String <string>CRAWLJOB_AUTOCRAWL</strong> = "55_autocrawl"</code></p>
     * <p>Name of the autocrawl thread</p>
     */
    public static final String CRAWLJOB_AUTOCRAWL                               = "55_autocrawl";
    public static final String CRAWLJOB_AUTOCRAWL_METHOD_START                  = "autocrawlJob";
    public static final String CRAWLJOB_AUTOCRAWL_METHOD_JOBCOUNT               = null;
    public static final String CRAWLJOB_AUTOCRAWL_METHOD_FREEMEM                = null;
    public static final String CRAWLJOB_AUTOCRAWL_IDLESLEEP                     = "55_autocrawl_idlesleep";
    public static final String CRAWLJOB_AUTOCRAWL_BUSYSLEEP                     = "55_autocrawl_busysleep";
    // 60_remotecrawlloader
    /**
     * <p><code>public static final String <strong>CRAWLJOB_REMOTE_CRAWL_LOADER</strong> = "60_remotecrawlloader"</code></p>
     * <p>Name of the remote crawl list loading thread</p>
     *
     * @see #CRAWLJOB_REMOTE_CRAWL_LOADER
     */
    public static final String CRAWLJOB_REMOTE                                 = "crawlResponse"; // enable/disable response to remote crawl requests
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
    public static final String CRAWLJOB_REMOTE_TRIGGERED_CRAWL_IDLESLEEP        = "62_remotetriggeredcrawl_idlesleep";
    public static final String CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP        = "62_remotetriggeredcrawl_busysleep";
 // 70_surrogates
    /**
     * <p><code>public static final String <strong>SURROGATES</strong> = "70_surrogates"</code></p>
     * <p>A thread that polls the SURROGATES path and puts all Documents in one surrogate file into the indexing queue.</p>
     */
    public static final String SURROGATES                      = "70_surrogates";
    public static final String SURROGATES_MEMPREREQ            = "70_surrogates_memprereq";
    public static final String SURROGATES_LOADPREREQ           = "70_surrogates_loadprereq";
    public static final String SURROGATES_IDLESLEEP            = "70_surrogates_idlesleep";
    public static final String SURROGATES_BUSYSLEEP            = "70_surrogates_busysleep";
    // 85_scheduler
    /**
     * <p><code>public static final String <strong>SCHEDULER</strong> = "85_scheduler"</code></p>
     * <p>The cleanup thread which is responsible for the start of scheduled processes from the API table</p>
     */
    public static final String SCHEDULER                    = "85_scheduler";
    public static final String SCHEDULER_IDLESLEEP          = "85_scheduler_idlesleep";
    public static final String SCHEDULER_BUSYSLEEP          = "85_scheduler_busysleep";
    // 90_cleanup
    /**
     * <p><code>public static final String <strong>CLEANUP</strong> = "90_cleanup"</code></p>
     * <p>The cleanup thread which is responsible for pendant cleanup-jobs, news/ranking distribution, etc.</p>
     */
    public static final String CLEANUP                      = "90_cleanup";
    public static final String CLEANUP_IDLESLEEP            = "90_cleanup_idlesleep";
    public static final String CLEANUP_BUSYSLEEP            = "90_cleanup_busysleep";
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
     * @see #INDEX_DIST_ALLOW_WHILE_CRAWLING
     */
    public static final String INDEX_DIST_ALLOW                 = "allowDistributeIndex";
    public static final String INDEX_RECEIVE_ALLOW              = "allowReceiveIndex";
    public static final String INDEX_RECEIVE_ALLOW_SEARCH       = "allowReceiveIndex.search";
    public static final String INDEX_RECEIVE_BLOCK_BLACKLIST    = "indexReceiveBlockBlacklist";
    
    /**
     * <p><code>public static final String <strong>INDEX_DIST_ALLOW_WHILE_CRAWLING</strong> = "allowDistributeIndexWhileCrawling"</code></p>
     * <p>Name of the setting whether Index Distribution shall be allowed while crawling is in progress, i.e.
     * the Local Crawler Queue is filled.</p>
     * <p>This setting only has effect if {@link #INDEX_DIST_ALLOW} is enabled</p>
     *
     * @see #INDEX_DIST_ALLOW
     */
    public static final String INDEX_DIST_ALLOW_WHILE_CRAWLING  = "allowDistributeIndexWhileCrawling";
    public static final String INDEX_DIST_ALLOW_WHILE_INDEXING  = "allowDistributeIndexWhileIndexing";
    public static final String INDEX_TRANSFER_TIMEOUT           = "indexTransfer.timeout";
    public static final String INDEX_TRANSFER_GZIP_BODY         = "indexTransfer.gzipBody";
    public static final String PARSER_MIME_DENY                 = "parser.mime.deny";
    public static final String PARSER_EXTENSIONS_DENY           = "parser.extensions.deny";
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
    public static final String PROXY_YACY_ONLY                 = "proxyYacyOnly";
    public static final String PROXY_TRANSPARENT_PROXY         = "isTransparentProxy";

    public static final String AUTOCRAWL                       = "autocrawl";
    public static final String AUTOCRAWL_INDEX_TEXT            = "autocrawl.index.text";
    public static final String AUTOCRAWL_INDEX_MEDIA           = "autocrawl.index.media";
    public static final String AUTOCRAWL_RATIO                 = "autocrawl.ratio";
    public static final String AUTOCRAWL_ROWS                  = "autocrawl.rows";
    public static final String AUTOCRAWL_DAYS                  = "autocrawl.days";
    public static final String AUTOCRAWL_QUERY                 = "autocrawl.query";
    public static final String AUTOCRAWL_DEEP_DEPTH            = "autocrawl.deep.depth";
    public static final String AUTOCRAWL_SHALLOW_DEPTH         = "autocrawl.shallow.depth";
    

    //////////////////////////////////////////////////////////////////////////////////////////////
    // Cluster settings
    //////////////////////////////////////////////////////////////////////////////////////////////

    public static final String CLUSTER_MODE                     = "cluster.mode";
    public static final String CLUSTER_MODE_PUBLIC_CLUSTER      = "publiccluster";
    public static final String CLUSTER_MODE_PUBLIC_PEER         = "publicpeer";
    public static final String CLUSTER_MODE_PRIVATE_PEER        = "privatepeer";
    public static final String CLUSTER_PEERS_IPPORT             = "cluster.peers.ipport";
    
    /** Key of the global HTTP Referrer policy delivered by meta tag */
    public static final String REFERRER_META_POLICY = "referrer.meta.policy";
    
    /** Default value for the global HTTP Referrer policy delivered by meta tag */
    public static final String REFERRER_META_POLICY_DEFAULT = "origin-when-cross-origin";


    public static final String NETWORK_UNIT_DHT                 = "network.unit.dht";
    public static final String NETWORK_UNIT_AGENT               = "network.unit.agent";
    public static final String REMOTESEARCH_MAXCOUNT_DEFAULT    = "network.unit.remotesearch.maxcount";
    public static final String REMOTESEARCH_MAXTIME_DEFAULT     = "network.unit.remotesearch.maxtime";
    public static final String REMOTESEARCH_MAXCOUNT_USER       = "remotesearch.maxcount";
    public static final String REMOTESEARCH_MAXTIME_USER        = "remotesearch.maxtime";
    public static final String REMOTESEARCH_RESULT_STORE        = "remotesearch.result.store"; // add remote results to local index
    /** Maximum size allowed (in kbytes) for a remote document result to be stored to local index */
    public static final String REMOTESEARCH_RESULT_STORE_MAXSIZE= "remotesearch.result.store.maxsize";
    
    /** Setting key to configure the maximum system load allowing remote RWI searches */
    public static final String REMOTESEARCH_MAXLOAD_RWI         = "remotesearch.maxload.rwi";
    
    /** Default maximum system load allowing remote RWI searches */
    public static final float REMOTESEARCH_MAXLOAD_RWI_DEFAULT  = 2.0f * (float) Runtime.getRuntime().availableProcessors();
    
    /** Setting key to configure the maximum system load allowing remote Solr searches */
    public static final String REMOTESEARCH_MAXLOAD_SOLR        = "remotesearch.maxload.solr";
    
    /** Default maximum system load allowing remote Solr searches */
    public static final float REMOTESEARCH_MAXLOAD_SOLR_DEFAULT = (float) Runtime.getRuntime().availableProcessors();
    
    /** Key of the setting controlling whether https should be preferred for remote searches, when available on the target peer */
    public static final String REMOTESEARCH_HTTPS_PREFERRED = "remotesearch.https.preferred";
    
    /** Default setting value controlling whether https should be preferred for remote searches, when available on the target peer */
    public static final boolean REMOTESEARCH_HTTPS_PREFERRED_DEFAULT = false;
    
	/**
	 * Setting key to configure whether responses from remote Solr instances
	 * should be binary encoded :
	 * <ul>
	 * <li>true : more efficient, uses the solrj binary response parser</li>
	 * <li>false : responses are transferred as XML, which can be captured and
	 * parsed by any external XML aware tool for debug/analysis</li>
	 * </ul>
	 */
    public static final String REMOTE_SOLR_BINARY_RESPONSE_ENABLED = "remote.solr.binaryResponse.enabled";
    
    /** Default configuration setting for remote Solr responses binary encoding */
    public static final boolean REMOTE_SOLR_BINARY_RESPONSE_ENABLED_DEFAULT            = true;

    /** Key of the setting controlling whether to use or not remote Solr server(s) */
    public static final String FEDERATED_SERVICE_SOLR_INDEXING_ENABLED      = "federated.service.solr.indexing.enabled";
    
    /** Default setting value controlling whether to use or not remote Solr server(s) */
    public static final boolean FEDERATED_SERVICE_SOLR_INDEXING_ENABLED_DEFAULT = false;
    
    public static final String FEDERATED_SERVICE_SOLR_INDEXING_URL          = "federated.service.solr.indexing.url";
    public static final String FEDERATED_SERVICE_SOLR_INDEXING_SHARDING     = "federated.service.solr.indexing.sharding";
    public static final String FEDERATED_SERVICE_SOLR_INDEXING_LAZY         = "federated.service.solr.indexing.lazy";
    public static final String FEDERATED_SERVICE_SOLR_INDEXING_TIMEOUT      = "federated.service.solr.indexing.timeout";
    public static final String FEDERATED_SERVICE_SOLR_INDEXING_WRITEENABLED = "federated.service.solr.indexing.writeEnabled";
    
    /** Setting key controlling whether a self-signed certificate is acceptable from a remote Solr instance requested with authentication credentials. 
     * This has no impact on connections to remote Solr instances used in p2p search for which self-signed certificates are always accepted. */
    public static final String FEDERATED_SERVICE_SOLR_INDEXING_AUTHENTICATED_ALLOW_SELF_SIGNED = "federated.service.solr.indexing.authenticated.allowSelfSigned";

    /** Default value controlling whether a self-signed certificate is acceptable from a remote Solr instance with authentication credentials. */
    public static final boolean FEDERATED_SERVICE_SOLR_INDEXING_AUTHENTICATED_ALLOW_SELF_SIGNED_DEFAULT = false;

    /** Key of the setting controlling whether to use or not an embedded Solr instance */
    public static final String CORE_SERVICE_FULLTEXT            = "core.service.fulltext";
    
    /** Default setting value controlling whether to use or not an embedded Solr instance */
    public static final boolean CORE_SERVICE_FULLTEXT_DEFAULT   = true;
    
    public static final String CORE_SERVICE_RWI                 = "core.service.rwi.tmp";
    public static final String CORE_SERVICE_CITATION            = "core.service.citation.tmp";
    public static final String CORE_SERVICE_WEBGRAPH            = "core.service.webgraph.tmp";

    /**
     * <p><code>public static final String <strong>CRAWLER_THREADS_ACTIVE_MAX</strong> = "crawler.MaxActiveThreads"</code></p>
     * <p>Name of the setting how many active crawler-threads may maximal be running on the same time</p>
     */
    public static final String CRAWLER_THREADS_ACTIVE_MAX       = "crawler.MaxActiveThreads";
    public static final String CRAWLER_LATENCY_FACTOR           = "crawler.latencyFactor";
    public static final String CRAWLER_MAX_SAME_HOST_IN_QUEUE   = "crawler.MaxSameHostInQueue";
    public static final String CRAWLER_FOLLOW_REDIRECTS         = "crawler.http.FollowRedirects"; // ignore the target url and follow to the redirect
    public static final String CRAWLER_RECORD_REDIRECTS         = "crawler.http.RecordRedirects"; // record the ignored redirected page to the index store
    
    public static final String CRAWLER_USER_AGENT_NAME          = "crawler.userAgent.name";
    public static final String CRAWLER_USER_AGENT_STRING        = "crawler.userAgent.string";
    public static final String CRAWLER_USER_AGENT_MINIMUMDELTA  = "crawler.userAgent.minimumdelta";
    public static final String CRAWLER_USER_AGENT_CLIENTTIMEOUT = "crawler.userAgent.clienttimeout";
    
    /** Key of the setting controlling the maximum time to wait for each wkhtmltopdf call when rendering PDF snapshots */
    public static final String SNAPSHOTS_WKHTMLTOPDF_TIMEOUT          = "snapshots.wkhtmltopdf.timeout";
    
    /** Default maximum time in seconds to wait for each wkhtmltopdf call when rendering PDF snapshots*/
    public static final long SNAPSHOTS_WKHTMLTOPDF_TIMEOUT_DEFAULT   = 30;
    
    /* --- debug flags ---  */
    
    /** when set to true : do not use the local dht/rwi index (which is not done if we do remote searches) */
    public static final String DEBUG_SEARCH_LOCAL_DHT_OFF       = "debug.search.local.dht.off";
    
    /** when set to true : do not use local solr index */
    public static final String DEBUG_SEARCH_LOCAL_SOLR_OFF      = "debug.search.local.solr.off";
    
    /** when set to true : do not use remote dht/rwi */
    public static final String DEBUG_SEARCH_REMOTE_DHT_OFF      = "debug.search.remote.dht.off";
    
    /** when set to true : do not use remote solr indexes */
    public static final String DEBUG_SEARCH_REMOTE_SOLR_OFF     = "debug.search.remote.solr.off";
    
    /** when set to true : do not use dht, search local peer in a shortcut to the own server */
    public static final String DEBUG_SEARCH_REMOTE_DHT_TESTLOCAL= "debug.search.remote.dht.testlocal";
    
    /** when set to true : do not use dht, search local peer in a shortcut to the own server */
    public static final String DEBUG_SEARCH_REMOTE_SOLR_TESTLOCAL= "debug.search.remote.solr.testlocal";
    
    /** Key of the setting controlling whether text snippets statistics should be computed */
    public static final String DEBUG_SNIPPETS_STATISTICS_ENABLED = "debug.snippets.statistics.enabled";
    
    /** Default value for the setting controlling whether text snippets statistics should be computed */
    public static final boolean DEBUG_SNIPPETS_STATISTICS_ENABLED_DEFAULT = false;
    
    /**
     * <p><code>public static final String <strong>WORDCACHE_MAX_COUNT</strong> = "wordCacheMaxCount"</code></p>
     * <p>Name of the setting how many words the word-cache (or DHT-Out cache) shall contain maximal. Indexing pages if the
     * cache has reached this limit will slow down the indexing process by flushing some of it's entries</p>
     */
    public static final String WORDCACHE_MAX_COUNT              = "wordCacheMaxCount";
    public static final String HTTPC_NAME_CACHE_CACHING_PATTERNS_NO = "httpc.nameCacheNoCachingPatterns";
    public static final String ROBOTS_TXT                       = "httpd.robots.txt";
    public static final String ROBOTS_TXT_DEFAULT               = RobotsTxtConfig.LOCKED + "," + RobotsTxtConfig.DIRS;
    /** Key of the setting configuring how many active robots.txt loading threads may be running on the same time at max */
    public static final String ROBOTS_TXT_THREADS_ACTIVE_MAX       = "robots.txt.MaxActiveThreads";
    /** Default value of the setting configuring how many active robots.txt loading threads may be running on the same time at max */
    public static final int ROBOTS_TXT_THREADS_ACTIVE_MAX_DEFAULT       = 200;

    /** Key of the setting configuring the bluelist file name */
    public static final String LIST_BLUE                = "plasmaBlueList";
    
    /** Default bluelist file name */
    public static final String LIST_BLUE_DEFAULT        = null;
    public static final String LIST_BADWORDS_DEFAULT    = "yacy.badwords";
    public static final String LIST_STOPWORDS_DEFAULT   = "yacy.stopwords";

    /**
     * <p><code>public static final String <strong>HTCACHE_PATH</strong> = "proxyCache"</code></p>
     * <p>Name of the setting specifying the folder beginning from the YaCy-installation's top-folder, where all
     * downloaded webpages and their respective ressources and HTTP-headers are stored. It is the location containing
     * the proxy-cache</p>
     */
    public static final String HTCACHE_PATH             = "proxyCache";
    public static final String HTCACHE_PATH_DEFAULT     = "DATA/HTCACHE";
    
    /** Key of the setting configuring the cache synchronization  */
    public static final String HTCACHE_COMPRESSION_LEVEL   = "proxyCache.compressionLevel";
    
    /** Default compression level for cached content */
    public static final int HTCACHE_COMPRESSION_LEVEL_DEFAULT = Deflater.BEST_COMPRESSION;
    
    /** Key of the setting configuring Cache synchronization lock timeout on getContent/store operations*/
    public static final String HTCACHE_SYNC_LOCK_TIMEOUT   = "proxyCache.sync.lockTimeout";
    
    /** Default timeout value (in milliseconds) for acquiring a synchronization lock on getContent/store Cache operations */
    public static final long HTCACHE_SYNC_LOCK_TIMEOUT_DEFAULT = 2000;
    
    public static final String RELEASE_PATH             = "releases";
    public static final String RELEASE_PATH_DEFAULT     = "DATA/RELEASE";

    public static final String SURROGATES_IN_PATH          = "surrogates.in";
    public static final String SURROGATES_IN_PATH_DEFAULT  = "DATA/SURROGATES/in";
    public static final String SURROGATES_OUT_PATH         = "surrogates.out";
    public static final String SURROGATES_OUT_PATH_DEFAULT = "DATA/SURROGATES/out";

    public static final String DICTIONARY_SOURCE_PATH         = "dictionaries";
    public static final String DICTIONARY_SOURCE_PATH_DEFAULT = "DATA/DICTIONARIES";
    
    /** Setting key for a set of comma separated vocabulary names whose terms should only be matched 
    * from linked data types annotations in documents (with microdata, RDFa, microformats...) 
    * instead of cleartext words */
    public static final String VOCABULARIES_MATCH_LINKED_DATA_NAMES = "vocabularies.matchLinkedData.names";
    
    public static final String CLASSIFICATION_SOURCE_PATH         = "classification";
    public static final String CLASSIFICATION_SOURCE_PATH_DEFAULT = "DATA/CLASSIFICATION";

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
    public static final String INDEX_PRIMARY_PATH       = "indexPrimaryPath"; // this is a relative path to the application root or an absolute path
    public static final String INDEX_PATH_DEFAULT       = "DATA/INDEX";
    public static final String INDEX_ARCHIVE_PATH       = "indexArchivePath"; // this is a relative path to the application root or an absolute path
    public static final String INDEX_ARCHIVE_DEFAULT    = "DATA/ARCHIVE";
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
     */
    public static final String WORK_PATH                = "workPath";
    public static final String WORK_PATH_DEFAULT        = "DATA/WORK";
    
    /** Setting key of the property that collects the names of all servlets that have been used so far. */
    public static final String SERVER_SERVLETS_CALLED    = "server.servlets.called";
    
    /** Key of the setting controlling whether HTTP responses should be compressed with gzip when the user-agent accepts it (by including gzip in a 'Accept-Encoding' HTTP request header) */
    public static final String SERVER_RESPONSE_COMPRESS_GZIP = "server.response.compress.gzip";
    
    /** Default setting value controlling whether HTTP responses should be compressed */
    public static final boolean SERVER_RESPONSE_COMPRESS_GZIP_DEFAULT = true;
    
    
    /** Key of the setting controlling the maximum number of simultaneously open outgoing HTTP connections in the general pool (net.yacy.cora.protocol.http.HTTPClient) */
    public static final String HTTP_OUTGOING_POOL_GENERAL_MAX_TOTAL = "http.outgoing.pool.general.maxTotal";
    
    /** Default setting value controlling the maximum number of simultaneously open outgoing HTTP connections in the general pool */
    public static final int HTTP_OUTGOING_POOL_GENERAL_MAX_TOTAL_DEFAULT = 200;
    
    /** Key of the setting controlling the maximum number of simultaneously open outgoing HTTP connections in the remote Solr pool (net.yacy.cora.federate.solr.instance.RemoteInstance) */
    public static final String HTTP_OUTGOING_POOL_REMOTE_SOLR_MAX_TOTAL = "http.outgoing.pool.remoteSolr.maxTotal";
    
    /** Default setting value controlling the maximum number of simultaneously open outgoing HTTP connections in the remote Solr pool */
    public static final int HTTP_OUTGOING_POOL_REMOTE_SOLR_MAX_TOTAL_DEFAULT = 100;
    
    /** Key of the setting controlling whether TLS Server Name Indication (SNI) extension is enabled on outgoing HTTP connections in the general http client (net.yacy.cora.protocol.http.HTTPClient) */
    public static final String HTTP_OUTGOING_GENERAL_TLS_SNI_EXTENSION_ENABLED = "http.outgoing.general.tls.sniExtension.enabled";
    
    /** Key of the setting controlling whether TLS Server Name Indication (SNI) extension is enabled on outgoing HTTP connections in the remote Solr http client (net.yacy.cora.federate.solr.instance.RemoteInstance) */
    public static final String HTTP_OUTGOING_REMOTE_SOLR_TLS_SNI_EXTENSION_ENABLED = "http.outgoing.remoteSolr.tls.sniExtension.enabled";
    


    /*
     * ResourceObserver
     * We apply the naming of control circuit states to resources observer limit values (steady-state value, over/undershot)
     * under/overshot states in the system are supposed to be regulated to match the steady-state value
     * ATTENTION: be aware that using the autoregulate-option causes that the search index data is DELETED as soon as threshold-values are reached!
     */
    
    /** Setting key to enable auto-regulation on disk free threshold values */
    public static final String RESOURCE_DISK_FREE_AUTOREGULATE    = "resource.disk.free.autoregulate";
    
    /** Default disk free auto-regulation activation setting */
    public static final boolean RESOURCE_DISK_FREE_AUTOREGULATE_DEFAULT    = false;
    
    /** Setting key for the target steady-state of minimum disk space left */
    public static final String RESOURCE_DISK_FREE_MIN_STEADYSTATE = "resource.disk.free.min.steadystate";
    
    /** Default value for target steady-state of minimum disk space left */
    public static final long RESOURCE_DISK_FREE_MIN_STEADYSTATE_DEFAULT = 2048L;
    
    /** Setting key for the undershot below the steady-state of minimum disk free as absolute size */
    public static final String RESOURCE_DISK_FREE_MIN_UNDERSHOT   = "resource.disk.free.min.undershot";
    
    /** Default value for undershot below the steady-state of minimum disk free as absolute size */
    public static final long RESOURCE_DISK_FREE_MIN_UNDERSHOT_DEFAULT   = 1024L;
    
    /** Setting key to enable auto-regulation on disk used threshold values */
    public static final String RESOURCE_DISK_USED_AUTOREGULATE    = "resource.disk.used.autoregulate";
    
    /** Default disk used auto-regulation activation setting */
    public static final boolean RESOURCE_DISK_USED_AUTOREGULATE_DEFAULT    = false;
    
    /** Setting key for the disk used maximum steady state value */
    public static final String RESOURCE_DISK_USED_MAX_STEADYSTATE = "resource.disk.used.max.steadystate";
    
    /** Default disk used maximum steady state value (in mebibyte)*/
    public static final long RESOURCE_DISK_USED_MAX_STEADYSTATE_DEFAULT = 524288L;
    
    /** Setting key for the disk used hard upper limit value */
    public static final String RESOURCE_DISK_USED_MAX_OVERSHOT    = "resource.disk.used.max.overshot";
    
    /** Default disk used hard upper limit value (in mebibyte) */
    public static final long RESOURCE_DISK_USED_MAX_OVERSHOT_DEFAULT    = 1048576L;
    
    public static final String MEMORY_ACCEPTDHT = "memory.acceptDHTabove"; // minimum memory to accept dht-in (MiB)
    public static final String INDEX_RECEIVE_AUTODISABLED = "memory.disabledDHT"; // set if DHT was disabled by ResourceObserver
    public static final String CRAWLJOB_LOCAL_AUTODISABLED = "memory.disabledLocalCrawler"; // set if local crawl was disabled by ResourceObserver
    public static final String CRAWLJOB_REMOTE_AUTODISABLED = "memory.disabledRemoteCrawler"; // set if remote crawl was disabled by ResourceObserver

    /*
     * Some constants
     */
    public static final String STR_REMOTECRAWLTRIGGER = "REMOTECRAWLTRIGGER: REMOTE CRAWL TO PEER ";

    /**
     * network properties
     *
     */
    public static final String NETWORK_NAME = "network.unit.name";
    public static final String NETWORK_DOMAIN = "network.unit.domain"; // can be filled with: global, local, any
    public static final String NETWORK_DOMAIN_NOCHECK = "network.unit.domain.nocheck";
    public static final String NETWORK_WHITELIST = "network.unit.access.whitelist";
    public static final String NETWORK_BLACKLIST = "network.unit.access.blacklist";
    public static final String NETWORK_BOOTSTRAP_SEEDLIST_STUB = "network.unit.bootstrap.seedlist";

    public static final String NETWORK_SEARCHVERIFY = "network.unit.inspection.searchverify";
    
    /** Key of the setting controlling whether https should be preferred for in-protocol operations when available on remote peers.
     * A distinct general setting is available to control whether https sould be used for remote search queries : see {@link #REMOTESEARCH_HTTPS_PREFERRED} */
    public static final String NETWORK_PROTOCOL_HTTPS_PREFERRED = "network.unit.protocol.https.preferred";
    
    /** Default setting value controlling whether https should be preferred for in-protocol operations when available on remote peers */
    public static final boolean NETWORK_PROTOCOL_HTTPS_PREFERRED_DEFAULT = false;

    /**
     * appearance
     */
    public static final String GREETING              = "promoteSearchPageGreeting";
    public static final String GREETING_NETWORK_NAME = "promoteSearchPageGreeting.useNetworkName";
    public static final String GREETING_HOMEPAGE     = "promoteSearchPageGreeting.homepage";
    public static final String GREETING_LARGE_IMAGE  = "promoteSearchPageGreeting.largeImage";
    public static final String GREETING_SMALL_IMAGE  = "promoteSearchPageGreeting.smallImage";
    public static final String GREETING_IMAGE_ALT    = "promoteSearchPageGreeting.imageAlt";

    /**
     * browser pop up
     */
    public static final String BROWSER_POP_UP_TRIGGER     = "browserPopUpTrigger";
    public static final String BROWSER_POP_UP_PAGE        = "browserPopUpPage";
    public static final String BROWSER_DEFAULT            = "defaultFiles";

    /**
     * forwarder of the index page
     */
    public static final String INDEX_FORWARD        = "indexForward";

    public static final String UPNP_ENABLED			= "upnp.enabled";
    public static final String UPNP_REMOTEHOST		= "upnp.remoteHost";

    public static final String SEARCH_ITEMS   = "search.items";
    public static final String SEARCH_TARGET_DEFAULT  = "search.target";
    public static final String SEARCH_TARGET_SPECIAL          = "search.target.special"; // exceptions to the search target
    public static final String SEARCH_TARGET_SPECIAL_PATTERN  = "search.target.special.pattern"; // ie 'own' addresses in topframe, 'other' in iframe
    public static final String SEARCH_VERIFY  = "search.verify";
    public static final String SEARCH_VERIFY_DELETE = "search.verify.delete";
    
	/**
	 * Key of the setting controlling whether content domain filtering is strict :
	 * when false, results can be extended to documents including links to documents
	 * of contentdom type, whithout being themselves of that type.
	 */
    public static final String SEARCH_STRICT_CONTENT_DOM = "search.strictContentDom";
    
	/** Default setting value controlling whether content domain filtering is strict. */
    public static final boolean SEARCH_STRICT_CONTENT_DOM_DEFAULT = false;
    
    /** Key of the setting controlling whether search results resorting by browser JavaScript is enabled */
    public static final String SEARCH_JS_RESORT = "search.jsresort";
    
    /** Default setting value controlling whether search results resorting by browser JavaScript is enabled */
    public static final boolean SEARCH_JS_RESORT_DEFAULT = false;
    
    /** Key of the setting controlling whether the search public top navigation bar includes a login link/status */
    public static final String SEARCH_PUBLIC_TOP_NAV_BAR_LOGIN = "search.publicTopNavBar.login";
    
    /** Default setting value controlling whether the search public top navigation bar includes a login link/status */
    public static final boolean SEARCH_PUBLIC_TOP_NAV_BAR_LOGIN_DEFAULT = true;

    /** Key of the setting controlling the max lines displayed in standard search navigators/facets */
    public static final String SEARCH_NAVIGATION_MAXCOUNT = "search.navigation.maxcount";
    
    /** Key of the setting controlling the max lines displayed in the dates navigator */
    public static final String SEARCH_NAVIGATION_DATES_MAXCOUNT = "search.navigation.dates.maxcount";
    
    /** Key of the setting controlling whether a noreferrer link type should be added to search result links */
    public static final String SEARCH_RESULT_NOREFERRER = "search.result.noreferrer";
    
    /** Default setting value controlling whether a noreferrer link type should be added to search result links */
    public static final boolean SEARCH_RESULT_NOREFERRER_DEFAULT = false;
    
    /** Key of the setting controlling whether the ranking score value should be displayed for each search result in the HTML results page */
    public static final String SEARCH_RESULT_SHOW_RANKING = "search.result.show.ranking";
    
    /** Default setting value controlling whether the ranking score value should be displayed for each search result in the HTML results page */
    public static final boolean SEARCH_RESULT_SHOW_RANKING_DEFAULT = false;
    
    /** Key of the setting controlling whether a tags/keywords list should be displayed for each search result in the HTML results page */
    public static final String SEARCH_RESULT_SHOW_KEYWORDS = "search.result.show.keywords";
    
    /** Default setting value controlling whether the ranking score value should be displayed for each search result in the HTML results page */
    public static final boolean SEARCH_RESULT_SHOW_KEYWORDS_DEFAULT = false;
    
    /** Key of the setting controlling the maximum number of tags/keywords initially displayed for each search result in the HTML results page (the eventual remaining ones can then be expanded) */
    public static final String SEARCH_RESULT_KEYWORDS_FISRT_MAX_COUNT = "search.result.keywords.firstMaxCount";
    
    /** Default setting value controlling the maximum number of tags/keywords initially displayed for each search result in the HTML results page (the eventual remaining ones can then be expanded) */
    public static final int SEARCH_RESULT_KEYWORDS_FISRT_MAX_COUNT_DEFAULT = 100;
    
    /** Key of the setting controlling whether the eventual website favicon should be fetched and displayed for each search result in the HTML results page */
    public static final String SEARCH_RESULT_SHOW_FAVICON = "search.result.show.favicon";
    
    /** Default setting value controlling whether the eventual website favicon should be fetched and displayed for each search result in the HTML results page */
    public static final boolean SEARCH_RESULT_SHOW_FAVICON_DEFAULT = true;
    
    

    /**
     * ranking+evaluation
     */
    public static final String SEARCH_RANKING_RWI_PROFILE = "search.ranking.rwi.profile"; // old rwi rankingProfile ranking
    public static final String SEARCH_RANKING_SOLR_DOUBLEDETECTION_MINLENGTH = "search.ranking.solr.doubledetection.minlength";
    public static final String SEARCH_RANKING_SOLR_DOUBLEDETECTION_QUANTRATE = "search.ranking.solr.doubledetection.quantrate";

    /**
     * boosts for different cores (add an number to the end of the property name)
     */
    public static final String SEARCH_RANKING_SOLR_COLLECTION_BOOSTNAME_         = "search.ranking.solr.collection.boostname.tmpa."; // temporary until we know best default values; add the index number (0..3) to that string
    public static final String SEARCH_RANKING_SOLR_COLLECTION_BOOSTFIELDS_       = "search.ranking.solr.collection.boostfields.tmpa.";
    public static final String SEARCH_RANKING_SOLR_COLLECTION_FILTERQUERY_       = "search.ranking.solr.collection.filterquery.tmpa.";
    public static final String SEARCH_RANKING_SOLR_COLLECTION_BOOSTQUERY_        = "search.ranking.solr.collection.boostquery.tmpa.";
    public static final String SEARCH_RANKING_SOLR_COLLECTION_BOOSTFUNCTION_     = "search.ranking.solr.collection.boostfunction.tmpb.";
    
    /**
     * system tray
     */
	public static final String TRAY_ICON_ENABLED                   = "tray.icon.enabled";
	public static final String TRAY_ICON_FORCED                    = "tray.icon.force";
	public static final String TRAY_ICON_LABEL                     = "tray.icon.label";
	public static final String TRAY_MENU_ENABLED                   = "tray.menu.enabled";
	
	/*
	 * search heuristics
	 */
    public static final String HEURISTIC_SITE                      = "heuristic.site";
    public static final String HEURISTIC_SEARCHRESULTS             = "heuristic.searchresults";
    public static final String HEURISTIC_SEARCHRESULTS_CRAWLGLOBAL = "heuristic.searchresults.crawlglobal";
    public static final String HEURISTIC_OPENSEARCH                = "heuristic.opensearch";
	
	/*
	 * automatic learning heuristic
	 */
    public static final String GREEDYLEARNING_ENABLED              = "greedylearning.enabled";
    public static final String GREEDYLEARNING_LIMIT_DOCCOUNT       = "greedylearning.limit.doccount";
    public static final String GREEDYLEARNING_ACTIVE               = "greedylearning.active";

    /*
     * Skins
     */
    public static final String SKINS_PATH_DEFAULT                  = "DATA/SKINS";
    
    /*
     * decorations
     */
    public static final String DECORATION_AUDIO                    = "decoration.audio";
    public static final String DECORATION_GRAFICS_LINKSTRUCTURE    = "decoration.grafics.linkstructure";

}
