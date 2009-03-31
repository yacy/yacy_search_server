// plasmaWordIndex.java
// (C) 2005, 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2005 on http://www.anomic.de
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

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.IndexingStack;
import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.http.httpdProxyCacheEntry;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.text.BufferedIndex;
import de.anomic.kelondro.text.BufferedIndexCollection;
import de.anomic.kelondro.text.IndexCell;
import de.anomic.kelondro.text.IndexCollectionMigration;
import de.anomic.kelondro.text.MetadataRowContainer;
import de.anomic.kelondro.text.ReferenceContainer;
import de.anomic.kelondro.text.IODispatcher;
import de.anomic.kelondro.text.ReferenceRow;
import de.anomic.kelondro.text.MetadataRepository;
import de.anomic.kelondro.text.Word;
import de.anomic.kelondro.text.Blacklist;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.kelondro.util.kelondroException;
import de.anomic.kelondro.util.Log;
import de.anomic.tools.iso639;
import de.anomic.xml.RSSFeed;
import de.anomic.xml.RSSMessage;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyURL;

public final class plasmaWordIndex {

    // environment constants
    public static final long wCacheMaxAge    = 1000 * 60 * 30; // milliseconds; 30 minutes
    public static final int  wCacheMaxChunk  =  800;           // maximum number of references for each urlhash
    public static final int  lowcachedivisor =  900;
    public static final int  maxCollectionPartition = 7;       // should be 7
    public static final int  maxCellArrayFiles = 10;
    
    public static final String CRAWL_PROFILE_PROXY                 = "proxy";
    public static final String CRAWL_PROFILE_REMOTE                = "remote";
    public static final String CRAWL_PROFILE_SNIPPET_LOCAL_TEXT    = "snippetLocalText";
    public static final String CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT   = "snippetGlobalText";
    public static final String CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA   = "snippetLocalMedia";
    public static final String CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA  = "snippetGlobalMedia";
    public static final String DBFILE_ACTIVE_CRAWL_PROFILES        = "crawlProfilesActive.heap";
    public static final String DBFILE_PASSIVE_CRAWL_PROFILES       = "crawlProfilesPassive.heap";
    
    public static final long CRAWL_PROFILE_PROXY_RECRAWL_CYCLE = 60L * 24L;
    public static final long CRAWL_PROFILE_SNIPPET_LOCAL_TEXT_RECRAWL_CYCLE = 60L * 24L * 30L;
    public static final long CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT_RECRAWL_CYCLE = 60L * 24L * 30L;
    public static final long CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA_RECRAWL_CYCLE = 60L * 24L * 30L;
    public static final long CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA_RECRAWL_CYCLE = 60L * 24L * 30L;
    
    public static final ByteOrder wordOrder = Base64Order.enhancedCoder;
    
    private final BufferedIndex   index;
    private final Log             log;
    private MetadataRepository    metadata;
    private final yacySeedDB      peers;
    private final File            primaryRoot, secondaryRoot;
    public        IndexingStack   queuePreStack;
    public        CrawlProfile    profilesActiveCrawls, profilesPassiveCrawls;
    public  CrawlProfile.entry    defaultProxyProfile;
    public  CrawlProfile.entry    defaultRemoteProfile;
    public  CrawlProfile.entry    defaultTextSnippetLocalProfile, defaultTextSnippetGlobalProfile;
    public  CrawlProfile.entry    defaultMediaSnippetLocalProfile, defaultMediaSnippetGlobalProfile;
    private final File            queuesRoot;
    private IODispatcher merger;
    
    public plasmaWordIndex(
            final String networkName,
            final Log log,
            final File indexPrimaryRoot,
            final File indexSecondaryRoot,
            final int entityCacheMaxSize,
            final boolean useCommons, 
            final int redundancy,
            final int partitionExponent,
            final boolean useCell) throws IOException {
        if (networkName == null || networkName.length() == 0) {
            log.logSevere("no network name given - shutting down");
            System.exit(0);
        }
        this.log = log;
        this.primaryRoot = new File(indexPrimaryRoot, networkName);
        this.secondaryRoot = new File(indexSecondaryRoot, networkName);
        File indexPrimaryTextLocation = new File(this.primaryRoot, "TEXT");
        if (!indexPrimaryTextLocation.exists()) {
            // patch old index locations; the secondary path is patched in plasmaCrawlLURL
            final File oldPrimaryPath = new File(new File(indexPrimaryRoot, "PUBLIC"), "TEXT");
            final File oldPrimaryTextLocation = new File(new File(indexPrimaryRoot, "PUBLIC"), "TEXT");
            if (oldPrimaryPath.exists() && oldPrimaryTextLocation.exists()) {
                // move the text folder from the old location to the new location
                assert !indexPrimaryTextLocation.exists();
                indexPrimaryTextLocation.mkdirs();
                if (oldPrimaryTextLocation.renameTo(indexPrimaryTextLocation)) {
                    FileUtils.deletedelete(oldPrimaryPath);
                } else {
                    indexPrimaryTextLocation = oldPrimaryTextLocation; // emergency case: stay with old directory
                }
            }
        }
        
        // check if the peer has migrated the index
        if (new File(indexPrimaryTextLocation, "RICOLLECTION").exists()) {
            this.merger = (useCell) ? new IODispatcher(1, 1) : null;
            if (this.merger != null) this.merger.start();
            this.index = (useCell) ? 
                                    new IndexCollectionMigration(
                                    indexPrimaryTextLocation,
                                    wordOrder,
                                    ReferenceRow.urlEntryRow,
                                    entityCacheMaxSize,
                                    maxCellArrayFiles,
                                    this.merger,
                                    log)
                                   :
                                    new BufferedIndexCollection(
                                            indexPrimaryTextLocation,
                                            wordOrder,
                                            ReferenceRow.urlEntryRow,
                                            entityCacheMaxSize,
                                            useCommons, 
                                            redundancy,
                                            log);
        } else {
            this.merger = new IODispatcher(1, 1);
            this.merger.start();
            this.index = new IndexCell(
                                    new File(indexPrimaryTextLocation, "RICELL"),
                                    wordOrder,
                                    ReferenceRow.urlEntryRow,
                                    entityCacheMaxSize,
                                    maxCellArrayFiles,
                                    this.merger);
        }
        
        
            
        // migrate LURL-db files into new subdirectory METADATA
        File textdir = new File(this.secondaryRoot, "TEXT");
        File metadatadir = new File(textdir, "METADATA");
        if (!metadatadir.exists()) metadatadir.mkdirs();
        String[] l = textdir.list();
        for (int i = 0; i < l.length; i++) {
            if (l[i].startsWith("urls.")) (new File(textdir, l[i])).renameTo(new File(metadatadir, l[i]));
        }
        // create LURL-db
        metadata = new MetadataRepository(metadatadir);
        
        // make crawl profiles database and default profiles
        this.queuesRoot = new File(this.primaryRoot, "QUEUES");
        this.queuesRoot.mkdirs();
        this.log.logConfig("Initializing Crawl Profiles");
        final File profilesActiveFile = new File(queuesRoot, DBFILE_ACTIVE_CRAWL_PROFILES);
        if (!profilesActiveFile.exists()) {
            // migrate old file
            final File oldFile = new File(new File(queuesRoot.getParentFile().getParentFile().getParentFile(), "PLASMADB"), "crawlProfilesActive1.db");
            if (oldFile.exists()) oldFile.renameTo(profilesActiveFile);
        }
        try {
            this.profilesActiveCrawls = new CrawlProfile(profilesActiveFile);
        } catch (IOException e) {
            FileUtils.deletedelete(profilesActiveFile);
            try {
                this.profilesActiveCrawls = new CrawlProfile(profilesActiveFile);
            } catch (IOException e1) {
                e1.printStackTrace();
                this.profilesActiveCrawls = null;
            }
        }
        initActiveCrawlProfiles();
        log.logConfig("Loaded active crawl profiles from file " + profilesActiveFile.getName() +
                ", " + this.profilesActiveCrawls.size() + " entries" +
                ", " + profilesActiveFile.length()/1024);
        final File profilesPassiveFile = new File(queuesRoot, DBFILE_PASSIVE_CRAWL_PROFILES);
        if (!profilesPassiveFile.exists()) {
            // migrate old file
            final File oldFile = new File(new File(queuesRoot.getParentFile().getParentFile().getParentFile(), "PLASMADB"), "crawlProfilesPassive1.db");
            if (oldFile.exists()) oldFile.renameTo(profilesPassiveFile);
        }
        try {
            this.profilesPassiveCrawls = new CrawlProfile(profilesPassiveFile);
        } catch (IOException e) {
            FileUtils.deletedelete(profilesPassiveFile);
            try {
                this.profilesPassiveCrawls = new CrawlProfile(profilesPassiveFile);
            } catch (IOException e1) {
                e1.printStackTrace();
                this.profilesPassiveCrawls = null;
            }
        }
        log.logConfig("Loaded passive crawl profiles from file " + profilesPassiveFile.getName() +
                ", " + this.profilesPassiveCrawls.size() + " entries" +
                ", " + profilesPassiveFile.length()/1024);
        
        // init queues
        final File preStackFile = new File(queuesRoot, "urlNoticePreStack");
        if (!preStackFile.exists()) {
            // migrate old file
            final File oldFile = new File(new File(queuesRoot.getParentFile().getParentFile().getParentFile(), "PLASMADB"), "switchboardQueue.stack");
            if (oldFile.exists()) oldFile.renameTo(preStackFile);
        }
        this.queuePreStack = new IndexingStack(this, preStackFile, this.profilesActiveCrawls);
        
        // create or init seed cache
        final File networkRoot = new File(this.primaryRoot, "NETWORK");
        networkRoot.mkdirs();
        final File mySeedFile = new File(networkRoot, yacySeedDB.DBFILE_OWN_SEED);
        final File oldSeedFile = new File(new File(indexPrimaryRoot.getParentFile(), "YACYDB"), "mySeed.txt");
        if (oldSeedFile.exists()) oldSeedFile.renameTo(mySeedFile);
        peers = new yacySeedDB(
                networkRoot,
                "seed.new.heap",
                "seed.old.heap",
                "seed.pot.heap",
                mySeedFile,
                redundancy,
                partitionExponent);
    }
    
    public MetadataRepository metadata() {
        return this.metadata;
    }

    public yacySeedDB peers() {
        return this.peers;
    }

    public BufferedIndex index() {
        return this.index;
    }
    
    public void clear() {
        try {
            index.clear();
            metadata.clear();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        queuePreStack.clear();
    }
    
    private void initActiveCrawlProfiles() {
        this.defaultProxyProfile = null;
        this.defaultRemoteProfile = null;
        this.defaultTextSnippetLocalProfile = null;
        this.defaultTextSnippetGlobalProfile = null;
        this.defaultMediaSnippetLocalProfile = null;
        this.defaultMediaSnippetGlobalProfile = null;
        final Iterator<CrawlProfile.entry> i = this.profilesActiveCrawls.profiles(true);
        CrawlProfile.entry profile;
        String name;
        try {
            while (i.hasNext()) {
                profile = i.next();
                name = profile.name();
                if (name.equals(CRAWL_PROFILE_PROXY)) this.defaultProxyProfile = profile;
                if (name.equals(CRAWL_PROFILE_REMOTE)) this.defaultRemoteProfile = profile;
                if (name.equals(CRAWL_PROFILE_SNIPPET_LOCAL_TEXT)) this.defaultTextSnippetLocalProfile = profile;
                if (name.equals(CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT)) this.defaultTextSnippetGlobalProfile = profile;
                if (name.equals(CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA)) this.defaultMediaSnippetLocalProfile = profile;
                if (name.equals(CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA)) this.defaultMediaSnippetGlobalProfile = profile;
            }
        } catch (final Exception e) {
            this.profilesActiveCrawls.clear();
            this.defaultProxyProfile = null;
            this.defaultRemoteProfile = null;
            this.defaultTextSnippetLocalProfile = null;
            this.defaultTextSnippetGlobalProfile = null;
            this.defaultMediaSnippetLocalProfile = null;
            this.defaultMediaSnippetGlobalProfile = null;
        }
        
        if (this.defaultProxyProfile == null) {
            // generate new default entry for proxy crawling
            this.defaultProxyProfile = this.profilesActiveCrawls.newEntry("proxy", null, CrawlProfile.KEYWORDS_PROXY, CrawlProfile.MATCH_ALL, CrawlProfile.MATCH_NEVER,
                    0 /*Integer.parseInt(getConfig(PROXY_PREFETCH_DEPTH, "0"))*/,
                    this.profilesActiveCrawls.getRecrawlDate(CRAWL_PROFILE_PROXY_RECRAWL_CYCLE), -1, -1, false,
                    true /*getConfigBool(PROXY_INDEXING_LOCAL_TEXT, true)*/,
                    true /*getConfigBool(PROXY_INDEXING_LOCAL_MEDIA, true)*/,
                    true, true,
                    false /*getConfigBool(PROXY_INDEXING_REMOTE, false)*/, true, true, true);
        }
        if (this.defaultRemoteProfile == null) {
            // generate new default entry for remote crawling
            defaultRemoteProfile = this.profilesActiveCrawls.newEntry(CRAWL_PROFILE_REMOTE, null, CrawlProfile.KEYWORDS_REMOTE, CrawlProfile.MATCH_ALL, CrawlProfile.MATCH_NEVER, 0,
                    -1, -1, -1, true, true, true, false, true, false, true, true, false);
        }
        if (this.defaultTextSnippetLocalProfile == null) {
            // generate new default entry for snippet fetch and optional crawling
            defaultTextSnippetLocalProfile = this.profilesActiveCrawls.newEntry(CRAWL_PROFILE_SNIPPET_LOCAL_TEXT, null, CrawlProfile.KEYWORDS_SNIPPET, CrawlProfile.MATCH_ALL, CrawlProfile.MATCH_NEVER, 0,
            		this.profilesActiveCrawls.getRecrawlDate(CRAWL_PROFILE_SNIPPET_LOCAL_TEXT_RECRAWL_CYCLE), -1, -1, true, false, false, false, false, false, true, true, false);
        }
        if (this.defaultTextSnippetGlobalProfile == null) {
            // generate new default entry for snippet fetch and optional crawling
            defaultTextSnippetGlobalProfile = this.profilesActiveCrawls.newEntry(CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT, null, CrawlProfile.KEYWORDS_SNIPPET, CrawlProfile.MATCH_ALL, CrawlProfile.MATCH_NEVER, 0,
            		this.profilesActiveCrawls.getRecrawlDate(CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT_RECRAWL_CYCLE), -1, -1, true, true, true, true, true, false, true, true, false);
        }
        if (this.defaultMediaSnippetLocalProfile == null) {
            // generate new default entry for snippet fetch and optional crawling
            defaultMediaSnippetLocalProfile = this.profilesActiveCrawls.newEntry(CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA, null, CrawlProfile.KEYWORDS_SNIPPET, CrawlProfile.MATCH_ALL, CrawlProfile.MATCH_NEVER, 0,
            		this.profilesActiveCrawls.getRecrawlDate(CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA_RECRAWL_CYCLE), -1, -1, true, false, false, false, false, false, true, true, false);
        }
        if (this.defaultMediaSnippetGlobalProfile == null) {
            // generate new default entry for snippet fetch and optional crawling
            defaultMediaSnippetGlobalProfile = this.profilesActiveCrawls.newEntry(CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA, null, CrawlProfile.KEYWORDS_SNIPPET, CrawlProfile.MATCH_ALL, CrawlProfile.MATCH_NEVER, 0,
            		this.profilesActiveCrawls.getRecrawlDate(CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA_RECRAWL_CYCLE), -1, -1, true, false, true, true, true, false, true, true, false);
        }
    }
    
    private void resetProfiles() {
        final File pdb = new File(this.queuesRoot, DBFILE_ACTIVE_CRAWL_PROFILES);
        if (pdb.exists()) FileUtils.deletedelete(pdb);
        try {
            profilesActiveCrawls = new CrawlProfile(pdb);
        } catch (IOException e) {
            e.printStackTrace();
        }
        initActiveCrawlProfiles();
    }
    
    
    public boolean cleanProfiles() throws InterruptedException {
        if (queuePreStack.size() > 0) return false;
        final Iterator<CrawlProfile.entry> iter = profilesActiveCrawls.profiles(true);
        CrawlProfile.entry entry;
        boolean hasDoneSomething = false;
        try {
            while (iter.hasNext()) {
                // check for interruption
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Shutdown in progress");
                
                // getting next profile
                entry = iter.next();
                if (!((entry.name().equals(CRAWL_PROFILE_PROXY))  ||
                      (entry.name().equals(CRAWL_PROFILE_REMOTE)) ||
                      (entry.name().equals(CRAWL_PROFILE_SNIPPET_LOCAL_TEXT))  ||
                      (entry.name().equals(CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT)) ||
                      (entry.name().equals(CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA)) ||
                      (entry.name().equals(CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA)))) {
                    profilesPassiveCrawls.newEntry(entry.map());
                    iter.remove();
                    hasDoneSomething = true;
                }
            }
        } catch (final kelondroException e) {
            resetProfiles();
            hasDoneSomething = true;
        }
        return hasDoneSomething;
    }
    
    public File getLocation(final boolean primary) {
        return (primary) ? this.primaryRoot : this.secondaryRoot;
    }

    /**
     * this is called by the switchboard to put in a new page into the index
     * use all the words in one condenser object to simultanous create index entries
     * 
     * @param url
     * @param urlModified
     * @param document
     * @param condenser
     * @param language
     * @param doctype
     * @param outlinksSame
     * @param outlinksOther
     * @return
     */
    public int addPageIndex(final yacyURL url, final Date urlModified, final plasmaParserDocument document, final plasmaCondenser condenser, final String language, final char doctype, final int outlinksSame, final int outlinksOther) {
        int wordCount = 0;
        final int urlLength = url.toNormalform(true, true).length();
        final int urlComps = htmlFilterContentScraper.urlComps(url.toString()).length;
        
        // iterate over all words of context text
        final Iterator<Map.Entry<String, Word>> i = condenser.words().entrySet().iterator();
        Map.Entry<String, Word> wentry;
        String word;
        ReferenceRow ientry;
        Word wprop;
        while (i.hasNext()) {
            wentry = i.next();
            word = wentry.getKey();
            wprop = wentry.getValue();
            assert (wprop.flags != null);
            ientry = new ReferenceRow(url.hash(),
                        urlLength, urlComps, (document == null) ? urlLength : document.dc_title().length(),
                        wprop.count,
                        condenser.RESULT_NUMB_WORDS,
                        condenser.RESULT_NUMB_SENTENCES,
                        wprop.posInText,
                        wprop.posInPhrase,
                        wprop.numOfPhrase,
                        urlModified.getTime(),
                        System.currentTimeMillis(),
                        language,
                        doctype,
                        outlinksSame, outlinksOther,
                        wprop.flags);
            try {
                this.index.add(Word.word2hash(word), ientry);
            } catch (IOException e) {
                e.printStackTrace();
            }
            wordCount++;
        }
        
        return wordCount;
    }

    public void close() {
        if (this.merger != null) this.merger.terminate();
        index.close();
        metadata.close();
        peers.close();
        profilesActiveCrawls.close();
        queuePreStack.close();
    }

    public MetadataRowContainer storeDocument(final IndexingStack.QueueEntry entry, final plasmaParserDocument document, final plasmaCondenser condenser) throws IOException {
        final long startTime = System.currentTimeMillis();

        // CREATE INDEX
        
        // load some document metadata
        final String dc_title = document.dc_title();
        final yacyURL referrerURL = entry.referrerURL();
        final Date docDate = entry.getModificationDate();
        
        // do a identification of the language
        String language = condenser.language(); // this is a statistical analysation of the content: will be compared with other attributes
        String bymetadata = document.languageByMetadata(); // the languageByMetadata may return null if there was no declaration
        if (language == null) {
            // no statistics available, we take either the metadata (if given) or the TLD
            language = (bymetadata == null) ? entry.url().language() : bymetadata;
            System.out.println("*** DEBUG LANGUAGE-BY-STATISTICS: " + entry.url() + " FAILED, taking " + ((bymetadata == null) ? "TLD" : "metadata") + ": " + language);
        } else {
            if (bymetadata == null) {
                // two possible results: compare and report conflicts
                if (language.equals(entry.url().language()))
                    System.out.println("*** DEBUG LANGUAGE-BY-STATISTICS: " + entry.url() + " CONFIRMED - TLD IDENTICAL: " + language);
                else {
                    String error = "*** DEBUG LANGUAGE-BY-STATISTICS: " + entry.url() + " CONFLICTING: " + language + " (the language given by the TLD is " + entry.url().language() + ")";
                    // see if we have a hint in the url that the statistic was right
                    String u = entry.url().toNormalform(true, false).toLowerCase();
                    if (!u.contains("/" + language + "/") && !u.contains("/" + iso639.country(language).toLowerCase() + "/")) {
                        // no confirmation using the url, use the TLD
                        language = entry.url().language();
                        System.out.println(error + ", corrected using the TLD");
                    } else {
                        // this is a strong hint that the statistics was in fact correct
                        System.out.println(error + ", but the url proves that the statistic is correct");
                    }
                }
            } else {
                // here we have three results: we can do a voting
                if (language.equals(bymetadata)) {
                    System.out.println("*** DEBUG LANGUAGE-BY-STATISTICS: " + entry.url() + " CONFIRMED - METADATA IDENTICAL: " + language);
                } else if (language.equals(entry.url().language())) {
                    System.out.println("*** DEBUG LANGUAGE-BY-STATISTICS: " + entry.url() + " CONFIRMED - TLD IS IDENTICAL: " + language);
                } else if (bymetadata.equals(entry.url().language())) {
                    System.out.println("*** DEBUG LANGUAGE-BY-STATISTICS: " + entry.url() + " CONFLICTING: " + language + " BUT METADATA AND TLD ARE IDENTICAL: " + bymetadata + ")");
                    language = bymetadata;
                } else {
                    System.out.println("*** DEBUG LANGUAGE-BY-STATISTICS: " + entry.url() + " CONFLICTING: ALL DIFFERENT! statistic: " + language + ", metadata: " + bymetadata + ", TLD: + " + entry.url().language() + ". taking metadata.");
                    language = bymetadata;
                }
            }
        }
        
        // create a new loaded URL db entry
        final long ldate = System.currentTimeMillis();
        final MetadataRowContainer newEntry = new MetadataRowContainer(
                entry.url(),                               // URL
                dc_title,                                  // document description
                document.dc_creator(),                     // author
                document.dc_subject(' '),                  // tags
                "",                                        // ETag
                docDate,                                   // modification date
                new Date(),                                // loaded date
                new Date(ldate + Math.max(0, ldate - docDate.getTime()) / 2), // freshdate, computed with Proxy-TTL formula 
                (referrerURL == null) ? null : referrerURL.hash(),            // referer hash
                new byte[0],                               // md5
                (int) entry.size(),                        // size
                condenser.RESULT_NUMB_WORDS,               // word count
                httpdProxyCacheEntry.docType(document.dc_format()), // doctype
                condenser.RESULT_FLAGS,                    // flags
                language,                                  // language
                document.inboundLinks(),                   // inbound links
                document.outboundLinks(),                  // outbound links
                document.getAudiolinks().size(),           // laudio
                document.getImages().size(),               // limage
                document.getVideolinks().size(),           // lvideo
                document.getApplinks().size()              // lapp
        );
        
        // STORE URL TO LOADED-URL-DB
        metadata.store(newEntry);
        
        final long storageEndTime = System.currentTimeMillis();
        
        // STORE PAGE INDEX INTO WORD INDEX DB
        final int words = addPageIndex(
                entry.url(),                                  // document url
                docDate,                                      // document mod date
                document,                                     // document content
                condenser,                                    // document condenser
                language,                                     // document language
                httpdProxyCacheEntry.docType(document.dc_format()),  // document type
                document.inboundLinks(),                      // inbound links
                document.outboundLinks()                      // outbound links
        );
            
        final long indexingEndTime = System.currentTimeMillis();
        
        if (log.isInfo()) {
            // TODO: UTF-8 docDescription seems not to be displayed correctly because
            // of string concatenation
            log.logInfo("*Indexed " + words + " words in URL " + entry.url() +
                    " [" + entry.urlHash() + "]" +
                    "\n\tDescription:  " + dc_title +
                    "\n\tMimeType: "  + document.dc_format() + " | Charset: " + document.getCharset() + " | " +
                    "Size: " + document.getTextLength() + " bytes | " +
                    "Anchors: " + ((document.getAnchors() == null) ? 0 : document.getAnchors().size()) +
                    "\n\tLinkStorageTime: " + (storageEndTime - startTime) + " ms | " +
                    "indexStorageTime: " + (indexingEndTime - storageEndTime) + " ms");
            RSSFeed.channels((entry.initiator().equals(peers.mySeed().hash)) ? RSSFeed.LOCALINDEXING : RSSFeed.REMOTEINDEXING).addMessage(new RSSMessage("Indexed web page", dc_title, entry.url().toNormalform(true, false)));
        }
        
        // finished
        return newEntry;
    }
    
    @SuppressWarnings("unchecked")
    public HashMap<String, ReferenceContainer>[] localSearchContainers(
                            final TreeSet<String> queryHashes, 
                            final TreeSet<String> excludeHashes, 
                            final Set<String> urlselection) {
        // search for the set of hashes and return a map of of wordhash:indexContainer containing the seach result

        // retrieve entities that belong to the hashes
        HashMap<String, ReferenceContainer> inclusionContainers = (queryHashes.size() == 0) ? new HashMap<String, ReferenceContainer>(0) : getContainers(
                        queryHashes,
                        urlselection);
        if ((inclusionContainers.size() != 0) && (inclusionContainers.size() < queryHashes.size())) inclusionContainers = new HashMap<String, ReferenceContainer>(0); // prevent that only a subset is returned
        final HashMap<String, ReferenceContainer> exclusionContainers = (inclusionContainers.size() == 0) ? new HashMap<String, ReferenceContainer>(0) : getContainers(
                excludeHashes,
                urlselection);
        return new HashMap[]{inclusionContainers, exclusionContainers};
    }

    /**
     * collect containers for given word hashes. This collection stops if a single container does not contain any references.
     * In that case only a empty result is returned.
     * @param wordHashes
     * @param urlselection
     * @return map of wordhash:indexContainer
     */
    private HashMap<String, ReferenceContainer> getContainers(final Set<String> wordHashes, final Set<String> urlselection) {
        // retrieve entities that belong to the hashes
        final HashMap<String, ReferenceContainer> containers = new HashMap<String, ReferenceContainer>(wordHashes.size());
        String singleHash;
        ReferenceContainer singleContainer;
            final Iterator<String> i = wordHashes.iterator();
            while (i.hasNext()) {
            
                // get next word hash:
                singleHash = i.next();
            
                // retrieve index
                try {
                    singleContainer = index.get(singleHash, urlselection);
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }
            
                // check result
                if ((singleContainer == null || singleContainer.size() == 0)) return new HashMap<String, ReferenceContainer>(0);
            
                containers.put(singleHash, singleContainer);
            }
        return containers;
    }
    
    //  The Cleaner class was provided as "UrldbCleaner" by Hydrox
    public synchronized ReferenceCleaner getReferenceCleaner(final String startHash) {
        return new ReferenceCleaner(startHash);
    }
    
    public class ReferenceCleaner extends Thread {
        
        private final String startHash;
        private boolean run = true;
        private boolean pause = false;
        public int rwiCountAtStart = 0;
        public String wordHashNow = "";
        public String lastWordHash = "";
        public int lastDeletionCounter = 0;
        
        public ReferenceCleaner(final String startHash) {
            this.startHash = startHash;
            this.rwiCountAtStart = index().size();
        }
        
        public void run() {
            Log.logInfo("INDEXCLEANER", "IndexCleaner-Thread started");
            ReferenceContainer container = null;
            ReferenceRow entry = null;
            yacyURL url = null;
            final HashSet<String> urlHashs = new HashSet<String>();
            try {
                Iterator<ReferenceContainer> indexContainerIterator = index.references(startHash, false, 100, false).iterator();
                while (indexContainerIterator.hasNext() && run) {
                    waiter();
                    container = indexContainerIterator.next();
                    final Iterator<ReferenceRow> containerIterator = container.entries();
                    wordHashNow = container.getWordHash();
                    while (containerIterator.hasNext() && run) {
                        waiter();
                        entry = containerIterator.next();
                        // System.out.println("Wordhash: "+wordHash+" UrlHash:
                        // "+entry.getUrlHash());
                        final MetadataRowContainer ue = metadata.load(entry.urlHash(), entry, 0);
                        if (ue == null) {
                            urlHashs.add(entry.urlHash());
                        } else {
                            url = ue.metadata().url();
                            if ((url == null) || (plasmaSwitchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_CRAWLER, url) == true)) {
                                urlHashs.add(entry.urlHash());
                            }
                        }
                    }
                    if (urlHashs.size() > 0) try {
                        final int removed = index.remove(container.getWordHash(), urlHashs);
                        Log.logFine("INDEXCLEANER", container.getWordHash() + ": " + removed + " of " + container.size() + " URL-entries deleted");
                        lastWordHash = container.getWordHash();
                        lastDeletionCounter = urlHashs.size();
                        urlHashs.clear();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    
                    if (!containerIterator.hasNext()) {
                        // We may not be finished yet, try to get the next chunk of wordHashes
                        final TreeSet<ReferenceContainer> containers = index.references(container.getWordHash(), false, 100, false);
                        indexContainerIterator = containers.iterator();
                        // Make sure we don't get the same wordhash twice, but don't skip a word
                        if ((indexContainerIterator.hasNext()) && (!container.getWordHash().equals(indexContainerIterator.next().getWordHash()))) {
                            indexContainerIterator = containers.iterator();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.logInfo("INDEXCLEANER", "IndexCleaner-Thread stopped");
        }
        
        public void abort() {
            synchronized(this) {
                run = false;
                this.notifyAll();
            }
        }

        public void pause() {
            synchronized (this) {
                if (!pause) {
                    pause = true;
                    Log.logInfo("INDEXCLEANER", "IndexCleaner-Thread paused");
                }
            }
        }

        public void endPause() {
            synchronized (this) {
                if (pause) {
                    pause = false;
                    this.notifyAll();
                    Log.logInfo("INDEXCLEANER", "IndexCleaner-Thread resumed");
                }
            }
        }

        public void waiter() {
            synchronized (this) {
                if (this.pause) {
                    try {
                        this.wait();
                    } catch (final InterruptedException e) {
                        this.run = false;
                        return;
                    }
                }
            }
        }
    }
}
