/**
 *  OllamaClient
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

package net.yacy.ai;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OllamaClient {

    public static String OLLAMA_API_HOST  = "http://localhost:11434";

    private final String hoststub;

    public OllamaClient(final String hoststub) {
        this.hoststub = hoststub;
    }

    public LinkedHashMap<String, Long> listOllamaModels() {
        final LinkedHashMap<String, Long> sortedMap = new LinkedHashMap<>();
        try {
            final String response = OpenAIClient.sendGetRequest(this.hoststub + "/api/tags");
            final JSONObject responseObject = new JSONObject(response);
            final JSONArray models = responseObject.getJSONArray("models");

            final List<Map.Entry<String, Long>> list = new ArrayList<>();
            for (int i = 0; i < models.length(); i++) {
                final JSONObject model = models.getJSONObject(i);
                final String name = model.optString("name", "");
                final long size = model.optLong("size", 0);
                list.add(new AbstractMap.SimpleEntry<>(name, size));
            }

            // Sort the list in descending order based on the values
            list.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));

            // Create a new LinkedHashMap and add the sorted entries
            for (final Map.Entry<String, Long> entry : list) {
                sortedMap.put(entry.getKey(), entry.getValue());
            }
        } catch (JSONException | URISyntaxException | IOException e) {
            e.printStackTrace();
        }
        return sortedMap;
    }

    public boolean ollamaModelExists(final String name) {
        final JSONObject data = new JSONObject();
        try {
            data.put("name", name);
            OpenAIClient.sendPostRequest(this.hoststub + "/api/show", data);
            return true;
        } catch (JSONException | URISyntaxException | IOException e) {
            return false;
        }
    }

    public boolean pullOllamaModel(final String name) {
        final JSONObject data = new JSONObject();
        try {
            data.put("name", name);
            data.put("stream", false);
            final String response = OpenAIClient.sendPostRequest(this.hoststub + "/api/pull", data);
            // this sends {"status": "success"} in case of success
            final JSONObject responseObject = new JSONObject(response);
            final String status = responseObject.optString("status", "");
            return status.equals("success");
        } catch (JSONException | URISyntaxException | IOException e) {
            return false;
        }
    }

    public static void main(final String[] args) {
        final OllamaClient oc = new OllamaClient(OLLAMA_API_HOST);

        final LinkedHashMap<String, Long> models = oc.listOllamaModels();
        System.out.println(models.toString());

        // check if model exists
        final String model = "phi3:3.8b";
        if (oc.ollamaModelExists(model))
            System.out.println("model " + model + " exists");
        else
            System.out.println("model " + model + " does not exist");

        // pull a model
        final boolean success = oc.pullOllamaModel(model);
        System.out.println("pulled model + " + model + ": " + success);

    }
}
