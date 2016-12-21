// ConfigSearchPage_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 4.7.2008
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Date;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.WorkTables;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.navigator.Navigator;
import net.yacy.search.navigator.NavigatorPlugins;
import net.yacy.search.query.QueryParams;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ConfigSearchPage_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        if (post != null) {
            // AUTHENTICATE
            if (!sb.verifyAuthentication(header)) {
                // force log-in
            	prop.authenticationRequired();
                return prop;
            }
  
            if (post.containsKey("searchpage_set")) {
                final String newGreeting = post.get(SwitchboardConstants.GREETING, "");
                // store this call as api call
                sb.tables.recordAPICall(post, "ConfigPortal.html", WorkTables.TABLE_API_TYPE_CONFIGURATION, "new portal design. greeting: " + newGreeting);

                sb.setConfig("publicTopmenu", post.getBoolean("publicTopmenu"));
                sb.setConfig("search.options", post.getBoolean("search.options"));

                sb.setConfig("search.text", post.getBoolean("search.text"));
                sb.setConfig("search.image", post.getBoolean("search.image"));
                sb.setConfig("search.audio", post.getBoolean("search.audio"));
                sb.setConfig("search.video", post.getBoolean("search.video"));
                sb.setConfig("search.app", post.getBoolean("search.app"));

                sb.setConfig("search.result.show.date", post.getBoolean("search.result.show.date"));
                sb.setConfig("search.result.show.size", post.getBoolean("search.result.show.size"));
                sb.setConfig("search.result.show.metadata", post.getBoolean("search.result.show.metadata"));
                sb.setConfig("search.result.show.parser", post.getBoolean("search.result.show.parser"));
                sb.setConfig("search.result.show.citation", post.getBoolean("search.result.show.citation"));
                sb.setConfig("search.result.show.pictures", post.getBoolean("search.result.show.pictures"));
                sb.setConfig("search.result.show.cache", post.getBoolean("search.result.show.cache"));
                sb.setConfig("search.result.show.proxy", post.getBoolean("search.result.show.proxy"));
                sb.setConfig("search.result.show.hostbrowser", post.getBoolean("search.result.show.hostbrowser"));
                sb.setConfig("search.result.show.snapshots", post.getBoolean("search.result.show.snapshots"));

                // construct navigation String
                String nav = "";
                if (post.getBoolean("search.navigation.location")) nav += "location,";
                // if (post.getBoolean("search.navigation.filetype")) nav += "filetype,";
                if (post.getBoolean("search.navigation.protocol")) nav += "protocol,";
                if (post.getBoolean("search.navigation.hosts")) nav += "hosts,";
                if (post.getBoolean("search.navigation.language")) nav += "language,";
                // if (post.getBoolean("search.navigation.authors")) nav += "authors,";
                // if (post.getBoolean("search.navigation.collections")) nav += "collections,";
                // if (post.getBoolean("search.navigation.namespace")) nav += "namespace,";
                if (post.getBoolean("search.navigation.topics")) nav += "topics,";
                if (post.getBoolean("search.navigation.date")) nav += "date,";
                // append active navigator plugins
                String[] navplugins = post.getAll("search.navigation.active");
                for (String navname:navplugins) {
                    nav += navname + ",";
                }
                if (nav.endsWith(",")) nav = nav.substring(0, nav.length() - 1);
                sb.setConfig("search.navigation", nav);
                // maxcount nav entries, default
                int navmaxcnt = post.getInt("search.navigation.maxcount", QueryParams.FACETS_STANDARD_MAXCOUNT);
                if (navmaxcnt > 5) {
                    sb.setConfig(SwitchboardConstants.SEARCH_NAVIGATION_MAXCOUNT, navmaxcnt);
                    if (navmaxcnt != QueryParams.FACETS_STANDARD_MAXCOUNT) QueryParams.FACETS_STANDARD_MAXCOUNT = navmaxcnt;
                }
            }

            if (post.containsKey("add.nav")) { // button: add navigator plugin to ative list
                String navname = post.get("search.navigation.navname");
                if (navname != null && !navname.isEmpty()) {
                    String naviconf = sb.getConfig("search.navigation", "");
                    naviconf += "," + navname;
                    sb.setConfig("search.navigation", naviconf);
                }
            } else if (post.containsKey("del.nav")) { // button: delete navigator plugin from active list
                String navname = post.get("del.nav");
                String naviconf = sb.getConfig("search.navigation", "");
                naviconf = naviconf.replace(navname, "");
                naviconf = naviconf.replace(",,", ",");
                sb.setConfig("search.navigation", naviconf);
            }

            if (post.containsKey("searchpage_default")) {
                // load defaults from defaults/yacy.init file
                final Properties config = new Properties();
                final String mes = "ConfigSearchPage_p";
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(new File(sb.appPath, "defaults/yacy.init"));
                    config.load(fis);
                } catch (final FileNotFoundException e) {
                    ConcurrentLog.severe(mes, "could not find configuration file.");
                    return prop;
                } catch (final IOException e) {
                    ConcurrentLog.severe(mes, "could not read configuration file.");
                    return prop;
                } finally {
                    if (fis != null) {
                        try {
                            fis.close();
                        } catch (final IOException e) {
                            ConcurrentLog.logException(e);
                        }
                    }
                }
                sb.setConfig("publicTopmenu", config.getProperty("publicTopmenu","true"));
                sb.setConfig("search.navigation", config.getProperty("search.navigation","hosts,authors,namespace,topics"));
                sb.setConfig("search.options", config.getProperty("search.options","true"));
                sb.setConfig("search.text", config.getProperty("search.text","true"));
                sb.setConfig("search.image", config.getProperty("search.image","true"));
                sb.setConfig("search.audio", config.getProperty("search.audio","false"));
                sb.setConfig("search.video", config.getProperty("search.video","false"));
                sb.setConfig("search.app", config.getProperty("search.app","false"));
                sb.setConfig("search.result.show.date", config.getProperty("search.result.show.date","true"));
                sb.setConfig("search.result.show.size", config.getProperty("search.result.show.size","false"));
                sb.setConfig("search.result.show.metadata", config.getProperty("search.result.show.metadata","false"));
                sb.setConfig("search.result.show.parser", config.getProperty("search.result.show.parser","false"));
                sb.setConfig("search.result.show.citation", config.getProperty("search.result.show.citation","false"));
                sb.setConfig("search.result.show.pictures", config.getProperty("search.result.show.pictures","false"));
                sb.setConfig("search.result.show.cache", config.getProperty("search.result.show.cache","true"));
                sb.setConfig("search.result.show.proxy", config.getProperty("search.result.show.proxy","false"));
                sb.setConfig("search.result.show.hostbrowser", config.getProperty("search.result.show.hostbrowser","true"));
                sb.setConfig("search.result.show.snapshots", config.getProperty("search.result.show.snapshots","true"));
            }
        }

        prop.putHTML(SwitchboardConstants.GREETING, sb.getConfig(SwitchboardConstants.GREETING, ""));
        prop.putHTML(SwitchboardConstants.GREETING_HOMEPAGE, sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
        prop.putHTML(SwitchboardConstants.GREETING_LARGE_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, ""));
        prop.putHTML(SwitchboardConstants.GREETING_SMALL_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));
        prop.putHTML(SwitchboardConstants.GREETING_IMAGE_ALT, sb.getConfig(SwitchboardConstants.GREETING_IMAGE_ALT, ""));
        prop.putHTML(SwitchboardConstants.INDEX_FORWARD, sb.getConfig(SwitchboardConstants.INDEX_FORWARD, ""));
        prop.put("publicTopmenu", sb.getConfigBool("publicTopmenu", false) ? 1 : 0);
        prop.put("search.options", sb.getConfigBool("search.options", false) ? 1 : 0);

        prop.put("search.text", sb.getConfigBool("search.text", false) ? 1 : 0);
        prop.put("search.image", sb.getConfigBool("search.image", false) ? 1 : 0);
        prop.put("search.audio", sb.getConfigBool("search.audio", false) ? 1 : 0);
        prop.put("search.video", sb.getConfigBool("search.video", false) ? 1 : 0);
        prop.put("search.app", sb.getConfigBool("search.app", false) ? 1 : 0);

        prop.put("search.result.show.date", sb.getConfigBool("search.result.show.date", false) ? 1 : 0);
        prop.put("search.result.show.size", sb.getConfigBool("search.result.show.size", false) ? 1 : 0);
        prop.put("search.result.show.metadata", sb.getConfigBool("search.result.show.metadata", false) ? 1 : 0);
        prop.put("search.result.show.parser", sb.getConfigBool("search.result.show.parser", false) ? 1 : 0);
        prop.put("search.result.show.citation", sb.getConfigBool("search.result.show.citation", false) ? 1 : 0);
        prop.put("search.result.show.pictures", sb.getConfigBool("search.result.show.pictures", false) ? 1 : 0);
        prop.put("search.result.show.cache", sb.getConfigBool("search.result.show.cache", false) ? 1 : 0);
        prop.put("search.result.show.proxy", sb.getConfigBool("search.result.show.proxy", false) ? 1 : 0);
        prop.put("search.result.show.hostbrowser", sb.getConfigBool("search.result.show.hostbrowser", false) ? 1 : 0);
        prop.put("search.result.show.snapshots", sb.getConfigBool("search.result.show.snapshots", false) ? 1 : 0);

        prop.put("search.navigation.location", sb.getConfig("search.navigation", "").indexOf("location",0) >= 0 ? 1 : 0);
        // prop.put("search.navigation.filetype", sb.getConfig("search.navigation", "").indexOf("filetype",0) >= 0 ? 1 : 0);
        prop.put("search.navigation.protocol", sb.getConfig("search.navigation", "").indexOf("protocol",0) >= 0 ? 1 : 0);
        prop.put("search.navigation.hosts", sb.getConfig("search.navigation", "").indexOf("hosts",0) >= 0 ? 1 : 0);
        prop.put("search.navigation.language", sb.getConfig("search.navigation", "").indexOf("language",0) >= 0 ? 1 : 0);
        // prop.put("search.navigation.authors", sb.getConfig("search.navigation", "").indexOf("authors",0) >= 0 ? 1 : 0);
        // prop.put("search.navigation.collections", sb.getConfig("search.navigation", "").indexOf("collections",0) >= 0 ? 1 : 0);
        // prop.put("search.navigation.namespace", sb.getConfig("search.navigation", "").indexOf("namespace",0) >= 0 ? 1 : 0);
        prop.put("search.navigation.topics", sb.getConfig("search.navigation", "").indexOf("topics",0) >= 0 ? 1 : 0);
        prop.put("search.navigation.date", sb.getConfig("search.navigation", "").indexOf("date",0) >= 0 ? 1 : 0);
        // list active navigator plugins
        String naviconf = sb.getConfig("search.navigation", "");
        Map<String, Navigator> navplugins = NavigatorPlugins.initFromCfgString(naviconf);
        int i = 0;
        for (String navname:navplugins.keySet()) {
            Navigator nav = navplugins.get(navname);
            prop.put("search.navigation.plugin_" + i + "_name", navname);
            prop.put("search.navigation.plugin_" + i + "_displayname", nav.getDisplayName());
            i++;
        }
        prop.put("search.navigation.plugin", i);

        // fill select field options (only navs not already active)
        Map<String, String> defaultnavplugins = NavigatorPlugins.listAvailable();
        i=0;
        for (String navname : defaultnavplugins.keySet()) {
            if (!navplugins.containsKey(navname)) {
                prop.put("search.navigation.list_" + i + "_name", navname);
                prop.put("search.navigation.list_" + i + "_displayname", defaultnavplugins.get(navname));
                i++;
            }
        }
        if (i == 0) { // on no new nav avail. put in dummy name to indicate empty list
            prop.put("search.navigation.list_" + i + "_name", "");
            prop.put("search.navigation.list_" + i + "_displayname", "---");
            i = 1;
        }
        prop.put("search.navigation.list", i);

        prop.put("search.navigation.maxcount", sb.getConfigInt(SwitchboardConstants.SEARCH_NAVIGATION_MAXCOUNT, QueryParams.FACETS_STANDARD_MAXCOUNT));

        prop.put("about.headline", sb.getConfig("about.headline", "About"));
        prop.put("about.body", sb.getConfig("about.body", ""));

        prop.put("content_showDate_date", GenericFormatter.RFC1123_SHORT_FORMATTER.format(new Date(System.currentTimeMillis())));
        return prop;
    }

}
