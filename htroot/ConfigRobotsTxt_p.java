// ConfigRobotsTxt_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Franz Brausze
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

// You must compile this file with
// javac -classpath .:../classes ConfigRobotsTxt_p.java
// if the shell's current path is HTROOT

import de.anomic.http.httpHeader;
import de.anomic.http.httpdRobotsTxtConfig;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;

public class ConfigRobotsTxt_p {
    
    public static servletProperties respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final servletProperties prop = new servletProperties();
        
        final httpdRobotsTxtConfig rbc = ((plasmaSwitchboard)env).robotstxtConfig;
        prop.put("clientname", sb.webIndex.seedDB.mySeed().getPublicAddress());
        
        if (post != null) {
            if (post.containsKey("save")) {
                rbc.setAllDisallowed(post.containsKey(httpdRobotsTxtConfig.ALL));
                rbc.setBlogDisallowed(post.containsKey(httpdRobotsTxtConfig.BLOG));
                rbc.setBookmarksDisallowed(post.containsKey(httpdRobotsTxtConfig.BOOKMARKS));
                rbc.setDirsDisallowed(post.containsKey(httpdRobotsTxtConfig.DIRS));
                rbc.setFileshareDisallowed(post.containsKey(httpdRobotsTxtConfig.FILESHARE));
                rbc.setHomepageDisallowed(post.containsKey(httpdRobotsTxtConfig.HOMEPAGE));
                rbc.setLockedDisallowed(post.containsKey(httpdRobotsTxtConfig.LOCKED));
                rbc.setNetworkDisallowed(post.containsKey(httpdRobotsTxtConfig.NETWORK));
                rbc.setNewsDisallowed(post.containsKey(httpdRobotsTxtConfig.NEWS));
                rbc.setStatusDisallowed(post.containsKey(httpdRobotsTxtConfig.STATUS));
                rbc.setSurftipsDisallowed(post.containsKey(httpdRobotsTxtConfig.SURFTIPS));
                rbc.setWikiDisallowed(post.containsKey(httpdRobotsTxtConfig.WIKI));
                rbc.setProfileDisallowed(post.containsKey(httpdRobotsTxtConfig.PROFILE));
                env.setConfig(plasmaSwitchboard.ROBOTS_TXT, rbc.toString());
            }
        }
        
        prop.put(httpdRobotsTxtConfig.ALL + ".checked", (rbc.isAllDisallowed()) ? "1" : "0");
        prop.put(httpdRobotsTxtConfig.BLOG + ".checked", (rbc.isBlogDisallowed()) ? "1" : "0");
        prop.put(httpdRobotsTxtConfig.BOOKMARKS + ".checked", (rbc.isBookmarksDisallowed()) ? "1" : "0");
        prop.put(httpdRobotsTxtConfig.DIRS + ".checked", (rbc.isDirsDisallowed()) ? "1" : "0");
        prop.put(httpdRobotsTxtConfig.FILESHARE + ".checked", (rbc.isFileshareDisallowed()) ? "1" : "0");
        prop.put(httpdRobotsTxtConfig.HOMEPAGE + ".checked", (rbc.isHomepageDisallowed()) ? "1" : "0");
        prop.put(httpdRobotsTxtConfig.LOCKED + ".checked", (rbc.isLockedDisallowed()) ? "1" : "0");
        prop.put(httpdRobotsTxtConfig.NETWORK + ".checked", (rbc.isNetworkDisallowed()) ? "1" : "0");
        prop.put(httpdRobotsTxtConfig.NEWS + ".checked", (rbc.isNewsDisallowed()) ? "1" : "0");
        prop.put(httpdRobotsTxtConfig.STATUS + ".checked", (rbc.isStatusDisallowed()) ? "1" : "0");
        prop.put(httpdRobotsTxtConfig.SURFTIPS + ".checked", (rbc.isSurftipsDisallowed()) ? "1" : "0");
        prop.put(httpdRobotsTxtConfig.WIKI + ".checked", (rbc.isWikiDisallowed()) ? "1" : "0");
        prop.put(httpdRobotsTxtConfig.PROFILE + ".checked", (rbc.isProfileDisallowed()) ? "1" : "0");
        return prop;
    }
}
