/**
 *  HttpJsonTool
 *  Copyright 2026 by Michael Peter Christen
 *  First released 06.02.2026 at https://yacy.net
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

package net.yacy.ai.tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.yacy.ai.ToolHandler;
import net.yacy.cora.document.id.DigestURL;

public class HttpJsonTool implements ToolHandler {

    private static final String NAME = "http_json";
    private static final Set<String> ALLOWED_METHODS = new HashSet<>(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE"));
    private static final Set<String> BLOCKED_HEADERS = new HashSet<>(Arrays.asList("host", "content-length", "connection", "transfer-encoding"));
    private static final int DEFAULT_TIMEOUT_MS = 15000;
    private static final int MAX_TIMEOUT_MS = 60000;
    private static final int DEFAULT_MAX_BYTES = 1_000_000;
    private static final int MAX_ALLOWED_BYTES = 2_000_000;

    @Override
    public JSONObject definition() throws JSONException {
        JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "Fetch an HTTP endpoint and return parsed JSON response.");

        JSONObject params = new JSONObject(true);
        params.put("type", "object");
        JSONObject props = new JSONObject(true);

        JSONObject url = new JSONObject(true);
        url.put("type", "string");
        url.put("description", "Absolute http or https URL.");
        props.put("url", url);

        JSONObject method = new JSONObject(true);
        method.put("type", "string");
        method.put("description", "HTTP method. Allowed: GET, POST, PUT, PATCH, DELETE.");
        props.put("method", method);

        JSONObject headers = new JSONObject(true);
        headers.put("type", "object");
        headers.put("description", "Optional request headers (string values).");
        props.put("headers", headers);

        JSONObject body = new JSONObject(true);
        body.put("description", "Optional JSON body as object/array/string.");
        props.put("body", body);

        JSONObject timeout = new JSONObject(true);
        timeout.put("type", "integer");
        timeout.put("description", "Timeout in milliseconds (max 60000).");
        props.put("timeout_ms", timeout);

        JSONObject maxBytes = new JSONObject(true);
        maxBytes.put("type", "integer");
        maxBytes.put("description", "Maximum response size in bytes (max 2000000).");
        props.put("max_bytes", maxBytes);

        params.put("properties", props);
        params.put("required", new JSONArray().put("url"));
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    
    public int maxCallsPerTurn() {
        return 1;
    }

    
    public String execute(String arguments) {
        final JSONObject args;
        try {
            args = (arguments == null || arguments.isEmpty()) ? new JSONObject(true) : new JSONObject(arguments);
        } catch (JSONException e) {
            return ToolHandler.errorJson("Invalid arguments JSON. don't try again");
        }

        final String urlRaw = args.optString("url", "").trim();
        if (urlRaw.isEmpty()) return ToolHandler.errorJson("Missing url. don't try again");

        final DigestURL digestUrl;
        try {
            digestUrl = new DigestURL(urlRaw);
        } catch (Exception e) {
            return ToolHandler.errorJson("Invalid URL. don't try again");
        }
        final String protocol = digestUrl.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            return ToolHandler.errorJson("Only http/https URLs are allowed. don't try again");
        }

        final String method = args.optString("method", "GET").trim().toUpperCase();
        if (!ALLOWED_METHODS.contains(method)) {
            return ToolHandler.errorJson("Unsupported method " + method + ". don't try again");
        }
        final int timeoutMs = clampPositive(args.optInt("timeout_ms", DEFAULT_TIMEOUT_MS), DEFAULT_TIMEOUT_MS, MAX_TIMEOUT_MS);
        final int maxBytes = clampPositive(args.optInt("max_bytes", DEFAULT_MAX_BYTES), DEFAULT_MAX_BYTES, MAX_ALLOWED_BYTES);
        final Object body = args.opt("body");
        final JSONObject headers = args.optJSONObject("headers");

        HttpURLConnection conn = null;
        try {
            final URL url = new URI(digestUrl.toNormalform(true)).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Accept", "application/json");
            applyHeaders(conn, headers);

            if (body != null && !"GET".equals(method)) {
                conn.setDoOutput(true);
                if (conn.getRequestProperty("Content-Type") == null) {
                    conn.setRequestProperty("Content-Type", "application/json");
                }
                byte[] payload = encodeBody(body);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
            }

            int status = conn.getResponseCode();
            String contentType = conn.getHeaderField("Content-Type");
            if (contentType == null) contentType = "";
            final InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) {
                return ToolHandler.errorJson("Empty response body. don't try again");
            }
            final String text;
            try (InputStream in = stream) {
                text = new String(readLimited(in, maxBytes), StandardCharsets.UTF_8);
            }
            if (text.trim().isEmpty()) {
                return ToolHandler.errorJson("Empty response body. don't try again");
            }

            final Object parsed;
            try {
                parsed = new JSONTokener(text).nextValue();
            } catch (JSONException e) {
                return ToolHandler.errorJson("Response is not valid JSON. don't try again");
            }
            if (!(parsed instanceof JSONObject) && !(parsed instanceof JSONArray)) {
                return ToolHandler.errorJson("Response is not JSON object/array. don't try again");
            }

            JSONObject result = new JSONObject(true);
            result.put("url", digestUrl.toNormalform(true));
            result.put("method", method);
            result.put("status", status);
            result.put("content_type", contentType);
            result.put("json", parsed);
            return result.toString();
        } catch (IOException e) {
            return ToolHandler.errorJson("Fetch error: " + e.getMessage() + ". don't try again");
        } catch (URISyntaxException e) {
            return ToolHandler.errorJson("Invalid URL syntax. don't try again");
        } catch (JSONException e) {
            return ToolHandler.errorJson("Failed to build tool response. don't try again");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void applyHeaders(HttpURLConnection conn, JSONObject headers) {
        if (conn == null || headers == null) return;
        for (String key : headers.keySet()) {
            if (key == null) continue;
            final String normalized = key.trim();
            if (normalized.isEmpty()) continue;
            if (BLOCKED_HEADERS.contains(normalized.toLowerCase())) continue;
            final String value = headers.optString(key, "");
            conn.setRequestProperty(normalized, value);
        }
    }

    private static byte[] encodeBody(Object body) {
        if (body == null) return new byte[0];
        if (body instanceof JSONObject) return ((JSONObject) body).toString().getBytes(StandardCharsets.UTF_8);
        if (body instanceof JSONArray) return ((JSONArray) body).toString().getBytes(StandardCharsets.UTF_8);
        if (body instanceof String) return ((String) body).getBytes(StandardCharsets.UTF_8);
        return String.valueOf(body).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Response too large");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static int clampPositive(int value, int defaultValue, int maxValue) {
        if (value <= 0) return defaultValue;
        if (value > maxValue) return maxValue;
        return value;
    }
}
