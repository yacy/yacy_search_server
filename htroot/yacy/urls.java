// urls.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 22.08.2007 on http://yacy.net
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
import java.util.Date;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import de.anomic.crawler.NoticedURL;
import de.anomic.crawler.retrieval.Request;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyNetwork;

public class urls {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        
        // insert default values
        final serverObjects prop = new serverObjects();
        prop.put("iam", sb.peers.mySeed().hash);
        prop.put("response", "rejected - insufficient call parameters");
        prop.put("channel_title", "");
        prop.put("channel_description", "");
        prop.put("channel_pubDate", "");
        prop.put("item", "0");
        
        if ((post == null) || (env == null)) return prop;
        if (!yacyNetwork.authentifyRequest(post, env)) return prop;
        
        if (post.get("call", "").equals("remotecrawl")) {
            // perform a remote crawl url handover
            final NoticedURL.StackType stackType = NoticedURL.StackType.LIMIT;
            int maxCount = Math.min(100, post.getInt("count", 10));
            long maxTime = Math.min(20000, Math.max(1000, post.getInt("time", 10000)));
            long timeout = System.currentTimeMillis() + maxTime;
            int c = 0;
            Request entry;
            DigestURI referrer;
            while ((maxCount > 0) &&
                   (System.currentTimeMillis() < timeout) &&
                   (sb.crawlQueues.noticeURL.stackSize(stackType) > 0)) {
                try {
                    entry = sb.crawlQueues.noticeURL.pop(stackType, false, sb.crawler);
                } catch (final IOException e) {
                    break;
                }
                if (entry == null) break;
                
                // find referrer, if there is one
                referrer = sb.getURL(Segments.Process.PUBLIC, entry.referrerhash());
                
                // place url to notice-url db
                sb.crawlQueues.delegatedURL.push(
                                entry,
                                sb.peers.mySeed().hash.getBytes(),
                                new Date(),
                                0,
                                "client=____________",
                                -1);
                
                // create RSS entry
                prop.put("item_" + c + "_title", "");
                prop.putXML("item_" + c + "_link", entry.url().toNormalform(true, false));
                prop.putXML("item_" + c + "_referrer", (referrer == null) ? "" : referrer.toNormalform(true, false));
                prop.putXML("item_" + c + "_description", entry.name());
                prop.put("item_" + c + "_author", "");
                prop.put("item_" + c + "_pubDate", GenericFormatter.SHORT_SECOND_FORMATTER.format(entry.appdate()));
                prop.put("item_" + c + "_guid", entry.url().hash());
                c++;
                maxCount--;
            }
            prop.put("item", c);
            prop.putXML("response", "ok");
        }
        
        if (post.get("call", "").equals("urlhashlist")) {
            // retrieve a list of urls from the LURL-db by a given list of url hashes
            final String urlhashes = post.get("hashes", "");
            if (urlhashes.length() % 12 != 0) return prop;
            final int count = urlhashes.length() / 12;
        	int c = 0;
        	URIMetadataRow entry;
        	URIMetadataRow.Components metadata;
            DigestURI referrer;
            for (int i = 0; i < count; i++) {
                entry = sb.indexSegments.urlMetadata(Segments.Process.PUBLIC).load(UTF8.getBytes(urlhashes.substring(12 * i, 12 * (i + 1))));
                if (entry == null) continue;
                // find referrer, if there is one
                referrer = sb.getURL(Segments.Process.PUBLIC, entry.referrerHash());
                // create RSS entry
                metadata = entry.metadata();
                prop.put("item_" + c + "_title", metadata.dc_title());
                prop.putXML("item_" + c + "_link", metadata.url().toNormalform(true, false));
                prop.putXML("item_" + c + "_referrer", (referrer == null) ? "" : referrer.toNormalform(true, false));
                prop.putXML("item_" + c + "_description", metadata.dc_title());
                prop.put("item_" + c + "_author", metadata.dc_creator());
                prop.put("item_" + c + "_pubDate", GenericFormatter.SHORT_SECOND_FORMATTER.format(entry.moddate()));
                prop.put("item_" + c + "_guid", UTF8.String(entry.hash()));
                c++;
            }
            prop.put("item", c);
            prop.putXML("response", "ok");
        }

        // return rewrite properties
        return prop;
    }
    
}
