// ConfigRobotsTxt_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Franz Brausze
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

// You must compile this file with
// javac -classpath .:../classes ConfigRobotsTxt_p.java
// if the shell's current path is HTROOT

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;
import net.yacy.server.http.RobotsTxtConfig;

public class ConfigRobotsTxt_p {

    public static servletProperties respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final servletProperties prop = new servletProperties();

        final RobotsTxtConfig rbc = ((Switchboard)env).robotstxtConfig;
        prop.put("clientname", sb.peers.mySeed().getPublicAddress());

        if (post != null) {
            if (post.containsKey("save")) {
                rbc.setAllDisallowed(post.containsKey(RobotsTxtConfig.ALL));
                rbc.setBlogDisallowed(post.containsKey(RobotsTxtConfig.BLOG));
                rbc.setBookmarksDisallowed(post.containsKey(RobotsTxtConfig.BOOKMARKS));
                rbc.setDirsDisallowed(post.containsKey(RobotsTxtConfig.DIRS));
                rbc.setFileshareDisallowed(post.containsKey(RobotsTxtConfig.FILESHARE));
                rbc.setHomepageDisallowed(post.containsKey(RobotsTxtConfig.HOMEPAGE));
                rbc.setLockedDisallowed(post.containsKey(RobotsTxtConfig.LOCKED));
                rbc.setNetworkDisallowed(post.containsKey(RobotsTxtConfig.NETWORK));
                rbc.setNewsDisallowed(post.containsKey(RobotsTxtConfig.NEWS));
                rbc.setStatusDisallowed(post.containsKey(RobotsTxtConfig.STATUS));
                rbc.setSurftipsDisallowed(post.containsKey(RobotsTxtConfig.SURFTIPS));
                rbc.setWikiDisallowed(post.containsKey(RobotsTxtConfig.WIKI));
                rbc.setProfileDisallowed(post.containsKey(RobotsTxtConfig.PROFILE));
                env.setConfig(SwitchboardConstants.ROBOTS_TXT, rbc.toString());
            }
        }

        prop.put(RobotsTxtConfig.ALL + ".checked", (rbc.isAllDisallowed()) ? "1" : "0");
        prop.put(RobotsTxtConfig.BLOG + ".checked", (rbc.isBlogDisallowed()) ? "1" : "0");
        prop.put(RobotsTxtConfig.BOOKMARKS + ".checked", (rbc.isBookmarksDisallowed()) ? "1" : "0");
        prop.put(RobotsTxtConfig.DIRS + ".checked", (rbc.isDirsDisallowed()) ? "1" : "0");
        prop.put(RobotsTxtConfig.FILESHARE + ".checked", (rbc.isFileshareDisallowed()) ? "1" : "0");
        prop.put(RobotsTxtConfig.HOMEPAGE + ".checked", (rbc.isHomepageDisallowed()) ? "1" : "0");
        prop.put(RobotsTxtConfig.LOCKED + ".checked", (rbc.isLockedDisallowed()) ? "1" : "0");
        prop.put(RobotsTxtConfig.NETWORK + ".checked", (rbc.isNetworkDisallowed()) ? "1" : "0");
        prop.put(RobotsTxtConfig.NEWS + ".checked", (rbc.isNewsDisallowed()) ? "1" : "0");
        prop.put(RobotsTxtConfig.STATUS + ".checked", (rbc.isStatusDisallowed()) ? "1" : "0");
        prop.put(RobotsTxtConfig.SURFTIPS + ".checked", (rbc.isSurftipsDisallowed()) ? "1" : "0");
        prop.put(RobotsTxtConfig.WIKI + ".checked", (rbc.isWikiDisallowed()) ? "1" : "0");
        prop.put(RobotsTxtConfig.PROFILE + ".checked", (rbc.isProfileDisallowed()) ? "1" : "0");
        return prop;
    }
}
