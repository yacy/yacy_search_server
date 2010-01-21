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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;

import de.anomic.http.server.RequestHeader;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Tables_p {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        String table = (post == null) ? null : post.get("table", null);
        if (table != null && !sb.tables.hasHeap(table)) table = null;

        // show table selection
        int count = 0;
        Iterator<String> ti = sb.tables.tables();
        String tablename;
        while (ti.hasNext()) {
            tablename = ti.next();
            prop.put("tables_" + count + "_name", tablename);
            prop.put("tables_" + count + "_selected", (table != null && table.equals(tablename)) ? 1 : 0);
            count++;
        }
        prop.put("tables", count);
        
        List<String> columns = null;
        if (table != null) try {
            columns = sb.tables.columns(table);
        } catch (IOException e) {
            Log.logException(e);
        }
        
        // apply deletion requests
        if (post != null && post.get("deletetable", "").length() > 0) {
            try {
                sb.tables.clear(table);
            } catch (IOException e) {
                Log.logException(e);
            }
        }
        
        if (post != null && post.get("deleterows", "").length() > 0) {
            try {
                for (Map.Entry<String, String> entry: post.entrySet()) {
                    if (entry.getKey().startsWith("mark_") && entry.getValue().equals("on")) {
                        sb.tables.delete(table, entry.getKey().substring(5).getBytes());
                    }
                }
            } catch (IOException e) {
                Log.logException(e);
            }
        }
        
        if (post != null && post.get("commitrow", "").length() > 0) {
            String pk = post.get("pk");
            Map<String, byte[]> map = new HashMap<String, byte[]>();
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getKey().startsWith("col_")) {
                    map.put(entry.getKey().substring(4), entry.getValue().getBytes());
                }
            }
            try {
                sb.tables.insert(table, pk.getBytes(), map);
            } catch (IOException e) {
                Log.logException(e);
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            }
        }
        
        // generate table
        prop.put("showtable", 0);
        prop.put("showedit", 0);
        
        if (table != null && !post.containsKey("editrow") && !post.containsKey("addrow")) try {
            prop.put("showtable", 1);
            prop.put("showtable_table", table);
            
            // insert the columns
            
            for (int i = 0; i < columns.size(); i++) {
                prop.putHTML("showtable_columns_" + i + "_header", columns.get(i));
            }
            prop.put("showtable_columns", columns.size());
            
            // insert all rows
            final int maxCount = Math.min(1000, sb.tables.size(table));
            final Iterator<Map.Entry<byte[], Map<String, byte[]>>> mapIterator = sb.tables.iterator(table);
            Map.Entry<byte[], Map<String, byte[]>> record;
            Map<String, byte[]> map;
            byte[] pk;
            count = 0;
            boolean dark = true;
            byte[] cell;
            while ((mapIterator.hasNext()) && (count < maxCount)) {
                record = mapIterator.next();
                if (record == null) continue;
                pk = record.getKey();
                map = record.getValue();
                prop.put("showtable_list_" + count + "_dark", ((dark) ? 1 : 0) ); dark=!dark;
                prop.put("showtable_list_" + count + "_pk",  new String(pk));
                for (int i = 0; i < columns.size(); i++) {
                    cell = map.get(columns.get(i));
                    prop.putHTML("showtable_list_" + count + "_columns_" + i + "_cell", cell == null ? "" : new String(cell));
                }
                prop.put("showtable_list_" + count + "_columns", columns.size());
                
                count++;
            }
            prop.put("showtable_list", count);
        } catch (IOException e) {}
        
        if (post != null && table != null && post.containsKey("editrow")) try {
            // check if we can find a key
            String pk = null;
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getKey().startsWith("mark_") && entry.getValue().equals("on")) {
                    pk = entry.getKey().substring(5);
                    break;
                }
            }
            if (pk != null && sb.tables.has(table, pk.getBytes())) {
                setEdit(sb, prop, table, pk, columns);
            }
        } catch (IOException e) {}
        
        if (post != null && table != null && post.containsKey("addrow")) try {
            // get a new key
            String pk = new String(sb.tables.insert(table, new HashMap<String, byte[]>()));
            setEdit(sb, prop, table, pk, columns);
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        
        // adding the peer address
        prop.put("address", sb.peers.mySeed().getPublicAddress());
        
        // return rewrite properties
        return prop;
    }
    
    private static void setEdit(final Switchboard sb, final serverObjects prop, final String table, final String pk, List<String> columns) throws IOException {
        prop.put("showedit", 1);
        prop.put("showedit_table", table);
        prop.put("showedit_pk", pk);
        Map<String, byte[]> map = sb.tables.select(table, pk.getBytes());
        int count = 0;
        byte[] cell;
        for (String col: columns) {
            cell = map.get(col);
            prop.put("showedit_list_" + count + "_key", col);
            prop.put("showedit_list_" + count + "_value", cell == null ? "" : new String(cell));
            count++;
        }
        prop.put("showedit_list", count);
    }
}
