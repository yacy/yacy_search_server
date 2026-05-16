/**
 *  ArtifactPlotter
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

package net.yacy.visualization;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

/**
 * Renders artifact envelopes from ArtifactEnvelopeTool into YaCy raster images.
 * <p>
 * Supported envelope families:
 * <ul>
 *   <li>chart artifacts (vega-lite style subset: mark, encoding, data.values)</li>
 *   <li>graph artifacts (spec.nodes/spec.edges using data.id/source/target)</li>
 * </ul>
 */
public class ArtifactPlotter extends RasterPlotter {

    public static final int DEFAULT_WIDTH = 640;
    public static final int DEFAULT_HEIGHT = 480;

    private static final long COLOR_BACKGROUND = 0xFFFFFFL;
    private static final long COLOR_AXES = 0x202020L;
    private static final long COLOR_GRID = 0xE6E6E6L;
    private static final long COLOR_TEXT = 0x222222L;
    private static final long COLOR_EDGE = 0x6C7A89L;
    private static final long COLOR_NODE = 0x2878B5L;
    private static final long COLOR_NODE_TEXT = 0x111111L;

    private static final long[] PALETTE = new long[] {
            0x1F77B4L, 0xFF7F0EL, 0x2CA02CL, 0xD62728L, 0x9467BDL, 0x8C564BL,
            0xE377C2L, 0x7F7F7FL, 0xBCBD22L, 0x17BECFL
    };

    private static final int MARGIN_LEFT = 64;
    private static final int MARGIN_RIGHT = 24;
    private static final int MARGIN_TOP = 48;
    private static final int MARGIN_BOTTOM = 58;

    public ArtifactPlotter() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public ArtifactPlotter(final int width, final int height) {
        super(width, height, DrawMode.MODE_REPLACE, COLOR_BACKGROUND);
    }

    public static ArtifactPlotter fromEnvelope(final String artifactEnvelopeJson) {
        final ArtifactPlotter p = new ArtifactPlotter();
        p.paintEnvelope(artifactEnvelopeJson);
        return p;
    }

    public static ArtifactPlotter fromEnvelope(final JSONObject artifactEnvelope) {
        final ArtifactPlotter p = new ArtifactPlotter();
        p.paintEnvelope(artifactEnvelope);
        return p;
    }

    public void paintEnvelope(final String artifactEnvelopeJson) {
        if (artifactEnvelopeJson == null || artifactEnvelopeJson.trim().isEmpty()) {
            drawError("empty artifact envelope");
            return;
        }
        try {
            final Object parsed = new JSONTokener(artifactEnvelopeJson).nextValue();
            if (!(parsed instanceof JSONObject)) {
                drawError("artifact envelope must be JSON object");
                return;
            }
            paintEnvelope((JSONObject) parsed);
        } catch (final JSONException e) {
            drawError("invalid JSON envelope");
        }
    }

    public void paintEnvelope(final JSONObject artifactEnvelope) {
        clear();
        if (artifactEnvelope == null) {
            drawError("null artifact envelope");
            return;
        }
        final String kind = artifactEnvelope.optString("kind", "");
        if (!"artifact".equalsIgnoreCase(kind)) {
            drawError("unsupported kind: " + kind);
            return;
        }
        final String artifactType = artifactEnvelope.optString("artifact_type", "").toLowerCase(Locale.ROOT);
        final JSONObject spec = artifactEnvelope.optJSONObject("spec");
        if (spec == null) {
            drawError("missing spec");
            return;
        }

        if ("chart".equals(artifactType)) {
            paintChart(artifactEnvelope, spec);
            return;
        }
        if ("graph".equals(artifactType)) {
            paintGraph(artifactEnvelope, spec);
            return;
        }
        drawError("unsupported artifact_type: " + artifactType);
    }

    private void paintChart(final JSONObject artifact, final JSONObject spec) {
        final JSONObject data = spec.optJSONObject("data");
        JSONArray values = data == null ? null : data.optJSONArray("values");
        if (values == null) values = spec.optJSONArray("values");
        if (values == null || values.length() == 0) {
            drawError("chart has no data values");
            return;
        }

        final JSONObject encoding = spec.optJSONObject("encoding");
        final JSONObject xEnc = encoding == null ? null : encoding.optJSONObject("x");
        final JSONObject yEnc = encoding == null ? null : encoding.optJSONObject("y");
        final JSONObject cEnc = encoding == null ? null : encoding.optJSONObject("color");

        final String xField = xEnc == null ? null : trimToNull(xEnc.optString("field", null));
        final String yField = yEnc == null ? null : trimToNull(yEnc.optString("field", null));
        final String xType = normalizeType(xEnc == null ? null : xEnc.optString("type", "nominal"));
        final String yType = normalizeType(yEnc == null ? null : yEnc.optString("type", "quantitative"));
        final String colorField = cEnc == null ? null : trimToNull(cEnc.optString("field", null));
        final String mark = spec.optString("mark", "line").toLowerCase(Locale.ROOT);

        if (xField == null || yField == null) {
            drawError("chart encoding requires x/y fields");
            return;
        }

        final List<ChartPoint> points = new ArrayList<ChartPoint>();
        final Map<String, Integer> xCategories = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> yCategories = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < values.length(); i++) {
            final JSONObject row = values.optJSONObject(i);
            if (row == null) continue;
            final ChartPoint p = pointFromRow(row, xField, xType, yField, yType, colorField, xCategories, yCategories);
            if (p != null) points.add(p);
        }
        if (points.isEmpty()) {
            drawError("chart data rows are not plottable");
            return;
        }

        final int left = MARGIN_LEFT;
        final int right = getWidth() - MARGIN_RIGHT;
        final int top = MARGIN_TOP;
        final int bottom = getHeight() - MARGIN_BOTTOM;
        final int plotW = Math.max(1, right - left);
        final int plotH = Math.max(1, bottom - top);

        final double minX = minX(points);
        final double maxX = maxX(points);
        final double minY = minY(points, "bar".equals(mark) || "area".equals(mark));
        final double maxY = maxY(points);
        final double safeMaxY = (maxY <= minY) ? minY + 1.0d : maxY;
        final double safeMaxX = (maxX <= minX) ? minX + 1.0d : maxX;
        final int baselineY = mapY(0.0d < minY ? minY : 0.0d, minY, safeMaxY, top, bottom, plotH);

        drawChartScaffold(artifact, spec, xField, yField, left, right, top, bottom, minY, safeMaxY);

        final Map<String, Long> seriesColors = assignSeriesColors(points);
        if ("line".equals(mark) || "area".equals(mark)) {
            final Map<String, List<ChartPoint>> groups = groupBySeries(points);
            for (final Map.Entry<String, List<ChartPoint>> entry : groups.entrySet()) {
                final List<ChartPoint> series = entry.getValue();
                Collections.sort(series, new Comparator<ChartPoint>() {
                    @Override
                    public int compare(final ChartPoint a, final ChartPoint b) {
                        return Double.compare(a.x, b.x);
                    }
                });
                final long color = seriesColors.get(entry.getKey()).longValue();
                for (int i = 1; i < series.size(); i++) {
                    final ChartPoint p0 = series.get(i - 1);
                    final ChartPoint p1 = series.get(i);
                    final int x0 = mapX(p0.x, minX, safeMaxX, left, plotW);
                    final int y0 = mapY(p0.y, minY, safeMaxY, top, bottom, plotH);
                    final int x1 = mapX(p1.x, minX, safeMaxX, left, plotW);
                    final int y1 = mapY(p1.y, minY, safeMaxY, top, bottom, plotH);
                    setColor(color);
                    line(x0, y0, x1, y1, 100);
                    if ("area".equals(mark)) {
                        line(x1, y1, x1, baselineY, 35);
                    }
                }
                for (int i = 0; i < series.size(); i++) {
                    final ChartPoint p = series.get(i);
                    final int x = mapX(p.x, minX, safeMaxX, left, plotW);
                    final int y = mapY(p.y, minY, safeMaxY, top, bottom, plotH);
                    setColor(color);
                    dot(x, y, 2, true, 100);
                }
            }
        } else if ("point".equals(mark)) {
            for (int i = 0; i < points.size(); i++) {
                final ChartPoint p = points.get(i);
                final int x = mapX(p.x, minX, safeMaxX, left, plotW);
                final int y = mapY(p.y, minY, safeMaxY, top, bottom, plotH);
                final long color = seriesColors.get(p.series).longValue();
                setColor(color);
                dot(x, y, 3, true, 100);
            }
        } else { // bar as default fallback for unknown marks
            final int count = Math.max(1, points.size());
            final int barWidth = Math.max(1, Math.min(22, (int) (plotW / (double) Math.max(2, count))));
            for (int i = 0; i < points.size(); i++) {
                final ChartPoint p = points.get(i);
                final int x = mapX(p.x, minX, safeMaxX, left, plotW);
                final int y = mapY(p.y, minY, safeMaxY, top, bottom, plotH);
                final long color = seriesColors.get(p.series).longValue();
                setColor(color);
                for (int dx = -barWidth / 2; dx <= barWidth / 2; dx++) {
                    line(x + dx, baselineY, x + dx, y, 100);
                }
            }
        }

        drawSeriesLegend(points, seriesColors, right, top + 6);
        drawNominalTicks(xType, xCategories, left, top, bottom, plotW);
    }

    private void paintGraph(final JSONObject artifact, final JSONObject spec) {
        final JSONArray nodes = spec.optJSONArray("nodes");
        final JSONArray edges = spec.optJSONArray("edges");
        if (nodes == null || edges == null) {
            drawError("graph requires nodes and edges");
            return;
        }

        final Set<String> nodeIds = new LinkedHashSet<String>();
        for (int i = 0; i < nodes.length(); i++) {
            final JSONObject node = nodes.optJSONObject(i);
            if (node == null) continue;
            final JSONObject data = node.optJSONObject("data");
            final String id = data == null ? null : trimToNull(data.optString("id", null));
            if (id != null) nodeIds.add(id);
        }
        for (int i = 0; i < edges.length(); i++) {
            final JSONObject edge = edges.optJSONObject(i);
            if (edge == null) continue;
            final JSONObject data = edge.optJSONObject("data");
            if (data == null) continue;
            final String source = trimToNull(data.optString("source", null));
            final String target = trimToNull(data.optString("target", null));
            if (source != null) nodeIds.add(source);
            if (target != null) nodeIds.add(target);
        }
        if (nodeIds.isEmpty()) {
            drawError("graph has no nodes");
            return;
        }

        final int left = 30;
        final int right = getWidth() - 30;
        final int top = 40;
        final int bottom = getHeight() - 24;
        final int cx = (left + right) / 2;
        final int cy = (top + bottom) / 2;
        final int radius = Math.max(20, Math.min(right - left, bottom - top) / 2 - 26);

        final List<String> ordered = new ArrayList<String>(nodeIds);
        Collections.sort(ordered);
        final Map<String, NodePos> positions = new LinkedHashMap<String, NodePos>();
        for (int i = 0; i < ordered.size(); i++) {
            final String id = ordered.get(i);
            final double angle = (Math.PI * 2.0d * i) / Math.max(1, ordered.size());
            final int x = cx + (int) Math.round(Math.cos(angle) * radius);
            final int y = cy + (int) Math.round(Math.sin(angle) * radius);
            positions.put(id, new NodePos(x, y));
        }

        drawGraphHeader(artifact, spec);
        setColor(COLOR_EDGE);
        for (int i = 0; i < edges.length(); i++) {
            final JSONObject edge = edges.optJSONObject(i);
            if (edge == null) continue;
            final JSONObject data = edge.optJSONObject("data");
            if (data == null) continue;
            final String source = trimToNull(data.optString("source", null));
            final String target = trimToNull(data.optString("target", null));
            if (source == null || target == null) continue;
            final NodePos from = positions.get(source);
            final NodePos to = positions.get(target);
            if (from == null || to == null) continue;
            lineArrow(from.x, from.y, to.x, to.y, 6, 8, COLOR_EDGE, COLOR_EDGE);
        }

        for (int i = 0; i < ordered.size(); i++) {
            final String id = ordered.get(i);
            final NodePos p = positions.get(id);
            setColor(COLOR_NODE);
            dot(p.x, p.y, 7, true, 100);
            setColor(COLOR_NODE_TEXT);
            PrintTool.print6(this, p.x, p.y + 16, 0, truncate(id, 14), 0, 90, false, false);
        }
    }

    private void drawChartScaffold(final JSONObject artifact, final JSONObject spec, final String xField, final String yField,
            final int left, final int right, final int top, final int bottom, final double minY, final double maxY) {
        final String title = trimToNull(spec.optString("title", null));
        setColor(COLOR_TEXT);
        PrintTool.print6(this, getWidth() / 2, 14, 0, title == null ? "ARTIFACT CHART" : title.toUpperCase(Locale.ROOT), 0, 90, true, false);

        setColor(COLOR_GRID);
        for (int i = 1; i < 6; i++) {
            final int y = top + ((bottom - top) * i / 6);
            line(left, y, right, y, 100);
        }

        setColor(COLOR_AXES);
        line(left, top, left, bottom, 100);
        line(left, bottom, right, bottom, 100);

        setColor(COLOR_TEXT);
        PrintTool.print6(this, left - 8, top - 2, 90, yField.toUpperCase(Locale.ROOT), 1, 80, false, false);
        PrintTool.print6(this, (left + right) / 2, getHeight() - 12, 0, xField.toUpperCase(Locale.ROOT), 0, 80, false, false);
        PrintTool.print6(this, left - 4, bottom + 12, 0, formatTick(minY), 1, 70, false, false);
        PrintTool.print6(this, left - 4, top + 6, 0, formatTick(maxY), 1, 70, false, false);
    }

    private void drawGraphHeader(final JSONObject artifact, final JSONObject spec) {
        final String title = trimToNull(spec.optString("title", null));
        setColor(COLOR_TEXT);
        PrintTool.print6(this, getWidth() / 2, 14, 0, title == null ? "ARTIFACT GRAPH" : title.toUpperCase(Locale.ROOT), 0, 90, true, false);
    }

    private void drawSeriesLegend(final List<ChartPoint> points, final Map<String, Long> seriesColors, final int right, final int startY) {
        if (seriesColors.size() <= 1) return;
        int y = startY;
        final List<String> keys = new ArrayList<String>(seriesColors.keySet());
        for (int i = 0; i < keys.size() && i < 8; i++) {
            final String key = keys.get(i);
            final long c = seriesColors.get(key).longValue();
            setColor(c);
            dot(right - 6, y, 3, true, 100);
            setColor(COLOR_TEXT);
            PrintTool.print6(this, right - 12, y + 2, 0, truncate(key.toUpperCase(Locale.ROOT), 16), 1, 70, false, false);
            y += 12;
        }
    }

    private void drawNominalTicks(final String xType, final Map<String, Integer> xCategories,
            final int left, final int top, final int bottom, final int plotW) {
        if (!"nominal".equals(xType) && !"ordinal".equals(xType)) return;
        if (xCategories.isEmpty()) return;
        final int n = xCategories.size();
        final int maxLabels = 8;
        int i = 0;
        for (final Map.Entry<String, Integer> entry : xCategories.entrySet()) {
            if (n > maxLabels && i % Math.max(1, n / maxLabels) != 0) {
                i++;
                continue;
            }
            final double xValue = entry.getValue().doubleValue();
            final int x = left + (int) Math.round((xValue / Math.max(1.0d, n - 1.0d)) * plotW);
            setColor(COLOR_AXES);
            line(x, bottom, x, bottom + 4, 100);
            setColor(COLOR_TEXT);
            PrintTool.print6(this, x, bottom + 14, 0, truncate(entry.getKey().toUpperCase(Locale.ROOT), 8), 0, 60, false, false);
            i++;
        }
    }

    private ChartPoint pointFromRow(final JSONObject row, final String xField, final String xType, final String yField, final String yType,
            final String colorField, final Map<String, Integer> xCategories, final Map<String, Integer> yCategories) {
        if (row == null) return null;
        final Object xRaw = row.opt(xField);
        final Object yRaw = row.opt(yField);
        if (xRaw == null || xRaw == JSONObject.NULL || yRaw == null || yRaw == JSONObject.NULL) return null;

        final Double xValue = valueForType(xRaw, xType, xCategories);
        final Double yValue = valueForType(yRaw, yType, yCategories);
        if (xValue == null || yValue == null) return null;

        final String series;
        if (colorField != null && row.has(colorField) && row.opt(colorField) != JSONObject.NULL) {
            series = String.valueOf(row.opt(colorField));
        } else {
            series = "_default";
        }
        return new ChartPoint(xValue.doubleValue(), yValue.doubleValue(), series);
    }

    private static Double valueForType(final Object raw, final String type, final Map<String, Integer> categories) {
        if ("temporal".equals(type)) return parseTemporal(raw);
        if ("quantitative".equals(type)) return parseDouble(raw);
        if ("nominal".equals(type) || "ordinal".equals(type)) {
            final String key = String.valueOf(raw);
            Integer idx = categories.get(key);
            if (idx == null) {
                idx = Integer.valueOf(categories.size());
                categories.put(key, idx);
            }
            return Double.valueOf(idx.doubleValue());
        }
        final Double d = parseDouble(raw);
        if (d != null) return d;
        return parseTemporal(raw);
    }

    private static Double parseTemporal(final Object raw) {
        if (raw == null || raw == JSONObject.NULL) return null;
        if (raw instanceof Number) return Double.valueOf(((Number) raw).doubleValue());
        final String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return null;
        try {
            return Double.valueOf(Instant.parse(s).toEpochMilli());
        } catch (final DateTimeParseException e) {
            // continue
        }
        try {
            return Double.valueOf(LocalDate.parse(s).toEpochDay());
        } catch (final DateTimeParseException e) {
            return parseDouble(s);
        }
    }

    private static Double parseDouble(final Object raw) {
        if (raw == null || raw == JSONObject.NULL) return null;
        if (raw instanceof Number) return Double.valueOf(((Number) raw).doubleValue());
        final String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return null;
        try {
            return Double.valueOf(Double.parseDouble(s));
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static String normalizeType(final String type) {
        final String t = trimToNull(type);
        if (t == null) return "nominal";
        final String lower = t.toLowerCase(Locale.ROOT);
        if ("temporal".equals(lower) || "quantitative".equals(lower) || "nominal".equals(lower) || "ordinal".equals(lower)) return lower;
        return "nominal";
    }

    private static String trimToNull(final String s) {
        if (s == null) return null;
        final String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static int mapX(final double value, final double minX, final double maxX, final int left, final int plotW) {
        final double ratio = (value - minX) / Math.max(1e-12d, maxX - minX);
        return left + (int) Math.round(ratio * plotW);
    }

    private static int mapY(final double value, final double minY, final double maxY, final int top, final int bottom, final int plotH) {
        final double ratio = (value - minY) / Math.max(1e-12d, maxY - minY);
        return bottom - (int) Math.round(ratio * plotH);
    }

    private static double minX(final List<ChartPoint> points) {
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.size(); i++) min = Math.min(min, points.get(i).x);
        return min;
    }

    private static double maxX(final List<ChartPoint> points) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < points.size(); i++) max = Math.max(max, points.get(i).x);
        return max;
    }

    private static double minY(final List<ChartPoint> points, final boolean includeZeroBaseline) {
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.size(); i++) min = Math.min(min, points.get(i).y);
        if (includeZeroBaseline) min = Math.min(0.0d, min);
        return min;
    }

    private static double maxY(final List<ChartPoint> points) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < points.size(); i++) max = Math.max(max, points.get(i).y);
        return max;
    }

    private static Map<String, List<ChartPoint>> groupBySeries(final List<ChartPoint> points) {
        final Map<String, List<ChartPoint>> grouped = new LinkedHashMap<String, List<ChartPoint>>();
        for (int i = 0; i < points.size(); i++) {
            final ChartPoint p = points.get(i);
            List<ChartPoint> list = grouped.get(p.series);
            if (list == null) {
                list = new ArrayList<ChartPoint>();
                grouped.put(p.series, list);
            }
            list.add(p);
        }
        return grouped;
    }

    private static Map<String, Long> assignSeriesColors(final List<ChartPoint> points) {
        final Map<String, Long> colors = new LinkedHashMap<String, Long>();
        int i = 0;
        for (int p = 0; p < points.size(); p++) {
            final String key = points.get(p).series;
            if (!colors.containsKey(key)) {
                colors.put(key, Long.valueOf(PALETTE[i % PALETTE.length]));
                i++;
            }
        }
        if (colors.isEmpty()) colors.put("_default", Long.valueOf(PALETTE[0]));
        return colors;
    }

    private void drawError(final String message) {
        clear();
        setColor(0xBB2222L);
        PrintTool.print6(this, getWidth() / 2, getHeight() / 2 - 6, 0, "ARTIFACT RENDER ERROR", 0, 100, true, false);
        setColor(0x222222L);
        PrintTool.print6(this, getWidth() / 2, getHeight() / 2 + 10, 0, truncate(message == null ? "unknown error" : message, 50).toUpperCase(Locale.ROOT), 0, 80, false, false);
    }

    private static String truncate(final String s, final int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        if (maxLen <= 3) return s.substring(0, Math.max(0, maxLen));
        return s.substring(0, maxLen - 3) + "...";
    }

    private static String formatTick(final double v) {
        if (Math.abs(v) >= 1000.0d || Math.abs(v) < 0.01d) return String.format(Locale.ROOT, "%.2e", v);
        if (Math.abs(v - Math.rint(v)) < 1e-9d) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static final class ChartPoint {
        final double x;
        final double y;
        final String series;

        ChartPoint(final double x, final double y, final String series) {
            this.x = x;
            this.y = y;
            this.series = series == null ? "_default" : series;
        }
    }

    private static final class NodePos {
        final int x;
        final int y;

        NodePos(final int x, final int y) {
            this.x = x;
            this.y = y;
        }
    }
}
