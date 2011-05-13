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
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;

import de.anomic.data.WorkTables;
import de.anomic.search.QueryParams;
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
        
        int startRecord = 0;
        int maximumRecords = 25;
        Pattern query = QueryParams.catchall_pattern;
        if (post != null && post.containsKey("startRecord")) startRecord = post.getInt("startRecord", 0);
        if (post != null && post.containsKey("maximumRecords")) maximumRecords = post.getInt("maximumRecords", 0);
        if (post != null && post.containsKey("query") && !post.get("query", "").isEmpty()) {
            query = Pattern.compile(".*" + post.get("query", "") + ".*");
            startRecord = 0;
            maximumRecords = 1000;
        }
        final boolean inline = (post != null && post.getBoolean("inline",false));

        prop.put("inline", (inline) ? 1 : 0);
        
        Pattern typefilter = QueryParams.catchall_pattern;
        if (post != null && post.containsKey("filter") && post.get("filter", "").length() > 0) {
            typefilter = Pattern.compile(post.get("filter", ".*"));
        }
        
        String pk;
        if (post != null && post.containsKey("repeat_select") && ((pk = post.get("pk")) != null)) try {
            final String action = post.get("repeat_select", "off");
            if (action.equals("on")) {
                Tables.Row row = sb.tables.select(WorkTables.TABLE_API_NAME, pk.getBytes());
                if (row != null) {
                    row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 7);
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
            final String action = post.get("repeat_time", "off");
            final Tables.Row row = sb.tables.select(WorkTables.TABLE_API_NAME, pk.getBytes());
            if (row != null) {
                if ("off".equals(action)) {
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
            final String action = post.get("repeat_unit", "seldays");
            final Tables.Row row = sb.tables.select(WorkTables.TABLE_API_NAME, pk.getBytes());
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
        
        if (post != null && !post.get("deleterows", "").isEmpty()) {
            for (final Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getValue().startsWith("mark_")) {
                    try {
                        sb.tables.delete(WorkTables.TABLE_API_NAME, entry.getValue().substring(5).getBytes());
                    } catch (IOException e) {
                        Log.logException(e);
                    }
                }
            }
        }

        if (post != null && !post.get("execrows", "").isEmpty()) {
            // create a time-ordered list of events to execute
            final Set<String> pks = new TreeSet<String>();
            for (final Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getValue().startsWith("mark_")) {
                    pks.add(entry.getValue().substring(5));
                }
            }
            
            // now call the api URLs and store the result status
            final Map<String, Integer> l = sb.tables.execAPICalls("localhost", (int) sb.getConfigLong("port", 8090), sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, ""), pks);
            
            // construct result table
            prop.put("showexec", l.size() > 0 ? 1 : 0);
            
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
        final List<Tables.Row> table = new ArrayList<Tables.Row>(maximumRecords);
        int count = 0;
        int tablesize = 0;
        try {
            tablesize = sb.tables.size(WorkTables.TABLE_API_NAME);
            final Iterator<Tables.Row> plainIterator = sb.tables.iterator(WorkTables.TABLE_API_NAME);
            final Iterator<Tables.Row> mapIterator = sb.tables.orderBy(plainIterator, -1, WorkTables.TABLE_API_COL_DATE_RECORDING).iterator();
            Tables.Row r;
            boolean dark = true;
            boolean scheduledactions = false;
            int c = 0;
            String type, comment;
            // first prepare a list
            while (mapIterator.hasNext()) {
                r = mapIterator.next();
                if (r == null) continue;
                type = UTF8.String(r.get(WorkTables.TABLE_API_COL_TYPE));
                if (!typefilter.matcher(type).matches()) continue;
                comment = UTF8.String(r.get(WorkTables.TABLE_API_COL_COMMENT));
                if (!query.matcher(comment).matches()) continue;
                if (c >= startRecord) table.add(r);
                c++;
                if (table.size() >= maximumRecords) break;
            }
            // then work on the list
            for (final Tables.Row row: table) {
                final Date now = new Date();
                final Date date = row.containsKey(WorkTables.TABLE_API_COL_DATE) ? row.get(WorkTables.TABLE_API_COL_DATE, now) : null;
                final Date date_recording = row.get(WorkTables.TABLE_API_COL_DATE_RECORDING, date);
                final Date date_last_exec = row.get(WorkTables.TABLE_API_COL_DATE_LAST_EXEC, date);
                final Date date_next_exec = row.get(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, (Date) null);
                final int callcount = row.get(WorkTables.TABLE_API_COL_APICALL_COUNT, 1);
                final String unit = row.get(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_UNIT, "days");
                final int time = row.get(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 0);
                prop.put("showtable_list_" + count + "_inline", inline ? 1 : 0);
                prop.put("showtable_list_" + count + "_dark", dark ? 1 : 0); dark=!dark;
                prop.put("showtable_list_" + count + "_pk", UTF8.String(row.getPK()));
                prop.put("showtable_list_" + count + "_count", count);
                prop.put("showtable_list_" + count + "_callcount", callcount);
                prop.put("showtable_list_" + count + "_dateRecording", date_recording == null ? "-" : DateFormat.getDateTimeInstance().format(date_recording));
                prop.put("showtable_list_" + count + "_dateLastExec",  date_last_exec == null ? "-" : DateFormat.getDateTimeInstance().format(date_last_exec));
                prop.put("showtable_list_" + count + "_dateNextExec",  date_next_exec == null ? "-" : DateFormat.getDateTimeInstance().format(date_next_exec));
                prop.put("showtable_list_" + count + "_selectedMinutes", unit.equals("minutes") ? 1 : 0);
                prop.put("showtable_list_" + count + "_selectedHours", unit.equals("hours") ? 1 : 0);
                prop.put("showtable_list_" + count + "_selectedDays", (unit.length() == 0 || unit.equals("days")) ? 1 : 0);
                prop.put("showtable_list_" + count + "_repeatTime", time);
                prop.put("showtable_list_" + count + "_type", row.get(WorkTables.TABLE_API_COL_TYPE));
                prop.put("showtable_list_" + count + "_comment", row.get(WorkTables.TABLE_API_COL_COMMENT));
                prop.putHTML("showtable_list_" + count + "_inline_url", "http://" + sb.myPublicIP() + ":" + sb.getConfig("port", "8090") + UTF8.String(row.get(WorkTables.TABLE_API_COL_URL)));

                if (time == 0) {
                    prop.put("showtable_list_" + count + "_scheduler", 0);
                    prop.put("showtable_list_" + count + "_scheduler_pk", UTF8.String(row.getPK()));
                } else {
                    scheduledactions = true;
                    prop.put("showtable_list_" + count + "_scheduler", 1);
                    prop.put("showtable_list_" + count + "_scheduler_pk", UTF8.String(row.getPK()));
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
                prop.put("showtable_list_" + count + "_scheduler_filter", typefilter.pattern());
                prop.put("showtable_list_" + count + "_scheduler_query", query.pattern());
                prop.put("showtable_list_" + count + "_scheduler_startRecord", startRecord);
                prop.put("showtable_list_" + count + "_scheduler_maximumRecords", maximumRecords);
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
        
        // write navigation details
        prop.put("showtable_startRecord", startRecord);
        prop.put("showtable_maximumRecords", maximumRecords);
        prop.put("showtable_inline", (inline) ? 1 : 0);
        prop.put("showtable_filter", typefilter.pattern());
        prop.put("showtable_query", query.pattern().replaceAll("\\.\\*", ""));
        if (tablesize >= 50) {
            prop.put("showtable_navigation", 1);
            prop.put("showtable_navigation_startRecord", startRecord);
            prop.put("showtable_navigation_to", Math.min(tablesize, startRecord + maximumRecords));
            prop.put("showtable_navigation_of", tablesize);
            prop.put("showtable_navigation_left", startRecord == 0 ? 0 : 1);
            prop.put("showtable_navigation_left_startRecord", Math.max(0, startRecord - maximumRecords));
            prop.put("showtable_navigation_left_maximumRecords", maximumRecords);
            prop.put("showtable_navigation_left_inline", (inline) ? 1 : 0);
            prop.put("showtable_navigation_left_filter", typefilter.pattern());
            prop.put("showtable_navigation_left", startRecord == 0 ? 0 : 1);
            prop.put("showtable_navigation_filter", typefilter.pattern());
            prop.put("showtable_navigation_right", startRecord + maximumRecords >= tablesize ? 0 : 1);
            prop.put("showtable_navigation_right_startRecord", Math.min(tablesize - maximumRecords, startRecord + maximumRecords));
            prop.put("showtable_navigation_right_maximumRecords", maximumRecords);
            prop.put("showtable_navigation_right_inline", (inline) ? 1 : 0);
            prop.put("showtable_navigation_right_filter", typefilter.pattern());
        } else {
            prop.put("showtable_navigation", 0);
        }
        
        // return rewrite properties
        return prop;
    }
    
}
