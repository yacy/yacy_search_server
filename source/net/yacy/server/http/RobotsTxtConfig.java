// RobotsTxtConfig.java 
// ---------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2007
// Created 22.02.2007
//
// This file is contributed by Franz Brausze
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

package net.yacy.server.http;

import net.yacy.cora.util.CommonPattern;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverSwitch;

public final class RobotsTxtConfig {
    
    public static final String WIKI = "wiki";
    public static final String BLOG = "blog";
    public static final String BOOKMARKS = "bookmarks";
    public static final String HOMEPAGE = "homepage";
    public static final String FILESHARE = "fileshare";
    public static final String SURFTIPS = "surftips";
    public static final String NEWS = "news";
    public static final String STATUS = "status";
    public static final String LOCKED = "locked";
    public static final String DIRS = "dirs";
    public static final String NETWORK = "network";
    public static final String PROFILE = "profile";
    public static final String ALL = "all";
    
    private boolean allDisallowed = false;
    private boolean lockedDisallowed = true;
    private boolean dirsDisallowed = true;
    private boolean wikiDisallowed = false;
    private boolean blogDisallowed = false;
    private boolean fileshareDisallowed = false;
    private boolean homepageDisallowed = false;
    private boolean newsDisallowed = false;
    private boolean statusDisallowed = false;
    private boolean networkDisallowed = false;
    private boolean surftipsDisallowed = false;
    private boolean bookmarksDisallowed = false;
    private boolean profileDisallowed = true;
    
    public RobotsTxtConfig() {  }
    
    public RobotsTxtConfig(final String[] active) {
        if (active == null) return;
        for (int i=0; i<active.length; i++) {
            if (active[i] == null) continue;
            if (active[i].equals(BLOG)) { this.blogDisallowed = true; continue; }
            if (active[i].equals(WIKI)) { this.wikiDisallowed = true; continue; }
            if (active[i].equals(BOOKMARKS)) { this.bookmarksDisallowed = true; continue; }
            if (active[i].equals(HOMEPAGE)) { this.homepageDisallowed = true; continue; }
            if (active[i].equals(FILESHARE)) { this.fileshareDisallowed = true; continue; }
            if (active[i].equals(SURFTIPS)) { this.surftipsDisallowed = true; continue; }
            if (active[i].equals(NEWS)) { this.newsDisallowed = true; continue; }
            if (active[i].equals(STATUS)) { this.statusDisallowed = true; continue; }
            if (active[i].equals(NETWORK)) { this.networkDisallowed = true; continue; }
            if (active[i].equals(LOCKED)) { this.lockedDisallowed = true; continue; }
            if (active[i].equals(DIRS)) { this.dirsDisallowed = true; continue; }
            if (active[i].equals(PROFILE)) { this.profileDisallowed = true; continue; }
            if (active[i].equals(ALL)) { this.allDisallowed = true; continue; }
        }
    }
    
    public static RobotsTxtConfig init(final serverSwitch env) {
        final String cfg = env.getConfig(SwitchboardConstants.ROBOTS_TXT, SwitchboardConstants.ROBOTS_TXT_DEFAULT);
        if (cfg == null) return new RobotsTxtConfig();
        return new RobotsTxtConfig(CommonPattern.COMMA.split(cfg));
    }
    
    @Override
    public String toString() {
        if (this.allDisallowed) return ALL;
        final StringBuilder sb = new StringBuilder(200);
        if (this.blogDisallowed) sb.append(BLOG).append(",");
        if (this.bookmarksDisallowed) sb.append(BOOKMARKS).append(",");
        if (this.dirsDisallowed) sb.append(DIRS).append(",");
        if (this.fileshareDisallowed) sb.append(FILESHARE).append(",");
        if (this.homepageDisallowed) sb.append(HOMEPAGE).append(",");
        if (this.lockedDisallowed) sb.append(LOCKED).append(",");
        if (this.networkDisallowed) sb.append(NETWORK).append(",");
        if (this.newsDisallowed) sb.append(NEWS).append(",");
        if (this.statusDisallowed) sb.append(STATUS).append(",");
        if (this.surftipsDisallowed) sb.append(SURFTIPS).append(",");
        if (this.wikiDisallowed) sb.append(WIKI).append(",");
        if (this.profileDisallowed) sb.append(PROFILE).append(",");
        return sb.toString();
    }
    
    public boolean isAllDisallowed() {
        return allDisallowed;
    }

    public void setAllDisallowed(final boolean allDisallowed) {
        this.allDisallowed = allDisallowed;
    }

    public boolean isLockedDisallowed() {
        return lockedDisallowed || this.allDisallowed;
    }

    public void setLockedDisallowed(final boolean lockedDisallowed) {
        this.lockedDisallowed = lockedDisallowed;
    }

    public boolean isDirsDisallowed() {
        return dirsDisallowed || this.allDisallowed;
    }

    public void setDirsDisallowed(final boolean dirsDisallowed) {
        this.dirsDisallowed = dirsDisallowed;
    }

    public boolean isBlogDisallowed() {
        return blogDisallowed || this.allDisallowed;
    }

    public void setBlogDisallowed(final boolean blogDisallowed) {
        this.blogDisallowed = blogDisallowed;
    }

    public boolean isBookmarksDisallowed() {
        return bookmarksDisallowed || this.allDisallowed;
    }

    public void setBookmarksDisallowed(final boolean bookmarksDisallowed) {
        this.bookmarksDisallowed = bookmarksDisallowed;
    }

    public boolean isFileshareDisallowed() {
        return fileshareDisallowed || this.allDisallowed;
    }

    public void setFileshareDisallowed(final boolean fileshareDisallowed) {
        this.fileshareDisallowed = fileshareDisallowed;
    }

    public boolean isHomepageDisallowed() {
        return homepageDisallowed || this.allDisallowed;
    }

    public void setHomepageDisallowed(final boolean homepageDisallowed) {
        this.homepageDisallowed = homepageDisallowed;
    }

    public boolean isNetworkDisallowed() {
        return networkDisallowed || this.allDisallowed;
    }

    public void setNetworkDisallowed(final boolean networkDisallowed) {
        this.networkDisallowed = networkDisallowed;
    }

    public boolean isNewsDisallowed() {
        return newsDisallowed || this.allDisallowed;
    }

    public void setNewsDisallowed(final boolean newsDisallowed) {
        this.newsDisallowed = newsDisallowed;
    }

    public boolean isStatusDisallowed() {
        return statusDisallowed || this.allDisallowed;
    }

    public void setStatusDisallowed(final boolean statusDisallowed) {
        this.statusDisallowed = statusDisallowed;
    }

    public boolean isSurftipsDisallowed() {
        return surftipsDisallowed || this.allDisallowed;
    }

    public void setSurftipsDisallowed(final boolean surftipsDisallowed) {
        this.surftipsDisallowed = surftipsDisallowed;
    }

    public boolean isWikiDisallowed() {
        return wikiDisallowed || this.allDisallowed;
    }

    public void setWikiDisallowed(final boolean wikiDisallowed) {
        this.wikiDisallowed = wikiDisallowed;
    }
    
    public boolean isProfileDisallowed() {
        return profileDisallowed || this.allDisallowed;
    }
    
    public void setProfileDisallowed(final boolean profileDisallowed) {
        this.profileDisallowed = profileDisallowed;
    }
}
