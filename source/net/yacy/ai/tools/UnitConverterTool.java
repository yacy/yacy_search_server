/**
 *  UnitConverterTool
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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.ToolHandler;

public class UnitConverterTool implements ToolHandler {

    private static final String NAME = "unit_converter";

    private static final Map<String, Double> LENGTH_TO_M = new HashMap<>();
    private static final Map<String, Double> MASS_TO_KG = new HashMap<>();
    private static final Map<String, Double> VOLUME_TO_L = new HashMap<>();
    private static final Map<String, Double> SPEED_TO_MPS = new HashMap<>();

    static {
        LENGTH_TO_M.put("mm", 0.001d);
        LENGTH_TO_M.put("cm", 0.01d);
        LENGTH_TO_M.put("m", 1d);
        LENGTH_TO_M.put("km", 1000d);
        LENGTH_TO_M.put("in", 0.0254d);
        LENGTH_TO_M.put("ft", 0.3048d);
        LENGTH_TO_M.put("yd", 0.9144d);
        LENGTH_TO_M.put("mi", 1609.344d);

        MASS_TO_KG.put("mg", 0.000001d);
        MASS_TO_KG.put("g", 0.001d);
        MASS_TO_KG.put("kg", 1d);
        MASS_TO_KG.put("t", 1000d);
        MASS_TO_KG.put("oz", 0.028349523125d);
        MASS_TO_KG.put("lb", 0.45359237d);

        VOLUME_TO_L.put("ml", 0.001d);
        VOLUME_TO_L.put("l", 1d);
        VOLUME_TO_L.put("m3", 1000d);
        VOLUME_TO_L.put("tsp", 0.00492892159375d);
        VOLUME_TO_L.put("tbsp", 0.01478676478125d);
        VOLUME_TO_L.put("cup", 0.2365882365d);
        VOLUME_TO_L.put("pt", 0.473176473d);
        VOLUME_TO_L.put("qt", 0.946352946d);
        VOLUME_TO_L.put("gal", 3.785411784d);

        SPEED_TO_MPS.put("m/s", 1d);
        SPEED_TO_MPS.put("km/h", 0.2777777777777778d);
        SPEED_TO_MPS.put("mph", 0.44704d);
        SPEED_TO_MPS.put("kn", 0.514444d);
    }

    @Override
    public JSONObject definition() throws JSONException {
        JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "Convert numeric values between common units (length, mass, temperature, volume, speed).");
        JSONObject params = new JSONObject(true);
        params.put("type", "object");
        JSONObject props = new JSONObject(true);
        props.put("value", new JSONObject(true).put("type", "number").put("description", "Numeric input value."));
        props.put("from", new JSONObject(true).put("type", "string").put("description", "Source unit, e.g. km, lb, C."));
        props.put("to", new JSONObject(true).put("type", "string").put("description", "Target unit, e.g. mi, kg, F."));
        params.put("properties", props);
        params.put("required", new JSONArray().put("value").put("from").put("to"));
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    
    public int maxCallsPerTurn() {
        return 10;
    }

    
    public String execute(String arguments) {
        final JSONObject args;
        try {
            args = (arguments == null || arguments.isEmpty()) ? new JSONObject(true) : new JSONObject(arguments);
        } catch (JSONException e) {
            return ToolHandler.errorJson("Invalid arguments JSON");
        }
        if (!args.has("value")) return ToolHandler.errorJson("Missing value");
        double value = args.optDouble("value", Double.NaN);
        if (Double.isNaN(value) || Double.isInfinite(value)) return ToolHandler.errorJson("Invalid value");
        String from = normalize(args.optString("from", ""));
        String to = normalize(args.optString("to", ""));
        if (from.isEmpty() || to.isEmpty()) return ToolHandler.errorJson("Missing from/to");

        try {
            ConversionResult result = convert(value, from, to);
            JSONObject out = new JSONObject(true);
            out.put("tool", NAME);
            out.put("category", result.category);
            out.put("value", value);
            out.put("from", from);
            out.put("to", to);
            out.put("result", result.value);
            return out.toString();
        } catch (IllegalArgumentException | JSONException e) {
            return ToolHandler.errorJson(e.getMessage());
        }
    }

    private static ConversionResult convert(double value, String from, String to) {
        if (isTemp(from) && isTemp(to)) {
            double c = toCelsius(value, from);
            return new ConversionResult("temperature", fromCelsius(c, to));
        }
        if (LENGTH_TO_M.containsKey(from) && LENGTH_TO_M.containsKey(to)) {
            double m = value * LENGTH_TO_M.get(from);
            return new ConversionResult("length", m / LENGTH_TO_M.get(to));
        }
        if (MASS_TO_KG.containsKey(from) && MASS_TO_KG.containsKey(to)) {
            double kg = value * MASS_TO_KG.get(from);
            return new ConversionResult("mass", kg / MASS_TO_KG.get(to));
        }
        if (VOLUME_TO_L.containsKey(from) && VOLUME_TO_L.containsKey(to)) {
            double l = value * VOLUME_TO_L.get(from);
            return new ConversionResult("volume", l / VOLUME_TO_L.get(to));
        }
        if (SPEED_TO_MPS.containsKey(from) && SPEED_TO_MPS.containsKey(to)) {
            double mps = value * SPEED_TO_MPS.get(from);
            return new ConversionResult("speed", mps / SPEED_TO_MPS.get(to));
        }
        throw new IllegalArgumentException("Unsupported conversion pair: " + from + " -> " + to);
    }

    private static boolean isTemp(String unit) {
        return "c".equals(unit) || "f".equals(unit) || "k".equals(unit);
    }

    private static double toCelsius(double v, String from) {
        if ("c".equals(from)) return v;
        if ("f".equals(from)) return (v - 32d) * 5d / 9d;
        if ("k".equals(from)) return v - 273.15d;
        throw new IllegalArgumentException("Unsupported temperature unit: " + from);
    }

    private static double fromCelsius(double c, String to) {
        if ("c".equals(to)) return c;
        if ("f".equals(to)) return (c * 9d / 5d) + 32d;
        if ("k".equals(to)) return c + 273.15d;
        throw new IllegalArgumentException("Unsupported temperature unit: " + to);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(Locale.ROOT);
        if ("°c".equals(t) || "celsius".equals(t)) return "c";
        if ("°f".equals(t) || "fahrenheit".equals(t)) return "f";
        if ("kelvin".equals(t)) return "k";
        if ("meter".equals(t) || "metre".equals(t)) return "m";
        if ("kilometer".equals(t) || "kilometre".equals(t)) return "km";
        if ("mile".equals(t)) return "mi";
        if ("foot".equals(t)) return "ft";
        if ("inch".equals(t)) return "in";
        if ("pound".equals(t)) return "lb";
        if ("ounce".equals(t)) return "oz";
        if ("liter".equals(t) || "litre".equals(t)) return "l";
        if ("gallon".equals(t)) return "gal";
        if ("kph".equals(t)) return "km/h";
        if ("fps".equals(t)) return "ft/s";
        return t;
    }

    private static final class ConversionResult {
        final String category;
        final double value;
        ConversionResult(String category, double value) {
            this.category = category;
            this.value = value;
        }
    }
}
