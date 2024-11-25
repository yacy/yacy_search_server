/**
 *  NetworkHistory
 *  Copyright 2014 by Michael Peter Christen
 *  First released 10.10.2014 at https://yacy.net
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

package net.yacy.htroot;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.blob.Tables.Row;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.visualization.ChartPlotter;
import net.yacy.visualization.RasterPlotter;


public class NetworkHistory {

    public static RasterPlotter respond(@SuppressWarnings("unused") final RequestHeader header, serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;

        if (post == null) post = new serverObjects();

        final int maxtime = post.getInt("maxtime", 48); // hours
        final int bottomscale = post.getInt("scale", 1); // 1h
        final String[] columnsx = post.get("columns", "cC").split("\\|"); // new String[]{"aM", "aW", "aD", "aH", "cC", "cD", "cP", "cR", "cI"};
        /*
           aM activeLastMonth
           aW activeLastWeek
           aD activeLastDay
           aH activeLastHour
           cC countConnected (Active Senior)
           cD countDisconnected (Passive Senior)
           cP countPotential (Junior)
           cR count of the RWI entries
           cI size of the index (number of documents)
         */
        final Set<String> columns = new LinkedHashSet<>();
        for (final String col: columnsx) columns.add(col);
        // scan the database and put in values
        final List<Map<String, Long>> rows = new ArrayList<>(maxtime * 2);
        final long now = System.currentTimeMillis();
        final long timelimit = now - maxtime * 3600000L;
        try {
            final Iterator<Row> rowi = sb.tables.iterator("stats", false);
            Map <String, Long> statrow;
            while (rowi.hasNext()) {
                final Row row = rowi.next();
                final String d = ASCII.String(row.getPK());
                final Date date = GenericFormatter.SHORT_MINUTE_FORMATTER.parse(d, 0).getTime();
                if (date.getTime() < timelimit) break;
                statrow = new HashMap<>();
                for (final String key: columns) {
                    final byte[] x = row.get(key);
                    if (x != null) statrow.put(key, Long.parseLong(ASCII.String(x)));
                }
                statrow.put("time", date.getTime());
                rows.add(statrow);
            }
        } catch (final IOException|ParseException e) {
            ConcurrentLog.logException(e);
        }

        // find correct scale
        int maxpeers = 100, minpeers = Integer.MAX_VALUE; // to be measured by pre-scanning the db
        for (final Map<String, Long> row: rows) {
            for (final String column: columns) {
                final Long v = row.get(column);
                if (v != null) maxpeers = Math.max(maxpeers, (int) v.longValue());
                if (v != null && v.longValue() > 0) minpeers = Math.min(minpeers, (int) v.longValue());
            }
        }
        if (minpeers == Integer.MAX_VALUE) minpeers=0; // no values
        if (minpeers < 0) {
        	ConcurrentLog.warn("NetworkHistory", "Negative value in plot. columns:"+columns);
        	minpeers=0;
        }
        if (maxpeers-minpeers > 2*minpeers) minpeers=0; // if we are close enough to zero, use zero as minimum
        int order=(int)Math.log10(maxpeers-minpeers);
        if (order<1) order=1;
        int scale=(int)Math.pow(10, order);
        minpeers=(minpeers/scale)*scale;
        maxpeers=((maxpeers/scale)+1)*scale;
        if ((maxpeers-minpeers)/scale < 3) scale=Math.max(5,scale/2);
        final int leftborder = 30;
        final int rightborder = 10;
        final int width = post.getInt("width", 768 + leftborder + rightborder);
        final int hspace = width - leftborder - rightborder;
        final int height = post.getInt("height", 240);
        final int topborder = 20;
        final int bottomborder = 20;
        final int vspace = height - topborder - bottomborder;
        final int leftscale = scale;
        String timestr = maxtime + " HOURS";
        if (maxtime > 24 && maxtime % 24 == 0) timestr = (maxtime / 24) + " DAYS";
        if (maxtime == 168) timestr = "WEEK";
        if (maxtime > 168 && maxtime % 168 == 0) timestr = (maxtime / 168) + " WEEKS";
        String headline = "YACY NETWORK HISTORY";
        if (columns.contains("aM")) headline += ", ACTIVE PEERS WITHIN THE LAST MONTH";
        if (columns.contains("aW")) headline += ", ACTIVE PEERS WITHIN THE LAST WEEK";
        if (columns.contains("aD")) headline += ", ACTIVE PEERS WITHIN THE LAST DAY";
        if (columns.contains("aH")) headline += ", ACTIVE PEERS WITHIN THE LAST HOUR";
        if (columns.contains("cC")) headline += ", ACTIVE SENIOR PEERS";
        if (columns.contains("cD")) headline += ", PASSIVE SENIOR PEERS";
        if (columns.contains("cP")) headline += ", POTENTIAL JUNIOR PEERS";
        if (columns.contains("cI")) headline = "YACY PEER '" + sb.peers.myName().toUpperCase() + "' INDEX SIZE HISTORY: NUMBER OF DOCUMENTS";
        if (columns.contains("cR")) headline = "YACY PEER '" + sb.peers.myName().toUpperCase() + "' INDEX SIZE HISTORY: NUMBER OF RWI ENTRIES";
        final ChartPlotter chart = new ChartPlotter(width, height, 0xFFFFFFl, 0x000000l, 0xAAAAAAl, leftborder, rightborder, topborder, bottomborder, headline, "IN THE LAST " + timestr);
        long pps = (long)hspace * (long)bottomscale / maxtime;
        int pixelperscale = Math.max(8, (int)pps );
        chart.declareDimension(ChartPlotter.DIMENSION_BOTTOM, bottomscale, pixelperscale, -maxtime, 0x000000l, 0xCCCCCCl, "TIME/HOURS");
        pps = (long)vspace * (long)leftscale / (maxpeers-minpeers);
        pixelperscale = Math.max(8, (int)pps );
        chart.declareDimension(ChartPlotter.DIMENSION_LEFT, leftscale, pixelperscale, minpeers, 0x008800l, null , columns.contains("cI") ? "DOCUMENTS" : columns.contains("cR") ? "RWIs" : "PEERS");

        // write the data
        float x0, x1;
        int y0, y1;
        Long time;
        for (final String column: columns) {
            x0 = 1.0f; y0 = 0;
            for (final Map<String, Long> row: rows) {
                time = row.get("time");
                if (time == null) continue;
                final Long v = row.get(column);
                if (v == null) continue;
                x1 = (time - now) / 3600000.0f;
                y1 = (int) v.longValue();
                chart.setColor(0x228822);
                chart.chartDot(ChartPlotter.DIMENSION_BOTTOM, ChartPlotter.DIMENSION_LEFT, x1, y1, 2, null, 315);
                chart.setColor(0x008800);
                if (x0 < 0.0f) chart.chartLine(ChartPlotter.DIMENSION_BOTTOM, ChartPlotter.DIMENSION_LEFT, x0, y0, x1, y1);
                x0 = x1; y0 = y1;
            }
        }
        return chart;
    }
}
