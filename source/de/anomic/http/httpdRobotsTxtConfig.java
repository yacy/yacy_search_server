// httpdRobotsTxtConfig.java 
// ---------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2007
// Created 22.02.2007
//
// This file is contributed by Franz Brauße
//
// $LastChangedDate: $
// $LastChangedRevision: $
// $LastChangedBy: $
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

package de.anomic.http;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverSwitch;

public final class httpdRobotsTxtConfig {
    
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
    
    public httpdRobotsTxtConfig() {  }
    
    public httpdRobotsTxtConfig(String[] active) {
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
    
    public static httpdRobotsTxtConfig init(serverSwitch env) {
        String cfg = env.getConfig(plasmaSwitchboard.ROBOTS_TXT, plasmaSwitchboard.ROBOTS_TXT_DEFAULT);
        if (cfg == null) return new httpdRobotsTxtConfig();
        return new httpdRobotsTxtConfig(cfg.split(","));
    }
    
    public String toString() {
        if (this.allDisallowed) return ALL;
        StringBuffer sb = new StringBuffer();
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

    public void setAllDisallowed(boolean allDisallowed) {
        this.allDisallowed = allDisallowed;
    }

    public boolean isLockedDisallowed() {
        return lockedDisallowed || this.allDisallowed;
    }

    public void setLockedDisallowed(boolean lockedDisallowed) {
        this.lockedDisallowed = lockedDisallowed;
    }

    public boolean isDirsDisallowed() {
        return dirsDisallowed || this.allDisallowed;
    }

    public void setDirsDisallowed(boolean dirsDisallowed) {
        this.dirsDisallowed = dirsDisallowed;
    }

    public boolean isBlogDisallowed() {
        return blogDisallowed || this.allDisallowed;
    }

    public void setBlogDisallowed(boolean blogDisallowed) {
        this.blogDisallowed = blogDisallowed;
    }

    public boolean isBookmarksDisallowed() {
        return bookmarksDisallowed || this.allDisallowed;
    }

    public void setBookmarksDisallowed(boolean bookmarksDisallowed) {
        this.bookmarksDisallowed = bookmarksDisallowed;
    }

    public boolean isFileshareDisallowed() {
        return fileshareDisallowed || this.allDisallowed;
    }

    public void setFileshareDisallowed(boolean fileshareDisallowed) {
        this.fileshareDisallowed = fileshareDisallowed;
    }

    public boolean isHomepageDisallowed() {
        return homepageDisallowed || this.allDisallowed;
    }

    public void setHomepageDisallowed(boolean homepageDisallowed) {
        this.homepageDisallowed = homepageDisallowed;
    }

    public boolean isNetworkDisallowed() {
        return networkDisallowed || this.allDisallowed;
    }

    public void setNetworkDisallowed(boolean networkDisallowed) {
        this.networkDisallowed = networkDisallowed;
    }

    public boolean isNewsDisallowed() {
        return newsDisallowed || this.allDisallowed;
    }

    public void setNewsDisallowed(boolean newsDisallowed) {
        this.newsDisallowed = newsDisallowed;
    }

    public boolean isStatusDisallowed() {
        return statusDisallowed || this.allDisallowed;
    }

    public void setStatusDisallowed(boolean statusDisallowed) {
        this.statusDisallowed = statusDisallowed;
    }

    public boolean isSurftipsDisallowed() {
        return surftipsDisallowed || this.allDisallowed;
    }

    public void setSurftipsDisallowed(boolean surftipsDisallowed) {
        this.surftipsDisallowed = surftipsDisallowed;
    }

    public boolean isWikiDisallowed() {
        return wikiDisallowed || this.allDisallowed;
    }

    public void setWikiDisallowed(boolean wikiDisallowed) {
        this.wikiDisallowed = wikiDisallowed;
    }
    
    public boolean isProfileDisallowed() {
        return profileDisallowed || this.allDisallowed;
    }
    
    public void setProfileDisallowed(boolean profileDisallowed) {
        this.profileDisallowed = profileDisallowed;
    }
}
