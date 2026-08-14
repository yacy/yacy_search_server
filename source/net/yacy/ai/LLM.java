/**
 *  LLM
 *  Copyright 2024 by Michael Peter Christen
 *  First released 17.05.2024 at https://yacy.net
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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.LogRedaction;
import net.yacy.search.Switchboard;

public class LLM {

    private static final ConcurrentLog log = new ConcurrentLog("LLM");
    private static final String MODEL_CAPABILITIES_CONFIG = "ai.model_capabilities";
    /** config key: JSON object mapping a service hoststub to its context window (num_ctx) */
    public static final String SERVICE_NUM_CTX_CONFIG = "ai.service_num_ctx";
    private static String[] STOPTOKENS = new String[]{"[/INST]", "<|im_end|>", "<|end_of_turn|>", "<|eot_id|>", "<|end_header_id|>", "<EOS_TOKEN>", "</s>", "<|end|>"};

    public static enum LLMType {
        OPENAI("https://api.openai.com"),
        OLLAMA("http://localhost:11434"),
        LMSTUDIO("http://localhost:1234"),
        OPENROUTER("https://openrouter.ai/api"),
        OTHER(null);
        public String hoststub;
        private LLMType(String hoststub) {
            this.hoststub = hoststub;
        }
    }
    
    public static enum LLMUsage {
        search,
        chat,
        translation,
        classification,
        query,
        qapairs,
        tldr,
        logreport
    }
    
    public static class LLMModel {
        public LLM llm;
        public String model;
        public boolean tooling;
        public boolean thinking;
        public LLMModel(LLM llm, String model, boolean tooling, boolean thinking) {
            this.llm = llm;
            this.model = model;
            this.tooling = tooling;
            this.thinking = thinking;
        }
    }
    
    /** Ollama's out-of-the-box context window; used when a service has no num_ctx configured. */
    public static final int DEFAULT_NUM_CTX = 4096;
    /**
     * Sole fallback for a model's max_tokens when a config row is missing the field
     * (legacy/malformed rows only). The Production Models Matrix is the place where
     * max_tokens is defined; this must match the matrix UI default (DEFAULT_MAX_TOKENS
     * in LLMSelection_p.html) so no divergent default exists.
     */
    public static final int DEFAULT_MAX_TOKENS = 2048;

    public final String hoststub;
    public final String api_key;
    public final int max_tokens; // output-token cap (OpenAI max_tokens = Ollama num_predict)
    public final int num_ctx;    // context window of the inference service (per-service, advisory)
    public final LLMType type;

    public LLM(final String hoststub, final String api_key, final int max_tokens, final LLMType type) {
        this(hoststub, api_key, max_tokens, DEFAULT_NUM_CTX, type);
    }

    public LLM(final String hoststub, final String api_key, final int max_tokens, final int num_ctx, final LLMType type) {
        this.hoststub = hoststub.endsWith("/") ? hoststub.substring(0, hoststub.length() - 1) : hoststub;
        this.api_key = api_key == null ? "" : api_key;
        this.max_tokens = max_tokens <= 0 ? DEFAULT_MAX_TOKENS : max_tokens;
        this.num_ctx = num_ctx <= 0 ? DEFAULT_NUM_CTX : num_ctx;
        this.type = type;
    }
    
    /**
     * The following function picks up the right model that was configured in the LLMSelection.
     * @param llmUsage
     * @return
     */
    public static LLMModel llmFromUsage(LLMUsage llmUsage) {
        return llmFromUsage(llmUsage, null, null);
    }

    public static LLMModel llmFromUsage(final LLMUsage llmUsage, final String runId, final String caller) {
        return llmFromUsage(llmUsage, runId, caller, true);
    }

    public static LLMModel llmFromUsageQuiet(final LLMUsage llmUsage) {
        return llmFromUsage(llmUsage, null, null, false);
    }

    private static LLMModel llmFromUsage(final LLMUsage llmUsage, final String runId, final String caller, final boolean logRouting) {
        final long start = System.currentTimeMillis();
        final Switchboard sb = Switchboard.getSwitchboard();
        if (sb == null) {
            if (logRouting) log.warn(routePrefix(runId, caller) + "event=model-routing phase=fail usage=" + llmUsage + " reason=switchboard-unavailable durationMs=" + elapsed(start));
            return null;
        }
        final String pms = sb.getConfig("ai.production_models", "[]");
        JSONObject model_capabilities = readModelCapabilities();
        try {
            JSONArray production_models = new JSONArray(new JSONTokener(pms));
            // got through all the selected models to find which one has the wanted usage flag switched on
            for (int i = 0; i < production_models.length(); i++) {
                JSONObject row = production_models.getJSONObject(i);
                boolean switched_on = row.optBoolean(llmUsage.name(), false);
                if (switched_on) {
                    // found one that shall be used for this use case
                    final String hoststub = row.optString("hoststub", "");
                    final String api_key = row.optString("api_key", "");
                    final int max_tokens = Integer.parseInt(row.optString("max_tokens", String.valueOf(DEFAULT_MAX_TOKENS)));
                    final String model = row.optString("model", "");
                    final LLMType type = LLMType.valueOf(row.optString("service", "OLLAMA"));
                    boolean tooling = row.optBoolean("tooling", false);
                    boolean thinking = row.optBoolean("thinking", false);
                    if (!tooling || !thinking) {
                        final JSONObject capabilityEntry = model_capabilities.optJSONObject(capabilityKey(type, hoststub, model));
                        if (capabilityEntry != null) {
                            if (!tooling) tooling = "supported".equals(capabilityEntry.optString("tooling", ""));
                            if (!thinking) thinking = "supported".equals(capabilityEntry.optString("thinking", ""));
                        }
                    }
                    final int num_ctx = serviceNumCtx(sb, hoststub);
                    LLM llm = new LLM(hoststub, api_key, max_tokens, num_ctx, type);
                    LLMModel llmmodel = new LLMModel(llm, model, tooling, thinking);
                    if (logRouting) {
                        log.info(routePrefix(runId, caller) + "event=model-routing phase=select usage=" + llmUsage + " row=" + i + " service=" + type.name() + " model=" + LogRedaction.redact(model) + " backend=" + LogRedaction.redact(llm.hoststub) + " maxTokens=" + llm.max_tokens + " numCtx=" + llm.num_ctx + " tooling=" + tooling + " thinking=" + thinking + " productionRows=" + production_models.length() + " durationMs=" + elapsed(start));
                    }
                    return llmmodel;
                }
            }
            if (logRouting) {
                log.info(routePrefix(runId, caller) + "event=model-routing phase=miss usage=" + llmUsage + " productionRows=" + production_models.length() + " durationMs=" + elapsed(start));
            }
        } catch (JSONException | IllegalArgumentException e) {
            if (logRouting) {
                log.warn(routePrefix(runId, caller) + "event=model-routing phase=fail usage=" + llmUsage + " errorClass=" + e.getClass().getName() + " reason=" + LogRedaction.redactMessage(e) + " durationMs=" + elapsed(start));
            }
        }
        // so if we don't find a model for that specific usage, we purposely return null to show that there is a missing configuration
        return null;
    }    

    private static String routePrefix(final String runId, final String caller) {
        final StringBuilder prefix = new StringBuilder();
        if (runId != null && !runId.isEmpty()) prefix.append("runId=").append(runId).append(' ');
        if (caller != null && !caller.isEmpty()) prefix.append("caller=").append(caller).append(' ');
        return prefix.toString();
    }

    private static long elapsed(final long start) {
        return System.currentTimeMillis() - start;
    }
    
    public String getHoststub() {
		return this.hoststub;
	}

    public static String capabilityKey(final LLMType type, final String hoststub, final String model) {
        final String normalizedType = type == null ? "" : type.name();
        final String normalizedHoststub = hoststub == null ? "" : hoststub.replaceAll("/+$", "");
        final String normalizedModel = model == null ? "" : model.trim();
        return normalizedType + "|" + normalizedHoststub + "|" + normalizedModel;
    }

    /** Normalize a hoststub for use as a service key: trim and drop trailing slashes. */
    public static String normalizeHoststub(final String hoststub) {
        if (hoststub == null) return "";
        return hoststub.trim().replaceAll("/+$", "");
    }

    /**
     * Context window (num_ctx) configured for the inference service at the given hoststub.
     * num_ctx is a per-service setting (a self-hosted server exposes one context length for
     * all its models via OLLAMA_CONTEXT_LENGTH), stored under SERVICE_NUM_CTX_CONFIG keyed by
     * hoststub. The value is advisory: YaCy uses it to budget the prompt against the window,
     * it does not enforce it on the backend. Falls back to DEFAULT_NUM_CTX when unset.
     */
    public static int serviceNumCtx(final Switchboard sb, final String hoststub) {
        if (sb == null) return DEFAULT_NUM_CTX;
        final String json = sb.getConfig(SERVICE_NUM_CTX_CONFIG, "{}");
        try {
            final JSONObject map = new JSONObject(new JSONTokener(json));
            final int value = map.optInt(normalizeHoststub(hoststub), 0);
            return value <= 0 ? DEFAULT_NUM_CTX : value;
        } catch (final JSONException e) {
            return DEFAULT_NUM_CTX;
        }
    }

    private static JSONObject readModelCapabilities() {
        final Switchboard sb = Switchboard.getSwitchboard();
        if (sb == null) return new JSONObject(true);
        final String capabilitiesJson = sb.getConfig(MODEL_CAPABILITIES_CONFIG, "{}");
        try {
            return new JSONObject(new JSONTokener(capabilitiesJson));
        } catch (JSONException e) {
            return new JSONObject(true);
        }
    }

    public static boolean isCapabilitySupported(final LLMType type, final String hoststub, final String model, final String capabilityName) {
        if (capabilityName == null || capabilityName.isEmpty()) return false;
        final JSONObject modelCapabilities = readModelCapabilities();
        final JSONObject capabilityEntry = modelCapabilities.optJSONObject(capabilityKey(type, hoststub, model));
        return capabilityEntry != null && "supported".equalsIgnoreCase(capabilityEntry.optString(capabilityName, ""));
    }

    public static void applyNoThinkingParameters(final JSONObject data) {
        if (data == null) return;
        try {
            data.put("reasoning_effort", "none");
            data.put("enable_thinking", false);
        } catch (JSONException e) {
        }
    }

    // API Helper Methods

    private static String sendPostRequest(final String urls, final JSONObject data, final String apiKey) throws IOException, URISyntaxException {
        final URL url = new URI(urls).toURL();
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        // Batch calls like the log report generation run large models on CPU and may
        // take many minutes for a single response. Nothing on our side is allowed to
        // abort such a call: connecting must fail fast, but reading must never time out.
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(0);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            final byte[] input = data.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        final int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                final StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
        } else {
            throw new IOException("Request failed with response code " + responseCode);
        }
    }

    private static String sendGetRequest(final String urls) throws IOException, URISyntaxException {
        final URL url = new URI(urls).toURL();
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        final int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                final StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
        } else {
            throw new IOException("Request failed with response code " + responseCode);
        }
    }
    
    
    public LinkedHashMap<String, Long> listOllamaModels() {
        final LinkedHashMap<String, Long> sortedMap = new LinkedHashMap<>();
        try {
            final String response = sendGetRequest(this.hoststub + "/api/tags");
            final JSONObject responseObject = new JSONObject(response);
            final JSONArray models = responseObject.getJSONArray("models");

            final List<Map.Entry<String, Long>> list = new ArrayList<>();
            for (int i = 0; i < models.length(); i++) {
                final JSONObject model = models.getJSONObject(i);
                final String name = model.optString("name", "");
                final long size = model.optLong("size", 0);
                list.add(new AbstractMap.SimpleEntry<>(name, size));
            }

            // Sort the list in descending order based on the values
            list.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));

            // Create a new LinkedHashMap and add the sorted entries
            for (final Map.Entry<String, Long> entry : list) {
                sortedMap.put(entry.getKey(), entry.getValue());
            }
        } catch (JSONException | URISyntaxException | IOException e) {
            e.printStackTrace();
        }
        return sortedMap;
    }

    public boolean ollamaModelExists(final String name) {
        final JSONObject data = new JSONObject();
        try {
            data.put("name", name);
            sendPostRequest(this.hoststub + "/api/show", data, this.api_key);
            return true;
        } catch (JSONException | URISyntaxException | IOException e) {
            return false;
        }
    }

    public boolean pullOllamaModel(final String name) {
        final JSONObject data = new JSONObject();
        try {
            data.put("name", name);
            data.put("stream", false);
            final String response = sendPostRequest(this.hoststub + "/api/pull", data, this.api_key);
            // this sends {"status": "success"} in case of success
            final JSONObject responseObject = new JSONObject(response);
            final String status = responseObject.optString("status", "");
            return status.equals("success");
        } catch (JSONException | URISyntaxException | IOException e) {
            return false;
        }
    }
    
    // chat endpoints
    
    public static class Context extends JSONArray {
        public Context(String systemPrompt) throws JSONException {
            super();
            final JSONObject systemPromptObject = new JSONObject(true);
            systemPromptObject.put("role", "system");
            systemPromptObject.put("content", systemPrompt);
            this.put(systemPromptObject);
        }
        public void addDialog(String user, String assistant) throws JSONException {
            final JSONObject userPromptObject = new JSONObject(true);
            userPromptObject.put("role", "user");
            userPromptObject.put("content", user);
            this.put(userPromptObject);
            final JSONObject assistantPromptObject = new JSONObject(true);
            assistantPromptObject.put("role", "assistant");
            assistantPromptObject.put("content", assistant);
            this.put(assistantPromptObject);
        }
        public void addPrompt(String userPrompt) throws JSONException {
            final JSONObject userPromptObject = new JSONObject(true);
            userPromptObject.put("role", "user");
            userPromptObject.put("content", userPrompt);
            this.put(userPromptObject);
        }
    }

    // OpenAI chat client, works with llama.cpp and Ollama
    public String chat(final String model, final Context context, JSONObject schema, final int max_tokens) throws IOException {
        final JSONObject data = new JSONObject();
        
        try {
            data.put("model", model);
            data.put("temperature", 0.1);
            data.put("max_tokens", max_tokens);
            // Best-effort hint for Ollama's context window (num_ctx), taken from the
            // per-service configuration. Ollama's OpenAI-compatible endpoint does not read
            // this today and pure-OpenAI backends ignore unknown fields, so it is a harmless
            // forward-looking hedge; the reliable way to raise the context window remains
            // OLLAMA_CONTEXT_LENGTH or a Modelfile PARAMETER num_ctx.
            data.put("num_ctx", this.num_ctx);
            data.put("messages", context);
            data.put("stop", new JSONArray(STOPTOKENS));
            data.put("stream", false);
            applyNoThinkingParameters(data);

            if (schema != null) {
                System.out.println(schema.toString());
                JSONObject json_schema = new JSONObject(true);
                json_schema.put("strict", true);
                json_schema.put("schema", schema);
                JSONObject response_format = new JSONObject();
                response_format.put("type", "json_schema");
                response_format.put("json_schema", json_schema);            
                data.put("response_format", response_format);
            }
            
            final String response = sendPostRequest(this.hoststub + "/v1/chat/completions", data, this.api_key);
            final JSONObject responseObject = new JSONObject(response);
            final JSONArray choices = responseObject.getJSONArray("choices");
            final JSONObject choice = choices.getJSONObject(0);
            final JSONObject message = choice.getJSONObject("message");
            final String content = message.optString("content", "");
            // A truncated answer is not an abort on our side but a generation limit;
            // make the cause visible because the caller only sees a fragment.
            final String finishReason = choice.optString("finish_reason", "");
            if ("length".equals(finishReason)) {
                log.warn("chat response was truncated by the max_tokens limit (" + max_tokens
                        + "), model=" + LogRedaction.redact(model)
                        + ", contentChars=" + content.length()
                        + ". Configure a higher max_tokens for this model if complete outputs are required.");
            }
            return stripThinkBlocks(content);
        } catch (JSONException | URISyntaxException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Remove reasoning blocks from a chat answer. Some backends deliver the chain of
     * thought of thinking models inline as &lt;think&gt;...&lt;/think&gt; in the content;
     * a report or answer must only contain the text after the reasoning. An unclosed
     * think block (e.g. because the response was cut by the token limit) is removed
     * up to the end of the content.
     */
    protected static String stripThinkBlocks(final String content) {
        if (content == null || content.indexOf("<think>") < 0) return content;
        final String withoutClosed = content.replaceAll("(?s)<think>.*?</think>", "");
        final int unclosed = withoutClosed.indexOf("<think>");
        final String result = unclosed < 0 ? withoutClosed : withoutClosed.substring(0, unclosed);
        return result.trim();
    }
    
    public String chat(final String model, final String systemPrompt, final String userPrompt, final int max_tokens) throws IOException {
        try {
            Context context = new Context(systemPrompt);
            context.addPrompt(userPrompt);
            return chat(model, context, null, max_tokens);
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * OpenAI chat client like chat(), but with streaming: the model output is read
     * as server-sent events and every content delta is passed to the onDelta consumer
     * as soon as it arrives. This allows callers (i.e. the log report generator) to
     * show partially generated output live while the model is still working.
     * @param onDelta receives each content fragment in order; may be null
     * @return the complete concatenated model output
     */
    public String chatStream(final String model, final String systemPrompt, final String userPrompt, final int max_tokens, final java.util.function.Consumer<String> onDelta) throws IOException {
        final JSONObject data = new JSONObject();
        try {
            final Context context = new Context(systemPrompt);
            context.addPrompt(userPrompt);
            data.put("model", model);
            data.put("temperature", 0.1);
            data.put("max_tokens", max_tokens);
            // best-effort num_ctx hint from the per-service config, see chat(); harmless to non-Ollama backends
            data.put("num_ctx", this.num_ctx);
            data.put("messages", context);
            data.put("stop", new JSONArray(STOPTOKENS));
            data.put("stream", true);
            applyNoThinkingParameters(data);

            final URL url = new URI(this.hoststub + "/v1/chat/completions").toURL();
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "text/event-stream");
            if (this.api_key != null && !this.api_key.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + this.api_key);
            }
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                final byte[] input = data.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            final int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Request failed with response code " + responseCode);
            }
            final StringBuilder full = new StringBuilder();
            String finishReason = "";
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.startsWith("data:")) continue; // SSE frames only; keep-alive lines are skipped
                    final String payload = line.substring(5).trim();
                    if (payload.equals("[DONE]")) break;
                    final JSONObject event = new JSONObject(payload);
                    final JSONArray choices = event.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) continue;
                    final JSONObject choice = choices.getJSONObject(0);
                    final JSONObject delta = choice.optJSONObject("delta");
                    final String content = delta == null ? "" : delta.optString("content", "");
                    if (!content.isEmpty()) {
                        full.append(content);
                        if (onDelta != null) onDelta.accept(content);
                    }
                    // the terminal chunk carries the finish_reason; remember the last non-empty one
                    final String reason = choice.optString("finish_reason", "");
                    if (!reason.isEmpty()) finishReason = reason;
                }
            }
            // A truncated answer is not an abort on our side but a generation limit. Unlike
            // chat() the streaming path used to drop this signal, so a report that ends
            // mid-word (prompt fills the context window, leaving no room to generate) was
            // invisible in the logs; surface it explicitly here.
            if ("length".equals(finishReason)) {
                log.warn("chatStream response was truncated by the max_tokens limit (" + max_tokens
                        + "), model=" + LogRedaction.redact(model)
                        + ", contentChars=" + full.length()
                        + ". The prompt likely fills the context window, leaving no room to generate;"
                        + " reduce the prompt size or raise the model context/max_tokens.");
            }
            return full.toString();
        } catch (JSONException | URISyntaxException e) {
            throw new IOException(e.getMessage());
        }
    }
    
    public static String[] stringsFromChat(String chatanswer) throws JSONException {
        final List<String> list = new ArrayList<>();
        if (chatanswer == null || chatanswer.isEmpty()) return new String[0];

        try {
            extractStrings(new JSONArray(chatanswer), list);
        } catch (JSONException e) {
            // Some models return a truncated JSON array on token limit. Try salvage.
            final String trimmed = chatanswer.trim();
            final int lastArrayEnd = trimmed.lastIndexOf(']');
            if (lastArrayEnd > 0) {
                try {
                    extractStrings(new JSONArray(trimmed.substring(0, lastArrayEnd + 1)), list);
                } catch (JSONException ignored) {
                    // ignore and continue with lightweight quoted-string extraction
                }
            }
            if (list.isEmpty()) {
                list.addAll(extractQuotedStrings(trimmed));
            }
            if (list.isEmpty()) throw e;
        }

        String[] result = new String[list.size()];
        return list.toArray(result);
    }

    private static void extractStrings(final JSONArray ja, final List<String> list) {
        for (int i = 0; i < ja.length(); i++) {
            final Object item = ja.opt(i);
            if (item == null) continue;
            if (item instanceof String) {
                final String s = ((String) item).trim();
                if (!s.isEmpty()) list.add(s);
                continue;
            }
            if (item instanceof JSONObject) {
                final JSONObject jo = (JSONObject) item;
                final String answer = jo.optString("answer", null);
                if (answer != null) {
                    final String s = answer.trim();
                    if (!s.isEmpty()) list.add(s);
                    continue;
                }
                for (String key : jo.keySet()) {
                    final String value = jo.optString(key, null);
                    if (value == null) continue;
                    final String s = value.trim();
                    if (s.isEmpty()) continue;
                    list.add(s);
                    break;
                }
            }
        }
    }

    private static List<String> extractQuotedStrings(final String input) {
        final List<String> list = new ArrayList<>();
        if (input == null || input.isEmpty()) return list;
        boolean inString = false;
        boolean escaped = false;
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            if (!inString) {
                if (c == '"') {
                    inString = true;
                    sb.setLength(0);
                }
                continue;
            }
            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                final String s = sb.toString().trim();
                if (!s.isEmpty()) list.add(s);
                inString = false;
                continue;
            }
            sb.append(c);
        }
        return list;
    }
    
    public final static JSONObject listSchema = new JSONObject(Map.of(
        "title", "Answer List",
        "type", "array",
        "items", Map.of("type", "string")
    ));
    
    public static void main(final String[] args) {
        final LLM llm = new LLM(LLMType.OLLAMA.hoststub, null, 4069, LLMType.OLLAMA);

        final LinkedHashMap<String, Long> models = llm.listOllamaModels();
        System.out.println(models.toString());

        // check if model exists
        final String model = "qwen2.5:0.5b";
        if (llm.ollamaModelExists(model))
            System.out.println("model " + model + " exists");
        else
            System.out.println("model " + model + " does not exist");

        // pull a model
        final boolean success = llm.pullOllamaModel(model);
        System.out.println("pulled model: " + model + ": " + success);
        
        String response;
		try {
			response = llm.chat(model, "You are a helpful assistant.", "What is the capital of France?", 1000);
	        System.out.println("Chat response: " + response);
		} catch (IOException e) {
	
			e.printStackTrace();
		}

        // make chat completion with model
        String question = "Who invented the wheel?";
        try {
            final String answer = llm.chat(model, "Make short answers.", question, 200);
            System.out.println(answer);
        } catch (final IOException e) {
            e.printStackTrace();
        }

        // try the json parser from chat results
        question = "Make a list of four names from Star Wars movies. Use a JSON Array.";
        try {
            Context context = new Context("Make short answers");
            context.addPrompt(question);
            final String[] a = stringsFromChat(llm.chat(model, context, listSchema, 1000));
            for (String s : a) {
                System.out.println(s);
            }
        } catch (final IOException | JSONException e) {
            e.printStackTrace();
        }
    }
    
}
