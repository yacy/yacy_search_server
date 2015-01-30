/**
 *  VocabularyScraper
 *  Copyright 2015 by Michael Peter Christen
 *  First released 30.01.2015 at http://yacy.net
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

package net.yacy.document;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.JSONException;
import net.yacy.cora.util.JSONObject;
import net.yacy.kelondro.io.CharBuffer;

public class VocabularyScraper {

    private final JSONObject scraperDefinition;
    private Map<String, String> classVocabulary; // a mapping from class names to the vocabulary where this class should be mapped
    private final Map<DigestURL, ConcurrentHashMap<String, String>> vocMap; // a mapping from a document to a map from vocabularies to terms

    public VocabularyScraper() {
        this.classVocabulary = null;
        this.scraperDefinition = new JSONObject();
        this.vocMap = new ConcurrentHashMap<>();
    }
    
    public VocabularyScraper(JSONObject init) {
        // init must be a property list of property lists: the key of the top property list is the name of the vocabulary, the name of the embedded property list is the entity class and the value of the embedded property is the entity name
        this.scraperDefinition = init == null ? new JSONObject() : init;
        this.vocMap = new ConcurrentHashMap<>();
        if (this.scraperDefinition.length() == 0) {
            this.classVocabulary = null;
        } else {
            this.classVocabulary = new ConcurrentHashMap<>();
            for (String voc: this.scraperDefinition.keySet()) {
                JSONObject props = this.scraperDefinition.getJSONObject(voc);
                try {
                    String classtype = props.getString("class");
                    this.classVocabulary.put(classtype, voc);
                } catch (JSONException e) {}
            }
            if (this.classVocabulary.size() == 0) this.classVocabulary = null;
        }
    }
    
    public VocabularyScraper(String init) {
        this(new JSONObject(init));
    }
    
    @Override
    public String toString() {
        return this.scraperDefinition.toString();
    }
    
    public void check(DigestURL root, String className, CharBuffer content) {
        if (this.classVocabulary == null) return;
        String voc = this.classVocabulary.get(className);
        if (voc == null) return;
        // record the mapping
        ConcurrentHashMap<String, String> vocmap = this.vocMap.get(root);
        if (vocmap == null) {
            synchronized (this) {
                vocmap = new ConcurrentHashMap<>();
                this.vocMap.put(root, vocmap);
            }
        }
        if (!vocmap.containsKey(voc)) vocmap.put(voc, content.toString()); // we put only the first occurrence of the entity into the vocmap
    }
    
    public Map<String, String> removeVocMap(DigestURL root) {
        return this.vocMap.remove(root);
    }
    
}
