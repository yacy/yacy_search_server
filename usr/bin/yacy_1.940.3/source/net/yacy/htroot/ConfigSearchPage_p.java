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

package net.yacy.htroot;

import java.sql.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.TransactionManager;
import net.yacy.data.WorkTables;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.navigator.Navigator;
import net.yacy.search.navigator.NavigatorPlugins;
import net.yacy.search.navigator.NavigatorSort;
import net.yacy.search.navigator.NavigatorSortDirection;
import net.yacy.search.navigator.NavigatorSortType;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEventCache;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ConfigSearchPage_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        if (post != null) {
        	/* Check this is a valid transaction */
        	TransactionManager.checkPostTransaction(header, post);

        	final int initialNavMaxCOunt = sb.getConfigInt(
    				SwitchboardConstants.SEARCH_NAVIGATION_MAXCOUNT, QueryParams.FACETS_STANDARD_MAXCOUNT_DEFAULT);
        	final String initialNavConf = sb.getConfig("search.navigation", "");

            if (post.containsKey("searchpage_set")) {
                final String newGreeting = post.get(SwitchboardConstants.GREETING, "");
                // store this call as api call
                sb.tables.recordAPICall(post, "ConfigPortal_p.html", WorkTables.TABLE_API_TYPE_CONFIGURATION, "new portal design. greeting: " + newGreeting);

                sb.setConfig("publicTopmenu", post.getBoolean("publicTopmenu"));

				sb.setConfig(SwitchboardConstants.SEARCH_PUBLIC_TOP_NAV_BAR_LOGIN,
						post.getBoolean(SwitchboardConstants.SEARCH_PUBLIC_TOP_NAV_BAR_LOGIN));

                sb.setConfig("search.options", post.getBoolean("search.options"));

                sb.setConfig("search.text", post.getBoolean("search.text"));
                sb.setConfig("search.image", post.getBoolean("search.image"));
                sb.setConfig("search.audio", post.getBoolean("search.audio"));
                sb.setConfig("search.video", post.getBoolean("search.video"));
                sb.setConfig("search.app", post.getBoolean("search.app"));

                sb.setConfig(SwitchboardConstants.SEARCH_RESULT_SHOW_FAVICON, post.getBoolean(SwitchboardConstants.SEARCH_RESULT_SHOW_FAVICON));
                sb.setConfig(SwitchboardConstants.SEARCH_RESULT_SHOW_KEYWORDS, post.getBoolean(SwitchboardConstants.SEARCH_RESULT_SHOW_KEYWORDS));

                // maximum number of initially displayed keywords/tags
				final int keywordsFirstMaxCount = post.getInt(SwitchboardConstants.SEARCH_RESULT_KEYWORDS_FISRT_MAX_COUNT,
						SwitchboardConstants.SEARCH_RESULT_KEYWORDS_FISRT_MAX_COUNT_DEFAULT);
				if (keywordsFirstMaxCount > 0) {
					sb.setConfig(SwitchboardConstants.SEARCH_RESULT_KEYWORDS_FISRT_MAX_COUNT, keywordsFirstMaxCount);
				}

                sb.setConfig("search.result.show.date", post.getBoolean("search.result.show.date"));
                sb.setConfig("search.result.show.size", post.getBoolean("search.result.show.size"));
                sb.setConfig("search.result.show.metadata", post.getBoolean("search.result.show.metadata"));
                sb.setConfig("search.result.show.parser", post.getBoolean("search.result.show.parser"));
                sb.setConfig("search.result.show.citation", post.getBoolean("search.result.show.citation"));
                sb.setConfig("search.result.show.pictures", post.getBoolean("search.result.show.pictures"));
                sb.setConfig("search.result.show.cache", post.getBoolean("search.result.show.cache"));
                sb.setConfig("search.result.show.proxy", post.getBoolean("search.result.show.proxy"));
                sb.setConfig("search.result.show.indexbrowser", post.getBoolean("search.result.show.indexbrowser"));
                sb.setConfig("search.result.show.snapshots", post.getBoolean("search.result.show.snapshots"));

                // construct navigation String
                final Set<String> navConfigs = new HashSet<>();
                if (post.getBoolean("search.navigation.location")) {
                	navConfigs.add("location");
                }
                if (post.getBoolean("search.navigation.protocol")) {
                	navConfigs.add("protocol");
                }
                if (post.getBoolean("search.navigation.topics")) {
                	navConfigs.add("topics");
                }
                if (post.getBoolean("search.navigation.date")) {
                	navConfigs.add("date");
                }
                // append active navigator plugins
                final String[] activeNavNames = post.getAll("search.navigation.active");
				for (final String navName : activeNavNames) {
					String navConfig = navName;
                	final String navSortConfig = post.get("search.navigation." + navName + ".navSort");
					final NavigatorSort defaultSort = NavigatorPlugins.getDefaultSort(navName);
					if (NavigatorPlugins.parseNavSortConfig(
							navName + NavigatorPlugins.NAV_PROPS_CONFIG_SEPARATOR + navSortConfig,
							defaultSort) != defaultSort) {
						navConfig += NavigatorPlugins.NAV_PROPS_CONFIG_SEPARATOR + navSortConfig;
					}
					navConfigs.add(navConfig);
                }
                sb.setConfig("search.navigation", navConfigs);
                // maxcount nav entries, default
                final int navmaxcnt = post.getInt(SwitchboardConstants.SEARCH_NAVIGATION_MAXCOUNT, QueryParams.FACETS_STANDARD_MAXCOUNT_DEFAULT);
                if (navmaxcnt > 5) {
                    sb.setConfig(SwitchboardConstants.SEARCH_NAVIGATION_MAXCOUNT, navmaxcnt);
                }

                // maxcount dates navigator entries
				final int datesNavMaxCnt = post.getInt(SwitchboardConstants.SEARCH_NAVIGATION_DATES_MAXCOUNT,
						QueryParams.FACETS_DATE_MAXCOUNT_DEFAULT);
				if (datesNavMaxCnt > 5) {
					sb.setConfig(SwitchboardConstants.SEARCH_NAVIGATION_DATES_MAXCOUNT, datesNavMaxCnt);
				}
            }

            if (post.containsKey("add.nav")) { // button: add navigator plugin to ative list
                final String navname = post.get("search.navigation.navname");
                if (navname != null && !navname.isEmpty()) {
                    final Set<String> navConfigs = sb.getConfigSet("search.navigation");
                    navConfigs.add(navname);
                    sb.setConfig("search.navigation", navConfigs);
                }
            } else if (post.containsKey("del.nav")) { // button: delete navigator plugin from active list
                final String navToDelete = post.get("del.nav");
                final Set<String> navConfigs = sb.getConfigSet("search.navigation");
                navConfigs.removeIf(navConfig -> NavigatorPlugins.getNavName(navConfig).equals(navToDelete));
                sb.setConfig("search.navigation", navConfigs);
            }

            if (post.containsKey("searchpage_default")) {
                // load defaults from defaults/yacy.init file
                final Properties config = sb.loadDefaultConfig();
                sb.setConfig("publicTopmenu", config.getProperty("publicTopmenu","true"));
				sb.setConfig(SwitchboardConstants.SEARCH_PUBLIC_TOP_NAV_BAR_LOGIN,
						config.getProperty(SwitchboardConstants.SEARCH_PUBLIC_TOP_NAV_BAR_LOGIN,
								Boolean.toString(SwitchboardConstants.SEARCH_PUBLIC_TOP_NAV_BAR_LOGIN_DEFAULT)));
                sb.setConfig("search.navigation", config.getProperty("search.navigation","hosts,authors,namespace,topics"));
                sb.setConfig("search.options", config.getProperty("search.options","true"));
                sb.setConfig("search.text", config.getProperty("search.text","true"));
                sb.setConfig("search.image", config.getProperty("search.image","true"));
                sb.setConfig("search.audio", config.getProperty("search.audio","false"));
                sb.setConfig("search.video", config.getProperty("search.video","false"));
                sb.setConfig("search.app", config.getProperty("search.app","false"));
				sb.setConfig(SwitchboardConstants.SEARCH_RESULT_SHOW_FAVICON,
						config.getProperty(SwitchboardConstants.SEARCH_RESULT_SHOW_FAVICON,
								Boolean.toString(SwitchboardConstants.SEARCH_RESULT_SHOW_FAVICON_DEFAULT)));
				sb.setConfig(SwitchboardConstants.SEARCH_RESULT_SHOW_KEYWORDS,
						config.getProperty(SwitchboardConstants.SEARCH_RESULT_SHOW_KEYWORDS,
								Boolean.toString(SwitchboardConstants.SEARCH_RESULT_SHOW_KEYWORDS_DEFAULT)));
				sb.setConfig(SwitchboardConstants.SEARCH_RESULT_KEYWORDS_FISRT_MAX_COUNT,
						config.getProperty(SwitchboardConstants.SEARCH_RESULT_KEYWORDS_FISRT_MAX_COUNT,
								String.valueOf(SwitchboardConstants.SEARCH_RESULT_KEYWORDS_FISRT_MAX_COUNT_DEFAULT)));
                sb.setConfig("search.result.show.date", config.getProperty("search.result.show.date","true"));
                sb.setConfig("search.result.show.size", config.getProperty("search.result.show.size","false"));
                sb.setConfig("search.result.show.metadata", config.getProperty("search.result.show.metadata","false"));
                sb.setConfig("search.result.show.parser", config.getProperty("search.result.show.parser","false"));
                sb.setConfig("search.result.show.citation", config.getProperty("search.result.show.citation","false"));
                sb.setConfig("search.result.show.pictures", config.getProperty("search.result.show.pictures","false"));
                sb.setConfig("search.result.show.cache", config.getProperty("search.result.show.cache","true"));
                sb.setConfig("search.result.show.proxy", config.getProperty("search.result.show.proxy","false"));
                sb.setConfig("search.result.show.indexbrowser", config.getProperty("search.result.show.indexbrowser","true"));
                sb.setConfig("search.result.show.snapshots", config.getProperty("search.result.show.snapshots","true"));
				sb.setConfig(SwitchboardConstants.SEARCH_NAVIGATION_MAXCOUNT,
						config.getProperty(SwitchboardConstants.SEARCH_NAVIGATION_MAXCOUNT,
								String.valueOf(QueryParams.FACETS_STANDARD_MAXCOUNT_DEFAULT)));
				sb.setConfig(SwitchboardConstants.SEARCH_NAVIGATION_DATES_MAXCOUNT,
						config.getProperty(SwitchboardConstants.SEARCH_NAVIGATION_DATES_MAXCOUNT,
								String.valueOf(QueryParams.FACETS_DATE_MAXCOUNT_DEFAULT)));
            }

            if(!initialNavConf.equals(sb.getConfig("search.navigation", "")) || initialNavMaxCOunt != sb.getConfigInt(
    				SwitchboardConstants.SEARCH_NAVIGATION_MAXCOUNT, QueryParams.FACETS_STANDARD_MAXCOUNT_DEFAULT)) {
            	/* Clean up search events cache when necessary */
                SearchEventCache.cleanupEvents(true);
            }
        }

        /* Acquire a transaction token for the next POST form submission */
        try {
            prop.put(TransactionManager.TRANSACTION_TOKEN_PARAM, TransactionManager.getTransactionToken(header));
        } catch (IllegalArgumentException e) {
            sb.log.fine("access by unauthorized or unknown user: no transaction token delivered");
        }

        prop.putHTML(SwitchboardConstants.GREETING, sb.getConfig(SwitchboardConstants.GREETING, ""));
        prop.putHTML(SwitchboardConstants.GREETING_HOMEPAGE, sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
        prop.putHTML(SwitchboardConstants.GREETING_LARGE_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, ""));
        prop.putHTML(SwitchboardConstants.GREETING_SMALL_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));
        prop.putHTML(SwitchboardConstants.GREETING_IMAGE_ALT, sb.getConfig(SwitchboardConstants.GREETING_IMAGE_ALT, ""));
        prop.putHTML(SwitchboardConstants.INDEX_FORWARD, sb.getConfig(SwitchboardConstants.INDEX_FORWARD, ""));
        prop.put("publicTopmenu", sb.getConfigBool("publicTopmenu", false) ? 1 : 0);

		prop.put(SwitchboardConstants.SEARCH_PUBLIC_TOP_NAV_BAR_LOGIN,
				sb.getConfigBool(SwitchboardConstants.SEARCH_PUBLIC_TOP_NAV_BAR_LOGIN,
						SwitchboardConstants.SEARCH_PUBLIC_TOP_NAV_BAR_LOGIN_DEFAULT) ? 1 : 0);

        prop.put("search.options", sb.getConfigBool("search.options", false) ? 1 : 0);

        prop.put("search.text", sb.getConfigBool("search.text", false) ? 1 : 0);
        prop.put("search.image", sb.getConfigBool("search.image", false) ? 1 : 0);
        prop.put("search.audio", sb.getConfigBool("search.audio", false) ? 1 : 0);
        prop.put("search.video", sb.getConfigBool("search.video", false) ? 1 : 0);
        prop.put("search.app", sb.getConfigBool("search.app", false) ? 1 : 0);

		prop.put(SwitchboardConstants.SEARCH_RESULT_SHOW_FAVICON,
				sb.getConfigBool(SwitchboardConstants.SEARCH_RESULT_SHOW_FAVICON,
						SwitchboardConstants.SEARCH_RESULT_SHOW_FAVICON_DEFAULT));

		prop.put(SwitchboardConstants.SEARCH_RESULT_SHOW_KEYWORDS,
				sb.getConfigBool(SwitchboardConstants.SEARCH_RESULT_SHOW_KEYWORDS,
						SwitchboardConstants.SEARCH_RESULT_SHOW_KEYWORDS_DEFAULT) ? 1 : 0);

		prop.put(SwitchboardConstants.SEARCH_RESULT_KEYWORDS_FISRT_MAX_COUNT,
				sb.getConfigInt(SwitchboardConstants.SEARCH_RESULT_KEYWORDS_FISRT_MAX_COUNT,
						SwitchboardConstants.SEARCH_RESULT_KEYWORDS_FISRT_MAX_COUNT_DEFAULT));

        prop.put("search.result.show.date", sb.getConfigBool("search.result.show.date", false) ? 1 : 0);
        prop.put("search.result.show.size", sb.getConfigBool("search.result.show.size", false) ? 1 : 0);
        prop.put("search.result.show.metadata", sb.getConfigBool("search.result.show.metadata", false) ? 1 : 0);
        prop.put("search.result.show.parser", sb.getConfigBool("search.result.show.parser", false) ? 1 : 0);
        prop.put("search.result.show.citation", sb.getConfigBool("search.result.show.citation", false) ? 1 : 0);
        prop.put("search.result.show.pictures", sb.getConfigBool("search.result.show.pictures", false) ? 1 : 0);
        prop.put("search.result.show.cache", sb.getConfigBool("search.result.show.cache", false) ? 1 : 0);
        prop.put("search.result.show.proxy", sb.getConfigBool("search.result.show.proxy", false) ? 1 : 0);
        prop.put("search.result.show.indexbrowser", sb.getConfigBool("search.result.show.indexbrowser", false) ? 1 : 0);
        prop.put("search.result.show.snapshots", sb.getConfigBool("search.result.show.snapshots", false) ? 1 : 0);
        prop.put("search.result.show.ranking", sb.getConfigBool(SwitchboardConstants.SEARCH_RESULT_SHOW_RANKING, SwitchboardConstants.SEARCH_RESULT_SHOW_RANKING_DEFAULT) ? 1 : 0);

        final Set<String> navConfigs = sb.getConfigSet("search.navigation");
        boolean locationNavEnabled = false;
        boolean protocolNavEnabled = false;
        boolean topicsNavEnabled = false;
        boolean dateNavEnabled = false;
        for(final String navConfig : navConfigs) {
        	final String navName = NavigatorPlugins.getNavName(navConfig);
        	if("location".equals(navName)) {
        		locationNavEnabled = true;
        	} else if("protocol".equals(navName)) {
        		protocolNavEnabled = true;
        	} else if("topics".equals(navName)) {
        		topicsNavEnabled = true;
        	} else if("date".equals(navName)) {
        		dateNavEnabled = true;
        	}
        }

        prop.put("search.navigation.location", locationNavEnabled);
        prop.put("search.navigation.protocol", protocolNavEnabled);
        prop.put("search.navigation.topics", topicsNavEnabled);
        prop.put("search.navigation.date", dateNavEnabled);
        // list active navigator plugins
        final Map<String, Navigator> navplugins = NavigatorPlugins.initFromCfgStrings(navConfigs);
        int i = 0;
		for (final String navname : navplugins.keySet()) {
            final Navigator nav = navplugins.get(navname);
            prop.put("search.navigation.plugin_" + i + "_name", navname);
            prop.put("search.navigation.plugin_" + i + "_displayname", nav.getDisplayName());
			final int navSort;
            if(nav.getSort() == null) {
            	navSort = 0;
            } else {
            	if(nav.getSort().getSortType() == NavigatorSortType.COUNT) {
            		if(nav.getSort().getSortDir() == NavigatorSortDirection.DESC) {
            			navSort = 0;
            		} else {
            			navSort = 1;
            		}
            	} else {
            		if(nav.getSort().getSortDir() == NavigatorSortDirection.DESC) {
            			navSort = 2;
            		} else {
            			navSort = 3;
            		}
            	}
            }
            prop.put("search.navigation.plugin_" + i + "_navSort", navSort);
            i++;
        }
        prop.put("search.navigation.plugin", i);

        // fill select field options (only navs not already active)
        final Map<String, String> defaultnavplugins = NavigatorPlugins.listAvailable();
        i=0;
        for (final String navname : defaultnavplugins.keySet()) {
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

		prop.put(SwitchboardConstants.SEARCH_NAVIGATION_MAXCOUNT, sb.getConfigInt(
				SwitchboardConstants.SEARCH_NAVIGATION_MAXCOUNT, QueryParams.FACETS_STANDARD_MAXCOUNT_DEFAULT));

		prop.put(SwitchboardConstants.SEARCH_NAVIGATION_DATES_MAXCOUNT, sb.getConfigInt(
				SwitchboardConstants.SEARCH_NAVIGATION_DATES_MAXCOUNT, QueryParams.FACETS_DATE_MAXCOUNT_DEFAULT));

        prop.put("about.headline", sb.getConfig("about.headline", "About"));
        prop.put("about.body", sb.getConfig("about.body", ""));

        prop.put("content_showDate_date", GenericFormatter.RFC1123_SHORT_FORMATTER.format(new Date(System.currentTimeMillis())));
        return prop;
    }

}
