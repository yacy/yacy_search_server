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


import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.Scanner;
import net.yacy.cora.protocol.Scanner.Access;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;

import de.anomic.data.WorkTables;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class CrawlStartScanner_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard)env;

        prop.put("selectiprange", 0);
        prop.put("noserverdetected", 0);
        prop.put("enterrange", 0);
        prop.put("servertable", 0);
        prop.put("enterrange_host", "");
        
        // make a comment cache
        Map<byte[], String> apiCommentCache = commentCache(sb);
        
        addSelectIPRange(sb, prop);
        addScantable(apiCommentCache, prop);
        
        // case: no query part of the request; ask for input
        if (post == null) {
            prop.put("selectiprange", 1);
            return prop;
        }
        
        // case: an IP range was given; scan the range for services and display result
        if (post.containsKey("scanip") || post.containsKey("scanhost")) {
            InetAddress ia;
            try {
                if (post.containsKey("scanip")) {
                    ia = InetAddress.getByAddress(new byte[]{(byte) post.getInt("ip4-0", 0), (byte) post.getInt("ip4-1", 0), (byte) post.getInt("ip4-2", 0), (byte) post.getInt("ip4-3", 0)});
                } else {
                    String host = post.get("scanhost", "");
                    if (host.startsWith("http://")) host = host.substring(7);
                    if (host.startsWith("https://")) host = host.substring(8);
                    if (host.startsWith("ftp://")) host = host.substring(6);
                    if (host.startsWith("smb://")) host = host.substring(6);
                    int p = host.indexOf('/');
                    if (p >= 0) host = host.substring(0, p);
                    ia = InetAddress.getByName(host);
                    prop.put("enterrange_host", host);
                }
                addSelectIPRange(ia, prop);
                Scanner scanner = new Scanner(ia, 100, sb.isIntranetMode() ? 100 : 3000);
                scanner.addFTP(false);
                scanner.addHTTP(false);
                scanner.addHTTPS(false);
                scanner.addSMB(false);
                scanner.start();
                scanner.terminate();
                enlargeScancache(apiCommentCache, scanner);
                addScantable(apiCommentCache, prop);
            } catch (UnknownHostException e) {}
        }
        
        if (post.containsKey("scanintranet")) {
            Scanner scanner = new Scanner(Domains.myIntranetIPs(), 100, sb.isIntranetMode() ? 100 : 3000);
            scanner.addFTP(false);
            scanner.addHTTP(false);
            scanner.addHTTPS(false);
            scanner.addSMB(false);
            scanner.start();
            scanner.terminate();
            enlargeScancache(apiCommentCache, scanner);
            addScantable(apiCommentCache, prop);
        }
        
        // check crawl request
        if (post != null && post.containsKey("crawl")) {
            // make a pk/url mapping
            Map<byte[], DigestURI> pkmap = new TreeMap<byte[], DigestURI>(Base64Order.enhancedCoder);
            for (MultiProtocolURI u: Scanner.scancache.keySet()) {
                DigestURI uu = new DigestURI(u);
                pkmap.put(uu.hash(), uu);
            }
            // search for crawl start requests in this mapping
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getValue().startsWith("mark_")) {
                    byte [] pk = entry.getValue().substring(5).getBytes();
                    DigestURI url = pkmap.get(pk);
                    if (url != null) {
                        String path = "/Crawler_p.html?createBookmark=off&xsstopw=off&crawlingDomMaxPages=10000&intention=&range=domain&indexMedia=on&recrawl=nodoubles&xdstopw=off&storeHTCache=on&sitemapURL=&repeat_time=7&crawlingQ=on&cachePolicy=iffresh&indexText=on&crawlingMode=url&mustnotmatch=&crawlingDomFilterDepth=1&crawlingDomFilterCheck=off&crawlingstart=Start%20New%20Crawl&xpstopw=off&repeat_unit=seldays&crawlingDepth=99";
                        path += "&crawlingURL=" + url.toNormalform(true, false);
                        WorkTables.execAPICall("localhost", (int) sb.getConfigLong("port", 8080), sb.getConfig("adminAccountBase64MD5", ""), path, pk);
                    }
                }
            }
        }
        
        return prop;
    }
    
    private static void addSelectIPRange(Switchboard sb, serverObjects prop) {
        InetAddress ip;
        List<InetAddress> ips = Domains.myIntranetIPs();
        prop.put("enterrange_intranethosts", ips.toString());
        prop.put("enterrange_intranetHint", 0);
        if (sb.isIntranetMode()) {
            if (ips.size() > 0) ip = ips.get(0); else try {
                ip = InetAddress.getByName("192.168.0.1");
            } catch (UnknownHostException e) {
                ip = null;
                e.printStackTrace();
            }
        } else {
            prop.put("enterrange_intranetHint", 1);
            ip = Domains.myPublicLocalIP();
        }
        addSelectIPRange(ip, prop);
    }
    
    private static void addSelectIPRange(InetAddress ip, serverObjects prop) {
        prop.put("enterrange", 1);
        byte[] address = ip.getAddress();
        prop.put("enterrange_ip4-0", 0xff & address[0]);
        prop.put("enterrange_ip4-1", 0xff & address[1]);
        prop.put("enterrange_ip4-2", 0xff & address[2]);
    }
    
    private static void addScantable(Map<byte[], String> commentCache, serverObjects prop) {
        if (Scanner.scancache.size() > 0) {
            // show scancache table
            prop.put("servertable", 1);
            String urlString;
            DigestURI u;
            table: while (true) {
                try {
                    int i = 0;
                    for (final Map.Entry<MultiProtocolURI, Scanner.Access> host: Scanner.scancache.entrySet()) {
                        u = new DigestURI(host.getKey());
                        urlString = u.toNormalform(true, false);
                        prop.put("servertable_list_" + i + "_pk", new String(u.hash()));
                        prop.put("servertable_list_" + i + "_count", i);
                        prop.putHTML("servertable_list_" + i + "_protocol", u.getProtocol());
                        prop.putHTML("servertable_list_" + i + "_ip", Domains.dnsResolve(u.getHost()).getHostAddress());
                        prop.putHTML("servertable_list_" + i + "_url", urlString);
                        prop.put("servertable_list_" + i + "_accessUnknown", host.getValue() == Access.unknown ? 1 : 0);
                        prop.put("servertable_list_" + i + "_accessEmpty", host.getValue() == Access.empty ? 1 : 0);
                        prop.put("servertable_list_" + i + "_accessGranted", host.getValue() == Access.granted ? 1 : 0);
                        prop.put("servertable_list_" + i + "_accessDenied", host.getValue() == Access.denied ? 1 : 0);
                        prop.put("servertable_list_" + i + "_process", inIndex(commentCache, urlString) == null ? 0 : 1);
                        prop.put("servertable_list_" + i + "_preselected", interesting(commentCache, u, host.getValue()) ? 1 : 0);
                        i++;
                    }
                    prop.put("servertable_list", i);
                    prop.put("servertable_num", i);
                    break table;
                } catch (ConcurrentModificationException e) {
                    continue table;
                }
            }
        }
    }
    
    private static void enlargeScancache(Map<byte[], String> commentCache, Scanner scanner) {
        if (Scanner.scancache == null) {
            Scanner.scancache = scanner.services();
            return;
        }
        Iterator<Map.Entry<MultiProtocolURI, Access>> i = Scanner.scancache.entrySet().iterator();
        Map.Entry<MultiProtocolURI, Access> entry;
        while (i.hasNext()) {
            entry = i.next();
            if (!interesting(commentCache, entry.getKey(), entry.getValue())) i.remove();
        }
        Scanner.scancache.putAll(scanner.services());
    }
    
    private static boolean interesting(Map<byte[], String> commentCache, MultiProtocolURI uri, Access access) {
        return inIndex(commentCache, uri.toNormalform(true, false)) == null && access == Access.granted && (uri.getProtocol().equals("smb") || uri.getProtocol().equals("ftp"));
    }
    
    private static byte[] inIndex(Map<byte[], String> commentCache, String url) {
        for (Map.Entry<byte[], String> comment: commentCache.entrySet()) {
            if (comment.getValue().contains(url)) return comment.getKey();
        }
        return null;
    }
    
    private static Map<byte[], String> commentCache(Switchboard sb) {
        Map<byte[], String> comments = new TreeMap<byte[], String>(Base64Order.enhancedCoder);
        Iterator<Tables.Row> i;
        try {
            i = sb.tables.iterator(WorkTables.TABLE_API_NAME);
            Tables.Row row;
            while (i.hasNext()) {
                row = i.next();
                comments.put(row.getPK(), new String(row.get(WorkTables.TABLE_API_COL_COMMENT)));
            }
        } catch (IOException e) {
            Log.logException(e);
        }
        return comments;
    }
    
}
