/**
 *  WikipediaLinkCreatorTool
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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.ToolHandler;

public class WikipediaLinkCreatorTool implements ToolHandler {

    private static final String NAME = "wikipedia_link_creator";
    private static final Pattern LANG_PATTERN = Pattern.compile("^[a-z]{2,10}(-[a-z0-9]{2,8})?$");

    @Override
    public JSONObject definition() throws JSONException {
        JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "In case you don't know the answer correctly or you are unsure. Build a Wikipedia article URL from topic keywords so it can be fetched with webfetch.");

        JSONObject params = new JSONObject(true);
        params.put("type", "object");
        JSONObject props = new JSONObject(true);

        JSONObject topic = new JSONObject(true);
        topic.put("type", "string");
        topic.put("description", "Target Wikipedia article title/topic (preferred input).");
        props.put("topic", topic);

        JSONObject keywords = new JSONObject(true);
        keywords.put("type", "array");
        keywords.put("description", "Optional keyword list from which a topic is composed.");
        keywords.put("items", new JSONObject(true).put("type", "string"));
        props.put("keywords", keywords);

        JSONObject language = new JSONObject(true);
        language.put("type", "string");
        language.put("description", "Wikipedia language code (e.g. en, de, fr). Defaults to en.");
        props.put("language", language);

        params.put("properties", props);
        params.put("required", new JSONArray());
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

        String topic = normalizeTopic(args.optString("topic", ""));
        JSONArray keywords = args.optJSONArray("keywords");
        String language = normalizeLanguage(args.optString("language", "en"));
        List<String> warnings = new ArrayList<>();

        if (topic.isEmpty() && keywords != null) {
            topic = normalizeTopic(joinKeywords(keywords));
            if (!topic.isEmpty()) {
                warnings.add("Used keywords to compose article topic");
            }
        }
        if (topic.isEmpty()) {
            return ToolHandler.errorJson("Missing topic/keywords");
        }
        if (language == null) {
            language = "en";
            warnings.add("Invalid language code; defaulted to en");
        }

        String encodedTitle = encodeTitle(topic);
        String url = "https://" + language + ".wikipedia.org/wiki/" + encodedTitle;

        try {
            JSONObject result = new JSONObject(true);
            result.put("tool", NAME);
            result.put("topic_input", topic);
            result.put("article_title", topic);
            result.put("language", language);
            result.put("url", url);
            result.put("next_tool_hint", "Use webfetch with this URL to retrieve the article content.");
            result.put("warnings", new JSONArray(warnings));
            return result.toString();
        } catch (JSONException e) {
            return ToolHandler.errorJson("Failed to build wikipedia link response");
        }
    }

    private static String joinKeywords(JSONArray keywords) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keywords.length(); i++) {
            String k = keywords.optString(i, "").trim();
            if (k.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(k);
        }
        return sb.toString();
    }

    private static String normalizeTopic(String topic) {
        if (topic == null) return "";
        String t = topic.trim();
        while (t.contains("  ")) t = t.replace("  ", " ");
        if (t.startsWith("https://") || t.startsWith("http://")) {
            int wikiIdx = t.indexOf("/wiki/");
            if (wikiIdx >= 0) {
                t = t.substring(wikiIdx + 6);
            }
        }
        t = t.replace('_', ' ').trim();
        if (t.indexOf('#') >= 0) t = t.substring(0, t.indexOf('#')).trim();
        return t;
    }

    private static String normalizeLanguage(String language) {
        if (language == null) return "en";
        String l = language.trim().toLowerCase(Locale.ROOT);
        if (l.isEmpty()) return "en";
        if (!LANG_PATTERN.matcher(l).matches()) return null;
        return l;
    }

    private static String encodeTitle(String title) {
        try {
            return URLEncoder.encode(title, StandardCharsets.UTF_8.name()).replace("+", "_");
        } catch (UnsupportedEncodingException e) {
            return title.replace(' ', '_');
        }
    }
}
