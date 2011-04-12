/**
 *  RSSLoader
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 27.8.2010 at http://yacy.net
 *
 * $LastChangedDate$
 * $LastChangedRevision$
 * $LastChangedBy$
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

package de.anomic.crawler;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;

import net.yacy.cora.document.RSSFeed;
import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.document.RSSReader;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ComparableARC;
import net.yacy.document.Parser.Failure;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import de.anomic.crawler.retrieval.Response;
import de.anomic.data.WorkTables;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;

public class RSSLoader extends Thread {
    
    public static final ARC<byte[], Date> indexTriggered = new ComparableARC<byte[], Date>(1000, Base64Order.enhancedCoder);
    
    DigestURI urlf;
    Switchboard sb;
    
    public RSSLoader(Switchboard sb, DigestURI urlf) {
        this.sb = sb;
        this.urlf = urlf;
    }
    
    public void run() {
        RSSReader rss = null;
        try {
            Response response = sb.loader.load(sb.loader.request(urlf, true, false), CrawlProfile.CacheStrategy.NOCACHE, Long.MAX_VALUE, true);
            byte[] resource = response == null ? null : response.getContent();
            rss = resource == null ? null : RSSReader.parse(RSSFeed.DEFAULT_MAXSIZE, resource);
        } catch (MalformedURLException e) {
            Log.logWarning("Load_RSS", "rss loading for url '" + this.getName().substring(9) + "' failed: " + e.getMessage());
            return;
        } catch (IOException e) {
            Log.logWarning("Load_RSS", "rss loading for url '" + urlf.toNormalform(true, false) + "' failed: " + e.getMessage());
            return;
        }
        if (rss == null) {
            Log.logWarning("Load_RSS", "no rss for url " + urlf.toNormalform(true, false));
            return;
        }
        RSSFeed feed = rss.getFeed();
        indexAllRssFeed(sb, urlf, feed);
    
        // add the feed also to the scheduler
        recordAPI(sb, null, urlf, feed, 7, "seldays");
    }
    
    public static void indexAllRssFeed(Switchboard sb, DigestURI url, RSSFeed feed) {
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
            rssRow.put("url", UTF8.getBytes(url.toNormalform(true, false)));
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
    
    
    public static void recordAPI(Switchboard sb, String apicall_pk, DigestURI url, RSSFeed feed, int repeat_time, String repeat_unit) {
        // record API action
        byte[] pk = null;
        serverObjects post = new serverObjects();
        post.put("url", url.toNormalform(true, false));
        post.put("indexAllItemContent", "");
        if (apicall_pk != null) post.put(WorkTables.TABLE_API_COL_APICALL_PK, apicall_pk);
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
        rssRow.put("url", UTF8.getBytes(url.toNormalform(true, false)));
        rssRow.put("title", feed.getChannel().getTitle());
        rssRow.put("api_pk", pk);
        try {
            sb.tables.update("rss", url.hash(), rssRow);
        } catch (IOException e) {
            Log.logException(e);
        }
    }
}