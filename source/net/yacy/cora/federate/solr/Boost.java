/**
 *  Boost
 *  Copyright 2012 by Michael Peter Christen
 *  First released 30.11.2012 at http://yacy.net
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

package net.yacy.cora.federate.solr;

import java.util.LinkedHashMap;

import net.yacy.cora.util.CommonPattern;
import net.yacy.search.schema.CollectionSchema;

/**
 * The Boost class is the solr ranking definition file. It contains boost values in a Linked HashMap; the 'linked'-Version is used
 * to maintain the order of the arguments which shall be stable according to the iteration order within a configuration servlet.
 * Because the order is influence by a double-check mechanismn the attributes to apply a document signature are also integrated
 * into this class.
 */
public class Boost extends LinkedHashMap<CollectionSchema, Float> {
    
    private static final long serialVersionUID = 5248172257724571603L;

    public final static Boost RANKING = new Boost();
    
    // for minTokenLen = 2 the quantRate value should not be below 0.24; for minTokenLen = 3 the quantRate value must be not below 0.5!
    private float quantRate = 0.5f; // to be filled with search.ranking.solr.doubledetection.quantrate
    private int   minTokenLen = 3;   // to be filled with search.ranking.solr.doubledetection.minlength
    
    private Boost() {
        super();
        this.initDefaults();
    }

    public void initDefaults() {
        this.clear();
        put(CollectionSchema.sku, 20.0f);
        put(CollectionSchema.url_paths_sxt, 20.0f);
        put(CollectionSchema.title, 15.0f);
        put(CollectionSchema.h1_txt, 11.0f);
        put(CollectionSchema.h2_txt, 10.0f);
        put(CollectionSchema.author, 8.0f);
        put(CollectionSchema.description, 5.0f);
        put(CollectionSchema.keywords, 2.0f);
        put(CollectionSchema.text_t, 1.0f);
        put(CollectionSchema.synonyms_sxt, 0.9f);
        put(CollectionSchema.references_i, 0.5f);
    }
   
    /**
     * override the get method to return 1.0f for each non-resolvable object
     */
    public Float get(Object field) {
        Float boost = super.get(field);
        if (boost == null) return 1.0f;
        return boost;
    }
    
    /**
     * the updateDef is a definition string that comes from a configuration file.
     * It should be a comma-separated list of field^boost values
     * This should be called with the field in search.ranking.solr.boost
     * @param boostDef the definition string
     */
    public void updateBoosts(String boostDef) {
        // call i.e. with "sku^20.0f,url_paths_sxt^20.0f,title^15.0f,h1_txt^11.0f,h2_txt^10.0f,author^8.0f,description^5.0f,keywords^2.0f,text_t^1.0f,fuzzy_signature_unique_b^100000.0f"
        if (boostDef == null || boostDef.length() == 0) return;
        String[] bf = CommonPattern.COMMA.split(boostDef);
        for (String boost: bf) {
            int p = boost.indexOf('^');
            if (p < 0) continue;
            CollectionSchema field = CollectionSchema.valueOf(boost.substring(0, p));
            Float factor = Float.parseFloat(boost.substring(p + 1));
            this.put(field, factor);
        }
    }
    
    public void setQuantRate(float quantRate) {
        this.quantRate = quantRate;
    }

    public void setMinTokenLen(int minTokenLen) {
        this.minTokenLen = minTokenLen;
    }

    public float getQuantRate() {
        return quantRate;
    }

    public int getMinTokenLen() {
        return minTokenLen;
    }

    /**
     * produce a string that can be added as a 'boost query' at the bq-attribute
     * @return
     */
    public String getBoostQuery() {
        return CollectionSchema.fuzzy_signature_unique_b.getSolrFieldName() + ":true^100000.0f";
    }
    
    /**
     * produce a boost function
     * @return
     */
    public String getBoostFunction() {
        return "div(add(1,references_i),pow(add(1,inboundlinkscount_i),1.6))^0.4";
    }
    
}
