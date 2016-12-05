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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.servlet.http.HttpServletRequest;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;

import net.yacy.contentcontrol.ContentControlFilterUpdateThread;
import net.yacy.contentcontrol.SMWListSyncThread;
import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.WordCache;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.feed.RSSReader;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.FailCategory;
import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.federate.solr.SchemaConfiguration;
import net.yacy.cora.federate.solr.connector.ShardSelection;
import net.yacy.cora.federate.solr.connector.SolrConnector.LoadTimeURL;
import net.yacy.cora.federate.solr.instance.RemoteInstance;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.TimeoutRequest;
import net.yacy.cora.protocol.ftp.FTPClient;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.protocol.http.ProxySettings;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.Memory;
import net.yacy.crawler.CrawlStacker;
import net.yacy.crawler.CrawlSwitchboard;
import net.yacy.crawler.HarvestProcess;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.CrawlQueues;
import net.yacy.crawler.data.NoticedURL;
import net.yacy.crawler.data.ResultImages;
import net.yacy.crawler.data.ResultURLs;
import net.yacy.crawler.data.NoticedURL.StackType;
import net.yacy.crawler.data.ResultURLs.EventOrigin;
import net.yacy.crawler.data.Transactions;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.data.BlogBoard;
import net.yacy.data.BlogBoardComments;
import net.yacy.data.BookmarkHelper;
import net.yacy.data.BookmarksDB;
import net.yacy.data.ListManager;
import net.yacy.data.MessageBoard;
import net.yacy.data.UserDB;
import net.yacy.data.UserDB.AccessRight;
import net.yacy.data.WorkTables;
import net.yacy.data.wiki.WikiBoard;
import net.yacy.data.wiki.WikiCode;
import net.yacy.data.wiki.WikiParser;
import net.yacy.data.ymark.YMarkTables;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.LibraryProvider;
import net.yacy.document.Parser;
import net.yacy.document.ProbabilisticClassifier;
import net.yacy.document.TextParser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.Parser.Failure;
import net.yacy.document.Tokenizer;
import net.yacy.document.content.DCEntry;
import net.yacy.document.content.SurrogateReader;
import net.yacy.document.importer.OAIListFriendsLoader;
import net.yacy.document.parser.audioTagParser;
import net.yacy.document.parser.pdfParser;
import net.yacy.document.parser.html.Evaluation;
import net.yacy.gui.Audio;
import net.yacy.gui.Tray;
import net.yacy.http.YaCyHttpServer;
import net.yacy.kelondro.blob.BEncodedHeap;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.logging.GuiHandler;
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
import net.yacy.peers.graphics.NetworkGraph;
import net.yacy.peers.graphics.WebStructureGraph;
import net.yacy.peers.operation.yacyBuildProperties;
import net.yacy.peers.operation.yacyRelease;
import net.yacy.peers.operation.yacyUpdateLocation;
import net.yacy.repository.Blacklist;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.repository.FilterEngine;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.search.index.Fulltext;
import net.yacy.search.index.Segment;
import net.yacy.search.index.Segment.ReferenceReportCache;
import net.yacy.search.query.AccessTracker;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.ranking.RankingProfile;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphConfiguration;
import net.yacy.server.serverSwitch;
import net.yacy.server.http.RobotsTxtConfig;
import net.yacy.utils.CryptoLib;
import net.yacy.utils.crypt;
import net.yacy.utils.upnp.UPnP;
import net.yacy.visualization.CircleTool;

import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;
import com.google.common.io.Files;



public final class Switchboard extends serverSwitch {

    final static String SOLR_COLLECTION_CONFIGURATION_NAME_OLD = "solr.keys.default.list";
    public final static String SOLR_COLLECTION_CONFIGURATION_NAME = "solr.collection.schema";
    public final static String SOLR_WEBGRAPH_CONFIGURATION_NAME = "solr.webgraph.schema";
    
    public static long lastPPMUpdate = System.currentTimeMillis() - 30000;
    private static final int dhtMaxContainerCount = 500;
    private int dhtMaxReferenceCount = 1000;

    // colored list management
    public static SortedSet<String> badwords = new TreeSet<String>(NaturalOrder.naturalComparator);
    public static SortedSet<String> stopwords = new TreeSet<String>(NaturalOrder.naturalComparator);
    public static SortedSet<String> blueList = null;
    public static SortedSet<byte[]> stopwordHashes = null;
    public static Blacklist urlBlacklist = null;

    public static WikiParser wikiParser = null;

    // storage management
    public File htCachePath;
    public final File dictionariesPath, classificationPath;
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
    public volatile long proxyLastAccess, localSearchLastAccess, remoteSearchLastAccess, adminAuthenticationLastAccess, optimizeLastRun;
    public Network yc;
    public ResourceObserver observer;
    public UserDB userDB;
    public BookmarksDB bookmarksDB;
    public WebStructureGraph webStructure;
    public ConcurrentHashMap<String, TreeSet<Long>> localSearchTracker, remoteSearchTracker; // mappings from requesting host to a TreeSet of Long(access time)
    public int searchQueriesRobinsonFromLocal = 0; // absolute counter of all local queries submitted on this peer from a local or autheticated used
    public int searchQueriesRobinsonFromRemote = 0; // absolute counter of all local queries submitted on this peer from a remote IP without authentication
    public float searchQueriesGlobal = 0f; // partial counter of remote queries (1/number-of-requested-peers)
    public SortedSet<byte[]> clusterhashes; // a set of cluster hashes
    public List<Pattern> networkWhitelist, networkBlacklist;
    public FilterEngine domainList;
    private Dispatcher dhtDispatcher;
    public LinkedBlockingQueue<String> trail; // connect infos from cytag servlet
    public SeedDB peers;
    public WorkTables tables;
    public Tray tray;
    private long lastStats = 0; // time when the last row was written to the stats table

    public WorkflowProcessor<IndexingQueueEntry> indexingDocumentProcessor;
    public WorkflowProcessor<IndexingQueueEntry> indexingCondensementProcessor;
    public WorkflowProcessor<IndexingQueueEntry> indexingAnalysisProcessor;
    public WorkflowProcessor<IndexingQueueEntry> indexingStorageProcessor;

    public RobotsTxtConfig robotstxtConfig = null;
    public boolean useTailCache;
    public boolean exceed134217727;

    public final long startupTime = System.currentTimeMillis();
    private final Semaphore shutdownSync = new Semaphore(0);
    private boolean terminate = false;
    private boolean startupAction = true; // this is set to false after the first event
    private static Switchboard sb;
    public HashMap<String, Object[]> crawlJobsStatus = new HashMap<String, Object[]>();

    public Switchboard(final File dataPath, final File appPath, final String initPath, final String configPath) {
        super(dataPath, appPath, initPath, configPath);
        sb = this;
        // check if port is already occupied
        final int port = getLocalPort();
        if (TimeoutRequest.ping(Domains.LOCALHOST, port, 500)) {
            throw new RuntimeException(
                    "a server is already running on the YaCy port "
                    + port
                    + "; possibly another YaCy process has not terminated yet. Please stop YaCy before running a new instance.");
        }

        MemoryTracker.startSystemProfiling();

        // set loglevel and log
        setLog(new ConcurrentLog("SWITCHBOARD"));
        AccessTracker.setDumpFile(new File(dataPath, "DATA/LOG/queries.log"));

        // set default peer name
        Seed.ANON_PREFIX = getConfig("peernameprefix", "_anon");

        // set timeoutrequests
        boolean timeoutrequests = getConfigBool("timeoutrequests", true);
        TimeoutRequest.enable = timeoutrequests;
        
        // UPnP port mapping
        if ( getConfigBool(SwitchboardConstants.UPNP_ENABLED, false) ) {
            InstantBusyThread.oneTimeJob(UPnP.class, "addPortMappings", 0);
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
        final File indexPath = getDataPath(SwitchboardConstants.INDEX_PRIMARY_PATH, SwitchboardConstants.INDEX_PATH_DEFAULT);
        this.log.config("Index Primary Path: " + indexPath.toString());
        final File archivePath = getDataPath(SwitchboardConstants.INDEX_ARCHIVE_PATH, SwitchboardConstants.INDEX_ARCHIVE_DEFAULT);
        this.log.config("Index Archive Path: " + archivePath.toString());
        this.listsPath =
            getDataPath(SwitchboardConstants.LISTS_PATH, SwitchboardConstants.LISTS_PATH_DEFAULT);
        this.log.config("Lists Path:     " + this.listsPath.toString());
        this.htDocsPath =
            getDataPath(SwitchboardConstants.HTDOCS_PATH, SwitchboardConstants.HTDOCS_PATH_DEFAULT);
        this.log.config("HTDOCS Path:    " + this.htDocsPath.toString());
        this.workPath = getDataPath(SwitchboardConstants.WORK_PATH, SwitchboardConstants.WORK_PATH_DEFAULT);
        this.workPath.mkdirs();
        // if default work files exist, copy them (don't overwrite existing!)
        File defaultWorkPath = new File(appPath, "defaults/data/work");
        if (defaultWorkPath.list() != null) {
            for (String fs : defaultWorkPath.list()) {
                File wf = new File(this.workPath, fs);
                if (!wf.exists()) {
                    try {
                        Files.copy(new File(defaultWorkPath, fs), wf);
                    } catch (final IOException e) {
                        ConcurrentLog.logException(e);
                    }
                }
            }
        }
        
        this.log.config("Work Path:    " + this.workPath.toString());

        this.dictionariesPath =
            getDataPath(
                SwitchboardConstants.DICTIONARY_SOURCE_PATH,
                SwitchboardConstants.DICTIONARY_SOURCE_PATH_DEFAULT);
        this.log.config("Dictionaries Path:" + this.dictionariesPath.toString());
        if (!this.dictionariesPath.exists()) this.dictionariesPath.mkdirs();
        
        this.classificationPath =
                getDataPath(
                    SwitchboardConstants.CLASSIFICATION_SOURCE_PATH,
                    SwitchboardConstants.CLASSIFICATION_SOURCE_PATH_DEFAULT);
            this.log.config("Classification Path:" + this.classificationPath.toString());
        if (!this.classificationPath.exists()) this.classificationPath.mkdirs();

        CollectionConfiguration.UNIQUE_HEURISTIC_PREFER_HTTPS = this.getConfigBool("search.ranking.uniqueheuristic.preferhttps", false);
        CollectionConfiguration.UNIQUE_HEURISTIC_PREFER_WWWPREFIX = this.getConfigBool("search.ranking.uniqueheuristic.preferwwwprefix", true);
        
        
        // init libraries
        this.log.config("initializing libraries");
        new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("LibraryProvider.initialize");
                LibraryProvider.initialize(Switchboard.this.dictionariesPath);
                // persistent Vocabulary Switch
                Set<String> omit = Switchboard.this.getConfigSet("search.result.show.vocabulary.omit");
                for (String o: omit) {
                    Tagging t = LibraryProvider.autotagging.getVocabulary(o);
                    if (t != null) t.setFacet(false);
                }

                Thread.currentThread().setName("ProbabilisticClassification.initialize");
                ProbabilisticClassifier.initialize(Switchboard.this.classificationPath);
            }
        }.start();
        
        // init the language detector
        this.log.config("Loading language profiles");
        try {
			DetectorFactory.loadProfile(new File(appPath, "langdetect").toString());
		} catch (LangDetectException e) {
			ConcurrentLog.logException(e);
		}

        // init global host name cache
        Domains.init(new File(this.workPath, "globalhosts.list"));

        // init sessionid name file
        final String sessionidNamesFile = getConfig("sessionidNamesFile", "defaults/sessionid.names");
        this.log.config("Loading sessionid file " + sessionidNamesFile);
        MultiProtocolURL.initSessionIDNames(FileUtils.loadList(new File(getAppPath(), sessionidNamesFile)));

        // init tables
        this.tables = new WorkTables(this.workPath);

        // set a high maximum cache size to current size; this is adopted later automatically
        final int wordCacheMaxCount = (int) getConfigLong(SwitchboardConstants.WORDCACHE_MAX_COUNT, 20000);
        setConfig(SwitchboardConstants.WORDCACHE_MAX_COUNT, Integer.toString(wordCacheMaxCount));

        // load the network definition
        try {
            overwriteNetworkDefinition(getSysinfo());
        } catch (final FileNotFoundException e) {
            ConcurrentLog.logException(e);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        // create custom user agent
        ClientIdentification.generateCustomBot(
                getConfig(SwitchboardConstants.CRAWLER_USER_AGENT_NAME, ""),
                getConfig(SwitchboardConstants.CRAWLER_USER_AGENT_STRING, ""),
                (int) getConfigLong(SwitchboardConstants.CRAWLER_USER_AGENT_MINIMUMDELTA, 500),
                (int) getConfigLong(SwitchboardConstants.CRAWLER_USER_AGENT_CLIENTTIMEOUT , 1000));
        
        // start indexing management
        this.log.config("Starting Indexing Management");
        final String networkName = getConfig(SwitchboardConstants.NETWORK_NAME, "");
        final long fileSizeMax = (OS.isWindows) ? this.getConfigLong("filesize.max.win", Integer.MAX_VALUE) : this.getConfigLong( "filesize.max.other", Integer.MAX_VALUE);
        final int redundancy = (int) this.getConfigLong("network.unit.dhtredundancy.senior", 1);
        final int partitionExponent = (int) this.getConfigLong("network.unit.dht.partitionExponent", 0);
        this.networkRoot = new File(new File(indexPath, networkName), "NETWORK");
        this.queuesRoot = new File(new File(indexPath, networkName), "QUEUES");
        this.networkRoot.mkdirs();
        this.queuesRoot.mkdirs();

        // prepare a solr index profile switch list
        final File solrCollectionConfigurationInitFile = new File(getAppPath(),  "defaults/" + SOLR_COLLECTION_CONFIGURATION_NAME);
        final File solrCollectionConfigurationWorkFile = new File(getDataPath(), "DATA/SETTINGS/" + SOLR_COLLECTION_CONFIGURATION_NAME);
        final File solrWebgraphConfigurationInitFile   = new File(getAppPath(),  "defaults/" + SOLR_WEBGRAPH_CONFIGURATION_NAME);
        final File solrWebgraphConfigurationWorkFile   = new File(getDataPath(), "DATA/SETTINGS/" + SOLR_WEBGRAPH_CONFIGURATION_NAME);
        CollectionConfiguration solrCollectionConfigurationWork = null;
        WebgraphConfiguration solrWebgraphConfigurationWork = null;

        // migrate the old Schema file path to a new one
        final File solrCollectionConfigurationWorkOldFile = new File(getDataPath(), "DATA/SETTINGS/" + SOLR_COLLECTION_CONFIGURATION_NAME_OLD);
        if (solrCollectionConfigurationWorkOldFile.exists() && !solrCollectionConfigurationWorkFile.exists()) solrCollectionConfigurationWorkOldFile.renameTo(solrCollectionConfigurationWorkFile);

        // initialize the collection schema if it does not yet exist
        if (!solrCollectionConfigurationWorkFile.exists()) try {
            Files.copy(solrCollectionConfigurationInitFile, solrCollectionConfigurationWorkFile);
        } catch (final IOException e) {ConcurrentLog.logException(e);}

        // lazy definition of schema: do not write empty fields
        final boolean solrlazy = getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_LAZY, true);

        // define collection schema
        try {
            final CollectionConfiguration solrCollectionConfigurationInit = new CollectionConfiguration(solrCollectionConfigurationInitFile, solrlazy);
            solrCollectionConfigurationWork = new CollectionConfiguration(solrCollectionConfigurationWorkFile, solrlazy);
            // update the working scheme with the backup scheme. This is necessary to include new features.
            // new features are always activated by default (if activated in input-backupScheme)
            solrCollectionConfigurationWork.fill(solrCollectionConfigurationInit, true);
            // switch on some fields which are necessary for ranking and faceting
            SchemaConfiguration.Entry entry;
            for (CollectionSchema field: new CollectionSchema[]{
                    CollectionSchema.host_s, CollectionSchema.load_date_dt,
                    CollectionSchema.url_file_ext_s, CollectionSchema.last_modified,                      // needed for media search and /date operator
                    /*YaCySchema.url_paths_sxt,*/ CollectionSchema.host_organization_s,                   // needed to search in the url
                    /*YaCySchema.inboundlinks_protocol_sxt,*/ CollectionSchema.inboundlinks_urlstub_sxt,  // needed for HostBrowser
                    /*YaCySchema.outboundlinks_protocol_sxt,*/ CollectionSchema.outboundlinks_urlstub_sxt,// needed to enhance the crawler
                    CollectionSchema.httpstatus_i                                                         // used in all search queries to filter out error documents
                }) {
                entry = solrCollectionConfigurationWork.get(field.name());
                if (entry != null) {
                    entry.setEnable(true);
                    solrCollectionConfigurationWork.put(field.name(), entry);
                }
            }
            
            // activate some fields that are necessary here
            entry = solrCollectionConfigurationWork.get(CollectionSchema.images_urlstub_sxt.getSolrFieldName());
            if (entry != null) {
                entry.setEnable(true);
                solrCollectionConfigurationWork.put(CollectionSchema.images_urlstub_sxt.getSolrFieldName(), entry);
            }
            solrCollectionConfigurationWork.commit();
        } catch (final IOException e) {ConcurrentLog.logException(e);}
        
        // initialize the webgraph schema if it does not yet exist
        if (!solrWebgraphConfigurationWorkFile.exists()) try {
            Files.copy(solrWebgraphConfigurationInitFile, solrWebgraphConfigurationWorkFile);
        } catch (final IOException e) {ConcurrentLog.logException(e);}
        
        // define webgraph schema
        try {
            final WebgraphConfiguration solrWebgraphConfigurationInit = new WebgraphConfiguration(solrWebgraphConfigurationInitFile, solrlazy);
            solrWebgraphConfigurationWork = new WebgraphConfiguration(solrWebgraphConfigurationWorkFile, solrlazy);
            solrWebgraphConfigurationWork.fill(solrWebgraphConfigurationInit, true);
            solrWebgraphConfigurationWork.commit();
        } catch (final IOException e) {ConcurrentLog.logException(e);}

        // define boosts
        Ranking.setMinTokenLen(this.getConfigInt(SwitchboardConstants.SEARCH_RANKING_SOLR_DOUBLEDETECTION_MINLENGTH, 3));
        Ranking.setQuantRate(this.getConfigFloat(SwitchboardConstants.SEARCH_RANKING_SOLR_DOUBLEDETECTION_QUANTRATE, 0.5f));
        for (int i = 0; i <= 3; i++) {
            // must be done every time the boosts change
            Ranking r = solrCollectionConfigurationWork.getRanking(i);
            String name = this.getConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTNAME_ + i, "_dummy" + i);
            String boosts = this.getConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFIELDS_ + i, "text_t^1.0");
            String fq = this.getConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_FILTERQUERY_ + i, "");
            String bq = this.getConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTQUERY_ + i, "");
            String bf = this.getConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFUNCTION_ + i, "");
            // apply some hard-coded patches for earlier experiments we do not want any more
            if (bf.equals("product(recip(rord(last_modified),1,1000,1000),div(product(log(product(references_external_i,references_exthosts_i)),div(references_internal_i,host_extent_i)),add(crawldepth_i,1)))") ||
                bf.equals("scale(cr_host_norm_i,1,20)")) bf = "";
            if (bf.equals("recip(rord(last_modified),1,1000,1000))")) bf = "recip(ms(NOW,last_modified),3.16e-11,1,1)"; // that was an outdated date boost that did not work well
            if (i == 0 && bq.equals("fuzzy_signature_unique_b:true^100000.0")) bq = "crawldepth_i:0^0.8 crawldepth_i:1^0.4";
            if (bq.equals("crawldepth_i:0^0.8 crawldepth_i:1^0.4")) bq = "crawldepth_i:0^0.8\ncrawldepth_i:1^0.4"; // Fix issue with multiple Boost Queries
            if (boosts.equals("url_paths_sxt^1000.0,synonyms_sxt^1.0,title^10000.0,text_t^2.0,h1_txt^1000.0,h2_txt^100.0,host_organization_s^100000.0")) boosts = "url_paths_sxt^3.0,synonyms_sxt^0.5,title^5.0,text_t^1.0,host_s^6.0,h1_txt^5.0,url_file_name_tokens_t^4.0,h2_txt^2.0";
            r.setName(name);
            r.updateBoosts(boosts);
            r.setFilterQuery(fq);
            r.setBoostQuery(bq);
            r.setBoostFunction(bf);
        }

        // initialize index
        ReferenceContainer.maxReferences = getConfigInt("index.maxReferences", 0);
        final File segmentsPath = new File(new File(indexPath, networkName), "SEGMENTS");
        try {this.index = new Segment(this.log, segmentsPath, archivePath, solrCollectionConfigurationWork, solrWebgraphConfigurationWork);} catch (IOException e) {ConcurrentLog.logException(e);}
        if (this.getConfigBool(SwitchboardConstants.CORE_SERVICE_RWI, true)) try {
            this.index.connectRWI(wordCacheMaxCount, fileSizeMax);
        } catch (final IOException e) {ConcurrentLog.logException(e);}
        if (this.getConfigBool(SwitchboardConstants.CORE_SERVICE_CITATION, true)) try {
            this.index.connectCitation(wordCacheMaxCount, fileSizeMax);
        } catch (final IOException e) {ConcurrentLog.logException(e);}
        if (this.getConfigBool(SwitchboardConstants.CORE_SERVICE_FULLTEXT, true)) {
            try {this.index.fulltext().connectLocalSolr();} catch (final IOException e) {ConcurrentLog.logException(e);}
        }
        this.index.fulltext().setUseWebgraph(this.getConfigBool(SwitchboardConstants.CORE_SERVICE_WEBGRAPH, false));

        // set up the solr interface
        final String solrurls = getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_URL, "http://127.0.0.1:8983/solr");
        final boolean usesolr = getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED, false) & solrurls.length() > 0;
        final int solrtimeout = getConfigInt(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_TIMEOUT, 60000);
        final boolean writeEnabled = getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_WRITEENABLED, true);

        if (usesolr && solrurls != null && solrurls.length() > 0) {
            try {
                ArrayList<RemoteInstance> instances = RemoteInstance.getShardInstances(solrurls, null, null, solrtimeout);
                String shardMethodName = getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_SHARDING, ShardSelection.Method.MODULO_HOST_MD5.name());
                ShardSelection.Method shardMethod = ShardSelection.Method.valueOf(shardMethodName);
                this.index.fulltext().connectRemoteSolr(instances, shardMethod, writeEnabled);
            } catch (final IOException e ) {
                ConcurrentLog.logException(e);
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
            if (!getConfig("network.unit.domainlist", "").equals("")) {
                final Reader r = getConfigFileFromWebOrLocally(
                        getConfig("network.unit.domainlist", ""),
                        getAppPath().getAbsolutePath(),
                        new File(this.networkRoot, "domainlist.txt"));
                this.domainList = new FilterEngine();
                BufferedReader br = new BufferedReader(r);
                this.domainList.loadList(br, null);
                br.close();
            }
        } catch (final FileNotFoundException e ) {
            this.log.severe("CONFIG: domainlist not found: " + e.getMessage());
        } catch (final IOException e ) {
            this.log.severe("CONFIG: error while retrieving domainlist: " + e.getMessage());
        }

        // create a crawler
        this.crawler = new CrawlSwitchboard(this);

        // start yacy core
        this.log.config("Starting YaCy Protocol Core");
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
        this.adminAuthenticationLastAccess = 0; // timestamp last admin authentication (as not autenticated here, stamp with 0)
        this.optimizeLastRun = System.currentTimeMillis();
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
            this.log.config("loaded blue-list from file "
                + plasmaBlueListFile.getName()
                + ", "
                + blueList.size()
                + " entries, "
                + ppRamString(plasmaBlueListFile.length() / 1024));
        }

        // load blacklist
        this.log.config("Loading blacklist ...");
        final File blacklistsPath =
            getDataPath(SwitchboardConstants.LISTS_PATH, SwitchboardConstants.LISTS_PATH_DEFAULT);
        urlBlacklist = new Blacklist(blacklistsPath);
        ListManager.switchboard = this;
        ListManager.listsPath = blacklistsPath;
        ListManager.reloadBlacklists();

        // Set jvm default locale to match UI language (
        String lng = this.getConfig("locale.language", "en");
        if (!"browser".equals(lng) && !"default".equals(lng)) {
            Locale.setDefault(new Locale(lng));
        } else {
            lng = "en"; // default = English
        }

        // load badwords (to filter the topwords)
        if ( badwords == null || badwords.isEmpty() ) {
            File badwordsFile = new File(appPath, "DATA/SETTINGS/" + SwitchboardConstants.LIST_BADWORDS_DEFAULT);
            if (!badwordsFile.exists()) {
                badwordsFile = new File(appPath, "defaults/" + SwitchboardConstants.LIST_BADWORDS_DEFAULT);
            }
            badwords = SetTools.loadList(badwordsFile, NaturalOrder.naturalComparator);
            this.log.config("loaded badwords from file "
                + badwordsFile.getName()
                + ", "
                + badwords.size()
                + " entries, "
                + ppRamString(badwordsFile.length() / 1024));
        }

        // load stopwords (to filter query and topwords)
        if ( stopwords == null || stopwords.isEmpty() ) {
            File stopwordsFile = new File(dataPath, "DATA/SETTINGS/" + SwitchboardConstants.LIST_STOPWORDS_DEFAULT);
            if (!stopwordsFile.exists()) {
                stopwordsFile = new File(appPath, "defaults/"+SwitchboardConstants.LIST_STOPWORDS_DEFAULT);
            }
            stopwords = SetTools.loadList(stopwordsFile, NaturalOrder.naturalComparator);
            // append locale language stopwords using setting of interface language (file yacy.stopwords.xx)
            // english is stored as default (needed for locale html file overlay)
            File stopwordsFilelocale = new File (dataPath, "DATA/SETTINGS/"+stopwordsFile.getName()+"."+lng);
            if (!stopwordsFilelocale.exists()) stopwordsFilelocale = new File (appPath, "defaults/"+stopwordsFile.getName()+"."+lng);
            if (stopwordsFilelocale.exists()) {
                // load YaCy locale stopword list
                stopwords.addAll(SetTools.loadList(stopwordsFilelocale, NaturalOrder.naturalComparator));
                this.log.config("append stopwords from file " + stopwordsFilelocale.getName());
            } else {
                // alternatively load/append default solr stopword list
                stopwordsFilelocale = new File (appPath, "defaults/solr/lang/stopwords_" + lng + ".txt");
                if (stopwordsFilelocale.exists()) {
                    stopwords.addAll(SetTools.loadList(stopwordsFilelocale, NaturalOrder.naturalComparator));
                    this.log.config("append stopwords from file " + stopwordsFilelocale.getName());
                }
            }
        }

        // start a cache manager
        this.log.config("Starting HT Cache Manager");

        // create the cache directory
        this.htCachePath =
            getDataPath(SwitchboardConstants.HTCACHE_PATH, SwitchboardConstants.HTCACHE_PATH_DEFAULT);
        this.log.info("HTCACHE Path = " + this.htCachePath.getAbsolutePath());
        final long maxCacheSize =
            1024L * 1024L * Long.parseLong(getConfig(SwitchboardConstants.PROXY_CACHE_SIZE, "2")); // this is megabyte
        Cache.init(this.htCachePath, this.peers.mySeed().hash, maxCacheSize);
        final File transactiondir = new File(this.htCachePath, "snapshots");
        Transactions.init(transactiondir);

        // create the surrogates directories
        this.surrogatesInPath =
            getDataPath(
                SwitchboardConstants.SURROGATES_IN_PATH,
                SwitchboardConstants.SURROGATES_IN_PATH_DEFAULT);
        this.log.info("surrogates.in Path = " + this.surrogatesInPath.getAbsolutePath());
        this.surrogatesInPath.mkdirs();
        this.surrogatesOutPath =
            getDataPath(
                SwitchboardConstants.SURROGATES_OUT_PATH,
                SwitchboardConstants.SURROGATES_OUT_PATH_DEFAULT);
        this.log.info("surrogates.out Path = " + this.surrogatesOutPath.getAbsolutePath());
        this.surrogatesOutPath.mkdirs();

        // copy opensearch heuristic config (if not exist)
        final File osdConfig = new File(getDataPath(), "DATA/SETTINGS/heuristicopensearch.conf");
        if (!osdConfig.exists()) {
            final File osdDefaultConfig = new File(appPath, "defaults/heuristicopensearch.conf");
            this.log.info("heuristic.opensearch list Path = " + osdDefaultConfig.getAbsolutePath());
            try {
                Files.copy(osdDefaultConfig, osdConfig);
            } catch (final IOException ex) { }
        }

        // create the release download directory
        this.releasePath =
            getDataPath(SwitchboardConstants.RELEASE_PATH, SwitchboardConstants.RELEASE_PATH_DEFAULT);
        this.releasePath.mkdirs();
        this.log.info("RELEASE Path = " + this.releasePath.getAbsolutePath());

        // starting message board
        try {
            initMessages();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }

        // starting wiki
        try {
            initWiki();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }

        //starting blog
        try {
            initBlog();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }

        // init User DB
        this.log.config("Loading User DB");
        final File userDbFile = new File(getDataPath(), "DATA/SETTINGS/user.heap");
        try {
            this.userDB = new UserDB(userDbFile);
            this.log.config("Loaded User DB from file "
                    + userDbFile.getName()
                    + ", "
                    + this.userDB.size()
                    + " entries"
                    + ", "
                    + ppRamString(userDbFile.length() / 1024));
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }

        // init html parser evaluation scheme
        File parserPropertiesPath = new File(appPath, "defaults/");
        String[] settingsList = parserPropertiesPath.list();
        for ( final String l : settingsList ) {
            if ( l.startsWith("parser.") && l.endsWith(".properties") ) {
                try {
                    Evaluation.add(new File(parserPropertiesPath, l));
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                }
            }
        }
        parserPropertiesPath = new File(getDataPath(), "DATA/SETTINGS/");
        settingsList = parserPropertiesPath.list();
        for ( final String l : settingsList ) {
            if ( l.startsWith("parser.") && l.endsWith(".properties") ) {
                try {
                    Evaluation.add(new File(parserPropertiesPath, l));
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                }
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
                } catch (final IOException e ) {
                    ConcurrentLog.logException(e);
                }
            }
        }.start();

        // define a realtime parsable mimetype list
        this.log.config("Parser: Initializing Mime Type deny list");
        
    	final boolean enableAudioTags = getConfigBool("parser.enableAudioTags", false);
        log.config("Parser: parser.enableAudioTags= "+enableAudioTags);
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
        pdfParser.individualPages = getConfigBool(SwitchboardConstants.PARSER_PDF_INDIVIDUALPAGES, false);
        pdfParser.individualPagePropertyname = getConfig(SwitchboardConstants.PARSER_PDF_INDIVIDUALPAGES_KEY, "page");

        // start a loader
        this.log.config("Starting Crawl Loader");
        this.loader = new LoaderDispatcher(this);
        
        // load the robots.txt db
        this.log.config("Initializing robots.txt DB");
		this.robots = new RobotsTxt(this.tables, this.loader,
				this.getConfigInt(SwitchboardConstants.ROBOTS_TXT_THREADS_ACTIVE_MAX, SwitchboardConstants.ROBOTS_TXT_THREADS_ACTIVE_MAX_DEFAULT));
        try {
            this.log.config("Loaded robots.txt DB: " + this.robots.size() + " entries");
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }

        // load oai tables
        final Map<String, File> oaiFriends =
            OAIListFriendsLoader.loadListFriendsSources(
                new File(appPath, "defaults/oaiListFriendsSource.xml"),
                getDataPath());
        OAIListFriendsLoader.init(this.loader, oaiFriends, ClientIdentification.yacyInternetCrawlerAgent);
        this.crawlQueues = new CrawlQueues(this, this.queuesRoot);

        // on startup, resume all crawls
        setConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL + "_isPaused", "false");
        setConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL + "_isPaused_cause", "");
        setConfig(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL + "_isPaused", "false");
        setConfig(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL + "_isPaused_cause", "");
        setConfig(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER + "_isPaused", "false");
        setConfig(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER + "_isPaused_cause", "");
        this.crawlJobsStatus.put(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL, new Object[] {new Object(), false});
        this.crawlJobsStatus.put(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL, new Object[] {new Object(), false});
        this.crawlJobsStatus.put(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER, new Object[] {new Object(), false});

        // init cookie-Monitor
        this.log.config("Starting Cookie Monitor");
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
        } catch (final IOException e ) {
        }

        // init nameCacheNoCachingList
        try {
            Domains.setNoCachingPatterns(getConfig(SwitchboardConstants.HTTPC_NAME_CACHE_CACHING_PATTERNS_NO, ""));
        } catch (final PatternSyntaxException pse) {
            ConcurrentLog.severe("Switchboard", "Invalid regular expression in "
                            + SwitchboardConstants.HTTPC_NAME_CACHE_CACHING_PATTERNS_NO
                            + " property: " + pse.getMessage());
            System.exit(-1);
        }

        // generate snippets cache
        this.log.config("Initializing Snippet Cache");

        // init the wiki
        wikiParser = new WikiCode();

        // initializing the resourceObserver
        this.observer = new ResourceObserver(this);
        InstantBusyThread.oneTimeJob(this.observer, "resourceObserverJob", 0);

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
        if ( !getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false) ) {
            if ( getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "").startsWith("0000") ) {
                // the password was set automatically with a random value.
                // We must remove that here to prevent that a user cannot log in any more
                setConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "");
                // after this a message must be generated to alert the user to set a new password
                this.log.info("RANDOM PASSWORD REMOVED! User must set a new password");
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
                1);
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
        this.log.config("Starting Threads");
        MemoryControl.gc(10000, "plasmaSwitchboard, help for profiler"); // help for profiler - thq

        deployThread(
                SwitchboardConstants.CLEANUP,
                "Cleanup",
                "cleaning process",
                null,
                new InstantBusyThread(
                    this,
                    SwitchboardConstants.CLEANUP_METHOD_START,
                    SwitchboardConstants.CLEANUP_METHOD_JOBCOUNT,
                    SwitchboardConstants.CLEANUP_METHOD_FREEMEM,
                    30000,
                    10000),
                60000); // all 10 minutes, wait 1 minute until first run
        deployThread(
                SwitchboardConstants.SCHEDULER,
                "Scheduler",
                "starts scheduled processes from the API Processing table",
                null,
                new InstantBusyThread(
                    this,
                    SwitchboardConstants.SCHEDULER_METHOD_START,
                    SwitchboardConstants.SCHEDULER_METHOD_JOBCOUNT,
                    SwitchboardConstants.SCHEDULER_METHOD_FREEMEM,
                    30000,
                    10000),
                60000); // all 10 minutes, wait 1 minute until first run
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
                0),
            10000);

        this.initRemoteCrawler(this.getConfigBool(SwitchboardConstants.CRAWLJOB_REMOTE, false));
        this.initAutocrawl(this.getConfigBool(SwitchboardConstants.AUTOCRAWL, false));

        deployThread(
            SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL,
            "Local Crawl",
            "thread that performes a single crawl step from the local crawl queue",
            "/IndexCreateQueues_p.html?stack=LOCAL",
            new InstantBusyThread(
                this.crawlQueues,
                SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_METHOD_START,
                SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_METHOD_JOBCOUNT,
                SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_METHOD_FREEMEM,
                0,
                0),
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
                300000),
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
                30000),
            10000);
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
                1000),
            60000,
            Long.parseLong(getConfig(SwitchboardConstants.INDEX_DIST_IDLESLEEP, "5000")),
            Long.parseLong(getConfig(SwitchboardConstants.INDEX_DIST_BUSYSLEEP, "0")),
            Long.parseLong(getConfig(SwitchboardConstants.INDEX_DIST_MEMPREREQ, "1000000")),
            Double.parseDouble(getConfig(SwitchboardConstants.INDEX_DIST_LOADPREREQ, "9.0")));

        // content control: initialize list sync thread
        deployThread(
            "720_ccimport",
            "Content Control Import",
            "this is the content control import thread",
            null,
            new InstantBusyThread(
                new SMWListSyncThread(this, sb.getConfig("contentcontrol.bookmarklist", "contentcontrol"), "Category:Content Source", "/?Url/?Filter/?Category/?Modification date", sb.getConfigBool(
        				"contentcontrol.smwimport.purgelistoninit", false)),
                "run",
                SwitchboardConstants.PEER_PING_METHOD_JOBCOUNT,
                SwitchboardConstants.PEER_PING_METHOD_FREEMEM,
                3000,
                3000),
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
                3000),
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

        // set configurable ui defaults
        QueryParams.FACETS_STANDARD_MAXCOUNT = sb.getConfigInt(SwitchboardConstants.SEARCH_NAVIGATION_MAXCOUNT, QueryParams.FACETS_STANDARD_MAXCOUNT); // max number of navigator/facet lines
        
        this.log.config("Finished Switchboard Initialization");
    }

    final String getSysinfo() {
        return getConfig(SwitchboardConstants.NETWORK_NAME, "") + (isRobinsonMode() ? "-" : "/") + getConfig(SwitchboardConstants.NETWORK_DOMAIN, "global");
    }
    
    @Override
    public void setHttpServer(YaCyHttpServer server) {
        super.setHttpServer(server);
        
        // finally start jobs which shall be started after start-up
        new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("Switchboard.setHttpServer");
                try {Thread.sleep(10000);} catch (final InterruptedException e) {} // needs httpd up
                schedulerJob(); // trigger startup actions
            }
        }.start();        
    }
    
    public int getIndexingProcessorsQueueSize() {
        return this.indexingDocumentProcessor.getQueueSize()
            + this.indexingCondensementProcessor.getQueueSize()
            + this.indexingAnalysisProcessor.getQueueSize()
            + this.indexingStorageProcessor.getQueueSize();
    }

    public void overwriteNetworkDefinition(final String sysinfo) throws FileNotFoundException, IOException {

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
                DigestURL locationURL;
                try {
                    // try to parse url
                    locationURL = new DigestURL(location);
                } catch (final MalformedURLException e ) {
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
                } catch (final InvalidKeySpecException e ) {
                    ConcurrentLog.logException(e);
                }
                final yacyUpdateLocation updateLocation = new yacyUpdateLocation(locationURL, publicKey);
                yacyRelease.latestReleaseLocations.add(updateLocation);
                i++;
            }
        } catch ( final NoSuchAlgorithmException e1 ) {
            // TODO Auto-generated catch block
            ConcurrentLog.logException(e1);
        }

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
        ClientIdentification.generateYaCyBot(sysinfo);
    }

    /**
     * Switch network configuration to the new specified one
     * @param networkDefinition YaCy network definition file path (relative to the app path) or absolute URL
     * @throws FileNotFoundException when the file was not found
     * @throws IOException when an error occured
     */
    public void switchNetwork(final String networkDefinition) throws FileNotFoundException, IOException {
        this.log.info("SWITCH NETWORK: switching to '" + networkDefinition + "'");
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
        this.log.info("SWITCH NETWORK: SHUT DOWN OF OLD INDEX DATABASE...");
        // clean search events which have cached relations to the old index
        SearchEventCache.cleanupEvents(true);

        // switch the networks
        synchronized ( this ) {

            // remember the solr scheme
            CollectionConfiguration collectionConfiguration = this.index.fulltext().getDefaultConfiguration();
            WebgraphConfiguration webgraphConfiguration = this.index.fulltext().getWebgraphConfiguration();

            // shut down
            this.crawler.close();
            if ( this.dhtDispatcher != null ) {
                this.dhtDispatcher.close();
            }
            /* Crawlstacker is eventually triggering write operations on this.index : we must therefore close it before closing this.index */
            this.crawlStacker.announceClose();
            this.crawlStacker.close();
            
            synchronized ( this.index ) {
                this.index.close();
            }
            this.webStructure.close();

            this.log.info("SWITCH NETWORK: START UP OF NEW INDEX DATABASE...");

            // new properties
            setConfig("network.unit.definition", networkDefinition);
            overwriteNetworkDefinition(getSysinfo());
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
            setConfig(SwitchboardConstants.HEURISTIC_SITE, false);
            setConfig(SwitchboardConstants.HEURISTIC_OPENSEARCH, false);

            // relocate
            this.peers.relocate(
                this.networkRoot,
                redundancy,
                partitionExponent,
                this.useTailCache,
                this.exceed134217727);
            final File segmentsPath = new File(new File(indexPrimaryPath, networkName), "SEGMENTS");
            final File archivePath = getDataPath(SwitchboardConstants.INDEX_ARCHIVE_PATH, SwitchboardConstants.INDEX_ARCHIVE_DEFAULT);
            this.index = new Segment(this.log, segmentsPath, archivePath, collectionConfiguration, webgraphConfiguration);
            if (this.getConfigBool(SwitchboardConstants.CORE_SERVICE_RWI, true)) this.index.connectRWI(wordCacheMaxCount, fileSizeMax);
            if (this.getConfigBool(SwitchboardConstants.CORE_SERVICE_CITATION, true)) this.index.connectCitation(wordCacheMaxCount, fileSizeMax);
            if (this.getConfigBool(SwitchboardConstants.CORE_SERVICE_FULLTEXT, true)) {
                this.index.fulltext().connectLocalSolr();
            }
            this.index.fulltext().setUseWebgraph(this.getConfigBool(SwitchboardConstants.CORE_SERVICE_WEBGRAPH, false));

            // set up the solr interface
            final String solrurls = getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_URL, "http://127.0.0.1:8983/solr");
            final boolean usesolr = getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED, false) & solrurls.length() > 0;
            final int solrtimeout = getConfigInt(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_TIMEOUT, 60000);
            final boolean writeEnabled = getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_WRITEENABLED, true);

            if (usesolr && solrurls != null && solrurls.length() > 0) {
                try {
                    ArrayList<RemoteInstance> instances = RemoteInstance.getShardInstances(solrurls, null, null, solrtimeout);
                    String shardMethodName = getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_SHARDING, ShardSelection.Method.MODULO_HOST_MD5.name());
                    ShardSelection.Method shardMethod = ShardSelection.Method.valueOf(shardMethodName);
                    this.index.fulltext().connectRemoteSolr(instances, shardMethod, writeEnabled);
                } catch (final IOException e ) {
                    ConcurrentLog.logException(e);
                }
            }

            // create a crawler
            this.crawlQueues.relocate(this.queuesRoot); // cannot be closed because the busy threads are working with that object
            this.crawler = new CrawlSwitchboard(this);

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
                    final Reader r = getConfigFileFromWebOrLocally(
                            getConfig("network.unit.domainlist", ""),
                            getAppPath().getAbsolutePath(),
                            new File(this.networkRoot, "domainlist.txt"));
                    this.domainList = new FilterEngine();
                    BufferedReader br = new BufferedReader(r);
                    this.domainList.loadList(br, null);
                    br.close();
                }
            } catch (final FileNotFoundException e ) {
                this.log.severe("CONFIG: domainlist not found: " + e.getMessage());
            } catch (final IOException e ) {
                this.log.severe("CONFIG: error while retrieving domainlist: " + e.getMessage());
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
            .info("SWITCH NETWORK: FINISHED START UP, new network is now '" + networkDefinition + "'.");

        // set the network-specific remote crawl ppm
        setRemotecrawlPPM(Math.max(1, (int) getConfigLong("network.unit.remotecrawl.speed", 60)));
    }

    public void setRemotecrawlPPM(final int ppm) {
        final long newBusySleep = Math.max(100, 60000 / ppm);

        // propagate to crawler
        final BusyThread rct = getThread(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
        setConfig(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP, newBusySleep);
        setConfig(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_IDLESLEEP,
                Math.min(10000, newBusySleep * 10));
        if (rct != null) {
            rct.setBusySleep(getConfigLong(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP, 1000));
            rct.setIdleSleep(getConfigLong(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_IDLESLEEP, 10000));
        }

        // propagate to loader
        final BusyThread rcl = getThread(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER);
        setConfig(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER_BUSYSLEEP, newBusySleep * 4);
        setConfig(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER_IDLESLEEP,
                Math.min(10000, newBusySleep * 20));
        if (rcl != null) {
            rcl.setBusySleep(getConfigLong(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER_BUSYSLEEP, 1000));
            rcl.setIdleSleep(getConfigLong(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER_IDLESLEEP, 10000));
        }
    }

    /**
     * Initialisize and perform all settings to enable remote crawls
     * (if remote crawl is not in use, save the resources) If called with
     * activate==false worker threads are closed and removed (to free resources)
     *
     * @param activate true=enable, false=disable
     */
    public void initRemoteCrawler(final boolean activate) {

        this.setConfig(SwitchboardConstants.CRAWLJOB_REMOTE, activate);
        this.peers.mySeed().setFlagAcceptRemoteCrawl(activate);
        if (activate) {
            this.crawlQueues.initRemoteCrawlQueues();

            BusyThread rct = getThread(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
            if (rct == null) {
                deployThread(
                        SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL,
                        "Remote Crawl Job",
                        "thread that performes a single crawl/indexing step triggered by a remote peer",
                        "/IndexCreateQueues_p.html?stack=REMOTE",
                        new InstantBusyThread(
                                this.crawlQueues,
                                SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_METHOD_START,
                                SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_METHOD_JOBCOUNT,
                                SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_METHOD_FREEMEM,
                                0,
                                0),
                        10000);
                rct = getThread(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
            }
            rct.setBusySleep(getConfigLong(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP, 1000));
            rct.setIdleSleep(getConfigLong(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_IDLESLEEP, 10000));

            BusyThread rcl = getThread(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER);
            if (rcl == null) {
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
                                10000),
                        10000);

                rcl = getThread(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER);
            }
            rcl.setBusySleep(getConfigLong(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER_BUSYSLEEP, 1000));
            rcl.setIdleSleep(getConfigLong(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER_IDLESLEEP, 10000));
        } else { // activate==false, terminate and remove threads
            terminateThread(SwitchboardConstants.CRAWLJOB_REMOTE_CRAWL_LOADER, true);
            terminateThread(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL, true);
        }
    }
    
    /**
     * Initialise the Autocrawl thread
     * @param activate true=enable, false=disable
     */
    public void initAutocrawl(final boolean activate) {
        this.setConfig(SwitchboardConstants.AUTOCRAWL, activate);
        if (activate) {
            BusyThread acr = getThread(SwitchboardConstants.CRAWLJOB_AUTOCRAWL);
            if (acr == null) {
                deployThread(
                        SwitchboardConstants.CRAWLJOB_AUTOCRAWL,
                        "Autocrawl",
                        "Thread that selects and automatically adds crawling jobs to the local queue",
                        null,
                        new InstantBusyThread(
                                this.crawlQueues,
                                SwitchboardConstants.CRAWLJOB_AUTOCRAWL_METHOD_START,
                                SwitchboardConstants.CRAWLJOB_AUTOCRAWL_METHOD_JOBCOUNT,
                                SwitchboardConstants.CRAWLJOB_AUTOCRAWL_METHOD_FREEMEM,
                                10000,
                                10000),
                        10000);
                
                acr = getThread(SwitchboardConstants.CRAWLJOB_AUTOCRAWL);
            }
            
            acr.setBusySleep(getConfigLong(SwitchboardConstants.CRAWLJOB_AUTOCRAWL_BUSYSLEEP, 10000));
            acr.setIdleSleep(getConfigLong(SwitchboardConstants.CRAWLJOB_AUTOCRAWL_IDLESLEEP, 10000));
        }
    }

    public void initMessages() throws IOException {
        this.log.config("Starting Message Board");
        final File messageDbFile = new File(this.workPath, "message.heap");
        this.messageDB = new MessageBoard(messageDbFile);
        this.log.config("Loaded Message Board DB from file "
            + messageDbFile.getName()
            + ", "
            + this.messageDB.size()
            + " entries"
            + ", "
            + ppRamString(messageDbFile.length() / 1024));
    }

    public void initWiki() throws IOException {
        this.log.config("Starting Wiki Board");
        final File wikiDbFile = new File(this.workPath, "wiki.heap");
        this.wikiDB = new WikiBoard(wikiDbFile, new File(this.workPath, "wiki-bkp.heap"));
        this.log.config("Loaded Wiki Board DB from file "
            + wikiDbFile.getName()
            + ", "
            + this.wikiDB.size()
            + " entries"
            + ", "
            + ppRamString(wikiDbFile.length() / 1024));
    }

    public void initBlog() throws IOException {
        this.log.config("Starting Blog");
        final File blogDbFile = new File(this.workPath, "blog.heap");
        this.blogDB = new BlogBoard(blogDbFile);
        this.log.config("Loaded Blog DB from file "
            + blogDbFile.getName()
            + ", "
            + this.blogDB.size()
            + " entries"
            + ", "
            + ppRamString(blogDbFile.length() / 1024));

        final File blogCommentDbFile = new File(this.workPath, "blogComment.heap");
        this.blogCommentDB = new BlogBoardComments(blogCommentDbFile);
        this.log.config("Loaded Blog-Comment DB from file "
            + blogCommentDbFile.getName()
            + ", "
            + this.blogCommentDB.size()
            + " entries"
            + ", "
            + ppRamString(blogCommentDbFile.length() / 1024));
    }

    public void initBookmarks() throws IOException {
        this.log.config("Loading Bookmarks DB");
        final File bookmarksFile = new File(this.workPath, "bookmarks.heap");
        final File tagsFile = new File(this.workPath, "bookmarkTags.heap");
        final File datesFile = new File(this.workPath, "bookmarkDates.heap");
        tagsFile.delete();
        this.bookmarksDB = new BookmarksDB(bookmarksFile, datesFile);
        this.log.config("Loaded Bookmarks DB from files "
            + bookmarksFile.getName()
            + ", "
            + tagsFile.getName());
        this.log.config(this.bookmarksDB.tagsSize()
            + " Tag, "
            + this.bookmarksDB.bookmarksSize()
            + " Bookmarks");
    }

    public static Switchboard getSwitchboard() {
        return sb;
    }

    public boolean isP2PMode() {
        return getConfig(SwitchboardConstants.NETWORK_BOOTSTRAP_SEEDLIST_STUB + "0", null) != null;
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
            || (!getConfigBool(SwitchboardConstants.INDEX_DIST_ALLOW, false) &&
                !getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW, false));
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
            return this.clusterhashes.contains(ASCII.getBytes(peer));
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
            return this.clusterhashes.contains(ASCII.getBytes(seed.hash));
        }
        return false;
    }

    /**
     * tests if hash occurs in any database.
     * @param hash
     * @return if it exists, the name of the database is returned, if it not exists, null is returned
     */
    public HarvestProcess urlExists(final String hash) throws IOException {
        LoadTimeURL md = this.index.fulltext().getDefaultConnector().getLoadTimeURL(hash);
        if (md != null && md.date >= 0) return HarvestProcess.LOADED;
        HarvestProcess hp = this.crawlQueues.exists(ASCII.getBytes(hash));
        if (hp != null) return hp;
        return null; // todo: can also be in error
    }

    public void urlRemove(final Segment segment, final byte[] hash) {
        segment.fulltext().remove(hash);
        ResultURLs.remove(ASCII.String(hash));
        this.crawlQueues.removeURL(hash);
    }

    public DigestURL getURL(final byte[] urlhash) throws IOException {
        if (urlhash == null) return null;
        if (urlhash.length == 0) return null;
        final DigestURL url = this.index.fulltext().getURL(ASCII.String(urlhash));
        if (url != null) return url;
        return this.crawlQueues.getURL(urlhash);
    }

    public RankingProfile getRanking() {
        return (getConfig(SwitchboardConstants.SEARCH_RANKING_RWI_PROFILE, "").isEmpty())
            ? new RankingProfile(Classification.ContentDomain.TEXT)
            : new RankingProfile("", crypt.simpleDecode(this.getConfig(SwitchboardConstants.SEARCH_RANKING_RWI_PROFILE, "")));
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
            this.crawlQueues.activeWorkerEntries().size() > 0 ||
            this.crawlQueues.coreCrawlJobSize() > 0 ||
            this.crawlQueues.limitCrawlJobSize() > 0 ||
            this.crawlQueues.remoteTriggeredCrawlJobSize() > 0 ||
            this.crawlQueues.noloadCrawlJobSize() > 0 ||
            (this.crawlStacker != null && !this.crawlStacker.isEmpty()) ||
            !this.crawlQueues.noticeURL.isEmpty()) {
            return false;
        }
        return this.crawler.clear();
    }
    
    public synchronized void close() {
        this.log.config("SWITCHBOARD SHUTDOWN STEP 1: sending termination signal to managed threads:");
        MemoryTracker.stopSystemProfiling();
        terminateAllThreads(true);
        net.yacy.gui.framework.Switchboard.shutdown();
        this.log.config("SWITCHBOARD SHUTDOWN STEP 2: sending termination signal to threaded indexing");
        // closing all still running db importer jobs
        this.crawlStacker.announceClose();
        this.crawlStacker.close();
        this.crawlQueues.close();
        this.robots.close();
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
        this.crawler.close();
        this.log.config("SWITCHBOARD SHUTDOWN STEP 3: sending termination signal to database manager (stand by...)");
        this.index.close();
        this.peers.close();
        Cache.close();
        this.tables.close();
        Domains.close();
        AccessTracker.dumpLog();
        Switchboard.urlBlacklist.close();
        UPnP.deletePortMappings();
        this.tray.remove();
        try {
            HTTPClient.closeConnectionManager();
        } catch (final InterruptedException e ) {
            ConcurrentLog.logException(e);
        }
        this.log.config("SWITCHBOARD SHUTDOWN TERMINATED");
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
                this.log.fine("deQueue: queue entry is null");
            }
            return "queue entry is null";
        }
        if ( response.profile() == null ) {
            if ( this.log.isFine() ) {
                this.log.fine("deQueue: profile is null");
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
            //if (log.isFine()) log.logFine("deQueue: not indexed any word in URL " + response.url() + "; cause: " + noIndexReason);
            // create a new errorURL DB entry
            this.crawlQueues.errorURL.push(response.url(), response.depth(), response.profile(), FailCategory.FINAL_PROCESS_CONTEXT, noIndexReason, -1);
            // finish this entry
            return "not allowed: " + noIndexReason;
        }

        this.indexingDocumentProcessor.enQueue(new IndexingQueueEntry(
            response,
            null,
            null));
        return null;
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
            } catch (final IOException e ) {
                ConcurrentLog.logException(e);
            } finally {
                moved = infile.renameTo(outfile);
                if (zis != null) try {zis.close();} catch (final IOException e) {}
            }
            return moved;
        }
        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(infile));
            if (s.endsWith(".gz")) is = new GZIPInputStream(is, 65535);
            processSurrogate(is, infile.getName());
        } catch (final IOException e ) {
            ConcurrentLog.logException(e);
        } finally {
            if (!shallTerminate()) {
                moved = infile.renameTo(outfile);
                if ( moved ) {
                    // check if this file is already compressed, if not, compress now
                    if ( !outfile.getName().endsWith(".gz") ) {
                        final String gzname = outfile.getName() + ".gz";
                        final File gzfile = new File(outfile.getParentFile(), gzname);
                        try {
                            final OutputStream os = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(gzfile), 65536){{def.setLevel(Deflater.BEST_COMPRESSION);}});
                            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(outfile)); 
                            FileUtils.copy(bis, os);
                            os.close();
                            bis.close();
                            if ( gzfile.exists() ) {
                                FileUtils.deletedelete(outfile);
                            }
                        } catch (final FileNotFoundException e ) {
                            ConcurrentLog.logException(e);
                        } catch (final IOException e ) {
                            ConcurrentLog.logException(e);
                        }
                    }
                    log.info("processed surrogate " + infile);
                }
            }
            if (is != null) try {is.close();} catch (IOException e) {}
        }
        return moved;
    }

    public void processSurrogate(final InputStream is, final String name) throws IOException {
        final int concurrency = Runtime.getRuntime().availableProcessors();

        // start reader thread
        final SurrogateReader reader = new SurrogateReader(is, 100, this.crawlStacker, this.index.fulltext().getDefaultConfiguration(), concurrency);
        final Thread readerThread = new Thread(reader, name);
        readerThread.setPriority(Thread.MAX_PRIORITY); // we must have maximum prio here because this thread feeds the other threads. It must always be ahead of them.
        readerThread.start();
        
        // start indexer threads
        assert this.crawlStacker != null;
        Thread[] indexer = new Thread[concurrency];
        for (int t = 0; t < concurrency; t++) {
            indexer[t] = new Thread("Switchboard.processSurrogate-" + t) {
                @Override
                public void run() {
                    VocabularyScraper scraper = new VocabularyScraper();
                    Object surrogateObj;
                    while ((surrogateObj = reader.take()) != SurrogateReader.POISON_DOCUMENT ) {
                        assert surrogateObj != null;
                        /* When parsing a full-text Solr xml data dump Surrogate reader produces SolrInputDocument instances */
                        if(surrogateObj instanceof SolrInputDocument) {
                        	SolrInputDocument surrogate = (SolrInputDocument)surrogateObj;
                        	try {
                        		// enrich the surrogate
                        		final String id = (String) surrogate.getFieldValue(CollectionSchema.id.getSolrFieldName());
                        		final String text = (String) surrogate.getFieldValue(CollectionSchema.text_t.getSolrFieldName());
                        		if (text != null && text.length() > 0 && id != null ) {
                            		final DigestURL root = new DigestURL((String) surrogate.getFieldValue(CollectionSchema.sku.getSolrFieldName()), ASCII.getBytes(id));
                        			// run the tokenizer on the text to get vocabularies and synonyms
                        			final Tokenizer tokenizer = new Tokenizer(root, text, LibraryProvider.dymLib, true, scraper);
                        			final Map<String, Set<String>> facets = Document.computeGenericFacets(tokenizer.tags());
                        			// overwrite the given vocabularies and synonyms with new computed ones
                        			Switchboard.this.index.fulltext().getDefaultConfiguration().enrich(surrogate, tokenizer.synonyms(), facets);
                        		}
                        	} catch (MalformedURLException e) {
                        		ConcurrentLog.logException(e);
                        	}
                        	// write the surrogate into the index
                        	Switchboard.this.index.putDocument(surrogate);
                        } else if(surrogateObj instanceof DCEntry) {
                        	/* When parsing a MediaWiki dump Surrogate reader produces DCEntry instances */
                            // create a queue entry
                        	final DCEntry entry = (DCEntry)surrogateObj;
                            final Document document = entry.document();
                            final Request request =
                                new Request(
                                    ASCII.getBytes(peers.mySeed().hash),
                                    entry.getIdentifier(true),
                                    null,
                                    "",
                                    entry.getDate(),
                                    crawler.defaultSurrogateProfile.handle(),
                                    0,
                                    crawler.defaultSurrogateProfile.timezoneOffset());
                            final Response response = new Response(request, null, null, crawler.defaultSurrogateProfile, false, null);
                            final IndexingQueueEntry queueEntry =
                                new IndexingQueueEntry(response, new Document[] {document}, null);
                
                            indexingCondensementProcessor.enQueue(queueEntry);
                        }
                        if (shallTerminate()) break;
                    }
                }
            };
            indexer[t].setPriority(5);
            indexer[t].start();
        }
        
        // wait for termination of indexer threads
        for (int t = 0; t < concurrency; t++) {
            try {indexer[t].join();} catch (InterruptedException e) {}
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
                this.log.fine("deQueue: online caution for "
                    + cautionCause
                    + ", omitting resource stack processing");
            }
            return false;
        }

        try {
            // check surrogates
            final String[] surrogatelist = this.surrogatesInPath.list();
            if ( surrogatelist != null && surrogatelist.length > 0 ) {
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

        } catch (final InterruptedException e ) {
            return false;
        }
        return false;
    }
    
    public static void clearCaches() {
        // flush caches in used libraries
        pdfParser.clean_up_idiotic_PDFParser_font_cache_which_eats_up_tons_of_megabytes(); // eats up megabytes, see http://markmail.org/thread/quk5odee4hbsauhu
        
        // clear caches
        if (WordCache.sizeCommonWords() > 1000) WordCache.clearCommonWords();
        Word.clearCache();
        // Domains.clear();
        
        // clean up image stack
        ResultImages.clearQueues();
        
        // flush the document compressor cache
        Cache.commit();
        Digest.cleanup(); // don't let caches become permanent memory leaks

        // clear graphics caches
        CircleTool.clearcache();
        NetworkGraph.clearcache();
    }

    public int schedulerJobSize() {
        try {
            return  this.tables.size(WorkTables.TABLE_API_NAME);
        } catch (IOException e) {
            ConcurrentLog.logException(e);
            return 0;
        }
    }

    /**
     * Check scheduled api calls scheduled execution time and execute all jobs due
     * @return true if calls have been executed
     */
    public boolean schedulerJob() {

        // execute scheduled API actions
        Tables.Row row;
        final Collection<String> pks = new LinkedHashSet<String>();
        final Date now = new Date();
        try {
            final Iterator<Tables.Row> plainIterator = this.tables.iterator(WorkTables.TABLE_API_NAME);
            final Iterator<Tables.Row> mapIterator = Tables.orderBy(plainIterator, -1, WorkTables.TABLE_API_COL_DATE_LAST_EXEC).iterator();
            while (mapIterator.hasNext()) {
                row = mapIterator.next();
                if (row == null) continue;
                
                // select api calls according to scheduler settings
                final int stime = row.get(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 0);
                if (stime > 0) { // has scheduled repeat
                    final Date date_next_exec = row.get(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, (Date) null);
                    if (date_next_exec != null) { // has been executed before
                        if (now.after(date_next_exec)) pks.add(UTF8.String(row.getPK()));
                    } else { // was never executed before
                        pks.add(UTF8.String(row.getPK()));
                    }
                }

                // select api calls according to event settings
                final String kind = row.get(WorkTables.TABLE_API_COL_APICALL_EVENT_KIND, "off");
                if (!"off".equals(kind)) {
                    String action = row.get(WorkTables.TABLE_API_COL_APICALL_EVENT_ACTION, "startup");
                    if ("startup".equals(action)) {
                        if (startupAction) {
                            pks.add(UTF8.String(row.getPK()));
                            if ("once".equals(kind)) {
                                row.put(WorkTables.TABLE_API_COL_APICALL_EVENT_KIND, "off");
                                sb.tables.update(WorkTables.TABLE_API_NAME, row);
                            }
                        }
                    } else try {
                        SimpleDateFormat dateFormat  = new SimpleDateFormat("yyyyMMddHHmm");
                        long d = dateFormat.parse(dateFormat.format(new Date()).substring(0, 8) + action).getTime();
                        long cycle = getThread(SwitchboardConstants.CLEANUP).getBusySleep();
                        if (d < System.currentTimeMillis() && System.currentTimeMillis() - d < cycle) {
                            pks.add(UTF8.String(row.getPK()));
                            if ("once".equals(kind)) {
                                row.put(WorkTables.TABLE_API_COL_APICALL_EVENT_KIND, "off");
                                row.put(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, "");
                                sb.tables.update(WorkTables.TABLE_API_NAME, row);
                            }
                        }
                    } catch (final ParseException e) {}
                }
            }
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        startupAction = false;
        
        // execute api calls
        final Map<String, Integer> callResult = this.tables.execAPICalls("localhost", getLocalPort(), pks, getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"), getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, ""));
        for ( final Map.Entry<String, Integer> call : callResult.entrySet() ) {
            this.log.info("Scheduler executed api call, response " + call.getValue() + ": " + call.getKey());
        }
        return pks.size() > 0;
    }
    
    public int cleanupJobSize() {
        int c = 1; // run this always!
        if (this.crawlQueues.delegatedURL != null && (this.crawlQueues.delegatedURL.size() > 1000) ) {
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
        
        ConcurrentLog.ensureWorkerIsRunning();
        try {
            clearCaches();

            // clear caches if necessary
            if ( !MemoryControl.request(128000000L, false) ) {
                this.index.clearCaches();
                SearchEventCache.cleanupEvents(false);
                this.trail.clear();
                GuiHandler.clear();
            }

            // set a random password if no password is configured
            if ( getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false)
                && getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "").isEmpty() ) {
                // make a 'random' password, this will keep the ability to log in from localhost without password
                setConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "0000" + this.genRandomPassword());
            }

            // stop greedylearning if limit is reached
            if (getConfigBool(SwitchboardConstants.GREEDYLEARNING_ACTIVE, false)) {
                long cs = this.index.fulltext().collectionSize();
                if (cs > getConfigInt(SwitchboardConstants.GREEDYLEARNING_LIMIT_DOCCOUNT, 0)) {
                    setConfig(SwitchboardConstants.GREEDYLEARNING_ACTIVE, false);
                    log.info("finishing greedy learning phase, size=" +cs);
                }
            }
            
            // refresh recrawl dates
            try {
                CrawlProfile selentry;
                for ( final byte[] handle : this.crawler.getActive() ) {
                    selentry = this.crawler.getActive(handle);
                    if ( selentry.handle() == null ) {
                        this.crawler.removeActive(handle);
                        continue;
                    }
                    boolean insert = false;
                    if ( selentry.name().equals(CrawlSwitchboard.CRAWL_PROFILE_PROXY) ) {
                        selentry.put(CrawlProfile.CrawlAttribute.RECRAWL_IF_OLDER.key, Long.toString(CrawlProfile.getRecrawlDate(CrawlSwitchboard.CRAWL_PROFILE_PROXY_RECRAWL_CYCLE).getTime()));
                        insert = true;
                    }
                    if ( selentry.name().equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_TEXT) ) {
                        selentry.put(CrawlProfile.CrawlAttribute.RECRAWL_IF_OLDER.key, Long.toString(CrawlProfile.getRecrawlDate(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_TEXT_RECRAWL_CYCLE).getTime()));
                        insert = true;
                    }
                    if ( selentry.name().equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT) ) {
                        selentry.put(CrawlProfile.CrawlAttribute.RECRAWL_IF_OLDER.key, Long.toString(CrawlProfile.getRecrawlDate(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT_RECRAWL_CYCLE).getTime()));
                        insert = true;
                    }
                    if ( selentry.name().equals(CrawlSwitchboard.CRAWL_PROFILE_GREEDY_LEARNING_TEXT) ) {
                        selentry.put(CrawlProfile.CrawlAttribute.RECRAWL_IF_OLDER.key, Long.toString(CrawlProfile.getRecrawlDate(CrawlSwitchboard.CRAWL_PROFILE_GREEDY_LEARNING_TEXT_RECRAWL_CYCLE).getTime()));
                        insert = true;
                    }
                    if ( selentry.name().equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA) ) {
                        selentry.put(CrawlProfile.CrawlAttribute.RECRAWL_IF_OLDER.key, Long.toString(CrawlProfile.getRecrawlDate(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA_RECRAWL_CYCLE).getTime()));
                        insert = true;
                    }
                    if ( selentry.name().equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA) ) {
                        selentry.put(CrawlProfile.CrawlAttribute.RECRAWL_IF_OLDER.key, Long.toString(CrawlProfile.getRecrawlDate(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA_RECRAWL_CYCLE).getTime()));
                        insert = true;
                    }
                    if ( selentry.name().equals(CrawlSwitchboard.CRAWL_PROFILE_SURROGATE) ) {
                        selentry.put(CrawlProfile.CrawlAttribute.RECRAWL_IF_OLDER.key, Long.toString(CrawlProfile.getRecrawlDate(CrawlSwitchboard.CRAWL_PROFILE_SURROGATE_RECRAWL_CYCLE).getTime()));
                        insert = true;
                    }
                    if ( insert ) {
                        this.crawler.putActive(UTF8.getBytes(selentry.handle()), selentry);
                    }
                }
            } catch (final Exception e ) {
                ConcurrentLog.logException(e);
            }

            // close unused connections
            ConnectionInfo.cleanUp();

            // clean up delegated stack
            checkInterruption();
            if (this.crawlQueues.delegatedURL != null && (this.crawlQueues.delegatedURL.size() > 1000) ) {
                if ( this.log.isFine() ) {
                    this.log.fine("Cleaning Delegated-URLs report stack, "
                        + this.crawlQueues.delegatedURL.size()
                        + " entries on stack");
                }
                this.crawlQueues.delegatedURL.clear();
            }

            // clean up error stack
            checkInterruption();
            if ( (this.crawlQueues.errorURL.stackSize() > 1000) ) {
                if ( this.log.isFine() ) {
                    this.log.fine("Cleaning Error-URLs report stack, "
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
                        this.log.fine("Cleaning Loaded-URLs report stack, "
                            + ResultURLs.getStackSize(origin)
                            + " entries on stack "
                            + origin.getCode());
                    }
                    ResultURLs.clearStack(origin);
                }
            }
            
            // clean up news
            checkInterruption();
            try {
                if ( this.log.isFine() ) {
                    this.log.fine("Cleaning Incoming News, "
                        + this.peers.newsPool.size(NewsPool.INCOMING_DB)
                        + " entries on stack");
                }
                this.peers.newsPool.automaticProcess(this.peers);
            } catch (final Exception e ) {
                ConcurrentLog.logException(e);
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
                this.log.info("AUTO-UPDATE: downloading more recent release " + updateVersion.getUrl());
                final File downloaded = updateVersion.downloadRelease();
                final boolean devenvironment = new File(this.getAppPath(), ".git").exists();
                if ( devenvironment ) {
                    this.log
                        .info("AUTO-UPDATE: omitting update because this is a development environment");
                } else if ( (downloaded == null) || (!downloaded.exists()) || (downloaded.length() == 0) ) {
                    this.log
                        .info("AUTO-UPDATE: omitting update because download failed (file cannot be found, is too small or signature is bad)");
                } else {
                    yacyRelease.deployRelease(downloaded);
                    terminate(10, "auto-update to install " + downloaded.getName());
                    this.log.info("AUTO-UPDATE: deploy and restart initiated");
                }
            }

            // initiate broadcast about peer startup to spread supporter url
            if ( !isRobinsonMode() && this.peers.newsPool.size(NewsPool.OUTGOING_DB) == 0 ) {
                // read profile
                final Properties profile = new Properties();
                FileInputStream fileIn = null;
                try {
                    fileIn = new FileInputStream(new File(this.dataPath, "DATA/SETTINGS/profile.txt"));
                    profile.load(fileIn);
                } catch (final IOException e ) {
                } finally {
                    if ( fileIn != null ) {
                        try {
                            fileIn.close();
                        } catch (final Exception e ) {
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
                UPnP.addPortMappings();
            }

            // after all clean up is done, check the resource usage
            this.observer.resourceObserverJob();

            // clean up profiles
            checkInterruption();

            // execute the (post-) processing steps for all entries that have a process tag assigned
            Fulltext fulltext = index.fulltext();
            CollectionConfiguration collection1Configuration = fulltext.getDefaultConfiguration();
            boolean allCrawlsFinished = this.crawler.allCrawlsFinished(this.crawlQueues);
            int proccount = 0;
    
            if (!this.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL)) {

                boolean process_key_exist = collection1Configuration.contains(CollectionSchema.process_sxt);
                if (!process_key_exist) log.info("postprocessing deactivated: field process_sxt is not enabled");
                boolean reference_index_exist = (index.connectedCitation() || fulltext.useWebgraph());
                if (!reference_index_exist) log.info("postprocessing deactivated: no reference index avilable; activate citation index or webgraph");
                boolean minimum_ram_fullfilled = MemoryControl.available() > getConfigLong("postprocessing.minimum_ram", 0);
                if (!minimum_ram_fullfilled) log.info("postprocessing deactivated: no enough ram (" + MemoryControl.available() + "), needed " + getConfigLong("postprocessing.minimum_ram", 0) + ", to force change field postprocessing.minimum_ram");
                boolean minimum_load_fullfilled = Memory.load() < getConfigFloat("postprocessing.maximum_load", 0);
                if (!minimum_load_fullfilled) log.info("postprocessing deactivated: too high load (" + Memory.load() + ") > " + getConfigFloat("postprocessing.maximum_load", 0) + ", to force change field postprocessing.maximum_load");
                boolean postprocessing = process_key_exist && reference_index_exist && minimum_ram_fullfilled && minimum_load_fullfilled;
                if (!postprocessing) log.info("postprocessing deactivated: constraints violated");

                if (allCrawlsFinished) {
                    // refresh the search cache
                    SearchEventCache.cleanupEvents(true);
                    sb.index.clearCaches(); // every time the ranking is changed we need to remove old orderings
                    
                    if (postprocessing) {
                        // run postprocessing on all profiles
                        ReferenceReportCache rrCache = index.getReferenceReportCache();
                        proccount += collection1Configuration.postprocessing(index, rrCache, null, getConfigBool("postprocessing.partialUpdate", true));
                        this.index.fulltext().commit(true); // without a commit the success is not visible in the monitoring
                    }
                    this.crawler.cleanProfiles(this.crawler.getActiveProfiles());
                    log.info("cleanup post-processed " + proccount + " documents");
                } else {
                    Set<String> deletionCandidates = collection1Configuration.contains(CollectionSchema.harvestkey_s.getSolrFieldName()) ?
                            this.crawler.getFinishedProfiles(this.crawlQueues) : new HashSet<String>();
                    int cleanupByHarvestkey = deletionCandidates.size();
                    if (cleanupByHarvestkey > 0) {
                        if (postprocessing) {
                            // run postprocessing on these profiles
                            ReferenceReportCache rrCache = index.getReferenceReportCache();
                            for (String profileHash: deletionCandidates) proccount += collection1Configuration.postprocessing(index, rrCache, profileHash, getConfigBool("postprocessing.partialUpdate", true));
                            this.index.fulltext().commit(true); // without a commit the success is not visible in the monitoring
                        }
                        this.crawler.cleanProfiles(deletionCandidates);
                        log.info("cleanup removed " + cleanupByHarvestkey + " crawl profiles, post-processed " + proccount + " documents");
                    } 
                }
            }

            if (allCrawlsFinished) {
                // flush caches
                Domains.clear();
                this.crawlQueues.noticeURL.clear();
                
                // do solr optimization
                /*
                long idleSearch = System.currentTimeMillis() - this.localSearchLastAccess;
                long idleAdmin  = System.currentTimeMillis() - this.adminAuthenticationLastAccess;
                long deltaOptimize = System.currentTimeMillis() - this.optimizeLastRun;
                boolean optimizeRequired = deltaOptimize > 60000 * 60 * 2 && idleAdmin > 600000; // optimize if user is idle for 10 minutes and at most every 2 hours
                int opts = Math.min(10, Math.max(1, (int) (fulltext.collectionSize() / 1000000)));
                if (proccount > 0) {
                    opts++; // have postprocessings will force optimazion with one more Segment which is small an quick
                    optimizeRequired = true;
                }
                log.info("Solr auto-optimization: idleSearch=" + idleSearch + ", idleAdmin=" + idleAdmin + ", deltaOptimize=" + deltaOptimize + ", proccount=" + proccount);
                if (optimizeRequired) {
                    if (idleSearch < 600000) opts++; // < 10 minutes idle time will cause a optimization with one more Segment which is small an quick
                    log.info("Solr auto-optimization: running solr.optimize(" + opts + ")");
                    fulltext.optimize(opts);
                    this.optimizeLastRun = System.currentTimeMillis();
                }
                */
            }
            
            // write statistics
            if (System.currentTimeMillis() - this.lastStats > 1500000 /*25min, should cause 2 entries every hour at least*/) try {
                BEncodedHeap statTable = this.tables.getHeap("stats");
                Map<String, byte[]> entry = new LinkedHashMap<>();
                if (this.isP2PMode()) {
                    entry.put("aM", ASCII.getBytes(Integer.toString(this.peers.sizeActiveSince(30 * 1440)))); // activeLastMonth
                    entry.put("aW", ASCII.getBytes(Integer.toString(this.peers.sizeActiveSince(7 * 1440)))); // activeLastWeek
                    entry.put("aD", ASCII.getBytes(Integer.toString(this.peers.sizeActiveSince(1440)))); // activeLastDay
                    entry.put("aH", ASCII.getBytes(Integer.toString(this.peers.sizeActiveSince(60)))); // activeLastHour
                    entry.put("cC", ASCII.getBytes(Integer.toString(this.peers.sizeConnected()))); // countConnected (Active Senior)
                    entry.put("cD", ASCII.getBytes(Integer.toString(this.peers.sizeDisconnected()))); // countDisconnected (Passive Senior)
                    entry.put("cP", ASCII.getBytes(Integer.toString(this.peers.sizePotential()))); // countPotential (Junior)
                    entry.put("cR", ASCII.getBytes(Long.toString(this.index.RWICount()))); // count of the RWI entries
                }
                entry.put("cI", ASCII.getBytes(Long.toString(this.index.fulltext().collectionSize()))); // size of the index (number of documents)
                byte[] pk = ASCII.getBytes(GenericFormatter.SHORT_MINUTE_FORMATTER.format()); // the primary key is the date, the maximum length is 12 characters which is sufficient for up-to-minute accuracy
                statTable.put(pk, entry);
                this.lastStats = System.currentTimeMillis();
            } catch (IOException e) {}
            
            // show deadlocks if there are any in the log
            if (Memory.deadlocks() > 0) Memory.logDeadlocks();
            
            return true;
        } catch (final InterruptedException e ) {
            this.log.info("cleanupJob: Shutdown detected");
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
        setConfig(jobType + "_isPaused_cause", cause);
        log.warn("Crawl job '" + jobType + "' is paused: " + cause);
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
        } catch (final InterruptedException e ) {
            documents = null;
        } catch (final Exception e ) {
            documents = null;
        }
        if ( documents == null ) {
            return null;
        }        
        return new IndexingQueueEntry(in.queueEntry, documents, null);
    }

    private Document[] parseDocument(final Response response) throws InterruptedException {
        Document[] documents = null;
        //final Pattern rewritePattern = Pattern.compile(";jsessionid.*");
        final EventOrigin processCase = response.processCase(this.peers.mySeed().hash);

        if ( this.log.isFine() ) {
            this.log.fine(
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
                this.log.warn("the resource '" + response.url() + "' is missing in the cache.");
                // create a new errorURL DB entry
                this.crawlQueues.errorURL.push(response.url(), response.depth(), response.profile(), FailCategory.FINAL_LOAD_CONTEXT, "missing in cache", -1);
                return null;
            }
        }
        assert response.getContent() != null;
        try {
            // parse the document
            documents =
                TextParser.parseSource(
                    new AnchorURL(response.url()),
                    response.getMimeType(),
                    response.getCharacterEncoding(),
                    response.profile().scraper(),
                    response.profile().timezoneOffset(),
                    response.depth(),
                    response.getContent());
            if ( documents == null ) {
                throw new Parser.Failure("Parser returned null.", response.url());
            }
        } catch (final Parser.Failure e ) {
            this.log.warn("Unable to parse the resource '" + response.url().toNormalform(true) + "'. " + e.getMessage());
            // create a new errorURL DB entry
            this.crawlQueues.errorURL.push(response.url(), response.depth(), response.profile(), FailCategory.FINAL_PROCESS_CONTEXT, e.getMessage(), -1);
            return null;
        }
        final long parsingEndTime = System.currentTimeMillis();
        
        
        // put anchors on crawl stack
        final long stackStartTime = System.currentTimeMillis();
        // check if the documents have valid urls; this is not a bug patch; it is possible that
        // i.e. the result of a feed parsing results in documents from domains which shall be filtered by the crawl profile
        if (response.profile() != null) {
            ArrayList<Document> newDocs = new ArrayList<Document>();
            for (Document doc: documents) {
                //doc.rewrite_dc_source(rewritePattern, "");
                String rejectReason = this.crawlStacker.checkAcceptanceChangeable(doc.dc_source(), response.profile(), 1 /*depth is irrelevant here, we just make clear its not the start url*/);
                if (rejectReason == null) {
                    newDocs.add(doc);
                } else {
                    // we consider this as fail urls to have a tracking of the problem
                    if (rejectReason != null && !rejectReason.startsWith("double in")) {
                        this.crawlStacker.nextQueue.errorURL.push(response.url(), response.depth(), response.profile(), FailCategory.FINAL_LOAD_CONTEXT, rejectReason, -1);
                    }
                }
            }
            if (newDocs.size() != documents.length) {
                documents = (Document[]) newDocs.toArray();
            }
        }
        
        // collect anchors within remaining documents
        if ((processCase == EventOrigin.PROXY_LOAD || processCase == EventOrigin.LOCAL_CRAWLING) &&
            (
                response.profile() == null ||
                response.depth() < response.profile().depth() ||
                response.profile().crawlerNoDepthLimitMatchPattern().matcher(response.url().toNormalform(true)).matches()
            )
           ) {
            
            for (Document d: documents) d.setDepth(response.depth());
            
            // get the hyperlinks
            final Map<AnchorURL, String> hl = Document.getHyperlinks(documents, !response.profile().obeyHtmlRobotsNofollow());
            
            if (response.profile().indexMedia()) {
                for (Map.Entry<DigestURL, String> entry: Document.getImagelinks(documents).entrySet()) {
                    if (TextParser.supportsExtension(entry.getKey()) == null) hl.put(new AnchorURL(entry.getKey()), entry.getValue());
                }
            }
            
            // add all media links also to the crawl stack. They will be re-sorted to the NOLOAD queue and indexed afterwards as pure links
            if (response.profile().directDocByURL()) {
                for (Map.Entry<DigestURL, String> entry: Document.getImagelinks(documents).entrySet()) {
                    if (TextParser.supportsExtension(entry.getKey()) != null) hl.put(new AnchorURL(entry.getKey()), entry.getValue());
                }
                for (Map.Entry<DigestURL, String> d: Document.getApplinks(documents).entrySet()) hl.put(new AnchorURL(d.getKey()), d.getValue());
                for (Map.Entry<DigestURL, String> d: Document.getVideolinks(documents).entrySet()) hl.put(new AnchorURL(d.getKey()), d.getValue());
                for (Map.Entry<DigestURL, String> d: Document.getAudiolinks(documents).entrySet()) hl.put(new AnchorURL(d.getKey()), d.getValue());
            }

            // insert those hyperlinks to the crawler
            MultiProtocolURL nextUrl;
            for ( final Map.Entry<AnchorURL, String> nextEntry : hl.entrySet() ) {
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
                    log.info("REWRITE of url = \"" + u + "\" to \"" + u0 + "\"");
                    u = u0;
                }
                //Matcher m = rewritePattern.matcher(u);
                //if (m.matches()) u = m.replaceAll("");
                
                // enqueue the hyperlink into the pre-notice-url db
                int nextdepth = nextEntry.getValue() != null && nextEntry.getValue().equals(Document.CANONICAL_MARKER) ? response.depth() : response.depth() + 1; // canonical documents are on the same depth
                try {
                    this.crawlStacker.enqueueEntry(new Request(
                        response.initiator(),
                        new DigestURL(u),
                        response.url().hash(),
                        nextEntry.getValue(),
                        new Date(),
                        response.profile().handle(),
                        nextdepth,
                        response.profile().timezoneOffset()));
                } catch (final MalformedURLException e ) {
                    ConcurrentLog.logException(e);
                }
            }
            final long stackEndTime = System.currentTimeMillis();
            if ( this.log.isInfo() ) {
                this.log.info("CRAWL: ADDED "
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
            if (this.log.isInfo()) this.log.info("Not Condensed Resource '" + urls + "': indexing of this media type not wanted by crawl profile");
            return new IndexingQueueEntry(in.queueEntry, in.documents, null);
        } else if (!profile.indexMedia()) { // check for media excluded for indexing
            // check media by file extension
            if ( Classification.isMediaExtension(MultiProtocolURL.getFileExtension(in.queueEntry.url().getFileName()))) {
                this.log.info("Not Condensed Resource '" + urls + "': indexing of media files not wanted by crawl profile");
                return new IndexingQueueEntry(in.queueEntry, in.documents, null);
            }
            // double check media by mime in case of no file extension
            Classification.ContentDomain cd = Classification.getContentDomainFromMime(in.queueEntry.getMimeType());
            // don't exclude contentdomain.app (from mime) to keep pdf word etc.
            if (cd == Classification.ContentDomain.IMAGE || cd == Classification.ContentDomain.VIDEO || cd == Classification.ContentDomain.AUDIO ) {
                this.log.info("Not Condensed Resource '" + urls + "': indexing of media not wanted by crawl profile");
                return new IndexingQueueEntry(in.queueEntry, in.documents, null);
            }
        }
        if (!(profile.indexUrlMustMatchPattern() == CrawlProfile.MATCH_ALL_PATTERN || profile.indexUrlMustMatchPattern().matcher(urls).matches()) ||
             (profile.indexUrlMustNotMatchPattern() != CrawlProfile.MATCH_NEVER_PATTERN && profile.indexUrlMustNotMatchPattern().matcher(urls).matches())) {
            if (this.log.isInfo()) this.log.info("Not Condensed Resource '" + urls + "': indexing prevented by regular expression on url; indexUrlMustMatchPattern = " + profile.indexUrlMustMatchPattern().pattern() + ", indexUrlMustNotMatchPattern = " + profile.indexUrlMustNotMatchPattern().pattern());
            // create a new errorURL DB entry
            this.crawlQueues.errorURL.push(in.queueEntry.url(), in.queueEntry.depth(), profile, FailCategory.FINAL_PROCESS_CONTEXT, "indexing prevented by regular expression on url; indexUrlMustMatchPattern = " + profile.indexUrlMustMatchPattern().pattern() + ", indexUrlMustNotMatchPattern = " + profile.indexUrlMustNotMatchPattern().pattern(), -1);
            return new IndexingQueueEntry(in.queueEntry, in.documents, null);
        }
        
        // check which files may take part in the indexing process
        final List<Document> doclist = new ArrayList<Document>();
        docloop: for (final Document document : in.documents) {
            if (document.indexingDenied() && profile.obeyHtmlRobotsNoindex() && !this.isIntranetMode()) {
                if (this.log.isInfo()) this.log.info("Not Condensed Resource '" + urls + "': denied by document-attached noindexing rule");
                // create a new errorURL DB entry
                this.crawlQueues.errorURL.push(in.queueEntry.url(), in.queueEntry.depth(), profile, FailCategory.FINAL_PROCESS_CONTEXT, "denied by document-attached noindexing rule", -1);
                continue docloop;
            }
            if (!(profile.indexContentMustMatchPattern() == CrawlProfile.MATCH_ALL_PATTERN || profile.indexContentMustMatchPattern().matcher(document.getTextString()).matches()) ||
                 (profile.indexContentMustNotMatchPattern() != CrawlProfile.MATCH_NEVER_PATTERN && profile.indexContentMustNotMatchPattern().matcher(document.getTextString()).matches())) {
                if (this.log.isInfo()) this.log.info("Not Condensed Resource '" + urls + "': indexing prevented by regular expression on content; indexContentMustMatchPattern = " + profile.indexContentMustMatchPattern().pattern() + ", indexContentMustNotMatchPattern = " + profile.indexContentMustNotMatchPattern().pattern());
                // create a new errorURL DB entry
                this.crawlQueues.errorURL.push(in.queueEntry.url(), in.queueEntry.depth(), profile, FailCategory.FINAL_PROCESS_CONTEXT, "indexing prevented by regular expression on content; indexContentMustMatchPattern = " + profile.indexContentMustMatchPattern().pattern() + ", indexContentMustNotMatchPattern = " + profile.indexContentMustNotMatchPattern().pattern(), -1);
                continue docloop;
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
                        in.documents[i], in.queueEntry.profile().scraper(), in.queueEntry.profile().indexText(),
                        in.queueEntry.profile().indexMedia(),
                        LibraryProvider.dymLib, true,
                        this.index.fulltext().getDefaultConfiguration().contains(CollectionSchema.dates_in_content_dts),
                        profile.timezoneOffset());

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
                CrawlProfile profile = in.queueEntry.profile();
                storeDocumentIndex(
                    in.queueEntry,
                    in.queueEntry.profile().collections(),
                    in.documents[i],
                    in.condenser[i],
                    null,
                    profile == null ? "crawler" : profile.handle());
            }
        }
        in.queueEntry.updateStatus(Response.QUEUE_STATE_FINISHED);
    }

    /**
     * 
     * @param queueEntry
     * @param collections
     * @param document
     * @param condenser
     * @param searchEvent
     * @param sourceName if this document was created by a crawl, then the sourceName contains the crawl hash
     */
    private void storeDocumentIndex(
        final Response queueEntry,
        final Map<String, Pattern> collections,
        final Document document,
        final Condenser condenser,
        final SearchEvent searchEvent,
        final String sourceName) {

        //TODO: document must carry referer, size and last modified

        // CREATE INDEX
        final String dc_title = document.dc_title();
        final DigestURL url = document.dc_source();
        final DigestURL referrerURL = queueEntry.referrerURL();
        EventOrigin processCase = queueEntry.processCase(this.peers.mySeed().hash);
        CrawlProfile profile = queueEntry.profile();

        if (condenser == null || (document.indexingDenied() && profile.obeyHtmlRobotsNoindex())) {
            //if (this.log.isInfo()) log.logInfo("Not Indexed Resource '" + queueEntry.url().toNormalform(false, true) + "': denied by rule in document, process case=" + processCase);
            // create a new errorURL DB entry
            this.crawlQueues.errorURL.push(url, queueEntry.depth(), profile, FailCategory.FINAL_PROCESS_CONTEXT, "denied by rule in document, process case=" + processCase, -1);
            return;
        }

        if ( profile != null && !profile.indexText() && !profile.indexMedia() ) {
            //if (this.log.isInfo()) log.logInfo("Not Indexed Resource '" + queueEntry.url().toNormalform(false, true) + "': denied by profile rule, process case=" + processCase + ", profile name = " + queueEntry.profile().name());
            // create a new errorURL DB entry
            this.crawlQueues.errorURL.push(url, queueEntry.depth(), profile, FailCategory.FINAL_LOAD_CONTEXT, "denied by profile rule, process case="
                                + processCase
                                + ", profile name = "
                                + profile.collectionName(), -1);
            return;
        }

        // remove stopwords
        this.log.info("Excluded " + condenser.excludeWords(stopwords) + " words in URL " + url.toNormalform(true));

        // STORE WORD INDEX
        SolrInputDocument newEntry =
            this.index.storeDocument(
                url,
                referrerURL,
                collections,
                profile,
                queueEntry.getResponseHeader(),
                document,
                condenser,
                searchEvent,
                sourceName,
                getConfigBool(SwitchboardConstants.DHT_ENABLED, false),
                this.getConfigBool(SwitchboardConstants.PROXY_TRANSPARENT_PROXY, false) ? "http://127.0.0.1:" + sb.getConfigInt("port", 8090) : null,
                this.getConfig("crawler.http.acceptLanguage", null));
        final RSSFeed feed =
            EventChannel.channels(queueEntry.initiator() == null
                ? EventChannel.PROXY
                : Base64Order.enhancedCoder.equal(
                    queueEntry.initiator(),
                    ASCII.getBytes(this.peers.mySeed().hash))
                    ? EventChannel.LOCALINDEXING
                    : EventChannel.REMOTEINDEXING);
        feed.addMessage(new RSSMessage("Indexed web page", dc_title, queueEntry.url(), ASCII.String(queueEntry.url().hash())));
        if (this.getConfigBool(SwitchboardConstants.DECORATION_AUDIO, false)) Audio.Soundclip.newdoc.play(-20.0f);
        
        // store rss feeds in document into rss table
        for ( final Map.Entry<DigestURL, String> rssEntry : document.getRSS().entrySet() ) {
            final Tables.Data rssRow = new Tables.Data();
            rssRow.put("referrer", url.hash());
            rssRow.put("url", UTF8.getBytes(rssEntry.getKey().toNormalform(true)));
            rssRow.put("title", UTF8.getBytes(rssEntry.getValue()));
            rssRow.put("recording_date", new Date());
            try {
                this.tables.update("rss", rssEntry.getKey().hash(), rssRow);
            } catch (final IOException e ) {
                ConcurrentLog.logException(e);
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
            final Seed initiatorPeer = this.peers.getConnected(queueEntry.initiator());
            if ( initiatorPeer != null ) {
                // start a thread for receipt sending to avoid a blocking here
                try {
                    SolrDocument sd = this.index.fulltext().getDefaultConfiguration().toSolrDocument(newEntry);
                    new Thread(new receiptSending(initiatorPeer, new URIMetadataNode(sd)), "sending receipt to " + ASCII.String(queueEntry.initiator())).start();
                } catch (MalformedURLException ex) {
                    this.log.info("malformed url: "+ex.getMessage());
                }
            }
        }
    }

    public final void addAllToIndex(
        final DigestURL url,
        final Map<AnchorURL, String> links,
        final SearchEvent searchEvent,
        final String heuristicName,
        final Map<String, Pattern> collections,
        final boolean doublecheck) {

        List<DigestURL> urls = new ArrayList<DigestURL>();
        // add the landing page to the index. should not load that again since it should be in the cache
        if (url != null) {
            urls.add(url);
        }

        // check if some of the links match with the query
        final Map<AnchorURL, String> matcher = searchEvent.query.separateMatches(links);

        // take the matcher and load them all
        for (final Map.Entry<AnchorURL, String> entry : matcher.entrySet()) {
            urls.add(new DigestURL(entry.getKey(), (byte[]) null));
        }

        // take then the no-matcher and load them also
        for (final Map.Entry<AnchorURL, String> entry : links.entrySet()) {
            urls.add(new DigestURL(entry.getKey(), (byte[]) null));
        }
        addToIndex(urls, searchEvent, heuristicName, collections, doublecheck);
    }
    
    public void reload(final Collection<String> reloadURLStrings, final Map<String, Pattern> collections, final boolean doublecheck) {
        final Collection<DigestURL> reloadURLs = new ArrayList<DigestURL>(reloadURLStrings.size());
        Collection<String> deleteIDs = new ArrayList<String>(reloadURLStrings.size());
        for (String u: reloadURLStrings) {
            DigestURL url;
            try {
                url = new DigestURL(u);
                reloadURLs.add(url);
                deleteIDs.add(ASCII.String(url.hash()));
            } catch (MalformedURLException e) {
            }
        }
        remove(deleteIDs);
        if (doublecheck) this.index.fulltext().commit(false); // if not called here the double-cgeck in addToIndex will reject the indexing
        addToIndex(reloadURLs, null, null, collections, doublecheck);
    }

    public void remove(final Collection<String> deleteIDs) {
        this.index.fulltext().remove(deleteIDs);
        for (String id: deleteIDs) {
            byte[] idh = ASCII.getBytes(id);
            this.crawlQueues.removeURL(idh);
            try {Cache.delete(idh);} catch (IOException e) {}
        }
    }
    
    public void remove(final byte[] urlhash) {
        this.index.fulltext().remove(urlhash);
        this.crawlQueues.removeURL(urlhash);
        try {Cache.delete(urlhash);} catch (IOException e) {}
    }

    public void stackURLs(Set<DigestURL> rootURLs, final CrawlProfile profile, final Set<DigestURL> successurls, final Map<DigestURL,String> failurls) {
        if (rootURLs == null || rootURLs.size() == 0) return;
        if (rootURLs.size() == 1) {
            // for single stack requests, do not use the multithreading overhead;
            final DigestURL turl = rootURLs.iterator().next();
            String failreason;
            if ((failreason = Switchboard.this.stackUrl(profile, turl)) == null) successurls.add(turl); else failurls.put(turl, failreason);
            return;
        }
        final List<Thread> stackthreads = new ArrayList<Thread>(); // do this concurrently
        for (DigestURL url: rootURLs) {
            final DigestURL turl = url;
            Thread t = new Thread() {
                @Override
                public void run() {
                	this.setName("Switchboard.stackURLs");
                    String failreason;
                    if ((failreason = Switchboard.this.stackUrl(profile, turl)) == null) successurls.add(turl); else failurls.put(turl, failreason);
                }
            };
            t.start();
            stackthreads.add(t);
            try {Thread.sleep(100);} catch (final InterruptedException e) {} // to prevent that this fires more than 10 connections pre second!
        }
        final long waitingtime = 10 + (30000 / rootURLs.size()); // at most wait only halve an minute to prevent that the crawl start runs into a time-out
        for (Thread t: stackthreads) try {t.join(waitingtime);} catch (final InterruptedException e) {}
    }
    
    /**
     * stack the url to the crawler
     * @param profile
     * @param url
     * @return null if this was ok. If this failed, return a string with a fail reason
     */
    public String stackUrl(CrawlProfile profile, DigestURL url) {
        
        byte[] handle = ASCII.getBytes(profile.handle());

        // remove url from the index to be prepared for a re-crawl
        final byte[] urlhash = url.hash();
        remove(urlhash);
        // because the removal is done concurrenlty, it is possible that the crawl
        // stacking may fail because of double occurrences of that url. Therefore
        // we must wait here until the url has actually disappeared
        int t = 100;
        while (t-- > 0) {
            try {
                long lt = this.index.getLoadTime(ASCII.String(urlhash));
                if (lt < 0) break;
            } catch (IOException e) {
                // if this fails, the url may still exist
                // we should abandon the whole process
                return "exist-test failed: " + e.getMessage();
            }
            try {Thread.sleep(100);} catch (final InterruptedException e) {}
            ConcurrentLog.fine("Switchboard", "STACKURL: waiting for deletion, t=" + t);
            //if (t == 20) this.index.fulltext().commit(true);
            if (t == 1) this.index.fulltext().commit(false);
        }
        
        // special handling of ftp protocol
        if (url.isFTP()) {
            try {
                this.crawler.putActive(handle, profile);
                String userInfo = url.getUserInfo();
                int p = userInfo == null ? -1 : userInfo.indexOf(':');
                String user = userInfo == null ? FTPClient.ANONYMOUS : userInfo.substring(0, p);
                String pw = userInfo == null || p == -1 ? "anomic" : userInfo.substring(p + 1);
                this.crawlStacker.enqueueEntriesFTP(
                        this.peers.mySeed().hash.getBytes(),
                        profile.handle(),
                        url.getHost(),
                        url.getPort(),
                        user,
                        pw,
                        false,
                        profile.timezoneOffset());
                return null;
            } catch (final Exception e) {
                // mist
                ConcurrentLog.logException(e);
                return "problem crawling an ftp site: " + e.getMessage();
            }
        }
        
        // remove the document from the error-db
        byte[] hosthash = new byte[6]; System.arraycopy(urlhash, 6, hosthash, 0, 6);
        Set<String> hosthashes = new HashSet<String>();
        hosthashes.add(ASCII.String(hosthash));
        this.crawlQueues.errorURL.removeHosts(hosthashes);
        this.index.fulltext().remove(urlhash);

        // get a scraper to get the title
        Document scraper;
        try {
            scraper = this.loader.loadDocument(url, CacheStrategy.IFFRESH, BlacklistType.CRAWLER, profile.getAgent());
        } catch (final IOException e) {
            return "scraper cannot load URL: " + e.getMessage();
        }
        
        final String title = scraper == null ? url.toNormalform(true) : scraper.dc_title();
        final String description = scraper.dc_description().length > 0 ? scraper.dc_description()[0] : "";

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
                profile.timezoneOffset()
                ));
        
        if (reasonString != null) return reasonString;
        
        // create a bookmark from crawl start url
        final Set<String> tags=ListManager.string2set(BookmarkHelper.cleanTagsString("/crawlStart"));
        tags.add("crawlStart");
        final Set<String> keywords = scraper.dc_subject();
        if (keywords != null) {
            for (final String k: keywords) {
                final String kk = BookmarkHelper.cleanTagsString(k);
                if (kk.length() > 0) tags.add(kk);
            }
        }

        // TODO: what to do with the result ?
        //String tagStr = tags.toString();
        //if (tagStr.length() > 2 && tagStr.startsWith("[") && tagStr.endsWith("]")) tagStr = tagStr.substring(1, tagStr.length() - 2);

        // we will create always a bookmark to use this to track crawled hosts
        final BookmarksDB.Bookmark bookmark = this.bookmarksDB.createorgetBookmark(url.toNormalform(true), "admin");
        if (bookmark != null) {
            bookmark.setProperty(BookmarksDB.Bookmark.BOOKMARK_TITLE, title);
            bookmark.setProperty(BookmarksDB.Bookmark.BOOKMARK_DESCRIPTION, description);
            bookmark.setPublic(false);
            bookmark.setTags(tags, true);
            this.bookmarksDB.saveBookmark(bookmark);
        }

        // do the same for ymarks
        // TODO: could a non admin user add crawls?
        try {
            this.tables.bookmarks.createBookmark(scraper, YMarkTables.USER_ADMIN, true, "crawlStart", "/Crawl Start");
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } catch (final Failure e) {
            ConcurrentLog.logException(e);
        }

        // that was ok
        return null;
    }
    
    /**
     * load the content of a URL, parse the content and add the content to the index This process is started
     * concurrently. The method returns immediately after the call.
     * Loaded/indexed pages are added to the given SearchEvent. If this is not required prefer addToCrawler
     * to spare concurrent processes, bandwidth and intransparent crawl/load activity
     *
     * @param url the url that shall be indexed
     * @param searchEvent (optional) a search event that shall get results from the indexed pages directly
     *        feeded. If object is null then it is ignored
     * @throws IOException
     * @throws Parser.Failure
     */
    public void addToIndex(final Collection<DigestURL> urls, final SearchEvent searchEvent, final String heuristicName, final Map<String, Pattern> collections, final boolean doublecheck) {
        Map<String, DigestURL> urlmap = new HashMap<String, DigestURL>();
        for (DigestURL url: urls) urlmap.put(ASCII.String(url.hash()), url);
        if (searchEvent != null) {
            for (String id: urlmap.keySet()) searchEvent.addHeuristic(ASCII.getBytes(id), heuristicName, true);
        }
        final List<Request> requests = new ArrayList<Request>();
        for (Map.Entry<String, DigestURL> e: urlmap.entrySet()) {
            final String urlName = e.getValue().toNormalform(true);
            if (doublecheck) {
                try {
                    if (this.index.getLoadTime(e.getKey()) >= 0) {
                        this.log.info("addToIndex: double " + urlName);
                        continue;
                    }
                } catch (IOException ee) {
                    // double check fail may mean that the url exist
                    this.log.info("addToIndex: doublecheck failed for " + urlName + ": " + ee.getMessage());
                    continue;
                }
            }
            final Request request = this.loader.request(e.getValue(), true, true);
            final CrawlProfile profile = this.crawler.get(ASCII.getBytes(request.profileHandle()));
            final String acceptedError = this.crawlStacker.checkAcceptanceChangeable(e.getValue(), profile, 0);
            if (acceptedError != null) {
                this.log.warn("addToIndex: cannot load " + urlName + ": " + acceptedError);
                continue;
            }
            requests.add(request);
            }
        
        new Thread() {
            @Override
            public void run() {
                for (Request request: requests) {
                    DigestURL url = request.url();
                    String urlName = url.toNormalform(true);
                    Thread.currentThread().setName("Switchboard.addToIndex:" + urlName);
                    try {
                        final Response response = Switchboard.this.loader.load(request, CacheStrategy.IFFRESH, BlacklistType.CRAWLER, ClientIdentification.yacyIntranetCrawlerAgent);
                        if (response == null) {
                            throw new IOException("response == null");
                        }
                        if (response.getContent() == null) {
                            throw new IOException("content == null");
                        }
                        if (response.getResponseHeader() == null) {
                            throw new IOException("header == null");
                        }
                        final Document[] documents = response.parse();
                        if (documents != null) {
                            for (final Document document: documents) {
                                final CrawlProfile profile = crawler.get(ASCII.getBytes(request.profileHandle()));
                                if (document.indexingDenied() && (profile == null || profile.obeyHtmlRobotsNoindex())) {
                                    throw new Parser.Failure("indexing is denied", url);
                                }
                                final Condenser condenser = new Condenser(
                                        document, null, true, true, LibraryProvider.dymLib, true,
                                        Switchboard.this.index.fulltext().getDefaultConfiguration().contains(CollectionSchema.dates_in_content_dts), 
                                        searchEvent == null ? 0 : searchEvent.query.timezoneOffset);
                                ResultImages.registerImages(url, document, true);
                                Switchboard.this.webStructure.generateCitationReference(url, document);
                                storeDocumentIndex(
                                    response,
                                    collections,
                                    document,
                                    condenser,
                                    searchEvent,
                                    "heuristic:" + heuristicName);
                                Switchboard.this.log.info("addToIndex fill of url " + urlName + " finished");
                            }
                        }
                    } catch (final IOException e ) {
                        Switchboard.this.log.warn("addToIndex: failed loading " + urlName + ": " + e.getMessage());
                    } catch (final Parser.Failure e ) {
                        Switchboard.this.log.warn("addToIndex: failed parsing " + urlName + ": " + e.getMessage());
                    }
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
    public void addToCrawler(final Collection<DigestURL> urls, final boolean asglobal) {
        Map<String, DigestURL> urlmap = new HashMap<String, DigestURL>();
        for (DigestURL url: urls) urlmap.put(ASCII.String(url.hash()), url);
        for (Map.Entry<String, DigestURL> e: urlmap.entrySet()) {
            try {
                if (this.index.getLoadTime(e.getKey()) >= 0) continue; // double
            } catch (IOException ee) {
                continue; // if the check fails, consider the url as double
            }
            DigestURL url = e.getValue();
            final Request request = this.loader.request(url, true, true);
            final CrawlProfile profile = this.crawler.get(ASCII.getBytes(request.profileHandle()));
            String acceptedError = this.crawlStacker.checkAcceptanceChangeable(url, profile, 0);
            if (acceptedError == null) acceptedError = this.crawlStacker.checkAcceptanceInitially(url, profile);
            if (acceptedError != null) {
                this.log.info("addToCrawler: cannot load " + url.toNormalform(true) + ": " + acceptedError);
                continue;
            }
            final String s;
            if (asglobal) {
                s = this.crawlQueues.noticeURL.push(StackType.GLOBAL, request, profile, this.robots);
            } else {
                s = this.crawlQueues.noticeURL.push(StackType.LOCAL, request, profile, this.robots);
            }
    
            if (s != null) {
                this.log.info("addToCrawler: failed to add " + url.toNormalform(true) + ": " + s);
            }
        }
    }

    public void initBookmarks(boolean b) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
                Switchboard.this.log.info("Sending crawl receipt for '"
                    + this.reference.url().toNormalform(true)
                    + "' to "
                    + this.initiatorPeer.getName()
                    + " FAILED, send time = "
                    + (System.currentTimeMillis() - t));
                return;
            }
            final String delay = response.get("delay");
            Switchboard.this.log.info("Sending crawl receipt for '"
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
     *  - requestHeader.AUTHORIZATION = B64encode("adminname:password") or = B64encode("adminname:valueOf_Base64MD5cft")
     *  - adminAccountBase64MD5 = MD5(B64encode("adminname:password") or = "MD5:"+MD5("adminname:peername:password")
     * @return the auth-level as described above or 1 which means 'not authorized'. a 0 is returned in case of
     *         fraud attempts
     */
    public int adminAuthenticated(final RequestHeader requestHeader) {

        // authorization (earlier) by servlet container with username/password
        // as this stays true as long as authenticated browser is open (even after restart of YaCy) add a timeout check to look at credentials again
        // TODO: same is true for credential checks below (at least with BASIC auth -> login should expire at least on restart
        if (requestHeader.isUserInRole(UserDB.AccessRight.ADMIN_RIGHT.toString())) {
            if (adminAuthenticationLastAccess + 60000 > System.currentTimeMillis()) // 1 minute
            return 4; // hard-authenticated, quick return
        }

        // authorization in case that there is no account stored
        final String adminAccountUserName = getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin");
        final String adminAccountBase64MD5 = getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "");
        if ( adminAccountBase64MD5.isEmpty() ) {
            adminAuthenticationLastAccess = System.currentTimeMillis();
            return 2; // no password stored; this should not happen for older peers
        }

        // authorization for localhost, only if flag is set to grant localhost access as admin
        final boolean accessFromLocalhost = requestHeader.accessFromLocalhost();
        if (accessFromLocalhost && getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false)) {
            adminAuthenticationLastAccess = System.currentTimeMillis();
            return 3; // soft-authenticated for localhost
        }

        // get the authorization string from the header
        final String realmProp = (requestHeader.get(RequestHeader.AUTHORIZATION, "")).trim();
        String realmValue = realmProp.isEmpty() ? null : realmProp.substring(6); // take out "BASIC "

        // authorization with admin keyword in configuration
        if ( realmValue == null || realmValue.isEmpty() ) {
            return 1;
        }

        if (HttpServletRequest.BASIC_AUTH.equalsIgnoreCase(requestHeader.getAuthType())) {
            // security check against too long authorization strings (for BASIC auth)
            if (realmValue.length() > 256) {
                return 0;
            }
        } else {
            // handle DIGEST auth by servlet container
            if (requestHeader.getUserPrincipal() != null) { // user is authenticated (by Servlet container)
                if (requestHeader.isUserInRole(AccessRight.ADMIN_RIGHT.toString())) {
                    // we could double check admin right (but we trust embedded container)
                    // String username = requestHeader.getUserPrincipal().getName();
                    // if ((username.equalsIgnoreCase(sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin")))
                    //        || (sb.userDB.getEntry(username).hasRight(AccessRight.ADMIN_RIGHT)))
                    adminAuthenticationLastAccess = System.currentTimeMillis();
                    return 4; // has admin right
                }
            }
        }

        // authorization by encoded password, only for localhost access
        String pass = Base64Order.standardCoder.encodeString(adminAccountUserName + ":" + adminAccountBase64MD5);
        if ( accessFromLocalhost && (pass.equals(realmValue)) ) { // assume realmValue as is in cfg
            adminAuthenticationLastAccess = System.currentTimeMillis();
            return 3; // soft-authenticated for localhost
        }

        // authorization by hit in userDB (authtype username:encodedpassword - handed over by DefaultServlet)
        if ( this.userDB.hasAdminRight(realmProp, requestHeader.getHeaderCookies()) ) {
            adminAuthenticationLastAccess = System.currentTimeMillis();
            return 4; //return, because 4=max
        }

        // athorization by BASIC auth (realmValue = "adminname:password")
        if (adminAccountBase64MD5.startsWith("MD5:")) {
            // handle new option   adminAccountBase64MD5="MD5:xxxxxxx" = encodeMD5Hex ("adminname:peername:password")
            String realmtmp = Base64Order.standardCoder.decodeString(realmValue); //decode to clear text
            int i = realmtmp.indexOf(':');
            if (i >= 3) { // put peer name in realmValue (>3 is ok to skip "MD5:" and usernames are min 4 characters, in basic auth realm "user:pwd")
                realmtmp = realmtmp.substring(0, i + 1) + sb.getConfig(SwitchboardConstants.ADMIN_REALM,"YaCy") + ":" + realmtmp.substring(i + 1);

                if (adminAccountBase64MD5.substring(4).equals(Digest.encodeMD5Hex(realmtmp))) {
                    adminAuthenticationLastAccess = System.currentTimeMillis();
                    return 4; // hard-authenticated, all ok
                }
            } else {
                // handle DIGEST auth (realmValue = adminAccountBase (set for lecacyHeader in DefaultServlet for authenticated requests)
                if (adminAccountBase64MD5.equals(realmValue)) {
                    adminAuthenticationLastAccess = System.currentTimeMillis();
                    return 4; // hard-authenticated, all ok
                }
            }
        } else {
            // handle old option  adminAccountBase64MD5="xxxxxxx" = encodeMD55Hex(encodeB64("adminname:password")
            if (adminAccountBase64MD5.equals(Digest.encodeMD5Hex(realmValue))) {
                adminAuthenticationLastAccess = System.currentTimeMillis();
                return 4; // hard-authenticated, all ok
            }
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
        if ( indexSegment.RWICount() < 100 ) {
            return "no DHT distribution: not enough words - wordIndex.size() = "
                + indexSegment.RWICount();
        }
        if ( (getConfig(SwitchboardConstants.INDEX_DIST_ALLOW_WHILE_CRAWLING, "false").equalsIgnoreCase("false")) && (!this.crawlQueues.noticeURL.isEmptyLocal()) ) {
            return "no DHT distribution: crawl in progress: noticeURL.stackSize() = "
                + this.crawlQueues.noticeURL.size()
                + ", sbQueue.size() = "
                + getIndexingProcessorsQueueSize();
        }
        if ( (getConfig(SwitchboardConstants.INDEX_DIST_ALLOW_WHILE_INDEXING, "false").equalsIgnoreCase("false")) && (getIndexingProcessorsQueueSize() > 1) ) {
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
                this.log.fine(rejectReason);
            }
            return false;
        }
        boolean hasDoneSomething = false;
        final long kbytesUp = ConnectionInfo.getActiveUpbytes() / 1024;
        // accumulate RWIs to transmission buffer
        if ( this.dhtDispatcher.bufferSize() > this.peers.scheme.verticalPartitions() ) {
            this.log.info("dhtTransferJob: no selection, too many entries in transmission buffer: "
                + this.dhtDispatcher.bufferSize());
        } else if ( MemoryControl.available() < 1024 * 1024 * 25 ) {
            this.log.info("dhtTransferJob: no selection, too less memory available : "
                + (MemoryControl.available() / 1024 / 1024)
                + " MB");
        } else if ( ConnectionInfo.getLoadPercent() > 50 ) {
            this.log.info("dhtTransferJob: too many connections in httpc pool : "
                + ConnectionInfo.getCount());
            // close unused connections
//            Client.cleanup();
        } else if ( kbytesUp > 128 ) {
            this.log.info("dhtTransferJob: too much upload(1), currently uploading: " + kbytesUp + " Kb");
        } else {
            byte[] startHash = null, limitHash = null;
            int tries = 10;
            while ( tries-- > 0 ) {
                startHash = DHTSelection.selectRandomTransferStart();
                assert startHash != null;
                limitHash = DHTSelection.limitOver(this.peers, startHash);
                if ( limitHash != null ) {
                    break;
                }
            }
            if ( limitHash == null || startHash == null ) {
                this.log.info("dhtTransferJob: approaching full DHT dispersion.");
                return false;
            }
            this.log.info("dhtTransferJob: selected " + ASCII.String(startHash) + " as start hash");
            this.log.info("dhtTransferJob: selected " + ASCII.String(limitHash) + " as limit hash");
            final boolean enqueued =
                this.dhtDispatcher.selectContainersEnqueueToBuffer(
                    startHash,
                    limitHash,
                    dhtMaxContainerCount,
                    this.dhtMaxReferenceCount,
                    5000);
            hasDoneSomething = hasDoneSomething | enqueued;
            this.log.info("dhtTransferJob: result from enqueueing: " + ((enqueued) ? "true" : "false"));
        }

        // check if we can deliver entries to other peers
        if ( this.dhtDispatcher.transmissionSize() >= 10 ) {
            this.log
                .info("dhtTransferJob: no dequeueing from buffer to transmission: too many concurrent sessions: "
                    + this.dhtDispatcher.transmissionSize());
        } else if ( ConnectionInfo.getLoadPercent() > 75 ) {
            this.log.info("dhtTransferJob: too many connections in httpc pool : "
                + ConnectionInfo.getCount());
            // close unused connections
//            Client.cleanup();
        } else if ( kbytesUp > 256 ) {
            this.log.info("dhtTransferJob: too much upload(2), currently uploading: " + kbytesUp + " Kb");
        } else {
            final boolean dequeued = this.dhtDispatcher.dequeueContainer();
            hasDoneSomething = hasDoneSomething | dequeued;
            this.log.info("dhtTransferJob: result from dequeueing: " + ((dequeued) ? "true" : "false"));
        }
        return hasDoneSomething;
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
                AnchorURL url;
                try {
                    url = new AnchorURL(r);
                } catch (final MalformedURLException e ) {
                    ConcurrentLog.logException(e);
                    return;
                }

                final Map<AnchorURL, String> links;
                searchEvent.oneFeederStarted();
                try {
                    links = Switchboard.this.loader.loadLinks(url, CacheStrategy.NOCACHE, BlacklistType.SEARCH, ClientIdentification.yacyIntranetCrawlerAgent, searchEvent.query.timezoneOffset);
                    if ( links != null ) {
                        final Iterator<AnchorURL> i = links.keySet().iterator();
                        while ( i.hasNext() ) {
                            if ( !i.next().getHost().endsWith(host) ) {
                                i.remove();
                            }
                        }

                        // add all pages to the index
                        addAllToIndex(url, links, searchEvent, "site", CrawlProfile.collectionParser("site"), true);
                    }
                } catch (final Throwable e ) {
                    ConcurrentLog.logException(e);
                } finally {
                    searchEvent.oneFeederTerminated();
                }
            }
        }.start();
    }

    /**
     * Get the outbound links of the result and add each unique link to crawler queue
     * Is input resulturl a full index document with outboundlinks these will be used
     * otherwise url is loaded and links are extracted/parsed
     *
     * @param resulturl the result doc which outbound links to add to crawler
     */
    public final void heuristicSearchResults(final URIMetadataNode resulturl) {
        new Thread("Switchboard.heuristicSearchResults") {

            @Override
            public void run() {

                // get the links for a specific site
                final DigestURL startUrl = resulturl.url();

                // result might be rich metadata, try to get outbout links directly from result
                Set<DigestURL> urls;
                Iterator<String> outlinkit = URIMetadataNode.getLinks(resulturl, false);
                if (outlinkit.hasNext()) {
                    urls = new HashSet<DigestURL>();
                    while (outlinkit.hasNext()) {
                        try {
                            urls.add(new DigestURL(outlinkit.next()));
                        } catch (MalformedURLException ex) { }
                    }
                } else { // otherwise get links from loader
                    urls = null;

                    try {
                        final Map<AnchorURL, String> links;
                        links = Switchboard.this.loader.loadLinks(startUrl, CacheStrategy.IFFRESH, BlacklistType.SEARCH, ClientIdentification.yacyIntranetCrawlerAgent, 0);
                        if (links != null) {
                            if (links.size() < 1000) { // limit to 1000 to skip large index pages
                                final Iterator<AnchorURL> i = links.keySet().iterator();
                                if (urls == null) urls = new HashSet<DigestURL>();
                                while (i.hasNext()) {
                                    DigestURL url = i.next();
                                    boolean islocal = (url.getHost() == null && startUrl.getHost() == null) || (url.getHost() != null && startUrl.getHost() != null && url.getHost().contentEquals(startUrl.getHost()));
                                    // add all external links or links to different page to crawler
                                    if ( !islocal ) {// || (!startUrl.getPath().endsWith(url.getPath()))) {
                                        urls.add(url);
                                    }
                                }
                            }
                        }
                    } catch (final Throwable e) { }
                }
                if (urls != null && urls.size() > 0) {
                    final boolean globalcrawljob = Switchboard.this.getConfigBool(SwitchboardConstants.HEURISTIC_SEARCHRESULTS_CRAWLGLOBAL,false);
                    addToCrawler(urls, globalcrawljob);
                }
            }
        }.start();
    }

    /**
     * Queries a remote opensearch system, expects RSS feed as response, parses the RSS feed and
     * - adds the results to the results of the searchEvent
     * - adds the results to the local index
     *
     * @param urlpattern the search query url (e.g. http://search.org?query=searchword)
     * @param searchEvent
     * @param feedName short/internal name of the remote system
     * @deprecated use FederateSearchManager(SearchEvent) instead
     */
    @Deprecated // not used (since 2015-01-18, v1.81)
    public final void heuristicRSS(
        final String urlpattern,
        final SearchEvent searchEvent,
        final String feedName) {

        new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("heuristicRSS:" + feedName);
                final DigestURL url;
                try {
                    url = new DigestURL(MultiProtocolURL.unescape(urlpattern));
                } catch (final MalformedURLException e1 ) {
                    ConcurrentLog.warn("heuristicRSS", "url not well-formed: '" + urlpattern + "'");
                    return;
                }

                // if we have an url then try to load the rss
                RSSReader rss = null;
                searchEvent.oneFeederStarted();
                try {
                    final Response response =
                        Switchboard.this.loader.load(Switchboard.this.loader.request(url, true, false), CacheStrategy.NOCACHE, BlacklistType.SEARCH, ClientIdentification.yacyIntranetCrawlerAgent);
                    final byte[] resource = (response == null) ? null : response.getContent();
                    rss = resource == null ? null : RSSReader.parse(RSSFeed.DEFAULT_MAXSIZE, resource);
                    if ( rss != null ) {
                        final Map<AnchorURL, String> links = new TreeMap<>();
                        AnchorURL uri;
                        for ( final RSSMessage message : rss.getFeed() ) {
                            try {
                                uri = new AnchorURL(message.getLink());
                                links.put(uri, message.getTitle());
                            } catch (final MalformedURLException e ) {
                            }
                        }

                        ConcurrentLog.info("heuristicRSS", "Heuristic: adding "
                            + links.size()
                            + " links from '"
                            + feedName
                            + "' rss feed");
                        // add all pages to the index
                        addAllToIndex(null, links, searchEvent, feedName, CrawlProfile.collectionParser("rss"), true);
                    }
                } catch (final Throwable e ) {
                } finally {
                    searchEvent.oneFeederTerminated();
                }
            }
        }.start();
    }

    public static int currentPPM() {
        return EventTracker.countEvents(EventTracker.EClass.INDEX, 20000) * 3;
    }

    public float averageQPM() {
        final long uptime = (System.currentTimeMillis() - this.startupTime) / 1000;
        return (this.searchQueriesRobinsonFromRemote + this.searchQueriesGlobal) * 60f / Math.max(uptime, 1f);
    }

    public float averageQPMGlobal() {
        final long uptime = (System.currentTimeMillis() - this.startupTime) / 1000;
        return (this.searchQueriesGlobal) * 60f / Math.max(uptime, 1f);
    }

    public float averageQPMPrivateLocal() {
        final long uptime = (System.currentTimeMillis() - this.startupTime) / 1000;
        return (this.searchQueriesRobinsonFromLocal) * 60f / Math.max(uptime, 1f);
    }

    public float averageQPMPublicLocal() {
        final long uptime = (System.currentTimeMillis() - this.startupTime) / 1000;
        return (this.searchQueriesRobinsonFromRemote) * 60f / Math.max(uptime, 1f);
    }

    private static long indeSizeCache = 0;
    private static long indexSizeTime = 0;
    public void updateMySeed() {
        this.peers.mySeed().put(Seed.PORT, Integer.toString(getPublicPort("port", 8090)));

        //the speed of indexing (pages/minute) of the peer
        final long uptime = (System.currentTimeMillis() - this.startupTime) / 1000;
        Seed mySeed = this.peers.mySeed();
        
        mySeed.put(Seed.ISPEED, Integer.toString(currentPPM()));
        mySeed.put(Seed.RSPEED, Float.toString(averageQPM()));
        mySeed.put(Seed.UPTIME, Long.toString(uptime / 60)); // the number of minutes that the peer is up in minutes/day (moving average MA30)

        long t = System.currentTimeMillis();
        if (t - indexSizeTime > 60000) {
            indeSizeCache = sb.index.fulltext().collectionSize();
            indexSizeTime = t;
        }
        mySeed.put(Seed.LCOUNT, Long.toString(indeSizeCache)); // the number of links that the peer has stored (LURL's)
        mySeed.put(Seed.NCOUNT, Integer.toString(this.crawlQueues.noticeURL.size())); // the number of links that the peer has noticed, but not loaded (NURL's)
        mySeed.put(Seed.RCOUNT, Integer.toString(this.crawlQueues.noticeURL.stackSize(NoticedURL.StackType.GLOBAL))); // the number of links that the peer provides for remote crawling (ZURL's)
        mySeed.put(Seed.ICOUNT, Long.toString(this.index.RWICount())); // the minimum number of words that the peer has indexed (as it says)
        mySeed.put(Seed.SCOUNT, Integer.toString(this.peers.sizeConnected())); // the number of seeds that the peer has stored
        mySeed.put(Seed.CCOUNT, Float.toString(((int) ((this.peers.sizeConnected() + this.peers.sizeDisconnected() + this.peers.sizePotential()) * 60.0f / (uptime + 1.01f)) * 100.0f) / 100.0f)); // the number of clients that the peer connects (as connects/hour)
        mySeed.put(Seed.VERSION, yacyBuildProperties.getLongVersion());
        mySeed.setFlagDirectConnect(true);
        mySeed.setLastSeenUTC();
        mySeed.put(Seed.UTC, GenericFormatter.UTCDiffString());
        mySeed.setFlagAcceptRemoteCrawl(getConfigBool(SwitchboardConstants.CRAWLJOB_REMOTE, false));
        mySeed.setFlagAcceptRemoteIndex(getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW, true));
        mySeed.setFlagSSLAvailable(this.getHttpServer() != null && this.getHttpServer().withSSL() && getConfigBool("server.https", false));
        if (mySeed.getFlagSSLAvailable()) mySeed.put(Seed.PORTSSL, Integer.toString(getPublicPort("port.ssl", 8443)));

        // set local ips
        String staticIP = this.getConfig("staticIP", "");
        if (staticIP.length() > 0) mySeed.setIP(staticIP);
        Set<String> publicips = myPublicIPs();
        if (!mySeed.clash(publicips)) mySeed.setIPs(publicips);
    }

    public void loadSeedLists() {
        // uses the superseed to initialize the database with known seeds

        String seedListFileURL;
        final int sc = this.peers.sizeConnected();
        Network.log.info("BOOTSTRAP: " + sc + " seeds known from previous run, concurrently starting seedlist loader");

        // - use the superseed to further fill up the seedDB
        AtomicInteger scc = new AtomicInteger(0);
        int c = 0;
        while ( true ) {
            if ( Thread.currentThread().isInterrupted() ) {
                break;
            }
            seedListFileURL = this.getConfig(SwitchboardConstants.NETWORK_BOOTSTRAP_SEEDLIST_STUB + c, "");
            if ( seedListFileURL.isEmpty() ) {
                break;
            }
            c++;
            if ( seedListFileURL.startsWith("http://") || seedListFileURL.startsWith("https://") ) {
                this.peers.loadSeedListConcurrently(seedListFileURL, scc, (int) getConfigLong("bootstrapLoadTimeout", 20000), c > 0);
            }
        }
    }

    public void initRemoteProxy() {
        // reading the proxy host name
        final String host = getConfig("remoteProxyHost", "").trim();
        // reading the proxy host port
        int port;
        try {
            port = Integer.parseInt(getConfig("remoteProxyPort", "3128"));
        } catch (final NumberFormatException e ) {
            port = 3128;
        }
        
        // create new config
        ProxySettings.port = port;
        ProxySettings.host = host;
        ProxySettings.setProxyUse4HTTP(ProxySettings.host != null && ProxySettings.host.length() > 0 && getConfigBool("remoteProxyUse", false));
        ProxySettings.setProxyUse4HTTPS(getConfig("remoteProxyUse4SSL", "true").equalsIgnoreCase("true"));
        ProxySettings.user = getConfig("remoteProxyUser", "").trim();
        ProxySettings.password = getConfig("remoteProxyPwd", "").trim();

        // determining addresses for which the remote proxy should not be used
        final String remoteProxyNoProxy = getConfig("remoteProxyNoProxy", "").trim();
        ProxySettings.noProxy = CommonPattern.COMMA.split(remoteProxyNoProxy);
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
        this.log.info("caught delayed terminate request: " + reason);
        (new Shutdown(this, delay, reason)).start();
    }

    public boolean shallTerminate() {
        return this.terminate;
    }

    public void terminate(final String reason) {
        this.terminate = true;
        this.log.info("caught terminate request: " + reason);
        this.tray.setShutdown();
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
