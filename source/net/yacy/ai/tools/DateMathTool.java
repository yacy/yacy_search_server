/**
 *  DateMathTool
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

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.ToolHandler;

public class DateMathTool implements ToolHandler {

    private static final String NAME = "date_math";

    @Override
    public JSONObject definition() throws JSONException {
        JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "Perform deterministic date/time operations: add, subtract, diff, start_of, end_of.");

        JSONObject params = new JSONObject(true);
        params.put("type", "object");
        JSONObject props = new JSONObject(true);
        props.put("operation", new JSONObject(true).put("type", "string").put("description", "add|subtract|diff|start_of|end_of"));
        props.put("datetime", new JSONObject(true).put("type", "string").put("description", "Base datetime/date in ISO format."));
        props.put("other_datetime", new JSONObject(true).put("type", "string").put("description", "Second datetime for diff."));
        props.put("amount", new JSONObject(true).put("type", "integer").put("description", "Amount for add/subtract."));
        props.put("unit", new JSONObject(true).put("type", "string").put("description", "minute|hour|day|week|month|year"));
        props.put("period", new JSONObject(true).put("type", "string").put("description", "day|week|month|year (for start_of/end_of)."));
        props.put("timezone", new JSONObject(true).put("type", "string").put("description", "IANA zone; default system zone."));
        params.put("properties", props);
        params.put("required", new JSONArray().put("operation"));
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    
    public int maxCallsPerTurn() {
        return 1;
    }

    
    public String execute(String arguments) {
        final JSONObject args;
        try {
            args = (arguments == null || arguments.isEmpty()) ? new JSONObject(true) : new JSONObject(arguments);
        } catch (JSONException e) {
            return ToolHandler.errorJson("Invalid arguments JSON");
        }

        String op = args.optString("operation", "").trim().toLowerCase(Locale.ROOT);
        if (op.isEmpty()) return ToolHandler.errorJson("Missing operation");

        ZoneId zone = parseZone(args.optString("timezone", ""));
        if (zone == null) return ToolHandler.errorJson("Invalid timezone");
        ZonedDateTime base = parseDateTime(args.optString("datetime", ""), zone);
        if (base == null) base = ZonedDateTime.now(zone);

        try {
            JSONObject out = new JSONObject(true);
            out.put("tool", NAME);
            out.put("operation", op);
            out.put("timezone", zone.getId());
            out.put("input_datetime", base.toString());

            if ("add".equals(op) || "subtract".equals(op)) {
                int amount = args.optInt("amount", Integer.MIN_VALUE);
                if (amount == Integer.MIN_VALUE) return ToolHandler.errorJson("Missing amount");
                String unit = normalizeUnit(args.optString("unit", ""));
                if (unit == null) return ToolHandler.errorJson("Invalid unit");
                long signed = "subtract".equals(op) ? -((long) amount) : amount;
                ZonedDateTime result = applyAdd(base, signed, unit);
                out.put("result_datetime", result.toString());
                out.put("result_epoch_ms", result.toInstant().toEpochMilli());
                return out.toString();
            }

            if ("diff".equals(op)) {
                ZonedDateTime other = parseDateTime(args.optString("other_datetime", ""), zone);
                if (other == null) return ToolHandler.errorJson("Missing or invalid other_datetime");
                Duration d = Duration.between(base.toInstant(), other.toInstant());
                out.put("other_datetime", other.toString());
                out.put("difference_seconds", d.getSeconds());
                out.put("difference_minutes", d.toMinutes());
                out.put("difference_hours", d.toHours());
                out.put("difference_days", d.toDays());
                return out.toString();
            }

            if ("start_of".equals(op) || "end_of".equals(op)) {
                String period = args.optString("period", "").trim().toLowerCase(Locale.ROOT);
                if (period.isEmpty()) return ToolHandler.errorJson("Missing period");
                ZonedDateTime result = "start_of".equals(op) ? startOf(base, period) : endOf(base, period);
                out.put("period", period);
                out.put("result_datetime", result.toString());
                out.put("result_epoch_ms", result.toInstant().toEpochMilli());
                return out.toString();
            }
            return ToolHandler.errorJson("Unsupported operation: " + op);
        } catch (IllegalArgumentException | JSONException e) {
            return ToolHandler.errorJson(e.getMessage());
        }
    }

    private static ZoneId parseZone(String timezone) {
        if (timezone == null || timezone.trim().isEmpty()) return ZoneId.systemDefault();
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static ZonedDateTime parseDateTime(String value, ZoneId zone) {
        if (value == null || value.trim().isEmpty()) return null;
        String v = value.trim();
        try {
            return ZonedDateTime.parse(v);
        } catch (DateTimeParseException e) {}
        try {
            LocalDateTime ldt = LocalDateTime.parse(v, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return ldt.atZone(zone);
        } catch (DateTimeParseException e) {}
        try {
            LocalDate ld = LocalDate.parse(v, DateTimeFormatter.ISO_LOCAL_DATE);
            return ld.atStartOfDay(zone);
        } catch (DateTimeParseException e) {}
        return null;
    }

    private static String normalizeUnit(String unit) {
        if (unit == null) return null;
        String u = unit.trim().toLowerCase(Locale.ROOT);
        if ("minutes".equals(u)) return "minute";
        if ("hours".equals(u)) return "hour";
        if ("days".equals(u)) return "day";
        if ("weeks".equals(u)) return "week";
        if ("months".equals(u)) return "month";
        if ("years".equals(u)) return "year";
        if ("minute".equals(u) || "hour".equals(u) || "day".equals(u) || "week".equals(u) || "month".equals(u) || "year".equals(u)) return u;
        return null;
    }

    private static ZonedDateTime applyAdd(ZonedDateTime dt, long amount, String unit) {
        if ("minute".equals(unit)) return dt.plus(amount, ChronoUnit.MINUTES);
        if ("hour".equals(unit)) return dt.plus(amount, ChronoUnit.HOURS);
        if ("day".equals(unit)) return dt.plus(amount, ChronoUnit.DAYS);
        if ("week".equals(unit)) return dt.plus(amount, ChronoUnit.WEEKS);
        if ("month".equals(unit)) return dt.plus(amount, ChronoUnit.MONTHS);
        if ("year".equals(unit)) return dt.plus(amount, ChronoUnit.YEARS);
        throw new IllegalArgumentException("Invalid unit: " + unit);
    }

    private static ZonedDateTime startOf(ZonedDateTime dt, String period) {
        if ("day".equals(period)) {
            return dt.toLocalDate().atStartOfDay(dt.getZone());
        }
        if ("week".equals(period)) {
            LocalDate date = dt.toLocalDate();
            while (date.getDayOfWeek() != DayOfWeek.MONDAY) date = date.minusDays(1);
            return date.atStartOfDay(dt.getZone());
        }
        if ("month".equals(period)) {
            LocalDate first = YearMonth.from(dt).atDay(1);
            return first.atStartOfDay(dt.getZone());
        }
        if ("year".equals(period)) {
            LocalDate first = LocalDate.of(dt.getYear(), 1, 1);
            return first.atStartOfDay(dt.getZone());
        }
        throw new IllegalArgumentException("Invalid period: " + period);
    }

    private static ZonedDateTime endOf(ZonedDateTime dt, String period) {
        if ("day".equals(period)) return startOf(dt, "day").plusDays(1).minusNanos(1);
        if ("week".equals(period)) return startOf(dt, "week").plusWeeks(1).minusNanos(1);
        if ("month".equals(period)) return startOf(dt, "month").plusMonths(1).minusNanos(1);
        if ("year".equals(period)) return startOf(dt, "year").plusYears(1).minusNanos(1);
        throw new IllegalArgumentException("Invalid period: " + period);
    }
}
