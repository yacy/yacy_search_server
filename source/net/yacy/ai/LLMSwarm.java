/**
 *  LLMSwarm.java
 *  Copyright 2025 by Michael Peter Christen
 *  First released 01.06.2025 at https://yacy.net
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


public class LLMSwarm {
	
	Random random = new Random();
	List<LLM> swarm;;

	public LLMSwarm() {
        this.swarm = new ArrayList<>();
	}

	public void addOllamaClient(final LLM client) {
		this.swarm.add(client);
	}
	
	public List<String> getSwarm() {
		List<String> hoststubs = new ArrayList<>();
		for (LLM client : this.swarm) {
			hoststubs.add(client.getHoststub());
		}
		return hoststubs;
	}
	
	/**
	 * Unlike the OllamaClient.listOllamaModels() method, this method returns not the size of the models, but the number of models available for all clients in the swarm.
	 * @return a LinkedHashMap where the key is the model name and the value is the number of models available across all clients in the swarm.
	 */
    public LinkedHashMap<String, Integer> listOllamaModels() {
    	final LinkedHashMap<String, Integer> modelCountMap = new LinkedHashMap<>();
		for (LLM client: this.swarm) {
			LinkedHashMap<String, Long> models = client.listOllamaModels();
			for (Map.Entry<String, Long> entry : models.entrySet()) {
				modelCountMap.merge(entry.getKey(), 1, Integer::sum);
			}
		}
		return modelCountMap;   
    }

    /**
     * check if a model exists in any of the clients in the swarm
     * @param name
     * @return true if at least one client has the model, false otherwise
     */
    public boolean ollamaModelExists(final String name) {
    	for (LLM client : this.swarm) {
			if (client.ollamaModelExists(name)) {
				return true;
			}
		}
		return false;
    }

    /**
	 * Pulls a model from either only one random client or all clients in the swarm.
	 * @param name the name of the model to pull
	 * @param all if true, pull from all clients; if false, pull from one random client
	 * @return true if the model was successfully pulled from at least one client, false otherwise
	 */
    public boolean pullOllamaModel(final String name, boolean all) {
    	boolean pulled = false;
    	if (all) {
			for (LLM client: this.swarm) {
				if (client.pullOllamaModel(name)) {
					pulled = true;
				}
			}
		} else {
			// we try several times in case one client is not available
			for (int i = 0; i < this.swarm.size(); i++) {
				int index = random.nextInt(this.swarm.size());
				LLM client = this.swarm.get(index);
				pulled = client.pullOllamaModel(name);
				if (pulled) return true;
			}
		}
		return pulled;
    }
    
    /**
     * get a sublist of the swarm where each client has the model
     * @param name
     * @return a List of OllamaClient objects that have the specified model
     */
    public List<LLM> getSwarm(String name) {
		List<LLM> clientsWithModel = new ArrayList<>();
		for (LLM client : this.swarm) {
			if (client.ollamaModelExists(name)) {
				clientsWithModel.add(client);
			}
		}
		return clientsWithModel;
    }
    
    /**
     * get an answer from a random client in the swarm that has the specified model
     * @param model the name of the model to use
     * @param systemPrompt a system prompt to set the context for the model
     * @param userPrompt the user prompt to get an answer for
     * @return an answer from the model
     * @throws IOException
     */
    public String getAnswer(String model, String systemPrompt, String userPrompt) throws IOException {
		List<LLM> clientsWithModel = getSwarm(model);
		if (clientsWithModel.isEmpty()) {
			return "No client with model " + model + " found.";
		}
		LLM client = clientsWithModel.get(random.nextInt(clientsWithModel.size()));
		return client.chat(model, systemPrompt, userPrompt, 4096);
	}
    
}
