/**
 *  LLMAdminProxyServlet
 *  Copyright 2026 by Michael Peter Christen
 *  First released 04.07.2026 at https://yacy.net
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.yacy.cora.protocol.Domains;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.LogRedaction;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;

/**
 * Admin passthrough proxy for OpenAI-API compatible LLM endpoints (Ollama, LM Studio,
 * OpenAI, OpenRouter, ...). This makes the LLM endpoints which are configured in
 * /LLMSelection_p.html reachable for the browser front-end even when YaCy runs on a
 * remote host and the LLM endpoint is only visible from the YaCy server, not from the
 * user's machine. As a side effect all endpoint calls become same-origin requests,
 * which also avoids CORS and mixed-content (https YaCy / http Ollama) problems.
 *
 * Concept - one endpoint, two operation modes:
 * YaCy mirrors the LLM API paths (/v1/chat/completions, /api/tags, /v1/models,
 * /api/pull, /api/delete, /api/show) on its own port. The mode of a request is not
 * distinguished by the path but by the presence of a "hoststub" request parameter
 * (or X-LLM-Hoststub header) that selects the target endpoint:
 *
 * - Public mode (no hoststub): the request is NOT handled here. The serving servlet
 *   (RAGProxyServlet, OllamaTagsServlet, OpenAIModelsServlet) keeps its normal public
 *   behavior: target endpoint, model routing and api_key come exclusively from the
 *   server-side configuration, and the AIShield rules (localhost restriction, rate
 *   limiting, see /AIShield_p.html) apply. This mode serves yacychat.html and external
 *   OpenAI-compatible clients, also for non-admin users.
 *
 * - Admin passthrough mode (hoststub given): the request is forwarded 1:1 to the given
 *   endpoint, streaming the response back chunk by chunk. Because here the caller
 *   determines the proxy target, this mode is strictly limited to authenticated
 *   administrators; AIShield rules and RAG augmentation are skipped. This mode serves
 *   the endpoint management functions of /LLMSelection_p.html (model lists, model
 *   pull/delete, capability tests).
 *
 * Security invariant: "hoststub given => admin authentication enforced before anything
 * else happens". A non-admin user cannot turn the public mode into an open proxy.
 * Additional hardening even for admins: only the paths listed above are mirrored, and
 * mutating operations are limited to endpoints already present in the configuration;
 * arbitrary hoststubs are only accepted for the read-only model list probes. The
 * api_key is injected server-side from the stored configuration, so it does not need
 * to be exposed to the browser.
 *
 * This class is both a servlet on its own (for the paths which exist only in
 * passthrough mode, mapped in defaults/web.xml) and a static helper embedded in the
 * servlets that share their path with the public mode.
 */
public class LLMAdminProxyServlet extends HttpServlet {

    private static final long serialVersionUID = 3411544789759643138L;

    private static final String HOSTSTUB_PARAMETER = "hoststub";
    private static final String HOSTSTUB_HEADER = "X-LLM-Hoststub";

    /** paths that this proxy is willing to mirror on the target endpoint */
    private static final Set<String> PROXY_PATHS = Set.of(
            "/v1/chat/completions", "/v1/models",
            "/api/tags", "/api/show", "/api/pull", "/api/delete");

    /** read-only probe paths which may be used with a not-yet-saved hoststub */
    private static final Set<String> PROBE_PATHS = Set.of("/api/tags", "/v1/models");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public void service(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        // this servlet is mapped to paths which only exist in passthrough mode (i.e. /api/pull);
        // without a hoststub there is nothing we can serve
        if (!tryHandle((HttpServletRequest) request, (HttpServletResponse) response)) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_BAD_REQUEST, "hoststub parameter required");
        }
    }

    /**
     * Handle the request in admin passthrough mode if a hoststub is given.
     * Servlets on shared paths (RAGProxyServlet, OllamaTagsServlet, OpenAIModelsServlet)
     * call this first and continue with their normal public behavior when this returns false.
     * @return true if the request carried a hoststub and was fully handled (including error responses)
     */
    public static boolean tryHandle(final HttpServletRequest hrequest, final HttpServletResponse hresponse) throws IOException {
        String hoststub = hrequest.getParameter(HOSTSTUB_PARAMETER);
        if (hoststub == null || hoststub.trim().isEmpty()) hoststub = hrequest.getHeader(HOSTSTUB_HEADER);
        if (hoststub == null || hoststub.trim().isEmpty()) return false;
        handle(hrequest, hresponse, hoststub.trim());
        return true;
    }

    private static void handle(final HttpServletRequest hrequest, final HttpServletResponse hresponse, String hoststub) throws IOException {
        final long start = System.currentTimeMillis();
        final String method = hrequest.getMethod();

        if ("OPTIONS".equals(method)) {
            hresponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // the caller determines the proxy target, therefore this mode is admin-only
        if (!requireAdmin(hrequest, hresponse)) {
            ConcurrentLog.warn("LLMAdminProxy", "event=passthrough phase=reject reason=not-admin ip=" + hrequest.getRemoteAddr());
            return;
        }

        while (hoststub.endsWith("/")) hoststub = hoststub.substring(0, hoststub.length() - 1);
        if (!hoststub.startsWith("http://") && !hoststub.startsWith("https://")) {
            hresponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "hoststub must be a http(s) URL");
            return;
        }

        final String path = hrequest.getRequestURI();
        if (!PROXY_PATHS.contains(path)) {
            hresponse.sendError(HttpServletResponse.SC_NOT_FOUND, "path not proxied");
            return;
        }

        // resolve the api_key for the hoststub from the stored configuration;
        // mutating operations are only allowed on configured endpoints
        final Map<String, String> configured = configuredHoststubs();
        final boolean known = configured.containsKey(hoststub);
        if (!known && !PROBE_PATHS.contains(path)) {
            hresponse.sendError(HttpServletResponse.SC_FORBIDDEN, "hoststub is not a configured endpoint");
            return;
        }

        final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(hoststub + path))
                .timeout(Duration.ofMillis("/api/pull".equals(path) ? 60 * 60 * 1000 : 10 * 60 * 1000));
        final String contentType = hrequest.getContentType();
        if (contentType != null) requestBuilder.header("Content-Type", contentType);
        final String accept = hrequest.getHeader("Accept");
        if (accept != null) requestBuilder.header("Accept", accept);

        // inject the Authorization header server-side; a client-provided header
        // (needed to probe endpoints which are not saved yet) takes precedence
        final String clientAuthorization = hrequest.getHeader("Authorization");
        final String configuredKey = configured.get(hoststub);
        if (clientAuthorization != null && !clientAuthorization.isEmpty()) {
            requestBuilder.header("Authorization", clientAuthorization);
        } else if (configuredKey != null && !configuredKey.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + configuredKey);
        }

        if ("GET".equals(method) || "HEAD".equals(method)) {
            requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            final byte[] body = hrequest.getInputStream().readAllBytes();
            requestBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        }

        ConcurrentLog.info("LLMAdminProxy", "event=passthrough phase=start method=" + method + " path=" + path + " backend=" + LogRedaction.redact(hoststub) + " known=" + known);
        try {
            final HttpResponse<InputStream> upstream = CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
            hresponse.setStatus(upstream.statusCode());
            hresponse.setHeader("Content-Type", upstream.headers().firstValue("Content-Type").orElse("application/json;charset=utf-8"));
            final ServletOutputStream out = hresponse.getOutputStream();
            try (InputStream in = upstream.body()) {
                final byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, count);
                    out.flush(); // flush each chunk to support streamed responses (chat completions, pull progress)
                }
            }
            ConcurrentLog.info("LLMAdminProxy", "event=passthrough phase=end status=" + upstream.statusCode() + " durationMs=" + (System.currentTimeMillis() - start));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            hresponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "upstream request interrupted");
        } catch (final IOException e) {
            ConcurrentLog.warn("LLMAdminProxy", "event=passthrough phase=end result=failure reason=" + LogRedaction.redactMessage(e) + " durationMs=" + (System.currentTimeMillis() - start));
            if (!hresponse.isCommitted()) hresponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "endpoint not reachable from the YaCy server");
        }
    }

    /**
     * Check for administrator access, mirroring the active server security handler:
     * localhost access with the localhost-admin setting is granted without credentials,
     * everything else requires an authenticated user with the admin right. Sends the
     * authentication challenge (401) when credentials are missing.
     */
    private static boolean requireAdmin(final HttpServletRequest hrequest, final HttpServletResponse hresponse) throws IOException {
        final Switchboard sb = Switchboard.getSwitchboard();
        final String adminRole = SwitchboardConstants.ADMIN_ACCOUNT_ROLE;
        if (sb.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false)
                && Domains.isLocalhost(hrequest.getRemoteAddr())) return true;
        if (hrequest.isUserInRole(adminRole)) return true;
        try {
            if (hrequest.authenticate(hresponse) && hrequest.isUserInRole(adminRole)) return true;
        } catch (final ServletException | IOException e) {
            // fall through to the error response below
        }
        if (!hresponse.isCommitted()) hresponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "admin login required");
        return false;
    }

    /**
     * Collect all LLM endpoints from the stored configuration together with their api_key:
     * the inference system used on /LLMSelection_p.html and all production model rows.
     */
    private static Map<String, String> configuredHoststubs() {
        final Switchboard sb = Switchboard.getSwitchboard();
        final Map<String, String> hoststubs = new HashMap<>();
        try {
            final JSONObject inference = new JSONObject(new JSONTokener(sb.getConfig("ai.inference_system", "{}")));
            putHoststub(hoststubs, inference);
        } catch (final JSONException e) {}
        try {
            final JSONArray productionModels = new JSONArray(new JSONTokener(sb.getConfig("ai.production_models", "[]")));
            for (int i = 0; i < productionModels.length(); i++) {
                putHoststub(hoststubs, productionModels.optJSONObject(i));
            }
        } catch (final JSONException e) {}
        return hoststubs;
    }

    private static void putHoststub(final Map<String, String> hoststubs, final JSONObject row) {
        if (row == null) return;
        String hoststub = row.optString("hoststub", "").trim();
        while (hoststub.endsWith("/")) hoststub = hoststub.substring(0, hoststub.length() - 1);
        if (hoststub.isEmpty()) return;
        final String apiKey = row.optString("api_key", "").trim();
        // do not let a row without key shadow a key from another row for the same endpoint
        if (!hoststubs.containsKey(hoststub) || !apiKey.isEmpty()) hoststubs.put(hoststub, apiKey);
    }
}
