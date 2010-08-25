/**
 *  RSSLoader_p
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 20.08.2010 at http://yacy.net
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
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Map;

import net.yacy.cora.document.Hit;
import net.yacy.cora.document.RSSFeed;
import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.document.RSSReader;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ComparableARC;
import net.yacy.document.Parser.Failure;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.retrieval.Response;
import de.anomic.data.WorkTables;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Load_RSS_p {

    private static final ARC<byte[], Date> indexTriggered = new ComparableARC<byte[], Date>(1000, Base64Order.enhancedCoder);
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard)env;

        prop.put("showitems", 0);
        prop.put("showload", 0);
        prop.put("url", "");
        
        if (post == null) return prop;

        prop.put("url", post.get("url", ""));
        
        int repeat_time = Integer.parseInt(post.get("repeat_time", "-1"));
        final String repeat_unit = post.get("repeat_unit", "seldays"); // selminutes, selhours, seldays
        if (!post.get("repeat", "off").equals("on") && repeat_time > 0) repeat_time = -1;
        
        boolean record_api = false;
        
        DigestURI url = null;
        try {
            url = post.containsKey("url") ? new DigestURI(post.get("url", ""), null) : null;
        } catch (MalformedURLException e) {
            Log.logWarning("Load_RSS_p", "url not well-formed: '" + post.get("url", "") + "'");
        }
        
        // if we have an url then try to load the rss
        RSSReader rss = null;
        if (url != null) try {
            prop.put("url", url.toNormalform(true, false));
            Response entry = sb.loader.load(sb.loader.request(url, true, false), CrawlProfile.CacheStrategy.NOCACHE, Long.MAX_VALUE);
            byte[] resource = entry == null ? null : entry.getContent();
            rss = resource == null ? null : RSSReader.parse(RSSFeed.DEFAULT_MAXSIZE, resource);
        } catch (IOException e) {
            Log.logException(e);
        }

        // index all selected items: description only
        if (rss != null && post.containsKey("indexSelectedItemContent")) {
            RSSFeed feed = rss.getFeed();
            loop: for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getValue().startsWith("mark_")) try {
                    RSSMessage message = feed.getMessage(entry.getValue().substring(5));
                    DigestURI messageurl = new DigestURI(message.getLink());
                    if (indexTriggered.containsKey(messageurl.hash())) continue loop;
                    if (sb.urlExists(Segments.Process.LOCALCRAWLING, messageurl.hash()) != null) continue loop;
                    sb.addToIndex(messageurl, null, null);
                    indexTriggered.put(messageurl.hash(), new Date());
                } catch (IOException e) {
                    Log.logException(e);
                } catch (Failure e) {
                    Log.logException(e);
                }
            }
        }
        if (rss != null && post.containsKey("indexAllItemContent")) {
            record_api = true;
            RSSFeed feed = rss.getFeed();
            loop: for (RSSMessage message: feed) {
                try {
                    DigestURI messageurl = new DigestURI(message.getLink());
                    if (indexTriggered.containsKey(messageurl.hash()) && post.containsKey("indexSelectedItemContent")) continue loop;
                    if (sb.urlExists(Segments.Process.LOCALCRAWLING, messageurl.hash()) != null) continue loop;
                    sb.addToIndex(messageurl, null, null);
                    indexTriggered.put(messageurl.hash(), new Date());
                } catch (IOException e) {
                    Log.logException(e);
                } catch (Failure e) {
                    Log.logException(e);
                }
            }
        }
        
        if (record_api) {
            // record API action
            if (repeat_time > 0) {
                // store as scheduled api call
                sb.tables.recordAPICall(post, "Load_RSS_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "import feed " + url.toNormalform(true, false), repeat_time, repeat_unit.substring(3));
            } else {
                // store just a protocol
                sb.tables.recordAPICall(post, "Load_RSS_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "import feed " + url.toNormalform(true, false));
            }
        }
        
        // show items from rss
        if (rss != null) {
            prop.put("showitems", 1);
            RSSFeed feed = rss.getFeed();
            RSSMessage channel = feed.getChannel();
            prop.putHTML("showitems_title", channel.getTitle());
            String author = channel.getAuthor();
            if (author == null || author.length() == 0) author = channel.getCopyright();
            Date pubDate = channel.getPubDate();
            prop.putHTML("showitems_author", author == null ? "" : author);
            prop.putHTML("showitems_description", channel.getDescription());
            prop.putHTML("showitems_language", channel.getLanguage());
            prop.putHTML("showitems_date", (pubDate == null) ? "" : DateFormat.getDateTimeInstance().format(pubDate));
            prop.putHTML("showitems_ttl", channel.getTTL());
            prop.putHTML("showitems_docs", channel.getDocs());
            
            int i = 0;
            for (final Hit item: feed) {
                try {
                    DigestURI messageurl = new DigestURI(item.getLink(), null);
                    author = item.getAuthor();
                    if (author == null) author = item.getCopyright();
                    pubDate = item.getPubDate();
                    prop.put("showitems_item_" + i + "_count", i);
                    prop.put("showitems_item_" + i + "_state", sb.urlExists(Segments.Process.LOCALCRAWLING, messageurl.hash()) != null ? 2 : indexTriggered.containsKey(messageurl.hash()) ? 1 : 0);
                    prop.putHTML("showitems_item_" + i + "_guid", item.getGuid());
                    prop.putHTML("showitems_item_" + i + "_author", author == null ? "" : author);
                    prop.putHTML("showitems_item_" + i + "_title", item.getTitle());
                    prop.putHTML("showitems_item_" + i + "_link", messageurl.toNormalform(false, false));
                    prop.putHTML("showitems_item_" + i + "_description", item.getDescription());
                    prop.putHTML("showitems_item_" + i + "_language", item.getLanguage());
                    prop.putHTML("showitems_item_" + i + "_date", (pubDate == null) ? "" : DateFormat.getDateTimeInstance().format(pubDate));
                    i++;
                } catch (MalformedURLException e) {
                    Log.logException(e);
                    continue;
                }
            }
            prop.put("showitems_item", i);
            prop.put("showitems_num", i);
            prop.putHTML("showitems_rss", url.toNormalform(true, false));
            if (i > 0) {
                prop.put("showload", 1);
                prop.put("showload_rss", url.toNormalform(true, false));
            }
        }
        
        return prop;
    }
    
}
