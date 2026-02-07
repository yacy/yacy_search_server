/**
 *  ChitChatTool
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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.ToolHandler;

public class ChitChatTool implements ToolHandler {

    private static final String NAME = "chitchat";
    private static final int DEFAULT_FACT_COUNT = 3;
    private static final int MAX_FACT_COUNT = 5;

    @Override
    public JSONObject definition() throws JSONException {
        JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "Provide friendly chitchat. Call me when the user just tests the chat, says hello, is unsure with the prompt or in case of profanity.");

        JSONObject params = new JSONObject(true);
        params.put("type", "object");
        JSONObject props = new JSONObject(true);

        JSONObject tone = new JSONObject(true);
        tone.put("type", "string");
        tone.put("description", "Optional tone hint, e.g. friendly, concise, playful.");
        props.put("tone", tone);

        JSONObject factCount = new JSONObject(true);
        factCount.put("type", "integer");
        factCount.put("description", "How many ability facts to include (1-5, default 3).");
        props.put("fact_count", factCount);

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
        String tone = "friendly";
        int factCount = DEFAULT_FACT_COUNT;
        if (arguments != null && !arguments.isEmpty()) {
            try {
                JSONObject args = new JSONObject(arguments);
                tone = args.optString("tone", tone).trim();
                factCount = args.optInt("fact_count", factCount);
            } catch (JSONException e) {
                return ToolHandler.errorJson("Invalid arguments JSON");
            }
        }

        int count = clamp(factCount, 1, MAX_FACT_COUNT);
        List<String> selected = pickFacts(count);
        String message = buildMessage(tone, selected);

        try {
            JSONObject result = new JSONObject(true);
            result.put("tool", NAME);
            result.put("tone", tone);
            result.put("message", message);
            result.put("facts", new JSONArray(selected));
            return result.toString();
        } catch (JSONException e) {
            return ToolHandler.errorJson("Failed to build chitchat response");
        }
    }

    private static List<String> pickFacts(int count) {
        List<String> facts = new ArrayList<>(SelfReflectTool.facts());
        if (facts.isEmpty()) return facts;
        Collections.shuffle(facts, ThreadLocalRandom.current());
        int n = Math.min(count, facts.size());
        return new ArrayList<>(facts.subList(0, n));
    }

    private static String buildMessage(String tone, List<String> facts) {
        StringBuilder sb = new StringBuilder();
        sb.append("Happy to chat. ");
        sb.append(SelfReflectTool.purpose());
        if (tone != null && !tone.isEmpty()) {
            sb.append(" Tone requested: ").append(tone).append(". ");
        } else {
            sb.append(" ");
        }
        sb.append("Here are a few things I can do: ");
        for (int i = 0; i < facts.size(); i++) {
            if (i > 0) sb.append(" ");
            sb.append(i + 1).append(") ").append(facts.get(i));
        }
        return sb.toString();
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
