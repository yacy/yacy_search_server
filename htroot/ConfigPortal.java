// ConfigPortal.java
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
import java.util.Properties;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.WorkTables;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.http.HTTPDFileHandler;

public class ConfigPortal {

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

            if (post.containsKey("popup")) {
                final String popup = post.get("popup", "status");
                if ("front".equals(popup)) {
                    sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "index.html");
                } else if ("search".equals(popup)) {
                    sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "yacysearch.html");
                } else if ("interactive".equals(popup)) {
                    sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "yacyinteractive.html");
                } else {
                    sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "Status.html");
                }
                sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, sb.getConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "index.html"));
                HTTPDFileHandler.initDefaultPath();
            }
            if (post.containsKey("searchpage_set")) {
                final String newGreeting = post.get(SwitchboardConstants.GREETING, "");
                // store this call as api call
                sb.tables.recordAPICall(post, "ConfigPortal.html", WorkTables.TABLE_API_TYPE_CONFIGURATION, "new portal design. greeting: " + newGreeting);

                sb.setConfig(SwitchboardConstants.GREETING, newGreeting);
                sb.setConfig(SwitchboardConstants.GREETING_HOMEPAGE, post.get(SwitchboardConstants.GREETING_HOMEPAGE, ""));
                sb.setConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, post.get(SwitchboardConstants.GREETING_LARGE_IMAGE, ""));
                sb.setConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, post.get(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));
                sb.setConfig(SwitchboardConstants.SEARCH_TARGET_DEFAULT, post.get("target", "_self"));
                sb.setConfig(SwitchboardConstants.SEARCH_TARGET_SPECIAL, post.get("target_special", "_self"));
                sb.setConfig(SwitchboardConstants.SEARCH_TARGET_SPECIAL_PATTERN, post.get("target_special_pattern", "_self"));
                sb.setConfig(SwitchboardConstants.SEARCH_ITEMS, post.getInt("maximumRecords", 10));
                sb.setConfig(SwitchboardConstants.INDEX_FORWARD, post.get(SwitchboardConstants.INDEX_FORWARD, ""));
                HTTPDFileHandler.indexForward = post.get(SwitchboardConstants.INDEX_FORWARD, "");
                sb.setConfig("publicTopmenu", !post.containsKey("publicTopmenu") || post.getBoolean("publicTopmenu"));
                sb.setConfig("publicSearchpage", !post.containsKey("publicSearchpage") || post.getBoolean("publicSearchpage"));
                sb.setConfig("search.options", post.getBoolean("search.options"));

                sb.setConfig("interaction.userlogon.enabled", post.getBoolean("interaction.userlogon"));

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
                sb.setConfig("search.result.show.tags", post.getBoolean("search.result.show.tags"));

                sb.setConfig(SwitchboardConstants.SEARCH_VERIFY, post.get("search.verify", "ifexist"));
                sb.setConfig(SwitchboardConstants.SEARCH_VERIFY_DELETE, post.getBoolean("search.verify.delete"));

                sb.setConfig("about.headline", post.get("about.headline", ""));
                sb.setConfig("about.body", post.get("about.body", ""));

                String excludehosts = post.get("search.excludehosts", "");
                sb.setConfig("search.excludehosts", excludehosts);
                sb.setConfig("search.excludehosth", DigestURI.hosthashes(excludehosts));

                // construct navigation String
                String nav = "";
                if (post.getBoolean("search.navigation.hosts")) nav += "hosts,";
                if (post.getBoolean("search.navigation.authors")) nav += "authors,";
                if (post.getBoolean("search.navigation.namespace")) nav += "namespace,";
                if (post.getBoolean("search.navigation.topics")) nav += "topics,";
                if (nav.endsWith(",")) nav = nav.substring(0, nav.length() - 1); sb.setConfig("search.navigation", nav);
            }
            if (post.containsKey("searchpage_default")) {
                // load defaults from defaults/yacy.init file
                final Properties config = new Properties();
                final String mes = "ConfigPortal";
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(new File(sb.appPath, "defaults/yacy.init"));
                    config.load(fis);
                } catch (final FileNotFoundException e) {
                    Log.logSevere(mes, "could not find configuration file.");
                    return prop;
                } catch (final IOException e) {
                    Log.logSevere(mes, "could not read configuration file.");
                    return prop;
                } finally {
                    if (fis != null) {
                        try {
                            fis.close();
                        } catch (final IOException e) {
                            Log.logException(e);
                        }
                    }
                }
                sb.setConfig(SwitchboardConstants.GREETING, config.getProperty(SwitchboardConstants.GREETING,"P2P Web Search"));
                sb.setConfig(SwitchboardConstants.GREETING_HOMEPAGE, config.getProperty(SwitchboardConstants.GREETING_HOMEPAGE,"http://yacy.net"));
                sb.setConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, config.getProperty(SwitchboardConstants.GREETING_LARGE_IMAGE,"/env/grafics/YaCyLogo_120ppi.png"));
                sb.setConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, config.getProperty(SwitchboardConstants.GREETING_SMALL_IMAGE,"/env/grafics/YaCyLogo_60ppi.png"));
                sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, config.getProperty(SwitchboardConstants.BROWSER_POP_UP_PAGE,"Status.html"));
                sb.setConfig(SwitchboardConstants.INDEX_FORWARD, config.getProperty(SwitchboardConstants.INDEX_FORWARD,""));
                HTTPDFileHandler.indexForward = "";
                sb.setConfig(SwitchboardConstants.SEARCH_TARGET_DEFAULT, config.getProperty(SwitchboardConstants.SEARCH_TARGET_DEFAULT,"_self"));
                sb.setConfig(SwitchboardConstants.SEARCH_TARGET_SPECIAL, config.getProperty(SwitchboardConstants.SEARCH_TARGET_SPECIAL,"_self"));
                sb.setConfig(SwitchboardConstants.SEARCH_TARGET_SPECIAL_PATTERN, config.getProperty(SwitchboardConstants.SEARCH_TARGET_SPECIAL_PATTERN,""));
                sb.setConfig("publicTopmenu", config.getProperty("publicTopmenu","true"));
                sb.setConfig("publicSearchpage", config.getProperty("publicSearchpage","true"));
                sb.setConfig("search.navigation", config.getProperty("search.navigation","hosts,authors,namespace,topics"));
                sb.setConfig("search.options", config.getProperty("search.options","true"));
                sb.setConfig("interaction.userlogon.enabled", config.getProperty("interaction.userlogon.enabled","false"));
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
                sb.setConfig("search.result.show.tags", config.getProperty("search.result.show.tags","false"));
                sb.setConfig(SwitchboardConstants.SEARCH_VERIFY, config.getProperty(SwitchboardConstants.SEARCH_VERIFY,"iffresh"));
                sb.setConfig(SwitchboardConstants.SEARCH_VERIFY_DELETE, config.getProperty(SwitchboardConstants.SEARCH_VERIFY_DELETE,"true"));
                sb.setConfig("about.headline", config.getProperty("about.headline",""));
                sb.setConfig("about.body", config.getProperty("about.body",""));
                sb.setConfig("search.excludehosts", config.getProperty("search.excludehosts",""));
                sb.setConfig("search.excludehosth", config.getProperty("search.excludehosth",""));
            }
        }

        prop.putHTML(SwitchboardConstants.GREETING, sb.getConfig(SwitchboardConstants.GREETING, ""));
        prop.putHTML(SwitchboardConstants.GREETING_HOMEPAGE, sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
        prop.putHTML(SwitchboardConstants.GREETING_LARGE_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, ""));
        prop.putHTML(SwitchboardConstants.GREETING_SMALL_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));
        prop.putHTML(SwitchboardConstants.INDEX_FORWARD, sb.getConfig(SwitchboardConstants.INDEX_FORWARD, ""));
        prop.put("publicTopmenu", sb.getConfigBool("publicTopmenu", false) ? 1 : 0);
        prop.put("publicSearchpage", sb.getConfigBool("publicSearchpage", false) ? 1 : 0);
        prop.put("search.options", sb.getConfigBool("search.options", false) ? 1 : 0);

        prop.put("interaction.userlogon", sb.getConfigBool("interaction.userlogon.enabled", false) ? 1 : 0);

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
        prop.put("search.result.show.tags", sb.getConfigBool("search.result.show.tags", false) ? 1 : 0);

        prop.put("search.navigation.hosts", sb.getConfig("search.navigation", "").indexOf("hosts",0) >= 0 ? 1 : 0);
        prop.put("search.navigation.authors", sb.getConfig("search.navigation", "").indexOf("authors",0) >= 0 ? 1 : 0);
        prop.put("search.navigation.namespace", sb.getConfig("search.navigation", "").indexOf("namespace",0) >= 0 ? 1 : 0);
        prop.put("search.navigation.topics", sb.getConfig("search.navigation", "").indexOf("topics",0) >= 0 ? 1 : 0);

        prop.put("search.verify.nocache", sb.getConfig("search.verify", "").equals("nocache") ? 1 : 0);
        prop.put("search.verify.iffresh", sb.getConfig("search.verify", "").equals("iffresh") ? 1 : 0);
        prop.put("search.verify.ifexist", sb.getConfig("search.verify", "").equals("ifexist") ? 1 : 0);
        prop.put("search.verify.cacheonly", sb.getConfig("search.verify", "").equals("cacheonly") ? 1 : 0);
        prop.put("search.verify.false", sb.getConfig("search.verify", "").equals("false") ? 1 : 0);
        prop.put("search.verify.delete", sb.getConfigBool(SwitchboardConstants.SEARCH_VERIFY_DELETE, true) ? 1 : 0);

        prop.put("about.headline", sb.getConfig("about.headline", ""));
        prop.put("about.body", sb.getConfig("about.body", ""));

        prop.put("search.excludehosts", sb.getConfig("search.excludehosts", ""));
        prop.put("search.excludehosth", sb.getConfig("search.excludehosth", ""));

        final String  browserPopUpPage = sb.getConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "ConfigBasic.html");
        prop.put("popupFront", 0);
        prop.put("popupSearch", 0);
        prop.put("popupInteractive", 0);
        prop.put("popupStatus", 0);
        if (browserPopUpPage.startsWith("index")) {
            prop.put("popupFront", 1);
        } else if (browserPopUpPage.startsWith("yacysearch")) {
            prop.put("popupSearch", 1);
        } else if (browserPopUpPage.startsWith("yacyinteractive")) {
            prop.put("popupInteractive", 1);
        } else {
            prop.put("popupStatus", 1);
        }

        prop.put("maximumRecords", sb.getConfigInt(SwitchboardConstants.SEARCH_ITEMS, 10));

        final String target = sb.getConfig(SwitchboardConstants.SEARCH_TARGET_DEFAULT, "_self");
        prop.put("target_selected_blank", "_blank".equals(target) ? 1 : 0);
        prop.put("target_selected_self", "_self".equals(target) ? 1 : 0);
        prop.put("target_selected_parent", "_parent".equals(target) ? 1 : 0);
        prop.put("target_selected_top", "_top".equals(target) ? 1 : 0);
        prop.put("target_selected_searchresult", "searchresult".equals(target) ? 1 : 0);

        final String target_special = sb.getConfig(SwitchboardConstants.SEARCH_TARGET_SPECIAL, "_self");
        prop.put("target_selected_special_blank", "_blank".equals(target_special) ? 1 : 0);
        prop.put("target_selected_special_self", "_self".equals(target_special) ? 1 : 0);
        prop.put("target_selected_special_parent", "_parent".equals(target_special) ? 1 : 0);
        prop.put("target_selected_special_top", "_top".equals(target_special) ? 1 : 0);
        prop.put("target_selected_special_searchresult", "searchresult".equals(target_special) ? 1 : 0);
        prop.put("target_special_pattern", sb.getConfig(SwitchboardConstants.SEARCH_TARGET_SPECIAL_PATTERN, ""));

        String myaddress = (sb.peers == null) ? null : sb.peers.mySeed() == null ? null : sb.peers.mySeed().getPublicAddress();
        if (myaddress == null) {
            myaddress = "localhost:" + sb.getConfig("port", "8090");
        }
        prop.put("myaddress", myaddress);
        return prop;
    }

}
