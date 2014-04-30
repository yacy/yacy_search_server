// CrawlStartExpert_p.java
// (C) 2004 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 02.12.2004 as IndexCreate_p.java on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2010-08-23 14:32:02 +0200 (Mo, 23 Aug 2010) $
// $LastChangedRevision: 7068 $
// $LastChangedBy: orbiter $
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

import java.util.ArrayList;
import java.util.List;

import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class CrawlStartExpert {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final String defaultCollection = "user";

        // javascript values
        prop.put("matchAllStr", CrawlProfile.MATCH_ALL_STRING);
        prop.put("matchNoneStr", CrawlProfile.MATCH_NEVER_STRING);
        prop.put("defaultCollection", defaultCollection);

        // ---------- Start point
        // crawl start URL
        if (post != null && post.containsKey("crawlingURL")) {
            final String crawlingURL = post.get("crawlingURL", "");
            prop.put("starturl", crawlingURL);
            // simple check for content since it may be empty
            if (!crawlingURL.trim().isEmpty()) {
                prop.put("has_url", 1);
            }
        } else {
            prop.put("starturl", "");
        }
        
        // sitemap URL
        if (post != null && post.containsKey("sitemapURL")) {
            final String sitemapURL = post.get("sitemapURL", "");
            prop.put("sitemapURL", sitemapURL);
            // simple check for content since it may be empty
            if (!sitemapURL.trim().isEmpty()) {
                prop.put("has_sitemapURL", 1);
            }
        } else {
            prop.put("sitemapURL", "");
        }
        
        // crawling file
        if (post != null && post.containsKey("crawlingFile")) {
            final String crawlingFile = post.get("crawlingFile", "");
            prop.put("crawlingFile", crawlingFile);
            // simple check for content since it may be empty
            if (!crawlingFile.trim().isEmpty()) {
                prop.put("has_crawlingFile", 1);
            }
        } else {
            prop.put("crawlingFile", "");
        }

        // Crawling mode
        if (post != null && post.containsKey("crawlingMode")) {
            final String crawlingMode = post.get("crawlingMode", "");
            boolean hasMode = false;
            if (crawlingMode.equalsIgnoreCase("sitelist")
                    && prop.getBoolean("has_url")) {
                // sitelist needs "crawlingURL" parameter, checked already
                prop.put("crawlingMode_sitelist", 1);
                hasMode = true;
            } else if (crawlingMode.equalsIgnoreCase("sitemap")
                    && prop.getBoolean("has_sitemapURL")) {
                // sitemap needs "sitemapURL" parameter, checked already
                prop.put("crawlingMode_sitemap", 1);
                hasMode = true;
            } else if (crawlingMode.equalsIgnoreCase("file")
                    && prop.getBoolean("has_crawlingFile")) {
                // sitemap needs "crawlingFile" parameter, checked already
                prop.put("crawlingMode_file", 1);
                hasMode = true;
            } else if (crawlingMode.equalsIgnoreCase("url")
                    && prop.getBoolean("has_crawlingURL")) {
                prop.put("crawlingMode_url", 1);
                hasMode = true;
            }
            // try to guess mode
            if (!hasMode) {
                if (prop.getBoolean("has_url")) {
                    prop.put("crawlingMode_url", 1);
                } else if (prop.getBoolean("has_sitemapURL")) {
                    prop.put("crawlingMode_sitemap", 1);
                } else if (prop.getBoolean("has_crawlingFile")) {
                    prop.put("crawlingMode_file", 1);
                } else {
                    prop.put("crawlingMode_url", 1);
                }
            }
        } else {
            // default to URL
            prop.put("crawlingMode_url", 1);
        }


        // Bookmark title (set by script)
        if (post != null && post.containsKey("bookmarkTitle")) {
            prop.put("bookmarkTitle", post.get("bookmarkTitle", ""));
        } else {
            prop.put("bookmarkTitle", "");
        }
        

        // ---------- Crawling filter
        final int crawlingDomMaxPages = env.getConfigInt(
                "crawlingDomMaxPages", -1);
        
        // crawling depth
        if (post != null && post.containsKey("crawlingDepth")) {
            final Integer depth = post.getInt("crawlingDepth", -1);
            // depth is limited to two digits, zero allowed
            if (depth >= 0 && depth < 100) {
                prop.put("crawlingDepth", depth);
            }
        }
        if (!prop.containsKey("crawlingDepth")) {
            prop.put("crawlingDepth", Math.min(3,
                    env.getConfigLong("crawlingDepth", 0)));
        }

        // linked non-parseable documents?
        if (post == null) {
            prop.put("directDocByURLChecked",
                    sb.getConfigBool("crawlingDirectDocByURL", true) ? 1 : 0);
        } else {
            prop.put("directDocByURLChecked",
                    post.getBoolean("directDocByURL") ? 1 : 0);
        }

        // Unlimited crawl depth for URLs matching with
        if (post != null && post.containsKey("crawlingDepthExtension")) {
            prop.put("crawlingDepthExtension", 
                    post.get("crawlingDepthExtension", ""));
        } else {
            prop.put("crawlingDepthExtension", CrawlProfile.MATCH_NEVER_STRING);
        }

        // Limit by maximum Pages per Domain?
        if (post == null) {
            prop.put("crawlingDomMaxCheck",
                    (crawlingDomMaxPages == -1) ? 0 : 1);
        } else {
            prop.put("crawlingDomMaxCheck",
                    post.getBoolean("crawlingDomMaxCheck") ? 1 : 0);
        }

        // Maximum Pages per Domain
        if (post != null && post.containsKey("crawlingDomMaxPages")) {
            final Integer maxPages = post.getInt("crawlingDomMaxPages", -1);
            // depth is limited to six digits, zero not allowed
            if (maxPages > 0 && maxPages < 1000000) {
                prop.put("crawlingDomMaxPages", maxPages);
            }
        }
        if (!prop.containsKey("crawlingDomMaxPages")) {
            prop.put("crawlingDomMaxPages",
                    (crawlingDomMaxPages == -1) ? 10000 : crawlingDomMaxPages);
        }

        // Accept URLs with query-part?
        // Obey html-robots-noindex?
        if (post == null) {
            prop.put("crawlingQChecked",
                    env.getConfigBool("crawlingQ", true) ? 1 : 0);
            prop.put("obeyHtmlRobotsNoindexChecked",
                    env.getConfigBool("obeyHtmlRobotsNoindex", true) ? 1 : 0);
        } else {
            prop.put("crawlingQChecked", post.getBoolean("crawlingQ") ? 1 : 0);
            prop.put("obeyHtmlRobotsNoindexChecked",
                    post.getBoolean("obeyHtmlRobotsNoindex") ? 1 : 0);
        }

        // Load Filter on URLs (range)
        if (post != null && post.containsKey("range")) {
            final String range = post.get("range", "");
            if (range.equalsIgnoreCase("domain")) {
                prop.put("range_domain", 1);
            } else if (range.equalsIgnoreCase("subpath")) {
                prop.put("range_subpath", 1);
            } else if (range.equalsIgnoreCase("wide")) {
                prop.put("range_wide", 1);
            }
        } else {
            prop.put("range_wide", 1);
        }

        // Load Filter on URLs: must match
        if (post != null && post.containsKey("mustmatch")) {
            prop.put("mustmatch", post.get("mustmatch", ""));
        } else {
            prop.put("mustmatch", CrawlProfile.MATCH_ALL_STRING);
        }

        // Load Filter on URLs: must-not-match
        if (post != null && post.containsKey("mustnotmatch")) {
            prop.put("mustnotmatch", post.get("mustnotmatch", ""));
        } else {
            prop.put("mustnotmatch", CrawlProfile.MATCH_NEVER_STRING);
        }

        // Load Filter on IPs: must match
        if (post != null && post.containsKey("ipMustmatch")) {
            prop.put("ipMustmatch", post.get("ipMustmatch", ""));
        } else {
            prop.put("ipMustmatch", sb.getConfig("crawlingIPMustMatch",
                    CrawlProfile.MATCH_ALL_STRING));
        }

        // Load Filter on IPs: must-not-match
        if (post != null && post.containsKey("ipMustnotmatch")) {
            prop.put("ipMustnotmatch", post.get("ipMustnotmatch", ""));
        } else {
            prop.put("ipMustnotmatch", sb.getConfig("crawlingIPMustNotMatch",
                    CrawlProfile.MATCH_NEVER_STRING));
        }

        // Use Country Codes Match-List?
        if (post == null) {
            // use the default that was set in the original template
            prop.put("countryMustMatchSwitchChecked", 0);
        } else {
            prop.put("countryMustMatchSwitchChecked",
                    post.getBoolean("countryMustMatchSwitch") ? 1 : 0);
        }

        // Must-Match List for Country Codes
        if (post != null && post.containsKey("countryMustMatchList")) {
            prop.put("countryMustMatch", post.get("countryMustMatchList", ""));
        } else {
            prop.put("countryMustMatch",
                    sb.getConfig("crawlingCountryMustMatch", ""));
        }


        // ---------- Document filter
        // Indexer filter on URLs: must match
        if (post != null && post.containsKey("indexmustmatch")) {
            prop.put("indexmustmatch", post.get("indexmustmatch", ""));
        } else {
            prop.put("indexmustmatch", CrawlProfile.MATCH_ALL_STRING);
        }

        // Indexer filter on URLs: must-no-match
        if (post != null && post.containsKey("indexmustnotmatch")) {
            prop.put("indexmustnotmatch", post.get("indexmustnotmatch", ""));
        } else {
            prop.put("indexmustnotmatch", CrawlProfile.MATCH_NEVER_STRING);
        }

        // Filter on Content of Document: must match
        if (post != null && post.containsKey("indexcontentmustmatch")) {
            prop.put("indexcontentmustmatch", 
                    post.get("indexcontentmustmatch", ""));
        } else {
            prop.put("indexcontentmustmatch", CrawlProfile.MATCH_ALL_STRING);
        }

        // Filter on Content of Document: must-not-match
        if (post != null && post.containsKey("indexcontentmustnotmatch")) {
            prop.put("indexcontentmustnotmatch",
                    post.get("indexcontentmustnotmatch", ""));
        } else {
            prop.put("indexcontentmustnotmatch", CrawlProfile.MATCH_NEVER_STRING);
        }


        // ---------- Clean-Up before Crawl Start
        // delete if older settings: number value
        prop.put("deleteIfOlderSelect", 1);
        for (int i=0; i<13; i++) {
            prop.put("deleteIfOlderSelect_list_"+i+"_name", Integer.toString(i));
        }
        prop.put("deleteIfOlderSelect_list_13_name", "14");
        prop.put("deleteIfOlderSelect_list_14_name", "21");
        prop.put("deleteIfOlderSelect_list_15_name", "28");
        prop.put("deleteIfOlderSelect_list_16_name", "30");
        prop.put("deleteIfOlderSelect_list", 17);

        if (post != null && post.containsKey("deleteIfOlderNumber")) {
            final Integer olderNumber = post.getInt("deleteIfOlderNumber", -1);
            if (olderNumber >0 && olderNumber <= 12) {
                prop.put("deleteIfOlderSelect_list_" + olderNumber +
                        "_default", 1);
            } else {
                switch (olderNumber) {
                    case 21:
                        prop.put("deleteIfOlderSelect_list_14_default", 1);
                        break;
                    case 28:
                        prop.put("deleteIfOlderSelect_list_15_default", 1);
                        break;
                    case 30:
                        prop.put("deleteIfOlderSelect_list_16_default", 1);
                        break;
                    default:
                        prop.put("deleteIfOlderSelect_list_13_default", 1);
                        break;
                }
            }
        } else {
            prop.put("deleteIfOlderSelect_list_13_default", 1);
        }

        // delete if older settings: number unit
        prop.put("deleteIfOlderUnitSelect", 1);
        prop.put("deleteIfOlderUnitSelect_list_0_name", "years");
        prop.put("deleteIfOlderUnitSelect_list_0_value", "year");
        prop.put("deleteIfOlderUnitSelect_list_1_name", "months");
        prop.put("deleteIfOlderUnitSelect_list_1_value", "month");
        prop.put("deleteIfOlderUnitSelect_list_2_name", "days");
        prop.put("deleteIfOlderUnitSelect_list_2_value", "day");
        prop.put("deleteIfOlderUnitSelect_list_3_name", "hours");
        prop.put("deleteIfOlderUnitSelect_list_3_value", "hour");
        prop.put("deleteIfOlderUnitSelect_list", 4);

        if (post != null && post.containsKey("deleteIfOlderUnit")) {
            final String olderUnit = post.get("deleteIfOlderUnit", "");
            if (olderUnit.equalsIgnoreCase("year")) {
                prop.put("deleteIfOlderUnitSelect_list_0_default", 1);
            } else if (olderUnit.equalsIgnoreCase("month")) {
                prop.put("deleteIfOlderUnitSelect_list_1_default", 1);
            } else if (olderUnit.equalsIgnoreCase("hour")) {
                prop.put("deleteIfOlderUnitSelect_list_3_default", 1);
            } else {
                prop.put("deleteIfOlderUnitSelect_list_2_default", 1);
            }
        } else {
            prop.put("deleteIfOlderUnitSelect_list_2_default", 1);
        }

        // delete any document before the crawl is started?
        if (post != null && post.containsKey("deleteold")) {
            final String deleteold = post.get("deleteold", "");
            if (deleteold.equalsIgnoreCase("on")){
                prop.put("deleteold_on", 1);
            } else if (deleteold.equalsIgnoreCase("age")) {
                prop.put("deleteold_age", 1);
            } else {
                prop.put("deleteold_off", 1);
            }
        } else {
            prop.put("deleteold_off", 1);
        }


        // ---------- Double-Check Rules
        // reload settings: number value
        prop.put("reloadIfOlderSelect", 1);
        for (int i=0; i<13; i++) {
            prop.put("reloadIfOlderSelect_list_"+i+"_name", Integer.toString(i));
        }
        prop.put("reloadIfOlderSelect_list_13_name", "14");
        prop.put("reloadIfOlderSelect_list_14_name", "21");
        prop.put("reloadIfOlderSelect_list_15_name", "28");
        prop.put("reloadIfOlderSelect_list_16_name", "30");
        prop.put("reloadIfOlderSelect_list", 17);

        if (post != null && post.containsKey("reloadIfOlderNumber")) {
            final Integer olderNumber = post.getInt("reloadIfOlderNumber", -1);
            if (olderNumber >0 && olderNumber <= 12) {
                prop.put("reloadIfOlderSelect_list_" + olderNumber +
                        "_default", 1);
            } else {
                switch (olderNumber) {
                    case 21:
                        prop.put("reloadIfOlderSelect_list_14_default", 1);
                        break;
                    case 28:
                        prop.put("reloadIfOlderSelect_list_15_default", 1);
                        break;
                    case 30:
                        prop.put("reloadIfOlderSelect_list_16_default", 1);
                        break;
                    default:
                        prop.put("reloadIfOlderSelect_list_13_default", 1);
                        break;
                }
            }
        } else {
            prop.put("reloadIfOlderSelect_list_13_default", 1);
        }

        // reload settings: number unit
        prop.put("reloadIfOlderUnitSelect", 1);
        prop.put("reloadIfOlderUnitSelect_list_0_name", "years");
        prop.put("reloadIfOlderUnitSelect_list_0_value", "year");
        prop.put("reloadIfOlderUnitSelect_list_1_name", "months");
        prop.put("reloadIfOlderUnitSelect_list_1_value", "month");
        prop.put("reloadIfOlderUnitSelect_list_2_name", "days");
        prop.put("reloadIfOlderUnitSelect_list_2_value", "day");
        prop.put("reloadIfOlderUnitSelect_list_3_name", "hours");
        prop.put("reloadIfOlderUnitSelect_list_3_value", "hour");
        prop.put("reloadIfOlderUnitSelect_list", 4);

        if (post != null && post.containsKey("reloadIfOlderUnit")) {
            final String olderUnit = post.get("reloadIfOlderUnit", "");
            if (olderUnit.equalsIgnoreCase("year")) {
                prop.put("reloadIfOlderUnitSelect_list_0_default", 1);
            } else if (olderUnit.equalsIgnoreCase("month")) {
                prop.put("reloadIfOlderUnitSelect_list_1_default", 1);
            } else if (olderUnit.equalsIgnoreCase("hour")) {
                prop.put("reloadIfOlderUnitSelect_list_3_default", 1);
            } else {
                prop.put("reloadIfOlderUnitSelect_list_2_default", 1);
            }
        } else {
            prop.put("reloadIfOlderUnitSelect_list_2_default", 1);
        }

        if (post != null && post.containsKey("recrawl")) {
            final String recrawl = post.get("recrawl", "");
            if (recrawl.equalsIgnoreCase("reload")) {
                prop.put("recrawl_reload", 1);
            } else {
                prop.put("recrawl_nodoubles", 1);
            }
        } else {
            prop.put("recrawl_nodoubles", 1);
        }


        // ---------- Document Cache
        // Store to Web Cache?
        if (post == null) {
            prop.put("storeHTCacheChecked",
                    env.getConfigBool("storeHTCache", true) ? 1 : 0);
        } else {
            prop.put("storeHTCacheChecked",
                    post.getBoolean("storeHTCache") ? 1 : 0);
        }
        
        // Policy for usage of Web Cache
        if (post != null && post.containsKey("cachePolicy")) {
            final String cachePolicy = post.get("cachePolicy", "");
            if (cachePolicy.equalsIgnoreCase("nocache")) {
                prop.put("cachePolicy_nocache", 1);
            } else if (cachePolicy.equalsIgnoreCase("ifexist")) {
                prop.put("cachePolicy_ifexist", 1);
            } else if (cachePolicy.equalsIgnoreCase("cacheonly")) {
                prop.put("cachePolicy_cacheonly", 1);
            } else {
                prop.put("cachePolicy_iffresh", 1);
            }
        } else {
            prop.put("cachePolicy_iffresh", 1);
        }

        // ---------- Agent name
        if (sb.isP2PMode()) {
            prop.put("agentSelect", 0);
        } else {
            prop.put("agentSelect", 1);
            List<String> agentNames = new ArrayList<String>();
            if (sb.isIntranetMode()) {
                agentNames.add(ClientIdentification.yacyIntranetCrawlerAgentName);
            }
            if (sb.isGlobalMode()) {
                agentNames.add(ClientIdentification.yacyInternetCrawlerAgentName);
            }
            agentNames.add(ClientIdentification.googleAgentName);
            if (sb.isAllIPMode()) {
                agentNames.add(ClientIdentification.browserAgentName);
            }
            String defaultAgentName = agentNames.get(0);
            if (post != null && post.containsKey("agentName")) {
                String agentName = post.get("agentName", sb.isIntranetMode() ? ClientIdentification.yacyIntranetCrawlerAgentName : ClientIdentification.yacyInternetCrawlerAgentName);
                if (agentNames.contains(agentName)) defaultAgentName = agentName;
            }
            for (int i = 0; i < agentNames.size(); i++) {
                prop.put("agentSelect_list_" + i + "_name", agentNames.get(i));
                prop.put("agentSelect_list_" + i + "_default", agentNames.get(i).equals(defaultAgentName) ? 1 : 0);
            }
            prop.put("agentSelect_list", agentNames.size());

        }
        prop.put("agentSelect_defaultAgentName",
                ClientIdentification.yacyInternetCrawlerAgentName);


        // ---------- Index Administration
        // Do Local Indexing
        if (post == null) {
            // Local index text?
            prop.put("indexingTextChecked",
                    env.getConfigBool("indexText", true) ? 1 : 0);
            // Local index media?
            prop.put("indexingMediaChecked",
                    env.getConfigBool("indexMedia", true) ? 1 : 0);
            // Do Remote Indexing?
            prop.put("crawlOrderChecked",
                    env.getConfigBool("crawlOrder", true) ? 1 : 0);
            // Remote crawl intention
            prop.put("intention", "");
        } else {
            prop.put("indexingTextChecked",
                    post.getBoolean("indexText") ? 1 : 0);
            prop.put("indexingMediaChecked",
                    post.getBoolean("indexMedia") ? 1 : 0);
            prop.put("crawlOrderChecked",
                    post.getBoolean("crawlOrder") ? 1 : 0);
            prop.put("intention", post.get("intention", ""));
        }

        // Target collection
        boolean collectionEnabled =
                sb.index.fulltext().getDefaultConfiguration().isEmpty() ||
                sb.index.fulltext().getDefaultConfiguration().contains(
                    CollectionSchema.collection_sxt);
        prop.put("collectionEnabled", collectionEnabled ? 1 : 0);
        if (collectionEnabled) {
            if (post != null && post.containsKey("collection")) {
                prop.put("collection", post.get("collection", ""));
            } else {
                prop.put("collection", collectionEnabled ? defaultCollection : "");
            }
        }

        // return rewrite properties
        return prop;
    }
}
