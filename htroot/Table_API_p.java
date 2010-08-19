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
import java.text.DateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;

import de.anomic.data.WorkTables;
import de.anomic.http.server.RequestHeader;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Table_API_p {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        prop.put("showexec", 0);
        prop.put("showtable", 0);
        
        prop.put("inline", 0);
        boolean inline = false;
        if (post != null && post.get("inline","false").equals("true")) {
            prop.put("inline", 1);
            inline = true;
        }
        
        String typefilter = ".*";
        if (post != null && post.containsKey("filter")) {
            typefilter = post.get("filter", ".*");
        }
        
        String pk;
        if (post != null && post.containsKey("repeat_select") && ((pk = post.get("pk")) != null)) try {
            String action = post.get("repeat_select", "off");
            if (action.equals("on")) {
                Tables.Row row = sb.tables.select(WorkTables.TABLE_API_NAME, pk.getBytes());
                if (row != null) {
                    row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 1);
                    row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_UNIT, "days");
                    WorkTables.calculateAPIScheduler(row, false);
                    sb.tables.update(WorkTables.TABLE_API_NAME, row);
                }
            }
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        
        if (post != null && post.containsKey("repeat_time") && ((pk = post.get("pk")) != null)) try {
            String action = post.get("repeat_time", "off");
            Tables.Row row = sb.tables.select(WorkTables.TABLE_API_NAME, pk.getBytes());
            if (row != null) {
                if (action.equals("off")) {
                    row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 0);
                } else {
                    row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, Integer.parseInt(action));
                }
                WorkTables.calculateAPIScheduler(row, false);
                sb.tables.update(WorkTables.TABLE_API_NAME, row);
            }
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        
        if (post != null && post.containsKey("repeat_unit") && ((pk = post.get("pk")) != null)) try {
            String action = post.get("repeat_unit", "seldays");
            Tables.Row row = sb.tables.select(WorkTables.TABLE_API_NAME, pk.getBytes());
            if (row != null) {
                int time = row.get(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 1);
                row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_UNIT, action.substring(3));
                if (action.equals("selminutes") && time > 0 && time < 10) row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 10);
                if (action.equals("selminutes") && time > 50) row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 50);
                if (action.equals("selhours") && time > 23) row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 23);
                if (action.equals("seldays") && time > 30) row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 30);
                WorkTables.calculateAPIScheduler(row, false);
                sb.tables.update(WorkTables.TABLE_API_NAME, row);
            }
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        
        if (post != null && post.get("deleterows", "").length() > 0) {
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getValue().startsWith("mark_")) {
                    try {
                        sb.tables.delete(WorkTables.TABLE_API_NAME, entry.getValue().substring(5).getBytes());
                    } catch (IOException e) {
                        Log.logException(e);
                    }
                }
            }
        }

        if (post != null && post.get("execrows", "").length() > 0) {
            // create a time-ordered list of events to execute
            TreeSet<String> pks = new TreeSet<String>();
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getValue().startsWith("mark_")) {
                    pks.add(entry.getValue().substring(5));
                }
            }
            
            // now call the api URLs and store the result status
            Map<String, Integer> l = sb.tables.execAPICall(pks, "localhost", (int) sb.getConfigLong("port", 8080), sb.getConfig("adminAccountBase64MD5", ""));
            
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
        prop.put("showtable_inline", inline ? 1 : 0);
        
        // insert rows
        int count = 0;
        try {
            final Iterator<Tables.Row> plainIterator = sb.tables.iterator(WorkTables.TABLE_API_NAME);
            final Iterator<Tables.Row> mapIterator = sb.tables.orderBy(plainIterator, -1, WorkTables.TABLE_API_COL_DATE_RECORDING).iterator();
            Tables.Row row;
            boolean dark = true;
            boolean scheduledactions = false;
            while (mapIterator.hasNext()) {
                row = mapIterator.next();
                if (row == null) continue;
                String type = new String(row.get(WorkTables.TABLE_API_COL_TYPE));
                if (!type.matches(typefilter)) continue;
                Date now = new Date();
                Date date = row.containsKey(WorkTables.TABLE_API_COL_DATE) ? row.get(WorkTables.TABLE_API_COL_DATE, now) : null;
                Date date_recording = row.get(WorkTables.TABLE_API_COL_DATE_RECORDING, date);
                Date date_last_exec = row.get(WorkTables.TABLE_API_COL_DATE_LAST_EXEC, date);
                Date date_next_exec = row.containsKey(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC) ? row.get(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, now) : null;
                int callcount = row.get(WorkTables.TABLE_API_COL_APICALL_COUNT, 1);
                String unit = row.get(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_UNIT, "days");
                int time = row.get(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 0);
                prop.put("showtable_list_" + count + "_inline", inline ? 1 : 0);
                prop.put("showtable_list_" + count + "_dark", dark ? 1 : 0); dark=!dark;
                prop.put("showtable_list_" + count + "_pk", new String(row.getPK()));
                prop.put("showtable_list_" + count + "_count", count);
                prop.put("showtable_list_" + count + "_callcount", callcount);
                prop.put("showtable_list_" + count + "_dateRecording", date_recording == null ? "-" : DateFormat.getDateTimeInstance().format(date_recording));
                prop.put("showtable_list_" + count + "_dateLastExec",  date_last_exec == null ? "-" : DateFormat.getDateTimeInstance().format(date_last_exec));
                prop.put("showtable_list_" + count + "_dateNextExec",  date_next_exec == null ? "-" : DateFormat.getDateTimeInstance().format(date_next_exec));
                prop.put("showtable_list_" + count + "_selectedMinutes", unit.equals("minutes") ? 1 : 0);
                prop.put("showtable_list_" + count + "_selectedHours", unit.equals("hours") ? 1 : 0);
                prop.put("showtable_list_" + count + "_selectedDays", (unit.length() == 0 || unit.equals("days")) ? 1 : 0);
                prop.put("showtable_list_" + count + "_repeatTime", time);
                prop.put("showtable_list_" + count + "_type", type);
                prop.put("showtable_list_" + count + "_comment", row.get(WorkTables.TABLE_API_COL_COMMENT));
                prop.put("showtable_list_" + count + "_inline_url", "http://" + sb.myPublicIP() + ":" + sb.getConfig("port", "8080") + new String(row.get(WorkTables.TABLE_API_COL_URL)));

                if (time == 0) {
                    prop.put("showtable_list_" + count + "_scheduler", 0);
                    prop.put("showtable_list_" + count + "_scheduler_pk", new String(row.getPK()));
                } else {
                    scheduledactions = true;
                    prop.put("showtable_list_" + count + "_scheduler", 1);
                    prop.put("showtable_list_" + count + "_scheduler_pk", new String(row.getPK()));
                    prop.put("showtable_list_" + count + "_scheduler_scale_" + 0 + "_time", "off");
                    prop.put("showtable_list_" + count + "_scheduler_selectedMinutes", 0);
                    prop.put("showtable_list_" + count + "_scheduler_selectedHours", 0);
                    prop.put("showtable_list_" + count + "_scheduler_selectedDays", 0);
                    if (unit.equals("minutes")) {
                        for (int i = 1; i <= 5 ; i++) {
                            prop.put("showtable_list_" + count + "_scheduler_scale_" + i + "_time", i * 10);
                            prop.put("showtable_list_" + count + "_scheduler_scale_" + i + "_selected", 0);
                        }
                        prop.put("showtable_list_" + count + "_scheduler_scale_" + (time / 10) + "_selected", 1);
                        prop.put("showtable_list_" + count + "_scheduler_scale", 6);
                        prop.put("showtable_list_" + count + "_scheduler_selectedMinutes", 1);
                    } else if (unit.equals("hours")) {
                        for (int i = 1; i <= 23 ; i++) {
                            prop.put("showtable_list_" + count + "_scheduler_scale_" + i + "_time", i);
                            prop.put("showtable_list_" + count + "_scheduler_scale_" + i + "_selected", 0);
                        }
                        prop.put("showtable_list_" + count + "_scheduler_scale_" + time + "_selected", 1);
                        prop.put("showtable_list_" + count + "_scheduler_scale", 24);
                        prop.put("showtable_list_" + count + "_scheduler_selectedHours", 1);
                    } else {
                        for (int i = 1; i <= 30 ; i++) {
                            prop.put("showtable_list_" + count + "_scheduler_scale_" + i + "_time", i);
                            prop.put("showtable_list_" + count + "_scheduler_scale_" + i + "_selected", 0);
                        }
                        prop.put("showtable_list_" + count + "_scheduler_scale_" + time + "_selected", 1);
                        prop.put("showtable_list_" + count + "_scheduler_scale", 31);
                        prop.put("showtable_list_" + count + "_scheduler_selectedDays", 1);
                    }
                }
                prop.put("showtable_list_" + count + "_scheduler_inline", inline ? "true" : "false");
                prop.put("showtable_list_" + count + "_scheduler_filter", typefilter);
                count++;
            }
            if (scheduledactions) {
                prop.put("showschedulerhint", 1);
                prop.put("showschedulerhint_tfminutes", sb.getConfigLong(SwitchboardConstants.CLEANUP_BUSYSLEEP, 300000) / 60000);
            } else {
                prop.put("showschedulerhint", 0);
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
