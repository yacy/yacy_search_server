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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class OpenAIClient {

    private static String[] STOPTOKENS = new String[]{"[/INST]", "<|im_end|>", "<|end_of_turn|>", "<|eot_id|>", "<|end_header_id|>", "<EOS_TOKEN>", "</s>", "<|end|>"};

    protected final String hoststub;

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
    	JSONArray ja = new JSONArray(chatanswer);
    	List<String> list = new ArrayList<>();
    	// parse the JSON array and extract strings
    	for (int i = 0; i < ja.length(); i++) {
			Object item = ja.get(i);
			if (item instanceof String) {
				list.add((String) item);
			} else if (item instanceof JSONObject) {
				JSONObject jo = (JSONObject) item;
				String answer = jo.optString("answer", null);
				if (answer != null) {
					list.add(answer);
				} else {
					// take any string value from the object
					for (String key : jo.keySet()) {
						Object value = jo.optString(key, null);
						if (value != null && value instanceof String) {
							list.add((String) value);
							break; // take the first string found
						}
					}
				}
			}
		}
		// convert the list to an array
		String[] result = new String[list.size()];
		return list.toArray(result);    	
    }
    
    public final static JSONObject listSchema = new JSONObject(Map.of(
        "title", "Answer List",
        "type", "array",
        "properties", Map.of(
            "answer", Map.of("type", "string")
        ),
        "required", List.of("answer")
    ));

    public static void main(final String[] args) {
        final String model = "qwen2.5:0.5b";
        final OpenAIClient oaic = new OpenAIClient(OllamaClient.OLLAMA_API_HOST);
        // make chat completion with model
        String question = "Who invented the wheel?";
        try {
            final String answer = oaic.chat(model, "Make short answers.", question, 200);
            System.out.println(answer);
        } catch (final IOException e) {
            e.printStackTrace();
        }

        // try the json parser from chat results
        question = "Make a list of four names from Star Wars movies. Use a JSON Array.";
        try {
        	Context context = new Context("Make short answers");
        	context.addPrompt(question);
            final String[] a = stringsFromChat(oaic.chat(model, context, listSchema, 1000));
            for (String s : a) {
				System.out.println(s);
			}
        } catch (final IOException | JSONException e) {
            e.printStackTrace();
        }
    }

}
