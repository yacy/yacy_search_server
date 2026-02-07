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
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.servlet.cache.Method;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.LLM;
import net.yacy.ai.RAGAugmentor;
import net.yacy.ai.ToolCallProtocol;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;

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
            UserObject userObject = null;
            String user = "";
            String ragMode = "no";
            String userPrompt = "";
            int lastUserIndex = -1;
            for (int i = messages.length() - 1; i >= 0; i--) {
                JSONObject message = messages.getJSONObject(i);
                if ("user".equals(message.optString("role", ""))) {
                    lastUserIndex = i;
                    break;
                }
            }
            if (lastUserIndex >= 0) {
                userObject = new UserObject(messages.getJSONObject(lastUserIndex));
                user = userObject.getContentText(); // this is the latest user prompt
                userPrompt = user;
                ragMode = userObject.getSearchMode();
            }
            ConcurrentLog.info("RAGProxy", "ragMode=" + ragMode + " userChars=" + (user == null ? 0 : user.length()));
            //List<DataURL> data_urls = userObject.getContentAttachments(); // this list is a copy of the content data_urls
            
            // RAG
            String searchResultQuery = "";
            String searchResultMarkdown = "";
            if (userObject != null && !"no".equals(ragMode)) {
                // modify system and user prompt here in bodyObject to enable RAG
                final String queryPrefix = sb.getConfig("ai.llm-query-generator-prefix", LLM_QUERY_GENERATOR_PREFIX_DEFAULT);
                final long queryStart = System.currentTimeMillis();
                searchResultQuery = RAGAugmentor.searchWordsForPrompt(llm4tldr.llm, llm4tldr.model, queryPrefix + user); // might return null in case any error occurred
                if (searchResultQuery == null || searchResultQuery.length() == 0) searchResultQuery = user; // in case there is an error we simply search with the prompt
                final long queryElapsed = System.currentTimeMillis() - queryStart;
                final Set<String> boostTerms = RAGAugmentor.intersectTokens(userPrompt, searchResultQuery, 8);
                final long searchStart = System.currentTimeMillis();
                searchResultMarkdown = RAGAugmentor.searchResultsAsMarkdown(searchResultQuery, 10, "global".equals(ragMode), boostTerms);
                final long searchElapsed = System.currentTimeMillis() - searchStart;
                ConcurrentLog.info(
                    "RAGProxy",
                    "searchQuery=\"" + searchResultQuery + "\" queryMs=" + queryElapsed + " searchMs=" + searchElapsed +
                    " markdownChars=" + searchResultMarkdown.length() + " boostTerms=" + boostTerms.size());
                user += userPrefix;
                user += searchResultMarkdown;
                userObject.setContentText(user);
            }
            
            JSONObject initialMetadata = null;
            if (searchResultMarkdown.length() > 0) {
                initialMetadata = new JSONObject(true);
                initialMetadata.put("search-filename", "search_result_" + searchResultQuery.replace(' ', '_') + ".md");
                initialMetadata.put("search-text-base64", new String(Base64.getEncoder().encode(searchResultMarkdown.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
            }

            // ToolCallProtocol owns request preparation, initial stream handling and follow-up tool rounds.
            final int status = ToolCallProtocol.proxyToolLifecycle(out, llm4Chat, bodyObject, messages, initialMetadata);
            hresponse.setStatus(status);
            out.close(); // close this here to end transmission
        } catch (JSONException e) {
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
