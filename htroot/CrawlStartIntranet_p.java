/**
 *  CrawlStartIntranet_p
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 28.10.2010 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */


import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.Scanner;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;

import de.anomic.data.WorkTables;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class CrawlStartIntranet_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard)env;

        prop.put("notintranet", 0);
        prop.put("servertable", 0);
        
        // check if there is a intranet configuration
        if (!sb.isIntranetMode()) {
            prop.put("notintranet", 1);
            return prop;
        }
        
        // if there are no intranet addresses known, scan the net
        if (sb.intranetURLs.size() == 0) {
            Scanner scanner = new Scanner(100, 10);
            scanner.addFTP(false);
            scanner.addHTTP(false);
            scanner.addHTTPS(false);
            scanner.addSMB(false);
            scanner.start();
            scanner.terminate();
            DigestURI url;
            for (MultiProtocolURI service: scanner.services()) {
                url = new DigestURI(service);
                sb.intranetURLs.put(url.hash(), url);
            }
        }
        
        // check crawl request
        if (post != null && post.containsKey("crawl")) {
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getValue().startsWith("mark_")) {
                    byte [] pk = entry.getValue().substring(5).getBytes();
                    DigestURI url = sb.intranetURLs.get(pk);
                    if (url != null) {
                        String path = "/Crawler_p.html?createBookmark=off&xsstopw=off&crawlingDomMaxPages=10000&intention=&range=domain&indexMedia=on&recrawl=nodoubles&xdstopw=off&storeHTCache=on&sitemapURL=&repeat_time=7&crawlingQ=on&cachePolicy=iffresh&indexText=on&crawlingMode=url&mustnotmatch=&crawlingDomFilterDepth=1&crawlingDomFilterCheck=off&crawlingstart=Start%20New%20Crawl&xpstopw=off&repeat_unit=seldays&crawlingDepth=99";
                        path += "&crawlingURL=" + url.toNormalform(true, false);
                        WorkTables.execAPICall("localhost", (int) sb.getConfigLong("port", 8080), sb.getConfig("adminAccountBase64MD5", ""), path, pk);
                    }
                }
            }
        }
        
        // show server table
        prop.put("servertable", 1);
        int i = 0;
        String urlString;
        for (final DigestURI url: sb.intranetURLs.values()) {
            urlString = url.toNormalform(true, false);
            prop.put("servertable_list_" + i + "_pk", new String(url.hash()));
            prop.put("servertable_list_" + i + "_count", i);
            prop.putHTML("servertable_list_" + i + "_ip", Domains.dnsResolve(url.getHost()).getHostAddress());
            prop.putHTML("servertable_list_" + i + "_url", urlString);
            prop.put("servertable_list_" + i + "_process", inIndex(sb, urlString) == null ? 0 : 1);
            i++;
        }
        prop.put("servertable_list", i);
        prop.put("servertable_num", i);
        return prop;
    }
    
    private static byte[] inIndex(Switchboard sb, String url) {
        Iterator<Tables.Row> i;
        try {
            i = sb.tables.iterator(WorkTables.TABLE_API_NAME);
            Tables.Row row;
            String comment;
            while (i.hasNext()) {
                row = i.next();
                comment = new String(row.get(WorkTables.TABLE_API_COL_COMMENT));
                if (comment.contains(url)) return row.getPK();
            }
            return null;
        } catch (IOException e) {
            Log.logException(e);
            return null;
        }
    }
    
}
