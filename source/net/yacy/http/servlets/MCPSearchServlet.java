/**
 *  MCPSearchServlet
 *  Copyright 2025 by Michael Peter Christen
 *  First released 11.10.2025 at https://yacy.net
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

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.util.ConcurrentLog;

import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.servlet.cache.Method;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * This servlet exposes a minimal Model Context Protocol (MCP) server that
 *  offers a single tool, `search`, backed by the YaCy search index.
 *  Clients can initialize a JSON-RPC session, list available tools and invoke
 *  the web search tool to retrieve results from the embedded Solr instance.
 *  The response payload follows the MCP conventions (JSON-RPC 2.0 with tool
 *  results wrapped inside a `content` array).
 */
public class MCPSearchServlet extends HttpServlet {

    private static final long serialVersionUID = 433609077273989355L;
    private static final ConcurrentLog log = new ConcurrentLog("MCPSearchServlet");

    private static final String JSONRPC_VERSION = "2.0";
    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";
    private static final String TOOL_NAME = "search";
    private static final int DEFAULT_RESULT_COUNT = 10;
    private static final int MAX_RESULT_COUNT = 100;

    @Override
    public void service(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        final HttpServletRequest hrequest = (HttpServletRequest) request;
        final HttpServletResponse hresponse = (HttpServletResponse) response;
        log.info("MCPSearchServlet: " + hrequest.getMethod() + " " + hrequest.getRequestURI());

        hresponse.setContentType("application/json;charset=utf-8");
        hresponse.setHeader(HeaderFramework.CORS_ALLOW_ORIGIN, "*");
        hresponse.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
        hresponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        final Method reqMethod = Method.getMethod(hrequest.getMethod());
        if (reqMethod == Method.OTHER) {
            hresponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        final String body = readBody(request);       
        Object parsed = null;
        if (body.length() == 0) {
            parsed = new JSONObject();
        } else try {
            final JSONTokener tokener = new JSONTokener(body);
            parsed = tokener.nextValue();
        } catch (JSONException e) {
            writeJsonResponse(hresponse, errorResponse(JSONObject.NULL, -32700, e.getMessage()));
        }
        
        try {
            if (parsed instanceof JSONObject) {
                if (((JSONObject) parsed).optString("method", "").length() == 0) {
                    String uri = hrequest.getRequestURI();
                    ((JSONObject) parsed).put("method", uri.substring(1));
                }
                final JSONObject responseObject = handleRequest((JSONObject) parsed);
                if (responseObject != null) {
                    writeJsonResponse(hresponse, responseObject);
                } else {
                    hresponse.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }
            } else if (parsed instanceof JSONArray) {
                final JSONArray requestArray = (JSONArray) parsed;
                final JSONArray responseArray = new JSONArray();
                for (int i = 0; i < requestArray.length(); i++) {
                    final Object entry = requestArray.get(i);
                    if (entry instanceof JSONObject) {
                        final JSONObject responseObject = handleRequest((JSONObject) entry);
                        if (responseObject != null) {
                            responseArray.put(responseObject);
                        }
                    }
                }
                if (responseArray.length() > 0) {
                    writeJsonResponse(hresponse, responseArray);
                } else {
                    hresponse.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }
            } else {
                writeJsonResponse(hresponse, errorResponse(JSONObject.NULL, -32600, "Invalid JSON-RPC payload"));
            }
        } catch (JSONException e) {
            writeJsonResponse(hresponse, errorResponse(JSONObject.NULL, -32700, e.getMessage()));
        }
    }

    private JSONObject handleRequest(final JSONObject requestObject) {
        final Object id = requestObject.opt("id");
        final String jsonrpc = requestObject.optString("jsonrpc", JSONRPC_VERSION);
        final String method = requestObject.optString("method", "");

        if (!JSONRPC_VERSION.equals(jsonrpc)) {
            return errorResponse(id, -32600, "Unsupported JSON-RPC version");
        }

        if (method == null || method.isEmpty()) {
            return errorResponse(id, -32600, "Missing method");
        }

        if (id == JSONObject.NULL || id == null) {
            // Notification: acknowledge silently
            if ("notifications/ping".equals(method)) {
                return null;
            }
            // No response for other notifications either
            return null;
        }

        switch (method) {
            case "initialize":
                return handleInitialize(id, requestObject.optJSONObject("params"));
            case "tools/initialize":
                return handleInitialize(id, requestObject.optJSONObject("params"));
            case "list":
                return handleToolsList(id);
            case "tools/list":
                return handleToolsList(id);
            case "call":
                return handleToolsCall(id, requestObject.optJSONObject("params"));
            case "tools/call":
                return handleToolsCall(id, requestObject.optJSONObject("params"));
            default:
                return errorResponse(id, -32601, "Unknown method: " + method);
        }
    }

    private JSONObject handleInitialize(final Object id, final JSONObject params) {
        try {log.info("MCPSearchServlet: initialize " + (params == null ? "" : params.toString(0)));} catch (JSONException e) {}
        final JSONObject result = new JSONObject(true);
        try {
            final JSONObject serverInfo = new JSONObject(true);
            serverInfo.put("name", "YaCy MCP Web Search");
            serverInfo.put("version", "1.0");

            final JSONObject capabilities = new JSONObject(true);
            final JSONObject toolsCapability = new JSONObject(true);
            toolsCapability.put("list", true);
            toolsCapability.put("call", true);
            capabilities.put("tools", toolsCapability);

            result.put("protocolVersion", MCP_PROTOCOL_VERSION);
            result.put("serverInfo", serverInfo);
            result.put("capabilities", capabilities);
        } catch (JSONException e) {
            return errorResponse(id, -32603, e.getMessage());
        }
        return successResponse(id, result);
    }

    private JSONObject handleToolsList(final Object id) {
        log.info("MCPSearchServlet: list " + (id == null ? "" : id.toString()));
        try {
            final JSONObject tool = new JSONObject(true);
            tool.put("name", TOOL_NAME);
            tool.put("description", "Search the YaCy index and return the most relevant documents.");

            final JSONObject inputProperties = new JSONObject(true);
            final JSONObject querySchema = new JSONObject(true);
            querySchema.put("type", "string");
            querySchema.put("description", "Search query to execute against the YaCy index.");
            inputProperties.put("query", querySchema);

            final JSONObject limitSchema = new JSONObject(true);
            limitSchema.put("type", "integer");
            limitSchema.put("minimum", 1);
            limitSchema.put("maximum", MAX_RESULT_COUNT);
            limitSchema.put("description", "Maximum number of results to return.");
            inputProperties.put("limit", limitSchema);

            final JSONObject includeSnippetSchema = new JSONObject(true);
            includeSnippetSchema.put("type", "boolean");
            includeSnippetSchema.put("description", "Include text snippets extracted from the indexed document content.");
            inputProperties.put("include_snippet", includeSnippetSchema);

            final JSONObject inputSchema = new JSONObject(true);
            inputSchema.put("type", "object");
            inputSchema.put("properties", inputProperties);
            final JSONArray required = new JSONArray();
            required.put("query");
            inputSchema.put("required", required);

            tool.put("inputSchema", inputSchema);

            final JSONObject outputSchema = new JSONObject(true);
            outputSchema.put("type", "object");
            final JSONObject outputProperties = new JSONObject(true);
            final JSONObject resultsSchema = new JSONObject(true);
            resultsSchema.put("type", "array");
            resultsSchema.put("description", "Ordered list of search results.");
            outputProperties.put("results", resultsSchema);
            outputSchema.put("properties", outputProperties);
            tool.put("outputSchema", outputSchema);

            final JSONArray tools = new JSONArray();
            tools.put(tool);

            final JSONObject result = new JSONObject(true);
            result.put("tools", tools);
            return successResponse(id, result);
        } catch (JSONException e) {
            return errorResponse(id, -32603, e.getMessage());
        }
    }

    private JSONObject handleToolsCall(final Object id, final JSONObject params) {
        try {log.info("MCPSearchServlet: call " + (id == null ? "" : id.toString()) + " params: " + (params == null ? "" : params.toString(0)));} catch (JSONException e) {}
        if (params == null) {
            return errorResponse(id, -32602, "Missing params");
        }
        final String name = params.optString("name", "");
        if (!TOOL_NAME.equals(name)) {
            return errorResponse(id, -32601, "Unknown tool: " + name);
        }

        final JSONObject arguments = params.optJSONObject("arguments");
        if (arguments == null) {
            return errorResponse(id, -32602, "Missing tool arguments");
        }

        final String query = arguments.optString("query", "").trim();
        if (query.isEmpty()) {
            return errorResponse(id, -32602, "Tool argument 'query' must be a non-empty string");
        }

        int limit = arguments.optInt("limit", DEFAULT_RESULT_COUNT);
        if (limit <= 0) {
            limit = DEFAULT_RESULT_COUNT;
        }
        limit = Math.min(limit, MAX_RESULT_COUNT);
        final boolean includeSnippet = arguments.optBoolean("include_snippet", true);

        JSONArray results;
        results = RAGProxyServlet.searchResults(query, limit, includeSnippet);

        try {
            final JSONObject payload = new JSONObject(true);
            payload.put("results", results);

            final JSONObject contentItem = new JSONObject(true);
            contentItem.put("type", "json");
            contentItem.put("json", payload);

            final JSONArray content = new JSONArray();
            content.put(contentItem);

            final JSONObject result = new JSONObject(true);
            result.put("content", content);
            return successResponse(id, result);
        } catch (JSONException e) {
            return errorResponse(id, -32603, e.getMessage());
        }
    }

    private static String readBody(final ServletRequest request) throws IOException {
        final StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static JSONObject successResponse(final Object id, final JSONObject result) {
        final JSONObject response = new JSONObject(true);
        try {
            response.put("jsonrpc", JSONRPC_VERSION);
            response.put("id", id);
            response.put("result", result);
        } catch (JSONException e) {
            // As this method is only called with valid JSON objects we rethrow as unchecked
            throw new IllegalStateException("Failed to construct success response", e);
        }
        return response;
    }

    private static JSONObject errorResponse(final Object id, final int code, final String message) {
        final JSONObject response = new JSONObject(true);
        try {
            response.put("jsonrpc", JSONRPC_VERSION);
            response.put("id", id);
            final JSONObject error = new JSONObject(true);
            error.put("code", code);
            error.put("message", message);
            response.put("error", error);
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to construct error response", e);
        }
        return response;
    }

    private static void writeJsonResponse(final HttpServletResponse response, final Object payload) throws IOException {
        final String serialized = payload instanceof JSONObject ? ((JSONObject) payload).toString()
            : payload instanceof JSONArray ? ((JSONArray) payload).toString() : payload.toString();
        response.getWriter().write(serialized);
        response.getWriter().flush();
    }
}
