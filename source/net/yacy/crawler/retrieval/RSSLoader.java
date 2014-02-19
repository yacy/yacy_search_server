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

package net.yacy.crawler.retrieval;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.feed.RSSReader;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ComparableARC;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.HarvestProcess;
import net.yacy.data.WorkTables;
import net.yacy.kelondro.blob.Tables;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;

public class RSSLoader extends Thread {

    public static final ARC<byte[], Date> indexTriggered = new ComparableARC<byte[], Date>(1000, Base64Order.enhancedCoder);

    private final DigestURL urlf;
    private final Switchboard sb;
    private final Map<String, Pattern> collections;
    private final ClientIdentification.Agent agent;

    public RSSLoader(final Switchboard sb, final DigestURL urlf, final Map<String, Pattern> collections, final ClientIdentification.Agent agent) {
        this.sb = sb;
        this.urlf = urlf;
        this.collections = collections;
        this.agent = agent;
    }

    @Override
    public void run() {
        RSSReader rss = null;
        try {
            final Response response = this.sb.loader.load(this.sb.loader.request(this.urlf, true, false), CacheStrategy.NOCACHE, Integer.MAX_VALUE, BlacklistType.CRAWLER, this.agent);
            final byte[] resource = response == null ? null : response.getContent();
            rss = resource == null ? null : RSSReader.parse(RSSFeed.DEFAULT_MAXSIZE, resource);
        } catch (final MalformedURLException e) {
            ConcurrentLog.warn("Load_RSS", "rss loading for url '" + getName().substring(9) + "' failed: " + e.getMessage());
            return;
        } catch (final IOException e) {
            ConcurrentLog.warn("Load_RSS", "rss loading for url '" + this.urlf.toNormalform(true) + "' failed: " + e.getMessage());
            return;
        }
        if (rss == null) {
            ConcurrentLog.warn("Load_RSS", "no rss for url " + this.urlf.toNormalform(true));
            return;
        }
        final RSSFeed feed = rss.getFeed();
        indexAllRssFeed(this.sb, this.urlf, feed, this.collections);

        // add the feed also to the scheduler
        recordAPI(this.sb, null, this.urlf, feed, 7, "seldays");
    }

    public static void indexAllRssFeed(final Switchboard sb, final DigestURL url, final RSSFeed feed, Map<String, Pattern> collections) {
        int loadCount = 0;
        List<DigestURL> list = new ArrayList<DigestURL>();
        Map<String, DigestURL> urlmap = new HashMap<String, DigestURL>();
        for (final RSSMessage message: feed) {
            try {
                final DigestURL messageurl = new DigestURL(message.getLink());
                if (indexTriggered.containsKey(messageurl.hash())) continue;
                urlmap.put(ASCII.String(messageurl.hash()), messageurl);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }
        for (final Map.Entry<String, DigestURL> e: urlmap.entrySet()) {
            HarvestProcess harvestProcess = sb.urlExists(e.getKey());
            if (harvestProcess != null) continue;
            list.add(e.getValue());
            indexTriggered.insertIfAbsent(ASCII.getBytes(e.getKey()), new Date());
            loadCount++;
        }
        sb.addToIndex(list, null, null, collections, true);
        // update info for loading

        try {
            Tables.Data rssRow = sb.tables.select("rss", url.hash());
            if (rssRow == null) rssRow = new Tables.Data();
            final Date lastLoadDate = rssRow.get("last_load_date", new Date(0));
            final long deltaTime = Math.min(System.currentTimeMillis() - lastLoadDate.getTime(), 1000 * 60 * 60 * 24);
            final int allLoadCount = rssRow.get("all_load_count", 0);
            final int lastAvg = rssRow.get("avg_upd_per_day", 0);
            final long thisAvg = 1000 * 60 * 60 * 24 / deltaTime * loadCount;
            final long nextAvg = lastAvg == 0 ? thisAvg : (thisAvg + lastAvg * 2) / 3;
            rssRow.put("url", UTF8.getBytes(url.toNormalform(true)));
            rssRow.put("title", feed.getChannel().getTitle());
            rssRow.put("last_load_date", new Date());
            rssRow.put("last_load_count", loadCount);
            rssRow.put("all_load_count", allLoadCount + loadCount);
            rssRow.put("avg_upd_per_day", nextAvg);
            sb.tables.update("rss", url.hash(), rssRow);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }
    }


    public static void recordAPI(final Switchboard sb, final String apicall_pk, final DigestURL url, final RSSFeed feed, final int repeat_time, final String repeat_unit) {
        // record API action
        byte[] pk = null;
        final serverObjects post = new serverObjects();
        post.put("url", url.toNormalform(true));
        post.put("indexAllItemContent", "");
        if (apicall_pk != null) post.put(WorkTables.TABLE_API_COL_APICALL_PK, apicall_pk);
        if (repeat_time > 0) {
            // store as scheduled api call
            pk = sb.tables.recordAPICall(post, "Load_RSS_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "import feed " + url.toNormalform(true), repeat_time, repeat_unit.substring(3));
        } else {
            // store just a protocol
            pk = sb.tables.recordAPICall(post, "Load_RSS_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "import feed " + url.toNormalform(true));
        }
        // store pk of api table into rss table to show that the entry has been recorded
        assert pk != null;
        final Tables.Data rssRow = new Tables.Data();
        rssRow.put("url", UTF8.getBytes(url.toNormalform(true)));
        rssRow.put("title", feed.getChannel().getTitle());
        rssRow.put("api_pk", pk);
        try {
            sb.tables.update("rss", url.hash(), rssRow);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }
}