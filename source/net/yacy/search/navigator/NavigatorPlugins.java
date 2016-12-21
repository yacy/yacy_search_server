/**
 * NavigatorPlugins.java
 * (C) 2016 by reger24; https://github.com/reger24
 *
 * This is a part of YaCy, a peer-to-peer based web search engine
 *
 * LICENSE
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */
package net.yacy.search.navigator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import net.yacy.crawler.CrawlSwitchboard;
import static net.yacy.search.query.SearchEvent.log;
import net.yacy.search.schema.CollectionSchema;

/**
 * Class to create and manipulate search navigator plugin list
 */
public class NavigatorPlugins {

    /**
     * List of available navigators
     * @return Map key=navigatorCfgname, value=std.DisplayName
     */
    static public Map<String, String> listAvailable() {
        Map<String, String> defaultnavplugins = new TreeMap();
        defaultnavplugins.put("filetype", "Filetype");
        // defaultnavplugins.put("hosts", "Provider");
        // defaultnavplugins.put("language", "Language");
        defaultnavplugins.put("authors", "Authors");
        defaultnavplugins.put("collections", "Collection");
        defaultnavplugins.put("namespace", "Wiki Name Space");
        defaultnavplugins.put("year", "Year");
        // defaultnavplugins.put("year:dates_in_content_dts:Event","Event");
        return defaultnavplugins;
    }

    /**
     * Creates map of active search navigators from comma separated config string
     * @param navcfg comma separated string of navigator names
     * @return map key=navigatorname, value=navigator.plugin reference
     */
    static public Map<String, Navigator> initFromCfgString(final String navcfg) {

        LinkedHashMap<String, Navigator> navigatorPlugins = new LinkedHashMap<String, Navigator>();
        if (navcfg == null || navcfg.isEmpty()) return navigatorPlugins;
        String[] navnames = navcfg.split(",");
        for (String navname : navnames) {
            if (navname.contains("authors")) {
                navigatorPlugins.put("authors", new StringNavigator("Authors", CollectionSchema.author_sxt));
            }
            
            if (navname.contains("collections")) {
                RestrictedStringNavigator tmpnav = new RestrictedStringNavigator("Collection", CollectionSchema.collection_sxt);
                // exclude default internal collection names
                tmpnav.addForbidden("dht");
                tmpnav.addForbidden("robot_" + CrawlSwitchboard.CRAWL_PROFILE_AUTOCRAWL_DEEP);
                tmpnav.addForbidden("robot_" + CrawlSwitchboard.CRAWL_PROFILE_AUTOCRAWL_SHALLOW);
                tmpnav.addForbidden("robot_" + CrawlSwitchboard.CRAWL_PROFILE_PROXY);
                tmpnav.addForbidden("robot_" + CrawlSwitchboard.CRAWL_PROFILE_REMOTE);
                tmpnav.addForbidden("robot_" + CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_TEXT);
                tmpnav.addForbidden("robot_" + CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT);
                tmpnav.addForbidden("robot_" + CrawlSwitchboard.CRAWL_PROFILE_GREEDY_LEARNING_TEXT);
                tmpnav.addForbidden("robot_" + CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA);
                tmpnav.addForbidden("robot_" + CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA);
                tmpnav.addForbidden("robot_" + CrawlSwitchboard.CRAWL_PROFILE_SURROGATE);
                navigatorPlugins.put("collections", tmpnav);
            }

            if (navname.contains("filetype")) {
                navigatorPlugins.put("filetype", new FileTypeNavigator("Filetype", CollectionSchema.url_file_ext_s));
            }
/*
            if (navname.contains("hosts")) {
                navigatorPlugins.put("hosts", new HostNavigator("Provider", CollectionSchema.host_s));
            }

            if (navname.contains("language")) {
                navigatorPlugins.put("language", new LanguageNavigator("Language"));
            }
*/
            if (navname.contains("namespace")) {
                navigatorPlugins.put("namespace", new NameSpaceNavigator("Wiki Name Space"));
            }

            // YearNavigator with possible def of :fieldname:title in configstring
            if (navname.contains("year")) {
                if ((navname.indexOf(':')) > 0) { // example "year:dates_in_content_dts:Events"
                    String[] navfielddef = navname.split(":");
                    try {
                        // year:fieldname:title
                        CollectionSchema field = CollectionSchema.valueOf(navfielddef[1]);
                        if (navfielddef.length > 2) {
                            navigatorPlugins.put(navfielddef[1], new YearNavigator(navfielddef[2], field));
                        } else {
                            navigatorPlugins.put(navfielddef[1], new YearNavigator("Year-" + navfielddef[1], field));
                        }
                    } catch (java.lang.IllegalArgumentException ex) {
                        log.severe("wrong navigator name in config: \"" + navname + "\" " + ex.getMessage());
                    }
                } else { // "year" only use default last_modified
                    navigatorPlugins.put("year", new YearNavigator("Year", CollectionSchema.last_modified));
                }
            }
        }
        return navigatorPlugins;
    }

}
