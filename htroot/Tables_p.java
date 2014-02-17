// Tables_p.java
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
import java.util.List;
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

public class Tables_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        prop.put("showtable", 0);
        prop.put("showedit", 0);
        prop.put("showselection", 0);

        String table = (post == null) ? null : post.get("table", null);
        if (table != null && !sb.tables.hasHeap(table)) table = null;

        // show table selection
        int count = 0;
        final Iterator<String> ti = sb.tables.iterator();
        String tablename;
        prop.put("showselection", 1);
        while (ti.hasNext()) {
            tablename = ti.next();
            prop.put("showselection_tables_" + count + "_name", tablename);
            prop.put("showselection_tables_" + count + "_selected", (table != null && table.equals(tablename)) ? 1 : 0);
            count++;
        }
        prop.put("showselection_tables", count);
        prop.put("showselection_pattern", "");

        if (post == null) return prop; // return rewrite properties

        final String counts = post.get("count", null);
        int maxcount = (counts == null || counts.equals("all")) ? Integer.MAX_VALUE : post.getInt("count", 10);
        final String pattern = post.get("search", "");
        final Pattern matcher = (pattern.isEmpty() || pattern.equals(".*")) ? null : Pattern.compile(".*" + pattern + ".*");
        prop.put("pattern", pattern);

        // apply deletion requests
        if (post.get("deletetable", "").length() > 0)
            sb.tables.clear(table);

        if (post.get("deleterows", "").length() > 0) {
            for (final Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getValue().startsWith("pk_")) try {
                    sb.tables.delete(table, entry.getValue().substring(3).getBytes());
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                }
            }
        }

        if (post.get("commitrow", "").length() > 0) {
            final String pk = post.get("pk");
            final Map<String, byte[]> map = new HashMap<String, byte[]>();
            for (final Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getKey().startsWith("col_")) {
                    map.put(entry.getKey().substring(4), entry.getValue().getBytes());
                }
            }
            try {
                sb.tables.update(table, pk.getBytes(), map);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }

        // generate table
        prop.put("showtable", 0);
        prop.put("showedit", 0);


        if (table != null) {

            List<String> columns = null;
            try {
                columns = sb.tables.columns(table);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                columns = new ArrayList<String>();
            }

            if (post.containsKey("editrow")) {
                // check if we can find a key
                String pk = null;
                for (final Map.Entry<String, String> entry: post.entrySet()) {
                    if (entry.getValue().startsWith("pk_")) {
                        pk = entry.getValue().substring(3);
                        break;
                    }
                }
                try {
                    if (pk != null && sb.tables.has(table, pk.getBytes())) {
                        setEdit(sb, prop, table, pk, columns);
                    }
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                }
            } else if (post.containsKey("addrow")) try {
                // get a new key
                final String pk = UTF8.String(sb.tables.createRow(table));
                setEdit(sb, prop, table, pk, columns);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            } catch (final SpaceExceededException e) {
                ConcurrentLog.logException(e);
            } else {
                prop.put("showtable", 1);
                prop.put("showtable_table", table);

                // insert the columns

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
                count = 0;
                try {
                    final Iterator<Tables.Row> plainIterator = sb.tables.iterator(table, matcher);
                    final Iterator<Tables.Row> mapIterator = sb.tables.orderByPK(plainIterator, maxcount).iterator();
                    Tables.Row row;
                    boolean dark = true;
                    byte[] cell;
                    while (mapIterator.hasNext() && count < maxcount) {
                        row = mapIterator.next();
                        if (row == null) continue;

                        // write table content
                        prop.put("showtable_list_" + count + "_dark", ((dark) ? 1 : 0) ); dark=!dark;
                        prop.put("showtable_list_" + count + "_pk", UTF8.String(row.getPK()));
                        prop.put("showtable_list_" + count + "_count", count);
                        prop.put("showtable_list_" + count + "_table", table); // tablename for edit link
                        for (int i = 0; i < columns.size(); i++) {
                            cell = row.get(columns.get(i));
                            prop.putHTML("showtable_list_" + count + "_columns_" + i + "_cell", cell == null ? "" : UTF8.String(cell));
                        }
                        prop.put("showtable_list_" + count + "_columns", columns.size());
                        count++;
                    }
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                }
                prop.put("showtable_list", count);
                prop.put("showtable_num", count);
            }

        }

        // adding the peer address
        prop.put("address", sb.peers.mySeed().getPublicAddress());

        // return rewrite properties
        return prop;
    }

    private static void setEdit(final Switchboard sb, final serverObjects prop, final String table, final String pk, final List<String> columns) throws IOException, SpaceExceededException {
        prop.put("showedit", 1);
        prop.put("showedit_table", table);
        prop.put("showedit_pk", pk);
        final Tables.Row row = sb.tables.select(table, pk.getBytes());
        if (row == null) return;
        int count = 0;
        byte[] cell;
        for (final String col: columns) {
            cell = row.get(col);
            prop.put("showedit_list_" + count + "_key", col);
            prop.put("showedit_list_" + count + "_value", cell == null ? "" : UTF8.String(cell));
            count++;
        }
        prop.put("showedit_list", count);
    }
}
