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
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

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
import org.json.JSONTokener;

import net.yacy.ai.LLM;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.SolrType;
import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.QueryGoal;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.ranking.RankingProfile;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.snippet.TextSnippet;

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

    public static final String LLM_SYSTEM_PROMPT_DEFAULT = "You are a smart and helpful chatbot. If possible, use friendly emojies.";
    private static final String LLM_SYSTEM_PREFIX_DEFAULT = "\n\nYou may receive additional expert knowledge in the user prompt after a 'Additional Information' headline to enhance your knowledge. Use it only if applicable.";
    private static final String LLM_USER_PREFIX_DEFAULT = "\n\nAdditional Information:\n\nbelow you find a collection of texts that might be useful to generate a response. Do not discuss these documents, just use them to answer the question above.\n\n";
    private static final String LLM_QUERY_GENERATOR_PREFIX_DEFAULT = "Make a list of search words with low document frequency for the following prompt; use a JSON Array: ";

    // Volatile, in-memory access log for rate limiting. This is intentionally not persisted
    // to respect user privacy; entries older than 24h are purged on each access.
    public static final Deque<AbstractMap.SimpleEntry<Long, String>> ACCESS_LOG = new ConcurrentLinkedDeque<>();
    public static final long ONE_MINUTE_MS = 60_000L;
    public static final long ONE_HOUR_MS = 60 * ONE_MINUTE_MS;
    public static final long ONE_DAY_MS = 24 * ONE_HOUR_MS;

    @Override
    public void service(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        response.setContentType("application/json;charset=utf-8");

        HttpServletResponse hresponse = (HttpServletResponse) response;
        HttpServletRequest hrequest = (HttpServletRequest) request;

        // Add CORS headers
        hresponse.setHeader("Access-Control-Allow-Origin", "*");
        hresponse.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
        hresponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        final Switchboard sb = Switchboard.getSwitchboard();
        final String clientIP = hrequest.getRemoteAddr();
        final boolean localhostAccess = Domains.isLocalhost(clientIP);
        if (!localhostAccess) {
            // obey the allow-nonlocalhost shield setting
            final boolean allowNonLocal = sb.getConfigBool("ai.shield.allow-nonlocalhost", false);
            if (!allowNonLocal) {
                hresponse.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        if (isRateLimited(sb, clientIP, localhostAccess)) {
            hresponse.sendError(429, "Too Many Requests"); // standard status for rate limits
            return;
        }
        recordAccess(clientIP);

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

            // resolve true model name from configuration
            LLM.LLMUsage usage = LLM.LLMUsage.chat;
            try {usage = LLM.LLMUsage.valueOf(model);} catch (IllegalArgumentException e) {}
            LLM.LLMModel llm4Chat = LLM.llmFromUsage(usage);
            LLM.LLMModel llm4tldr = LLM.llmFromUsage(LLM.LLMUsage.tldr);
            bodyObject.put("model", llm4Chat.model); // replace the model with the decoded model name
            
            // get messages and prepare user message attachments
            JSONArray messages = bodyObject.optJSONArray("messages");
            final String systemPrefix = sb.getConfig("ai.llm-system-prefix", LLM_SYSTEM_PREFIX_DEFAULT);
            final String userPrefix = sb.getConfig("ai.llm-user-prefix", LLM_USER_PREFIX_DEFAULT);
            
            // debug
            //System.out.println(messages.toString());
            
            for (int i = 0; i < messages.length(); i++) {
                JSONObject message = messages.getJSONObject(i);
                if (message.optString("role", "").equals("user")) {
                    UserObject userObject = new UserObject(message);
                    userObject.attachAttachment(userPrefix);
                }
            }
            UserObject userObject = new UserObject(messages.getJSONObject(messages.length() - 1));
            String user = userObject.getContentText(); // this is the latest prompt
            final String userPrompt = user;
            String ragMode = userObject.getSearchMode();
            ConcurrentLog.info("RAGProxy", "ragMode=" + ragMode + " userChars=" + (user == null ? 0 : user.length()));
            //List<DataURL> data_urls = userObject.getContentAttachments(); // this list is a copy of the content data_urls
            
            // RAG
            String searchResultQuery = "";
            String searchResultMarkdown = "";
            if (!"no".equals(ragMode)) {
                // modify system and user prompt here in bodyObject to enable RAG
                final String queryPrefix = sb.getConfig("ai.llm-query-generator-prefix", LLM_QUERY_GENERATOR_PREFIX_DEFAULT);
                final long queryStart = System.currentTimeMillis();
                searchResultQuery = this.searchWordsForPrompt(llm4tldr.llm, llm4tldr.model, queryPrefix + user);
                final long queryElapsed = System.currentTimeMillis() - queryStart;
                final Set<String> boostTerms = intersectTokens(userPrompt, searchResultQuery, 8);
                final long searchStart = System.currentTimeMillis();
                searchResultMarkdown = searchResultsAsMarkdown(searchResultQuery, 10, "global".equals(ragMode), boostTerms);
                final long searchElapsed = System.currentTimeMillis() - searchStart;
                ConcurrentLog.info(
                    "RAGProxy",
                    "searchQuery=\"" + searchResultQuery + "\" queryMs=" + queryElapsed + " searchMs=" + searchElapsed +
                    " markdownChars=" + searchResultMarkdown.length() + " boostTerms=" + boostTerms.size());
                user += userPrefix;
                user += searchResultMarkdown;
                userObject.setContentText(user);
            }
            
            // write back modified bodyMap to body
            body = bodyObject.toString();

            // Open request to back-end service
            final URL url = new URI(llm4Chat.llm.hoststub + "/v1/chat/completions").toURL();
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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
                final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
                final String POISON = "POISON"; 
                Thread readerThread = new Thread(() -> {
                    // read the response of the back-end line-by-line and push it to a stack concurrently
                    try {
                        final BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {inputQueue.put(inputLine);}
                        in.close();
                        inputQueue.put(POISON);
                    } catch (IOException | InterruptedException e) {
                    } finally {
                        try {inputQueue.put(POISON);} catch (InterruptedException e) {}
                    }
                });
                readerThread.start();
                
                // read the stack line-by-line and write it to the client line-by-line
                try {
                    String inputLine;
                    int count = 0;
                    while (!(inputLine = inputQueue.take()).equals(POISON)) {
                        if (count == 0 && searchResultMarkdown.length() > 0) {
                            // for the first line we modify the data line to integrate the search result as file
                            int p = inputLine.indexOf('{');
                            if (p > 0) {
                                JSONObject j = new JSONObject(new JSONTokener(inputLine.substring(p)));
                                j.put("search-filename", "search_result_"+ searchResultQuery.replace(' ', '_') + ".md");
                                j.put("search-text-base64", new String(Base64.getEncoder().encode(searchResultMarkdown.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
                                inputLine = inputLine.substring(0, p) + j.toString();
                            }
                        }
                        out.println(inputLine); // i.e. data: {"id":"chatcmpl-69","object":"chat.completion.chunk","created":1715908287,"model":"llama3:8b","system_fingerprint":"fp_ollama","choices":[{"index":0,"delta":{"role":"assistant","content":"ߘ"},"finish_reason":null}]}
                        out.flush();
                        count++;
                    }
                } catch (InterruptedException e) {}
            }
            out.close(); // close this here to end transmission
        } catch (JSONException | URISyntaxException e) {
            throw new IOException(e.getMessage());
        }
    }
    
    public final static class DataURL {
    	private String mimetype;
    	private byte[] data;
    	private int signature; // identifier/helper
    	public DataURL(String data_url) {
    		if (data_url == null || !data_url.startsWith("data:")) {
                throw new IllegalArgumentException("data url not valid: it must start with 'data:'");
            }
    		int commaIndex = data_url.indexOf(',');
            if (commaIndex == -1) {
                throw new IllegalArgumentException("data url not valid: it must contain a comma");
            }
            String header = data_url.substring(5, commaIndex); // "image/jpeg;base64"
            String base64Data = data_url.substring(commaIndex + 1); // "/9j/4AAQSkZJRgABAQEASAB..."
            String[] headerParts = header.split(";");
            this.mimetype = headerParts[0]; // i.e. "image/jpeg"
            this.data = Base64.getDecoder().decode(base64Data);
            this.signature = base64Data.hashCode();
    	}
    	public String getMimetype() {
    		return this.mimetype;
    	}
    	public byte[] getData() {
    		return this.data;
    	}
    	public int getSiganture() {
    	    return this.signature;
    	}
    }

    public final static class UserObject {
        private JSONObject userObject;

        public UserObject(JSONObject userObject) {
            this.userObject = userObject;
        }
        
        public void attachAttachment(String prefix) {
            List<DataURL> data_urls = this.getContentAttachments(); // this list is a copy of the content data_urls
            
            // if the data_urls contains a text object, we remove that and inject it into the text prompt
            for (DataURL data_url: data_urls) {
                if (!data_url.getMimetype().startsWith("text/")) continue;
                String user = this.getContentText(); // this is the latest prompt
                String attachment = new String(data_url.getData(), StandardCharsets.UTF_8);
                user += prefix;
                user += attachment;
                this.setContentText(user);
                this.removeContentAttachment(data_url);
            }
            this.normalize();
        }
        
        public String getSearchMode() {
            Object raw = this.userObject.opt("search");
            if (raw instanceof Boolean) {
                return ((Boolean) raw) ? "local" : "no";
            }
            final String search = this.userObject.optString("search", "").trim().toLowerCase();
            if (search.isEmpty() || "no".equals(search) || "false".equals(search)) {
                return "no";
            }
            if ("local".equals(search) || "global".equals(search)) {
                return search;
            }
            return "no";
        }
        
        public String getContentText() {
            Object content = this.userObject.opt("content");
            assert content != null;
            if (content instanceof JSONArray) {
                JSONArray array = (JSONArray) content;
                for (int i = 0; i < array.length(); i++) {
                    JSONObject j = array.optJSONObject(i);
                    String ctype = j.optString("type");
                    if (ctype != null && ctype.equals("text")) {
                        String text = j.optString("text", "");
                        return text;
                    }
                }
                return "";
            }
            assert content instanceof String;
            return (String) content;
        }
        
        public List<DataURL> getContentAttachments() {
        	ArrayList<DataURL> list = new ArrayList<>();
            Object content = this.userObject.opt("content");
            assert content != null;
            if (content instanceof JSONArray) {
                JSONArray array = (JSONArray) content;
                for (int i = 0; i < array.length(); i++) {
                    JSONObject j = array.optJSONObject(i);
                    String ctype = j.optString("type");
                    if (ctype != null && ctype.equals("image_url")) {
                        JSONObject image_url = j.optJSONObject("image_url");
                        if (image_url != null) {
                        	String data_url = image_url.optString("url", "");
                        	if (data_url.length() > 0) {
                        		DataURL dataurl = new DataURL(data_url);
                        		list.add(dataurl);
                        	}
                        }
                    }
                }
            }
            return list;
        }
        
        public void removeContentAttachment(final DataURL delete_data_url) {
            Object content = this.userObject.opt("content");
            assert content != null;
            if (content instanceof JSONArray) {
                JSONArray array = (JSONArray) content;
                arrayloop: for (int i = 0; i < array.length(); i++) {
                    JSONObject j = array.optJSONObject(i);
                    String ctype = j.optString("type");
                    if (ctype != null && ctype.equals("image_url")) {
                        JSONObject image_url = j.optJSONObject("image_url");
                        if (image_url != null) {
                            String data_url = image_url.optString("url", "");
                            if (data_url.length() > 0) {
                                DataURL dataurl = new DataURL(data_url);
                                if (dataurl.getSiganture() == delete_data_url.getSiganture()) {
                                    array.remove(i);
                                    break arrayloop;
                                }
                            }
                        }
                    }
                }
                normalize();
            }
        }
        
        public void normalize() {
            // make a canonical form, which is that if the user object has no attachment,
            // then it should not have a "content" object.
            Object content = this.userObject.opt("content");
            assert content != null;
            if (content instanceof String) return;
            assert content instanceof JSONArray;
            JSONArray array = (JSONArray) content;
            assert array.length() > 0;
            if (array.length() != 1) return;
            JSONObject j = array.optJSONObject(0);
            String ctype = j.optString("type");
            assert ctype != null;
            assert ctype.equals("text");
            if (!ctype.equals("text")) return; // but thats wrong
            String text = j.optString("text", "");
            // simply replace the content array with the text, because nothing else is there.
            try {this.userObject.putOpt("content", text);} catch (JSONException e) {}
        }
        
        public void setContentText(String text) {
            Object content = this.userObject.opt("content");
            assert content != null;
            if (content instanceof String) {
                try {this.userObject.put("content", text);} catch (JSONException e) {}
                return;
            }
            assert content instanceof JSONArray;
            JSONArray array = (JSONArray) content;
            for (int i = 0; i < array.length(); i++) {
                JSONObject j = array.optJSONObject(i);
                String ctype = j.optString("type");
                if (ctype != null && ctype.equals("text")) {
                    try {j.putOpt("text", text);} catch (JSONException e) {}
                    return;
                }
            }
        }
    }
    
    public static JSONArray searchResults(String query, int count, final boolean includeSnippet) {
        return searchResults(query, count, includeSnippet, new LinkedHashSet<>());
    }

    public static JSONArray searchResults(String query, int count, final boolean includeSnippet, final Set<String> boostTerms) {
        final JSONArray results = new JSONArray();
        if (query == null || query.length() == 0 || count == 0) return results;
        Switchboard sb = Switchboard.getSwitchboard();
        EmbeddedSolrConnector connector = sb.index.fulltext().getDefaultEmbeddedConnector();
        // construct query
        final SolrQuery params = new SolrQuery();
        params.setQuery(query);
        params.set("defType", "edismax");
        params.set("qf",
            CollectionSchema.title.getSolrFieldName() + "^3 " +
            CollectionSchema.text_t.getSolrFieldName() + "^1 " +
            CollectionSchema.sku.getSolrFieldName() + "^0.5 " +
            CollectionSchema.h1_txt.getSolrFieldName() + "^2");
        params.set("pf",
            CollectionSchema.title.getSolrFieldName() + "^5 " +
            CollectionSchema.text_t.getSolrFieldName() + "^2");
        params.set("mm", "2<75%");
        final List<String> bqParts = new ArrayList<>();
        if (boostTerms != null && !boostTerms.isEmpty()) {
            for (String term : boostTerms) {
                if (term == null || term.isEmpty()) continue;
                bqParts.add(CollectionSchema.title.getSolrFieldName() + ":" + term + "^0.5");
                bqParts.add(CollectionSchema.h1_txt.getSolrFieldName() + ":" + term + "^0.4");
                bqParts.add(CollectionSchema.text_t.getSolrFieldName() + ":" + term + "^0.2");
            }
        }
        bqParts.add("(" + CollectionSchema.url_file_ext_s.getSolrFieldName() + ":(zip rar 7z tar gz bz2 xz tgz))^0.1");
        params.set("bq", String.join(" ", bqParts));
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
                        result.put("text", limitSnippet(text == null ? "" : text.trim(), 2000));
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
    
    public static String searchResultsAsMarkdown(String query, int count, boolean global) {
        return searchResultsAsMarkdown(query, count, global, new LinkedHashSet<>());
    }

    public static String searchResultsAsMarkdown(String query, int count, boolean global, final Set<String> boostTerms) {
        final long searchStart = System.currentTimeMillis();
        JSONArray searchResults = global ? searchResultsGlobal(query, count, true) : searchResults(query, count, true, boostTerms);
        ConcurrentLog.info("RAGProxy", "searchResults=" + searchResults.length() + " global=" + global + " searchMs=" + (System.currentTimeMillis() - searchStart));
        StringBuilder sb = new StringBuilder();
        
        // collect snippets
        List<Snippet> results = new ArrayList<>();
        for (int i  = 0; i < searchResults.length(); i++) {
            try {
                JSONObject r = searchResults.getJSONObject(i);
                String title = r.optString("title", "");
                String url = r.optString("url", "");
                String text = r.optString("text", "");
                if (title.isEmpty()) title = url;
                if (text.isEmpty()) text = title;
                if (title.length() > 0 && text.length() > 0) {
                    Snippet snippet = new Snippet(query, text, url, title, 256); // we always compute a snippet because that gives us a hint if the query appears at all
                    if (snippet.getText().length() > 0) results.add(snippet);
                }
            } catch (JSONException e) {}
        }
        
        // sort snippets again by score
        results.sort(Comparator.comparingDouble(Snippet::getScore));
        
        int limit = results.size() / 2;
        if (results.size() > 0 && limit == 0) limit = 1;
        for (int i  = 0; i < limit; i++) {
            Snippet snippet = results.get(i);
            sb.append("## ").append(snippet.getTitle()).append("\n");
            sb.append(snippet.text).append("\n");
            if (snippet.getURL().length() > 0) sb.append("Source: ").append(snippet.getURL()).append("\n");
            sb.append("\n\n");
        }
        
        ConcurrentLog.info("RAGProxy", "markdownChars=" + sb.length() + " snippetCount=" + results.size());
        return sb.toString();
    }

    public static JSONArray searchResultsGlobal(String query, int count, final boolean includeSnippet) {
        final JSONArray results = new JSONArray();
        if (query == null || query.length() == 0 || count == 0) return results;
        final Switchboard sb = Switchboard.getSwitchboard();
        final RankingProfile ranking = sb.getRanking();
        final int timezoneOffset = 0;
        final QueryModifier modifier = new QueryModifier(timezoneOffset);
        String querystring = modifier.parse(query);
        if (querystring.length() == 0) {
            querystring = query == null ? "" : query.trim();
        }
        if (querystring.length() == 0) return results;
        final QueryGoal qg = new QueryGoal(querystring);
        final QueryParams theQuery = new QueryParams(
                qg,
                modifier,
                0,
                "",
                Classification.ContentDomain.TEXT,
                "",
                timezoneOffset,
                new HashSet<Tagging.Metatag>(),
                CacheStrategy.IFFRESH,
                count,
                0,
                ".*",
                null,
                null,
                QueryParams.Searchdom.GLOBAL,
                null,
                true,
                DigestURL.hosthashess(sb.getConfig("search.excludehosth", "")),
                MultiProtocolURL.TLD_any_zone_filter,
                null,
                false,
                sb.index,
                ranking,
                ClientIdentification.yacyIntranetCrawlerAgent.userAgent(),
                0.0d,
                0.0d,
                0.0d,
                sb.getConfigSet("search.navigation"));
        final SearchEvent theSearch = SearchEventCache.getEvent(
                theQuery,
                sb.peers,
                sb.tables,
                (sb.isRobinsonMode()) ? sb.clusterhashes : null,
                false,
                sb.loader,
                (int) sb.getConfigLong(
                        SwitchboardConstants.REMOTESEARCH_MAXCOUNT_USER,
                        sb.getConfigLong(SwitchboardConstants.REMOTESEARCH_MAXCOUNT_DEFAULT, 10)),
                sb.getConfigLong(
                        SwitchboardConstants.REMOTESEARCH_MAXTIME_USER,
                        sb.getConfigLong(SwitchboardConstants.REMOTESEARCH_MAXTIME_DEFAULT, 3000)));
        final long timeout = sb.getConfigLong(
                SwitchboardConstants.REMOTESEARCH_MAXTIME_USER,
                sb.getConfigLong(SwitchboardConstants.REMOTESEARCH_MAXTIME_DEFAULT, 3000));
        waitForFeedingAndResort(theSearch, timeout);
        for (int i = 0; i < count; i++) {
            URIMetadataNode node = theSearch.oneResult(i, timeout);
            if (node == null) break;
            try {
                final JSONObject result = new JSONObject(true);
                result.put("url", node.urlstring());
                result.put("title", node.title());
                if (includeSnippet) {
                    String text = node.snippet();
                    if (text == null || text.isEmpty()) {
                        TextSnippet snippet = node.textSnippet();
                        if (snippet != null && snippet.exists() && !snippet.getErrorCode().fail()) {
                            text = snippet.getLineRaw();
                        }
                    }
                    if (text == null || text.isEmpty()) {
                        text = firstFieldString(node.getFieldValue(CollectionSchema.description_txt.getSolrFieldName()));
                    }
                    if (text == null || text.isEmpty()) {
                        text = firstFieldString(node.getFieldValue(CollectionSchema.text_t.getSolrFieldName()));
                    }
                    result.put("text", limitSnippet(text == null ? "" : text.trim(), 2000));
                }
                results.put(result);
            } catch (JSONException e) {
                // skip this result
            }
        }
        return results;
    }

    private static String limitSnippet(String text, int maxChars) {
        if (text == null) return "";
        if (maxChars <= 0 || text.length() <= maxChars) return text;
        return text.substring(0, maxChars);
    }

    private static String firstFieldString(Object value) {
        if (value == null) return "";
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                if (item != null) return item.toString();
            }
            return "";
        }
        return value.toString();
    }

    private static void waitForFeedingAndResort(SearchEvent search, long timeoutMs) {
        if (search == null || timeoutMs <= 0) return;
        final long end = System.currentTimeMillis() + timeoutMs;
        while (!search.isFeedingFinished() && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        search.resortCachedResults();
    }
    
    
    public static class Snippet {
        
        private String text, url, title;
        private double score;
        
        /**
         * Find a snippet inside a given text that contains most of the searched words plus some context.
         * @param query a string with a search query; query words are separated by space
         * @param text the text where we want to find the snippets
         * @param maxChunkLength the maximum length of a single chunk; however the snippet is three times as this.
         * @return one string containing the snippet.
         */
        public Snippet(String query, String text, String url, String title, int maxChunkLength) {
            this.url = url;
            this.title = title;
            this.score = 0.0;
            
            if (text == null || text.isEmpty() || maxChunkLength <= 0 || query == null) {
                this.text = "";
                return;
            }

            // Step 1: Slice text and make copy with lowercase version to support tf*idf computation
            List<String> chunks = slicer(text, maxChunkLength);
            if (chunks.isEmpty()) {
                this.text = "";
                return;
            }
            List<String> chunksLowerCase = new ArrayList<>(chunks.size());
            for (String chunk: chunks) chunksLowerCase.add(chunk.toLowerCase());

            // Step 2: Preprocess query
            Set<String> queryWordSet = querySet(query);
            if (queryWordSet.isEmpty()) {
                this.text = "";
                return;
            }

            // Step 3: Compute IDF
            // IDF uses a logarithm because the information gain of rare words grows non-linearly; 
            // the log dampens extreme ratios (N/df), stabilizes TF-IDF values, and matches the 
            // information-theoretic definition of word informativeness.
            int totalChunks = chunksLowerCase.size();
            Map<String, Double> idf = new HashMap<>();
            for (String word: queryWordSet) {
                int docFreq = 0;
                for (String chunk: chunksLowerCase) {
                    if (chunk.contains(word)) docFreq++;
                }
                idf.put(word, Math.log((double) totalChunks / (docFreq + 1)) + 1);
            }

            // Step 4: Score chunks
            Map<Integer, Double> chunkScores = new HashMap<>();
            for (int i = 0; i < chunksLowerCase.size(); i++) {
                String chunk = chunksLowerCase.get(i);
                double score = 0.0;
                Map<String, Integer> tf = new HashMap<>(); // counts occurrence in query for each word in chunk

                // Extract words and clean
                String[] wordsInChunk = chunk.split("\\s+");
                for (String w : wordsInChunk) {
                    String cleanWord = w.replaceAll("[.,!?;:]", "");
                    if (cleanWord.length() > 0 && queryWordSet.contains(cleanWord)) {
                        tf.put(cleanWord, tf.getOrDefault(cleanWord, 0) + 1);
                    }
                }

                // Sum TF-IDF
                for (String word: queryWordSet) {
                    int tfValue = tf.getOrDefault(word, 0);
                    double tfIdf = (double) tfValue * idf.getOrDefault(word, 1.0);
                    score += tfIdf;
                }
                chunkScores.put(i, score);
            }

            // Step 5: Find best chunk
            int topChunkIndex = -1;
            for (Map.Entry<Integer, Double> entry: chunkScores.entrySet()) {
                if (entry.getValue() > this.score) {
                    this.score = entry.getValue();
                    topChunkIndex = entry.getKey();
                }
            }

            // if there is no best chunk, return an empty snippet
            if (topChunkIndex < 0) {
                this.text = "";
                this.score = 0.0;
                return;
            }
            
            // Step 6: Get 3-slice snippet
            List<String> snippetChunks = new ArrayList<>();
            if (topChunkIndex > 0) {
                snippetChunks.add(chunks.get(topChunkIndex - 1));
            }
            snippetChunks.add(chunks.get(topChunkIndex));
            if (topChunkIndex < chunks.size() - 1) {
                snippetChunks.add(chunks.get(topChunkIndex + 1));
            }

            // Step 7: Join
            this.text = String.join(" ", snippetChunks);
        }
        
        public double getScore() {
            return this.score;
        }
        
        public String getText() {
            return this.text;
        }
        
        public String getURL() {
            return this.url;
        }
        
        public String getTitle() {
            return this.title;
        }
    }

    /**
     * Creates slices of a given text. We want slices of average same size,
     * but we want to prevent that cuts are made within sentences.
     * @param text the given text
     * @param len the minimum length of the wanted slices; actual slices may be longer
     * @return a list of text slices
     */
    public static List<String> slicer(String text, int len) {
        List<String> result = new ArrayList<>();
        if (text == null || len <= 0) return result;

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + len, text.length());

            // Move end position further out until a a sentence end is found:
            // look for sentence boundary: .!?, followed by whitespace char.
            while (end < text.length()) {
                char ch = text.charAt(end - 1);
                if ((ch == '.' || ch == '?' || ch == '!') && Character.isWhitespace(text.charAt(end))) {
                    break;
                }
                end++;
            }
            result.add(text.substring(start, end));
            start = end;
        }

        return result;
    }
    
    private static String getOneString(SolrDocument doc, CollectionSchema field) {
        assert field.isMultiValued();
        assert field.getType() == SolrType.string || field.getType() == SolrType.text_general;
        Object r = doc.getFieldValue(field.getSolrFieldName());
        if (r == null) return "";
        if (r instanceof ArrayList) {
            return (String) ((ArrayList<?>) r).get(0);
        }
        return r.toString();
    }

    private String searchWordsForPrompt(LLM llm, String model, String prompt) {
        String question = prompt;
        try {
            LLM.Context context = new LLM.Context(LLM_SYSTEM_PREFIX_DEFAULT);
            context.addPrompt(question);
            Set<String> singlewords = new LinkedHashSet<>();
            String[] a = LLM.stringsFromChat(llm.chat(model, context, LLM.listSchema, 200));
            // unfortunately this might not be a single word per line but several words; we collect them all.
            for (String s: a) {
                for (String t: s.split(" ")) singlewords.add(t.toLowerCase());
            }
            StringBuilder query = new StringBuilder();
            for (String s: singlewords) query.append(s).append(' ');
            return query.toString().trim();
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return "";
        }
    }
    
    private static Set<String> querySet(String query) {
        Set<String> queryWordSet = Arrays.stream(query.trim().toLowerCase().split("\\s+"))
                .map(String::toLowerCase)
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toSet());
        return queryWordSet;
    }

    private static Set<String> intersectTokens(String originalPrompt, String computedQuery, int maxTerms) {
        Set<String> promptTerms = querySet(originalPrompt == null ? "" : originalPrompt);
        Set<String> queryTerms = querySet(computedQuery == null ? "" : computedQuery);
        Set<String> intersection = new LinkedHashSet<>();
        for (String term : promptTerms) {
            if (!queryTerms.contains(term)) continue;
            final String cleaned = cleanToken(term);
            if (cleaned.isEmpty()) continue;
            intersection.add(cleaned);
            if (maxTerms > 0 && intersection.size() >= maxTerms) break;
        }
        return intersection;
    }

    private static String cleanToken(String term) {
        if (term == null) return "";
        String cleaned = term.replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.length() < 2) return "";
        return cleaned.toLowerCase();
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

    public static void pruneOldEntries(long now) {
        while (true) {
            final AbstractMap.SimpleEntry<Long, String> head = ACCESS_LOG.peekFirst();
            if (head == null) break;
            if (now - head.getKey() > ONE_DAY_MS) {
                ACCESS_LOG.pollFirst();
            } else {
                break;
            }
        }
    }

    public static void recordAccess(String ip) {
        final long now = System.currentTimeMillis();
        pruneOldEntries(now);
        ACCESS_LOG.addLast(new AbstractMap.SimpleEntry<>(now, ip));
    }

    public static long countAccess(String ip, long windowMillis, long now) {
        return ACCESS_LOG.stream()
                .filter(e -> (ip == null || e.getValue().equals(ip)) && (now - e.getKey()) <= windowMillis)
                .count();
    }

    public static boolean isRateLimited(Switchboard sb, String ip, boolean localhostAccess) {
        final long now = System.currentTimeMillis();
        pruneOldEntries(now);
        boolean allow_nonlocalhost = sb.getConfigBool("ai.shield.allow-nonlocalhost", false);
        boolean limit_all = sb.getConfigBool("ai.shield.limit-all", false);
        
        // guest limits apply only to non-localhost
        if (!localhostAccess) {
            long perMinuteLimit = allow_nonlocalhost ? parseLimit(sb.getConfig("ai.shield.rate.per-minute", "0")) : 0;
            long perHourLimit = allow_nonlocalhost ? parseLimit(sb.getConfig("ai.shield.rate.per-hour", "0")) : 0;
            long perDayLimit = allow_nonlocalhost ? parseLimit(sb.getConfig("ai.shield.rate.per-day", "0")) : 0;
            if (perMinuteLimit > 0 && countAccess(ip, ONE_MINUTE_MS, now) >= perMinuteLimit) return true;
            if (perHourLimit > 0 && countAccess(ip, ONE_HOUR_MS, now) >= perHourLimit) return true;
            if (perDayLimit > 0 && countAccess(ip, ONE_DAY_MS, now) >= perDayLimit) return true;
        }

        if (localhostAccess && limit_all) {
            long allMinute = parseLimit(sb.getConfig("ai.shield.all.per-minute", "0"));
            long allHour = parseLimit(sb.getConfig("ai.shield.all.per-hour", "0"));
            long allDay = parseLimit(sb.getConfig("ai.shield.all.per-day", "0"));
            if (allMinute > 0 && countAccess(null, ONE_MINUTE_MS, now) >= allMinute) return true;
            if (allHour > 0 && countAccess(null, ONE_HOUR_MS, now) >= allHour) return true;
            if (allDay > 0 && countAccess(null, ONE_DAY_MS, now) >= allDay) return true;
        }
        return false;
    }

    private static long parseLimit(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

}
