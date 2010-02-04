// Table_API_p.java
// -----------------------
// (C) 2010 by Michael Peter Christen; mc@yacy.net
// first published 01.02.2010 in Frankfurt, Germany on http://yacy.net
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.logging.Log;

import de.anomic.data.WorkTables;
import de.anomic.http.client.Client;
import de.anomic.http.server.RequestHeader;
import de.anomic.http.server.ResponseContainer;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Table_API_p {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        if (post != null && post.get("deleterows", "").length() > 0) {
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getKey().startsWith("mark_") && entry.getValue().equals("on")) {
                    try {
                        sb.tables.delete(WorkTables.TABLE_API_NAME, entry.getKey().substring(5).getBytes());
                    } catch (IOException e) {
                        Log.logException(e);
                    }
                }
            }
        }

        prop.put("showexec", 0);
        prop.put("showtable", 0);
        
        if (post != null && post.get("execrows", "").length() > 0) {
            // create a time-ordered list of events to execute
            TreeSet<String> pks = new TreeSet<String>();
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getKey().startsWith("mark_") && entry.getValue().equals("on")) {
                    pks.add(entry.getKey().substring(5));
                }
            }
            
            // now call the api URLs and store the result status
            final RequestHeader reqHeader = new RequestHeader();
            final Client client = new Client(120000, reqHeader);
            ResponseContainer result;
            LinkedHashMap<String, Integer> l = new LinkedHashMap<String, Integer>();
            for (String pk: pks) {
                try {
                    Tables.Row row = sb.tables.select(WorkTables.TABLE_API_NAME, pk.getBytes());
                    String url = "http://localhost:" + sb.getConfig("port", "8080") + new String(row.from(WorkTables.TABLE_API_COL_URL));
                    result = client.GET(url);
                    l.put(url, result.getStatusCode());
                } catch (IOException e) {
                    Log.logException(e);
                }
            }
            
            // construct result table
            prop.put("showexec", 1);
            
            final Iterator<Map.Entry<String, Integer>> resultIterator = l.entrySet().iterator();
            Map.Entry<String, Integer> record;
            int count = 0;
            boolean dark = true;
            while (resultIterator.hasNext()) {
                record = resultIterator.next();
                if (record == null) continue;
                prop.put("showexec_list_" + count + "_dark", ((dark) ? 1 : 0) ); dark=!dark;
                prop.put("showexec_list_" + count + "_status", record.getValue());
                prop.put("showexec_list_" + count + "_url", record.getKey());
                count++;
            }
            prop.put("showexec_list", count);
        }
        
        // generate table
        prop.put("showtable", 1);
        
        // insert rows
        int count = 0;
        try {
            final Iterator<Tables.Row> mapIterator = sb.tables.orderBy(WorkTables.TABLE_API_NAME, -1, WorkTables.TABLE_API_COL_DATE).iterator();
            Tables.Row row;
            boolean dark = true;
            while (mapIterator.hasNext()) {
                row = mapIterator.next();
                if (row == null) continue;
                prop.put("showtable_list_" + count + "_dark", ((dark) ? 1 : 0) ); dark=!dark;
                prop.put("showtable_list_" + count + "_pk", new String(row.getPK()));
                prop.put("showtable_list_" + count + "_date", row.from(WorkTables.TABLE_API_COL_DATE));
                prop.put("showtable_list_" + count + "_type", row.from(WorkTables.TABLE_API_COL_TYPE));
                prop.put("showtable_list_" + count + "_comment", row.from(WorkTables.TABLE_API_COL_COMMENT));
                prop.put("showtable_list_" + count + "_url", "http://" + sb.myPublicIP() + ":" + sb.getConfig("port", "8080") + new String(row.from(WorkTables.TABLE_API_COL_URL)));
                count++;
            }
        } catch (IOException e) {
            Log.logException(e);
        }
        prop.put("showtable_list", count);
        
        // return rewrite properties
        return prop;
    }
    
}
