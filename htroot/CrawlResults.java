// CrawlResults.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 09.03.2005 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import de.anomic.http.httpRequestHeader;
import de.anomic.index.indexURLReference;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public class CrawlResults {

    public static serverObjects respond(final httpRequestHeader header, serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();

        int lines = 500;
        boolean showInit = env.getConfigBool("IndexMonitorInit", false);
        boolean showExec = env.getConfigBool("IndexMonitorExec", false);
        boolean showDate = env.getConfigBool("IndexMonitorDate", true);
        boolean showWords = env.getConfigBool("IndexMonitorWords", true);
        boolean showTitle = env.getConfigBool("IndexMonitorTitle", true);
        boolean showURL = env.getConfigBool("IndexMonitorURL", true);

        if (post == null) {
            post = new serverObjects();
            post.put("process", "0");
        }

        // find process number
        int tabletype;
        try {
            tabletype = Integer.parseInt(post.get("process", "0"));
        } catch (final NumberFormatException e) {
            tabletype = 0;
        }

        if ((post != null) && (post.containsKey("autoforward")) && (tabletype == 5) && (sb.crawlResults.getStackSize(5) == 0)) {
            // the main menu does a request to the local crawler page, but in case this table is empty, the overview page is shown
            tabletype = 0;
        }
        
        // check if authorization is needed and/or given
        if (((tabletype > 0) && (tabletype < 6)) ||
            (post != null && (post.containsKey("clearlist") ||
            post.containsKey("deleteentry")))) {
            final String authorization = ((String) header.get(httpRequestHeader.AUTHORIZATION, "xxxxxx"));
            if (authorization.length() != 0) {
                if (! sb.verifyAuthentication(header, true)){
                    // force log-in (again, because wrong password was given)
                    prop.put("AUTHENTICATE", "admin log-in");
                    return prop;
                }
            } else {
                // force log-in
                prop.put("AUTHENTICATE", "admin log-in");
                return prop;
            }
        }

        if(post != null) {
        // custom number of lines
        if (post.containsKey("count")) {
            lines = Integer.parseInt(post.get("count", "500"));
        }

        // do the commands
        if (post.containsKey("clearlist")) sb.crawlResults.clearStack(tabletype);
        
        if (post.containsKey("deleteentry")) {
            final String hash = post.get("hash", null);
            if (hash != null) {
                // delete from database
                sb.webIndex.removeURL(hash);
            }
        }
        
        if (post.containsKey("deletedomain")) {
            final String hashpart = post.get("hashpart", null);
            final String domain = post.get("domain", null);
            if (hashpart != null) {
                // delete all urls for this domain from database
                try {
                    sb.webIndex.deleteDomain(hashpart);
                    sb.crawlResults.deleteDomain(tabletype, domain, hashpart);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        if (post.containsKey("moreIndexed")) {
            lines = Integer.parseInt(post.get("showIndexed", "500"));
        }
        
        if (post.get("si") != null)
            if (post.get("si").equals("0")) showInit = false; else showInit = true;
        if (post.get("se") != null)
            if (post.get("se").equals("0")) showExec = false; else showExec = true;
        if (post.get("sd") != null)
            if (post.get("sd").equals("0")) showDate = false; else showDate = true;
        if (post.get("sw") != null)
            if (post.get("sw").equals("0")) showWords = false; else showWords = true;
        if (post.get("st") != null)
            if (post.get("st").equals("0")) showTitle = false; else showTitle = true;
        if (post.get("su") != null)
            if (post.get("su").equals("0")) showURL = false; else showURL = true;
        } // end != null

        // create table
        if (tabletype == 0) {
            prop.put("table", "2");
        } else if (sb.crawlResults.getStackSize(tabletype) == 0 && sb.crawlResults.getDomainListSize(tabletype) == 0) {
            prop.put("table", "0");
        } else {
            prop.put("table", "1");
            if (lines > sb.crawlResults.getStackSize(tabletype)) lines = sb.crawlResults.getStackSize(tabletype);
            if (lines == sb.crawlResults.getStackSize(tabletype)) {
                prop.put("table_size", "0");
            } else {
                prop.put("table_size", "1");
                prop.put("table_size_count", lines);
            }
            prop.put("table_size_all", sb.crawlResults.getStackSize(tabletype));
            
            prop.putHTML("table_feedbackpage", "CrawlResults.html");
            prop.put("table_tabletype", tabletype);
            prop.put("table_showInit", (showInit) ? "1" : "0");
            prop.put("table_showExec", (showExec) ? "1" : "0");
            prop.put("table_showDate", (showDate) ? "1" : "0");
            prop.put("table_showWords", (showWords) ? "1" : "0");
            prop.put("table_showTitle", (showTitle) ? "1" : "0");
            prop.put("table_showURL", (showURL) ? "1" : "0");

            boolean dark = true;
            String urlHash, initiatorHash, executorHash;
            String urlstr, urltxt;
            yacySeed initiatorSeed, executorSeed;
            indexURLReference urle;
            indexURLReference.Components comp;

            int i, cnt = 0;
            for (i = sb.crawlResults.getStackSize(tabletype) - 1; i >= (sb.crawlResults.getStackSize(tabletype) - lines); i--) {
                initiatorHash = sb.crawlResults.getInitiatorHash(tabletype, i);
                executorHash = sb.crawlResults.getExecutorHash(tabletype, i);
                urlHash = sb.crawlResults.getUrlHash(tabletype, i);
                try {
                    urle = sb.webIndex.getURL(urlHash, null, 0);
                    if(urle == null) {
                        serverLog.logWarning("PLASMA", "CrawlResults: URL not in index for crawl result "+ i +" with hash "+ urlHash);
                        urlstr = null;
                        urltxt = null;
                        comp = null;
                    } else {
                        comp = urle.comp();
                        urlstr = comp.url().toNormalform(false, true);
                        urltxt = nxTools.shortenURLString(urlstr, 72); // shorten the string text like a URL
                    }
                    initiatorSeed = sb.webIndex.seedDB.getConnected(initiatorHash);
                    executorSeed = sb.webIndex.seedDB.getConnected(executorHash);

                    prop.put("table_indexed_" + cnt + "_dark", (dark) ? "1" : "0");
                    prop.put("table_indexed_" + cnt + "_feedbackpage", "CrawlResults.html");
                    prop.put("table_indexed_" + cnt + "_tabletype", tabletype);
                    prop.put("table_indexed_" + cnt + "_urlhash", urlHash);

                    if (showInit) {
                        prop.put("table_indexed_" + cnt + "_showInit", "1");
                        prop.put("table_indexed_" + cnt + "_showInit_initiatorSeed", (initiatorSeed == null) ? "unknown" : initiatorSeed.getName());
                    } else
                        prop.put("table_indexed_" + cnt + "_showInit", "0");

                    if (showExec) {
                        prop.put("table_indexed_" + cnt + "_showExec", "1");
                        prop.put("table_indexed_" + cnt + "_showExec_executorSeed", (executorSeed == null) ? "unknown" : executorSeed.getName());
                    } else
                        prop.put("table_indexed_" + cnt + "_showExec", "0");

                    if (showDate && urle != null) {
                        prop.put("table_indexed_" + cnt + "_showDate", "1");
                        prop.put("table_indexed_" + cnt + "_showDate_modified", daydate(urle.moddate()));
                    } else
                        prop.put("table_indexed_" + cnt + "_showDate", "0");

                    if (showWords && urle != null) {
                        prop.put("table_indexed_" + cnt + "_showWords", "1");
                        prop.put("table_indexed_" + cnt + "_showWords_count", urle.wordCount());
                    } else
                        prop.put("table_indexed_" + cnt + "_showWords", "0");

                    if (showTitle) {
                        prop.put("table_indexed_" + cnt + "_showTitle", (showTitle) ? "1" : "0");
                            prop.put("table_indexed_" + cnt + "_showTitle_available", "1");

                            if (comp == null || comp.dc_title() == null || comp.dc_title().trim().length() == 0)
                                prop.put("table_indexed_" + cnt + "_showTitle_available_nodescr", "0");
                            else {
                                prop.put("table_indexed_" + cnt + "_showTitle_available_nodescr", "1");
                                prop.putHTML("table_indexed_" + cnt + "_showTitle_available_nodescr_urldescr", comp.dc_title());
                            }

                            prop.put("table_indexed_" + cnt + "_showTitle_available_urlHash", urlHash);
                            prop.putHTML("table_indexed_" + cnt + "_showTitle_available_urltitle", urlstr);
                    } else
                        prop.put("table_indexed_" + cnt + "_showTitle", "0");

                    if (showURL) {
                        prop.put("table_indexed_" + cnt + "_showURL", "1");
                            prop.put("table_indexed_" + cnt + "_showURL_available", "1");

                            prop.put("table_indexed_" + cnt + "_showURL_available_urlHash", urlHash);
                            prop.putHTML("table_indexed_" + cnt + "_showURL_available_urltitle", urlstr);
                            prop.put("table_indexed_" + cnt + "_showURL_available_url", urltxt);
                    } else
                        prop.put("table_indexed_" + cnt + "_showURL", "0");

                    dark = !dark;
                    cnt++;
                } catch (final Exception e) {
                    serverLog.logSevere("PLASMA", "genTableProps", e);
                }
            }
            prop.put("table_indexed", cnt);
            
            cnt = 0;
            dark = true;
            Iterator<String> j = sb.crawlResults.domains(tabletype);
            String domain;
            while (j.hasNext() && cnt < 100) {
                domain = j.next();
                if (domain == null) break;
                prop.put("table_domains_" + cnt + "_dark", (dark) ? "1" : "0");
                prop.put("table_domains_" + cnt + "_feedbackpage", "CrawlResults.html");
                prop.put("table_domains_" + cnt + "_tabletype", tabletype);
                prop.put("table_domains_" + cnt + "_domain", domain);
                prop.put("table_domains_" + cnt + "_hashpart", yacyURL.hosthash6(domain));
                prop.put("table_domains_" + cnt + "_count", sb.crawlResults.domainCount(tabletype, domain));
                dark = !dark;
                cnt++;
            }
            prop.put("table_domains", cnt);
        }
        prop.put("process", tabletype);
        // return rewrite properties
        return prop;
    }

    private static SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
    private static String daydate(final Date date) {
        if (date == null) {
            return "";
        }
        return dayFormatter.format(date);
    }
}
