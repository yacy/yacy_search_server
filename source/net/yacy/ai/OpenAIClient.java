/**
 *  OpenAIClient
 *  Copyright 2024 by Michael Peter Christen
 *  First released 17.05.2024 at http://yacy.net
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

    private String hoststub;
    
    public OpenAIClient(String hoststub) {
        this.hoststub = hoststub;
    }


    // API Helper Methods
    
    public static String sendPostRequest(String endpoint, JSONObject data) throws IOException, URISyntaxException {
        URL url = new URI(endpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = data.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
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

    public static String sendGetRequest(String endpoint) throws IOException, URISyntaxException {
        URL url = new URI(endpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
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
    
    public String chat(String model, String prompt, int max_tokens) throws IOException {
        JSONObject data = new JSONObject();
        JSONArray messages = new JSONArray();
        JSONObject systemPrompt = new JSONObject(true);
        JSONObject userPrompt = new JSONObject(true);
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
            String response = sendPostRequest(this.hoststub + "/v1/chat/completions", data);
            JSONObject responseObject = new JSONObject(response);
            JSONArray choices = responseObject.getJSONArray("choices");
            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice.getJSONObject("message");
            String content = message.optString("content", "");
            return content;
        } catch (JSONException | URISyntaxException e) {
            throw new IOException(e.getMessage());
        }
    }

    public static String[] stringsFromChat(String answer) {
        int p = answer.indexOf('[');
        int q = answer.indexOf(']');
        if (p < 0 || q < 0 || q < p) return new String[0];
        try {
            JSONArray a = new JSONArray(answer.substring(p, q + 1));
            String[] arr = new String[a.length()];
            for (int i = 0; i < a.length(); i++) arr[i] = a.getString(i);
            return arr;
        } catch (JSONException e) {
            return new String[0];
        }
    }
    
    public static void main(String[] args) {
        String model = "phi3:3.8b";
        OpenAIClient oaic = new OpenAIClient(OllamaClient.OLLAMA_API_HOST);
        // make chat completion with model
        String question = "Who invented the wheel?";
        try {
            String answer = oaic.chat(model, question, 80);
            System.out.println(answer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // try the json parser from chat results
        question = "Make a list of four names from Star Wars movies. Use a JSON Array.";
        try {
            String[] a = stringsFromChat(oaic.chat(model, question, 80));
            for (String s: a) System.out.println(s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
