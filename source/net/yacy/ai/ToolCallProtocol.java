/**
 *  ToolCallProtocol
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;


/**
 * Implements the protocol glue for streamed chat completions that include tool
 * calls.
 * <p>
 * Main responsibilities:
 * <ul>
 *   <li>Collect partial tool-call fragments from streaming deltas.</li>
 *   <li>Merge fragments into complete tool calls (index-based merge).</li>
 *   <li>Execute tools once a tool turn is complete.</li>
 *   <li>Append assistant + tool messages back to the conversation.</li>
 *   <li>Start follow-up model rounds until no further tool call is requested.</li>
 * </ul>
 * <p>
 * The class also enriches streamed lines with {@code tool-calls} and
 * {@code tool-results} metadata so clients can display tool activity while the
 * stream is forwarded.
 */
public final class ToolCallProtocol {

    /**
     * Hard stop to prevent infinite tool loops (assistant requests tool calls
     * repeatedly without converging to a final answer).
     */
    private static final int MAX_TOOL_ROUNDS = 8;

    /**
     * Utility class; not instantiable.
     */
    private ToolCallProtocol() {}

    /**
     * Builds a request body that is ready for tool-aware chat completion calls.
     * <p>
     * This method clones the input object, injects tool definitions and optionally
     * enforces streaming.
     *
     * @param body original request body
     * @param forceStream when true, sets {@code stream=true} in the cloned body
     * @return prepared request body clone
     */
    public static JSONObject prepareToolRequestBody(JSONObject body, boolean forceStream) {
        try {
            final JSONObject prepared = body == null ? new JSONObject(true) : new JSONObject(body.toString());
            if (forceStream) prepared.put("stream", true);
            net.yacy.ai.ToolProvider.ensureTools(prepared);
            return prepared;
        } catch (JSONException e) {
            final JSONObject fallback = new JSONObject(true);
            net.yacy.ai.ToolProvider.ensureTools(fallback);
            return fallback;
        }
    }

    /**
     * Runs the full server-side tool-stream lifecycle for one chat request.
     * <p>
     * Lifecycle:
     * <ol>
     *   <li>Prepare request body for tools.</li>
     *   <li>Send initial upstream chat request.</li>
     *   <li>Forward initial stream while collecting tool calls.</li>
     *   <li>If tool calls appeared, continue tool rounds until completion.</li>
     * </ol>
     *
     * @param out downstream SSE output stream
     * @param llm4Chat model config
     * @param originalBody request body template
     * @param messages mutable message history
     * @param initialMetadata optional JSON fields to inject into the first JSON data line
     * @return upstream HTTP status code for the initial request
     * @throws IOException on network/stream/protocol errors
     */
    public static int proxyToolLifecycle(ServletOutputStream out, LLM.LLMModel llm4Chat, JSONObject originalBody, JSONArray messages, JSONObject initialMetadata) throws IOException {
        final JSONObject preparedBody = prepareToolRequestBody(originalBody, false);
        final HttpURLConnection conn = openChatCompletionConnection(llm4Chat, preparedBody);
        final int status = conn.getResponseCode();
        if (status == 200) {
            handleInitialStreamAndContinue(out, conn, llm4Chat, preparedBody, messages, initialMetadata);
        }
        return status;
    }

    /**
     * Extracts streamed assistant content and tool-call deltas from one chat chunk.
     * <p>
     * Supports both modern {@code delta.tool_calls} and legacy
     * {@code delta.function_call} formats.
     *
     * @param j streamed JSON chunk
     * @param toolCalls mutable map collecting tool calls by index
     * @param assistantContent buffer accumulating assistant text tokens
     * @param sawToolCalls single-item boolean holder set to true once any tool
     *                     call appears in this stream
     */
    public static void captureToolCalls(JSONObject j, Map<Integer, ToolCall> toolCalls, StringBuilder assistantContent, boolean[] sawToolCalls) {
        if (j == null) return;
        // Only first choice is processed because streaming APIs typically emit one choice.
        JSONArray choices = j.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return;
        JSONObject choice = choices.optJSONObject(0);
        if (choice == null) return;
        JSONObject delta = choice.optJSONObject("delta");
        if (delta == null) return;

        // Assistant text arrives token-by-token in streaming mode.
        String content = delta.optString("content", null);
        if (content != null) assistantContent.append(content);

        // Preferred modern tool-call format.
        JSONArray toolDelta = delta.optJSONArray("tool_calls");
        if (toolDelta != null) {
            sawToolCalls[0] = true;
            mergeToolCalls(toolCalls, toolDelta);
        }

        // Legacy compatibility: transform function_call into tool_calls-like shape.
        JSONObject functionCall = delta.optJSONObject("function_call");
        if (functionCall != null) {
            sawToolCalls[0] = true;
            try {
                JSONArray wrapper = new JSONArray();
                JSONObject entry = new JSONObject(true);
                entry.put("index", 0);
                entry.put("type", "function");
                entry.put("function", functionCall);
                wrapper.put(entry);
                mergeToolCalls(toolCalls, wrapper);
            } catch (JSONException e) {
                // ignore malformed legacy function_call chunk
            }
        }
    }

    /**
     * Forwards the initial upstream stream to the client while collecting tool
     * calls, then executes follow-up tool rounds when needed.
     *
     * @param out downstream SSE output stream
     * @param conn initial upstream connection
     * @param llm4Chat model config for follow-up rounds
     * @param originalBody request body template for follow-up rounds
     * @param messages mutable conversation history
     * @param initialMetadata optional metadata injected once into the first JSON line
     * @throws IOException on I/O errors
     */
    private static void handleInitialStreamAndContinue(ServletOutputStream out, HttpURLConnection conn, LLM.LLMModel llm4Chat, JSONObject originalBody, JSONArray messages, JSONObject initialMetadata) throws IOException {
        final StringBuilder assistantContent = new StringBuilder();
        final Map<Integer, ToolCall> toolCalls = new HashMap<>();
        final boolean[] sawToolCalls = new boolean[]{false};
        boolean firstJsonDataLinePending = initialMetadata != null && initialMetadata.length() > 0;

        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                boolean isDoneLine = "data: [DONE]".equals(line.trim()) || "[DONE]".equals(line.trim());
                if (firstJsonDataLinePending) {
                    String patched = injectJsonMetadata(line, initialMetadata);
                    if (patched != null) {
                        line = patched;
                        firstJsonDataLinePending = false;
                    }
                }
                if (line.startsWith("data:")) {
                    int p = line.indexOf('{');
                    if (p > 0) {
                        try {
                            JSONObject j = new JSONObject(new JSONTokener(line.substring(p)));
                            captureToolCalls(j, toolCalls, assistantContent, sawToolCalls);
                        } catch (JSONException e) {
                            // ignore malformed stream chunks and continue forwarding stream
                        }
                    }
                }
                if (!isDoneLine || !sawToolCalls[0]) {
                    out.println(line);
                    out.flush();
                }
            }
        } finally {
            conn.disconnect();
        }

        if (sawToolCalls[0]) {
            handleToolCallsAndContinue(out, llm4Chat, originalBody, messages, assistantContent.toString(), toolCalls);
        }
    }

    /**
     * Runs the complete tool-turn loop after initial stream parsing detected tool
     * calls.
     * <p>
     * Per round:
     * <ol>
     *   <li>Append assistant tool request + tool response messages.</li>
     *   <li>Call the chat completion endpoint again with updated messages.</li>
     *   <li>Forward streamed lines to the client while capturing potential next
     *   tool calls.</li>
     *   <li>Stop when no more tool calls are requested or the round cap is hit.</li>
     * </ol>
     *
     * @param out output stream to the client (SSE style lines)
     * @param llm4Chat selected model configuration
     * @param originalBody original request body template
     * @param messages mutable conversation messages from original request
     * @param assistantContent already streamed assistant text from current round
     * @param toolCalls collected tool calls from current round
     * @throws IOException when network I/O or JSON processing fails
     */
    public static void handleToolCallsAndContinue(ServletOutputStream out, LLM.LLMModel llm4Chat, JSONObject originalBody, JSONArray messages, String assistantContent, Map<Integer, ToolCall> toolCalls) throws IOException {
        try {
            // Work on a copy so caller-owned message arrays are not modified unexpectedly.
            JSONArray newMessages = new JSONArray();
            for (int i = 0; i < messages.length(); i++) {
                newMessages.put(messages.get(i));
            }
            String roundAssistantContent = assistantContent == null ? "" : assistantContent;
            Map<Integer, ToolCall> roundToolCalls = new HashMap<>(toolCalls);
            // Track total calls per tool across this whole tool-turn lifecycle.
            Map<String, Integer> toolCallCounters = new HashMap<>();

            for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                // Convert captured tool calls into assistant/tool messages and execute tools.
                ToolRoundData roundData = appendAssistantAndToolMessages(newMessages, roundAssistantContent, roundToolCalls, toolCallCounters);
                if (roundData == null || roundData.toolResults.length() == 0) {
                    // No executable tool calls; terminate stream cleanly.
                    out.println("data: [DONE]");
                    out.flush();
                    return;
                }

                // Build follow-up completion request from original body template.
                final JSONObject followup = prepareToolRequestBody(originalBody, true);
                followup.put("messages", newMessages);
                final HttpURLConnection followConn = openChatCompletionConnection(llm4Chat, followup);
                if (followConn.getResponseCode() != 200) {
                    // Upstream error: close downstream stream instead of sending broken chunks.
                    out.println("data: [DONE]");
                    out.flush();
                    followConn.disconnect();
                    return;
                }

                // Collect data for the next potential tool round while forwarding this stream.
                final StringBuilder nextAssistantContent = new StringBuilder();
                final Map<Integer, ToolCall> nextToolCalls = new HashMap<>();
                final boolean[] sawToolCalls = new boolean[]{false};

                try (BufferedReader in = new BufferedReader(new InputStreamReader(followConn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    boolean injectedToolResults = false;
                    while ((line = in.readLine()) != null) {
                        // Inject tool metadata once into outgoing stream so UI can show executed tools.
                        if (!injectedToolResults && roundData.toolResults.length() > 0) {
                            line = injectToolMetadata(line, roundData);
                            if (line != null && line.indexOf("\"tool-results\"") >= 0) {
                                injectedToolResults = true;
                            }
                        }
                        boolean isDoneLine = "data: [DONE]".equals(line.trim()) || "[DONE]".equals(line.trim());
                        if (line.startsWith("data:")) {
                            int p = line.indexOf('{');
                            if (p > 0) {
                                try {
                                    JSONObject j = new JSONObject(new JSONTokener(line.substring(p)));
                                    // Parse streamed deltas to capture possible next tool round.
                                    captureToolCalls(j, nextToolCalls, nextAssistantContent, sawToolCalls);
                                } catch (JSONException e) {
                                    // ignore malformed stream chunks and continue forwarding stream
                                }
                            }
                        }
                        // Hide interim [DONE] if another tool round is expected.
                        if (!isDoneLine || !sawToolCalls[0]) {
                            out.println(line);
                            out.flush();
                        }
                    }
                } finally {
                    followConn.disconnect();
                }

                // Ignore phantom rounds where stream signaled tool-calls but no concrete calls were parsed.
                if (nextToolCalls.isEmpty()) sawToolCalls[0] = false;
                // Final answer reached; caller already received forwarded stream lines.
                if (!sawToolCalls[0]) return;

                // Continue with next round tool request that was found in follow-up stream.
                roundAssistantContent = nextAssistantContent.toString();
                roundToolCalls = nextToolCalls;
            }
            // Safety fallback when round cap is hit.
            out.println("data: [DONE]");
            out.flush();
        } catch (JSONException e) {
            throw new IOException("JSON processing error in tool handling", e);
        }
    }

    /**
     * Opens and sends one chat completion request.
     *
     * @param llm4Chat model config used for endpoint and auth
     * @param requestBody request payload
     * @return opened connection after request body has been sent
     * @throws IOException on URI/network/write failures
     */
    private static HttpURLConnection openChatCompletionConnection(LLM.LLMModel llm4Chat, JSONObject requestBody) throws IOException {
        final URL url;
        try {
            url = new URI(llm4Chat.llm.hoststub + "/v1/chat/completions").toURL();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid chat completion URL: " + e.getMessage(), e);
        }
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (!llm4Chat.llm.api_key.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + llm4Chat.llm.api_key);
        }
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        return conn;
    }

    /**
     * Merges incremental tool call fragments from a streaming delta into the
     * in-progress tool call map.
     * <p>
     * Streaming responses may deliver one tool call over multiple chunks. This method
     * merges fields by tool-call index:
     * <ul>
     *   <li>{@code id}, {@code type}, and function {@code name} keep the latest non-empty value.</li>
     *   <li>function {@code arguments} are appended because they are often streamed in pieces.</li>
     * </ul>
     *
     * @param toolCalls current merged tool calls, keyed by call index
     * @param deltaCalls new delta chunk containing partial tool call objects
     */
    private static void mergeToolCalls(Map<Integer, ToolCall> toolCalls, JSONArray deltaCalls) {
        // Each array element can contain only a fragment of the final tool call.
        for (int i = 0; i < deltaCalls.length(); i++) {
            JSONObject delta = deltaCalls.optJSONObject(i);
            if (delta == null) continue;
            Integer index = readToolCallIndex(delta);
            // Ignore chunks without a valid non-negative call index.
            if (index == null || index.intValue() < 0) continue;

            // Reuse existing partial state for this index or start a new one.
            ToolCall call = toolCalls.getOrDefault(index, new ToolCall());

            // Scalar fields are replaced when a newer non-empty value arrives.
            String id = delta.optString("id", null);
            if (id != null && !id.isEmpty()) call.id = id;
            String type = delta.optString("type", null);
            if (type != null && !type.isEmpty()) call.type = type;
            JSONObject fn = delta.optJSONObject("function");
            if (fn != null) {
                String name = fn.optString("name", null);
                if (name != null && !name.isEmpty()) call.name = name;
                Object rawArgs = fn.opt("arguments");
                if (rawArgs instanceof String) {
                    String args = (String) rawArgs;
                    // Arguments are streamed as string fragments and must be concatenated.
                    if (!args.isEmpty()) call.arguments = (call.arguments == null ? "" : call.arguments) + args;
                }
            }
            toolCalls.put(index, call);
        }
    }

    /**
     * Appends one assistant message containing tool_calls plus corresponding tool
     * role messages with execution results.
     * <p>
     * Only executable tool calls are included (must have a function name and valid
     * JSON arguments if arguments are present).
     *
     * @param messages mutable conversation message array to append to
     * @param assistantContent assistant text that accompanied tool call emission
     * @param toolCalls merged tool calls keyed by index
     * @param toolCallCounters cumulative per-tool call counters for this turn
     * @return round data containing emitted {@code tool_calls} and tool execution
     *         results, or {@code null} when JSON assembly fails
     */
    private static ToolRoundData appendAssistantAndToolMessages(JSONArray messages, String assistantContent, Map<Integer, ToolCall> toolCalls, Map<String, Integer> toolCallCounters) {
        try {
            // Keep original model call order stable by sorting call indices.
            List<Integer> indices = new ArrayList<>(toolCalls.keySet());
            Collections.sort(indices);
            JSONArray toolCallsArray = new JSONArray();
            JSONArray toolMessages = new JSONArray();
            JSONArray toolResults = new JSONArray();
            for (Integer idx : indices) {
                ToolCall call = toolCalls.get(idx);
                if (call == null) continue;
                // Ensure required defaults for follow-up protocol compliance.
                if (call.id == null || call.id.isEmpty()) call.id = "toolcall_" + idx + "_" + System.currentTimeMillis();
                if (call.type == null || call.type.isEmpty()) call.type = "function";
                if (!isExecutableToolCall(call)) continue;
                // Enforce per-tool max calls for the complete tool turn lifecycle.
                final String toolName = call.name == null ? "" : call.name.trim();
                final int maxCalls = net.yacy.ai.ToolProvider.maxCallsPerTurn(toolName);
                final int usedCalls = toolCallCounters.getOrDefault(toolName, Integer.valueOf(0)).intValue();
                if (usedCalls >= maxCalls) continue;

                // Assistant message representation of requested tool call.
                JSONObject toolCallJson = new JSONObject(true);
                toolCallJson.put("id", call.id);
                toolCallJson.put("type", call.type);
                JSONObject fn = new JSONObject(true);
                fn.put("name", call.name == null ? "" : call.name);
                fn.put("arguments", call.arguments == null ? "" : call.arguments);
                toolCallJson.put("function", fn);
                toolCallsArray.put(toolCallJson);

                // Execute tool locally and create "tool" role response message.
                String result = net.yacy.ai.ToolProvider.executeTool(call.name, call.arguments);
                JSONObject toolMessage = new JSONObject(true);
                toolMessage.put("role", "tool");
                toolMessage.put("tool_call_id", call.id);
                toolMessage.put("name", call.name == null ? "" : call.name);
                toolMessage.put("content", result);
                toolMessages.put(toolMessage);
                toolCallCounters.put(toolName, Integer.valueOf(usedCalls + 1));

                // Separate compact result list for stream metadata injection.
                JSONObject toolResult = new JSONObject(true);
                toolResult.put("tool_call_id", call.id);
                toolResult.put("name", call.name == null ? "" : call.name);
                toolResult.put("content", result);
                toolResults.put(toolResult);
            }

            // If nothing is executable, leave the message history unchanged.
            if (toolCallsArray.length() == 0) {
                return new ToolRoundData(new JSONArray(), new JSONArray());
            }

            // Add assistant turn that requested tools.
            JSONObject assistantMessage = new JSONObject(true);
            assistantMessage.put("role", "assistant");
            assistantMessage.put("content", assistantContent == null ? "" : assistantContent);
            assistantMessage.put("tool_calls", toolCallsArray);
            messages.put(assistantMessage);
            // Follow with synthetic tool result messages for next model round context.
            for (int i = 0; i < toolMessages.length(); i++) messages.put(toolMessages.get(i));

            return new ToolRoundData(toolCallsArray, toolResults);
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Injects tool metadata into one outbound SSE line if the line contains JSON
     * data.
     * <p>
     * Metadata keys:
     * <ul>
     *   <li>{@code tool-calls}: normalized tool call objects sent in the round.</li>
     *   <li>{@code tool-results}: local tool execution outputs.</li>
     * </ul>
     *
     * @param line outbound line (typically prefixed with {@code data:})
     * @param roundData round metadata to inject
     * @return original or augmented line
     */
    private static String injectToolMetadata(String line, ToolRoundData roundData) {
        if (line == null || roundData == null || roundData.toolResults == null || roundData.toolResults.length() == 0) return line;
        if (!line.startsWith("data:")) return line;
        int p = line.indexOf('{');
        if (p <= 0) return line;
        try {
            JSONObject j = new JSONObject(new JSONTokener(line.substring(p)));
            // Do not overwrite if upstream already provided these fields.
            if (!j.has("tool-calls") && roundData.toolCalls != null) j.put("tool-calls", roundData.toolCalls);
            if (!j.has("tool-results")) j.put("tool-results", roundData.toolResults);
            return line.substring(0, p) + j.toString();
        } catch (JSONException e) {
            // Non-JSON data lines are forwarded unchanged.
            return line;
        }
    }

    /**
     * Injects arbitrary JSON metadata into one SSE {@code data: ...} JSON line.
     * Existing keys are preserved.
     *
     * @param line outbound stream line
     * @param metadata metadata object to inject
     * @return augmented line, or {@code null} when the line is not a JSON data line
     */
    private static String injectJsonMetadata(String line, JSONObject metadata) {
        if (line == null || metadata == null || metadata.length() == 0) return null;
        if (!line.startsWith("data:")) return null;
        int p = line.indexOf('{');
        if (p <= 0) return null;
        try {
            JSONObject j = new JSONObject(new JSONTokener(line.substring(p)));
            JSONArray names = metadata.names();
            if (names == null || names.length() == 0) return null;
            for (int i = 0; i < names.length(); i++) {
                String name = names.optString(i, null);
                if (name == null || name.isEmpty()) continue;
                if (!j.has(name)) j.put(name, metadata.get(name));
            }
            return line.substring(0, p) + j.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Reads the {@code index} field used to correlate streamed tool-call fragments.
     *
     * @param delta one tool-call delta object
     * @return index as Integer when present and numeric, otherwise {@code null}
     */
    private static Integer readToolCallIndex(JSONObject delta) {
        if (delta == null || !delta.has("index")) return null;
        Object raw = delta.opt("index");
        if (raw instanceof Number) return Integer.valueOf(((Number) raw).intValue());
        return null;
    }

    /**
     * Checks whether a merged tool call is executable locally.
     * <p>
     * Requirements:
     * <ul>
     *   <li>non-empty tool/function name</li>
     *   <li>arguments omitted/empty or valid JSON object text</li>
     * </ul>
     *
     * @param call merged tool call candidate
     * @return true when tool execution should be attempted
     */
    private static boolean isExecutableToolCall(ToolCall call) {
        if (call == null || call.name == null || call.name.trim().isEmpty()) return false;
        String args = call.arguments;
        if (args == null || args.isEmpty()) return true;
        try {
            new JSONObject(args);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    /**
     * Round-local metadata container used during tool loop execution.
     */
    private static final class ToolRoundData {
        /** Tool calls that were executed in the round. */
        final JSONArray toolCalls;
        /** Tool outputs corresponding to {@link #toolCalls}. */
        final JSONArray toolResults;

        /**
         * Builds a round data object with non-null arrays.
         *
         * @param toolCalls tool call descriptors
         * @param toolResults tool execution outputs
         */
        ToolRoundData(JSONArray toolCalls, JSONArray toolResults) {
            this.toolCalls = toolCalls == null ? new JSONArray() : toolCalls;
            this.toolResults = toolResults == null ? new JSONArray() : toolResults;
        }
    }

    /**
     * Mutable tool-call assembly object built from streamed deltas.
     * <p>
     * Fields may be populated incrementally over multiple chunks.
     */
    public static final class ToolCall {
        /** Tool call id as emitted by model/provider. */
        public String id;
        /** Tool call type, usually {@code function}. */
        public String type;
        /** Tool/function name. */
        public String name;
        /** JSON argument text (can be assembled from fragments). */
        public String arguments;
    }
}
