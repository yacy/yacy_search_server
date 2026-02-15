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

import net.yacy.search.Switchboard;

public class LLM {

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
        tldr
    }
    
    public static class LLMModel {
        public LLM llm;
        public String model;
        public LLMModel(LLM llm, String model) {
            this.llm = llm;
            this.model = model;
        }
    }
    
    public final String hoststub;
    public final String api_key;
    public final int max_tokens; // the max_tokens as configured by the endpoint for all models
    public final LLMType type;
    
    public LLM(final String hoststub, final String api_key, final int max_tokens, final LLMType type) {
        this.hoststub = hoststub.endsWith("/") ? hoststub.substring(0, hoststub.length() - 1) : hoststub;
        this.api_key = api_key == null ? "" : api_key;
        this.max_tokens = max_tokens <= 0 ? 4096 : max_tokens;
        this.type = type;
    }
    
    /**
     * The following function picks up the right model that was configured in the LLMSelection.
     * @param llmUsage
     * @return
     */
    public static LLMModel llmFromUsage(LLMUsage llmUsage) {
        Switchboard sb = Switchboard.getSwitchboard();
        String pms = sb.getConfig("ai.production_models", "[]");
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
                    final int max_tokens = Integer.parseInt(row.optString("max_tokens", "4096"));
                    final String model = row.optString("model", "");
                    final LLMType type = LLMType.valueOf(row.optString("service", "OLLAMA"));
                    LLM llm = new LLM(hoststub, api_key, max_tokens, type);
                    LLMModel llmmodel = new LLMModel(llm, model);
                    return llmmodel;
                }
            }
        } catch (JSONException | NumberFormatException e) {
            e.printStackTrace();
        }
        // so if we don't find a model for that specific usage, we purposely return null to show that there is a missing configuration
        return null;
    }    
    
    public String getHoststub() {
		return this.hoststub;
	}


    // API Helper Methods

    private static String sendPostRequest(final String urls, final JSONObject data, final String apiKey) throws IOException, URISyntaxException {
        final URL url = new URI(urls).toURL();
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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
            data.put("messages", context);
            data.put("stop", new JSONArray(STOPTOKENS));
            data.put("stream", false);

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
            return content;
        } catch (JSONException | URISyntaxException e) {
            throw new IOException(e.getMessage());
        }
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
