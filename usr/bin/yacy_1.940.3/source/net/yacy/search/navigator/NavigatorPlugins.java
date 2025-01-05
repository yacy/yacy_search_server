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

import static net.yacy.search.query.SearchEvent.log;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.yacy.crawler.CrawlSwitchboard;
import net.yacy.search.schema.CollectionSchema;

/**
 * Class to create and manipulate search navigator plugin list
 */
public class NavigatorPlugins {
	
	/**
	 * Separator for specific properties in each navigator configuration 
	 */
	public static final String NAV_PROPS_CONFIG_SEPARATOR = ":";
	
    /**
     * List of available navigators
     * @return Map key=navigatorCfgname, value=std.DisplayName
     */
    static public Map<String, String> listAvailable() {
        Map<String, String> defaultnavplugins = new TreeMap<String, String>();
        defaultnavplugins.put("filetype", "Filetype");
        defaultnavplugins.put("hosts", "Provider");
        defaultnavplugins.put("language", "Language");
        defaultnavplugins.put("authors", "Authors");
        defaultnavplugins.put("collections", "Collection");
        defaultnavplugins.put("namespace", "Wiki Name Space");
        defaultnavplugins.put("year", "Year");
        // defaultnavplugins.put("year:dates_in_content_dts:Event","Event");
        defaultnavplugins.put("keywords", "Keywords");
        return defaultnavplugins;
    }
    
    /**
     * @param navConfig a navigator configuration String
     * @return the name identifying the navigator, or null when navConfig is null 
     */
    public static final String getNavName(final String navConfig) {
    	String name = navConfig;
    	if(navConfig != null) {
    		final int navConfigPopsIndex = navConfig.indexOf(NAV_PROPS_CONFIG_SEPARATOR);
    		if(navConfigPopsIndex >= 0) {
    			name = navConfig.substring(0, navConfigPopsIndex);
    		}
    	}
    	return name;
    }
    
    /**
     * @param navName a navigator name
     * @return the default sort properties to apply for the given navigator
     */
    public static final NavigatorSort getDefaultSort(final String navName) {
    	if("year".equals(navName)) {
    		return NavigatorSort.LABEL_DESC;
    	}
    	return NavigatorSort.COUNT_DESC;
    }
    
	/**
	 * <p>
	 * Parse a navigator configuration entry and return the sort properties to
	 * apply.
	 * </p>
	 * <p>
	 * Supported formats :
	 * <ul>
	 * <li>"navName" : descending sort by count values (except for the year navigator, where the default is by descending displayed labels)</li>
	 * <li>"navName:count" : descending sort by count values</li>
	 * <li>"navName:label" : ascending sort by displayed labels</li>
	 * <li>"navName:count:asc" : ascending sort by count values</li>
	 * <li>"navName:count:desc" : descending sort by count values</li>
	 * <li>"navName:label:asc" : ascending sort by displayed labels</li>
	 * <li>"navName:label:desc" : descending sort by displayed labels</li>
	 * </ul>
	 * </p>
	 * 
	 * @param navConfig   a navigator configuration String
	 * @return return the sort properties of the navigator
	 */
	public static final NavigatorSort parseNavSortConfig(final String navConfig) {
		return parseNavSortConfig(navConfig, getDefaultSort(getNavName(navConfig)));
	}
    
	/**
	 * <p>
	 * Parse a navigator configuration entry and return the sort properties to
	 * apply.
	 * </p>
	 * <p>
	 * Supported formats :
	 * <ul>
	 * <li>"navName" : apply provided default sort/li>
	 * <li>"navName:count" : descending sort by count values</li>
	 * <li>"navName:label" : ascending sort by displayed labels</li>
	 * <li>"navName:count:asc" : ascending sort by count values</li>
	 * <li>"navName:count:desc" : descending sort by count values</li>
	 * <li>"navName:label:asc" : ascending sort by displayed labels</li>
	 * <li>"navName:label:desc" : descending sort by displayed labels</li>
	 * </ul>
	 * </p>
	 * 
	 * @param navConfig   a navigator configuration String
	 * @param defaultSort the default sort properties to apply when the
	 *                    configuration String does not specify sort properties
	 * @return return the sort properties of the navigator
	 */
	public static final NavigatorSort parseNavSortConfig(final String navConfig, final NavigatorSort defaultSort) {
		if (navConfig == null) {
			return defaultSort;
		}
		final Set<String> navProperties = new HashSet<>();
		Collections.addAll(navProperties, navConfig.split(NAV_PROPS_CONFIG_SEPARATOR));

		NavigatorSort sort = defaultSort;
		if (navProperties.contains(NavigatorSortType.LABEL.toString().toLowerCase(Locale.ROOT))) {
			sort = NavigatorSort.LABEL_ASC; // default label sort
			if (navProperties.contains(NavigatorSortDirection.DESC.toString().toLowerCase(Locale.ROOT))) {
				sort = NavigatorSort.LABEL_DESC;
			}
		} if (navProperties.contains(NavigatorSortType.COUNT.toString().toLowerCase(Locale.ROOT))) {
			sort = NavigatorSort.COUNT_DESC; // default count sort
			if (navProperties.contains(NavigatorSortDirection.ASC.toString().toLowerCase(Locale.ROOT))) {
				sort = NavigatorSort.COUNT_ASC;
			}
		} 
		return sort;
	}
	
    /**
     * Creates map of active search navigators from navigator config strings
     * @param navConfigs navigator configuration strings
     * @return map key=navigatorname, value=navigator.plugin reference
     */
    public static Map<String, Navigator> initFromCfgStrings(final Set<String> navConfigs) {

        final LinkedHashMap<String, Navigator> navigatorPlugins = new LinkedHashMap<>();
        if(navConfigs == null) {
        	return navigatorPlugins;
        }
        for (final String navConfig : navConfigs) {
        	final String navName = getNavName(navConfig);
            if ("authors".equals(navName)) {
                navigatorPlugins.put("authors", new StringNavigator("Authors", CollectionSchema.author_sxt, parseNavSortConfig(navConfig)));
            } else if ("collections".equals(navName)) {
                RestrictedStringNavigator tmpnav = new RestrictedStringNavigator("Collection", CollectionSchema.collection_sxt, parseNavSortConfig(navConfig));
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
            } else if ("filetype".equals(navName)) {
				navigatorPlugins.put("filetype", new FileTypeNavigator("Filetype", CollectionSchema.url_file_ext_s,
						parseNavSortConfig(navConfig)));
            } else if("hosts".equals(navName)) {
				navigatorPlugins.put("hosts",
						new HostNavigator("Provider", CollectionSchema.host_s, parseNavSortConfig(navConfig)));
            } else if ("language".equals(navName)) {
                navigatorPlugins.put("language", new LanguageNavigator("Language", parseNavSortConfig(navConfig)));
            } else if ("namespace".equals(navName)) {
                navigatorPlugins.put("namespace", new NameSpaceNavigator("Wiki Name Space", parseNavSortConfig(navConfig)));
            } else if ("year".equals(navName)) {
            	// YearNavigator with possible def of :fieldname:title in configstring
            	final LinkedHashSet<String> navProperties = new LinkedHashSet<>();
            	Collections.addAll(navProperties, navConfig.split(NAV_PROPS_CONFIG_SEPARATOR));
            		
            	/* Remove sort related properties */
            	for(final NavigatorSortType sortType : NavigatorSortType.values()) {
            		navProperties.remove(sortType.toString().toLowerCase(Locale.ROOT));
            	}
            	for(final NavigatorSortDirection sortDir : NavigatorSortDirection.values()) {
            		navProperties.remove(sortDir.toString().toLowerCase(Locale.ROOT));
            	}
            	final String[] navfielddef = navProperties.toArray(new String[navProperties.size()]);
            		
            	if (navfielddef.length > 1) {
            		try {
            			// year:fieldname:title
            			CollectionSchema field = CollectionSchema.valueOf(navfielddef[1]);
            			if (navfielddef.length > 2) {
            				navigatorPlugins.put(navfielddef[1], new YearNavigator(navfielddef[2], field, parseNavSortConfig(navConfig)));
            			} else {
            				navigatorPlugins.put(navfielddef[1], new YearNavigator("Year-" + navfielddef[1], field, parseNavSortConfig(navConfig)));
            			}
            		} catch (final java.lang.IllegalArgumentException ex) {
            			log.severe("wrong navigator name in config: \"" + navConfig + "\" " + ex.getMessage());
            		}
            	} else { // "year" only use default last_modified
                    navigatorPlugins.put("year", new YearNavigator("Year", CollectionSchema.last_modified, parseNavSortConfig(navConfig)));
                }
            } else if ("keywords".equals(navName)) {
                navigatorPlugins.put("keywords", new TokenizedStringNavigator("Keywords", CollectionSchema.keywords, parseNavSortConfig(navConfig)));
            }
        }
        return navigatorPlugins;
    }

}
