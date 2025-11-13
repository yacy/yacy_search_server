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
                sb.setConfig("ai.production_models", production_models.toString(0));
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
          "max_tokens":"4096",
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
        
        // generate table for production_models
        String pms = sb.getConfig("ai.production_models", "[]");
        try {
            production_models = new JSONArray(new JSONTokener(pms));
            for (int i = 0; i < production_models.length(); i++) {
                JSONObject row = production_models.getJSONObject(i);
                prop.put("productionmodels_" + i + "_service", row.optString("service", "OLLAMA"));
                prop.put("productionmodels_" + i + "_model", row.optString("model", ""));
                prop.put("productionmodels_" + i + "_hoststub", row.optString("hoststub", ""));
                prop.put("productionmodels_" + i + "_api_key", row.optString("api_key", ""));
                prop.put("productionmodels_" + i + "_max_tokens", row.optString("max_tokens", "4096"));
                
                prop.put("productionmodels_" + i + "_search", row.optBoolean("search", false));
                prop.put("productionmodels_" + i + "_chat", row.optBoolean("chat", false));
                prop.put("productionmodels_" + i + "_translation", row.optBoolean("translation", false));
                prop.put("productionmodels_" + i + "_classification", row.optBoolean("classification", false));
                prop.put("productionmodels_" + i + "_query", row.optBoolean("query", false));
                prop.put("productionmodels_" + i + "_qapairs", row.optBoolean("qapairs", false));
                prop.put("productionmodels_" + i + "_tldr", row.optBoolean("tldr", false));
                
                prop.put("productionmodels_" + i + "_tooling", row.optBoolean("tooling", false));
                prop.put("productionmodels_" + i + "_vision", row.optBoolean("vision", false));
            }
            prop.put("productionmodels", production_models.length());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (post == null || env == null) {
            return prop; // nothing to do
        }

        // return rewrite properties
        return prop;
    }

}