/**
 *  ArtifactEnvelopeTool
 *  Copyright 2026 by Michael Peter Christen
 *  First released 11.02.2026 at https://yacy.net
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.yacy.ai.ToolHandler;

/**
 * Build a renderer-ready artifact envelope from structured context data.
 *
 * <h2>Introduction: idea and principle</h2>
 * This tool bridges two worlds:
 * <ul>
 *   <li><b>LLM/tool output</b>: plain structured JSON data extracted from conversation context.</li>
 *   <li><b>Frontend visualization</b>: a dedicated renderer that expects a normalized artifact envelope.</li>
 * </ul>
 *
 * The central idea is to make visualization generation deterministic and transparent.
 * Instead of asking the model to invent arbitrary UI markup, this tool converts known
 * data into a stable envelope schema:
 * <pre>
 * {
 *   "kind": "artifact",
 *   "artifact_type": "chart|graph",
 *   "renderer": "...",
 *   "spec": { ... renderer-specific payload ... }
 * }
 * </pre>
 *
 * <h3>Design principles</h3>
 * <ul>
 *   <li><b>Deterministic mapping</b>: same input structure yields same artifact shape.</li>
 *   <li><b>Schema-first output</b>: frontend can render without reinterpreting natural language.</li>
 *   <li><b>Heuristic, but explainable</b>: lightweight field inference for x/y/series or graph edges.</li>
 *   <li><b>Fail fast</b>: invalid or insufficient data returns explicit tool errors.</li>
 * </ul>
 *
 * <h3>Transformation strategy</h3>
 * <ol>
 *   <li>Normalize incoming {@code data} into a row array (JSON objects).</li>
 *   <li>Choose artifact mode:
 *     <ul>
 *       <li>Graph when input looks like an edge list or caller hints {@code graph}.</li>
 *       <li>Chart otherwise (default path).</li>
 *     </ul>
 *   </li>
 *   <li>Infer missing fields (x/y/series or source/target) from row content.</li>
 *   <li>Emit envelope with renderer-specific {@code spec}:
 *     <ul>
 *       <li>Vega-Lite for charts</li>
 *       <li>Cytoscape-style node/edge spec for graphs</li>
 *     </ul>
 *   </li>
 * </ol>
 */
public class ArtifactEnvelopeTool implements ToolHandler {

    private static final String NAME = "artifact_envelope";
    private static final String VEGA_SCHEMA = "https://vega.github.io/schema/vega-lite/v5.json";

    @Override
    public JSONObject definition() throws JSONException {
        final JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        final JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "Transform structured context data into a graphical artifact envelope for chart or graph rendering.");

        final JSONObject params = new JSONObject(true);
        params.put("type", "object");
        final JSONObject props = new JSONObject(true);

        props.put("data", new JSONObject(true)
                .put("description", "Structured input data. Prefer array of row objects; object or JSON string also accepted."));
        props.put("artifact_hint", new JSONObject(true)
                .put("type", "string")
                .put("description", "Optional hint: auto, chart, or graph."));
        props.put("title", new JSONObject(true)
                .put("type", "string")
                .put("description", "Optional chart or graph title."));
        props.put("renderer", new JSONObject(true)
                .put("type", "string")
                .put("description", "Optional renderer override. Defaults: vega-lite for charts, cytoscape for graphs."));
        props.put("x_field", new JSONObject(true)
                .put("type", "string")
                .put("description", "Optional x axis field for chart artifacts."));
        props.put("y_field", new JSONObject(true)
                .put("type", "string")
                .put("description", "Optional y axis field for chart artifacts."));
        props.put("series_field", new JSONObject(true)
                .put("type", "string")
                .put("description", "Optional series/group field for chart color encoding."));
        props.put("source_field", new JSONObject(true)
                .put("type", "string")
                .put("description", "Optional source field for graph edge lists."));
        props.put("target_field", new JSONObject(true)
                .put("type", "string")
                .put("description", "Optional target field for graph edge lists."));
        props.put("chart_mark", new JSONObject(true)
                .put("type", "string")
                .put("description", "Optional Vega-Lite mark override: line, bar, point, area."));

        params.put("properties", props);
        params.put("required", new JSONArray().put("data"));
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    @Override
    public int maxCallsPerTurn() {
        return 3;
    }

    @Override
    public String execute(final String arguments) {
        // Parse tool-call arguments strictly once; all downstream logic works on this object.
        final JSONObject args;
        try {
            args = (arguments == null || arguments.isEmpty()) ? new JSONObject(true) : new JSONObject(arguments);
        } catch (final JSONException e) {
            return ToolHandler.errorJson("Invalid arguments JSON");
        }

        // "data" is the only hard requirement: we cannot produce a visualization envelope without it.
        final Object dataRaw = args.opt("data");
        if (dataRaw == null || dataRaw == JSONObject.NULL) return ToolHandler.errorJson("Missing data");

        // Normalize arbitrary accepted inputs (array/object/stringified JSON) into row objects.
        final JSONArray rows;
        try {
            rows = normalizeRows(dataRaw);
        } catch (final JSONException e) {
            return ToolHandler.errorJson("Failed to parse data: " + e.getMessage());
        }
        if (rows.length() == 0) return ToolHandler.errorJson("No rows available after normalization");

        // Optional hints allow caller control while preserving safe defaults.
        final String artifactHint = normalizeHint(args.optString("artifact_hint", "auto"));
        final String title = trimToNull(args.optString("title", null));
        final String rendererOverride = trimToNull(args.optString("renderer", null));

        try {
            // Artifact type choice:
            // 1) explicit hint wins
            // 2) otherwise detect graph-like edge-list structure
            // 3) fallback is chart
            if ("graph".equals(artifactHint) || isGraphLike(rows, args)) {
                return buildGraphArtifact(rows, args, title, rendererOverride).toString();
            }
            return buildChartArtifact(rows, args, title, rendererOverride).toString();
        } catch (final JSONException | IllegalArgumentException e) {
            return ToolHandler.errorJson(e.getMessage() == null ? "Failed to build artifact envelope" : e.getMessage());
        }
    }

    private static JSONObject buildChartArtifact(final JSONArray rows, final JSONObject args,
            final String title, final String rendererOverride) throws JSONException {
        // Clone rows so local transforms (such as adding synthetic index or grouping fallback)
        // do not mutate caller-provided JSON structures.
        JSONArray chartRows = cloneRows(rows);

        // Scan fields once to classify likely numeric/temporal/string roles.
        final FieldProfile profile = inferFieldProfile(chartRows);

        // User-configured fields are respected only when they actually exist in data.
        String xField = firstExistingField(args, chartRows, "x_field");
        String yField = firstExistingField(args, chartRows, "y_field");
        String seriesField = firstExistingField(args, chartRows, "series_field");

        if (xField == null || yField == null) {
            // Fill missing axes via deterministic heuristic (temporal->x, numeric->y, etc.).
            final FieldChoice inferred = chooseChartFields(chartRows, profile, xField, yField);
            if (xField == null) xField = inferred.xField;
            if (yField == null) yField = inferred.yField;
        }

        if (xField == null || yField == null) {
            // Last-resort fallback:
            // If we cannot derive meaningful x/y directly, aggregate category counts.
            // This still produces a useful bar-chart-ready artifact for categorical data.
            final String fallbackField = profile.firstStringField();
            if (fallbackField == null) throw new IllegalArgumentException("Could not infer chart axes from data");
            final JSONArray grouped = countByField(chartRows, fallbackField);
            chartRows = grouped;
            xField = "category";
            yField = "count";
            seriesField = null;
        }

        if (seriesField == null) {
            // Optional color grouping: only selected when a suitable low-cardinality string field exists.
            seriesField = inferSeriesField(chartRows, xField, yField, profile);
        }

        // Map internal field classification to Vega-Lite type system.
        final String xType = vegaTypeForField(chartRows, xField, profile);
        final String yType = vegaTypeForField(chartRows, yField, profile);

        // Select chart mark automatically unless caller overrides with a supported mark.
        final String mark = chooseMark(args.optString("chart_mark", ""), xType, yType);

        // Build Vega-Lite encoding block.
        final JSONObject encoding = new JSONObject(true);
        encoding.put("x", axis(xField, xType));
        encoding.put("y", axis(yField, yType));
        if (seriesField != null && !seriesField.equals(xField) && !seriesField.equals(yField)) {
            encoding.put("color", axis(seriesField, "nominal"));
        }

        // Build renderer payload ("spec") and wrap it in the common artifact envelope.
        final JSONObject spec = new JSONObject(true);
        spec.put("$schema", VEGA_SCHEMA);
        if (title != null) spec.put("title", title);
        spec.put("mark", mark);
        spec.put("encoding", encoding);
        spec.put("data", new JSONObject(true).put("values", chartRows));

        final JSONObject out = new JSONObject(true);
        out.put("kind", "artifact");
        out.put("artifact_type", "chart");
        out.put("renderer", rendererOverride == null ? "vega-lite" : rendererOverride);
        out.put("spec", spec);
        return out;
    }

    private static JSONObject buildGraphArtifact(final JSONArray rows, final JSONObject args,
            final String title, final String rendererOverride) throws JSONException {
        // Identify source/target edge fields from explicit arguments first, then common conventions.
        final String sourceField = chooseEdgeField(args.optString("source_field", ""), rows,
                new String[] { "source", "from", "src", "origin" });
        final String targetField = chooseEdgeField(args.optString("target_field", ""), rows,
                new String[] { "target", "to", "dst", "destination" });
        if (sourceField == null || targetField == null) {
            throw new IllegalArgumentException("Graph artifact requires source/target fields");
        }

        // Convert edge-list rows to a deduplicated node set + edge array.
        // Output format is intentionally simple for frontend adapters.
        final Set<String> seenNodes = new LinkedHashSet<>();
        final JSONArray nodes = new JSONArray();
        final JSONArray edges = new JSONArray();
        for (int i = 0; i < rows.length(); i++) {
            final JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            final String src = asNodeId(row.opt(sourceField));
            final String dst = asNodeId(row.opt(targetField));
            if (src == null || dst == null) continue;
            if (seenNodes.add(src)) nodes.put(node(src));
            if (seenNodes.add(dst)) nodes.put(node(dst));
            final JSONObject edgeData = new JSONObject(true);
            edgeData.put("id", "e" + i);
            edgeData.put("source", src);
            edgeData.put("target", dst);
            if (row.has("weight")) edgeData.put("weight", row.opt("weight"));
            edges.put(new JSONObject(true).put("data", edgeData));
        }
        if (edges.length() == 0) throw new IllegalArgumentException("No valid edges found in data");

        final JSONObject spec = new JSONObject(true);
        if (title != null) spec.put("title", title);
        spec.put("nodes", nodes);
        spec.put("edges", edges);

        // Graph envelope payload; default renderer targets common graph frontend adapters.
        final JSONObject out = new JSONObject(true);
        out.put("kind", "artifact");
        out.put("artifact_type", "graph");
        out.put("renderer", rendererOverride == null ? "cytoscape" : rendererOverride);
        out.put("spec", spec);
        return out;
    }

    private static JSONObject node(final String id) throws JSONException {
        return new JSONObject(true).put("data", new JSONObject(true).put("id", id).put("label", id));
    }

    private static JSONObject axis(final String field, final String type) throws JSONException {
        return new JSONObject(true).put("field", field).put("type", type);
    }

    private static String chooseMark(final String override, final String xType, final String yType) {
        // Respect explicit override when it is one of the marks we support safely.
        final String normalized = trimToNull(override);
        if (normalized != null) {
            final String lower = normalized.toLowerCase(Locale.ROOT);
            if ("line".equals(lower) || "bar".equals(lower) || "point".equals(lower) || "area".equals(lower)) return lower;
        }
        // Deterministic default heuristics by axis type.
        if ("temporal".equals(xType) && "quantitative".equals(yType)) return "line";
        if ("nominal".equals(xType) && "quantitative".equals(yType)) return "bar";
        if ("quantitative".equals(xType) && "quantitative".equals(yType)) return "point";
        return "bar";
    }

    private static String normalizeHint(final String hint) {
        final String value = trimToNull(hint);
        if (value == null) return "auto";
        final String lower = value.toLowerCase(Locale.ROOT);
        if ("chart".equals(lower) || "graph".equals(lower) || "auto".equals(lower)) return lower;
        return "auto";
    }

    private static JSONArray normalizeRows(final Object dataRaw) throws JSONException {
        // The tool accepts multiple shapes to be easy to call from other tools/LLM outputs:
        // - array of objects
        // - object containing values/rows arrays
        // - plain object (single row)
        // - JSON string representing any of the above
        if (dataRaw instanceof JSONArray) return toRowObjects((JSONArray) dataRaw);
        if (dataRaw instanceof JSONObject) return fromObject((JSONObject) dataRaw);
        if (dataRaw instanceof String) {
            final String text = ((String) dataRaw).trim();
            if (text.isEmpty()) return new JSONArray();
            final Object parsed = new JSONTokener(text).nextValue();
            if (parsed instanceof JSONArray) return toRowObjects((JSONArray) parsed);
            if (parsed instanceof JSONObject) return fromObject((JSONObject) parsed);
            throw new JSONException("String data is not a JSON object or array");
        }
        return new JSONArray().put(new JSONObject(true).put("value", dataRaw));
    }

    private static JSONArray fromObject(final JSONObject obj) throws JSONException {
        if (obj == null) return new JSONArray();
        final JSONArray values = obj.optJSONArray("values");
        if (values != null) return toRowObjects(values);
        final JSONArray rows = obj.optJSONArray("rows");
        if (rows != null) return toRowObjects(rows);
        return new JSONArray().put(obj);
    }

    private static JSONArray toRowObjects(final JSONArray array) throws JSONException {
        final JSONArray rows = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            final Object v = array.opt(i);
            if (v instanceof JSONObject) {
                rows.put((JSONObject) v);
            } else {
                // Scalar array elements are wrapped to preserve position and value.
                rows.put(new JSONObject(true).put("index", i).put("value", v));
            }
        }
        return rows;
    }

    private static JSONArray cloneRows(final JSONArray in) throws JSONException {
        final JSONArray out = new JSONArray();
        for (int i = 0; i < in.length(); i++) {
            final JSONObject row = in.optJSONObject(i);
            if (row == null) continue;
            out.put(new JSONObject(row.toString()));
        }
        return out;
    }

    private static boolean isGraphLike(final JSONArray rows, final JSONObject args) {
        // Explicitly configured source+target implies graph intent.
        final String configuredSource = trimToNull(args.optString("source_field", null));
        final String configuredTarget = trimToNull(args.optString("target_field", null));
        if (configuredSource != null && configuredTarget != null) return true;

        // Otherwise, treat data as graph when most rows look like edges.
        int candidates = 0;
        for (int i = 0; i < rows.length(); i++) {
            final JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            final String source = chooseEdgeField(configuredSource, row, new String[] { "source", "from", "src", "origin" });
            final String target = chooseEdgeField(configuredTarget, row, new String[] { "target", "to", "dst", "destination" });
            if (source != null && target != null) candidates++;
        }
        return candidates > 0 && candidates >= Math.max(1, rows.length() / 2);
    }

    private static String chooseEdgeField(final String preferred, final JSONArray rows, final String[] fallbacks) {
        if (preferred != null && preferred.length() > 0 && hasField(rows, preferred)) return preferred;
        for (final String fallback : fallbacks) {
            if (hasField(rows, fallback)) return fallback;
        }
        return null;
    }

    private static String chooseEdgeField(final String preferred, final JSONObject row, final String[] fallbacks) {
        if (row == null) return null;
        if (preferred != null && preferred.length() > 0 && row.has(preferred)) return preferred;
        for (final String fallback : fallbacks) {
            if (row.has(fallback)) return fallback;
        }
        return null;
    }

    private static boolean hasField(final JSONArray rows, final String field) {
        if (field == null || field.isEmpty()) return false;
        for (int i = 0; i < rows.length(); i++) {
            final JSONObject row = rows.optJSONObject(i);
            if (row != null && row.has(field)) return true;
        }
        return false;
    }

    private static String firstExistingField(final JSONObject args, final JSONArray rows, final String argumentName) {
        final String field = trimToNull(args.optString(argumentName, null));
        if (field == null) return null;
        return hasField(rows, field) ? field : null;
    }

    private static String trimToNull(final String value) {
        if (value == null) return null;
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String asNodeId(final Object value) {
        if (value == null || value == JSONObject.NULL) return null;
        final String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static FieldChoice chooseChartFields(final JSONArray rows, final FieldProfile profile,
            final String configuredX, final String configuredY) throws JSONException {
        String x = configuredX;
        String y = configuredY;

        // Preferred order:
        // x: temporal -> string -> numeric -> synthetic index
        // y: numeric
        if (x == null && !profile.temporalFields.isEmpty()) x = profile.temporalFields.get(0);
        if (y == null) {
            final String numeric = profile.firstNumericFieldExcluding(x);
            if (numeric != null) y = numeric;
        }

        if (x == null && y != null) {
            final String candidate = profile.firstStringFieldExcluding(y);
            if (candidate != null) x = candidate;
        }
        if (x == null) {
            final String candidate = profile.firstNumericFieldExcluding(y);
            if (candidate != null) x = candidate;
        }

        if (x == null && y != null) {
            // When only y can be found, add row index as synthetic x axis.
            addSequentialIndex(rows);
            x = "index";
        }
        if (y == null && x != null) {
            final String candidate = profile.firstNumericFieldExcluding(x);
            if (candidate != null) y = candidate;
        }

        return new FieldChoice(x, y);
    }

    private static void addSequentialIndex(final JSONArray rows) throws JSONException {
        for (int i = 0; i < rows.length(); i++) {
            final JSONObject row = rows.optJSONObject(i);
            if (row != null && !row.has("index")) row.put("index", i);
        }
    }

    private static String inferSeriesField(final JSONArray rows, final String xField,
            final String yField, final FieldProfile profile) {
        final List<String> strings = profile.stringFields;
        for (int i = 0; i < strings.size(); i++) {
            final String candidate = strings.get(i);
            if (candidate.equals(xField) || candidate.equals(yField)) continue;
            // Keep chart readable by selecting only low-cardinality categories as series.
            final int distinct = distinctValueCount(rows, candidate, 20);
            if (distinct >= 2 && distinct <= 8) return candidate;
        }
        return null;
    }

    private static String vegaTypeForField(final JSONArray rows, final String field, final FieldProfile profile) {
        if (field == null) return "nominal";
        if ("index".equals(field)) return "quantitative";
        if (profile.temporalFields.contains(field)) return "temporal";
        if (profile.numericFields.contains(field)) return "quantitative";
        // Recheck values for late-added synthetic fields or uncertain classifications.
        final int temporalVotes = countTemporalValues(rows, field);
        final int numericVotes = countNumericValues(rows, field);
        if (temporalVotes > 0 && temporalVotes >= numericVotes) return "temporal";
        if (numericVotes > 0) return "quantitative";
        return "nominal";
    }

    private static JSONArray countByField(final JSONArray rows, final String field) throws JSONException {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            final JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            final Object value = row.opt(field);
            final String key = value == null || value == JSONObject.NULL ? "null" : String.valueOf(value);
            final Integer current = counts.get(key);
            counts.put(key, current == null ? 1 : current.intValue() + 1);
        }
        final JSONArray out = new JSONArray();
        for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
            out.put(new JSONObject(true).put("category", entry.getKey()).put("count", entry.getValue()));
        }
        return out;
    }

    private static int distinctValueCount(final JSONArray rows, final String field, final int maxDistinct) {
        final Set<String> distinct = new LinkedHashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            final JSONObject row = rows.optJSONObject(i);
            if (row == null || !row.has(field)) continue;
            final Object v = row.opt(field);
            if (v == null || v == JSONObject.NULL) continue;
            distinct.add(String.valueOf(v));
            if (distinct.size() > maxDistinct) return distinct.size();
        }
        return distinct.size();
    }

    private static int countTemporalValues(final JSONArray rows, final String field) {
        int count = 0;
        for (int i = 0; i < rows.length(); i++) {
            final JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            final Object value = row.opt(field);
            if (looksTemporal(value)) count++;
        }
        return count;
    }

    private static int countNumericValues(final JSONArray rows, final String field) {
        int count = 0;
        for (int i = 0; i < rows.length(); i++) {
            final JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            final Object value = row.opt(field);
            if (asDouble(value) != null) count++;
        }
        return count;
    }

    private static boolean looksTemporal(final Object value) {
        if (value == null || value == JSONObject.NULL) return false;
        if (value instanceof Number) {
            // Numeric epoch-like values are treated as temporal candidates.
            final double d = ((Number) value).doubleValue();
            return d > 1_000_000_000d && d < 9_999_999_999_999d;
        }
        final String s = String.valueOf(value).trim();
        if (s.isEmpty()) return false;
        try {
            Instant.parse(s);
            return true;
        } catch (final DateTimeParseException e) {
            // next parser
        }
        try {
            LocalDate.parse(s);
            return true;
        } catch (final DateTimeParseException e) {
            return false;
        }
    }

    private static Double asDouble(final Object value) {
        if (value == null || value == JSONObject.NULL) return null;
        if (value instanceof Number) return Double.valueOf(((Number) value).doubleValue());
        final String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;
        try {
            return Double.valueOf(Double.parseDouble(text));
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static FieldProfile inferFieldProfile(final JSONArray rows) {
        final Map<String, Integer> numericVotes = new LinkedHashMap<>();
        final Map<String, Integer> temporalVotes = new LinkedHashMap<>();
        final Map<String, Integer> stringVotes = new LinkedHashMap<>();
        final Map<String, Integer> presentVotes = new LinkedHashMap<>();

        for (int i = 0; i < rows.length(); i++) {
            final JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            for (final String key : row.keySet()) {
                increment(presentVotes, key);
                final Object value = row.opt(key);
                if (value == null || value == JSONObject.NULL) continue;
                if (looksTemporal(value)) {
                    increment(temporalVotes, key);
                    continue;
                }
                if (asDouble(value) != null) {
                    increment(numericVotes, key);
                } else {
                    increment(stringVotes, key);
                }
            }
        }

        final List<String> numeric = new ArrayList<>();
        final List<String> temporal = new ArrayList<>();
        final List<String> stringy = new ArrayList<>();

        for (final Map.Entry<String, Integer> present : presentVotes.entrySet()) {
            final String field = present.getKey();
            final int total = Math.max(1, present.getValue().intValue());
            final int temporalCount = temporalVotes.containsKey(field) ? temporalVotes.get(field).intValue() : 0;
            final int numericCount = numericVotes.containsKey(field) ? numericVotes.get(field).intValue() : 0;
            final int stringCount = stringVotes.containsKey(field) ? stringVotes.get(field).intValue() : 0;
            if (temporalCount >= Math.max(1, (int) Math.ceil(total * 0.5d))) {
                temporal.add(field);
            } else if (numericCount >= Math.max(1, (int) Math.ceil(total * 0.5d))) {
                numeric.add(field);
            } else if (stringCount > 0) {
                stringy.add(field);
            }
        }
        return new FieldProfile(numeric, temporal, stringy);
    }

    private static void increment(final Map<String, Integer> map, final String key) {
        final Integer value = map.get(key);
        map.put(key, value == null ? Integer.valueOf(1) : Integer.valueOf(value.intValue() + 1));
    }

    private static final class FieldChoice {
        final String xField;
        final String yField;

        FieldChoice(final String xField, final String yField) {
            this.xField = xField;
            this.yField = yField;
        }
    }

    private static final class FieldProfile {
        final List<String> numericFields;
        final List<String> temporalFields;
        final List<String> stringFields;

        FieldProfile(final List<String> numericFields, final List<String> temporalFields, final List<String> stringFields) {
            this.numericFields = numericFields == null ? new ArrayList<String>() : numericFields;
            this.temporalFields = temporalFields == null ? new ArrayList<String>() : temporalFields;
            this.stringFields = stringFields == null ? new ArrayList<String>() : stringFields;
        }

        String firstNumericFieldExcluding(final String exclude) {
            for (int i = 0; i < this.numericFields.size(); i++) {
                final String field = this.numericFields.get(i);
                if (exclude == null || !exclude.equals(field)) return field;
            }
            return null;
        }

        String firstStringField() {
            return this.stringFields.isEmpty() ? null : this.stringFields.get(0);
        }

        String firstStringFieldExcluding(final String exclude) {
            for (int i = 0; i < this.stringFields.size(); i++) {
                final String field = this.stringFields.get(i);
                if (exclude == null || !exclude.equals(field)) return field;
            }
            return null;
        }
    }
}
