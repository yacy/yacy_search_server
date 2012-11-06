// plasmaSwitchboard.java
// (C) 2004-2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2004 on http://yacy.net
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

/*
   This class holds the run-time environment of the plasma
   Search Engine. It's data forms a blackboard which can be used
   to organize running jobs around the indexing algorithm.
   The blackboard consist of the following entities:
   - storage: one plasmaStore object with the url-based database
   - configuration: initialized by properties once, then by external functions
   - job queues: for parsing, condensing, indexing
 */

package net.yacy.search;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.solr.common.SolrInputDocument;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.Classification;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.RSSFeed;
import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.document.RSSReader;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.document.WordCache;
import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.federate.solr.connector.ShardSelection;
import net.yacy.cora.federate.solr.connector.ShardSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.federate.yacy.ConfigurationSet;
import net.yacy.cora.lod.JenaTripleStore;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.TimeoutRequest;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.protocol.http.ProxySettings;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.CrawlStacker;
import net.yacy.crawler.CrawlSwitchboard;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.CrawlQueues;
import net.yacy.crawler.data.NoticedURL;
import net.yacy.crawler.data.ResultImages;
import net.yacy.crawler.data.ResultURLs;
import net.yacy.crawler.data.NoticedURL.StackType;
import net.yacy.crawler.data.ResultURLs.EventOrigin;
import net.yacy.crawler.data.ZURL.FailCategory;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.data.BlogBoard;
import net.yacy.data.BlogBoardComments;
import net.yacy.data.BookmarkHelper;
import net.yacy.data.BookmarksDB;
import net.yacy.data.ListManager;
import net.yacy.data.MessageBoard;
import net.yacy.data.URLLicense;
import net.yacy.data.UserDB;
import net.yacy.data.WorkTables;
import net.yacy.data.wiki.WikiBoard;
import net.yacy.data.wiki.WikiCode;
import net.yacy.data.wiki.WikiParser;
import net.yacy.data.ymark.YMarkTables;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.LibraryProvider;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.document.Parser.Failure;
import net.yacy.document.content.DCEntry;
import net.yacy.document.content.SurrogateReader;
import net.yacy.document.importer.OAIListFriendsLoader;
import net.yacy.document.parser.audioTagParser;
import net.yacy.document.parser.html.Evaluation;
import net.yacy.gui.Tray;
import net.yacy.interaction.contentcontrol.ContentControlFilterUpdateThread;
import net.yacy.interaction.contentcontrol.ContentControlImportThread;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.OS;
import net.yacy.kelondro.util.SetTools;
import net.yacy.kelondro.workflow.BusyThread;
import net.yacy.kelondro.workflow.InstantBusyThread;
import net.yacy.kelondro.workflow.WorkflowProcessor;
import net.yacy.kelondro.workflow.WorkflowThread;
import net.yacy.peers.Dispatcher;
import net.yacy.peers.EventChannel;
import net.yacy.peers.Network;
import net.yacy.peers.NewsPool;
import net.yacy.peers.DHTSelection;
import net.yacy.peers.Protocol;
import net.yacy.peers.Seed;
import net.yacy.peers.SeedDB;
import net.yacy.peers.graphics.WebStructureGraph;
import net.yacy.peers.operation.yacyBuildProperties;
import net.yacy.peers.operation.yacyRelease;
import net.yacy.peers.operation.yacyUpdateLocation;
import net.yacy.repository.Blacklist;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.repository.FilterEngine;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.search.index.Segment;
import net.yacy.search.index.SolrConfiguration;
import net.yacy.search.query.AccessTracker;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.ranking.BlockRank;
import net.yacy.search.ranking.RankingProfile;
import net.yacy.search.snippet.TextSnippet;
import net.yacy.server.serverCore;
import net.yacy.server.serverSwitch;
import net.yacy.server.http.RobotsTxtConfig;
import net.yacy.utils.CryptoLib;
import net.yacy.utils.UPnP;
import net.yacy.utils.crypt;

import com.google.common.io.Files;


public final class Switchboard extends serverSwitch {

    // load slots
    public static int xstackCrawlSlots = 2000;
    public static long lastPPMUpdate = System.currentTimeMillis() - 30000;
    private static final int dhtMaxContainerCount = 500;
    private int dhtMaxReferenceCount = 1000;

    // colored list management
    public static SortedSet<String> badwords = new TreeSet<String>(NaturalOrder.naturalComparator);
    public static SortedSet<String> stopwords = new TreeSet<String>(NaturalOrder.naturalComparator);
    public static SortedSet<String> blueList = null;
    public static HandleSet badwordHashes = null;
    public static HandleSet blueListHashes = null;
    public static HandleSet stopwordHashes = null;
    public static Blacklist urlBlacklist = null;

    public static WikiParser wikiParser = null;

    // storage management
    public File htCachePath;
    public final File dictionariesPath;
    public File listsPath;
    public File htDocsPath;
    public File workPath;
    public File releasePath;
    public File networkRoot;
    public File queuesRoot;
    public File surrogatesInPath;
    public File surrogatesOutPath;
    public Segment index;
    public LoaderDispatcher loader;
    public CrawlSwitchboard crawler;
    public CrawlQueues crawlQueues;
    public CrawlStacker crawlStacker;
    public MessageBoard messageDB;
    public WikiBoard wikiDB;
    public BlogBoard blogDB;
    public BlogBoardComments blogCommentDB;
    public RobotsTxt robots;
    public Map<String, Object[]> outgoingCookies, incomingCookies;
    public volatile long proxyLastAccess, localSearchLastAccess, remoteSearchLastAccess;
    public Network yc;
    public ResourceObserver observer;
    public UserDB userDB;
    public BookmarksDB bookmarksDB;
    public WebStructureGraph webStructure;
    public ConcurrentHashMap<String, TreeSet<Long>> localSearchTracker, remoteSearchTracker; // mappings from requesting host to a TreeSet of Long(access time)
    public long indexedPages = 0;
    public int searchQueriesRobinsonFromLocal = 0; // absolute counter of all local queries submitted on this peer from a local or autheticated used
    public int searchQueriesRobinsonFromRemote = 0; // absolute counter of all local queries submitted on this peer from a remote IP without authentication
    public float searchQueriesGlobal = 0f; // partial counter of remote queries (1/number-of-requested-peers)
    public SortedMap<byte[], String> clusterhashes; // map of peerhash(String)/alternative-local-address as ip:port or only ip (String) or null if address in seed should be used
    public URLLicense licensedURLs;
    public List<Pattern> networkWhitelist, networkBlacklist;
    public FilterEngine domainList;
    private Dispatcher dhtDispatcher;
    public LinkedBlockingQueue<String> trail;
    public SeedDB peers;
    public WorkTables tables;
    public Tray tray;

    public WorkflowProcessor<IndexingQueueEntry> indexingDocumentProcessor;
    public WorkflowProcessor<IndexingQueueEntry> indexingCondensementProcessor;
    public WorkflowProcessor<IndexingQueueEntry> indexingAnalysisProcessor;
    public WorkflowProcessor<IndexingQueueEntry> indexingStorageProcessor;

    public RobotsTxtConfig robotstxtConfig = null;
    public boolean useTailCache;
    public boolean exceed134217727;

    private final Semaphore shutdownSync = new Semaphore(0);
    private boolean terminate = false;
    private static Switchboard sb;
    public HashMap<String, Object[]> crawlJobsStatus = new HashMap<String, Object[]>();

    public Switchboard(final File dataPath, final File appPath, final String initPath, final String configPath) throws IOException {
        super(dataPath, appPath, initPath, configPath);
        sb = this;
        // check if port is already occupied
        final int port = getConfigInt("port", 8090);
        try {
            if ( TimeoutRequest.ping(Domains.LOCALHOST, port, 500) ) {
                throw new RuntimeException(
                    "a server is already running on the YaCy port "
                        + port
                        + "; possibly another YaCy process has not terminated yet. Please stop YaCy before running a new instance.");
            }
        } catch ( final ExecutionException e1 ) {
        }

        MemoryTracker.startSystemProfiling();

        // set loglevel and log
        setLog(new Log("SWITCHBOARD"));
        AccessTracker.setDumpFile(new File("DATA/LOG/queries.log"));

        // set default peer name
        Seed.ANON_PREFIX = getConfig("peernameprefix", "_anon");

        // UPnP port mapping
        if ( getConfigBool(SwitchboardConstants.UPNP_ENABLED, false) ) {
            InstantBusyThread.oneTimeJob(UPnP.class, "addPortMapping", 0);
        }

        // init TrayIcon if possible
        this.tray = new Tray(this);

        // remote proxy configuration
        initRemoteProxy();

        // memory configuration
        long tableCachingLimit = getConfigLong("tableCachingLimit", 419430400L);
        if ( MemoryControl.available() > tableCachingLimit ) {
            this.useTailCache = true;
        }
        this.exceed134217727 = getConfigBool("exceed134217727", true);
        if ( MemoryControl.available() > 1024L * 1024L * 1024L * 2L ) {
            this.exceed134217727 = true;
        }

        // load values from configs
        final File indexPath =
            getDataPath(SwitchboardConstants.INDEX_PRIMARY_PATH, SwitchboardConstants.INDEX_PATH_DEFAULT);
        this.log.logConfig("Index Primary Path: " + indexPath.toString());
        this.listsPath =
            getDataPath(SwitchboardConstants.LISTS_PATH, SwitchboardConstants.LISTS_PATH_DEFAULT);
        this.log.logConfig("Lists Path:     " + this.listsPath.toString());
        this.htDocsPath =
            getDataPath(SwitchboardConstants.HTDOCS_PATH, SwitchboardConstants.HTDOCS_PATH_DEFAULT);
        this.log.logConfig("HTDOCS Path:    " + this.htDocsPath.toString());
        this.workPath = getDataPath(SwitchboardConstants.WORK_PATH, SwitchboardConstants.WORK_PATH_DEFAULT);
        this.workPath.mkdirs();
        this.log.logConfig("Work Path:    " + this.workPath.toString());
        this.dictionariesPath =
            getDataPath(
                SwitchboardConstants.DICTIONARY_SOURCE_PATH,
                SwitchboardConstants.DICTIONARY_SOURCE_PATH_DEFAULT);
        this.log.logConfig("Dictionaries Path:" + this.dictionariesPath.toString());

        // init libraries
        this.log.logConfig("initializing libraries");
        new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("LibraryProvider.initialize");
                LibraryProvider.initialize(Switchboard.this.dictionariesPath);
            }
        }.start();

        // init global host name cache
        Domains.init(new File(this.workPath, "globalhosts.list"));

        // init sessionid name file
        final String sessionidNamesFile = getConfig("sessionidNamesFile", "defaults/sessionid.names");
        this.log.logConfig("Loading sessionid file " + sessionidNamesFile);
        MultiProtocolURI.initSessionIDNames(FileUtils.loadList(new File(getAppPath(), sessionidNamesFile)));

        // init tables
        this.tables = new WorkTables(this.workPath);

        // set a high maximum cache size to current size; this is adopted later automatically
        final int wordCacheMaxCount = (int) getConfigLong(SwitchboardConstants.WORDCACHE_MAX_COUNT, 20000);
        setConfig(SwitchboardConstants.WORDCACHE_MAX_COUNT, Integer.toString(wordCacheMaxCount));

        // load the network definition
        overwriteNetworkDefinition();

        // start indexing management
        this.log.logConfig("Starting Indexing Management");
        final String networkName = getConfig(SwitchboardConstants.NETWORK_NAME, "");
        final long fileSizeMax = (OS.isWindows) ? this.getConfigLong("filesize.max.win", Integer.MAX_VALUE) : this.getConfigLong( "filesize.max.other", Integer.MAX_VALUE);
        final int redundancy = (int) this.getConfigLong("network.unit.dhtredundancy.senior", 1);
        final int partitionExponent = (int) this.getConfigLong("network.unit.dht.partitionExponent", 0);
        this.networkRoot = new File(new File(indexPath, networkName), "NETWORK");
        this.queuesRoot = new File(new File(indexPath, networkName), "QUEUES");
        this.networkRoot.mkdirs();
        this.queuesRoot.mkdirs();

        // prepare a solr index profile switch list
        final File solrBackupProfile = new File("defaults/solr.keys.list");
        final String schemename = getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_SCHEMEFILE, "solr.keys.default.list");
        final File solrWorkProfile = new File(getDataPath(), "DATA/SETTINGS/" + schemename);
        if ( !solrWorkProfile.exists() ) {
            Files.copy(solrBackupProfile, solrWorkProfile);
        }
        final boolean solrlazy = getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_LAZY, true);
        final SolrConfiguration backupScheme = new SolrConfiguration(solrBackupProfile, solrlazy);
        final SolrConfiguration solrScheme = new SolrConfiguration(solrWorkProfile, solrlazy);
        // update the working scheme with the backup scheme. This is necessary to include new features.
        // new features are always activated by default (if activated in input-backupScheme)
        solrScheme.fill(backupScheme, true);
        // switch on some fields which are necessary for ranking and faceting
        for (YaCySchema field: new YaCySchema[]{
                YaCySchema.host_s,
                YaCySchema.url_file_ext_s, YaCySchema.last_modified,                        // needed for media search and /date operator
                YaCySchema.url_paths_sxt, YaCySchema.host_organization_s,                   // needed to search in the url
                YaCySchema.inboundlinks_protocol_sxt, YaCySchema.inboundlinks_urlstub_txt,  // needed for HostBrowser
                YaCySchema.outboundlinks_protocol_sxt, YaCySchema.outboundlinks_urlstub_txt // needed to enhance the crawler
            }) {
            ConfigurationSet.Entry entry = solrScheme.get(field.name()); entry.setEnable(true); solrScheme.put(field.name(), entry);
        }
        solrScheme.commit();
        
        // initialize index
        ReferenceContainer.maxReferences = getConfigInt("index.maxReferences", 0);
        final File segmentsPath = new File(new File(indexPath, networkName), "SEGMENTS");
        this.index = new Segment(this.log, new File(segmentsPath, "default"), solrScheme);
        final int connectWithinMs = this.getConfigInt(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_COMMITWITHINMS, 180000);
        if (this.getConfigBool(SwitchboardConstants.CORE_SERVICE_RWI, true)) this.index.connectRWI(wordCacheMaxCount, fileSizeMax);
        if (this.getConfigBool(SwitchboardConstants.CORE_SERVICE_CITATION, true)) this.index.connectCitation(wordCacheMaxCount, fileSizeMax);
        if (this.getConfigBool(SwitchboardConstants.CORE_SERVICE_FULLTEXT, true)) {
            this.index.connectUrlDb(this.useTailCache, this.exceed134217727);
            this.index.fulltext().connectLocalSolr(connectWithinMs);
        }

        // set up the solr interface
        final String solrurls = getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_URL, "http://127.0.0.1:8983/solr");
        final boolean usesolr = getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED, false) & solrurls.length() > 0;

        if (usesolr && solrurls != null && solrurls.length() > 0) {
            try {
                SolrConnector solr = new ShardSolrConnector(
                                solrurls,
                                ShardSelection.Method.MODULO_HOST_MD5,
                                10000, true);
                solr.setCommitWithinMs(connectWithinMs);
                this.index.fulltext().connectRemoteSolr(solr);
            } catch ( final IOException e ) {
                Log.logException(e);
            }
        }

        // initialize network database
        final File mySeedFile = new File(this.networkRoot, SeedDB.DBFILE_OWN_SEED);
        this.peers =
            new SeedDB(
                this.networkRoot,
                "seed.new.heap",
                "seed.old.heap",
                "seed.pot.heap",
                mySeedFile,
                redundancy,
                partitionExponent,
                false,
                this.exceed134217727);

        // load domainList
        try {
            this.domainList = null;
            if ( !getConfig("network.unit.domainlist", "").equals("") ) {
                final Reader r =
                    getConfigFileFromWebOrLocally(getConfig("network.unit.domainlist", ""), getAppPath()
                        .getAbsolutePath(), new File(this.networkRoot, "domainlist.txt"));
                this.domainList = new FilterEngine();
                this.domainList.loadList(new BufferedReader(r), null);
            }
        } catch ( final FileNotFoundException e ) {
            this.log.logSevere("CONFIG: domainlist not found: " + e.getMessage());
        } catch ( final IOException e ) {
            this.log.logSevere("CONFIG: error while retrieving domainlist: " + e.getMessage());
        }

        // create a crawler
        this.crawler = new CrawlSwitchboard(networkName, this.log, this.queuesRoot);

        // start yacy core
        this.log.logConfig("Starting YaCy Protocol Core");
        this.yc = new Network(this);
        InstantBusyThread.oneTimeJob(this, "loadSeedLists", 0);
        //final long startedSeedListAquisition = System.currentTimeMillis();

        // init a DHT transmission dispatcher
        this.dhtDispatcher =
            (this.peers.sizeConnected() == 0) ? null : new Dispatcher(
                this.index,
                this.peers,
                true,
                10000);

        // set up local robots.txt
        this.robotstxtConfig = RobotsTxtConfig.init(this);

        // setting timestamp of last proxy access
        this.proxyLastAccess = System.currentTimeMillis() - 10000;
        this.localSearchLastAccess = System.currentTimeMillis() - 10000;
        this.remoteSearchLastAccess = System.currentTimeMillis() - 10000;
        this.webStructure = new WebStructureGraph(new File(this.queuesRoot, "webStructure.map"));

        // configuring list path
        if ( !(this.listsPath.exists()) ) {
            this.listsPath.mkdirs();
        }

        // load coloured lists
        if ( blueList == null ) {
            // read only once upon first instantiation of this class
            final String f =
                getConfig(SwitchboardConstants.LIST_BLUE, SwitchboardConstants.LIST_BLUE_DEFAULT);
            final File plasmaBlueListFile = new File(f);
            if ( f != null ) {
                blueList = SetTools.loadList(plasmaBlueListFile, NaturalOrder.naturalComparator);
            } else {
                blueList = new TreeSet<String>();
            }
            blueListHashes = Word.words2hashesHandles(blueList);
            this.log.logConfig("loaded blue-list from file "
                + plasmaBlueListFile.getName()
                + ", "
                + blueList.size()
                + " entries, "
                + ppRamString(plasmaBlueListFile.length() / 1024));
        }

        // load blacklist
        this.log.logConfig("Loading blacklist ...");
        final File blacklistsPath =
            getDataPath(SwitchboardConstants.LISTS_PATH, SwitchboardConstants.LISTS_PATH_DEFAULT);
        urlBlacklist = new Blacklist(blacklistsPath);
        ListManager.switchboard = this;
        ListManager.listsPath = blacklistsPath;
        ListManager.reloadBlacklists();

        // load badwords (to filter the topwords)
        if ( badwords == null || badwords.isEmpty() ) {
            final File badwordsFile = new File(appPath, SwitchboardConstants.LIST_BADWORDS_DEFAULT);
            badwords = SetTools.loadList(badwordsFile, NaturalOrder.naturalComparator);
            badwordHashes = Word.words2hashesHandles(badwords);
            this.log.logConfig("loaded badwords from file "
                + badwordsFile.getName()
                + ", "
                + badwords.size()
                + " entries, "
                + ppRamString(badwordsFile.length() / 1024));
        }

        // load stopwords
        if ( stopwords == null || stopwords.isEmpty() ) {
            final File stopwordsFile = new File(appPath, SwitchboardConstants.LIST_STOPWORDS_DEFAULT);
            stopwords = SetTools.loadList(stopwordsFile, NaturalOrder.naturalComparator);
            stopwordHashes = Word.words2hashesHandles(stopwords);
            this.log.logConfig("loaded stopwords from file "
                + stopwordsFile.getName()
                + ", "
                + stopwords.size()
                + " entries, "
                + ppRamString(stopwordsFile.length() / 1024));
        }

        // load ranking from distribution
        final File rankingPath = new File(this.appPath, "ranking/YBR".replace('/', File.separatorChar));
        BlockRank.loadBlockRankTable(rankingPath, 16);

        // load distributed ranking
        // very large memory configurations allow to re-compute a ranking table
        /*
        final File hostIndexFile = new File(this.queuesRoot, "hostIndex.blob");
        if (MemoryControl.available() > 1024 * 1024 * 1024) new Thread() {
            public void run() {
                ReferenceContainerCache<HostReference> hostIndex; // this will get large, more than 0.5 million entries by now
                if (!hostIndexFile.exists()) {
                    hostIndex = BlockRank.collect(Switchboard.this.peers, Switchboard.this.webStructure, Integer.MAX_VALUE);
                    BlockRank.saveHostIndex(hostIndex, hostIndexFile);
                } else {
                    hostIndex = BlockRank.loadHostIndex(hostIndexFile);
                }

                // use an index segment to find hosts for given host hashes
                final String segmentName = getConfig(SwitchboardConstants.SEGMENT_PUBLIC, "default");
                final Segment segment = Switchboard.this.indexSegments.segment(segmentName);
                final MetadataRepository metadata = segment.urlMetadata();
                Map<String,HostStat> hostHashResolver;
                try {
                    hostHashResolver = metadata.domainHashResolver(metadata.domainSampleCollector());
                } catch (final IOException e) {
                    hostHashResolver = new HashMap<String, HostStat>();
                }

                // recursively compute a new ranking table
                Switchboard.this.log.logInfo("BLOCK RANK: computing new ranking tables...");
                BlockRank.ybrTables = BlockRank.evaluate(hostIndex, hostHashResolver, null, 0);
                hostIndex = null; // we don't need that here any more, so free the memory

                // use the web structure and the hostHash resolver to analyse the ranking table
                Switchboard.this.log.logInfo("BLOCK RANK: analysis of " + BlockRank.ybrTables.length + " tables...");
                BlockRank.analyse(Switchboard.this.webStructure, hostHashResolver);
                // store the new table
                Switchboard.this.log.logInfo("BLOCK RANK: storing fresh table...");
                BlockRank.storeBlockRankTable(rankingPath);
            }
        }.start();
        */

        // start a cache manager
        this.log.logConfig("Starting HT Cache Manager");

        // create the cache directory
        this.htCachePath =
            getDataPath(SwitchboardConstants.HTCACHE_PATH, SwitchboardConstants.HTCACHE_PATH_DEFAULT);
        this.log.logInfo("HTCACHE Path = " + this.htCachePath.getAbsolutePath());
        final long maxCacheSize =
            1024L * 1024L * Long.parseLong(getConfig(SwitchboardConstants.PROXY_CACHE_SIZE, "2")); // this is megabyte
        Cache.init(this.htCachePath, this.peers.mySeed().hash, maxCacheSize);

        // create the surrogates directories
        this.surrogatesInPath =
            getDataPath(
                SwitchboardConstants.SURROGATES_IN_PATH,
                SwitchboardConstants.SURROGATES_IN_PATH_DEFAULT);
        this.log.logInfo("surrogates.in Path = " + this.surrogatesInPath.getAbsolutePath());
        this.surrogatesInPath.mkdirs();
        this.surrogatesOutPath =
            getDataPath(
                SwitchboardConstants.SURROGATES_OUT_PATH,
                SwitchboardConstants.SURROGATES_OUT_PATH_DEFAULT);
        this.log.logInfo("surrogates.out Path = " + this.surrogatesOutPath.getAbsolutePath());
        this.surrogatesOutPath.mkdirs();

        // create the release download directory
        this.releasePath =
            getDataPath(SwitchboardConstants.RELEASE_PATH, SwitchboardConstants.RELEASE_PATH_DEFAULT);
        this.releasePath.mkdirs();
        this.log.logInfo("RELEASE Path = " + this.releasePath.getAbsolutePath());

        // starting message board
        initMessages();

        // starting wiki
        initWiki();

        //starting blog
        initBlog();

        // init User DB
        this.log.logConfig("Loading User DB");
        final File userDbFile = new File(getDataPath(), "DATA/SETTINGS/user.heap");
        this.userDB = new UserDB(userDbFile);
        this.log.logConfig("Loaded User DB from file "
            + userDbFile.getName()
            + ", "
            + this.userDB.size()
            + " entries"
            + ", "
            + ppRamString(userDbFile.length() / 1024));

     // init user triplestores
        JenaTripleStore.initPrivateStores();

        // init html parser evaluation scheme
        File parserPropertiesPath = new File("defaults/");
        String[] settingsList = parserPropertiesPath.list();
        for ( final String l : settingsList ) {
            if ( l.startsWith("parser.") && l.endsWith(".properties") ) {
                Evaluation.add(new File(parserPropertiesPath, l));
            }
        }
        parserPropertiesPath = new File(getDataPath(), "DATA/SETTINGS/");
        settingsList = parserPropertiesPath.list();
        for ( final String l : settingsList ) {
            if ( l.startsWith("parser.") && l.endsWith(".properties") ) {
                Evaluation.add(new File(parserPropertiesPath, l));
            }
        }

        // init bookmarks DB: needs more time since this does a DNS lookup for each Bookmark.
        // Can be started concurrently
        new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("Switchboard.initBookmarks");
                try {
                    initBookmarks();
                } catch ( final IOException e ) {
                    Log.logException(e);
                }
            }
        }.start();

        // define a realtime parsable mimetype list
        this.log.logConfig("Parser: Initializing Mime Type deny list");
        
    	final boolean enableAudioTags = getConfigBool("parser.enableAudioTags", false);
        log.logConfig("Parser: parser.enableAudioTags= "+enableAudioTags);
    	final StringBuilder denyExt = new StringBuilder(256);
    	final StringBuilder denyMime = new StringBuilder(256);
    	denyExt.append(getConfig(SwitchboardConstants.PARSER_MIME_DENY, ""));
    	denyMime.append(getConfig(SwitchboardConstants.PARSER_EXTENSIONS_DENY, ""));
    	
    	if (!enableAudioTags) {
    		if(denyExt.length()>0) {
    			denyExt.append(audioTagParser.SEPERATOR);
    		}
    		denyExt.append(audioTagParser.EXTENSIONS);
    		
    		if(denyMime.length()>0) {
    			denyMime.append(audioTagParser.SEPERATOR);
    		}
    		denyMime.append(audioTagParser.MIME_TYPES);
        	
        	setConfig(SwitchboardConstants.PARSER_EXTENSIONS_DENY, denyExt.toString());
        	setConfig(SwitchboardConstants.PARSER_MIME_DENY, denyMime.toString());
        	setConfig("parser.enableAudioTags", true);
        }
                
    	TextParser.setDenyMime(getConfig(SwitchboardConstants.PARSER_MIME_DENY, ""));
        TextParser.setDenyExtension(getConfig(SwitchboardConstants.PARSER_EXTENSIONS_DENY, ""));

        // start a loader
        this.log.logConfig("Starting Crawl Loader");
        this.loader = new LoaderDispatcher(this);
        
        // load the robots.txt db
        this.log.logConfig("Initializing robots.txt DB");
        this.robots = new RobotsTxt(this.tables, this.loader);
        this.log.logConfig("Loaded robots.txt DB: " + this.robots.size() + " entries");

        // load oai tables
        final Map<String, File> oaiFriends =
            OAIListFriendsLoader.loadListFriendsSources(
                new File("defaults/oaiListFriendsSource.xml"),
                getDataPath());
        OAIListFriendsLoader.init(this.loader, oaiFriends);
        this.crawlQueues = new CrawlQueues(this, this.queuesRoot);
        this.crawlQueues.noticeURL.setMinimumDelta(
            getConfigInt("minimumLocalDelta", this.crawlQueues.noticeURL.getMinimumLocalDelta()),
            getConfigInt("minimumGlobalDelta", this.crawlQueues.noticeURL.getMinimumGlobalDelta()));

        /*
         * Creating sync objects and loading status for the crawl jobs
         * a) local crawl
         * b) remote triggered crawl
         * c) global crawl trigger
         */
        this.crawlJobsStatus.put(
            SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL,
            new Object[] {
                new Object(),
                Boolean.valueOf(getConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL + "_isPaused", "false"))
            });
        this.crawlJobsStatus.put(
            SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL,
            new Object[] {
                new Object(),
                Boolean.valueOf(getConfig(
                    SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL + "_isPaused",
                    "false"))
            });
        this.crawlJobsStatus.put(
            SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER,
            new Object[] {
                new Object(),
                Boolean.valueOf(getConfig(
                    SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER + "_isPaused",
                    "false"))
            });

        // init cookie-Monitor
        this.log.logConfig("Starting Cookie Monitor");
        this.outgoingCookies = new ConcurrentHashMap<String, Object[]>();
        this.incomingCookies = new ConcurrentHashMap<String, Object[]>();

        // init search history trackers
        this.localSearchTracker = new ConcurrentHashMap<String, TreeSet<Long>>(); // String:TreeSet - IP:set of Long(accessTime)
        this.remoteSearchTracker = new ConcurrentHashMap<String, TreeSet<Long>>();

        // init messages: clean up message symbol
        final File notifierSource =
            new File(getAppPath(), getConfig(
                SwitchboardConstants.HTROOT_PATH,
                SwitchboardConstants.HTROOT_PATH_DEFAULT) + "/env/grafics/empty.gif");
        final File notifierDest =
            new File(
                getDataPath(SwitchboardConstants.HTDOCS_PATH, SwitchboardConstants.HTDOCS_PATH_DEFAULT),
                "notifier.gif");
        try {
            Files.copy(notifierSource, notifierDest);
        } catch ( final IOException e ) {
        }

        // init nameCacheNoCachingList
        try {
            Domains.setNoCachingPatterns(getConfig(SwitchboardConstants.HTTPC_NAME_CACHE_CACHING_PATTERNS_NO, ""));
        } catch (PatternSyntaxException pse) {
            Log.logSevere("Switchboard", "Invalid regular expression in "
                            + SwitchboardConstants.HTTPC_NAME_CACHE_CACHING_PATTERNS_NO
                            + " property: " + pse.getMessage());
            System.exit(-1);
        }

        // generate snippets cache
        this.log.logConfig("Initializing Snippet Cache");

        // init the wiki
        wikiParser = new WikiCode();

        // initializing the resourceObserver
        InstantBusyThread.oneTimeJob(ResourceObserver.class, "initThread", 0);

        // initializing the stackCrawlThread
        this.crawlStacker =
            new CrawlStacker(
                this.robots,
                this.crawlQueues,
                this.crawler,
                this.index,
                this.peers,
                isIntranetMode(),
                isGlobalMode(),
                this.domainList); // Intranet and Global mode may be both true!

        // possibly switch off localIP check
        Domains.setNoLocalCheck(isAllIPMode());

        // check status of account configuration: when local url crawling is allowed, it is not allowed
        // that an automatic authorization of localhost is done, because in this case crawls from local
        // addresses are blocked to prevent attack szenarios where remote pages contain links to localhost
        // addresses that can steer a YaCy peer
        if ( !getConfigBool("adminAccountForLocalhost", false) ) {
            if ( getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "").startsWith("0000") ) {
                // the password was set automatically with a random value.
                // We must remove that here to prevent that a user cannot log in any more
                setConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "");
                // after this a message must be generated to alert the user to set a new password
                this.log.logInfo("RANDOM PASSWORD REMOVED! User must set a new password");
            }
        }

        // initializing dht chunk generation
        this.dhtMaxReferenceCount = (int) getConfigLong(SwitchboardConstants.INDEX_DIST_CHUNK_SIZE_START, 50);

        // init robinson cluster
        // before we do that, we wait some time until the seed list is loaded.
        this.clusterhashes = this.peers.clusterHashes(getConfig("cluster.peers.yacydomain", ""));

        // deploy blocking threads
        this.indexingStorageProcessor =
            new WorkflowProcessor<IndexingQueueEntry>(
                "storeDocumentIndex",
                "This is the sequencing step of the indexing queue. Files are written as streams, too much councurrency would destroy IO performance. In this process the words are written to the RWI cache, which flushes if it is full.",
                new String[] {
                    "RWI/Cache/Collections"
                },
                this,
                "storeDocumentIndex",
                2,
                null,
                1 /*Math.max(1, WorkflowProcessor.availableCPU / 2)*/);
        this.indexingAnalysisProcessor =
            new WorkflowProcessor<IndexingQueueEntry>(
                "webStructureAnalysis",
                "This just stores the link structure of the document into a web structure database.",
                new String[] {
                    "storeDocumentIndex"
                },
                this,
                "webStructureAnalysis",
                WorkflowProcessor.availableCPU + 1,
                this.indexingStorageProcessor,
                WorkflowProcessor.availableCPU);
        this.indexingCondensementProcessor =
            new WorkflowProcessor<IndexingQueueEntry>(
                "condenseDocument",
                "This does a structural analysis of plain texts: markup of headlines, slicing into phrases (i.e. sentences), markup with position, counting of words, calculation of term frequency.",
                new String[] {
                    "webStructureAnalysis"
                },
                this,
                "condenseDocument",
                WorkflowProcessor.availableCPU + 1,
                this.indexingAnalysisProcessor,
                WorkflowProcessor.availableCPU);
        this.indexingDocumentProcessor =
            new WorkflowProcessor<IndexingQueueEntry>(
                "parseDocument",
                "This does the parsing of the newly loaded documents from the web. The result is not only a plain text document, but also a list of URLs that are embedded into the document. The urls are handed over to the CrawlStacker. This process has two child process queues!",
                new String[] {
                    "condenseDocument", "CrawlStacker"
                },
                this,
                "parseDocument",
                Math.max(20, WorkflowProcessor.availableCPU * 2), // it may happen that this is filled with new files from the search process. That means there should be enough place for two result pages
                this.indexingCondensementProcessor,
                WorkflowProcessor.availableCPU);

        // deploy busy threads
        this.log.logConfig("Starting Threads");
        MemoryControl.gc(10000, "plasmaSwitchboard, help for profiler"); // help for profiler - thq

        deployThread(
            SwitchboardConstants.CLEANUP,
            "Cleanup",
            "simple cleaning process for monitoring information",
            null,
            new InstantBusyThread(
                this,
                SwitchboardConstants.CLEANUP_METHOD_START,
                SwitchboardConstants.CLEANUP_METHOD_JOBCOUNT,
                SwitchboardConstants.CLEANUP_METHOD_FREEMEM,
                60000,
                Long.MAX_VALUE,
                10000,
                Long.MAX_VALUE),
            60000); // all 5 Minutes, wait 1 minute until first run
        deployThread(
            SwitchboardConstants.SURROGATES,
            "Surrogates",
            "A thread that polls the SURROGATES path and puts all Documents in one surroagte file into the indexing queue.",
            null,
            new InstantBusyThread(
                this,
                SwitchboardConstants.SURROGATES_METHOD_START,
                SwitchboardConstants.SURROGATES_METHOD_JOBCOUNT,
                SwitchboardConstants.SURROGATES_METHOD_FREEMEM,
                20000,
                Long.MAX_VALUE,
                0,
                Long.MAX_VALUE),
            10000);
        deployThread(
            SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL,
            "Remote Crawl Job",
            "thread that performes a single crawl/indexing step triggered by a remote peer",
            "/IndexCreateWWWRemoteQueue_p.html",
            new InstantBusyThread(
                this.crawlQueues,
                SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_METHOD_START,
                SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_METHOD_JOBCOUNT,
                SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_METHOD_FREEMEM,
                0,
                Long.MAX_VALUE,
                0,
                Long.MAX_VALUE),
            10000);
        deployThread(
            SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER,
            "Remote Crawl URL Loader",
            "thread that loads remote crawl lists from other peers",
            null,
            new InstantBusyThread(
                this.crawlQueues,
                SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER_METHOD_START,
                SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER_METHOD_JOBCOUNT,
                SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER_METHOD_FREEMEM,
                10000,
                Long.MAX_VALUE,
                10000,
                Long.MAX_VALUE),
            10000); // error here?
        deployThread(
            SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL,
            "Local Crawl",
            "thread that performes a single crawl step from the local crawl queue",
            "/IndexCreateWWWLocalQueue_p.html",
            new InstantBusyThread(
                this.crawlQueues,
                SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_METHOD_START,
                SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_METHOD_JOBCOUNT,
                SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_METHOD_FREEMEM,
                0,
                Long.MAX_VALUE,
                0,
                Long.MAX_VALUE),
            10000);
        deployThread(
            SwitchboardConstants.SEED_UPLOAD,
            "Seed-List Upload",
            "task that a principal peer performes to generate and upload a seed-list to a ftp account",
            null,
            new InstantBusyThread(
                this.yc,
                SwitchboardConstants.SEED_UPLOAD_METHOD_START,
                SwitchboardConstants.SEED_UPLOAD_METHOD_JOBCOUNT,
                SwitchboardConstants.SEED_UPLOAD_METHOD_FREEMEM,
                600000,
                Long.MAX_VALUE,
                300000,
                Long.MAX_VALUE),
            180000);
        deployThread(
            SwitchboardConstants.PEER_PING,
            "YaCy Core",
            "this is the p2p-control and peer-ping task",
            null,
            new InstantBusyThread(
                this.yc,
                SwitchboardConstants.PEER_PING_METHOD_START,
                SwitchboardConstants.PEER_PING_METHOD_JOBCOUNT,
                SwitchboardConstants.PEER_PING_METHOD_FREEMEM,
                30000,
                Long.MAX_VALUE,
                30000,
                Long.MAX_VALUE),
            2000);
        deployThread(
            SwitchboardConstants.INDEX_DIST,
            "DHT Distribution",
            "selection, transfer and deletion of index entries that are not searched on your peer, but on others",
            null,
            new InstantBusyThread(
                this,
                SwitchboardConstants.INDEX_DIST_METHOD_START,
                SwitchboardConstants.INDEX_DIST_METHOD_JOBCOUNT,
                SwitchboardConstants.INDEX_DIST_METHOD_FREEMEM,
                10000,
                Long.MAX_VALUE,
                1000,
                Long.MAX_VALUE),
            5000,
            Long.parseLong(getConfig(SwitchboardConstants.INDEX_DIST_IDLESLEEP, "5000")),
            Long.parseLong(getConfig(SwitchboardConstants.INDEX_DIST_BUSYSLEEP, "0")),
            Long.parseLong(getConfig(SwitchboardConstants.INDEX_DIST_MEMPREREQ, "1000000")));

        // content control: initialize list sync thread
        deployThread(
                "720_ccimport",
                "Content Control Import",
                "this is the content control import thread",
                null,
                new InstantBusyThread(
                    new ContentControlImportThread(this),
                    "run",
                    SwitchboardConstants.PEER_PING_METHOD_JOBCOUNT,
                    SwitchboardConstants.PEER_PING_METHOD_FREEMEM,
                    3000,
                    10000,
                    3000,
                    10000),
                2000);
        deployThread(
                "730_ccfilter",
                "Content Control Filter",
                "this is the content control filter update thread",
                null,
                new InstantBusyThread(
                    new ContentControlFilterUpdateThread(this),
                    "run",
                    SwitchboardConstants.PEER_PING_METHOD_JOBCOUNT,
                    SwitchboardConstants.PEER_PING_METHOD_FREEMEM,
                    3000,
                    10000,
                    3000,
                    10000),
                2000);

        // set network-specific performance attributes
        if ( this.firstInit ) {
            setRemotecrawlPPM(Math.max(1, (int) getConfigLong("network.unit.remotecrawl.speed", 60)));
        }

        // test routine for snippet fetch
        //Set query = new HashSet();
        //query.add(CrawlSwitchboardEntry.word2hash("Weitergabe"));
        //query.add(CrawlSwitchboardEntry.word2hash("Zahl"));
        //plasmaSnippetCache.result scr = snippetCache.retrieve(new URL("http://www.heise.de/mobil/newsticker/meldung/mail/54980"), query, true);
        //plasmaSnippetCache.result scr = snippetCache.retrieve(new URL("http://www.heise.de/security/news/foren/go.shtml?read=1&msg_id=7301419&forum_id=72721"), query, true);
        //plasmaSnippetCache.result scr = snippetCache.retrieve(new URL("http://www.heise.de/kiosk/archiv/ct/2003/4/20"), query, true, 260);

        this.trail = new LinkedBlockingQueue<String>();

        this.log.logConfig("Finished Switchboard Initialization");
    }

    public int getIndexingProcessorsQueueSize() {
        return this.indexingDocumentProcessor.queueSize()
            + this.indexingCondensementProcessor.queueSize()
            + this.indexingAnalysisProcessor.queueSize()
            + this.indexingStorageProcessor.queueSize();
    }

    public void overwriteNetworkDefinition() throws FileNotFoundException, IOException {

        // load network configuration into settings
        String networkUnitDefinition =
            getConfig("network.unit.definition", "defaults/yacy.network.freeworld.unit");
        if (networkUnitDefinition.isEmpty()) networkUnitDefinition = "defaults/yacy.network.freeworld.unit"; // patch for a strange failure case where the path was overwritten by empty string

        // patch old values
        if ( networkUnitDefinition.equals("yacy.network.unit") ) {
            networkUnitDefinition = "defaults/yacy.network.freeworld.unit";
            setConfig("network.unit.definition", networkUnitDefinition);
        }

        // remove old release and bootstrap locations
        final Iterator<String> ki = configKeys();
        final ArrayList<String> d = new ArrayList<String>();
        String k;
        while ( ki.hasNext() ) {
            k = ki.next();
            if ( k.startsWith("network.unit.update.location") || k.startsWith("network.unit.bootstrap") ) {
                d.add(k);
            }
        }
        for ( final String s : d ) {
            removeConfig(s); // must be removed afterwards otherwise a ki.remove() would not remove the property on file
        }

        // include additional network definition properties into our settings
        // note that these properties cannot be set in the application because they are
        // _always_ overwritten each time with the default values. This is done so on purpose.
        // the network definition should be made either consistent for all peers,
        // or independently using a bootstrap URL
        Map<String, String> initProps;
        final Reader netDefReader =
            getConfigFileFromWebOrLocally(networkUnitDefinition, getAppPath().getAbsolutePath(), new File(
                this.workPath,
                "network.definition.backup"));
        initProps = FileUtils.table(netDefReader);
        setConfig(initProps);

        // set release locations
        int i = 0;
        CryptoLib cryptoLib;
        try {
            cryptoLib = new CryptoLib();
            while ( true ) {
                final String location = getConfig("network.unit.update.location" + i, "");
                if ( location.isEmpty() ) {
                    break;
                }
                DigestURI locationURL;
                try {
                    // try to parse url
                    locationURL = new DigestURI(location);
                } catch ( final MalformedURLException e ) {
                    break;
                }
                PublicKey publicKey = null;
                // get public key if it's in config
                try {
                    final String publicKeyString =
                        getConfig("network.unit.update.location" + i + ".key", null);
                    if ( publicKeyString != null ) {
                        final byte[] publicKeyBytes =
                            Base64Order.standardCoder.decode(publicKeyString.trim());
                        publicKey = cryptoLib.getPublicKeyFromBytes(publicKeyBytes);
                    }
                } catch ( final InvalidKeySpecException e ) {
                    Log.logException(e);
                }
                final yacyUpdateLocation updateLocation = new yacyUpdateLocation(locationURL, publicKey);
                yacyRelease.latestReleaseLocations.add(updateLocation);
                i++;
            }
        } catch ( final NoSuchAlgorithmException e1 ) {
            // TODO Auto-generated catch block
            Log.logException(e1);
        }

        // initiate url license object
        this.licensedURLs = new URLLicense(8);

        // set white/blacklists
        this.networkWhitelist = Domains.makePatterns(getConfig(SwitchboardConstants.NETWORK_WHITELIST, ""));
        this.networkBlacklist = Domains.makePatterns(getConfig(SwitchboardConstants.NETWORK_BLACKLIST, ""));

        /*
        // in intranet and portal network set robinson mode
        if (networkUnitDefinition.equals("defaults/yacy.network.webportal.unit") ||
            networkUnitDefinition.equals("defaults/yacy.network.intranet.unit")) {
            // switch to robinson mode
            setConfig("crawlResponse", "false");
            setConfig(plasmaSwitchboardConstants.INDEX_DIST_ALLOW, false);
            setConfig(plasmaSwitchboardConstants.INDEX_RECEIVE_ALLOW, false);
        }

        // in freeworld network set full p2p mode
        if (networkUnitDefinition.equals("defaults/yacy.network.freeworld.unit")) {
            // switch to robinson mode
            setConfig("crawlResponse", "true");
            setConfig(plasmaSwitchboardConstants.INDEX_DIST_ALLOW, true);
            setConfig(plasmaSwitchboardConstants.INDEX_RECEIVE_ALLOW, true);
        }
        */
        // write the YaCy network identification inside the yacybot client user agent to distinguish networks
        String newagent =
            ClientIdentification.generateYaCyBot(getConfig(SwitchboardConstants.NETWORK_NAME, "")
                + (isRobinsonMode() ? "-" : "/")
                + getConfig(SwitchboardConstants.NETWORK_DOMAIN, "global"));
        if ( !getConfigBool(SwitchboardConstants.DHT_ENABLED, false)
            && getConfig("network.unit.tenant.agent", "").length() > 0 ) {
            newagent = getConfig("network.unit.tenant.agent", "").trim();
            this.log.logInfo("new user agent: '" + newagent + "'");
        }
        ClientIdentification.setUserAgent(newagent);
    }

    public void switchNetwork(final String networkDefinition) throws FileNotFoundException, IOException {
        this.log.logInfo("SWITCH NETWORK: switching to '" + networkDefinition + "'");
        // pause crawls
        final boolean lcp = crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
        if ( !lcp ) {
            pauseCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL, "network switch to " + networkDefinition);
        }
        final boolean rcp = crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
        if ( !rcp ) {
            pauseCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL, "network switch to " + networkDefinition);
        }
        // trigger online caution
        this.proxyLastAccess = System.currentTimeMillis() + 3000; // at least 3 seconds online caution to prevent unnecessary action on database meanwhile
        this.log.logInfo("SWITCH NETWORK: SHUT DOWN OF OLD INDEX DATABASE...");
        // clean search events which have cached relations to the old index
        SearchEventCache.cleanupEvents(true);

        // switch the networks
        synchronized ( this ) {

            // remember the solr scheme
            SolrConfiguration solrScheme = this.index.fulltext().getSolrScheme();

            // shut down
            this.crawler.close();
            if ( this.dhtDispatcher != null ) {
                this.dhtDispatcher.close();
            }
            synchronized ( this.index ) {
                this.index.close();
            }
            this.crawlStacker.announceClose();
            this.crawlStacker.close();
            this.webStructure.close();

            this.log.logInfo("SWITCH NETWORK: START UP OF NEW INDEX DATABASE...");

            // new properties
            setConfig("network.unit.definition", networkDefinition);
            overwriteNetworkDefinition();
            final File indexPrimaryPath =
                getDataPath(SwitchboardConstants.INDEX_PRIMARY_PATH, SwitchboardConstants.INDEX_PATH_DEFAULT);
            final int wordCacheMaxCount =
                (int) getConfigLong(SwitchboardConstants.WORDCACHE_MAX_COUNT, 20000);
            final long fileSizeMax =
                (OS.isWindows) ? this.getConfigLong("filesize.max.win", Integer.MAX_VALUE) : this.getConfigLong("filesize.max.other", Integer.MAX_VALUE);
            final int redundancy = (int) this.getConfigLong("network.unit.dhtredundancy.senior", 1);
            final int partitionExponent = (int) this.getConfigLong("network.unit.dht.partitionExponent", 0);
            final String networkName = getConfig(SwitchboardConstants.NETWORK_NAME, "");
            this.networkRoot = new File(new File(indexPrimaryPath, networkName), "NETWORK");
            this.queuesRoot = new File(new File(indexPrimaryPath, networkName), "QUEUES");
            this.networkRoot.mkdirs();
            this.queuesRoot.mkdirs();

            // clear statistic data
            ResultURLs.clearStacks();

            // remove heuristics
            setConfig("heuristic.site", false);
            setConfig("heuristic.blekko", false);
            setConfig("heuristic.twitter", false);

            // relocate
            this.peers.relocate(
                this.networkRoot,
                redundancy,
                partitionExponent,
                this.useTailCache,
                this.exceed134217727);
            this.index = new Segment(this.log, new File(new File(new File(indexPrimaryPath, networkName), "SEGMENTS"), "default"), solrScheme);
            final int connectWithinMs = this.getConfigInt(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_COMMITWITHINMS, 180000);
            if (this.getConfigBool(SwitchboardConstants.CORE_SERVICE_RWI, true)) this.index.connectRWI(wordCacheMaxCount, fileSizeMax);
            if (this.getConfigBool(SwitchboardConstants.CORE_SERVICE_CITATION, true)) this.index.connectCitation(wordCacheMaxCount, fileSizeMax);
            if (this.getConfigBool(SwitchboardConstants.CORE_SERVICE_FULLTEXT, true)) {
                this.index.fulltext().connectLocalSolr(connectWithinMs);
                this.index.connectUrlDb(this.useTailCache, this.exceed134217727);
            }

            // set up the solr interface
            final String solrurls = getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_URL, "http://127.0.0.1:8983/solr");
            final boolean usesolr = getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED, false) & solrurls.length() > 0;

            if (usesolr && solrurls != null && solrurls.length() > 0) {
                try {
                    SolrConnector solr = new ShardSolrConnector(
                                    solrurls,
                                    ShardSelection.Method.MODULO_HOST_MD5,
                                    10000, true);
                    solr.setCommitWithinMs(connectWithinMs);
                    this.index.fulltext().connectRemoteSolr(solr);
                } catch ( final IOException e ) {
                    Log.logException(e);
                }
            }

            // create a crawler
            this.crawlQueues.relocate(this.queuesRoot); // cannot be closed because the busy threads are working with that object
            this.crawler = new CrawlSwitchboard(networkName, this.log, this.queuesRoot);

            // init a DHT transmission dispatcher
            this.dhtDispatcher =
                (this.peers.sizeConnected() == 0) ? null : new Dispatcher(
                    this.index,
                    this.peers,
                    true,
                    10000);

            // create new web structure
            this.webStructure = new WebStructureGraph(new File(this.queuesRoot, "webStructure.map"));

            // load domainList
            try {
                this.domainList = null;
                if ( !getConfig("network.unit.domainlist", "").equals("") ) {
                    final Reader r =
                        getConfigFileFromWebOrLocally(getConfig("network.unit.domainlist", ""), getAppPath()
                            .getAbsolutePath(), new File(this.networkRoot, "domainlist.txt"));
                    this.domainList = new FilterEngine();
                    this.domainList.loadList(new BufferedReader(r), null);
                }
            } catch ( final FileNotFoundException e ) {
                this.log.logSevere("CONFIG: domainlist not found: " + e.getMessage());
            } catch ( final IOException e ) {
                this.log.logSevere("CONFIG: error while retrieving domainlist: " + e.getMessage());
            }

            this.crawlStacker =
                new CrawlStacker(
                    this.robots,
                    this.crawlQueues,
                    this.crawler,
                    this.index,
                    this.peers,
                    "local.any".indexOf(getConfig(SwitchboardConstants.NETWORK_DOMAIN, "global")) >= 0,
                    "global.any".indexOf(getConfig(SwitchboardConstants.NETWORK_DOMAIN, "global")) >= 0,
                    this.domainList);

        }
        Domains.setNoLocalCheck(isAllIPMode()); // possibly switch off localIP check

        // start up crawl jobs
        continueCrawlJob(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
        continueCrawlJob(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
        this.log
            .logInfo("SWITCH NETWORK: FINISHED START UP, new network is now '" + networkDefinition + "'.");

        // set the network-specific remote crawl ppm
        setRemotecrawlPPM(Math.max(1, (int) getConfigLong("network.unit.remotecrawl.speed", 60)));
    }

    public void setRemotecrawlPPM(final int ppm) {
        final long newBusySleep = Math.max(100, 60000 / ppm);

        // propagate to crawler
        final BusyThread rct = getThread(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
        setConfig(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP, newBusySleep);
        setConfig(
            SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_IDLESLEEP,
            Math.min(10000, newBusySleep * 10));
        rct.setBusySleep(getConfigLong(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP, 1000));
        rct
            .setIdleSleep(getConfigLong(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_IDLESLEEP, 10000));

        // propagate to loader
        final BusyThread rcl = getThread(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER);
        setConfig(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER_BUSYSLEEP, newBusySleep * 4);
        setConfig(
            SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER_IDLESLEEP,
            Math.min(10000, newBusySleep * 20));
        rcl.setBusySleep(getConfigLong(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER_BUSYSLEEP, 1000));
        rcl.setIdleSleep(getConfigLong(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER_IDLESLEEP, 10000));
    }

    public void initMessages() throws IOException {
        this.log.logConfig("Starting Message Board");
        final File messageDbFile = new File(this.workPath, "message.heap");
        this.messageDB = new MessageBoard(messageDbFile);
        this.log.logConfig("Loaded Message Board DB from file "
            + messageDbFile.getName()
            + ", "
            + this.messageDB.size()
            + " entries"
            + ", "
            + ppRamString(messageDbFile.length() / 1024));
    }

    public void initWiki() throws IOException {
        this.log.logConfig("Starting Wiki Board");
        final File wikiDbFile = new File(this.workPath, "wiki.heap");
        this.wikiDB = new WikiBoard(wikiDbFile, new File(this.workPath, "wiki-bkp.heap"));
        this.log.logConfig("Loaded Wiki Board DB from file "
            + wikiDbFile.getName()
            + ", "
            + this.wikiDB.size()
            + " entries"
            + ", "
            + ppRamString(wikiDbFile.length() / 1024));
    }

    public void initBlog() throws IOException {
        this.log.logConfig("Starting Blog");
        final File blogDbFile = new File(this.workPath, "blog.heap");
        this.blogDB = new BlogBoard(blogDbFile);
        this.log.logConfig("Loaded Blog DB from file "
            + blogDbFile.getName()
            + ", "
            + this.blogDB.size()
            + " entries"
            + ", "
            + ppRamString(blogDbFile.length() / 1024));

        final File blogCommentDbFile = new File(this.workPath, "blogComment.heap");
        this.blogCommentDB = new BlogBoardComments(blogCommentDbFile);
        this.log.logConfig("Loaded Blog-Comment DB from file "
            + blogCommentDbFile.getName()
            + ", "
            + this.blogCommentDB.size()
            + " entries"
            + ", "
            + ppRamString(blogCommentDbFile.length() / 1024));
    }

    public void initBookmarks() throws IOException {
        this.log.logConfig("Loading Bookmarks DB");
        final File bookmarksFile = new File(this.workPath, "bookmarks.heap");
        final File tagsFile = new File(this.workPath, "bookmarkTags.heap");
        final File datesFile = new File(this.workPath, "bookmarkDates.heap");
        tagsFile.delete();
        this.bookmarksDB = new BookmarksDB(bookmarksFile, datesFile);
        this.log.logConfig("Loaded Bookmarks DB from files "
            + bookmarksFile.getName()
            + ", "
            + tagsFile.getName());
        this.log.logConfig(this.bookmarksDB.tagsSize()
            + " Tag, "
            + this.bookmarksDB.bookmarksSize()
            + " Bookmarks");
    }

    public static Switchboard getSwitchboard() {
        return sb;
    }

    public boolean isIntranetMode() {
        return "local.any".indexOf(getConfig(SwitchboardConstants.NETWORK_DOMAIN, "global")) >= 0;
    }

    public boolean isGlobalMode() {
        return "global.any".indexOf(getConfig(SwitchboardConstants.NETWORK_DOMAIN, "global")) >= 0;
    }

    public boolean isAllIPMode() {
        return "any".indexOf(getConfig(SwitchboardConstants.NETWORK_DOMAIN, "global")) >= 0;
    }

    /**
     * in nocheck mode the isLocal property is not checked to omit DNS lookup. Can only be done in allip mode
     *
     * @return
     */
    public boolean isIPNoCheckMode() {
        return isAllIPMode() && getConfigBool(SwitchboardConstants.NETWORK_DOMAIN_NOCHECK, false);
    }

    public boolean isRobinsonMode() {
        // we are in robinson mode, if we do not exchange index by dht distribution
        // we need to take care that search requests and remote indexing requests go only
        // to the peers in the same cluster, if we run a robinson cluster.
        return (this.peers != null && this.peers.sizeConnected() == 0)
            || (!getConfigBool(SwitchboardConstants.INDEX_DIST_ALLOW, false) && !getConfigBool(
                SwitchboardConstants.INDEX_RECEIVE_ALLOW,
                false));
    }

    public boolean isPublicRobinson() {
        // robinson peers may be member of robinson clusters, which can be public or private
        // this does not check the robinson attribute, only the specific subtype of the cluster
        final String clustermode =
            getConfig(SwitchboardConstants.CLUSTER_MODE, SwitchboardConstants.CLUSTER_MODE_PUBLIC_PEER);
        return (clustermode.equals(SwitchboardConstants.CLUSTER_MODE_PUBLIC_CLUSTER))
            || (clustermode.equals(SwitchboardConstants.CLUSTER_MODE_PUBLIC_PEER));
    }

    public boolean isInMyCluster(final String peer) {
        // check if the given peer is in the own network, if this is a robinson cluster
        // depending on the robinson cluster type, the peer String may be a peerhash (b64-hash)
        // or a ip:port String or simply a ip String
        // if this robinson mode does not define a cluster membership, false is returned
        if ( peer == null ) {
            return false;
        }
        if ( !isRobinsonMode() ) {
            return false;
        }
        final String clustermode =
            getConfig(SwitchboardConstants.CLUSTER_MODE, SwitchboardConstants.CLUSTER_MODE_PUBLIC_PEER);
        if ( clustermode.equals(SwitchboardConstants.CLUSTER_MODE_PUBLIC_CLUSTER) ) {
            // check if we got the request from a peer in the public cluster
            return this.clusterhashes.containsKey(ASCII.getBytes(peer));
        }
        return false;
    }

    public boolean isInMyCluster(final Seed seed) {
        // check if the given peer is in the own network, if this is a robinson cluster
        // if this robinson mode does not define a cluster membership, false is returned
        if ( seed == null ) {
            return false;
        }
        if ( !isRobinsonMode() ) {
            return false;
        }
        final String clustermode =
            getConfig(SwitchboardConstants.CLUSTER_MODE, SwitchboardConstants.CLUSTER_MODE_PUBLIC_PEER);
        if ( clustermode.equals(SwitchboardConstants.CLUSTER_MODE_PUBLIC_CLUSTER) ) {
            // check if we got the request from a peer in the public cluster
            return this.clusterhashes.containsKey(ASCII.getBytes(seed.hash));
        }
        return false;
    }

    public String urlExists(final byte[] hash) {
        // tests if hash occurrs in any database
        // if it exists, the name of the database is returned,
        // if it not exists, null is returned
        if (this.index.exists(hash)) return "loaded";
        return this.crawlQueues.urlExists(hash);
    }

    public void urlRemove(final Segment segment, final byte[] hash) {
        segment.fulltext().remove(hash);
        ResultURLs.remove(ASCII.String(hash));
        this.crawlQueues.urlRemove(hash);
    }

    public DigestURI getURL(final byte[] urlhash) {
        if ( urlhash == null ) {
            return null;
        }
        if ( urlhash.length == 0 ) {
            return null;
        }
        final URIMetadataNode le = this.index.fulltext().getMetadata(urlhash);
        if ( le != null ) {
            return le.url();
        }
        return this.crawlQueues.getURL(urlhash);
    }

    public RankingProfile getRanking() {
        return (getConfig("rankingProfile", "").isEmpty())
            ? new RankingProfile(Classification.ContentDomain.TEXT)
            : new RankingProfile("", crypt.simpleDecode(this.getConfig("rankingProfile", "")));
    }

    /**
     * checks if the proxy, the local search or remote search was accessed some time before If no limit is
     * exceeded, null is returned. If a limit is exceeded, then the name of the service that caused the
     * caution is returned
     *
     * @return
     */
    public String onlineCaution() {
        if ( System.currentTimeMillis() - this.proxyLastAccess < Integer.parseInt(getConfig(
            SwitchboardConstants.PROXY_ONLINE_CAUTION_DELAY,
            "100")) ) {
            return "proxy";
        }
        if ( System.currentTimeMillis() - this.localSearchLastAccess < Integer.parseInt(getConfig(
            SwitchboardConstants.LOCALSEACH_ONLINE_CAUTION_DELAY,
            "1000")) ) {
            return "localsearch";
        }
        if ( System.currentTimeMillis() - this.remoteSearchLastAccess < Integer.parseInt(getConfig(
            SwitchboardConstants.REMOTESEARCH_ONLINE_CAUTION_DELAY,
            "500")) ) {
            return "remotesearch";
        }
        return null;
    }

    /**
     * Creates a human readable string from a number which represents the size of a file. The method has a
     * side effect: It changes the input. Since the method is private and only used in this class for values
     * which are not used later, this should be OK in this case, but one should never use this method without
     * thinking about the side effect. [MN]
     *
     * @param bytes the length of a file
     * @return the length of a file in human readable form
     */
    private static String ppRamString(long bytes) {
        if ( bytes < 1024 ) {
            return bytes + " KByte";
        }
        bytes = bytes / 1024;
        if ( bytes < 1024 ) {
            return bytes + " MByte";
        }
        bytes = bytes / 1024;
        if ( bytes < 1024 ) {
            return bytes + " GByte";
        }
        return (bytes / 1024) + "TByte";
    }

    /**
     * {@link CrawlProfiles Crawl Profiles} are saved independently from the queues themselves and therefore
     * have to be cleaned up from time to time. This method only performs the clean-up if - and only if - the
     * {@link IndexingStack switchboard}, {@link LoaderDispatcher loader} and {@link plasmaCrawlNURL local
     * crawl} queues are all empty.
     * <p>
     * Then it iterates through all existing {@link CrawlProfiles crawl profiles} and removes all profiles
     * which are not hard-coded.
     * </p>
     * <p>
     * <i>If this method encounters DB-failures, the profile DB will be reseted and</i> <code>true</code><i>
     * will be returned</i>
     * </p>
     *
     * @see #CRAWL_PROFILE_PROXY hardcoded
     * @see #CRAWL_PROFILE_REMOTE hardcoded
     * @see #CRAWL_PROFILE_SNIPPET_TEXT hardcoded
     * @see #CRAWL_PROFILE_SNIPPET_MEDIA hardcoded
     * @return whether this method has done something or not (i.e. because the queues have been filled or
     *         there are no profiles left to clean up)
     * @throws <b>InterruptedException</b> if the current thread has been interrupted, i.e. by the shutdown
     *         procedure
     */
    public boolean cleanProfiles() throws InterruptedException {
        if (getIndexingProcessorsQueueSize() > 0 ||
            this.crawlQueues.workerSize() > 0 ||
            this.crawlQueues.coreCrawlJobSize() > 0 ||
            this.crawlQueues.limitCrawlJobSize() > 0 ||
            this.crawlQueues.remoteTriggeredCrawlJobSize() > 0 ||
            this.crawlQueues.noloadCrawlJobSize() > 0 ||
            (this.crawlStacker != null && !this.crawlStacker.isEmpty()) ||
            this.crawlQueues.noticeURL.notEmpty()) {
            return false;
        }
        return this.crawler.clear();
    }

    public synchronized void close() {
        this.log.logConfig("SWITCHBOARD SHUTDOWN STEP 1: sending termination signal to managed threads:");
        MemoryTracker.stopSystemProfiling();
        terminateAllThreads(true);
        net.yacy.gui.framework.Switchboard.shutdown();
        this.log.logConfig("SWITCHBOARD SHUTDOWN STEP 2: sending termination signal to threaded indexing");
        // closing all still running db importer jobs
        this.crawlStacker.announceClose();
        this.crawlStacker.close();
        this.indexingDocumentProcessor.shutdown();
        this.indexingCondensementProcessor.shutdown();
        this.indexingAnalysisProcessor.shutdown();
        this.indexingStorageProcessor.shutdown();
        if ( this.dhtDispatcher != null ) {
            this.dhtDispatcher.close();
        }
//        de.anomic.http.client.Client.closeAllConnections();
        this.wikiDB.close();
        this.blogDB.close();
        this.blogCommentDB.close();
        this.userDB.close();
        if ( this.bookmarksDB != null ) {
            this.bookmarksDB.close(); // may null if concurrent initialization was not finished
        }
        this.messageDB.close();
        this.webStructure.close();
        this.crawlQueues.close();
        this.crawler.close();
        this.log.logConfig("SWITCHBOARD SHUTDOWN STEP 3: sending termination signal to database manager (stand by...)");
        this.index.close();
        this.peers.close();
        Cache.close();
        this.tables.close();
        Domains.close();
        AccessTracker.dumpLog();
        Switchboard.urlBlacklist.close();
        UPnP.deletePortMapping();
        this.tray.remove();
        try {
            HTTPClient.closeConnectionManager();
        } catch ( final InterruptedException e ) {
            Log.logException(e);
        }
        this.log.logConfig("SWITCHBOARD SHUTDOWN TERMINATED");
    }

    /**
     * pass a response to the indexer
     *
     * @param response
     * @return null if successful, an error message otherwise
     */
    public String toIndexer(final Response response) {
        assert response != null;

        // get next queue entry and start a queue processing
        if ( response == null ) {
            if ( this.log.isFine() ) {
                this.log.logFine("deQueue: queue entry is null");
            }
            return "queue entry is null";
        }
        if ( response.profile() == null ) {
            if ( this.log.isFine() ) {
                this.log.logFine("deQueue: profile is null");
            }
            return "profile is null";
        }

        // check if the document should be indexed based on proxy/crawler rules
        String noIndexReason = "unspecified indexing error";
        if ( response.processCase(this.peers.mySeed().hash) == EventOrigin.PROXY_LOAD ) {
            // proxy-load
            noIndexReason = response.shallIndexCacheForProxy();
        } else {
            // normal crawling
            noIndexReason = response.shallIndexCacheForCrawler();
        }

        // check if the parser supports the mime type
        if ( noIndexReason == null ) {
            noIndexReason = TextParser.supports(response.url(), response.getMimeType());
        }

        // check X-YACY-Index-Control
        // With the X-YACY-Index-Control header set to "no-index" a client could disallow
        // yacy to index the response returned as answer to a request
        if ( noIndexReason == null && response.requestProhibitsIndexing() ) {
            noIndexReason = "X-YACY-Index-Control header prohibits indexing";
        }

        // check accepted domain / localhost accesses
        if ( noIndexReason == null ) {
            noIndexReason = this.crawlStacker.urlInAcceptedDomain(response.url());
        }

        // in the noIndexReason is set, indexing is not allowed
        if ( noIndexReason != null ) {
            // log cause and close queue
            final DigestURI referrerURL = response.referrerURL();
            //if (log.isFine()) log.logFine("deQueue: not indexed any word in URL " + response.url() + "; cause: " + noIndexReason);
            addURLtoErrorDB(
                response.url(),
                (referrerURL == null) ? null : referrerURL.hash(),
                response.initiator(),
                response.name(),
                FailCategory.FINAL_PROCESS_CONTEXT,
                noIndexReason);
            // finish this entry
            return "not allowed: " + noIndexReason;
        }

        // put document into the concurrent processing queue
        try {
            this.indexingDocumentProcessor.enQueue(new IndexingQueueEntry(
                response,
                null,
                null));
            return null;
        } catch ( final InterruptedException e ) {
            Log.logException(e);
            return "interrupted: " + e.getMessage();
        }
    }

    public boolean processSurrogate(final String s) {
        final File infile = new File(this.surrogatesInPath, s);
        if ( !infile.exists() || !infile.canWrite() || !infile.canRead() ) {
            return false;
        }
        final File outfile = new File(this.surrogatesOutPath, s);
        //if (outfile.exists()) return false;
        boolean moved = false;
        if ( s.endsWith("xml.zip") ) {
            // open the zip file with all the xml files in it
            ZipInputStream zis = null;
            try {
                final InputStream is = new BufferedInputStream(new FileInputStream(infile));
                zis = new ZipInputStream(is);
                ZipEntry entry;
                while ( (entry = zis.getNextEntry()) != null ) {
                    int size;
                    final byte[] buffer = new byte[2048];
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    while ( (size = zis.read(buffer, 0, buffer.length)) != -1 ) {
                        baos.write(buffer, 0, size);
                    }
                    baos.flush();
                    processSurrogate(new ByteArrayInputStream(baos.toByteArray()), entry.getName());
                    baos.close();
                    if (shallTerminate()) break;
                }
            } catch ( final IOException e ) {
                Log.logException(e);
            } finally {
                moved = infile.renameTo(outfile);
                if (zis != null) try {zis.close();} catch (IOException e) {}
            }
            return moved;
        }
        try {
            InputStream is = new BufferedInputStream(new FileInputStream(infile));
            if ( s.endsWith(".gz") ) {
                is = new GZIPInputStream(is);
            }
            processSurrogate(is, infile.getName());
        } catch ( final IOException e ) {
            Log.logException(e);
        } finally {
            if (!shallTerminate()) {
                moved = infile.renameTo(outfile);
                if ( moved ) {
                    // check if this file is already compressed, if not, compress now
                    if ( !outfile.getName().endsWith(".gz") ) {
                        final String gzname = outfile.getName() + ".gz";
                        final File gzfile = new File(outfile.getParentFile(), gzname);
                        try {
                            final OutputStream os =
                                new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(gzfile)));
                            FileUtils.copy(new BufferedInputStream(new FileInputStream(outfile)), os);
                            os.close();
                            if ( gzfile.exists() ) {
                                FileUtils.deletedelete(outfile);
                            }
                        } catch ( final FileNotFoundException e ) {
                            Log.logException(e);
                        } catch ( final IOException e ) {
                            Log.logException(e);
                        }
                    }
                }
            }
        }
        return moved;
    }

    public void processSurrogate(final InputStream is, final String name) throws IOException {
        final SurrogateReader reader = new SurrogateReader(is, 100);
        final Thread readerThread = new Thread(reader, name);
        readerThread.start();
        DCEntry surrogate;
        Response response;
        while ( (surrogate = reader.take()) != DCEntry.poison ) {
            // check if url is in accepted domain
            assert surrogate != null;
            assert this.crawlStacker != null;
            final String urlRejectReason =
                this.crawlStacker.urlInAcceptedDomain(surrogate.getIdentifier(true));
            if ( urlRejectReason != null ) {
                this.log.logWarning("Rejected URL '"
                    + surrogate.getIdentifier(true)
                    + "': "
                    + urlRejectReason);
                continue;
            }

            // create a queue entry
            final Document document = surrogate.document();
            final Request request =
                new Request(
                    ASCII.getBytes(this.peers.mySeed().hash),
                    surrogate.getIdentifier(true),
                    null,
                    "",
                    surrogate.getDate(),
                    this.crawler.defaultSurrogateProfile.handle(),
                    0,
                    0,
                    0,
                    0);
            response = new Response(request, null, null, this.crawler.defaultSurrogateProfile, false);
            final IndexingQueueEntry queueEntry =
                new IndexingQueueEntry(response, new Document[] {document}, null);

            // place the queue entry into the concurrent process of the condenser (document analysis)
            try {
                this.indexingCondensementProcessor.enQueue(queueEntry);
            } catch ( final InterruptedException e ) {
                Log.logException(e);
                break;
            }
            if (shallTerminate()) break;
        }
    }

    public int surrogateQueueSize() {
        // count surrogates
        final String[] surrogatelist = this.surrogatesInPath.list();
        if ( surrogatelist.length > 100 ) {
            return 100;
        }
        int count = 0;
        for ( final String s : surrogatelist ) {
            if ( s.endsWith(".xml") ) {
                count++;
            }
            if ( count >= 100 ) {
                break;
            }
        }
        return count;
    }

    public void surrogateFreeMem() {
        // do nothing
    }

    public boolean surrogateProcess() {
        // work off fresh entries from the proxy or from the crawler
        final String cautionCause = onlineCaution();
        if ( cautionCause != null ) {
            if ( this.log.isFine() ) {
                this.log.logFine("deQueue: online caution for "
                    + cautionCause
                    + ", omitting resource stack processing");
            }
            return false;
        }

        try {
            // check surrogates
            final String[] surrogatelist = this.surrogatesInPath.list();
            if ( surrogatelist.length > 0 ) {
                // look if the is any xml inside
                for ( final String surrogate : surrogatelist ) {

                    // check for interruption
                    checkInterruption();

                    if ( surrogate.endsWith(".xml")
                        || surrogate.endsWith(".xml.gz")
                        || surrogate.endsWith(".xml.zip") ) {
                        // read the surrogate file and store entry in index
                        if ( processSurrogate(surrogate) ) {
                            return true;
                        }
                    }
                }
            }

        } catch ( final InterruptedException e ) {
            return false;
        }
        return false;
    }

    public int cleanupJobSize() {
        int c = 1; // "es gibt immer was zu tun"
        if ( (this.crawlQueues.delegatedURL.stackSize() > 1000) ) {
            c++;
        }
        if ( (this.crawlQueues.errorURL.stackSize() > 1000) ) {
            c++;
        }
        for ( final EventOrigin origin : EventOrigin.values() ) {
            if ( ResultURLs.getStackSize(origin) > 1000 ) {
                c++;
            }
        }
        return c;
    }

    public boolean cleanupJob() {
        
        if (MemoryControl.shortStatus()) {
            WordCache.clear();
            Domains.clear();
            Digest.cleanup();
        }
        
        try {
        	// flush the document compressor cache
        	Cache.commit();
        	Digest.cleanup(); // don't let caches become permanent memory leaks

            // clear caches if necessary
            if ( !MemoryControl.request(8000000L, false) ) {
                this.index.fulltext().clearCache();
                SearchEventCache.cleanupEvents(false);
                this.trail.clear();
            }

            // set a random password if no password is configured
            if ( getConfigBool("adminAccountForLocalhost", false)
                && getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "").isEmpty() ) {
                // make a 'random' password
                setConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "0000" + this.genRandomPassword());
                setConfig("adminAccount", "");
            }

            // refresh recrawl dates
            try {
                CrawlProfile selentry;
                for ( final byte[] handle : this.crawler.getActive() ) {
                    selentry = this.crawler.getActive(handle);
                    assert selentry.handle() != null : "profile.name = " + selentry.collectionName();
                    if ( selentry.handle() == null ) {
                        this.crawler.removeActive(handle);
                        continue;
                    }
                    boolean insert = false;
                    if ( selentry.name().equals(CrawlSwitchboard.CRAWL_PROFILE_PROXY) ) {
                        selentry.put(CrawlProfile.RECRAWL_IF_OLDER, Long.toString(CrawlProfile.getRecrawlDate(CrawlSwitchboard.CRAWL_PROFILE_PROXY_RECRAWL_CYCLE)));
                        insert = true;
                    }
                    if ( selentry.name().equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_TEXT) ) {
                        selentry.put(
                                CrawlProfile.RECRAWL_IF_OLDER,
                                Long.toString(CrawlProfile.getRecrawlDate(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_TEXT_RECRAWL_CYCLE)));
                        insert = true;
                    }
                    if ( selentry.name().equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT) ) {
                        selentry.put(
                                CrawlProfile.RECRAWL_IF_OLDER,
                                Long.toString(CrawlProfile.getRecrawlDate(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT_RECRAWL_CYCLE)));
                        insert = true;
                    }
                    if ( selentry.name().equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA) ) {
                        selentry.put(
                                CrawlProfile.RECRAWL_IF_OLDER,
                                Long.toString(CrawlProfile.getRecrawlDate(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA_RECRAWL_CYCLE)));
                        insert = true;
                    }
                    if ( selentry.name().equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA) ) {
                        selentry.put(
                                CrawlProfile.RECRAWL_IF_OLDER,
                                Long.toString(CrawlProfile.getRecrawlDate(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA_RECRAWL_CYCLE)));
                        insert = true;
                    }
                    if ( selentry.name().equals(CrawlSwitchboard.CRAWL_PROFILE_SURROGATE) ) {
                        selentry.put(CrawlProfile.RECRAWL_IF_OLDER, Long.toString(CrawlProfile.getRecrawlDate(CrawlSwitchboard.CRAWL_PROFILE_SURROGATE_RECRAWL_CYCLE)));
                        insert = true;
                    }
                    if ( insert ) {
                        this.crawler.putActive(UTF8.getBytes(selentry.handle()), selentry);
                    }
                }
            } catch ( final Exception e ) {
                Log.logException(e);
            }

            // execute scheduled API actions
            Tables.Row row;
            final List<String> pks = new ArrayList<String>();
            final Date now = new Date();
            try {
                final Iterator<Tables.Row> plainIterator = this.tables.iterator(WorkTables.TABLE_API_NAME);
                final Iterator<Tables.Row> mapIterator =
                    this.tables
                        .orderBy(plainIterator, -1, WorkTables.TABLE_API_COL_DATE_RECORDING)
                        .iterator();
                while ( mapIterator.hasNext() ) {
                    row = mapIterator.next();
                    if ( row == null ) {
                        continue;
                    }
                    final Date date_next_exec = row.get(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, (Date) null);
                    if ( date_next_exec == null ) {
                        continue;
                    }
                    if ( date_next_exec.after(now) ) {
                        continue;
                    }
                    pks.add(UTF8.String(row.getPK()));
                }
            } catch ( final IOException e ) {
                Log.logException(e);
            }
            for ( final String pk : pks ) {
                try {
                    row = this.tables.select(WorkTables.TABLE_API_NAME, UTF8.getBytes(pk));
                    WorkTables.calculateAPIScheduler(row, true); // calculate next update time
                    this.tables.update(WorkTables.TABLE_API_NAME, row);
                } catch ( final IOException e ) {
                    Log.logException(e);
                    continue;
                } catch ( final SpaceExceededException e ) {
                    Log.logException(e);
                    continue;
                }
            }
            final Map<String, Integer> callResult =
                this.tables.execAPICalls(
                    "localhost",
                    (int) getConfigLong("port", 8090),
                    getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, ""),
                    pks);
            for ( final Map.Entry<String, Integer> call : callResult.entrySet() ) {
                this.log.logInfo("Scheduler executed api call, response "
                    + call.getValue()
                    + ": "
                    + call.getKey());
            }

            // close unused connections
            ConnectionInfo.cleanUp();

            // clean up delegated stack
            checkInterruption();
            if ( (this.crawlQueues.delegatedURL.stackSize() > 1000) ) {
                if ( this.log.isFine() ) {
                    this.log.logFine("Cleaning Delegated-URLs report stack, "
                        + this.crawlQueues.delegatedURL.stackSize()
                        + " entries on stack");
                }
                this.crawlQueues.delegatedURL.clearStack();
            }

            // clean up error stack
            checkInterruption();
            if ( (this.crawlQueues.errorURL.stackSize() > 1000) ) {
                if ( this.log.isFine() ) {
                    this.log.logFine("Cleaning Error-URLs report stack, "
                        + this.crawlQueues.errorURL.stackSize()
                        + " entries on stack");
                }
                this.crawlQueues.errorURL.clearStack();
            }

            // clean up loadedURL stack
            for ( final EventOrigin origin : EventOrigin.values() ) {
                checkInterruption();
                if ( ResultURLs.getStackSize(origin) > 1000 ) {
                    if ( this.log.isFine() ) {
                        this.log.logFine("Cleaning Loaded-URLs report stack, "
                            + ResultURLs.getStackSize(origin)
                            + " entries on stack "
                            + origin.getCode());
                    }
                    ResultURLs.clearStack(origin);
                }
            }

            // clean up image stack
            ResultImages.clearQueues();

            // clean up profiles
            checkInterruption();
            cleanProfiles();

            // clean up news
            checkInterruption();
            try {
                if ( this.log.isFine() ) {
                    this.log.logFine("Cleaning Incoming News, "
                        + this.peers.newsPool.size(NewsPool.INCOMING_DB)
                        + " entries on stack");
                }
                this.peers.newsPool.automaticProcess(this.peers);
            } catch ( final Exception e ) {
                Log.logException(e);
            }
            if ( getConfigBool("cleanup.deletionProcessedNews", true) ) {
                this.peers.newsPool.clear(NewsPool.PROCESSED_DB);
            }
            if ( getConfigBool("cleanup.deletionPublishedNews", true) ) {
                this.peers.newsPool.clear(NewsPool.PUBLISHED_DB);
            }

            // clean up seed-dbs
            if ( getConfigBool("routing.deleteOldSeeds.permission", true) ) {
                final long deleteOldSeedsTime =
                    getConfigLong("routing.deleteOldSeeds.time", 7) * 24 * 3600000;
                Iterator<Seed> e = this.peers.seedsSortedDisconnected(true, Seed.LASTSEEN);
                Seed seed = null;
                final List<String> deleteQueue = new ArrayList<String>();
                checkInterruption();
                // clean passive seeds
                while ( e.hasNext() ) {
                    seed = e.next();
                    if ( seed != null ) {
                        //list is sorted -> break when peers are too young to delete
                        if ( !seed.isLastSeenTimeout(deleteOldSeedsTime) ) {
                            break;
                        }
                        deleteQueue.add(seed.hash);
                    }
                }
                for ( int i = 0; i < deleteQueue.size(); ++i ) {
                    this.peers.removeDisconnected(deleteQueue.get(i));
                }
                deleteQueue.clear();
                e = this.peers.seedsSortedPotential(true, Seed.LASTSEEN);
                checkInterruption();
                // clean potential seeds
                while ( e.hasNext() ) {
                    seed = e.next();
                    if ( seed != null ) {
                        //list is sorted -> break when peers are too young to delete
                        if ( !seed.isLastSeenTimeout(deleteOldSeedsTime) ) {
                            break;
                        }
                        deleteQueue.add(seed.hash);
                    }
                }
                for ( int i = 0; i < deleteQueue.size(); ++i ) {
                    this.peers.removePotential(deleteQueue.get(i));
                }
            }

            // check if update is available and
            // if auto-update is activated perform an automatic installation and restart
            final yacyRelease updateVersion = yacyRelease.rulebasedUpdateInfo(false);
            if ( updateVersion != null ) {
                // there is a version that is more recent. Load it and re-start with it
                this.log.logInfo("AUTO-UPDATE: downloading more recent release " + updateVersion.getUrl());
                final File downloaded = updateVersion.downloadRelease();
                final boolean devenvironment = new File(this.getAppPath(), ".git").exists();
                if ( devenvironment ) {
                    this.log
                        .logInfo("AUTO-UPDATE: omitting update because this is a development environment");
                } else if ( (downloaded == null) || (!downloaded.exists()) || (downloaded.length() == 0) ) {
                    this.log
                        .logInfo("AUTO-UPDATE: omitting update because download failed (file cannot be found, is too small or signature is bad)");
                } else {
                    yacyRelease.deployRelease(downloaded);
                    terminate(10, "auto-update to install " + downloaded.getName());
                    this.log.logInfo("AUTO-UPDATE: deploy and restart initiated");
                }
            }

            // initiate broadcast about peer startup to spread supporter url
            if ( !isRobinsonMode() && this.peers.newsPool.size(NewsPool.OUTGOING_DB) == 0 ) {
                // read profile
                final Properties profile = new Properties();
                FileInputStream fileIn = null;
                try {
                    fileIn = new FileInputStream(new File("DATA/SETTINGS/profile.txt"));
                    profile.load(fileIn);
                } catch ( final IOException e ) {
                } finally {
                    if ( fileIn != null ) {
                        try {
                            fileIn.close();
                        } catch ( final Exception e ) {
                        }
                    }
                }
                final String homepage = (String) profile.get("homepage");
                if ( (homepage != null) && (homepage.length() > 10) ) {
                    final Properties news = new Properties();
                    news.put("homepage", profile.get("homepage"));
                    this.peers.newsPool.publishMyNews(
                        this.peers.mySeed(),
                        NewsPool.CATEGORY_PROFILE_BROADCAST,
                        news);
                }
            }

            // update the cluster set
            this.clusterhashes = this.peers.clusterHashes(getConfig("cluster.peers.yacydomain", ""));

            // check if we are reachable and try to map port again if not (e.g. when router rebooted)
            if ( getConfigBool(SwitchboardConstants.UPNP_ENABLED, false) && this.peers.mySeed().isJunior() ) {
                UPnP.addPortMapping();
            }

            // after all clean up is done, check the resource usage
            this.observer.resourceObserverJob();

            // cleanup cached search failures
            if ( getConfigBool(SwitchboardConstants.NETWORK_SEARCHVERIFY, false)
                && this.peers.mySeed().getFlagAcceptRemoteIndex() ) {
                this.tables.cleanFailURLS(getConfigLong("cleanup.failedSearchURLtimeout", -1));
            }

            // periodically store the triple store
            if (getConfigBool("triplestore.persistent", false)) {
                JenaTripleStore.saveAll();
            }

            return true;
        } catch ( final InterruptedException e ) {
            this.log.logInfo("cleanupJob: Shutdown detected");
            return false;
        }
    }

    /**
     * With this function the crawling process can be paused
     *
     * @param jobType
     */
    public void pauseCrawlJob(final String jobType, String cause) {
        final Object[] status = this.crawlJobsStatus.get(jobType);
        synchronized ( status[SwitchboardConstants.CRAWLJOB_SYNC] ) {
            status[SwitchboardConstants.CRAWLJOB_STATUS] = Boolean.TRUE;
        }
        setConfig(jobType + "_isPaused", "true");
        setConfig(jobType + "_isPaused_cause", "cause");
        log.logWarning("Crawl job '" + jobType + "' is paused: " + cause);
    }

    /**
     * Continue the previously paused crawling
     *
     * @param jobType
     */
    public void continueCrawlJob(final String jobType) {
        final Object[] status = this.crawlJobsStatus.get(jobType);
        synchronized ( status[SwitchboardConstants.CRAWLJOB_SYNC] ) {
            if ( ((Boolean) status[SwitchboardConstants.CRAWLJOB_STATUS]).booleanValue() ) {
                status[SwitchboardConstants.CRAWLJOB_STATUS] = Boolean.FALSE;
                status[SwitchboardConstants.CRAWLJOB_SYNC].notifyAll();
            }
        }
        setConfig(jobType + "_isPaused", "false");
    }

    /**
     * @param jobType
     * @return <code>true</code> if crawling was paused or <code>false</code> otherwise
     */
    public boolean crawlJobIsPaused(final String jobType) {
        final Object[] status = this.crawlJobsStatus.get(jobType);
        synchronized ( status[SwitchboardConstants.CRAWLJOB_SYNC] ) {
            return ((Boolean) status[SwitchboardConstants.CRAWLJOB_STATUS]).booleanValue();
        }
    }

    public IndexingQueueEntry parseDocument(final IndexingQueueEntry in) {
        in.queueEntry.updateStatus(Response.QUEUE_STATE_PARSING);

        Document[] documents = null;
        try {
            documents = parseDocument(in.queueEntry);
        } catch ( final InterruptedException e ) {
            documents = null;
        } catch ( final Exception e ) {
            documents = null;
        }
        if ( documents == null ) {
            return null;
        }
        return new IndexingQueueEntry(in.queueEntry, documents, null);
    }

    private Document[] parseDocument(final Response response) throws InterruptedException {
        Document[] documents = null;
        final EventOrigin processCase = response.processCase(this.peers.mySeed().hash);

        if ( this.log.isFine() ) {
            this.log.logFine(
                "processResourceStack processCase=" + processCase
                + ", depth=" + response.depth()
                + ", maxDepth=" + ((response.profile() == null) ? "null" : Integer.toString(response.profile().depth()))
                + ", must-match=" + ((response.profile() == null) ? "null" : response.profile().urlMustMatchPattern().toString())
                + ", must-not-match=" + ((response.profile() == null) ? "null" : response.profile().urlMustNotMatchPattern().toString())
                + ", initiatorHash=" + ((response.initiator() == null) ? "null" : ASCII.String(response.initiator()))
                + ", url=" + response.url()); // DEBUG
        }

        // PARSE CONTENT
        final long parsingStartTime = System.currentTimeMillis();
        if ( response.getContent() == null ) {
            // fetch the document from cache
            response.setContent(Cache.getContent(response.url().hash()));
            if ( response.getContent() == null ) {
                this.log.logWarning("the resource '" + response.url() + "' is missing in the cache.");
                addURLtoErrorDB(
                    response.url(),
                    response.referrerHash(),
                    response.initiator(),
                    response.name(),
                    FailCategory.FINAL_LOAD_CONTEXT,
                    "missing in cache");
                return null;
            }
        }
        assert response.getContent() != null;
        try {
            // parse the document
            documents =
                TextParser.parseSource(
                    response.url(),
                    response.getMimeType(),
                    response.getCharacterEncoding(),
                    response.getContent());
            if ( documents == null ) {
                throw new Parser.Failure("Parser returned null.", response.url());
            }
        } catch ( final Parser.Failure e ) {
            this.log.logWarning("Unable to parse the resource '" + response.url() + "'. " + e.getMessage());
            addURLtoErrorDB(
                response.url(),
                response.referrerHash(),
                response.initiator(),
                response.name(),
                FailCategory.FINAL_PROCESS_CONTEXT,
                e.getMessage());
            return null;
        }

        final long parsingEndTime = System.currentTimeMillis();

        // put anchors on crawl stack
        final long stackStartTime = System.currentTimeMillis();
        if ((processCase == EventOrigin.PROXY_LOAD || processCase == EventOrigin.LOCAL_CRAWLING) &&
            (
                response.profile() == null ||
                response.depth() < response.profile().depth() ||
                response.profile().crawlerNoDepthLimitMatchPattern().matcher(response.url().toNormalform(true)).matches()
            )
           ) {
            // get the hyperlinks
            final Map<MultiProtocolURI, String> hl = Document.getHyperlinks(documents);

            // add all media links also to the crawl stack. They will be re-sorted to the NOLOAD queue and indexed afterwards as pure links
            if (response.profile().directDocByURL()) {
                hl.putAll(Document.getImagelinks(documents));
                hl.putAll(Document.getApplinks(documents));
                hl.putAll(Document.getVideolinks(documents));
                hl.putAll(Document.getAudiolinks(documents));
            }

            // insert those hyperlinks to the crawler
            MultiProtocolURI nextUrl;
            for ( final Map.Entry<MultiProtocolURI, String> nextEntry : hl.entrySet() ) {
                // check for interruption
                checkInterruption();

                // process the next hyperlink
                nextUrl = nextEntry.getKey();
                String u = nextUrl.toNormalform(true, true);
                if ( !(u.startsWith("http://")
                    || u.startsWith("https://")
                    || u.startsWith("ftp://")
                    || u.startsWith("smb://") || u.startsWith("file://")) ) {
                    continue;
                }
                
                // rewrite the url
                String u0 = LibraryProvider.urlRewriter.apply(u);
                if (!u.equals(u0)) {
                    log.logInfo("REWRITE of url = \"" + u + "\" to \"" + u0 + "\"");
                    u = u0;
                }
                
                // enqueue the hyperlink into the pre-notice-url db
                try {
                    this.crawlStacker.enqueueEntry(new Request(
                        response.initiator(),
                        new DigestURI(u),
                        response.url().hash(),
                        nextEntry.getValue(),
                        new Date(),
                        response.profile().handle(),
                        response.depth() + 1,
                        0,
                        0,
                        response.size() < 0 ? 0 : response.size()));
                } catch ( final MalformedURLException e ) {
                    Log.logException(e);
                }
            }
            final long stackEndTime = System.currentTimeMillis();
            if ( this.log.isInfo() ) {
                this.log.logInfo("CRAWL: ADDED "
                    + hl.size()
                    + " LINKS FROM "
                    + response.url().toNormalform(true)
                    + ", STACKING TIME = "
                    + (stackEndTime - stackStartTime)
                    + ", PARSING TIME = "
                    + (parsingEndTime - parsingStartTime));
            }
        }
        return documents;
    }

    public IndexingQueueEntry condenseDocument(final IndexingQueueEntry in) {
        in.queueEntry.updateStatus(Response.QUEUE_STATE_CONDENSING);
        CrawlProfile profile = in.queueEntry.profile();
        String urls = in.queueEntry.url().toNormalform(true);
        
        // check profile attributes which prevent indexing (while crawling is allowed)
        if (!profile.indexText() && !profile.indexMedia()) {
            if (this.log.isInfo()) this.log.logInfo("Not Condensed Resource '" + urls + "': indexing of this media type not wanted by crawl profile");
            return new IndexingQueueEntry(in.queueEntry, in.documents, null);
        }
        if (!profile.indexUrlMustMatchPattern().matcher(urls).matches() ||
             profile.indexUrlMustNotMatchPattern().matcher(urls).matches() ) {
            if (this.log.isInfo()) this.log.logInfo("Not Condensed Resource '" + urls + "': indexing prevented by regular expression on url; indexUrlMustMatchPattern = " + profile.indexUrlMustMatchPattern().pattern() + ", indexUrlMustNotMatchPattern = " + profile.indexUrlMustNotMatchPattern().pattern());
            return new IndexingQueueEntry(in.queueEntry, in.documents, null);
        }
        
        // check which files may take part in the indexing process
        final List<Document> doclist = new ArrayList<Document>();
        for ( final Document document : in.documents ) {
            if ( document.indexingDenied() ) {
                if ( this.log.isInfo() ) this.log.logInfo("Not Condensed Resource '" + urls + "': denied by document-attached noindexing rule");
                addURLtoErrorDB(
                    in.queueEntry.url(),
                    in.queueEntry.referrerHash(),
                    in.queueEntry.initiator(),
                    in.queueEntry.name(),
                    FailCategory.FINAL_PROCESS_CONTEXT,
                    "denied by document-attached noindexing rule");
                continue;
            }
            doclist.add(document);
        }

        if ( doclist.isEmpty() ) {
            return new IndexingQueueEntry(in.queueEntry, in.documents, null);
        }
        in.documents = doclist.toArray(new Document[doclist.size()]);
        final Condenser[] condenser = new Condenser[in.documents.length];
        for ( int i = 0; i < in.documents.length; i++ ) {
            condenser[i] =
                new Condenser(
                        in.documents[i], in.queueEntry.profile().indexText(),
                        in.queueEntry.profile().indexMedia(),
                        LibraryProvider.dymLib, LibraryProvider.synonyms, true);

            // update image result list statistics
            // its good to do this concurrently here, because it needs a DNS lookup
            // to compute a URL hash which is necessary for a double-check
            ResultImages.registerImages(in.queueEntry.url(), in.documents[i], (profile == null)
                ? true
                : !profile.remoteIndexing());
        }
        return new IndexingQueueEntry(in.queueEntry, in.documents, condenser);
    }

    public IndexingQueueEntry webStructureAnalysis(final IndexingQueueEntry in) {
        in.queueEntry.updateStatus(Response.QUEUE_STATE_STRUCTUREANALYSIS);
        for (Document document : in.documents) {
            assert this.webStructure != null;
            assert in != null;
            assert in.queueEntry != null;
            assert in.documents != null;
            assert in.queueEntry != null;
            this.webStructure.generateCitationReference(in.queueEntry.url(), document); // [outlinksSame, outlinksOther]
        }
        return in;
    }

    public void storeDocumentIndex(final IndexingQueueEntry in) {
        in.queueEntry.updateStatus(Response.QUEUE_STATE_INDEXSTORAGE);
        // the condenser may be null in case that an indexing is not wanted (there may be a no-indexing flag in the file)
        if ( in.condenser != null ) {
            for ( int i = 0; i < in.documents.length; i++ ) {
                storeDocumentIndex(
                    in.queueEntry,
                    in.documents[i],
                    in.condenser[i],
                    null,
                    "crawler/indexing queue");
            }
        }
        in.queueEntry.updateStatus(Response.QUEUE_STATE_FINISHED);
    }

    private void storeDocumentIndex(
        final Response queueEntry,
        final Document document,
        final Condenser condenser,
        final SearchEvent searchEvent,
        final String sourceName) {

        //TODO: document must carry referer, size and last modified

        // CREATE INDEX
        final String dc_title = document.dc_title();
        final DigestURI url = DigestURI.toDigestURI(document.dc_source());
        final DigestURI referrerURL = queueEntry.referrerURL();
        EventOrigin processCase = queueEntry.processCase(this.peers.mySeed().hash);

        if ( condenser == null || document.indexingDenied() ) {
            //if (this.log.isInfo()) log.logInfo("Not Indexed Resource '" + queueEntry.url().toNormalform(false, true) + "': denied by rule in document, process case=" + processCase);
            addURLtoErrorDB(
                url,
                (referrerURL == null) ? null : referrerURL.hash(),
                queueEntry.initiator(),
                dc_title,
                FailCategory.FINAL_PROCESS_CONTEXT,
                "denied by rule in document, process case=" + processCase);
            return;
        }

        if ( !queueEntry.profile().indexText() && !queueEntry.profile().indexMedia() ) {
            //if (this.log.isInfo()) log.logInfo("Not Indexed Resource '" + queueEntry.url().toNormalform(false, true) + "': denied by profile rule, process case=" + processCase + ", profile name = " + queueEntry.profile().name());
            addURLtoErrorDB(
                url,
                (referrerURL == null) ? null : referrerURL.hash(),
                queueEntry.initiator(),
                dc_title,
                FailCategory.FINAL_LOAD_CONTEXT,
                "denied by profile rule, process case="
                    + processCase
                    + ", profile name = "
                    + queueEntry.profile().collectionName());
            return;
        }

        // remove stopwords
        this.log.logInfo("Excluded " + condenser.excludeWords(stopwords) + " words in URL " + url);

        // STORE WORD INDEX
        SolrInputDocument newEntry =
            this.index.storeDocument(
                url,
                referrerURL,
                queueEntry.profile(),
                queueEntry.getResponseHeader(),
                document,
                condenser,
                searchEvent,
                sourceName,
                getConfigBool(SwitchboardConstants.DHT_ENABLED, false));
        final RSSFeed feed =
            EventChannel.channels(queueEntry.initiator() == null
                ? EventChannel.PROXY
                : Base64Order.enhancedCoder.equal(
                    queueEntry.initiator(),
                    ASCII.getBytes(this.peers.mySeed().hash))
                    ? EventChannel.LOCALINDEXING
                    : EventChannel.REMOTEINDEXING);
        feed.addMessage(new RSSMessage("Indexed web page", dc_title, queueEntry.url(), ASCII.String(queueEntry.url().hash())));

        // store rss feeds in document into rss table
        for ( final Map.Entry<MultiProtocolURI, String> rssEntry : document.getRSS().entrySet() ) {
            final Tables.Data rssRow = new Tables.Data();
            rssRow.put("referrer", url.hash());
            rssRow.put("url", UTF8.getBytes(rssEntry.getKey().toNormalform(true)));
            rssRow.put("title", UTF8.getBytes(rssEntry.getValue()));
            rssRow.put("recording_date", new Date());
            try {
                this.tables.update("rss", DigestURI.toDigestURI(rssEntry.getKey()).hash(), rssRow);
            } catch ( final IOException e ) {
                Log.logException(e);
            }
        }

        // update url result list statistics
        ResultURLs.stack(
            ASCII.String(url.hash()), // loaded url db entry
            url.getHost(),
            queueEntry.initiator(), // initiator peer hash
            UTF8.getBytes(this.peers.mySeed().hash), // executor peer hash
            processCase // process case
            );

        // increment number of indexed urls
        this.indexedPages++;

        // update profiling info
        if ( System.currentTimeMillis() - lastPPMUpdate > 20000 ) {
            // we don't want to do this too often
            updateMySeed();
            EventTracker.update(EventTracker.EClass.PPM, Long.valueOf(currentPPM()), true);
            lastPPMUpdate = System.currentTimeMillis();
        }
        EventTracker.update(EventTracker.EClass.INDEX, url.toNormalform(true), false);

        // if this was performed for a remote crawl request, notify requester
        if ( (processCase == EventOrigin.GLOBAL_CRAWLING) && (queueEntry.initiator() != null) ) {
            final Seed initiatorPeer = this.peers.get(ASCII.String(queueEntry.initiator()));
            if ( initiatorPeer != null ) {
                if ( this.clusterhashes != null ) {
                    initiatorPeer.setAlternativeAddress(this.clusterhashes.get(queueEntry.initiator()));
                }
                // start a thread for receipt sending to avoid a blocking here
                new Thread(new receiptSending(initiatorPeer, new URIMetadataNode(newEntry)), "sending receipt to " + ASCII.String(queueEntry.initiator())).start();
            }
        }
    }

    public final void addAllToIndex(
        final DigestURI url,
        final Map<MultiProtocolURI, String> links,
        final SearchEvent searchEvent,
        final String heuristicName) {

        // add the landing page to the index. should not load that again since it should be in the cache
        if ( url != null ) {
            try {
                addToIndex(url, searchEvent, heuristicName);
            } catch ( final IOException e ) {
            } catch ( final Parser.Failure e ) {
            }

        }

        // check if some of the links match with the query
        final Map<MultiProtocolURI, String> matcher = searchEvent.query.separateMatches(links);

        // take the matcher and load them all
        for ( final Map.Entry<MultiProtocolURI, String> entry : matcher.entrySet() ) {
            try {
                addToIndex(new DigestURI(entry.getKey(), (byte[]) null), searchEvent, heuristicName);
            } catch ( final IOException e ) {
            } catch ( final Parser.Failure e ) {
            }
        }

        // take then the no-matcher and load them also
        for ( final Map.Entry<MultiProtocolURI, String> entry : links.entrySet() ) {
            try {
                addToIndex(new DigestURI(entry.getKey(), (byte[]) null), searchEvent, heuristicName);
            } catch ( final IOException e ) {
            } catch ( final Parser.Failure e ) {
            }
        }
    }

    public void stackURLs(Set<DigestURI> rootURLs, final CrawlProfile profile, final Set<DigestURI> successurls, final Map<DigestURI,String> failurls) {
        List<Thread> stackthreads = new ArrayList<Thread>(); // do this concurrently
        for (DigestURI url: rootURLs) {
            final DigestURI turl = url;
            Thread t = new Thread() {
                public void run() {
                    String failreason;
                    if ((failreason = Switchboard.this.stackUrl(profile, turl)) == null) successurls.add(turl); else failurls.put(turl, failreason);
                }
            };
            t.start();
            stackthreads.add(t);
        }
        for (Thread t: stackthreads)try {t.join(5000);} catch (InterruptedException e) {}
    }
    
    
    /**
     * stack the url to the crawler
     * @param profile
     * @param url
     * @return null if this was ok. If this failed, return a string with a fail reason
     */
    public String stackUrl(CrawlProfile profile, DigestURI url) {
        
        byte[] handle = ASCII.getBytes(profile.handle());

        // remove url from the index to be prepared for a re-crawl
        final byte[] urlhash = url.hash();
        this.index.fulltext().remove(urlhash);
        this.crawlQueues.noticeURL.removeByURLHash(urlhash);
        this.crawlQueues.errorURL.remove(urlhash);
        
        // special handling of ftp protocol
        if (url.isFTP()) {
            try {
                this.crawler.putActive(handle, profile);
                this.crawlStacker.enqueueEntriesFTP(this.peers.mySeed().hash.getBytes(), profile.handle(), url.getHost(), url.getPort(), false);
                return null;
            } catch (final Exception e) {
                // mist
                Log.logException(e);
                return "problem crawling an ftp site: " + e.getMessage();
            }
        }

        // get a scraper to get the title
        Document scraper;
        try {
            scraper = this.loader.loadDocument(url, CacheStrategy.IFFRESH, BlacklistType.CRAWLER, CrawlQueues.queuedMinLoadDelay);
        } catch (IOException e) {
            Log.logException(e);
            return "scraper cannot load URL: " + e.getMessage();
        }
        
        final String title = scraper == null ? url.toNormalform(true) : scraper.dc_title();
        final String description = scraper.dc_description();

        // add the url to the crawl stack
        this.crawler.removePassive(handle); // if there is an old entry, delete it
        this.crawler.putActive(handle, profile);
        final String reasonString = this.crawlStacker.stackCrawl(new Request(
                this.peers.mySeed().hash.getBytes(),
                url,
                null,
                "CRAWLING-ROOT",
                new Date(),
                profile.handle(),
                0,
                0,
                0,
                0
                ));
        
        if (reasonString != null) return reasonString;
        
        // create a bookmark from crawl start url
        //final Set<String> tags=ListManager.string2set(BookmarkHelper.cleanTagsString(post.get("bookmarkFolder","/crawlStart")));
        final Set<String> tags=ListManager.string2set(BookmarkHelper.cleanTagsString("/crawlStart"));
        tags.add("crawlStart");
        final String[] keywords = scraper.dc_subject();
        if (keywords != null) {
            for (final String k: keywords) {
                final String kk = BookmarkHelper.cleanTagsString(k);
                if (kk.length() > 0) tags.add(kk);
            }
        }
        String tagStr = tags.toString();
        if (tagStr.length() > 2 && tagStr.startsWith("[") && tagStr.endsWith("]")) tagStr = tagStr.substring(1, tagStr.length() - 2);

        // we will create always a bookmark to use this to track crawled hosts
        final BookmarksDB.Bookmark bookmark = this.bookmarksDB.createBookmark(url.toNormalform(true), "admin");
        if (bookmark != null) {
            bookmark.setProperty(BookmarksDB.Bookmark.BOOKMARK_TITLE, title);
            bookmark.setProperty(BookmarksDB.Bookmark.BOOKMARK_DESCRIPTION, description);
            bookmark.setOwner("admin");
            bookmark.setPublic(false);
            bookmark.setTags(tags, true);
            this.bookmarksDB.saveBookmark(bookmark);
        }

        // do the same for ymarks
        // TODO: could a non admin user add crawls?
        try {
            this.tables.bookmarks.createBookmark(this.loader, url, YMarkTables.USER_ADMIN, true, "crawlStart", "/Crawl Start");
        } catch (IOException e) {
            Log.logException(e);
        } catch (Failure e) {
            Log.logException(e);
        }

        // that was ok
        return null;
    }
    
    /**
     * load the content of a URL, parse the content and add the content to the index This process is started
     * concurrently. The method returns immediately after the call.
     *
     * @param url the url that shall be indexed
     * @param searchEvent (optional) a search event that shall get results from the indexed pages directly
     *        feeded. If object is null then it is ignored
     * @throws IOException
     * @throws Parser.Failure
     */
    public void addToIndex(final DigestURI url, final SearchEvent searchEvent, final String heuristicName)
        throws IOException,
        Parser.Failure {
        if ( searchEvent != null ) {
            searchEvent.addHeuristic(url.hash(), heuristicName, true);
        }
        if ( this.index.exists(url.hash()) ) {
            return; // don't do double-work
        }
        final Request request = this.loader.request(url, true, true);
        final CrawlProfile profile = this.crawler.getActive(ASCII.getBytes(request.profileHandle()));
        final String acceptedError = this.crawlStacker.checkAcceptance(url, profile, 0);
        final String urls = url.toNormalform(true);
        if ( acceptedError != null ) {
            this.log.logWarning("addToIndex: cannot load "
                + urls
                + ": "
                + acceptedError);
            return;
        }
        new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("Switchboard.addToIndex:" + urls);
                try {
                    final Response response =
                        Switchboard.this.loader.load(request, CacheStrategy.IFFRESH, BlacklistType.CRAWLER, CrawlQueues.queuedMinLoadDelay);
                    if ( response == null ) {
                        throw new IOException("response == null");
                    }
                    if ( response.getContent() == null ) {
                        throw new IOException("content == null");
                    }
                    if ( response.getResponseHeader() == null ) {
                        throw new IOException("header == null");
                    }
                    final Document[] documents = response.parse();
                    if ( documents != null ) {
                        for ( final Document document : documents ) {
                            if ( document.indexingDenied() ) {
                                throw new Parser.Failure("indexing is denied", url);
                            }
                            final Condenser condenser = new Condenser(document, true, true, LibraryProvider.dymLib, LibraryProvider.synonyms, true);
                            ResultImages.registerImages(url, document, true);
                            Switchboard.this.webStructure.generateCitationReference(url, document);
                            storeDocumentIndex(
                                response,
                                document,
                                condenser,
                                searchEvent,
                                "heuristic:" + heuristicName);
                            Switchboard.this.log.logInfo("addToIndex fill of url "
                                + url.toNormalform(true)
                                + " finished");
                        }
                    }
                } catch ( final IOException e ) {
                    Switchboard.this.log.logWarning("addToIndex: failed loading "
                        + url.toNormalform(true)
                        + ": "
                        + e.getMessage());
                } catch ( final Parser.Failure e ) {
                    Switchboard.this.log.logWarning("addToIndex: failed parsing "
                        + url.toNormalform(true)
                        + ": "
                        + e.getMessage());
                }
            }
        }.start();
    }

     /**
     * add url to Crawler - which itself loads the URL, parses the content and adds it to the index
     * transparent alternative to "addToIndex" including, double in crawler check, display in crawl monitor
     * but doesn't return results for a ongoing search
     *
     * @param url the url that shall be indexed
     * @param asglobal true adds the url to global crawl queue (for remote crawling), false to the local crawler
     */
    public void addToCrawler(final DigestURI url, final boolean asglobal) {

        if ( this.index.exists(url.hash()) ) {
            return; // don't do double-work
        }
        final Request request = this.loader.request(url, true, true);
        final CrawlProfile profile = this.crawler.getActive(ASCII.getBytes(request.profileHandle()));
        final String acceptedError = this.crawlStacker.checkAcceptance(url, profile, 0);
        if (acceptedError != null) {
            this.log.logInfo("addToCrawler: cannot load "
                    + url.toNormalform(true)
                    + ": "
                    + acceptedError);
            return;
        }
        final String s;
        if (asglobal) {
            s = this.crawlQueues.noticeURL.push(StackType.GLOBAL, request, this.robots);
        } else {
            s = this.crawlQueues.noticeURL.push(StackType.LOCAL, request, this.robots);
        }

        if (s != null) {
            Switchboard.this.log.logInfo("addToCrawler: failed to add "
                    + url.toNormalform(true)
                    + ": "
                    + s);
        }
    }

    public class receiptSending implements Runnable
    {
        private final Seed initiatorPeer;
        private final URIMetadataNode reference;

        public receiptSending(final Seed initiatorPeer, final URIMetadataNode reference) {
            this.initiatorPeer = initiatorPeer;
            this.reference = reference;
        }

        @Override
        public void run() {
            final long t = System.currentTimeMillis();
            final Map<String, String> response =
                Protocol.crawlReceipt(
                    Switchboard.this.peers.mySeed(),
                    this.initiatorPeer,
                    "crawl",
                    "fill",
                    "indexed",
                    this.reference,
                    "");
            if ( response == null ) {
                Switchboard.this.log.logInfo("Sending crawl receipt for '"
                    + this.reference.url().toNormalform(true)
                    + "' to "
                    + this.initiatorPeer.getName()
                    + " FAILED, send time = "
                    + (System.currentTimeMillis() - t));
                return;
            }
            final String delay = response.get("delay");
            Switchboard.this.log.logInfo("Sending crawl receipt for '"
                + this.reference.url().toNormalform(true)
                + "' to "
                + this.initiatorPeer.getName()
                + " success, delay = "
                + delay
                + ", send time = "
                + (System.currentTimeMillis() - t));
        }
    }

    /**
     * check authentication status for request access shall be granted if return value >= 2; these are the
     * cases where an access is granted to protected pages: - a password is not configured: auth-level 2 -
     * access from localhost is granted and access comes from localhost: auth-level 3 - a password is
     * configured and access comes from localhost and the realm-value of a http-authentify String is equal to
     * the stored base64MD5: auth-level 3 - a password is configured and access comes with matching
     * http-authentify: auth-level 4
     *
     * @param requestHeader
     * @return the auth-level as described above or 1 which means 'not authorized'. a 0 is returned in case of
     *         fraud attempts
     */
    public int adminAuthenticated(final RequestHeader requestHeader) {

        // authorization in case that there is no account stored
        final String adminAccountBase64MD5 = getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "");
        if ( adminAccountBase64MD5.isEmpty() ) {
            return 2; // no password stored; this should not happen for older peers
        }

        // authorization for localhost, only if flag is set to grant localhost access as admin
        final boolean accessFromLocalhost = requestHeader.accessFromLocalhost();
        if ( getConfigBool("adminAccountForLocalhost", false) && accessFromLocalhost ) {
            return 3; // soft-authenticated for localhost
        }

        // get the authorization string from the header
        final String realmProp = (requestHeader.get(RequestHeader.AUTHORIZATION, "xxxxxx")).trim();
        final String realmValue = realmProp.substring(6);

        // security check against too long authorization strings
        if ( realmValue.length() > 256 ) {
            return 0;
        }

        // authorization by encoded password, only for localhost access
        if ( accessFromLocalhost && (adminAccountBase64MD5.equals(realmValue)) ) {
            return 3; // soft-authenticated for localhost
        }

        // authorization by hit in userDB
        if ( this.userDB.hasAdminRight(realmProp, requestHeader.getHeaderCookies()) ) {
            return 4; //return, because 4=max
        }

        // authorization with admin keyword in configuration
        if ( realmValue == null || realmValue.isEmpty() ) {
            return 1;
        }
        if ( adminAccountBase64MD5.equals(Digest.encodeMD5Hex(realmValue)) ) {
            return 4; // hard-authenticated, all ok
        }
        return 1;
    }

    public boolean verifyAuthentication(final RequestHeader header) {
        // handle access rights
        switch ( adminAuthenticated(header) ) {
            case 0: // wrong password given
                //try { Thread.sleep(3000); } catch (final InterruptedException e) { } // prevent brute-force
                return false;
            case 1: // no password given
                return false;
            case 2: // no password stored
                return true;
            case 3: // soft-authenticated for localhost only
                return true;
            case 4: // hard-authenticated, all ok
                return true;
            default:
                return false;
        }
    }

    public void setPerformance(final int wantedPPM) {
        int wPPM = wantedPPM;
        // we consider 3 cases here
        //         wantedPPM <=   10: low performance
        // 10   <  wantedPPM <  30000: custom performance
        // 30000 <= wantedPPM        : maximum performance
        if ( wPPM <= 0 ) {
            wPPM = 1;
        }
        if ( wPPM >= 30000 ) {
            wPPM = 30000;
        }
        final int newBusySleep = 60000 / wPPM; // for wantedPPM = 10: 6000; for wantedPPM = 1000: 60

        BusyThread thread;

        thread = getThread(SwitchboardConstants.INDEX_DIST);
        if ( thread != null ) {
            setConfig(
                SwitchboardConstants.INDEX_DIST_BUSYSLEEP,
                thread.setBusySleep(Math.max(2000, thread.setBusySleep(newBusySleep * 2))));
            thread.setIdleSleep(30000);
        }

        thread = getThread(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL);
        if ( thread != null ) {
            setConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, thread.setBusySleep(newBusySleep));
            thread.setIdleSleep(2000);
        }
    }

    public String dhtShallTransfer() {
        final String cautionCause = onlineCaution();
        if ( cautionCause != null ) {
            return "online caution for " + cautionCause + ", dht transmission";
        }
        if ( this.peers == null ) {
            return "no DHT distribution: seedDB == null";
        }
        if ( this.peers.mySeed() == null ) {
            return "no DHT distribution: mySeed == null";
        }
        if ( this.peers.mySeed().isVirgin() ) {
            return "no DHT distribution: status is virgin";
        }
        if ( this.peers.noDHTActivity() ) {
            return "no DHT distribution: network too small";
        }
        if ( !getConfigBool(SwitchboardConstants.DHT_ENABLED, true) ) {
            return "no DHT distribution: disabled by network.unit.dht";
        }
        if ( getConfig(SwitchboardConstants.INDEX_DIST_ALLOW, "false").equalsIgnoreCase("false") ) {
            return "no DHT distribution: not enabled (per setting)";
        }
        final Segment indexSegment = this.index;
        int size = indexSegment.fulltext().size();
        if ( size < 10 ) {
            return "no DHT distribution: loadedURL.size() = " + size;
        }
        if ( indexSegment.RWICount() < 100 ) {
            return "no DHT distribution: not enough words - wordIndex.size() = "
                + indexSegment.RWICount();
        }
        if ( (getConfig(SwitchboardConstants.INDEX_DIST_ALLOW_WHILE_CRAWLING, "false")
            .equalsIgnoreCase("false")) && (this.crawlQueues.noticeURL.notEmptyLocal()) ) {
            return "no DHT distribution: crawl in progress: noticeURL.stackSize() = "
                + this.crawlQueues.noticeURL.size()
                + ", sbQueue.size() = "
                + getIndexingProcessorsQueueSize();
        }
        if ( (getConfig(SwitchboardConstants.INDEX_DIST_ALLOW_WHILE_INDEXING, "false")
            .equalsIgnoreCase("false")) && (getIndexingProcessorsQueueSize() > 1) ) {
            return "no DHT distribution: indexing in progress: noticeURL.stackSize() = "
                + this.crawlQueues.noticeURL.size()
                + ", sbQueue.size() = "
                + getIndexingProcessorsQueueSize();
        }
        return null; // this means; yes, please do dht transfer
    }

    public boolean dhtTransferJob() {
        if ( this.dhtDispatcher == null ) {
            return false;
        }
        final String rejectReason = dhtShallTransfer();
        if ( rejectReason != null ) {
            if ( this.log.isFine() ) {
                this.log.logFine(rejectReason);
            }
            return false;
        }
        boolean hasDoneSomething = false;
        final long kbytesUp = ConnectionInfo.getActiveUpbytes() / 1024;
        // accumulate RWIs to transmission cloud
        if ( this.dhtDispatcher.cloudSize() > this.peers.scheme.verticalPartitions() ) {
            this.log.logInfo("dhtTransferJob: no selection, too many entries in transmission cloud: "
                + this.dhtDispatcher.cloudSize());
        } else if ( MemoryControl.available() < 1024 * 1024 * 25 ) {
            this.log.logInfo("dhtTransferJob: no selection, too less memory available : "
                + (MemoryControl.available() / 1024 / 1024)
                + " MB");
        } else if ( ConnectionInfo.getLoadPercent() > 50 ) {
            this.log.logInfo("dhtTransferJob: too many connections in httpc pool : "
                + ConnectionInfo.getCount());
            // close unused connections
//            Client.cleanup();
        } else if ( kbytesUp > 128 ) {
            this.log.logInfo("dhtTransferJob: too much upload(1), currently uploading: " + kbytesUp + " Kb");
        } else {
            byte[] startHash = null, limitHash = null;
            int tries = 10;
            while ( tries-- > 0 ) {
                startHash = DHTSelection.selectTransferStart();
                assert startHash != null;
                limitHash = DHTSelection.limitOver(this.peers, startHash);
                if ( limitHash != null ) {
                    break;
                }
            }
            if ( limitHash == null || startHash == null ) {
                this.log.logInfo("dhtTransferJob: approaching full DHT dispersion.");
                return false;
            }
            this.log.logInfo("dhtTransferJob: selected " + ASCII.String(startHash) + " as start hash");
            this.log.logInfo("dhtTransferJob: selected " + ASCII.String(limitHash) + " as limit hash");
            final boolean enqueued =
                this.dhtDispatcher.selectContainersEnqueueToCloud(
                    startHash,
                    limitHash,
                    dhtMaxContainerCount,
                    this.dhtMaxReferenceCount,
                    5000);
            hasDoneSomething = hasDoneSomething | enqueued;
            this.log.logInfo("dhtTransferJob: result from enqueueing: " + ((enqueued) ? "true" : "false"));
        }

        // check if we can deliver entries to other peers
        if ( this.dhtDispatcher.transmissionSize() >= 10 ) {
            this.log
                .logInfo("dhtTransferJob: no dequeueing from cloud to transmission: too many concurrent sessions: "
                    + this.dhtDispatcher.transmissionSize());
        } else if ( ConnectionInfo.getLoadPercent() > 75 ) {
            this.log.logInfo("dhtTransferJob: too many connections in httpc pool : "
                + ConnectionInfo.getCount());
            // close unused connections
//            Client.cleanup();
        } else if ( kbytesUp > 256 ) {
            this.log.logInfo("dhtTransferJob: too much upload(2), currently uploading: " + kbytesUp + " Kb");
        } else {
            final boolean dequeued = this.dhtDispatcher.dequeueContainer();
            hasDoneSomething = hasDoneSomething | dequeued;
            this.log.logInfo("dhtTransferJob: result from dequeueing: " + ((dequeued) ? "true" : "false"));
        }
        return hasDoneSomething;
    }

    private void addURLtoErrorDB(
        final DigestURI url,
        final byte[] referrerHash,
        final byte[] initiator,
        final String name,
        final FailCategory failCategory,
        final String failreason) {
        // assert initiator != null; // null == proxy
        // create a new errorURL DB entry
        final Request bentry =
            new Request(
                initiator,
                url,
                referrerHash,
                (name == null) ? "" : name,
                new Date(),
                null,
                0,
                0,
                0,
                0);
        this.crawlQueues.errorURL.push(bentry, initiator, new Date(), 0, failCategory, failreason, -1);
    }

    public final void heuristicSite(final SearchEvent searchEvent, final String host) {
        new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("Switchboard.heuristicSite:" + host);
                String r = host;
                if ( r.indexOf("//", 0) < 0 ) {
                    r = "http://" + r;
                }

                // get the links for a specific site
                DigestURI url;
                try {
                    url = new DigestURI(r);
                } catch ( final MalformedURLException e ) {
                    Log.logException(e);
                    return;
                }

                final Map<MultiProtocolURI, String> links;
                searchEvent.rankingProcess.oneFeederStarted();
                try {
                    links = Switchboard.this.loader.loadLinks(url, CacheStrategy.NOCACHE, BlacklistType.SEARCH, TextSnippet.snippetMinLoadDelay);
                    if ( links != null ) {
                        final Iterator<MultiProtocolURI> i = links.keySet().iterator();
                        while ( i.hasNext() ) {
                            if ( !i.next().getHost().endsWith(host) ) {
                                i.remove();
                            }
                        }

                        // add all pages to the index
                        addAllToIndex(url, links, searchEvent, "site");
                    }
                } catch ( final Throwable e ) {
                    Log.logException(e);
                } finally {
                    searchEvent.rankingProcess.oneFeederTerminated();
                }
            }
        }.start();
    }

    public final void heuristicSearchResults(final String host) {
        new Thread() {

            @Override
            public void run() {

                // get the links for a specific site
                final DigestURI startUrl;
                try {
                    startUrl = new DigestURI(host);
                } catch (final MalformedURLException e) {
                    Log.logException(e);
                    return;
                }

                final Map<MultiProtocolURI, String> links;
                DigestURI url;
                try {
                    links = Switchboard.this.loader.loadLinks(startUrl, CacheStrategy.IFFRESH, BlacklistType.SEARCH, TextSnippet.snippetMinLoadDelay);
                    if (links != null) {
                        if (links.size() < 1000) { // limit to 1000 to skip large index pages
                            final Iterator<MultiProtocolURI> i = links.keySet().iterator();
                            final boolean globalcrawljob = Switchboard.this.getConfigBool("heuristic.searchresults.crawlglobal",false);
                            while (i.hasNext()) {
                                url = DigestURI.toDigestURI(i.next());
                                boolean islocal = url.getHost().contentEquals(startUrl.getHost());
                                // add all external links or links to different page to crawler
                                if ( !islocal ) {// || (!startUrl.getPath().endsWith(url.getPath()))) {
                                    addToCrawler(url,globalcrawljob);
                                }
                            }
                        }
                    }
                } catch (final Throwable e) {
                    Log.logException(e);
                }
            }
        }.start();
    }

    // blekko pattern: http://blekko.com/ws/$+/rss
    public final void heuristicRSS(
        final String urlpattern,
        final SearchEvent searchEvent,
        final String feedName) {
        final int p = urlpattern.indexOf('$');
        if ( p < 0 ) {
            return;
        }
        new Thread() {
            @Override
            public void run() {
                String queryString = searchEvent.query.queryString(true);
                Thread.currentThread().setName("Switchboard.heuristicRSS:" + queryString);
                final int meta = queryString.indexOf("heuristic:", 0);
                if ( meta >= 0 ) {
                    final int q = queryString.indexOf(' ', meta);
                    if ( q >= 0 ) {
                        queryString = queryString.substring(0, meta) + queryString.substring(q + 1);
                    } else {
                        queryString = queryString.substring(0, meta);
                    }
                }

                final String urlString =
                    urlpattern.substring(0, p)
                        + queryString.trim().replaceAll(" ", "+")
                        + urlpattern.substring(p + 1);
                final DigestURI url;
                try {
                    url = new DigestURI(MultiProtocolURI.unescape(urlString));
                } catch ( final MalformedURLException e1 ) {
                    Log.logWarning("heuristicRSS", "url not well-formed: '" + urlString + "'");
                    return;
                }

                // if we have an url then try to load the rss
                RSSReader rss = null;
                searchEvent.rankingProcess.oneFeederStarted();
                try {
                    final Response response =
                        Switchboard.this.loader.load(Switchboard.this.loader.request(url, true, false), CacheStrategy.NOCACHE, BlacklistType.SEARCH, TextSnippet.snippetMinLoadDelay);
                    final byte[] resource = (response == null) ? null : response.getContent();
                    //System.out.println("BLEKKO: " + UTF8.String(resource));
                    rss = resource == null ? null : RSSReader.parse(RSSFeed.DEFAULT_MAXSIZE, resource);
                    if ( rss != null ) {
                        final Map<MultiProtocolURI, String> links = new TreeMap<MultiProtocolURI, String>();
                        MultiProtocolURI uri;
                        for ( final RSSMessage message : rss.getFeed() ) {
                            try {
                                uri = new MultiProtocolURI(message.getLink());
                                links.put(uri, message.getTitle());
                            } catch ( final MalformedURLException e ) {
                            }
                        }

                        Log.logInfo("heuristicRSS", "Heuristic: adding "
                            + links.size()
                            + " links from '"
                            + feedName
                            + "' rss feed");
                        // add all pages to the index
                        addAllToIndex(null, links, searchEvent, feedName);
                    }
                } catch ( final Throwable e ) {
                    //Log.logException(e);
                } finally {
                    searchEvent.rankingProcess.oneFeederTerminated();
                }
            }
        }.start();
    }

    public static int currentPPM() {
        return EventTracker.countEvents(EventTracker.EClass.INDEX, 20000) * 3;
    }

    public float averageQPM() {
        final long uptime = (System.currentTimeMillis() - serverCore.startupTime) / 1000;
        return (this.searchQueriesRobinsonFromRemote + this.searchQueriesGlobal) * 60f / Math.max(uptime, 1f);
    }

    public float averageQPMGlobal() {
        final long uptime = (System.currentTimeMillis() - serverCore.startupTime) / 1000;
        return (this.searchQueriesGlobal) * 60f / Math.max(uptime, 1f);
    }

    public float averageQPMPrivateLocal() {
        final long uptime = (System.currentTimeMillis() - serverCore.startupTime) / 1000;
        return (this.searchQueriesRobinsonFromLocal) * 60f / Math.max(uptime, 1f);
    }

    public float averageQPMPublicLocal() {
        final long uptime = (System.currentTimeMillis() - serverCore.startupTime) / 1000;
        return (this.searchQueriesRobinsonFromRemote) * 60f / Math.max(uptime, 1f);
    }

    public void updateMySeed() {
        this.peers.mySeed().put(Seed.PORT, Integer.toString(serverCore.getPortNr(getConfig("port", "8090"))));

        //the speed of indexing (pages/minute) of the peer
        final long uptime = (System.currentTimeMillis() - serverCore.startupTime) / 1000;
        this.peers.mySeed().put(Seed.ISPEED, Integer.toString(currentPPM()));
        this.peers.mySeed().put(Seed.RSPEED, Float.toString(averageQPM()));
        this.peers.mySeed().put(Seed.UPTIME, Long.toString(uptime / 60)); // the number of minutes that the peer is up in minutes/day (moving average MA30)
        this.peers.mySeed().put(Seed.LCOUNT, Long.toString(this.index.URLCount())); // the number of links that the peer has stored (LURL's)
        this.peers.mySeed().put(Seed.NCOUNT, Integer.toString(this.crawlQueues.noticeURL.size())); // the number of links that the peer has noticed, but not loaded (NURL's)
        this.peers.mySeed().put(
            Seed.RCOUNT,
            Integer.toString(this.crawlQueues.noticeURL.stackSize(NoticedURL.StackType.GLOBAL))); // the number of links that the peer provides for remote crawling (ZURL's)
        this.peers.mySeed().put(Seed.ICOUNT, Long.toString(this.index.RWICount())); // the minimum number of words that the peer has indexed (as it says)
        this.peers.mySeed().put(Seed.SCOUNT, Integer.toString(this.peers.sizeConnected())); // the number of seeds that the peer has stored
        this.peers.mySeed().put(
            Seed.CCOUNT,
            Float.toString(((int) ((this.peers.sizeConnected() + this.peers.sizeDisconnected() + this.peers
                .sizePotential()) * 60.0f / (uptime + 1.01f)) * 100.0f) / 100.0f)); // the number of clients that the peer connects (as connects/hour)
        this.peers.mySeed().put(Seed.VERSION, yacyBuildProperties.getLongVersion());
        this.peers.mySeed().setFlagDirectConnect(true);
        this.peers.mySeed().setLastSeenUTC();
        this.peers.mySeed().put(Seed.UTC, GenericFormatter.UTCDiffString());
        this.peers.mySeed().setFlagAcceptRemoteCrawl(getConfig("crawlResponse", "").equals("true"));
        this.peers.mySeed().setFlagAcceptRemoteIndex(getConfig("allowReceiveIndex", "").equals("true"));
        //mySeed.setFlagAcceptRemoteIndex(true);
    }

    public void loadSeedLists() {
        // uses the superseed to initialize the database with known seeds

        String seedListFileURL;
        final int sc = this.peers.sizeConnected();
        Network.log.logInfo("BOOTSTRAP: " + sc + " seeds known from previous run, concurrently starting seedlist loader");

        // - use the superseed to further fill up the seedDB
        AtomicInteger scc = new AtomicInteger(0);
        int c = 0;
        while ( true ) {
            if ( Thread.currentThread().isInterrupted() ) {
                break;
            }
            seedListFileURL = this.getConfig("network.unit.bootstrap.seedlist" + c, "");
            if ( seedListFileURL.isEmpty() ) {
                break;
            }
            c++;
            if ( seedListFileURL.startsWith("http://") || seedListFileURL.startsWith("https://") ) {
                loadSeedListConcurrently(this.peers, seedListFileURL, scc, (int) getConfigLong("bootstrapLoadTimeout", 10000));
            }
        }
    }

    private static void loadSeedListConcurrently(final SeedDB peers, final String seedListFileURL, final AtomicInteger scc, final int timeout) {
        // uses the superseed to initialize the database with known seeds

        Thread seedLoader = new Thread() {
            @Override
            public void run() {
                // load the seed list
                try {
                    DigestURI url = new DigestURI(seedListFileURL);
                    //final long start = System.currentTimeMillis();
                    final RequestHeader reqHeader = new RequestHeader();
                    reqHeader.put(HeaderFramework.PRAGMA, "no-cache");
                    reqHeader.put(HeaderFramework.CACHE_CONTROL, "no-cache");
                    final HTTPClient client = new HTTPClient(ClientIdentification.getUserAgent(), timeout);
                    client.setHeader(reqHeader.entrySet());

                    client.HEADResponse(url.toString());
                    int statusCode = client.getHttpResponse().getStatusLine().getStatusCode();
                    ResponseHeader header = new ResponseHeader(statusCode, client.getHttpResponse().getAllHeaders());
                    //final long loadtime = System.currentTimeMillis() - start;
                    /*if (header == null) {
                        if (loadtime > getConfigLong("bootstrapLoadTimeout", 6000)) {
                            yacyCore.log.logWarning("BOOTSTRAP: seed-list URL " + seedListFileURL + " not available, time-out after " + loadtime + " milliseconds");
                        } else {
                            yacyCore.log.logWarning("BOOTSTRAP: seed-list URL " + seedListFileURL + " not available, no content");
                        }
                    } else*/if ( header.lastModified() == null ) {
                        Network.log.logWarning("BOOTSTRAP: seed-list URL "
                            + seedListFileURL
                            + " not usable, last-modified is missing");
                    } else if ( (header.age() > 86400000) && (scc.get() > 0) ) {
                        Network.log.logInfo("BOOTSTRAP: seed-list URL "
                            + seedListFileURL
                            + " too old ("
                            + (header.age() / 86400000)
                            + " days)");
                    } else {
                        scc.incrementAndGet();
                        final byte[] content = client.GETbytes(url);
                        Iterator<String> enu = FileUtils.strings(content);
                        int lc = 0;
                        while ( enu.hasNext() ) {
                            try {
                                Seed ys = Seed.genRemoteSeed(enu.next(), false, null);
                                if ( (ys != null)
                                    && (!peers.mySeedIsDefined() || !peers.mySeed().hash.equals(ys.hash)) ) {
                                    final long lastseen = Math.abs((System.currentTimeMillis() - ys.getLastSeenUTC()) / 1000 / 60);
                                    if ( lastseen < 60 ) {
                                        if ( peers.peerActions.connectPeer(ys, false) ) {
                                            lc++;
                                        }
                                    }
                                }
                            } catch ( final IOException e ) {
                                Network.log.logInfo("BOOTSTRAP: bad seed from " + seedListFileURL + ": " + e.getMessage());
                            }
                        }
                        Network.log.logInfo("BOOTSTRAP: "
                            + lc
                            + " seeds from seed-list URL "
                            + seedListFileURL
                            + ", AGE="
                            + (header.age() / 3600000)
                            + "h");
                    }

                } catch ( final IOException e ) {
                    // this is when wget fails, commonly because of timeout
                    Network.log.logWarning("BOOTSTRAP: failed (1) to load seeds from seed-list URL "
                        + seedListFileURL + ": " + e.getMessage());
                } catch ( final Exception e ) {
                    // this is when wget fails; may be because of missing internet connection
                    Network.log.logSevere("BOOTSTRAP: failed (2) to load seeds from seed-list URL "
                        + seedListFileURL + ": " + e.getMessage(), e);
                }
            }
        };
        seedLoader.start();
    }

    public void initRemoteProxy() {
        // reading the proxy host name
        final String host = getConfig("remoteProxyHost", "").trim();
        // reading the proxy host port
        int port;
        try {
            port = Integer.parseInt(getConfig("remoteProxyPort", "3128"));
        } catch ( final NumberFormatException e ) {
            port = 3128;
        }
        // create new config
        ProxySettings.use4ssl = true;
        ProxySettings.use4YaCy = true;
        ProxySettings.port = port;
        ProxySettings.host = host;
        ProxySettings.use = ((ProxySettings.host != null) && (ProxySettings.host.length() > 0));

        // determining if remote proxy usage is enabled
        ProxySettings.use = getConfigBool("remoteProxyUse", false);

        // determining if remote proxy should be used for yacy -> yacy communication
        ProxySettings.use4YaCy = getConfig("remoteProxyUse4Yacy", "true").equalsIgnoreCase("true");

        // determining if remote proxy should be used for ssl connections
        ProxySettings.use4ssl = getConfig("remoteProxyUse4SSL", "true").equalsIgnoreCase("true");

        ProxySettings.user = getConfig("remoteProxyUser", "").trim();
        ProxySettings.password = getConfig("remoteProxyPwd", "").trim();

        // determining addresses for which the remote proxy should not be used
        final String remoteProxyNoProxy = getConfig("remoteProxyNoProxy", "").trim();
        ProxySettings.noProxy = remoteProxyNoProxy.split(",");
        // trim split entries
        int i = 0;
        for ( final String pattern : ProxySettings.noProxy ) {
            ProxySettings.noProxy[i] = pattern.trim();
            i++;
        }
    }

    public void checkInterruption() throws InterruptedException {
        final Thread curThread = Thread.currentThread();
        if ( (curThread instanceof WorkflowThread) && ((WorkflowThread) curThread).shutdownInProgress() ) {
            throw new InterruptedException("Shutdown in progress ...");
        } else if ( this.terminate || curThread.isInterrupted() ) {
            throw new InterruptedException("Shutdown in progress ...");
        }
    }

    public void terminate(final long delay, final String reason) {
        if ( delay <= 0 ) {
            throw new IllegalArgumentException("The shutdown delay must be greater than 0.");
        }
        this.log.logInfo("caught delayed terminate request: " + reason);
        (new Shutdown(this, delay, reason)).start();
    }

    public boolean shallTerminate() {
        return this.terminate;
    }

    public void terminate(final String reason) {
        this.terminate = true;
        this.log.logInfo("caught terminate request: " + reason);
        this.shutdownSync.release();
    }

    public boolean isTerminated() {
        return this.terminate;
    }

    public boolean waitForShutdown() throws InterruptedException {
        this.shutdownSync.acquire();
        return this.terminate;
    }
}
