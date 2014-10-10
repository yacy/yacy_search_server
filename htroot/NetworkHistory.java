/**
 *  NetworkHistory
 *  Copyright 2014 by Michael Peter Christen
 *  First released 10.10.2014 at http://yacy.net
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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.blob.Tables;
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
        final String[] columns = post.get("columns", "cC").split("\\|"); // new String[]{"aM", "aW", "aD", "aH", "cC", "cD", "cP", "cR", "cI"};
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
        
        // scan the database and put in values
        List<Map<String, Long>> rows = new ArrayList<>(maxtime * 2);
        long now = System.currentTimeMillis();
        long timelimit = now - maxtime * 60 * 60 * 1000;
        try {
            // BEncodedHeap statTable = sb.tables.getHeap("stats");
            // Iterator<byte[]> i = statTable.keys(false, false);
            Collection<Row> rowi = Tables.orderByPK(sb.tables.iterator("stats"), 10000, true); // this is bad, fix the statTable.keys(false, false) method
            Map <String, Long> statrow;
            for (Row row: rowi) {
                String d = ASCII.String(row.getPK());
                Date date = GenericFormatter.SHORT_MINUTE_FORMATTER.parse(d);
                //System.out.println(date);
                if (date.getTime() < timelimit) break;
                //Map<String, byte[]> row = statTable.get(pk);
                statrow = new HashMap<>();
                for (String key: columns) {
                    byte[] x = row.get(key);
                    if (x != null) statrow.put(key, Long.parseLong(ASCII.String(x)));
                }
                statrow.put("time", date.getTime());
                rows.add(statrow);
            }
        } catch (final IOException|ParseException e) {
            ConcurrentLog.logException(e);
        }
        
        // find correct scale
        int maxpeers = 10; // to be measured by pre-scanning the db
        for (Map<String, Long> row: rows) {
            for (String column: columns) {
                Long v = row.get(column);
                if (v != null) maxpeers = Math.max(maxpeers, (int) v.longValue());
            }
        }
        final int leftborder = 40;
        final int rightborder = 10;
        final int width = post.getInt("width", 768 + leftborder + rightborder);
        final int hspace = width - leftborder - rightborder;
        final int height = post.getInt("height", 240);
        final int topborder = 20;
        final int bottomborder = 20;
        final int vspace = height - topborder - bottomborder;
        final int leftscale = (maxpeers / 100) * 10;
        ChartPlotter chart = new ChartPlotter(width, height, "FFFFFF", "000000", "AAAAAA", leftborder, rightborder, topborder, bottomborder, "YACY NETWORK HISTORY", "IN THE LAST 48 HOURS");
        chart.declareDimension(ChartPlotter.DIMENSION_BOTTOM, bottomscale, hspace / (maxtime / bottomscale), -maxtime, "000000", "CCCCCC", "TIME/HOURS");
        chart.declareDimension(ChartPlotter.DIMENSION_LEFT, leftscale, vspace * leftscale / maxpeers, 0, "008800", null , "PEERS");
        
        // write the data
        float x0, x1;
        int y0, y1;
        Long time;
        for (String column: columns) {
            x0 = 1.0f; y0 = 0;
            for (Map<String, Long> row: rows) {
                time = row.get("time");
                if (time == null) continue;
                Long v = row.get(column);
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
