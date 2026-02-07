/**
 *  NumberParserTool
 *  Copyright 2026 by Michael Peter Christen
 *  First released 07.02.2026 at https://yacy.net
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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.ToolHandler;

public class NumberParserTool implements ToolHandler {

    private static final String NAME = "number_parser";

    private enum Lang {
        DE, EN, FR, ES, IT
    }

    private static final class Lexicon {
        final Map<String, Integer> small = new HashMap<>();
        final Map<String, Integer> scales = new HashMap<>();
        final String decimalMarker;
        Lexicon(String decimalMarker) {
            this.decimalMarker = decimalMarker;
        }
    }

    private static final Map<Lang, Lexicon> LEX = buildLexicons();

    @Override
    public JSONObject definition() throws JSONException {
        JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "Parse written number words into numeric values. Supports de/en/fr/es/it.");

        JSONObject params = new JSONObject(true);
        params.put("type", "object");
        JSONObject props = new JSONObject(true);

        props.put("text", new JSONObject(true)
            .put("type", "string")
            .put("description", "Number words, e.g. einundzwanzig, five hundred seven, vingt-et-un."));

        props.put("language", new JSONObject(true)
            .put("type", "string")
            .put("description", "Optional: de|en|fr|es|it|auto (default auto)."));

        props.put("strict", new JSONObject(true)
            .put("type", "boolean")
            .put("description", "If true (default), all tokens must be recognized as number words."));

        params.put("properties", props);
        params.put("required", new JSONArray().put("text"));
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    @Override
    public int maxCallsPerTurn() {
        return 10;
    }

    @Override
    public String execute(String arguments) {
        final JSONObject args;
        try {
            args = (arguments == null || arguments.isEmpty()) ? new JSONObject(true) : new JSONObject(arguments);
        } catch (JSONException e) {
            return ToolHandler.errorJson("Invalid arguments JSON");
        }

        final String text = args.optString("text", "").trim();
        if (text.isEmpty()) return ToolHandler.errorJson("Missing text");

        final String language = args.optString("language", "auto").trim().toLowerCase(Locale.ROOT);
        final boolean strict = args.optBoolean("strict", true);

        ParseResult best = null;
        Lang bestLang = null;

        if (!"auto".equals(language)) {
            Lang lang = parseLang(language);
            if (lang == null) return ToolHandler.errorJson("Invalid language. Use de|en|fr|es|it|auto");
            ParseResult r = parseWithLang(text, lang, strict);
            if (r == null) return ToolHandler.errorJson("Could not parse number words");
            best = r;
            bestLang = lang;
        } else {
            for (Lang lang : Lang.values()) {
                ParseResult r = parseWithLang(text, lang, strict);
                if (r == null) continue;
                if (best == null || r.score > best.score) {
                    best = r;
                    bestLang = lang;
                }
            }
            if (best == null) return ToolHandler.errorJson("Could not parse number words in supported languages");
        }

        try {
            JSONObject out = new JSONObject(true);
            out.put("tool", NAME);
            out.put("text", text);
            out.put("language", bestLang.name().toLowerCase(Locale.ROOT));
            out.put("value", best.value.stripTrailingZeros().toPlainString());
            return out.toString();
        } catch (JSONException e) {
            return ToolHandler.errorJson("Failed to build number parser response");
        }
    }

    private static Lang parseLang(String code) {
        if ("de".equals(code)) return Lang.DE;
        if ("en".equals(code)) return Lang.EN;
        if ("fr".equals(code)) return Lang.FR;
        if ("es".equals(code)) return Lang.ES;
        if ("it".equals(code)) return Lang.IT;
        return null;
    }

    private static final class ParseResult {
        final BigDecimal value;
        final int score;
        ParseResult(BigDecimal value, int score) {
            this.value = value;
            this.score = score;
        }
    }

    private static ParseResult parseWithLang(String input, Lang lang, boolean strict) {
        String normalized = normalize(input);
        List<String> tokens = tokenizeByLanguage(normalized, lang);
        if (tokens.isEmpty()) return null;

        Lexicon lx = LEX.get(lang);
        if (lx == null) return null;

        int decimalIndex = tokens.indexOf(lx.decimalMarker);
        BigDecimal intPart;
        int consumedInt;
        if (decimalIndex >= 0) {
            ParseSpan left = parseIntegerTokens(tokens.subList(0, decimalIndex), lang, strict);
            if (left == null) return null;
            intPart = new BigDecimal(left.value);
            consumedInt = left.consumed;

            ParseFraction frac = parseFractionTokens(tokens.subList(decimalIndex + 1, tokens.size()), lang, strict);
            if (frac == null) return null;
            BigDecimal value = intPart.add(frac.value);
            int score = consumedInt + frac.consumed + 1;
            return new ParseResult(value, score);
        }

        ParseSpan span = parseIntegerTokens(tokens, lang, strict);
        if (span == null) return null;
        return new ParseResult(new BigDecimal(span.value), span.consumed);
    }

    private static final class ParseSpan {
        final long value;
        final int consumed;
        ParseSpan(long value, int consumed) {
            this.value = value;
            this.consumed = consumed;
        }
    }

    private static ParseSpan parseIntegerTokens(List<String> tokens, Lang lang, boolean strict) {
        if (tokens.isEmpty()) return new ParseSpan(0L, 0);
        Lexicon lx = LEX.get(lang);
        long total = 0L;
        long current = 0L;
        int consumed = 0;

        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t == null || t.isEmpty() || "and".equals(t) || "et".equals(t) || "y".equals(t) || "und".equals(t) || "e".equals(t)) {
                continue;
            }

            if (isDigits(t)) {
                long v = Long.parseLong(t);
                current += v;
                consumed++;
                continue;
            }

            // French special: quatre vingt => 80
            if (lang == Lang.FR && "quatre".equals(t) && i + 1 < tokens.size() && "vingt".equals(tokens.get(i + 1))) {
                current += 80;
                consumed += 2;
                i++;
                continue;
            }

            Integer small = lx.small.get(t);
            if (small != null) {
                current += small.intValue();
                consumed++;
                continue;
            }

            if ("hundred".equals(t) || "hundert".equals(t) || "cent".equals(t) || "cien".equals(t) || "ciento".equals(t) || "cento".equals(t)) {
                if (current == 0) current = 1;
                current *= 100;
                consumed++;
                continue;
            }

            Integer scale = lx.scales.get(t);
            if (scale != null) {
                if (current == 0) current = 1;
                total += current * scale.longValue();
                current = 0;
                consumed++;
                continue;
            }

            if (strict) return null;
        }
        return new ParseSpan(total + current, consumed);
    }

    private static final class ParseFraction {
        final BigDecimal value;
        final int consumed;
        ParseFraction(BigDecimal value, int consumed) {
            this.value = value;
            this.consumed = consumed;
        }
    }

    private static ParseFraction parseFractionTokens(List<String> tokens, Lang lang, boolean strict) {
        if (tokens.isEmpty()) return new ParseFraction(BigDecimal.ZERO, 0);
        Lexicon lx = LEX.get(lang);
        StringBuilder digits = new StringBuilder();
        int consumed = 0;
        for (String t : tokens) {
            if (t == null || t.isEmpty()) continue;
            if (isDigits(t)) {
                digits.append(t);
                consumed++;
                continue;
            }
            Integer v = lx.small.get(t);
            if (v != null && v.intValue() >= 0 && v.intValue() <= 9) {
                digits.append(v.intValue());
                consumed++;
                continue;
            }
            if (strict) return null;
        }
        if (digits.length() == 0) return new ParseFraction(BigDecimal.ZERO, consumed);
        BigDecimal frac = new BigDecimal("0." + digits.toString());
        return new ParseFraction(frac, consumed);
    }

    private static boolean isDigits(String t) {
        if (t == null || t.isEmpty()) return false;
        for (int i = 0; i < t.length(); i++) if (!Character.isDigit(t.charAt(i))) return false;
        return true;
    }

    private static String normalize(String s) {
        String n = s == null ? "" : s.toLowerCase(Locale.ROOT);
        n = n.replace('’', '\'');
        n = n.replace('-', ' ');
        n = n.replace("ß", "ss");
        n = Normalizer.normalize(n, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        n = n.replaceAll("[^a-z0-9' ]", " ");
        n = n.replaceAll("\\s+", " ").trim();
        return n;
    }

    private static List<String> tokenizeByLanguage(String normalized, Lang lang) {
        if (normalized.isEmpty()) return new ArrayList<>();
        String s = normalized;

        if (lang == Lang.DE) {
            // Split common German compounding boundaries in one pass to avoid
            // destructive re-replacements (e.g. "hundert" -> "... t").
            s = s.replaceAll("(millionen|million)", " million ");
            s = s.replaceAll("(milliarden|milliarde)", " milliarde ");
            s = s.replaceAll("(tausend)", " tausend ");
            s = s.replaceAll("(hundert|hunder)", " hundert ");
            s = s.replace("tausend", " tausend ");
            // Split forms like "hundertundsieben" -> "hundert und sieben".
            s = s.replaceAll("\\bund(ein|eine|eins|zwei|drei|vier|funf|fuenf|sechs|sieben|acht|neun|zehn|elf|zwolf|zwoelf|dreizehn|vierzehn|funfzehn|fuenfzehn|sechzehn|siebzehn|achtzehn|neunzehn|zwanzig|dreissig|dreiig|vierzig|funfzig|fuenfzig|sechzig|siebzig|achtzig|neunzig)\\b", "und $1");
            // Split only canonical DE one-and-tens compounds (e.g. einundzwanzig).
            s = s.replaceAll("(ein|eine|zwei|drei|vier|funf|fuenf|sechs|sieben|acht|neun)und(zwanzig|dreissig|dreiig|vierzig|funfzig|fuenfzig|sechzig|siebzig|achtzig|neunzig)", "$1 und $2");
            s = s.replace("eins", "ein");
        }

        if (lang == Lang.ES) {
            s = s.replaceAll("veinti([a-z]+)", "veinte $1");
            s = s.replaceAll("dieci([a-z]+)", "diez $1");
        }

        if (lang == Lang.IT) {
            s = s.replaceAll("(venti|trenta|quaranta|cinquanta|sessanta|settanta|ottanta|novanta)([a-z]+)", "$1 $2");
        }

        if (lang == Lang.FR) {
            s = s.replace("quatre vingt", "quatre vingt");
        }

        s = s.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(s.split(" ")));
    }

    private static Map<Lang, Lexicon> buildLexicons() {
        Map<Lang, Lexicon> map = new HashMap<>();

        Lexicon de = new Lexicon("komma");
        putAll(de.small,
            "null",0, "ein",1, "eine",1, "einen",1, "eins",1, "zwo",2, "zwei",2, "drei",3, "vier",4, "funf",5, "fuenf",5, "sechs",6,
            "sieben",7, "acht",8, "neun",9, "zehn",10, "elf",11, "zwolf",12, "zwoelf",12, "dreizehn",13, "vierzehn",14,
            "funfzehn",15, "fuenfzehn",15, "sechzehn",16, "siebzehn",17, "achtzehn",18, "neunzehn",19,
            "zwanzig",20, "dreissig",30, "dreiig",30, "vierzig",40, "funfzig",50, "fuenfzig",50, "sechzig",60, "siebzig",70,
            "achtzig",80, "neunzig",90);
        putAll(de.scales, "tausend",1000, "million",1000000, "millionen",1000000, "milliarde",1000000000, "milliarden",1000000000);
        map.put(Lang.DE, de);

        Lexicon en = new Lexicon("point");
        putAll(en.small,
            "zero",0, "one",1, "two",2, "three",3, "four",4, "five",5, "six",6, "seven",7, "eight",8, "nine",9,
            "ten",10, "eleven",11, "twelve",12, "thirteen",13, "fourteen",14, "fifteen",15, "sixteen",16,
            "seventeen",17, "eighteen",18, "nineteen",19,
            "twenty",20, "thirty",30, "forty",40, "fifty",50, "sixty",60, "seventy",70, "eighty",80, "ninety",90);
        putAll(en.scales, "thousand",1000, "million",1000000, "billion",1000000000);
        map.put(Lang.EN, en);

        Lexicon fr = new Lexicon("virgule");
        putAll(fr.small,
            "zero",0, "un",1, "une",1, "deux",2, "trois",3, "quatre",4, "cinq",5, "six",6, "sept",7, "huit",8, "neuf",9,
            "dix",10, "onze",11, "douze",12, "treize",13, "quatorze",14, "quinze",15, "seize",16,
            "vingt",20, "trente",30, "quarante",40, "cinquante",50, "soixante",60, "soixantedix",70,
            "quatrevingt",80, "quatrevingtdix",90);
        putAll(fr.scales, "mille",1000, "million",1000000, "milliard",1000000000);
        map.put(Lang.FR, fr);

        Lexicon es = new Lexicon("coma");
        putAll(es.small,
            "cero",0, "uno",1, "una",1, "dos",2, "tres",3, "cuatro",4, "cinco",5, "seis",6, "siete",7, "ocho",8, "nueve",9,
            "diez",10, "once",11, "doce",12, "trece",13, "catorce",14, "quince",15, "dieciseis",16,
            "diecisiete",17, "dieciocho",18, "diecinueve",19,
            "veinte",20, "treinta",30, "cuarenta",40, "cincuenta",50, "sesenta",60, "setenta",70, "ochenta",80, "noventa",90,
            "cien",100, "ciento",100, "doscientos",200, "trescientos",300, "cuatrocientos",400, "quinientos",500,
            "seiscientos",600, "setecientos",700, "ochocientos",800, "novecientos",900);
        putAll(es.scales, "mil",1000, "millon",1000000, "millones",1000000, "milmillon",1000000000);
        map.put(Lang.ES, es);

        Lexicon it = new Lexicon("virgola");
        putAll(it.small,
            "zero",0, "uno",1, "una",1, "due",2, "tre",3, "quattro",4, "cinque",5, "sei",6, "sette",7, "otto",8, "nove",9,
            "dieci",10, "undici",11, "dodici",12, "tredici",13, "quattordici",14, "quindici",15, "sedici",16,
            "diciassette",17, "diciotto",18, "diciannove",19,
            "venti",20, "trenta",30, "quaranta",40, "cinquanta",50, "sessanta",60, "settanta",70, "ottanta",80, "novanta",90,
            "cento",100, "duecento",200, "trecento",300, "quattrocento",400, "cinquecento",500,
            "seicento",600, "settecento",700, "ottocento",800, "novecento",900);
        putAll(it.scales, "mille",1000, "mila",1000, "milione",1000000, "milioni",1000000, "miliardo",1000000000);
        map.put(Lang.IT, it);

        return map;
    }

    private static void putAll(Map<String, Integer> m, Object... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), Integer.valueOf(((Number) kv[i + 1]).intValue()));
        }
    }
}
