/**
 *  RAGProxyServlet
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

package net.yacy.http.servlets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionSchema;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.servlet.cache.Method;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class implements a Retrieval Augmented Generation ("RAG") proxy which uses a YaCy search index
 * to enrich a chat with search results. The  
 */
public class RAGProxyServlet extends HttpServlet {
	
    private static final long serialVersionUID = 3411544789759603107L;
    private static String[] STOPTOKENS = new String[]{"[/INST]", "<|im_end|>", "<|end_of_turn|>", "<|eot_id|>", "<|end_header_id|>", "<EOS_TOKEN>", "</s>", "<|end|>"};

	private static Boolean LLM_ENABLED = false;
    private static Boolean LLM_CONTROL_OLLAMA = true;
    private static Boolean LLM_ATTACH_QUERY = false; // instructs the proxy to attach the prompt generated to do the RAG search
    private static Boolean LLM_ATTACH_REFERENCES = false; // instructs the proxy to attach a list of sources that had been used in RAG
    private static String  LLM_LANGUAGE  = "en"; // used to select proper language in RAG augmentation
    private static String  LLM_SYSTEM_PREFIX  = "\n\nYou may receive additional expert knowledge in the user prompt after a 'Additional Information' headline to enhance your knowledge. Use it only if applicable.";
    private static String  LLM_USER_PREFIX  = "\n\nAdditional Information:\n\nbelow you find a collection of texts that might be useful to generate a response. Do not discuss these documents, just use them to answer the question above.\n\n";
	private static String  LLM_API_HOST  = "http://localhost:11434"; // Ollama port; install ollama from https://ollama.com/
    private static String  LLM_QUERY_MODEL = "phi3:3.8b";
	private static String  LLM_ANSWER_MODEL = "llama3:8b"; // or "phi3:3.8b" i.e. on a Raspberry Pi 5
	private static Boolean LLM_API_MODEL_OVERWRITING = true; // if true, the value configured in YaCy overwrites the client model
    private static String  LLM_API_KEY   = ""; // not required; option to use this class to use a OpenAI API
    
    @Override
    public void service(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        response.setContentType("application/json;charset=utf-8");
        
        HttpServletResponse hresponse = (HttpServletResponse) response;
		HttpServletRequest hrequest = (HttpServletRequest) request;

        // Add CORS headers
        hresponse.setHeader("Access-Control-Allow-Origin", "*");
        hresponse.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
        hresponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

		final Method reqMethod = Method.getMethod(hrequest.getMethod());
        if (reqMethod == Method.OTHER) {
        	// required to handle CORS
        	hresponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        
        // We expect a POST request
        if (reqMethod != Method.POST) {
        	hresponse.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        	return;
        }
        
        // get the output stream early to be able to generate messages to the user before the actual retrieval starts
        ServletOutputStream out = response.getOutputStream();
        
        // read the body of the request and parse it as JSON
        BufferedReader reader = request.getReader();
        StringBuilder bodyBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            bodyBuilder.append(line);
        }
        String body = bodyBuilder.toString();
        JSONObject bodyObject;
		try {
			// get system message and user prompt
			bodyObject = new JSONObject(body);
			String model = bodyObject.optString("model", LLM_ANSWER_MODEL); // we need a switch to allow overwriting
			JSONArray messages = bodyObject.optJSONArray("messages");
			JSONObject systemObject = messages.getJSONObject(0);
			String system = systemObject.optString("content", ""); // the system prompt
			JSONObject userObject = messages.getJSONObject(messages.length() - 1);
			String user = userObject.optString("content", ""); // this is the latest prompt

            // modify system and user prompt here in bodyObject to enable RAG
			String query = searchWordsForPrompt(LLM_QUERY_MODEL, user);
			out.print(responseLine("Searching for '" + query + "'\n\n").toString() + "\n"); out.flush();
			LinkedHashMap<String, String> searchResults = searchResults(query, 4);
			out.print(responseLine("Using the following sources for RAG:\n\n").toString() + "\n"); out.flush();
			for (String s: searchResults.keySet()) {out.print(responseLine("- `" + s + "`\n").toString() + "\n"); out.flush();}
			out.print(responseLine("\n").toString()); out.flush();
			system += LLM_SYSTEM_PREFIX;
			user += LLM_USER_PREFIX;
			for (String s: searchResults.values()) user += s + "\n\n";
			systemObject.put("content", system);
			userObject.put("content", user);
			
			if (LLM_API_MODEL_OVERWRITING) bodyObject.put("model", LLM_ANSWER_MODEL);
			
			// write back modified bodyMap to body
			body = bodyObject.toString();

			// Open request to back-end service
			URL url = new URI(LLM_API_HOST + "/v1/chat/completions").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (!LLM_API_KEY.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + LLM_API_KEY);
            }
            conn.setDoOutput(true);

            // write the body to back-end LLM
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }

            // write back response of the back-end service to the client; use status of backend-response
            int status = conn.getResponseCode();
            String rmessage = conn.getResponseMessage();
            hresponse.setStatus(status);
            
            if (status == 200) {
	            // read the response of the back-end line-by-line and write it to the client line-by-line
	            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            	String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    out.print(inputLine); // i.e. data: {"id":"chatcmpl-69","object":"chat.completion.chunk","created":1715908287,"model":"llama3:8b","system_fingerprint":"fp_ollama","choices":[{"index":0,"delta":{"role":"assistant","content":"ߘ"},"finish_reason":null}]}
                    out.flush();
                }
                in.close();
            }
            out.close(); // close this here to end transmission
		} catch (JSONException | URISyntaxException e) {
			throw new IOException(e.getMessage());
		}
    }

	private static JSONObject responseLine(String payload) {
		JSONObject j = new JSONObject(true);
		try {
			j.put("id", "log");
			j.put("object", "chat.completion.chunk");
			j.put("created", System.currentTimeMillis() / 1000);
			j.put("model", "log");
			j.put("system_fingerprint", "YaCy");
			JSONArray choices = new JSONArray();
			JSONObject choice = new JSONObject(true); // {"index":0,"delta":{"role":"assistant","content":"ߘ"
			choice.put("index", 0);
			JSONObject delta = new JSONObject(true);
			delta.put("role", "assistant");
			delta.put("content", payload);
			choice.put("delta", delta);
			choices.put(choice);
			j.put("choices", choices);
			//j.put("finish_reason", null); // this is problematic with the JSON library
		} catch (JSONException e) {}
		return j;
	}
	
	// API Helper Methods for Ollama
	
	private static String sendPostRequest(String endpoint, JSONObject data) throws IOException, URISyntaxException {
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

    private static String sendGetRequest(String endpoint) throws IOException, URISyntaxException {
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
    
	// OpenAI chat client, works also with llama.cpp and Ollama 
    
    public static String chat(String model, String prompt, int max_tokens) throws IOException {
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
	        String response = sendPostRequest(LLM_API_HOST + "/v1/chat/completions", data);
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
    
	private static String searchWordsForPrompt(String model, String prompt) {
		StringBuilder query = new StringBuilder();
		String question = "Make a list of a maximum of four search words for the following question; use a JSON Array: " + prompt;
		try {
		    String[] a = stringsFromChat(chat(model, question, 80));
		    for (String s: a) query.append(s).append(' ');
		    return query.toString().trim();
		} catch (IOException e) {
			e.printStackTrace();
			return "";
		}
	}
	
	private static LinkedHashMap<String, String> searchResults(String query, int count) {
		Switchboard sb = Switchboard.getSwitchboard();
		EmbeddedSolrConnector connector = sb.index.fulltext().getDefaultEmbeddedConnector();
		// construct query
        final SolrQuery params = new SolrQuery();
        params.setQuery(CollectionSchema.text_t.getSolrFieldName() + ":" + query);
        params.setRows(count);
        params.setStart(0);
        params.setFacet(false);
        params.clearSorts();
        params.setFields(CollectionSchema.sku.getSolrFieldName(), CollectionSchema.text_t.getSolrFieldName());
        params.setIncludeScore(false);
        params.set("df", CollectionSchema.text_t.getSolrFieldName());

        // query the server
        try {
        	final SolrDocumentList sdl = connector.getDocumentListByParams(params);
        	LinkedHashMap<String, String> a = new LinkedHashMap<String, String>();
        	Iterator<SolrDocument> i = sdl.iterator();
        	while (i.hasNext()) {
        		SolrDocument doc = i.next();
        		String url = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
        		String text = (String) doc.getFieldValue(CollectionSchema.text_t.getSolrFieldName());
        		a.put(url, text);
        	}
        	return a;
        } catch (SolrException | IOException e) {
        	return new LinkedHashMap<String, String>();
        }
	}
    
	// Ollama client functions

    public static LinkedHashMap<String, Long> listOllamaModels() {
        LinkedHashMap<String, Long> sortedMap = new LinkedHashMap<>();
        try {
	        String response = sendGetRequest(LLM_API_HOST + "/api/tags");
	        JSONObject responseObject = new JSONObject(response);
	        JSONArray models = responseObject.getJSONArray("models");
	        
		    List<Map.Entry<String, Long>> list = new ArrayList<>();
	        for (int i = 0; i < models.length(); i++) {
	        	JSONObject model = models.getJSONObject(i);
	        	String name = model.optString("name", "");
	        	long size = model.optLong("size", 0);
	        	list.add(new AbstractMap.SimpleEntry<String, Long>(name, size));
	        }

	        // Sort the list in descending order based on the values
	        list.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));
	        
	        // Create a new LinkedHashMap and add the sorted entries
	        for (Map.Entry<String, Long> entry : list) {
	        	sortedMap.put(entry.getKey(), entry.getValue());
	        }
        } catch (JSONException | URISyntaxException | IOException e) {
        	e.printStackTrace();
        }
        return sortedMap;
    }

    public static boolean ollamaModelExists(String name) {
        JSONObject data = new JSONObject();
        try {
	        data.put("name", name);
	        sendPostRequest(LLM_API_HOST + "/api/show", data);
	        return true;
        } catch (JSONException | URISyntaxException | IOException e) {
        	return false;
        }
    }

    public static boolean pullOllamaModel(String name) {
        JSONObject data = new JSONObject();
        try {
	        data.put("name", name);
	        data.put("stream", false);
	        String response = sendPostRequest(LLM_API_HOST + "/api/pull", data);
	        // this sends {"status": "success"} in case of success
	        JSONObject responseObject = new JSONObject(response);
	        String status = responseObject.optString("status", "");
	        return status.equals("success");
        } catch (JSONException | URISyntaxException | IOException e) {
        	return false;
        }
    }
    
    public static void main(String[] args) {
    	LinkedHashMap<String, Long> models = listOllamaModels();
		System.out.println(models.toString());

    	// check if model exists
    	//String model = "phi3:3.8b";
    	String model = "gemma:2b";
		if (ollamaModelExists(model))
			System.out.println("model " + model + " exists");
		else
			System.out.println("model " + model + " does not exist");
		
    	// pull a model
		boolean success = pullOllamaModel(model);
	    System.out.println("pulled model + " + model + ": " + success);
    	
    	// make chat completion with model
	    String question = "Who invented the wheel?";
		try {
		    String answer = chat(model, question, 80);
		    System.out.println(answer);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// try the json parser from chat results
		question = "Make a list of four names from Star Wars movies. Use a JSON Array.";
		try {
		    String[] a = stringsFromChat(chat(model, question, 80));
		    for (String s: a) System.out.println(s);
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
}
