/**
 *  CrawlStartScanner_p
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 12.12.2010 at http://yacy.net
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


import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.Scanner;
import net.yacy.cora.protocol.Scanner.Access;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;

import de.anomic.data.WorkTables;
import de.anomic.search.SearchEventCache;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class CrawlStartScanner_p {
    
    private final static int CONCURRENT_RUNNER = 100;
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard)env;

        // clean up all search events
        SearchEventCache.cleanupEvents(true);
        
        prop.put("noserverdetected", 0);
        prop.put("servertable", 0);
        prop.put("hosts", "");
        prop.put("intranet.checked", sb.isIntranetMode() ? 1 : 0);

        int timeout = sb.isIntranetMode() ? 200 : 3000;
        timeout = post == null ? timeout : post.getInt("timeout", timeout);
        
        // make a scanhosts entry
        String hosts = post == null ? "" : post.get("scanhosts", "");
        Set<InetAddress> ips = Domains.myIntranetIPs();
        prop.put("intranethosts", ips.toString());
        prop.put("intranetHint", sb.isIntranetMode() ? 0 : 1);
        if (hosts.length() == 0) {
            InetAddress ip;
            if (sb.isIntranetMode()) {
                if (ips.size() > 0) ip = ips.iterator().next();
                else ip = Domains.dnsResolve("192.168.0.1");
            } else {
                ip = Domains.myPublicLocalIP();
                if (Domains.isThisHostIP(ip)) ip = sb.peers.mySeed().getInetAddress();
            }
            if (ip != null) hosts = ip.getHostAddress();
        }
        prop.put("scanhosts", hosts);
        
        // parse post requests
        if (post != null) {
            int repeat_time = 0;
            String repeat_unit = "seldays";
            long validTime = 0;

            // check scheduler
            if (post.get("rescan", "").equals("scheduler")) {
                repeat_time = post.getInt("repeat_time", -1);
                repeat_unit = post.get("repeat_unit", "selminutes"); // selminutes, selhours, seldays
                if (repeat_unit.equals("selminutes")) validTime = repeat_time * 60 * 1000;
                if (repeat_unit.equals("selhours")) validTime = repeat_time * 60 * 60 * 1000;
                if (repeat_unit.equals("seldays")) validTime = repeat_time * 24 * 60 * 60 * 1000;
            }
            
            boolean bigrange = post.getBoolean("bigrange", false);
            
            // case: an IP range was given; scan the range for services and display result
            if (post.containsKey("scan") && "hosts".equals(post.get("source", ""))) {
                final Set<InetAddress> ia = new HashSet<InetAddress>();
                for (String host : hosts.split(",")) {
                    if (host.startsWith("http://")) host = host.substring(7);
                    if (host.startsWith("https://")) host = host.substring(8);
                    if (host.startsWith("ftp://")) host = host.substring(6);
                    if (host.startsWith("smb://")) host = host.substring(6);
                    int p = host.indexOf('/');
                    if (p >= 0) host = host.substring(0, p);
                    ia.add(Domains.dnsResolve(host));
                }
                final Scanner scanner = new Scanner(ia, CONCURRENT_RUNNER, timeout);
                if (post.get("scanftp", "").equals("on")) scanner.addFTP(bigrange);
                if (post.get("scanhttp", "").equals("on")) scanner.addHTTP(bigrange);
                if (post.get("scanhttps", "").equals("on")) scanner.addHTTPS(bigrange);
                if (post.get("scansmb", "").equals("on")) scanner.addSMB(bigrange);
                scanner.start();
                scanner.terminate();
                if ("on".equals(post.get("accumulatescancache", "")) && !"scheduler".equals(post.get("rescan", ""))) {
                    Scanner.scancacheExtend(scanner, validTime);
                } else {
                    Scanner.scancacheReplace(scanner, validTime);
                }
            }
            
            if (post.containsKey("scan") && "intranet".equals(post.get("source", ""))) {
                final Scanner scanner = new Scanner(Domains.myIntranetIPs(), CONCURRENT_RUNNER, timeout);
                if ("on".equals(post.get("scanftp", ""))) scanner.addFTP(bigrange);
                if ("on".equals(post.get("scanhttp", ""))) scanner.addHTTP(bigrange);
                if ("on".equals(post.get("scanhttps", ""))) scanner.addHTTPS(bigrange);
                if ("on".equals(post.get("scansmb", ""))) scanner.addSMB(bigrange);
                scanner.start();
                scanner.terminate();
                if ("on".equals(post.get("accumulatescancache", "")) && !"scheduler".equals(post.get("rescan", ""))) {
                    Scanner.scancacheExtend(scanner, validTime);
                } else {
                    Scanner.scancacheReplace(scanner, validTime);
                }
            }
            
            // check crawl request
            if (post.containsKey("crawl")) {
                // make a pk/url mapping
                final Iterator<Map.Entry<Scanner.Service, Scanner.Access>> se = Scanner.scancacheEntries();
                final Map<byte[], DigestURI> pkmap = new TreeMap<byte[], DigestURI>(Base64Order.enhancedCoder);
                while (se.hasNext()) {
                    Scanner.Service u = se.next().getKey();
                    DigestURI uu;
                    try {
                        uu = new DigestURI(u.url());
                        pkmap.put(uu.hash(), uu);
                    } catch (MalformedURLException e) {
                        Log.logException(e);
                    }
                }
                // search for crawl start requests in this mapping
                for (final Map.Entry<String, String> entry: post.entrySet()) {
                    if (entry.getValue().startsWith("mark_")) {
                        byte [] pk = entry.getValue().substring(5).getBytes();
                        DigestURI url = pkmap.get(pk);
                        if (url != null) {
                            String path = "/Crawler_p.html?createBookmark=off&xsstopw=off&crawlingDomMaxPages=10000&intention=&range=domain&indexMedia=on&recrawl=nodoubles&xdstopw=off&storeHTCache=on&sitemapURL=&repeat_time=7&crawlingQ=on&cachePolicy=iffresh&indexText=on&crawlingMode=url&mustnotmatch=&crawlingDomFilterDepth=1&crawlingDomFilterCheck=off&crawlingstart=Start%20New%20Crawl&xpstopw=off&repeat_unit=seldays&crawlingDepth=99";
                            path += "&crawlingURL=" + url.toNormalform(true, false);
                            WorkTables.execAPICall("localhost", (int) sb.getConfigLong("port", 8090), sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, ""), path, pk);
                        }
                    }
                }
            }
            
            // check scheduler
            if ("scheduler".equals(post.get("rescan", ""))) {
                
                // store this call as api call
                if (repeat_time > 0) {
                    // store as scheduled api call
                    sb.tables.recordAPICall(post, "CrawlStartScanner_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "network scanner for hosts: " + hosts, repeat_time, repeat_unit.substring(3));
                }
                
                // execute the scan results
                if (Scanner.scancacheSize() > 0) {
                    // make a comment cache
                    final Map<byte[], String> apiCommentCache = WorkTables.commentCache(sb);
                    
                    String urlString;
                    DigestURI u;
                    try {
                        int i = 0;
                        final Iterator<Map.Entry<Scanner.Service, Scanner.Access>> se = Scanner.scancacheEntries();
                        Map.Entry<Scanner.Service, Scanner.Access> host;
                        while (se.hasNext()) {
                            host = se.next();
                            try {
                                u = new DigestURI(host.getKey().url());
                                urlString = u.toNormalform(true, false);
                                if (host.getValue() == Access.granted && Scanner.inIndex(apiCommentCache, urlString) == null) {
                                    String path = "/Crawler_p.html?createBookmark=off&xsstopw=off&crawlingDomMaxPages=10000&intention=&range=domain&indexMedia=on&recrawl=nodoubles&xdstopw=off&storeHTCache=on&sitemapURL=&repeat_time=7&crawlingQ=on&cachePolicy=iffresh&indexText=on&crawlingMode=url&mustnotmatch=&crawlingDomFilterDepth=1&crawlingDomFilterCheck=off&crawlingstart=Start%20New%20Crawl&xpstopw=off&repeat_unit=seldays&crawlingDepth=99";
                                    path += "&crawlingURL=" + urlString;
                                    WorkTables.execAPICall("localhost", (int) sb.getConfigLong("port", 8090), sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, ""), path, u.hash());
                                }
                                i++;
                            } catch (MalformedURLException e) {
                                Log.logException(e);
                            }
                        }
                    } catch (ConcurrentModificationException e) {}
                }
                
            }
        }
        
        // write scan table
        if (Scanner.scancacheSize() > 0) {
            // make a comment cache
            final Map<byte[], String> apiCommentCache = WorkTables.commentCache(sb);
            
            // show scancache table
            prop.put("servertable", 1);
            String urlString;
            DigestURI u;
            table: while (true) {
                try {
                    int i = 0;
                    final Iterator<Map.Entry<Scanner.Service, Scanner.Access>> se = Scanner.scancacheEntries();
                    Map.Entry<Scanner.Service, Scanner.Access> host;
                    while (se.hasNext()) {
                        host = se.next();
                        try {
                            u = new DigestURI(host.getKey().url());
                            urlString = u.toNormalform(true, false);
                            prop.put("servertable_list_" + i + "_pk", UTF8.String(u.hash()));
                            prop.put("servertable_list_" + i + "_count", i);
                            prop.putHTML("servertable_list_" + i + "_protocol", u.getProtocol());
                            prop.putHTML("servertable_list_" + i + "_ip", host.getKey().getInetAddress().getHostAddress());
                            prop.putHTML("servertable_list_" + i + "_url", urlString);
                            prop.put("servertable_list_" + i + "_accessUnknown", host.getValue() == Access.unknown ? 1 : 0);
                            prop.put("servertable_list_" + i + "_accessEmpty", host.getValue() == Access.empty ? 1 : 0);
                            prop.put("servertable_list_" + i + "_accessGranted", host.getValue() == Access.granted ? 1 : 0);
                            prop.put("servertable_list_" + i + "_accessDenied", host.getValue() == Access.denied ? 1 : 0);
                            prop.put("servertable_list_" + i + "_process", Scanner.inIndex(apiCommentCache, urlString) == null ? 0 : 1);
                            prop.put("servertable_list_" + i + "_preselected", host.getValue() == Access.granted && Scanner.inIndex(apiCommentCache, urlString) == null ? 1 : 0);
                            i++;
                        } catch (MalformedURLException e) {
                            Log.logException(e);
                        }
                    }
                    prop.put("servertable_list", i);
                    prop.put("servertable_num", i);
                    break table;
                } catch (ConcurrentModificationException e) {
                    continue table;
                }
            }
        }
        return prop;
    }
    
}
