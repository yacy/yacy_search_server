// Table_p.java
// -----------------------
// (C) 2010 by Michael Peter Christen; mc@yacy.net
// first published 21.01.2010 in Frankfurt, Germany on http://yacy.net
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.blob.Tables;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class table_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        final String EXT = header.get("EXT", "");
        final boolean html = EXT.equals("html");
        final boolean xml = EXT.equals("xml");

        String table = (post == null) ? null : post.get("table");
        if (post == null || (!post.containsKey("commitrow") && table != null && !sb.tables.hasHeap(table))) table = null;
        prop.put("showtable", 0);
        prop.put("tablecount", sb.tables.size());

        // apply deletion requests
        if (table != null && post != null && post.containsKey("deletetable")) {
            sb.tables.clear(table);
            table = null;
        }

        if (table == null) {
            // list all tables that we know
            int c = 0;
            for (final String name: sb.tables) {
                try {
                    if (html) {
                        prop.putHTML("showtable_tables_" + c + "_table", name);
                    }
                    if (xml) {
                        prop.putXML("showtable_tables_" + c + "_table", name);
                    }
                    prop.put("showtable_tables_" + c + "_num", sb.tables.size(name));
                    c++;
                } catch (final IOException e) {
                }
            }
            prop.put("showtable_tables", c);
            prop.put("tablecount", c);
            return prop;
        }

        final boolean showpk = post.containsKey("pk");

        final String selectKey = post.containsKey("selectKey") ? post.get("selectKey") : null;
        final String selectValue = (selectKey != null && post.containsKey("selectValue")) ? post.get("selectValue") : null;

        final String counts = post.get("count", null);
        int maxcount = (counts == null || counts.equals("all")) ? Integer.MAX_VALUE : post.getInt("count", 10);
        final String pattern = post.get("search", "");
        final Pattern matcher = (pattern.isEmpty() || pattern.equals(".*")) ? null : Pattern.compile(".*" + pattern + ".*");


        if (post.containsKey("deleterows")) {
            for (final Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getValue().startsWith("pk_")) try {
                    sb.tables.delete(table, entry.getValue().substring(3).getBytes());
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                }
            }
        }

        if (post.containsKey("commitrow")) {
            final String pk = post.get("pk");
            final Map<String, byte[]> map = new HashMap<String, byte[]>();
            for (final Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getKey().startsWith("col_")) {
                    map.put(entry.getKey().substring(4), entry.getValue().getBytes());
                }
            }
            try {
                if (pk == null || pk.isEmpty()) {
                    sb.tables.insert(table, map);
                } else {
                    sb.tables.update(table, pk.getBytes(), map);
                }
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            } catch (final SpaceExceededException e) {
                ConcurrentLog.logException(e);
            }
        }

        // generate table
        prop.put("showtable", 1);
        prop.put("showtable_table", table);

        // insert the columns
        ArrayList<String> columns = null;
        try {
            columns = sb.tables.columns(table);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            columns = new ArrayList<String>();
        }

        // if a row attribute is given
        // then order the columns according to the given order
        final String[] row = post.get("row", "").split(",");
        for (int i = 0; i < row.length; i++) {
            if (columns.contains(row[i])) {
                columns.remove(row[i]);
                if (i < columns.size()) columns.add(i, row[i]);
            }
        }
        prop.put("showtable_showpk", showpk ? 1 : 0);
        for (int i = 0; i < columns.size(); i++) {
            prop.putHTML("showtable_columns_" + i + "_header", columns.get(i));
        }
        prop.put("showtable_columns", columns.size());

        // insert all rows
        try {
            maxcount = Math.min(maxcount, sb.tables.size(table));
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            maxcount = 0;
        }
        int count = 0;
        try {
            final Iterator<Tables.Row> plainIterator = sb.tables.iterator(table, matcher);
            final Iterator<Tables.Row> mapIterator = sb.tables.orderByPK(plainIterator, maxcount).iterator();
            Tables.Row trow;
            boolean dark = true;
            String cellName, cellValue;
            rowloop: while ((mapIterator.hasNext()) && (count < maxcount)) {
                trow = mapIterator.next();
                if (row == null) continue;
                prop.put("showtable_list_" + count + "_dark", ((dark) ? 1 : 0) ); dark=!dark;
                prop.put("showtable_list_" + count + "_showpk", showpk ? 1 : 0);
                prop.put("showtable_list_" + count + "_showpk_pk", UTF8.String(trow.getPK()));
                prop.put("showtable_list_" + count + "_count", count);
                for (int i = 0; i < columns.size(); i++) {
                    cellName = columns.get(i);
                    if (trow.containsKey(cellName)) {
                        cellValue = UTF8.String(trow.get(cellName));
                        if (selectKey != null && cellName.equals(selectKey) && !cellValue.matches(selectValue)) continue rowloop;
                    } else {
                        cellValue = "";
                    }
                    if (html) {
                        prop.putHTML("showtable_list_" + count + "_columns_" + i + "_column", cellName);
                        prop.putHTML("showtable_list_" + count + "_columns_" + i + "_cell", cellValue);
                    }
                    if (xml) {
                        prop.putXML("showtable_list_" + count + "_columns_" + i + "_column", cellName);
                        prop.putXML("showtable_list_" + count + "_columns_" + i + "_cell", cellValue);
                    }
                }
                prop.put("showtable_list_" + count + "_columns", columns.size());

                count++;
            }
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        prop.put("showtable_list", count);
        prop.put("showtable_num", count);

        // return rewrite properties
        return prop;
    }

}
