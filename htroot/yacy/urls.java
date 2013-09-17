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
import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.crawler.data.NoticedURL;
import net.yacy.crawler.retrieval.Request;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.peers.Protocol;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class urls {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
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
        if (!Protocol.authentifyRequest(post, env)) return prop;

        if (post.get("call", "").equals("remotecrawl")) {
            // perform a remote crawl url handover
            final NoticedURL.StackType stackType = NoticedURL.StackType.GLOBAL;
            int maxCount = Math.min(100, post.getInt("count", 10));
            final long maxTime = Math.min(20000, Math.max(1000, post.getInt("time", 10000)));
            final long timeout = System.currentTimeMillis() + maxTime;
            int c = 0;
            Request entry;
            DigestURL referrer;
            while ((maxCount > 0) &&
                   (System.currentTimeMillis() < timeout) &&
                   (sb.crawlQueues.noticeURL.stackSize(stackType) > 0)) {
                try {
                    entry = sb.crawlQueues.noticeURL.pop(stackType, false, sb.crawler, sb.robots);
                } catch (final IOException e) {
                    break;
                }
                if (entry == null) break;

                // find referrer, if there is one
                referrer = sb.getURL(entry.referrerhash());

                // place url to notice-url db
                sb.crawlQueues.delegatedURL.put(ASCII.String(entry.url().hash()), entry.url());

                // create RSS entry
                prop.put("item_" + c + "_title", "");
                prop.putXML("item_" + c + "_link", entry.url().toNormalform(true));
                prop.putXML("item_" + c + "_referrer", (referrer == null) ? "" : referrer.toNormalform(true));
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
        	URIMetadataNode entry;
            DigestURL referrer;
            for (int i = 0; i < count; i++) {
                entry = sb.index.fulltext().getMetadata(ASCII.getBytes(urlhashes.substring(12 * i, 12 * (i + 1))));
                if (entry == null) continue;
                // find referrer, if there is one
                referrer = sb.getURL(entry.referrerHash());
                // create RSS entry
                prop.put("item_" + c + "_title", entry.dc_title());
                prop.putXML("item_" + c + "_link", entry.url().toNormalform(true));
                prop.putXML("item_" + c + "_referrer", (referrer == null) ? "" : referrer.toNormalform(true));
                prop.putXML("item_" + c + "_description", entry.dc_title());
                prop.put("item_" + c + "_author", entry.dc_creator());
                prop.put("item_" + c + "_pubDate", GenericFormatter.SHORT_SECOND_FORMATTER.format(entry.moddate()));
                prop.put("item_" + c + "_guid", ASCII.String(entry.hash()));
                c++;
            }
            prop.put("item", c);
            prop.putXML("response", "ok");
        }

        // return rewrite properties
        return prop;
    }

}
