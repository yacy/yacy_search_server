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
import java.util.Iterator;
import java.util.Map;

import net.yacy.cora.document.Hit;
import net.yacy.cora.document.RSSFeed;
import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.document.RSSReader;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ComparableARC;
import net.yacy.document.Parser.Failure;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.blob.Tables.Row;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.index.RowSpaceExceededException;
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

        prop.put("showload", 0);
        prop.put("showitems", 0);
        prop.put("shownewfeeds", 0);
        prop.put("showscheduledfeeds", 0);
        prop.put("url", "");

        if (post != null && post.containsKey("removeSelectedFeedNewList")) {
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getValue().startsWith("mark_")) try {
                    sb.tables.delete("rss", entry.getValue().substring(5).getBytes());
                } catch (IOException e) {
                    Log.logException(e);
                }
            }
        }
        
        if (post != null && post.containsKey("removeSelectedFeedScheduler")) {
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getValue().startsWith("mark_")) try {
                    byte[] pk = entry.getValue().substring(5).getBytes();
                    Row rssRow = sb.tables.select("rss", pk);
                    byte[] schedulerPK = rssRow.get("api_pk", (byte[]) null);
                    if (schedulerPK != null) sb.tables.delete("api", schedulerPK);
                    rssRow.remove("api_pk");
                    sb.tables.insert("rss", pk, rssRow);
                } catch (IOException e) {
                    Log.logException(e);
                } catch (RowSpaceExceededException e) {
                    Log.logException(e);
                }
            }
        }
        
        if (post != null && post.containsKey("addSelectedFeedScheduler")) {
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getValue().startsWith("mark_")) {
                    Row row;
                    RSSReader rss = null;
                    try {
                        byte [] pk = entry.getValue().substring(5).getBytes();
                        row = sb.tables.select("rss", pk);
                    } catch (IOException e) {
                        Log.logException(e);
                        continue;
                    } catch (RowSpaceExceededException e) {
                        Log.logException(e);
                        continue;
                    }
                    DigestURI url = null;
                    try {
                        url = new DigestURI(row.get("url", ""));
                    } catch (MalformedURLException e) {
                        Log.logWarning("Load_RSS", "malformed url '" + row.get("url", "") + "': " + e.getMessage());
                        continue;
                    }
                    try {
                        Response response = sb.loader.load(sb.loader.request(url, true, false), CrawlProfile.CacheStrategy.NOCACHE, Long.MAX_VALUE);
                        byte[] resource = response == null ? null : response.getContent();
                        rss = resource == null ? null : RSSReader.parse(RSSFeed.DEFAULT_MAXSIZE, resource);
                    } catch (IOException e) {
                        Log.logWarning("Load_RSS", "rss loading for url '" + url.toNormalform(true, false) + "' failed: " + e.getMessage());
                        continue;
                    }
                    if (rss == null) {
                        Log.logWarning("Load_RSS", "no rss for url " + url.toNormalform(true, false));
                    } else {
                        RSSFeed feed = rss.getFeed();
                        indexAllRssFeed(sb, url, feed);
                    
                        // add the feed also to the scheduler
                        recordAPI(sb, url, rss.getFeed(), 1, "seldays");
                    }
                }
            }
        }
        
        if (post == null || (post != null && (post.containsKey("addSelectedFeedScheduler") || post.containsKey("removeSelectedFeedNewList") || post.containsKey("removeSelectedFeedScheduler")))) {
            try {
                // get list of primary keys from the api table with scheduled feed loading requests
                Tables.Row row;
                String messageurl;
                
                // check feeds
                int newc = 0, apic = 0;
                Iterator<Row> plainIterator = sb.tables.iterator("rss");
                while (plainIterator.hasNext()) {
                    row = plainIterator.next();
                    if (row == null) continue;
                    messageurl = row.get("url", "");
                    if (messageurl.length() == 0) continue;
                    // get referrer
                    DigestURI referrer = sb.getURL(Segments.Process.LOCALCRAWLING, row.get("referrer", "").getBytes());
                    // check if feed is registered in scheduler
                    byte[] api_pk = row.get("api_pk");
                    Row r = api_pk == null ? null : sb.tables.select("api", api_pk);
                    if (r != null && r.get("comment", "").matches(".*\\Q" + messageurl + "\\E.*")) {
                        // this is a recorded entry
                        Date date_next_exec = r.get(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, (Date) null);
                        prop.put("showscheduledfeeds_list_" + apic + "_pk", new String(row.getPK()));
                        prop.put("showscheduledfeeds_list_" + apic + "_count", apic);
                        prop.put("showscheduledfeeds_list_" + apic + "_rss", messageurl);
                        prop.put("showscheduledfeeds_list_" + apic + "_title", row.get("title", ""));
                        prop.put("showscheduledfeeds_list_" + apic + "_referrer", referrer == null ? "" : referrer.toNormalform(true, false));
                        prop.put("showscheduledfeeds_list_" + apic + "_recording", DateFormat.getDateTimeInstance().format(row.get("recording_date", new Date())));
                        prop.put("showscheduledfeeds_list_" + apic + "_lastload", DateFormat.getDateTimeInstance().format(row.get("last_load_date", new Date())));
                        prop.put("showscheduledfeeds_list_" + apic + "_nextload", date_next_exec == null ? "" : DateFormat.getDateTimeInstance().format(date_next_exec));
                        prop.put("showscheduledfeeds_list_" + apic + "_lastcount", row.get("last_load_count", 0));
                        prop.put("showscheduledfeeds_list_" + apic + "_allcount", row.get("all_load_count", 0));
                        prop.put("showscheduledfeeds_list_" + apic + "_updperday", row.get("avg_upd_per_day", 0));
                        apic++;
                    } else {
                        // this is a new entry
                        prop.put("shownewfeeds_list_" + newc + "_pk", new String(row.getPK()));
                        prop.put("shownewfeeds_list_" + newc + "_count", newc);
                        prop.put("shownewfeeds_list_" + newc + "_rss", messageurl);
                        prop.put("shownewfeeds_list_" + newc + "_title", row.get("title", ""));
                        prop.put("shownewfeeds_list_" + newc + "_referrer", referrer == null ? "" : referrer.toNormalform(true, false));
                        prop.put("shownewfeeds_list_" + newc + "_recording", DateFormat.getDateTimeInstance().format(row.get("recording_date", new Date())));
                        newc++;
                    }
                }
                prop.put("showscheduledfeeds_list" , apic);
                prop.put("showscheduledfeeds_num", apic);
                prop.put("showscheduledfeeds", apic > 0 ? apic : 0);
                prop.put("shownewfeeds_list" , newc);
                prop.put("shownewfeeds_num", newc);
                prop.put("shownewfeeds", newc > 0 ? 1 : 0);
            } catch (IOException e) {
                Log.logException(e);
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            }
            
            return prop;
        }

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
            Response response = sb.loader.load(sb.loader.request(url, true, false), CrawlProfile.CacheStrategy.NOCACHE, Long.MAX_VALUE);
            byte[] resource = response == null ? null : response.getContent();
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
            indexAllRssFeed(sb, url, feed);
        }
        
        if (record_api && rss != null && rss.getFeed() != null && rss.getFeed().getChannel() != null) {
            // record API action
            recordAPI(sb, url, rss.getFeed(), repeat_time, repeat_unit);
        }
        
        // show items from rss
        if (rss != null) {
            prop.put("showitems", 1);
            RSSFeed feed = rss.getFeed();
            RSSMessage channel = feed.getChannel();
            prop.putHTML("showitems_title", channel == null ? "" : channel.getTitle());
            String author = channel == null ? "" : channel.getAuthor();
            if (author == null || author.length() == 0) author = channel == null ? "" : channel.getCopyright();
            Date pubDate = channel == null ? null : channel.getPubDate();
            prop.putHTML("showitems_author", author == null ? "" : author);
            prop.putHTML("showitems_description", channel == null ? "" : channel.getDescription());
            prop.putHTML("showitems_language", channel == null ? "" : channel.getLanguage());
            prop.putHTML("showitems_date", (pubDate == null) ? "" : DateFormat.getDateTimeInstance().format(pubDate));
            prop.putHTML("showitems_ttl", channel == null ? "" : channel.getTTL());
            prop.putHTML("showitems_docs", channel == null ? "" : channel.getDocs());
            
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
    
    private static void indexAllRssFeed(Switchboard sb, DigestURI url, RSSFeed feed) {
        int loadCount = 0;
        loop: for (RSSMessage message: feed) {
            try {
                DigestURI messageurl = new DigestURI(message.getLink());
                if (indexTriggered.containsKey(messageurl.hash())) continue loop;
                if (sb.urlExists(Segments.Process.LOCALCRAWLING, messageurl.hash()) != null) continue loop;
                sb.addToIndex(messageurl, null, null);
                indexTriggered.put(messageurl.hash(), new Date());
                loadCount++;
            } catch (IOException e) {
                Log.logException(e);
            } catch (Failure e) {
                Log.logException(e);
            }
        }
        // update info for loading

        try {
            Tables.Data rssRow = sb.tables.select("rss", url.hash());
            if (rssRow == null) rssRow = new Tables.Data();
            Date lastLoadDate = rssRow.get("last_load_date", new Date(0));
            long deltaTime = Math.min(System.currentTimeMillis() - lastLoadDate.getTime(), 1000 * 60 * 60 * 24);
            int allLoadCount = rssRow.get("all_load_count", 0);
            int lastAvg = rssRow.get("avg_upd_per_day", 0);
            long thisAvg = 1000 * 60 * 60 * 24 / deltaTime * loadCount;
            long nextAvg = lastAvg == 0 ? thisAvg : (thisAvg + lastAvg * 2) / 3;
            rssRow.put("url", url.toNormalform(true, false).getBytes());
            rssRow.put("title", feed.getChannel().getTitle());
            rssRow.put("last_load_date", new Date());
            rssRow.put("last_load_count", loadCount);
            rssRow.put("all_load_count", allLoadCount + loadCount);
            rssRow.put("avg_upd_per_day", nextAvg);
            sb.tables.update("rss", url.hash(), rssRow);
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
    }
    
    
    private static void recordAPI(Switchboard sb, DigestURI url, RSSFeed feed, int repeat_time, String repeat_unit) {
        // record API action
        byte[] pk = null;
        serverObjects post = new serverObjects();
        post.put("url", url.toNormalform(true, false));
        post.put("indexAllItemContent", "");
        if (repeat_time > 0) {
            // store as scheduled api call
            pk = sb.tables.recordAPICall(post, "Load_RSS_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "import feed " + url.toNormalform(true, false), repeat_time, repeat_unit.substring(3));
        } else {
            // store just a protocol
            pk = sb.tables.recordAPICall(post, "Load_RSS_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "import feed " + url.toNormalform(true, false));
        }
        // store pk of api table into rss table to show that the entry has been recorded
        assert pk != null;            
        Tables.Data rssRow = new Tables.Data();
        rssRow.put("url", url.toNormalform(true, false).getBytes());
        rssRow.put("title", feed.getChannel().getTitle());
        rssRow.put("api_pk", pk);
        try {
            sb.tables.update("rss", url.hash(), rssRow);
        } catch (IOException e) {
            Log.logException(e);
        }
    }
}
