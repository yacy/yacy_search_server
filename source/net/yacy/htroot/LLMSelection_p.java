// IndexExport_p.java
// -----------------------
// (C) 2004-2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2004 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.htroot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class LLMSelection_p {

    private static final String MODEL_CAPABILITIES_CONFIG = "ai.model_capabilities";

    private static String normalizeCapabilityStatus(final Object value) {
        if (Boolean.TRUE.equals(value)) return "supported";
        if (Boolean.FALSE.equals(value)) return "unsupported";
        final String text = value == null ? "" : value.toString().trim().toLowerCase();
        if ("supported".equals(text) || "unsupported".equals(text) || "unknown".equals(text)) return text;
        return "unknown";
    }

    private static JSONObject normalizeModelCapabilities(final JSONObject source) throws JSONException {
        final JSONObject normalized = new JSONObject(true);
        if (source == null) return normalized;
        for (final String key : source.keySet()) {
            final JSONObject entry = source.optJSONObject(key);
            final JSONObject normalizedEntry = new JSONObject(true);
            if (entry != null) {
                normalizedEntry.put("thinking", normalizeCapabilityStatus(entry.opt("thinking")));
                normalizedEntry.put("tooling", normalizeCapabilityStatus(entry.opt("tooling")));
                normalizedEntry.put("vision", normalizeCapabilityStatus(entry.opt("vision")));
                normalizedEntry.put("format", normalizeCapabilityStatus(entry.opt("format")));
            } else {
                normalizedEntry.put("thinking", "unknown");
                normalizedEntry.put("tooling", "unknown");
                normalizedEntry.put("vision", "unknown");
                normalizedEntry.put("format", "unknown");
            }
            normalized.put(key, normalizedEntry);
        }
        return normalized;
    }

    private static String capabilityKey(final JSONObject row) {
        if (row == null) return "";
        final String service = row.optString("service", "").trim();
        String hoststub = row.optString("hoststub", "").trim();
        while (hoststub.endsWith("/")) hoststub = hoststub.substring(0, hoststub.length() - 1);
        final String model = row.optString("model", "").trim();
        return service + "|" + hoststub + "|" + model;
    }

    private static boolean optBooleanRole(final JSONObject row, final String canonicalKey, final String displayKey) {
        if (row == null) return false;
        return row.optBoolean(canonicalKey, row.optBoolean(displayKey, false));
    }

    private static JSONObject normalizeProductionModelRow(final JSONObject row) throws JSONException {
        final JSONObject normalized = new JSONObject(true);
        normalized.put("service", row.optString("service", "OLLAMA"));
        normalized.put("model", row.optString("model", ""));
        normalized.put("hoststub", row.optString("hoststub", ""));
        normalized.put("api_key", row.optString("api_key", ""));
        normalized.put("max_tokens", row.optString("max_tokens", String.valueOf(net.yacy.ai.LLM.DEFAULT_MAX_TOKENS)));

        normalized.put("search", false);
        normalized.put("chat", row.optBoolean("chat", false));
        normalized.put("translation", false);
        normalized.put("classification", false);
        normalized.put("query", false);
        normalized.put("qapairs", false);
        normalized.put("tldr", row.optBoolean("tldr", false));
        normalized.put("logreport", optBooleanRole(row, "logreport", "log-report"));

        normalized.put("thinking", row.optBoolean("thinking", false));
        normalized.put("tooling", row.optBoolean("tooling", false));
        normalized.put("vision", row.optBoolean("vision", false));
        normalized.put("format", row.optBoolean("format", false));
        return normalized;
    }

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;

        final serverObjects prop = new serverObjects();
        String body = post == null ? "" : post.get("BODY", "");
        JSONObject bodyj = new JSONObject();
        if (body.length() > 0) {
            try {
                bodyj = new JSONObject(new JSONTokener(body));
            } catch (JSONException e) {
                // silently catch this
            }
        }
        JSONArray production_models = bodyj.optJSONArray("production_models");
        if (production_models != null) {
            // simply store the model array
            try {
                final JSONArray normalizedModels = new JSONArray();
                for (int i = 0; i < production_models.length(); i++) {
                    normalizedModels.put(normalizeProductionModelRow(production_models.getJSONObject(i)));
                }
                sb.setConfig("ai.production_models", normalizedModels.toString(0));
            } catch (JSONException e) {
                //e.printStackTrace();
            }
        }

        JSONObject modelCapabilities = bodyj.optJSONObject("model_capabilities");
        if (modelCapabilities != null) {
            try {
                sb.setConfig(MODEL_CAPABILITIES_CONFIG, normalizeModelCapabilities(modelCapabilities).toString());
            } catch (JSONException e) {
                sb.setConfig(MODEL_CAPABILITIES_CONFIG, "{}");
            }
        }

        JSONObject inferenceSystem = bodyj.optJSONObject("inference_system");
        if (inferenceSystem != null) {
            sb.setConfig("ai.inference_system", inferenceSystem.toString());
        }

        JSONObject serviceNumCtx = bodyj.optJSONObject("service_num_ctx");
        if (serviceNumCtx != null) {
            // per-service context window (num_ctx), keyed by normalized hoststub
            try {
                final JSONObject normalized = new JSONObject(true);
                for (final String hoststub : serviceNumCtx.keySet()) {
                    final String key = net.yacy.ai.LLM.normalizeHoststub(hoststub);
                    if (key.isEmpty()) continue;
                    final int value = serviceNumCtx.optInt(hoststub, 0);
                    if (value > 0) normalized.put(key, value);
                }
                sb.setConfig(net.yacy.ai.LLM.SERVICE_NUM_CTX_CONFIG, normalized.toString());
            } catch (JSONException e) {
                //e.printStackTrace();
            }
        }
        /*
        {"production_models":[{
          "service":"OLLAMA",
          "model":"hf.co\/janhq\/Jan-v1-edge-gguf:Q4_K_M",
          "hoststub":"http:\/\/localhost:11434",
          "api_key":"",
          "max_tokens":"2048",
          "answers":true,
          "chat":true,
          "translation":true,
          "qa-generation":true,
          "classification":true,
          "tldr-shortener":true,
          "tooling":true,
          "vision":true
        }]}
        */
        
        JSONObject capabilities = new JSONObject(true);
        final String capabilitiesJson = sb.getConfig(MODEL_CAPABILITIES_CONFIG, "{}");
        try {
            capabilities = normalizeModelCapabilities(new JSONObject(new JSONTokener(capabilitiesJson)));
        } catch (JSONException e) {
            capabilities = new JSONObject(true);
        }

        // generate table for production_models
        String pms = sb.getConfig("ai.production_models", "[]");
        if (pms.isEmpty() || pms.equals("{}")) pms = "[]";
        try {
            production_models = new JSONArray(new JSONTokener(pms));
            for (int i = 0; i < production_models.length(); i++) {
                JSONObject row = normalizeProductionModelRow(production_models.getJSONObject(i));
                prop.put("productionmodels_" + i + "_service", row.optString("service", "OLLAMA"));
                prop.put("productionmodels_" + i + "_model", row.optString("model", ""));
                prop.put("productionmodels_" + i + "_hoststub", row.optString("hoststub", ""));
                prop.put("productionmodels_" + i + "_api_key", row.optString("api_key", ""));
                prop.put("productionmodels_" + i + "_max_tokens", row.optString("max_tokens", String.valueOf(net.yacy.ai.LLM.DEFAULT_MAX_TOKENS)));
                
                prop.put("productionmodels_" + i + "_search", row.optBoolean("search", false));
                prop.put("productionmodels_" + i + "_chat", row.optBoolean("chat", false));
                prop.put("productionmodels_" + i + "_translation", row.optBoolean("translation", false));
                prop.put("productionmodels_" + i + "_classification", row.optBoolean("classification", false));
                prop.put("productionmodels_" + i + "_query", row.optBoolean("query", false));
                prop.put("productionmodels_" + i + "_qapairs", row.optBoolean("qapairs", false));
                prop.put("productionmodels_" + i + "_tldr", row.optBoolean("tldr", false));
                prop.put("productionmodels_" + i + "_logreport", row.optBoolean("logreport", false));
                
                final String key = capabilityKey(row);
                JSONObject capabilityEntry = key.isEmpty() ? null : capabilities.optJSONObject(key);
                String thinkingStatus = capabilityEntry == null ? "unknown" : normalizeCapabilityStatus(capabilityEntry.opt("thinking"));
                String toolingStatus = capabilityEntry == null ? "unknown" : normalizeCapabilityStatus(capabilityEntry.opt("tooling"));
                String visionStatus = capabilityEntry == null ? "unknown" : normalizeCapabilityStatus(capabilityEntry.opt("vision"));
                String formatStatus = capabilityEntry == null ? "unknown" : normalizeCapabilityStatus(capabilityEntry.opt("format"));
                if (row.optBoolean("thinking", false)) thinkingStatus = "supported";
                if (row.optBoolean("tooling", false)) toolingStatus = "supported";
                if (row.optBoolean("vision", false)) visionStatus = "supported";
                if (row.optBoolean("format", false)) formatStatus = "supported";
                prop.put("productionmodels_" + i + "_thinking",
                        "supported".equals(thinkingStatus) ? "yes" : "unsupported".equals(thinkingStatus) ? "no" : "?");
                prop.put("productionmodels_" + i + "_tooling",
                        "supported".equals(toolingStatus) ? "yes" : "unsupported".equals(toolingStatus) ? "no" : "?");
                prop.put("productionmodels_" + i + "_vision",
                        "supported".equals(visionStatus) ? "yes" : "unsupported".equals(visionStatus) ? "no" : "?");
                prop.put("productionmodels_" + i + "_format",
                        "supported".equals(formatStatus) ? "yes" : "unsupported".equals(formatStatus) ? "no" : "?");
            }
            prop.put("productionmodels", production_models.length());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // build the per-service table: one row per distinct hoststub found in the
        // production models, with its configured context window (num_ctx)
        try {
            JSONObject numCtxMap = new JSONObject(true);
            try {
                numCtxMap = new JSONObject(new JSONTokener(sb.getConfig(net.yacy.ai.LLM.SERVICE_NUM_CTX_CONFIG, "{}")));
            } catch (JSONException e) {
                numCtxMap = new JSONObject(true);
            }
            final java.util.LinkedHashMap<String, String> serviceByHoststub = new java.util.LinkedHashMap<>();
            if (production_models != null) {
                for (int i = 0; i < production_models.length(); i++) {
                    final JSONObject row = production_models.getJSONObject(i);
                    final String hoststub = net.yacy.ai.LLM.normalizeHoststub(row.optString("hoststub", ""));
                    if (hoststub.isEmpty() || serviceByHoststub.containsKey(hoststub)) continue;
                    serviceByHoststub.put(hoststub, row.optString("service", "OLLAMA"));
                }
            }
            int s = 0;
            for (final java.util.Map.Entry<String, String> service : serviceByHoststub.entrySet()) {
                final int numCtx = numCtxMap.optInt(service.getKey(), net.yacy.ai.LLM.DEFAULT_NUM_CTX);
                prop.put("services_" + s + "_service", service.getValue());
                prop.putHTML("services_" + s + "_hoststub", service.getKey());
                prop.put("services_" + s + "_num_ctx", numCtx);
                s++;
            }
            prop.put("services", s);
        } catch (JSONException e) {
            prop.put("services", 0);
        }

        try {
            if (production_models != null) {
                for (int i = 0; i < production_models.length(); i++) {
                    final JSONObject row = normalizeProductionModelRow(production_models.getJSONObject(i));
                    final String key = capabilityKey(row);
                    if (key.isEmpty()) continue;
                    JSONObject entry = capabilities.optJSONObject(key);
                    if (entry == null) {
                        entry = new JSONObject(true);
                        entry.put("thinking", "unknown");
                        entry.put("tooling", "unknown");
                        entry.put("vision", "unknown");
                        entry.put("format", "unknown");
                        capabilities.put(key, entry);
                    }
                    if (row.optBoolean("thinking", false)) entry.put("thinking", "supported");
                    if (row.optBoolean("tooling", false)) entry.put("tooling", "supported");
                    if (row.optBoolean("vision", false)) entry.put("vision", "supported");
                    if (row.optBoolean("format", false)) entry.put("format", "supported");
                }
            }
            prop.putHTML("model_capabilities", capabilities.toString());
        } catch (JSONException e) {
            prop.putHTML("model_capabilities", "{}");
        }

        // expose the stored per-service num_ctx map to the page so the Services
        // table can prefill the window for a selected-but-not-yet-deployed endpoint
        prop.putHTML("service_num_ctx_json", sb.getConfig(net.yacy.ai.LLM.SERVICE_NUM_CTX_CONFIG, "{}"));

        // prefill inference system configuration if present
        final String inferenceJson = sb.getConfig("ai.inference_system", "{}");
        try {
            JSONObject inference = new JSONObject(new JSONTokener(inferenceJson));
            prop.put("llm_service", inference.optString("service", "OLLAMA"));
            prop.put("llm_hoststub", inference.optString("hoststub", "http://localhost:11434"));
            prop.put("llm_apikey", inference.optString("api_key", ""));
        } catch (JSONException e) {
            prop.put("llm_service", "OLLAMA");
            prop.put("llm_hoststub", "http://localhost:11434");
            prop.put("llm_apikey", "");
        }

        if (post == null || env == null) {
            return prop; // nothing to do
        }

        // return rewrite properties
        return prop;
    }

}
