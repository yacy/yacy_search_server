/**
 *  RAGProxyServlet
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

package net.yacy.http.servlets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.servlet.cache.Method;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.LLM;
import net.yacy.cora.federate.solr.SolrType;
import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionSchema;

/**
 * This class implements a Retrieval Augmented Generation ("RAG") proxy which
 * uses a YaCy search index to enrich a chat with search results.
 * You can test this using a curl command:
 curl -X POST "http://localhost:8090/v1/chat/completions"\
     -s -H "Content-Type: application/json"\
     -d '{
    "model": "llama3.2:1b", "temperature": 0.1, "max_tokens": 1024,
    "messages": [
      {"role": "system", "content": "You are a helpful assistant."},
      {"role": "user", "content": "Hello, how are you?"}
    ],
    "stream": false
 }'
 */
public class RAGProxyServlet extends HttpServlet {

    private static final long serialVersionUID = 3411544789759643137L;

    private static String LLM_SYSTEM_PREFIX = "\n\nYou may receive additional expert knowledge in the user prompt after a 'Additional Information' headline to enhance your knowledge. Use it only if applicable.";
    private static String LLM_USER_PREFIX = "\n\nAdditional Information:\n\nbelow you find a collection of texts that might be useful to generate a response. Do not discuss these documents, just use them to answer the question above.\n\n";
    
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

        // get the output stream early to be able to generate messages to the user
        // before the actual retrieval starts
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
            // get chat functions
            String model = bodyObject.optString("model", LLM.LLMUsage.chat.name());
            //Double temperature = bodyObject.optDouble("temperature", 0.0);
            //int max_tokens = bodyObject.optInt("max_tokens", 1024);
            //boolean stream = bodyObject.optBoolean("stream", false);
            boolean rag = bodyObject.optBoolean("rag", false);

            // resolve true model name from configuration
            LLM.LLMUsage usage = LLM.LLMUsage.chat;
            try {usage = LLM.LLMUsage.valueOf(model);} catch (IllegalArgumentException e) {}
            LLM.LLMModel llm4Chat = LLM.llmFromUsage(usage);
            LLM.LLMModel llm4tldr = LLM.llmFromUsage(LLM.LLMUsage.tldr);
            bodyObject.put("model", llm4Chat.model); // replace the model with the decoded model name
            
            // get messages
            JSONArray messages = bodyObject.optJSONArray("messages");
            //JSONObject systemObject = messages.getJSONObject(0);
            //String system = systemObject.optString("content", ""); // the system prompt
            JSONObject userObject = messages.getJSONObject(messages.length() - 1);
            String user = userObject.optString("content", ""); // this is the latest prompt

            // RAG
            if (rag) {
                // modify system and user prompt here in bodyObject to enable RAG
                String query = this.searchWordsForPrompt(llm4tldr.llm, llm4tldr.model, user);
                String searchResultMarkdown = searchResultsAsMarkdown(query, 4);
                user += LLM_USER_PREFIX;
                user += searchResultMarkdown;
                userObject.put("content", user);
    
                // write back modified bodyMap to body
                body = bodyObject.toString();
            }

            // Open request to back-end service
            URL url = new URI(llm4Chat.llm.hoststub + "/v1/chat/completions").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (!llm4Chat.llm.api_key.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + llm4Chat.llm.api_key);
            }
            conn.setDoOutput(true);

            // write the body to back-end LLM
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            } // here we wait for the response from upstream

            // write back response of the back-end service to the client; use status of
            // backend-response
            final int status = conn.getResponseCode();
            // String rmessage = conn.getResponseMessage();
            hresponse.setStatus(status);

            if (status == 200) {
                // read the response of the back-end line-by-line and write it to the client line-by-line
                final BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
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

    public static JSONArray searchResults(String query, int count, final boolean includeSnippet) {
        final JSONArray results = new JSONArray();
        if (query == null || query.length() == 0 || count == 0) return results;
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
        params.setIncludeScore(true);
        params.set("df", CollectionSchema.text_t.getSolrFieldName());

        // query the server
        try {
            final SolrDocumentList sdl = connector.getDocumentListByParams(params);
            Iterator<SolrDocument> i = sdl.iterator();
            while (i.hasNext()) {
                try {
                    SolrDocument doc = i.next();
                    final JSONObject result = new JSONObject(true);
                    String url = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
                    result.put("url", url == null ? "" : url.trim());
                    String title = getOneString(doc, CollectionSchema.title);
                    result.put("title", title == null ? "" : title.trim());
                    if (includeSnippet) {
                        String text = (String) doc.getFieldValue(CollectionSchema.text_t.getSolrFieldName());
                        result.put("snippet", text == null ? "" : text.trim());
                    }
                results.put(result);
                } catch (JSONException e) {
                    // skip this result
                }
            }
            return results;
        } catch (SolrException | IOException e) {
            return results;
        }
    }
    
    public static String searchResultsAsMarkdown(String query, int count) {
        JSONArray searchResults = searchResults(query, count, true);
        StringBuilder sb = new StringBuilder();
        
        for (int i  = 0; i < searchResults.length(); i++) {
            try {
                JSONObject r = searchResults.getJSONObject(i);
                String title = r.optString("title", "");
                String url = r.optString("url", "");
                String snippet = r.optString("snippet", "");
                if (title.length() > 0 && snippet.length() > 0) {
                    sb.append("## ").append(title).append("\n");
                    sb.append(snippet).append("\n");
                    if (url.length() > 0) sb.append("Source: ").append(url).append("\n");
                    sb.append("\n\n");
                }
            } catch (JSONException e) {}
        }
        return sb.toString();
    }
    
    private static String getOneString(SolrDocument doc, CollectionSchema field) {
        assert field.isMultiValued();
        assert field.getType() == SolrType.string || field.getType() == SolrType.text_general;
        Object r = doc.getFieldValue(field.getSolrFieldName());
        if (r == null) return "";
        if (r instanceof ArrayList) {
            return ((ArrayList<String>) r).get(0);
        }
        return r.toString();
    }

    private String searchWordsForPrompt(LLM llm, String model, String prompt) {
        StringBuilder query = new StringBuilder();
        String question = "Make a list of a maximum of four search words for the following question; use a JSON Array: " + prompt;
        try {
            LLM.Context context = new LLM.Context(LLM_SYSTEM_PREFIX);
            context.addPrompt(question);
            String[] a = LLM.stringsFromChat(llm.chat(model, context, LLM.listSchema, 80));
            for (String s : a)
                query.append(s).append(' ');
            return query.toString().trim();
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return "";
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
            // j.put("finish_reason", null); // this is problematic with the JSON library
        } catch (JSONException e) {
        }
        return j;
    }

}
