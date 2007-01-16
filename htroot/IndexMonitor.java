// IndexMonitor.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last change: 09.03.2005
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// You must compile this file with
// javac -classpath .:../Classes Settings_p.java
// if the shell's current path is HTROOT

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.anomic.http.httpHeader;
import de.anomic.index.indexURLEntry;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class IndexMonitor {

    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	// return variable that accumulates replacements
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
	serverObjects prop = new serverObjects();
        
        int lines = 40;
        boolean showInit = false;
        boolean showExec = false;
        
        
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
        
        // check if authorization is needed and/or given
        if (((tabletype > 0) && (tabletype < 6)) ||
            (post.containsKey("clearlist")) ||
            (post.containsKey("deleteentry"))) {
            String authorization = ((String) header.get("Authorization", "xxxxxx"));
            if (authorization.length() != 0) {
                if (! sb.verifyAuthentication(header, true)){
                    // force log-in (again, because wrong password was given)
                    prop.put("AUTHENTICATE", "admin log-in");
                    return prop;
                }
            }else{
                // force log-in
                prop.put("AUTHENTICATE", "admin log-in");
                return prop;
            }
        }
        
        // custom number of lines
        if (post.containsKey("count")) {
            lines = Integer.parseInt(post.get("count", "40"));
        }
        
        // do the commands
        if (post.containsKey("clearlist")) sb.wordIndex.loadedURL.clearStack(tabletype);
        if (post.containsKey("deleteentry")) {
                String hash = post.get("hash", null);
                if (hash != null) {
                    // delete from database
                    sb.wordIndex.loadedURL.remove(hash);
                }
            }
        if (post.containsKey("moreIndexed")) {
            lines = Integer.parseInt(post.get("showIndexed", "40"));
        }
        if (post.get("si") != null) showInit = true;
        if (post.get("se") != null) showExec = true;
        
        // create table
        if (tabletype == 0) {
            prop.put("table", 2);
        } else if (sb.wordIndex.loadedURL.getStackSize(tabletype) == 0) {
            prop.put("table", 0);
        } else {
            prop.put("table", 1);
            if (lines > sb.wordIndex.loadedURL.getStackSize(tabletype)) lines = sb.wordIndex.loadedURL.getStackSize(tabletype);
            if (lines == sb.wordIndex.loadedURL.getStackSize(tabletype)) {
                prop.put("table_size", 0);
            } else {
                prop.put("table_size", 1);
                prop.put("table_size_count", lines);
            }
            prop.put("table_size_all", sb.wordIndex.loadedURL.getStackSize(tabletype));
            prop.put("table_feedbackpage", "IndexMonitor.html");
            prop.put("table_tabletype", tabletype);
            prop.put("table_showInit", (showInit) ? 1 : 0);
            prop.put("table_showExec", (showExec) ? 1 : 0);

            boolean dark = true;
            String urlHash, initiatorHash, executorHash;
            String cachepath, urlstr, urltxt;
            yacySeed initiatorSeed, executorSeed;
            indexURLEntry urle;

            // needed for getCachePath(url)
            final plasmaHTCache cacheManager = sb.getCacheManager();

            int i, cnt = 0;
            for (i = sb.wordIndex.loadedURL.getStackSize(tabletype) - 1; i >= (sb.wordIndex.loadedURL.getStackSize(tabletype) - lines); i--) {
                initiatorHash = sb.wordIndex.loadedURL.getInitiatorHash(tabletype, i);
                executorHash = sb.wordIndex.loadedURL.getExecutorHash(tabletype, i);
//              serverLog.logFinest("PLASMA", "plasmaCrawlLURL/genTableProps initiatorHash=" + initiatorHash + " executorHash=" + executorHash);
                urlHash = sb.wordIndex.loadedURL.getUrlHash(tabletype, i);
//              serverLog.logFinest("PLASMA", "plasmaCrawlLURL/genTableProps urlHash=" + urlHash);
                try {
                    urle = sb.wordIndex.loadedURL.load(urlHash, null);
                    indexURLEntry.Components comp = urle.comp();
//                  serverLog.logFinest("PLASMA", "plasmaCrawlLURL/genTableProps urle=" + urle.toString());
                    initiatorSeed = yacyCore.seedDB.getConnected(initiatorHash);
                    executorSeed = yacyCore.seedDB.getConnected(executorHash);

                    urlstr = comp.url().toNormalform();
                    urltxt = nxTools.shortenURLString(urlstr, 72); // shorten the string text like a URL
                    cachepath = cacheManager.getCachePath(new URL(urlstr)).toString().replace('\\', '/').substring(cacheManager.cachePath.toString().length() + 1);

                    prop.put("table_indexed_" + cnt + "_dark", (dark) ? 1 : 0);
                    prop.put("table_indexed_" + cnt + "_feedbackpage", "IndexMonitor.html");
                    prop.put("table_indexed_" + cnt + "_tabletype", tabletype);
                    prop.put("table_indexed_" + cnt + "_urlhash", urlHash);
                    prop.put("table_indexed_" + cnt + "_showInit", (showInit) ? 1 : 0);
                    prop.put("table_indexed_" + cnt + "_showInit_initiatorSeed", (initiatorSeed == null) ? "unknown" : initiatorSeed.getName());
                    prop.put("table_indexed_" + cnt + "_showExec", (showExec) ? 1 : 0);
                    prop.put("table_indexed_" + cnt + "_showExec_executorSeed", (executorSeed == null) ? "unknown" : executorSeed.getName());
                    prop.put("table_indexed_" + cnt + "_moddate", daydate(urle.moddate()));
                    prop.put("table_indexed_" + cnt + "_wordcount", urle.wordCount());
                    prop.put("table_indexed_" + cnt + "_urldescr", comp.descr());
                    prop.put("table_indexed_" + cnt + "_url", (cachepath == null) ? "-not-cached-" : "<a href=\"CacheAdmin_p.html?action=info&amp;path=" + cachepath + "\" class=\"small\" title=\"" + de.anomic.data.wikiCode.replaceXMLEntities(urlstr) + "\">" + de.anomic.data.wikiCode.replaceXMLEntities(urltxt) + "</a>");
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
