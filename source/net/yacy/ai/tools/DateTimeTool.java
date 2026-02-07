/**
 *  DateTimeTool
 *  Copyright 2026 by Michael Peter Christen
 *  First released 06.02.2026 at https://yacy.net
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

package net.yacy.ai.tools;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.ToolHandler;

public class DateTimeTool implements ToolHandler {

    private static final String NAME = "datetime";
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public JSONObject definition() throws JSONException {
        JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "Get the current date and time. Optionally specify an IANA timezone.");
        JSONObject params = new JSONObject(true);
        params.put("type", "object");
        JSONObject props = new JSONObject(true);
        JSONObject tz = new JSONObject(true);
        tz.put("type", "string");
        tz.put("description", "IANA timezone name like America/New_York. Defaults to local time zone.");
        props.put("timezone", tz);
        params.put("properties", props);
        params.put("required", new JSONArray());
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    
    public int maxCallsPerTurn() {
        return 1;
    }

    
    public String execute(String arguments) {
        String timezone = null;
        if (arguments != null && !arguments.isEmpty()) {
            try {
                JSONObject obj = new JSONObject(arguments);
                timezone = obj.optString("timezone", null);
            } catch (JSONException e) {
                return ToolHandler.errorJson("Invalid arguments JSON");
            }
        }

        ZoneId localZone = ZoneId.systemDefault();
        ZoneId zone = localZone;
        String note = null;
        if (timezone != null && !timezone.isEmpty()) {
            try {
                zone = ZoneId.of(timezone);
            } catch (Exception e) {
                zone = localZone;
                note = "Invalid timezone \"" + timezone + "\". Used local timezone \"" + localZone.getId() + "\".";
            }
        }

        ZonedDateTime now = ZonedDateTime.now(zone);
        try {
            JSONObject result = new JSONObject(true);
            result.put("datetime", now.format(DATETIME_FORMAT));
            result.put("timezone", zone.getId());
            result.put("time_of_day", classifyTimeOfDay(now.getHour()));
            result.put("utc_offset_minutes", now.getOffset().getTotalSeconds() / 60);
            result.put("utc_datetime", Instant.now().toString());
            result.put("epoch_ms", System.currentTimeMillis());
            if (note != null) result.put("note", note);
            return result.toString();
        } catch (JSONException e) {
            return ToolHandler.errorJson("Failed to build datetime response");
        }
    }

    private static String classifyTimeOfDay(int hour) {
        if (hour < 0 || hour > 23) return "night";
        if (hour < 6) return "night";
        if (hour < 12) return "morning";
        if (hour < 18) return "afternoon";
        return "evening";
    }
}
