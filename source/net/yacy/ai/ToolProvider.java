/**
 *  ToolProvider
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

package net.yacy.ai;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.tools.CalculatorTool;
import net.yacy.ai.tools.ChitChatTool;
import net.yacy.ai.tools.DateMathTool;
import net.yacy.ai.tools.DateTimeTool;
import net.yacy.ai.tools.HttpJsonTool;
import net.yacy.ai.tools.Mermaid2ASCIITool;
import net.yacy.ai.tools.NumberParserTool;
import net.yacy.ai.tools.PromptToMermaidTool;
import net.yacy.ai.tools.SelfReflectTool;
import net.yacy.ai.tools.TableOpsTool;
import net.yacy.ai.tools.UnitConverterTool;
import net.yacy.ai.tools.WebFetchTool;
import net.yacy.ai.tools.WikipediaLinkCreatorTool;

/**
 * Central registry and dispatch utility for all built-in YaCy LLM tools.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Expose tool JSON schemas into outbound chat-completion requests.</li>
 *   <li>Resolve tool names to handlers.</li>
 *   <li>Execute tool calls by name with raw JSON argument text.</li>
 * </ul>
 */
public final class ToolProvider {

    /**
     * Ordered list of built-in tool handlers. Order is preserved when exposing
     * definitions to providers.
     */
    private static final List<ToolHandler> TOOLS = Arrays.asList(
            new DateTimeTool(),
            new DateMathTool(),
            new CalculatorTool(),
            new NumberParserTool(),
            new UnitConverterTool(),
            new WebFetchTool(),
            new HttpJsonTool(),
            new TableOpsTool(),
            new SelfReflectTool(),
            new ChitChatTool(),
            new PromptToMermaidTool(),
            new Mermaid2ASCIITool(),
            new WikipediaLinkCreatorTool()
    );

    /**
     * Lookup table for fast runtime dispatch by function/tool name.
     */
    private static final Map<String, ToolHandler> TOOL_BY_NAME = buildToolIndex(TOOLS);

    /**
     * Utility class; not instantiable.
     */
    private ToolProvider() {}

    /**
     * Ensures a chat request body contains all available tool definitions and a
     * default tool selection mode.
     * <p>
     * Existing tool definitions are kept; missing ones are appended.
     *
     * @param body request body to mutate
     */
    public static void ensureTools(JSONObject body) {
        if (body == null) return;
        try {
            // Create "tools" array lazily when caller did not provide one.
            JSONArray tools = body.optJSONArray("tools");
            if (tools == null) {
                tools = new JSONArray();
                body.put("tools", tools);
            }
            // Merge registry definitions without duplicating by tool name.
            for (ToolHandler tool : TOOLS) {
                JSONObject definition = tool.definition();
                addToolDefinitionIfMissing(tools, definition);
            }
            // Providers usually expect explicit tool-choice mode.
            if (!body.has("tool_choice")) {
                body.put("tool_choice", "auto");
            }
        } catch (JSONException e) {
            // keep body unchanged if tool schema cannot be injected
        }
    }

    /**
     * Dispatches one tool invocation by name.
     *
     * @param name tool/function name
     * @param arguments raw JSON arguments string
     * @return tool execution result as JSON string (or error JSON)
     */
    public static String executeTool(String name, String arguments) {
        if (name == null) return errorJson("Invalid tool call");
        ToolHandler tool = TOOL_BY_NAME.get(name);
        if (tool == null) return errorJson("Unknown tool: " + name);
        // Tool implementations are responsible for argument parsing/validation.
        return tool.execute(arguments);
    }

    /**
     * Returns the configured maximum number of calls for a tool within one tool
     * turn lifecycle.
     *
     * @param name tool/function name
     * @return maximum call count, defaults to 1 for unknown/invalid values
     */
    public static int maxCallsPerTurn(String name) {
        if (name == null) return 1;
        ToolHandler tool = TOOL_BY_NAME.get(name);
        if (tool == null) return 1;
        int max = tool.maxCallsPerTurn();
        return max <= 0 ? 1 : max;
    }

    /**
     * Builds an immutable map from tool name to handler.
     * Invalid or unnamed definitions are skipped.
     *
     * @param tools source handlers
     * @return unmodifiable name-indexed map
     */
    private static Map<String, ToolHandler> buildToolIndex(List<ToolHandler> tools) {
        Map<String, ToolHandler> byName = new LinkedHashMap<>();
        for (ToolHandler tool : tools) {
            if (tool == null) continue;
            try {
                String name = extractToolName(tool.definition());
                if (name == null || name.isEmpty()) continue;
                byName.put(name, tool);
            } catch (JSONException e) {
                // skip invalid tool definition
            }
        }
        return Collections.unmodifiableMap(byName);
    }

    /**
     * Appends a tool definition if no existing tool with the same function name
     * is already present.
     *
     * @param tools destination tool array in request body
     * @param toolDefinition candidate definition
     */
    private static void addToolDefinitionIfMissing(JSONArray tools, JSONObject toolDefinition) {
        if (tools == null || toolDefinition == null) return;
        try {
            String name = extractToolName(toolDefinition);
            if (name == null || name.isEmpty()) return;
            // Dedupe by logical function name (not by full JSON string equality).
            for (int i = 0; i < tools.length(); i++) {
                JSONObject existing = tools.optJSONObject(i);
                if (existing == null) continue;
                String existingName = extractToolName(existing);
                if (name.equals(existingName)) return;
            }
            tools.put(toolDefinition);
        } catch (JSONException e) {
            // ignore malformed tool schema
        }
    }

    /**
     * Extracts the function name from a tool definition object.
     *
     * @param toolDefinition MCP/OpenAI-style tool definition
     * @return trimmed function name or {@code null}
     * @throws JSONException if JSON access fails unexpectedly
     */
    private static String extractToolName(JSONObject toolDefinition) throws JSONException {
        if (toolDefinition == null) return null;
        JSONObject fn = toolDefinition.optJSONObject("function");
        if (fn == null) return null;
        String name = fn.optString("name", "");
        return name == null ? null : name.trim();
    }

    /**
     * Builds a minimal error JSON string while avoiding propagation of secondary
     * JSON construction failures.
     *
     * @param message human-readable error message
     * @return serialized JSON error object
     */
    private static String errorJson(String message) {
        try {
            JSONObject err = new JSONObject(true);
            err.put("error", message == null ? "error" : message);
            return err.toString();
        } catch (JSONException e) {
            return "{\"error\":\"" + (message == null ? "error" : message.replace("\"", "'")) + "\"}";
        }
    }
}
