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
import java.util.Iterator;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.logging.Log;

import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class table_p {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        String table = (post == null) ? null : post.get("table", null);
        if (table != null && !sb.tables.hasHeap(table)) table = null;
        prop.put("showtable", 0);
        
        if (table == null) return prop;
        
        boolean showpk = post.containsKey("pk");
        
        String selectKey = post.containsKey("selectKey") ? post.get("selectKey") : null;
        String selectValue = (selectKey != null && post.containsKey("selectValue")) ? post.get("selectValue") : null;
        
        ArrayList<String> columns = null;
        try {
            columns = sb.tables.columns(table);
        } catch (IOException e) {
            Log.logException(e);
            columns = new ArrayList<String>();
        }
        
        // if a row attribute is given
        // then order the columns according to the given order
        String[] row = post.get("row", "").split(",");
        for (int i = 0; i < row.length; i++) {
            if (columns.contains(row[i])) {
                columns.remove(row[i]);
                if (i < columns.size()) columns.add(i, row[i]);
            }
        }
        
        // generate table
        prop.put("showtable", 1);
        prop.put("showtable_table", table);
        
        // insert the columns
        prop.put("showtable_showpk", showpk ? 1 : 0);
        for (int i = 0; i < columns.size(); i++) {
            prop.putHTML("showtable_columns_" + i + "_header", columns.get(i));
        }
        prop.put("showtable_columns", columns.size());
        
        // insert all rows
        int maxCount;
        try {
            maxCount = Math.min(1000, sb.tables.size(table));
        } catch (IOException e) {
            Log.logException(e);
            maxCount = 0;
        }
        int count = 0;
        try {
            final Iterator<Tables.Row> plainIterator = sb.tables.iterator(table);
            final Iterator<Tables.Row> mapIterator = sb.tables.orderByPK(plainIterator, maxCount).iterator();
            Tables.Row trow;
            boolean dark = true;
            String cellName, cellValue;
            rowloop: while ((mapIterator.hasNext()) && (count < maxCount)) {
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
                    prop.putHTML("showtable_list_" + count + "_columns_" + i + "_column", cellName);
                    prop.putHTML("showtable_list_" + count + "_columns_" + i + "_cell", cellValue);
                }
                prop.put("showtable_list_" + count + "_columns", columns.size());
                
                count++;
            }
        } catch (IOException e) {
            Log.logException(e);
        }
        prop.put("showtable_list", count);
        prop.put("showtable_num", count);
        
        // return rewrite properties
        return prop;
    }
    
}
