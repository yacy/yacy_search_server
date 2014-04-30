// CrawlSwitchboard.java
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

package net.yacy.crawler;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.CrawlQueues;
import net.yacy.crawler.data.NoticedURL.StackType;
import net.yacy.crawler.retrieval.Request;
import net.yacy.kelondro.blob.MapHeap;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.kelondroException;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;

public final class CrawlSwitchboard {

    public static final String CRAWL_PROFILE_PROXY = "proxy";
    public static final String CRAWL_PROFILE_REMOTE = "remote";
    public static final String CRAWL_PROFILE_SNIPPET_LOCAL_TEXT = "snippetLocalText";
    public static final String CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT = "snippetGlobalText";
    public static final String CRAWL_PROFILE_GREEDY_LEARNING_TEXT = "snippetGreedyLearningText";
    public static final String CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA = "snippetLocalMedia";
    public static final String CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA = "snippetGlobalMedia";
    public static final String CRAWL_PROFILE_SURROGATE = "surrogates";

    public static Set<String> DEFAULT_PROFILES = new HashSet<String>();
    static {
        DEFAULT_PROFILES.add(CRAWL_PROFILE_PROXY);
        DEFAULT_PROFILES.add(CRAWL_PROFILE_REMOTE);
        DEFAULT_PROFILES.add(CRAWL_PROFILE_SNIPPET_LOCAL_TEXT);
        DEFAULT_PROFILES.add(CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT);
        DEFAULT_PROFILES.add(CRAWL_PROFILE_GREEDY_LEARNING_TEXT);
        DEFAULT_PROFILES.add(CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA);
        DEFAULT_PROFILES.add(CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA);
        DEFAULT_PROFILES.add(CRAWL_PROFILE_SURROGATE);
    }
    
    public static final String DBFILE_ACTIVE_CRAWL_PROFILES = "crawlProfilesActive1.heap";
    public static final String DBFILE_PASSIVE_CRAWL_PROFILES = "crawlProfilesPassive1.heap";

    public static final long CRAWL_PROFILE_PROXY_RECRAWL_CYCLE = 60L * 24L;
    public static final long CRAWL_PROFILE_SNIPPET_LOCAL_TEXT_RECRAWL_CYCLE = 60L * 24L * 30L;
    public static final long CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT_RECRAWL_CYCLE = 60L * 24L * 30L;
    public static final long CRAWL_PROFILE_GREEDY_LEARNING_TEXT_RECRAWL_CYCLE = 60L * 24L * 30L;
    public static final long CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA_RECRAWL_CYCLE = 60L * 24L * 30L;
    public static final long CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA_RECRAWL_CYCLE = 60L * 24L * 30L;
    public static final long CRAWL_PROFILE_SURROGATE_RECRAWL_CYCLE = 60L * 24L * 30L;

    private final ConcurrentLog log;
    private MapHeap profilesActiveCrawls;
    private final MapHeap profilesPassiveCrawls;
    private final Map<byte[], CrawlProfile> profilesActiveCrawlsCache; //TreeMap<byte[], DigestURI>(Base64Order.enhancedCoder);
    private final Map<String, RowHandleSet> profilesActiveCrawlsCounter;
    public CrawlProfile defaultProxyProfile;
    public CrawlProfile defaultRemoteProfile;
    public CrawlProfile defaultTextSnippetLocalProfile, defaultTextSnippetGlobalProfile;
    public CrawlProfile defaultTextGreedyLearningProfile;
    public CrawlProfile defaultMediaSnippetLocalProfile, defaultMediaSnippetGlobalProfile;
    public CrawlProfile defaultSurrogateProfile;
    private final File queuesRoot;
    private Switchboard switchboard;

    public CrawlSwitchboard(final String networkName, Switchboard switchboard) {

        this.switchboard = switchboard;
        this.log = this.switchboard.log;
        this.queuesRoot = this.switchboard.queuesRoot;
        this.log.info("Initializing Word Index for the network '" + networkName + "'.");

        if ( networkName == null || networkName.isEmpty() ) {
            log.severe("no network name given - shutting down");
            System.exit(0);
        }
        this.profilesActiveCrawlsCache = Collections.synchronizedMap(new TreeMap<byte[], CrawlProfile>(Base64Order.enhancedCoder));
        this.profilesActiveCrawlsCounter = new ConcurrentHashMap<String, RowHandleSet>();

        // make crawl profiles database and default profiles
        this.queuesRoot.mkdirs();
        this.log.config("Initializing Crawl Profiles");

        final File profilesActiveFile = new File(queuesRoot, DBFILE_ACTIVE_CRAWL_PROFILES);
        this.profilesActiveCrawls = loadFromDB(profilesActiveFile);
        for ( final byte[] handle : this.profilesActiveCrawls.keySet() ) {
            CrawlProfile p;
            try {
                p = new CrawlProfile(this.profilesActiveCrawls.get(handle));
            } catch (final IOException e ) {
                p = null;
            } catch (final SpaceExceededException e ) {
                p = null;
            }
            if ( p == null ) {
                continue;
            }
        }
        initActiveCrawlProfiles();
        log.info("Loaded active crawl profiles from file "
            + profilesActiveFile.getName()
            + ", "
            + this.profilesActiveCrawls.size()
            + " entries");

        final File profilesPassiveFile = new File(queuesRoot, DBFILE_PASSIVE_CRAWL_PROFILES);
        this.profilesPassiveCrawls = loadFromDB(profilesPassiveFile);
        for ( final byte[] handle : this.profilesPassiveCrawls.keySet() ) {
            CrawlProfile p;
            try {
                p = new CrawlProfile(this.profilesPassiveCrawls.get(handle));
                ConcurrentLog.info("CrawlProfiles", "loaded Profile " + p.handle() + ": " + p.collectionName());
            } catch (final IOException e ) {
                continue;
            } catch (final SpaceExceededException e ) {
                continue;
            }
        }
        log.info("Loaded passive crawl profiles from file "
            + profilesPassiveFile.getName()
            + ", "
            + this.profilesPassiveCrawls.size()
            + " entries"
            + ", "
            + profilesPassiveFile.length()
            / 1024);
    }

    /**
     * Get a profile from active or passive stack. Should be used to be sure not to miss old, cleaned profiles.
     * A profile that was discovered from the passive stack is automatically shifted back to the active stack.
     * @param profileKey
     * @return
     */
    public CrawlProfile get(final byte[] profileKey) {
        CrawlProfile profile = getActive(profileKey);
        if (profile != null) return profile;
        profile = getPassive(profileKey);
        if (profile == null) return null;
        // clean up
        this.putActive(profileKey, profile);
        this.removePassive(profileKey);
        return profile;
    }

    public CrawlProfile getActive(final byte[] profileKey) {
        if ( profileKey == null ) {
            return null;
        }
        // get from cache
        CrawlProfile p = this.profilesActiveCrawlsCache.get(profileKey);
        if ( p != null ) {
            return p;
        }

        // get from db
        Map<String, String> m;
        try {
            m = this.profilesActiveCrawls.get(profileKey);
        } catch (final IOException e ) {
            m = null;
        } catch (final SpaceExceededException e ) {
            m = null;
        }
        if ( m == null ) {
            return null; //return getPassive(profileKey);
        }
        p = new CrawlProfile(m);
        this.profilesActiveCrawlsCache.put(profileKey, p);
        return p;
    }

    public CrawlProfile getPassive(final byte[] profileKey) {
        if ( profileKey == null ) {
            return null;
        }
        Map<String, String> m;
        try {
            m = this.profilesPassiveCrawls.get(profileKey);
        } catch (final IOException e ) {
            m = null;
        } catch (final SpaceExceededException e ) {
            m = null;
        }
        if ( m == null ) {
            return null;
        }
        return new CrawlProfile(m);
    }

    public Set<byte[]> getActive() {
        return this.profilesActiveCrawls.keySet();
    }

    public Set<byte[]> getPassive() {
        return this.profilesPassiveCrawls.keySet();
    }

    public void removeActive(final byte[] profileKey) {
        if ( profileKey == null ) {
            return;
        }
        this.profilesActiveCrawlsCache.remove(profileKey);
        this.profilesActiveCrawls.remove(profileKey);
    }

    public void removePassive(final byte[] profileKey) {
        if ( profileKey == null ) {
            return;
        }
        this.profilesPassiveCrawls.remove(profileKey);
    }

    public void putActive(final byte[] profileKey, final CrawlProfile profile) {
        this.profilesActiveCrawls.put(profileKey, profile);
        this.profilesActiveCrawlsCache.put(profileKey, profile);
        this.removePassive(profileKey);
    }

    public void putPassive(final byte[] profileKey, final CrawlProfile profile) {
        this.profilesPassiveCrawls.put(profileKey, profile);
        this.removeActive(profileKey);
    }

    public RowHandleSet getURLHashes(final byte[] profileKey) {
        return this.profilesActiveCrawlsCounter.get(ASCII.String(profileKey));
    }
    
    
    private void initActiveCrawlProfiles() {
        // generate new default entry for proxy crawling
    	final Switchboard sb = Switchboard.getSwitchboard();
        this.defaultProxyProfile =
            new CrawlProfile(
                CRAWL_PROFILE_PROXY,
                CrawlProfile.MATCH_ALL_STRING,   //crawlerUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //crawlerIpMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerIpMustNotMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerCountryMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerNoDepthLimitMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexContentMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexContentMustNotMatch
                Integer.parseInt(sb.getConfig(SwitchboardConstants.PROXY_PREFETCH_DEPTH, "0")),
                true,
                CrawlProfile.getRecrawlDate(CRAWL_PROFILE_PROXY_RECRAWL_CYCLE),
                -1,
				false, true, true,
                sb.getConfigBool(SwitchboardConstants.PROXY_INDEXING_LOCAL_TEXT, true),
                sb.getConfigBool(SwitchboardConstants.PROXY_INDEXING_LOCAL_MEDIA, true),
                true,
                sb.getConfigBool(SwitchboardConstants.PROXY_INDEXING_REMOTE, false),
                CacheStrategy.IFFRESH,
                "robot_" + CRAWL_PROFILE_PROXY,
                ClientIdentification.yacyProxyAgentName);
        this.profilesActiveCrawls.put(
            UTF8.getBytes(this.defaultProxyProfile.handle()),
            this.defaultProxyProfile);
        // generate new default entry for remote crawling
        this.defaultRemoteProfile =
            new CrawlProfile(
                CRAWL_PROFILE_REMOTE,
                CrawlProfile.MATCH_ALL_STRING,   //crawlerUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //crawlerIpMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerIpMustNotMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerCountryMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerNoDepthLimitMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexContentMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexContentMustNotMatch
                0,
                false,
                -1,
                -1,
                true, true, true,
                true,
                true,
                false,
                false,
                CacheStrategy.IFFRESH,
                "robot_" + CRAWL_PROFILE_REMOTE,
                ClientIdentification.yacyInternetCrawlerAgentName);
        this.profilesActiveCrawls.put(
            UTF8.getBytes(this.defaultRemoteProfile.handle()),
            this.defaultRemoteProfile);
        // generate new default entry for snippet fetch and optional crawling
        this.defaultTextSnippetLocalProfile =
            new CrawlProfile(
                CRAWL_PROFILE_SNIPPET_LOCAL_TEXT,
                CrawlProfile.MATCH_ALL_STRING,   //crawlerUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //crawlerIpMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerIpMustNotMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerCountryMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerNoDepthLimitMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexContentMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexContentMustNotMatch
                0,
                false,
                CrawlProfile.getRecrawlDate(CRAWL_PROFILE_SNIPPET_LOCAL_TEXT_RECRAWL_CYCLE),
                -1,
                true, true, true,
                false,
                false,
                true,
                false,
                CacheStrategy.IFEXIST,
                "robot_" + CRAWL_PROFILE_SNIPPET_LOCAL_TEXT,
                ClientIdentification.yacyIntranetCrawlerAgentName);
        this.profilesActiveCrawls.put(
            UTF8.getBytes(this.defaultTextSnippetLocalProfile.handle()),
            this.defaultTextSnippetLocalProfile);
        // generate new default entry for snippet fetch and optional crawling
        this.defaultTextSnippetGlobalProfile =
            new CrawlProfile(
                CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT,
                CrawlProfile.MATCH_ALL_STRING,   //crawlerUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //crawlerIpMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerIpMustNotMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerCountryMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerNoDepthLimitMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexContentMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexContentMustNotMatch
                0,
                false,
                CrawlProfile.getRecrawlDate(CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT_RECRAWL_CYCLE),
                -1,
                true, true, true,
                true,
                true,
                true,
                false,
                CacheStrategy.IFEXIST,
                "robot_" + CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT,
                ClientIdentification.yacyIntranetCrawlerAgentName);
        this.profilesActiveCrawls.put(
            UTF8.getBytes(this.defaultTextSnippetGlobalProfile.handle()),
            this.defaultTextSnippetGlobalProfile);
        this.defaultTextSnippetGlobalProfile.setCacheStrategy(CacheStrategy.IFEXIST);
        // generate new default entry for greedy learning
        this.defaultTextGreedyLearningProfile =
            new CrawlProfile(
                CRAWL_PROFILE_GREEDY_LEARNING_TEXT,
                CrawlProfile.MATCH_ALL_STRING,   //crawlerUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //crawlerIpMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerIpMustNotMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerCountryMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerNoDepthLimitMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexContentMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexContentMustNotMatch
                0,
                false,
                CrawlProfile.getRecrawlDate(CRAWL_PROFILE_GREEDY_LEARNING_TEXT_RECRAWL_CYCLE),
                -1,
                true, true, true,
                false,
                false,
                true,
                false,
                CacheStrategy.IFEXIST,
                "robot_" + CRAWL_PROFILE_GREEDY_LEARNING_TEXT,
                ClientIdentification.browserAgentName);
        this.profilesActiveCrawls.put(
            UTF8.getBytes(this.defaultTextSnippetGlobalProfile.handle()),
            this.defaultTextSnippetGlobalProfile);
        // generate new default entry for snippet fetch and optional crawling
        this.defaultMediaSnippetLocalProfile =
            new CrawlProfile(
                CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA,
                CrawlProfile.MATCH_ALL_STRING,   //crawlerUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //crawlerIpMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerIpMustNotMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerCountryMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerNoDepthLimitMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexContentMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexContentMustNotMatch
                0,
                false,
                CrawlProfile.getRecrawlDate(CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA_RECRAWL_CYCLE),
                -1,
                true, true, true,
                false,
                false,
                true,
                false,
                CacheStrategy.IFEXIST,
                "robot_" + CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA,
                ClientIdentification.yacyIntranetCrawlerAgentName);
        this.profilesActiveCrawls.put(
            UTF8.getBytes(this.defaultMediaSnippetLocalProfile.handle()),
            this.defaultMediaSnippetLocalProfile);
        // generate new default entry for snippet fetch and optional crawling
        this.defaultMediaSnippetGlobalProfile =
            new CrawlProfile(
                CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA,
                CrawlProfile.MATCH_ALL_STRING,   //crawlerUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //crawlerIpMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerIpMustNotMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerCountryMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerNoDepthLimitMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexContentMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexContentMustNotMatch
                0,
                false,
                CrawlProfile.getRecrawlDate(CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA_RECRAWL_CYCLE),
                -1,
                true, true, true,
                false,
                true,
                true,
                false,
                CacheStrategy.IFEXIST,
                "robot_" + CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA,
                ClientIdentification.yacyIntranetCrawlerAgentName);
        this.profilesActiveCrawls.put(
            UTF8.getBytes(this.defaultMediaSnippetGlobalProfile.handle()),
            this.defaultMediaSnippetGlobalProfile);
        // generate new default entry for surrogate parsing
        this.defaultSurrogateProfile =
            new CrawlProfile(
                CRAWL_PROFILE_SURROGATE,
                CrawlProfile.MATCH_ALL_STRING,   //crawlerUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //crawlerIpMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerIpMustNotMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerCountryMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //crawlerNoDepthLimitMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING,   //indexContentMustMatch
                CrawlProfile.MATCH_NEVER_STRING, //indexContentMustNotMatch
                0,
                false,
                CrawlProfile.getRecrawlDate(CRAWL_PROFILE_SURROGATE_RECRAWL_CYCLE),
                -1,
                true, true, true,
                true,
                false,
                false,
                false,
                CacheStrategy.NOCACHE,
                "robot_" + CRAWL_PROFILE_SURROGATE,
                ClientIdentification.yacyIntranetCrawlerAgentName);
        this.profilesActiveCrawls.put(
            UTF8.getBytes(this.defaultSurrogateProfile.handle()),
            this.defaultSurrogateProfile);
    }

    private void resetProfiles() {
        this.profilesActiveCrawlsCache.clear();
        final File pdb = new File(this.queuesRoot, DBFILE_ACTIVE_CRAWL_PROFILES);
        if ( pdb.exists() ) {
            FileUtils.deletedelete(pdb);
        }
        try {
            this.profilesActiveCrawls =
                new MapHeap(pdb, Word.commonHashLength, NaturalOrder.naturalOrder, 1024 * 64, 500, ' ');
        } catch (final IOException e1 ) {
            ConcurrentLog.logException(e1);
            this.profilesActiveCrawls = null;
        }
        initActiveCrawlProfiles();
    }

    public boolean clear() throws InterruptedException {
        this.profilesActiveCrawlsCache.clear();
        CrawlProfile entry;
        boolean hasDoneSomething = false;
        try {
            for ( final byte[] handle : this.profilesActiveCrawls.keySet() ) {
                // check for interruption
                if ( Thread.currentThread().isInterrupted() ) {
                    throw new InterruptedException("Shutdown in progress");
                }

                // getting next profile
                try {
                    entry = new CrawlProfile(this.profilesActiveCrawls.get(handle));
                } catch (final IOException e ) {
                    continue;
                } catch (final SpaceExceededException e ) {
                    continue;
                }
                if (!DEFAULT_PROFILES.contains(entry.name())) {
                    final CrawlProfile p = new CrawlProfile(entry);
                    this.profilesPassiveCrawls.put(UTF8.getBytes(p.handle()), p);
                    this.profilesActiveCrawls.remove(handle);
                    hasDoneSomething = true;
                }
            }
        } catch (final kelondroException e ) {
            resetProfiles();
            hasDoneSomething = true;
        }
        return hasDoneSomething;
    }

    public Set<String> getActiveProfiles() {
        // find all profiles that are candidates for deletion
        Set<String> profileKeys = new HashSet<String>();
        for (final byte[] handle: this.getActive()) {
            CrawlProfile entry;
            entry = new CrawlProfile(this.getActive(handle));
            if (!CrawlSwitchboard.DEFAULT_PROFILES.contains(entry.name())) {
                profileKeys.add(ASCII.String(handle));
            }
        }
        return profileKeys;
    }
    
    public Set<String> getFinishesProfiles(CrawlQueues crawlQueues) {
        // clear the counter cache
        this.profilesActiveCrawlsCounter.clear();        
        
        // find all profiles that are candidates for deletion
        Set<String> deletionCandidate = getActiveProfiles();
        if (deletionCandidate.size() == 0) return new HashSet<String>(0);
        
        // iterate through all the queues and see if one of these handles appear there
        // this is a time-consuming process, set a time-out
        long timeout = System.currentTimeMillis() + 60000L; // one minute time
        try {
            for (StackType stack: StackType.values()) {
                Iterator<Request> sei = crawlQueues.noticeURL.iterator(stack);
                if (sei == null) continue;
                Request r;
                while (sei.hasNext()) {
                    r = sei.next();
                    if (r == null) continue;
                    String handle = r.profileHandle();
                    RowHandleSet us = this.profilesActiveCrawlsCounter.get(handle);
                    if (us == null) {us =  new RowHandleSet(Word.commonHashLength, Word.commonHashOrder, 0); this.profilesActiveCrawlsCounter.put(handle, us);}
                    if (us.size() < 100) us.put(r.url().hash()); // store the hash, but not too many
                    deletionCandidate.remove(handle);
                    if (deletionCandidate.size() == 0) return new HashSet<String>(0);
                    if (System.currentTimeMillis() > timeout) return new HashSet<String>(0); // give up; this is too large
                }
                if (deletionCandidate.size() == 0) return new HashSet<String>(0);
            }
            // look into the CrawlQueues.worker as well
            Map<DigestURL, Request> map = switchboard.crawlQueues.activeWorkerEntries();
            for (Request request: map.values()) {
                deletionCandidate.remove(request.profileHandle());
            }
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
            return new HashSet<String>(0);
        }
        return deletionCandidate;
    }
    
    public boolean allCrawlsFinished(CrawlQueues crawlQueues) {
        if (!crawlQueues.noticeURL.isEmpty()) return false;
        // look into the CrawlQueues.worker as well
        if (switchboard.crawlQueues.activeWorkerEntries().size() > 0) return false;
        return true;
    }
    
    public void cleanProfiles(Set<String> deletionCandidate) {
        // all entries that are left are candidates for deletion; do that now
        for (String h: deletionCandidate) {
            byte[] handle = ASCII.getBytes(h);
            final CrawlProfile p = this.getActive(handle);
            if (p != null) {
                this.putPassive(handle, p);
                this.removeActive(handle);
            }
        }
    }
    
    public synchronized void close() {
        this.profilesActiveCrawlsCache.clear();
        this.profilesActiveCrawls.close();
        this.profilesPassiveCrawls.close();
    }

    /**
     * Loads crawl profiles from a DB file.
     *
     * @param file DB file
     * @return crawl profile data
     */
    private static MapHeap loadFromDB(final File file) {
        MapHeap ret;
        try {
            ret = new MapHeap(file, Word.commonHashLength, NaturalOrder.naturalOrder, 1024 * 64, 500, ' ');
        } catch (final IOException e ) {
            ConcurrentLog.logException(e);
            ConcurrentLog.logException(e);
            FileUtils.deletedelete(file);
            try {
                ret =
                    new MapHeap(file, Word.commonHashLength, NaturalOrder.naturalOrder, 1024 * 64, 500, ' ');
            } catch (final IOException e1 ) {
                ConcurrentLog.logException(e1);
                ret = null;
            }
        }
        return ret;
    }

}
