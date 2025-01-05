// ConfigPortal_p.java
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

import java.net.MalformedURLException;
import java.time.Duration;
import java.util.Properties;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.TransactionManager;
import net.yacy.data.WorkTables;
import net.yacy.http.servlets.YaCyDefaultServlet;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.snippet.TextSnippet;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.http.HTTPDFileHandler;

public class ConfigPortal_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        if (post != null) {
        	/* Check this is a valid transaction */
        	TransactionManager.checkPostTransaction(header, post);

        	boolean cleanSearchCache = false;

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
                sb.tables.recordAPICall(post, "ConfigPortal_p.html", WorkTables.TABLE_API_TYPE_CONFIGURATION, "new portal design. greeting: " + newGreeting);

                sb.setConfig(SwitchboardConstants.GREETING, newGreeting);
                sb.setConfig(SwitchboardConstants.GREETING_HOMEPAGE, post.get(SwitchboardConstants.GREETING_HOMEPAGE, ""));
                sb.setConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, post.get(SwitchboardConstants.GREETING_LARGE_IMAGE, ""));
                sb.setConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, post.get(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));
                sb.setConfig(SwitchboardConstants.GREETING_IMAGE_ALT, post.get(SwitchboardConstants.GREETING_IMAGE_ALT, ""));
                sb.setConfig(SwitchboardConstants.SEARCH_TARGET_DEFAULT, post.get("target", "_self"));
                sb.setConfig(SwitchboardConstants.SEARCH_TARGET_SPECIAL, post.get("target_special", "_self"));
                sb.setConfig(SwitchboardConstants.SEARCH_TARGET_SPECIAL_PATTERN, post.get("target_special_pattern", "_self"));
                sb.setConfig(SwitchboardConstants.SEARCH_ITEMS, post.getInt("maximumRecords", 10));
                sb.setConfig(SwitchboardConstants.INDEX_FORWARD, post.get(SwitchboardConstants.INDEX_FORWARD, ""));
                HTTPDFileHandler.indexForward = post.get(SwitchboardConstants.INDEX_FORWARD, "");
                sb.setConfig("publicTopmenu", !post.containsKey("publicTopmenu") || post.getBoolean("publicTopmenu"));
                sb.setConfig(SwitchboardConstants.PUBLIC_SEARCHPAGE, !post.containsKey(SwitchboardConstants.PUBLIC_SEARCHPAGE) || post.getBoolean(SwitchboardConstants.PUBLIC_SEARCHPAGE));
                sb.setConfig("search.options", post.getBoolean("search.options"));

                final boolean oldJsResort = sb.getConfigBool(SwitchboardConstants.SEARCH_JS_RESORT, SwitchboardConstants.SEARCH_JS_RESORT_DEFAULT);
                final boolean newJsResort = post.getBoolean(SwitchboardConstants.SEARCH_JS_RESORT);
                /* When this setting has changed we must clean up the search event cache as it affects how search results are retrieved */
                cleanSearchCache = cleanSearchCache || oldJsResort != newJsResort;
                sb.setConfig(SwitchboardConstants.SEARCH_JS_RESORT, newJsResort);

                final boolean oldStrictContentDom = sb.getConfigBool(SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM, SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM_DEFAULT);
                final boolean newStrictContentDom = post.getBoolean(SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM);
                /* When this setting has changed we must clean up the search event cache as it affects how search results are retrieved */
                cleanSearchCache = cleanSearchCache || oldStrictContentDom != newStrictContentDom;
                sb.setConfig(SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM, newStrictContentDom);

				sb.setConfig(SwitchboardConstants.REMOTESEARCH_HTTPS_PREFERRED,
						post.getBoolean(SwitchboardConstants.REMOTESEARCH_HTTPS_PREFERRED));

                sb.setConfig(SwitchboardConstants.GREEDYLEARNING_ACTIVE, post.getBoolean(SwitchboardConstants.GREEDYLEARNING_ACTIVE));

                final boolean storeresult = post.getBoolean(SwitchboardConstants.REMOTESEARCH_RESULT_STORE);
                sb.setConfig(SwitchboardConstants.REMOTESEARCH_RESULT_STORE, storeresult);
                sb.setConfig(SwitchboardConstants.REMOTESEARCH_RESULT_STORE_MAXSIZE, post.getLong(SwitchboardConstants.REMOTESEARCH_RESULT_STORE_MAXSIZE, -1));

                sb.setConfig(SwitchboardConstants.SEARCH_VERIFY, post.get("search.verify", "ifexist"));
                sb.setConfig(SwitchboardConstants.SEARCH_VERIFY_DELETE, post.getBoolean("search.verify.delete"));

                sb.setConfig("about.headline", post.get("about.headline", ""));
                sb.setConfig("about.body", post.get("about.body", ""));

                final String excludehosts = post.get("search.excludehosts", "");
                sb.setConfig("search.excludehosts", excludehosts);
                try {
                    sb.setConfig("search.excludehosth", DigestURL.hosthashes(excludehosts));
                } catch (final MalformedURLException e) {
                    ConcurrentLog.logException(e);
                    sb.setConfig("search.excludehosth", "");
                }
            }
            if (post.containsKey("searchpage_default")) {
                // load defaults from defaults/yacy.init file
                final Properties config = sb.loadDefaultConfig();
                sb.setConfig(SwitchboardConstants.GREETING, config.getProperty(SwitchboardConstants.GREETING,"P2P Web Search"));
                sb.setConfig(SwitchboardConstants.GREETING_HOMEPAGE, config.getProperty(SwitchboardConstants.GREETING_HOMEPAGE,"https://yacy.net"));
                sb.setConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, config.getProperty(SwitchboardConstants.GREETING_LARGE_IMAGE,"env/grafics/YaCyLogo_120ppi.png"));
                sb.setConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, config.getProperty(SwitchboardConstants.GREETING_SMALL_IMAGE,"env/grafics/YaCyLogo_60ppi.png"));
                sb.setConfig(SwitchboardConstants.GREETING_IMAGE_ALT, config.getProperty(SwitchboardConstants.GREETING_IMAGE_ALT,"YaCy project web site"));
                sb.setConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, config.getProperty(SwitchboardConstants.BROWSER_POP_UP_PAGE,"Status.html"));
                sb.setConfig(SwitchboardConstants.INDEX_FORWARD, config.getProperty(SwitchboardConstants.INDEX_FORWARD,""));
                HTTPDFileHandler.indexForward = "";
                sb.setConfig(SwitchboardConstants.SEARCH_TARGET_DEFAULT, config.getProperty(SwitchboardConstants.SEARCH_TARGET_DEFAULT,"_self"));
                sb.setConfig(SwitchboardConstants.SEARCH_TARGET_SPECIAL, config.getProperty(SwitchboardConstants.SEARCH_TARGET_SPECIAL,"_self"));
                sb.setConfig(SwitchboardConstants.SEARCH_TARGET_SPECIAL_PATTERN, config.getProperty(SwitchboardConstants.SEARCH_TARGET_SPECIAL_PATTERN,""));
                sb.setConfig("publicTopmenu", config.getProperty("publicTopmenu","true"));
                sb.setConfig(SwitchboardConstants.PUBLIC_SEARCHPAGE, config.getProperty(SwitchboardConstants.PUBLIC_SEARCHPAGE,"true"));
                sb.setConfig("search.navigation", config.getProperty("search.navigation","hosts,authors,namespace,topics"));
                sb.setConfig("search.options", config.getProperty("search.options","true"));

                final boolean oldJsResort = sb.getConfigBool(SwitchboardConstants.SEARCH_JS_RESORT, SwitchboardConstants.SEARCH_JS_RESORT_DEFAULT);
                final boolean newJsResort = Boolean.parseBoolean(config.getProperty(SwitchboardConstants.SEARCH_JS_RESORT, String.valueOf(SwitchboardConstants.SEARCH_JS_RESORT_DEFAULT)));
                /* When this setting has changed we must clean up the search event cache as it affects how search results are retrieved */
                cleanSearchCache = cleanSearchCache || oldJsResort != newJsResort;
                sb.setConfig(SwitchboardConstants.SEARCH_JS_RESORT, newJsResort);

                final boolean oldStrictContentDom = sb.getConfigBool(SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM, SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM_DEFAULT);
                final boolean newStrictContentDom = Boolean.parseBoolean(config.getProperty(SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM, String.valueOf(SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM_DEFAULT)));
                /* When this setting has changed we must clean up the search event cache as it affects how search results are retrieved */
                cleanSearchCache = cleanSearchCache || oldStrictContentDom != newStrictContentDom;
                sb.setConfig(SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM, newStrictContentDom);

				sb.setConfig(SwitchboardConstants.REMOTESEARCH_HTTPS_PREFERRED,
						Boolean.parseBoolean(config.getProperty(SwitchboardConstants.REMOTESEARCH_HTTPS_PREFERRED,
								String.valueOf(SwitchboardConstants.REMOTESEARCH_HTTPS_PREFERRED_DEFAULT))));

                sb.setConfig(SwitchboardConstants.GREEDYLEARNING_ACTIVE, config.getProperty(SwitchboardConstants.GREEDYLEARNING_ACTIVE));
                sb.setConfig(SwitchboardConstants.REMOTESEARCH_RESULT_STORE, config.getProperty(SwitchboardConstants.REMOTESEARCH_RESULT_STORE));
                sb.setConfig(SwitchboardConstants.REMOTESEARCH_RESULT_STORE_MAXSIZE, config.getProperty(SwitchboardConstants.REMOTESEARCH_RESULT_STORE_MAXSIZE));
                sb.setConfig(SwitchboardConstants.SEARCH_VERIFY, config.getProperty(SwitchboardConstants.SEARCH_VERIFY,"iffresh"));
                sb.setConfig(SwitchboardConstants.SEARCH_VERIFY_DELETE, config.getProperty(SwitchboardConstants.SEARCH_VERIFY_DELETE,"true"));
                sb.setConfig("about.headline", config.getProperty("about.headline",""));
                sb.setConfig("about.body", config.getProperty("about.body",""));
                sb.setConfig("search.excludehosts", config.getProperty("search.excludehosts",""));
                sb.setConfig("search.excludehosth", config.getProperty("search.excludehosth",""));
            }

            if(cleanSearchCache) {
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
        prop.put(SwitchboardConstants.PUBLIC_SEARCHPAGE, sb.getConfigBool(SwitchboardConstants.PUBLIC_SEARCHPAGE, false) ? 1 : 0);
        prop.put("search.options", sb.getConfigBool("search.options", false) ? 1 : 0);
        prop.put(SwitchboardConstants.SEARCH_JS_RESORT, sb.getConfigBool(SwitchboardConstants.SEARCH_JS_RESORT, SwitchboardConstants.SEARCH_JS_RESORT_DEFAULT) ? 1 : 0);
		prop.put(SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM,
				sb.getConfigBool(SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM,
						SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM_DEFAULT) ? 1 : 0);

		prop.put(SwitchboardConstants.REMOTESEARCH_HTTPS_PREFERRED,
				sb.getConfigBool(SwitchboardConstants.REMOTESEARCH_HTTPS_PREFERRED,
						SwitchboardConstants.REMOTESEARCH_HTTPS_PREFERRED_DEFAULT) ? 1 : 0);

		final boolean textSnippetsStatisticsEnabled = sb.getConfigBool(
				SwitchboardConstants.DEBUG_SNIPPETS_STATISTICS_ENABLED,
				SwitchboardConstants.DEBUG_SNIPPETS_STATISTICS_ENABLED_DEFAULT);
		prop.put(SwitchboardConstants.DEBUG_SNIPPETS_STATISTICS_ENABLED, textSnippetsStatisticsEnabled);

        prop.put(SwitchboardConstants.GREEDYLEARNING_ACTIVE, sb.getConfigBool(SwitchboardConstants.GREEDYLEARNING_ACTIVE, false) ? 1 : 0);
        prop.put(SwitchboardConstants.GREEDYLEARNING_LIMIT_DOCCOUNT, sb.getConfig(SwitchboardConstants.GREEDYLEARNING_LIMIT_DOCCOUNT, "0"));

        prop.put(SwitchboardConstants.REMOTESEARCH_RESULT_STORE, sb.getConfigBool(SwitchboardConstants.REMOTESEARCH_RESULT_STORE, true) ? 1 : 0);
        final long resultStoredMaxSize = sb.getConfigLong(SwitchboardConstants.REMOTESEARCH_RESULT_STORE_MAXSIZE, -1);
        if(resultStoredMaxSize > 0) {
        	prop.put(SwitchboardConstants.REMOTESEARCH_RESULT_STORE_MAXSIZE, resultStoredMaxSize);
    	} else {
    		prop.put(SwitchboardConstants.REMOTESEARCH_RESULT_STORE_MAXSIZE, "");
    	}

        /* Provide some basic stats about text snippets generation time to help choosing snippet options */
        if(textSnippetsStatisticsEnabled) {
			final long totalSnippets = TextSnippet.statistics.getTotalSnippets();
			final long totalSnippetsInitTime = TextSnippet.statistics.getTotalInitTime();
			prop.put(SwitchboardConstants.DEBUG_SNIPPETS_STATISTICS_ENABLED + "_totalSnippets", totalSnippets);
			prop.put(SwitchboardConstants.DEBUG_SNIPPETS_STATISTICS_ENABLED + "_totalFromSolr",
					TextSnippet.statistics.getTotalFromSolr());
			prop.put(SwitchboardConstants.DEBUG_SNIPPETS_STATISTICS_ENABLED + "_totalFromCache",
					TextSnippet.statistics.getTotalFromCache());
			prop.put(SwitchboardConstants.DEBUG_SNIPPETS_STATISTICS_ENABLED + "_totalFromMetadata",
					TextSnippet.statistics.getTotalFromMetadata());
			prop.put(SwitchboardConstants.DEBUG_SNIPPETS_STATISTICS_ENABLED + "_totalFromWeb",
					TextSnippet.statistics.getTotalFromWeb());
			prop.put(SwitchboardConstants.DEBUG_SNIPPETS_STATISTICS_ENABLED + "_totalFailures",
					TextSnippet.statistics.getTotalFailures());
			prop.put(SwitchboardConstants.DEBUG_SNIPPETS_STATISTICS_ENABLED + "_snippetsMeanTime",
					formatDuration(totalSnippets > 0 ? totalSnippetsInitTime / totalSnippets : 0));

			prop.put(SwitchboardConstants.DEBUG_SNIPPETS_STATISTICS_ENABLED + "_snippetsMaxTime",
					formatDuration(TextSnippet.statistics.getMaxInitTime()));
        }

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

        prop.put("myContext", YaCyDefaultServlet.getContext(header, sb));
        return prop;
    }

    /**
     * @param durationValue a duration in milliseconds
     * @return the duration value formatted for display with its time unit
     */
	private static String formatDuration(final long durationValue) {
		final Duration duration = Duration.ofMillis(durationValue);

		final String formattedDuration;
		if(duration.getSeconds() > 0) {
			formattedDuration = duration.getSeconds() + "s";
		} else {
			formattedDuration = duration.toMillis() + "ms";
		}
		return formattedDuration;
	}

}
