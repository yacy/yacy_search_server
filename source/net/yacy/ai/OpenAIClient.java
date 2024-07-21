/**
 *  OpenAIClient
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class OpenAIClient {

    private static String[] STOPTOKENS = new String[]{"[/INST]", "<|im_end|>", "<|end_of_turn|>", "<|eot_id|>", "<|end_header_id|>", "<EOS_TOKEN>", "</s>", "<|end|>"};

    private final String hoststub;

    public OpenAIClient(final String hoststub) {
        this.hoststub = hoststub;
    }


    // API Helper Methods

    public static String sendPostRequest(final String endpoint, final JSONObject data) throws IOException, URISyntaxException {
        final URL url = new URI(endpoint).toURL();
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
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

    public static String sendGetRequest(final String endpoint) throws IOException, URISyntaxException {
        final URL url = new URI(endpoint).toURL();
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

    // OpenAI chat client, works with llama.cpp and Ollama

    public String chat(final String model, final String prompt, final int max_tokens) throws IOException {
        final JSONObject data = new JSONObject();
        final JSONArray messages = new JSONArray();
        final JSONObject systemPrompt = new JSONObject(true);
        final JSONObject userPrompt = new JSONObject(true);
        messages.put(systemPrompt);
        messages.put(userPrompt);
        try {
            systemPrompt.put("role", "system");
            systemPrompt.put("content", "Make short answers.");
            userPrompt.put("role", "user");
            userPrompt.put("content", prompt);
            data.put("model", model);
            data.put("temperature", 0.1);
            data.put("max_tokens", max_tokens);
            data.put("messages", messages);
            data.put("stop", new JSONArray(STOPTOKENS));
            data.put("stream", false);
            final String response = sendPostRequest(this.hoststub + "/v1/chat/completions", data);
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

    public static String[] stringsFromChat(final String answer) {
        final int p = answer.indexOf('[');
        final int q = answer.indexOf(']');
        if (p < 0 || q < 0 || q < p) return new String[0];
        try {
            final JSONArray a = new JSONArray(answer.substring(p, q + 1));
            final String[] arr = new String[a.length()];
            for (int i = 0; i < a.length(); i++) arr[i] = a.getString(i);
            return arr;
        } catch (final JSONException e) {
            return new String[0];
        }
    }

    public static void main(final String[] args) {
        final String model = "phi3:3.8b";
        final OpenAIClient oaic = new OpenAIClient(OllamaClient.OLLAMA_API_HOST);
        // make chat completion with model
        String question = "Who invented the wheel?";
        try {
            final String answer = oaic.chat(model, question, 80);
            System.out.println(answer);
        } catch (final IOException e) {
            e.printStackTrace();
        }

        // try the json parser from chat results
        question = "Make a list of four names from Star Wars movies. Use a JSON Array.";
        try {
            final String[] a = stringsFromChat(oaic.chat(model, question, 80));
            for (final String s: a) System.out.println(s);
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

}
