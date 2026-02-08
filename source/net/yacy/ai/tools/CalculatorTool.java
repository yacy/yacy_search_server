/**
 *  CalculatorTool
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

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.ToolHandler;

public class CalculatorTool implements ToolHandler {

    private static final String NAME = "calculator";

    @Override
    public JSONObject definition() throws JSONException {
        JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "Evaluate a mathematical formula. Supports operators (+, -, *, /, %, ^), constants (pi, e, tau, phi), functions (sqrt, abs, ln, log, sin, cos, tan, asin, acos, atan, min, max, pow, root, ...) and scientific notation. Use this as your calculator.");
        JSONObject params = new JSONObject(true);
        params.put("type", "object");
        JSONObject props = new JSONObject(true);
        JSONObject formula = new JSONObject(true);
        formula.put("type", "string");
        formula.put("description", "Formula to evaluate, e.g. (2+3)*4/5");
        props.put("formula", formula);
        params.put("properties", props);
        params.put("required", new JSONArray().put("formula"));
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    
    public int maxCallsPerTurn() {
        return 10;
    }

    
    public String execute(String arguments) {
        String formula;
        try {
            JSONObject obj = (arguments == null || arguments.isEmpty()) ? new JSONObject(true) : new JSONObject(arguments);
            formula = obj.optString("formula", "").trim();
        } catch (JSONException e) {
            return ToolHandler.errorJson("Invalid arguments JSON");
        }
        if (formula == null || formula.isEmpty()) return ToolHandler.errorJson("Missing formula");
        try {
            BigDecimal value = new ExpressionParser(formula).parse();
            JSONObject result = new JSONObject(true);
            result.put("formula", formula);
            result.put("result", value.stripTrailingZeros().toPlainString());
            return result.toString();
        } catch (Exception e) {
            return ToolHandler.errorJson("Invalid formula: " + e.getMessage());
        }
    }

    private static final class ExpressionParser {
        private final String expression;
        private int pos;

        private ExpressionParser(String expression) {
            this.expression = expression == null ? "" : expression;
        }

        private BigDecimal parse() {
            BigDecimal value = parseExpression();
            skipWs();
            if (pos != expression.length()) throw new IllegalArgumentException("Unexpected token at " + pos);
            return value;
        }

        private BigDecimal parseExpression() {
            BigDecimal value = parseTerm();
            while (true) {
                skipWs();
                if (eat('+')) value = value.add(parseTerm());
                else if (eat('-')) value = value.subtract(parseTerm());
                else return value;
            }
        }

        private BigDecimal parseTerm() {
            BigDecimal value = parseFactor();
            while (true) {
                skipWs();
                if (eat('*')) value = value.multiply(parseFactor(), MathContext.DECIMAL64);
                else if (eat('/')) value = value.divide(parseFactor(), MathContext.DECIMAL64);
                else if (eat('%')) value = value.remainder(parseFactor(), MathContext.DECIMAL64);
                else return value;
            }
        }

        private BigDecimal parseFactor() {
            skipWs();
            if (eat('+')) return parseFactor();
            if (eat('-')) return parseFactor().negate();
            BigDecimal base;
            if (eat('(')) {
                base = parseExpression();
                if (!eat(')')) throw new IllegalArgumentException("Missing ')'");
            } else if (isIdentifierStart(currentChar())) {
                String ident = parseIdentifier();
                if (eat('(')) {
                    List<BigDecimal> args = parseArguments();
                    if (!eat(')')) throw new IllegalArgumentException("Missing ')' after function argument");
                    base = applyFunction(ident, args);
                } else {
                    base = resolveConstant(ident);
                }
            } else {
                base = parseNumber();
            }
            skipWs();
            if (eat('^')) {
                BigDecimal exp = parseFactor();
                try {
                    int intExp = exp.intValueExact();
                    return base.pow(intExp, MathContext.DECIMAL64);
                } catch (ArithmeticException e) {
                    double b = base.doubleValue();
                    double ex = exp.doubleValue();
                    // Fractional powers are evaluated in double precision.
                    double result = Math.pow(b, ex);
                    if (Double.isNaN(result) || Double.isInfinite(result)) {
                        throw new IllegalArgumentException("Invalid exponentiation result");
                    }
                    return new BigDecimal(result, MathContext.DECIMAL64);
                }
            }
            return base;
        }

        private BigDecimal parseNumber() {
            skipWs();
            int start = pos;
            boolean dot = false;
            boolean exponent = false;
            while (pos < expression.length()) {
                char c = expression.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                    continue;
                }
                if (c == '.' && !dot) {
                    dot = true;
                    pos++;
                    continue;
                }
                if ((c == 'e' || c == 'E') && !exponent) {
                    exponent = true;
                    pos++;
                    if (pos < expression.length()) {
                        char sign = expression.charAt(pos);
                        if (sign == '+' || sign == '-') pos++;
                    }
                    continue;
                }
                break;
            }
            if (start == pos) throw new IllegalArgumentException("Number expected at " + pos);
            return new BigDecimal(expression.substring(start, pos), MathContext.DECIMAL64);
        }

        private String parseIdentifier() {
            skipWs();
            int start = pos;
            if (!isIdentifierStart(currentChar())) throw new IllegalArgumentException("Identifier expected at " + pos);
            pos++;
            while (pos < expression.length()) {
                char c = expression.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                    pos++;
                    continue;
                }
                break;
            }
            return expression.substring(start, pos).toLowerCase();
        }

        private List<BigDecimal> parseArguments() {
            List<BigDecimal> args = new ArrayList<>();
            skipWs();
            if (currentChar() == ')') return args;
            args.add(parseExpression());
            while (true) {
                skipWs();
                if (!eat(',')) break;
                args.add(parseExpression());
            }
            return args;
        }

        private BigDecimal applyFunction(String name, List<BigDecimal> args) {
            String fn = shortName(name);
            if ("sqrt".equals(fn)) return bd(Math.sqrt(nonNegative(arg(args, 0), "sqrt")));
            if ("cbrt".equals(fn)) return bd(Math.cbrt(arg(args, 0).doubleValue()));
            if ("abs".equals(fn)) return arg(args, 0).abs();
            if ("floor".equals(fn)) return bd(Math.floor(arg(args, 0).doubleValue()));
            if ("ceil".equals(fn)) return bd(Math.ceil(arg(args, 0).doubleValue()));
            if ("round".equals(fn)) return bd(Math.rint(arg(args, 0).doubleValue()));
            if ("trunc".equals(fn)) return bd(arg(args, 0).doubleValue() < 0d ? Math.ceil(arg(args, 0).doubleValue()) : Math.floor(arg(args, 0).doubleValue()));

            if ("exp".equals(fn)) return bd(Math.exp(arg(args, 0).doubleValue()));
            if ("ln".equals(fn)) return bd(Math.log(positive(arg(args, 0), "ln")));
            if ("log".equals(fn)) {
                if (args.size() == 1) return bd(Math.log10(positive(arg(args, 0), "log")));
                double value = positive(arg(args, 0), "log");
                double base = positive(arg(args, 1), "log base");
                if (base == 1d) throw new IllegalArgumentException("log base must not be 1");
                return bd(Math.log(value) / Math.log(base));
            }
            if ("log10".equals(fn)) return bd(Math.log10(positive(arg(args, 0), "log10")));
            if ("log2".equals(fn)) return bd(Math.log(positive(arg(args, 0), "log2")) / Math.log(2d));

            if ("sin".equals(fn)) return bd(Math.sin(arg(args, 0).doubleValue()));
            if ("cos".equals(fn)) return bd(Math.cos(arg(args, 0).doubleValue()));
            if ("tan".equals(fn)) return bd(Math.tan(arg(args, 0).doubleValue()));
            if ("asin".equals(fn)) return bd(Math.asin(arg(args, 0).doubleValue()));
            if ("acos".equals(fn)) return bd(Math.acos(arg(args, 0).doubleValue()));
            if ("atan".equals(fn)) return bd(Math.atan(arg(args, 0).doubleValue()));
            if ("atan2".equals(fn)) return bd(Math.atan2(arg(args, 0).doubleValue(), arg(args, 1).doubleValue()));
            if ("sinh".equals(fn)) return bd(Math.sinh(arg(args, 0).doubleValue()));
            if ("cosh".equals(fn)) return bd(Math.cosh(arg(args, 0).doubleValue()));
            if ("tanh".equals(fn)) return bd(Math.tanh(arg(args, 0).doubleValue()));

            if ("deg".equals(fn) || "degrees".equals(fn) || "todeg".equals(fn)) return bd(Math.toDegrees(arg(args, 0).doubleValue()));
            if ("rad".equals(fn) || "radians".equals(fn) || "torad".equals(fn)) return bd(Math.toRadians(arg(args, 0).doubleValue()));

            if ("pow".equals(fn)) return bd(Math.pow(arg(args, 0).doubleValue(), arg(args, 1).doubleValue()));
            if ("root".equals(fn)) {
                double n = arg(args, 1).doubleValue();
                if (n == 0d) throw new IllegalArgumentException("root degree must not be 0");
                return bd(Math.pow(arg(args, 0).doubleValue(), 1d / n));
            }
            if ("mod".equals(fn)) return arg(args, 0).remainder(arg(args, 1), MathContext.DECIMAL64);

            if ("min".equals(fn)) return min(args);
            if ("max".equals(fn)) return max(args);
            if ("avg".equals(fn) || "mean".equals(fn)) return mean(args);
            if ("sum".equals(fn)) return sum(args);
            throw new IllegalArgumentException("Unknown function: " + name);
        }

        private BigDecimal resolveConstant(String name) {
            String n = shortName(name);
            if ("pi".equals(n)) return new BigDecimal(Math.PI, MathContext.DECIMAL64);
            if ("e".equals(n)) return new BigDecimal(Math.E, MathContext.DECIMAL64);
            if ("tau".equals(n)) return new BigDecimal(2d * Math.PI, MathContext.DECIMAL64);
            if ("phi".equals(n)) return new BigDecimal((1d + Math.sqrt(5d)) / 2d, MathContext.DECIMAL64);
            throw new IllegalArgumentException("Unknown identifier: " + name);
        }

        private String shortName(String name) {
            if (name == null || name.isEmpty()) return "";
            int p = name.lastIndexOf('.');
            if (p < 0 || p >= name.length() - 1) return name;
            return name.substring(p + 1);
        }

        private BigDecimal arg(List<BigDecimal> args, int index) {
            if (args == null || index < 0 || index >= args.size()) throw new IllegalArgumentException("Missing function argument at position " + (index + 1));
            return args.get(index);
        }

        private double positive(BigDecimal value, String label) {
            double v = value.doubleValue();
            if (v <= 0d) throw new IllegalArgumentException(label + " argument must be > 0");
            return v;
        }

        private double nonNegative(BigDecimal value, String label) {
            double v = value.doubleValue();
            if (v < 0d) throw new IllegalArgumentException(label + " argument must be >= 0");
            return v;
        }

        private BigDecimal bd(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException("Invalid numeric result");
            }
            return new BigDecimal(value, MathContext.DECIMAL64);
        }

        private BigDecimal min(List<BigDecimal> args) {
            if (args == null || args.isEmpty()) throw new IllegalArgumentException("min requires at least one argument");
            BigDecimal m = args.get(0);
            for (int i = 1; i < args.size(); i++) if (args.get(i).compareTo(m) < 0) m = args.get(i);
            return m;
        }

        private BigDecimal max(List<BigDecimal> args) {
            if (args == null || args.isEmpty()) throw new IllegalArgumentException("max requires at least one argument");
            BigDecimal m = args.get(0);
            for (int i = 1; i < args.size(); i++) if (args.get(i).compareTo(m) > 0) m = args.get(i);
            return m;
        }

        private BigDecimal sum(List<BigDecimal> args) {
            if (args == null || args.isEmpty()) throw new IllegalArgumentException("sum requires at least one argument");
            BigDecimal s = BigDecimal.ZERO;
            for (BigDecimal v : args) s = s.add(v, MathContext.DECIMAL64);
            return s;
        }

        private BigDecimal mean(List<BigDecimal> args) {
            return sum(args).divide(new BigDecimal(args.size()), MathContext.DECIMAL64);
        }

        private char currentChar() {
            if (pos < 0 || pos >= expression.length()) return '\0';
            return expression.charAt(pos);
        }

        private boolean isIdentifierStart(char c) {
            return Character.isLetter(c) || c == '_';
        }

        private void skipWs() {
            while (pos < expression.length() && Character.isWhitespace(expression.charAt(pos))) pos++;
        }

        private boolean eat(char c) {
            if (pos < expression.length() && expression.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }
    }
}
