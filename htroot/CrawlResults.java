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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.anomic.http.httpHeader;
import de.anomic.index.indexURLReference;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public class CrawlResults {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();

        int lines = 500;
        boolean showControl = env.getConfigBool("IndexMonitorControl", true);
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
        } catch (NumberFormatException e) {
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
            String authorization = ((String) header.get(httpHeader.AUTHORIZATION, "xxxxxx"));
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
                String hash = post.get("hash", null);
                if (hash != null) {
                    // delete from database
                    sb.webIndex.removeURL(hash);
                }
            }
        if (post.containsKey("moreIndexed")) {
            lines = Integer.parseInt(post.get("showIndexed", "500"));
        }
        if (post.get("sc") != null)
            if (post.get("sc").equals("0")) showControl = false; else showControl = true;
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
        } else if (sb.crawlResults.getStackSize(tabletype) == 0) {
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
            
            if (showControl) {
                prop.put("table_showControl", "1");
                prop.putHTML("table_showControl_feedbackpage", "CrawlResults.html");
                prop.put("table_showControl_tabletype", tabletype);
            } else
                prop.put("table_showControl", "0");
            prop.put("table_showInit", (showInit) ? "1" : "0");
            prop.put("table_showExec", (showExec) ? "1" : "0");
            prop.put("table_showDate", (showDate) ? "1" : "0");
            prop.put("table_showWords", (showWords) ? "1" : "0");
            prop.put("table_showTitle", (showTitle) ? "1" : "0");
            prop.put("table_showURL", (showURL) ? "1" : "0");

            boolean dark = true;
            String urlHash, initiatorHash, executorHash;
            String cachepath, urlstr, urltxt;
            yacySeed initiatorSeed, executorSeed;
            indexURLReference urle;

            int i, cnt = 0;
            for (i = sb.crawlResults.getStackSize(tabletype) - 1; i >= (sb.crawlResults.getStackSize(tabletype) - lines); i--) {
                initiatorHash = sb.crawlResults.getInitiatorHash(tabletype, i);
                executorHash = sb.crawlResults.getExecutorHash(tabletype, i);
//              serverLog.logFinest("PLASMA", "plasmaCrawlLURL/genTableProps initiatorHash=" + initiatorHash + " executorHash=" + executorHash);
                urlHash = sb.crawlResults.getUrlHash(tabletype, i);
//              serverLog.logFinest("PLASMA", "plasmaCrawlLURL/genTableProps urlHash=" + urlHash);
                try {
                    urle = sb.webIndex.getURL(urlHash, null, 0);
                    if(urle == null) {
                        serverLog.logWarning("PLASMA", "CrawlResults: URL not in index for crawl result "+ i +" with hash "+ urlHash);
                        urlstr = null;
                        urltxt = null;
                        cachepath = null;
                    } else {
                        indexURLReference.Components comp = urle.comp();
                        urlstr = comp.url().toNormalform(false, true);
                        urltxt = nxTools.shortenURLString(urlstr, 72); // shorten the string text like a URL
                        cachepath = plasmaHTCache.getCachePath(new yacyURL(urlstr, null)).toString().replace('\\', '/').substring(plasmaHTCache.cachePath.toString().length() + 1);
                    }
//                  serverLog.logFinest("PLASMA", "plasmaCrawlLURL/genTableProps urle=" + urle.toString());
                    initiatorSeed = sb.webIndex.seedDB.getConnected(initiatorHash);
                    executorSeed = sb.webIndex.seedDB.getConnected(executorHash);

                    prop.put("table_indexed_" + cnt + "_dark", (dark) ? "1" : "0");
                    if (showControl) {
                        prop.put("table_indexed_" + cnt + "_showControl", "1");
                        prop.put("table_indexed_" + cnt + "_showControl_feedbackpage", "CrawlResults.html");
                        prop.put("table_indexed_" + cnt + "_showControl_tabletype", tabletype);
                        prop.put("table_indexed_" + cnt + "_showControl_urlhash", urlHash);
                    } else
                        prop.put("table_indexed_" + cnt + "_showControl", "0");

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

                    if (showDate) {
                        prop.put("table_indexed_" + cnt + "_showDate", "1");
                        prop.put("table_indexed_" + cnt + "_showDate_modified", daydate(urle.moddate()));
                    } else
                        prop.put("table_indexed_" + cnt + "_showDate", "0");

                    if (showWords) {
                        prop.put("table_indexed_" + cnt + "_showWords", "1");
                        prop.put("table_indexed_" + cnt + "_showWords_count", urle.wordCount());
                    } else
                        prop.put("table_indexed_" + cnt + "_showWords", "0");

                    if (showTitle) {
                        prop.put("table_indexed_" + cnt + "_showTitle", (showTitle) ? "1" : "0");
                        if (cachepath == null) {
                            prop.put("table_indexed_" + cnt + "_showTitle_available", "0");
                        } else {
                            prop.put("table_indexed_" + cnt + "_showTitle_available", "1");

                            if (comp.dc_title() == null || comp.dc_title().trim().length() == 0)
                                prop.put("table_indexed_" + cnt + "_showTitle_available_nodescr", "0");
                            else
                                prop.put("table_indexed_" + cnt + "_showTitle_available_nodescr", "1");
                            prop.putHTML("table_indexed_" + cnt + "_showTitle_available_nodescr_urldescr", comp.dc_title());

                            prop.put("table_indexed_" + cnt + "_showTitle_available_cachepath", cachepath);
                            prop.putHTML("table_indexed_" + cnt + "_showTitle_available_urltitle", urlstr);
                        }
                    } else
                        prop.put("table_indexed_" + cnt + "_showTitle", "0");

                    if (showURL) {
                        prop.put("table_indexed_" + cnt + "_showURL", "1");
                        if (cachepath == null) {
                            prop.put("table_indexed_" + cnt + "_showURL_available", "0");
                        } else {
                            prop.put("table_indexed_" + cnt + "_showURL_available", "1");

                            prop.put("table_indexed_" + cnt + "_showURL_available_cachepath", cachepath);
                            prop.putHTML("table_indexed_" + cnt + "_showURL_available_urltitle", urlstr);
                            prop.put("table_indexed_" + cnt + "_showURL_available_url", urltxt);
                        }
                    } else
                        prop.put("table_indexed_" + cnt + "_showURL", "0");

                    dark = !dark;
                    cnt++;
                } catch (Exception e) {
                    serverLog.logSevere("PLASMA", "genTableProps", e);
                }
            }
            prop.put("table_indexed", cnt);
        }
        prop.put("process", tabletype);
        // return rewrite properties
        return prop;
    }

    private static SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
    private static String daydate(Date date) {
        if (date == null) {
            return "";
        } else {
            return dayFormatter.format(date);
        }
    }
}
