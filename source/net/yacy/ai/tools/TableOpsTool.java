/**
 *  TableOpsTool
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.ToolHandler;

public class TableOpsTool implements ToolHandler {

    private static final String NAME = "table_ops";

    @Override
    public JSONObject definition() throws JSONException {
        JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "Apply deterministic table operations to a JSON array of row objects.");

        JSONObject params = new JSONObject(true);
        params.put("type", "object");
        JSONObject props = new JSONObject(true);

        JSONObject rows = new JSONObject(true);
        rows.put("type", "array");
        rows.put("description", "Input table rows as an array of objects.");
        props.put("rows", rows);

        JSONObject operations = new JSONObject(true);
        operations.put("type", "array");
        operations.put("description", "Pipeline of operations: filter, sort, limit, project, group_count.");
        props.put("operations", operations);

        params.put("properties", props);
        params.put("required", new JSONArray().put("rows").put("operations"));
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    
    public int maxCallsPerTurn() {
        return 3;
    }

    
    public String execute(String arguments) {
        final JSONObject args;
        try {
            args = (arguments == null || arguments.isEmpty()) ? new JSONObject(true) : new JSONObject(arguments);
        } catch (JSONException e) {
            return ToolHandler.errorJson("Invalid arguments JSON");
        }

        final JSONArray rowsArray = args.optJSONArray("rows");
        final JSONArray ops = args.optJSONArray("operations");
        if (rowsArray == null) return ToolHandler.errorJson("Missing rows");
        if (ops == null) return ToolHandler.errorJson("Missing operations");

        try {
            List<JSONObject> rows = toObjectRows(rowsArray);
            for (int i = 0; i < ops.length(); i++) {
                JSONObject op = ops.optJSONObject(i);
                if (op == null) return ToolHandler.errorJson("Operation at index " + i + " must be an object");
                rows = applyOperation(rows, op);
            }
            JSONObject result = new JSONObject(true);
            result.put("row_count", rows.size());
            result.put("rows", new JSONArray(rows));
            return result.toString();
        } catch (IllegalArgumentException | JSONException e) {
            return ToolHandler.errorJson(e.getMessage());
        }
    }

    private static List<JSONObject> toObjectRows(JSONArray array) {
        List<JSONObject> rows = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) throw new IllegalArgumentException("Row at index " + i + " is not an object");
            rows.add(row);
        }
        return rows;
    }

    private static List<JSONObject> applyOperation(List<JSONObject> rows, JSONObject op) {
        String type = op.optString("type", "").trim().toLowerCase();
        if (type.isEmpty()) throw new IllegalArgumentException("Operation missing type");
        if ("filter".equals(type)) return applyFilter(rows, op);
        if ("sort".equals(type)) return applySort(rows, op);
        if ("limit".equals(type)) return applyLimit(rows, op);
        if ("project".equals(type)) return applyProject(rows, op);
        if ("group_count".equals(type)) return applyGroupCount(rows, op);
        throw new IllegalArgumentException("Unsupported operation type: " + type);
    }

    private static List<JSONObject> applyFilter(List<JSONObject> rows, JSONObject op) {
        String field = requiredField(op, "field");
        String cmp = op.optString("op", "eq").trim().toLowerCase();
        Object expected = op.opt("value");
        List<JSONObject> result = new ArrayList<>();
        for (JSONObject row : rows) {
            Object actual = row.opt(field);
            if (matches(actual, cmp, expected)) result.add(row);
        }
        return result;
    }

    private static List<JSONObject> applySort(List<JSONObject> rows, JSONObject op) {
        String field = requiredField(op, "field");
        String order = op.optString("order", "asc").trim().toLowerCase();
        final int direction = "desc".equals(order) ? -1 : 1;

        List<JSONObject> copy = new ArrayList<>(rows);
        Collections.sort(copy, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                Object va = a.opt(field);
                Object vb = b.opt(field);
                return direction * compareValues(va, vb);
            }
        });
        return copy;
    }

    private static List<JSONObject> applyLimit(List<JSONObject> rows, JSONObject op) {
        int count = op.optInt("count", -1);
        int offset = op.optInt("offset", 0);
        if (count < 0) throw new IllegalArgumentException("limit.count must be >= 0");
        if (offset < 0) throw new IllegalArgumentException("limit.offset must be >= 0");
        if (offset >= rows.size()) return new ArrayList<>();
        int end = Math.min(rows.size(), offset + count);
        return new ArrayList<>(rows.subList(offset, end));
    }

    private static List<JSONObject> applyProject(List<JSONObject> rows, JSONObject op) {
        JSONArray fields = op.optJSONArray("fields");
        if (fields == null || fields.length() == 0) throw new IllegalArgumentException("project.fields must be a non-empty array");
        List<String> keep = new ArrayList<>();
        for (int i = 0; i < fields.length(); i++) {
            String f = fields.optString(i, "").trim();
            if (!f.isEmpty()) keep.add(f);
        }
        if (keep.isEmpty()) throw new IllegalArgumentException("project.fields must contain at least one field");

        List<JSONObject> out = new ArrayList<>();
        for (JSONObject row : rows) {
            JSONObject projected = new JSONObject(true);
            for (String key : keep) {
                if (row.has(key)) {
                    try {
                        projected.put(key, row.get(key));
                    } catch (JSONException e) {
                        throw new IllegalArgumentException("Failed to project field: " + key);
                    }
                }
            }
            out.add(projected);
        }
        return out;
    }

    private static List<JSONObject> applyGroupCount(List<JSONObject> rows, JSONObject op) {
        String field = requiredField(op, "field");
        String order = op.optString("order", "desc").trim().toLowerCase();
        final int direction = "asc".equals(order) ? 1 : -1;

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JSONObject row : rows) {
            Object v = row.opt(field);
            String key = v == null || v == JSONObject.NULL ? "null" : String.valueOf(v);
            Integer cur = counts.get(key);
            counts.put(key, cur == null ? 1 : cur + 1);
        }

        List<JSONObject> grouped = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            try {
                JSONObject g = new JSONObject(true);
                g.put("key", entry.getKey());
                g.put("count", entry.getValue());
                grouped.add(g);
            } catch (JSONException e) {
                throw new IllegalArgumentException("Failed to build group_count result");
            }
        }
        Collections.sort(grouped, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return direction * Integer.compare(a.optInt("count", 0), b.optInt("count", 0));
            }
        });
        return grouped;
    }

    private static String requiredField(JSONObject obj, String field) {
        String value = obj.optString(field, "").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Missing " + field);
        return value;
    }

    private static boolean matches(Object actual, String cmp, Object expected) {
        if ("eq".equals(cmp)) return valuesEqual(actual, expected);
        if ("ne".equals(cmp)) return !valuesEqual(actual, expected);
        if ("gt".equals(cmp)) return compareValues(actual, expected) > 0;
        if ("gte".equals(cmp)) return compareValues(actual, expected) >= 0;
        if ("lt".equals(cmp)) return compareValues(actual, expected) < 0;
        if ("lte".equals(cmp)) return compareValues(actual, expected) <= 0;
        if ("contains".equals(cmp)) {
            if (actual == null || actual == JSONObject.NULL || expected == null || expected == JSONObject.NULL) return false;
            if (actual instanceof JSONArray) return arrayContains((JSONArray) actual, expected);
            return String.valueOf(actual).toLowerCase().contains(String.valueOf(expected).toLowerCase());
        }
        if ("in".equals(cmp)) {
            if (!(expected instanceof JSONArray)) return false;
            return arrayContains((JSONArray) expected, actual);
        }
        throw new IllegalArgumentException("Unsupported filter op: " + cmp);
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a == b) return true;
        if (a == null || a == JSONObject.NULL) return b == null || b == JSONObject.NULL;
        if (b == null || b == JSONObject.NULL) return false;
        Double na = asDouble(a);
        Double nb = asDouble(b);
        if (na != null && nb != null) return Double.compare(na, nb) == 0;
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private static int compareValues(Object a, Object b) {
        if (a == b) return 0;
        if (a == null || a == JSONObject.NULL) return -1;
        if (b == null || b == JSONObject.NULL) return 1;
        Double na = asDouble(a);
        Double nb = asDouble(b);
        if (na != null && nb != null) return Double.compare(na, nb);
        return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
    }

    private static Double asDouble(Object value) {
        if (value == null || value == JSONObject.NULL) return null;
        if (value instanceof Number) return Double.valueOf(((Number) value).doubleValue());
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean arrayContains(JSONArray array, Object wanted) {
        for (int i = 0; i < array.length(); i++) {
            if (valuesEqual(array.opt(i), wanted)) return true;
        }
        return false;
    }
}
