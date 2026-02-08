/**
 *  PromptToMermaidTool
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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.yacy.ai.ToolHandler;
import net.yacy.ai.LLM;

public class PromptToMermaidTool implements ToolHandler {

    private static final String NAME = "prompt_to_mermaid";
    //private static final List<String> DIAGRAM_TYPES = Arrays.asList("flowchart", "sequence", "graph");

    private static final String SYSTEM_PROMPT =
            "You convert natural-language process descriptions into Mermaid diagrams with deterministic, transparent behavior. "
          + "Prefer explicit structure only from user text. Do not invent hidden steps. "
          + "Support flowchart, sequence diagram, and graph only. "
          + "If prompt is ambiguous or too vague, return fallback with plain-language summary and no forced diagram. "
          + "Return ONLY JSON matching the requested fields.";

    private static final JSONObject RESPONSE_SCHEMA = buildResponseSchema();

    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b\\+?[0-9][0-9\\s().-]{6,}[0-9]\\b");

    @Override
    public JSONObject definition() throws JSONException {
        JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "Convert a user prompt into a Mermaid diagram with fallback summary when prompt is ambiguous.");

        JSONObject params = new JSONObject(true);
        params.put("type", "object");
        JSONObject props = new JSONObject(true);

        JSONObject prompt = new JSONObject(true);
        prompt.put("type", "string");
        prompt.put("description", "Natural-language diagram request.");
        props.put("prompt", prompt);

        JSONObject preferredType = new JSONObject(true);
        preferredType.put("type", "string");
        preferredType.put("description", "Optional hint: flowchart, sequence, graph.");
        props.put("preferred_diagram_type", preferredType);

        params.put("properties", props);
        params.put("required", new JSONArray().put("prompt"));
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    
    public int maxCallsPerTurn() {
        return 1;
    }

    
    public String execute(String arguments) {
        final JSONObject args;
        try {
            args = (arguments == null || arguments.isEmpty()) ? new JSONObject(true) : new JSONObject(arguments);
        } catch (JSONException e) {
            return fallback("Invalid arguments JSON", "", null, Arrays.asList("Could not parse tool arguments"));
        }

        String prompt = args.optString("prompt", "").trim();
        String preferredType = normalizeType(args.optString("preferred_diagram_type", null));
        if (prompt.isEmpty()) {
            return fallback("Missing prompt", "", preferredType, Arrays.asList("No prompt text provided"));
        }

        String redactedPrompt = redactSensitive(prompt);
        if (isVague(redactedPrompt)) {
            return fallback("Prompt is too vague for deterministic diagram generation", redactedPrompt, preferredType,
                    Arrays.asList("Add concrete steps, actors, or relationships", "Fallback used to avoid hallucinated structure"));
        }

        LLM.LLMModel llmModel = LLM.llmFromUsage(LLM.LLMUsage.chat);
        if (llmModel == null || llmModel.llm == null || llmModel.model == null || llmModel.model.isEmpty()) {
            return fallback("No chat model configured for Mermaid generation", redactedPrompt, preferredType,
                    Arrays.asList("Configure a chat model in AI production models"));
        }

        final String userPrompt = buildUserPrompt(redactedPrompt, preferredType);
        try {
            String raw = queryModel(llmModel, userPrompt, RESPONSE_SCHEMA);
            JSONObject parsed = parseResponseObject(raw);
            return normalizeResult(parsed, redactedPrompt, preferredType).toString();
        } catch (IOException e) {
            try {
                String raw = queryModel(llmModel, userPrompt, null);
                JSONObject parsed = parseResponseObject(raw);
                return normalizeResult(parsed, redactedPrompt, preferredType).toString();
            } catch (Exception retryError) {
                return fallback("LLM generation failed", redactedPrompt, preferredType,
                        Arrays.asList("Model call failed: " + retryError.getMessage()));
            }
        } catch (Exception e) {
            return fallback("Invalid model output", redactedPrompt, preferredType,
                    Arrays.asList("Failed to parse model output: " + e.getMessage()));
        }
    }

    private static String queryModel(LLM.LLMModel llmModel, String userPrompt, JSONObject schema) throws IOException {
        try {
            LLM.Context context = new LLM.Context(SYSTEM_PROMPT);
            context.addPrompt(userPrompt);
            return llmModel.llm.chat(llmModel.model, context, schema, 1200);
        } catch (JSONException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private static JSONObject parseResponseObject(String raw) throws JSONException {
        if (raw == null) throw new JSONException("Empty model response");
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence >= 0) {
                trimmed = trimmed.substring(0, lastFence).trim();
            }
        }
        return new JSONObject(new JSONTokener(trimmed));
    }

    private static JSONObject normalizeResult(JSONObject parsed, String prompt, String preferredType) throws JSONException {
        String status = parsed.optString("status", "fallback").trim().toLowerCase(Locale.ROOT);
        if (!"success".equals(status) && !"fallback".equals(status)) status = "fallback";

        String diagramType = normalizeType(parsed.optString("diagram_type", null));
        String mermaidCode = parsed.optString("mermaid_code", "").trim();
        String reasoning = parsed.optString("reasoning", "").trim();
        JSONArray warnings = parsed.optJSONArray("warnings");
        if (warnings == null) warnings = new JSONArray();

        if (reasoning.isEmpty()) {
            reasoning = "Generated from prompt using local structured conversion rules and LLM formatting.";
        }

        if ("success".equals(status)) {
            if (diagramType == null) {
                status = "fallback";
                warnings.put("diagram_type missing or invalid");
            }
            if (!isValidMermaid(mermaidCode, diagramType)) {
                status = "fallback";
                warnings.put("Invalid Mermaid syntax for declared diagram_type");
            }
        }

        if ("fallback".equals(status)) {
            String fallbackReason = reasoning.isEmpty() ? "Prompt could not be deterministically mapped to a clear diagram." : reasoning;
            return buildResult("fallback", null, "", "", fallbackReason, warnings, prompt, preferredType);
        }
        String mermaidAscii = toAsciiDiagram(mermaidCode);
        return buildResult("success", diagramType, mermaidCode, mermaidAscii, reasoning, warnings, prompt, preferredType);
    }

    private static JSONObject buildResult(String status, String diagramType, String mermaidCode, String mermaidAscii, String reasoning,
            JSONArray warnings, String prompt, String preferredType) throws JSONException {
        JSONObject out = new JSONObject(true);
        out.put("status", status);
        out.put("diagram_type", diagramType == null ? JSONObject.NULL : diagramType);
        out.put("mermaid_code", mermaidCode == null ? "" : mermaidCode);
        out.put("mermaid_ascii", mermaidAscii == null ? "" : mermaidAscii);
        out.put("reasoning", reasoning == null ? "" : reasoning);
        out.put("warnings", warnings == null ? new JSONArray() : warnings);
        if (prompt != null && !prompt.isEmpty()) out.put("prompt_redacted", prompt);
        if (preferredType != null) out.put("preferred_diagram_type", preferredType);
        return out;
    }

    private static boolean isValidMermaid(String code, String diagramType) {
        if (code == null || code.trim().isEmpty() || diagramType == null) return false;
        String text = code.trim().toLowerCase(Locale.ROOT);
        if ("flowchart".equals(diagramType)) return text.startsWith("flowchart");
        if ("sequence".equals(diagramType)) return text.startsWith("sequencediagram");
        if ("graph".equals(diagramType)) return text.startsWith("graph");
        return false;
    }

    private static String buildUserPrompt(String prompt, String preferredType) {
        StringBuilder sb = new StringBuilder();
        sb.append("Convert the following user request into Mermaid.\n");
        sb.append("Requirements:\n");
        sb.append("- deterministic interpretation only\n");
        sb.append("- supported diagram types: flowchart, sequence, graph\n");
        sb.append("- if ambiguous, return fallback with summary reasoning\n");
        sb.append("- include warnings for assumptions\n");
        sb.append("- output JSON only with keys: status, diagram_type, mermaid_code, reasoning, warnings\n");
        if (preferredType != null) {
            sb.append("- user preferred diagram type: ").append(preferredType).append("\n");
        }
        sb.append("\nUser prompt:\n");
        sb.append(prompt);
        return sb.toString();
    }

    private static boolean isVague(String prompt) {
        if (prompt == null) return true;
        String trimmed = prompt.trim();
        if (trimmed.length() < 24) return true;
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 6) return true;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return !(lower.contains("then") || lower.contains("first") || lower.contains("after")
                || lower.contains("if") || lower.contains("when") || lower.contains("->")
                || lower.contains("sequence") || lower.contains("workflow") || lower.contains("relationship"));
    }

    private static String redactSensitive(String prompt) {
        if (prompt == null) return "";
        String s = EMAIL_PATTERN.matcher(prompt).replaceAll("[EMAIL]");
        s = PHONE_PATTERN.matcher(s).replaceAll("[PHONE]");
        return s;
    }

    private static String normalizeType(String value) {
        if (value == null) return null;
        String type = value.trim().toLowerCase(Locale.ROOT);
        if (type.isEmpty()) return null;
        if ("flowchart".equals(type) || "sequence".equals(type) || "graph".equals(type)) return type;
        if ("sequence diagram".equals(type) || "sequencediagram".equals(type)) return "sequence";
        if ("flow".equals(type)) return "flowchart";
        return null;
    }

    private static String fallback(String reason, String prompt, String preferredType, List<String> warnings) {
        try {
            JSONArray w = new JSONArray();
            if (warnings != null) {
                for (String warning : warnings) {
                    if (warning != null && !warning.isEmpty()) w.put(warning);
                }
            }
            return buildResult("fallback", null, "", "",
                    reason == null ? "Fallback used due to insufficient structure in prompt." : reason,
                    w, prompt, preferredType).toString();
        } catch (JSONException e) {
            return ToolHandler.errorJson("Failed to build fallback response");
        }
    }

    private static String toAsciiDiagram(String mermaidCode) {
        if (mermaidCode == null || mermaidCode.trim().isEmpty()) return "";
        try {
            // Render in strict ASCII to keep tool output model-friendly and terminal-safe.
            return Mermaid2ASCIITool.renderMermaidAscii(mermaidCode, true, 3, 2, 1);
        } catch (Exception e) {
            return "";
        }
    }

    private static JSONObject buildResponseSchema() {
        try {
            JSONObject schema = new JSONObject(true);
            schema.put("title", "PromptToMermaidResult");
            schema.put("type", "object");
            JSONObject properties = new JSONObject(true);

            JSONObject status = new JSONObject(true);
            status.put("type", "string");
            status.put("enum", new JSONArray().put("success").put("fallback"));
            properties.put("status", status);

            JSONObject diagramType = new JSONObject(true);
            diagramType.put("type", "string");
            diagramType.put("enum", new JSONArray().put("flowchart").put("sequence").put("graph"));
            properties.put("diagram_type", diagramType);

            JSONObject mermaid = new JSONObject(true);
            mermaid.put("type", "string");
            properties.put("mermaid_code", mermaid);

            JSONObject reasoning = new JSONObject(true);
            reasoning.put("type", "string");
            properties.put("reasoning", reasoning);

            JSONObject warnings = new JSONObject(true);
            warnings.put("type", "array");
            warnings.put("items", new JSONObject(true).put("type", "string"));
            properties.put("warnings", warnings);

            schema.put("properties", properties);
            schema.put("required", new JSONArray().put("status").put("diagram_type").put("mermaid_code").put("reasoning").put("warnings"));
            return schema;
        } catch (JSONException e) {
            return null;
        }
    }
}
