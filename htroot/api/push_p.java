/**
 *  push_p
 *  Copyright 2014 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 12.06.2014 at http://yacy.net
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

import java.net.MalformedURLException;
import java.util.Date;
import java.util.Set;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;
import net.yacy.search.IndexingQueueEntry;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class push_p {
    
    // test: http://localhost:8090/api/push_p.json?count=1&synchronous=false&commit=false&url-0=http://nowhere.cc/example.txt&data-0=%22hello%20world%22&lastModified-0=Tue,%2015%20Nov%201994%2012:45:26%20GMT&contentType-0=text/plain&collection-0=testpush
    
    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        // display mode: this only helps to display a nice input form for test cases
        int c = post == null ? 1 : post.getInt("c", 0);
        if (c > 0) {
            prop.put("mode", 0);
            for (int i = 0; i < c; i++) prop.put("mode_input_" + i + "_count", i);
            prop.put("mode_input", c);
            prop.put("mode_count", c);
            return prop;
        }
        
        // push mode: this does a document upload
        prop.put("mode", 1);
        if (post == null) return prop;
        boolean commit = post.getBoolean("commit");
        boolean synchronous = commit || post.getBoolean("synchronous");
        int count = post.getInt("count", 0);
        boolean successall = true;
        int countsuccess = 0;
        int countfail = 0;
        for (int i = 0; i < count; i++) {
            try {
                prop.put("mode_results_" + i + "_item", i);
                String u = post.get("url-" + i, "");
                prop.put("mode_results_" + i + "_url", u);
                DigestURL url = new DigestURL(u);
                String collection = post.get("collection-" + i, "");
                String lastModified = post.get("lastModified-" + i, ""); // must be in RFC1123 format
                String contentType = post.get("contentType-" + i, "");
                String data64 = post.get("data-" + i + "$file", ""); // multi-file uploads are all base64-encoded in YaCyDefaultServlet.parseMultipart
                byte[] data = Base64Order.standardCoder.decode(data64);
                if ((data == null || data.length == 0) && data64.length() > 0) data = UTF8.getBytes(data64); // for test cases
    
                // create response header
                final ResponseHeader responseHeader = new ResponseHeader(200);
                responseHeader.put(HeaderFramework.LAST_MODIFIED, lastModified);
                responseHeader.put(HeaderFramework.CONTENT_TYPE, contentType);
                responseHeader.put(HeaderFramework.CONTENT_LENGTH, Long.toString(data.length));
                // add generic fields
                String[] responseHeaderMap = post.getParams("responseHeader-" + i); // strings with key-value pairs; separated by ':'
                for (String kv: responseHeaderMap) {
                    int p = kv.indexOf(':');
                    if (p < 0) continue;
                    String key = kv.substring(0, p).trim();
                    String value = kv.substring(p + 1).trim();
                    responseHeader.put(key, value);
                }
                CrawlProfile profile = sb.crawler.getPushCrawlProfile(collection);
                
                // create requests and artificial response
                final Request request = new Request(
                        ASCII.getBytes(sb.peers.mySeed().hash),
                        url,
                        null,             // referrer hash
                        "",               // the name of the document to crawl
                        new Date(),       // current date
                        profile.handle(), // the name of the prefetch profile. This must not be null!
                        0,                // forkfactor sum of anchors of all ancestors
                        profile.timezoneOffset());
                Response response = new Response(
                        request,
                        null,
                        responseHeader,
                        profile,
                        false,            // from cache?
                        data);            // content
                IndexingQueueEntry in = new IndexingQueueEntry(response, null, null);
                
                if (synchronous) {
                    // synchronously process the content
                    sb.storeDocumentIndex(sb.webStructureAnalysis(sb.condenseDocument(sb.parseDocument(in))));
                } else {
                    // asynchronously push the content to the indexing queue
                    sb.indexingDocumentProcessor.enQueue(in);
                }
                prop.put("mode_results_" + i + "_success", "1");

                Set<String> ips = Domains.myPublicIPs();
                String address = ips.size() == 0 ? "127.0.0.1" : ips.iterator().next();
                if (address == null) address = "127.0.0.1";
                prop.put("mode_results_" + i + "_success_message", "http://" + address + ":" + sb.getLocalPort() + "/solr/select?q=sku:%22" + u + "%22");
                countsuccess++;
            } catch (MalformedURLException e) {
                e.printStackTrace();
                prop.put("mode_results_" + i + "_success", "0");
                prop.put("mode_results_" + i + "_success_message", e.getMessage());
                successall = false;
                countfail++;
            }
        }
        prop.put("mode_results", count);
        prop.put("mode_successall", successall ? "1" : "0");
        prop.put("mode_count", count);
        prop.put("mode_countsuccess", countsuccess);
        prop.put("mode_countfail", countfail);
     
        if (synchronous && commit) sb.index.fulltext().commit(true);
        
        return prop;
    }
    
}
