/**
 *  SelfReflectTool
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.ToolHandler;

public class SelfReflectTool implements ToolHandler {

    private static final String NAME = "self_reflect";

    private static final String PURPOSE =
            "YaCy AI aims to be the good one: privacy-aware, open, and useful AI that combines LLM responses with transparent tools and retrieval from YaCy search.";

    private static final List<String> FACTS = Collections.unmodifiableList(Arrays.asList(
            "You can choose default dialog augmentation mode: no search, local search, or global search.",
            "You can click the search button for the current message to attach search results as context without uploading a file.",
            "You can run with no search and attach your own files instead, so you control exactly what context is sent.",
            "You can attach PNG/JPG images and text files (.txt, .md, .tex), preview them, download them, or open them in a full view.",
            "You can drag and drop supported files directly into the composer.",
            "You can see transparency in action: search results are turned into virtual attachments so you can inspect which documents contributed to the answer. Click on the attachment button in your prompt to see the search answers!",
            "You can see each tool call with wrench icons (click on it!) in assistant messages and open per-call details (arguments and tool response).",
            "You can use deterministic tools such as datetime, calculator, webfetch, http_json, and table_ops to improve answer quality.",
            "You can copy assistant answers, trim user prompts, and delete a user/assistant turn pair for iterative editing.",
            "You can clear chat history, download the chat as JSON, and upload it again to continue later.",
            "You can show or hide the system prompt in the interface to better understand chat behavior.",
            "You can read markdown-formatted answers, including code blocks with syntax highlighting and collapsible model-thought sections when present.",
            "You can use YaCy AI components beyond chat via standards-oriented integration points such as RAG proxy and MCP tooling."
    ));

    private static final List<String> IDEAS = Collections.unmodifiableList(Arrays.asList(
            "Transparency, not mystery: ranking, retrieval, and model use should be explainable and inspectable.",
            "Autonomy, no dependency: avoid hidden data flows and centralized lock-in whenever possible.",
            "Collaboration with user control: intelligence should grow from shared knowledge while users stay in charge.",
            "Reproducibility: users should be able to choose models and verify outcomes.",
            "Integration instead of isolation: AI should be part of the open web, not a wall around it.",
            "Teach openly: communicate purpose and benefit so AI is understandable, not opaque or intimidating.",
            "YaCy extends open-search principles into AI: openness, autonomy, and trust."
    ));

    private static final String TALKS_URL = "https://www.youtube.com/orbiterlab";

    @Override
    public JSONObject definition() throws JSONException {
        JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "This is the help function. Explain what YaCy AI does and why it is built this way. Use when user asks about abilities, purpose, or philosophy.");
        JSONObject params = new JSONObject(true);
        params.put("type", "object");
        params.put("properties", new JSONObject(true));
        params.put("required", new JSONArray());
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    
    public int maxCallsPerTurn() {
        return 1;
    }

    
    public String execute(String arguments) {
        try {
            JSONObject result = new JSONObject(true);
            result.put("tool", NAME);
            result.put("purpose", PURPOSE);
            result.put("facts", new JSONArray(FACTS));
            result.put("ideas", new JSONArray(IDEAS));
            result.put("talks_url", TALKS_URL);
            return result.toString();
        } catch (JSONException e) {
            return ToolHandler.errorJson("Failed to build self_reflect response");
        }
    }

    public static List<String> facts() {
        return FACTS;
    }

    public static List<String> ideas() {
        return IDEAS;
    }

    public static String purpose() {
        return PURPOSE;
    }
}
