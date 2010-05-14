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

package de.anomic.crawler;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.kelondroException;

public final class CrawlSwitchboard {

    public static final String CRAWL_PROFILE_PROXY                 = "proxy";
    public static final String CRAWL_PROFILE_REMOTE                = "remote";
    public static final String CRAWL_PROFILE_SNIPPET_LOCAL_TEXT    = "snippetLocalText";
    public static final String CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT   = "snippetGlobalText";
    public static final String CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA   = "snippetLocalMedia";
    public static final String CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA  = "snippetGlobalMedia";
    public static final String CRAWL_PROFILE_SURROGATE             = "surrogates";
    
    public static final String DBFILE_ACTIVE_CRAWL_PROFILES        = "crawlProfilesActive.heap";
    public static final String DBFILE_PASSIVE_CRAWL_PROFILES       = "crawlProfilesPassive.heap";
    
    public static final long CRAWL_PROFILE_PROXY_RECRAWL_CYCLE = 60L * 24L;
    public static final long CRAWL_PROFILE_SNIPPET_LOCAL_TEXT_RECRAWL_CYCLE = 60L * 24L * 30L;
    public static final long CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT_RECRAWL_CYCLE = 60L * 24L * 30L;
    public static final long CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA_RECRAWL_CYCLE = 60L * 24L * 30L;
    public static final long CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA_RECRAWL_CYCLE = 60L * 24L * 30L;
    public static final long CRAWL_PROFILE_SURROGATE_RECRAWL_CYCLE = 60L * 24L * 30L;
    
    private final Log             log;
    public        CrawlProfile    profilesActiveCrawls, profilesPassiveCrawls;
    public  CrawlProfile.entry    defaultProxyProfile;
    public  CrawlProfile.entry    defaultRemoteProfile;
    public  CrawlProfile.entry    defaultTextSnippetLocalProfile, defaultTextSnippetGlobalProfile;
    public  CrawlProfile.entry    defaultMediaSnippetLocalProfile, defaultMediaSnippetGlobalProfile;
    public  CrawlProfile.entry    defaultSurrogateProfile;
    private final File            queuesRoot;
    
    public CrawlSwitchboard(
            final String networkName,
            final Log log,
            final File queuesRoot) {
        
        log.logInfo("Initializing Word Index for the network '" + networkName + "'.");
                        
        if (networkName == null || networkName.length() == 0) {
            log.logSevere("no network name given - shutting down");
            System.exit(0);
        }
        this.log = log;
        
        // make crawl profiles database and default profiles
        this.queuesRoot = queuesRoot;
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
            Log.logException(e);Log.logException(e);
            FileUtils.deletedelete(profilesActiveFile);
            try {
                this.profilesActiveCrawls = new CrawlProfile(profilesActiveFile);
            } catch (IOException e1) {
                Log.logException(e1);
                this.profilesActiveCrawls = null;
            }
        }
        initActiveCrawlProfiles();
        log.logInfo("Loaded active crawl profiles from file " + profilesActiveFile.getName() + ", " + this.profilesActiveCrawls.size() + " entries");
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
                Log.logException(e1);
                this.profilesPassiveCrawls = null;
            }
        }
        log.logInfo("Loaded passive crawl profiles from file " + profilesPassiveFile.getName() +
                ", " + this.profilesPassiveCrawls.size() + " entries" +
                ", " + profilesPassiveFile.length()/1024);
    }
    
    public void clear() {
    }
    
    private void initActiveCrawlProfiles() {
        this.defaultProxyProfile = null;
        this.defaultRemoteProfile = null;
        this.defaultTextSnippetLocalProfile = null;
        this.defaultTextSnippetGlobalProfile = null;
        this.defaultMediaSnippetLocalProfile = null;
        this.defaultMediaSnippetGlobalProfile = null;
        this.defaultSurrogateProfile = null;
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
                if (name.equals(CRAWL_PROFILE_SURROGATE)) this.defaultSurrogateProfile = profile;
            }
        } catch (final Exception e) {
            this.profilesActiveCrawls.clear();
            this.defaultProxyProfile = null;
            this.defaultRemoteProfile = null;
            this.defaultTextSnippetLocalProfile = null;
            this.defaultTextSnippetGlobalProfile = null;
            this.defaultMediaSnippetLocalProfile = null;
            this.defaultMediaSnippetGlobalProfile = null;
            this.defaultSurrogateProfile = null;
        }
        
        if (this.defaultProxyProfile == null) {
            // generate new default entry for proxy crawling
            this.defaultProxyProfile = this.profilesActiveCrawls.newEntry("proxy", null, CrawlProfile.MATCH_ALL, CrawlProfile.MATCH_BAD_URL,
                    0 /*Integer.parseInt(getConfig(PROXY_PREFETCH_DEPTH, "0"))*/,
                    this.profilesActiveCrawls.getRecrawlDate(CRAWL_PROFILE_PROXY_RECRAWL_CYCLE), -1, -1, false,
                    true /*getConfigBool(PROXY_INDEXING_LOCAL_TEXT, true)*/,
                    true /*getConfigBool(PROXY_INDEXING_LOCAL_MEDIA, true)*/,
                    true, true,
                    false /*getConfigBool(PROXY_INDEXING_REMOTE, false)*/, true, true, true,
                    CrawlProfile.CacheStrategy.IFFRESH);
        }
        if (this.defaultRemoteProfile == null) {
            // generate new default entry for remote crawling
            defaultRemoteProfile = this.profilesActiveCrawls.newEntry(CRAWL_PROFILE_REMOTE, null, CrawlProfile.MATCH_ALL, CrawlProfile.MATCH_BAD_URL, 0,
                    -1, -1, -1, true, true, true, false, true, false, true, true, false, CrawlProfile.CacheStrategy.IFFRESH);
        }
        if (this.defaultTextSnippetLocalProfile == null) {
            // generate new default entry for snippet fetch and optional crawling
            defaultTextSnippetLocalProfile = this.profilesActiveCrawls.newEntry(CRAWL_PROFILE_SNIPPET_LOCAL_TEXT, null, CrawlProfile.MATCH_ALL, CrawlProfile.MATCH_BAD_URL, 0,
                    this.profilesActiveCrawls.getRecrawlDate(CRAWL_PROFILE_SNIPPET_LOCAL_TEXT_RECRAWL_CYCLE), -1, -1, true, false, false, true, true, false, true, true, false, CrawlProfile.CacheStrategy.IFFRESH);
        }
        if (this.defaultTextSnippetGlobalProfile == null) {
            // generate new default entry for snippet fetch and optional crawling
            defaultTextSnippetGlobalProfile = this.profilesActiveCrawls.newEntry(CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT, null, CrawlProfile.MATCH_ALL, CrawlProfile.MATCH_BAD_URL, 0,
                    this.profilesActiveCrawls.getRecrawlDate(CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT_RECRAWL_CYCLE), -1, -1, true, true, true, true, true, false, true, true, false, CrawlProfile.CacheStrategy.CACHEONLY);
        }
        if (this.defaultMediaSnippetLocalProfile == null) {
            // generate new default entry for snippet fetch and optional crawling
            defaultMediaSnippetLocalProfile = this.profilesActiveCrawls.newEntry(CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA, null, CrawlProfile.MATCH_ALL, CrawlProfile.MATCH_BAD_URL, 0,
                    this.profilesActiveCrawls.getRecrawlDate(CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA_RECRAWL_CYCLE), -1, -1, true, false, false, false, false, false, true, true, false, CrawlProfile.CacheStrategy.IFEXIST);
        }
        if (this.defaultMediaSnippetGlobalProfile == null) {
            // generate new default entry for snippet fetch and optional crawling
            defaultMediaSnippetGlobalProfile = this.profilesActiveCrawls.newEntry(CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA, null, CrawlProfile.MATCH_ALL, CrawlProfile.MATCH_BAD_URL, 0,
                    this.profilesActiveCrawls.getRecrawlDate(CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA_RECRAWL_CYCLE), -1, -1, true, false, true, true, true, false, true, true, false, CrawlProfile.CacheStrategy.IFEXIST);
        }
        if (this.defaultSurrogateProfile == null) {
            // generate new default entry for surrogate parsing
            defaultSurrogateProfile = this.profilesActiveCrawls.newEntry(CRAWL_PROFILE_SURROGATE, null, CrawlProfile.MATCH_ALL, CrawlProfile.MATCH_BAD_URL, 0,
                    this.profilesActiveCrawls.getRecrawlDate(CRAWL_PROFILE_SURROGATE_RECRAWL_CYCLE), -1, -1, true, true, false, false, false, false, true, true, false, CrawlProfile.CacheStrategy.NOCACHE);
        }
    }
    
    private void resetProfiles() {
        final File pdb = new File(this.queuesRoot, DBFILE_ACTIVE_CRAWL_PROFILES);
        if (pdb.exists()) FileUtils.deletedelete(pdb);
        try {
            profilesActiveCrawls = new CrawlProfile(pdb);
        } catch (IOException e) {
            Log.logException(e);
        }
        initActiveCrawlProfiles();
    }
    
    public boolean cleanProfiles() throws InterruptedException {
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
                      (entry.name().equals(CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA)) ||
                      (entry.name().equals(CRAWL_PROFILE_SURROGATE)))) {
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

    
    public void close() {
        this.profilesActiveCrawls.close();
        this.profilesPassiveCrawls.close();
    }

}
