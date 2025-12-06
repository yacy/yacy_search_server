// AILab.java
// -------------
// (C) 2024 by contributors to the YaCy project
//
// This servlet feeds the AI Lab landing page with completion hints
// derived from existing configuration and index state.

package net.yacy.htroot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class AILab {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        boolean hasEngine = false;
        boolean hasModel = false;
        boolean hasRagRole = false;

        // Parse configured production models to infer readiness of engine/model/RAG quests.
        final String productionModelJson = sb.getConfig("ai.production_models", "[]");
        try {
            final JSONArray productionModels = new JSONArray(new JSONTokener(productionModelJson));
            for (int i = 0; i < productionModels.length(); i++) {
                final org.json.JSONObject row = productionModels.getJSONObject(i);
                if (!hasEngine) {
                    hasEngine = !row.optString("hoststub", "").isEmpty();
                }
                if (!hasModel) {
                    hasModel = !row.optString("model", "").isEmpty();
                }
                if (!hasRagRole) {
                    hasRagRole = row.optBoolean("search", false) || row.optBoolean("query", false) || row.optBoolean("qapairs", false);
                }
                if (hasEngine && hasModel && hasRagRole) {
                    break;
                }
            }
        } catch (final JSONException e) {
            // ignore malformed configuration; fallback statuses will stay "pending"
        }

        // consider explicit inference_system configuration as an engine binding
        if (!hasEngine) {
            final String inferenceJson = sb.getConfig("ai.inference_system", "{}");
            try {
                final JSONObject inference = new JSONObject(new JSONTokener(inferenceJson));
                final String hoststub = inference.optString("hoststub", "").trim();
                if (!hoststub.isEmpty()) {
                    hasEngine = true;
                }
            } catch (final JSONException e) {
                // ignore malformed inference_system
            }
        }

        // Index presence check: if there are documents in the default fulltext collection, we consider the quest ready.
        final long indexDocs = sb.index.fulltext().collectionSize();
        final long indexNeeded = 1000L;
        final boolean hasIndex = indexDocs >= indexNeeded;

        // Shield configuration: treat any non-empty custom value as "ready".
        final String shieldDefinition = sb.getConfig("ai.shield.definition", "").trim();
        final boolean hasShield = !shieldDefinition.isEmpty();

        prop.put("ailab_inference_status", hasEngine ? "ready" : "pending");
        prop.put("ailab_model_status", hasModel ? "ready" : "pending");
        prop.put("ailab_inference_configured", hasEngine ? "1" : "0");
        prop.put("ailab_index_status", hasIndex ? "ready" : "pending");
        prop.putNum("ailab_index_count", indexDocs);
        prop.putNum("ailab_index_needed", indexNeeded);
        prop.put("ailab_rag_status", hasRagRole ? "ready" : "pending");
        prop.put("ailab_shield_status", hasShield ? "ready" : "pending");

        return prop;
    }
}
