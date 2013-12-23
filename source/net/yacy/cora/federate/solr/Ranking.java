/**
 *  Ranking
 *  Copyright 2013 by Michael Peter Christen
 *  First released 12.03.2013 at http://yacy.net
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
import java.util.Map;
import java.util.Set;

import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.schema.CollectionSchema;

/**
 * The Ranking class is the solr ranking definition file for boosts and query functions.
 */
public class Ranking {
    
    // for minTokenLen = 2 the quantRate value should not be below 0.24; for minTokenLen = 3 the quantRate value must be not below 0.5!
    private static float quantRate = 0.5f; // to be filled with search.ranking.solr.doubledetection.quantrate
    private static int   minTokenLen = 3;   // to be filled with search.ranking.solr.doubledetection.minlength
    
    private Map<SchemaDeclaration, Float> fieldBoosts;
    private String name, boostQuery, boostFunction;
    
    public Ranking() {
        super();
        this.name = "";
        this.fieldBoosts = new LinkedHashMap<SchemaDeclaration, Float>();
        this.boostQuery = "";
        this.boostFunction = "";
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public void putFieldBoost(SchemaDeclaration schema, float boost) {
        this.fieldBoosts.put(schema,  boost);
    }
    
    public Float getFieldBoost(SchemaDeclaration schema) {
        return this.fieldBoosts.get(schema);
    }
    
    public Set<Map.Entry<SchemaDeclaration,Float>> getBoostMap() {
        return this.fieldBoosts.entrySet();
    }
    
    /**
     * the updateDef is a definition string that comes from a configuration file.
     * It should be a comma-separated list of field^boost values
     * This should be called with the field in search.ranking.solr.boost
     * @param boostDef the definition string
     */
    public void updateBoosts(String boostDef) {
        // call i.e. with "sku^20.0,url_paths_sxt^20.0,title^15.0,h1_txt^11.0,h2_txt^10.0,author^8.0,description_txt^5.0,keywords^2.0,text_t^1.0,fuzzy_signature_unique_b^100000.0"
        if (boostDef == null || boostDef.length() == 0) return;
        String[] bf = CommonPattern.COMMA.split(boostDef);
        this.fieldBoosts.clear();
        for (String boost: bf) {
            int p = boost.indexOf('^');
            if (p < 0) continue;
            String boostkey = boost.substring(0, p);
            try {
                CollectionSchema field = CollectionSchema.valueOf(boostkey);
                Float factor = Float.parseFloat(boost.substring(p + 1));
                this.fieldBoosts.put(field, factor);
            } catch (IllegalArgumentException e) {
                // boostkey is unknown; ignore it but print warning
                ConcurrentLog.warn("Ranking", "unknwon boost key '" + boostkey + "'");
            }
        }
    }

    public void setBoostQuery(String boostQuery) {
        this.boostQuery = boostQuery;
    }
    
    /**
     * produce a string that can be added as a 'boost query' at the bq-attribute
     * @return
     */
    public String getBoostQuery() {
        return this.boostQuery;
    }

    public void setBoostFunction(String boostFunction) {
        this.boostFunction = boostFunction;
    }
    
    /**
     * produce a boost function
     * @return
     */
    public String getBoostFunction() {
        return this.boostFunction;
    }
    
    /*
     * duplicate check static methods
     */

    public static void setQuantRate(float newquantRate) {
        quantRate = newquantRate;
    }

    public static void setMinTokenLen(int newminTokenLen) {
        minTokenLen = newminTokenLen;
    }

    public static float getQuantRate() {
        return quantRate;
    }

    public static int getMinTokenLen() {
        return minTokenLen;
    }
    
}
