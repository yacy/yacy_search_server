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

import net.yacy.kelondro.logging.Log;

import de.anomic.data.Tables;
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
                    sb.tables.delete(Tables.API_TABLENAME, entry.getKey().substring(5).getBytes());
                }
            }
        }

        prop.put("showexec", 0);
        prop.put("showtable", 0);
        
        if (post != null && post.get("execrows", "").length() > 0) {
            final RequestHeader reqHeader = new RequestHeader();
            final Client client = new Client(120000, reqHeader);
            ResponseContainer result;
            LinkedHashMap<String, Integer> l = new LinkedHashMap<String, Integer>();
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getKey().startsWith("mark_") && entry.getValue().equals("on")) {
                    Map<String, byte[]> map = sb.tables.select(Tables.API_TABLENAME, entry.getKey().substring(5).getBytes());
                    String url = "http://localhost:" + sb.getConfig("port", "8080") + new String(map.get(Tables.API_COL_URL));
                    try {
                        result = client.GET(url);
                        l.put(url, result.getStatusCode());
                    } catch (IOException e) {
                        Log.logException(e);
                    }
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
        final int maxCount = Math.min(1000, sb.tables.size(Tables.API_TABLENAME));
        final Iterator<Map.Entry<byte[], Map<String, byte[]>>> mapIterator = sb.tables.iterator(Tables.API_TABLENAME);
        Map.Entry<byte[], Map<String, byte[]>> record;
        Map<String, byte[]> map;
        int count = 0;
        boolean dark = true;
        while ((mapIterator.hasNext()) && (count < maxCount)) {
            record = mapIterator.next();
            if (record == null) continue;
            map = record.getValue();
            prop.put("showtable_list_" + count + "_dark", ((dark) ? 1 : 0) ); dark=!dark;
            prop.put("showtable_list_" + count + "_pk", new String(record.getKey()));
            prop.put("showtable_list_" + count + "_date", map.get(Tables.API_COL_DATE));
            prop.put("showtable_list_" + count + "_type", map.get(Tables.API_COL_TYPE));
            prop.put("showtable_list_" + count + "_comment", map.get(Tables.API_COL_COMMENT));
            prop.put("showtable_list_" + count + "_url", "http://" + sb.myPublicIP() + ":" + sb.getConfig("port", "8080") + new String(map.get(Tables.API_COL_URL)));
            count++;
        }
        prop.put("showtable_list", count);
        
        // return rewrite properties
        return prop;
    }
    
}
